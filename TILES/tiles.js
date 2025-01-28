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
const CancelToken = axios.CancelToken;
let pendingRequests = [];


// const modes = "http://" + ip + "/apps/api/" + appNumber + "/modes?/all??access_token=" + access_token;

jQuery(function () {

  console.log("------------- dom loaded -------------");



  // console.log(JSON.stringify(allDevices));
  //delete all comments so they don't show in dev tools
  $("*").contents().filter(function () {
    return this.nodeType == 8;
  }).remove();

  console.log("allDevices:", allDevices);


  $("#lightsToggle").on("click", togglePanels);
  $("#switchesToggle").on("click", togglePanels);
  $("#dimmersToggle").on("click", togglePanels);
  $("#locksToggle").on("click", togglePanels);
  $("#thremostatsToggle").on("click", togglePanels);
  $("#showAll").on("click", togglePanels);
  $("#refreshValues").on("click", refreshValues);



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
          $("body").css("background", "black") //"url(/images/klein_explosion.jpg)  no-repeat center center fixed")
        }
        else {
          $("body").css("background", "black") //"url(/images/klein_explosion.jpg)  no-repeat ")
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
          $("body").css("background", "black")
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
        // $(document.body).css("background", $(":root").css("--thermostatsBackground"))
        if (smartDevice) {
          $("body").css("background", "teal")
        }
        else {
          $("body").css("background", `${$(":root").css("--thermostatsBackground")} no-repeat`)
        }

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

  if (smartDevice) {
    $("#thermostats").addClass('smart-device');
  }

});

function openThermostatModal(thermostatWrap, id) {
  // Remove existing click event listener from the thermostat wrap
  $(thermostatWrap).off('click');

  // Store the next element before moving
  const nextElement = $(thermostatWrap).next();

  // Create the modal element
  const modal = $('<div>').addClass('modal fade').attr('id', `thermostatModal${id}`);
  const modalDialog = $('<div>').addClass('modal-dialog modal-dialog-centered modal-lg');
  const modalContent = $('<div>').addClass('modal-content');

  // Move the thermostat container into the modal
  $(thermostatWrap).appendTo(modalContent);

  // Add the modal structure to the page
  modal.append(modalDialog.append(modalContent));
  $('body').append(modal);

  // Show the modal
  modal.modal('show');

  // Add event listener for clicking outside modal to close it
  modal.on('click', (event) => {
    if ($(event.target).is(modal)) {
      modal.modal('hide');
    }
  });

  // When modal is hidden, move thermostat wrap back and restore click listener
  modal.on('hidden.bs.modal', () => {
    // Move thermostat wrap back to its original position
    if (nextElement.length) {
      $(thermostatWrap).insertBefore(nextElement);
    } else {
      // If it was the last element, append it
      $(thermostatWrap).appendTo('#thermostats');
    }

    // Restore original click event listener
    $(thermostatWrap).on('click', function (event) {
      if (smartDevice) {
        openThermostatModal(thermostatWrap, id);
      } else {
        $(this).toggleClass('expanded');
      }
    });

    // Remove modal from DOM after animation completes
    setTimeout(() => modal.remove(), 300);
  });
}

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
  console.log("initializing...");

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
      const hasSwitchCapability = e.capabilities.some(capability => ["Switch", "switch"].includes(capability));
      const isSwitch = hasSwitchCapability && !isSwitchLevel;
      const isWindow = e.label.toLowerCase().includes("window")
      const isthermostat = e.capabilities.includes("Thermostat")
      const isPowerMeterOnly = e.capabilities.includes("PowerMeter") && !hasSwitchCapability

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
        const hasTurbo = e.attributes.hasOwnProperty('turboMode') || e.commands.includes('controlTurboMode');

        $("#thermostats").append(`
          <div class="thermostatWrap" id="thermostatWrap${id_From_Hub}">
            <span class="spanThermostat">${e.label}</span>
            <div class="thermostat" id="thermostat${id_From_Hub}">
            </div>
            <div class="thermostat-modes" id="thermostatModes${id_From_Hub}"></div>
            ${hasTurbo ? `
              <div class="thermostat-turbo" id="thermostatTurbo${id_From_Hub}">
                <button class="btn thermostat-mode-btn mode-turbo" data-turbo="off">
                  Turbo
                </button>
              </div>` : ''}
          </div>
        `);

        const thermostatWrapSelector = `#thermostatWrap${id_From_Hub}`;
        const thermostatWrap = $(thermostatWrapSelector);

        // Setup turbo button if device has the capability
        if (hasTurbo) {
          const turboBtn = $(`#thermostatTurbo${id_From_Hub} button`);
          turboBtn.on('click', async () => {
            const currentState = turboBtn.attr('data-turbo');
            const newState = currentState === 'on' ? 'off' : 'on';
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/controlTurboMode/${newState}?access_token=${access_token}`;
            await sendCommand(url);
          });

          // Set initial state
          if (e.attributes.turboMode === 'on') {
            turboBtn.addClass('active');
            turboBtn.attr('data-turbo', 'on');
          }
        }


        // Add click/touch event listener to the thermostat container
        thermostatWrap.on('click', function (event) {
          console.log("CLICK THERMOSTAT CONTAINER")

          if (smartDevice) {
            console.log("OPENING THERMOSTAT MODAL")
            // Open the thermostat container in a modal
            openThermostatModal(thermostatWrap, id_From_Hub);
          } else {
            console.log("NOT OPENING MODAL")
            // Slide the thermostat container upon touch
            $(this).toggleClass('expanded');
          }
        });


        // Initialize the round slider
        $(`#thermostat${id_From_Hub}`).roundSlider({
          sliderType: "min-range",
          radius: 130,
          width: 20,
          value: e.attributes.thermostatSetpoint,
          min: 62,
          max: 86,
          handleSize: "+8",
          circleShape: "full",
          startAngle: 315,
          showTooltip: true,
          mouseScrollAction: true,
          tooltipFormat: function (args) {
            const currentTemp = e.attributes.temperature;
            setTimeout(() => {
              const tooltipEl = $(this.control).find('.rs-tooltip');
              tooltipEl.html(`
                    ${args.value}°F
                    <div class="current-temp">Temp: ${currentTemp}°F</div>
                `);
            }, 0);
            // Return the initial value without any symbol - the HTML update above will handle the display
            return `${args.value}`;
          },
          change: function (evt) {
            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/setHeatingSetpoint/${evt.value}?access_token=${access_token}`;
            console.log("Setting temperature to:", evt.value);
            sendCommand(url);
          }
        });

        // Add mode buttons
        const currentMode = e.attributes.thermostatMode || 'auto';
        const modeButtons = createThermostatModeButtons(id_From_Hub, currentMode);
        $(`#thermostatModes${id_From_Hub}`).empty().append(modeButtons);
      }
      if (isPowerMeterOnly) {
        console.log(e.label, " is a power meter")
        const v = e.attributes.power
        if (v !== null && v !== undefined) {
          const nav = $("#row-nav-buttons")
          nav.prepend(`
            <div id="power${id_From_Hub}" class="col-fluid m-1 block">
              <button id="pwr${id_From_Hub}" class="btn btn-outline-dark bt-block navButton m-0">POWER</button>
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
    setTimeout(restart, 60000);
  });
};
const debounce = (func, wait) => {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
};
// Debounce the sendCommand function for rapid updates
const debouncedSendCommand = debounce(sendCommand, 250);

async function sendCommand(cmdurl) {
  const source = CancelToken.source();
  pendingRequests.push(source.cancel);

  try {
    const response = await axios.get(cmdurl, {
      cancelToken: source.token
    });
    console.log(response);
  } catch (err) {
    if (!axios.isCancel(err)) {
      console.error(err);
    }
  } finally {
    pendingRequests = pendingRequests.filter(req => req !== source.cancel);
  }
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
  // Early return if missing required parameters
  if (!id_From_Hub || !id_html) {
    console.error('Missing required parameters for updateDeviceState');
    return;
  }

  // Create cancel token for this request
  const source = CancelToken.source();
  pendingRequests.push(source.cancel);

  const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}?access_token=${access_token}`;

  try {
    const response = await axios.get(url, {
      cancelToken: source.token,
      timeout: 5000 // 5 second timeout
    });

    const data = response.data;
    if (!data) {
      console.warn(`No data received for device ${id_From_Hub}`);
      return;
    }

    // Find the switch state in attributes
    const switchAttribute = data.attributes.find(val => val.name === "switch");
    const state = switchAttribute?.currentValue;

    // Handle different device types
    if (type === "switch&Level" && typeof value !== 'undefined') {
      // Handle dimmer devices
      let color = value > 0 && state === "on"
        ? $(":root").css("--onColor")
        : $(":root").css("--offColorSlider");

      let spanDimmerColor = value > 0 && state === "on"
        ? $(":root").css("--spanDimmerOn")
        : $(":root").css("--spanDimmerOff");

      // Update dimmer span color
      const dimmerSpan = $(`#${id_From_Hub}dimSpan`);
      if (dimmerSpan.length) {
        dimmerSpan.css("color", spanDimmerColor);
      }

      // Update roundSlider if it exists
      const sliderElement = $(`#${id_html}`);
      if (sliderElement.length) {
        sliderElement.roundSlider();
        const Obj = sliderElement.data("roundSlider");
        if (Obj) {
          Obj.option("value", value);
          Obj.option("tooltipColor", color);
          Obj.option("borderColor", color);
          Obj.option("pathColor", color);
        }
      }
    } else if (type !== "lock") {
      // Handle regular switches and lights
      if (state) {
        const clsRemove = state === "on" ? "off" : "on";
        const element = $(`#${id_html}`);

        if (element.length) {
          element.removeClass(clsRemove);
          element.addClass(state);

          // Update bulb image if it exists
          const bulbImage = $(`#bulb${id_From_Hub}`);
          if (bulbImage.length) {
            bulbImage.attr("src", state === "on" ? "images/lightOn.png" : "images/lightOff.png");
          }
        }

        // Handle fan specific updates if needed
        if (data.displayName && data.displayName.toLowerCase().includes("fan")) {
          const imgpath = state === "on" ? "/images/fan.gif" : "/images/fan.png";
          const tile = $(`#${id_html}`);

          tile.find('img:last-child').remove();
          tile.find(`#img${id_html}`).remove();

          tile.append($("<img>")
            .addClass("img-fluid")
            .attr({
              src: imgpath,
              id: `img${id_html}`
            })
            .css({
              width: "20%",
              "z-index": "20"
            }));
        }
      }
    }

    // Update power display if available
    const powerAttribute = data.attributes.find(val => val.name === "power");
    if (powerAttribute && powerAttribute.currentValue !== null) {
      const switchElement = $(`#${id_From_Hub}switch`);
      if (switchElement.length) {
        switchElement.text(`${data.label} \n ${powerAttribute.currentValue}W`);
      }
    }

  } catch (error) {
    if (axios.isCancel(error)) {
      console.log('Request cancelled:', id_From_Hub);
    } else if (error.code === 'ECONNABORTED') {
      console.error('Request timeout for device:', id_From_Hub);
    } else {
      console.error('Error updating device state:', id_From_Hub, error);
    }
  } finally {
    // Remove the cancel function from pending requests
    pendingRequests = pendingRequests.filter(req => req !== source.cancel);
  }
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
  let reconnectAttempts = 0;
  let maxReconnectAttempts = 5;
  let reconnectTimeout;
  let socket;

  function connect() {
    // Cancel any existing connection
    if (socket) {
      socket.close();
    }

    // Create WebSocket connection
    socket = new WebSocket(`ws://${ip}/eventsocket`);

    socket.addEventListener("open", event => {
      console.log("WebSocket connected");
      socket.send("Hello Server!");
      reconnectAttempts = 0;
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
      }
    });

    socket.addEventListener("message", event => {
      try {
        const evt = JSON.parse(event.data);
        console.log(evt.displayName, evt.name, "is", evt.value);

        // Find device capabilities
        const device = allDevices.find(el => el.id === `${evt.deviceId}`);
        if (!device) {
          console.warn(`Device not found: ${evt.deviceId}`);
          return;
        }

        const isDimCapable = device.capabilities?.find(el => el === "SwitchLevel");

        // Find device elements
        const deviceElement = evt.name !== "level"
          ? $(`*[data-id_From_Hub="${evt.deviceId}"]`)
          : $(`*[data-id_From_Hub_level="${evt.deviceId}"]`);

        const states = ["on", "off", "locked", "unlocked"];

        // Handle different types of events
        if (evt.name === "power") {
          $(`#${evt.deviceId}switch`).text(`${evt.displayName} \n ${evt.value}W`);

          const mainPow = document.getElementById(`pwr${evt.deviceId}`);
          console.log("*********mainPow = ", mainPow);
          if (mainPow !== null) {
            console.log("----------------MAIN POWER METER EVT-----------------");
            $(`#pwr${evt.deviceId}`).text(`${evt.value} Watts`);
          }
        }
        if (evt.name === "lock") {
          const classToRemove = evt.value === "locked"
            ? "btn btn-warning bi bi-unlock"
            : "btn btn-success bi bi-lock";

          const classToAdd = evt.value === "locked"
            ? "btn btn-warning bi bi-lock"
            : "btn btn-success bi bi-unlock";

          $(`#${evt.deviceId}lock`).removeClass(classToRemove).addClass(classToAdd);
        }
        if (evt.name === "level") {
          updateDimmerState(deviceElement, evt.deviceId, evt.name, evt.value);
        }
        if (states.find(e => e === evt.value)) {
          if (isDimCapable) {
            updateDimmerState(deviceElement, evt.deviceId, evt.name, evt.value);
          } else {
            const clsRemove = evt.value === "on" ? "off" : "on";

            if (evt.value === "on") {
              $(`#bulb${evt.deviceId}`).attr("src", "images/lightOn.png");
            } else {
              $(`#bulb${evt.deviceId}`).attr("src", "images/lightOff.png");
            }

            deviceElement.removeClass(clsRemove);
            deviceElement.addClass(evt.value);
          }

          // Handle fan-specific UI updates
          if (evt.displayName.toLowerCase().includes("fan")) {
            const tile = $(`#${evt.deviceId}switch`);
            const imgpath = evt.value === "on"
              ? "/images/fan.gif"
              : "/images/fan.png";

            $(`#${evt.deviceId}switch img:last-child`).remove();
            $(`#img${evt.deviceId}switch`).remove();

            tile.append($("<img>")
              .addClass("img-fluid")
              .attr({
                src: imgpath,
                id: `img${evt.deviceId}switch`
              })
              .css({
                width: "20%",
                "z-index": "20"
              }));

            tile.removeAttr("src");
          }
        }
        if (evt.name === "thermostatMode") {
          // handle thermostat mode changes
          const modesContainer = $(`#thermostatModes${evt.deviceId}`);
          if (modesContainer.length) {
            modesContainer.find('.thermostat-mode-btn').removeClass('active');
            modesContainer.find(`[data-mode="${evt.value}"]`).addClass('active');
          }
        }
        if (evt.name === "turboMode") {
          console.log("Turbo mode event received:", evt);
          const turboBtn = $(`#thermostatTurbo${evt.deviceId} button`);
          if (turboBtn.length) {
            const isOn = evt.value === "on";
            turboBtn.toggleClass('active', isOn);
            turboBtn.attr('data-turbo', evt.value);
          }
        }
        if (evt.name === "thermostatSetpoint" || evt.name === "temperature") {
          const thermostat = $(`#thermostat${evt.deviceId}`);
          if (thermostat.length) {
            const slider = thermostat.data("roundSlider");
            if (evt.name === "thermostatSetpoint") {
              slider.setValue(evt.value);
            }
            // Update the current temperature display
            if (evt.name === "temperature") {
              const tooltipEl = thermostat.find('.rs-tooltip');
              const currentValue = slider.getValue();
              tooltipEl.html(`
                ${currentValue}°F
                <div class="current-temp">Temp: ${evt.value}°F</div>
              `);
            }
          }
        }
      } catch (error) {
        console.error("Error processing WebSocket message:", error, event.data);
      }
    });

    socket.addEventListener("error", event => {
      console.error("WebSocket error:", event);
    });

    socket.addEventListener("close", event => {
      console.log("WebSocket closed. Code:", event.code, "Reason:", event.reason);

      // Attempt reconnection if not at max attempts
      if (reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++;
        console.log(`WebSocket reconnecting, attempt ${reconnectAttempts}`);
        reconnectTimeout = setTimeout(connect, 5000 * reconnectAttempts);
      } else {
        overlayOn("Connection lost after multiple attempts. Please reload the page manually.");
      }
    });


  }

  // Start initial connection
  connect();

  // Cleanup function
  function cleanup() {
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout);
    }
    if (socket) {
      socket.close();
    }
  }
  //  cancel all pending requests
  function cancelPendingRequests() {
    pendingRequests.forEach(cancel => cancel());
    pendingRequests = [];
  }

  // Add cleanup on page unload
  window.addEventListener('beforeunload', cleanup);
  window.addEventListener('beforeunload', () => {
    cancelPendingRequests();
  });

  // Return cleanup function for external use if needed
  return cleanup;
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



function overlayOn(text) {
  const o = $("#overlay")
  o.css("display", "block");
  $("#OverlayText").html(text + "  ")
  $("#OverlayText").append($("<button>").addClass("btn btn-primary").text("RELOAD").on("click", restart))

}

function overlayOff() {
  document.getElementById("overlay").style.display = "none";
}

async function refreshValues() {
  console.log("REFRESHING ALL DEVICES VALUES");
  const refreshPromises = [];

  for (const device of allDevices) {
    if (!device.capabilities.includes("Refresh")) continue;

    const url = `http://${ip}/apps/api/${appNumber}/devices/${device.id}/refresh?access_token=${access_token}`;

    const promise = axios.get(url)
      .then(response => {
        console.log(`${device.name} Refresh successful : ${response.data}`);
      })
      .catch(error => {
        console.error(`Error refreshing ${device.name}:`, error);
      });

    refreshPromises.push(promise);
  }

  try {
    await Promise.all(refreshPromises);
    console.log("All devices refreshed");
  } catch (error) {
    console.error("Error during refresh:", error);
  }
}


//reload every 10 hours to refresh with recent modifications (new devices, UI changes, etc.)
setTimeout(restart, 10 * 60 * 60 * 1000);


function restart() {
  location.reload();
}

// Creates the mode buttons for a thermostat
function createThermostatModeButtons(id_From_Hub, currentMode) {
  console.log("createThermostatModeButtons()...........");
  const modes = [
    { name: 'off', label: 'Off' },
    { name: 'auto', label: 'Auto' },
    { name: 'heat', label: 'Heat' },
    { name: 'cool', label: 'Cool' },
    { name: 'fan_only', label: 'Fan On' },
    { name: 'fan_auto', label: 'Fan Auto' }
  ];

  const buttonsContainer = $('<div>').addClass('thermostat-modes');

  modes.forEach(mode => {
    const button = $('<button>')
      .addClass(`btn thermostat-mode-btn mode-${mode.name}`)
      .text(mode.label)
      .attr('data-mode', mode.name);

    if (currentMode === mode.name) {
      button.addClass('active');
    }

    button.on('click', async () => {
      await setThermostatMode(id_From_Hub, mode.name);
      // Update active state of buttons
      buttonsContainer.find('.thermostat-mode-btn').removeClass('active');
      button.addClass('active');
    });

    buttonsContainer.append(button);
  });

  return buttonsContainer;
}

// Sets the mode for a thermostat
async function setThermostatMode(deviceId, mode) {
  const url = `http://${ip}/apps/api/${appNumber}/devices/${deviceId}/${mode}?access_token=${access_token}`;
  try {
    await sendCommand(url);
    console.log(`Set thermostat ${deviceId} to mode: ${mode}`);
  } catch (error) {
    console.error(`Failed to set thermostat mode: ${error}`);
  }
}



class RequestQueue {
  constructor() {
    this.queue = [];
    this.processing = false;
  }

  async add(request) {
    this.queue.push(request);
    if (!this.processing) {
      await this.process();
    }
  }

  async process() {
    if (this.queue.length === 0) {
      this.processing = false;
      return;
    }

    this.processing = true;
    const request = this.queue.shift();

    try {
      await request();
    } catch (error) {
      console.error('Request failed:', error);
    }

    await this.process();
  }
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
