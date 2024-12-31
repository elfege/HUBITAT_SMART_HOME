import axios from 'axios';
import { sendCommand, updateDeviceState, trimLabel, WebSocket_init } from './utils.js';
import { RequestQueue } from './requestQueue.js';

const requestQueue = new RequestQueue(5); // Set the concurrency limit to 5

export async function getSettings() {
  const response = await axios.get("\\settings.json");
  console.log("response.data => ", response.data);
  access_token = response.data.access_token;
  ip = response.data.ip;
  appNumber = response.data.appNumber;
  everythingUrl = "http://" + ip + "/apps/api/" + appNumber + "/devices/all?access_token=" + access_token;
  modesUrl = "http://" + ip + "/apps/api/" + appNumber + "/modes/all?access_token=" + access_token;
  getMode(modesUrl);
}

export async function initialize(access_token, ip, appNumber) {
  console.log("initialize...");
  WebSocket_init(ip);
  await axios.get(everythingUrl).then(res => {
    // ... (rest of the initialization code)
  }).then(resp => {
    $("#loading_message_container").remove();
    $("#master_container").removeAttr("hidden");
  }).catch(error => {
    console.log("error: ", error);
    $("body").append($("<div class='col-lg-4'>").text(`
      Hubitat isn't responding.
      \n${JSON.stringify(error)}
    `).css("color", "white"));
    overlayOn("Page will reload in 2 seconds...");
    setTimeout(restart, 2000);
  });
}

async function getMode(url) {
  axios.get(modesUrl)
    .then(modes => {
      // ... (rest of the getMode code)
    })
    .catch(err => console.log("ERROR GETTING MODES => ", err));
}

async function setMode(mode, id) {
  console.log("setting location mode to ", mode);
  const url = "http://" + ip + "/apps/api/" + appNumber + "/modes/" + id + "?access_token=" + access_token;
  requestQueue.enqueue(() => axios.get(url)
    .then(resp => console.log(resp))
    .catch(err => console.log("Mode Update failed => ", err)));
  // ... (rest of the setMode code)
}