import json
import os
from paho.mqtt import client as mqtt_client
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

# Configuración de MQTT
broker = 'node02.myqtthub.com'
port = 8883
topic = "tu_topic_aqui"
username = 'PQSolutions'
password = 'Quixy35L4M3J0R'

# Configuración de Firestore
cred = credentials.Certificate("P:\PPP\PQSolutions\APP\KEYS APP\fir-hdd-monitor-firebase-adminsdk-l8ui2-f517870b2b.json")
firebase_admin.initialize_app(cred, {
    'projectId': 'fir-hdd-monitor-d00de',
})

db = firestore.client()

def connect_mqtt() -> mqtt_client:
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            print("Connected to MQTT Broker!")
            client.subscribe(topic)
        else:
            print("Failed to connect, return code %d\n", rc)

    client = mqtt_client.Client("LambdaClient")
    client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.tls_set()  # Configurar TLS/SSL si es necesario
    client.connect(broker, port)
    return client

def on_message(client, userdata, msg):
    print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
    data = json.loads(msg.payload.decode())
    save_to_firestore(data)

def save_to_firestore(data):
    # Asigna los valores del mensaje MQTT a los campos del documento de Firestore
    doc_ref = db.document('/hdd-monitor/accounts/clients/client_1/panels/panel_1/relays/relay_1')
    doc_ref.set(data, merge=True)

def handler(event, context):
    client = connect_mqtt()
    client.on_message = on_message
    client.loop_forever()

    return {
        'statusCode': 200,
        'body': json.dumps('Data processed successfully!')
    }




