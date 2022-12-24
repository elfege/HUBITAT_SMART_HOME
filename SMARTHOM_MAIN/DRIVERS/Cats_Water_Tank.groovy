/**
*  Cats Water Tank
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
    definition (name: "CatsWaterTank", namespace: "Elfege", author: "Elfege") {
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Valve"
        capability "Water Sensor"
        

        command "reset"


        attribute "headline", "string"
        attribute "status", "string"
        attribute "TANK", "string"
        attribute "voltage", "number"

    }

}

// simulator metadata
simulator {
}

// Preferences
preferences {
    input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
    input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
    input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
}

// UI tile definitions

def updated() {
    log.debug "Updated settings ${settings}.."
    device.latestValue("status")
}
def configure() {
    log.debug "Executing 'configure'"
    updateDeviceNetworkID()
    //poll();
}

def refresh(){
    sendEthernet("refresh")
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
    //log.debug "bodyString ================================= $bodyString"

    def result = null
    def name = "TANK"
    def value = null
    def parts = null

    if(bodyString){
       // log.debug "bodyString = $bodyString ||| parts = $parts"

        parts = bodyString.split(" ")
        name  = parts.length>0?parts[0].trim():null
        value = parts.length>1?parts[1].trim():null

        log.debug "name: $name, value: $value"
        sendEvent(name: name, value: value)
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

// Switch methods

def on(){
    //sendEvent(name: "switch", value: "on")
    sendEthernet("on")
}
def open(){
    //sendEvent(name: "valve", value: "open")
    sendEthernet("open")
}
def off(){
    //sendEvent(name: "switch", value: "off")
    sendEthernet("off")
}


def reset() {
    log.debug "Executing 'TANK reset'"
    sendEthernet("reset")
}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId!=mac) {
        log.debug "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
}