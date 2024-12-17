definition(
    name: "Thermostat Manager Lite V1",
    namespace: "elfege",
    author: "ELFEGE",

    description: "A lite thermostat manager for basic operations",

    category: "Green Living",
    iconUrl: "https://www.elfege.com/penrose.jpg",
    iconX2Url: "https://www.elfege.com/penrose.jpg",
    iconX3Url: "https://www.elfege.com/penrose.jpg",
    image: "https://www.elfege.com/penrose.jpg"
)

preferences {

    page name: "MainPage"

}

def MainPage() {

    def pageProperties = [
        name: "MainPage",
        title: "${app.label}",
        nextPage: null,
        install: true,
        uninstall: true
    ]

    dynamicPage(pageProperties) {

        section("Name"){
            input "appLabel", "text", title: "Assign a name to this instance of $app.name", submitOnChange: true
            app.updateLabel(appLabel)
        }

        section("Thermostats"){
            input "thermostats", "capability.thermostat", title:"Select one or more thermostats", required: true, multiple: true, submitOnChange:true
            input "isMidea", "bool", title: "This unit runs on a Midea firmware (most split AC made in the US and Canada) [Not Implemented yet]"
            input "boost", "bool", title: "Boost when heating or cooling", submitOnChange:true
            if (boost){
                input "tempBoostHeat", "number", title: "Temperature to set Thermostat to when boost heating", defaultValue:85
                input "tempBoostCool", "number", title: "Temperature to set Thermostat to when boost cooling", defaultValue:65
            }
            input "fan_only", "bool", title: "Set thermostats to 'fan_only' instead of 'off' (recommended to avoid mold formation)", submitOnChange:true
            if(fan_only){
                def supportedFanModes = [:] 
                thermostats.each { thermostat -> 
                    def modesJson = thermostat.currentValue("supportedThermostatFanModes") ?: thermostat.currentValue("supportedFanSpeeds")
                    logDebug "modesJson: $modesJson (${modesJson.class})"
                    def modes = new groovy.json.JsonSlurper().parseText(modesJson)
                    logDebug "Parsed fanModes: $modes (${modes.class})"    
                    supportedFanModes[thermostat.displayName] = modes
                    
                    logDebug "supportedFanModes: $supportedFanModes[thermostat.displayName]"
                    
                    def defaultVal = supportedFanModes[thermostat.displayName][1]
                    
                    logDebug "defaultVal for $thermostat.displayName: ${defaultVal}"
                    input "fan_mode_on${thermostat.displayName}", "enum", 
                        title: "Select the fan mode compatible with $thermostat.displayName", 
                        options: supportedFanModes[thermostat.displayName], 
                        defaultValue: defaultVal
                }
                // state.supportedFanModes = supportedFanModes
            }
            
            input "set_to_auto_instead_of_off", "bool", title: "Set thermostats to 'auto' instead of 'off'", submitOnChange:true
            if (fan_only || set_to_auto_instead_of_off){
                if(fan_only) app.updateSetting('set_to_auto_instead_of_off', [type: "bool", value: false])
                if(set_to_auto_instead_of_off) app.updateSetting('fan_only', [type: "bool", value: false])
            }
            // input "dimmer", "capability.switchLevel", title: "Select a virtual dimmer to memoize my desired setpoint", required: true, submitOnChange:true
            
        }

        section("Temperature Sensors") {
            input "tempSensors", "capability.temperatureMeasurement", title: "Select one ore more temperature measurement devices (${thermostats[0]} will be used by default if left empty)", required: false, multiple:true, submitOnChange:true
            input "outsideTemp", "capability.temperatureMeasurement", title: "Select a sensor for outside temperature (required)", required: true, submitOnChange: true
        }

        section("Contact sensors"){
            input "contacts", "capability.contactSensor", title: "Select one ore more contacts sensor", required: false, multiple: true, submitOnChange:true
        }

        section("Modes"){
            input "restricted", "mode", title: "Restricted modes", multiple: true
            input "powerSavingModes", "mode", title: "Power Saving Mode(s)", required: false, multiple: true, submitOnChange:true
        }

        section("App Controls"){
            if(!state.installed){
                input "install", "button", title: "Install", submitOnChange:true
            }
            else {
                input "update", "button", title: "Update", submitOnChange:true
                input "pause", "button", title: "${state.paused ? 'Resume' : 'Pause'}", submitOnChange:true
                input "run", "button", title: "Test Run", submitOnChange: true
                input "reset", "button", title: "Reset Memoized States", submitOnChange: true
            }
        }
        section('Logging') {
            input 'enableDebug', 'bool', title: 'Enable debug logging', defaultValue: false, submitOnChange: true
            input 'enableTrace', 'bool', title: 'Enable trace logging', defaultValue: false, submitOnChange: true
            input 'enableWarn', 'bool', title: 'Enable warn logging', defaultValue: false, submitOnChange: true
            input 'enableInfo', 'bool', title: 'Enable info logging', defaultValue: true, submitOnChange: true
        }
    }
}
def installed(){
    logDebug "Installing with settings: $settings"
    initialize()
    
    state.installed = true 

}
def updated(){
    initialize()
}
def initialize(){
    logDebug "initializing with settings: $settings"

    unschedule()
    unsubscribe()
    
    update_app_label(state.paused)

    subscribe(location, "mode", ChangedModeHandler)
    subscribe(outsideTemp, "temperature", outsideTempHandler)
    subscribe(thermostats, "thermostatSetpoint", setPointHandler)
    subscribe(thermostats, "heatingSetpoint", setPointHandler)
    subscribe(thermostats, "coolingSetpoint", setPointHandler)

    if (tempSensors){
        subscribe(tempSensors, "temperature", temperatureHandler)
    } else {
        subscribe(thermostat, "temperature", temperatureHandler)
    }
    if (contacts){
        subscribe(contacts, "contact", contactHandler)
    }

    // Initialize debug and trace timing
    initializeLogging()

    initializeStates()
   
}
def initializeStates(){
    // initialize setpoints memoization
    state.thermostatsSetpointSTATES = state.thermostatsSetpointSTATES == null ? [:] : state.thermostatsSetpointSTATES

    if (state.thermostatsSetpointSTATES == [:]){
        //populate it if empty. 
        def allThermostats = getAllThermostats()
            
        logDebug "allThermostats: <br> ${allThermostats.join('<br>')}"

        allThermostats.each{ thermostat -> 
            logWarn "Calling memoization for $thermostat"
            updateMem(thermostat)
        }

        logInfo "state.thermostatsSetpointSTATES (after population): $state.thermostatsSetpointSTATES"
    }

}
def appButtonHandler(btn) {

    switch (btn) {
        case "pause": 
            def paused = !state.paused
            if (enablewarning) logWarn "paused = $paused"

            if (paused) {
                logInfo "unsuscribing from events..."
                unsubscribe()
                logInfo "unschedule()..."
                unschedule()
            }
            else {
                updated()
            }
            state.paused = paused
            update_app_label(paused)
            break
        case "update":
            state.paused = false
            updated()
            break
        case "install":
            state.paused = false
            installed()
            break
        case "run":
            main(calledBy="appButtonHandler")
            break
        case "reset":
            resetMem()
            break
    }
}
def setPointHandler(evt){
    logTrace "state.boostMode: ${state.boostMode} **********-------------- $evt.device $evt.name set to ${evt.value}F" 

    

    // Convert boost temperatures to strings for comparison
    def tbh = tempBoostHeat?.toString() ?: "85"
    def tbc = tempBoostCool?.toString() ?: "65"
    
    // Create array of possible boost temperature formats
    def boostTemps = [
        tbh, tbc,
        "${tbh}.0", "${tbc}.0",
        tempBoostHeat as BigDecimal, tempBoostCool as BigDecimal,
        (tempBoostHeat as BigDecimal).setScale(1), (tempBoostCool as BigDecimal).setScale(1)
    ]
    
    // Check if current value is a boost temperature
    if(evt.value in boostTemps){
        log.warn "boostTemps -- operation in boost mode. Not updating state.thermostatsSetpointSTATES"
        return
    }
    // if(state.boostMode){
    //     log.warn "state.boostMode -- operation in boost mode. Not updating state.thermostatsSetpointSTATES"
    //     return
    // }

    // Initialize tracking maps
    state.lastEvent = state.lastEvent ?: now()
    state.lastUpdatedEventName = state.lastUpdatedEventName ?: [:]
    state.pendingEvents = state.pendingEvents ?: [:]
    
    def minUpdateInterval = 1 * 60 * 60 * 1000  // 1 hour
    def deviceId = evt.device.id
    
    // Track events per device
    state.pendingEvents[deviceId] = state.pendingEvents[deviceId] ?: []
    state.pendingEvents[deviceId] << [name: evt.name, value: evt.value, time: now()]
    
    // Wait for all events in the batch to arrive
    runIn(2, "processPendingEvents", [data: [deviceId: deviceId]])
    
    // If this specific event type hasn't been updated in an hour, force an update
    def lastUpdateForThisEvent = state.lastUpdatedEventName[evt.name] ?: 0
    if ((now() - lastUpdateForThisEvent) > minUpdateInterval) {
        logDebug "Forcing update for ${evt.name} due to time elapsed"
        processEvent(evt)
    }
}

def processPendingEvents(data) {
    def deviceId = data.deviceId
    def events = state.pendingEvents[deviceId] ?: []
    
    if (!events) {
        return
    }
    
    // Get the most recent event only
    def latestEvent = events.max { it.time }
    def device = getDeviceById(deviceId, getAllThermostats())
    
    if (!device) {
        log.error "Device not found for ID: ${deviceId}"
        state.pendingEvents[deviceId] = []
        return
    }

    // Check for boost temperatures
    def tempBoostHeat = tempBoostHeat?.toString() ?: "85"
    def tempBoostCool = tempBoostCool?.toString() ?: "65"
    
    def boostTemps = [
        tempBoostHeat, tempBoostCool,
        "${tempBoostHeat}.0", "${tempBoostCool}.0",
        tempBoostHeat as BigDecimal, tempBoostCool as BigDecimal,
        (tempBoostHeat as BigDecimal).setScale(1), (tempBoostCool as BigDecimal).setScale(1)
    ]
    
    if(!(latestEvent.value in boostTemps)) {
        logWarn "updating mem from setPointHandler for $device"
        updateMem(device, "all")  // Update all setpoints since they're all the same
        
        // Update timestamps for all event types
        ["thermostatSetpoint", "coolingSetpoint", "heatingSetpoint"].each { eventName ->
            state.lastUpdatedEventName[eventName] = now()
        }
        
        main(calledBy="setPointHandler")
    } else {
        logWarn "operation in boost mode. Not updating state.thermostatsSetpointSTATES"
    }
    
    // Clear pending events for this device
    state.pendingEvents[deviceId] = []
}

def processEvent(evt) {
    // Convert boost temperatures to strings for comparison
    def tempBoostHeat = tempBoostHeat?.toString() ?: "85"
    def tempBoostCool = tempBoostCool?.toString() ?: "65"
    
    def boostTemps = [
        tempBoostHeat, tempBoostCool,
        "${tempBoostHeat}.0", "${tempBoostCool}.0",
        tempBoostHeat as BigDecimal, tempBoostCool as BigDecimal,
        (tempBoostHeat as BigDecimal).setScale(1), (tempBoostCool as BigDecimal).setScale(1)
    ]
    
    if(!(evt.value in boostTemps)) {
        logWarn "updating mem from setPointHandler for ${evt.device}"
        updateMem(evt.device, evt.name)
        state.lastUpdatedEventName[evt.name] = now()
        main(calledBy="setPointHandler")
    } else {
        logWarn "operation in boost mode. Not updating state.thermostatsSetpointSTATES"
    }
}
def ChangedModeHandler(evt){
    logInfo "Location is in ${evt.value} mode" 
    main(calledBy="ChangedModeHandler")
}
def temperatureHandler(evt){
    logInfo "$evt.device temperature is ${evt.value}F" 
    main(calledBy="temperatureHandler")
}
def outsideTempHandler(evt){
    logInfo "$evt.device temperature is ${evt.value}F" 
    main(calledBy="outsideTempHandler")
}
def contactHandler(evt){
    logInfo "$evt.device is ${evt.value}" 
    main(calledBy="contactHandler")
}

def main(calledBy="unknown"){
    
    def lapse = 5 // interval in seconds

    state.lastRun = state.lastRun == null ? now() : state.lastRun

    logDebug("state.thermostatsSetpointSTATES: <br> ${state.thermostatsSetpointSTATES}")

    if (now() - state.lastRun > lapse * 1000){

        state.lastRun = now() 

        def allThermostats = getAllThermostats()

        def contactOpen = contacts.any{it -> it.currentValue("contact") == "open"}

        if(contactOpen){
            if(thermostats.any{it -> it.currentValue("thermostatMode") != "off"}) {
                def openContacts = contacts.findAll{it -> it.currentValue('contact') == 'open'}
                logWarn "${openContacts.join(', ')} ${openContacts.size() > 1 ? 'are' : 'is'} open - turning off HVAC"
                thermostats.setThermostatMode("off")
            }
            return
        }

        def need = get_need()


        allThermostats.each { thermostat -> 

            setThermostatsMode([deviceId: thermostat.id, need: need])

            if (!state.boostMode){
                // restore memoized setpoint
                def thermStates = state.thermostatsSetpointSTATES[thermostat.displayName]
                def targetTemp = need == "cool" ? thermStates.coolingSetpoint : 
                need == "heat" ? thermStates.heatingSetpoint : thermStates.thermostatSetpoint


                def attribute = need in ["cool", "heat"] ? "${need}ingSetpoint" : "thermostatSetpoint"

                setThermostatsSetpoint([
                        deviceId: thermostat.id, 
                        need: need, 
                        attribute: attribute != "" ? attribute : false,
                        value: targetTemp, 
                        turboRequired:boost,
                        calledBy:"main"
                        ])
            }
        
        }
        
    }
    else {
        logWarn "main ran less than $lapse seconds ago. Skipping"
    }
}

def handleBoost(need){
    logWarn "handling boost (need:$need)"
    if(boost){
        
        if(need in ["heat", "cool"]){

            state.boostMode = true
            
            def val = need == "heat" ? tempBoostHeat : tempBoostCool
            def attribute = "${need}ingSetpoint"

            thermostats.each { thermostat -> 
                if(thermostat.currentValue(attribute) != val) {
                    logDebug "thermostat -> $thermostat"
                    updateMem(thermostat, "${need}ingSetpoint")
                    log.debug "CALLING RUNIN TO setThermostatsSetpoint"
                    runIn(1, "setThermostatsSetpoint",
                    [
                        data: [
                            deviceId: thermostat.id,
                            need: need,
                            attribute: attribute,
                            value: val,
                            turboRequired:true,
                            calledBy:"handleBoost"
                            ],
                        overwrite: false
                    ])

                    return 
                }
            }
        }
    }
    
    state.boostMode = false
    
}

def updateMem(thermostat, attribute="all"){

    logDebug "--------------- Updating memoization state for $thermostat"

    

    //ensure it doesn't initialize with boost values if this was called during a boosting operation
    // Convert current values to BigDecimal with scale 1
    def currSP = (thermostat.currentValue("thermostatSetpoint") as BigDecimal).setScale(1)
    def currCSP = (thermostat.currentValue("coolingSetpoint") as BigDecimal).setScale(1)
    def currHSP = (thermostat.currentValue("heatingSetpoint") as BigDecimal).setScale(1)


    // Convert boost temps to BigDecimal with scale 1
    def boostTempArray = [
        (tempBoostCool as BigDecimal).setScale(1),
        (tempBoostHeat as BigDecimal).setScale(1)
    ]

    def thermostatSetpoint = currSP in boostTempArray ? 72.0 : currSP
    def coolingSetpoint = currCSP in boostTempArray ? 72.0 : currCSP
    def heatingSetpoint = currHSP in boostTempArray ? 75.0 : currHSP

    logDebug """
        <br> tempBoostCool: $tempBoostCool (${tempBoostCool.class})
        <br> tempBoostHeat: $tempBoostHeat (${tempBoostHeat.class})
        <br> currSP: $currSP (${currSP.class})
        <br> currHSP: $currHSP (${currHSP.class})
        <br> currCSP: $currCSP (${currCSP.class})
        <br> boostTempArray: $boostTempArray
        <br> currSP in boostTempArray ? ${currSP in boostTempArray}
        <br> currHSP in boostTempArray ? ${currHSP in boostTempArray}
        <br> currCSP in boostTempArray ? ${currCSP in boostTempArray}
        """

    // TODO: warn user

    def previousSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.thermostatSetpoint ?: currSP
    def previousCSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.coolingSetpoint ?: currCSP
    def previousHSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.heatingSetpoint ?: currHSP

    thermostatSetpoint = attribute in ["thermostatSetpoint", "all"] ? thermostatSetpoint : previousSP
    coolingSetpoint = attribute in ["coolingSetpoint", "all"] ? coolingSetpoint : previousCSP    
    heatingSetpoint = attribute in ["heatingSetpoint", "all"] ? heatingSetpoint : previousHSP

    if (attribute != "all") {
    logTrace """Selective update for $attribute:
        <br> ${attribute == "thermostatSetpoint" ? "Updating" : "Preserving"} thermostatSetpoint: $thermostatSetpoint
        <br> ${attribute == "coolingSetpoint" ? "Updating" : "Preserving"} cooling setpoint: $coolingSetpoint
        <br> ${attribute == "heatingSetpoint" ? "Updating" : "Preserving"} heating setpoint: $heatingSetpoint
        """
    }

    state.thermostatsSetpointSTATES[thermostat.displayName] = [
        thermostatSetpoint: thermostatSetpoint,
        coolingSetpoint: coolingSetpoint,
        heatingSetpoint: heatingSetpoint 
    ]
}
def resetMem(){
    logInfo "Resetting states..."
    state.thermostatsSetpointSTATES = [:]
    initializeStates() // repopulate
    log.trace "Done."

}
def setThermostatsMode(data){
    // deviceId: The unique identifier of the thermostat device
    // Example: "1150" or "2f3a8b9c-1234-5678-90ab-cd1234567890"
    def deviceId = data.deviceId
    
    // allThermostats: Array of all unique thermostat devices from user preferences
    // Example: ["AC OFFICE", "AC BEDROOM", "AC LIVING ROOM"]
    def allThermostats = getAllThermostats()
    
    // device: The specific thermostat device object found by its ID
    // Example: {id: "1150", name: "AC OFFICE", currentMode: "heat"}
    def device = getDeviceById(deviceId, allThermostats)
    
    // need: The desired thermostat operation mode
    // Example: "heat", "cool", "auto", "off", or "fan_only"
    def need = data.need

    def cmd = need == "fan_only" ? "setThermostatFanMode" : "setThermostatMode"
    
    def attribute = need == "fan_only" ? "thermostatFanMode" : "thermostatMode" 

    
        
    if(device){

        def fan_mode = settings["fan_mode_on${device.displayName}"] ?: "false"
        
        def mode = attribute == "thermostatFanMode" ? fan_mode : need
        if (device.currentValue(attribute) != mode){
            
            device."${cmd}"(mode)
            logTrace "${device.displayName} ${attribute} set to ${mode}"

            if (attribute == "thermostatFanMode") {  
                
                logWarn "fan mode required for $device.displayName: $fan_mode"              
                if (!fan_mode) {
                    log.error "ERROR: fan mode not set!"
                    return
                }
            }
            else {
                logTrace "reverting fan to auto"
                device.setThermostatFanMode("auto")
            }

            handleBoost(mode)
        }
        else {
            logTrace "${device.displayName} ${attribute} <b>ALREADY</b> set to ${need}"
        }
    }
    else {
        log.error "Device with ID ${data.deviceId} not found (setThermostatsMode)."
        return 
    }

    
}
def setThermostatsSetpoint(data){

    logDebug "setThermostatsSetpoint called by ${data.calledBy}"

    // need: The required HVAC operation mode
    // Example: "heat" or "cool"
    def need = data.need

    // attribute: The thermostat setting to be modified
    // Example: "heatingSetpoint" or "coolingSetpoint"
    def attribute = data.attribute 

    def cmd = need in ["cool", "heat"] ? "set${need.capitalize()}ingSetpoint" : "setThermostatSetpoint" 

    if (cmd && need != "off") {

        // deviceId: The unique identifier of the thermostat device
        // Example: "1150" or "2f3a8b9c-1234-5678-90ab-cd1234567890"
        def deviceId = data.deviceId

        // allThermostats: Array of all unique thermostat devices from user preferences
        // Example: ["AC OFFICE", "AC BEDROOM", "AC LIVING ROOM"]
        def allThermostats = getAllThermostats()

        // device: The specific thermostat device object found by its ID
        // Example: {id: "1150", name: "AC OFFICE", currentTemp: 72}
        def device = getDeviceById(deviceId, allThermostats)

        // value: The target temperature to be set
        // Example: 72 (for normal operation) or 85/65 (for boost mode)
        def value = data.value

        def turboRequired = data.turboRequired
        state.boostMode = turboRequired


        if(device){
            
                if (device.currentValue(attribute) != value) {
                    logTrace "${device.displayName} ${attribute} set to ${value}F"
                    device."${cmd}"(value)
                }
                else {
                    logTrace "${device.displayName} ${attribute} ALREADY set to ${value}F"
                }
                setTurbo(turboRequired)
            
        }
        else {
            log.error "Device with ID ${data.deviceId} not found."
        }
    }
    else{
        logDebug "cmd:$cmd"
    }
    
}
def getDeviceById(id, devicesCollection){
    
    def device = devicesCollection.find { it.id == id }
    if (!device) {
        log.error "Device with ID ${id} not found in devicesCollection: $devicesCollection."
        return false
    }
    return device

}
def getAllThermostats(){
    // will be usefull once we implement multiple thermostats user selections and features
    allThermostats = thermostats.unique {it.id}
    logTrace "allThermostats: $allThermostats"
    return allThermostats
    
}
def setTurbo(required){
    def allThermostats = getAllThermostats() 
    for(t in allThermostats){
        def turboMode = required ? "on" : "off"
        if(t.hasAttribute("turboMode") && t.hasCommand("controlTurboMode")){
            logInfo "$t has turboMode and controlTurboMode"
            if(t.currentValue("turboMode") != turboMode){
                logTrace "turbo $turboMode"
                t.controlTurboMode(turboMode)
            }
            else {
                logTrace "turboMode already set to '${turboMode}' for $t"
            }
        }
    }
}

def get_need(){

    logTrace "state.thermostatsSetpointSTATES: ${state.thermostatsSetpointSTATES}"

    def allThermostats = getAllThermostats()
    def off_mode = fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off"

    def results = []

    allThermostats.each { thermostat -> 

        logDebug "-> processing $thermostat "

        def thermStates = state.thermostatsSetpointSTATES[thermostat.displayName]
        def targetTemp = thermStates.thermostatSetpoint
        def currentMode = location.mode
        def currentIndoorTemp = get_indoor_temperature() as BigDecimal
        def currentOutdoorTemp =  outsideTemp.currentValue("temperature") as BigDecimal
        def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds
        def currentTime = now()
        
        state.lastModeChangeTime = state.lastModeChangeTime == null ? currentTime : state.lastModeChangeTime
        state.lastMode = state.lastMode == null ? "initial" : state.lastMode

        def timeSinceLastChange = currentTime - state.lastModeChangeTime

        // Early returns for restricted conditions
        if(currentMode in restricted){
            logDebug "Currently in restricted mode: $currentMode - no action needed"
            return null
        }

        def swing = 0.5

        // Temperature differential thresholds
        def coolDiff = location.mode in powerSavingModes ? 5.0 : swing
        def heatDiff = location.mode in powerSavingModes ? -5.0 : -swing
        
        // Calculate temperature differences
        def indoorToSetpointDiff = currentIndoorTemp - targetTemp
        def indoorToOutdoorDiff = currentIndoorTemp - currentOutdoorTemp
        
        // Determine if temperature deviation is severe (+ 2 degrees beyond normal threshold)
        def highAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff >= (coolDiff + 2.0)
        def lowAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff <= (heatDiff - 2.0)

        
        
        logTrace """<div style='border:1 px solid gray;'>
            <br><b><u>Current conditions:</u></b>
            <br><b>Target: </b>${targetTemp}°
            <br><b>Indoor temp:</b> ${currentIndoorTemp}°
            <br><b>Outdoor temp:</b> ${currentOutdoorTemp}°
            <br><b>Indoor/Setpoint </b>difference: ${indoorToSetpointDiff}°
            <br><b>Indoor/Outdoor </b>difference: ${indoorToOutdoorDiff}°
            <br><b>High amplitude:</b> ${highAmplitude}
            <br><b>Low amplitude:</b> ${lowAmplitude}
            <br><b>Last mode:</b> ${state.lastMode}
            <br><b>Time since </b>last change: ${timeSinceLastChange/1000/60} minutes
            </div>
        """


        // Decision logic
        if (indoorToSetpointDiff > coolDiff) {
            if (currentOutdoorTemp >= currentIndoorTemp || highAmplitude) {
                logTrace "Indoor temperature too high, ${highAmplitude ? 'amplitude too high' : 'outdoor is warmer'} - mechanical cooling needed"
                results += changeMode("cool", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = false
            } else {
                logTrace "Indoor temperature high but outdoor is cooler - natural cooling possible"
                results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = false
            }
        } 
        else if (indoorToSetpointDiff < heatDiff) {
            
            if (currentOutdoorTemp <= currentIndoorTemp || lowAmplitude) {
                logTrace "Indoor temperature too low, ${lowAmplitude ? 'amplitude too high' : 'outdoor is colder'} - heating needed"
                results += changeMode("heat", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = boost ? true : false
            } else {
                logTrace "Indoor temperature low but outdoor is warmer - natural heating possible"
                results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = boost ? true : false
            }
        }
        else {
            logTrace "No extreme conditions detected - setting to ${off_mode}"
            results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
        }
    }

    log.debug "results: $results"
    if (results){
        def cools = results.findAll{it -> it == "cool"}
        def heats = results.findAll{it -> it == "heat"}

        log.debug "cools.size(): ${cools.size()}"
        log.debug "heats.size(): ${heats.size()}"

        if (cools.size() > heats.size()){
            log.debug "cools win"
            final_result = "cool"
        }
        else if (heats.size() > cools.size()){
            log.debug "heats win"
            final_result = "heat"
        }
        else {
            log.debug "nor cool nor heat win..."
            final_result =  off_mode
        }
        
    }
    else {        
        log.error "This line shouldn't appear. Need set to auto as fallback"
        final_result =  "auto"
    }

    logTrace "need returns: $final_result"
    return final_result
}


// Helper function to update mode with delay check
def changeMode(String newMode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime) {
    // Only heat and cool are active HVAC modes that need delays
    
    // Only apply delay when switching between heat and cool or high/low amplitude compared to target temp
    if (isActiveMode(newMode, highAmplitude, lowAmplitude) &&
     isActiveMode(state.lastMode, highAmplitude, lowAmplitude) && 
     newMode != state.lastMode && 
     timeSinceLastChange <= delayBetweenModes) {
        logTrace "Waiting for delay between heating/cooling modes (${(delayBetweenModes - timeSinceLastChange)/1000/60} minutes remaining)"
        return "auto" // if not ready to switch modes, set to auto
    }

    // Update timestamp only when changing between active modes
    if (isActiveMode(newMode, highAmplitude, lowAmplitude) || isActiveMode(state.lastMode, highAmplitude, lowAmplitude)) {
        state.lastModeChangeTime = currentTime
    }
  
    state.lastMode = newMode

    logWarn "changeMode returns $newMode"
    return newMode
}
def isActiveMode(mode, highAmplitude, lowAmplitude){
    def result = mode in ["heat", "cool"] && !highAmplitude && !lowAmplitude
    logTrace "isActiveMode ? $result"
    return result    
}

def get_indoor_temperature() {
    def temps = []
    
    // If temperature sensors are configured, use them
    if (tempSensors) {
        tempSensors.each { sensor ->
            if (sensor.currentTemperature != null) {
                temps << sensor.currentTemperature
            }
        }
    }
    
    // If no valid temperatures from sensors or no sensors configured,
    // use thermostat temperatures as fallback
    if (temps.size() == 0 && thermostats) {
        thermostats.each { thermostat ->
            if (thermostat.currentTemperature != null) {
                temps << thermostat.currentTemperature
            }
        }
    }
    
    // Calculate average temperature if we have any valid readings
    if (temps.size() > 0) {
        def avgTemp = (temps.sum() / temps.size())
        // Properly round BigDecimal to 1 decimal place
        return (avgTemp instanceof BigDecimal) ? 
            avgTemp.setScale(1, BigDecimal.ROUND_HALF_UP) : 
            new BigDecimal(avgTemp).setScale(1, BigDecimal.ROUND_HALF_UP)
    } else {
        logWarn "No valid temperature readings available from sensors. Using $thermostat"
        try {
            def temp = thermostat.currentValue("temperature")
            return (temp instanceof BigDecimal) ? 
                temp.setScale(1, BigDecimal.ROUND_HALF_UP) : 
                new BigDecimal(temp).setScale(1, BigDecimal.ROUND_HALF_UP)
        }
        catch (Exception e) {
            log.error "getCurrentTemp => Exception: $e"
            return null
        }
    }
}
def update_app_label(paused){
   
    while (app.label.contains(" paused ")) {
        app.updateLabel(app.label.minus(" paused "))
    }
    if(paused){
        app.updateLabel(previousLabel + ("<span style='color:red;font-weight:900'> paused </span>")) // recreate label
    }

}

def enableDebugLog() {
    state.EnableDebugTime = now()
    app.updateSetting('enableDebug', [type: 'bool', value: true])
    logDebug 'Debug logging enabled. Will automatically disable in 30 minutes.'
    runIn(1800, disableDebugLog)
}
def disableDebugLog() {
    state.EnableDebugTime = null
    app.updateSetting('enableDebug', [type: 'bool', value: false])
    logInfo 'Debug logging disabled.'
}
def enableTraceLog() {
    state.EnableTraceTime = now()
    app.updateSetting('enableTrace', [type: 'bool', value: true])
    log.trace 'Trace logging enabled. Will automatically disable in 30 minutes.'
    runIn(1800, disableTraceLog)
}
def disableTraceLog() {
    state.EnableTraceTime = null
    app.updateSetting('enableTrace', [type: 'bool', value: false])
    logInfo 'Trace logging disabled.'
}
def enableInfoLog() {
    state.EnableInfoTime = now()
    app.updateSetting('enableInfo', [type: 'bool', value: true])
    runIn(1800, disableInfoLog)
    logInfo 'Description logging enabled.'
}
def disableInfoLog() {
    state.EnableInfoTime = null
    app.updateSetting('enableInfo', [type: 'bool', value: false])
    logInfo 'Description logging disabled.'
}
def check_logs_timer() {
    long now = now()
    if (now - state.lastCheckTimer >= 60000) {  // Check every minute
        if (enableDebug && state.EnableDebugTime != null && now - state.EnableDebugTime > 1800000) {
            disableDebugLog()
        }
        if (enableDebug && state.EnableInfoTime != null && now - state.EnableInfoTime > 1800000) {
            disableInfoLog()
        }
        if (enableTrace && state.EnableTraceTime != null && now - state.EnableTraceTime > 24 * 60 * 60 * 1000) {
            disableTraceLog()
        }
        // We don't automatically disable description logging
        state.lastCheckTimer = now
    }
}
private void logDebug(String message) {
    if (enableDebug) {
        log.debug((message))
    }
}
private void logInfo(String message) {
    if (enableInfo) {
        log.info(message)
    }
}
private void logWarn(String message) {
    if (enableWarn){
        log.warn(message)
    }
}

private void logTrace(String message) {
    if (enableTrace) {
        log.trace(message)
    }
}
private void initializeLogging() {
    state.EnableDebugTime = now()
    state.EnableTraceTime = now()
    state.EnableWarnTime = now()
    state.EnableInfoTime = now()
    state.lastCheckTimer = now()
    if (enableDebug) {
        logDebug 'Debug logging enabled. Will automatically disable in 30 minutes.'
        runIn(1800, disableDebugLog)
    }
    if (enableTrace) {
        log.trace 'Trace logging enabled. Will automatically disable in 30 minutes.'
        runIn(24 * 60 * 60, disableTraceLog)
    }
    if (enableInfo) {
        runIn(1800, disableInfoLog)
        logInfo('Description logging enabled.')
    }
    if (enableWarn) {
        logInfo('Warn logging enabled. Will never be disabled by this app.')
    }
}