definition(
    name: "Rename devices labels",
    namespace: "elfege",
    author: "ELFEGE",

    description: "Change devices labels and names at once",

    category: "maintenance",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet@2x.png"
)

preferences {
    page name: "pageSetup"
}

def pageSetup()
{
    def pageProperties = [
        name: "pageSetup",
        title: "${app.label}",
        install: true,
        uninstall: true,

    ]

    return dynamicPage(pageProperties) {


        section("devices")
        {
            input "allDevices", "capability.*", title: "Select the devices you want to rename", multiple: true, submitOnChange: true
            input "toRemove", "text", title: "Text to remove", description: "separate different texts with commas"
            state.toRemove = toRemove?.split(",")
            if (enabledebug) log.debug "state.toRemove = $state.toRemove"

            input "setName", "bool", title: "also change the device's name (not just its label - EXPERIMENTAL)"
            input "setLabelFromName", "bool", title: "Restore The device label from the device's name before removing characters"

            input "enabledebug", "bool", title: "Debug", submitOnChange:true
            input "enableinfo", "bool", title: "Info logs", submitOnChange:true
            input "enablewarning", "bool", title: "Warning logs", submitOnChange:true
            input "enabletrace", "bool", title: "Trace logs", submitOnChange:true

            input "execute", "button", title: "execute", submitOnChange:true
            
            state.installed = state.installed == null ? false : state.installed

            state.btnName = state.installed ? "update" : "install"
            input "${state.btnName}", "button", title: "${state.btnName.toUpperCase()}", submitOnChange:true

            log.debug "state.installed = $state.installed"
            
        }


    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {    
    state.installed = true

    schedule("0 0 * * * ?", execute)  // run every hour
    log.debug "state.installed set to true: $state.installed"
}

def appButtonHandler(btn) {
    // def alert = com.eviware.soapui.support.UISupport;
    switch (btn) {
        case "execute":
            if (!state.toRemove) {
                if (enablewarning) log.warn "empty string!"
            }
            else {
                execute()
            }
            break
        case "${state.btnName}":
            if (!state.installed) {
                log.info "Installing ..."
                installed()
            }
            else {
                log.info "updating ..."
                initialize()
            }
            break
    }
}

def isMeshDevice(device) {

    if (enableWarning) log.warn "Checking if device is a Hubitat Mesh Device: ${device.displayName}"

    if (enableTrace) {
        log.trace "Device properties: ${device.properties*.name}"
        log.trace "Device capabilities: ${device.capabilities*.name}"
    }

    // **Method 0: Check 'hubMeshDisabled' Attribute**
    try {
        def hubMeshDisabled = device.currentValue("hubMeshDisabled")
        if (enableTrace) log.trace "Device 'hubMeshDisabled' attribute: ${hubMeshDisabled}"
        if (hubMeshDisabled == "false" || hubMeshDisabled == false) {
            if (enabledebug) log.debug "${device.displayName} is MESH"
            return true
        }
    } catch (Exception e) {
        log.error "Error retrieving 'hubMeshDisabled' attribute for ${device.displayName}: ${e.message}"
    }

    if (enabledebug) log.debug "Device ${device.displayName} is NOT identified as a Hubitat Mesh Device."
    return false
}

def updateLabelFromName(device){
    try {
        if (enabledebug) log.debug "Setting label for ${device.displayName} to match system name: ${device.name}"
        device.setLabel(device.name)
    } catch (Exception e) {
        log.error "setLabelFromName ==> ${e}"
    }
}




def execute()
{
    log.info "Renaming devices..."
    if (allDevices) {

        log.debug "NUMBER OF DEVICES: ${allDevices.size()} (MAX: 149)"
        
        allDevices.each { device -> 
        
            //if (enabledebug) log.debug "renaming $device ($device.displayName)"
            def str = device.displayName
            def stringToRemove = state.toRemove

            // Ensure the device label is first updated with the device name
            // This weirdly restores the original MESH name (on Home 1, etc.)
            // BEWARE! device.name = the mesh name. device.typeName = the original device name on the source hub! (i.e. Senled Zigbee Dimmer, etc.)
            if (setLabelFromName) {
                // START OF NOTES SECTION
                // KEEP THESE NOTES FOR FUTURE REFERENCE. 
                // the original name is device.typeName! Not "name" which still returns the label. 
                // NAME is the actual NAME IF AND ONLY IF the device is paired to this hub. 
                // If it's a MESH device, then its name is typeName and WE CANNOT USE THAT (it'll probably be something like "Zigbee Device thingy.. etc." unless we do some pattern 
                // recognition, comparing with the label - which is a useless headache for now)
                // if (enabledebug) log.debug "setLabelFromName-----------> <br>typeName: ${device.typeName} <br> name: ${device.name} <br>displayName: ${device.displayName} <br>label: ${device.label}"
                // END OF NOTES SECTION

                // we update from the name, as an option, to restore the "on Home X" values that we want to remove. Can be useful to restore original "names" (in truth: lables, when it's mesh device)
                updateLabelFromName(device)
            }


            state.backup = state.backup ?: [:]
            for (int a = 0; a < stringToRemove.size(); a++)
            {
                """
                Check if the device name contains the text to be removed
                Remove that text
                Clean up any trailing spaces that might be left
                Set the new label

                """
                state.backup[device.id] = str // backup the original name

                if (str.contains(stringToRemove[a])) {
                    if (enabledebug) log.debug "$str contains characters to remove"
                    str = str - stringToRemove[a]
                    if (enabledebug) log.debug "new device label is '$str'"
                }

                // remove trailing spaces (best for homebridge)
                // str = str.replaceAll(/\s+$/, "")
                def cleanedName = str.trim()
                device.setLabel(cleanedName)

                
                // Validate the change
                def currentLabel = device.label
                def labelTrimmed = currentLabel.trim()
                if (currentLabel != labelTrimmed) {
                    log.error "Trailing spaces still in LABEL for '${device.displayName}': '${currentLabel}'"
                    return
                }

                if(setName){
                    // just remove white spaces to the name (for now)
                    if (isMeshDevice(device)){ // don't change a non-Mesh device's original name. 
                        device.setName(cleanedName)
                        log.warn "Name updated successfully for '${device.name}' to '${cleanedName}'"
                    }

                    
                }

                if (enabledebug) log.debug "'${device.displayName}' => '${str}' => then => '${cleanedName}'"

            }
        }
        log.info "Done!"
    }

}

