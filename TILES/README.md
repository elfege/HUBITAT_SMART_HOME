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

# NOTES

# Development Documentation - January 5, 2025

## Overview

Today I worked on several issues related to the thermostat interface in our home automation system, primarily focusing on mobile responsiveness and user interaction patterns. The work spanned multiple areas including modal functionality, display issues, and mobile-specific behaviors.

## Timeline of Changes

### Morning Session (11:05 AM - 11:30 AM)

#### Thermostat Container Interaction Implementation

- Implemented differential behavior for smart devices vs desktop devices
- Smart devices: Container opens in modal for exclusive control
- Desktop: Container slides open for interactive access
- Debugged event listener issues:
  - Relocated event listener attachment to post-DOM insertion
  - Added extensive logging for troubleshooting
  - Switched from class-based to ID-based selectors for better reliability

### Early Afternoon Session (2:15 PM - 3:00 PM)

#### Thermostat Mode Buttons

- **2:15-2:30 PM**: Fixed button visibility issues
- **2:35-2:40 PM**: Removed erroneous percentage symbol from temperature display
- **2:45-2:50 PM**: Identified and removed unexpected degree symbol (caused by CSS rule)
- **2:55-3:00 PM**: Resolved mobile display centering issues

### Evening Session (7:30 PM - 7:41 PM)

#### Mobile Display Optimization

- **7:30 PM**: Identified cursor circle centering issue on mobile displays
- **7:34 PM**: Diagnosed setpoint and temperature display shift issue
- **7:36 PM**: Modified roundSlider initialization code
- **7:40 PM**: Implemented focused fix by removing setTimeout wrapper

## Technical Implementation Details

### Modal Implementation

```javascript
function openThermostatModal(thermostatWrap, id) {
  const modal = $('<div>').addClass('modal fade')
    .attr('id', `thermostatModal${id}`);
  const modalDialog = $('<div>')
    .addClass('modal-dialog modal-dialog-centered modal-lg');
  const modalContent = $('<div>').addClass('modal-content');

  $(thermostatWrap).appendTo(modalContent);
  modal.append(modalDialog.append(modalContent));
  $('body').append(modal);
  
  modal.modal('show');
  
  modal.on('click', (event) => {
    if (event.target === modal[0]) {
      modal.modal('hide');
    }
  });
}
```

### CSS Optimizations

- Standardized container widths across device types
- Implemented flexbox-based centering for consistent alignment
- Added responsive breakpoints for mobile optimization
- Removed redundant positioning rules

## Known Issues and Future Work

1. Modal cleanup on device rotation needs improvement
2. Temperature display occasionally shows rounding artifacts
3. Consider implementing transition animations for smoother UX
4. Need to add error handling for network timeouts

## Lessons Learned

1. Always test modal implementations with both touch and click events
2. CSS inheritance in the roundSlider library can cause unexpected behavior
3. Mobile-first development would have prevented several of the issues we encountered
4. Important to maintain clear separation between smart device and desktop behaviors

## Next Steps

1. Implement comprehensive error handling
2. Add loading states for network operations
3. Optimize modal transitions
4. Add automated tests for device-specific behaviors

## Dependencies

- jQuery 3.6.3
- Bootstrap 4.0.0
- roundSlider library
- axios 1.2.2

This documentation will be updated as we continue to refine the implementation.
