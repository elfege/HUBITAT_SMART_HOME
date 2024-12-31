/**
*  PIR Motion Sensor ELFEGE
*
*  
*/

metadata {
    definition (name: "PIR motion sensor", namespace: "elfege", author: "elfege") {

        capability "Refresh"
        capability "Sensor"
        capability "Motion Sensor"
        capability "Configuration"

        command "reset"
    }

    simulator {

    }

    // Preferences
    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
        input "enablelogging", "bool", title: "Log debug", defaultValue: true, submitOnChange:true
    }

    // Tile Definitions
    tiles (scale: 1) {
        multiAttributeTile(name:"mainTile", type: "generic", width: 6, height: 4){
            tileAttribute("motion", key: "PRIMARY_CONTROL"){

                attributeState "inactive", label: "NO MOTION", backgroundColor:"#ffffff"
                attributeState "active", label: "MOTION", backgroundColor:"#00a0dc"
            }
        }


        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }  
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        standardTile("reset", "device.reset", inactiveLabel: false, decoration: "flat") {
            state "reset", label:'reset', action:"reset", icon:"st.secondary.reset"
        }
        main(["mainTile"])
        details(["mainTile","refresh","configure", "reset"])
    }
}

// create states


// parse events into attributes
def parse(String description) {
    //log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }
    def bodyString = msg.body

    //log.debug "bodyString ===================== $bodyString"


    if(!bodyString){
        //log.debug "bodyString is null for some reason"
    }
    else {
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null
        boolean isStateChange = false
        def timeLimit = 60*1000

        def Delay = 5000 as long

            if(!state.lastDeclaredEvent)
        {
            state.lastDeclaredEvent = now() as long
                }

        //log.trace "state.lastDeclaredEvent = $state.lastDeclaredEvent"
        boolean pastDelay = now() > (state.lastDeclaredEvent + Delay) 
        //log.info "pastDelay = $pastDelay" // state.lastDeclaredEvent = $state.lastDeclaredEvent"


        def evt1 = createEvent(name: name, value: value)     
        state.lastDeclaredEvent = now()
        log.info "${device.displayName} is $value " //||| state.lastValue = $state.lastValue"
        state.lastValue = value

        // Update lastUpdated date and time
        def nowDay = new Date().format("MMM dd", location.timeZone)
        def nowTime = new Date().format("h:mm a", location.timeZone)
        def evt2 = createEvent(name: "lastUpdated", value: nowDay + " " + nowTime, displayed: false)

        return [evt1, evt2]
    }


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

def configure() {
    log.debug "Executing 'configure'"
    state.lastDeclaredEvent = now()
    log.debug "state.lastDeclaredEvent = ${now()}"

    updateDeviceNetworkID()
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
    }
    else {
        log.trace "updated(): Ran within last 5 seconds so aborting."
    }

}

def refresh(){
    sendEthernet("refresh")
}

def reset() {
    log.debug "Executing 'reset'"
    sendEvent(name: "reset", value: "reset")
    sendEthernet("reset")
}