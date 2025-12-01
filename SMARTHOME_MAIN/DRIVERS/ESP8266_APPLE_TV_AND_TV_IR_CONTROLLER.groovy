/**
 *  ESP8266 SWITCH FOR SAMSUNG LED TV AND APPLE TV
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
    definition (name: "ESP8266 SWITCH TV & APPLE TV", namespace: "elfege", author: "Elfege") {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        capability "TV"

      
        command "reset"
        command "playpause"
        command "play"
        command "pause"
        command "menu"
        command "up"
        command "down"
        command "left"
        command "right"
        command "select"
        

        attribute "appletv", "string"
    }

    // Preferences
    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
    }


    tiles {
        multiAttributeTile(name:"switch", type: "switch", width: 6, height: 4, canChangeIcon: true, decoration: "flat"){
            tileAttribute("switch", key: "PRIMARY_CONTROL"){     
                attributeState "on", label: "ON", action: "on", backgroundColor: "#bc2323"
                attributeState "off",label: 'OFF', action: "off",  backgroundColor: "#3349A6"
            }
            tileAttribute("device.lastUpdated", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last updated ${currentValue}',icon: "st.Health & Wellness.health9")
            }

        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }        
        standardTile("appletv", "mediaPlayer", decoration: "flat") {
            state "default", label:'play/pause', action:"playpause"
        }        
        main "switch"
        details(["switch", "appletv", "refresh"])
    }
}

// parse events into attributes
def parse(String description) {


    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }
    def bodyString = msg.body


    //log.debug "bodyString ===================== $bodyString"

    if(state.lastDeclaredEvent == null) {state.lastDeclaredEvent = now() as long}


    if(!bodyString){
        log.debug "bodyString is null for some reason"
    }
    else {
        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null
        log.debug "$name is $value"
        sendEvent(name: name, value: value)

        def timeLimit = 60*1000

        def timeAtlastEvent = state.lastDeclaredEvent as long
            long Now = now()
        boolean pastDelay = (Now - timeAtlastEvent) > now()

        state.lastDeclaredEvent = now()as long
            // Update lastUpdated date and time
            def nowDay = new Date().format("MMM dd", location.timeZone)
            def nowTime = new Date().format("h:mm a", location.timeZone)
            createEvent(name: "lastUpdated", value: nowDay + " " + nowTime, displayed: false)


    }

}

def on() {
    log.debug "sending 'curtains opening'"
    //sendEvent(name: "switch", value: "on") 
    sendEthernet("on")
}

def off() {
    log.debug "sending 'curtains closing'"
    sendEvent(name: "switch", value: "off") 
    sendEthernet("off")
}

def reset()
{
    log.debug "sending 'switch reset'"
    sendEvent(name: "switch", value: "reset")
    sendEthernet("reset")
}

def playpause()
{
    log.debug "sending 'switch playpause'"
    sendEvent(name: "appletv", value: "playpause")
    sendEthernet("appletvplaypause") 
}
def play()
{
    playpause()
}
def pause()
{
    playpause() 
}

def refresh()
{
    log.debug "sending 'switch refresh'"
    sendEvent(name: "switch", value: "refresh")
    sendEthernet("refresh")
}

def configure() {
    log.debug "Executing 'configure'"
    state.lastDeclaredEvent = now()
    log.debug "state.lastDeclaredEvent = ${now()}"

    updateDeviceNetworkID()
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