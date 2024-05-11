from umqtt.robust import MQTTClient
import time

class MQTTManager:
    def __init__(self):
        self.client = None
        self.message_queue = []  # Cola para mensajes pendientes
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 8883
        self.MQTT_CLIENT_ID = "ESP32-PQ1"
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"

    def ensure_client(self):
        if self.client is None:
            self.reinitialize_client()
        else:
            try:
                self.client.ping()  # Usar ping como verificación de conexión activa
            except Exception as e:
                print("Error de conexión MQTT durante ping: ", e)
                self.reinitialize_client()

    def reinitialize_client(self):
        try:
            self.client = MQTTClient(self.MQTT_CLIENT_ID.encode('utf-8'), self.MQTT_BROKER, self.MQTT_PORT,
                                     user=self.MQTT_USER.encode('utf-8'), password=self.MQTT_PASSWORD.encode('utf-8'), ssl=True)
            self.client.connect()
            print("Conectado al broker MQTT después de la reconexión.")
        except Exception as e:
            print(f"No se pudo conectar al broker MQTT: {e}")
            self.client = None

    def publish_event(self, topic, message):
        try:
            self.ensure_client()
            if self.client:
                self.client.publish(topic, message, qos=1)
                print(f"Evento enviado al broker MQTT: {message}")
        except Exception as e:
            print(f"Fallo al publicar debido a: {e}")
            self.message_queue.append({'topic': topic, 'message': message})  # Encolar mensaje si la publicación falla
            self.client = None

