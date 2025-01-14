/* 
MIT License
Copyright (c) Niklas Gustafsson
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

// History:
// 
// 2021-10-22: v1.0 Support for Levoit Air Purifier Core 200S / 400S / 600S

import java.security.MessageDigest

metadata {
    definition(
        name: "VeSync Integration (Levoit)",
        namespace: "NiklasGustafsson",
        author: "Niklas Gustafsson",
        description: "Integrates VeSync devices with Hubitat Elevation",
        category: "My Apps",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        documentationLink: "https://github.com/dcmeglio/hubitat-bond/blob/master/README.md")
        {
            capability "Actuator"
        }

    preferences {
        input(name: "email", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Email Address</font>", description: "<font style='font-size:12px; font-style: italic'>VeSync Account Email Address</font>", defaultValue: "", required: true);
        input(name: "password", type: "password", title: "<font style='font-size:12px; color:#1a77c9'>Password</font>", description: "<font style='font-size:12px; font-style: italic'>VeSync Account Password</font>", defaultValue: "");
        input("refreshInterval", "number", title: "<font style='font-size:12px; color:#1a77c9'>Refresh Interval</font>", description: "<font style='font-size:12px; font-style: italic'>Poll VeSync status every N seconds</font>", required: true, defaultValue: 30)
        input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: false, required: false)

        command "resyncEquipment"

    }
}

def installed() {
    logDebug "Installed with settings: ${settings}"
}

def updated() { 
    logDebug "Updated with settings: ${settings}"
    // state.clear()
    unschedule()
    initialize()

    updateDevices()

    // Turn off debug log in 30 minutes
    if (settings?.debugOutput) runIn(1800, logDebugOff);
}

def uninstalled() {
    logDebug "Uninstalled app"

    for (device in getChildDevices())
    {
        deleteChildDevice(device.deviceNetworkId)
    }    
}

def initialize() {
    logDebug "initializing"

    login()
}

private Boolean login()
{
    def logmd5 = MD5(password)

    def params = [
        uri: "https://smartapi.vesync.com/cloud/v1/user/login",
        contentType: "application/json",
        requestContentType: "application/json",
        body: [
            "timeZone": "America/Los_Angeles",
            "acceptLanguage": "en",
            "appVersion": "2.5.1",
            "phoneBrand": "SM N9005",
            "phoneOS": "Android",
            "traceId": "1634265366",
            "email": email,
            "password": logmd5,
            "devToken": "",
            "userType": "1",
            "method": "login"
        ],
        headers: [ 
            "Accept": "application/json",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "User-Agent": "Hubitat Elevation", 
            "accept-language": "en",
            "appVersion": "2.5.1",
            "tz": "America/Los_Angeles"
 ]
    ]

    logDebug "login: ${params.uri}"

    try
    {
        def result = false
        httpPost(params) { resp ->
            if (checkHttpResponse("login", resp))
            {
                state.token = resp.data.result.token
                state.accountID = resp.data.result.accountID
            }
        }
        return result
    }
    catch (e)
    {
        logDebug e.toString();
        checkHttpResponse("login", e.getResponse())
        return false
    }
}

def Boolean updateDevices()
{
    for (e in state.deviceList) {

        def dni = e.key
        def configModule = e.value

        if (dni.endsWith("-nl")) continue;
        
        logDebug "Updating ${dni}"

        def dev = getChildDevice(dni)

        sendBypassRequest(dev,  [
            "method": "getPurifierStatus",
            "source": "APP"
        ]) { resp ->
            if (checkHttpResponse("update", resp))
            {
                def status = resp.data.result
                log.debug "updating status (from parent driver): ${status}"
                result = dev.update(status, getChildDevice(dni+"-nl"))
            }
        }
    }

    runIn((int)settings.refreshInterval, updateDevices)
}

private Boolean getDevices() {

    def params = [
        uri: "https://smartapi.vesync.com/cloud/v1/deviceManaged/devices",
        contentType: "application/json",
        requestContentType: "application/json",
        body: [
            "timeZone": "America/Los_Angeles",
            "acceptLanguage": "en",
            "appVersion": "2.5.1",
            "phoneBrand": "SM N9005",
            "phoneOS": "Android",
            "traceId": "1634265366",
            "accountID": state.accountID,
            "token": state.token,
            "method": "devices",
            "pageNo": "1",
            "pageSize": "100"
        ],
        headers: [ 
            "tz": "America/Los_Angeles",
            "Accept": "application/json",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "User-Agent": "Hubitat Elevation", 
            "accept-language": "en",
            "appVersion": "2.5.1",
            "accountID": state.accountID,
            "tk": state.token ]
    ]
    try
    {
        def result = false
        httpPost(params) { resp ->
            if (checkHttpResponse("getDevices", resp))
            {
                def newList = [:]

                for (device in resp.data.result.list) {
                    logDebug "Device found: ${device.deviceType} / ${device.deviceName} / ${device.macID}"

                    if (device.deviceType == "Core200S" || device.deviceType == "LAP-C201S-AUSR")
                    {
                        newList[device.cid] = device.configModule;
                        newList[device.cid+"-nl"] = device.configModule;
                    }
                    else if (device.deviceType == "Core400S") {
                        newList[device.cid] = device.configModule;
                    }
                    else if (device.deviceType == "LAP-C601S-WUS") {
                        newList[device.cid] = device.configModule;
                    }
                }
                
                // Remove devices that are no longer present.

                List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
                if (list) list.each {
                    String dni = it.getDeviceNetworkId();
                    if (newList.containsKey(dni) == false) {
                        logDebug "Deleting ${dni}"
                        deleteChildDevice(dni);
                    }
                }

                for (device in resp.data.result.list) {
                    
                    com.hubitat.app.ChildDeviceWrapper equip1 = getChildDevice(device.cid)

                    if (device.deviceType == "Core200S" || device.deviceType == "LAP-C201S-AUSR")
                    {
                        def update = null

                        com.hubitat.app.ChildDeviceWrapper equip2 = getChildDevice(device.cid+"-nl")

                        if (equip2 == null)
                        {
                            equip2 = addChildDevice("Levoit Core200S Air Purifier Light", device.cid+"-nl", [name: device.deviceName + " Light", label: device.deviceName + " Light", isComponent: false]);                                                    
                            equip2.updateDataValue("configModule", device.configModule);
                            equip2.updateDataValue("cid", device.cid);
                            equip2.updateDataValue("uuid", device.uuid);
                        }

                        if (equip1 == null)
                        {
                            logDebug "Adding ${device.deviceName}"
                            equip1 = addChildDevice("Levoit Core200S Air Purifier", device.cid, [name: device.deviceName, label: device.deviceName, isComponent: false]);                                                    
                            equip1.updateDataValue("configModule", device.configModule);
                            equip1.updateDataValue("cid", device.cid);
                            equip1.updateDataValue("uuid", device.uuid);
                        }                        
                    }
                    else if (device.deviceType == "Core400S" || device.deviceType == "Core300S" || device.deviceType == "LAP-C601S-WUS")
                    {
                        if (equip1 == null)
                        {
                            logDebug "Adding ${device.deviceName}"
                            equip1 = addChildDevice("Levoit Core400S Air Purifier", device.cid, [name: device.deviceName, label: device.deviceName, isComponent: false]);                                                    
                            equip1.updateDataValue("configModule", device.configModule);
                            equip1.updateDataValue("cid", device.cid);
                            equip1.updateDataValue("uuid", device.uuid);
                        }
                    }
                }                

                state.deviceList = newList

                updateDevices()

                result = true
            }
        }
        return result
    }
    catch (e)
    {
        logError e.getMessage()
//        checkHttpResponse("getDevices", e.getMessage())
        return false
    }
}

def Boolean sendBypassRequest(equipment, payload, Closure closure)
{
    logDebug "sendBypassRequest(${payload})"

    def params = [
        uri: "https://smartapi.vesync.com/cloud/v2/deviceManaged/bypassV2",
        contentType: "application/json; charset=UTF-8",
        requestContentType: "application/json; charset=UTF-8",
        body: [
            "timeZone": "America/Los_Angeles",
            "acceptLanguage": "en",
            "appVersion": "2.5.1",
            "phoneBrand": "SM N9005",
            "phoneOS": "Android",
            "traceId": "1634265366",
            "cid": equipment.getDataValue("cid"),
            "configModule": equipment.getDataValue("configModule"),
            "payload": payload,
            "accountID": getAccountID(),
            "token": getAccountToken(),
            "method": "bypassV2",
            "debugMode": false,
            "deviceRegion": "US"
        ],
        headers: [
            "tz": "America/Los_Angeles",
            "Accept": "application/json",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "User-Agent": "Hubitat Elevation",
            "accept-language": "en",
            "appVersion": "2.5.1",
            "accountID": getAccountID(),
            "tk": getAccountToken() ]
    ]
    
    try
    {
        httpPost(params, closure)
        return true
    }
    catch (e)
    {
        logError e.getMessage()
        return false
    }
}

// Commands -------------------------------------------------------------------------------------------------------------------

def resyncEquipment() {
  //
  // This will trigger a sensor remapping and cleanup
  //
  try {
    logDebug "resyncEquipment()"

    getDevices()
  }
  catch (Exception e) {
    logError("Exception in resyncEquipment(): ${e}");
  }
}

// Helpers -------------------------------------------------------------------------------------------------------------------

def getAccountToken() {
    return state.token
}

def getAccountID() {
    return state.accountID
}

def MD5(s) {
    def digest = MessageDigest.getInstance("MD5")
    new BigInteger(1,digest.digest(s.getBytes())).toString(16).padLeft(32,"0")
} 

def parseJSON(data) {
    def json = data.getText()
    def slurper = new groovy.json.JsonSlurper()
    return slurper.parseText(json)
}

def logDebug(msg) {
    if (settings?.debugOutput) {
        log.debug msg
    }
}

def logError(msg) {
    log.error msg
}

void logDebugOff() {
  //
  // runIn() callback to disable "Debug" logging after 30 minutes
  // Cannot be private
  //
  if (settings?.debugOutput) device.updateSetting("debugOutput", [type: "bool", value: false]);
}


def checkHttpResponse(action, resp) {
    if (resp.status == 200 || resp.status == 201 || resp.status == 204)
        return true
    else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500)
    {
        log.error "${action}: ${resp.status} - ${resp.getData()}"
        return false
    }
    else
    {
        log.error "${action}: unexpected HTTP response: ${resp.status}"
        return false
    }
}