/*
*  Copyright 2016 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Light / motion Management
*
*  Author: Elfege test///
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
            logging("button name is: $atomicState.button_name")
        }
        else {
            atomicState.button_name = "pause"
            logging("button name is: $atomicState.button_name")
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
                logging """
                doubletapable ? $doubletapable
holdable ? $holdable
pushable ? $pushable
releasable ? $releasable
"""
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
            input "motionSensors", "capability.motionSensor", title: "Choose your motion sensors", despcription: "pick a motion sensor", required: true, multiple: true, submitOnChange: true
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
                input "keepSomeSwitchesOffInCertainModes", "bool", title: "In certain modes, keep one ore some of those switches off", submitOnChange: true

                if (keepSomeSwitchesOffInCertainModes) {
                    def list = []
                    int i = 0
                    int s = switches.size()
                    for (s != 0; i < s; i++) {
                        list += switches[i].toString()
                    }

                    list = list.sort()
                    //log.debug "------------- list = $list"

                    input "modeSpecificSwitches", "mode", title: "select the modes under which you want only some specific switches to stay off", multiple: true, required: true
                    input "onlyThoseSwitchesStayOff", "enum", title: "Select the switches that will stay off in these modes", options: list, required: true, multiple: true
                    def switchesWithDimCap = switches.findAll{ it.hasCapability("Switch Level") }
                    logging "list of devices with dimming capability = $switchesWithDimCap"
                    haveDim = switchesWithDimCap.size() > 0
                    if (useDim && haveDim) {
                        input "keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes", "number", title: "When some switches are supposed to stay off, keep all dimmers at this specific value", description: "leave empty to discard this option"
                    }
                }
                else {
                    app.updateSetting("onlyThoseSwitchesStayOff", [value: null, type: "enum"])
                    app.updateSetting("modeSpecificSwitches", [value: null, type: "mode"])
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
            logging "list of devices with dimming capability = $switchesWithDimCap"
            haveDim = switchesWithDimCap.size() > 0
            logging "dimmer capability?:$haveDim"
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
                    logging "available button actions for toggling lux sensitivity: $remainingButtonCmds"

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
        section("logging"){
            input "enablelogging", "bool", title: "Enable logging", value: false, submitOnChange: true
            input "enabledescriptiontext", "bool", title: "Enable description text", value: false, submitOnChange: true
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
        log.debug "new app label: ${app.label}"
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
    logging("Installed with settings: ${settings}")
    atomicState.lastReboot = now()
    atomicState.installed = true
    initialize()

}
def updated() {
    descriptiontext "updated with settings: ${settings}"
    atomicState.installed = true
    atomicState.fix = 0
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {

    if (enablelogging == true) {
        atomicState.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptiontext "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else {
        log.warn "debug logging disabled!"
    }

    subscribe(motionSensors, "motion", motionHandler)
    log.trace "${motionSensors} subscribed to motionHandler"


    i = 0
    s = switches.size()
    for (s != 0; i < s; i++) {
        subscribe(switches[i], "switch", switchHandler)
        log.trace "${switches[i]} subscribed to switchHandler"
    }
    if (contacts) {
        i = 0
        s = contacts.size()
        for (s != 0; i < s; i++) {
            subscribe(contacts[i], "contact", contactHandler)
            log.trace "${contacts[i]} subscribed to contactHandler"
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
    // log.debug "******* Random schedule = $timer minutes....."
    schedule("0 0/${timer} * * * ?", master)

    atomicState.LuxCancelDeltaTime = 24 * 3600 // 24 hours delta time in minutes (not millis)
    atomicState.pauseDueToButtonEvent = false
    logging("initialization done")
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
        log.debug "$presenceRestricted subscribed to events"
    }
    if (absenceRestricted) {
        subscribe(absenceRestricted, "presence", presenceHandler)
        log.debug "$absenceRestricted subscribed to events"
    }
    if (absenceTimeoutSensor) {
        subscribe(absenceTimeoutSensor, "presence", presenceHandler)
        log.debug "$absenceTimeoutSensor subscribed to events"
    }
}

def appButtonHandler(btn) {

    appLabel()

    switch (btn) {
        case "pause": atomicState.paused = !atomicState.paused
            log.debug "atomicState.paused = $atomicState.paused"
        case "update":
            atomicState.paused = false
            updated()
            break
        case "run":
            if (!atomicState.paused) {

                log.trace "running master() loop() at user's request"
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

    descriptiontext """BUTTON EVT $evt.device $evt.name $evt.value
    buttonEvtTypeLux = $buttonEvtTypeLux
    buttonOverridesChecklux = $buttonOverridesChecklux
    buttonEvtTypePause = $buttonEvtTypePause
    """

    toggleLightsFromButtonEvt(evt.name)

    if (buttonOverridesChecklux && evt.name == buttonEvtTypeLux && atomicState.LuxCanceledbyButtonEvt) // cancel / resume lux sensitivity
    {
        log.debug "RESUMING USER DEFAULT LUX SENSITIVITY"
        atomicState.LuxCanceledbyButtonEvt = false
    }
    else if (buttonOverridesChecklux && evt.name == buttonEvtTypeLux && !atomicState.LuxCanceledbyButtonEvt) {
        atomicState.LuxCanceledbyButtonEvt = true
        atomicState.LuxCanceledbyButtonEvtTime = now()
        runIn(atomicState.LuxCancelDeltaTime, resetLuxCancel)
        log.trace "LUX SENSITIVITY CANCELED FOR 24 HOURS"
    }
    else if (evt.name == buttonEvtTypePause) // pause function
    {
        atomicState.paused = !atomicState.paused
        atomicState.pauseDueToButtonEvent = atomicState.paused
        logging """
        atomicState.paused = $atomicState.paused
        atomicState.pauseDueToButtonEvent = $atomicState.pauseDueToButtonEvent
        """
        if (atomicState.paused) {
            atomicState.buttonPausedTime = now()
            schedule("0 0/1 * * * ?", checkPauseButton)
            logging("--------- checkPauseButton scheduled to run every 1 minute")
            log.trace "APP PAUSED FOR $pauseDuration MINUTES"
        }
        else {
            log.trace "RESUMING APP AT USER'S REQUEST DUE TO BUTTON EVENT"
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

    logging "$evt.device is $evt.value (delay btw cmd and this event = ${now() - atomicState.mainHandlerEventTime} milliseconds"

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
    logging("$evt.name is now in $evt.value mode")
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
            log.debug "start = $start"
            log.debug "end = $end"
            log.debug "start < end ? ${start < end} || start > end ${start > end}"
            log.debug "currTime <= end ${currTime <= end} || currTime >= start ${currTime >= start}"

            result = start < end ? currTime >= start && currTime <= end : currTime <= end || currTime >= start
            if (result) break
        }
    }
    log.debug "restricted time returns $result"
    return result
}
boolean InRestrictedModeOrTime(){
    boolean inRestrictedTime = restrictedTime()
    boolean inRestrictedMode = location.mode in restrictedModes
    if (inRestrictedMode || inRestrictedTime) {
        descriptiontext "location ${inRestrictedMode ? " in restricted mode" : inRestrictedTime ? "outside of time window" : "ERROR"}, doing nothing"
        return true
    }
    return false
}
def motionHandler(evt){
    atomicState.mainHandlerEventTime = now()
    descriptiontext "${evt.name}: $evt.device is $evt.value"

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

        if (switches.any{ it -> it.currentValue("switch") == "off" }) switches.on()

        if (powerOnWithContactOnly) {
            if (location.mode in noTurnOnMode && !ignoreModes) {
                descriptiontext "$powerswitch is not being turned on because location is in $noTurnOnMode modes (${location.mode})"
                return
            }
            if (switchOnWithContactOnly) {
                if (powerswitch?.currentValue("switch") == "off") powerswitch?.on() // user might have requested contact event based only, so we can't rely on on() method only (which will test for this condition)
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

    descriptiontext "$evt.name is now $evt.value"

    def anyOn = switches.any{ it -> it.currentValue("switch") == "on" }//.size() > 0
    descriptiontext "anyOn = $anyOn"
    atomicState.LuxCanceledbyButtonEvt = atomicState.LuxCanceledbyButtonEvt == null ? false : atomicState.LuxCanceledbyButtonEvt
    boolean daytime = evt.value.toInteger() > atomicState.illuminanceThreshold && !atomicState.LuxCanceledbyButtonEvt

    atomicState.daytimeSwitchExecuted = atomicState.daytimeSwitchExecuted == null ? atomicState.daytimeSwitchExecuted = false : atomicState.daytimeSwitchExecuted

    if (daytime && anyOn && !atomicState.daytimeSwitchExecuted) { // turn off at first occurence of light sup to threshold but don't reiterate
        descriptiontext "turning off $switches 545r"
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

    logging "master start"

    appLabel()

    if (InRestrictedModeOrTime()) return

    logging "atomicState.lastRun = $atomicState.lastRun"
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
            def listOfPresence = absenceRestricted.join(", ")

            log.info "App is paused because $listOfPresence ${list.size() > 1 ? "are" : "is"} NOT present"
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
            log.trace "END OF OVERRIDE"
            atomicState.override = false
        }
        else if (atomicState.override) {
            def remain = (((overrideTime * 60 * 60 * 1000) - (now() - atomicState.overrideStart)) / (60 * 60 * 1000)).toDouble().round(2)
            log.trace "app in override mode because a switch was turned ${atomicState.switches == "off" ? "on" : "off"} manually. App will resume in ${remain} hours"
        }
    }



    if (!Active()) {
        off()
        logging "lights turned off 638ef"
    }
    else {

        if (switchOnWithContactOnly) {
            logging "switchOnWithContactOnly = $switchOnWithContactOnly"
        }
        else {
            on() 
            logging "lights turned on 638ef"
        }

    }

    if (enabledebug && now() - atomicState.EnableDebugTime > 1800000) {
        descriptiontext "Debug has been up for too long..."
        disablelogging()
    }


    logging "---end of master loop. Duration = ${now() - startTime} milliseconds"
}

def checkPauseButton(){

    log.debug("check pause")

    logging("""atomicState.pauseDueToButtonEvent = $atomicState.pauseDueToButtonEvent now() - atomicState.buttonPausedTime > pauseDuration : ${now() - atomicState.buttonPausedTime > pauseDuration * 60 * 1000}""")

    if (atomicState.pauseDueToButtonEvent && now() - atomicState.buttonPausedTime > pauseDuration * 60 * 1000) {
        atomicState.paused = false
        atomicState.pauseDueToButtonEvent = false
        log.warn "PAUSE BUTTON TIME IS UP! Resuming operations"
        unschedule(checkPauseButton)
    }
    else if (atomicState.pauseDueToButtonEvent) {
        logging("APP PAUSED BY BUTTON EVENT")
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
        descriptiontext "LUX SENNSITIVITY PAUSED BY BUTTON EVENT"
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

    logging("Currently Open Contacts $openList")
    return openList.size() > 0
}

boolean Active(){
    logging "motionSensors = $motionSensors"

    def currentlyActive = motionSensors.findAll{ it -> it.currentValue("motion") == "active" }
    if (currentlyActive?.size() > 0) {
        log.trace "${currentlyActive.join(", ")} ${currentlyActive?.size() > 1 ? "are" : "is"} currently active"
        return true
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


    descriptiontext "atomicState.activeEvents = $atomicState.activeEvents | collectionSize = $events | timeout: $timeOut $timeUnit"

    return result = events > 0 || atomicState.activeEvents > 0
}
def getTimeout(){
    def result = noMotionTime // default

    if (absenceTimeoutSensor) {
        def listOfAbsents = absenceTimeoutSensor.findAll{ it.currentValue("presence") == "not present" }
        boolean absenceRestriction = absenceTimeoutSensor ? listOfAbsents.size() == absenceTimeoutSensor.size() : false
        if (absenceRestriction) {
            descriptiontext "$absenceTimeoutSensor not present, timeout returns $absenceTimeout absenceTimeoutSensor.size() = ${absenceTimeoutSensor.size()} listOfAbsents.size() = ${listOfAbsents.size()}"
            return absenceTimeout
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
    logging("getTimeout() returns $result")
    return result
}

def off(){
    logging "off function"

    def anyOn = switches.any{ it -> it.currentValue("switch") == "on" }


    atomicState.mainHandlerEventTime = now()
    if (anyOn) {
        if (!atomicState.test && (atomicState.switches == "on" || !allowOverride)) {
            switches.off()
            descriptiontext "turning off $switches 59989e"
            atomicState.switches = "off"
        }
        else if (allowOverride && !atomicState.test && atomicState.switches == "off" && switches.any{ it -> it.currentValue("switch") == "on" })
        {
            descriptiontext "lights were turned on manually, app in override mode"
        }
        else
        {
            log.debug "$switches would have turned off - test succeeded!"
        }
    }
    else {
        logging "$switches already off"
    }

    if (powerswitch || atomicState.test) {
        logging  """
        powerswitch = $powerswitch
        waitForStatus = $waitForStatus
        anyOn = $anyOn
        atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures
        """
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
            log.debug "$powerswitch would have turned off - test succeeded !"
        }
    }
    atomicState.test = false
}
def on(){
    atomicState.mainHandlerEventTime = now()

    if (InRestrictedModeOrTime()) return

    if (!keepSomeSwitchesOffInCertainModes && noTurnOnMode) {
        app.updateSetting("noTurnOnMode", [type: "mode", value: []])
    }

    if (location.mode in noTurnOnMode && keepSomeSwitchesOffInCertainModes) {
        descriptiontext "$switches ${powerswitch ? " & $powerswitch":""} not turned on because location is in $noTurnOnMode modes (${location.mode}) 545r"

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
    logging "anyOff = $anyOff ${checklux ? " | daytime = $daytime | illuminance = $illuminance" : ""}"
    //log.warn "atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures"

    if (anyOff || (keepSomeSwitchesOffInCertainModes && location.mode in modeSpecificSwitches)) { // run only if any is off or if there's need to update states with specific switches and modes


        if (!daytime) {
            if (!powerOnWithContactOnly && !switchOnWithContactOnly) // only when user didn't ask for this to be turned on only whith contact events
            {
                if (powerswitch?.currentValue("switch") == "off") {
                    powerswitch?.on()
                    log.warn "turnging on ${powerswitch.join(", ")}"
                }
            }
            if (keepSomeSwitchesOffInCertainModes && location.mode in modeSpecificSwitches) {
                specificSwitch(false) // only the switches not selected by user will be turned on
            }
            else {
                if (anyOff && (atomicState.switch == "off" || !allowOverride)) {
                    switches.on()
                    descriptiontext "turnging on ${switches.join(", ")} 54dfze"
                    atomicState.switches = "on"
                }
                else if (anyOff && atomicState.switches == "on") {
                    descriptiontext "lights were turned off manually, app in override mode"
                }
            }
        }
        else {
            descriptiontext "daytime is on, not turning on the lights"
            boolean anyOn = switches.any{ it -> it.currentValue("switch") == "on" }
            if (anyOn && daytime && !atomicState.daytimeSwitchExecuted && (atomicState.switches == "on" || !allowOverride)) {
                // turn off at first occurence of light sup to threshold but don't reiterate so if user turns them back on they'll stay on
                descriptiontext "turning off $switches due to daytime - you can still turn them back on manually if you wish"
                switches.off()
                atomicState.switches = "off"
                atomicState.daytimeSwitchExecuted = true
            }
            else if (atomicState.switches == "off" && anyOn && allowOverride) {
                descriptiontext "lights were turned on manually, app in override mode"
            }

        }
    }
    else {
        logging "$switches already on"
    }

    if (atomicState.LuxCanceledbyButtonEvt) {
        checkLuxCancel()
    }
}

def powerSwitchOff(){
    def listOfSwitchesCurrentStates = switches.findAll{ it.currentValue("switch") } 
    boolean allStatesOk = switches.findAll{ it.currentValue(switchAttribute) == switchState }.size() == switches.size() // do another verification 
    if (!allStatesOk) {
        log.warn "One of $switches still returning '${switchState}'... $powerswitch operation ABORTED"
        def timeStamp = new Date().format("MMM dd EEE h:mm:ss a", location.timeZone)
        def culpritDevice = switches.find{ it.currentValue(switchAttribute) != switchState }
        def stringRecord = "faillure Time: $timeStamp"

        log.warn stringRecord
        // record related data for future debug 

        atomicState.switchesExpectedStatesFailures = atomicState.switchesExpectedStatesFailures == null || atomicState.switchesExpectedStatesFailures.size() > 50 ? [] : atomicState.switchesExpectedStatesFailures
        atomicState.switchesExpectedStatesFailures += stringRecord

        log.warn """end of power switch failure log
        atomicState.switchesExpectedStatesFailures = $atomicState.switchesExpectedStatesFailures
        """
        //return
    }
    else {
        log.warn "TURNING OFF $powerswitch"
        if (powerswitch?.currentValue("switch") == "on") powerswitch?.off()
    }

}
def specificSwitch(boolean exception){

    def objectDevice
    int i = 0
    int s = switches.size()
    for (s != 0; i < s; i++) {
        def device = switches[i]
        boolean thisIsTheSwitchToKeepOff = device.displayName in onlyThoseSwitchesStayOff
        logging "onlyThoseSwitchesStayOff = $onlyThoseSwitchesStayOff | $device is to be kept off = $thisIsTheSwitchToKeepOff"

        if (exception) // boolean declared true only when in notTurnOn Mode, so here we will turn lights off only
        {
            log.warn "exception true"
            if (thisIsTheSwitchToKeepOff) {
                // all still on, some need to be off
                log.warn"$device NEEDS TO BE OFF"
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability("Switch Level")) {
                    logging "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                }
                else {
                    if (device.currentValue("switch") == "on" && (atomicState.switches == "on" || !allowOverride)) {
                        device.off()
                        atomicState.switches = "off"
                    }
                    else if (atomicState.switches == "off" && allowOverride) {
                        descriptiontext "lights were turned on manually, app in override mode"
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
                        logging "${device} stays on at user's request"
                    }
                    else if (allowOverride && atomicState.switches == "on" && isOff) {
                        descriptiontext "lights were turned on manually, app in override mode"
                    }
                }
            }
            if (thisIsTheSwitchToKeepOff) // turn off the other switch 
            {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability("Switch Level")) {
                    logging "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                }
                else if (device.currentValue("switch") != "off" && (atomicState.switches == "on" || !allowOverride)) {
                    device.off()
                    atomicState.switches = "off"
                    logging "${device} is turned off at user's request"
                }
                else if (allowOverride && atomicState.switches == "off") {
                    descriptiontext "lights were turned on manually, app in override mode"
                }
            }
        }
    }
}
def dim(){

    if (keepSomeSwitchesOffInCertainModes && location.mode in modeSpecificSwitches && !keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) {
        log.warn "not dimming because app is in specific switches mode"
    }
    else {
        boolean closed = !contactsAreOpen()
        def switchesWithDimCap = switches.findAll{ it.hasCapability("SwitchLevel") }
        logging "list of devices with dimming capability = $switchesWithDimCap"

        logging "dimValClosed = $dimValClosed"

        int i = 0
        int s = switchesWithDimCap.size()

        if (closed) {
            for (s != 0; i < s; i++) {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches) {
                    switchesWithDimCap[i].setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) // dimming value kept when in specific stay off mode
                }
                else {
                    dimValClosed = dimValClosed < 10 ? 10 : dimValClosed
                    switchesWithDimCap[i].setLevel(dimValClosed)
                    logging("${switchesWithDimCap[i]} set to $dimValClosed 9zaeth")
                }
            }
        }
        else {
            if (!contactModeOk()) // ignore that location is not in the contact mode and dim to dimValClosed
            {
                for (s != 0; i < s; i++) {
                    if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches) {
                        switchesWithDimCap[i].setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) // dimming value kept when in specific stay off mode
                    }
                    else {
                        dimValClosed = dimValClosed < 10 ? 10 : dimValClosed
                        switchesWithDimCap[i].setLevel(dimValClosed)
                        logging("${switchesWithDimCap[i]} set to $dimValClosed 78fr")
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
                        logging("${switchesWithDimCap[i]} set to $dimValOpen 54fre")
                    }
                }
            }
        }
    }
}

def runCmd(String ip, String port, String path) {

    def uri = "http://${ip}${": "}${port}${path}"
    log.debug "POST: $uri"

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
def logging(msg){
    //log.warn "enablelogging ? $enablelogging" 
    if (enablelogging) log.debug msg
    if (debug && atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptiontext(msg){
    //log.warn "enabledescriptiontext = ${enabledescriptiontext}" 
    if (enabledescriptiontext) log.info msg
}
def disablelogging(){
    app.updateSetting("enablelogging", [value: "false", type: "bool"])
    log.warn "logging disabled!"
}
def formatText(title, textColor, bckgColor){
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}
