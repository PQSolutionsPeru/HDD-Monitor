import utime
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from performance_monitor import check_performance

# Configuración de red WiFi
SSID = "PABLO-2.4G"
PASSWORD = "47009410"

# Configuración del broker MQTT
MQTT_BROKER = "node02.myqtthub.com"
MQTT_PORT = 8883
MQTT_CLIENT_ID = "ESP32-PQ1"
MQTT_USER = "ESP32-1"
MQTT_PASSWORD = "esp32"

# Configuración de los pines de los relés
RELAY_PINS = [32, 33, 25]

# Crear instancias de los gestores
wifi_manager = WiFiManager(SSID, PASSWORD)
mqtt_manager = MQTTManager(MQTT_BROKER, MQTT_PORT, MQTT_CLIENT_ID, MQTT_USER, MQTT_PASSWORD)
relay_manager = RelayManager()

# Configurar y agregar callbacks para los relés
def relay_callback(pin, pin_num):
    event_type = f"{pin_num}_activado" if pin.value() else f"{pin_num}_desactivado"
    mqtt_manager.publish_event(f"EMPRESA_TEST/{MQTT_CLIENT_ID}/eventos", event_type)

def main():
    wifi_manager.connect_wifi()  # Conecta y sincroniza la hora inicial
    for relay_pin in RELAY_PINS:
        pin = relay_manager.setup_relay(relay_pin, relay_callback)
    while True:
        utime.sleep(60)  # Ciclo de espera para mantener el programa en ejecución

if __name__ == "__main__":
    check_performance(main)
