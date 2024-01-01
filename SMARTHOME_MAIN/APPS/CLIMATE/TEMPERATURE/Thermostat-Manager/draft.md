Big app for Hubitat. Not asking you to fully grasp it, it's probably beyond your current capabilities.

However, I would love to find inconsistencies that lead to the following behavior:

As you may be able to see, there is a pushableButton capability, and a pushableButtonHandler that sets the atomicState.buttonPushed boolean.

This boolean is called in many of the functions and methods to apply different settings. Basically, it's used when I go to bed so I have a specific set point value when I sleep.

The app also works on the following mechanism: it reads the value of a dimmer to set the temperature: 74% is read as 74°F as a target temperature to reach.

This "trick" is to allow to set the temperature easily with Alexa, the dimmer being called, for instance, "temperature living" - Alexa doesn't do so well with thermostat capabilities.

Now, there is a second dimmer used when the pushableButton is pushed and the atomicState.buttonPushed boolean is therefore set to true: this is where the app will get the new desired temperature from. I call, in

my hub, this dimmer "temperature bed time" so I can just say "Alexa, set temperature bedtime to 68°F" and then press the button. As you can see, maybe, in the code, when the atomicState.buttonPushed boolean is set to true

I also get a visual confirmation calling the flashTheLight() method. No visual confirmation when the boolean is reset to false either after a customizable period of time, or after pushing the same button again.

Now, you can also see that I have a complex setup of capabilities allowing users to use 2 thermostats:

```Groovy
 input "useBothThermostatsForHeat", "bool", title: "Use both when in heat mode", submitOnChange:true, defaultValue:false
                input "useBothThermostatsForCool", "bool", title: "Use both when in cool mode", submitOnChange:true, defaultValue:false
                input "thermostatHeat", "capability.thermostat", title: "Select a thermostat used exclusively for heating", required:true, submitOnChange:true
                input "thermostatCool", "capability.thermostat", title: "Select a thermostat used exclusively for cooling", required: true, submitOnChange:true
                input "keep2ndThermOffAtAllTimes", "bool", title: "Keep the unused thermostat off at all times (if enabled, you can't use this thermostat for any other purpose, it'll be shut down within a minute after you turn it on when not in use by this app",
                defaultValue:true, submitOnChange: true

```
