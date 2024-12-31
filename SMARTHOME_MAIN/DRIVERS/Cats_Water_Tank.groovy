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

import com.hubitat.app.exception.LimitExceededException

metadata {
    definition (name: "CatsWaterTank", namespace: "Elfege", author: "Elfege") {
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Valve"
        capability "Water Sensor"
        
        command "reset"
        command "configure"
        command "rebootHub"

        attribute "headline", "string"
        attribute "status", "string"
        attribute "TANK", "string"
        attribute "voltage", "number"
    }
}

// Preferences
preferences {
    input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
    input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
    input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
}

// Call initialize in the updated() method
def updated() {
    log.debug "Updated settings ${settings}.."
    initialize()
    device.latestValue("status")
}

// Initialize state variables
def initialize() {
    log.debug "Initializing"
    state.lastTankState = device.currentValue("TANK")
    state.lastSwitchState = device.currentValue("switch")
    state.bootEvents = []
    state.lastBootTime = 0
    state.deviceFailedDeclared = false
}

def configure() {
    log.debug "Executing 'configure'"
    initialize()
    updateDeviceNetworkID()
}

def refresh(){
    sendEthernet("refresh")
}


// parse events into attributes
def parse(String description) {
    def msg = parseLanMessage(description)
    def bodyString = msg.body

    if(bodyString){
        def parts = bodyString.split(" ")
        def name = parts.length > 0 ? parts[0].trim() : null
        def value = parts.length > 1 ? parts[1].trim() : null
        
        if (name && value) {
            send_event(name, value)
        } else {
            log.warn "Incomplete data received: name=$name, value=$value"
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

def on() {
    try {
        send_event("switch", "on")
        sendEthernet("on")
    } catch (LimitExceededException e) {
        log.error "Failed to turn on due to event queue full. Exception: ${e}"
        handleEventQueueFull()
    }
}

def off() {
    try {
        send_event("switch", "off")
        sendEthernet("off")
    } catch (LimitExceededException e) {
        log.error "Failed to turn off due to event queue full. Exception: ${e}"
        handleEventQueueFull()
    }
}

def open(){
    send_event("valve", "open")
    sendEthernet("open")
}

def close(){
    send_event("valve", "close")
    sendEthernet("close")
}

def reset() {
    log.debug "Executing 'TANK reset'"
    sendEthernet("reset")
}

def updateDeviceNetworkID() {
    log.debug "Executing 'updateDeviceNetworkID'"
    if(device.deviceNetworkId != mac) {
        log.debug "setting deviceNetworkID = ${mac}"
        device.setDeviceNetworkId("${mac}")
    }
}

private send_event(name, value) {
    def currentTime = now()
    def currentValue = device.currentValue(name)
    
    if (name == "TANK") {
        log.trace "state.lastTankState = $state.lastTankState | value = $value"
    } else if (name == "switch") {
        log.trace "state.lastSwitchState = $state.lastSwitchState | value = $value"
    } else if (name == "device" && value == "boot") {
        handleBootEvent(currentTime)
        return  // Exit the function after handling boot event
    }
    
    if (currentValue != value) {
        log.debug "name: $name, value: $value"
        try {
            sendEvent(name: name, value: value)
            
            // Update state after successful event send
            if (name == "TANK") {
                state.lastTankState = value
            } else if (name == "switch") {
                state.lastSwitchState = value
            }
        } catch (LimitExceededException e) {
            log.error "Event queue is full. Exception: ${e}"
            handleEventQueueFull()
        }
    } else {
        log.debug "$name is already $value, not sending event"
    }
}

private handleBootEvent(currentTime) {
    if (!state.bootEvents) state.bootEvents = []
    if (!state.lastBootTime) state.lastBootTime = 0
    if (!state.deviceFailedDeclared) state.deviceFailedDeclared = false
    
    // Remove boot events older than 30 minutes
    state.bootEvents = state.bootEvents.findAll { (currentTime - it) <= 1800000 }
    
    // Add current boot event
    state.bootEvents.add(currentTime)
    
    // Check if we should declare device failed
    if (state.bootEvents.size() >= 5 && !state.deviceFailedDeclared) {
        log.warn "Device failed: Too many reboots in a short period"
        sendEvent(name: "deviceStatus", value: "failed")
        state.deviceFailedDeclared = true
    }
    
    // Check if we should send a boot event (once per 15 minutes)
    if (currentTime - state.lastBootTime >= 900000) {
        log.debug "Device booted"
        sendEvent(name: "device", value: "boot")
        state.lastBootTime = currentTime
    } else {
        log.debug "Boot event suppressed (cooldown period). Time since last boot: ${(currentTime - state.lastBootTime) / 1000} seconds"
    }
    
    // Check if we should clear the failed state
    if (state.deviceFailedDeclared && state.bootEvents.size() < 2) {
        log.info "Device recovered: Normal boot pattern resumed"
        sendEvent(name: "deviceStatus", value: "normal")
        state.deviceFailedDeclared = false
    }
    
    // If too many boot events, consider hub reboot
    if (state.bootEvents.size() >= 10) {
        log.warn "Excessive reboots detected. Considering hub reboot."
        considerHubReboot()
    }
}

private handleEventQueueFull() {
    if (!state.lastEventQueueFullTime) {
        state.lastEventQueueFullTime = 0
        state.eventQueueFullCount = 0
    }
    
    def currentTime = now()
    if (currentTime - state.lastEventQueueFullTime > 3600000) { // Reset count if more than 1 hour has passed
        state.eventQueueFullCount = 0
    }
    
    state.eventQueueFullCount++
    state.lastEventQueueFullTime = currentTime
    
    if (state.eventQueueFullCount >= 5) {
        log.warn "Event queue full 5 times in the last hour. Triggering hub reboot."
        rebootHub()
    }
}


def rebootHub(String endpoint = '/hub/reboot', String ip = location.hub.localIP, String port = "8080") {
    def fullUri = "http://${ip}:${port}${endpoint}"

    log.debug("Sending POST request to: $fullUri")

    try {
        httpPost([
            uri: "http://${location.hub.localIP}:8080",
            path: "/hub/reboot",
            timeout: 10
        ]) {
            response ->
                log.warning "Reboot command sent successfully"
        }
    } catch (Exception e) {
        log.error "Failed to send reboot command: ${e.message}"
        try {
            // Fallback to maintenance API
            httpPost([
                uri: "http://${location.hub.localIP}:8081",
                path: "/api/rebootHub",
                timeout: 10
            ]) {
                response ->
                    log.warning "Reboot command sent successfully using maintenance API"
            }
        } catch (Exception e2) {
            log.error "All reboot attempts failed. Final error: ${e2.message}"
            sendNotification("Failed to reboot hub. Manual intervention may be required.")
            if (enableRemote) {
                resumeRemoteHubChecks()
            }
            return
        }
    }
}

