const access_token = "d6699bb3-0d13-48d1-ab5d-8cc583efa76c"
const ip = "192.168.10.72"
const appNumber = "35"

const everythingUrl = "http://" + ip + "/apps/api/" + appNumber + "/devices/all?access_token=" + access_token;

const labelLength = 35;

console.log(everythingUrl)
$.ready(initialize())

function initialize() {
    console.log("initialize")
    axios.get(everythingUrl)
        .then(res => {
            // console.log("response:", res.data)
            const allDevices = res.data



            allDevices.forEach((e, index) => {
                const id_From_Hub = e.id

                const isLock = e.capabilities.find(el => el === "Lock")
                const isLight = e.name.toLowerCase().includes("light") || e.label.toLowerCase().includes("light")
                const isSwitchLevel = e.capabilities.find(el => el === "SwitchLevel")
                const isSwitch = e.capabilities.find(el => el === "Switch") && !isSwitchLevel
                let notAButton = !e.capabilities.find(el => el.toLowerCase().includes("button"))
                notAButton = notAButton ? notAButton : e.type.toLowerCase().includes("button") ? false : true

                if (notAButton && (isLight || isLock || isSwitchLevel)) {
                    if (isLight) {
                        const id_html = id_From_Hub + "light"

                        $("#lights").append(
                            $("<button>").addClass("tiles")
                                .attr({
                                    "id": id_html,
                                    "data-id_From_Hub": id_From_Hub,
                                    "data-state": ""
                                })
                                .text(trimLabel(e.label, labelLength))
                        )

                        $(`#${id_html}`).on("click", () => {
                            console.log("id_From_Hub => ", id_From_Hub)
                            const url = `http://${ip}/apps/api/${appNumber}/devices/${id_From_Hub}/toggle?access_token=${access_token}`
                            sendCommand(url)
                        })

                        updateDeviceState(id_From_Hub, id_html, "switch", notAButton)

                    }
                }
            }
        }
    }
