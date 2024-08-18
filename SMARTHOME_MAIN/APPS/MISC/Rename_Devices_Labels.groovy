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
            atomicState.toRemove = toRemove?.split(",")
            log.debug "atomicState.toRemove = $atomicState.toRemove"

            input "setName", "bool", title: "also change the device's name (not just its label)"
            input "setLabelFromName", "bool", title: "The device label must be the device's name"

            input "execute", "button", title: "execute"
        }


    }
}

def installed()
{
}

def updated()
{
}

def appButtonHandler(btn) {
    // def alert = com.eviware.soapui.support.UISupport;
    switch (btn) {
        case "execute":
            if (!atomicState.toRemove) {
                log.warn "empty string!"
            }
            else {
                execute()
            }
            break
    }
}

def execute()
{
    if (allDevices) {
        for (int i = 0; i < allDevices.size(); i++)
        {
            def device = allDevices[i]
            //log.debug "renaming $device ($device.displayName)"
            def str = device.displayName
            def strRem = atomicState.toRemove

            /******************************************************************************************
                        THIS SECTION DOES NOT WORK. 
                        
                        TEST BEFORE ATTEMPTING TO USE. NEEDS FURTHER RESEARCH ON device.name. 
                        
                        Example of problem encountered: 
                        
                        logs from (log.warn "${str} == ${device.name} | ${str == device.name}") 
                        oupput this:
                        
                        'Light cat feeder == Light cat feeder on Home 2 | false'
                        
                        While in U.I. Device Name* = Light cat feeder... !!!!!!!!
            
                        log.warn "${str} == ${device.name} | ${str == device.name}"
                        if (setLabelFromName && str != "${device.name}") {
                            try {
                                log.debug "Renaming ${str} as ${device.name}"
                            } catch (Exception e) {
                                log.error "setLabelFromName ==> ${e}"
                            }
                        }
            ******************************************************************************************/

            for (int a = 0; a < strRem.size(); a++)
            {
                if (str.contains(strRem[a])) {
                    log.debug "contains $toRemove"
                    str = str - strRem[a]
                    //log.debug "new device name is '$str'"
                    device.setLabel(str)

                }
                // log.debug "device.name = $device.name"
                if (setName && device.name.contains(strRem[a])) {
                    // log.debug "also setting the updating device's name"
                    device.setName(str)

                }

            }
        }
    }

}

