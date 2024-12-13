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
* disabled=ts1434
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
        }
        section('App Control') {
            input 'update', 'button', title: 'UPDATE'
            input 'run', 'button', title: 'RUN'
            input 'reset', 'button', title: 'Reset States'

        }
        section('Logging') {
            input 'enableDebug', 'bool', title: 'Enable debug logging', defaultValue: false, submitOnChange: true
            input 'enableTrace', 'bool', title: 'Enable trace logging', defaultValue: false, submitOnChange: true
            input 'enableWarn', 'bool', title: 'Enable warn logging', defaultValue: false, submitOnChange: true
            input 'enableInfo', 'bool', title: 'Enable info logging', defaultValue: true, submitOnChange: true
        }
    }
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
            logDebug "Subscribed to ${pauseButtonAction} events for button: ${button.displayName}"
        }
    }

    // schedule('0 * * * * ?', master)
    // schedule the first run:
    scheduleNextRun()

    state.paused = false
    state.pauseStart = null

    // Initialize debug and trace timing
    initializeLogging()

    resetStates()
}

def appButtonHandler(btn) {
    switch (btn) {
        case 'pause':
            state.paused = !state.paused
            logDebug "${app.label} is now ${state.paused ? 'PAUSED' : 'RESUMING'}"
            break
        case 'update':
            logDebug 'Update button pressed. Refreshing app configuration.'
            state.paused = false
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
    }
    appLabel()
}
def getAllSwitches() {
    def mainSwitches = switches ?: []
    def resumeSwitches = additionalResumeSwitches ?: []
    def pauseSwitches = additionalPauseSwitches ?: []
    def keepOffSwitchesModes = switchesToKeepOff ?: []
    def keepOffSwitchesAll = switchesToKeepOffAtAllTimes ?: []

    allSwitches = (mainSwitches + resumeSwitches + pauseSwitches + keepOffSwitchesModes + keepOffSwitchesAll).unique { it.id }

    return allSwitches
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
        logDebug "Motion detected, but app is paused. Ignoring."
        return
    }

    logInfo "*******motion event: $evt.displayName is ${evt.value}"

    state.functionalSensors = state.functionalSensors ?: [:]

    state.functionalSensors[evt.displayName] = false

    if (InRestrictedModeOrTime()) return

    def now = now()
    def lastMotionHandled = state.lastMotionHandled ?: 0
    def intervalBetweenEvents = 30 // in seconds
    def someSecondsAgo = now - intervalBetweenEvents * 1000 // N seconds in milliseconds
    
    if (lastMotionHandled > someSecondsAgo) {
        master()
    }


    state.lastMotionHandled = now

}
def buttonHandler(evt) {
    if (evt == null) {
        log.error 'buttonHandler received null event'
        return
    }

    logDebug "Button event: name=${evt.name}, value=${evt.value}, deviceId=${evt.deviceId}"

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


def master() {
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

    if (Active()) {
        controlLights('turn on')
    } else {
        log.warn "...................................... INACTIVE => OFF ................................."
        controlLights('turn off')
    }

    logDebug "---end of master loop. Duration = ${now() - startTime} milliseconds"

    scheduleNextRun()
}


def pauseApp() {
    state.paused = true
    state.pauseStart = now()
    def formattedDuration = formatPauseDuration()
    logDebug "App paused for ${formattedDuration}"

    schedule("0 * * * * ?", checkPauseTimer)// check every minute

    if (controlLightsOnPause) {
        resetStates()
        controlLights(pauseLightAction)
    }
}
def resumeNormalOperation() {
    if (!state.paused) return  // Avoid running if already resumed

    state.paused = false
    state.pauseStart = null
    logDebug 'Resuming normal operation'

    if (controlLightsOnResume) {
        controlLights(resumeLightAction)
    }

    runIn(1, master)  // Schedule master to run shortly after resuming
    appLabel()
}


def controlLights(action) {
    def allSwitches = getAllSwitches()

    switch (action) {
        case 'toggle':
            allSwitches.each {
                sw ->
                if (!shouldKeepSwitchOff(sw.id)) {
                    if (sw.currentValue('switch') == 'on') {
                        sw.off()
                        logDebug "Turned off: ${sw.displayName}"
                    } else {
                        sw.on()
                        logDebug "Turned on: ${sw.displayName}"
                    }
                } else {
                    logDebug "Skipped toggling ${sw.displayName} due to keep-off rule"
                }
            }
            break
        case 'turn on':
            if (exceptions()) {
                log.error "Exception..."
                break
            }

            allSwitches.each { sw ->
                logDebug "switch -> $sw.displayName"

                def mem = memoizeThisSwitch(sw.id)

                logWarn "state.switchState: $state.switchState"

                if (!shouldKeepSwitchOff(sw.id)) {
                    if (sw.currentValue("switch") == "on"){
                        log.trace "${sw.displayName} already on. Skipping"
                        return  // this works like 'continue' in the closure
                    }
                    if (state.switchState[sw.displayName] == "on" && mem) {
                        log.warn "$sw.displayName has already been turned on by this app and is memoized. Skipping."
                    } else {
                        logInfo "turning on $sw.displayName"
                        updateSwitchMem(mem = mem, value = "on", deviceName = sw.displayName)
                        runIn(1, "setSwitch", [data: [deviceId: sw.id, value: "on"], overwrite: false])
                    }
                } else {
                    if (sw.currentValue("switch") == "off"){
                        log.trace "${sw.displayName} already off. Skipping"
                        return  // this works like 'continue' in the closure
                    }
                    if (state.switchState[sw.displayName] != "off" && mem) {
                        runIn(1, "setSwitch", [data: [deviceId: sw.id, value:"off"], overwrite: false])

                        handleLevelAndColors(deviceId=sw.id, mem=mem, cmd="off")
                    }
                    else {
                        log.warn "Skipped shouldKeepSwitchOff off for ${sw.displayName}; manually turned on"
                    }
                }
            }
            break
        case 'turn off':
            allSwitches.each {
                sw ->

                def mem = memoizeThisSwitch(sw.id)

                log.debug "eval case turn off for ${sw.displayName}"

                if (sw.currentValue("switch") == "off"){
                    logTrace "${sw.displayName} already off. Skipping"
                    return  // this works like 'continue' in the closure
                }

                if (state.switchState[sw.displayName] == "off" && mem) {
                    log.warn "$sw.displayName has already been turned off by this app and is memoized. Skipping."
                } else {
                    logTrace "turning off $sw.displayName in 1 second..."
                    updateSwitchMem(mem = mem, value = "off", deviceName = sw.displayName)
                    runIn(1, "setSwitch", [data: [deviceId: sw.id, value:"off"], overwrite: false])

                }
                handleLevelAndColors(deviceId=sw.id, mem=mem, cmd="off")
            }
            break
        default:
            log.warn "Unknown light control action: ${action}"
    }
}

def handleLevelAndColors(deviceId, mem, cmd){
    def sw = getDeviceById_switch(deviceId)
    
    if (useDim) {
        def level = sw.currentValue('level') != currentDimLevelMode && sw.hasCommand('setLevel') ? cmd == "off" ? "" : getCurrentDimLevelPerMode() : null
        def colorValue = useColor && sw.hasCapability('ColorControl') ? cmd == "off" ? "" : getColorValue() : null
        def tempValue =  colorValue ? colorValue.containsKey('colorTemperature') && sw.hasCapability('ColorTemperature') ? cmd == "off" ? "" : colorValue.colorTemperature : null : null

        if (colorValue) {
            if (tempValue) {
                if (state.colorTemperature[sw.displayName] == colorValue.colorTemperature) {
                    log.warn "$sw.displayName color temperature has already been set to ${colorValue.colorTemperature} by this app and is memoized. Skipping."
                } else {
                    updateTempMem(mem=mem, tempValue=colorValue.colorTemperature, deviceName = sw.displayName)
                    runIn(1, "_setColorTemperature", [data: [deviceId: sw.id, value: colorValue.colorTemperature], overwrite: false])
                    
                }
            } 
            else if (colorValue) {
                if (state.color[sw.displayName] == colorValue) {
                    log.warn "$sw.displayName color has already been set to $colorValue by this app and is memoized. Skipping."
                } else {
                    updateColorMem(mem=mem, colorValue=colorValue, deviceName = sw.displayName)
                    runIn(1, "_setColor", [data: [deviceId: sw.id, value: colorValue], overwrite: false])

                }
            }
        }
        if (level) {
            if (state.dimLevel[sw.displayName] == level && consistentState) {
                log.warn "$sw.displayName level already been set to ${level}% by this app. Skipping."
            }
            else {
                updateLevelMem(mem=mem, value=level, deviceName=sw.displayName)
                runIn(1, "_setLevel", [data: [deviceId: sw.id, value: level], overwrite: false])
                
            }
        }
    }
}

def setSwitch(data) {
    if (data && data.deviceId) {
        def device = getDeviceById_switch(data.deviceId)
        logDebug "device is $device"
        if (device) {
            device."${data.value}"()
            logInfo "Successfully turned ${data.value} ${device.displayName}"
        } else {
            logError "Device with ID ${data.deviceId} not found."
        }
    } else {
        logError "No deviceId provided to turnOnSwitch."
    }
}
def _setColorTemperature(data){
    if (data && data.deviceId && data.value) {
        def device = getDeviceById_switch(data.deviceId)
        if (device) {
            
            device.setColorTemperature(data.value)
            logInfo "Successfully set ${device.displayName} color temperature to ${data.value}"
        } else {
            logError "Device with ID ${data.deviceId} not found."
        }
    } else {
        logError "No deviceId provided to _setColorTemperature."
    }
}
def _setColor(data){
    if (data && data.deviceId && data.value) {
        def device = getDeviceById_switch(data.deviceId)
        if (device) {
            
            device.setColorTemperature(data.value)
            logInfo "Successfully set ${device.displayName} color to ${data.value}"
        } else {
            logError "Device with ID ${data.deviceId} not found."
        }
    } else {
        logError "No deviceId provided to _setColor."
    }
}
def _setLevel(data){
    if (data && data.deviceId && data.value) {
        def device = getDeviceById_switch(data.deviceId)
        if (device) {
            device.setLevel(data.value)
            logInfo "Successfully set ${device.displayName} level to ${data.value}"
        } else {
            logError "Device with ID ${data.deviceId} not found."
        }
    } else {
        logError "No deviceId provided to _setLevel."
    }
}

def memoizeThisSwitch(deviceId){
    def memThisOne = memoizeSwitchesThatAreKeptOffAtAllTimes && switchesToKeepOffAtAllTimes?.any{ it.id == deviceId }
    result = memoize || memThisOne

    logDebug "deviceId = $deviceId"
    def device = getDeviceById_switch(deviceId)
    logTrace "memoizeThisSwitch returns $result for ${device.displayName}"
    return result
}

// these are all values as set BY THE APP
def updateSwitchMem(mem, value, deviceName){

    logDebug "----------- mem = $mem"

    if (mem) {
        logTrace "memoizing state '${value}' for ${deviceName}"
        state.switchState[deviceName] = value
    }
    else {
        logWarn "NOT memoizing state '${value}' for ${deviceName}"
    }
}
def updateColorMem(mem, value, deviceName){
    if (mem) {
        state.color[deviceName] = value
    }
}
def updateTempMem(mem, value, deviceName){
    if (mem) {
        state.colorTemperature[deviceName] = value
    }
}
def updateLevelMem(mem, value, deviceName){
    if (mem) {
        state.dimLevel[deviceName] = value
    }
}

def resetStates(){

    def now = now()

    // Initialize memoization meant to prevent reseting the color/colorTemperature, 
    // to allow other systems to use colors as needed (e.g. for water leak, smoke, gaz, fire alerts, etc.)
    state.color = [:]                          // initialize memoization of colors
    state.colorTemperature = [:]    // initialize memoization of color temperatures

    // same logic, but for comfort reasons: allow user to manually set a level value. 
    state.dimLevel = [:] 

    // same logic as above, but for switch's 'on' and 'off' states. 
    state.switchState = [:]

    state.paused = state.paused ?: false
    state.pauseStart = state.pauseStart ?: now

    state.functionalSensors = state.functionalSensors ?: [:]

    state.lastMotionHandled = state.lastMotionHandled ?: now
    
    state.pauseStart = state.pauseStart ?: now 

    state.pauseDueToButtonEvent = state.pauseDueToButtonEvent ?: false

    state.EnableDebugTime = state.EnableDebugTime ?: now
    state.EnableTraceTime = state.EnableTraceTime ?: now
    state.EnableInfoTime = state.EnableInfoTime ?: now
    state.EnableWarnTime = state.EnableWarnTime ?: now 

    state.lastCheckTimer = state.lastCheckTimer ?: now

    log.trace "---------- states reset ok ----------"
        
}
// def getDeviceById(deviceId) {
//     logDebug "Device ID types - passed in: ${deviceId.class}, device ids in list: ${allSwitches.collect{it.id.class}}"

//     def allSwitches = getAllSwitches()
//     logDebug "allSwitches: $allSwitches"
//     def device = allSwitches.find { it.id == deviceId }
//     logDebug "device id $deviceId is $device"
//     return device
// }

def getDeviceById_switch(deviceId) {
    def device = switches.find { it.id == deviceId }
    if (!device) {
        log.warn "Device with ID ${deviceId} not found in switches."
    }
    return device
}

// Add similar methods for other device types if needed:
def getDeviceById_sensor(deviceId) {
    def device = motionSensors.find { it.id == deviceId }
    if (!device) {
        log.warn "Device with ID ${deviceId} not found in motion sensors."
    }
    return device
}

// TODO
def cleanUpSwitchState() {
    cleanUpStateForDeviceType('switchState', getAllSwitches(), 'switch')
}

def cleanUpStateForDeviceType(stateMapKey, deviceList, deviceType) {
    def originalState = state."${stateMapKey}" ?: [:]
    def validDevices = deviceList.collect { it.id }
    
    // Retain only valid keys in the state map
    state."${stateMapKey}" = originalState.findAll { key, value ->
        def device = getDeviceById_switch(key)  // Update this to the relevant device lookup
        if (validDevices.contains(key)) {
            true
        } else {
            log.warn "${deviceType.capitalize()} with ID ${key} is no longer valid. Removing from state.${stateMapKey}."
            false
        }
    }
}

def formatPauseDuration() {
    if (pauseDurationUnit == 'Hours') {
        return "${pauseDuration} hour${pauseDuration == 1 ? '' : 's'}"
    } else {
        return "${pauseDuration} minute${pauseDuration == 1 ? '' : 's'}"
    }
}
def getPauseDurationMillis() {
    return pauseDurationUnit == 'Hours' ? pauseDuration * 3600 : pauseDuration * 60
}
def Active() {

    // return false

    logDebug "Checking if motion is active. Motion sensors: $motionSensors"

    def functionalSensors = getFunctionalSensors()
    logDebug "Functional sensors: ${functionalSensors.collect { it.displayName }}"

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
def getFunctionalSensors() {
    def recentPeriod = new Date(now() - (24 * 60 * 60 * 1000)) // 24 hours ago

    state.functionalSensors = state.functionalSensors ?: [:]  // if the device is a hub mesh device, motion events history might not show
    
    def functionalSensors = motionSensors.findAll {
        sensor ->

            // Get events for this sensor in last 24 hours
            def events = sensor.eventsSince(recentPeriod)

        // Check if there are any 'inactive' motion events
        def hasMotionEvents = events.any {
            event ->
                event.name == 'motion' && event.value == 'inactive'
        }

        if (!hasMotionEvents) {
            def m = "${app.label}: ${sensor} appears UNRESPONSIVE -- <a href='http://${location.hub.localIP}/device/edit/${sensor.id}' target='_blank'>Manage Device</a>"
            log.warn formatText(m, "black", "#E0E0E0")
            state.functionalSensors[sensor.displayName] = false

        } else {
            state.functionalSensors[sensor.displayName] = true
        }

        // Return true if sensor has events (it's functioning as expected)
        hasMotionEvents || state.functionalSensors[sensor.displayName]
    }

    log.debug "functionalSensors: $functionalSensors"

    if (functionalSensors.size() == 0) {
        def m = "ALL MOTION SENSORS ARE UNRESPONSIVE IN ${app.label}"
        log.warn formatText(m, "yellow", "red")
    }

    return functionalSensors
}
def scheduleNextRun() {
    def timeout = getTimeout()
    def nextRun = timeout * (timeUnit == 'minutes' ? 60 : 1)
    runIn(nextRun, master)
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

    if (enableTrace) log.trace "timeModes: $timeModes"
    if (enableTrace) log.trace "Current mode: ${location.mode}"

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
def checkPauseTimer() {
    logDebug ('check pause')
    def pauseMillis = getPauseDurationMillis()
    if (state.pauseDueToButtonEvent && now() - state.buttonPausedTime > pauseMillis) {
        state.paused = false
        state.pauseDueToButtonEvent = false
        log.warn 'PAUSE BUTTON TIME IS UP! Resuming operations'
        unschedule(checkPauseTimer)
    } else if (state.pauseDueToButtonEvent) {
        logDebug ('APP PAUSED BY BUTTON EVENT')
    }
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
            def modeSpecificLevel = settings["dimLevel_${currentMode}"]
            if (modeSpecificLevel != null) {
                logDebug "Using mode-specific dim level for ${currentMode}: ${modeSpecificLevel}"
                return modeSpecificLevel
            }
        }
        logDebug "Using default dim level: ${defaultDimLevel}"
        return defaultDimLevel
    }
    return 100  // If dimming is not used, return full brightness
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
    runIn(1800, disableTraceLog)
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
        if (enableTrace && state.EnableTraceTime != null && now - state.EnableTraceTime > 1800000) {
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
        runIn(1800, disableTraceLog)
    }
    if (enableInfo) {
        logInfo('Description logging enabled.')
    }
    if (enableWarn) {
        logInfo('Warn logging enabled.')
    }
}
def formatText(title, textColor, bckgColor){
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}