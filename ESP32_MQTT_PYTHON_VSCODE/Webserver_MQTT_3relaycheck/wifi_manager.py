import network # type: ignore
import ntptime # type: ignore
import utime # type: ignore

class WiFiManager:
    def __init__(self, ssid, password):
        self.ssid = ssid
        self.password = password
        self.sta_if = network.WLAN(network.STA_IF)
        self.max_retries = 50
        self.retry_interval = 1800

    def connect_wifi(self):
        if not self.sta_if.isconnected():
            print("Activando interfaz WiFi...")
            self.sta_if.active(True)
            print("Conectando a WiFi...")
            self.sta_if.connect(self.ssid, self.password)

            attempt_count = 0
            while not self.sta_if.isconnected() and attempt_count < self.max_retries:
                utime.sleep(3)
                attempt_count += 1
                print(f"Reintentando conexión... Intento {attempt_count}")

            if self.sta_if.isconnected():
                print("Conectado a WiFi")
                self.sync_time()
            else:
                print("No se pudo conectar a WiFi después de varios intentos.")
                self.sta_if.active(False)  # Desactiva la interfaz para ahorrar energía
                self.schedule_reconnect()

    def sync_time(self):
        try:
            ntptime.settime()
            print("Hora sincronizada con NTP.")
        except Exception as e:
            print("Error al sincronizar hora:", str(e))

    def disconnect(self):
        if self.sta_if.isconnected():
            print("Desconectando de WiFi...")
            self.sta_if.disconnect()
            self.sta_if.active(False)
            print("Desconectado.")

    def schedule_reconnect(self):
        print(f"Se programará un reintento en {self.retry_interval/60} minutos.")
        while not self.sta_if.isconnected():
            utime.sleep(self.retry_interval)
            print("Intentando reconectar a WiFi...")
            self.connect_wifi()

    def get_formatted_time(self):
        year, month, mday, hour, minute, second, _, _ = utime.localtime()
        return f"{year:04d}-{month:02d}-{mday:02d} {hour:02d}:{minute:02d}:{second:02d}"
