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
        self.last_ping_time = 0
        self.last_pong_time = 0
        self.ping_pending = False

        # Estados de conexión
        self.connection_healthy = False
        self.last_activity_time = 0
        self.last_heartbeat_time = 0
        
        # Límites y buffering
        self.MSG_BUFFER_SIZE = self.mqtt_config.get_buffer_size()
        self.MAX_QUEUE_SIZE = self.mqtt_config.get_queue_size()
        self.MAX_PROCESSED_IDS = self.mqtt_config.get_max_processed_ids()
        
        print("[MQTT] Manager iniciado con configuración optimizada")

    def check_connection(self):
        """Verifica si hay conexión MQTT usando check_msg periódico"""
        try:
            if not self.client:
                return False
                
            current_time = utime.ticks_ms()
            health_timeout = self.mqtt_config.get_health_timeout()
            
            # Primero verificar WiFi
            if not self.wifi_manager.check_connection():
                print("[MQTT] Sin conexión WiFi")
                return False
                
            try:
                # Intentar check_msg como prueba de conexión
                self.client.sock.setblocking(False)
                self.client.check_msg()
                
                # Actualizar timestamp de última actividad exitosa
                self.last_activity_time = current_time
                self.connection_healthy = True
                
            except OSError as e:
                print(f"[MQTT] Error de red: {e}")
                if utime.ticks_diff(current_time, getattr(self, 'last_activity_time', 0)) > health_timeout:
                    print("[MQTT] Conexión perdida - Iniciando reconexión")
                    self.connection_healthy = False
                    self.reconnect()
                    return False
                    
            except Exception as e:
                print(f"[MQTT] Error verificando mensajes: {e}")
                self.connection_healthy = False
                return False
                
            # Enviar ping periódico para mantener la conexión viva
            ping_interval = 30000  # 30 segundos
            if utime.ticks_diff(current_time, getattr(self, 'last_ping_time', 0)) >= ping_interval:
                try:
                    self.client.ping()
                    self.last_ping_time = current_time
                except:
                    pass
                    
            # Publicar heartbeat periódicamente
            heartbeat_interval = self.mqtt_config.get_status_interval()
            if utime.ticks_diff(current_time, getattr(self, 'last_heartbeat_time', 0)) >= heartbeat_interval:
                try:
                    if self.esp32_id:
                        heartbeat_msg = {
                            'esp32_id': self.esp32_id,
                            'status': 'ONLINE',
                            'timestamp': current_time,
                            'type': 'heartbeat',
                            'message_id': f"hb-{current_time}-{random.randint(1000,9999)}"
                        }
                        
                        if self.client_id and self.panel_id:
                            heartbeat_msg.update({
                                'client_id': self.client_id,
                                'panel_id': self.panel_id
                            })
                        
                        self.publish_event(
                            f"system/status/{self.esp32_id}",
                            heartbeat_msg,
                            qos=2,
                            retain=False
                        )
                        self.last_heartbeat_time = current_time
                        
                except Exception as e:
                    print(f"[MQTT] Error enviando heartbeat: {e}")
            
            return self.connection_healthy
                    
        except Exception as e:
            print(f"[MQTT] Error verificando conexión: {e}")
            self.connection_healthy = False
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

            # Limpiar cliente anterior si existe
            if self.client:
                try:
                    self.client.disconnect()
                except:
                    pass
                self.client = None
                gc.collect()
                utime.sleep_ms(1000)

            print("[MQTT] Creando cliente...")
            self.client = MQTTClient(
                client_id=self.MQTT_CLIENT_ID,
                server=self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=self.MQTT_USER,
                password=self.MQTT_PASSWORD,
                keepalive=self.mqtt_config.get_keepalive()
            )

            # Configurar LWT antes de conectar
            if self.esp32_id:
                self._setup_lwt()

            # Intentar conexión con retry
            retry_count = 0
            max_retries = 3
            while retry_count < max_retries:
                try:
                    print(f"[MQTT] Intento de conexión {retry_count + 1}/{max_retries}")
                    self.client.connect()
                    print("[MQTT] Conectado exitosamente!")
                    break
                except Exception as e:
                    print(f"[MQTT] Error en intento {retry_count + 1}: {e}")
                    retry_count += 1
                    if retry_count < max_retries:
                        utime.sleep_ms(1000 * retry_count)
                        continue
                    return False

            # Resetear estados de conexión
            self.connection_healthy = True
            self.last_ping_time = utime.ticks_ms()

            # Configurar callback y suscripción si tenemos ID
            if self.esp32_id:
                config_topic = f"esp32/config/{self.esp32_id}"
                print(f"[MQTT] Suscribiendo a: {config_topic}")
                self.client.set_callback(self._handle_config_message)
                try:
                    self.client.subscribe(config_topic.encode())
                    print("[MQTT] Suscripción exitosa")
                except Exception as e:
                    print(f"[MQTT] Error en suscripción: {e}")
                    return False

                # Publicar estado inicial
                info = {
                    'esp32_id': self.esp32_id,
                    'MAC': self.mac_address,
                    'IP': self.wifi_manager.current_ip,
                    'status': 'ONLINE',
                    'timestamp': {
                        'value': utime.ticks_ms(),
                        'type': 'realtime'
                    },
                    'message_id': f"{utime.ticks_ms()}-{random.randint(1000,9999)}"
                }
                
                print("[MQTT] Enviando info inicial...")
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
            self.client = None
            return False

    def set_relay_manager(self, relay_manager):
        """Establece la referencia al gestor de relays"""
        self.relay_manager = relay_manager

    def _handle_config_message(self, topic, msg):
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
                return

            # Validar estructura del mensaje
            required_fields = ['status', 'client_id', 'panel_id', 'mqtt', 'relays']
            missing_fields = [field for field in required_fields if field not in config]
            if missing_fields:
                print(f"[MQTT] Campos faltantes en la configuración: {missing_fields}")
                return

            # Procesar configuración
            if config['status'] == 'REGISTERED':
                print("[MQTT] Aplicando configuración...")
                
                # Guardar configuración
                self.client_id = config['client_id']
                self.panel_id = config['panel_id']
                self.relay_config = config['relays']
                
                # Configurar tópicos MQTT
                mqtt_config = config['mqtt']['topics']
                self.topics = {
                    'status': mqtt_config['status'],
                    'relays': mqtt_config['relays'],
                    'config': mqtt_config['config'],
                    'response': f"{mqtt_config['config']}/response"
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
                
                # Publicar estado actual de los relays una sola vez al iniciar
                relay_states = {}
                if hasattr(self, 'relay_manager'):
                    relay_states = self.relay_manager.get_all_states()
                
                # Enviar estado inicial después de aceptar config
                self.publish_event(
                    self.topics['relays'],
                    {
                        'esp32_id': self.esp32_id,
                        'relay_states': relay_states,
                        'timestamp': utime.ticks_ms(),
                        'message_id': f"init-{utime.ticks_ms()}-{random.randint(1000,9999)}",
                        'type': 'initial_status'
                    },
                    qos=1
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
        """Publica evento MQTT respetando límites de buffer y asegurando confirmaciones QoS"""
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
                # Establecer socket en modo bloqueante para mensajes críticos
                if qos > 0:
                    self.client.sock.setblocking(True)
                    self.client.sock.settimeout(2.0)  # 2 segundos timeout
                
                self.client.publish(
                    topic.encode(),
                    msg_str.encode(),
                    qos=qos,
                    retain=retain
                )
                
                # Esperar un momento para mensajes críticos
                if qos > 0:
                    utime.sleep_ms(100)
                
                # Restaurar socket no bloqueante después de publicación
                if qos > 0:
                    self.client.sock.setblocking(False)
                
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
        """Configura Last Will Testament con mejor manejo"""
        if not self.esp32_id:
            return

        try:
            # Configurar mensaje OFFLINE con el timestamp en 0 para que se asigne al momento de la desconexión
            offline_msg = {
                'esp32_id': self.esp32_id,
                'status': 'OFFLINE',
                'type': 'lwt',
                'client_id': self.client_id,
                'panel_id': self.panel_id,
                'message_id': f"lwt-{self.esp32_id}-{random.randint(1000,9999)}"
            }
            
            # Guardar mensaje LWT para uso posterior
            self.lwt_message = offline_msg
            self.lwt_topic = f"system/status/{self.esp32_id}"
            
            # Configurar LWT con QoS 2 para garantizar entrega
            self.client.set_last_will(
                self.lwt_topic,
                json.dumps(offline_msg),
                retain=False,
                qos=2
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