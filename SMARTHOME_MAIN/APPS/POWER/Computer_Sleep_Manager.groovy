/*
*  Copyright 2016 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Battery level notifications
*
*  Author: Elfege
*/

import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput

definition(
    name: "Computer Sleep Manager",
    namespace: "elfege",
    author: "elfege",
    description: "Computer Sleep Manager: Keeps a windows computer asleep and powered down with inactive motion events",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {

    page name:"pageSetup"

}
def pageSetup() {

    
    label()

    def pageProperties = [
        name:       "pageSetup",
        title:      "${app.label}",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    return dynamicPage(pageProperties) {
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
        section("Pause/Resume")
        {
            input "pause", "button", title: "$atomicState.button_name"
        }
        section("App Name") {
            label title: "Assign a name", required: false, submitOnChange:true
        }
        section("Restricted Modes"){
            input "restrictedModes", "mode", title:"Pause this app if location is in one of these modes", required: false, multiple: true
        }

        section("Main"){

            input "motionSensors", "capability.motionSensor", title: "Choose your motion sensors", despcription: "pick a motion sensor", required: true, multiple: true, submitOnChange: true
            input "timeOut", "number", title:"After how long (in minutes)?", submitOnChange: true

            input "computer", "capability.switch", title: "Put this computer to sleep"            
            input "off_state_value", "text", title:"Write down the value returned by $sleepSwitch when your computer is asleep", required: powerSwitch ?: false, submitOnChange: true 
            input "powerSwitch", "capability.switch", title: "(optional) Ensure Complete Power Cut off", submitOnChange: true
            input "presenceSensor", "capability.presenceSensor", title: "Put $computer to sleep when someone is away", required: false, submitOnChange: true
        }
        section("Notifications"){
            input "notification", "capability.notification", title: "Select notification devices", multiple:true, required:false, submitOnChange: true             
        }

        section("logging"){
            input "enablelogging", "bool", title:"Enable logging", value:false, submitOnChange:true
            input "enabledescriptionText", "bool", title:"Enable description text", value:false, submitOnChange:true
        }
        section("--"){
            if(atomicState.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "TEST"
            }       
        }
    }
}
def label(){
    if(atomicState.paused)
    {
        logging "new app label: ${app.label}"
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
        logging "new app label: ${app.label}"
    }
}
def installed() {
    logging("Installed with settings: ${settings}")
    initialize()

}
def updated() {
    descriptionText "updated with settings: ${settings}"     
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {
    atomicState.installed = true

    if(enablelogging == true){
        atomicState.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptionText "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else 
    {
        log.warn "debug logging disabled!"
    }

    if (presenceSensor) {
        subscribe(presenceSensor, "presence", mainHandler)
        log.debug "$presenceSensor subscribed to presence events"
    }

    schedule("0 0/1 * * * ?", mainHandler)   
}

def appButtonHandler(btn) {

    switch(btn) {
        case "pause": 
            atomicState.paused = !atomicState.paused
            logging "atomicState.paused = $atomicState.paused"
            if(atomicState.paused)
            {
                logging "unsuscribing from events..."
                unsubscribe()  
                logging "unschedule()..."
                unschedule()
            }
            else
            {
                updated()
            }
            break
        case "update":
            atomicState.paused = false
            updated()
            break
        case "run": 
            mainHandler() 
    }
}

def mainHandler(evt){
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    def computerState = computer.currentValue("switch")
    def powerSwitchState = powerSwitch?.currentValue("switch")

    logging "computer switch state: ${computerState}"
    if(powerSwitchState) logging "powerSwitch switch state: ${powerSwitchState}"

    if(!Active()){
        if(computerState == "on"){
            cumputer.off() // put to sleep 
        }
        else{
            logging "$computer is $computerState"
        }
        if(powerSwitch){
                if(computerState == "sleeping" && powerSwitchState == "on"){
                    powerSwitch.off() 
            }
        }
    }
    else {
        if(powerSwitch){
            if(powerSwitchState == "off"){
                powerSwitch.on() 
            }
        }
        else{
            logging "$powerSwitch is already on"
        }
    }
}

boolean Active() {
    long Dtime = timeOut * 60 * 1000
    def period = new Date(now() - Dtime)
    int events = 0

    motionSensors.each {
        sensor ->
            events += sensor.eventsSince(period, [max: 200]).findAll{ it.value == "active" }.size()
    }
    descriptionText "motion events = $events | timeout: $timeOut minutes"
    
    if(presenceSensor){
        def lastAbsent = presenceSensor.events()findAll{it -> it.value == "not present"}[0].date
        logging "lastAbsent: $lastAbsent"

        def timeDifference =  new Date() - lastAbsent
        
        absent = timeDifference >= Dtime && presenceSensor.currentValue("presence") == "absent"
        logging "absent = $absent"
    }

    return result = events > 0 || absent 
}

def logging(msg){
    //log.warn "enablelogging ? $enablelogging" 
    if (enablelogging) log.debug msg
    if(debug && atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptionText(msg){
    //log.warn "enabledescriptionText = ${enabledescriptionText}" 
    if (enabledescriptionText) log.info msg
}
def disablelogging(){
    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}
def formatText(title, textColor, bckgColor){
    return  "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}
