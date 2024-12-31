import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput

@Field static remoteResponded = true

//#######################################-#######################################

//#                               DEFINITION                                     
//#######################################-#######################################


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

//#######################################-#######################################

//#                               PREFERENCES                                    
//#######################################-#######################################


preferences {
    page(name: "mainPage")
    page(name: "remotePage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "CSP Watchdog Configuration", install: true, uninstall: true) {
        section("Local Hub Monitoring") {
            input "useMotionSensorsAndSwitches", "bool", title: "Optional: Use motion sensors events to trigger a virtual switch and measure hub's reaction time", defaultValue: false, submitOnChange:true
            if(useMotionSensorsAndSwitches){
                paragraph "$app.label will use a virtual switch to test hub's reactivity and continuously monitor critical events such as CPU severe load, radios and cloud connectivity. It will assess the severity and reboot the hub if deemed necessary"

                input "motionSensors", "capability.motionSensor", title: "Motion sensors to trigger checks", multiple: false, required: true, submitOnChange: true
                if (motionSensors) {
                    input "testSwitch", "capability.switch", title: "Switch for response time test", required: false, submitOnChange: true
                }
            }
            else {
                paragraph "$app.label will continuously monitor critical events such as CPU severe load, radios and cloud connectivity. It will assess the severity and reboot the hub if deemed necessary"
            }
            input "rebootThreshold", "number", title: "Number of failed tests before reboot", defaultValue: 3, range: "1..10", submitOnChange: true

            // local_http_relay_switch Controls
            input "local_http_relay_switch", "bool", title: "Use a web/http relay power switch to <b><u>POWER CYCLE</u></b> your <b><u>LOCAL</u></b> hub", submitOnChange:true, defaultValue:false
            if(local_http_relay_switch){
                input "local_http_relay_switch_url", "text", title: "Url", required: true, description:"enter your http switch API reset url", submitOnChange:true
                input "local_http_relay_username", "text", title: "Username", defaultValue: "admin", required: false
                input "local_http_relay_password", "text", title: "Password", defaultValue: "admin", required: false
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
                def ipList = additionalHubIps.tokenize(',') *.trim()
                ipList.each {
                    ip ->
                        input "rebootAdditionalHub_${ip.replaceAll('\\.', '_')}", "button", title: "Reboot ${ip}", submitOnChange: true
                }
            }
        }
        section("Reboot Settings") {
            input "rebootCooldown", "enum", title: "Minimum time between reboots", options: [
                30: "30 minutes", 60: "1 hour", 120: "2 hours", 180: "3 hours", 
                240: "4 hours", 300: "5 hours", 360: "6 hours", 480: "8 hours", 600: "10 hours"
            ], defaultValue: 60, required: true
        }
        section("Actions") {
            // Health Check and Dev Mode
            input "testNow", "button", title: "Run Health Check Now"
            input "devmode", "bool", title: "Test reboot buttons without rebooting", submitOnChange: true
            if (devmode) {
                paragraph "<span style='color:orange;'>Dev Mode Enabled! Will auto-disable in 10 minutes.</span>"
                runIn(600, cancel_devmode)
            }

            // Local Hub Controls
            input "no_reboot", "bool", title: "Disable local hub reboot?", defaultValue: true, submitOnChange: true
            if (!no_reboot) {
                input "rebootLocalHub", "button", title: "Reboot Local Hub", submitOnChange: true
            } else {
                input "forceReboot", "button", title: "Force Local Hub Reboot", submitOnChange: true
            }

            // Remote Hub Controls
            if (enableRemote) {
                input "no_remote_reboot", "bool", title: "Disable remote hub reboot?", defaultValue: true, submitOnChange: true
                if (!no_remote_reboot) {
                    input "rebootRemoteHub", "button", title: "Reboot Remote Hub", submitOnChange: true
                } else {
                    input "forceRebootRemoteHub", "button", title: "Force Remote Hub Reboot", submitOnChange: true
                }
                input "createRemoteBackup", "button", title: "Create a new backup on remote hub"
                
                if (state.localBackupInProgress) {
                    def backup_status = state.localBackupStillInProgress ? "Backup still in progress..." : "Backup in progress..."
                    paragraph """
                        <div style="display: inline-block; width: 20px; height: 20px; border: 2px solid #f3f3f3; border-top: 2px solid #3498db; border-radius: 50%; animation: spin 1s linear infinite;"></div>
                        $backup_status
                        <style>
                            @keyframes spin {
                                0% { transform: rotate(0deg); }
                                100% { transform: rotate(360deg); }
                            }
                        </style>
                        <script>
                            setTimeout(function() {
                                location.reload();
                            }, 30000);
                        </script>
                    """
                    state.localBackupInProgress = false
                    

                } else if (state.localBackupComplete) {
                    paragraph state.localBackupSuccess ? "Backup completed successfully!" : "Backup failed. Please check logs."
                }
                input "checkLocalBackupStatus", "button", title: "Check Backup Status", submitOnChange: true
                input "stopBackup", "button", title: "Cancel Backup Request", submitOnChange: true
                input "cancelReboot", "button", title: "Cancel Reboot"
                
                
            }

            // Confirmation Dialogs
            if (state.confirmReboot) {
                paragraph "<b style='color:red;'>Are you sure you want to reboot the ${state.hubToReboot} hub (${state.hubToReboot == 'local' ? location.name : clientName})?</b>"
                input "confirmReboot", "button", title: "Yes, Reboot"
                input "cancelBackup", "button", title: "Skip Backup and Reset"
            }

            // remote relay switch confirmation message (see remotePage() separate dynamicPage's handler for details)
            if (remote_http_relay_switch) {
                input "reset_remote_http_relay_switch", "button", title: "Power Cycle <b><u>Remote Hub</u></b>", submitOnChange: true
                if (state.confirm_remote_http_relay_switch_reset) {
                    paragraph warning_with_javascript()

                    input "confirm_remote_http_relay_switch_reset", "button", title: "Yes, Reset local_http_relay_switch", submitOnChange: true
                    input "cancel_remote_http_relay_switch_reset", "button", title: "Cancel Remote Power Cycle", submitOnChange: true
                }
            }

            // local relay switch button and confirmation message (see remotePage() 'Local Hub Monitoring' section for details)
            if (local_http_relay_switch) {
                input "reset_local_http_relay_switch", "button", title: "Power Cycle Local Hub", submitOnChange: true

                // confirmation message
                if (state.confirm_local_http_relay_switch_reset) {
                    paragraph warning_with_javascript()
                    input "confirm_local_http_relay_switchReset", "button", title: "Yes, Reset local_http_relay_switch", submitOnChange: true
                    input "cancel_local_http_relay_switch_reset", "button", title: "Cancel Local Power Cycle", submitOnChange: true
                }
            }
            

            // Maintenance Actions
            input "clearRebootHistoryBtn", "button", title: "Clear Reboot History"
            input "clearStates", "button", title: "Clear States"
            
            if (state.installed) {
                input "update", "button", title: "Update"
            } else {
                input "install", "button", title: "Install"
            }
        }
        section("Logging") {
            input "enableLogging", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
        }
    }
}

def warning_with_javascript(){

    return """
    <div style="color: red; font-weight: bold;">
        WARNING: You are about to power cycle the remote hub using the local_http_relay_switch.
        This should only be used if the hub has become completely unreachable.
        Are you sure you want to proceed?
    </div>
    <script>
    let timeoutId = setTimeout(function() {
        location.reload();
    }, 30000);
    </script>

    """
}

def cancel_javascript(){
    state.lastBackupCheck = now() // force a page refresh
    dynamicPage(name: "scriptPage", title: "Test") {
        section(){
            paragraph """
            <script>
            clearTimeout(timeoutId)
            </script>

            """
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
                input "pingInterval", "enum", title: "Ping Interval", options: [1: "1 minute", 5: "5 minutes", 10: "10 minutes", 15: "15 minutes", 30: "30 minutes", 60: "1 hour"], defaultValue: 5, submitOnChange: true
                input "remoteRebootThreshold", "number", title: "Failed pings before reboot", defaultValue: 10, range: "5..100", submitOnChange: true
            }
        }
        section ("Reset Remote Hub With A Network Switch (failsafe)"){
            input "remote_http_relay_switch", "bool", title: "Use a web/http relay power switch to <b><u>POWER CYCLE</u></b> your <b><u>REMOTE</u></b> hub if reboot command failed", submitOnChange:true, defaultValue:false
            if(remote_http_relay_switch){
                input "remote_http_relay_switch_url", "text", title: "Url", required: true, description:"enter your http switch API reset url", submitOnChange:true
                input "remote_http_relay_username", "text", title: "Username", defaultValue: "admin", required: false
                input "remote_http_relay_password", "text", title: "Password", defaultValue: "admin", required: false
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

//#######################################-#######################################
//#                               MAPPINGS                                       
//#######################################-#######################################


mappings {
    path("/ping") { action: [GET: "parsePing"] }
    path("/confirmPing") { action: [GET: "confirmPing"] }
    path("/rebooting") { action: [GET: "registerRebootHub"] }
    path("/localHubRebooting") { action: [GET: "handleLocalHubRebooting"] } // Add this line
    path("/getManagementToken") { action: [GET: "sendRemoteManagementToken"] }
    path("/receiveManagementToken") { action: [GET: "receiveManagementToken"] }
}


//#######################################-#######################################

//#                           LIFECYCLE METHODS                                  
//#######################################-#######################################


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
    state.cancelReboot = false
    state.interrupPauseLoop = false

    unsubscribe()
    unschedule()

    subscribeToEvents()
    scheduleJobs()

    if (enableRemote) {
        parseRemoteConnectionString()
        resumeRemoteHubChecks()
        try {
            log.debug "Retrieving and storing management token..."
            getAndStoreManagementToken()

            // Schedule daily token refresh
            schedule("0 0 0 * * ?", refreshManagementToken)
        } catch (e) {
            log.error "Error setting up remote functionality: ${e.message}"
        }
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
    subscribe(location, "zwaveCrashed", zwaveStatusHandler)
    subscribe(location, 'cloudStatus', cloudStatusHandler)

    if(useMotionSensorsAndSwitches){
        if (motionSensors) {
            subscribe(motionSensors, "motion.active", motionHandler)
        }        
        if (testSwitch) {
            subscribe(testSwitch, "switch", switchHandler)
        }
    }
}

def scheduleJobs() {
    if (enableRemote) {
        def interval = (settings.pingInterval as String)?.toInteger() ?: 5
        def cronExpression = interval < 60 ? "0 */${interval} * ? * *" : "0 0 */${interval/60} ? * *"
        schedule(cronExpression, remoteServerHealth)
    }
}

//#######################################-#######################################
//#                           EVENT HANDLERS                                     
//#######################################-#######################################


def appButtonHandler(btn) {
    switch (btn) {
        case "forceReboot":
            state.confirmReboot = true
            state.hubToReboot = "local"
            state.forceReboot = true
            break
        case "forceRebootRemoteHub":
            state.confirmReboot = true
            state.hubToReboot = "remote"
            state.forceReboot = true
            break
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
                rebootHub(override = true)
            } else if (state.hubToReboot == "remote") {
                rebootRemoteHub(override = true)
            }
            state.confirmReboot = false
            state.forceReboot = false
            break
        case "cancelReboot":
            state.confirmReboot = false
            state.forceReboot = false
            state.cancelReboot = true
            state.interrupPauseLoop = true
            cancel_javascript()
            break
        case "cancelBackup":
            state.confirmReboot = false
            state.forceReboot = false
            state.cancelReboot = false
            state.interrupPauseLoop = true
            cancel_javascript()
            break
        case "testNow":
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
        
        // remote
        case "reset_remote_http_relay_switch":
            state.confirm_remote_http_relay_switch_reset = true
            state.interrupPauseLoop = false
            break
        case "confirm_remote_http_relay_switch_reset":
            reset_with_http_switch(remote_http_relay_switch_url, remote_http_relay_username, remote_http_relay_password, local=false)
            state.confirm_remote_http_relay_switch_reset = false
            state.cancelReboot = false
            break
        case "cancel_remote_http_relay_switch_reset":
            state.confirm_remote_http_relay_switch_reset = false
            cancel_javascript()
            break
        
        // local
        case "reset_local_http_relay_switch":
            state.confirm_local_http_relay_switch_reset = true
            state.interrupPauseLoop = false
            break
        case "confirm_local_http_relay_switchReset":
            reset_with_http_switch(local_http_relay_switch_url, local_http_relay_username, local_http_relay_password, local=true)
            state.confirm_local_http_relay_switch_reset = false
            state.cancelReboot = false
            break
        case "cancel_local_http_relay_switch_reset":
            state.confirm_local_http_relay_switch_reset = false
            cancel_javascript()
            break
        case "createRemoteBackup":
            createRemoteBackup()
            break
        case "stopBackup": 
            log.debug("stopBackup")
            state.interrupPauseLoop = true
            state.localBackupInProgress = false
            state.localBackupComplete = false
            state.localBackupSuccess = false
            break
        case "checkLocalBackupStatus":
            checkLocalBackupStatus()
            break
        case "clearStates":
            clear_states()
        default:
            if (btn.startsWith("rebootAdditionalHub_")) {
                def ip = btn.split("_")[1..- 1].join('.') // Convert back to IP format
                rebootAdditionalRemoteHub(ip)
            }
    }
}

def clear_states() {
    state.localBackupInProgress = false
    state.localBackupStillInProgress = false
    state.localBackupComplete = false
    state.localBackupSuccess = false
    state.confirmReboot = false
    state.confirm_remote_http_relay_switch_reset = false
    state.confirm_local_http_relay_switch_reset = false
    state.installed = false
    state.pausedRemoteReboot = false
    state.paused = false
    state.cancelReboot = false
    state.interrupPauseLoop = false
    state.forceReboot = false
    state.remoteHubChecksPaused = false
    state.remoteResponded = false
    state.remoteBackupComplete = false
    state.remoteBackupInProgress = false
    state.remoteBackupSuccess = false
    state.remoteBackupStillInProgress = false
    state.localBackupStillInProgress = false
    state.remoteRebooting = false
    log.debug ("All states variables set to false")
}


def hubInfoHandler(evt) {
    log.debug "Hub info event: ${evt.descriptionText}"
    if (evt.name == "hubInfo" && evt.value == "elevated") {
        handleCriticalEvent("Hub elevated load detected")
    }
}

def severeLoadHandler(evt) {
    log.debug "event: ${evt.descriptionText}"
    handleCriticalEvent("Hub severe load detected")
}

def systemStartHandler(evt) {
    log.warn "Hub restarted: ${evt.descriptionText}"
    state.pausedRemoteReboot = false
    state.paused = false
    updated()
}

def zigbeeStatusHandler(evt) {
    log.debug "Zigbee status changed: ${evt.value}"
    if (evt.value == "down") {
        sendNotification("Zigbee status changed: ${evt.value} ($location.name)")
        log.warn "Zigbee network is down"
        rebootHub(override=true)
    }
}

def zwaveStatusHandler(evt) {
    log.warn "Z-Wave status changed: ${evt.value}"
    if (evt.value in ["zwaveCrashed", "down"]) {
        log.warn "Z-Wave network is down"
        rebootHub(override=true)
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
        logDebug "Switch response time within acceptable range"
    }
}

def cloudStatusHandler(evt) {
    if (evt.value != 'connected') {
        log.warn "Cloud connection issue: ${evt.value}"
        handleFailedTest("Cloud Connection Is Down")
    } else {
        log.info 'Cloud connection restored'
    }
}

//#######################################-#######################################
//#                           HEALTH CHECK METHODS                               
//#######################################-#######################################


def runHealthCheck() {
    log.debug "Running health check"

    if (testSwitch) {
        state.lastSwitchCmd = now()
        testSwitch.on()
        log.debug "Triggered test switch"
    }

    if (enableRemote) {
        log.debug "Initiating remote hub health check"
        remoteServerHealth()
    }
}

def handleFailedTest(reason) {
    log.warn "Failed test: $reason"
    sendNotification("${location.name}: ${reason}")
    state.failedTests = (state.failedTests ?: 0) + 1

    if (state.failedTests >= rebootThreshold) {
        log.error "Failed test threshold reached. Initiating hub reboot."
        rebootHub(override=false)
    } else {
        log.warn "Failed test count / reboot threshold: ${state.failedTests}/${rebootThreshold}"
        // a notification here to warn about approaching the threshold
        if (state.failedTests == rebootThreshold - 1) {
            sendNotification("Hub health check: ${state.failedTests} failed tests. One more will trigger a reboot.")
        }
    }
}

def handleCriticalEvent(String reason) {
    log.warn "Critical event detected: $reason"
    state.criticalEventCount = (state.criticalEventCount ?: 0) + 1

    if (state.criticalEventCount >= rebootThreshold) {
        log.error "Critical event threshold reached. Rebooting hub."
        rebootHub(override = true)
    } else {
        log.warn "Critical event count: ${state.criticalEventCount}/${rebootThreshold}"
    }
}

//#######################################-#######################################
//#                           REMOTE HUB METHODS                                 
//#######################################-#######################################


def remoteServerHealth() {
    if (state.remoteHubChecksPaused) {
        log.debug "Remote hub health check paused because local hub rebooting"
        return
    }

    logDebug("Remote Server Health Check")
    state.remoteResponded = false

    if (state.pausedRemoteReboot) {
        log.warn "Ping activity suspended because ${clientName} is rebooting"
    } else {
        try {
            sendGetCommand("/ping")
        } catch (Exception e) {
            log.error "Failed to send ping: ${e.message}"
            
        }
    }

    runIn(15, checkResult)  // Increased timeout to 15 seconds
}

def checkResult() {
    logDebug """
    remoteResponded = ${ state.remoteResponded }
    state.attempts = ${ state.attempts }
    remoteRebootThreshold = $remoteRebootThreshold
    """
    if (!state.remoteResponded) {
        state.attempts++
        log.warn "${clientName} failed to respond! attempt #${state.attempts}"

        if (state.attempts >= remoteRebootThreshold) {
            log.warn "Reboot threshold reached. Initiating reboot of ${clientName}."
            rebootRemoteHub()
        } else {
            // Exponential backoff for retry
            def delay = Math.min(2 ** state.attempts, 30)  // Max delay of 30 minutes
            runIn(delay * 60, remoteServerHealth)
        }
    } else {
        if (state.attempts > 0) {
            log.warn "Connection restored to ${clientName} after ${state.attempts} failed attempts. Resuming normal operations."
        } else {
            logDebug "${clientName} responded normally."
        }
        state.attempts = 0
        resumeNormalOperations()
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
    // called by remote hub as a handshake / confimation message
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

def sendRemoteManagementToken() {
    log.debug "sendRemoteManagementToken///"
    try {
        if (state.localManagementToken) {
            try {
                // "/receiveManagementToken?..." calls the receiveManagementToken() method on the remote hub through the mappings' API
                // and passes the local management token in the query params. 
                sendGetCommand("/receiveManagementToken?token=${state.localManagementToken}")
                log.debug "Management token sent to remote hub"
                return [status: "success", message: "Management token sent to remote hub"]
            } catch (e) {
                log.error "Failed to send management token to remote hub: ${e.message}"
                return [status: "error", message: "Failed to send management token to remote hub: ${e.message}"]
            }
        } else {
            log.error "No local management token available to send"
            return [status: "error", message: "No local management token available"]
        }
    } catch (Exception e){
        log.error "Failed to send Remote Management Token to remote hub: ${e}"
    }
}

def receiveManagementToken() {
    // Never called locally. Always by remote hub, through mappings. 
    log.debug "receiving remote management token..."
    def token = params.token
    if (token) {
        state.remoteManagementToken = token ? token : state.remoteManagementToken == null ? "not_received" : state.remoteManagementToken
        log.debug "Received management token $token from remote hub"
        log.debug "state.remoteManagementToken = $state.remoteManagementToken"
        return [status: "success"]
    } else {
        log.error "Failed to receive management token"
        return [status: "error", message: "No token provided"]
    }
}

private getAndStoreManagementToken() {
    def token = getLocalManagementToken()
    log.debug "token = $token"
    if (token != null) {
        // The token is a string, so we can store it directly
        state.localManagementToken = token.trim() // Trim any potential whitespace
        log.debug "Management token stored successfully: ${state.localManagementToken ? state.localManagementToken[0..4] + '...' : 'null'}" // Only log the first 5 characters for security
        log.debug "Sending management token to remote hub... this can take a while"
        sendRemoteManagementToken()
        return true
    } else {
        log.error "Failed to retrieve and thus share management token"
        return false
    }
}

private getLocalManagementToken() {
    def params = [
        uri: "http://localhost:8080",
        path: "/hub/advanced/getManagementToken",
        contentType: "application/json"
    ]
    
    def tokenData = null

    try {
        asynchttpGet("handleTokenResponse", params)
        // Wait for a short time to allow the async call to complete
        pauseExecution(2000)
        tokenData = state.tempToken
        log.debug "tokenData =====> ${tokenData ? tokenData[0..4] + '...' : 'null'}"
        state.remove("tempToken")  // Clean up temporary storage
    } catch (e) {
        log.error "Error initiating management token request: ${e.message}"
    }

    return tokenData
}

def handleTokenResponse(response, data) {
    if (response.status == 200) {
        // Store the raw response data, which should be the token itself
        state.tempToken = response.data
        log.debug "Successfully retrieved local management token"
    } else {
        log.error "Unexpected response when getting management token: ${response.status}"
    }
}

def refreshManagementToken() {
    log.debug "Refreshing management token"
    def result = sendRemoteManagementToken()
    if (result.status == "success") {
        log.debug "Management token refreshed and sent to remote hub"
    } else {
        log.error "Failed to refresh management token: ${result.message}"
    }
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
        timeout: 30
    ]

    try {
        httpGet(requestParams) {
            response ->
            if (response.status == 200) {
                logDebug "GET request successful"
                state.remoteResponded = true
            } else {
                log.warn "GET request returned status ${response.status}"
                state.remoteResponded = false
            }
        }
    } catch (Exception e) {
        log.error "GET request failed: ${e.message}"
        state.remoteResponded = false
        throw e
    }
}


//#######################################-#######################################
//#                        REMOTE HUB REBOOT PROCEDURE                           
//#######################################-#######################################



def rebootRemoteHub(override = false) {

    if(!isRemoteRebootAllowed()) {
        log.warn "Remote hub reboot prevented due to cooldown period"
        return
    }

    if(!override){
        if (no_remote_reboot) {
            log.warn "Remote hub reboot is disabled. Skipping reboot despite connection issues."
            sendNotification("Connection to ${clientName} lost, but reboot is disabled. Please check manually.")
            return
        }
        if (devmode) {
            log.warn "Remote Reboot command test successful for monitored remote hub - the hub WILL NOT REBOOT"
            return
        }
    }

    log.warn formatText("----------------- INITIATING REBOOT OF ${clientName} ----------------------", "white", "red")

    state.pausedRemoteReboot = true
    state.paused = true

    unschedule(remoteServerHealth)

    try {
        def rebootSuccess = reboot_remote_hub()

        if (!rebootSuccess) {
            log.warn "POST reboot command failed"
            if (remote_http_relay_switch) {
                log.warn "Resetting the remote_http_relay_switch... "
                rebootSuccess = reset_with_http_switch(url, username, password)
            }
            if(!rebootSuccess){
                resumeNormalOperations()
            }
        }
        
        if (rebootSuccess) {
            log.info "Reboot command sent successfully to ${clientName}"
            handleRemoteHubRebooting()
        } else {
            throw new Exception("Remote Reboot Command Failed")
        }
    } catch (Exception e) {
        log.error "Failed to send reboot command to ${clientName}: ${e.message}"
        log.warn "Reboot attempt failed. Manual intervention may be required to reboot ${clientName}"
        sendNotification("Failed to reboot ${clientName}. Manual intervention required.")
        resumeNormalOperations()
        return
    }

    state.rebootAttempts = 0
    runIn(300, "checkRemoteHubReboot")
}

def reboot_remote_hub() {
    def serverURI = state.remoteUri.split('/')[0..2].join('/')
    
	log.debug "serverURI: $serverURI"

    def rebootUris = [
        "${serverURI}:8080/hub/reboot", 
        "${serverURI}:8081/management/reboot?token=${state.remoteManagementToken}",
        "${serverURI}:8080/management/reboot?token=${state.remoteManagementToken}"
    ]

    for (rebootUri in rebootUris) {
	    log.debug "rebootUri : $rebootUri"
        success = false

        try {
            httpPost(
                [
                    uri: rebootUri
                ]
            )
            {
                resp -> log.debug "response from hub: ${resp.data.message}"
                if(resp.data.message == "Hub rebooting"){
                    log.warn "remote hub is rebooting"
                    success = true
                }
            }
        } catch (Exception e) {
            log.debug "Unable to reach $rebootUri. Error: ${e.message}"
        }

        if (success) return true

        try {
            httpGet(
                [
                    uri: rebootUri
                ]
            ) 
            {
                resp -> log.debug "response from hub: ${resp.data.message}"
                if(resp.data.message == "Hub rebooting"){
                    log.warn "remote hub is rebooting"
                    success = true
                }
            }
        } catch (Exception e) {
            log.debug "Unable to reach $rebootUri. Error: ${e.message}"
        }

        if (success) return true
    }

    return false
}

def hubActionCallback(response, data) {
    log.debug "Response status: ${response.status}"
    log.debug "Response data: ${response.data}"
    log.debug "Response headers: ${response.headers}"

    if (response.status == 200 || response.status == 408) {
        log.info "Reboot command likely successful. Response: ${response.status}"
        handlePotentialReboot()
    } else {
        log.warn "Unexpected response from reboot command: ${response.status}, ${response.data}"
        handleFailedReboot(response)
    }
}

def handlePotentialReboot() {
    log.info "Remote hub may be rebooting. Initiating verification process."
    state.rebootStartTime = now()
    runIn(60, "verifyReboot")
}

def verifyReboot() {
    def elapsedTime = (now() - state.rebootStartTime) / 1000  // in seconds
    if (elapsedTime < 300) {  // Check for 5 minutes
        checkIfHubRebooted()
        runIn(60, "verifyReboot")
    } else {
        log.warn "Reboot verification timed out after 5 minutes"
        resumeNormalOperations()
    }
}

def checkIfHubRebooted() {
    def serverURI = state.remoteUri.split('/')[0..2].join('/')
    def checkUri = "${serverURI}:8081/api/status"
    
    try {
        httpGet([uri: checkUri, timeout: 10]) { response ->
            if (response.status == 200) {
                def uptime = response.data.uptime
                if (uptime < 300) { // less than 5 minutes
                    log.info "Remote hub has successfully rebooted. Uptime: ${uptime} seconds"
                    resumeNormalOperations()
                } else {
                    log.debug "Remote hub uptime: ${uptime} seconds. Continuing to monitor."
                }
            }
        }
    } catch (Exception e) {
        log.debug "Unable to reach remote hub. This is expected if it's still rebooting. Error: ${e.message}"
    }
}

def getRebootCommandToRemoteHub() {
    def serverURI = state.remoteUri.split('/')[0..2].join('/')
    def rebootUri = "${serverURI}:8081/api/rebootHub"

    log.debug "Attempting to reboot remote hub using GET at: $rebootUri"

    try {
        def success = false
        httpGet([
            uri: rebootUri,
            headers: [
                'User-Agent': 'Hubitat',
                'Origin': "http://${location.hub.localIP}",
                'Access-Control-Request-Method': 'GET'
            ]
        ]) { resp -> 
            log.debug "GET Reboot command response: ${resp.status}"
            log.debug "Response body: ${resp.data}"
            if (resp.status == 200 && resp.data.success == true) {
                log.info "GET Reboot command sent successfully to $rebootUri"
                success = true
            } else {
                log.warn "Unexpected response from GET reboot command: ${resp.status}, ${resp.data}"
            }
        }
        return success
    } catch (Exception e) {
        log.error "Failed to send GET reboot command to $rebootUri: ${e.message}"
        return false
    }
}

def getMACAddress(){
    def remoteMAC = "34:e1:d1:81:b3:1e"
    return remoteMAC
}

def reset_with_http_switch(url, username, password, local=true) {
    if (!isRemoteRebootAllowed()) {
        log.warn "local_http_relay_switch reset prevented due to cooldown period"
        sendNotification("Remote hub ${clientName}: local_http_relay_switch reset prevented due to cooldown period. Please check manually.")
        return
    }

     if(local){
        createLocalBackup()
    } else {
        createRemoteBackup()
    }

     // Start monitoring the backup progress
    state.backupStartTime = now()
        monitorBackupProgress([url: url, username: username, password: password, local: local])
}

def monitorBackupProgress(data) {

    def url = data.url
    def username = data.username
    def password = data.password
    def local = data.local

    def timeout = 300 // 5 minutes in seconds
    def elapsedTime = (now() - state.backupStartTime) / 1000

    if (state.cancelReboot || state.interrupPauseLoop) {
        log.warn "Reset canceled by user request"
        state.cancelReboot = false
        state.interrupPauseLoop = false
        return
    }

    def backupComplete = local ? state.localBackupComplete : state.remoteBackupComplete

    if (backupComplete || elapsedTime >= timeout) {
        if (backupComplete) {
            log.debug "Backup completed successfully"
        } else {
            log.warn "Backup timed out after ${timeout} seconds"
        }
        proceedWithReset(url, username, password, local)
    } else {
        log.debug "Waiting for backup to finish... elapsed time: ${elapsedTime}s"
        // Schedule this method to run again in 3 seconds
        log.debug "calling: runIn(3, 'monitorBackupProgress', [data: [url: url, username: username, password: password, local: local]])"
        runIn(3, "monitorBackupProgress", [data: data])

    }
}
    
def proceedWithReset(url, username, password, local) {
    log.debug "Proceeding with reset for ${local ? 'local' : 'remote'} hub"

    log.debug "url: ${url}"
    log.debug "username: ${url}"
    log.debug "password: ${url}"
    log.debug "local: ${url}"

    if(url.contains('http://')){
        normalized_url = url.minus('http://')
    }
    def resetUri = "http://${normalized_url}"
    // ip/reset.cgi

    log.debug "Sending reset command to http relay switch at $resetUri (url: $url)"

    // ########################################################################################################
    log.warn formatText("NO WEB RELAY RESET CMD EXECUTED - TEST MODE!", "white", "red")
    return
    // ########################################################################################################

    def reqParamsAuth = [
        uri: resetUri,
        headers: [
            'Authorization': "Basic ${(remote_http_relay_username + ':' + remote_http_relay_password).bytes.encodeBase64()}"
        ]
    ]

    def reqParamsNoAuth = [
        uri: resetUri
    ]

    def resetSuccess = false

    // First attempt with authentication
    try {
        httpGet(reqParamsAuth) { resp ->
            if (resp.status == 200) {
                resetSuccess = true
                handleResetSuccess(resp, backupSuccess)
            } else {
                log.warn "Failed to reset http relay switch with auth: ${resp.status}, ${resp.data} (url: $url)"
            }
        }
    } catch (Exception e) {
        log.warn "Failed to send http relay switch reset command with auth: ${e.message} (url: $url)"
    }

    // If first attempt failed, try without authentication
    if (!resetSuccess) {
        log.debug "Retrying http relay switch reset without authentication (url: $url)"
        try {
            httpGet(reqParamsNoAuth) { resp ->
                if (resp.status == 200) {
                    resetSuccess = true
                    handleResetSuccess(resp, backupSuccess)
                } else {
                    log.warn "Failed to reset http relay switch without auth: ${resp.status}, ${resp.data} (url: $url)"
                }
            }
        } catch (Exception e) {
            log.error "Failed to send http relay switch reset command without auth: ${e.message} (url: $url)"
        }
    }

    if (!resetSuccess) {
        log.error "All attempts to reset http relay switch failed (url: $url)"
        sendNotification("Remote hub ${clientName}: All attempts to send http relay switch reset command failed (url: $url)" )
    }
}

def soft_local_shutdown(){
    // to be implemented with an app button. For later dev. 
    try {
        httpPost([
            uri: "http://localhost:8080",
            path: "/hub/shutdown",
            timeout: 10
        ]) {
            response ->
                log.info "Shutdown command sent successfully"
        }
    
    } catch (Exception e) {
        log.error "All Shutdown attempts failed. Final error: ${e.message}"
        sendNotification("Failed to Shutdown hub. Manual intervention is required.")
        if (enableRemote) {
            resumeRemoteHubChecks()
        }
        return
    }
}

def soft_remote_shutdown(){
   // to be implemented with an app button. For later dev. 
    def success = false
    try {
        httpPost([
            uri: "http://localhost:8080",
            path: "/hub/shutdown",
            timeout: 10
        ]) {
            response ->
                log.info "Shutdown command sent successfully"
                return true
        }    
    } catch (Exception e) {
        log.error "All Shutdown attempts failed. Final error: ${e.message}"
        sendNotification("Failed to Shutdown hub. Manual intervention is required.")
        if (enableRemote) {
            resumeRemoteHubChecks()
        }
        return false
    }
}

private def handleResetSuccess(resp, backupSuccess, local=true) {
    log.info "http power cycle reset command sent successfully"
    log.info "Reset successful: ${resp.data}"    
    state.cancelReboot = false
    if(!local) {
        state.lastRemoteReboot = now()
        sendNotification("Remote hub ${clientName}: http power cycle reset command sent successfully from ${location.name} ${!local ?: 'to ${clientName}'} + (backupSuccess ? ' (backup created)' : ' (but backup failed)')")
    }
    cancel_javascript()
}

def createRemoteBackup() {
    log.debug "executing createRemoteBackup"
    if (state.remoteBackupInProgress) {
        log.warn "Backup already in progress - backup operation canceled"
        state.interrupPauseLoop = true
        return
    } else {
        state.interrupPauseLoop = false
    }

    state.remoteBackupInProgress = true
    state.remoteBackupComplete = false
    state.remoteBackupSuccess = false

    def baseUri = state.remoteUri.split('/')[0..2].join('/')
    def backupUri = "${baseUri}/hub/backupDB?fileName=latest"

    log.debug "Attempting to create a backup on remote hub at ${backupUri}"

    try {
        asynchttpGet(
            'handleRemoteBackupResponse', 
            [
                uri: backupUri,
                timeout: 300  // 5 minutes timeout for backup
            ],
            [:]
        )
    } catch (Exception e) {
        log.error "Failed to initiate backup: ${e.message}"
        state.remoteBackupInProgress = false
        state.remoteBackupComplete = true
        state.remoteBackupSuccess = false
    }
}

def createLocalBackup() {
    log.debug "executing createLocalBackup"
    if (state.localBackupInProgress) {
        log.warn "Backup already in progress - backup operation canceled"
        state.interrupPauseLoop = true
        return
    } else {
        state.interrupPauseLoop = false
    }

    state.localBackupInProgress = true
    state.localBackupComplete = false
    state.localBackupSuccess = false

    def backupUri = "http://localhost:8080/hub/backupDB?fileName=latest"
    // ${location.hub.localIP}

    log.debug "Attempting to create a backup at ${backupUri}"

    try {
        asynchttpGet(
            'handleLocalBackupResponse', 
            [
                uri: backupUri,
                timeout: 300  // 5 minutes timeout for backup
            ],
            [:]
        )
    } catch (Exception e) {
        log.error "Failed to initiate backup: ${e.message}"
        state.localBackupInProgress = false
        state.localBackupComplete = true
        state.localBackupSuccess = false
    }
}

def handleLocalBackupResponse(response, data) {
    state.localBackupStillInProgress = false // not to be mistaken with state.localBackupInProgress
    state.localBackupComplete = true
    
    if (response.hasError()) {
        log.error "Backup failed: ${response.getErrorMessage()}"
        state.localBackupSuccess = false
    } else {
        def status = response.status
        if (status == 200) {
            log.info "Backup created successfully on local hub"
            state.localBackupSuccess = true
        } else {
            log.warn "Failed to create backup. Status code: ${status}"
            state.localBackupSuccess = false
        }
    }
    
    // Forces a page refresh
    state.lastBackupCheck = now()
}

def checkRemoteBackupStatus() {
    if (!state.remoteBackupComplete && !state.interrupPauseLoop) {
        state.remoteBackupStillInProgress = true // not the same as remoteBackupInProgress
        log.debug "Backup still in progress"
    } else {
        state.remoteBackupInProgress = false
        log.debug "Backup process completed"
    }
    // The page will refresh when this button is pressed due to submitOnChange: true
}

def handleRemoteBackupResponse(response, data) {
    state.remoteBackupStillInProgress = false // not to be mistaken with state.remoteBackupInProgress
    state.remoteBackupComplete = true
    
    if (response.hasError()) {
        log.error "Backup failed: ${response.getErrorMessage()}"
        state.remoteBackupSuccess = false
    } else {
        def status = response.status
        if (status == 200) {
            log.info "Backup created successfully on remote hub"
            state.remoteBackupSuccess = true
        } else {
            log.warn "Failed to create backup. Status code: ${status}"
            state.remoteBackupSuccess = false
        }
    }
    
    // Force a page refresh
    state.lastBackupCheck = now()
}

// for U.I. only
def checkLocalBackupStatus() {
    if (!state.localBackupComplete && !state.interrupPauseLoop) {
        state.localBackupStillInProgress = true // not the same as state.remoteBackupInProgress
        log.debug "Backup still in progress"
    } else {
        state.localBackupStillInProgress = false
        log.debug "Backup process completed"
        cancel_javascript() // cancel the page refresh interval
    }
}



/** ####################################### END REMOTE REBOOT PROCEDURE #######################################*/




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

def rebootAdditionalHub(String endpoint = '/hub/reboot', String ip, String port = "8080") {
    def fullUri = "http://${ip}:${port}${endpoint}"

    logDebug("Sending POST request to: $fullUri")

    def requestParams = [
        uri: fullUri,
        requestContentType: "application/json",
        headers: [: ],
        body: [: ],
        timeout: 15
    ]

    try {
        httpPost(requestParams) {
            response ->
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

def registerRebootHub() {
    log.warn "REMOTE HUB is rebooting on its own, stopping all ping activities"
    state.pausedRemoteReboot = true
    state.paused = true
}

def notifyRemoteHubOfrebootHub() {
    log.info "Notifying remote hub of local reboot"
    
    // Add mapping check
    if (!enableRemote || !state.remoteUri) {
        log.debug "Remote hub notifications disabled or not configured - skipping notification"
        return
    }

    try {
        
        sendGetCommand("/localHubRebooting")

    } catch (Exception e) {
        // We'll let this exception bubble up to be handled by the caller
        log.error "Failed to notify remote hub: ${e.message}"
        throw e
    }

    // Send notification to selected devices
    def message = "${location.name} hub is rebooting"
    sendNotification(message)
}

def handleRemoteHubRebooting() {
    log.info "Remote hub is rebooting. Pausing health checks for 5 minutes."
    state.remoteRebooting = true
    state.rebootStartTime = now()
    runIn(300, "resumeRemoteHubChecks")

    sendNotification("Remote hub ${clientName} is rebooting due to connection issues. Will resume monitoring in 5 minutes.")
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
    notificationDevices?.each {
        device ->
            device.deviceNotification(message)
    }

    // Send to speaker devices
    speakerDevices?.each {
        speaker ->
            speaker.speak(message)
    }
}

def handleExternalRebootDetected() {
    log.warn "External reboot detected on remote hub"
    handleRemoteHubRebooting()
    sendNotification("External reboot detected on remote hub ${clientName}")
    return [status: "received"]
}

//#######################################-#######################################

//#                           LOCAL HUB REBOOT METHODS                                    
//#######################################-#######################################


def rebootHub(override = false) {
    if (!override) {
        if (no_reboot) {
            log.warn "Local hub reboot is disabled. Skipping reboot despite failed tests."
            sendNotification("Hub health check: Reboot threshold reached, but reboot is disabled. Please check manually.")
            return
        }
        if (devmode) {
            log.warn "Remote Reboot command test successful for $ip - the hub WILL NOT REBOOT"
            return
        }
        if (!isRebootAllowed(override)) {
            def m = "Reboot prevented due to excessive reboot frequency"
            log.error m
            sendNotification(m)
            return
        }
    }

    log.warn "Initiating ${location.name} due to failed tests"

    // inform reboot hub
    try {
        notifyRemoteHubOfrebootHub()
    } catch (Exception e){
        log.error "failed to notify remote hub! ${e}"
    }

    // Add this reboot to the history
    try {
        addRebootToHistory()
    } catch (Exception e){
        log.error "failed to update reboot history! ${e}"
    }
    

    // Notify about the reboot
    try {
        sendNotification("Initiating hub reboot due to failed tests")
    } catch (Exception e){
        log.error "failed to send notification to notification devices! ${e}"
    }
    

    // Pause remote hub pinging if applicable
    if (enableRemote) {
        pauseRemoteHubChecks()
    }

    try {
        httpPost([
            uri: "http://localhost:8080",
            // ${location.hub.localIP}
            path: "/hub/reboot",
            timeout: 10
        ]) {
            response ->
                log.info "Reboot command sent successfully"
        }
    } catch (Exception e) {
        log.error "Failed to send reboot command: ${e.message}"
        try {
            // Fallback to maintenance API
            httpPost([
                uri: "http://${location.hub.localIP}:8081",
                path: "/api/rebootHub",
                timeout: 10
            ]) {
                response ->
                    log.info "Reboot command sent successfully using maintenance API"
            }
        } catch (Exception e2) {
            log.error "All reboot attempts failed. Final error: ${e2.message}"
            sendNotification("Failed to reboot hub. Manual intervention may be required.")
            if (enableRemote) {
                resumeRemoteHubChecks()
            }
            return
        }
    }

    // Reset failed tests counter
    state.failedTests = 0

    // Schedule a check to verify if reboot occurred
    runIn(300, "checkIfRebooted")
}

def checkIfRebooted() {
    log.info "Checking if hub successfully rebooted..."
    
    def currentTime = now()
    def rebootTime = state.lastRebootTime ?: 0
    def uptime = location.hub.uptime

    if (currentTime - rebootTime > 240000 && uptime < 240) {  // 4 minutes
        log.info "Hub has successfully rebooted. Uptime: ${uptime} seconds"
        sendNotification("Hub has successfully rebooted")
        if (enableRemote) {
            resumeRemoteHubChecks()
        }
    } else if (currentTime - rebootTime > 600000) {  // 10 minutes
        log.warn "Hub may not have rebooted successfully. Please check manually."
        sendNotification("Hub may not have rebooted successfully. Please check manually.")
        if (enableRemote) {
            resumeRemoteHubChecks()
        }
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

def isRebootAllowed(override=false) {

    def result = false
    long now = now()
    def recentReboots = state.rebootHistory.findAll { (now - it) < state.rebootTimeWindow }

   if (recentReboots.size() < state.rebootLimit) {
        result = true
    } else {
        log.warn "Too many reboots (${recentReboots.size()}) in the last ${state.rebootTimeWindow / (60 * 1000)} minutes"
        result = false
    }
    
    long lastReboot = state.lastLocalReboot == null ? now : state.lastLocalReboot
    def cooldownMillis = (settings.rebootCooldown as Integer) * 60 * 1000
    if ((now - lastReboot) > cooldownMillis){
        result = true
    }
    else{
        result = false
    }

    return result
}

def isRemoteRebootAllowed() {
    def lastReboot = state.lastRemoteReboot ?: 0
    def cooldownMillis = (settings.rebootCooldown as Integer) * 60 * 1000
    return (now() - lastReboot) > cooldownMillis
}

//#######################################-#######################################

//#                           UTILITY METHODS                                    
//#######################################-#######################################


def resumeRemoteHubChecks() {
    log.info "Resuming remote hub checks after reboot attempt"
    state.remoteHubChecksPaused = false
    state.pausedRemoteReboot = false
    state.paused = false
    state.attempts = 0

    // Immediately run a health check
    remoteServerHealth()

    // Reschedule regular health checks
    if (enableRemote) {
        def interval = (settings.pingInterval as String)?.toInteger() ?: 5
        def cronExpression = interval < 60 ? "0 */${interval} * ? * *" : "0 0 */${interval/60} ? * *"
        schedule(cronExpression, remoteServerHealth)
    }
}

def resumeNormalOperations() {
    state.pausedRemoteReboot = false
    state.paused = false
    state.rebootStartTime = null
    unschedule("verifyReboot")
    unschedule("checkIfHubRebooted")
    log.info "Resuming normal operations for ${clientName}"
    // Reschedule your normal monitoring tasks here
    schedule("0 */5 * ? * *", remoteServerHealth)  // Adjust as needed
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

//#######################################-#######################################

//#                           HELPER METHODS                                     
//#######################################-#######################################


def formatText(title, textColor, bckgColor) {
    return "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:10px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: 3px\">${title}</div>"
}

def menuHeader(titleText) {
    return "<div style=\"width:102%;background-color:#1C2BB7;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"
}
