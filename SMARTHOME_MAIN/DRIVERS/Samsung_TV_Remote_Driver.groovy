/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Samsung TV Remote Driver
		Copyright 2022 Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2024 Version 2.3.9 ====================================================================
a.  Created preset functions (create, execute, trigger). Function work with and without
	SmartThings.  SEE DOCUMENTATION.
b.	Added buttons to support preset functions in dashboards.
c.	Added app codes to built-in app search list.
d.  Created methods to support adding running app automatically to state.appData
	if the SmartThings interface is enabled.
===========================================================================================*/
def driverVer() { return version() }
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
	definition (name: "Samsung TV Remote",
				namespace: "davegut",
				author: "David Gutheinz",
				singleThreaded: true,
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungTVRemote.groovy"
			   ){
		capability "Refresh"
		capability "Configuration"
		capability "SamsungTV"
		command "showMessage", [[name: "Not Implemented"]]
		capability "Switch"
		capability "PushableButton"
		capability "Variable"
	}
	preferences {
		pageSetup()
	}
}

def pageSetup(){

		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
		input "macAddressPref", "text", title: "Mac Address"
		if(macAddressPref){
			device.updateSetting("macAddressPref", [value: macAddressPref.replaceAll(":", ""), type: "text"])
		}

		if (deviceIp) {
			List modeOptions = ["ART_MODE", "Ambient", "none"]
			if (getDataValue("frameTv") == "false") {
				modeOptions = ["Ambient", "none"]
			}
			input ("tvPwrOnMode", "enum", title: "TV Startup Display", 
				   options: modeOptions, defaultValue: "none")
			input ("logEnable", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
			input ("infoLog", "bool", 
				   title: "Enable information logging " + helpLogo(),
				   defaultValue: true)
			stPreferences()
		}
		input ("pollInterval","enum", title: "Power Polling Interval (seconds)",
			   options: ["off", "10", "20", "30", "60"], defaultValue: "60")
		tvAppsPreferences()

		input "childProtect", "bool", title: "Enable child protection (TV will turn off if turned on, for some time)"
		input "childProtectDelay", "number", title: "Duration TV can't be turned back on (in seconds)", defaultValue:120
		if(childProtect){
			device.updateSetting("pollInterval", ["value": "10", "type": "enum"])
		}
		
}

String helpLogo() { 
	return """<a href="https://github.com/DaveGut/HubitatActive/blob/master/SamsungTvRemote/README.md">""" +
		"""<div style="position: absolute; top: 10px; right: 10px; height: 80px; font-size: 20px;">Samsung TV Remote Help</div></a>"""
}

//	===== Installation, setup and update =====
def installed() {
	state.token = "12345678"
	def tokenSupport = "false"
	sendEvent(name: "wsStatus", value: "closed")
//	sendEvent(name: "numberOfButtons", value: "60")
	runIn(1, updated)
}

def updated() {
	sendEvent(name: "numberOfButtons", value: "60")
	unschedule()
	close()
	state.wsData = ""
	def updStatus = [:]
	if (!deviceIp) {
		logWarn("\n\n\t\t<b>Enter the deviceIp and Save Preferences</b>\n\n")
		updStatus << [status: "ERROR", data: "Device IP not set."]
	} else {
		if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
			updateDataValue("driverVersion", driverVer())
			updStatus << [driverVer: driverVer()]
		}
		if (logEnable) { runIn(1800, debugLogOff) }
		updStatus << [logEnable: logEnable, infoLog: infoLog]
		updStatus << [setOnPollInterval: setOnPollInterval()]
		sendEvent(name: "numberOfButtons", value: 60)
		sendEvent(name: "wsStatus", value: "closed")
		def action = configure()
		if (!state.appData) { state.appData == [:] }
		updStatus << [updApps: updateAppCodes()]
	}
	
	logInfo("updated: ${updStatus}")
}

def setOnPollInterval() {
	if (pollInterval == null) {
		pollInterval = "60"
		device.updateSetting("pollInterval", [type:"enum", value: "60"])
	}
	if (pollInterval == "60") {
		runEvery1Minute(onPoll)
	} else if (pollInterval != "off") {
		schedule("0/${pollInterval} * * * * ?",  onPoll)
	}
	return pollInterval
}

def configure() {
	def respData = [:]
	def tvData = [:]
	try{
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
			tvData = resp.data
			runIn(1, getArtModeStatus)
		}
	} catch (error) {
		tvData << [status: "error", data: error]
		logError("configure: TV Off during setup or Invalid IP address.\n\t\tTurn TV On and Run CONFIGURE or Save Preferences!")

	}
	log.debug "tvData: $tvData"
	
	if (!tvData.status) {
		def wifiMac = tvData.device.wifiMac ?: tvData.id.split("-")[4]
		log.debug "wifiMac (1st attempt): $wifiMac"
		if (!wifiMac){
			wifiMac = macAddressPref
		}
		log.debug "wifiMac: $wifiMac"
		updateDataValue("deviceMac", wifiMac)
		def alternateWolMac = wifiMac.replaceAll(":", "").toUpperCase()
		updateDataValue("alternateWolMac", alternateWolMac)
		device.setDeviceNetworkId(alternateWolMac)
		def modelYear = "20" + tvData.device.model[0..1]
		updateDataValue("modelYear", modelYear)
		def frameTv = "false"
		if (tvData.device.FrameTVSupport) {
			frameTv = tvData.device.FrameTVSupport
		}
		updateDataValue("frameTv", frameTv)
		if (tvData.device.TokenAuthSupport) {
			tokenSupport = tvData.device.TokenAuthSupport
			updateDataValue("tokenSupport", tokenSupport)
		}
		def uuid = tvData.device.duid.substring(5)
		updateDataValue("uuid", uuid)
		respData << [status: "OK", dni: alternateWolMac, modelYear: modelYear,
					 frameTv: frameTv, tokenSupport: tokenSupport]
		sendEvent(name: "artModeStatus", value: "none")
		def data = [request:"get_artmode_status",
					id: "${getDataValue("uuid")}"]
		data = JsonOutput.toJson(data)
		artModeCmd(data)
	} else {
		respData << tvData
	}
	runIn(1, stUpdate)
	
	runIn(10, keepAlive)
	
	logInfo("configure: ${respData}")
	return respData
}

//	===== Polling/Refresh Capability =====
def onPoll() {
	logDebug "Polling..."
    def sendCmdParams = [
        uri: "http://${deviceIp}:8001/api/v2/",
        timeout: 6
    ]
    try {
        asynchttpGet("onPollParse", sendCmdParams)
        if (getDataValue("driverVersion") != driverVer()) {
            logInfo("Auto Configuring changes to this TV.") 
            updateDriver()
            pauseExecution(3000)
        }
    } catch (e) {
        logWarn("onPoll error: ${e}")
        // Retry once after brief delay
        runIn(2, onPoll)
    }
}

def updateDriver() {
}

def onPollParse(resp, data) {
	def powerState
	if (resp.status == 200) {
		powerState = new JsonSlurper().parseText(resp.data).device.PowerState
	} else {
		powerState = "NC"
	}
	def onOff = "off"
	if (powerState == "on") { 
		onOff = "on" 
		if (childProtectIsActive()) {
			off()	
			return
		} 
	}

	Map logData = [method: "onPollParse", httpStatus: resp.status, 
				   powerState: powerState, onOff: onOff]
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
		logData << [switch: onOff]
		if (onOff == "on") {
			runIn(5, setPowerOnMode)
		} else {
			setPowerOffMode()
		}
	}
	logDebug(logData)
}

def childProtectIsActive(){
	if(childProtect){
		state.lastOffCmdTime = state.lastOffCmdTime ?: now() 
		long timeSinceLastOffCmdWasSent = now() - state.lastOffCmdTime 
        long delayMillis = childProtectDelay * 1000L

		if(timeSinceLastOffCmdWasSent < delayMillis)
		{
			log.warn "TV IS IN CHILD PROTECT MODE. NOT TURNING IT ON."
			sendEvent(name: "switch", value: "off")
			return true
		}
		else {
			log.info "CHILD PROTECT TIMED OUT. Ok to turn on..."
			return false 
		}
	}
	return false 
}

//	===== Capability Switch =====
def on(calledBy="unknown") {	

	log.debug "Opening websocket before sending on command... "
	webSocketOpen()
	pauseExecution(1000)

	log.debug "on() calledBy=$calledBy"

	if (childProtectIsActive()) return 

	def delay = 1500
	def attempts = state.onAttempts ?: 0
	state.onAttempts = attempts + 1

	if (device.currentValue("switch") != "on") {

		if (device.currentValue("wsStatus") == "open") {	
			log.info "POWER ON, Press"	
			sendKey("POWER", "Press") 
			pauseExecution(delay)
			log.info "POWER ON, Release"	
			sendKey("POWER", "Release")
			
		}
		else {
			log.warn "SOCKET IS CLOSED!"
			// run(1, keepAlive) 
			webSocketOpen()
		

			def wolMac = getDataValue("alternateWolMac")

			if (!wolMac){
				wolMac = macAddressPref
			}

			// wolMac = "54880e84866e" // tv office
			log.debug "wolMac: $wolMac"
			def cmd = "FFFFFFFFFFFF$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac"
			def wol = new hubitat.device.HubAction(
				cmd,
				hubitat.device.Protocol.LAN,
				[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
				destinationAddress: "255.255.255.255:7",
				encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
			
			log.debug "wol: <br> $wol"
			
			int c = 0;
			while (c < 3){
				c++
				sendHubCommand(wol)
				log.trace c
				pauseExecution(500)
			}
			log.debug "WoL packet sent $c times..."
		}
		
		// Poll to check if successful
		onPoll()
		runIn(5, checkOnStatus)
		
		if(!state.onAttempts || state.onAttempts == 0){
			// Reset attempt counter after 30 seconds
			runIn(30, clearOnAttempts)
		}
	}
	else {
		log.info "${device.label} is already on"
	}
}

def checkOnStatus() {
	log.debug "checking status..."
	log.debug "state.onAttempts = $state.onAttempts"
	
    
	def currentState = device.currentValue('switch')
	log.debug "checkOnStatus(1) => current state: ${currentState}"

	onPoll()
	pauseExecution(3000)

	currentState = device.currentValue('switch')
	log.debug "checkOnStatus(2) => current state: ${currentState}"

	if (currentState == "on") {
		log.info "TV is on."
		clearOnAttempts()
		return
	}

	def max = 10
	// If still off after max attempts, log warning
    if (device.currentValue("switch") == "off" && state.onAttempts >= max) {
        logError("on: Multiple power on attempts unsuccessful")
        state.onAttempts = 0
		clearOnAttempts()
    }
	else {
		if(currentState != "on") {
			log.warn "Still off, new attempt"
			on(calledBy="checkOnStatus")
		}
	}
}

def clearOnAttempts() {
	log.debug "clearOnAttempts()"
    state.onAttempts = 0
	unschedule(checkOnStatus)
}

def setPowerOnMode() {
	logInfo("setPowerOnMode: [tvPwrOnMode: ${tvPwrOnMode}]")
	if(tvPwrOnMode == "ART_MODE") {
		getArtModeStatus()
		pauseExecution(1000)
		artMode()
	} else if (tvPwrOnMode == "Ambient") {
		ambientMode()
	}
	runIn(5, refresh)
}

def off() {

	webSocketOpen()
	pauseExecution(1000)

	clearOnAttempts() // clear any currently running attempts to turn the tv on

	if (device.currentValue("switch") != "off") {

		logInfo("off: --[frameTv: ${getDataValue("frameTv")}]")
		def attempts = state.offAttempts ?: 0
		state.offAttempts = attempts + 1
		
		def delay = 1500

		if (device.currentValue("wsStatus") == "open") {	
			log.info "POWER OFF, Press"	
			sendKey("POWER", "Press") 
			pauseExecution(delay)
			log.info "POWER OFF, Release"	
			sendKey("POWER", "Release")
		}
		else {
			log.warn "SOCKET IS CLOSED!"
			// run(1, keepAlive) 
			webSocketOpen()
		}
		
		// Poll to check if successful
		onPoll()
		runIn(5, checkOffStatus)
		
		if(!state.offAttempts || state.offAttempts == 0){
			delay = childProtectDelay ?: 30
			runIn(delay, clearOffAttempts)

			if (childProtect){
				state.lastOffCmdTime = now() 
			}
		}

	} else {
		log.debug("off: TV is already off")
	}
}

def checkOffStatus() {
	log.debug "checking status..."
	log.debug "state.offAttempts = $state.offAttempts"
	
    
	def currentState = device.currentValue('switch')
	log.debug "checkOffStatus(1) => current state: ${currentState}"

	onPoll()
	pauseExecution(3000)

	currentState = device.currentValue('switch')
	log.debug "checkOffStatus(2) => current state: ${currentState}"

	if (currentState == "off") {
		log.info "TV is off."
		if(!childProtect) clearOffAttempts() 
		return
	}

	def max = 10
	// If still on after max attempts, log warning
    if (device.currentValue("switch") == "on" && state.offAttempts >= max) {
        logError("off: Multiple power off attempts unsuccessful")
        state.offAttempts = 0
		clearOffAttempts()
    }
	else {
		if(currentState != "off") {
			log.warn "Still on, new attempt"
			off()
		}
	}
}

def clearOffAttempts() {
	log.debug "clearOffAttempts()"
    state.offAttempts = 0
	unschedule(checkOffStatus)
}

def setPowerOffMode() {
	logInfo("setPowerOffMode")
	sendEvent(name: "appId", value: " ")
	sendEvent(name: "appName", value: " ")
	sendEvent(name: "tvChannel", value: " ")
	sendEvent(name: "tvChannelName", value: " ")
	runIn(5, refresh)
}

def setVariable(valueString) {
	sendEvent(name: "variable", value: valueString)
}

def refresh() {
	if (connectST) {
		deviceRefresh()
	}
	runIn(4, updateTitle)
	onPoll()
}

//	===== BUTTON INTERFACE =====
def push(pushed) {
	logDebug("push: button = ${pushed}, trigger = ${state.triggered}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	pushed = pushed.toInteger()
	switch(pushed) {
		//	===== Physical Remote Commands =====
		case 2 : mute(); break			// toggles mute/unmute
		case 3 : numericKeyPad(); break
		case 4 : Return(); break		//	Goes back one level on apps. I.e., if playing, goes to
										//	app main screen
		case 6 : artMode(); break
		case 7 : ambientMode(); break
		case 8 : arrowLeft(); break
		case 9 : arrowRight(); break
		case 10: arrowUp(); break
		case 11: arrowDown(); break
		case 12: enter(); break			//	Executes selected OSD item (both apps and tv menus.
		case 13: exit(); break			//	Exits last selected item (if playing in app, will exit
										//	and return to app main screen.  If on main screen, exits app.
		case 14: home(); break			//	toggles on/off the TV's smart control page.
		case 18: channelUp(); break
		case 19: channelDown(); break
		case 20: guide(); break
		case 21: volumeUp(); break
		case 22: volumeDown(); break
		//	===== Direct Access Functions
		case 23: menu(); break			//	Calls upd OSD setting menu for TV.
		case 26: channelList(); break
		case 27: play(); break			//	Player control (playing app media that is not streaming
		case 28: pause(); break			//	(i.e., a recording in app).
		case 29: stop(); break			//	Exits the playing recording, returns to menu
		//	===== Other Commands =====
		case 34: previousChannel(); break	//	aka back
		case 35: sourceToggle(); break		//	Changed from hdmi().  Goes to next active source.
		case 36: fastBack(); break		//	player control
		case 37: fastForward(); break	//	player control
		//	===== Application Interface =====
		case 42: toggleSoundMode(); break		//	ST function
		case 43: togglePictureMode(); break		//	ST function
		case 44: 
			// Allows opening an app by name.  Enter using dashboard tile variable
			//	which creates the attribute variable.
			def variable = device.currentValue("variable")			
			if (variable == null || variable == " ") {
				logWarn("{button44: error: null variable, action: use setVariable to enter the variable]")
			} else {
				appOpenByName(variable)
			}
			sendEvent(name: "variable", value: " ")
			break
		
		case 45:
			//	TV Channel Preset Function.  Allows adding TV channels without the
			//	SmartThing interface.
			def variable = device.currentValue("variable")			
			if (variable == null || variable == " ") {
				logWarn("{button45: error: null variable, action: use setVariable to enter the variable]")
				logWarn("{button45: variable must be in format PresetNo, TVCHANNEL, TVCHANNELTITLE]]")
			} else {
				def arr = variable.split(",")
				if (arr.size() == 3) {
					try {
						arr[0].trim().toInteger()
						arr[1].trim().toInteger()
						presetCreateTV(arr[0].trim(), arr[1].trim(), arr[2].trim())
					} catch (err) {
						logWarn("{button45: [variable: ${variable}, error: must be in format PresetNo, TVCHANNEL, TVCHANNELTITLE]]")
					}
				} else {
					logWarn("{button45: [variable: ${variable}, error: must be in format PresetNo, TVCHANNEL, TVCHANNELTITLE]]")
				}
			}
			sendEvent(name: "variable", value: " ")
			break
		case 46:
			//	TV Channel set.  Must first enter variable.
			def variable = device.currentValue("variable")			
			if (variable == null || variable == " ") {
				logWarn("{button46: error: null variable, action: use setVariable to enter the variable]")
			} else {
				channelSet(variable)
			}
			sendEvent(name: "variable", value: " ")
			break
		//	Trigger function.  Once pressed, the preset buttons become ADD for 5 seconds.
		case 50: presetUpdateNext(); break
		case 51: presetExecute("1"); break
		case 52: presetExecute("2"); break
		case 53: presetExecute("3"); break
		case 54: presetExecute("4"); break
		case 55: presetExecute("5"); break
		case 56: presetExecute("6"); break
		case 57: presetExecute("7"); break
		case 58: presetExecute("8"); break
		case 59: presetExecute("9"); break
		case 60: presetExecute("10"); break
		default:
			logDebug("push: Invalid Button Number!")
			break
	}
}

//	===== Libraries =====
//#include davegut.samsungTvTEST








// ~~~~~ start include (89) davegut.samsungTvWebsocket ~~~~~
library ( 
	name: "samsungTvWebsocket", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Common Samsung TV Websocket Commands", 
	category: "utilities", 
	documentationLink: "" 
) 

import groovy.json.JsonOutput 

command "webSocketClose" 
command "webSocketOpen" 
command "close" 
attribute "wsStatus", "string" 
if (getDataValue("frameTv") == "true") { 
	command "artMode" 
	attribute "artModeStatus", "string" 
} 
command "ambientMode" 
//	Remote Control Keys (samsungTV-Keys) 
command "pause" 
command "play" 
command "stop" 
command "sendKey", ["string"] 
//	Cursor and Entry Control 
command "arrowLeft" 
command "arrowRight" 
command "arrowUp" 
command "arrowDown" 
command "enter" 
command "numericKeyPad" 
//	Menu Access 
command "home" 
command "menu" 
command "guide" 
//command "info"	//  enter 
//	Source Commands 
command "sourceSetOSD" 
command "sourceToggle" 
//command "hdmi" 
//	TV Channel 
command "channelList" 
command "channelUp" 
command "channelDown" 
command "channelSet", ["string"] 
command "previousChannel" 
//	Playing Navigation Commands 
command "exit" 
command "Return" 
command "fastBack" 
command "fastForward" 

//	== ART/Ambient Mode 
def artMode() { 
	def artModeStatus = device.currentValue("artModeStatus") 
	def logData = [artModeStatus: artModeStatus, artModeWs: state.artModeWs] 
	if (getDataValue("frameTv") != "true") { 
		logData << [status: "Not a Frame TV"] 
	} else if (artModeStatus == "on") { 
		logData << [status: "artMode already set"] 
	} else { 
		if (state.artModeWs) { 
			def data = [value:"on", 
						request:"set_artmode_status", 
						id: "${getDataValue("uuid")}"] 
			data = JsonOutput.toJson(data) 
			artModeCmd(data) 
			logData << [status: "Sending artMode WS Command"] 
		} else { 
			sendKey("POWER") 
			logData << [status: "Sending Power WS Command"] 
			if (artModeStatus == "none") { 
				logData << [NOTE: "SENT BLIND. Enable SmartThings interface!"] 
			} 
		} 
		runIn(10, getArtModeStatus) 
	} 
	logInfo("artMode: ${logData}") 
} 

def getArtModeStatus() { 
	if (getDataValue("frameTv") == "true") { 
		if (state.artModeWs) { 
			def data = [request:"get_artmode_status", 
						id: "${getDataValue("uuid")}"] 
			data = JsonOutput.toJson(data) 
			artModeCmd(data) 
		} else { 
			refresh() 
		} 
	} 
} 

def artModeCmd(data) { 
	def cmdData = [method:"ms.channel.emit", 
				   params:[data:"${data}", 
						   to:"host", 
						   event:"art_app_request"]] 
	cmdData = JsonOutput.toJson(cmdData) 
	sendMessage("frameArt", cmdData) 
} 

def ambientMode() { 
	sendKey("AMBIENT") 
	runIn(10, refresh) 
} 

//	== Remote Commands 
def mute() { sendKeyThenRefresh("MUTE") } 

def unmute() { mute() } 

def volumeUp() { sendKeyThenRefresh("VOLUP") } 

def volumeDown() { sendKeyThenRefresh("VOLDOWN") } 

def play() { sendKeyThenRefresh("PLAY") } 

def pause() { sendKeyThenRefresh("PAUSE") } 

def stop() { sendKeyThenRefresh("STOP") } 

def exit() { sendKeyThenRefresh("EXIT") } 

def Return() { sendKeyThenRefresh("RETURN") } 

def fastBack() { 
	sendKey("LEFT", "Press") 
	pauseExecution(1000) 
	sendKey("LEFT", "Release") 
} 

def fastForward() { 
	sendKey("RIGHT", "Press") 
	pauseExecution(1000) 
	sendKey("RIGHT", "Release") 
} 

def arrowLeft() { sendKey("LEFT") } 

def arrowRight() { sendKey("RIGHT") } 

def arrowUp() { sendKey("UP") } 

def arrowDown() { sendKey("DOWN") } 

def enter() { sendKeyThenRefresh("ENTER") } 

def numericKeyPad() { sendKey("MORE") } 

def home() { sendKey("HOME") } 

def menu() { sendKey("MENU") } 

def guide() { sendKey("GUIDE") } 

def info() { enter() } 

def source() { sourceSetOSD() } 
def sourceSetOSD() { sendKey("SOURCE") } 

def hdmi() { sourceToggle() } 
def sourceToggle() { sendKeyThenRefresh("HDMI") } 

def channelList() { sendKey("CH_LIST") } 

def channelUp() { sendKeyThenRefresh("CHUP") } 
def nextTrack() { channelUp() } 

def channelDown() { sendKeyThenRefresh("CHDOWN") } 
def previousTrack() { channelDown() } 

//	Uses ST interface if available. 
def channelSet(channel) { 
	if (connectST) { 
		setTvChannel(channel) 
	} else { 
		for (int i = 0; i < channel.length(); i++) { 
			sendKey(channel[i]) 
		} 
		enter() 
		sendEvent(name: "tvChannel", value: channel) 
	} 
} 

def previousChannel() { sendKeyThenRefresh("PRECH") } 

def showMessage() { logWarn("showMessage: not implemented") } 

//	== WebSocket Communications / Parse 
def sendKeyThenRefresh(key) { 
	sendKey(key) 
	if (connectST) { runIn(3, deviceRefresh) } 
} 

def sendKey(key, cmd = "Click") { 
	key = "KEY_${key.toUpperCase()}" 
	def data = [method:"ms.remote.control", 
				params:[Cmd:"${cmd}", 
						DataOfCmd:"${key}", 
						TypeOfRemote:"SendRemoteKey"]] 
	sendMessage("remote", JsonOutput.toJson(data).toString() ) 
} 

def xxxsendMessage(funct, data) { 
	def wsStat = device.currentValue("wsStatus") 
	logDebug("sendMessage: [wsStatus: ${wsStat}, function: ${funct}, data: ${data}, connectType: ${state.currentFunction}") 
	if (wsStat != "open" || state.currentFunction != funct) { 
		connect(funct) 
		pauseExecution(500) 
	} 
	interfaces.webSocket.sendMessage(data) 
	// runIn(600, close) 
} 

def sendMessage(funct, data) { 
	def wsStat = device.currentValue("wsStatus") 
	Map logData = [method: "sendMessage", wsStat: wsStat, funct: funct, data: data] 
	logDebug("sendMessage: [wsStatus: ${wsStat}, function: ${funct}, data: ${data}, connectType: ${state.currentFunction}") 
	if (wsStat == "open" && state.currentFunction == funct) { 
		execMessage(data) 
		logData << [action: "execMessage"] 
	} else { 
		if (wsStat == "open") { close() } 
		state.wsData = data 
		def await = connect(funct) 
		// runIn(600, close)	//	close ws after 5 minutes. 
		logData << [action: "connect"] 
	} 
	logDebug(logData) 
} 
def execMessage(data) { 
	interfaces.webSocket.sendMessage(data) 
} 

def webSocketOpen() { connect("remote") } 
def webSocketClose() { close() } 

def connect(funct) { 
	logDebug("connect: function = ${funct}") 
	def url 
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ==" 
	if (getDataValue("tokenSupport") == "true") { 
		if (funct == "remote") { 
			url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${state.token}" 
		} else if (funct == "frameArt") { 
			url = "wss://${deviceIp}:8002/api/v2/channels/com.samsung.art-app?name=${name}&token=${state.token}" 
		} else { 
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = true") 
		} 
	} else { 
		if (funct == "remote") { 
			url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}" 
		} else if (funct == "frameArt") { 
			url = "ws://${deviceIp}:8001/api/v2/channels/com.samsung.art-app?name=${name}" 
		} else { 
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = false") 
		} 
	} 
	state.currentFunction = funct 
	interfaces.webSocket.connect(url, ignoreSSLIssues: true) 
	return 
} 

def close() { 
	logDebug("close") 
	interfaces.webSocket.close() 
	sendEvent(name: "wsStatus", value: "closed") 
} 

def webSocketStatus(message) { 
	def status 
	Map logData = [method: "webSocketStatus"] 
	if (message == "status: open") { 
		status = "open" 
		if (state.wsData != "") { 
			execMessage(state.wsData) 
			state.wsData = "" 
			logData << [action: "execMessage"] 
		} 
	} else if (message == "status: closing") { 
		status = "closed" 
		state.currentFunction = "close" 
	} else if (message.substring(0,7) == "failure") { 
		status = "closed-failure" 
		state.currentFunction = "close" 
		close() 
	} 
	sendEvent(name: "wsStatus", value: status) 
	logData << [wsStatus: status] 
	logDebug(logData) 
} 

// Added by ELFEGE
def keepAlive() {
	log.info "keepAlive()..."
    if (device.currentValue("wsStatus") == "open") {
        // ping or any safe message
        interfaces.webSocket.sendMessage("ping")  
    }
    // schedule again in, say, 2 minutes
    runIn(120, keepAlive)
}



def parse(resp) { 
	def logData = [method: "parse"] 
	try { 
		resp = parseJson(resp) 
		def event = resp.event 
		logData << [EVENT: event] 
		switch(event) { 
			case "ms.channel.connect": 
				def newToken = resp.data.token 
				if (newToken != null && newToken != state.token) { 
					state.token = newToken 
					logData << [TOKEN: "updated"] 
				} else { 
					logData << [TOKEN: "noChange"] 
				} 
				break 
			case "d2d_service_message": 
				def data = parseJson(resp.data) 
				if (data.event == "artmode_status" || 
					data.event == "art_mode_changed") { 
					def status = data.value 
					if (status == null) { status = data.status } 
					sendEvent(name: "artModeStatus", value: status) 
					logData << [artModeStatus: status] 
					state.artModeWs = true 
				} 
				break 
			case "ms.error": 
			case "ms.channel.ready": 
			case "ms.channel.clientConnect": 
			case "ms.channel.clientDisconnect": 
			case "ms.remote.touchEnable": 
			case "ms.remote.touchDisable": 
				break 
			default: 
				logData << [STATUS: "Not Parsed", DATA: resp.data] 
				break 
		} 
		logDebug(logData) 
	} catch (e) { 
		logData << [STATUS: "unhandled", ERROR: e] 
		logWarn(logData) 
	} 
} 

// ~~~~~ end include (89) davegut.samsungTvWebsocket ~~~~~

// ~~~~~ start include (88) davegut.samsungTvApps ~~~~~
library ( 
	name: "samsungTvApps", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Samsung TV Applications", 
	category: "utilities", 
	documentationLink: "" 
) 

import groovy.json.JsonSlurper 

command "appOpenByName", ["string"] 
command "appClose" 
attribute "nowPlaying", "string" 
attribute "appName", "string" 
attribute "appId", "string" 
attribute "tvChannel", "string" 
attribute "tvChannelName", "string" 

def tvAppsPreferences() { 
	input ("findAppCodes", "enum", title: "Scan for App Codes (takes 10 minutes)",  
		   options: ["off", "startOver", "find"], defaultValue: "off") 
} 

def appOpenByName(appName) { 
	def logData = [method: "appOpenByName"] 
	def thisApp = state.appData.find { it.key.toLowerCase().contains(appName.toLowerCase()) } 
	if (thisApp != null) { 
		def appId = thisApp.value 
		appName = thisApp.key 
		logData << [appName: appName, appId: appId] 
		def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}" 
		try { 
			httpPost(uri, body) { resp -> 
				logData << [status: resp.statusLine, data: resp.data, success: resp.success] 
				if (resp.status == 200) { 
					if (connectST) { runIn(10, deviceRefresh) } 
					sendEvent(name: "appName", value: appName) 
					sendEvent(name: "appId", value: appId) 
					logDebug(logData) 
				} else { 
					logWarn(logData) 
				} 
			} 
			logDebug(logData) 
		} catch (err) { 
			logData << [status: "httpPost error", data: err] 
			logWarn(logData) 
		} 
	} else { 
		logData << [error: "appId is null"] 
		logWarn(logData) 
	} 
} 

def appClose(appId = device.currentValue("appId")) { 
	def logData = [method: "appClose", appId: appId] 
	if (appId == null || appId == " ") { 
		logData << [status: "appId is null", action: "try exit()"] 
		sendEvent(name: "appName", value: " ") 
		sendEvent(name: "appId", value: " ") 
		if (connectST) { runIn(5, deviceRefresh) } 
	} else { 
		logData << [status: "sending appClose"] 
		Map params = [uri: "http://${deviceIp}:8001/api/v2/applications/${appId}", 
					  timeout: 3] 
		asynchttpDelete("appCloseParse", params, [appId: appId]) 
	} 
	logDebug(logData) 
} 

def appCloseParse(resp, data) { 
	Map logData = [method: "appCloseParse", data: data] 
	if (resp.status == 200 && resp.json == true) { 
		logData << [status: resp.status, success: resp.json] 
		logDebug(logData) 
	} else { 
		logData << [status: resp.status, success: "false", action: "exit()"] 
		exit() 
		exit() 
		logWarn(logData) 
	} 
	if (connectST) { runIn(5, deviceRefresh) } 
	sendEvent(name: "appName", value: " ") 
	sendEvent(name: "appId", value: " ") 
} 

def updateAppCodes() { 
	Map logData = [method: "updateAppCodes", findAppCodes: findAppCodes] 
	if (findAppCodes != "off" &&  
		device.currentValue("switch") == "on") { 
		device.updateSetting("findAppCodes", [type:"enum", value: "off"]) 
		if (findAppCodes == "startOver") {  
			state.appData = [:] 
			logData << [appData: "reset"] 
		} 
		unschedule("onPoll") 
		state.appsInstalled = [] 
		def appIds = appIdList() 
		def appId = 0 
		logData << [codesToCheck: appIds.size(), status: "OK"] 
		runIn(5, getAppData) 
		logInfo(logData) 
	} else if (device.currentValue("switch") == "off") { 
		logData << [status: "FAILED", reason: "tv off"] 
		logWarn(logData) 
	} 
	return logData 
} 

def getAppData(appId = 0) { 
	Map logData = [method: "getAppData", appId: appId] 
	def appIds = appIdList() 
	if (appId < appIds.size()) { 
		def appCode = appIds[appId] 
		logData << [appCode: appCode] 
		def thisDevice = state.appData.find { it.value == appCode } 
		if (thisDevice != null) { 
			logData << [thisDevice: thisDevice, status: "Already in appData"] 
			appId = appId + 1 
			runInMillis(100, getAppData, [data: appId]) 
		} else { 
			logData << [status: "looking for App"] 
			Map params = [uri: "http://${deviceIp}:8001/api/v2/applications/${appCode}", 
						  timeout: 10] 
			asynchttpGet("parseGetAppData", params, [appId:appId, appCode: appCode]) 
		} 
		logDebug(logData) 
	} else { 
		runIn(5, setOnPollInterval) 
		logData << [status: "Done finding", totalApps: state.appData.size(), appsInstalled: state.appsInstalled] 
		state.remove("appsInstalled") 
		state.remove("retry") 
		logInfo(logData) 
	} 
} 

def parseGetAppData(resp, data) { 
	Map logData = [method: "parseGetAppData", data: data, status: resp.status] 
	if (resp.status == 200) { 
		def respData = new JsonSlurper().parseText(resp.data) 
		String name = shortenName(respData.name) 
		logData << [name: name, status: "appAdded"] 
		state.appData << ["${name}": respData.id] 
		state.appsInstalled << name 
		logDebug(logData) 
		state.retry = false 
		runIn(1, getAppData, [data: data.appId + 1]) 
	} else if (resp.status == 404) { 
		logData << [status: "appNotAdded", reason: "not installed in TV"] 
		logDebug(logData) 
		state.retry = false 
		runIn(1, getAppData, [data: data.appId + 1]) 
	} else { 
		logData << [retry: state.retry, status: "appNotAdded", 
					reason: "invalid response from device"] 
		if (state.retry == false) { 
			logData << [action: "<b>RETRYING</b>"] 
			state.retry = true 
			runIn(5, getAppData, [data: data.appId]) 
		} else { 
			runIn(1, getAppData, [data: data.appId + 1]) 
		} 
		logWarn(logData) 
	} 
} 

def shortenName(name) { 
	if (name.contains(" - ")) { 
		name = name.substring(0, name.indexOf(" - ")) 
	} else if (name.contains(" by ")) { 
		name = name.substring(0, name.indexOf(" by ")) 
	} else if (name.contains(": ")) { 
		name = name.substring(0, name.indexOf(": ")) 
	} else if (name.contains(" | ")) { 
		name = name.substring(0, name.indexOf(" | ")) 
	} 
	return name 
} 

def appIdList() { 
	def appList = [ 
		"Nuvyyo0002.tablo", "5b8c3eb16b.BeamCTVDev", "kk8MbItQ0H.VUDU", "vYmY3ACVaa.emby",  
		"ZmmGjO6VKO.slingtv", "PvWgqxV3Xa.YouTubeTV", "LBUAQX1exg.Hulu",  
		"AQKO41xyKP.AmazonAlexa", "3KA0pm7a7V.TubiTV", "cj37Ni3qXM.HBONow", "gzcc4LRFBF.Peacock",  
		"9Ur5IzDKqV.TizenYouTube", "BjyffU0l9h.Stream", "3201907018807", "3201910019365",  
		"3201907018784", "kIciSQlYEM.plex", "ckfgqqzvt0.dplus", "H7DIeAitkn.DisneyNOW", 
		"MCmYXNxgcu.DisneyPlus", "tCyZuSsCVw.Britbox", "tzo5Zi4mCPv.fuboTV", "3HYANqBDJD.DFW", 
		"EYm8vc1St4.Philo", "N4St7cQBPD.SiriusXM", "sNUyBbfvHf.SpectrumTV", "rJeHak5zRg.Spotify", 
		"3KA0pm7a7V.TubiTV", "r1mzFxGfYe.E","3201606009684", "3201910019365", "3201807016597",  
		"3201601007625", "3201710015037", "3201908019041", "3201504001965", "3201907018784",  
		"org.tizen.browser", "org.tizen.primevideo", "org.tizen.netflix-app",  
		"com.samsung.tv.aria-video", "com.samsung.tv.gallery", "org.tizen.apple.apple-music", 
		"com.samsung.tv.store", 

		"3202203026841", "3202103023232", "3202103023185", "3202012022468", "3202012022421", 
		"3202011022316", "3202011022131", "3202010022098", "3202009021877", "3202008021577", 
		"3202008021462", "3202008021439", "3202007021336", "3202004020674", "3202004020626", 
		"3202003020365", "3201910019457", "3201910019449", "3201910019420", "3201910019378", 
		"3201910019354", "3201909019271", "3201909019175", "3201908019041", "3201908019022",  
		"3201907018786", "3201906018693", 
		"3201901017768", "3201901017640", "3201812017479", "3201810017091", "3201810017074", 
		"3201807016597", "3201806016432", "3201806016390", "3201806016381", "3201805016367", 
		"3201803015944", "3201803015934", "3201803015869", "3201711015226", "3201710015067", 
		"3201710015037", "3201710015016", "3201710014874", "3201710014866", "3201707014489", 
		"3201706014250", "3201706012478", "3201704012212", "3201704012147", "3201703012079", 
		"3201703012065", "3201703012029", "3201702011851", "3201612011418", "3201611011210", 
		"3201611011005", "3201611010983", "3201608010385", "3201608010191", "3201607010031", 
		"3201606009910", "3201606009798", "3201606009684", "3201604009182", "3201603008746", 
		"3201603008210", "3201602007865", "3201601007670", "3201601007625", "3201601007230", 
		"3201512006963", "3201512006785", "3201511006428", "3201510005981", "3201506003488", 
		"3201506003486", "3201506003175", "3201504001965", "121299000612", "121299000101", 
		"121299000089", "111399002220", "111399002034", "111399000741", "111299002148", 
		"111299001912", "111299000769", "111012010001", "11101200001", "11101000407", 
		"11091000000" 
	] 
	return appList 
} 

def updateAppName(tvName = device.currentValue("tvChannelName")) { 
	//	If the tvChannel is blank, the the name may reflect the appId 
	//	that is used by the device.  Thanks SmartThings. 
	String appId = " " 
	String appName = " " 
	Map logData = [method: "updateAppName", tvName: tvName] 
	//	There are some names that need translation based on known 
	//	idiosyncracies with the SmartThings implementation. 
	//	Go to translation table and if the translation exists, 
	//	set the appName to that value. 
	def tempName = transTable().find { it.key == tvName } 
	if (tempName != null) {  
		appName = tempName.value 
		logData << [tempName: tempName] 
	} 
	//	See if the name is in the app list.  If so, update here 
	//	and in states. 
	def thisApp = state.appData.find { it.key == appName } 
	if (thisApp) { 
		logData << [thisApp: thisApp] 
		appId = thisApp.value 
	} else { 
		Map params = [uri: "http://${deviceIp}:8001/api/v2/applications/${tvName}", 
				  	timeout: 10] 
		try { 
			httpGet(params) { resp -> 
				appId = resp.data.id 
				appName = shortenName(resp.data.name) 
				logData << [appId: appId, appName: appName] 
			} 
		}catch (err) { 
			logData << [error: err] 
		} 
		if (appId != "") { 
			state.appData << ["${appName}": appId] 
			logData << [appData: ["${appName}": appId]] 
		} 
	} 
	sendEvent(name: "appName", value: appName) 
	sendEvent(name: "appId", value: appId) 
	logDebug(logData) 
} 

def updateTitle() { 
	String tvChannel = device.currentValue("tvChannel") 
	String title = "${tvChannel}: ${device.currentValue("tvChannelName")}" 
	if (tvChannel == " ") { 
		title = "app: ${device.currentValue("appName")}" 
	} 
	sendEvent(name: "nowPlaying", value: title) 
} 

def transTable() { 
	def translations = [ 
		"org.tizen.primevideo": "Prime Video", 
		"org.tizen.netflix-app": "Netflix", 
		"org.tizen.browser": "Internet", 
		"com.samsung.tv.aria-video": "Apple TV", 
		"com.samsung.tv.gallery": "Gallery", 
		"org.tizen.apple.apple-music": "Apple Music" 
		] 
	return translations 
} 

// ~~~~~ end include (88) davegut.samsungTvApps ~~~~~

// ~~~~~ start include (93) davegut.samsungTvPresets ~~~~~
library ( 
	name: "samsungTvPresets", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Samsung TV Preset Implementation", 
	category: "utilities", 
	documentationLink: "" 
) 

command "presetUpdateNext" 
command "presetCreate", [ 
	[name: "Preset Number", type: "ENUM",  
	 constraints: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]]] 
command "presetExecute", [ 
	[name: "Preset Number", type: "ENUM", 
	 constraints: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]]] 
command "presetCreateTv", [ 
	[name: "Preset Number", type: "ENUM", 
	 constraints: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]], 
	[name: "tvChannel", type: "STRING"], 
	[name: "tvChannelName", type: "STRING"]] 
attribute "presetUpdateNext", "string" 

def presetUpdateNext() { 
	//	Sets up next presetExecute to update the preset selected 
	//	Has a 10 second timer to select preset to reset.  Will then 
	//	revert back to false 
	sendEvent(name: "presetUpdateNext", value: "true") 
	runIn(5, undoUpdate) 
} 
def undoUpdate() { sendEvent(name: "presetUpdateNext", value: "false") } 

def presetCreate(presetNumber) { 
	//	Called from Hubitat Device's page for TV or from presetExecute 
	//	when state.updateNextPreset is true 
	refresh() 
	pauseExecution(2000) 
	Map logData = [method: "presetCreate", presetNumber: presetNumber] 
	String appName = device.currentValue("appName") 
	String tvChannel = device.currentValue("tvChannel") 
	if (appName != " ") { 
		String appId = device.currentValue("appId") 
		presetCreateApp(presetNumber, appName, appId) 
		logData << [action: "appPresetCreate"] 
	} else if (tvChannel != " ") { 
		String tvChannelName = device.currentValue("tvChannelName") 
		presetCreateTv(presetNumber, tvChannel, tvChannelName) 
		logData << [action: "tvPresetCreate"] 
	} 
	logInfo(logData) 
} 

def presetCreateApp(presetNumber, appName, appId) { 
	Map logData = [method: "appPresetCreate", presetNumber: presetNumber, 
				   appName: appName, appId: appId] 
	Map thisPresetData = [type: "application", execute: appName, appId: appId] 
	logData << [thisPresetData: thisPresetData, status: "updating, check state to confirm"] 
	presetDataUpdate(presetNumber, thisPresetData) 
	logInfo(logData) 
} 

def presetCreateTv(presetNumber, tvChannel, tvChannelName) { 
	Map logData = [method: "resetCreateTv", presetNumber: presetNumber,  
				   tvChannel: tvChannel, tvChannelName: tvChannelName] 
	Map thisPresetData = [type: "tvChannel", execute: tvChannel, tvChannelName: tvChannelName] 
	logData << [thisPresetData: thisPresetData, status: "updating, check state to confirm"] 
	presetDataUpdate(presetNumber, thisPresetData) 
	logInfo(logData) 
} 

def presetDataUpdate(presetNumber, thisPresetData) { 
	Map presetData = state.presetData 
	state.remove("presetData") 
	if (presetData == null) { presetData = [:] } 
	if (presetData.find{it.key == presetNumber}) { 
		presetData.remove(presetNumber) 
	} 
	presetData << ["${presetNumber}": thisPresetData] 
	state.presetData = presetData 
} 

def presetExecute(presetNumber) { 
	Map logData = [method: "presetExecute", presetNumber: presetNumber] 
	if (device.currentValue("presetUpdateNext") == "true") { 
		sendEvent(name: "presetUpdateNext", value: "false") 
		logData << [action: "presetCreate"] 
		presetCreate(presetNumber) 
	} else { 
		def thisPreset = state.presetData.find { it.key == presetNumber } 
		if (thisPreset == null) { 
			logData << [error: "presetNotSet"] 
			logWarn(logData) 
		} else { 
			def execute = thisPreset.value.execute 
			def presetType = thisPreset.value.type 
			if (presetType == "application") { 
				//	Simply open the app. 
				appOpenByName(execute) 
				sendEvent(name: "appId", value: thisPreset.value.appId) 
				sendEvent(name: "appName", value: execute) 
				sendEvent(name: "tvChannel", value: " ") 
				sendEvent(name: "tvChannelName", value: " ") 
				logData << [appName: execute, appId: thisPreset.value.appId] 
			} else if (presetType == "tvChannel") { 
				//	Close running app the update channel 
				if (!ST && device.currentValue("appId") != " ") { 
					appClose() 
					pauseExecution(7000) 
				} 
				channelSet(execute) 
				sendEvent(name: "appId", value: " ") 
				sendEvent(name: "appName", value: " ") 
				sendEvent(name: "tvChannel", value: execute) 
				sendEvent(name: "tvChannelName", value: thisPreset.value.tvChannelName) 
				logData << [tvChannel: tvChannel, tvChannelName: thisPreset.value.tvChannelName] 
			} else { 
				logData << [error: "invalid preset type"] 
				logWarn(logData) 
			} 
		} 
		runIn(2, updateTitle) 
	} 
	logDebug(logData) 
} 


// ~~~~~ end include (93) davegut.samsungTvPresets ~~~~~

// ~~~~~ start include (91) davegut.SmartThingsInterface ~~~~~
library ( 
	name: "SmartThingsInterface", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Samsung TV SmartThings Capabilities", 
	category: "utilities", 
	documentationLink: "" 
) 

def stPreferences() { 
	input ("connectST", "bool", title: "Connect to SmartThings for added functions", defaultValue: false) 
	if (connectST) { 
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "") 
		if (stApiKey) { 
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "") 
		} 
		input ("stPollInterval", "enum", title: "SmartThings Poll Interval (minutes)", 
			   options: ["off", "1", "5", "15", "30"], defaultValue: "15") 
		input ("stTestData", "bool", title: "Get ST data dump for developer", defaultValue: false) 
	} 
} 

def stUpdate() { 
	def stData = [:] 
	if (connectST) { 
		stData << [connectST: "true"] 
		stData << [connectST: connectST] 
		if (!stApiKey || stApiKey == "") { 
			logWarn("\n\n\t\t<b>Enter the ST API Key and Save Preferences</b>\n\n") 
			stData << [status: "ERROR", date: "no stApiKey"] 
		} else if (!stDeviceId || stDeviceId == "") { 
			getDeviceList() 
			logWarn("\n\n\t\t<b>Enter the deviceId from the log List and Save Preferences</b>\n\n") 
			stData << [status: "ERROR", date: "no stDeviceId"] 
		} else { 
			def stPollInterval = stPollInterval 
			if (stPollInterval == null) {  
				stPollInterval = "15" 
				device.updateSetting("stPollInterval", [type:"enum", value: "15"]) 
			} 
			switch(stPollInterval) { 
				case "1" : runEvery1Minute(refresh); break 
				case "5" : runEvery5Minutes(refresh); break 
				case "15" : runEvery15Minutes(refresh); break 
				case "30" : runEvery30Minutes(refresh); break 
				default: unschedule("refresh") 
			} 
			deviceSetup() 
			stData << [stPollInterval: stPollInterval] 
		} 
	} else { 
		stData << [connectST: "false"] 
	} 
	logInfo("stUpdate: ${stData}") 
} 

def deviceSetup() { 
	if (!stDeviceId || stDeviceId.trim() == "") { 
		respData = "[status: FAILED, data: no stDeviceId]" 
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") 
	} else { 
		def sendData = [ 
			path: "/devices/${stDeviceId.trim()}/status", 
			parse: "distResp" 
			] 
		asyncGet(sendData, "deviceSetup") 
	} 
} 

def getDeviceList() { 
	def sendData = [ 
		path: "/devices", 
		parse: "getDeviceListParse" 
		] 
	asyncGet(sendData) 
} 

def getDeviceListParse(resp, data) { 
	def respData 
	if (resp.status != 200) { 
		respData = [status: "ERROR", 
					httpCode: resp.status, 
					errorMsg: resp.errorMessage] 
	} else { 
		try { 
			respData = new JsonSlurper().parseText(resp.data) 
		} catch (err) { 
			respData = [status: "ERROR", 
						errorMsg: err, 
						respData: resp.data] 
		} 
	} 
	if (respData.status == "ERROR") { 
		logWarn("getDeviceListParse: ${respData}") 
	} else { 
		log.info "" 
		respData.items.each { 
			log.trace "${it.label}:   ${it.deviceId}" 
		} 
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" 
	} 
} 

def deviceSetupParse(mainData) { 
	def setupData = [:] 

	def pictureModes = mainData["custom.picturemode"].supportedPictureModes.value 
	state.pictureModes = pictureModes 
	setupData << [pictureModes: pictureModes] 

	def soundModes =  mainData["custom.soundmode"].supportedSoundModes.value 
	state.soundModes = soundModes 
	setupData << [soundModes: soundModes] 

	logInfo("deviceSetupParse: ${setupData}") 
} 

def deviceCommand(cmdData) { 
	logDebug("deviceCommand: $cmdData") 
	def respData = [:] 
	if (!stDeviceId || stDeviceId.trim() == "") { 
		respData << [status: "FAILED", data: "no stDeviceId"] 
	} else { 
		def sendData = [ 
			path: "/devices/${stDeviceId.trim()}/commands", 
			cmdData: cmdData 
		] 
		respData = syncPost(sendData) 
	} 
	if (respData.status == "OK") { 
		if (cmdData.capability && cmdData.capability != "refresh") { 
			deviceRefresh() 
		} else { 
			poll() 
		} 
	}else { 
		logWarn("deviceCommand: [status: ${respData.status}, data: ${respData}]") 
		if (respData.toString().contains("Conflict")) { 
			logWarn("<b>Conflict internal to SmartThings.  Device may be offline in SmartThings</b>") 
		} 
	} 
} 

def statusParse(mainData) { 
	Map logData = [method: "statusParse"] 
	if (stTestData) { 
		device.updateSetting("stTestData", [type:"bool", value: false]) 
		Map testData = [stTestData: mainData] 
	} 
	String onOff = mainData.switch.switch.value 
	Map parseResults = [:] 
	if (onOff == "on") { 
		Integer volume = mainData.audioVolume.volume.value.toInteger() 
		sendEvent(name: "volume", value: volume) 
		sendEvent(name: "level", value: volume) 
		parseResults << [volume: volume] 

		String mute = mainData.audioMute.mute.value 
		sendEvent(name: "mute", value: mute) 
		parseResults << [mute: mute] 

		String inputSource = mainData.mediaInputSource.inputSource.value 
		sendEvent(name: "inputSource", value: inputSource)		 
		parseResults << [inputSource: inputSource] 

		String tvChannel = mainData.tvChannel.tvChannel.value 
		if (tvChannel == null) { tvChannel = " " } 
		String tvChannelName = mainData.tvChannel.tvChannelName.value 
		parseResults << [tvChannel: tvChannel, tvChannelName: tvChannelName] 
		if (tvChannel == " " && tvChannelName != device.currentValue("tvChannelName")) { 
			//	tvChannel indicates app, tvChannelName is thrn spp code (ST Version) 
			if (tvChannelName.contains(".")) { 
				runIn(2, updateAppName) 
			} 
		} 
		sendEvent(name: "tvChannel", value: tvChannel) 
		sendEvent(name: "tvChannelName", value: tvChannelName) 

		String pictureMode = mainData["custom.picturemode"].pictureMode.value 
		sendEvent(name: "pictureMode",value: pictureMode) 
		parseResults << [pictureMode: pictureMode] 

		String soundMode = mainData["custom.soundmode"].soundMode.value 
		sendEvent(name: "soundMode",value: soundMode) 
		parseResults << [soundMode: soundMode] 
	} 
	logDebug(logData) 
} 

private asyncGet(sendData, passData = "none") { 
	if (!stApiKey || stApiKey.trim() == "") { 
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") 
	} else { 
		logDebug("asyncGet: ${sendData}, ${passData}") 
		def sendCmdParams = [ 
			uri: "https://api.smartthings.com/v1", 
			path: sendData.path, 
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] 
		try { 
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) 
		} catch (error) { 
			logWarn("asyncGet: [status: FAILED, errorMsg: ${error}]") 
		} 
	} 
} 

private syncGet(path){ 
	def respData = [:] 
	if (!stApiKey || stApiKey.trim() == "") { 
		respData << [status: "FAILED", 
					 errorMsg: "No stApiKey"] 
	} else { 
		logDebug("syncGet: ${sendData}") 
		def sendCmdParams = [ 
			uri: "https://api.smartthings.com/v1", 
			path: path, 
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] 
		] 
		try { 
			httpGet(sendCmdParams) {resp -> 
				if (resp.status == 200 && resp.data != null) { 
					respData << [status: "OK", results: resp.data] 
				} else { 
					respData << [status: "FAILED", 
								 httpCode: resp.status, 
								 errorMsg: resp.errorMessage] 
				} 
			} 
		} catch (error) { 
			respData << [status: "FAILED", 
						 errorMsg: error] 
		} 
	} 
	return respData 
} 

private syncPost(sendData){ 
	def respData = [:] 
	if (!stApiKey || stApiKey.trim() == "") { 
		respData << [status: "FAILED", 
					 errorMsg: "No stApiKey"] 
	} else { 
		logDebug("syncPost: ${sendData}") 
		def cmdBody = [commands: [sendData.cmdData]] 
		def sendCmdParams = [ 
			uri: "https://api.smartthings.com/v1", 
			path: sendData.path, 
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], 
			body : new groovy.json.JsonBuilder(cmdBody).toString() 
		] 
		try { 
			httpPost(sendCmdParams) {resp -> 
				if (resp.status == 200 && resp.data != null) { 
					respData << [status: "OK", results: resp.data.results] 
				} else { 
					respData << [status: "FAILED", 
								 httpCode: resp.status, 
								 errorMsg: resp.errorMessage] 
				} 
			} 
		} catch (error) { 
			respData << [status: "FAILED", 
						 errorMsg: error] 
		} 
	} 
	return respData 
} 

def distResp(resp, data) { 
	def resplog = [:] 
	if (resp.status == 200) { 
		try { 
			def respData = new JsonSlurper().parseText(resp.data) 
			if (data.reason == "deviceSetup") { 
				deviceSetupParse(respData.components.main) 
				runIn(1, statusParse, [data: respData.components.main]) 
			} else { 
				statusParse(respData.components.main) 
			} 
		} catch (err) { 
			resplog << [status: "ERROR", 
						errorMsg: err, 
						respData: resp.data] 
		} 
	} else { 
		resplog << [status: "ERROR", 
					httpCode: resp.status, 
					errorMsg: resp.errorMessage] 
	} 
	if (resplog != [:]) { 
		logWarn("distResp: ${resplog}") 
	} 
} 

// ~~~~~ end include (91) davegut.SmartThingsInterface ~~~~~

// ~~~~~ start include (90) davegut.samsungTvST ~~~~~
library ( 
	name: "samsungTvST", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Samsung TV SmartThings Capabilities", 
	category: "utilities", 
	documentationLink: "" 
) 

command "toggleSoundMode", [[name: "SmartThings Function"]] 
command "togglePictureMode", [[name: "SmartThings Function"]] 
command "sourceSetST", ["SmartThings Function"] 
attribute "inputSource", "string" 
command "setVolume", ["SmartThings Function"] 
command "setPictureMode", ["SmartThings Function"] 
command "setSoundMode", ["SmartThings Function"] 
command "setLevel", ["SmartThings Function"] 
attribute "level", "NUMBER" 

def deviceRefresh() { 
	if (connectST && stApiKey!= null) { 
		def cmdData = [ 
			component: "main", 
			capability: "refresh", 
			command: "refresh", 
			arguments: []] 
		deviceCommand(cmdData) 
	} 
} 

def poll() { 
	if (!stDeviceId || stDeviceId.trim() == "") { 
		respData = "[status: FAILED, data: no stDeviceId]" 
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") 
	} else { 
		def sendData = [ 
			path: "/devices/${stDeviceId.trim()}/status", 
			parse: "distResp" 
			] 
		asyncGet(sendData, "statusParse") 
	} 
} 

def setLevel(level) { setVolume(level) } 

def setVolume(volume) { 
	def cmdData = [ 
		component: "main", 
		capability: "audioVolume", 
		command: "setVolume", 
		arguments: [volume.toInteger()]] 
	deviceCommand(cmdData) 
} 

def togglePictureMode() { 
	//	requires state.pictureModes 
	def pictureModes = state.pictureModes 
	def totalModes = pictureModes.size() 
	def currentMode = device.currentValue("pictureMode") 
	def modeNo = pictureModes.indexOf(currentMode) 
	def newModeNo = modeNo + 1 
	if (newModeNo == totalModes) { newModeNo = 0 } 
	def newPictureMode = pictureModes[newModeNo] 
	setPictureMode(newPictureMode) 
} 

def setPictureMode(pictureMode) { 
	def cmdData = [ 
		component: "main", 
		capability: "custom.picturemode", 
		command: "setPictureMode", 
		arguments: [pictureMode]] 
	deviceCommand(cmdData) 
} 

def toggleSoundMode() { 
	def soundModes = state.soundModes 
	def totalModes = soundModes.size() 
	def currentMode = device.currentValue("soundMode") 
	def modeNo = soundModes.indexOf(currentMode) 
	def newModeNo = modeNo + 1 
	if (newModeNo == totalModes) { newModeNo = 0 } 
	def soundMode = soundModes[newModeNo] 
	setSoundMode(soundMode) 
} 

def setSoundMode(soundMode) {  
	def cmdData = [ 
		component: "main", 
		capability: "custom.soundmode", 
		command: "setSoundMode", 
		arguments: [soundMode]] 
	deviceCommand(cmdData) 
} 

def toggleInputSource() { sourceToggle() } 

def setInputSource(inputSource) { sourceSetST(inputSource) } 
def sourceSetST(inputSource) { 
	def cmdData = [ 
		component: "main", 
		capability: "mediaInputSource", 
		command: "setInputSource", 
		arguments: [inputSource]] 
	deviceCommand(cmdData) 
} 

def setTvChannel(newChannel) { 
	def cmdData = [ 
		component: "main", 
		capability: "tvChannel", 
		command: "setTvChannel", 
		arguments: [newChannel]] 
	deviceCommand(cmdData) 
} 

// ~~~~~ end include (90) davegut.samsungTvST ~~~~~

// ~~~~~ start include (79) davegut.Logging ~~~~~
library ( 
	name: "Logging", 
	namespace: "davegut", 
	author: "Dave Gutheinz", 
	description: "Common Logging and info gathering Methods", 
	category: "utilities", 
	documentationLink: "" 
) 

def nameSpace() { return "davegut" } 

def version() { return "2.3.9b" } 

def label() { 
	if (device) {  
		return device.displayName + "-${version()}" 
	} else {  
		return app.getLabel() + "-${version()}" 
	} 
} 

def listAttributes() { 
	def attrData = device.getCurrentStates() 
	Map attrs = [:] 
	attrData.each { 
		attrs << ["${it.name}": it.value] 
	} 
	return attrs 
} 

def setLogsOff() { 
	def logData = [logEnable: logEnable] 
	if (logEnable) { 
		runIn(1800, debugLogOff) 
		logData << [debugLogOff: "scheduled"] 
	} 
	return logData 
} 

def logTrace(msg){ log.trace "${label()}: ${msg}" } 

def logInfo(msg) {  
	if (infoLog) { log.info "${label()}: ${msg}" } 
} 

def debugLogOff() { 
	if (device) { 
		device.updateSetting("logEnable", [type:"bool", value: false]) 
	} else { 
		app.updateSetting("logEnable", false) 
	} 
	logInfo("debugLogOff") 
} 

def logDebug(msg) { 
	if (logEnable) { log.debug "${label()}: ${msg}" } 
} 

def logWarn(msg) { log.warn "${label()}: ${msg}" } 

def logError(msg) { log.error "${label()}: ${msg}" } 

// ~~~~~ end include (79) davegut.Logging ~~~~~