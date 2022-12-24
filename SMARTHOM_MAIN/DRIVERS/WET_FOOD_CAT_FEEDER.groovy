/**
*  PetSafe Cat dry food Feeder
*
*  
*/

metadata {
    definition (name: "WET FOOD CAT FEEDER", namespace: "elfege", author: "elfege") {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        //capability "refresh"
        //capability "Switch Level"

        command "stop"
        command "feed"
        command "refresh"
        command "reset"


        command "lock"
        command "unlock"
        command "opencan"       
        command "unload"
        command "push"
        command "pull"
        command "verticalpush"
        command "verticalpull"
        command "verticaldownstep"
        command "verticalupstep"
        command "poplid"
        command "compress"
        command "decompress"
        command "cosinuspush"
        command "cosinuspull"
        command "cosinusplus"
        command "cosinusminus"
        command "toggledebug"
        command "verticalpushsensor"


        attribute "locked", "String"
        attribute "unlocked", "String"



        attribute "lastUpdated", "String"
        attribute "feeding", "String"
        attribute "full", "String"
        attribute "empty", "String"

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

    //log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header


    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }
    def bodyString = msg.body

    log.debug "bodyString ===================== $bodyString"


    if(!bodyString){
        log.debug "bodyString is null for some reason"
    }
    else {
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null
        boolean isStateChange = false
        def timeLimit = 60*1000
        def timeAtlastEvent = state.lastDeclaredEvent.toInteger()
        def Now = now()
        boolean pastDelay = (Now - timeAtlastEvent) > now()

        if(name == "feeder" && value == "feeding")
        {

            def nowTime = new Date().format("h:mm a", location.timeZone)
            sendEvent(name: "lastfeedingtime", value: nowTime)
        }

        if(name != "rssi")
        {

            log.debug "sending event: name: $name, value: $value 56df"


            sendEvent(name: name, value: value, isStateChange: true)


            // Update lastUpdated date and time
            def nowDay = new Date().format("MMM dd", location.timeZone)
            def nowTime = new Date().format("h:mm a", location.timeZone)
            sendEvent(name: "lastUpdated", value: nowDay + " " + nowTime, displayed: false)

        }

    }
}

def on(){
    log.debug "Executing 'switch on'"
    sendEthernet("on")
}
def off(){
    log.debug "Executing 'switch off'"
    sendEthernet("off")
}
def feed(){
    sendEthernet("feed")
}
def stop(){
    sendEvent(name: "feeder", value: "stop")
    log.debug "Executing 'stop'"   
    sendEthernet("stop")
}
def unload(){
    sendEvent(name: "feeder", value: "unload")
    log.debug "Executing 'unload'"   
    sendEthernet("unload")
}
def lock(){
    sendEvent(name: "feeder", value: "lock")
    log.debug "Executing 'lock'"   
    sendEthernet("lock")
}
def unlock(){
    sendEvent(name: "feeder", value: "unlock")
    log.debug "Executing 'unlock'"   
    sendEthernet("unlock")
}
def opencan(){
    sendEvent(name: "feeder", value: "opencan")
    log.debug "Executing 'opencan'"   
    sendEthernet("opencan")
}
def push(){
    sendEvent(name: "feeder", value: "push")
    log.debug "Executing 'push'"   
    sendEthernet("push")
}
def pull(){
    sendEvent(name: "feeder", value: "pull")
    log.debug "Executing 'pull'"   
    sendEthernet("pull")
}
def verticalpush(){
    sendEvent(name: "feeder", value: "verticalpush")
    log.debug "Executing 'verticalpush'"   
    sendEthernet("verticalpush")
}
def verticalpull(){
    sendEvent(name: "feeder", value: "verticalpull")
    log.debug "Executing 'verticalpull'"   
    sendEthernet("verticalpull")
}
def verticalpushsensor(){
     sendEvent(name: "feeder", value: "verticalpullsensor")
    log.debug "Executing 'verticalpullsensor'"   
    sendEthernet("verticalpullsensor")
}
def verticaldownstep(){
    sendEvent(name: "feeder", value: "verticaldownstep")
    log.debug "Executing 'verticaldownstep'"   
    sendEthernet("verticaldownstep")
}
def verticalupstep(){
    sendEvent(name: "feeder", value: "verticalupstep")
    log.debug "Executing 'verticalupstep'"   
    sendEthernet("verticalupstep")
}
def poplid(){
    sendEvent(name: "feeder", value: "poplid")
    log.debug "Executing 'poplid'"   
    sendEthernet("poplid")
}
def compress(){
    sendEvent(name: "feeder", value: "compress")
    log.debug "Executing 'compress'"   
    sendEthernet("compress")
}
def decompress(){
    sendEvent(name: "feeder", value: "decompress")
    log.debug "Executing 'decompress'"   
    sendEthernet("decompress")
}
def pushupperholder(){
    sendEvent(name: "feeder", value: "pushupperholder")
    log.debug "Executing 'pushupperholder'"   
    sendEthernet("pushupperholder")
}
def cosinusplus(){
    sendEvent(name: "feeder", value: "cosinusplus")
    log.debug "Executing 'cosinusplus'"   
    sendEthernet("cosinusplus")
}
def cosinusminus(){
    sendEvent(name: "feeder", value: "cosinusminus")
    log.debug "Executing 'cosinusminus'"   
    sendEthernet("cosinusminus")
}
def cosinuspush(){
    sendEvent(name: "feeder", value: "cosinuspush")
    log.debug "Executing 'cosinuspush'"   
    sendEthernet("cosinuspush")
}
def cosinuspull(){
    sendEvent(name: "feeder", value: "cosinuspull")
    log.debug "Executing 'cosinuspull'"   
    sendEthernet("cosinuspull")
}
def toggledebug(){
    sendEvent(name: "feeder", value: "toggledebug")
    log.debug "Executing 'toggledebug'"   
    sendEthernet("toggledebug")
}
def reset() {
    log.debug "Executing 'reset'"
    sendEvent(name: "reset", value: "reset")
    sendEthernet("reset")
}
/////////////////


def refresh(){
    sendEthernet("refresh")
}
def configure() {
    log.debug "Executing 'configure'"
    state.lastDeclaredEvent = now()
    log.debug "state.lastDeclaredEvent = ${now()}"

    updateDeviceNetworkID()
}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    // if(device.deviceNetworkId!=mac) {
    log.debug "setting deviceNetworkID = ${mac}"
    device.setDeviceNetworkId("${mac}")
    // }
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
def getHostAddress() {
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



def sendGet(cmd, ip) // sends request to ESP8266 which serves as redundency to reset the Atmega in case of total failure
{
    log.debug "sending $cmd to $ip"
    def deviceNetworkId = "479694101:50"  //  "19216810241:80"
    //def ip = "192.168.10.241:80"

    try {
        httpGet("http://${ip}/${cmd}"){ resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }

    //sendHubCommand(new hubitat.device.HubAction("""GET /$cmd HTTP/1.1\r\nHOST: $ip\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))

}