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
            href "thermostats", title: "Thermostats and other devices", description: ""
            if(thermostat && outsideTemp)
            {
                href "methods", title: "Methods of evaluation", description: ""
                href "contactsensors", title: "Contacts, Windows And Doors", description: ""
                href "powersaving", title: "Motion", description: ""           
                href "windowsManagement", title: "Windows Control", description: ""
                href "comfortSettings", title: "Simple Mode", description: ""
                href "fanCirculation", title: "Air circulation", description:""

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
            input "poll", "button", title: "REFRESH SENSORS"
            input "polldevices", "bool", title: "Poll devices"

            input "enabledebug", "bool", title: "Debug", submitOnChange:true
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
            input "forceCmd", "bool", title:"Force commands (for old non-Zwave-plus devices that don't refresh their status properly under certain mesh conditions)", defaultValue:false
            input "pw", "capability.powerMeter", title:"optional: verify my thermostat's consistent operation with a power meter", required:false
            input "heatpump", "bool", title: "$thermostat is a heat pump", submitOnChange:true
            def mssg = "Because $thermostat is a heatpump, you must select an alternate heater controlled by a switch (see further down). This is due to the fact that a heatpump will not be power efficient under certain weather conditions and temperatures. ${app.label} will make sure the best device is being used when needed"
            if(heatpump){paragraph formatText (mssg, "blue", "white")}
            if(restricted)
            {
                input "restrictedThermMode", "enum", title:"Select default's thermostat operation once your location is in restricted mode", options:["off", "cool", "heat", "auto"], required:false, defaultValue: "off", submitOnChange:true
                if(restrictedThermMode == "auto")
                {
                    paragraph formatText("Beware that 'auto' is the override mode, which means that this app won't be able to control your thermostat until you set your thermostat back to either 'cool', 'heat' or 'off'", "white", "red")
                }
            }

        }    

        section("Select alternate heater and/or cooler")
        {
            boolean heaterRequired = heatpump ? true : false
            if(heaterRequired) 
            {
                paragraph formatText("Alternate heater is mandatory with heat pump option", "red", "white")
            }
            input "heater", "capability.switch", title: "Select a switch to control an alternate heater", required: heaterRequired, submitOnChange:true, multiple: false 
            if(heater)
            {
                input "addLowTemp", "bool", title: "Turn on $heater only if OUTSIDE temperature goes below a certain threshold", submitOnChange:true
                if(heatpump || addLowTemp)
                {
                    input "lowtemp", "number", title: "low outside temperature threshold", required: true, defaultValue: 30
                }
                if(heater.hasAttribute("power") && pw) // if both are true: power meter cap for heater switch and verify status with power meter
                {
                    input "controlPowerConsumption", "bool", title:"control power consumption", submitOnChange: true, required: false
                    if(controlPowerConsumption)
                    {
                        input "maxPowerConsumption", "number", title: "Set maximum power in watts", submitOnChange:true, required:true
                        input "devicePriority", "enum", title: "Priority given to:",options: ["$heater", "$thermostat"], required: true, submitOnChange:true
                        log.warn """
devicePriority = $devicePriority
"""
                        /* if(pw) // too problematic
{
if(pw?.hasCapability("Switch"))
{
input "controlpwswitch", "bool", title: "Also control $pw as a switch", defaultValue:false, submitOnChange:true
if(controlpwswitch)
{
paragraph formatText("$pw will be toggled when the app notices that some commands are not going through properly and everytime power consumption is too high. It'll turn it back on $thermostat is needed again", "white", "grey")
}
}
}*/
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
        section("Sensors")
        {
            input "outsideTemp", "capability.temperatureMeasurement", title: "Required: select a weather sensor for outside temperature", required:true, submitOnChange:true

            input "sensor", "capability.temperatureMeasurement", title: "select a temperature sensor (optional)", submitOnChange:true, multiple:true
            if(sensor)
            {
                input "offrequiredbyuser", "bool", title: "turn off thermostat when target temperature has been reached", defaultValue: false, submitOnChange:true
            }
        }
        section("Central Thermostat")
        {
            if(thermostat){
                paragraph formatText("Make $thermostat a central thermostat for your home", "white", "blue")
                input "sync", "bool", title:"Synchronize ${thermostat} with states from a different thermostat", defaultValue:false, submitOnChange:true
                if(sync)
                {
                    input "thermostatB", "capability.thermostat", title: "select a second thermostat", required: true, multiple: true, description: null, submitOnChange:true
                    input "ignoreTherModes", "bool", title: "Ignore operating modes, synchronize set points only", defaultValue: false
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
            paragraph formatText("""auto method: (BETA) the app manages your set points based on several algebra functions and on your inputs normal method: the app manages your set points based on your inputs and some algebra (notably for humidity) 

NB: If you chose to not use a dimmer as an input source, you may have to repeat your input several times before the app 'understands' that this is a user input. There is no other way at the moment for the platform to distinguish the source of a command: from software or hardware, either way, the event handler will always return the evt.source as "DEVICE". Until that changes, the dimmer is the best way to insure that your inputs are taken into consideration as soon as you implement them. It also has the benefit to facilitate Alexa integration and voice commands if you name your dimmer 'temperature [room name]'
""", "black", "white")

            //used by both methods
            input "dimmer", "capability.switchLevel", title: "Optional but HIGHLY recommended: Use a dimmer as an input source", required: false, submitOnChange:true
            input "tellMeMore", "bool", title: "Tell me how to create a virtual dimmer", submitOnChange:true
            if(tellMeMore)
            {    
                text = """In Hubitat, just go to 'devices' and click on 'add a virtual dimmer'. Then give it a name such as 'temperature living' to facilitate its usage with your voice assistant"""
                paragraph formatText(text, "white", "blue")
            }


            if(method == "auto")
            {    
                def text = "Note that the automatic settings are still in testing mode. If you find you're not happy with it, please think of switching to the dimmer method (and don't forget to create a virtual dimmer in Hubitat)"
                paragraph formatText(text, "white", "red")

                def outside = outsideTemp?.currentValue("temperature")
                def reF = outsideTemp ? (celsius ? getCelsius(outside.toInteger()) : outside ) : (celsius ? getCelsius(77) : 77) 
                reF = reF?.toInteger()
                reF = celsius ? getCelsius(reF) : reF 
                def des = 2
                def refTempTitle = outsideTemp ? "Set an outside temperature reference (currently ${reF}F)": "Set an outside temperature reference for which you will set (below) a target variation (for example: 77)"
                //if(outsideTemp) {app.updateSetting("refTemp",[type:"number", value:reF])}
                if(!refTemp){app.updateSetting("targetVar",[type:"number", value:des])}

                input "refTemp", "number", range:"-100..180", title: refTempTitle, required: false, submitOnChange:true, defaultValue:ref
                input "targetVar", "number", title: "Set a target variation: by how much do you want inside temperature to differ from outside temp when outside temp is $reF?", required:false, range: "1..40", submitOnChange:true
                if(refTemp && targetVar)
                {
                    log.debug "refTemp = $refTemp targetVar = $targetVar"
                }
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

            input "antifreeze", "bool", title:"Optional: Customize Antifreeze",submitOnChange:true,defaultValue:false

            if(antifreeze)
            {
                input "safeValue", "number", title: "safety temperature", required:true, submitOnChange:true
                input "backupSensor", "capability.temperatureMeasurement", title: "Optional but highly recommended: pick a backup sensor (in case of network failure)", required:false

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
        section(){
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
                        input "overrideSimpleMode", "bool", title: "$doorsContacts events override simple mode (until they're closed again)"

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
            input "powersavingmode", "mode", title: "Save power when in one of these modes", required: false, multiple: true, submitOnChange: true
        }
        section(formatText("Motion Management", "white", "blue")){
            input "motionSensors", "capability.motionSensor", title: "Save power when there's no motion", required: false, multiple: true, submitOnChange:true

            if(motionSensors)
            {
                input "noMotionTime", "number", title: "after how long?", description: "Time in minutes"
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true 
            }  

            if(powersavingmode || motionSensors)
            {
                input "criticalcold", "number", title: "Set a critical low temperature", required: true
                input "criticalhot", "number", title: "Set a critical high temperature", required: true
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
        section(formatText("Simple comfort mode", "white", "blue")){
            input "UseSimpleMode", "bool", title: "Use a simple mode trigger", submitOnChange:true
            if(UseSimpleMode)
            {                
                def message = ""
                def devivesStr = ""

                def s = simpleModeButton?.size() 
                def i = 0
                input "simpleModeButton", "capability.holdableButton", title: "When ${!simpleModeButton ? "this button is" : (s > 1 ? "these buttons are" : "this button is")} pushed, work in limited mode (push again to cancel)", multiple: true, required: false, submitOnChange:true
                input "simpleModeTimeLimit", "number", title: "Optional: return to normal operation after a certain amount of time", descripition: "Time in hours", submitOnChange:true
                input "allowWindowsInSimpleMode", "bool", title:"Allow windows management, if any", defaultValue:false
                if(simpleModeButton)
                {
                    for(s!=0;i<s;i++){
                        devivesStr = devivesStr.length() > 0 ? devivesStr + ", " + simpleModeButton[i].toString() : simpleModeButton[i].toString()
                    } 
                    if(simpleModeTimeLimit)
                    {
                        message = "Limited mode will be canceled after $simpleModeTimeLimit hours or after a new button event" //. Note that $devivesStr will not be able to cancel limited mode before time is out" 
                        paragraph formatText(message, "white", "grey") 
                    }
                    message = simpleModeButton ? "$app.label will operate in limited mode when $devivesStr ${s > 1 ? "have" : "has"} been pushed and canceled when held, double tapped or pushed again. Power saving options will not be active" : ""
                    if(message) paragraph formatText(message, "white", "grey") //simpleModeButton message
                }

                def bedDevice = simpleModeTriggerType == "contact" ? simpleModeContact : simpleModeButton
                input "setSpecialTemp", "bool", title: "Keep room at a preset temperature when in $bedDevice is ${simpleModeTriggerType == "contact" ? "closed" : "pushed"}", submitOnChange:true, defaultValue:false
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
            }
            else
            {
                app.updateSetting("simpleModeButton", [type:"capability", value:null])
                app.updateSetting("simpleModeTimeLimit", [type:"number", value:null])
                app.updateSetting("allowWindowsInSimpleMode", [type:"bool", value:false])
                app.updateSetting("setSpecialTemp", [type:"bool", value:false])
                app.updateSetting("specialSubstraction", [type:"bool", value:false])
                app.updateSetting("substract", [type:"number", value:null])
                app.updateSetting("specialTemp", [type:"number", value:null])
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
                input "windows", "capability.switch", title: "Turn on those switches when home needs to cool down, wheather permitting", multiple:true, required: false, submitOnChange: true
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

                    input "windowsModes", "mode", title: "Select under which modes ALL WINDOWS can be operated", required:true, multiple:true

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


                                input "differentDuration", "bool", title: "Differentiate operation time", defaultValue:false, submitOnChange:true
                                if(!differentDuration)
                                {
                                    input "maxDuration", "number", title: "Set maximum operation time", description: "time in seconds", required: false, submitOnChange:true
                                    input "customCommand", "text", title: "custom command to stop operation (default is 'off()')", required: false, submitOnChange:true
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
                input "fancirculate", "bool", title:"Run ${thermostat}'s fan circulation when contacts are open and temp is getting too high", submitOnChange:true
                input "fancirculateAlways", "bool", title:"Run ${thermostat}'s fan circulation without interruption", submitOnChange:true
                if(fancirculate) app.updateSetting("fancirculateAlways", [type:"bool", value:false])
                if(fancirculateAlways)
                {
                    app.updateSetting("fancirculate", [type:"bool", value:false])
                    input "fanCirculateSimpleModeOnly", "bool", title:"Run fan circulation only in simple mode", submitOnChange:true
                    if(fanCirculateSimpleModeOnly) app.updateSetting("fanCirculateModes", [type:"mode", value:null])
                    if(!fanCirculateSimpleModeOnly) input "fanCirculateModes", "mode", title:"Run fan circulation only in certain modes", multiple:true, submitOnChange:true
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
def pageNameUpdate(){
    closeBoolQuestions()
    def pauseVar = atomicState.failedSensors ? "FAILED SENSORS" : "paused"
    def previousLabel = app.label // save current label
    if(atomicState.paused)
    {
        while(app.label.contains(" $pauseVar "))
        {
            app.updateLabel(app.label.minus(" $pauseVar "))
        }

        log.debug "original label = $app.label"            

        app.updateLabel(previousLabel + ("<font color = 'red'> $pauseVar </font>" )) // recreate label

        atomicState.button_name = "resume"
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
    if(simpleModeButton)
    {
        subscribe(simpleModeButton, "pushed", pushableButtonHandler)   

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
        schedule("0 0/1 * * * ?", Poll)
    }
    if(controlPowerConsumption || coolerControlPowerConsumption)
    {
        schedule("0 0/1 * * * ?", pollPowerMeters)
    }

    schedule("0 0/5 * * * ?", mainloop)


    descriptionText "END OF INITIALIZATION"

}

/************************************************EVT HANDLERS***************************************************/
def modeChangeHandler(evt){
    log.debug "$evt.name is now $evt.value"

    if(location.mode in restricted)
    {
        log.trace "$thermostat set to $restrictedThermMode due to restricted mode"
        thermostat."${restrictedThermMode}"()
        //toggleRelatedSwitch("off")  // make sure this one is off 
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
        log.trace "location not in restricted mode, resuming normal operations"
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
        atomicState.lastContactOpenEvt = now() 
        runIn(openDelay, mainloop,[data:"contactHandler"])
    }
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

        if(now() - atomicState.lastBSevent > 60000) // prevent false positives due to floating state of the simple mode trigger due to the mattress's weight (still working on this...)
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
        if(method == "auto" && !atomicState.setpointSentByApp)
        {
            log.debug "Updating automatic settings based on new dimmer level"

            def outside = outsideTemp.currentValue("temperature")
            def outsideThreshold = getOutsideThershold()
            def needSituation = outside < outsideThreshold ? "heat" : "cool"
            def newRefTemp = outside
            updateValues(evt.value)

        }
        else if(!atomicState.setpointSentByApp)
        {
            log.info "NOT AUTO METHOD"
        }
        else if(atomicState.setpointSentByApp)
        {
            log.info "command coming from this app, skipping"
        }

        atomicState.setpointSentByApp = false // always reset this variable after calling it

        //mainloop() // prevent feedback loops so both dimmer and thermostat set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def specialDimmerHandler(evt){

    log.trace "$evt.device set to $evt.value | NEW simple mode target temperature"
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
            atomicState.buttonPushed = !atomicState.buttonPushed

            atomicState.lastButtonEvent = atomicState.buttonPushed ? now() : atomicState.lastButtonEvent // time stamp when true only

            def status = atomicState.buttonPushed ? "NOW ACTIVE" : "DISABLED"
            log.info formatText("Simple Mode $status", "white", "grey")
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
    log.debug "--------- $evt.device set to $evt.value"

    if(evt.value == "auto" && autoOverride)
    {
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
        if(sync && thermostatB)
        {
            int i = 0
            int s = thermostatB.size()

            if(!ignoreTherModes)
            {
                if("$evt.device" == "$thermostat")
                {
                    //log.warn "case AM"
                    def cmd = "set${evt.name.capitalize()}"
                    for(s!=0; i<s; i++)
                    {
                        thermostatB[i]."${cmd}"(evt.value)
                        descriptionText "${thermostatB[i]} $cmd $evt.value"
                    }
                }
                else if(thermostatB.find{it.toString() == "$evt.device"})
                {
                    //log.warn "case BM"
                    def cmd = "set${evt.name.capitalize()}"
                    thermostat."${cmd}"(evt.value)
                    descriptionText "$thermostat $cmd $evt.value"
                }
            }
            else
            {
                descriptionText "ignoring operating mode sync at user request (syncing set points only)"
            }
        }
    }
}
def setPointHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        } 
        log.trace "$evt.device $evt.name $evt.value SOURCE: $evt.source"

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
                thermostat."${cmd}"(evt.value)
                descriptionText "$thermostat $cmd $evt.value"
                //
            }
            //return // must not set atomicState.setpointSentByApp back to false in this case
        }

        if(!atomicState.setpointSentByApp)
        {
            descriptionText "new $evt.name is $evt.value -------------------------------------"
            if(method == "auto" && !atomicState.setpointSentByApp)
            {
                log.debug "Updating automatic settings based on new thermostat set point input $evt.value"
                //updateValues(evt.value.toInteger())
                updateValues(evt.value)
            }

            def currDim = !dimmer ? atomicState.lastThermostatInput : dimmer?.currentValue("level")
            def thermMode = thermostat?.currentValue("thermostatMode")

            // this will be true only if thermostat is heating or cooling; therefore, dimmer won't be adjusted if off 
            // using atomicState.lastNeed == "heat" / "cool" seemed to allow exceptions... 
            boolean correspondingMode = (evt.name == "heatingSetpoint" && thermMode == "heat") || (evt.name == "coolingSetpoint" && thermMode == "cool")

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

            def inside = getInsideTemp()
            atomicState.inside = atomicState.inside != null ? atomicState.inside : inside
            def needData = getNeed(target, simpleModeActive, inside)
            def need = needData[1]
            def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter


            // make sure the therm event is same as current need
            // as to not apply a value from a differentiated thermostat mode (heat set to 75 will modify coolingSP and then trigger an event)

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
            log.trace "event generated by this app, doing nothing"
        }

        //mainloop() // prevent feedback loops so both dimmer and thermosta set points can be modified. Changes will be made on next scheduled loop or motion events
        atomicState.lastSetPoint = evt.value
    }

    atomicState.setpointSentByApp = false // always reset this static/class variable after calling it
}
def updateValues(evtVal){

    evtVal = Double.parseDouble(evtVal)
    def lastSetpoint = atomicState.lastSetPoint.toDouble()

    log.warn """
evtVal instanceof Double ? ${evtVal instanceof Double}
tempConvertVal instanceof Double ? ${tempConvertVal instanceof Double}
tempConvertVal instanceof String ? ${tempConvertVal instanceof String}
"""

    def outside = outsideTemp.currentValue("temperature")
    def outsideThreshold = getOutsideThershold()
    def needSituation = outside < outsideThreshold ? "heat" : "cool"
    def newRefTemp = outside
    def newtargetVar = Math.abs(evtVal - outside).toInteger()
    log.warn """
newtargetVar = $newtargetVar
"""
    app.updateSetting("targetVar",[type:"number",value:newtargetVar]) // update this setting
    app.updateSetting("refTemp",[type:"number", value:newRefTemp]) // we also need to update the new reference temperature in order to modify the linear equation 
    log.info """
newtargetVar = $newtargetVar
outside = $outside
needSituation = $needSituation
lastSetpoint = $lastSetpoint
new setpoint = $evtVal
"""          

    // calculate an absolute value of the difference between old and new value
    def absDifference = Math.abs(lastSetpoint - evtVal)
    // update min and max auto cool values
    if(needSituation == "cool")
    {        
        int val = 0

        logging """
maxAutoCool = $maxAutoCool instanceof String ? ${maxAutoCool instanceof String}
absDifference = $absDifference instanceof String ? ${absDifference instanceof String}
val = $val instanceof String ? ${val instanceof String}

"""

        val = maxAutoCool.toInteger() - absDifference.toInteger()    
        val = val < 70 ? 70 : val // don't go too low
        // so we lower this setting's value a bit
        app.updateSetting("minAutoCool",[type:"number",value:val])
        log.debug "minAutoCool is now $minAutoCool"
        // }
        // else { // if need warmer room, raise max
        val = maxAutoCool + absDifference
        val = val > 80 ? 80 : val // don't go too high
        app.updateSetting("maxAutoCool",[type:"number", value:val])
        log.debug "maxAutoCool is now $maxAutoCool"
        // }
    }
    // update min and max auto heat values
    if(needSituation == "heat")
    {       
        int val = 0

        logging """
minAutoHeat = $minAutoHeat instanceof String ? ${minAutoHeat instanceof String}
absDifference = $absDifference instanceof String ? ${absDifference instanceof String}
val = $val instanceof String ? ${val instanceof String}

"""

        val = minAutoHeat.toInteger() - absDifference.toInteger()    

        log.debug "minAutoHeat = $minAutoHeat instanceof String ? ${minAutoHeat instanceof String}"

        val = val < 68 ? 68 : val // don't go too low
        // so we lower this setting's value a bit        
        app.updateSetting("minAutoHeat",[type:"number", value:val])     
        log.debug "minAutoHeat is now $minAutoHeat"
        val = maxAutoHeat + absDifference
        val = val > 80 ? 80 : val // don't go too high
        app.updateSetting("maxAutoHeat",[type:"number", value:val])
        log.debug "maxAutoHeat is now $maxAutoHeat"
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

/************************************************MAIN functions*************************************************/
def mainloop(source){

    descriptionText "mainloop called by $source"

    atomicState.lastThermostatInput = atomicState.lastThermostatInput ? atomicState.lastThermostatInput : thermostat.currentValue("thermostatSetpoint")
    if(!atomicState.paused)
    {
        if(location.mode in restricted){
            descriptionText "location in restricted mode, doing nothing"
            return
        }    

        boolean simpleModeActive = simpleModeIsActive()
        boolean motionActive = Active()
        boolean contactClosed = !contactsAreOpen()
        boolean doorContactsAreOpen = doorsContactsAreOpen()

        int target = getTarget(simpleModeActive)

        def inside = getInsideTemp()
        def outside = outsideTemp.currentValue("temperature")
        descriptionText "outside temperature is $outside"
        def needData = getNeed(target, simpleModeActive, inside)
        def need = needData[1]

        def currSP = thermostat?.currentValue("thermostatSetpoint")
        //log.warn "--- $currSP"
        def thermMode = thermostat?.currentValue("thermostatMode")
        logging("need is needData[1] = $need")
        def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter


        /********************** ANTI FREEZE SAFETY TESTS *************************/
        if(atomicState.antifreeze)
        {
            log.warn "ANTI FREEZE HAS BEEN TRIGGERED"
        }
        // antifreeze precaution (runs after calling atomicState.antifreeze on purpose here)
        def backupSensorTemp = backupSensor ? backupSensor.currentValue("temperature"): inside

        if(antifreeze && !atomicState.setPointOverride){
            def safeVal = safeValue != null ? safeValue : criticalcold != null ? criticalcold : celsius ? getCelsius(67) : 67

            if(inside <= safeVal || backupSensorTemp <= safeVal){

                atomicState.antifreeze = true

                log.warn """$thermostat setpoint set to 72 as ANTI FREEZE VALUE
inside = $inside
safeValue = $safeVal
"""
                thermostat.setThermostatMode("heat")
                atomicState.resendAttempt = now()
                thermostat.setHeatingSetpoint(72)
                windows?.off() // make sure all windows linked to this instance are closed
                heater?.on()// turn on the alternate heater, if any
                sendNotification()
                return
            }
            else if(atomicState.antifreeze)
            {
                atomicState.antifreeze = false
                log.trace "END OF ANTI FREEZE"
            }

        }
        else if(!atomicState.setPointOverride)// default & built-in this app's anti freeze
        {
            def defaultSafeTemp = criticalcold == null ? 58 : criticalcold <= 58 ? criticalcold : 58 
            if(inside <= defaultSafeTemp || backupSensorTemp <= defaultSafeTemp){
                log.warn """ANTIFREEZE (DEFAULT) IS TRIGGERED: 
inside = $inside
backupSensorTemp = $backupSensorTemp
defaultSafeTemp = $defaultSafeTemp (is this user's criticalcold set temp ? ${criticalcold == null ? false : true}
"""
                windows?.off() // make sure all windows linked to this instance are closed
                thermostat.setThermostatMode("heat")
                atomicState.resendAttempt = now()
                thermostat.setHeatingSetpoint(72)
                atomicState.antifreeze = true
                //sendNotification()
            }
            else
            {
                atomicState.antifreeze = false
            }
        }
        //log.warn "mode: ${thermostat.currentValue("thermostatMode")}"
        if(autoOverride && thermMode == "auto"){
            atomicState.override = true // wanted redundency for the rare cases when evt handler failed
            def overrideDur = overrideDuration != null ? overrideDuration : 0
            def timeLimit = overrideDur * 60 * 60 * 1000
            def timeStamp = atomicState.overrideTime

            if(overrideDur != 0 && overrideDur != null)
            {
                if(now() - timeStamp > timeLimit)
                {
                    log.warn "END OF OVERRIDE, turning off $thermostat"
                    atomicState.override = false
                    thermostat.setThermostatMode("off")
                    atomicState.offAttempt = now()
                }
                else 
                {
                    log.warn "OVERRIDE - AUTO MODE - remaining time: ${getRemainTime(overrideDur, atomicState.overrideTime)}"
                    return
                }
            }
            else 
            {
                log.warn "OVERRIDE - APP PAUSED DUE TO AUTO MODE (no time limit)"
                return
            }
        }

        if(thermostat.currentValue("thermostatFanMode") == "on" && contactClosed && fancirculate && atomicState.fanOn){
            if(!fancirculateAlways)
            {
                descriptionText "Setting fan back to auto"
                if(thermostat.currentValue("thermostatFanMode") != "auto")thermostat.setThermostatFanMode("auto")
            }
            atomicState.fanOn = false 
        }
        if(fancirculateAlways)
        {
            boolean inFanCirculateMode = fanCirculateModes ? location.mode in fanCirculateModes : false
            if(fanCirculateSimpleModeOnly && !simpleModeActive)
            {
                if(thermostat.currentValue("thermostatFanMode") != "auto")
                {
                    descriptionText "Setting fan back to auto because simple mode not currently enabled"
                    thermostat.setThermostatFanMode("auto")
                }
            }
            else if(fanCirculateModes && inFanCirculateMode)
            {
                if(thermostat.currentValue("thermostatFanMode") != "auto")
                {
                    descriptionText "Setting fan back to auto because location is no longer in fan circulate mode"
                    thermostat.setThermostatFanMode("auto")
                }
            }
            else
            {
                descriptionText "fan is always on at user's request"
                if(thermostat.currentValue("thermostatFanMode") != "on")
                {
                    thermostat.setThermostatFanMode("on")
                }
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
            if((heatpump && heatpumpConditionsTrue) || (tooMuchPower && devicePriority != "$thermostat"))
            {
                if(thermostat.currentValue("thermostatMode") != "off")
                {
                    thermostat.setThermostatMode("off") // so as to take precedence over any other condition 
                    atomicState.offAttempt = now()
                    //runIn(4, toggleRelatedSwitch, [data:"off"]) // switch that powers this specific thermostat, if any and if option was enabled // 4secs to give time for proper off() method to take effect
                    log.info "$thermostat turned off due ${preferCooler && atomicState.lastNeed == "cool" ? "to preferCooler option" : "to heatpump or power usage conditions"}"
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

                if(timeToRefreshMeters && !timeIsUp && !timeIsUpOff) // make sure to attempt a refresh before sending more commands
                {
                    descriptionText "pwLow = $pwLow refreshing $pw because power is $pwVal while it should be ${need == "off" ? "below 100 Watts":"above 100 Watts"}"
                    pollPowerMeters()
                }
                else if(timeIsUp && pwLow && need != "off")
                {
                    descriptionText "$app.label is resending ${cmd}(${target}) due to inconsistency in power value"
                    atomicState.resendAttempt = now() 
                    atomicState.setpointSentByApp = true
                    thermostat."${cmd}"(target) // resend cmd
                    pollPowerMeters()
                }
                else if(timeIsUpOff && need == "off" && !pwLow && !doorsContactsAreOpen())
                {
                    log.warn("$thermostat should be off but still draining power, resending cmd")
                    atomicState.offAttempt = now() 
                    thermostat.setThermostatMode("off")
                    atomicState.offAttempt = now()
                    thermostat.off()
                    //runIn(10, toggleRelatedSwitch, [data:"off"])
                    pollPowerMeters()
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
target + swing = ${target + swing}
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

                    // if(!atomicState.setPointOverride)     
                    //toggleRelatedSwitch("on") // make sure the power plug, if any, is turned on since this is an emergency heating/cooling 

                    def temporarySetpoint = need == "cool" ? 62 : need == "heat" ? 85 : 72 // 72 if by any chance this went wrong
                    def currCSP = thermostat.currentValue("coolingSetpoint")
                    def currHSP = thermostat.currentValue("heatingSetpoint")
                    boolean notSet = need == "cool" && currSP != temporarySetpoint ? true : need == "heat" && currHSP != temporarySetpoint ? true : false 
                    notSet = notSet && currCSP > currHSP + 2 // we want to keep a +2-2 difference between heating and cooling setpoints to prevent cricling down values
                    if(notSet)
                    {
                        atomicState.setpointSentByApp = true // prevents new inputs to be taken as new heuristics // must be reset at setDimmer

                        log.trace "setting $thermostat to $need"
                        thermostat.setThermostatMode(need)
                        log.debug "$thermostat $cmd to $temporarySetpoint"
                        thermostat."${cmd}"(temporarySetpoint)


                        if(need == "cool") // prevent thermostat firmware from circling down its setpoints
                        {
                            thermostat.setHeatingSetpoint(temporarySetpoint-2)
                            log.debug "$thermostat heatingsetpoint set to ${temporarySetpoint-2} to prevent circling down SP's"
                        }
                        else if(need == "heat") // prevent thermostat firmware from circling down its setpoints
                        {
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
                    if(manageThermDiscrepancy) log.trace formatText("END OF SET POINT OVERRIDE - BACK TO NORMAL OPERATION", "white", "grey")
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

            boolean heatpumpConditionsTrue = outside < lowTemp
            // if forececommand true and need is not off make sure we're not under heat pump cold conditions 
            forceCommand = forceCommand && need != "off" ? !heatpumpConditionsTrue : forceCommand // if need = off then apply forcecommand functions to make sure to turn it off

            if(!atomicState.setPointOverride && thermMode != need || forceCommand && contactClosed)
            {
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


                if(need != "off" || forceCommand || (need == "off" && (sensor || offrequiredbyuser)))
                {                
                    if((!OperatingStateOk || now() - atomicState.lastSetTime > 5 * 60 * 1000) || need == "off" || forceCommand)
                    {
                        atomicState.coolerTurnedOnTimeStamp = atomicState.coolerTurnedOnTimeStamp != null ? atomicState.coolerTurnedOnTimeStamp : 31*60*1000

                        if(need == "cool" && preferCooler && (outside < preferCoolerLimitTemperature || !preferCoolerLimitTemperature))
                        {
                            checkEfficiency(coolerNotEfficientEnough, boost, thermMode)
                        }
                        else {
                            if(thermMode != need){
                                thermostat.setThermostatMode(need) // set needed mode
                            }
                            else 
                            {
                                logging "$thermostat already set to $need"
                            }
                        }

                        atomicState.lastSetTime = now()

                        if(need in ["cool", "heat"])
                        {
                            atomicState.lastSetTime = now() // prevent switching from heat to cool too frequently 
                            //during conditions that might have been uncounted for, like during shoulder season
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
            else if(need != "off" && contactClosed)
            {
                descriptionText "Thermostat already set to $need"
            }

            //log.trace "atomicState.setPointOverride = $atomicState.setPointOverride"
            if(!atomicState.setPointOverride)
            {
                if(need != "off" && currSP != target && !thermTempDiscrepancy)
                {
                    atomicState.setpointSentByApp = true
                    thermostat."${cmd}"(target)   // set target temp
                    atomicState.resendAttempt = now() // needs to be updated here otherwise it'll resend immediately after since last one was long ago at this point
                    logging("THERMOSTAT SET TO $target (564fdevrt)")
                }
                else if(need != "off" && !thermTempDiscrepancy)
                {
                    logging("Thermostat already set to $target")
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

            virtualThermostat(need) // redundancy due to return statement above
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

    if(!coolerNotEfficientEnough && thermMode != "off" && !boost)
    {
        log.debug """preferCooler = true and need = cool
thermosat kept off ${preferCoolerLimitTemperature ? "unless outside temperature reaches $preferCoolerLimitTemperature":""} 56er"""
        thermostat.setThermostatMode("off")
        atomicState.offAttempt = now()
    }
    else if(coolerNotEfficientEnough || boost)
    {
        log.trace "${boost ? "boosting with $thermotat at user's request" : "$cooler is not efficient enough turning $thermostat back on"} 44JKD"
        if(thermMode != "cool")
        {
            thermostat.setThermostatMode("cool") // will run as long as inside > target + efficiencyOffset
            atomicState.resendAttempt = now()
        }
    }
}
def toggleRelatedSwitch(cmd){
    /*if(controlpwswitch)
{
if(pw?.currentValue("switch") != cmd)
{
pw?."${cmd}"()   
}
else
{
descriptionText "$pw already turned $cmd"
}
}*/
}
def sendNotification(){
    def message = "Temperature is too low at $thermostat, antifreeze is now active. Please make sure everything is ok"

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
    log.warn "Resetting forceCommand counter"
    atomicState.forceAttempts = 0   
}
def setDimmer(val){

    if(!atomicState.setPointOverride)
    {
        if(dimmer)
        {
            atomicState.setpointSentByApp = true
            dimmer.setLevel(Math.round(Double.parseDouble(val))) // some thermostats will parse set points as double, 
            //so it needs to be rounded so as to be parsed as a string in the dimmer driver        
            descriptionText "$dimmer set to $val BY THIS APP"
        }
        else
        {
            def thisVal = Math.round(Double.parseDouble(val))
            atomicState.lastThermostatInput = thisVal            
            //atomicState.setpointSentByApp = true   // not applicable in this case since it won't trigger any device event
            descriptionText "atomicState.lastThermostatInput set to $thisVal"
        }
    }
    else
    {
        log.trace "SETPOINT OVERRIDE DUE TO THERMOSTAT DISCREPANCY NOT CHANGING DIMMER VALUE"
    }

    atomicState.setPointOverride = false


}
def virtualThermostat(need){

    logging "virtualThermostat need = $need atomicState.lastNeed = $atomicState.lastNeed"

    def outsideTemperature = outsideTemp?.currentValue("temperature") // only needed if electric heater here
    def lowTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? getCelsius(28) : 28 : celsius ? getCelsius(40) : 40 
    def highTemperature = lowtemp ? lowtemp : heatpump && !lowtemp ? celsius ? getCelsius(28) : 28 : celsius ? getCelsius(40) : 40 
    boolean lowLimitReached = !thermostat ? true : (heater || heatpump) && (addLowTemp || heatpump) ? outsideTemperature < lowTemperature : true 
    //if heatpump, lowLimitReached is when it's too cold outside for a heatpump to remain efficient, or if threshold has been reached so the heater has to take over, if any...
    //if heater and no heatpump, lowLimitReached is when it's so cold that heater has to come and help
    //if heater AND no thermostat, heater runs all the time when needed, no low limit so lowLimitReached returns true

    /*    log.warn """
heatpump ? $heatpump
!thermostat ? ${!thermostat}
lowlimit reached ? $lowLimitReached
lowtemp = $lowtemp
outsideTemperature = $outsideTemperature
"""
*/
    if(heater)
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
                log.trace "both $thermostat and $heater are using too much power but heater needs to stay on due to outside temperature being too low for the heat pump to remain efficient. $thermostat should be off" 
            }
            // if device priority is not the heater while outside temp is low and heatpump true, we need to keep the heater on so tooMuchPower must be false
            // in the unlikely but still possible case where the thermostat is not already off 
        }
        if(tooMuchPower && devicePriority != "$heater") // if thermosat isn't priority it's overriden if it's a heatpump and low temp outside is true
        {
            need = "off"
            log.warn "$thermostat and $heater use too much power at the same time. Turning $heater off since $thermostat has precedence"
        }

        if(need == "heat" && atomicState.lastNeed == "cool")
        {
            if(lowLimitReached)
            {
                heater?.on() 
                logging "Turning on $heater "  
            }
            else
            {
                descriptionText "$heater not turning on because low temp limit outside hasn't been reached yet"
                logging("Turning $heater off")
                heater?.off()
            }

        }
        else 
        {
            logging("Turning $heater off")
            heater?.off()
        }
    }
    if(cooler)
    {
        boolean tooMuchPower = false

        if(preferCooler)
        {
            // user wants the cooler to run as main device and thermostat as secondary device
            // don't change the need value
        }
        else if(coolerControlPowerConsumption)
        {
            if(cooler?.hasCapability("powerMeter") || cooler?.hasAttribute("power"))
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

        if(need == "cool" && cooler?.currentValue("switch") != "on"){
            log.trace "turning $cooler on"
            cooler?.on()
            atomicState.coolerTurnedOnTimeStamp = now()
        }
        else if(need == "off" && cooler?.currentValue("switch") != "off"){
            log.trace "turning $cooler off"
            cooler?.off()
        }
        else if(need != "off")
        {
            log.trace "virtual thermostat need = $need"
            if(cooler || heater) 
            {
                if(need == "cool" && cooler?.currentValue("switch") != "on" ) log.trace "cooler is still off"
                if(need == "heat" && heater?.currentValue("switch") != "on") log.trace "heater is still off"
            }
        }
    }
}
def windowsControl(target, simpleModeActive, inside, outsideTemperature, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh){

    // motionActive argument not sent through because in getNeed() it's mixed with simpleModeActive
    // so that would open windows every time simple mode would be active instead of cooling the room with AC as needed

    //atomicState.lastClosingTime = 360000000 //TESTS

    if(controlWindows && windows && (!simpleModeActive || allowWindowsInSimpleMode) && !atomicState.override)
    {
        def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp
        boolean tooHumid = humidity >= 90 ? true : humidity >= humThres 
        boolean contactCapable = windows.any{it -> it.hasCapability("ContactSensor")}//?.size() == windows.size() 
        boolean someAreOff =  contactCapable ? (windows.findAll{it?.currentValue("contact") == "closed"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "off"}?.size() > 0)
        boolean someAreOpen = contactCapable ? (windows.findAll{it?.currentValue("contact") == "open"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "on"}?.size() > 0)
        boolean withinRange = outsideTemperature < outsidetempwindowsH && outsideTemperature > outsidetempwindowsL // stric temp value

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
        boolean openSinceLong = lastOpeningTime > 15.0 && someAreOpen // been open for more than 15 minutes

        atomicState.lastClosingTime = atomicState.lastClosingTime ? atomicState.lastClosingTime : (atomicState.lastClosingTime = now()) // make sure value is not null
        double lastClosingTime = (now() - atomicState.lastClosingTime) / 1000 / 60 
        lastClosingTime = lastClosingTime.round(2)
        boolean closedSinceLong = lastClosingTime > 10.0 && someAreClosed // been open for more than 30 minutes

        boolean tooColdInside = inside <= target - 8 
        //log.warn "tooColdInside = $tooColdInside : inside = $inside && target = $target"
        //closing error management for safety, if cmd didn't go through for whatever reason and temp went too low, force close the windows
        boolean exception = someAreOpen && ((atomicState.closedByApp && now() - lastClosingTime > 30 && tooColdInside) || (!outsideWithinRange && tooColdInside))
        long elapsed = now() - lastClosingTime
        def elapsedseconds = elapsed/1000
        def elapsedminutes = elapsed/1000/60
        if(exception) {log.warn "$windows still open! EMERGENCY CLOSING WILL BE ATTEMPTED"}

        // allow for more frequent window operation when outside temp might be low enough to cool the room fast
        boolean outsideSubstantiallyLowEnough = outside < 71 && outside > outsidetempwindowsL
        float timeBtwWinOp = outsideSubstantiallyLowEnough ? 5.0 : 30// 5 min if it's cool enough outside, otherwise, give it 30 min before reopening

        boolean enoughTimeBetweenOpenAndClose = ((now() - atomicState.lastOpeningTime) / 1000 / 60) > 10.0 || inside < target - swing //-> give it a chance to cool down the place
        boolean enoughTimeBetweenCloseAndOpen = ((now() - atomicState.lastClosingTime) / 1000 / 60) > timeBtwWinOp //-> don't reopen too soon after closing

        boolean needToClose = (enoughTimeBetweenOpenAndClose  && ((inside > target + (swing * 3) && openSinceLong) || inside < target - swing || insideTempIsHopeLess)) || !outsideWithinRange
        boolean needToOpen = (enoughTimeBetweenCloseAndOpen && (inside > target + swing && !needToClose)) && outsideWithinRange //|| amplitudeTooHigh) // timer ok, too hot inside + within range (acounting for humidity) and no discrepency


        if(INpwSavingMode || !Active())
        {
            needToOpen = atomicState.lastNeed = "cool" && inside >= 74 && outside < inside + 2 && !tooHumid
            descriptionText "${needToOpen ? "${windows.join(", ")} open to regulate temp while in power saving mode" : "windows are to remain closed"}"
        }

        logging formatText("""
**********************WINDOWS************************
inWindowsModes = $inWindowsModes
${windows.join(",")} ${contactCapable ? "${(windows.size() > 1) ? "have":"has"} contact capability" : "${(windows.size() > 1) ? "don't have":"doesn't have"} contact capability"}
closed: ${windows.findAll{it?.currentValue("contact") == "closed"}.join(",")}
Open: ${windows.findAll{it?.currentValue("contact") == "open"}}.join(",")}
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
inside > target + (swing * 2) : ${inside > target + (swing * 2)}
inside > target + swing : ${inside > target + swing}
inside < target - swing       : ${inside < target - swing}
enoughTimeBetweenOpenAndClose : $enoughTimeBetweenOpenAndClose
enoughTimeBetweenCloseAndOpen : $enoughTimeBetweenCloseAndOpen
outsideSubstantiallyLowEnough = $outsideSubstantiallyLowEnough // allows to bypass enoughTimeBetweenCloseAndOpen 
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
</div>
""", "white", "green")

        def causeClosing = "${needToClose ? "WINDOWS CLOSED OR CLOSING BECAUSE: ${enoughTimeBetweenOpenAndClose && inside > target + (swing * 2) && openSinceLong ? "enoughTimeBetweenOpenAndClose && inside > target + (swing * 2) && openSinceLong" : inside < target - swing ? "inside < target - $swing" : !outsideWithinRange ? "!outsideWithinRange" : insideTempIsHopeLess ? "insideTempIsHopeLess" : !someAreOpen ? "Already closed" : atomicState.lastNeed == "heat" ? "atomicState.lastNeed = heat" : "FIRE THE DEVELOPER IF THIS MESSAGE SHOWS UP"}":""}"


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
                                    //log.debug "${windows[i]} is the right device"
                                }
                            }
                            descriptionText message
                        }
                        else {
                            message = "opening $windows"
                            descriptionText message
                            windows.on()
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
                            thermostat.setThermostatMode("off")
                            atomicState.offAttempt = now()
                        }
                        if(!openMore)
                        {
                            atomicState.lastOpeningTime = now()
                            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                            atomicState.outsideTempAtTimeOfOpening = outsideTemperature
                            atomicState.insideTempAtTimeOfOpening = inside
                        }
                        atomicState.openByApp = true
                        atomicState.closedByApp = false

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
                                    max = settings.find{it.key == "maxDuration${i.toString()}"}?.value.toInteger()
                                    min = settings.find{it.key == "windowsDuration${i.toString()}"}?.value.toInteger()
                                    log.warn "found : $device with max = $max min = $min"

                                    time = getWindowsTimeOfOperation(outsideTemperature, max, min)
                                    runIn(time, stop)

                                }
                            }
                        }
                        //log.warn message

                    }
                    else
                    {
                        //descriptionText "$windows were not closed by this app" // might be inadequate since atomicState.closedByApp = false after opening
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
                    if(exception) {
                        if(windows.any{it.hasCapability("Switch Level")}){ windows.setLevel(100) }
                    }
                    if(doorsManagement && doorContactsAreOpen && otherWindows?.currentValue("switch") == "on" && atomicState.otherWindowsOpenByApp)
                    {
                        atomicState.otherWindowsOpenByApp = false
                        log.warn "closing $otherWindows"
                        otherWindows?.off()
                    }
                    //log.debug "56FG"
                    atomicState.openByApp = false
                    atomicState.closedByApp = true
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
                    otherWindows?.off()
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
        descriptionText "skipping windows management due to simple mode trigger mode"
    }
    else if(atomicState.override)
    {
        descriptionText "Override mode because $thermostat is set to 'auto'"
    }


}

/************************************************DECISIONS******************************************************/
def getTarget(simpleModeActive){
    int target = 70 // default value
    def inside = getInsideTemp()

    if(method == "auto")
    {
        target = getAutoVal()
    }
    else
    {
        //log.warn "dimmer = $dimmer dimmer?.currentValue(level) = ${dimmer?.currentValue("level")}"
        target = !dimmer ? atomicState.lastThermostatInput.toInteger() : dimmer?.currentValue("level").toInteger()

    }

    // safety checkup for when alexa misinterpret a command to a light dimmer and applies it to the dimmer used by this app
    def maxHi = celsius ? getCelsius(88) : 88 // assuming no one would be stupid enough to set their thermostat to 88, if so it's interpreted as a problem by the app
    def minLow = celsius ? getCelsius(30) : 30 // same, but when setpoint is too low
    boolean problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow & lastNeed == "cool" ? true : false
    def lastNeed = atomicState.lastNeed
    logging """
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
"""

    if(problem)
    {

        log.warn "There's a problem with current target temperature ($target). Readjusting from $thermostat setpoints"
        target = thermostat.currentValue("thermostatSetpoint")
        // fix the dimmer's value if any dimmer
        dimmer?.setLevel(target)
        log.warn "************ $app.name successfuly fixed its target temperature data point! ********" 
    }
    problem = target >= maxHi & lastNeed == "heat" ? true : target <= minLow && lastNeed == "cool" ? true : false
    if(problem)
    {
        log.warn "${thermostat}'s values are as inconsistent as ${dimmer ? "${dimmer}'s" : "the previous'"}, meaning data retrieval failed. Applying safe values instead until next user's input"
        target = celsius ? getCelsius(72) : 72 // safe value
    }

    if(simpleModeActive)
    {
        if(doorsContactsAreOpen() && overrideSimpleMode)
        {
            descriptionText "some doors are open: simple mode trigger mode ignored at user's request"
        }
        else if(setSpecialTemp || specialSubstraction)
        {       
            target = specialSubstraction ? target - substract : specialTemp && specialDimmer ? specialDimmer.currentValue("level") : specialTemp

            descriptionText "target temperature ${substract ? "(specialSubstraction)":"(specialTemp)"} is: $target and last recorded temperature is ${inside}"
            return target // END due to simple mode trigger mode
        }
        else
        {
            descriptionText "target temperature is: $target and last recorded temperature is ${inside}"
            return target // return the default value
        }
    } 
    log.trace "target temperature is: $target and current temperature is ${inside} (swing = $atomicState.swing)"


    return target
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
    descriptionText "Inside humidity is ${result}%"
    return result
}
def getNeed(target, simpleModeActive, inside){

    def humidity = outsideTemp?.currentValue("humidity") 
    def insideHum = getInsideHumidity() // backup for windows and value used for negative swing variation when cooling   
    humidity = humidity != null ? humidity : (insideHum != null ? insideHum : 50)
    boolean doorContactsAreOpen = doorsContactsAreOpen()
    boolean INpwSavingMode = powersavingmode && location.mode in powersavingmode && !simpleModeActive && (!doorContactsAreOpen && doorsOverrideMotion)
    boolean inWindowsModes = windows && location.mode in windowsModes
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

    boolean needCool = !simpleModeActive ? (inWindowsModes ? outsideTemperature >= outsideThres && inside >= target + swing : outsideTemperature >= outsideThres && inside >= target + swing) : outsideTemperature >= outsideThres + 5 && inside >= target + swing


    logging"""
inside = $inside
target = $target
swing = $swing
outsideTemperature >= outsideThres + 5 = ${outsideTemperature >= outsideThres + 5}
outsideTemperature = $outsideTemperature
outsideThres + 5 = ${outsideThres + 5}
needCool = $needCool
simpleModeActive = $simpleModeActive

"""
    boolean needHeat = !simpleModeActive ? (outsideTemperature < outsideThres /* makes heat run during summer... || amplitudeTooHigh*/) && inside <= target - swing : inside <= target - swing && outsideTemperature < outsideThres

    //log.warn "inside = $inside inside >= target + swing : ${inside >= target + swing} |||needCool=$needCool"

    boolean motionActive = Active() || simpleModeActive

    // shoulder season management: simple mode trigger forces ac to run despite cold outside if it gets too hot inside
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
                atomicState.lastNeed = need1
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
            if(needHeat) // heating need supercedes cooling need in order to prevent amplitude paradox
            {
                descriptionText "needHeat true"
                need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
                need1 = "heat"
                atomicState.lastNeed = need1
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
        }
        else if(offrequiredbyuser)
        {
            need0 = "off"
            need1 = "off"
            logging("need set to OFF")
        }
        else if(!offrequiredbyuser)
        {
            need0 = atomicState.lastNeed.capitalize()
            need1 = atomicState.lastNeed
            descriptionText """Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)
Temperature managed by unit's by thermostat's firmware directly
need0 = $need0
need1 = $need1
atomicState.lastNeed = $atomicState.lastNeed

"""
        }

        // log.warn "POWER SAVING MODE NOT ACTIVE" 
    }
    else   // POWER SAVING MODE OR NO MOTION OR CONTACTS OPEN     
    { 
        // log.warn "POWER SAVING MODE ACTIVE" 

        def cause = !motionActive ? "no motion" : (INpwSavingMode ? "power saving mode" : (!contactClosed ? "Contacts Open" : "UNKNOWN CAUSE - SPANK DEVELOPPER"))
        cause = cause == "Contacts Open" ? "${cause}: ${atomicState.listOfOpenContacts}" : cause
        def message = ""

        logging """
inside < criticalhot :  ${inside < criticalhot}
inside > criticalcold :  ${inside > criticalcold}

"""        
        need0 = "off"
        need1 = "off"

        if(inside > criticalhot)
        {
            log.debug formatText("POWER SAVING MODE EXPCETION: TOO HOT! ($cause criticalhot = $criticalhot)", "black", "red")

            if(!contactClosed) // if contacts open then just fan circulate
            {
                message = formatText("FAN CIRCULATE DUE TO EXCESSIVE HEAT AND CONTACTS OPEN: $atomicState.listOfOpenContacts", "black", "red")
                if(fancirculate)
                {
                    thermostat.setThermostatFanMode("on")
                    atomicState.fanOn = true // this global is to ensure user's override
                }
                need0 = "off"
                need1 = "off"
            }
            else 
            {
                need0 = "Cool"
                need1 = "cool"
            }
        }
        else
        {
            // thermostat.setThermostatFanMode("auto")
            atomicState.fanOn = false
        }

        if(inside < criticalcold)
        {
            message = formatText("POWER SAVING MODE EXPCETION: TOO COLD! ($cause)", "white", "blue")    
            need0 = "Heat"
            need1 = "heat"
        }
        else 
        {
            message = formatText("POWER SAVING MODE ($cause)", "white", "#90ee90")   
        }

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
!overrideSimpleMode = ${!overrideSimpleMode}
simpleModeIsActive() = ${simpleModeIsActive()}
"""


    if(UseSimpleMode && ((simpleModeActive && !doorContactsAreOpen) || (!simpleModeActive && !overrideSimpleMode && simpleModeActive)))
    {
        log.info formatText("Simple Mode Enabled", "white", "grey")
    }
    else if(UseSimpleMode && simpleModeActive && overrideSimpleMode && doorsOpen)
    {
        log.info formatText("Simple Mode Called but NOT active due to doors being open", "white", "grey")
    }
    else if(UseSimpleMode)
    {
        descriptionText formatText("Simple Mode Disabled", "white", "grey")
    }

    need = [need0, need1]

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
inside < target = ${inside < target}

simpleModeActive = $simpleModeActive (simpleModeTriggerType = $simpleModeTriggerType)
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

    descriptionText "current need: ${need1 != "off" ? "${need1}ing" : need1} contacts open? ${contactsAreOpen()}"
    need = [need0, need1]
    return need

}
def getAutoVal(){

    def outside = outsideTemp?.currentValue("temperature") 
    def need = outside >= getOutsideThershold() ? "cool" : "heat"
    def result = celsius ? getCelsius(73):73 // just a temporary default value  
    def defaultV = result
    //def humidity = outsideTemp?.currentValue("humidity") // outside humidity
    def humidity = getInsideHumidity() // in auto mode we evaluate based only on inside humidity
    humidity = humidity != null ? humidity : 50 // assume 50 as a temporary value to prevent errors when a has just been installed by user and humidity value has yet to be parsed

    def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp

    def variation = getVariationAmplitude(outside, need)

    descriptionText "variation amplitude = $variation | absolute need (auto method, not from getNeed()) is $need "

    result = need == "cool" ? humidity >= humThres ? outside - variation + 1 : outside - variation : need == "heat" ? humidity >= humThres ? outside + variation + 1 : outside + variation : "ERROR"


    if(result == "ERROR") { 
        log.error """ERROR at getAutoVal()
need = $need
atomicState.lastNeed = $atomicState.lastNeed
humidity = $humidity
insideHum = $insideHum
humThres = $humThres
outside = $outside
"""     

        return defaultV
    }

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

    //https://www.desmos.com/calculator/uc9391tw1f

    def y = 0 // value to find
    def x = outside // current temperature outside
    def ya = targetVar != null ? targetVar : 1 // coressponding difference required when outside temperature = xa
    ya = ya.toFloat()
    def xa = refTemp != null ? refTemp : 77 // 
    xa = xa.toFloat()
    def slope = 0.8
    def m = need == "cool" ? slope : (slope + 0.1)*-1  // slope 
    //def a = -1 // offset

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)

    logging """
y = $y
outside = $outside
outside instance of String ? ${outside instanceof String}
ya instance of String ? ${ya instanceof String}
slope instance of String ? ${slope instanceof String}
y instanceof String ? ${y instanceof String}
"""
    //y = y < max ? y : max // deprecated
    y = y < 1 ? 1 : y

    logging "linear result for amplitude variation for auto temp = ${y.toInteger()}"
    return y.toInteger()

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

    atomicState.disabledSensors = atomicState.disabledSensors ? atomicState.disabledSensors : []
    atomicState.disabledSensors = atomicState.disabledSensors.size() != 0 ? atomicState.disabledSensors : []

    if(sensor)
    {
        def sum = 0
        int i = 0
        int s = sensor.size()
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
                log.warn "$device hasn't returned any event in the last 72 hours!"
                if(sensor.size() > 1)
                {
                    atomicState.disabledSensors += "$device"
                    log.trace "$device is being ignored because it is unresponsive."
                    sum -= val
                }
            }
            else if(atomicState.disabledSensors.any{it->it == device.displayName}) 
            {
                log.warn "deleting $device from disabledSensors because it is active again"
                atomicState.disabledSensors -= "$device"
            }
            if(device.hasCapability("battery"))
            {
                def batteryValue = device.currentValue("battery")
                if(batteryValue <= 30)
                {
                    log.warn "WARNING ! ${device}'s BATTERY LEVEL IS LOW!!!"
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
            thermostat.setThermostatMode("auto") // set the thermostat to auto for safety. 
            atomicState.failedSensors = true // force the user to attend the fact that all sensors are irresponsive
            return 0
        }
        else
        {
            atomicState.failedSensors = false
        }
        inside = sum/s
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

    descriptionText "${sensor?"average":""} temperature in this room is: $inside (source: ${sensor ? "${sensor.join(", ")} ${sensor.collect{it.currentValue("temperature")}.join("°F, ")}°F":thersmostat} )"


    inside = inside.toDouble()
    inside = inside.round(2)
    atomicState.inside = inside
    return inside
}
def getOutsideThershold(){

    // define the outside temperature as of which heating or cooling are respectively required 
    // modulated with outside humidity 

    def humidity = outsideTemp?.currentValue("humidity") 
    humidity = humidity != null ? humidity : celsius ? getCelsius(50):50 // prevents error from recently installed thermostats
    if(humidity == null){
        def message = """$outsideTemp is not returning any humdity value - it may be because it was just included; if so, this will resolve ont its own.
If this message still shows within an hour, check your thermostat configuration..."""
        formatText(message, red, white)
    }
    def outsideTemperature = outsideTemp?.currentValue("temperature")

    // the higher the humidity, the lower the threshold so cooling can happen 
    def y = null // value to find
    def x = humidity ? humidity : 50 // 50 if no data returned to allow the app to run
    def ya = celsius ? getCelsius(60):60 // coressponding outside temperature value for when humidity = xa 
    def xa = 60 // humidity level
    def m = -0.1 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //log.warn "y = $y"
    def lo = celsius ? getCelsius(60):60
    def hi = celsius ? getCelsius(72):72
    def result = y > hi ? hi : (y < lo ? lo : y) // max and min

    descriptionText "cool/heat decision result = ${y != result ? "$result (corrected from y=$y)" : "$result"} (humidity being ${humidity < 40 ? "low at ${humidity}%" : "high at ${humidity}%"})"
    return result

}
def getHumidityThreshold(){ // must be called only upon windows opening decision
    def humidity = outsideTemp?.currentValue("humidity") 
    humidity = humidity != null ? humidity : celsius ? getCelsius(50):50
    def outsideTemperature = outsideTemp?.currentValue("temperature")

    // we want to set a humidity threshold depending on outside temperature
    // humidity of 98, even an outside temp of 70 will feel too warm so we don't open the windows
    // but humidity of 98 at 60F, it's ok to use outside air to cool down the house

    def y = null // value to find
    def x = outsideTemperature 
    def ya = celsius ? getCelsius(70):70 // coressponding temperature at which xa humidity level is a threshold for when humidity = xa
    def xa = 70 // humidity level
    def m = -3 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)

    descriptionText "Humidity threshold = $y"

    return y

}
def getLastMotionEvents(Dtime, testType){

    /******************************BEFORE COLLECTION**********************************************************/
    //this is faster to check if a sensor is still active than to collect past events, so return true if it's the case    
    if(motionSensors.any{it -> it.currentValue("motion") == "active"})
    {
        descriptionText "Sensor still active: ${motionSensors.findAll{it.currentValue("motion") == "active"}}"
        events = 10 // this is not a boolean method but called by a boolean variable
        if(testType == "motionTest") descriptionText "$atomicState.activeMotionCount active events in the last ${Dtime/1000/60} minutes (atomicState)"
        return events // so we must return a number equal or greater than 1
    }
    /*********************************************************************************************************/

    int s = motionSensors.size() 
    int i = 0
    def thisDeviceEvents = []
    int events = 0
    //Dtime = 600 * 60 * 1000 
    for(s != 0; i < s; i++) // collect active events
    { 
        def device = motionSensors[i]                
        thisDeviceEvents = device.eventsSince(new Date(now() - Dtime), max).findAll{it.name == "motion" && it.value == "active"} // collect motion events for each sensor separately
        //log.debug "Collected ${thisDeviceEvents.size()} evts for $device"
        events += thisDeviceEvents.size()
        //log.trace "added ${thisDeviceEvents.size()} events to collection = $events"
    }
    // eventsSince() can be messy // hubitat still doesn't acknowledge the issue despite several tickets and screenshots. 
    atomicState.activeMotionCount = atomicState.activeMotionCount ? atomicState.activeMotionCount : 0
    if(testType == "motionTest" && now() - atomicState.lastMotionEvent > Dtime && atomicState.activeMotionCount != 0)
    {
        atomicState.activeMotionCount = 0 // time is up, reset this variable
        events = 0
    }
    else if(atomicState.activeMotionCount > 0 && testType == "motionTest")
    {
        events = atomicState.activeMotionCount
    }
    if(events > atomicState.activeMotionCount && testType == "motionTest")
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
/************************************************BOOLEANS******************************************************/
boolean contactsAreOpen(){

    boolean Open = WindowsContact?.any{it -> it.currentValue("contact") == "open"}      
    def listOfOpenContacts = []
    listOfOpenContacts = WindowsContact?.findAll{it.currentValue("contact") == "open"}
    atomicState.listOfOpenContacts = listOfOpenContacts.join(", ")
    return Open
}
boolean simpleModeIsActive(){
    atomicState.lastButtonEvent = atomicState.lastButtonEvent != null ? atomicState.lastButtonEvent : now()
    boolean result =  atomicState.lastResultWasTrue 
    //boolean doorOpen = doorsContactsAreOpen() // FEEDBACK LOOP since doorsContactsAreOpen() function calls simpleModeIsActive()
    boolean currentlyClosed = false 

    if(UseSimpleMode)
    {
        result = atomicState.buttonPushed      
    }
    if(UseSimpleMode && simpleModeTimeLimit && atomicState.buttonPushed) // if user set a time limit
    {     
        def remainTime = getRemainTime(simpleModeTimeLimit, atomicState.lastButtonEvent)
        def message = "SIMPLE MODE - remaining time: ${remainTime}"
        descriptionText formatText(message, "white", "grey")

        if(remainTime <= 0) // time is up
        {
            result = false 
            atomicState.buttonPushed = false
        }
    }

    logging"simple mode trigger boolean returns $result"   

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
    if(Open && !overrideSimpleMode && simpleModeIsActive())
    {
        descriptionText "$doorsContacts open but $simpleModeContact is closed and user doesn't wish to override"
        return false
    }

    logging """doors: $doorsContacts open ?: ${listOpen}"""
    return Open
}
boolean Active(){
    boolean result = true // default is true  always return Active = true when no sensor is selected by the user

    if(motionSensors)
    {
        long Dtime = noMotionTime * 1000 * 60
        boolean inMotionMode = location.mode in motionmodes
        logging "inMotionMode = $inMotionMode"

        if(inMotionMode)
        {
            result = getLastMotionEvents(Dtime, "motionTest") > 0
        }
        else 
        {
            logging("motion returns true because outside of motion modes")
        }
        Dtime = 60 * 60 * 1000 
        if(getLastMotionEvents(Dtime, "windows overrideTest") == 0) // if no motion for over one hour then save power by resetting windows override
        {
            //atomicState.openByApp = true
            //log.debug "56HY"
            atomicState.closedByApp = true
        }
    }
    else 
    {
        logging("user did not select any motion sensor")
    }
    descriptionText "motion test returns $result"
    return result
}
/************************************************MISCELANEOUS*********************************************************/
def stop(){
    if(customCommand)
    {
        def cmd = customCommand.minus("()")
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
    if(atomicState.paused == true)
    {
        return
    }

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
    atomicState.polls += 1
    atomicState.lastPoll = atomicState.lastPoll ? atomicState.lastPoll : now()
    if(now() - atomicState.lastPoll > 1000 * 60 * 60) atomicState.polls = 0
    
    log.trace "polling power meters. $atomicState.polls occurences in the last hour..."
    if(atomicState.polls > 20)
    {
        log.warn "too many polls within the last hour. Not polling, not refreshing..."
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
        descriptionText("refreshing $pw")
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
