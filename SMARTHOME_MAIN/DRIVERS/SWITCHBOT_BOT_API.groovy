/*
* SwitchBot Open API - Bot
*
* Uses the SwitchBotAPI to control a SwitchBot bot.
*
* Prerequisites:
* - The SwitchBot needs to be cloud enable via a hub to make this work.
* - You need to have a OpenToken from SwitchBot
* - You need to know the DeviceID of the Bot you would like to control
*

GITHUB REPO: https://github.com/toffehoff/hubitat/tree/main/Drivers/api-bot
*/

metadata {
    definition(name: "SwitchBot Bot - API", namespace: "elfege", author: "ToffeHoff & elfege", importUrl: "https://raw.githubusercontent.com/toffehoff/hubitat/main/Drivers/api-bot/switchBot-Bot.groovy") {
        capability "Switch"
        capability "Actuator"
        capability "Momentary"
        capability "PowerMeter"

        command "configure"
        command "refresh"
        command "refreshPowerMeter"
    }
}

preferences {
    section("URIs") {
        input name: "openToken", type: "string", title: "Your SwitchBot Open Token", required: true
        input name: "deviceId", type: "string", title: "Device ID of the bot", required: true
        input name: "switchBotMode", type: "enum", title: "Device mode of the bot", options: ["Press","Switch"], defaultValue: "Press", required: true
        input name: "logEnable", type: "bool", title: "Enable Debug", defaultValue: true
        input name: "warnEnable", type: "bool", title: "Enable Warnings", defaultValue: true

    }
    section("Power Measurement Device")
    {
        input name: "powerDevId", type: "string", title:"Power Measurement Capable Device ID", required:false
        input name: "AppNumber", type: "string", title: "Maker API's app number"
        input name: "MakerApiToken", type: "string", title: "Maker API's token"
        input name: "localHub", type: "string", title: "Local Hub IP address", defaultValue:"lochalhost:8080"
        input name: "powerThres", type: "float", title: "Power threshold (expected power value when device is off)", defaultValue:0
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def configure(){
    updated()
}
def updated() { 
    unschedule()
    log.warn "debug logging is: ${logEnable == true}"
    schedule("0 0/1 * * * ?", refresh) 
    if (logEnable) runIn(1800, logsOff)
    log.info "updated..."
}

def parse(String description) {
    if (logEnable) log.debug(description)
}

def push() {

    def postBody = '{ "command":"press" }'

    if (logEnable) log.debug "Sending push POST request to device [${deviceId}] with message [${postBody}]"

    try {
        httpPost([
            uri: "https://api.switch-bot.com/v1.0/devices/${deviceId}/commands",
            headers: [
                "Content-type": "application/json; charset=utf8",
                "Authorization": "${openToken}"
            ],
            body: postBody
        ]) { resp ->
            if (logEnable)
            if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        if (warnEnable) log.warn "Call to on failed: ${e.message}"
    }
    runIn(5, refresh)
}

def on() {

    // if ( device.currentValue("switch") == "on" ) {
    //     if (logEnable) log.debug "Getting on request - Switch already on - no action required"
    // } else {

        def postBody = '{ "command":"press" }'
        if ( switchBotMode == "Switch" ) postBody = '{ "command":"turnOn" }'

        if (logEnable) log.debug "Sending on POST request to device [${deviceId}] with message [${postBody}]"

        try {
            httpPost([
                uri: "https://api.switch-bot.com/v1.0/devices/${deviceId}/commands",
                headers: [
                    "Content-type": "application/json; charset=utf8",
                    "Authorization": "${openToken}"
                ],
                body: postBody
            ]) { resp ->
                if (resp.success) {
                    sendEvent(name: "switch", value: "on", isStateChange: true)
                }
                if (logEnable)
                if (resp.data) log.debug "${resp.data}"
            }
        } catch (Exception e) {
            log.warn "Call to on failed: ${e.message}"
        }
    // }
    sendEvent(name: "switch", value: "on", isStateChange: true)
    runIn(30, refreshPowerMeter)
}

def off() {

    // if ( device.currentValue("switch") == "off" ) {

    //     if (logEnable) log.debug "Getting off request - Switch already off - no action required"

    // } else {

        def postBody = '{ "command":"press" }'
        if ( switchBotMode == "Switch" ) postBody = '{ "command":"turnOff" }'

        if (logEnable) log.debug "Sending off POST request to device [${deviceId}] with message [${postBody}]"

        try {
            httpPost([
                uri: "https://api.switch-bot.com/v1.0/devices/${deviceId}/commands",
                headers: [
                    "Content-type": "application/json; charset=utf8",
                    "Authorization": "${openToken}"
                ],
                body: postBody
            ]) { resp ->
                if (resp.success) {
                    sendEvent(name: "switch", value: "off", isStateChange: true)
                }
                if (logEnable)
                if (resp.data) log.debug "${resp.data}"
            }
        } catch (Exception e) {
            log.warn "Call to on failed: ${e.message}"
        }
    // }
    sendEvent(name: "switch", value: "off", isStateChange: true)
    runIn(30, refreshPowerMeter)
}

def refresh(){
    log.info "refresh()"
    def powerValue = getPowerValue()
    def threshold = powerThres as float
   
    if(logEnable) log.info "power value is: "+powerValue
    if(logEnable) log.info "power threshold is: "+threshold
    if(powerValue)
    {
        
        if(powerValue >= threshold)
        {
            sendEvent(name: "switch", value: "on", isStateChange: true)
        }
        else 
        {
            sendEvent(name: "switch", value: "off", isStateChange: true)
        }
    }
}

def refreshPowerMeter(){

    if(powerDevId && AppNumber && MakerApiToken)
    {
        def uri = "http://"+localHub+"/apps/api/"+AppNumber+"/devices/"+powerDevId+"/refresh?access_token="+MakerApiToken
        log.debug "refresh uri:::: "+uri
        // `http://${ip}/apps/api/${appNumber}/devices/${device.id}/refresh?access_token=${access_token}`;

        if (logEnable) log.debug uri

        def DATA = []
        def value = null
        def name = null
        try {
            httpGet(uri) { resp ->
                if (resp.success) {  
                    if (logEnable) log.debug "resp.data = $resp.data"
                }
            }
            sendEvent(name: "refreshPower", value: "ok")
        } catch (Exception e) {
            log.warn "getPowerValue URI HttpGet call failed: ${e.message}"
            sendEvent(name: "refreshPower", value: "API ERROR")
        }


    }
    runIn(3, refresh)

}

def getPowerValue(){
    log.warn """
powerDevId = $powerDevId
AppNumber = $AppNumber
MakerApiToken = $MakerApiToken
"""
    if(powerDevId && AppNumber && MakerApiToken)
    {
        def uri = "http://"+localHub+"/apps/api/"+AppNumber+"/devices/"+powerDevId+"/events?access_token="+MakerApiToken

        if (logEnable) log.debug uri

        def DATA = []
        def value = null
        def name = null
        try {
            httpGet(uri) { resp ->
                if (resp.success) {  
                    if (logEnable) log.debug "resp.data = $resp.data"
                    DATA = resp.data
                    def powerVal = DATA.find{element -> element.'name' == 'power'}.value
                    powerVal = powerVal.toFloat()
                    if (logEnable) log.debug "********power: "+powerVal+" Watts"
                    sendEvent(name: "power", value: powerVal)
                    return powerVal     
                }
                if (resp.data && logEnable) log.debug "${resp.data}"
            }
        } catch (Exception e) {
            log.warn "getPowerValue URI HttpGet call failed: ${e.message}"
            sendEvent(name: "power", value: "API ERROR")
        }


    }
    else 
    {
        log.warn "power null value!"
        return null
    }
}