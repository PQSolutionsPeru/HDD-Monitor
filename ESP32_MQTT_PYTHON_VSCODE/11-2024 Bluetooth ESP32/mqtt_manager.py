from umqtt.robust import MQTTClient
import json
import gc
import utime

class MQTTManager:
    def __init__(self, wifi_manager):
        """Inicializa el gestor MQTT con credenciales fijas"""
        self.wifi_manager = wifi_manager
        self.client = None
        self.message_queue = []
        
        # Credenciales fijas del broker
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 1883
        self.MQTT_CLIENT_ID = "ESP32-PQ1"  # ID fijo para todos los ESP32
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"
        
        # Control de topics
        self.esp32_id = None
        self.CONFIG_TOPIC = None
        self.topic_callbacks = {}
        
        print(f"[MQTT] Manager inicializado - Broker: {self.MQTT_BROKER}:{self.MQTT_PORT}")

    def configure(self, esp32_id):
        """Configura los tópicos específicos para este ESP32"""
        try:
            self.esp32_id = esp32_id
            self.CONFIG_TOPIC = f"esp32/config/{esp32_id}"
            print(f"[MQTT] Configurado para ESP32-{esp32_id}")
            print(f"[MQTT] Tópico de configuración: {self.CONFIG_TOPIC}")
            return self.reinitialize_client()
        except Exception as e:
            print(f"[MQTT] Error en configure: {e}")
            return False

    def reinitialize_client(self):
        """Inicializa o reinicializa el cliente MQTT"""
        try:
            if self.client:
                try:
                    self.client.disconnect()
                except:
                    pass
                
            self.client = MQTTClient(
                self.MQTT_CLIENT_ID,  # Usamos el ID fijo
                self.MQTT_BROKER,
                port=self.MQTT_PORT,
                user=self.MQTT_USER,
                password=self.MQTT_PASSWORD
            )
            
            self.client.set_callback(self._on_message)
            if self.client.connect():
                print("[MQTT] Conexión establecida")
                return True
            return False
            
        except Exception as e:
            print(f"[MQTT] Error conectando: {e}")
            return False

    def start_config_mode(self):
        """Inicia el modo de configuración vía MQTT"""
        try:
            if not self.esp32_id:
                raise ValueError("ESP32 ID no configurado")

            # Suscribirse al tópico de configuración específico
            self.subscribe(self.CONFIG_TOPIC)
            
            # Publicar disponibilidad en el tópico general
            self.publish_event(
                "esp32/available",
                {
                    "esp32_id": self.esp32_id,
                    "status": "waiting_config",
                    "timestamp": utime.ticks_ms()
                }
            )
            return True

        except Exception as e:
            print(f"[MQTT] Error iniciando modo configuración: {e}")
            return False

    def _on_message(self, topic, msg):
        """Procesa mensajes entrantes"""
        try:
            topic = topic.decode()
            msg = msg.decode()
            print(f"[MQTT] Mensaje recibido en {topic}: {msg}")
            
            if topic in self.topic_callbacks:
                self.topic_callbacks[topic](topic, msg)
                
        except Exception as e:
            print(f"[MQTT] Error procesando mensaje: {e}")

    def subscribe(self, topic, callback=None):
        """Suscribe a un tópico"""
        try:
            if callback:
                self.topic_callbacks[topic] = callback
            return self.client.subscribe(topic.encode())
        except Exception as e:
            print(f"[MQTT] Error en subscribe: {e}")
            return False

    def publish_event(self, topic, message, retain=False):
        """Publica un mensaje"""
        try:
            if isinstance(message, dict):
                message = json.dumps(message)
                
            print(f"[MQTT] Publicando en {topic}: {message}")
            return self.client.publish(
                topic.encode(),
                message.encode(),
                retain=retain
            )
        except Exception as e:
            print(f"[MQTT] Error publicando: {e}")
            self.message_queue.append((topic, message, retain))
            return False

    def process_messages(self):
        """Procesa mensajes pendientes"""
        try:
            self.client.check_msg()
            return True
        except Exception as e:
            print(f"[MQTT] Error procesando mensajes: {e}")
            return False

    def process_queue(self):
        """Procesa la cola de mensajes pendientes"""
        while self.message_queue:
            topic, message, retain = self.message_queue[0]
            if self.publish_event(topic, message, retain):
                self.message_queue.pop(0)
            else:
                break

    def check_connection(self):
        """Verifica el estado de la conexión"""
        try:
            self.client.ping()
            return True
        except:
            return False

    def close(self):
        """Cierra la conexión"""
        try:
            if self.client:
                if self.esp32_id:
                    # Publicar mensaje de desconexión
                    self.publish_event(
                        "esp32/available",
                        {
                            "esp32_id": self.esp32_id,
                            "status": "offline",
                            "timestamp": utime.ticks_ms()
                        },
                        retain=True
                    )
                self.client.disconnect()
            self.client = None
        except Exception as e:
            print(f"[MQTT] Error cerrando conexión: {e}")