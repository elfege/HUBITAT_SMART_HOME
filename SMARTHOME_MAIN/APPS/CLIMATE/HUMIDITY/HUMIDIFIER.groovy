/**
*  
*/
definition(
    name: "HUMIDIFIER",
    namespace: "elfege",
    author: "elfege",
    description: "Turn on/off a plug based on a humidity threshold",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",

)

preferences {
    page name: "pageSetup"
}

def pageSetup(){
    if(atomicState.paused)
    {
        log.debug "new app label: ${app.label}"
        while(app.label.contains(" (Paused) "))
        {
            app.updateLabel(app.label.minus("(Paused)" ))
        }
        app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
    }
    else if(app.label.contains("(Paused)"))
    {
        app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
        while(app.label.contains(" (Paused) ")){app.updateLabel(app.label.minus("(Paused)" ))}
        log.debug "new app label: ${app.label}"
    }

    def pageProperties = [
        name:       "pageSetup",
        title:      "${app.label}",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    return dynamicPage(pageProperties){

        if(atomicState.paused == true)
        {
            atomicState.button_name = "resume"
            logging("button name is: $atomicState.button_name")
        }
        else 
        {
            atomicState.button_name = "pause"
            logging("button name is: $atomicState.button_name")
        }
        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"

        }
        section("restricted modes")
        {
            input "restrictedmodes", "mode", title: "Select the location modes under which this app should not run", required: false, multiple:true
        }

        section() 
        {
            label title: "Assign a name", required: false
        }

        section("Select a switch...") {
            input "SWITCH", "capability.switch", title: "pick a switch", required:true, multiple: true
            if(restrictedmodes)
            {
                input "offInRestrictedMode", "bool", title: "Keep ${SWITCH.join(", ")} off when in restricted mode", defaultValue:true
            }

        }
        section("Humidity Settings ") {
            input "sensor", "capability.relativeHumidityMeasurement", title: "pick a sensor", required:true, multiple: false
            input "threshold", "number", title:"Select humidity threshold above which $SWITCH will be turned off", required: true, 
                range: "10..100", defaultValue: "50", submitOnChange:true

            input "humidity_level", "capability.switchLevel", title: "Adjust desired humidity level with a virtual dimmer", required: false, multiple: false, submitOnChange:true 
        }

        

        section("save power with sensors")
        {
            input "usemotion", "bool", title: "Keep ${SWITCH ? SWITCH.join(", ") : "switches"} off when there's no motion", submitOnChange: true, defaultValue:false
            if(usemotion)
            {
                input "motionSensors", "capability.motionSensor", title: "Select your motion sensor(s)", despcription: "pick a motion sensor", required:false, multiple:true, submitOnChange:true   
                input "noMotionTime", "number", title: "Motion timeout", required: true, description: "time in minutes"
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true, submitOnChange:true

                input "modetimeout", "bool", title: "Differentiate Timeouts With Modes", submitOnChange: true
                if(modetimeout)
                {
                    input "timeoutModes", "mode", title:"select modes", required: true, multiple: true, submitOnChange: true

                    if(timeoutModes){
                        def i = 0
                        atomicState.timeoutValMode = []
                        def timeoutValMode = []
                        for(timeoutModes.size() != 0; i < timeoutModes.size(); i++){
                            input "timeoutValMode${i}", "number", required:true, title: "select a timeout value for ${timeoutModes[i]} ", description:"Time in minutes"
                        }
                    }
                }
            }
            input "contacts", "capability.contactSensor", title:"Turn off everything when these contacts are open", multiple:true
        }



        section("logging")
        {
            input "polldevices", "bool", title:"Poll devices (disable if your humidity sensor is battery powered)", defaultValue:false, submitOnChange:true
            if(!polldevices)
            {
                unschedule(Poll)
            }
            input "enabledebug", "bool", title: "Debug", submitOnChange:true, defaultValue: false
            input "logWarn", "bool", title: "Log Warn", submitOnChange:true, defaultValue: false
            input "description", "bool", title: "Description Text", submitOnChange:true
            atomicState.EnableDebugTime = now()
            logging "enable debug logs ? ${enabledebug}"
            logwarn "enable warnings logs ? ${logWarn}"
        }


        section()
        {
            if(atomicState.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
                input "poll", "button", title: "REFRESH"
            }
            else
            {
                input "installApp", "button", title:"INSTALL APP", submitOnChange:true
            }
        }
    }
}

def installed() {
    logging("Installed with settings: ${settings}")
    atomicState.installed = true 
    initialize()

}
def updated() {
    atomicState.installed = true 
    atomicState.lastUpdated = now()
    log.info "updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()

}
def initialize() {
    if(enabledebug || logWarn)
    {
        if(logWarn) log.debug "warning logs enabled"  
        if(enabledebug) log.warn "warning debug enabled"
        atomicState.EnableDebugTime = now()
        runIn(1800,disablelogging)
        if(logWarn) descriptiontext "Warnings will be disabled in 30 minutes"
        if(enabledebug) descriptiontext "debug will be disabled in 30 minutes"
    }
    else 
    {
        log.warn "debug disabled"
    }

    subscribe(SWITCH, "switch", switchHandler)
    subscribe(sensor, "humidity", humidityHandler)

    if(humidity_level){
        subscribe(humidity_level, "level", humidityLevelHandler)
    }

    if(usemotion && motionSensors)
    {
        int i = 0
        int s = motionSensors.size()
        for(s!=0;i<s;i++)
        {
            subscribe(motionSensors[i], "motion", motionHandler)
            log.trace "${motionSensors[i]} subscribed to motion events"
        }
    }
    if(contacts)
    { 
        int i = 0
        int s = contacts.size()
        for(s!=0;i<s;i++)
        {
            subscribe(contacts[i], "contact", contactHandler)
            log.trace "${contacts[i]} subscribed to contacts events"
        }
    }

    if(polldevices)
    {
        schedule("0 0/10 * * * ?", Poll)  
    }
    schedule("0 0/1 * * * ?", master) 
    master()
}
def appButtonHandler(btn) {
    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused
        log.debug "atomicState.paused = $atomicState.paused"
        if(atomicState.paused)
        {
            log.debug "unsuscribing from events..."
            unsubscribe()  
            log.debug "unschedule()..."
            unschedule()
            break
        }
        else
        {
            updated()            
            break
        }
        case "update":
        atomicState.paused = false
        updated()
        break
        case "run":
        if(!atomicState.paused)
        master()
        break
        case "poll":
        if(!polldevices)
        {
            log.warn "Polling not enabled, command ignored!"
        }
        else 
        {
            Poll()
        }
        break
        case "installApp":
        installed()
        break
    }
}

def humidityLevelHandler(evt){
    log.debug "$evt.device set to $evt.value"

    app.updateSetting("threshold",[value:evt.value,type:"number"])

    master()
}


def switchHandler(evt){
    log.debug "$evt.device turned $evt.value"
    if(atomicState.lastUpdated == null || (now() - atomicState.lastUpdated > 2 * 24 * 60 * 60 * 1000 || atomicState.installed == false))
    {
        log.warn "app is self repairing..."
        updated()
    }
}
def motionHandler(evt){
    if(location.mode in restrictedmodes && !offInRestrictedMode) return
    descriptiontext "$evt.device is $evt.value"

     if(evt.value == "active")
        {
            atomicState.activeEvents = atomicState.activeEvents != null ? atomicState.activeEvents + 1 : 1
            atomicState.lastActiveEvent = now()
        }

    master()
}
def contactHandler(evt){
    if(location.mode in restrictedmodes && !offInRestrictedMode) return
    descriptiontext "$evt.device is $evt.value"
    master()
}
def humidityHandler(evt){
    if(location.mode in restrictedmodes && !offInRestrictedMode) return
    log.info "$evt.device returns ${evt.value}% humidity"
    master()
}

def master(){
    
    if(location.mode in restrictedmodes && !offInRestrictedMode) return
    if(location.mode in restrictedmodes)
    {        
        logging "outside of operating mode"
        if(offInRestrictedMode)
        {
            descriptiontext("(restricted off mode)")
            SWITCH.off()
        }
    }
    else 
    {
        def thres = threshold.toInteger()
        log.debug "threshold is: $thres"
        boolean anyIsOff  = SWITCH.any{it -> it.currentValue("switch") == "off"}
        boolean anyIsOn = SWITCH.any{it -> it.currentValue("switch") == "on"}
        boolean anyContactOpen = contacts.any{it -> it.currentValue("contact") == "open"}
        boolean motionIsActive = activeMotion()


        logging """

anyIsOn = $anyIsOn
anyIsOff = $anyIsOff
"""
        atomicState.falseValue = false
        def hum = sensor.currentValue("humidity")?.toInteger()
        hum = hum != null ? hum : 0



        if(hum == 0 && sensor.hasCapability("RelativeHumidityMeasurement"))
        {
            log.warn formatText("""
*********************************************************************************************
Although $sensor has humidity measurement capability, it has not returned any value yet. 
Please, wait and if this message continues to appear after a day, check your device. 
This app will now run based on a 40% humidity value
*********************************************************************************************
""", "black", "red")
            //Poll()
            atomicState.falseValue = true 
            hum = 40
        }
        else if(hum == 0)
        {
            log.error "For some reason, $sensor does not have humidity capability. Please, select a different device" // veeeery unlikely... 
            return
        }

        descriptiontext "humidity is ${hum}% ${atomicState.falseValue ? formatText("(FALSE VALUE CHECK YOUR DEVICE)","white","red"):""} and threshold is: $thres (${app.label})"

        if(!motionIsActive || hum >= thres || anyContactOpen)
        {
            if(anyIsOn)
            {
                logging("switch off")
                SWITCH.off()
            }
            else 
            {
                def causes = [!motionIsActive, anyContactOpen, hum >= thres]
                moreThanOneCause = causes.size() > 1                
                descriptiontext "${SWITCH.size() > 1 ? "Switches are" : "Switch is"} off because ${!motionIsActive ? "there is no motion" : ""}${!motionIsActive && moreThanOneCause ? " and ":""}${anyContactOpen ? "some contacts are open" : ""}${(!motionIsActive || anyContactOpen) && moreThanOneCause ? " and ":""}${hum >= thres ? "humidity level is above ${thres}%" : ""}"
            }
        }
        else 
        {
            if(anyIsOff) 
            {
                logging("switch on")
                SWITCH.on()
            }
            else
            {
                logging("switch already on")
            }
        }
    }
}
def Poll(){

    if(location.mode in restrictedmodes)
    {
        descriptiontext "outside of operating mode"
    }
    else 
    {
        if(polldevices)
        {
            boolean sensorpoll = sensor.hasCommand("poll")
            boolean sensorrefresh = sensor.hasCommand("refresh") 

            if(sensorrefresh){
                sensor.refresh()
                logging("refreshing $sensor")
            }
            if(sensorpoll){
                sensor.poll()
                logging("polling $sensor")
            }

        }
    }

}

def logging(msg){

    /*log.warn """
atomicState.EnableDebugTime = $atomicState.EnableDebugTime
enabledebug = $enabledebug
"""
*/
    if (enabledebug) log.debug msg

    atomicState.EnableDebugTime = atomicState.EnableDebugTime != null ? atomicState.EnableDebugTime : now()

    if(enabledebug && now() - atomicState.EnableDebugTime > 30 * 60 * 1000)
    {
        descriptiontext "Debug has been up for too long..."
        disablelogging() 
    }

}
def disablelogging(){
    app.updateSetting("enabledebug",[value:"false",type:"bool"])
    app.updateSetting("logWarn",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}

boolean activeMotion(){

    boolean result = true
    boolean inMotionMode = motionmodes ? location.mode in motionmodes : true

    if(usemotion && inMotionMode)
    {
        /******************************BEFORE COLLECTION**********************************************************/
        //it is faster to check if a sensor is still active than to collect past events, so return true if it's the case    
        if(motionSensors.any{it -> it.currentValue("motion") == "active"})
        {
            def stillActiveDevices = motionSensors.findAll{it.currentValue("motion") == "active"}
            log.debug "${stillActiveDevices.join(", ")} ${stillActiveDevices.size() > 1 ? "are" : "is"} currently active"
            return true
        }
        /*********************************************************************************************************/

        atomicState.activeEvents = atomicState.activeEvents == null ? 0 : atomicState.activeEvents
        atomicState.lastActiveEvent = atomicState.lastActiveEvent == null ? now() : atomicState.lastActiveEvent

        //when no sensor is currently active, run collection method to check recent past events within user's timeout settings
        int timeOut = getTimeout()
        long Dtime = timeOut * 60 * 1000 
        atomicState.activeEvents = now() - atomicState.lastActiveEvent > Dtime ? 0 : atomicState.activeEvents

        descriptiontext "atomicState.activeEvents = $atomicState.activeEvents | timeout: $timeOut minutes"   

        return atomicState.activeEvents > 0
    }

    return result
}
def getTimeout(){
    def result = noMotionTime // default
    def valMode = location.mode

    if(modetimeout && location.mode in timeoutModes)
    {
        int s = timeoutModes.size()
        int i = 0
        logging("timeoutModes: $timeoutModes")
        while(i < s && location.mode != timeoutModes[i]){i++}
        logging("${location.mode} == ${timeoutModes[i]} (timeoutModes${i} : index $i) ?: ${location.mode == timeoutModes[i]}")
        valMode = "timeoutValMode${i}" // get the key as string to search its corresponding value within settings
        logging("valMode = $valMode")
        result = settings.find{it.key == valMode}?.value
        logging("valMode.value == $result")
    }
    if(result == null)
    {
        result = noMotionTime
    }
    logging("timeout is: $result  ${if(modetimeout){"because home in $location.mode mode"}}")
    return result
}

def descriptiontext(msg){
    if (description) log.info msg
}
def logwarn(msg){
    if(logWarn) log.warn msg
}
def formatText(title, textColor, bckgColor){
    return  "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}