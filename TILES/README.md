<!-- markdownlint-disable MD024 -->

# HUBITAT TILES by Elfege

## How to Use Tiles for Hubitat

1. **Copy the Files**  
   Save the files into a directory of your choice. For best results, use either:  
   - A webroot directory  
   - The built-in web server on your Hubitat Elevation (using Hubitat's File Manager).

2. **Use a Dedicated Directory** *(Strongly Recommended)*  
   Organize the files by placing them into a dedicated folder.

3. **Create and Configure `settings.json`**  
   In the same folder, create a file named `settings.json` and include the following content:

   ```json
   {
       "access_token": "your_access_token",
       "ip": "your_hub_ip",
       "appNumber": "app_number"
   }
   ```

   - **`access_token`**: Obtain this from your Maker API app.  
   - **`ip`**: Enter your Hubitat hub's IP address, found in the Maker API app.  
   - **`appNumber`**: Retrieve this from your Maker API app as well.

4. **Load the Page**  
   Open your browser and navigate to:  
   `http://[your_hub_ip]/[dedicated_directory_if_applicable]/`  

   Your page should now display any switches, lights, or locks configured in your Maker API app.

---

## Release Notes

### January 5, 2025

#### Updates

- Improved mobile UI for tiles and controls
- Enhanced modal interactions
- Standardized design elements across tiles
- Optimized performance and fixed known bugs

### February 22, 2023

#### Updates

- Added power consumption tile display
- Fixed bugs related to display and interaction issues

### February 4, 2023

#### Updates

- Major design and performance enhancements
- New device management capabilities
- Improved layout and responsiveness

### January 22, 2023

#### Updates

- Multiple design improvements
- Enhanced layout and responsiveness across devices

### January 20, 2023

#### Updates

- Restored locks management functionality

### January 18, 2023

#### Updates

- Refactored code to ES2017 standards
- Improved code maintainability and performance
- Updated documentation

### January 17, 2023

#### Updates

- Released `elfegeTILES 2.0`
- ES2015 and ES2017 refactoring
- Enhanced token management system

---

This documentation will be updated as further improvements are made.
