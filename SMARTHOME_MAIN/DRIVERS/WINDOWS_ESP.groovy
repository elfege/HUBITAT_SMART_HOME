/**
*  Windows Controller
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
    definition (name: "WINDOW ESP", namespace: "Elfege", author: "Elfege") {
        capability "Switch Level"
        capability "SwitchLevel"
        capability "Switch"
        capability "ContactSensor"
        capability "Refresh" 

        // capability "DoorControl"


        command "open"
        command "close"
        command "stop"
        command "fast"
        command "slow"
        command "medium"
        command "reset"
        command "ultraslow"
        command "slow"
        command "medium"
        command "fast"
        command "configure"
        //command "setLevel"

        attribute "window", "string"

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

    state.evtCount += 1

    logging "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        logging "headerstring was null for some reason :("
    }
    def bodyString = msg.body


    if(!bodyString){
        logging "bodyString is null for some reason"
    }
    else {

        logging "bodyString $bodyString"
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null
        boolean isStateChanged = value != state.lastEventValue

        def timeThreshold = 60000

        /*log.debug """now() - state.lastEvent > timeThreshold ? ${now() - state.lastEvent > timeThreshold}
state.lastEvent = $state.lastEvent
now() = ${now()}
now() - state.lastEvent = ${now() - state.lastEvent}
"""*/

        if(now() - state.lastEvent > timeThreshold || isStateChanged || state.refreshRequest || value in ["closed", "open"]) 
        {
            state.lastEventName = name
            state.lastEventValue = value
            state.lastEvent = now()
            state.refreshRequest = false


            textdescription "$name is $value"
            /* if(name == "window") // THOSE TRIGGER FALSE NEGATIVES TO it.currentValue("contact") == closed
{
sendEvent(name: "contact", value: value)
textdescription "contact is $value"
}*/

            if(value in ["open", "closed"])
            {
                logging "sendEvent(name: $name, value: $value)"
                sendEvent(name: "contact", value: value)
                def OnOff = value == "open" ? "on" : "off"
                sendEvent(name: "switch", value: OnOff)
                
            }

        }
        else 
        {
            logging "duplicate event value less than ${timeThreshold/1000} seconds apart, skipping"
        }
    }

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

// handle commands
def setLevel(value) {
    logging "sending ${value}%"
    sendEvent(name: "level", value: value)
    sendEthernet(value)
}

def open() {
    logging "Executing 'open'"
    sendEvent(name: "window", value: "opening")
    sendEthernet("open")

}
def close(){
    logging "Executing 'close'"
    sendEvent(name: "window", value: "closing")
    sendEthernet("close")
}
def stop(){
    logging "Executing 'stop'"
    sendEvent(name: "window", value: "stopped")
    sendEthernet("stop")
}
// for alexa
def on(){
    sendEvent(name: "switch", value: "on")
    open()
}
def off(){
    sendEvent(name: "switch", value: "off")
    close()
}
def reset() {
    logging "Executing 'reset'"
    sendEvent(name: "status", value:"sleep")
    sendEthernet("reset")
}

def refresh(){
    state.refreshRequest = true
    sendEthernet("refresh")
}
def configure() {
    logging "Executing 'configure'"
    state.lastEvent = now() as long
        updateDeviceNetworkID()
}
def updateDeviceNetworkID() {
    logging "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId!=mac) {
        logging "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
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

}

def logging(String message)
{
    if(enablelogging){
        log.debug message
    }
}

def textdescription(String message)
{
    if(enabledescription){
        log.info message
    }
}

def disablelogging()
{
    device.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}
