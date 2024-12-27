from machine import Pin
import bluetooth
import json
import gc
import utime
import ubinascii
import machine
from ble_uart_peripheral import BLEUART

class BluetoothManager:
    def __init__(self, esp32_id=None, config_manager=None, wifi_manager=None):
        """Inicializa BLE solo para configuración WiFi"""
        print("[BLE] Iniciando BLE...")
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        
        # Generar ID basado en MAC si no fue proporcionado
        if not esp32_id:
            mac = ubinascii.hexlify(machine.unique_id()).decode().upper()
            esp32_id = mac[-4:] + "AC" + mac[:2]  # Mismo formato que en ESP32IdManager
            print(f"[BLE] ID generado desde MAC: {esp32_id}")
            # Guardar el ID para que ESP32IdManager lo use después
            try:
                with open("esp32_id.json", 'w') as f:
                    json.dump({
                        'esp32_id': esp32_id,
                        'mac': mac,
                        'timestamp': utime.ticks_ms(),
                        'history': [{'id': esp32_id, 'event': 'generated_by_ble', 'timestamp': utime.ticks_ms()}]
                    }, f)
            except Exception as e:
                print(f"[BLE] Error guardando ID: {e}")
        
        # Identificación del dispositivo
        self.esp32_id = esp32_id
        self.device_name = f"ESP32-{esp32_id}"
        self.uart = BLEUART(self.ble, name=self.device_name)
        
        # Gestores
        self.config_manager = config_manager
        self.wifi_manager = wifi_manager
        
        # Callback WiFi
        self.wifi_callback = None
        
        # Control de estado
        self.is_configured = False
        self.current_state = 'waiting'
        self.last_activity = utime.ticks_ms()
        
        # Constantes
        self.TIMEOUT = 300000           # 5 minutos timeout
        self.WIFI_CONNECT_TIMEOUT = 60000  # 60 segundos timeout para WiFi
        self.CONNECT_RETRY_DELAY = 30000   # 30 segundos entre reintentos
        self.MAX_RETRIES = 5               # 5 intentos máximo
        self.MAX_MSG_SIZE = 1024           # 1KB máximo mensaje
        self.WAIT_CONFIRMATION = 10000     # 10 segundos para esperar confirmación
        
        # Inicialización
        self.uart.irq(self._on_uart_rx)
        print(f"[BLE] BLE activo - Nombre: {self.device_name}")

    def set_wifi_callback(self, wifi_callback):
        """Establece callback para configuración WiFi"""
        self.wifi_callback = wifi_callback

    def _on_uart_rx(self):
        """Maneja datos recibidos con protección"""
        try:
            data = self.uart.read().decode()
            if not data:
                return
                
            print(f"[BLE] Datos recibidos: {data}")
            
            # Verificar tamaño máximo
            if len(data) > self.MAX_MSG_SIZE:
                self.write_response("error:mensaje_muy_grande")
                return
                
            if "{START}" in data and "{END}" in data:
                self._process_wifi_config(data)
                
            self.last_activity = utime.ticks_ms()
            
        except Exception as e:
            print(f"[BLE] Error en rx: {e}")
            self.write_response("error:rx_error")

    def _process_wifi_config(self, data):
        """Procesa datos JSON de configuración WiFi"""
        try:
            json_text = data.split("{START}")[1].split("{END}")[0].strip()
            config = json.loads(json_text)
            
            if 'ssid' in config and 'password' in config:
                ssid = config['ssid'].strip()
                password = config['password'].strip()
                
                # Validación básica
                if len(ssid) == 0:
                    self.write_response("error:ssid_invalido")
                    return
                    
                if len(password) < 8:
                    self.write_response("error:password_corto")
                    return
                
                if not self.wifi_callback:
                    self.write_response("error:no_callback")
                    return
                
                # Enviar confirmación inicial con el ID del ESP32
                self.write_response(f"ready:wifi_config|esp32_id:{self.esp32_id}")
                utime.sleep_ms(500)
                
                # Intentar conectar WiFi con reintentos
                for attempt in range(self.MAX_RETRIES):
                    if attempt > 0:
                        print(f"[BLE] Intento {attempt + 1} de conexión WiFi")
                        utime.sleep_ms(self.CONNECT_RETRY_DELAY)
                    
                    if not self.wifi_callback(ssid, password):
                        print("[BLE] Fallo en conexión")
                        continue
                    
                    # Esperar a que se obtenga IP válida
                    start_time = utime.ticks_ms()
                    while utime.ticks_diff(utime.ticks_ms(), start_time) < self.WIFI_CONNECT_TIMEOUT:
                        if self.wifi_manager and self.wifi_manager.check_connection():
                            ip = self.wifi_manager.get_ip_address()
                            if ip and ip != "0.0.0.0":
                                print(f"[BLE] WiFi conectado con IP: {ip}")
                                self.is_configured = True
                                
                                # Enviar confirmación con IP y ESP32 ID
                                self.write_response(f"ok:wifi_configurado|ip:{ip}|esp32_id:{self.esp32_id}")
                                
                                # Esperar configuración del panel (aumentar tiempo si es necesario)
                                print("[BLE] Esperando configuración del panel...")
                                
                                # NO cerrar BLE aquí, mantenerlo activo hasta recibir comando de cierre
                                # o hasta que se complete la configuración del panel
                                return True
                        utime.sleep_ms(100)
                    
                    print("[BLE] Timeout esperando IP")
                
                # Si llegamos aquí, todos los intentos fallaron
                print("[BLE] Todos los intentos de conexión fallaron")
                self.write_response("error:conexion_fallida")
                return False
                        
        except ValueError as e:
            print(f"[BLE] Error en datos: {e}")
            self.write_response("error:formato_invalido")
        except Exception as e:
            print(f"[BLE] Error procesando datos: {e}")
            self.write_response("error:error_proceso")

    def write_response(self, message):
        """Envía respuesta de manera segura"""
        try:
            if not message.endswith('\n'):
                message += '\n'
                
            print(f"[BLE] Enviando respuesta: {message.strip()}")
            self.uart.write(message.encode())
                
        except Exception as e:
            print(f"[BLE] Error enviando respuesta: {e}")

    def process(self):
        """Procesa timeout de BLE"""
        # Solo procesar si estamos en modo CONFIG inicial
        if self.config_manager and self.config_manager.get_mode() != 'CONFIG':
            return

        current_time = utime.ticks_ms()
        
        # Verificar timeout solo si no hay actividad reciente
        if utime.ticks_diff(current_time, self.last_activity) > self.TIMEOUT:
            print("[BLE] Timeout de inactividad - Cerrando BLE")
            self.close()
            return

    def close(self):
        """Cierra conexiones BLE"""
        try:
            print("[BLE] Cerrando BLE...")
            
            # Primero enviar mensaje final si es necesario
            if hasattr(self, 'uart') and self.uart:
                try:
                    self.write_response("bye:closing_connection")
                    utime.sleep_ms(500)  # Esperar que se envíe el mensaje
                except:
                    pass

            # Luego desconectar UART
            if hasattr(self, 'uart') and self.uart:
                try:
                    self.uart.close()
                    utime.sleep_ms(500)
                except:
                    pass
                self.uart = None
            
            # Recolectar basura
            gc.collect()
            utime.sleep_ms(500)
            
            # Finalmente desactivar BLE
            if hasattr(self, 'ble') and self.ble:
                try:
                    self.ble.active(False)
                    utime.sleep_ms(500)
                except:
                    pass
                self.ble = None
            
            # Última recolección de basura
            gc.collect()
            print("[BLE] BLE cerrado")
            
            return True
                
        except Exception as e:
            print(f"[BLE] Error cerrando: {e}")
            return False