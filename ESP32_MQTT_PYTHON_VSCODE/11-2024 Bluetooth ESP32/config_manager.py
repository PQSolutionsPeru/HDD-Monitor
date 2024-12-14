import json
import os
import gc
import machine
import utime

class ConfigManager:
    def __init__(self, mqtt_manager=None):
        """Inicializa el gestor de configuración"""
        self.CONFIG_FILE = "device_config.json"
        self.BACKUP_FILE = "device_config.bak"
        self.mqtt_manager = mqtt_manager
        
        # Tópicos MQTT
        self.CONFIG_TOPIC_BASE = "esp32/config"
        self.CONFIG_AVAILABLE_TOPIC = "esp32/available"
        self.esp32_id = None
        self.config_topic = None
        
        # Configuración por defecto
        self.default_config = {
            "wifi": {
                "ssid": None,
                "password": None
            },
            "panel": {
                "clientId": None,
                "panelId": None,
                "name": None,
                "location": None
            },
            "esp32_id": None,
            "configured": False,
            "last_update": 0
        }
        
        self.config = None
        self._load_config()
        print("[CONFIG] Gestor de configuración iniciado")

    def set_mqtt_manager(self, mqtt_manager):
        """Establece el gestor MQTT después de la inicialización"""
        self.mqtt_manager = mqtt_manager

    def set_esp32_id(self, esp32_id):
        """Configura el ID del ESP32 y tópicos relacionados"""
        self.esp32_id = esp32_id
        self.config_topic = f"{self.CONFIG_TOPIC_BASE}/{esp32_id}"
        self.config["esp32_id"] = esp32_id
        return self.save_config()

    def start_config_mode(self):
        """Inicia modo de configuración del panel vía MQTT"""
        try:
            if not self.mqtt_manager or not self.esp32_id:
                raise ValueError("MQTT Manager o ESP32 ID no configurados")

            print(f"[CONFIG] Iniciando modo configuración en {self.config_topic}")
            
            # Publicar disponibilidad con ID y estado actual
            self.mqtt_manager.publish_event(
                self.CONFIG_AVAILABLE_TOPIC,
                {
                    "esp32_id": self.esp32_id,
                    "name": f"ESP32-{self.esp32_id}",
                    "status": "waiting_config",
                    "timestamp": utime.ticks_ms()
                }
            )

            # Suscribirse al tópico específico de configuración
            self.mqtt_manager.subscribe(self.config_topic, self.handle_config_message)
            return True

        except Exception as e:
            print(f"[CONFIG] Error iniciando modo configuración: {e}")
            return False

    def handle_config_message(self, topic, message):
        """Maneja mensajes de configuración recibidos vía MQTT"""
        try:
            print(f"[CONFIG] Mensaje recibido: {message}")
            config = json.loads(message)

            if self._validate_panel_config(config):
                # Confirmar recepción
                self.mqtt_manager.publish_event(
                    self.config_topic,
                    {
                        "esp32_id": self.esp32_id,
                        "status": "config_received",
                        "timestamp": utime.ticks_ms()
                    }
                )

                # Guardar configuración
                self.config['panel'] = {
                    'clientId': config['client_id'],
                    'panelId': config['panel_id'],
                    'name': config['panel_name'],
                    'location': config['panel_location']
                }
                self.config['configured'] = True
                self.config['last_update'] = utime.ticks_ms()

                if self._save_config(self.CONFIG_FILE):
                    # Enviar confirmación final
                    self.mqtt_manager.publish_event(
                        self.config_topic,
                        {
                            "esp32_id": self.esp32_id,
                            "status": "config_applied",
                            "timestamp": utime.ticks_ms()
                        }
                    )

                    # Despedirse del tópico de configuración
                    self.mqtt_manager.publish_event(
                        self.CONFIG_AVAILABLE_TOPIC,
                        {
                            "esp32_id": self.esp32_id,
                            "status": "configured",
                            "timestamp": utime.ticks_ms()
                        },
                        retain=True
                    )

                    # Esperar que se envíen los mensajes
                    utime.sleep_ms(1000)
                    machine.reset()

            return True

        except Exception as e:
            print(f"[CONFIG] Error procesando mensaje: {e}")
            self._publish_error("Error procesando configuración", str(e))
            return False

    def _publish_error(self, title, message):
        """Publica un mensaje de error en el tópico de configuración"""
        if self.mqtt_manager and self.config_topic:
            self.mqtt_manager.publish_event(
                self.config_topic,
                {
                    "esp32_id": self.esp32_id,
                    "status": "error",
                    "error": title,
                    "message": message,
                    "timestamp": utime.ticks_ms()
                }
            )

    def _validate_panel_config(self, config):
        """Valida la configuración del panel recibida"""
        required = ['client_id', 'panel_id', 'panel_name', 'panel_location']
        return (
            isinstance(config, dict) and
            all(key in config for key in required) and
            all(isinstance(config[key], str) and config[key].strip() for key in required) and
            config['client_id'].startswith('client_') and
            config['panel_id'].startswith('panel_')
        )

    def get_operation_topic(self):
        """Obtiene el tópico de operación basado en la configuración"""
        if not self.is_configured():
            return None
            
        panel = self.config["panel"]
        return f"clients/{panel['clientId']}/panels/{panel['panelId']}"

    # [Mantener los métodos de manejo de archivos sin cambios]
    def _save_config(self, filename):
        """Guarda la configuración de manera segura"""
        try:
            temp_file = filename + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump(self.config, f)
            
            with open(temp_file, 'r') as f:
                test_config = json.load(f)
                if not self._validate_config(test_config):
                    raise ValueError("Validación fallida")
            
            os.rename(temp_file, filename)
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error guardando en {filename}: {e}")
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def save_config(self):
        """Guarda la configuración con respaldo"""
        try:
            gc.collect()
            if not self._save_config(self.BACKUP_FILE):
                return False
            if not self._save_config(self.CONFIG_FILE):
                return False
            return True
        except Exception as e:
            print(f"[CONFIG] Error en save_config: {e}")
            return False

    def save_wifi_config(self, ssid, password):
        """Guarda configuración WiFi"""
        try:
            self.config["wifi"]["ssid"] = ssid
            self.config["wifi"]["password"] = password
            return self.save_config()
        except Exception as e:
            print(f"[CONFIG] Error guardando WiFi: {e}")
            return False

    def is_configured(self):
        """Verifica si el dispositivo está configurado"""
        return self.config["configured"]

    def get_wifi_config(self):
        """Obtiene configuración WiFi"""
        return self.config["wifi"].copy() if self.config else None

    def get_panel_config(self):
        """Obtiene configuración del panel"""
        return self.config["panel"].copy() if self.config else None

    def _load_config(self):
        """Carga la configuración desde archivo con sistema de respaldo"""
        try:
            # Intentar cargar archivo principal
            if self.CONFIG_FILE in os.listdir():
                with open(self.CONFIG_FILE, 'r') as f:
                    self.config = json.load(f)
                    if self._validate_config(self.config):
                        return True

            # Intentar cargar backup
            if self.BACKUP_FILE in os.listdir():
                print("[CONFIG] Usando archivo de respaldo")
                with open(self.BACKUP_FILE, 'r') as f:
                    self.config = json.load(f)
                    if self._validate_config(self.config):
                        self._save_config(self.CONFIG_FILE)  # Restaurar principal
                        return True

            # Usar configuración por defecto
            print("[CONFIG] Usando configuración por defecto")
            self.config = self.default_config.copy()
            return self._save_config(self.CONFIG_FILE)

        except Exception as e:
            print(f"[CONFIG] Error cargando configuración: {e}")
            self.config = self.default_config.copy()
            return False

    def _validate_config(self, config):
        """Valida la estructura de la configuración"""
        try:
            return (
                isinstance(config, dict) and
                'wifi' in config and
                'panel' in config and
                'configured' in config and
                isinstance(config['wifi'], dict) and
                isinstance(config['panel'], dict) and
                isinstance(config['configured'], bool)
            )
        except Exception as e:
            print(f"[CONFIG] Error validando configuración: {e}")
            return False