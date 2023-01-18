const smartDevice = /android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(navigator.userAgent.toLowerCase());
let access_token;
let ip;
let appNumber;
let everythingUrl;
let labelLength = 35;
let allDevices = {};

// const modes = "http://" + ip + "/apps/api/" + appNumber + "/modes?/all??access_token=" + access_token;

jQuery(function () {
  console.log("dom loaded");
  // allDevices = initialize(access_token, ip, appNumber);
  console.log(JSON.stringify(allDevices));
  //delete all comments so they don't show in dev tools
  $("*").contents().filter(function () {
    return this.nodeType == 8;
  }).remove();

  console.log("allDevices:", allDevices);
});

getCredentials().then(() => {
  console.log(`
  ---
  access_token => ${access_token}
  ip => ${ip}
  appNumber => ${appNumber}
  everythingUrl => ${everythingUrl}
  ---
  `);
  initialize(access_token, ip, appNumber).then(() => {
    console.log(allDevices)
  })
});

async function getCredentials() {
  const response = await axios.get("\\credentials.json");
  console.log("response.data => ", response.data);
  access_token = response.data.access_token;
  ip = response.data.ip;
  appNumber = response.data.appNumber;
  everythingUrl = "http://" + ip + "/apps/api/" + appNumber + "/devices/all?access_token=" + access_token;

  
}

async function initialize(access_token, ip, appNumber) {
  console.log("initialize...");

  WebSocket_init(ip);

  await axios.get(everythingUrl).then(res => {
    // console.log("response:", res.data)
    allDevices = res.data;

    allDevices.forEach((e, index) => {
      const id_From_Hub = e.id;

      const isLock = e.capabilities.find(el => el === "Lock");
      const isLight = e.name.toLowerCase().includes("light") || e.label.toLowerCase().includes("light");
      const isSwitchLevel = e.capabilities.find(el => el === "SwitchLevel");
      const isSwitch = e.capabilities.find(el => el === "Switch") && !isSwitchLevel;
      let notAButton = !e.capabilities.find(el => el.toLowerCase().includes("button"));
      notAButton = notAButton
        ? notAButton
        : e.type.toLowerCase().includes("button")
          ? false
          : true;

      const deviceType = isLock
        ? "lock"
        : isSwitchLevel
          ? "dimmer"
          : isLight
            ? "light"
            : "otherSwitch";

      if (notAButton && (isLight || isLock || isSwitchLevel)) {
        if (isLight) {
          const id_html = id_From_Hub + "light";

          $("#lights").append($("<button>").addClass("tiles").attr({id: id_html, "data-id_From_Hub": id_From_Hub, "data-device-type": `${deviceType}`}).text(trimLabel(e.label, labelLength)));

          $(`#${id_html}`).on("click", () => {
            console.log("id_From_Hub => ", id_From_Hub);
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/toggle?access_token=${access_token}`;
            sendCommand(url);
          });

          updateDeviceState(id_From_Hub, id_html, "switch", notAButton);
        }
        if (isSwitchLevel) {
          const id_From_Hub_level = id_From_Hub;
          const id_html = id_From_Hub_level + "leveldiv";
          const idspan = id_From_Hub_level + "dimSpan";
          const label = trimLabel(e.label, labelLength);

          /******************CREATE DIMMERS****************** */
          // console.log(index)
          $("#dimmers").append($("<div>").attr("id", id_html).addClass("float-left mt-5 mr-2 text-center dimmerObject").roundSlider().attr({
            id: id_html, "data-id_From_Hub_level": id_From_Hub_level, // every dimmer is also a switch, so we need a different data attr here.
            "data-device-type": `${deviceType}`
          }));

          // Call the roundSlider
          $(`#${id_html}`).roundSlider();
          //create object
          const Obj = $(`#${id_html}`).data("roundSlider");
          //enable svg Mode
          Obj.option("svgMode", true);

          // prepend device name to leveldiv => must be done after enabling svg mode
          $(`#${id_html}`).prepend($("<span>").attr({
            id: idspan, "data-id_From_Hub": id_From_Hub, // we want the switch on/off value here
            "data-spandimmer-state": ""
          }).addClass("spanDimmer").text(label));

          $(`#${id_html}`).on("change", function (evt) {
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub_level}/setLevel/${evt.value}?access_token=${access_token}`;
            sendCommand(url);
          });

          const level = e.attributes.level;

          Obj.option("value", level);
          updateDeviceState(id_From_Hub_level, id_html, "switch&Level", notAButton, level); // request update for its on/off switch state
        }
        if (isLock) {
          const id_html = id_From_Hub + "lock";

          /************************CREATE LOCKS ********************************/
          $("#lockContainer").append($("<div>").addClass("row")).append($("<a>").addClass("btn btn-outline-primary btn-block text-white").attr({id: `${id_html}`, "data-id_From_Hub": id_From_Hub, "data-device-type": "lock"}).text(e.label));

          $(`#${id_html}`).on("click", () => {
            axios.get(`http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}?access_token=d6699bb3-0d13-48d1-ab5d-8cc583efa76c`).then(resp => {
              const data = resp.data;
              console.log("data => ", data);
              const state = data.attributes.find(val => {
                if (val.name === "lock") {
                  return val.currentValue;
                }
              }).currentValue;
              console.log(
                `${data.label} is ${state}. ${state === "locked"
                ? "unlocking"
                : "locking"}`);
              const cmd = state === "locked"
                ? "unlock"
                : "lock";
              const url = `http://${ip}/apps/api/${appNumber}/devices/${data.id}/${cmd}?access_token=${access_token}`;
              sendCommand(url);
            });
          });
        }
      } else {
        /* ALL OTHER DEVICES */
        /************************CREATE OTHER SWITCHES********************************/
        if (isSwitch && notAButton) {
          const id_html = id_From_Hub + "switch";

          $("#switches").append($("<button>").addClass("tiles").attr({id: id_html, "data-id_From_Hub": id_From_Hub,  "data-device-type": `${deviceType}`}).text(trimLabel(e.label, labelLength)));

          $(`#${id_html}`).on("click", () => {
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/toggle?access_token=${access_token}`;
            sendCommand(url);
          });

          updateDeviceState(id_From_Hub, id_html, "switch", notAButton);
        }
      }
    });
  }).then(resp => {
    $("#loading_message_container").remove();
    $("#master_container").removeAttr("hidden");
  }).catch(error => {
    console.log("error: ", error);
    setTimeout(restart, 5000);
  });
}
async function sendCommand(cmdurl) {
  axios.get(cmdurl).then(resp => {
    console.log(resp);
  }).catch(err => console.log(err));
}

//used only when document is loaded for the first time
async function updateDeviceState(id_From_Hub, id_html, type, notAButton, value) {
  const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}?access_token=d6699bb3-0d13-48d1-ab5d-8cc583efa76c`;
  // console.log("updateDeviceState url => ", url)

  axios.get(url).then(resp => {
    const data = resp.data;
    const state = data.attributes.find(val => {
      if (val.name === "switch") {
        return val.currentValue;
      }
    })
      ?.currentValue;

    if (type !== "switch&Level") {
      const clsRemove = state === "on" ? "off" : "on"
      $(`#${id_html}`).removeClass(clsRemove)
      $(`#${id_html}`).addClass(state);
    }

    if (type === "switch&Level") {
      let color = value > 1 && state === "on"
        ? "yellow"
        : "white";
      $(`#${id_From_Hub}dimSpan`).css("color", color);
      $(`#${id_html}`).roundSlider();
      const Obj = $(`#${id_html}`).data("roundSlider");

      Obj.option("value", value);
      Obj.option("tooltipColor", color);
      Obj.option("borderColor", color);
      Obj.option("pathColor", color);
    }
  });
}

function trimLabel(label, length) {
  label = label.replace(")", "");
  label = label.replace("(", "");
  label = label.replace("-", "");
  label = label.replace("OFFLINE", "");
  label = label.replace("on Home 1", "");
  label = label.replace("on Home 2", "");
  label = label.replace("on Home 3", "");
  label = label.replace("on HOME 1", "");
  label = label.replace("on HOME 2", "");
  label = label.replace("on HOME 3", "");
  label = label.replace("temperature", "temp.");
  label = label.replace("Temperature", "temp.");

  if (label.length > length) {
    label = label.substr(0, length);
  }
  return label;
}

function WebSocket_init(ip) {
  console.log("ip => ", ip);

  // Create WebSocket connection.
  const socket = new WebSocket(`ws://${ip}/eventsocket`);
  // Connection opened
  socket.addEventListener("open", event => {
    socket.send("Hello Server!");
  });
  // Listen for messages
  socket.addEventListener("message", event => {
    const evt = JSON.parse(event.data);
    console.log(evt.displayName, evt.name, "is", evt.value);


    const isDimCapable = allDevices.find(el => el.id === `${evt.deviceId}`)?.capabilities?.find(el => el === "SwitchLevel");

    const device = evt.name !== "level"
      ? $(`*[data-id_From_Hub="${evt.deviceId}"]`)
      : $(`*[data-id_From_Hub_level="${evt.deviceId}"]`);
    const states = ["on", "off", "locked", "unlocked"];

    

    if (evt.name === "level") {
      // DIMMERS
      console.log("*************************************** ", evt.value);
      updateDimmerState(device, evt.deviceId, evt.name, evt.value);
    } else if (states.find(e => e === evt.value)) {
      // switches or devices as switches

      if (isDimCapable) {
        //switch with level capab.
        updateDimmerState(device, evt.deviceId, evt.name, evt.value);
      } else {
        // for regular tiles only
        const clsRemove = evt.value === "on" ? "off" : "on"
        console.log("updating state class for ", device);
        console.log("removing class ", clsRemove)
        device.removeClass(clsRemove)
        console.log("Adding class ", evt.value)
        device.addClass(evt.value);
        
      }
    }
  });
  socket.addEventListener("close", event => {
    console.log("connexion to ", origin, " closed... reloading");
    setTimeout(restart, 1000);
  });
  socket.addEventListener("failed", event => {
    console.log("connexion to ", origin, " failed... reloading");
    setTimeout(restart, 100);
  });
}

//recurrent call by websocket event listener
function updateDimmerState(device, deviceId, evtName, value) {
  const state = parseInt(value) > 0
    ? "on"
    : "off";
  const color = state === "on"
    ? "yellow"
    : "white";

  const devAsDimSpan = $(`#${deviceId}dimSpan`);

  console.log(`
  deviceId = ${deviceId}
  evtName = ${evtName}
  value = ${value}
  devAsDimSpan = ${$(`#${deviceId}dimSpan`)}
  `);

  //update the dimmer's properties AS A SWITCH TILE

  const devAsSwitch = $(`*[data-id_From_Hub="${deviceId}"]`);

  devAsDimSpan.css("color", color);

  if (evtName === "switch") {
    console.log("******************")
    const clsRemove = value === "on" ? "off" : "on"
    console.log("updating state class for ", device);
    console.log("removing class", clsRemove)
    device.removeClass(clsRemove)
    console.log("adding class ", value)
    device.addClass(value);
  }

  // select the ROUNDSLIDER object
  const devAsSlider = $(`*[data-id_From_Hub_level="${deviceId}"]`);
  devAsSlider.roundSlider();

  //update object's path, tooltip and border colors whether it's an on/off or a level event
  const Obj = devAsSlider.data("roundSlider");
  Obj.option("tooltipColor", color);
  Obj.option("borderColor", color);
  Obj.option("pathColor", color);

  if (evtName == "level") {
    // only if it's a level event
    Obj.option("value", value);
  }
}

function restart() {
  location.reload();
}

/*
       structure of message:

       "name":"power",
       "displayName" : "Server Room ",
        "value" : "251.001",
        "type" : "null",
        "unit":"W",
       "deviceId":83,
       "hubId":0,
       "installedAppId":0,
       "descriptionText" : "Server Room power is: 251.001W"}
       */
//http://192.168.10.15:20010/event, => for homebridge, to be put back into makerAPI post section
//
// const id_From_Hub = device.attr("data-id_From_Hub_level");
