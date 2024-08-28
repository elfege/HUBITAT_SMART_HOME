def initialize() {
    log.debug "Initializing CSP Watchdog"
    state.failedTests = 0
    state.lastSwitchCmd = now()
    state.lastPing = now()
    state.criticalEventCount = 0
    
    unsubscribe()
    unschedule()
    
    // Subscribe to hub events
    subscribe(location, "hubInfo", hubInfoHandler)
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
        runEvery1Minute(checkTrueSwitches)
    }
    if (enableRemote) {
        parseRemoteConnectionString()
        def interval = pingInterval as int ?: 5  // Default to 5 minutes if not set
        schedule("0 */${interval} * * * ?", checkRemoteHub)
    }
    
    if (enableLogging) {
        runIn(1800, disableLogging)
    }
}