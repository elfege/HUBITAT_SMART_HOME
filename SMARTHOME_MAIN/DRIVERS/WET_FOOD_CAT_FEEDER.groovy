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
        input name: "enabledebug", type: "bool", title: "Enable debug logging?", defaultValue: true
        input name: "enabledescription", type: "bool", title: "Enable description text?", defaultValue: true

        state.enableDebugTime = now()
    }

}


// parse events into attributes
def parse(String description) {

    //logging "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header


    if (!headerString) {
        logging "headerstring was null for some reason :("
    }
    def bodyString = msg.body

    logging "bodyString ===================== $bodyString"


    if(!bodyString){
        logging "bodyString is null for some reason"
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

            descriptionText "sending event: name: $name, value: $value 56df"


            sendEvent(name: name, value: value, isStateChange: true)


            // Update lastUpdated date and time
            def nowDay = new Date().format("MMM dd", location.timeZone)
            def nowTime = new Date().format("h:mm a", location.timeZone)
            sendEvent(name: "lastUpdated", value: nowDay + " " + nowTime, displayed: false)

        }

    }
}

def on(){
    descriptionText "Executing 'switch on'"
    sendEthernet("on")
}
def off(){
    logging "Executing 'switch off'"
    sendEthernet("off")
}
def feed(){
    sendEthernet("feed")
}
def stop(){
    sendEvent(name: "feeder", value: "stop")
    descriptionText "Executing 'stop'"   
    sendEthernet("stop")
}
def unload(){
    sendEvent(name: "feeder", value: "unload")
    descriptionText "Executing 'unload'"   
    sendEthernet("unload")
}
def lock(){
    sendEvent(name: "feeder", value: "lock")
    descriptionText "Executing 'lock'"   
    sendEthernet("lock")
}
def unlock(){
    sendEvent(name: "feeder", value: "unlock")
    descriptionText "Executing 'unlock'"   
    sendEthernet("unlock")
}
def opencan(){
    sendEvent(name: "feeder", value: "opencan")
    descriptionText "Executing 'opencan'"   
    sendEthernet("opencan")
}
def push(){
    sendEvent(name: "feeder", value: "push")
    descriptionText "Executing 'push'"   
    sendEthernet("push")
}
def pull(){
    sendEvent(name: "feeder", value: "pull")
    descriptionText "Executing 'pull'"   
    sendEthernet("pull")
}
def verticalpush(){
    sendEvent(name: "feeder", value: "verticalpush")
    descriptionText "Executing 'verticalpush'"   
    sendEthernet("verticalpush")
}
def verticalpull(){
    sendEvent(name: "feeder", value: "verticalpull")
    descriptionText "Executing 'verticalpull'"   
    sendEthernet("verticalpull")
}
def verticalpushsensor(){
     sendEvent(name: "feeder", value: "verticalpullsensor")
    descriptionText "Executing 'verticalpullsensor'"   
    sendEthernet("verticalpullsensor")
}
def verticaldownstep(){
    sendEvent(name: "feeder", value: "verticaldownstep")
    descriptionText "Executing 'verticaldownstep'"   
    sendEthernet("verticaldownstep")
}
def verticalupstep(){
    sendEvent(name: "feeder", value: "verticalupstep")
    descriptionText "Executing 'verticalupstep'"   
    sendEthernet("verticalupstep")
}
def poplid(){
    sendEvent(name: "feeder", value: "poplid")
    descriptionText "Executing 'poplid'"   
    sendEthernet("poplid")
}
def compress(){
    sendEvent(name: "feeder", value: "compress")
    descriptionText "Executing 'compress'"   
    sendEthernet("compress")
}
def decompress(){
    sendEvent(name: "feeder", value: "decompress")
    descriptionText "Executing 'decompress'"   
    sendEthernet("decompress")
}
def pushupperholder(){
    sendEvent(name: "feeder", value: "pushupperholder")
    descriptionText "Executing 'pushupperholder'"   
    sendEthernet("pushupperholder")
}
def cosinusplus(){
    sendEvent(name: "feeder", value: "cosinusplus")
    descriptionText "Executing 'cosinusplus'"   
    sendEthernet("cosinusplus")
}
def cosinusminus(){
    sendEvent(name: "feeder", value: "cosinusminus")
    descriptionText "Executing 'cosinusminus'"   
    sendEthernet("cosinusminus")
}
def cosinuspush(){
    sendEvent(name: "feeder", value: "cosinuspush")
    descriptionText "Executing 'cosinuspush'"   
    sendEthernet("cosinuspush")
}
def cosinuspull(){
    sendEvent(name: "feeder", value: "cosinuspull")
    descriptionText "Executing 'cosinuspull'"   
    sendEthernet("cosinuspull")
}
def toggledebug(){
    sendEvent(name: "feeder", value: "toggledebug")
    descriptionText "Executing 'toggledebug'"   
    sendEthernet("toggledebug")
}
def reset() {
    descriptionText "Executing 'reset'"
    sendEvent(name: "reset", value: "reset")
    sendEthernet("reset")
}
/////////////////


def refresh(){
    sendEthernet("refresh")
}
def configure() {
    logging "Executing 'configure'"
    state.lastDeclaredEvent = now()
    logging "state.lastDeclaredEvent = ${now()}"

    updateDeviceNetworkID()
}

def updateDeviceNetworkID() {
    logging "Executing 'updateDeviceNetworkID'"
    // if(device.deviceNetworkId!=mac) {
    logging "setting deviceNetworkID = ${mac}"
    device.setDeviceNetworkId("${mac}")
    // }
}
def updated() {


    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
        state.updatedLastRanAt = now()
        logging "Executing 'updated'"
        runIn(3, updateDeviceNetworkID)
    }
    else {
        log.trace "updated(): Ran within last 5 seconds so aborting."
    }

}
def getHostAddress() {
    def ip = settings.ip
    def port = settings.port

    logging "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ":" + port
}
def sendEthernet(message) {
    logging "Executing 'sendEthernet' ${message}"
    new hubitat.device.HubAction(
        method: "POST",
        path: "/${message}?",
        headers: [ HOST: "${getHostAddress()}" ]
    )
}



def sendGet(cmd, ip) // sends request to ESP8266 which serves as redundency to reset the Atmega in case of total failure
{
    logging "sending $cmd to $ip"
    def deviceNetworkId = "479694101:50"  //  "19216810241:80"
    //def ip = "192.168.10.241:80"

    try {
        httpGet("http://${ip}/${cmd}"){ resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            if (resp.data) logging "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }

}

def logging(String message){

    state.enableDebugTime = state.enableDebugTime ? state.enableDebugTime : now()

    if(enabledebug){
        if(now() - state.enableDebugTime >= 30 * 60 * 1000)
        log.debug message
    }
}
def descriptionText(String message){
    if(enabledescription){
        log.info message 
    }
}
def disablelogging(){
    device.updateSetting("enabledebug",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}