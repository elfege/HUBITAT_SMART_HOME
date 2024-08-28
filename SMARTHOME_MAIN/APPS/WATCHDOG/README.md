# CSP Watchdog

## Disclaimer

 <b style="color:red;">This application can reboot your Hubitat hub. Use with caution and ensure you understand the implications before enabling automatic reboots.</b>

CSP Watchdog is a comprehensive monitoring and maintenance application for Hubitat home automation systems. It provides robust health checks for both local and remote hubs, ensuring system stability and responsiveness.

## Features

1. **Local Hub Monitoring**:
   - Motion sensor-triggered health checks
   - Virtual switch response time testing
   - Physical Z-wave switch monitoring for mesh stability

2. **Remote Hub Monitoring**:
   - Regular ping checks to ensure remote hub responsiveness
   - Configurable ping intervals and reboot thresholds

3. **Automatic Reboot Functionality**:
   - Local hub reboot on critical events or failed tests
   - Remote hub reboot capability with fallback mechanisms
   - Reboot rate limiting to prevent reboot loops

4. **Flexible Configuration**:
   - Customizable reboot thresholds
   - Option to disable reboots for testing purposes

5. **Comprehensive Logging**:
   - Detailed debug logging for troubleshooting
   - Clear warning and error messages for critical events

6. **Notification System**:
   - Support for notification devices and speakers
   - Alerts for critical events and reboot actions

## Installation

1. Log in to your Hubitat hub's admin interface.
2. Go to "Apps Code" and click "New App".
3. Paste the contents of `CSP_WATCHDOG_V2.groovy` into the editor.
4. Click "Save".
5. Go to "Apps" and click "Add User App".
6. Select "CSP Watchdog" from the list and follow the configuration prompts.

## Configuration

### Local Hub Monitoring

- Select motion sensors to trigger health checks.
- Optionally, choose a virtual switch for response time testing.
- Select physical Z-wave switches for mesh stability monitoring (avoid hub mesh devices).
- Set the number of failed tests before a reboot is triggered.

### Remote Hub Monitoring

- Enable remote hub monitoring.
- Configure the remote hub settings on a separate page.
- Set ping interval and reboot threshold for the remote hub.

### Notifications

- Select notification devices for important alerts.
- Optionally, choose speaker devices for voice notifications.

### Reboot Controls

- Options to disable local and remote hub reboots.
- Buttons to manually initiate reboots (with confirmation).

### Additional Actions

- Run an immediate health check.
- Update the app.
- Clear reboot history.
- Enable dev mode for testing reboot functionality without actual reboots.

### Additional Remote Hub Reboots

- Enter IP addresses for additional remote Hubitat hubs that you want to be able to reboot.
- Multiple IP addresses can be entered, separated by commas.
- Each added hub will have its own reboot button.
- These additional hubs are for manual reboot only and are not monitored by the app.
- Reboot commands are sent securely over your local network.
- Use with caution, as this feature allows rebooting remote hubs without additional verification.

Note: This feature is intended for advanced users who need to manage multiple Hubitat hubs. Always ensure you have the correct IP addresses to avoid unintended reboots.

## Usage

Once configured, CSP Watchdog will:

1. Perform health checks triggered by motion sensor activity.
2. Regularly test the responsiveness of the selected virtual switch.
3. Monitor the activity of physical Z-wave switches.
4. Conduct periodic checks on the remote hub (if configured).
5. Automatically reboot the local or remote hub if issues are detected (unless disabled).
6. Send notifications for critical events and reboot actions.

## Troubleshooting

- Enable debug logging for detailed operation information.
- Check the Hubitat logs for warnings and error messages.
- Use dev mode to test reboot functionality without actual reboots.
- If experiencing unexpected behavior, review the configuration and ensure all selected devices are functioning correctly.

## Contributing

Contributions to CSP Watchdog are welcome! Please submit pull requests or open issues on the project's GitHub repository.

## License

