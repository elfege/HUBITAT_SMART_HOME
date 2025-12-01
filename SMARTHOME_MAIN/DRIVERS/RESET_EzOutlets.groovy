/*
* Http GET Switch
*
* Calls URIs with HTTP GET for switch on or off
* 
*/
metadata {
    definition(name: "RESET EzOutlet2 over Http", namespace: "community", author: "Community", importUrl: "https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/httpGetSwitch.groovy") {

        //capability "Button"
        capability "Switch"

        command "reset"
        command "toggle"
        //command "poweroff"
        command "test"
        command "setManual"
    }
}

preferences {
    section("URIs") {
        input "toggleURI", "text", title: "toggle URI", required: false
        input "resetURI", "text", title: "Reset URI", required: false
        input "testURI", "text", title: "Test URI", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}


def reset()
{
    if (logEnable) log.debug "Sending RESET request to [${settings.resetURI}]"

    if(state.switch == "off") // if off reset won't work on EzOutlet2
    {
        toggle()
        pauseExecution(1000)
    }
    sendGet(resetURI)
}

def on()
{
    if(state.switch == "off")
    {
        toggle()
    }
    else 
    {
        log.debug "already on"
    }
}

def poweroff()
{
    if(state.switch == "on")
    {
        toggle()
    }
    else 
    {
        log.debug "already off"
    }
}
def off()
{

    if(state.switch == "on")
    {
        toggle()
    }
    else 
    {
        log.debug "already off"
    }
}
def toggle() {

    sendGet(toggleURI)
}

def test()
{
    runCmd()
}

def parse(String description) {
    def result = ""

    log.debug "Parsing '${description}'"
    def msg = parseLanMessage(description)
    def headerString = msg.header

    def bodyString = msg.body
    log.debug "${msg}"

    if (!headerString) {
        log.debug "headerstring was null for some reason :("
    }


    log.debug "bodyString ================================= $bodyString"

    if(bodyString){

        def parts = bodyString.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>1?parts[1].trim():null

        log.debug "bodyString = $bodyString ||| parts = $parts"

        log.debug "name: $name, value: $value"

        result = createEvent(name: name, value: value)


    }
    return result
}




def sendGet(uri) // sends request to ESP8266 which serves as redundency to reset the Atmega in case of total failure
{
    log.debug "sending $uri"
    def pa = ""

    def reqParams = [
        uri: "http://${uri}"
    ]
    try {
        httpGet(reqParams){ resp ->
            if (resp.success) {

             /*   if(resp.data == "1,0")
                {
                    log.debug "sending on event"
                    sendEvent(name: "switch", value: "on", isStateChange: true)
                    state.switch = "on"
                }
                else if(resp.data == "0,0")
                {
                    log.debug "sending off event"
                    sendEvent(name: "switch", value: "off", isStateChange: true)
                    state.switch = "off"
                }
                if(uri == resetURI) 
                {
                    state.switch = "on"
                    log.debug "sending reset event"
                    sendEvent(name: "switch", value: "reset", isStateChange: true)
                    sendEvent(name: "switch", value: "on", isStateChange: true)
                }
*/
                log.info "resp.data = $resp.data state.switch = ${state.switch}"

            }
            if (resp.data) log.debug "${resp.data}"
            pa = resp.data.toString()

        }
    } catch (Exception e) {
        log.warn "Call to $uri failed: ${e.message}"
        sendEvent(name: "switch", value: "failed!", isStateChange: true)
    }
}



def setManual()
{
    
    sendGet("192.168.10.132/control.cgi?target=1&control=2")
}


def runCmd(String ip, String port) {
    //def localDevicePort = (port==null) ? "80" : port
    def path = "/" 
    def body = "${Manual()}"
    if(deviceBody) body = deviceBody
    def headers = [:] 
    headers.put("HOST", "${ip}:${port}")
    headers.put("Content-Type", deviceContent)

    try {
        def hubAction = new hubitat.device.HubAction(
            method: "POST",
            path: path,
            body: body,
            headers: headers
        )
        log.debug hubAction
        return hubAction
    }
    catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }  
}