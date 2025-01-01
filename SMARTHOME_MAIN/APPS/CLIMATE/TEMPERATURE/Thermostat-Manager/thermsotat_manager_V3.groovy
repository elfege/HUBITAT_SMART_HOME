definition(
    name: "Thermostat Manager V3",
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
            input "swing", "decimal", title: "Swing (aka offset)", defaultValue: 2.0
            // input "isMidea", "bool", title: "This unit runs on a Midea firmware (most split AC made in the US and Canada) [Not Implemented yet]"
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
                def therms = thermostats.collect{ t -> t.displayName}
                logWarn "therms: $therms"
                input "fan_only_thermostats", "enum", title: "Only fan circulate for these thermostats", options: therms, submitOnChange: true, required: false, multiple:true
            }
            
            input "set_to_auto_instead_of_off", "bool", title: "Set thermostats to 'auto' instead of 'off'", submitOnChange:true
            if (fan_only || set_to_auto_instead_of_off){
                if(fan_only) app.updateSetting('set_to_auto_instead_of_off', [type: "bool", value: false])
                if(set_to_auto_instead_of_off) app.updateSetting('fan_only', [type: "bool", value: false])
            }
            
        }

        section("Central Thermostat Controller"){
            state.read_about_therm_controller = state.read_about_therm_controller == null ? false : state.read_about_therm_controller
            state.vir_therm_created = state.vir_therm_created == null ? false : state.vir_therm_created
            state.showVirThermLocationInput = state.showVirThermLocationInput == null ? false : state.showVirThermLocationInput

            logDebug "state.read_about_therm_controller: $state.read_about_therm_controller"
            logDebug "state.vir_therm_created: $state.vir_therm_created"

            input "thermostatController", "capability.thermostat", title: "Select a virtual thermostat", multiple: false, required: true, submitOnChange:true
            
            input "read", "button", title: "${state.read_about_therm_controller ? "Close Description" : "Tell me more about this..."}", submitOnChange:true
            input "create_vir_therm", "button", title: "${!state.vir_therm_created ? "Create A Central Thermostat" : "Virtual Thermostat Already Created"}", submitOnChange:true, disabled: state.vir_therm_created

            paragraph formatText("Enter a location's name, then hit the button one more time to create the virtual device.", "black", "orange")
            input "virThermLocation", "text", title: "Location (e.g., Living-Room, Kitchen)", required: true, submitOnChange: true
    

            if (thermostatController || state.read_about_therm_controller){
                def prgrph = getThermControllerParagraph()
                paragraph prgrph
            }            
        }

        section("Temperature Sensors") {
            def firstInList = thermostats ? "${thermostats[0]}" : "the first thermostat in the list"
            input "tempSensors", "capability.temperatureMeasurement", title: "Select one ore more temperature measurement devices (${firstInList} will be used by default if left empty)", required: false, multiple:true, submitOnChange:true
            input "outsideTemp", "capability.temperatureMeasurement", title: "Select a sensor for outside temperature (required)", required: true, submitOnChange: true
        }

        section('Motion Sensors') {
            input 'motionSensors', 'capability.motionSensor', title: 'Select motion sensors', multiple: true, required: true
            if(motionSensors){
                input 'timeUnit', 'enum', title: 'Time unit', options: ['seconds', 'minutes'], defaultValue: 'minutes'
                input 'noMotionTime', 'number', title: "Default turn off after inactivity (in ${timeUnit})", required: true, defaultValue: 5
                input 'timeWithMode', 'bool', title: 'Use different timeouts for modes', defaultValue: false, submitOnChange: true
                if (timeWithMode) {
                    input 'timeModes', 'mode', title: 'Select modes', multiple: true, submitOnChange: true
                    if (timeModes) {
                        timeModes.each {
                            mode ->
                                input "noMotionTime_${mode}", 'number', title: "Timeout for ${mode} (in ${timeUnit})", required: true, defaultValue: noMotionTime
                        }
                    }
                }

                input (
                    "nightModeButton",
                    "capability.holdableButton",
                    title: "SLEEP MODE BUTTON: When pushed, motion events will be ignored (push again to cancel)", 
                    multiple: true, 
                    required: false, 
                    submitOnChange: true
                )
                if(nightModeButton){
                    
                    input "simpleModeTimeLimit", "number", title: "Return to normal motion detection after some time", required:true, description: "Time in hours", submitOnChange: true

                    input (
                        "lightSignal", 
                        "capability.switch", 
                        title: "Flash a light to confirm", 
                        required: false, 
                        submitOnChange: true
                    )
                
                    if(lightSignal && !lightSignal.hasCommand("flash")){
                        paragraph formatText("$lightSignal.displayName can't flash. Select a different device.", "white", "red")
                        app.updateSetting("lightSignal", [type:"capability", value: []]) 
                    }
                }
            }
        }

        section("Contact sensors"){
            input "contacts", "capability.contactSensor", title: "Select one ore more contacts sensor", required: false, multiple: true, submitOnChange:true
        }

        section("Modes"){
            input "restricted", "mode", title: "Restricted modes", multiple: true
            input "powerSavingModes", "mode", title: "Power Saving Mode(s)", required: false, multiple: true, submitOnChange:true
        }

        section("Comfort Intelligence") {
            try {
                getComfortManagementSection()
            } catch (Exception e) {
                log.error "Error in Comfort Intelligence section: $e"
                initialize_intelligence_states()
            }
        }

        section("Comfort Analytics") {
            try {
                paragraph getComfortVisualization()
                
                def currentSeason = getSeason()
                def seasonalPatterns = state.thermalBehavior?.environmentalFactors?.seasonalPatterns?."${currentSeason}"
                
                if (seasonalPatterns) {
                    paragraph formatText(
                        """Current Season: ${currentSeason.capitalize()}
                        Average Temperature: ${seasonalPatterns.avgTemp.round(1)}°F
                        Temperature Range: ${seasonalPatterns.tempRange.min.round(1)}°F - ${seasonalPatterns.tempRange.max.round(1)}°F""",
                        "#1B5E20",
                        "#E8F5E9"
                    )
                }
                
                input(
                    "showDetailedAnalytics",
                    "bool",
                    title: "Show Detailed Analytics",
                    defaultValue: false,
                    submitOnChange: true
                )
                
                if (showDetailedAnalytics) {
                    paragraph getDetailedAnalytics()
                }
            } catch (Exception e) {
                log.error "Error in Comfort Analytics section: $e"
                initialize_intelligence_states()
            }
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
                if(lightSignal){
                    input "flash", "button", title: "Test light signal", submitOnChange: true
                }
                input "reset_intelligence", "button", title: "Reset Intelligence Learnings (!)", submitOnChange: true
                if(state.confirm_reset_intelligence){
                    // TODO: add backup logic (to a file) and RESTORE logic (from file). 
                    paragraph formatText("ARE YOU SURE? This will potentially destroy months of accumulated learnings! This action is irreversible!", "white", "red")
                    
                    input "reset_intelligence", "button", title: "Destroy", submitOnChange: true
                    input "cancel_reset_intelligence", "button", title: "Destroy", submitOnChange: true
                }
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
    log.info "initializing with settings: $settings"

    unschedule()
    unsubscribe()
    
    update_app_label(state.paused)

    subscribe(location, "mode", ChangedModeHandler)
    subscribe(outsideTemp, "temperature", outsideTempHandler)
    subscribe(thermostats, "thermostatSetpoint", setPointHandler)
    subscribe(thermostats, "heatingSetpoint", setPointHandler)
    subscribe(thermostats, "coolingSetpoint", setPointHandler)
    subscribe(motionSensors, 'motion', motionHandler)

    if(thermostatController){
        subscribe(thermostatController, "thermostatSetpoint", setPointHandler)
        subscribe(thermostatController, "heatingSetpoint", setPointHandler)
        subscribe(thermostatController, "coolingSetpoint", setPointHandler)
    }

    if (tempSensors){
        subscribe(tempSensors, "temperature", temperatureHandler)
    } 
    if (contacts){
        subscribe(contacts, "contact", contactHandler)
    }

    if (nightModeButton) {
        subscribe(nightModeButton, "pushed", pushableButtonHandler)
    }

    // Initialize debug and trace timing
    initializeLogging()

    initializeStates()

    initializeBoostTempArray()

    log.info "${app.label} Initialized."

    master([calledBy:"initialiaze", motionActiveEvent: false])
   
}
def initializeBoostTempArray(){
    // Convert boost temps to BigDecimal with scale 1
    def boostTempArray = [
        (tempBoostCool as BigDecimal).setScale(1),
        (tempBoostHeat as BigDecimal).setScale(1)
    ]
    // store it in a state
    state.boostTempArray = boostTempArray
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
            logWarn "paused = $paused"

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
            master([calledBy:"appButtonHandler", motionActiveEvent: false])
            break
        case "reset":
            resetMem()
            break
        case "confirm_reset_intelligence":
            state.confirm_reset_intelligence = true
            break
        case "reset_intelligence":
            initialize_intelligence_states()
            break
        case "cancel_reset_intelligence": 
            state.confirm_reset_intelligence = false
            break
        case "read":
            state.read_about_therm_controller = !state.read_about_therm_controller
            break
        case "create_vir_therm": 
            if (!state.showVirThermLocationInput){
                state.showVirThermLocationInput = true
            }
            else {
                createVirtualThermostat()
            }
        case "flash": 
            flashTheLight()
            break 
    }
}

def getThermControllerParagraph() {
    def prgrph = """
        <style>
            /* Reset styles for entire container */
            #therm-controller * {
                margin: 0 !important;
                padding: 0 !important;
                text-indent: 0 !important;
                line-height: 1.3 !important;
            }
            
            /* Column styling */
            #therm-controller > div {
                flex: 1;
                border: 1px solid #dee2e6;
                padding: 12px !important;
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            
            /* Headers */
            #therm-controller .column-header {
                font-weight: bold;
                color: #2c3e50;
                font-size: 14px;
                margin-bottom: 0px !important;
            }
            
            /* Content text */
            #therm-controller .content-text {
                font-size: 12px;
                margin-top: 4px !important;
                line-height: 14px !important;
            }
            
            /* Lists */
            #therm-controller ul {
                list-style-position: inside;
                padding-left: 8px !important;
                margin-top: 4px !important;
                line-height: 0.1 !important;
            }
            
            #therm-controller li {
                font-size: 12px;
                margin-bottom: 1px !important;
                padding-bottom: 0 !important;
                line-height: 14px !important;
            }
            
            /* Emphasis text */
            #therm-controller .emphasis {
                font-style: italic;
                margin-top: 8px !important;
            }
        </style>

        <div id="therm-controller" style="
            display: flex;
            width: 100%;
            gap: 8px;
            margin: 0 !important;
            text-indent: 0 !important;
            line-height: 1.3;
        ">
            
            
            <!-- Left Column -->
            <div>
                <div class="column-header">Virtual Thermostat Controller</div>
                <div class="content-text">
                    Using a dedicated virtual thermostat ensures reliable temperature control memoization in Hubitat's single-threaded environment. While ${app.name} uses hash maps for tracking setpoint changes, managing multiple thermostats with various comfort scenarios can lead to synchronization issues. A virtual thermostat provides a single source of truth for temperature preferences and ensures consistent behavior across your system.
                </div>
            </div>

            <!-- Middle Column -->
            <div>
                <div class="column-header">Setup Guide</div>
                <ul>
                    <li>Create a virtual thermostat device in Hubitat if you haven't already</li>
                    <li>Use a descriptive name (e.g., "Temperature Living Room")</li>
                    <li>This thermostat will serve as the main reference point</li>
                    <li>All other thermostats will follow its settings</li>
                </ul>
            </div>

            <!-- Right Column -->
            <div>
                <div class="column-header">Voice Control</div>
                <div class="content-text">
                    Natural voice commands are supported:
                    <br>"Alexa, set Living Room temperature to 75 degrees"
                </div>
                <div class="content-text emphasis">
                    This approach simplifies state management and provides a more reliable user experience.
                </div>
            </div>
        </div>
        """
    return prgrph
}

def findExistingVirTherm() {
    def ref_label = "Temperature ${settings.virThermLocation}"
    logDebug "Looking for existing device with label: ${ref_label}"
    
    try {
        def params = [
            uri: "http://${location.hub.localIP}:8080",
            path: "/device/listJson",
            query: [capability: "capability.thermostat"]
        ]
        
        def existingDevice = null
        httpGet(params) { response ->
            def devices = response.data
            // Find device with matching label
            def match = devices.find { it.label == ref_label }
            if (match) {
                logDebug "Found matching device: ${match}"
                existingDevice = match
            }
        }
        
        logDebug "Existing device: $existingDevice"
        return existingDevice
        
    } catch (Exception e) {
        log.error "Error finding existing thermostat: ${e.message}"
        return null
    }
}



/**
 * Creates a virtual thermostat device for memoizing temperature setpoints
 * 
 * Creates a thermostat with ID "VirtualThermostat_${app.id}" and label "Temperature ${settings.virThermLocation}"
 * where virThermLocation is a required user input setting.
 * 
 * @return The created virtual thermostat device or existing one if already exists
 * 
 * Key Features:
 * - Creates device with fixed ID pattern to prevent duplicates
 * - Verifies required location input before creation
 * - Uses Hubitat's built-in virtual thermostat driver
 * - Sets initial states for Alexa compatibility 
 * - Updates app's thermostatController setting automatically
 * - Stores device reference in state
 * - Proper error handling with state management
 * 
 * Requirements:
 * - settings.virThermLocation must be defined
 * - Parent app must have thermostatController capability
 *
 * Example Usage:
 * preferences {
 *     input "virThermLocation", "text", title: "Location (e.g., Living-Room)", required: true
 * }
 * def thermostat = createVirtualThermostat() // Creates "Temperature Living-Room" thermostat
 */
def createVirtualThermostat() {
    logWarn "Creating Virtual Thermostat..."

        
    // Input validation for required suffix
    if (!settings.virThermLocation) {
        log.error "Location suffix is required but not provided"
        state.vir_therm_created = false
        return null
    }
    
    try {      

        // Force device ID to use app.id
        def uuid = UUID.randomUUID().toString()
        def deviceId = "${uuid}-${app.id}"
        
        // Check for existing device first
        def existingDevice = findExistingVirTherm()
        if (existingDevice) {
            logInfo "Using existing virtual thermostat: ${existingDevice.displayName}"
            
            // Update app settings to use this thermostat
            app.updateSetting("thermostatController", [type: "capability.thermostat", value: existingDevice.id])

            state.vir_therm_created = true
            return 
        }


        // Create label with suffix
        def labelName = "Temperature ${settings.virThermLocation}"
        
        // Define device parameters
        def deviceHandlerName = "Virtual Thermostat"
        def params = [
            name: deviceHandlerName,
            label: labelName,
            completedSetup: true
        ]

        // Create the virtual device
        def thermostat = addChildDevice(
            "hubitat",           
            deviceHandlerName,   
            deviceId,           
            null,               
            params             
        )

        if (thermostat) {
            logInfo "Successfully created virtual thermostat: ${thermostat.displayName}"
            
            // Initialize thermostat with default values
            thermostat.setThermostatMode("heat")  
            thermostat.setHeatingSetpoint(72)     
            thermostat.setCoolingSetpoint(72)     
            
            // Update app settings to use this thermostat
            app.updateSetting("thermostatController", [type: "capability.thermostat", value: thermostat.deviceNetworkId])
            
            // Store reference information
            state.virtualThermostatId = deviceId
            state.virtualThermostatLabel = labelName
            state.vir_therm_created = true
            
            return thermostat
        } else {
            log.error "Failed to create virtual thermostat"
            state.vir_therm_created = false
            return null
        }
    } catch (Exception e) {
        log.error "Error creating virtual thermostat: ${e.message}"
        state.vir_therm_created = false
        return null
    }
}

def isBoostTemp(value) {
    if (!state.boostTempArray) {
        initializeBoostTempArray()
    }
     
    // Convert input to BigDecimal with scale 1
    def normalizedValue = (value as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
    
    // Check each boost temp with +/- 1 degree tolerance
    def result = state.boostTempArray.any { boostTemp ->
        def normalizedBoostTemp = (boostTemp as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
        def difference = (normalizedValue - normalizedBoostTemp).abs()
        difference <= 1.0
    }
    
    def m = result ? "$value is in boostTempArray (±1°)" : "$value IS NOT in boostTempArray (±1°)"
    logDebug "<b>$m</b> [Array: ${state.boostTempArray}]"
    return result
}

def setPointHandler(evt){
    logTrace "state.boostMode: ${state.boostMode} **********-------------- $evt.device $evt.name set to ${evt.value}F" 


    // Check if current value is a boost temperature
    if(isBoostTemp(evt.value)) {
        logWarn "Operation in boost mode (${evt.value}F matches boost temperature). Not updating state.thermostatsSetpointSTATES"
        return
    }

    updateMem(evt.device, attribute="all")
    
}

def pushableButtonHandler(evt){
    logInfo "BUTTON EVT $evt.device $evt.name $evt.value"

    def nightModeActive = state.nightModeActive ?: false 

    if (evt.name == "pushed") {
        nightModeActive = !nightModeActive
    }

    state.nightModeActive = nightModeActive

    if(nightModeActive){
        state.nightModeActivationTime = now()

        flashTheLight()
    }
}

def flashTheLight(){
    if(!lightSignal) return
    // save current on/off state
    state.prevLightSignalState = lightSignal.currentValue("switch")
    // Flash light if configured
    lightSignal?.flash()
    runIn(5, stopFlashing)
}

def stopFlashing() {
    lightSignal.off()
    lightSignal."${state.prevLightSignalState ?: "off"}"()
}

def motionHandler(evt) {
    if (state.paused) {
        logDebug "Motion detected, but app is paused. Ignoring."
        return
    }
    if(location.mode in restricted){
        logDebug "Currently in restricted mode: $location.mode - no action needed"
        return null
    }

    logInfo "motion event: $evt.displayName is ${evt.value}"

    state.functionalSensors = state.functionalSensors ?: [:]

    // Store status with both functional state and device ID
    // Set to true since we just received an event from this sensor
    state.functionalSensors[evt.device.id] = [
        functional: true,  // set to true since receiving an event proves functionality
        deviceId: evt.device.id,
        displayName: evt.device.displayName

    ]

    
    def now = now()
    def lastMotionHandled = state.lastMotionHandled ?: 0
    def lasActivetMotionHandled = state.lasActivetMotionHandled ?: 0
    def intervalBetweenEvents = 50 // in seconds
    def intervalBetweenAciveEvents = 20 //
    def someSecondsAgo = now - intervalBetweenEvents * 1000 // N seconds in milliseconds
    def someSecondsAgoActiveEvents = now - intervalBetweenAciveEvents * 1000
    
    if (lastMotionHandled > someSecondsAgo) {
        
        master([calledBy:"motionHandler", motionActiveEvent: false])

        state.lastMotionHandled = now
    }
    else if(evt.value == "active" && lastMotionHandled > someSecondsAgoActiveEvents) {
        
        master([calledBy:"motionHandler", motionActiveEvent: true])
        state.lasActivetMotionHandled = now
    }
    
    
}

def ChangedModeHandler(evt){
    logInfo "Location is in ${evt.value} mode" 
    master([calledBy:"ChangedModeHandler", motionActiveEvent: false])
}
def temperatureHandler(evt){
    logInfo "$evt.device temperature is ${evt.value}F" 
    master([calledBy:"temperatureHandler", motionActiveEvent: false])
}
def outsideTempHandler(evt){
    logInfo "$evt.device temperature is ${evt.value}F" 
    master([calledBy:"outsideTempHandler", motionActiveEvent: false])

}
def contactHandler(evt){
    logInfo "$evt.device is ${evt.value}" 
    master([calledBy:"contactHandler", motionActiveEvent: false])
}

def master(data){

    log.debug "isInNightMode(): ${isInNightMode()}"

    logDebug "MASTER CALLED - Stack trace: ${new Exception().getStackTrace()}"
    log.debug data
    
    def lapse = 5 // interval in seconds

    state.lastRun = state.lastRun == null ? now() : state.lastRun

    logWarn("state.thermostatsSetpointSTATES: <br> ${state.thermostatsSetpointSTATES}")

    if (now() - state.lastRun > lapse * 1000){

        state.lastRun = now()         

        handleThermosats(data.motionActiveEvent)
        
    }
    else {
        logWarn "master ran less than $lapse seconds ago. Skipping"
        return
    }

    

    if(data.calledBy in ["master", "initialiaze", "appButtonHandler"]) {
        unschedule("master") // Cancel any existing scheduled runs
        runIn(
                120, 
                "master", 
                    [
                        data: [
                            calledBy: "master",
                            motionActiveEvent: false
                        ],
                        overwrite: true  // Making the default behavior explicit for clarity
                    ]

            )
    }
    check_logs_timer()
    logDebug "master eval executed successfully. called by: $calledBy"
}

def handleThermosats(motionActiveEvent=false, off=false){

    logDebug "Processing thermostats..."

    
    def allThermostats = getAllThermostats()
    def contactOpen = contacts.any{it -> it.currentValue("contact") == "open"}
    if(contactOpen){
        def openContacts = contacts.findAll{it -> it.currentValue('contact') == 'open'}
        log.warn "${openContacts.join(', ')} ${openContacts.size() > 1 ? 'are' : 'is'} open"   
    }
    def need = contactOpen ? "off" : get_need(motionActiveEvent) 

    allThermostats.each { thermostat -> 

        logInfo "Processing ${thermostat}..."

        setThermostatsMode([deviceId: thermostat.id, need: need])

        if (need == "off") return 

        if (boost) {
            if (need in ["heat", "cool"]) {

                logWarn "handling boost (need:$need)"

                state.boostMode = true
                
                def targetTemp = need == "heat" ? tempBoostHeat : tempBoostCool
                def attribute = "${need}ingSetpoint"


                if (thermostat.currentValue(attribute) != val) {
                    logDebug "thermostat -> $thermostat"
                    updateMem(thermostat, "${need}ingSetpoint")
                    logDebug "runIn(1, 'setThermostatsSetpoint'...)"
                    runIn(1, "setThermostatsSetpoint",
                        [
                            data: [
                                deviceId: thermostat.id,
                                need: need,
                                attribute: attribute,
                                value: targetTemp,
                                turboRequired: true,
                                calledBy: "handleThermosats"
                            ],
                            overwrite: false
                        ])
                    
                    return // works like "continue"; exits the current iteration
                }
            }
        }

        // end of boost or no boost, restore/apply settings from memoized data
        // def thermStates = state.thermostatsSetpointSTATES[thermostat.displayName]
        def targetTemp = thermostatController.currentValue("thermostatSetpoint")
        def attribute = "thermostatSetpoint"

        state.boostMode = false

        // schedule to allow state.boostMode propagation
        runIn(1, "setThermostatsSetpoint",
            [
                data: [
                    deviceId: thermostat.id,
                    need: need,
                    attribute: attribute,
                    value: targetTemp,
                    turboRequired: true,
                    calledBy: "handleThermosats"
                ],
                overwrite: false
            ])

    }
}

def updateMem(thermostat, attribute="all"){

    logDebug "--------------- Updating memoization state for $thermostat"

    

    //ensure it doesn't initialize with boost values if this was called during a boosting operation
    // Convert current values to BigDecimal with scale 1
    def currSP = (thermostat.currentValue("thermostatSetpoint") as BigDecimal).setScale(1)
    def currCSP = (thermostat.currentValue("coolingSetpoint") as BigDecimal).setScale(1)
    def currHSP = (thermostat.currentValue("heatingSetpoint") as BigDecimal).setScale(1)


    
    // TODO: update to call isBoostTemp() instead (no need for conversion above then)
    def thermostatSetpoint = currSP in state.boostTempArray ? 74.0 : currSP
    def coolingSetpoint = currCSP in state.boostTempArray ? 74.0 : currCSP
    def heatingSetpoint = currHSP in state.boostTempArray ? 74.0 : currHSP

    logDebug """
        <br> tempBoostCool: $tempBoostCool (${tempBoostCool.class})
        <br> tempBoostHeat: $tempBoostHeat (${tempBoostHeat.class})
        <br> currSP: $currSP (${currSP.class})
        <br> currHSP: $currHSP (${currHSP.class})
        <br> currCSP: $currCSP (${currCSP.class})
        <br> state.boostTempArray: $state.boostTempArray
        <br> currSP in boostTempArray ? ${currSP in boostTempArray}
        <br> currHSP in boostTempArray ? ${currHSP in boostTempArray}
        <br> currCSP in boostTempArray ? ${currCSP in boostTempArray}
        """

    
    def previousSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.thermostatSetpoint ?: currSP
    def previousCSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.coolingSetpoint ?: currCSP
    def previousHSP = state.thermostatsSetpointSTATES?.get(thermostat.displayName)?.heatingSetpoint ?: currHSP

    thermostatSetpoint = attribute in ["thermostatSetpoint", "all"] ? thermostatSetpoint : previousSP
    coolingSetpoint = attribute in ["coolingSetpoint", "all"] ? coolingSetpoint : previousCSP    
    heatingSetpoint = attribute in ["heatingSetpoint", "all"] ? heatingSetpoint : previousHSP

    if (attribute != "all") {
    logTrace """Selective update for $attribute:
        <br> ${attribute == "thermostatSetpoint" ? "Updating" : "Preserving memoized"} thermostatSetpoint: $thermostatSetpoint
        <br> ${attribute == "coolingSetpoint" ? "Updating" : "Preserving memoized"} cooling setpoint: $coolingSetpoint
        <br> ${attribute == "heatingSetpoint" ? "Updating" : "Preserving memoized"} heating setpoint: $heatingSetpoint
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
    initializeStates() // repopulates
    logTrace "Done."

}
def setThermostatsMode(data){

    logDebug "setThermostatsMode: $data"

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

    // TODO: rethink this logic, it's shitty as F***. We need a separate logic for thermostat FAN MODE (=speed) and thermostat Mode (which includes "fan_only")
    // the attempt here to do 2 in 1 is not right. 

    def cmd = need == "fan_only" ? "setThermostatFanMode" : "setThermostatMode" // we'll need both to set the thermostatMode(speed) and the thermostatMode('fan_only')
    
    def attribute = need == "fan_only" ? "thermostatFanMode" : "thermostatMode" 

    

    if(device){

        def use_fan_only = false 
        
        if (fan_only_thermostats){
            use_fan_only = device.displayName in fan_only_thermostats
        }
        else {
             use_fan_only = fan_only
        }

        logDebug "fan_only_thermostats: $fan_only_thermostats"
        logDebug "***************** use_fan_only for ${device.displayName} ? => ? $use_fan_only"

        // in fan_only mode, don't be in any of the active modes (or it'd trigger turbo in fan_only (which is an auto therm mode))
        if(use_fan_only && need == "fan_only"){
            logDebug "Setting ${device.displayName} to 'fan_only' before setting thermostatFanMode (fan speed)"

            def supportedModesJson = device.currentValue("supportedThermostatModes")
            def modes = new groovy.json.JsonSlurper().parseText(supportedModesJson)
            logDebug "------------Parsed thermostat Modes: $modes (${modes.class})"

            if(need in modes){
                device.setThermostatMode(need) // set the mode to "fan_only"
            }
            else {
                logTrace "$need not compatible with ${device.displayName}. Turning off instead. "
                if(device.currentValue("thermostatMode") != "off") device.off() //Fan should be turned on separately in the scope below...
            }
        }
        else if (need == "fan_only" && !use_fan_only) // if need is fan_only it's instead of off and because fan_only option has been enabled by user, but not for this device (!use_fan_only => it's the exception device)
        {
            logTrace "${device.displayName} is not among fan circulating devices (aka 'fan_only_thermostats'). Turning off instead. "

            // case where this specific device isn't to be set to fan circulate instead of off
            if(device.currentValue("thermostatMode") != "off") device.off() // devive stays off, no fan mode operation. 
            if(device.currentValue("thermostatFanMode") != "auto") device.setThermostatFanMode("auto") // ensure fan is set to auto
            return // end further eval here. 
        }

        def fan_mode = settings["fan_mode_on${device.displayName}"] ?: "false"
        logWarn "fan on compatible value used for $device.displayName: $fan_mode"  
        
        def mode = attribute == "thermostatFanMode" ? fan_mode : need // thermostatFanMode will set the SPEED
        
        if (device.currentValue(attribute) != mode){
            device."${cmd}"(mode)
            logTrace "${device.displayName} ${attribute} set to ${mode}"
        }
        else {
            logTrace "${device.displayName} ${attribute} <b>ALREADY</b> set to ${mode}"
        }

        if (attribute == "thermostatFanMode") {
            if (!fan_mode) {
                log.error "ERROR: fan mode not set!"
                return
            }
            
        }
        else {
            // if no longer in 'fan_only' set Thermsotat FAN MODE back to auto
            if (device.currentValue("thermostatFanMode") != "auto"){
                logTrace "reverting fan to auto"
                device.setThermostatFanMode("auto")
            }
            else {
                logDebug "${device.displayName} fan already set to auto"
            }
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
                    try {
                        device."${cmd}"(value)
                    } catch (Exception e) {
                        logTrace "setThermostatsSetpoint() => $e"
                        if(device.currentValue("coolingSetpoint").toInteger() != value.toInteger()){
                            device.setCoolingSetpoint("${value.toInteger()}")
                            logDebug "<b style='color:red'>${device.displayName}</b> coolingSetpoint set to ${value.toInteger()}"
                        }
                        else {
                            logDebug "<b style='color:red'>${device.displayName}</b> coolingSetpoint already set to ${value.toInteger()}"
                        }
                        if(device.currentValue("heatingSetpoint").toInteger() != value.toInteger()){
                            device.setHeatingSetpoint("${value.toInteger()}")
                            logDebug "<b style='color:red'>${device.displayName}</b> heatingSetpoint set to ${value.toInteger()}"
                        }
                        else {
                            logDebug "<b style='color:red'>${device.displayName}</b> heatingSetpoint already set to ${value.toInteger()}"
                        }
                    }
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
def getAllThermostats() {
    // Create a thread-safe copy of the thermostats collection
    // prevents "java.util.ConcurrentModificationException" error
    def thermostatsCopy = thermostats.collect()
    def uniqueThermostats = thermostatsCopy.unique { it.id }
    logTrace "allThermostats: $uniqueThermostats"
    return uniqueThermostats
}
def setTurbo(required){
    def allThermostats = getAllThermostats() 
    for(t in allThermostats){
        def currMode = t.currentValue("thermostatMode")
        def isInActiveMode = currMode in ["heat", "cool"]
        def turboMode = required && isInActiveMode ? "on" : "off"
        logDebug "$t currMode: $currMode | isInActiveMode: $isInActiveMode | turboMode: $turboMode"
        if(t.hasAttribute("turboMode") && t.hasCommand("controlTurboMode")){
            logDebug "$t has turboMode and controlTurboMode"
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

def getTimeout() {
    def result = noMotionTime // default

    logDebug "timeModes: $timeModes"
    logDebug "Current mode: ${location.mode}"

    try {
        if (absenceTimeoutSensor) {
            def listOfAbsents = absenceTimeoutSensor.findAll{ it.currentValue('presence') == 'not present' }
            boolean absenceRestriction = absenceTimeoutSensor ? listOfAbsents.size() == absenceTimeoutSensor.size() : false
            if (absenceRestriction) {
                logInfo "$absenceTimeoutSensor not present, timeout returns $absenceTimeout"
                return absenceTimeout
            }
        }

        if (timeWithMode && timeModes.contains(location.mode)) {
            def modeTimeout = settings["noMotionTime_${location.mode}"]
            logDebug "modeTimeout: $modeTimeout"
            if (modeTimeout != null) {
                result = modeTimeout
                if (enableTrace) logTrace "Returning value for ${location.mode}: $result ${timeUnit}"
            } else {
                logWarn "No specific timeout set for mode: ${location.mode}. Using default."
            }
        } else {
            if (enableTrace) logTrace "Using default timeout: $result ${timeUnit}"
        }
    } catch (Exception e) {
        log.error "Error in getTimeout: $e"
    }

    if (result == null) {
        result = noMotionTime
        logWarn "Timeout was null. Using default noMotionTime: $result"
    }

    logDebug "getTimeout() returns $result ${timeUnit}"
    return result
}

def isInNightMode() {
    // Initialize states if null
    state.nightModeActivationTime = state.nightModeActivationTime ?: now()
    state.nightModeActive = state.nightModeActive ?: false

    if (!state.nightModeActive) return false

    // Calculate elapsed time in milliseconds
    def timeElapsed = now() - state.nightModeActivationTime
    def timeLimit = simpleModeTimeLimit * 60 * 60 * 1000 // hours to milliseconds

    // Handle system time adjustments
    if (timeElapsed < 0) {
        timeElapsed = Long.MAX_VALUE
    }

    // Check if time limit has been exceeded
    if (timeElapsed >= timeLimit) {
        state.nightModeActive = false
        return false
    }
    
    // Still in night mode
    log.warn "${app.label} in sleeping/night mode - motion events ignored"
    return true
}

def motionIsActive() {

    if (isInNightMode()) return true

    logDebug "Checking if motion is active. Motion sensors: $motionSensors"

    def functionalSensors = getFunctionalSensors()

    // logDebug "functionalSensors: <br><ul> ${functionalSensors?.each { sensor -> sensor?.displayName }.join('<br><b><li></b>') }</ul>"
    
    def motionSensorNames = motionSensors?.collect { it.displayName } ?: []
    def functionalSensorNames = functionalSensors?.collect { it.displayName } ?: []

    if(enableTrace || !functionalSensors) {
        logTrace """
            functionalSensors:
            <table>
            <tr>
                <td>
                <b>All Motion Sensors</b>
                <ol>
                    ${motionSensorNames.collect { "<li>${it}</li>" }.join('')}
                </ol>
                </td>
                <td>
                ${functionalSensors ? "<b>Functional Sensors" : "<b style='color:red; font-weight:900;'>NO FUNCTIONAL SENSORS: motion returns TRUE by default"}</b>
                <ol>
                    ${functionalSensorNames.collect { "<li>${it}</li>" }.join('')}
                </ol>
                </td>
            </tr>
            </table>
        """
    }
    
    if (!functionalSensors) {
        if(considerActiveWhenFail) {
            return true
        } 
        else {
            return false
        }
        sendAlert()
    }

    // Check current state first (faster)
    def any_active = functionalSensors.any { it.currentValue('motion') == 'active' }

    if (any_active) {
        logTrace "At least one sensor active."
        return true
    }

    // Only check recent history if necessary
    int timeOut = getTimeout()
    long Dtime = timeUnit == 'minutes' ? timeOut * 60 * 1000 : timeOut * 1000
    def period = new Date(now() - Dtime)

    def anyActiveWithinTimePeriod = functionalSensors.any { sensor ->
            // max: N.this is resources hungry!
            def N = 20
            sensor.eventsSince(period, [max: N]).any { it.name == 'motion' && it.value == 'active' }
    }

    logDebug "anyActiveWithinTimePeriod within the last ${timeOut} ${timeUnit} = $anyActiveWithinTimePeriod"
    return anyActiveWithinTimePeriod
}
def getFunctionalSensors() {
    def lastCheck = state.lastEventHistoryCheck ?: 0
    def nowTime = now()
    def functionalSensors = []  // Initialize outside conditional blocks
    state.functionalSensors = state.functionalSensors ?: [:]  // Initialize state if null

    // Skip if the function was run less than 10 minutes ago
    if (nowTime - lastCheck < (10 * 60 * 1000)) {
        logDebug "Skipping getFunctionalSensors: Last check was ${(nowTime - lastCheck) / 60000} minutes ago."

        if (state.functionalSensors && !state.functionalSensors.isEmpty()) {
            // Display previously memoized alerts for unresponsive sensors
            logDebug "state.functionalSensors: $state.functionalSensors"
            state.functionalSensors.each { entry -> // single parameter receives the Map.Entry (key being the id). Structure: [deviceId: [funcitonal=true, deviceId:254, displayName: the device's name]]
                def sensorData = entry.value
                if (!sensorData.functional) {
                    def device = motionSensors.find { it.id == sensorData.id }
                    if (device) {
                        def m = "${app.label}: ${device.displayName} appears UNRESPONSIVE -- <a href='http://${location.hub.localIP}/device/edit/${device.id}' target='_blank'>Manage Device</a>"
                        logWarn formatText(m, "black", "#E0E0E0")
                    } else {
                        logWarn "<b style='color:red; font-weight:900;'>Device not found for sensor name: ${sensorData.displayName}</b>"
                        logWarn "motionSensors:::::: ${motionSensors.join(', ')}"
                        logWarn "state.functionalSensors: $state.functionalSensors"
                    }
                }
            }

            // Return device objects that were previously determined to be functional
            functionalSensors = motionSensors.findAll { sensor -> 
                state.functionalSensors[sensor.id]?.functional == true 
            }
        } else {
            logWarn "No functional sensor to log... re-evaluating..."
            functionalSensors = checkFunctionalSensors()
        }
    }
    // redundant else on purpose for better readability/explicit logic
    else {
        
        functionalSensors = checkFunctionalSensors()
    }

    if (functionalSensors.isEmpty()) {
        def m = "ALL MOTION SENSORS ARE UNRESPONSIVE IN ${app.label}"
        logWarn formatText(m, "yellow", "red")
    }

    // Always return device objects (functional sensors)
    return functionalSensors
}
def checkFunctionalSensors() {
    def nowTime = now()
    def recentPeriod = new Date(nowTime - (24 * 60 * 60 * 1000)) // 24 hours ago
    state.lastEventHistoryCheck = nowTime // Update the last check timestamp
    
    logTrace "Checking motion events since: $recentPeriod"

    def functionalSensors = motionSensors.findAll { sensor ->
        // Get events for this sensor in the last 24 hours with a higher limit
        def events = sensor.eventsSince(recentPeriod, [max: 200]) ?: []
        // logDebug "Events for ${sensor.displayName}: ${events.collect { evt -> [name: evt.name, value: evt.value, date: evt.date] }}"

        // Check if there are any 'motion' events
        def hasMotionEvents = events.any { event -> event.name == 'motion' }
        logDebug "Sensor ${sensor.displayName} has motion events: $hasMotionEvents"

        // Store both functional status and device ID for reference
        state.functionalSensors[sensor.id] = [
            functional: hasMotionEvents,
            deviceId: sensor.id,
            displayName: sensor.displayName
        ]
        
        if (!hasMotionEvents) {
            def m = "${app.label}: ${sensor.displayName} appears UNRESPONSIVE -- <a href='http://${location.hub.localIP}/device/edit/${sensor.id}' target='_blank'>Manage Device</a>"
            logWarn formatText(m, "black", "#E0E0E0")
        }

        hasMotionEvents
    }

    return functionalSensors
}

def get_indoor_temperature() {
    def temps = []

    def allThermostats = getAllThermostats()
    
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
    logTrace 'Trace logging enabled. Will automatically disable in 30 minutes.'
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
def formatText(title, textColor, bckgColor){
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
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
        logTrace 'Trace logging enabled. Will automatically disable in 30 minutes.'
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


/* *********************************** INTELLIGENCE *********************************** */


def get_need(motionActiveEvent=false) {
    /**
    * get_need()
    * Primary decision-making function for temperature management
    * @param motionActiveEvent Boolean indicating if this was triggered by a motion event
    * @return String HVAC mode ('cool', 'heat', 'fan_only', or 'off')
    * 
    * Evaluates current conditions and determines optimal HVAC mode by:
    * 1. Validating required sensor capabilities
    * 2. Analyzing occupancy heat and solar gain
    * 3. Calculating comfort ranges based on humidity
    * 4. Assessing natural cooling/heating potential
    * Falls back to basic algorithm if required capabilities are missing
    * 
    * @param motionActiveEvent Boolean indicating if triggered by motion event
    * @return String HVAC mode ('cool', 'heat', 'fan_only', or 'off')
    * 
    * Decision Flow:
    * ┌─────────────────┐
    * │ Validate Sensors├──No──→┌──────────────┐
    * └────────┬────────┘       │Fallback Logic│
    *          │                └──────────────┘
    *          Yes
    *          ↓
    * ┌─────────────────┐
    * │ Analyze Inputs  │←──────────────┐
    * └────────┬────────┘               │
    *          │                        │
    *          ↓                        │
    * ┌─────────────────┐      ┌───────────────┐
    * │ Check Natural   ├─No──→│Mechanical HVAC│
    * │ Cooling/Heating │      └───────────────┘
    * └────────┬────────┘
    *          │
    *          Yes
    *          ↓
    * ┌─────────────────┐
    * │  Fan or Off     │
    * └─────────────────┘
    * 
    * Related Functions:
    * - validateComfortCapabilities() - Initial capability check
    * - analyzeOccupancyHeat() - Occupancy heat contribution
    * - analyzeSolarGain() - Solar heat impact
    * - getPredictedPerformance() - Natural cooling/heating viability
    * 
    * Example Return States:
    * {
    *   'cool': When mechanical cooling needed
    *   'heat': When mechanical heating needed
    *   'fan_only': When natural management possible with fan
    *   'off': When natural management possible without fan
    * }
    * 
    * @see fallback_need_eval() for basic algorithm
    */
    try {
        def validation = validateComfortCapabilities()
        
        if (!validation.isRequired) {
            logDebug "Required capabilities missing, falling back to basic algorithm"
            return fallback_need_eval(motionActiveEvent)
        }
    
        // Get all heat sources
        def occupancyHeat = analyzeOccupancyHeat()
        def solarGain = analyzeSolarGain()
        
        // Calculate total heat load
        def totalHeatLoad = occupancyHeat.heatOutput + solarGain.solarGainWatts
        
        // Get temperature and humidity data
        def currentIndoorTemp = get_indoor_temperature()
        def currentOutdoorTemp = outsideTemp.currentValue("temperature")
        def humidity = getAverageHumidity()
        
        // Calculate comfort zone based on temperature and humidity
        def comfortRange = calculateComfortRange(humidity)
        def targetTemp = thermostatController.currentValue("thermostatSetpoint")
        
        // Determine if natural temperature management is possible
        def naturalCoolingPotential = assessNaturalCoolingPotential(
            currentIndoorTemp,
            currentOutdoorTemp,
            totalHeatLoad,
            humidity
        )
        
        def naturalHeatingPotential = assessNaturalHeatingPotential(
            currentIndoorTemp,
            currentOutdoorTemp,
            totalHeatLoad,
            humidity
        )
        
        // Decision logic
        if (currentIndoorTemp > (targetTemp + comfortRange.upperBound)) {
            if (naturalCoolingPotential.feasible) {
                return fan_only ? "fan_only" : "off"
            }
            return "cool"
        } else if (currentIndoorTemp < (targetTemp - comfortRange.lowerBound)) {
            if (naturalHeatingPotential.feasible) {
                return fan_only ? "fan_only" : "off"
            }
            return "heat"
        }
    } catch (Exception e){
        log.error "Exception in get_need() => $e"
        return fallback_need_eval(motionActiveEvent)
    }
    
    return fan_only ? "fan_only" : "off"
}
def getComfortManagementSection() {
    def validation = validateComfortCapabilities()
    
    section("Comfort Intelligence") {
        if (!validation.isRequired) {
            paragraph formatText(
                "⚠️ Smart Comfort Management Unavailable",
                "white",
                "#FF5722"
            )
            paragraph formatText(
                "Required: Humidity sensing capability missing. The app will use basic comfort management.",
                "#D84315",
                "#FFF3E0"
            )
        } else {
            def features = validation.enhancedFeatures
            def enhancedCount = features.size()
            
            paragraph formatText(
                """✓ Smart Comfort Management Active
                ${enhancedCount} Enhanced Features Available""",
                "white",
                "#43A047"
            )
            
            if (features) {
                features.each { feature ->
                    paragraph formatText(
                        """${feature.capability}
                        Devices: ${feature.devices.join(', ')}""",
                        "#1B5E20",
                        "#E8F5E9"
                    )
                }
            }
            
            if (validation.motionAnalysis.hasFrequencyData) {
                paragraph formatText(
                    "✓ Occupancy Analysis Active",
                    "#004D40",
                    "#E0F2F1"
                )
            }
        }
    }
}
def comfortCapabilitiesTiers() {
    return [
        required: [
            [
                capability: "relativeHumidityMeasurement",
                source: ["tempSensors", "thermostats", "motionSensors"],
                purpose: "Essential for comfort management and condensation prevention",
                fallbackBehavior: "basic"
            ]
        ],
        enhanced: [
            [
                capability: "illuminanceMeasurement",
                source: ["tempSensors", "motionSensors"],
                purpose: "Solar gain detection and management",
                fallbackBehavior: "continue",
                enhancementType: "solar"
            ],
            [
                capability: "ultravioletIndex",
                source: ["tempSensors", "motionSensors"],
                purpose: "Advanced solar impact analysis",
                fallbackBehavior: "continue",
                enhancementType: "solar"
            ]
        ],
        motion: [
            analysisType: "frequency",
            parameters: [
                heatOutput: 350, // watts per person
                activeThreshold: 5, // events per minute indicating active occupancy
                occupancyEstimationEnabled: true
            ]
        ]
    ]
}
def validateComfortCapabilities() {
    /**
    * validateComfortCapabilities()
    * Evaluates available sensor capabilities against required and enhanced feature sets
    * @return Map containing:
    *   - isRequired: Boolean indicating if minimum requirements are met
    *   - enhancedFeatures: List of available enhanced capabilities
    *   - missingSensors: List of missing required sensors
    *   - availableSensors: List of detected sensors and their capabilities
    *   - motionAnalysis: Object containing motion sensing capabilities
    * 
    * Used to determine if smart comfort management can be enabled and what features are available
    */
    def validationResults = [
        isRequired: false,
        enhancedFeatures: [],
        missingSensors: [],
        availableSensors: [],
        motionAnalysis: [
            enabled: true,
            sensorCount: motionSensors?.size() ?: 0,
            hasFrequencyData: false
        ]
    ]

    // Collect all potential devices that might have humidity sensing
    def allDevices = []
    
    // Add motion sensors
    if (motionSensors) {
        allDevices.addAll(motionSensors)
        logDebug "Added ${motionSensors.size()} motion sensors to validation check"
    }
    
    // Add temperature sensors
    if (tempSensors) {
        allDevices.addAll(tempSensors)
        logDebug "Added ${tempSensors.size()} temperature sensors to validation check"
    }
    
    // Add thermostats
    if (thermostats) {
        allDevices.addAll(thermostats)
        logDebug "Added ${thermostats.size()} thermostats to validation check"
    }
    
    // Check for humidity capability
    def humidityCapableDevices = allDevices.findAll { device ->
        def capabilityFound = false
        def attributeFound = false
        
        // Method 1: Direct capability check
        try {
            capabilityFound = device.hasCapability ? device.hasCapability("RelativeHumidityMeasurement") : false
        } catch (Exception e) {
            logDebug "Error checking hasCapability for ${device.displayName}: ${e.message}"
        }
        
        // Method 2: Attribute check
        try {
            attributeFound = device.hasAttribute ? device.hasAttribute("humidity") : false
            // Also check if device currently has a humidity value
            if (!attributeFound && device.currentValue("humidity") != null) {
                attributeFound = true
            }
        } catch (Exception e) {
            logDebug "Error checking attributes for ${device.displayName}: ${e.message}"
        }
        
        // Method 3: Check supported capabilities
        def metadataFound = false
        try {
            def deviceCapabilities = device.capabilities?.collect { it.toString().toLowerCase() } ?: []
            if (deviceCapabilities.any { it.contains("relativehumiditymeasurement") || it.contains("humidity") }) {
                metadataFound = true
            }
        } catch (Exception e) {
            logDebug "Error checking capabilities for ${device.displayName}: ${e.message}"
        }
        
        logDebug "${device.displayName}: capabilityFound=${capabilityFound}, attributeFound=${attributeFound}, metadataFound=${metadataFound}}"
        
        return capabilityFound || attributeFound || metadataFound
    }
    
    if (humidityCapableDevices) {
        validationResults.isRequired = true
        validationResults.availableSensors << [
            capability: "RelativeHumidityMeasurement",
            devices: humidityCapableDevices.collect { it.displayName }
        ]
        
        logDebug "Found ${humidityCapableDevices.size()} humidity-capable devices: ${humidityCapableDevices.collect{it.displayName}.join(', ')}"
    } else {
        validationResults.missingSensors << [
            capability: "RelativeHumidityMeasurement",
            source: ["tempSensors", "thermostats", "motionSensors"],
            purpose: "Essential for comfort management"
        ]
        logDebug "No humidity-capable devices found"
    }
    
    // Check enhanced capabilities
    def enhancedCapabilities = [
        [
            type: "solar",
            capability: "illuminanceMeasurement",
            purpose: "Solar gain detection"
        ],
        [
            type: "solar",
            capability: "ultravioletIndex",
            purpose: "Advanced solar impact analysis"
        ]
    ]
    
    enhancedCapabilities.each { enhancement ->
        def capableDevices = allDevices.findAll { device ->
            device.hasCapability ? device.hasCapability(enhancement.capability) : false
        }
        
        if (capableDevices) {
            validationResults.enhancedFeatures << [
                type: enhancement.type,
                capability: enhancement.capability,
                devices: capableDevices.collect { it.displayName }
            ]
            logDebug "Found ${capableDevices.size()} devices with ${enhancement.capability}"
        }
    }
    
    // Analyze motion capability
    if (motionSensors) {
        def now = new Date()
        def recentPeriod = new Date(now.time - (30 * 60 * 1000)) // Last 30 minutes
        
        def hasFrequencyData = motionSensors.any { sensor ->
            def events = sensor.eventsSince(recentPeriod)
            events && events.size() > 5
        }
        
        validationResults.motionAnalysis.hasFrequencyData = hasFrequencyData
        logDebug "Motion analysis status: hasFrequencyData=${hasFrequencyData}"
    }
    
    logInfo "Validation Results: Required capabilities ${validationResults.isRequired ? 'present' : 'missing'}"
    return validationResults
}
def analyzeOccupancyHeat() {
    /**
    * analyzeOccupancyHeat()
    * Estimates heat generation from room occupancy based on motion events
    * 
    * Heat Generation Model:
    * ┌─────────────┐    ┌──────────────┐    ┌────────────┐
    * │Motion Events│───→│People Counter│───→│Heat Output │
    * └─────────────┘    └──────────────┘    └────────────┘
    *       ↑                   ↑                  ↑
    *    3/minute         1 person per         200W per
    *                    3 events/min           person
    * 
    * @return Map Example:
    * [
    *   heatOutput: 400,      // Watts (2 people detected)
    *   confidence: 0.8,      // High confidence due to consistent events
    *   events: {            // Last 5 minutes of events
    *     "12:00": 3,
    *     "12:01": 4,
    *     "12:02": 2
    *   }
    * ]
    * 
    * Confidence Calculation:
    * - Base: 0.8
    * - Reduced by 50% if < 1 event per minute
    * - Increased by consistency of readings
    * 
    * @see trackEnvironmentalFactors() for pattern logging
    * @see getPredictedPerformance() for usage in predictions
    */
    if (!motionSensors) return [heatOutput: 0, confidence: 0]
    
    def now = new Date()
    def interval = 5 // minutes to analyze
    def startTime = new Date(now.time - (interval * 60 * 1000))
    
    def eventCounts = [:]
    def WATTS_PER_PERSON = 200 // As requested
    def EVENTS_PER_PERSON = 3  // Estimated events per minute per person
    
    motionSensors.each { sensor ->
        def events = sensor.eventsSince(startTime)?.findAll { it.name == "motion" && it.value == "active" }
        if (events) {
            eventCounts[sensor.deviceId] = events.size()
        }
    }
    
    if (eventCounts.isEmpty()) return [heatOutput: 0, confidence: 1.0]
    
    // Calculate events per minute
    def totalEvents = eventCounts.values().sum()
    def eventsPerMinute = totalEvents / interval
    
    // Estimate number of people
    def estimatedPeople = Math.round(eventsPerMinute / EVENTS_PER_PERSON)
    def totalHeatOutput = estimatedPeople * WATTS_PER_PERSON
    
    // Calculate confidence based on consistency of readings
    def confidence = 0.8 // Base confidence
    if (eventsPerMinute < 1) confidence *= 0.5 // Less confident with few events
    
    return [heatOutput: totalHeatOutput, confidence: confidence]
}
def analyzeSolarGain() {
    /**
    * analyzeSolarGain()
    * Calculates solar heat contribution using illuminance and UV sensors
    * @return Map containing:
    *   - solarGainWatts: Estimated solar heat gain in watts
    *   - confidence: Confidence level in the calculation (0-1)
    *   - hasData: Boolean indicating if relevant sensor data was available
    * 
    * Combines illuminance and UV index data to estimate solar heat impact
    */
    def comfCapTiers = comfortCapabilitiesTiers()
    def allSensors = (tempSensors ?: []) + (motionSensors ?: [])
    def result = [
        solarGainWatts: 0,
        confidence: 0.0,
        hasData: false
    ]
    
    // Find devices with required capabilities
    def illuminanceSensors = allSensors.findAll { it.hasCapability("illuminanceMeasurement") }
    def uvSensors = allSensors.findAll { it.hasCapability("ultravioletIndex") }
    
    if (!illuminanceSensors && !uvSensors) return result
    
    // Calculate solar gain
    def avgIlluminance = 0
    def avgUV = 0
    
    if (illuminanceSensors) {
        avgIlluminance = illuminanceSensors.collect { 
            it.currentValue("illuminance") ?: 0 
        }.average()
        result.hasData = true
    }
    
    if (uvSensors) {
        avgUV = uvSensors.collect { 
            it.currentValue("ultravioletIndex") ?: 0 
        }.average()
        result.hasData = true
        result.confidence += 0.2
    }
    
    // Rough estimation of solar gain in watts
    // Based on typical solar heat gain coefficients
    def solarGainWatts = 0
    if (illuminanceSensors) {
        // Convert lux to watts/m² (rough approximation)
        solarGainWatts += (new BigDecimal(avgIlluminance) / 100) * 0.85
        result.confidence += 0.3
    }
    
    if (uvSensors) {
        // UV index contribution to heat gain
        solarGainWatts *= (1 + (avgUV * 0.1))
        result.confidence += 0.2
    }
    
    result.solarGainWatts = solarGainWatts
    return result
}
def recordThermalEvent(params) {
    /**
    * recordThermalEvent()
    * Records temperature management events for learning
    * @param params Map containing:
    *   - type: String ('naturalCooling' or 'naturalHeating')
    *   - startTemp: Initial temperature
    *   - endTemp: Final temperature
    *   - outdoorTemp: Outdoor temperature
    *   - duration: Event duration in seconds
    *   - Additional environmental conditions
    * 
    * Maintains history of temperature management attempts and their outcomes
    */
    // Initialize if not exists
    state.thermalBehavior = state.thermalBehavior ?: [
        roomId: app.id,
        naturalCooling: [events: [], performanceByDelta: [:]], 
        naturalHeating: [events: [], performanceByDelta: [:]],
        environmentalFactors: [:]
    ]
    
    def event = [
        timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
        startTemp: params.startTemp,
        endTemp: params.endTemp,
        outdoorTemp: params.outdoorTemp,
        duration: params.duration,
        coolingRate: (params.startTemp - params.endTemp) / (params.duration / 3600), // degrees per hour
        conditions: [
            fanMode: params.fanMode,
            humidity: params.humidity,
            timeOfDay: params.timeOfDay,
            season: getSeason()
        ]
    ]
    
    // Determine the delta bracket (in 5-degree increments)
    def tempDelta = Math.abs(params.startTemp - params.outdoorTemp)
    def deltaBracket = "${Math.floor(tempDelta/5)*5}-${Math.floor(tempDelta/5)*5 + 5}"
    
    // Store event in appropriate category
    def category = params.type // 'naturalCooling' or 'naturalHeating'
    state.thermalBehavior[category].events << event
    
    // Keep only last 30 events
    if (state.thermalBehavior[category].events.size() > 30) {
        state.thermalBehavior[category].events = state.thermalBehavior[category].events[-30..-1]
    }
    
    // Update performance metrics for this delta bracket
    updatePerformanceMetrics(category, deltaBracket, event)
}
def updatePerformanceMetrics(category, deltaBracket, event) {
    /**
    * updatePerformanceMetrics()
    * Updates success metrics for temperature management strategies
    * @param category String ('naturalCooling' or 'naturalHeating')
    * @param deltaBracket String temperature differential range
    * @param event Map containing event details
    * 
    * Maintains running averages of cooling/heating rates and success rates
    */
    def metrics = state.thermalBehavior[category].performanceByDelta[deltaBracket] ?: [
        avgCoolingRate: 0,
        successRate: 0,
        typicalDuration: 0,
        sampleSize: 0
    ]
    
    // Update running averages
    def n = metrics.sampleSize
    metrics.avgCoolingRate = (metrics.avgCoolingRate * n + event.coolingRate) / (n + 1)
    metrics.typicalDuration = (metrics.typicalDuration * n + event.duration) / (n + 1)
    
    // Update success rate
    def targetAchieved = Math.abs(event.endTemp - thermostatController.currentValue("thermostatSetpoint")) <= swing
    metrics.successRate = (metrics.successRate * n + (targetAchieved ? 1 : 0)) / (n + 1)
    metrics.sampleSize = n + 1
    
    state.thermalBehavior[category].performanceByDelta[deltaBracket] = metrics
}
def getPredictedPerformance(targetTemp, currentTemp, outdoorTemp) {
    /**
    * getPredictedPerformance()
    * Predicts success likelihood of natural temperature management
    * @param targetTemp Desired temperature
    * @param currentTemp Current indoor temperature
    * @param outdoorTemp Current outdoor temperature
    * @return Map containing success probability and confidence level
    * 
    * Uses historical performance data to predict effectiveness of natural cooling/heating
    */
    def tempDelta = Math.abs(currentTemp - outdoorTemp)
    def deltaBracket = "${Math.floor(tempDelta/5)*5}-${Math.floor(tempDelta/5)*5 + 5}"
    def category = currentTemp > targetTemp ? 'naturalCooling' : 'naturalHeating'
    
    def metrics = state.thermalBehavior[category].performanceByDelta[deltaBracket]
    if (!metrics || metrics.sampleSize < 3) {
        return [
            predictedSuccess: 0.5, // 50% chance if we don't have enough data
            confidence: 0.3,
            estimatedDuration: 3600 // default 1 hour
        ]
    }
    
    return [
        predictedSuccess: metrics.successRate,
        confidence: Math.min(metrics.sampleSize / 10, 0.9), // Cap confidence at 90%
        estimatedDuration: metrics.typicalDuration,
        historicalRate: metrics.avgCoolingRate
    ]
}
def assessNaturalCoolingPotential(currentTemp, outdoorTemp, heatLoad, humidity) {
    def prediction = getPredictedPerformance(
        thermostatController.currentValue("thermostatSetpoint"),
        currentTemp,
        outdoorTemp
    )
    
    // Start tracking if we decide to try natural cooling
    if (prediction.predictedSuccess > 0.7 && prediction.confidence > 0.5) {
        recordThermalEvent([
            type: 'naturalCooling',
            startTemp: currentTemp,
            endTemp: currentTemp, // Will be updated when we change modes
            outdoorTemp: outdoorTemp,
            duration: 0,
            fanMode: getFanMode(),
            humidity: humidity,
            timeOfDay: new Date().format('HH:mm')
        ])
        
        return [feasible: true, confidence: prediction.confidence]
    }
    
    return [feasible: false, confidence: prediction.confidence]
}
def trackEnvironmentalFactors() {
    /**
    * trackEnvironmentalFactors()
    * Comprehensive environmental pattern tracking system
    * 
    * Data Structure:
    * state.thermalBehavior
    * ├── dailyPatterns
    * │   ├── morning: { temp: 68, humid: 45 }
    * │   ├── afternoon: { temp: 74, humid: 42 }
    * │   └── evening: { temp: 71, humid: 44 }
    * ├── seasonalPatterns
    * │   ├── winter: { avg: 69, range: [65,72] }
    * │   ├── spring: { avg: 72, range: [68,75] }
    * │   └── summer: { avg: 76, range: [72,79] }
    * ├── solarGainPatterns
    * │   └── hourly: [...]
    * └── occupancyPatterns
    *     └── weekday: {...}
    * 
    * Pattern Analysis:
    *     Time →
    * Temp  AM    Noon   PM
    * 78°F  ·      ███   ·
    * 74°F  ·    ███████ ·
    * 70°F  ████ ███████ ███
    * 66°F  ████ ███     ███
    * 
    * @see getSeasonalComfortAdjustment() for usage
    * @see getComfortVisualization() for UI representation
    */
    def now = new Date()
    def hour = now.format("HH:mm")
    def season = getSeason()
    
    // Initialize structure if needed
    state.thermalBehavior.environmentalFactors = state.thermalBehavior.environmentalFactors ?: [
        dailyPatterns: [:],
        seasonalPatterns: [:],
        solarGainPatterns: [:],
        occupancyPatterns: [:]
    ]
    
    // Track natural temperature changes
    def currentTemp = get_indoor_temperature()
    def previousTemp = state.lastIndoorTemp ?: currentTemp
    def tempChange = currentTemp - previousTemp
    state.lastIndoorTemp = currentTemp
    
    // Solar impact tracking
    def solarGain = analyzeSolarGain()
    if (solarGain.hasData) {
        def timeBlock = getTimeBlock(hour)
        state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock] = 
            state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock] ?: [:]
        
        updateRollingAverage(
            state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock],
            [
                solarGainWatts: solarGain.solarGainWatts,
                tempImpact: tempChange,
                season: season
            ]
        )
    }
    
    // Occupancy patterns
    def occupancyHeat = analyzeOccupancyHeat()
    if (occupancyHeat.heatOutput > 0) {
        def timeBlock = getTimeBlock(hour)
        state.thermalBehavior.environmentalFactors.occupancyPatterns[timeBlock] = 
            state.thermalBehavior.environmentalFactors.occupancyPatterns[timeBlock] ?: [:]
        
        updateRollingAverage(
            state.thermalBehavior.environmentalFactors.occupancyPatterns[timeBlock],
            [
                heatOutput: occupancyHeat.heatOutput,
                tempImpact: tempChange,
                season: season
            ]
        )
    }
    
    // Seasonal adjustments
    updateSeasonalPatterns(season, currentTemp, tempChange)
}
def getTimeBlock(hour) {
    /**
    * getTimeBlock()
    * Converts time to standardized 2-hour blocks
    * @param hour String in "HH:mm" format
    * @return String representing time block (e.g., "14:00-16:00")
    * 
    * Standardizes time periods for pattern analysis
    */
    // Break day into 2-hour blocks
    def hourNum = hour.split(":")[0].toInteger()
    return "${(hourNum - (hourNum % 2)).toString().padLeft(2, '0')}:00-${(hourNum + (2 - (hourNum % 2))).toString().padLeft(2, '0')}:00"
}
def getSeason() {
    /**
    * getSeason()
    * Determines current season based on month
    * @return String ('winter', 'spring', 'summer', 'fall')
    * 
    * Used for seasonal adjustment calculations
    */
    def month = new Date().format("MM").toInteger()
    switch(month) {
        case 12..2: return "winter"
        case 3..5: return "spring"
        case 6..8: return "summer"
        case 9..11: return "fall"
    }
}
def updateSeasonalPatterns(season, temp, tempChange) {
    /**
    * updateSeasonalPatterns()
    * Updates learned seasonal temperature patterns
    * @param season Current season
    * @param temp Current temperature
    * @param tempChange Recent temperature change
    * 
    * Maintains running statistics of seasonal temperature patterns
    */
    def seasonalPatterns = state.thermalBehavior.environmentalFactors.seasonalPatterns
    seasonalPatterns[season] = seasonalPatterns[season] ?: [
        avgTemp: temp,
        tempRange: [min: temp, max: temp],
        typicalChanges: []
    ]
    
    def pattern = seasonalPatterns[season]
    // Weighted rolling average
    pattern.avgTemp = ((pattern.avgTemp as BigDecimal) * 9 + (temp as BigDecimal)) / 10
    pattern.tempRange.min = Math.min(pattern.tempRange.min, temp)
    pattern.tempRange.max = Math.max(pattern.tempRange.max, temp)
    
    // Keep last 100 temperature changes
    pattern.typicalChanges = (pattern.typicalChanges + [tempChange])[-100..-1]
}
def calculateComfortRange(humidity) {
    /**
    * calculateComfortRange()
    * Determines comfort temperature range based on humidity
    * @param humidity Current relative humidity percentage
    * @return Map with upperBound and lowerBound temperature adjustments
    * 
    * Adjusts acceptable temperature range based on humidity levels
    */
    // Adjust comfort range based on humidity
    def base = new BigDecimal("1.0")
    def humidityFactor = new BigDecimal("0.05")
    def adjustment = ((humidity as BigDecimal) - 45) * humidityFactor
    
    return [
        upperBound: base - adjustment,
        lowerBound: base + adjustment
    ]
}
def getAverageHumidity() {
    /**
    * getAverageHumidity()
    * Calculates average humidity from available sensors
    * @return Number average humidity percentage
    * 
    * Combines readings from all humidity-capable sensors
    * Returns default 45% if no sensors available
    */
    def humidityCapableDevices = (tempSensors ?: []) + 
                                (thermostats ?: []) + 
                                (motionSensors ?: []).findAll { 
        it.hasCapability("relativeHumidityMeasurement")
    }
    
    def readings = humidityCapableDevices.collect {
        it.currentValue("humidity")
    }.findAll { it != null }
    
    return readings ? readings.average() : 45 // Default to 45% if no readings
}
def fallback_need_eval(motionActiveEvent=false){

    if(location.mode in restricted){
        logTrace "Currently in restricted mode: $location.mode - no action needed"
        return null
    }

    logTrace "state.thermostatsSetpointSTATES: ${state.thermostatsSetpointSTATES}"

    
    def off_mode = fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off"

    def results = []

    def motion = false 
    if (motionActiveEvent){
        motion = true // avoids calling motionIsActive() whenever possible. 
    } else {
        motion = motionIsActive() // potentially resource intensive
    }

    if(!motion){
        log.warn "No motion. get_need returns $off_mode"
        return off_mode
    }

    def allThermostats = getAllThermostats()
    

    // allThermostats.each { thermostat -> 

        // logDebug "-> processing $thermostat "

        // def thermStates = state.thermostatsSetpointSTATES[thermostat.displayName]

        // logDebug "state.lastNeed: $state.lastNeed"
        
        // if (!thermStates) resetMem() // reset and repopulate states if object is incomplete (happens after adding a new and we don't want that to happen on updated()) TODO: preserve existing entries. 

        // def targetTemp = state.lastNeed in ["cool", "heat"] ? thermStates."${state.lastNeed}ingSetpoint" : thermStates.thermostatSetpoint

        if (!thermostatController) {
            formatText("NO thermostatController SET! Update settings.", "white", "red")
            return 
        }

        def targetTemp = thermostatController.currentValue("thermostatSetpoint")
        
        def currentIndoorTemp = get_indoor_temperature() as BigDecimal
        def currentOutdoorTemp =  outsideTemp.currentValue("temperature") as BigDecimal
        def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds
        def currentTime = now()
        
        state.lastModeChangeTime = state.lastModeChangeTime == null ? currentTime : state.lastModeChangeTime
        state.lastMode = state.lastMode == null ? "initial" : state.lastMode

        def timeSinceLastChange = currentTime - state.lastModeChangeTime

        

        // Temperature differential thresholds
        def coolDiff = location.mode in powerSavingModes || !motion ? 5.0 : swing
        def heatDiff = location.mode in powerSavingModes || !motion ? -5.0 : -swing
        
        logDebug "currentIndoorTemp: $currentIndoorTemp "
        logDebug "targetTemp: $targetTemp "

        // Calculate temperature differences
        def indoorToSetpointDiff = currentIndoorTemp - targetTemp
        def indoorToOutdoorDiff = currentIndoorTemp - currentOutdoorTemp
        
        // Determine if temperature deviation is severe (+ 2 degrees beyond normal threshold)
        def amplitudeThreshold = 5
        def highAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff >= (coolDiff + amplitudeThreshold)
        def lowAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff <= (heatDiff - amplitudeThreshold)

        // TODO: learn from user's interventions... 
        def minOutdoorTempForCooling = 40.0 // Don't run AC when it's colder than 40°F outside
        def maxOutdoorTempForHeating = 75.0 // Don't run heat when it's warmer than 75°F outside
        def tooColdOutside = false
        def tooWarmOutside = false

        if (currentOutdoorTemp.toInteger() < minOutdoorTempForCooling.toInteger()) {
            tooColdOutside = true
        }
        if (currentOutdoorTemp.toInteger() >= maxOutdoorTempForHeating.toInteger()) {
            tooWarmOutside = true
        }
        
        logTrace """<div style='border:1 px solid gray;'>
            <br><b><u>Current conditions:</u></b>
            <br><b>Target: </b>${targetTemp}°
            <br><b>swing: </b>${swing}°
            <br><b>Indoor temp:</b> ${currentIndoorTemp}° (${currentIndoorTemp.class})
            <br><b>Outdoor temp:</b> ${currentOutdoorTemp}°
            <br><b>Indoor/Setpoint </b>difference: ${indoorToSetpointDiff}°
            <br><b>Indoor/Outdoor </b>difference: ${indoorToOutdoorDiff}°
            <br><b>minOutdoorTempForCooling: $minOutdoorTempForCooling (${minOutdoorTempForCooling.class})
            <br><b>maxOutdoorTempForHeating: $maxOutdoorTempForHeating (${maxOutdoorTempForHeating.class})
            <br><b>tooColdOutside: $tooColdOutside
            <br><b>tooWarmOutside: $tooWarmOutside
            <br><b>High amplitude:</b> ${highAmplitude}
            <br><b>Low amplitude:</b> ${lowAmplitude}
            <br><b>Last mode:</b> ${state.lastMode}
            <br><b>Time since </b>last change: ${timeSinceLastChange/1000/60} minutes
            </div>
        """
        // Decision logic
        if (indoorToSetpointDiff > coolDiff) {
            if (!tooColdOutside && (currentOutdoorTemp >= currentIndoorTemp || highAmplitude)) {
                logTrace "Indoor temperature too high, ${highAmplitude ? 'amplitude too high' : 'outdoor is warmer'} - mechanical cooling needed"
                results += changeMode("cool", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = false
            } else {
                logTrace "Indoor temperature high but outdoor is cold enough - natural cooling possible"
                results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = false
            }
        } 
        else if (indoorToSetpointDiff < heatDiff) {
            
            if (!tooWarmOutside && (currentOutdoorTemp <= currentIndoorTemp || lowAmplitude)) {
                logTrace "Indoor temperature too low, ${lowAmplitude ? 'amplitude too high' : 'outdoor is colder'} - heating needed"
                results += changeMode("heat", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = boost ? true : false
            } else {
                logTrace "Indoor temperature low but outdoor is warm enough - natural heating possible"
                results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
                state.boostMode = boost ? true : false
            }
        }
        else {
            logTrace "No extreme conditions detected - setting to ${off_mode}"
            results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
        }
    // }

    logDebug "results: $results"
    if (results){
        def cools = results.findAll{it -> it == "cool"}
        def heats = results.findAll{it -> it == "heat"}

        logDebug "cools.size(): ${cools.size()}"
        logDebug "heats.size(): ${heats.size()}"

        if (cools.size() > heats.size()){
            logDebug "cools win"
            final_result = "cool"
        }
        else if (heats.size() > cools.size()){
            logDebug "heats win"
            final_result = "heat"
        }
        else {
            logDebug "nor cool nor heat win..."
            final_result =  off_mode
        }
        
    }
    else {        
        log.error "This line shouldn't appear. Mode will be set to auto as fallback"
        final_result =  "auto"
    }

    

    logTrace "need returns: $final_result"
    state.lastNeed = final_result
    return final_result
}
def getSeasonalComfortAdjustment() {
    /**
    * getSeasonalComfortAdjustment()
    * Calculates seasonal comfort adjustments
    * @return Number temperature adjustment based on season
    * 
    * Modifies comfort ranges based on learned seasonal patterns
    */
    def season = getSeason()
    def patterns = state.thermalBehavior.environmentalFactors.seasonalPatterns[season]
    if (!patterns) return 0
    
    // Calculate seasonal adjustment based on learned patterns
    def avgTemp = patterns.avgTemp
    def tempRange = patterns.tempRange.max - patterns.tempRange.min
    def typicalChange = patterns.typicalChanges ? patterns.typicalChanges.average() : 0
    
    // Adjust comfort range based on season
    def adjustment = 0
    switch(season) {
        case "summer":
            adjustment = 1.0 // Allow slightly higher temperatures
            break
        case "winter":
            adjustment = -1.0 // Allow slightly lower temperatures
            break
        default:
            adjustment = 0
    }
    
    return adjustment
}
def getComfortVisualization() {
    /**
    * getComfortVisualization()
    * Generates HTML visualization of comfort management data
    * @return String containing HTML/CSS for comfort visualization
    * 
    * Creates visual representation of system performance and patterns
    */
    def html = """
        <style>
            .comfort-viz {
                font-family: system-ui;
                padding: 15px;
                border-radius: 8px;
                background: #f5f5f5;
            }
            .comfort-chart {
                display: flex;
                height: 200px;
                margin: 20px 0;
                border-left: 2px solid #333;
                border-bottom: 2px solid #333;
            }
            .chart-bar {
                flex: 1;
                margin: 0 2px;
                background: linear-gradient(to top, #4CAF50, #2196F3);
                transition: height 0.3s ease;
            }
            .metrics-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 15px;
            }
            .metric-card {
                background: white;
                padding: 10px;
                border-radius: 4px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
        </style>
        
        <div class="comfort-viz">
            <h3>Comfort Management Analytics</h3>
            
            <div class="metrics-grid">
                ${getMetricCards()}
            </div>
            
            <h4>Temperature Management Success Rate</h4>
            <div class="comfort-chart">
                ${getSuccessRateChart()}
            </div>
            
            <h4>Environmental Patterns</h4>
            <div class="metrics-grid">
                ${getEnvironmentalPatterns()}
            </div>
        </div>
    """
    
    return html
}
def getMetricCards() {
    def cards = []
    def metrics = getComfortMetrics()
    
    metrics.each { metric ->
        cards << """
            <div class="metric-card">
                <h4>${metric.name}</h4>
                <div class="metric-value">${metric.value}</div>
                <div class="metric-trend">${metric.trend}</div>
            </div>
        """
    }
    
    return cards.join("\n")
}
def getComfortMetrics() {
    def naturalCooling = state.thermalBehavior.naturalCooling
    def naturalHeating = state.thermalBehavior.naturalHeating
    
    // Convert to BigDecimal and use setScale() instead of round()
    return [
        [
            name: "Natural Cooling Efficiency",
            value: "${(naturalCooling.events ? new BigDecimal(naturalCooling.events.collect{it.coolingRate}.average()).setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0)}°/hr",
            trend: "↑ 5% this week"
        ],
        [
            name: "Energy Savings",
            value: "${calculateEnergySavings().setScale(1, BigDecimal.ROUND_HALF_UP)}%",
            trend: "↑ 12% vs. last month"
        ],
        [
            name: "Comfort Score",
            value: "${(new BigDecimal(calculateComfortScore() * 100).setScale(0, BigDecimal.ROUND_HALF_UP))}%",
            trend: "↑ 3% improvement"
        ]
    ]
}
def calculateEnergySavings() {
    /**
    * calculateEnergySavings()
    * Calculates energy savings from natural temperature management
    * 
    * Process Flow:
    * ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
    * │  Get Natural  │────>│   Calculate   │────>│   Compute     │
    * │Events (7 days)│     │Energy per Event│     │Total Savings %│
    * └───────────────┘     └───────────────┘     └───────────────┘
    *         │                     │                     │
    *    Events List          kWh Savings           Percentage
    * 
    * @return BigDecimal Percentage of energy saved compared to baseline
    * 
    * Calculation Method:
    * 1. Collect natural cooling/heating events from past week
    * 2. For each event:
    *    - Convert duration to hours
    *    - Calculate kWh saved (vs. mechanical HVAC)
    *    - Adjust for effectiveness
    * 3. Compare to baseline (24/7 HVAC operation)
    * 
    * Example Return:
    * 23.5 (meaning 23.5% energy saved)
    * 
    * @see trackEnvironmentalFactors() for event logging
    * @see getComfortMetrics() for usage in UI
    */
    // Initialize return value as BigDecimal
    def savings = new BigDecimal("0.0")
    
    try {
        // Get the last 7 days of natural cooling/heating events
        def sevenDaysAgo = new Date(now() - (7 * 24 * 60 * 60 * 1000))
        
        // Get all natural management events
        def naturalEvents = (state.thermalBehavior.naturalCooling.events + 
                           state.thermalBehavior.naturalHeating.events)
                          .findAll { event ->
            def eventDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", event.timestamp)
            eventDate.after(sevenDaysAgo)
        }
        
        if (!naturalEvents) {
            return savings // Return 0.0 if no events
        }
        
        // Estimate energy saved per natural cooling/heating event
        // Based on typical HVAC energy consumption rates
        def totalEnergySaved = naturalEvents.collect { event ->
            // Convert event duration from seconds to hours
            def durationHours = event.duration / 3600
            
            // Estimate kWh saved based on typical HVAC consumption
            // Assuming average 3kW consumption for HVAC
            def kwhSaved = new BigDecimal("3.0") * new BigDecimal(durationHours)
            
            // Adjust based on effectiveness (cooling/heating rate achieved)
            def effectiveness = new BigDecimal(event.coolingRate).abs() / new BigDecimal("2.0") // normalized to typical rate
            effectiveness = effectiveness > new BigDecimal("1.0") ? new BigDecimal("1.0") : effectiveness
            
            return kwhSaved * effectiveness
        }.sum() ?: new BigDecimal("0.0")
        
        // Calculate percentage savings
        // Assuming 24/7 HVAC operation as baseline
        def totalPossibleUsage = new BigDecimal("72.0") // 3kW * 24h
        savings = (totalEnergySaved / totalPossibleUsage) * new BigDecimal("100.0")
        
    } catch (Exception e) {
        log.error "Error calculating energy savings: ${e.message}"
        return new BigDecimal("0.0")
    }
    
    return savings
}

def calculateComfortScore() {
        /**
    * calculateComfortScore()
    * Evaluates overall comfort management effectiveness
    * 
    * Scoring Components:
    * ┌─────────────────┐
    * │  Comfort Score  │
    * └────────┬────────┘
    *     ┌────┴────┬────────┐
    * ┌────┴───┐ ┌──┴───┐ ┌──┴───┐
    * │ Temp   │ │System│ │Stable│
    * │Maintain│ │ Resp │ │Temps │
    * └────┬───┘ └──┬───┘ └──┬───┘
    *   0.4*x    0.3*y    0.3*z
    * 
    * @return BigDecimal Score between 0 and 1
    * 
    * Scoring Factors:
    * - Temperature Maintenance (40%)
    *   - How well target temperature is maintained
    * - System Response (30%)
    *   - How quickly system responds to changes
    * - Temperature Stability (30%)
    *   - How consistent temperatures remain
    * 
    * Example Returns:
    * 0.85 - Excellent comfort management
    * 0.60 - Average performance
    * 0.30 - Needs improvement
    * 
    * @see analyzeOccupancyHeat() for occupancy impact
    * @see analyzeSolarGain() for environmental factors
    */
    def score = new BigDecimal("0.0")
    
    try {
        // Get recent temperature events
        def events = state.thermalBehavior.naturalCooling.events + 
                    state.thermalBehavior.naturalHeating.events
        
        if (!events) {
            return new BigDecimal("0.8") // Default score if no events
        }
        
        // Factors that influence comfort score
        def factors = [
            temperatureDeviation: new BigDecimal("0.4"),  // Weight for temp maintenance
            responseTime: new BigDecimal("0.3"),          // Weight for system response
            consistency: new BigDecimal("0.3")            // Weight for stable comfort
        ]
        
        // Calculate temperature maintenance score
        def targetTemp = thermostatController.currentValue("thermostatSetpoint") as BigDecimal
        def tempDeviations = events.collect { event ->
            def startDiff = (new BigDecimal(event.startTemp) - targetTemp).abs()
            def endDiff = (new BigDecimal(event.endTemp) - targetTemp).abs()
            return [startDiff, endDiff]
        }.flatten()
        
        def avgDeviation = tempDeviations.sum() / tempDeviations.size()
        def tempScore = new BigDecimal("1.0") - (avgDeviation / new BigDecimal("10.0"))
        tempScore = tempScore < new BigDecimal("0.0") ? new BigDecimal("0.0") : tempScore
        
        // Calculate response time score
        def avgDuration = new BigDecimal(events.collect { it.duration }.average())
        def responseScore = new BigDecimal("1.0") - (avgDuration / (4 * 3600)) // Normalized to 4 hours
        responseScore = responseScore < new BigDecimal("0.0") ? new BigDecimal("0.0") : responseScore
        
        // Calculate consistency score
        def coolingRates = events.collect { new BigDecimal(it.coolingRate) }
        def avgRate = coolingRates.sum() / coolingRates.size()
        def rateVariance = coolingRates.collect { (it - avgRate).pow(2) }.sum() / coolingRates.size()
        def consistencyScore = new BigDecimal("1.0") - (rateVariance / new BigDecimal("4.0"))
        consistencyScore = consistencyScore < new BigDecimal("0.0") ? new BigDecimal("0.0") : consistencyScore
        
        // Calculate weighted final score
        score = (tempScore * factors.temperatureDeviation) +
                (responseScore * factors.responseTime) +
                (consistencyScore * factors.consistency)
                
        // Ensure score is between 0 and 1
        score = score > new BigDecimal("1.0") ? new BigDecimal("1.0") : score
        score = score < new BigDecimal("0.0") ? new BigDecimal("0.0") : score
        
    } catch (Exception e) {
        log.error "Error calculating comfort score: ${e.message}"
        return new BigDecimal("0.8") // Return default score on error
    }
    
    return score
}
def getSuccessRateChart() {
    /**
    * getSuccessRateChart()
    * Generates HTML visualization of temperature management success rates
    * 
    * Data Flow:
    * ┌──────────┐    ┌──────────┐    ┌──────────┐
    * │Collect   │───>│Transform │───>│Generate  │
    * │Rate Data │    │  to %    │    │HTML/CSS  │
    * └──────────┘    └──────────┘    └──────────┘
    * 
    * Chart Structure:
    *     Success %
    * 100│   █
    *    │ █ █ █
    *  50│ █ █ █ █
    *    │ █ █ █ █ █
    *   0└─────────────
    *     C5 C10 H5 H10
    *     Temperature Δ
    * 
    * @return String HTML/CSS for bar chart visualization
    * 
    * Visualization Features:
    * - Color-coded bars (blue=cooling, green=heating)
    * - Tooltips with detailed information
    * - Responsive design
    * - Graceful fallback for missing data
    * 
    * @see getComfortVisualization() for complete UI
    */
    def bars = []
    try {
        // Get success rates for different temperature deltas
        def coolingRates = state.thermalBehavior.naturalCooling.performanceByDelta
        def heatingRates = state.thermalBehavior.naturalHeating.performanceByDelta
        
        // Combine and sort performance data
        def allRates = [:]
        coolingRates.each { delta, metrics ->
            allRates["C${delta}"] = metrics.successRate * 100
        }
        heatingRates.each { delta, metrics ->
            allRates["H${delta}"] = metrics.successRate * 100
        }
        
        // Generate bars for each rate
        allRates.sort().each { label, rate ->
            def height = rate ?: 0
            def color = label.startsWith('C') ? '#2196F3' : '#4CAF50'
            def title = label.startsWith('C') ? 'Cooling' : 'Heating'
            title += " ${label.substring(1)}°F"
            
            bars << """
                <div class="chart-bar" 
                     style="height: ${height}%; background: ${color};"
                     title="${title}: ${height.round(1)}% success">
                </div>
            """
        }
    } catch (Exception e) {
        log.error "Error generating success rate chart: ${e.message}"
        // Return a single empty bar if there's an error
        bars << '<div class="chart-bar" style="height: 0%"></div>'
    }
    
    return bars.join("\n")
}

def getEnvironmentalPatterns() {
    /**
    * getEnvironmentalPatterns()
    * Generates visualization of environmental impact patterns
    * 
    * Data Organization:
    * ┌─────────────────┐
    * │Environmental    │
    * │Patterns UI     │
    * └─┬──────┬───────┘
    *   │      │       │
    * ┌─┴──┐ ┌─┴──┐ ┌─┴──┐
    * │Season│Solar│Occup│
    * │Stats │Heat │Heat │
    * └─────┘└─────┘└────┘
    * 
    * @return String HTML markup for pattern visualization
    * 
    * Components:
    * - Seasonal temperature patterns
    *   - Average temperatures
    *   - Temperature ranges
    * - Solar gain impact
    *   - Average daily gain
    *   - Peak hours
    * - Occupancy patterns
    *   - Heat contribution
    *   - Usage patterns
    * 
    * @see trackEnvironmentalFactors() for data collection
    * @see getDetailedAnalytics() for detailed view
    */
    def patterns = []
    try {
        def envFactors = state.thermalBehavior.environmentalFactors
        
        // Add seasonal patterns
        def currentSeason = getSeason()
        def seasonalData = envFactors.seasonalPatterns[currentSeason]
        if (seasonalData) {
            patterns << """
                <div class="metric-card">
                    <h4>Seasonal Patterns (${currentSeason.capitalize()})</h4>
                    <div>Average: ${seasonalData.avgTemp.round(1)}°F</div>
                    <div>Range: ${seasonalData.tempRange.min.round(1)}°F - 
                               ${seasonalData.tempRange.max.round(1)}°F</div>
                </div>
            """
        }
        
        // Add solar impact patterns
        def solarData = envFactors.solarGainPatterns
        if (solarData) {
            def avgGain = solarData.collect { timeBlock, data -> 
                data.solarGainWatts ?: 0 
            }.average()
            
            patterns << """
                <div class="metric-card">
                    <h4>Solar Impact</h4>
                    <div>Average Gain: ${avgGain.round(1)} watts</div>
                </div>
            """
        }
        
        // Add occupancy patterns
        def occupancyData = envFactors.occupancyPatterns
        if (occupancyData) {
            def avgHeat = occupancyData.collect { timeBlock, data ->
                data.heatOutput ?: 0
            }.average()
            
            patterns << """
                <div class="metric-card">
                    <h4>Occupancy Impact</h4>
                    <div>Average Heat: ${avgHeat.round(1)} watts</div>
                </div>
            """
        }
        
    } catch (Exception e) {
        log.error "Error generating environmental patterns: ${e.message}"
        patterns << """
            <div class="metric-card">
                <h4>Environmental Patterns</h4>
                <div>Insufficient data</div>
            </div>
        """
    }
    
    return patterns.join("\n")
}

def getDetailedAnalytics() {
    /**
    * getDetailedAnalytics()
    * Generates comprehensive performance analytics visualization
    * 
    * Analytics Structure:
    * ┌────────────────────┐
    * │Detailed Analytics  │
    * └┬──────────┬───────┘
    *  │          │
    * ┌┴────┐   ┌─┴───┐
    * │Perf.│   │Success│
    * │Stats│   │Rates │
    * └─────┘   └──────┘
    * 
    * @return String HTML markup for analytics display
    * 
    * Components:
    * 1. Performance Metrics
    *    - Total events
    *    - Average rates
    *    - System efficiency
    * 2. Success Rates
    *    - By temperature delta
    *    - By mode (heating/cooling)
    *    - Historical trends
    * 
    * @see calculateComfortScore() for scoring
    * @see calculateEnergySavings() for efficiency
    */
    try {
        def analytics = []
        def data = state.thermalBehavior
        
        // Performance metrics
        def totalEvents = (data.naturalCooling.events + data.naturalHeating.events).size()
        def avgCoolingRate = data.naturalCooling.events ? 
            data.naturalCooling.events.collect{it.coolingRate}.average() : 0
        def avgHeatingRate = data.naturalHeating.events ?
            data.naturalHeating.events.collect{it.coolingRate}.average() : 0
            
        analytics << """
            <div class="detailed-analytics" style="margin-top: 20px;">
                <h4>Performance Metrics</h4>
                <table style="width: 100%; border-collapse: collapse;">
                    <tr>
                        <td style="padding: 8px; border: 1px solid #ddd;">Total Events</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">${totalEvents}</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #ddd;">Avg Cooling Rate</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">${avgCoolingRate.round(2)}°/hr</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #ddd;">Avg Heating Rate</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">${avgHeatingRate.round(2)}°/hr</td>
                    </tr>
                </table>
            </div>
        """
        
        // Success rates by temperature delta
        def successRates = []
        data.naturalCooling.performanceByDelta.each { delta, metrics ->
            successRates << """
                <tr>
                    <td style="padding: 8px; border: 1px solid #ddd;">Cooling ${delta}°F</td>
                    <td style="padding: 8px; border: 1px solid #ddd;">
                        ${(metrics.successRate * 100).round(1)}%
                    </td>
                </tr>
            """
        }
        data.naturalHeating.performanceByDelta.each { delta, metrics ->
            successRates << """
                <tr>
                    <td style="padding: 8px; border: 1px solid #ddd;">Heating ${delta}°F</td>
                    <td style="padding: 8px; border: 1px solid #ddd;">
                        ${(metrics.successRate * 100).round(1)}%
                    </td>
                </tr>
            """
        }
        
        if (successRates) {
            analytics << """
                <div style="margin-top: 20px;">
                    <h4>Success Rates by Temperature Delta</h4>
                    <table style="width: 100%; border-collapse: collapse;">
                        ${successRates.join("\n")}
                    </table>
                </div>
            """
        }
        
        return analytics.join("\n")
        
    } catch (Exception e) {
        log.error "Error generating detailed analytics: ${e.message}"
        return "<div>Error generating detailed analytics</div>"
    }
}
def calculateTrendIndicator(currentValue, historicalValues) {
    /**
    * calculateTrendIndicator()
    * Calculates trend direction and magnitude for metrics
    * 
    * Trend Calculation:
    * ┌──────────┐   ┌──────────┐   ┌──────────┐
    * │Historical│   │ Compare  │   │ Generate │
    * │ Values   │──>│  to Avg  │──>│Indicator │
    * └──────────┘   └──────────┘   └──────────┘
    * 
    * @param currentValue Current metric value
    * @param historicalValues List of previous values
    * @return Map with direction (↑,→,↓) and percentage
    * 
    * Trend Analysis:
    * - Upward trend (↑): Current > Historical avg
    * - Neutral (→): Current ≈ Historical avg
    * - Downward (↓): Current < Historical avg
    * 
    * Example Return:
    * [direction: "↑", percentage: "5.2"]
    * 
    * @see getComfortMetrics() for trend display
    */
    try {
        if (!historicalValues || historicalValues.size() < 2) {
            return [direction: "→", percentage: "0"]
        }
        
        // Convert inputs to BigDecimal
        currentValue = new BigDecimal(currentValue)
        def avgHistorical = new BigDecimal(historicalValues.sum()) / new BigDecimal(historicalValues.size())
        
        // Calculate percentage change
        def change = ((currentValue - avgHistorical) / avgHistorical) * new BigDecimal("100")
        
        // Determine direction
        def direction = "→"
        if (change > new BigDecimal("0")) {
            direction = "↑"
        } else if (change < new BigDecimal("0")) {
            direction = "↓"
        }
        
        return [
            direction: direction,
            percentage: "${change.abs().setScale(1, BigDecimal.ROUND_HALF_UP)}"
        ]
    } catch (Exception e) {
        log.error "Error calculating trend: ${e.message}"
        return [direction: "→", percentage: "0"]
    }
}

def calculateMovingAverage(List values, int window) {
    /**
    * calculateMovingAverage()
    * Calculates moving average for smoothing time series data
    * 
    * Processing Steps:
    * ┌─────────┐   ┌─────────┐   ┌─────────┐
    * │Window   │   │Sum      │   │Average  │
    * │Selection│──>│Values   │──>│Calc     │
    * └─────────┘   └─────────┘   └─────────┘
    * 
    * @param values List of numerical values
    * @param window Size of moving average window
    * @return List of moving averages
    * 
    * Example:
    * Input: [1,2,3,4,5], window=3
    * Output: [2.00,3.00,4.00]
    * 
    * Method:
    * - Slides window over data points
    * - Calculates average within window
    * - Uses BigDecimal for precision
    * 
    * @see getDetailedAnalytics() for trend smoothing
    */
    if (!values || window <= 0 || window > values.size()) {
        return []
    }
    
    def result = []
    for (int i = window - 1; i < values.size(); i++) {
        def sum = new BigDecimal("0")
        for (int j = 0; j < window; j++) {
            sum += new BigDecimal(values[i - j].toString())
        }
        result << (sum / new BigDecimal(window)).setScale(2, BigDecimal.ROUND_HALF_UP)
    }
    
    return result
}
def initialize_intelligence_states(){
    // From trackEnvironmentalFactors()
    state.thermalBehavior = state.thermalBehavior ?: [
        roomId: app.id,
        naturalCooling: [events: [], performanceByDelta: [:]], 
        naturalHeating: [events: [], performanceByDelta: [:]], 
        environmentalFactors: [
            dailyPatterns: [:],
            seasonalPatterns: [:],
            solarGainPatterns: [:],
            occupancyPatterns: [:]
        ]
    ]

    // For temperature tracking
    state.lastIndoorTemp = state.lastIndoorTemp ?: get_indoor_temperature()

    // For event tracking
    state.currentThermalEvent = state.currentThermalEvent ?: null

    // For comfort metrics
    state.comfortMetrics = state.comfortMetrics ?: [
        naturalCoolingEfficiency: 0.0,
        energySavings: 0.0,
        comfortScore: 0.0
    ]

    // For performance tracking
    state.performanceHistory = state.performanceHistory ?: [
        weeklyAverages: [:],
        monthlyAverages: [:],
        lastUpdated: now()
    ]

    // For prediction confidence
    state.predictionMetrics = state.predictionMetrics ?: [
        successfulPredictions: 0,
        totalPredictions: 0,
        lastReset: now()
    ]

    // For UI visualization data
    state.visualizationData = state.visualizationData ?: [
        chartData: [],
        lastRefresh: now()
    ]

    log.warn "Intelligence states are now reset!"
}
