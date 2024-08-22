def controlLights(action, switchesToControl) {
    log.debug "Controlling lights: action=${action}, switches=${switchesToControl.collect { it.displayName }}"

    switch (action) {
        case "toggle":
            switchesToControl.each { sw ->
                if (!shouldKeepSwitchOff(sw)) {
                    if (sw.currentValue("switch") == "on") {
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
        case "turn on":
            switchesToControl.each { sw ->
                if (!shouldKeepSwitchOff(sw)) {
                    sw.on()
                    log.debug "Turned on: ${sw.displayName}"
                } else {
                    log.debug "Skipped turning on ${sw.displayName} due to keep-off rule"
                }
            }
            break
        case "turn off":
            switchesToControl.off()
            log.debug "Turned off all switches"
            break
        default:
            log.warn "Unknown light control action: ${action}"
    }
}