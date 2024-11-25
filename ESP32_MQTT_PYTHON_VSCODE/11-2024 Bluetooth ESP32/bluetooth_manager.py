from micropython import const
import bluetooth
import json
import utime
from ble_uart_peripheral import BLEUART

# Constantes BLE
_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)
_IRQ_GATTS_WRITE = const(3)

class BluetoothManager:
    def __init__(self, name="ESP32-Monitor"):
        print("[BT] Iniciando BLE...")
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        
        # Crear UART con nuestro manejador de IRQ personalizado
        self.uart = BLEUART(self.ble, name=name, rxbuf=100)
        self.uart.irq(self._on_uart_rx)
        
        self._connected = False
        self.last_parsed_data = None
        print("[BT] BLE activo y listo - Nombre:", name)
        print("[BT] Esperando conexión BLE...")

    def _on_uart_rx(self):
        """Callback cuando se reciben datos por UART"""
        try:
            # Leer datos disponibles
            data = self.uart.read().decode().strip()
            print(f"[BT] Datos recibidos: '{data}'")
            
            if data:
                # Intentar parsear los datos
                parsed = self.parse_simple_format(data)
                if parsed:
                    print(f"[BT] Datos parseados: {parsed}")
                    self.last_parsed_data = parsed
                    # Enviar confirmación
                    self.write_data("status:received")
        except Exception as e:
            print(f"[BT] Error en rx callback: {e}")
            import sys
            sys.print_exception(e)

    def parse_simple_format(self, text):
        """Parsea el formato simple key:value,key:value"""
        try:
            if not text:
                return None
                
            text = text.strip()
            pairs = [pair.strip() for pair in text.split(',') if ':' in pair]
            result = {}
            
            for pair in pairs:
                key, value = pair.split(':', 1)
                key = key.strip()
                value = value.strip()
                if key and value:
                    result[key] = value
                    
            return result if result else None
            
        except Exception as e:
            print(f"[BT] Error parseando: {e}")
            return None

    def write_data(self, data):
        """Envía datos a través de BLE"""
        try:
            if isinstance(data, dict):
                text = ','.join(f"{k}:{v}" for k, v in data.items())
            else:
                text = str(data)
            print(f"[BT] Enviando: {text}")
            self.uart.write(text + "\n")
            return True
        except Exception as e:
            print(f"[BT] Error enviando datos: {e}")
            return False

    def wait_for_data(self, timeout=1):
        """Espera y retorna datos si están disponibles"""
        if self.last_parsed_data is not None:
            data = self.last_parsed_data
            self.last_parsed_data = None
            return data
        return None

    def close(self):
        """Cierra la conexión BLE"""
        try:
            self.uart.close()
        except:
            pass