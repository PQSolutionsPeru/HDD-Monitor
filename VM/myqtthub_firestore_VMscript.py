import json
import paho.mqtt.client as mqtt
from google.cloud import firestore
import ssl
import time
from datetime import datetime
from typing import Optional, Dict, Any
import logging

# Configuración de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

# Configuración MQTT
MQTT_BROKER = 'node02.myqtthub.com'
MQTT_PORT = 8883
MQTT_CLIENT_ID = 'compute_engine'
MQTT_USER = 'compute_engine'
MQTT_PASSWORD = 'compute_engine'

# Configuración de Firestore
db = firestore.Client(project='fir-hdd-monitor-d00de')

def on_connect(client, userdata, flags, rc):
    """Callback de conexión MQTT"""
    if rc == 0:
        logging.info("Conectado al Broker MQTT!")
        # Suscribirse a todos los tópicos relevantes
        topics = [
            ("esp32/mac_search", 1),
            ("esp32/network_info", 1),
            ("esp32/config/+", 1),
            ("esp32/panel_assignment", 1),
            ("clients/+/panels/+", 1),
            ("system/status/+", 1)
        ]
        for topic, qos in topics:
            client.subscribe(topic, qos=qos)
            logging.info(f"Suscrito a: {topic}")
    else:
        logging.error(f"Error al conectar, código: {rc}")

def normalize_mac(mac: str) -> str:
    """Normaliza formato MAC address"""
    return mac.upper().replace(":", "").replace("-", "")

def handle_mac_search(client, payload: Dict[str, Any]):
    """Maneja búsqueda de ESP32 por MAC"""
    try:
        mac = payload.get('MAC')
        response_topic = payload.get('response_topic')

        if not mac or not response_topic:
            logging.warning("MAC o response_topic faltante")
            return

        esp32_ref = db.collection('hdd-monitor/esp32/registered')
        query = esp32_ref.where('MAC', '==', mac).limit(1).get()

        for doc in query:
            response = {
                'MAC': mac,
                'esp32_id': doc.id
            }
            client.publish(response_topic, json.dumps(response), qos=1)
            logging.info(f"MAC encontrada: {mac}, ESP32 ID: {doc.id}")
            return

        logging.info(f"MAC no encontrada: {mac}")

    except Exception as e:
        logging.error(f"Error en búsqueda MAC: {e}", exc_info=True)

def handle_network_info(client, payload: Dict[str, Any]):
    """Registra o actualiza información de red del ESP32 y envía configuración"""
    try:
        logging.debug(f"Procesando network_info: {payload}")
        mac = normalize_mac(payload.get('MAC', ''))
        ip = payload.get('IP')
        esp32_id = payload.get('esp32_id')
        status = payload.get('status')

        if not all([mac, ip, esp32_id]):
            logging.error("Información faltante en payload")
            return

        esp32_ref = db.collection('hdd-monitor/esp32/registered')
        esp32_doc = esp32_ref.document(esp32_id)

        update_data = {
            'MAC': mac,
            'IP': ip,
            'status': status,
            'lastUpdate': firestore.SERVER_TIMESTAMP
        }
        
        esp32_doc.set(update_data, merge=True)
        logging.info(f"ESP32 {esp32_id} actualizado con status: {status}")

        if status == 'AWAITING_CONFIG':
            handle_awaiting_config(client, esp32_id, esp32_doc)

    except Exception as e:
        logging.error(f"Error en handle_network_info: {e}", exc_info=True)

def handle_awaiting_config(client, esp32_id: str, esp32_doc):
    """Maneja ESP32s en estado AWAITING_CONFIG"""
    try:
        clients_ref = db.collection('hdd-monitor/accounts/clients')
        
        for client_doc in clients_ref.stream():
            client_id = client_doc.id
            panels_ref = clients_ref.document(client_id).collection('panels')
            
            query = panels_ref.where('esp32_id', '==', esp32_id).limit(1).get()
            
            for panel in query:
                panel_id = panel.id
                config_message = {
                    'client_id': client_id,
                    'panel_id': panel_id,
                    'esp32_id': esp32_id,
                    'timestamp': datetime.now().isoformat()
                }
                
                config_topic = f"esp32/config/{esp32_id}"
                client.publish(config_topic, json.dumps(config_message), qos=1, retain=False)
                logging.info(f"Configuración enviada a ESP32 {esp32_id}")
                
                esp32_doc.set({
                    'client_id': client_id,
                    'panel_id': panel_id,
                    'status': 'CONFIG',
                    'lastUpdate': firestore.SERVER_TIMESTAMP
                }, merge=True)
                return

        logging.warning(f"ESP32 {esp32_id} en AWAITING_CONFIG sin panel asignado")

    except Exception as e:
        logging.error(f"Error en handle_awaiting_config: {e}", exc_info=True)

def handle_config_message(payload: Dict[str, Any]):
    """Procesa mensajes de configuración de ESP32"""
    try:
        esp32_id = payload.get('esp32_id')
        if not esp32_id:
            logging.error("esp32_id faltante")
            return

        esp32_doc = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')

        if payload.get('status') == 'config_applied':
            logging.info(f"ESP32 {esp32_id} confirmó configuración")
            esp32_doc.set({
                'status': 'RUNNING',
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }, merge=True)

    except Exception as e:
        logging.error(f"Error procesando configuración: {e}", exc_info=True)

def handle_panel_message(topic: str, payload: Dict[str, Any]):
    """Procesa mensajes de estado de paneles"""
    try:
        parts = topic.split('/')
        if len(parts) != 4:
            logging.warning(f"Formato de tópico inválido: {topic}")
            return

        client_id = parts[1]
        panel_id = parts[3]
        esp32_id = payload.get('esp32_id')

        if not esp32_id:
            logging.warning("ESP32 ID faltante en mensaje de panel")
            return

        if 'relay' in payload and 'state' in payload:
            relay_name = payload['relay']
            new_state = payload['state']
            date_time = payload.get('date_time', datetime.now().strftime('%d-%m-%Y %H:%M'))

            panel_ref = db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
            relay_ref = panel_ref.collection('relays').document(relay_name)

            old_doc = relay_ref.get()
            old_state = old_doc.get('status') if old_doc.exists else None

            relay_ref.set({
                'status': new_state,
                'date_time': date_time
            }, merge=True)

            if old_state != new_state:
                create_notification(client_id, panel_id, relay_name, new_state)

            logging.info(f"Actualizado relay {relay_name} a {new_state}")

    except Exception as e:
        logging.error(f"Error procesando mensaje de panel: {e}", exc_info=True)

def handle_status_update(payload: Dict[str, Any]):
    """Maneja actualizaciones de estado de ESP32s"""
    try:
        esp32_id = payload.get('esp32_id')
        if not esp32_id:
            logging.warning("ESP32 ID faltante en actualización de estado")
            return

        status = payload.get('status')
        if status in ['CONFIG', 'RUNNING', 'OFFLINE']:
            doc_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')
            doc_ref.set({
                'status': status,
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }, merge=True)
            logging.info(f"Estado de ESP32 {esp32_id} actualizado a: {status}")

    except Exception as e:
        logging.error(f"Error actualizando estado: {e}", exc_info=True)

def handle_panel_assignment(payload: Dict[str, Any]):
    """Maneja la asignación de un ESP32 a un panel específico"""
    try:
        client_id = payload.get('client_id')
        panel_id = payload.get('panel_id')
        esp32_id = payload.get('esp32_id')

        if not all([client_id, panel_id, esp32_id]):
            logging.error("Información faltante en asignación de panel")
            return

        esp32_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')
        esp32_doc = esp32_ref.get()
        
        if not esp32_doc.exists:
            logging.warning(f"ESP32 {esp32_id} no encontrado")
            return
        
        esp32_data = esp32_doc.to_dict()
        if esp32_data.get('status') == 'AWAITING_CONFIG':
            config_message = {
                'client_id': client_id,
                'panel_id': panel_id,
                'esp32_id': esp32_id,
                'timestamp': datetime.now().isoformat()
            }
            
            client.publish(
                f"esp32/config/{esp32_id}",
                json.dumps(config_message),
                qos=1,
                retain=False
            )
            
            esp32_ref.set({
                'clientId': client_id,
                'panelId': panel_id,
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }, merge=True)
            
    except Exception as e:
        logging.error(f"Error en handle_panel_assignment: {e}", exc_info=True)

def create_notification(client_id: str, panel_id: str, relay_name: str, new_status: str):
    """Crea notificación de cambio de estado de relay"""
    try:
        panel_ref = db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
        panel_data = panel_ref.get().to_dict()
        panel_name = panel_data.get('name', 'Panel desconocido')

        notification = {
            'panelDocName': panel_id,
            'date_time': datetime.now().strftime('%d/%m/%Y, %H:%M'),
            'message': f'El relay {relay_name} del panel "{panel_name}" ha cambiado a {new_status}',
            'relayName': relay_name,
            'isRead': False
        }

        notifications_ref = db.collection(f'hdd-monitor/accounts/clients/{client_id}/notifications')
        notifications_ref.add(notification)
        logging.info(f"Notificación creada para cambio en {panel_name}")

    except Exception as e:
        logging.error(f"Error creando notificación: {e}", exc_info=True)

def on_disconnect(client, userdata, rc):
    """Callback llamado al desconectarse del broker MQTT"""
    if rc != 0:
        logging.warning(f"Desconexión inesperada. Código: {rc}")
    else:
        logging.info("Desconexión normal")

def handle_config_request(client, payload: Dict[str, Any]):
    """Maneja solicitudes de configuración de ESP32s"""
    try:
        esp32_id = payload.get('esp32_id')
        if not esp32_id:
            return

        esp32_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')
        esp32_doc = esp32_ref.get()
        
        if not esp32_doc.exists:
            logging.warning(f"ESP32 {esp32_id} no encontrado")
            return
            
        esp32_data = esp32_doc.to_dict()
        client_id = esp32_data.get('client_id')
        panel_id = esp32_data.get('panel_id')

        if client_id and panel_id:
            config_message = {
                'client_id': client_id,
                'panel_id': panel_id,
                'esp32_id': esp32_id,
                'message_id': f"{int(time.time())}-{random.randint(1000, 9999)}",
                'timestamp': datetime.now().isoformat()
            }
            
            client.publish(
                f"esp32/config/{esp32_id}",
                json.dumps(config_message),
                qos=2
            )
            logging.info(f"Configuración reenviada a ESP32 {esp32_id}")

    except Exception as e:
        logging.error(f"Error en handle_config_request: {e}", exc_info=True)

def handle_config_ack(payload: Dict[str, Any]):
    """Maneja confirmaciones de configuración"""
    try:
        esp32_id = payload.get('esp32_id')
        status = payload.get('status')
        config_id = payload.get('config_id')

        if not all([esp32_id, status, config_id]):
            return

        esp32_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')
        
        if status == 'config_received':
            esp32_ref.set({
                'status': 'CONFIG',
                'last_config_id': config_id,
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }, merge=True)

        elif status == 'config_applied':
            esp32_ref.set({
                'status': 'RUNNING',
                'last_config_id': config_id,
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }, merge=True)

    except Exception as e:
        logging.error(f"Error en handle_config_ack: {e}", exc_info=True)

def on_message(client, userdata, msg):
    """Procesa mensajes MQTT recibidos"""
    try:
        topic = msg.topic
        payload = json.loads(msg.payload.decode())
        logging.debug(f"Mensaje recibido en {topic}: {payload}")

        handlers = {
            "esp32/mac_search": lambda: handle_mac_search(client, payload),
            "esp32/network_info": lambda: handle_network_info(client, payload),
            "esp32/config_request/+": lambda: handle_config_request(client, payload),
            "esp32/config_ack/+": lambda: handle_config_ack(payload),
            "esp32/panel_assignment": lambda: handle_panel_assignment(payload)
        }

        for pattern, handler in handlers.items():
            if topic.startswith(pattern.replace('+', '')):
                handler()
                return

    except json.JSONDecodeError as e:
        logging.error(f"Error decodificando JSON: {e}")
    except Exception as e:
        logging.error(f"Error procesando mensaje: {e}", exc_info=True)

def create_mqtt_client():
    """Crea y configura el cliente MQTT"""
    client = mqtt.Client(client_id=MQTT_CLIENT_ID, clean_session=True)
    
    # Configurar callbacks
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message
    
    # Configurar autenticación
    client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    
    # Configurar TLS
    context = ssl.create_default_context()
    context.load_verify_locations(cafile='combined_ca.crt')
    context.check_hostname = False
    client.tls_set_context(context)
    
    return client

def run():
    """Función principal"""
    while True:
        client = None
        try:
            client = create_mqtt_client()
            logging.info(f"Conectando a {MQTT_BROKER}:{MQTT_PORT}")
            client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            client.loop_forever()
            
        except KeyboardInterrupt:
            logging.info("Programa interrumpido por usuario")
            break
        except Exception as e:
            logging.error(f"Error en la conexión: {e}", exc_info=True)
            time.sleep(5)
        finally:
            if client:
                try:
                    client.disconnect()
                except:
                    pass

if __name__ == '__main__':
    run()