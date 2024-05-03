import network
import urequests
from machine import RTC

class WiFiManager:
    def __init__(self, ssid, password):
        self.ssid = ssid
        self.password = password
        self.wifi_connected = False
        self.time_synced = False
        self.current_time = None  # Guardar la fecha y hora actuales

    def connect_wifi(self):
        if not self.wifi_connected:
            sta_if = network.WLAN(network.STA_IF)
            if not sta_if.isconnected():
                print("Conectando a WiFi...")
                sta_if.active(True)
                sta_if.connect(self.ssid, self.password)
                while not sta_if.isconnected():
                    pass
                print("Conectado a WiFi")
                self.wifi_connected = True
                if not self.time_synced:
                    self.set_peru_time()
                    self.time_synced = True
        return self.wifi_connected

    def set_peru_time(self):
        try:
            response = urequests.get('http://worldtimeapi.org/api/timezone/America/Lima')
            if response.status_code == 200:
                data = response.json()
                self.current_time = data['datetime']
                print("Hora actualizada a la de Perú:", self.current_time)
            else:
                print("Error al obtener la hora de Perú:", response.status_code)
        except Exception as e:
            print("Error al configurar la hora de Perú:", str(e))

    def get_current_time(self):
        if self.current_time:
            return self.current_time
        else:
            print("Hora no configurada correctamente")
            return None
