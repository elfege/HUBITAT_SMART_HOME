# **Tutorial: Discovering and Controlling Midea Devices with msmart**

## **Step 1: Install Python and msmart**

1. **Install Python (if not already installed)**:
   - Download and install Python from [python.org](https://www.python.org/downloads/).
   - Make sure to check the option to **add Python to your PATH** during installation.

2. **Install msmart**:
   Open your command prompt or terminal and install the `msmart` package via pip:

   ```bash
   pip install msmart
   ```

## **Step 2: Discover Midea Devices on the Local Network**

To find Midea air conditioning devices on your network, use the `midea-discover` tool, which sends out a broadcast message to detect devices.

## **For Windows**

1. Open the command prompt and navigate to the directory where you installed `msmart` (or where your project is located if applicable).

2. Run the discovery command:

   ```bash
   python -m msmart.cli discover
   ```

   If the devices are correctly set up on the same subnet, the tool should return their details, including their IP addresses, device IDs, and more.

## **For WSL**

In WSL, network traffic, especially broadcast traffic, may not behave the same way as in native Windows. To avoid these issues, follow these steps:

1. **Run the discovery command in Windows first**:
   Since broadcast traffic may not work as expected in WSL, use your native Windows environment to run the `midea-discover` tool:

   ```bash
   python -m msmart.cli discover
   ```

2. **Manually configure devices in WSL**:
   After running the discovery in Windows and retrieving the device details (such as IP, device ID, token, and key), you can then pass these values manually into your WSL environment or Hubitat.

## **Step 3: Configure Devices in Hubitat (or Other Controllers)**

Once you have the required details (IP, device ID, token, key), you can use these in your smart home platform for direct control.

1. Input the device information (IP address, device ID, token, and key) into your Hubitat or local controller configuration.

## **Step 4: Automating the Process**

1. **Write a script** to automate the discovery and configuration for future use. Below is an example of a Python script for repeating the discovery process:

   ```python
   import subprocess

   def discover_midea_devices():
       result = subprocess.run(['python', '-m', 'msmart.cli', 'discover'], capture_output=True, text=True)
       if result.returncode == 0:
           print("Device discovered successfully:")
           print(result.stdout)
       else:
           print("Error during discovery:")
           print(result.stderr)

   if __name__ == "__main__":
       discover_midea_devices()
   ```

   You can save this script and run it whenever you need to rediscover devices.

## **Step 5: Troubleshooting**

If devices are not discovered:

- Ensure the devices and your computer are on the same subnet.
- Try disabling any firewalls or security settings that may be blocking the discovery.
- If using WSL, confirm broadcast traffic is supported, or revert to using Windows for discovery.

---

## **Key Commands Recap**

- **Install msmart**: `pip install msmart`
- **Discover devices**: `python -m msmart.cli discover`
- **Manually input the device details in Hubitat after discovery**.

By following these steps, you should be able to discover and control your Midea devices reliably using `msmart`.
