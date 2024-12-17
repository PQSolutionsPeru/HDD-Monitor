import json
import os
import gc
import machine
import utime

class ConfigManager:
    def __init__(self, mqtt_manager=None):
        """Inicializa el gestor de configuración"""
        self.CONFIG_FILE = "device_config.json"
        self.mqtt_manager = mqtt_manager
        self.esp32_id = None
        
        # Modos de operación
        self.MODES = {
            'CONFIG': 'CONFIG',      # Esperando configuración inicial/reconfigurando
            'RUNNING': 'RUNNING'     # Operación normal
        }
        self.current_mode = self.MODES['CONFIG']
        
        # Configuración por defecto
        self.config = {
            "wifi": {
                "ssid": None,
                "password": None
            },
            "panel": {
                "client_id": None,
                "panel_id": None
            },
            "esp32_id": None,
            "configured": False,
            "mode": self.MODES['CONFIG']
        }
        
        self._load_config()
        self.current_mode = self.config.get('mode', self.MODES['CONFIG'])
        print(f"[CONFIG] Gestor iniciado en modo: {self.current_mode}")

    def set_mqtt_manager(self, mqtt_manager):
        """Establece el gestor MQTT después de la inicialización"""
        self.mqtt_manager = mqtt_manager

    def set_esp32_id(self, esp32_id):
        """Configura el ID del ESP32"""
        self.esp32_id = esp32_id
        self.config["esp32_id"] = esp32_id
        return self._save_config()

    def enter_config_mode(self, reason="manual"):
        """Entra en modo configuración"""
        try:
            print(f"[CONFIG] Entrando en modo configuración. Razón: {reason}")
            
            # Actualizar modo
            self.current_mode = self.MODES['CONFIG']
            self.config['mode'] = self.MODES['CONFIG']
            self.config['configured'] = False
            self._save_config()
            
            if self.mqtt_manager and self.esp32_id:
                # Desuscribirse del tópico de operación si existe
                operation_topic = self.get_operation_topic()
                if operation_topic:
                    self.mqtt_manager.unsubscribe(operation_topic)
                
                # Notificar cambio de modo
                self.mqtt_manager.publish_event(
                    f"system/status/{self.esp32_id}",
                    {
                        "esp32_id": self.esp32_id,
                        "status": "CONFIG",
                        "reason": reason,
                        "timestamp": utime.ticks_ms()
                    },
                    retain=True
                )
                
                # Iniciar modo configuración
                return self.start_config_mode()
                
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error entrando en modo configuración: {e}")
            return False

    def handle_operation_message(self, topic, message):
        """Maneja mensajes en modo operación"""
        try:
            msg = json.loads(message)
            
            # Verificar comando y ESP32 ID
            if (msg.get('command') == 'enter_config_mode' and 
                msg.get('esp32_id') == self.esp32_id):
                print("[CONFIG] Comando de reconfiguración recibido")
                self.enter_config_mode("command_received")
                return True
                
        except Exception as e:
            print(f"[CONFIG] Error en mensaje de operación: {e}")
            return False

    def start_config_mode(self):
        """Inicia modo de configuración del panel vía MQTT"""
        try:
            if not self.mqtt_manager or not self.esp32_id:
                return False

            print("[CONFIG] Iniciando modo configuración")
            
            # Suscribirse al tópico de configuración
            config_topic = f"esp32/config/{self.esp32_id}"
            self.mqtt_manager.subscribe(config_topic, self.handle_config_message)
            
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
                # Guardar configuración
                self.config['panel'] = {
                    'client_id': config['client_id'],
                    'panel_id': config['panel_id']
                }
                self.config['configured'] = True
                self.config['mode'] = self.MODES['RUNNING']
                self.current_mode = self.MODES['RUNNING']

                if self._save_config():
                    # Confirmar configuración aplicada
                    self.mqtt_manager.publish_event(
                        f"esp32/config/{self.esp32_id}",
                        {
                            "esp32_id": self.esp32_id,
                            "status": "config_applied",
                            "client_id": config['client_id'],
                            "panel_id": config['panel_id']
                        },
                        retain=True
                    )

                    # Esperar que se envíe el mensaje
                    utime.sleep_ms(1000)
                    machine.reset()

            return True

        except Exception as e:
            print(f"[CONFIG] Error procesando mensaje: {e}")
            return False

    def enter_running_mode(self):
        """Entra en modo de operación normal"""
        if not self.is_configured():
            return False
            
        try:
            self.current_mode = self.MODES['RUNNING']
            self.config['mode'] = self.MODES['RUNNING']
            self._save_config()
            
            if self.mqtt_manager:
                # Suscribirse al tópico de operación
                operation_topic = self.get_operation_topic()
                if operation_topic:
                    self.mqtt_manager.subscribe(operation_topic, self.handle_operation_message)
                
                # Notificar cambio de modo
                self.mqtt_manager.publish_event(
                    f"system/status/{self.esp32_id}",
                    {
                        "esp32_id": self.esp32_id,
                        "status": "RUNNING",
                        "timestamp": utime.ticks_ms()
                    },
                    retain=True
                )
                
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error entrando en modo operación: {e}")
            return False

    def save_wifi_config(self, ssid, password):
        """Guarda configuración WiFi"""
        try:
            self.config["wifi"]["ssid"] = ssid
            self.config["wifi"]["password"] = password
            return self._save_config()
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

    def get_operation_topic(self):
        """Obtiene el tópico de operación"""
        if not self.is_configured():
            return None
            
        panel = self.config["panel"]
        return f"clients/{panel['client_id']}/panels/{panel['panel_id']}"

    def _save_config(self):
        """Guarda la configuración"""
        try:
            gc.collect()
            
            # Guardar en temporal primero
            temp_file = self.CONFIG_FILE + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump(self.config, f)
            
            # Verificar archivo temporal
            with open(temp_file, 'r') as f:
                test_config = json.load(f)
                if not self._validate_config(test_config):
                    raise ValueError("Validación fallida")
            
            # Reemplazar archivo principal
            os.rename(temp_file, self.CONFIG_FILE)
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error guardando config: {e}")
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def _load_config(self):
        """Carga la configuración desde archivo"""
        try:
            if self.CONFIG_FILE in os.listdir():
                with open(self.CONFIG_FILE, 'r') as f:
                    stored_config = json.load(f)
                    if self._validate_config(stored_config):
                        self.config = stored_config
                        return True
            return False
            
        except Exception as e:
            print(f"[CONFIG] Error cargando config: {e}")
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
            print(f"[CONFIG] Error validando config: {e}")
            return False

    def _validate_panel_config(self, config):
        """Valida la configuración del panel recibida"""
        return (
            isinstance(config, dict) and
            'client_id' in config and
            'panel_id' in config and
            isinstance(config['client_id'], str) and
            isinstance(config['panel_id'], str) and
            len(config['client_id'].strip()) > 0 and
            len(config['panel_id'].strip()) > 0
        )

    def get_mode(self):
        """Obtiene el modo actual"""
        return self.current_mode