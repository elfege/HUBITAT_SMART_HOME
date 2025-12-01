# Advanced Motion Lighting Management

## Table of Contents
1. [General Description of Functionalities](#1-general-description-of-functionalities)
2. [History of Changes](#2-history-of-changes)
3. [Improvements in V2 compared to V1](#3-improvements-in-v2-compared-to-v1)
4. [Quick Start](#4-quick-start)
5. [Configuration](#5-configuration)
6. [Troubleshooting](#6-troubleshooting)
7. [Contributing](#7-contributing)
8. [License](#8-license)

## 1. General Description of Functionalities

The Advanced Motion Lighting Management is a sophisticated SmartThings app designed to automate lighting based on motion detection, time, modes, and various other conditions. Key functionalities include:

- Motion-activated lighting control
- Time-based and mode-based restrictions
- Dimming and color control for compatible lights
- Illuminance-based lighting decisions
- Button control for app pausing and light toggling
- Contact sensor integration for additional control options
- Override capabilities for manual control
- Watchdog functionality for monitoring hub health

## 2. History of Changes

### Version 1.0 (Original)
- Basic motion-activated lighting control
- Simple time-based restrictions
- Basic mode-based control
- Dimming capabilities for compatible switches
- Contact sensor integration

### Version 1.5 (Intermediate Updates)
- Added override functionality for manual control
- Improved logging capabilities
- Enhanced mode-specific timeout settings

### Version 2.0 (Current)
- Restructured and modularized code base
- Added color control for RGB-capable dimmers
- Implemented mode-specific dimming levels
- Enhanced pause functionality with flexible duration options
- Integrated illuminance sensor support
- Improved button control options
- Implemented comprehensive logging system
- Added watchdog functionality for hub monitoring
- Refined motion detection logic
- Improved user interface and settings organization

## 3. Improvements in V2 compared to V1

1. **Enhanced Lighting Control:**
   - Added support for color control in RGB-capable dimmers
   - Implemented mode-specific dimming levels
   - Improved handling of switches that should remain off in certain modes

2. **Pause Functionality:**
   - Redesigned with flexible duration options (minutes or hours)
   - Added ability to control lights when pausing/resuming the app

3. **Illuminance-based Control:**
   - Integrated illuminance sensor support to prevent unnecessary light activation

4. **Button Control:**
   - Enhanced options for pausing/resuming the app
   - Added ability to toggle, turn on, or turn off lights with button presses

5. **Logging and Debugging:**
   - Implemented comprehensive logging system with debug, trace, and description logs
   - Added automatic disabling of debug and trace logs after 30 minutes

6. **Watchdog Functionality:**
   - Added feature to monitor and report on hub health and connectivity issues

7. **Override Handling:**
   - Improved functionality to handle manual switch operations more effectively

8. **Motion Detection Logic:**
   - Refined for more accurate triggering and improved reliability

9. **Time and Mode Restrictions:**
   - Enhanced handling for more precise control

10. **User Interface:**
    - Improved settings page layout for easier configuration
    - Added more granular control options

11. **Performance Optimizations:**
    - Optimized code execution to reduce unnecessary processing
    - Improved event handling efficiency

12. **Error Handling and Robustness:**
    - Added more comprehensive error checking and handling

13. **Compatibility:**
    - Ensured compatibility with a wider range of devices and hub capabilities

## 4. Quick Start

To install and set up the Advanced Motion Lighting Management app:

1. Open your SmartThings IDE
2. Go to "My SmartApps"
3. Click on "New SmartApp"
4. Copy and paste the code from `Advanced_Motion_Lighting_Management_V2.groovy`
5. Click "Create"
6. Click "Publish" -> "For Me"
7. Open your SmartThings mobile app
8. Go to "Automations" -> "Add a SmartApp"
9. Find and select "Advanced Motion Lighting Management V2"
10. Follow the on-screen instructions to configure the app

## 5. Configuration

The app offers various configuration options:

- **Motion Sensors**: Select the motion sensors to trigger the lighting.
- **Switches**: Choose the switches or dimmers to control.
- **Time Restrictions**: Set specific times when the app should or shouldn't operate.
- **Mode Restrictions**: Configure the app to work only in certain modes.
- **Dimming Options**: Set default and mode-specific dimming levels.
- **Color Control**: Configure color settings for RGB-capable dimmers.
- **Illuminance Threshold**: Set a lux level to prevent unnecessary light activation.
- **Button Control**: Set up buttons to pause/resume the app or control lights.
- **Contact Sensors**: Integrate contact sensors for additional control.
- **Logging Options**: Configure debug, trace, and description logging.

Refer to the app's settings page for detailed configuration options.

## 6. Troubleshooting

- **Lights not turning on**: Ensure motion sensors are properly placed and functioning.
- **Lights not turning off**: Check timeout settings and ensure no continuous motion is being detected.
- **App not respecting modes**: Verify mode restrictions are correctly set up.
- **Dimming not working**: Ensure switches support dimming and dimming is enabled in settings.
- **Color control issues**: Verify switches support color control and color settings are correctly configured.
- **Button control not working**: Check button device selection and configured actions.

For persistent issues, enable debug logging and check the IDE logs for more information.

## 7. Contributing

Contributions to the Advanced Motion Lighting Management app are welcome! Here's how you can contribute:

1. Fork the repository
2. Create a new branch for your feature or bug fix
3. Commit your changes
4. Push to your fork and submit a pull request

Please ensure your code adheres to the existing style and includes appropriate tests.

## 8. License

This project is licensed under the MIT License. See the LICENSE file for details.