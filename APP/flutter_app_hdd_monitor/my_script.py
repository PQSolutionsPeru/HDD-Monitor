import json
import paho.mqtt.client as mqtt
from google.cloud import firestore

# Configuración del broker MQTT
MQTT_BROKER = 'node02.myqtthub.com'
MQTT_PORT = 8883
MQTT_CLIENT_ID = 'compute_engine'
MQTT_USER = 'compute_engine'
MQTT_PASSWORD = 'compute_engine'
MQTT_TOPIC = 'EMPRESA_TEST/ESP32-PQ1/eventos'

# Configuración de Firestore
db = firestore.Client(project='fir-hdd-monitor-d00de')
collection_path = 'hdd-monitor/accounts/clients/client_1/panels/panel_1/relays'

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Conectado al Broker MQTT!")
    else:
        print("Error al conectar, código de retorno:", rc)

def on_message(client, userdata, msg):
    print(f"Mensaje recibido: `{msg.payload.decode()}` desde el tema `{msg.topic}`")
    data = json.loads(msg.payload.decode())
    update_firestore(data)

def update_firestore(data):
    # Extraer información relevante del mensaje MQTT
    relay_name = data['name']
    status = data['status']
    date_time = data['date_time']

    # Referencia del documento Firestore
    doc_ref = db.document(f"{collection_path}/{relay_name}")
    
    # Actualizar Firestore
    doc_ref.set({
        'status': status,
        'date_time': date_time
    }, merge=True)
    print(f"Firestore actualizado para {relay_name} con estado {status} y fecha/hora {date_time}")

def run():
    client = mqtt.Client(client_id=MQTT_CLIENT_ID, protocol=mqtt.MQTTv311)
    client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    client.tls_set()
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(MQTT_BROKER, MQTT_PORT)
    client.subscribe(MQTT_TOPIC)
    client.loop_forever()

if __name__ == '__main__':
    run()
