from umqtt.robust import MQTTClient # type: ignore
import json

class MQTTManager:
    def __init__(self, broker, port, client_id, user, password, wifi_manager):
        self.broker = broker
        self.port = port
        self.client_id = client_id
        self.user = user
        self.password = password
        self.client = None
        self.connected = False
        self.wifi_manager = wifi_manager  # Añadir como dependencia
        self.ensure_client()

    def ensure_client(self):
        if self.client is None:
            self.client = MQTTClient(self.client_id.encode('utf-8'), self.broker, self.port,
                                     user=self.user.encode('utf-8'), password=self.password.encode('utf-8'), ssl=True)

    def connect_mqtt(self):
        try:
            self.ensure_client()
            self.client.connect()
            self.connected = True
            print("Connected to MQTT Broker!")
        except Exception as e:
            self.connected = False
            print(f"Failed to connect to MQTT: {str(e)}")
            self.client = None

    def publish_event(self, topic, message):
        if not self.connected:
            self.connect_mqtt()
        if self.connected:
            try:
                self.client.publish(topic, message, qos=1)
                print(f"Evento enviado al broker MQTT: {message}")
            except Exception as e:
                print(f"Failed to publish: {str(e)}")
                self.handle_reconnect()

    def handle_reconnect(self):
        self.disconnect()
        self.connect_mqtt()

    def disconnect(self):
        if self.client:
            try:
                self.client.disconnect()
            except Exception:
                pass
        self.client = None
        self.connected = False

    def publish_status(self, description, status):
        message = {
            "date_time": self.wifi_manager.get_formatted_time(),
            "description": description,
            "status": status
        }
        self.publish_event(f"EMPRESA_TEST/{self.client_id}/estado", json.dumps(message))
