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
            "status_interval": 300000,     # 5 minutos
            "health_timeout": 300000,      # 5 minutos (aumentado)
            "reconnect_delay": 10000,      # 10 segundos (aumentado)
            "keepalive": 120,             # 2 minutos (aumentado)
            "ping_interval": 30000,       # 30 segundos (nuevo)
            
            # Reintentos y delays
            "initial_retry_delay": 5000,   # 5 segundos (aumentado)
            "max_retry_delay": 60000,      # 1 minuto (aumentado)
            "max_retries": 5,             # 5 intentos (aumentado)
            
            # Límites de buffer y cola
            "buffer_size": 256,           # 256 bytes
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
            "broker": self.config.get('broker', self.DEFAULT_CONFIG['broker']),
            "port": self.config.get('port', self.DEFAULT_CONFIG['port']),
            "client_id": self.config.get('client_id', self.DEFAULT_CONFIG['client_id']),
            "user": self.config.get('user', self.DEFAULT_CONFIG['user']),
            "password": self.config.get('password', self.DEFAULT_CONFIG['password'])
        }

    def get_status_interval(self):
        return self.config.get('status_interval', self.DEFAULT_CONFIG['status_interval'])

    def get_reconnect_delay(self):
        return self.config.get('reconnect_delay', self.DEFAULT_CONFIG['reconnect_delay'])

    def get_buffer_size(self):
        return self.config.get('buffer_size', self.DEFAULT_CONFIG['buffer_size'])

    def get_queue_size(self):
        return self.config.get('max_queue_size', self.DEFAULT_CONFIG['max_queue_size'])

    def get_max_processed_ids(self):
        return self.config.get('max_processed_ids', self.DEFAULT_CONFIG['max_processed_ids'])

    def get_keepalive(self):
        return self.config.get('keepalive', self.DEFAULT_CONFIG['keepalive'])

    def get_initial_retry_delay(self):
        return self.config.get('initial_retry_delay', self.DEFAULT_CONFIG['initial_retry_delay'])

    def get_max_retry_delay(self):
        return self.config.get('max_retry_delay', self.DEFAULT_CONFIG['max_retry_delay'])

    def get_max_retries(self):
        return self.config.get('max_retries', self.DEFAULT_CONFIG['max_retries'])
    
    def get_health_timeout(self):
        return self.config.get('health_timeout', self.DEFAULT_CONFIG['health_timeout'])