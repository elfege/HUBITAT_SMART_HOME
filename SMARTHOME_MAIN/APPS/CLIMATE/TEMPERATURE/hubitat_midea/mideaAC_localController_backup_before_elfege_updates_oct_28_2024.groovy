/*

Copyright 2021 - tomw

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

-------------------------------------------

Change history:

0.9.0 - tomw - Initial release.
0.9.1 - tomw - Add 'off' as a thermostatMode.  Some improvements to Fahrenheit setpoints (still in progress).
0.9.2 - tomw - Fix for data format of attributes: supportedThermostatFanModes and supportedThermostatModes
0.9.3 -
    tcurtin - Add FanControl capability.
    tomw - Command dropdown lists on device page now match actual supported settings.
0.9.4 - jcox10 - Bugfixes for "off" thermostat mode.
0.9.5 - tomw - Support for Turbo mode (command and attribute).

*/

metadata
{
    definition(name: "Midea AC local controller", namespace: "tomw", author: "tomw", importUrl: "")
    {
        attribute "outdoorTemperature", "number"
        
        capability "Configuration"
        capability "FanControl"
        capability "Initialize"
        capability "Refresh"
        capability "Thermostat"
        
        command "dry"
        command "fan_only"
        
        command "setSpeed", [[name: "Fan speed*", type: "ENUM", constraints: supportedFanSpeedsList()]]
        command "setThermostatFanMode", [[name: "Fan mode*", type: "ENUM", constraints: supportedThermoFanModesList()]]        
        command "setThermostatMode", [[name: "Thermostat mode*", type: "ENUM", constraints: supportedThermoModesList()]]
        
        command "controlTurboMode", [[name: "Turbo mode*", type: "ENUM", constraints: ["on", "off"]]]
        attribute "turboMode", "enum", ["on", "off"]
    }
}

preferences
{
    section
    {
        input name: "ipAddress", type: "text", title: "IP address", required: true
        input name: "port", type: "number", title: "port", required: true, defaultValue: 6444
        input name: "id", type: "number", title: "id", required: true
        input name: "token", type: "text", title: "token", required: true
        input name: "key", type: "text", title: "key", required: true        
    }
    section
    {
        input name: "degC", type: "bool", title: "Use degrees C? (Off for Degrees F)", defaultValue: true
        input name: "refreshInterval", type: "number", title: "Polling refresh interval in seconds", defaultValue: 60
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def updated()
{
    configure()
}

def configure()
{
    def jsonOut = new groovy.json.JsonOutput()
    
    def fanModes = jsonOut.toJson(supportedThermoFanModesList())
    sendEvent(name: "supportedThermostatFanModes", value: fanModes)

    def fanSpeeds = jsonOut.toJson(supportedFanSpeedsList())
    sendEvent(name: "supportedFanSpeeds", value: fanSpeeds)

    def thermoModes = jsonOut.toJson(supportedThermoModesList())
    sendEvent(name: "supportedThermostatModes", value: thermoModes)    
    
    initialize()
}

def supportedThermoFanModesList()
{
    return ["auto", "full", "high", "medium", "low", "silent"]
}

def supportedFanSpeedsList()
{
    return ["auto", "full", "high", "medium", "low", "silent"]
}

def supportedThermoModesList()
{
    return ["auto", "cool", "dry", "fan_only", "heat", "off"]    
}

def initialize()
{
    refresh()
}

def refresh()
{
    if(getVolatileState("sessionActive"))
    {
        logDebug("refresh(): socket access failed. another session is already in progress.")
        return
    }
    
    try
    {
        unschedule(refresh)
        
        setVolatileState("sessionActive", true)
        
        if(!openSocket()) {throw new Exception("failed to open socket")}
        
        syncWait([waitSetter: "auth", timeoutSec: 4])
        runInMillis(200, '_authenticate')
        doWait()
        
        syncWait([waitSetter: "refresh", timeoutSec: 4])
        runInMillis(200, '_refresh')
        doWait()
    }
    catch (Exception e)
    {
        log.debug "error: refresh(): ${e.message}"
    }
    finally
    {
        closeSocket()
        setVolatileState("sessionActive", false)
        
        runIn(refreshInterval ?: 60, refresh)
    }
}

def apply(cmdParams)
{    
    if(getVolatileState("sessionActive"))
    {
        logDebug("apply(): socket access failed. another session is already in progress.")
        return
    }
    
    try
    {
        setVolatileState("sessionActive", true)
        
        if(!openSocket()) {throw new Exception("failed to open socket")}
        
        syncWait([waitSetter: "auth", timeoutSec: 4])
        runInMillis(200, '_authenticate')
        doWait()
        
        syncWait([waitSetter: "apply", timeoutSec: 4])
        runInMillis(200, '_apply', [data: cmdParams])
        doWait()
    }
    catch (Exception e)
    {
        log.debug "error: apply(): ${e.message}"
    }
    finally
    {
        closeSocket()
        setVolatileState("sessionActive", false)
    }
}

def logDebug(msg) 
{
    if (logEnable)
    {
        log.debug(msg)
    }
}

def logHexBytes(data)
{
    String res = ""

    data.each
    {
        res += String.format("%02X ", it & 0xFF)
    }
    
    logDebug("bytes: ${res}")
    logDebug("string: ${hubitat.helper.HexUtils.byteArrayToHexString(data as byte[])}")
}

def socketStatus(String message)
{
    logDebug("socketStatus: ${message}")
}

def parse(String message)
{
    logDebug("parse: ${message}")    
    def rdB = hubitat.helper.HexUtils.hexStringToByteArray(message)
    //logDebug("parse: ${rdB}")
    
    if(rdB.size() < 13)
    {
        logDebug("ignoring short response")
        
        // clear this syncWait, likely set by connectSocket
        // note: the Midea units appear to return one 0x00 byte on connect.
        //    if this behavior changes, we will need a different signal to clear the wait.
        clearWait([waitSetter: "open"])
        
        return
    }
    
    if(rdB.size() == 13)
    {
        // just catch 'ERROR'
        rdB = subBytes(rdB, 8, 5)
        //logDebug("rdB = ${new String(rdB)}")
        return
    }
    
    switch(getMsgType(rdB))
    {
        case MSGTYPE_HANDSHAKE_RESPONSE:
            def tcp_key = tcp_key(rdB, key)
            if(tcp_key)
            {
                setTcpKey(tcp_key)
                
                // clear this syncWait, likely set by _authenticate
                clearWait([waitSetter: "auth"])
            }
            break
        
        case MSGTYPE_ENCRYPTED_RESPONSE:
            //logDebug("parse enc resp = ${rdB}")
            //logDebug("decode_8370 = ${decode_8370(rdB)}")

            def dec_8370 = decode_8370(rdB)
            if(([] != dec_8370) && (dec_8370.size() > (40 + 16)))
            {
                def dec_resp = aes_ecb(subBytes(dec_8370, 40, dec_8370.size() - (40 + 16)), "dec")   
                _process_response(dec_resp)
            }
            break
    }
}

def openSocket()
{
    logDebug("opening socket")
    interfaces.rawSocket.connect(ipAddress, port.toInteger(), byteInterface: true)
    
    // workaround: wait a little while to ensure the socket opened
    pauseExecution(500)
    
    return true
}

def closeSocket()
{
    try
    {
        logDebug("closing socket")
        interfaces.rawSocket.close()
        
        // workaround: wait a little while to ensure the socket closed
        pauseExecution(500)
    }
    catch (Exception e)
    {
        // swallow errors
    }
    
    return true
}

def _writeBytes(byte[] bytes)
{
    def wrStr = hubitat.helper.HexUtils.byteArrayToHexString(bytes)
    logDebug("writeBytes: ${wrStr}")
    
    interfaces.rawSocket.sendMessage(wrStr)  
}

def updateSettingParams(paramsIn)
{
    // do a refresh first...
    refresh()
    
    // ...then tweak the settings that we need
    def paramsOut = getVolatileState("state")
    
    paramsIn.each()
    {
        paramsOut[it.key] = it.value
    }
    
    // always set these -- they won't be in the response but need to be in the command
    paramsOut['fahrenheit_enabled'] = !(degC)
    paramsOut['screen_display'] = true
    paramsOut['feedback_enabled'] = true    
    
    return paramsOut
}

def auto()
{
    setThermostatMode("auto")
}

def cool()
{
    setThermostatMode("cool")
}

def dry()
{
    setThermostatMode("dry")
}

def emergencyHeat()
{
    log.warn "emergencyHeat() not supported by this device"
}

def controlTurboMode(turboMode)
{
    def turboOn = ["on", "true", true].contains(turboMode)

    def params = updateSettingParams([turbo_mode: turboOn])
    if(params)
    {
        apply(params)
    }
}

def fanAuto()
{
    def params = updateSettingParams([fan_speed: translateFanMode("auto", "string")])
    if(params)
    {
        apply(params)
    }
}

def setSpeed(fanSpeed)
{
    if(supportedFanSpeedsList().contains(fanSpeed))
    {
        def params = updateSettingParams([fan_speed: translateFanMode(fanSpeed, "string")])
        if(params)
        {
            apply(params)
        }
    }
    else
    {
        log.warn("setSpeed(): unsupported mode ${fanSpeed}")
    }
}

def cycleSpeed()
{
    log.warn "cycleSpeed() not supported by this device"
}

def fanCirculate()
{
    def params = updateSettingParams([fan_speed: translateFanMode("medium", "string")])
    if(params)
    {
        apply(params)
    }
}

def fanOn()
{
    def params = updateSettingParams([fan_speed: translateFanMode("high", "string")])
    if(params)
    {
        apply(params)
    }
}

def fan_only()
{
    setThermostatMode("fan_only")
}

def heat()
{
    setThermostatMode("heat")
}

def off()
{
    def params = updateSettingParams([power_state: false])
    if(params)
    {
        apply(params)
    }
}

def setCoolingSetpoint(temperature)
{
    logDebug("setCoolingSetpoint(${temperature})")
    temperature = ensureCelsius(temperature)
    
    def params = updateSettingParams([target_temperature: temperature])
    if(params)
    {
        apply(params)
    }
}

def setHeatingSetpoint(temperature)
{
    logDebug("setHeatingSetpoint(${temperature})")
    setCoolingSetpoint(temperature)
}

def setSchedule(JSON_OBJECT)
{
    log.warn "setSchedule() not supported by this device"
}

def setThermostatFanMode(fanmode)
{
    if(supportedThermoFanModesList().contains(fanmode))
    {
        def params = updateSettingParams([fan_speed: translateFanMode(fanmode, "string")])
        if(params)
        {
            apply(params)
        }
    }
    else
    {
        log.warn("setThermostatFanMode(): unsupported mode ${fanmode}")
    }
}

def setThermostatMode(thermostatmode)
{
    if("off" == thermostatmode)
    {
        off()
        
        return
    }

    if(supportedThermoModesList().contains(thermostatmode))
    {
        def params = updateSettingParams([operational_mode: translateMode(thermostatmode, "string")])
        
        if(params)
        {
            // all of these modes indicate power_state should be 'on'
            params.power_state = true
            
            apply(params)
        }
        
        return
    }
    
    log.warn("setThermostatMode(): unsupported mode")
}

def _authenticate()
{
    def tokenBytes = hubitat.helper.HexUtils.hexStringToByteArray(token)
    
    logDebug("_authenticate packet follows:")
    
    request = encode_8370(tokenBytes, MSGTYPE_HANDSHAKE_REQUEST)
    _writeBytes(request)
}

def _refresh()
{
    def packet = packet_builder(id.toLong(), request_status_command())
    logDebug("_refresh packet follows:")
    
    appliance_transparent_send_8370(packet, MSGTYPE_ENCRYPTED_REQUEST)
}

def _apply(cmdParams)
{
    def packet = packet_builder(id.toLong(), set_command(cmdParams))
    logDebug("_apply packet follows:")
    
    appliance_transparent_send_8370(packet, MSGTYPE_ENCRYPTED_REQUEST)    
}

def _process_response(data)
{
    def resp
    
    if(data == 'ERROR'.getBytes())
    {
        logDebug("response ERROR")
        return null
    }
    
    if(0xC0 == i8Tou8(data[0xA]))
    {
        resp = appliance_response(data)
        logDebug("appliance_response: ${resp}")
        
        if(resp)
        {
            _updateAttributes(resp)
            setVolatileState("state", resp)
        }
        
        // clear this syncWait, possibly set by refresh()
        clearWait([waitSetter: "refresh"])
        // clear this syncWait, possibly set by _apply()
        clearWait([waitSetter: "apply"])
    }
    
    return resp
}

def correctTemp(input, inputScale)
{
    switch(inputScale)
    {
        case "F":
            return (degC ? fahrenheitToCelsius(new BigDecimal(input)) : (Math.round(new BigDecimal(input) * 2) / 2).setScale(1, BigDecimal.ROUND_HALF_UP))
        
        case "C":
            return (degC ? new BigDecimal(input) : (Math.round(celsiusToFahrenheit(new BigDecimal(input)) * 2) / 2).setScale(1, BigDecimal.ROUND_HALF_UP))
        
        default:
            return input
    }
}

def ensureCelsius(input)
{
    return (degC) ? new BigDecimal(input) : fahrenheitToCelsius(new BigDecimal(input))
}

def _updateAttributes(resp)
{
    if(!resp)
    {
        return
    }
    
    def events = [[:]]
    
    events +=    [name: "coolingSetpoint",             value: correctTemp(resp.target_temperature, "C"), unit: getUnit()]
    events +=    [name: "heatingSetpoint",             value: correctTemp(resp.target_temperature, "C"), unit: getUnit()]
    events +=    [name: "temperature",                 value: correctTemp(resp.indoor_temperature, "C"), unit: getUnit()]
    events +=    [name: "thermostatFanMode",           value: translateFanMode(resp.fan_speed?.toInteger(), "integer")]
    events +=    [name: "thermostatMode",              value: translateMode(resp.operational_mode?.toInteger(), "integer")]
    events +=    [name: "thermostatOperatingState",    value: resp.power_state ? "on" : "off" ]
    events +=    [name: "thermostatSetpoint",          value: correctTemp(resp.target_temperature, "C"), unit: getUnit()]
    events +=    [name: "outdoorTemperature",          value: correctTemp(resp.outdoor_temperature, "C"), unit: getUnit()]
    events +=    [name: "turboMode",                   value: resp.turbo_mode ? "on" : "off"]
    
    events.each
    {
        sendEvent(it)
    }  
}

def getUnit()
{
    return (degC) ? "°C" : "°F"
}

def translateMode(value, inputType)
{
    switch(inputType)
    {
        case "integer":
            switch(value.toInteger())
            {
                case 1: return "auto"
                case 2: return "cool"
                case 3: return "dry"
                case 4: return "heat"
                case 5: return "fan_only"
                default: return "unknown"
            }

        case "string":
            switch(value)
            {
                case "auto": return 1
                case "cool": return 2
                case "dry":  return 3
                case "heat": return 4
                case "fan_only": return 5
                default: return
            }
        
        default:
            return 
    }
}

def translateFanMode(value, inputType)
{
    switch(inputType)
    {
        case "integer":
            switch(value.toInteger())
            {
                case 102: return "auto"
                case 100: return "full"
                case 80: return "high"
                case 60: return "medium"
                case 40: return "low"
                case 20: return "silent"
                default: return "unknown"
            }

        case "string":
            switch(value)
            {
                case "auto": return 102
                case "full": return 100
                case "high": return 80
                case "medium": return 60
                case "low": return 40
                case "silent": return 20
                default: return
            }
        
        default:
            return
    }
}

def setTcpKey(key)
{
    setVolatileState('tcp_key', key)
}

def getTcpKey()
{
    getVolatileState('tcp_key')
}

import groovy.transform.Field
@Field MSGTYPE_HANDSHAKE_REQUEST = 0x0
@Field MSGTYPE_HANDSHAKE_RESPONSE = 0x1
@Field MSGTYPE_ENCRYPTED_RESPONSE = 0x3
@Field MSGTYPE_ENCRYPTED_REQUEST = 0x6
@Field MSGTYPE_TRANSPARENT = 0xf

import java.security.MessageDigest

def getMsgType(header)
{
    if(i8Tou8(bytesToInt([header[0]], "big")) != 0x83 || i8Tou8(bytesToInt([header[1]], "big")) != 0x70)
    {
        logDebug("not an 8370 message")
        return -1
    }
    
    if(header.size() < 6)
    {
        logDebug("header too short")
        return -1
    }
    
    def msgtype = i8Tou8(bytesToInt([header[5]], "big")) & 0xf
}

def encode_8370(data, msgtype)
{
    byte[] header = [i8Tou8(0x83), i8Tou8(0x70)]
    
    def size = data.size()
    def padding = 0
    
    if(msgtype in [MSGTYPE_ENCRYPTED_RESPONSE, MSGTYPE_ENCRYPTED_REQUEST])
    {
        if((size + 2) % 16 != 0)
        {
            padding = 16 - ((size + 2) & 0xf)
            size += (padding + 32)
            
            byte[] pBytes = new byte[padding]
            new Random().nextBytes(pBytes)
            data = appendByteArr(data, pBytes)
        }
    }
    
    header = appendByteArr(header, intToBytes(size, 2, "big"))
    header = appendByteArr(header, [i8Tou8(0x20), i8Tou8(padding << 4 | msgtype)])
    
    def request_count = state.request_count ?: 0
    data = appendByteArr(intToBytes(request_count, 2, "big"), data)
    state.request_count = request_count + 1
    
    if(msgtype in [MSGTYPE_ENCRYPTED_RESPONSE, MSGTYPE_ENCRYPTED_REQUEST])
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256")
        def sign = digest.digest(appendByteArr(header, data))
        
        // if tcp_key isn't available, just use a random key
        data = aes_cbc(data, "enc", getTcpKey() ?: '4D67055D53288313335D65FB2CBA3DDB04001F8AF6880CBDB5BC45DA67EC8A35')
        
        data = appendByteArr(data, sign)
    }
    
    return appendByteArr(header, data)
}

def decode_8370(data)
{    
    def header = subBytes(data, 0, 6)
    data = subBytes(data, 6, data.size() - 6)

    if(i8Tou8(bytesToInt([header[0]], "big")) != 0x83 || i8Tou8(bytesToInt([header[1]], "big")) != 0x70)
    {
        logDebug("not an 8370 message")
        return []
    }
    
    if(i8Tou8(bytesToInt([header[4]], "big")) != 0x20)
    {
        logDebug("missing byte 4")
        return []
    }
    
    def padding = i8Tou8(bytesToInt([header[5]], "big")) >> 4
    def msgtype = getMsgType(header)
    
    def size = i16Tou16(bytesToInt(subBytes(header, 2, 2), "big"))
    
    if(data.size() < (size + 2))
    {
        // request_count was not in size, so count 2 extra bytes here
        logDebug("data.size() = ${data.size()}, size + 2 = ${size + 2}")
        return []
    }
    
    if(msgtype in [MSGTYPE_ENCRYPTED_RESPONSE, MSGTYPE_ENCRYPTED_REQUEST])
    {
        sign = subBytes(data, data.size() - 32, 32)
        data = subBytes(data, 0, data.size() - 32)
        
        // if tcp_key isn't available, just use a random key
        data = aes_cbc(data, "dec", getTcpKey() ?: '4D67055D53288313335D65FB2CBA3DDB04001F8AF6880CBDB5BC45DA67EC8A35')
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256")
        def check = digest.digest(appendByteArr(header, data))
        
        if(check != sign)
        {
            logDebug("sign does not match")
            return []
        }
        
        if(padding)
        {
            data = subBytes(data, 0, data.size() - padding)
        }
    }    

    state.response_count = i16Tou16(bytesToInt(subBytes(data, 0, 2), "big"))
    data = subBytes(data, 2, data.size() - 2)
    
    return data
}

import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.Cipher

@Field signKey = 'xhdiwjnchekd4d512chdjx5d8e4c394D2D7S'.getBytes()

def aes_cbc(data, op = "enc", key = key)
{
    // thanks: https://community.hubitat.com/t/groovy-aes-encryption-driver/31556
    
    def cipher = Cipher.getInstance("AES/CBC/NoPadding", "SunJCE")
    
    // note: we already have the key from midea-smart    
    byte[] keyBytes = hubitat.helper.HexUtils.hexStringToByteArray(key)
    SecretKeySpec aKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES")
    
    //self.iv = b'\0' * 16
    def IVKey = '\0' * 16
    IvParameterSpec iv = new IvParameterSpec(IVKey.getBytes("UTF-8"))
    
    cipher.init(op == "enc" ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, aKey, iv)
    
    return cipher.doFinal(data)
}

def aes_ecb(data, op = "enc")
{    
    def encKey = md5(signKey)
    SecretKeySpec aKey = new SecretKeySpec(encKey, 0, encKey.length, "AES")
    
    def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "SunJCE")
    cipher.init(op == "enc" ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, aKey)
    
    return cipher.doFinal(data)
}

def md5(data)
{
    MessageDigest digest = MessageDigest.getInstance("MD5")
    digest.update(data)
    byte[] md5sum = digest.digest()
    
    return md5sum
}

def tcp_key(response, key = key)
{
    if(subBytes(response, 8, 5) == 'ERROR'.getBytes())
    {
        logDebug("authentication failed")
        return null
    }
    
    if(response.size() != 72)
    {
        logDebug("unexpected data length")
        return null
    }

    response = subBytes(response, 8, 64)
        
    def payload = subBytes(response, 0, 32)
    def sign = subBytes(response, 32, 32)
    
    def plain = aes_cbc(payload, "dec", key)
    
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    if(sign != digest.digest(plain))
    {
        logDebug("sign does not match")
        return null
    }    
    
    byte[] keyBytes = hubitat.helper.HexUtils.hexStringToByteArray(key)
    
    if(plain.size() != keyBytes.size())
    {
        logDebug("size mismatch")
        return null
    }
    
    (0..(plain.size() - 1)).each
    {
        // tcp_key = strxor(plain, key)
        plain[it] = plain[it] ^ keyBytes[it]
    }
    
    def tcp_key = hubitat.helper.HexUtils.byteArrayToHexString(plain)
    
    state.request_count = 0
    state.response_count = 0
    
    return tcp_key
}

def appliance_response(data)
{
    // The response data from the appliance includes a packet header which we don't want
    data = subBytes(data, 0xA, data.size() - 0xA)
    
    def resp = [:]
    
    resp += [power_state:           (data[0x01] & 0x1) > 0]
    resp += [imode_resume:          (data[0x01] & 0x4) > 0]
    resp += [timer_mode:            (data[0x01] & 0x10) > 0]
    resp += [appliance_error:       (data[0x01] & 0x80) > 0]
    resp += [target_temperature:    (data[0x02] & 0xf) + 16.0 + (((data[0x02] & 0x10) > 0) ? 0.5 : 0.0)]
    resp += [operational_mode:      (data[0x02] & 0xe0) >> 5]
    resp += [fan_speed:             data[0x03] & 0x7f]
    
    
    def on_timer_value = data[0x04]
    def on_timer_minutes = data[0x06]
    resp += [on_timer_status:        ((on_timer_value & 0x80) >> 7) > 0]
    resp += [on_timer_hour:          (on_timer_value & 0x7c) >> 2]
    resp += [on_timer_minutes:       (on_timer_value & 0x3) | ((on_timer_minutes & 0xf0) >> 4)]
    
    
    off_timer_value = data[0x05]
    off_timer_minutes = data[0x06]    
    resp += [off_timer_status:        ((off_timer_value & 0x80) >> 7) > 0]
    resp += [off_timer_hour:          (off_timer_value & 0x7c) >> 2]
    resp += [off_timer_minutes:       (off_timer_value & 0x3) | (off_timer_minutes & 0xf)]
    
    
    resp += [swing_mode:              data[0x07] & 0x0f]
    resp += [cozy_sleep:              data[0x08] & 0x03]
    resp += [save:                    (data[0x08] & 0x08) > 0]
    resp += [low_frequency_fan:       (data[0x08] & 0x10) > 0]
    resp += [super_fan:               (data[0x08] & 0x20) > 0]
    
    resp += [feel_own:                (data[0x08] & 0x80) > 0]
    resp += [child_sleep_mode:        (data[0x09] & 0x01) > 0]
    resp += [exchange_air:            (data[0x09] & 0x02) > 0]
    resp += [dry_clean:               (data[0x09] & 0x04) > 0]
    resp += [aux_heat:                (data[0x09] & 0x08) > 0]
    resp += [eco_mode:                (data[0x09] & 0x10) > 0]
    resp += [clean_up:                (data[0x09] & 0x20) > 0]
    resp += [temp_unit:               (data[0x09] & 0x80) > 0]
    resp += [sleep_function:          (data[0x0a] & 0x01) > 0]
    resp += [turbo_mode:              ((data[0x08] & 0x20) | (data[0x0a] & 0x02)) > 0]
    
    resp += [catch_cold:              (data[0x0a] & 0x08) > 0]
    resp += [night_light:             (data[0x0a] & 0x10) > 0]
    resp += [peak_elec:               (data[0x0a] & 0x20) > 0]
    resp += [natural_fan:             (data[0x0a] & 0x40) > 0]
    
    resp += [outdoor_temperature:     ((data[0x0c] - 50) / 2.0).toFloat()]
    resp += [humidity:                (data[0x0d] & 0x7f)]    
    
    // indoor_temperature
    Integer indoorTempInteger = 0
    if(((i8Tou8(data[11]) - 50) / 2).toInteger() < -19 || ((i8Tou8(data[11]) - 50) / 2).toInteger() > 50)
    {
        resp += [indoor_temperature:   0xFF]
    }
    else
    {
        indoorTempInteger = ((i8Tou8(data[11]) - 50) / 2).toInteger()        
        Float indoorTempDecimal = getBits(data, 15, 0, 3) * 0.1
        
        indoorTempDecimal = (i8Tou8(data[11]) > 49) ? (indoorTempInteger.toFloat() + indoorTempDecimal) : (indoorTempInteger.toFloat() - indoorTempDecimal)
        
        resp += [indoor_temperature:   indoorTempDecimal]
    }
  
    return resp
}

def appliance_transparent_send_8370(data, msgtype=MSGTYPE_ENCRYPTED_REQUEST)
{
    if(!getTcpKey())
    {
        logDebug("missing tcp_key.  need to _authenticate")
        return
    }
    
    def sData = subBytes(data, 0, data.size())
    sData = encode_8370(sData, msgtype)
    _writeBytes(sData)
}

def request_status_command()
{
    return base_command()
}

def set_command(Map cmdParams)
{
    def data = base_command()
    
    data[0x01] = 0x23
    data[0x09] = 0x02
    
    // Set up Mode
    data[0x0a] = 0x40
    
    // prompt_tone
    data[0x0b] = 0x40
    
    data = appendByteArr(data, [0x00, 0x00, 0x00])
    
    // tone.setter
    data[0x0b] &= ~ 0x42
    data[0x0b] |= (cmdParams.feedback_enabled ? 0x42 : 0)
    
    // power_state.setter
    data[0x0b] &= ~ 0x01
    data[0x0b] |= (cmdParams.power_state ? 0x01 : 0)
    
    // target_temperature.setter
    def target_temperature = cmdParams.target_temperature
    // bound the temperature setpoint to within the range of 17C to 30C
    target_temperature = (target_temperature < 17) ? 17 : ((target_temperature > 30) ? 30 : target_temperature)
    data[0x0c] &= ~ 0x0f
    data[0x0c] |= (target_temperature?.toInteger() & 0xf)
    // temperature_dot5.setter
    if((target_temperature * 2).toInteger() % 2 != 0)
    {
        data[0x0c] |= 0x10
    }
    else
    {
        data[0x0c] &= (~0x10)
    }
    
    // operational_mode.setter
    data[0x0c] &= ~ 0xe0
    data[0x0c] |= (cmdParams.operational_mode << 5) & 0xe0
    
    // fan_speed.setter
    data[0x0d] = cmdParams.fan_speed
    
    // eco_mode.setter
    data[0x13] = (cmdParams.eco_mode ? 0xFF : 0)
    
    // swing_mode.setter
    data[0x11] = 0x30
    data[0x11] |= (cmdParams.swing_mode & 0x3f)
    
    // turbo_mode.setter
    if(cmdParams.turbo_mode)
    {
        data[0x12] = 0x20
        data[0x14] |= 0x02
    }
    else
    {
        data[0x12] = 0
        data[0x14] &= (~0x02)
    }
    
    // screen_display.setter
    if(cmdParams.screen_display)
    {
        data[0x14] |= 0x10
    }
    else
    {
        data[0x14] &= (~0x10)
    }
    
    // fahrenheit.setter
    if(cmdParams.fahrenheit_enabled)
    {
        data[0x14] |= 0x04
    }
    else
    {
        data[0x14] &= (~0x04)
    }
    
    return data
}

def base_command()
{
    byte[] req = 
    [
        // 0 header
        0xaa,
        // 1 command lenght: N+10
        0x20,
        // 2 device type (0xAC for air conditioner)
        0xac,
        // 3 Frame SYN CheckSum
        0x00,
        // 4-5 Reserved
        0x00, 0x00,
        // 6 Message ID
        0x00,
        // 7 Frame Protocol Version
        0x00,
        // 8 Device Protocol Version
        0x00,
        // 9 Message Type: request is 0x03; setting is 0x02
        0x03,
        
        // Byte0 - Data request/response type: 0x41 - check status; 0x40 - Set up
        0x41,
        // Byte1
        0x81,
        // Byte2 - operational_mode
        0x00,
        // Byte3
        0xff,
        // Byte4
        0x03,
        // Byte5
        0xff,
        // Byte6
        0x00,
        // Byte7 - Room Temperature Request: 0x02 - indoor_temperature, 0x03 - outdoor_temperature
        // when set, this is swing_mode
        0x02,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        // Message ID
        (state.request_count ?: 1) & 0xFF
    ]
    
    return req
}

def packet_builder(device_id, command)
{
    // Init the packet with the header data
    def packet =
        [
            // 2 bytes - StaicHeader
            0x5a, 0x5a,
            // 2 bytes - mMessageType
            0x01, 0x11,
            // 2 bytes - PacketLenght
            0x00, 0x00,
            // 2 bytes
            0x20, 0x00,
            // 4 bytes - MessageId
            0x00, 0x00, 0x00, 0x00,
            // 8 bytes - Date&Time
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // 6 bytes - mDeviceID
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // 12 bytes
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        ]
    
    //self.packet[12:20] = self.packet_time()
    //'%Y%m%d%H%M%S%f'
    def dateBytes = hubitat.helper.HexUtils.hexStringToByteArray(new Date().format('yyyyMMddHHmmssSSSS'))
    packet = replaceSubArr(packet, subBytes(dateBytes, 0, 8), 12)
    
    //self.packet[20:28] = device_id.to_bytes(8, 'little')
    packet = replaceSubArr(packet, intToBytes(device_id, 8, "little"), 20)
    
    
    // base_command.finalize()
    // Add the CRC8
    def crc = crc8(subBytes(command, 10, command.size() - 10))
    command = appendByteArr(command, [i8Tou8(crc)])
    // Set the length of the command data
    // self.data[0x01] = len(self.data)
    // Add checksum
    def checksum = checksum(subBytes(command, 1, command.size() - 1))
    command = appendByteArr(command, [i8Tou8(checksum)])
    
    
    // packet_builder.finalize()
    def encCmd = aes_ecb(command, "enc")
    // Append the command data(48 bytes) to the packet
    packet = appendByteArr(packet, subBytes(encCmd, 0, 48))
    // PacketLength
    packet = replaceSubArr(packet, intToBytes(packet.size() + 16, 2, "little"), 4)    
    // Append a basic checksum data(16 bytes) to the packet
    packet = appendByteArr(packet, md5(appendByteArr(packet, signKey)))
    
    return packet
}

def appendByteArr(a, b)
{
    byte[] c = new byte[a.size() + b.size()]
    
    a.eachWithIndex()
    {
        it, i ->
        c[i] = it
    }
    
    def aSz = a.size()
    
    b.eachWithIndex()
    {
        it, i ->
        c[i + aSz] = it
    }
    
    return c
}

def replaceSubArr(orig_arr, new_arr, start)
{
    def tmp_arr = orig_arr.collect()
    new_arr.eachWithIndex
    {
        it, i ->
        tmp_arr[i + start] = it
    }
    
    return tmp_arr
}

private subBytes(arr, start, length)
{
    byte[] sub = new byte[length]
    
    for(int i = 0; i < length; i++)
    {
        sub[i] = arr[i + start]
    }
    
    return sub
}

def swapEndiannessU16(input)
{    
    return [i8Tou8(input[1]), i8Tou8(input[0])]
}

def swapEndiannessU32(input)
{
    return [input[3], input[2], input[1], input[0]]
}

def swapEndiannessU64(input)
{
    return [input[7], input[6], input[5], input[4],
            input[3], input[2], input[1], input[0]]
}

def intToBytes(input, width, endian = "little")
{
    def output = new BigInteger(input).toByteArray()
    
    if(output.size() > width)
    {
        // if we got too many bytes, lop off the MSB(s)
        output = subBytes(output, output.size() - width, width)
        output = output.collect{it & 0xFF}
    }
    
    byte[] pad    
   
    if(output.size() < width)
    {
        def padding = width - output.size()
        pad = [0] * padding
        output = appendByteArr(pad, output)        
    }
    
    if("little" == endian)
    {
        switch(width)
        {
            case 1:
                break
            case 2:
                output = swapEndiannessU16(output)
                break
            case 4:
                output = swapEndiannessU32(output)
                break
            case 8:
                output = swapEndiannessU64(output)
                break
        }
    }
    
    return output.collect{it & 0xFF}
}

def bytesToInt(input, endian = "little")
{
    def output = subBytes(input, 0, input.size())
    
    long retVal = 0
    output.eachWithIndex
    {
        it, i ->
        
        switch(endian)
        {
            case "little":
                retVal += ((it & 0xFF).toLong() << (i * 8))
                break
            case "big":
            default:
                retVal += (it & 0xFF).toLong() << ((output.size() - 1 - i) * 8)
                break
        }
    }
    
    if(input.size() == 8)
    {
        // 8 bytes is too big for integer
        return retVal
    }
    
    return retVal as Integer
}

def i8Tou8(input)
{
    return input & 0xFF
}

def i16Tou16(input)
{
    return input & 0xFFFF
}

def getBit(pByte, pIndex)
{
    return (pByte >> pIndex) & 0x01
}

def getBits(pBytes, pIndex, pStartIndex, pEndIndex)
{
    if(pStartIndex > pEndIndex)
    {
        StartIndex = pEndIndex
        EndIndex = pStartIndex
    }
    else
    {
        StartIndex = pStartIndex
        EndIndex = pEndIndex
    }
    
    tempVal = 0x00
    StartIndex.upto(EndIndex)
    {
        tempVal = tempVal | getBit(pBytes[pIndex], it) << (it - StartIndex)
    }
    
    return tempVal
}

@Field crc8_854_table =
    [
    0x00, 0x5E, 0xBC, 0xE2, 0x61, 0x3F, 0xDD, 0x83,
    0xC2, 0x9C, 0x7E, 0x20, 0xA3, 0xFD, 0x1F, 0x41,
    0x9D, 0xC3, 0x21, 0x7F, 0xFC, 0xA2, 0x40, 0x1E,
    0x5F, 0x01, 0xE3, 0xBD, 0x3E, 0x60, 0x82, 0xDC,
    0x23, 0x7D, 0x9F, 0xC1, 0x42, 0x1C, 0xFE, 0xA0,
    0xE1, 0xBF, 0x5D, 0x03, 0x80, 0xDE, 0x3C, 0x62,
    0xBE, 0xE0, 0x02, 0x5C, 0xDF, 0x81, 0x63, 0x3D,
    0x7C, 0x22, 0xC0, 0x9E, 0x1D, 0x43, 0xA1, 0xFF,
    0x46, 0x18, 0xFA, 0xA4, 0x27, 0x79, 0x9B, 0xC5,
    0x84, 0xDA, 0x38, 0x66, 0xE5, 0xBB, 0x59, 0x07,
    0xDB, 0x85, 0x67, 0x39, 0xBA, 0xE4, 0x06, 0x58,
    0x19, 0x47, 0xA5, 0xFB, 0x78, 0x26, 0xC4, 0x9A,
    0x65, 0x3B, 0xD9, 0x87, 0x04, 0x5A, 0xB8, 0xE6,
    0xA7, 0xF9, 0x1B, 0x45, 0xC6, 0x98, 0x7A, 0x24,
    0xF8, 0xA6, 0x44, 0x1A, 0x99, 0xC7, 0x25, 0x7B,
    0x3A, 0x64, 0x86, 0xD8, 0x5B, 0x05, 0xE7, 0xB9,
    0x8C, 0xD2, 0x30, 0x6E, 0xED, 0xB3, 0x51, 0x0F,
    0x4E, 0x10, 0xF2, 0xAC, 0x2F, 0x71, 0x93, 0xCD,
    0x11, 0x4F, 0xAD, 0xF3, 0x70, 0x2E, 0xCC, 0x92,
    0xD3, 0x8D, 0x6F, 0x31, 0xB2, 0xEC, 0x0E, 0x50,
    0xAF, 0xF1, 0x13, 0x4D, 0xCE, 0x90, 0x72, 0x2C,
    0x6D, 0x33, 0xD1, 0x8F, 0x0C, 0x52, 0xB0, 0xEE,
    0x32, 0x6C, 0x8E, 0xD0, 0x53, 0x0D, 0xEF, 0xB1,
    0xF0, 0xAE, 0x4C, 0x12, 0x91, 0xCF, 0x2D, 0x73,
    0xCA, 0x94, 0x76, 0x28, 0xAB, 0xF5, 0x17, 0x49,
    0x08, 0x56, 0xB4, 0xEA, 0x69, 0x37, 0xD5, 0x8B,
    0x57, 0x09, 0xEB, 0xB5, 0x36, 0x68, 0x8A, 0xD4,
    0x95, 0xCB, 0x29, 0x77, 0xF4, 0xAA, 0x48, 0x16,
    0xE9, 0xB7, 0x55, 0x0B, 0x88, 0xD6, 0x34, 0x6A,
    0x2B, 0x75, 0x97, 0xC9, 0x4A, 0x14, 0xF6, 0xA8,
    0x74, 0x2A, 0xC8, 0x96, 0x15, 0x4B, 0xA9, 0xF7,
    0xB6, 0xE8, 0x0A, 0x54, 0xD7, 0x89, 0x6B, 0x35
    ]

int crc8(value)
{
    // thanks: http://www.java2s.com/example/java-utility-method/crc-calculate/crc8-string-value-6f7a7.html
    
    int crc = 0
    for (int i = 0; i < value.size(); i++)
    {
        crc = crc8_854_table[value[i] ^ (crc & 0xFF)]
    }
    
    return crc
}

def checksum(data)
{
    def sum = data.sum()
    return (~ sum + 1) & 0xFF
}

def encode(byte[] data)
{
    data.collect { it & 0xFF }
}


//////////////////////////////////////
// volatile state and sync code below
//////////////////////////////////////

@Field static volatileState = [:].asSynchronized()

def setVolatileState(name, value)
{
    def tempState = volatileState[device.getDeviceNetworkId()] ?: [:]
    tempState.putAt(name, value)
    
    volatileState.putAt(device.getDeviceNetworkId(), tempState)
    
    return volatileState
}

def getVolatileState(name)
{
    return volatileState.getAt(device.getDeviceNetworkId())?.getAt(name) ?: null
}

def syncWait(data)
{
    // set up for checking 5x per second
    setVolatileState("syncWaitDetails", [waitSetter: data.waitSetter, retryCount: data.timeoutSec * 5])
}

def doWait()
{
    def wtDetails = getVolatileState("syncWaitDetails")
    
    // check every 200 ms whether the wait was cleared...
    if(wtDetails?.waitSetter == "")
    {
        return
    }
    
    // ...or throw an exception if we ran out of tries
    if(wtDetails?.retryCount == 0)
    {
        throw new Exception("wait timed out")
    }
    
    wtDetails.putAt("retryCount", wtDetails.getAt("retryCount") - 1)
    
    setVolatileState("syncWaitDetails", wtDetails)
    
    pauseExecution(200)
    doWait()
}

def clearWait(data)
{
    def wtDetails = getVolatileState("syncWaitDetails")
    
    if(data.waitSetter == wtDetails?.getAt("waitSetter"))
    {
        syncWait([waitSetter: "", timeoutSec: 0])
    }
}

def clearAllWaits()
{
    syncWait([waitSetter: "", timeoutSec: 0])
}