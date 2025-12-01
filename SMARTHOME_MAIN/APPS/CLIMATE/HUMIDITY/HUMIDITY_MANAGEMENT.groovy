/**
*  Copyright 2015 SmartThings
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  Curling Iron
*
*  Author: SmartThings
*  Date: 2013-03-20
*/
definition(
    name: "HUMIDITY MANAGEMENT",
    namespace: "elfege",
    author: "elfege",
    description: "Set a dimmer according to humidity level",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",

)

preferences {
    page name:"mainpage"
}

def mainpage(){

    def pageProperties = [
        name:       "mainpage",
        title:      "$app.label",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    return dynamicPage(pageProperties){

        appLabel("mainPage")


        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"
            input "buttonPause", "capability.doubleTapableButton", title: "Pause/resume this app when I press a button", multiple: true, required: false, submitOnChange:true
            input "doubleTapCancels", "bool", title:"Allow double tap to resume (pushing the button will still allow a resume action)"
            if(buttonPause)
            {
                input "buttonTimer", "number", title: "optional: time limit in minutes", required:false
                input "pauseSpeed", "number", title: "optional: set your fan to a specific speed when the app paused", required:false
            }

        }
        section("restricted modes")
        {
            input "restrictedmodes", "mode", title: "Select the location modes under which this app should not run", required: false, multiple:true, submitOnChange:true
            if(restrictedmodes) input "offInRestrictedMode", "bool", title: "turn off all devices when in restricted mode", defaultValue:true

        }
        section("Set a dimmer...") {
            input "dimmer", "capability.switchLevel", title: "pick a dimmer", required:true, multiple: true, submitOnChange: true  
            input "minLevel", "number", title: "set a minimum dimmer level", required: false, defaultValue: 0
        }
        section("Select Humidity Sensor") {
            input "sensor", "capability.relativeHumidityMeasurement", title: "pick a sensor", required:true, multiple: false, submitOnChange: true
        }
        section("Adjust ${dimmer}'s level when a switch is turned on") {
            input "altswt", "capability.switch", title: "select a switch", required:false, multiple: true, submitOnChange: true   
            if(altswt){
                input "tempLevel", "number", title: "set a value", required: true, range: "0..100"
            }
            input "swtchOffFullFan", "bool", title: "set ${dimmer.join(", ")} back to a higher value after the switch was turned off", default: true, submitOnChange: true
            if(swtchOffFullFan){
                input "deltaMinutes", "number", title: "for how long?", description: "time in minutes", required: true, range: "1..30"
                input "smellVal", "number", title: "set a value", required: true, range: "0..100", defaultValue: 100
            }
        }
        section("Manage modes") {
            input "Modes", "mode", title: "select modes", required:false, multiple: true, submitOnChange: true
            if(Modes){
                int i = 0
                int s = Modes?.size()
                for(s != 0; i < s; i++){
                    input "ModeLevel${i}", "number", title: "set a maximum dimmer setting for when your location is in ${Modes[i]} mode", required: true, range: "0..100"
                }

            }
        }
        section()
        {
            input (name: "stopSwitch", type: "capability.switch", title: "Do not run this app when this switch is on", submitOnChange: true, multiple: true, required: false)
        }

        section() {
            input "userLabel", "text", title: "Rename this app", submitOnChange: true, required: false



            input "enabledebug", "bool", title: "Debug", submitOnChange:true
            if(enabledebug)
            {
                log.warn "debug enabled"      
                state.EnableDebugTime = now()
                runIn(1800,disablelogging)
                descriptiontext "debug will be disabled in 30 minutes"
            }
            else 
            {
                log.warn "debug disabled"
            }
            input "description", "bool", title: "Description Text", submitOnChange:true

            if(state.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
            }
        }
    }
}

def appLabel(calledBy){
    atomicState.lastLabelTime = now()

    def appLa = appLa != null && appLa != "" ? appLa : "HUMIDITY MANAGEMENT"
    appLa = userLabel != null  && userLabel != "" ? userLabel : appLa

    atomicState.button_name = atomicState.paused ? "resume" : "pause"
    logging "button name is: $atomicState.button_name"
    logging "atomicState.paused = $atomicState.paused called by $calledBy"    

    appLa = atomicState.paused ? appLa + ("<font color = 'red'> paused </font>")  : appLa    

    logging "****appLa = $appLa"
    app.updateLabel(appLa)

}

def appButtonHandler(btn) {
    if(location.mode in restrictedmode) log.debug "App in restricted mode!"
    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused       
        atomicState.button_name = atomicState.paused ? "resume" : "pause"
        atomicState.softPaused = !atomicState.softPaused
        atomicState.pauseEvt = now()
        log.debug "atomicState.paused = $atomicState.paused" 
        break
        case "update":
        updated()
        break
        case "run":
        if(!state.paused) eval()
        break

    }
}

def installed() {
    state.installed = true
    init()

}
def updated() {
    unsubscribe()
    unschedule()

    init()

}
def init() {

    atomicState.smellMode = false

    if(enabledebug)
    {
        log.warn "debug enabled"      
        state.EnableDebugTime = now()
        runIn(1800,disablelogging)
        descriptiontext "debug will be disabled in 30 minutes"
    }
    else 
    {
        log.warn "debug disabled"
    }

    if(altswt){
        subscribe(altswt, "switch", switchHandler)
    }

    if(buttonPause) subscribe(buttonPause, "pushed", doubleTapableButtonHandler)
    if(buttonPause && doubleTapCancels) 
    {
        subscribe(buttonPause, "doubleTapped", doubleTapableButtonHandler)
        log.info "${buttonPause.join(", ")} SUBSCRIBED to double tapable events"
    }

    subscribe(sensor, "humidity", humidityHandler)
    subscribe(dimmer, "level", dimmersHandler)

    def timer = 5 //Math.abs(new Random().nextInt() % 100) + 1
    //log.debug "******* Random schedule = $timer minutes....."
    schedule("0 0/$timer * * * ?", eval)
}

def dimmersHandler(evt) {
    if(location.mode in restrictedmode) return
    log.info "$evt.device set to $evt.value"

}
def switchHandler(evt){
    if(atomicState.paused) return 
    if(location.mode in restrictedmode) return

    log.warn "$evt.device turned $evt.value"

    if(swtchOffFullFan && evt.value == "off"){
        atomicState.smellMode = true
        atomicState.smellModeTime = now()
        int timer = deltaMinutes * 60
        runIn(timer, endOfSmellMode)
    }
    eval()
}
def humidityHandler(evt){
    if(location.mode in restrictedmode) return
    if(buttonTimer && atomicState.paused && now() - atomicState.pauseEvt > buttonTimer * 60 * 1000) 
    {
        atomicState.paused = false
        atomicState.pauseSpeedDone = false
    }
    if(atomicState.paused) return 
    descriptiontext "$evt.device returns ${evt.value}% humidity"
}
def doubleTapableButtonHandler(evt){

    if(location.mode in restrictedmode) return

    log.debug "BUTTON EVT $evt.device $evt.name $evt.value"

    def message = "no condition met in doubleTapableButtonHandler"

    if(evt.name == "doubleTapped")
    {
        atomicState.paused = false
        atomicState.pauseSpeedDone = false
    }
    else
    {
        atomicState.paused = !atomicState.paused 
    }
    atomicState.pauseEvt = now()


    if(atomicState.paused)
    {           
        message = "APP PAUSED WITH BUTTON PUSH EVENT" 
        if(buttonTimer) {
            log.debug "App will resume in $buttonTimer minutes"
            runIn(buttonTimer, updated)
        }
        if(pauseSpeed && !atomicState.pauseSpeedDone) 
        {
            atomicState.pauseSpeedDone = true
            log.trace "PAUSE/OVERRIDE MODE : ${dimmer.join(", ")} set to ${pauseSpeed}%"
            if(dimmer.any{it -> it.currentValue("level") != pauseSpeed}) dimmer.setLevel(pauseSpeed)
        }
    }
    else
    {
        message = "APP RESUMED WITH BUTTON EVENT"
        atomicState.pauseSpeedDone = false
        updated()  
    }

    appLabel("doubleTapableButtonHandler")
    log.trace message
    eval()
}

def eval(){
    def start = now()

    if(location.mode in restrictedmode) 
    {
        if(offInRestrictedMode && dimmer.any{it -> it.currentValue("level") != 0}) dimmer.setLevel(0)
        return
    }
    if(atomicState.paused) 
    {
        if(buttonTimer && now() - atomicState.pauseEvt > buttonTimer * 60 * 1000) 
        {
            atomicState.paused = false
            atomicState.pauseSpeedDone = false
        }


        return 
    }
    else
    {
        atomicState.pauseSpeedDone = false
    }

    if(!stopAll())
    {
        atomicState.smellModeTime = atomicState.smellModeTime != null ? atomicState.smellModeTime : now()
        logging "smellMode = $atomicState.smellMode"
        if(atomicState.smellMode && now() - atomicState.smellModeTime > deltaMinutes * 60 * 1000) // scheduled task can be canceled by init(), in which case this will insure this boolean is reset in due time
        {
            endOfSmellMode()   
        }
        // set dimmer to the same level as humidity
        def val = sensor.currentValue("humidity")
        def valRec = val

        boolean altswtIsOn = false
        if(altswt){
            int i = 0
            int s = altswt.size()

            for(s != 0;(altswt && i < s && !altswtIsOn);i++)
            {
                altswtIsOn =  "on" in altswt[i]?.currentValue("switch")
            }
        }

        //atomicState.smellMode = false // TEST ONLY DELETE AFTER

        if(!atomicState.smellMode)
        {
            logging """
Modes ? ${Modes?.size()>1}
altswtIsOn ? $altswtIsOn

"""
            if(altswtIsOn)
            {
                logging "$altswt is on, so now dimmer setting is $tempLevel"
                val = tempLevel
            }
            else if(Modes){
                if(location.mode in Modes){
                    logging """
Home is in one the specified modes: ${location.mode}
"""
                    i = 0
                    while("${location.mode}" != "${Modes[i]}")
                    {
                        logging "************** ${location.mode}" != "${Modes[i]}"
                        i++;
                    }
                    def foundMode = Modes[i]
                    def ModeLv = "ModeLevel${i}"
                    logging """
ModeLv = $ModeLv
foundMode = $foundMode
----------------------
settings: $settings
"""
                    ModeLv = settings.find{it.key == ModeLv}.value

                    val = valRec > ModeLv ? ModeLv : valRec
                    logging "MAXIMUM Dimmer value for ${Modes[i]} is $ModeLv and current applied value is $val"
                }
            }


            logging "humidity = ${val} | ${dimmer.join(", ")} set to $val"

        }
        else {
            logging "smell mode, keeping $dimmer at ${smellVal}% for $deltaMinutes minutes"
            val = smellVal
        }

        val = minLevel ? val < minLevel ? minLevel : val : val 

        if(dimmer.any{it -> it.currentValue("level") != val}) dimmer.setLevel(val)
    }
    else 
    {
        logging "APP NOT RUNNING BECAUSE STOP SWITCH IS ON"
    }
    //log.trace "main eval took ${now() - start} milliseconds to execute"
}

def endOfSmellMode(){
    if(location.mode in restrictedmode) return
    log.info "END OF SMELL MODE"
    atomicState.smellMode = false
    //eval()
}

boolean stopAll(){
    if(location.mode in restrictedmode) return
    boolean result = false
    if(stopSwitch){
        int i = 0
        int s = stopSwitch.size()
        for(s != 0;(i < s && !result);i++)
        {
            result =  "on" in stopSwitch[i].currentValue("switch")
        }
    }
    logging "stopAll() returns ${result}"
    return result

}
def logging(message){
    if(enabledebug)
    {
        log.debug message
    }
}
def descriptiontext(message){
    if(location.mode in restrictedmode) return
    if(description)
    {
        log.info message
    }
}
def disablelogging(){
    if(location.mode in restrictedmode) return
    log.warn "debug logging disabled..."
    app.updateSetting("enabledebug",[value:"false",type:"bool"])
}


