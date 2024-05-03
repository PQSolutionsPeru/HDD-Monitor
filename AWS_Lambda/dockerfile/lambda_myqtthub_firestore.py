import json
import os
from google.cloud import firestore
from google.oauth2 import service_account

def lambda_handler(event, context):
    # Parsea el mensaje de entrada, que se espera sea un string JSON
    message = json.loads(event['body'])
    name = message['name']
    date_time = message['date_time']
    status = message['status']

    # Carga las credenciales desde la variable de entorno
    credentials_info = json.loads(os.environ['FIRESTOREDB_CREDENTIALS_JSON'])
    credentials = service_account.Credentials.from_service_account_info(credentials_info)

    # Inicializa el cliente de Firestore
    db = firestore.Client(credentials=credentials, project=credentials_info['fir-hdd-monitor-d00de'])

    # Ruta del documento a actualizar
    document_path = f'hdd-monitor/accounts/clients/client_1/panels/panel_1/relays/{name}'

    # Referencia al documento en Firestore
    doc_ref = db.document(document_path)

    # Actualiza el documento con el status y la fecha/hora recibidos
    doc_ref.set({
        'status': status,
        'date_time': date_time
    })

    return {
        'statusCode': 200,
        'body': json.dumps("Firestore document updated successfully")
    }
