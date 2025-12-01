# script name: elfege_msmart.py
# script written by Elfege Leylavergne

import asyncio
import logging
from msmart.device import air_conditioning
from msmart.lan import lan
from msmart.security import security, get_udpid
from msmart.cloud import cloud
from credentials import *

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

async def test_connection():
    print(f"\nTesting connection to Midea AC (V3 Protocol):")
    print(f"IP: {DEVICE_IP}")
    print(f"Port: {DEVICE_PORT}")
    print(f"ID: {DEVICE_ID}")
    
    try:
        # Créer l'instance cloud pour obtenir un vrai token/key
        print("\nAttempting cloud login...")
        cloud_client = cloud(MIDEA_USER, MIDEA_PASSWORD)
        cloud_client.login()
        
        # Calculer l'UDPID
        device_id_bytes = DEVICE_ID.to_bytes(6, 'little')
        udpid = get_udpid(device_id_bytes)
        print(f"Calculated UDPID: {udpid}")
        
        # Obtenir token et key du cloud
        print("Getting token/key from cloud...")
        token, key = cloud_client.gettoken(udpid)
        
        if not token or not key:
            raise Exception("Failed to get token/key from cloud")
            
        print(f"Got token: {token}")
        print(f"Got key: {key}")
        
        # Créer une connexion avec le protocole V3
        connection = lan(DEVICE_IP, DEVICE_ID, DEVICE_PORT)
        
        # Configurer l'authentification
        print("\nAttempting V3 authentication...")
        token_bytes = bytearray.fromhex(token)
        key_bytes = bytearray.fromhex(key)
        
        auth_result = connection.authenticate(token_bytes, key_bytes)
        print(f"Authentication result: {auth_result}")
        
        if auth_result:
            print("\nAuthentication successful! Attempting to get device status...")
            query_status = bytearray([
                0x83, 0x70,  # Header V3
                0x00, 0x10,  # Length
                0x20, 0x01,  # Message type (query)
                0x00, 0x00,  # Sequence number
                0xac        # Device type (AC)
            ])
            
            response = await connection.appliance_transparent_send_8370(query_status)
            
            if response:
                print("\nReceived device status!")
                for packet in response:
                    print(f"Status packet: {packet.hex()}")
                    try:
                        decrypted = security().aes_decrypt(packet)
                        print(f"Decrypted: {decrypted.hex()}")
                    except Exception as e:
                        print(f"Could not decrypt packet: {e}")
            else:
                print("\nNo status response received")
        else:
            print("\nAuthentication failed")
            
    except Exception as e:
        print(f"\nError occurred:")
        print(f"Type: {type(e).__name__}")
        print(f"Message: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    if asyncio.get_event_loop().is_closed():
        asyncio.set_event_loop(asyncio.ProactorEventLoop())
    
    loop = asyncio.get_event_loop()
    try:
        loop.run_until_complete(asyncio.wait_for(test_connection(), timeout=20))
    except asyncio.TimeoutError:
        print("\nConnection timed out after 20 seconds")
    finally:
        loop.close()