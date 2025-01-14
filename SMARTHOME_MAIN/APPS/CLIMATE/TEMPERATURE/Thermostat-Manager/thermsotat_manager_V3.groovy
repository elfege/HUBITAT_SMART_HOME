/** 
 * Last Updated: 2025-01-14
 */
/**
 * Thermostat Manager V3
 *
 * License: Non-Commercial Use Only
 * This software is licensed under the Thermostat Manager Non-Commercial License.
 * You are free to use, copy, modify, and distribute this software for non-commercial purposes only.
 * For more details, see the LICENSE file included in this repository or contact the author.
 *
 * Author: Elfege Leylavergne
 * Version: 
*/

import java.text.SimpleDateFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

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
            def therms = thermostats.collect{ t -> t.displayName}
            if (boost){
                input "tempBoostHeat", "number", title: "Temperature to set Thermostat to when boost heating", defaultValue:85
                input "tempBoostCool", "number", title: "Temperature to set Thermostat to when boost cooling", defaultValue:65
                input "boostThermostats", "enum", title: "Thermosats to boost", options: therms, submitOnChange: true, required: false, multiple:true
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
                    def therms = thermostats.collect{ t -> t.displayName}
                    if (therms.size() > 1) {
                        input "nightModeThermostats", "enum", title: "optional: apply this rule only to these thermostats:", options: therms, submitOnChange: true, multiple: true, required: false
                    }
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
            def use_ai_descr = useAI ? "Disable AI management (not destructive)" : "Enable AI management (beta)"
            input "useAI", "bool", title:"$use_ai_descr", submitOnChange: true
            if (useAI){
                try {
                    getComfortManagementSection()
                } catch (Exception e) {
                    log.error "Error in Comfort Intelligence section: $e"
                    initialize_intelligence_states()
                }
            }
        }

                section("Comfort Analytics") {
            if (useAI) {
                try {
                    input "toggleKPIs", "button", 
                        title: "${state.showKPIs ? 'Close KPIs' : 'Show KPIs'}", 
                        submitOnChange: true
                        
                    if (state.showKPIs) {
                        try {
                            paragraph getComfortKPIDashboard()
                        } catch (Exception e) {
                            log.error "Error generating KPI dashboard: $e"
                            paragraph formatText("Error generating KPI dashboard. Check logs for details.", "white", "red")
                        }
                    }
                    
                    input "toggleAnalytics", "button", 
                        title: "${state.showAnalytics ? 'Hide Analytics' : 'Show Analytics'}", 
                        submitOnChange: true
                        
                    if (state.showAnalytics) {
                        try {
                            paragraph getComfortVisualization()
                            
                            input "showDetailedAnalytics", "bool", 
                                title: "Show Detailed Analytics", 
                                defaultValue: false, 
                                submitOnChange: true
                            
                            if (showDetailedAnalytics) {
                                paragraph getDetailedAnalytics()
                            }
                        } catch (Exception e) {
                            log.error "Error generating analytics visualization: $e"
                            paragraph formatText("Error generating analytics. Check logs for details.", "white", "red")
                        }
                    }
                } catch (Exception e) {
                    log.error "Error in Comfort Analytics section: $e"
                    initialize_intelligence_states()
                }
            } else {
                paragraph "Enable AI to see analytics"
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
                input "reset_therm_memoizations", "button", title: "Reset Thermostats Memoized States", submitOnChange: true
                if(lightSignal){
                    input "flash", "button", title: "Test light signal", submitOnChange: true
                }
                input "prompt_reset_intelligence", "button", title: "Reset Intelligence Learnings (!)", submitOnChange: true
                if(state.prompt_reset_intelligence){
                    // TODO: add backup logic (to a file) and RESTORE logic (from file). 
                    paragraph formatText("ARE YOU SURE? This will potentially destroy months of accumulated learnings! This action is irreversible!", "white", "red")
                    
                    input "reset_intelligence", "button", title: "Yes, melt the T2", submitOnChange: true
                    input "cancel_reset_intelligence", "button", title: "Cancel, let the apocalypse come!", submitOnChange: true
                }
                // backup/restore controls
                input "backup_intelligence", "button", title: "Backup Intelligence Data", submitOnChange: true
                input "restore_intelligence", "button", title: "Restore From Backup", submitOnChange: true
                if(state.showRestoreOptions) {
                    paragraph "Select backup file to restore from:"
                    input "backupFile", "string", title: "Backup file name", submitOnChange: true
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
    // Initialize setpoints memoization
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

    // UI-related states
    state.showKPIs = state.showKPIs ?: false
    state.showAnalytics = state.showAnalytics ?: false
    
    // Device temperature tracking
    state.deviceTemperatures = state.deviceTemperatures ?: [:]

    // Logging-related states
    state.EnableDebugTime = state.EnableDebugTime ?: 0
    state.EnableTraceTime = state.EnableTraceTime ?: 0
    state.EnableInfoTime = state.EnableInfoTime ?: 0
    state.EnableWarnTime = state.EnableWarnTime ?: 0
    state.lastCheckTimer = state.lastCheckTimer ?: now()

    // Pause-related states
    state.paused = state.paused ?: false
    state.pauseStart = state.pauseStart ?: 0
    state.pauseDueToButtonEvent = state.pauseDueToButtonEvent ?: false

    // Night mode states
    state.nightModeActive = state.nightModeActive ?: false
    state.nightModeActivationTime = state.nightModeActivationTime ?: 0

    // Boost mode states    
    state.boostTempArray = state.boostTempArray ?: []

    // Last operation tracking
    state.lastRun = state.lastRun ?: now()
    state.lastNeed = state.lastNeed ?: "heat"
    state.lastModeChangeTime = state.lastModeChangeTime ?: now()
    state.lastMode = state.lastMode ?: state.lastNeed
    state.lastMotionHandled = state.lastMotionHandled ?: 0
    state.lasActivetMotionHandled = state.lasActivetMotionHandled ?: 0

    // Functional sensors tracking
    state.functionalSensors = state.functionalSensors ?: [:]

    // Virtual thermostat states
    state.read_about_therm_controller = state.read_about_therm_controller ?: false
    state.vir_therm_created = state.vir_therm_created ?: false
    state.showVirThermLocationInput = state.showVirThermLocationInput ?: false
    
    // Confirmation states
    state.prompt_reset_intelligence = state.prompt_reset_intelligence ?: false

    // Location URL for device management
    state.locationUrl = "http://${location.hub.localIP}/device/edit"
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
        case "reset_therm_memoizations":
            resetMem()
            break
        case "prompt_reset_intelligence":
            state.prompt_reset_intelligence = true
            break
        case "reset_intelligence":
            initialize_intelligence_states()
            break
        case "cancel_reset_intelligence": 
            state.prompt_reset_intelligence = false
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
        case "toggleKPIs":
            state.showKPIs = !state.showKPIs
            break
            
        case "toggleAnalytics":
            state.showAnalytics = !state.showAnalytics
            break
        case "backup_intelligence":
            try {
                def fileName = backupIntelligenceData()
                if (fileName) {
                    paragraph "Data backed up to: ${fileName}"
                }
            } catch (Exception e) {
                logError "backup_intelligence => Couldn't backup data. Operation canceled: $e"
            }
            break
        case "restore_intelligence":
            state.showRestoreOptions = true
            break
        case "reset_intelligence":
            try {
                backupIntelligenceData()
            } catch (Exception e){
                logError "Couldn't backup data. Reset operation canceled."
                break
            }
                initialize_intelligence_states()
            break
    }
}

def getThermControllerParagraph() {
    def prgrph = """
        <style>
            #therm-controller * {
                margin: 0 !important;
                padding: 0 !important;
                text-indent: 0 !important;
                line-height: 1.3 !important;
            }
            #therm-controller > div {
                flex: 1;
                border: 1px solid #dee2e6;
                padding: 12px !important;
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            #therm-controller .column-header {
                font-weight: bold;
                color: #2c3e50;
                font-size: 14px;
                margin-bottom: 0px !important;
            }
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
def createVirtualThermostat() {
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
    /** 
    * Last Updated: 2025-01-14
    */
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
    if(restriction().data.restricted_mode) return
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
    if(restriction().data.restricted_mode) return
    
    // Check if current value is a boost temperature
    if(isBoostTemp(evt.value)) {
        logWarn "Operation in boost mode (${evt.value}F matches boost temperature). Not updating state.thermostatsSetpointSTATES"
        return
    }

    updateMem(evt.device, attribute="all")
    
}

def pushableButtonHandler(evt){
    if(restriction().data.restricted_mode) return
    log.trace "BUTTON EVT $evt.device $evt.name $evt.value"

    def nightModeActive = state.nightModeActive ?: false 

    if (evt.name == "pushed") {
        nightModeActive = !nightModeActive
    }

    state.nightModeActive = nightModeActive

    if(nightModeActive){
        log.trace formatText("NIGHT MODE ACTIVATED", "white", "orange")
        state.nightModeActivationTime = now()

        flashTheLight()
    }else {
        log.trace formatText("NIGHT MODE CANCELED", "white", "orange")
    }
}

def flashTheLight(){
    if(!lightSignal) return
    // save current on/off state
    state.prevLightSignalState = lightSignal.currentValue("switch")
    // Flash light if configured
    lightSignal?.flash()
    runIn(3, stopFlashing)
}

def stopFlashing() {
    lightSignal.off()
    lightSignal."${state.prevLightSignalState ?: "off"}"()
}

def motionHandler(evt) {
    if(restriction().data.restricted_mode) return
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
    else if(lastMotionHandled > someSecondsAgoActiveEvents) {
        
        master([calledBy:"motionHandler", motionActiveEvent: true])
        state.lasActivetMotionHandled = now
    }
    
    
}

def ChangedModeHandler(evt){
    if(restriction(motionActiveEvent).data.restricted_mode) return
    logInfo "Location is in ${evt.value} mode" 
    master([calledBy:"ChangedModeHandler", motionActiveEvent: false])
}
def temperatureHandler(evt){
    if(restriction(motionActiveEvent).data.restricted_mode) return
    // Initialize state for tracking device temperatures if not exists
    state.deviceTemperatures = state.deviceTemperatures ?: [:]

    log.debug "state.deviceTemperatures: $state.deviceTemperatures"
    
    // Convert deviceId to string to ensure consistent key type
    def deviceId = evt.deviceId.toString()
    def currentTemp = evt.value.toFloat()
    def previousTemp = state.deviceTemperatures[deviceId] ?: currentTemp
    
    logInfo "$evt.device temperature is ${currentTemp}F"
    log.debug "Previous temp for device $deviceId was: $previousTemp"
    
    // Calculate absolute temperature difference
    def tempDifference = Math.abs(currentTemp - previousTemp)
        
    // Only call master if temperature change is ≥ 0.5°F
    if (tempDifference >= 0.5) {
        logInfo "Temperature change of ${tempDifference.round(1)}°F detected for ${evt.device}. Triggering master()"
        // Update the temperature used for comparison only after deciding to call master()
        state.deviceTemperatures[deviceId] = currentTemp
        master([calledBy:"temperatureHandler", motionActiveEvent: false])
    } else {
        logTrace "Temperature change of ${tempDifference}°F is less than threshold. Skipping master() call."
    }
}
def outsideTempHandler(evt){
    if(restriction().data.restricted_mode) return
    logInfo "$evt.device temperature is ${evt.value}F" 
    master([calledBy:"outsideTempHandler", motionActiveEvent: false])

}
def contactHandler(evt){
    if(restriction().data.restricted_mode) return
    logInfo "$evt.device is ${evt.value}" 
    master([calledBy:"contactHandler", motionActiveEvent: false])
}

def master(data){

    if(restriction().data.restricted_mode) return

    def lapse = 5 // interval in seconds
    state.lastRun = state.lastRun == null ? now() : state.lastRun

    if (now() - state.lastRun > lapse * 1000){

        log.trace "isInNightMode(): ${isInNightMode()}"
        log.debug "master: $data"

        def isNightMode = isInNightMode()

        handleThermosats(data.motionActiveEvent, isNightMode)

        state.lastRun = now()
    

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
    else {
        logTrace "master ran less than $lapse seconds ago. Skipping"
    }
}

def handleThermosats(motionActiveEvent=false, isNightMode=false){

    logDebug "Processing thermostats..."

    
    def allThermostats = getAllThermostats()
    
    def contactOpen = contacts.any{it -> it.currentValue("contact") == "open"}
    if(contactOpen){
        def openContacts = contacts.findAll{it -> it.currentValue('contact') == 'open'}
        log.warn "<b>${openContacts.join(', ')} ${openContacts.size() > 1 ? 'are' : 'is'} open </b>"   
    }
    def need = contactOpen ? "off" : get_need(motionActiveEvent) 
    attribute = need in ["heat", "cool"] ? "${need}ingSetpoint" : "thermostatSetpoint"

    allThermostats.each { thermostat -> 

        logInfo "Processing ${thermostat}..."

        def isNightModeTherm = !nightModeThermostats ? true : nightModeThermostats.contains(thermostat.displayName)

        if (isNightMode && !isNightModeTherm) {
            log.warn "Keeping ${thermostat.displayName} off due to night/sleeping mode"
            setThermostatsMode([deviceId: thermostat.id, need: "off"])
            return
        }

        setThermostatsMode([deviceId: thermostat.id, need: need])

        if (need in ["heat", "cool"]){ 
            def isBoostThermostat = boostThermostats?.contains(thermostat.displayName) ?: false
            def isBoost = !boostThermostats ? boost : isBoostThermostat
            

            log.debug "${thermostat.displayName} isBoostThermostat ? $isBoostThermostat | isBoost ? $isBoost"
            
            if (isBoost) {

                logWarn "handling boost (need:$need)"

                // def targetTemp = need == "heat" ? tempBoostHeat : tempBoostCool
                def targetTemp = getTargetTemp(true, need)
                

                if (thermostat.currentValue(attribute) != val) {
                    logDebug "thermostat -> $thermostat"
                    updateMem(thermostat, attribute)
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

        // end of boost or no boost, apply (or restore) target as setpoint
        def targetTemp = getTargetTemp()
        

        
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
    log.trace "allThermostats: $uniqueThermostats"
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
// def changeMode(String newMode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime) {
//     // Only heat and cool are active HVAC modes that need delays
    
//     // Only apply delay when switching between heat and cool or high/low amplitude compared to target temp
//     if (isActiveMode(newMode, highAmplitude, lowAmplitude) &&
//      isActiveMode(state.lastMode, highAmplitude, lowAmplitude) && 
//      newMode != state.lastMode && 
//      timeSinceLastChange <= delayBetweenModes) {
//         logTrace "Waiting for delay between heating/cooling modes (${(delayBetweenModes - timeSinceLastChange)/1000/60} minutes remaining)"
//         return "auto" // if not ready to switch modes, set to auto
//     }

//     // Update timestamp only when changing between active modes
//     if (isActiveMode(newMode, highAmplitude, lowAmplitude) || isActiveMode(state.lastMode, highAmplitude, lowAmplitude)) {
//         state.lastModeChangeTime = currentTime
//     }
  
//     state.lastMode = newMode

//     logWarn "changeMode returns $newMode"
//     return newMode
// }
def changeMode(String newMode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime) {
    // Only apply delay and timestamp updates when dealing with actual mode changes between heat/cool
    def isNewModeActive = isActiveMode(newMode, highAmplitude, lowAmplitude)
    def wasLastModeActive = isActiveMode(state.lastMode, highAmplitude, lowAmplitude)
    
    // Log the transition we're evaluating
    logTrace "Mode transition: ${state.lastMode} -> ${newMode} (isNewModeActive: ${isNewModeActive}, wasLastModeActive: ${wasLastModeActive})"
    
    // Only enforce delay when switching between active modes
    if (isNewModeActive && wasLastModeActive && 
        newMode != state.lastMode && 
        timeSinceLastChange <= delayBetweenModes) {
            
        logTrace "Waiting for delay between heating/cooling modes (${(delayBetweenModes - timeSinceLastChange)/1000/60} minutes remaining)"
        return "auto"
    }

    // Only update timestamp when switching TO or FROM an active mode (heat/cool)
    if ((isNewModeActive || wasLastModeActive) && newMode != state.lastMode) {
        logDebug "Updating lastModeChangeTime due to switch ${state.lastMode} -> ${newMode}"
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
def getFanMode() {
    // Returns current fan mode from thermostat(s)
    def allThermostats = getAllThermostats()
    return allThermostats[0].currentValue("thermostatFanMode")
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
        state.lastFuncSensorsLog = state.lastFuncSensorsLog ? state.lastFuncSensorsLog : now()
        if (now() - state.lastFuncSensorsLog > 60 * 1000){
            state.lastFuncSensorsLog = now() 
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
                        def m = "${app.label}: ${device.displayName} appears UNRESPONSIVE -- <a href='${state.locationUrl}/${device.id}' target='_blank'>Manage Device</a>"
                        logWarn formatText(m, "black", "#E0E0E0")
                    } else {
                        logWarn "<b style='color:red; font-weight:900;'>Device not found for sensor name: <a href='${state.locationUrl}/${device.id}' target='_blank'>${sensorData.displayName}</a></b>"
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
            def m = "${app.label}: ${sensor.displayName} appears UNRESPONSIVE -- <a href='${state.locationUrl}/${sensor.id}' target='_blank'>Manage Device</a>"
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
        log.debug "calculating inside temp from tempSensors: <br><ul><li>${tempSensors.join("<li>")} </ul>"
        tempSensors.each { sensor ->
            if (sensor.currentTemperature != null) {
                temps << sensor.currentTemperature
            }
        }
    }
    
    // If no valid temperatures from sensors or no sensors configured,
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
private void logError(String message) {
    log.error formatText("ERROR: $message", "black", "red")
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


private void logConditions(String source="", Map params = [:]) {
    logTrace """
    <div style='border:1px solid gray;'>
        <br><b><u>Comfort ${source} Analysis:</u></b>
        <ol>
            ${params.collect { key, value -> 
                if(value instanceof Map){
                "key: $key values: $value"
                """
                    <li style='border: 1px dashed grey; color: green; margin-right:50%;'><u>${key.replaceAll(/([A-Z])/, ' $1').capitalize()}:</u>
                        <ul>
                        ${
                            value.collect { k, v ->                         
                                "<li><b>${k.replaceAll(/([A-Z])/, ' $1').capitalize()}:</b> ${v}</li>"
                                }.join('\n')                        
                        }
                        </ul>
                    </li>
                """
                }
                else {
                    "<li><b>${key.replaceAll(/([A-Z])/, ' $1').capitalize()}:</b> ${value}</li>"
                }
            }.join('\n')}
        </ol>
        <ul>
            ${tempSensors?.collect { sensor -> 
                "<li><a href='${state.locationUrl}${sensor.deviceId}' target='_blank'>${sensor.displayName}: ${sensor.currentValue('temperature')}F</a></li>"
                }.join('\n')
            }
        </ul>        
    </div>
    """   
}

def getTargetTemp(Boolean boostAllowed = false, String need = null) {
    // If it's a boost command, use boost temperatures
    if (boostAllowed && need) {
         if (!(need in ["heat", "cool"])) {
            log.warn "Invalid need for boost mode: ${need}. Defaulting to 74°."
            return 74
        }
        return need == "heat" ? tempBoostHeat : tempBoostCool
    }
    
    // Get the virtual thermostat's setpoint
    def candidateTemp = thermostatController?.currentValue("thermostatSetpoint") ?: 74
    
    // Validate temperature
    try {
        // Convert to numeric and ensure it's a valid temperature
        def temp = new BigDecimal(candidateTemp).setScale(1, BigDecimal.ROUND_HALF_UP)
        
        // Check for extremely out-of-bounds temperatures
        if (temp < 40 || temp > 90) {
            log.warn formatText("Extremely out-of-bounds temperature detected: ${temp}°. Defaulting to 74°.", "white", "red")
            return 74
        }
        
        return temp
    } catch (Exception e) {
        log.error "Error processing temperature: ${candidateTemp}. Error: ${e.message}"
        return 74 // safe default
    }
}

def restriction(motionActiveEvent=false){
    if(location.mode in restricted){
        logTrace "Currently in restricted mode: $location.mode"
        return [
        data: [
                set_to_off : false,
                off_mode : null,
                restricted_mode : true
            ]
        ]
    }
    def off_mode = fan_only ? "fan_only" : set_to_auto_instead_of_off ? "auto" : "off"

    // avoid calling motionIsActive() if it's already executed from the motion events handler.  
    def motion = motionActiveEvent ? true : motionIsActive()
    if(!motion){
        log.warn "No motion. get_need shall return $off_mode"
        return [
        data: [
                set_to_off : true,
                off_mode : off_mode,
                restricted_mode : false
            ]
        ]
    }

    return [
        data: [
                set_to_off : false,
                off_mode : off_mode,
                restricted_mode : false
            ]
        ]
}

/* *********************************** INTELLIGENCE *********************************** */


/** 
 * Last Updated: 2025-01-14
 */

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

    /** 
    * Last Updated: 2025-01-14
    */

    def restrict = restriction(motionActiveEvent)
    if(restrict.data.restricted_mode) return
    def off_mode = restrict.data.off_mode    
    if(restrict.data.set_to_off) return off_mode 

    if (!useAI) return fallback_need_eval(motionActiveEvent=motionActiveEvent, logs=true)
    

    try {
        // get a first raw eval by legacy algorithm:
        def legacy_need = fallback_need_eval(motionActiveEvent=motionActiveEvent, logs=false)

        // Proceed to intelligent evaluation attempts
        def result = null
        def currentTime = now()
        def timeSinceLastMode = currentTime - (state.lastModeChangeTime ?: 0)
        def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds
        def coolDiff = location.mode in powerSavingModes || !motion ? 5.0 : swing
        def heatDiff = location.mode in powerSavingModes || !motion ? -5.0 : -swing
        // Determine if temperature deviation is severe (+ 2 degrees beyond normal threshold)
        def amplitudeThreshold = 5
        def highAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff >= (coolDiff + amplitudeThreshold)
        def lowAmplitude = location.mode in powerSavingModes ? false : indoorToSetpointDiff <= (heatDiff - amplitudeThreshold)

        def validation = validateComfortCapabilities()
        
        if (!validation.isRequired) {
            log.warn "Required capabilities missing, falling back to basic algorithm"
            return legacy_need // defined above by fallback_need_eval(motionActiveEvent) call
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

        try {
            // Track environmental patterns
            trackEnvironmentalFactors()
        } catch (Exception e){
            logError "get_need / call trackEnvironmentalFactors() => $e"
        }

        // Calculate comfort zone based on temperature and humidity
        def comfortRange = calculateComfortRange(humidity)
        def targetTemp = getTargetTemp()
        def outdoorTempCoolingThreshold = getOutdoorTempCoolingThreshold(currentOutdoorTemp)
        def outdoorTempHeatingThreshold = getOutdoorTempHeatingThreshold(currentOutdoorTemp)
        // get prediction of successfull natural heating/cooling
        def prediction = getPredictedPerformance(targetTemp, currentIndoorTemp, currentOutdoorTemp)

        // Determine if natural temperature management is possible
        def naturalCoolingPotential = assessNaturalCoolingPotential(
            prediction,
            targetTemp,
            currentIndoorTemp,
            currentOutdoorTemp,
            outdoorTempCoolingThreshold,
            totalHeatLoad,
            humidity
        )
        
        def naturalHeatingPotential = assessNaturalHeatingPotential(
            prediction,
            targetTemp,
            currentIndoorTemp,
            currentOutdoorTemp,
            outdoorTempHeatingThreshold,
            totalHeatLoad,
            humidity
        )

        conditions = [
            need:"<b style='color:red;'>ERROR</b>",
            legacyNeed: legacy_need,
            targetTemp: targetTemp, 
            swing: swing, 
            indoorTemp: currentIndoorTemp, 
            outdoorTemp: currentOutdoorTemp,
            occupancyHeatLoad: occupancyHeat.heatOutput,
            solarGain: solarGain.solarGainWatts,
            totalHeatLoad: totalHeatLoad,
            comfortRangeUpper: comfortRange.upperBound,
            comfortRangeLower: comfortRange.lowerBound,
            OutdoorTempTooLowForAC: outdoorTempCoolingThreshold,
            OutdoorTempTooHighForHeat: outdoorTempHeatingThreshold,
            coolingPotentialFeasible: naturalCoolingPotential.feasible,
            coolingPotentialConfidence: naturalCoolingPotential.confidence,
            heatingPotentialFeasible: naturalHeatingPotential.feasible,
            heatingPotentialConfidence: naturalHeatingPotential.confidence,
            humidity: humidity,
            prediction: prediction
        ]
             
        /** 
        * Decision logic:
        *
        * Tracks the start of natural cooling/heating periods
        * Records complete events with actual duration and temperature changes
        * Finalizes events when:
        *   - Switching to mechanical HVAC
        *   - Reaching target temperature
        *   - Changing modes
        */ 
        if (currentIndoorTemp > (targetTemp + comfortRange.upperBound)) {
            if (naturalCoolingPotential.feasible) {
                if (!state.currentThermalEvent) {
                    // Start new cooling event
                    recordThermalEvent([
                        type: 'naturalCooling',
                        startTemp: currentIndoorTemp,
                        outdoorTemp: currentOutdoorTemp,
                        fanMode: getFanMode(),
                        humidity: humidity,
                        timeOfDay: new Date().format('HH:mm'),
                        isNewEvent: true
                    ])
                }
                result = fan_only ? "fan_only" : "off"
            } else {
                
                if(changeMode("cool", delayBetweenModes, timeSinceLastMode, highAmplitude, lowAmplitude, currentTime)){
                    // If switching to mechanical cooling, finalize any ongoing event
                    if (state.currentThermalEvent) {
                        recordThermalEvent([
                            endTemp: currentIndoorTemp,
                            isNewEvent: false
                        ])
                    }
                    result = "cool"
                }
                else {
                    result = off_mode
                }
            }
        } else if (currentIndoorTemp < (targetTemp - comfortRange.lowerBound)) {
            if (naturalHeatingPotential.feasible) {
                if (!state.currentThermalEvent) {
                    // Start new heating event
                    recordThermalEvent([
                        type: 'naturalHeating',
                        startTemp: currentIndoorTemp,
                        outdoorTemp: currentOutdoorTemp,
                        fanMode: getFanMode(),
                        humidity: humidity,
                        timeOfDay: new Date().format('HH:mm'),
                        isNewEvent: true
                    ])
                }
                result = fan_only ? "fan_only" : "off"
            } else {
                
                if(changeMode("heat", delayBetweenModes, timeSinceLastMode, highAmplitude, lowAmplitude, currentTime)){
                    // If switching to mechanical heating, finalize any ongoing event
                    if (state.currentThermalEvent) {
                        recordThermalEvent([
                            endTemp: currentIndoorTemp,
                            isNewEvent: false
                        ])
                    }
                    result = "heat"
                }
                else {
                    result = off_mode
                }
            }
        }
    } catch (Exception e){
        logError "Exception in get_need() => $e (app is FALLING BACK TO LEGACY ALGORITHM)"
        return fallback_need_eval(motionActiveEvent=motionActiveEvent, logs=true)
    }

    result = result ? result : fan_only ? "fan_only" : "off"
    def colorMap = [cool:"blue", heat: "red", fan_only: "green", off: "brown"]
    def color = colorMap."${result}"
    log.debug "color: $color"
    conditions["need"] = color ? "<b style='color:${color};'>${result}</b>" : "<b style='color:red;'>ERROR. data: result value: ${result}. color:${color}. Map: ${colorMap}</b>"
    logConditions(source="Intelligence", conditions)
    return result
    
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
/** 
 * Last Updated: 2025-01-14
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
/** 
 * Last Updated: 2025-01-14
 */

    
    if (!motionSensors) return [heatOutput: 0, confidence: 0]
    
    def now = new Date()
    def interval = 5 // minutes to analyze
    def startTime = new Date(now.time - (interval * 60 * 1000))
    
    def eventCounts = [:]
    def WATTS_PER_PERSON = 200
    def EVENTS_PER_PERSON = 3
    
    motionSensors.each { sensor ->
        def events = sensor.eventsSince(startTime)?.findAll { it.name == "motion" && it.value == "active" }
        if (events) {
            eventCounts[sensor.deviceId] = events.size()
        }
    }
    
    if (eventCounts.isEmpty()) return [heatOutput: 0, confidence: 1.0]
    
    // Calculate events per minute using safeAverage
    def totalEvents = eventCounts.values().sum() ?: 0
    def eventsPerMinute = totalEvents / interval
    
    // Estimate number of people
    def estimatedPeople = Math.round(eventsPerMinute / EVENTS_PER_PERSON)
    def totalHeatOutput = estimatedPeople * WATTS_PER_PERSON
    
    // Calculate confidence
    def confidence = 0.8
    if (eventsPerMinute < 1) confidence *= 0.5
    
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
    /** 
    * Last Updated: 2025-01-14
    */

    def comfCapTiers = comfortCapabilitiesTiers()
    def allSensors = (tempSensors ?: []) + (motionSensors ?: [])
    def result = [
        solarGainWatts: 0,
        confidence: 0.0,
        hasData: false
    ]
    
    try {
        // Find devices with required capabilities
        def illuminanceSensors = allSensors.findAll { it.hasCapability("illuminanceMeasurement") }
        def uvSensors = allSensors.findAll { it.hasCapability("ultravioletIndex") }
        
        if (!illuminanceSensors && !uvSensors) return result
        
        // Calculate solar gain
        def avgIlluminance = 0
        def avgUV = 0
        
        if (illuminanceSensors) {
            avgIlluminance = safeAverage(illuminanceSensors.collect { 
                it.currentValue("illuminance") ?: 0 
            })
            result.hasData = true
        }
        
        if (uvSensors) {
            avgUV = safeAverage(uvSensors.collect { 
                it.currentValue("ultravioletIndex") ?: 0 
            })
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
    }
    catch (Exception e){
        logError "analyzeSolarGain() => $e"
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
    /** 
    * Last Updated: 2025-01-14
    */

    log.warn "Recording thermal event with params: $params"
    
    def now = now()
    
    if (params.isNewEvent) {
        // Start new thermal event
        state.currentThermalEvent = [
            type: params.type,
            startTemp: params.startTemp,
            startTime: now,
            outdoorTemp: params.outdoorTemp,
            fanMode: params.fanMode,
            humidity: params.humidity,
            timeOfDay: params.timeOfDay
        ]
        logDebug "Started new thermal event: ${state.currentThermalEvent}"
        return
    }
    
    // If we get here, we're finalizing an event
    if (!state.currentThermalEvent) {
        logDebug "No thermal event to finalize"
        return
    }

    def duration = (now - state.currentThermalEvent.startTime) / 1000 // Convert to seconds
    
    def event = [
        timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
        startTemp: state.currentThermalEvent.startTemp,
        endTemp: params.endTemp,
        outdoorTemp: state.currentThermalEvent.outdoorTemp,
        duration: duration,
        coolingRate: state.currentThermalEvent.startTemp == params.endTemp ? 0 : 
            (state.currentThermalEvent.startTemp - params.endTemp) / (duration / 3600), // degrees per hour
        conditions: [
            fanMode: state.currentThermalEvent.fanMode,
            humidity: state.currentThermalEvent.humidity,
            timeOfDay: state.currentThermalEvent.timeOfDay,
            season: getSeason()
        ]
    ]
    
    // Determine the delta bracket
    def tempDelta = Math.abs(state.currentThermalEvent.startTemp - state.currentThermalEvent.outdoorTemp)
    def deltaBracket = "${Math.floor(tempDelta/5)*5}-${Math.floor(tempDelta/5)*5 + 5}"
    
    // Store event
    def category = state.currentThermalEvent.type
    state.thermalBehavior[category].events << event
    
    // Keep only last 30 events
    if (state.thermalBehavior[category].events.size() > 30) {
        state.thermalBehavior[category].events = state.thermalBehavior[category].events[-30..-1]
    }
    
    updatePerformanceMetrics(category, deltaBracket, event)
    
    // Clear current event
    state.currentThermalEvent = null
    
    logDebug "Finalized thermal event: $event"
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
/** 
 * Last Updated: 2025-01-14
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
    /** 
    * Last Updated: 2025-01-14
    */

    def tempDelta = Math.abs(currentTemp - outdoorTemp)
    def deltaBracket = "${Math.floor(tempDelta/5)*5}-${Math.floor(tempDelta/5)*5 + 5}"
    def category = currentTemp > targetTemp ? 'naturalCooling' : 'naturalHeating'
    
    def metrics = state.thermalBehavior[category].performanceByDelta[deltaBracket]
    if (!metrics || metrics.sampleSize < 3) {
        log.info formatText("Not enough data yet for performance prediction.", "black", "lightgray")
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
def assessNaturalCoolingPotential(prediction, targetTemp, currentTemp, outdoorTemp, outdoorTempCoolingThreshold, heatLoad, humidity) {
    log.debug "assessing Natural Cooling Potential..."

    // Check if we recently switched modes
    def currentTime = now()
    def timeSinceLastMode = currentTime - (state.lastModeChangeTime ?: 0)
    def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds

    // Get current event duration if one exists
    def currentEvent = state.currentThermalEvent
    // def MAX_NATURAL_ATTEMPT_DURATION = 30 * 60 * 1000 // 30 minutes
    def MAX_NATURAL_ATTEMPT_DURATION = calculateMaxNaturalAttemptDuration(currentTemp, targetTemp)

    log.debug "<b>currentEvent: $currentEvent</b>"
    // If we've been trying natural cooling for too long without success
    if (currentEvent){
        if (currentEvent?.type == 'naturalCooling') {
            def duration = now() - currentEvent.startTime
            if (duration > MAX_NATURAL_ATTEMPT_DURATION) {
                logTrace "Natural cooling attempt exceeded maximum duration"
                // Record the failed attempt
                recordThermalEvent([
                    endTemp: currentIndoorTemp,
                    isNewEvent: false
                ])
                return [feasible: false, confidence: 0.9]
            }
            // Add this logging block
            def remainingTime = (MAX_NATURAL_ATTEMPT_DURATION - duration) / (60 * 1000) // Convert to minutes
            def elapsedTime = duration / (60 * 1000)
            logTrace "Natural ${currentEvent.type} time check: ${new BigDecimal(elapsedTime).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes elapsed, ${new BigDecimal(remainingTime).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes remaining until timeout (max: ${new BigDecimal(MAX_NATURAL_ATTEMPT_DURATION/(60*1000)).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes)"
        }
    }
    
    // If we recently changed modes, prefer natural methods
    if (timeSinceLastMode < delayBetweenModes) {
        logTrace "Recent mode change (${timeSinceLastMode/1000/60} minutes ago). Preferring natural cooling."
        return [feasible: true, confidence: 0.7]
    }

    log.debug "<b>currentEvent: $currentEvent</b>"
    log.debug "assessing Natural Cooling Potential..."
    
    // Use historical performance data to determine feasibility
    if (prediction.predictedSuccess > 0.7 && prediction.confidence > 0.5) {
        recordThermalEvent([
            type: 'naturalCooling',
            startTemp: currentTemp,
            endTemp: currentTemp,
            outdoorTemp: outdoorTemp,
            duration: 0,
            fanMode: getFanMode(),
            humidity: humidity,
            timeOfDay: new Date().format('HH:mm')
        ])
        
        return [feasible: true, confidence: prediction.confidence]
    }

    // Don't allow cooling if outdoor temp is too low (dynamic threshold based on history)
    if (outdoorTempCoolingThreshold.status == true) {
        logTrace "Outdoor temperature ${outdoorTemp}°F is below minimum ${outdoorTempCoolingThreshold.temperature}°F for cooling"
        return [feasible: true, confidence: 0.9] // High confidence in natural cooling
    }
    
    return [feasible: false, confidence: prediction.confidence]
}
def assessNaturalHeatingPotential(prediction, targetTemp, currentTemp, outdoorTemp, outdoorTempHeatingThreshold, heatLoad, humidity) {
    log.debug "assessing Natural Heating Potential..."
    // Check if we recently switched modes
    def currentTime = now()
    def timeSinceLastMode = currentTime - (state.lastModeChangeTime ?: 0)
    def delayBetweenModes = 15 * 60 * 1000

    // Get current event duration if one exists
    def currentEvent = state.currentThermalEvent
    // def MAX_NATURAL_ATTEMPT_DURATION = 30 * 60 * 1000 // 30 minutes
    def MAX_NATURAL_ATTEMPT_DURATION = calculateMaxNaturalAttemptDuration(currentTemp, targetTemp)

    log.debug "<b>currentEvent: $currentEvent</b>"
    // If we've been trying natural heating for too long without success
    if (currentEvent){
        if (currentEvent?.type == 'naturalHeating') {
            def duration = now() - currentEvent.startTime
            if (duration > MAX_NATURAL_ATTEMPT_DURATION) {
                logTrace "Natural heating attempt exceeded maximum duration"
                // Record the failed attempt
                recordThermalEvent([
                    endTemp: currentIndoorTemp,
                    isNewEvent: false
                ])
                return [feasible: false, confidence: 0.9]
            }
            // Add this logging block
            def remainingTime = (MAX_NATURAL_ATTEMPT_DURATION - duration) / (60 * 1000) // Convert to minutes
            def elapsedTime = duration / (60 * 1000)
            logTrace "Natural ${currentEvent.type} time check: ${new BigDecimal(elapsedTime).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes elapsed, ${new BigDecimal(remainingTime).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes remaining until timeout (max: ${new BigDecimal(MAX_NATURAL_ATTEMPT_DURATION/(60*1000)).setScale(1, BigDecimal.ROUND_HALF_UP)} minutes)"
        }
    }
    
    // If we recently changed modes, prefer natural methods
    if (timeSinceLastMode < delayBetweenModes) {
        logTrace "Recent mode change (${timeSinceLastMode/1000/60} minutes ago). Preferring natural heating."
        return [feasible: true, confidence: 0.7]
    }

    log.debug "assessing Natural Heating Potential..."

    if (prediction.predictedSuccess > 0.7 && prediction.confidence > 0.5) {
        recordThermalEvent([
            type: 'naturalHeating',
            startTemp: currentTemp,
            endTemp: currentTemp,
            outdoorTemp: outdoorTemp,
            duration: 0,
            fanMode: getFanMode(),
            humidity: humidity,
            timeOfDay: new Date().format('HH:mm')
        ])
        
        return [feasible: true, confidence: prediction.confidence]
    }

    // Don't allow heating if outdoor temp is too high (dynamic threshold)
    if (outdoorTempHeatingThreshold.status == true) {
        logTrace "Outdoor temperature ${outdoorTemp}°F is above maximum ${outdoorTempHeatingThreshold.temperature}°F for heating"
        return [feasible: true, confidence: 0.9] // High confidence in natural heating
    }
    
    return [feasible: false, confidence: prediction.confidence]
}
def calculateMaxNaturalAttemptDuration(currentTemp, targetTemp) {
    /**
    *    Small temp differences (1-2°) get ~15-20 minutes
    *    Medium differences (3-5°) get ~25-35 minutes
    *    Large differences (>5°) get up to but never exceed 45 minutes
    */
    try {
        def BASE_DURATION = 20 * 60 * 1000L  // 20 minutes base
        def MAX_DURATION = 45 * 60 * 1000L   // 45 minutes max
        def MIN_DURATION = 15 * 60 * 1000L   // 15 minutes min
        
        // Convert temps to BigDecimal for precise math
        def tempDiff = Math.abs(new BigDecimal(currentTemp.toString()) - new BigDecimal(targetTemp.toString()))
        
        
        // Using natural log to create diminishing returns
        // Add 1 to tempDiff to avoid log(0)
        // Math.log returns double, convert duration to Long for milliseconds
        def adjustedDuration = (BASE_DURATION * Math.log(tempDiff.doubleValue() + 1)).longValue()
        
        
        // Constrain between MIN and MAX
        def result = Math.max(MIN_DURATION, Math.min(adjustedDuration, MAX_DURATION))
        logTrace "calculateMaxNaturalAttemptDuration => ${(result / 60 / 1000)} minutes"
        return result
    } catch (Exception e){
        logError "Exception in calculateMaxNaturalAttemptDuration() => $e"
        return MAX_DURATION
    }
}
def getOutdoorTempCoolingThreshold(outdoorTemp) {
    def seasonalPatterns = state.thermalBehavior?.environmentalFactors?.seasonalPatterns
    def currentSeason = getSeason()
    def result = null
    def value = null
    def evalMethod = ""

    log.info "seasonalPatterns: $seasonalPatterns"
    
    // If we have learned patterns, use them
    if (seasonalPatterns && seasonalPatterns[currentSeason]?.avgTemp) {
        value = Math.max(40.0, seasonalPatterns[currentSeason].avgTemp - 15.0)
        evalMethod = "Learned Patterns (narrow A.I.)"
    }
    else {    
        evalMethod = "season defaults"
        // Otherwise use season-based defaults
        switch(currentSeason) 
        {
            case "winter":
                value = 50.0
                break
            case "summer":
                value =  50.0
                break
            case "spring":
                value = 50.0
                break     
            case "fall":
                value =  50.0
                break
            default:
                value = 50.0
                break
        }
    }

    if (!value) {
        logError "Error in seasonal pattern evaluation (getOutdoorTempCoolingThreshold method)"
    }
    result = [temperature: value, status: (outdoorTemp < value)]
    log.debug "eval from seasonal patterns: $result (getOutdoorTempHeatingThreshold) | <b>method: $evalMethod</b>"
    return result
}

def getOutdoorTempHeatingThreshold(outdoorTemp) {
    def seasonalPatterns = state.thermalBehavior?.environmentalFactors?.seasonalPatterns
    def currentSeason = getSeason()
    def result = null
    def value = null
    def evalMethod = ""
    
    // If we have learned patterns, use them
    if (seasonalPatterns && seasonalPatterns[currentSeason]?.avgTemp) {
        value = Math.min(75.0, seasonalPatterns[currentSeason].avgTemp + 15.0)
        evalMethod = "Learned Patterns (narrow A.I.)"
    } else {
        evalMethod = "season defaults"
        // Otherwise use season-based defaults
        switch(currentSeason) 
        {
            case "winter":
                value = 70.0
                break
            case "summer":
                value =  72.0
                break
            case "spring":
                value = 72.0
                break     
            case "fall":
                value =  72.0
                break
            default:
                value = 72.0
                break
        }
    }

    if (!value) {
        logError "Error in seasonal pattern evaluation (getOutdoorTempHeatingThreshold method)"
    }
    result = [temperature: value, status: (outdoorTemp > value)]
    log.debug "eval from seasonal patterns: $result (getOutdoorTempHeatingThreshold) | <b>method: $evalMethod</b>"
    return result
}
def trackEnvironmentalFactors_old() {
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
    /** 
    * Last Updated: 2025-01-14
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
def trackEnvironmentalFactors() {
    log.debug "Starting trackEnvironmentalFactors()"
    def now = new Date()
    def hour = now.format("HH:mm")
    def season = getSeason()
    def currentTemp
    def previousTemp
    def tempChange
    
    log.debug "Current hour: $hour, season: $season"

    try {
        log.debug "state.thermalBehavior: $state.thermalBehavior"
        if(!state.thermalBehavior) {
            log.trace formatText("state.thermalBehavior is empty", "white", "grey")
            // Initialize structure if needed
            initializeStateThermalBehavior()
        }
                
        log.debug "Current environmental factors state: ${state.thermalBehavior.environmentalFactors}"
        
        // Track natural temperature changes
        try {
            currentTemp = (get_indoor_temperature() ?: 0) as BigDecimal
            previousTemp = (state.lastIndoorTemp     ?: 0) as BigDecimal
            tempChange = currentTemp - previousTemp
            state.lastIndoorTemp = currentTemp
            
            log.debug "Temperature tracking - Current: $currentTemp, Previous: $previousTemp, Change: $tempChange"
            
            // Solar impact tracking
            def solarGain = analyzeSolarGain()
            log.debug "Solar gain analysis: $solarGain"
            
            if (solarGain.hasData) {
                def timeBlock = "00:00-00:00"
                try {
                    timeBlock = getTimeBlock(hour)
                } catch (Exception e) {
                    logError "trackEnvironmentalFactors / getTimeBlock() / natural temperature changes => $e"
                }

                state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock] = state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock] ?: [:]
                
                log.debug "state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock]: ${state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock]}"

                updateRollingAverage(
                    state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock],
                    [
                        solarGainWatts: solarGain.solarGainWatts,
                        tempImpact: tempChange,
                        season: season
                    ]
                )
                
                log.debug "Updated solar gain patterns for $timeBlock: ${state.thermalBehavior.environmentalFactors.solarGainPatterns[timeBlock]}"
            }
        } catch (Exception e) {
            logError "trackEnvironmentalFactors() / natural temperature changes => $e"
        }
        
        // Occupancy patterns
        try {
            def occupancyHeat = analyzeOccupancyHeat()
            log.debug "Occupancy heat analysis: $occupancyHeat"
        
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
                log.debug "Updated occupancy patterns for $timeBlock: ${state.thermalBehavior.environmentalFactors.occupancyPatterns[timeBlock]}"
            }
        } catch (Exception e) {
            logError "trackEnvironmentalFactors() / Occupancy patterns => $e"
        }
        
        // Seasonal adjustments
        try {
            log.debug "<b>season: $season</b>"
            log.debug "<b>currentTemp: $currentTemp</b>"
            log.debug "<b>tempChange: $tempChange</b>"
            updateSeasonalPatterns(season, currentTemp, tempChange)
        } catch (Exception e) {
            logError "trackEnvironmentalFactors() / Seasonal adjustments => $e"
        }
        log.debug "Final environmental factors state: ${state.thermalBehavior.environmentalFactors}"

    } catch (Exception e) {
        logError "trackEnvironmentalFactors() => $e"
    }
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
/** 
 * Last Updated: 2025-01-14
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
/** 
 * Last Updated: 2025-01-14
 */

    def month = new Date().format("MM").toInteger()
    log.debug "current month: $month"
    switch(month) {
        case [12, 1, 2]:  // winter months explicitly listed
            return "winter"
        case 3..5:        // spring months
            return "spring"
        case 6..8:        // summer months
            return "summer" 
        case 9..11:       // fall months
            return "fall"
        default:          // should never happen but good practice
            return "unknown"
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
    /** 
    * Last Updated: 2025-01-14
    */

    log.debug "season: $season"
    log.debug "temp: $temp"
    log.debug "tempChange: $tempChange"

    // TODO: store these patterns to a file.

    try {
        def seasonalPatterns = state.thermalBehavior.environmentalFactors.seasonalPatterns
        seasonalPatterns[season] = seasonalPatterns[season] ?: [
            avgTemp: temp,
            tempRange: [min: temp, max: temp],
            typicalChanges: []
        ]
        
        def pattern = seasonalPatterns[season]
        // Weighted rolling average
        pattern.avgTemp = (((pattern.avgTemp ?: 0) as BigDecimal) * 9 + (temp as BigDecimal)) / 10
        // Convert pattern.tempRange.min to a BigDecimal or use `temp` if null:
        pattern.tempRange.min = ((pattern.tempRange.min == null) 
                                ? (temp as BigDecimal) 
                                : (pattern.tempRange.min as BigDecimal))
                            .min(temp as BigDecimal)

        pattern.tempRange.max = ((pattern.tempRange.max == null) 
                                ? (temp as BigDecimal) 
                                : (pattern.tempRange.max as BigDecimal))
                            .max(temp as BigDecimal)
        
        // Keep last 100 temperature changes
        // If object is less than 500, ensure we never try to slice from an out-of-range negative index.
        def combinedList = pattern.typicalChanges + [tempChange]
        if (combinedList.size() > 500) {
            // Keep only the last 500 elements
            def startIndex = combinedList.size() - 500
            pattern.typicalChanges = combinedList[startIndex..-1]
        } else {
            // Otherwise just use the full list
            pattern.typicalChanges = combinedList
        }
    } catch (Exception e) {
        logError "updateSeasonalPatterns() => $e"
        return [season, temp, tempChange]
    }
}
def calculateComfortRange(humidity) {
    /**
    * Determines comfort temperature range based on humidity
    * @param humidity Current relative humidity percentage
    * @return Map with upperBound and lowerBound temperature adjustments
    */
    
    // Convert inputs to BigDecimal for precise calculation
    def swingValue = new BigDecimal(swing.toString())
    def humidityValue = new BigDecimal(humidity.toString())
    def baselineHumidity = new BigDecimal("45")
    def humidityFactor = new BigDecimal("0.05")
    
    // Calculate humidity-based adjustment
    def adjustment = (humidityValue - baselineHumidity) * humidityFactor
    
    // Calculate temperature bounds with adjustment
    def upperBound = swingValue - adjustment
    def lowerBound = swingValue + adjustment
    
    return [
        upperBound: upperBound.setScale(3, BigDecimal.ROUND_HALF_UP),
        lowerBound: lowerBound.setScale(3, BigDecimal.ROUND_HALF_UP)
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
/** 
 * Last Updated: 2025-01-14
 */

    def humidityCapableDevices = (tempSensors ?: []) + 
                                (thermostats ?: []) + 
                                (motionSensors ?: []).findAll { 
        it.hasCapability("relativeHumidityMeasurement")
    }
    
    def readings = humidityCapableDevices.collect {
        it.currentValue("humidity")
    }.findAll { it != null }

    logDebug "humidity readings: $readings"
    
    return readings ? safeAverage(readings) : 45 // Default to 45% if no readings
}
def fallback_need_eval(motionActiveEvent=false, logs=true){

    def restrict = restriction(motionActiveEvent)
    if(restrict.data.restricted_mode) return
    def off_mode = restrict.data.off_mode    
    if(restrict.data.set_to_off) return off_mode 

    def results = []
    def allThermostats = getAllThermostats()
    

    if (!thermostatController) {
        formatText("NO thermostatController SET! Update settings.", "white", "red")
        return 
    }

    def targetTemp = getTargetTemp()        
    def currentIndoorTemp = get_indoor_temperature() as BigDecimal
    def currentOutdoorTemp =  outsideTemp.currentValue("temperature") as BigDecimal
    def delayBetweenModes = 15 * 60 * 1000 // 15 minutes in milliseconds
    def currentTime = now()
    
    state.lastModeChangeTime = state.lastModeChangeTime == null ? currentTime : state.lastModeChangeTime
    state.lastMode = state.lastMode == null ? "initial" : state.lastMode

    def timeSinceLastChange = currentTime - (state.lastModeChangeTime ?: 0)

    // Temperature differential thresholds
    def coolDiff = location.mode in powerSavingModes || !motion ? 5.0 : swing
    def heatDiff = location.mode in powerSavingModes || !motion ? -5.0 : -swing
    
    if (logs) logDebug "currentIndoorTemp: $currentIndoorTemp "
    if (logs) logDebug "targetTemp: $targetTemp "

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
    
    if (logs) {
        logConditions(source="Legacy", params=
            [
                target: targetTemp, 
                swing: swing, 
                indoorTemp: currentIndoorTemp, 
                indoorTempClass: currentIndoorTemp.class,
                outdoorTemp: currentOutdoorTemp, 
                indoorSetpointDifference: indoorToSetpointDiff,
                indoorOutdoorDifference: indoorToOutdoorDiff,
                minOutdoorTempForCooling: minOutdoorTempForCooling,
                minOutdoorTempForCoolingClass: minOutdoorTempForCooling.class,
                maxOutdoorTempForHeating: maxOutdoorTempForHeating,
                maxOutdoorTempForHeatingClass: maxOutdoorTempForHeating.class,
                tooColdOutside: tooColdOutside,
                tooWarmOutside: tooWarmOutside,
                highAmplitude: highAmplitude,
                lowAmplitude: lowAmplitude,
                lastMode: state.lastMode,
                timeSinceLastChange: "${timeSinceLastChange/1000/60} minutes"
            ]
        )
    }

    // Decision logic
    if (indoorToSetpointDiff > coolDiff) {
        if (!tooColdOutside && (currentOutdoorTemp >= currentIndoorTemp || highAmplitude)) {
            if (logs) logTrace "Indoor temperature too high, ${highAmplitude ? 'amplitude too high' : 'outdoor is warmer'} - mechanical cooling needed"
            results += changeMode("cool", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
            
        } else {
            if (logs) logTrace "Indoor temperature high but outdoor is cold enough - natural cooling possible"
            results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
            
        }
    } 
    else if (indoorToSetpointDiff < heatDiff) {
        
        if (!tooWarmOutside && (currentOutdoorTemp <= currentIndoorTemp || lowAmplitude)) {
            if (logs) logTrace "Indoor temperature too low, ${lowAmplitude ? 'amplitude too high' : 'outdoor is colder'} - heating needed"
            results += changeMode("heat", delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
            
        } else {
            if (logs) logTrace "Indoor temperature low but outdoor is warm enough - natural heating possible"
            results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
            
        }
    }
    else {
        if (logs) logTrace "No extreme conditions detected - setting to ${off_mode}"
        results += changeMode(off_mode, delayBetweenModes, timeSinceLastChange, highAmplitude, lowAmplitude, currentTime)
    }

    if (logs) logDebug "results: $results"
    if (results){
        def cools = results.findAll{it -> it == "cool"}
        def heats = results.findAll{it -> it == "heat"}

        if (logs) logDebug "cools.size(): ${cools.size()}"
        if (logs) logDebug "heats.size(): ${heats.size()}"

        if (cools.size() > heats.size()){
            if (logs) logDebug "cools win"
            final_result = "cool"
        }
        else if (heats.size() > cools.size()){
            if (logs) logDebug "heats win"
            final_result = "heat"
        }
        else {
            if (logs) logDebug "nor cool nor heat win..."
            final_result =  off_mode
        }
        
    }
    else {        
        if (logs) log.error "This line shouldn't appear. Mode will be set to auto as fallback"
        final_result =  "auto"
    }

    

    if (logs) logTrace "need returns: $final_result"
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
/** 
 * Last Updated: 2025-01-14
 */

    def season = getSeason()
    def patterns = state.thermalBehavior.environmentalFactors.seasonalPatterns[season]
    if (!patterns) return 0
    
    // Calculate seasonal adjustment based on learned patterns
    def avgTemp = patterns.avgTemp
    def tempRange = patterns.tempRange.max - patterns.tempRange.min
    def typicalChange = patterns.typicalChanges ? safeAverage(patterns.typicalChanges) : 0

    
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
    return """
        <style>
            #comfort-analytics * {
                margin: 0 !important;
                padding: 0 !important;
                text-indent: 0 !important;
                line-height: 1.3 !important;
            }
            
            #comfort-analytics {
                display: flex;
                flex-direction: column;
                gap: 24px;
                width: 100%;
                margin-top: 16px !important;
            }
            
            .analytics-section {
                background: white;
                border: 1px solid #dee2e6;
                border-radius: 8px;
                padding: 16px !important;
            }
            
            .section-header {
                font-size: 16px;
                color: #1a237e;
                font-weight: bold;
                margin-bottom: 16px !important;
                padding-bottom: 8px !important;
                border-bottom: 2px solid #e3f2fd;
            }
            
            .chart-container {
                height: 200px;
                display: flex;
                align-items: flex-end;
                gap: 2px;
                padding: 16px 0 !important;
                border-left: 2px solid #333;
                border-bottom: 2px solid #333;
            }
            
            .chart-bar {
                flex: 1;
                transition: height 0.3s ease;
                position: relative;
            }
            
            .chart-bar:hover::after {
                content: attr(data-tooltip);
                position: absolute;
                bottom: 100%;
                left: 50%;
                transform: translateX(-50%);
                background: rgba(0,0,0,0.8);
                color: white;
                padding: 4px 8px !important;
                border-radius: 4px;
                font-size: 12px;
                white-space: nowrap;
            }
            
            .patterns-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 16px;
                margin-top: 16px !important;
            }
            
            .pattern-card {
                background: #f8f9fa;
                border-radius: 4px;
                padding: 12px !important;
            }
            
            .pattern-title {
                font-weight: bold;
                color: #2c3e50;
                margin-bottom: 8px !important;
            }
            
            .pattern-value {
                font-size: 13px;
                color: #666;
            }
        </style>
        
        <div id="comfort-analytics">
            <div class="analytics-section">
                <div class="section-header">Temperature Management Success Rate</div>
                <div class="chart-container">
                    ${getSuccessRateChart()}
                </div>
            </div>
            
            <div class="analytics-section">
                <div class="section-header">Environmental Patterns</div>
                <div class="patterns-grid">
                    ${getEnvironmentalPatterns()}
                </div>
            </div>
        </div>
    """
}

def getComfortMetrics() {
    // Initialize metrics history if needed
    state.metricsHistory = state.metricsHistory ?: [
        coolingEfficiency: [],
        energySavings: [],
        comfortScore: []
    ]
    
    def naturalCooling = state.thermalBehavior.naturalCooling
    def naturalHeating = state.thermalBehavior.naturalHeating
    
    // Calculate current values
    def currentCoolingEfficiency = naturalCooling.events ? 
        safeAverage(naturalCooling.events.collect{it.coolingRate}) : 0.0
    def currentEnergySavings = calculateEnergySavings()
    def currentComfortScore = calculateComfortScore()
    
    // Update historical values (keep last 7 days of daily averages)
    def today = new Date().format('yyyy-MM-dd')
    
    // Update each metric's history
    ['coolingEfficiency', 'energySavings', 'comfortScore'].each { metric ->
        state.metricsHistory[metric] = state.metricsHistory[metric].findAll { entry ->
            // Keep entries from last 7 days
            def entryDate = Date.parse('yyyy-MM-dd', entry.date)
            def sevenDaysAgo = new Date() - 7
            entryDate.after(sevenDaysAgo)
        }
    }
    
    // Add today's values
    def currentValues = [
        coolingEfficiency: currentCoolingEfficiency,
        energySavings: currentEnergySavings,
        comfortScore: currentComfortScore
    ]
    
    currentValues.each { metric, value ->
        def todayEntry = state.metricsHistory[metric].find { it.date == today }
        if (todayEntry) {
            // Update today's running average
            todayEntry.value = safeAverage([todayEntry.value, value])
        } else {
            state.metricsHistory[metric] << [date: today, value: value]
        }
    }
    
    // Calculate trends using historical data
    def coolingEfficiencyTrend = calculateTrendIndicator(
        currentCoolingEfficiency,
        state.metricsHistory.coolingEfficiency.collect { it.value }
    )
    
    def energySavingsTrend = calculateTrendIndicator(
        currentEnergySavings,
        state.metricsHistory.energySavings.collect { it.value }
    )
    
    def comfortScoreTrend = calculateTrendIndicator(
        currentComfortScore,
        state.metricsHistory.comfortScore.collect { it.value }
    )
    
    // Return metrics with real trend data
    return [
        [
            name: "Natural Cooling Efficiency",
            value: "${currentCoolingEfficiency}°/hr",
            trend: "${coolingEfficiencyTrend.direction} ${coolingEfficiencyTrend.percentage}% this week"
        ],
        [
            name: "Energy Savings",
            value: "${currentEnergySavings.setScale(1, BigDecimal.ROUND_HALF_UP)}%",
            trend: "${energySavingsTrend.direction} ${energySavingsTrend.percentage}% vs. last week"
        ],
        [
            name: "Comfort Score",
            value: "${(new BigDecimal(currentComfortScore * 100).setScale(0, BigDecimal.ROUND_HALF_UP))}%",
            trend: "${comfortScoreTrend.direction} ${comfortScoreTrend.percentage}% improvement"
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
/** 
 * Last Updated: 2025-01-14
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
    /** 
    * Last Updated: 2025-01-14
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
        def targetTemp = getTargetTemp()
        def tempDeviations = events.collect { event ->
            def startDiff = (new BigDecimal(event.startTemp) - targetTemp).abs()
            def endDiff = (new BigDecimal(event.endTemp) - targetTemp).abs()
            return [startDiff, endDiff]
        }.flatten()
        
        def avgDeviation = tempDeviations.sum() / tempDeviations.size()
        def tempScore = new BigDecimal("1.0") - (avgDeviation / new BigDecimal("10.0"))
        tempScore = tempScore < new BigDecimal("0.0") ? new BigDecimal("0.0") : tempScore
        
        // Calculate response time score
        def avgDuration = safeAverage(events.collect { it.duration })
        def responseScore = new BigDecimal("1.0") - (avgDuration / (4 * 3600)) // Normalized to 4 hours
        responseScore = responseScore < new BigDecimal("0.0") ? new BigDecimal("0.0") : responseScore
        
        // Calculate consistency score
        def coolingRates = events.collect { new BigDecimal(it.coolingRate) }
        def avgRate = safeAverage(coolingRates)
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

// may be removed if new getSuccessRateChart is satisfactory. 
// waiting to see true historics appear first.
def getSuccessRateChart_old() {
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
    /** 
    * Last Updated: 2025-01-14
    */

    def bars = []
    try {
        // Get success rates for different temperature deltas
        def coolingRates = state.thermalBehavior.naturalCooling.performanceByDelta
        def heatingRates = state.thermalBehavior.naturalHeating.performanceByDelta
        
        // Combine and sort performance data
        def allRates = [:]
        coolingRates.each { delta, metrics ->
            allRates["C${delta}"] = new BigDecimal(metrics.successRate * 100)
        }
        heatingRates.each { delta, metrics ->
            allRates["H${delta}"] = new BigDecimal(metrics.successRate * 100)
        }
        
        // Generate bars for each rate
        allRates.sort().each { label, rate ->
            def height = rate ?: new BigDecimal(0)
            def color = label.startsWith('C') ? '#2196F3' : '#4CAF50'
            def title = label.startsWith('C') ? 'Cooling' : 'Heating'
            title += " ${label.substring(1)}°F"
            
            // Format the success rate with one decimal place
            def formattedRate = height.setScale(1, BigDecimal.ROUND_HALF_UP)
            
            bars << """
                <div class="chart-bar" 
                     style="height: ${height}%; background: ${color};"
                     title="${title}: ${formattedRate}% success">
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

def getSuccessRateChart() {
    /**
    * Generates HTML visualization of temperature management success rates with fallback states
    * 
    * Data Flow:
    * ┌──────────┐    ┌──────────┐    ┌────────────┐    ┌──────────┐
    * │Check Data│───>│Transform │───>│Handle Empty│───>│Generate  │
    * │Available │    │  to %    │    │   States   │    │HTML/CSS  │
    * └──────────┘    └──────────┘    └────────────┘    └──────────┘
    * 
    * Visualization States:
    * 1. No Data:
    *    - Shows placeholder bars
    *    - Displays "Collecting data" message
    *    - Semi-transparent visual indicators
    * 
    * 2. Valid Data:
    *    - Color-coded bars (blue=cooling, green=heating)
    *    - Success rates by temperature differential
    *    - Detailed tooltips with metrics
    * 
    * 3. Error State:
    *    - Friendly error message
    *    - Logs detailed error info
    * 
    * Bar Chart Structure:
    *     Success %
    * 100│   █
    *    │ █ █ █
    *  50│ █ █ █ █
    *    │ █ █ █ █ █
    *   0└─────────────
    *     C5 C10 H5 H10
    *     Temperature Δ
    * 
    * Features:
    * - Graceful handling of empty data states
    * - BigDecimal precision for success rates
    * - Dynamic tooltips with context
    * - Visual feedback during data collection
    * - Responsive layout
    * 
    * @return String HTML/CSS markup for visualization
    * 
    * @see getComfortVisualization() for complete UI
    * @see updatePerformanceMetrics() for data collection
    */
    def bars = []
    try {
        // Check if we have any performance data
        def coolingRates = state.thermalBehavior?.naturalCooling?.performanceByDelta ?: [:]
        def heatingRates = state.thermalBehavior?.naturalHeating?.performanceByDelta ?: [:]
        
        if (coolingRates.isEmpty() && heatingRates.isEmpty()) {
            // If no data, show placeholder bars with message
            return """
                <div style="text-align: center; color: #666; padding: 20px;">
                    <div style="margin-bottom: 10px;">Collecting performance data...</div>
                    <div style="display: flex; justify-content: space-around; height: 100px;">
                        <div class="chart-bar" style="width: 40px; height: 30%; background: #2196F3; opacity: 0.3;"
                             title="Cooling performance data will appear here">
                        </div>
                        <div class="chart-bar" style="width: 40px; height: 30%; background: #4CAF50; opacity: 0.3;"
                             title="Heating performance data will appear here">
                        </div>
                    </div>
                </div>
            """
        }
        
        // Combine and sort performance data
        def allRates = [:]
        
        log.debug "Initial coolingRates: $coolingRates"
        log.debug "Initial heatingRates: $heatingRates"
        
        // Add cooling rates with temperature differentials
        coolingRates.each { delta, metrics ->
            log.debug "Processing cooling delta: $delta, metrics: $metrics"
            if (metrics.successRate != null) {
                allRates["C${delta}"] = new BigDecimal(metrics.successRate * 100)
                log.debug "Added cooling rate C${delta}: ${allRates["C${delta}"]}"
            }
        }
        
        // Add heating rates with temperature differentials
        heatingRates.each { delta, metrics ->
            log.debug "Processing heating delta: $delta, metrics: $metrics"
            if (metrics.successRate != null) {
                allRates["H${delta}"] = new BigDecimal(metrics.successRate * 100)
                log.debug "Added heating rate H${delta}: ${allRates["H${delta}"]}"
            }
        }
        
        log.debug "Final allRates: $allRates"
        
        // If we still have no valid rates, show empty chart with message
        if (allRates.isEmpty()) {
            return """
                <div style="text-align: center; color: #666; padding: 20px;">
                    Waiting for first performance metrics...
                </div>
            """
        }
        
        // Generate bars for each rate
        allRates.sort().each { label, rate ->
            def height = rate.max(new BigDecimal(0)) // Ensure non-negative
            def color = label.startsWith('C') ? '#2196F3' : '#4CAF50'
            def type = label.startsWith('C') ? 'Cooling' : 'Heating'
            def delta = label.substring(1)
            def formattedRate = height.setScale(1, BigDecimal.ROUND_HALF_UP)
            
            // Convert to BigDecimal for consistent type handling
            def minHeight = new BigDecimal(5)
            def displayHeight = height == 0 ? minHeight : height
            
            def bar = """<div class="chart-bar" 
                 style="height: ${displayHeight}%; background: ${color}; opacity: ${height == 0 ? 0.3 : 1.0};"
                 title="${type} at ${delta} difference: ${formattedRate}% success rate">
            </div>"""
            bars << bar
            log.debug "Added bar for ${label}: ${bar}"
        }
        
    } catch (Exception e) {
        log.error "Error generating success rate chart: ${e.message}"
        return """
            <div style="text-align: center; color: #666; padding: 20px;">
                Error generating chart. Check logs for details.
            </div>
        """
    }
    log.debug "bars: $bars"
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
/** 
 * Last Updated: 2025-01-14
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
                    <div>Average: ${seasonalData.avgTemp ? seasonalData.avgTemp.setScale(1, BigDecimal.ROUND_HALF_UP) : 'N/A'}°F</div>
                    <div>Range: ${seasonalData.tempRange?.min ? seasonalData.tempRange.min.setScale(1, BigDecimal.ROUND_HALF_UP) : 'N/A'}°F - ${seasonalData.tempRange?.max ? seasonalData.tempRange.max.setScale(1, BigDecimal.ROUND_HALF_UP) : 'N/A'}°F</div>
                </div>
            """
        }
        
        // Add solar impact patterns
        def solarData = envFactors.solarGainPatterns
        if (solarData) {
            def avgGain = safeAverage(solarData.collect { timeBlock, data -> 
                data.solarGainWatts ?: 0 
            })
            
            patterns << """
                <div class="metric-card">
                    <h4>Solar Impact</h4>
                    <div>Average Gain: ${avgGain} watts</div>
                </div>
            """
        }
        
        // Add occupancy patterns
        def occupancyData = envFactors.occupancyPatterns
        if (occupancyData) {
            def avgHeat = safeAverage(occupancyData.collect { timeBlock, data ->
                data.heatOutput ?: 0
            })
            
            patterns << """
                <div class="metric-card">
                    <h4>Occupancy Impact</h4>
                    <div>Average Heat: ${avgHeat} watts</div>
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



def updateRollingAverage(map, newData) {
    // Ensure map exists
    map = map ?: [:]
    def n = (map.sampleSize ?: 0) as BigDecimal
    def newN = n + 1
    def newVal
    def oldVal
    try {
        // Update each value with rolling average
        newData.each { key, value ->
            try {
                if (value instanceof Number || (value instanceof String && value.isNumber())) {
                    newVal = value ? new BigDecimal(value.toString()) : BigDecimal.ZERO
                    oldVal = map[key] ? new BigDecimal(map[key].toString()) : BigDecimal.ZERO
                    map[key] = ((oldVal * n) + newVal) / newN
                } else {
                    // Non-numeric => just keep the raw string (e.g. "winter")
                    map[key] = value
                }
            }
            catch (Exception e) {
                logError "Error in updateRollingAverage for key $key: ${e.message}"
                // If something blows up parsing, just store the raw value
                map[key] = value
            }
        }
    } catch (Exception e){
        logError "updateRollingAverage() => $e"
    }
    
    map.sampleSize = newN
    return map
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
    /** 
    * Last Updated: 2025-01-14
    */

    try {
        def analytics = []
        def data = state.thermalBehavior
        
        // Create pattern analysis visualization
        analytics << """
            <style>
            .pattern-grid {
                display: grid;
                grid-template-columns: repeat(24, 1fr);
                gap: 2px;
                margin: 20px 0;
            }
            .pattern-cell {
                height: 30px;
                position: relative;
            }
            .cell-value {
                position: absolute;
                top: -20px;
                left: 50%;
                transform: translateX(-50%);
                font-size: 10px;
            }
            .success-high { background-color: #4CAF50; }
            .success-medium { background-color: #FFC107; }
            .success-low { background-color: #f44336; }
            </style>
            
            <div class="analytics-section">
                <h3>Natural Temperature Management Success (24-hour view)</h3>
                <div class="pattern-grid">
        """
        
        // Get hourly success rates from our environmental patterns
        for (int hour = 0; hour < 24; hour++) {
            def timeBlock = String.format("%02d:00", hour)
            def success = getSuccessRateForHour(hour)
            def colorClass = success >= 0.7 ? "success-high" : 
                           success >= 0.4 ? "success-medium" : "success-low"
            
            analytics << """
                <div class="pattern-cell ${colorClass}">
                    <span class="cell-value">${(success * 100).round()}%</span>
                </div>
            """
        }
        
        analytics << """
                </div>
                <div style="margin-top: 10px; font-size: 12px; color: #666;">
                    <span style="color: #4CAF50;">■</span> High Success (>70%)
                    <span style="color: #FFC107; margin-left: 10px;">■</span> Medium Success (40-70%)
                    <span style="color: #f44336; margin-left: 10px;">■</span> Low Success (<40%)
                </div>
            </div>
        """
        
        // Add temperature differential effectiveness chart
        analytics << """
            <div class="analytics-section" style="margin-top: 30px;">
                <h3>Temperature Differential Effectiveness</h3>
                <div style="display: flex; height: 200px; align-items: flex-end; gap: 10px;">
        """
        
        data.naturalCooling.performanceByDelta.each { delta, metrics ->
            def height = (metrics.successRate * 100).round()
            analytics << """
                <div style="flex: 1; background: #2196F3; height: ${height}%;">
                    <div style="transform: rotate(-90deg) translateY(20px); 
                         white-space: nowrap; font-size: 12px;">
                        ${delta}°F diff: ${height}%
                    </div>
                </div>
            """
        }
        
        analytics << """
                </div>
            </div>
        """
        
        // Add current learning status
        def learningStatus = getLearningProgress()
        analytics << """
            <div class="analytics-section" style="margin-top: 30px;">
                <h3>Learning Status</h3>
                <div style="background: #f5f5f5; padding: 15px; border-radius: 5px;">
                    <div style="margin-bottom: 10px;">Data Collection: ${learningStatus.dataPoints} points</div>
                    <div style="background: #ddd; height: 20px; border-radius: 10px; overflow: hidden;">
                        <div style="background: #2196F3; width: ${learningStatus.confidence}%; height: 100%;"></div>
                    </div>
                    <div style="font-size: 12px; color: #666; margin-top: 5px;">
                        System Confidence: ${learningStatus.confidence}%
                    </div>
                </div>
            </div>
        """
        
        return analytics.join("\n")
        
    } catch (Exception e) {
        log.error "Error generating detailed analytics: ${e}"
        return "<div>Error generating detailed analytics</div>"
    }
}

private def getSuccessRateForHour(hour) {
    // Get success rate from our patterns
    def timeBlock = String.format("%02d:00", hour)
    def patterns = state.thermalBehavior.environmentalFactors.dailyPatterns[timeBlock]
    return patterns?.successRate ?: 0.0
}

private def getLearningProgress() {
    def totalPossibleDataPoints = 24 * 7 // One week of hourly data points
    def actualDataPoints = state.thermalBehavior.environmentalFactors.dailyPatterns.size()
    def confidence = Math.min((actualDataPoints / totalPossibleDataPoints * 100).round(), 100)
    
    return [
        dataPoints: actualDataPoints,
        confidence: confidence
    ]
}
def getComfortKPIDashboard() {
    def metrics = getComfortMetrics()
    
    return """
        <style>
            #kpi-dashboard * {
                margin: 0 !important;
                padding: 0 !important;
                text-indent: 0 !important;
                line-height: 1.3 !important;
            }
            
            #kpi-dashboard {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 12px;
                width: 100%;
                margin-top: 16px !important;
            }
            
            .kpi-card {
                background: white;
                border: 1px solid #dee2e6;
                border-radius: 8px;
                padding: 16px !important;
                display: flex;
                flex-direction: column;
                gap: 8px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            }
            
            .kpi-header {
                font-size: 14px;
                color: #2c3e50;
                font-weight: bold;
                margin-bottom: 4px !important;
            }
            
            .kpi-value {
                font-size: 24px;
                font-weight: bold;
                color: #1a237e;
                margin: 8px 0 !important;
            }
            
            .kpi-trend {
                font-size: 13px;
                display: flex;
                align-items: center;
                gap: 4px;
            }
            
            .trend-up { color: #2e7d32; }
            .trend-down { color: #c62828; }
            .trend-neutral { color: #757575; }
            
            .kpi-context {
                font-size: 12px;
                color: #666;
                margin-top: 4px !important;
                font-style: italic;
            }
        </style>
        
        <div id="kpi-dashboard">
            ${metrics.collect { metric ->
                def trendClass = metric.trend.startsWith('↑') ? 'trend-up' : 
                               metric.trend.startsWith('↓') ? 'trend-down' : 
                               'trend-neutral'
                """
                <div class="kpi-card">
                    <div class="kpi-header">${metric.name}</div>
                    <div class="kpi-value">${metric.value}</div>
                    <div class="kpi-trend ${trendClass}">${metric.trend}</div>
                    ${getKPIContext(metric.name)}
                </div>
                """
            }.join('')}
        </div>
    """
}

private String getKPIContext(metricName) {
    def context = [
        "Natural Cooling Efficiency": "Average temperature change rate during natural cooling",
        "Energy Savings": "Reduction in HVAC usage compared to baseline",
        "Comfort Score": "Overall system effectiveness rating"
    ]
    
    return """<div class="kpi-context">${context[metricName]}</div>"""
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
/** 
 * Last Updated: 2025-01-14
 */

    try {
        if (!historicalValues || historicalValues.isEmpty() || historicalValues.every { it == 0 }) {
            logDebug "Insufficient historical data for trend calculation"
            return [direction: "→", percentage: "0"]
        }
        
        // Convert inputs to BigDecimal and handle zero cases
        def current = new BigDecimal(currentValue.toString())
        def avgHistorical = safeAverage(historicalValues)
        
        if (avgHistorical == 0) {
            logDebug "Historical average is zero, cannot calculate percentage change"
            return [direction: "→", percentage: "0"]
        }

        // Calculate percentage change
        def change = ((current - avgHistorical) / avgHistorical) * 100
        
        // Determine direction
        def direction = "→"
        if (change > 0) {
            direction = "↑"
        } else if (change < 0) {
            direction = "↓"
        }
        
        return [
            direction: direction,
            percentage: "${change.abs().setScale(1, BigDecimal.ROUND_HALF_UP)}"
        ]
    } catch (Exception e) {
        logError "Error calculating trend: ${e.message}"
        return [direction: "→", percentage: "0"]
    }
}

/**
    TODO:
    Keep and use calculateMovingAverage() to improve our trend calculations.
    We could update calculateTrendIndicator() to use it for smoother, more accurate trends.
*/
/** 
 * Last Updated: 2025-01-14
 */

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
/** 
 * Last Updated: 2025-01-14
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

def safeAverage(list) {
    logDebug "Calculating average for list: $list"
    if (!list || list.isEmpty()) {
        return 0.0
    }
    // Convert all elements to BigDecimal for consistent decimal arithmetic
    def sum = list.collect { new BigDecimal(it.toString()) }.sum()
    logDebug "sum: $sum"
    def result = (sum / list.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
    logDebug "avg result: $result"
    return result ? result : 0.0
}

def initialize_intelligence_states(){
    // From trackEnvironmentalFactors()
    initializeStateThermalBehavior()

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
def initializeStateThermalBehavior() {
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
}


def backupIntelligenceData() {
    // Create timestamp for filename
    def timestamp = new Date().format("yyyy-MM-dd_HH-mm-ss")
    def fileName = "Thermostat_Manager_Data_Backup_${timestamp}.json"
    
    // Collect all intelligence-related states
    def backupData = [
        thermalBehavior: state.thermalBehavior,
        deviceTemperatures: state.deviceTemperatures,
        comfortMetrics: state.comfortMetrics,
        performanceHistory: state.performanceHistory,
        predictionMetrics: state.predictionMetrics
    ]
    
    // Serialize the data
    def jsonData = serializeHashTable(backupData)
    
    // Write to file
    if (writeToFile(fileName, jsonData)) {
        log.info "Intelligence data successfully backed up to ${fileName}"
        return fileName
    } else {
        log.error "Failed to backup intelligence data"
        return null
    }
}

def restoreIntelligenceData(fileName) {
    try {
        // Read the backup file
        def jsonData = readFromFile(fileName)
        if (!jsonData) {
            log.error "No data found in backup file"
            return false
        }
        
        // Deserialize the data
        def backupData = deserializeHashTable(jsonData)
        
        // Merge backup data with existing data
        if (backupData.thermalBehavior) {
            mergeAndUpdateData("thermalBehavior", backupData.thermalBehavior)
        }
        if (backupData.deviceTemperatures) {
            mergeAndUpdateData("deviceTemperatures", backupData.deviceTemperatures)
        }
        if (backupData.comfortMetrics) {
            mergeAndUpdateData("comfortMetrics", backupData.comfortMetrics)
        }
        if (backupData.performanceHistory) {
            mergeAndUpdateData("performanceHistory", backupData.performanceHistory)
        }
        if (backupData.predictionMetrics) {
            mergeAndUpdateData("predictionMetrics", backupData.predictionMetrics)
        }
        
        log.info "Intelligence data successfully restored from ${fileName}"
        return true
    } catch (Exception e) {
        log.error "Error restoring intelligence data: ${e}"
        return false
    }
}

private void mergeAndUpdateData(String key, Map backupData) {
    def currentData = state[key] ?: [:]
    
    // For each type of data, implement specific merge logic
    switch(key) {
        case "thermalBehavior":
            mergeThermalBehavior(currentData, backupData)
            break
        case "deviceTemperatures":
            mergeDeviceTemperatures(currentData, backupData)
            break
        case "comfortMetrics":
            mergeComfortMetrics(currentData, backupData)
            break
        case "performanceHistory":
            mergePerformanceHistory(currentData, backupData)
            break
        case "predictionMetrics":
            mergePredictionMetrics(currentData, backupData)
            break
    }
    
    state[key] = currentData
}

private void mergeThermalBehavior(Map current, Map backup) {
    // Merge events while keeping the most recent ones
    if (current.events && backup.events) {
        def allEvents = (current.events + backup.events).unique { it.timestamp }
        current.events = allEvents.sort { it.timestamp }
    }
    
    // Merge performance metrics by combining data points
    if (current.performanceByDelta && backup.performanceByDelta) {
        backup.performanceByDelta.each { delta, metrics ->
            if (current.performanceByDelta[delta]) {
                current.performanceByDelta[delta].avgCoolingRate = 
                    (current.performanceByDelta[delta].avgCoolingRate + metrics.avgCoolingRate) / 2
                current.performanceByDelta[delta].successRate = 
                    (current.performanceByDelta[delta].successRate + metrics.successRate) / 2
            } else {
                current.performanceByDelta[delta] = metrics
            }
        }
    }
}

Boolean writeToFile(String fileName, String data) {
    // Create boundary and payload
    String boundary = "----CustomBoundary"
    String payloadTop = "--${boundary}\r\nContent-Disposition: form-data; name=\"uploadFile\"; filename=\"${fileName}\"\r\nContent-Type: text/plain\r\n\r\n"
    String payloadBottom = "\r\n--${boundary}--"

    String fullPayload = "${payloadTop}${data}${payloadBottom}"

    try {
        def hubAction = new hubitat.device.HubAction(
        method: "POST",
        path: "/hub/fileManager/upload",
        headers: [
        HOST: "127.0.0.1:8080",
        'Content-Type': "multipart/form-data; boundary=${boundary}"
    ],
        body: fullPayload
    )

        sendHubCommand(hubAction)

        if (enabledebug) log.debug "HTTP POST was successful."
        return true
    } catch (Exception e) {
        log.error "HTTP POST failed: ${e.message}"
        return false
    }
}

def readFromFile(fileName) {
    def host = "localhost"  // or "127.0.0.1"
    def port = "8080"  
    def path = "/local/" + fileName
    def uri = "http://" + host + ":" + port + path

    if (enabledebug) log.trace "HTTP GET URI ====> $uri"

    def fileData = null

    try {
        httpGet(uri) { resp ->
            if (enabledebug) log.debug "HTTP Response Code: ${resp.status}"
            if (enabledebug) log.debug "HTTP Response Headers: ${resp.headers}"
            if (resp.success) {
                if (enabledebug) log.debug "HTTP GET successful."
                fileData = resp.data.text
            } else {
                log.error "HTTP GET failed. Response code: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "HTTP GET call failed: ${e.message}"
    }

    if (enabledebug) log.debug "HTTP GET RESPONSE DATA: ${fileData}"
    return fileData
}

def serializeHashTable(hashTable) {
    return JsonOutput.toJson(hashTable)
}

def deserializeHashTable(jsonString) {
    JsonSlurper jsonSlurper = new JsonSlurper()
    return jsonSlurper.parseText(jsonString)
}