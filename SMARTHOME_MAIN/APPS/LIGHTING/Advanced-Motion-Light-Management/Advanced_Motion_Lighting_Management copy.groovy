/*
*  Copyright 2016 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Light / motion Management
*
*  Author: Elfege 
*/

import java.text.SimpleDateFormat
import groovy.time.TimeCategory
import groovy.transform.Field
import groovy.json.JsonOutput

@Field static int delays = 0

definition(
    name: "Advanced Motion Lighting Management",
    namespace: "elfege",
    author: "elfege",
    description: "Switch light with motion events",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
)

preferences {
    page name: "pageSetup"
}
def pageSetup() {

    boolean haveDim = false

    appLabel()

    def pageProperties = [
        name: "pageSetup",
        title: "${app.label}",
        nextPage: null,
        install: true,
        uninstall: true
    ]

    return dynamicPage(pageProperties) {
        if (atomicState.paused == true) {
            atomicState.button_name = "resume"
            if (enabledebug) log.debug("button name is: $atomicState.button_name")
        }
        else {
            atomicState.button_name = "pause"
            if (enabledebug) log.debug("button name is: $atomicState.button_name")
        }
        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"
            input "allowOverride", "bool", title: "Override automations when I manually toggle a light", submitOnChange: true, defaultValue: false
            if (allowOverride) {
                input "overrideDelay", "number", title: "App override duration (in hours)", required: false
                paragraph formatText("Manually turning on/off one of your lights momentarily overrides automation (default is 2 hours)", "white", "grey")
            }
        }
        section("modes"){
            input "restrictedModes", "mode", title: "Pause this app if location is in one of these modes", required: false, multiple: true, submitOnChange: true
            if (restrictedModes) {
                input "keepLightsOffInRestrictedMode", "bool", title: "Keeps all lights off when in restricted mode", submitOnChange: true
            }

            input "restrictedTimeSlots", "bool", title: "Pause this app between times of day", defaultValue: false, submitOnChange: true

            if (restrictedTimeSlots) {
                atomicState.timeSlots = atomicState.timeSlots == 1 || atomicState.timeSlots == null ? 1 : atomicState.timeSlots
                input "addTime", "bool", title: "Add more time slots", submitOnChange: true
                if (addTime) {
                    app.updateSetting("addTime", [type: "bool", value: false])
                    atomicState.timeSlots += 1
                }
                if (atomicState.timeSlots > 1) {
                    input "lessSlots", "bool", title: "Less time slots", submitOnChange: true
                    if (lessSlots) {
                        app.updateSetting("lessSlots", [type: "bool", value: false])
                        atomicState.timeSlots = atomicState.timeSlots > 1 ? atomicState.timeSlots - 1 : atomicState.timeSlots
                    }
                }
                int s = atomicState.timeSlots
                for (int i = 0; i < s; i++) {
                    input "restrictedTimeStart${i}", "time", title: "From", required: false
                    input "restrictedTimeEnd${i}", "time", title: "To", required: false

                }
            }
            input "managePresence", "bool", title: "Pause / resume app with presence sensors?", defaultValue: false, submitOnChange: true
            if (managePresence) {
                input "presenceRestricted", "capability.presenceSensor", title: "Pause this app when any of these people is present", required: false, multiple: true, submitOnChange: true
                input "absenceRestricted", "capability.presenceSensor", title: "Pause this app when all of these people are NOT present", required: false, multiple: true, submitOnChange: true
            }
        }
        section(){
            label title: "Assign a name", required: false, submitOnChange: true
        }
        section("pause button")
        {
            input "pauseButton", "capability.holdableButton", title: "Pause this app when this button is pressed, double-tapped or held", multiple: true, required: false, submitOnChange: true
            if (pauseButton) {
                boolean doubletapable = buttonPause.every{ element -> element.hasCapability("DoubleTapableButton") }
                boolean holdable = buttonPause.every{ element -> element.hasCapability("HoldableButton") }
                boolean pushable = buttonPause.every{ element -> element.hasCapability("PushableButton") }
                boolean releasable = buttonPause.every{ element -> element.hasCapability("ReleasableButton") }
                if (enabledebug) log.debug "doubletapable ? $doubletapable"
                if (enabledebug) log.debug "holdable ? $holdable"
                if (enabledebug) log.debug "pushable ? $pushable"
                if (enabledebug) log.debug "releasable ? $releasable"

                def list = releasable ? ["pushed", "held", "doubleTapped", "released"] : doubletap ? ["pushed", "held", "doubleTapped"] : holdable ? ["pushed", "held"] : ["push"]


                input "buttonEvtTypePause", "enum", title: "Select the type of button event", options: list, required: true, submitOnChange: true
                input "pauseDuration", "number", title: "Pause for how long?", description: "time in minutes", required: true
                input "controlLights", "bool", title: "When enabling pause, turn on/off or toggle ${switches?.join(", ")}", submitOnChange: true
                if (controlLights) {
                    input "actionButton", "enum", title: "Select the type of action", options: ["off", "on", "toggle"], required: true, submitOnChange: true
                }
                def i = 0
                def s = pauseButton?.size()
                def strButtons = ""
                for (s != 0; i < s; i++) {
                    strButtons = "${pauseButton[i].toString()}"
                }
                def m1 = "When $strButtons ${s>1 ? "are": "is"} $buttonEvtTypePause, the app will be paused for ${pauseDuration} minutes"
                paragraph formatText(m1, "white", "grey")
            }


        }
        section("motion sensors")
        {
            paragraph "<div style='color:darkgray;'>------------------------------------------------------------</div>"
            input "motionSensors", "capability.motionSensor", title: "Choose your motion sensors", despcription: "pick a motion sensor", required: true, multiple: true, submitOnChange: true
            input "eventsOnly", "bool", title: "Events Based Behavior", defaultValue: true, submitOnChange: true
            if (!eventsOnly) {
                def m = "Ignore sensors' current state, decide upon events history only, except for turning the lights back on"
                paragraph formatText(m, "darkgray", "teal")
            }
            paragraph "<div style='color:darkgray;'>------------------------------------------------------------</div>"
        }
        section("Timing")
        {
            input "timeUnit", "enum", title: "Timeout Time Unit ?", description: "select your prefered unit of time", defaultValue: "seconds", options: ["seconds", "minutes"], submitOnChange: true

            if (timeUnit == null) { app.updateSetting("timeUnit", [value: "seconds", type: "enum"]) }

            input "timeWithMode", "bool", title: "Timeout with modes", submitOnChange: true, defaultValue: false
            if (timeWithMode) {
                input "timeModes", "mode", title: "Select modes", multiple: true, submitOnChange: true
                if (timeModes) {
                    def i = 0
                    // atomicState.dimValMode = []
                    // def dimValMode = []
                    for (timeModes.size() != 0; i < timeModes.size(); i++) {
                        input "noMotionTime${i}", "number", required: true, title: "select a timeout value for ${timeModes[i]}", description: "Enter a value in $timeUnit"
                    }
                    input "noTurnOnMode", "mode", title: "Select the modes under which lights must not be turned back on after being turned off", multiple: true, submitOnChange: true, required: false
                }
            }
            else {
                input "noMotionTime", "number", title: "turn light off after how long?", required: true, description: "Enter a value in $timeUnit", defaultValue: 10
            }
            input "noMotionTimeAbsence", "bool", title: "Set a specific motion time out when someone is absent", defaultValue: false, submitOnChange: true
            if (noMotionTimeAbsence) {
                input "absenceTimeoutSensor", "capability.presenceSensor", title: "Select a presence sensor", required: false, multiple: true, submitOnChange: true
                input "absenceTimeout", "number", title: "Timeout in $timeUnit", required: true, submitOnChange: true

            }
        }
        section("Switches")
        {
            input "switches", "capability.switch", title: "Control this light", required: true, multiple: true, description: "Select a switch", submitOnChange: true
            if (switches?.size() > 0) {
                input "keepSomeSwitches_Off_InCertainModes", "bool", title: "In certain modes, keep one ore some of those switches <b>OFF</b>", submitOnChange: true

                def list = []
                    int i = 0
                    int s = switches.size()
                    for (s != 0; i < s; i++) {
                        list += switches[i].toString()
                    }

                    list = list.sort()

                if (keepSomeSwitches_Off_InCertainModes) {
                    

                    input "modeSpecificSwitches_stay_Off", "mode", title: "select the modes under which you want only some specific switches to stay off", multiple: true, required: true
                    input "onlyThoseSwitchesStay_Off", "enum", title: "Select the switches that will stay off in these modes", options: list, required: true, multiple: true
                    def switchesWithDimCap = switches.findAll{ it.hasCapability("Switch Level") }
                    if (enabledebug) log.debug "list of devices with dimming capability = $switchesWithDimCap"
                    haveDim = switchesWithDimCap.size() > 0
                    if (useDim && haveDim) {
                        input "keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes", "number", title: "When some switches are supposed to stay off, keep all dimmers at this specific value", description: "leave empty to discard this option"
                    }
                }
                else {
                    app.updateSetting("onlyThoseSwitchesStay_Off", [value: null, type: "enum"])
                    app.updateSetting("modeSpecificSwitches_stay_Off", [value: null, type: "mode"])
                }


// ********************************************

                input "keepSomeSwitches_On_InCertainModes", "bool", title: "In certain modes, keep one ore some of those switches <b>ON</b>", submitOnChange: true

                if (keepSomeSwitches_On_InCertainModes) {
                
                    input "modeSpecificSwitches_stay_on", "mode", title: "select the modes under which you want only some specific switches to stay <b>ON</b>", multiple: true, required: true
                    input "onlyThoseSwitchesStay_On", "enum", title: "Select the switches that will stay On in these modes", options: list, required: true, multiple: true
                    
                }
                else {
                    app.updateSetting("onlyThoseSwitchesStay_On", [value: null, type: "enum"])
                    app.updateSetting("modeSpecificSwitches_stay_On", [value: null, type: "mode"])
                }
            }
        }
        section("Control a separate switch, following different conditions")
        {
            input "powerswitch", "capability.switch", title: "Also control this switch", required: false, multiple: false, description: "Select a switch", submitOnChange: true

            if (powerswitch && switches) {
                def text = "Control a separate switch under certain conditions and only after turning off ${switches.join(", ")}, and before turning them on. This can be usefull to shut down a power source that powers the lights switches themselves. Make sure, though, that you apply this option only if $switches are LAN devices. Powering off the source of a zigbee a zwave device will seriously damage your mesh network and render your hub unstable"
                paragraph formatText(text, "white", "red")

                input "waitForStatus", "bool", title: "Turn off $powerswitch only when ${switches.join(", ")} ${switches.size()>1 ? "are":"is"} returning a specific state", description: "type status value", submitOnChange: true
                if (waitForStatus) {
                    input "switchAttribute", "text", title: "state attribute name as string", defaultValue: "switch"
                    input "switchState", "text", title: "state name as string"
                }
            }
        }
        section("contact sensors")
        {
            input "contacts", "capability.contactSensor", title: "Use contact sensors to trigger these lights", multiple: true, required: false, submitOnChange: true
            if (contacts) {
                input "switchOnWithContactOnly", "bool", title: "Turn back on ${switches.join(", ")}  only when one of these contacts opens", submitOnChange: true
            }

            def switchesWithDimCap = switches.findAll{ it.hasCapability("Switch Level") }
            if (enabledebug) log.debug "list of devices with dimming capability = $switchesWithDimCap"
            haveDim = switchesWithDimCap.size() > 0
            if (enabledebug) log.debug "dimmer capability?:$haveDim"
            if (haveDim) {
                input "useDim", "bool", title: "Use ${switchesWithDimCap.toString()} dimming capabilities", submitOnChange: true
            }
            if (contacts && useDim) {
                input "dimValClosed", "number", title: "Desired value when contacts are closed", required: true
                input "dimValOpen", "number", title: "Desired value when contacts are open", required: true
                input "contactModes", "bool", title: "Use this option only if location is in certain modes", defaultValue: false, submitOnChange: true
                if (contactModes) {
                    input "modesForContacts", "mode", title: "Select modes", multiple: true, submitOnChange: true
                }
                else {
                    app.updateSetting("modesForContacts", [value: null, type: "mode"]) // foolproofing
                }
            }
            if (powerswitch) {
                input "powerOnWithContactOnly", "bool", title: "Turn $powerswitch back on only when one of these contacts has been opened", submitOnChange: true
                if (powerOnWithContactOnly && noTurnOnMode) {
                    input "ignoreModes", "bool", title: "Ignore the 'do not turn back on' option for this power switch. Turn it on even when contacts are triggered while in one of these modes: $noTurnOnMode", submitOnChange: true
                }
                else {
                    app.updateSetting("ignoreModes", [value: "false", type: "bool"]) // foolproofing
                }

            }
        }
        section("Illuminance")
        {
            input "checklux", "bool", title: "Keep the lights off when light is above a certain level", defaultValue: false, submitOnChange: true
            if (checklux) {
                input "sensor", "capability.illuminanceMeasurement", title: "pick an illuminance sensor", required: true, multiple: false, submitOnChange: true
                input "illumThres", "number", title: "Set an illuminance threshold above which lights stay off"

                if (pauseButton) {    
                    def list = ["pressed", "held", "doubleTapped"]
                    def remainingButtonCmds = list.findAll{ it != buttonEvtTypePause }
                    if (enabledebug) log.debug "available button actions for toggling lux sensitivity: $remainingButtonCmds"

                    input "buttonOverridesChecklux", "bool", title: "$pauseButton $remainingButtonCmds cancels/resumes illuminance sensitivity", submitOnChange: true

                    if (buttonOverridesChecklux) {
                        input "buttonEvtTypeLux", "enum", title: "Select the type of button event", options: remainingButtonCmds, required: true, submitOnChange: true
                    }
                    if (buttonOverridesChecklux && buttonEvtTypeLux) {
                        def s = pauseButton.size()
                        def m2 = "When strButtons ${s>1 ? "are": "is"} $buttonEvtTypeLux, the app will no longer react to illuminance until it is $buttonEvtTypeLux again"
                        paragraph formatText(m2, "white", "grey")
                    }
                }
            }
        }
        section("debug"){
            input "enabledebug", "bool", title: "Enable debug logs", submitOnChange: true
            input "tracedebug", "bool", title: "Enable trace logs", submitOnChange: true
            input "logwarndebug", "bool", title: "Enable warning logs", submitOnChange: true
            input "description", "bool", title: "Enable description text", submitOnChange: true

            long now = now()

            atomicState.EnableDebugTime = atomicState.EnableDebugTime == null ? now : atomicState.EnableDebugTime
            atomicState.enableDescriptionTime = atomicState.enableDescriptionTime == null ? now : atomicState.enableDescriptionTime
            atomicState.EnableWarningTime = atomicState.EnableWarningTime == null ? now : atomicState.EnableWarningTime
            atomicState.EnableTraceTime = atomicState.EnableTraceTime == null ? now : atomicState.EnableTraceTime
            atomicState.lastCheckTimer = atomicState.lastCheckTimer == null ? now : atomicState.lastCheckTimer


            if (enabledebug) {
            def m = [
                    "<br>now = $now",
                    "<br>enabledebug: $enabledebug",
                    "<br>tracedebug: $tracedebug",
                    "<br>logwarndebug: $logwarndebug",
                    "<br>description: $description",
                    "<br>atomicState.EnableDebugTime = $atomicState.EnableDebugTime",
                    "<br>atomicState.enableDescriptionTime = $atomicState.enableDescriptionTime",
                    "<br>atomicState.EnableWarningTime = $atomicState.EnableWarningTime",
                    "<br>atomicState.EnableTraceTime = $atomicState.EnableTraceTime",
                ]
                log.debug m.join()
            }



            if (enabledebug) atomicState.EnableDebugTime = now
            if (description) atomicState.enableDescriptionTime = now
            if (logwarndebug) atomicState.EnableWarningTime = now
            if (tracedebug) atomicState.EnableTraceTime = now


            atomicState.lastCheckTimer = now // ensure it won't run check_logs_timer right away to give time for states to update


        }
        section(){
            if (atomicState.installed) {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
                input "testoff", "button", title: "test turn off"
                input "teston", "button", title: "test turn on"
            }
        }
    }
}

def appLabel(){
    if (atomicState.paused) {
        restoreLabel()
        app.updateLabel(app.label + ("<font color = 'red'>(Paused)</font>"))
    }
    else if (app.label.contains("(Paused)")) {
        restoreLabel()
        if (enabledebug) log.debug "new app label: ${app.label}"
    }
}
def restoreLabel(){
    app.updateLabel(app.label.minus("<font color = 'red'>(Paused)</font>"))

    def start = now()
    // while(app.label.contains(" (Paused) ") || app.label.contains("Paused") || app.label.contains("(") || app.label.contains(")"))
    // {
    app.updateLabel(app.label.minus(" (Paused) "))
    app.updateLabel(app.label.minus("Paused"))
    app.updateLabel(app.label.minus("("))
    app.updateLabel(app.label.minus(")"))
    //     if(now() - start > 3000)
    //     {
    //         log.error "LABEL FAILLED"
    //         break
    //     }
    // }
}

def installed() {
    if (enabledebug) log.debug("Installed with settings: ${settings}")
    atomicState.lastReboot = now()
    atomicState.installed = true
    initialize()

}
def updated() {
    if (description) log.info "updated with settings: ${settings}"
    atomicState.installed = true
    atomicState.fix = 0
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {


    subscribe(motionSensors, "motion", motionHandler)
    if (tracedebug) log.trace "${motionSensors} subscribed to motionHandler"


    i = 0
    s = switches.size()
    for (s != 0; i < s; i++) {
        subscribe(switches[i], "switch", switchHandler)
        if (tracedebug) log.trace "${switches[i]} subscribed to switchHandler"
    }
    if (contacts) {
        i = 0
        s = contacts.size()
        for (s != 0; i < s; i++) {
            subscribe(contacts[i], "contact", contactHandler)
            if (tracedebug) log.trace "${contacts[i]} subscribed to contactHandler"
        }
    }

    subscribe_to_pause_related_events()


    atomicState.illuminanceThreshold = illumThres == null ? 1000 : illumThres.toInteger()
    atomicState.LuxCanceledbyButtonEvt = false

    atomicState.override = atomicState.override != null ? atomicState.override : false
    atomicState.overrideStart = atomicState.overrideStart != null ? atomicState.overrideStart : now()


    subscribe(location, "systemStart", hubEventHandler) // manage bugs and hub crashes

    subscribe(modes, "mode", locationModeChangeHandler)

    def timer = 3 //Math.abs(new Random().nextInt() % 10) + 1
    if (enabledebug) log.debug "******* Random schedule = $timer minutes....."
    schedule("0 0/${timer} * * * ?", master)

    atomicState.LuxCancelDeltaTime = 24 * 3600 // 24 hours delta time in minutes (not millis)
    atomicState.pauseDueToButtonEvent = false
    if (enabledebug) log.debug("initialization done")
    //master()
}
def subscribe_to_pause_related_events(){
    if (pauseButton) {
        subscribe(pauseButton, "$buttonEvtTypePause", holdableButtonHandler)
    }
    if (checklux) {
        subscribe(sensor, "illuminance", illuminanceHandler)
        if (buttonOverridesChecklux) // for canceling/resuming lux sensitivity and daytime options
        {
            subscribe(pauseButton, "$buttonEvtTypeLux", holdableButtonHandler)
        }

    }
    if (presenceRestricted) {
        subscribe(presenceRestricted, "presence", presenceHandler)
        if (enabledebug) log.debug "$presenceRestricted subscribed to events"
    }
    if (absenceRestricted) {
        subscribe(absenceRestricted, "presence", presenceHandler)
        if (enabledebug) log.debug "$absenceRestricted subscribed to events"
    }
    if (absenceTimeoutSensor) {
        subscribe(absenceTimeoutSensor, "presence", presenceHandler)
        if (enabledebug) log.debug "$absenceTimeoutSensor subscribed to events"
    }
}

def appButtonHandler(btn) {

    appLabel()

    switch (btn) {
        case "pause":
            atomicState.paused = !atomicState.paused
            log.debug "${app.label} is now ${atomicState.paused ? 'PAUSED' : 'RESUMING'}"
            appLabel()
            break
        case "update":
            atomicState.paused = false
            updated()
            break
        case "run":
            if (!atomicState.paused) {

                if (tracedebug) log.trace "running master() loop() at user's request"
                master()
                if (useDim) {
                    dim()
                }
            }
            else {
                log.warn "App is paused!"
            }
            break
        case "testoff":
            atomicState.test = true
            off()
            break
        case "teston":
            atomicState.test = true
            on()
            break

    }
}
def holdableButtonHandler(evt){

    if (description) if (description) log.info "BUTTON EVT $evt.device $evt.name $evt.value buttonEvtTypeLux = $buttonEvtTypeLux buttonOverridesChecklux = $buttonOverridesChecklux buttonEvtTypePause = $buttonEvtTypePause"

    toggleLightsFromButtonEvt(evt.name)

    if (buttonOverridesChecklux && evt.name == buttonEvtTypeLux && atomicState.LuxCanceledbyButtonEvt) // cancel / resume lux sensitivity
    {
        if (enabledebug) log.debug "RESUMING USER DEFAULT LUX SENSITIVITY"
        atomicState.LuxCanceledbyButtonEvt = false
    }
    else if (buttonOverridesChecklux && evt.name == buttonEvtTypeLux && !atomicState.LuxCanceledbyButtonEvt) {
        atomicState.LuxCanceledbyButtonEvt = true
        atomicState.LuxCanceledbyButtonEvtTime = now()
        runIn(atomicState.LuxCancelDeltaTime, resetLuxCancel)
        if (tracedebug) log.trace "LUX SENSITIVITY CANCELED FOR 24 HOURS"
    }
    else if (evt.name == buttonEvtTypePause) // pause function
    {
        atomicState.paused = !atomicState.paused
        atomicState.pauseDueToButtonEvent = atomicState.paused
        if (enabledebug) if (enabledebug) log.debug "atomicState.paused = $atomicState.paused atomicState.pauseDueToButtonEvent = $atomicState.pauseDueToButtonEvent"
        if (atomicState.paused) {
            atomicState.buttonPausedTime = now()
            schedule("0 0/1 * * * ?", checkPauseButton)
            if (enabledebug) log.debug("--------- checkPauseButton scheduled to run every 1 minute")
            if (tracedebug) log.trace "APP PAUSED FOR $pauseDuration MINUTES"
        }
        else {
            if (tracedebug) log.trace "RESUMING APP AT USER'S REQUEST DUE TO BUTTON EVENT"
            unschedule(checkPauseButton)
        }
    }

    master()
}
def toggleLightsFromButtonEvt(evtName){
    if (controlLights && evtName == buttonEvtTypePause) // toggle applies only to pause button request
    {
        if (actionButton != "toggle") {
            switches."$actionButton"()
        }
        else {
            int i = 0
            int s = switches.size()
            for (s != 0; i < s; i++) {
                def device = switches[i]
                def currentState = device.currentValue("switch")
                def toggleCmd = currentState == "on" ? "off" : "on"
                device."$toggleCmd"()
            }
        }
    }
}

def switchHandler(evt){
    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }
    if (atomicState.paused) return

    if (enabledebug) log.debug "$evt.device is $evt.value (delay btw cmd and this event = ${now() - atomicState.mainHandlerEventTime} milliseconds"

    if (allowOverride == true) {
        if ((evt.value == "on" && atomicState.switches == "off") || (evt.value == "off" && atomicState.switches == "on")) {
            log.warn "OVERRIDE TRIGGERED for $overrideDelay ${overrideDelay > 1 ? "hours" : "hour"} (evt handler)"
            atomicState.overrideStart = now()
            atomicState.override = true
        }
        else if (evt.value == atomicState.switches) {
            log.warn "END OF OVERRIDE (evt handler)"
            atomicState.override = false
        }
    }
    else {
        atomicState.override = false // make sure it stays false after user may have disabled this feature
    }

    atomicState.thisIsAMotionEvent = false
    atomicState.cmdFromApp = false

}
def locationModeChangeHandler(evt){
    if (enabledebug) log.debug("$evt.name is now in $evt.value mode")
}

boolean restrictedTime(){
    result = false
    if (!restrictedTimeSlots) return false

    if (atomicState.timeSlots) {
        int s = atomicState.timeSlots
        for (int i = 0; i < s; i++) {
            def starting = settings["restrictedTimeStart${i}"]            
            def ending = settings["restrictedTimeEnd${i}"]
            def currTime = now()
            def start = timeToday(starting, location.timeZone).time
            def end = timeToday(ending, location.timeZone).time
            if (enabledebug) log.debug "start = $start"
            if (enabledebug) log.debug "end = $end"
            if (enabledebug) log.debug "start < end ? ${start < end} || start > end ${start > end}"
            if (enabledebug) log.debug "currTime <= end ${currTime <= end} || currTime >= start ${currTime >= start}"

            result = start < end ? currTime >= start && currTime <= end : currTime <= end || currTime >= start
            if (result) break
        }
    }
    if (enabledebug) log.debug "restricted time returns $result"
    return result
}
boolean InRestrictedModeOrTime(){
    boolean inRestrictedTime = restrictedTime()
    boolean inRestrictedMode = location.mode in restrictedModes
    if (inRestrictedMode || inRestrictedTime) {
        if (description) log.info "location ${inRestrictedMode ? " in restricted mode" : inRestrictedTime ? "outside of time window" : "ERROR"}, doing nothing"
        return true
    }
    return false
}
def motionHandler(evt){
    atomicState.mainHandlerEventTime = now()
    if (description) log.info "${evt.name}: $evt.device is $evt.value"

    if (InRestrictedModeOrTime()) return

    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }
    if (atomicState.paused) return

    atomicState.activeEvents = atomicState.activeEvents == null ? 0 : atomicState.activeEvents
    atomicState.lastActiveEvent = atomicState.lastActiveEvent == null ? now() : atomicState.lastActiveEvent

    if (evt.value in ["open", "active"]) {
        if (switchOnWithContactOnly && evt.value != "open") {
            // do nothing 
        }
        else {
            on()
        }

        if (evt.value == "active") {
            atomicState.activeEvents += 1
            atomicState.lastActiveEvent = now()
        }

    }
    master()
}
def contactHandler(evt){
    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }
    if (atomicState.paused) return

    if (InRestrictedModeOrTime()) return


    if (evt.value == "open") {

        if (switches.any{ it -> it.currentValue("switch") == "off" }) {
            switches.on()
            if (enabledebug) log.debug("siwtches on 5dfrj")
        }

        if (powerOnWithContactOnly) {
            if (location.mode in noTurnOnMode && !ignoreModes) {
                if (description) log.info "$powerswitch is not being turned on because location is in $noTurnOnMode modes (${location.mode})"
                return
            }
            if (switchOnWithContactOnly) {
                if (powerswitch?.currentValue("switch") == "off") {
                    if (enabledebug) log.debug("siwtches on 34ghj4")
                    powerswitch?.on() // user might have requested contact event based only, so we can't rely on on() method only (which will test for this condition)
                }
            }
        }

    }

}
def hubEventHandler(evt){
    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }
    if (atomicState.paused) return

    if (InRestrictedModeOrTime()) return

    log.warn "HUB $evt.name"
    if (evt.name == "systemStart") {
        log.warn "reset atomicState.lastReboot = now()"
        atomicState.lastReboot = now()

        updated()
    }
}
def illuminanceHandler(evt){

    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }
    if (atomicState.paused) {
        log.info "APP PAUSED"
        return
    }

    if (InRestrictedModeOrTime()) return

    if (description) log.info "$evt.name is now $evt.value"

    def anyOn = switches.any{ it -> it.currentValue("switch") == "on" }//.size() > 0
    if (description) log.info "anyOn = $anyOn"
    atomicState.LuxCanceledbyButtonEvt = atomicState.LuxCanceledbyButtonEvt == null ? false : atomicState.LuxCanceledbyButtonEvt
    boolean daytime = evt.value.toInteger() > atomicState.illuminanceThreshold && !atomicState.LuxCanceledbyButtonEvt

    atomicState.daytimeSwitchExecuted = atomicState.daytimeSwitchExecuted == null ? atomicState.daytimeSwitchExecuted = false : atomicState.daytimeSwitchExecuted

    if (daytime && anyOn && !atomicState.daytimeSwitchExecuted) { // turn off at first occurence of light sup to threshold but don't reiterate
        if (description) log.info "turning off ${switches} 545r"
        off()
        atomicState.daytimeSwitchExecuted = true
    }
    else if (atomicState.daytimeSwitchExecuted && !daytime) {
        atomicState.daytimeSwitchExecuted = false   // reset this value for the next occurence of an illum > threshold
    }

    master()

}
def presenceHandler(evt){
    log.info "$evt.device is $evt.value"
    master()
}

def master(){

    def startTime = now()

    if (enabledebug) log.debug "master start"

    check_logs_timer()

    appLabel()

    if (InRestrictedModeOrTime()) return

    if (enabledebug) log.debug "atomicState.lastRun = $atomicState.lastRun"
    atomicState.lastRun = atomicState.lastRun == null ? now() : atomicState.lastRun
    if (atomicState.lastRun < 1500) {
        log.warn "events are too close, delaying this run of master loop"
        return
    }
    atomicState.lastRun = now()

    if (managePresence) {
        boolean presenceRestriction = presenceRestricted ? presenceRestricted.any{ it -> it.currentValue("presence") == "present" }  : false
        boolean absenceRestriction = absenceRestricted ? absenceRestricted.findAll{ it.currentValue("presence") == "not present" }.size() == absenceRestricted.size() : false
        if (presenceRestriction) {
            def list = presenceRestricted.findAll{ it.currentValue("presence") == "present" }
            def listOfPresence = list.join(", ")

            log.info "App is paused because $listOfPresence ${list.size() > 1 ? "are" : "is"} present"
            atomicState.pausedByPresenceSensor = true
            atomicState.paused = true
        }
        else if (absenceRestriction) {
            def list = absenceRestricted.findAll{ it.currentValue("presence") == "present" }
            def listOfAbsence = absenceRestricted.join(", ")


            log.info "App is paused because $listOfAbsence ${list.size() > 1 ? "are" : "is"} NOT present"
            atomicState.pausedByPresenceSensor = true
            atomicState.paused = true
        }
        else if (atomicState.pausedByPresenceSensor) {
            atomicState.pausedByPresenceSensor = false
            atomicState.paused = false
            updated()
        }
    }



    if (atomicState.pauseDueToButtonEvent) {
        checkPauseButton()
        return
    }

    if (atomicState.paused) return

    atomicState.overrideStart = atomicState.overrideStart != null ? atomicState.overrideStart : now()

    if (allowOverride) {
        def overrideTime = overrideDelay != null && overrideDelay != 0 ? overrideDelay : 2 // 2 hours override default

        if (atomicState.override && now() - atomicState.overrideStart > overrideTime * 60 * 60 * 1000) {
            if (tracedebug) log.trace "END OF OVERRIDE"
            atomicState.override = false
        }
        else if (atomicState.override) {
            def remain = (((overrideTime * 60 * 60 * 1000) - (now() - atomicState.overrideStart)) / (60 * 60 * 1000)).toDouble().round(2)
            if (tracedebug) log.trace "app in override mode because a switch was turned ${atomicState.switches == "off" ? "on" : "off"} manually. App will resume in ${remain} hours"
        }
    }



    if (!Active()) {
        off()
        if (enabledebug) log.debug "lights turned off 638ef"
    }
    else {

        if (switchOnWithContactOnly) {
            if (enabledebug) log.debug "switchOnWithContactOnly = $switchOnWithContactOnly"
        }
        else {
            on()
            if (enabledebug) log.debug "lights turned on 638ef"
        }

    }

    if (enabledebug && now() - atomicState.EnableDebugTime > 1800000) {
        if (description) log.info "Debug has been up for too long..."
        log.debug()
    }


    if (enabledebug) log.debug "---end of master loop. Duration = ${now() - startTime} milliseconds"
}

def checkPauseButton(){

    if (enabledebug) log.debug("check pause")

    if (enabledebug) log.debug("atomicState.pauseDueToButtonEvent = $atomicState.pauseDueToButtonEvent now() - atomicState.buttonPausedTime > pauseDuration : ${now() - atomicState.buttonPausedTime > pauseDuration * 60 * 1000}")

    if (atomicState.pauseDueToButtonEvent && now() - atomicState.buttonPausedTime > pauseDuration * 60 * 1000) {
        atomicState.paused = false
        atomicState.pauseDueToButtonEvent = false
        log.warn "PAUSE BUTTON TIME IS UP! Resuming operations"
        unschedule(checkPauseButton)
    }
    else if (atomicState.pauseDueToButtonEvent) {
        if (enabledebug) log.debug("APP PAUSED BY BUTTON EVENT")
    }
}
def checkLuxCancel(){
    dt = atomicState.LuxCancelDeltaTime * 1000 // convert 24 hours counted in seconds, in millis
    if (atomicState.LuxCanceledbyButtonEvt && now() - atomicState.LuxCanceledbyButtonEvtTime > dt) {
        log.warn "(periodic schedule version) LUX PAUSE TIME IS UP! Resuming operations (runIn method seems to have failed)"
        atomicState.LuxCanceledbyButtonEvt = false
        unschedule(resetLuxCancel)
        //master() // feedback loop
    }
    else if (atomicState.LuxCanceledbyButtonEvt) {
        if (description) log.info "LUX SENNSITIVITY PAUSED BY BUTTON EVENT"
    }

}
def resetLuxCancel(){
    atomicState.LuxCanceledbyButtonEvt = false
}

boolean contactModeOk(){
    boolean result = true
    if (contacts && contactModes) {
        if (location.mode in modesForContacts) {
            return true
        }
        else {
            return false
        }
    }
    return result
}
boolean contactsAreOpen(){

    def openList = contacts?.findAll{ it.currentValue("contact") == "open" }
    openList = openList != null ? openList : []

    if (enabledebug) log.debug("Currently Open Contacts $openList")
    return openList.size() > 0
}

boolean Active(){
    if (enabledebug) log.debug "motionSensors = $motionSensors"

    if (!eventsOnly) {
        def currentlyActive = motionSensors.findAll{ it -> it.currentValue("motion") == "active" }
        if (currentlyActive?.size() > 0) {
            if (tracedebug) log.trace "${currentlyActive.join(", ")} ${currentlyActive?.size() > 1 ? "are" : "is"} currently active"
            return true
        }
    }

    boolean result = true
    int events = 0
    int timeOut = getTimeout()
    long Dtime = timeOut * 1000
    if (timeUnit == "minutes") Dtime = timeOut * 1000 * 60

    atomicState.lastActiveEvent = atomicState.lastActiveEvent == null ? 0 : atomicState.lastActiveEvent
    atomicState.activeEvents = now() - atomicState.lastActiveEvent > Dtime ? 0 : atomicState.activeEvents
    def collectionSize = 0
    def period = new Date(now() - Dtime)

    motionSensors.each {
        sensor ->
            events += sensor.eventsSince(period, [max: 200]).findAll{ it.value == "active" }.size()
    }


    if (description) log.info "atomicState.activeEvents = $atomicState.activeEvents | collectionSize = $events | timeout: $timeOut $timeUnit"

    return result = events > 0 || atomicState.activeEvents > 0
}
def getTimeout(){
    def result = noMotionTime // default

    try {
        if (absenceTimeoutSensor) {
            def listOfAbsents = absenceTimeoutSensor.findAll{ it.currentValue("presence") == "not present" }
            boolean absenceRestriction = absenceTimeoutSensor ? listOfAbsents.size() == absenceTimeoutSensor.size() : false
            if (absenceRestriction) {
                if (description) log.info "$absenceTimeoutSensor not present, timeout returns $absenceTimeout absenceTimeoutSensor.size() = ${absenceTimeoutSensor.size()} listOfAbsents.size() = ${listOfAbsents.size()}"
                return absenceTimeout
            }
        }
    }
    catch (Exception e) {
        def lineNumber = -1

        try {
            def stackTrace = e.stackTrace

            if (stackTrace && stackTrace.size() > 0) {
                console.log formatText(stackTrace, "white", "gray")
                lineNumber = stackTrace[0].lineNumber
            }
        } catch (Exception er) {
            def errorMessage = lineNumber != -1 ? "absenceTimeoutSensor setting value NO LONGER WORKS AND NEEDS REFACTORING (${app.label}): $e at line $lineNumber" : "absenceTimeoutSensor setting value NO LONGER WORKS AND NEEDS REFACTORING (${app.label}): $e"
            log.error(formatText(errorMessage, "white", "red"))
        }
    }


    if (timeWithMode) {
        int i = 0
        while (location.mode != timeModes[i]) { i++ }
        valMode = "noMotionTime${i}"
        // valMode = settings.find{it.key == valMode}?.value
        valMode = settings["noMotionTime${i}"]
        log.trace("returning value for ${timeModes[i]}: $valMode ${timeUnit}")
        result = valMode
    }
    if (result == null) {
        return noMotiontime
    }
    if (enabledebug) log.debug("getTimeout() returns $result")
    return result
}

def off(){
    if (enabledebug) log.debug "off function"

    def anyOn = switches.any{ it -> it.currentValue("switch") == "on" }


    atomicState.mainHandlerEventTime = now()
    if (anyOn) {
        if (!atomicState.test && (atomicState.switches == "on" || !allowOverride)) {
            switchesOff()
            if (description) log.info "turning off ${switches.join(", ")} 59989e"
            atomicState.switches = "off"
        }
        else if (allowOverride && !atomicState.test && atomicState.switches == "off" && switches.any{ it -> it.currentValue("switch") == "on" })
        {
            if (description) log.info "lights were turned on manually, app in override mode"
        }
        else
        {
            if (enabledebug) log.debug "$switches would have turned off - test succeeded!"
        }
    }
    else {
        if (enabledebug) log.debug "$switches already off"
    }

    if (powerswitch || atomicState.test) {
        if (enabledebug) if (enabledebug) log.debug  "powerswitch = $powerswitch waitForStatus = $waitForStatus anyOn = $anyOn atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures"
        boolean allStatesOk = true
        if (waitForStatus) {
            allStatesOk = switches.findAll{ it.currentValue(switchAttribute) == switchState }.size() == switches.size()
            if (!allStatesOk) {
                log.warn "NOT OK TO TURN OFF $powerswitch yet because $switches ${switches.size()>1 ? "have":"has"} not returned '${switchState}' state yet"
                return
            }
        }
        if (!atomicState.test && allStatesOk) {
            log.warn "TURNING OFF $powerSwitchOff in 5 seconds... "
            runIn(5, powerSwitchOff)
        }
        else {
            if (enabledebug) log.debug "$powerswitch would have turned off - test succeeded !"
        }
    }
    atomicState.test = false
}
def on(){
    atomicState.mainHandlerEventTime = now()

    if (InRestrictedModeOrTime()) return

    if (!keepSomeSwitches_Off_InCertainModes && noTurnOnMode) {
        app.updateSetting("noTurnOnMode", [type: "mode", value: []])
    }

    if (location.mode in noTurnOnMode && keepSomeSwitches_Off_InCertainModes) {
        if (description) log.info "$switches ${powerswitch ? " & $powerswitch":""} not turned on because location is in $noTurnOnMode modes (${location.mode}) 545r"

        if (switches.any{ it -> it.currentValue("switch") == "on" }) // this prevents the exception light from staying on during noTurnOnMode. Without this test, it would turn back on with motion. 
        {
            specificSwitch(true) // update to the desired outcome: some off, some on. 
        }

        return
    }
    if (useDim) {
        dim()
    }
    atomicState.cmdFromApp = true
    boolean anyOff = switches.any{ it -> it.currentValue("switch") == "off" }
    def illuminance = sensor?.currentValue("illuminance")
    atomicState.LuxCanceledbyButtonEvt = atomicState.LuxCanceledbyButtonEvt == null ? false : atomicState.LuxCanceledbyButtonEvt
    boolean daytime = checklux ? illuminance > atomicState.illuminanceThreshold && !atomicState.LuxCanceledbyButtonEvt : false
    if (enabledebug) log.debug "anyOff = $anyOff ${checklux ? " | daytime = $daytime | illuminance = $illuminance" : ""}"
    //log.warn "atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures"

    if (anyOff || (keepSomeSwitches_Off_InCertainModes && location.mode in modeSpecificSwitches_stay_Off)) { // run only if any is off or if there's need to update states with specific switches and modes


        if (!daytime) {
            if (!powerOnWithContactOnly && !switchOnWithContactOnly) // only when user didn't ask for this to be turned on only whith contact events
            {
                if (powerswitch?.currentValue("switch") == "off") {
                    powerswitch?.on()
                    log.warn "turnging on ${powerswitch.join(", ")} 1r2hk"
                }
            }
            if (keepSomeSwitches_Off_InCertainModes && location.mode in modeSpecificSwitches_stay_Off) {
                specificSwitch(false) // only the switches not selected by user will be turned on
            }
            else {
                if (anyOff && (atomicState.switch == "off" || !allowOverride)) {
                    switches.on()
                    if (description) log.info "turnging on ${switches.join(", ")} 54dfze"
                    atomicState.switches = "on"
                }
                else if (anyOff && atomicState.switches == "on") {
                    if (description) log.info "lights were turned off manually, app in override mode"
                }
            }
        }
        else {
            if (description) log.info "daytime is on, not turning on the lights"
            boolean anyOn = switches.any{ it -> it.currentValue("switch") == "on" }
            if (anyOn && daytime && !atomicState.daytimeSwitchExecuted && (atomicState.switches == "on" || !allowOverride)) {
                // turn off at first occurence of light sup to threshold but don't reiterate so if user turns them back on they'll stay on
                if (description) log.info "turning off $switches.join(", ") due to daytime - you can still turn them back on manually if you wish"
                // switches.off()
                switchesOff()
                atomicState.switches = "off"
                atomicState.daytimeSwitchExecuted = true
            }
            else if (atomicState.switches == "off" && anyOn && allowOverride) {
                if (description) log.info "lights were turned on manually, app in override mode"
            }

        }
    }
    else {
        if (enabledebug) log.debug "$switches already on"
    }

    if (atomicState.LuxCanceledbyButtonEvt) {
        checkLuxCancel()
    }
}

def switchesOff(){
    atomicState.dimmersSetToOffByRestrictedMode = atomicState.dimmersSetToOffByRestrictedMode == null ? false : atomicState.dimmersSetToOffByRestrictedMode

    if (location.mode in restrictedModes && keepLightsOffInRestrictedMode) {
        log.warn "location in restricted mode (${location.mode})"


        //make sure this runs only once so user can turn and keep lights on if they wish when in restricted mode
        if (!atomicState.dimmersSetToOffByRestrictedMode) {
            switches.off()()
            pauseExecution(100)
            atomicState.dimmersSetToOffByRestrictedMode = true
        }
        return
    }
    atomicState.dimmersSetToOffByRestrictedMode = false

    switches.off()
}

def powerSwitchOff(){
    def listOfSwitchesCurrentStates = switches.findAll{ it.currentValue("switch") } 
    boolean allStatesOk = switches.findAll{ it.currentValue(switchAttribute) == switchState }.size() == switches.size() // do another verification 
    if (!allStatesOk) {
        log.warn "One of $switches still returning '${switchState}'... $powerswitch operation ABORTED"
        def timeStamp = new Date().format("MMM dd EEE h:mm:ss a", location.timeZone)
        def culpritDevice = switches.find{ it.currentValue(switchAttribute) != switchState }
        def stringRecord = "faillure Time: $timeStamp"

        if (logwarndebug) log.warn stringRecord
        // record related data for future debug 

        atomicState.switchesExpectedStatesFailures = atomicState.switchesExpectedStatesFailures == null || atomicState.switchesExpectedStatesFailures.size() > 50 ? [] : atomicState.switchesExpectedStatesFailures
        atomicState.switchesExpectedStatesFailures += stringRecord

        if (logwarndebug) log.warn "end of power switch failure log atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures"
        //return
    }
    else {
        if (logwarndebug) log.warn "TURNING OFF $powerswitch"
        if (powerswitch?.currentValue("switch") == "on") powerswitch?.off()
    }

}
def specificSwitch(boolean exception){

    def objectDevice
    int i = 0
    int s = switches.size()
    for (s != 0; i < s; i++) {
        def device = switches[i]
        boolean thisIsTheSwitchToKeepOff = device.displayName in onlyThoseSwitchesStay_Off
        if (enabledebug) log.debug "onlyThoseSwitchesStay_Off = $onlyThoseSwitchesStay_Off | $device is to be kept off = $thisIsTheSwitchToKeepOff"

        if (exception) // boolean declared true only when in notTurnOn Mode, so here we will turn lights off only
        {
            log.warn "exception true"
            if (thisIsTheSwitchToKeepOff) {
                // all still on, some need to be off
                log.warn"$device NEEDS TO BE OFF"
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability("Switch Level")) {
                    if (enabledebug) log.debug "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                }
                else {
                    if (device.currentValue("switch") == "on" && (atomicState.switches == "on" || !allowOverride)) {
                        device.off()
                        atomicState.switches = "off"
                    }
                    else if (atomicState.switches == "off" && allowOverride) {
                        if (description) log.info "lights were turned on manually, app in override mode"
                    }
                }
            }
        }
        else { // in any other valid case of specific switches, we turn on what must stay on and off what must stay off
            //log.warn "exception false"
            if (!thisIsTheSwitchToKeepOff) {
                if (switchOnWithContactOnly) {
                    // do nothing 
                }
                else {
                    boolean isOff = device.currentValue("switch") == "off"
                    if (isOff && (atomicState.switches == "off" || !allowOverride)) {
                        device.on()
                        atomicState.switches = "on"
                        if (enabledebug) log.debug "${device} stays on at user's request"
                    }
                    else if (allowOverride && atomicState.switches == "on" && isOff) {
                        if (description) log.info "lights were turned on manually, app in override mode"
                    }
                }
            }
            if (thisIsTheSwitchToKeepOff) // turn off the other switch 
            {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability("Switch Level")) {
                    if (enabledebug) log.debug "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                }
                else if (device.currentValue("switch") != "off" && (atomicState.switches == "on" || !allowOverride)) {
                    device.off()
                    atomicState.switches = "off"
                    if (enabledebug) log.debug "${device} is turned off at user's request"
                }
                else if (allowOverride && atomicState.switches == "off") {
                    if (description) log.info "lights were turned on manually, app in override mode"
                }
            }
        }
    }
}
def dim(){

    if (keepSomeSwitches_Off_InCertainModes && location.mode in modeSpecificSwitches_stay_Off && !keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) {
        log.warn "not dimming because app is in specific switches mode"
    }
    else {
        boolean closed = !contactsAreOpen()
        def switchesWithDimCap = switches.findAll{ it.hasCapability("SwitchLevel") }
        if (enabledebug) log.debug "list of devices with dimming capability = $switchesWithDimCap"

        if (enabledebug) log.debug "dimValClosed = $dimValClosed"

        int i = 0
        int s = switchesWithDimCap.size()

        if (closed) {
            for (s != 0; i < s; i++) {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches_stay_Off) {
                    switchesWithDimCap[i].setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) // dimming value kept when in specific stay off mode
                }
                else {
                    dimValClosed = dimValClosed < 10 ? 10 : dimValClosed
                    switchesWithDimCap[i].setLevel(dimValClosed)
                    if (enabledebug) log.debug("${switchesWithDimCap[i]} set to $dimValClosed 9zaeth")
                }
            }
        }
        else {
            if (!contactModeOk()) // ignore that location is not in the contact mode and dim to dimValClosed
            {
                for (s != 0; i < s; i++) {
                    if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches_stay_Off) {
                        switchesWithDimCap[i].setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) // dimming value kept when in specific stay off mode
                    }
                    else {
                        dimValClosed = dimValClosed < 10 ? 10 : dimValClosed
                        switchesWithDimCap[i].setLevel(dimValClosed)
                        if (enabledebug) log.debug("${switchesWithDimCap[i]} set to $dimValClosed 78fr")
                    }
                }
            }
            else {
                for (s != 0; i < s; i++) {
                    if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) {
                        switchesWithDimCap[i].setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) // dimming value kept when in specific stay off mode
                    }
                    else {
                        dimValOpen = dimValOpen < 10 ? 10 : dimValOpen
                        switchesWithDimCap[i].setLevel(dimValOpen)
                        if (enabledebug) log.debug("${switchesWithDimCap[i]} set to $dimValOpen 54fre")
                    }
                }
            }
        }
    }
}

def runCmd(String ip, String port, String path) {

    def uri = "http://${ip}${": "}${port}${path}"
    if (enabledebug) log.debug "POST: $uri"

    def reqParams = [
        uri: uri
    ]

    try {
        httpPost(reqParams){
            response ->
        }
    } catch (Exception e) {
        log.error "${e}"
    }
}

def formatText(title, textColor, bckgColor){
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}

def check_logs_timer(){

    long now = now()


    atomicState.EnableDebugTime = atomicState.EnableDebugTime == null ? now : atomicState.EnableDebugTime
    atomicState.enableDescriptionTime = atomicState.enableDescriptionTime == null ? now : atomicState.enableDescriptionTime
    atomicState.EnableWarningTime = atomicState.EnableWarningTime == null ? now : atomicState.EnableWarningTime
    atomicState.EnableTraceTime = atomicState.EnableTraceTime == null ? now : atomicState.EnableTraceTime
    atomicState.lastCheckTimer = atomicState.lastCheckTimer == null ? now : atomicState.lastCheckTimer

    if (enabledebug) {
            def m = [
            "<br>now = $now",
            "<br>atomicState.EnableDebugTime = $atomicState.EnableDebugTime",
            "<br>atomicState.enableDescriptionTime = $atomicState.enableDescriptionTime",
            "<br>atomicState.EnableWarningTime = $atomicState.EnableWarningTime",
            "<br>atomicState.EnableTraceTime = $atomicState.EnableTraceTime",
            "<br>atomicState.lastCheckTimer = $atomicState.lastCheckTimer",
        ]
        log.debug m
    }


    if (atomicState.lastCheckTimer == null || (now - atomicState.lastCheckTimer) >= 1000) {

        if (description) "---------------check_logs_timer---------------"

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
                "<br>now = $now",
                "<br>now - atomicState.EnableDebugTime: ${now - atomicState.EnableDebugTime}",
                "<br>now - atomicState.enableDescriptionTime: ${now - atomicState.enableDescriptionTime}",
                "<br>now - atomicState.EnableWarningTime: ${now - atomicState.EnableWarningTime}",
                "<br>now - atomicState.EnableTraceTime: ${now - atomicState.EnableTraceTime}",
                "<br>now - atomicState.lastCheckTimer: ${now - atomicState.lastCheckTimer}",
                "<br>longTerm: $longTerm",
                "<br>mediumTerm: $mediumTerm",
                "<br>shortTerm: $shortTerm",
            ]
            if (enabledebug) log.debug message.join()
        }

        if (endDebug && enabledebug) disablelogging()
        if (endDescription && description) disabledescription()
        if (endWarning && logwarndebug) disablewarnings()
        if (endTrace && tracedebug) disabletrace()

        atomicState.lastCheckTimer = now
    }
    else {
        if (tracedebug) log.trace "log timer already checked in the last 60 seconds"
    }
}
def disablelogging(){
    log.warn "debug disabled..."
    app.updateSetting("enabledebug", [type: "bool", value: false])
}
def disabledescription(){
    log.warn "description text disabled..."
    app.updateSetting("description", [type: "bool", value: false])
}
def disablewarnings(){
    log.warn "warnings disabled..."
    app.updateSetting("logwarndebug", [type: "bool", value: false])
}
def disabletrace(){
    log.warn "trace disabled..."
    app.updateSetting("tracedebug", [type: "bool", value: false])
}
