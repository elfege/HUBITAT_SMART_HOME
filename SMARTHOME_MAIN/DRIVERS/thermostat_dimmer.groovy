/*
*	dimmer that is also recognized by alexa as thermostat so it can be set event when the actual thermostat is off

/// ORIGINAL MUST ALWAYS BE ON HUB1 !!!!!!!!!!!!!!!!!!!!!

NB : MUST NEVER BE SET TO AUTO OR ALEXA WILL RETURN "Hum... deviceName is not responding" JAN 2025: STILL TRUE? 

*/
/** 
 * Last Updated: 2025-01-27
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
        command "setThermostatFanMode"
        command "setHeatingSetpoint"
        command "setCoolingSetpoint"
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
    state.max_value = 130
    log.debug "state.max_value: $state.max_value"
    // setLevel(state.value)
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
    // log.warn "this device must never be set to any other mode than 'heat'"
   sendEvent(name: "thermostatMode",value: "heat")
}

def parse(String description) // no parse in virtual device
{
    log.trace "Msg: Description is $description"

}

def on() {
    log.info "on()"
    sendEvent(name: "switch", value: "on")
    // setToHeat()// NEVER AUTO NEVER OFF !! 
}
def off() {
    log.info "off()"
    sendEvent(name: "switch", value: "off")  // always on
    sendEvent(name: "switch", value: "on")
    // setToHeat() // NEVER AUTO NEVER OFF !! 
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
def setHeatingSetpoint(value) {
    if (value == null) return
    state.level = value.toString().toFloat().round()
    setLevel(state.level)
}
def setCoolingSetpoint(value) {
    if (value == null) return
    state.level = value.toString().toFloat().round()
    setLevel(state.level)
}
def setThermostatFanMode(fanMode) {
    return
}
def setLevel(value) {
    if (value == null) return
    
    log.info "setLevel $value"
    
    def max_value = state.max_value ?: 130
    log.debug "max_value (setLevel): $max_value"
    
    value_float = value.toString().toFloat()
    
    if (value_float > max_value.toFloat()) {
        log.warn "Value exceeds maximum limit of ${max_value}, setting to max"
        value_float = max_value.toFloat()
    }
    
    state.level = value
    sendEvent(name: "level", value: value.toString())
    sendEvent(name: "thermostatSetpoint", value: value_float.toString())
    sendEvent(name: "coolingSetpoint", value: value_float.toString())
    sendEvent(name: "heatingSetpoint", value: value_float.toString())


log.warn "- device.currentValue('thermostatSetpoint') != value_float: ${device.currentValue('thermostatSetpoint') != value_float} -- value_float=$value_float device's val: ${device.currentValue('thermostatSetpoint')}"
log.warn "- device.currentValue('coolingSetpoint') != value_float: ${device.currentValue('coolingSetpoint') != value_float} -- value_float=$value_float device's val: ${device.currentValue('coolingSetpoint')}"
log.warn "- device.currentValue('heatingSetpoint') != value: ${device.currentValue('heatingSetpoint') != value_float} -- value_float=$value_float device's val: ${device.currentValue('heatingSetpoint')}"

    // bad idea... lol
    // if (device.currentValue("thermostatSetpoint") != value_float) device.setThermostatSetPoint(value_float)
    // if (device.currentValue("coolingSetpoint") != value_float) device.setCoolingSetpoint(value_float)
    // if (device.currentValue("heatingSetpoint") != value_float) device.setHeatingSetpoint(value_float)
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




