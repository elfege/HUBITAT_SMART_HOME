# elfegetiles
Create tiles for your hubitat hub using maker api

WebApp Optimized to be used with webkit based browsers such as Safari or Chrome. It does not work very well (yet) with FireFox, although if you don't have any dimmer you shouldn't feel the difference. 

Simply dowload all the files at the root of your Hubitat's file manager interface and then, in any browser, open this address: 

http://192.168.xx.xx/local/elfegeTiles.html (you can also put those files on a separate webserver and rename "elfegeTiles.html" as "index.html" to simplify the url. 

You will be prompted to enter your hubitat's maker API's number and access token as well as given the opportunity to tweak your preferences (especially in term of device ressoruces saving methods). From there, once you submit the form, you should see your tiles. 

You can also enter your access token in the code directly in the "setDefaultPreferences()" function so you can then simply hit the "default values" button on the form on every device you want to use to display your tiles, instead of having to type the token on every new device/computer/ipad you wish to use for these tiles. 

On iPad and iPhone you will be offered the possibility to add this to your home screen so you can show your tiles in a nice webapp presentation. 

Feel free to submit any comment for improvement. I'm not (yet) a professional developper so I know I still have some way to go to make things great, although so far this interface has been more convenient and reactive than any other I've ever gotten the opportunity to try. 


