const smartDevice = /android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(navigator.userAgent.toLowerCase());
let access_token;
let ip;
let ip2; // only for modes updates on all hubs
let ip3;
let ip4;
let appNumber;
let everythingUrl;
let modesUrl;
let labelLength = 35;
let allDevices = [];

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
    console.log(allDevices);
  });
});

async function getCredentials() {
  const response = await axios.get("\\credentials.json");
  console.log("response.data => ", response.data);
  access_token = response.data.access_token;
  ip = response.data.ip;
  appNumber = response.data.appNumber;
  everythingUrl = "http://" + ip + "/apps/api/" + appNumber + "/devices/all?access_token=" + access_token;
  modesUrl = "http://" + ip + "/apps/api/" + appNumber + "/modes/all?access_token=" + access_token;
  getMode(modesUrl)
}

async function initialize(access_token, ip, appNumber) {
  console.log("initialize...");

  WebSocket_init(ip);

  await axios.get(everythingUrl).then(res => {
    // console.log("response:", res.data)

    console.log("res.data instanceof Object", res.data instanceof Object)
    console.log("Array.isArray(res.data)", Array.isArray(res.data))

    const d = res.data[0].id
    console.log(d, "is Integer", " ", Number.isInteger(d))


    // sort all data by labels by alphab. order. 
    const array = []
    res.data.forEach((e) => {
      array.push(e.label)
    })
    array.sort().forEach((e) => {
      allDevices.push(res.data.find(it => it.label === e))
    })

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

          $("#lights").append(
            $("<button>")
              .addClass("btn btn-primary tiles")
              .attr({ id: id_html, "data-id_From_Hub": id_From_Hub, "data-device-type": `${deviceType}` }
              )
              .text(trimLabel(e.label, labelLength))
              .append($("<img>").attr({
                "src": "images/lightOff.png",
                "id": "bulb" + id_From_Hub
              }).css({
                "position": "relative",
                "float": "left",
                "left": "0px"
              }))
          );

          $(`#${id_html}`).on("click", () => {
            console.log("id_From_Hub => ", id_From_Hub);
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/toggle?access_token=${access_token}`;
            sendCommand(url);
          });

          const state = e.attributes.switch
          // console.log("e.currentValue = ", state)
          if (state === "on") {
            $(`#bulb${id_From_Hub}`).attr("src", "images/lightOn.png")
          }
          else {
            $(`#bulb${id_From_Hub}`).attr("src", "images/lightOff.png")
          }
          const clsRemove = state === "on"
            ? "off"
            : "on";
          $(`#${id_html}`).removeClass(clsRemove);
          $(`#${id_html}`).addClass(state);

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

          console.log("/************************CREATE LOCKS ********************************/");
          $("#rowLocks").append($("<div>").addClass("col-lg-fill"))
            .append($("<button>")
              .addClass("btn btn-primary tiles")
              .attr({ id: `${id_html}`, "data-id_From_Hub": id_From_Hub, "data-device-type": "lock" })
              .text(e.label.toLowerCase().replace("lock", ""))
              .css("text-transform", "capitalize"));

          const state = e.attributes.lock;
          const classToRemove = state === "locked"
            ? "btn btn-success bi bi-unlock"
            : "btn btn-warning bi bi-lock";

          const classToAdd = state === "locked"
            ? "btn btn-warning bi bi-lock"
            : "btn btn-success bi bi-unlock";

          $(`#${id_html}`).removeClass(classToRemove).addClass(classToAdd);

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

          $("#switches").append($("<button>").addClass("btn btn-primary tiles").attr({ id: id_html, "data-id_From_Hub": id_From_Hub, "data-device-type": `${deviceType}` }).text(trimLabel(e.label, labelLength)));

          const hasPower = Object.values(e.attributes).find(val => val === "power");
          if (e.attributes.power !== null && e.attributes.power !== undefined) {
            $(`#${id_html}`).text(`${e.label} \n ${e.attributes.power}W`);
          }
          if (e.label.toLowerCase().includes("fan")) {
            const imgpath = e.attributes.switch === "on"
              ? "/images/fan.gif"
              : "/images/fan.png";
            $(`#${id_html}`).append($("<img>").addClass("img-fluid").attr("src", imgpath).css({ width: "20%", "z-index": "20" }));
          }

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
    setTimeout(restart, 1000);
  });
}
async function sendCommand(cmdurl) {
  axios.get(cmdurl).then(resp => {
    console.log(resp);
  }).catch(err => console.log(err));
}
async function getMode(url) {

  axios.get(modesUrl)
    .then(modes => {
      const all = modes.data
      console.log("modes: ", all)
      const currentMode = all.find(e => e.active).name
      console.log("currentMode => ", currentMode)
      $("#currentMode").text(currentMode)

      // const drop = $("#modesDrop")
      for (m of all) {
        console.log("***********", m.name)
        $("#modesDrop").append(
          $("<a>").attr({
            "id": `${m.name}Mode`,
            "href": `javascript:setMode("${m.name}", "${m.id}")`
          })
            .addClass("dropdown-item")
            .text(m.name))


      }

    })
    .catch(err => console.log("ERROR GETTING MODES => ", err))


}

async function setMode(mode, id) {
  console.log("setting location mode to ", mode)
  const url = "http://" + ip + "/apps/api/" + appNumber + "/modes/" + id + "?access_token=" + access_token
  axios.get(url)
    .then(resp => console.log(resp))
    .catch(err => console.log("Mode Update failed => ", err))
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

    if (type !== "switch&Level" && type !== "lock") {
      const clsRemove = state === "on"
        ? "off"
        : "on";
      $(`#${id_html}`).removeClass(clsRemove);
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
    // console.log(evt.displayName, evt.name, "is", evt.value);

    const isDimCapable = allDevices.find(el => el.id === `${evt.deviceId}`)
      ?.capabilities
      ?.find(el => el === "SwitchLevel");

    const device = evt.name !== "level"
      ? $(`*[data-id_From_Hub="${evt.deviceId}"]`)
      : $(`*[data-id_From_Hub_level="${evt.deviceId}"]`);

    const states = ["on", "off", "locked", "unlocked"];

    if (evt.name === "power") {
      $(`#${evt.deviceId}switch`).text(`${evt.displayName} \n ${evt.value}W`);
    } else if (evt.name === "lock") {
      const classToRemove = evt.value === "locked"
        ? "btn btn-warning bi bi-unlock"
        : "btn btn-success bi bi-lock";

      const classToAdd = evt.value === "locked"
        ? "btn btn-warning bi bi-lock"
        : "btn btn-success bi bi-unlock";

      $(`#${evt.deviceId}lock`).removeClass(classToRemove).addClass(classToAdd);

      // DIMMERS
    } else if (evt.name === "level") {
      updateDimmerState(device, evt.deviceId, evt.name, evt.value);

      // switches or devices as switches
    } else if (states.find(e => e === evt.value)) {
      if (isDimCapable) {
        //switch with level capab.
        updateDimmerState(device, evt.deviceId, evt.name, evt.value);
      } else {
        // for regular tiles only
        const clsRemove = evt.value === "on"
          ? "off"
          : "on";

        if (evt.value === "on") {
          $(`#bulb${evt.deviceId}`).attr("src", "images/lightOn.png")
        }
        else {
          $(`#bulb${evt.deviceId}`).attr("src", "images/lightOff.png")
        }
        console.log("updating state class for ", device);
        console.log("removing class ", clsRemove);
        device.removeClass(clsRemove);
        console.log("Adding class ", evt.value);
        device.addClass(evt.value);
      }

      if (evt.displayName.toLowerCase().includes("fan")) {
        const tile = $(`#${evt.deviceId}switch`);
        const imgpath = evt.value === "on"
          ? "/images/fan.gif"
          : "/images/fan.png";

        $(`#${evt.deviceId}switch img:last-child`).remove();
        $(`#img${evt.deviceId}switch`).remove();

        tile.append($("<img>").addClass("img-fluid").attr({ src: imgpath, id: `img${evt.deviceId}switch` }).css({ width: "20%", "z-index": "20" }));

        tile.removeAttr("src");

        console.log(`***************${evt.deviceId}`);
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

  // console.log(`
  // deviceId = ${deviceId}
  // evtName = ${evtName}
  // value = ${value}
  // devAsDimSpan = ${$(`#${deviceId}dimSpan`)}
  // `);

  //update the dimmer's properties AS A SWITCH TILE

  const devAsSwitch = $(`*[data-id_From_Hub="${deviceId}"]`);

  devAsDimSpan.css("color", color);

  if (evtName === "switch") {
    console.log("******************");
    const clsRemove = value === "on"
      ? "off"
      : "on";
    console.log("updating state class for ", device);
    console.log("removing class", clsRemove);
    device.removeClass(clsRemove);
    console.log("adding class ", value);
    device.addClass(value);

    if (value === "on") {
      $(`#bulb${deviceId}`).attr("src", "images/lightOn.png")
    }
    else {
      $(`#bulb${deviceId}`).attr("src", "images/lightOff.png")
    }
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



jQuery(() => {
  $("#lightsToggle").click(togglePanels);
  $("#switchesToggle").click(togglePanels);
  $("#dimmersToggle").click(togglePanels);
  $("#locksToggle").click(togglePanels);
  $("#showAll").click(togglePanels);

  function togglePanels(e) {
    switch (this.id) {
      case "lightsToggle":
        console.log("case: ", "lightsToggle");

        $("#lightsCol").removeAttr("hidden");
        $("#otherSwitchesCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);

        $("#lightsToggle").addClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").removeClass("active");
        break;

      case "switchesToggle":
        console.log("case: ", "switchesToggle");

        $("#otherSwitchesCol").removeAttr("hidden");
        $("#lightsCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);
        $("#locksCol").attr("hidden", true);

        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").addClass("active");
        $("#dimmersToggle").removeClass("active");
        $("#locksToggle").removeClass("active");
        break;

      case "dimmersToggle":
        console.log("case: ", "dimmersToggle");

        $("#dimmersCol").removeAttr("hidden");
        $("#lightsCol").attr("hidden", true);
        $("#otherSwitchesCol").attr("hidden", true);
        $("#locksCol").attr("hidden", true);

        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").addClass("active");
        $("#locksToggle").removeClass("active");
        break;

      case "locksToggle":
        console.log("case: ", "locksToggle");

        $("#locksCol").removeAttr("hidden");
        $("#lightsCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);
        $("#otherSwitchesCol").attr("hidden", true);

        $("#locksToggle").addClass("active");
        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").removeClass("active");
        break;

      case "showAll":
        $("#lightsCol").removeAttr("hidden");
        $("#otherSwitchesCol").removeAttr("hidden");
        $("#dimmersCol").removeAttr("hidden");
        $("#locksCol").removeAttr("hidden");

        $("#lightsToggle").addClass("active");
        $("#switchesToggle").addClass("active");
        $("#dimmersToggle").addClass("active");
        $("#locksToggle").addClass("active");
        break;

    }
  }

  $("#lightsToggle").click();
});

//reload every 10 hours to refresh with recent modifications (new devices, UI changes, etc.)
setTimeout(restart, 10 * 60 * 60 * 1000);

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
