# hubitatWatchdog

This app runs using 3 methods (2 and 3 being optional): 

1) A routine runs based on both scheduling (1minute) and motion sensor events. If, upon a motion event, the routine detects that the last scheduled execution is past due by 30 seconds, a warning process is triggered and, if confirmed several times, then it means that CRON scheduling service is no longer responding on your hub, which is a frequent symptom of an unresponsive hub. Hub will then reboot. 

2) A virtual switch is turned on, then the app measures the time it takes for the hub to parse the switch event. If within false alarm threshold the hub remains slow to respond, the hub is rebooted. 

 <h4> Make sure you enable OAuth if you use the method 3 below: </h4>
 
3) If you install this app on 2 Hubitat hubs, they can ping each other. If one becomes unresponsive for two long, it will receive a remote reboot command. (works only if your hub doesn't require ID and password for now)
