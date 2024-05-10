from umqtt.robust import MQTTClient
import time

class MQTTManager:
    MQTT_BROKER = "node02.myqtthub.com"
    MQTT_PORT = 8883
    MQTT_CLIENT_ID = "ESP32-PQ1"
    MQTT_USER = "ESP32-1"
    MQTT_PASSWORD = "esp32"
    MAX_RETRIES = 3
    RETRY_DELAY = 10  # 10 seconds delay before retry

    def __init__(self):
        self.client = None

    def ensure_client(self, retry_count=0):
        if self.client is None and retry_count < self.MAX_RETRIES:
            self.client = MQTTClient(self.MQTT_CLIENT_ID.encode('utf-8'), self.MQTT_BROKER, self.MQTT_PORT,
                                     user=self.MQTT_USER.encode('utf-8'), password=self.MQTT_PASSWORD.encode('utf-8'), ssl=True)
            try:
                self.client.connect()
                print("Connected to MQTT Broker!")
            except Exception as e:
                print(f"Failed to connect to MQTT: {str(e)}")
                self.client = None
                time.sleep(self.RETRY_DELAY)
                if retry_count + 1 < self.MAX_RETRIES:
                    self.ensure_client(retry_count + 1)
        elif retry_count >= self.MAX_RETRIES:
            print("Failed to connect after several retries. Check network and broker settings.")

    def publish_event(self, topic, message):
        try:
            if self.client is None:
                print("Client not connected. Attempting to reconnect...")
                self.ensure_client()
            self.client.publish(topic, message, qos=1)
            print(f"Evento enviado al broker MQTT: {message}")
        except Exception as e:
            print(f"Failed to publish: {str(e)}")
            self.client = None
            self.ensure_client()  # Reconnect on failure
