import network
import utime
import urequests
import machine
import gc

class WiFiManager:
    def __init__(self):
        """Inicializa el gestor WiFi con parámetros optimizados"""
        self.SSID = None
        self.PASSWORD = None
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)
        
        # Parámetros de conexión
        self.MAX_RETRIES = 3
        self.CONNECT_TIMEOUT = 20  # segundos
        self.CHECK_INTERVAL = 10000  # 10 segundos
        self.last_check = utime.ticks_ms()
        
        # Control de reconexión
        self.reconnect_count = 0
        self.last_connect_attempt = 0
        self.RECONNECT_WAIT = 5000  # 5 segundos entre intentos
        
        print("[WIFI] Gestor WiFi iniciado")

    def connect_wifi(self, ssid=None, password=None):
        """Conecta al WiFi con manejo robusto de errores"""
        try:
            if ssid:
                self.SSID = ssid
            if password:
                self.PASSWORD = password

            if not self.SSID or not self.PASSWORD:
                raise ValueError("[WIFI] SSID y PASSWORD requeridos")

            # Evitar intentos muy frecuentes
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_connect_attempt) < self.RECONNECT_WAIT:
                return False

            self.last_connect_attempt = current_time
            
            if self.sta_if.isconnected():
                return True

            print(f"[WIFI] Conectando a: {self.SSID}")
            gc.collect()  # Limpiar memoria antes de conectar

            # Desconectar si hay conexión previa
            try:
                self.sta_if.disconnect()
                utime.sleep_ms(100)
            except:
                pass

            # Intentar conexión
            self.sta_if.connect(self.SSID, self.PASSWORD)
            
            # Esperar conexión con timeout
            start = utime.time()
            while not self.sta_if.isconnected():
                if utime.time() - start > self.CONNECT_TIMEOUT:
                    print("[WIFI] Timeout de conexión")
                    return False
                utime.sleep_ms(100)
                machine.idle()  # Ahorro de energía mientras espera

            print(f"[WIFI] Conectado. IP: {self.sta_if.ifconfig()[0]}")
            self.reconnect_count = 0
            return True

        except Exception as e:
            print(f"[WIFI] Error en connect_wifi: {e}")
            self.reconnect_count += 1
            return False

    def ensure_connected(self):
        """Asegura que hay una conexión WiFi activa"""
        if not self.sta_if.isconnected():
            if self.reconnect_count < self.MAX_RETRIES:
                return self.connect_wifi()
            return False
        return True

    def get_status(self):
        """Obtiene el estado detallado de la conexión"""
        try:
            status = {
                'connected': self.sta_if.isconnected(),
                'active': self.sta_if.active(),
                'rssi': self.get_signal_strength(),
                'ip': self.sta_if.ifconfig()[0] if self.sta_if.isconnected() else None,
                'reconnect_count': self.reconnect_count,
                'last_connect': self.last_connect_attempt
            }
            return status
        except:
            return {'connected': False, 'error': True}

    def get_signal_strength(self):
        """Obtiene la intensidad de la señal WiFi"""
        try:
            if self.sta_if.isconnected():
                return self.sta_if.status('rssi')
            return None
        except:
            return None

    def check_connection(self):
        """Verifica periódicamente la conexión"""
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_check) >= self.CHECK_INTERVAL:
            self.last_check = current_time
            return self.ensure_connected()
        return self.sta_if.isconnected()

    def disconnect(self):
        """Desconecta de manera segura"""
        try:
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
                while self.sta_if.isconnected():
                    utime.sleep_ms(100)
            self.sta_if.active(False)
        except Exception as e:
            print(f"[WIFI] Error en disconnect: {e}")