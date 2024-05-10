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
    """ Callback llamado al conectar con el broker MQTT.
        Se suscribe al tópico si la conexión es exitosa.
    """
    if rc == 0:
        print("Conectado al Broker MQTT!")
        client.subscribe(MQTT_TOPIC)  # Suscripción al tópico tras una reconexión exitosa.
    else:
        print("Error al conectar, código de retorno:", rc)

def on_message(client, userdata, msg):
    """ Callback llamado al recibir un mensaje del broker MQTT.
        Decodifica el mensaje JSON y actualiza Firestore.
    """
    try:
        print(f"Mensaje recibido: `{msg.payload.decode()}` desde el tema `{msg.topic}`")
        data = json.loads(msg.payload.decode())
        update_firestore(data)
    except json.JSONDecodeError as e:
        print(f"Error al decodificar JSON: {e}")

def update_firestore(data):
    """ Actualiza Firestore con los datos recibidos del mensaje MQTT.
    """
    try:
        relay_name = data['name']
        status = data['status']
        date_time = data['date_time']
        doc_ref = db.document(f"{collection_path}/{relay_name}")
        doc_ref.set({
            'status': status,
            'date_time': date_time
        }, merge=True)
        print(f"Firestore actualizado para {relay_name} con estado {status} y fecha/hora {date_time}")
    except Exception as e:
        print(f"Error al actualizar Firestore: {e}")

def run():
    """ Crea y ejecuta el cliente MQTT, gestionando la conexión y el loop principal.
    """
    client = mqtt.Client(client_id=MQTT_CLIENT_ID, protocol=mqtt.MQTTv311)
    client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    client.tls_set()
    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(MQTT_BROKER, MQTT_PORT)
        client.loop_forever()
    except Exception as e:
        print(f"Error al conectar o durante MQTT loop: {e}")
        client.disconnect()
        raise

if __name__ == '__main__':
    run()
