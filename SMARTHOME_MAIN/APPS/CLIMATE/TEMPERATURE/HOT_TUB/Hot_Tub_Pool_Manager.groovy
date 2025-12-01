// @ts-nocheck
definition(
    name: "Hot Tub Manager",
    namespace: "yourNamespace",
    author: "Your Name",
    description: "Efficient hot tub management app",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: ""
)


preferences {
    page(name: "mainPage", title: "Hot Tub Manager Settings", install: true, uninstall: true) {
        state.powerDevices = []  // Initialize as an empty list
        section('General Settings') {
            input 'appName', 'text', title: 'Name this app instance', required: false, submitOnChange: true
            if (appName) {
                app.updateLabel(appName)
            }

        }
        section("Restricted Modes"){
            input "restricted", "mode", title: "Restricted modes", multiple: true
        }
        section("System Control") {
            input "pauseMode", "bool", title: "Pause System", defaultValue: false, submitOnChange: true
        }

        section("Pump Settings") {
            
            input "primaryPump", "capability.switch", title: "Select Primary Pump", required: true, submitOnChange: true
            if (primaryPump?.hasCapability("powerMeter") || primaryPump?.hasAttribute("power")) {
                if (enabletrace) log.trace "$primaryPump has power measurement capability"
                paragraph format_text("<span style='color:yellow';font-weight:'1200';>✔</span> Primary pump has power measurement capability.", "white", "green")
                state.powerDevices << '${primaryPump}'
            } else {
                if (enablewarning) log.warn "$primaryPump does not have power measurement capability"
            }
            
            input "useSecondaryPump", "bool", title: "Use Secondary Pump?", defaultValue: false, submitOnChange: true
            if (useSecondaryPump) {
                input "secondaryPump", "capability.switch", title: "Select Secondary Pump", required: true, submitOnChange: true
                if (secondaryPump?.hasCapability("powerMeter") || secondaryPump?.hasAttribute("power")) {
                    if (enabletrace) log.trace "$secondaryPump has power measurement capability"
                    paragraph format_text("<span style='color:yellow';font-weight:'1200';>✔</span> Secondary pump has power measurement capability.", "white", "green")
                    state.powerDevices << '${secondaryPump}'
                } else {
                    if (enablewarning) log.warn "$secondaryPump does not have power measurement capability"
                }
                input "alternatePumps", "bool", title: "Alternate Pumps?", defaultValue: false, submitOnChange: true
                if (alternatePumps) {
                    input "pumpAlternateDelay", "number", title: "Delay between pump swaps (minutes)", required: true, defaultValue: 30
                }
            } else {
                app.removeSetting("secondaryPump")
                app.removeSetting("alternatePumps")
                app.removeSetting("pumpAlternateDelay")
            }

        }

        section("Heater Settings") {
            input "mainHeater", "capability.switch", title: "Select Main Heater", required: true, submitOnChange: true
            if (mainHeater?.hasCapability("powerMeter") || mainHeater?.hasAttribute("power")) {
                if (enabletrace) log.trace "$mainHeater has power measurement capability"
                paragraph format_text("<span style='color:yellow';font-weight:'1200';>✔</span> Main heater has power measurement capability.", "white", "green")
                state.powerDevices << '${mainHeater}'
            } else {
                if (enablewarning) log.warn "$mainHeater does not have power measurement capability"
            }
            input "mainHeaterFan", "capability.switch", title: "Activate a fan with this heater"
            
            input "useSecondaryHeater", "bool", title: "Use Secondary Heater?", defaultValue: false, submitOnChange: true
            if (useSecondaryHeater) {
                input "secondaryHeater", "capability.switch", title: "Select Secondary Heater", required: true, submitOnChange: true
                if (secondaryHeater?.hasCapability("powerMeter") || secondaryHeater?.hasAttribute("power")) {
                    if (enabletrace) log.trace "$secondaryHeater has power measurement capability"
                    paragraph format_text("<span style='color:yellow';font-weight:'1200';>✔</span> Secondary heater has power measurement capability.", "white", "green")
                    state.powerDevices << '${secondaryHeater}'
                } else {
                    if (enablewarning) log.warn "$secondaryHeater does not have power measurement capability"
                }
                input "alternateHeaters", "bool", title: "Alternate Heaters?", defaultValue: false, submitOnChange: true
                if (alternateHeaters) {
                    input "heaterAlternateDelay", "number", title: "Delay between heater swaps (minutes)", required: true, defaultValue: 30
                }
            } else {
                app.removeSetting("secondaryHeater")
                app.removeSetting("alternateHeaters")
                app.removeSetting("heaterAlternateDelay")
            }
            
            input "desiredTemperature", "number", title: "Desired Temperature (°F)", required: true, range: "80..104", defaultValue: 100, submitOnChange: true
            input "highTempThreshold", "number", title: "High Temperature Threshold (°F)", required: true, range: "90..110", defaultValue: 106, submitOnChange: true
        }

        section("Power Management"){
            if (enabledebug) log.debug "state.powerDevices: $state.powerDevices"
            if (state.powerDevices.size() > 0) {
                input "maxPowerAllowance", "number", title: "Set a maximum total power allowance (in watts)", defaultValue: 2000
            }
        }

        section("Water Settings"){
            input "water", "capability.switch", title: "Level up the water", submitOnChange: true
            input "water_as_heat_source", "bool", title: "Use $water as a heat source", submitOnChange: true
            if (water_as_heat_source) {
                state.max_water_off_interval = 10
                def max_water_off_interval = state.max_water_off_interval == null ? 10 : state.max_water_off_interval
                input "water_off_interval", "number", title: "Run for how long?", description: "Time in minutes", value: $max_water_off_interval, submitOnChange: true
                if (water_off_interval > max_water_off_interval) {
                    app.updateSetting("water_off_interval", [type: "number", value: max_water_off_interval])
                    paragraph format_text("Water on duration cannot exceed $max_water_off_interval minutes!", "white", "red")
                }
                state.water_off_interval = 30
                def water_off_interval = state.water_off_interval == null ? 10 : state.water_off_interval
                input "water_off_duration", "number", title: "Water off for how long?", description: "Time in minutes", value: $max_water_off_interval, submitOnChange: true
                if (water_off_duration < water_off_interval) {
                    app.updateSetting("water_off_duration", [type: "number", value: water_off_interval])
                    paragraph format_text("Water off duration cannot be less $water_off_interval minutes!", "white", "red")
                }
                input "low_temp_threshod_for_water_as_heat_source", "number", title: "Set a low temp threshold below which $water is used as a heat source (in °F)", submitOnChange: true


            }
        }

        section("Sensor Settings") {
            input "waterLevelSensor", "capability.waterSensor", title: "Select Water Level Sensor", required: true, submitOnChange: true
            input "waterTempSensor", "capability.temperatureMeasurement", title: "Select Water Temperature Sensor", required: true, submitOnChange: true
        }

        section("Notifications") {
            input "notificationDevices", "capability.notification", title: "Select notification devices", multiple: true, required: false
            input "speakers", "capability.speechSynthesis", title: "Select speakers for audio notifications", multiple: true, required: false
        }

        section("Manual Control") {
            input "runButton", "button", title: "RUN", submitOnChange: true
            if (pauseMode) {
                input "pauseButton", "button", title: "RESUME", submitOnChange: true
            } else {
                input "pauseButton", "button", title: "PAUSE", submitOnChange: true
            }
            input "emergencyOffButton", "button", title: "Emergency Off", submitOnChange: true
            if (state.installed) {
                input "updateButton", "button", title: "UPDATE", submitOnChange: true
            } else {
                input "installButton", "button", title: "INSTALL", submitOnChange: true
            }
            if (useSecondaryHeater && alternateHeaters) {
                input "swapHeatersButton", "button", title: "Swap Heaters", submitOnChange: true
            }
            if (useSecondaryPump && alternatePumps) {
                input "swapPumpsButton", "button", title: "Swap Pumps", submitOnChange: true
            }
            input "testMode", "bool", title: "Test Mode (runs only by hitting the RUN button)", submitOnChange: true
            if (testMode) {
                if (enablewarning) log.warn "Test mode activated for 10 minutes"
                runIn(600, resetTestMode)
            }
            else {
                if (enableinfo) log.info "Test mode is disabled."
            }
            if (notificationDevices || speakers) {
                input "testAlerts", "button", title: "Test Alert System", submitOnChange: true
            }
            input "refreshDevices", "button", title: "refresh devices states", submitOnChange: true
            input "resetLabel", "button", title: "Reset App's Label", submitOnChange: true
            if (water) {
            input "wateroff", "button", title: "Water Off", submitOnChange: true
            input "wateron", "button", title: "Water On", submitOnChange: true
            }
        }

        section("Logging") {
            input "enabledebug", "bool", title: "Enable debug logging", defaultValue: true
            input "enableinfo", "bool", title: "Enable info logging", defaultValue: true
            input "enablewarning", "bool", title: "Enable warning logging", defaultValue: true
            input "enabletrace", "bool", title: "Enable trace logging", defaultValue: true
        }
    }
}

/**
 * Runs when the app is first installed.
 * Calls the initialize() method to set up the app.
 */
/** 
 * Last Updated: 2025-01-05
 */

def installed() {
    if (enabledebug) log.debug "Installed with settings: ${settings}"
    state.installed = true
    initialize()
}

/**
 * Runs every time user settings are updated.
 * Unsubscribes from all events and reinitializes the app.
 */
/** 
 * Last Updated: 2025-01-05
 */

def updated() {
    if (enabledebug) log.debug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

/**
 * Initializes the app by setting up event subscriptions and scheduling.
 * This method is called after installation and every update.
 */
/** 
 * Last Updated: 2025-01-05
 */

def initialize() {
    if (enabledebug) log.debug "Initializing Hot Tub Manager"
    state.powerConsumption = [: ]  // Initialize as an empty map
    state.powerDevices = []
    subscribe(waterLevelSensor, "water", waterLevelHandler)
    subscribe(waterTempSensor, "temperature", temperatureHandler)
    subscribe(app, "pauseMode", pauseModeHandler)





    if (mainHeater?.hasCapability("powerMeter") || mainHeater?.hasAttribute("power")) {
        subscribe(mainHeater, "power", powerHandler)
        state.powerDevices << '${mainHeater}'
    }

    if (useSecondaryHeater && (secondaryHeater?.hasCapability("powerMeter") || secondaryHeater?.hasAttribute("power"))) {
        subscribe(secondaryHeater, "power", powerHandler)
        state.powerDevices << '${secondaryHeater}'
    }

    if (primaryPump?.hasCapability("powerMeter") || primaryPump?.hasAttribute("power")) {
        subscribe(primaryPump, "power", powerHandler)
        state.powerDevices << '${primaryPump}'
    }

    if (useSecondaryPump && (secondaryPump?.hasCapability("powerMeter") || secondaryPump?.hasAttribute("power"))) {
        subscribe(secondaryPump, "power", powerHandler)
        state.powerDevices << '${secondaryPump}'
    }

    state.powerConsumption = [: ]

    runEvery1Minute(checkAndUpdateSystem)
    runIn(1800, disable_logging)
    runIn(3600, disable_description)
    runIn(86400, disable_trace)
}

def pauseModeHandler(evt) {
    if (enabledebug) log.debug "Pause mode changed to: ${evt.value}"
    if (evt.value == "true") {
        if (enableinfo) log.info "System paused. Disabling all devices."
        disableSystem()
        handleLabel([label: '(paused)', append: true])
    } else {
        if (enableinfo) log.info "System unpaused. Resuming normal operation."
        handleLabel([label: '(paused)', append: false])
        main()
    }
}

def handleLabel(Map params) {

    def label = params.label
    def append = params.append ?: false
    def temperature = params.temperature ?: false
    def waterUpdate = params.waterUpdate ?: false
    def powerUpdate = params.powerUpdate ?: false 

    def baseName = settings?.appName ?: 'Hot Tub Manager'
    def s = " "

    // Initialize label parts if not already present
    state.temperatureLabelPart = state.temperatureLabelPart ?: ""
    state.waterLabelPart = state.waterLabelPart ?: ""
    state.powerLabelPart = state.powerLabelPart ?: ""
    state.otherLabelPart = state.otherLabelPart ?: ""


    // Update label parts based on parameters
    if (append && !temperature && !waterUpdate && !powerUpdate) {
        // Handle generic append (e.g., paused)
        def textColor = "red"
        def span = "<span style='color:${textColor}; font-weight:900;'>"
        state.otherLabelPart = "${s}${span}${label}</span>"
        if (enabledebug) log.debug "Set paused label part: ${state.otherLabelPart}"
    }

    if (!append && !temperature && !waterUpdate && !powerUpdate) {
        state.otherLabelPart = ""
    }

    if (temperature) {
        // Handle temperature update
        def textColor = label?.toDouble() < desiredTemperature ? "green" : "red"
        def span = "<span style='color:${textColor}; font-weight:800;'>"
        state.temperatureLabelPart = "${s}${span}${label}°F</span>"
        if (enabledebug) log.debug "Set temperature label part: ${state.temperatureLabelPart}"
    }

    if (waterUpdate) {
        // Handle water level update
        def textColor = label == "Water High" ? "darkblue" : "teal"
        def span = "<span style='color:${textColor}; font-weight:800;'>"
        state.waterLabelPart = "${s}${span}${label}</span>"
        if (enabledebug) log.debug "Set water label part: ${state.waterLabelPart}"
    }

    if (powerUpdate) {
        // Handle power update
        def textColor = "red"
        def span = "<span style='color:${textColor}; font-weight:800;'>"
        state.waterLabelPart = "${s}${span}${label}</span>"
        if (enabledebug) log.debug "Set power label part: ${state.powerUpdate}"
    }


    // Construct the new label from parts
    def parts = [baseName]
    if (state.temperatureLabelPart) {
        parts << state.temperatureLabelPart
    }
    if (state.waterLabelPart) {
        parts << state.waterLabelPart
    }
    if (state.powerLabelPart) {
        parts << state.powerLabelPart
    }
    if (state.otherLabelPart) {
        parts << state.otherLabelPart
    }

    def newLabel = parts.join('')

    if (enableinfo) {
        if (enableinfo) log.info "********************************************************************"
        if (enableinfo) log.info "Updating app label to: $newLabel"
        if (enableinfo) log.info "********************************************************************"
    }

    app.updateLabel(newLabel)
    state.lastLabel = newLabel
}

def resetLabel(){
    if (enabledebug) log.debug "Reseting application's label to default base name..................."
    def baseName = settings?.appName ?: 'Hot Tub Manager'
    app.updateLabel(baseName)
    state.lastLabel = baseName
    if (enabledebug) log.debug "App label reset to: ${app.label}"
}

def resetTestMode(){
    app.updateSetting('testMode', [type: 'bool', value: false])
}

/**
 * Handles app button events.
 * @param btn The button event object
 */
/** 
 * Last Updated: 2025-01-05
 */

def appButtonHandler(btn) {
    switch (btn) {
        case "runButton":
            runButtonAction()
            break
        case "pauseButton":
            pauseUnpauseAction()
            break
        case "emergencyOffButton":
            emergencyOffAction()
            break
        case "updateButton":
            updated()
            break
        case "installButton":
            installed()
            break
        case "swapHeatersButton":
            manualSwapHeaters()
            break
        case "swapPumpsButton":
            manualSwapPumps()
            break
        case "testAlerts":
            checkDeviceStates()
            break
        case "refreshDevices":
            Poll()
            poll_power_meters
            break
        case "resetLabel":
            resetLabel()
            break
        case "wateroff":
            waterOff()
            break
        case "wateron":
            waterOn()
            break
        default:
            if (enablewarning) log.warn "Unknown button pressed: ${btn}"
    }
}

def manualSwapHeaters() {
    if (enableinfo) log.info "Manual heater swap requested"
    state.lastHeaterSwapTime = now() - (heaterAlternateDelay * 60000) // Force swap by setting last swap time in the past
    enableHeaters()
}

def manualSwapPumps() {
    if (enableinfo) log.info "Manual pump swap requested"
    state.lastPumpSwapTime = now() - (pumpAlternateDelay * 60000) // Force swap by setting last swap time in the past
    managePumps()
}

/**
 * Executes the action for the Run button.
 */
/** 
 * Last Updated: 2025-01-05
 */

def runButtonAction() {
    if (enableinfo) log.info "Manually running system check"
    main("runButtonAction")
}

/*
 * Executes the action for the Pause/Unpause button.
 */
/** 
 * Last Updated: 2025-01-05
 */

def pauseUnpauseAction() {
    // Toggle the pause mode
    boolean newPauseMode = !pauseMode
    app.updateSetting("pauseMode", [type: "bool", value: newPauseMode])
    pauseModeHandler([value: newPauseMode.toString()])
}

/**
 * Executes the action for the Emergency Off button.
 */
/** 
 * Last Updated: 2025-01-05
 */

def emergencyOffAction() {
    if (enablewarning) log.warn "Emergency Off activated. Shutting down all systems."
    disableSystem()
    app.updateSetting("pauseMode", [type: "bool", value: true])
    // You might want to implement additional safety measures here
}

/**
 * Handles changes in the water level sensor state.
 * Immediately disables the system if water level is low.
 * @param evt The event object containing the new water level state
 */
/** 
 * Last Updated: 2025-01-05
 */

def waterLevelHandler(evt) {
    if (enabledebug) log.debug "Water level changed to: '${evt.value}'"

    dry = dry()
    state.waterState = waterState

    if (!dry) {
        if (enableinfo) log.info "Water level normal. Checking if system can be enabled."
        handleLabel([label: "Water High", append: true, waterUpdate: true])
        handleLabel([label: "Water Low", append: false, waterUpdate: true])
        if (state.alertScheduled == null || state.alertScheduled) {
            if (enabletrace) log.trace "unscheduling alerts"
            state.alertScheduled = false
            unschedule(sendNotification)
        }
    }
    else {
        disableSystem()
        handleLabel([label: "Water High", append: false, waterUpdate: true])
        handleLabel([label: "Water Low", append: true, waterUpdate: true])
    }
    checkAndUpdateSystem()
}


/**
 * Checks the water level after a 10-second delay.
 * If the water level is still low, it disables the system.
 */
/** 
 * Last Updated: 2025-01-05
 */

def checkWaterLevel() {
    if (enabledebug) log.debug "Checking water level"
    if (waterLevelSensor.currentValue("water") != "dry") {
        if (enableinfo) log.info "Water level has returned to normal. Attempting to enable system."
        checkAndUpdateSystem()
    } else {
        if (enablewarning) log.warn "Water level still low. System remains disabled."
        // Schedule another check
        runIn(60, checkWaterLevel)
    }
}

/**
 * Handles changes in the water temperature.
 * @param evt The event object containing the new temperature reading
 */
/** 
 * Last Updated: 2025-01-05
 */

def temperatureHandler(evt) {
    if (enabledebug) log.debug "Water temperature changed to: ${evt.value}°F"
    handleLabel([label: evt.value, append: true, temperature: true])
    checkAndUpdateSystem()
}

/**
 * Handles changes in heater power consumption.
 * @param evt The event object containing the new power consumption reading
 */
/** 
 * Last Updated: 2025-01-05
 */

def powerHandler(evt) {
    state.lastPowerEvent = state.lastPowerEvent == null ? now() : state.lastPowerEvent
    interval = 40
    if (state.lastPowerEvent && now() - state.lastPowerEvent < interval * 1000) {
        if (enabledebug) log.debug "last power event was reported less then $interval seconds ago. Skipping"
        return
    }
    state.lastPowerEvent = now()

    if (enabledebug) log.debug "<span style='color:red'; font-weight='900';>Device ${evt.device} power changed to: ${evt.value}W</span>"

    // Update power consumption for the specific device
    state.powerConsumption[evt.device.id] = evt.value.toDouble()

    state.totalPower = 0.0
   
    def totalPower = state.powerConsumption.values().sum() ?: 0

    if (enabledebug) {
        if (enabledebug) log.debug "state.powerConsumption.size() => ${state.powerConsumption.size()}"
        if (enabledebug) log.debug "state.powerDevices.size() => ${state.powerDevices.size()}"
    }

    if (state.powerConsumption.size() != state.powerDevices.size()) { 
        def ph = mainHeater.currentValue("power")
        def sh = useSecondaryHeater ? secondaryHeater?.currentValue("power") : 0.0
        def pp = primaryPump.currentValue("power")
        def sp = useSecondaryPump ? secondaryPump?.currentValue("power") : 0.0

        if (enabledebug) {
            if (enabledebug) log.debug "ph power value: $ph W"
            if (enabledebug) log.debug "sh power value: $sh W"
            if (enabledebug) log.debug "pp power value: $pp W"
            if (enabledebug) log.debug "sp power value: $sp W"
        }
        totalPower = ph + sh + pp + sp
    }

    handleLabel([label: "${totalPower}W", append: true, powerUpdate: true])

    state.totalPower = totalPower
    if (enabletrace) log.trace "Total power consumption is: <span style='color:red'; font-weight:'900';>${totalPower}</span>"

    state.totalPower = totalPower

    if (totalPower > maxPowerAllowance) {
        if (enablewarning) log.warn "<span style='color:red'; font-weight:'900';>Total power consumption (${totalPower}W) exceeds allowance (${maxPowerAllowance}W)</span>"
        managePowerConsumption()
    }
}

def managePowerConsumption() {
    def excessPower = state.totalPower - maxPowerAllowance
    if (enabledebug) log.debug "Attempting to reduce power consumption by ${excessPower}W"

    // First, try to swap pumps if possible
    if (useSecondaryPump && alternatePumps) {
        def currentPump = state.lastUsedPump == "primary" ? primaryPump : secondaryPump
        def alternatePump = state.lastUsedPump == "primary" ? secondaryPump : primaryPump

        if (state.powerConsumption[currentPump.id] > state.powerConsumption[alternatePump.id]) {
            if (enableinfo) log.info "Swapping to lower power pump"
            swapPumps()
            runIn(60, checkPowerAfterSwap)
            poll_power_meters()
            return
        }
    }

    // If pump swap didn't solve the issue or wasn't possible, try heater swap
    if (useSecondaryHeater && alternateHeaters) {
        def currentHeater = state.lastUsedHeater == "main" ? mainHeater : secondaryHeater
        def alternateHeater = state.lastUsedHeater == "main" ? secondaryHeater : mainHeater

        if (state.powerConsumption[currentHeater.id] > state.powerConsumption[alternateHeater.id]) {
            if (enableinfo) log.info "Swapping to lower power heater"
            swapHeaters()
            poll_power_meters()
            runIn(60, checkPowerAfterSwap)
            return
        }
    }

    // If swaps didn't work, start disabling devices
    if (useSecondaryPump) {
        secondaryPump.off()
        if (enablewarning) log.warn "Disabled secondary pump to reduce power consumption"
        runIn(60, checkPowerAfterDisable)
    } else if (useSecondaryHeater) {
        secondaryHeater.off()
        if (enablewarning) log.warn "Disabled secondary heater to reduce power consumption"
        runIn(60, checkPowerAfterDisable)
    } else {
        log.error "Unable to reduce power consumption below allowance. Manual intervention may be required."
        sendNotification("ALERT: Power consumption exceeds allowance. Manual check required.", true)
    }
}

def checkPowerAfterSwap() {
    if (state.totalPower <= maxPowerAllowance) {
        if (enableinfo) log.info "Power consumption now within allowance after device swap"
    } else {
        if (enablewarning) log.warn "Power consumption still exceeds allowance after swap. Attempting further reduction."
        managePowerConsumption()
    }
}

def checkPowerAfterDisable() {
    if (state.totalPower <= maxPowerAllowance) {
        if (enableinfo) log.info "Power consumption now within allowance after disabling device"
    } else {
        if (enablewarning) log.warn "Power consumption still exceeds allowance after disabling device. Manual intervention may be required."
        sendNotification("ALERT: Power consumption still exceeds allowance after automatic adjustments. Manual check required.", true)
    }
}

/**
 * Checks the current system state and updates it accordingly.
 * This method is the core logic of the app, managing temperature control and safety.
 */
/** 
 * Last Updated: 2025-01-05
 */

def checkAndUpdateSystem() {
    if (enabledebug) log.debug "Checking and updating system state"
    main("checkAndUpdateSystem")
}

/**
 * Main function to evaluate all conditions and take appropriate actions.
 * This function is called by the run button and scheduled updates.
 */
/** 
 * Last Updated: 2025-01-05
 */

def main(calledby = 'Unknown') {

    def currentTemp = waterTempSensor.currentValue("temperature")
    state.currentTemp = currentTemp
    def waterLevel = waterLevelSensor.currentValue("water")
    state.waterLevel = waterLevel

    if (state.systemDisabled) {
        disableSystem()
        return
    }


    def waterLabel = waterLevel == 'wet' ? 'Water High' : 'Water Low'
    handleLabel([label: waterLabel, append: true, waterUpdate: true])
    handleLabel([label: currentTemp, append: true, temperature: true])

    if (location.mode in restricted) {
        if (enabledebug) log.debug "Location in restricted mode. Critical Temp and Water Level still monitored..."
        if (currentTemp >= highTempThreshold || dry()) {
            disableSystem()
            state.water_off_restricted_mode == state.water_off_restricted_mode ? state.water_off_restricted_mode : false
            if (!state.water_off_restricted_mode && water.currentValue("switch") == "on") // do it once only, so water can be used
            {
                state.water_off_restricted_mode = true
                if (state.water_on_by_app) {
                    waterOff()
                }
            }
        }
        return
    }


    if (pauseMode && calledby != "runButtonAction") {
        if (enableinfo) log.info "System is paused"
        return
    }



    if (testMode && calledby != "runButtonAction") {
        if (enabledebug) log.debug "(TEST MODE | main eval RETURNED)"
        return
    }



    handleRefillwater()


    if (currentTemp >= highTempThreshold) {
        if (enablewarning) log.warn "High temperature threshold reached. Disabling heaters."
        disableHeaters()
    } else if (currentTemp < desiredTemperature) {
        if (enableinfo) log.info "Current temperature (${currentTemp}°F) below desired (${desiredTemperature}°F). Enabling heaters."
        enableHeaters()
    } else {
        if (enableinfo) log.info "Temperature (${currentTemp}°F) at or above desired. Disabling heaters."
        disableHeaters()
    }

    managePumps()


}

def handleWaterAsHeatSource(){
    if (enabledebug) log.debug "state.water_off_by_app: ${state.water_off_by_app}"
    if (enabledebug) log.debug "state.water_on_by_app: ${state.water_on_by_app}"
    if (enabledebug) log.debug "water_off_duration: ${water_off_duration} minutes"
    if (enabledebug) log.debug "state.refilling: ${state.refilling}"

    if (water && water_as_heat_source) {
        if (enabledebug) log.debug "Water handler..."

        if (state.currentTemp <= low_temp_threshod_for_water_as_heat_source) {
            if (state.last_time_water_was_turned_off == null || now() - state.last_time_water_was_turned_off > water_off_duration * 60 * 1000) {
                if (!state.WATER_HEATING) {
                    waterOn()
                    state.WATER_HEATING = true
                    runIn(water_on_duration * 60, waterOff)
                }
            } else {
                if (enabledebug) log.debug "Water was off for less than ${water_off_duration} minutes. Not turning it on yet."
            }
        } else {
            waterOff()
        }
    }
}

def waterOff(){
    state.refilling = state.refilling == null ? false : state.refilling
    if (state.refilling && dry()) {
        if (enabletrace) log.trace "Refilling... "
        return
    }
    if (state.water_on_by_app) {
        if (enablewarning) log.warn "Shutting off the water..."
        if (!waterIsOff()) water?.off()
        state.WATER_HEATING = false
        state.last_time_water_was_turned_off = now()
        state.water_off_by_app = true
        state.water_on_by_app = false
        state.refilling = false
    } else {
        if (enablewarning) log.warn "Water was manually turned off. Skipping heat with water."
    }
}

def waterOn(){
    if (state.water_off_by_app) {
        if (enablewarning) log.warn "Turning on the water switch..."
        if (waterIsOff()) water?.on()
        state.last_time_water_was_turned_on = now()
        state.water_off_by_app = false
        state.water_on_by_app = true
        state.heat_refill = true
    } else {
        if (enablewarning) log.warn "Water was manually turned off. Skipping heat with water."
    }
}

def waterIsOff(){
    return water.currentValue("switch") == "off"
}

def handleRefillwater(){
    if (water) {
        state.heat_refill = state.heat_refill == null ? false : state.heat_refill
        if (state.heat_refill && !dry()) {
            if (enableinfo) log.info "Water is managed by heat management at the moment"
            return
        }
        if (dry()) {
            if (water.currentValue("switch") == "off") {
                if (enablewarning) log.warn "Turning on $water"
                waterOn()
                unschedule(waterOff)
            }
        }
        else {
            if (state.refilling) {
                runIn(29 * 60, resetStateRefilling)
                runIn(30 * 60, waterOff) // run for an extra 30 minutes
            }
        }
    }
}

def resetStateRefilling(){
    state.refilling = false
}


/**
 * Enables the hot tub system.
 * This method is called when the water level returns to normal.
 */
/** 
 * Last Updated: 2025-01-05
 */

def enableSystem() {
    if (enableinfo) log.info "Enabling hot tub system"
    state.systemDisabled = false
    main("enableSystem")
}

/**
 * Disables the entire hot tub system.
 * This method is called when a critical issue (like low water level) is detected.
 */
/** 
 * Last Updated: 2025-01-05
 */

// Modify the disableSystem function
def disableSystem() {
    if (enablewarning) log.warn "Disabling hot tub system"
    disableHeaters()
    disablePumps()
    if (enableinfo) log.info "System disabled due to critical issue."
    state.systemDisabled = true
    return

    // Check if any devices are still on after attempting to disable them
    runIn(60, checkDeviceStates)
}

def checkDeviceStates() {
    if (pauseMode) return 

    def devicesStillOn = []

    def secondaryPumpSwitchState = secondaryPump?.currentValue("switch")
    def secondaryHeaterSwitchState = secondaryHeater?.currentValue("switch")
    def primaryPumpSwitchState = primaryPump.currentValue("switch") 
    def mainHeaterSwitchState = mainHeater.currentValue("switch")

    if (enabledebug) {
        if (enabledebug) log.debug "mainHeaterSwitchState : ${mainHeaterSwitchState}"
        if (enabledebug) log.debug "secondaryHeaterSwitchState : ${secondaryHeaterSwitchState}"

        if (enabledebug) log.debug "primaryPumpSwitchState: ${primaryPumpSwitchState}"
        if (enabledebug) log.debug "secondaryPumpSwitchState : ${secondaryPumpSwitchState}"
    }

    if (mainHeaterSwitchState == "on") {
        devicesStillOn.add(mainHeater.displayName)
    }
    if (useSecondaryHeater && secondaryHeaterSwitchState == "on") {
        devicesStillOn.add(secondaryHeater.displayName)
    }
    if (primaryPumpSwitchState == "on") {
        devicesStillOn.add(primaryPump.displayName)
    }
    if (useSecondaryPump && secondaryPumpSwitchState == "on") {
        devicesStillOn.add(secondaryPump.displayName)
    }

    if (enabledebug) log.debug "devicesStillOn: {devicesStillOn.size() > 0 ? devicesStillOn.join(', ') : 'None'}"
    if (devicesStillOn.size() > 0 || state.test_alert) {
        disableSystem() // new attempt
        runIn(60, checkDeviceStates) // run recursively as long as it's not solved
        state.test_alert = false
        def message = "ALERT: ${devicesStillOn.join(', ')} ${devicesStillOn.size() > 1 ? 'are' : 'is'} still on after system disable. Please check your hot tub system immediately!"
        sendNotification(message, true)
        Poll()
    }
}

/**
 * Enables the heaters based on the current configuration.
 * If heater alternation is enabled, it switches between main and secondary heaters.
 */
/** 
 * Last Updated: 2025-01-05
 */

/**
 * Enables the heaters based on the current configuration.
 * If heater alternation is enabled, it switches between main and secondary heaters only when necessary.
 */
/** 
 * Last Updated: 2025-01-05
 */

def enableHeaters() {

    if (dry()) {
        return
    }

    if (enableinfo) log.info "Enabling heaters. Target temperature: ${desiredTemperature}°F"
    if (alternateHeaters && useSecondaryHeater) {
        def currentTime = now()
        if (state.lastHeaterSwapTime == null || (currentTime - state.lastHeaterSwapTime) >= (heaterAlternateDelay * 60000)) {
            if (state.lastUsedHeater == "main" || state.lastUsedHeater == null) {
                if (enabledebug) log.debug "Switching to secondary heater"
                mainHeater.off()
                mainHeaterFan?.off()
                secondaryHeater.on()
                state.lastUsedHeater = "secondary"
            } else {
                if (enabledebug) log.debug "Switching to main heater"
                secondaryHeater.off()
                mainHeater.on()
                secondaryHeaterFan?.on()
                state.lastUsedHeater = "main"
            }
            state.lastHeaterSwapTime = currentTime
            runIn(heaterAlternateDelay * 60, swapHeaters)
        } else {
            if (enabledebug) log.debug "Using current heater: ${state.lastUsedHeater}: ${state.lastUsedHeater == "main" ? mainHeater.displayName : secondaryHeater.displayName}"
            if (state.lastUsedHeater == "main") {
                mainHeater.on()
                mainHeaterFan?.on()
            } else {
                secondaryHeater.on()
                secondaryHeaterFan?.on()
            }
        }
    } else {
        if (enabledebug) log.debug "Enabling main heater"
        mainHeater.on()
        mainHeaterFan?.on()
        if (useSecondaryHeater && !alternateHeaters) {
            if (enabledebug) log.debug "Enabling secondary heater"
            secondaryHeater.on()
            secondaryHeaterFan?.on()
        }
    }
    if (water_as_heat_source) {
        handleWaterAsHeatSource()
    }
}

def handleWaterDryConfirmed() {
    // Actions to take when water is confirmed to be dry
    if (enablewarning) log.warn "Confirmed: Water is 'dry' after 10 seconds. Disabling system."
    disableSystem()
    handleLabel([label: "Water Low", append: true, waterUpdate: true])
    // Schedule a check to re-enable the system when water level returns to normal
    runIn(60, checkWaterLevel)
}

def dry() {
    waterState = waterLevelSensor.currentValue("water")
    if (waterState == "dry") {
        if (enablewarning) log.warn "Water level is low.... Checking 2s for false positive"
        pauseExecution(2000)
        waterState = waterLevelSensor.currentValue("water")
    }
    state.waterLevel = waterState
    if (waterState == "dry") {
        disableSystem()
    }
    else {
        state.systemDisabled = false
    }
    return waterState == "dry"
}

def wet(){
    return !dry()
}

/**
 * Disables all heaters.
 */
/** 
 * Last Updated: 2025-01-05
 */

def disableHeaters() {
    if (enableinfo) log.info "Disabling all heaters"
    mainHeater.off()
    mainHeaterFan?.off()
    if (useSecondaryHeater) {
        secondaryHeater.off()
        secondaryHeaterFan?.off()
    }

    unschedule(swapHeaters)
}

def disablePumps(){
    if (enableinfo) log.info "Disabling all pumps"
    primaryPump.off()
    secondaryPump?.off()
}

/**
 * Manages the operation of pumps based on the current configuration.
 */
/** 
 * Last Updated: 2025-01-05
 */

def managePumps() {
    if (dry()) {
        return
    }
    if (enabledebug) log.debug "Managing pumps"
    if (useSecondaryPump && alternatePumps) {
        def currentTime = now()
        if (state.lastPumpSwapTime == null || (currentTime - state.lastPumpSwapTime) >= (pumpAlternateDelay * 60000)) {
            if (state.lastUsedPump == "secondary" || state.lastUsedPump == null) {
                if (enabledebug) log.debug "Switching to primary pump"
                secondaryPump.off()
                primaryPump.on()
                state.lastUsedPump = "primary"
            } else {
                if (enabledebug) log.debug "Switching to secondary pump"
                primaryPump.off()
                secondaryPump.on()
                state.lastUsedPump = "secondary"
            }
            state.lastPumpSwapTime = currentTime
            runIn(pumpAlternateDelay * 60, swapPumps)
        } else {
            if (enabledebug) log.debug "Using current pump: ${state.lastUsedPump}"
            if (state.lastUsedPump == "primary") {
                primaryPump.on()
            } else {
                secondaryPump.on()
            }
        }
    } else {
        primaryPump.on()
        if (useSecondaryPump && !alternatePumps) {
            secondaryPump.on()
        }
    }
    if (enabledebug) log.debug "Pumps managed."
}

/**
 * Swaps the active heater when alternating heaters.
 */
/** 
 * Last Updated: 2025-01-05
 */

def swapHeaters() {
    if (enabledebug) log.debug "Time to swap heaters"
    enableHeaters()
}

/**
 * Swaps the active pump when alternating pumps.
 */
/** 
 * Last Updated: 2025-01-05
 */

def swapPumps() {
    if (enabledebug) log.debug "Time to swap pumps"
    managePumps()
}

def sendNotification(message, critical = false) {
    if (enabledebug) log.debug "Sending notification: $message"

    if (pauseMode) return

    // Send push notification
    if (notificationDevices) {
        notificationDevices.deviceNotification(message)
    }

    // Play sound and speak message on speakers
    if (speakers) {
        speakers.speak(message)
    }

    // If the notification is critical, you might want to repeat it or use a different sound
    if (critical) {
        state.alertScheduled = true
        runIn(60, sendNotification, [data: ["message": "CRITICAL: $message", "critical": true]]) // Repeat critical messages after 1 minute
    }
}

/**
 * Polls the devices to update their status.
 */
/** 
 * Last Updated: 2025-01-05
 */

def Poll() {
    if (enabledebug) log.debug "Polling devices"
    
    def devicesToPoll = [primaryPump, mainHeater, waterLevelSensor, waterTempSensor]
    if (useSecondaryPump) devicesToPoll.add(secondaryPump)
    if (useSecondaryHeater) devicesToPoll.add(secondaryHeater)

    devicesToPoll.each {
        device ->
        if (device.hasCommand("poll")) {
            if (enabledebug) log.debug "Polling ${device.displayName}"
            device.poll()
        } else {
            if (enablewarning) log.warn "${device.displayName} does not have polling capability"
        }
        if (device.hasCommand("refresh")) {
            if (enabledebug) log.debug "Refreshing ${device.displayName}"
            device.refresh()
        } else {
            if (enablewarning) log.warn "${device.displayName} does not have refresh capability"
        }
    }
}

/**
 * Polls power meters to update their status.
 */
/** 
 * Last Updated: 2025-01-05
 */

def poll_power_meters() {
    state.lastPoll = state.lastPoll == null ? now() : state.lastPoll

    interval = 60
    if (now() - state.lastPoll > interval * 1000) {
        return
    }

    state.lastPoll = now()

    log.debug formatText("Polling power meters...", "white", "red")
    
    def powerMeters = []

    if (primaryPump.hasCapability("powerMeter")) powerMeters.add(primaryPump)
    if (useSecondaryPump && secondaryPump.hasCapability("powerMeter")) powerMeters.add(secondaryPump)
    if (mainHeater.hasCapability("powerMeter")) powerMeters.add(mainHeater)
    if (useSecondaryHeater && secondaryHeater.hasCapability("powerMeter")) powerMeters.add(secondaryHeater)

    powerMeters.each {
        device ->
        if (device.hasCommand("poll")) {
            if (enabledebug) log.debug "Polling power meter for ${device.displayName}"
            device.poll()
        } else {
            if (enablewarning) log.warn "${device.displayName} does not have polling capability"
        }
        if (device.hasCommand("refresh")) {
            if (enabledebug) log.debug "Refreshing power meter for ${device.displayName}"
            device.refresh()
        } else {
            if (enablewarning) log.warn "${device.displayName} does not have refresh capability"
        }
    }
}

/**
 * Disables debug logging after a delay.
 */
/** 
 * Last Updated: 2025-01-05
 */

def disable_logging() {
    if (enablewarning) log.warn "Debug logging disabled..."
    app.updateSetting("enabledebug", [type: "bool", value: false])
}

/**
 * Disables info logging after a delay.
 */
/** 
 * Last Updated: 2025-01-05
 */

def disable_description() {
    if (enablewarning) log.warn "Info logging disabled..."
    app.updateSetting("enableinfo", [type: "bool", value: false])
}

/**
 * Disables warning logging after a delay.
 */
/** 
 * Last Updated: 2025-01-05
 */

def disable_warnings() {
    if (enablewarning) log.warn "Warning logging disabled..."
    app.updateSetting("enablewarning", [type: "bool", value: false])
}

/**
 * Disables trace logging after a delay.
 */
/** 
 * Last Updated: 2025-01-05
 */

def disable_trace() {
    if (enablewarning) log.warn "Trace logging disabled..."
    app.updateSetting("enabletrace", [type: "bool", value: false])
}

/**
 * Formats text with custom styling.
 * @param title The text to format
 * @param textColor The color of the text
 * @param bckgColor The background color
 * @return Formatted HTML string
 */
/** 
 * Last Updated: 2025-01-05
 */

def format_text(title, textColor, bckgColor) {
    return [
        "<div style='width:80%;",
        "background-color:${bckgColor};",
        "border: 10px solid ${bckgColor};",
        "color:${textColor};",
        "font-weight: bold;",
        "box-shadow:4px 4px 4px #bababa;",
        "margin-left:0px'>${title}",
        "</div>"
    ].join()
}

/**
 * Formats a title with custom styling.
 * @param title The title to format
 * @return Formatted HTML string
 */
/** 
 * Last Updated: 2025-01-05
 */

def format_title(title) {
    return [
        "<div style=",
        "'background-color: lightgrey;",
        "width: 80%;",
        "border: 3px solid green;",
        "padding: 10px;",
        "margin: 20px;'>${title}</div>"
    ].join()
}