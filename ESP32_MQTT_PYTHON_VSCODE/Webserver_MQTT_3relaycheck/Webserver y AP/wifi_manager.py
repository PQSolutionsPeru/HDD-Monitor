import machine
import utime
import network
import urequests
import json
from umqtt.robust import MQTTClient

class WiFiManager:
    SSID = None
    PASSWORD = None
    IP = None
    NETMASK = None
    GATEWAY = None
    WIFI_CONFIG_FILE = "/wifi_config.json"  # Archivo para almacenar las credenciales Wi-Fi

    def __init__(self):
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)
        self.ap_if = network.WLAN(network.AP_IF)
        self.ap_if.active(False)
        self.cargar_credenciales_wifi()

    def conectar_wifi(self):
        if self.SSID and self.PASSWORD:
            print(f"Conectando a la red Wi-Fi {self.SSID}...")
            if self.IP and self.NETMASK and self.GATEWAY:
                print(f"Configurando IP estática: IP={self.IP}, NETMASK={self.NETMASK}, GATEWAY={self.GATEWAY}")
                self.sta_if.ifconfig((self.IP, self.NETMASK, self.GATEWAY, self.GATEWAY))
            self.sta_if.connect(self.SSID, self.PASSWORD)
            for _ in range(10):
                if self.sta_if.isconnected():
                    print("Conectado a Wi-Fi")
                    ip_address = self.obtener_direccion_ip()
                    print(f"Dirección IP: {ip_address}")
                    self.publicar_ip(ip_address)
                    break
                utime.sleep(1)
        else:
            print("No hay credenciales WiFi disponibles.")

    def cargar_credenciales_wifi(self):
        try:
            with open(self.WIFI_CONFIG_FILE, "r") as f:
                config = json.load(f)
                self.SSID = config.get("ssid")
                self.PASSWORD = config.get("password")
                self.IP = config.get("ip")
                self.NETMASK = config.get("netmask")
                self.GATEWAY = config.get("gateway")
                print(f"Credenciales WiFi cargadas: SSID={self.SSID}, PASSWORD={self.PASSWORD}, IP={self.IP}, NETMASK={self.NETMASK}, GATEWAY={self.GATEWAY}")
        except OSError:
            print("No se encontraron credenciales WiFi guardadas.")

    def guardar_credenciales_wifi(self, ssid, password, ip=None, netmask=None, gateway=None):
        print(f"Guardando credenciales WiFi: SSID={ssid}, PASSWORD={password}, IP={ip}, NETMASK={netmask}, GATEWAY={gateway}")
        self.SSID = ssid
        self.PASSWORD = password
        self.IP = ip
        self.NETMASK = netmask
        self.GATEWAY = gateway
        try:
            with open(self.WIFI_CONFIG_FILE, "w") as f:
                json.dump({"ssid": ssid, "password": password, "ip": ip, "netmask": netmask, "gateway": gateway}, f)
            print(f"Credenciales WiFi guardadas: SSID={ssid}, PASSWORD={password}, IP={ip}, NETMASK={netmask}, GATEWAY={gateway}")
        except Exception as e:
            print(f"Error al guardar credenciales WiFi: {e}")

    def asegurar_conexion_wifi(self):
        retry_count = 0
        while not self.sta_if.isconnected() and retry_count < 5:
            print("Intentando conectar WiFi...")
            self.conectar_wifi()
            retry_count += 1
            utime.sleep(5)
        if not self.sta_if.isconnected():
            print("No se pudo conectar a WiFi después de varios intentos. Reiniciando...")
            machine.reset()

    def obtener_hora_actual(self):
        self.asegurar_conexion_wifi()
        try:
            response = urequests.get("http://worldtimeapi.org/api/timezone/America/Lima")
            data = response.json()
            response.close()
            return data["datetime"]
        except Exception as e:
            print("Error al obtener la fecha y hora:", e)
            return None

    def iniciar_ap(self):
        self.ap_if.active(True)
        # Configurar IP estática
        ip = '192.168.168.192'
        netmask = '255.255.255.0'
        gateway = '192.168.168.192'
        self.ap_if.ifconfig((ip, netmask, gateway, gateway))
        self.ap_if.config(essid='HDD-Monitor', password='1234556')
        print('Configuración del Access Point:', self.ap_if.ifconfig())
        return self.ap_if

    def iniciar(self):
        self.cargar_credenciales_wifi()
        if self.SSID and self.PASSWORD:
            self.conectar_wifi()
        else:
            self.iniciar_ap()

    def obtener_direccion_ip(self):
        return self.sta_if.ifconfig()[0]

    def publicar_ip(self, ip):
        try:
            mqtt_client = MQTTClient("ESP32-PQ1", "node02.myqtthub.com", port=8883, user="ESP32-1", password="esp32", ssl=True)
            mqtt_client.connect()
            message = {"date_time": self.obtener_hora_actual(), "name": "ESP32 IP", "status": ip}
            mqtt_client.publish("EMPRESA_TEST/ESP32-PQ1/eventos", json.dumps(message))
            mqtt_client.disconnect()
            print(f"IP publicada al broker MQTT: {ip}")
        except Exception as e:
            print(f"Error al publicar IP al broker MQTT: {e}")
