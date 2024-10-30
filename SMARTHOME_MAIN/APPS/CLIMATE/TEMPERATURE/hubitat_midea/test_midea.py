# script name: test_midea.py
# script written by Elfege Leylavergne

import logging
import requests
import json
from datetime import datetime
from time import time
from hashlib import sha256, md5
import hmac
from secrets import token_hex
from credentials import *

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

class MideaCloudTester:
    def __init__(self):
        self.base_url = "https://mp-prod.appsmb.com/mas/v5/app/proxy?alias="
        self.hmac_key = "PROD_VnoClJI9aikS8dyy"
        self.iot_key = "meicloud"
        
    def try_different_formats(self):
        """Try different request formats to understand what the API expects"""
        
        test_formats = [
            # Test 1: Original format
            {
                "loginAccount": MIDEA_USER,
                "appId": "1010",
                "format": 2,
                "clientType": 1,
                "language": "en_US",
                "src": "1010",
                "stamp": datetime.now().strftime("%Y%m%d%H%M%S"),
                "reqId": token_hex(16)
            },
            
            # Test 2: NetHome format (based on mobile hints)
            {
                "mobile": MIDEA_USER_MOBILE,
                "appId": "1010",
                "format": 2,
                "clientType": 1,
                "language": "en_US",
                "src": "1010",
                "stamp": datetime.now().strftime("%Y%m%d%H%M%S"),
                "reqId": token_hex(16)
            },
            
            # Test 3: Alternative format
            {
                "account": MIDEA_USER_MOBILE,
                "appId": "1010",
                "format": 2,
                "clientType": 1,
                "language": "en_US",
                "src": "1010",
                "stamp": datetime.now().strftime("%Y%m%d%H%M%S"),
                "reqId": token_hex(16)
            },
            
            # Test 4: Simplified format
            {
                "loginAccount": MIDEA_USER,
                "appId": "1010",
                "clientType": 1,
                "stamp": datetime.now().strftime("%Y%m%d%H%M%S"),
                "reqId": token_hex(16)
            }
        ]
        
        print("Testing different request formats to understand API requirements...")
        
        for i, data in enumerate(test_formats, 1):
            print(f"\n=== Test Format {i} ===")
            try:
                # Generate signature
                random = str(int(time()))
                data_str = json.dumps(data)
                msg = self.iot_key + data_str + random
                sign = hmac.new(
                    self.hmac_key.encode("ascii"),
                    msg.encode("ascii"),
                    sha256
                ).hexdigest()
                
                headers = {
                    'Content-Type': 'application/json',
                    'secretVersion': '1',
                    'random': random,
                    'sign': sign
                }
                
                url = self.base_url + "/v1/user/login/id/get"
                
                print(f"Request data: {json.dumps(data, indent=2)}")
                print(f"Sign input: {msg}")
                print(f"Generated sign: {sign}")
                
                response = requests.post(url, headers=headers, json=data)
                result = response.json()
                print(f"Response: {json.dumps(result, indent=2)}")
                
            except Exception as e:
                print(f"Error: {str(e)}")
                
            print("=" * 50)

def main():
    print("Starting Midea Cloud API Format Testing")
    tester = MideaCloudTester()
    tester.try_different_formats()

if __name__ == "__main__":
    main()