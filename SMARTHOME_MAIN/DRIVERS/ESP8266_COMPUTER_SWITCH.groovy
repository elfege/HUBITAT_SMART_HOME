/**
*  Computer Swtich
*
*  
*/

metadata {
    definition (name: "Computer Switch", namespace: "elfege", author: "elfege")
    {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        capability "Valve"

        command "Sleep"
        command "ShutDown" 
        command "TurnOn"   
        command "override" // override failsafes for up to 10 hours
        command "toggleShutDownPermission"

        attribute "headline", "string"
        attribute "status", "string"
        attribute "computer", "string"


    }
    // Preferences
    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
    }
}


// parse events into attributes
def parse(String description) {

    state.lastEvent = state.lastEvent ? state.lastEvent : now()
    state.lastEventName = state.lastEventName ? state.lastEventName : ""
    state.lastEventValue = state.lastEventValue ? state.lastEventValue : ""

    //log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }
    def bodyString = msg.body
    //log.debug "bodyString $bodyString"
    state.allowshutDownWhenFail = state.allowshutDownWhenFail == null ? false : state.allowshutDownWhenFail
    state.lastReboot = state.lastReboot == null ? now() : state.lastReboot 


    def result = null
    def name = null
    def value = null
    if(!bodyString){
        log.debug "bodyString is null for some reason"
    }
    else {

        def parts = bodyString.split(" ")
        name  = parts.length>0?parts[0].trim():null
        value = parts.length>1?parts[1].trim():null

        def timeThreshold = 60000

        if(now() - state.lastEvent > timeThreshold || (name != state.lastEventName && value != state.lastEventValue)) 
        {
            state.lastEventName = name
            state.lastEventValue = value
            state.lastEvent = now()

            log.info "name:$name, value:$value"

            if(value in ["ALLOWED", "FORBIDDEN"])
            {
                //log.warn "SHUTDOWN STATUS REQUEST"
                state.allowshutDownWhenFail = value == "ALLOWED" ? true : false
            }

            sendEvent(name: name, value: value)
        }
        else
        {
            log.warn "duplicate event name and value less than ${timeThreshold/1000}seconds apart, skipping"
        }


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

// handle commands

def toggleShutDownPermission()
{
    state.allowshutDownWhenFail = !state.allowshutDownWhenFail
    runIn(1, updateShutDownPermission)

}
def updateShutDownPermission()
{
    def var = state.allowshutDownWhenFail ? "ALLOWSHUTDOWN" : "FORBIDSHUTDOWN"
    sendEthernet(var)
}
def allowShutDownUpdate() // 10 seconds after "SWITCH REBOOT" event
{
    log.trace "UPDATING DEVICE WITH state.allowshutDownWhenFail = TRUE"      
    sendEthernet("ALLOWSHUTDOWN")
}
def override(){
    log.debug "Executing 'override'"
    sendEvent(name: "headline", value: "override mode")
    sendEthernet("override")
}
def TurnOn() {
    log.debug "Executing 'switch on'"

    sendEvent(name: "switch", value: "on")
    sendEvent(name: "status", value: "turningon")
    sendEthernet("on")

}
def ShutDown(){
    log.debug "Executing 'switch off'"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "status", value: "shuttingdown")
    sendEthernet("off")
}
// for alexa
def on(){
    TurnOn()
}
def off(){
    Sleep()
}
def Sleep() {
    log.debug "Executing 'switch sleep'"

    sendEvent(name: "switch", value: "off")
    sendEvent(name: "status", value:"sleep")
    sendEthernet("sleep")
}



def refresh(){
    sendEthernet("refresh")
}

def configure() {
    log.debug "Executing 'configure'"
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
