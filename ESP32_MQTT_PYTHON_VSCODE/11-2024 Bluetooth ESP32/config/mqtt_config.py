from config.base_config import BaseConfig

class MQTTConfig(BaseConfig):
    def __init__(self):
        self.DEFAULT_CONFIG = {
            # Configuración del broker
            "broker": "node02.myqtthub.com",
            "port": 1883,
            "client_id": "ESP32-PQ1",
            "user": "ESP32-1",
            "password": "esp32",
            
            # Intervalos y tiempos
            "status_interval": 60000,      # 1 minuto
            "health_timeout": 60000,       # 1 minuto
            "reconnect_delay": 5000,       # 5 segundos
            "keepalive": 60,              # 1 minuto
            "ping_interval": 30000,       # 30 segundos
            
            # Reintentos y delays
            "initial_retry_delay": 2000,    # 2 segundos
            "max_retry_delay": 10000,       # 10 segundos
            "max_retries": 3,              # 3 intentos
            
            # Límites de buffer y cola
            "buffer_size": 512,           # 512 bytes
            "max_queue_size": 10,         # 10 mensajes
            "max_processed_ids": 100      # 100 IDs procesados
        }
        super().__init__('mqtt_config.json')
        self._init_default_config()

    def _init_default_config(self):
        if not self.config:
            self.config = self.DEFAULT_CONFIG.copy()
            self._save_config()

    def get_broker_config(self):
        return {
            "broker": self.config.get('broker'),
            "port": self.config.get('port'),
            "client_id": self.config.get('client_id'),
            "user": self.config.get('user'),
            "password": self.config.get('password')
        }

    def get_status_interval(self):
        return self.config.get('status_interval')

    def get_reconnect_delay(self):
        return self.config.get('reconnect_delay')

    def get_buffer_size(self):
        return self.config.get('buffer_size')

    def get_queue_size(self):
        return self.config.get('max_queue_size')

    def get_max_processed_ids(self):
        return self.config.get('max_processed_ids')

    def get_keepalive(self):
        return self.config.get('keepalive')

    def get_initial_retry_delay(self):
        return self.config.get('initial_retry_delay')

    def get_max_retry_delay(self):
        return self.config.get('max_retry_delay')

    def get_max_retries(self):
        return self.config.get('max_retries')
    
    def get_health_timeout(self):
        return self.config.get('health_timeout')