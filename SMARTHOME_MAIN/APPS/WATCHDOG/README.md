Here's an updated version of the README.md that incorporates a more thorough review of the code and its functionalities:

# CSP Watchdog

## Disclaimer

<b style="color:red;">This application can reboot your Hubitat hub. Use with caution and ensure you understand the implications before enabling automatic reboots.</b>

CSP Watchdog is a comprehensive monitoring and maintenance application for Hubitat home automation systems. It provides robust health checks for both local and remote hubs, ensuring system stability and responsiveness.

## Features

1. **Local Hub Monitoring**:
   - Motion sensor-triggered health checks
   - Virtual switch response time testing
   - Physical Z-wave switch monitoring for mesh stability
   - System event monitoring (severe load, zigbee/zwave status, cloud connectivity)

2. **Remote Hub Monitoring**:
   - Regular ping checks to ensure remote hub responsiveness
   - Configurable ping intervals and reboot thresholds
   - Fallback mechanisms for remote reboot (including EzOutlet2 support)

3. **Automatic Reboot Functionality**:
   - Local hub reboot on critical events or failed tests
   - Remote hub reboot capability with multiple fallback mechanisms
   - Reboot rate limiting to prevent reboot loops

4. **Flexible Configuration**:
   - Customizable reboot thresholds for both local and remote hubs
   - Option to disable reboots for testing purposes (Dev Mode)

5. **Comprehensive Logging**:
   - Detailed debug logging for troubleshooting
   - Clear warning and error messages for critical events

6. **Notification System**:
   - Support for notification devices and speakers
   - Alerts for critical events and reboot actions

7. **Advanced Features**:
   - Management token system for secure communication between hubs
   - Support for rebooting additional remote hubs
   - Reboot history tracking and management

8. **Remote Hub Backup**:
   - Initiate backups on remote hubs directly from the app
   - Real-time status updates with auto-refresh functionality
   - Visual indicators for backup progress

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
- Configure EzOutlet2 settings for power-cycling remote hubs if necessary.

### Notifications

- Select notification devices for important alerts.
- Optionally, choose speaker devices for voice notifications.

### Reboot Controls

- Options to disable local and remote hub reboots.
- Buttons to manually initiate reboots (with confirmation).
- Configure reboot rate limiting to prevent excessive reboots.

### Additional Actions

- Run an immediate health check.
- Update the app.
- Clear reboot history.
- Enable dev mode for testing reboot functionality without actual reboots.

### Additional Remote Hub Reboots

- Enter IP addresses for additional remote Hubitat hubs you want to be able to reboot.
- Each added hub will have its own reboot button.

### Remote Hub Backup

- Enable remote hub monitoring to access backup features.
- Use the "Create a new backup on remote hub" button to initiate a backup.
- The app will display a spinner and auto-refresh every 30 seconds to show backup progress.
- Once complete, the app will display the backup status (success or failure).


## Usage

Once configured, CSP Watchdog will:

1. Continuously monitor local hub health through various methods.
2. Perform regular checks on the remote hub (if configured).
3. Automatically reboot the local or remote hub if issues are detected (unless disabled).
4. Send notifications for critical events and reboot actions.
5. Maintain a reboot history and limit reboot frequency to prevent cascading issues.

## Advanced Features

- **Management Token System**: Securely communicates between local and remote hubs.
- **Multiple Reboot Methods**: Attempts various methods to reboot remote hubs, including direct API calls and power cycling through EzOutlet2.
- **Reboot History**: Tracks recent reboots to prevent excessive rebooting in short time periods.

## Troubleshooting

- Enable debug logging for detailed operation information.
- Check the Hubitat logs for warnings and error messages.
- Use dev mode to test reboot functionality without actual reboots.
- Review the reboot history to understand system behavior over time.
- If experiencing unexpected behavior, review the configuration and ensure all selected devices are functioning correctly.

## Contributing

Contributions to CSP Watchdog are welcome! Please submit pull requests or open issues on the project's GitHub repository.

## Future Enhancements

- Implement a configuration backup/restore feature.
- Add a "safe mode" for running checks without performing reboots.
- Create a web interface for more detailed status reporting and control.
- Implement more granular logging levels for better troubleshooting.

## License

This project is licensed under the MIT License. See the LICENSE file for details.

## Disclaimer

This application is provided as-is, without any guarantees or warranty. Users are solely responsible for any damage or data loss that may occur from using this application.