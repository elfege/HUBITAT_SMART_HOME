// Hub_Mesh_Migrate_Swap_UUIDs.groovy

/**
 * Hubitat app to migrate and swap UUIDs in Hub Mesh network devices.
 * This app identifies all devices linked to a specific UUID in their Device Network ID (DNI)
 * and replaces it with a new UUID input by the user, ensuring continuity in device settings and operations.
 */

definition(
    name: "Hub Mesh Migrate Swap UUIDs",
    namespace: "elfege",
    author: "elfege",
    description: "Swaps UUIDs for devices in a Hub Mesh network, aiding in migration and device reconfiguration.",
    category: "ConvenUtilityience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
)

preferences {
    page(name: "mainPage")
}

def mainPage() {

    def pageProperties = [
        name: "mainPage",
        title: "${app.label}",
        nextPage: null,
        install: true,
        uninstall: true
    ]

    return dynamicPage(pageProperties) {
        section("UUID Replacement Settings") {
            input "uuidToReplace", "text", title: "Old UUID:", required: true
            input "newUUID", "text", title: "New UUID:", required: true
            input "deviceList", "capability.*", title: "Devices to use:", multiple: true, required: true, submitOnChange: true

            // for (device in deviceList){
            //     log.info device.displayName + ": " + device.deviceNetworkId
            // }

        }

        section(){
            input "run", "button", title: "RUN"
        }
    }
}

def appButtonHandler(btn) {

    switch (btn) {
        case "run":
            updateDeviceUUIDs()
            break
    }
}


def installed() {
    log.debug "App installed with settings: ${settings}"
}

def updated() {
    log.debug "App updated with new settings: ${settings}"
}

def updateDeviceUUIDs() {
    def devicesWithOldUUID = getDevicesByUUID(uuidToReplace)
    if (devicesWithOldUUID.isEmpty()) {
        log.warn "No devices found with UUID: $uuidToReplace"
        return
    }
    devicesWithOldUUID.each {
        device ->
        if(device != null){
            if(device.name.contains("OFFLINE") && device.name.contains("Home 2")){
            // def matcher = device.deviceNetworkId = ~ /.*-(\d+)$/
            // if (matcher.find()) {
            def DNI = device.deviceNetworkId
            log.trace DNI
            def matcher = DNI =~ /^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-(\d+)$/
            if (matcher.find()) {
                String oldUUID = matcher.group(1) // This captures the old UUID.
                String deviceId = matcher.group(2) // This captures the device number.
                log.debug "            oldUUID  : $oldUUID"
                log.debug "            newUUID  : $newUUID"
                log.debug "destination device id: $deviceId"
                String updatedDNI = "${newUUID}-${deviceId}"
                try {
                    updateDeviceNetworkID(device, updatedDNI)
                }
                catch (error) {
                    log.error "===> $error"
                }
            } else {
                log.warn "Could not extract device identifier from DNI: ${device.deviceNetworkId}"
            }
            }
        }
    }
}

def updateDeviceNetworkID(device, updatedDNI) {
    log.debug "Executing 'updateDeviceNetworkID' for ${device.displayName}"
    try{
        if (device.deviceNetworkId == updatedDNI) {
        log.info "${device.displayName} already updated"
        } else {
            device.setDeviceNetworkId(updatedDNI)
            log.info "Updated device ${device.displayName} with new DNI: $updatedDNI"
        }
    }
    catch (error){
        log.error "deviceNetworkId ${error}"
    }
    log.warn "${device.name} new DNI is: ${device.deviceNetworkId}"
}

def getDevicesByUUID(String uuid) {
    // log.info "All devices: ${deviceList}"
    // deviceList.each {
    //     device ->
    //     log.debug "deviceNetworkId: ${device.deviceNetworkId}"
    // }
    def matchedDevices = deviceList.findAll { it.deviceNetworkId.contains(uuid) }
    log.info matchedDevices

    log.debug "Found ${matchedDevices.size()} devices with the specified UUID."
    return matchedDevices
}


def getOfflineDevices() {
    def offlineDevices = deviceList.findAll { device ->
        if(device != null) {
            device.label.toUpperCase().contains("OFFLINE")
        }
    }
    return offlineDevices.collect { it.id }
}