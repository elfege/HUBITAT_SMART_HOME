/*
*	dimmer that is also recognized by alexa as thermostat so it can be set event when the actual thermostat is off

/// ORIGINAL MUST ALWAYS BE ON HUB1 !!!!!!!!!!!!!!!!!!!!!

NB : MUST NEVER BE SET TO AUTO OR ALEXA WILL RETURN "Hum... deviceName is not responding"

*/


metadata 
{
    definition(name: "Thermostat Dimmer", namespace: "elfege", author: "elfege") 
    {
        capability "Refresh"
        capability "Thermostat" 
        capability "ChangeLevel"
        capability "Switch"
        capability "Switch Level"
        capability "SwitchLevel"
        attribute "version", "string"

        command "configure"
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
        command "setThermostatSetPoint"
    }
}



def installed()
{
    initialize()
}

def updated()
{
    initialize()
}

def configure(){
    initialize()
}

def initialize()
{
    unschedule()
    state.level = state.level != null ? state.level : 73
    state.level = value.toString()
    setLevel(state.value)
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "thermostatMode",value: "heat") // NEVER AUTO NEVER OFF !!
    schedule("0 0/10 * * * ?", setToHeat) 
}
def refresh()
{
    initialize()
}

def setToHeat()
{
    log.warn "this device must never be set to any other mode than 'heat'"
   sendEvent(name: "thermostatMode",value: "heat")
}

def parse(String description) // no parse in virtual device
{
    log.trace "Msg: Description is $description"

}

def on() {
    log.info "on()"
    sendEvent(name: "switch", value: "on")
    ssetToHeat()// NEVER AUTO NEVER OFF !! 
}
def off() {
    log.info "off()"
    sendEvent(name: "switch", value: "off")  // always on
    sendEvent(name: "switch", value: "on")
    setToHeat() // NEVER AUTO NEVER OFF !! 
}

def raiseSetpoint() {
    log.debug("raising setpoint ++")
    state.level = state.level + 1
    def val = state.level + 1
    setLevel(val)
}
def lowerSetpoint() {
    log.debug("lowering setpoint --")
    state.level = state.level - 1
    def val = state.level - 1
    setLevel(val)
}
def setThermostatSetpoint(value){
    state.level = value.toInteger()
    setLevel(value)
}
def setHeatingSetpoint(value){
    state.level = value.toInteger()
    setLevel(value)
}
def setCoolingSetpoint(value){
    state.level = value.toInteger()
    setLevel(value)
}
def setLevel(value)
{
    log.info "setLevel $value"
    
    // Ensure that the level does not exceed 110%
    if (value > 110) {
        log.warn "Value exceeds the maximum limit of 110%, setting to 110%"
        value = 110
    } 
    
    state.level = value.toInteger()
    sendEvent(name: "level", value: value.toString())
}
def cool()
{
    setThermostatMode("cool")
}
def heat()
{
    setThermostatMode("heat")
}
def auto()
{
    setThermostatMode("auto")
}
def setThermostatMode(cmd)
{
    log.info "setThermsotatMode $cmd"
    sendEvent(name: "thermostatMode", value: cmd.toString())
    setToHeat()
}




