script to check/uncheck all devices in Hubitat's HomeKit Integration 


const b = $( "[id^='homeKitDevice']" )

for(d of b) {
    const device = $(`#${d.id}`)

    console.log(device.attr("class"))
    if(device.attr("class") === "he-checkbox-checked cursor-pointer pr-3"){
        
    device.click()
    }
}