/*
*  Copyright 2016 elfege
*
*    Eternal Sunshine©: Adjust dimmers with illuminance and (optional) motion
*
*    Software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
*    
*    The name Eternal Sunshine as an illuminance management software is protected under copyright
*
*  Author: Elfege
*/

definition(
    name: "Eternal Sunshine©",
    namespace: "elfege",
    author: "elfege",
    description: "Adjust dimmers with illuminance",
    category: "Convenience",
)

preferences {

    page name:"pageSetup"

}
def pageSetup() {

    appLabel()

    def pageProperties = [
        name:       "pageSetup",
        title:      "${app.label}",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    return dynamicPage(pageProperties) {


        section("")
        {
            atomicState.button_name = atomicState.paused == true ? "resume" : "pause"
            input "pause", "button", title: "$atomicState.button_name"

        }
        section("modes"){
            input "restrictedModes", "mode", title:"Pause this app if location is in one of these modes", required: false, multiple: true, submitOnChange: true 
            input "managePresence", "bool", title: "Pause / resume app with presence sensors?", defaultValue:false, submitOnChange:true
            if(managePresence)
            {
                input "presenceRestricted", "capability.presenceSensor", title:"Pause this app when any of these people is present", required:false, multiple:true, submitOnChange:true
                input "absenceRestricted", "capability.presenceSensor", title:"Pause this app when all of these people are NOT present", required:false, multiple:true, submitOnChange:true
            }
        }
        section(){
            label title: "Assign a name", required: false
        }
        section("pause when a button is pressed")
        {input "buttonPause", "capability.pushableButton", title: "Pause/resume this app when I press a button", multiple: true, required: false, submitOnChange:true
         if(buttonPause)
         {
             boolean doubletapable = buttonPause.every{element -> element.hasCapability("DoubleTapableButton")}
             boolean holdable = buttonPause.every{element -> element.hasCapability("HoldableButton")}
             boolean pushable = buttonPause.every{element -> element.hasCapability("PushableButton")}
             boolean releasable = buttonPause.every{element -> element.hasCapability("ReleasableButton")}
             logging """
doubletapable ? $doubletapable
holdable ?      $holdable
pushable ?      $pushable
releasable ?    $releasable
"""
             def list = releasable ?  ["pushed", "held", "doubleTapped", "released"] : doubletap ? ["pushed", "held", "doubleTapped"] : holdable ? ["pushed", "held"] : ["push"]

             input "buttonAction", "enum", title: "Select a button action", options:["pushed", "held", "doubleTapped"], required:true, submitOnChange:true
             input "buttonNumber", "number", title: "Select button number", required: true
             input "buttonPauseDelay", "number", title: "Pause for how long ?", description:"Time in hours. Leave empty to stay paused indefinitely" 
         }
        }

        section("Select the dimmers you wish to control") {
            input "dimmers", "capability.switchLevel", title: "pick a dimmer", required:true, multiple: true
            input "minimumValue", "number", title: "Optional: set a minimum dimming value (for certain dimmers not holding or flickering at low values)", required:false

        }
        section("Override and potential app conflict management"){
            input "override", "bool", title: "Allow user override (adjut the light to a new setting when prefered)", defaultValue: false, submitOnChange:true

            if(override) // if override, otherAPp is true by default
            {
                input "overrideDuration", "number", title: "Ovrride this app for how long?", description: "time in hours"
                app.updateSetting("otherApp", [type:"bool", value:true])  
            }

            input "otherApp", "bool", title: "These dimmers are turned off by another app", submitOnChange:true
            paragraph "IMPORTANT: Enable this option if you know that these dimmers might be turned off by another app"
            if(override) paragraph "This option is automatically set to true if you enabled 'Allow user override'" 
        }
        section("Select Illuminance Sensor") {
            input "sensor", "capability.illuminanceMeasurement", title: "pick a sensor", required:true, multiple: false, submitOnChange: true

            if(sensor){
                input "idk", "bool", title:"I don't know the maximum illuminance value for this device", submitOnChange: true, defaultValue: false

                if(!idk)
                {
                    input "maxValue", "number", title: "Select max lux value for this sensor", default: false, required:true, submitOnChange: true
                }
                else 
                {
                    if(logarithm) { app.updateSetting("logarithm",[value:"false",type:"bool"])

                                   def message = "This option isn't compatible with logarithmic variations - are you sure ? If Yes, enable it again"
                                   paragraph formatText(message, "red", "white")

                                  }
                    else 
                    {
                        paragraph "It will take up to 72 hours for the app to learn the maxium illuminance value this device can return, but it will start working immediately based on a preset value"
                        atomicState.maxValue = atomicState.maxValue == null ? 1000 : atomicState.maxValue // temporary value
                    }
                }
                logging( "maxValue = $atomicState.maxValue")

            }

            input "sensor2", "capability.illuminanceMeasurement", title: "pick a second sensor", required:false, multiple: false, submitOnChange: true
            if(sensor2){
                input "switchSensor2", "capability.switch", title: "when this switch is on/off, use second sensor", required:true, multiple:false, submitOnChange:true
                input "switchState", "enum", title: "when it is on or off?", options: ["on", "off"], required: true, submitOnChange:true
                if(switchSensor2 && switchState)
                {
                    input "highLuxSwitch", "bool", title:"when $sensor returns high lux, turn $switchState $switchSensor2", submitOnChange:true
                    if(highLuxSwitch)
                    {
                        input "onlyIfTempHigh", "bool", title:"Do this only when a sensor returns a temperature that is higher than a certain value", submitOnChange:true
                    }
                    if(onlyIfTempHigh) 
                    {
                        input "highTempSensor", "capability.temperatureMeasurement", title: "Select a temperature sensor", required:true, submitOnChange:true
                        if(highTempSensor)
                        {
                            input "tempThreshold", "number", title: "select a temperature threshold", required: true, submitOnChange:true
                        }
                    }
                    input "toggleBack", "bool", title: "turn $switchSensor2 back ${switchState == "off" ? "on" : "off"} once lux are back to a lower value", submitOnChange:true 
                }
            }
        }
        section("Location Modes Management") {
            input "modemgt", "bool", title: "Differentiate Maximum Dimmers' Values With Location Mode", submitOnChange: true
            if(modemgt)
            {
                input "modes", "mode", title:"select modes", required: true, multiple: true, submitOnChange: true

                if(modes){
                    def i = 0
                    atomicState.dimValMode = []
                    def dimValMode = []
                    for(modes.size() != 0; i < modes.size(); i++){
                        input "dimValMode${i}", "number", required:true, title: "select a maximum value for ${modes[i]}"
                    }
                }
            }
        }
        section("Motion Management")        {
            input "usemotion", "bool", title: "Turn On / Off with Motion", submitOnChange: true
            if(usemotion)
            {
                input "motionSensors", "capability.motionSensor", title: "Select your motion sensor(s)", despcription: "pick a motion sensor", required:false, multiple:true, submitOnChange:true
                if(motionSensors)
                {
                    input "noMotionTime", "number", title: "turn back off after how long?", required: true, description: "time in minutes"
                    input "modetimeout", "bool", title: "Differentiate Timeouts With Location Mode", submitOnChange: true
                    if(modetimeout)
                    {
                        input "timeoutModes", "mode", title:"select modes", required: true, multiple: true, submitOnChange: true

                        if(timeoutModes){
                            def i = 0
                            atomicState.timeoutValMode = []
                            def timeoutValMode = []
                            for(timeoutModes.size() != 0; i < timeoutModes.size(); i++){
                                input "timeoutValMode${i}", "number", required:true, title: "select a timeout value for ${timeoutModes[i]} ", description:"Time in minutes"
                            }
                        }
                    }
                }
                input "switches", "capability.switch", title: "also turn on/off some light switches", multiple: true, required:false
            }
        }


        section(""){
            input"logarithm", "bool", title:"Use logarithmic variations (beta)", value:false, submitOnChange:true
            if(logarithm)
            {
                if(idk) {app.updateSetting("idk",[value:"false",type:"bool"])
                         message} // fool proof, idk isn't compatible with logarith
                input "advanced", "bool", title: "Advanced logarithm settings (use with caution and with the graph helper)", submitOnChange:true, defaultValue:false
                if(!advanced)
                {
                    logarithmPref()
                }
                else 
                {
                    advancedLogPref()
                }
            }
        }
        section("logging"){
            input"enablewarning", "bool", title:"Enable Warning logs", value:false, submitOnChange:true
            input"enablelogging", "bool", title:"Enable Debug logs", value:false, submitOnChange:true

            input"enabledescription", "bool", title:"Enable Description text", value:false, submitOnChange:true

            if(enablelogging) atomicState.enableLogging = now()
            if(enablewarning) atomicState.enableWarning = now()

        }
        section(""){
            input "update", "button", title: "UPDATE"
            input "run", "button", title: "RUN"
        }
        section("Support this app's development"){
            // def url = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6JJV76SQGDVD6&source=url"
            def url = "<a href='https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6JJV76SQGDVD6&source=url' target='_blank'><div style=\"color:blue;font-weight:bold\"><center>CLICK HERE TO SUPPORT THIS APP!</center></div></a>"
            paragraph """

$url
"""
        }
    }
}

def appLabel(){
    if(atomicState.paused)
    {
        restoreLabel()
        app.updateLabel(app.label + ("<font color = 'red'>(Paused)</font>" ))
    }
    else if(app.label.contains("(Paused)"))
    {
        restoreLabel()
        log.debug "new app label: ${app.label}"
    }
}
def restoreLabel(){
    app.updateLabel(app.label.minus("<font color = 'red'>(Paused)</font>" ))

    def start = now()
    while(app.label.contains(" (Paused) ") || app.label.contains("Paused") || app.label.contains("(") || app.label.contains(")"))
    {
        app.updateLabel(app.label.minus(" (Paused) " ))
        app.updateLabel(app.label.minus("Paused" ))
        app.updateLabel(app.label.minus("(" ))
        app.updateLabel(app.label.minus(")" ))
        if(now() - start > 3000)
        {
            log.error "LABEL FAILLED"
            break
        }
    }
}

def advancedLogPref(){


    if(advanced)
    {
        def url = "<a href='https://www.desmos.com/calculator/vi0qou21ol' target='_blank'><div style=\"color:blue;font-weight:bold\"><center>CLICK HERE TO OPEN THE GRAPH HELPER</center></div></a>"
        //paragraph url
        input "offset", "number", range: "3..10000", required:true, title: "Offset: value named 'a' in graph tool", description:"Integer between 3 and 10000", submitOnChange:true
        logarithmPref()
        input "multiplier", "number", range: "3..3000", required:true, title: "Multipler: value named 'c' in graph tool", description:"Integer between 3 and 3000", submitOnChange:true

        def message =""" $url 
<div style=\"color:black;font-weight:bold\"><center>In the graph helper, move the cursors to create the ideal curve for your specific environment.</center></div>

a. Cursor named "a" is an offset. it moves the curve up and down, without changing the curve's shape. 
b. Cursor named "b" is the logarithm's base. It changes the shape of the curve by making it slightly stipper or flatter
c. Cursor named "c" is the multiplier. It changes the curve's shape more drastically. 
2. Make sure the curve meets the abscisse (the horizontal line) at the level of your sensor's max lux value (unless you want your lights to never turn off). 
3. If your curve ends up crossing the abscisse and go into negative values, those values will be ignored: 
lights will be set to 0 as soon as your sensor returns a value corresponding at the point where the curve crosses the abcisse.
5. Once you've found your ideal curve in the graph helper, simply report the values of a, b and c here.

Suggested values for an environment of 1000 max lux (most indoor sensors): 
a = 300 (offset)
b = 7   (sensitivity as log base)
c = 70  (multiplier; sets the gradient of the curve)

"""
        paragraph """<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 10px 10px 10px #141414;margin-left: -10px\">${message}</div>"""
    }
}
def logarithmPref(){

    def title = advanced ? "Base: value named 'b' in the graph tool (decimal value such as '5.0' or '4.9')" : "set a sensitivity value"
    input "sensitivity", "decimal", range: "1.0..200.0", required:true, title: "$title", description:"DECIMAL between 1.0 and 50.0", submitOnChange:true // serves as xa basis in linear determination of log() function's multiplier
    if(!advanced)
    {
        paragraph "The higher the value, the more luminance will be needed for $app.name to turn off your lights. For a maximum illuminance of 1000 (max value for most indoor sensors), a value between 5.0 and 6.0 is recommended"
    }

    if(sensitivity)
    {
        boolean wrong = sensitivity > 200.0 || sensitivity < 1.0
        def message = "${wrong ? "WRONG VALUE PLEASE SET A VALUE BETWEEN 1 and 200!" : "sensitivity set to $sensitivity"}"

        if(wrong) {
            paragraph "<div style=\"width:102%;background-color:red;color:black;padding:4px;font-weight: bold;box-shadow: 10px 10px 10px #141414;margin-left: -10px\">${message}</div>"
            log.warn message
        }
        else 
        {
            logging message
        }
    }
}
def installed() {
    logging("Installed with settings: ${settings}")
    initialize()
}
def updated() {
    logging("updated with settings: ${settings}")
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {
    atomicState.enableWarning = now()
    atomicState.enableLogging = now()
    atomicState.maxValue = !idk && !logarithm ? maxValue : 1000

    if(enablelogging == true){
        runIn(1800, disablelogging)
        descriptiontext("disablelogging scheduled to run in ${1800/60} minutes")
    }
    else 
    {
        log.warn "debug logging disabled!"
    }

    atomicState.motionEvents = 0

    //user input override variables
    atomicState.override = false
    atomicState.overrideTime = now()
    atomicState.lastDimValSetByApp = true 

    atomicState.lastDimVal = dimmers[0].currentValue("level")


    int i = 0
    int s = 0

    if(usemotion && motionSensors)
    {
        i = 0
        s = motionSensors.size()
        for(s!=0;i<s;i++)
        {
            subscribe(motionSensors[i], "motion", motionHandler)
            log.trace "${motionSensors[i]} subscribed to motion events"
        }
    }
    i = 0
    s = dimmers.size()
    for(s!=0;i<s;i++)
    {
        subscribe(dimmers[i], "level", dimmersHandler)
        subscribe(dimmers[i], "switch", switchHandler)
    }
    if(buttonPause)
    {
        if(buttonAction == "pushed") 
        {
            //subscribe(buttonPause, "pushed", doubleTapableButtonHandler)  
            subscribe(buttonPause, "pushed.$buttonNumber", doubleTapableButtonHandler) 
        }
        else if(buttonAction == "doubleTapped") 
        {
            //subscribe(buttonPause, "doubleTapped", doubleTapableButtonHandler)
            subscribe(buttonPause, "doubleTapped.$buttonNumber", doubleTapableButtonHandler)
        }
        else if(buttonAction == "held")    
        {
            //subscribe(buttonPause, "held", doubleTapableButtonHandler)
            subscribe(buttonPause, "held.$buttonNumber", doubleTapableButtonHandler)
        }
        else if(buttonAction == "released")    
        {
            //subscribe(buttonPause, "released", doubleTapableButtonHandler)
            subscribe(buttonPause, "released.$buttonNumber", doubleTapableButtonHandler)
        }



    }

    if(presenceRestricted)
    {
        subscribe(presenceRestricted, "presence", presenceHandler)
        log.debug "$presenceRestricted subscribed to events"
    }
    if(absenceRestricted)
    {
        subscribe(absenceRestricted, "presence", presenceHandler)
        log.debug "$absenceRestricted subscribed to events"
    }
    subscribe(modes, "mode", locationModeChangeHandler)
    subscribe(sensor, "illuminance", illuminanceHandler)

    schedule("0 0/1 * * * ?", mainloop) 
    schedule("0 0/10 * * * ?", poll) 

    logging("initialization ok")
    mainloop()
}
def switchHandler(evt){

    atomicState.timeCmd = atomicState.timeCmd != null ? atomicState.timeCmd : now()
    logwarn "switchHandler event after ${now() - atomicState.timeCmd} millis"

    if(atomicState.paused)
    {
        return
    }
    if(location.mode in restrictedModes)
    {
        log.info "App paused due to modes restrictions (current mode: $location.mode | restricted Modes: ${restrictedModes?.join(", ")} 4zg"
        return
    }

    logging("$evt.device is now set to $evt.value - - SOURCE: is $evt.source TYPE is $evt.type isPhysical: ${evt.isPhysical()}")
    atomicState.lastEvent = evt.name
    //mainloop() // infinite feedback loop!
}
def doubleTapableButtonHandler(evt){

    appLabel()
    atomicState.paused = !atomicState.paused
    if(atomicState.paused)
    {
        log.info formatText("${app.label} is now paused", "white", "red")
        atomicState.pauseTime = now()
    }
    else
    {
        log.info formatText("Resuming ${app.label}", "white", "red")
        updated()         
    }
}
def locationModeChangeHandler(evt){
    if(atomicState.paused)
    {
        return
    }
    logging("$evt.name is now in $evt.value mode")   

    mainloop()
}
def dimmersHandler(evt){

    if(atomicState.paused)
    {
        return
    }
    if(location.mode in restrictedModes)
    {
        log.info "App paused due to modes restrictions (current mode: $location.mode | restricted Modes: ${restrictedModes?.join(", ")} trfh5"
        return
    }
    logging """
$evt.device set to $evt.value 
atomicState.lastDimValSetByApp = $atomicState.lastDimValSetByApp 
atomicState.override=$atomicState.override """

    atomicState.lastDimValSetByApp = atomicState.lastDimValSetByApp != null ? atomicState.lastDimValSetByApp : true

    if(override && !atomicState.lastDimValSetByApp && !atomicState.override)
    {
        log.trace "USER OVERRIDE (will be canceled in 2 hours)"
        atomicState.overrideTime = now()
        atomicState.override = true
    }
    if(atomicState.override)
    {
        atomicState.override = false
        log.trace "END OF USER OVERRIDE DUE TO NEW USER NEW INPUT"
    }

    //atomicState.lastDimValSetByApp = false // get ready for a new user input 

    //mainloop() // infinite feedback loop if called from here...
}
def illuminanceHandler(evt){
    if(atomicState.paused)
    {
        return
    }
    if(location.mode in restrictedModes)
    {
        log.info "App paused due to modes restrictions (current mode: $location.mode | restricted Modes: ${restrictedModes?.join(", ")} 2z6rg7"
        return
    }
    descriptiontext("$evt.name is now $evt.value")

    // learn max value if required
    def currentSensor = switchSensor2 == null || sensor2 == null ? sensor : switchSensor2?.currentValue("switch") == "switchState" ? sensor2 : sensor 
    def illum = currentSensor.currentValue("illuminance")
    def maxVal = atomicState.maxValue.toInteger()
    if(idk && illum?.toInteger() > maxVal && !logarithm)
    {
        atomicState.maxValue = illum
        logging("new maximum lux value registered as: $atomicState.maxValue")
    }
    else 
    {
        logging "max value preset by user: ${maxValue}lux"
        atomicState.maxValue = maxValue
    }

    mainloop()

}
def motionHandler(evt){
    if(atomicState.paused)
    {
        return
    }
    if(location.mode in restrictedModes)
    {
        log.info "App paused due to modes restrictions (current mode: $location.mode | restricted Modes: ${restrictedModes?.join(", ")}8erthj"
        return
    }

    log.trace "MOTION EVT ----- $evt.device is $evt.value"

    if(usemotion) 
    {
        if(evt.value == "active")
        {
            atomicState.activeEvents = atomicState.activeEvents == null ? 0 : atomicState.activeEvents
            atomicState.activeEvents += 1
            atomicState.lastActiveEvent = now()

            if(enablewarning) atomicState.timeCmd = now()
            def dimVal = logarithm ? getDimValLog() : getDimVal()
            setDimmers(["dimVal": dimVal]) // for faster exec
        }
        if(evt.value == "inactive") mainloop()
    }
}
def presenceHandler(evt){
    log.info "$evt.device is $evt.value"
    mainloop()
}
def appButtonHandler(btn) {
    logwarn "atomicState.paused = $atomicState.paused"

    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused
        log.debug "atomicState.paused = $atomicState.paused"
        appLabel()
        break
        case "update":
        atomicState.paused = false
        updated()
        break
        case "run":
        atomicState.Tname = "button handler"
        atomicState.T = now() 
        mainloop()
        appLabel()
        break

    }
}

def mainloop(){

    def start = now()

    if(enablelogging && now() - atomicState.enableLogging > 1800 * 60)
    {
        disablelogging()
        log.debug "logging disabled"
    }

    if(location.mode in restrictedModes)
    {
        log.info "App paused due to modes restrictions (current mode: $location.mode | restricted Modes: ${restrictedModes?.join(", ")} 8rth4zj"
        return
    }
    if(managePresence)
    {
        boolean presenceRestriction = presenceRestricted ? presenceRestricted.any{it -> it.currentValue("presence") == "present"}  : false
        boolean absenceRestriction = absenceRestricted ? absenceRestricted.findAll{it.currentValue("presence") == "not present"}.size() == absenceRestricted.size() : false
        if(presenceRestriction)
        {
            def list = presenceRestricted.findAll{it.currentValue("presence") == "present"}
            def listOfPresence = list?.join(", ")

            log.info "App is paused because $listOfPresence ${list.size() > 1 ? "are" : "is"} present"
            atomicState.pausedByPresenceSensor = true
            atomicState.paused = true
        }
        else if(absenceRestriction)
        {
            def listOfPresence = absenceRestricted?.join(", ")

            log.info "App is paused because $listOfPresence ${list.size() > 1 ? "are" : "is"} NOT present"
            atomicState.pausedByPresenceSensor = true
            atomicState.paused = true
        }
        else if(atomicState.pausedByPresenceSensor)
        {
            atomicState.pausedByPresenceSensor = false
            atomicState.paused = false
        }
        if(atomicState.paused)
        {
            atomicState.pauseTime = atomicState.pauseTime ? atomicState.pauseTime : now()
            if(buttonPauseDelay && now() - atomicState.pauseTime > buttonPauseDelay)
            {
                atomicState.paused = false
            }
            return
        }
    }

    atomicState.T = atomicState.T != null ? atomicState.T : now()
    atomicState.T = atomicState.Tname == "end of main loop" ? atomicState.T = now() : atomicState.T // when called by schedule()
    atomicState.Tname = atomicState.Tname == "end of main loop" ? atomicState.Tname = "schedule call" :  atomicState.Tname

    atomicState.overrideTime = atomicState.overrideTime != null ? atomicState.overrideTime : now()

    if(override)
    {
        if(atomicState.override && now() - atomicState.overrideTime < overrideDuration*60*60*1000)
        {
            log.trace("App paused for $overrideDuration ${overrideDuration>1 ? "hours":"hour"} due to user's manual input override")  
            return
        }
        else if(atomicState.override && atomicState.overrideTime >= overrideDuration*60*60*1000)
        {
            atomicState.override = false
            atomicState.lastDimValSetByApp = true // prevent false positive on next cycle
            log.trace "END OF OVERRIDE, RESUMING NORMAL OPERATION"
        }
    }


    boolean Active = stillActive()

    boolean dimOff = dimmers.findAll{it.currentValue("switch") == "off"}.size() == dimmers.size() 
    boolean keepDimmersOff = false

    logging("""
number of dimmers that are off = ${dimmers.findAll{it.currentValue("switch") == "off"}.size()}
number of dimmers that are set to 0 = ${dimmers.findAll{it.currentValue("level") == 0}.size()}
dimmers.size() = ${dimmers.size()}
""")

    keepDimmersOff = usemotion ? dimOff && (otherApp || override) && !atomicState.turnedOffByNoMotionEvent : dimOff && (otherApp || override)

    logging("""
usemotion = $usemotion
keepDimmersOff = $keepDimmersOff
dimOff = $dimOff
outofmodes = $outofmodes
override = $override ${usemotion ? "" : "(Redundant when not using motion)"}
Active = $Active ${usemotion ? "" : "but doesn't use motion"}
location.mode = ${location.mode}
restrictedModes = $restrictedModes

""")

    if(Active && (!keepDimmersOff || atomicState.turnedOffByNoMotionEvent))
    {
        atomicState.turnedOffByNoMotionEvent = false
        def dimVal = logarithm ? getDimValLog() : getDimVal()

        atomicState.lastDimValSetByApp = true
        runIn(10, resetLastDimBool) // get ready for a new user manual override input 
        //dimVal = dimVal == 0 ? 1 : dimVal // 0 can't seem to go through data parameter with runin / platform inteprets it as boolean "false" and throws MissingMethodExceptionNoStack
        //dimVal = 10
        //log.warn "dimVal = $dimVal"
        //setDimmers(dimVal)
        runIn(5, setDimmers, [data:[dimVal:dimVal]])

        //switches?.on()
        if(switches) logging "${switches} turned off"
    }
    else if(Active && keepDimmersOff)
    {
        def message = ""
        if(override) message = "App in override mode for $overrideDuration ${overrideDuration > 1 ? "hours":"hour"} - or dimmers turned off by a different app or by user"
        if(!override) message = "dimmers are off and managed by a different app, $app.label will resume when they're turned back on keepDimmersOff = $keepDimmersOff"
        description message
    }
    else if(!Active)
    {
        descriptiontext "no motion..."
        atomicState.turnedOffByNoMotionEvent = true
        dimmers.off() 
        switches?.off()
        if(switches) logging "${switches} turned off"
    }

    if(atomicState.turnedOffByNoMotionEvent)
    {
        log.trace "keepDimmersOff not set to true because atomicState.turnedOffByNoMotionEvent = true : turned off by no motion event, not user or other app input"
    }

    if(highLuxSwitch)
    {
        def illuminance = sensor.currentValue("illuminance").toInteger()
        def maxVal = atomicState.maxValue != null ? atomicState.maxValue.toInteger() : maxValue.toInteger()
        def NeedCurtainOff = onlyIfTempHigh ? highTempSensor.currentValue("temperature") >= tempThreshold && illuminance >= maxVal : illuminance >= maxVal


        descriptiontext """*********
measured illuminance = $illuminance
maxVal (for curtains) = $maxVal

"""
        atomicState.curtainsWereTurnedOff = atomicState.curtainsWereTurnedOff != null ? atomicState.curtainsWereTurnedOff : false

        if(switchSensor2 && NeedCurtainOff && !atomicState.curtainsWereTurnedOff)
        {
            switchSensor2."${switchState}"()
            atomicState.curtainsWereTurnedOff = true
            logging "turning $switchSensor2 $switchState due to excess of illuminance"
        }
        else if(switchSensor2 && !NeedCurtainOff && toggleBack && atomicState.curtainsWereTurnedOff)
        {
            switchSensor2."${switchState == "off" ? "on" : "off"}"()
            atomicState.curtainsWereTurnedOff = false
            logging "turning $switchSensor2 ${switchState == "off" ? "on" : "off"} because illumiance is low again"
        }

    }

    logwarn "mainloop duration: ${(now() - start)} millis"
}

def resetLastDimBool(){
    atomicState.lastDimValSetByApp = false
}
def getDimVal(){
    if(atomicState.paused)
    {
        return
    }

    boolean switchStateTrue = switchSensor2 ? switchSensor2?.currentValue("switch") == switchState : false
    def currentSensor =  switchStateTrue ? sensor2 : sensor
    def illum = currentSensor.currentValue("illuminance")

    logging """
LINEAR
${switchSensor2 ? "switchSensor2 = $switchSensor2" : ""}
${switchSensor2 ? "${switchSensor2?.currentValue("switch")}" : ""}
${switchSensor2 ? "switchStateTrue = $switchStateTrue" : ""}
${switchSensor2 ? "switchState boolean reference is: $switchState" : ""}


illuminance sensor is: $currentSensor
illuminance is: $illum lux
maxValue = ${maxValue ? "$maxValue (user defined value, no learning)" : "atomicState.maxValue = $atomicState.maxValue (learned value)"}
"""
    def maxIllum = idk && !logarithm ? atomicState.maxValue : maxValue  // if idk selected, then use last learned max value (atomicState.maxValue)


    def y = null // value to find
    def x = illum // current illuminance
    def xa = maxIllum // maximum dimming value
    def ya = 0      //coressponding dimming value for when illuminance = xa

    def m = -0.1 // multiplier/slope 

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //logging "algebra found y = $y"
    dimVal = y.toInteger()
    dimVal = otherApp ? (dimVal < 1 ? dimVal = 1 : dimVal) : (dimVal < 0 ? dimVal = 0 : dimVal)
    dimVal = dimVal > 100 ? 100 : dimVal // useless due to slope being -0.1 but just in case I forget about the slope's value function, I leave this line and its comment here

    logging """illuminance: $illum, maximum illuminance: $maxIllum -|- ${maxValue ? "(user defined maxValue = $maxValue)" : ""}

linear dimming value result = ${dimVal} 
"""

    return dimVal.toInteger()
}
def getDimValLog(){ // logarithmic 
    if(atomicState.paused)
    {
        return
    }
    boolean switchStateTrue = switchSensor2 ? switchSensor2.currentValue("switch") == switchState : false
    def currentSensor =  switchStateTrue ? sensor2 : sensor
    def illum = currentSensor.currentValue("illuminance")


    logging """
LOGARITHMIC
${switchSensor2 ? "switchSensor2 = $switchSensor2" : ""}
${switchSensor2 ? "${switchSensor2?.currentValue("switch")}" : ""}
${switchSensor2 ? "switchStateTrue = $switchStateTrue" : ""}
${switchSensor2 ? "switchState boolean reference is: $switchState" : ""}


illuminance sensor is: $currentSensor
illuminance is: $illum lux 
No max value in logarithmic mode..
"""
    def y = null // value to find
    def x = illum != 0 ? illum : 1 // current illuminance // prevent "ava.lang.ArithmeticException: Division by zero "


    def a = offset ? offset : 300
    def b = sensitivity // this value is the overall sensitivity set by the user
    def c = multiplier ? multiplier : 70 

    y = (Math.log10(1/x)/Math.log10(b))*c+a
    logging "log${b}(1/${x})*${c}+${a} -> $y"
    dimVal = y.toInteger()
    dimVal = otherApp ? (dimVal < 1 ? dimVal = 1 : dimVal) : (dimVal < 0 ? dimVal = 0 : dimVal)
    dimVal = dimVal > 100 ? 100 : dimVal 

    logging "LOGARITHMIC dimming value = ${dimVal} (illuminance: $illum)"
    return dimVal
}
def setDimmers(val){   

    val = val.dimVal
    //val = val == "null" || val == null ? 0 : val
    log.warn "setDimmers to $val"

    if(atomicState.paused)
    {
        return
    }

    atomicState.lastDimValSetByApp = true

    def i = 0
    def s = dimmers.size()

    if(modemgt)
    {
        if(location.mode in modes){

            while(location.mode != modes[i]){i++}
            def valMode = "dimValMode${i}" // set as max
            def maxvalinthismode = settings.find{it.key == valMode}.value

            if(val > maxvalinthismode)
            {
                logging("ADJUSTED WITH CURRENT MODE == > valMode = $valMode && maxvalinthismode = $maxvalinthismode")
                val = maxvalinthismode
            }
        }
    }

    val = val < 0 ? 0 : (val > 100 ? 100 : val) // just a precaution
    if(val == 0) 
    { 
        dimmers.off() // it seems some hue devices don't fully turn off when simply dimmed to 0, so turn them off
    }
    else
    {
        dimmers.on() // make sure it's on, in case some other dumb device drivers don't get that 0+1 != 0... 
    }
    if(minimumValue && val < minimumValue) val = minimumValue
    dimmers.setLevel(val)
    logging("${dimmers.join(",")} set to $val ---")
}

boolean stillActive(){
    logwarn "motion test start"
    def start = now()

    boolean result = true
    int events = 0
    boolean inTimeOutModes = modetimeout ? location.mode in timeoutModes : true
    atomicState.activeEvents = now() - atomicState.lastActiveEvent > Dtime ? 0 : atomicState.activeEvents

    if(modetimeout && !inTimeOutModes) // if use timeout modes and not in this mode, then ignore motion (keep lights on)
    {
        log.trace "Location is outside of timeout modes, ignoring motion events"
        return result
    }
    if(usemotion)
    {
        def currentlyActive = motionSensors.findAll{it -> it.currentValue("motion") == "active"}
        if(currentlyActive?.size() > 0) 
        {
            log.trace "${currentlyActive.join(", ")} ${currentlyActive?.size() > 1 ? "are" : "is"} currently active"
            return true
        }

        atomicState.activeEvents = atomicState.activeEvents == null ? 0 : atomicState.activeEvents
        atomicState.lastActiveEvent = atomicState.lastActiveEvent == null ? now() : atomicState.lastActiveEvent

        int timeOut = getTimeout()
        long Dtime = timeOut * 60 * 1000 
        events = 0
        def period = new Date(now() - Dtime)

        motionSensors.each { sensor ->
            events += sensor.eventsSince(period, [max:200]).findAll{it.value == "active"}.size()
        }

        descriptiontext "atomicState.activeEvents = $atomicState.activeEvents | collectionSize = $events | timeout: $timeOut minutes"    

        result = events > 0 || atomicState.activeEvents > 0 
    }

    logwarn "motion test duration: ${(now() - start)} millis"
    //return true
    return result
}
def getTimeout(){
    def result = noMotionTime // default
    def valMode = location.mode

    if(modetimeout && location.mode in timeoutModes)
    {
        int s = timeoutModes.size()
        int i = 0
        logging("timeoutModes: $timeoutModes")
        while(i < s && location.mode != timeoutModes[i]){i++}
        logging("${location.mode} == ${timeoutModes[i]} (timeoutModes${i} : index $i) ?: ${location.mode == timeoutModes[i]}")
        valMode = "timeoutValMode${i}" // get the key as string to search its corresponding value within settings
        logging("valMode = $valMode")
        result = settings.find{it.key == valMode}?.value
        logging("valMode.value == $result")
    }
    if(result == null)
    {
        result = noMotionTime
    }
    logging("timeout is: $result  ${if(modetimeout){"because home is in $location.mode mode"}}")


    return result
}
def resetMotionEvents(){
    logging("No motion event has occured during the past $noMotionTime minutes")
    atomicState.motionEvents = 0   
}
def logging(msg){
    //def debug = settings.find{it.key == "enablelogging"}?.value
    //log.warn "debug = $debug"
    if (enablelogging) log.debug msg
}
def descriptiontext(msg){
    //def debug = settings.find{it.key == "enablelogging"}?.value
    //log.warn "debug = $debug"
    if (enabledescription) log.info msg
}
def logwarn(msg){
    if(enablewarning) log.warn msg
    if(enablewarning && now() - atomicState.enableWarning > 30 * 60 * 1000)
    {
        disablelogging()
    }
}
def disablelogging(){

    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    app.updateSetting("enablewarning",[value:"false",type:"bool"])
    log.debug "logging disabled!"
}
def poll(){
    logging "polling devices"
    boolean haspoll = false
    boolean hasrefresh = false
    dimmers.each{
        if(it.hasCommand("poll")){ it.poll() }else{logging("$it doesn't have poll command")}
        if(it.hasCommand("refresh")){ it.refresh() }else{logging("$it doesn't have refresh command")}
    }
}
def formatText(title, textColor, bckgColor){
    return  "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}

def donate(){
    def a = """
<form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
<input type="hidden" name="cmd" value="_s-xclick" />
<input type="hidden" name="hosted_button_id" value="6JJV76SQGDVD6" />
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" title="PayPal - The safer, easier way to pay online!" alt="Donate with PayPal button" />
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</form>

"""
    return a
}