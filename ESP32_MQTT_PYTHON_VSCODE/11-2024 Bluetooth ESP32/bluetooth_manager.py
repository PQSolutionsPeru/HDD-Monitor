from machine import Pin
import bluetooth
import json
import gc
import utime
from ble_uart_peripheral import BLEUART

class BluetoothManager:
    def __init__(self, esp32_id):
        """Inicializa BLE solo para configuración WiFi"""
        print("[BLE] Iniciando BLE...")
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        
        # Identificación del dispositivo
        self.esp32_id = esp32_id
        self.device_name = f"ESP32-{esp32_id}" if esp32_id else "ESP32-SETUP"
        self.uart = BLEUART(self.ble, name=self.device_name)
        
        # Callback WiFi
        self.wifi_callback = None
        
        # Control de estado
        self.current_state = 'waiting'
        self.last_activity = utime.ticks_ms()
        
        # Constantes
        self.TIMEOUT = 300000  # 5 minutos timeout
        self.MAX_MSG_SIZE = 1024  # 1KB máximo mensaje
        
        # Inicialización
        self.uart.irq(self._on_uart_rx)
        print(f"[BLE] BLE activo - Nombre: {self.device_name}")

    def set_wifi_callback(self, callback):
        """Establece callback para configuración WiFi"""
        self.wifi_callback = callback

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
                self._process_data(data)
                
            self.last_activity = utime.ticks_ms()
            
        except Exception as e:
            print(f"[BLE] Error en rx: {e}")
            self.write_response("error:rx_error")

    def _process_data(self, data):
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
                
                if self.wifi_callback:
                    if self.wifi_callback(ssid, password):
                        self.write_response("ok:wifi_configurado")
                        return
                    else:
                        self.write_response("error:conexion_fallida")
                        return
                        
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
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_activity) > self.TIMEOUT:
            print("[BLE] Timeout de inactividad")
            self.close()

    def close(self):
        """Cierra conexiones BLE"""
        try:
            print("[BLE] Cerrando BLE...")
            
            # Primero desconectar UART
            if hasattr(self, 'uart') and self.uart:
                try:
                    self.uart.close()
                    utime.sleep_ms(100)  # Esperar que se complete
                except:
                    pass
                self.uart = None
            
            gc.collect()
            utime.sleep_ms(100)  # Esperar GC
            
            # Luego desactivar BLE
            if hasattr(self, 'ble') and self.ble:
                try:
                    self.ble.active(False)
                    utime.sleep_ms(100)  # Esperar que se complete
                except:
                    pass
                self.ble = None
            
            gc.collect()
            print("[BLE] BLE cerrado")
            return True
            
        except Exception as e:
            print(f"[BLE] Error cerrando: {e}")
            return False