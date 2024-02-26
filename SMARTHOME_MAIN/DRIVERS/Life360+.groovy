/**
 * ------------------------------------------------------------------------------------------------------------------------------
 * ** LIFE360+ Hubitat App **
 * 
 * - see community discussion here: https://community.hubitat.com/t/release-life360/118544
 * 
 * ------------------------------------------------------------------------------------------------------------------------------
 *  ---- Original Header ----
 *
 *  Life360 with States - Hubitat Port
 *
 *  BTRIAL DISTANCE AND SLEEP PATCH 29-12-2017
 *  Updated Code to handle distance from, and sleep functionality
 *
 *  TMLEAFS REFRESH PATCH 06-12-2016 V1.1
 *  Updated Code to match Smartthings updates 12-05-2017 V1.2
 *  Added updateMember function that pulls all usefull information Life360 provides for webCoRE use V2.0
 *
 *  Copyright 2014 Jeff's Account
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ---- End of original header ----
 *
 * ---- New Header ----
 *
 *  ****************  L360 with States App  ****************
 *
 *  Design Usage:
 *  Life360 with all States included
 *
 *  Copyright 2019-2020 Bryan Turcotte (@bptworld)
 *
 *  This App is free.  If you like and use this app, please be sure to mention it on the Hubitat forums!  Thanks.
 *
 *  Remember...I am not a programmer, everything I do takes a lot of time and research!
 *  Donations are never necessary but always appreciated.  Donations to support development efforts are accepted via:
 *
 *  Paypal at: https://paypal.me/bptworld
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Special thanks goes out to @cwwilson08 for working on and figuring out the oauth stuff!
 *  This would not be possible without his work.
 *
 *  Changes:
 *  3.0.25 - 12/29/23 - a few more API changes to try and help some login again
 *  3.0.24 - 12/21/23 - fix Life360 - unable to login
 *  3.0.22 - 12/06/23 - webpage formatting; option to only show a single user
 *  3.0.21 - 12/05/23 - add webpage link which shows users on google map
 *  3.0.20 - 10/24/23 - add arrived/departed commands to driver; better error logging
 *  3.0.19 - 10/01/23 - add sendHistory, sendTheMap, historyClearData commands for supporting Life360 Tracker app
 *  3.0.18 - 09/07/23 - reverting previous PR -- it drastically slowed down refresh logic which is a user preference
 *  3.0.17 - 08/06/23 - merge PR by imnotbob - https://github.com/jpage4500/hubitat-drivers/pull/16
 *  3.0.16 - 08/25/23 - handle http:401 response from life360 and clear token; allowing re-login
 *  3.0.15 - 08/22/23 - merge PR from @jbaruch - Fixed url and auth token after Life360 migration to cloudfront
 *  3.0.14 - 05/31/23 - add setting to auto-adjust refresh rate when users are moving
 *  3.0.13 - 05/30/23 - minor changes
 *  3.0.12 - 05/28/23 - change isDriving, isTransit, wifiState from string to boolean (enum)
 *  3.0.8 - 05/18/23 - more changes/cleanup from @Scottma61
 *  3.0.5 - 05/15/23 - several changes including PR from @Scottma61
 *  3.0.3 - 05/11/23 - fix status attribute
 *  3.0.2 - 05/11/23 - set presence attribute
 *  3.0.0 - 05/05/23 - refactor driver
 *                       - Only notify on location or battery change
 *                       - add accuracy - useful to know how accurate a given location is
 *                       - don't set fields for both miles and km - just the one the user selects
 *                       - last updated time
 *                       - add phone and email fields
 *  2.6.4 - 09/06/22 - Added option to export all life360 Places to file
 *  2.6.3 - 08/15/22 - Bundle Manager changes
 *  2.6.2 - 05/06/22 - Bundle Manager
 *  2.6.1 - 02/26/21 - Fixed a login bug where authentication would fail when certain special characters were included in
 *                     the login information.
 *  2.6.0 - 01/07/21 - Interim release reintegrating push notifications with a new POST request
                       to force location name update from real-time servers *  2.5.5 - 12/20/20 - Reliability Improvements:
                     - Added a 1 sec delay after push event to let data packet catch-up
                     - Cleaned up Scheduling
                     - Cleaned up Logging
 *  2.5.3 - 12/17/20 - Fixed a logging issue
 *  2.5.2 - 12/17/20 - 30 second refresh intervals
                       To-Do: Provide a user preference field in app for refresh intervals
 *  2.5.1 - 12/11/20 - Resubscribe to notifications on Update() event
 *  2.5.0 - 12/06/20 - Moved all member location functionality to Location Tracker child Driver
                       Keeping only Circle level functionality at parent app level
 *  ---
 *  v1.0.0 - 06/30/19 - Initial port of ST app (cwwilson08) (bptworld)
 */

String.metaClass.encodeURL = {
     java.net.URLEncoder.encode(delegate, "UTF-8")
}

def syncVersion(evt){
}

definition(
    name: "Life360+",
    namespace: "jpage4500",
    author: "Joe Page",
    description: "Life360 app that exposes more details than the built-in driver",
    category: "",
    iconUrl: "",
    iconX2Url: "",
    oauth: [displayName: "Life360", displayLink: "Life360"],
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/jpage4500/hubitat-drivers/master/life360/life360_app.groovy",
) {
  appSetting "clientId"
  appSetting "clientSecret"
}

preferences {
    page(name: "Credentials", title: "Enter Life360 Credentials", content: "getCredentialsPage", nextPage: "testLife360Connection", install: false)
    page(name: "listCirclesPage", title: "Select Life360 Circle", content: "listCircles", install: false)
    page(name: "myPlaces", title: "My Places", content: "myPlaces", install: true)
}

mappings {
  path("/placecallback") {
    action: [
              POST: "placeEventHandler",
              GET: "placeEventHandler"
    ]
  }

    path("/receiveToken") {
    action: [
            POST: "receiveToken",
            GET: "receiveToken"
    ]
  }

  path("/circle") {
    action: [
              GET: "circleEventHandler"
    ]
  }
}

def getCredentialsPage() {
    if (logEnable) log.debug "Life360+: In getCredentialsPage"
    if(state.life360AccessToken) {
        listCircles()
    } else {
        dynamicPage(name: "Credentials", title: "Enter Life360 Credentials", nextPage: "listCirclesPage", uninstall: true, install:false){
            section(getFormat("header-green", "${getImage("Blank")}"+" Life360 Credentials")) {
            input "username", "text", title: "Life360 Username?", multiple: false, required: true
            input "password", "password", title: "Life360 Password?", multiple: false, required: true, autoCorrect: false
          }
        }
    }
}

def getCredentialsErrorPage(String message) {
    if (logEnable) log.debug "Life360+: getCredentialsErrorPage:"
    dynamicPage(name: "Credentials", title: "Enter Life360 Credentials", nextPage: "listCirclesPage", uninstall: uninstallOption, install:false) {
      section(getFormat("header-green", "${getImage("Blank")}"+" Life360 Credentials")) {
        input "username", "text", title: "Life360 Username?", multiple: false, required: true
        input "password", "password", title: "Life360 Password?", multiple: false, required: true, autoCorrect: false
        paragraph "${message}"
      }
    }
}

def testLife360Connection() {
    if(state.life360AccessToken) {
        return true
    } else {
        return initializeLife360Connection()
    }
}

def initializeLife360Connection() {
    if (logEnable) log.debug "Life360+: initializeLife360Connection"

    initialize()

    def username = settings.username.encodeURL()
    def password = settings.password.encodeURL()


    // for reference see https://github.com/pnbruckner/life360/blob/master/life360/api.py
    def url = "https://api-cloudfront.life360.com/v3/oauth2/token"

    def postBody =  "grant_type=password&" +
        "username=${username}&"+
        "password=${password}"

    def result = null

    try {
        //httpPost(uri: url, body: postBody, headers: ["Authorization": "Basic cFJFcXVnYWJSZXRyZTRFc3RldGhlcnVmcmVQdW1hbUV4dWNyRUh1YzptM2ZydXBSZXRSZXN3ZXJFQ2hBUHJFOTZxYWtFZHI0Vg==" ]) {response ->
        httpPost(uri: url, body: postBody, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Basic Y2F0aGFwYWNyQVBoZUtVc3RlOGV2ZXZldnVjSGFmZVRydVl1ZnJhYzpkOEM5ZVlVdkE2dUZ1YnJ1SmVnZXRyZVZ1dFJlQ1JVWQ==" ]) {response ->
             result = response
        }
        if (result.data.access_token) {
           state.life360AccessToken = result.data.access_token
            return true;
        }
        getCredentialsErrorPage("Life360 Data Error: ${status}")
        return false;
    }
    catch (e) {
       def status = e.getResponse().status
       log.error "Life360+: initializeLife360Connection, error: $e"
       getCredentialsErrorPage("Life360 Connection Error: ${status}")
       return false;
    }
}

def listCircles() {
    if (logEnable) log.debug "Life360+: listCircles:"
    def uninstallOption = false
    if (app.installationState == "COMPLETE") uninstallOption = true
    dynamicPage(name: "listCirclesPage", title: "", install: true, uninstall: true) {
        displayHeader()

        if (!testLife360Connection()) {
            return
        }

        def urlCircles = "https://api-cloudfront.life360.com/v4/circles.json"
        def resultCircles = null

        try {
            httpGet(uri: urlCircles, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}", timeout: 30 ]) {response -> resultCircles = response }
        }
        catch (e) {
           def status = e.getResponse().status
           log.error "Life360+: listCircles, error: http:$status, $e"
           // on 401 Unauthorized response, clear access token
           if (status == 401) {
               state.life360AccessToken = null
           }
           getCredentialsErrorPage("Error logging into Life360!")
           displayFooter()
           return
        }

        def circles = resultCircles.data.circles

        section(getFormat("header-green", "${getImage("Blank")}"+" Select Life360 Circle")) {
          input "circle", "enum", multiple: false, required:true, title:"Life360 Circle", options: circles.collectEntries{[it.id, it.name]}, submitOnChange: true
        }

        if(circles) {
            state.circle = settings.circle
        } else {
            getCredentialsErrorPage("Invalid Usernaname or password.")
        }

        if(circle) {
            if (logEnable) log.trace "Life360+: listPlaces"
            if (app.installationState == "COMPLETE") uninstallOption = true

            if (!state?.circle) state.circle = settings.circle

            def url = "https://api-cloudfront.life360.com/v3/circles/${state.circle}/places.json"
            def result = null

            try {
                httpGet(uri: url, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}", timeout: 30 ]) {response -> result = response }
            }
            catch (e) {
               def status = e.getResponse().status
               log.error "Life360+: listCircles, error: http:$status, $e"
               getCredentialsErrorPage("Error logging into Life360!")
               return
            }
            def places = result.data.places
            state.places = places

            section(getFormat("header-green", "${getImage("Blank")}"+" Select Life360 Place to Match Current Location")) {
                paragraph "Please select the ONE Life360 Place that matches your Hubitat location: ${location.name}"
                thePlaces = places.collectEntries{[it.id, it.name]}
                sortedPlaces = thePlaces.sort { a, b -> a.value <=> b.value }
                input "place", "enum", multiple: false, required:true, title:"Life360 Places: ", options: sortedPlaces, submitOnChange: true
                paragraph "<hr>"
                input "exportPlaces", "bool", title: "Export all Life360 Places to file<br><small>* Switch will turn off when finished</small>", defaultValue:false, submitOnChange:true
                if(exportPlaces) {
                    life360PlacesExportMap = [:]
                    for(rec in places) {
                        jsonName = rec.name
                        jsonLat = rec.latitude
                        jsonLon = rec.longitude
                        jsonRadius = rec.radius
                        stuff = "${jsonLat};${jsonLon};${jsonRadius}"
                        //log.trace "$jsonName - $stuff"
                        life360PlacesExportMap.put(jsonName, stuff) 
                    }
                    saveLif360PlacesHandler(life360PlacesExportMap)
                    app.updateSetting("exportPlaces",[value:"false",type:"bool"])
                }
                if(finished) { paragraph "Places have been saved as 'life360Places.txt'" }
            }
        }

        if(place && circle) {
            if (logEnable) log.trace "Life360+: listUsers"
            if (app.installationState == "COMPLETE") uninstallOption = true
            if (!state?.circle) state.circle = settings.circle

            def url = "https://api-cloudfront.life360.com/v3/circles/${state.circle}/members.json"
            def result = null

            httpGet(uri: url, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}", timeout: 30 ]) {response ->
               result = response
            }

            def members = result.data.members
            state.members = members

            section(getFormat("header-green", "${getImage("Blank")}"+" Select Life360 Members to Import into Hubitat")) {
                theMembers = members.collectEntries{[it.id, it.firstName+" "+it.lastName]}
                sortedMembers = theMembers.sort { a, b -> a.value <=> b.value }
              input "users", "enum", multiple: true, required:false, title:"Life360 Members: ", options: sortedMembers, submitOnChange: true
            }

            section(getFormat("header-green", "${getImage("Blank")}"+" Other Options")) {
                input (name: "pollFreq", type: "enum", title: "Refresh Rate", required: true, defaultValue: "auto", options: ['auto':'Auto Refresh (faster when devices are moving)','30':'30 seconds','60':'1 minute'])
                
                paragraph "View members on map: [<a href=\"${getLocalUri(None)}\">LOCAL</a>] [<a href=\"${getCloudUri(None)}\">REMOTE</a>]"
                paragraph "<hr>"

                input(name: "logEnable", type: "bool", defaultValue: "false", submitOnChange: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
            }
        }

        displayFooter()
    }
}

def installed() {
    if (logEnable) log.trace "Life360+: installed"
    if(!state?.circle) state.circle = settings.circle

    settings.users.each {memberId->
        def member = state.members.find{it.id==memberId}
        if(member) {
            // Modified from @Stephack
            def childDevice = childList()
            if(childDevice.find{it.data.vcId == "${member}"}){
                if (logEnable) log.info "${member.firstName} already exists...skipping"
            } else {
                if (logEnable) log.info "Creating Life360 Device: " + member
                try{
                    addChildDevice("jpage4500", "Life360+ Driver", "${app.id}.${member.id}", 1234, ["name": "Life360 - ${member.firstName}", isComponent: false])
                }
                catch (e) {
                    log.error "Child device creation failed with error = ${e}"
                }
            }
            // end mod

            if (childDevice) {
                if (logEnable) log.info "Child Device Successfully Created"
            }
        }
    }
    createCircleSubscription()
}

def createCircleSubscription() {
    if (logEnable) log.debug "Life360+: createCircleSubscription: Remove webhook for circle:${state.circle}"

    def deleteUrl = "https://api-cloudfront.life360.com/v3/circles/${state.circle}/webhook.json"
    try { 
        // ignore any errors - there many not be any existing webhooks
        httpDelete (uri: deleteUrl, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}" ]) {response ->
            result = response}
    }
    catch (e) {
        log.debug(e)
    }

    // subscribe to the life360 webhook to get push notifications on place events within this circle
    if (logEnable) log.debug "Life360+: createCircleSubscription: Create webhooks for circle:${state.circle}"
    createAccessToken() // create our own OAUTH access token to use in webhook url
    def hookUrl = "${getFullApiServerUrl()}/placecallback?access_token=${state.accessToken}"
    def url = "https://api-cloudfront.life360.com/v3/circles/${state.circle}/webhook.json"
    def postBody =  "url=${hookUrl}"
    def result = null
    try {
        httpPost(uri: url, body: postBody, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}" ]) {response ->
            result = response}
    } catch (e) {
        log.debug(e)
    }

    if (result.data?.hookUrl) {
        log.info "Life360+: createCircleSubscription: subscribed to Life360 circle push events"
        if (logEnable) log.debug "Life360+: createCircleSubscription: result: ${result.data}"
        updateMembers()
        scheduleUpdates()
    }
}

def updated() {
    if (logEnable) log.trace "Life360+: updated"
    if (!state?.circle) { state.circle = settings.circle }

    settings.users.each {memberId->
        def externalId = "${app.id}.${memberId}"
        def deviceWrapper = getChildDevice("${externalId}")

        if (!deviceWrapper) { // device isn't there - so we need to create
            member = state.members.find{it.id==memberId}
            // Modified from @Stephack
            def childDevice = childList()
            if(childDevice.find{it.data.vcId == "${member}"}){
                if (logEnable) log.info "${member.firstName} already exists...skipping"
            } else {
                if (logEnable) log.info "Creating Life360 Device: " + member
                try{
                    addChildDevice("jpage4500", "Life360+ Driver", "${app.id}.${member.id}", 1234, ["name": "Life360 - ${member.firstName}", isComponent: false])
                }
                catch (e) {
                    log.error "Child device creation failed with error = ${e}"
                }
            }
            // end mod

            if (childDevice) {
                if (logEnable) log.info "Child Device Successfully Created"
            }
        }
    }

    // update webhook
    createCircleSubscription()

    def childDevices = childList()
    if (logEnable) log.debug "Life360+: Child Devices: ${childDevices}"
    childDevices.each {childDevice->
        def (childAppName, childMemberId) = childDevice.deviceNetworkId.split("\\.")
        if (!settings.users.find{it==childMemberId}) {
            deleteChildDevice(childDevice.deviceNetworkId)
            def member = state.members.find {it.id==memberId}
            if (member) state.members.remove(member)
        }
    }
    updateMembers()
    // Since we updated the app, make sure we also reschedule the updateMembers function
    scheduleUpdates()
}

def initialize() {
    subscribe(location, "getVersionsInfo", syncVersion)
    // TODO: subscribe to attributes, devices, locations, etc.
}

def placeEventHandler() {
    if (logEnable) log.debug "Life360+: placeEventHandler: params:${params}"
    def circleId = params?.circleId
    def placeId = params?.placeId
    def memberId = params?.userId
    def direction = params?.direction
    def timestamp = params?.timestamp
    def requestId = null
    def requestResult = null
    def isPollable = null
  
    def externalId = "${app.id}.${memberId}"
    def deviceWrapper = getChildDevice("${externalId}")
  
    if (logEnable) log.trace "Life360+: placeEventHandler: about to post request update..."
    def postUrl = "https://api-cloudfront.life360.com/v3/circles/${circleId}/members/${memberId}/request.json"
    requestResult = null
  
    // once a push event is received, we can force a real-time update of location data via issuing the following
    // post request against the member for which the push event was received
    try {
        httpPost(uri: postUrl, body: ["type": "location"], headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}"]) {response ->
            requestResult = response
        }
        requestId = requestResult.data?.requestId
        isPollable = requestResult.data?.isPollable
        if (logEnable) log.debug "Life360+: placeEventHandler: reqId:${requestId}, pollable:$isPollable, response:${requestResult.data}"
    }
    catch (e) {
        log.error "Life360+: request post / get, error: $e"
    }
  
    // we got a PUSH EVENT from Life360 - better update everything by pulling a fresh data packet
    // But first, wait a second to let packet catch-up with push event
    updateMembers()
}


def refresh() {
    if (logEnable) log.debug("Life360+: refresh:")
    listCircles()
    updateMembers()
    scheduleUpdates()
}

def scheduleUpdates() {
    unschedule()

    def Integer refreshSecs = 30
    if (pollFreq == "auto") {
        // adjust refresh rate based on if devices are moving
        def Integer numLocationUpdates = state.numLocationUpdates != null ? state.numLocationUpdates : 0
        // TODO: experiment with these values.. make sure we're not calling the API too frequently but still get timely user updates
        if (numLocationUpdates == 0) {
            // update every 30 seconds
            refreshSecs = 30
        } else if (numLocationUpdates == 1) {
            // update every 15 seconds
            refreshSecs = 15
        } else if (numLocationUpdates >= 2) {
            // update every 10 seconds
            refreshSecs = 10
        }
    } else {
        refreshSecs = pollFreq.toInteger()
    }
    if (logEnable) log.debug("Life360+: scheduleUpdates: refreshSecs:$refreshSecs, pollFreq:$pollFreq")
    if (refreshSecs > 0 && refreshSecs < 60) {
        // seconds
        schedule("0/${refreshSecs} * * * * ? *", updateMembers)
    } else {
        // mins
        schedule("0 */${refreshSecs/60} * * * ? *", updateMembers)
    }
}

def updateMembers(){
    // prevent calling API too frequently 
    // - I noticed calling schedule() can cause it to fire immediately sometimes
    def Long currentTimeMs = new Date().getTime()
    def Long lastUpdateMs = state.lastUpdateMs != null ? state.lastUpdateMs : 0
    def Long diff = currentTimeMs - lastUpdateMs
    if (diff < 2000) {
        if (logEnable) log.trace "Life360+: updateMembers: already up-to-date; ${diff}ms"
        return
    }
    state.lastUpdateMs = currentTimeMs
    if (logEnable) log.trace "Life360+: updateMembers: last:${diff}ms"

    if (!state?.circle) state.circle = settings.circle

    url = "https://api-cloudfront.life360.com/v3/circles/${state.circle}/members.json"
    def result = null
    sendCmd(url, result)
}

def sendCmd(url, result){
    def requestParams = [ uri: url, headers: ["Accept": "application/json", "cache-control": "no-cache", "user-agent": "com.life360.android.safetymapd/KOKO/23.50.0 android/13", "Authorization": "Bearer ${state.life360AccessToken}"], timeout: 10 ]
    asynchttpGet("cmdHandler", requestParams)
}

def cmdHandler(resp, data) {
    def status = resp.getStatus()
    if (status == 200 || status == 207) {
        result = resp.getJson()
        def members = result.members
        state.members = members

        // Get a *** sorted *** list of places for easier navigation
        def thePlaces = state.places.sort { a, b -> a.name <=> b.name }
        def home = state.places.find{it.id==settings.place}

        placesMap = [:]
        for (rec in thePlaces) {
            placesMap.put(rec.name, "${rec.latitude};${rec.longitude};${rec.radius}") 
        }

        // Iterate through each member and trigger an update from payload
        def Boolean isAnyChanged = false
        settings.users.each {memberId->
            def externalId = "${app.id}.${memberId}"
            def member = state.members.find{it.id==memberId}
            try {
                // find the appropriate child device based on app id and the device network id
                def deviceWrapper = getChildDevice("${externalId}")

                // send circle places and home to individual children
                def Boolean isChanged = deviceWrapper.generatePresenceEvent(member, placesMap, home)
                if (isChanged) isAnyChanged = true
            } catch(e) {
                log.error "Life360+: cmdHandler: Exception: member: ${member}"
                log.error e
            }
        }

        if (pollFreq == "auto") {
            // numLocationUpdates = how many consecutive location changes which can be used to speed up the next location check
            // - if user(s) are moving, update more frequently; if not, update less frequently
            def Integer prevLocationUpdates = state.numLocationUpdates != null ? state.numLocationUpdates : 0
            def Integer numLocationUpdates = prevLocationUpdates
            numLocationUpdates += isAnyChanged ? 1 : -1
            // max out at 3 to prevent a long drive from not slowing down API calls for a while after
            if (numLocationUpdates < 0) numLocationUpdates = 0
            else if (numLocationUpdates > 2) numLocationUpdates = 2
            state.numLocationUpdates = numLocationUpdates
            //if (logEnable) log.debug "cmdHandler: members:$settings.users.size, isAnyChanged:$isAnyChanged, numLocationUpdates:$numLocationUpdates"

            // calling schedule() can result in it firing right away so only do it if anything changes
            if (numLocationUpdates != prevLocationUpdates) {
                scheduleUpdates()
            }
        }
    } else {
        // connection error
        log.error("Life360+: cmdHandler: status:${status}, resp:${resp}")
    }
}

def childList() {
    def children = getChildDevices()
    if (logEnable) log.debug "Life360+: childList: children: ${children}"
    return children
}

def saveLif360PlacesHandler(data) {
    if(data) {
        if (logEnable) log.debug "Life360+: saveLif360PlacesHandler: writting to file: life360Places.txt"
        writeFile("life360Places.txt", data)
        finished = true
    } else {
        log.info "Life360+: There is no data to save."
        finished = false
    }
    return finished
}

Boolean writeFile(fName, fData) {
    //if (logEnable) log.debug "Writing to file - ${fName} - ${fData}"
    login()
    try {
        def params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/fileManager/upload",
            query: [
                "folder": "/"
            ],
            headers: [
                "Cookie": state.cookie,
                "Content-Type": "multipart/form-data; boundary=----WebKitFormBoundaryDtoO2QfPwfhTjOuS"
            ],
            body: """------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="uploadFile"; filename="${fName}"
Content-Type: text/plain

${fData}

------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="folder"


------WebKitFormBoundaryDtoO2QfPwfhTjOuS--""",
            timeout: 300,
            ignoreSSLIssues: true
        ]
        httpPost(params) { resp ->    
        }
    } catch (e) {
        log.error "Life360+: Error writing file $fName: ${e}"
    }
}

def login() {        // Modified from code by @dman2306
    if (logEnable) log.debug "Life360+: login: Checking Hub Security"
    state.cookie = ""
    if(hubSecurity) {
        try{
            httpPost(
                [
                    uri: "http://127.0.0.1:8080",
                    path: "/login",
                    query: 
                    [
                        loginRedirect: "/"
                    ],
                    body:
                    [
                        username: hubUsername,
                        password: hubPassword,
                        submit: "Login"
                    ],
                    textParser: true,
                    ignoreSSLIssues: true
                ]
            )
            { resp ->
                if (resp.data?.text?.contains("The login information you supplied was incorrect.")) {
                    log.warn "Life360+: Quick Chart Data Collector - username/password is incorrect."
                } else {
                    state.cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
                }
            }
        } catch (e) {
            log.error(getExceptionMessageWithLine(e))
        }
    }
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def getImage(type) {                    // Modified from @Stephack Code
    def loc = "<img src=https://raw.githubusercontent.com/jpage4500/hubitat-drivers/master/life360/images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
    if(type == "optionsRed") return "${loc}options-red.png height=30 width=30>"
}

def getFormat(type, myText=null, page=null) {            // Modified code from @Stephack
    if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid #000000;box-shadow: 2px 3px #8B8F8F;border-radius: 10px'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;' />"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
    
    if(type == "button-blue") return "<a style='color:white;text-align:center;font-size:20px;font-weight:bold;background-color:#03FDE5;border:1px solid #000000;box-shadow:3px 4px #8B8F8F;border-radius:10px' href='${page}'>${myText}</a>"
}

def displayHeader(data) {
    if(data == null) data = ""
    if(app.label) {
        if(app.label.contains("(Paused)")) {
            theName = app.label - " <span style='color:red'>(Paused)</span>"
        } else {
            theName = app.label
        }
    }
    if(theName == null || theName == "") theName = "New Child App"
    headerName = "Life360+"
    section() {
    }
}

def displayFooter() {
    section() {
        if(state.appType == "parent") { href "removePage", title:"${getImage("optionsRed")} <b>Remove App and all child apps</b>", description:"" }
        paragraph getFormat("line")
        bMes = "<div style='color:#1A77C9;text-align:center;font-size:20px;font-weight:bold'>Life360+</div>"
        paragraph "${bMes}"
    }
}

def circleEventHandler() {
    def user = params?.user
    if (logEnable) log.debug "Life360+: circleEventHandler: user:${user}"
    def html = ""
    def title = "Life360+"
    if (!user) {
        title += " - ALL"
    }

    settings.users.each { memberId->
        def externalId = "${app.id}.${memberId}"
        def device = getChildDevice("${externalId}")
        if (!device) return

        def id = device.getId()

        // if user param is passed, only show matching user
        if (user && user != id) return;

        def name = device.currentValue('memberName')
        def lat = device.currentValue('latitude')
        def lng = device.currentValue('longitude')
        def lastUpdated = device.currentValue('lastUpdated')
        def htmlLink = device.currentValue('html')
        if (!lat || !lng) return
        //if (logEnable) log.debug "Life360+: circleEventHandler: ID: ${id}, name: ${name}, lat: ${lat}, lng: ${lng}"

        html += "<h2>${name}</h2>"
        if (htmlLink) {
            // NOTE: html has a height defined which we don't want here
            htmlLink = htmlLink.replace('height:90%', '')
            html += htmlLink + "</br>"
        } else {
            html += "Last Updated: ${lastUpdated}</br>"
        }
        html += "[<a href=\"" + getLocalUri(id) + "\">LOCAL</a>] [<a href=\"" + getCloudUri(id) + "\">REMOTE</a>]</br>"
        // add Google Map
        html += "<div style=\"width: 100%\"><iframe width=\"100%\" height=\"600\" frameborder=\"0\" scrolling=\"no\" marginheight=\"0\" marginwidth=\"0\" src=\"https://maps.google.com/maps?width=100%25&amp;height=600&amp;hl=en&amp;q=${lat},${lng}&amp;t=&amp;z=13&amp;ie=UTF8&amp;iwloc=B&amp;output=embed\"></iframe></div>"
        html += "<hr>"

        if (user) {
            // set title
            title += " - " + name
        }
    }

    // add link to all users
    html += "<hr>"
    html += "<b>ALL</b> "
    html += "[<a href=\"" + getLocalUri(None) + "\">LOCAL</a>] [<a href=\"" + getCloudUri(None) + "\">REMOTE</a>]</br>"

    html = "<title>" + title + "</title>" + html
    render contentType: "text/html", data: html, status: 200
}

def getLocalUri(String user) {
    def url = getFullLocalApiServerUrl() + "/circle?access_token=${state.accessToken}"
    if (user != None) {
        url += "&user=${user}"
    }
    return url
}

def getCloudUri(String user) {
    def url = "${getApiServerUrl()}/${hubUID}/apps/${app.id}/circle?access_token=${state.accessToken}"
    if (user != None) {
        url += "&user=${user}"
    }
    return url
}
