let stateLookUp
let descr
let devices

const run = () => {
    let input = prompt("Check or Uncheck? (enter '1' for checking unchecked boxes or '2' for unchecking checked boxes):");
    console.log("You entered:", input);

    // Determine state to look up based on user input
    stateLookUp = input === '1' ? "he-checkbox-unchecked" : "he-checkbox-checked"; // if input is 1, user wants to check unchecked boxes
    descr = stateLookUp === "he-checkbox-checked" ? "Unchecking device" : "Checking device";

    updateDevices(lapse = 500)

    console.log("Devices updated. Double checking soon...")

    setTimeout(() => {
        recheck()
    }, 10000)

};

const updateDevices = (lapse = 0) => {

    let timeout = 0

    devices = $("[id^='homeKitDevice']");
    // console.log("Devices object:", devices);
    // console.log("json: ", JSON.stringify(devices))

    console.log("Devices found:", devices.length); // Check how many devices were found

    // Check if devices were found before proceeding
    if (devices.length === 0) {
        console.log("No devices found with the specified ID pattern.");
        return;
    }

    // Iterate through devices and schedule clicks
    for (d of devices) {
        console.log("device: ", d)

        let jQDevice = $(`#${d.id}`); // Wrap the current d in a jQuery object
        let className = jQDevice.attr("class") || ""; // Get the class attribute (fallback to empty)

        // Trigger click if the class matches the stateLookUp
        if (className.includes(stateLookUp)) {
            console.log(`${descr} ${jQDevice.attr('id')}, Class: ${className}`); // Log ID and class
            jQDevice.click(); // Perform the click
        }
    };
}

const recheck = () => {
    console.log("Double checking... ")

    updateDevices()

    console.log("Done!")
}

