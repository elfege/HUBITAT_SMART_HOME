# HOW TO USE TILES for HUBITAT? 

1) Copy the files into a directory of your choice - preferably into a web root directory on your local network and/or in any case on a constantly accessible network path. 
2) You can also simply use your HE HUB's built-in web server by copying those onto your hub directly, using Hubitat's files options. ***In this case make sure to copy the files into a new dedicated directory!!***

3) In the same folder where you just copied the files, create a credentials.json file (you can just create a new text file and save it with the extension ".json") and add the following content exactly:


```JSON 
    "access_token": "your_access_token", ➡️This info is available in your MAKER API app. 
    "ip": "your_hub's_ip",  ➡️This info is available in your MAKER API app. 
    "appNumber": "app_number"   ➡️This info is available in your MAKER API app. 
```

5) In a browser, simply type :"http://[your_ip]/[dedicated_directory_if_applicable]/ and your page should load with any switch, light or lock you've added to your MAKER API app. 

