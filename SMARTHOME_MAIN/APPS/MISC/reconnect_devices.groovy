/**
 * Hubitat Reconnect Device App
 */
definition(
    name: "Reconnect Device",
    namespace: "hubitat",
    author: "Your Name",
    description: "Reestablishes connection for devices marked as offline.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Device Selection") {
        input "devices", "capability.*", title: "Select Specific Devices (if not selecting all offline)", required: true, multiple: true, submitOnChange: true
    }
    section("Logging") {
        input "enableDebug", "bool", title: "Enable Debug Logging", required: false, defaultValue: false
    }
    section("Actions") {
        input "run", "button", title: "RUN"
        input "initialize", "button", title: "INITIALIZE"
    }
}

def installed() {
    log.info "App installed"
    initialize()
}

def updated() {
    log.info "App updated"
    unsubscribe()
    initialize()
}

def initialize() {
    if (enableDebug) log.debug "Initializing app with settings: $settings"
    // if (enableDebug) log.debug "devices: $devices"
    
}

def appButtonHandler(btn) {
    if (btn == "run") {
        log.info "RUN button pressed"
        runReconnect()
    }
    if (btn == "initialize") {
        log.info "initialize button pressed"
        initialize()
    }
}

def runReconnect() {
    log.info "Running reconnection process"
    def offlineDevices = getAllOfflineDevices()
    if (enableDebug) log.debug "Selected devices for reconnection: ${offlineDevices*.displayName}"

    if (enableDebug) log.debug "Processing devices..."
    for(device in selectedDevices){
        if (enableDebug) log.debug "Processing device: ${device.displayName}"
        reconnectDevice(device)
    }
    // selectedDevices.each { device ->
    //     if (enableDebug) log.debug "Processing device: ${device.displayName}"
    //     reconnectDevice(device)
    // }
}

def reconnectDevice(device) {
    log.info "Attempting to reconnect device: ${device.displayName}"
    try {
        device.refresh() // Example action to reestablish connection
        log.info "Reconnection attempt sent to device: ${device.displayName}"
    } catch (e) {
        log.error "Failed to reconnect device: ${device.displayName}, Error: ${e.message}"
    }
}



def getAllOfflineDevices() {
    log.debug "Retrieving all offline devices"
    def offlineDevices = devices.findAll { it.displayName.startsWith("OFFLINE - ") }
    if (enableDebug) log.debug "Offline devices: ${offlineDevices*.displayName}"
    return offlineDevices
}



