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
  // console.log(JSON.stringify(allDevices));
  //delete all comments so they don't show in dev tools
  $("*").contents().filter(function () {
    return this.nodeType == 8;
  }).remove();

  console.log("allDevices:", allDevices);

});
getSettings()
  .then(() => {
    initialize(access_token, ip, appNumber)
      .then(() => {
        // console.log(allDevices);
      });
  })
  .catch(() => {
    overlayOn("SOMETHING WENT WRONG");
    setTimeout(restart, 2000);
  })

async function getSettings() {
  const response = await axios.get("\\settings.json");
  console.log("response.data => ", response.data);

  // let { access_token, ip, appNumber, ...other } = response.data

  console.log("access_token: ", access_token)
  console.log("ip: ", ip)
  console.log("appNumber: ", appNumber)


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
      const isSwitchLevel = e.capabilities.includes("SwitchLevel");
      const isSwitch = e.capabilities.includes("Switch") && !isSwitchLevel;
      const isWindow = e.label.toLowerCase().includes("window")
      const isthermostat = e.capabilities.includes("Thermostat")
      const isPowerMeterOnly = e.capabilities.includes("PowerMeter") && !isSwitch

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

      if (e.label.toLowerCase().includes("window")) {
        let { name, ...other } = e
        // console.log(name, other)
      }
      if (notAButton && (isLight || isLock || isSwitchLevel || isSwitch)) {
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

          // console.log("/************************CREATE LOCKS ********************************/");
          $("#rowLocks").append($("<div>").addClass("col-lg-fill"))
            .append($("<button>")
              .addClass("btn btn-primary tiles")
              .attr({ id: `${id_html}`, "data-id_From_Hub": id_From_Hub, "data-device-type": "lock" })
              .text(trimLabel(e.label.toLowerCase().replace("lock", "")))
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

        /************************ALL OTHER DEVICES + all dimmers as switch buttons********************************/

        if (!isLight && !isLock && (isSwitch || isSwitchLevel)) {

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
      if (isthermostat) {
        $("#thermostats").append(`
          <section class="thermostatWrap">
            <span class="spanThermostat"> ${e.label}</span>
            <div class="thermostat"> 
            <div class="temperature" id=temperature${id_From_Hub} role="slider" aria-valuenow="72" aria-valuemin="0" aria-valuemax="100"></div> 
            </div>
            <div class="sliderWrapper"><input class="tempSlider" id=thermostat${id_From_Hub} type="range" value="72" min="0" max="100" /></div>
          </section> 
        `)

        const radiusPercent = $(`#temperature${id_From_Hub}`);
        const slider = $(`#thermostat${id_From_Hub}`)

        const increment = (radius) => {
          const value = `${radius}%`;
          radiusPercent.attr("aria-valuenow", value);
          radiusPercent.css("--radius", value);
          radiusPercent.html(parseFloat(value));
        };

        const currentValue = e.attributes.thermostatSetpoint

        // console.log(e.label, " value => ", currentValue)
        increment(currentValue);

        // change ui while sliding
        slider.on("input", (evt) => {
          console.log("evt ============> ", evt.target.value)

          increment(evt.target.value);
        });

        //send command once mouse is up 
        slider.on("change", (evt) => {
          const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/setLevel/${evt.target.value}?access_token=${access_token}`;
          console.log(url)
          // TODO : thermostats specific commands... 
          // sendCommand(url);
        })
      }
      if (isPowerMeterOnly) {
        console.log(e.label, " is a power meter")
        const v = e.attributes.power
        if (v !== null && v !== undefined) {
          const nav = $("#row-nav-buttons")
          nav.prepend(`
            <div id="power${id_From_Hub}" class="col-fluid m-1 block">
              <button id="pwr${id_From_Hub}" class="btn btn-outline-warning bt-block navButton m-0">POWER</button>
            </div>
            `)
          const value = `${v} Watts`
          console.log(value)
          $(`#pwr${id_From_Hub}`).text(value)
        }
      }


    });

  }).then(resp => {
    $("#loading_message_container").remove();
    $("#master_container").removeAttr("hidden");
  }).catch(error => {
    console.log("error: ", error);

    // $("div").remove()
    $("body").append($("<div class='col-lg-4'>").text(`
    Hubitat isn't responding.
    \n${JSON.stringify(error)}
    `)
      .css("color", "white"))
    overlayOn("Page will reload in 2 seconds...")
    setTimeout(restart, 2000);
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
      // console.log("modes: ", all)
      const currentMode = all.find(e => e.active).name
      console.log("currentMode => ", currentMode)
      $("#currentMode").text(currentMode)

      // const drop = $("#modesDrop")
      for (m of all) {
        // console.log("***********", m.name)
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

  //other hubs?
  const response = await axios.get("\\settings.json");

  for (let i = 0; i < Object.values(response.data.otherHubs).length; i++) {

    const ip_ = Object.values(response.data.otherHubs)[i]
    const appNumb = Object.values(response.data.otherHubsAppNumbers)[i]
    const token = Object.values(response.data.otherHubsTokens)[i]
    console.log("updating mode for ip:", ip_, " index: ", i, " appNumb:", appNumb, " token:", token)

    const u = "http://" + ip_ + "/apps/api/" + appNumb + "/modes/" + id + "?access_token=" + token
    axios.get(u)
      .then(resp => console.log(resp))
      .catch(err => console.log("Mode Update failed => ", err))
  }
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


    //only devices with switch attribute, without level attribute
    if (type !== "switch&Level" && type !== "lock") {
      const clsRemove = state === "on"
        ? "off"
        : "on";
      $(`#${id_html}`).removeClass(clsRemove);
      $(`#${id_html}`).addClass(state);
    }

    // if the switch is also a dimmer, update slider with the dimmer's switch attribute value
    if (type === "switch&Level") {
      let color = value > 0 && state === "on"
        ? $(":root").css("--onColor")
        : $(":root").css("--offColorSlider");

      let spanDimmerColor = value > 0 && state === "on"
        ? $(":root").css("--spanDimmerOn")
        : $(":root").css("--spanDimmerOff");

      $(`#${id_From_Hub}dimSpan`).css("color", spanDimmerColor);

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
  // console.log("triming ", label)
  label = label.replace(")", "")
    .replace("(", "")
    .replace("-", "")
    .replace("OFFLINE", "")
    .replace("on Home 1", "")
    .replace("on Home 2", "")
    .replace("on Home 3", "")
    .replace("on HOME 1", "")
    .replace("on HOME 2", "")
    .replace("on HOME 3", "")
    .replace("temperature", "temp.")
    .replace("Temperature", "temp.");

  if (label.length > length) {
    label = label.substr(0, length);
  }
  // console.log("trim result: ", label)
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

    const isDimCapable = allDevices.find(el => el.id === `${evt.deviceId}`)
      ?.capabilities
      ?.find(el => el === "SwitchLevel");

    const device = evt.name !== "level"
      ? $(`*[data-id_From_Hub="${evt.deviceId}"]`)
      : $(`*[data-id_From_Hub_level="${evt.deviceId}"]`);

    const states = ["on", "off", "locked", "unlocked"];

    if (evt.name === "power") {
      $(`#${evt.deviceId}switch`).text(`${evt.displayName} \n ${evt.value}W`);

      const mainPow = document.getElementById(`pwr${evt.deviceId}`)
      console.log("*********mainPow = ", mainPow)
      if (mainPow !== null) {
        console.log("----------------MAIN POWER METER EVT-----------------")
        $(`#pwr${evt.deviceId}`).text(`${evt.value} Watts`) //update main power meter's value, if any
      }
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
        device.removeClass(clsRemove);
        // console.log("Adding class ", evt.value);
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
    overlayOn("connexion to server closed... The hub is probably reloading or shut down. Please wait about 60s.  ");

    setTimeout(restart, 60000);
  });
  socket.addEventListener("failed", event => {
    overlayOn("connexion to server failed... The hub is probably reloading or shut down. Please wait about 60s.  ");

    setTimeout(restart, 60000);
  });
}


//called by websocket event listener
function updateDimmerState(device, deviceId, evtName, value) {
  const state = parseInt(value) > 0
    ? "on"
    : "off";
  const color = state === "on"
    ? $(":root").css("--onColor")
    : $(":root").css("--offColorSlider");

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
  $("#lightsToggle").on("click", togglePanels);
  $("#switchesToggle").on("click", togglePanels);
  $("#dimmersToggle").on("click", togglePanels);
  $("#locksToggle").on("click", togglePanels);
  $("#thremostatsToggle").on("click", togglePanels);
  $("#showAll").on("click", togglePanels);



  function togglePanels(e) {

    const background = $(":root").css("--baseBackground")

    switch (this.id) {
      case "lightsToggle":
        console.log("case: ", "lightsToggle");

        $(document.body).css("background", background)

        $("#lightsCol").removeAttr("hidden");
        $("#otherSwitchesCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);

        $("#lightsToggle").addClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").removeClass("active");
        break;

      case "switchesToggle":
        console.log("case: ", "switchesToggle");

        $(document.body).css("background", background)

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

        if (smartDevice) {
          $("body").css("background", "url(/images/klein_explosion.jpg)  no-repeat center center fixed")
        }
        else {
          $("body").css("background", "url(/images/klein_explosion.jpg)  no-repeat ")
        }

        $("#dimmersCol").removeAttr("hidden");
        $("#lightsCol").attr("hidden", true);
        $("#otherSwitchesCol").attr("hidden", true);
        $("#locksCol").attr("hidden", true);
        $("#thermostatsCol").attr("hidden", true);

        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").addClass("active");
        $("#locksToggle").removeClass("active");
        $("#thremostatsToggle").removeClass("active");
        break;

      case "locksToggle":
        console.log("case: ", "locksToggle");

        if (smartDevice) {
          $("body").css("background", `${$(":root").css("--locksBackground")}  no-repeat center center fixed`)
        }
        else {
          $("body").css("background", `${$(":root").css("--locksBackground")} no-repeat`)
        }

        // $(document.body).css("background", $(":root").css("--locksBackground"))

        $("#locksCol").removeAttr("hidden");
        $("#lightsCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);
        $("#otherSwitchesCol").attr("hidden", true);
        $("#thermostatsCol").attr("hidden", true);

        $("#locksToggle").addClass("active");
        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").removeClass("active");
        $("#thremostatsToggle").removeClass("active");
        break;

      case "thremostatsToggle":
        $(document.body).css("background", $(":root").css("--thermostatsBackground"))

        $("#thermostatsCol").removeAttr("hidden");
        $("#locksCol").attr("hidden", true);
        $("#lightsCol").attr("hidden", true);
        $("#dimmersCol").attr("hidden", true);
        $("#otherSwitchesCol").attr("hidden", true);

        $("#thremostatsToggle").addClass("active");
        $("#locksToggle").removeClass("active");
        $("#lightsToggle").removeClass("active");
        $("#switchesToggle").removeClass("active");
        $("#dimmersToggle").removeClass("active");

        break;

      case "showAll":

        if (smartDevice) {
          $("body").css("background", "url(/images/klein_explosion.jpg)  no-repeat center center fixed")
        }
        else {
          $("body").css("background", "url(/images/klein_explosion.jpg)  no-repeat ")
        }

        $("#lightsCol").removeAttr("hidden");
        $("#otherSwitchesCol").removeAttr("hidden");
        $("#dimmersCol").removeAttr("hidden");
        $("#locksCol").removeAttr("hidden");
        $("#thermostatsCol").removeAttr("hidden");

        $("#lightsToggle").addClass("active");
        $("#switchesToggle").addClass("active");
        $("#dimmersToggle").addClass("active");
        $("#locksToggle").addClass("active");
        $("#thremostatsToggle").addClass("active");
        break;

    }
    $("body").css({
      "-webkit-background-size": "cover",
      "-moz-background-size": "cover",
      "-o-background-size": "cover",
      "background-size": "cover",
      "background-size": "100vw 100vh",
      "background-attachment": "fixed"
    })
  }



  // $("#dimmersToggle").trigger("click")
  $("#lightsToggle").trigger("click")
  // $("#thremostatsToggle").trigger("click")
  // $("#locksToggle").trigger("click")

});


function overlayOn(text) {
  const o = $("#overlay")
  o.css("display", "block");
  $("#OverlayText").html(text + "  ")
  $("#OverlayText").append($("<button>").addClass("btn btn-primary").text("RELOAD").on("click", restart))

}

function overlayOff() {
  document.getElementById("overlay").style.display = "none";
}


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
