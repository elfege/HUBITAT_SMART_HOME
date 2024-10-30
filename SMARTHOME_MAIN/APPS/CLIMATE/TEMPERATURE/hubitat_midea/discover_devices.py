#!/usr/bin/env python3

"""
Script created by Elfege Leylavergne to automate and streamline the discovery process

This is meant for local discovery only. It does not use any cloud connection. 
"""

import subprocess
import json
import re
from typing import List, Dict
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def discover_midea_devices() -> List[Dict]:
    """
    Run msmart discovery and parse the output to find Midea devices.
    Returns a list of dictionaries containing device information.
    """
    try:
        # Run the msmart discover command
        result = subprocess.run(
            ['python', '-m', 'msmart.cli', 'discover'],
            capture_output=True,
            text=True
        )
        
        # Log the raw output for debugging
        logger.debug(f"stdout: {result.stdout}")
        logger.debug(f"stderr: {result.stderr}")

        # Combine stdout and stderr for parsing since some implementations
        # output to different streams
        output = result.stdout + result.stderr

        # Extract device information using regex
        # This pattern looks for content between 'Found device:' and closing brace
        devices = []
        device_matches = re.finditer(
            r"Found device:\s*{([^}]+)}", 
            output, 
            re.MULTILINE | re.DOTALL
        )

        for match in device_matches:
            # Extract the device info string and clean it up
            device_str = match.group(1).strip()
            
            # Convert the string into a proper dictionary
            # Split by comma and handle each key-value pair
            device_dict = {}
            for pair in device_str.split(','):
                if ':' in pair:
                    key, value = pair.split(':', 1)
                    key = key.strip().strip("'")
                    value = value.strip().strip("'")
                    device_dict[key] = value

            if device_dict:  # Only add if we got valid data
                devices.append(device_dict)

        # Save to JSON file for reference
        with open('midea_devices.json', 'w') as f:
            json.dump(devices, f, indent=2)
            logger.info("Device information saved to midea_devices.json")

        return devices

    except subprocess.CalledProcessError as e:
        logger.error(f"Error running msmart discover: {e}")
        raise
    except Exception as e:
        logger.error(f"Error processing discovery results: {e}")
        raise

def main():
    """Main function to run discovery and display results."""
    try:
        devices = discover_midea_devices()
        if devices:
            print("\nDiscovered Midea Devices:")
            print(json.dumps(devices, indent=2))
            print(f"\nFound {len(devices)} device(s)")
        else:
            print("No Midea devices found")
    except Exception as e:
        print(f"Discovery failed: {e}")

if __name__ == "__main__":
    main()