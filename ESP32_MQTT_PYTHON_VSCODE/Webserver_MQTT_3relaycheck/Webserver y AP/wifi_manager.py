# wifi_manager.py
import machine
import network
import utime
import urequests
import uos

class WiFiManager:
    AP_SSID = "ESP32-AP"
    AP_PASSWORD = "12345678"
    AP_IP = "192.168.4.1"
    AP_SUBNET = "255.255.255.0"
    AP_GATEWAY = "192.168.4.1"

    def __init__(self):
        self.sta_if = network.WLAN(network.STA_IF)
        self.ap_if = network.WLAN(network.AP_IF)
        self.sta_if.active(True)
        self.ap_if.active(False)
        self.SSID = None
        self.PASSWORD = None
        self.load_credentials()

    def load_credentials(self):
        try:
            with open('wifi_credentials.json', 'r') as f:
                creds = ujson.load(f)
                self.SSID = creds.get('ssid')
                self.PASSWORD = creds.get('password')
        except (OSError, ValueError):
            print("No se pudieron cargar las credenciales WiFi.")

    def save_credentials(self, ssid, password):
        self.SSID = ssid
        self.PASSWORD = password
        with open('wifi_credentials.json', 'w') as f:
            ujson.dump({'ssid': ssid, 'password': password}, f)

    def connect_wifi(self):
        if not self.SSID or not self.PASSWORD:
            self.start_ap_mode()
            return

        self.ap_if.active(False)
        self.sta_if.active(True)
        print("Conectando a WiFi...")
        self.sta_if.connect(self.SSID, self.PASSWORD)
        start_time = utime.ticks_ms()
        while not self.sta_if.isconnected() and utime.ticks_diff(utime.ticks_ms(), start_time) < 10000:
            utime.sleep(1)
        if self.sta_if.isconnected():
            print("Conectado a WiFi con IP:", self.sta_if.ifconfig()[0])
        else:
            print("No se pudo conectar a WiFi, iniciando modo AP...")
            self.start_ap_mode()

    def start_ap_mode(self):
        print("Iniciando modo AP...")
        self.ap_if.active(True)
        self.ap_if.config(essid=self.AP_SSID, password=self.AP_PASSWORD)
        self.ap_if.ifconfig((self.AP_IP, self.AP_SUBNET, self.AP_GATEWAY, self.AP_GATEWAY))
        print(f"AP iniciado con SSID: {self.AP_SSID}, IP: {self.AP_IP}")

    def ensure_wifi_connected(self):
        if not self.sta_if.isconnected():
            print("WiFi desconectado, intentando reconectar...")
            self.connect_wifi()

    def check_connection(self):
        self.ensure_wifi_connected()

    def get_current_time(self):
        self.check_connection()
        current_time = self._get_world_time()
        return current_time

    def _get_world_time(self):
        try:
            response = urequests.get("http://worldtimeapi.org/api/timezone/America/Lima")
            data = response.json()
            current_datetime = data["datetime"]
            response.close()
            return self._format_datetime(current_datetime)
        except Exception as e:
            print("Error al obtener la fecha y hora:", e)
            return None

    def _format_datetime(self, datetime_str):
        # Formatear la fecha y hora según los requisitos
        year, month, day = datetime_str[:10].split("-")
        time_str = datetime_str[11:19]
        return f"el {day}-{month}-{year} a las {time_str}"
