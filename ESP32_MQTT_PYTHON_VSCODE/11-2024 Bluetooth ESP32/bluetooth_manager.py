import bluetooth
import ujson
import gc
import utime
from ble_uart_peripheral import BLEUART

class BluetoothManager:
    def __init__(self, esp32_id):
        """Inicializa el gestor Bluetooth solo para configuración WiFi"""
        print("[BT] Iniciando BLE...")
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        
        self.device_name = f"ESP32-{esp32_id}"
        self.uart = BLEUART(self.ble, name=self.device_name, rxbuf=4096)
        
        # Callback WiFi
        self.wifi_callback = None
        
        # Control de estado y buffer
        self.current_state = 'waiting'
        self.last_activity = utime.ticks_ms()
        self.process_queue = []
        
        # Constantes
        self.TIMEOUT = 60000  # 1 minuto (reducido ya que solo maneja WiFi)
        self.QUEUE_MAX_SIZE = 5
        self.MAX_CHUNK_SIZE = 20
        
        self.uart.irq(self._on_uart_rx)
        print(f"[BT] BLE activo - Nombre: {self.device_name}")
        
    def set_wifi_callback(self, callback):
        """Establece el callback para la configuración WiFi"""
        self.wifi_callback = callback

    def _on_uart_rx(self):
        """Maneja datos recibidos con protección"""
        try:
            data = self.uart.read().decode('utf-8')
            if not data:
                return
                
            print(f"[BT] Datos recibidos: {data}")
            if len(self.process_queue) < self.QUEUE_MAX_SIZE:
                self.process_queue.append(data)
            else:
                print("[BT] Cola de procesamiento llena")
            
            self.last_activity = utime.ticks_ms()
            
        except Exception as e:
            print(f"[BT] Error en rx: {e}")
            self.write_response("status:error,message:rx_error")

    def process(self):
        """Procesa la cola de datos pendientes"""
        try:
            gc.collect()
            
            while self.process_queue:
                data = self.process_queue.pop(0)
                if "{START}" in data and "{END}" in data:
                    self._process_json_data(data)
                    
            if utime.ticks_diff(utime.ticks_ms(), self.last_activity) > self.TIMEOUT:
                print("[BT] Timeout de inactividad")
                self.close()
                
        except Exception as e:
            print(f"[BT] Error en process: {e}")

    def _process_json_data(self, data):
        """Procesa datos JSON de configuración WiFi"""
        try:
            json_text = data.split("{START}")[1].split("{END}")[0].strip()
            config = ujson.loads(json_text)
            
            if self._validate_wifi_config(config):
                if self.wifi_callback:
                    if self.wifi_callback(config['ssid'], config['password']):
                        self.write_response("status:ok,message:wifi_configured")
                        return True
                    else:
                        self.write_response("status:error,message:wifi_connection_failed")
                        return False
                
        except ValueError as e:
            print(f"[BT] Error en datos: {e}")
            self.write_response("status:error,message:invalid_format")
        except Exception as e:
            print(f"[BT] Error procesando datos: {e}")
            self.write_response("status:error,message:processing_error")

    def _validate_wifi_config(self, config):
        """Valida configuración WiFi"""
        return (
            isinstance(config, dict) and
            'ssid' in config and
            'password' in config and
            isinstance(config['ssid'], str) and
            isinstance(config['password'], str) and
            len(config['ssid'].strip()) > 0 and
            len(config['password'].strip()) >= 8
        )

    def write_response(self, message):
        """Envía respuesta de manera segura en chunks"""
        try:
            if not message.endswith('\n'):
                message += '\n'
                
            print(f"[BT] Enviando respuesta: {message.strip()}")
            message_bytes = message.encode('utf-8')
            
            for i in range(0, len(message_bytes), self.MAX_CHUNK_SIZE):
                chunk = message_bytes[i:i + self.MAX_CHUNK_SIZE]
                self.uart.write(chunk)
                utime.sleep_ms(10)
                
        except Exception as e:
            print(f"[BT] Error enviando respuesta: {e}")

    def close(self):
        """Cierra conexiones de manera segura"""
        try:
            print("[BT] Cerrando conexión BLE...")
            
            if hasattr(self, 'uart') and self.uart:
                try:
                    self.uart.close()
                    utime.sleep_ms(100)
                except:
                    pass
                self.uart = None
            
            gc.collect()
            utime.sleep_ms(100)
            
            if hasattr(self, 'ble') and self.ble:
                try:
                    self.ble.active(False)
                except:
                    pass
                self.ble = None
            
            print("[BT] Conexión BLE cerrada")
            return True
            
        except Exception as e:
            print(f"[BT] Error cerrando conexión: {e}")
            return False