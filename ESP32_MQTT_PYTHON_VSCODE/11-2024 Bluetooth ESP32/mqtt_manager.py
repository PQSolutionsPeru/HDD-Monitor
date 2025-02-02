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
        self.last_activity_time = 0
        self.last_heartbeat_time = 0
        
        # Límites y buffering
        self.MSG_BUFFER_SIZE = self.mqtt_config.get_buffer_size()
        self.MAX_QUEUE_SIZE = self.mqtt_config.get_queue_size()
        self.MAX_PROCESSED_IDS = self.mqtt_config.get_max_processed_ids()
        
        print("[MQTT] Manager iniciado con configuración optimizada")

    def connect(self):
        """Conecta al broker MQTT"""
        try:
            if not self.wifi_manager.check_connection():
                return False
                
            print("[MQTT] Iniciando conexión...")
            print(f"[MQTT] - Broker: {self.MQTT_BROKER}")
            print(f"[MQTT] - Puerto: {self.MQTT_PORT}")
            print(f"[MQTT] - Cliente ID: {self.MQTT_CLIENT_ID}")
            print("[MQTT] Creando cliente...")
            
            self.client = MQTTClient(
                client_id=self.MQTT_CLIENT_ID,
                server=self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=self.MQTT_USER,
                password=self.MQTT_PASSWORD,
                keepalive=60
            )
            
            if self.esp32_id:
                self._setup_lwt()

            self.client.connect()
            print("[MQTT] Conectado exitosamente!")
            
            if self.esp32_id:
                config_topic = f"esp32/config/{self.esp32_id}"
                print(f"[MQTT] Suscribiendo a: {config_topic}")
                self.client.set_callback(self._handle_config_message)
                self.client.subscribe(config_topic.encode())
            
            return True
            
        except Exception as e:
            print(f"[MQTT] Error en conexión: {e}")
            return False

    def _handle_config_message(self, topic, msg):
        """Maneja mensajes de configuración sin bloqueos"""
        try:
            print(f"[MQTT] Mensaje de configuración recibido en: {topic}")
            msg_str = msg.decode()
            config = json.loads(msg_str)
            print("[MQTT] Configuración parseada exitosamente")

            if not all(field in config for field in ['status', 'client_id', 'panel_id', 'mqtt', 'relays']):
                print("[MQTT] Configuración incompleta")
                return

            if config['status'] != 'REGISTERED':
                print(f"[MQTT] Estado no reconocido: {config['status']}")
                return

            # Aplicar configuración
            self.client_id = config['client_id']
            self.panel_id = config['panel_id']
            self.relay_config = config['relays']
            
            mqtt_config = config['mqtt']['topics']
            self.topics = {
                'status': mqtt_config['status'],
                'relays': mqtt_config['relays'],
                'config': mqtt_config['config'],
                'response': f"{mqtt_config['config']}/response"
            }

            # Preparar respuesta compatible con VM
            response_msg = {
                'esp32_id': self.esp32_id,
                'status': 'CONFIG_ACCEPTED',
                'client_id': self.client_id,
                'panel_id': self.panel_id,
                'timestamp': utime.ticks_ms(),
                'message_id': f"{utime.ticks_ms()}-{random.randint(1000,9999)}"
            }

            # Publicar sin espera de confirmación pero con QoS 1
            self.client.publish(
                self.topics['response'],
                json.dumps(response_msg).encode(),
                qos=1,
                retain=False
            )

            print("[MQTT] Configuración aplicada")
            self.operation_mode = 'RUNNING'
            return True
            
        except Exception as e:
            print(f"[MQTT] Error en configuración: {e}")
            return False

    def publish_event(self, topic, message, qos=1, retain=False):
        """Publica evento MQTT"""
        try:
            print(f"[MQTT] Intentando publicar en tópico: {topic}")
            
            msg_str = json.dumps(message)
            if len(msg_str) > self.MSG_BUFFER_SIZE:
                print(f"[MQTT] Mensaje excede el tamaño máximo: {len(msg_str)}")
                return False

            if not self.ensure_connection():
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                return False

            try:
                print("[MQTT] Preparando publicación...")
                self.client.publish(
                    topic.encode(),
                    msg_str.encode(),
                    qos=qos,
                    retain=retain
                )
                print("[MQTT] Publicación completada")
                return True
                
            except Exception as e:
                print(f"[MQTT] Error en publicación: {str(e)}")
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                return False

        except Exception as e:
            print(f"[MQTT] Error crítico: {str(e)}")
            return False

    def check_connection(self):
        """Verifica conexión MQTT"""
        try:
            if not self.client:
                return False
                    
            if not self.wifi_manager.check_connection():
                return False
                    
            try:
                self.client.check_msg()
                self.last_activity_time = utime.ticks_ms()
                return True
                    
            except Exception as e:
                print(f"[MQTT] Error verificando conexión: {e}")
                return False
                
        except Exception as e:
            print(f"[MQTT] Error en check_connection: {e}")
            return False

    def ensure_connection(self):
        """Asegura conexión MQTT"""
        if not self.wifi_manager.check_connection():
            return False
            
        try:
            if self.client:
                return True
                
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_connection_attempt) < self.RECONNECT_DELAY:
                return False
                
            self.last_connection_attempt = current_time
            return self.connect()
                
        except Exception as e:
            print(f"[MQTT] Error en ensure_connection: {e}")
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
                'message_id': f"lwt-{utime.ticks_ms()}-{random.randint(1000,9999)}"
            }
            
            if self.client_id and self.panel_id:
                offline_msg.update({
                    'client_id': self.client_id,
                    'panel_id': self.panel_id
                })
            
            lwt_topic = f"system/status/{self.esp32_id}"
            print(f"[MQTT] Configurando LWT en tópico: {lwt_topic}")
            
            self.client.set_last_will(
                lwt_topic,
                json.dumps(offline_msg),
                retain=False,
                qos=1
            )
            print("[MQTT] LWT configurado exitosamente")
                
        except Exception as e:
            print(f"[MQTT] Error en LWT: {e}")
            raise

    def subscribe(self, topic, callback=None):
        """Suscribe a tópico"""
        try:
            print(f"[MQTT] Intentando suscribirse a: {topic}")
            
            if not self.ensure_connection():
                return False
                
            if callback:
                self.client.set_callback(callback)
                
            self.client.subscribe(topic.encode())
            return True
                
        except Exception as e:
            print(f"[MQTT] Error suscribiendo: {e}")
            return False

    def check_msg(self):
        """Verifica mensajes pendientes"""
        try:
            if not self.ensure_connection():
                return False
                
            try:
                return self.client.check_msg()
            except OSError as e:
                print(f"[MQTT] Error de red en check_msg: {e}")
                return False
                
        except Exception as e:
            print(f"[MQTT] Error en check_msg: {e}")
            return False

    def close(self):
        """Cierra conexión y limpia recursos"""
        if self.client:
            try:
                self.client.publish(
                    f"system/status/{self.esp32_id}",
                    json.dumps({
                        "esp32_id": self.esp32_id,
                        "status": "OFFLINE",
                        "message_id": f"{utime.ticks_ms()}-{random.randint(1000,9999)}",
                        "timestamp": utime.ticks_ms()
                    }).encode(),
                    qos=1
                )
                utime.sleep_ms(100)
                self.client.disconnect()
            except:
                pass
            self.client = None
            
        self.device_pool.release_device(self.mac_address)
        self._processed_ids.clear()
        self.message_queue.clear()
        gc.collect()