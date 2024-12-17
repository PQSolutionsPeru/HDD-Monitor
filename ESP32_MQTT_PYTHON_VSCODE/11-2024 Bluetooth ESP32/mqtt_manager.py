from umqtt.robust import MQTTClient
import json
import gc
import utime
import machine
import ubinascii

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
        
        # Control de reportes
        self.last_status_report = 0
        self.STATUS_REPORT_INTERVAL = 1800000  # 30 minutos
        
        # Credenciales compartidas del broker
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 8883
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"

        # Control de reconexión
        self.last_connection_attempt = 0
        self.RECONNECT_DELAY = 5000  # 5 segundos entre intentos

        # Reducir tamaño de buffer MQTT
        self.MSG_BUFFER_SIZE = 512  # Reducir de 1024 a 512 bytes
        
        print("[MQTT] Manager iniciado")

    def check_connection(self):
        """Verifica si hay conexión MQTT"""
        try:
            if not self.client:
                return False
                
            try:
                # Si el cliente existe, intenta usarlo
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
            # Si hay cliente, verificar conexión
            if self.client:
                return True
                
            # Si no hay cliente, intentar nueva conexión
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
            
            # Esperar respuesta con timeout
            start_time = utime.ticks_ms()
            while not response_received:
                if utime.ticks_diff(utime.ticks_ms(), start_time) > 10000:
                    return False
                self.check_msg()
                utime.sleep_ms(100)
            
            return True
            
        except Exception as e:
            print(f"[MQTT] Error recuperando ID: {e}")
            return False

    def connect(self):
        """Conecta al broker MQTT usando credenciales compartidas"""
        try:
            print("[MQTT] Iniciando conexión...")
            gc.collect()
            utime.sleep_ms(500)
            
            print("[MQTT] Creando cliente...")
            import ssl
            self.client = MQTTClient(
                b"ESP32-PQ1",  # Client ID fijo y en bytes
                self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=b"ESP32-1",  # Usuario en bytes
                password=b"esp32",  # Password en bytes
                keepalive=30,
                ssl=ssl
            )
            
            print("[MQTT] Conectando...")
            self.client.connect(clean_session=True)
            print("[MQTT] Conectado exitosamente")
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
            
            if config.get('client_id') and config.get('panel_id'):
                self.client_id = config['client_id']
                self.panel_id = config['panel_id']
                self.operation_mode = 'RUNNING'
                
                # Confirmar configuración
                self.publish_event(
                    f"esp32/config/{self.esp32_id}",
                    {
                        'esp32_id': self.esp32_id,
                        'status': 'config_applied',
                        'client_id': self.client_id,
                        'panel_id': self.panel_id
                    },
                    retain=True
                )
                
                # Desuscribirse del tópico de configuración
                self.unsubscribe(f"esp32/config/{self.esp32_id}")
                
                # Esperar que se envíe el mensaje
                utime.sleep_ms(1000)
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
                retain=True,
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
                retain=True
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
                'timestamp': utime.ticks_ms()
            }
            
            self.client.set_last_will(
                f"system/status/{self.esp32_id}",
                json.dumps(offline_msg),
                retain=True,
                qos=1
            )
        except Exception as e:
            print(f"[MQTT] Error en LWT: {e}")

    def publish_event(self, topic, message, retain=False, qos=0):
        """Publica mensaje MQTT con reintentos y cola"""
        try:
            print(f"[MQTT] Intentando publicar en tópico: {topic}")
            
            # Verificar conexión
            if not self.ensure_connection():
                print("[MQTT] Sin conexión, encolando mensaje")
                self.message_queue.append((topic, message, retain, qos))
                return False

            # Procesar mensaje
            if isinstance(message, dict):
                # Añadir ID del dispositivo
                if self.esp32_id:
                    message['esp32_id'] = self.esp32_id
                    
                # Añadir timestamp solo si está sincronizado
                if hasattr(self, 'time_manager') and self.time_manager and self.time_manager.is_synced:
                    message['timestamp'] = self.time_manager.get_timestamp()
                    message['datetime'] = self.time_manager.get_datetime_str()
                
                # Convertir a JSON
                message = json.dumps(message)

            # Convertir a bytes si es necesario
            topic = topic.encode() if isinstance(topic, str) else topic
            message = message.encode() if isinstance(message, str) else message
            
            # Publicar mensaje
            print("[MQTT] Publicando mensaje...")
            self.client.publish(topic, message, retain=retain, qos=qos)
            print("[MQTT] Mensaje publicado exitosamente")
            
            # Procesar cola de mensajes pendientes
            while self.message_queue and self.ensure_connection():
                print("[MQTT] Procesando mensaje encolado...")
                t, m, r, q = self.message_queue.pop(0)
                # Llamada recursiva para procesar mensaje encolado
                self.publish_event(t, m, r, q)
            
            return True
                
        except Exception as e:
            print(f"[MQTT] Error publicando: {e}")
            # Si hay error, encolar el mensaje actual
            self.message_queue.append((topic, message, retain, qos))
            gc.collect()  # Limpiar memoria
            return False

    def subscribe(self, topic, callback=None):
        """Suscribe a tópico con verificación de conexión"""
        try:
            print(f"[MQTT] Intentando suscribirse a: {topic}")
            
            # Asegurar conexión antes de suscribir
            if not self.client:
                print("[MQTT] No hay conexión para suscribirse")
                return False
            
            # Si no hay callback, usar uno predeterminado que procese los mensajes
            if callback is None:
                def default_callback(topic, msg):
                    try:
                        message = json.loads(msg.decode())
                        print(f"[MQTT] Mensaje recibido en {topic}: {message}")
                        
                        # Si es mensaje de configuración
                        if topic.startswith("esp32/config/"):
                            if message.get('client_id') and message.get('panel_name'):
                                print("[MQTT] Configuración de panel recibida")
                                # Procesar configuración...
                                return
                                
                        # Otros tipos de mensajes aquí...
                        
                    except Exception as e:
                        print(f"[MQTT] Error procesando mensaje: {e}")
                
                callback = default_callback
            
            print("[MQTT] Configurando callback")
            self.client.set_callback(callback)
            
            print("[MQTT] Ejecutando suscripción...")
            topic_bytes = topic.encode() if isinstance(topic, str) else topic
            result = self.client.subscribe(topic_bytes)
            
            if result in (0, None):  # 0 o None indican éxito en diferentes implementaciones
                print("[MQTT] Suscripción exitosa")
                # Iniciar proceso de registro si es la primera suscripción
                if topic.startswith("esp32/config"):
                    self.start_registration()
                return True
            else:
                print(f"[MQTT] Error en suscripción: {result}")
                return False
                
        except Exception as e:
            print(f"[MQTT] Error suscribiendo: {e}")
            return False

    def start_registration(self):
        """Publica información inicial del ESP32"""
        try:
            print("[MQTT] Iniciando registro del dispositivo...")
            
            # Publicar info del dispositivo
            network_info = {
                'MAC': self.mac_address,
                'IP': self.wifi_manager.get_ip_address(),
                'status': 'CONFIG',
                'timestamp': utime.ticks_ms()
            }
            
            # Publicar en tópico de registro
            self.publish_event(
                f"esp32/network_info",  # Tópico general de registro
                network_info,
                retain=True
            )
            
            print("[MQTT] Información de registro publicada")
            return True
        except Exception as e:
            print(f"[MQTT] Error en registro: {e}")
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
                self.client.disconnect()
            except:
                pass
            self.client = None
        gc.collect()