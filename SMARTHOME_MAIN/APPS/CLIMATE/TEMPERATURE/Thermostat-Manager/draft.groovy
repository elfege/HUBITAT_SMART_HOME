
def set_mode(mode, origin){
    if(differentiateThermostatsHeatCool)
    {
        append_multiple_thermostats()  

        for(int i = 0; i < atomicState.neededThermostats.size(); i++)
        {
            set_thermostat_mode(atomicState.neededThermostats[i], "off", origin)                                    
        }
    }
    else{
        set_thermostat_mode(thermostat, "off", origin)
    }
}
def set_target(cmd, target, origin){
    if(differentiateThermostatsHeatCool)
    {
        append_multiple_thermostats()                
        for(int i = 0; i < atomicState.neededThermostats.size(); i++)
        {
            set_thermostat_target(atomicState.neededThermostats[i], cmd, target, origin)                                    
        }
    }
    else{
        set_thermostat_target(thermostat, cmd, target, origin)
    }
}
def set_thermostat_mode(t, mode, origin){
    try {
        log.trace "$t set to $mode (origin: $origin)"
        t.setThermostatMode(mode)
    }
    catch (Exception e){
        log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}
def set_thermostat_target(t, cmd, target, origin){
    try {
        log.trace "$t set to $mode (origin: $origin)"
        t."${cmd}"(target)
    }
    catch (Exception e){
        log.warn "Object class error for 't' in set_thermostat - item skipped: $e"
    }
}