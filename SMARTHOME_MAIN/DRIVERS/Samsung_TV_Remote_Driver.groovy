/*	Samsung TV Remote Driver — Minimalist Edition
	Based on Dave Gutheinz v2.3.9 (Apache 2.0 License)
	Copyright 2022 Dave Gutheinz
	License: https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

	Stripped down by Elfege Leylavergne (2026) to focus on reliable on/off only.
	Full version backed up as Samsung_TV_Remote_Driver.groovy.bk
===========================================================================================*/
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
	definition (name: "Samsung TV Remote",
				namespace: "davegut",
				author: "David Gutheinz",
				singleThreaded: true) {
		capability "Switch"
		capability "Configuration"
		capability "Refresh"
		attribute "wsStatus", "string"
	}
	preferences {
		input "deviceIp", "text", title: "Samsung TV IP", required: true
		input "macAddressPref", "text", title: "MAC Address (no colons, e.g. D0C24EE93390)", required: true
		input "logEnable", "bool", title: "Enable debug logging", defaultValue: true
	}
}

// ===== Lifecycle =====

def installed() {
	log.info "installed"
	state.wsData = ""
	state.wsRetryCount = 0
	sendEvent(name: "wsStatus", value: "closed")
}

def updated() {
	log.info "updated: deviceIp=${deviceIp}"
	unschedule()
	state.wsData = ""
	if (!deviceIp || !macAddressPref) {
		log.error "Set IP and MAC address in preferences"
		return
	}
	device.updateSetting("macAddressPref", [value: macAddressPref.replaceAll(":", "").toUpperCase(), type: "text"])
	configure()
	runEvery1Minute(poll)
	runIn(10, keepAlive)
	if (logEnable) { runIn(1800, debugOff) }
}

def configure() {
	log.info "configure: querying TV at ${deviceIp}..."
	try {
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
			def tv = resp.data
			def mac = tv.device?.wifiMac ?: macAddressPref
			mac = mac.replaceAll(":", "").toUpperCase()
			updateDataValue("deviceMac", mac)
			updateDataValue("alternateWolMac", mac)
			device.setDeviceNetworkId(mac)
			def tokenSupport = tv.device?.TokenAuthSupport ?: "false"
			updateDataValue("tokenSupport", tokenSupport)
			def uuid = tv.device?.duid?.substring(5) ?: "unknown"
			updateDataValue("uuid", uuid)
			log.info "configure: mac=${mac}, tokenSupport=${tokenSupport}, uuid=${uuid}"
		}
	} catch (e) {
		log.warn "configure: TV unreachable (${e.message}). Set IP/MAC manually."
	}
	sendEvent(name: "wsStatus", value: "closed")
}

// ===== Switch: ON =====

def on() {
	log.info "on()"
	// Optimistic — tell the hub immediately
	sendEvent(name: "switch", value: "on")

	// WoL
	def mac = getDataValue("alternateWolMac") ?: macAddressPref?.replaceAll(":", "")?.toUpperCase()
	if (mac) {
		def wol = new hubitat.device.HubAction(
			"FFFFFFFFFFFF${mac * 16}",
			hubitat.device.Protocol.LAN,
			[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
			 destinationAddress: "255.255.255.255:7",
			 encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
		3.times {
			sendHubCommand(wol)
			pauseExecution(200)
		}
		log.debug "on: WoL sent 3x to ${mac}"
	}

	// Also send POWER key via WS (works if TV is in standby with WS still reachable)
	sendKey("POWER")
}

// ===== Switch: OFF =====

def off() {
	log.info "off()"
	// Optimistic — tell the hub immediately
	sendEvent(name: "switch", value: "off")

	// Send POWER toggle via WS
	sendKey("POWER")
}

// ===== Polling =====

def refresh() { poll() }

def poll() {
	try {
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 4]) { resp ->
			def power = "off"
			if (resp.status == 200) {
				def body = resp.data?.text ?: resp.data?.toString()
				if (body) {
					try {
						def parsed = new JsonSlurper().parseText(body)
						if (parsed?.device?.PowerState == "on") { power = "on" }
					} catch (pe) {
						// If body exists but isn't JSON, TV is likely on (API is responding)
						power = "on"
					}
				} else {
					// Got HTTP 200 but empty body — TV is alive
					power = "on"
				}
			}
			if (device.currentValue("switch") != power) {
				log.info "poll: switch changed to ${power}"
				sendEvent(name: "switch", value: power)
			}
		}
	} catch (e) {
		// Unreachable = off
		if (device.currentValue("switch") != "off") {
			log.info "poll: TV unreachable, setting off"
			sendEvent(name: "switch", value: "off")
		}
	}
}

// ===== WebSocket Core =====

def sendKey(key, cmd = "Click") {
	key = "KEY_${key.toUpperCase()}"
	def data = JsonOutput.toJson([
		method: "ms.remote.control",
		params: [Cmd: cmd, DataOfCmd: key, TypeOfRemote: "SendRemoteKey"]
	])
	sendWsMessage(data)
}

def sendWsMessage(data) {
	def wsStat = device.currentValue("wsStatus")
	if (wsStat == "open") {
		log.info "sendWsMessage: WS open, sending now"
		interfaces.webSocket.sendMessage(data)
	} else {
		log.info "sendWsMessage: WS ${wsStat}, queuing and connecting..."
		state.wsData = data
		wsConnect()
	}
}

def wsConnect() {
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ=="
	def url
	if (getDataValue("tokenSupport") == "true") {
		url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${state.token}"
	} else {
		url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
	}
	log.debug "wsConnect: ${url.take(60)}..."
	interfaces.webSocket.connect(url, ignoreSSLIssues: true)
}

def webSocketStatus(message) {
	log.info "webSocketStatus: ${message}"
	if (message == "status: open") {
		sendEvent(name: "wsStatus", value: "open")
		state.wsRetryCount = 0
		if (state.wsData) {
			log.info "webSocketStatus: sending queued command"
			interfaces.webSocket.sendMessage(state.wsData)
			state.wsData = ""
		}
	} else if (message == "status: closing") {
		sendEvent(name: "wsStatus", value: "closed")
	} else if (message?.take(7) == "failure") {
		log.warn "webSocketStatus: failure — ${message}"
		sendEvent(name: "wsStatus", value: "closed")
		// Retry if we have a queued command
		if (state.wsData) {
			def retries = state.wsRetryCount ?: 0
			if (retries < 3) {
				state.wsRetryCount = retries + 1
				log.warn "webSocketStatus: retry ${retries + 1}/3 for queued command"
				runIn(2, wsConnect)
			} else {
				log.error "webSocketStatus: giving up after 3 retries"
				state.wsData = ""
				state.wsRetryCount = 0
			}
		}
	}
}

def parse(resp) {
	try {
		def msg = parseJson(resp)
		if (msg.event == "ms.channel.connect" && msg.data?.token) {
			state.token = msg.data.token
			log.info "parse: token updated"
		}
	} catch (e) {
		log.debug "parse: ${resp?.take(100)}"
	}
}

def close() {
	interfaces.webSocket.close()
	sendEvent(name: "wsStatus", value: "closed")
}

// ===== Keep-alive =====

def keepAlive() {
	def wsStat = device.currentValue("wsStatus")
	if (wsStat == "open") {
		try {
			interfaces.webSocket.sendMessage("ping")
		} catch (e) {
			log.debug "keepAlive: ping failed, reconnecting"
			sendEvent(name: "wsStatus", value: "closed")
			wsConnect()
		}
	} else if (device.currentValue("switch") == "on") {
		log.info "keepAlive: TV on but WS closed, reconnecting"
		wsConnect()
	}
	runIn(60, keepAlive)
}

// ===== Logging =====

def debugOff() {
	device.updateSetting("logEnable", [type: "bool", value: false])
	log.info "Debug logging disabled"
}
