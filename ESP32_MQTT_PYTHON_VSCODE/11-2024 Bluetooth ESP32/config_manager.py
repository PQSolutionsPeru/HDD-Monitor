import json
import os
import gc
import machine
import utime

class ConfigManager:
    def __init__(self, mqtt_manager=None):
        """Inicializa el gestor de configuración"""
        print("[CONFIG] Iniciando gestor de configuración...")
        self.CONFIG_FILE = "device_config.json"
        self.mqtt_manager = mqtt_manager
        self.esp32_id = None
        
        # Modos de operación
        self.MODES = {
            'WIFI_CONFIG': 'WIFI_CONFIG',    # Esperando configuración WiFi
            'AWAITING_CONFIG': 'AWAITING_CONFIG',  # Esperando config MQTT
            'RUNNING': 'RUNNING'     # Operación normal
        }
        self.current_mode = self.MODES['WIFI_CONFIG']
        
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
            "mode": self.MODES['WIFI_CONFIG'],
            "pending_config": None
        }
        
        # Timeouts y reintentos
        self.CONFIG_TIMEOUT = 300000  # 5 minutos
        self.RETRY_DELAY = 5000      # 5 segundos
        self.MAX_RETRIES = 3
        self.last_retry = 0
        self.retry_count = 0
        
        self._load_config()
        self.current_mode = self.config.get('mode', self.MODES['WIFI_CONFIG'])
        print(f"[CONFIG] Gestor iniciado en modo: {self.current_mode}")

    def set_mqtt_manager(self, mqtt_manager):
        """Establece el gestor MQTT después de la inicialización"""
        try:
            print("[CONFIG] Configurando gestor MQTT")
            self.mqtt_manager = mqtt_manager
            
            if not self.mqtt_manager.check_connection():
                print("[CONFIG] Error: MQTT no conectado")
                return False
                
            # Configuración adicional si es necesaria
            return True
        except Exception as e:
            print(f"[CONFIG] Error configurando MQTT: {e}")
            return False

    def set_esp32_id(self, esp32_id):
        """Configura el ID del ESP32"""
        try:
            print(f"[CONFIG] Configurando ESP32 ID: {esp32_id}")
            self.esp32_id = esp32_id
            self.config["esp32_id"] = esp32_id
            
            # Si hay MQTT, publicar información de red
            if self.mqtt_manager and self.mqtt_manager.check_connection():
                network_info = {
                    "esp32_id": self.esp32_id,
                    "MAC": self.mqtt_manager.get_mac(),
                    "IP": self.mqtt_manager.get_ip_address(),
                    "status": "AWAITING_CONFIG",
                    "timestamp": utime.ticks_ms()
                }
                print(f"[CONFIG] Publicando info de red: {network_info}")
                self.mqtt_manager.publish_event(
                    "esp32/network_info",
                    network_info,
                    retain=False
                )
            
            return self._save_config()
        except Exception as e:
            print(f"[CONFIG] Error en set_esp32_id: {e}")
            return False

    def wifi_configured(self):
        """Se llama cuando WiFi se configura exitosamente"""
        try:
            print("[CONFIG] WiFi configurado, cambiando a modo AWAITING_CONFIG")
            self.current_mode = self.MODES['AWAITING_CONFIG']
            self.config['mode'] = self.MODES['AWAITING_CONFIG']
            self.config['wifi']['last_connected'] = utime.time()
            self._save_config()

            if not self.wifi_manager.sta_if.isconnected():
                print("[CONFIG] Conexión WiFi perdida, volviendo a modo WIFI_CONFIG")
                self.current_mode = self.MODES['WIFI_CONFIG']
                self.config['mode'] = self.MODES['WIFI_CONFIG']
                self._save_config()
                return False
            
            if self.mqtt_manager and self.esp32_id:
                # Esperar conexión MQTT con reintentos
                retry_count = 0
                while not self.mqtt_manager.check_connection() and retry_count < 3:
                    print(f"[CONFIG] Esperando conexión MQTT... ({retry_count + 1})")
                    utime.sleep_ms(1000)
                    retry_count += 1
                
                if not self.mqtt_manager.check_connection():
                    print("[CONFIG] Error: No se pudo establecer conexión MQTT")
                    return False
                
                # Publicar estado con QoS 1 sin retain
                status_message = {
                    "esp32_id": self.esp32_id,
                    "status": "AWAITING_CONFIG",
                    "MAC": self.mqtt_manager.get_mac(),
                    "IP": self.mqtt_manager.get_ip_address(),
                    "timestamp": utime.ticks_ms(),
                    "wifi_ssid": self.config['wifi']['ssid']  # Añadir para debug
                }
                
                result = self.mqtt_manager.publish_event(
                    "esp32/network_info",
                    status_message,
                    qos=1
                )
                
                if not result:
                    print("[CONFIG] Error publicando estado")
                    return False
                    
                # Suscribirse al tópico de configuración
                config_topic = f"esp32/config/{self.esp32_id}"
                print(f"[CONFIG] Suscribiendo a: {config_topic}")
                if not self.mqtt_manager.subscribe(config_topic, self.handle_config_message):
                    print("[CONFIG] Error en suscripción MQTT")
                    return False
                    
                return True
                
            print("[CONFIG] MQTT no disponible")
            return False
                
        except Exception as e:
            print(f"[CONFIG] Error en wifi_configured: {e}")
            return False

    def recover_from_disconnect(self):
        """Maneja recuperación de desconexión"""
        try:
            print("[CONFIG] Iniciando recuperación de desconexión...")
            
            # Si tenemos configuración WiFi guardada
            if self.config['wifi']['ssid']:
                print("[CONFIG] Intentando reconectar a WiFi...")
                wifi_config = self.get_wifi_config()
                if self.wifi_manager.connect_wifi(wifi_config['ssid'], wifi_config['password']):
                    print("[CONFIG] Reconexión WiFi exitosa")
                    
                    # Si estábamos en modo CONFIG o superior, reconectar MQTT
                    if self.current_mode in [self.MODES['AWAITING_CONFIG'], self.MODES['RUNNING']]:
                        if self.mqtt_manager and self.mqtt_manager.reconnect():
                            print("[CONFIG] Reconexión MQTT exitosa")
                            return True
                        
            return False
            
        except Exception as e:
            print(f"[CONFIG] Error en recuperación: {e}")
            return False

    def _validate_message_id(self, message_id: str) -> bool:
        """Valida ID de mensaje para evitar duplicados"""
        try:
            # Mantener un set de los últimos N IDs procesados
            if not hasattr(self, '_processed_ids'):
                self._processed_ids = set()
                self._max_processed_ids = 100
                
            # Si el ID ya fue procesado, ignorar
            if message_id in self._processed_ids:
                return False
                
            # Agregar nuevo ID
            self._processed_ids.add(message_id)
            
            # Mantener tamaño máximo
            if len(self._processed_ids) > self._max_processed_ids:
                self._processed_ids.pop()
                
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error validando ID: {e}")
            return False

    def handle_config_message(self, topic, message):
        """Maneja mensajes de configuración recibidos vía MQTT"""
        try:
            print(f"[CONFIG] Procesando mensaje de configuración: {message}")
            
            # Ignorar mensajes vacíos (usados para limpiar retain)
            if not message or (isinstance(message, str) and message.isspace()):
                print("[CONFIG] Ignorando mensaje vacío (limpieza retain)")
                return False
                
            if isinstance(message, bytes):
                message = message.decode('utf-8')
            
            # Ignorar strings vacíos después de decode
            if not message or message.isspace():
                print("[CONFIG] Ignorando mensaje vacío después de decode")
                return False
                
            try:
                config = json.loads(message)
            except json.JSONDecodeError as e:
                print(f"[CONFIG] Error decodificando JSON: {e}")
                return False

            # Validar que el mensaje no sea un estado
            if config.get('status') == 'running_mode_entered':
                print("[CONFIG] Ignorando mensaje de estado")
                return False

            # Validar configuración
            if not self._validate_panel_config(config):
                print("[CONFIG] Error: Configuración inválida")
                return False

            print("[CONFIG] Configuración válida recibida:")
            print(f"[CONFIG] - Cliente: {config['client_id']}")
            print(f"[CONFIG] - Panel: {config['panel_id']}")
            print(f"[CONFIG] - ESP32: {config['esp32_id']}")
            
            # Actualizar configuración
            self.config['panel']['client_id'] = config['client_id']
            self.config['panel']['panel_id'] = config['panel_id']
            self.config['configured'] = True
            self.config['mode'] = self.MODES['RUNNING']
            self.current_mode = self.MODES['RUNNING']

            if not self._save_config():
                print("[CONFIG] Error guardando configuración")
                return False

            print("[CONFIG] Configuración guardada exitosamente")
            
            # Confirmar recepción
            if self.mqtt_manager:
                confirmation = {
                    "esp32_id": self.esp32_id,
                    "status": "config_applied",
                    "client_id": config['client_id'],
                    "panel_id": config['panel_id'],
                    "timestamp": {
                        "value": utime.ticks_ms(),
                        "type": "realtime"
                    }
                }
                
                print("[CONFIG] Enviando confirmación...")
                if not self.mqtt_manager.publish_event(
                    f"esp32/config/{self.esp32_id}",
                    confirmation,
                    retain=False,
                    qos=1
                ):
                    print("[CONFIG] Error enviando confirmación")
                    return False
                    
                print("[CONFIG] Confirmación enviada")
                utime.sleep_ms(2000)
                
                # Entrar en modo RUNNING
                running_result = self.enter_running_mode()
                if not running_result:
                    print("[CONFIG] Error entrando en modo RUNNING")
                    return False
                    
                print("[CONFIG] Transición a modo RUNNING completada")
                return True
            
            return False

        except Exception as e:
            print(f"[CONFIG] Error procesando configuración: {e}")
            return False

    def enter_running_mode(self, panel_config=None):
        """Entra en modo de operación normal"""
        try:
            # Si ya estamos en modo RUNNING, ignorar
            if self.current_mode == self.MODES['RUNNING'] and not panel_config:
                print("[CONFIG] Ya en modo RUNNING, ignorando")
                return True
                
            if not panel_config and not self.is_configured():
                print("[CONFIG] Error: Dispositivo no configurado")
                return False
            
            print("[CONFIG] Entrando en modo RUNNING")
            gc.collect()  # Limpiar memoria antes de proceder
            
            # Actualizar configuración si se proporciona
            if panel_config:
                print("[CONFIG] Actualizando configuración del panel")
                self.config['panel'].update(panel_config)
                self.config['configured'] = True
                
            self.current_mode = self.MODES['RUNNING']
            self.config['mode'] = self.MODES['RUNNING']
            
            # Guardar configuración
            if not self._save_config():
                print("[CONFIG] Error guardando estado RUNNING")
                return False
            
            # Configurar y verificar relays primero
            if hasattr(self, 'relay_manager'):
                print("[CONFIG] Configurando relays...")
                try:
                    # Configurar pines de relays
                    for pin in [32, 33, 25]:  # Pines definidos para relays
                        self.relay_manager.setup_relay(
                            pin_num=pin,
                            callback=self._relay_state_callback
                        )
                    print("[CONFIG] Relays configurados exitosamente")
                except Exception as e:
                    print(f"[CONFIG] Error crítico configurando relays: {e}")
                    return False
            else:
                print("[CONFIG] ADVERTENCIA: relay_manager no disponible")
                
            # Publicar estado si MQTT está disponible
            if self.mqtt_manager:
                print("[CONFIG] Preparando publicación de estado RUNNING")
                network_info = {
                    'esp32_id': self.esp32_id,
                    'status': 'RUNNING',
                    'client_id': self.config['panel']['client_id'],
                    'panel_id': self.config['panel']['panel_id'],
                    'MAC': self.mqtt_manager.get_mac(),
                    'IP': self.mqtt_manager.get_ip_address(),
                    'timestamp': {
                        'value': utime.ticks_ms(),
                        'type': 'realtime'
                    },
                    'message_id': f"{utime.ticks_ms()}-running"
                }
                
                print("[CONFIG] Publicando estado final RUNNING")
                success = self.mqtt_manager.publish_event(
                    "esp32/network_info",
                    network_info,
                    qos=1,
                    retain=False
                )
                
                if not success:
                    print("[CONFIG] Error publicando estado RUNNING")
                    return False
                
                # Suscribirse a tópico de operación
                operation_topic = self.get_operation_topic()
                if operation_topic:
                    print(f"[CONFIG] Suscribiendo a tópico de operación: {operation_topic}")
                    if not self.mqtt_manager.subscribe(operation_topic, self.handle_operation_message):
                        print("[CONFIG] Error en suscripción a tópico de operación")
                        return False
                    print("[CONFIG] Suscripción a tópico de operación exitosa")
            
            print("[CONFIG] Modo RUNNING establecido correctamente")
            return True
                
        except Exception as e:
            print(f"[CONFIG] Error entrando en modo RUNNING: {e}")
            if hasattr(e, '__class__'):
                print(f"[CONFIG] Tipo de error: {e.__class__.__name__}")
            return False

    def _relay_state_callback(self, pin, pin_number, timestamp=None):
        """Callback para cambios en estado de relays"""
        try:
            state = "DISC" if pin.value() else "OK"
            pin_names = {32: "Alarma", 33: "Problema", 25: "Supervision"}
            pin_name = pin_names.get(pin_number, str(pin_number))
            
            print(f"[CONFIG] Cambio en relay {pin_number} ({pin_name}): {state}")
            
            if self.mqtt_manager and self.config['panel'].get('client_id') and self.config['panel'].get('panel_id'):
                message = {
                    'esp32_id': self.esp32_id,
                    'relay': pin_name,
                    'state': state,
                    'timestamp': {
                        'value': utime.ticks_ms(),
                        'type': 'realtime'
                    }
                }
                
                if timestamp:
                    message['date_time'] = timestamp
                
                self.mqtt_manager.publish_event(
                    f"clients/{self.config['panel']['client_id']}/panels/{self.config['panel']['panel_id']}",
                    message,
                    qos=1,
                    retain=False
                )
        except Exception as e:
            print(f"[CONFIG] Error en relay callback: {e}")
            
    def handle_operation_message(self, topic, message):
        """
        Maneja mensajes de operación recibidos del panel
        """
        try:
            print(f"[CONFIG] Procesando mensaje de operación: {message}")
            
            # Ignorar mensajes vacíos (usados para limpiar retain)
            if not message or (isinstance(message, str) and message.isspace()) or message == b'':
                print("[CONFIG] Ignorando mensaje vacío de operación")
                return True
            
            if isinstance(message, (bytes, bytearray)):
                message = message.decode('utf-8')
            
            # Validar el mensaje
            try:
                data = json.loads(message) if isinstance(message, str) else message
            except Exception as e:
                print(f"[CONFIG] Error decodificando mensaje de operación: {e}")
                return False
                
            # Verificar campos requeridos
            required_fields = ['esp32_id', 'client_id', 'panel_id']
            if not all(field in data for field in required_fields):
                print("[CONFIG] Campos requeridos faltantes en mensaje de operación")
                return False
                
            # Verificar que el mensaje es para este ESP32
            if data['esp32_id'] != self.esp32_id:
                print(f"[CONFIG] ID no coincide: esperado {self.esp32_id}, recibido {data['esp32_id']}")
                return False
                
            # Verificar que coincida con la configuración actual
            if (data['client_id'] != self.config['panel']['client_id'] or 
                data['panel_id'] != self.config['panel']['panel_id']):
                print("[CONFIG] Configuración de panel no coincide")
                return False
                
            # Procesar el mensaje según su tipo
            message_type = data.get('type', 'status')
            print(f"[CONFIG] Procesando mensaje tipo: {message_type}")
            
            if message_type == 'status':
                # Actualizar estado si es necesario
                new_status = data.get('status')
                if new_status:
                    self.config['status'] = new_status
                    self._save_config()
                    print(f"[CONFIG] Estado actualizado a: {new_status}")
                    
            elif message_type == 'config':
                # Procesar cambios de configuración si es necesario
                if 'panel_config' in data:
                    self.config['panel'].update(data['panel_config'])
                    self._save_config()
                    print("[CONFIG] Configuración del panel actualizada")
                    
            # Confirmar recepción si se requiere
            if data.get('require_ack'):
                if self.mqtt_manager:
                    ack_message = {
                        'esp32_id': self.esp32_id,
                        'message_id': data.get('message_id'),
                        'status': 'received',
                        'timestamp': utime.ticks_ms()
                    }
                    self.mqtt_manager.publish_event(
                        f"esp32/operation_ack/{self.esp32_id}",
                        ack_message,
                        qos=1
                    )
                    print("[CONFIG] Confirmación de recepción enviada")
                    
            print("[CONFIG] Mensaje de operación procesado exitosamente")
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error procesando mensaje de operación: {e}")
            if hasattr(e, '__class__'):
                print(f"[CONFIG] Tipo de error: {e.__class__.__name__}")
            return False

    def _validate_panel_config(self, config):
        """Valida la configuración del panel recibida"""
        try:
            print(f"[CONFIG] Validando config: {config}")
            required_fields = ['client_id', 'panel_id', 'esp32_id']
            
            # Verificar campos requeridos
            if not all(key in config for key in required_fields):
                print("[CONFIG] Error: Campos faltantes")
                print(f"[CONFIG] Campos presentes: {list(config.keys())}")
                return False
            
            # Verificar tipos
            if not all(isinstance(config[key], str) for key in required_fields):
                print("[CONFIG] Error: Tipos inválidos")
                for key in required_fields:
                    if key in config:
                        print(f"[CONFIG] {key}: {type(config[key])}")
                return False
            
            # Verificar contenido
            if not all(len(str(config[key]).strip()) > 0 for key in required_fields):
                print("[CONFIG] Error: Valores vacíos")
                for key in required_fields:
                    if key in config:
                        print(f"[CONFIG] {key}: '{config[key]}'")
                return False
            
            # Verificar ID
            if config['esp32_id'] != self.esp32_id:
                print(f"[CONFIG] Error: ID no coincide")
                print(f"[CONFIG] Esperado: {self.esp32_id}")
                print(f"[CONFIG] Recibido: {config['esp32_id']}")
                return False
            
            print("[CONFIG] Validación exitosa")
            return True
            
        except Exception as e:
            print(f"[CONFIG] Error en validación: {e}")
            return False

    def _save_config(self):
        """Guarda la configuración de manera segura"""
        try:
            gc.collect()
            
            # Usar archivo temporal
            temp_file = self.CONFIG_FILE + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump(self.config, f)
            
            # Verificar archivo temporal
            with open(temp_file, 'r') as f:
                test_config = json.load(f)
                if not self._validate_config(test_config):
                    raise ValueError("Validación de config fallida")
            
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
            config_found = False
            
            # Intentar cargar configuración principal
            if self.CONFIG_FILE in os.listdir():
                print("[CONFIG] Intentando cargar configuración guardada...")
                with open(self.CONFIG_FILE, 'r') as f:
                    stored_config = json.load(f)
                    if self._validate_config(stored_config):
                        self.config = stored_config
                        config_found = True
                        print("[CONFIG] Configuración cargada exitosamente")

            # Intentar cargar configuración del panel si existe
            try:
                if 'panel_config.json' in os.listdir():
                    with open('panel_config.json', 'r') as f:
                        panel_config = json.load(f)
                        if panel_config.get('client_id') and panel_config.get('panel_id'):
                            # Actualizar configuración con datos del panel
                            self.config['panel']['client_id'] = panel_config['client_id']
                            self.config['panel']['panel_id'] = panel_config['panel_id']
                            self.config['configured'] = True
                            self.config['mode'] = self.MODES['RUNNING']
                            print("[CONFIG] Configuración del panel cargada")
                            # Guardar configuración actualizada
                            self._save_config()
            except Exception as e:
                print(f"[CONFIG] Error cargando config panel: {e}")
                        
            if config_found:
                if (self.config.get("configured") and 
                    self.config.get("panel") and 
                    self.config["panel"].get("client_id") and 
                    self.config["panel"].get("panel_id")):
                    print("[CONFIG] Configuración completa encontrada")
                    self.current_mode = self.MODES['RUNNING']
                    return True
                            
                elif self.config.get("wifi") and self.config["wifi"].get("ssid"):
                    print("[CONFIG] Solo configuración WiFi encontrada")
                    self.current_mode = self.MODES['AWAITING_CONFIG']
                    return True
                        
            print("[CONFIG] No se encontró configuración válida")
            self.current_mode = self.MODES['WIFI_CONFIG']
            return False
                
        except Exception as e:
            print(f"[CONFIG] Error cargando config: {e}")
            self.current_mode = self.MODES['WIFI_CONFIG']
            return False

    def get_operation_topic(self):
        """Obtiene el tópico de operación basado en la configuración actual"""
        try:
            if not self.config.get('panel'):
                return None
                
            client_id = self.config['panel'].get('client_id')
            panel_id = self.config['panel'].get('panel_id')
            
            if not client_id or not panel_id:
                return None
                
            return f"clients/{client_id}/panels/{panel_id}"
            
        except Exception as e:
            print(f"[CONFIG] Error obteniendo tópico de operación: {e}")
            return None

    def _validate_config(self, config):
        """Valida estructura completa de la configuración"""
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

    def get_mode(self):
        """Obtiene el modo actual"""
        return self.current_mode

    def is_configured(self):
        """Verifica si el dispositivo está configurado"""
        return self.config["configured"]

    def save_wifi_config(self, ssid, password):
        try:
            print(f"[CONFIG] Guardando configuración WiFi para SSID: {ssid}")
            self.config['wifi']['ssid'] = ssid
            self.config['wifi']['password'] = password
            # Agregar backup adicional para WiFi
            wifi_backup = {
                'ssid': ssid,
                'password': password,
                'timestamp': utime.ticks_ms()
            }
            # Guardar en archivo separado
            with open('wifi_backup.json', 'w') as f:
                json.dump(wifi_backup, f)
            return self._save_config()
        except Exception as e:
            print(f"[CONFIG] Error guardando config WiFi: {e}")
            return False

    def get_wifi_config(self):
        """Obtiene configuración WiFi actual"""
        return self.config.get('wifi', {'ssid': None, 'password': None})