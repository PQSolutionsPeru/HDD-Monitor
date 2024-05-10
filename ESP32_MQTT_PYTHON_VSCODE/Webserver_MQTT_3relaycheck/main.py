import urequests
import utime
import json
import _thread
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from machine import Timer
import gc

# Configuration
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {32: "Alarma", 33: "Problema", 25: "Supervision"}

# Managers
wifi_manager = WiFiManager()
mqtt_manager = MQTTManager()
relay_manager = RelayManager()
watchdog_manager = WatchdogManager()

# Memory Management
memory_timer = Timer(-1)

def memory_watchdog(timer):
    gc.collect()
    free_memory = gc.mem_free()
    print(f"Memoria libre actual: {free_memory} bytes")
    if free_memory < 50000:
        print("Posible fuga de memoria detectada.")

memory_timer.init(period=60000, mode=Timer.PERIODIC, callback=memory_watchdog)

def network_thread():
    while True:
        print("Checking WiFi connection...")
        wifi_manager.check_connection()
        utime.sleep(5)

def get_current_datetime():
    try:
        print("Fetching current datetime from worldtimeapi.org...")
        response = urequests.get("http://worldtimeapi.org/api/timezone/America/Lima")
        data = response.json()
        current_datetime = data["datetime"]
        response.close()
        print(f"Current datetime fetched successfully: {current_datetime}")
        return current_datetime
    except Exception as e:
        print(f"Error al obtener la fecha y hora: {e}")
        return None

def relay_callback(pin, pin_num):
    print(f"Relay callback triggered for pin {pin_num}.")
    current_datetime = get_current_datetime()
    if current_datetime:
        message = {
            "date_time": current_datetime[:19],
            "name": RELAY_NAMES.get(pin_num, "Unknown Relay"),
            "status": "DISC" if pin.value() else "OK"
        }
        print(f"Preparing to send MQTT message: {message}")
        mqtt_manager.publish_event(f"EMPRESA_TEST/{mqtt_manager.MQTT_CLIENT_ID}/eventos", json.dumps(message))

def main():
    _thread.start_new_thread(network_thread, ())
    wifi_manager.connect_wifi()
    mqtt_manager.ensure_client()
    for relay_pin in RELAY_PINS:
        pin = relay_manager.setup_relay(relay_pin, relay_callback)
    last_feed_time = utime.ticks_ms()
    while True:
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, last_feed_time) > 5000:
            watchdog_manager.feed()
            print("Watchdog fed.")
            last_feed_time = current_time
        utime.sleep(1)

if __name__ == "__main__":
    main()
