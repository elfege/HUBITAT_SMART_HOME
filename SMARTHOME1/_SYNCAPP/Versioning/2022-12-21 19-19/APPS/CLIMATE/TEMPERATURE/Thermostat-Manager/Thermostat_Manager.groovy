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
/************************************************SETTINGS******************************************************/
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
        name:       "MainPage",
        title:      "${app.label}",
        nextPage:   null,
        install: true,
        uninstall: true
    ]

    dynamicPage(pageProperties) {

        section() {
            if(simpleModeName == null) app.updateSetting("simpleModeName", [type:"text", value:"Sleep"])

            //label title: "Assign a name",description:"$atomicState.appLabel", required: false, submitOnChange:true // can't use this because it shows html font tags
            input "appLabel", "text", title: "Assign a name to this instance of $app.name", submitOnChange:true
            app.updateLabel(appLabel)
            input "celsius", "bool", title: "Celsius"
        }
        section()
        {
            input "pause", "button", title: "$atomicState.button_name"
            input "buttonPause", "capability.doubleTapableButton", title: "Pause/resume this app when I double tap a button", multiple: true, required: false, submitOnChange:true

            if(buttonPause)
            {
                input "buttonTimer", "number", title: "optional: time limit in minutes", required:false
            }
            input "restricted", "mode", title: "Restricted modes", multiple: true
        }
        section("Main Settings") {

            href "thermostats", title: "Thermostats and temperature sensors", description: ""

            if(thermostat && outsideTemp)
            {
                href "methods", title: "Methods of evaluation", description: ""
                href "contactsensors", title: "Contacts, Windows And Doors", description: ""
                href "powersaving", title: "Motion and power saving", description: ""           
                href "windowsManagement", title: "Windows Control", description: ""
                href "comfortSettings", title: "$simpleModeName Mode", description: ""
                href "fanCirculation", title: "Air circulation", description:""
                href "virtualThermostat", title: "Manage an extra electric heater and/or cooler", description: ""
                href "operationConsistency", title: "Fail safe", description: ""

            }
            else
            {
                paragraph formatText("You need to select a thermostat and a weather data source before you can access more settings.", "white", "blue")
            }

        }
        section("Actions")
        {
            input "run", "button", title: "RUN"
            input "update", "button", title: "UPDATE"
            input "poll", "button", title: "REFRESH DEVICES"
            input "polldevices", "bool", title: "Poll devices"

            input "enabledebug", "bool", title: "Debug logs", submitOnChange:true
            input "tracedebug", "bool", title: "Trace logs", submitOnChange:true
            input "description", "bool", title: "Description Text", submitOnChange:true

            if(enabledebug)
            {
                logging "debug enabled"      
                atomicState.EnableDebugTime = now()
                runIn(1800,disablelogging)

                descriptionText "debug will be disabled in 30 minutes"
            }
            else 
            {
                logging "debug disabled"
            }

            if(description) { 
                atomicState.enableDescriptionTime = now() 
                runIn(86400, disabledescription)
            }

        }
    }
}
def thermostats(){

    def title = formatText("Thermostats, sensors, heaters and coolers", "white", "grey")

    def pageProperties = [
        name:       "thermostats",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {
        section("Select the thermostat you want to control")
        { 
            input "thermostat", "capability.thermostat", title: "select a thermostat", required: true, multiple: false, description: null, submitOnChange:true
            input "ignoreTarget", "bool", title:"Optional: Do not send any target command to the main thermostat (only heat/cool commands will be sent)", defaultValue:false, submitOnChange:true
            input "ignoreMode", "bool", title:"Optional: Do not send any cool/heat command to the main thermostat (only set points commands will be sent)", defaultValue:false, submitOnChange:true
            if(ignoreTarget)
            {
                app.updateSetting("offrequiredbyuser", [type:"bool", value:true]) // needs to be true when ignoreTarget is enabled  
                app.updateSetting("ignoreMode", [type:"bool", value:false])// can't have both
            }
            if(ignoreMode){
                app.updateSetting("ignoreTarget", [type:"bool", value:false])// can't have both

            } 

            input "differentiateThermostatsHeatCool", "bool", title: "Use 2 different thermostats: 1 for cooling, 1 for heating", submitOnChange:true, defaultValue:false
            if(differentiateThermostatsHeatCool)
            {
                input "thermostatHeat", "capability.thermostat", title: "Select a thermostat used exclusively for heating", required:true
                input"thermostatCool", "capability.thermostat", title: "Select a thermostat used exclusively for cooling", required: true
            }


            // verify not only capability, but also actual reading, some thermostats working with generic drivers
            // will return true to some capabilities while the hardware won't parse any value
            boolean hasHumidity = thermostat != null && thermostat.hasCapability("RelativeHumidityMeasurement") && thermostat.currentValue("humidity") != null
            log.debug "$thermostat has humidity capability ? $hasHumidity"
            if(thermostat && !hasHumidity && !optionalHumSensor)
            {
                paragraph formatText("""Your thermostat doesn't support humidity measurement (or doesn't return any humidity value). As a consequence, you must select a separate humidity sensor""", "white", "blue")
            }
            if(thermostat || !hasHumidity)
            {
                input "optionalHumSensor", "capability.relativeHumidityMeasurement", title: "${!hasHumidity ? "S":"Optional: s"}elect a humidity sensor", required:false, submitOnChange:true
            }

            if(restricted)
            {
                input "restrictedThermMode", "enum", title:"Select default's thermostat operation once your location is in restricted mode", options:["off", "cool", "heat", "auto"], required:false, defaultValue: "off", submitOnChange:true
                if(restrictedThermMode == "auto")
                {
                    paragraph formatText("Beware that 'auto' is the override mode, which means that this app won't be able to control your thermostat until you set your thermostat back to either 'cool', 'heat' or 'off'", "white", "red")
                }
            }

        }    


        section("Sensors")
        {
            input "outsideTemp", "capability.temperatureMeasurement", title: "Required: select a weather sensor for outside temperature", required:true, submitOnChange:true

            input "sensor", "capability.temperatureMeasurement", title: "select a temperature sensor (optional)", submitOnChange:true, multiple:true
            if(sensor)
            {
                input "offrequiredbyuser", "bool", title: "Set thermostat's mode to 'off' when target temperature has been reached", defaultValue: false, submitOnChange:true
                atomicState.pageRefresh = atomicState.pageRefresh != null ? atomicState.pageRefresh : 0
                atomicState.pageRefresh += 1
                if(atomicState.pageRefresh > 2 && offrequiredbyuser)
                {
                    paragraph formatText("Thermostat off when target temp is reached is mandatory with 'do not sent target temperatures', which you have enabled in the sections above", "white", "red")
                    atomicState.pageRefresh = 0
                }
                atomicState.fanCirculateAlways = atomicState.fanCirculateAlways != null ? atomicState.fanCirculateAlways : false
                if(atomicState.fanCirculateAlways == true && (offrequiredbyuser || ignoreTarget))
                {
                    paragraph formatText("You have previously enabled the fan circulation option. It is not compatible with the thermostat's off mode so it has been disabled", "white", "red")
                    app.updateSetting("fanCirculateAlways",[type:"bool", value:false])  
                    app.updateSetting("alwaysButNotWhenPowerSaving", [type:"bool", value:false])
                    app.updateSetting("ignoreTarget", [type:"bool", value:true]) 
                }
            }
        }
    }
}
def methods(){

    def title = formatText("METHODS OF EVALUTATION:", "white", "grey")

    def pageProperties = [
        name:       "methods",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section(){
            input "autoOverride", "bool", title:"Pause this app when thermostat mode is 'auto'", submitOnChange: true, defaultValue: false
            if(autoOverride)
            {
                input "overrideDuration", "number", title: "Set a time limit", description: "number in hours, 0 means unlimitted time", submitOnChange:true
            }

            input "method", "enum", title:"select the method you want $app.name to use to adjust your thermostats cooling and heating set points", options:["normal","auto"],submitOnChange:true
            if(method == "auto")
            {
                paragraph formatText("auto method: the app sets your target temperature based on several learning functions by taking humidity levels and outside temperature into consideration", "black", "white")
            }
            else 
            {
                paragraph formatText("IMPORTANT: If you chose to not use a dimmer as an input source, you may have to repeat your input several times before the app 'understands' that this is a user input. There is no other way at the moment for the platform to distinguish the source of a command. It also greatly facilitates Alexa integration if you name your dimmer 'temperature [room name]'", "white", "black")
            }
            if(method == "auto")
            {    
                atomicState.converted = atomicState.converted ? atomicState.converted : false
                if(maxAutoHeat != "null" && minAutoHeat != "null" && minAutoCool != "null" && maxAutoCool != "null")
                {   
                    input "convertToCelsius", "bool", title: "Convert all these values to Celsius (if you forgot to select this option on the main page)", submitOnChange:true
                    if(convertToCelsius && !atomicState.converted)
                    {
                        atomicState.converted = true
                        atomicState.maxAutoHeatRestore = maxAutoHeat // backup to prevent loop down conversions
                        atomicState.minAutoHeatRestore = minAutoHeat
                        atomicState.minAutoCoolRestore = minAutoCool
                        atomicState.maxAutoCoolRestore = maxAutoCool

                        app.updateSetting("celsius", [type:"bool", value:true])

                        app.updateSetting("maxAutoHeat", [type:"number",value:getCelsius(maxAutoHeat.toInteger())])
                        app.updateSetting("minAutoHeat", [type:"number",value:getCelsius(minAutoHeat.toInteger())])
                        app.updateSetting("minAutoCool", [type:"number",value:getCelsius(minAutoCool.toInteger())])
                        app.updateSetting("maxAutoCool", [type:"number",value:getCelsius(maxAutoCool.toInteger())])
                    }
                    else if(!convertToCelsius && atomicState.converted)
                    {
                        atomicState.converted = false
                        log.debug """restoring values: 

atomicState.maxAutoHeatRestore = $atomicState.maxAutoHeatRestore
atomicState.minAutoHeatRestore = $atomicState.minAutoHeatRestore
atomicState.minAutoCoolRestore = $atomicState.minAutoCoolRestore
atomicState.maxAutoCoolRestore = $atomicState.maxAutoCoolRestore
"""
                        app.updateSetting("maxAutoHeat", [type:"number",value:atomicState.maxAutoHeatRestore])
                        app.updateSetting("minAutoHeat", [type:"number",value:atomicState.minAutoHeatRestore])
                        app.updateSetting("minAutoCool", [type:"number",value:atomicState.minAutoCoolRestore])
                        app.updateSetting("maxAutoCool", [type:"number",value:atomicState.maxAutoCoolRestore]) 
                    }
                    else if(convertToCelsius && atomicState.converted)
                    {
                        log.debug "already converted, doing nothing"   
                    }

                    log.debug "atomicState.converted = $atomicState.converted"
                }
                input "maxAutoHeat", "number", title: "Highest heating set point", defaultValue:celsius?getCelsius(78):78, submitOnChange:true
                input "minAutoHeat", "number", title: "Lowest heating set point", defaultValue:celsius?getCelsius(70):70, submitOnChange:true
                input "minAutoCool", "number", title: "Lowest cooling set point",  defaultValue:celsius?getCelsius(70):70, submitOnChange:true 
                input "maxAutoCool", "number", title: "Highest cooling set point", defaultValue:celsius?getCelsius(78):78, submitOnChange:true

            }

            input "dimmer", "capability.switchLevel", title: "Optional but HIGHLY recommended: Use a dimmer as a mean for this app to learn from your input", required: false, submitOnChange:true
        }
        section(){
            if(sensor)
            {
                input "manageThermDiscrepancy", "bool", title:"My thermosat needs to be boosted (for example, because it's too close to a window or to your HVAC", submitOnChange:true
                if(manageThermDiscrepancy)
                {
                    input "UserSwing", "double", title:"Input your thermostat's default swing/offset", defaultValue:0.5, submitOnChange:true
                    def text = """This is a setting made directly on your physical thermoat. If you need to cool at 73F, your thermostat will stop at 73 but not start again until 73.5 if your swing is set to 0.5. Generally this swing can be up to 2 or more degrees of variation. This input is necessary to allow this app to detect discrpancies and run emergency heating or cooling when, for example, your thermostat is located too close to a window or any other heat/cold source. Make sure you checked this setting on your thermostat directly (not accessible through device driver interface) and that it is set to your liking. See your thermostat documentation if you don't know how to modify this value."""
                    paragraph formatText(text, "white", "grey")
                }
            }
        }
    }
}
def closeBoolQuestions(){    
    //log.debug "closing bool questions"
    app.updateSetting("whyAdimmer",[type:"bool", value:false])
    app.updateSetting("tellMeMore",[type:"bool", value:false])
}
def contactsensors(){

    def title = formatText("CONTACTS AND DOORS", "white", "grey")

    def pageProperties = [
        name:       "contactsensors",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section()
        {
            input "WindowsContact", "capability.contactSensor", title: "Turn off everything when any of these contacts is open", multiple: true, required: false, submitOnChange:true            
            if(WindowsContact)
            {
                input "openDelay", "number", title: "After how long?", description: "Time in seconds", required:true
            }


            input "doorsManagement", "bool", title: "When some doors are open, synchronise $thermostat with a thermostat from another room", defaultValue:false, submitOnChange:true
            if(doorsManagement)
            {
                input "doorsContacts", "capability.contactSensor", title: "select contact sensors", required:true, multiple:true, submitOnChange:true

                input "doorThermostat", "capability.thermostat", title: "select a thermostat from a different room", required:true, submitOnChange:true
                if(doorsContacts && doorThermostat)
                {
                    paragraph "when ${doorsContacts?.size()>1?"any of":""} ${doorsContacts} ${doorsContacts?.size()>1?"are":"is"} open, $thermostat will synchornise with $doorThermostat"
                    if(motionSensors)
                    {
                        input "doorsOverrideMotion", "bool", title: "This option overrides motion based power savings", defaultValue:true, submitOnChange:true
                    }
                    if(UseSimpleMode)
                    {
                        input "contactsOverrideSimpleMode", "bool", title: "$doorsContacts events override $simpleModeName Mode (until they're closed again)"

                    }
                    if(doorsContacts)
                    {
                        input "useDifferentSetOfSensors", "bool", title: "Use a different set of temperature sensors when ${doorsContacts} ${doorsContacts.size()>1?"are":"is"} open", submitOnChange:true
                        if(useDifferentSetOfSensors)
                        {
                            input "doorSetOfSensors", "capability.temperatureMeasurement", title: "Select your sensors", multiple:true, submitOnChange:true, required:true
                        }
                        input "otherRoomHasCoolerPreference", "bool", title: "$doorThermostat never goes into cool mode due to alternate cooler preference", submitOnChange:true
                        if(otherRoomHasCoolerPreference)
                        {
                            input "otherRoomCooler", "capability.switch", title: "Select the switch being used by this other instance", required:true
                        }
                    }
                }
            }
        }
    }
}
def powersaving(){

    def title = formatText("POWER SAVING OPTIONS","white", "grey")

    def pageProperties = [
        name:       "powersaving",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section(formatText("Power saving modes", "white", "blue")){
            input "powersavingmode", "mode", title: "Save power when my home is in one of these modes", required: false, multiple: true, submitOnChange: true
        }
        section(formatText("Motion Management", "white", "blue")){
            input "motionSensors", "capability.motionSensor", title: "Save power when there's no motion", required: false, multiple: true, submitOnChange:true


            if(motionSensors)
            {
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true, submitOnChange:true

                if(motionmodes && motionmodes?.size() > 1)
                {
                    int s = motionmodes.size()
                    int i = 0
                    for(s != 0; i<s; i++)
                    {
                        def val = settings.find{it?.key == "noMotionTimeWithMode${motionmodes[i]}"}?.value?.toInteger() 
                        def valString = ""
                        def text = "Motion timeout (in minutes) when mode is ${motionmodes[i]}"
                        if(val != null)
                        {
                            float valHour = val/60
                            valString = val >= 60 ? "<font color = 'red'>(that's ${valHour > valHour.toInteger() ? valHour.round(2) : valHour.toInteger()} hours)</font>" : ""
                            text = "Motion timeout (in minutes) when mode is ${motionmodes[i]} ${valString}"
                        }
                        input "noMotionTimeWithMode${motionmodes[i]}", "number", title: text, description: "${valString}", submitOnChange:true  

                    }
                }
                else
                {
                    input "noMotionTime", "number", title: "Motion delay (in minutes):", description: "Time in minutes"
                }

                input "testMotionBattery", "bool", title: "Test motion sensors battery levels and ignore them if they're all too low", defaultValue:true, submitOnChange:true
                if(testMotionBattery)
                {
                    input "lowBatLevel", "number", title:"Indicate the low battery level threshold", defaultValue:40   
                }

            }  

        }
        section()
        {
            if(powersavingmode || motionSensors)
            {
                input "criticalcold", "number", title: "Set a minimum temperature when there is no motion", required: true
                input "criticalhot", "number", title: "Set a maximum temperature when there is no motion", required: true
            }
        }
    }
}
def comfortSettings(){

    def title = formatText("COMFORT SETTINGS","white", "grey")

    def pageProperties = [
        name:       "comfortSettings",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section(formatText("$simpleModeName: set this app to specific conditions for when, for example, you go to sleep, you're working out or anything else that requires unique settings...", "white", "blue")){

            input "simpleModeName", "text", title: "Rename this unique mode as you see fit", defaultValue: "Sleep", submitOnChange:true
            input "UseSimpleMode", "bool", title: "Use a button to trigger $simpleModeName mode", submitOnChange:true

            if(UseSimpleMode)
            {                
                def message = ""
                def devicesStr = ""

                def s = nightModeButton?.size() 
                def i = 0
                input "nightModeButton", "capability.holdableButton", title: "When ${!nightModeButton ? "this button is" : (s > 1 ? "these buttons are" : "this button is")} pushed, work in limited mode (push again to cancel)", multiple: true, required: false, submitOnChange:true

                if(nightModeButton)
                {
                    input "simpleModeSimplyIgnoresMotion", "bool", title:"Simply ignore motion rules and keep running other rules normally when I hit $nightModeButton", submitOnChange:true, defaultValue:false
                    input "lightSignal", "capability.switch", title:"Flash a light three times to confirm", required: false, submitOnChange:true

                    if(lightSignal)
                    {
                        input "turnSignalLightOffAfter", "bool", title:"turn off $lightSignal when done"

                        log.debug "$lightSignal capabilities : ${lightSignal.getCapabilities()}"
                        if(lightSignal.hasCapability("ColorControl"))
                        {
                            input "nightModeColor", "enum", title: "set the bulb to a specific color",options:["blue", "red", "green"]
                            input "setPreviousColor", "bool", title: "set the bulb back to its previous color after signal", defaultValue:true
                        }
                    }

                    for(s!=0;i<s;i++){
                        devicesStr = devicesStr.length() > 0 ? devicesStr + ", " + nightModeButton[i].toString() : nightModeButton[i].toString()
                    } 
                    input "simpleModeTimeLimit", "number", title: "Optional: return to normal operation after a certain amount of time", descripition: "Time in hours", submitOnChange:true
                    input "allowWindowsInSimpleMode", "bool", title:"Allow windows management, if any", defaultValue:false
                    if(simpleModeTimeLimit)
                    {
                        message = "Limited mode will be canceled after $simpleModeTimeLimit hours or after a new button event" //. Note that $devicesStr will not be able to cancel limited mode before time is out" 
                        paragraph formatText(message, "white", "grey") 
                    }
                    message = nightModeButton ? "$app.label will operate in limited mode when $devicesStr ${s > 1 ? "have" : "has"} been pushed and canceled when held, double tapped or pushed again. Power saving options will not be active" : ""
                    if(message) paragraph formatText(message, "white", "grey") //nightModeButton message

                    if(!simpleModeSimplyIgnoresMotion){
                        input "setSpecialTemp", "bool", title: "Keep room at a preset temperature when $nightModeButton is pressed", submitOnChange:true, defaultValue:false
                        input "specialSubstraction", "bool", title: "Lower the current set point instead?", submitOnChange:true


                        if(setSpecialTemp)
                        {
                            app.updateSetting("specialSubstraction",[type:"bool", value:false]) // foolproofing
                            input "specialTemp", "number", title: "Set the target temperature", required: true
                            input "specialDimmer", "capability.switchLevel", title:"Optional: Select a dimmer to adjust this spectific target temperature if needed", submitOnChange:true
                        }
                        if(specialSubstraction)
                        {
                            app.updateSetting("setSpecialTemp",[type:"bool", value:false]) // foolproofing
                            input "substract", "number", title: "Substract this value to the current set point", required:true 
                        }
                        if(ignoreTarget)
                        {   
                            paragraph formatText("You see the option below because you enabled the 'ignore set point targets' option in the thermostats section", "white", "grey")
                            input "doNotIgnoreTargetInNightMode", "bool", title:"When in $simpleModeName, don't ignore temperature targets, send those values to the thermostat instead", defaultValue:false, submitOnChange:true
                            if(doNotIgnoreTargetInNightMode)
                            {
                                input "dontSetThermModes", "bool", title:"Dont send any heat/cool command in $simpleModeName mode", submitOnChange:true, defaultValue:false
                            }
                            else if(dontSetThermModes) // if it was previously enabled and doNotIgnoreTargetInNightMode was just disabled, then make sure dontSetThermModes is now disabled too. foolproofing... 
                            {
                                app.updateSetting("dontSetThermModes", [type:"bool", value:false])
                            }
                        }
                        if(offrequiredbyuser && doNotIgnoreTargetInNightMode)
                        {
                            input "dontTurnOffinNightMode", "bool", title:"Don't turn off my thermostat in $simpleModeName mode", submitOnChange:true, defaultValue:true  
                        }
                    }

                    if(!doNotIgnoreTargetInNightMode) app.updateSetting("dontTurnOffinNightMode", [type:"bool", value:"false"])
                }
            }
            else
            {
                if(nightModeButton) app.updateSetting("nightModeButton", [type:"capability", value:null])
                app.updateSetting("simpleModeTimeLimit", [type:"number", value:null])
                app.updateSetting("allowWindowsInSimpleMode", [type:"bool", value:false])
                app.updateSetting("setSpecialTemp", [type:"bool", value:false])
                app.updateSetting("specialSubstraction", [type:"bool", value:false])
                app.updateSetting("substract", [type:"number", value:null])
                app.updateSetting("specialTemp", [type:"number", value:null])
                app.updateSetting("doNotIgnoreTargetInNightMode", [type:"bool", value:false])
                app.updateSetting("dontSetThermModes", [type:"bool", value:false])
                app.updateSetting("dontTurnOffinNightMode", [type:"bool", value:false])
            }
        }
    }
}
def windowsManagement(){
    def title = formatText("WINDOWS SETTINGS","white", "grey")

    def pageProperties = [
        name:       "windowsManagement",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section(formatText("Fans or Windows", "white", "blue"))
        {
            input "controlWindows", "bool", title: "Control some windows", submitOnChange:true
            if(controlWindows)
            {
                input "windows", "capability.switch", title: "Turn on some switches/fan/windows when home needs to cool down, wheather permitting", multiple:true, required: false, submitOnChange: true
                if(windows)
                {
                    if(windows.size() > 1)
                    {
                        input "onlySomeWindowsWillOpen", "bool", title:"Differentiate some windows' behavior based on location mode", submitOnChange: true, defaultValue:false

                        if(onlySomeWindowsWillOpen)
                        {
                            def list = []
                            int i = 0
                            int s = windows.size() 
                            for(s!=0;i<s;i++)
                            {
                                list += windows[i].toString()
                            }

                            list = list.sort()
                            //log.debug "------------- list = $list"

                            input "modeSpecificWindows", "mode", title:"select the modes under which you want only some specific windows to be operated", multiple:true, required:true
                            input "onlyThoseWindows", "enum", title:"Select the windows for these modes", options:list, required:true
                        }
                    }

                    input "timeWindowInsteadofModes", "bool", title: "use a time window instead of location modes for windows management", submitOnChange:true 
                    if(timeWindowInsteadofModes)
                    {
                        input "windowsFromTime", "time", title: "From", required: true
                        input "windowsToTime", "time", title: "To", required: true
                    }
                    else
                    {
                        input "windowsModes", "mode", title: "Select under which modes ALL WINDOWS can be operated", required:true, multiple:true, submitOnChange:true
                    }

                    if(windowsModes || timeWindowInsteadofModes)
                    {
                        input "closeWhenOutsideWindowsModes", "bool", title:"Close all windows once home location mode is no longer in ${windowsModes ? "one of these modes" : "this time window"}", defaultValue:false, submitOnChange:true
                    }

                    input "outsidetempwindowsH", "number", title: "Set a temperature below which it's ok to turn on $windows", required: true, submitOnChange: true
                    input "outsidetempwindowsL", "number", title: "Set a temperature below which it's NOT ok to turn on $windows", required: true, submitOnChange: true
                    if(outsidetempwindowsH && outsidetempwindowsL)
                    {
                        paragraph "If outside temperature is between ${outsidetempwindowsL}F & ${outsidetempwindowsH}F, $windows will be used to coold down your place instead of your AC"

                        input "operationTime", "bool", title: "${windows}' operation must stop after a certain time", defaultValue:false, submitOnChange:true
                        if(operationTime)
                        {
                            input "windowsDuration", "number", title: "Set minimum operation time", description: "time in seconds", required: false, submitOnChange:true
                            if(windowsDuration)
                            {
                                paragraph "<div style=\"width:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${app.name} will determine duration based on this value and outside temperature. The cooler it is outside, the shorter the duration (the closer the duration will be to the minimum you set here). Recommended value: 10 seconds</div>"

                                input "customCommand", "text", title: "custom command to stop operation (default is 'off()')", required: false, defaultValue:"off()", submitOnChange:true
                                input "differentDuration", "bool", title: "Differentiate operation time", defaultValue:false, submitOnChange:true
                                if(!differentDuration)
                                {
                                    input "maxDuration", "number", title: "Set maximum operation time", description: "time in seconds", required: false, submitOnChange:true

                                }
                                else
                                {
                                    def list = []
                                    int i = 0
                                    int s = windows.size() 
                                    def device
                                    for(s!=0;i<s;i++)
                                    {
                                        device = windows[i]
                                        input "windowsDuration${i}", "number", title: "Set minimum operation time for $device", description: "time in seconds", required: false, submitOnChange:true
                                        input "maxDuration${i}", "number", title: "Set maximum operation time $device", description: "time in seconds", required: false, submitOnChange:true

                                    }
                                }
                            }

                            if(customCommand)
                            {
                                def cmd = customCommand.contains("()") ? customCommand.minus("()") : customCommand
                                def windowsCmds = windows.findAll{it.hasCommand("${cmd}")}
                                boolean cmdOk = windowsCmds.size() == windows.size()
                                if(!cmdOk)
                                {
                                    paragraph "<div style=\"width:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">SORRY, THIS COMMAND $customCommand IS NOT SUPPORTED BY AT LEAST ONE OF YOUR DEVICES! Maybe a spelling error? In any case, make sure that each one of them support this command</div>"

                                }
                                else
                                {
                                    paragraph """<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">The command $customCommand is supported by all your devices!</div> """

                                }
                            }

                        }
                    }

                    if(doorsContacts && doorThermostat)
                    {
                        paragraph """In the 'contact sensors' settings you opted for for synchronizing your thermostat's operations 
with another thermostat's when some door contacts are open. Do you want to also control the windows from this other thermostat's room?"""
                        input "useOtherWindows", "bool", title: "Also control these windows when $doorsContacts are open", submitOnChange:true, defaultValue:false
                        if(useOtherWindows)
                        {
                            input "otherWindows", "capability.switch", title: "Select your windows", required:true, multiple:true
                        }

                    }
                }
            }
        }
    }
}
def fanCirculation(){
    def title = formatTitle("AIR CIRCULATION")

    def pageProperties = [
        name:       "fanCirculation",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section()
        {
            if(contact || windows)
            {
                input "fanCirculateAlways", "bool", title:"Run ${thermostat}'s fan circulation when contacts are open and temp is getting too high", submitOnChange:true
                input "fanCirculateSimpleModeOnly", "bool", title:"Run fan circulation only in $simpleModeName Mode", submitOnChange:true, defaultValue:false
                if(ignoreTarget && (fanCirculateAlways || fanCirculateSimpleModeOnly)) 
                {
                    app.updateSetting("fanCirculateAlways", [type:"bool", value:false])
                    app.updateSetting("fanCirculateSimpleModeOnly", [type:"bool", value:false])
                    paragraph formatText("Fan circulate is not compatible with 'ignore target' settings! Disable it in thermostats section)", "white", "red")
                }
                input "fanCirculateAlways", "bool", title:"Run ${thermostat}'s fan circulation without interruption", submitOnChange:true

                if(fancirculate) 
                {
                    app.updateSetting("fanCirculateAlways", [type:"bool", value:false])
                }
                if(fanCirculateAlways)
                {
                    app.updateSetting("offrequiredbyuser",[type:"bool", value:false])    
                    atomicState.fanCirculateAlways = true // needed for the UI to notify user of incompatibility with "offrequiredbyuser"
                    input "alwaysButNotWhenPowerSaving", "bool", title:"Fan circulation must stop when there is no motion (if motion sensitivity has been enabled) and/or in away mode", defaultValue:false
                }
                else
                {
                    atomicState.fanCirculateAlways = false
                }
                if(fanCirculateSimpleModeOnly)
                {
                    if(fanCirculateSimpleModeOnly) app.updateSetting("fanCirculateModes", [type:"mode", value:null])
                    if(!fanCirculateSimpleModeOnly) input "fanCirculateModes", "mode", title:"Run fan circulation only in certain modes", multiple:true, submitOnChange:true
                    app.updateSetting("fancirculate", [type:"bool", value:false])
                }

            }
            input "fan", "capability.switch", title: "Turn on a fan", submitOnChange:true
            if(fan)
            {
                input "fanWhenCoolingOnly", "bool", title:"Turn $fan on when cooling only", submitOnChange:true
                if(fanWhenCoolingOnly && neverTurnOff) paragraph "not compatible with keeping the fan on"
                input "fanWhenHeatingOnly", "bool", title:"turn on $fan when heating only", submitOnChange:true
                if(fanWhenHeatingOnly && neverTurnOff) paragraph "not compatible with keeping the fan on"
                input "neverTurnOff", "bool", title:"Never turn off $fan", submitOnChange:true

                if(fanWhenCoolingOnly || neverTurnOff) app.updateSetting("fanWhenHeatingOnly", [value:false, type:"bool"])// foolproofing 
                if(fanWhenHeatingOnly || neverTurnOff) app.updateSetting("fanWhenCoolingOnly", [value:false, type:"bool"])// foolproofing 

                if(neverTurnOff) 
                {
                    app.updateSetting("fanWhenCoolingOnly", [value:false, type:"bool"])// foolproofing 
                    app.updateSetting("fanWhenHeatingOnly", [value:false, type:"bool"])// foolproofing 
                }
                else if(!neverTurnOff && (fanWhenHeatingOnly || fanWhenCoolingOnly))
                {

                }
                input "keepFanOnInRestrictedMode", "bool", title: "Keep the fan running when in restrcited mode..."
                input "keepFanOnInNoMotionMode", "bool", title: "Keep the fan running when in there is no motion..."
            }
            else // foolproofing 
            {
                app.updateSetting("fanWhenCoolingOnly", [value:false, type:"bool"])
                app.updateSetting("fanWhenHeatingOnly", [value:false, type:"bool"])
                app.updateSetting("keepFanOnInRestrictedMode", [value:false, type:"bool"])
                app.updateSetting("keepFanOnInNoMotionMode", [value:false, type:"bool"])
                app.updateSetting("neverTurnOff", [value:false, type:"bool"])
            }
            input "fanDimmer", "capability.switchLevel", title:"Control a fan with a dimmer", submitOnChange:true

            if(fanDimmer)
            {
                input "maxFanSpeed", "number", title: "Set the maxium value for $fanDimmer", submitOnChange:true, defaultValue:50,range:"30..100"
                input "mediumFanSpeed", "number", title:"Set a medium value for $fanDimmer",range:"30..$maxFanSpeed"
                input "lowFanSpeed", "number", title:"Set the lowest value for $fanDimmer", range:"0..$mediumFanSpeed"

                log.debug "dimmer = $dimmer fanDimmer = $fanDimmer"

                if(fanDimmer?.displayName == dimmer?.displayName || fanDimmer?.displayName == fan?.displayName)
                {
                    def m = "You cannot use ${fanDimmer == dimmer ? "$dimmer" : "$fan"} for this operation"
                    paragraph formatText(m, "white", "red")
                    app.updateSetting("fanDimmer", [type:"capability", value:null])

                }
                else
                {
                    paragraph formatText("Fan speed will adjust with cooling efficiency", "white", "grey")
                    input "silenceMode", "mode", title:"Keep this dimmer at a certain level in certain modes", submitOnChange:true
                    if(silenceMode)
                    {
                        input "silenceValue", "number", title:"Set a target level for these modes"
                    }
                }
                input "neverTurnOffFanDimmer","bool", title:"Keep this fan on at all times"
                input "keepFanDimmerOnIfOutsideLowerThanInside","bool", title:"Keep this fan on if outside temperature is lower than inside temp"

                if(WindowsContact)
                {
                    input "keepFanOnWhenWindowsOpen", "bool", title:"Keep this fan on when ${WindowsContact.join(", ")} ${WindowsContact.size() > 1 ? "are":"is"} open"
                }
            }
        }
    }
}
def virtualThermostat(){
    def title = formatTitle("Heater & Cooler")

    def pageProperties = [
        name:       "virtualThermostat",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section("Select alternate heater and/or cooler")
        {
            input "heatpump", "bool", title: "$thermostat is a heat pump", submitOnChange:true

            if(differentiateThermostatsHeatCool && heatpump) {
                app.updateSetting("heatpump", [type:"bool", value: false])
                def mess = "'Heat pump management' is not compatible with the dual thermostat option you selected in the thermostats section"
                paragraph formatText(mess, "white", "darkgray")
            }
            else {
                def mssg = "Because $thermostat is a heatpump, you must select an alternate heater controlled by a switch (see further down). This is due to the fact that a heatpump will not be power efficient under certain weather conditions and temperatures. ${app.label} will make sure the best device is being used when needed"
                if(heatpump){paragraph formatText (mssg, "blue", "white")}
            }

            boolean heaterRequired = heatpump && !altThermostat ? true : altThermostat ? false : false // to set the "required" parameter, since it's a boolean. 
            boolean altThermRequired = heatpump && !heater ? true : false

            if(heaterRequired) 
            {
                paragraph formatText("Either heater or alternate thermostat is mandatory with heat pump option", "red", "white")
            }

            input "heater", "capability.switch", title: "Select a switch to control an alternate heater", required: heaterRequired, submitOnChange:true, multiple: false 

            input "altThermostat", "capability.thermostat", title:"Select an alternate thermostat", submitOnChange:true

            def noIssue = true
            if(differentiateThermostatsHeatCool && ("${altThermostat}" == "${thermostatHeat}" || "${altThermostat}" == "${thermostatCool}"))
            {
                log.warn "test"
                app.updateSetting("altThermostat", [type:"capability", value:[]])
                def mess = "$altThermostat can't be used as your alternate thermostat because it's already selected in the 'dual thermostat' section"
                paragraph formatText(mess, "red", "darkgray")
                input "acknowledge", "bool", title: "OK", required:true, submitOnChange:true, defaultValue:false // just to refresh the dynamic page
                noIssue = false

            }
            if(noIssue && (heater || altThermostat))
            {
                def only = "ONLY if outside temperature falls below a threshold"
                def m = heater && altThermostat ? "Turn on $heater and set $altThermostat to heat $only" : heater ? "Turn on $heater $only" : altThermostat ? "Set $altThermostat to heat $only" : "error"
                input "addLowTemp", "bool", title: m, submitOnChange:true


                if(heatpump || addLowTemp)
                {
                    input "lowtemp", "number", title: "low outside temperature threshold", required: true, defaultValue: 30

                    input "useAllHeatSources", "bool", title: "Use both heatpump and alternate heating source until outside temp reaches an even lower threshold", submitOnChange:true

                    if(differentiateThermostatsHeatCool && useAllHeatSources && heatpump) {
                        app.updateSetting("useAllHeatSources", [type:"bool", value: false])
                        def mess = "'Use both heat sources' is not compatible with the dual thermostat option you selected in the thermostats section"
                        paragraph formatText(mess, "white", "darkgray")
                    }
                    if(useAllHeatSources)
                    {
                        input "evenLowertemp", "number", title: "Second low outside temperature threshold", required: true, defaultValue: 0, submitOnChange:true 

                    }
                    input "useAllHeatSourcesWithMode", "bool", title: "Use all heat sources when in certain modes", submitOnChange:true
                    if(useAllHeatSourcesWithMode)
                    {
                        input "allHeatModes", "mode", title: "Select modes under which all heat sources will be warming your place", required: true, submitOnChange:true, multiple:true

                    }

                    input "doNotUseMainThermostatInCertainModes", "bool", title: "Exclusively use ${heater && altThermostat?"$heater and $altThermostat":heater?"$heater" : "$altThermostat"} when in certain modes", submitOnChange:true
                    if(doNotUseMainThermostatInCertainModes)
                    {
                        input "altThermostatORheaterOnlyModes", "mode", title: "Select modes under which all heat sources will be warming your place", required: true, submitOnChange:true, multiple:true
                        if(nightModeButton)
                        {
                            input "altThermostatOnlyInSimpleMode" , "bool", title: "Apply this option when in $simpleModeName mode (related to $nightModeButton)", submitOnChange:true
                        }

                    }
                    updateAllHeatSourcesBooleans()

                }
                if(heater?.hasAttribute("power") && pw) // if both are true: power meter cap for heater switch and verify status with power meter
                {
                    input "controlPowerConsumption", "bool", title:"control power consumption", submitOnChange: true, required: false
                    if(controlPowerConsumption)
                    {
                        input "maxPowerConsumption", "number", title: "Set maximum power in watts", submitOnChange:true, required:true
                        input "devicePriority", "enum", title: "Priority given to:",options: ["$heater", "$thermostat"], required: true, submitOnChange:true
                    }
                }
            }


            input "cooler", "capability.switch", title: "Select a switch to control an alternate cooler", required: false, submitOnChange:true, multiple: false
            if(cooler)
            {
                input "preferCooler", "bool", title: "Prefer cooler (set to false to see power savings options)", submitOnChange:true, defaultValue:false
                if(preferCooler)
                {
                    def x = "$cooler will be the only unit to cool this room, ${thermostat.toString()} is still used as an important sensor and user input source"
                    paragraph formatText(x, "white", "grey")

                    input "preferCoolerLimitTemperature", "number", title: "unless outside temperature is beyond this value", submitOnChange:true, description:"set a temperature threshold, leave empty if not required"
                    input "userBoostOffset", "number", title:"unless inside temperature is greater than target temperature by this amplitude", description:"set the amplitude (x=inside temp - target)"

                    // foolproof, user must preferCooler with these options
                    app.updateSetting("coolerControlPowerConsumption", [type:"bool",value:false])
                    app.updateSetting("coolerMaxPowerConsumption", [type:"number", value:null])
                    app.updateSetting("coolerDevicePriority", [type:"enum", value:null])
                    app.updateSetting("addHighTemp", [type:"bool", value:false])   

                    input "efficiencyOverride", "bool", title:"Efficiency override: keep $thermostat cooling if it turns out that $cooler isn't as efficient as it should"

                }
                else
                { // if not preferCooler... 
                    input "addHighTemp", "bool", title: "Turn on $cooler only if OUTSIDE temperature goes beyond a certain threshold", submitOnChange:true

                    if(addHighTemp)
                    {
                        input "hightemp", "number", title: "high outside temperature threshold", required: true, defaultValue: 80
                    }
                    if(cooler.hasAttribute("power") && pw) // if both are true: power meter cap for heater switch and verify status with power meter
                    {
                        input "coolerControlPowerConsumption", "bool", title:"control power consumption", submitOnChange: true, required: false
                        if(coolerControlPowerConsumption)
                        {
                            input "coolerMaxPowerConsumption", "number", title: "Set maximum power in watts", submitOnChange:true, required:true
                            input "coolerDevicePriority", "enum",title: "Priority given to:", options: ["$cooler", "$thermostat"], required: true, submitOnChange:true
                            log.warn """
devicePriority = $devicePriority
"""
                        }
                    }
                }
            }
        }
    }
}
def updateAllHeatSourcesBooleans(){
    log.debug "udpating updateAllHeatSourcesBooleans"
    if(useAllHeatSourcesWithMode) 
    {
        app.updateSetting("useAllHeatSources", [type:"bool",value:false])
        log.debug "useAllHeatSources set to false"
    }
    if(useAllHeatSources || doNotUseMainThermostatInCertainModes) 
    {
        app.updateSetting("useAllHeatSourcesWithMode", [type:"bool",value:false])
        log.debug "useAllHeatSourcesWithMode set to false"
    }
}
def operationConsistency(){
    def title = formatTitle("Verify consistency of most operations")

    def pageProperties = [
        name:       "operationConsistency",
        title:      title,
        nextPage:   "MainPage",
        install: false,
        uninstall: false
    ]
    dynamicPage(pageProperties) {
        section("power consistency")
        {
            input "forceCmd", "bool", title:"Force commands (for old non-Zwave-plus devices that don't refresh their status properly under certain mesh conditions)", defaultValue:false
            input "pw", "capability.powerMeter", title:"optional: verify my thermostat's consistent operation with a power meter", required:false, submitOnChange:true
            if(!pw)
            {
                input "dontcheckthermstate", "bool", title:"Do not check current thermostat's state before sending a command", defaultValue:false
            }
        }
        section("Safety")
        {
            input "antifreeze", "bool", title:"Optional: Customize Antifreeze",submitOnChange:true,defaultValue:false

            if(antifreeze)
            {
                input "safeValue", "number", title: "safety temperature", required:true, submitOnChange:true
                input "backupSensor", "capability.temperatureMeasurement", title: "Optional: pick a backup sensor (in case of device failure)", required:false

            }
            input "sendAlert", "bool", title: "Send a sound and/or text notification when temperature goes below antifreeze safety", submitOnChange:true
            if(sendAlert)            
            {
                input "speech", "capability.speechSynthesis", title: "Select speech devices", multiple:true, required:false, submitOnChange: true 
                input "musicDevice", "capability.musicPlayer", title: "Select music players", multiple:true, required:false, submitOnChange: true 
                if(musicDevice || speech)
                {
                    input "volumeLevel", "number", title: "Set the volume level", range: "10..100",required:true, submitOnChange: true  
                }
                input "initializeDevices", "bool", title:"Try to fix unresponsive speakers (such as Chrome's)", defaultValue:false
                input "notification", "capability.notification", title: "Select notification devices", multiple:true, required:false, submitOnChange: true 
            }   
        }
    }
}

def pageNameUpdate(){
    closeBoolQuestions()
    def pauseVar = atomicState.failedSensors ? "FAILED SENSORS" : "paused"
    def batteryVar = "LOW BATTERY"
    def previousLabel = app.label // save current label

    if(atomicState.paused || atomicState.lowBattery || atomicState.lowBatterySensor)
    {
        while(app.label.contains(" $pauseVar "))
        {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        while(app.label.contains(" $batteryVar "))
        {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }

        log.debug "original label = $app.label"            

        if(atomicState.paused)
        {
            app.updateLabel(previousLabel + ("<font color = 'red'> $pauseVar </font>" )) // recreate label
        }
        else if(atomicState.lowBattery || atomicState.lowBatterySensor)
        {
            app.updateLabel(previousLabel + ("<font color = 'red'> $batteryVar </font>" )) // recreate label
        }

        atomicState.button_name = atomicState.paused ? "resume" : "pause"
        log.debug "button name is: $atomicState.button_name new app label: ${app.label}"
    }
    else 
    {
        atomicState.button_name = "pause"
        logging "button name is: $atomicState.button_name"
    }
    if(app.label.contains(pauseVar) && !atomicState.paused)
    {
        app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>" ))
        while(app.label.contains(" $pauseVar "))
        {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }
        log.debug "new app label: ${app.label}"
    }
    if(app.label.contains(batteryVar) && (!atomicState.lowBattery && !atomicState.lowBatterySensor))
    {
        app.updateLabel(app.label.minus("<font color = 'red'> $batteryVar </font>" ))
        while(app.label.contains(" $batteryVar "))
        {
            app.updateLabel(app.label.minus(" $batteryVar "))
        }
        log.debug "new app label: ${app.label}"
    }

    // when page is loaded and app is paused make sure page loading doesn't add "paused" several times
    if(app.label.contains(pauseVar) && atomicState.paused)
    {
        //app.updateLabel(app.label.minus("<font color = 'red'> $pauseVar </font>" ))
        while(app.label.contains(" $pauseVar "))
        {
            app.updateLabel(app.label.minus(" $pauseVar "))
            app.updateLabel(app.label.minus("<font color = 'red'>"))
            app.updateLabel(app.label.minus("</font>"))
        }
        app.updateLabel(app.label + ("<font color = 'red'> $pauseVar </font>" )) // recreate label
    }
    if(app.label.contains(batteryVar) && (atomicState.lowBattery || atomicState.lowBatterySensor))
    {
        //app.updateLabel(app.label.minus("<font color = 'red'> $batteryVar </font>" ))
        while(app.label.contains(" $batteryVar "))
        {
            app.updateLabel(app.label.minus(" $batteryVar "))
            app.updateLabel(app.label.minus("<font color = 'red'>"))
            app.updateLabel(app.label.minus("</font>"))
        }
        app.updateLabel(app.label + ("<font color = 'red'> $batteryVar </font>" )) // recreate label
    }
}

/************************************************INITIALIZATION*************************************************/
def installed() {
    logging("Installed with settings: ${settings}")

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
    if(enabledebug)
    {
        log.warn "debug enabled"      
        atomicState.EnableDebugTime = now()
        runIn(1800,disablelogging)
        atomicState.enableDescriptionTime = now()
        runIn(86400, disabledescription)
        descriptionText "debug will be disabled in 30 minutes"
    }
    else 
    {
        log.warn "debug disabled"
    }

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
    atomicState.overrideTime = now() as long
        atomicState.resendAttempt = now() as long
        atomicState.offAttempt = now() as long

        atomicState.lastMotionEvent = now() as long
        atomicState.lastNotification = now() as long
        atomicState.motionEvents = 0
    atomicState.lastTimeBsTrue = now() as long
        atomicState.setPointOverride = false

    logging("subscribing to events...")

    //subscribe(location, "mode", ChangedModeHandler) 
    subscribe(thermostat, "temperature", temperatureHandler)
    if(sensor)
    {
        int i = 0
        int s = sensor.size()
        for(s != 0; i<s;i++)
        {
            subscribe(sensor[i], "temperature", temperatureHandler)
        }
    }
    if(dimmer)
    {
        subscribe(dimmer, "level", dimmerHandler)
        descriptionText "subscribed $dimmer to dimmerHandler"
    }
    if(specialDimmer)
    {
        subscribe(specialDimmer, "level", specialDimmerHandler)
    }
    atomicState.lastThermostatInput = atomicState.lastThermostatInput ? atomicState.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")

    subscribe(thermostat, "heatingSetpoint", setPointHandler)
    subscribe(thermostat, "coolingSetpoint", setPointHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)

    descriptionText "subscribed ${thermostat}'s coolingSetpoint to setPointHandler"
    descriptionText "subscribed ${thermostat}'s heatingSetpoint to setPointHandler"
    descriptionText "subscribed ${thermostat}'s thermostatMode to thermostatModeHandler"

    if(sync && thermostatB)
    {
        int i = 0
        int s = thermostatB.size()
        for(s!= 0; i<s; i++)
        {
            subscribe(thermostatB[i], "heatingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "coolingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "thermostatMode", thermostatModeHandler)
            descriptionText "subscribed ${thermostatB[i]}'s thermostatMode to thermostatModeHandler"
            descriptionText "subscribed ${thermostatB[i]}'s heatingSetpoint to setPointHandler"
            descriptionText "subscribed ${thermostatB[i]}'s coolingSetpoint to setPointHandler"
        }
    }    

    if(altThermostat)
    {
        subscribe(altThermostat, "heatingSetpoint", setPointHandler)
        subscribe(altThermostat, "coolingSetpoint", setPointHandler)
        subscribe(altThermostat, "thermostatMode", thermostatModeHandler)
    }


    subscribe(location, "mode", modeChangeHandler)

    if(windows && controlWindows)
    {
        if(windows.every{element -> element.hasCapability("ContactSensor")})
        {
            subscribe(windows, "contact", contactHandler)
            subscribe(windows, "contact", windowsHandler)
            log.debug "$windows subscribed to contactHandler()"
        }
    }
    if(simpleModeContact)
    {        
        subscribe(simpleModeContact, "contact", simpleModeContactHandler)
    }
    if(nightModeButton)
    {
        subscribe(nightModeButton, "pushed", pushableButtonHandler)   

    }
    if(buttonPause)
    {
        subscribe(buttonPause, "doubleTapped", doubleTapableButtonHandler) 
        log.debug "${buttonPause.join(", ")} subscribed to doubleTapableButtonHandler"
    }
    if(WindowsContact)
    {
        subscribe(WindowsContact, "contact", contactHandler)
        log.debug "subscribed ${WindowsContact.join(", ")} to events"
    }
    if(motionSensors)
    {
        subscribe(motionSensors, "motion", motionHandler)
        log.debug "subscribed ${motionSensors.join(", ")} to motion events"
    }

    if(polldevices)
    {
        schedule("0 0/5 * * * ?", Poll)
    }
    if(controlPowerConsumption || coolerControlPowerConsumption)
    {
        schedule("0 0/2 * * * ?", pollPowerMeters)
    }

    /* UNCOMMENT, RUN THE UPDATED(), THEN COMMENT OUT AGAIN TO RESET THE LEARNING BASE *****/
    //atomicState.db = null
    /*******************************************************************************/
    if(atomicState.db == null)
    {
        log.warn "DB RESET !"
        atomicState.db = ['-40':113,'-30':103,'-20':93,'-10':83,'0':73,'10':63,'15':58,'20':53,'25':48,'30':43,'35':38,'40':33,'45':28,'50':23,'55':18,'60':13,'65':8,'70':3,'75':0,'80':-5,'85':-9,'90':-12,'95':-17,'100':-21,'105':-26,'110':-30,'115':-35,'120':-40,'125':-45,'130':-50]
    }

    if(celsius)
    {
        def db = [:]
        atomicState.db.eachWithIndex { 
            key, val, index ->
            db += ["${getCelsius(key.toInteger())}":val]
        }
        log.debub "DB CELSIUS = $db"
        atomicState.db = db
    }

    schedule("0 0/1 * * * ?", mainloop)


    descriptionText "END OF INITIALIZATION"

}

/************************************************EVT HANDLERS***************************************************/
def modeChangeHandler(evt){
    log.debug "$evt.name is now $evt.value"

    atomicState.dontcheckthermstateCount = 0

    if(location.mode in restricted)
    {
        logtrace "$thermostat set to $restrictedThermMode due to restricted mode"
        thermostat."${restrictedThermMode}"()
        altThermostat?."${restrictedThermMode}"()
        heater?.off() // make sure this one is off 
        cooler?.off() // same
        def fanCmd = keepFanOnInRestrictedMode ? "on" : "off"
        if(fan && fan?.currentValue("switch") != "fanCmd")
        {
            fan?."${fanCmd}()"
            descriptionText "$fan turned $fanCmd fe58"
        }
    }
    else
    {
        logtrace "location not in restricted mode, resuming normal operations"

        if(location.mode in windowsModes)
        {
            // do nothing
        }
        else if(closeWhenOutsideWindowsModes)
        {
            windows?.off()
        }
    }


    //mainloop()
}
def appButtonHandler(btn) {

    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused
        log.warn "atomicState.paused = $atomicState.paused"

        if(atomicState.paused)
        {
            log.debug "unsuscribing from events..."
            unsubscribe()  
            log.debug "unschedule()..."
            unschedule()
            break
        }
        else
        {
            updated()            
            break
        }
        case "update":
        atomicState.paused = false
        updated()
        break
        case "run":
        if(!atomicState.paused) mainloop("btn")
        break
        case "poll":
        Poll()
        pollPowerMeters()
        break

    }
}
def contactHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.info "$evt.device is $evt.value"
        if(evt.value == "open")
        {
            atomicState.lastContactOpenEvt = now() 
        }
    }
    mainloop("contactHandler")
}
def motionHandler(evt){
    logging "MOTION EVENT $evt.device is $evt.value"
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        atomicState.activeMotionCount = atomicState.activeMotionCount ? atomicState.activeMotionCount : 0
        if(evt.value == "active")
        {
            atomicState.activeMotionCount += 1 // eventsSince() can be messy // hubitat still doesn't acknowledge the issue despite several tickets and screenshots. 
            atomicState.lastMotionEvent = now() // initialized at install or update
            // so this is a workaround in the meantime
        }
        mainloop("motionHandler")
    }
}
def temperatureHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        logging("$evt.device returns ${evt.value}F")
        mainloop("temperatureHandler")
    }
}
def simpleModeContactHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.info "$evt.device is $evt.value"

        atomicState.lastBSeventStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        if(now() - atomicState.lastBSevent > 60000) // prevent false positives due to floating state of the $simpleModeName Mode trigger due to the mattress's weight (still working on this...)
        {
            atomicState.ButtonSupercedes = false // if there's a new contact event, this means it is working as expected, therefore no need for the button to supercede the sensor
        }

        // this boolean remains false until next button event
        atomicState.lastBSevent = now()
        mainloop("simpleModeContactHandler")
    }
}
def dimmerHandler(evt){

    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 

        descriptionText "new dimmer level is $evt.value method = $method && setpointSentByApp = $atomicState.setpointSentByApp"

        // learning from user's input for the auto method
        learn(evt.value) // will also respond to thermostat inputs because it is ran before testing if it's set by the app or not

        if(atomicState.setpointSentByApp)
        {
            logtrace "dimmer value set by this app"
        }
        else 
        {
            userWants(evt.value.toInteger(), getInsideTemp())
        }

        atomicState.setpointSentByApp = false // always reset this variable after calling it

        //mainloop() // prevent feedback loops so both dimmer and thermostat set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def setPointHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        logtrace "$evt.device $evt.name $evt.value"

        logging "sync ? $sync thermostatB: $thermostatB" 

        if(sync && thermostatB)
        {
            def cmd = "set${evt.name.capitalize()}"
            int i = 0
            int s = thermostatB.size()


            logging """
thermostat = $evt.device
evt.value = $evt.value
evt.name = $evt.name
${thermostat?.currentValue(evt.name) != "$evt.value"}

KEEP FOR FUTURE REFERENCE!
thermostatB current set point: ${thermostatB[0].currentValue(evt.name)} = $evt.value
true? ${thermostatB[0].currentValue(evt.name) == evt.value.toInteger()}
any found with same current value: ${thermostatB?.any{it -> it.currentValue(evt.name) == evt.value.toInteger()}} 


"""
            if("$evt.device" == "$thermostat")
            {
                //log.warn "case ASP"
                for(s!=0; i<s; i++)
                {
                    thermostatB[i]."${cmd}"(evt.value)
                    descriptionText "${thermostatB[i]} $cmd $evt.value"
                }
            }
            if(thermostatB.find{it.toString() == "$evt.device"})
            {
                //log.warn "case BSP"
                atomicState.setpointSentByApp = true
                resetSetByThisApp()                
                boolean okToOff = evt.value == "off" ? okToTurnOff() : true 
                if(okToOff) {

                    descriptionText "$thermostat $cmd $evt.value 7rgha"
                    thermostat."${cmd}"(evt.value)
                }
                //
            }
            //return // must not set atomicState.setpointSentByApp back to false in this case
        }

        if(!atomicState.setpointSentByApp)
        {
            descriptionText "new $evt.name is $evt.value -------------------------------------"
            def inside = getInsideTemp()

            userWants(evt.value.toInteger(), inside)

            def currDim = !dimmer ? atomicState.lastThermostatInput : dimmer?.currentValue("level")
            def thermMode = thermostat?.currentValue("thermostatMode")

            // this will be true only if thermostat is heating or cooling; therefore, dimmer won't be adjusted if off 
            // using atomicState.lastNeed == "heat" / "cool" seemed to allow exceptions... UPDATE but we need it. Let's keep an eye on this... 
            boolean correspondingMode = (evt.name == "heatingSetpoint" && atomicState.lastNeed == "heat") || (evt.name == "coolingSetpoint" && atomicState.lastNeed == "cool")
            //boolean correspondingMode = (evt.name == "heatingSetpoint" && thermMode == "heat") || (evt.name == "coolingSetpoint" && thermMode == "cool")

            def message = """
atomicState.setpointSentByApp = $atomicState.setpointSentByApp
Current $dimmer value is $currDim
atomicState.lastThermostatInput = $atomicState.lastThermostatInput
atomicState.lastNeed = $atomicState.lastNeed   
evt.value = $evt.value   
"""
            logging "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">$message</div>"

            boolean simpleModeActive = simpleModeIsActive()
            def target = getTarget(simpleModeActive)


            atomicState.inside = atomicState.inside != null ? atomicState.inside : inside
            def needData = getNeed(target, simpleModeActive, inside)
            def need = needData[1]
            def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter


            // make sure the therm event is same as current need
            // as to not apply a value from a differentiated thermostat mode (heat set to 75 will modify coolingSP and then trigger an event)
            logging """
method = $method
correspondingMode = $correspondingMode
currDim = $currDim
evt.value = $currDim"""

            if(correspondingMode && currDim != evt.value) // if an only if this is regarding the right operating mode, update the dimmer's value
            {
                if(method == "normal")
                {
                    //runIn(3, setDimmer, [data:evt.value.toInteger()])

                    setDimmer(evt.value) // called only if it's not a value automatically set by the thermostat on the opposite operating mode (heatingSetpoint when cooling)

                    // every thermostat making sure to keep an offset between heating SP and cooling SP equal or superior to 2 degrees
                    //atomicState.lastThermostatInput = evt.value //////done by setDimmer()

                }
            }
            if(!correspondingMode)
            {
                descriptionText "not updating ${dimmer ? "dimmer" : "atomicState.lastThermostatInput"} because this is $evt.name and current mode is $thermMode"
            }
            if(currDim == evt.value)
            {
                descriptionText "${dimmer ? "dimmer" : "atomicState.lastThermostatInput"} value ok (${dimmer ? '${dimmer?.currentValue("level")}' : "atomicState.lastThermostatInput"} = ${evt.value}"
            }
        }
        else
        {
            logtrace "event generated by this app, doing nothing"
        }

        //mainloop() // prevent feedback loops so both dimmer and thermosta set points can be modified. Changes will be made on next scheduled loop or motion events
        atomicState.lastSetPoint = evt.value
    }
    atomicState.setpointSentByApp = false // always reset this static/class variable after calling it
}
def specialDimmerHandler(evt){

    logtrace "$evt.device set to $evt.value | NEW $simpleModeName Mode target temperature"
    app.updateSetting("specialTemp", [type:"number", value:"$evt.value"])
    mainloop()

}
def pushableButtonHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.debug "BUTTON EVT $evt.device $evt.name $evt.value"

        if(evt.name == "pushed") 
        {
            if(!ignoreTarget && !simpleModeSimplyIgnoresMotion) thermostat.off() // always set it to off in order to reset values in this case (idiosyncratic of my setup, feel free to comment out this line)

            atomicState.buttonPushed = !atomicState.buttonPushed 

            atomicState.lastButtonEvent = atomicState.buttonPushed ? now() : atomicState.lastButtonEvent // time stamp when true only

            if(lightSignal && atomicState.buttonPushed) 
            {
                flashTheLight()
            }

            def status = atomicState.buttonPushed ? "NOW ACTIVE" : "DISABLED"
            log.info formatText("$simpleModeName Mode $status", "white", "grey")

            mainloop("pushableButtonHandler")

            return
        }
        mainloop("pushableButtonHandler")
    }
    else
    {
        log.warn "App is paused, button event was ignored"
    }
}
def doubleTapableButtonHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.debug "BUTTON EVT $evt.device $evt.name $evt.value"

        if(evt.name == "doubleTapped")
        {
            atomicState.paused = !atomicState.paused 
            def message = atomicState.paused ? "APP PAUSED BY DOUBLE TAP" : "APP RESUMED BY DOUBLE TAP"
            log.warn message
            if(buttonTimer && atomicState.paused) {
                log.debug "App will resume in $buttonTimer minutes"
                runIn(buttonTimer, updated)
            }
        } 
    }
}
def thermostatModeHandler(evt){

    if(location.mode in restricted){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 

    if(evt.value == "auto" && autoOverride)
    {
        log.debug "OVERRIDE REQUEST DETECTED"
        atomicState.overrideTime = now()  
        atomicState.override = true
        return
    }
    else 
    {

        atomicState.override = false

    }

    if(!atomicState.restricted && !atomicState.paused){
        logging """$evt.device $evt.name $evt.value
sync ? $sync
thermostatB: $thermostatB

"""

    }
}
def outsideThresDimmerHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        descriptionText "*********** Outside threshold value is now: $evt.value ***********"
        //mainloop()
    }
}
def windowsHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.debug "$evt.device is $evt.value"
        boolean doorContactsAreOpen = doorsContactsAreOpen()

        atomicState.closingCommand = atomicState.closingCommand != null ? atomicState.closingCommand : true

        if(evt.value == "open")
        {
            boolean openMore = !atomicState.widerOpeningDone && atomicState.insideTempHasIncreased

            if(!openMore){
                atomicState.lastOpeningTime = now()
            }
            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        }
        else if(evt.value == "closed" && atomicState.closingCommand)
        {
            atomicState.closingCommand = false // reset this boolean to false
            atomicState.lastClosingTime = now() // we don't want this value to be reset at every device's wake up/refresh, hence 'atomicState.closingCommand' boolean"
            atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
        }
    }
}

/************************************************MAIN OPERATIONS*************************************************/

def mainloop(source){

    descriptionText "mainloop called by $source"

    atomicState.lastThermostatInput = atomicState.lastThermostatInput ? atomicState.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")
    if(!atomicState.paused)
    {
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        }   

        if(offrequiredbyuser && fanCirculateAlways) // fool proof, these two options must never be true together, fancirculate takes precedence
        {
            app.updateSetting("offrequiredbyuser",[type:"bool", value:false]) 
        }



        //atomicState.override = true // -> uncomment to test this function
        if(atomicState.override){
            def overrideDur = overrideDuration != null ? overrideDuration : 0
            def timeLimit = overrideDur * 60 * 60 * 1000
            def timeStamp = atomicState.overrideTime

            if(overrideDur != 0 && overrideDur != null)
            {
                if(now() - timeStamp > timeLimit)
                {
                    log.warn "END OF OVERRIDE"
                    atomicState.override = false
                    if(!fanCirculateAlways)
                    {
                        if(okToTurnOff())
                        {
                            descriptionText "thermostat off 54erg5"
                            turnOffThermostats()
                            atomicState.offAttempt = now()
                        }
                    }
                    else {
                        log.warn "END OF AUTO OVERRIDE - setting last target"
                        def target = getTarget(false)
                        def need = getNeed(target, false, getInsideTemp())[0]
                        def cmd = "set${need != "off" ? "${need$}ingSetpoint" : "${atomicState.lastNeed.capitalize()}ingSetpoint"}"
                        thermostat."${cmd}"(target)   
                    }
                }
                else 
                {
                    log.warn "OVERRIDE - AUTO MODE - remaining time: ${getRemainTime(overrideDur, atomicState.overrideTime)}"
                    def critical = criticalcold ? criticalcold : 65
                    if(thermostat.currentValue("temperature") < critical) 
                    {
                        atomicState.override = false // cancel if it gets too cold
                        atomicState.antifreeze = true
                    }
                    return
                }
            }
            else 
            {
                log.warn "OVERRIDE - APP PAUSED DUE TO AUTO MODE (no time limit)"
                return
            }
        }

        boolean contactClosed = !contactsAreOpen()
        boolean simpleModeActive = simpleModeIsActive()
        boolean motionActive = Active()
        boolean ignoreSetPoint = simpleModeActive && doNotIgnoreTargetInNightMode ? false : ignoreTarget 

        boolean doorContactsAreOpen = doorsContactsAreOpen()

        int target = getTarget(simpleModeActive)

        def inside = getInsideTemp()
        def outside = outsideTemp.currentValue("temperature")

        descriptionText "outside temperature is $outside"

        // boolean to keep main thermostat off when conditions are met
        //doNotUseMainThermostatInCertainModes
        boolean doNotUseMain = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes
        boolean heatpumpConditionsTrue = heatpump && outside < lowtemp && !useAllHeatSources ? true : useAllHeatSources && outside < evenLowertemp ? true : false 
        //use all heat sources, whatever the temp outside, in certain modes
        heatpumpConditionsTrue = doNotUseMain ? true : useAllHeatSourcesWithMode && location.mode in allHeatModes ? false : heatpumpConditionsTrue

        boolean ignoreTherMode = (dontSetThermModes && simpleModeIsActive()) || ignoreMode

        //log.warn "heatpumpConditionsTrue = $heatpumpConditionsTrue"

        def needData = getNeed(target, simpleModeActive, inside)
        def need = needData[1]

        def currSP = thermostat?.currentValue("thermostatSetpoint").toInteger()
        //log.warn "--- $currSP"
        def thermMode = thermostat?.currentValue("thermostatMode")
        logging("need is needData[1] = $need")
        def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter


        /********************** ANTI FREEZE SAFETY TESTS *************************/
        // first make sure dimmer is within safe parameters. Accidental modifications with Alexa or Siri can make it problematic...         
        def Crit = criticalcold ? criticalcold : 70
        if(dimmer?.currentValue("level") <= Crit - 1 && !simpleModeActive)
        {
            def safeVal = criticalcold && criticalhot ? (criticalcold + criticalhot) / 2 : 72
            safeVal = safeVal < 70 ? 70 : safeVal >=73 ? 72 : safeVal
            setDimmer(safeVal)
        }

        if(powersavingmode != null && location.mode in powersavingmode)
        {
            // do nothing 
            log.info "location is in power saving mode, antifreeze test is being ignored"
            atomicState.antifreeze = false
        }
        else
        {

            if(atomicState.antifreeze)
            {
                log.warn "ANTI FREEZE HAS BEEN TRIGGERED"
            }
            // antifreeze precaution (runs after calling atomicState.antifreeze on purpose here)
            def backupSensorTemp = backupSensor ? backupSensor.currentValue("temperature"): inside

            if(antifreeze && !atomicState.setPointOverride){
                def safeVal = safeValue != null ? safeValue : criticalcold != null ? criticalcold : celsius ? getCelsius(67) : 65

                if(inside <= safeVal || backupSensorTemp <= safeVal){

                    atomicState.antifreeze = true
                    atomicState.setpointSentByApp = true // make sure not to learn desired setpoint from this value
                    runIn(3, resetSetByThisApp)

                    log.warn """$thermostat setpoint set to 72 as ANTI FREEZE VALUE
inside = $inside
safeValue = $safeVal
"""
                    descriptionText "thermostat.setThermostatMode('heat')"
                    thermostat.setThermostatMode("heat")
                    thermostat.setHeatingSetpoint(72)

                    atomicState.resendAttempt = now()
                    windows?.off() // make sure all windows linked to this instance are closed
                    heater?.on()// turn on the alternate heater, if any
                    altThermostat?.setThermostatMode("heat")
                    altThermostat?.setHeatingSetpoint(72)
                    sendNotification()
                    return
                }
                else if(atomicState.antifreeze)
                {
                    atomicState.antifreeze = false
                    logtrace "END OF ANTI FREEZE"
                }

            }
            else if(!atomicState.setPointOverride)// default & built-in app's anti freeze
            {
                def defaultSafeTemp = criticalcold == null ? 58 : criticalcold <= 58 ? criticalcold : 58 
                if(inside <= defaultSafeTemp || backupSensorTemp <= defaultSafeTemp){
                    log.warn """ANTIFREEZE (DEFAULT) IS TRIGGERED: 
inside = $inside 
thermostat val = ${thermostat?.currentValue("temperature")}
backupSensorTemp = $backupSensorTemp 
backupSensor = $backupSensor

defaultSafeTemp = $defaultSafeTemp (is this user's criticalcold set temp ? ${criticalcold == null ? false : true}
"""
                    windows?.off() // make sure all windows linked to this instance are closed
                    thermostat.setThermostatMode("heat")
                    atomicState.resendAttempt = now()
                    atomicState.antifreeze = true
                    //sendNotification()
                }
                else
                {
                    atomicState.antifreeze = false
                }
            }
            //log.warn "mode: ${thermostat.currentValue("thermostatMode")}"
        }

        /********************** END OF ANTIFREEZ MANAGEMENT **********************/

        if(need in ["cool", "heat"])
        {
            logging "not changing fan mode because current need is ${need}"   
        }
        else
        {
            if(thermostat.currentValue("thermostatFanMode") == "on" && contactClosed && !fancirculate && atomicState.fanOn && !fanCirculateAlways){

                descriptionText "Setting fan back to auto"
                if(thermostat.currentValue("thermostatFanMode") != "auto") thermostat.setThermostatFanMode("auto")
                atomicState.fanOn = false 
            }
            if(fanCirculateAlways)
            {
                boolean inFanCirculateMode = fanCirculateModes ? location.mode in fanCirculateModes : false
                def fanMode = thermostat.currentValue("thermostatFanMode")
                log.trace "fanCirculateAlways => fanMode = $fanMode inFanCirculateMode (location mode) = $inFanCirculateMode"
                if(fanCirculateSimpleModeOnly && !simpleModeActive)
                {
                    if(thermostat.currentValue("thermostatFanMode") != "auto")
                    {
                        descriptionText "Setting fan back to auto because $simpleModeName Mode not currently enabled"
                        thermostat.setThermostatFanMode("auto")
                    }
                }
                else if(fanCirculateModes && !inFanCirculateMode)
                {
                    if(fanMode != "auto")
                    {
                        descriptionText "Setting fan back to auto because location is no longer in fan circulate mode"
                        thermostat.setThermostatFanMode("auto")
                    }
                }
                else
                {

                    if(fanMode != "on")
                    {                                                
                        if(!motionActive && alwaysButNotWhenPowerSaving)
                        {
                            if(thermMode != "off" || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10))
                            {
                                atomicState.dontcheckthermstateCount += 1

                                if(okToTurnOff())
                                {
                                    descriptionText "thermostat off 78egj"                                    
                                    turnOffThermostat()
                                    thermostat.setThermostatFanMode("auto")
                                }
                            }
                        }
                        else
                        {                            
                            if(okToTurnOff())
                            {
                                descriptionText "thermostat off 54gt6z34"
                                //thermostat.setThermostatMode("off")                                
                                turnOffThermostat()                                
                            }

                            if(fanCirculateAlways) 
                            { 
                                descriptionText "fan stays on at user's request"
                                thermostat.setThermostatFanMode("on")
                            }
                            else
                            {
                                thermostat.setThermostatFanMode("auto")
                            }
                        }
                    }
                    else {
                        logtrace "fan already on"
                    }
                }

                logging """
fanCirculateAlways true
fanCirculateSimpleModeOnly = $fanCirculateSimpleModeOnly
inFanCirculateMode = $inFanCirculateMode
thermostat.currentValue("thermostatFanMode") = ${thermostat.currentValue("thermostatFanMode")}
"""
            }
        }

        if(enabledebug && now() - atomicState.EnableDebugTime > 1800000){
            descriptionText "Debug has been up for too long..."
            disablelogging() 
        }
        if(description && now() - atomicState.enableDescriptionTime > 86400000){
            descriptionText "Description text has been up for too long..."
            disabledescription() 
        }

        if(pw){
            logging("$pw power meter returns ${pw?.currentValue("power")}Watts")
        }
        if(!atomicState.override){

            /********************** END OF ANTIFREEZE TESTS *************************/



            /********************** VERIFY HEATPUMP AND POWER USAGE CONDITIONS (HEATER OR COOLER)*************************/
            boolean tooMuchPower = false
            if(controlPowerConsumption && atomicState.lastNeed == "heat" && pw && heater) // heater only
            {
                currentPower = pw?.currentValue("power").toInteger() + heater?.currentValue("power").toInteger()
                tooMuchPower = currentPower > maxPowerConsumption.toInteger()
                if(tooMuchPower)
                {
                    log.warn formatText("power consumption ${heater?.currentValue("power")!=0 ? "$heater +":""} $thermostat = $currentPower Watts", "white", "red")
                }
                else
                {
                    descriptionText formatText("power consumption ${heater?.currentValue("power")!=0 ? "$heater +":""} $thermostat = $currentPower Watts", "white", "lightgreen")
                }
                //tooMuchPower = devicePriority == "$thermostat" && heatpumpConditionsTrue ? true : tooMuchPower
                // redundant: if device priority is thermostat and heatpump conditions are true, then the thermostat will be shut down
                // what we need is to make sure that the alternate heater (mandatory if heatpump true) will kick in, which is tested by virtualThermostat method
            }
            if(coolerControlPowerConsumption && atomicState.lastNeed == "cool" && pw && cooler && !preferCooler) // cooler only and if not already prefered
            {
                currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
                tooMuchPower = currentPower > coolerMaxPowerConsumption.toInteger()
                if(tooMuchPower)
                {
                    log.warn formatText("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler +":""} $thermostat = $currentPower Watts", "white", "red")
                }
                else
                {
                    descriptionText formatText("power consumption ${cooler?.currentValue("power")!=0 ? "$cooler +":""} $thermostat = $currentPower Watts", "white", "lightgreen")
                }
            }

            //if heatpump and conditions for heatpump are met (too cold outside) OR too much power and priority is not thermostat 
            //Then keep the thermostat in off mode. 

            if((heatpumpConditionsTrue) || (tooMuchPower && devicePriority != "$thermostat"))
            {
                if(thermostat.currentValue("thermostatMode") != "off" && !fanCirculateAlways)
                {
                    if(okToTurnOff())
                    {
                        descriptionText "thermostat off 735h4ze6 heatpumpConditionsTrue = $heatpumpConditionsTrue"
                        turnOffThermostat() // so as to take precedence over any other condition 

                        atomicState.offAttempt = now()
                        log.info "$thermostat turned off due ${preferCooler && atomicState.lastNeed == "cool" ? "to preferCooler option" : "to heatpump or power usage conditions"}"
                    }
                }
            }

            // POWER USAGE CONSISTENCY TEST
            if(pw){
                atomicState.resendAttempt = atomicState.resendAttempt ? atomicState.resendAttempt : now()
                atomicState.offAttempt = atomicState.offAttempt ? atomicState.offAttempt : now()
                // here we manage possible failure for a thermostat to have received the z-wave/zigbee or http command
                long timeElapsedSinceLastResend = now() - atomicState.resendAttempt
                long timeElapsedSinceLastOff = now() - atomicState.offAttempt // when device driver returns state off while in fact signal didn't go through
                long threshold = 2 * 60 * 1000 // give power meter 2 minutes to have its power measurement refreshed before attempting new request 
                boolean timeIsUp = timeElapsedSinceLastResend > threshold
                boolean timeIsUpOff = timeElapsedSinceLastOff > threshold
                def pwVal = pw?.currentValue("power")
                boolean pwLow = pwVal < 100 // below 100 watts we assume there's no AC compression nor resistor heat running
                boolean timeToRefreshMeters = need == "off" ? timeElapsedSinceLastOff > 10000 && !pwLow : timeElapsedSinceLastResend > 10000 && pwLow
                logging("time since last Resend Attempt = ${timeElapsedSinceLastResend/1000} seconds & threshold = ${threshold/1000}sec")
                logging("time since last OFF Attempt = ${timeElapsedSinceLastOff/1000} seconds & threshold = ${30}sec")

                //this must not run in ignoreMode 
                if(timeToRefreshMeters && !timeIsUp && !timeIsUpOff && !ignoreMode) // make sure to attempt a refresh before sending more commands
                {
                    descriptionText "pwLow = $pwLow refreshing $pw because power is $pwVal while it should be ${need == "off" ? "below 100 Watts":"above 100 Watts"}"
                    pollPowerMeters()
                }
                else if(!ignoreMode && timeIsUp && pwLow && (need != "off" || !offrequiredbyuser))
                {
                    descriptionText "$app.label is resending ${cmd}(${target}) due to inconsistency in power value"
                    atomicState.resendAttempt = now() 
                    atomicState.setpointSentByApp = true
                    runIn(3, resetSetByThisApp)

                    if(cmd in ["setCoolingSetpoint", "setHeatingSetpoint", "setThermostatSetpoint"])
                    {

                        descriptionText "$thermostat $cmd 4gh5ze"
                        boolean inpowerSavingMode = location.mode in powersavingmode
                        if(ignoreSetPoint && !inpowerSavingMode)
                        {
                            logtrace "Target ($target) temp not sent to $thermostat at user's request"
                        }
                        else
                        {
                            thermostat."${cmd}"(target) // resend cmd
                        }


                    }
                    else
                    {
                        if(cmd.contains("off"))
                        {
                            if((!offrequiredbyuser && (!contactClosed || !motionActive)) || offrequiredbyuser)
                            {
                                descriptionText "resending off cmd"
                                turnOffThermostat()
                            }
                            else
                            {
                                descriptionText "power discrepancy but not re-sending off cmd at user's request"
                            }
                        }
                        else
                        {
                            log.warn "INVALID CMD for this device: $cmd"
                        }
                    }
                    //pollPowerMeters()
                }
                else if(timeIsUpOff && need == "off" && !pwLow && !doorsContactsAreOpen())
                {

                    atomicState.offAttempt = now() 
                    if(!fanCirculateAlways)
                    {                        
                        if(okToTurnOff() && !atomicState.userWantsWarmer && !atomicState.userWantsCooler)
                        {        
                            log.warn("$thermostat should be off but still draining power, resending cmd")
                            descriptionText "thermostat off 34t5zl"
                            turnOffThermostat()
                            atomicState.offAttempt = now()
                            thermostat.off()
                        }
                    }
                    else if(need == "off" && fanCirculateAlways)
                    {

                        if(!motionActive && alwaysButNotWhenPowerSaving)
                        {
                            if(thermMode != "off" || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10))
                            {
                                atomicState.dontcheckthermstateCount += 1
                                if(okToTurnOff())
                                {
                                    descriptionText "thermostat off fan auto 639t4js"
                                    thermostat.setThermostatFanMode("auto")          
                                    turnOffThermostat()
                                }
                            }
                        }
                        else
                        {
                            descriptionText "fancriculateAlways is true, so not turning off the thermostat despite power discrepancy. resending fanonly() instead"
                            if(okToTurnOff())
                            {                                
                                turnOffThermostat() // needed to prevent thermostat staying in "cool" or "heat" mode
                                descriptionText "thermostat fan on 47tyuz"
                                thermostat.setThermostatFanMode("on")
                            }
                        }
                    }
                }
                else if((!pwLow &&  need in ["heat", "cool"]) || (need == "off" && pwLow))
                {
                    logging("EVERYTHING OK")
                }
                else 
                {
                    logging("Auto Fix Should Kick in within time threshold")
                }
            }


            /****************END OF HEAT PUMP AND POWER MANAGEMENT FUNCTIONS *************************/

            /****************FAN CIRCULATION MANAGEMENT*************************/
            if(fan && motionActive && need == "cool") // only if motion active and cooling. If user wants it to run in no motion state, then this will be taken care of later down
            {
                def fanCmd = neverTurnOff ? "on" : fanWhenCoolingOnly && need == "cool" ? "on" : fanWhenHeatingOnly && need == "heat" ? "on" : "off" 

                if(fan?.currentValue("switch") != "fanCmd")
                {
                    fan?."${fanCmd}"()
                    descriptionText "$fan turned $fanCmd gt97"
                }
            }
            if(fanDimmer)
            {
                logging""" fanDimmer
neverTurnOffFanDimmer
inside = $inside
target = $target
need in ["cool", "heat"] = ${need in ["cool", "heat"]}
location.mode in silenceMode
"""
                boolean keepOnWindows = keepFanOnWhenWindowsOpen && !contactClosed
                boolean keepFandDimmerCoolerOutside = keepFanDimmerOnIfOutsideLowerThanInside && outside < inside && inside > 74

                def dimmerValue = need in ["cool", "heat"] || neverTurnOffFanDimmer || (keepOnWindows && atomiState.lastNeed != "heat") ? inside >= target + 4 || outside >= 85 ? maxFanSpeed : inside >= target + 2 ? mediumFanSpeed : lowFanSpeed : 0  
                dimmerValue = keepFandDimmerCoolerOutside ? maxFanSpeed : dimmerValue
                dimmerValue = location.mode in silenceMode ? silenceValue : dimmerValue
                if(fanDimmer.currentValue("level") != dimmerValue) fanDimmer?.setLevel(dimmerValue)
                descriptionText "$fanDimmer running at ${fanDimmer.currentValue("level")}%"
            }
            /****************END OF FAN CIRCULATION MANAGEMENT*************************/

            /****************CONSISTENCY TESTS AND EMERGENCY HEAT/COLD DUE TO A POSSIBLE BADLY LOCATED THERMOSTAT*************************/

            def currentOperatingNeed = need == "cool" ? "cooling" : need == "heat" ? "heating" : need == "off" ? "idle" : "ERROR" 
            if(currentOperatingNeed == "ERROR"){log.error "currentOperatingNeed = $currentOperatingNeed"}
            logging """currentOperatingNeed = $currentOperatingNeed && need = $need
thermostat.currentValue("thermostatOperatingState") = ${thermostat.currentValue("thermostatOperatingState")}
${thermostat.currentValue("thermostatOperatingState") == currentOperatingNeed}"""

            atomicState.lastSetTime = atomicState.lastSetTime != null ? atomicState.lastSetTime : now() + 31 * 60 * 1000
            def currentOperatingState = thermostat.currentValue("thermostatOperatingState")
            boolean OperatingStateOk = contactClosed && !doorContactsAreOpen ? currentOperatingState in [currentOperatingNeed, "fanCirculate"] : true

            double swing = UserSwing ? UserSwing.toDouble() : 0.5 // swing is the target amplitude set at the level of the thermostat directly (hardware setting) - user must specify, default is 0.5
            double undesirableOffset = 2
            // the problem is when the thermostat returns a temp that is too close to the target temp while the alt sensor is still too far from it
            double thermostatTemp = thermostat.currentValue("temperature").toDouble() 
            //boolean insideTempNotOk = need == "cool" ? inside > target + swing : need == "heat" ? inside < target - swing : false 
            // if need = cool and thermostatTemp >= target + swing that means the thermostat will stop cooling
            // if need = heat and thermostatTemp <= target - swing that means the thermostat will stop heating
            // so, if that happens while inside temperature is still far beyond or below the target temperature (+/- default swing), then
            // we want the app to increase the set point (if need is heat) or decrease it (if need is cool) 
            // so as to force the unit to continue to work until alternate sensors array measures an average inside temp that matches the target (+/- swing)
            boolean thermTempTooCloseToCoolTargetdWhileInsideNotGood = thermostatTemp == target && inside >= target && !OperatingStateOk
            boolean thermTempTooCloseToHeatTargetdWhileInsideNotGood = thermostatTemp == target && inside <= target && !OperatingStateOk
            def thermostatTempProblem = (need == "cool" && thermTempTooCloseToCoolTargetdWhileInsideNotGood ) || (need == "heat" && thermTempTooCloseToHeatTargetdWhileInsideNotGood)
            boolean thermTempDiscrepancy = manageThermDiscrepancy && sensor && thermostatTempProblem && contactClosed 

            logging """
currentOperatingState = $currentOperatingState
currentOperatingNeed = $currentOperatingNeed
OperatingStateOk = $OperatingStateOk
swing (user defined) = $swing
undesirableOffset = $undesirableOffset
thermostatTemp = $thermostatTemp
target + swing = ${target + swing}12
target - swing = ${target - swing}
thermTempTooCloseToCoolTargetdWhileInsideNotGood = $thermTempTooCloseToCoolTargetdWhileInsideNotGood
thermTempTooCloseToHeatTargetdWhileInsideNotGood = $thermTempTooCloseToHeatTargetdWhileInsideNotGood

"""

            // check cooler performance and turn thermostat back on (override preferCooler bool) if needed 
            atomicState.coolerTurnedOnTimeStamp = atomicState.coolerTurnedOnTimeStamp != null ? atomicState.coolerTurnedOnTimeStamp : 31*60*1000
            def efficiencyOffset = 2
            boolean coolerNotEfficientEnough = efficiencyOverride && preferCooler && now() - atomicState.coolerTurnedOnTimeStamp > 30*60*1000 && inside >= target + efficiencyOffset
            boolean boost = userBoostOffset && inside >= target + userBoostOffset 
            logging """
need = $need
preferCooler = $preferCooler
efficiencyOverride = $efficiencyOverride
preferCoolerLimitTemperature = $preferCoolerLimitTemperature
outside = $outside
outside < preferCoolerLimitTemperature = ${outside < preferCoolerLimitTemperature}
inside = $inside
target = $target 
inside >= target + $efficiencyOffset ? ${inside >= target + efficiencyOffset}
atomicState.coolerTurnedOnTimeStamp > 30*60*1000 ? ${atomicState.coolerTurnedOnTimeStamp > 30*60*1000}
coolerNotEfficientEnough = $coolerNotEfficientEnough
boost = $boost
userBoostOffset = $userBoostOffset

"""
            if(coolerNotEfficientEnough && need == "cool") log.warn formatText("$cooler not efficient enough, turning on $thermostat", "red", "white")

            if(need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)) // NO OVERRIDE WHEN preferCooler and need == "cool"
            {
                log.debug "EFFICIENCY TEST"
                checkEfficiency(coolerNotEfficientEnough, boost, thermMode)
            }
            else
            {
                //log.warn formatText("contactClosed = $contactClosed", "white", "red") 

                if(manageThermDiscrepancy && thermTempDiscrepancy && contactClosed)
                {                
                    atomicState.setPointOverride = true // avoids modifying target values (in setDimmer) and prevents the app from running other normal operations
                    // since it is reset at setDimmer() (to allow for new user's inputs), it must be set to true each time here, or other normal functions will run

                    def m = ""
                    def delayBtwMessages = 5*60000
                    atomicState.lastSetpointMessage = atomicState.lastSetpointMessage ? atomicState.lastSetpointMessage : now()
                    def timeBeforeNewtOverrideBigMessage = (delayBtwMessages - (now() - atomicState.lastSetpointMessage))/1000/60 
                    timeBeforeNewtOverrideBigMessage = timeBeforeNewtOverrideBigMessage.toDouble().round(2)
                    if(now() - atomicState.lastSetpointMessage > delayBtwMessages)
                    {
                        m = """SET POINT OVERRIDE - make sure your main thermostat is not too close to a window. 
If so, this app will attempt to keep your room at your target temperature ($target) by temporarily changing setpoints on your thermostat. 
This should not be affecting your input values (your target temperature)"""
                        atomicState.lastSetpointMessage = now()
                    }
                    else
                    {
                        def timeUnit = timeBeforeNewtOverrideBigMessage < 1 ? "seconds" : timeBeforeNewtOverrideBigMessage >= 2 ? "minutes" : "minute"
                        def timeDisplay = timeBeforeNewtOverrideBigMessage < 1 ? timeBeforeNewtOverrideBigMessage*100 : timeBeforeNewtOverrideBigMessage 
                        m = "SET POINT OVERRIDE (detailed description in ${timeDisplay} ${timeUnit})"
                    }

                    log.warn formatText(m, "red", "white")

                    def temporarySetpoint = need == "cool" ? 62 : need == "heat" ? 85 : 72 // 72 if by any chance this went wrong

                    def currCSP = thermostat.currentValue("coolingSetpoint").toInteger()
                    def currHSP = thermostat.currentValue("heatingSetpoint").toInteger()
                    boolean notSet = need == "cool" && currCSP != temporarySetpoint.toInteger() || need == "heat" && currHSP != temporarySetpoint.toInteger()

                    logging """
need = $need
currCSP = $currCSP | currCSP != temporarySetpoint.toInteger() ? ${currCSP.toInteger() != temporarySetpoint.toInteger()}
currHSP = $currHSP | currHSP != temporarySetpoint.toInteger() ? ${currHSP.toInteger() != temporarySetpoint.toInteger()}
temporarySetpoint = $temporarySetpoint
notSet = $notSet
"""
                    if(notSet)
                    {


                        logtrace "setting $thermostat to $need"

                        if(heatpumpConditionsTrue && need != "off")
                        {
                            descriptionText "$thermostat stays off due to heatpump and cold temp outside"
                        }
                        else
                        {
                            log.warn "$thermostat $cmd to temporarySetpoint $temporarySetpoint 478r6gh"  
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat."${cmd}"(temporarySetpoint)
                        }

                        if(need == "cool") // prevent thermostat firmware from circling down its setpoints
                        {
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setHeatingSetpoint(temporarySetpoint-2)
                            log.debug "$thermostat heatingsetpoint set to ${temporarySetpoint-2} to prevent circling down SP's"
                        }
                        else if(need == "heat") // prevent thermostat firmware from circling down its setpoints
                        {
                            atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // reset by setDimmer() method. 
                            runIn(3, resetSetByThisApp)
                            thermostat.setCoolingSetpoint(temporarySetpoint+2) 
                            log.debug "$thermostat coolingSetpoint set to ${temporarySetpoint+2} to prevent circling down SP's"
                        }

                        atomicState.lastSetTime = now()   
                        return 
                    }
                }
                else if(!thermTempDiscrepancy && atomicState.setPointOverride)
                {
                    atomicState.setPointOverride = false // if this line is read, then setpoint override is no longer needed
                    if(manageThermDiscrepancy) logtrace formatText("END OF SET POINT OVERRIDE - BACK TO NORMAL OPERATION", "white", "grey")
                }

                logging "forceCommand ? $forceCommand atomicState.forceAttempts = $atomicState.forceAttempts | abs(inside-target) = ${Math.abs(inside-target).round(2)}"
            }
            /****************END OF CONSISTENCY OR EFFICIENCY TESTS AND EMERGENCY HEAT/COLD DUE TO A POSSIBLE BADLY LOCATED THERMOSTAT*************************/

            /****************NORMAL EVALUATION WITH POSSIBLE NEED FOR REDUNDENT FORCED COMMANDS (possibly needed due to bad Z-Wave mesh)******/
            atomicState.forceLimit = Math.abs(inside-target) > 5 ? 20 : 5 // higher amount of attempts if bigger discrepancy         
            atomicState.forceAttempts = atomicState.forceAttempts != null ? atomicState.forceAttempts : 0
            boolean forceCommand = forceCommand ? (atomicState.forceAttempts < atomicState.forceLimit ? true : false) : false //
            forceCommand = forceCommand ? (need in ["cool", "heat"] && Math.abs(inside-target) > 3 ? true : false) : false // 
            forceCommand = !forceCommand && forceCommand && Math.abs(inside-target) >= 5 ? true : (forceCommand ? true : false) // counter ignored if forceCmd user decision is true and temp discrepancy too high: continue trying until temp is ok
            forceCommand = !forceCommand && !OperatingStateOk ? true : forceCommand // OperatingStateOk supercedes all other conditions
            forceCommand = contactClosed && !doorContactsAreOpen ? forceCommand : false // don't use this method when contacts are open, even door contacts


            // if forececommand true and need is not off make sure we're not under heat pump cold conditions 
            forceCommand = forceCommand && (need != "off" || !offrequiredbyuser) ? !heatpumpConditionsTrue : forceCommand // if need = off then apply forcecommand functions to make sure to turn it off

            //log.warn "ignoreTherMode = $ignoreTherMode"


            if(!atomicState.setPointOverride && (thermMode != need || forceCommand || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10)) && contactClosed)
            {
                atomicState.dontcheckthermstateCount = atomicState.dontcheckthermstateCount == null ? 1 : atomicState.dontcheckthermstateCount
                atomicState.dontcheckthermstateCount += 1
                if(forceCommand && OperatingStateOk) {logging "FORCING CMD TO DEVICE BECAUSE temperature difference is TOO HIGH"}
                if(forceCommand && !OperatingStateOk && !thermTempDiscrepancy) {logging "FORCING CMD TO DEVICE BECAUSE current operating state is INCONSISTENT"}

                atomicState.forceAttempts += 1
                if(atomicState.forceAttempts >= forceLimit) { runIn(1800, resetCmdForce)} // after 5 attempts, stop and retry in half an hour to prevent z-wave cmds overflow onto the device

                //atomicState.lastSetTime =  5 * 60 * 1000 + 1 // for TESTS ONLY
                logging """
preferCooler ? $preferCooler && outside < preferCoolerLimitTemperature =  $outside < $preferCoolerLimitTemperature
need != "off" || forceCommand || (need == "off" && (sensor || offrequiredbyuser)) = ${need != "off" || forceCommand || (need == "off" && (sensor || offrequiredbyuser))}
need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature) = ${need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature)}
thermMode = $thermMode
need = $need
thermMode != need = ${thermMode != need}
"""


                if((need != "off" || !offrequiredbyuser) || forceCommand || (need == "off" && (sensor || offrequiredbyuser)))
                {                
                    if((!OperatingStateOk || now() - atomicState.lastSetTime > 5 * 60 * 1000) || need == "off" || forceCommand)
                    {
                        atomicState.coolerTurnedOnTimeStamp = atomicState.coolerTurnedOnTimeStamp != null ? atomicState.coolerTurnedOnTimeStamp : 31*60*1000

                        if(need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature))
                        {
                            checkEfficiency(coolerNotEfficientEnough, boost, thermMode)
                        }
                        else 
                        {
                            logging """
1. ${need == "off" && fanCirculateAlways}
1.1. need = $need
1.2. fanciruclateAlways = $fanCirculateAlways
2. ${thermMode == need}
3. ${need == "off" && fanCirculateAlways}
4. ${need == "off" && offrequiredbyuser}
4.1. offrequiredbyuser = $offrequiredbyuser
5. ${thermostat.currentValue("thermostatFanMode")}

"""


                            if((thermMode != need || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10) || thermostat.currentValue("thermostatFanMode") != "auto") && need == "off" && offrequiredbyuser)
                            {
                                atomicState.dontcheckthermstateCount += 1
                                if(okToTurnOff())
                                {
                                    descriptionText "Sending off command to thermostat 4felf65"
                                    turnOffThermostat()
                                    thermostat.setThermostatFanMode("auto")
                                }
                            }
                            // if fanCirculateAlways is true, offrequiredbyuser will automatically be set to false in mainloop and/or within dynamic settings pages
                            else if((thermMode != need  || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10)) && (!fanCirculateAlways || (need != "off" || !offrequiredbyuser)))
                            { 
                                atomicState.dontcheckthermstateCount += 1
                                boolean okToOff = need == off ? okToTurnOff() : true
                                if(okToOff)
                                {
                                    if(heatpumpConditionsTrue && need != "off")
                                    {
                                        descriptionText "heatpumpConditionsTrue 5z4rg4"
                                    }

                                    else if(thermMode != need || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10))
                                    {
                                        atomicState.dontcheckthermstateCount += 1
                                        if(!ignoreTherMode) 
                                        {
                                            logtrace "thermostat set to $need g5h4hr "
                                            thermostat.setThermostatMode(need) // set needed mode
                                        }
                                        else
                                        {
                                            logtrace "ignoreTherMode true"
                                        }

                                    }
                                    else 
                                    {
                                        logtrace "thermostat already set to $need etgh4ze5"
                                    }
                                }
                            }
                            else if(thermMode == need)
                            {
                                descriptionText "$thermostat already set to $need 5z4gth"
                            }
                            else if(need == "off" && fanCirculateAlways)
                            {
                                if(!motionActive && alwaysButNotWhenPowerSaving)
                                {
                                    if(thermMode != "off" || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10))
                                    {
                                        atomicState.dontcheckthermstateCount += 1
                                        thermostat.setThermostatFanMode("auto")
                                        if(okToTurnOff())
                                        {
                                            descriptionText "thermostat off 4n2r4zk"
                                            turnOffThermostat()
                                        }
                                    }
                                }
                                else
                                {
                                    descriptionText "fancriculateAlways is true, so not turning off the thermostat. sending fanonly() instead"
                                    if(okToTurnOff())
                                    {
                                        descriptionText "thermostat off 96th34z"
                                        turnOffThermostat() // needed to prevent thermostat staying in "cool" or "heat" mode
                                        log.warn "fan mode on"
                                        thermostat.setThermostatFanMode("on")
                                    }
                                }
                            }


                        }

                        atomicState.lastSetTime = now()

                        if(need in ["cool", "heat"])
                        {
                            atomicState.lastSetTime = now() // prevent switching from heat to cool too frequently 
                            //during conditions that might have been unacounted for, like during shoulder season
                        }

                        logging "THERMOSTAT SET TO $need mode (587gf)"
                    }
                    else if(now() - atomicState.lastSetTime < 30 * 60 * 1000)
                    {
                        logging "THERMOSTAT CMD NOT SENT due to the fact that a cmd was already sent less than 30 minutes ago"
                    }

                    if(need == "off")
                    {
                        atomicState.offAttempt = now() as long

                            }
                }
                else 
                {
                    logging("THERMOSTAT stays in $thermMode mode")
                }

            }
            else if((need != "off" || !offrequiredbyuser) && contactClosed)
            {
                descriptionText "$thermostat already set to $need 8ryjyuz"
            }

            //logtrace "atomicState.setPointOverride = $atomicState.setPointOverride"
            if(!atomicState.setPointOverride)
            {
                logging """ 
need = $need
currSP = $currSP
target = $target
currSP != target ? ${currSP != target.toInteger()}
thermMode = $thermMode 
thermMode == need ? ${thermMode == need}
currSP != target || thermMode != need => ${currSP != target || thermMode != need}
thermTempDiscrepancy = $thermTempDiscrepancy 
"""
                def fanMode = thermostat.currentValue("thermostatFanMode")

                if((need != "off" || !offrequiredbyuser || simpleModeIsActive()) && (currSP != target || thermoMode != need) && !thermTempDiscrepancy && contactClosed)
                {
                    //log.warn "need = $need last need = $atomicState.lastNeed **************** "
                    cmd = need == "off" ? "set"+atomicState.lastNeed.capitalize()+"ingSetpoint" : cmd // restore the last need if need = off
                    //log.warn "cmd = $cmd ${atomicState.lastNeed.capitalize()+"ingSetpoint"} +++"
                    atomicState.setpointSentByApp = true
                    runIn(3, resetSetByThisApp)
                    boolean inpowerSavingMode = location.mode in powersavingmode
                    if(ignoreSetPoint && !inpowerSavingMode)
                    {
                        logtrace "Target ($target) temp not sent to $thermostat at user's request"
                    }
                    else if(currSP != target || thermMode != need)
                    {                        
                        //log.warn ""

                        if(need == "off" && ignoreMode) 
                        {
                            descriptionText "need is $need but not sending cmd due to ignoreMode (only off, cool/heat still need to be sent in case it's off or in the wrong mode and before overriding with setpoint"
                            logtrace "fanCirculateAlways is $fanCirculateAlways"

                            if(fanCirculateAlways || (alwaysButNotWhenPowerSaving && !inpowerSavingMode && Active() && !contactsAreOpen()))
                            {
                                if(fanMode != "on") 
                                { 
                                    thermostat.setThermostatFanMode("on")  
                                    descriptionText "fan set to 'on'" 
                                }
                                else 
                                {
                                    descriptionText "fan already on"
                                }

                            }
                            else if(fanMode == "on" && !fanCirculateAlways)
                            {
                                descriptionText "thermostat fan mode set back to auto" 
                                thermostat.setThermostatFanMode("auto")
                            }
                        }
                        else if(!ignoreMode)
                        {
                            descriptionText "Thermostat set to $need (5rrgh4klt5ui)"
                            thermostat.setThermostatMode(need); 
                        }

                        logtrace """
fanMode = $fanMode
thermMode = $thermMode
need = $need
"""
                        if(currSP != target || (thermMode != need && need != "off")) 
                        {
                            thermostat."${cmd}"(target)   // set target temp AFTER the mode for ignoreMode to work as intended when enabled
                            descriptionText "THERMOSTAT SET TO $target (564fdevrt)"
                        }
                        else {
                            def m = "THERMOSTAT ALREADY SET TO $target "
                            if(fanMode == "on" && need == "off") m = "THERMOSTAT IN FAN CIRCULATE OPERATION "
                            descriptionText m
                        }
                    }

                    atomicState.resendAttempt = now() // needs to be updated here otherwise it'll resend immediately after since last one was long ago at this point

                }
                else if((need != "off" || !offrequiredbyuser) && !thermTempDiscrepancy)
                {
                    descriptionText("Thermostat already set to $target 47ry6ze")
                }
                else if(thermTempDiscrepancy)
                {
                    log.warn "Skipping normal setpoint and thermostatMode management due to thermTempDiscrepancy = $thermTempDiscrepancy"   
                }
            }
            else
            {
                log.warn "Skipping normal set point management due to set point override"   
            }

            virtualThermostat(need, target) // redundancy due to return statement above
        }
        else{
            descriptionText("OVERRIDE MODE--------------")   
        }
    }
    else if(atomicState.restricted){
        log.info "app in restricted mode, doing nothing"
    } 
}

def checkEfficiency(coolerNotEfficientEnough, boost, thermMode){


    log.debug """
coolerNotEfficientEnough = $coolerNotEfficientEnough
boost = $boost
thermMode = $thermMode
"""

    if(!coolerNotEfficientEnough && (thermMode != "off" || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10)) && !boost)
    {
        atomicState.dontcheckthermstateCount += 1
        log.debug """preferCooler = true and need = cool
thermosat kept off ${preferCoolerLimitTemperature ? "unless outside temperature reaches $preferCoolerLimitTemperature":""} 56er"""
        if(!fanCirculateAlways)
        {
            if(okToTurnOff())
            {
                descriptionText "thermostat off 5fh4z2"
                turnOffThermostat()
                atomicState.offAttempt = now()
            }
        }
    }
    else if(coolerNotEfficientEnough || boost)
    {
        logtrace "${boost ? "boosting with $thermotat at user's request" : "$cooler is not efficient enough turning $thermostat back on"} 44JKD"
        if(thermMode != "cool" || (dontcheckthermstate && atomicState.dontcheckthermstateCount < 10))
        {
            atomicState.dontcheckthermstateCount += 1
            log.warn "cooler not efficient enough 5zr4z8h"
            thermostat.setThermostatMode("cool") // will run as long as inside > target + efficiencyOffset
            atomicState.resendAttempt = now()
        }
    }
}
def flashTheLight(){

    def lastState = lightSignal.currentValue("switch")
    def previousColor = lightSignal.currentValue("colorTemperature") //[hue: lightSignal.currentValue("hue").toInteger(), saturation: lightSignal.currentValue("saturation").toInteger(), level: lightSignal.currentValue("level").toInteger()]
    lightSignal.on()
    pauseExecution(500)


    logging("${lightSignal.getCapabilities()}") //weirdly required otherwise platform doesn't read colorControl capability... pb raised on the forums, never resolved by Hubitat's staff, for some reason. 
    boolean colorControlCap = lightSignal?.hasCapability("ColorControl")
    log.debug "colorControlCap = $colorControlCap"

    if(nightModeColor && colorControlCap)
    {
        log.debug "previous color : $previousColor"

        //https://www.peko-step.com/en/tool/hsvrgb_en.html and then SELECT RANGE 0..100

        def red = [hue:0, saturation:100, level:100]
        def blue = [hue:66, saturation:100, level:100]
        def green = [hue:32, saturation:100, level:100]
        def white = [hue:20, saturation:40, level:100]
        //log.warn "nightModeColor = $nightModeColor"
        def theColor = nightModeColor == "red" ? red : nightModeColor == "blue" ? blue : nightModeColor == "green" ? green : white
        //log.debug "setting $lightSignal color to $theColor"
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



    if(nightModeColor && colorControlCap && setPreviousColor) 
    {
        log.debug "restoring previous color"
        previousColor = previousColor ? previousColor : "white" 
        lightSignal?.setColorTemperature(previousColor) 
        pauseExecution(500)
    }
    if(turnSignalLightOffAfter)
    {
        lightSignal.off()
    }
    else
    {
        lightSignal."${lastState}"()
    }
}
def sendNotification(){
    def message = "${thermostat}'s temperature is too low. Antifreeze is now active."

    atomicState.lastNotification = atomicState.lastNotification != null ? atomicState.lastNotification : now()

    def dTime = 5*60*1000 // every 5 minutes

    if(now() - atomicState.lastNotification >= dTime)
    {
        atomicState.lastNotification = now()

        def musicDeviceList = musicDevice ? buildDebugString(musicDevice) : ""  // build a list of the devices as string
        def speechDeviceList = speech ? buildDebugString(speech) : ""
        def notifDevices = notification ? buildDebugString(notification) : ""

        def notifLogs = "${notification && speaker && speech ? "to ${notifDevices}, ${speakers}, ${speechDeviceList}" : notification && speaker ? "to ${notifDevices}, ${musicDeviceList}" : notification && speech ? "to ${notifDevices}, ${speechDeviceList}" : speaker && speech ? "to ${speakers}, ${speechDeviceList}" : speaker ? "to ${musicDeviceList}" : speech ? "to ${speechDeviceList}" : ""}" 

        def debugMessage = "message to be sent: '${message} ${notifLogs}" 

        descriptionText formatText(debugMessage, "white", "red")

        if(notification)
        {
            notification.deviceNotification(message)
        }
        else
        {
            log.info "User did not select any text notification device"
        }
        if(musicDevice || speech)
        {
            if(musicDevice)
            {
                if(initializeDevices)
                {
                    int i = 0
                    int s = musicDevice.size()
                    def device = []
                    for(s!=0;i!=s;i++)
                    {
                        device = musicDevice[i]                        
                        if(device.hasCommand("initialize"))
                        {
                            logging "Initializing $device (musicDevice)"
                            device.initialize()
                            logging "waiting for 1 second"
                            pauseExecution(1000)
                        }
                    }
                }

                int i = 0
                int s = musicDevice.size()
                def level
                for(s!=0;i!=s;i++)
                {
                    log.debug "Sending message to $device"
                    device = musicDevice[i] 
                    level = device.currentValue("level") // record value for later restore   
                    log.debug "$device volume level is $level"
                    device.setLevel(volumeLevel.toInteger()) // set target level // for some reason this doesn't work
                    pauseExecution(500)// give it time to go through
                    device.playText(message) // send the message to play
                    device.setLevel(level.toInteger()) // restore previous level value
                }
                return
            }
            if(speech)
            {
                if(initializeDevices)
                {
                    int i = 0
                    int s = speech.size()
                    def device 
                    for(s!=0;i!=s;i++)
                    {
                        device = speech[i]
                        if(device.hasCommand("initialize"))
                        {
                            log.debug "Initializing $device (speech)"
                            device.initialize()
                            log.debug "wainting for 1 second"
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
    if(s != 0) { 

        for(s!=0; i!=s; i++)
        {
            devices += "${deviceList[i]}, "   
        }

    }
    return devices
}
def resetCmdForce(){
    log.trace "Resetting forceCommand counter"
    atomicState.forceAttempts = 0   
}
def setDimmer(val){
    log.warn "setDimmer $val"
    if(!atomicState.setPointOverride)
    {
        if(dimmer)
        {
            atomicState.setpointSentByApp = true
            runIn(3, resetSetByThisApp)
            dimmer.setLevel(Math.round(Double.parseDouble(val.toString()))) // some thermostats will parse set points as double. Here, to parse as double, first we need to parse as string, hence toString()
            //so it needs to be rounded so as to be parsed as a string in the dimmer driver        
            descriptionText "$dimmer set to $val BY THIS APP"
        }
        else
        {
            def thisVal = Math.round(Double.parseDouble(val.toString()))
            atomicState.lastThermostatInput = thisVal            
            //atomicState.setpointSentByApp = true   // not applicable in this case since it won't trigger any device event
            descriptionText "atomicState.lastThermostatInput set to $thisVal"
        }
    }
    else
    {
        logtrace "SETPOINT OVERRIDE DUE TO THERMOSTAT DISCREPANCY NOT CHANGING DIMMER VALUE"
    }

    atomicState.setPointOverride = false


}
def virtualThermostat(need, target){

    logging "virtualThermostat need = $need atomicState.lastNeed = $atomicState.lastNeed"

    def outsideTemperature = outsideTemp?.currentValue("temperature") // only needed if electric heater here
    def lowTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? getCelsius(28) : 28 : celsius ? getCelsius(40) : 40 
    def highTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? getCelsius(28) : 28 : celsius ? getCelsius(40) : 40 
    boolean lowLimitReached = heatpump && !addLowTemp ? true : !thermostat ? true : (heater || heatpump) && addLowTemp ? outsideTemperature < lowTemperature : true 
    //if heatpump, lowLimitReached is when it's too cold outside for a heatpump to remain efficient, or if threshold has been reached so the heater has to take over, if any...
    //if heater and no heatpump, lowLimitReached is when it's so cold that heater has to come and help
    //if heater AND no thermostat, heater runs all the time when needed, no low limit so lowLimitReached returns true

    boolean inAllHeatSourcesMode = useAllHeatSourcesWithMode && location.mode in allHeatModes 
    boolean altHeatExclusiveMode = doNotUseMainThermostatInCertainModes && location.mode in altThermostatORheaterOnlyModes || altThermostatOnlyInSimpleMode && simpleModeIsActive()
    boolean ignoreSetPoint = simpleModeIsActive() && doNotIgnoreTargetInNightMode ? false : ignoreTarget 
    boolean ignoreTherMode = dontSetThermModes && simpleModeIsActive() || ignoreMode

    if(heater || altThermostat || inAllHeatSourcesMode || altHeatExclusiveMode)
    {
        boolean tooMuchPower = false
        if(controlPowerConsumption && atomicState.lastNeed == "heat")
        {
            if(heater?.hasCapability("powerMeter") || heater?.hasAttribute("power"))
            {
                def pwVal = !pw ? 0 : pw?.currentValue("power")?.toInteger()
                def heaterVal = !heater ? 0 : heater?.currentValue("power")?.toInteger()
                def currentPower = pwVal + heaterVal
                tooMuchPower = currentPower > maxPowerConsumption.toInteger()
                tooMuchPower = devicePriority != "$heater" && heatpump && outsideTemperature < lowTemperature ? false : tooMuchPower
            }
            else
            {
                log.warn "$heater doesn't have power measurement capability"
                app.updateSetting("controlPowerConsumption", [type:"bool", value:false])
                tooMuchPower = false
            }

            if(tooMuchPower) 
            {
                logtrace "both $thermostat and $heater are using too much power but heater needs to stay on due to outside temperature being too low for the heat pump to remain efficient. $thermostat should be off" 
            }
            // if device priority is not the heater while outside temp is low and heatpump true, we need to keep the heater on so tooMuchPower must be false
            // in the unlikely but still possible case where the thermostat is not already off 
        }
        if(tooMuchPower && devicePriority != "$heater") // if thermosat isn't priority it's overriden if it's a heatpump and low temp outside is true
        {
            need = "off"
            log.warn "$thermostat and $heater use too much power at the same time. Turning $heater off since $thermostat takesh precedence"
        }


        if(need == "heat" && atomicState.lastNeed == "heat")
        {
            if(lowLimitReached || useAllHeatSources || inAllHeatSourcesMode || altHeatExclusiveMode)
            {
                if(heater?.currentValue("switch") == "off")
                {
                    heater?.on()    
                    def m = heater && altThermostat ? "Turning on heater and setting $altThermostat to heat" : heater ? "Turning on $heater 54d54" : altThermostat ? "setting $altThermostat to heat" : "no alternate heater or thermostat to manage"
                    descriptionText "$m"  
                }
                else {
                    logtrace("heater is on")
                }
                if(altThermostat)
                {
                    if(altThermostat?.currentValue("thermostatMode") != "heat" || altThermostat?.currentValue("heatingSetpoint") != target)
                    {
                        logtrace "$altThermostat set to heat and $target 5zrgj5r"
                        if(!ignoreTherMode)
                        {
                            altThermostat?.setThermostatMode("heat")
                        }
                        else
                        {
                            logtrace "ignoreTherMode true 6rer5r4z63"
                        }
                        boolean inpowerSavingMode = location.mode in powersavingmode
                        if(ignoreSetPoint && !inpowerSavingMode)
                        {
                            logtrace "Target ($target) temp not sent to $altThermostat at user's request"
                        }
                        else
                        {
                            logtrace "$altThermostat heat set to $target 54zgy8ui6"
                            altThermostat?.setHeatingSetpoint(target)
                        }
                    }
                }
            }
            else if(useAllHeatSourcesWithMode && !inAllHeatSourcesMode)
            {
                log.trace "outside of useAllHeatSourcesWithMode modes ${altThermostat && heater ? "$altThermostat & $heater stay off" : altThermostat ? "$altThermostat stays off" : heater ? "$heater stays off" : ""} "
            }
            else if(heater)
            {
                descriptionText "$heater not turning on because low temp limit outside hasn't been reached yet"
                if(heater) logging("Turning $heater off dzgz5h")
                heater?.off()
                altThermostat?.setThermostatMode("off")
            }
        }
        else 
        {
            if(heater) logging("Turning $heater off 5gz4")
            heater?.off()
            altThermostat?.setThermostatMode("off")
        }
    }
    if(cooler)
    {
        boolean tooMuchPower = false
        boolean powerCapable = cooler?.hasCapability("powerMeter") || cooler?.hasAttribute("power")

        if(preferCooler)
        {
            // user wants the cooler to run as main device and thermostat as secondary device
            // don't change the need value
        }
        else if(coolerControlPowerConsumption && atomicState.lastNeed == "cool")
        {
            if(powerCapable)
            {
                def currentPower = pw?.currentValue("power").toInteger() + cooler?.currentValue("power").toInteger()
                tooMuchPower = CurrentPower > coolerMaxPowerConsumption.toInteger()
                tooMuchPower = coolerDevicePriority != "$cooler" && outsideTemperature > lowTemperature ? false : tooMuchPower
            }
            else
            {
                log.warn "$cooler doesn't have power measurement capability"
                app.updateSetting("coolerControlPowerConsumption", [type:"bool", value:false])
                tooMuchPower = false
            }

            if(tooMuchPower && coolerDevicePriority != "$cooler") // if thermosat isn't priority it's overriden if high temp outside is true
            {
                need = "off"
                log.warn "$thermostat and $cooler use too much power at the same time. Turning $cooler off since $thermostat has precedence"
            }
        }

        atomicState.coolCmdSent = atomicState.coolCmdSent == null ? false : atomicState.coolCmdSent
        atomicState.offCmdSent = atomicState.offCmdSent == null ? false : atomicState.offCmdSent

        def coolerCurVal = cooler?.currentValue("switch")
        def powerDiscrepancy = false
        def powerValue = null
        if(powerCapable)
        {
            powerValue = cooler?.currentValue("power")
            powerDiscrepancy = atomicState.coolCmdSent && need == "cool" && powerValue < 100 ? true : atomicState.offCmdSent && need == "off" && powerValue > 100 ? true : false
        }

        logtrace "coolerCurVal = ${coolerCurVal} ${powerCapable ? "powerValue = $powerValue" : ""} powerDiscrepancy = $powerDiscrepancy"


        if(need == "cool"){
            if(coolerCurVal != "on" || powerDiscrepancy)
            {
                logtrace "${powerDiscrepancy ? "POWER DISCREPANCY: turning on $cooler AGAIN" : "turning on $cooler 89e4"}"
                cooler?.on()
                atomicState.coolerTurnedOnTimeStamp = now()
                atomicState.coolCmdSent = true
                atomicState.offCmdSent = false
                //pollPowerMeters()
            }
            else 
            {
                log.info "$cooler already on"
            }
        }
        //////////////////
        if(need == "off"){
            if(coolerCurVal != "off" || powerDiscrepancy)
            {
                logtrace "${powerDiscrepancy ? "POWER DISCREPANCY: turning off $cooler AGAIN" : "turning off $cooler"}"
                cooler?.off()
                atomicState.coolCmdSent = false
                atomicState.offCmdSent = true
                //pollPowerMeters()
            }
            else 
            {
                logging "$cooler already off"
            }
        }

    }
}
def windowsControl(target, simpleModeActive, inside, outsideTemperature, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh){

    def outside = outsideTemperature //...

    // motionActive argument not sent through because in getNeed() it's mixed with simpleModeActive
    // so that would open windows every time $simpleModeName Mode would be active instead of cooling the room with AC as needed

    //atomicState.lastClosingTime = 360000000 //TESTS

    if(controlWindows && windows && (!simpleModeActive || allowWindowsInSimpleMode) && !atomicState.override)
    {
        if(location.mode in windowsModes)
        {
            // do nothing
        }
        else if(closeWhenOutsideWindowsModes && (windows.any{it -> it.currentValue("switch") == "on"} || windows.any{it -> it.currentValue("contact") == "open"}))
        {
            log.trace "outside windows mode"
            if(!atomicState.windowsClosedDueToOutsideMode)
            {
                if(atomicState.openByApp) windows?.off()
                log.trace "closing windows because outside windows mode"
                atomicState.windowsClosedDueToOutsideMode = true
                atomicState.openByApp = false
                atomicState.closedByApp = true
            }
            else
            {
                descriptionText "windows already closed by location mode management"
            }
            return;
        }
        atomicState.windowsClosedDueToOutsideMode = false // reset this value once the return statement is no longer called since location is in windows modes again

        def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp
        boolean tooHumid = humidity >= 90 ? true : humidity >= humThres 
        boolean contactCapable = windows.any{it -> it.hasCapability("ContactSensor")}//?.size() == windows.size() 
        boolean someAreOff =  contactCapable ? (windows.findAll{it?.currentValue("contact") == "closed"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "off"}?.size() > 0)
        boolean someAreOpen = contactCapable ? (windows.findAll{it?.currentValue("contact") == "open"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "on"}?.size() > 0)
        boolean withinRange = outsideTemperature < outsidetempwindowsH && outsideTemperature > outsidetempwindowsL // strict temp value

        boolean outsideWithinRange = withinRange && !tooHumid // same as withinRange but with humidity

        atomicState.lastOpeningTime = atomicState.lastOpeningTime != null ? atomicState.lastOpeningTime : now() // make sure value is not null
        atomicState.outsideTempAtTimeOfOpening = atomicState.outsideTempAtTimeOfOpening  != null ? atomicState.outsideTempAtTimeOfOpening : outsideTemperature // make sure value is not null
        boolean outsideTempHasDecreased = outsideTemperature < atomicState.outsideTempAtTimeOfOpening - swing // serves mostly to reset opening time stamp
        atomicState.outsideTempAtTimeOfOpening = outsideTempHasDecreased ? outsideTemperature : atomicState.outsideTempAtTimeOfOpening // if outsideTempHasDecreased true, reset outsidetemAtTimeOfOpening stamp so to use outsideTempHasDecreased only once 
        atomicState.lastOpeningTime = outsideTempHasDecreased ? now() : atomicState.lastOpeningTime // reset opening time stamp if it got cooler outside, allowing more time to cool the room

        atomicState.insideTempAtTimeOfOpening = atomicState.insideTempAtTimeOfOpening ? atomicState.insideTempAtTimeOfOpening : inside // make sure value is not null
        boolean insideTempHasIncreased = inside > atomicState.insideTempAtTimeOfOpening + swing // serves for windows wider opening ONLY
        atomicState.insideTempHasIncreased = insideTempHasIncreased
        atomicState.widerOpeningDone = (atomicState.widerOpeningDone != null) ? atomicState.widerOpeningDone : (atomicState.widerOpeningDone = false) // make sure value is not null
        boolean openMore = !atomicState.widerOpeningDone && insideTempHasIncreased && someAreOpen

        boolean insideTempIsHopeLess = inside > atomicState.insideTempAtTimeOfOpening + 2 && atomicState.widerOpeningDone

        double lastOpeningTime = (now() - atomicState.lastOpeningTime) / 1000 / 60 
        lastOpeningTime = lastOpeningTime.round(2)
        boolean openSinceLong = lastOpeningTime > 30 && someAreOpen // been open for more than 15 minutes

        atomicState.lastClosingTime = atomicState.lastClosingTime ? atomicState.lastClosingTime : (atomicState.lastClosingTime = now()) // make sure value is not null
        double lastClosingTime = (now() - atomicState.lastClosingTime) / 1000 / 60 
        lastClosingTime = lastClosingTime.round(2)
        boolean closedSinceLong = lastClosingTime > 30 && someAreClosed // been closed for more than 30 minutes

        boolean tooColdInside = inside <= target - 8 
        //log.warn "tooColdInside = $tooColdInside : inside = $inside && target = $target"
        //closing error management for safety, if cmd didn't go through for whatever reason and temp went too low, force close the windows
        boolean exception = someAreOpen && ((atomicState.closedByApp && now() - lastClosingTime > 30 && tooColdInside) || (!outsideWithinRange && tooColdInside))
        long elapsed = now() - lastClosingTime
        def elapsedseconds = elapsed/1000
        def elapsedminutes = elapsed/1000/60
        if(exception) {log.warn "$windows still open! EMERGENCY CLOSING WILL BE ATTEMPTED"}

        // allow for more frequent window operation when outside temp might be low enough to cool the room fast
        boolean outsideSubstantiallyLowEnough = outside < 72 && outside > outsidetempwindowsL
        float timeBtwWinOp = outsideSubstantiallyLowEnough ? 5 : 15// 5 min if it's cool enough outside, otherwise, give it 15 min before reopening

        boolean enoughTimeBetweenOpenAndClose = ((now() - atomicState.lastOpeningTime) / 1000 / 60) > 10.0 || inside < target - swing //-> give it a chance to cool down the place
        boolean enoughTimeBetweenCloseAndOpen = ((now() - atomicState.lastClosingTime) / 1000 / 60) > timeBtwWinOp //-> don't reopen too soon after closing

        boolean needToClose = (enoughTimeBetweenOpenAndClose  && ((inside > target + (swing * 3) && openSinceLong) || inside < target - swing || insideTempIsHopeLess)) || !outsideWithinRange
        boolean needToOpen = (enoughTimeBetweenCloseAndOpen && (inside > target + swing && !needToClose)) && outsideWithinRange //|| amplitudeTooHigh) // timer ok, too hot inside + within range (acounting for humidity) and no discrepency


        if((INpwSavingMode || !Active()) && outsideWithinRange)
        {
            needToOpen = atomicState.lastNeed == "cool" && inside >= 74 && outside < inside + 2 && !tooHumid
            descriptionText "${needToOpen ? "${windows.join(", ")} open to regulate temp while in power saving mode" : "windows are to remain closed"}"
        }

        def logs = """ **********************WINDOWS************************
inWindowsModes = $inWindowsModes
${windows.join(",")} ${contactCapable ? "${(windows.size() > 1) ? "have":"has"} contact capability" : "${(windows.size() > 1) ? "don't have":"doesn't have"} contact capability"}
closed: ${windows.findAll{it?.currentValue("contact") == "closed"}.join(",")}
Open: ${windows.findAll{it?.currentValue("contact") == "open"}.join(",")}
atomicState.openByApp = $atomicState.openByApp
atomicState.closedByApp = $atomicState.closedByApp
withinRange (stritcly): $withinRange
humidity >= humThres  : ${humidity >= humThres}
outsideWithinRange = $outsideWithinRange [range: $outsidetempwindowsL <> $outsidetempwindowsH] ${tooHumid ? "Too humid" : ""}
insideTempHasIncreased = $insideTempHasIncreased
atomicState.outsideTempAtTimeOfOpening = $atomicState.outsideTempAtTimeOfOpening
atomicState.insideTempAtTimeOfOpening = $atomicState.insideTempAtTimeOfOpening
insideTempIsHopeLess = $insideTempIsHopeLess ${insideTempIsHopeLess ? "temp went from: $atomicState.outsideTempAtTimeOfOpening to $inside" : ""}
amplThreshold = $amplThreshold
someAreOff = $someAreOff
someAreOpen = $someAreOpen
last time windows were OPEN = at $atomicState.lastOpeningTimeStamp ${lastOpeningTime < 2 ? "less than 1 minute ago" : (lastOpeningTime < 60 ? "${lastOpeningTime} minutes ago" : (lastOpeningTime < 60*2 ? "${(lastOpeningTime/60).round(2)} hour ago" : "${(lastOpeningTime/60).round(2)} hours ago"))}
last time windows were CLOSED = $atomicState.lastClosingTimeStamp ${lastClosingTime < 2 ? "less than 1 minute ago" : (lastClosingTime < 60 ? "${lastClosingTime} minutes ago" : (lastClosingTime < 60*2 ? "${(lastClosingTime/60).round(2)} hour ago" : "${(lastClosingTime/60).round(2)} hours ago"))}
humThres = ${humThres}
humidity = ${humidity}%
tooHumid = $tooHumid
openMore = $openMore
target = $target
outside = $outside
inside = $inside
swing = $swing
inside > target + (swing * 2) : ${inside > target + (swing * 2)}
inside > target + swing : ${inside > target + swing}
inside < target - swing       : ${inside < target - swing}
enoughTimeBetweenOpenAndClose : $enoughTimeBetweenOpenAndClose
enoughTimeBetweenCloseAndOpen : $enoughTimeBetweenCloseAndOpen
outsideSubstantiallyLowEnough = $outsideSubstantiallyLowEnough // allows to bypass enoughTimeBetweenCloseAndOpen 
outsidetempwindowsL =  $outsidetempwindowsL
outsidetempwindowsH = $outsidetempwindowsH
lastOpeningTime = $lastOpeningTime minutes ago ${outsideTempHasDecreased ? "value was reset to 0 because outsideTempHasDecreased = true (outsideTempHasDecreased = $outsideTempHasDecreased" : ""}
lastClosingTime = $lastClosingTime minutes ago
openSinceLong = $openSinceLong
temperature at last window opening = $atomicState.outsideTempAtTimeOfOpening
now() = ${now()}
atomicState.lastOpeningTime = $atomicState.lastOpeningTime 
atomicState.outsideTempAtTimeOfOpening = $atomicState.outsideTempAtTimeOfOpening  
atomicState.widerOpeningDone = $atomicState.widerOpeningDone
atomicState.lastNeed = $atomicState.lastNeed

needToOpen = $needToOpen
needToClose = $needToClose
*****************************************************
"""

 /*  def causeClosing = "${needToClose ? "WINDOWS CLOSED OR CLOSING BECAUSE: ${enoughTimeBetweenOpenAndClose && inside > target + (swing * 2) && openSinceLong ? "enoughTimeBetweenOpenAndClose && inside > target + (swing * 2) && openSinceLong" : inside < target - swing ? "inside < target - $swing" : !outsideWithinRange ? "!outsideWithinRange" : insideTempIsHopeLess ? "insideTempIsHopeLess" : !someAreOpen ? "Already closed" : atomicState.lastNeed == "heat" ? atomicState.lastNeed : "FIRE THE DEVELOPER IF THIS MESSAGE SHOWS UP"}":""}"*/

        logging formatText(logs, "white", "green")

       

/*
        log.warn"********************************TESTING*********************************"
        
         atomicState.lastTest = atomicState.lastTest == null ? now() : atomicState.lastTest
        if(now() - atomicState.lastTest > 10000)
        {
            windows[0].on()
            atomicState.lastTest = now() 
        }
        else 
        {
            log.warn "./............................................................ ./ / ??"
            
        }
        return 
        needToOpen = true
        someAreOff = true
        atomicState.closedByApp = true

        log.debug "needToOpen ================== $needToOpen"
        log.debug "--------------inWindowsModes $inWindowsModes-------------------------------"
        log.debug "atomicState.closedByApp ---------> $atomicState.closedByApp"


        log.warn"********************************TESTING*********************************"

*/
        if(inWindowsModes || exception){

            def time = maxDuration ? getWindowsTimeOfOperation(outsideTemperature, maxDuration, windowsDuration) : 30 // if !maxDuration time will be refined below for each individual window if needed

            if(needToOpen) // outsideWithinRange and humidity level are accounted for in needToOpen boolean, unless in power saving mode
            {
                descriptionText "using $windows INSTEAD OF AC"

                if(someAreOff || openMore)
                {
                    if(openMore) {
                        atomicState.widerOpeningDone = true
                        unschedule(stop)
                    }
                    if(atomicState.closedByApp || (openMore && atomicState.openByApp))
                    {
                        def message = ""
                        if(onlySomeWindowsWillOpen && location.mode in modeSpecificWindows)
                        {
                            log.warn"""
openMore = $openMore
atomicState.openByApp = $atomicState.openByApp
atomicState.closedByApp = $atomicState.closedByApp
atomicState.insideTempHasIncreased = $atomicState.insideTempHasIncreased
atomicState.widerOpeningDone = $atomicState.widerOpeningDone
"""
                            message = "opening $onlyThoseWindows ONLY"
                            def objectDevice
                            int i = 0
                            int s = windows.size() 
                            for(s!=0;i<s;i++)
                            {
                                if("${windows[i]}" in onlyThoseWindows)
                                {
                                    windows[i].on()
                                    log.debug "${windows[i]} is the right device"
                                }
                                atomicState.openByApp = true
                                atomicState.closedByApp = false
                            }
                            descriptionText message
                        }
                        else {
                            if(atomicState.closedByApp){
                                message = "opening ${windows.join(", ")} 564df4"
                                log.warn message
                                for(w in windows)
                                {
                                    log.warn "Opening => $w"
                                    w.on()
                                }
                                atomicState.openByApp = true
                                atomicState.closedByApp = false
                            }
                            else {
                                log.warn "windows were not closed by this app, doing nothing"
                            }
                            log.trace "openMore && atomicState.openByApp ===> ${openMore && atomicState.openByApp}"
                        }

                        if(doorsManagement && doorContactsAreOpen)
                        {
                            atomicState.otherWindowsOpenByApp = true
                            otherWindows?.on()
                        }
                        need0 = "off"
                        need1 = "off"
                        atomicState.lastContactOpenEvt = atomicState.lastContactOpenEvt ? atomicState.lastContactOpenEvt : now()
                        def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0
                        if(contactsAreOpen() && now() - atomicState.lastContactOpenEvt > delayB4TurningOffThermostat) 
                        {
                            if(!fanCirculateAlways)
                            {
                                if(okToTurnOff())
                                {
                                    descriptionText "thermostat off 4trgh26"
                                    turnOffThermostat()
                                    atomicState.offAttempt = now()
                                }
                            }
                            else
                            {
                                // fan circulation conditions managed in getNeed() scope
                            }
                        }
                        if(!openMore)
                        {
                            atomicState.lastOpeningTime = now()
                            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                            atomicState.outsideTempAtTimeOfOpening = outsideTemperature
                            atomicState.insideTempAtTimeOfOpening = inside
                        }

                        log.warn "--------------------------------differentDuration = $differentDuration"
                        if(operationTime && !openMore && !INpwSavingMode) // if openMore or INpwSavingMode ignore stop() and open in full
                        {
                            if(!differentDuration)
                            {
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
                                for(s!=0;i<s;i++)
                                {
                                    device = windows[i]
                                    max = settings["maxDuration${i}"].toInteger()
                                    min = settings["windowsDuration${i}"].toInteger()
                                    log.warn "found : $device with max = $max minimum = $min *********************"

                                    time = getWindowsTimeOfOperation(outsideTemperature, max, min)
                                    runIn(time, stop, [data: ["device": "${device}"], overwrite:false])
                                    log.warn "$device scheduled to stop in $time seconds"

                                }
                            }
                        }
                        //log.warn message
                        atomicState.openByApp = true
                        atomicState.closedByApp = false
                    }
                    else
                    {
                        logtrace "$windows were not closed by this app - ignoring request to turn on any of ${windows?.join(", ")}" 
                    }
                }
                else
                {
                    descriptionText "$windows already open"
                }
            }
            else if(someAreOpen && needToClose)
            {
                if((atomicState.openByApp) || exception)
                {
                    if(exception) { log.warn "EXCEPTION CLOSING" }
                    log.warn "closing $windows"
                    unschedule(stop)
                    atomicState.closingCommand = true // the evt handler will need to know the "closed" event is not just a refresh (isStateChange can fail)
                    atomicState.lastClosingTime = now() 
                    atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                    atomicState.widerOpeningDone = false // simple value reset
                    windows.off()
                    boolean  levelCapabality = windows.any{it.hasCapability("Switch Level")}
                    if(exception) {
                        windows.setLevel(50)
                    }
                    else
                    {
                        windows.setLevel(1)
                    }
                    if(doorsManagement && doorContactsAreOpen && otherWindows?.currentValue("switch") == "on" && atomicState.otherWindowsOpenByApp)
                    {
                        if(atomicState.openByApp && atomicState.otherWindowsOpenByApp) otherWindows?.off()
                        atomicState.otherWindowsOpenByApp = false

                        log.warn "closing $otherWindows"

                    }
                    atomicState.openByApp = false
                    atomicState.closedByApp = true
                    log.debug "56FG"

                }
                else if(!atomicState.openByApp)
                {
                    descriptionText "$windows were not open by this app"
                }
                else if(needToClose)
                {
                    descriptionText "$windows may close soon"
                }
                else 
                {
                    log.error "WINDOWS MANAGEMENT ERROR - fire the developper"
                }
            }
        }
        else if(windows && !inWindowsModes){
            descriptionText "outside of windows modes"
            if(someAreOpen && atomicState.openByApp) // && (inside > target + 2 || inside < target - 2 ))
            {
                windows.off()
                if(windows.any{it.hasCapability("Switch Level")}){ 
                    windows.setLevel(50) 
                }
                //log.debug "56TG"
                atomicState.openByApp = false
                atomicState.closedByApp = true
                if(doorsManagement && doorContactsAreOpen && otherWindows?.currentValue("switch") == "on")
                {
                    if(atomicState.otherWindowsOpenByApp) otherWindows?.off()
                    atomicState.otherWindowsOpenByApp = false
                }
            }
        }

    }
    else if(!windows) {
        logging "user did not select any window switch"
    }
    else if(simpleModeActive)
    {
        descriptionText "skipping windows management due to $simpleModeName Mode trigger mode"
    }
    else if(atomicState.override)
    {
        descriptionText "Override mode because $thermostat is set to 'auto'"
    }


}
def turnOffThermostat(){
    logtrace """turnOffThermostat()
fanCirculateAlways = $fanCirculateAlways
alwaysButNotWhenPowerSaving = $alwaysButNotWhenPowerSaving
"""
    def inside = getInsideTemp()
    if(inside < criticalcold)
    {
        log.warn "not turning off thermostat because inside temp is too low"
    }
    else
    {          
        boolean inpowerSavingMode = location.mode in powersavingmode
        if(!fanCirculateAlways && alwaysButNotWhenPowerSaving) app.updateSetting("alwaysButNotWhenPowerSaving", [type:"bool", value:false]) // fool proofing (the fool being the developper here...)

        if(fanCirculateAlways || (alwaysButNotWhenPowerSaving && Active() && !inpowerSavingMode))
        {
            log.trace "fanCirculateAlways (or related options) returns true, ignoring off command"
            return
        }
        if(simpleModeIsActive() && dontTurnOffinNightMode)
        {
            descriptionText "NOT Turning off thermostats because in simple mode and doNotIgnoreTargetInNightMode is true"
        }
        else if(!ignoreMode || contactsAreOpen() || !Active() || offrequiredbyuser)
        {
            descriptionText "Turning thermostat"
            if(thermostat.currentValue("thermostatMode") != "off") thermostat.setThermostatMode("off") 
        }
    }
}
def resetSetByThisApp(){
    if(atomicState.setpointSentByApp)
    {
        atomicState.setpointSentByApp = false;
    }
}
def userWants(val, inside){
    log.warn "userWants($val while inside is $inside)"
    if(val < inside - 2)
    {
        atomicState.userWantsCooler = true
        runIn(120, resetUserWants)
    }
    else {
        atomicState.userWantsCooler = false
    }
    if(val > inside + 2)
    {
        atomicState.userWantsWarmer = true
        runIn(120, resetUserWants)
    }
    else {
        atomicState.userWantsWarmer = false
    }
}
def resetUserWants(){
    log.warn "resetUserWants()"
    atomicState.userWantsWarmer = false
    atomicState.userWantsCooler = false
}

/************************************************DECISIONS******************************************************/
def getTarget(simpleModeActive){

    int target = 74 // default value
    def inside = getInsideTemp()

    if(method == "auto")
    {
        target = getAutoVal()
        logtrace "getAutoVal() returned $target"
    }
    else
    {
        //log.warn "dimmer = $dimmer dimmer?.currentValue(level) = ${dimmer?.currentValue("level")}"
        target = !dimmer ? atomicState.lastThermostatInput.toInteger() : dimmer?.currentValue("level").toInteger()
    }

    // safety checkup for when alexa misinterpret a command to a light dimmer and applies it to the dimmer used by this app
    def maxHi = celsius ? getCelsius(88) : 88 // assuming no one would be stupid enough to set their thermostat to 88, if so it's interpreted as a problem by the app
    def minLow = celsius ? getCelsius(30) : 30 // same, but when setpoint is too low
    def lastNeed = atomicState.lastNeed
    boolean problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false

    logging """
Celsius ? $celsius
maxAutoHeat = $maxAutoHeat
minAutoCool = $minAutoCool
maxHi = $maxHi
minLow = $minLow
atomicState.lastThermostatInput = $atomicState.lastThermostatInput 
dimmer value = ${dimmer?.currentValue("level")}
heatingSetpoint = ${thermostat.currentValue("heatingSetpoint")}
coolingSetpoint = ${thermostat.currentValue("coolingSetpoint")}
boolean problem = $target >= $maxHi ? ${target >= maxHi} : $target <= $minLow ? ${target <= minLow} : false
atomicState.lastNeed = $atomicState.lastNeed
problem detected : $problem
target = $target
"""

    if(problem)
    {
        log.warn "There's a problem with current target temperature ($target). Readjusting from $thermostat setpoints"
        target = celsius ? getCelsius(74) : 74
        // fix the dimmer's value if any dimmer
        setDimmer(target.toString())
        log.warn "************ $app.name successfuly fixed its target temperature data point! ********" 
    }
    problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false
    logging """
maxHi = $maxHi
minLow = $minLow
target = $target
problem =$problem
"""
    if(problem)
    {
        log.warn "${thermostat}'s values are as inconsistent as ${dimmer ? "${dimmer}'s" : "the previous'"}, meaning data retrieval failed. Applying safe values instead until next user's input"
        target = celsius ? getCelsius(74) : 74 // safe value

    }
    logtrace "simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ****** target = $target************ dimmerLevel = ${dimmer?.currentValue("level")}"


    if(simpleModeActive && !simpleModeSimplyIgnoresMotion)
    {
        if(doorsContactsAreOpen() && contactsOverrideSimpleMode)
        {
            logtrace "some doors are open: $simpleModeName Mode trigger mode ignored at user's request"
        }
        else if(setSpecialTemp || specialSubstraction)
        {       
            target = specialSubstraction ? target - substract : specialTemp && specialDimmer ? specialDimmer.currentValue("level") : specialTemp

            logtrace "target temperature ${substract ? "(specialSubstraction)":"(specialTemp)"} is: $target and last recorded temperature is ${inside} | specialDimmer level = ${specialDimmer.currentValue("level")}"
            return target // END due to simpleModeName Mode trigger mode
        }
        else
        {
            logtrace "simpleModeActive using default target temp: $target | last recorded temperature is ${inside}"
            return target // return the default value
        }

    } 
    logtrace "target temperature is: $target and current temperature is ${inside} (swing = $atomicState.swing) thermostat state is: ${thermostat.currentValue("thermostatMode")}"

    return target
}

def getNeed(target, simpleModeActive, inside){
    atomicState.lastTimeCool = atomicState.lastTimeCool == null ? 3 * 60 * 60 * 1000 + 1 : atomicState.lastTimeCool 
    atomicState.lastTimeHeat = atomicState.lastTimeHeat == null ? 3 * 60 * 60 * 1000 + 1 : atomicState.lastTimeHeat 
    atomicState.userWantsWarmer = atomicState.userWantsWarmer == null ? false : atomicState.userWantsWarmer
    atomicState.userWantsCooler = atomicState.userWantsCooler == null ? false : atomicState.userWantsCooler

    def humidity = outsideTemp?.currentValue("humidity") 
    def insideHum = getInsideHumidity() // backup for windows and value used for negative swing variation when cooling   
    humidity = humidity != null ? humidity : (insideHum != null ? insideHum : 50)
    boolean doorContactsAreOpen = doorsContactsAreOpen()
    boolean doorsOverride = doorsManagement ? doorsOverrideMotion : true // must return true by default if none due to "(!doorContactsAreOpen && doorsOverrideMotion)"
    boolean INpwSavingMode = powersavingmode != null && location.mode in powersavingmode  ? true : false //!simpleModeActive && (!doorContactsAreOpen && doorsOverrideMotion)
    boolean isBetween = timeWindowInsteadofModes ? timeOfDayIsBetween(toDateTime(windowsFromTime), toDateTime(windowsToTime), new Date(), location.timeZone) : false
    logging """current mode = $location.mode 
IN POWER SAVING MODE ? $INpwSavingMode 
powersavingmode = $powersavingmode 
!simpleModeActive = ${!simpleModeActive} 
simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion
!doorContactsAreOpen = ${!doorContactsAreOpen}
location.mode in powersavingmode = ${location.mode in powersavingmode}
doorsOverrideMotion = $doorsOverrideMotion

--**--
"""
    boolean withinTimeWindow = timeWindowInsteadofModes ? isBetween : true // no pun intended... :) 

    boolean inWindowsModes = timeWindowInsteadofModes ? windows && withinTimeWindow : windows && location.mode in windowsModes
    boolean contactClosed = !contactsAreOpen()  

    def outsideThres = getOutsideThershold()
    def outsideTemperature = outsideTemp.currentValue("temperature")
    def need0 = ""
    def need1 = ""
    def need = []
    def amplThreshold = 2
    def amplitude = Math.abs(inside - target)
    def lo = celsius ? getCelsius(50) : 50
    def hi = celsius ? getCelsius(75) : 75
    def swing = outsideTemperature < lo  || outsideTemperature > hi ? 0.5 : 1 // lower swing when hot or cold outside
    atomicState.swing = swing

    def loCoolSw = celsius ? getCelsius(60) : 60
    //DEPRECATED FOR NOW def coolswing = insideHum < loCoolSw ? target + swing : target - swing // if too humid, swing down the threshold when cooling
    boolean amplitudeTooHigh = amplitude >= amplThreshold // amplitude between inside temp and target / preventing amplitude paradox during mid-season

    boolean needCool = !simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ? (inWindowsModes ? outsideTemperature >= outsideThres && inside >= target + swing : outsideTemperature >= outsideThres && inside >= target + swing) : inside >= target + swing

    needCool = atomicState.userWantsCooler || inside > target + 4 ? true : needCool

    logging"""
inside = $inside
target = $target
swing = $swing
outsideTemperature >= outsideThres + 5 = ${outsideTemperature >= outsideThres + 5}
outsideTemperature = $outsideTemperature
outsideThres + 5 = ${outsideThres + 5}
needCool = $needCool
simpleModeActive = $simpleModeActive
simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion

"""
    boolean needHeat = !simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ? (outsideTemperature < outsideThres || (amplitudeTooHigh && atomicState.lastNeed != "cool")) && inside <= target - swing : inside <= target - swing && outsideTemperature < outsideThres

    needHeat = atomicState.userWantsWarmer || inside < target - 4 ? true : needHeat

    //log.warn "inside = $inside inside >= target + swing : ${inside >= target + swing} |||needCool=$needCool"

    boolean motionActive = Active()// || simpleModeActive

    // shoulder season management: simpleModeName Mode trigger forces ac to run despite cold outside if it gets too hot inside
    boolean norHeatNorCool = !needCool && !needHeat && inside > target + swing && simpleModeActive && outsideTemperature >= 55 ? true : false
    // the other room could be in an inadequate mode, which would be noticed by an undesirable temperature amplitude
    boolean unacceptable = doorContactsAreOpen && !atomicState.override && (inside < target - 2 || inside > target + 2) // if it gets too cold or too hot, ignore doorsManagement
    logging """inside = $inside 
target = $target 
$inside < ${target - 2} : ${inside < target - 2} 
$inside > ${target + 2} : ${inside > target + 2}
"""

    //log.warn "doorContactsAreOpen = $doorContactsAreOpen"
    if(unacceptable) // when doors are open, other room's thermostat manager might be in power saving mode
    {
        log.info formatText("UNACCEPTABLE TEMP - ignoring doors management sync", "red", "white")   
    }

    logging """
INpwSavingMode = $INpwSavingMode
contactClosed = $INpwSavingMode
motionActive = $INpwSavingMode
"""

    if(!unacceptable && doorsManagement && doorContactsAreOpen && contactClosed)
    {
        def n = otherRoomCooler ? otherRoomCooler.currentValue("switch") == "on" ? "cool" : "off" : doorThermostat?.currentValue("thermostatMode")
        need0 = n.capitalize() // capital letter for later construction of the setCoolingSetpoint cmd String
        need1 = n

        def message = "$doorsContacts ${doorsContacts.size() > 1 ? "are":"is"} open. $thermostat set to ${doorThermostat}'s mode ($n)"     
        descriptionText "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">$message</div>"         

    } 
    else if(!INpwSavingMode && contactClosed && motionActive)
    {
        if(needCool || needHeat || norHeatNorCool)
        {
            if(needCool || norHeatNorCool)
            {
                descriptionText "needCool true"
                need0 = "Cool"// capital letter for later construction of the setCoolingSetpoint cmd
                need1 = "cool"
                atomicState.lastNeed = "cool"
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
            else if(needHeat) // heating need supercedes cooling need in order to prevent amplitude paradox
            {
                descriptionText "needHeat true"
                need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
                need1 = "heat"
                atomicState.lastNeed = "heat"
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
        }
        else if(offrequiredbyuser)
        {
            need0 = "off"
            need1 = "off"
            logging("need set to OFF")
        }
        else 
        {

            if(fanCirculateAlways)
            {
                need0 = "off"
                need1 = "off"
                logging("need set to OFF")
            }
            else
            {
                need0 = atomicState.lastNeed.capitalize()
                need1 = atomicState.lastNeed
                descriptionText "Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)"
            }
        }
    }
    else   // POWER SAVING MODE OR NO MOTION OR CONTACTS OPEN     
    { 
        log.trace "POWER SAVING MODE ACTIVE" 

        def cause = location.mode in powersavingmode ? "$location.mode" : !motionActive ? "no motion" : (INpwSavingMode ? "power saving mode" : (!contactClosed ? "Contacts Open" : "UNKNOWN CAUSE - SPANK THE DEVELOPPER"))
        cause = cause == "Contacts Open" ? "${cause}: ${atomicState.listOfOpenContacts}" : cause
        def message = ""

        logging """
inside < criticalhot :  ${inside < criticalhot}
inside > criticalcold :  ${inside > criticalcold}

"""        
        need0 = "off"
        need1 = "off"

        if(criticalhot == null) 
        {
            app.updateSetting("criticalhot", [type:"number", value:"78"])
        }

        if(inside > criticalhot || fancirculateAllways)
        {
            log.debug formatText("${fancirculateAllways ? "fan set to always be on" : "POWER SAVING MODE EXPCETION: TOO HOT! (${cause} criticalhot = ${criticalhot})"}", "black", "red")
            if(thermostat.currentValue("thermostatFanMode") != "on")
            {
                thermostat.setThermostatFanMode("on")
            }
            return [need0, need1]

        }
        else if(inside < criticalcold)
        {
            log.warn formatText("POWER SAVING MODE EXPCETION: TOO COLD! ($cause criticalcold = $criticalcold)", "white", "blue")
            need0 = "Heat"
            need1 = "heat"
            atomicState.lastNeed = "heat"    
        }
        else
        {
            thermostat.setThermostatFanMode("auto")

            atomicState.fanOn = false
            if(okToTurnOff()) turnOffThermostat()
        }


        message = formatText("POWER SAVING MODE ($cause)", "white", "#90ee90")   

        def fanCmd = keepFanOnInNoMotionMode && !motionActive ? "on" : "off" // only when motion is not active, otherwise it'll also turn on when windows are open
        if(fan && fan?.currentValue("switch") != "fanCmd")
        {
            descriptionText "$fan turned $fanCmd hyr354"
            fan?."${fanCmd}"()
        }

        log.warn message

    }

    windowsControl(target, simpleModeActive, inside, outsideTemperature, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh)

    logging"""
simpleModeActive = $simpleModeActive
doorContactsAreOpen = $doorContactsAreOpen
!contactsOverrideSimpleMode = ${!contactsOverrideSimpleMode}
simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion
simpleModeIsActive() = ${simpleModeIsActive()}
"""


    if(UseSimpleMode && (simpleModeActive && !doorContactsAreOpen && !contactsOverrideSimpleMode && !simpleModeSimplyIgnoresMotion)) // 
    {
        log.info formatText("$simpleModeName Mode Enabled", "white", "grey")
    }
    else if(UseSimpleMode && simpleModeActive && contactsOverrideSimpleMode && doorsOpen)
    {
        log.info formatText("$simpleModeName Mode Called but NOT active due to doors being open", "white", "grey")
    }
    else if(UseSimpleMode)
    {
        descriptionText formatText("$simpleModeName Mode Disabled", "white", "grey")
    }

    need = [need0, need1]

    if(differentiateThermostatsHeatCool)
    {
        def neededThermostat = need1 == "cool" ? thermostatCool : thermostatHeat
        if(thermostat.id != neededThermostat.id)
        {
            log.warn "using $neededThermostat as ${need1 == "cool" ? "cooling unit" : "heating unit"} due to user's requested differenciation"
            app.updateSetting("thermostat", [type:"capability", value: neededThermostat])
        }
        def remainsOff = need1 == "cool" ? thermostatHeat : thermostatCool
        if(remainsOff.currentValue("thermostatMode") != "off") remainsOff.off()
    }

    logging formatText("""
--------------NEED---------------------
inWindowsModes = $inWindowsModes
power saving management= ${powersavingmode ? "$powersavingmode INpwSavingMode = $INpwSavingMode":"option not selected by user"}
amplitude = $amplitude
amplitudeTooHigh = $amplitudeTooHigh

humidity = ${humidity}%
insideHum = ${insideHum}%

outside = $outsideTemperature
inside = $inside
criticalhot = $criticalhot
criticalcold = $criticalcold
target = $target

swing = $swing

inside > target = ${inside > target}
inside < target = ${inside < target} | $inside < $target

simpleModeActive = $simpleModeActive 
contactClosed = $contactClosed 
outsideThres = $outsideThres
outsideTemperature > target = ${outsideTemperature > target}
outsideTemperature < target = ${outsideTemperature < target}
outsideTemperature >= outsideThres = ${outsideTemperature >= outsideThres}
outsideTemperature < outsideThres = ${outsideTemperature < outsideThres}

needCool = $needCool
needHeat = $needHeat (needHeat supercedes needCool) 

final NEED value = $need
---------------------------------------
</div>
""", "white", "blue")

    logtrace "current need: ${need1 != "off" ? "${need1}ing" : need1} contacts open? ${contactsAreOpen()} | userWantsCooler ? $atomicState.userWantsCooler | userWantsWarmer ? $atomicState.userWantsWarmer"


    if(need1 == "cool" && atomicState.lastNeed == "heat") atomicState.lastTimeCool = now() // keep track of time to avoid oscillations between cool / heat during shoulder season
    if(need1 == "heat" && atomicState.lastNeed == "cool") atomicState.lastTimeHeat = now() // same idea


    if(need1 == "heat" && atomicState.userWantsWarmer) 
    {
        log.warn "user wants a warmer room, shoulder season timed override ignored. Switching to heating mode"
    }
    else if(need1 == "heat" && now() - atomicState.lastTimeCool < 3 * 60 * 60 * 1000 && inside > target - 2)
    {
        log.warn "last cooling request was too close to switch to heating mode now"
        need0 = "off"
        need1 = "off"
    }
    if(need1 == "cool" && atomicState.userWantsCooler)
    {
        log.warn "user wants a cooler room, shoulder season timed override ignored. Switching to cooling mode"   
    }
    else if(need1 == "cool" && now() - atomicState.lastTimeHeat < 3 * 60 * 60 * 1000 && inside < target + 2)
    {
        log.warn "last heating request was too close to switch to cooling mode now"
        need0 = "off"
        need1 = "off"
    }

    need = [need0, need1]

    descriptionText "atomicState.lastNeed = $atomicState.lastNeed"
    logtrace "need: $need | target: $target | outside: $outsideTemperature | inside: $inside"
    return need


}
def getAutoVal(){

    def outside = outsideTemp?.currentValue("temperature") 
    def need = outside >= getOutsideThershold() ? "cool" : "heat"
    def result = celsius ? getCelsius(73):73 // just a temporary default value  
    def defaultV = result
    //def humidity = outsideTemp?.currentValue("humidity") // outside humidity
    def humidity = getInsideHumidity() // in auto mode we evaluate based only on inside humidity
    humidity = humidity != null ? humidity : 50 // assume 50 as a temporary value to prevent errors when a sensor has just been installed by user and humidity value has yet to be parsed

    def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp

    def variation = getVariationAmplitude(outside, need)

    logging "variation amplitude = $variation | absolute need (auto method, not from getNeed()) is $need outside: $outside "

    result = need == "cool" ? humidity >= humThres ? outside - (variation + 1) : outside - variation : need == "heat" ? humidity >= humThres ? outside + variation + 1 : outside + variation : "ERROR"

    //result = need == "cool" ? outside - Math.abs(variation) : need == "heat" ? outside + Math.abs(variation) : "ERROR"

    //log.error "result = $result"

    if(result == "ERROR") { 
        log.error """ERROR at getAutoVal()
need = $need
atomicState.lastNeed = $atomicState.lastNeed
humidity inside = $humidity 
humThres = $humThres
outside = $outside
"""     
        return defaultV
    }

    logging """need = $need
atomicState.lastNeed = $atomicState.lastNeed
humidity inside = $humidity 
insideHum = $insideHum
humThres = $humThres
outside = $outside"""

    def hiCool = celsius ? getCelsius(77) : 77
    def loCool = celsius ? getCelsius(70) : 70
    def hiHeat = celsius ? getCelsius(75) : 75
    def loHeat = celsius ? getCelsius(70) : 70
    def maxAH = maxAutoHeat != null ? maxAutoHeat : hiHeat
    def minAC = minAutoCool != null ? minAutoCool : loCool
    def minAH = minAutoHeat != null ? minAutoHeat : loHeat
    def maxAC = maxAutoCool != null ? maxAutoCool : hiCool

    logging """
maxAH = $maxAH
minAC = $minAC
minAH = $minAH
maxAC = $maxAC
"""

    result = result > maxAH && need == "heat" ? maxAH : result // in this scope need is always either "cool" or "heat", never "off" so these conditions won't be ignored
    result = result < minAC && need == "cool" ? minAC : result
    result = result < minAH && need == "heat" ? minAH : result
    result = result > maxAC && need == "cool" ? maxAC : result

    descriptionText "target temperature (auto) in this room is: $result (${humidity > humThres ? "humid condition true" : "humid condition false"}(${humidity}%) | outside temp: $outside) "
    return result
}
def getVariationAmplitude(outside, need){

    def y = 0 // value to find
    def x = outside // current temperature outside
    def ya = targetVar != null ? targetVar : 1 // coressponding difference required when outside temperature = xa

    y = 314.734*Math.log(16.2364-0.018708*x)-839.624 // 


    logtrace "atomicState.db //= ${atomicState.db.sort{it.value.toInteger()}}"

    // we want to find the temperature value (key) that is the closest to current temperature. For that, we find the minimum difference between current temp and list of temps
    def differences = []
    def childMap = [:]

    atomicState.db.eachWithIndex { 
        key, val, index ->
        def diff = Math.abs(x - key.toInteger()).toInteger()
        differences += diff
        childMap."$diff" = key // remember the key in a separate map
    }
    def minD = differences.min() // closest temperature in db to current temperature
    //log.debug "childMap = $childMap"
    def keySearch = childMap."$minD"
    logtrace "minD = $minD, keySearch = $keySearch"

    y = atomicState.db."$keySearch"   
    y = y.toInteger()
    y = Math.abs(y)

    logging """
y = ${Math.abs(y)}
x / outside = $outside
theoretical target temperature (before humidity adjustments) = ${outside - y.toInteger()}

"""
    log.info "db variation amplitude = ${y}"



    return y

}
def getWindowsTimeOfOperation(outsideTemperature, max, min){

    def y = null // value to find
    def x = outsideTemperature // current temperature outside
    def ya = min // minimal duration // coressponding duration for when outside temperature = xa
    def xa = outsidetempwindowsL // minimal operation temperature
    def m = 0.9 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)

    logging """
y = $y
max = $max
min = $min
"""
    y = y < min ? min : y > max ? max : y
    y = outsideTemperature > 74 ? max : y

    descriptionText "linear result for windows duration = ${y?.toInteger()} seconds"
    return y.toInteger()
}
def getInsideTemp(){

    def inside = thermostat?.currentValue("temperature") 
    def deltaHours = 72*3600*1000

    atomicState.disabledSensors = []

    if(sensor)
    {

        def sum = 0
        int i = 0
        int s = sensor.size()
        int substract = 0 // value to substract to s when a device is to be ignored
        for(s != 0; i<s;i++)
        {
            def device = sensor[i]
            def val = device?.currentValue("temperature")
            logging "--${device}'s temperature is: $val"
            sum += val

            // check sensors responsiveness
            events = device.eventsSince(new Date(now() - deltaHours)).size() // collect all events within 72 hours
            logging "$device has returned $events events in the last 72 hours" 
            if(events == 0)
            {
                log.warn formatText("$device hasn't returned any event in the last 72 hours!", "white", "red")
                if(sensor.size() > 1)
                {
                    if(atomicState.disabledSensors.contains("$device"))
                    {
                        //already in, do nothing 
                        //log.warn " INPUT ALREADY DONE --------"
                    }
                    else 
                    {
                        atomicState.disabledSensors += "$device"
                    }
                    logtrace "$device is being ignored because it is unresponsive."
                    sum -= val

                }
                substract += 1
            }
            else if(atomicState.disabledSensors.any{it->it == device.displayName}) 
            {
                log.warn "deleting $device from disabledSensors because it is active again"
                atomicState.disabledSensors -= "$device"
            }
            if(device.hasCapability("Battery"))
            {
                def batteryValue = device.currentValue("battery")
                def batThreshold = lowBatLevel ? lowBatLevel : 40
                if(batteryValue <= batThreshold && batteryValue > 0) // batteryValue > 0 is because some cheap devices can return negative values when they're powered by AC current from and HVAC / PTAC
                {
                    log.warn formatText("WARNING! ${device}'s BATTERY LEVEL IS LOW (${batteryValue}%)", "white", "red")
                    atomicState.lowBatterySensor = true
                }
                else 
                {
                    atomicState.lowBatterySensor = false
                }
            }
        }

        if(atomicState.disabledSensors.size() != 0)
        {
            log.debug "disabledSensors size = ${atomicState.disabledSensors.size()}"
            def a = atomicState.disabledSensors
            log.warn "SOME SENSORS FAILED: ${a.join(", ")}"
        }

        if(sum == 0)
        {
            atomicState.paused = true
            log.warn "SUM = 0 !!! 5erg4z"
            thermostat.setThermostatMode("auto") // set the thermostat to auto for safety. 
            atomicState.failedSensors = true // force the user to attend the fact that all sensors are irresponsive
            return 0
        }
        else
        {
            atomicState.failedSensors = false
        }
        inside = sum/(s-substract)
    }
    else if(doorsManagement && doorsContactsAreOpen() && doorSetOfSensors && useDifferentSetOfSensors)
    {
        def sum = 0
        int i = 0
        int s = doorSetOfSensors.size()
        for(s != 0; i<s;i++)
        {
            def val = doorSetOfSensors[i]?.currentValue("temperature")
            descriptionText "**${doorSetOfSensors[i]} temperature is: $val"
            sum += val
        }

        inside = sum/s
    }

    descriptionText "${sensor?"average":""} temperature in this room is: $inside"

    logtrace "measured ${sensor && sensor.size() > 1 ? "temperatures are" : "is"}: ${sensor ? "${sensor.join(", ")} ${sensor.collect{it.currentValue("temperature")}.join("°F, ")}°F": "${thermostat.currentValue("temperature")}°F"}"




    inside = inside.toDouble()
    inside = inside.round(2)
    atomicState.inside = inside
    return inside
}
def getOutsideThershold(){

    // define the outside temperature as of which heating or cooling are respectively required 
    // modulated with outside humidity 

    def outsideTemperature = outsideTemp?.currentValue("temperature")


    return getHumidityThreshold()
}
def getInsideHumidity(){

    def result 

    if(!optionalHumSensor)
    {
        // if  we tested with hasCapability() it could return true due to generic thermostat drivers, so we test null value instead
        result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity") 

        if(result == null) // if still null, force the user to review their settings
        {
            log.error formatText("NOR YOUR THERMOSTAT NOR YOUR OUTSIDE SENSOR SUPPORT HUMIDITY MEASUREMENT - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
        }
    }
    else
    {
        result = optionalHumSensor.currentValue("humidity")   
        if(result == null) // if still null, force the user to review their settings
        {
            log.warn formatText("$optionalHumSensor does not support humidity (beware of generic drivers!). - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
            result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity") 
            if(result != null)
            {
                log.warn formatText("This app is using ${thermostat?.currentValue("humidity") != null ? "$thermostat" : "$outsideTemp"} as a default humidity sensor in the mean time", "black", "red")
            }
            result = result == null ? 50 : result // temporary value as last resort
        }
    }
    logging "Inside humidity is ${result}%"
    return result
}
def getHumidityThreshold(){

    def humidity = getInsideHumidity() //outsideTemp?.currentValue("humidity") //NB: getInsideHumidity() will use outside humidity if and only if inside sensor is not returning values for some reason
    humidity = humidity != null ? humidity : celsius ? getCelsius(50):50 // prevents error from recently installed thermostats
    if(humidity == null){
        def message = """$outsideTemp is not returning any humdity value - it may be because it was just included; if so, this will resolve ont its own.
If this message still shows within an hour, check your thermostat configuration..."""
        log.warn formatText(message, red, white)
    }
    // the higher the humidity, the lower the threshold so cooling can happen 
    def y = null // value to find
    def x = humidity ? humidity : 50 // 50 if no data returned to allow the app to run
    def ya = celsius ? getCelsius(58) : 58 //celsius ? getCelsius(60):60 // coressponding outside temperature value for when humidity = xa 
    def xa = 90 // humidity level
    def m = atomicState.lastNeed == "cool" ? 0.1 : -0.1 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //log.warn "y = $y"
    def lo = celsius ? getCelsius(65):65 // it's ok to run the heat if humidity is high while temp below 65 outside
    def hi = celsius ? getCelsius(72):72 // not ok to run the heat if temp ouside is above 72
    def result = y > hi ? hi : (y < lo ? lo : y) // max and min //  


    descriptionText "humidity threshold (cool/heat decision or/and windows decision) is ${y != result ? "$result (corrected from y=$y)" : "$result"} (humidity being ${humidity < 40 ? "low at ${humidity}%" : "high at ${humidity}%"})"

    return result
}
def getLastMotionEvents(Dtime, testType){

    int events = 0

    /******************************IF ANY ACTIVE, THEN NO NEED FOR COLLECTION***********************************************/
    //this is faster to check if a sensor is still active than to collect past events, so return true if it's the case    
    if(motionSensors.any{it -> it.currentValue("motion") == "active"})
    {
        logging "Sensor still active: ${motionSensors.findAll{it.currentValue("motion") == "active"}}"
        events = 10 // this is not a boolean method but called through the definition of a boolean variable in Active() scope, so it must return a positive integer value if motion is active. 
        if(testType == "motionTest") descriptionText "$atomicState.activeMotionCount active events in the last ${Dtime/1000/60} minutes (currently active)"
        return events // so we must return a number equal or greater than 1
    }


    /******************************COLLECTION**********************************************************/
    collection = motionSensors.collect{it.eventsSince(new Date(now() - Dtime)).findAll{it.value == "active"}}.flatten()
    events = collection.size()
    /**************************************************************************************************/

    if(testType == "motionTest")logtrace "$events active events collected within the last ${Dtime/1000/60} minutes (eventsSince)"

    // eventsSince() can be messy // hubitat still doesn't acknowledge the issue despite several tickets and screenshots. 
    atomicState.activeMotionCount = atomicState.activeMotionCount ? atomicState.activeMotionCount : 0
    if(testType == "motionTest" && now() - atomicState.lastMotionEvent > Dtime && atomicState.activeMotionCount != 0) // if time is up, reset atomicState events value
    {
        atomicState.activeMotionCount = 0 // time is up, reset this variable
        //events = 0
    }
    else if(events < atomicState.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        events = atomicState.activeMotionCount
    }

    if(events > atomicState.activeMotionCount && testType == "motionTest") // whichever the greater takes precedence
    {
        atomicState.activeMotionCount = events
    }

    descriptionText "$events ${testType == "motionTest" ? "($atomicState.activeMotionCount)":""} active events in the last ${Dtime/1000/60} minutes ($testType)"
    return events
}
def getRemainTime(timeLimit, timeStamp){

    timeLimit = timeLimit.toInteger() * 60 * 60 * 1000
    long elapsedTime = now() - timeStamp // total elapsed time since last true event and now

    if(elapsedTime > timeLimit)
    {
        return 0
    }

    // get the remaining time given the time limit
    float minutes = (timeLimit - elapsedTime)/1000/60 // remaining minutes
    float hours = (timeLimit - elapsedTime)/1000/60/60 // remaining hours
    float remain = minutes >= 60 ? hours : minutes // decision hours/minutes
    def unit = minutes >= 60 ? "hours" : "minutes"

    logging """
timeLlimit = $timeLimit
timeStamp = $timeStamp
(now() - timeStamp)/1000/60 = ${(now() - timeStamp)/1000/60} minutes
elapsedTime = $elapsedTime
//REMAINING TIME in minutes, hours
minutes = $minutes
hours = $hours
remain = $remain
unit = $unit 
"""

    return "${Math.round(remain)} $unit"
}
def getCelsius(int value){
    def C = (value - 32) * (5/9) 
    descriptionText "${value}F converted to ${C}C"
    return C.toInteger()
}
def getFahrenheit(int value){
    //(0°C × 9/5) + 32 = 32°F
    def F = (value * 9/5) + 32 
    descriptionText "${value}F converted to ${F}F"
    return F.toInteger()
}
/************************************************A.I. LEARNING (beta) ******************************************************/
def learn(val){
    def outside = outsideTemp.currentValue("temperature")
    def amplitude = outside.toInteger() - val.toInteger()
    log.warn "atomicState.lastNeed = $atomicState.lastNeed"

    if(!atomicState.setpointSentByApp)
    {
        log.debug "LEARNING $outside : $amplitude => new target: ${atomicState.lastNeed == "cool" ? outside - amplitude : atomicState.lastNeed == "heat" ? outside + Math.abs(amplitude) : null} "

        def db = atomicState.db
        db."${outside}" = amplitude

        atomicState.db = db
        log.debug "Updated map : $atomicState.db"
    }
    else
    {
        logging "not learning from this entry ($val) because it doesn't seem to be coming from a user's input"
    }
}

/************************************************BOOLEANS******************************************************/
boolean contactsAreOpen(){
    if(WindowsContact){
        boolean Open = WindowsContact?.any{it -> it.currentValue("contact") == "open"}      
        def listOfOpenContacts = []
        listOfOpenContacts = WindowsContact?.findAll{it.currentValue("contact") == "open"}
        atomicState.listOfOpenContacts = listOfOpenContacts.join(", ")
        return Open
    }
    else
    {
        return false
    }
}
boolean simpleModeIsActive(){
    atomicState.lastButtonEvent = atomicState.lastButtonEvent != null ? atomicState.lastButtonEvent : now()
    boolean result =  atomicState.lastResultWasTrue 
    //boolean doorOpen = doorsContactsAreOpen() // FEEDBACK LOOP since doorsContactsAreOpen() function calls simpleModeIsActive()
    boolean currentlyClosed = false 

    if(!UseSimpleMode)
    {
        return false
    }
    if(UseSimpleMode)
    {
        result = atomicState.buttonPushed      
    }
    if(UseSimpleMode && simpleModeTimeLimit && atomicState.buttonPushed) // if user set a time limit
    {     
        def remainTime = getRemainTime(simpleModeTimeLimit, atomicState.lastButtonEvent)
        def message = "$simpleModeName Mode - remaining time: ${remainTime}"
        descriptionText formatText(message, "white", "grey")

        if(remainTime <= 0) // time is up
        {
            result = false 
            atomicState.buttonPushed = false
        }
    }

    logging"$simpleModeName Mode trigger boolean returns $result"   

    return result
}
boolean doorsContactsAreOpen(){
    boolean Open = false
    def listOpen = []

    if(doorsContacts)
    {
        listOpen = doorsContacts?.findAll{it?.currentValue("contact") == "open"}
        Open = doorsContacts?.any{it->it.currentValue("contact") == "open"}
    }
    if(Open && !contactsOverrideSimpleMode && simpleModeIsActive())
    {
        descriptionText "$doorsContacts open but $simpleModeContact is closed and user doesn't wish to override"
        return false
    }

    logging """doors: $doorsContacts open ?: ${listOpen}"""
    return Open
}
boolean Active(){
    boolean result = true // default is true  always return Active = true when no sensor is selected by the user

    if(simpleModeIsActive()) return true

    if(motionSensors)
    {
        // def currentModeMotionTimeout = settings.find{it?.key == "noMotionTimeWithMode${location.mode}"}?.value?.toInteger()        
        def modeTimeVal = motionmodes?.size() > 1 ? settings.find{it?.key == "noMotionTimeWithMode${location.mode}"}?.value?.toInteger() : 0
        modeTimeVal = modeTimeVal != null && modeTimeVal != 0 ? modeTimeVal : noMotionTime // extra precaution, probably useless... 
        long Dtime = motionmodes?.size() > 1 ? modeTimeVal * 1000 * 60: noMotionTime != null && noMotionTime != 0 ? noMotionTime * 1000 * 60 : 30 * 1000 * 60 // in case noMotionTime is compromised, set a 30 minutes default. Another probably useless precaution... 
        boolean inMotionMode = location.mode in motionmodes

        if(inMotionMode)
        {
            if(testMotionBattery)
            {
                def devicesWithBatteryCapability = motionSensors.findAll{it.hasCapability("Battery")} 
                def batThreshold = lowBatLevel ? lowBatLevel : 40
                def devicesWithLowBattery = devicesWithBatteryCapability.findAll{it.currentValue("battery") <= batThreshold && it.currentValue("battery") > 0} // cannot write expression "it -> it.currentValue..." inside a dynamic page for some reason... 
                if(devicesWithLowBattery.size() != 0)
                {
                    atomicState.lowBattery = true
                    def m = "LOW BATTERY: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                    if(devicesWithLowBattery.size() == motionSensors.size())
                    {
                        log.warn formatText(m,"white", "red")
                        m = "All motion sensors batteries are low! Motion test returns true as a safety measure. Make sure to replace the battery of the following devices: \r\n\r\n - ${devicesWithLowBattery.join("\r\n - ")}"
                        log.warn formatText(m,"white", "grey")
                        return true
                    }
                    else 
                    {
                        log.warn formatText(m,"white", "red")
                    }
                }
                else
                {
                    atomicState.lowBattery = false
                }
            }

            result = getLastMotionEvents(Dtime, "motionTest") > 0

        }
        else 
        {
            logtrace("motion returns true because outside of motion modes")
        }
    }
    else 
    {
        logging("user did not select any motion sensor")
    }
    logging "motion test returns $result"
    return result
}
boolean okToTurnOff(){
    // this function is mostly meant to provide delay between open contact event and turn off
    atomicState.lastContactOpenEvt = atomicState.lastContactOpenEvt ? atomicState.lastContactOpenEvt : now()
    def delayB4TurningOffThermostat = openDelay ? openDelay * 1000 : 0

    if(contactsAreOpen())
    {
        if(fanCirculateAlways || (alwaysButNotWhenPowerSaving && Active() && !inpowerSavingMode))
        {
            return false
        }
        if(now() - atomicState.lastContactOpenEvt > delayB4TurningOffThermostat) 
        {
            log.trace "okToTurnOff() returning true zgf45"
            return true
        } 
        else 
        {
            log.trace "contacts are open, thermostat will be set to off in ${(delayB4TurningOffThermostat/1000)-((now() - atomicState.lastContactOpenEvt)/1000)} seconds"
            return false
        }
    }
    else
    {
        // log.warn "ignoreMode = $ignoreMode"
        if(ignoreMode && Active() && !offrequiredbyuser) return false;
        descriptionText "okToTurnOff() returning true 5gr4"
        return true // if contacts are closed, any other request to turn off the AC coming from this app must be granted
    }
}


/************************************************MISCELANEOUS*********************************************************/
def stop(data){
    //log.warn "STOP STOP STOP STOP ++++++++++++++++++++++++++++++customCommand = $customCommand"

    def cmd = customCommand ? customCommand.minus("()") : "off"

    if(differentDuration){

        def dev = settings["windows"].find{it.name == data.device}
        log.trace "differentiated scheduled STOP for ${dev}"

        dev."${cmd}"()  
    }
    else {


        int s = windows.size()
        int i = 0
        for(s!=0;i<s;i++)
        {
            windows[i]."${cmd}"()
            log.warn "${windows[i]} $customCommand"
        }
        if(doorsManagement && doorContactsAreOpen && atomicState.otherWindowsOpenByApp)
        {
            s = otherWindows.size()
            i = 0
            for(s!=0;i<s;i++)
            {
                otherWindows[i]."${cmd}"()
                log.warn "${otherWindows[i]} $customCommand"
            }
        }
    }


}

def Poll(){
    
    if(location.mode in restricted){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    if(!polldevices) return

    if(atomicState.paused == true)
    {
        return
    }

    logtrace "POLLING DEVICES"
    boolean override = atomicState.override   
    boolean thermPoll = thermostat.hasCommand("poll")
    boolean thermRefresh = thermostat.hasCommand("refresh") 
    boolean outsidePoll = outsideTemp.hasCommand("poll")
    boolean outsideRefresh = outsideTemp.hasCommand("refresh") 


    if(thermRefresh){
        thermostat.refresh()
        descriptionText("refreshing $thermostat")
    }
    if(thermPoll){
        thermostat.poll()
        descriptionText("polling $thermostat")
    }

    // no longer poll sensors mostly because not needed while battery powered

    if(windows)
    {
        boolean windowsPoll = windows.findAll{it.hasCommand("poll")}.size() == windows.size()
        boolean windowsRefresh = windows.findAll{it.hasCommand("refresh")}.size() == windows.size()

        if(windowsRefresh){
            int i = 0
            int s = windows.size()
            for(s!=0;i<s;i++)
            {
                def dev = windows[i]
                dev.refresh()
                descriptionText("refreshing $dev")
            }
        }
        if(windowsPoll){
            int i = 0
            int s = windows.size()
            for(s!=0;i<s;i++)
            {
                def dev = windows[i]
                dev.refresh()
                descriptionText("refreshing $dev")
            }
        }
    }

}
def pollPowerMeters(){

    if(!polldevices) return

    atomicState.polls = atomicState.polls == null ? 1 : atomicState.polls + 1
    atomicState.lastPoll = atomicState.lastPoll ? atomicState.lastPoll : now()
    if(now() - atomicState.lastPoll > 1000 * 60 * 60) atomicState.polls = 0

    logtrace "polling power meters. $atomicState.polls occurences in the last hour..."
    if(atomicState.polls > 50)
    {
        log.warn "too many polling requests within the last hour. Not polling, not refreshing..."
        return
    }

    boolean heaterPoll = heater?.hasCommand("poll")
    boolean heaterRefresh = heater?.hasCommand("refresh") 
    boolean coolerPoll = cooler?.hasCommand("poll")
    boolean coolerRefresh = cooler?.hasCommand("refresh") 
    boolean pwPoll = pw?.hasCommand("poll")
    boolean pwRefresh = pw?.hasCommand("refresh") 

    if(pwRefresh){
        pw.refresh()
        descriptionText("refreshing $pw 5df4")
    }
    if(pwPoll){
        pw.poll()
        descriptionText("polling $pw")
    }
    if(heaterRefresh){
        heater?.refresh()
        descriptionText("refreshing $heater")
    }
    if(heaterPoll){
        heater?.poll()
        descriptionText("polling $heater")
    }
    if(coolerRefresh){
        cooler?.refresh()
        descriptionText("refreshing $cooler")
    }
    if(coolerPoll){
        cooler?.poll()
        descriptionText("polling $cooler")
    }
    atomicState.lastPoll = now()
}
def logging(message){
    if(enabledebug)
    {
        log.debug message
    }
    atomicState.EnableDebugTime = atomicState.EnableDebugTime == null ? atomicState.EnableDebugTime = now() : atomicState.EnableDebugTime
    atomicState.enableDescriptionTime = atomicState.enableDescriptionTime == null ? atomicState.enableDescriptionTime = now() : atomicState.enableDescriptionTime
}
def logtrace(message){
    if(tracedebug) log.trace message
}
def descriptionText(message){
    if(description)
    {
        log.info message
    }
}
def disablelogging(){
    log.warn "debug logging disabled..."
    app.updateSetting("enabledebug",[type:"bool", value:"false"])
}
def disabledescription(){
    log.warn "description text disabled..."
    app.updateSetting("description",[type:"bool",value:"false"])
}
def formatText(title, textColor, bckgColor){
    return  """<div style=\
"width:80%;
background-color:${bckgColor};
border: 10px solid ${bckgColor};
color:${textColor};
font-weight: bold;
box-shadow:4px 4px 4px #bababa;
margin-left:0px\">${title}</div>"""
}
def formatTitle(title){
    return  """<div style=\
"background-color: lightgrey;
width: 80%;
border: 3px solid green;
padding: 10px;
margin: 20px;\">${title}</div>"""
}