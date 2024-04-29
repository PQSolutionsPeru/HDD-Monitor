import utime  # type: ignore
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from performance_monitor import check_performance
import json

# Global configuration
SSID = "PABLO-2.4G"
PASSWORD = "47009410"
MQTT_BROKER = "node02.myqtthub.com"
MQTT_PORT = 8883
MQTT_CLIENT_ID = "ESP32-PQ1"
MQTT_USER = "ESP32-1"
MQTT_PASSWORD = "esp32"
EMPRESA = "Empresa_Test"  # Name of the company

# Relay pin configuration
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {
    32: "Alarma",
    33: "Problema",
    25: "Supervision"
}

# Create instances of managers
wifi_manager = WiFiManager(SSID, PASSWORD)
mqtt_manager = MQTTManager(MQTT_BROKER, MQTT_PORT, MQTT_CLIENT_ID, MQTT_USER, MQTT_PASSWORD, wifi_manager)
relay_manager = RelayManager()

def main():
    try:
        wifi_manager.connect_wifi()  # Connect and sync time initially
        print("Debug: WiFi connected")
        mqtt_manager.connect_mqtt()  # Connect to MQTT broker
        print("Debug: MQTT connected")
        if mqtt_manager.connected():
            mqtt_manager.publish_status(f"ESP32 en la empresa: {EMPRESA}", "ESP32 en línea")
        else:
            raise Exception("Failed to connect to MQTT broker")
        # Continues with relay logic and monitoring...
        for relay_pin in RELAY_PINS:
            print(f"Debug: Setting up relay for pin {relay_pin}")
            pin = relay_manager.setup_relay(relay_pin, lambda pin, num: relay_callback(pin, num, mqtt_manager))
        while True:
            utime.sleep(60)  # Wait cycle to keep the program running
            print("Debug: Waiting cycle complete")
    except Exception as e:
        mqtt_manager.publish_status(f"ESP32 en la empresa: {EMPRESA}", f"ESP32 con error de: {str(e)}")
        print(f"Debug: Error occurred - {str(e)}")

def relay_callback(pin, pin_num, mqtt_manager):
    print(f"Debug: relay_callback triggered for pin {pin_num}")
    try:
        pin_state = pin.value()
        print(f"Debug: Retrieved pin state for pin {pin_num}: {pin_state}")
        status = "DISC" if pin_state else "OK"
    except Exception as e:
        print(f"Debug: Error retrieving pin state - {str(e)}")
        raise
    print(f"Debug: Pin status - {status}")
    message = {
        "date_time": wifi_manager.get_formatted_time(),
        "name": RELAY_NAMES.get(pin_num, "Relay no configurado"),
        "status": status
    }
    print(f"Debug: Message to be published - {message}")
    mqtt_manager.publish_event(f"EMPRESA_TEST/{MQTT_CLIENT_ID}/eventos", json.dumps(message))


if __name__ == "__main__":
    check_performance(main)
