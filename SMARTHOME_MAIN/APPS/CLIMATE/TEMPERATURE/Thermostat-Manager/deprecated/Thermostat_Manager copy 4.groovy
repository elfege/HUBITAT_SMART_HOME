import java.text.SimpleDateFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static var1 = "test" // works. ToDo: swap as many atomicState with static; but mostly constant ones (in which case I should then mark them as "final")

import java.text.SimpleDateFormat

definition(
    name: "Thermostat Manager",
    namespace: "elfege",
    author: "ELFEGE",

    description: "A Trully Smart Thermostat Manager That Can Save You Tons Of Money",

    category: "Green Living",
    iconUrl: "https://www.elfege.com/penrose.jpg",
    iconX2Url: "https://www.elfege.com/penrose.jpg",
    iconX3Url: "https://www.elfege.com/penrose.jpg",
    image: "https://www.elfege.com/penrose.jpg"
)
/* ################################*SETTINGS* #################################*/
preferences {

    page name: "MainPage"
    page name: "thermostats"
    page name: "methods"
    page name: "contactsensors"
    page name: "powersaving"
    page name: "comfortSettings"
    page name: "windowsManagement"
    page name: "fanCirculation"
    page name: "virtualThermostat"
    page name: "operationConsistency"

}
def MainPage() {

    update_app_label()   

    def pageProperties = [
        name: "MainPage",
        title: "${app.label}",
        nextPage: null,
        install: true,
        uninstall: true
    ]

    dynamicPage(pageProperties) {

        section() {
            if (simpleModeName == null) app.updateSetting("simpleModeName", [type: "text", value: "Sleep"])

            //label title: "Assign a name",description:"$state.appLabel", required: false, submitOnChange:true // can't use this because it shows html font tags
            input "appLabel", "text", title: "Assign a name to this instance of $app.name", submitOnChange: true
            app.updateLabel(appLabel)
            input "celsius", "bool", title: "Celsius (untested! Please send feedback)", submitOnChange: true
            paragraph "It's nearly impossible to test the efficacy of this feature in an environment where all devices work in Fahrenheit. Please, if you get to test it, send feedback through Hubitat Community threads"
            if (celsius) {
                convert_db_to_celsius()
            }
            else {
                if (state.currentUnit == "celsius") convert_db_to_fahrenheit() // will run only if db was already converted to celsius, F being default. 
            }
        }
        section()
        {
            input "pause", "button", title: "$state.button_name"
            input "buttonPause", "capability.doubleTapableButton", title: "Pause/resume this app when I double tap a button", multiple: true, required: false, submitOnChange: true

            if (buttonPause) {
                input "buttonTimer", "number", title: "optional: time limit in minutes", required: false
            }
            input "restricted", "mode", title: "Restricted modes", multiple: true
        }
        section("Main Settings") {

            href "thermostats", title: "Thermostats and temperature sensors", description: ""

            if (thermostat && outsideTemp) {
                href "methods", title: "Methods of evaluation", description: ""
                href "contactsensors", title: "Contacts, Windows And Doors", description: ""
                href "powersaving", title: "Motion and power saving", description: ""           
                href "windowsManagement", title: "Windows Control", description: ""
                href "comfortSettings", title: "$simpleModeName Mode", description: ""
                href "fanCirculation", title: "Air circulation", description: ""
                href "virtualThermostat", title: "Manage an extra electric heater and/or cooler", description: ""
                href "operationConsistency", title: "Fail safe", description: ""

            }
            else {
                paragraph format_text("You need to select a thermostat and a weather data source before you can access more settings.", "white", "blue")
            }
        }

        section("Actions")
        {

            long now = now() 

            input "run", "button", title: "RUN"
            input "update", "button", title: "UPDATE"
            input "poll", "button", title: "REFRESH DEVICES"
            input "no_reboot", "bool", title: "No Automatic Reboot", defaultValue: true, submitOnChange: true
            if (!no_reboot) {
                def H = other_hubs ? "all hubs" : "hub"
                input "reboot", "button", title: "Reboot ${H}"
                input "other_hubs", "text", title: "Also reboot other hubs (enter IP addresses separated by commas)", submitOnChange: true
                if (other_hubs) {
                    def validatedIPs = validateAndFormatIPs(other_hubs)
                    if (validatedIPs.size() > 0) {
                        paragraph "Validated IP addresses: ${validatedIPs.join(', ')}"
                    } else {
                        paragraph "No valid IP addresses entered. Please check your input."
                    }
                }
            }

            input "polldevices", "bool", title: "Poll devices", submitOnChange: true
            input "enabledebug", "bool", title: "Debug logs (verbose)", submitOnChange: true
            input "enabletrace", "bool", title: "Trace logs", submitOnChange: true
            input "enablewarning", "bool", title: "Warning logs", submitOnChange: true
            input "enableinfo", "bool", title: "Info logs", submitOnChange: true
            input "dev_mode", "bool", title: "Dev Mode", submitOnChange: true

            if (dev_mode) {
                state.dev_mode_time = now

                state.dev_mode_just_activated = true

                app.updateSetting("enabletrace", [type: "bool", value: true])
                app.updateSetting("enablewarning", [type: "bool", value: true])
                app.updateSetting("enableinfo", [type: "bool", value: true])

                input "dev_mode_only", "bool", title: "Dev Mode Only", submitOnChange: true
                if (dev_mode_only) {
                    app.updateSetting("enabletrace", [type: "bool", value: false])
                    app.updateSetting("enablewarning", [type: "bool", value: false])
                    app.updateSetting("enableinfo", [type: "bool", value: false])
                }
            }
            else if (dev_mode_only) {
                app.updateSetting("dev_mode_only", [type: "bool", value: false])
            }


            state.EnableDebugTime = state.EnableDebugTime == null ? now : state.EnableDebugTime
            state.enableDescriptionTime = state.enableDescriptionTime == null ? now : state.enableDescriptionTime
            state.EnableWarningTime = state.EnableWarningTime == null ? now : state.EnableWarningTime
            state.EnableTraceTime = state.EnableTraceTime == null ? now : state.EnableTraceTime


            if (enabledebug) state.EnableDebugTime = now
            if (enabledebug) state.EnableTraceTime = now
            if (enablewarning) state.EnableWarningTime = now
            if (enableinfo) state.enableDescriptionTime = now

            state.lastCheckTimer = now // ensure it won't run check_logs_timer right away to give time for states to update
        }
    }
}

def validateAndFormatIPs(input) {
    def ipList = input.split(',').collect { it.trim() }
    def validIPs = []

    ipList.each {
        ip ->
            // Remove any http:// or https:// prefix
            def cleanIP = ip.replaceAll('^(https?://)?', '')

        // Simple validation: check if it has 4 parts, each between 0 and 255
        def parts = cleanIP.split('\\.')
        if (parts.size() == 4 && parts.every {
            part ->
            try {
                def num = part.toInteger()
                return num >= 0 && num <= 255
            } catch (Exception e) {
                return false
            }
        }) {
            validIPs.add(cleanIP)
            if (enabledebug || is_dev_app()) log.debug "Valid IP address: $cleanIP"
        } else {
            log.warn "Invalid IP address format: $cleanIP"
        }
    }

    log.debug "Valid hub IPs: $validIPs"
    return validIPs
}

def thermostats(){

    def title = format_text("Thermostats, sensors, heaters and coolers", "white", "grey")

    def pageProperties = [
        name: "thermostats",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {
        section("Select the thermostat you want to control")
        { 
            input "thermostat", "capability.thermostat", title: "select a thermostat", required: true, multiple: false, description: null, submitOnChange: true
            
            input "ignoreTarget", "bool", title: "Optional: Do not send any target command to the main thermostat (only heat/cool commands will be sent)", defaultValue: false, submitOnChange: true

            if (!useBothThermostatsForCool && !useBothThermostatsForHeat) {
                input "doNotSendAnyCoolHeatOffComm", "bool", title: "Optional: Do not send any cool/heat command to the main thermostat (only set points commands will be sent)", defaultValue: false, submitOnChange: true
            }
            else if (doNotSendAnyCoolHeatOffComm) {
                // not compatible, disable if enabled
                app.updateSetting("doNotSendAnyCoolHeatOffComm", [type: "bool", value: false])
            }
            if (ignoreTarget) {
                app.updateSetting("offrequiredbyuser", [type: "bool", value: true]) // needs to be true when ignoreTarget is enabled  
                app.updateSetting("doNotSendAnyCoolHeatOffComm", [type: "bool", value: false])// can't have both

                if (differentiateThermostatsHeatCool) {
                    input "exceptForThermostatCool", "bool", title: "Exception: Send target commands to $thermostatCool", submitOnChange: true, defaultValue: false 
                    input "exceptForThermostatHeat", "bool", title: "Exception: Send target commands to $thermostatHeat", submitOnChange: true, defaultValue: false

                    if (exceptForThermostatHeat) {
                        app.updateSetting("exceptForThermostatCool", [type: "bool", value: false])
                    }
                    if (exceptForThermostatCool) {
                        app.updateSetting("exceptForThermostatHeat", [type: "bool", value: false])
                    }
                }

            }
            if (doNotSendAnyCoolHeatOffComm) {
                app.updateSetting("ignoreTarget", [type: "bool", value: false])// can't have both
            } 

            input "differentiateThermostatsHeatCool", "bool", title: "Use 2 different thermostats: 1 for cooling, 1 for heating", submitOnChange: true, defaultValue: false

            if (differentiateThermostatsHeatCool) {
                input "useBothThermostatsForHeat", "bool", title: "Use both when in heat mode", submitOnChange: true, defaultValue: false
                input "useBothThermostatsForCool", "bool", title: "Use both when in cool mode", submitOnChange: true, defaultValue: false
                input "thermostatHeat", "capability.thermostat", title: "Select a thermostat used exclusively for heating", required: true, submitOnChange: true
                input "thermostatCool", "capability.thermostat", title: "Select a thermostat used exclusively for cooling", required: true, submitOnChange: true
                input "keep2ndThermOffAtAllTimes", "bool", title: "Keep the unused thermostat off at all times (if enabled, you can't use this thermostat for any other purpose, it'll be shut down within a minute after you turn it on when not in use by this app",
                    defaultValue: true, submitOnChange: true
            }
            else {
                app.updateSetting("useBothThermostatsForHeat", [type: "bool", value: false])
                app.updateSetting("useBothThermostatsForCool", [type: "bool", value: false])
                // app.updateSetting("thermostatCool", [type: "capability", value:null])
                // app.updateSetting("thermostatHeat", [type: "capability", value:null])
            }
            if ((useBothThermostatsForCool && !useBothThermostatsForHeat) || (!useBothThermostatsForCool && useBothThermostatsForHeat)) {
                // this option becomes mandatory if only one of these two options is selected: in the other mode than the one using 2 therms, other therm must stay off (except if too cold, see logic further down)
                app.updateSetting("keep2ndThermOffAtAllTimes", [type: "bool", value: true])
            }

            // verify not only capability, but also actual reading, some thermostats working with generic drivers
            // will return true to some capabilities while the hardware won't parse any value
            boolean hasHumidity = thermostat != null && thermostat.hasCapability("RelativeHumidityMeasurement") && thermostat.currentValue("humidity") != null
            if (enableinfo) log.info "$thermostat has humidity capability ? $hasHumidity"
            if (thermostat && !hasHumidity && !optionalHumSensor) {
                paragraph format_text("""Your thermostat doesn't support humidity measurement (or doesn't return any humidity value). As a consequence, you must select a separate humidity sensor""", "white", "blue")
            }
            if (thermostat || !hasHumidity) {
                input "optionalHumSensor", "capability.relativeHumidityMeasurement", title: "Select a humidity sensor", required: false, submitOnChange: true
            }

            if (restricted) {
                input "restrictedThermMode", "enum", title: "Select default's thermostat operation once your location is in restricted mode", options: ["off", "cool", "heat", "auto"], required: false, defaultValue: "off", submitOnChange: true
                if (restrictedThermMode == "auto") {
                    paragraph format_text("Beware that 'auto' is the override mode, which means that this app won't be able to control your thermostat until you set your thermostat back to either 'cool', 'heat' or 'off'", "white", "red")
                }
            }

        }

        section("Sensors")
        {
            input "outsideTemp", "capability.temperatureMeasurement", title: "Required: select a weather sensor for outside temperature", required: true, submitOnChange: true

            input "sensor", "capability.temperatureMeasurement", title: "select a temperature sensor (optional)", submitOnChange: true, multiple: true
            if (sensor) {
                input "preserveSensorBatteryLife", "bool", title: "Preseve Sensors Battery Life (if battery operated, expect less accurate temp. measurements)", defaultValue: true, submitOnChange: true 
                input "offrequiredbyuser", "bool", title: "Set thermostat's mode to 'off' when target temperature has been reached", defaultValue: false, submitOnChange: true
                state.pageRefresh = state.pageRefresh != null ? state.pageRefresh : 0
                state.pageRefresh += 1
                if (state.pageRefresh > 2 && offrequiredbyuser) {
                    paragraph format_text("Thermostat off when target temp is reached is mandatory with 'do not send target temperatures', which you have enabled in the sections above", "white", "red")
                    state.pageRefresh = 0
                }
                state.fanCirculateAlways = state.fanCirculateAlways != null ? state.fanCirculateAlways : false
                if (state.fanCirculateAlways == true && (offrequiredbyuser || ignoreTarget)) {
                    paragraph format_text("You have previously enabled the fan circulation option. It is not compatible with the thermostat's off mode so it has been disabled", "white", "red")
                    app.updateSetting("fanCirculateAlways", [type: "bool", value: false])
                    app.updateSetting("alwaysButNotWhenPowerSaving", [type: "bool", value: false])
                    app.updateSetting("ignoreTarget", [type: "bool", value: true])
                }
            }
        }
    }
}
def methods(){

    def title = format_text("METHODS OF EVALUTATION:", "white", "grey")

    def pageProperties = [
        name: "methods",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section(){
            input "autoOverride", "bool", title: "Ighore all commands to a thermostat when it is set to 'auto'", submitOnChange: true, defaultValue: false
            if (autoOverride) {
                input "overrideDuration", "number", title: "Set a time limit", description: "number in hours, 0 means unlimitted time", submitOnChange: true
            }

            input "method", "enum", title: "select the method you want $app.name to use to adjust your thermostats cooling and heating set points", options: ["normal", "auto"], submitOnChange: true
            if (method == "auto") {
                state.confirmed = state.confirmed == null ? "yes" : state.confirmed
                paragraph format_text("auto method: the app sets your target temperature based on several learning functions by taking humidity levels and outside temperature into consideration", "black", "white")
                input "RESET", "button", title: "RESET", submitOnChange: true
                if (state.confirmed == "no") { 
                    paragraph """<div style='
                    z - index: 9999;
                    padding: 20px;
                    border - radius: 10px;
                    font - size: 25px;
                    color: white;
                    background: rgba(0, 0, 0, 0.5);
                    position: relative;
                    margin: auto;
                    '>
Are you sure ? You will lose everything your app has learned over time!</div > """
                    input "reset_confirmed", "button", title: "YES", submitOnChange: true
                    input "no_reset", "button", title: "NO", submitOnChange: true
                }
                input "useDryBulbEquation", "bool", title: "Use the Predicted Mean Vote (PMV): algorithm that optimizes temperature based on humidity)"

            }
            else {
                paragraph format_text("IMPORTANT: If you chose to not use a dimmer as an input source, you may have to repeat your input several times before the app 'understands' that this is a user input. There is no other way at the moment for the platform to distinguish the source of a command. It also greatly facilitates Alexa integration if you name your dimmer 'temperature [room name]'", "white", "black")
            }
            if (method == "auto") {
                state.currentUnit = state.currentUnit ? state.currentUnit : false
                if (maxAutoHeat != "null" && minAutoHeat != "null" && minAutoCool != "null" && maxAutoCool != "null") {   
                    input "convertToCelsius", "bool", title: "Convert all these values to Celsius (if you forgot to select this option on the main page)", submitOnChange: true
                    if (convertToCelsius && !state.currentUnit) {
                        state.currentUnit = true
                        state.maxAutoHeatRestore = maxAutoHeat // backup to prevent loop down conversions
                        state.minAutoHeatRestore = minAutoHeat
                        state.minAutoCoolRestore = minAutoCool
                        state.maxAutoCoolRestore = maxAutoCool

                        app.updateSetting("celsius", [type: "bool", value: true])

                        app.updateSetting("maxAutoHeat", [type: "number", value: get_celsius(maxAutoHeat.toInteger())])
                        app.updateSetting("minAutoHeat", [type: "number", value: get_celsius(minAutoHeat.toInteger())])
                        app.updateSetting("minAutoCool", [type: "number", value: get_celsius(minAutoCool.toInteger())])
                        app.updateSetting("maxAutoCool", [type: "number", value: get_celsius(maxAutoCool.toInteger())])

                        convert_db_to_celsius()
                    }
                    else if (!convertToCelsius && state.currentUnit) {
                        state.currentUnit = false
                        if (enableinfo) log.info """restoring values: 

                        state.maxAutoHeatRestore = $state.maxAutoHeatRestore
                        state.minAutoHeatRestore = $state.minAutoHeatRestore
                        state.minAutoCoolRestore = $state.minAutoCoolRestore
                        state.maxAutoCoolRestore = $state.maxAutoCoolRestore
                        """
                        app.updateSetting("maxAutoHeat", [type: "number", value: state.maxAutoHeatRestore])
                        app.updateSetting("minAutoHeat", [type: "number", value: state.minAutoHeatRestore])
                        app.updateSetting("minAutoCool", [type: "number", value: state.minAutoCoolRestore])
                        app.updateSetting("maxAutoCool", [type: "number", value: state.maxAutoCoolRestore])
                    }
                    else if (convertToCelsius && state.currentUnit) {
                        if (enableinfo) log.info "already converted, doing nothing"
                    }

                    if (enableinfo) log.info "state.currentUnit = $state.currentUnit"
                }
                input "maxAutoHeat", "number", title: "Highest heating set point", defaultValue: celsius ? get_celsius(78) : 78, submitOnChange: true
                input "minAutoHeat", "number", title: "Lowest heating set point", defaultValue: celsius ? get_celsius(70) : 70, submitOnChange: true
                input "minAutoCool", "number", title: "Lowest cooling set point", defaultValue: celsius ? get_celsius(70) : 70, submitOnChange: true 
                input "maxAutoCool", "number", title: "Highest cooling set point", defaultValue: celsius ? get_celsius(78) : 78, submitOnChange: true

            }

            boolean dimmerRequired = false
            if (!useDryBulbEquation) {
                dimmerRequired = true
            }
            input "dimmer", "capability.switchLevel", title: "${dimmerRequired ? 'Select' : 'Use'} a dimmer as a mean for this app to learn from your input", required: dimmerRequired, submitOnChange: true
        }
        section(){
            if (sensor) {
                input "manageThermDiscrepancy", "bool", title: "My thermosat needs to be boosted (for example, because it's too close to a window or to your HVAC", submitOnChange: true
                if (manageThermDiscrepancy) {
                    input "UserSwing", "double", title: "Input your thermostat's default swing/offset", defaultValue: 0.5, submitOnChange: true
                    def text = """This is a setting made directly on your physical thermoat. If you need to cool at 73F, your thermostat will stop at 73 but not start again until 73.5 if your swing is set to 0.5. Generally this swing can be up to 2 or more degrees of variation. This input is necessary to allow this app to detect discrpancies and run emergency heating or cooling when, for example, your thermostat is located too close to a window or any other heat/cold source. Make sure you checked this setting on your thermostat directly (not accessible through device driver interface) and that it is set to your liking. See your thermostat documentation if you don't know how to modify this value."""
                    paragraph format_text(text, "white", "grey")
                }
            }
        }
    }
}
def closeBoolQuestions(){
    //if(enableinfo) log.info "closing bool questions"
    app.updateSetting("whyAdimmer", [type: "bool", value: false])
    app.updateSetting("tellMeMore", [type: "bool", value: false])
}
def contactsensors(){

    def title = format_text("CONTACTS AND DOORS", "white", "grey")

    def pageProperties = [
        name: "contactsensors",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section()
        {
            input "WindowsContact", "capability.contactSensor", title: "Turn off everything when any of these contacts is open", multiple: true, required: false, submitOnChange: true
            if (WindowsContact) {
                input "openDelay", "number", title: "After how long?", description: "Time in seconds", required: true
            }
            if (UseSimpleMode) {
                input "override_contacts_in_simple_mode", "bool", title: "When in $simpleModeName mode, ignore this rule", submitOnChange: true, defaultValue: false
            }

            input "doorsManagement", "bool", title: "When some doors are open, synchronise $thermostat with a thermostat from another room", defaultValue: false, submitOnChange: true
            if (doorsManagement) {
                input "doorsContacts", "capability.contactSensor", title: "select contact sensors", required: true, multiple: true, submitOnChange: true

                input "doorThermostat", "capability.thermostat", title: "select a thermostat from a different room", required: true, submitOnChange: true
                if (doorsContacts && doorThermostat) {
                    paragraph "when ${doorsContacts?.size()>1?"any of":""} ${doorsContacts} ${doorsContacts?.size()>1?"are":"is"} open, $thermostat will synchornise with $doorThermostat"
                    if (motionSensors) {
                        input "doorsOverrideMotion", "bool", title: "This option overrides motion based power savings", defaultValue: true, submitOnChange: true
                    }
                    if (UseSimpleMode) {
                        input "contactsOverrideSimpleMode", "bool", title: "$doorsContacts events override $simpleModeName Mode (until they're closed again)"

                    }
                    if (doorsContacts) {
                        input "useDifferentSetOfSensors", "bool", title: "Use a different set of temperature sensors when ${doorsContacts} ${doorsContacts.size()>1?"are":"is"} open", submitOnChange: true
                        if (useDifferentSetOfSensors) {
                            input "doorSetOfSensors", "capability.temperatureMeasurement", title: "Select your sensors", multiple: true, submitOnChange: true, required: true
                        }
                        input "otherRoomHasCoolerPreference", "bool", title: "$doorThermostat never goes into cool mode due to alternate cooler preference", submitOnChange: true
                        if (otherRoomHasCoolerPreference) {
                            input "otherRoomCooler", "capability.switch", title: "Select the switch being used by this other instance", required: true
                        }
                    }
                }
            }
            else {
                try {
                    app.updateSetting("doorsContacts", [type: "capability", value: []])
                }
                catch (Exception e) {
                    log.error "doorsContacts () => Exception $e"
                }
                try {
                    app.updateSetting("doorThermostat", [type: "capability", value: null])
                }
                catch (Exception e) {
                    log.error "def doorThermostat ) => Exception $e"
                }
                try {
                    app.updateSetting("contactsOverrideSimpleMode", [type: "bool", value: false])
                }
                catch (Exception e) {
                    log.error "def contactsensors() contactsOverrideSimpleMode on $e"
                }
                try {
                    app.updateSetting("useDifferentSetOfSensors", [type: "bool", value: false])
                }
                catch (Exception e) {
                    log.error "def contactsensors() useDifferentSetOfSensors tion $e"
                }
                try {
                    app.updateSetting("doorSetOfSensors", [type: "capability", value: []])
                }
                catch (Exception e) {
                    log.error "def doorSetOfSensors => Exception $e"
                }
                try {
                    app.updateSetting("otherRoomHasCoolerPreference", [type: "bool", value: false])
                }
                catch (Exception e) {
                    log.error "def contactsensors() otherRoomHasCoolerPreference  $e"
                }
                try {
                    app.updateSetting("otherRoomCooler", [type: "capability", value: null])
                }
                catch (Exception e) {
                    log.error "def otherRoomCooler  => Exception $e"
                }

            }
        }
    }
}
def powersaving(){

    def title = format_text("POWER SAVING OPTIONS", "white", "grey")

    def pageProperties = [
        name: "powersaving",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section(format_text("Power saving modes", "white", "blue")){
            input "powersavingmode", "mode", title: "Save power when my home is in one of these modes", required: false, multiple: true, submitOnChange: true
        }
        section(format_text("Motion Management", "white", "blue")){
            input "motionSensors", "capability.motionSensor", title: "Save power when there's no motion", required: false, multiple: true, submitOnChange: true

            if (motionSensors) {
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true, submitOnChange: true

                if (motionmodes && motionmodes?.size() > 1) {
                    int s = motionmodes.size()
                    int i = 0
                    for (s != 0; i < s; i++) {
                        def val = settings.find{ it?.key == "noMotionTimeWithMode${motionmodes[i]}" }?.value?.toInteger() 
                        def valString = ""
                        def text = "Motion timeout (in minutes) when mode is ${motionmodes[i]}"
                        if (val != null) {
                            float valHour = val / 60
                            valString = val >= 60 ? "<font color = 'red'>(that's ${valHour > valHour.toInteger() ? valHour.round(2) : valHour.toInteger()} hours)</font>" : ""
                            text = "Motion timeout (in minutes) when mode is ${motionmodes[i]} ${valString}"
                        }
                        input "noMotionTimeWithMode${motionmodes[i]}", "number", title: text, description: "${valString}", submitOnChange: true

                    }
                }
                else {
                    input "noMotionTime", "number", title: "Motion delay (in minutes):", description: "Time in minutes"
                }

                input "testMotionBattery", "bool", title: "Test motion sensors battery levels and ignore them if they're all too low", defaultValue: true, submitOnChange: true
                if (testMotionBattery) {
                    input "lowBatLevel", "number", title: "Indicate the low battery level threshold", defaultValue: 40
                }

            }

        }
        section()
        {
            if (powersavingmode || motionSensors) {
                input "criticalcold", "number", title: "Set a minimum temperature when there is no motion", required: true
                input "criticalhot", "number", title: "Set a maximum temperature when there is no motion", required: true
            }
        }
    }
}
def comfortSettings(){

    def title = format_text("COMFORT SETTINGS", "white", "grey")

    def pageProperties = [
        name: "comfortSettings",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section(format_text("$simpleModeName: set this app to specific conditions for when, for example, you go to sleep, you're working out or anything else that requires unique settings...", "white", "blue")){

            input "simpleModeName", "text", title: "Rename this unique mode as you see fit", defaultValue: "Sleep", submitOnChange: true
            input "UseSimpleMode", "bool", title: "Use a button to trigger $simpleModeName mode", submitOnChange: true

            if (UseSimpleMode) {                
                def message = ""
                def devicesStr = ""

                def s = nightModeButton?.size() 
                def i = 0
                input "nightModeButton", "capability.holdableButton", title: "When ${!nightModeButton ? "this button is" : (s > 1 ? "these buttons are" : "this button is")} pushed, work in limited mode (push again to cancel)", multiple: true, required: false, submitOnChange: true

                if (nightModeButton) {

                    if (useBothThermostatsForHeat) {
                        input "useOnlyThermostatHeatForHeatInSimpleMode", "bool", title: "use only $thermostatHeat for cooling when $simpleModeName mode is active", submitOnChange: true, defaultValue: false

                    }
                    if (useBothThermostatsForCool) {
                        input "useOnlyThermostatCoolForCoolInSimpleMode", "bool", title: "use only $thermostatCool for cooling when $simpleModeName mode is active", submitOnChange: true, defaultValue: false
                    }
                    input "simpleModeSimplyIgnoresMotion", "bool", title: "Simply ignore motion rules and keep running other rules normally when I hit $nightModeButton", submitOnChange: true, defaultValue: false
                    input "lightSignal", "capability.switch", title: "Flash a light three times to confirm", required: false, submitOnChange: true

                    if (lightSignal) {
                        input "turnSignalLightOffAfter", "bool", title: "turn off $lightSignal when done"

                        if (enableinfo) log.info "$lightSignal capabilities : ${lightSignal.getCapabilities()}"
                        if (lightSignal.hasCapability("ColorControl")) {
                            input "nightModeColor", "enum", title: "set the bulb to a specific color", options: ["blue", "red", "green"]
                            input "setPreviousColor", "bool", title: "set the bulb back to its previous color after signal", defaultValue: true
                        }
                    }

                    for (s != 0; i < s; i++) {
                        devicesStr = devicesStr.length() > 0 ? devicesStr + ", " + nightModeButton[i].toString() : nightModeButton[i].toString()
                    } 
                    input "simpleModeTimeLimit", "number", title: "Optional: return to normal operation after a certain amount of time (hours)", description: "Time in hours", submitOnChange: true
                    input "allowWindowsInSimpleMode", "bool", title: "Allow windows management, if any", defaultValue: false

                    if (simpleModeTimeLimit) {
                        message = "Limited mode will be canceled after $simpleModeTimeLimit hours or after a new button event" //. Note that $devicesStr will not be able to cancel limited mode before time is out" 
                        paragraph format_text(message, "white", "grey")
                    }
                    message = nightModeButton ? "$app.label will operate in limited mode when $devicesStr ${s > 1 ? "have" : "has"} been pushed and canceled when held, double tapped or pushed again. Power saving options will not be active" : ""
                    if (message) paragraph format_text(message, "white", "grey") //nightModeButton message

                    if (!simpleModeSimplyIgnoresMotion) {
                        input "setSpecialTemp", "bool", title: "Keep room at a preset temperature when $nightModeButton is pressed", submitOnChange: true, defaultValue: false
                        input "specialSubstraction", "bool", title: "Lower the current set point instead?", submitOnChange: true

                        if (setSpecialTemp) {
                            app.updateSetting("specialSubstraction", [type: "bool", value: false]) // foolproofing
                            input "specialTemp", "number", title: "Set the target temperature", required: true
                            input "specialDimmer", "capability.switchLevel", title: "Optional: Select a dimmer to adjust this spectific target temperature if needed", submitOnChange: true
                        }
                        if (specialSubstraction) {
                            app.updateSetting("setSpecialTemp", [type: "bool", value: false]) // foolproofing
                            input "substract", "number", title: "Substract this value to the current set point", required: true
                        }
                        if (ignoreTarget) {   
                            paragraph format_text("You see the option below because you enabled the 'ignore set point targets' option in the thermostats section", "white", "grey")
                            input "doNotIgnoreTargetInSimpleMode", "bool", title: "When in $simpleModeName mode, don't ignore temperature targets, send those values to the thermostat instead", defaultValue: false, submitOnChange: true
                            if (doNotIgnoreTargetInSimpleMode) {
                                input "dontSetThermModesInSimpleMode", "bool", title: "Dont send any heat/cool command in $simpleModeName mode", submitOnChange: true, defaultValue: false
                            }
                            else if (dontSetThermModesInSimpleMode) // if it was previously enabled and doNotIgnoreTargetInSimpleMode was just disabled, then make sure dontSetThermModesInSimpleMode is now disabled too. foolproofing... 
                            {
                                app.updateSetting("dontSetThermModesInSimpleMode", [type: "bool", value: false])
                            }
                        }
                        if (offrequiredbyuser && doNotIgnoreTargetInSimpleMode) {
                            input "dontTurnOffinNightMode", "bool", title: "Don't turn off my thermostat in $simpleModeName mode", submitOnChange: true, defaultValue: true
                        }
                    }

                    if (!doNotIgnoreTargetInSimpleMode) app.updateSetting("dontTurnOffinNightMode", [type: "bool", value: "false"])
                }
            }
            else {
                if (nightModeButton) app.updateSetting("nightModeButton", [type: "capability", value: []])
                app.updateSetting("simpleModeTimeLimit", [type: "number", value: null])
                app.updateSetting("allowWindowsInSimpleMode", [type: "bool", value: false])
                app.updateSetting("setSpecialTemp", [type: "bool", value: false])
                app.updateSetting("specialSubstraction", [type: "bool", value: false])
                app.updateSetting("substract", [type: "number", value: null])
                app.updateSetting("specialTemp", [type: "number", value: null])
                app.updateSetting("doNotIgnoreTargetInSimpleMode", [type: "bool", value: false])
                app.updateSetting("dontSetThermModesInSimpleMode", [type: "bool", value: false])
                app.updateSetting("dontTurnOffinNightMode", [type: "bool", value: false])
            }
        }
    }
}
def windowsManagement(){
    def title = format_text("WINDOWS SETTINGS", "white", "grey")

    def pageProperties = [
        name: "windowsManagement",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section()
        {
            input "controlWindows", "bool", title: "Control some windows", submitOnChange: true
            if (controlWindows) {
                input "windows", "capability.switch", title: "Turn on some switches/fan/windows when home needs to cool down, wheather permitting", multiple: true, required: false, submitOnChange: true
                if (windows) {

                    def text = """
                        < div style = 'background-color: #f2f2f2; border: 1px solid #ddd; border-radius: 5px; padding: 20px; margin-block-end: 20px; white-space: nowrap;' >
                            <h3 style='font-size: 24px; margin-block-end: 20px;'>Read this to avoid frustration!</h3>
                            <span style='font-size: 18px; line-height: 1.5; margin-block-end: 20px; word-wrap: break-word;white-space: normal'>
                            Having your windows reopening or re-closing after you intervened can be frustrating. To prevent that, the app will detect manual intervention and release control of your windows after you either closed or opened them yourself (for example, using rule machine or a voice command).
                            </span>
                            <span style='font-size: 18px; line-height: 1.5; margin-block-end: 20px;white-space: normal'>
                            However, this override functionality cancels out the benefit of this feature.
                            After you intervened to close your automated windows, the app will never open them again, with the following exceptions:
                            </span>
                            <ul style='font-size: 18px; line-height: 1.5; margin-block-end: 20px; margin-inline-start: 20px;'>
                            <li>you reset/update the app settings</li>
                            <li>You intervene again to close/open them back</li>
                            <li>If it gets dangerously cold inside</li>
                            </ul>
                            <span style='font-size: 18px; line-height: 1.5; margin-block-end: 20px; white-space: normal;'>
                            Beside that, after a manual override occured, the app won't use the windows to save power (instead of using your A.C., for instance), which is the core benefit of this feature
                            In order to prevent such inconvenience, you can enable the option named 'cancel manual override with location mode events'. That way, you can override the app's decision, for instance, 
                            to open the windows instead of using the A.C. by having them close back and, when your location changes its mode, the app will start controlling your windows again to save power whenever possible.
                            </span>
                            <span style='font-size: 18px; line-height: 1.5; margin-block-end: 20px; white-space: normal'>
                            Note that manual overrides will also ALWAYS be canceled out if you use a ${simpleModeName} mode button with (windows management enabled, since ${simpleModeName} allows you to totally ignore the windows if you wish).
                            </span>
                            <span style='font-size: 18px; line-height: 1.5; margin-block-end: 20px; word-wrap: break-word;white-space: normal'>
                            You can also leave this option disabled and let the app manage your windows entirely. You'll have to close a window back after you manually opened it (and reciprocally, reopen it after you opened it) for the app to resume its normal windows control.
                            </span>
                            </div >
                        """

                    paragraph text
                    input "resetWindowsOverrideWithLocationModeChange", "bool", title: "Cancel manual override with location mode events: the app always regains control of your windows when the mode changes", defaultValue: true

                    if (windows.size() > 1) {
                        input "onlySomeWindowsWillOpen", "bool", title: "Differentiate some windows' behavior based on location mode", submitOnChange: true, defaultValue: false

                        if (onlySomeWindowsWillOpen) {
                            def list = []
                            for (window in windows) {
                                list += window.toString()
                            }

                            list = list?.sort()
                            //if(enableinfo) log.info "------------- list = $list"

                            input "modeSpecificWindows", "mode", title: "select the modes under which you want only some specific windows to be operated", multiple: true, required: true
                            input "onlyThoseWindows", "enum", title: "Select the windows for these modes", options: list, required: true
                        }
                    }

                    input "timeWindowInsteadofModes", "bool", title: "use a time window instead of location modes for windows management", submitOnChange: true
                    if (timeWindowInsteadofModes) {
                        input "windowsFromTime", "time", title: "From", required: true
                        input "windowsToTime", "time", title: "To", required: true
                    }
                    else {
                        input "windowsModes", "mode", title: "Select under which modes ALL WINDOWS can be operated", required: true, multiple: true, submitOnChange: true
                    }

                    if (windowsModes || timeWindowInsteadofModes) {
                        input "closeWhenOutsideWindowsModes", "bool", title: "Close all windows once home location mode is no longer in ${windowsModes ? "one of these modes" : "this time window"}", defaultValue: false, submitOnChange: true
                    }

                    input "outsidetempwindowsH", "number", title: "Set a temperature below which it's ok to turn on $windows", required: true, submitOnChange: true
                    input "outsidetempwindowsL", "number", title: "Set a temperature below which it's NOT ok to turn on $windows", required: true, submitOnChange: true
                    if (outsidetempwindowsH && outsidetempwindowsL != null) // for the rare case a user might set low to "0" - it'd be interpreted as boolean false
                    {
                        paragraph "If outside temperature is between ${outsidetempwindowsL}F & ${outsidetempwindowsH}F, $windows will be used to coold down your place instead of your AC"

                        input "operationTime", "bool", title: "${windows}' operation must stop after a certain time", defaultValue: false, submitOnChange: true
                        if (operationTime) {
                            input "windowsDuration", "number", title: "Set minimum operation time", description: "time in seconds", required: false, submitOnChange: true
                            if (windowsDuration) {
                                paragraph "<div style=\"inline-size:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">${app.name} will determine duration based on this value and outside temperature. The cooler it is outside, the shorter the duration (the closer the duration will be to the minimum you set here). Recommended value: 10 seconds</div>"

                                input "customCommand", "text", title: "custom command to stop operation (default is 'off()')", required: false, defaultValue: "off()", submitOnChange: true
                                input "differentDuration", "bool", title: "Differentiate operation time", defaultValue: false, submitOnChange: true
                                if (!differentDuration) {
                                    input "maxDuration", "number", title: "Set maximum operation time", description: "time in seconds", required: false, submitOnChange: true

                                }
                                else {
                                    def list = []
                                    int i = 0
                                    int s = windows.size() 
                                    def device
                                    for (s != 0; i < s; i++) {
                                        device = windows[i]
                                        input "windowsDuration${i}", "number", title: "Set minimum operation time for $device", description: "time in seconds", required: false, submitOnChange: true
                                        input "maxDuration${i}", "number", title: "Set maximum operation time $device", description: "time in seconds", required: false, submitOnChange: true

                                    }
                                }
                            }

                            if (customCommand) {
                                def cmd = customCommand.contains("()") ? customCommand.minus("()") : customCommand
                                def windowsCmds = windows.findAll{ it.hasCommand("${cmd}") }
                                boolean cmdOk = windowsCmds.size() == windows.size()
                                if (!cmdOk) {
                                    paragraph "<div style=\"inline-size:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">SORRY, THIS COMMAND $customCommand IS NOT SUPPORTED BY AT LEAST ONE OF YOUR DEVICES! Maybe a spelling error? In any case, make sure that each one of them support this command</div>"

                                }
                                else {
                                    paragraph """<div style=\"inline-size:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">The command $customCommand is supported by all your devices!</div> """

                                }
                            }

                        }
                    }

                    if (doorsContacts && doorThermostat) {
                        paragraph """In the 'contact sensors' settings you opted for for synchronizing your thermostat's operations 
                        with another thermostat's when some door contacts are open. Do you want to also control the windows from this other thermostat's room ? """
                        input "useOtherWindows", "bool", title: "Also control these windows when $doorsContacts are open", submitOnChange: true, defaultValue: false
                        if (useOtherWindows) {
                            input "otherWindows", "capability.switch", title: "Select your windows", required: true, multiple: true
                        }

                    }
                }
            }
        }
    }
}
def fanCirculation(){
    def title = format_title("AIR CIRCULATION")

    def pageProperties = [
        name: "fanCirculation",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section()
        {
            if (contact || windows) {
                input "fanCirculateAlways", "bool", title: "Run ${thermostat}'s fan circulation when contacts are open and temp is getting too high", submitOnChange: true
                input "fanCirculateSimpleModeOnly", "bool", title: "Run fan circulation only in $simpleModeName Mode", submitOnChange: true, defaultValue: false
                if (ignoreTarget && (fanCirculateAlways || fanCirculateSimpleModeOnly)) {
                    app.updateSetting("fanCirculateAlways", [type: "bool", value: false])
                    app.updateSetting("fanCirculateSimpleModeOnly", [type: "bool", value: false])
                    paragraph format_text("Fan circulate is not compatible with 'ignore target' settings! Disable it in thermostats section)", "white", "red")
                }
                input "fanCirculateAlways", "bool", title: "Run ${thermostat}'s fan circulation without interruption", submitOnChange: true

                if (fancirculate) {
                    app.updateSetting("fanCirculateAlways", [type: "bool", value: false])
                }
                if (fanCirculateAlways) {
                    app.updateSetting("offrequiredbyuser", [type: "bool", value: false])
                    state.fanCirculateAlways = true // needed for the UI to notify user of incompatibility with "offrequiredbyuser"
                    input "alwaysButNotWhenPowerSaving", "bool", title: "Fan circulation must stop when there is no motion (if motion sensitivity has been enabled) and/or in away mode", defaultValue: false
                }
                else {
                    state.fanCirculateAlways = false
                }
                if (fanCirculateSimpleModeOnly) {
                    if (fanCirculateSimpleModeOnly) app.updateSetting("fanCirculateModes", [type: "mode", value: null])
                    if (!fanCirculateSimpleModeOnly) input "fanCirculateModes", "mode", title: "Run fan circulation only in certain modes", multiple: true, submitOnChange: true
                    app.updateSetting("fancirculate", [type: "bool", value: false])
                }

            }
            input "fan", "capability.switch", title: "Turn on a fan", submitOnChange: true
            if (fan) {
                input "fanWhenCoolingOnly", "bool", title: "Turn $fan on when cooling only", submitOnChange: true
                if (fanWhenCoolingOnly && neverTurnOff) paragraph "not compatible with keeping the fan on"
                input "fanWhenHeatingOnly", "bool", title: "turn on $fan when heating only", submitOnChange: true
                if (fanWhenHeatingOnly && neverTurnOff) paragraph "not compatible with keeping the fan on"
                input "neverTurnOff", "bool", title: "Never turn off $fan", submitOnChange: true

                if (fanWhenCoolingOnly || neverTurnOff) app.updateSetting("fanWhenHeatingOnly", [value: false, type: "bool"])// foolproofing 
                if (fanWhenHeatingOnly || neverTurnOff) app.updateSetting("fanWhenCoolingOnly", [value: false, type: "bool"])// foolproofing 

                if (neverTurnOff) {
                    app.updateSetting("fanWhenCoolingOnly", [value: false, type: "bool"])// foolproofing 
                    app.updateSetting("fanWhenHeatingOnly", [value: false, type: "bool"])// foolproofing 
                }
                else if (!neverTurnOff && (fanWhenHeatingOnly || fanWhenCoolingOnly)) {

                }
                input "keepFanOnInRestrictedMode", "bool", title: "Keep the fan running when in restrcited mode..."
                input "keepFanOnInNoMotionMode", "bool", title: "Keep the fan running when in there is no motion..."
            }
            else // foolproofing 
            {
                app.updateSetting("fanWhenCoolingOnly", [value: false, type: "bool"])
                app.updateSetting("fanWhenHeatingOnly", [value: false, type: "bool"])
                app.updateSetting("keepFanOnInRestrictedMode", [value: false, type: "bool"])
                app.updateSetting("keepFanOnInNoMotionMode", [value: false, type: "bool"])
                app.updateSetting("neverTurnOff", [value: false, type: "bool"])
            }
            input "fanDimmer", "capability.switchLevel", title: "Control a fan with a dimmer", submitOnChange: true

            if (fanDimmer) {
                input "maxFanSpeed", "number", title: "Set the maxium value for $fanDimmer", submitOnChange: true, defaultValue: 50, range: "30..100"
                input "mediumFanSpeed", "number", title: "Set a medium value for $fanDimmer", range: "30..$maxFanSpeed"
                input "lowFanSpeed", "number", title: "Set the lowest value for $fanDimmer", range: "0..$mediumFanSpeed"

                if (enableinfo) log.info "dimmer = $dimmer fanDimmer = $fanDimmer"

                if (fanDimmer?.displayName == dimmer?.displayName || fanDimmer?.displayName == fan?.displayName) {
                    def m = "You cannot use ${fanDimmer == dimmer ? "$dimmer" : "$fan"} for this operation"
                    paragraph format_text(m, "white", "red")
                    app.updateSetting("fanDimmer", [type: "capability", value: []])

                }
                else {
                    paragraph format_text("Fan speed will adjust with cooling efficiency", "white", "grey")
                    input "silenceMode", "mode", title: "Keep this dimmer at a certain level in certain modes", submitOnChange: true
                    if (silenceMode) {
                        input "silenceValue", "number", title: "Set a target level for these modes"
                    }
                }
                input "neverTurnOffFanDimmer", "bool", title: "Keep this fan on at all times"
                input "keepFanDimmerOnIfOutsideLowerThanInside", "bool", title: "Keep this fan on if outside temperature is lower than inside temp"

                if (WindowsContact) {
                    input "keepFanOnWhenWindowsOpen", "bool", title: "Keep this fan on when ${WindowsContact.join(", ")} ${WindowsContact.size() > 1 ? "are":"is"} open"
                }
            }
        }
    }
}
def virtualThermostat(){
    def title = format_title("Heater & Cooler")

    def pageProperties = [
        name: "virtualThermostat",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section("Select alternate heater and/or cooler")
        {
            input "heatpump", "bool", title: "$thermostat is a heat pump", submitOnChange: true

            if (differentiateThermostatsHeatCool && heatpump) {
                app.updateSetting("heatpump", [type: "bool", value: false])
                def mess = "'Heat pump management' is not compatible with the dual thermostat option you selected in the thermostats section"
                paragraph format_text(mess, "white", "darkgray")
            }
            else {
                def mssg = "Because $thermostat is a heatpump, you must select an alternate heater controlled by a switch (see further down). This is due to the fact that a heatpump will not be power efficient under certain weather conditions and temperatures. ${app.label} will make sure the best device is being used when needed"
                if (heatpump) {paragraph format_text(mssg, "blue", "white") }
            }

            boolean heaterRequired = heatpump && !altThermostat ? true : altThermostat ? false : false // to set the "required" parameter, since it's a boolean. 
            boolean altThermRequired = heatpump && !heater ? true : false

            if (heaterRequired) {
                paragraph format_text("Either heater or alternate thermostat is mandatory with heat pump option", "red", "white")
            }

            input "heater", "capability.switch", title: "Select a switch to control an alternate heater", required: heaterRequired, submitOnChange: true, multiple: false 

            input "altThermostat", "capability.thermostat", title: "Select an alternate thermostat", submitOnChange: true

            def noIssue = true
            if (differentiateThermostatsHeatCool && ("${altThermostat}" == "${thermostatHeat}" || "${altThermostat}" == "${thermostatCool}")) {
                app.updateSetting("altThermostat", [type: "capability", value: []])
                def mess = "$altThermostat can't be used as your alternate thermostat because it's already selected in the 'dual thermostat' section"
                paragraph format_text(mess, "red", "darkgray")
                input "acknowledge", "bool", title: "OK", required: true, submitOnChange: true, defaultValue: false // just to refresh the dynamic page
                noIssue = false

            }
            if (noIssue && (heater || altThermostat)) {
                def only = "ONLY if outside temperature falls below a threshold"
                def m = heater && altThermostat ? "Turn on $heater and set $altThermostat to heat $only" : heater ? "Turn on $heater $only" : altThermostat ? "Set $altThermostat to heat $only" : "error"
                input "addLowTemp", "bool", title: m, submitOnChange: true

                if (heatpump || addLowTemp) {
                    input "lowtemp", "number", title: "low outside temperature threshold", required: true, defaultValue: 30

                    input "useAllHeatSources", "bool", title: "Use both heatpump and alternate heating source until outside temp reaches an even lower threshold", submitOnChange: true

                    if (differentiateThermostatsHeatCool && useAllHeatSources && heatpump) {
                        app.updateSetting("useAllHeatSources", [type: "bool", value: false])
                        def mess = "'Use both heat sources' is not compatible with the dual thermostat option you selected in the thermostats section"
                        paragraph format_text(mess, "white", "darkgray")
                    }
                    if (useAllHeatSources) {
                        input "evenLowertemp", "number", title: "Second low outside temperature threshold", required: true, defaultValue: 0, submitOnChange: true

                    }
                    input "useAllHeatSourcesWithMode", "bool", title: "Use all heat sources when in certain modes", submitOnChange: true
                    if (useAllHeatSourcesWithMode) {
                        input "allHeatModes", "mode", title: "Select modes under which all heat sources will be warming your place", required: true, submitOnChange: true, multiple: true

                    }

                    input "doNotUseMainThermostatInCertainModes", "bool", title: "Exclusively use ${heater && altThermostat?"$heater and $altThermostat":heater?"$heater" : "$altThermostat"} when in certain modes", submitOnChange: true
                    if (doNotUseMainThermostatInCertainModes) {
                        input "altThermostatORheaterOnlyModes", "mode", title: "Select modes under which all heat sources will be warming your place", required: true, submitOnChange: true, multiple: true
                        if (nightModeButton) {
                            input "altThermostatOnlyInSimpleMode", "bool", title: "Apply this option when in $simpleModeName mode (related to $nightModeButton)", submitOnChange: true
                        }

                    }
                    updateAllHeatSourcesBooleans()

                }
                if (heater?.hasAttribute("power") && pw) // if both are true: power meter cap for heater switch and verify status with power meter
                {
                    input "controlPowerConsumption", "bool", title: "control power consumption", submitOnChange: true, required: false
                    if (controlPowerConsumption) {
                        input "maxPowerConsumption", "number", title: "Set maximum power in watts", submitOnChange: true, required: true
                        input "devicePriority", "enum", title: "Priority given to:", options: ["$heater", "$thermostat"], required: true, submitOnChange: true
                    }
                }
            }

            input "cooler", "capability.switch", title: "Select a switch to control an alternate cooler", required: false, submitOnChange: true, multiple: false
            if (cooler) {
                input "preferCooler", "bool", title: "Prefer cooler (set to false to see power savings options)", submitOnChange: true, defaultValue: false
                if (preferCooler) {
                    def x = "$cooler will be the only unit to cool this room, ${thermostat.toString()} is still used as an important sensor and user input source"
                    paragraph format_text(x, "white", "grey")

                    input "preferCoolerLimitTemperature", "number", title: "unless outside temperature is beyond this value", submitOnChange: true, description: "set a temperature threshold, leave empty if not required"
                    input "userBoostOffset", "number", title: "unless inside temperature is greater than target temperature by this amplitude", description: "set the amplitude (x=inside temp - target)"

                    // foolproof, user must preferCooler with these options
                    app.updateSetting("coolerControlPowerConsumption", [type: "bool", value: false])
                    app.updateSetting("coolerMaxPowerConsumption", [type: "number", value: null])
                    app.updateSetting("coolerDevicePriority", [type: "enum", value: null])
                    app.updateSetting("addHighTemp", [type: "bool", value: false])   

                    input "efficiencyOverride", "bool", title: "Efficiency override: keep $thermostat cooling if it turns out that $cooler isn't as efficient as it should"

                }
                else { // if not preferCooler... 
                    input "addHighTemp", "bool", title: "Turn on $cooler only if OUTSIDE temperature goes beyond a certain threshold", submitOnChange: true

                    if (addHighTemp) {
                        input "hightemp", "number", title: "high outside temperature threshold", required: true, defaultValue: 80
                    }
                    if (cooler.hasAttribute("power") && pw) // if both are true: power meter cap for heater switch and verify status with power meter
                    {
                        input "coolerControlPowerConsumption", "bool", title: "control power consumption", submitOnChange: true, required: false
                        if (coolerControlPowerConsumption) {
                            input "coolerMaxPowerConsumption", "number", title: "Set maximum power in watts", submitOnChange: true, required: true
                            input "coolerDevicePriority", "enum", title: "Priority given to:", options: ["$cooler", "$thermostat"], required: true, submitOnChange: true
                            if (enablewarning) log.warn """
                            devicePriority = $devicePriority
                            """
                        }
                    }
                }
            }
            else {
                app.updateSetting("preferCooler", [type: "bool", value: false])
            }
        }
    }
}
def updateAllHeatSourcesBooleans(){
    if (enableinfo) log.info "udpating updateAllHeatSourcesBooleans"
    if (useAllHeatSourcesWithMode) {
        app.updateSetting("useAllHeatSources", [type: "bool", value: false])
        if (enableinfo) log.info "useAllHeatSources set to false"
    }
    if (useAllHeatSources || doNotUseMainThermostatInCertainModes) {
        app.updateSetting("useAllHeatSourcesWithMode", [type: "bool", value: false])
        if (enableinfo) log.info "useAllHeatSourcesWithMode set to false"
    }
}
def operationConsistency(){
    def title = format_title("Verify consistency of most operations")

    def pageProperties = [
        name: "operationConsistency",
        title: title,
        nextPage: "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section("power consistency")
        {
            input "forceCmd", "bool", title: "Force commands (for old non-Zwave-plus devices that don't refresh their status properly under certain mesh conditions)", defaultValue: false
            input "pw", "capability.powerMeter", title: "optional: verify my thermostat's consistent operation with a power meter", required: false, submitOnChange: true
            if (!pw) {
                input "overrideThermostatModeCheckBeforeSendingCmd", "bool", title: "Do not check current thermostat's state before sending a command", defaultValue: false
            }
            // if (differentiateThermostatsHeatCool && pw) {
            if (enablewarning) log.warn "pw = $pw must be off loaded from settings"
            //     def text = "Sorry, this fail safe feature is not compatible with using two thermostats at the moment"
            //     paragraph format_text(text, "white", "blue")
            //     app.updateSetting("pw", [type: "capability", value: []])
            // }
        }
        section("Safety")
        {
            input "antifreeze", "bool", title: "Optional: Customize Antifreeze", submitOnChange: true, defaultValue: false

            if (antifreeze) {
                input "antiFreezeThreshold", "number", title: "Threshold Temperature (as of which antifreeze is triggered)", required: true, submitOnChange: true
                input "safeValue", "number", title: "Safety Temperature", required: true, submitOnChange: true, default: 72
                input "backupSensor", "capability.temperatureMeasurement", title: "Optional: pick a backup sensor (in case of device failure)", required: false

            }
            input "sendAlert", "bool", title: "Send a sound and/or text notification when temperature goes below antifreeze safety", submitOnChange: true
            if (sendAlert) {
                input "speech", "capability.speechSynthesis", title: "Select speech devices", multiple: true, required: false, submitOnChange: true 
                input "musicDevice", "capability.musicPlayer", title: "Select music players", multiple: true, required: false, submitOnChange: true
                if (musicDevice || speech) {
                    input "volumeLevel", "number", title: "Set the volume level", range: "10..100", required: true, submitOnChange: true
                }
                input "initializeDevices", "bool", title: "Try to fix unresponsive speakers (such as Chrome's)", defaultValue: false
                input "notification", "capability.notification", title: "Select notification devices", multiple: true, required: false, submitOnChange: true
            }
        }
    }
}
def update_app_label(text){
    closeBoolQuestions()

    def failedSensorsList = state.disabledSensors ? state.disabledSensors.join(", ") : "None"
    def pauseVar = text ? text : state.disabledSensors && !state.disabledSensors.isEmpty() ? "FAILED SENSORS: ${failedSensorsList.join(", ")}" : "paused"

    // def pauseVar = state.failedSensors ? "FAILED SENSORS" : "paused"
    def batteryVar = "LOW BATTERY"
    def previousLabel = app.label // save current label

    if (state.paused || state.lowBattery || state.lowBatterySensor) {
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        while (app.label.contains(" $batteryVar ")) {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }

        if (enableinfo) log.info "original label = $app.label"

        if (state.paused) {
            app.updateLabel(previousLabel + ("<font color = 'red'> $pauseVar </font>")) // recreate label
        }
        else if (state.lowBattery || state.lowBatterySensor) {
            app.updateLabel(previousLabel + ("<font color = 'red'> $batteryVar </font>")) // recreate label
        }

        state.button_name = state.paused ? "resume" : "pause"
        if (enableinfo) log.info "button name is: $state.button_name new app label: ${app.label}"
    }
    else {
        state.button_name = "pause"
        if (enabledebug) log.debug "button name is: $state.button_name"
    }
    if (app.label.contains(pauseVar) && !state.paused) {
        app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>"))
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        if (enableinfo) log.info "new app label: ${app.label}"
    }
    if (app.label.contains(batteryVar) && (!state.lowBattery && !state.lowBatterySensor)) {
        app.updateLabel(app.label.minus("<font color = 'red'> $batteryVar </font>"))
        while (app.label.contains(" $batteryVar ")) {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }
        if (enableinfo) log.info "new app label: ${app.label}"
    }

    // when page is loaded and app is paused make sure page loading doesn't add "paused" several times
    if (app.label.contains(pauseVar) && state.paused) {
        //app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>" ))
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
            app.updateLabel(app.label.minus("<font color = 'red'>"))
            app.updateLabel(app.label.minus("</font>"))
        }
        app.updateLabel(app.label + ("<font color = 'red'> $pauseVar </font>")) // recreate label
    }
    if (app.label.contains(batteryVar) && (state.lowBattery || state.lowBatterySensor)) {
        //app.updateLabel(app.label.minus("<font color = 'red'> $batteryVar </font>" ))
        while (app.label.contains(" $batteryVar ")) {
            app.updateLabel(app.label.minus(" $batteryVar "))
            app.updateLabel(app.label.minus("<font color = 'red'>"))
            app.updateLabel(app.label.minus("</font>"))
        }
        app.updateLabel(app.label + ("<font color = 'red'> $batteryVar </font>")) // recreate label
    }
}

/* ############################### DEBUG TIMER* ################################ */
def check_logs_timer(){
    
    long now = now()

    if (state.lastCheckTimer == null || (now - state.lastCheckTimer) >= 60000) {

        state.lastCheckTimer = now

        long days = 30L
        def hours = 10
        def minutes = 30
        //the int data type is a 32-bit signed integer, which has a maximum value of 2,147,483,647. 
        // 30 * 24 * 60 * 60 * 1000, exceeds this maximum value, resulting in an integer overflow and thus a negative number.
    
        long longTerm = days * 24L * 60L * 60L * 1000L 
        long mediumTerm = hours * 60 * 60 * 1000
        long shortTerm = minutes * 60 * 1000
    
        boolean endDebug = (now - state.EnableDebugTime) >= shortTerm
        boolean endDescription = (now - state.enableDescriptionTime) >= longTerm
        boolean endWarning = (now - state.EnableWarningTime) >= mediumTerm
        boolean endTrace = (now - state.EnableTraceTime) >= mediumTerm

        if (enabledebug) {
            def message = [
                "<br>end debug  ? $endDebug",
                "<br>end descr  ? $endDescription",
                "<br>end warn   ? $endWarning",
                "<br>end trace  ? $endTrace",
                "<br>now - state.EnableDebugTime: ${now - state.EnableDebugTime}",
                "<br>now - state.enableDescriptionTime: ${now - state.enableDescriptionTime}",
                "<br>now - state.EnableWarningTime: ${now - state.EnableWarningTime}",
                "<br>now - state.EnableTraceTime: ${now - state.EnableTraceTime}",
                "<br>longTerm: $longTerm",
                "<br>mediumTerm: $mediumTerm",
                "<br>shortTerm: $shortTerm",
            ]
            log.debug message.join()
        }


        if (endDebug && enabledebug) disable_logging()
        if (endDescription && enableinfo) disable_description()
        if (endWarning && enablewarning) disable_warnings()
        if (endTrace && enabledebug) disable_trace()
    }
    else {
        if (enabledebug) log.trace "log timer already checked in the last 60 seconds"
    }
}

/* ############################### INITIALIZATION* ################################ */
def installed() {
    if (enabledebug) log.debug "Installed with settings: ${settings}"

    initialize()
}
def updated() {

    log.info "${app.name} updated with settings: $settings"

    unsubscribe()
    unschedule()
    initialize()
}
def initialize(){
    log.info "initializing"

    initialize_states()

    // state.neededThermostats = []

    if (enabledebug) log.debug "subscribing to events..."

    //subscribe(location, "mode", ChangedModeHandler) 
    subscribe(thermostat, "temperature", temperatureHandler)
    if (sensor) {
        int i = 0
        int s = sensor.size()
        for (s != 0; i < s; i++) {
            subscribe(sensor[i], "temperature", temperatureHandler)
        }
    }
    if (dimmer) {
        subscribe(dimmer, "level", dimmerHandler)
        if (enableinfo) log.info "subscribed $dimmer to dimmerHandler"
    }
    if (specialDimmer) {
        subscribe(specialDimmer, "level", specialDimmerHandler)
    }
    state.lastThermostatInput = state.lastThermostatInput ? state.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")

    subscribe(thermostat, "heatingSetpoint", setPointHandler)
    subscribe(thermostat, "coolingSetpoint", setPointHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)

    if (enableinfo) log.info "subscribed ${thermostat}'s coolingSetpoint to setPointHandler"
    if (enableinfo) log.info "subscribed ${thermostat}'s heatingSetpoint to setPointHandler"
    if (enableinfo) log.info "subscribed ${thermostat}'s thermostatMode to thermostatModeHandler"

    subscribe(location, "zwaveStatus", hubEventHandler)
    subscribe(location, "zigbeeStatus", hubEventHandler)
    subscribe(location, "systemStart", hubEventHandler) // manage bugs and hub crashes
    subscribe(location, "severeLoad", locationEventHandler)

    if (sync && thermostatB) {
        int i = 0
        int s = thermostatB.size()
        for (s != 0; i < s; i++) {
            subscribe(thermostatB[i], "heatingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "coolingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "thermostatMode", thermostatModeHandler)
            if (enableinfo) log.info "subscribed ${thermostatB[i]}'s thermostatMode to thermostatModeHandler"
            if (enableinfo) log.info "subscribed ${thermostatB[i]}'s heatingSetpoint to setPointHandler"
            if (enableinfo) log.info "subscribed ${thermostatB[i]}'s coolingSetpoint to setPointHandler"
        }
    }

    if (altThermostat) {
        subscribe(altThermostat, "heatingSetpoint", setPointHandler)
        subscribe(altThermostat, "coolingSetpoint", setPointHandler)
        subscribe(altThermostat, "thermostatMode", thermostatModeHandler)
    }

    subscribe(location, "mode", modeChangeHandler)

    if (windows && controlWindows) {
        if (windows.every{ element -> element.hasCapability("ContactSensor") })
        {
            subscribe(windows, "contact", contactHandler)
            subscribe(windows, "contact", windowsHandler)
            if (enableinfo) log.info "$windows subscribed to contactHandler()"
        }
    }
    if (nightModeButton) {
        subscribe(nightModeButton, "pushed", pushableButtonHandler)

    }
    if (buttonPause) {
        subscribe(buttonPause, "doubleTapped", doubleTapableButtonHandler)
        if (enableinfo) log.info "${buttonPause.join(", ")} subscribed to doubleTapableButtonHandler"
    }
    if (WindowsContact) {
        subscribe(WindowsContact, "contact", contactHandler)
        if (enableinfo) log.info "subscribed ${WindowsContact.join(", ")} to events"
    }
    if (motionSensors) {
        subscribe(motionSensors, "motion", motionHandler)
        if (enableinfo) log.info "subscribed ${motionSensors.join(", ")} to motion events"
    }

    if (polldevices) {
        schedule("0 0/2 * * * ?", Poll)
    }
    if (controlPowerConsumption || coolerControlPowerConsumption) {
        schedule("0 0/5 * * * ?", poll_power_meters)
    }

    if (celsius) {
        convert_db_to_celsius() // can run only once or after converted back to F
    }
    else {
        convert_db_to_fahrenheit() // will run only if was converted to celsius before
    }


    def hashTableJson = readFromFile("hash_table.txt")
    if (hashTableJson == null || hashTableJson.isEmpty()) {
        reset_db()
        if (enablewarning) log.warn "Hash table file was empty. Database has been reset and populated."
    }

    schedule("0 0/1 * * * ?", pre_start, [data: ["source": "schedule"]])

    resetBusy()

    if (enableinfo) log.info "END OF INITIALIZATION"

}

/* ############################### EVT HANDLERS* ################################# */
def modeChangeHandler(evt){
    if (enableinfo) log.info "$evt.name is now $evt.value"

    state.dontChekcThermostatStateCount = 0


    // if(enableinfo) log.info """
    // location.mode in restricted ? ${location.mode} in ${restricted} ? ---------------------
    // """
    if (location.mode in restricted) {
        // thermostat."${restrictedThermMode}"()

        set_multiple_thermostats_mode(restrictedThermMode, "location in restricted mode", null)

        altThermostat?."${restrictedThermMode}"()

        heater?.off() // make sure this one is off 
        cooler?.off() // same
        def fanCmd = keepFanOnInRestrictedMode ? "on" : "off"
        if (fan && fan?.currentValue("switch") != "fanCmd") {
            fan?."${fanCmd}()"
            if (enableinfo) log.info "$fan turned $fanCmd fe58"
        }
    }
    else {
        if (enabledebug) log.trace "location not in restricted mode, resuming normal operations"

        if (location.mode in windowsModes) {
            if (resetWindowsOverrideWithLocationModeChange) {
                if (!simpleModeIsActive()) {
                    if (enableinfo) log.info format_text("WINDOWS OVERRIDE RESET", "white", "darkblue")
                    state.openByApp = true;
                    state.closedByApp = true;
                }
            }
        }
        else if (closeWhenOutsideWindowsModes) {
            windows?.off()
            state.otherWindowsOpenByApp = false
        }
    }

    //pre_start()
}
def appButtonHandler(btn) {

    switch (btn) {
        case "pause": state.paused = !state.paused
            if (enablewarning) log.warn "state.paused = $state.paused"

            if (state.paused) {
                if (enableinfo) log.info "unsuscribing from events..."
                unsubscribe()
                if (enableinfo) log.info "unschedule()..."
                unschedule()
                break
            }
            else {
                updated()
                break
            }
        case "update":
            state.paused = false
            updated()
            break
        case "run":
            if (enabledebug) log.debug "Running pre_start('btn')"
            resetBusy()
            pauseExecution(500)
            if (!state.paused) pre_start("btn")
            break
        case "poll":
            Poll()
            poll_power_meters()
            break
        case "RESET":
            state.confirmed = "no"
            break
        case "reset_confirmed":
            state.confirmed = "yes"
            if (enabledebug) log.debug "**********************************RESETING DATABASE*****************************"
            reset_db()
            break
        case "no_reset":
            state.confirmed = "na"
            break
        case "reboot":
            reboot(true, max_reboots = 100, duration = 0, reboot_threshold_in_secs = 1, "btn")
    }
}
def contactHandler(evt){
    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "$evt.device is $evt.value"
        if (evt.value == "open") {
            state.lastContactOpenEvt = now()
        }
    }
    pre_start("contactHandler")
}
def motionHandler(evt){
    long start = now()
    if (enabledebug) log.debug "motionHandler: $evt.device returns ${evt.value}F"

    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        state.activeMotionCount = state.activeMotionCount ? state.activeMotionCount : 0
        if (evt.value == "active") {
            state.activeMotionCount += 1 // eventsSince() can be messy 
            state.lastMotionEvent = now() // initialized upon install or update
        }
        state.lastMotionEvent = state.lastMotionEvent == null ? now() : state.lastMotionEvent
        if (now() - state.lastMotionEvent > 30 * 1000) pre_start("motionHandler")
    }
    if (enabledebug) log.debug "motionHandler execution time: ${now() - start} ms"
}
def temperatureHandler(evt){
    long start = now()
    if (enabledebug) log.debug "temperatureHandler: $evt.device returns ${evt.value}F"


    if (!state.paused) {
        if (location.mode in restricted) {
            log.info "location in restricted mode, doing nothing"
            def critical = criticalcold ? criticalcold : 65
            if (get_inside_temperature() < critical) {
                state.override = false // cancel if it gets too cold
                state.antifreeze = true
            }
            return
        }
        state.lastTempEvent = state.lastTempEvent == null ? now() : state.lastTempEvent
        if (now() - state.lastTempEvent > 30 * 1000) pre_start("temperatureHandler")
    }
    if (enabledebug) log.debug "temperatureHandler execution time: ${now() - start} ms"
}
def dimmerHandler(evt){

    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }

        if (enablewarning) log.warn "new dimmer level is $evt.value method = $method && setpointSentByApp = $state.setpointSentByApp"

        // learning from user's input for the auto method
        learn(evt.value) // will also respond to thermostat inputs because it is ran before testing if it's set by the app or not

        if (state.setpointSentByApp) {
            if (enabledebug) log.trace "dimmer value set by this app"
        }
        else {
            userWants(evt.value.toInteger(), get_inside_temperature())
        }



        state.setpointSentByApp = false // always reset this variable after calling it

        //pre_start() // prevent feedback loops so both dimmer and thermostat set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def setPointHandler(evt){
    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enabledebug) log.trace "$evt.device $evt.name $evt.value"

        if (enabledebug) log.debug "sync ? $sync thermostatB: $thermostatB"

        if (sync && thermostatB) {
            def cmd = "set${evt.name.capitalize()}"
            int i = 0
            int s = thermostatB.size()

            def debug = [
                "thermostat = $evt.device",
                "evt.value = $evt.value",
                "evt.name = $evt.name",
                "${thermostat?.currentValue(evt.name) != "$evt.value"}",
                "thermostatB current set point: ${thermostatB[0].currentValue(evt.name)} = $evt.value",
                "true? ${thermostatB[0].currentValue(evt.name) == evt.value.toInteger()}",
                "any found with same current value: ${thermostatB?.any{it -> it.currentValue(evt.name) == evt.value.toInteger()}} ",
            ]

            if (enabledebug) log.debug debug_from_list(debug)

            if ("$evt.device" == "$thermostat") {
                if (enablewarning) log.warn "case ASP"
                for (s != 0; i < s; i++) {
                    thermostatB[i]."${cmd}"(evt.value)
                    if (enableinfo) log.info "${thermostatB[i]} $cmd $evt.value"
                }
            }
            if (thermostatB.find{ it.toString() == "$evt.device" })
            {
                if (enablewarning) log.warn "case BSP"
                state.setpointSentByApp = true
                resetSetByThisApp()
                contactsClosed = !contactsAreOpen()             
                boolean okToOff = evt.value == "off" ? check_contacts_delay(contactsClosed) : true
                if (okToOff) {

                    if (enableinfo) log.info "$thermostat $cmd $evt.value 7rgha"
                    // thermostat."${cmd}"(evt.value)
                    inside = get_inside_temperature()
                    set_target(
                        cmd,
                        evt.value,
                        inside,
                        outside = get_outside_temperature(),
                        motionActive = Active(),
                        doorsContactsAreOpen = doorsContactsAreOpen(),
                        contactsClosed = contactsClosed,
                        thermModes = get_thermostats_modes(),
                        humThres = get_humidity_threshold(inside)
                    "5df4grlgk")
                }
                //
            }
            //return // must not set state.setpointSentByApp back to false in this case
        }

        if (!state.setpointSentByApp) {
            if (enableinfo) log.info "new $evt.name is $evt.value -------------------------------------"
            def inside = get_inside_temperature()

            userWants(evt.value.toInteger(),
                inside)

            def currDim = !dimmer ? state.lastThermostatInput : get_dimmer_value()


            // this will be true only if thermostat is heating or cooling; therefore, 
            // dimmer won't be adjusted if off 
            // using state.lastNeed == "heat" / "cool" seemed to allow exceptions... UPDATE but we need it. Let's keep an eye on this... 
            boolean correspondingMode = (evt.name == "heatingSetpoint" && state.lastNeed == "heat") || (evt.name == "coolingSetpoint" && state.lastNeed == "cool")

            def debug = [
                "state.setpointSentByApp = $state.setpointSentByApp",
                "Current $dimmer value is $currDim",
                "state.lastThermostatInput = $state.lastThermostatInput",
                "state.lastNeed = $state.lastNeed   ",
                "evt.value = $evt.value   ",
            ]

            if (enabledebug) log.debug format_text("black", "white", debug_from_list(debug))

            boolean simpleModeActive = simpleModeIsActive()
            def outside = get_outside_temperature() 

            def target = get_target(simpleModeActive, get_inside_temperature(), outside)
            state.inside = state.inside != null ? state.inside : inside

            def thermModes = get_thermostats_modes()

            def needData = get_need(target, simpleModeActive, inside, outside, Active(), doorsOpen(), state.neededThermostats, thermModes, get_humidity_threshold(), "setPointHandler")
                  
            def need = needData[1]
            def cmd = need == "auto" ? "setThermostatSetpoint" : "set" + "${needData[0]}" + "ingSetpoint" // "Cool" or "Heat" with a capital letter

            // make sure the therm event is same as current need
            // as to not apply a value from a differentiated thermostat mode (heat set to 75 will modify coolingSP and then trigger an event)
            debug = [
                "method = $method",
                "correspondingMode = $correspondingMode",
                "currDim = $currDim",
                "evt.value = $currDim",
            ]

            if (enabledebug) log.debug debug_from_list(debug)

            if (correspondingMode && currDim != evt.value) // if and only if this is regarding the right operating mode, update the dimmer's value
            {
                if (method == "normal") {
                    //runIn(3, setDimmer, [data:evt.value.toInteger()])

                    set_dimmer(evt.value, "setPointHandler") // called only if it's not a value automatically set by the thermostat on the opposite operating mode (heatingSetpoint when cooling)

                    // every thermostat making sure to keep an offset between heating SP and cooling SP equal or superior to 2 degrees
                    //state.lastThermostatInput = evt.value //////done by set_dimmer()

                }
            }
            if (!correspondingMode) {
                if (enableinfo) log.info "not updating ${dimmer ? "dimmer" : "state.lastThermostatInput"} because this is $evt.name and current mode is $thermModes"
            }
            if (currDim == evt.value) {
                // if(enableinfo) log.info "${dimmer ? "dimmer" : "state.lastThermostatInput"} value ok (${dimmer ? '${get_dimmer_value()}' : "state.lastThermostatInput"} = "${evt.value}")
                if (enableinfo) log.info "${dimmer ? 'dimmer' : 'state.lastThermostatInput'} value ok (${dimmer ? get_dimmer_value() : 'state.lastThermostatInput'} = '${evt.value}')"

            }
        }
        else {
            if (enabledebug) log.trace "event generated by this app, doing nothing"
        }

        //pre_start() // prevent feedback loops so both dimmer and thermosta set points can be modified. Changes will be made on next scheduled loop or motion events
        state.lastSetPoint = evt.value
    }
    state.setpointSentByApp = false // always reset this static/class variable after calling it
}
def specialDimmerHandler(evt){

    if (enabledebug) log.trace "$evt.device set to $evt.value | NEW $simpleModeName Mode target temperature"
    app.updateSetting("specialTemp", [type: "number", value: "$evt.value"])
    pre_start("specialDimmerHandler")

}
def pushableButtonHandler(evt){
    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "BUTTON EVT $evt.device $evt.name $evt.value"

        if (evt.name == "pushed") {
            if (!ignoreTarget && !simpleModeSimplyIgnoresMotion) {
                set_multiple_thermostats_mode("off", "5rgklgu", null)
            }

            state.buttonPushed = !state.buttonPushed

            state.simpleModeOverrideResetDone = state.buttonPushed ? false : true

            def warning = [
                "allowWindowsInSimpleMode = $allowWindowsInSimpleMode",
                "state.simpleModeOverrideResetDone = $state.simpleModeOverrideResetDone",
            ]

            if (enablewarning) log.warn debug_from_list(warning)


            if (allowWindowsInSimpleMode && state.simpleModeOverrideResetDone == false) {
                if (enableinfo) log.info format_text("RESET WINDOWS OVERRIDE DUE TO ${simpleModeName} BEING ACTIVE", "white", "magenta")
                state.openByApp = true;
                state.closedByApp = true;
                state.simpleModeOverrideResetDone = true;
            }

            state.lastButtonEvent = state.buttonPushed ? now() : state.lastButtonEvent // time stamp when true only

            if (lightSignal && state.buttonPushed) {
                flashTheLight()
            }

            pre_start("pushableButtonHandler")
            return
        }
        pre_start("pushableButtonHandler")
    }
    else {
        if (enablewarning) log.warn "App is paused, button event was ignored"
    }
}
def doubleTapableButtonHandler(evt){

    if (location.mode in restricted) {
        if (enableinfo) log.info "location in restricted mode, doing nothing"
        return
    }
    if (enableinfo) log.info "BUTTON EVT $evt.device $evt.name $evt.value"

    if (evt.name == "doubleTapped") {
        state.paused = !state.paused 
        def message = state.paused ? "APP PAUSED BY DOUBLE TAP" : "APP RESUMED BY DOUBLE TAP"
        if (enablewarning) log.warn message
        if (buttonTimer && state.paused) {
            if (enableinfo) log.info "App will resume in $buttonTimer minutes"
            runIn(buttonTimer, updated)
        }
    }
    
}
def thermostatModeHandler(evt){

    state.dontChekcThermostatStateCount = 0

    if (location.mode in restricted) {
        if (enableinfo) log.info "location in restricted mode, doing nothing"
        return
    }

    if (evt.value == "auto" && autoOverride) {
        if (enableinfo) log.info "OVERRIDE REQUEST DETECTED"
        state.overrideTime = now()
        state.override = true
        return
    }
    else {

        state.override = false

    }

    if (!state.restricted && !state.paused) {
        if (enabledebug) log.debug """$evt.device $evt.name $evt.value
        sync ? $sync
thermostatB: $thermostatB

        """

    }
}
def outsideThresDimmerHandler(evt){
    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "*********** Outside threshold value is now: $evt.value ***********"
        //pre_start()
    }
}
def windowsHandler(evt){
    if (!state.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "$evt.device is $evt.value"
        boolean doorsContactsAreOpen = doorsOpen()

        state.closingCommand = state.closingCommand != null ? state.closingCommand : true

        if (evt.value == "open") {
            boolean openMore = !state.widerOpeningDone && state.insideTempHasIncreased

            if (!openMore) {
                state.lastOpeningTime = now()
            }
            state.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        }
        else if (evt.value == "closed" && state.closingCommand) {
            state.closingCommand = false // reset this boolean to false
            state.lastClosingTime = now() // we don't want this value to be reset at every device's wake up/refresh, hence 'state.closingCommand' boolean"
            state.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
        }
    }
}
def locationEventHandler(evt){
    state.severeLoad = state.severeLoad ? state.severeLoad : 0

    log.warning "$evt.description $evt.name evt.date event number:${state.severeLoad} (reboot after 5 events within 30 minutes)"

    if (evt.name == "severeLoad") {
        state.severeLoadTime = now()
        state.severeLoad += 1

        if (state.severeLoad > 5) {
            state.problemLogs += 'Hub had to reboot due to <b>$state.severeLoad severe load events</a>'
            state.severeLoad = 0
            reboot(true, max_reboots = 100, duration = 0, reboot_threshold_in_secs = 1, "locationEventHandler")

        }
    }

}
def hubEventHandler(evt){

    log.debug "$evt.device $evt.name $evt.value"

    if (evt.name == "systemStart") {
        runIn(20, initialize)
    }

    if (evt.name.contains("is offline")) {
        reboot(false, max_reboots = 100, duration = 0, reboot_threshold_in_secs = 1, "hubEventHandler") // reboot without delay when that happens. 
        return
    }


}
/* ################################# MASTER LOOP ################################# */

def pre_start(source){
    state.busy = state.busy == null ? true : state.busy

    // prevent stacking
    if (state.busy) {
        if (enablewarning || dev_mode) log.warn "$app.label is busy..."
        if (time_is_up(state.run_pre_start)) {
            state.stop = true
            checkIfRebootIsNeeded()
        }
    } else {

        // prevent overflow
    def interval = 30
        if (now() - state.run_pre_start < interval * 1000) {
            if (enablewarning) log.warn "master thread ran less than $interval seconds ago. Skipping..."
            if (enablewarning) log.warn "source: $source"
            return
        }
        else {
            state.busy = true
            runIn(1, master, [data: ["source": "pre_start runin(master) called from ${source}", "start": "${start}"], overwrite: true])
            runIn(30, forceReset)
        }
    }
}

def master(source){

    long start = now()

    state.run_pre_start = start
    state.stop = false

    if (state.paused) {
        log.debug "App paused ${state.pausedByApp ? 'due to internal error, check for defective sensors' : ''}"
        if (state.pausedByApp) check_inside_temp()
        return
    }

    if (state.buttonPushed != null && UseSimpleMode) {
        def status = state.buttonPushed ? "ACTIVE" : "INACTIVE"
        log.info format_text("$simpleModeName Mode $status", "white", "grey")
    }

    
    boolean contactsClosed = true
    boolean simpleModeActive = false
    boolean motionActive = true
    boolean doorsContactsAreOpen = false
    def thermModes
    def target
    def inside
    def outside
    def needData
    def need
    def neededThermostats
    def cmd
    def humThres
    boolean doNotUseMain = true
    boolean heatpumpConditionsTrue = true
    boolean dontSendThermostatModeCmd = true
    def currSP
    long s
    long blocktime = now()

    if (enablewarning) log.warn "MASTER INITIALIZATION block"
    try {


        if (enabledebug) "pre_start called by ${source}" // if param is passed from schedule(), it's a map.

        foolproof()

        state.lastThermostatInput = state.lastThermostatInput ? state.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")

        /********************** VARIABLES' DATA COLLECTION *************************/


        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking contactsAreOpen"
        try {
            if (!time_is_up(start)) {
                contactsClosed = !contactsAreOpen()
            }
            else {
                log.error format_text("contactsClosed check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            contactsClosed = false // default safety
            log.error "contactsClosed => $e"
        }
        if (enabledebug) log.trace "Result of contactsAreOpen: $contactsClosed execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking simpleModeIsActive"
        try {
            if (!time_is_up(start)) {
                simpleModeActive = simpleModeIsActive()
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            simpleModeActive = false // default safety
            log.error "simpleModeActive => $e"
        }
        if (enabledebug) log.trace "Result of simpleModeIsActive: $simpleModeActive execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking Active"
        try {
            if (!time_is_up(start)) {
                motionActive = Active()
            }
            else {
                log.error format_text("motionActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            motionActive = false // default safety
            log.error "motionActive => $e"
        }
        if (enabledebug) log.trace "Result of Active: $motionActive execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking doorsContactsAreOpen"
        try {
            if (!time_is_up(start)) {
                doorsContactsAreOpen = doorsOpen()
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            doorsContactsAreOpen = false // default safety
            log.error "doorsContactsAreOpen => $e"
        }
        if (enabledebug) log.trace "Result of doorsContactsAreOpen: $doorsContactsAreOpen execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting inside temperature from get_inside_temperature"
        try {
            if (!time_is_up(start)) {
                inside = get_inside_temperature()
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "get_inside_temperature => $e"
        }
        if (enabledebug) log.trace "Result of get_inside_temperature: $inside execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        try {
            if (!time_is_up(start)) {
                humThres = get_humidity_threshold(inside)
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "get_humidity_threshold() => $e"
        }
        if (enabledebug) log.trace "Updated humThres: $humThres execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting outside temperature from outsideTemp"
        try {
            if (!time_is_up(start)) {
                outside = outsideTemp.currentValue("temperature").toDouble()
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "outsideTemp => $e"
        }
        if (enabledebug) log.trace "Result of outsideTemp: $outside execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting target from get_target"
        try {
            if (!time_is_up(start)) {
                target = get_target(simpleModeActive, inside, outside)
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "get_target => $e"
            def m = [
                "<br> target: $target",
                "<br> simpleModeActive: $simpleModeActive",
                "<br> inside: $inside",
                "<br> outside: $outside",
                "<br> motionActive: $motionActive",
                "<br> doorsContactsAreOpen: $doorsContactsAreOpen",
                "<br> neededThermostats: $neededThermostats",
                "<br> thermModes: $thermModes",
            ]

            log.warn m.join()
            resetBusy()
            return
        }
        if (enabledebug) log.trace "Result of get_target: $target execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting needed thermostats from get_needed_thermosats"
        try {
            if (!time_is_up(start)) {
                neededThermostats = get_needed_thermosats(need)
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
            if (enablewarning) log.warn "neededThermostats ----------------------> $neededThermostats"
        } catch (Exception e) {
            log.error "neededThermostats => $e"
        }
        if (enabledebug) log.trace "Result of get_needed_thermosats: $neededThermostats execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking thermostats' current modes"
        try {
            if (!time_is_up(start)) {
                thermModes = get_thermostats_modes()
            }
            else {
                log.error format_text("simpleModeActive check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "thermModes => $e"
        }
        if (enabledebug) log.trace "Result of thermostat mode check: $thermModes execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Calculating need data from get_need"
        try {
            if (!time_is_up(start)) {
                needData = get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, neededThermostats, thermModes, humThres, "master")
            }
            else {
                log.error format_text("needData check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "get_need => $e"
            def m = [
                "<br> target: $target",
                "<br> simpleModeActive: $simpleModeActive",
                "<br> inside: $inside",
                "<br> outside: $outside",
                "<br> motionActive: $motionActive",
                "<br> doorsContactsAreOpen: $doorsContactsAreOpen",
                "<br> neededThermostats: $neededThermostats",
                "<br> thermModes: $thermModes",
            ]

            log.warn m.join()
            resetBusy()
            return
        }
        if (enabledebug) log.trace "Result of get_need: $needData execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting need"
        try {
            if (!time_is_up(start)) {
                need = needData[1]
            }
            else {
                log.error format_text("needData check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "needData => $e"
            log.error "need: $need"
        }
        if (enabledebug) log.trace "need = $need execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Creating cmd string"
        try {
            if (!time_is_up(start)) {
                cmd = "set" + "${needData[0]}" + "ingSetpoint" // "Cool" or "Heat" with a capital letter
            }
            else {
                log.error format_text("cmd set skipped due to time_is_up() = true", "white", "black")
            }
        } catch (Exception e) {
            log.error "cmd => $e"
        }
        if (enabledebug) log.trace "Cmd string: $cmd execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking doNotUseMain condition"
        try {
            if (!time_is_up(start)) {
                doNotUseMain = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes
            }
            else {
                log.error format_text("doNotUseMain check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "doNotUseMain => $e"
        }
        if (enabledebug) log.trace "Result of doNotUseMain condition: $doNotUseMain execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking heatpumpConditions"
        try {
            if (!time_is_up(start)) {
                heatpumpConditionsTrue = heatpump && outside < lowtemp && !useAllHeatSources ? true : useAllHeatSources && outside < evenLowertemp ? true : false
                heatpumpConditionsTrue = doNotUseMain ? true : useAllHeatSourcesWithMode && location.mode in allHeatModes ? false : heatpumpConditionsTrue
            }
            else {
                log.error format_text("heatpump check skipped due to time_is_up() = true", "white", "black")
                return
            }

        } catch (Exception e) {
            log.error "heatpumpConditionsTrue => $e"
        }
        if (enabledebug) log.trace "Result of heatpumpConditions check: $heatpumpConditionsTrue execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Checking dontSendThermostatModeCmd condition"
        try {
            if (!time_is_up(start)) {
                dontSendThermostatModeCmd = (dontSetThermModesInSimpleMode && simpleModeActive) || doNotSendAnyCoolHeatOffComm
            }
            else {
                log.error format_text("dontSendThermostatModeCmd check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "dontSendThermostatModeCmd => $e"
        }
        if (enabledebug) log.trace "Result of dontSendThermostatModeCmd condition: $dontSendThermostatModeCmd execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        if (enabledebug) log.trace "Getting current setpoint"
        try {
            if (!time_is_up(start)) {
                currSP = [thermostat?.currentValue("thermostatSetpoint").toInteger()]
            }
            else {
                log.error format_text("currSP check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "currSP => $e"
        }
        if (enabledebug) log.trace "Current setpoint: $currSP execution time: ${now() -s} ms"

        if (enabledebug) s = now()
        try {
            if (!time_is_up(start)) {
                if (neededThermostats[0]) {
                    if (enabledebug) log.trace "Adding needed thermostats' setpoints"
                    if (enablewarning) if (enabledebug) log.warn "neededThermostats => $neededThermostats"
                    currSP += neededThermostats?.collect{ it?.currentValue("thermostatSetpoint").toInteger() }
                }
            }
            else {
                log.error format_text("neededThermostats currSP check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "currSP += neededThermostats() => $e"
        }
        if (enabledebug) log.trace "Updated setpoints: $currSP execution time: ${now() -s} ms"

        /*********************** ANTI FREEZE SAFETY TEST *************************/
        try {
            inside = inside ? inside : get_inside_temperature()
            if (antifreeze(inside, simpleModeActive, contactsClosed)) {
                state.busy = false
                return
            }
        } catch (Exception e) {
            log.error "antifreeze => $e"
        }


        /********************** UPDATE thermModes values *************************/
        if (differentiateThermostatsHeatCool) {
            if (!time_is_up(start)) {
                thermModes = get_thermostats_modes()
            }
            else {
                log.error format_text("thermModes check skipped due to time_is_up() = true", "white", "black")
                return
            }
        }

        /********************** AUTO OVERRIDE BUSY FALSE *************************/
        try {
            if (auto_override(inside, need, target, cmd, humThres, contactsAreOpen)) {
                state.busy = false
                return
            }
        } catch (Exception e) {
            log.error "auto_override() => $e"
        }


        /***************************** FAN CIRCULATE *****************************/
        try {
            if (!time_is_up(start)) {
                fanCirculateManagement(need, target, inside, contactsClosed, motionActive, thermModes)
            }
            else {
                log.error format_text("fanCirculateManagement currSP check skipped due to time_is_up() = true", "white", "black")
                return
            }
        } catch (Exception e) {
            log.error "fanCirculateManagement() => $e"
        }
    }
    catch (Exception e) {
        log.error "'pre_start' failed!!! => $e APPLICATION IS NOT RUNNING!"
        state.busy = false
        return
    }
    if (enablewarning) log.warn "INITIALIZATION took: ${(now() - blocktime)/1000} seconds"

    boolean skip = false
    // CONTACTS OPEN
    try {
        if (!time_is_up(start)) {
            if (!contactsClosed && need == "off") {
                are_open = WindowsContact?.findAll{ it -> it.currentValue('contact') == 'open' }
                log.debug "****************************SOME CONTACTS ARE OPEN : ${are_open.join(', ')}**************************************"
                turn_off_thermostats(need, inside, thermModes, are_open.size() > 0) // manages user define delay
            }
            else if (motionActive) {
                set_multiple_thermostats_mode(need, "first send", null)
            }
        }
    }
    catch (Exception e) {
        log.error "turn_off_thermostats with contacts open failed: $e"
    }

    if (enablewarning) log.warn"LOG TIMER CHECK"
    blocktime = now()
    try {
        if (!time_is_up(start)) check_logs_timer()
        if (enablewarning) log.warn "CHECK LOG TIMER OFF"
    } catch (Exception e) {
        log.error "check_logs_timer ==> $e"
    }
    if (enablewarning) log.warn "LOG TIMER CHECK took: ${(now() - blocktime)/1000} seconds"

    /********************** VERIFY HEATPUMP AND POWER USAGE CONDITIONS (HEATER OR COOLER)*************************/

    if (!time_is_up(start)) {
        if (enablewarning) log.warn "powerManagement block"
        blocktime = now()
        try {
            if (enablewarning) log.warn "neededThermostats ================ $neededThermostats";

            currentOperatingNeed = need == "cool" ? "cooling" : need == "heat" ? "heating" : need == "off" ? "idle" : "ERROR"
            if (currentOperatingNeed == "ERROR") {
                log.error "currentOperatingNeed = $currentOperatingNeed"
                return false
            }

            opStateOk = operatingStateOk(contactsClosed, doorsContactsAreOpen, currentOperatingState, currentOperatingNeed)

            powerManagement(inside, outside, need, target, cmd, contactsClosed, doorsContactsAreOpen, motionActive, heatpumpConditionsTrue, dontSendThermostatModeCmd, currSP, neededThermostats, thermModes, opStateOk, humThres)

        } catch (Exception e) {
            log.error "powerManagement() => $e"
        }
        if (enablewarning) log.warn "powerManagement block took: ${(now() - blocktime)/1000} seconds"
    }

    // VIRTUAL THERMOSTAT

    if (!time_is_up(start)) {
        if (enablewarning) log.warn "VIRTUAL THERMOSTAT block"
        blocktime = now()
        try {
            virtualThermostat(need, target) // redundancy due to return statement above
        } catch (Exception e) {
            log.error "virtualThermostat => $e"
        }
        if (enablewarning) log.warn "VIRTUAL THERMOSTAT block took: ${(now() - blocktime)/1000} seconds"
    }
    else {
        log.error format_text("VIRTUAL THERMOSTAT skipped due to time_is_up() = true", "white", "black")
    }

    checkIfRebootIsNeeded()

    state.busy = false

    unschedule(forceReset) // Cancel if finished normally


}

/* ################################# OPERATIONS ################################# */

def forceReset() {
    if (enablewarning) format_text("Force resetting app state due to timeout", "black", "red")
    state.busy = false
    state.stop = true
}

def checkIfRebootIsNeeded(){

    float duration = (now() - state.run_pre_start) / 1000
    def initialize_threshold = 25.0
    def max_reboots = 1
    def reboot_threshold_in_secs = 150.0
    def reinit_limit = 3
    def delay_between_reboots = 6 * 60 * 60 * 1000 // delay between reboots in hours

    if (enablewarning || duration > 6.0 || is_dev_app()) {
        log.warn "Main Loop took ${duration} seconds to execute..."

        if (duration > 120.0) {
            reboot(true, max_reboots, duration, reboot_threshold_in_secs, "checkIfRebootIsNeeded 54dhjk4")
        }
        if (duration > 20.0) {
            forceReset()

        }


    }
    // Check if app needs to be reinitialized or hub needs to be rebooted
    try {


        // Reset reboot counter if it's been more than delay_between_reboots (in hours) duration since last reboot
        state.lastRebootTime = state.lastRebootTime == null ? now() : state.lastRebootTime

        if (now() - state.lastRebootTime < delay_between_reboots) {
            state.numberOfReinit = 0
        }

        // Reinitialize if duration is between thresholds
        if (duration > initialize_threshold && duration < reboot_threshold_in_secs) {
            state.numberOfReinit = state.numberOfReinit == null ? 0 : state.numberOfReinit
            state.numberOfReinit += 1
                def now = new Date()
                def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                def formattedDate = dateFormat.format(now)
            state.problemLogs += "App re-initialized $state.numberOfReinit times. Last time was @ ${formattedDate}"
            if (state.numberOfReinit < reinit_limit) {
                initialize()
                return
            }
        }


        if (duration >= reboot_threshold_in_secs || state.numberOfReinit >= reinit_limit) {
            reboot(true, max_reboots, duration, reboot_threshold_in_secs, "checkIfRebootIsNeeded fjii78ty2")
        }


    } catch (Exception error) {
        log.error "Initialize and reboot threshold management error => $error"
    }
}

def reboot(override, max_reboots, duration, reboot_threshold_in_secs, source) {

    if (no_reboot && !override) {
        log.debug "reboot() called from $source"
        log.warn format_text("Automatic reboots are disabled in this instance. Not sending reboot command...", "blue", "white")
        return "Reboot skipped: Automatic reboots are disabled"
    }

    return format_text("NO REBOOT - FEATURE STILL IN DEVELOPMENT", "teal", "darkblue")

    state.numberOfReboots = state.numberOfReboots == null ? 0 : state.numberOfReboots
    state.lastRebootTime = state.lastRebootTime == null ? now() : state.lastRebootTime

    if (state.numberOfReboots <= max_reboots) {
        state.lastRebootTime = now()

        try {
                def mainHub = location.hub
                def mainIp = mainHub.localIP
            log.debug "Main Hub IP Address: ${mainIp}"

            // Get the list of all hub IPs (main hub + other hubs)
            def allHubIps = [mainIp] + (other_hubs ? validateAndFormatIPs(other_hubs) : [])
            log.debug "All Hub IPs to reboot: ${allHubIps}"


            if (now() - state.lastRebootTime < 60 * 60 * 1000 || override) {
                unschedule()
                unsubscribe() // temporarily stop all instances
                subscribe(location, "systemStart", hubEventHandler)

                log.warn "-----------------${app.label} is REBOOTING ${location} and ${allHubIps.size() - 1} other hub(s) ---------------------- "
                state.severeLoad = state.severeLoad ?: 0
                    def text = state.severeLoad >= 5 ? "REBOOTING THE HUBS DUE TO SEVERE CPU LOAD" : "NOW REBOOTING THE HUBS"
                log.warn "state.severeLoad = $state.severeLoad"
                if (state.severeLoad >= 5) {
                    state.severeLoad = 0
                }

                state.numberOfReboots += 1
                    def now = new Date()
                    def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    def formattedDate = dateFormat.format(now)
                state.problemLogs += "hubs rebooted $state.numberOfReboots times. Last time was @ ${formattedDate}"
                log.warn format_text(text, "white", "red")

                if (state.numberOfReboots >= 5) {
                    // Reboot all hubs
                    allHubIps.each {
                        ip ->
                            try {
                            log.info "Attempting to reboot hub at IP: ${ip}"
                                    def result = runCmd(ip, "8080", "/hub/reboot", override)
                            log.info "Reboot attempt result for ${ip}: ${result}"
                        } catch (error) {
                            log.error "Failed to reboot hub at IP ${ip}: $error"
                        }
                    }
                } else {
                    runIn(3600, reset_nb_reboots)
                }
            }
        }
        catch (Exception error) {
            log.error "reboot => $error"
        }
    }
}


def runCmd(String ip, String port, String path, override) {
    if (no_reboot && !override) {
        log.warn format_text("Automatic reboots are disabled in this instance. Not sending reboot command...", "blue", "white")
        return "Reboot skipped: Automatic reboots are disabled"
    }

    state.numberOfReinit = 0
    log.warn format_text("SENDING REBOOT CMD NOW!", "white", "red")

    return

    state.lastRebootTime = now()

    try {
        def uri = "http://${ip}:${port}${path}"
        log.debug "POST: $uri"

        def reqParams = [
            uri: uri,
            timeout: 30
        ]

        try {
            httpPost(reqParams) {
                response ->
                    log.debug "HTTP Response: ${response.status}"
                return "Reboot command sent. HTTP Response: ${response.status}"
            }
        } catch (Exception e) {
            log.error "HTTP POST failed: ${e}"
            return "Failed to send reboot command: ${e.message}"
        }
    } catch (Exception e) {
        log.error "runCmd => ${e}"
        return "Error in runCmd: ${e.message}"
    }
}


def resetBusy(){
    if (state.busy) {
        state.busy = false
        state.stop = true
        log.debug "state.busy reset to false"
    }
}
def reset_nb_reboots(){
    state.numberOfReboots = 0
}
def foolproof(){
    if (offrequiredbyuser && fanCirculateAlways) // fool proof, these two options must never be true together, fancirculate takes precedence
    {
        app.updateSetting("offrequiredbyuser", [type: "bool", value: false])
    }
}
def auto_override(inside, need, target, cmd, humThres, contactsAreOpen){
    //state.override = true // -> uncomment to test this function
    if (state.override) {
        def overrideDur = overrideDuration != null ? overrideDuration : 0
        def timeLimit = overrideDur * 60 * 60 * 1000
        def timeStamp = state.overrideTime

        if (overrideDur != 0 && overrideDur != null) {
            if ((now() - timeStamp) > timeLimit) {
                if (enablewarning) log.warn "END OF OVERRIDE"
                state.override = false
                if (!fanCirculateAlways) {

                    if (enableinfo) log.info "thermostat off 54erg5"
                    turn_off_thermostats(need, inside, thermModes, contactsAreOpen)
                    state.offAttempt = now()

                }
                else {
                    if (enablewarning) log.warn "END OF AUTO OVERRIDE - setting last target"
                    cmd = "set${need != "off" ? "${ need$ }ingSetpoint" : "${ state.lastNeed.capitalize() }ingSetpoint"}"
                    set_target(cmd,
                        target,
                        inside,
                        outside = get_outside_temperature(),
                        motionActive = Active(),
                        doorsContactsAreOpen = doorsContactsAreOpen(),
                        contactsClosed = !contactsAreOpen(),
                        thermModes = get_thermostats_modes(),
                        humThres,
                        "END OF AUTO OVERRIDE")

                }
                return false
            }
            else {
                if (enablewarning) log.warn "OVERRIDE - AUTO MODE - remaining time: ${get_thermostat_that_must_remain_off(overrideDur, state.overrideTime)}"
                def critical = criticalcold ? criticalcold : 65
                if (inside < critical) {
                    state.override = false // cancel if it gets too cold
                    state.antifreeze = true
                }
                return true
            }
        }
        else {
            if (enablewarning) log.warn "OVERRIDE - APP PAUSED DUE TO AUTO MODE (no time limit)"
            return true
        }
    }
}
def powerManagement(inside,
    outside,
    need,
    target,
    cmd,
    contactsClosed,
    doorsContactsAreOpen,
    motionActive,
    heatpumpConditionsTrue,
    dontSendThermostatModeCmd,
    currSP,
    neededThermostats,
    thermModes,
    opStateOk,
    humThres){

    log.warn "**************** CONSISTENCY CHECK **************************"

    if (enabledebug) {
    def msg = [
            "<br>inside:$inside",
            "<br>outside:$outside",
            "<br>need:$need",
            "<br>target:$target",
            "<br>cmd:$cmd",
            "<br>contactsClosed:$contactsClosed",
            "<br>doorsContactsAreOpen:$doorsContactsAreOpen",
            "<br>motionActive:$motionActive",
            "<br>heatpumpConditionsTrue:$heatpumpConditionsTrue",
            "<br>dontSendThermostatModeCmd:$dontSendThermostatModeCmd",
            "<br>currSP:$currSP",
            "<br>neededThermostats:$neededThermostats",
            "<br>thermModes:$thermModes"
        ]

        log.trace msg.join()

        if (pw) log.trace "$pw power meter returns ${pw?.currentValue("power")} Watts"
    }

    if (!state.override) {        

        def currentOperatingNeed
        def currentOperatingState
        def currentOperatingStates
        boolean allOk
        double swing
        double undesirableOffset
        double thermostatTemp
        boolean thermTempTooCloseToCoolTargetdWhileInsideNotGood
        boolean thermTempTooCloseToHeatTargetdWhileInsideNotGood
        def thermostatTempProblem
        boolean thermTempDiscrepancy
        def efficiencyOffset
        boolean coolerNotEfficientEnough
        boolean boost
        boolean forceCommand
        boolean thermostatModeNotOk
        boolean currSpNotOk
        def fanMode
        boolean fanModeNotON
        def m
        def delayBtwMessages
        def timeBeforeNewtOverrideBigMessage
        def timeUnit
        def timeDisplay
        def temporarySetpoint
        def currCSP
        def currHSP

        state.reCheckStateCount = 100

        // HEAT PUMP OPTIMIZATION
        try {
            checkHeatPump(need, inside, thermModes, contactsClosed)
        }
        catch (Exception e) {
            log.error "checkHeatPump => $e"
        }
        // POWER CONSISTENCY TEST
        try {
            powerConsistency(need, target, inside, cmd, contactsClosed, doorsContactsAreOpen, motionActive, thermModes, opStateOk, currentOperatingNeed, humThres)
        }
        catch (Exception e) {
            log.error "powerConsistency => $e"
        }
        // FORCE COMMAND
        try {
            swing = UserSwing ? UserSwing.toDouble() : 0.5 // swing is the target amplitude set at the level of the thermostat directly (hardware setting) - user must specify, default is 0.5
            undesirableOffset = 2
            // the problem is when the thermostat returns a temp that is too close to the target temp while the alt sensor is still too far from it

            thermostatTemp = get_inside_temperature()
            //boolean insideTempNotOk = need == "cool" ? inside > target + swing : need == "heat" ? inside < target - swing : false 
            // if need = cool and thermostatTemp >= target + swing that means the thermostat will stop cooling
            // if need = heat and thermostatTemp <= target - swing that means the thermostat will stop heating
            // so, if that happens while inside temperature is still far beyond or below the target temperature (+/- default swing), then
            // we want the app to increase the set point (if need is heat) or decrease it (if need is cool) 
            // so as to force the unit to continue to work until alternate sensors array measures an average inside temp that matches the target (+/- swing)
            thermTempTooCloseToCoolTargetdWhileInsideNotGood = thermostatTemp == target && inside >= target && !opStateOk
            thermTempTooCloseToHeatTargetdWhileInsideNotGood = thermostatTemp == target && inside <= target && !opStateOk
            thermostatTempProblem = (need == "cool" && thermTempTooCloseToCoolTargetdWhileInsideNotGood) || (need == "heat" && thermTempTooCloseToHeatTargetdWhileInsideNotGood)
            thermTempDiscrepancy = manageThermDiscrepancy && sensor && thermostatTempProblem && contactsClosed


            // check cooler performance and turn thermostat back on (override preferCooler bool) if needed 
            state.coolerTurnedOnTimeStamp = state.coolerTurnedOnTimeStamp != null ? state.coolerTurnedOnTimeStamp : 31 * 60 * 1000
            efficiencyOffset = 2
            coolerNotEfficientEnough = efficiencyOverride && preferCooler && (now() - state.coolerTurnedOnTimeStamp) > 30 * 60 * 1000 && inside >= target + efficiencyOffset
            boost = userBoostOffset && inside >= target + userBoostOffset

            if (enablewarning) if (coolerNotEfficientEnough && need == "cool") if (enablewarning) log.warn format_text("$cooler not efficient enough, turning on $thermostat", "red", "white")

            if (need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)) // NO OVERRIDE WHEN preferCooler and need == "cool"
            {
                if (enabledebug) log.debug "EFFICIENCY TEST"
                checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside, contactsClosed)
            }
            else {
                if (enablewarning && !contactsClosed) log.warn format_text("contactsClosed = $contactsClosed", "white", "red")

                if (manageThermDiscrepancy && thermTempDiscrepancy && contactsClosed) {
                    state.setPointOverride = true // avoids modifying target values (in setDimmer) and prevents the app from running other normal operations

                    m = ""
                    delayBtwMessages = 5 * 60000
                    state.lastSetpointMessage = state.lastSetpointMessage == null ? state.lastSetpointMessage : now()
                    timeBeforeNewtOverrideBigMessage = (delayBtwMessages - (now() - state.lastSetpointMessage)) / 1000 / 60
                    timeBeforeNewtOverrideBigMessage = timeBeforeNewtOverrideBigMessage.toDouble().round(2)
                    if ((now() - state.lastSetpointMessage) > delayBtwMessages) {
                        m = "SET POINT OVERRIDE - make sure your main thermostat is not too close to a window. If so, this app will attempt to keep your room at your target temperature ($target) by temporarily changing setpoints on your thermostat. This should not be affecting your input values (your target temperature)"
                        state.lastSetpointMessage = now()
                    }
                    else {
                        timeUnit = timeBeforeNewtOverrideBigMessage < 1 ? "seconds" : timeBeforeNewtOverrideBigMessage >= 2 ? "minutes" : "minute"
                        timeDisplay = timeBeforeNewtOverrideBigMessage < 1 ? timeBeforeNewtOverrideBigMessage * 100 : timeBeforeNewtOverrideBigMessage
                        m = "SET POINT OVERRIDE (detailed description in ${timeDisplay} ${timeUnit})"
                    }

                    if (enablewarning) log.warn format_text(m, "red", "white")

                    temporarySetpoint = need == "cool" ? 62 : need == "heat" ? 85 : 72 // 72 if by any chance this went wrong

                    currCSP = thermostat.currentValue("coolingSetpoint").toInteger()
                    currHSP = thermostat.currentValue("heatingSetpoint").toInteger()
                    boolean notSet = need == "cool" && currCSP != temporarySetpoint.toInteger() || need == "heat" && currHSP != temporarySetpoint.toInteger()


                    if (notSet) {

                        if (enabledebug) log.trace "setting $thermostat to $need"

                        if (heatpumpConditionsTrue && need != "off") {
                            if (enableinfo) log.info "$thermostat stays off due to heatpump and cold temp outside"
                        }
                        else {
                            if (enablewarning) log.warn "$thermostat $cmd to temporarySetpoint $temporarySetpoint 478r6gh"
                            state.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by set_dimmer() method. 
                            runIn(3, resetSetByThisApp)

                            set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, "temporarysetpoint")

                        }

                        if (need == "cool") // prevent thermostat firmware from circling down its setpoints
                        {
                            state.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by set_dimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setHeatingSetpoint(temporarySetpoint - 2)
                            if (enableinfo) log.info "$thermostat heatingsetpoint set to ${temporarySetpoint-2} to prevent circling down SP's"
                        }
                        else if (need == "heat") // prevent thermostat firmware from circling down its setpoints
                        {
                            state.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by set_dimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setCoolingSetpoint(temporarySetpoint + 2)
                            if (enableinfo) log.info "$thermostat coolingSetpoint set to ${temporarySetpoint+2} to prevent circling down SP's"
                        }

                        state.lastSetTime = now()
                        return
                    }
                }
                else if (!thermTempDiscrepancy && state.setPointOverride) {
                    state.setPointOverride = false // if this line is read, then setpoint override is no longer needed
                    if (manageThermDiscrepancy && enabledebug) log.trace  format_text("END OF SET POINT OVERRIDE - BACK TO NORMAL OPERATION", "white", "grey")
                }

                if (enabledebug) log.debug "forceCommand ? $forceCommand state.forceAttempts = $state.forceAttempts | abs(inside-target) = ${Math.abs(inside-target).round(2)}"
            }
            /****************END OF CONSISTENCY OR EFFICIENCY TESTS AND EMERGENCY HEAT/COLD DUE TO A POSSIBLE BADLY LOCATED THERMOSTAT*************************/

            /****************NORMAL EVALUATION WITH POSSIBLE NEED FOR REDUNDENT FORCED COMMANDS (possibly needed due to bad Z-Wave mesh)******/
            state.forceLimit = Math.abs(inside - target) > 5 ? 20 : 5 // higher amount of attempts if bigger discrepancy         
            state.forceAttempts = state.forceAttempts != null ? state.forceAttempts : 0
            forceCommand = state.forceAttempts < state.forceLimit ? true : false
            forceCommand = need in ["cool", "heat"] && Math.abs(inside - target) > 3 ? true : false // 
            forceCommand = Math.abs(inside - target) >= 5 ? true : (forceCommand ? true : false) // counter ignored if forceCmd user decision is true and temp discrepancy too high: continue trying until temp is ok
            forceCommand = !opStateOk ? true : forceCommand // !opStateOk supercedes all other conditions
            forceCommand = contactsClosed && !doorsContactsAreOpen ? forceCommand : false // don't use this method when contacts are open, even door contacts

            thermostatModeNotOk = thermModes.any{ it -> it != need }
            currSpNotOk = currSP.any{ it -> it != target }

            // if forececommand true and need is not off make sure we're not under heat pump cold conditions 
            forceCommand = forceCommand && (need != "off" || !offrequiredbyuser) ? !heatpumpConditionsTrue : forceCommand // if need = off then apply forcecommand functions to make sure to turn it off

        }
        catch (Exception e) {
            log.error "forceCommand => {e}"
        }


        // CONSISTENCY OR EFFICIENCY TESTS
        try {
            if (enablewarning) log.warn "dontSendThermostatModeCmd = $dontSendThermostatModeCmd"

            if (!state.setPointOverride && (thermostatModeNotOk || forceCommand || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) && contactsClosed) {
                state.dontChekcThermostatStateCount = state.dontChekcThermostatStateCount == null ? 1 : state.dontChekcThermostatStateCount
                state.dontChekcThermostatStateCount += 1
                if (forceCommand && opStateOk) { if (enabledebug) log.debug "FORCING CMD TO DEVICE BECAUSE temperature difference is TOO HIGH" }
                if (forceCommand && !opStateOk && !thermTempDiscrepancy) { if (enabledebug) log.debug "FORCING CMD TO DEVICE BECAUSE current operating state is INCONSISTENT" }

                state.forceAttempts += 1
                if (state.forceAttempts >= forceLimit) { runIn(1800, resetCmdForce) } // after 5 attempts, stop and retry in half an hour to prevent z-wave cmds overflow onto the device

                //state.lastSetTime =  5 * 60 * 1000 + 1 // for TESTS ONLY
                if (enabledebug) {
                        def msg = [
                        "<br>preferCooler ? $preferCooler && outside < preferCoolerLimitTemperature =  $outside < $preferCoolerLimitTemperature",
                        "<br>need != 'off' || forceCommand || (need == 'off' && (sensor || offrequiredbyuser)) = ${need != 'off' || forceCommand || (need == 'off' && (sensor || offrequiredbyuser))}",
                        "<br>need == 'cool' && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature) = ${need == 'cool' && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)}",
                        "<br>thermModes = $thermModes",
                        "<br>need = $need",
                        "<br>thermostatModeNotOk = ${thermostatModeNotOk}",
                        "<br>state.dontChekcThermostatStateCount: $state.dontChekcThermostatStateCount"
                    ]
                    log.debug msg.join()
                }

                if (enablewarning || thermostatModeNotOk) {
                    if (enablewarning) log.warn "thermostatModeNotOk ===> $thermostatModeNotOk"
                    if (enablewarning) log.warn "overrideThermostatModeCheckBeforeSendingCmd ===> $overrideThermostatModeCheckBeforeSendingCmd"
                    if (enablewarning) log.warn "state.dontChekcThermostatStateCount < state.reCheckStateCount ===> $state.dontChekcThermostatStateCount < state.reCheckStateCount"
                }

                if ((forceCommand || need == "off") && (sensor || offrequiredbyuser || !contactsClosed)) {



                    if ((!opStateOk || (now() - state.lastSetTime) > 5 * 60 * 1000) || need == "off" || forceCommand) {
                        state.coolerTurnedOnTimeStamp = state.coolerTurnedOnTimeStamp != null ? state.coolerTurnedOnTimeStamp : 31 * 60 * 1000

                        if (need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)) {
                            checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside, contactsClosed)
                        }
                        else {
                            if ((thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount) || thermostat.currentValue("thermostatFanMode") != "auto") && need == "off" && offrequiredbyuser) {
                                state.dontChekcThermostatStateCount += 1

                                if (enableinfo) log.info "Sending off command to thermostat 4felf65"
                                turn_off_thermostats(need, inside, thermModes, !contactsClosed)
                                set_multiple_thermostats_fan_mode("auto", "fan auto when thermostat off 4felf65")

                            }
                            // if fanCirculateAlways is true, offrequiredbyuser will automatically be set to false in pre_start and/or within dynamic settings pages
                            else if ((thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) && (!fanCirculateAlways || (need != "off" || !offrequiredbyuser))) {
                                state.dontChekcThermostatStateCount += 1

                                if (heatpumpConditionsTrue && need != "off") {
                                    if (enableinfo) log.info "heatpumpConditionsTrue 5z4rg4"
                                }
                                else if (!thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) {
                                    state.dontChekcThermostatStateCount += 1
                                    if (!dontSendThermostatModeCmd) {
                                        set_multiple_thermostats_mode(need, "checkthermstate force command", null)   // do not modify string "checkthermstate force command" it serves as a test at set_thermostat_mode()
                                    }
                                    else {
                                        if (enabledebug) log.trace "dontSendThermostatModeCmd true"
                                    }

                                }
                                else {
                                    if (enabledebug) log.trace "thermostat already set to $need etgh4ze5"
                                }

                            }
                            else if (!thermostatModeNotOk) {
                                if (enableinfo) log.info "$thermostat already set to $need 5z4gth"
                            }
                            else if (need == "off" && fanCirculateAlways) {
                                if (!motionActive && alwaysButNotWhenPowerSaving) {
                                    if (thermModes.any{ it -> it != "off" } || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount))
                                    {
                                        state.dontChekcThermostatStateCount += 1
                                        set_multiple_thermostats_fan_mode("auto", "fanCirculateAlways = true")

                                        if (enableinfo) log.info "thermostat off 4n2r4zk"
                                        turn_off_thermostats(need, inside, thermModes, !contactsClosed)

                                    }
                                }
                                else {
                                    if (enableinfo) log.info "fancriculateAlways is true, so not turning off the thermostat. sending fanonly() instead"

                                    if (enableinfo) log.info "thermostat off 96th34z"
                                    turn_off_thermostats(need, inside, thermModes, !contactsClosed) // needed to prevent thermostat staying in "cool" or "heat" mod, thermModese
                                    if (enablewarning) log.warn "fan mode on"
                                    set_multiple_thermostats_fan_mode("on", "fanOn when thermostat off 96th34z")

                                }
                            }
                        }

                        state.lastSetTime = now()

                        if (need in ["cool", "heat"]) {
                            state.lastSetTime = now() // prevent switching from heat to cool too frequently 
                            //during conditions that might have been unacounted for, like during shoulder season
                        }

                        if (enabledebug) log.debug "THERMOSTAT SET TO $need mode (587gf)"
                    }
                    else if ((now() - state.lastSetTime) < 30 * 60 * 1000) {
                        if (enabledebug) log.debug "THERMOSTAT CMD NOT SENT due to the fact that a cmd was already sent less than 30 minutes ago"
                    }

                    if (need == "off") {
                        state.offAttempt = now()

                    }
                }
                else {
                    log.debug "THERMOSTAT stays in $thermModes mode"
                }

            }
            else if ((need != "off" || !offrequiredbyuser) && contactsClosed) {
                if (enableinfo) log.info "$thermostat already set to $need 8ryjyuz"
            }
        } catch (Exception e) {
            log.error "forceCommand => $e"
        }

        // FAN MANAGEMENT
        try {
            if (!state.setPointOverride) {
                fanMode = [thermostat.currentValue("thermostatFanMode")]

                if (enabledebug) log.debug "neededThermostats => > > > $neededThermostats"

                fanMode += neededThermostats.collect{ it -> it?.currentValue("thermostatFanMode") }

                fanModeNotON = fanMode.any{ it -> it != "on" }

                if (enabledebug) {
                    log.trace "currSpNotOk => $currSpNotOk"
                    log.trace "thermostatModeNotOk => $thermostatModeNotOk"
                    log.trace "thermTempDiscrepancy => $thermTempDiscrepancy"
                    log.trace "contactsClosed => $contactsClosed"
                }

                if ((currSpNotOk || thermostatModeNotOk) && !thermTempDiscrepancy && contactsClosed) {
                    if (enablewarning) log.warn "need = $need last need = $state.lastNeed **************** "
                    cmd = need == "off" ? "set" + state.lastNeed.capitalize() + "ingSetpoint" : cmd // restore the last need if need = off
                    if (enablewarning) log.warn "cmd = $cmd ${state.lastNeed.capitalize()+'ingSetpoint'}"
                    state.setpointSentByApp = true
                    runIn(3, resetSetByThisApp)
                        boolean inpowerSavingMode = location.mode in powersavingmode

                    if (enablewarning) {
                        log.warn "target: $target"
                        log.warn "inside: $inside"
                        log.warn "outside: $outside"
                        log.warn "motionActive: $motionActive"
                        log.warn "doorsContactsAreOpen: $doorsContactsAreOpen"
                        log.warn "thermModes: $thermModes"
                    }

                    try {
                        set_thermostat_target_ignore_setpoint(cmd,
                            target,
                            inside,
                            outside,
                            motionActive,
                            doorsContactsAreOpen,
                            contactsClosed,
                            thermModes,
                            humThres,
                            "d54fdhj")
                        //  (cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, origin)
                    } catch (Exception e) {
                        log.error "set_thermostat_target_ignore_setpoint (call from fanMode) => $e"
                    }

                    if (currSpNotOk || thermostatModeNotOk) {

                        if (need == "off" && doNotSendAnyCoolHeatOffComm) {
                            if (enableinfo) log.info "need is $need but not sending cmd due to doNotSendAnyCoolHeatOffComm (only off, cool/heat still need to be sent in case it's off or in the wrong mode and before overriding with setpoint"
                            if (enabledebug) log.trace "fanCirculateAlways is $fanCirculateAlways"

                            if (fanCirculateAlways || (alwaysButNotWhenPowerSaving && !inpowerSavingMode && Active() && contactsClosed)) {
                                if (fanModeNotON) {
                                    try {
                                        set_multiple_thermostats_fan_mode("on", "fanCirculateAlways true dkjf5")
                                        if (enableinfo) log.info "fan set to 'on'"
                                    } catch (Exception e) {
                                        log.error "set_multiple_thermostats_fan_mode in powerManagement 1 ==> $e"
                                    }
                                }
                                else {
                                    if (enableinfo) log.info "fan already on"
                                }

                            }
                            else if (!fanModeNotON && !fanCirculateAlways) {
                                if (enableinfo) log.info "thermostat fan mode set back to auto"
                                try {
                                    set_multiple_thermostats_fan_mode("auto", "thermostat fan mode set back to auto 5df4glg")
                                } catch (Exception e) {
                                    log.error "set_multiple_thermostats_fan_mode in powerManagement 2 ==> $e"
                                }
                            }
                        }
                        else if (!doNotSendAnyCoolHeatOffComm) {
                            set_multiple_thermostats_mode(need, "5rrgh4klt5ui", null)
                        }

                        if (currSpNotOk || (thermostatModeNotOk && need != "off")) {
                            // set target temp AFTER the mode for doNotSendAnyCoolHeatOffComm to work as intended when enabled  
                            try {
                                set_thermostat_target_ignore_setpoint(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, "sljdfo")
                            } catch (Exception e) {
                                log.error "set_thermostat_target_ignore_setpoint in powerManagement ==> $e"
                            }

                        }
                        else {
                            m = "THERMOSTAT ALREADY SET TO $target "
                            if (!fanModeNotON && need == "off") m = "THERMOSTAT IN FAN CIRCULATE OPERATION "
                            if (enableinfo) log.info m
                        }
                    }

                    // state.resendAttempt = now() // needs to be updated here otherwise it'll resend immediately after 

                }
                else if ((need != "off" || !offrequiredbyuser) && !thermTempDiscrepancy) {
                    if (enableinfo) log.info "Thermostat already set to $target 47ry6ze"
                }
                else if (thermTempDiscrepancy) {
                    if (enablewarning) log.warn "Skipping normal setpoint and thermostatMode management due to thermTempDiscrepancy = $thermTempDiscrepancy"
                }
            }
            else {
                if (enablewarning) log.warn "Skipping normal set point management due to set point override"
            }
        } catch (Exception e) {
            log.error "fanMode => $e"
        }


    }
    else {
        if (enableinfo) log.info "OVERRIDE MODE--------------"
    }
}

def powerConsistency(need, target, inside, cmd, contactsClosed, doorsContactsAreOpen, motionActive, thermModes, operatingStateOk, currentOperatingNeed, humThres){
    def resendId = memoizedGetResendId()
    log.debug "Starting powerConsistency check (ID: ${resendId})"

    if (!pw) {
        log.debug "No power meter configured. Skipping power consistency check."
        return
    }

    state.resendAttempt = state.resendAttempt ?: now()
    state.offAttempt = state.offAttempt ?: now()

    def pwVal = pw.currentValue("power")
    def timeElapsedSinceLastResend = now() - state.resendAttempt
    def timeElapsedSinceLastOff = now() - state.offAttempt
    def threshold = 3 * 60 * 1000 // 3 minutes
    
    boolean pwLow = pwVal < 100
    boolean timeIsUp = timeElapsedSinceLastResend > threshold
    boolean timeIsUpOff = timeElapsedSinceLastOff > threshold
    boolean doorsOpen = doorsOpen()

    log.debug """
        Power consistency check (ID: ${resendId})
        need: ${need}, pwVal: ${pwVal}, pwLow: ${pwLow}
        timeIsUp: ${timeIsUp}, timeIsUpOff: ${timeIsUpOff}
        contactsClosed: ${contactsClosed}, doorsOpen: ${doorsOpen}
        operatingStateOk: ${operatingStateOk}
    """

    // Refresh power meters if needed
    if ((need == "off" && timeElapsedSinceLastOff > 10000 && !pwLow) ||
        (need != "off" && timeElapsedSinceLastResend > 10000 && pwLow)) {
        log.info "Refreshing power meters (ID: ${resendId})"
        poll_power_meters()
        return  // Exit and wait for next cycle to evaluate with fresh data
    }

    // Check for inconsistencies and resend commands if necessary
    if (need != "off" && timeIsUp && pwLow) {
        log.warn "Inconsistency detected: Need is ${need} but power is low. Resending commands. (ID: ${resendId})"
        resendCommands(need, cmd, target, inside, doorsContactsAreOpen, contactsClosed, thermModes, humThres, resendId)
    } else if (need == "off" && timeIsUpOff && !pwLow && !doorsOpen) {
        log.warn "Inconsistency detected: Need is off but power is high. Resending off command. (ID: ${resendId})"
        turn_off_thermostats(need, inside, thermModes, !contactsClosed)
        state.offAttempt = now()
    }

    // Check operating state consistency
    if (!operatingStateOk) {
        log.warn "Operating state inconsistency detected. Sending emergency command. (ID: ${resendId})"
        sendEmergencyCommand(need, currentOperatingNeed)
    }
}

def resendCommands(need, cmd, target, inside, doorsContactsAreOpen, contactsClosed, thermModes, humThres, resendId) {
    if (!doNotSendAnyCoolHeatOffComm) {
        log.debug "Resending thermostat mode: ${need} (ID: ${resendId})"
        set_multiple_thermostats_mode(need, "powerConsistency resend", null, resendId)
    }
    if (!ignoreTarget) {
        log.debug "Resending target temperature: ${target} (ID: ${resendId})"
        set_target(cmd, target, inside, get_outside_temperature(), true, doorsContactsAreOpen, contactsClosed, thermModes, humThres, "powerConsistency resend", resendId)
    }
    state.resendAttempt = now()
    state.setpointSentByApp = true
    runIn(3, resetSetByThisApp)
}

def memoizedGetResendId = { ->
    if (state.currentResendId == null || now() - (state.lastResendIdTime ?: 0) > 60000) { // 1 minute expiration
        state.currentResendId = UUID.randomUUID().toString()
        state.lastResendIdTime = now()
    }
    return state.currentResendId
}

def sendEmergencyCommand(need, currentOperatingNeed) {
    def faulty_devices = neededThermostats.findAll{ it -> it.currentValue("thermostatOperatingState") != currentOperatingNeed }
    faulty_devices.each { device ->
        log.warn "Sending emergency command to ${device}"
        device.refresh()
        pauseExecution(1000)
        if (device.currentValue("thermostatOperatingState") != "fanCirculate") {
            set_thermostat_mode(device, need, "emergency resend")
        }
    }
}
def fanCirculateManagement(need, target, inside, contactsClosed, motionActive, thermModes){
    /****************FAN CIRCULATION MANAGEMENT*************************/

    if (need in ["cool", "heat"]) {
        if (enabledebug) log.debug "not changing fan mode because current need is ${need}"
    }
    else {
        if (thermostat.currentValue("thermostatFanMode") == "on" && contactsClosed && !fancirculate && state.fanOn && !fanCirculateAlways) {

            if (enableinfo) log.info "Setting fan back to auto"
            if (thermostat.currentValue("thermostatFanMode") != "auto") set_multiple_thermostats_fan_mode("auto", "Setting fan back to auto")
            state.fanOn = false
        }
        if (fanCirculateAlways) {
            boolean inFanCirculateMode = fanCirculateModes ? location.mode in fanCirculateModes : false
            def fanMode = thermostat.currentValue("thermostatFanMode")
            if (enabledebug) log.trace  "fanCirculateAlways => fanMode = $fanMode inFanCirculateMode (location mode) = $inFanCirculateMode"
            if (fanCirculateSimpleModeOnly && !simpleModeActive) {
                if (thermostat.currentValue("thermostatFanMode") != "auto") {
                    if (enableinfo) log.info "Setting fan back to auto because $simpleModeName Mode not currently enabled"
                    set_multiple_thermostats_fan_mode("auto", "Setting fan back to auto 2")
                }
            }
            else if (fanCirculateModes && !inFanCirculateMode) {
                if (fanMode != "auto") {
                    if (enableinfo) log.info "Setting fan back to auto because location is no longer in fan circulate mode"
                    set_multiple_thermostats_fan_mode("auto", "Setting fan back to auto 3")
                }
            }
            else {

                boolean thermModeNotOff = thermModes.any{ it -> it.currentValue("thermostatMode" != "off") }
                if (fanMode != "on") {
                    if (!motionActive && alwaysButNotWhenPowerSaving) {
                        if (thermModeNotOff || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) {
                            state.dontChekcThermostatStateCount += 1


                            if (enableinfo) log.info "thermostat off 78egj"
                            turn_off_thermostats(need, inside, thermModes, !contactsClosed)
                            set_multiple_thermostats_fan_mode("auto", "fan auto when thermostat off 78egj")

                        }
                    }
                    else {

                        if (enableinfo) log.info "thermostat off 54gt6z34"
                        turn_off_thermostats(need, inside, thermModes, !contactsClosed)


                        if (fanCirculateAlways) {
                            if (enableinfo) log.info "fan stays on at user's request"
                            set_multiple_thermostats_fan_mode("on", "fan stays on at user's request")
                        }
                        else {
                            set_multiple_thermostats_fan_mode("on", "fan on instead of off")
                        }
                    }
                }
                else {
                    if (enabledebug) log.trace "fan already on"
                }
            }


            if (enabledebug) log.debug "fanCirculateAlways true"
            if (enabledebug) log.debug "fanCirculateSimpleModeOnly = $fanCirculateSimpleModeOnly"
            if (enabledebug) log.debug "inFanCirculateMode = $inFanCirculateMode"
            if (enabledebug) log.debug "thermostat.currentValue('thermostatFanMode') = ${thermostat.currentValue('thermostatFanMode')}"

        }
    }
    /****************END OF FAN CIRCULATION MANAGEMENT*************************/

    /****************FAN SWITCH MANAGEMENT*************************/
    if (fan && motionActive && need == "cool") // only if motion active and cooling. If user wants it to run in no motion state, then this will be taken care of later down
    {
        def fanCmd = neverTurnOff ? "on" : fanWhenCoolingOnly && need == "cool" ? "on" : fanWhenHeatingOnly && need == "heat" ? "on" : "off"

        if (fan?.currentValue("switch") != "fanCmd") {
            fan?."${fanCmd}"()
            if (enableinfo) log.info "$fan turned $fanCmd gt97"
        }
    }
    if (fanDimmer) {

        boolean keepOnWindows = keepFanOnWhenWindowsOpen && !contactsClosed
        boolean keepFandDimmerCoolerOutside = keepFanDimmerOnIfOutsideLowerThanInside && outside < inside && inside > 74

        def dimmerValue = need in ["cool", "heat"] || neverTurnOffFanDimmer || (keepOnWindows && state.lastNeed != "heat") ? inside >= target + 4 || outside >= 85 ? maxFanSpeed : inside >= target + 2 ? mediumFanSpeed : lowFanSpeed : 0
        dimmerValue = keepFandDimmerCoolerOutside ? maxFanSpeed : dimmerValue
        dimmerValue = location.mode in silenceMode ? silenceValue : dimmerValue
        if (fanDimmer.currentValue("level") != dimmerValue) fanDimmer?.setLevel(dimmerValue)
        if (enableinfo) log.info "$fanDimmer running at ${fanDimmer.currentValue("level")}%"
    }
    /****************END OF FAN SWITCH MANAGEMENT*************************/
}
def checkHeatPump(need, inside, thermModes, contactsClosed){
    boolean tooMuchPower = false
    if (controlPowerConsumption && state.lastNeed == "heat" && pw && heater) // heater only
    {
        currentPower = pw?.currentValue("power").toInteger() + heater?.currentValue("power").toInteger()
        tooMuchPower = currentPower > maxPowerConsumption.toInteger()
        if (tooMuchPower) {
            if (enablewarning) log.warn format_text("power consumption ${heater?.currentValue("power")!=0 ? "$heater + ":""} $thermostat = $currentPower Watts", "white", "red")
        }
        else {
            if (enableinfo) log.info format_text("power consumption ${heater?.currentValue("power")!=0 ? "$heater + ":""} $thermostat = $currentPower Watts", "white", "lightgreen")
        }
        //tooMuchPower = devicePriority == "$thermostat" && heatpumpConditionsTrue ? true : tooMuchPower
        // redundant: if device priority is thermostat and heatpump conditions are true, then the thermostat will be shut down
        // what we need is to make sure that the alternate heater (mandatory if heatpump true) will kick in, which is tested by virtualThermostat method
    }
    if (coolerControlPowerConsumption && state.lastNeed == "cool" && pw && cooler && !preferCooler) // cooler only and if not already prefered
    {
        currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
        tooMuchPower = currentPower > coolerMaxPowerConsumption.toInteger()
        if (tooMuchPower) {
            if (enablewarning) log.warn format_text("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler + ":""} $thermostat = $currentPower Watts", "white", "red")
        }
        else {
            if (enableinfo) log.info format_text("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler + ":""} $thermostat = $currentPower Watts", "white", "lightgreen")
        }
    }

    //if heatpump and conditions for heatpump are met (too cold outside) OR too much power and priority is not thermostat 
    //Then keep the thermostat in off mode. 

    if ((heatpumpConditionsTrue) || (tooMuchPower && devicePriority != "$thermostat")) {
        if ((thermostat.currentValue("thermostatMode") != "off" || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) && !fanCirculateAlways) {
            state.dontChekcThermostatStateCount += 1

            if (enableinfo) log.info "thermostat off 735h4ze6 heatpumpConditionsTrue = $heatpumpConditionsTrue"
            turn_off_thermostats(need, inside, thermModes, !contactsClosed) // so as to take precedence over any other condition, thermModes 

            state.offAttempt = now()
            if (enableinfo) log.info "$thermostat turned off due ${preferCooler && state.lastNeed == "cool" ? "to preferCooler option" : "to heatpump or power usage conditions"}"

        }
    }
}
def antifreeze(inside, simpleModeActive, contactsClosed){
    if (powersavingmode && location.mode in powersavingmode) {
        // do nothing 
        if (enableinfo) log.info "location is in power saving mode, antifreeze test is being ignored"
        state.antifreeze = false
    }
    else if (simpleModeActive) {
        if (enabledebug) log.debug "Antifreeze not running due to simpleModeActive = $simpleModeActive"
        state.antifreeze = false
    }
    else {
        if (state.antifreeze) {
            log.warn "ANTI FREEZE HAS BEEN TRIGGERED"
        }
        // antifreeze precaution (runs after calling state.antifreeze on purpose here)
        def backupSensorTemp = backupSensor ? backupSensor.currentValue("temperature") : inside

        if (antifreeze && !state.setPointOverride) {
            def antiFreezeThreshold_V = antiFreezeThreshold != null ? antiFreezeThreshold : criticalcold != null ? criticalcold : celsius ? get_celsius(65) : 65

            if (inside <= antiFreezeThreshold_V || backupSensorTemp <= antiFreezeThreshold_V) {

                state.antifreeze = true
                state.setpointSentByApp = true // make sure not to learn desired setpoint from this value
                runIn(3, resetSetByThisApp)

                if (enablewarning) log.warn "$thermostat setpoint set to $safeValue as ANTI FREEZE VALUE | inside = $inside | antiFreezeThreshold = $antiFreezeThreshold_V | safeValue = $safeValue"

                set_multiple_thermostats_mode("heat", "antifreeze_user", safeValue)

                state.resendAttempt = now()
                windows?.off() // make sure all windows linked to this instance are closed
                if (enablewarning) if (heater) log.warn "turning on heater: $heater"
                heater?.on()// turn on the alternate heater, if any
                set_thermostat_mode(altThermostat, "heat", "antiFreeze")

                
                def cmd = "setHeatingSetpoint"
                def target = safeValue
                def outside = get_outside_temperature() 
                def motionActive = true 
                def doorsContactsAreOpen = false 
                def thermModes = get_thermostats_modes()
                def humThres = get_humidity_threshold()
                def origin = "antiFreeze"


                set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, origin)

                // if there's an alt thermosat, it's not accounted for in set_target(). 
                // so we run the command here directly. 
                altThermostat?.setHeatingSetpoint(safeValue)

                sendNotification()

                return true // don't run any other operation until temp is back to desired value
            }
            else if (state.antifreeze) {
                state.antifreeze = false
                if (enabledebug) log.trace "END OF ANTI FREEZE"
            }
        }
        else if (!state.setPointOverride)// default & built-in app's anti freeze
        {
            def defaultSafeTemp = criticalcold == null ? 58 : criticalcold <= 58 ? criticalcold : 58
            if (inside <= defaultSafeTemp || backupSensorTemp <= defaultSafeTemp) {
                if (enablewarning) log.warn "ANTIFREEZE (DEFAULT) IS TRIGGERED: inside = $inside | thermostat val = ${thermostat?.currentValue('temperature')} backupSensorTemp = $backupSensorTemp backupSensor = $backupSensor defaultSafeTemp = $defaultSafeTemp (is this user's criticalcold set temp ? ${criticalcold == null ? false : true}"
                windows?.off() // make sure all windows linked to this instance are closed

                set_multiple_thermostats_mode("heat", "antifreeze", null)

                state.resendAttempt = now()
                state.antifreeze = true
                return true
                //sendNotification()
            }
            else {
                state.antifreeze = false
                return false
            }
        }
        if (enablewarning) log.warn "mode: ${thermostat.currentValue("thermostatMode")}"
    }

    return state.antifreeze
}
def checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside, contactsClosed){

    if (enabledebug) log.debug "coolerNotEfficientEnough = $coolerNotEfficientEnough boost = $boost thermModes = $thermModes"

    if (!coolerNotEfficientEnough && (thermModes.any{ it -> it != "off" } || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount)) && !boost)
    {
        state.dontChekcThermostatStateCount += 1
        if (enabledebug) log.trace "preferCooler = true and need = cool thermosat kept off ${preferCoolerLimitTemperature ? "unless outside temperature reaches $preferCoolerLimitTemperature":""} 56er"
        if (!fanCirculateAlways) {
            if (enableinfo) log.info "thermostat off 5fh4z2"
            turn_off_thermostats(need, inside, thermModes, !contactsClosed)
            state.offAttempt = now()
        }
    }
    else if (coolerNotEfficientEnough || boost) {
        message = "${boost ? 'boosting with ${thermotat} at user s request' : '${cooler} is not efficient enough turning $thermostat back on'} 44JKD"
        if (enabledebug) log.trace message

        if (thermModes.any{ it -> it != need } || (overrideThermostatModeCheckBeforeSendingCmd && state.dontChekcThermostatStateCount < state.reCheckStateCount))
        {
            state.dontChekcThermostatStateCount += 1
            if (enablewarning) log.warn "cooler not efficient enough 5zr4z8h"

            set_multiple_thermostats_mode("cool", "efficiency boost", null)

            state.resendAttempt = now()
        }
    }
}
def flashTheLight(){

    def lastState = lightSignal.currentValue("switch")
    def previousColor = lightSignal.currentValue("colorTemperature") //[hue: lightSignal.currentValue("hue").toInteger(), saturation: lightSignal.currentValue("saturation").toInteger(), level: lightSignal.currentValue("level").toInteger()]
    lightSignal.on()
    pauseExecution(500)

    if (enabledebug) log.debug "${lightSignal.getCapabilities()}" //weirdly required otherwise platform doesn't read colorControl capability... pb raised on the forums, never resolved by Hubitat's staff, for some reason
    boolean colorControlCap = lightSignal?.hasCapability("ColorControl")
    if (enableinfo) log.info "colorControlCap = $colorControlCap"

    if (nightModeColor && colorControlCap) {
        if (enableinfo) log.info "previous color : $previousColor"

        //https://www.peko-step.com/en/tool/hsvrgb_en.html and then SELECT RANGE 0..100

        def red = [hue: 0, saturation: 100, level: 100]
        def blue = [hue: 66, saturation: 100, level: 100]
        def green = [hue: 32, saturation: 100, level: 100]
        def white = [hue: 20, saturation: 40, level: 100]
        if (enablewarning) log.warn "nightModeColor = $nightModeColor"
        def theColor = nightModeColor == "red" ? red : nightModeColor == "blue" ? blue : nightModeColor == "green" ? green : white
        //if(enableinfo) log.info "setting $lightSignal color to $theColor"
        lightSignal?.setColor(theColor)

    }

    pauseExecution(500)
    lightSignal.off()
    pauseExecution(500)
    lightSignal.on()
    pauseExecution(500)
    lightSignal.off()
    pauseExecution(500)
    lightSignal.on()
    pauseExecution(500)

    if (nightModeColor && colorControlCap && setPreviousColor) {
        if (enableinfo) log.info "restoring previous color"
        previousColor = previousColor ? previousColor : "white"
        lightSignal?.setColorTemperature(previousColor)
        pauseExecution(500)
    }
    if (turnSignalLightOffAfter) {
        lightSignal.off()
    }
    else {
        lightSignal."${lastState}"()
    }
}
def sendNotification(){
    def message = "${thermostat}'s temperature is too low. Antifreeze is now active."

    state.lastNotification = state.lastNotification != null ? state.lastNotification : now()

    def dTime = 5 * 60 * 1000 // every 5 minutes

    if ((now() - state.lastNotification) >= dTime) {
        state.lastNotification = now()

        def musicDeviceList = musicDevice ? buildDebugString(musicDevice) : ""  // build a list of the devices as string
        def speechDeviceList = speech ? buildDebugString(speech) : ""
        def notifDevices = notification ? buildDebugString(notification) : ""

        def notifLogs = "${notification && speaker && speech ? "to ${ notifDevices }, ${ speakers }, ${ speechDeviceList } " : notification && speaker ? "to ${ notifDevices }, ${ musicDeviceList } " : notification && speech ? "to ${ notifDevices }, ${ speechDeviceList } " : speaker && speech ? "to ${ speakers }, ${ speechDeviceList } " : speaker ? "to ${ musicDeviceList } " : speech ? "to ${ speechDeviceList } " : ""}" 

        def debugMessage = "message to be sent: '${message} ${notifLogs}"

        if (enableinfo) log.info format_text(debugMessage, "white", "red")

        if (notification) {
            notification.deviceNotification(message)
        }
        else {
            if (enableinfo) log.info "User did not select any text notification device"
        }
        if (musicDevice || speech) {
            if (musicDevice) {
                if (initializeDevices) {
                    int i = 0
                    int s = musicDevice.size()
                    def device = []
                    for (s != 0; i != s; i++) {
                        device = musicDevice[i]
                        if (device.hasCommand("initialize")) {
                            if (enabledebug) log.debug "Initializing $device (musicDevice)"
                            device.initialize()
                            if (enabledebug) log.debug "waiting for 1 second"
                            pauseExecution(1000)
                        }
                    }
                }

                int i = 0
                int s = musicDevice.size()
                def level
                for (s != 0; i != s; i++) {
                    if (enableinfo) log.info "Sending message to $device"
                    device = musicDevice[i]
                    level = device.currentValue("level") // record value for later restore   
                    if (enableinfo) log.info "$device volume level is $level"
                    device.setLevel(volumeLevel.toInteger()) // set target level // for some reason this doesn't work
                    pauseExecution(500)// give it time to go through
                    device.playText(message) // send the message to play
                    device.setLevel(level.toInteger()) // restore previous level value
                }
                return
            }
            if (speech) {
                if (initializeDevices) {
                    int i = 0
                    int s = speech.size()
                    def device
                    for (s != 0; i != s; i++) {
                        device = speech[i]
                        if (device.hasCommand("initialize")) {
                            if (enableinfo) log.info "Initializing $device (speech)"
                            device.initialize()
                            if (enableinfo) log.info "wainting for 1 second"
                            pauseExecution(1000)
                        }
                    }
                }
                def volume = volumeLevel ? volumeLevel : 70
                speech.speak(message, volume)
            }
        }
    }
}
def buildDebugString(deviceList){
    def devices = ""
    int i = 0 
    int s = deviceList.size()
    if (s != 0) {

        for (s != 0; i != s; i++) {
            devices += "${deviceList[i]}, "
        }

    }
    return devices
}
def resetCmdForce(){
    if (enabledebug) log.trace "Resetting forceCommand counter"
    state.forceAttempts = 0
}
def set_dimmer(val, calledby){

    log.warn format_text("SETTING DIMMER TO $val - cmd called by $calledby", "white", "red")
    try {
        if (simpleModeIsActive()) return
        if (enabledebug) log.trace "setDimmer $val"
        if (!state.setPointOverride) {
            if (dimmer) {
                state.setpointSentByApp = true
                runIn(3, resetSetByThisApp)
                dimmer.setLevel(Math.round(Double.parseDouble(val.toString()))) // some thermostats will parse set points as double. Here, to parse as double, first we need to parse as string, hence toString()
                //so it needs to be rounded so as to be parsed as a string in the dimmer driver        
                if (enableinfo) log.info "$dimmer set to $val BY THIS APP"
            }
            else {
            def thisVal = Math.round(Double.parseDouble(val.toString()))
                state.lastThermostatInput = thisVal
                //state.setpointSentByApp = true   // not applicable in this case since it won't trigger any device event
                if (enableinfo) log.info "state.lastThermostatInput set to $thisVal"
            }
        }
        else {
            if (enabledebug) log.trace "SETPOINT OVERRIDE DUE TO THERMOSTAT DISCREPANCY NOT CHANGING DIMMER VALUE"
        }

    }
    catch (Exception e) {
        log.error "set_dimmer() ==> error: $e"
    }
    state.setPointOverride = false
}
def virtualThermostat(need, target){

    try {
        if (enabledebug) log.debug "virtualThermostat need = $need state.lastNeed = $state.lastNeed"

        def outside = outsideTemp?.currentValue("temperature") // only needed if electric heater here
        def lowTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? get_celsius(28) : 28 : celsius ? get_celsius(40) : 40 
        def highTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? get_celsius(28) : 28 : celsius ? get_celsius(40) : 40 
        boolean lowLimitReached = heatpump && !addLowTemp ? true : !thermostat ? true : (heater || heatpump) && addLowTemp ? outside < lowTemperature : true
        //if heatpump, lowLimitReached is when it's too cold outside for a heatpump to remain efficient, or if threshold has been reached so the heater has to take over, if any...
        //if heater and no heatpump, lowLimitReached is when it's so cold that heater has to come and help
        //if heater AND no thermostat, heater runs all the time when needed, no low limit so lowLimitReached returns true

        boolean inAllHeatSourcesMode = useAllHeatSourcesWithMode && location.mode in allHeatModes 
        boolean altHeatExclusiveMode = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes || altThermostatOnlyInSimpleMode && simpleModeIsActive()
        boolean dontSendThermostatModeCmd = dontSetThermModesInSimpleMode && simpleModeIsActive() || doNotSendAnyCoolHeatOffComm

        if (heater || altThermostat || inAllHeatSourcesMode || altHeatExclusiveMode) {
            boolean tooMuchPower = false
            if (controlPowerConsumption && state.lastNeed == "heat") {
                if (heater?.hasCapability("powerMeter") || heater?.hasAttribute("power")) {
                def pwVal = !pw ? 0 : pw?.currentValue("power")?.toInteger()
                def heaterVal = !heater ? 0 : heater?.currentValue("power")?.toInteger()
                def currentPower = pwVal + heaterVal
                    tooMuchPower = currentPower > maxPowerConsumption.toInteger()
                    tooMuchPower = devicePriority != "$heater" && heatpump && outside < lowTemperature ? false : tooMuchPower
                }
                else {
                    if (enablewarning) log.warn "$heater doesn't have power measurement capability"
                    app.updateSetting("controlPowerConsumption", [type: "bool", value: false])
                    tooMuchPower = false
                }

                if (tooMuchPower) {
                    if (enablewarning) log.warn "both $thermostat and $heater are using too much power but heater needs to stay on due to outside temperature being too low for the heat pump to remain efficient. $thermostat should be off"
                }
                // if device priority is not the heater while outside temp is low and heatpump true, we need to keep the heater on so tooMuchPower must be false
                // in the unlikely but still possible case where the thermostat is not already off 
            }
            if (tooMuchPower && devicePriority != "$heater") // if thermosat isn't priority it's overriden if it's a heatpump and low temp outside is true
            {
                need = "off"
                if (enablewarning) log.warn "$thermostat and $heater use too much power at the same time. Turning $heater off since $thermostat takes precedence"
            }

            if (need == "heat" && state.lastNeed == "heat") {
                if (lowLimitReached || useAllHeatSources || inAllHeatSourcesMode || altHeatExclusiveMode) {
                    if (heater?.currentValue("switch") == "off") {
                        heater?.on()    
                    def m = heater && altThermostat ? "Turning on heater and setting $altThermostat to heat" : heater ? "Turning on $heater 54d54" : altThermostat ? "setting $altThermostat to heat" : "no alternate heater or thermostat to manage"
                        if (enableinfo) log.info "$m"
                    }
                    else {
                        if (enabledebug) log.trace "heater is on"
                    }
                    if (altThermostat) {
                        if (altThermostat?.currentValue("thermostatMode") != "heat" || altThermostat?.currentValue("heatingSetpoint") != target) {
                            if (enabledebug) log.trace "$altThermostat set to heat and $target 5zrgj5r"
                            if (!dontSendThermostatModeCmd) {
                                altThermostat?.setThermostatMode("heat")
                            }
                            else {
                                if (enabledebug) log.trace "dontSendThermostatModeCmd true 6rer5r4z63"
                            }
                        boolean inpowerSavingMode = location.mode in powersavingmode
                            if (checkIgnoreTarget() && !inpowerSavingMode) {
                                if (enabledebug) log.trace  "Target ($target) temp not sent to $altThermostat at user's request"
                            }
                            else {
                                if (enabledebug) log.trace "$altThermostat heat set to $target 54zgy8ui6"
                                altThermostat?.setHeatingSetpoint(target)
                            }
                        }
                    }
                }
                else if (useAllHeatSourcesWithMode && !inAllHeatSourcesMode) {
                    if (enabledebug) log.trace "outside of useAllHeatSourcesWithMode modes ${altThermostat && heater ? "$altThermostat & $heater stay off" : altThermostat ? "$altThermostat stays off" : heater ? "$heater stays off" : ""} "
                }
                else if (heater) {
                    if (enableinfo) log.info "$heater not turning on because low temp limit outside hasn't been reached yet"
                    if (enabledebug) if (heater) log.debug "Turning $heater off dzgz5h"
                    heater?.off()
                    altThermostat?.setThermostatMode("off")
                }
            }
            else {
                if (enabledebug) if (heater) log.debug "Turning $heater off 5gz4"
                heater?.off()
                altThermostat?.setThermostatMode("off")
            }
        }
        if (cooler) {
        boolean tooMuchPower = false
        boolean powerCapable = cooler?.hasCapability("powerMeter") || cooler?.hasAttribute("power")

            if (preferCooler) {
                // user wants the cooler to run as main device and thermostat as secondary device
                // don't change the need value
            }
            else if (coolerControlPowerConsumption && state.lastNeed == "cool") {
                if (powerCapable) {
                def currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
                    tooMuchPower = CurrentPower > coolerMaxPowerConsumption.toInteger()
                    tooMuchPower = coolerDevicePriority != "$cooler" && outside > lowTemperature ? false : tooMuchPower
                }
                else {
                    if (enablewarning) log.warn "$cooler doesn't have power measurement capability"
                    app.updateSetting("coolerControlPowerConsumption", [type: "bool", value: false])
                    tooMuchPower = false
                }

                if (tooMuchPower && coolerDevicePriority != "$cooler") // if thermosat isn't priority it's overriden if high temp outside is true
                {
                    need = "off"
                    if (enablewarning) log.warn "$thermostat and $cooler use too much power at the same time. Turning $cooler off since $thermostat has precedence"
                }
            }

            state.coolCmdSent = state.coolCmdSent == null ? false : state.coolCmdSent
            state.offCmdSent = state.offCmdSent == null ? false : state.offCmdSent

        def coolerCurVal = cooler?.currentValue("switch")
        def powerDiscrepancy = false
        def powerValue = null
            if (powerCapable) {
                powerValue = cooler?.currentValue("power")
                powerDiscrepancy = state.coolCmdSent && need == "cool" && powerValue < 100 ? true : state.offCmdSent && need == "off" && powerValue > 100 ? true : false
            }

            if (enabledebug) log.trace "coolerCurVal = ${coolerCurVal} ${powerCapable ? "powerValue = $powerValue" : ""} powerDiscrepancy = $powerDiscrepancy"

            if (need == "cool") {
                if (coolerCurVal != "on" || powerDiscrepancy) {
                    if (enabledebug) log.trace "${powerDiscrepancy ? "POWER DISCREPANCY: turning on $cooler AGAIN" : "turning on $cooler 89e4"}"
                    cooler?.on()
                    state.coolerTurnedOnTimeStamp = now()
                    state.coolCmdSent = true
                    state.offCmdSent = false
                    //poll_power_meters()
                }
                else {
                    if (enableinfo) log.info "$cooler already on"
                }
            }
            //////////////////
            if (need == "off") {
                if (coolerCurVal != "off" || powerDiscrepancy) {
                    if (enabledebug) log.trace "${powerDiscrepancy ? "POWER DISCREPANCY: turning off $cooler AGAIN" : "turning off $cooler"}"
                    cooler?.off()
                    state.coolCmdSent = false
                    state.offCmdSent = true
                    //poll_power_meters()
                }
                else {
                    if (enabledebug) log.debug "$cooler already off"
                }
            }

        }
    }
    catch (Exception e) {
        log.error "virtualThermostat() ==> $e"
    }

}
def windowsControl(target, simpleModeActive, inside, outside, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh, thermModes, humThres, contactsClosed){

    // motionActive argument not sent through because in get_need() it's mixed with simpleModeActive
    // so that would open windows every time $simpleModeName Mode would be active instead of cooling the room with AC as needed

    //state.lastClosingTime = 360000000 //TESTS

    if (controlWindows && windows && (!simpleModeActive || allowWindowsInSimpleMode) && !state.override) {
        if (location.mode in windowsModes) {
            // do nothing
        }
        else if (closeWhenOutsideWindowsModes && (windows.any{ it -> it.currentValue("switch") == "on" } || windows.any{ it -> it.currentValue("contact") == "open" }))
        {
            if (enabledebug) log.trace "outside windows mode"
            if (!state.windowsClosedDueToOutsideMode) {
                if (state.openByApp) windows?.off()
                if (enabledebug) log.trace "closing windows because outside windows mode"
                state.windowsClosedDueToOutsideMode = true
                state.openByApp = false
                state.closedByApp = true
            }
            else {
                if (enableinfo) log.info "windows already closed by location mode management"
            }
            return;
        }
        state.windowsClosedDueToOutsideMode = false // reset this value once the return statement is no longer called since location is in windows modes again

        boolean tooHumid = humidity >= 90 ? true : humidity >= humThres 
        boolean contactCapable = windows.any{ it -> it.hasCapability("ContactSensor") }//?.size() == windows.size() 
        boolean someAreOff = contactCapable ? (windows.findAll{ it?.currentValue("contact") == "closed" }?.size() > 0) : (windows.findAll{ it?.currentValue("switch") == "off" }?.size() > 0)
        boolean someAreOpen = contactCapable ? (windows.findAll{ it?.currentValue("contact") == "open" }?.size() > 0) : (windows.findAll{ it?.currentValue("switch") == "on" }?.size() > 0)
        boolean withinRange = outside < outsidetempwindowsH && outside > outsidetempwindowsL // strict temp value

        boolean outsideWithinRange = withinRange && !tooHumid // same as withinRange but with humidity

        state.lastOpeningTime = state.lastOpeningTime != null ? state.lastOpeningTime : now() // make sure value is not null
        state.outsideTempAtTimeOfOpening = state.outsideTempAtTimeOfOpening != null ? state.outsideTempAtTimeOfOpening : outside // make sure value is not null
        boolean outsideTempHasDecreased = outside < state.outsideTempAtTimeOfOpening - swing // serves mostly to reset opening time stamp
        state.outsideTempAtTimeOfOpening = outsideTempHasDecreased ? outside : state.outsideTempAtTimeOfOpening // if outsideTempHasDecreased true, reset outsidetemAtTimeOfOpening stamp so to use outsideTempHasDecreased only once 
        state.lastOpeningTime = outsideTempHasDecreased ? now() : state.lastOpeningTime // reset opening time stamp if it got cooler outside, allowing more time to cool the room

        state.insideTempAtTimeOfOpening = state.insideTempAtTimeOfOpening ? state.insideTempAtTimeOfOpening : inside // make sure value is not null
        boolean insideTempHasIncreased = inside > state.insideTempAtTimeOfOpening + swing // serves for windows wider opening ONLY
        state.insideTempHasIncreased = insideTempHasIncreased
        state.widerOpeningDone = (state.widerOpeningDone != null) ? state.widerOpeningDone : (state.widerOpeningDone = false) // make sure value is not null
        boolean openMore = !state.widerOpeningDone && insideTempHasIncreased && someAreOpen

        boolean insideTempIsHopeLess = inside > state.insideTempAtTimeOfOpening + 2 && state.widerOpeningDone

        double lastOpeningTime = (now() - state.lastOpeningTime) / 1000 / 60
        lastOpeningTime = lastOpeningTime.round(2)
        boolean beenOpenForLong = lastOpeningTime > 30 && someAreOpen // been open for more than 15 minutes

        state.lastClosingTime = state.lastClosingTime ? state.lastClosingTime : (state.lastClosingTime = now()) // make sure value is not null
        double lastClosingTime = (now() - state.lastClosingTime) / 1000 / 60
        lastClosingTime = lastClosingTime.round(2)
        boolean closedSinceLong = lastClosingTime > 30 && someAreClosed // been closed for more than 30 minutes

        boolean tooColdInside = inside <= target - 8
        if (enablewarning) log.warn "tooColdInside = $tooColdInside : inside = $inside && target = $target"
        //closing error management for safety, if cmd didn't go through for whatever reason and temp went too low, force close the windows
        boolean exception = someAreOpen && ((state.closedByApp && (now() - lastClosingTime) > 30 && tooColdInside) || (!outsideWithinRange && tooColdInside))
        long elapsed = now() - lastClosingTime
        def elapsedseconds = elapsed / 1000
        def elapsedminutes = elapsed / 1000 / 60
        if (enablewarning) if (exception) { if (enablewarning) log.warn "$windows still open! EMERGENCY CLOSING WILL BE ATTEMPTED" }

        // allow for more frequent window operation when outside temp might be low enough to cool the room fast
        boolean outsideSubstantiallyLowEnough = outside < 72 && outside > outsidetempwindowsL
        float timeBtwWinOp = outsideSubstantiallyLowEnough ? 5 : 15// 5 min if it's cool enough outside, otherwise, give it 15 min before reopening

        boolean enoughTimeBetweenOpenAndClose = ((now() - state.lastOpeningTime) / 1000 / 60) > 10.0 || inside < target - swing //-> give it a chance to cool down the place
        boolean enoughTimeBetweenCloseAndOpen = ((now() - state.lastClosingTime) / 1000 / 60) > timeBtwWinOp //-> don't reopen too soon after closing

        boolean needToClose = (enoughTimeBetweenOpenAndClose && ((inside > target + (swing * 3) && beenOpenForLong) || inside < target - swing || insideTempIsHopeLess)) || !outsideWithinRange
        boolean needToOpen = (enoughTimeBetweenCloseAndOpen && (inside > target + swing && !needToClose)) && outsideWithinRange //|| amplitudeTooHigh) // timer ok, too hot inside + within range (acounting for humidity) and no discrepency

        state.otherWindowsOpenByApp = state.otherWindowsOpenByApp == null ? false : state.otherWindowsOpenByApp
        boolean synchronize = doorsManagement && doorsOpen() && otherWindows.any{ it.currentValue("switch") == "on" } && !state.otherWindowsOpenByApp
        if (synchronize) {
            state.otherWindowsOpenByApp = true
            needToOpen = synchronize
            needToClose = synchronize ? false : needToClose
        }


        if (inWindowsModes || exception) {

            def time = maxDuration ? get_windows_operation_time(outside, maxDuration, windowsDuration) : 30 // if !maxDuration time will be refined below for each individual window if needed

            if (needToOpen) // outsideWithinRange and humidity level are accounted for in needToOpen boolean, unless in power saving mode
            {
                if (enableinfo) log.info "using $windows INSTEAD OF AC"

                if (someAreOff || openMore) {
                    if (openMore) {
                        state.widerOpeningDone = true
                        unschedule(stop)
                    }

                    if (state.closedByApp || (openMore && state.openByApp)) {
                        def message = ""
                        if (onlySomeWindowsWillOpen && location.mode in modeSpecificWindows) {

                            message = "opening $onlyThoseWindows ONLY"
                            def objectDevice
                            int i = 0
                            int s = windows.size()
                            for (s != 0; i < s; i++) {
                                if ("${windows[i]}" in onlyThoseWindows) {
                                    windows[i].on()
                                    if (enableinfo) log.info "${windows[i]} is the right device"
                                }
                                state.openByApp = true
                                state.closedByApp = false
                            }
                            if (enableinfo) log.info message
                        }
                        else {
                            if (state.closedByApp) {
                                message = "opening ${windows.join(", ")} 564df4"
                                if (enablewarning) log.warn message
                                for (w in windows) {
                                    if (enablewarning) log.warn "Opening => $w"
                                    w.on()
                                }
                                state.openByApp = true
                                state.closedByApp = false
                            }
                            else {
                                if (enablewarning) log.warn "windows were not closed by this app, doing nothing"
                            }
                            if (enabledebug) log.trace "openMore && state.openByApp ===> ${openMore && state.openByApp}"
                        }

                        need0 = "off"
                        need1 = "off"
                        state.lastContactOpenEvt = state.lastContactOpenEvt ? state.lastContactOpenEvt : now()
                        def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0
                        if (!contactsClosed && (now() - state.lastContactOpenEvt) > delayB4TurningOffThermostat) {
                            if (!fanCirculateAlways) {
                                if (enableinfo) log.info "thermostat off 4trgh26"
                                turn_off_thermostats(need1, inside, thermModes, !contactsClosed)
                                state.offAttempt = now()
                            }
                            else {
                                // fan circulation conditions managed in get_need() scope
                            }
                        }
                        if (!openMore) {
                            state.lastOpeningTime = now()
                            state.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                            state.outsideTempAtTimeOfOpening = outside
                            state.insideTempAtTimeOfOpening = inside
                        }

                        if (enablewarning) log.warn "--------------------------------differentDuration = $differentDuration"
                        if (operationTime && !openMore && !INpwSavingMode) // if openMore or INpwSavingMode ignore stop() and open in full
                        {
                            if (!differentDuration) {
                                runIn(time, stop)
                                message += " for a duration of $time seconds"
                            }
                            else // apply diffent durations per device
                            {

                                int i = 0
                                int s = windows.size()
                                def device = null
                                def max = 0
                                def min = 0
                                for (s != 0; i < s; i++) {
                                    device = windows[i]
                                    max = settings["maxDuration${i}"].toInteger()
                                    min = settings["windowsDuration${i}"].toInteger()
                                    if (enablewarning) log.warn "found : $device with max = $max minimum = $min *********************"

                                    time = get_windows_operation_time(outside, max, min)
                                    runIn(time, stop, [data: ["device": "${device}"], overwrite: false])
                                    if (enablewarning) log.warn "$device scheduled to stop in $time seconds"

                                }
                            }
                        }
                        if (enablewarning) log.warn message
                        state.openByApp = true
                        state.closedByApp = false
                    }
                    else {
                        if (enablewarning) log.warn "${windows.join(", ")} were not closed by this app - ignoring on/open request"
                    }
                }
                else {
                    if (enableinfo) log.info "$windows already open"
                }
            }
            else if (someAreOpen && needToClose) {
                if ((state.openByApp) || exception) {
                    if (enablewarning) if (exception) { if (enablewarning) log.warn "EXCEPTION CLOSING" }
                    if (enablewarning) log.warn "closing $windows"
                    unschedule(stop)
                    state.closingCommand = true // the evt handler will need to know the "closed" event is not just a refresh (isStateChange can fail)
                    state.lastClosingTime = now()
                    state.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                    state.widerOpeningDone = false // simple value reset
                    windows.off()
                    state.otherWindowsOpenByApp = false
                    boolean  levelCapabality = windows.any{ it.hasCapability("Switch Level") }
                    if (exception) {
                        windows.setLevel(50)
                    }
                    else {
                        windows.setLevel(1)
                    }

                    state.openByApp = false
                    state.closedByApp = true
                    if (enableinfo) log.info "56FG"

                }
                else if (!state.openByApp) {
                    if (enableinfo) log.info "$windows were not open by this app"
                }
                else if (needToClose) {
                    if (enableinfo) log.info "$windows may close soon"
                }
                else {
                    log.error "WINDOWS MANAGEMENT ERROR - fire the developper"
                }
            }
        }
        else if (windows && !inWindowsModes) {
            if (enableinfo) log.info "outside of windows modes (current mode:${location.mode}"
            if (someAreOpen && state.openByApp) // && (inside > target + 2 || inside < target - 2 ))
            {
                windows.off()
                state.otherWindowsOpenByApp = false
                if (windows.any{ it.hasCapability("Switch Level") }) {
                    windows.setLevel(50)
                }
                //if(enableinfo) log.info "56TG"
                state.openByApp = false
                state.closedByApp = true

            }
        }

    }
    else if (!windows) {
        if (enabledebug) log.debug "user did not select any window switch"
    }
    else if (simpleModeActive) {
        if (enableinfo) log.info "skipping windows management due to $simpleModeName Mode trigger mode"
    }
    else if (state.override) {
        if (enableinfo) log.info "Override mode because $thermostat is set to 'auto'"
    }

}
def resetSetByThisApp(){
    if (state.setpointSentByApp) {
        state.setpointSentByApp = false;
    }
}
def userWants(val, inside){
    if (enablewarning) log.warn "userWants($val while inside is $inside)"
    if (val < inside - 2) {
        state.userWantsCooler = true
        state.userWantsCoolerTimeStamp = now()
        runIn(120, resetUserWants)
    }
    else {
        state.userWantsCooler = false
    }
    if (val > inside + 2) {
        state.userWantsWarmer = true
        state.userWantsWarmerTimeStamp = now()
        runIn(120, resetUserWants)
    }
    else {
        state.userWantsWarmer = false
    }
}
def resetUserWants(){
    if (enablewarning) log.warn "resetUserWants()"
    state.userWantsWarmer = false
    state.userWantsCooler = false
}


/* ############################### GETTERS ############################### */

def get_thermostats_modes() {
    def thermModes = []
    try {
        if (differentiateThermostatsHeatCool) {

            def neededThermostats = get_needed_thermosats(state.lastNeed)
            if (!neededThermostats) log.warn "neededThermostats returns $neededThermostats"
            thermModes = neededThermostats.collect{ it -> it.currentValue("thermostatMode") }
            if (!thermModes) log.warn "thermModes collection failed!:::: $thermModes"
        }
        else {
            thermModes = [thermostat.currentValue("thermostatMode")]
            if (!thermModes) log.warn "Failed to get $thermostat current mode! ::: $thermModes"

        }

    } catch (Exception e) {
        log.error "thermModes (setPointHandler) => $e"
        return ["off", "off"]
    }
    if (enabledebug || !thermModes) log.trace "get_thermostats_modes returns: ${thermModes}"
    return thermModes
}
def get_target(simpleModeActive, inside, outside){

    def target = 72 // default value
    def safeValue = 72 // fallback value

    if (enablewarning) {
        log.warn format_text("method is: $method", "white", "red")
    }

    if (method == "auto" && !simpleModeActive) {
        target = get_auto_value() as int
        if (enabledebug) log.trace  "get_auto_value() returned $target"
    }
    else if (!simpleModeActive) {
        if (enablewarning) log.warn "dimmer = $dimmer dimmer?.currentValue(level) = ${get_dimmer_value()}"
        target = !dimmer ? state.lastThermostatInput : get_dimmer_value()
    }

    // safety checkup for when alexa misinterpret a command to a light dimmer and applies it to the dimmer used by this app
    def maxHi = celsius ? get_celsius(88) : 88 // assuming no one would be stupid enough to set their thermostat to 88, if so it's interpreted as a problem by the app
    def minLow = celsius ? get_celsius(30) : 30 // same, but when setpoint is too low
    def lastNeed = state.lastNeed
    boolean problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false

   
    def debug = [
        "<br>- Celsius ? $celsius",
        "<br>- maxAutoHeat = $maxAutoHeat",
        "<br>- minAutoCool = $minAutoCool",
        "<br>- maxHi = $maxHi",
        "<br>- minLow = $minLow",
        "<br>- state.lastThermostatInput = $state.lastThermostatInput ",
        "<br>- dimmer value = ${get_dimmer_value()}",
        "<br>- heatingSetpoint = ${thermostat.currentValue('heatingSetpoint')}",
        "<br>- coolingSetpoint = ${thermostat.currentValue('coolingSetpoint')}",
        "<br>- boolean problem = $target >= $maxHi ? ${target >= maxHi} : $target <= $minLow ? ${target <= minLow} : false",
        "<br>- state.lastNeed = $state.lastNeed",
        "<br>- problem detected : $problem",
        "<br>- target = $target"
    ]
    if (enabledebug) debug_from_list(debug)

    state.problemLogs = state.problemLogs == null ? state.problemLogs = [] : state.problemLogs


    max_pb_size = 10
    // state.problemLogs = []
    if (state.problemLogs.size() >= max_pb_size) {
        while (state.problemLogs.size() > max_pb_size - 1) {
            if (dev_mode) log.debug "Removing ${state.problemLogs[0]} entry from state.problemLogs"
            state.problemLogs.remove(0)  // Removes the oldest entry
        }
    }
    if ((state.problemLogs.size() != 0 || dev_mode) && (problem || dev_mode)) log.error "Problem = $state.problemLogs"


    if (problem) {
        if (enablewarning) log.warn "There's a problem with current target temperature ($target). Readjusting with default safe value of $safeValue"


        try {
            def now = new Date()
            def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            def formattedDate = dateFormat.format(now)

            // record the problem
            state.problemLogs += "App had to fix target ($target) with default ($safeValue) safety value @${formattedDate})"

            target = celsius ? get_celsius(safeValue) : safeValue
            target = target.toString()
            // fix the dimmer's value if any dimmer
            set_dimmer(target, "get_target() problem section")

        } catch (Exception e) {
            log.error "ERROR in get_target() / if(problem) section: $e"
        }


        if (enablewarning) log.warn "************ $app.name successfuly fixed its target temperature data point! ********"
    }
    problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false

    debug = [
        "maxHi = $maxHi",
        "minLow = $minLow",
        "target = $target",
        "problem =$problem"
    ]
    if (enabledebug) debug_from_list(debug)

    if (enabledebug) log.trace  "simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ****** target = $target************ dimmerLevel = ${get_dimmer_value()}"

    if (simpleModeActive && !simpleModeSimplyIgnoresMotion) {
        if (doorsOpen() && contactsOverrideSimpleMode) {
            if (enabledebug) log.trace "some doors are open: $simpleModeName Mode trigger mode ignored at user's request"
        }
        else if (setSpecialTemp || specialSubstraction) {
            target = specialSubstraction ? target - substract : specialTemp && specialDimmer ? specialDimmer.currentValue("level") : specialTemp

            if (enabledebug) log.trace  "target temperature ${substract ? '(specialSubstraction)':'(specialTemp)'} is: $target and last recorded temperature is ${inside} | specialDimmer level = ${specialDimmer.currentValue('level')}"
            return target // END due to simpleModeName Mode trigger mode
        }
        else {
            if (enabledebug) log.trace "simpleModeActive using default target temp: $target | last recorded temperature is ${inside}"
            return target // return the default value
        }

    }
    if (enabledebug) log.trace  "target temperature is: $target and current temperature is ${inside} (swing = $state.swing) thermostat state is: ${thermostat.currentValue('thermostatMode')}"

    if (enabledebug) log.debug format_text("get_target() =====returns====> $target", "white", "red")
    return target
}
def get_needed_thermosats(need){

    // state.neededThermostats = []
    def simpleModeActive = simpleModeIsActive()

    if (enabledebug) {
        def m = [
            "<br>- useBothThermostatsForHeat: $useBothThermostatsForHeat",
            "<br>- useBothThermostatsForCool: $useBothThermostatsForCool",
            "<br>- need: $need"
        ]
        log.debug m.join()
    }

    if (useBothThermostatsForHeat || useBothThermostatsForCool) {

        if (useBothThermostatsForHeat && need in ["heat", "off"]) {
            result = [thermostatHeat, thermostatCool]
        }
        else if (useBothThermostatsForCool && need in ["cool", "off"]) {

            result = [thermostatHeat, thermostatCool]
        }
        else if (need == "off") {
            result = [thermostatHeat, thermostatCool]
        }
        else {
            result = [thermostatHeat, thermostatCool]
        }
    }
    else {
        result = [thermostat]
    }

    if (enabledebug) log.trace "get_needed_thermosats returns: $result"
    return result
}
def get_windows_operation_time(outside, max, min){

    def y = null // value to find
    def x = outside // current temperature outside
    def ya = min // minimal duration // coressponding duration for when outside temperature = xa
    def xa = outsidetempwindowsL // minimal operation temperature
    def m = 0.9 // slope / coef

    y = m * (x - xa) + ya // solving y-ya = m*(x-xa)

    if (enabledebug) log.debug " y = $y max = $max min = $min "

    y = y < min ? min : y > max ? max : y
    y = outside > 74 ? max : y

    if (enableinfo) log.info "linear result for windows duration = ${y?.toInteger()} seconds"
    return y.toInteger()
}
def get_inside_temperature(){

    def inside = thermostat?.currentValue("temperature")

    state.disabledSensors = []

    if (sensor) {

        def sensors = sensor
        // if (sensors.size() == 0 && !sensors.find{ it -> it.id == thermostat.id }) {
        //     sensors += thermostat
        // }

        def sum = 0
        int i = 0
        int s = sensors.size()
        int substract = 0 // value to substract to s when a device is to be ignored
        for (i = 0; i < s; i++) {
            def device = sensors[i]
            def val = 0
            def period = 5 // history length in hours
            def eventName = "temperature"
            def device_health_ok = hasRecentlyReportedEvents(device, period, eventName)

            if (!device_health_ok) {
                
                def m = "$device has not been sending any temperature value recently and it is being ignored during average temperature calculation"
                log.debug format_text(m, "black", "yellow")

                state.disabledSensors += device
                substract += 1
                continue

            }

            val = device?.currentValue("temperature")

            if (enabledebug) log.debug format_text("$device temperature: $val", "white", "black")
            if (enabledebug) log.debug "--${device} returns temperature: $val device id: ${device.id}"
            if (val == null) {
                log.warn "${device} is not returning any temperature: ${val} device id: ${device.id}"
                substract += 1
                continue
            }
            sum += val
        }

        if (state.disabledSensors.size() != 0) {
            if (enableinfo) log.info "disabledSensors size = ${state.disabledSensors.size()}"
            def a = state.disabledSensors
            if (enablewarning) log.warn "SOME SENSORS FAILED: ${a.join(', ')}"
        }


        inside = sum / (s - substract)

    }
    else if (doorsManagement && doorsOpen() && doorSetOfSensors && useDifferentSetOfSensors) {
        def sum = 0
        int i = 0
        int s = doorSetOfSensors.size()
        for (i = 0; i < s; i++) {
            def val = doorSetOfSensors[i]?.currentValue("temperature")
            if (enableinfo) log.info "**${doorSetOfSensors[i]} temperature is: $val"
            sum += val
        }

        inside = sum / s
    }

    if (enableinfo) log.info "${sensor?"average":""} temperature in this room is: $inside"

    inside = inside?.toDouble()
    inside = inside?.round(2)
    state.inside = inside ? inside : state.inside

    boolean problem = is_dev_app() && (inside == 0 || inside == null)

    if (enabledebug || problem) log.trace  "measured ${sensor && sensor.size() > 1 ? "temperatures are" : "is"}: ${sensor ? "${ sensor.join(", ") } ${ sensor.collect{ it.currentValue("temperature") }.join("°F, ") }°F" : inside}"

    // inside = is_dev_app() ? 0 : inside /* ****************************************************** TEST */

    if (problem) {
        inside = thermostat.currentValue("temperature")
        log.warn format_text("Temperature sensors inconsistent data: falling back to $thermostat as default sensor (which returns: ${inside}F", "white", "red")

        // inside = is_dev_app() ? 0 : inside /* ****************************************************** TEST */

        state.pausedByApp = state.pausedByApp ? state.pausedByApp : false

        problem = is_dev_app() && (inside == 0 || inside == null)
        if (problem) {

            // state.pausedByApp = true
            // state.paused = true // pause the app due to inconsistencies
            log.warn format_text("PAUSING APP DUE TO FAILURE TO READ INSIDE TEMPERATURE - INTERVENTION REQUIRED", "white", "red")
            runIn(10, check_inside_temp)
        }
    }

    return inside
}

boolean is_dev_app(){
    state.dev_mode_time = state.dev_mode_time == null ? now() : state.dev_mode_time
    if (dev_mode && (now() - state.dev_mode_time) > 10 * 60 * 1000) {
        log.warn "RESETING DEV_MODE TO FALSE"
        app.updateSetting("dev_mode", [type: "bool", value: false])
        app.updateSetting("dev_mode_only", [type: "bool", value: false])
        app.updateSetting("enabletrace", [type: "bool", value: false])
        app.updateSetting("enablewarning", [type: "bool", value: false])
        app.updateSetting("enableinfo", [type: "bool", value: false])
    }
    return dev_mode
}

boolean hasRecentlyReportedEvents(device, period, eventName) {
    def deltaTime = period * 60 * 60 * 1000 // N hours in milliseconds
    def now = new Date()
    def recentPeriod = new Date(now.time - deltaTime)

    // Fetch events for the device in the recent period
    def events = device.eventsSince(recentPeriod, [max: 200])

    if (events && enabledebug) {
        events.each {
            event ->
                boolean testMode = false
            if (testMode) log.debug "Event for $device: ${event.name}, Date: ${event.date}, Value: ${event.value}"
        }
    } else if (!events) {
        log.debug "No events found for device ${device} since ${recentPeriod}"
    }

    // Filter events to find any matching the eventName
    def matchingEvents = events.findAll { it.name == eventName }
    if (enableinfo || is_dev_app()) log.info "Found ${matchingEvents.size()} recent matching '${eventName}' events."

    if (!matchingEvents.isEmpty()) {
        if (enableinfo || is_dev_app()) log.info format_text("$device is healthy (id: ${device.id})", "white", "green")
        if (state.pausedByApp && state.paused) {
            state.paused = false // cancel the pause since at least one temp sensor is now healthy again
        }
    }
    else {
        log.error format_text("$device is not healthy (id: ${device.id})", "white", "red")
    }

    return !matchingEvents.isEmpty()

}


def check_inside_temp(){
    log.debug "checking inside temp..."
    def eventName = "temperature"
    def device_health_ok = true
    if (sensor) {
        for (s in sensor) {
            device_health_ok = hasRecentlyReportedEvents(s, 3, eventName)
            if (device_health_ok) {
                state.paused = false
                initialize()
                break
            }
        }
    }
    if (!device_health_ok) {
        // if (true) { /* ****************************************************** TEST */

        if (altThermostat && altThermostat.currentValue("thermostatMode") != "off") {
            altThermostat?.off()
        }
        if (thermostatCool && thermostatCool.currentValue("thermostatMode") != "auto") {
            thermostatCool?.setThermostatMode("auto")
        }
        if (thermostatHeat && thermostatHeat.currentValue("thermostatMode") != "auto") {
            thermostatHeat?.setThermostatMode("auto")
        }
        if (heater && heater.currentValue("switch") != "off") {
            heater?.off()
        }
        if (cooler && cooler.currentValue("switch") != "off") {
            cooler?.off()
        }
        if (thermostat && thermostat.currentValue("thermostatMode") != "auto") {
            thermostat?.setThermostatMode("auto")
        }
        log.warn format_text("Faulty Temperature Sensors! $thermostat returns: ${thermostat.currentValue("temperature")} F", "yellow", "black")
        update_app_label(" faulty inside temperature. Intervention needed.")
        runIn(10, check_inside_temp)
        return
    }
    if (state.pausedByApp) {
        state.pausedByApp = false
        state.paused = false
        if (altThermostat && altThermostat.currentValue("thermostatMode") != "off") {
            altThermostat?.off()
        }
        if (thermostatCool && thermostatCool.currentValue("thermostatMode") != "off") {
            thermostatCool?.off()
        }
        if (thermostatHeat && thermostatHeat.currentValue("thermostatMode") != "off") {
            thermostatHeat?.off()
        }
        if (heater && heater.currentValue("switch") != "off") {
            heater?.off()
        }
        if (cooler && cooler.currentValue("switch") != "off") {
            cooler?.off()
        }
        if (thermostat && thermostat.currentValue("thermostatMode") != "off") {
            thermostat?.off()
        }
    }
    app.label.minus("paused faulty inside temperature. Intervention needed.")
}

def get_outside_temperature(){
    return outsideTemp.currentValue("temperature")
}
def get_outside_threshold(humThres) {
    def outside = get_outside_temperature()
    def outsideHumidity = get_outside_humidity()
    def insideTemp = get_inside_temperature()
    def insideHumidity = get_inside_humidity()

    // Base temperature thresholds
    def heatingThreshold = 55
    def coolingThreshold = 75

    // Adjust thresholds based on outside humidity
    if (outsideHumidity > 70) {
        coolingThreshold -= 5
    } else if (outsideHumidity < 30) {
        heatingThreshold += 5
    }

    // Adjust thresholds based on inside temperature and humidity
    def indoorHumidityFactor = (insideHumidity - 50) / 10
    def indoorTempFactor = (humThres - 68) / 2

    heatingThreshold += (indoorTempFactor - indoorHumidityFactor)
    coolingThreshold += (indoorTempFactor + indoorHumidityFactor)

    // Determine the current season
    def season = getSeason()

    // Adjust thresholds based on the season
    if (season == "winter") {
        heatingThreshold -= 5
    } else if (season == "summer") {
        coolingThreshold += 5
    }

    // Return the appropriate threshold based on the current outside temperature
    if (outside < heatingThreshold) {
        result = heatingThreshold
    } else if (outside > coolingThreshold) {
        result = coolingThreshold
    } else {
        result = humThres ? humThres : 65 // default fallback value
    }
    if (enablewarning) log.warn "Now get_outside_threshold() returns $result"
    return result
}
def getSeason() {
    def now = new Date()
    def month = now.format("MM")
    def day = now.format("dd")

    if ((month == "12" && day >= "21") || (month in ["01", "02"]) || (month == "03" && day < "20")) {
        if (enableinfo) log.info "season is winter"
        return "winter"
    } else if ((month == "03" && day >= "20") || (month in ["04", "05"]) || (month == "06" && day < "21")) {
        if (enableinfo) log.info "season is spring"
        return "spring"
    } else if ((month == "06" && day >= "21") || (month in ["07", "08"]) || (month == "09" && day < "23")) {
        if (enableinfo) log.info "season is summer"
        return "summer"
    } else {
        return "fall"
    }
}
def get_inside_humidity(){

    def result

    if (!optionalHumSensor) {
        // if  we tested with hasCapability() it could return true due to generic thermostat drivers, so we test null value instead
        result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity")

        if (result == null) // if still null, force the user to review their settings
        {
            log.erro  format_text("NOR YOUR THERMOSTAT NOR YOUR OUTSIDE SENSOR SUPPORT HUMIDITY MEASUREMENT - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
        }
    }
    else {
        result = optionalHumSensor.currentValue("humidity")
        if (result == null) // if still null, force the user to review their settings
        {
            if (enablewarning) log.warn format_text("$optionalHumSensor does not support humidity (beware of generic drivers!). - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
            result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity")
            if (result != null) {
                if (enablewarning) log.warn format_text("This app is using ${thermostat?.currentValue("humidity") != null ? "$thermostat" : "$outsideTemp"} as a default humidity sensor in the mean time", "black", "red")
            }
            result = result == null ? 50 : result // temporary value as last resort
        }
    }
    if (enabledebug || result == null) log.debug "Inside humidity is ${result}%"
    return result
}
def get_outside_humidity(){
    return outsideTemp.hasCapability("RelativeHumidityMeasurement") ? outsideTemp.currentValue("humidity") : get_inside_humidity()
}
def get_humidity_threshold_old(){

    def humidity = get_inside_humidity() //outsideTemp?.currentValue("humidity") //NB: get_inside_humidity() will use outside humidity if and only if inside sensor is not returning values for some reason
    humidity = humidity != null ? humidity : celsius ? get_celsius(50) : 50 // prevents error from recently installed thermostats
    if (humidity == null) {
        def message = "$outsideTemp is not returning any humdity value - it may be because it was just included; if so, this will resolve ont its own. If this message still shows within an hour, check your thermostat configuration..."
        if (enablewarning) log.warn format_text(message, red, white)
    }
    // the higher the humidity, the lower the threshold so cooling can happen 
    def y = null // value to find
    def x = humidity ? humidity : 50 // 50 if no data returned to allow the app to run
    def ya = celsius ? get_celsius(58) : 58 //celsius ? get_celsius(60):60 // coressponding outside temperature value for when humidity = xa 
    def xa = 90 // humidity level
    def m = state.lastNeed == "cool" ? 0.1 : -0.1 // slope / coef

    y = m * (x - xa) + ya // solving y-ya = m*(x-xa)
    if (enablewarning) log.warn "y = $y"
    def lo = celsius ? get_celsius(65) : 65 // it's ok to run the heat if humidity is high while temp below 65 outside
    def hi = celsius ? get_celsius(72) : 72 // not ok to run the heat if temp ouside is above 72
    def result = y > hi ? hi : (y < lo ? lo : y) // max and min //  

    if (enableinfo) log.info "humidity-related temperature threshold (cool/heat decision or/and windows decision) is ${y != result ? "$result(corrected from y = $y)" : "$result"} (humidity being ${humidity < 40 ? "low at ${ humidity }% " : "high at ${ humidity }% "})"

    return result
}
def get_humidity_threshold(inside) {
    /**
     * The function calculates both the Heat Index and the Wind Chill inside based on the provided inside and humidity values.
     * If the inside is below 50°F (10°C), the Wind Chill inside is used as the perceived inside; otherwise, the Heat Index is used.
     * The perceived inside is compared to the actual inside to determine if it feels colder or warmer.
     * If the perceived inside is lower than the actual inside and the last mode was cooling, the function suggests waiting to switch from cooling to heating mode. Similarly, if the perceived inside is higher than the actual inside and the last mode was heating, it suggests waiting to switch from heating to cooling mode.
     * The perceived inside and the waiting decision are logged for informational purposes.
    */
    // def inside = get_inside_temperature()
    def humidity = get_inside_humidity()

    if (inside == null || humidity == null) {
        log.warn "Temperature or humidity value is missing. Defaulting to a safe threshold."
        return celsius ? get_celsius(73) : 73
    }
    
    def perceivedTemp = get_perceived_temp(inside, humidity) 
    def threshold = celsius ? (perceivedTemp - 32) * 5 / 9 : perceivedTemp
    threshold = Math.round(threshold)
    
    def feelingTemp = perceivedTemp < tempF ? "colder" : "warmer"
    if (enableinfo) log.info "Actual Temperature: $inside${celsius ? '°C' : '°F'}, Perceived Temperature: $threshold${celsius ? '°C' : '°F'} (feels $feelingTemp)"

    if (state.lastNeed == "cool" && perceivedTemp < inside - 2) {
        log.warn "Waiting to switch from cooling to heating mode due to humidity."
        state.force_wait = true
    } else if (state.lastNeed == "heat" && perceivedTemp > inside + 2) {
        log.warn "Waiting to switch from heating to cooling mode due to humidity."
        state.force_wait = true
    }
    else {
        state.force_wait = false
    }

    return threshold
}
def get_perceived_temp(inside, humidity) {
    def tempF = celsius ? (inside * 9 / 5) + 32 : inside
    def heatIndex = calculateHeatIndex(tempF, humidity)
    def windChill = calculateWindChill(tempF, humidity)
    
    def perceivedTemp = tempF < 50 ? windChill : heatIndex
    return perceivedTemp
}    
def calculateWindChill(inside, humidity) {
    def wc = 35.74 + (0.6215 * inside) - (35.75 * (humidity ** 0.16)) + (0.4275 * inside * (humidity ** 0.16))
    return wc
}
def calculateHeatIndex(inside, humidity) {
    def c1 = -42.379
    def c2 = 2.04901523
    def c3 = 10.14333127
    def c4 = -0.22475541
    def c5 = -0.00683783
    def c6 = -0.05481717
    def c7 = 0.00122874
    def c8 = 0.00085282
    def c9 = -0.00000199

    def heatIndex = c1 + (c2 * inside) + (c3 * humidity) + (c4 * inside * humidity) +
        (c5 * inside ** 2) + (c6 * humidity ** 2) + (c7 * inside ** 2 * humidity) +
        (c8 * inside * humidity ** 2) + (c9 * inside ** 2 * humidity ** 2)

    return heatIndex
}
def get_last_motion_event(Dtime, testType){

    int events = 0

    /* ############################### IF ANY ACTIVE, THEN NO NEED FOR COLLECTION ############################### */

    //this is faster to check if a sensor is still active than to collect past events
    if (motionSensors.any{ it -> it.currentValue("motion") == "active" })
    {
        if (enabledebug) log.debug "Sensor still active: ${motionSensors.findAll{it.currentValue('motion') == 'active'}}"
        events = 10 // this is not a boolean method but called through the definition of a boolean variable in Active() scope, so it must return a positive integer value if motion is active. 
        if (testType == "motionTest") if (enableinfo) log.info "$state.activeMotionCount active events in the last ${Dtime/1000/60} minutes (currently active)"
        return events // so we must return a number equal or greater than 1
    }

    /******************************COLLECTION  O(n)!!!**********************************************************/
    collection = motionSensors.collect{ it.eventsSince(new Date(now() - Dtime)).findAll{ it.value == "active" } }.flatten()
    events = collection.size()
    /* ########################################################################################################## */

    if (testType == "motionTest") if (enabledebug) log.debug  "$events active events collected within the last ${Dtime/1000/60} minutes (eventsSince)"

    // eventsSince() can be messy 

    if (events < state.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        events = state.activeMotionCount
    }
    if (events > state.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        state.activeMotionCount = events
    }

    if (enableinfo) log.info "$events ${testType == "motionTest" ? "($state.activeMotionCount)":""} active events in the last ${Dtime/1000/60} minutes ($testType)"
    return events
}
def get_remains_off(need) {
    if (need == "cool") {
        if (useBothThermostatsForCool) {
            return null
        } else {
            return thermostatHeat
        }
    } else if (need == "heat") {
        if (useBothThermostatsForHeat) {
            return null
        } else {
            return thermostatCool
        }
    } else {
        return null
    }
}
def get_thermostat_that_must_remain_off(timeLimit, timeStamp){

    timeLimit = timeLimit.toInteger() * 60 * 60 * 1000
    long elapsedTime = now() - timeStamp // total elapsed time since last true event and now

    if (elapsedTime > timeLimit) {
        return 0
    }

    // get the remaining time given the time limit
    float minutes = (timeLimit - elapsedTime) / 1000 / 60 // remaining minutes
    float hours = (timeLimit - elapsedTime) / 1000 / 60 / 60 // remaining hours
    float remain = minutes >= 60 ? hours : minutes // decision hours/minutes
    def unit = minutes >= 60 ? "hours" : "minutes"

    if (enabledebug) log.debug " timeLlimit = $timeLimit | timeStamp = $timeStamp | (now() - timeStamp)/1000/60 = ${(now() - timeStamp)/1000/60} minutes | elapsedTime = $elapsedTime | //REMAINING TIME in minutes, hours | minutes = $minutes | hours = $hours | remain = $remain | unit = $unit "

    return "${Math.round(remain)} $unit"
}
def get_celsius(int value){
    def C = (value - 32) * (5 / 9)
    if (enableinfo) log.info "${value}F converted to ${C}C"
    return C.toInteger()
}
def get_farhenheit(int value){
    //(0°C × 9/5) + 32 = 32°F
    def F = (value * 9 / 5) + 32
    if (enableinfo) log.info "${value}F converted to ${F}F"
    return F.toInteger()
}
def get_dimmer_value(){
    return dimmer?.currentValue("level")
}
// Function to get current conditions (for demonstration purposes in A.I. beta 2)
def get_conditions() {
    // Replace with actual logic to get current conditions
    def inside = Math.round(get_inside_temperature())
    def outside = Math.round(get_outside_temperature())
    def insideHumidity = Math.round(get_inside_humidity()) // Round humidity values
    def outsideHumidity = Math.round(get_outside_humidity()) // Round humidity values
    return [inside as int, outside as int, insideHumidity as int, outsideHumidity as int]
}
def get_auto_value() {
    // Get current conditions and round temperature values to integers
    def outside = Math.round(get_outside_temperature())
    def inside = Math.round(get_inside_temperature())
    def insideHumidity = get_inside_humidity()
    def outsideHumidity = get_outside_humidity()

    // Create the conditions array and key
    def conditions = [inside, outside, insideHumidity, outsideHumidity].collect { it as int }
    def conditionsKey = conditions.join('-')

    // NEW CODE STARTS HERE
    // Read existing hash table from your HTTP server
    def hashTableJson = readFromFile("hash_table.txt")
    def hashTable = hashTableJson ? deserializeHashTable(hashTableJson) : [: ] // Helper function to deserialize JSON string to hash table
    // NEW CODE ENDS HERE

    def learnedTarget = hashTable[conditionsKey] // Modified to use the newly read hashTable

    pauseExecution(1000)

    if (enablewarning) log.warn "next loop over: $hashTable"  // Modified to use the newly read hashTabl
    if (enabledebug) log.trace "hashTable size: ${hashTable.size()}"  // Modified to use the newly read hashTabl

    if (enabledebug) log.debug "outside =========> $outside"
    if (enabledebug) log.debug "inside =========> $inside"
    if (enabledebug) log.debug "insideHumidity =========> $insideHumidity"
    if (enabledebug) log.debug "outsideHumidity =========> $outsideHumidity"
    if (enabledebug) log.debug "Generated conditionsKey =========> ${conditionsKey}"
    if (enabledebug) log.debug "learnedTarget =========> ${learnedTarget}"

    def level = get_dimmer_value()

    if (learnedTarget == null && level) {
        if (enableinfo) log.info "No corresponding conditions in the database. Adding current conditions and set point..."

        // Add the conditions to the hash table with dimmer level as the value
        hashTable[conditionsKey] = level  // Modified to use the newly read hashTable
        if (enablewarning) log.warn "Added conditions to the hash table with dimmer level as the value."

        // Write updated hash table back to your HTTP server
        def newHashTableJson = serializeHashTable(hashTable)  // Helper function to serialize hash table to JSON string
        writeToFile("hash_table.txt", newHashTableJson)


        learnedTarget = hashTable[conditionsKey]  // Modified to use the newly read hashTable

        if (enabledebug) log.trace "${learnedTarget == null ? 'FAILED to retrieve any value despite adding one... investigate.' : 'Successfully added and retrieved: ' + learnedTarget}"

        if (enabledebug) log.debug hashTable  // Modified to use the newly read hashTabl
        if (enablewarning) log.warn "hashTable size: ${hashTable.size()}"  // Modified to use the newly read hashTab
    }

    if (learnedTarget != null) {
        if (enabledebug) log.debug "Learned target applied: $learnedTarget"
        return learnedTarget // Return the target based on learned data
    } else if (useDryBulbEquation) {
        // Fallback to dry-bulb temperature if no learned data is available
        def drybulbval = defaultSetpoint()
        if (enablewarning) log.warn "drybulbval fallback value => ${drybulbval}"
        return drybulbval
    } else {
        // If nothing else, 
        if (enablewarning) log.warn "------------conditions: $conditions"
        log.warn "******* level: ${level}"
        return level
    }
}

/* ############################### NEED EVAL ############################### */


def get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, neededThermostats, thermModes, humThres, origin) {
    def need = ["off", "off"]
    def cause = "UNKNOWN"
    def swing = get_temperature_swing(outside, celsius)

    try {

        boolean tooCold = inside.toDouble() < target.toDouble() - swing.toDouble()  
        boolean tooHot = inside.toDouble() > target.toDouble() + swing.toDouble()

        // log.warn format_text("tooHot=$tooHot | ${inside.toDouble()} >= ${target.toDouble()} + ${swing.toDouble()} (${target.toDouble() + swing.toDouble()})",  "black", "yellow")

        if (enabletrace || is_dev_app()) log.trace format_text("thermModes: $thermModes", "black", "orange")
        if (enalbetrace || is_dev_app()) log.trace format_text("Inside: $inside | swing = $swing | target = $target | tooHot = $tooHot | tooCold = $tooCold | origin: $origin", "black", "yellow")

        if (tooHot) {
            need = ["Cool", "cool"]
            cause = cause.minus("UNKNOWN")
            cause += "It's getting hot in here"
        }
        else if (tooCold) {
            need = ["Heat", "heat"]
            cause = cause.minus("UNKNOWN")
            cause += "It's getting cold in here"
        }

        def humidity = get_humidity(outsideTemp, get_inside_humidity())
        def doorsOverride = get_doors_override(doorsManagement, doorsOverrideMotion)
        def inPowerSavingMode = is_in_power_saving_mode(powersavingmode, location.mode, contactsClosed, simpleModeActive, doorsContactsAreOpen, doorsOverride)
        def inWindowsModes = is_in_windows_modes(windows, location.mode, windowsModes, timeWindowInsteadofModes, windowsFromTime, windowsToTime)

        log_power_saving_mode_debug(inPowerSavingMode, powersavingmode, simpleModeActive, simpleModeSimplyIgnoresMotion, doorsContactsAreOpen, doorsOverrideMotion)

        def outsideThres = get_outside_threshold(humThres)
        def amplitude = get_temperature_amplitude(inside, target)
        
        def amplitudeTooHigh = is_amplitude_too_high(amplitude)

        def swamp = is_swamp_condition(insideHum, inside, target, swing, state.lastNeed, amplitude, contactsClosed)
        def toohot = is_too_hot(inside, target, amplitudeTooHigh)

        update_was_too_hot_state(toohot, inside, target)

        def needCool = evaluate_need_cool(state.userWantsCooler, swamp, state.lastNeed, toohot, state.wasTooHot, simpleModeActive, simpleModeSimplyIgnoresMotion, inWindowsModes, outside, outsideThres, inside, target, swing)
        def needHeat = evaluate_need_heat(simpleModeActive, simpleModeSimplyIgnoresMotion, outside, outsideThres, amplitudeTooHigh, state.lastNeed, inside, target, swing, state.userWantsWarmer)

        def norHeatNorCool = !needCool && !needHeat && inside > target + swing && simpleModeActive && outside >= 55
        def unacceptable = (!contactsClosed || doorsContactsAreOpen) && !state.override && (inside < target - 2 || inside > target + 2)

        log_doors_contacts_debug(doorsContactsAreOpen)

        if (unacceptable) {
            log.info format_text("UNACCEPTABLE TEMP - ignoring doors or windows contacts management sync", "red", "white")
            cause = cause.minus("UNKNOWN")
            cause += "Unacceptable temperature - ignoring doors or windows contacts management sync. "
        }

        log_power_saving_mode_debug_2(inPowerSavingMode, !contactsClosed, motionActive)

        if (!unacceptable && doorsManagement && doorsContactsAreOpen && !contactsClosed) {
            need = sync_with_other_room(otherRoomCooler, doorThermostat, thermostat, doorsContacts)
            cause = cause.minus("UNKNOWN")
            cause += "Doors are open, syncing with other room. "
        } else if (!inPowerSavingMode && !contactsClosed && motionActive) {
            need = handle_normal_operation(needCool, needHeat, norHeatNorCool, offrequiredbyuser, fanCirculateAlways, thermostat)
            cause = cause.minus("UNKNOWN")
            cause += "Normal operation - evaluating cooling and heating needs. "
        }

        if (!contactsClosed || !motionActive) {
            need = handle_power_saving_mode(inside, criticalhot, criticalcold, fanCirculateAllways, thermModes, keepFanOnInNoMotionMode, motionActive, inPowerSavingMode, fan, contactsClosed)
            cause = cause.minus("UNKNOWN")
            cause += "<b>Power saving mode active.</b> !contactsClosed ? ${!contactsClosed}"
        }

        windowsControl(target, simpleModeActive, inside, outside, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh, thermModes, humThres, contactsClosed)
        log_simple_mode_status(simpleModeName, simpleModeActive, doorsContactsAreOpen, contactsOverrideSimpleMode, simpleModeSimplyIgnoresMotion, doorsOpen)

        if (differentiateThermostatsHeatCool) {
            handle_differentiate_thermostats(thermostat, neededThermostats, need[1], inside, target)
        }

        handle_delayed_mode_switch(need[1], inside, humidity, target)

        state.lastNeed = need[1] == "off" ? state.lastNeed : need[1]
        state.need1 = need[1]

        log_need_debug(
            need[1],
            target,
            outside,
            inside,
            get_inside_humidity(),
            get_outside_humidity(),
            doorsContactsAreOpen,
            state.listOfOpenContacts,
            state.userWantsCooler,
            state.userWantsWarmer,
            toohot,
            swamp,
            insideHum,
            humidity,
            swing,
            outsideThres,
            needCool,
            thermModes,
            simpleModeActive,
            simpleModeSimplyIgnoresMotion,
            inWindowsModes,
            powersavingmode,
            inPowerSavingMode,
            amplitude,
            amplitudeTooHigh,
            criticalhot,
            criticalcold,
            !contactsClosed,
        )

        if (enableinfo || is_dev_app()) log.info "<b>get_need() returning need: ${need} with cause:  ${cause}</b>"

    } catch (Exception e) {
        log.error "get_need error: ${e}"
        // state.paused = true
        // state.pausedByApp = true
        check_inside_temp()
        if (altThermostat && altThermostat.currentValue("thermostatMode") != "off") {
            altThermostat?.off()
        }
        if (thermostatCool && thermostatCool.currentValue("thermostatMode") != "auto") {
            thermostatCool?.setThermostatMode("auto")
        }
        if (thermostatHeat && thermostatHeat.currentValue("thermostatMode") != "auto") {
            thermostatHeat?.setThermostatMode("auto")
        }
        if (heater && heater.currentValue("switch") != "off") {
            heater?.off()
        }
        if (cooler && cooler.currentValue("switch") != "off") {
            cooler?.off()
        }
        if (thermostat && thermostat.currentValue("thermostatMode") != "auto") {
            thermostat?.setThermostatMode("auto")
        }
        return ["auto", "auto"]
    }

    if (state.pausedByApp && state.paused) {
        def eventName = "temperature"
        def device_health_ok = hasRecentlyReportedEvents(thermostat, 3, eventName)
        if (device_health_ok) {
            state.paused = false
        }
    }

    return need
}

/* ############################### NEED EVAL HELPERS ############################### */


def get_humidity(outsideTemp, insideHumidity) {
    def humidity = outsideTemp?.currentValue("humidity")
    return humidity != null ? humidity : (insideHumidity != null ? insideHumidity : 50)
}
def get_doors_override(doorsManagement, doorsOverrideMotion) {
    return doorsManagement ? doorsOverrideMotion : true
}
def is_in_power_saving_mode(powersavingmode, locationMode, contactsClosed, simpleModeActive, doorsContactsAreOpen, doorsOverride) {
    return powersavingmode != null && (locationMode in powersavingmode || !contactsClosed) && !simpleModeActive && (!doorsContactsAreOpen && doorsOverride)
}
def is_in_windows_modes(windows, locationMode, windowsModes, timeWindowInsteadofModes, windowsFromTime, windowsToTime) {
    def isBetween = timeWindowInsteadofModes ? timeOfDayIsBetween(toDateTime(windowsFromTime), toDateTime(windowsToTime), new Date(), location.timeZone) : false
    return timeWindowInsteadofModes ? windows && isBetween : windows && locationMode in windowsModes
}
def log_power_saving_mode_debug(inPowerSavingMode, powersavingmode, simpleModeActive, simpleModeSimplyIgnoresMotion, doorsContactsAreOpen, doorsOverrideMotion) {
    if (enablewarning) {
        def message = [
            "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-10%; padding-inline-start:20px'>",
            "<br> ---------------------------- ",
            "<br> current mode = $location.mode  ",
            "<br> IN POWER SAVING MODE ? $inPowerSavingMode  ",
            "<br> powersavingmode = $powersavingmode  ",
            "<br> simpleModeActive = ${simpleModeActive}  ",
            "<br> simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ",
            "<br> !doorsContactsAreOpen = ${!doorsContactsAreOpen} ",
            "<br> location.mode in powersavingmode = ${location.mode in powersavingmode} ",
            "<br> doorsOverrideMotion = $doorsOverrideMotion ",
            "<br> state.userWantsWarmerTimeStamp = $state.userWantsWarmerTimeStamp ",
            "<br> state.userWantsCoolerTimeStamp = $state.userWantsCoolerTimeStamp ",
            "<br> now() - state.userWantsWarmerTimeStamp => ${(now() - state.userWantsWarmerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - state.userWantsWarmerTimeStamp) >= 120 * 60 * 1000} ",
            "<br> now() - state.userWantsCoolerTimeStamp => ${(now() - state.userWantsCoolerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - state.userWantsCoolerTimeStamp) >= 120 * 60 * 1000}     ",
            "<br> current mode = $location.mode ",
            "<br> ---------------------------- ",
            "</div>"
        ]
        log.warn message.join()
    }
}
def get_temperature_amplitude(inside, target) {
    return Math.abs(inside - target)
}
def get_temperature_swing(outside, celsius) {
    def lo = celsius ? get_celsius(50) : 50
    def hi = celsius ? get_celsius(75) : 75
    return outside < lo || outside > hi ? 0.5 : 1
}
def is_amplitude_too_high(amplitude) {
    return amplitude >= 3
}
def is_swamp_condition(insideHum, inside, target, swing, lastNeed, amplitude, contactsClosed) {
    return insideHum >= 50 && inside > target + swing && (lastNeed == "cool" || (insideHum >= 65 && amplitude >= 3)) && contactsClosed
}
def is_too_hot(inside, target, amplitudeTooHigh) {
    return inside >= target && amplitudeTooHigh
}
def update_was_too_hot_state(toohot, inside, target) {
    if (toohot && !state.wasTooHot) {
        state.wasTooHot = true
    }
    if (inside <= target && state.wasTooHot) {
        state.wasTooHot = false
    }
}
def evaluate_need_cool(userWantsCooler, swamp, lastNeed, toohot, wasTooHot, simpleModeActive, simpleModeSimplyIgnoresMotion, inWindowsModes, outside, outsideThres, inside, target, swing) {
    def result = userWantsCooler ? true :
        (swamp ? true :
            lastNeed == "cool" && (toohot || wasTooHot) ? true :
                (!simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ?
                    (inWindowsModes ?
                        outside >= outsideThres && inside >= target + swing :
                        outside >= outsideThres && inside >= target + swing || swamp
                    ) :
                    inside >= target + swing))
    if (enalbetrace) log.trace "evaluate_need_cool returns $result (swanp? $swamp | toohot? $toohot | wasTooHot? $wasTooHot | simpleModeActive? $simpleModeActive | simpleModeSimplyIgnoresMotion? $simpleModeSimplyIgnoresMotion"
    return result
}
def evaluate_need_heat(simpleModeActive, simpleModeSimplyIgnoresMotion, outside, outsideThres, amplitudeTooHigh, lastNeed, inside, target, swing, userWantsWarmer) {
    def needHeat = !simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ?
        (
            outside < outsideThres || (
                amplitudeTooHigh && lastNeed != "cool"
            )
        ) && inside <= target - swing :
        inside <= target - swing && outside < outsideThres

    return userWantsWarmer || inside < target - 4 ? true : needHeat
}
def log_doors_contacts_debug(doorsContactsAreOpen) {
    if (enablewarning) {
        log.warn "doorsContactsAreOpen = $doorsContactsAreOpen"
    }
}
def log_power_saving_mode_debug_2(inPowerSavingMode, contactsClosed, motionActive) {
    if (enabledebug) {
        def message = [
            "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-20%'>",
            "inPowerSavingMode = $inPowerSavingMode",
            "contactsClosed = $contactsClosed",
            "motionActive = $motionActive",
            "<div>",
        ]
        log.debug message.join()
    }
}
def sync_with_other_room(otherRoomCooler, doorThermostat, thermostat, doorsContacts) {
    def n = otherRoomCooler ? otherRoomCooler.currentValue("switch") == "on" ? "cool" : "off" : doorThermostat?.currentValue("thermostatMode")
    def need0 = n.capitalize()
    def need1 = n

    def message = "$doorsContacts ${doorsContacts.size() > 1 ? "are":"is"} open. $thermostat set to ${doorThermostat}'s mode ($n)"
    if (enableinfo) {
        log.info "<div style=\"inline-size:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">$message</div>"
    }

    return [need0, need1]
}
def handle_normal_operation(needCool, needHeat, norHeatNorCool, offrequiredbyuser, fanCirculateAlways, thermostat) {
    def need0 = ""
    def need1 = ""

    if (needCool || norHeatNorCool) {
        if (enableinfo) log.info "needCool true"
        need0 = "Cool"
        need1 = "cool"
        if (enabledebug) log.debug "need set to ${[need0,need1]}"
    } else if (needHeat) {
        if (enableinfo) log.info "needHeat true"
        need0 = "Heat"
        need1 = "heat"
        if (enabledebug) log.debug "need set to ${[need0,need1]}"
    } else if (offrequiredbyuser) {
        need0 = "off"
        need1 = "off"
        if (enabledebug) log.debug "need set to OFF 6f45h4"
    } else {
        if (fanCirculateAlways) {
            need0 = "off"
            need1 = "off"
            if (enabledebug) log.debug "need set to OFF"
        } else {
            if (enableinfo) log.info "Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)"
        }
    }

    return [need0, need1]
}
def handle_power_saving_mode(inside, criticalhot, criticalcold, fanCirculateAllways, thermModes, keepFanOnInNoMotionMode, motionActive, inPowerSavingMode, fan, contactsClosed) {
    def need0 = "off"
    def need1 = "off"
    def need = ["off", "off"]
    def cause = ""

    if (criticalhot == null) {
        app.updateSetting("criticalhot", [type: "number", value: "80"])
    }

    if (inside > criticalhot) {

        need0 = "Cool"
        need1 = "cool"
        cause = "too hot"
        set_multiple_thermostats_mode("cool", "getNeed() criticalhot event", null)

        log.warn format_text("${fanCirculateAllways ? "fan set to always be on" : "POWER SAVING MODE EXCEPTION: TOO HOT!(${ cause } criticalhot = ${ criticalhot })"}", "black", "red")

        need = [need0, need1]

    } else if (inside < criticalcold) {

        need0 = "Heat"
        need1 = "heat"
        cause = "too cold"
        set_multiple_thermostats_mode("heat", "getNeed() criticalcold event", null)
        log.warn format_text("POWER SAVING MODE EXCEPTION: TOO COLD! ($cause = $cause)", "white", "blue")

        need = [need0, need1]


    }

    if (!fanCirculateAllways && need0 == "off") {

        set_multiple_thermostats_fan_mode("auto", "back to auto when thermostat off")
        state.fanOn = false
        turn_off_thermostats(need1, inside, thermModes, !contactsClosed)

    }

    cause = !motionActive ? "No Motion" : cause

    def message = format_text("POWER SAVING MODE ${cause == "too hot" ? "IGNORED!" : "ACTIVE"} (cause: $cause)", "white", "#90ee90")

    def fanCmd = keepFanOnInNoMotionMode && !motionActive ? "on" : "off"
    if (fan && fan?.currentValue("switch") != "fanCmd") {
        if (enableinfo) log.info "$fan turned $fanCmd hyr354"
        fan?."${fanCmd}"()
    }

    if (enableinfo) log.info message

    return need
}
def log_simple_mode_status(simpleModeName, simpleModeActive, doorsContactsAreOpen, contactsOverrideSimpleMode, simpleModeSimplyIgnoresMotion, doorsOpen) {
    if (UseSimpleMode && (simpleModeActive && !doorsContactsAreOpen && !contactsOverrideSimpleMode && !simpleModeSimplyIgnoresMotion)) {
        if (enableinfo) log.info format_text("$simpleModeName Mode Enabled", "white", "grey")
    } else if (UseSimpleMode && simpleModeActive && contactsOverrideSimpleMode && doorsOpen) {
        if (enableinfo) log.info format_text("$simpleModeName Mode Called but NOT active due to doors being open", "white", "grey")
    } else if (UseSimpleMode) {
        if (enableinfo) log.info format_text("$simpleModeName Mode Disabled", "white", "grey")
    }
}
def handle_differentiate_thermostats(thermostat, neededThermostats, need, inside, target) {
    if (thermostat?.id != neededThermostats[0]?.id) {
        if (enablewarning) log.warn "using ${neededThermostats[0]} as ${need == "cool" ? "cooling unit" : "heating unit"} due to user's requested differentiation"
        app.updateSetting("thermostat", [type: "capability", value: neededThermostats[0]])
        state.otherThermWasTurnedOff = false
    }

    def remainsOff = get_remains_off(need)

    if (enabledebug) log.trace "remainsOff =====> $remainsOff"

    if (remainsOff) {
        if ((remainsOff.currentValue("thermostatMode") != "off" || keep2ndThermOffAtAllTimes) && (!state.otherThermWasTurnedOff || keep2ndThermOffAtAllTimes)) {
            state.keepOffAtAllTimesRun = state.keepOffAtAllTimesRun == null ? 8 * 60 * 1000 : state.keepOffAtAllTimesRun

            long deltaTimeResend = 1 * 60 * 1000
            state.resentOccurences = state.resentOccurences == null ? 0 : state.resentOccurences

            def timeToResend = (now() - state.keepOffAtAllTimesRun) > deltaTimeResend && state.resentOccurences < 5

            if (keep2ndThermOffAtAllTimes && timeToResend) {
                state.keepOffAtAllTimesRun = now()

                if (need == "heat" && inside <= target - 15) {
                    if (enablewarning) log.warn "ignoring keep2ndThermOffAtAllTimes, it's far too cold"
                } else {
                    set_thermostat_mode(remainsOff, "off", "remainsOffCmd")
                    state.resentOccurences += 1
                }
            } else {
                if (state.resentOccurences >= 4 && need == "heat") {
                    if (enablewarning) log.warn "$remainsOff seems to want to remain in $need mode... giving up on trying to keep it off to avoid pissing the user or damaging the hardware or overriding a vital antifreeze"
                } else if (state.resentOccurences >= 4 && need == "cool") {
                    if (enablewarning) log.warn "$remainsOff seems to insist on staying in $need mode... since mode is 'cool', $app.name will continue to send off commands"
                    state.resentOccurences = 0
                }
            }

            state.otherThermWasTurnedOff = true
        }
    }
}
def handle_delayed_mode_switch(need, inside, humidity, target) {
    state.waitAfterCoolConditionMet = state.waitAfterCoolConditionMet == null ? false : state.waitAfterCoolConditionMet
    state.waitAfterHeatConditionMet = state.waitAfterHeatConditionMet == null ? false : state.waitAfterHeatConditionMet

    def perceivedTemp = get_perceived_temp(inside.toDouble(), humidity.toDouble())
    if (enablewarning) {
        log.warn "-------------perceivedTemp: $perceivedTemp"
        log.warn "-------------humidity: $humidity"
    }
    def needToWait = false

    if (need == "cool" && state.lastNeed == "heat" && !state.waitAfterHeatConditionMet) {
        state.lastTimeCool = now()
        state.waitAfterHeatConditionMet = true
        needToWait = true
    } else if (need == "heat" && state.lastNeed == "cool" && !state.waitAfterCoolConditionMet) {
        state.lastTimeHeat = now()
        state.waitAfterCoolConditionMet = true
        needToWait = true
    }

    if (!(need in ["cool", "heat", "off"])) {
        need = "off"
    }

    if (needToWait) {
        def delayMinutes = 30 // Adjust the delay duration as needed
        def waitUntil = need == "cool" ? state.lastTimeCool + (delayMinutes * 60 * 1000) : state.lastTimeHeat + (delayMinutes * 60 * 1000)
        def remainingMinutes = ((waitUntil - now()) / 1000 / 60).round()

        if (now() < waitUntil) {
            log.warn "Waiting for ${remainingMinutes} minutes before switching to ${need} mode."
            return ["off", "off"]
        } else {
            log.warn "Waiting period has ended. Switching to ${need} mode."
            state.waitAfterCoolConditionMet = false
            state.waitAfterHeatConditionMet = false
        }
    }

    return [need == "off" ? "off" : need.capitalize(), need]
}
def log_need_debug(
    need,
    target,
    outside,
    inside,
    insideHumidity,
    outsideHumidity,
    doorsContactsAreOpen,
    listOfOpenContacts,
    userWantsCooler,
    userWantsWarmer,
    toohot,
    swamp,
    insideHum,
    humidity,
    swing,
    outsideThres,
    needCool,
    thermModes,
    simpleModeActive,
    simpleModeSimplyIgnoresMotion,
    inWindowsModes,
    powersavingmode,
    inPowerSavingMode,
    amplitude,
    amplitudeTooHigh,
    criticalhot,
    criticalcold,
    contactsClosed
) {
    if (enableinfo) log.info "need: $need | target: $target | outside: $outside | inside: $inside | inside hum: ${insideHumidity} | outside hum ${outsideHumidity} | Open Contacts:$doorsContactsAreOpen ${doorsContactsAreOpen ? "($listOfOpenContacts) " : ""} | userWantsCooler ? ${userWantsCooler} | userWantsWarmer ? ${userWantsWarmer}"

    if (enabledebug) {
        def message = [
            "<div style='background:darkgray;color:darkblue;display:inline-block:position:relative;inset-inline-start:-20%'> ",
            "<br> --------------NEED--------------------- ",
            "<br>toohot = $toohot  ",
            "<br>swamp = $swamp ",
            "<br> insideHum = $insideHum ",
            "<br> humidity = $humidity ",
            "<br> inside = $inside ",
            "<br> swing = $swing ",
            "<br> outside >= outsideThres + 5 = ${outside >= outsideThres + 5} ",
            "<br> outside = $outside ",
            "<br> outsideThres + 5 = ${outsideThres + 5} ",
            "<br> needCool = $needCool ",
            "<br> thermModes = $thermModes ",
            "<br> simpleModeActive = $simpleModeActive ",
            "<br> simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ",
            "<br> state.userWantsCooler = $userWantsCooler ",
            "<br> inWindowsModes = $inWindowsModes ",
            "<br> power saving management= ${powersavingmode ? "$powersavingmode inPowerSavingMode = $inPowerSavingMode":"option not selected by user"} ",
            "<br> amplitude = $amplitude ",
            "<br> amplitudeTooHigh = $amplitudeTooHigh ",
            "<br>  ",
            "<br> humidity = ${humidity}% ",
            "<br> insideHum = ${insideHum}% ",
            "<br>  ",
            "<br> outside = $outside ",
            "<br> inside = $inside ",
            "<br> criticalhot = $criticalhot ",
            "<br> criticalcold = $criticalcold ",
            "<br> target = $target ",
            "<br>  ",
            "<br> swing = $swing ",
            "<br>  ",
            "<br> inside > target = ${inside > target} ",
            "<br> inside < target = ${inside < target} | $inside < $target ",
            "<br>  ",
            "<br> simpleModeActive = $simpleModeActive  ",
            "<br> contactsClosed = $contactsClosed  ",
            "<br> outsideThres = $outsideThres ",
            "<br> outside > target = ${outside > target} ",
            "<br> outside < target = ${outside < target} ",
            "<br> outside >= outsideThres = ${outside >= outsideThres} ",
            "<br> outside < outsideThres = ${outside < outsideThres} ",
            "<br>  ",
            "<br> needCool = $needCool ",
            "<br> needHeat = $needHeat (needHeat supercedes needCool)  ",
            "<br>  ",
            "<br> final NEED value = $need ",
            "<br> --------------------------------------- ",
            "</div> ",
        ]
        log.debug format_text(message.join(), "white", "blue")
    }
}

/* ############################### SETTERS ############################### */

def set_multiple_thermostats_mode(mode, origin, safeValue){


    if (enablewarning) log.warn "--                                               set_multiple_thermostats_mode                                                --"

    boolean forceCommand = origin == "checkthermstate force command" || origin == "getNeed() criticalhot event"

    if (differentiateThermostatsHeatCool) {            

        def neededThermostats = get_needed_thermosats(mode)

        for (therm in neededThermostats) {
            if ((useBothThermostatsForHeat && mode in ["heat", "off"]) || (useBothThermostatsForCool && mode in ["cool", "off"])) {

                simpleModeActive = simpleModeIsActive()

                if (forceCommand) {
                    if (enabledebug) log.debug "therm.displayName => ${therm.displayName}"
                    try {
                        log.debug "setting $therm to $mode"
                        // set_thermostat_mode(therm, mode, origin)
                        def check = check_simplemode_and_exclusive_thermostat(mode, therm, simpleModeActive, origin)

                        // antifreeze emergency command
                        if (safeValue && !simpleModeActive) {
                            therm.setHeatingSetpoint(safeValue)
                        }
                    }
                    catch (Exception e) {
                        log.error "object class instance error for ${therm.displayName}: ${e}"
                    }
                }
                else {
                    if (check_simplemode_and_exclusive_thermostat(mode, therm, simpleModeActive, origin)) {
                        break // if true, that means we are in simpleModeActive with exclusive thermosat use, so no need to loop through the list of thermostats
                    }
                }
            }
            else {
                if (!useBothThermostatsForHeat && mode == "heat") {
                    set_thermostat_mode(thermostatHeat, mode, origin + " one to heat") // set the thermostat needed for the other mode
                    set_thermostat_mode(thermostatCool, "off", origin + "Turning off $thermostatCool in simple mode")
                }
                else if (!useBothThermostatsForCool && mode == "cool") {
                    set_thermostat_mode(thermostatCool, mode, origin + " one to cool")
                    set_thermostat_mode(thermostatHeat, "off", origin + "Turning off $thermostatHeat in simple mode")
                }
                break // not a double thermosat command case, exit the loop
            }
        }
    }
    else {
        set_thermostat_mode(thermostat, mode, origin) // set the thermostat  
        if (safeValue) {
            thermostat.setHeatingSetpoint(safeValue)
        }
    }
}
def check_simplemode_and_exclusive_thermostat(mode, therm, simpleModeActive, origin){

    try {
        // ignore therm parameter and apply simple mode exceptions
        if (simpleModeActive && mode == "cool" && useOnlyThermostatCoolForCoolInSimpleMode) {
            if (enabledebug) log.debug "simplemode is active and user chose to use only one thermostat for cool"
            set_thermostat_mode(thermostatCool, mode, origin + "only $thermostatCool cools in simple mode")
            set_thermostat_mode(thermostatHeat, "off", origin + "Turning off $thermostatHeat in simple mode")
            return true
        }
        else if (simpleModeActive && mode == "heat" && useOnlyThermostatHeatForHeatInSimpleMode) {
            if (enabledebug) log.debug "simplemode is active and user chose to use only one thermostat for heat"
            set_thermostat_mode(thermostatHeat, mode, origin + "only $thermostatHeat heats in simple mode")
            set_thermostat_mode(thermostatCool, "off", origin + "Turning off $thermostatCool in simple mode")
            return true
        }
        else {
            if (enabledebug) log.debug "setting $therm to '$mode'"
            set_thermostat_mode(therm, mode, origin)
            return false
        }
    }
    catch (Exception e) {
        if (enablewarning) log.warn "object class instance error for ${therm.displayName}"
    }
}
def set_multiple_thermostats_fan_mode(fanMode, origin){

    if (differentiateThermostatsHeatCool) {    
        def need = state.lastNeed // can't call get_need() from here because set_multiple_thermostats_fan_mode is called by it: infinite callback loop

        for (therm in get_needed_thermosats(need)) {
            therm.setThermostatFanMode(fanMode)
        }

    }
    else {
        thermostat.setThermostatFanMode(fanMode)
    }
}
def set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, origin){
    if (differentiateThermostatsHeatCool) {
        def simpleModeActive = simpleModeIsActive()     
        def need = get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, neededThermostats, thermModes, humThres, "set_target")   
                            
        def neededThermostats = get_needed_thermosats(need)

        for (int i = 0; i < neededThermostats.size(); i++)
        {
            boolean override = neededThermostats[i].displayName == exceptForThermostatCool.displayName || neededThermostats[i].displayName == exceptForThermostatHeat.displayName
            set_thermostat_target(neededThermostats[i], cmd, target, override, origin)
        }
    }
    else {
        set_thermostat_target(thermostat, cmd, target, false, origin)
    }
}
def set_thermostat_mode(t, mode, origin){
    if (enablewarning) log.warn "set_thermostat_mode called from $origin"
    try {
        if (t.currentValue("thermostatMode") != mode || origin in ["checkthermstate force command", "remainsOffCmd"]) {
            if (enabledebug) log.trace  "$t set to $mode (origin: $origin)"
            if (autoOverride && t.currentValue("thermostatMode") == "auto") {
                if (enablewarning) log.warn "$t is in auto mode - command to set to $mode ignored..."
            }
            else {
                t.setThermostatMode(mode)
            }
        }
        else {
            if (enabledebug) log.trace  "$t already set to $mode (set_thermostat_mode origin: $origin)"
        }
    }
    catch (Exception e) {
        if (enablewarning) log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}
def set_thermostat_target_ignore_setpoint(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, origin){
    /**
     * Manages setpoint changes for dual thermostat setups (separate cooling and heating thermostats).
     * 
     * This function serves as a control layer for systems using separate thermostats for cooling and heating.
     * It determines which thermostat should receive setpoint commands based on:
     * 1. The current need (cooling or heating)
     * 2. User preferences for ignoring setpoints
     * 3. Exceptions for specific thermostats in certain modes
     * 4. Power saving modes
     * 
     * Key features:
     * - Respects the 'ignoreTarget' setting, but allows exceptions
     * - Handles special cases for cooling and heating thermostats separately
     * - Considers power saving modes which may override other settings
     * - Ensures safety conditions are met before applying or ignoring setpoints
     * 
     * If conditions are met for changing the setpoint, it calls set_target() to apply the change
     * to the appropriate thermostat.
     * 
     * @param cmd The command to be sent (e.g., "setCoolingSetpoint" or "setHeatingSetpoint")
     * @param target The target temperature to be set
     * @param inside Current inside temperature
     * @param outside Current outside temperature
     * @param motionActive Whether motion is currently detected
     * @param doorsContactsAreOpen Whether doors/contacts are currently open
     * @param thermModes Current thermostat modes
     * @param humThres Humidity threshold
     * @param origin A string indicating where this function call originated from
     */
    
    boolean inpowerSavingMode = location.mode in powersavingmode
    try {
        boolean ignore = checkIgnoreTarget()
    } catch (Exception e) {
        log.error "checkIgnoreTarget (called from set_thermostat_target_ignore_setpoint): $e"
    }

    def debug = [
        "checkIgnoreTarget: $ignore",
        "cmd: $cmd",
        "exceptForThermostatCool: $exceptForThermostatCool",
        "exceptForThermostatHeat: $exceptForThermostatHeat",
        "inpowerSavingMode: $inpowerSavingMode"
    ]

    if (enabledebug) log.debug debug_from_list(debug)

    try {
        if (ignore && !inpowerSavingMode && !exceptForThermostatCool && !exceptForThermostatHeat) {
            /*if (enabledebug)*/ log.trace  "Target ($target) temp not sent to $thermostat at user's request"
            return
        }
        else if (differentiateThermostatsHeatCool && (exceptForThermostatCool || exceptForThermostatHeat) && !inpowerSavingMode) {

            if (exceptForThermostatHeat) {
                try {
                    if (dev_mode) log.debug "set_thermostat_target called for exceptForThermostatHeat"
                    set_thermostat_target(thermostatHeat, cmd, exceptForThermostatHeat, target, "set_thermostat_target_ignore_setpoint/exceptForThermostatHeat") //
                } catch (Exception e) {
                    log.error "set_thermostat_target thermostatHeat:$thermostatHeat (called from set_thermostat_target_ignore_setpoint): $e"
                }
            }

            else if (exceptForThermostatCool) {
                try {
                    if (dev_mode) log.debug "set_thermostat_target called for exceptForThermostatCool"
                    set_thermostat_target(thermostatCool, cmd, target, exceptForThermostatCool, origin)
                } catch (Exception e) {
                    log.error "set_thermostat_target thermostatCool:$thermostatCool (called from set_thermostat_target_ignore_setpoint): $e"
                }
            }
        }
        else {
            try {
                set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, contactsClosed, thermModes, humThres, "dsfszd") //
                // def set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, humThres, origin){

            } catch (Exception e) {
                log.error "set_thermostat_target thermostatCool:$thermostatCool (called from set_thermostat_target_ignore_setpoint): $e"
            }
        }
    } catch (Exception e) {
        log.error "set_thermostat_target_ignore_setpoint: $e"
    }
}
def set_thermostat_target(t, cmd, target, override, origin){
    if (dev_mode) log.debug "set_thermostat_target called from $origin | ignoreTarget: $ignoreTarget"

    if (ignoreTarget && !override) {
        if (dev_mode) log.debug "set_thermostat_target exiting due to ignoreTarget setting"
        return
    }
    try {
        def query = cmd == "setCoolingSetpoint" ? "coolingSetpoint" : cmd == "setHeatingSetpoint" ? "heatingSetpoint" : "thermostatSetpoint"
        if (t.currentValue(query) != target || origin == "checkthermstate force command") {
            if (enabledebug) log.trace  "$t set to $target (origin: $origin)"
            t."${cmd}"(target)
        }
        else {
            if (enabledebug) log.trace  "$t already set to $target (set_thermostat_target origin: $origin)"
        }
    }
    catch (Exception e) {
        if (enablewarning) log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}
def turn_off_thermostats(need, inside, thermModes, contactsOpen) {

    if (enablewarning) log.warn "--                                                         turn_off_thermostats                                                         --"


    if ((state.lastNeed == "heat" && inside < criticalcold) || (state.lastNeed == "cool" && inside > criticalhot)) {
        if (enablewarning) log.warn "not turning off thermostat because inside temp is ${inside < criticalcold ? 'too low' : 'too hot'}"
        return
    }
    else {
        boolean timeIsup = check_contacts_delay(contactsOpen) // manages time delay for when some contacts, if any, have been opened
        boolean inpowerSavingMode = location.mode in powersavingmode
        if (!fanCirculateAlways && alwaysButNotWhenPowerSaving) app.updateSetting("alwaysButNotWhenPowerSaving", [type: "bool", value: false]) // fool proofing 

        if (fanCirculateAlways || (alwaysButNotWhenPowerSaving && Active() && !inpowerSavingMode)) {
            log.trace  "fanCirculateAlways (or related options) returns true, ignoring off command"
            return
        }
        if (simpleModeIsActive() && dontTurnOffinNightMode) {
            if (enablewarning) log.warn "NOT Turning off thermostats because in simple mode and dontTurnOffinNightMode is true"
            return
        }
        else if (!doNotSendAnyCoolHeatOffComm || (contactsOpen && timeIsup) || !Active() || offrequiredbyuser) {
            set_multiple_thermostats_mode("off", "turn_off_thermostats(${need}, ${inside})", null)
        }
    }
}

/* ############################### DECISIONS & A.I. LEARNING (beta 2 October 2023) ############################### */

Boolean createFile(String fName, String fData) {
    try {
        def params = [
            uri: 'http://127.0.0.1:8080',
            path: '/hub/fileManager/upload',
            query: [
                'folder': '/'
            ],
            headers: [
                'Content-Type': 'multipart/form-data; boundary=----BoundaryStringKDJfkhsdkhfzuUUUenfunrghedkh'
            ],
            body: """------BoundaryStringKDJfkhsdkhfzuUUUenfunrghedkh
Content - Disposition: form - data; name =\"uploadFile\"; filename=\"${fName}\"
Content - Type: text / plain
${ fData }
------BoundaryStringKDJfkhsdkhfzuUUUenfunrghedkh
Content - Disposition: form - data; name =\"folder\"
------BoundaryStringKDJfkhsdkhfzuUUUenfunrghedkh--""",
            timeout: 300,
            ignoreSSLIssues: true
        ]
        httpPost(params) {
            resp ->
                if (enableinfo) log.info resp.data.status;
            return resp.data.success;
        }
    } catch (Exception e) {
        log.error "Error creating file $fName: ${e}"
        return false;
    }
}
def call_create_file(){
    if (is_dev_app()) {
        if (enabledebug) log.debug "This is Elfege's sandbox... "

        try {
            String fileName = "myFile2.txt";
            // Check if the file already exists
            if (!file_exists(fileName)) {
                // Create a text file named "myFile.txt" with the initial content "Hello, World!"
                Boolean result = createFile(fileName, "Hello, World!");

                // Check if the file was successfully created
                if (result) {
                    if (enableinfo) log.info "File successfully created.";
                } else {
                    log.error "Failed to create the file.";
                }
            } else {
                if (enableinfo) log.info "File already exists. No need to create.";
            }
        }
        catch (Exception e) {
            log.error "Error attempting to create a file: $e";
        }
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
        httpGet(uri) {
            resp ->
                if (enabledebug) log.debug "HTTP Response Code: ${resp.status}"
            if (enabledebug) log.debug "HTTP Response Headers: ${resp.headers}"
            if (resp.success) {
                if (enabledebug) log.debug "HTTP GET successful."
                fileData = resp.data.text
                // if(enabledebug) log.debug "resp.data =================================> \n\n ${resp.data}"
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

    // if(enabledebug) log.trace "deserializeHashTable ====> $jsonString"

    JsonSlurper jsonSlurper = new JsonSlurper()
    return jsonSlurper.parseText(jsonString)
}
// Function to learn from a new setpoint
def learn(value) {
    def conditions = get_conditions()
    def dimmerValue = value ? value : get_dimmer_value()

    // Calculate grid ID (simplified example)
    def gridID = conditions.join("-")

    // Read existing hash table
    def hashTableJson = readFromFile("hash_table.txt")

    if (enabledebug) log.debug "hashTableJson ===> $hashTableJson"

    def hashTable = deserializeHashTable(hashTableJson)

    // Update hash table
    hashTable[gridID] = dimmerValue

    // Write updated hash table back to file
    def newHashTableJson = serializeHashTable(hashTable)
    writeToFile("hash_table.txt", newHashTableJson)
}
// Reset database function
def reset_db() {
    // Initialize an empty hash table
    def hashTable = [: ]
    if (enablewarning) log.warn "DATABASE DROPPED !"

    def indoorTempRange = [65, 70, 75]
    def outdoorTempRange = [30, 50, 70, 90, 100]
    def indoorHumidityRange = [20, 40, 60]
    def outdoorHumidityRange = [20, 50, 80, 100]

    for (indoorTemp in indoorTempRange) {
        for (outdoorTemp in outdoorTempRange) {
            for (indoorHumidity in indoorHumidityRange) {
                for (outdoorHumidity in outdoorHumidityRange) {
                    def conditions = [indoorTemp, outdoorTemp, indoorHumidity, outdoorHumidity]
                    def conditionsKey = conditions.join('-')
                    def initialValue = calculateComfortScore(indoorTemp, outdoorTemp, indoorHumidity, outdoorHumidity)
                    hashTable[conditionsKey] = Math.round(initialValue)
                }
            }
        }
    }
    def newHashTableJson = serializeHashTable(hashTable)
    writeToFile("hash_table.txt", newHashTableJson)
    if (enablewarning) log.warn "Database has been reset and populated."
}
// HELPER FUNCTION TO CALCULATE COMFORT SCORE
def calculateComfortScore(indoorTemp, outdoorTemp, indoorHumidity, outdoorHumidity) {
    // Higher weight to indoor conditions
    return (0.6 * indoorTemp + 0.2 * indoorHumidity + 0.1 * outdoorTemp + 0.1 * outdoorHumidity) / 4
}
// Function to calculate the Wet-Bulb temperature as the default setpoint
def defaultSetpoint(insideTemp = null, insideHum = null, dimmerPref = null) {
    // Fetch the current indoor temperature, humidity, and other factors
    def currentInsideTemp = get_inside_temperature()
    def currentInsideHumidity = get_inside_humidity()

    // Define personal preference (0 for neutral, positive for warmer, negative for cooler)
    def personalPreference = 0

    dimmerPreference = get_dimmer_value()
    // Calculate the PMV (Predicted Mean Vote) using environmental parameters
    def pmv = calculatePMV(currentInsideTemp, currentInsideHumidity, dimmerPreference)

    // Adjust the personal preference based on PMV
    if (pmv > 0) {
        personalPreference += 1 // Slightly warmer
    } else if (pmv < 0) {
        personalPreference -= 1 // Slightly cooler
    }

    // Calculate the ideal room temperature based on the adjusted personal preference
    def idealTemperature = currentInsideTemp + personalPreference

    // Ensure the ideal temperature is within a reasonable range
    idealTemperature = Math.max(65, Math.min(78, idealTemperature))

    return idealTemperature
}
// Function to calculate PMV based on temperature, humidity, and dimmer preference
def calculatePMV(temperature, humidity, dimmerPreference) {
    // Here, we implement a simplified PMV calculation
    // using the ASHRAE Standard 55 or ISO 7730 guidelines.
    // This calculation can be more complex and depends on
    // factors like clothing insulation, metabolic rate, etc.

    // For simplicity, we'll return a constant value for now.

    // Calculate a comfort score based on temperature and humidity.
    def comfortScore = temperature - (humidity / 2)

    // Adjust the comfort score based on the user's dimmer preference.
    comfortScore += dimmerPreference / 10 // Adjust as needed

    // Map the comfort score to a PMV value (-2 to 2 for simplicity).
    def pmv = mapComfortScoreToPMV(comfortScore)

    return pmv
}
// Function to map a comfort score to a PMV value (simplified)
def mapComfortScoreToPMV(comfortScore) {
    // Map the comfort score to a PMV value (-2 to 2).
    // You can fine-tune this mapping based on your comfort model.
    if (comfortScore < -2) {
        return -2
    } else if (comfortScore > 2) {
        return 2
    } else {
        return comfortScore
    }
}
def convert_db_to_celsius() {

    if (state.currentUnit == "fahrenheit") {
        // Read existing hash table from HTTP server
        def hashTableJson = readFromFile("hash_table.txt")
        def hashTable = deserializeHashTable(hashTableJson)

        // Convert each dimmer value to Celsius
        try {
            hashTable.collectEntries {
                key, value ->
                    [(key): (value - 32) * 5 / 9]
            }
        }
        catch (Exception e) {
            if (enablewarning) log.warn"hastable not computable at the moment... "
        }

        // Serialize and write the updated hash table back to the HTTP server
        def newHashTableJson = serializeHashTable(hashTable)
        writeToFile("hash_table.txt", newHashTableJson)

        state.currentUnit = "celsius"

        if (enablewarning) log.warn "Database has been converted to Celsius."
    }
    else {
        if (enablewarning) log.warn "database already in celsius"
    }
}
def convert_db_to_fahrenheit() {

    if (state.currentUnit == "celsius") {

        // Read existing hash table from HTTP server
        def hashTableJson = readFromFile("hash_table.txt")
        def hashTable = deserializeHashTable(hashTableJson)

        // Convert each dimmer value to Fahrenheit
        hashTable.collectEntries {
            key, value ->
                [(key): (value * 9 / 5) + 32]
        }

        // Serialize and write the updated hash table back to the HTTP server
        def newHashTableJson = serializeHashTable(hashTable)
        writeToFile("hash_table.txt", newHashTableJson)

        if (enablewarning) log.warn "Database has been converted to Fahrenheit."

        state.currentUnit = "celsius"
    }
    else {
        if (enablewarning) log.warn "database already in fahrenheit"
    }
}

/* ############################### BOOLEANS ###############################****** */
boolean time_is_up(long start_time, override = false){
    def threshold = is_dev_app() ? 30.0 : 30.0
    float duration = (now() - start_time) / 1000
    state.stop = state.stop == null ? false : state.stop
    result = duration >= threshold || state.stop
    if (enabledebug || dev_mode) log.debug "time_is_up() returns: $result"
    return result
}
boolean operatingStateOk(contactsClosed, doorsContactsAreOpen, currentOperatingState, currentOperatingNeed){
    
    def result = true
    try {

        if (enablewarning) log.warn "Main thermostat = $thermostat"
        if (enabledebug) log.debug "currentOperatingNeed = $currentOperatingNeed && need = $need |thermostat?.currentValue('thermostatOperatingState') = ${thermostat.currentValue('thermostatOperatingState')}"
        if (enabledebug) log.debug "${thermostat?.currentValue('thermostatOperatingState') == currentOperatingNeed}"

        state.lastSetTime = state.lastSetTime != null ? state.lastSetTime : now() + 31 * 60 * 1000



        currentOperatingState = thermostat.currentValue("thermostatOperatingState")

        if (differentiateThermostatsHeatCool) {
            if (enablewarning) log.warn "neededThermostats: ${neededThermostats?.join(', ')}"



            currentOperatingStates = neededThermostats.collect { it.currentValue("thermostatOperatingState") }
            allOk = !currentOperatingStates.any { it -> it != currentOperatingNeed || it == "fanCirculate" }
            if (enabledebug) log.debug "contactsClosed && !doorsContactsAreOpen => ${contactsClosed && !doorsContactsAreOpen}"

            result = contactsClosed && !doorsContactsAreOpen ? allOk : true

            if (enabledebug || !result) {
                log.warn "currentOperatingNeed => $currentOperatingNeed"
                log.warn "currentOperatingStates => $currentOperatingStates"
                log.warn "allOk => $allOk"
                log.warn "result => $result"
            }


        }
        else {

            if (enabledebug) {
                log.debug("contactsClosed => ${contactsClosed}")
                log.debug("doorsContactsAreOpen => ${doorsContactsAreOpen}")
                log.debug("currentOperatingState => ${currentOperatingState}")
                log.debug("currentOperatingNeed => ${currentOperatingNeed}")
            }
            result = contactsClosed && !doorsContactsAreOpen ? currentOperatingState in [currentOperatingNeed, "fanCirculate"] : true



        }

    } catch (Exception e) {
        log.error "operatingStateOk() => $e"
    }

    return result

}
boolean simpleModeIsActive(){
    state.lastButtonEvent = state.lastButtonEvent != null ? state.lastButtonEvent : now()
    boolean result = false
    //boolean doorOpen = doorsOpen() // FEEDBACK LOOP since doorsOpen() function calls simpleModeIsActive()
    boolean currentlyClosed = false

    if (!UseSimpleMode) {
        return false
    }
    if (UseSimpleMode) {
        result = state.buttonPushed
    }
    if (UseSimpleMode && simpleModeTimeLimit && state.buttonPushed) // if user set a time limit
    {     
        def remainTime = get_thermostat_that_must_remain_off(simpleModeTimeLimit, state.lastButtonEvent)
        def message = "$simpleModeName Mode - remaining time: ${remainTime}"
        if (enableinfo) log.info format_text(message, "white", "grey")

        if (remainTime <= 0) // time is up
        {
            result = false
            state.buttonPushed = false
        }
    }

    if (enabledebug) log.debug "$simpleModeName Mode trigger boolean returns $result"

    return result
}
boolean contactsAreOpen(){
    def listOfOpenContacts = []
    listOfOpenContacts = WindowsContact?.findAll{ it.currentValue("contact") == "open" }
    boolean someAreOpen = listOfOpenContacts.size() > 0

    if (listOfOpenContacts.size() != 0 || is_dev_app()) log.info "------------------ windows open ?: ${listOfOpenContacts.join(', ')}"

    if (someAreOpen && override_contacts_in_simple_mode && simpleModeIsActive()) {
        log.warn format_text("------ IGNORING CONTACTS due to $simpleModeName mode ------", "black", "yellow")
        return false
    }
    if (WindowsContact) {
        boolean Open = WindowsContact?.any{ it -> it.currentValue("contact") == "open" }

        state.listOfOpenContacts = listOfOpenContacts.join(", ")
        if (state.listOfOpenContacts.size() > 0) {
            if (enabledebug) log.trace "Contacts are open: ${listOfOpenContacts}"
        }
        return Open
    }
    else {
        return false
    }
}
boolean doorsOpen(){
    if (simpleModeIsActive()) {
        return false
    }

    boolean Open = false
    def listOpen = []

    if (doorsContacts) {
        listOpen = doorsContacts?.findAll{ it?.currentValue("contact") == "open" }
        Open = doorsContacts?.any{ it -> it.currentValue("contact") == "open" }
    }

    if (enableinfo || is_dev_app()) log.info "------------------ doors: $doorsContacts open ?: ${listOpen.join(', ')}"
    return Open
}
boolean Active(){
    boolean result = true // default is true  always return Active = true when no sensor is selected by the user

    try {
        if (simpleModeIsActive()) return true

        if (motionSensors) {
            // def currentModeMotionTimeout = settings.find{it?.key == "noMotionTimeWithMode${location.mode}"}?.value?.toInteger()        
            def modeTimeVal = motionmodes?.size() > 1 ? settings.find{ it?.key == "noMotionTimeWithMode${location.mode}" }?.value?.toInteger() : 0
            modeTimeVal = modeTimeVal != null && modeTimeVal != 0 ? modeTimeVal : noMotionTime // extra precaution, probably useless... 
            long Dtime = motionmodes?.size() > 1 ? modeTimeVal * 1000 * 60 : noMotionTime != null && noMotionTime != 0 ? noMotionTime * 1000 * 60 : 30 * 1000 * 60 // in case noMotionTime is compromised, set a 30 minutes default. Another probably useless precaution... 
            boolean inMotionMode = location.mode in motionmodes

            if (inMotionMode) {
                if (testMotionBattery) {
                    def devicesWithBatteryCapability = motionSensors.findAll{ it.hasCapability("Battery") } 
                    def batThreshold = lowBatLevel ? lowBatLevel : 40
                    def devicesWithLowBattery = devicesWithBatteryCapability.findAll{ it.currentValue("battery") <= batThreshold && it.currentValue("battery") > 0 } // cannot write expression "it -> it.currentValue..." inside a dynamic page for some reason... 
                    if (devicesWithLowBattery.size() != 0) {
                        state.lowBattery = true
                        def m = "LOW BATTERY: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                        if (devicesWithLowBattery.size() == motionSensors.size()) {
                            if (enablewarning) log.warn format_text(m, "white", "red")
                            m = "All motion sensors' batteries are DEAD! Motion test returns true as a safety measure. Make sure to replace the battery of the following devices: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                            if (enablewarning) log.warn format_text(m, "white", "grey")
                            return true
                        }
                        else {
                            if (enablewarning) log.warn format_text(m, "white", "red")
                        }
                    }
                    else {
                        state.lowBattery = false
                    }
                }

                result = get_last_motion_event(Dtime, "motionTest") > 0

            }
            else {
                if (enabledebug) log.trace "motion returns true because outside of motion modes"
            }

            // this must happen outside of get_last_motion_event() collection, because the latter isn't called when outside of motion modes.  
            state.activeMotionCount = state.activeMotionCount ? state.activeMotionCount : 0

            // if(enabledebug) log.debug "now() - state.lastMotionEvent > 1000 => ${(now() - state.lastMotionEvent) > 1000}"
            if ((now() - state.lastMotionEvent) > Dtime && state.activeMotionCount != 0) // if time is up, reset atomicState events value
            {

                state.activeMotionCount = 0 // time is up, reset this variable
                // if(enabledebug) log.debug "state.activeMotionCount set to $state.activeMotionCount"
                //events = 0
            }
        }
        else {
            if (enabledebug) log.debug "user did not select any motion sensor"
        }
    } catch (Exception e) {
        log.error "Active() => $e"
        result = true // default for safety
    }

    if (enabledebug) log.debug "motion test returns $result"
    return result
}
boolean check_contacts_delay(contactsClosed){
    // this function is mostly meant to provide delay between open contact event and turn off
    state.lastContactOpenEvt = state.lastContactOpenEvt ? state.lastContactOpenEvt : now()
    def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0

    boolean result = true

    if (enablewarning) log.warn "-- check_contacts_delay --"
    if (!contactsClosed) {
        if (fanCirculateAlways || (alwaysButNotWhenPowerSaving && Active() && !inpowerSavingMode)) {
            result = false
        }
        if (now() - state.lastContactOpenEvt > delayB4TurningOffThermostat) {
            if (enabletrace) log.trace "check_contacts_delay() returning true zgf45"
            result = true
        }
        else {
            if (enabledebug) log.trace  "contacts are open, thermostat will be set to off in ${(delayB4TurningOffThermostat/1000)-((now() - state.lastContactOpenEvt)/1000)} seconds"
            result = false
        }
    }
    else {
        if (enablewarning) log.warn "doNotSendAnyCoolHeatOffComm = $doNotSendAnyCoolHeatOffComm"
        if (doNotSendAnyCoolHeatOffComm && Active() && !offrequiredbyuser) return false;
        if (enableinfo) log.info "check_contacts_delay() returning true 5gr4"
        return true // if contacts are closed, any other request to turn off the AC coming from this app must be granted
    }

    if (enableinfo) log.info "check_contacts_delay returns $result"
    return result
}
boolean checkIgnoreTarget(){
    def result = simpleModeIsActive() && doNotIgnoreTargetInSimpleMode ? false : ignoreTarget
    return result
}
boolean need_to_wait_between_modes(String need, double inside, double perceivedTemp, int target) {
    if (state.force_wait) {
        log.debug "Waiting between modes due to humidity conditions (force_wait flag is true)"
        return true
    }

    try {
        if (enabledebug) {
            log.debug "Inside: $inside, Target: $target, Perceived Temperature: $perceivedTemp, Last Need: $state.lastNeed, Need: $need"
        }

        def offset = 1
        boolean inertialRange = need == "heat" && perceivedTemp >= inside + offset ? true /** let inertial heat do its work */ : need == "cool" && perceivedTemp <= inside - offset /* Allow entropy */ ? true : false

        if (inertialRange == null) {
            log.debug "Inertial range is null, not waiting between modes"
            return false
        }

        def cause = ""
        if (need == "heat" && perceivedTemp >= target + offset) {
            cause = "letting inertial heat do its work"
        } else if (need == "cool" && perceivedTemp <= target - offset) {
            cause = "allowing entropy"
        }
        if (enableinfo) log.info "Need to wait between modes: ${inertialRange ? "Yes" : "No"} ${inertialRange ? "(Need: $need, Cause: $cause)": ""} | -- | perceivedTemp: $perceivedTemp | inside: $inside | offset: $offset"
        return inertialRange
    }
    catch (Exception e) {
        log.error("Error in need_to_wait_between_modes: $e")
        return false
    }
}
Boolean file_exists(String fName) {
    def uri = "http://127.0.0.1:8080/local/${fName}"
    def params = [uri: uri]

    try {
        httpGet(params) {
            resp ->
            return resp.status == 200
        }
    } catch (Exception e) {
        return false
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

/* ################################# POLLING AND LOGGING ################################# */

def stop(data){
    if (enablewarning) log.warn "STOP customCommand = $customCommand"

    def cmd = customCommand ? customCommand.minus("()") : "off"

    if (differentDuration) {

        def dev = settings["windows"].find{ it.name == data.device }
        if (enabledebug) log.trace "differentiated scheduled STOP for ${dev}"

        dev."${cmd}"()
    }
    else {

        int s = windows.size()
        int i = 0
        for (s != 0; i < s; i++) {
            windows[i]."${cmd}"()
            if (enablewarning) log.warn "${windows[i]} $customCommand"
        }

    }

}
def Poll(){

    if (location.mode in restricted) {
        if (enableinfo) log.info "location in restricted mode, doing nothing"
        return
    }
    if (!polldevices) return

    if (state.paused == true) {
        return
    }

    def neededThermostats = get_needed_thermosats(state.lastNeed)

    if (enabledebug) log.trace "POLLING THERMOSTATS"

    boolean override = state.override   
    boolean outsidePoll = outsideTemp.hasCommand("poll")
    boolean outsideRefresh = outsideTemp.hasCommand("refresh")

    if (differentiateThermostatsHeatCool) {

        boolean thermPoll = thermostat.hasCommand("poll")
        boolean thermRefresh = thermostat.hasCommand("refresh")

        for (t in neededThermostats) {
            if (enabledebug) log.debug "t?.displayName => ${t?.displayName}"
            try {
                if (enabledebug) log.trace "REFRESHING "
                t?.refresh()
            }
            catch (Exception e) {
                if (enablewarning) log.warn "Could not refresh ${t?.displayName} $e"
                try {
                    t?.poll()
                }
                catch (Exception err) {
                    if (enablewarning) log.warn "Could not poll ${t?.displayName} $err"
                }
            }
        }

    }
    else {
        
        boolean thermPoll = thermostat.hasCommand("poll")
        boolean thermRefresh = thermostat.hasCommand("refresh")


        if (thermRefresh) {
            thermostat.refresh()
            if (enableinfo) log.info "refreshing $thermostat"
        }
        if (thermPoll) {
            thermostat.poll()
            if (enableinfo) log.info "polling $thermostat"
        }
    }

    if (sensor && !preserveSensorBatteryLife) {

        for (s in sensor) {
            
            boolean sensor_has_refresh = s.hasCommand("refresh")
            boolean sensor_has_poll = s.hasCommand("poll")
            def cmd = sensor_has_refresh ? "refresh()" : sensor_has_poll ? "poll()" : null

            if (cmd == null) {
                if (enablewarning) log.warn "$s has no refresh nor polling capability???"
            }
            else {
                try {
                    if (enabledebug) log.trace "REFRESHING $s"
                    s.cmd
                }
                catch (Exception e) {
                    try {
                        if (enabledebug) log.trace "POLLING $s"
                        s.poll()
                    }
                    catch (Exception err) {
                        if (enablewarning) log.warn "Couldn't refresh nor poll ${s.displayName}"
                    }
                }
            }

        }
    }


    if (windows) {
        if (enabledebug) log.trace "POLLING WINDOWS"
        boolean windowsPoll = windows.findAll{ it.hasCommand("poll") }.size() == windows.size()
        boolean windowsRefresh = windows.findAll{ it.hasCommand("refresh") }.size() == windows.size()


        for (window in windows) {
            if (windowsRefresh) {
                window.refresh()
                if (enableinfo) log.info "refreshing $window"
            }
            else if (windowsPoll) {
                window = windows[i]
                window.refresh()
                if (enableinfo) log.info "refreshing $window"
            }
        }


    }

}
def poll_power_meters(){

    if (!polldevices) return
    
    boolean heaterPoll = heater?.hasCommand("poll")
    boolean heaterRefresh = heater?.hasCommand("refresh") 
    boolean coolerPoll = cooler?.hasCommand("poll")
    boolean coolerRefresh = cooler?.hasCommand("refresh") 
    boolean pwPoll = pw?.hasCommand("poll")
    boolean pwRefresh = pw?.hasCommand("refresh")

    if (pwRefresh) {
        pw.refresh()
        if (enableinfo) log.info "refreshing $pw 5df4"
    }
    if (pwPoll) {
        pw.poll()
        if (enableinfo) log.info "polling $pw"
    }
    if (heaterRefresh) {
        heater?.refresh()
        if (enableinfo) log.info "refreshing $heater"
    }
    if (heaterPoll) {
        heater?.poll()
        if (enableinfo) log.info "polling $heater"
    }
    if (coolerRefresh) {
        cooler?.refresh()
        if (enableinfo) log.info "refreshing $cooler"
    }
    if (coolerPoll) {
        cooler?.poll()
        if (enableinfo) log.info "polling $cooler"
    }
    state.lastPoll = now()
}
def disable_logging(){
    if (enablewarning) log.warn "log.debug disabled..."
    app.updateSetting("enabledebug", [type: "bool", value: false])
}
def disable_description(){
    if (enablewarning) log.warn "description text disabled..."
    app.updateSetting("enableinfo", [type: "bool", value: false])
}
def disable_warnings(){
    if (enablewarning) log.warn "warnings disabled..."
    app.updateSetting("enablewarning", [type: "bool", value: false])
}
def disable_trace(){
    if (enablewarning) log.warn "trace disabled..."
    app.updateSetting("enabletrace", [type: "bool", value: false])
}
def format_text(title, textColor, bckgColor){
    return [
        "<div style='inline-size:80%;",
        "background-color:${bckgColor};",
        "border: 10px solid ${bckgColor};",
        "color:${textColor};",
        "font-weight: bold;",
        "box-shadow:4px 4px 4px #bababa;",
        "margin-inline-start:0px'>${title}",
        "</div>"
    ].join()
}
def format_title(title){
    return [
        "<div style=",
        "'background-color: lightgrey;",
        "inline-size: 80%;",
        "border: 3px solid green;",
        "padding: 10px;",
        "margin: 20px;'>${title}</div>"
    ].join()
}
def debug_from_list(msg){
    msg.join("\n")
}

def initialize_states(){
    log.debug "intializing states"

    state.appLabel = state.appLabel ? state.appLabel : ""
    state.button_name = state.button_name ? state.button_name : "pause"
    state.paused = state.paused ? state.paused : false
    state.lowBattery = state.lowBattery ? state.lowBattery : false
    state.lowBatterySensor = state.lowBatterySensor ? state.lowBatterySensor : false
    state.restricted = state.restricted ? state.restricted : false
    state.lastNeed = state.lastNeed ? state.lastNeed : "cool"
    state.antifreeze = state.antifreeze ? state.antifreeze : false
    state.setpointSentByApp = state.setpointSentByApp ? state.setpointSentByApp : false
    state.openByApp = state.openByApp ? state.openByApp : true
    state.closedByApp = state.closedByApp ? state.closedByApp : true
    state.lastPlay = state.lastPlay ? state.lastPlay : now()
    state.overrideTime = state.overrideTime ? state.overrideTime : now()
    state.resendAttempt = state.resendAttempt ? state.resendAttempt : now()
    state.offAttempt = state.offAttempt ? state.offAttempt : now()
    state.lastMotionEvent = state.lastMotionEvent ? state.lastMotionEvent : now()
    state.lastNotification = state.lastNotification ? state.lastNotification : now()
    state.userWantsWarmerTimeStamp = state.userWantsWarmerTimeStamp ? state.userWantsWarmerTimeStamp : now()
    state.userWantsCoolerTimeStamp = state.userWantsCoolerTimeStamp ? state.userWantsCoolerTimeStamp : now()
    state.setPointOverride = state.setPointOverride ? state.setPointOverride : false
    state.lastButtonEvent = state.lastButtonEvent ? state.lastButtonEvent : now()
    state.lastResultWasTrue = state.lastResultWasTrue ? state.lastResultWasTrue : true
    state.buttonPushed = state.buttonPushed ? state.buttonPushed : false
    state.EnableDebugTime = state.EnableDebugTime ? state.EnableDebugTime : now()
    state.enableDescriptionTime = state.enableDescriptionTime ? state.enableDescriptionTime : now()
    state.EnableWarningTime = state.EnableWarningTime ? state.EnableWarningTime : now()
    state.EnableTraceTime = state.EnableTraceTime ? state.EnableTraceTime : now()
    state.pageRefresh = state.pageRefresh ? state.pageRefresh : 0
    state.fanCirculateAlways = state.fanCirculateAlways ? state.fanCirculateAlways : false
    state.lastCheckTimer = state.lastCheckTimer ? state.lastCheckTimer : now()
    state.lastThermostatInput = state.lastThermostatInput ? state.lastThermostatInput : 72
    state.problemLogs = state.problemLogs ? state.problemLogs : []
    state.lastRebootTime = state.lastRebootTime ? state.lastRebootTime : now()
    state.numberOfReinit = state.numberOfReinit ? state.numberOfReinit : 0
    state.numberOfReboots = state.numberOfReboots ? state.numberOfReboots : 0
    state.severeLoad = state.severeLoad ? state.severeLoad : 0
    state.severeLoadTime = state.severeLoadTime ? state.severeLoadTime : now()
    state.run_pre_start = state.run_pre_start ? state.run_pre_start : now()
    state.stop = state.stop ? state.stop : false
    state.busy = state.busy ? state.busy : false
    state.lastBSeventStamp = state.lastBSeventStamp ? state.lastBSeventStamp : ""
    state.lastBSevent = state.lastBSevent ? state.lastBSevent : now()
    state.lastOpeningTime = state.lastOpeningTime ? state.lastOpeningTime : now()
    state.lastOpeningTimeStamp = state.lastOpeningTimeStamp ? state.lastOpeningTimeStamp : ""
    state.lastClosingTime = state.lastClosingTime ? state.lastClosingTime : now()
    state.lastClosingTimeStamp = state.lastClosingTimeStamp ? state.lastClosingTimeStamp : ""
    state.closingCommand = state.closingCommand ? state.closingCommand : true
    state.insideTempHasIncreased = state.insideTempHasIncreased ? state.insideTempHasIncreased : false
    state.widerOpeningDone = state.widerOpeningDone ? state.widerOpeningDone : false
    state.otherWindowsOpenByApp = state.otherWindowsOpenByApp ? state.otherWindowsOpenByApp : false
    state.outsideTempAtTimeOfOpening = state.outsideTempAtTimeOfOpening ? state.outsideTempAtTimeOfOpening : 70
    state.insideTempAtTimeOfOpening = state.insideTempAtTimeOfOpening ? state.insideTempAtTimeOfOpening : 72
    state.lastContactOpenEvt = state.lastContactOpenEvt ? state.lastContactOpenEvt : now()
    state.listOfOpenContacts = state.listOfOpenContacts ? state.listOfOpenContacts : []
    state.activeMotionCount = state.activeMotionCount ? state.activeMotionCount : 0
    state.lastSetTime = state.lastSetTime ? state.lastSetTime : now()
    state.coolerTurnedOnTimeStamp = state.coolerTurnedOnTimeStamp ? state.coolerTurnedOnTimeStamp : now()
    state.lastSetpointMessage = state.lastSetpointMessage ? state.lastSetpointMessage : now()
    state.forceLimit = state.forceLimit ? state.forceLimit : 5
    state.forceAttempts = state.forceAttempts ? state.forceAttempts : 0
    state.dontChekcThermostatStateCount = state.dontChekcThermostatStateCount ? state.dontChekcThermostatStateCount : 0
    state.fanOn = state.fanOn ? state.fanOn : false
    state.coolCmdSent = state.coolCmdSent ? state.coolCmdSent : false
    state.offCmdSent = state.offCmdSent ? state.offCmdSent : false
    state.windowsClosedDueToOutsideMode = state.windowsClosedDueToOutsideMode ? state.windowsClosedDueToOutsideMode : false
    state.simpleModeOverrideResetDone = state.simpleModeOverrideResetDone ? state.simpleModeOverrideResetDone : false
    state.confirmed = state.confirmed ? state.confirmed : "yes"
    state.currentUnit = state.currentUnit ? state.currentUnit : "fahrenheit"
    state.maxAutoHeatRestore = state.maxAutoHeatRestore ? state.maxAutoHeatRestore : 78
    state.minAutoHeatRestore = state.minAutoHeatRestore ? state.minAutoHeatRestore : 70
    state.minAutoCoolRestore = state.minAutoCoolRestore ? state.minAutoCoolRestore : 70
    state.maxAutoCoolRestore = state.maxAutoCoolRestore ? state.maxAutoCoolRestore : 78
    state.disabledSensors = state.disabledSensors ? state.disabledSensors : []
    state.inside = state.inside ? state.inside : 72
    state.dev_mode_time = state.dev_mode_time ? state.dev_mode_time : now()
    state.pausedByApp = state.pausedByApp ? state.pausedByApp : false
    state.lastPoll = state.lastPoll ? state.lastPoll : now()
    state.lastSetPoint = state.lastSetPoint ? state.lastSetPoint : 72
    state.wasTooHot = state.wasTooHot ? state.wasTooHot : false
    state.force_wait = state.force_wait ? state.force_wait : false
    state.lastTimeCool = state.lastTimeCool ? state.lastTimeCool : now()
    state.lastTimeHeat = state.lastTimeHeat ? state.lastTimeHeat : now()
    state.waitAfterCoolConditionMet = state.waitAfterCoolConditionMet ? state.waitAfterCoolConditionMet : false
    state.waitAfterHeatConditionMet = state.waitAfterHeatConditionMet ? state.waitAfterHeatConditionMet : false
    state.keepOffAtAllTimesRun = state.keepOffAtAllTimesRun ? state.keepOffAtAllTimesRun : now()
    state.resentOccurences = state.resentOccurences ? state.resentOccurences : 0
    state.otherThermWasTurnedOff = state.otherThermWasTurnedOff ? state.otherThermWasTurnedOff : false
    state.reCheckStateCount = state.reCheckStateCount ? state.reCheckStateCount : 100
    state.need1 = state.need1 ? state.need1 : "off"
    state.userWantsWarmer = state.userWantsWarmer ? state.userWantsWarmer : false
    state.userWantsCooler = state.userWantsCooler ? state.userWantsCooler : false
}