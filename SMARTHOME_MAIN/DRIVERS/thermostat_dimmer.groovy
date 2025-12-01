/**
 * Thermostat Dimmer Driver
 * 
 * Purpose:
 * This driver creates a virtual thermostat that's recognized by Alexa as both a thermostat and a dimmer.
 * It allows temperature control even when the physical thermostat is off, making it ideal for voice control
 * and automation scenarios.
 * 
 * Key Features:
 * - Maintains compatibility with Alexa for voice control
 * - Functions as both thermostat and dimmer
 * - Stays in "heat" mode to ensure Alexa compatibility
 * - Supports temperature range of 0-130°F
 * 
 * Important Notes:
 * - MUST NEVER BE SET TO AUTO OR ALEXA WILL RETURN "Hum... deviceName is not responding"
 * - Original must always be on HUB1
 * 
* Last Updated: 2025-01-28
 * Author: elfege
 */

metadata {
    definition(name: "Thermostat Dimmer", namespace: "elfege", author: "elfege") {
        // Core capabilities required for thermostat and dimmer functionality
        capability "Refresh"              // Allows refreshing of device state
        capability "Thermostat"           // Core thermostat functionality
        capability "ChangeLevel"          // Required for dimmer control
        capability "Switch"               // Basic on/off functionality
        capability "Switch Level"         // Dimmer level control
        capability "SwitchLevel"          // Additional dimmer compatibility
        
        // Custom attribute for version tracking
        attribute "version", "string"

        // Commands supported by this device
        command "configure"                           // Initial setup
        command "setThermostatSetpoint", ["number"]  // Set main temperature
        command "setThermostatMode", ["string"]      // Set mode (heat/cool)
        command "switchMode"                         // Toggle between modes
        command "switchFanMode"                      // Change fan operation
        command "lowerHeatingSetpoint"               // Decrease heating temp
        command "raiseHeatingSetpoint"               // Increase heating temp
        command "lowerCoolSetpoint"                  // Decrease cooling temp
        command "raiseCoolSetpoint"                  // Increase cooling temp
        command "lowerSetpoint"                      // Generic decrease temp
        command "raiseSetpoint"                      // Generic increase temp
        command "setThermostatFanMode", ["string"]   // Set fan mode
        command "setHeatingSetpoint", ["number"]     // Set heating temp
        command "setCoolingSetpoint", ["number"]     // Set cooling temp
    }
}

/**
 * Initialization Methods
 */

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def configure() {
    initialize()
}

/**
 * Primary initialization method
 * Sets up initial state and schedules regular heat mode enforcement
 */
def initialize() {
    unschedule()  // Clear any existing schedules
    
    // Initialize state variables or use existing values
    state.level = state.level != null ? state.level : 73  // Default temperature
    state.level = value.toString()  // Ensure string format
    state.max_value = 130  // Maximum allowable temperature
    
    log.debug "state.max_value: $state.max_value"
    
    // Set initial device states
    sendEvent(name: "switch", value: "on")  // Always keep switch on
    sendEvent(name: "thermostatMode", value: "heat")  // CRITICAL: Must stay in heat mode for Alexa
    
    // Schedule regular heat mode enforcement every 10 minutes
    schedule("0 0/10 * * * ?", setToHeat)  // Ensures device stays in heat mode
}

/**
 * Core Functionality Methods
 */

// Refresh device state
def refresh() {
    initialize()
}

// Enforce heat mode - critical for Alexa compatibility
def setToHeat() {
    sendEvent(name: "thermostatMode", value: "heat")
}

// Message parsing (virtual device, so minimal implementation)
def parse(String description) {
    log.trace "Msg: Description is $description"
}

/**
 * Switch Control Methods
 */

def on() {
    log.info "on()"
    sendEvent(name: "switch", value: "on")
}

// Special off handling - immediately turns back on
def off() {
    log.info "off()"
    sendEvent(name: "switch", value: "off")  
    sendEvent(name: "switch", value: "on")  // Immediately turn back on
}

/**
 * Temperature Control Methods
 */

// Increase temperature by 1 degree
def raiseSetpoint() {
    log.debug("raising setpoint ++")
    state.level = state.level + 1
    def val = state.level + 1
    setLevel(val)
}

// Decrease temperature by 1 degree
def lowerSetpoint() {
    log.debug("lowering setpoint --")
    state.level = state.level - 1
    def val = state.level - 1
    setLevel(val)
}

/**
 * Core Thermostat Control Methods
 * These methods handle the various ways temperature can be set
 */

def setThermostatSetpoint(value) {
    if (value == null) return
    state.level = value.toString().toFloat().round()
    setLevel(state.level)
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

// Fan mode is not implemented in this virtual device
def setThermostatFanMode(fanMode) {
    return
}

/**
 * Primary Level Control Method
 * Handles all temperature/level changes and enforces limits
 */
def setLevel(value) {
    if (value == null) return
    
    log.info "setLevel $value"
    
    def max_value = state.max_value ?: 130
    log.debug "max_value (setLevel): $max_value"
    
    // Convert and round the input value
    value_float = value.toString().toFloat().round()
    
    // Enforce maximum value limit
    if (value_float > max_value.toFloat()) {
        log.warn "Value exceeds maximum limit of ${max_value}, setting to max"
        value_float = max_value.toFloat()
    }
    
    // Update state and send events
    state.level = value_float
    // Send events for both dimmer and thermostat capabilities
    sendEvent(name: "level", value: value_float.toString())
    sendEvent(name: "thermostatSetpoint", value: value_float.toString())
    sendEvent(name: "coolingSetpoint", value: value_float.toString())
    sendEvent(name: "heatingSetpoint", value: value_float.toString())
}

/**
 * Thermostat Mode Control Methods
 */

def cool() {
    setThermostatMode("cool")
}

def heat() {
    setThermostatMode("heat")
}

def auto() {
    setThermostatMode("auto")  // Note: Auto mode should be avoided for Alexa compatibility
}

/**
 * Mode Setting Method
 * Note: Always reverts to heat mode for Alexa compatibility
 */
def setThermostatMode(cmd) {
    log.info "setThermostatMode $cmd"
    sendEvent(name: "thermostatMode", value: cmd.toString())
    setToHeat()  // Always revert to heat mode
}