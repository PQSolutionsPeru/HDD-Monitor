# config/device_pool_config.py
from config.base_config import BaseConfig
import utime

class DevicePoolConfig(BaseConfig):
    def __init__(self):
        self.DEFAULT_CONFIG = {
            # Lista de dispositivos MQTT disponibles
            "devices": [
                {
                    "client_id": "ESP32-PQ1",
                    "user": "ESP32-1",
                    "password": "esp32",
                    "in_use": False,  # Indica si está siendo usado
                    "assigned_to": None,  # MAC address del ESP32 que lo usa
                    "last_used": 0  # Timestamp del último uso
                },
                {
                    "client_id": "ESP32-PQ2",
                    "user": "ESP32-2",
                    "password": "esp32",
                    "in_use": False,
                    "assigned_to": None,
                    "last_used": 0
                }
                # Agregar más dispositivos según necesites
            ],
            # Historial de asignaciones
            "assignments": []
        }
        super().__init__('device_pool.json')
        self._init_default_config()

    def _init_default_config(self):
        if not self.config:
            self.config = self.DEFAULT_CONFIG.copy()
            self._save_config()

    def get_available_device(self):
        """Obtiene un dispositivo disponible del pool"""
        for device in self.config['devices']:
            if not device['in_use']:
                return device
        return None

    def assign_device(self, mac_address):
        """Asigna un dispositivo a un ESP32 específico"""
        device = self.get_available_device()
        if device:
            device['in_use'] = True
            device['assigned_to'] = mac_address
            device['last_used'] = utime.ticks_ms()
            
            # Registrar asignación
            self.config['assignments'].append({
                'mac': mac_address,
                'client_id': device['client_id'],
                'timestamp': utime.ticks_ms()
            })
            
            self._save_config()
            return device
        return None

    def release_device(self, mac_address):
        """Libera un dispositivo asignado"""
        for device in self.config['devices']:
            if device['assigned_to'] == mac_address:
                device['in_use'] = False
                device['assigned_to'] = None
                device['last_used'] = 0
                self._save_config()
                return True
        return False

    def get_device_by_mac(self, mac_address):
        """Obtiene la configuración del dispositivo asignado a un MAC"""
        for device in self.config['devices']:
            if device['assigned_to'] == mac_address:
                return device
        return None