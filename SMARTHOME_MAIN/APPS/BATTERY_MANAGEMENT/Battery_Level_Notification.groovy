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
    name: "Battery level notifications",
    namespace: "elfege",
    author: "elfege",
    description: "Battery level notifications",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {

    page name:"pageSetup"

}
def pageSetup() {

    boolean haveDim = false

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
        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"
        }
        section() {
            label title: "Assign a name", required: false, submitOnChange:true
        }
        section("modes"){
            input "restrictedModes", "mode", title:"Pause this app if location is in one of these modes", required: false, multiple: true
        }

        section(){
            input "devices", "capability.battery", title: "Select your devices", multiple:true
            input "batteryValue", "number", title: "Set a minimum battery value (or leave default value)", defaultValue:40
        }
        section("Notifications"){
            input "notification", "capability.notification", title: "Select notification devices", multiple:true, required:false, submitOnChange: true 
            input "sendAudioAlert", "bool", title: "Send a sound notification", submitOnChange:true
            if(sendAudioAlert)
            {
                input "speech", "capability.speechSynthesis", title: "Select your speech device", multiple:true, required:false, submitOnChange: true 
                input "speaker", "capability.musicPlayer", title: "Select your speakers", multiple:true, required:false, submitOnChange: true 
                if(speaker)
                {
                    input "volumeLevel", "number", title: "Set the volume level", range: "0..100",required:true, submitOnChange: true  
                }
                input "initializeDevices", "bool", title:"Try to fix unresponsive speakers (such as Chrome's)", defaultValue:false

                //input "repeatDelay", "number", title:"repeat this alert after how long?", required:true, description:"time in hours"
            }
        }


        def title = "DEVICES'HEALTH REPORTS"

        section(formatText(title, "white", "grey")){

            title = atomicState.devicesStringWithValue != "" && atomicState.devicesStringWithValue != null ? "${atomicState.devicesStringWithValue}" : "Devices with low battery will show in this blue field"
            paragraph formatText(title, "white", "blue") 

            input "deviceHealth", "bool", title:"Report devices that have been inactive for more than N hours", defaultValue:false, submitOnChange:true 
            if(deviceHealth)
            {
                input "unresponsiveTime", "number", title: "number of hours"
                title = "See the devices that are unresponsive here"
                paragraph formatText(title, "white", "grey")
                title = atomicState.devicesStringWithValueH != "" && atomicState.devicesStringWithValueH != null ? "${atomicState.devicesStringWithValueH}" : "Unresponsive devices will show in this blue field"
                paragraph formatText(title, "white", "blue")
            }
            input "refresh", "bool", title: "Click here to refresh the lists of faulty devices", submitOnChange:true
            app.updateSetting("refresh",[value:"false",type:"bool"])
        }
        section("Schedules")
        {
            input "scheduleReminders", "number", title:"If any device shows low battery, send me a reminder every x hours", required:false, description:"Set time in hours"            
            input "checkData", "bool", title:"Check devices' current values every hour", defaultValue:true, submitOnChange:true
            if(!checkData)
            {
                def m = "Battery Levels Will Be Reported Through Events Only - if one of your device is no longer reporting due to a very low battery level, you won't be notified"
                paragraph formatText(m, "white", "red")
            }
            input "pollDevices6", "bool", title:"Poll all devices every 6 hours (shortens battery life)", defaultValue:false, submitOnChange:true
            input "pollDevices12", "bool", title:"Poll all devices every 12 hours (preferable)", defaultValue:false, submitOnChange:true
            if(pollDevices12)
            {
                app.updateSetting("pollDevices6",[value:"false",type:"bool"])   
            }
            if(pollDevices6)
            {
                app.updateSetting("pollDevices12",[value:"false",type:"bool"])   
            }
        }
        section("logging"){
            input "enablelogging", "bool", title:"Enable logging", value:false, submitOnChange:true
            input "enabledescriptiontext", "bool", title:"Enable description text", value:false, submitOnChange:true
        }
        section(){
            if(atomicState.installed)
            {
                input "update", "button", title: "UPDATE/RESET"
                //input "run", "button", title: "RUN"
                input "checkAll", "button", title: "Get All Devices With Low Values"
                input "checkAllValues", "button", title: "Get All Values For All Devices"
                if(deviceHealth)
                {
                    input "checkAllLastEvent", "button", title: "Devices event time higher than unresponsiveTime hours"
                    input "checkAllLastEventValues", "button", title: "Last event time for all devices"
                }
                input "sendNotifications", "button", title: "Send notifications"
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
    Poll()
}
def initialize() {
    atomicState.installed = true   
    atomicState.messageList = []
    atomicState.messageString = ""

    if(enablelogging == true){
        atomicState.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptionText "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else 
    {
        log.warn "debug logging disabled!"
    }
    logging "Subscribing $devices to battery events"
    subscribe(devices, "battery", mainHandler)
    logging "Building the array"

    initDb(true, true) // intialize DB's

    if(scheduleReminders)
    {
        // sends notification without building new data
        def dTime = scheduleReminders ? scheduleReminders*3600  : 1*3600 
        schedule("0 */$dTime * * * ?", sendNotification) //“At minute 0 past every Nth hour.”
    }
    if(checkData)
    {
        // updates data without sending notifications
        runEvery1Hour(checkAll, [data: false]) 
        descriptionText "checkAll() scheduled to run every hour (getAllValues ? false)"
        if(deviceHealth)
        {
            runEvery1Hour(checkAllDevicesHealth, [data: false])
            descriptionText "checkAllDevicesHealth() scheduled to run every hour (getAllValues ? false)"
        }
    }

    if(pollDevices6)
    {
        // poll / refresh devices
        def t = 6 * 60  // default value, not customizable
        schedule("0 */$t * * * ?", Poll) //“At minute 0 past every Nth hour.”
    }    
    else if(pollDevices12)
    {
        // poll / refresh devices
        def t = 12 * 60// default value, not customizable
        schedule("0 */$t * * * ?", Poll) //“At minute 0 past every Nth hour.”
    }     
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
        case "checkAll":
        atomicState.buttonRequest = true // allows to bypass all restrictions
        checkAll(false)
        atomicState.buttonRequest = false
        break
        case "checkAllValues":
        atomicState.buttonRequest = true // allows to bypass all restrictions
        checkAll(true)
        atomicState.buttonRequest = false
        break
        case "checkAllLastEvent":
        atomicState.buttonRequest = true // allows to bypass all restrictions
        checkAllDevicesHealth(false)
        atomicState.buttonRequest = false
        break
        case "checkAllLastEventValues":
        atomicState.buttonRequest = true // allows to bypass all restrictions
        checkAllDevicesHealth(true)
        atomicState.buttonRequest = false
        break
        case "sendNotifications":
        atomicState.buttonRequest = true // allows to bypass all restrictions
        atomicState.lastNotification = 6*3600*1000 // allow to pass time test
        atomicState.messageList = []
        atomicState.messageSrting = ""
        checkAll(false)
        checkAllDevicesHealth(false)
        sendNotification()
        atomicState.buttonRequest = false
        break

    }
}
def mainHandler(evt){
    descriptionText "$evt.device battery is at ${evt.value}%"
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 

    checkAll(false) // check only values that are below < batteryValue 
    if(deviceHealth)
    {
        checkAllDevicesHealth(false) // check only devices that haven't return any event in more than unresponsiveTime hours
    }
    sendNotification()


}
def checkAll(getAllValues){  // called by java button event
    //log.warn "atomicState.buttonRequest = $atomicState.buttonRequest"
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    initDb(true, false) // always reconstruct the entire db here
    int i = 0
    int s = devices.size()
    def device = null
    def deviceValue = 0
    def db = [:]
    for(s!=0;i<s;i++)
    {
        device = devices[i]
        if(device != null)
        {
            deviceValue = device.currentValue("battery")
            if(deviceValue != null)
            {
                logging "checking battery value for $device. Value = $deviceValue deviceValue.toInteger() <= 100"
                if(deviceValue.toInteger() < batteryValue || getAllValues)
                {
                    logging "updating data for $device with value $deviceValue"
                    db."${device}" = deviceValue
                }
            }

            else
            {
                log.warn "$device has null battery value ($deviceValue)"
                db."${device}" = "NO VALUE!"
            }
        }
    }
    atomicState.array = db
    db = [:] // flush data
    logging "atomicState.array = $atomicState.array"

    logging "database battery values done"
    buildDataString()

}
def checkAllDevicesHealth(getAllValues){  // called by java button event
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    initDb(false, true) // always reconstruct the entire db here
    int i = 0
    int s = devices.size()
    def device = null
    def events = 0
    def dbH = [:]

    def deltaHours = unresponsiveTime*3600*1000

    for(s!=0;i<s;i++)
    {
        device = devices[i]
        if(device != null)
        {
            events = device.eventsSince(new Date(now() - deltaHours)).size() // collect all events within 24 hours
            logging "$device has returned $events events in the last $unresponsiveTime hours"       

            if(events == 0 || getAllValues)
            {
                logging "updating data for $device with value $deviceValue"
                if(events == 0)
                {
                    dbH."${device}" = "unresponsive!"
                }
                else 
                {
                    dbH."${device}" = "OK!"
                }
            }
        }
    }
    atomicState.arrayH = dbH
    dbH = [:] // flush data
    logging "atomicState.arrayH = $atomicState.arrayH"
    logging "database health values done"
    buildDataStringDevicesHealth()
}
def initDb(a, b){
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    logging "a = $a b = $b"
    if(a)
    {
        atomicState.array = [:] // array of devices and battery levels        
        atomicState.devicesString = "" // devices list as string for battery levels
        atomicState.devicesStringWithValue = "" // devices list with values for battery levels
    }
    if(b)
    {
        atomicState.arrayH = [:] // array of devices and time since last event
        atomicState.devicesStringH = "" // devices list as string for last events

        atomicState.devicesStringWithValueH = "" // devices list with values for last event
    }
    logging "database reset done"
}
def buildDataString(){ // simply update database, without sending notifications

    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    logging "building message data"
    atomicState.duplicate = false
    int i = 0
    def listOfDevices = atomicState.array.keySet()
    int s = listOfDevices.size()
    atomicState.s = s
    logging "atomicState.array = $atomicState.array"
    logging "s = $s"

    for(s!=0;i<s;i++)
    {
        atomicState.duplicate = atomicState.devicesString.contains("${listOfDevices[i]}") 

        logging "listOfDevices[i] = ${listOfDevices[i]}"
        if(!atomicState.duplicate)
        {
            atomicState.devicesString += """
${listOfDevices[i]}
"""
            atomicState.devicesStringWithValue += """${listOfDevices[i]}: ${atomicState.array."${listOfDevices[i]}"}%
"""
            logging "atomicState.devicesString: ${atomicState.devicesString}"

            if(atomicState.array."${listOfDevices[i]}" == "NO VALUE!")
            {
                atomicState.messageList += "${listOfDevices[i]}'s is not working properly" // list so we can send one text notification per device
                atomicState.messageString += "${listOfDevices[i]}'s is not working properly" // string for speakers
            }
            else if(atomicState.array."${listOfDevices[i]}"?.toInteger() < batteryValue)
            {
                logging "${listOfDevices[i]}"
                atomicState.messageList += "${listOfDevices[i]}'s battery is low... "// list so we can send one text notification per device
                atomicState.messageString += "${listOfDevices[i]}'s s battery is low..." // string for speakers
            }
        }
        else if(atomicState.devicesString == "")
        {
            logging "empty String"
        }
        else if(atomicState.duplicate)
        {
            logging "duplicate!"
        }
        else 
        {
            log.error "ERROR"
        }

        // log.warn "atomicState.array = $atomicState.array"
    }
    logging """database string battery values done 
atomicState.messageList = $atomicState.messageList
atomicState.messageString = $atomicState.messageString
"""
}
def buildDataStringDevicesHealth(){ // simply update database, without sending notifications
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    atomicState.duplicateH = false
    int i = 0
    def listOfDevices = atomicState.arrayH.keySet()
    int s = listOfDevices.size()
    atomicState.s = s
    logging "atomicState.arrayH = $atomicState.arrayH"
    logging "s = $s"

    for(s!=0;i<s;i++)
    {
        atomicState.duplicateH = atomicState.devicesStringH.contains("${listOfDevices[i]}") 

        logging "listOfDevices[i] = ${listOfDevices[i]}"
        if(!atomicState.duplicateH)
        {
            atomicState.devicesStringH += """
${listOfDevices[i]}
"""
            atomicState.devicesStringWithValueH += """${listOfDevices[i]}: ${atomicState.arrayH."${listOfDevices[i]}"}
"""
            logging """Device = ${listOfDevices[i]}
atomicState.arrayH.{listOfDevices[i]} = ${atomicState.arrayH."${listOfDevices[i]}"}
"""
            if(atomicState.arrayH."${listOfDevices[i]}" == "unresponsive")
            {
                atomicState.messageList += "${listOfDevices[i]} is unresponsive... " // list for text notifications 1 text per device
                atomicState.messageString += "${listOfDevices[i]} is unresponsive... " // text for speakers
            }
        }
        else if(atomicState.devicesStringH == "")
        {
            logging "empty String"
        }
        else if(atomicState.duplicateH)
        {
            logging "duplicate!"
        }
        else 
        {
            log.error "ERROR"
        }
    }
    logging "database string health values done"
} 
def sendNotification(){ // sends notification without building new data
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    def messageList = atomicState.messageList != null ? atomicState.messageList : []
    def messageString = atomicState.messageString != null ? atomicState.messageString : ""

    if(messageList.size() == 0 && messageString.size() == 0) { 
        log.warn "string is empty" 
        return 
    }

    atomicState.lastNotification = atomicState.lastNotification != null ? atomicState.lastNotification : now()

    def dTime = scheduleReminders ? scheduleReminders*3600*1000 : 1*3600*1000

    if(now() - atomicState.lastNotification >= dTime)
    {
        atomicState.lastNotification = now()


        def speakers = speaker ? buildDebugString(speaker) : ""
        def speeches = speech ? buildDebugString(speech) : ""
        def notifDevices = notification ? buildDebugString(notification) : ""

        def notifLogs = "${notification && speaker && speech ? "to ${notifDevices}, ${speakers}, ${speeches}" : notification && speaker ? "to ${notifDevices}, ${speakers}" : notification && speech ? "to ${notifDevices}, ${speechesspeeches}" : speaker && speech ? "to ${speakers}, ${speeches}" : speaker ? "to ${speakers}" : speech ? "to ${speeches}" : ""}" 

        def debugMessage = "message to be sent: '${messageString} ${notifLogs}" 

        descriptionText formatText(debugMessage, "white", "red")

        if(notification) // text notification
        {
            int i = 0
            int s = messageList.size()
            for(i!=0;i<s;i++)
            {
                log.debug "message = ${messageList[i]}"
                notification.deviceNotification(messageList[i])
            }

        }
        else
        {
            descriptionText "User did not select any text notification device"
        }
        if(sendAudioAlert){
            if(speaker || speech)
            {
                if(speaker)
                {
                    if(initializeDevices)
                    {
                        int i = 0
                        int s = speaker.size()
                        def device 
                        for(s!=0;i!=s;i++)
                        {
                            device = speaker[i]
                            if(device.hasCommand("initialize"))
                            {
                                logging "Initializing $device (speaker)"
                                device.initialize()
                                logging "wainting for 1 second"
                                pauseExecution(1000)
                            }
                        }
                    }
                    speaker.setLevel(volumeLevel)
                    pauseExecution(500)
                    speaker.playTextAndRestore(messageString)
                }
                else if(speech)
                {
                    if(initializeDevices)
                    {
                        int i = 0
                        int s = speech.size()
                        def device 
                        for(s!=0;i!=s;i++)
                        {
                            device = speech[i]
                            if(device.hasCommand("initialize"))
                            {
                                logging "Initializing $device (speech)"
                                device.initialize()
                                logging "wainting for 1 second"
                                pauseExecution(1000)
                            }
                        }
                    }
                    log.debug "sending $messageString to $speech"
                    speech.speak(messageString)    
                }
            }
        }
        else if(!sendAudioAlert)
        {
            descriptionText "Sound notifications disabled at user's request"   
        }
    }
    else 
    {
        descriptionText "last notification sent less than ${dTime/3600/1000} ${scheduleReminders?.toInteger() > 1 ? "hours":"hour"} ago"
    }

    atomicState.messageList = []
    atomicState.messageString = ""
}
def buildDebugString(deviceList){
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    def devices = ""
    int i = 0 
    int s = deviceList.size()
    if(s != 0) { 

        for(s!=0; i!=s; i++)
        {
            devices += "${deviceList[i]}, "   
        }

    }

    return devices
}
def Poll(){
    if(atomicState.paused && !atomicState.buttonRequest) return
    if(location.mode in restrictedModes && !atomicState.buttonRequest){
        descriptionText "location in restricted mode, doing nothing"
        return
    } 
    int i = 0
    int s = devices.size()
    for(s!=0;i<s;i++)
    {
        def dev = devices[i]
        if(dev.hasCommand("Poll") || dev.hasCommand("poll"))
        {
            dev.poll()
            descriptionText("polling $dev")
        }
        if(dev.hasCommand("Refresh") || dev.hasCommand("refresh"))
        {
            dev.refresh()
            descriptionText("refreshing $dev")
        }
    }
}
def logging(msg){
    //log.warn "enablelogging ? $enablelogging" 
    if (enablelogging) log.debug msg
    if(debug && atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptionText(msg){
    //log.warn "enabledescriptiontext = ${enabledescriptiontext}" 
    if (enabledescriptiontext) log.info msg
}
def disablelogging(){
    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}
def formatText(title, textColor, bckgColor){
    return  "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}
