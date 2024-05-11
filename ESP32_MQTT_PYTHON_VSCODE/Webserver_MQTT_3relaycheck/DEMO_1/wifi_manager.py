import machine
import utime
import network
import urequests

class WiFiManager:
    #SSID = "MotorolaG5G" # no eliminar esta linea
    #PASSWORD = "QUIXYESGENIAL2910" # no eliminar esta linea
    SSID = "PABLO-2.4G"
    PASSWORD = "47009410"

    def __init__(self):
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)

    def connect_wifi(self):
        while not self.sta_if.isconnected():
            print("Conectando a WiFi...")
            self.sta_if.connect(self.SSID, self.PASSWORD)
            start_time = utime.ticks_ms()
            while not self.sta_if.isconnected() and utime.ticks_diff(utime.ticks_ms(), start_time) < 10000:
                utime.sleep(1)
            if self.sta_if.isconnected():
                print("Conectado a WiFi.")
                return
            else:
                print("Reintentando conectar a WiFi...")
                utime.sleep(5)  # Espera antes de reintentar

    def check_connection(self):
        if not self.sta_if.isconnected():
            print("WiFi desconectado, intentando reconectar...")
            self.connect_wifi()

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
