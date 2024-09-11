/* groovylint-disable UnnecessaryGString */
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

        section('General Settings') {
            input 'appName', 'text', title: 'Name this app instance', required: false, submitOnChange: true
            appLabel()
        }
        section('Motion Sensors') {
            input 'motionSensors', 'capability.motionSensor', title: 'Select motion sensors', multiple: true, required: true
        }
        section('Switches') {
            input 'switches', 'capability.switch', title: 'Control these switches', multiple: true, required: true, submitOnChange: true
        
            if (switches) {
                def hasDimmers = switches.any { it.hasCapability('SwitchLevel') }
                def hasColorControls = switches.any { it.hasCapability('ColorControl') || it.hasCapability('ColorTemperature') }
        
                if (hasDimmers) {
                    input 'useDim', 'bool', title: 'Use dimming capabilities', defaultValue: false, submitOnChange: true
                    
                    if (useDim) {
                        input 'defaultDimLevel', 'number', title: 'Default dim level (0-100)', range: '0..100', defaultValue: 50, required: true
                        input 'useModeSpecificDimming', 'bool', title: 'Use mode-specific dimming levels', defaultValue: false, submitOnChange: true
                        
                        if (useModeSpecificDimming) {
                            location.modes.each { mode ->
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
        }
        section('App Control') {
            input 'update', 'button', title: 'UPDATE'
            input 'run', 'button', title: 'RUN'
        }
        section('Logging') {
            input 'enableDebug', 'bool', title: 'Enable debug logging', defaultValue: false
            input 'enableTrace', 'bool', title: 'Enable trace logging', defaultValue: false
            input 'description', 'bool', title: 'Enable info logging', defaultValue: true
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    log.debug 'Initializing'
    subscribe(motionSensors, 'motion', motionHandler)
    subscribe(switches, 'switch', switchHandler)
    if (contacts) {
        subscribe(contacts, 'contact', contactHandler)
    }
    if (useIlluminance) {
        subscribe(illuminanceSensor, 'illuminance', illuminanceHandler)
    }
    subscribe(location, 'mode', modeChangeHandler)


    if (watchdog) {
        // Subscribe to hub events
        subscribe(location, 'systemStart', hubEventHandler)
        subscribe(location, 'zigbeeStatus', hubEventHandler)
        subscribe(location, 'zwaveStatus', hubEventHandler)
        subscribe(location, 'hubHealthStatus', hubEventHandler)
        subscribe(location, 'internetStatus', hubEventHandler)
        subscribe(location, 'cloudStatus', hubEventHandler)
        subscribe(location, 'alertStatus', hubEventHandler)
        subscribe(location, 'cpuUsage', hubEventHandler)
        subscribe(location, 'memoryUsage', hubEventHandler)
    }

    if (pauseButtons) {
        pauseButtons.each {
            button ->
                subscribe(button, pauseButtonAction, buttonHandler)
            log.debug "Subscribed to ${pauseButtonAction} events for button: ${button.displayName}"
        }
    }

    // schedule('0 * * * * ?', master)
    // schedule the first run:
    scheduleNextRun()

    state.paused = false
    state.pauseStart = null

    // Initialize debug and trace timing
    initializeLogging()
}

def master() {
    def startTime = now()
    if (enableDebug) log.debug 'master start'

    check_logs_timer()
    appLabel()

    if (InRestrictedModeOrTime() || state.paused) {
        scheduleNextRun()
        return
    }

    if (useIlluminance && illuminanceSensor.currentValue('illuminance') > illuminanceThreshold) {
        off()
        if (enableDebug) log.debug "Turned off lights due to high illuminance"
        scheduleNextRun()
        return
    }

    if (Active()) {
        on()
    } else {
        off()
    }

    if (enableDebug) log.debug "---end of master loop. Duration = ${now() - startTime} milliseconds"
    
    scheduleNextRun()
}

def handlePresenceRestrictions() {
    if (managePresence) {
        boolean presenceRestriction = presenceRestricted ? presenceRestricted.any{ it -> it.currentValue('presence') == 'present' } : false
        boolean absenceRestriction = absenceRestricted ? absenceRestricted.every{ it.currentValue('presence') == 'not present' } : false

        if (presenceRestriction || absenceRestriction) {
            log.info 'App is paused due to presence/absence restrictions'
            state.pausedByPresenceSensor = true
            state.paused = true
        } else if (state.pausedByPresenceSensor) {
            state.pausedByPresenceSensor = false
            state.paused = false
            updated()
        }
    }
}

def pauseApp() {
    state.paused = true
    state.pauseStart = now()
    def formattedDuration = formatPauseDuration()
    log.debug "App paused for ${formattedDuration}"

    if (controlLightsOnPause) {
        controlLights(pauseLightAction, switches, additionalPauseSwitches)
    }
}

def resumeNormalOperation() {
    state.paused = false
    state.pauseStart = null
    log.debug 'Resuming normal operation'

    if (controlLightsOnResume) {
        def allSwitches = (switches + additionalResumeSwitches).unique { it.id }
        controlLights(resumeLightAction, allSwitches)
    }

    runIn(1, master)  // Schedule master to run shortly after resuming
}

def controlLights(action, mainSwitches, additionalSwitches = []) {
    def allSwitches = (mainSwitches + additionalSwitches).unique { it.id }

    log.debug "Controlling lights: action=${action}, switches=${allSwitches.collect { it.displayName }}"

    switch (action) {
        case 'toggle':
            allSwitches.each { sw ->
                if (!shouldKeepSwitchOff(sw)) {
                    if (sw.currentValue('switch') == 'on') {
                        sw.off()
                        log.debug "Turned off: ${sw.displayName}"
                    } else {
                        sw.on()
                        log.debug "Turned on: ${sw.displayName}"
                    }
                } else {
                    log.debug "Skipped toggling ${sw.displayName} due to keep-off rule"
                }
            }
            break
        case 'turn on':
            allSwitches.each { sw ->
                if (!shouldKeepSwitchOff(sw)) {
                    sw.on()
                    log.debug "Turned on: ${sw.displayName}"
                } else {
                    log.debug "Skipped turning on ${sw.displayName} due to keep-off rule"
                }
            }
            break
        case 'turn off':
            allSwitches.each { sw ->
                sw.off()
                log.debug "Turned off: ${sw.displayName}"
            }
            break
        default:
            log.warn "Unknown light control action: ${action}"
    }
}

def formatPauseDuration() {
    if (pauseDurationUnit == 'Hours') {
        return "${pauseDuration} hour${pauseDuration == 1 ? '' : 's'}"
    } else {
        return "${pauseDuration} minute${pauseDuration == 1 ? '' : 's'}"
    }
}

def startOverride() {
    state.override = true
    state.overrideStart = now()
    log.debug "Override started for ${overrideDelay} hours"
}

def handleOverride() {
    if (allowOverride && state.override) {
        def overrideMillis = overrideDelay * 60 * 60 * 1000 // Override is always in hours
        if (now() - state.overrideStart > overrideMillis) {
            if (enableTrace) log.trace 'END OF OVERRIDE'
            resumeNormalOperation()
        } else {
            def remainingHours = ((overrideMillis - (now() - state.overrideStart)) / (60 * 60 * 1000)).toDouble().round(2)
            if (enableTrace) log.trace "App in override mode. Will resume in ${remainingHours} hours"
        }
    }
}

def getPauseDurationMillis() {
    return pauseDurationUnit == 'Hours' ? pauseDuration * 60 * 60 * 1000 : pauseDuration * 60 * 1000
}

def Active() {
    if (enableDebug) log.debug "Checking if motion is active. Motion sensors: $motionSensors"

    // Check current state first (faster)
    if (motionSensors.any { it.currentValue('motion') == 'active' }) {
        if (enableTrace) log.trace "Motion is currently active"
        return true
    }

    // Only check recent history if necessary
    int timeOut = getTimeout()
    long Dtime = timeUnit == 'minutes' ? timeOut * 60 * 1000 : timeOut * 1000
    def period = new Date(now() - Dtime)

    return motionSensors.any { sensor ->
        sensor.eventsSince(period, [max: 1]).any { it.name == 'motion' && it.value == 'active' }
    }
}


def scheduleNextRun() {
    def timeout = getTimeout()
    def nextRun = timeout * (timeUnit == 'minutes' ? 60 : 1)
    runIn(nextRun, master)
    if (enableDebug) log.debug "Next master() run scheduled in ${nextRun} seconds"
}

def on() {
    logDebug('Entering on() method')
    if (InRestrictedModeOrTime()) {
        logDebug('In restricted mode or time, exiting on() method')
        return
    }

    if (useIlluminance && illuminanceSensor.currentValue('illuminance') > illuminanceThreshold) {
        logDebug("Current illuminance is above threshold. Not turning on lights.")
        return
    }

    def currentDimLevel = getCurrentDimLevel()
    def colorValue = useColor ? getColorValue() : null

    switches.each { sw ->
        if (shouldKeepSwitchOff(sw)) {
            logInfo("${sw.displayName} is kept off due to mode settings")
        } else {
            logInfo("Turning on ${sw.displayName}")
            if (useDim && sw.hasCommand('setLevel')) {
                if (colorValue) {
                    if (colorValue.containsKey('colorTemperature') && sw.hasCapability('ColorTemperature')) {
                        sw.setColorTemperature(colorValue.colorTemperature)
                    } else if (sw.hasCapability('ColorControl')) {
                        sw.setColor(colorValue)
                    }
                }
                sw.setLevel(currentDimLevel)
            } else {
                sw.on()
            }
        }
    }

    logInfo('Finished processing switches')
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

private int getCurrentDimLevel() {
    if (useDim) {
        if (useModeSpecificDimming) {
            def currentMode = location.mode
            def modeSpecificLevel = settings["dimLevel_${currentMode}"]
            if (modeSpecificLevel != null) {
                logDebug("Using mode-specific dim level for ${currentMode}: ${modeSpecificLevel}")
                return modeSpecificLevel
            }
        }
        logDebug("Using default dim level: ${defaultDimLevel}")
        return defaultDimLevel
    }
    return 100  // If dimming is not used, return full brightness
}

def shouldKeepSwitchOff(sw) {
    logDebug("Checking if ${sw.displayName} should be kept off")
    logDebug("keepSomeSwitchesOffInModes: $keepSomeSwitchesOffInModes")
    logDebug("modesForSwitchesOff: $modesForSwitchesOff")
    logDebug("switchesToKeepOff: ${switchesToKeepOff?.collect { it.displayName }}")
    logDebug("Current mode: ${location.mode}")


    if (keepSomeSwitchesOffInModes &&
        modesForSwitchesOff?.contains(location.mode) &&
        switchesToKeepOff?.find { it.id == sw.id }) {

        logInfo("Keeping ${sw.displayName} off due to current mode: ${location.mode}")
        return true
}

    logDebug("${sw.displayName} can be turned on")
    return false
}

def off() {
    if (enableDebug) log.debug 'off function'

    def anyOn = switches.any{ it -> it.currentValue('switch') == 'on' }

    state.mainHandlerEventTime = now()
    if (anyOn) {
        if (!state.test && (state.switches == 'on' || !allowOverride)) {
            switchesOff()
            if (description) log.info "turning off ${switches.join(', ')} 59989e"
            state.switches = 'off'
        } else if (allowOverride && !state.test && state.switches == 'off' && switches.any{ it -> it.currentValue('switch') == 'on' }) {
            if (description) log.info 'lights were turned on manually, app in override mode'
        } else {
            if (enableDebug) log.debug "$switches would have turned off - test succeeded!"
        }
    } else {
        if (enableDebug) log.debug "$switches already off"
    }

}

def appLabel() {
    def baseName = settings?.appName ?: 'Advanced Motion Lighting Management V2'
    def pausedSuffix = '(Paused)'
    
    log.debug "Current app label: ${app.label}"
    log.debug "Base name: ${baseName}"
    log.debug "state.paused: ${state.paused}"
    
    def newLabel = baseName
    
    if (state.paused) {
        newLabel += " ${pausedSuffix}"
    }
    
    if (app.label != newLabel) {
        app.updateLabel(newLabel)
        log.debug "Updated app label to: ${newLabel}"
    } else {
        log.debug 'App label unchanged'
    }
}

def restoreLabel() {
    def currentLabel = app.label ?: 'Advanced Motion Lighting Management V2'
    def newLabel = currentLabel.replace("<font color = 'red'>(Paused)</font>", '')
        .replace(' (Paused) ', '')
        .replace('Paused', '')
        .replace('(', '')
        .replace(')', '')
        .trim()

    if (newLabel != currentLabel) {
        app.updateLabel(newLabel)
        if (enableDebug) log.debug "App label restored to: $newLabel"
    }
}




def InRestrictedModeOrTime() {
    boolean inRestrictedTime = restrictedTime()
    boolean inRestrictedMode = location.mode in restrictedModes
    if (inRestrictedMode || inRestrictedTime) {
        if (description) log.info "location ${inRestrictedMode ? ' in restricted mode' : inRestrictedTime ? 'outside of time window' : 'ERROR'}, doing nothing"
        return true
    }
    return false
}

def restrictedTime() {
    result = false
    if (!restrictedTimeSlots) return false

    if (state.timeSlots) {
        int s = state.timeSlots
        for (int i = 0; i < s; i++) {
            def starting = settings["restrictedTimeStart${i}"]            
            def ending = settings["restrictedTimeEnd${i}"]
            def currTime = now()
            def start = timeToday(starting, location.timeZone).time
            def end = timeToday(ending, location.timeZone).time
            if (enableDebug) log.debug "start = $start"
            if (enableDebug) log.debug "end = $end"
            if (enableDebug) log.debug "start < end ? ${start < end} || start > end ${start > end}"
            if (enableDebug) log.debug "currTime <= end ${currTime <= end} || currTime >= start ${currTime >= start}"

            result = start < end ? currTime >= start && currTime <= end : currTime <= end || currTime >= start
            if (result) break
        }
    }
    if (enableDebug) log.debug "restricted time returns $result"
    return result
}

def getTimeout() {
    def result = noMotionTime // default

    if (enableTrace) log.trace "timeModes: $timeModes"
    if (enableTrace) log.trace "Current mode: ${location.mode}"

    try {
        if (absenceTimeoutSensor) {
            def listOfAbsents = absenceTimeoutSensor.findAll{ it.currentValue('presence') == 'not present' }
            boolean absenceRestriction = absenceTimeoutSensor ? listOfAbsents.size() == absenceTimeoutSensor.size() : false
            if (absenceRestriction) {
                if (description) log.info "$absenceTimeoutSensor not present, timeout returns $absenceTimeout"
                return absenceTimeout
            }
        }

        if (timeWithMode && timeModes.contains(location.mode)) {
            def modeTimeout = settings["noMotionTime_${location.mode}"]
            if (enableDebug) log.debug "modeTimeout: $modeTimeout"
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

    if (enableDebug) log.debug "getTimeout() returns $result ${timeUnit}"
    return result
}

def checkPauseButton() {
    if (enableDebug) log.debug('check pause')
    def pauseMillis = getPauseDurationMillis()
    if (state.pauseDueToButtonEvent && now() - state.buttonPausedTime > pauseMillis) {
        state.paused = false
        state.pauseDueToButtonEvent = false
        log.warn 'PAUSE BUTTON TIME IS UP! Resuming operations'
        unschedule(checkPauseButton)
    } else if (state.pauseDueToButtonEvent) {
        if (enableDebug) log.debug('APP PAUSED BY BUTTON EVENT')
    }
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
                log.info 'Zigbee network is back online'
            }
            break
        case 'zwaveStatus':
            if (evt.value != 'online') {
                log.error 'Z-Wave network is offline or experiencing issues'
            } else {
                log.info 'Z-Wave network is back online'
            }
            break
        case 'hubHealthStatus':
            if (evt.value != 'online') {
                log.error "Hub health status: ${evt.value}"
            } else {
                log.info 'Hub health status is back to normal'
            }
            break
        case 'internetStatus':
            if (evt.value != 'full') {
                log.warn "Internet connectivity issue: ${evt.value}"
            } else {
                log.info 'Internet connectivity restored'
            }
            break
        case 'cloudStatus':
            if (evt.value != 'connected') {
                log.warn "Cloud connection issue: ${evt.value}"
            } else {
                log.info 'Cloud connection restored'
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
            log.debug "Unhandled hub event: ${evt.name} = ${evt.value}"
    }


}

def motionHandler(evt) {
    if (state.paused) return

    if (description) log.info "${evt.name}: $evt.device is $evt.value"

    if (InRestrictedModeOrTime()) return

    def now = now()
    def lastMotionHandled = state.lastMotionHandled ?: 0
    def thirtySecondsAgo = now - 30000 // 30 seconds in milliseconds

    if (lastMotionHandled > thirtySecondsAgo) {
        if (enableDebug) log.debug "Motion event received within 30 seconds of last handled event. Skipping processing."
        return
    }

    state.lastMotionHandled = now

    if (evt.value == 'active') {
        if (switches.any { it.currentValue('switch') == 'on' }) {
            if (enableDebug) log.debug "Lights are already on. Skipping 'on' action."
            return
        }
        unschedule(master)  // Unschedule the next run
        master()  // Run master immediately
    } else {
        // For 'inactive' events, we'll let the scheduled master() handle it
        // This avoids running master() too frequently on motion stopping
    }
}


def buttonHandler(evt) {
    if (evt == null) {
        log.error 'buttonHandler received null event'
        return
    }

    log.debug "Button event: name=${evt.name}, value=${evt.value}, deviceId=${evt.deviceId}"

    if (evt.name == pauseButtonAction) {
        if (state.paused) {
            resumeNormalOperation()
        } else {
            pauseApp()
        }
    } else {
        log.warn "Unexpected button action: ${evt.name}. Expected: ${pauseButtonAction}"
    }
}

def switchHandler(evt) {
    if (state.paused) return

    if (enableDebug) {
        def delay = state.mainHandlerEventTime ? now() - state.mainHandlerEventTime : 0
        log.debug "$evt.device is $evt.value (delay between cmd and this event = ${delay} milliseconds)"
    }

    if (allowOverride == true) {
        if ((evt.value == 'on' && state.switches == 'off') || (evt.value == 'off' && state.switches == 'on')) {
            log.warn "OVERRIDE TRIGGERED for $overrideDelay ${overrideDelay > 1 ? 'hours' : 'hour'} (evt handler)"
            state.overrideStart = now()
            state.override = true
        } else if (evt.value == state.switches) {
            log.warn 'END OF OVERRIDE (evt handler)'
            state.override = false
        }
    } else {
        state.override = false // make sure it stays false after user may have disabled this feature
    }

    state.thisIsAMotionEvent = false
    state.cmdFromApp = false
}

def contactHandler(evt) {
    if (state.paused) return

    if (InRestrictedModeOrTime()) return

    if (evt.value == 'open') {
        if (switches.any{ it -> it.currentValue('switch') == 'off' }) {
            switches.on()
            if (enableDebug) log.debug('switches on 5dfrj')
    }

        if (powerOnWithContactOnly) {
            if (location.mode in noTurnOnMode && !ignoreModes) {
                if (description) log.info "$powerSwitch is not being turned on because location is in $noTurnOnMode modes (${location.mode})"
                return
            }
            if (switchOnWithContactOnly) {
                if (powerSwitch?.currentValue('switch') == 'off') {
                    if (enableDebug) log.debug('switches on 34ghj4')
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
        if (enableDebug) log.debug "Illuminance change within 1 minute of last handled event. Skipping processing."
        return
    }

    state.lastIlluminanceHandled = now

    if (description) log.info "$evt.name is now $evt.value"
    
    boolean daytime = evt.value.toInteger() > illuminanceThreshold
    if (daytime != state.lastDaytimeState) {
        state.lastDaytimeState = daytime
        unschedule(master)
        master()
    }
}

def modeChangeHandler(evt) {
    if (enableDebug) log.debug("$evt.name is now in $evt.value mode")
    unschedule(master)
    master()
}

def switchesOff() {
    switches.off()
}

def powerSwitchOff() {
    if (powerSwitch?.currentValue('switch') == 'on') powerSwitch?.off()
}

def specificSwitch(boolean exception) {
    switches.each {
        device ->
            boolean thisIsTheSwitchToKeepOff = device.displayName in onlyThoseSwitchesStay_Off
        if (enableDebug) log.debug "onlyThoseSwitchesStay_Off = $onlyThoseSwitchesStay_Off | $device is to be kept off = $thisIsTheSwitchToKeepOff"

        if (exception) {
            if (thisIsTheSwitchToKeepOff) {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability('SwitchLevel')) {
                    if (enableDebug) log.debug "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                } else {
                    if (device.currentValue('switch') == 'on' && (state.switches == 'on' || !allowOverride)) {
                        device.off()
                        state.switches = 'off'
                    } else if (state.switches == 'off' && allowOverride) {
                        if (description) log.info 'lights were turned on manually, app in override mode'
                    }
                }
            }
        } else {
            if (!thisIsTheSwitchToKeepOff) {
                if (!switchOnWithContactOnly) {
                    boolean isOff = device.currentValue('switch') == 'off'
                    if (isOff && (state.switches == 'off' || !allowOverride)) {
                        device.on()
                        state.switches = 'on'
                        if (enableDebug) log.debug "${device} stays on at user's request"
                    } else if (allowOverride && state.switches == 'on' && isOff) {
                        if (description) log.info 'lights were turned on manually, app in override mode'
                    }
                }
            } else {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && device.hasCapability('SwitchLevel')) {
                    if (enableDebug) log.debug "not turning $device off because it's a dimmer and it's been requested to stay at level $keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes"
                } else if (device.currentValue('switch') != 'off' && (state.switches == 'on' || !allowOverride)) {
                    device.off()
                    state.switches = 'off'
                    if (enableDebug) log.debug "${device} is turned off at user's request"
                } else if (allowOverride && state.switches == 'off') {
                    if (description) log.info 'lights were turned on manually, app in override mode'
                }
            }
        }
    }
}

def dim() {
    if (keepSomeSwitches_Off_InCertainModes && location.mode in modeSpecificSwitches_stay_Off && !keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) {
        log.warn 'not dimming because app is in specific switches mode'
    } else {
        boolean closed = !contactsAreOpen()
        def switchesWithDimCap = switches.findAll{ it.hasCapability('SwitchLevel') }
        if (enableDebug) log.debug "list of devices with dimming capability = $switchesWithDimCap"

        switchesWithDimCap.each {
            dimmer ->
            if (closed) {
                if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches_stay_Off) {
                    dimmer.setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes)
                } else {
                    def level = dimValClosed < 10 ? 10 : dimValClosed
                    dimmer.setLevel(level)
                    if (enableDebug) log.debug("${dimmer} set to $level 9zaeth")
                }
            } else {
                if (!contactModeOk()) {
                    if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes && location.mode in modeSpecificSwitches_stay_Off) {
                        dimmer.setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes)
                    } else {
                        def level = dimValClosed < 10 ? 10 : dimValClosed
                        dimmer.setLevel(level)
                        if (enableDebug) log.debug("${dimmer} set to $level 78fr")
                    }
                } else {
                    if (keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes) {
                        dimmer.setLevel(keepDimmerAtValueWhenSupposedToBeOffInSpecificSwitchOffModes)
                    } else {
                        def level = dimValOpen < 10 ? 10 : dimValOpen
                        dimmer.setLevel(level)
                        if (enableDebug) log.debug("${dimmer} set to $level 54fre")
                    }
                }
            }
        }
    }
}

def scheduleOff() {
    if (!Active()) {
        off()
    }
}

def contactsAreOpen() {
    def openList = contacts?.findAll{ it.currentValue('contact') == 'open' }
    openList = openList != null ? openList : []
    if (enableDebug) log.debug("Currently Open Contacts $openList")
    return openList.size() > 0
}

def contactModeOk() {
    if (contacts && contactModes) {
        return location.mode in modesForContacts
    }
    return true
}





def checkLuxCancel() {
    long dt = state.LuxCancelDeltaTime * 1000
    if (state.LuxCanceledbyButtonEvt && now() - state.LuxCanceledbyButtonEvtTime > dt) {
        log.warn '(periodic schedule version) LUX PAUSE TIME IS UP! Resuming operations (runIn method seems to have failed)'
        state.LuxCanceledbyButtonEvt = false
        unschedule(resetLuxCancel)
    } else if (state.LuxCanceledbyButtonEvt) {
        if (description) log.info 'LUX SENSITIVITY PAUSED BY BUTTON EVENT'
    }
}

def resetLuxCancel() {
    state.LuxCanceledbyButtonEvt = false
}

def appButtonHandler(btn) {
    switch (btn) {
        case 'pause':
            state.paused = !state.paused
            log.debug "${app.label} is now ${state.paused ? 'PAUSED' : 'RESUMING'}"
            break
        case 'update':
            log.debug 'Update button pressed. Refreshing app configuration.'
            state.paused = false
            updated()
            break
        case 'run':
            if (!state.paused) {
                log.debug 'Run button pressed. Executing master() function.'
                master()
            } else {
                log.warn 'App is paused. Cannot run master() function.'
            }
            break
    }
    appLabel()
}




def enableDebugLog() {
    state.EnableDebugTime = now()
    app.updateSetting('enableDebug', [type: 'bool', value: true])
    log.debug 'Debug logging enabled. Will automatically disable in 30 minutes.'
    runIn(1800, disableDebugLog)
}

def disableDebugLog() {
    state.EnableDebugTime = null
    app.updateSetting('enableDebug', [type: 'bool', value: false])
    log.info 'Debug logging disabled.'
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
    log.info 'Trace logging disabled.'
}

def enableDescriptionLog() {
    state.EnableDescriptionTime = now()
    app.updateSetting('description', [type: 'bool', value: true])
    log.info 'Description logging enabled.'
}

def disableDescriptionLog() {
    state.EnableDescriptionTime = null
    app.updateSetting('description', [type: 'bool', value: false])
    log.info 'Description logging disabled.'
}

def check_logs_timer() {
    long now = now()
    if (now - state.lastCheckTimer >= 60000) {  // Check every minute
        if (enableDebug && state.EnableDebugTime != null && now - state.EnableDebugTime > 1800000) {
            disableDebugLog()
        }
        if (enableTrace && state.EnableTraceTime != null && now - state.EnableTraceTime > 1800000) {
            disableTraceLog()
        }
        // We don't automatically disable description logging
        state.lastCheckTimer = now
    }
}


private void logDebug(String message) {
    if (enableDebug) {
        log.debug(message)
    }
}

private void logInfo(String message) {
    if (description) {
        log.info(message)
    }
}

private void logWarn(String message) {
    log.warn(message)
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
    state.EnableDescriptionTime = now()
    state.lastCheckTimer = now()

    if (enableDebug) {
        log.debug 'Debug logging enabled. Will automatically disable in 30 minutes.'
        runIn(1800, disableDebugLog)
    }
    if (enableTrace) {
        log.trace 'Trace logging enabled. Will automatically disable in 30 minutes.'
        runIn(1800, disableTraceLog)
    }
    if (description) {
        log.info 'Description logging enabled.'
    }
}
