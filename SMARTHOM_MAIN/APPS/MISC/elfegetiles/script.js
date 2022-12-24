
    /***************************************PREFERENCES*****************************/
    //SET YOUR PREFERENCES
    
    var access_token = window.localStorage.getItem('access_token');
    var ip = window.localStorage.getItem('ip')
    var appNumber = window.localStorage.getItem('appNumber')
    var FirstStageRessourceSavingTimeout = window.localStorage.getItem('FirstStageRessourceSavingTimeout') 
    var firstStageRessourceSavingInterval =  window.localStorage.getItem('firstStageRessourceSavingInterval')
    var RessourceSavingTime = window.localStorage.getItem('RessourceSavingTime'); 
    var ressourceSavingTimeout = window.localStorage.getItem('ressourceSavingTimeout')   
    var stopAllScripts = window.localStorage.getItem('stopAllScripts')
    var stopAllScriptsTimeout = window.localStorage.getItem('stopAllScriptsTimeout') 
    var labelLength = window.localStorage.getItem('labelLength')
   
    /*** BACKUP YOUR DEFAULT PREFERENCES INSIDE THE 'setDefaultPreferences()' FUNCTION BELOW*******/
    /************* I'LL PUSH AN UPDATE WITH A MORE ELEGANT SOLUTION SOME TIME SOON ****************/

    function setDefaultPreferences()
    {
      /* 
      modify this section with your settings to prevent having to enter the access token on all my devices after every test
      this will allow you to simply hit the "set default values" on the configuration page, instead of having to re-enter your access token 
      on every device you might use to display the tiles
      */
      /*
      document.getElementById("access_token").value = "";
      document.getElementById("ip").value = "";
      document.getElementById("appNumber").value = "";
      */
      
      /* this part is for me only and must be deleted in the public distribution*/
      document.getElementById("access_token").value = "access_token=d6699bb3-0d13-48d1-ab5d-8cc583efa76c";
      document.getElementById("ip").value = "192.168.10.72";
      document.getElementById("appNumber").value = "35";

      /*Do not modify anything below*/
      console.log("setting default values")
      document.getElementById("FirstStageRessourceSavingTimeout").value = "30"; // in seconds
      document.getElementById("firstStageRessourceSavingInterval").value = "10"; // in seconds
      document.getElementById("RessourceSavingTime").value = "120"; // in seconds
      document.getElementById("ressourceSavingTimeout").value = "1" // in hours
      document.getElementById("stopAllScripts").value = false; 
      document.getElementById("stopAllScriptsTimeout").value = "2" // in hours
      document.getElementById("labelLength").value = "18"; // number of characters
    }

    /********************************************************************************/
    /****************DO NOT MODIFY ANYTHING BELOW THIS LINE!**************************/
    /********************************************************************************/

    /*************************************************INITIALIZATION*********************************************/
    var version = 14 

    var ShortRefreshTime = "" // in seconds ; Beware of ressources. Too low of a value can render your hub inoperable
    

    var RessourceSavingRefreshInterval = RessourceSavingTime 
    var interval1; // refresh interval 
    var interval2; // new interval after power saving mode has started
    var interval3; // long interval
    var timeoutRessourceSaving = ""; // set the refresh intervals
    var cmdLevel = "setLevel"
    var cmdSwitch = "toggle";
    var id = ""; 
    var listOfDevicesURL = "http://"+ip+"/apps/api/"+appNumber+"/devices/all?"+access_token;
    var listOfModes = "http://"+ip+"/apps/api/"+appNumber+"/modes?/all?"+access_token;
    var allDevices; 
    var listString = "";
    var sliderId = "sliderId"; // this var needs to be public
    var nSId = 0; // this number needs to be public
    var spanIdList = [] // this value must be public, will be updated here with nSId number
    var allDevicesLabelsSorted = [];
    var dimmerList = []
    var lockList = []
    var deviceCapabilities = [];
    var mainPower = 0
    var acPower = 0
    var otherPowerSwitches = 0
    var stopAddingMore = false
    var thereIsAMainMeter = false
    var backGroundModifiedByUser = false
    inputRefreshRate = document.getElementById("refreshRate");
    var buttonOff = getComputedStyle(document.body).getPropertyValue('--buttonOff');
    var buttonOn = getComputedStyle(document.body).getPropertyValue('--buttonOn');
    var buttonOnHover = buttonOff
    var buttonOffHover = buttonOn
    var backgroundColor   = window.navigator.userAgent.indexOf('iPhone') != -1 ? "darkblue" : getComputedStyle(document.body).getPropertyValue('--backgroundColor');
    var backgroundColor2  = window.navigator.userAgent.indexOf('iPhone') != -1 ? "darkblue" : getComputedStyle(document.body).getPropertyValue('--backgroundColor2');
    var backgroundColor3  = window.navigator.userAgent.indexOf('iPhone') != -1 ? "darkblue" : getComputedStyle(document.body).getPropertyValue('--backgroundColor3');
    var backgroundColor4  = window.navigator.userAgent.indexOf('iPhone') != -1 ? "darkblue" : getComputedStyle(document.body).getPropertyValue('--backgroundColor4');
    var backgroundColor5  = window.navigator.userAgent.indexOf('iPhone') != -1 ? "darkblue" : getComputedStyle(document.body).getPropertyValue('--backgroundColor5');
    var backgroundColorSliderRamp = getComputedStyle(document.body).getPropertyValue('--backgroundColorSliderRamp');
    var enableDebug = false;
    var debugTimeout = 1; // in minutes
    var called = 0; 
    var lastPause = Date.now() // for logging()
    const switchStateValues = new Map();
    var loadCounter = 0;
    var xhrErrors = 0;
    var prefList = [];

    if (isIOS() || isIpadOS()) {
      if (window.navigator.standalone == true) {

      }else{
        alert("Tap the + button and choose 'Add to Home Screen'");
      }
    };

    function isIOS() {
      if (/iPad|iPhone|iPod/.test(navigator.platform)) {
        return true;
      } else {
        return navigator.maxTouchPoints &&
        navigator.maxTouchPoints > 2 &&
        /MacIntel/.test(navigator.platform);
      }
    };

    function isIpadOS() {
      return navigator.maxTouchPoints && navigator.maxTouchPoints > 2 &&
      /MacIntel/.test(navigator.platform);
    };

    function init(){
      
      document.getElementById("pageTitle").innerHTML = "<h1>Loading data...</h1> "

      if(ValidateIPaddress(ip) == false)
      {
        alert("INCORRECT IP ADDRESS FORMAT! Please review your entry")
      }
      getAllDevicesData(listOfDevicesURL); 
      ShortRefreshTime = defineShortRefreshRate();
      
      console.log ("page initialization..."); 
      initialized = false; // prevent refresh() from running before buttons are generated
      setTimeout(function(){buildButtons();}, 4000); 

      const letters = document.querySelectorAll('.letter')
      setInterval(function() {
        for(let letter of letters){
        letter.style.color = randomColor()
        }
      }, 5000)

      setBackgroundRandomInterval()
    }

    let backgroundRandomActive = true
    function setBackgroundRandomInterval()
      {
        const backgroundRandom = setInterval(function() {
          backgroundCss(false)
        }, 20000)
        backgroundRandomActive = true;
      };

    function ValidateIPaddress(ipaddress) 
    {  
      if (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test(ipaddress)) 
      {  
        return (true);  
      }  
      logging("NOT AN IP ADDRESS");
      return (false);
    };



    function defineShortRefreshRate() 
    {
      var newVar = document.getElementById("refreshRate").value;
      logging("newVar = "+newVar)
      var status = newVar == ""
      logging("newVar true/false ? "+status)
      if(newVar == "") // first boot on the device (computer or phone)
      {
        newVar = window.localStorage.getItem('storedRefreshRate'); // check if there's a stored value
        console.log("newVar local = "+newVar)
        var status2 = newVar == null
        logging("newVar status2 ? "+status2)
        if(newVar == null)
        {
          console.log("default 5 seconds refresh rate")
          return 5
        }
        else 
        {
           var newVarInt = parseInt(newVar) // stuck here, returns NaN... // found the solution: must be parsed as int before storage!
           console.log("returning user's stored value:"+newVarInt)
           return newVarInt
         }
       }
      else // this is a user custom request 
      {
        // if(newVar == 1)
        // {
        //   alert("Interval must be 2 or higher!")
        //   newVar = 2; // below 2 seconds, asynchronous requests go crazy and interval is less than 1 seconds for some reason (despite recent colde cleaning 3/21/2022). 
        // }
        newVarStored = parseInt(newVar);
        window.localStorage.setItem('storedRefreshRate', newVarStored); // store the value on local storage
      }
      //location.reload()
    }

    function checkVersion() 
    {
     var v = window.localStorage.getItem('version')
     logging("stored version = "+v)
     if(v != version)
     {
      console.log("code needs to be updated, reloading")
      window.localStorage.setItem('version', version);
      location.reload()
      }
    };

     function setPreferences()
    {     
      console.log("Updating Preferences")

      access_token = document.getElementById("access_token").value;
      console.log("access_token:"+access_token)
      window.localStorage.setItem('access_token', access_token);

      ip = document.getElementById("ip").value;
      console.log("ip:"+ip)
      window.localStorage.setItem('ip', ip);

      appNumber = document.getElementById("appNumber").value;
      console.log("appNumber:"+appNumber)
      window.localStorage.setItem('appNumber', appNumber);

      FirstStageRessourceSavingTimeout = document.getElementById("FirstStageRessourceSavingTimeout").value;
      window.localStorage.setItem('FirstStageRessourceSavingTimeout', FirstStageRessourceSavingTimeout);

      firstStageRessourceSavingInterval = document.getElementById("firstStageRessourceSavingInterval").value;
      window.localStorage.setItem('firstStageRessourceSavingInterval', firstStageRessourceSavingInterval);

      RessourceSavingTime = document.getElementById("RessourceSavingTime").value;
      window.localStorage.setItem('RessourceSavingTime', RessourceSavingTime);

      ressourceSavingTimeout = document.getElementById("ressourceSavingTimeout").value;
      window.localStorage.setItem('ressourceSavingTimeout', ressourceSavingTimeout);

      stopAllScripts = document.getElementById("stopAllScripts").value;
      window.localStorage.setItem('stopAllScripts', stopAllScripts);

      stopAllScriptsTimeout = document.getElementById("stopAllScriptsTimeout").value;
      window.localStorage.setItem('stopAllScriptsTimeout', stopAllScriptsTimeout);

      labelLength = document.getElementById("labelLength").value;
      window.localStorage.setItem('labelLength', labelLength);

      location.reload();
      
    }

    function getPreferences()
    {

     prefList = [access_token, ip, appNumber, FirstStageRessourceSavingTimeout, firstStageRessourceSavingInterval, RessourceSavingTime, ressourceSavingTimeout, stopAllScripts, stopAllScriptsTimeout, labelLength]; 

     var someNull = prefList.some(element => element == null) || prefList.some(element => element == "null") 
     console.log(prefList)
     console.log("someNull:"+someNull)

     console.log("access_token = "+access_token)
     console.log("ip = "+ip)
     console.log("appNumber = "+appNumber)
     console.log("FirstStageRessourceSavingTimeout = "+FirstStageRessourceSavingTimeout)
     console.log("firstStageRessourceSavingInterval = "+firstStageRessourceSavingInterval)
     console.log("RessourceSavingTime = "+RessourceSavingTime)
     console.log("ressourceSavingTimeout = "+ressourceSavingTimeout)
     console.log("stopAllScripts = "+stopAllScripts)
     console.log("stopAllScriptsTimeout = "+stopAllScriptsTimeout)
     console.log("labelLength = "+labelLength)

     if(someNull)
     {
        x = `
        <table> 
          <tr>
            <button style="height:25px;" onclick='resetPreferences()'>Reset</button>
            <button style="height:25px;" onclick='setPreferences()'>SUBMIT</button>
            <button style="height:25px; width:150px; color:red;" onclick='setDefaultPreferences()'>restore default values</button>
          </tr>
          <tr>
            <td><SPAN style="color:white">Enter your HUB's access token (example: 'access_token=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx')</SPAN></td>
            <td><textarea id="access_token"  autofocus></textarea></td>
          </tr>
          <tr> 
            <td><SPAN style="color:white">Enter your HUB's ip address</SPAN></td> 
            <td><textarea id="ip" onchange="testIp(id)"></textarea> </td>
          </tr>
          <tr>
            <td><SPAN style="color:white">Enter your HUB's MakerAPI's app Number</SPAN></td>
            <td><textarea id="appNumber" onchange="testNumber(id)"></textarea></td>  
          </tr> 
         
          <tr>
            <td><SPAN style="color:white">First level sleep timeout (in seconds)</SPAN></td>
            <td><textarea id="FirstStageRessourceSavingTimeout" onchange="testNumber(id)"></textarea></td>  
          </tr>
          <tr>
            <td><SPAN style="color:white">Refresh interval (in seconds)</SPAN></td>
            <td><textarea id="firstStageRessourceSavingInterval" onchange="testNumber(id)"></textarea></td>  
          </tr>
          <tr>
            <td><SPAN style="color:white">Longer Refresh interval (in seconds)</SPAN></td>
            <td><textarea id="RessourceSavingTime" onchange="testNumber(id)"> </textarea></td> 
          </tr>
          <tr>
            <td><SPAN style="color:white">Second level sleep timeout (in hours)</SPAN></td>
            <td><textarea id="ressourceSavingTimeout" onchange="testNumber(id)"></textarea> </td> 
          </tr>
          <tr>
            <td><SPAN style="color:white">stop all scripts after a certain time? (type 'true' or 'false')</SPAN></td>
            <td><textarea id="stopAllScripts" onchange="isTrueFalse(id)"></textarea> </td> 
          </tr>
          <tr>
            <td><SPAN style="color:white">Stop all scripts timeout value (in hours)</SPAN></td>
            <td><textarea id="stopAllScriptsTimeout" onchange="testNumber(id)"></textarea> </td> 
          </tr>
          <tr>
            <td><SPAN style="color:white">Max length of your devices labels (in number of char.)</SPAN></td>
            <td><textarea id="labelLength" onchange="testNumber(id)"></textarea></td>
          </tr>
        </table>
        `;  

        document.getElementById("buttons").innerHTML = x;   

      //prefill what can be prefilled. 
      document.getElementById("access_token").value = "access_token=";
      document.getElementById("FirstStageRessourceSavingTimeout").value = "30";
      document.getElementById("firstStageRessourceSavingInterval").value = "10";
      document.getElementById("RessourceSavingTime").value = "120";
      document.getElementById("ressourceSavingTimeout").value = "1"
      document.getElementById("stopAllScripts").value = false;
      document.getElementById("stopAllScriptsTimeout").value = "2"
      document.getElementById("labelLength").value = "18";    
     }
     else 
     {
      init();
     }

    };    

    function resetPreferences(){
      let alert = confirm("Are you sure ?")
      console.log(alert)
      if(alert) 
      {        
        window.localStorage.removeItem('access_token');
        window.localStorage.removeItem('ip');
        window.localStorage.removeItem('appNumber');
        window.localStorage.removeItem('FirstStageRessourceSavingTimeout');
        window.localStorage.removeItem('firstStageRessourceSavingInterval');
        window.localStorage.removeItem('RessourceSavingTime');
        window.localStorage.removeItem('ressourceSavingTimeout');
        window.localStorage.removeItem('stopAllScripts');
        window.localStorage.removeItem('stopAllScriptsTimeout');
        window.localStorage.removeItem('labelLength');
        location.reload()
      }
    }

    

    function testNumber(id)
    {
      var data = document.getElementById(id)
      if(isNaN(data.value))
      {
        alert("Make sure to enter a number!")
        data.value = ""
      }
    }

    function isTrueFalse(id)
    {
      var data = document.getElementById(id)
      if(data.value != "true" && data.value != "false")
      {
        alert("You must enter a true/false value (without any ' ' )")
      }
    }

    function testIp(id)
    {
      var data = document.getElementById(id)
      if(ValidateIPaddress(data.value) != true)
      {
        alert("INVALID IP ADDRESS!")
      } 
    }

    
    function backgroundCss(userRequest) // attempt to change the background with time of day... not working for now
    {  
      // console.log("userRequest = "+userRequest)
      if(userRequest) {
        backGroundModifiedByUser = !backGroundModifiedByUser
        if(backGroundModifiedByUser)
        {
          clearInterval("backgroundRandom")
          setTimeout(function(){document.body.style.background = randomColor();}, 500);
        }
        else if(!backgroundRandomActive)
        {
          console.log("restoring backgroundRandom interval");
          setBackgroundRandomInterval();
        }
      }
      // console.log("userRequest = "+backGroundModifiedByUser)
      if(!backGroundModifiedByUser && !userRequest)
      {  
        document.body.style.background = randomColor()
      }
    }

    function buildButtons()//
    {   
      logging("buildButtons()")   
      if(allDevices == undefined) 
      {
        setTimeout(function() {buildButtons();}, 1000)
        console.log("No data yet... please wait")
        loadCounter++;
        if(loadCounter > 10)
        {
          location.reload();
        }
        return
      }
      loadCounter = 0
      var s = allDevices.length;
      var lasti = i; 
      var lastiLights = i;   
      var device = "";
      var deviceId = "";
      var html = "";  
      var x = "";
      var lights = "";
      var someDimmers = false
      var labelSorted = "";
      var idSorted    = "";
      var deviceCapabilitiesSorted = "";
      var allDevicesLabels = [];
      var thereIsLight;
      
      setTimeout(function(){sliderEventListener();}, 2000); // must be called last for db to be updated
      setTimeout(function(){getMode();}, 2000);
      var checkModeInterval = setInterval(function(){getMode();}, 10000);  
      setInterval(function(){location.reload();}, 24 * 60 * 60 * 1000); // reload the page every 24 hours 
      toggleDebug(true)
      setTimeout(function(){ setRefreshInterval("reset"); }, FirstStageRessourceSavingTimeout * 1000);
      setRefreshInterval("reset"); //set refresh interval
      setTimeout(function(){refresh("buildButtons()/init()");}, 3000);
      for (var i = 0; i < s ; i++) 
      {
        labelSorted = allDevices[i].label;
        idSorted    = allDevices[i].id;    
        deviceCapabilitiesSorted = allDevices[i].capabilities; // is an array
        allDevicesLabels.push([labelSorted, idSorted, deviceCapabilitiesSorted]);
      }
      allDevicesLabelsSorted = allDevicesLabels.sort(); // update global variable with this array
      lights = x;
      s = allDevicesLabelsSorted.length; // should be same size but never mind... 
      i = 0;
      var k = i;  
      var deviceCount = 0; // we need a separate iteration counter here as to not affect the layout when the device is not a button (dimmers)
      var kL = i;
      var deviceCountLighs = 0;
      logging(JSON.stringify(allDevices))     
      for (i=0; i < s ; i++)
      {
        // listString += allDevices[i].name;
        var deviceLabel = allDevicesLabelsSorted[i][0]; // don't use allDevicesLabels here, not in same order as allDevicesLabelsSorted  
        var originalLabel = deviceLabel  
        var deviceLabel = trimLabel(deviceLabel, labelLength);
        var deviceLabel = deviceLabel.toLowerCase();    
        var deviceId = allDevicesLabelsSorted[i][1]; //listOfDevicesURL returns ONLY id, name, label and type.     
        var deviceCapabilities = allDevicesLabelsSorted[i][2];// so in order to get capabilities we need to use a different function using the proper url
        var id = deviceId; 

        var isSwitch = deviceCapabilities.find(element => element == "Switch") == "Switch"
        var isDimmer = deviceCapabilities.find(element => element == "SwitchLevel") == "SwitchLevel"
        var isPowerMeter = deviceCapabilities.find(element => element == "PowerMeter") == "PowerMeter"
        var isLock = deviceCapabilities.find(element => element == "Lock") == "Lock"
        var isContact = deviceCapabilities.find(element => element == "ContactSensor") == "ContactSensor"
        var isLight = deviceLabel.includes("light")
        var level = isDimmer ? allDevices[i].attributes.level : null
        

        logging("isContact ? "+isContact)

        /*********************************/
        if(isLock)
        {
          someLocks = true // confirm existence of locks for later construction
          lockList.push([deviceId, deviceLabel])
        }
        else if(isSwitch || isDimmer) // find SwitchLevel in capabilities 
        {

          if(isDimmer)
          {

            logging(deviceLabel+" is a dimmer")
            nSId++;
            var spanSliderId = "spanId"+nSId; // create a unique ID for this slider value text span
            deviceIdLevelButton = deviceId+"button"; // id of the corresponding on/off button of the dimmer
            someDimmers = true // confirm existence of dimmers for later construction
            spanIdList.push([deviceId, spanSliderId, deviceLabel]); // public list updated for later slider.value update
            dimmerList.push([deviceId, deviceLabel, spanSliderId, deviceIdLevelButton])
            

            deviceId = deviceId+"dimmer"

            if(isContact)
            {
              x += `<div  style="height:var(--buttonSize); width:var(--buttonSize);">          
              <button id='`+deviceId+`' onclick='toggleDevice(cmdSwitch,id)'>`+deviceLabel.toLowerCase()+`</button>
              </div>`; 
            }
            else if(isLight)
            {
              thereIsLight = true 
              lights +=  `<div style="height:var(--buttonSize); width:var(--buttonSize);">          
              <button id='`+deviceId+`'onclick='toggleDevice(cmdSwitch,id)'>`+deviceLabel.toLowerCase()+`</button>
              </div>`;  
            }
            else 
            {
              x += `<div style="height:var(--buttonSize); width:var(--buttonSize);">          
              <button id='`+deviceId+`'onclick='toggleDevice(cmdSwitch,id)'>`+deviceLabel.toLowerCase()+`</button>
              </div>`;  
            }
          }
          else 
          {
            if(isLight)
            {
              thereIsLight = true 
              lights +=  `<div style="height:var(--buttonSize); width:var(--buttonSize);">          
              <button id='`+deviceId+`'onclick='toggleDevice(cmdSwitch,id)'>`+deviceLabel.toLowerCase()+`</button>
              </div>`;  
            }
            else 
            {
              x += `<div style="height:var(--buttonSize); width:var(--buttonSize);">         
              <button id='`+deviceId+`' onclick='toggleDevice(cmdSwitch,id)'>`+deviceLabel.toLowerCase()+`</button>
              </div>`;  
            }
          }  
        }    
      }      
      if(thereIsLight)
      {
        x = lights + x
      }
      document.getElementById("buttons").innerHTML = x;
      if(someLocks)
      {
       var l = buildLocks(lockList)
       document.getElementById("locks").innerHTML = l;
     }
     if(someDimmers) 
     {
      var d = buildDimmers(dimmerList)
        // x = x + "<p></p>" + d 
        document.getElementById("dimmers").innerHTML = d;  
      }


      setTimeout(function() {setRefreshInterval("reset");}, 1000);
      setTimeout(function(){mouseEventListener();}, 1000) 
      setTimeout(function(){eventListeners();}, 1000)
      initialized = true;     
    };

    function getAllDevicesData(url)//
    {

      // console.log("getAllDevicesData(url) "+url)
      allDevices;
      var xhr = new XMLHttpRequest;       
      xhr.onreadystatechange = function() 
      {
        if (xhr.readyState == 4)
        {
          if(xhr.status == 200) 
          {
            allDevices = JSON.parse(xhr.responseText);  
          }
          else
          {
            xhrErrors++;
            console.log("----------Error--------", xhr.statusText);
            if(xhrErrors >= 20)
            {
              location.reload();
            }  
            return        
          }
            //if(callback) callback(allDevices); // could never get this to really work so far... need to figure this out
          };
        };
        xhr.open("GET", url, true);
        xhr.send();
      };

      function trimLabel(label, length)
      {
        label = label.replace("on Home 1", "");
        label = label.replace("on Home 2", "");
        label = label.replace("on Home 3", "");
        label = label.replace("on HOME 1", "");
        label = label.replace("on HOME 2", "");
        label = label.replace("on HOME 3", "");
        label = label.replace("temperature", "temp.")
        label = label.replace("Temperature", "temp.")

        if(label.length > length)
        {
         label =  label.substr(0, length);
       }
       return label 
     };

     function buildDimmers(dimmerList, lasti, maxDimmers)
     {
      var i = 0
      var s = dimmerList.length
      logging("dimmerList length="+s)
      var a = "<p></p>"
      for(i=0;i<s;i++)
      {
          //dimmerList.push([deviceId, deviceLabel, spanSliderId,deviceIdLevelButton])
          deviceId = dimmerList[i][0]
          deviceLabel = dimmerList[i][1]
          spanSliderId = dimmerList[i][2]
          deviceIdLevelButton = dimmerList[i][3]
          var buttId = deviceId+"button"
          a += `
          <div style="margin-top:50px; float:left; height:200px; width:var(--buttonSize);"> 
            <div style="float:left; height:20px; width:var(--buttonSize);">
              <span class="sliderSpanName">`+deviceLabel+`</span>        
            </div> 
            <div style="margin-top:80px; margin-left:30px; float:left; height:200px; width:var(--buttonSize);">  
              <input type='range' orient='vertical' min='0' max='99' step='1' id='`+deviceId+`' onchange='setLevel(id, value)'>
              <div style="margin-top:-100px;margin-left:20px;">
                <span class="sliderSpanVal" id=`+spanSliderId+`></span>
              </div>      
            </div> 
          </div> 
          `;
          
        }
        return a   
      };

      function buildLocks(lockList, lasti, maxLocks)
      {
        console.log("buildLocks()")
        var i = 0
        var s = lockList.length
        var b = "<tr>"
        for(i=0;i<s;i++)
        {

          deviceId = lockList[i][0]
          console.log(`creating button for lock ${deviceId} / ${lockList[i][1]}`)
          deviceLabel = lockList[i][1]   
          b += `<th><button class="lockButton" id='${deviceId}'onclick='toggleLock(id)'></button></th>`;       
        }

        if(i==(maxLocks-1) || i==lasti+maxLocks) // new row every 4 columns
        {
          lasti = i;
          b += "</td><td class='lockButton'>"; // new row and horizontal line
        } 
        b += "</tr>"
        return b   
      };

      /*************************************************END OF INITIALIZATION*********************************************/

      function clearLocalStorage()
      {
        window.localStorage.clear();
        logging("data cleared");
        location.reload();
      };

      function getMode()
      {
        var xmlhttp = new XMLHttpRequest();
        var listOfModesURL = "http://"+ip+"/apps/api/"+appNumber+"/modes?"+access_token
        var listOfModes = ""
        logging("http://"+ip+"/apps/api/"+appNumber+"/modes?"+access_token)
      xmlhttp.onreadystatechange = function() 
      {
        if (this.readyState == 4 && this.status == 200) 
        {
          var listOfModes = JSON.parse(this.responseText);
          logging("list of modes: "+JSON.stringify(listOfModes))
          var currentMode = "";
          var s = listOfModes.length

            for(var i = 0;i<s;i++) // find the active:true value 
            {
              var entry =  listOfModes[i]
              logging("entry: "+JSON.stringify(entry))

              if(entry.active == true)
              {
                currentMode = "Home is in "+entry.name.toLowerCase()+" mode"
                logging("Current Mode is:"+entry.name)
              }
            }
            if(currentMode == "")
            {
              currentMode = "error"
            }
            document.getElementById("currentMode").innerHTML = currentMode
          }
        };
        xmlhttp.open("GET", listOfModesURL, true);
        xmlhttp.send();
      };

      /******************************* VALUES UPDATES AND REFRESH *****************************/
      function updateButton(id, value, calledBy, label, type, secondType) 
      {
        logging(id+" "+value+" "+calledBy+" "+label+" "+type+" "+secondType)  
        logging("device id:"+id+" is:"+value+" ,label:"+label+", calledBy:"+calledBy+", type:"+type+", secondType:"+secondType);
      //background-color: url(/lightOff.png) no-repeat;
      var cssImage = "";
      var cssBackground = "";
      var cssHover = "";
      var cssTextColor = "";
      var buttonToChange = ""; 
      var buttonToChange = document.getElementById(id)
      
      var style = document.createElement('style');

      if(buttonToChange == null)
      {
        logging("failed to get element for "+label+"'s id("+id+") (a)")
        return 
      }
      label = trimLabel(label, labelLength)
      
      if(type == "lock")
      {
        buttonToChange.style.fontSize="12px";
        if(value == "locked")
        {
          //logging(label+" is "+value)
          buttonToChange.style.background="#FD0808" //     
          buttonToChange.style.color="white"
          buttonToChange.style.opacity="1"
        }
        else if(value == "unlocked")
        {
          //logging(label+" is "+value)
          buttonToChange.style.background="#F7F2F2" //
          buttonToChange.style.color="black"
          buttonToChange.style.opacity="1"
        }
        //console.log("---"+label+" is "+value)
        document.getElementById(id).innerHTML = label+" is "+value; 
      }
      else if(type == "button")
      {
        if(value == "on")
        {
          label = label.toLowerCase()
          if(label.includes("light")) // check if it's a light
          {        
            buttonToChange.className="buttonBulb on"; // change the light buld image by changing its class within the btn group
          }
          else if(label.includes("cam"))
          {
            buttonToChange.className="buttonCam on";
          }
          else if(secondType == "FanControl")
          {            
            buttonToChange.className="buttonFan on";
          }
          else if(secondType == "contact")   
          {
            buttonToChange.className="buttonContact on"; 
            buttonToChange.innerHTML = label+"\n (open)"
          }            
          else
          {
            buttonToChange.className="button on"; // allows to apply new button:hover colors
          }
          
          //buttonToChange.style.color="black"      
        }
        else if(value == "off") // if device has no value yet we still need to create its style so it can be appended below
        {
          label = label.toLowerCase()
          if(label.includes("light")) // check if it's a light
          {  
            buttonToChange.className="buttonBulb off"; // change the light bulb image by changing its class within the btn group
          }
          else if(label.includes("cam"))
          {
            buttonToChange.className="buttonCam off";
          }
          else if(secondType == "FanControl")
          {
            buttonToChange.className="buttonFan off";
          }
          else if(secondType == "contact")   
          {
            buttonToChange.className="buttonContact off";
            buttonToChange.innerHTML = label+"\n (closed)"
          }  
          else
          {
            buttonToChange.className="button off"; // allows to apply new button:hover colors
          }          
        }  
      }
      if(type == "dimmer") 
      {
        // logging("id:"+id+" label:"+label+" isSlider: "+isSlider+" value:"+value)
        var max = buttonToChange.max 
        var min = buttonToChange.min 
        var level = buttonToChange.value
        // iconsole.log(`max = ${max} value = ${buttonToChange.value}`)

        if(value == "off")
        {
          buttonToChange.style.background = `linear-gradient(to right, blue 0%, blue ${(level-min)/(max-min)*100}%, #DEE2E6 ${(level-min)/(max-min)*100}%, #DEE2E6 100%)` 
        }
        else if(value == "on")
        {
          buttonToChange.style.background = `linear-gradient(to right, yellow 0%, yellow ${(level-min)/(max-min)*100}%, #DEE2E6 ${(level-min)/(max-min)*100}%, #DEE2E6 100%)` 
        }
      }
    };

    function refreshAll(origin)
    {

      checkVersion();
      logging("refreshAll("+origin+")")
      getAllDevicesData(listOfDevicesURL) //listOfDevicesURL, function(allDevices){}); // callback() method
      setTimeout(function(){refresh("fromRFA")}, 500); // give time for data collection due to the asyncrhonus nature of ajax. 
    };

    function toRGB(rgbstring) {

    if(rgbstring.length > 1)
    {
      var rgb = rgbstring.split( ',' ) ;
      r=parseInt( rgb[0].substring(4) ) ; // skip the first 4 chars "rgb("
      g=parseInt( rgb[1] ) ; // this is just g
      b=parseInt( rgb[2] ) ; // 

      // console.log("rgb as list = ", rgb)

      let result = [r, g, b]
      // console.log(result)
      
      return result 

      }
      else {
        console.log ("no rgb value yet")
        return rgbstring
      }

    }

    
    var DOMloaded = false; 

    document.addEventListener('DOMContentLoaded', function(event) {  
      console.log("DOM FULLY LOADED")  
      DOMloaded = true;
      document.querySelector("body").style.background = "rgb(7, 141, 216)"
    })

    function randomColor()
    {
      if(!DOMloaded) return 

      let r = Math.floor(Math.random() * 256);
      let g = Math.floor(Math.random() * 256);
      let b = Math.floor(Math.random() * 256);

      let result = `rgb(${r}, ${g}, ${b})`

      //rgb(7, 141, 216) is the slider's background color, so prevent this one from being generated

      const currentBackgroundColor = document.querySelector('body').style.background

      const curRgb = toRGB(currentBackgroundColor)
      // console.log("curRgb: ", curRgb)

      // return result;

      let curR = curRgb[0]
      let curG = curRgb[1]
      let curB = curRgb[2]
      let offset = 50

      if((r < curR + offset || r > curR - offset) && (g < curG + offset || g > curG - offset) && (b < curB + offset || b > curB - offset))
      {
        // console.log(`new background color: rgb(${r}, ${g}, ${b})`)
        return result;
      } 
      else 
      {
        console.log(`rgb(${r}, ${g}, ${b} is too close to ${curRgb}`)
      }   
    }

    function refresh(origin)
    {
      logging("refresh("+origin+")")

      if(allDevices == undefined) 
      {
        setTimeout(function() {refreshAll("watchdog");}, 1000)
        console.log("No data... ")
        loadCounter++;
        if(loadCounter > 4)
        {
          location.reload();
        }
        
        return
      };
      const pageTitleElement = document.getElementById("pageTitle")
      if(pageTitleElement != null) pageTitleElement.remove() // once data is loaded, delete this message

      loadCounter = 0
      var s = allDevices.length;
      // var s = allDevicesLabelsSorted.length;
      //alert(allDevicesLabelsSorted);
      logging("********************* LIST SIZE: "+s)
      var lasti = i;  
      var device = "";
      var deviceId = "";
      var a = 0;
      var b = 0
      mainPower = 0
      otherPowerSwitches = 0
      for (var i = 0; i < s ; i++) 
      {
        var myObj = allDevices[i]
        logging(JSON.stringify(myObj))
        var SwitchState = myObj.attributes.switch
        var level = myObj.attributes.level 
        var ObjCapabilities = myObj.capabilities    
        var deviceLabel = trimLabel(myObj.label, labelLength);
        deviceLabel = deviceLabel.toLowerCase();
        var deviceId = myObj.id; 
        var deviceType = "unknown"   
        var size = myObj.attributes.length;
        var hasSwitchCapability = ObjCapabilities.find(element => element == "Switch") == "Switch"  
        var hasPowerCapability = ObjCapabilities.find(element => element == "PowerMeter") == "PowerMeter" 
        var hasSwitchLevelCapability = ObjCapabilities.find(element => element == "SwitchLevel") == "SwitchLevel" 
        var isLock = ObjCapabilities.find(element => element == "Lock") == "Lock" 
        var isContact = ObjCapabilities.find(element => element == "ContactSensor") == "ContactSensor"
        var isFan = ObjCapabilities.find(element => element == "FanControl")== "FanControl" 
        logging(deviceLabel+" hasSwitchLevelCapability ? "+hasSwitchLevelCapability)
        logging(ObjCapabilities)
        if(deviceId == null)
        {
          console.log("failed to get element for "+label+"'s id '"+id+"' (refresh)")
        }  
        else 
        {
          if(isLock) // is it a lock ? 
          {
            var state = myObj.attributes.lock
            logging(deviceLabel+" is "+state+" deviceId = "+deviceId)
            deviceType = "lock"      
            document.getElementById(deviceId).innerHTML = deviceLabel+" is "+state;       
            updateButton(deviceId, state, "refresh()",deviceLabel, deviceType, myObj.type);
          }    
          else if(hasPowerCapability && hasSwitchCapability)
          {
            deviceType = "meterSwitch"
            var powerVal = myObj.attributes.power
            if(powerVal != null)
            {
              if(powerVal != null) otherPowerSwitches += parseInt(powerVal)
                logging(deviceLabel+" IS POWER METER val = "+powerVal+" Watts")
              document.getElementById("powerSwitches").innerHTML = "Switches:"+parseInt(otherPowerSwitches)+"W";
              if(powerVal != null) document.getElementById(deviceId).innerHTML = deviceLabel+" is "+parseInt(powerVal)+"W"; 
              updateButton(deviceId, SwitchState, "refreshAll()", deviceLabel, "button", myObj.type);  // it's a switch so has to be updated as button type
              switchStateValues.set(deviceId, [deviceLabel, deviceType, SwitchState, level]); // store devices states for instantaneous button color update in toggledevice()
              logging("index "+i+": "+deviceLabel+" powerVal = "+powerVal+" total is:"+otherPowerSwitches)
            }
            else
            {
              logging(deviceLabel+" powerVal is null - deviceId is:"+deviceId);
            }
          }
          else if(hasPowerCapability && !hasSwitchCapability)
          {
            deviceType = "mainMeter"
            var powerVal = myObj.attributes.power
            if(powerVal != null)      
            {
              mainPower +=  powerVal
              logging("-----------MAIN POWER METER DETECTED val = "+powerVal+" Watts")
              document.getElementById("homepower").innerHTML = "Home:"+parseInt(mainPower)+"W"
            }
            else
            {
              console.log(deviceLabel+" powerVal is null - deviceId is:"+deviceId);
            }
          }
          else if(hasSwitchLevelCapability) // is it a dimmer ? 
          {

            deviceType = "dimmer"
            refreshSlider(deviceId, level, deviceLabel); 
            updateButton(deviceId, SwitchState, "refreshAll()", deviceLabel, deviceType, myObj.type);
            switchStateValues.set(deviceId, [deviceLabel, deviceType, SwitchState, level]); // store devices states for instantaneous button color update in toggledevice()

            var thisId = deviceId+"button" // button next to the dimmer
            logging("updateButton("+thisId+", "+SwitchState+", 'refreshAll()', "+deviceLabel+", "+deviceType+", '');")
            updateButton(thisId, SwitchState, "refreshAll()", deviceLabel, deviceType, secondType);
            switchStateValues.set(thisId, [deviceLabel, deviceType, SwitchState, level]); 

            var thisId = deviceId+"dimmer" // every dimmer must have its own on/off button within the buttons panel
            deviceType = "button"
            var secondType = isFan ? "FanControl" : isContact ? "contact" : "buttonDimmer"
            updateButton(thisId, SwitchState, "refreshAll()", deviceLabel, deviceType, secondType);
          }
          else if(hasSwitchCapability) // is it a switch ? 
          {
            deviceType = "button"
            //if(isFan) console.log(deviceLabel+" has fan capability")
            if(isFan) secondType = "FanControl"
            updateButton(myObj.id, SwitchState, "refreshAll()", deviceLabel, deviceType, secondType);
            switchStateValues.set(deviceId, [deviceLabel, deviceType, SwitchState, level]); // store devices states for instantaneous button color update in toggledevice() 

          }
           
        }       
        logging("a="+a+"/"+i);
        logging("b="+b+"/"+i); 
      }
      logging("allDevices = "+JSON.stringify(allDevices));
      logging(switchStateValues);
    };

    function refreshSlider(deviceId, value, label)
    {
      var s = spanIdList.length;
      var spanId = ""; 
      var buttonId = "" // object id of the button onto wich we want to display the device name + its value in %
      var buttonName = "" // just for display purpose, not an object id... 
      for(var i = 0;i<s;i++)
      {
        if(spanIdList[i][0] == deviceId){

          spanId = spanIdList[i][1];
          buttonName = spanIdList[i][2];
        }
      }
      if(spanId == "" || spanId == null || deviceId == null)
      {
        console.log("failed to get element for "+label+"'s id("+id+") (e)")
        return
      }  
      logging("label:"+label+" spanId:"+spanId+" deviceId:"+deviceId+" value:"+value)
      document.getElementById(spanId).innerHTML = value; // update span number value
      document.getElementById(deviceId).value = value;// update the value displayed on the slider}
    };

    /******************************* DEVICE COMMANDS**************************************/
    function setLevel(id, value)
    {
      setRefreshInterval("reset"); // resume normal interval
      logging(value+" "+id);      
      var data = switchStateValues.get(id) // we collect last values to append the level value to this device's last states
      var lastLabel = data[0]
      var lastDeviceType = data[1]
      var LastSwitchState = data[2]
      var newLevel = value      
      if(value == 0){
        value = 1 //for some reason, hubitats' level devices will go back to previous level value if set to 0 from here... 
        newLevel = 1
      }
      switchStateValues.set(id, [lastLabel, lastDeviceType, LastSwitchState, newLevel]); // "newLevel" is the data that is being updated here
      var url = "http://"+ip+"/apps/api/"+appNumber+"/devices/"+id+"/setLevel/"+value+"?"+access_token;
      var xmlhttp = new XMLHttpRequest();
      xmlhttp.onreadystatechange = function() 
      {
        if (this.readyState == 4 && this.status == 200) 
        {
        };
      };
      xmlhttp.open("GET", url, true);
      xmlhttp.send();
      setTimeout(function(){setValueOneToggle(id, value)}, 2000)
    };

    function setValueOneToggle(id, value){
      if(value == 1) toggleDevice("off", id)
        if(value == "toggle") toggleDevice("toggle", id)
      };

    function toggleDevice(cmd, id) 
    { 
      console.log("toggle "+cmd+" "+id)
      setRefreshInterval("reset"); // resume normal interval
      var trueId = id.includes("button") ? id.replace("button", "") : id.includes("dimmer") ? id.replace("dimmer", "") : id 
      logging(switchStateValues)
      var data = switchStateValues.get(id);
      console.log("collected data for this "+id+": "+data)
      if(data != undefined)
      {    
        var deviceLabel   = data[0] 
        var deviceType    = data[1]
        var LastSwitchState = data[2] 
        var LastLevel   = data[3]
        var newState = LastSwitchState != null && (deviceType == "button" || id.includes("button")) ? LastSwitchState == "off" ? "on" : "off" :"false"
        console.log("newState = "+newState)
        if(newState != "false")
        {

          if(deviceType == "button" || id.includes("button")) 
          {
            console.log("UPDATING BUTTON")
            updateButton(trueId, newState, "toggleDevice()", deviceLabel, deviceType, "");
            updateButton(id, newState, "toggleDevice()", deviceLabel, deviceType, "");
          }
        }
        setTimeout(function(){refreshAll("toggle");}, 1000)
      }
      var url = "http://"+ip+"/apps/api/"+appNumber+"/devices/"+trueId+"/"+cmd+"?"+access_token;    
      var xmlhttp = new XMLHttpRequest();
      xmlhttp.onreadystatechange = function() 
      {
        if (this.readyState == 4 && this.status == 200) 
        {
        };
      };
      xmlhttp.open("GET", url, true);
      xmlhttp.send();
    };

    function toggleLock(id)
    {
      let thisLock = allDevices.find(element => element.id == id)
      logging("thisLock = ", thisLock)
      
      let lockState = thisLock.attributes.lock
      logging("///////// lockState:",lockState)
      logging("*********lock id:"+id+" is "+lockState)
      if(lockState == null || lockState == undefined) 
      {
        console.log("REQUEST STOPPED")
        return 
      }
      var cmd = lockState == "locked" ? "unlock" : "lock"
      document.getElementById(id).innerText =  lockState == "locked" ? "unlocking" : "locking"; 
      logging("sending lock cmd: "+cmd)
      var url = "http://"+ip+"/apps/api/"+appNumber+"/devices/"+id+"/"+cmd+"?"+access_token;    
      var xmlhttp = new XMLHttpRequest();
      xmlhttp.onreadystatechange = function() 
      {
        if (this.readyState == 4 && this.status == 200) 
        {
        };
      };
      xmlhttp.open("GET", url, true);
      xmlhttp.send();
      getAllDevicesData(listOfDevicesURL) //listOfDevicesURL, function(allDevices){}); 
      setTimeout(function(){refresh("toggleLock")}, 5000);
    };

    /*******************************END OF DEVICE COMMANDS**************************************/
    function eventListeners()
    {
      const form = document.getElementById("submitBtnRefreshRate")
      const refreshText = document.getElementById("refreshRate")
      inputRefreshRate.addEventListener("keyup", function(event) {
        if (event.keyCode === 13) {
          event.preventDefault();
          form.click();
          alert("refresh rate updated to "+refreshText.value+" seconds")
        }
      });
    }

    function handleDragStart(e){
      this.style.opacity = '0.4';
    }
    function handleDragEnd(e){
      this.style.opacity = '1';
    }
    let items = document.querySelectorAll('.container .box');
    items.forEach(function (item) {
      item.addEventListener('dragstart', handleDragStart);
      item.addEventListener('dragend', handleDragEnd);
    });

    function mouseEventListener() {
      document.querySelector('body').addEventListener("mousemove", function(event) {
      setRefreshInterval("reset"); // resume normal interval
    });
      console.log("event listner")
    };

    function sliderEventListener()
    {
      // create an event listener for all sliders inputs, if any
      var b = spanIdList.length;
      var n = 0;
      logging("b = "+b+" n = "+n);
      for (b != 0; n < b; n++ ) { 
        //[deviceId, spanSliderId]  
        var slider = spanIdList[n][0]; // slider object id
        var output = spanIdList[n][1]; // span object that shows the value number
        logging("creating listener for "+slider+" && "+output);
        var rangeInput = document.getElementById(spanIdList[n][0]);    
        rangeInput.addEventListener("input", sliderOutput(slider, output, n), false);
      };
    };

    function sliderOutput(sliderObj, spanObj, n)
    {
      var buttonName = spanIdList[n][2];
      var deviceLabel = spanObj;
      //logging(buttonName+" "+deviceLabel+" spanObj is:"+spanIdList[n][1])
      var slider = document.getElementById(sliderObj);
      var output = document.getElementById(spanObj);
      //var output2 = document.getElementById(buttonId);

      //logging(buttonName+"slider value: "+slider.value+"%")
      
      output.value = slider.value; // update the slider position
      output.innerHTML = slider.value;// update span number dispayed value // we actually prefer to keep the value as modified with cursor otherwise we get the last value and the new value will appear only after next refresh cycle.

      const min = slider.min
      const max = slider.max
      const value = slider.value

      slider.style.background = `linear-gradient(to right, red 0%, red ${(value-min)/(max-min)*100}%, #DEE2E6 ${(value-min)/(max-min)*100}%, #DEE2E6 100%)` // fills up the track as we move the cursor

      
      slider.oninput = function() //allows to see changes as the cursor is being moved
      {
        this.style.background = `linear-gradient(to right, red 0%, red ${(this.value-this.min)/(this.max-this.min)*100}%, #DEE2E6 ${(this.value-this.min)/(this.max-this.min)*100}%, #DEE2E6 100%)`
        //updates cursor and number value once input done
        output.value = slider.value; // update the slider position
        output.innerHTML = this.value;// update span number dispayed value     
        
        if(slider.value == 0 || slider.value == 1)
        {
          //document.getElementById(sliderObj+"button").innerHTML = "off"; // we do this with colors so as to keep the device name on the button
          var calledBy = "sliderOutput 524"
          
          updateButton(sliderObj, "off", calledBy, deviceLabel, "dimmer", "dimmer");
        }
        else
        {
          //document.getElementById(sliderObj+"button").innerHTML = "on"; // we do this with colors so as to keep the device name on the button
          var calledBy = "sliderOutput 895"
          updateButton(sliderObj, "on", calledBy, deviceLabel, "dimmer", "dimmer");
        }
      }
    };  

    /******************************* INTERVALS AND TIMEOUTS MANAGEMENT*************************/
    function setRefreshInterval(param)
    {  

      //console.log("setRefreshInterval param: '"+param+"'");
      var refreshState = "NA";
      var refInterval = 0;
      clearAllIntervals()
      if(param == "reset") // short interval - set at boot then after resume from saving modes
      {        
        // console.log("Short ressource interval")
        refInterval = ShortRefreshTime * 1000;
        interval1 = setInterval(function(){refreshAll("interval1");}, refInterval);
        timeoutRessourceSaving = setTimeout(function(){ setRefreshInterval("firstStage"); }, FirstStageRessourceSavingTimeout * 1000); // reset timeout
      }
      else if(param == "firstStage") // first stage ressource saving
      {
        // console.log("First stage ressource saving interval")
        refInterval = firstStageRessourceSavingInterval * 1000;
        interval2 = setInterval(function(){refreshAll("interval2");}, refInterval); // set interval refresh of n seconds
        timeoutRessourceSaving = setTimeout(function(){ setRefreshInterval("secondStage"); }, ressourceSavingTimeout * 1000 * 60 * 60); // schedule the next refresh intervals
      }
      else if(param == "secondStage") //Ressourcesaving mode
      {   
        // console.log("Second ressource saving interval") 
        refInterval = RessourceSavingRefreshInterval * 1000;
        interval3 = setInterval(function(){refreshAll("interval3");}, refInterval); // set interval refresh of n seconds
      }
      
      if(refInterval === NaN) refInterval = 4; 

      refreshState = "refresh rate is "+refInterval/1000+" seconds"; // value to display on top of the page
      //console.log(refreshState)
      var ob = document.getElementById("refreshRateMessage")
      if(ob != undefined) ob.innerHTML = refreshState;  

      if(stopAllScripts == true && stopAllScriptsTimeout != 0)
      {
        setTimeout(function(){clearAllIntervals();}, stopAllScriptsTimeout * 1000 * 60 * 60)
        clearTimeout(timeoutRessourceSaving);
        console.log("All scripts will be stopped after "+stopAllScriptsTimeout+" hours");
      }
    };

    function clearAllIntervals()
    {
      clearInterval(interval1);
      clearInterval(interval2);
      clearInterval(interval3);
    };

    /******************************************* DEBUG ***************************/
    function toggleDebug(startCmd)
    {
      enableDebug = startCmd ? false : !enableDebug
      var debugStatus = enableDebug ? "DEBUG IS ON" : "DEBUG IS OFF"
      if(enableDebug == true)
      {
        setTimeout(function(){ toggleDebug(true); }, debugTimeout * 1000 * 60); 
      }
      else {
        //console.clear();
      }
      document.getElementById("debugbtn").innerText = debugStatus
    };

    function logging(message)
    {
      if(enableDebug == true)
      {
        var maxDebug = 20000
        called+=1
        if(called < maxDebug)
        {
          console.log(message)
        }
        else if(called == maxDebug)
        {
          lastPause = Date.now()
          setTimeout(() => {
            const millis = Date.now() - lastPause;
            console.clear();
            console.log ("called reset");
            called = 0;
          }, 2000);
        }    
      }
    };