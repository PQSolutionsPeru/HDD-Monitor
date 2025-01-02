import network
import utime
import machine
import gc
import json
import os

class WiFiManager:
    _instance = None
    _initialized = False

    def __new__(cls):
        if cls._instance is None:
            print("[WIFI] Creando instancia única de WiFiManager")
            cls._instance = super(WiFiManager, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if WiFiManager._initialized:
            return
            
        try:
            print("[WIFI] Inicializando WiFiManager por primera vez")
            WiFiManager._initialized = True
            
            # Limpiar memoria antes de inicializar
            gc.collect()
            utime.sleep_ms(1000)
            
            # Asegurar que cualquier instancia previa está limpia
            network.WLAN(network.STA_IF).active(False)
            utime.sleep_ms(1000)
            
            # Crear interfaz pero mantenerla desactivada inicialmente
            self.sta_if = None
            
            # Configuración WiFi
            self.ssid = None
            self.password = None
            self.WIFI_CONFIG_FILE = "wifi_config.json"
            
            # Parámetros de conexión
            self.CONNECT_TIMEOUT = 20000  # 20 segundos timeout por intento
            self.MAX_RETRIES = 3         # 3 intentos máximo por ciclo
            self.RETRY_DELAY = 2000      # 2 segundos entre intentos
            self.CHECK_INTERVAL = 60000   # 60 segundos entre verificaciones
            
            self.last_check = utime.ticks_ms()
            self.last_error = None
            self.current_ip = None
            
            # Inicializar la interfaz de manera segura
            if not self._safe_init_interface():
                raise Exception("No se pudo inicializar la interfaz WiFi")
                
            print("[WIFI] WiFiManager inicializado correctamente")
            
        except Exception as e:
            print(f"[WIFI] Error en inicialización: {e}")
            WiFiManager._initialized = False
            machine.reset()

    def _clean_wifi_state(self):
        """Limpia el estado del WiFi"""
        try:
            # Desactivar cualquier instancia de WiFi existente
            try:
                wlan = network.WLAN(network.STA_IF)
                if wlan:
                    wlan.active(False)
                    utime.sleep_ms(500)
                del wlan
            except:
                pass
            
            # Forzar limpieza de memoria
            gc.collect()
            utime.sleep_ms(500)
            
            return True
        except:
            return False

    def _safe_init_interface(self):
        """Inicializa la interfaz WiFi de manera segura con reintentos"""
        for attempt in range(3):  # 3 intentos máximo
            try:
                print(f"[WIFI] Intento de inicialización {attempt + 1}/3")
                
                # Limpiar estado previo
                self._clean_wifi_state()
                gc.collect()
                utime.sleep_ms(1000)
                
                # Crear nueva interfaz
                self.sta_if = network.WLAN(network.STA_IF)
                if not self.sta_if:
                    print("[WIFI] Error: No se pudo crear la interfaz")
                    continue
                
                # Desactivar primero
                self.sta_if.active(False)
                utime.sleep_ms(500)
                
                # Configurar antes de activar
                try:
                    self.sta_if.config(reconnects=5)
                    self.sta_if.config(txpower=20)
                except Exception as e:
                    print(f"[WIFI] Advertencia en config básica: {e}")
                
                # Configuración de buffer por etapas
                buffer_configs = [
                    {"rxbuf": 512, "txbuf": 512},
                    {"rxbuf": 256, "txbuf": 256},
                    {"rxbuf": 128, "txbuf": 128}
                ]
                
                for config in buffer_configs:
                    try:
                        print(f"[WIFI] Probando configuración de buffer: {config}")
                        if hasattr(self.sta_if, 'config'):
                            self.sta_if.config(**config)
                            utime.sleep_ms(100)
                        break
                    except:
                        continue
                
                # Verificar que la interfaz quedó en buen estado
                if self.sta_if and hasattr(self.sta_if, 'active'):
                    return True
                    
            except Exception as e:
                print(f"[WIFI] Error en intento {attempt + 1}: {e}")
                gc.collect()
                utime.sleep_ms(1000)
                
                # Limpiar para el siguiente intento
                self._clean_wifi_state()
                self.sta_if = None
        
        return False  # Si todos los intentos fallan

    def check_connection(self):
        """Verifica y mantiene la conexión WiFi"""
        try:
            if self.sta_if is None:
                return False
                
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_check) >= self.CHECK_INTERVAL:
                self.last_check = current_time
                
                if not self.sta_if.isconnected():
                    print("[WIFI] Conexión perdida, intentando reconexión...")
                    if self.ssid and self.password:
                        return self.connect_wifi(self.ssid, self.password)
                    return False
                    
                self.current_ip = self.sta_if.ifconfig()[0]
                return True
                
            return self.sta_if.isconnected()
                
        except Exception as e:
            print(f"[WIFI] Error verificando conexión: {e}")
            return False

    def _init_interface(self):
        """Inicializa la interfaz WiFi de manera segura"""
        try:
            if self.sta_if is not None:
                try:
                    self.sta_if.active(False)
                    utime.sleep_ms(500)
                except:
                    pass
                self.sta_if = None
            
            gc.collect()
            utime.sleep_ms(500)
            
            self.sta_if = network.WLAN(network.STA_IF)
            self.sta_if.active(False)
            utime.sleep_ms(500)
            
            # Configurar el buffer antes de activar
            try:
                if hasattr(self.sta_if, 'config'):
                    self.sta_if.config(txbuf=1024, rxbuf=1024)
                    utime.sleep_ms(500)
            except:
                print("[WIFI] Advertencia: No se pudo configurar buffer")
            
        except Exception as e:
            print(f"[WIFI] Error inicializando interfaz: {e}")
            self.sta_if = None
            raise

    def reset_interface(self):
        """Reinicia la interfaz WiFi si hay problemas"""
        try:
            print("[WIFI] Reiniciando interfaz WiFi...")
            self._init_interface()
            return True
        except:
            return False

    def connect_wifi(self, ssid, password):
        """Conecta al WiFi con manejo de errores mejorado"""
        try:
            if not ssid or not password:
                print("[WIFI] Error: Credenciales faltantes")
                self.last_error = "credenciales_invalidas"
                return False

            print(f"[WIFI] Intentando conexión a: {ssid}")
            
            # Verificar estado de la interfaz
            if self.sta_if is None or not self._safe_init_interface():
                print("[WIFI] Error: Interfaz no disponible")
                return False

            gc.collect()
            utime.sleep_ms(500)

            # Activar interfaz
            if not self.sta_if.active():
                self.sta_if.active(True)
                utime.sleep_ms(1000)

            # Desconectar si ya está conectado
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
                utime.sleep_ms(500)

            retry_count = 0
            while retry_count < self.MAX_RETRIES:
                print(f"[WIFI] Intento {retry_count + 1}/{self.MAX_RETRIES}")
                
                try:
                    self.sta_if.connect(ssid, password)
                except Exception as e:
                    print(f"[WIFI] Error intentando conectar: {e}")
                    retry_count += 1
                    if retry_count < self.MAX_RETRIES:
                        gc.collect()
                        utime.sleep_ms(self.RETRY_DELAY)
                        continue
                    return False
                
                start_time = utime.ticks_ms()
                while utime.ticks_diff(utime.ticks_ms(), start_time) < self.CONNECT_TIMEOUT:
                    if self.sta_if.isconnected():
                        utime.sleep_ms(500)  # Esperar a que la conexión se estabilice
                        self.current_ip = self.sta_if.ifconfig()[0]
                        print(f"[WIFI] Conectado exitosamente - IP: {self.current_ip}")
                        self.ssid = ssid
                        self.password = password
                        self._save_config()
                        return True
                    
                    if not self.sta_if.active():
                        print("[WIFI] Interfaz desactivada durante la conexión")
                        break
                        
                    status = self.sta_if.status()
                    if status == network.STAT_CONNECTING:
                        utime.sleep_ms(100)
                        continue
                    elif status == network.STAT_WRONG_PASSWORD:
                        print("[WIFI] Error: Contraseña incorrecta")
                        return False
                    elif status == network.STAT_NO_AP_FOUND:
                        print("[WIFI] Error: Red no encontrada")
                        break
                    
                    utime.sleep_ms(100)
                
                retry_count += 1
                if retry_count < self.MAX_RETRIES:
                    print(f"[WIFI] Reintentando conexión... ({retry_count + 1})")
                    gc.collect()
                    utime.sleep_ms(self.RETRY_DELAY)
                    # Reinicializar interfaz entre intentos
                    self._safe_init_interface()
                    self.sta_if.active(True)
                    utime.sleep_ms(500)

            print("[WIFI] No se pudo establecer conexión después de todos los intentos")
            return False

        except Exception as e:
            print(f"[WIFI] Error en conexión: {e}")
            return False

    def _save_config(self):
        """Guarda configuración WiFi de manera segura"""
        try:
            if self.sta_if.isconnected():
                print(f"[WIFI] Guardando configuración - SSID: {self.ssid}")
                config = {
                    'ssid': self.ssid,
                    'password': self.password,
                    'last_connected': utime.time(),
                    'ip': self.sta_if.ifconfig()[0]
                }

                temp_file = self.WIFI_CONFIG_FILE + '.tmp'
                with open(temp_file, 'w') as f:
                    json.dump(config, f)
                os.rename(temp_file, self.WIFI_CONFIG_FILE)
                print("[WIFI] Configuración guardada exitosamente")
                return True
            else:
                print("[WIFI] No se guarda la configuración, no hay conexión")
                return False
                    
        except Exception as e:
            print(f"[WIFI] Error guardando configuración: {e}")
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def _load_saved_config(self):
        """Carga configuración WiFi guardada"""
        try:
            if self.WIFI_CONFIG_FILE in os.listdir():
                print("[WIFI] Cargando configuración guardada...")
                with open(self.WIFI_CONFIG_FILE, 'r') as f:
                    config = json.load(f)
                    if config.get('ssid') and config.get('password'):
                        self.ssid = config['ssid']
                        self.password = config['password']
                        print(f"[WIFI] Configuración cargada para SSID: {self.ssid}")
                        return True
            print("[WIFI] No se encontró configuración guardada")
            return False
        except Exception as e:
            print(f"[WIFI] Error cargando configuración: {e}")
            return False

    def forget_wifi_config(self):
        """Olvida la configuración WiFi guardada"""
        try:
            self.ssid = None
            self.password = None
            if self.sta_if.active():
                self.sta_if.active(False)
            if self.WIFI_CONFIG_FILE in os.listdir():
                os.remove(self.WIFI_CONFIG_FILE)
            print("[WIFI] Configuración WiFi olvidada")
            return True
        except Exception as e:
            print(f"[WIFI] Error olvidando configuración: {e}")
            return False

    def disconnect(self):
        """Desconecta del WiFi actual si está conectado"""
        try:
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
                self.sta_if.active(False)
                while self.sta_if.isconnected():
                    utime.sleep_ms(100)
                print("[WIFI] Desconexión exitosa")
                return True
            return True
        except Exception as e:
            print(f"[WIFI] Error en desconexión: {e}")
            return False

    def get_current_ip(self):
        """Obtiene la IP actual si está conectado"""
        try:
            if self.sta_if.isconnected():
                return self.sta_if.ifconfig()[0]
            return None
        except Exception as e:
            print(f"[WIFI] Error obteniendo IP: {e}")
            return None