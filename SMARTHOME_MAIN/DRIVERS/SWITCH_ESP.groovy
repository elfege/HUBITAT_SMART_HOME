/**
*  Virtual Switch
*
*  Copyright 2018 Elfege
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
    definition (name: "SWITCH ESP", namespace: "Elfege", author: "Elfege") {
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        
        command "reset"
        command "refresh"
        command "reboot"
        command "configure"
        command "battery"

        attribute "computer", "string"
    }
}


// Preferences
preferences {
    input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
    input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
    input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
}

def updated() {
    log.debug "Updated settings ${settings}.."
    device.latestValue("status")
}
def configure() {
    log.debug "Executing 'configure'"
    updateDeviceNetworkID()
}

def refresh(){
    sendEthernet("refresh")
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

            value == "off" ? "sleeping" : "running"
            sendEvent(name: "status", value: value)

            value == "sleepingf" ? "off" : "on"
            sendEvent(name: "switch", value: value)
        }
        else
        {
            log.warn "duplicate event name and value less than ${timeThreshold/1000} seconds apart, skipping"
        }
    }
}


def on(){
    log.debug "Sending 'on'"
    sendEthernet("on")
    log.debug "Sending 'L' for mebo'"
    sendEthernet("L") // for mebo aux power switch
}
def off(){
    log.debug "Sending 'off'"
    sendEthernet("off")
    log.debug "Sending 'H' for mebo'"
    sendEthernet("H") // for mebo aux power switch
}

def reboot(){
    log.debug "Sending 'rebootrobot'"
    sendEthernet("reboot")
}

def reset() {
    log.debug "Sending 'reset'"
    sendEthernet("reset")
}


def battery()
{
    log.debug "Sending 'chargeon'"
    sendEthernet("togglecharge")  
}




private getHostAddress() {
    def ip = settings.ip
    def port = settings.port

    log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ":" + port
}





def sendEthernet(message) {
    log.debug "Sending 'sendEthernet' ${message}"
    new hubitat.device.HubAction(
        method: "POST",
        path: "/${message}?",
        headers: [ HOST: "${getHostAddress()}" ]
    )

    try {
    def uri = "http://${getHostAddress()}/${message}"
    log.debug "http GET => $uri"
    httpGet(uri) { resp ->
        if (resp.success) {
            log.debug "switch on"
            createEvent(name: "switch", value: "on", isStateChange: true)
        }
        if (resp.data) log.debug "${resp.data}"
    }
     } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }

    try {
        // without the port for when it's a direct get request (without using STAnything library)
        def uri = "http://${settings.ip}/${message}"
        log.debug "http GET => $uri"
        httpGet(uri) { resp ->
        if (resp.success) {
            log.debug "switch on"
            createEvent(name: "switch", value: "on", isStateChange: true)
        }
        if (resp.data) log.debug "${resp.data}"
    }
     } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId!=mac) {
        log.debug "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
}
