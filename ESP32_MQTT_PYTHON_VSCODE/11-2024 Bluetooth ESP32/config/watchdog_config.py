from config.base_config import BaseConfig

class WatchdogConfig(BaseConfig):
    def __init__(self):
        self.DEFAULT_CONFIG = {
            # Timeouts generales
            "timeout": 60000,          # 1 minuto general
            "feed_interval": 15000,    # 15 segundos entre feeds
            
            # Timeouts específicos por modo
            "config_mode": {
                "timeout": 60000,      # 1 minuto para configuración
                "relay_check": False,  # No verificar relays durante configuración
                "mqtt_check": False    # No verificar MQTT durante configuración
            },
            "running_mode": {
                "timeout": 60000,      # 1 minuto en operación normal
                "relay_check": True,   # Verificar relays en operación
                "relay_check_interval": 20000,  # 20 segundos
                "mqtt_check": True,    # Verificar MQTT en operación
                "mqtt_timeout": 60000  # 1 minuto timeout MQTT
            },
            
            # Configuración de resets
            "reset": {
                "window": 300000,      # 5 minutos ventana
                "max_count": 3,        # máximo 3 resets
                "memory_threshold": 20000  # 20KB mínimo
            }
        }
        super().__init__('watchdog_config.json')
        self._init_default_config()
        self.current_mode = "config_mode"

    def _init_default_config(self):
        if not self.config:
            self.config = self.DEFAULT_CONFIG.copy()
            self._save_config()

    def set_mode(self, mode):
        if mode in ["config_mode", "running_mode"]:
            self.current_mode = mode
            return True
        return False

    def should_check_relays(self):
        mode_config = self.config.get(self.current_mode, {})
        return mode_config.get('relay_check', False)

    def should_check_mqtt(self):
        mode_config = self.config.get(self.current_mode, {})
        return mode_config.get('mqtt_check', False)

    def get_current_timeout(self):
        mode_config = self.config.get(self.current_mode, {})
        return mode_config.get('timeout', self.config['timeout'])

    def get_relay_check_interval(self):
        mode_config = self.config.get(self.current_mode, {})
        return mode_config.get('relay_check_interval', 20000)

    def get_mqtt_timeout(self):
        mode_config = self.config.get(self.current_mode, {})
        return mode_config.get('mqtt_timeout', 60000)