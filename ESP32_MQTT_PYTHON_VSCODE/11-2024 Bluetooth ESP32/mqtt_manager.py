from umqtt.robust import MQTTClient
import json
import gc
import utime
import machine
import ubinascii
import random

class MQTTManager:
    def __init__(self, wifi_manager):
        """Inicializa el gestor MQTT"""
        self.wifi_manager = wifi_manager
        self.client = None
        self.esp32_id = None
        self.mac_address = ubinascii.hexlify(machine.unique_id()).decode()
        
        # Información de operación
        self.client_id = None
        self.panel_id = None
        self.operation_mode = 'CONFIG'
        self.message_queue = []
        self._processed_ids = set()
        
        # Control de reportes
        self.last_status_report = 0
        self.STATUS_REPORT_INTERVAL = 3600000  # 60 minutos
        
        # Credenciales del broker
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 1883
        self.MQTT_CLIENT_ID = "ESP32-PQ1"
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"

        # Control de reconexión y timeouts
        self.last_connection_attempt = 0
        self.RECONNECT_DELAY = 15000      # 15 segundos entre intentos
        self.MQTT_TIMEOUT = 10000         # 10 segundos para operaciones MQTT
        self.MAX_RECONNECT_ATTEMPTS = 3    # 3 intentos máximos
        
        # Configuración de buffer y memoria
        self.MSG_BUFFER_SIZE = 256      # 256 bytes
        self.MAX_QUEUE_SIZE = 10        # 10 mensajes máximo en cola
        self.MIN_FREE_MEMORY = 15000    # 15KB mínimo
        self.MAX_PROCESSED_IDS = 100    # Máximo IDs procesados guardados
        
        print("[MQTT] Manager iniciado con configuración optimizada")

    def check_connection(self):
        """Verifica si hay conexión MQTT"""
        try:
            if not self.client:
                return False
                
            try:
                return self.ensure_connection()
            except:
                return False
                
        except Exception as e:
            print(f"[MQTT] Error verificando conexión: {e}")
            return False

    def ensure_connection(self):
        """Asegura que hay conexión MQTT con manejo de errores"""
        if not self.wifi_manager.check_connection():
            print("[MQTT] Sin conexión WiFi")
            return False
            
        try:
            if self.client:
                return True
                
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_connection_attempt) < self.RECONNECT_DELAY:
                return False
                
            print("[MQTT] Intentando nueva conexión...")
            self.last_connection_attempt = current_time
            return self.connect()
                
        except Exception as e:
            print(f"[MQTT] Error en ensure_connection: {e}")
            return False

    def subscribe(self, topic, callback=None):
        """Suscribe a tópico con verificación de conexión"""
        try:
            print(f"[MQTT] Intentando suscribirse a: {topic}")
            
            if not self.ensure_connection():
                print("[MQTT] Error: No hay conexión para suscribirse")
                return False
                
            if callback:
                print("[MQTT] Configurando callback")
                self.client.set_callback(callback)
                
            print("[MQTT] Ejecutando suscripción...")
            self.client.subscribe(topic.encode(), qos=1)
            print("[MQTT] Suscripción exitosa")
            return True
                
        except Exception as e:
            print(f"[MQTT] Error suscribiendo: {e}")
            return False

    def connect(self):
        """Conecta al broker MQTT con manejo de errores mejorado"""
        try:
            print("[MQTT] Iniciando conexión...")
            gc.collect()
            
            if not self.wifi_manager.check_connection():
                print("[MQTT] Error: Sin conexión WiFi")
                return False
            
            if not self.wifi_manager.current_ip:
                print("[MQTT] Error: No se pudo obtener IP")
                return False

            print("[MQTT] Creando cliente...")
            self.client = MQTTClient(
                client_id=self.MQTT_CLIENT_ID,
                server=self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=self.MQTT_USER,
                password=self.MQTT_PASSWORD,
                keepalive=30
            )

            print("[MQTT] Intentando conexión...")
            self.client.connect()
            print("[MQTT] Conectado exitosamente!")

            # Configurar suscripción si tenemos ID
            if self.esp32_id:
                config_topic = f"esp32/config/{self.esp32_id}"
                print(f"[MQTT] Suscribiendo a: {config_topic}")
                self.client.set_callback(self._handle_config_message)
                self.client.subscribe(config_topic.encode())

                # Publicar estado inicial
                self._publish_network_info()

            return True

        except Exception as e:
            print(f"[MQTT] Error en conexión: {str(e)}")
            return False

    def _handle_config_message(self, topic, msg):
        """Procesa mensajes de configuración"""
        try:
            print(f"[MQTT] Mensaje de configuración recibido: {msg}")
            
            # Ignorar mensajes vacíos (usados para limpiar retain)
            if not msg or (isinstance(msg, str) and msg.isspace()):
                print("[MQTT] Ignorando mensaje vacío (limpieza retain)")
                return
                    
            if isinstance(msg, bytes):
                try:
                    msg = msg.decode('utf-8')
                except UnicodeError:
                    print("[MQTT] Error decodificando mensaje")
                    return
            
            if not msg or msg.isspace():
                print("[MQTT] Ignorando mensaje vacío después de decode")
                return
                
            try:
                config = json.loads(msg)
                print(f"[MQTT] Configuración decodificada: {config}")
            except Exception as e:
                print(f"[MQTT] Error decodificando JSON: {e}")
                return

            # Validar campos requeridos
            client_id = config.get('client_id')
            panel_id = config.get('panel_id')
            esp32_id = config.get('esp32_id')
            message_id = config.get('message_id')

            if not all([client_id, panel_id, esp32_id, message_id]):
                print("[MQTT] Campos requeridos faltantes")
                return

            if esp32_id != self.esp32_id:
                print("[MQTT] ID no coincide")
                return
                
            if hasattr(self, 'config_manager'):
                print("[MQTT] Verificando estado actual con ConfigManager")
                if self.config_manager.get_mode() == self.config_manager.MODES['RUNNING']:
                    print("[MQTT] Ya en modo RUNNING, ignorando configuración")
                    return

            print(f"[MQTT] Configuración válida recibida para panel {panel_id}")

            # Guardar configuración en archivo permanente
            try:
                panel_config = {
                    'client_id': client_id,
                    'panel_id': panel_id,
                    'esp32_id': esp32_id,
                    'timestamp': utime.ticks_ms()
                }
                with open('panel_config.json', 'w') as f:
                    json.dump(panel_config, f)
                print("[MQTT] Configuración guardada en almacenamiento permanente")
            except Exception as e:
                print(f"[MQTT] Error guardando configuración: {e}")
                return

            # Guardar configuración en memoria
            self.client_id = client_id
            self.panel_id = panel_id

            # Enviar confirmación con todos los campos necesarios
            confirmation = {
                'esp32_id': self.esp32_id,
                'status': 'config_applied',
                'client_id': client_id,
                'panel_id': panel_id,
                'config_id': message_id,
                'timestamp': {
                    'value': utime.ticks_ms(),
                    'type': 'realtime'
                },
                'message_id': f"{utime.ticks_ms()}-ack",
                'MAC': self.mac_address,
                'IP': self.wifi_manager.current_ip if self.wifi_manager else None
            }

            # Publicar confirmación con QoS 1
            if not self.publish_event(
                f"esp32/config_ack/{self.esp32_id}",
                confirmation,
                qos=1,
                retain=False
            ):
                print("[MQTT] Error enviando confirmación")
                return

            print("[MQTT] Confirmación enviada, actualizando modo...")
            utime.sleep_ms(500)  # Esperar para asegurar que la confirmación se envió
            
            # Notificar cambio de estado en network_info
            network_status = {
                'esp32_id': self.esp32_id,
                'status': 'RUNNING',
                'client_id': client_id,
                'panel_id': panel_id,
                'MAC': self.mac_address,
                'IP': self.wifi_manager.current_ip if self.wifi_manager else None,
                'timestamp': {
                    'value': utime.ticks_ms(),
                    'type': 'realtime'
                },
                'message_id': f"{utime.ticks_ms()}-status"
            }
            
            # Publicar estado en network_info con QoS 1
            if not self.publish_event(
                "esp32/network_info",
                network_status,
                qos=1,
                retain=False
            ):
                print("[MQTT] Error publicando estado RUNNING")
                return

            print("[MQTT] Estado RUNNING publicado, notificando ConfigManager...")
            utime.sleep_ms(1000)
            
            # Notificar a ConfigManager
            if hasattr(self, 'config_manager') and self.config_manager:
                result = self.config_manager.enter_running_mode({
                    'client_id': client_id,
                    'panel_id': panel_id
                })
                if not result:
                    print("[MQTT] Error en transición a modo RUNNING")
                    return
                    
            print("[MQTT] Transición a modo RUNNING completada")
            
        except Exception as e:
            print(f"[MQTT] Error procesando configuración: {e}")
            if hasattr(e, '__class__'):
                print(f"[MQTT] Tipo de error: {e.__class__.__name__}")

    def _validate_message_id(self, message_id: str) -> bool:
        """Valida ID de mensaje para evitar duplicados"""
        if message_id in self._processed_ids:
            return False
            
        self._processed_ids.add(message_id)
        if len(self._processed_ids) > self.MAX_PROCESSED_IDS:
            self._processed_ids.pop()
            
        return True

    def publish_event(self, topic, message, qos=1, retain=False):
        """Publica evento MQTT con manejo de errores mejorado"""
        try:
            if not self.ensure_connection():
                print("[MQTT] No hay conexión al intentar publicar")
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                return False

            # Forzar retain=False para mensajes de red y status
            if topic in ["esp32/network_info", f"system/status/{self.esp32_id}"]:
                retain = False

            # Crear una copia limpia del mensaje
            message_copy = message.copy() if isinstance(message, dict) else message

            # Agregar campos adicionales
            if isinstance(message_copy, dict):
                message_copy['message_id'] = f"{utime.ticks_ms()}-{random.randint(1000,9999)}"
                message_copy['timestamp'] = {
                    'value': utime.ticks_ms(),
                    'type': 'realtime'
                }

            print(f"[MQTT] Publicando en {topic} (QoS: {qos})")
            print(f"[MQTT] Mensaje: {message_copy}")

            try:
                msg_json = json.dumps(message_copy)
                self.client.publish(
                    topic.encode(),
                    msg_json.encode(),
                    qos=qos,
                    retain=retain
                )
                print("[MQTT] Publicación enviada exitosamente")
                utime.sleep_ms(100)
                return True

            except Exception as e:
                print(f"[MQTT] Error específico de publicación: {str(e)}")
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                return False

        except Exception as e:
            print(f"[MQTT] Error general en publish_event: {str(e)}")
            return False

    def reconnect(self):
        """Intenta reconexión con backoff exponencial"""
        try:
            if self.client:
                try:
                    self.client.disconnect()
                except:
                    pass
                    
            retry_count = 0
            retry_delay = 1000  # 1 segundo inicial
            
            while retry_count < self.MAX_RECONNECT_ATTEMPTS:
                try:
                    self.connect()
                    
                    # Republicar mensajes en cola
                    while self.message_queue:
                        topic, msg, qos, retain = self.message_queue.pop(0)
                        self.publish_event(topic, msg, qos, retain)
                        
                    return True
                    
                except Exception as e:
                    print(f"[MQTT] Error en intento {retry_count + 1}: {e}")
                    retry_count += 1
                    utime.sleep_ms(retry_delay)
                    retry_delay = min(retry_delay * 2, 30000)  # Máximo 30 segundos
                    
            return False
            
        except Exception as e:
            print(f"[MQTT] Error en reconnect: {e}")
            return False

    def check_msg(self):
        """Verifica mensajes pendientes"""
        try:
            if self.ensure_connection():
                return self.client.check_msg()
        except:
            pass
        return None

    def check_status_report(self):
        """Verifica si es momento de enviar reporte de estado"""
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_status_report) >= self.STATUS_REPORT_INTERVAL:
            if self.publish_status({}):
                self.last_status_report = current_time

    def _publish_network_info(self):
        """Publica información de red inicial"""
        try:
            ip_address = None
            if self.wifi_manager:
                ip_address = self.wifi_manager.current_ip
                
            info = {
                'esp32_id': self.esp32_id,
                'MAC': self.mac_address,
                'IP': ip_address,
                'status': 'AWAITING_CONFIG'
            }
            
            return self.publish_event(
                "esp32/network_info",
                info,
                qos=1,
                retain=False
            )
            
        except Exception as e:
            print(f"[MQTT] Error publicando info de red: {e}")
            return False

    def _setup_lwt(self):
        """Configura Last Will Testament"""
        if not self.esp32_id:
            return

        try:
            offline_msg = {
                'esp32_id': self.esp32_id,
                'status': 'OFFLINE',
                'timestamp': utime.ticks_ms(),
                'message_id': f"lwt-{utime.ticks_ms()}"
            }
            
            self.client.set_last_will(
                f"system/status/{self.esp32_id}",
                json.dumps(offline_msg),
                retain=False,
                qos=1
            )
            
        except Exception as e:
                print(f"[MQTT] Error en LWT: {e}")

    def publish_status(self, relay_states):
        """Publica estado de relés y sistema"""
        if not self.client_id or not self.panel_id:
            return False
            
        try:
            gc.collect()
            message = {
                'esp32_id': self.esp32_id,
                'relay_states': relay_states,
                'system': {
                    'memory_free': gc.mem_free(),
                    'memory_alloc': gc.mem_alloc(),
                    'uptime': utime.ticks_ms() // 1000
                },
                'message_id': f"{utime.ticks_ms()}-{random.randint(1000,9999)}",
                'timestamp': utime.ticks_ms()
            }
            
            return self.publish_event(
                f"clients/{self.client_id}/panels/{self.panel_id}",
                message,
                qos=1
            )
            
        except Exception as e:
            print(f"[MQTT] Error publicando estado: {e}")
            return False

    def close(self):
        """Cierra conexión MQTT y limpia recursos"""
        if self.client:
            try:
                # Publicar desconexión limpia
                self.publish_event(
                    f"system/status/{self.esp32_id}",
                    {
                        "esp32_id": self.esp32_id,
                        "status": "OFFLINE",
                        "message_id": f"{utime.ticks_ms()}-{random.randint(1000,9999)}",
                        "timestamp": utime.ticks_ms()
                    },
                    qos=1
                )
                utime.sleep_ms(500)  # Esperar envío
                self.client.disconnect()
            except:
                pass
            self.client = None
        self._processed_ids.clear()
        self.message_queue.clear()
        gc.collect()

    def unsubscribe(self, topic):
        """Desuscribe de tópico con verificación de conexión"""
        try:
            if not self.ensure_connection():
                return False
            return self.client.unsubscribe(topic.encode())
        except Exception as e:
            print(f"[MQTT] Error desuscribiendo: {e}")
            return False

    def get_mac(self):
        """Obtiene MAC address"""
        return self.mac_address

    def get_ip_address(self):
        """Obtiene IP actual"""
        return self.wifi_manager.current_ip if self.wifi_manager else None