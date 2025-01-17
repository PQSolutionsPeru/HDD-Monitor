from umqtt.robust import MQTTClient
import json
import gc
import utime
import machine
import ubinascii
import random
from config.mqtt_config import MQTTConfig
from config.device_pool_config import DevicePoolConfig

class MQTTManager:
    def __init__(self, wifi_manager):
        """Inicializa el gestor MQTT con configuración"""
        self.wifi_manager = wifi_manager
        self.mqtt_config = MQTTConfig()
        self.device_pool = DevicePoolConfig()
        self.client = None
        self.esp32_id = None
        self.mac_address = ubinascii.hexlify(machine.unique_id()).decode()
        
        # Obtener dispositivo del pool
        device = self.device_pool.get_device_by_mac(self.mac_address) or self.device_pool.assign_device(self.mac_address)
        if device:
            print(f"[MQTT] Usando device del pool: {device['client_id']}")
            self.MQTT_BROKER = self.mqtt_config.get_broker_config()['broker']
            self.MQTT_PORT = self.mqtt_config.get_broker_config()['port']
            self.MQTT_CLIENT_ID = device['client_id']
            self.MQTT_USER = device['user']
            self.MQTT_PASSWORD = device['password']
        else:
            print("[MQTT] Error: No hay dispositivos disponibles en el pool")
            raise Exception("No MQTT devices available")
        
        # Información de operación
        self.client_id = None
        self.panel_id = None
        self.operation_mode = 'CONFIG'
        self.message_queue = []
        self._processed_ids = set()
        
        # Control de reportes y timeouts
        self.last_status_report = 0
        self.STATUS_REPORT_INTERVAL = self.mqtt_config.get_status_interval()
        self.RECONNECT_DELAY = self.mqtt_config.get_reconnect_delay()
        self.last_connection_attempt = 0
        
        # Límites y buffering
        self.MSG_BUFFER_SIZE = self.mqtt_config.get_buffer_size()
        self.MAX_QUEUE_SIZE = self.mqtt_config.get_queue_size()
        self.MAX_PROCESSED_IDS = self.mqtt_config.get_max_processed_ids()
        
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

    def connect(self):
        """Conecta al broker MQTT con manejo de errores mejorado"""
        try:
            print("[MQTT] Iniciando conexión...")
            print(f"[MQTT] - Broker: {self.MQTT_BROKER}")
            print(f"[MQTT] - Puerto: {self.MQTT_PORT}")
            print(f"[MQTT] - Cliente ID: {self.MQTT_CLIENT_ID}")
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
                keepalive=self.mqtt_config.get_keepalive()
            )

            print("[MQTT] Intentando conexión...")
            self.client.connect()
            print("[MQTT] Conectado exitosamente!")

            # Configurar callback y suscripción si tenemos ID
            if self.esp32_id:
                config_topic = f"esp32/config/{self.esp32_id}"
                print(f"[MQTT] Suscribiendo a: {config_topic}")
                self.client.set_callback(self._handle_config_message)
                print(f"[MQTT] Intentando suscribirse a: {config_topic}")
                self.client.subscribe(config_topic.encode())
                print("[MQTT] Ejecutando suscripción...")
                print("[MQTT] Suscripción exitosa")

                info = {
                    'esp32_id': self.esp32_id,
                    'MAC': self.mac_address,
                    'IP': self.wifi_manager.current_ip,
                    'status': 'AWAITING_CONFIG',
                    'timestamp': {
                        'value': utime.ticks_ms(),
                        'type': 'realtime'
                    },
                    'message_id': f"{utime.ticks_ms()}-{random.randint(1000,9999)}"
                }
                
                print(f"[MQTT] Enviando info inicial: {info}")

                self._setup_lwt()
                
                # Publicar estado inicial
                result = self.publish_event(
                    "esp32/network_info",
                    info,
                    qos=1,
                    retain=False
                )
                print(f"[MQTT] Resultado envío info inicial: {'Exitoso' if result else 'Fallido'}")

            print("[MQTT] Setup completed successfully")
            return True

        except Exception as e:
            print(f"[MQTT] Error en conexión: {str(e)}")
            import sys
            sys.print_exception(e)
            return False

    def _handle_config_message(self, topic, msg):
        """Maneja mensajes de configuración desde la VM"""
        try:
            print(f"[MQTT] Mensaje de configuración recibido en: {topic}")
            print(f"[MQTT] Contenido del mensaje: {msg}")
            
            # Decodificar mensaje
            try:
                msg_str = msg.decode()
                print(f"[MQTT] Mensaje decodificado: {msg_str}")
                config = json.loads(msg_str)
                print(f"[MQTT] Configuración parseada exitosamente")
            except Exception as e:
                print(f"[MQTT] Error decodificando mensaje: {e}")
                print(f"[MQTT] Tipo de mensaje: {type(msg)}")
                return

            # Validar estructura del mensaje
            required_fields = ['status', 'client_id', 'panel_id', 'mqtt', 'relays']
            missing_fields = []
            for field in required_fields:
                if field not in config:
                    missing_fields.append(field)
                    print(f"[MQTT] Campo faltante: {field}")
            
            if missing_fields:
                print(f"[MQTT] Campos faltantes en la configuración: {missing_fields}")
                return

            # Procesar configuración
            if config['status'] == 'REGISTERED':
                print("[MQTT] Aplicando configuración...")
                print(f"[MQTT] Cliente: {config['client_id']}")
                print(f"[MQTT] Panel: {config['panel_id']}")
                
                # Guardar configuración
                self.client_id = config['client_id']
                self.panel_id = config['panel_id']
                
                # Guardar configuración de relay
                self.relay_config = config['relays']
                print(f"[MQTT] Configuración de relays: {self.relay_config}")
                
                # Configurar tópicos MQTT
                base_topic = config['mqtt']['base_topic']
                self.topics = {
                    'status': f"{base_topic}/status",
                    'relays': f"{base_topic}/relays",
                    'config': f"esp32/config/{self.esp32_id}",
                    'response': f"esp32/config/{self.esp32_id}/response"
                }
                print(f"[MQTT] Tópicos configurados: {self.topics}")
                
                # Enviar confirmación
                self.publish_event(
                    self.topics['response'],
                    {
                        'esp32_id': self.esp32_id,
                        'status': 'CONFIG_ACCEPTED',
                        'client_id': self.client_id,
                        'panel_id': self.panel_id,
                        'timestamp': utime.ticks_ms(),
                        'message_id': f"{utime.ticks_ms()}-{random.randint(1000,9999)}"
                    }
                )
                
                print("[MQTT] Configuración aplicada exitosamente")
                self.operation_mode = 'RUNNING'
            else:
                print(f"[MQTT] Estado no reconocido: {config['status']}")

        except Exception as e:
            print(f"[MQTT] Error procesando configuración: {e}")
            import sys
            sys.print_exception(e)

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

    def publish_event(self, topic, message, qos=1, retain=False):
        """Publica evento MQTT respetando límites de buffer"""
        try:
            print(f"[MQTT] Intentando publicar en tópico: {topic}")
            print(f"[MQTT] Mensaje a enviar: {message}")
            
            msg_str = json.dumps(message)
            if len(msg_str) > self.MSG_BUFFER_SIZE:
                print(f"[MQTT] Mensaje excede el tamaño máximo: {len(msg_str)} > {self.MSG_BUFFER_SIZE}")
                return False

            if not self.ensure_connection():
                print("[MQTT] No hay conexión disponible para publicar")
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    print("[MQTT] Agregando mensaje a la cola")
                    self.message_queue.append((topic, message, qos, retain))
                return False

            try:
                print("[MQTT] Enviando mensaje...")
                self.client.publish(
                    topic.encode(),
                    msg_str.encode(),
                    qos=qos,
                    retain=retain
                )
                print("[MQTT] Mensaje enviado exitosamente")
                
                # Actualizar último reporte si es un mensaje de estado
                if "status" in message or "relay_states" in message:
                    self.last_status_report = utime.ticks_ms()
                    print(f"[MQTT] Actualizado último reporte de estado: {self.last_status_report}")
                
                return True
                
            except Exception as e:
                print(f"[MQTT] Error de publicación: {e}")
                print("[MQTT] Intentando agregar a cola de mensajes")
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                    print("[MQTT] Mensaje agregado a la cola")
                return False

        except Exception as e:
            print(f"[MQTT] Error en publish_event: {e}")
            import sys
            sys.print_exception(e)
            return False

    def check_msg(self):
        """Verifica mensajes pendientes con mejor manejo de errores"""
        try:
            if not self.ensure_connection():
                print("[MQTT] Sin conexión al verificar mensajes")
                return False
                
            self.client.sock.setblocking(False)
            
            try:
                result = self.client.check_msg()
                return result
            except OSError as e:
                print(f"[MQTT] Error de red en check_msg: {e}")
                self.reconnect()
                return False
                
        except Exception as e:
            print(f"[MQTT] Error crítico en check_msg: {e}")
            self.reconnect()
            return False

    def check_status_report(self):
        """Verifica si es momento de enviar reporte de estado"""
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_status_report) >= self.STATUS_REPORT_INTERVAL:
            if self.publish_status({}):
                self.last_status_report = current_time

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
            
            # Incluir IDs si están disponibles
            if self.client_id and self.panel_id:
                offline_msg.update({
                    'client_id': self.client_id,
                    'panel_id': self.panel_id
                })
            
            self.client.set_last_will(
                f"system/status/{self.esp32_id}",
                json.dumps(offline_msg),
                retain=False,
                qos=1
            )
            
        except Exception as e:
            print(f"[MQTT] Error en LWT: {e}")

    def reconnect(self):
        """Intenta reconexión con backoff exponencial mejorado"""
        if len(self.message_queue) >= self.MAX_QUEUE_SIZE:
            print("[MQTT] Cola de mensajes llena, limpiando mensajes antiguos")
            self.message_queue = self.message_queue[-self.MAX_QUEUE_SIZE:]
            
        try:
            if self.client:
                try:
                    self.client.disconnect()
                except:
                    pass
                    
            retry_count = 0
            retry_delay = self.mqtt_config.get_initial_retry_delay()
            max_retry_delay = self.mqtt_config.get_max_retry_delay()
            
            while retry_count < self.mqtt_config.get_max_retries():
                try:
                    print(f"[MQTT] Intento de reconexión {retry_count + 1}")
                    if self.connect():
                        while self.message_queue:
                            topic, msg, qos, retain = self.message_queue.pop(0)
                            if not self.publish_event(topic, msg, qos, retain):
                                self.message_queue.insert(0, (topic, msg, qos, retain))
                                break
                        return True
                        
                except Exception as e:
                    print(f"[MQTT] Error en intento {retry_count + 1}: {e}")
                    
                retry_count += 1
                utime.sleep_ms(retry_delay)
                retry_delay = min(retry_delay * 2, max_retry_delay)
                
            print("[MQTT] Máximo de reintentos alcanzado")
            return False
                
        except Exception as e:
            print(f"[MQTT] Error en reconnect: {e}")
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
            
        # Liberar dispositivo del pool
        self.device_pool.release_device(self.mac_address)
        
        self._processed_ids.clear()
        self.message_queue.clear()
        gc.collect()

    def get_mac(self):
        """Obtiene MAC address"""
        return self.mac_address

    def get_ip_address(self):
        """Obtiene IP actual"""
        return self.wifi_manager.current_ip if self.wifi_manager else None