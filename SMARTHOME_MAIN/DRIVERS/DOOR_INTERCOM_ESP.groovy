/**
*   INTERCOM INTERFACE Controller 
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
    definition (name: "DOOR-INTERCOM ESP", namespace: "Elfege", author: "Elfege") {
        capability "DoorControl"
        capability "Switch"
        capability "Refresh" 
        capability "AccelerationSensor" // used for bell events

        command "talk"
        command "reset"
        command "configure"
        command "test"

        attribute "door", "string"
        attribute "bell", "string"

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

    //log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header


    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }
    def bodyString = msg.body

    logging "bodyString $bodyString"
    if(bodyString == null) return 
    
    def parts = bodyString.split(" ")
    def name  = parts.length>0?parts[0].trim():null
    def value = parts.length>1?parts[1].trim():null
    
    boolean isStateChanged = value != state.lastEventValue && name != state.lastEventName


    logging "sendEvent(name: $name, value: $value)"
    sendEvent(name: name, value: value)
    if(name == "acceleration") sendEvent(name: "bell", value: value)

    state.lastEventValue = value
    state.lastEventName = name

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
def open() {
    logging "Executing 'open'"
    sendEthernet("open")

}
def close(){
    logging "Executing 'close'"
    sendEthernet("close")
}
def talk(){
   logging "Executing 'talk'"
    sendEthernet("talk")  
}
def test(){
   logging "Executing 'test'"
    sendEthernet("simulator")  
}


def on(){
    open()
}
def off(){
    close()
}




def reset() {
    logging "Executing 'reset'"
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
