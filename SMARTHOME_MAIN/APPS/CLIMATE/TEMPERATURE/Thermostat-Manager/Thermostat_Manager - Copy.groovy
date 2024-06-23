import java.text.SimpleDateFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static var1 = "test" // works. ToDo: swap as many atomicState with static; but mostly constant ones (in which case I should then mark them as "final")

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

    pageNameUpdate()   

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

            //label title: "Assign a name",description:"$atomicState.appLabel", required: false, submitOnChange:true // can't use this because it shows html font tags
            input "appLabel", "text", title: "Assign a name to this instance of $app.name", submitOnChange: true
            app.updateLabel(appLabel)
            input "celsius", "bool", title: "Celsius (untested! Please send feedback)", submitOnChange: true
            paragraph "It's nearly impossible to test the efficacy of this feature in an environment where all devices work in Fahrenheit. Please, if you get to test it, send feedback through Hubitat Community threads"
            if (celsius) {
                convert_db_to_celsius()
            }
            else {
                if (atomicState.currentUnit == "celsius") convert_db_to_fahrenheit() // will run only if db was already converted to celsius, F being default. 
            }
        }
        section()
        {
            input "pause", "button", title: "$atomicState.button_name"
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
            input "polldevices", "bool", title: "Poll devices"

            input "enabledebug", "bool", title: "Debug logs", submitOnChange: true
            input "enabletrace", "bool", title: "Trace logs", submitOnChange: true
            input "enablewarning", "bool", title: "Warning logs", submitOnChange: true
            input "enableinfo", "bool", title: "Info logs", submitOnChange: true


            atomicState.EnableDebugTime = atomicState.EnableDebugTime == null ? now : atomicState.EnableDebugTime
            atomicState.enableDescriptionTime = atomicState.enableDescriptionTime == null ? now : atomicState.enableDescriptionTime
            atomicState.EnableWarningTime = atomicState.EnableWarningTime == null ? now : atomicState.EnableWarningTime
            atomicState.EnableTraceTime = atomicState.EnableTraceTime == null ? now : atomicState.EnableTraceTime


            if (enabledebug) atomicState.EnableDebugTime = now
            if (enabletrace) atomicState.EnableTraceTime = now
            if (enablewarning) atomicState.EnableWarningTime = now
            if (enableinfo) atomicState.enableDescriptionTime = now

            atomicState.lastCheckTimer = now // ensure it won't run check_logs_timer right away to give time for states to update
        }
    }
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
                input "ignoreMode", "bool", title: "Optional: Do not send any cool/heat command to the main thermostat (only set points commands will be sent)", defaultValue: false, submitOnChange: true
            }
            else if (ignoreMode) {
                // not compatible, disable if enabled
                app.updateSetting("ignoreMode", [type: "bool", value: "false"])
            }
            if (ignoreTarget) {
                app.updateSetting("offrequiredbyuser", [type: "bool", value: true]) // needs to be true when ignoreTarget is enabled  
                app.updateSetting("ignoreMode", [type: "bool", value: false])// can't have both

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
            if (ignoreMode) {
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
                atomicState.pageRefresh = atomicState.pageRefresh != null ? atomicState.pageRefresh : 0
                atomicState.pageRefresh += 1
                if (atomicState.pageRefresh > 2 && offrequiredbyuser) {
                    paragraph format_text("Thermostat off when target temp is reached is mandatory with 'do not send target temperatures', which you have enabled in the sections above", "white", "red")
                    atomicState.pageRefresh = 0
                }
                atomicState.fanCirculateAlways = atomicState.fanCirculateAlways != null ? atomicState.fanCirculateAlways : false
                if (atomicState.fanCirculateAlways == true && (offrequiredbyuser || ignoreTarget)) {
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
                atomicState.confirmed = atomicState.confirmed == null ? true : atomicState.confirmed
                paragraph format_text("auto method: the app sets your target temperature based on several learning functions by taking humidity levels and outside temperature into consideration", "black", "white")
                input "RESET", "button", title: "RESET", submitOnChange: true
                if (atomicState.confirmed == "no") { 
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
                atomicState.currentUnit = atomicState.currentUnit ? atomicState.currentUnit : false
                if (maxAutoHeat != "null" && minAutoHeat != "null" && minAutoCool != "null" && maxAutoCool != "null") {   
                    input "convertToCelsius", "bool", title: "Convert all these values to Celsius (if you forgot to select this option on the main page)", submitOnChange: true
                    if (convertToCelsius && !atomicState.currentUnit) {
                        atomicState.currentUnit = true
                        atomicState.maxAutoHeatRestore = maxAutoHeat // backup to prevent loop down conversions
                        atomicState.minAutoHeatRestore = minAutoHeat
                        atomicState.minAutoCoolRestore = minAutoCool
                        atomicState.maxAutoCoolRestore = maxAutoCool

                        app.updateSetting("celsius", [type: "bool", value: true])

                        app.updateSetting("maxAutoHeat", [type: "number", value: get_celsius(maxAutoHeat.toInteger())])
                        app.updateSetting("minAutoHeat", [type: "number", value: get_celsius(minAutoHeat.toInteger())])
                        app.updateSetting("minAutoCool", [type: "number", value: get_celsius(minAutoCool.toInteger())])
                        app.updateSetting("maxAutoCool", [type: "number", value: get_celsius(maxAutoCool.toInteger())])

                        convert_db_to_celsius()
                    }
                    else if (!convertToCelsius && atomicState.currentUnit) {
                        atomicState.currentUnit = false
                        if (enableinfo) log.info """restoring values: 

                        atomicState.maxAutoHeatRestore = $atomicState.maxAutoHeatRestore
                        atomicState.minAutoHeatRestore = $atomicState.minAutoHeatRestore
                        atomicState.minAutoCoolRestore = $atomicState.minAutoCoolRestore
                        atomicState.maxAutoCoolRestore = $atomicState.maxAutoCoolRestore
                        """
                        app.updateSetting("maxAutoHeat", [type: "number", value: atomicState.maxAutoHeatRestore])
                        app.updateSetting("minAutoHeat", [type: "number", value: atomicState.minAutoHeatRestore])
                        app.updateSetting("minAutoCool", [type: "number", value: atomicState.minAutoCoolRestore])
                        app.updateSetting("maxAutoCool", [type: "number", value: atomicState.maxAutoCoolRestore])
                    }
                    else if (convertToCelsius && atomicState.currentUnit) {
                        if (enableinfo) log.info "already converted, doing nothing"
                    }

                    if (enableinfo) log.info "atomicState.currentUnit = $atomicState.currentUnit"
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
                            for (window in windows) {
                                list += window.toString()
                            }

                            list = list.sort()
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
                    atomicState.fanCirculateAlways = true // needed for the UI to notify user of incompatibility with "offrequiredbyuser"
                    input "alwaysButNotWhenPowerSaving", "bool", title: "Fan circulation must stop when there is no motion (if motion sensitivity has been enabled) and/or in away mode", defaultValue: false
                }
                else {
                    atomicState.fanCirculateAlways = false
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
                            if (enablewarning) if (enablewarning) log.warn """
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
def pageNameUpdate(){
    closeBoolQuestions()

    def failedSensorsList = atomicState.disabledSensors ? atomicState.disabledSensors.join(", ") : "None"
    def pauseVar = atomicState.disabledSensors && !atomicState.disabledSensors.isEmpty() ? "FAILED SENSORS: ${failedSensorsList}" : "paused"

    // def pauseVar = atomicState.failedSensors ? "FAILED SENSORS" : "paused"
    def batteryVar = "LOW BATTERY"
    def previousLabel = app.label // save current label

    if (atomicState.paused || atomicState.lowBattery || atomicState.lowBatterySensor) {
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        while (app.label.contains(" $batteryVar ")) {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }

        if (enableinfo) log.info "original label = $app.label"

        if (atomicState.paused) {
            app.updateLabel(previousLabel + ("<font color = 'red'> $pauseVar </font>")) // recreate label
        }
        else if (atomicState.lowBattery || atomicState.lowBatterySensor) {
            app.updateLabel(previousLabel + ("<font color = 'red'> $batteryVar </font>")) // recreate label
        }

        atomicState.button_name = atomicState.paused ? "resume" : "pause"
        if (enableinfo) log.info "button name is: $atomicState.button_name new app label: ${app.label}"
    }
    else {
        atomicState.button_name = "pause"
        if (enabledebug) log.debug "button name is: $atomicState.button_name"
    }
    if (app.label.contains(pauseVar) && !atomicState.paused) {
        app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>"))
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        if (enableinfo) log.info "new app label: ${app.label}"
    }
    if (app.label.contains(batteryVar) && (!atomicState.lowBattery && !atomicState.lowBatterySensor)) {
        app.updateLabel(app.label.minus("<font color = 'red'> $batteryVar </font>"))
        while (app.label.contains(" $batteryVar ")) {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }
        if (enableinfo) log.info "new app label: ${app.label}"
    }

    // when page is loaded and app is paused make sure page loading doesn't add "paused" several times
    if (app.label.contains(pauseVar) && atomicState.paused) {
        //app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>" ))
        while (app.label.contains(" $pauseVar ")) {
            app.updateLabel(app.label.minus(" $pauseVar "))
            app.updateLabel(app.label.minus("<font color = 'red'>"))
            app.updateLabel(app.label.minus("</font>"))
        }
        app.updateLabel(app.label + ("<font color = 'red'> $pauseVar </font>")) // recreate label
    }
    if (app.label.contains(batteryVar) && (atomicState.lowBattery || atomicState.lowBatterySensor)) {
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

    if (atomicState.lastCheckTimer == null || (now - atomicState.lastCheckTimer) >= 60000) {

        atomicState.lastCheckTimer = now

        long days = 30L
        def hours = 10
        def minutes = 30
        //the int data type is a 32-bit signed integer, which has a maximum value of 2,147,483,647. 
        // 30 * 24 * 60 * 60 * 1000, exceeds this maximum value, resulting in an integer overflow and thus a negative number.
    
        long longTerm = days * 24L * 60L * 60L * 1000L 
        long mediumTerm = hours * 60 * 60 * 1000
        long shortTerm = minutes * 60 * 1000
    
        boolean endDebug = (now - atomicState.EnableDebugTime) >= shortTerm
        boolean endDescription = (now - atomicState.enableDescriptionTime) >= longTerm
        boolean endWarning = (now - atomicState.EnableWarningTime) >= mediumTerm
        boolean endTrace = (now - atomicState.EnableTraceTime) >= mediumTerm

        if (enabledebug) {
            def message = [
                "<br>end debug  ? $endDebug",
                "<br>end descr  ? $endDescription",
                "<br>end warn   ? $endWarning",
                "<br>end trace  ? $endTrace",
                "<br>now - atomicState.EnableDebugTime: ${now - atomicState.EnableDebugTime}",
                "<br>now - atomicState.enableDescriptionTime: ${now - atomicState.enableDescriptionTime}",
                "<br>now - atomicState.EnableWarningTime: ${now - atomicState.EnableWarningTime}",
                "<br>now - atomicState.EnableTraceTime: ${now - atomicState.EnableTraceTime}",
                "<br>longTerm: $longTerm",
                "<br>mediumTerm: $mediumTerm",
                "<br>shortTerm: $shortTerm",
            ]
            log.debug message.join()
        }


        if (endDebug && enabledebug) disable_logging()
        if (endDescription && enableinfo) disable_description()
        if (endWarning && enablewarning) disablewarnings()
        if (endTrace && enabletrace) disable_trace()
    }
    else {
        if (enabletrace) log.trace "log timer already checked in the last 60 seconds"
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
    atomicState.EnableDebugTime = now()
    atomicState.enableDescriptionTime = now()
    atomicState.EnableWarningTime = now()
    atomicState.EnableTraceTime = now()

    updateAllHeatSourcesBooleans()

    atomicState.pageRefresh = 0
    atomicState.lowBattery = false
    atomicState.lowBatterySensor = false
    atomicState.paused = false
    atomicState.restricted = false
    atomicState.lastNeed = "cool"
    atomicState.antifreeze = false
    atomicState.buttonPushed = false
    atomicState.setpointSentByApp = false
    atomicState.openByApp = true
    atomicState.closedByApp = true
    atomicState.lastPlay = atomicState.lastPlay != null ? atomicState.lastPlay : now()
    atomicState.overrideTime = now()
    atomicState.resendAttempt = now()
    atomicState.offAttempt = now()

    atomicState.lastMotionEvent = now()
    atomicState.lastNotification = now()
    atomicState.motionEvents = 0
    atomicState.lastTimeBsTrue = now()

    atomicState.userWantsWarmerTimeStamp = now()
    atomicState.userWantsCoolerTimeStamp = now()

    atomicState.setPointOverride = false

    // atomicState.neededThermostats = []

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
    atomicState.lastThermostatInput = atomicState.lastThermostatInput ? atomicState.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")

    subscribe(thermostat, "heatingSetpoint", setPointHandler)
    subscribe(thermostat, "coolingSetpoint", setPointHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)

    if (enableinfo) log.info "subscribed ${thermostat}'s coolingSetpoint to setPointHandler"
    if (enableinfo) log.info "subscribed ${thermostat}'s heatingSetpoint to setPointHandler"
    if (enableinfo) log.info "subscribed ${thermostat}'s thermostatMode to thermostatModeHandler"

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
    if (simpleModeContact) {
        subscribe(simpleModeContact, "contact", simpleModeContactHandler)
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
        schedule("0 0/5 * * * ?", Poll)
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


    schedule("0 0/1 * * * ?", mainloop, [data: ["source": "schedule"]])

    resetBusy()

    if (enableinfo) log.info "END OF INITIALIZATION"

}

/* ############################### EVT HANDLERS* ################################# */
def modeChangeHandler(evt){
    if (enableinfo) log.info "$evt.name is now $evt.value"

    atomicState.dontChekcThermostatStateCount = 0


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
        if (enabletrace) log.trace "location not in restricted mode, resuming normal operations"

        if (location.mode in windowsModes) {
            if (resetWindowsOverrideWithLocationModeChange) {
                if (!simpleModeIsActive()) {
                    if (enableinfo) log.info format_text("WINDOWS OVERRIDE RESET", "white", "darkblue")
                    atomicState.openByApp = true;
                    atomicState.closedByApp = true;
                }
            }
        }
        else if (closeWhenOutsideWindowsModes) {
            windows?.off()
            atomicState.otherWindowsOpenByApp = false
        }
    }

    //mainloop()
}
def appButtonHandler(btn) {

    switch (btn) {
        case "pause": atomicState.paused = !atomicState.paused
            if (enablewarning) if (enablewarning) log.warn "atomicState.paused = $atomicState.paused"

            if (atomicState.paused) {
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
            atomicState.paused = false
            updated()
            break
        case "run":
            if (enabledebug) log.debug "Running mainloop('btn')"
            resetBusy()
            pauseExecution(100)
            if (!atomicState.paused) mainloop("btn")
            break
        case "poll":
            Poll()
            poll_power_meters()
            break
        case "RESET":
            atomicState.confirmed = "no"
            break
        case "reset_confirmed":
            atomicState.confirmed = "yes"
            if (enabledebug) log.debug "**********************************RESETING DATABASE*****************************"
            reset_db()
            break
        case "no_reset":
            atomicState.confirmed = "na"
            break
    }
}
def contactHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "$evt.device is $evt.value"
        if (evt.value == "open") {
            atomicState.lastContactOpenEvt = now()
        }
    }
    mainloop("contactHandler")
}
def motionHandler(evt){
    long start = now()
    log.debug "motionHandler: $evt.device returns ${evt.value}F"

    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        atomicState.activeMotionCount = atomicState.activeMotionCount ? atomicState.activeMotionCount : 0
        if (evt.value == "active") {
            atomicState.activeMotionCount += 1 // eventsSince() can be messy 
            atomicState.lastMotionEvent = now() // initialized upon install or update
        }
        mainloop("motionHandler")
    }
    log.debug "motionHandler execution time: ${now() - start} ms"
}
def temperatureHandler(evt){
    long start = now()
    log.debug "temperatureHandler: $evt.device returns ${evt.value}F"


    if (!atomicState.paused) {
        if (location.mode in restricted) {
            log.info "location in restricted mode, doing nothing"
            def critical = criticalcold ? criticalcold : 65
            if (get_inside_temperature() < critical) {
                atomicState.override = false // cancel if it gets too cold
                atomicState.antifreeze = true
            }
            return
        }

        mainloop("temperatureHandler")
    }
    if (enabledebug) log.debug "temperatureHandler execution time: ${now() - start} ms"
}
def simpleModeContactHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "$evt.device is $evt.value"

        atomicState.lastBSeventStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        if ((now() - atomicState.lastBSevent) > 60000) // prevent false positives due to floating state of the $simpleModeName Mode trigger due to the mattress's weight (still working on this...)
        {
            atomicState.ButtonSupercedes = false // if there's a new contact event, this means it is working as expected, therefore no need for the button to supercede the sensor
        }

        // this boolean remains false until next button event
        atomicState.lastBSevent = now()
        mainloop("simpleModeContactHandler")
    }
}
def dimmerHandler(evt){

    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }

        if (enablewarning) log.warn "new dimmer level is $evt.value method = $method && setpointSentByApp = $atomicState.setpointSentByApp"

        // learning from user's input for the auto method
        learn(evt.value) // will also respond to thermostat inputs because it is ran before testing if it's set by the app or not

        if (atomicState.setpointSentByApp) {
            if (enabletrace) log.trace "dimmer value set by this app"
        }
        else {
            userWants(evt.value.toInteger(), get_inside_temperature())
        }



        atomicState.setpointSentByApp = false // always reset this variable after calling it

        //mainloop() // prevent feedback loops so both dimmer and thermostat set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def setPointHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enabletrace) log.trace "$evt.device $evt.name $evt.value"

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
                atomicState.setpointSentByApp = true
                resetSetByThisApp()                
                boolean okToOff = evt.value == "off" ? check_contacts_delay() : true
                if (okToOff) {

                    if (enableinfo) log.info "$thermostat $cmd $evt.value 7rgha"
                    // thermostat."${cmd}"(evt.value)
                    set_target(cmd, evt.value, inside = get_inside_temperature(), outside = get_outside_temperature, motionActive = Active(), doorsContactsAreOpen = doorsContactsAreOpen(), thermModes = get_thermostats_modes(), "5df4grlgk")
                }
                //
            }
            //return // must not set atomicState.setpointSentByApp back to false in this case
        }

        if (!atomicState.setpointSentByApp) {
            if (enableinfo) log.info "new $evt.name is $evt.value -------------------------------------"
            def inside = get_inside_temperature()

            userWants(evt.value.toInteger(), inside)

            def currDim = !dimmer ? atomicState.lastThermostatInput : get_dimmer_value()


            // this will be true only if thermostat is heating or cooling; therefore, dimmer won't be adjusted if off 
            // using atomicState.lastNeed == "heat" / "cool" seemed to allow exceptions... UPDATE but we need it. Let's keep an eye on this... 
            boolean correspondingMode = (evt.name == "heatingSetpoint" && atomicState.lastNeed == "heat") || (evt.name == "coolingSetpoint" && atomicState.lastNeed == "cool")

            def debug = [
                "atomicState.setpointSentByApp = $atomicState.setpointSentByApp",
                "Current $dimmer value is $currDim",
                "atomicState.lastThermostatInput = $atomicState.lastThermostatInput",
                "atomicState.lastNeed = $atomicState.lastNeed   ",
                "evt.value = $evt.value   ",
            ]

            if (enabledebug) log.debug format_text("black", "white", debug_from_list(debug))

            boolean simpleModeActive = simpleModeIsActive()
            def outside = get_outside_temperature() 

            def target = get_target(simpleModeActive, get_inside_temperature(), outside)
            atomicState.inside = atomicState.inside != null ? atomicState.inside : inside

            def thermModes = get_thermostats_modes()

            def needData = get_need(target, simpleModeActive, inside, outside, Active(), doorsOpen(), atomicState.neededThermostats, thermModes, "setPointHandler")
            def need = needData[1]
            def cmd = "set" + "${needData[0]}" + "ingSetpoint" // "Cool" or "Heat" with a capital letter

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

                    setDimmer(evt.value) // called only if it's not a value automatically set by the thermostat on the opposite operating mode (heatingSetpoint when cooling)

                    // every thermostat making sure to keep an offset between heating SP and cooling SP equal or superior to 2 degrees
                    //atomicState.lastThermostatInput = evt.value //////done by setDimmer()

                }
            }
            if (!correspondingMode) {
                if (enableinfo) log.info "not updating ${dimmer ? "dimmer" : "atomicState.lastThermostatInput"} because this is $evt.name and current mode is $thermModes"
            }
            if (currDim == evt.value) {
                // if(enableinfo) log.info "${dimmer ? "dimmer" : "atomicState.lastThermostatInput"} value ok (${dimmer ? '${get_dimmer_value()}' : "atomicState.lastThermostatInput"} = "${evt.value}")
                if (enableinfo) log.info "${dimmer ? 'dimmer' : 'atomicState.lastThermostatInput'} value ok (${dimmer ? get_dimmer_value() : 'atomicState.lastThermostatInput'} = '${evt.value}')"

            }
        }
        else {
            if (enabletrace) log.trace "event generated by this app, doing nothing"
        }

        //mainloop() // prevent feedback loops so both dimmer and thermosta set points can be modified. Changes will be made on next scheduled loop or motion events
        atomicState.lastSetPoint = evt.value
    }
    atomicState.setpointSentByApp = false // always reset this static/class variable after calling it
}
def specialDimmerHandler(evt){

    if (enabletrace) log.trace "$evt.device set to $evt.value | NEW $simpleModeName Mode target temperature"
    app.updateSetting("specialTemp", [type: "number", value: "$evt.value"])
    mainloop("specialDimmerHandler")

}
def pushableButtonHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "BUTTON EVT $evt.device $evt.name $evt.value"

        if (evt.name == "pushed") {
            if (!ignoreTarget && !simpleModeSimplyIgnoresMotion) {
                set_multiple_thermostats_mode("off", "5rgklgu", null)
            }

            atomicState.buttonPushed = !atomicState.buttonPushed

            atomicState.simpleModeOverrideResetDone = atomicState.buttonPushed ? false : true

            def warning = [
                "allowWindowsInSimpleMode = $allowWindowsInSimpleMode",
                "atomicState.simpleModeOverrideResetDone = $atomicState.simpleModeOverrideResetDone",
            ]

            if (enablewarning) log.warn debug_from_list(warning)


            if (allowWindowsInSimpleMode && atomicState.simpleModeOverrideResetDone == false) {
                if (enableinfo) log.info format_text("RESET WINDOWS OVERRIDE DUE TO ${simpleModeName} BEING ACTIVE", "white", "magenta")
                atomicState.openByApp = true;
                atomicState.closedByApp = true;
                atomicState.simpleModeOverrideResetDone = true;
            }

            atomicState.lastButtonEvent = atomicState.buttonPushed ? now() : atomicState.lastButtonEvent // time stamp when true only

            if (lightSignal && atomicState.buttonPushed) {
                flashTheLight()
            }

            mainloop("pushableButtonHandler")
            return
        }
        mainloop("pushableButtonHandler")
    }
    else {
        if (enablewarning) if (enablewarning) log.warn "App is paused, button event was ignored"
    }
}
def doubleTapableButtonHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "BUTTON EVT $evt.device $evt.name $evt.value"

        if (evt.name == "doubleTapped") {
            atomicState.paused = !atomicState.paused 
            def message = atomicState.paused ? "APP PAUSED BY DOUBLE TAP" : "APP RESUMED BY DOUBLE TAP"
            if (enablewarning) if (enablewarning) log.warn message
            if (buttonTimer && atomicState.paused) {
                if (enableinfo) log.info "App will resume in $buttonTimer minutes"
                runIn(buttonTimer, updated)
            }
        }
    }
}
def thermostatModeHandler(evt){

    atomicState.dontChekcThermostatStateCount = 0

    if (location.mode in restricted) {
        if (enableinfo) log.info "location in restricted mode, doing nothing"
        return
    }

    if (evt.value == "auto" && autoOverride) {
        if (enableinfo) log.info "OVERRIDE REQUEST DETECTED"
        atomicState.overrideTime = now()
        atomicState.override = true
        return
    }
    else {

        atomicState.override = false

    }

    if (!atomicState.restricted && !atomicState.paused) {
        if (enabledebug) log.debug """$evt.device $evt.name $evt.value
        sync ? $sync
thermostatB: $thermostatB

        """

    }
}
def outsideThresDimmerHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "*********** Outside threshold value is now: $evt.value ***********"
        //mainloop()
    }
}
def windowsHandler(evt){
    if (!atomicState.paused) {
        if (location.mode in restricted) {
            if (enableinfo) log.info "location in restricted mode, doing nothing"
            return
        }
        if (enableinfo) log.info "$evt.device is $evt.value"
        boolean doorsContactsAreOpen = doorsOpen()

        atomicState.closingCommand = atomicState.closingCommand != null ? atomicState.closingCommand : true

        if (evt.value == "open") {
            boolean openMore = !atomicState.widerOpeningDone && atomicState.insideTempHasIncreased

            if (!openMore) {
                atomicState.lastOpeningTime = now()
            }
            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        }
        else if (evt.value == "closed" && atomicState.closingCommand) {
            atomicState.closingCommand = false // reset this boolean to false
            atomicState.lastClosingTime = now() // we don't want this value to be reset at every device's wake up/refresh, hence 'atomicState.closingCommand' boolean"
            atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
        }
    }
}

/* ################################# MASTER LOOP ################################# */

def mainloop(source = "UNDEFINED"){

    atomicState.busy = atomicState.busy == null ? false : atomicState.busy


    // prevent mainloop() stacking
    if (atomicState.busy) {
        if (enablewarning) log.warn "$app.label is busy..."
        // runIn(80, resetBusy, [overwrite: false]) // far too ressource hungry
        if (atomicState.startMainLoop && now() - atomicState.startMainLoop > 30000) {
            log.error "Problem with main thread not terminating within 30 seconds! Resetting atomicState.busy to FALSE"
            atomicState.busy = false
        }
        return
    }

    atomicState.startMainLoop = now()

    if (atomicState.buttonPushed != null && UseSimpleMode) {
    def status = atomicState.buttonPushed ? "ACTIVE" : "INACTIVE"
        log.info format_text("$simpleModeName Mode $status", "white", "grey")
    }

    atomicState.busy = true
    boolean contactClosed = true
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
    boolean doNotUseMain = true
    boolean heatpumpConditionsTrue = true
    boolean dontSendThermostatModeCmd = true
    def currSP
    long s
    long blocktime = now()

    if (enablewarning) log.warn "MAINLOOP INITIALIZATION block"
    try {


        if (enabletrace) "mainloop called by ${source}" // if param is passed from schedule(), it's a map.

        atomicState.gotBusy = 0

        foolproof()

        atomicState.lastThermostatInput = atomicState.lastThermostatInput ? atomicState.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")

        /********************** VARIABLES' DATA COLLECTION *************************/

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking contactsAreOpen"
        try {
            contactClosed = !contactsAreOpen()
        } catch (Exception e) {
            contactClosed = false // default safety
            log.error "contactClosed => $e"
        }
        if (enabletrace) log.trace "Result of contactsAreOpen: $contactClosed execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking simpleModeIsActive"
        try {
            simpleModeActive = simpleModeIsActive()
        } catch (Exception e) {
            simpleModeActive = false // default safety
            log.error "simpleModeActive => $e"
        }
        if (enabletrace) log.trace "Result of simpleModeIsActive: $simpleModeActive execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking Active"
        try {
            motionActive = Active()
        } catch (Exception e) {
            motionActive = false // default safety
            log.error "motionActive => $e"
        }
        if (enabletrace) log.trace "Result of Active: $motionActive execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking doorsContactsAreOpen"
        try {
            doorsContactsAreOpen = doorsOpen()
        } catch (Exception e) {
            doorsContactsAreOpen = false // default safety
            log.error "doorsContactsAreOpen => $e"
        }
        if (enabletrace) log.trace "Result of doorsContactsAreOpen: $doorsContactsAreOpen execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting inside temperature from get_inside_temperature"
        try {
            inside = get_inside_temperature()
        } catch (Exception e) {
            log.error "get_inside_temperature => $e"
        }
        if (enabletrace) log.trace "Result of get_inside_temperature: $inside execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting outside temperature from outsideTemp"
        try {
            outside = outsideTemp.currentValue("temperature").toDouble()
        } catch (Exception e) {
            log.error "outsideTemp => $e"
        }
        if (enabletrace) log.trace "Result of outsideTemp: $outside execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting target from get_target"
        try {
            target = get_target(simpleModeActive, inside, outside)
        } catch (Exception e) {
            log.error "get_target => $e"
        }
        if (enabletrace) log.trace "Result of get_target: $target execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting needed thermostats from get_needed_thermosats"
        try {
            neededThermostats = get_needed_thermosats(need)
            if (enablewarning) if (enablewarning) log.warn "neededThermostats ----------------------> $neededThermostats"
        } catch (Exception e) {
            log.error "neededThermostats => $e"
        }
        if (enabletrace) log.trace "Result of get_needed_thermosats: $neededThermostats execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking thermostats' current modes"
        try {
            thermModes = get_thermostats_modes()
        } catch (Exception e) {
            log.error "thermModes => $e"
        }
        if (enabletrace) log.trace "Result of thermostat mode check: $thermModes execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Calculating need data from get_need"
        try {
            needData = get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, neededThermostats, thermModes, "mainloop")
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

            if (enablewarning) log.warn m.join()
        }
        if (enabletrace) log.trace "Result of get_need: $needData execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting need"
        try {
            need = needData[1]
        } catch (Exception e) {
            log.error "needData => $e"
            log.error "need: $need"
        }
        if (enabletrace) log.trace "need = $need execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Creating cmd string"
        try {
            cmd = "set" + "${needData[0]}" + "ingSetpoint" // "Cool" or "Heat" with a capital letter
        } catch (Exception e) {
            log.error "cmd => $e"
        }
        if (enabletrace) log.trace "Cmd string: $cmd execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking doNotUseMain condition"
        try {
            doNotUseMain = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes
        } catch (Exception e) {
            log.error "doNotUseMain => $e"
        }
        if (enabletrace) log.trace "Result of doNotUseMain condition: $doNotUseMain execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking heatpumpConditions"
        try {
            heatpumpConditionsTrue = heatpump && outside < lowtemp && !useAllHeatSources ? true : useAllHeatSources && outside < evenLowertemp ? true : false
            heatpumpConditionsTrue = doNotUseMain ? true : useAllHeatSourcesWithMode && location.mode in allHeatModes ? false : heatpumpConditionsTrue
        } catch (Exception e) {
            log.error "heatpumpConditionsTrue => $e"
        }
        if (enabletrace) log.trace "Result of heatpumpConditions check: $heatpumpConditionsTrue execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Checking dontSendThermostatModeCmd condition"
        try {
            dontSendThermostatModeCmd = (dontSetThermModesInSimpleMode && simpleModeActive) || ignoreMode
        } catch (Exception e) {
            log.error "dontSendThermostatModeCmd => $e"
        }
        if (enabletrace) log.trace "Result of dontSendThermostatModeCmd condition: $dontSendThermostatModeCmd execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        if (enabletrace) log.trace "Getting current setpoint"
        try {
            currSP = [thermostat?.currentValue("thermostatSetpoint").toInteger()]
        } catch (Exception e) {
            log.error "currSP => $e"
        }
        if (enabletrace) log.trace "Current setpoint: $currSP execution time: ${now() -s} ms"

        if (enabletrace) s = now()
        try {
            if (neededThermostats[0]) {
                if (enabletrace) log.trace "Adding needed thermostats' setpoints"
                if (enablewarning) if (enabletrace) log.warn "neededThermostats => $neededThermostats"
                currSP += neededThermostats?.collect{ it?.currentValue("thermostatSetpoint").toInteger() }
            }
        } catch (Exception e) {
            log.error "currSP += neededThermostats() => $e"
        }
        if (enabletrace) log.trace "Updated setpoints: $currSP execution time: ${now() -s} ms"

        /********************** UPDATE thermModes values *************************/
        if (differentiateThermostatsHeatCool) {
            thermModes = get_thermostats_modes()
        }

        /********************** AUTO OVERRIDE BUSY FALSE *************************/
        try {
            if (autoOverride(inside, need, target, cmd)) {
                atomicState.busy = false
                return
            }
        } catch (Exception e) {
            log.error "autoOverride() => $e"
        }


        /*********************** ANTI FREEZE SAFETY TEST *************************/
        try {
            if (antifreeze(inside, simpleModeActive)) {
                atomicState.busy = false
                return
            }
        } catch (Exception e) {
            log.error "antifreeze => $e"
        }


        /***************************** FAN CIRCULATE *****************************/
        try {
            fanCirculateManagement(need, target, inside, contactClosed, motionActive, thermModes)
        } catch (Exception e) {
            log.error "fanCirculateManagement() => $e"
        }
    }
    catch (Exception e) {
        log.error "'mainloop' failed!!! => $e APPLICATION IS NOT RUNNING!"
        atomicState.busy = false
        return
    }
    if (enablewarning) log.warn "INITIALIZATION took: ${(now() - blocktime)/1000} seconds"

    boolean skip = false
    // CONTACTS OPEN
    try {
        if (!contactClosed && need == "off") {
            log.debug "****************************NOT contactClosed**************************************"
            turn_off_thermostats(need, inside, thermModes) // manages user define delay
        }
        else if (motionActive) {
            set_multiple_thermostats_mode(need, "first send", null)
        }
    }
    catch (Exception e) {
        log.error "turn_off_thermostats with contacts open failed: $e"
    }

    if (enablewarning) log.warn"LOG TIMER CHECK"
    blocktime = now()
    try {
        check_logs_timer()
        log.warn "CHECK LOG TIMER OFF"
    } catch (Exception e) {
        log.error "check_logs_timer ==> $e"
    }
    if (enablewarning) log.warn "LOG TIMER CHECK took: ${(now() - blocktime)/1000} seconds"

    /********************** VERIFY HEATPUMP AND POWER USAGE CONDITIONS (HEATER OR COOLER)*************************/

    if (enablewarning) log.warn "powerManagement block"
    blocktime = now()
    try {
        if (enablewarning) if (enablewarning) log.warn "neededThermostats ================ $neededThermostats";

        currentOperatingNeed = need == "cool" ? "cooling" : need == "heat" ? "heating" : need == "off" ? "idle" : "ERROR"
        if (currentOperatingNeed == "ERROR") {
            log.error "currentOperatingNeed = $currentOperatingNeed"
            return false
        }

        opStateOk = operatingStateOk(contactClosed, doorsContactsAreOpen, currentOperatingState, currentOperatingNeed)

        powerManagement(inside, outside, need, target, cmd, contactClosed, doorsContactsAreOpen, motionActive, heatpumpConditionsTrue, dontSendThermostatModeCmd, currSP, neededThermostats, thermModes, opStateOk)

    } catch (Exception e) {
        log.error "powerManagement() => $e"
    }
    if (enablewarning) log.warn "powerManagement block took: ${(now() - blocktime)/1000} seconds"


    // VIRTUAL THERMOSTAT
    if (enablewarning) log.warn "VIRTUAL THERMOSTAT block"
    blocktime = now()
    try {
        virtualThermostat(need, target) // redundancy due to return statement above
    } catch (Exception e) {
        log.error "virtualThermostat => $e"
    }
    if (enablewarning) log.warn "VIRTUAL THERMOSTAT block took: ${(now() - blocktime)/1000} seconds"

    atomicState.busy = false
    log.debug "end of mainloop. Duration: ${(now() - atomicState.startMainLoop)/1000} seconds"
}

/* ############################### MAIN OPERATIONS ############################### */
def resetBusy(){
    if (atomicState.busy) {
        atomicState.busy = false
        log.debug "atomicState.busy reset to false"
    }
}
def foolproof(){
    if (offrequiredbyuser && fanCirculateAlways) // fool proof, these two options must never be true together, fancirculate takes precedence
    {
        app.updateSetting("offrequiredbyuser", [type: "bool", value: false])
    }
}
def autoOverride(inside, need, target, cmd){
    //atomicState.override = true // -> uncomment to test this function
    if (atomicState.override) {
        def overrideDur = overrideDuration != null ? overrideDuration : 0
        def timeLimit = overrideDur * 60 * 60 * 1000
        def timeStamp = atomicState.overrideTime

        if (overrideDur != 0 && overrideDur != null) {
            if ((now() - timeStamp) > timeLimit) {
                if (enablewarning) if (enablewarning) log.warn "END OF OVERRIDE"
                atomicState.override = false
                if (!fanCirculateAlways) {

                    if (enableinfo) log.info "thermostat off 54erg5"
                    turn_off_thermostats(need, inside, thermModes)
                    atomicState.offAttempt = now()

                }
                else {
                    if (enablewarning) if (enablewarning) log.warn "END OF AUTO OVERRIDE - setting last target"
                    cmd = "set${need != "off" ? "${ need$ }ingSetpoint" : "${ atomicState.lastNeed.capitalize() }ingSetpoint"}"
                    set_target(cmd, target, inside, outside = get_outside_temperature(), motionActive = Active(), doorsContactsAreOpen = doorsContactsAreOpen(), thermModes = get_thermostats_modes(), "END OF AUTO OVERRIDE")

                }
                return false
            }
            else {
                if (enablewarning) if (enablewarning) log.warn "OVERRIDE - AUTO MODE - remaining time: ${get_thermostat_that_must_remain_off(overrideDur, atomicState.overrideTime)}"
                def critical = criticalcold ? criticalcold : 65
                if (inside < critical) {
                    atomicState.override = false // cancel if it gets too cold
                    atomicState.antifreeze = true
                }
                return true
            }
        }
        else {
            if (enablewarning) if (enablewarning) log.warn "OVERRIDE - APP PAUSED DUE TO AUTO MODE (no time limit)"
            return true
        }
    }
}
def powerManagement(inside, outside, need, target, cmd, contactClosed, doorsContactsAreOpen, motionActive, heatpumpConditionsTrue, dontSendThermostatModeCmd, currSP, neededThermostats, thermModes, opStateOk){

    /**************** CONSISTENCY EVALUATIONS **************************/

    if (enabletrace) {
    def msg = [
            "<br>inside:$inside",
            "<br>outside:$outside",
            "<br>need:$need",
            "<br>target:$target",
            "<br>cmd:$cmd",
            "<br>contactClosed:$contactClosed",
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

    if (!atomicState.override) {        

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

        atomicState.reCheckStateCount = 100

        // HEAT PUMP OPTIMIZATION
        try {
            checkHeatPump(need, inside, thermModes)
        }
        catch (Exception e) {
            log.error "checkHeatPump => $e"
        }
        // POWER CONSISTENCY TEST
        try {
            powerConsistency(need, target, inside, cmd, contactClosed, doorsContactsAreOpen, motionActive, thermModes, opStateOk, currentOperatingNeed)
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
            thermTempDiscrepancy = manageThermDiscrepancy && sensor && thermostatTempProblem && contactClosed


            // check cooler performance and turn thermostat back on (override preferCooler bool) if needed 
            atomicState.coolerTurnedOnTimeStamp = atomicState.coolerTurnedOnTimeStamp != null ? atomicState.coolerTurnedOnTimeStamp : 31 * 60 * 1000
            efficiencyOffset = 2
            coolerNotEfficientEnough = efficiencyOverride && preferCooler && (now() - atomicState.coolerTurnedOnTimeStamp) > 30 * 60 * 1000 && inside >= target + efficiencyOffset
            boost = userBoostOffset && inside >= target + userBoostOffset

            if (enablewarning) if (coolerNotEfficientEnough && need == "cool") if (enablewarning) log.warn format_text("$cooler not efficient enough, turning on $thermostat", "red", "white")

            if (need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)) // NO OVERRIDE WHEN preferCooler and need == "cool"
            {
                if (enabledebug) log.debug "EFFICIENCY TEST"
                checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside)
            }
            else {
                if (enablewarning) log.warn format_text("contactClosed = $contactClosed", "white", "red")

                if (manageThermDiscrepancy && thermTempDiscrepancy && contactClosed) {
                    atomicState.setPointOverride = true // avoids modifying target values (in setDimmer) and prevents the app from running other normal operations
                    // since it is reset at setDimmer() (to allow for new user's inputs), it must be set to true each time here, or other normal functions will run

                    m = ""
                    delayBtwMessages = 5 * 60000
                    atomicState.lastSetpointMessage = atomicState.lastSetpointMessage == null ? atomicState.lastSetpointMessage : now()
                    timeBeforeNewtOverrideBigMessage = (delayBtwMessages - (now() - atomicState.lastSetpointMessage)) / 1000 / 60
                    timeBeforeNewtOverrideBigMessage = timeBeforeNewtOverrideBigMessage.toDouble().round(2)
                    if ((now() - atomicState.lastSetpointMessage) > delayBtwMessages) {
                        m = "SET POINT OVERRIDE - make sure your main thermostat is not too close to a window. If so, this app will attempt to keep your room at your target temperature ($target) by temporarily changing setpoints on your thermostat. This should not be affecting your input values (your target temperature)"
                        atomicState.lastSetpointMessage = now()
                    }
                    else {
                        timeUnit = timeBeforeNewtOverrideBigMessage < 1 ? "seconds" : timeBeforeNewtOverrideBigMessage >= 2 ? "minutes" : "minute"
                        timeDisplay = timeBeforeNewtOverrideBigMessage < 1 ? timeBeforeNewtOverrideBigMessage * 100 : timeBeforeNewtOverrideBigMessage
                        m = "SET POINT OVERRIDE (detailed description in ${timeDisplay} ${timeUnit})"
                    }

                    if (enablewarning) if (enablewarning) log.warn format_text(m, "red", "white")

                    temporarySetpoint = need == "cool" ? 62 : need == "heat" ? 85 : 72 // 72 if by any chance this went wrong

                    currCSP = thermostat.currentValue("coolingSetpoint").toInteger()
                    currHSP = thermostat.currentValue("heatingSetpoint").toInteger()
                    boolean notSet = need == "cool" && currCSP != temporarySetpoint.toInteger() || need == "heat" && currHSP != temporarySetpoint.toInteger()


                    if (notSet) {

                        if (enabletrace) log.trace "setting $thermostat to $need"

                        if (heatpumpConditionsTrue && need != "off") {
                            if (enableinfo) log.info "$thermostat stays off due to heatpump and cold temp outside"
                        }
                        else {
                            if (enablewarning) if (enablewarning) log.warn "$thermostat $cmd to temporarySetpoint $temporarySetpoint 478r6gh"
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)

                            set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, "temporarysetpoint")

                        }

                        if (need == "cool") // prevent thermostat firmware from circling down its setpoints
                        {
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setHeatingSetpoint(temporarySetpoint - 2)
                            if (enableinfo) log.info "$thermostat heatingsetpoint set to ${temporarySetpoint-2} to prevent circling down SP's"
                        }
                        else if (need == "heat") // prevent thermostat firmware from circling down its setpoints
                        {
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setCoolingSetpoint(temporarySetpoint + 2)
                            if (enableinfo) log.info "$thermostat coolingSetpoint set to ${temporarySetpoint+2} to prevent circling down SP's"
                        }

                        atomicState.lastSetTime = now()
                        return
                    }
                }
                else if (!thermTempDiscrepancy && atomicState.setPointOverride) {
                    atomicState.setPointOverride = false // if this line is read, then setpoint override is no longer needed
                    if (manageThermDiscrepancy && enabletrace) log.trace  format_text("END OF SET POINT OVERRIDE - BACK TO NORMAL OPERATION", "white", "grey")
                }

                if (enabledebug) log.debug "forceCommand ? $forceCommand atomicState.forceAttempts = $atomicState.forceAttempts | abs(inside-target) = ${Math.abs(inside-target).round(2)}"
            }
            /****************END OF CONSISTENCY OR EFFICIENCY TESTS AND EMERGENCY HEAT/COLD DUE TO A POSSIBLE BADLY LOCATED THERMOSTAT*************************/

            /****************NORMAL EVALUATION WITH POSSIBLE NEED FOR REDUNDENT FORCED COMMANDS (possibly needed due to bad Z-Wave mesh)******/
            atomicState.forceLimit = Math.abs(inside - target) > 5 ? 20 : 5 // higher amount of attempts if bigger discrepancy         
            atomicState.forceAttempts = atomicState.forceAttempts != null ? atomicState.forceAttempts : 0
            forceCommand = atomicState.forceAttempts < atomicState.forceLimit ? true : false
            forceCommand = need in ["cool", "heat"] && Math.abs(inside - target) > 3 ? true : false // 
            forceCommand = Math.abs(inside - target) >= 5 ? true : (forceCommand ? true : false) // counter ignored if forceCmd user decision is true and temp discrepancy too high: continue trying until temp is ok
            forceCommand = !opStateOk ? true : forceCommand // !opStateOk supercedes all other conditions
            forceCommand = contactClosed && !doorsContactsAreOpen ? forceCommand : false // don't use this method when contacts are open, even door contacts

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
            if (enablewarning) if (enablewarning) log.warn "dontSendThermostatModeCmd = $dontSendThermostatModeCmd"

            if (!atomicState.setPointOverride && (thermostatModeNotOk || forceCommand || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) && contactClosed) {
                atomicState.dontChekcThermostatStateCount = atomicState.dontChekcThermostatStateCount == null ? 1 : atomicState.dontChekcThermostatStateCount
                atomicState.dontChekcThermostatStateCount += 1
                if (forceCommand && opStateOk) { if (enabledebug) log.debug "FORCING CMD TO DEVICE BECAUSE temperature difference is TOO HIGH" }
                if (forceCommand && !opStateOk && !thermTempDiscrepancy) { if (enabledebug) log.debug "FORCING CMD TO DEVICE BECAUSE current operating state is INCONSISTENT" }

                atomicState.forceAttempts += 1
                if (atomicState.forceAttempts >= forceLimit) { runIn(1800, resetCmdForce) } // after 5 attempts, stop and retry in half an hour to prevent z-wave cmds overflow onto the device

                //atomicState.lastSetTime =  5 * 60 * 1000 + 1 // for TESTS ONLY
                if (enabledebug) {
                        def msg = [
                        "<br>preferCooler ? $preferCooler && outside < preferCoolerLimitTemperature =  $outside < $preferCoolerLimitTemperature",
                        "<br>need != 'off' || forceCommand || (need == 'off' && (sensor || offrequiredbyuser)) = ${need != 'off' || forceCommand || (need == 'off' && (sensor || offrequiredbyuser))}",
                        "<br>need == 'cool' && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature) = ${need == 'cool' && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)}",
                        "<br>thermModes = $thermModes",
                        "<br>need = $need",
                        "<br>thermostatModeNotOk = ${thermostatModeNotOk}",
                        "<br>atomicState.dontChekcThermostatStateCount: $atomicState.dontChekcThermostatStateCount"
                    ]
                    log.debug msg.join()
                }

                if (enablewarning || thermostatModeNotOk) {
                    if (enablewarning) log.warn "thermostatModeNotOk ===> $thermostatModeNotOk"
                    if (enablewarning) log.warn "overrideThermostatModeCheckBeforeSendingCmd ===> $overrideThermostatModeCheckBeforeSendingCmd"
                    if (enablewarning) log.warn "atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount ===> $atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount"
                }

                if ((forceCommand || need == "off") && (sensor || offrequiredbyuser || !contactClosed)) {



                    if ((!opStateOk || (now() - atomicState.lastSetTime) > 5 * 60 * 1000) || need == "off" || forceCommand) {
                        atomicState.coolerTurnedOnTimeStamp = atomicState.coolerTurnedOnTimeStamp != null ? atomicState.coolerTurnedOnTimeStamp : 31 * 60 * 1000

                        if (need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)) {
                            checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside)
                        }
                        else {
                            if ((thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount) || thermostat.currentValue("thermostatFanMode") != "auto") && need == "off" && offrequiredbyuser) {
                                atomicState.dontChekcThermostatStateCount += 1

                                if (enableinfo) log.info "Sending off command to thermostat 4felf65"
                                turn_off_thermostats(need, inside, thermModes)
                                set_multiple_thermostats_fan_mode("auto", "fan auto when thermostat off 4felf65")

                            }
                            // if fanCirculateAlways is true, offrequiredbyuser will automatically be set to false in mainloop and/or within dynamic settings pages
                            else if ((thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) && (!fanCirculateAlways || (need != "off" || !offrequiredbyuser))) {
                                atomicState.dontChekcThermostatStateCount += 1



                                if (heatpumpConditionsTrue && need != "off") {
                                    if (enableinfo) log.info "heatpumpConditionsTrue 5z4rg4"
                                }
                                else if (!thermostatModeNotOk || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) {
                                    atomicState.dontChekcThermostatStateCount += 1
                                    if (!dontSendThermostatModeCmd) {
                                        set_multiple_thermostats_mode(need, "checkthermstate force command", null)   // do not modify string "checkthermstate force command" it serves as a test at set_thermostat_mode()
                                    }
                                    else {
                                        if (enabletrace) log.trace "dontSendThermostatModeCmd true"
                                    }

                                }
                                else {
                                    if (enabletrace) log.trace "thermostat already set to $need etgh4ze5"
                                }

                            }
                            else if (!thermostatModeNotOk) {
                                if (enableinfo) log.info "$thermostat already set to $need 5z4gth"
                            }
                            else if (need == "off" && fanCirculateAlways) {
                                if (!motionActive && alwaysButNotWhenPowerSaving) {
                                    if (thermModes.any{ it -> it != "off" } || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount))
                                    {
                                        atomicState.dontChekcThermostatStateCount += 1
                                        set_multiple_thermostats_fan_mode("auto", "fanCirculateAlways = true")

                                        if (enableinfo) log.info "thermostat off 4n2r4zk"
                                        turn_off_thermostats(need, inside, thermModes)

                                    }
                                }
                                else {
                                    if (enableinfo) log.info "fancriculateAlways is true, so not turning off the thermostat. sending fanonly() instead"

                                    if (enableinfo) log.info "thermostat off 96th34z"
                                    turn_off_thermostats(need, inside, thermModes) // needed to prevent thermostat staying in "cool" or "heat" mod, thermModese
                                    if (enablewarning) if (enablewarning) log.warn "fan mode on"
                                    set_multiple_thermostats_fan_mode("on", "fanOn when thermostat off 96th34z")

                                }
                            }
                        }

                        atomicState.lastSetTime = now()

                        if (need in ["cool", "heat"]) {
                            atomicState.lastSetTime = now() // prevent switching from heat to cool too frequently 
                            //during conditions that might have been unacounted for, like during shoulder season
                        }

                        if (enabledebug) log.debug "THERMOSTAT SET TO $need mode (587gf)"
                    }
                    else if ((now() - atomicState.lastSetTime) < 30 * 60 * 1000) {
                        if (enabledebug) log.debug "THERMOSTAT CMD NOT SENT due to the fact that a cmd was already sent less than 30 minutes ago"
                    }

                    if (need == "off") {
                        atomicState.offAttempt = now()

                    }
                }
                else {
                    log.debug "THERMOSTAT stays in $thermModes mode"
                }

            }
            else if ((need != "off" || !offrequiredbyuser) && contactClosed) {
                if (enableinfo) log.info "$thermostat already set to $need 8ryjyuz"
            }
        } catch (Exception e) {
            log.error "forceCommand => $e"
        }

        try {
            if (!atomicState.setPointOverride) {
                fanMode = [thermostat.currentValue("thermostatFanMode")]

                if (enabledebug) log.debug "neededThermostats => > > > $neededThermostats"

                fanMode += neededThermostats.collect{ it -> it?.currentValue("thermostatFanMode") }

                fanModeNotON = fanMode.any{ it -> it != "on" }

                if (enabletrace) {
                    log.trace "currSpNotOk => $currSpNotOk"
                    log.trace "thermostatModeNotOk => $thermostatModeNotOk"
                    log.trace "thermTempDiscrepancy => $thermTempDiscrepancy"
                    log.trace "contactClosed => $contactClosed"
                }

                if ((currSpNotOk || thermostatModeNotOk) && !thermTempDiscrepancy && contactClosed) {
                    if (enablewarning) log.warn "need = $need last need = $atomicState.lastNeed **************** "
                    cmd = need == "off" ? "set" + atomicState.lastNeed.capitalize() + "ingSetpoint" : cmd // restore the last need if need = off
                    if (enablewarning) log.warn "cmd = $cmd ${atomicState.lastNeed.capitalize()+'ingSetpoint'}"
                    atomicState.setpointSentByApp = true
                    runIn(3, resetSetByThisApp)
                        boolean inpowerSavingMode = location.mode in powersavingmode


                    set_thermostat_target_ignore_setpoint(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, "d54fdhj")


                    if (currSpNotOk || thermostatModeNotOk) {

                        if (need == "off" && ignoreMode) {
                            if (enableinfo) log.info "need is $need but not sending cmd due to ignoreMode (only off, cool/heat still need to be sent in case it's off or in the wrong mode and before overriding with setpoint"
                            if (enabletrace) log.trace "fanCirculateAlways is $fanCirculateAlways"

                            if (fanCirculateAlways || (alwaysButNotWhenPowerSaving && !inpowerSavingMode && Active() && !contactsAreOpen())) {
                                if (fanModeNotON) {
                                    set_multiple_thermostats_fan_mode("on", "fanCirculateAlways true dkjf5")
                                    if (enableinfo) log.info "fan set to 'on'"
                                }
                                else {
                                    if (enableinfo) log.info "fan already on"
                                }

                            }
                            else if (!fanModeNotON && !fanCirculateAlways) {
                                if (enableinfo) log.info "thermostat fan mode set back to auto"
                                set_multiple_thermostats_fan_mode("auto", "thermostat fan mode set back to auto 5df4glg")
                            }
                        }
                        else if (!ignoreMode) {
                            log.debug "Setting thermostats to $need (5rrgh4klt5ui)"
                            set_multiple_thermostats_mode(need, "5rrgh4klt5ui", null)

                        }

                        if (currSpNotOk || (thermostatModeNotOk && need != "off")) {
                            // set target temp AFTER the mode for ignoreMode to work as intended when enabled    
                            set_thermostat_target_ignore_setpoint(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, "sljdfo")

                        }
                        else {
                            m = "THERMOSTAT ALREADY SET TO $target "
                            if (!fanModeNotON && need == "off") m = "THERMOSTAT IN FAN CIRCULATE OPERATION "
                            if (enableinfo) log.info m
                        }
                    }

                    // atomicState.resendAttempt = now() // needs to be updated here otherwise it'll resend immediately after 

                }
                else if ((need != "off" || !offrequiredbyuser) && !thermTempDiscrepancy) {
                    if (enableinfo) log.info "Thermostat already set to $target 47ry6ze"
                }
                else if (thermTempDiscrepancy) {
                    if (enablewarning) if (enablewarning) log.warn "Skipping normal setpoint and thermostatMode management due to thermTempDiscrepancy = $thermTempDiscrepancy"
                }
            }
            else {
                if (enablewarning) if (enablewarning) log.warn "Skipping normal set point management due to set point override"
            }
        } catch (Exception e) {
            log.error "fanMode => $e"
        }


    }
    else {
        if (enableinfo) log.info "OVERRIDE MODE--------------"
    }
}
def powerConsistency(need, target, inside, cmd, contactClosed, doorsContactsAreOpen, motionActive, thermModes, operatingStateOk, currentOperatingNeed){
    boolean pwLow
    boolean timeToRefreshMeters
    def timeElapsedSinceLastResend
    boolean timeIsUp
    boolean timeIsUpOff

    if (need == "off") {
        cmd = "setThermostatSetpoint"
    }

    try {
        if (pw) {
            if (enabletrace) log.trace "Power evaluation"
            atomicState.resendAttempt = atomicState.resendAttempt ? atomicState.resendAttempt : now()
            atomicState.offAttempt = atomicState.offAttempt ? atomicState.offAttempt : now()
            // here we manage possible failure for a thermostat to have received the z-wave/zigbee or http command
            timeElapsedSinceLastResend = now() - atomicState.resendAttempt
            atomicState.timeElapsedSinceLastOff = now() - atomicState.offAttempt // when device driver returns state off while in fact signal didn't go through
            atomicState.threshold = 3 * 60 * 1000 // give power meter 3 minutes to have its power measurement refreshed before attempting new request 
            timeIsUp = timeElapsedSinceLastResend > atomicState.threshold
            timeIsUpOff = atomicState.timeElapsedSinceLastOff > atomicState.threshold
            def pwVal = pw.currentValue("power")
            if (enabletrace) log.trace "${pw}'s current power consumption is $pwVal"

            try {
                pwLow = pwVal < 100 // below 100 watts we assume there's no AC compression nor resistor heat currently at work
                timeToRefreshMeters = need == "off" ? atomicState.timeElapsedSinceLastOff > 10000 && !pwLow : timeElapsedSinceLastResend > 10000 && pwLow
            }
            catch (Exception e) {
                log.error "boolean pwlow => $e"
            }

            // timeToRefreshMeters = true // for testing
            if (timeToRefreshMeters /*&& !timeIsUp && !timeIsUpOff && !ignoreMode*/) // make sure to attempt a refresh before sending more commands
            {
                if (enableinfo) log.info "pwLow = $pwLow refreshing $pw because power is $pwVal while it should be ${need == "off" ? "below 100 Watts":"above 100 Watts"}"
                try {
                    poll_power_meters()
                }
                catch (Exception e) {
                    log.error "poll_power_meters() => $e"
                }
            }

            if (enablewarning) {
            def m = [
                    "<br> ignoreMode: $ignoreMode",
                    "<br> timeIsUp: $timeIsUp",
                    "<br> pwLow: $pwLow",
                    "<br> need: $need",
                    "<br> offrequiredbyuser: $offrequiredbyuser",
                    "<br> timeIsUpOff: $timeIsUpOff",
                    "<br> doorsOpen: $doorsOpen()",
                    "<br> fanCirculateAlways: $fanCirculateAlways",

                ]

                if (enablewarning) log.warn m.join()
            }

            // timeIsUp = true
            if (need != "off" && timeIsUp && pwLow) {

                def message = "<div style='color:white;background:red;'> resending commands to thermostat due to inconsistency between power measurement (${pwVal}Watts) and current need ($need)</div>"

                if (!ignoreMode) {
                    message = "<div style='color:white;background:red;'> resending setThermostatMode(${need}) due to inconsistency between power measurement (${pwVal}Watts) and current need ($need)</div>"
                    set_multiple_thermostats_mode(need, "checkthermstate force command", null)
                }
                if (!ignoreTarget) {
                    message = "<div style='color:white;background:red;'> resending ${cmd}(${target}) due to inconsistency between power measurement (${pwVal}Watts) and current need ($need)</div>"
                    set_target(cmd, target, inside, get_outside_temperature(), motionActive, doorsContactsAreOpen, thermModes, "checkthermstate force command")
                }
                atomicState.resendAttempt = now()
                atomicState.setpointSentByApp = true
                runIn(3, resetSetByThisApp)



                try {
                    if (!operatingStateOk) {

                        if (enablewarning) log.warn "EMERGENCY COMMAND"
                            def faulty_devices = neededThermostats.findAll{ it -> it.currentValue("thermostatOperatingState") != currentOperatingNeed }
                        if (enablewarning) log.warn "faulty_device: $faulty_devices need: $need"

                        try {
                            for (device in faulty_devices) {
                                try {
                                    device.refresh()
                                    pauseExecution(1000)
                                }
                                catch (Exception) {
                                    try {
                                        device.poll()
                                        pauseExecution(1000)
                                    }
                                    catch (Exception e) {
                                        log.error "$device can't be refreshed nor polled, for some reason: ${e}"
                                    }
                                }
                                if (enablewarning) log.warn("TRYING TO SET $device to $need")
                                if (device.currentValue("thermostatOperatingState") != "fanCirculate") {
                                    set_thermostat_mode(device, need, "ermergency resend")
                                }
                                else {
                                    log.debug "$device in fanCriclateMode, not re-sending $need command"
                                }
                            }
                            // set_multiple_thermostats_mode(need, "emergency_restore", null)
                        } catch (Exception error) {
                            log.error "Failed to loop through emergency commands for faulty devices: $error"
                        }
                    }
                }
                catch (Exception err) {
                    log.error "Failed to send emergency command for faulty devices: $err"
                }

                //poll_power_meters()
            }
            else if (timeIsUpOff && need == "off" && !pwLow && !doorsOpen()) {

                if (!fanCirculateAlways) {
                    if (!atomicState.userWantsWarmer && !atomicState.userWantsCooler) {
                        if (enablewarning) if (enablewarning) log.warn "$thermostat should be off but still draining power, resending cmd"
                        if (enableinfo) log.info "thermostat off 34t5zl"
                        turn_off_thermostats(need, inside, thermModes)
                        atomicState.offAttempt = now()
                    }
                }
                else if (need == "off" && fanCirculateAlways) {
                    boolean thermModeNotOff = thermModes.any{ it -> it.currentValue("thermostatMode") != "off" }
                    if (!motionActive && alwaysButNotWhenPowerSaving) {
                        if (thermModeNotOff || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) {
                            atomicState.dontChekcThermostatStateCount += 1

                            if (enableinfo) log.info "thermostat off fan auto 639t4js"
                            turn_off_thermostats(need, inside, thermModes)
                            set_multiple_thermostats_fan_mode("on", "fan on instead of off 2")

                        }
                    }
                    else {
                        if (enableinfo) log.info "fancriculateAlways is true, so not turning off the thermostat despite power discrepancy. resending fanonly() instead"

                        turn_off_thermostats(need, inside, thermModes) // needed to prevent thermostat staying in "cool" or "heat" mod, thermModese
                        if (enableinfo) log.info "thermostat fan on 47tyuz"
                        set_multiple_thermostats_fan_mode("on", "fanOn when thermostat off 47tyuz")

                    }
                }
            }
            else if ((!pwLow && need in ["heat", "cool"]) || (need == "off" && pwLow)) {
                if (enabledebug) log.debug "EVERYTHING OK"
            }
            else {
                if (enabledebug) log.debug "Auto Fix Should Kick in within time threshold"
            }
        }
    }
    catch (Exception e) {
        log.error "pw tests ==> $e"
    }
}
def fanCirculateManagement(need, target, inside, contactClosed, motionActive, thermModes){
    /****************FAN CIRCULATION MANAGEMENT*************************/

    if (need in ["cool", "heat"]) {
        if (enabledebug) log.debug "not changing fan mode because current need is ${need}"
    }
    else {
        if (thermostat.currentValue("thermostatFanMode") == "on" && contactClosed && !fancirculate && atomicState.fanOn && !fanCirculateAlways) {

            if (enableinfo) log.info "Setting fan back to auto"
            if (thermostat.currentValue("thermostatFanMode") != "auto") set_multiple_thermostats_fan_mode("auto", "Setting fan back to auto")
            atomicState.fanOn = false
        }
        if (fanCirculateAlways) {
            boolean inFanCirculateMode = fanCirculateModes ? location.mode in fanCirculateModes : false
            def fanMode = thermostat.currentValue("thermostatFanMode")
            if (enabletrace) log.trace  "fanCirculateAlways => fanMode = $fanMode inFanCirculateMode (location mode) = $inFanCirculateMode"
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
                        if (thermModeNotOff || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) {
                            atomicState.dontChekcThermostatStateCount += 1


                            if (enableinfo) log.info "thermostat off 78egj"
                            turn_off_thermostats(need, inside, thermModes)
                            set_multiple_thermostats_fan_mode("auto", "fan auto when thermostat off 78egj")

                        }
                    }
                    else {

                        if (enableinfo) log.info "thermostat off 54gt6z34"
                        turn_off_thermostats(need, inside, thermModes)


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
                    if (enabletrace) log.trace "fan already on"
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

        boolean keepOnWindows = keepFanOnWhenWindowsOpen && !contactClosed
        boolean keepFandDimmerCoolerOutside = keepFanDimmerOnIfOutsideLowerThanInside && outside < inside && inside > 74

        def dimmerValue = need in ["cool", "heat"] || neverTurnOffFanDimmer || (keepOnWindows && atomicState.lastNeed != "heat") ? inside >= target + 4 || outside >= 85 ? maxFanSpeed : inside >= target + 2 ? mediumFanSpeed : lowFanSpeed : 0
        dimmerValue = keepFandDimmerCoolerOutside ? maxFanSpeed : dimmerValue
        dimmerValue = location.mode in silenceMode ? silenceValue : dimmerValue
        if (fanDimmer.currentValue("level") != dimmerValue) fanDimmer?.setLevel(dimmerValue)
        if (enableinfo) log.info "$fanDimmer running at ${fanDimmer.currentValue("level")}%"
    }
    /****************END OF FAN SWITCH MANAGEMENT*************************/
}
def checkHeatPump(need, inside, thermModes){
    boolean tooMuchPower = false
    if (controlPowerConsumption && atomicState.lastNeed == "heat" && pw && heater) // heater only
    {
        currentPower = pw?.currentValue("power").toInteger() + heater?.currentValue("power").toInteger()
        tooMuchPower = currentPower > maxPowerConsumption.toInteger()
        if (tooMuchPower) {
            if (enablewarning) if (enablewarning) log.warn format_text("power consumption ${heater?.currentValue("power")!=0 ? "$heater + ":""} $thermostat = $currentPower Watts", "white", "red")
        }
        else {
            if (enableinfo) log.info format_text("power consumption ${heater?.currentValue("power")!=0 ? "$heater + ":""} $thermostat = $currentPower Watts", "white", "lightgreen")
        }
        //tooMuchPower = devicePriority == "$thermostat" && heatpumpConditionsTrue ? true : tooMuchPower
        // redundant: if device priority is thermostat and heatpump conditions are true, then the thermostat will be shut down
        // what we need is to make sure that the alternate heater (mandatory if heatpump true) will kick in, which is tested by virtualThermostat method
    }
    if (coolerControlPowerConsumption && atomicState.lastNeed == "cool" && pw && cooler && !preferCooler) // cooler only and if not already prefered
    {
        currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
        tooMuchPower = currentPower > coolerMaxPowerConsumption.toInteger()
        if (tooMuchPower) {
            if (enablewarning) if (enablewarning) log.warn format_text("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler + ":""} $thermostat = $currentPower Watts", "white", "red")
        }
        else {
            if (enableinfo) log.info format_text("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler + ":""} $thermostat = $currentPower Watts", "white", "lightgreen")
        }
    }

    //if heatpump and conditions for heatpump are met (too cold outside) OR too much power and priority is not thermostat 
    //Then keep the thermostat in off mode. 

    if ((heatpumpConditionsTrue) || (tooMuchPower && devicePriority != "$thermostat")) {
        if ((thermostat.currentValue("thermostatMode") != "off" || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) && !fanCirculateAlways) {
            atomicState.dontChekcThermostatStateCount += 1

            if (enableinfo) log.info "thermostat off 735h4ze6 heatpumpConditionsTrue = $heatpumpConditionsTrue"
            turn_off_thermostats(need, inside, thermModes) // so as to take precedence over any other condition, thermModes 

            atomicState.offAttempt = now()
            if (enableinfo) log.info "$thermostat turned off due ${preferCooler && atomicState.lastNeed == "cool" ? "to preferCooler option" : "to heatpump or power usage conditions"}"

        }
    }
}
def antifreeze(inside, simpleModeActive){
    if (powersavingmode && location.mode in powersavingmode) {
        // do nothing 
        if (enableinfo) log.info "location is in power saving mode, antifreeze test is being ignored"
        atomicState.antifreeze = false
    }
    else if (simpleModeActive) {
        if (enabledebug) log.debug "Antifreeze not running due to simpleModeActive = $simpleModeActive"
    }
    else {
        if (atomicState.antifreeze) {
            if (enablewarning) if (enablewarning) log.warn "ANTI FREEZE HAS BEEN TRIGGERED"
        }
        // antifreeze precaution (runs after calling atomicState.antifreeze on purpose here)
        def backupSensorTemp = backupSensor ? backupSensor.currentValue("temperature") : inside

        if (antifreeze && !atomicState.setPointOverride) {
            def antiFreezeThreshold_V = antiFreezeThreshold != null ? antiFreezeThreshold : criticalcold != null ? criticalcold : celsius ? get_celsius(65) : 65

            if (inside <= antiFreezeThreshold_V || backupSensorTemp <= antiFreezeThreshold_V) {

                atomicState.antifreeze = true
                atomicState.setpointSentByApp = true // make sure not to learn desired setpoint from this value
                runIn(3, resetSetByThisApp)

                if (enablewarning) log.warn "$thermostat setpoint set to $safeValue as ANTI FREEZE VALUE | inside = $inside | antiFreezeThreshold = $antiFreezeThreshold_V | safeValue = $safeValue"

                set_multiple_thermostats_mode("heat", "antifreeze_user", safeValue)

                atomicState.resendAttempt = now()
                windows?.off() // make sure all windows linked to this instance are closed
                if (enablewarning) if (heater) log.warn "turning on heater: $heater"
                heater?.on()// turn on the alternate heater, if any
                set_thermostat_mode(altThermostat, "heat", "antiFreeze")

                set_target("setHeatingSetpoint", safeValue, inside, outside = get_outside_temperature(), motionActive = true, doorsContactsAreOpen = false, thermModes = get_thermostats_modes(), "antiFreeze")

                // if there's an alt thermosat, it's not accounted for in set_target(). 
                // so we run the command here directly. 
                altThermostat?.setHeatingSetpoint(safeValue)

                sendNotification()

                return true // don't run any other operation until temp is back to desired value
            }
            else if (atomicState.antifreeze) {
                atomicState.antifreeze = false
                if (enabletrace) log.trace "END OF ANTI FREEZE"
            }
        }
        else if (!atomicState.setPointOverride)// default & built-in app's anti freeze
        {
            def defaultSafeTemp = criticalcold == null ? 58 : criticalcold <= 58 ? criticalcold : 58
            if (inside <= defaultSafeTemp || backupSensorTemp <= defaultSafeTemp) {
                if (enablewarning) log.warn "ANTIFREEZE (DEFAULT) IS TRIGGERED: inside = $inside | thermostat val = ${thermostat?.currentValue('temperature')} backupSensorTemp = $backupSensorTemp backupSensor = $backupSensor defaultSafeTemp = $defaultSafeTemp (is this user's criticalcold set temp ? ${criticalcold == null ? false : true}"
                windows?.off() // make sure all windows linked to this instance are closed

                set_multiple_thermostats_mode("heat", "antifreeze", null)

                atomicState.resendAttempt = now()
                atomicState.antifreeze = true
                return true
                //sendNotification()
            }
            else {
                atomicState.antifreeze = false
                return false
            }
        }
        if (enablewarning) log.warn "mode: ${thermostat.currentValue("thermostatMode")}"
    }
}
def checkEfficiency(coolerNotEfficientEnough, boost, thermModes, need, inside){

    if (enabledebug) log.debug "coolerNotEfficientEnough = $coolerNotEfficientEnough boost = $boost thermModes = $thermModes"

    if (!coolerNotEfficientEnough && (thermModes.any{ it -> it != "off" } || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount)) && !boost)
    {
        atomicState.dontChekcThermostatStateCount += 1
        if (enabletrace) log.trace "preferCooler = true and need = cool thermosat kept off ${preferCoolerLimitTemperature ? "unless outside temperature reaches $preferCoolerLimitTemperature":""} 56er"
        if (!fanCirculateAlways) {
            if (enableinfo) log.info "thermostat off 5fh4z2"
            turn_off_thermostats(need, inside, thermModes)
            atomicState.offAttempt = now()
        }
    }
    else if (coolerNotEfficientEnough || boost) {
        message = "${boost ? 'boosting with ${thermotat} at user s request' : '${cooler} is not efficient enough turning $thermostat back on'} 44JKD"
        if (enabletrace) log.trace message

        if (thermModes.any{ it -> it != need } || (overrideThermostatModeCheckBeforeSendingCmd && atomicState.dontChekcThermostatStateCount < atomicState.reCheckStateCount))
        {
            atomicState.dontChekcThermostatStateCount += 1
            if (enablewarning) if (enablewarning) log.warn "cooler not efficient enough 5zr4z8h"

            set_multiple_thermostats_mode("cool", "efficiency boost", null)

            atomicState.resendAttempt = now()
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

    atomicState.lastNotification = atomicState.lastNotification != null ? atomicState.lastNotification : now()

    def dTime = 5 * 60 * 1000 // every 5 minutes

    if ((now() - atomicState.lastNotification) >= dTime) {
        atomicState.lastNotification = now()

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
    if (enabletrace) log.trace "Resetting forceCommand counter"
    atomicState.forceAttempts = 0
}
def setDimmer(val){
    if (simpleModeIsActive()) return
    if (enabletrace) log.trace "setDimmer $val"
    if (!atomicState.setPointOverride) {
        if (dimmer) {
            atomicState.setpointSentByApp = true
            runIn(3, resetSetByThisApp)
            dimmer.setLevel(Math.round(Double.parseDouble(val.toString()))) // some thermostats will parse set points as double. Here, to parse as double, first we need to parse as string, hence toString()
            //so it needs to be rounded so as to be parsed as a string in the dimmer driver        
            if (enableinfo) log.info "$dimmer set to $val BY THIS APP"
        }
        else {
            def thisVal = Math.round(Double.parseDouble(val.toString()))
            atomicState.lastThermostatInput = thisVal
            //atomicState.setpointSentByApp = true   // not applicable in this case since it won't trigger any device event
            if (enableinfo) log.info "atomicState.lastThermostatInput set to $thisVal"
        }
    }
    else {
        if (enabletrace) log.trace "SETPOINT OVERRIDE DUE TO THERMOSTAT DISCREPANCY NOT CHANGING DIMMER VALUE"
    }
    atomicState.setPointOverride = false
}
def virtualThermostat(need, target){

    try {
        if (enabledebug) log.debug "virtualThermostat need = $need atomicState.lastNeed = $atomicState.lastNeed"

        def outside = outsideTemp?.currentValue("temperature") // only needed if electric heater here
        def lowTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? get_celsius(28) : 28 : celsius ? get_celsius(40) : 40 
        def highTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? get_celsius(28) : 28 : celsius ? get_celsius(40) : 40 
        boolean lowLimitReached = heatpump && !addLowTemp ? true : !thermostat ? true : (heater || heatpump) && addLowTemp ? outside < lowTemperature : true
        //if heatpump, lowLimitReached is when it's too cold outside for a heatpump to remain efficient, or if threshold has been reached so the heater has to take over, if any...
        //if heater and no heatpump, lowLimitReached is when it's so cold that heater has to come and help
        //if heater AND no thermostat, heater runs all the time when needed, no low limit so lowLimitReached returns true

        boolean inAllHeatSourcesMode = useAllHeatSourcesWithMode && location.mode in allHeatModes 
        boolean altHeatExclusiveMode = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes || altThermostatOnlyInSimpleMode && simpleModeIsActive()
        boolean dontSendThermostatModeCmd = dontSetThermModesInSimpleMode && simpleModeIsActive() || ignoreMode

        if (heater || altThermostat || inAllHeatSourcesMode || altHeatExclusiveMode) {
            boolean tooMuchPower = false
            if (controlPowerConsumption && atomicState.lastNeed == "heat") {
                if (heater?.hasCapability("powerMeter") || heater?.hasAttribute("power")) {
                def pwVal = !pw ? 0 : pw?.currentValue("power")?.toInteger()
                def heaterVal = !heater ? 0 : heater?.currentValue("power")?.toInteger()
                def currentPower = pwVal + heaterVal
                    tooMuchPower = currentPower > maxPowerConsumption.toInteger()
                    tooMuchPower = devicePriority != "$heater" && heatpump && outside < lowTemperature ? false : tooMuchPower
                }
                else {
                    if (enablewarning) if (enablewarning) log.warn "$heater doesn't have power measurement capability"
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

            if (need == "heat" && atomicState.lastNeed == "heat") {
                if (lowLimitReached || useAllHeatSources || inAllHeatSourcesMode || altHeatExclusiveMode) {
                    if (heater?.currentValue("switch") == "off") {
                        heater?.on()    
                    def m = heater && altThermostat ? "Turning on heater and setting $altThermostat to heat" : heater ? "Turning on $heater 54d54" : altThermostat ? "setting $altThermostat to heat" : "no alternate heater or thermostat to manage"
                        if (enableinfo) log.info "$m"
                    }
                    else {
                        if (enabletrace) log.trace "heater is on"
                    }
                    if (altThermostat) {
                        if (altThermostat?.currentValue("thermostatMode") != "heat" || altThermostat?.currentValue("heatingSetpoint") != target) {
                            if (enabletrace) log.trace "$altThermostat set to heat and $target 5zrgj5r"
                            if (!dontSendThermostatModeCmd) {
                                altThermostat?.setThermostatMode("heat")
                            }
                            else {
                                if (enabletrace) log.trace "dontSendThermostatModeCmd true 6rer5r4z63"
                            }
                        boolean inpowerSavingMode = location.mode in powersavingmode
                            if (checkIgnoreTarget() && !inpowerSavingMode) {
                                if (enabletrace) log.trace  "Target ($target) temp not sent to $altThermostat at user's request"
                            }
                            else {
                                if (enabletrace) log.trace "$altThermostat heat set to $target 54zgy8ui6"
                                altThermostat?.setHeatingSetpoint(target)
                            }
                        }
                    }
                }
                else if (useAllHeatSourcesWithMode && !inAllHeatSourcesMode) {
                    if (enabletrace) log.trace "outside of useAllHeatSourcesWithMode modes ${altThermostat && heater ? "$altThermostat & $heater stay off" : altThermostat ? "$altThermostat stays off" : heater ? "$heater stays off" : ""} "
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
            else if (coolerControlPowerConsumption && atomicState.lastNeed == "cool") {
                if (powerCapable) {
                def currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
                    tooMuchPower = CurrentPower > coolerMaxPowerConsumption.toInteger()
                    tooMuchPower = coolerDevicePriority != "$cooler" && outside > lowTemperature ? false : tooMuchPower
                }
                else {
                    if (enablewarning) if (enablewarning) log.warn "$cooler doesn't have power measurement capability"
                    app.updateSetting("coolerControlPowerConsumption", [type: "bool", value: false])
                    tooMuchPower = false
                }

                if (tooMuchPower && coolerDevicePriority != "$cooler") // if thermosat isn't priority it's overriden if high temp outside is true
                {
                    need = "off"
                    if (enablewarning) if (enablewarning) log.warn "$thermostat and $cooler use too much power at the same time. Turning $cooler off since $thermostat has precedence"
                }
            }

            atomicState.coolCmdSent = atomicState.coolCmdSent == null ? false : atomicState.coolCmdSent
            atomicState.offCmdSent = atomicState.offCmdSent == null ? false : atomicState.offCmdSent

        def coolerCurVal = cooler?.currentValue("switch")
        def powerDiscrepancy = false
        def powerValue = null
            if (powerCapable) {
                powerValue = cooler?.currentValue("power")
                powerDiscrepancy = atomicState.coolCmdSent && need == "cool" && powerValue < 100 ? true : atomicState.offCmdSent && need == "off" && powerValue > 100 ? true : false
            }

            if (enabletrace) log.trace "coolerCurVal = ${coolerCurVal} ${powerCapable ? "powerValue = $powerValue" : ""} powerDiscrepancy = $powerDiscrepancy"

            if (need == "cool") {
                if (coolerCurVal != "on" || powerDiscrepancy) {
                    if (enabletrace) log.trace "${powerDiscrepancy ? "POWER DISCREPANCY: turning on $cooler AGAIN" : "turning on $cooler 89e4"}"
                    cooler?.on()
                    atomicState.coolerTurnedOnTimeStamp = now()
                    atomicState.coolCmdSent = true
                    atomicState.offCmdSent = false
                    //poll_power_meters()
                }
                else {
                    if (enableinfo) log.info "$cooler already on"
                }
            }
            //////////////////
            if (need == "off") {
                if (coolerCurVal != "off" || powerDiscrepancy) {
                    if (enabletrace) log.trace "${powerDiscrepancy ? "POWER DISCREPANCY: turning off $cooler AGAIN" : "turning off $cooler"}"
                    cooler?.off()
                    atomicState.coolCmdSent = false
                    atomicState.offCmdSent = true
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
def windowsControl(target, simpleModeActive, inside, outside, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh, thermModes){

    // motionActive argument not sent through because in get_need() it's mixed with simpleModeActive
    // so that would open windows every time $simpleModeName Mode would be active instead of cooling the room with AC as needed

    //atomicState.lastClosingTime = 360000000 //TESTS

    if (controlWindows && windows && (!simpleModeActive || allowWindowsInSimpleMode) && !atomicState.override) {
        if (location.mode in windowsModes) {
            // do nothing
        }
        else if (closeWhenOutsideWindowsModes && (windows.any{ it -> it.currentValue("switch") == "on" } || windows.any{ it -> it.currentValue("contact") == "open" }))
        {
            if (enabletrace) log.trace "outside windows mode"
            if (!atomicState.windowsClosedDueToOutsideMode) {
                if (atomicState.openByApp) windows?.off()
                if (enabletrace) log.trace "closing windows because outside windows mode"
                atomicState.windowsClosedDueToOutsideMode = true
                atomicState.openByApp = false
                atomicState.closedByApp = true
            }
            else {
                if (enableinfo) log.info "windows already closed by location mode management"
            }
            return;
        }
        atomicState.windowsClosedDueToOutsideMode = false // reset this value once the return statement is no longer called since location is in windows modes again

        def humThres = get_humidity_threshold() // linear equation: hum thres varies with outside temp
        boolean tooHumid = humidity >= 90 ? true : humidity >= humThres 
        boolean contactCapable = windows.any{ it -> it.hasCapability("ContactSensor") }//?.size() == windows.size() 
        boolean someAreOff = contactCapable ? (windows.findAll{ it?.currentValue("contact") == "closed" }?.size() > 0) : (windows.findAll{ it?.currentValue("switch") == "off" }?.size() > 0)
        boolean someAreOpen = contactCapable ? (windows.findAll{ it?.currentValue("contact") == "open" }?.size() > 0) : (windows.findAll{ it?.currentValue("switch") == "on" }?.size() > 0)
        boolean withinRange = outside < outsidetempwindowsH && outside > outsidetempwindowsL // strict temp value

        boolean outsideWithinRange = withinRange && !tooHumid // same as withinRange but with humidity

        atomicState.lastOpeningTime = atomicState.lastOpeningTime != null ? atomicState.lastOpeningTime : now() // make sure value is not null
        atomicState.outsideTempAtTimeOfOpening = atomicState.outsideTempAtTimeOfOpening != null ? atomicState.outsideTempAtTimeOfOpening : outside // make sure value is not null
        boolean outsideTempHasDecreased = outside < atomicState.outsideTempAtTimeOfOpening - swing // serves mostly to reset opening time stamp
        atomicState.outsideTempAtTimeOfOpening = outsideTempHasDecreased ? outside : atomicState.outsideTempAtTimeOfOpening // if outsideTempHasDecreased true, reset outsidetemAtTimeOfOpening stamp so to use outsideTempHasDecreased only once 
        atomicState.lastOpeningTime = outsideTempHasDecreased ? now() : atomicState.lastOpeningTime // reset opening time stamp if it got cooler outside, allowing more time to cool the room

        atomicState.insideTempAtTimeOfOpening = atomicState.insideTempAtTimeOfOpening ? atomicState.insideTempAtTimeOfOpening : inside // make sure value is not null
        boolean insideTempHasIncreased = inside > atomicState.insideTempAtTimeOfOpening + swing // serves for windows wider opening ONLY
        atomicState.insideTempHasIncreased = insideTempHasIncreased
        atomicState.widerOpeningDone = (atomicState.widerOpeningDone != null) ? atomicState.widerOpeningDone : (atomicState.widerOpeningDone = false) // make sure value is not null
        boolean openMore = !atomicState.widerOpeningDone && insideTempHasIncreased && someAreOpen

        boolean insideTempIsHopeLess = inside > atomicState.insideTempAtTimeOfOpening + 2 && atomicState.widerOpeningDone

        double lastOpeningTime = (now() - atomicState.lastOpeningTime) / 1000 / 60
        lastOpeningTime = lastOpeningTime.round(2)
        boolean beenOpenForLong = lastOpeningTime > 30 && someAreOpen // been open for more than 15 minutes

        atomicState.lastClosingTime = atomicState.lastClosingTime ? atomicState.lastClosingTime : (atomicState.lastClosingTime = now()) // make sure value is not null
        double lastClosingTime = (now() - atomicState.lastClosingTime) / 1000 / 60
        lastClosingTime = lastClosingTime.round(2)
        boolean closedSinceLong = lastClosingTime > 30 && someAreClosed // been closed for more than 30 minutes

        boolean tooColdInside = inside <= target - 8
        if (enablewarning) log.warn "tooColdInside = $tooColdInside : inside = $inside && target = $target"
        //closing error management for safety, if cmd didn't go through for whatever reason and temp went too low, force close the windows
        boolean exception = someAreOpen && ((atomicState.closedByApp && (now() - lastClosingTime) > 30 && tooColdInside) || (!outsideWithinRange && tooColdInside))
        long elapsed = now() - lastClosingTime
        def elapsedseconds = elapsed / 1000
        def elapsedminutes = elapsed / 1000 / 60
        if (enablewarning) if (exception) { if (enablewarning) log.warn "$windows still open! EMERGENCY CLOSING WILL BE ATTEMPTED" }

        // allow for more frequent window operation when outside temp might be low enough to cool the room fast
        boolean outsideSubstantiallyLowEnough = outside < 72 && outside > outsidetempwindowsL
        float timeBtwWinOp = outsideSubstantiallyLowEnough ? 5 : 15// 5 min if it's cool enough outside, otherwise, give it 15 min before reopening

        boolean enoughTimeBetweenOpenAndClose = ((now() - atomicState.lastOpeningTime) / 1000 / 60) > 10.0 || inside < target - swing //-> give it a chance to cool down the place
        boolean enoughTimeBetweenCloseAndOpen = ((now() - atomicState.lastClosingTime) / 1000 / 60) > timeBtwWinOp //-> don't reopen too soon after closing

        boolean needToClose = (enoughTimeBetweenOpenAndClose && ((inside > target + (swing * 3) && beenOpenForLong) || inside < target - swing || insideTempIsHopeLess)) || !outsideWithinRange
        boolean needToOpen = (enoughTimeBetweenCloseAndOpen && (inside > target + swing && !needToClose)) && outsideWithinRange //|| amplitudeTooHigh) // timer ok, too hot inside + within range (acounting for humidity) and no discrepency

        atomicState.otherWindowsOpenByApp = atomicState.otherWindowsOpenByApp == null ? false : atomicState.otherWindowsOpenByApp
        boolean synchronize = doorsManagement && doorsOpen() && otherWindows.any{ it.currentValue("switch") == "on" } && !atomicState.otherWindowsOpenByApp
        if (synchronize) {
            atomicState.otherWindowsOpenByApp = true
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
                        atomicState.widerOpeningDone = true
                        unschedule(stop)
                    }

                    if (atomicState.closedByApp || (openMore && atomicState.openByApp)) {
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
                                atomicState.openByApp = true
                                atomicState.closedByApp = false
                            }
                            if (enableinfo) log.info message
                        }
                        else {
                            if (atomicState.closedByApp) {
                                message = "opening ${windows.join(", ")} 564df4"
                                if (enablewarning) if (enablewarning) log.warn message
                                for (w in windows) {
                                    if (enablewarning) if (enablewarning) log.warn "Opening => $w"
                                    w.on()
                                }
                                atomicState.openByApp = true
                                atomicState.closedByApp = false
                            }
                            else {
                                if (enablewarning) if (enablewarning) log.warn "windows were not closed by this app, doing nothing"
                            }
                            if (enabletrace) log.trace "openMore && atomicState.openByApp ===> ${openMore && atomicState.openByApp}"
                        }

                        need0 = "off"
                        need1 = "off"
                        atomicState.lastContactOpenEvt = atomicState.lastContactOpenEvt ? atomicState.lastContactOpenEvt : now()
                        def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0
                        if (contactsAreOpen() && (now() - atomicState.lastContactOpenEvt) > delayB4TurningOffThermostat) {
                            if (!fanCirculateAlways) {
                                if (enableinfo) log.info "thermostat off 4trgh26"
                                turn_off_thermostats(need1, inside, thermModes)
                                atomicState.offAttempt = now()
                            }
                            else {
                                // fan circulation conditions managed in get_need() scope
                            }
                        }
                        if (!openMore) {
                            atomicState.lastOpeningTime = now()
                            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                            atomicState.outsideTempAtTimeOfOpening = outside
                            atomicState.insideTempAtTimeOfOpening = inside
                        }

                        if (enablewarning) if (enablewarning) log.warn "--------------------------------differentDuration = $differentDuration"
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
                                    if (enablewarning) if (enablewarning) log.warn "found : $device with max = $max minimum = $min *********************"

                                    time = get_windows_operation_time(outside, max, min)
                                    runIn(time, stop, [data: ["device": "${device}"], overwrite: false])
                                    if (enablewarning) if (enablewarning) log.warn "$device scheduled to stop in $time seconds"

                                }
                            }
                        }
                        if (enablewarning) log.warn message
                        atomicState.openByApp = true
                        atomicState.closedByApp = false
                    }
                    else {
                        if (enablewarning) if (enablewarning) log.warn "${windows.join(", ")} were not closed by this app - ignoring on/open request"
                    }
                }
                else {
                    if (enableinfo) log.info "$windows already open"
                }
            }
            else if (someAreOpen && needToClose) {
                if ((atomicState.openByApp) || exception) {
                    if (enablewarning) if (exception) { if (enablewarning) log.warn "EXCEPTION CLOSING" }
                    if (enablewarning) if (enablewarning) log.warn "closing $windows"
                    unschedule(stop)
                    atomicState.closingCommand = true // the evt handler will need to know the "closed" event is not just a refresh (isStateChange can fail)
                    atomicState.lastClosingTime = now()
                    atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                    atomicState.widerOpeningDone = false // simple value reset
                    windows.off()
                    atomicState.otherWindowsOpenByApp = false
                    boolean  levelCapabality = windows.any{ it.hasCapability("Switch Level") }
                    if (exception) {
                        windows.setLevel(50)
                    }
                    else {
                        windows.setLevel(1)
                    }

                    atomicState.openByApp = false
                    atomicState.closedByApp = true
                    if (enableinfo) log.info "56FG"

                }
                else if (!atomicState.openByApp) {
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
            if (someAreOpen && atomicState.openByApp) // && (inside > target + 2 || inside < target - 2 ))
            {
                windows.off()
                atomicState.otherWindowsOpenByApp = false
                if (windows.any{ it.hasCapability("Switch Level") }) {
                    windows.setLevel(50)
                }
                //if(enableinfo) log.info "56TG"
                atomicState.openByApp = false
                atomicState.closedByApp = true

            }
        }

    }
    else if (!windows) {
        if (enabledebug) log.debug "user did not select any window switch"
    }
    else if (simpleModeActive) {
        if (enableinfo) log.info "skipping windows management due to $simpleModeName Mode trigger mode"
    }
    else if (atomicState.override) {
        if (enableinfo) log.info "Override mode because $thermostat is set to 'auto'"
    }

}
def resetSetByThisApp(){
    if (atomicState.setpointSentByApp) {
        atomicState.setpointSentByApp = false;
    }
}
def userWants(val, inside){
    if (enablewarning) if (enablewarning) log.warn "userWants($val while inside is $inside)"
    if (val < inside - 2) {
        atomicState.userWantsCooler = true
        atomicState.userWantsCoolerTimeStamp = now()
        runIn(120, resetUserWants)
    }
    else {
        atomicState.userWantsCooler = false
    }
    if (val > inside + 2) {
        atomicState.userWantsWarmer = true
        atomicState.userWantsWarmerTimeStamp = now()
        runIn(120, resetUserWants)
    }
    else {
        atomicState.userWantsWarmer = false
    }
}
def resetUserWants(){
    if (enablewarning) if (enablewarning) log.warn "resetUserWants()"
    atomicState.userWantsWarmer = false
    atomicState.userWantsCooler = false
}


/* ############################### GETTERS ############################### */

def get_thermostats_modes() {
    def thermModes = []
    try {
        if (differentiateThermostatsHeatCool) {

            def neededThermostats = get_needed_thermosats(atomicState.lastNeed)
            thermModes = neededThermostats.collect{ it -> it.currentValue("thermostatMode") }
        }
        else {
            thermModes = [thermostat.currentValue("thermostatMode")]
        }
    } catch (Exception e) {
        log.error "thermModes (setPointHandler) => $e"
        return ["off", "off"]
    }
    if (enabletrace) log.trace "get_thermostats_modes returns: ${thermModes}"
    return thermModes
}
def get_target(simpleModeActive, inside, outside){

    int target = 74 // default value


    if (method == "auto" && !simpleModeActive) {
        target = get_auto_value() as int
        if (enabletrace) log.trace  "get_auto_value() returned $target"
    }
    else if (!simpleModeActive) {
        if (enablewarning) log.warn "dimmer = $dimmer dimmer?.currentValue(level) = ${get_dimmer_value()}"
        target = !dimmer ? atomicState.lastThermostatInput.toInteger() : get_dimmer_value().toInteger()
    }

    // safety checkup for when alexa misinterpret a command to a light dimmer and applies it to the dimmer used by this app
    def maxHi = celsius ? get_celsius(88) : 88 // assuming no one would be stupid enough to set their thermostat to 88, if so it's interpreted as a problem by the app
    def minLow = celsius ? get_celsius(30) : 30 // same, but when setpoint is too low
    def lastNeed = atomicState.lastNeed
    boolean problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false

   
    def debug = [
        "Celsius ? $celsius",
        "maxAutoHeat = $maxAutoHeat",
        "minAutoCool = $minAutoCool",
        "maxHi = $maxHi",
        "minLow = $minLow",
        "atomicState.lastThermostatInput = $atomicState.lastThermostatInput ",
        "dimmer value = ${get_dimmer_value()}",
        "heatingSetpoint = ${thermostat.currentValue('heatingSetpoint')}",
        "coolingSetpoint = ${thermostat.currentValue('coolingSetpoint')}",
        "boolean problem = $target >= $maxHi ? ${target >= maxHi} : $target <= $minLow ? ${target <= minLow} : false",
        "atomicState.lastNeed = $atomicState.lastNeed",
        "problem detected : $problem",
        "target = $target"
    ]
    if (enabledebug) debug_from_list(debug)


    if (problem) {
        if (enablewarning) if (enablewarning) log.warn "There's a problem with current target temperature ($target). Readjusting from $thermostat setpoints"
        target = celsius ? get_celsius(74) : 74
        // fix the dimmer's value if any dimmer
        setDimmer(target.toString())
        if (enablewarning) if (enablewarning) log.warn "************ $app.name successfuly fixed its target temperature data point! ********"
    }
    problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false

    debug = [
        "maxHi = $maxHi",
        "minLow = $minLow",
        "target = $target",
        "problem =$problem"
    ]
    if (enabledebug) debug_from_list(debug)

    if (problem) {
        if (enablewarning) log.warn "${thermostat}'s values are as inconsistent as ${dimmer ? "${ dimmer } 's" : "the previous'"}, meaning data retrieval failed. Applying safe values instead until next user's input"
        target = celsius ? get_celsius(74) : 74 // safe value

    }
    if (enabletrace) log.trace  "simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ****** target = $target************ dimmerLevel = ${get_dimmer_value()}"

    if (simpleModeActive && !simpleModeSimplyIgnoresMotion) {
        if (doorsOpen() && contactsOverrideSimpleMode) {
            if (enabletrace) log.trace "some doors are open: $simpleModeName Mode trigger mode ignored at user's request"
        }
        else if (setSpecialTemp || specialSubstraction) {
            target = specialSubstraction ? target - substract : specialTemp && specialDimmer ? specialDimmer.currentValue("level") : specialTemp

            if (enabletrace) log.trace  "target temperature ${substract ? '(specialSubstraction)':'(specialTemp)'} is: $target and last recorded temperature is ${inside} | specialDimmer level = ${specialDimmer.currentValue('level')}"
            return target // END due to simpleModeName Mode trigger mode
        }
        else {
            if (enabletrace) log.trace "simpleModeActive using default target temp: $target | last recorded temperature is ${inside}"
            return target // return the default value
        }

    }
    if (enabletrace) log.trace  "target temperature is: $target and current temperature is ${inside} (swing = $atomicState.swing) thermostat state is: ${thermostat.currentValue('thermostatMode')}"

    return target
}
def get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, neededThermostats, thermModes, origin){

    if (enabletrace) log.trace "get_need called from $origin | target: $target | outside: $outside | simpleModeActive: $simpleModeActive | inside: $inside | neededThermostats: $neededThermostats | thermModes: $thermModes"

    def humidity
    def insideHum
    def outsideThres
    def need0
    def need1
    def need
    def amplThreshold
    def amplitude
    def lo
    def hi
    def swing
    def loCoolSw
    def message
    def n
    boolean doorsOverride
    boolean INpwSavingMode
    boolean isBetween
    boolean withinTimeWindow
    boolean inWindowsModes
    boolean contactClosed
    boolean amplitudeTooHigh
    boolean swamp
    boolean toohot
    boolean needCool
    boolean needHeat
    boolean norHeatNorCool
    boolean unacceptable
    boolean timeToResend
    boolean here



    atomicState.userWantsWarmerTimeStamp = atomicState.userWantsWarmerTimeStamp == null ? now() : atomicState.userWantsWarmerTimeStamp
    atomicState.userWantsCoolerTimeStamp = atomicState.userWantsCoolerTimeStamp == null ? now() : atomicState.userWantsCoolerTimeStamp

    try {


        atomicState.lastTimeCool = atomicState.lastTimeCool == null ? 3 * 60 * 1000 + 1 : atomicState.lastTimeCool
        atomicState.lastTimeHeat = atomicState.lastTimeHeat == null ? 3 * 60 * 1000 + 1 : atomicState.lastTimeHeat
        atomicState.userWantsWarmer = atomicState.userWantsWarmer == null || (now() - atomicState.userWantsWarmerTimeStamp) >= 120 * 60 * 1000 ? false : atomicState.userWantsWarmer
        atomicState.userWantsCooler = atomicState.userWantsCooler == null || (now() - atomicState.userWantsCoolerTimeStamp) >= 120 * 60 * 1000 ? false : atomicState.userWantsCooler

        humidity = outsideTemp?.currentValue("humidity")
        insideHum = get_inside_humidity() // backup for windows and value used for negative swing variation when cooling   
        humidity = humidity != null ? humidity : (insideHum != null ? insideHum : 50)
        // boolean doorsContactsAreOpen = doorsOpen()
        doorsOverride = doorsManagement ? doorsOverrideMotion : true // must return true by default if none due to "(!doorsContactsAreOpen && doorsOverrideMotion)"
        INpwSavingMode = powersavingmode != null && location.mode in powersavingmode ? true : false //!simpleModeActive && (!doorsContactsAreOpen && doorsOverrideMotion)
        isBetween = timeWindowInsteadofModes ? timeOfDayIsBetween(toDateTime(windowsFromTime), toDateTime(windowsToTime), new Date(), location.timeZone) : false

        try {
            if (enablewarning) {
                message = ["<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-10%; padding-inline-start:20px'>",
                    "<br> ---------------------------- ",
                    "<br> current mode = $location.mode  ",
                    "<br> IN POWER SAVING MODE ? $INpwSavingMode  ",
                    "<br> powersavingmode = $powersavingmode  ",
                    "<br> simpleModeActive = ${simpleModeActive}  ",
                    "<br> simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ",
                    "<br> !doorsContactsAreOpen = ${!doorsContactsAreOpen} ",
                    "<br> location.mode in powersavingmode = ${location.mode in powersavingmode} ",
                    "<br> doorsOverrideMotion = $doorsOverrideMotion ",
                    "<br> atomicState.userWantsWarmerTimeStamp = $atomicState.userWantsWarmerTimeStamp ",
                    "<br> atomicState.userWantsCoolerTimeStamp = $atomicState.userWantsCoolerTimeStamp ",
                    "<br> now() - atomicState.userWantsWarmerTimeStamp => ${(now() - atomicState.userWantsWarmerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - atomicState.userWantsWarmerTimeStamp) >= 120 * 60 * 1000} ",
                    "<br> now() - atomicState.userWantsCoolerTimeStamp => ${(now() - atomicState.userWantsCoolerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - atomicState.userWantsCoolerTimeStamp) >= 120 * 60 * 1000}     ",
                    "<br> current mode = $location.mode ",
                    "<br> ---------------------------- ",
                    "</div>"
                ]

                if (enablewarning) log.warn message.join()
            }
        } catch (Exception e) {
            log.error "get_need debug => ${e}"
        }

        withinTimeWindow = timeWindowInsteadofModes ? isBetween : true // no pun intended... 
        inWindowsModes = timeWindowInsteadofModes ? windows && withinTimeWindow : windows && location.mode in windowsModes
        contactClosed = !contactsAreOpen()



        outsideThres = get_outside_threshold()
        need0 = ""
        need1 = ""
        need = [need0, need1]
        amplThreshold = 3
        amplitude = Math.abs(inside - target)
        lo = celsius ? get_celsius(50) : 50
        hi = celsius ? get_celsius(75) : 75
        swing = outside < lo || outside > hi ? 0.5 : 1 // lower swing when hot or cold outside
        atomicState.swing = swing

        loCoolSw = celsius ? get_celsius(60) : 60
        amplitudeTooHigh = amplitude >= amplThreshold // amplitude between inside temp and target / preventing amplitude paradox during mid-season

        // prevent the swamp effect: not so hot outside, but very humid so cooling might be needed. NB: overrides simple mode. 
        swamp = insideHum >= 50 && inside > target + swing && (!inWindowsModes || contactClosed) && (atomicState.lastNeed == "cool" || (need == "off" && insideHum >= 65 && amplitude >= amplThreshold))
        toohot = inside >= target && amplitudeTooHigh // usefull when inside's inertial heat gets too strong during shoulder season. 

        // remember toohot event so as to continue to override normal eval until target is reached
        if (toohot && !atomicState.wasTooHot) {
            atomicState.wasTooHot = true
        }
        if (inside <= target && atomicState.wasTooHot) { // once and only once target is reached, and if there was an override previously set, cancel this cooling decision's override
            atomicState.wasTooHot = false
        }
        try {
            needCool = atomicState.userWantsCooler ? true :
                (swamp ? true :
                    atomicState.lastNeed == "cool" && (toohot || atomicState.wasTooHot) ? true :
                        (!simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ?
                            (inWindowsModes ?
                                outside >= outsideThres && inside >= target + swing :
                                outside >= outsideThres && inside >= target + swing || swamp
                            ) :
                            inside >= target + swing))

            if (enablewarning || swamp) {
                if (enablewarning) log.warn "needCool => $needCool"
                if (enablewarning) log.warn "swamp => $swamp"
            }


        }
        catch (Exception e) {
            log.error "get_need neddCool => $e"
        }
        try {
            needHeat = !simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ?
                (
                    outside < outsideThres || (
                        amplitudeTooHigh && atomicState.lastNeed != "cool"
                    )
                ) && inside <= target - swing :
                inside <= target - swing && outside < outsideThres

            needHeat = atomicState.userWantsWarmer || inside < target - 4 ? true : needHeat
        }
        catch (Exception e) {
            log.error "get_need needHeat => $e"
        }

        // shoulder season management: simpleModeName Mode trigger forces ac to run despite cold outside if it gets too hot inside
        norHeatNorCool = !needCool && !needHeat && inside > target + swing && simpleModeActive && outside >= 55 ? true : false
        // the other room could be in an inadequate mode, which would be noticed by an undesirable temperature amplitude
        unacceptable = doorsContactsAreOpen && !atomicState.override && (inside < target - 2 || inside > target + 2) // if it gets too cold or too hot, ignore doorsManagement
    }
    catch (Exception e) {
        log.error "get_need boolean contingencies => $e"
    }
    message = []

    try {
        if (enabletrace) {
            message = [
                "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-20%'>",
                "inside = $inside ",
                "target = $target ",
                "$inside < ${target - 2} : ${inside < target - 2} ",
                "$inside > ${target + 2} : ${inside > target + 2}",
                "</div>"
            ]

            log.trace message.join()
        }
    } catch (Exception e) {
        log.error "get_need debug 2 => ${e}"
    }

    try {
        if (enablewarning) log.warn "doorsContactsAreOpen = $doorsContactsAreOpen"
        if (unacceptable) // when doors are open, other room's thermostat manager might be in power saving mode
        {
            if (enableinfo) log.info format_text("UNACCEPTABLE TEMP - ignoring doors management sync", "red", "white")
        }
        if (enabledebug) {
            message = [
                "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-20%'>",
                "INpwSavingMode = $INpwSavingMode",
                "contactClosed = $INpwSavingMode",
                "motionActive = $INpwSavingMode",
                "<div>",
            ]
            log.debug message.join()
        }
        if (!unacceptable && doorsManagement && doorsContactsAreOpen && contactClosed) {
            n = otherRoomCooler ? otherRoomCooler.currentValue("switch") == "on" ? "cool" : "off" : doorThermostat?.currentValue("thermostatMode")
            need0 = n.capitalize() // capital letter for later construction of the setCoolingSetpoint cmd String
            need1 = n

            message = "$doorsContacts ${doorsContacts.size() > 1 ? "are":"is"} open. $thermostat set to ${doorThermostat}'s mode ($n)"
            if (enableinfo) log.info "<div style=\"inline-size:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">$message</div>"

        }
        else if (!INpwSavingMode && contactClosed && motionActive) {
            if (needCool || needHeat || norHeatNorCool) {
                if (needCool || norHeatNorCool) {
                    if (enableinfo) log.info "needCool true"
                    need0 = "Cool"// capital letter for later construction of the setCoolingSetpoint cmd
                    need1 = "cool"
                    if (enabledebug) log.debug "need set to ${[need0,need1]}"
                }
                else if (needHeat) // heating need supercedes cooling need in order to prevent amplitude paradox
                {
                    if (enableinfo) log.info "needHeat true"
                    need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
                    need1 = "heat"
                    if (enabledebug) log.debug "need set to ${[need0,need1]}"
                }
            }
            else if (offrequiredbyuser) {
                need0 = "off"
                need1 = "off"
                if (enabledebug) log.debug "need set to OFF 6f45h4"
            }
            else {

                if (fanCirculateAlways) {
                    need0 = "off"
                    need1 = "off"
                    if (enabledebug) log.debug "need set to OFF"
                }
                else {
                    if (enableinfo) log.info "Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)"
                }
            }
        }
        else   // POWER SAVING MODE OR NO MOTION OR CONTACTS OPEN     
        {
            if (enabledebug) log.debug "POWER SAVING MODE EVAL" 

            
            def cause = location.mode in powersavingmode ? "$location.mode" :
                !motionActive ? "no motion" :
                    INpwSavingMode ? "power saving mode" :
                        !contactClosed ? "Contacts Open" :
                            "UNKNOWN CAUSE - SPANK THE DEVELOPER"

            cause = cause == "Contacts Open" ? "${cause}: ${atomicState.listOfOpenContacts}" : cause

            message = ""

            if (enabledebug) log.debug "inside < criticalhot :  ${inside < criticalhot} | inside > criticalcold :  ${inside > criticalcold}"

            need0 = "off"
            need1 = "off"

            if (criticalhot == null) {
                app.updateSetting("criticalhot", [type: "number", value: "80"])
            }

            if (inside > criticalhot || fancirculateAllways) {

                if (enablewarning) log.warn format_text("${fancirculateAllways ? "fan set to always be on" : "POWER SAVING MODE EXPCETION: TOO HOT!(${ cause } criticalhot = ${ criticalhot })"}", "black", "red")

                if (!contactClosed) {
                    need0 = "off"
                    need1 = "off"
                }
                else {
                    need0 = "Cool"
                    need1 = "cool"
                    set_multiple_thermostats_mode("cool", "getNeed() criticalhot event", null) // don't modify this string, it's used as a forceCommand criterium in set_multiple_thermostats_mode() function
                    return [need0, need1]
                }

            }
            else if (inside < criticalcold) {
                if (enablewarning) if (enablewarning) log.warn format_text("POWER SAVING MODE EXPCETION: TOO COLD! ($cause criticalcold = $criticalcold)", "white", "blue")

                // always allow the heater to run when critically cold, even when contacts might be open
                need0 = "Heat"
                need1 = "heat"
                set_multiple_thermostats_mode("heat", "getNeed() criticalhot event", null) // don't modify this string, it's used as a forceCommand criterium in set_multiple_thermostats_mode() function
                return [need0, need1]
            }
            else {
                set_multiple_thermostats_fan_mode("auto", "back to auto when thermostat off")
                atomicState.fanOn = false
                turn_off_thermostats(need1, inside, thermModes)
            }

            message = format_text("POWER SAVING MODE ACTIVE ($cause)", "white", "#90ee90")   

            def fanCmd = keepFanOnInNoMotionMode && !motionActive ? "on" : "off" // only when motion is not active, otherwise it'll also turn on when windows are open
            if (fan && fan?.currentValue("switch") != "fanCmd") {
                if (enableinfo) log.info "$fan turned $fanCmd hyr354"
                fan?."${fanCmd}"()
            }

            if (enablewarning) log.warn message

        }
    } catch (Exception e) {
        log.error "get_need exception and power saving mode management => ${e}"
    }

    /****************WINDOWS MANAGEMENT*************************/
    try {
        windowsControl(target, simpleModeActive, inside, outside, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh, thermModes)
    }
    catch (Exception e) {
        message = [
            "<br> target: $target",
            "<br> simpleModeActive: $simpleModeActive",
            "<br> inside: $inside",
            "<br> outsi: $outsi",
            "<br> humidity: $humidity",
            "<br> swing: $swing",
            "<br> needCool: $needCool",
            "<br> inWindowsModes: $inWindowsModes",
            "<br> amplitudeTooHigh: $amplitudeTooHigh"
        ]

        log.debug message.join()

        log.error "windowsControl call from get_need => $e"
    }
    /****************SIMPLE MODE MANAGEMENT*************************/
    try {
        if (UseSimpleMode && (simpleModeActive && !doorsContactsAreOpen && !contactsOverrideSimpleMode && !simpleModeSimplyIgnoresMotion)) // 
        {
            if (enableinfo) log.info format_text("$simpleModeName Mode Enabled", "white", "grey")
        }
        else if (UseSimpleMode && simpleModeActive && contactsOverrideSimpleMode && doorsOpen) {
            if (enableinfo) log.info format_text("$simpleModeName Mode Called but NOT active due to doors being open", "white", "grey")
        }
        else if (UseSimpleMode) {
            if (enableinfo) log.info format_text("$simpleModeName Mode Disabled", "white", "grey")
        }
    }
    catch (Exception e) {
        log.error "get_need UseSimpleMode management => $e"
    }

    /****************differentiateThermostatsHeatCool MANAGEMENT*************************/
    try {
        if (differentiateThermostatsHeatCool) {


            if (enabledebug) {
                message = [

                    "thermostat is: $thermostat | its id is: ${thermostat?.id}",
                    "neededThermostats[0] is ${neededThermostats[0]} | its id is: ${neededThermostats[0]?.id}"
                ]

                log.debug debug_from_list(message)
            }

            if (thermostat?.id != neededThermostats[0]?.id) {
                if (enablewarning) if (enablewarning) log.warn "using ${neededThermostats[0]} as ${need1 == "cool" ? "cooling unit" : "heating unit"} due to user's requested differenciation"
                app.updateSetting("thermostat", [type: "capability", value: neededThermostats[0]])
                atomicState.otherThermWasTurnedOff = false // reset this value so as to allow to turn off the other unit now that we swapped them
            }

            remainsOff = get_remains_off(need1)

            if (enabletrace) log.trace "remainsOff =====> $remainsOff"

            if (remainsOff) {
                if ((remainsOff.currentValue("thermostatMode") != "off" || keep2ndThermOffAtAllTimes) && (!atomicState.otherThermWasTurnedOff || keep2ndThermOffAtAllTimes)) {
                    atomicState.keepOffAtAllTimesRun = atomicState.keepOffAtAllTimesRun == null ? 8 * 60 * 1000 : atomicState.keepOffAtAllTimesRun

                long deltaTimeResend = 1 * 60 * 1000 // minutes 
                    atomicState.resentOccurences = atomicState.resentOccurences == null ? 0 : atomicState.resentOccurences
                    timeToResend = (now() - atomicState.keepOffAtAllTimesRun) > deltaTimeResend && atomicState.resentOccurences < 5 // resend off command every x minutes

                    if (keep2ndThermOffAtAllTimes && timeToResend) {
                        atomicState.keepOffAtAllTimesRun = now() //prevent sending too many requests when user enabled this fail-safe option

                        if (need == "heat" && inside <= target - 15) {
                            if (enablewarning) log.warn "ignoring keep2ndThermOffAtAllTimes, ist far too cold"
                        }
                        else {
                            // remainsOff.off()
                            set_thermostat_mode(remainsOff, "off", "remainsOffCmd")
                            atomicState.resentOccurences += 1
                        }
                    }
                    else {
                        if (atomicState.resentOccurences >= 4 && need1 == "heat") {
                            if (enablewarning) log.warn "$remainsOff seems to want to remain in $need1 mode... giving up on trying to keep it off to avoid pissing the user or damaging the hardware or overriding a vital antifreeze"
                        }
                        else if (atomicState.resentOccurences >= 4 && need1 == "cool") {
                            if (enablewarning) log.warn "$remainsOff seems to insist in staying in $need1 mode... since mode is 'cool', $app.name will continue to send off commands"
                            atomicState.resentOccurences = 0
                        }
                    }
                    atomicState.otherThermWasTurnedOff = true // allow user to manually turn this unit back on, since we won't use it here until weather changes
                }
            }
        }
    }
    catch (Exception e) {
        log.error "get_need differentiateThermostatsHeatCool management => $e"
    }

    /****************DELAYED MODES SWITCH MANAGEMENT*************************/

    // if lastNeed was A and new need is B, then it's a mode switch, which requires a delay for multiple reasons: 
    // 1. HVAC or other electrical heaters/coolers operating stability and, in the long run, lifespan
    // 2. Avoids wasting power cooling when it's cool enough outside for the house to cool on its own
    // 3. Avoids wasting power heating when it's hot enough outside or through house's inertia (which is related to outside's temp and atm pressure anyway)
    // 4. In simpler terms: we don't want cooling operation in the winter or heating in the summer. 

    atomicState.waitAfterCoolConditionMet = atomicState.waitAfterCoolConditionMet == null ? false : atomicState.waitAfterCoolConditionMet
    atomicState.waitAfterHeatlConditionMet = atomicState.waitAfterHeatlConditionMet == null ? false : atomicState.waitAfterHeatlConditionMet

    if (need1 == "cool" && atomicState.lastNeed == "heat" && !atomicState.waitAfterCoolConditionMet) {
        atomicState.lastTimeCool = now() // keep track of time to avoid oscillations between cool / heat during shoulder season
        atomicState.waitAfterHeatlConditionMet = true
    }
    if (need1 == "heat" && atomicState.lastNeed == "cool" && !atomicState.waitAfterHeatlConditionMet) {
        atomicState.lastTimeHeat = now() // same idea
        atomicState.waitAfterCoolConditionMet = true
    }
    
    def need_to_wait = false
    try {
        need_to_wait = need_to_wait_between_modes(need1, inside, outside, target)
    }
    catch (Exception e) {
        log.error "need_to_wait => $e"
    }


    try {
        if (need1 == "heat" && atomicState.userWantsWarmer) {
            if (enablewarning) if (enablewarning) log.warn "user wants a warmer room, shoulder season timed override ignored. Switching to heating mode"
        }
        else if (need_to_wait) {
            if (enablewarning) if (enablewarning) log.warn "last cooling request was too close to switch to heating mode now"
            need0 = "off"
            need1 = "off"
        }


        if (need1 == "cool" && atomicState.userWantsCooler) {
            if (enablewarning) if (enablewarning) log.warn "user wants a cooler room, shoulder season timed override ignored. Switching to cooling mode"
        }
        else if (need_to_wait) {
            if (enablewarning) if (enablewarning) log.warn "last heating request was too close to switch to cooling mode now"
            need0 = "off"
            need1 = "off"
        }
    }
    catch (Exception e) {
        log.error "need_to_wait eval shoulder season test => $e"
    }

    need = [need0, need1]

    /**************** END AND DEBUG *************************/


    try {
        atomicState.lastNeed = need1 == "off" ? atomicState.lastNeed : need1  // need1 should always be "off" when need_to_wait_between_modes == true, so no need to verify this here. 
        atomicState.need1 = need1 // memoization for other methods that can't pass needed parameters for get_need to work

        if (enableinfo) log.info "atomicState.lastNeed = $atomicState.lastNeed"
        if (enabletrace) log.trace  "need: $need1 | target: $target | outside: $outside | inside: $inside | inside hum: ${get_inside_humidity()} | outside hum ${get_outside_humidity()} | Open Contacts:$doorsContactsAreOpen  65fhjk45"

        if (enabledebug) {
            message = [
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
                "<br> atomicState.userWantsCooler = $atomicState.userWantsCooler ",
                "<br> inWindowsModes = $inWindowsModes ",
                "<br> power saving management= ${powersavingmode ? "$powersavingmode INpwSavingMode = $INpwSavingMode":"option not selected by user"} ",
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
                "<br> contactClosed = $contactClosed  ",
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
        log.trace "current therm. modes: ${thermModes} need_to_wait: $need_to_wait | target: $target | inside: $inside | swing=$swing | outside: $outside | need: $need1 | contacts open? ${contactsAreOpen()} (${contactsAreOpen() ? "${ atomicState.listOfOpenContacts } " : ""}) | userWantsCooler ? ${atomicState.userWantsCooler} | userWantsWarmer ? ${atomicState.userWantsWarmer}"
    }
    catch (Exception e) {
        log.error "get_need formated debug => $e"
    }

    return need
}
def get_needed_thermosats(need){

    // atomicState.neededThermostats = []
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

    log.trace "get_needed_thermosats returns: $result"
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
    def deltaHours = 72 * 3600 * 1000

    atomicState.disabledSensors = []

    if (sensor) {

        def sum = 0
        int i = 0
        int s = sensor.size()
        int substract = 0 // value to substract to s when a device is to be ignored
        for (i = 0; i < s; i++) {
            def device = sensor[i]
            def val = device?.currentValue("temperature")

            if (enabledebug) log.debug "--${device} returns temperature: $val device id: ${device.id}"
            if (val == null) {
                s -= 1
                if (enablewarning) log.warn "${device} did not return any temperature: ${val} device id: ${device.id}"
                continue
            }
            sum += val

            // check sensors responsiveness
            events = device.eventsSince(new Date(now() - deltaHours)).size() // collect all events within 72 hours
            if (enabledebug) log.debug "$device has returned $events events in the last 72 hours"
            if (events == 0) {
                if (enablewarning) if (enablewarning) log.warn format_text("$device hasn't returned any event in the last 72 hours!", "white", "red")
                if (sensor.size() > 1) {
                    if (atomicState.disabledSensors.contains("$device")) {
                        //already in, do nothing 
                        if (enablewarning) log.warn " INPUT ALREADY DONE --------"
                    }
                    else {
                        atomicState.disabledSensors += "$device"
                    }
                    if (enabletrace) log.trace "$device is being ignored because it is unresponsive."
                    sum -= val

                }
                substract += 1
            }
            else if (atomicState.disabledSensors.any{ it -> it == device.displayName })
            {
                if (enablewarning) if (enablewarning) log.warn "deleting $device from disabledSensors because it is active again"
                atomicState.disabledSensors -= "$device"
            }
            if (device.hasCapability("Battery")) {
                def batteryValue = device.currentValue("battery")
                def batThreshold = lowBatLevel ? lowBatLevel : 40
                if (batteryValue <= batThreshold && batteryValue > 0) // batteryValue > 0 is because some cheap devices can return negative values when they're powered by AC current from and HVAC / PTAC
                {
                    if (enablewarning) if (enablewarning) log.warn format_text("WARNING! ${device}'s BATTERY LEVEL IS LOW (${batteryValue}%)", "white", "red")
                    atomicState.lowBatterySensor = true
                }
                else {
                    atomicState.lowBatterySensor = false
                }
            }
        }

        if (atomicState.disabledSensors.size() != 0) {
            if (enableinfo) log.info "disabledSensors size = ${atomicState.disabledSensors.size()}"
            def a = atomicState.disabledSensors
            if (enablewarning) if (enablewarning) log.warn "SOME SENSORS FAILED: ${a.join(', ')}"
        }


        if (sum == 0) {
            atomicState.paused = true
            if (enablewarning) if (enablewarning) log.warn "SUM = 0 !!! ALL ALTERNATE SENSORS ARE UNRESPONSIVE! Setting thermostat to auto 5erg4z"

            set_multiple_thermostats_mode("auto", "get_inside_temperature all sensors are dead", null)

            atomicState.failedSensors = true // force the user to attend the fact that all sensors are unrresponsive

            log.error "All temperature sensors have become unresponsive! User intervention is required."
            return 0
        }
        else {
            atomicState.failedSensors = false
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

    inside = inside.toDouble()
    inside = inside.round(2)
    atomicState.inside = inside

    if (enabletrace) log.trace  "measured ${sensor && sensor.size() > 1 ? "temperatures are" : "is"}: ${sensor ? "${ sensor.join(", ") } ${ sensor.collect{ it.currentValue("temperature") }.join("°F, ") }°F" : inside}"

    return inside
}
def get_outside_temperature(){
    return outsideTemp.currentValue("temperature")
}
def get_outside_threshold(){

    // define the outside temperature as of which heating or cooling are respectively required 
    // modulated with outside humidity 

    // def outside = outsideTemp?.currentValue("temperature")

    return get_humidity_threshold()
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
            if (enablewarning) if (enablewarning) log.warn format_text("$optionalHumSensor does not support humidity (beware of generic drivers!). - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
            result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity")
            if (result != null) {
                if (enablewarning) if (enablewarning) log.warn format_text("This app is using ${thermostat?.currentValue("humidity") != null ? "$thermostat" : "$outsideTemp"} as a default humidity sensor in the mean time", "black", "red")
            }
            result = result == null ? 50 : result // temporary value as last resort
        }
    }
    if (enabledebug) log.debug "Inside humidity is ${result}%"
    return result
}
def get_outside_humidity(){
    return outsideTemp.hasCapability("RelativeHumidityMeasurement") ? outsideTemp.currentValue("humidity") : get_inside_humidity()
}
def get_humidity_threshold(){

    def humidity = get_inside_humidity() //outsideTemp?.currentValue("humidity") //NB: get_inside_humidity() will use outside humidity if and only if inside sensor is not returning values for some reason
    humidity = humidity != null ? humidity : celsius ? get_celsius(50) : 50 // prevents error from recently installed thermostats
    if (humidity == null) {
        def message = "$outsideTemp is not returning any humdity value - it may be because it was just included; if so, this will resolve ont its own. If this message still shows within an hour, check your thermostat configuration..."
        if (enablewarning) if (enablewarning) log.warn format_text(message, red, white)
    }
    // the higher the humidity, the lower the threshold so cooling can happen 
    def y = null // value to find
    def x = humidity ? humidity : 50 // 50 if no data returned to allow the app to run
    def ya = celsius ? get_celsius(58) : 58 //celsius ? get_celsius(60):60 // coressponding outside temperature value for when humidity = xa 
    def xa = 90 // humidity level
    def m = atomicState.lastNeed == "cool" ? 0.1 : -0.1 // slope / coef

    y = m * (x - xa) + ya // solving y-ya = m*(x-xa)
    if (enablewarning) log.warn "y = $y"
    def lo = celsius ? get_celsius(65) : 65 // it's ok to run the heat if humidity is high while temp below 65 outside
    def hi = celsius ? get_celsius(72) : 72 // not ok to run the heat if temp ouside is above 72
    def result = y > hi ? hi : (y < lo ? lo : y) // max and min //  

    if (enableinfo) log.info "humidity-related temperature threshold (cool/heat decision or/and windows decision) is ${y != result ? "$result(corrected from y = $y)" : "$result"} (humidity being ${humidity < 40 ? "low at ${ humidity }% " : "high at ${ humidity }% "})"

    return result
}
def get_last_motion_event(Dtime, testType){

    int events = 0

    /* ############################### IF ANY ACTIVE, THEN NO NEED FOR COLLECTION ############################### */

    //this is faster to check if a sensor is still active than to collect past events
    if (motionSensors.any{ it -> it.currentValue("motion") == "active" })
    {
        if (enabledebug) log.debug "Sensor still active: ${motionSensors.findAll{it.currentValue('motion') == 'active'}}"
        events = 10 // this is not a boolean method but called through the definition of a boolean variable in Active() scope, so it must return a positive integer value if motion is active. 
        if (testType == "motionTest") if (enableinfo) log.info "$atomicState.activeMotionCount active events in the last ${Dtime/1000/60} minutes (currently active)"
        return events // so we must return a number equal or greater than 1
    }

    /******************************COLLECTION  O(n)!!!**********************************************************/
    collection = motionSensors.collect{ it.eventsSince(new Date(now() - Dtime)).findAll{ it.value == "active" } }.flatten()
    events = collection.size()
    /* ########################################################################################################## */

    if (testType == "motionTest") if (enabletrace) log.trace  "$events active events collected within the last ${Dtime/1000/60} minutes (eventsSince)"

    // eventsSince() can be messy 

    if (events < atomicState.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        events = atomicState.activeMotionCount
    }
    if (events > atomicState.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        atomicState.activeMotionCount = events
    }

    if (enableinfo) log.info "$events ${testType == "motionTest" ? "($atomicState.activeMotionCount)":""} active events in the last ${Dtime/1000/60} minutes ($testType)"
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
    if (enabletrace) log.trace "hashTable size: ${hashTable.size()}"  // Modified to use the newly read hashTabl

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

        if (enabletrace) log.trace "${learnedTarget == null ? 'FAILED to retrieve any value despite adding one... investigate.' : 'Successfully added and retrieved: ' + learnedTarget}"

        if (enabledebug) log.debug hashTable  // Modified to use the newly read hashTabl
        if (enablewarning) if (enablewarning) log.warn "hashTable size: ${hashTable.size()}"  // Modified to use the newly read hashTab
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

/* ############################### DECISIONS ############################### */

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
                        log.error "object class instance error for ${therm.displayName}"
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
        def need = atomicState.lastNeed // can't call get_need() from here because set_multiple_thermostats_fan_mode is called by it: infinite callback loop

        for (therm in get_needed_thermosats(need)) {
            therm.setThermostatFanMode(fanMode)
        }

    }
    else {
        thermostat.setThermostatFanMode(fanMode)
    }
}
def set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, origin){
    if (differentiateThermostatsHeatCool) {
        def simpleModeActive = simpleModeIsActive()     
        def need = get_need(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, neededThermostats, thermModes, "set_target")   
        def neededThermostats = get_needed_thermosats(need)

        for (int i = 0; i < neededThermostats.size(); i++)
        {
            set_thermostat_target(neededThermostats[i], cmd, target, origin)
        }
    }
    else {
        set_thermostat_target(thermostat, cmd, target, origin)
    }
}
def set_thermostat_mode(t, mode, origin){
    if (enablewarning) log.warn "set_thermostat_mode called from $origin"
    try {
        if (t.currentValue("thermostatMode") != mode || origin in ["checkthermstate force command", "remainsOffCmd"]) {
            if (enabletrace) log.trace  "$t set to $mode (origin: $origin)"
            if (autoOverride && t.currentValue("thermostatMode") == "auto") {
                if (enablewarning) log.warn "$t is in auto mode - command to set to $mode ignored..."
            }
            else {
                t.setThermostatMode(mode)
            }
        }
        else {
            /* if (enabletrace) */ log.trace  "$t already set to $mode (set_thermostat_mode origin: $origin)"
        }
    }
    catch (Exception e) {
        if (enablewarning) log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}
def set_thermostat_target_ignore_setpoint(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, origin){

    boolean inpowerSavingMode = location.mode in powersavingmode
    boolean ignore = checkIgnoreTarget() 

    def debug = [
        "checkIgnoreTarget: $ignore",
        "cmd: $cmd",
        "exceptForThermostatCool: $exceptForThermostatCool",
        "exceptForThermostatHeat: $exceptForThermostatHeat",
        "inpowerSavingMode: $inpowerSavingMode"
    ]

    if (enabledebug) log.debug debug_from_list(debug)

    if (ignore && !inpowerSavingMode && !exceptForThermostatCool && !exceptForThermostatHeat) {
        if (enabletrace) log.trace  "Target ($target) temp not sent to $thermostat at user's request"
    }
    else if ((exceptForThermostatCool || exceptForThermostatHeat) && !inpowerSavingMode) {
        if (exceptForThermostatHeat) {
            set_thermostat_target(thermostatHeat, cmd, target, origin)
        }
        else if (exceptForThermostatCool) {
            set_thermostat_target(thermostatCool, cmd, target, origin)
        }
    }
    else {
        set_target(cmd, target, inside, outside, motionActive, doorsContactsAreOpen, thermModes, "dsfszd")
    }
}
def set_thermostat_target(t, cmd, target, origin){
    try {
        def query = cmd == "setCoolingSetpoint" ? "coolingSetpoint" : cmd == "setHeatingSetpoint" ? "heatingSetpoint" : "thermostatSetpoint"
        if (t.currentValue(query) != target || origin == "checkthermstate force command") {
            if (enabletrace) log.trace  "$t set to $target (origin: $origin)"
            t."${cmd}"(target)
        }
        else {
            if (enabletrace) log.trace  "$t already set to $target (set_thermostat_target origin: $origin)"
        }
    }
    catch (Exception e) {
        if (enablewarning) log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}
def turn_off_thermostats(need, inside, thermModes) {

    if (enablewarning) log.warn "--                                                         turn_off_thermostats                                                         --"


    if ((atomicState.lastNeed == "heat" && inside < criticalcold) || (atomicState.lastNeed == "cool" && inside > criticalhot)) {
        if (enablewarning) log.warn "not turning off thermostat because inside temp is ${inside < criticalcold ? 'too low' : 'too hot'}"
        return
    }
    else {

        log.debug "1"    

        boolean contactsOpen = contactsAreOpen()
        boolean timeIsup = check_contacts_delay() // manages time delay for when some contacts, if any, have been opened
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
        else if (!ignoreMode || (contactsOpen && timeIsup) || !Active() || offrequiredbyuser) {
            log.info "Turning off thermostats"

            set_multiple_thermostats_mode("off", "turn_off_thermostats(${need}, ${inside})", null)

        }
    }
}

/* ############################### A.I. LEARNING (beta 2 October 2023) ############################### */

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
    } catch (e) {
        log.error "Error creating file $fName: ${e}"
        return false;
    }
}
def call_create_file(){
    if (app.label.contains("Elfege")) {
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
        catch (e) {
            log.error "Error attempting to create a file: $e";
        }
    }
}
def readFromFile(fileName) {
    def host = "localhost"  // or "127.0.0.1"
    def port = "8080"  
    def path = "/local/" + fileName
    def uri = "http://" + host + ":" + port + path

    if (enabletrace) log.trace "HTTP GET URI ====> $uri"

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

    // if(enabletrace) log.trace "deserializeHashTable ====> $jsonString"

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
    // Here, you can implement a simplified PMV calculation
    // using the ASHRAE Standard 55 or ISO 7730 guidelines.
    // This calculation can be more complex and depends on
    // factors like clothing insulation, metabolic rate, etc.

    // For simplicity, we'll return a constant value for now.

    // Calculate a comfort score based on temperature and humidity.
    // Adjust the formula based on your specific comfort model.
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

    if (atomicState.currentUnit == "fahrenheit") {
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
        catch (e) {
            if (enablewarning) log.warn"hastable not computable at the moment... "
        }

        // Serialize and write the updated hash table back to the HTTP server
        def newHashTableJson = serializeHashTable(hashTable)
        writeToFile("hash_table.txt", newHashTableJson)

        atomicState.currentUnit = "celsius"

        if (enablewarning) log.warn "Database has been converted to Celsius."
    }
    else {
        if (enablewarning) log.warn "database already in celcius"
    }
}
def convert_db_to_fahrenheit() {

    if (atomicState.currentUnit == "celcius") {

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

        atomicState.currentUnit = "celsius"
    }
    else {
        if (enablewarning) log.warn "database already in fahrenheit"
    }
}

/* ############################### BOOLEANS ###############################****** */
boolean operatingStateOk(contactClosed, doorsContactsAreOpen, currentOperatingState, currentOperatingNeed){

    def state = true
    log.debug "1"
    try {

        if (enablewarning) if (enablewarning) log.warn "Main thermostat = $thermostat"
        if (enabledebug) log.debug "currentOperatingNeed = $currentOperatingNeed && need = $need |thermostat?.currentValue('thermostatOperatingState') = ${thermostat.currentValue('thermostatOperatingState')}"
        if (enabledebug) log.debug "${thermostat?.currentValue('thermostatOperatingState') == currentOperatingNeed}"

        atomicState.lastSetTime = atomicState.lastSetTime != null ? atomicState.lastSetTime : now() + 31 * 60 * 1000



        currentOperatingState = thermostat.currentValue("thermostatOperatingState")

        if (differentiateThermostatsHeatCool) {
            if (enablewarning) if (enablewarning) log.warn "neededThermostats: ${neededThermostats?.join(', ')}"



            currentOperatingStates = neededThermostats.collect { it.currentValue("thermostatOperatingState") }
            allOk = !currentOperatingStates.any { it -> it != currentOperatingNeed || it == "fanCirculate" }
            if (enabledebug) log.debug "contactClosed && !doorsContactsAreOpen => ${contactClosed && !doorsContactsAreOpen}"

            state = contactClosed && !doorsContactsAreOpen ? allOk : true

            if (enabletrace || !state) {
                log.debug "currentOperatingNeed => $currentOperatingNeed"
                if (enablewarning) log.warn "currentOperatingStates => $currentOperatingStates"
                if (enablewarning) log.warn "allOk => $allOk"
                if (enablewarning) log.warn "state => $state"
            }


        }
        else {

            log.debug("contactClosed => ${contactClosed}")
            log.debug("doorsContactsAreOpen => ${doorsContactsAreOpen}")
            log.debug("currentOperatingState => ${currentOperatingState}")
            log.debug("currentOperatingNeed => ${currentOperatingNeed}")
            state = contactClosed && !doorsContactsAreOpen ? currentOperatingState in [currentOperatingNeed, "fanCirculate"] : true



        }

    } catch (Exception e) {
        log.error "operatingStateOk() => $e"
    }

    return state

}
boolean simpleModeIsActive(){
    atomicState.lastButtonEvent = atomicState.lastButtonEvent != null ? atomicState.lastButtonEvent : now()
    boolean result = atomicState.lastResultWasTrue
    //boolean doorOpen = doorsOpen() // FEEDBACK LOOP since doorsOpen() function calls simpleModeIsActive()
    boolean currentlyClosed = false

    if (!UseSimpleMode) {
        return false
    }
    if (UseSimpleMode) {
        result = atomicState.buttonPushed
    }
    if (UseSimpleMode && simpleModeTimeLimit && atomicState.buttonPushed) // if user set a time limit
    {     
        def remainTime = get_thermostat_that_must_remain_off(simpleModeTimeLimit, atomicState.lastButtonEvent)
        def message = "$simpleModeName Mode - remaining time: ${remainTime}"
        if (enableinfo) log.info format_text(message, "white", "grey")

        if (remainTime <= 0) // time is up
        {
            result = false
            atomicState.buttonPushed = false
        }
    }

    if (enabledebug) log.debug "$simpleModeName Mode trigger boolean returns $result"

    return result
}
boolean contactsAreOpen(){
    if (WindowsContact) {
        boolean Open = WindowsContact?.any{ it -> it.currentValue("contact") == "open" }      
        def listOfOpenContacts = []
        listOfOpenContacts = WindowsContact?.findAll{ it.currentValue("contact") == "open" }
        atomicState.listOfOpenContacts = listOfOpenContacts.join(", ")
        if (atomicState.listOfOpenContacts.size() > 0) {
            if (enabletrace) log.trace "Contacts are open: ${listOfOpenContacts}"
        }
        return Open
    }
    else {
        return false
    }
}
boolean doorsOpen(){
    boolean Open = false
    def listOpen = []

    if (doorsContacts) {
        listOpen = doorsContacts?.findAll{ it?.currentValue("contact") == "open" }
        Open = doorsContacts?.any{ it -> it.currentValue("contact") == "open" }
    }
    if (Open && !contactsOverrideSimpleMode && simpleModeIsActive()) {
        if (enableinfo) log.info "$doorsContacts open but $simpleModeContact is closed and user doesn't wish to override"
        return false
    }

    if (enableinfo) log.info "------------------ doors: $doorsContacts open ?: ${listOpen.join(', ')}"
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
                        atomicState.lowBattery = true
                        def m = "LOW BATTERY: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                        if (devicesWithLowBattery.size() == motionSensors.size()) {
                            if (enablewarning) log.warn format_text(m, "white", "red")
                            m = "All motion sensors' batteries are DEAD! Motion test returns true as a safety measure. Make sure to replace the battery of the following devices: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                            if (enablewarning) if (enablewarning) log.warn format_text(m, "white", "grey")
                            return true
                        }
                        else {
                            if (enablewarning) if (enablewarning) log.warn format_text(m, "white", "red")
                        }
                    }
                    else {
                        atomicState.lowBattery = false
                    }
                }

                result = get_last_motion_event(Dtime, "motionTest") > 0

            }
            else {
                if (enabletrace) log.trace "motion returns true because outside of motion modes"
            }

            // this must happen outside of get_last_motion_event() collection, because the latter isn't called when outside of motion modes.  
            atomicState.activeMotionCount = atomicState.activeMotionCount ? atomicState.activeMotionCount : 0

            // if(enabledebug) log.debug "now() - atomicState.lastMotionEvent > 1000 => ${(now() - atomicState.lastMotionEvent) > 1000}"
            if ((now() - atomicState.lastMotionEvent) > Dtime && atomicState.activeMotionCount != 0) // if time is up, reset atomicState events value
            {

                atomicState.activeMotionCount = 0 // time is up, reset this variable
                // if(enabledebug) log.debug "atomicState.activeMotionCount set to $atomicState.activeMotionCount"
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
boolean check_contacts_delay(){
    // this function is mostly meant to provide delay between open contact event and turn off
    atomicState.lastContactOpenEvt = atomicState.lastContactOpenEvt ? atomicState.lastContactOpenEvt : now()
    def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0

    boolean result = true

    if (enablewarning) log.warn "-- check_contacts_delay --"
    if (contactsAreOpen()) {
        if (fanCirculateAlways || (alwaysButNotWhenPowerSaving && Active() && !inpowerSavingMode)) {
            result = false
        }
        if (now() - atomicState.lastContactOpenEvt > delayB4TurningOffThermostat) {
            log.trace "check_contacts_delay() returning true zgf45"
            result = true
        }
        else {
            if (enabletrace) log.trace  "contacts are open, thermostat will be set to off in ${(delayB4TurningOffThermostat/1000)-((now() - atomicState.lastContactOpenEvt)/1000)} seconds"
            result = false
        }
    }
    else {
        if (enablewarning) log.warn "ignoreMode = $ignoreMode"
        if (ignoreMode && Active() && !offrequiredbyuser) return false;
        if (enableinfo) log.info "check_contacts_delay() returning true 5gr4"
        return true // if contacts are closed, any other request to turn off the AC coming from this app must be granted
    }

    log.info "check_contacts_delay returns $result"
    return result
}
boolean checkIgnoreTarget(){
    def result = simpleModeIsActive() && doNotIgnoreTargetInSimpleMode ? false : ignoreTarget
    return result
}
boolean need_to_wait_between_modes(String need, double inside, double outside, double target) {
    if (enabledebug) {
        log.debug "inside: $inside, target: $target, outside: $outside, lastTimeHeat: $atomicState.lastTimeHeat, lastNeed: $atomicState.lastNeed, need: $need"
    }
    if (enabledebug) {
        log.debug "inside: $inside, target: $target, outside: $outside, lastTimeHeat: $atomicState.lastTimeHeat, lastNeed: $atomicState.lastNeed, need: $need"
    }
    
        int variant = 6
        boolean hotInHere = inside >= target + variant
        boolean coldInHere = inside <= target - variant
    
        int outsideThreshold = get_outside_threshold().toInteger()
    log.trace "outsideThreshold: $outsideThreshold"


    // prevent AC from running in winter
    int winterThreshold = outsideThreshold - 10; // threshold for winter
    if (need == "cool" && outside < winterThreshold && !hotInHere) {
        return true; // Don't run AC in winter
    }
    // prevent Heat from running in summer
    int summerThreshold = outsideThreshold; // threshold for summer
    if (need == "heat" && outside > summerThreshold && !coldInHere) {
        return true; // Don't run AC in summer
    }

    return false
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
    if (enablewarning) log.warn "STOP STOP STOP STOP ++++++++++++++++++++++++++++++customCommand = $customCommand"

    def cmd = customCommand ? customCommand.minus("()") : "off"

    if (differentDuration) {

        def dev = settings["windows"].find{ it.name == data.device }
        if (enabletrace) log.trace "differentiated scheduled STOP for ${dev}"

        dev."${cmd}"()
    }
    else {

        int s = windows.size()
        int i = 0
        for (s != 0; i < s; i++) {
            windows[i]."${cmd}"()
            if (enablewarning) if (enablewarning) log.warn "${windows[i]} $customCommand"
        }

    }

}

def Poll(){

    if (location.mode in restricted) {
        if (enableinfo) log.info "location in restricted mode, doing nothing"
        return
    }
    if (!polldevices) return

    if (atomicState.paused == true) {
        return
    }

    def neededThermostats = get_needed_thermosats(atomicState.lastNeed)

    if (enabletrace) log.trace "POLLING THERMOSTATS"

    boolean override = atomicState.override   
    boolean outsidePoll = outsideTemp.hasCommand("poll")
    boolean outsideRefresh = outsideTemp.hasCommand("refresh")

    if (differentiateThermostatsHeatCool) {

        boolean thermPoll = thermostat.hasCommand("poll")
        boolean thermRefresh = thermostat.hasCommand("refresh")

        for (t in neededThermostats) {
            if (enabledebug) log.debug "t?.displayName => ${t?.displayName}"
            try {
                if (enabletrace) log.trace "REFRESHING "
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
                    if (enabletrace) log.trace "REFRESHING $s"
                    s.cmd
                }
                catch (Exception e) {
                    try {
                        if (enabletrace) log.trace "POLLING $s"
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
        if (enabletrace) log.trace "POLLING WINDOWS"
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

    atomicState.polls = atomicState.polls == null ? 1 : atomicState.polls + 1
    atomicState.lastPoll = atomicState.lastPoll ? atomicState.lastPoll : now()
    if ((now() - atomicState.lastPoll) > 1000 * 60 * 60) atomicState.polls = 0

    if (enabletrace) log.trace "polling power meters. $atomicState.polls occurences in the last hour..."
    // if(atomicState.polls > 50)
    // {
        //  if(enablewarning) log.warn "too many polling requests within the last hour. Not polling, not refreshing..."
    //     return
    // }

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
    atomicState.lastPoll = now()
}
def disable_logging(){
    if (enablewarning) log.warn "log.debug disabled..."
    app.updateSetting("enabledebug", [type: "bool", value: "false"])
}
def disable_description(){
    if (enablewarning) log.warn "description text disabled..."
    app.updateSetting("enableinfo", [type: "bool", value: "false"])
}
def disable_warnings(){
    if (enablewarning) log.warn "warnings disabled..."
    app.updateSetting("enablewarning", [type: "bool", value: "false"])
}
def disable_trace(){
    if (enablewarning) log.warn "trace disabled..."
    app.updateSetting("enabletrace", [type: "bool", value: "false"])
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