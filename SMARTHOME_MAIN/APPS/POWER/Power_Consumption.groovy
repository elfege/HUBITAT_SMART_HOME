/**
*  
*
*  Author: ELFEGE
*/
definition(
    name: "POWER CONSUMPTION",
    namespace: "elfege",
    author: "elfege",
    description: "Control How Much Power Your Home Is Consumming",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",

)

preferences {
    page(name: "settings", title: "Set preferences", uninstal: true, install: true)
}

def settings(){
    dynamicPage(name: "settings", title: "Set preferences", uninstal: true, install: true){

        section("Select Your Power Meters") {
            input "devices", "capability.powerMeter", title: "Power Meters", required:false, multiple: true, submitOnChange: true      
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        }
    }
}

def installed() {
        init()

}

def updated() {
    unsubscribe()
    unschedule()
    init()

}

def init() {

    state.lastOn = now() as Long
    state.logCmdSent = false

    int i = 0
    int s = devices.size()
   
    for(s != 0; i < s; i++)
    {
        subscribe(devices[i], "power", powerHandler)
    }
    
    
    log.debug """initialized with settings: $settings"""
    eval()
    ref()
}

def logsOff() {
    log.warn "debug logging disabled..."
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def powerHandler(evt){
    //log.debug "$evt.device turned $evt.value"
    
  eval()

}

def eval()
{
   if (logEnable && !state.logCmdSent)
    {
        runIn(1800, logsOff)
        state.logCmdSent = true
    }
    
    float totalPower = 0 
    int i = 0
    int s = devices.size()
    def a
    for(s != 0; i < s; i++)
    {
               
        def pw = devices[i].currentValue("power")
       
        if (logEnable)
        log.debug "${devices[i]} returns ${pw}Watts"
        
        if(pw != null && pw > 0)
        {
        totalPower += pw
        }
    }


    log.info "Total Power Consumtion in Your House is: $totalPower"
        
    
}

def ref()
{
    float totalPower = 0 
    int i = 0
    int s = devices.size()
    def a
    for(s != 0; i < s; i++)
    {
        if(devices[i].hasCapability("Refresh"))
        {
            if (logEnable)
            {
                log.debug "refreshing ${devices[i]}"
            }
            devices[i].refresh()
        }
    }
    runIn(240, ref)
}
