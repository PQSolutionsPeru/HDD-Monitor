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
        self._processed_ids = set()  # Para control de duplicados
        
        # Control de reportes
        self.last_status_report = 0
        self.STATUS_REPORT_INTERVAL = 3600000  # 60 minutos
        
        # Credenciales del broker
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 8883
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"

        # Control de reconexión y timeouts
        self.last_connection_attempt = 0
        self.RECONNECT_DELAY = 15000      # 15 segundos entre intentos
        self.MQTT_CONNECT_TIMEOUT = 60000  # 60 segundos timeout para conexión
        self.MQTT_TIMEOUT = 10000         # 10 segundos para operaciones MQTT
        self.MAX_RECONNECT_ATTEMPTS = 3    # 3 intentos máximos de reconexión
        
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
            
            # Asegurar conexión antes de suscribir
            if not self.client:
                print("[MQTT] No hay conexión para suscribirse")
                return False
                    
            if callback:
                print("[MQTT] Configurando callback")
                self.client.set_callback(callback)
                
            print("[MQTT] Ejecutando suscripción...")
            topic_bytes = topic.encode() if isinstance(topic, str) else topic
            self.client.subscribe(topic_bytes)
            print("[MQTT] Suscripción exitosa")
            return True
                
        except Exception as e:
            print(f"[MQTT] Error suscribiendo: {e}")
            return False

    def configure(self, esp32_id=None):
        """Configura el dispositivo y establece conexión inicial"""
        try:
            if esp32_id:
                self.esp32_id = esp32_id
            else:
                if not self.recover_id_by_mac():
                    return False
            
            if self.connect():
                # Suscribirse al tópico de configuración
                config_topic = f"esp32/config/{self.esp32_id}"
                self.subscribe(config_topic, self._handle_config_message)
                
                # Publicar información de red
                self._publish_network_info()
                return True
            return False
            
        except Exception as e:
            print(f"[MQTT] Error en configure: {e}")
            return False

    def recover_id_by_mac(self):
        """Recupera ESP32 ID usando MAC address"""
        try:
            if not self.ensure_connection():
                return False
            
            response_topic = f"esp32/mac_response/{self.mac_address}"
            response_received = False
            
            def mac_callback(topic, msg):
                try:
                    response = json.loads(msg.decode())
                    if response.get('MAC') == self.mac_address:
                        self.esp32_id = response.get('esp32_id')
                        nonlocal response_received
                        response_received = True
                except:
                    pass
            
            self.subscribe(response_topic, mac_callback)
            
            # Publicar búsqueda MAC
            self.publish_event(
                "esp32/mac_search",
                {
                    'MAC': self.mac_address,
                    'response_topic': response_topic
                }
            )
            
            # Esperar respuesta con timeout aumentado
            start_time = utime.ticks_ms()
            while not response_received:
                if utime.ticks_diff(utime.ticks_ms(), start_time) > self.MQTT_CONNECT_TIMEOUT:
                    return False
                self.check_msg()
                utime.sleep_ms(100)
            
            return True
            
        except Exception as e:
            print(f"[MQTT] Error recuperando ID: {e}")
            return False

    def connect(self):
        """Conecta al broker MQTT con SSL"""
        try:
            print("[MQTT] Iniciando conexión...")
            gc.collect()
            utime.sleep_ms(1000)
            
            print("[MQTT] Creando cliente...")
            import ssl
            self.client = MQTTClient(
                b"ESP32-PQ1",
                self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=b"ESP32-1",
                password=b"esp32",
                keepalive=60,
                ssl=ssl
            )
            
            # Configurar LWT antes de conectar
            self._setup_lwt()
            
            print("[MQTT] Conectando...")
            self.client.connect(clean_session=True)
            print("[MQTT] Conectado exitosamente")
            
            # Publicar estado actual
            self._publish_network_info()
            
            return True
                
        except Exception as e:
            print(f"[MQTT] Error conectando: {e}")
            if self.client:
                try:
                    self.client.disconnect()
                except:
                    pass
                self.client = None
            gc.collect()
            return False

    def _handle_config_message(self, topic, msg):
        """Maneja mensajes de configuración"""
        try:
            config = json.loads(msg.decode())
            message_id = config.get('message_id')
            
            # Validar mensaje no duplicado
            if message_id and not self.validate_message_id(message_id):
                print("[MQTT] Mensaje duplicado ignorado")
                return
            
            if config.get('client_id') and config.get('panel_id'):
                self.client_id = config['client_id']
                self.panel_id = config['panel_id']
                self.operation_mode = 'RUNNING'
                
                # Confirmar recepción
                self.publish_event(
                    f"esp32/config_ack/{self.esp32_id}",
                    {
                        'esp32_id': self.esp32_id,
                        'status': 'config_received',
                        'config_id': message_id
                    },
                    qos=2
                )
                
                # Aplicar configuración y confirmar
                self.publish_event(
                    f"esp32/config_ack/{self.esp32_id}",
                    {
                        'esp32_id': self.esp32_id,
                        'status': 'config_applied',
                        'client_id': self.client_id,
                        'panel_id': self.panel_id,
                        'config_id': message_id
                    },
                    qos=2
                )
                
                # Actualizar estado
                self.publish_event(
                    f"system/status/{self.esp32_id}",
                    {
                        "esp32_id": self.esp32_id,
                        "status": "RUNNING",
                        "timestamp": utime.ticks_ms()
                    },
                    qos=2
                )
                
                # Esperar envío de mensajes
                utime.sleep_ms(2000)
                machine.reset()
                
        except Exception as e:
            print(f"[MQTT] Error procesando configuración: {e}")

    def start_normal_operation(self):
        """Inicia operación normal después de configuración"""
        if not self.client_id or not self.panel_id:
            return False
            
        try:
            # Suscribirse al tópico de operación
            operation_topic = f"clients/{self.client_id}/panels/{self.panel_id}"
            self.subscribe(operation_topic, self._handle_operation_message)
            
            self.operation_mode = 'RUNNING'
            self._publish_network_info()
            return True
            
        except Exception as e:
            print(f"[MQTT] Error iniciando operación: {e}")
            return False

    def _handle_operation_message(self, topic, msg):
        """Maneja mensajes en modo operación"""
        try:
            message = json.loads(msg.decode())
            
            if message.get('command') == 'enter_config_mode':
                self.operation_mode = 'CONFIG'
                # Desuscribirse del tópico de operación
                self.unsubscribe(f"clients/{self.client_id}/panels/{self.panel_id}")
                # Volver a modo configuración
                self.configure(self.esp32_id)
                
        except Exception as e:
            print(f"[MQTT] Error en mensaje de operación: {e}")

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

    def check_status_report(self):
        """Verifica si es momento de enviar reporte de estado"""
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_status_report) >= self.STATUS_REPORT_INTERVAL:
            if self.publish_status({}):
                self.last_status_report = current_time

    def _publish_network_info(self):
        """Publica información de red"""
        try:
            info = {
                'esp32_id': self.esp32_id,
                'MAC': self.mac_address,
                'IP': self.wifi_manager.get_ip_address(),
                'status': self.operation_mode,
                'timestamp': utime.ticks_ms()
            }
            
            return self.publish_event(
                f"esp32/network_info/{self.esp32_id}",
                info,
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
                qos=2
            )
        except Exception as e:
            print(f"[MQTT] Error en LWT: {e}")

    def validate_message_id(self, message_id: str) -> bool:
        """Valida ID de mensaje para evitar duplicados"""
        if message_id in self._processed_ids:
            return False
            
        self._processed_ids.add(message_id)
        if len(self._processed_ids) > self.MAX_PROCESSED_IDS:
            self._processed_ids.pop()
            
        return True

    def publish_event(self, topic, message, qos=2, retain=False):
        """Publica mensaje MQTT con control de errores y confirmación"""
        try:
            if not self.ensure_connection():
                if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                    self.message_queue.append((topic, message, qos, retain))
                return False

            # Generar ID único para el mensaje
            message_id = f"{utime.ticks_ms()}-{random.getrandbits(16)}"
            message['message_id'] = message_id

            # Control de QoS y retain según tipo de mensaje
            if topic.endswith('/status') or 'status' in message:
                retain = False
                qos = min(qos, 1)  # QoS máximo 1 para estados
            elif topic.startswith('esp32/config/'):
                qos = 2  # QoS 2 para config

            print(f"[MQTT] Publicando en {topic} (QoS: {qos})")
            result = self.client.publish(
                topic.encode(),
                json.dumps(message).encode(),
                qos=qos,
                retain=retain
            )

            # Esperar confirmación para QoS > 0
            if qos > 0:
                start_time = utime.ticks_ms()
                while not result.is_published():
                    if utime.ticks_diff(utime.ticks_ms(), start_time) > self.MQTT_TIMEOUT:
                        raise Exception("Timeout esperando confirmación")
                    utime.sleep_ms(100)

            return True

        except Exception as e:
            print(f"[MQTT] Error publicando: {e}")
            if len(self.message_queue) < self.MAX_QUEUE_SIZE:
                self.message_queue.append((topic, message, qos, retain))
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

    def subscribe(self, topic, callback):
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

    def unsubscribe(self, topic):
        """Desuscribe de tópico con verificación de conexión"""
        try:
            if not self.ensure_connection():
                return False
            return self.client.unsubscribe(topic.encode())
        except Exception as e:
            print(f"[MQTT] Error desuscribiendo: {e}")
            return False

    def check_msg(self):
        """Verifica mensajes pendientes con manejo de errores"""
        try:
            if self.ensure_connection():
                return self.client.check_msg()
        except:
            pass
        return None

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
                        "timestamp": utime.ticks_ms()
                    },
                    qos=2
                )
                utime.sleep_ms(500)  # Esperar envío
                self.client.disconnect()
            except:
                pass
            self.client = None
        self._processed_ids.clear()
        self.message_queue.clear()
        gc.collect()

    def start_registration(self):
        """Publica información inicial del ESP32"""
        try:
            print("[MQTT] Iniciando registro del dispositivo...")
            
            # Publicar info del dispositivo
            network_info = {
                'esp32_id': self.esp32_id,
                'MAC': self.mac_address,
                'IP': self.wifi_manager.get_ip_address(),
                'status': 'AWAITING_CONFIG',
                'lastUpdate': int(time.time() * 1000)  # Timestamp en milisegundos
            }
            
            # Publicar en tópico de registro
            self.publish_event(
                f"esp32/network_info",  # Tópico general de registro
                network_info,
                retain=False
            )
            
            print("[MQTT] Información de registro publicada")
            return True
        except Exception as e:
            print(f"[MQTT] Error en registro: {e}")
            return False