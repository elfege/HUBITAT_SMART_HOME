/**
AC (SPLIT AC) VIRTUAL THERMOSTAT


*/

metadata {
    definition(name: "SPLIT AC THERMOSTAT", namespace: "elfege", author: "elfege") {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        capability "Switch Level"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Thermostat"
        capability "Battery"
        capability "Refresh"
        capability "Sensor"
        capability "Health Check"
        capability "ThermostatFanMode"
        capability "ThermostatMode"

        command "SetTemp"
        command "setThermostatMode"
        command "switchMode"
        command "switchFanMode"
        command "lowerHeatingSetpoint"
        command "raiseHeatingSetpoint"
        command "lowerCoolSetpoint"
        command "raiseCoolSetpoint"
        command "lowerSetpoint"
        command "raiseSetpoint"
        command "poll"
        command "ToggleLED"
        command "setThermostatSetPoint"
        command "cool"
        command "heat"
        command "fanOn"
        command "auto"
        command "fanHigh"
        command "fanMed"
        command "fanLow"
        command "turbo"
        command "reset"
        command "off"
        command "on"
        command "getTemperature"
        command "getHumidity"

        attribute "heatingSetpoint", "string"
        attribute "coolingSetpoint", "string"
        attribute "thermostatSetpoint", "string"
        attribute "headline", "string"
        attribute "thermostatMode", "string"
        attribute "fanSpeed", "string"
        attribute "low", "string"
        attribute "medium", "string"
        attribute "high", "string"
        attribute "lastUpdated", "String"
        attribute "supportedThermostatFanModes", "String"
        attribute "supportedThermostatModes", "String"
    }

    preferences {
        input "ip", "text", title: "Arduino IP Address", description: "ip", required: true, displayDuringSetup: true
        input "port", "text", title: "Arduino Port", description: "port", required: true, displayDuringSetup: true
        input "mac", "text", title: "Arduino MAC Addr", description: "mac", required: true, displayDuringSetup: true
        input name: "enabledebug", type: "bool", title: "Enable debug logging?", defaultValue: true
        input name: "enabledescription", type: "bool", title: "Enable description text?", defaultValue: true


        input "tempSensor", "text", title: "Maker API device number for a temperature sensor", required: false
        input "humSensor", "text", title: "Maker API device number for a humidity sensor", required: false
        input "IP", "text", title: "Ip address (xxx.xxx.xxx.xxx)", required: false
        input "APInumber", "text", title: "Maker API's number", required: false
        input "accessToken", "text", title: "Access Token", required: false
        input "refreshRate", "number", title: "Refresh Rate (in minutes)", required: false, defaultValue: 5

    }
}

// parse events into attributes
def parse(String description) {

    logging("Parsing '${description}'")

    state.enableLoggingTime = state.enableLoggingTime == null ? now() : state.enableLoggingTime
    state.lastEvent = state.lastEvent ? state.lastEvent : now()
    state.lastEventName = state.lastEventName ? state.lastEventName : ""
    state.lastEventValue = state.lastEventValue ? state.lastEventValue : ""

    if (enablelogging && now() - state.enableLoggingTime > 30 * 60 * 1000) {
        disablelogging()
    }


    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (!headerString) {
        // logging("headerstring was null for some reason :(")
    }
    def bodyString = msg.body

    logging("bodyString: $bodyString")


    if (!bodyString) {
        logging("bodyString is null for some reason")
    }
    else {
        def parts = bodyString.split(" ")
        def name = parts.length > 0 ? parts[0].trim() : null
        def value = parts.length > 1 ? parts[1].trim() : null

        boolean isStateChanged = false

        def timeThreshold = 20000 // now() - state.lastEvent > timeThreshold  must take precedence to prevent filling the event queue

        if (now() - state.lastEvent > timeThreshold && (isStateChanged || state.refreshRequest)) {
            state.lastEventName = name
            state.lastEventValue = value
            state.lastEvent = now()
            state.refreshRequest = false

            logging("$name, $value")
            try {

                sendEvent(name: name, value: value)

                // no feedback from the unit since this is an infra-red controller, so default operatingState value to mode's value
                if (value in ["heat", "cool"]) {
                    sendEvent(name: "thermostatOperatingState", value: "${value}ing")

                }
                else if (value == "fanCirculate") {
                    sendEvent(name: "thermostatOperatingState", value: value)
                }
                else {
                    sendEvent(name: "thermostatOperatingState", value: "idle")
                }
            }
            catch (Exception e) {
                log.error "CUST. ERR. MSG. (try/catch): $e"
            }
        }
        else {
            logging "duplicate event less than ${timeThreshold/1000} seconds apart, skipping"
        }
    }
}

private getHostAddress() {
    def ip = settings.ip
    def port = settings.port

    logging("Using ip: ${ip} and port: ${port} for device: ${device.id}")
    return ip + ":" + port
}
def sendEthernet(message) {
    logging("Executing 'sendEthernet' ${message}")
    new hubitat.device.HubAction(
        method: "POST",
        path: "/${message}?",
        headers: [HOST: "${getHostAddress()}"]
    )
}

def initialize() {
    sendEvent(name: "supportedThermostatFanModes", value: getSupportedThermostatFanModes().join(", "))
    sendEvent(name: "supportedThermostatModes", value: getSupportedThermostatModes().join(", "))
}

// handle commands
def on() {

    //sendEvent(name: "switch", value: "on") // event sent by ESP
    logging("Executing 'switch on'")

    sendEthernet("on")

}
def off() {

    //sendEvent(name: "switch", value: "off") // event sent by ESP

    logging("Executing 'thermostatMode off'")
    //sendEvent(name: "switch", value:"off")
    state.currentState = "off"
    sendEthernet("off")
}
def ToggleLED() {
    sendEthernet("Ledtoggle")
}
def setThermostatFanMode(String value){
    logging "setThermostatFanMode $value"
    if (value == "on") {
        logging "fanOn()"
        fanOn()
    }
    else {
        logging "fanOff()"
        fanAuto()
    }
}
def setThermostatMode(String value) {

    logging("new ThermostatMode = $value ")

    sendEthernet(value)
}
def fanOn() {
    //sendEvent(name:"thermostatFanMode", value:"on")
    logging("Executing 'fan on'")
    sendEthernet("fanOn")
}
def fanAuto(){
    logging("Executing 'fan auto'")
    //sendEvent(name:"thermostatFanMode", value:"auto")
    sendEthernet("fanAuto")
}  

def raiseSetpoint() {

    logging("raising setpoint ++")
    sendEthernet("raiseSetpoint")

}
def lowerSetpoint() {

    logging("lowering setpoint --")
    sendEthernet("lowerSetpoint")
}
def raiseHeatingSetpoint() {

    logging("sending heat raiseTemp")
    sendEthernet("raiseHeatingSetpoint")


}
def lowerHeatingSetpoint() {

    logging("sending heat lowerTemp")

    sendEthernet("lowerHeatingSetpoint")


}
def lowerCoolSetpoint() {
    logging("sending cool raiseTemp")
    sendEthernet("lowerCoolSetpoint")


}
def raiseCoolSetpoint() {

    logging("sending cool lowerTemp")
    sendEthernet("raiseCoolSetpoint")

}
def setHeatingSetpoint(cmd) {

    logging("HEATING")
    state.lastHeatSetpoint = cmd.toInteger()
    //sendEvent(name: "switch", value: "on")
    //sendEvent(name: "thermostatSetpoint", value: cmd)
    sendEthernet("setHeatingSetpoint${cmd.toInteger()}") // will effectively change on the controller if and only if current mode corresponds

}
def setLevel(value){
    sendEthernet("level${value.toString()}")
}
def setCoolingSetpoint(cmd) {


    logging("COOLING")
    //sendEvent(name: "coolingSetpoint", value: cmd) // event should be triggered by device's feedback

    state.lastCoolSetpoint = cmd.toInteger()
    //sendEvent(name: "switch", value: "on")
    //sendEvent(name: "thermostatSetpoint", value: cmd)
    sendEthernet("setCoolSetpoint${cmd.toString()}")

}
def cool() {

    // if(state.currentState != "cool"){
    logging("Executing 'cool'")
    //
    //sendEvent(name: "thermostatMode", value: "cool")// event sent by ESP32
    //
    state.currentState = "cool"
    sendEthernet("cool")

}

def heat() {

    //if(state.currentState != "heat"){
    logging("Executing 'heat'")
    //
    //sendEvent(name: "thermostatMode", value: "heat") // event sent by ESP32
    //
    state.currentState = "heat"
    sendEthernet("heat")

}
def auto() {
    logging("Executing 'auto'")
    //
    //sendEvent(name: "switch", value: "on") // event sent by ESP32
    //
    //sendEvent(name: "thermostatMode", value: "auto")// event sent by ESP32
    sendEthernet("auto")
}

def turbo() {
    logging("Executing 'turbo'")
    //sendEvent(name: "turbo", value: "true")
    sendEthernet("turbo")
}
def fanHigh() {
    logging("FAN HIGH CMD")
    event sent by ESP32 //sendEvent(name: "FANSPEED", value: "fanhigh")
    //sendEvent(name: "fanSpeed", value: "high")
    sendEthernet("fanhigh")
}
def fanMed() {
    logging("FAN MED CMD")
    event sent by ESP32 //sendEvent(name: "FANSPEED", value: "fanmed")
    //sendEvent(name: "fanSpeed", value: "medium")
    sendEthernet("fanmed")
}
def fanLow() {
    logging("FAN LOW CMD")

    //sendEvent(name: "fanSpeed", value: "low")
    sendEthernet("fanlow")
}

def getTemperature(){

    if (!IP || !APInumber || !tempSensor) {
        log.warn "missing device... getTemperature() canceled"
        return
    }

    def uri = "http://" + IP + "/apps/api/" + APInumber + "/devices/" + tempSensor + "?access_token=" + accessToken

    //http://192.168.10.69/apps/api/1035/devices/1835?access_token=ae3f6fe1-10d5-44b9-841c-81ef66260314
    //http://192.168.10.69/apps/api/1035/devices/1835?access_token=ae3f6fe1-10d5-44b9-841c-81ef66260314
    logging "Sending  URI request to $uri"

    def value = null
    def name = null
    try {
        httpGet(uri) {
            resp ->
            if (resp.success) {
                // log.debug "resp.data = $resp.data"
                def attributes = resp.data.attributes
                value = attributes.find{ it -> it.name == "temperature" }.currentValue
                log.info "temperature: ${value}"
                sendEvent(name: "temperature", value: value)
            }
            if (resp.data) logging "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "getTemperature() URI HttpGet call failed: ${e.message}"
        sendEvent(name: "temperature", value: "API ERROR")
    }

    return value == null ? "72" : value
}

def getSupportedThermostatFanModes() {
    return ["auto", "on", "circulate"]
}

def getSupportedThermostatModes() {
    return ["off", "heat", "cool", "auto", "emergency heat"]
}

def getHumidity(){
    if (!IP || !APInumber || !humSensor) {
        log.warn "missing device... getHumidity() canceled"
        return
    }

    def uri = "http://" + IP + "/apps/api/" + APInumber + "/devices/" + humSensor + "?access_token=" + accessToken

    logging "Sending  URI request to $uri"

    def value = null
    def name = null
    try {
        httpGet(uri) {
            resp ->
            if (resp.success) {  
                logging "${resp.data}"
                def attributes = resp.data.attributes
                value = attributes.find{ it -> it.name == "humidity" }.currentValue
                log.info "humidity: ${value}"
                sendEvent(name: "humidity", value: value)
            }
        }
    } catch (Exception e) {
        log.warn "getHumidity URI HttpGet call failed: ${e.message}"
        sendEvent(name: "humidity", value: "API ERROR")
    }

    return value == null ? "50" : value
}
def getsupportedattributes(){
    def a = ["heatingSetpoint", "coolingSetpoint", "coolingSetpoint", "thermostatSetpoint", "thermostatMode", "fanSpeed", "low", "medium", "high", "lastUpdated"]
    return a
}

def refresh() {
    state.lastRefresh = state.lastRefresh == null ? now() : state.lastRefresh
    state.lastTemperature = state.lastTemperature == null ? getTemperature() : state.lastTemperature
    state.lastHumidity = state.lastHumidity == null ? getHumidity() : state.lastHumidity

    refreshInterval = 2 * 60 * 1000
    log.debug("${device.displayName} | Refresh()'s next request temperature and humidity will take place in ${(refreshInterval - (now() - state.lastRefresh))/1000} seeconds")
    
    state.refreshRequest = true
    sendEthernet("refresh")

    if (now() - state.lastRefresh >= refreshInterval) {
        if (tempSensor) {
            state.lastTemperature = getTemperature()
        }
        if (humSensor) {
            state.lastHumidity = getHumidity()
        }
    }
}

def poll(){
    refresh()
}
def configure() {
    initialize()
    logging("Executing 'configure'")
    state.lastDeclaredEvent = now()
    logging("state.lastDeclaredEvent = ${now()}")
    state.lastDeclaredEvent = state.lastDeclaredEvent ? state.lastDeclaredEvent : now()
    state.lastEvenName = state.lastEvenName ? state.lastEvenName : ""
    state.lastEventValue = state.lastEventValue ? state.lastEventValue : ""


    updated()
}
def updateDeviceNetworkID() {
    logging("Executing 'updateDeviceNetworkID'")
    if (device.deviceNetworkId != mac) {
        logging("setting deviceNetworkID = ${mac}")
        device.setDeviceNetworkId("${mac}")
    }
}
def updated() {
    unschedule()
    schedule("0 0/$refreshRate * * * ?", refresh)
    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
        state.updatedLastRanAt = now()
        log.info("Executing 'updated'")
        runIn(3, updateDeviceNetworkID)
    }
    else {
        log.trace "updated(): Ran within last 5 seconds so aborting."
    }
    if (enablelogging) {
        runIn(1800, disablelogging)
        descriptionText("disablelogging scheduled to run in ${1800/60} minutes")
    }


}
def reset() {
    logging("Executing 'reset'")
    sendEthernet("reset")
}
def logging(String message){
    if (enabledebug) {
        log.debug message
    }
}
def descriptionText(String message){
    if (enabledescription) {
        log.info message
    }
}
def disablelogging(){
    device.updateSetting("enabledebug", [value: "false", type: "bool"])
    log.warn "logging disabled!"
}