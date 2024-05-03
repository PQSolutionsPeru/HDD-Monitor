import utime
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from performance_monitor import check_performance
import json

# Configuración global
SSID = "PABLO-2.4G"
PASSWORD = "47009410"
MQTT_BROKER = "node02.myqtthub.com"
MQTT_PORT = 8883
MQTT_CLIENT_ID = "ESP32-PQ1"
MQTT_USER = "ESP32-1"
MQTT_PASSWORD = "esp32"
EMPRESA = "Empresa_Test"

# Configuración de los pines de los relays
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {
    32: "Alarma",
    33: "Problema",
    25: "Supervision"
}

# Creación de instancias de los manejadores
wifi_manager = WiFiManager(SSID, PASSWORD)
mqtt_manager = MQTTManager(MQTT_BROKER, MQTT_PORT, MQTT_CLIENT_ID, MQTT_USER, MQTT_PASSWORD)
relay_manager = RelayManager()

def get_formatted_time():
    year, month, mday, hour, minute, second, _, _ = utime.localtime()
    return "{:04d}-{:02d}-{:02d} {:02d}:{:02d}:{:02d}".format(year, month, mday, hour, minute, second)

def relay_callback(pin, pin_num):
    status = "OK" if pin.value() else "ERROR"
    message = {
        "date_time": get_formatted_time(),
        "name": RELAY_NAMES.get(pin_num, "Unknown Relay"),
        "status": status
    }
    mqtt_manager.publish_event(f"EMPRESA_TEST/{MQTT_CLIENT_ID}/eventos", json.dumps(message))

def main():
    wifi_manager.connect_wifi()  # Conecta y sincroniza la hora inicial
    mqtt_manager.connect_mqtt()
    print("Connected to MQTT Broker!")
    for relay_pin in RELAY_PINS:
        print(f"Setting up relay for pin {relay_pin}")
        relay_manager.setup_relay(relay_pin, relay_callback)
    print("All relays setup completed, entering main loop")
    while True:
        utime.sleep(60)  # Ciclo de espera para mantener el programa en ejecución

if __name__ == "__main__":
    check_performance(main)
