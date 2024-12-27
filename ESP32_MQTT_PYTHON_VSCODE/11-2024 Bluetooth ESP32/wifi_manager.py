import network
import utime
import machine
import gc
import json

class WiFiManager:
    def __init__(self):
        """Inicializa el gestor WiFi"""
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)
        
        # Configuración WiFi
        self.ssid = None
        self.password = None
        self.WIFI_CONFIG_FILE = "wifi_config.json"
        
        # Parámetros de conexión
        self.CONNECT_TIMEOUT = 60    # 60 segundos timeout conexión
        self.MAX_RETRIES = 5        # 5 intentos máximo
        self.RETRY_DELAY = 15000     # 15 segundos entre intentos
        self.CHECK_INTERVAL = 60000  # 60 segundos entre verificaciones
        
        self.last_check = utime.ticks_ms()
        self.last_error = None
        self.current_ip = None
        
        print("[WIFI] Gestor WiFi iniciado")

    def connect_wifi(self, ssid, password):
        """Conecta al WiFi con reintentos"""
        try:
            if not ssid or not password:
                self.last_error = "credenciales_invalidas"
                return False

            print(f"[WIFI] Conectando a: {ssid}")
            gc.collect()

            # Desconectar si hay conexión previa
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
                utime.sleep_ms(100)

            self.sta_if.connect(ssid, password)
            
            # Esperar conexión con timeout
            retry_count = 0
            while retry_count < self.MAX_RETRIES:
                start_time = utime.time()
                
                while not self.sta_if.isconnected():
                    if utime.time() - start_time > self.CONNECT_TIMEOUT:
                        break
                        
                    status = self.sta_if.status()
                    if status == network.STAT_WRONG_PASSWORD:
                        self.last_error = "password_incorrecto"
                        return False
                    elif status == network.STAT_NO_AP_FOUND:
                        self.last_error = "red_no_encontrada"
                        break
                    
                    utime.sleep_ms(100)
                
                if self.sta_if.isconnected():
                    self.ssid = ssid
                    self.password = password
                    self.current_ip = self.sta_if.ifconfig()[0]
                    self.last_error = None
                    self._save_config()
                    print(f"[WIFI] Conectado - IP: {self.current_ip}")
                    return True
                
                retry_count += 1
                if retry_count < self.MAX_RETRIES:
                    print(f"[WIFI] Reintento {retry_count}")
                    utime.sleep_ms(self.RETRY_DELAY)

            self.last_error = "timeout_conexion"
            return False

        except Exception as e:
            print(f"[WIFI] Error conectando: {e}")
            self.last_error = str(e)
            return False

    def check_connection(self):
        """Verifica y mantiene la conexión WiFi"""
        try:
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_check) >= self.CHECK_INTERVAL:
                self.last_check = current_time
                
                if not self.sta_if.isconnected():
                    print("[WIFI] Conexión perdida, reconectando...")
                    if self.ssid and self.password:
                        return self.connect_wifi(self.ssid, self.password)
                    return False
                    
                # Actualizar IP actual
                self.current_ip = self.sta_if.ifconfig()[0]
                return True
                
            return self.sta_if.isconnected()
            
        except Exception as e:
            print(f"[WIFI] Error verificando conexión: {e}")
            return False

    def get_ip_address(self):
        """Obtiene dirección IP actual"""
        try:
            if self.sta_if.isconnected():
                self.current_ip = self.sta_if.ifconfig()[0]
                return self.current_ip
            return None
        except:
            return None

    def load_config(self):
        """Carga configuración WiFi guardada"""
        try:
            with open(self.WIFI_CONFIG_FILE, 'r') as f:
                config = json.load(f)
                if config.get('ssid') and config.get('password'):
                    return self.connect_wifi(config['ssid'], config['password'])
                    
            return False
            
        except:
            return False

    def _save_config(self):
        """Guarda configuración WiFi"""
        try:
            config = {
                'ssid': self.ssid,
                'password': self.password
            }
            
            with open(self.WIFI_CONFIG_FILE, 'w') as f:
                json.dump(config, f)
                
        except Exception as e:
            print(f"[WIFI] Error guardando config: {e}")

    def disconnect(self):
        """Desconecta del WiFi"""
        try:
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
            self.sta_if.active(False)
        except:
            pass

    def get_error(self):
        """Obtiene último error"""
        return self.last_error