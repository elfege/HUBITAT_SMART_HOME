/**
*  CURTAINS
*
*  Copyright 2016 Elfege
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*/
metadata {
    definition (name: "CURTAINS ESP8266", namespace: "elfege", author: "Elfege") {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"

        // capability "DoorControl"

        command "open"
        command "close"
        command "reset"
        command "stop"


        attribute "curtains", "String"
        attribute "closed", "String"
        attribute "open", "String"
        attribute "stop", "String"

    }

    // Preferences
    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
        
        input "enablelogging", "bool", title:"Enable debug logging", defautlValue: false
        input "enabledescription", "bool", title:"Enable description text", defautlValue: false
    }
}

// parse events into attributes
def parse(String description) {
    
    state.enableLoggingTime = state.enableLoggingTime == null ? now() : state.enableLoggingTime
    state.lastEvent = state.lastEvent ? state.lastEvent : now()
    state.lastEventName = state.lastEventName ? state.lastEventName : ""
    state.lastEventValue = state.lastEventValue ? state.lastEventValue : ""
    state.enablologgingWasFalseAtLastUpdate = state.enablologgingWasFalseAtLastUpdate ? state.enablologgingWasFalseAtLastUpdate : true
    
    if(enablelogging && state.enablologgingWasFalseAtLastUpdate)
    {
        runIn(1800, disablelogging)
        state.enablologgingWasFalseAtLastUpdate = false
        state.enableLoggingTime = now()
    }
    if(enablelogging == true && now() - state.enableLoggingTime > 30 * 60 * 1000)
    {
        disablelogging()
    }

    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        logging "headerstring was null for some reason :("
    }
    def bodyString = msg.body


    //logging "bodyString ===================== $bodyString"

    state.lastEvent = state.lastEvent ? state.lastEvent : now()
    state.lastEventName = state.lastEventName ? state.lastEventName : ""
    state.lastEventValue = state.lastEventValue ? state.lastEventValue : ""


    if(!bodyString){
        logging "bodyString is null for some reason"
    }
    else {
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null
        boolean isStateChanged = value != state.lastEventValue

        def timeThreshold = 60000

        if(now() - state.lastEvent > timeThreshold || isStateChanged || state.refreshRequest) 
        {
            state.lastEventName = name
            state.lastEventValue = value
            state.lastEvent = now()
            state.refreshRequest = false

            logging "sending event: name: $name, value: $value"
            sendEvent(name: name, value: value)

            def timeAtlastEvent = state.lastDeclaredEvent as long
                long Now = now()
            boolean pastDelay = (Now - timeAtlastEvent) > now()


            // Update lastUpdated date and time
            def nowDay = new Date().format("MMM dd", location.timeZone)
            def nowTime = new Date().format("h:mm a", location.timeZone)
            createEvent(name: "lastUpdated", value: nowDay + " " + nowTime, displayed: false)
        }
        else
        {
            logging "duplicate event name and value less than ${timeThreshold/1000}seconds apart, skipping"
        }


    }

}

// handle commands
def open(){
    on()
}
def close(){
    off()
}
def on() {
    logging "sending 'curtains opening'"
    sendEvent(name: "switch", value: "on")     // for alexa
    sendEvent(name: "curtains", value: "opening")
    sendEthernet("on")
}
def off() {
    logging "sending 'curtains closing'"
    sendEvent(name: "switch", value: "off") // for alexa
    sendEvent(name: "curtains", value: "closing")
    sendEthernet("off")
}
def reset(){
    logging "sending 'switch reset'"
    sendEthernet("reset")
}
def stop() {
    logging "sending 'switch stop'"
    sendEvent(name: "curtains", value: "stop")

    sendEthernet("stop")
}
def refresh(){
    state.refreshRequest = true
    sendEthernet("refresh")
}
def configure() {
    logging "Executing 'configure'"
    state.lastDeclaredEvent = now()
    logging "state.lastDeclaredEvent = ${now()}"

    updated()
}
private getHostAddress() {
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

def updateDeviceNetworkID() {
    logging "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId!=mac) {
        logging "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
}
def updated() {
    if(enablelogging){
        state.enableLoggingTime = now()
        state.enablologgingWasFalseAtLastUpdate = false
        runIn(1800, disablelogging)
        textdescription("disablelogging scheduled to run in ${1800/60} minutes")
    }
    else // if enablelogging is false at time of update, mark it so if changed in preferences, the parse() method will now it changed and
        // then reset the counter
    {
        state.enablologgingWasFalseAtLastUpdate = true
    }
    
    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
        state.updatedLastRanAt = now()
        logging "Executing 'updated'"
        runIn(3, updateDeviceNetworkID)
    }
    else {
        log.trace "updated(): Ran within last 5 seconds so aborting."
    }

}

def logging(String message){
    if(enablelogging){
        log.debug message
    }
}
def textdescription(String message){
    if(enabledescription){
        log.info message
    }
}
def disablelogging(){
    device.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}
