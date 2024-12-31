/**
*  from ST_Anything_Ethernet.device.groovy
*
*
*/

metadata {
    definition(name: 'Water Sensor ESP', namespace: 'elfege', author: 'elfege') {
        capability 'Configuration'
        capability 'Water Sensor'
        capability 'Temperature Measurement'
        capability "Temperature Measurement"
        capability "Sensor"
        capability "Refresh"
        
        attribute 'status', 'string'
        attribute 'temperature', 'number'

        command 'dry'
        command 'wet'
        command 'refresh'
    }

    preferences {
        input 'ip', 'text', title: 'Arduino IP Address', description: 'ip', required: true, displayDuringSetup: true
        input 'port', 'text', title: 'Arduino Port', description: 'port', required: true, displayDuringSetup: true
        input 'mac', 'text', title: 'Arduino MAC Addr', description: 'mac', required: true, displayDuringSetup: true
    }
}

def parse(String description) {
    def msg = parseLanMessage(description)
    def bodyString = msg.body

    if (bodyString) {
        if (logEnable) log.debug "BodyString: $bodyString"
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null

        if (name && value) {
            switch(name) {
                case "water":
                    if (value == "wet" || value == "dry") {
                        if (state.lastWaterState != value){
                            sendEvent(name: "water", value: value)
                            state.lastWaterState = value
                        }
                    }
                    break
                case "temperature":
                    def tempValue = value.toBigDecimal()
                    sendEvent(name: "temperature", value: tempValue, unit: "F")
                    break
                default:
                    log.warn "Unknown name: $name"
                    break
            }
        }
    }
}

private getHostAddress() {
    def ip = settings.ip
    def port = settings.port

    log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ':' + port
}

def sendEthernet(message) {
    log.debug "Executing 'sendEthernet' ${message}"
    new hubitat.device.HubAction(
        method: 'POST',
        path: "/${message}?",
        headers: [ HOST: "${getHostAddress()}" ]
    )
}

// handle commands

def poll() {
    //temporarily implement poll() to issue a configure() command to send the polling interval settings to the arduino
    configure()
}

def configure() {
    log.debug "Executing 'configure'"
    updateDeviceNetworkID()
}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    if (device.deviceNetworkId != mac) {
        log.debug "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
}

def updated() {
    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
        state.updatedLastRanAt = now()
        log.debug "Executing 'updated'"
        runIn(3, updateDeviceNetworkID)
    //sendEvent(name: "numberOfButtons", value: numButtons)
    }
    else {
        log.trace 'updated(): Ran within last 5 seconds so aborting.'
    }
}

def initialize() {
// sendEvent(name: "numberOfButtons", value: numButtons)
}

def dry() {
    log.debug 'Virtual Water Dry'
    sendEvent(name: 'water', value: 'dry', isStateChange: true)
}

def wet() {
    log.debug 'Virtual Water Wet'
    sendEvent(name: 'water', value: 'wet', isStateChange: true)
}
