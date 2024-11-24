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
            input "boost", "bool", title: "Boost when heating or cooling", submitOnChange:true
            input "fan_only", "bool", title: "Set thermostats to 'fan_only' instead of 'off'", submitOnChange:true
            input "set_to_auto_instead_of_off", "bool", title: "Set thermostats to 'auto' instead of 'off'", submitOnChange:true
            if(fan_only) app.updateSetting('auto', [type: "bool", value: false])
            if(auto) app.updateSetting('fan_only', [type: "bool", value: false])
            
            input "dimmer", "capability.switchLevel", title: "Select a virtual dimmer to memoize my desired setpoint", required: true, submitOnChange:true
            
        }

        section("Temperature Sensors") {
            input "tempSensors", "capability.temperatureMeasurement", title: "Select one ore more temperature measurement devices ($thermostat will be used by default if left empty)", required: false, multiple:true, submitOnChange:true
            input "outsideTemp", "capability.temperatureMeasurement", title: "Select a sensor for outside temperature (required)", required: true, submitOnChange: true
        }

        section("Contact sensors"){
            input "contacts", "capability.contactSensor", title: "Select one ore more contacts sensor", required: false, multiple: true, submitOnChange:true
        }

        section("Modes"){
            input "restricted", "mode", title: "Restricted modes", multiple: true
            input "powerSavingMode", "mode", title: "Power Saving Mode", required: false, multiple: false, submitOnChange:true
        }

        section("App Controls"){
            if(!state.installed){
                input "install", "button", title: "Install", submitOnChange:true
            }
            else {
                input "update", "button", title: "Update", submitOnChange:true
                input "pause", "button", title: "${state.paused ? 'Resume' : 'Pause'}", submitOnChange:true
                input "run", "button", title: "Test Run", submitOnChange: true
                input "reset setpoints", "button", title: "Reset Setpoints", submitOnChange: true
            }
        }
    }
}
def installed(){
    log.debug "Installing with settings: $settings"
    initialize()
    
    state.installed = true 

}
def updated(){
    initialize()
}
def initialize(){
    log.debug "initializing with settings: $settings"

    unschedule()
    unsubscribe()
    
    update_app_label(state.paused)

    subscribe(location, "mode", ChangedModeHandler)
    subscribe(outsideTemp, "temperature", outsideTempHandler)
    subscribe(dimmer, "thermostatSetpoint", setPointHandler)
    subscribe(dimmer, "heatingSetpoint", setPointHandler)
    subscribe(dimmer, "coolingSetpoint", setPointHandler)
    subscribe(dimmer, "level", setPointHandler)

    if (tempSensors){
        subscribe(tempSensors, "temperature", temperatureHandler)
    } else {
        subscribe(thermostat, "temperature", temperatureHandler)
    }
    if (contacts){
        subscribe(contacts, "contact", contactHandler)
    }
   
}
def appButtonHandler(btn) {

    switch (btn) {
        case "pause": 
            def paused = !state.paused
            if (enablewarning) log.warn "paused = $paused"

            if (paused) {
                if (enableinfo) log.info "unsuscribing from events..."
                unsubscribe()
                if (enableinfo) log.info "unschedule()..."
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
    }
}
def setPointHandler(evt){
    log.info "$evt.device set to ${evt.value}F" 
    if(state.boostMode || evt.value in ["85", "65", "85.0", "65.0", 85, 65, 85.0, 65.0]){
        log.warn "operation in boost mode. Not updating state.userDefinedSetpoint"
        return
    }
    state.userDefinedSetpoint = evt.value as BigDecimal
    main(calledBy="setPointHandler")
}
def ChangedModeHandler(evt){
    log.info "Location is in ${evt.value} mode" 
    main(calledBy="ChangedModeHandler")
}
def temperatureHandler(evt){
    log.info "$evt.device temperature is ${evt.value}F" 
    main(calledBy="temperatureHandler")
}
def outsideTempHandler(evt){
    log.info "$evt.device temperature is ${evt.value}F" 
    main(calledBy="outsideTempHandler")
}
def contactHandler(evt){
    log.info "$evt.device is ${evt.value}" 
    main(calledBy="contactHandler")
}

def main(calledBy="unknown"){

    state.lastRun = state.lastRun == null ? now() : state.lastRun

    if (now() - state.lastRun > 30000){

        
        def contactOpen = contacts.any{it -> it.currentValue("contact") == "open"}

        if(contactOpen){
            if(thermostats.any{it -> it.currentValue("thermostatMode") != "off"}) {
                def openContacts = contacts.findAll{it -> it.currentValue('contact') == 'open'}
                log.warn "${openContacts.join(', ')} ${openContacts.size() > 1 ? 'are' : 'is'} open - turning off HVAC"
                thermostats.setThermostatMode("off")
            }
            return
        }

        def need = get_need()
        log.trace "need is $need"

        if(thermostats.any{it -> it.currentValue("thermostatMode") != need}){
            thermostats.setThermostatMode(need)
        }
        if (need in ['fan_only', 'auto']){
            if(thermostats.any{it -> it.currentValue("thermostatMode") != state.userDefinedSetpoint}){
                thermostats.setThermostatSetpoint(state.userDefinedSetpoint)
            }
            else {
                log.info "${thermostats.join(', ')} already set to need & ${state.userDefinedSetpoint}F"
            }
        }
        handleBoost(need)
    }
    else {
        log.warn "main ran less than 30 seconds ago. Skipping"
    }
}

def handleBoost(need){
    if(boost){
        def turboRequired = false 
        if(need in ["heat", "cool"]){
            
            if(need == "heat"){
                if(thermostats.any{it -> it.currentValue("thermostatSetpoint") != 85}) {
                    state.userDefinedSetpoint = dimmer.currentValue("level") as BigDecimal
                    thermostats.setThermostatSetpoint(85)
                }
            }
            else if(need == "cool"){                
                if(thermostats.any{it -> it.currentValue("thermostatSetpoint") != 65}) {
                    def userDefinedSetpoint = thermostats[0].currentValue("thermostatSetpoint") as BigDecimal // memoize setpoint 
                    state.userDefinedSetpoint = dimmer.currentValue("level") as BigDecimal
                    thermostats.setThermostatSetpoint(65)
                }
            }
            
            turboRequired = true
            state.boostMode = turboRequired
            
        }
        else {
            state.boostMode = false
        }
        
        setTurbo(turboRequired)
    }
}
def setTurbo(required){
    for(t in thermostats){
        def turboMode = required ? "on" : "off"
        if(t.hasAttribute("turboMode") && t.hasCommand("controlTurboMode")){
            log.info "$t has turboMode and controlTurboMode"
            if(t.currentValue("turboMode") != turboMode){
                log.trace "setting turbo to $turboMode"
                t.controlTurboMode(turboMode)
            }
            else {
                log.info "turboMode already set to '${turboMode}' for $t"
            }
        }
    }
}

def get_need(){
    try {
        def currentSetpoint = dimmer.currentValue("level") as BigDecimal
        def currentMode = location.mode
        def currentIndoorTemp = get_indoor_temperature() as BigDecimal
        def currentOutdoorTemp =  outsideTemp.currentValue("temperature") as BigDecimal

        // Early returns for restricted conditions
        if(currentMode in restricted){
            log.debug "Currently in restricted mode: $currentMode - no action needed"
            return null
        }

        def swing = 0.5

        // Temperature differential thresholds
        def coolDiff = location.mode in powerSavingMode ? 5.0 : swing
        def heatDiff = location.mode in powerSavingMode ? -5.0 : -swing
        
        // Calculate temperature differences
        def indoorToSetpointDiff = currentIndoorTemp - currentSetpoint
        def indoorToOutdoorDiff = currentIndoorTemp - currentOutdoorTemp
        
        // Determine if temperature deviation is severe (+ 2 degrees beyond normal threshold)
        def highAmplitude = location.mode in powerSavingMode ? false : indoorToSetpointDiff >= (coolDiff + 2.0)
        def lowAmplitude = location.mode in powerSavingMode ? false : indoorToSetpointDiff <= (heatDiff - 2.0)

        def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds
        def currentTime = now()
        
        // Initialize last mode change timestamp if not exists
        if (!state.lastModeChangeTime) {
            state.lastModeChangeTime = currentTime
            state.lastMode = "initial"
        }

        def timeSinceLastChange = currentTime - state.lastModeChangeTime
        
        log.debug """Current conditions:
            <br><b>Indoor temp:</b> ${currentIndoorTemp}°
            <br><b>Outdoor temp:</b> ${currentOutdoorTemp}°
            <br><b>Setpoint: </b>${currentSetpoint}°
            <br><b>Indoor/Setpoint </b>difference: ${indoorToSetpointDiff}°
            <br><b>Indoor/Outdoor </b>difference: ${indoorToOutdoorDiff}°
            <br><b>High amplitude:</b> ${highAmplitude}
            <br><b>Low amplitude:</b> ${lowAmplitude}
            <br><b>Last mode:</b> ${state.lastMode}
            <br><b>Time since </b>last change: ${timeSinceLastChange/1000/60} minutes
        """

        // Helper function to update mode with delay check
        def changeMode = { String newMode ->
            // Only heat and cool are active HVAC modes that need delays
            def isActiveMode = { mode -> mode in ["heat", "cool"] && !highAmplitude && !lowAmplitude}
            
            // Only apply delay when switching between heat and cool or high/low amplitude compared to target temp
            if (isActiveMode(newMode) && isActiveMode(state.lastMode) && 
                newMode != state.lastMode && timeSinceLastChange <= delayBetweenModes) {
                log.debug "Waiting for delay between heating/cooling modes (${(delayBetweenModes - timeSinceLastChange)/1000/60} minutes remaining)"
                return "auto" // if not ready to switch modes, set to auto
            }
            
            // Update timestamp only when changing between active modes
            if (isActiveMode(newMode) || isActiveMode(state.lastMode)) {
                state.lastModeChangeTime = currentTime
            }
            
            state.lastMode = newMode
            return newMode
        }

        // Decision logic
        if (indoorToSetpointDiff > coolDiff) {
            if (currentOutdoorTemp >= currentIndoorTemp || highAmplitude) {
                log.debug "Indoor temperature too high, ${highAmplitude ? 'amplitude too high' : 'outdoor is warmer'} - mechanical cooling needed"
                return changeMode("cool")
            } else {
                log.debug "Indoor temperature high but outdoor is cooler - natural cooling possible"
                return changeMode(fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off")
            }
        } 
        else if (indoorToSetpointDiff < heatDiff) {
            if (currentOutdoorTemp <= currentIndoorTemp || lowAmplitude) {
                log.debug "Indoor temperature too low, ${lowAmplitude ? 'amplitude too high' : 'outdoor is colder'} - heating needed"
                return changeMode("heat")
            } else {
                log.debug "Indoor temperature low but outdoor is warmer - natural heating possible"
                return changeMode(fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off")
            }
        }
        else {
            log.debug "No extreme conditions detected - setting to ${fan_only ? 'fan_only' : 'off'}"
            return changeMode(fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off")
        }
    }
    catch (Exception e){
        log.error "get_need => Exception $e"
        return "auto"
    }
    log.warn "This line shouldn't appear. Need set to auto as default"
    return "auto"
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
        log.warn "No valid temperature readings available from sensors. Using $thermostat"
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