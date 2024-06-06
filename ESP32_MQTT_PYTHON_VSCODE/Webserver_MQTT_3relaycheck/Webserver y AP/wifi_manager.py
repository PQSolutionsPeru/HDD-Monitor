import machine
import utime
import network
import urequests
import esp32
import os

class WiFiManager:
    def __init__(self):
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)
        self.ap_if = network.WLAN(network.AP_IF)
        self.ap_if.active(False)
        self.nvs = esp32.NVS('wifi_creds')

    def conectar_wifi(self):
        ssid, password = self.obtener_credenciales_wifi()
        while not self.sta_if.isconnected():
            print("Conectando a WiFi...")
            self.sta_if.connect(ssid, password)
            start_time = utime.ticks_ms()
            while not self.sta_if.isconnected() and utime.ticks_diff(utime.ticks_ms(), start_time) < 10000:
                utime.sleep(1)
            if self.sta_if.isconnected():
                print("Conectado a WiFi.")
            else:
                print("Reintentando conectar a WiFi...")
                utime.sleep(5)

    def obtener_credenciales_wifi(self):
        ssid = self.nvs.get('ssid')
        password = self.nvs.get('password')
        return ssid, password

    def guardar_credenciales_wifi(self, ssid, password):
        self.nvs.set('ssid', ssid)
        self.nvs.set('password', password)
        self.nvs.commit()

    def asegurar_conexion_wifi(self):
        if not self.sta_if.isconnected():
            print("WiFi desconectado, intentando reconectar...")
            self.conectar_wifi()

    def obtener_hora_actual(self):
        self.asegurar_conexion_wifi()
        current_time = self._obtener_hora_mundial()
        return current_time

    def _obtener_hora_mundial(self):
        try:
            response = urequests.get("http://worldtimeapi.org/api/timezone/America/Lima")
            data = response.json()
            current_datetime = data["datetime"]
            response.close()
            return self._formatear_fecha_hora(current_datetime)
        except Exception as e:
            print("Error al obtener la fecha y hora:", e)
            return None

    def _formatear_fecha_hora(self, datetime_str):
        year, month, day = datetime_str[:10].split("-")
        time_str = datetime_str[11:19]
        return f"el {day}-{month}-{year} a las {time_str}"

    def iniciar_ap(self):
        self.ap_if.active(True)
        self.ap_if.config(essid='ESP32-AP', password='12345678')
        print('Configuración del Access Point:', self.ap_if.ifconfig())
        return self.ap_if
