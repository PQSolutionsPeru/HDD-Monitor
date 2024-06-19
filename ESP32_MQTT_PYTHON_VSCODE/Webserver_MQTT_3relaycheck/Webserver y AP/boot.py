import machine
import network
import esp
import uasyncio as asyncio
import ujson
import os

esp.osdebug(None)

async def connect_to_wifi():
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    if 'wifi_config.json' in os.listdir():
        with open('wifi_config.json') as f:
            config = ujson.load(f)
            wifi_ssid = config.get('ssid')
            wifi_pass = config.get('password')
            if wifi_ssid and wifi_pass:
                wlan.connect(wifi_ssid, wifi_pass)
                while not wlan.isconnected():
                    await asyncio.sleep(1)
    else:
        start_ap_mode()

def start_ap_mode():
    ap = network.WLAN(network.AP_IF)
    ap.active(True)
    ap.config(essid='ESP32-Setup', authmode=network.AUTH_WPA_WPA2_PSK, password='12345678')

async def main():
    await connect_to_wifi()
    from webserver import start_web_server
    await start_web_server()

asyncio.run(main())
