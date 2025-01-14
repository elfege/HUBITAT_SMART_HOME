/* groovylint-disable UnnecessaryGString */
/** 
 * Last Updated: 2025-01-14
 */

/*
*  Copyright 2024 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Light / motion Management
*
*  Author: Elfege 
* disabled=ts1434
*/
/** 
 * Last Updated: 2025-01-14
 */



definition(
    name: 'Advanced Motion Lighting Management V2',
    namespace: 'elfege',
    author: 'elfege',
    description: 'Switch lights with motion events V2',
    category: 'Convenience',
    iconUrl: 'http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w',
    iconX2Url: 'http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w',
    iconX3Url: 'http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w',
)
preferences {
    page(name: 'mainPage', title: 'Advanced Motion Lighting Management V2', install: true, uninstall: true) {

        section('pause'){
            input 'pause', 'button', title: "${state.paused ? 'RESUME' : 'PAUSE'}", submitOnChange: true
        }
        section('General Settings') {
            input 'appName', 'text', title: 'Name this app instance', required: false, submitOnChange: true
            appLabel()
        }
        section('Motion Sensors') {
            input 'motionSensors', 'capability.motionSensor', title: 'Select motion sensors', multiple: true, required: true
        }
        section('Switches') {
            input 'switches', 'capability.switch', title: 'Control these switches', multiple: true, required: true, submitOnChange: true
            input 'memoize', 'bool', title: 'memoize switch states (allow user override: keep a switch off when manually turned off). Note: resets after location mode change (away, night, etc.)', submitOnChange: true, defaultValue: false

            if (switches) {
                def hasDimmers = switches.any { it.hasCapability('SwitchLevel') }
                def hasColorControls = switches.any { it.hasCapability('ColorControl') || it.hasCapability('ColorTemperature') }

                if (hasDimmers) {
                    input 'useDim', 'bool', title: 'Use dimming capabilities', defaultValue: false, submitOnChange: true

                    if (useDim) {
                        input 'defaultDimLevel', 'number', title: 'Default dim level (0-100)', range: '0..100', defaultValue: 50, required: true
                        input 'useModeSpecificDimming', 'bool', title: 'Use mode-specific dimming levels', defaultValue: false, submitOnChange: true

                        if (useModeSpecificDimming) {
                            location.modes.each {
                                mode ->
                                    input "dimLevel_${mode.id}", 'number', title: "Dim level for ${mode.name} (0-100)", range: '0..100', defaultValue: defaultDimLevel, required: true
                            }
                        }

                        if (hasColorControls) {
                            input 'useColor', 'bool', title: 'Set color for RGB-capable dimmers', defaultValue: false, submitOnChange: true
                            if (useColor) {
                                input 'colorPreset', 'enum', title: 'Choose color preset', options: [
                                    'Daylight': 'Daylight',
                                    'Soft White': 'Soft White',
                                    'Warm White': 'Warm White',
                                    'Cool White': 'Cool White',
                                    'Red': 'Red',
                                    'Green': 'Green',
                                    'Blue': 'Blue',
                                    'Yellow': 'Yellow',
                                    'Purple': 'Purple',
                                    'Pink': 'Pink',
                                    'Custom': 'Custom'
                                ], defaultValue: 'Daylight', required: true, submitOnChange: true

                                if (colorPreset == 'Custom') {
                                    input 'customColorTemperature', 'number', title: 'Custom Color Temperature (2000-6500K)', range: '2000..6500', required: true
                                }
                            }
                        }
                    }
                }
            }            
            input 'keepSomeSwitchesOffInModes', 'bool', title: 'Keep some switches off in certain modes?', defaultValue: false, submitOnChange: true
            if (keepSomeSwitchesOffInModes) {
                input 'modesForSwitchesOff', 'mode', title: 'Select modes', multiple: true, required: true
                input 'switchesToKeepOff', 'capability.switch', title: 'Switches to keep off in selected modes', multiple: true, required: true
                input 'memoizeSwitchesThatAreKeptOffInModes', 'bool', title: 'Allow memoization override for these specific switches', defaultValue: true, submitOnChange: true
            }
            input 'keepSomeSwitchesOffAtAllTimes', 'bool', title: 'Keep some switches off at all times?', defaultValue: false, submitOnChange: true
            if (keepSomeSwitchesOffAtAllTimes) {
                input 'switchesToKeepOffAtAllTimes', 'capability.switch', title: 'Switches to keep off in any modes', multiple: true, required: true, submitOnChange: true
                input 'memoizeSwitchesThatAreKeptOffAtAllTimes', 'bool', title: 'Allow memoization override for these specific switches', defaultValue: true, submitOnChange: true
            }
        }
        section('Timing') {
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
        }
        section('Button Control') {
            input 'pauseButtons', 'capability.pushableButton', title: 'Buttons to pause/resume app', multiple: true, required: false, submitOnChange: true
            if (pauseButtons) {
                input 'pauseButtonAction', 'enum', title: 'Action to pause/resume', options: ['pushed', 'held', 'doubleTapped'], required: true
                input 'pauseDurationUnit', 'enum', title: 'Pause duration unit', options: ['Minutes', 'Hours'], defaultValue: 'Minutes', submitOnChange: true
                input 'pauseDuration', 'number', title: "Pause duration (${pauseDurationUnit?.toLowerCase()})", required: true, defaultValue: pauseDurationUnit == 'Hours' ? 1 : 60
                
                input 'controlLightsOnPause', 'bool', title: 'Control lights when pausing?', defaultValue: false, submitOnChange: true
                if (controlLightsOnPause) {
                    input 'pauseLightAction', 'enum', title: 'Light action when pausing', options: ['toggle', 'turn on', 'turn off'], required: true
                    input 'additionalPauseSwitches', 'capability.switch', title: 'Additional switches to control when pausing', multiple: true, required: false
                }
                
                input 'controlLightsOnResume', 'bool', title: 'Control lights when resuming?', defaultValue: false, submitOnChange: true
                if (controlLightsOnResume) {
                    input 'resumeLightAction', 'enum', title: 'Light action when resuming', options: ['toggle', 'turn on', 'turn off'], required: true
                    input 'additionalResumeSwitches', 'capability.switch', title: 'Additional switches to control when resuming', multiple: true, required: false
                }
            }
        }
        section('Restrictions') {
            input 'restrictedModes', 'mode', title: 'Restricted modes', multiple: true
            input 'restrictedTimeStart', 'time', title: "Don't run between"
            input 'restrictedTimeEnd', 'time', title: 'and'
        }
        section('Advanced') {
            input 'contacts', 'capability.contactSensor', title: 'Use contact sensors', multiple: true
            input 'useIlluminance', 'bool', title: 'Use illuminance sensor?', defaultValue: false, submitOnChange: true
            if (useIlluminance) {
                input 'illuminanceSensor', 'capability.illuminanceMeasurement', title: 'Select illuminance sensor', required: true
                input 'illuminanceThreshold', 'number', title: 'Illuminance threshold (lux)', required: true, defaultValue: 50
            }
            input 'watchdog', 'bool', title: 'Enable Watchdog', defaultValue: false

            paragraph formatText('Sensor Failure Handling', 'black', '#E8E8E8')
            input 'considerActiveWhenFail', 'bool', title: 'Consider motion active when all sensors fail?', defaultValue: false, submitOnChange: true
            if (considerActiveWhenFail) {
                paragraph formatText('When enabled, the app will consider motion as ACTIVE if all motion sensors become unresponsive. This can help prevent lights from being stuck OFF if sensors fail, but may result in unnecessary energy usage.', 'darkslate', '#F5F5F5')
            }
            else {
                paragraph formatText('When disabled, the app will consider motion as INACTIVE if all motion sensors become unresponsive. This can help prevent lights from being stuck ON if sensors fail, avoiding unnecessary energy usage.', 'darkslate', '#F5F5F5')
            }
            
            input 'flashLights', 'bool', title: 'Flash lights on sensor failure?', defaultValue: false, submitOnChange: true
            if (flashLights) {
                input 'flasher', 'capability.switch', title: 'Select light to flash', multiple: false, required: true
            }
            
            input 'notificationDevices', 'capability.notification', title: 'Select notification devices', multiple: true, submitOnChange: true

            if(flasher || notificationDevices) {
                input 'notificationModes', 'mode', title: 'Location modes when notifications can be triggered', multiple: true, required: true
            }
        }
        section('App Control') {
            input 'update', 'button', title: 'UPDATE'
            input 'run', 'button', title: 'RUN'
            input 'reset', 'button', title: 'Reset States'
            input 'checkSensors', 'button', title: 'Health Check'
            input 'closeHealthcheck', 'button', title: 'Close Health Check'
            
            if (state.showHealthCheck) {
                displayHealthCheckResults()
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

def displayHealthCheckResults() {
    def motionSensorNames = motionSensors?.collect { it.displayName } ?: []
    def functionalSensors = checkFunctionalSensors()
    def functionalSensorNames = functionalSensors?.collect { it.displayName } ?: []
    
    href(
        name: "closeHealthCheck",
        id: "healthCheck",
        title: "Close Health Check",
        required: false,
        style: "button",
        url: "#", //${location.hub.localIP}/installedapp/configure/${app.id}/mainPage",
        // onclick: "location.reload()",
        description: """
            ${generateHtmlTable()}

            <script>
            setTimeout(() => { js_resetHealthCheck() } , 60000);
            function js_resetHealthCheck() {
                console.log("closing health check section")
                const closeHealthChk = document.getElementById("settings[closeHealthcheck]")
                closeHealthChk.click()
                setTimeout(() => { location.reload() } , 1000)
            }
            </script>
        """
    )

    log.debug "state.showHealthCheck: $state.showHealthCheck"

    runIn(3, resetHealthCheck)
}

def resetHealthCheck(){
    log.warn "----------------------------resetting healthcheck bool"
    state.showHealthCheck = false
}
def installed() {
    logDebug "Installed with settings: ${settings}"
    initialize()
}
def updated() {
    logDebug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {
    logDebug 'Initializing'
    subscribe(motionSensors, 'motion', motionHandler)
    log.trace "motionSensors: ${motionSensors.join(", ")} subscribed to motion events"

    subscribe(location, 'mode', modeChangeHandler)

    subscribe(switches, 'switch', switchHandler)
    log.debug "${switches.join(', ')} subscribed to switch events (switches)"


    logDebug "${switches.join(", ")} subscribed to switch events"
    if (contacts) {
        subscribe(contacts, 'contact', contactHandler)
    }
    if (useIlluminance) {
        subscribe(illuminanceSensor, 'illuminance', illuminanceHandler)
    }
    if (switchesToKeepOff) {
        subscribe(switchesToKeepOff, 'switch', switchHandler)
        log.debug "${switchesToKeepOff.join(', ')} subscribed to switch events (switchesToKeepOff)"
    }
    if (switchesToKeepOffAtAllTimes) {
        subscribe(switchesToKeepOffAtAllTimes, 'switch', switchHandler)
        log.debug "${switchesToKeepOffAtAllTimes.join(', ')} subscribed to switch events (switchesToKeepOffAtAllTimes)"
    }
    if (pauseButtons) {
        pauseButtons.each { button ->
            subscribe(button, pauseButtonAction, buttonHandler)
            logDebug "Subscribed to ${pauseButtonAction} events for button: ${button.displayName}"
        }
    }

    scheduleNextRun()
    initializeLogging()
    resetStates()
}

def appButtonHandler(btn) {
    switch (btn) {
        case 'pause':
            state.paused = !state.paused
            log.debug "${app.label} is now ${state.paused ? 'PAUSED' : 'RESUMING'}"
            break
        case 'update':
            logDebug 'Update button pressed. Refreshing app configuration.'
            updated()
            break
        case 'run':
            if (!state.paused) {
                logDebug 'Run button pressed. Executing master() function.'
                master()
            } else {
                log.warn 'App is paused. Cannot run master() function.'
            }
            break
        case 'reset':
            resetStates()
            break
        case 'checkSensors':
            case 'checkSensors':
            getFunctionalSensors(btnCmd=true)
            state.showHealthCheck = true
            break
        case 'closeHealthCheck':
            state.showHealthCheck = false
            break
    }
    appLabel()
}
def hubEventHandler(evt) {
    switch (evt.name) {
        case 'systemStart':
            log.warn 'Hub has rebooted or app has been updated'
            // might want to re-initialize some states or perform certain actions here
            updated()
            break
        case 'zigbeeStatus':
            if (evt.value != 'online') {
                log.error 'Zigbee network is offline or experiencing issues'
            } else {
                logInfo 'Zigbee network is back online'
            }
            break
        case 'zwaveStatus':
            if (evt.value != 'online') {
                log.error 'Z-Wave network is offline or experiencing issues'
            } else {
                logInfo 'Z-Wave network is back online'
            }
            break
        case 'hubHealthStatus':
            if (evt.value != 'online') {
                log.error "Hub health status: ${evt.value}"
            } else {
                logInfo 'Hub health status is back to normal'
            }
            break
        case 'internetStatus':
            if (evt.value != 'full') {
                log.warn "Internet connectivity issue: ${evt.value}"
            } else {
                logInfo 'Internet connectivity restored'
            }
            break
        case 'cloudStatus':
            if (evt.value != 'connected') {
                log.warn "Cloud connection issue: ${evt.value}"
            } else {
                logInfo 'Cloud connection restored'
            }
            break
        case 'alertStatus':
            log.warn "Hub alert: ${evt.value}"
            break
        case 'cpuUsage':
            if (evt.numberValue > 80) {
                log.warn "High CPU usage: ${evt.numberValue}%"
            }
            break
        case 'memoryUsage':
            if (evt.numberValue > 80) {
                log.warn "High memory usage: ${evt.numberValue}%"
            }
            break
        default:
            logDebug "Unhandled hub event: ${evt.name} = ${evt.value}"
    }


}
def motionHandler(evt) {
    if (state.paused) {
        log.info "Motion detected, but app is paused. Ignoring."
        checkPauseTimer()
        return
    }

    logInfo "*******motion event: $evt.displayName is ${evt.value}"

    state.functionalSensors = state.functionalSensors ?: [:]

    // Store status with both functional state and device ID
    // Set to true since we just received an event from this sensor
    state.functionalSensors[evt.device.id] = [
        functional: true,  // set to true since receiving an event proves functionality
        deviceId: evt.device.id,
        displayName: evt.device.displayName

    ]

    if (InRestrictedModeOrTime()) return

    
    
    if(evt.value == "active") {
        master(motionActiveEvent=true) 
    }
    else {
        def now = now()
        def lastMotionHandled = state.lastMotionHandled ?: 0
        def intervalBetweenEvents = 30 // in seconds
        def someSecondsAgo = now - intervalBetweenEvents * 1000 // N seconds in milliseconds
        
        if (lastMotionHandled > someSecondsAgo) {
            master()
        }
    }
    
    state.lastMotionHandled = now
}
def buttonHandler(evt) {
    log.debug "Button event: name=${evt.name}, value=${evt.value}, deviceId=${evt.deviceId}"

    if (evt.name == pauseButtonAction) {
        def currentPauseState = state.paused // get current state's value
        log.warn formatText("currentPauseState: $currentPauseState", "black", "yellow")
        log.trace "state.paused: $state.paused"

        if (state.paused == null) {
            log.error "state.paused is null???"
            resetStates()
            log.trace "state.paused (after resetStates()): $state.paused"
        }

        def newPauseState = !currentPauseState // toggle state's value
        state.paused = newPauseState // Persist state update (not necessarily available in this execution)

        log.warn formatText("newPauseState: $newPauseState", "black", "green")
        

        if (newPauseState == true) {
            state.pauseDueToButtonEvent = true
            pauseApp() // motion events will trigger a double check of the timing.
        } else { 
            state.pauseDueToButtonEvent = false
            resumeNormalOperation()
        }
    } else {
        log.warn "Unexpected button action: ${evt.name}. Expected: ${pauseButtonAction}"
    }
}
def switchHandler(evt) {
    if (state.paused) return

    log.info "$evt.device is $evt.value"

    // def switchName = evt.displayName
    // def memoize = memoizeThisSwitch(evt.deviceId.toString())

    
    
}
def contactHandler(evt) {
    if (state.paused) return

    if (InRestrictedModeOrTime()) return

    if (evt.value == 'open') {
        if (switches.any{ it -> it.currentValue('switch') == 'off' }) {
            switches.on()
            logDebug ('switches on 5dfrj')
        }

        if (powerOnWithContactOnly) {
            if (location.mode in noTurnOnMode && !ignoreModes) {
                logInfo "$powerSwitch is not being turned on because location is in $noTurnOnMode modes (${location.mode})"
                return
            }
            if (switchOnWithContactOnly) {
                if (powerSwitch?.currentValue('switch') == 'off') {
                    logDebug ('switches on 34ghj4')
                    powerSwitch?.on()
                }
            }
        }
    }
}
def illuminanceHandler(evt) {
    if (state.paused || InRestrictedModeOrTime()) return

    def lastIlluminanceHandled = state.lastIlluminanceHandled ?: 0
    def now = now()
    if (now - lastIlluminanceHandled < 60000) { // 1 minute debounce
        logDebug "Illuminance change within 1 minute of last handled event. Skipping processing."
        return
    }

    state.lastIlluminanceHandled = now

    logInfo "$evt.name is now $evt.value"
    
    boolean daytime = evt.value.toInteger() > illuminanceThreshold
    if (daytime != state.lastDaytimeState) {
        state.lastDaytimeState = daytime
        unschedule(master)
        master()
    }
}
def modeChangeHandler(evt) {
    logDebug ("$evt.name is now in $evt.value mode")
    resetStates()
    unschedule(master)
    master()
}

def master(motionActiveEvent=false) {
    def startTime = now()
    logDebug 'master start'

    check_logs_timer()
    appLabel()

    if (state.paused) {
        logDebug "App is paused. Skipping master execution."
        return
    }

    if (exceptions()) {
        scheduleNextRun()
        return
    }

    if (motionActiveEvent || Active()) {
        controlLights('turn on')
    } else {
        controlLights('turn off')
    }

    scheduleNextRun()

    logDebug "---end of master loop. Duration = ${now() - startTime} milliseconds"
}

def pauseApp() {
    log.debug "Pausing $app.label..."
    state.pauseStart = now()
    state.currentPauseDuration = pauseMillis
    def formattedDuration = formatPauseDuration()
    logDebug "App paused for ${formattedDuration}"    

    schedule("0 * * * * ?", checkPauseTimer)// check every minute

    if (controlLightsOnPause) {
        handleLightsPauseResume("pause")
    }
}
def handleLightsPauseResume(action){
    def switchesToControl = action == "pause" ? additionalPauseSwitches : additionalResumeSwitches
    switchesToControl += switches 

    log.debug "controlLightsOnPause: $controlLightsOnPause"
    log.debug "pauseLightAction: $pauseLightAction"
    log.debug "switchesToControl: $switchesToControl"

        if (controlLightsOnPause == "toggle") {
            switchesToControl.each { sw ->        
                toggleLight(sw)
            }
        } else {
            def cmd
            if(action == "pause") cmd = pauseLightAction == "turn off" ? "off" : "on"
            if(action == "resume") cmd = resumeLightAction == "turn off" ? "off" : "on"
            if(!cmd) {
                log.error "no cmd? pauseLightAction = $pauseLightAction"
                cmd = "off"
            }

            // we don't use controlLights() method here as there are too many interpolled switch categories, exceptions and exclusions managed by it
            // which is not only useless here, it's also susceptible to mangle the outcome, notably because some, if not all of additionalPauseSwitches could 
            // be outside of the motion controled swithces group (main switches): which controlLights() method actively exclude. 
            switchesToControl.each { sw -> 
                def swLink = "<a href='http://${location.hub.localIP}/device/edit/${sw.id}' target='_blank'>${sw.displayName}</a>"
                logTrace "${sw.displayName} $action cmd -> $cmd | ${swLink}"
                // log.warn formatText("TESTING! uncomment the line below when done!", "white", "red")
                sw."${cmd}"() 
            }
            resetStates() // we reset the states so as to ensure that once the app resumes, current statuses are not recorded as user override.
            // This is why state.paused must not ever be reset to false in resetStates() method. 
        }
}
def resumeNormalOperation() {

    log.debug formatText("RESUMING NORMAL OPERATION", "white", "green")
    state.pauseStart = null
    logDebug 'Resuming normal operation'

    if (controlLightsOnResume) {
        handleLightsPauseResume("resume")
    }

    runIn(1, master)  // Schedule master to run shortly after resuming
    appLabel()
}
def checkPauseTimer() {
    if (!state.pauseStart || !state.currentPauseDuration) {
        log.warn "Pause state variables missing - resetting"
        state.pauseStart = now()
        state.currentPauseDuration = getPauseDurationMillis()
    }

    def now = now()
    def elapsedTime = now - state.pauseStart
    def remainingTimeMillis = state.currentPauseDuration - elapsedTime
    remainingTime = pauseDurationUnit == "Hours" ? (remainingTimeMillis / 60 / 60 / 1000) : (remainingTimeMillis / 60 / 1000)

    if (state.paused && elapsedTime > state.currentPauseDuration) {
        state.paused = false
        state.pauseStart = null
        state.currentPauseDuration = null
        log.warn 'PAUSE TIME IS UP! Resuming operations'
        unschedule(checkPauseTimer)
        resumeNormalOperation()
    } else if (state.paused) {
        if (state.pauseDueToButtonEvent) {
            log.info "${app.label} PAUSED BY BUTTON EVENT - ${formatPauseDuration(remainingTime)} remaining"
        } else {
            log.info "${app.label} PAUSED - ${remainingMinutes} minutes remaining"
        }
    }
}
def formatPauseDuration(duration=pauseDuration) {
    // Convert to BigDecimal and set scale to 2 decimal places
    def formattedDuration = new BigDecimal(duration).setScale(2, BigDecimal.ROUND_HALF_UP)
    
    if (pauseDurationUnit == 'Hours') {
        return "${formattedDuration} hour${formattedDuration == 1.00 ? '' : 's'}"
    } else {
        return "${formattedDuration} minute${formattedDuration == 1.00 ? '' : 's'}"
    }
}

def controlLights(action) {
    def allSwitches = getAllSwitches()

    switch (action) {
        case 'toggle':
            allSwitches.each {
                sw ->
                if (!shouldKeepSwitchOff(sw.id)) {
                    if (sw.currentValue('switch') == 'on') {
                        logTrace "Toggling off ${getDeviceUrl(sw.id, sw.displayName)}"
                        sw.off()
                    } else {
                        logTrace "Toggling on ${getDeviceUrl(sw.id, sw.displayName)}"
                        sw.on()
                    }
                } else {
                    logDebug "Skipped toggling ${getDeviceUrl(sw.id, sw.displayName)} due to keep-off rule"
                }
            }
            resetStates() // toggles are user intervention bound. So reset any manual override memoization. 
            break
        case 'turn on':
            if (exceptions()) {
                log.error "Exception..."
                break
            }

            allSwitches.each { sw ->
                logTrace "switch -> ${getDeviceUrl(sw.id, sw.displayName)}"

                if (isNotBothAdditionaAndRegularSwitch(sw.id)){
                    logTrace "${getDeviceUrl(sw.id, sw.displayName)} is not a regular motion controlled switch. Skipping"
                    return // this works like 'continue' in the closure
                }

                def mem = memoizeThisSwitch(sw.id)

                logDebug "state.switchState: $state.switchState"

                if (!shouldKeepSwitchOff(sw.id)) {
                    handleLevelAndColors(deviceId=sw.id, mem=mem, cmd="on")
                    if (sw.currentValue("switch") == "on"){
                        logTrace "${getDeviceUrl(sw.id, sw.displayName)} already on. Skipping"
                        return  // this works like 'continue' in the closure
                    }
                    if (state.switchState[sw.displayName] == "on" && mem) {
                        log.warn "${getDeviceUrl(sw.id, sw.displayName)} has already been turned on by this app and is memoized. Skipping."
                    } else {
                        logInfo "turning on ${getDeviceUrl(sw.id, sw.displayName)}"
                        updateSwitchMem(mem = mem, value = "on", deviceName = sw.displayName)
                        runIn(1, "setSwitch", [data: [deviceId: sw.id, value: "on"], overwrite: false])
                    }
                    
                } else {
                    handleLevelAndColors(deviceId=sw.id, mem=mem, cmd="off")
                    if (sw.currentValue("switch") == "off"){
                        logTrace "${getDeviceUrl(sw.id, sw.displayName)} already off. Skipping"
                        return  // this works like 'continue' in the closure
                    }
                    if (state.switchState[sw.displayName] != "off" && mem) {
                        updateSwitchMem(mem = mem, value = "off", deviceName = sw.displayName)
                        runIn(1, "setSwitch", [data: [deviceId: sw.id, value:"off"], overwrite: false])
                    }
                    else {
                        logWarn "Skipped shouldKeepSwitchOff off for ${getDeviceUrl(sw.id, sw.displayName)}; manually turned on"
                    }
                    
                }
            }
            break
        case 'turn off':
            allSwitches.each {
                sw ->

                if (isNotBothAdditionaAndRegularSwitch(sw.id)){
                    log.debug "${sw.displayName} is not a regular motion controlled switch. Skipping"  
                    return // this works like 'continue' in the closure
                }

                def mem = memoizeThisSwitch(sw.id)                

                log.debug "eval case turn off for ${getDeviceUrl(sw.id, sw.displayName)}"

                if (sw.currentValue("switch") == "off"){
                    logTrace "${getDeviceUrl(sw.id, sw.displayName)} already off. Skipping"
                    return  // this works like 'continue' in the closure
                }

                if (state.switchState[sw.displayName] == "off" && mem) {
                    logWarn "${getDeviceUrl(sw.id, sw.displayName)} has already been turned off by this app and is memoized. Skipping."
                } else {
                    logTrace "turning off ${getDeviceUrl(sw.id, sw.displayName)} in 1 second..."
                    updateSwitchMem(mem = mem, value = "off", deviceName = sw.displayName)
                    runIn(1, "setSwitch", [data: [deviceId: sw.id, value:"off"], overwrite: false])

                }
                handleLevelAndColors(deviceId=sw.id, mem=mem, cmd="off")
            }
            break
        default:
            logWarn "Unknown light control action: ${action}"
    }
}

def handleLevelAndColors(deviceId, mem, cmd) {   
    
    if (useDim) {
        def sw = getDeviceById_switch(deviceId)
        
        if(!sw.hasCommand('setLevel')) return

        def currentLevel = sw.currentValue('level')
        def targetLevel = cmd == "off" ? 0 : getCurrentDimLevelPerMode()

        logTrace """<b>
        <br> ${sw.displayName} current level is: ${currentLevel}
        <br> Current location mode is: ${location.mode}
        <br> targetLevel is: ${targetLevel} 
        </b>
        """
        
        // Only change level if it's different from current
        if (currentLevel != targetLevel) {
            if (cmd == "off") {
                logDebug "${sw.displayName}: Turning off"
                updateLevelMem(mem=mem, value=0, deviceName=sw.displayName)
                runIn(1, "_setLevel", [data: [deviceId: sw.id, value: 0], overwrite: false])
            } else if (mem && state.dimLevel[sw.displayName] == targetLevel) {
                log.warn "${getDeviceUrl(sw.id, sw.displayName)} has already been set to $targetLevel by this app and is memoized. Skipping."
            } else {
                logDebug "${sw.displayName}: Setting level to ${targetLevel}%"
                updateLevelMem(mem=mem, value=targetLevel, deviceName=sw.displayName)
                runIn(1, "_setLevel", [data: [deviceId: sw.id, value: targetLevel], overwrite: false])
            }
        } else {
            logDebug "${sw.displayName}: Current level ${currentLevel}% matches target level, no change needed"
        }

        // Handle colors if enabled and capable
        if (useColor && sw.hasCapability('ColorControl')) {
            def colorValue = cmd == "off" ? null : getColorValue()
            if (colorValue) {
                if (colorValue.containsKey('colorTemperature') && sw.hasCapability('ColorTemperature')) {
                    handleColorTemperature(sw, colorValue.colorTemperature, mem)
                } else {
                    handleColor(sw, colorValue, mem)
                }
            }
        }
    }
}
def setSwitch(data) {
    if (data){
        if (data.deviceId) {
            def device = getDeviceById_switch(data.deviceId)
            logDebug "device is $device"
            if (device) {
                device."${data.value}"()
                logInfo "Successfully turned ${data.value} ${device.displayName}"
            } else {
                logError "Device with ID ${data.deviceId} not found."
            }
        } else {
            logError "Missing deviceId in data: $data"
        }
    } else {
        logError "No device data parsed to turnOnSwitch."
    }

    
}
private void handleColorTemperature(device, temperature, mem) {
    if (state.colorTemperature[device.displayName] == temperature) {
        log.warn "${device.displayName)} ColorTemperature already set to $temperature by this app and is memoized. Skipping."
    }
    else {
        updateTempMem(mem=mem, tempValue=temperature, deviceName=device.displayName)
        runIn(1, "_setColorTemperature", [data: [deviceId: device.id, value: temperature], overwrite: false])
    }
}
def _setColorTemperature(data){
    if (data){
        if (data.deviceId && data.value != null) {
            def device = getDeviceById_switch(data.deviceId)
            if (device) {
                
                device.setColorTemperature(data.value)
                logInfo "Successfully set ${device.displayName} color temperature to ${data.value}"
            } else {
                logError "Device with ID ${data.deviceId} not found."
            }
        } else {
            logError "Missing deviceId in data: $data"
        }
    } else {
        logError "No device data parsed to _setColorTemperature. data: ${data}"
    }
}
private void handleColor(device, colorValue, mem) {
    if (state.color[device.displayName] == colorValue) {
        log.warn "${device.displayName)} color already set to $colorValue by this app and is memoized. Skipping."
    }
    else {
        updateColorMem(mem=mem, colorValue=colorValue, deviceName=device.displayName)
        runIn(1, "_setColor", [data: [deviceId: device.id, value: colorValue], overwrite: false])
    }
}
def _setColor(data){
    if (data){
        if (data.deviceId && data.value != null) {
            def device = getDeviceById_switch(data.deviceId)
            if (device) {
                
                device.setColorTemperature(data.value)
                logInfo "Successfully set ${device.displayName} color to ${data.value}"
            } else {
                logError "Device with ID ${data.deviceId} not found."
            }
        } else {
            logError "Missing deviceId in data: $data"
        }
    } else {
        logError "No device data parsed to _setColor. data: ${data}"
    }
}
def _setLevel(data){
    if (data){
        if (data.deviceId && data.value != null) {
            def device = getDeviceById_switch(data.deviceId)
            if (device) {
                device.setLevel(data.value)
                logInfo "Successfully set ${device.displayName} level to ${data.value}"
            } else {
                logError "Device with ID ${data.deviceId} not found."
            }
        } else {
            logError "Missing deviceId in data: $data"
        }
    } else {
        logError "No device data parsed to _setLevel. data: ${data}"
    }
}
def toggleLight(sw){
    if(sw.currentValue("switch") == "on") {
        sw.off()
    } else {
        sw.on()
    } 
}

def isNotBothAdditionaAndRegularSwitch(deviceId) {
    // If a device is not in the main 'switches' list but IS in one of the additional lists,
    // then it should not be turned on/off by motion events.
    // Return true if it's in additional lists but not in regular
    return ((additionalPauseSwitches ?: []) + (additionalResumeSwitches ?: [])).any { it?.id == deviceId } && !((switches ?: []).any { it?.id == deviceId })
}
def memoizeThisSwitch(deviceId){

    if (memoize){
        // always return true if user selected memoization for all
        return true
    }
        
    if(memoizeSwitchesThatAreKeptOffAtAllTimes){
        def memThisOne = switchesToKeepOffAtAllTimes.any{ sw ->         
            
            if (sw.id == deviceId)
            {
                logInfo "${sw.displayName} (id:${sw.id}) - is memoized"
                return true
            } 
        
        }
    }
    if(memoizeSwitchesThatAreKeptOffInModes){
        def memThisOne = switchesToKeepOff.any{ sw ->         
            
            if (sw.id == deviceId)
            {
                logInfo "${sw.displayName} (id:${sw.id}) - is memoized"
                return true
            } 
        
        }
    }
    
    return false
}

def updateSwitchMem(mem, value, deviceName){

    logDebug "----------- mem = $mem"

    if (mem) {
        logTrace "updateSwitchMem: memoizing state '${value}' for ${deviceName}"
        state.switchState[deviceName] = value
    }
    else {
        logWarn "NOT memoizing state '${value}' for ${deviceName}"
    }
}
def updateColorMem(mem, value, deviceName){
    if (mem) {
        logTrace "updateColorMem: memoizing state '${value}' for ${deviceName}"
        state.color[deviceName] = value
    }
    else {
        logWarn "NOT memoizing state '${value}' for ${deviceName}"
    }
}
def updateTempMem(mem, value, deviceName){
    if (mem) {
        logTrace "updateTempMem: memoizing state '${value}' for ${deviceName}"
        state.colorTemperature[deviceName] = value
    }
    else {
        logWarn "NOT memoizing state '${value}' for ${deviceName}"
    }
}
def updateLevelMem(mem, value, deviceName){
    if (mem) {
        logTrace "updateLevelMem: memoizing state '${value}' for ${deviceName}"
        state.dimLevel[deviceName] = value
    }
    else {
        logWarn "NOT memoizing state '${value}' for ${deviceName}"
    }
}
def resetStates(){

    def now = now()

    /** Purpose of Memoization 
    * First, and foremost, SAFETY! allows other systems to use colors as needed (e.g. for water leak, smoke, gaz, fire alerts, etc.)
    */
    state.color = [:]                           // initialize memoization for colors
    state.colorTemperature = [:]                // initialize memoization for color temperatures
    state.dimLevel = [:]                        // initialize memoization for switch level
    state.switchState = [:]                     // initialize memoization for on/off sw states

    state.paused = state.paused == null ? false : state.paused // preserve the state if it exists. 
    state.pauseStart = state.pauseStart ?: now

    state.functionalSensors = [:]
    
    // Pre-populate with all known sensors
    motionSensors.each { sensor ->
        state.functionalSensors[sensor.id] = [
            functional: true,  // default to true until proven otherwise
            deviceId: sensor.id,
            displayName: sensor.displayName
        ]
    }
    checkFunctionalSensors() // update from devices retuned values

    state.functionalSensors.each { it -> 
        log.debug "${it}"
    }

    state.lastMotionHandled = state.lastMotionHandled ?: now
    

    state.pauseDueToButtonEvent = state.pauseDueToButtonEvent ?: false

    state.EnableDebugTime = state.EnableDebugTime ?: now
    state.EnableTraceTime = state.EnableTraceTime ?: now
    state.EnableInfoTime = state.EnableInfoTime ?: now
    state.EnableWarnTime = state.EnableWarnTime ?: now 

    state.lastCheckTimer = state.lastCheckTimer ?: now

    state.locationUrl = "http://${location.hub.localIP}/device/edit"

    log.trace "---------- states reset ok ----------"
        
}

// NOT IN USE BUT PRESERVED AS IT CAN BE USEFULL IN THERMOSTAT MANAGER
def cleanUpSwitchState() {
    cleanUpStateForDeviceType('switchState', getAllSwitches(), 'switch')
}
def cleanUpStateForDeviceType(stateMapKey, deviceList, deviceType) {
    /**
    * Cleans up the state map for a specific device type by ensuring all device names
    * in the state map correspond to existing devices in the provided device list.
    *
    * @param stateMapKey The key in the state object (e.g., 'switchState').
    * @param deviceList The list of current devices of the specified type.
    * @param deviceType A string representing the device type for logging purposes.
    */
    /** 
    * Last Updated: 2025-01-14
    */

    def originalState = state."${stateMapKey}" ?: [:]
    def validDeviceNames = deviceList.collect { it.displayName }  // Collect device display names
    
    // Retain only valid keys (device names) in the state map
    state."${stateMapKey}" = originalState.findAll { deviceName, value -> 
        if (validDeviceNames.contains(deviceName)) {
            true  // Retain this entry in the state map
        } else {
            // Log the removal and remove invalid device entry from the state map
            log.warn "${deviceType.capitalize()} '${deviceName}' is no longer valid. Removing from state.${stateMapKey}."
            false  // Remove this entry from the state map
        }
    }
}

def getAllSwitches() {
    // Combine all switch lists, defaulting to empty lists if any are null
    def combinedSwitches = (switches ?: []) +
                           (additionalResumeSwitches ?: []) +
                           (additionalPauseSwitches ?: []) +
                           (switchesToKeepOff ?: []) +
                           (switchesToKeepOffAtAllTimes ?: [])

    // Filter out any null entries to avoid errors
    def nonNullSwitches = combinedSwitches.findAll { it != null }

    // Deduplicate switches by their 'id' to ensure each switch is only included once
    // This is necessary because the same switch can appear in multiple lists
    def uniqueSwitches = nonNullSwitches.unique { it.id }

    // Return the final list of unique switches
    return uniqueSwitches
}
def getDeviceById_switch(deviceId) {
    def allSwitches = getAllSwitches()
    def device = allSwitches.find { it.id == deviceId }
    if (!device) {
        log.warn "Device with ID ${deviceId} not found in switches."
    }
    return device
}
def getDeviceById_sensor(deviceId) {
    def device = motionSensors.find { it.id == deviceId }
    if (!device) {
        log.warn "Device with ID ${deviceId} not found in motion sensors."
    }
    return device
}
def getPauseDurationMillis() {
    if (!pauseDuration) {
        log.warn "No pause duration specified - defaulting to 60 minutes"
        return 60 * 60 * 1000
    }
    def pauseDurationMillis = pauseDurationUnit == 'Hours' ? 
        pauseDuration * 60 * 60 * 1000 : // Hours to milliseconds
        pauseDuration * 60 * 1000        // Minutes to milliseconds

    log.debug "Pause Will last $pauseDuration $pauseDurationUnit"
    log.debug "Pause Duration in milliseconds: $pauseDurationMillis"
    return pauseDurationMillis
}

def Active() {

    // return false

    logDebug "Checking if motion is active. Motion sensors: $motionSensors"

    def functionalSensors = getFunctionalSensors()

    // log.debug "functionalSensors: <br><ul> ${functionalSensors?.each { sensor -> sensor?.displayName }.join('<br><b><li></b>') }</ul>"
    
    def motionSensorNames = motionSensors?.collect { it.displayName } ?: []
    def functionalSensorNames = functionalSensors?.collect { it.displayName } ?: []

    if(enableTrace || !functionalSensors) {
        log.trace """
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
        log.trace "--- Motion is currently active on at least one sensor."
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

    log.warn "anyActiveWithinTimePeriod within the last ${timeOut} ${timeUnit} = $anyActiveWithinTimePeriod"
    return anyActiveWithinTimePeriod
}
def sendAlert() {
    if (location.mode in notificationModes){
        def now = now()
        def lastAlertTime = state.lastAlertTime ?: 0
        def oneHourInMillis = 60 * 60 * 1000

        // Only send alerts once per hour
        if (now - lastAlertTime > oneHourInMillis) {
            // Send notification
            if (notificationDevices) {
                def msg = "${app.label}: All motion sensors are unresponsive!"
                notificationDevices.deviceNotification(msg)
                logDebug "Notification sent to ${notificationDevices}"
            }

            // Flash lights if enabled
            if (flashLights && flasher) {
                3.times {
                    flasher.on()
                    pauseExecution(500)
                    flasher.off()
                    pauseExecution(500)
                }
                logDebug "Flashed ${flasher.displayName} for alert"
            }

            state.lastAlertTime = now
        }
    }
}
def getFunctionalSensors(btnCmd=false) {
    /** 
    * Last Updated: 2025-01-14
    */
    def lastCheck = state.lastEventHistoryCheck ?: 0
    def nowTime = now()
    def functionalSensors = []  // Initialize local array
    state.functionalSensors = state.functionalSensors ?: resetStates()  // Initialize global state array if null
    state.locationUrl = state.locationUrl ?: resetStates()

    // Skip if the function was run less than 10 minutes ago, unless it's a btnCmd (from btn handler)
    if (btnCmd || nowTime - lastCheck < (10 * 60 * 1000)) {
        logDebug "Skipping getFunctionalSensors: Last check was ${(nowTime - lastCheck) / 60000} minutes ago."

        if (state.functionalSensors && !state.functionalSensors.isEmpty()) {
            // Display previously memoized alerts for unresponsive sensors
            logDebug "state.functionalSensors: $state.functionalSensors"            
            def iterated = false
            state.functionalSensors.collect { it -> 
                /**
                    Structure:
                        [
                            deviceId:   [
                                            funcitonal=true,
                                            deviceId:254,
                                            displayName: the device's name
                                        ]
                        ]
                */
                def device = it.value
                if (!device.functional) {
                        def m = "${app.label}: ${device.displayName} appears UNRESPONSIVE -- <a href='${state.locationUrl}/${device.deviceId}' target='_blank'>Manage Device</a>"
                        log.warn formatText(m, "black", "#E0E0E0")
                
                    if(!iterated){
                        if (enableDebug || btnCmd)  log.info generateHtmlTable()
                    }
                    iterated = true // avoid generating/logging the table over each iteration
                }
            }

            // Return device objects that were previously determined to be functional
            functionalSensors = motionSensors.findAll { sensor -> 
                state.functionalSensors[sensor.id]?.functional == true 
            }
        } else {
            log.warn "No functional sensor to log... re-evaluating..."
            // functionalSensors = checkFunctionalSensors()???? 
        }
    }
    // redundant else on purpose for better readability/explicit logic
    else {        
        functionalSensors = checkFunctionalSensors()
    }

    if (functionalSensors.isEmpty()) {
        def m = "ALL MOTION SENSORS ARE UNRESPONSIVE IN ${app.label}"
        log.warn formatText(m, "yellow", "red")
    }

    // return functional sensors as device objects
    return functionalSensors
}
def generateHtmlTable(){
    def list_functional = """              
        <ol>
            ${state.functionalSensors.collect{ sensorState -> 
                sensorState.value.functional ? 
                    "<li><a href='${state.locationUrl}/${sensorState.value.deviceId}' target='_blank'>${sensorState.value.displayName}</a></li>" : 
                    "" 
            }.join()}
        </ol>
    """
    
    def list_not_functional = """
        <ol>
            ${state.functionalSensors.collect{ sensorState -> 
                !sensorState.value.functional ? 
                    "<li><a href='${state.locationUrl}/${sensorState.value.deviceId}' target='_blank'>${sensorState.value.displayName}</a></li>" : 
                    "" 
            }.join()}
        </ol>
    """
    def table = """
        <style>
            .tablediv {
                background: white; 
                padding: 20px;
                border-radius: 5px;
                margin: 10px 0;
            }
            table {
                font-family: arial, sans-serif;
                border-collapse: collapse;
                width: 100%;
            }

            td {
                border: 1px solid rgb(105, 29, 29);
                text-align: left;
                padding: 8px;
            }

            th {
                border: 1px solid rgb(15, 14, 14);
                text-align: center;
                padding: 8px;
            }

            .not_functioning {
                color: red;
                font-weight: 900;
            }
            .functioning {
                color: green;
                font-weight: 900;
            }
        </style>

        <div class=tablediv>
            <table>
                <tr>
                    <th class="not_functioning" >UNRESPONSIVE SENSORS</th>
                    <th class="functioning" >FUNCTIONAL SENSORS</th>
                </tr>
                <tr>
                    <td>${list_not_functional}</td>
                    <td>${list_functional}</td>
                </tr>
            </table>
        </div>
    """

    return table
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
            log.warn formatText(m, "black", "#E0E0E0")
        }

        hasMotionEvents
    }

    return functionalSensors
}



def scheduleNextRun() {
    def timeout = getTimeout()
    def nextRun = timeout * (timeUnit == 'minutes' ? 60 : 1)

    // ensure nextRun is never lower than 5 minutes
    def minInterv = 300
    if (nextRun < minInterv) {
        nextRun = minInterv
    }

    runIn(nextRun.toInteger(), 'master')
    logDebug "Next master() run scheduled in ${nextRun} seconds"
}
def shouldKeepSwitchOff(deviceId) {
    def device = getDeviceById_switch(deviceId)
    logDebug "Checking if ${device.displayName} should be kept off"
    logDebug "keepSomeSwitchesOffInModes: $keepSomeSwitchesOffInModes"
    logDebug "modesForSwitchesOff: $modesForSwitchesOff"
    logDebug "switchesToKeepOff: ${switchesToKeepOff?.collect { it.displayName }}"
    logDebug "switchesToKeepOffAtAllTimes: ${switchesToKeepOffAtAllTimes?.collect { it.displayName }}"
    logDebug "Current mode: ${location.mode}"

    if (switchesToKeepOffAtAllTimes && switchesToKeepOffAtAllTimes?.find { it.id == device.id }) {
        return true
    }

    if (keepSomeSwitchesOffInModes && modesForSwitchesOff?.contains(location.mode) && switchesToKeepOff?.find { it.id == deviceId }) {
        logInfo "Keeping ${device.displayName} off due to current mode: ${location.mode}"



        return true
    }

    logDebug "${device.displayName} can be turned on"
    return false
}
def exceptions()  {
    if (InRestrictedModeOrTime()) {
        logDebug 'In restricted mode or time, exiting on() method'
        return true
    }

    if (useIlluminance && illuminanceSensor.currentValue('illuminance') > illuminanceThreshold) {
        log.trace("Current illuminance is above threshold. Not turning on lights.")
        return true
    }

    return false
}
def appLabel() {
    def baseName = settings?.appName ?: 'Advanced Motion Lighting Management V2'
    def pausedSuffix = '(Paused)'

    logDebug "Current app label: ${app.label}"
    logDebug "Base name: ${baseName}"
    logDebug "state.paused: ${state.paused}"
    
    def newLabel = baseName

    if (state.paused) {
        newLabel += " ${pausedSuffix}"
    }

    if (app.label != newLabel) {
        app.updateLabel(newLabel)
        logDebug "Updated app label to: ${newLabel}"
    } else {
        logDebug 'App label unchanged'
    }
}
def InRestrictedModeOrTime() {
    boolean inRestrictedTime = restrictedTime()
    boolean inRestrictedMode = location.mode in restrictedModes
    if (inRestrictedMode || inRestrictedTime) {
        logInfo "location ${inRestrictedMode ? ' in restricted mode' : inRestrictedTime ? 'outside of time window' : 'ERROR'}, doing nothing"
        return true
    }
    return false
}
def restrictedTime() {
    result = false
    if (!restrictedTimeSlots) return false

    int s = restrictedTimeSlots.size()
    for (int i = 0; i < s; i++) {
        def starting = settings["restrictedTimeStart${i}"]            
        def ending = settings["restrictedTimeEnd${i}"]
        def currTime = now()
        def start = timeToday(starting, location.timeZone).time
        def end = timeToday(ending, location.timeZone).time
        logDebug "start = $start"
        logDebug "end = $end"
        logDebug "start < end ? ${start < end} || start > end ${start > end}"
        logDebug "currTime <= end ${currTime <= end} || currTime >= start ${currTime >= start}"

        result = start < end ? currTime >= start && currTime <= end : currTime <= end || currTime >= start
        if (result) break
    }
    
    logDebug "restricted time returns $result"
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
                if (enableTrace) log.trace "Returning value for ${location.mode}: $result ${timeUnit}"
            } else {
                log.warn "No specific timeout set for mode: ${location.mode}. Using default."
            }
        } else {
            if (enableTrace) log.trace "Using default timeout: $result ${timeUnit}"
        }
    } catch (Exception e) {
        log.error "Error in getTimeout: $e"
    }

    if (result == null) {
        result = noMotionTime
        log.warn "Timeout was null. Using default noMotionTime: $result"
    }

    logDebug "getTimeout() returns $result ${timeUnit}"
    return result
}
private Map getColorValue() {
    switch (colorPreset) {
        case 'Soft White':
            return [colorTemperature: 2700, level: 100]
        case 'Warm White':
            return [colorTemperature: 3000, level: 100]
        case 'Cool White':
            return [colorTemperature: 4000, level: 100]
        case 'Daylight':
            return [colorTemperature: 6500, level: 100]
        case 'Red':
            return [hue: 0, saturation: 100, level: 100]
        case 'Green':
            return [hue: 120, saturation: 100, level: 100]
        case 'Blue':
            return [hue: 240, saturation: 100, level: 100]
        case 'Yellow':
            return [hue: 60, saturation: 100, level: 100]
        case 'Purple':
            return [hue: 280, saturation: 100, level: 100]
        case 'Pink':
            return [hue: 300, saturation: 100, level: 100]
        case 'Custom':
            return [colorTemperature: customColorTemperature, level: 100]
        default:
            return [colorTemperature: 6500, level: 100]  // Default to Daylight
    }
}
private int getCurrentDimLevelPerMode() {
    if (useDim) {
        if (useModeSpecificDimming) {
            def currentMode = location.mode
            // Find the mode object that matches current mode name
            def modeObject = location.modes.find { it.name == currentMode }
            if (modeObject) {
                def modeSpecificLevel = settings["dimLevel_${modeObject.id}"]
                if (modeSpecificLevel != null) {
                    logDebug "Using mode-specific dim level for ${currentMode}: ${modeSpecificLevel}"
                    return modeSpecificLevel.toInteger()
                } else {
                    logDebug "No specific dim level found for mode ${currentMode}, using default"
                }
            } else {
                logWarn "Could not find mode object for ${currentMode}"
            }
        }
        logDebug "Using default dim level: ${defaultDimLevel}"
        return defaultDimLevel.toInteger()
    }
    return 100  // If dimming is not used, return full brightness
}
private String getModeSpecificDimSettingKey() {
    // Helper method to get the current mode's specific dimming level setting key
    def currentMode = location.mode
    def modeObject = location.modes.find { it.name == currentMode }
    return modeObject ? "dimLevel_${modeObject.id}" : null
}
def scheduleOff() {
    if (!Active()) {
        controlLights('turn off')
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
    log.trace 'Trace logging enabled. Will automatically disable in 30 minutes.'
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
private void logError(String message) {
    log.error(message)
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
        log.trace 'Trace logging enabled. Will automatically disable in 30 minutes.'
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
def getDeviceUrl(deviceId, displayName){
    return "<a href='http://${location.hub.localIP}/device/edit/${deviceId}' target='_blank'>${displayName}</a>"
}
def formatText(title, textColor, bckgColor){
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}