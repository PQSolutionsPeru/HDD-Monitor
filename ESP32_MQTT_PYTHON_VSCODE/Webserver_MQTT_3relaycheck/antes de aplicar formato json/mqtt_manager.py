from umqtt.robust import MQTTClient

class MQTTManager:
    def __init__(self, broker, port, client_id, user, password):
        self.broker = broker
        self.port = port
        self.client_id = client_id
        self.user = user
        self.password = password
        self.client = None

    def connect_mqtt(self):
        if not self.client or not self.client.isconnected():
            self.client = MQTTClient(self.client_id.encode('utf-8'), self.broker, self.port, user=self.user.encode('utf-8'), password=self.password.encode('utf-8'), ssl=True)
            self.client.connect()

    def publish_event(self, topic, message):
        self.connect_mqtt()
        self.client.publish(topic, message, qos=1)
        print(f"Evento enviado al broker MQTT: {message}")
