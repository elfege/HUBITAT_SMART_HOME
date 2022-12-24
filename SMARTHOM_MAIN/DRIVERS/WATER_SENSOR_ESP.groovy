/**
*  from ST_Anything_Ethernet.device.groovy
*
*
*/

metadata {
    definition (name: "Water Sensor ESP", namespace: "elfege", author: "elfege") {
        capability "Configuration"
        capability "Water Sensor"

        attribute "status", "string"
        
        command "dry"
        command "wet"
    }

    simulator {

    }

    // Preferences
    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true    
        //input "waterSampleRate", "number", title: "Water Sensor Inputs", description: "Sampling Interval (seconds)", defaultValue: 30, required: true, displayDuringSetup: true
    }

    // Tile Definitions
    tiles {

        multiAttributeTile(name:"water", type: "generic", width: 6, height: 4){
            tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
                attributeState("dry", label: '${name}', icon:"st.alarm.water.dry", backgroundColor:"#ffffff")
                attributeState("wet", label: '${name}', icon:"st.alarm.water.wet", backgroundColor:"#00a0dc")
            }
            tileAttribute("device.lastUpdated", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'    Last updated ${currentValue}',icon: "st.Health & Wellness.health9")
            }
        }

        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }


        main(["water"])
        details(["water","configure"])
    }
}

// parse events into attributes
def parse(String description) {
    //log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        //log.debug "headerstring was null for some reason :("
    }

    def bodyString = msg.body
    def result = null

    if (bodyString) {
        
        log.debug "BodyString: $bodyString"
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null

        if(value == "wet" || value == "dry"){
            log.debug "sending status update for value = $value"
            //sendEvent(name: "status", value: value)
            result = createEvent(name: name, value: value)
            // Update lastUpdated date and time
            def nowDay = new Date().format("MMM dd", location.timeZone)
            def nowTime = new Date().format("h:mm a", location.timeZone)
            sendEvent(name: "lastUpdated", value: nowDay + " at " + nowTime, displayed: false)
        }
        else {
            result = createEvent(name: name, value: value)
        }
        log.debug result
        
    }
    return result
}

private getHostAddress() {
    def ip = settings.ip
    def port = settings.port

    log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ":" + port
}


def sendEthernet(message) {
    log.debug "Executing 'sendEthernet' ${message}"
    new hubitat.device.HubAction(
        method: "POST",
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
    // sendEvent(name: "numberOfButtons", value: numButtons)
    //log.debug "illuminance " + illuminanceSampleRate + "|temphumid " + temphumidSampleRate + "|water " + waterSampleRate
    // log.debug "water " + waterSampleRate
    // log.debug "illuminance " + illuminanceSampleRate
    // log.debug "temphumid " + temphumidSampleRate
    // [sendEthernet("water " + waterSampleRate)]


}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId!=mac) {
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
        log.trace "updated(): Ran within last 5 seconds so aborting."
    }
}

def initialize() {
    // sendEvent(name: "numberOfButtons", value: numButtons)
}

def dry() {
        log.debug "Virtual Water Dry"
        sendEvent(name: "water", value: "dry", isStateChange: true)
}

def wet() {
        log.debug "Virtual Water Wet"
        sendEvent(name: "water", value: "wet", isStateChange: true)
}