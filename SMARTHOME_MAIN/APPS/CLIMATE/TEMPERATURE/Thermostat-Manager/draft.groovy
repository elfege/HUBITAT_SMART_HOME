def getNeed(target, simpleModeActive, inside, outside, motionActive, doorsContactsAreOpen, neededThermostats, thermModes, origin){

    log.warn "getNeed called from $origin | target: $target | outside: $outside | simpleModeActive: $simpleModeActive | inside: $inside | neededThermostats: $neededThermostats | thermModes: $thermModes"

    def humidity
    def insideHum
    def outsideThres
    def need0
    def need1
    def need
    def amplThreshold
    def amplitude
    def lo
    def hi
    def swing
    def loCoolSw
    def message
    def n
    boolean doorsOverride
    boolean INpwSavingMode
    boolean isBetween
    boolean withinTimeWindow
    boolean inWindowsModes
    boolean contactClosed
    boolean amplitudeTooHigh
    boolean swamp
    boolean toohot
    boolean needCool
    boolean needHeat
    boolean norHeatNorCool
    boolean unacceptable
    boolean timeToResend
    boolean here

    atomicState.userWantsWarmerTimeStamp = atomicState.userWantsWarmerTimeStamp == null ? now() : atomicState.userWantsWarmerTimeStamp
    atomicState.userWantsCoolerTimeStamp = atomicState.userWantsCoolerTimeStamp == null ? now() : atomicState.userWantsCoolerTimeStamp

    try {
        atomicState.lastTimeCool = atomicState.lastTimeCool == null ? 3 * 60 * 1000 + 1 : atomicState.lastTimeCool
        atomicState.lastTimeHeat = atomicState.lastTimeHeat == null ? 3 * 60 * 1000 + 1 : atomicState.lastTimeHeat
        atomicState.userWantsWarmer = atomicState.userWantsWarmer == null || (now() - atomicState.userWantsWarmerTimeStamp) >= 120 * 60 * 1000 ? false : atomicState.userWantsWarmer
        atomicState.userWantsCooler = atomicState.userWantsCooler == null || (now() - atomicState.userWantsCoolerTimeStamp) >= 120 * 60 * 1000 ? false : atomicState.userWantsCooler

        humidity = outsideTemp?.currentValue("humidity")
        insideHum = getInsideHumidity() // backup for windows and value used for negative swing variation when cooling   
        humidity = humidity != null ? humidity : (insideHum != null ? insideHum : 50)
        // boolean doorsContactsAreOpen = doorsOpen()
        doorsOverride = doorsManagement ? doorsOverrideMotion : true // must return true by default if none due to "(!doorsContactsAreOpen && doorsOverrideMotion)"
        INpwSavingMode = powersavingmode != null && location.mode in powersavingmode ? true : false //!simpleModeActive && (!doorsContactsAreOpen && doorsOverrideMotion)
        isBetween = timeWindowInsteadofModes ? timeOfDayIsBetween(toDateTime(windowsFromTime), toDateTime(windowsToTime), new Date(), location.timeZone) : false

        try {
            if (enablewarning) {
                message = ["<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-10%; padding-inline-start:20px'>",
                    "<br> ---------------------------- ",
                    "<br> current mode = $location.mode  ",
                    "<br> IN POWER SAVING MODE ? $INpwSavingMode  ",
                    "<br> powersavingmode = $powersavingmode  ",
                    "<br> simpleModeActive = ${simpleModeActive}  ",
                    "<br> simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ",
                    "<br> !doorsContactsAreOpen = ${!doorsContactsAreOpen} ",
                    "<br> location.mode in powersavingmode = ${location.mode in powersavingmode} ",
                    "<br> doorsOverrideMotion = $doorsOverrideMotion ",
                    "<br> atomicState.userWantsWarmerTimeStamp = $atomicState.userWantsWarmerTimeStamp ",
                    "<br> atomicState.userWantsCoolerTimeStamp = $atomicState.userWantsCoolerTimeStamp ",
                    "<br> now() - atomicState.userWantsWarmerTimeStamp => ${(now() - atomicState.userWantsWarmerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - atomicState.userWantsWarmerTimeStamp) >= 120 * 60 * 1000} ",
                    "<br> now() - atomicState.userWantsCoolerTimeStamp => ${(now() - atomicState.userWantsCoolerTimeStamp)} >= ${120 * 60 * 1000} ==> ${(now() - atomicState.userWantsCoolerTimeStamp) >= 120 * 60 * 1000}     ",
                    "<br> current mode = $location.mode ",
                    "<br> ---------------------------- ",
                    "</div>"
                ]

                log.warn message.join()
            }
        } catch (Exception e) {
            log.error "getNeed debug => ${e}"
        }

        withinTimeWindow = timeWindowInsteadofModes ? isBetween : true // no pun intended... 
        inWindowsModes = timeWindowInsteadofModes ? windows && withinTimeWindow : windows && location.mode in windowsModes
        contactClosed = !contactsAreOpen()

        outsideThres = getOutsideThreshold()
        need0 = ""
        need1 = ""
        need = [need0, need1]
        amplThreshold = 3
        amplitude = Math.abs(inside - target)
        lo = celsius ? getCelsius(50) : 50
        hi = celsius ? getCelsius(75) : 75
        swing = outside < lo || outside > hi ? 0.5 : 1 // lower swing when hot or cold outside
        atomicState.swing = swing

        loCoolSw = celsius ? getCelsius(60) : 60
        amplitudeTooHigh = amplitude >= amplThreshold // amplitude between inside temp and target / preventing amplitude paradox during mid-season
        swamp = insideHum >= 50 && inside > target + swing && (!inWindowsModes || contactClosed) // prevent the swamp effect: not so hot outside, but very humid so cooling might be needed. NB: overrides simple mode!
        toohot = inside >= target && amplitudeTooHigh // usefull when inside's inertial heat gets too strong during shoulder season. 

        // remember toohot event so as to continue to override normal eval until target is reached
        if (toohot && !atomicState.wasTooHot) {
            atomicState.wasTooHot = true
        }
        if (inside <= target && atomicState.wasTooHot) { // once and only once target is reached, and if there was an override previously set up, cancel this cooling decision's override
            atomicState.wasTooHot = false
        }
        try {
            needCool = atomicState.userWantsCooler ? true :
                (swamp ? true && atomicState.lastNeed == "cool" :
                    atomicState.lastNeed == "cool" && (toohot || atomicState.wasTooHot) ? true :
                        (!simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ?
                            (inWindowsModes ?
                                outside >= outsideThres && inside >= target + swing :
                                outside >= outsideThres && inside >= target + swing || swamp
                            ) :
                            inside >= target + swing))
        }
        catch (Exception e) {
            log.error "getNeed neddCool => $e"
        }
        try {
            needHeat = !simpleModeActive || (simpleModeActive && simpleModeSimplyIgnoresMotion) ? (outside < outsideThres || (amplitudeTooHigh && atomicState.lastNeed != "cool")) && inside <= target - swing : inside <= target - swing && outside < outsideThres

            needHeat = atomicState.userWantsWarmer || inside < target - 4 ? true : needHeat
        }
        catch (Exception e) {
            log.error "getNeed needHeat => $e"
        }

        // shoulder season management: simpleModeName Mode trigger forces ac to run despite cold outside if it gets too hot inside
        norHeatNorCool = !needCool && !needHeat && inside > target + swing && simpleModeActive && outside >= 55 ? true : false
        // the other room could be in an inadequate mode, which would be noticed by an undesirable temperature amplitude
        unacceptable = doorsContactsAreOpen && !atomicState.override && (inside < target - 2 || inside > target + 2) // if it gets too cold or too hot, ignore doorsManagement
    }
    catch (Exception e) {
        log.error "getNeed boolean contingencies => $e"
    }
    message = []

    try {
        if (enabletrace) {
            message = [
                "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-20%'>",
                "inside = $inside ",
                "target = $target ",
                "$inside < ${target - 2} : ${inside < target - 2} ",
                "$inside > ${target + 2} : ${inside > target + 2}",
                "</div>"
            ]

            log.trace message.join()
        }
    } catch (Exception e) {
        log.error "getNeed debug 2 => ${e}"
    }

    try {
        //if(enablewarning) log.warn "doorsContactsAreOpen = $doorsContactsAreOpen"
        if (unacceptable) // when doors are open, other room's thermostat manager might be in power saving mode
        {
            if (enableinfo) log.info formatText("UNACCEPTABLE TEMP - ignoring doors management sync", "red", "white")
        }
        if (enabledebug) {
            message = [
                "<div style='background:black;color:white;display:inline-block:position:relative;inset-inline-start:-20%'>",
                "INpwSavingMode = $INpwSavingMode",
                "contactClosed = $INpwSavingMode",
                "motionActive = $INpwSavingMode",
                "<div>",
            ]
            log.debug message.join()
        }
        if (!unacceptable && doorsManagement && doorsContactsAreOpen && contactClosed) {
            n = otherRoomCooler ? otherRoomCooler.currentValue("switch") == "on" ? "cool" : "off" : doorThermostat?.currentValue("thermostatMode")
            need0 = n.capitalize() // capital letter for later construction of the setCoolingSetpoint cmd String
            need1 = n

            message = "$doorsContacts ${doorsContacts.size() > 1 ? "are":"is"} open. $thermostat set to ${doorThermostat}'s mode ($n)"
            if (enableinfo) log.info "<div style=\"inline-size:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-inline-start: -10px\">$message</div>"

        }
        else if (!INpwSavingMode && contactClosed && motionActive) {
            if (needCool || needHeat || norHeatNorCool) {
                if (needCool || norHeatNorCool) {
                    if (enableinfo) log.info "needCool true"
                    need0 = "Cool"// capital letter for later construction of the setCoolingSetpoint cmd
                    need1 = "cool"
                    if (enabledebug) log.debug "need set to ${[need0,need1]}"
                }
                else if (needHeat) // heating need supercedes cooling need in order to prevent amplitude paradox
                {
                    if (enableinfo) log.info "needHeat true"
                    need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
                    need1 = "heat"
                    if (enabledebug) log.debug "need set to ${[need0,need1]}"
                }
            }
            else if (offrequiredbyuser) {
                need0 = "off"
                need1 = "off"
                if (enabledebug) log.debug "need set to OFF 6f45h4"
            }
            else {

                if (fanCirculateAlways) {
                    need0 = "off"
                    need1 = "off"
                    if (enabledebug) log.debug "need set to OFF"
                }
                else {
                    if (enableinfo) log.info "Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)"
                }
            }
        }
        else   // POWER SAVING MODE OR NO MOTION OR CONTACTS OPEN     
        {
            log.warn "POWER SAVING MODE ACTIVE" 

        def cause = location.mode in powersavingmode ? "$location.mode" :
                !motionActive ? "no motion" :
                    INpwSavingMode ? "power saving mode" :
                        !contactClosed ? "Contacts Open" :
                            "UNKNOWN CAUSE - SPANK THE DEVELOPER"

            cause = cause == "Contacts Open" ? "${cause}: ${atomicState.listOfOpenContacts}" : cause

            message = ""

            if (enabledebug) log.debug "inside < criticalhot :  ${inside < criticalhot} | inside > criticalcold :  ${inside > criticalcold}"

            need0 = "off"
            need1 = "off"

            if (criticalhot == null) {
                app.updateSetting("criticalhot", [type: "number", value: "78"])
            }

            if (inside > criticalhot || fancirculateAllways) {
                log.warn formatText("${fancirculateAllways ? "fan set to always be on" : "POWER SAVING MODE EXPCETION: TOO HOT!(${ cause } criticalhot = ${ criticalhot })"}", "black", "red")
                need0 = "Cool"
                need1 = "cool"

                if (enabletrace) log.trace "****************************** returning need ${need1}"
                return [need0, need1]

            }
            else if (inside < criticalcold) {
                if (enablewarning) log.warn formatText("POWER SAVING MODE EXPCETION: TOO COLD! ($cause criticalcold = $criticalcold)", "white", "blue")
                need0 = "Heat"
                need1 = "heat"
            }
            else {
                set_multiple_thermostats_fan_mode("auto", "back to auto when thermostat off")
                atomicState.fanOn = false
                if (okToTurnOff()) turnOffThermostats(need1, inside)
            }

            message = formatText("POWER SAVING MODE ($cause)", "white", "#90ee90")   

        def fanCmd = keepFanOnInNoMotionMode && !motionActive ? "on" : "off" // only when motion is not active, otherwise it'll also turn on when windows are open
            if (fan && fan?.currentValue("switch") != "fanCmd") {
                if (enableinfo) log.info "$fan turned $fanCmd hyr354"
                fan?."${fanCmd}"()
            }

            if (enablewarning) log.warn message

        }
    } catch (Exception e) {
        log.error "getNeed exception and power saving mode management => ${e}"
    }

    /****************WINDOWS MANAGEMENT*************************/
    try {
        windowsControl(target, simpleModeActive, inside, outside, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh)
    }
    catch (Exception e) {
        message = [
            "<br> target: $target",
            "<br> simpleModeActive: $simpleModeActive",
            "<br> inside: $inside",
            "<br> outsi: $outsi",
            "<br> humidity: $humidity",
            "<br> swing: $swing",
            "<br> needCool: $needCool",
            "<br> inWindowsModes: $inWindowsModes",
            "<br> amplitudeTooHigh: $amplitudeTooHigh"
        ]

        log.debug message.join()

        log.error "windowsControl call from getNeed => $e"
    }
    /****************SIMPLE MODE MANAGEMENT*************************/
    try {
        if (UseSimpleMode && (simpleModeActive && !doorsContactsAreOpen && !contactsOverrideSimpleMode && !simpleModeSimplyIgnoresMotion)) // 
        {
            if (enableinfo) log.info formatText("$simpleModeName Mode Enabled", "white", "grey")
        }
        else if (UseSimpleMode && simpleModeActive && contactsOverrideSimpleMode && doorsOpen) {
            if (enableinfo) log.info formatText("$simpleModeName Mode Called but NOT active due to doors being open", "white", "grey")
        }
        else if (UseSimpleMode) {
            if (enableinfo) log.info formatText("$simpleModeName Mode Disabled", "white", "grey")
        }
    }
    catch (Exception e) {
        log.error "getNeed UseSimpleMode management => $e"
    }

    /****************differentiateThermostatsHeatCool MANAGEMENT*************************/
    try {
        if (differentiateThermostatsHeatCool) {


            if (enabledebug) {
                message = [

                    "thermostat is: $thermostat | its id is: ${thermostat?.id}",
                    "neededThermostats[0] is ${neededThermostats[0]} | its id is: ${neededThermostats[0]?.id}"
                ]

                log.debug debugFromList(message)
            }

            if (thermostat?.id != neededThermostats[0]?.id) {
                if (enablewarning) log.warn "using ${neededThermostats[0]} as ${need1 == "cool" ? "cooling unit" : "heating unit"} due to user's requested differenciation"
                app.updateSetting("thermostat", [type: "capability", value: neededThermostats[0]])
                atomicState.otherThermWasTurnedOff = false // reset this value so as to allow to turn off the other unit now that we swapped them
            }

            remainsOff = get_remains_off(need1)

            if (enabletrace) log.trace "remainsOffCmd =====> $remainsOffCmd"

            if (remainsOff) {
                if ((remainsOff.currentValue("thermostatMode") != "off" || keep2ndThermOffAtAllTimes) && (!atomicState.otherThermWasTurnedOff || keep2ndThermOffAtAllTimes)) {
                    atomicState.keepOffAtAllTimesRun = atomicState.keepOffAtAllTimesRun == null ? 8 * 60 * 1000 : atomicState.keepOffAtAllTimesRun

                long deltaTimeResend = 1 * 60 * 1000 // minutes 
                    atomicState.resentOccurences = atomicState.resentOccurences == null ? 0 : atomicState.resentOccurences
                    timeToResend = (now() - atomicState.keepOffAtAllTimesRun) > deltaTimeResend && atomicState.resentOccurences < 5 // resend off command every x minutes

                    if (keep2ndThermOffAtAllTimes && timeToResend) {
                        atomicState.keepOffAtAllTimesRun = now() //prevent sending too many requests when user enabled this fail-safe option

                        if (need == "heat" && inside <= target - 15) {
                            log.warn "ignoring keep2ndThermOffAtAllTimes, ist far too cold"
                        }
                        else {
                            // remainsOff.off()
                            set_thermostat_mode(remainsOff, "off", "remainsOffCmd")
                            atomicState.resentOccurences += 1
                        }
                    }
                    else {
                        if (atomicState.resentOccurences >= 50 && need1 == "heat") {
                            log.warn "$remainsOff seems to want to remain in $need1 mode... giving up on trying to keep it off to avoid pissing the user or damaging the hardware or overriding a vital antifreeze"
                        }
                        else if (atomicState.resentOccurences >= 5 && need1 == "cool") {
                            log.warn "$remainsOff seems to insist in staying in $need1 mode... since mode is 'cool', $app.name will continue to send off commands"
                            atomicState.resentOccurences = 0
                        }
                    }
                    atomicState.otherThermWasTurnedOff = true // allow user to manually turn this unit back on, since we won't use it here until weather changes
                }
            }
        }
    }
    catch (Exception e) {
        log.error "getNeed differentiateThermostatsHeatCool management => $e"
    }

    /****************DELAYED MODES SWITCH MANAGEMENT*************************/

    // if lastNeed was A and new need is B, then it's a mode switch, which requires a delay for multiple reasons: 
    // 1. HVAC or other electrical heaters/coolers operating stability and, in the long run, lifespan
    // 2. Avoids wasting power cooling when it's cool enough outside for the house to cool on its own
    // 3. Avoids wasting power heating when it's hot enough outside or through house's inertia (which is related to outside's temp and atm pressure anyway)
    // 4. In simpler terms: we don't want cooling operation in the winter or heating in the summer. 

    atomicState.waitAfterCoolConditionMet = atomicState.waitAfterCoolConditionMet == null ? false : atomicState.waitAfterCoolConditionMet
    atomicState.waitAfterHeatlConditionMet = atomicState.waitAfterHeatlConditionMet == null ? false : atomicState.waitAfterHeatlConditionMet

    if (need1 == "cool" && atomicState.lastNeed == "heat" && !atomicState.waitAfterCoolConditionMet) {
        atomicState.lastTimeCool = now() // keep track of time to avoid oscillations between cool / heat during shoulder season
        atomicState.waitAfterHeatlConditionMet = true
    }
    if (need1 == "heat" && atomicState.lastNeed == "cool" && !atomicState.waitAfterHeatlConditionMet) {
        atomicState.lastTimeHeat = now() // same idea
        atomicState.waitAfterCoolConditionMet = true
    }
    
    def need_to_wait = false
    try {
        need_to_wait = need_to_wait_between_modes(need1, inside, outside, target)
    }
    catch (Exception e) {
        log.error "need_to_wait => $e"
    }


    try {
        if (need1 == "heat" && atomicState.userWantsWarmer) {
            if (enablewarning) log.warn "user wants a warmer room, shoulder season timed override ignored. Switching to heating mode"
        }
        else if (need_to_wait) {
            if (enablewarning) log.warn "last cooling request was too close to switch to heating mode now"
            need0 = "off"
            need1 = "off"
        }


        if (need1 == "cool" && atomicState.userWantsCooler) {
            if (enablewarning) log.warn "user wants a cooler room, shoulder season timed override ignored. Switching to cooling mode"
        }
        else if (need_to_wait) {
            if (enablewarning) log.warn "last heating request was too close to switch to cooling mode now"
            need0 = "off"
            need1 = "off"
        }
    }
    catch (Exception e) {
        log.error "need_to_wait eval shoulder season test => $e"
    }

    need = [need0, need1]

    /**************** END AND DEBUG *************************/


    try {
        atomicState.lastNeed = need1 == "off" ? atomicState.lastNeed : need1  // need1 should always be "off" when need_to_wait_between_modes == true, so no need to verify this here. 
        atomicState.need1 = need1 // memoization for other methods that can't pass needed parameters for getNeed to work

        if (enableinfo) log.info "atomicState.lastNeed = $atomicState.lastNeed"
        if (enabletrace) log.trace  "need: $need1 | target: $target | outside: $outside | inside: $inside | inside hum: ${getInsideHumidity()} | outside hum ${getOutsideHumidity()} | Open Contacts:$doorsContactsAreOpen  65fhjk45"

        if (enabledebug) {
            message = [
                "<div style='background:darkgray;color:darkblue;display:inline-block:position:relative;inset-inline-start:-20%'> ",
                "<br> --------------NEED--------------------- ",
                "<br>toohot = $toohot  ",
                "<br>swamp = $swamp ",
                "<br> insideHum = $insideHum ",
                "<br> humidity = $humidity ",
                "<br> inside = $inside ",
                "<br> swing = $swing ",
                "<br> outside >= outsideThres + 5 = ${outside >= outsideThres + 5} ",
                "<br> outside = $outside ",
                "<br> outsideThres + 5 = ${outsideThres + 5} ",
                "<br> needCool = $needCool ",
                "<br> thermModes = $thermModes ",
                "<br> simpleModeActive = $simpleModeActive ",
                "<br> simpleModeSimplyIgnoresMotion = $simpleModeSimplyIgnoresMotion ",
                "<br> atomicState.userWantsCooler = $atomicState.userWantsCooler ",
                "<br> inWindowsModes = $inWindowsModes ",
                "<br> power saving management= ${powersavingmode ? "$powersavingmode INpwSavingMode = $INpwSavingMode":"option not selected by user"} ",
                "<br> amplitude = $amplitude ",
                "<br> amplitudeTooHigh = $amplitudeTooHigh ",
                "<br>  ",
                "<br> humidity = ${humidity}% ",
                "<br> insideHum = ${insideHum}% ",
                "<br>  ",
                "<br> outside = $outside ",
                "<br> inside = $inside ",
                "<br> criticalhot = $criticalhot ",
                "<br> criticalcold = $criticalcold ",
                "<br> target = $target ",
                "<br>  ",
                "<br> swing = $swing ",
                "<br>  ",
                "<br> inside > target = ${inside > target} ",
                "<br> inside < target = ${inside < target} | $inside < $target ",
                "<br>  ",
                "<br> simpleModeActive = $simpleModeActive  ",
                "<br> contactClosed = $contactClosed  ",
                "<br> outsideThres = $outsideThres ",
                "<br> outside > target = ${outside > target} ",
                "<br> outside < target = ${outside < target} ",
                "<br> outside >= outsideThres = ${outside >= outsideThres} ",
                "<br> outside < outsideThres = ${outside < outsideThres} ",
                "<br>  ",
                "<br> needCool = $needCool ",
                "<br> needHeat = $needHeat (needHeat supercedes needCool)  ",
                "<br>  ",
                "<br> final NEED value = $need ",
                "<br> --------------------------------------- ",
                "</div> ",
            ]
            log.debug formatText(message.join(), "white", "blue")
        }
        log.trace "current therm. modes: ${thermModes.join(", ")} need_to_wait: $need_to_wait | target: $target | inside: $inside | swing=$swing | outside: $outside | need: $need1 | contacts open? ${contactsAreOpen()} (${contactsAreOpen() ? "${ atomicState.listOfOpenContacts } " : ""}) | userWantsCooler ? ${atomicState.userWantsCooler} | userWantsWarmer ? ${atomicState.userWantsWarmer}"
    }
    catch (Exception e) {
        log.error "getNeed formated debug => $e"
    }
    
    return need
}