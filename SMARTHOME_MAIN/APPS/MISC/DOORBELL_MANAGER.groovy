/*
*  Copyright 2016 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Light / motion Management
*
*  Author: Elfege
*/

import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput


@Field static int delays = 0


definition(
    name: "DOORBELL MANAGER",
    namespace: "elfege",
    author: "elfege",
    description: "App exclusively dedicated to use the 'playSoundByName() function of Echo Speaks",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
)

preferences {

    page name:"pageSetup"

}
def pageSetup() {

    boolean haveDim = false


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
        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"
        }
        /*********************/

        section("trigger")
        {
            input "triggerSelect", "enum", title: "select a capability for your trigger", options:["acceleration", "switch"], submitOnChange:true
            if(triggerSelect == "acceleration")
            {
                input "trigger", "capability.accelerationSensor", title: "Choose your accelerometer", submitOnChange:true
            }
            else if(triggerSelect == "switch")
            {
                input "trigger", "capability.switch", title: "Choose your switch capable device", submitOnChange:true
            }
            log.debug "$trigger is trigger"
        }
        section("Notification devices")
        {

            input "speaker", "capability.audioNotification", title: "Select your speakers", multiple:true, required:false, submitOnChange: true 
            if(speaker)
            {
                input "soundName", "enum", title: "Select a sound", options:["bells", "buzzer", "church_bell", "doorbell1", "doorbell2","doorbell3"] 
                input "prioritySpeaker", "capability.audioNotification", title: "Select a priority speaker", multiple:false, required:false, submitOnChange: true 
            }
           
            input "musicPlayer", "capability.musicPlayer", title: "Select your music players", multiple:true, required:false, submitOnChange: true
            if(musicPlayer || !speaker) {
                input "uri", "text", title:"add an audio file uri", required:true 
                input "fallbackUri", "text", title: "Optional: fallback url", required: false
            }
            
            input "volumeLevel", "number", title: "Set the volume level", range: "0..100",required:true, submitOnChange: true 
            input "volumeRestore", "number", title: "restore to this volume level", range: "0..100", required:false, defaultValue: 50

            input "notification", "capability.notification", title: "Select notification devices", multiple: true, required: false, submitOnChange: true
            
        }

        section("modes")        
        {
            input "restrictedModes", "mode", title:"Pause this app if location is in one of these modes", required: false, multiple: true
        }

        section() {
            label title: "Assign a name", required: false
        }
        section("logging")
        {
            input "enablelogging", "bool", title:"Enable logging", value:false, submitOnChange:true
            input "enabledescriptiontext", "bool", title:"Enable description text", value:false, submitOnChange:true
        }
        section()
        {
            if(atomicState.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
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
    descriptiontext "updated with settings: ${settings}"
    atomicState.installed = true        
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {


    if(enablelogging == true){
        atomicState.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptiontext "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else 
    {
        log.warn "debug logging disabled!"
    }

    subscribe(trigger, triggerSelect, mainHandler)    
    log.debug "$trigger subscribed to $triggerSelect events"

    if(speaker){
        subscribe(speaker, "playSoundByName", echoHandler)
        log.debug "${speaker.join(", ")} subscribed to playSoundByName events"
    }

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
        {
            if(speaker) playSound()
            if(musicPlayer) play_Track()
            if(notification) send_notification()
        else
        {
            log.warn "App is paused!"
        }
        break
        }
    }
}
def locationModeChangeHandler(evt){
    logging("$evt.name is now in $evt.value mode")   
}
def echoHandler(evt){
    log.warn "$evt.device ---> $evt.value"

}

def mainHandler(evt){

    if(atomicState.paused == true)
    {
        return
    }
    log.info "location is in ${location.mode} mode"
    log.warn "${evt.name}: $evt.device is $evt.value (mainHandler)"

    if(location.mode in restrictedModes)
    {
        log.info "location in restricted mode, doing nothing"
        return
    }

    if(evt.value in ["active", "on"]) {
        logging "speakers = ${speakers?.join(", ")}"
        if(speaker) playSound()
        
        logging "music Players = ${musicPlayer?.join(", ")}"
        if(musicPlayer) play_Track()

        logging "notification devices = ${notification?.join(", ")}"
        if(notification) send_notification() 
    }

}

/* ECHO SPEAKERS */
def playSound(){
    if(location.mode in restrictedModes)
    {
        log.info "location in restricted mode, doing nothing"
        return
    }

    if(prioritySpeaker)
    {
        log.trace "sending '$soundName sound to prioritySpeaker:${prioritySpeaker}"

        playSpeakers(prioritySpeaker)
        runIn(1, otherSpeakers)
    }
    else {
        otherSpeakers()
    }
}
def otherSpeakers(){
    if(location.mode in restrictedModes)
    {
        log.info "location in restricted mode, doing nothing"
        return
    }

    for(int i=0; i < speaker.size(); i++)
    {

        def device = speaker[i] 
        if(device.id != prioritySpeaker?.id) playSpeakers(device)
    }   
}
/***************************/

/* NOTIFICATION DEVICES */
def send_notification(){
    def message = "Someone called on the INTERCOM on ${get_time()}"

    for(int i = 0; i < notification.size(); i++){
        def device = notification[i]
        try {
            notification?.deviceNotification(message)
        }
        catch(error){
            log.error "Failed to send notification to $device"
        }
    }   
      
}


/* MUSIC PLAYERS*/
def play_Track(){
    if(location.mode in restrictedModes)
    {
        log.info "location in restricted mode, doing nothing"
        return
    }

    for(int i=0; i < musicPlayer.size(); i++)
    {
        def device = musicPlayer[i]
        try{            
            device.playTrack(uri)
            device.setLevel(volumelevel)
            device.setLevel(volumeRestore)
        }
        catch(error) {
            log.warn "Failed to access url: ${url}. ERROR message: ${error}"
            try {
                log.info "Playing fallback url: ${fallbackUri}"
                device.playTrack(fallbackUri)
            }
            catch(err){
                log.error "FAILED TO PLAY FALL BACK URL: ${err}"
            }
        }
    }
}

def playSpeakers(device){
    if(location.mode in restrictedModes)
    {
        log.info "location in restricted mode, doing nothing"
        return
    }

    logging "sending '$soundName sound to ${device}"
    try {
        // prevent the stupid echo's "BEEP" when changing volume level
        device.doNotDisturbOn() 
        device.setLevel(volumeLevel) 
        device.doNotDisturbOff() 
        pauseExecution(500)

        device.playSoundByName(soundName)
    }
    catch(err) {
        log.warn "Failed to access url: ${url}. ERROR message: ${err}"
        if(musicPlayer){
            for(int i=0; i < musicPlayer.size(); i++)
            {
                if(fallbackUri){
                    try {
                        log.info "Playing fallback url: ${fallbackUri}"
                        device.playTrack(fallbackUri)
                    }
                    catch(error){
                        log.error "FAILED TO PLAY FALL BACK URL: ${error}"
                    }

                }
            }
        }
    }
}

def get_time() {
    // Get the current time in milliseconds
    def currentTimeMillis = now()

    // Create a Date object from the current time milliseconds
    def currentDate = new Date(currentTimeMillis)

    // Format the month
    def monthFormat = new SimpleDateFormat("MMM.")
    def readableMonth = monthFormat.format(currentDate)

    // Get the day of the month
    def dayOfMonth = currentDate.date

    // Determine the appropriate suffix for the day
    def suffix = getDayOfMonthSuffix(dayOfMonth)

    // Format the time
    def timeFormat = new SimpleDateFormat("HH:mm:ss")
    def readableTime = timeFormat.format(currentDate)

    // Return the formatted date and time with the appropriate suffix
    return "${readableMonth} ${dayOfMonth}${suffix} at ${readableTime}"
}

def getDayOfMonthSuffix(dayOfMonth) {
    if (dayOfMonth in 11..13) {
        return "th"
    }
    switch (dayOfMonth % 10) {
        case 1:
            return "st"
        case 2:
            return "nd"
        case 3:
            return "rd"
        default:
            return "th"
    }
}


def logging(msg){
    //log.warn "enablelogging ? $enablelogging" 
    if (enablelogging) log.debug msg
    if(debug && atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptiontext(msg){
    //log.warn "enabledescriptiontext = ${enabledescriptiontext}" 
    if (enabledescriptiontext) log.info msg
}
def disablelogging(){
    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}


