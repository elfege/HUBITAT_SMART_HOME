import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput

@Field static remoteResponded = true

//###############################################################################
//#                               DEFINITION                                     
//###############################################################################

definition(
    name: "CSP Watchdog",
    namespace: "elfege",
    author: "ELFEGE",
    description: "Monitor hub health and reboot if necessary. Can also monitor a remote hub.",
    category: "Utility",
    iconUrl: "https://www.philonyc.com/assets/penrose.jpg",
    iconX2Url: "https://www.philonyc.com/assets/penrose.jpg",
    oauth: true
)

//###############################################################################
//#                               PREFERENCES                                    
//###############################################################################

preferences {
    page(name: "mainPage")
    page(name: "remotePage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "CSP Watchdog Configuration", install: true, uninstall: true) {
        section("Local Hub Monitoring") {
            input "motionSensors", "capability.motionSensor", title: "Motion sensors to trigger checks", multiple: true, required: true, submitOnChange: true
            if (motionSensors) {
                input "testSwitch", "capability.switch", title: "Switch for response time test", required: false, submitOnChange: true
                input "trueSwitches", "capability.switch", title: "Physical Z-wave switches for mesh test", multiple: true, required: false, submitOnChange: true
                input "rebootThreshold", "number", title: "Number of failed tests before reboot", defaultValue: 3, range: "1..10", submitOnChange: true
            }
        }
        section("Remote Hub Monitoring") {
            input "enableRemote", "bool", title: "Enable remote hub monitoring?", defaultValue: false, submitOnChange: true
            if (enableRemote) {
                href "remotePage", title: "Configure Remote Hub", description: "Tap to configure"
            }
        }
        section("Notifications (recommended)") {
            input "notificationDevices", "capability.notification", title: "Select notification devices", multiple: true, required: false
            input "speakerDevices", "capability.speechSynthesis", title: "Select speaker devices (optional)", multiple: true, required: false
        }
        section("Additional Remote Hub Reboots") {
            input "additionalHubIps", "text", title: "Remote Hub IPs (comma-separated)", required: false, submitOnChange: true
            paragraph "Enter IP addresses of additional Hubitat hubs you want to be able to reboot. Separate multiple IPs with commas."
            if (additionalHubIps) {
                def ipList = additionalHubIps.tokenize(',')*.trim()
                ipList.each { ip ->
                    input "rebootAdditionalHub_${ip.replaceAll('\\.', '_')}", "button", title: "Reboot ${ip}", submitOnChange: true
                }
            }
            
        }
        section("Actions") {
            input "testNow", "button", title: "Run Health Check Now"
            if (state.installed) {
                input "update", "button", title: "Update"
            } else {
                input "install", "button", title: "Install"
            }
            input "devmode", "bool", title: "Test the reboot buttons without rebooting", submitOnChange:true
    
            // Local hub reboot controls
            if (!no_reboot) {
                input "rebootLocalHub", "button", title: "Reboot Local Hub", width: 3, submitOnChange: true
            }
            input "no_reboot", "bool", title: "Disable local hub reboot?", defaultValue: true, submitOnChange: true, width: 3
    
            // Remote hub reboot controls (if enabled)
            if (enableRemote) {
                if (!no_remote_reboot) {
                    input "rebootRemoteHub", "button", title: "Reboot Remote Hub", width: 3, submitOnChange: true
                }
                input "no_remote_reboot", "bool", title: "Disable remote hub reboot?", defaultValue: true, submitOnChange: true, width: 3
            }
    
            if (state.confirmReboot) {
                paragraph "<b style='color:red;'>Are you sure you want to reboot the ${state.hubToReboot} hub (${state.hubToReboot == 'local' ? location.name : clientName})?</b>"
                input "confirmReboot", "button", title: "Yes, Reboot"
                input "cancelReboot", "button", title: "Cancel"
            }
            if (devmode) {
                log.debug "Dev Mode Enabled!"
                runIn(600, cancel_devmode)
            }
            input "clearRebootHistoryBtn", "button", title: "Clear Reboot History"
        }
        section("Logging") {
            input "enableLogging", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
        }
    }
}

def remotePage() {
    dynamicPage(name: "remotePage", title: "Remote Hub Configuration") {
        section {
            paragraph "Copy this connection string to the remote hub's CSP Watchdog app:"
            paragraph "${getConnectString()}"
            input "remoteConnectionString", "text", title: "Paste remote hub's connection string here:", required: true, submitOnChange: true
            if (remoteConnectionString) {
                input "clientName", "text", title: "Name for remote hub", required: true, submitOnChange: true
                input "pingInterval", "enum", title: "Ping Interval", options: [1:"1 minute", 5:"5 minutes", 10:"10 minutes", 15:"15 minutes", 30:"30 minutes", 60:"1 hour"], defaultValue: 5, submitOnChange: true
                input "remoteRebootThreshold", "number", title: "Failed pings before reboot", defaultValue: 3, range: "1..10", submitOnChange: true
            }
        }
        if (remoteConnectionString && clientName) {
            section("Actions") {
                input "testRemotePing", "button", title: "Test Remote Hub Connection"
            }
        }
    }
}

def cancel_devmode() {
    app.updateSetting("devmode", [type: "bool", value: "false"])
    log.debug "devmode disabled"
}

//###############################################################################
//#                               MAPPINGS                                       
//###############################################################################

mappings {
    path("/ping") { action: [GET: "parsePing"] }
    path("/confirmPing") { action: [GET: "confirmPing"] }
    path("/rebooting") { action: [GET: "registerReboot"] }
}

//###############################################################################
//#                           LIFECYCLE METHODS                                  
//###############################################################################

def installed() {
    log.info "CSP Watchdog installed"
    state.installed = true
    initialize()
}

def updated() {
    log.info "CSP Watchdog updated with settings: $settings"
    initialize()
}

def initialize() {
    log.debug "Initializing CSP Watchdog"
    state.rebootHistory = []
    state.rebootLimit = 3  // Maximum number of reboots
    state.rebootTimeWindow = 60 * 60 * 1000  // Time window in milliseconds (1 hour)
    state.failedTests = 0
    state.lastSwitchCmd = now()
    state.lastPing = now()
    state.attempts = 0
    state.pausedRemoteReboot = false
    state.paused = false

    unsubscribe()
    unschedule()

    subscribeToEvents()
    scheduleJobs()

    if (enableRemote) {
        parseRemoteConnectionString()
    }

    if (enableLogging) {
        runIn(1800, disableLogging)
    }

    state.installed = true
}

def subscribeToEvents() {
    subscribe(location, "hubInfo", hubInfoHandler)
    subscribe(location, "severeLoad", severeLoadHandler)
    subscribe(location, "systemStart", systemStartHandler)
    subscribe(location, "zigbeeStatus", zigbeeStatusHandler)
    subscribe(location, "zwaveStatus", zwaveStatusHandler)

    if (motionSensors) {
        subscribe(motionSensors, "motion.active", motionHandler)
    }
    if (testSwitch) {
        subscribe(testSwitch, "switch", switchHandler)
    }
    if (trueSwitches) {
        trueSwitches.each { subscribe(it, "switch", trueSwitchHandler) }
    }
}

def scheduleJobs() {
    if (trueSwitches) {
        runEvery1Minute(checkTrueSwitches)
    }
    if (enableRemote) {
        def interval = (settings.pingInterval as String)?.toInteger() ?: 5
        def cronExpression = interval < 60 ? "0 */${interval} * ? * *" : "0 0 */${interval/60} ? * *"
        schedule(cronExpression, remoteServerHealth)
    }
}

//###############################################################################
//#                           EVENT HANDLERS                                     
//###############################################################################

def appButtonHandler(btn) {
    switch (btn) {
        case "clearRebootHistoryBtn":
            clearRebootHistory()
            break
        case "rebootLocalHub":
            state.confirmReboot = true
            state.hubToReboot = "local"
            break
        case "rebootRemoteHub":
            state.confirmReboot = true
            state.hubToReboot = "remote"
            break
        case "confirmReboot":
            if (state.hubToReboot == "local") {
                rebootHub()
            } else if (state.hubToReboot == "remote") {
                rebootRemoteHub()
            }
            state.confirmReboot = false
            break
        case "cancelReboot":
            state.confirmReboot = false
            break
        case "testNow":
            runHealthCheck()
            break
        case "testRemotePing":
            remoteServerHealth()
            break
        case "update":
            updated()
            break
        case "install":
            installed()
            break
        case "addAdditionalHub":
            def currentHubs = settings.additionalHubs ?: []
            app.updateSetting("additionalHubs", currentHubs + [""])
            break
        default:
            if (btn.startsWith("rebootAdditionalHub_")) {
                def ip = btn.split("_")[1..-1].join('.') // Convert back to IP format
                rebootAdditionalRemoteHub(ip)
            }
    }
}

def hubInfoHandler(evt) {
    log.debug "Hub info event: ${evt.descriptionText}"
    if (evt.name == "hubInfo" && evt.value == "overload") {
        handleCriticalEvent("Hub overload detected")
    }
}

def severeLoadHandler(evt) {
    log.debug "event: ${evt.descriptionText}"
    handleCriticalEvent("Hub severe load detected")
}

def systemStartHandler(evt) {
    log.warn "Hub restarted: ${evt.descriptionText}"
    state.pausedRemoteReboot = true
    state.paused = true
    runIn(120, updated)
}

def zigbeeStatusHandler(evt) {
    log.debug "Zigbee status changed: ${evt.value}"
    if (evt.value == "down") {
        log.warn "Zigbee network is down"
        rebootHub()
    }
}

def zwaveStatusHandler(evt) {
    log.debug "Z-Wave status changed: ${evt.value}"
    if (evt.value == "down") {
        log.warn "Z-Wave network is down"
        rebootHub()
    }
}

def motionHandler(evt) {
    logDebug "Motion detected, running health check"
    runHealthCheck()
}

def switchHandler(evt) {
    def responseTime = now() - state.lastSwitchCmd
    logDebug "Switch ${evt.device} changed to ${evt.value}. Response time: ${responseTime}ms"
    if (responseTime > 10000) {  // 10 seconds threshold
        handleFailedTest("Switch response time too long: ${responseTime}ms")
    } else {
        state.failedTests = 0
    }
}

def trueSwitchHandler(evt) {
    logDebug "True switch ${evt.device} changed to ${evt.value}"
    state.lastTrueSwitchEvent = now()
}

//###############################################################################
//#                           HEALTH CHECK METHODS                               
//###############################################################################

def runHealthCheck() {
    log.debug "Running health check"

    if (testSwitch) {
        state.lastSwitchCmd = now()
        testSwitch.on()
        log.debug "Triggered test switch"
    }

    checkTrueSwitches()

    if (enableRemote) {
        log.debug "Initiating remote hub health check"
        remoteServerHealth()
    }
}

def checkTrueSwitches() {
    if (trueSwitches && trueSwitches.size() > 0) {
        def now = now()
        def fourHoursAgo = now - (4 * 60 * 60 * 1000)
        def inactiveDevices = trueSwitches.count { device ->
            device.events().find { it.date.time > fourHoursAgo } == null
        }

        if (inactiveDevices == trueSwitches.size()) {
            handleFailedTest("No true switch activity for over 4 hours")
        } else {
            logDebug "${trueSwitches.size() - inactiveDevices} out of ${trueSwitches.size()} true switches have been active in the last 4 hours"
        }
    }
    trueSwitches?.each { it.refresh() }
}

def handleFailedTest(reason) {
    log.warn "Failed test: $reason"
    state.failedTests++
    if (state.failedTests >= rebootThreshold) {
        rebootHub()
    }
}

def handleCriticalEvent(String reason) {
    log.warn "Critical event detected: $reason"
    state.criticalEventCount = (state.criticalEventCount ?: 0) + 1

    if (state.criticalEventCount >= rebootThreshold) {
        log.error "Critical event threshold reached. Rebooting hub."
        rebootHub()
    } else {
        log.warn "Critical event count: ${state.criticalEventCount}/${rebootThreshold}"
    }
}

//###############################################################################
//#                           REMOTE HUB METHODS                                 
//###############################################################################

def remoteServerHealth() {
    if (state.remoteHubChecksPaused) {
        log.debug "Remote hub health check paused because it's rebooting"
        return
    }

    logDebug("Remote Server Health Check")
    remoteResponded = false

    if (state.pausedRemoteReboot) {
        log.warn "Ping activity suspended because ${clientName} is rebooting"
    } else {
        sendGetCommand("/ping")
    }

    runIn(10, checkResult)
}

def checkResult() {
    logDebug """
    remoteResponded = $remoteResponded
    state.attempts = ${state.attempts}
    remoteRebootThreshold = $remoteRebootThreshold
    """
    if (!remoteResponded) {
        if (state.attempts < remoteRebootThreshold) {
            state.attempts++
            log.warn "${clientName} failed to respond! attempt #${state.attempts}"
            state.pingInterval = 3000 // set shorter ping interval 
            runIn(30, remoteServerHealth)
        } else {
            log.warn "Reboot threshold reached. Initiating reboot of ${clientName}."
            rebootRemoteHub()
        }
    } else {
        if (state.attempts > 0) { 
            log.warn "FALSE ALARM, ${clientName} is now responding after ${state.attempts} failed attempts. Resuming normal operations." 
            resumeNormalOperations()
        } else {
            logDebug "${clientName} responded normally."
        }
    }
}

def checkRemoteHubrebootHub() {
    log.info "Checking if ${clientName} has successfully rebooted..."
    
    // Attempt to ping the remote hub
    try {
        sendGetCommand("/ping")
        log.info "${clientName} is responsive after reboot attempt"
        resumeNormalOperations()
    } catch (Exception e) {
        log.warn "${clientName} is not responsive after reboot attempt. Scheduling another check."
        state.rebootAttempts = (state.rebootAttempts ?: 0) + 1
        if (state.rebootAttempts < 5) {  // Limit to 5 attempts
            runIn(60, "checkRemoteHubReboot")
        } else {
            log.error "${clientName} failed to respond after 5 reboot checks. Manual intervention may be required."
            resumeNormalOperations()
        }
    }
}

def parsePing() {
    logDebug "Received ping from remote hub"
    if (state.paused && !state.pausedRemoteReboot) {
        log.info "Ping activity paused"
    } else {
        logDebug "Received ping from ${clientName}."
        if (state.pausedRemoteReboot) {
            updated()
        }
    }
    sendConfirmation()
    return [status: "received"]
}

def confirmPing() {
    logDebug "Ping confirmation received from remote hub"
    remoteResponded = true
    if (state.attempts != 0) {
        log.warn "FALSE ALARM ${clientName} is online"
    } else {
        log.info "CONNECTION TO ${clientName} IS HEALTHY"
    }
    state.attempts = 0
}

def sendConfirmation() {
    sendGetCommand("/confirmPing")
}

def rebootRemoteHub() {
    if (no_remote_reboot) {
        log.warn "Remote hub reboot is disabled. Skipping reboot."
        return
    }

    if (devmode) {
        log.warn "Remote Reboot command test successful - the WILL NOT REBOOT"
        return 
    }

    log.warn "----------------- INITIATING REBOOT OF ${clientName} ----------------------"
    
    state.pausedRemoteReboot = true
    state.paused = true
    
    // Pause the regular health check schedule
    unschedule(remoteServerHealth)
    
    // Attempt reboot using main API
    try {
        sendPostRebootCommand("/hub/reboot")
        log.info "Reboot command sent to ${clientName} using main API"
        handleRemoteHubRebooting()
    } catch (Exception e) {
        log.error "Failed to send reboot command to ${clientName} using main API: ${e.message}"
        
        // Fallback to maintenance API
        try {
            sendPostRebootCommand("/api/rebootHub", "8081")
            log.info "Reboot command sent to ${clientName} using fallback maintenance API"
        } catch (Exception e2) {
            log.error "Failed to send reboot command to ${clientName} using fallback API: ${e2.message}"
            log.warn "Manual intervention may be required to reboot ${clientName}"
            runIn(1, resumeNormalOperations)
            return
        }
    }

    // Schedule a check to verify if remote hub rebooted
    state.rebootAttempts = 0
    runIn(300, "checkRemoteHubReboot")
}

def sendGetCommand(String command, String overrideUri = null) {
    def serverURI = overrideUri ?: state.remoteUri
    def fullUri = serverURI + command

    logDebug("state.remoteUri: $state.remoteUri")
    logDebug("serverURI: $serverURI")
    logDebug("command: $command")

    logDebug("Sending GET request to: $fullUri")

    def requestParams = [
        uri: fullUri,
        requestContentType: "application/json",
        headers: [
            Authorization: "Bearer ${state.remoteToken}"
        ],
        timeout: 10
    ]

    httpGet(requestParams) { response ->
        if (response.status == 200) {
            logDebug "GET request successful"
        } else {
            log.warn "GET request returned status ${response.status}"
        }
    }
}

def sendPostRebootCommand(String command, String port = "8080") {
    // Extract the IP address part from state.remoteUri
    def serverURI = state.remoteUri.split('/')[0..2].join('/')
    def fullUri = "${serverURI}:${port}/${command}"

    logDebug("command: $command")
    logDebug("serverURI: $serverURI")
    logDebug("Sending GET request to: $fullUri")

    def requestParams = [
        uri: fullUri
    ]

    try {
        httpPost(reqParams) {
            response -> log.debug response
        }
    } catch (Exception e) {
        log.error "${e}"
    }
}

def rebootAdditionalRemoteHub(String ip) {
    if (devmode) {
        log.warn "Remote Reboot command test successful for $ip - the hub WILL NOT REBOOT"
        return 
    }

    log.warn "Initiating reboot of remote hub at $ip"
    sendNotification("Initiating reboot of remote hub at $ip")

    try {
        rebootAdditionalHub("/hub/reboot", ip)
        log.info "Reboot command sent successfully to $ip using main API"
    } catch (Exception e) {
        log.error "Failed to send reboot command to $ip using main API: ${e.message}"
        
        try {
            rebootAdditionalHub("/api/rebootHub", ip, "8081")
            log.info "Reboot command sent successfully to $ip using fallback maintenance API"
        } catch (Exception e2) {
            log.error "Failed to send reboot command to $ip using fallback API: ${e2.message}"
            sendNotification("Failed to reboot remote hub at $ip. Manual intervention may be required.")
        }
    }
}

def rebootAdditionalHub(String ip, String port = "8080") {
    def fullUri = "http://${ip}:${port}/hub/reboot"

    logDebug("Sending POST request to: $fullUri")

    def requestParams = [
        uri: fullUri
    ]

    try {
        httpPost(requestParams) { response ->
            log.debug "POST response: ${response.status}"
        }
    } catch (Exception e) {
        log.error "POST request failed: ${e.message}"
        throw e
    }
}

def asyncHTTPHandler(response, data) {
    if (response?.status != 200) {
        log.error "asynchttpGet() request failed with error ${response?.status}"
    }
}

def registerrebootHub() {
    log.warn "REMOTE HUB is rebooting on its own, stopping all ping activities"
    state.pausedRemoteReboot = true
    state.paused = true
}

def notifyRemoteHubOfrebootHub() {
    log.info "Notifying remote hub of local reboot"
    sendGetCommand("/localHubRebooting")
    
    // Send notification to selected devices
    def message = "${location.name} hub is rebooting"
    sendNotification(message)
}

def handleRemoteHubRebooting() {
    log.info "Remote hub is rebooting. Starting ping attempts."
    state.remoteRebooting = true
    state.rebootStartTime = now()
    runIn(60, "pingRemoteHub")
    
    // Send notification
    sendNotification("Remote hub ${clientName} is rebooting. Monitoring for its return.")
}

def pingRemoteHub() {
    if (now() - state.rebootStartTime > 600000) { // 10 minutes timeout
        log.warn "Remote hub hasn't responded for 10 minutes after reboot."
        sendNotification("Remote hub ${clientName} hasn't responded for 10 minutes after reboot.")
        state.remoteRebooting = false
        return
    }
    
    try {
        sendGetCommand("/ping")
        log.info "Remote hub responded to ping after reboot."
        state.remoteRebooting = false
        sendNotification("Remote hub ${clientName} is back online after reboot.")
    } catch (Exception e) {
        log.debug "Remote hub not responding yet. Retrying in 60 seconds."
        runIn(60, "pingRemoteHub")
    }
}

def sendNotification(message) {
    log.warn "Sending notification: $message"
    
    // Send to notification devices (e.g., mobile devices)
    notificationDevices?.each { device ->
        device.deviceNotification(message)
    }
    
    // Send to speaker devices
    speakerDevices?.each { speaker ->
        speaker.speak(message)
    }
}

def handleExternalRebootDetected() {
    log.warn "External reboot detected on remote hub"
    handleRemoteHubRebooting()
    sendNotification("External reboot detected on remote hub ${clientName}")
    return [status: "received"]
}


//###############################################################################
//#                           LOCAL HUB REBOOT METHODS                                    
//###############################################################################

def rebootHub() {
    if (no_reboot) {
        log.warn "Local hub reboot is disabled. Skipping reboot."
        return
    }

    if (devmode) {
        log.warn "Local Reboot command test successful - the WILL NOT REBOOT"
        return 
    }

    log.warn "Initiating hub reboot due to critical events or failed tests"
    state.failedTests = 0
    state.criticalEventCount = 0

    if (!isRebootAllowed()) {
        log.warn "Reboot prevented due to excessive reboot frequency"
        return
    }

    log.warn "Initiating hub reboot due to critical events or failed tests"
    state.failedTests = 0
    state.criticalEventCount = 0
    
    // Add this reboot to the history
    addRebootToHistory()
    
    // Pause remote hub pinging
    pauseRemoteHubChecks()
    
    try {
        // First attempt: Using HubAction
        sendHubCommand(new hubitat.device.HubAction("reboot", hubitat.device.Protocol.INTERNAL))
        log.info "Reboot command sent successfully using HubAction"
    } catch (Exception e) {
        log.error "Failed to reboot using HubAction: ${e.message}"
        
        try {
            // Second attempt: Using httpPost on port 8080
            httpPost([
                uri: "http://${location.hub.localIP}:8080",
                path: "/hub/reboot",
                timeout: 10
            ]) { response ->
                log.info "Reboot command sent successfully using httpPost on port 8080"
            }
        } catch (Exception e2) {
            log.error "Failed to send reboot command over port 8080: ${e2.message}"
            
            try {
                // Third attempt: Using Hub's maintenance API on port 8081
                httpPost([
                    uri: "http://${location.hub.localIP}:8081",
                    path: "/api/rebootHub",
                    timeout: 10
                ]) { response ->
                    log.info "Reboot command sent successfully using maintenance API on port 8081"
                }
            } catch (Exception e3) {
                log.error "All reboot attempts failed. Final error: ${e3.message}"
                log.warn "Manual intervention may be required to reboot the hub."
                // Resume remote hub pinging if all reboot attempts failed
                resumeRemoteHubChecks()
                return
            }
        }
    }
    
    // Schedule a check to verify if reboot occurred
    runIn(300, "checkIfRebooted")
}

def checkIfRebooted() {
    log.info "Checking if hub successfully rebooted..."
    
    def currentTime = now()
    def rebootTime = state.rebootInitiatedTime ?: 0
    def uptime = location.hub.uptime
    
    if (currentTime - rebootTime > 240000 && uptime < 240) {  // 4 minutes
        log.info "Hub has successfully rebooted. Uptime: ${uptime} seconds"
        resumeRemoteHubChecks()
    } else if (currentTime - rebootTime > 600000) {  // 10 minutes
        log.warn "Hub may not have rebooted successfully. Please check manually."
        resumeRemoteHubChecks()
    } else {
        log.debug "Reboot not confirmed yet. Scheduling another check."
        runIn(60, "checkIfRebooted")
    }
}

def clearRebootHistory() {
    state.rebootHistory = []
    log.info "Reboot history cleared"
}

def addRebootToHistory() {
    def now = now()
    state.rebootHistory << now
    
    // Keep only the recent reboot history
    state.rebootHistory = state.rebootHistory.findAll { (now - it) < state.rebootTimeWindow }
}

def isRebootAllowed() {
    def now = now()
    def recentReboots = state.rebootHistory.findAll { (now - it) < state.rebootTimeWindow }
    
    if (recentReboots.size() < state.rebootLimit) {
        return true
    } else {
        log.warn "Too many reboots (${recentReboots.size()}) in the last ${state.rebootTimeWindow / (60 * 1000)} minutes"
        return false
    }
}

//###############################################################################
//#                           UTILITY METHODS                                    
//###############################################################################

def resumeRemoteHubChecks() {
    log.info "Resuming remote hub checks"
    state.remoteHubChecksPaused = false
    
    // Reschedule remote hub health checks
    if (enableRemote) {
        def interval = (settings.pingInterval as String)?.toInteger() ?: 5
        def cronExpression = interval < 60 ? "0 */${interval} * ? * *" : "0 0 */${interval/60} ? * *"
        schedule(cronExpression, remoteServerHealth)
    }
}

def resumeNormalOperations() {
    log.info "Resuming normal operations for ${clientName}"
    state.attempts = 0
    state.rebootAttempts = 0
    state.pingInterval = 60000 // reset ping interval
    state.pausedRemoteReboot = false
    state.paused = false
    
    // Reschedule normal health checks
    if (enableRemote) {
        def interval = (settings.pingInterval as String)?.toInteger() ?: 5
        def cronExpression = interval < 60 ? "0 */${interval} * ? * *" : "0 0 */${interval/60} ? * *"
        schedule(cronExpression, remoteServerHealth)
    }
}

def pauseRemoteHubChecks() {
    log.info "Pausing remote hub checks due to local hub reboot process"
    state.remoteHubChecksPaused = true
    unschedule(remoteServerHealth)
}

def parseRemoteConnectionString() {
    try {
        def connectionData = new String(remoteConnectionString.decodeBase64()).split(',')
        state.remoteUri = connectionData[0]
        state.remoteToken = connectionData[1]
    } catch (Exception e) {
        log.error "Failed to parse remote connection string: ${e.message}"
    }
}

def getConnectString() {
    def uri = getFullLocalApiServerUrl()
    def token = state.accessToken ?: createAccessToken()
    return "${uri},${token}".bytes.encodeBase64()
}

def logDebug(msg) {
    if (enableLogging) {
        log.debug msg
    }
}

def disableLogging() {
    log.info "Disabling debug logging"
    app.updateSetting("enableLogging", [value: "false", type: "bool"])
}

def jsonResponse(respMap) {
    render contentType: 'application/json', data: JsonOutput.toJson(respMap)
}

//###############################################################################
//#                           HELPER METHODS                                     
//###############################################################################

def formatText(title, textColor, bckgColor) {
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:10px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: 3px\">${title}</div>"
}

def menuHeader(titleText) {
    return "<div style=\"width:102%;background-color:#1C2BB7;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"
}
