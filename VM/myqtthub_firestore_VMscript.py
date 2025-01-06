import json
import paho.mqtt.client as mqtt
from google.cloud import firestore
import ssl
import time
from datetime import datetime
from typing import Optional, Dict, Any
import logging
import random

# Configuración de logging con niveles diferentes
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
# Configurar logging específico para streams
logging.getLogger('google.cloud.firestore_v1.watch').setLevel(logging.WARNING)

# Configuración MQTT
MQTT_BROKER = 'node02.myqtthub.com'
MQTT_PORT = 8883
MQTT_CLIENT_ID = 'compute_engine'
MQTT_USER = 'compute_engine'
MQTT_PASSWORD = 'compute_engine'

# Configuración de Firestore
db = firestore.Client(project='fir-hdd-monitor-d00de')

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logging.info("Conectado al Broker MQTT!")
        
        # Limpiar mensajes retain al conectar
        clean_all_retained_messages(client)
        
        # Suscribirse a los tópicos
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

class StreamManager:
    def __init__(self):
        self.last_reconnect = 0
        self.MIN_RECONNECT_INTERVAL = 300  # 5 minutos entre reconexiones

    def can_reconnect(self):
        current_time = time.time()
        if current_time - self.last_reconnect >= self.MIN_RECONNECT_INTERVAL:
            self.last_reconnect = current_time
            return True
        return False

stream_manager = StreamManager()

def setup_firestore_listeners():
    """Configura listeners de Firestore con mejor manejo de reconexión"""
    retry_count = 0
    MAX_RETRIES = 3
    
    while retry_count < MAX_RETRIES:
        try:
            # Configurar listeners
            return True
        except Exception as e:
            retry_count += 1
            if retry_count == MAX_RETRIES:
                logging.error(f"Error crítico configurando listeners: {e}")
                return False
            logging.warning(f"Reintento {retry_count} de {MAX_RETRIES}")
            time.sleep(5)

def handle_stream_error(e):
    """Maneja errores de stream con control de frecuencia"""
    if stream_manager.can_reconnect():
        logging.warning(f"Reconectando stream después de error: {e}")
    # Si no se puede reconectar aún, silenciosamente espera

def clean_retained_message(client, esp32_id):
    """Limpia mensajes retain específicos para un ESP32"""
    try:
        topics_to_clean = [
            f"esp32/config/{esp32_id}",
            f"system/status/{esp32_id}",
            f"esp32/network_info",  # Removido /{esp32_id}
            f"esp32/config_ack/{esp32_id}"
        ]
        
        for topic in topics_to_clean:
            # Verificar que el tópico no contenga wildcards
            if '+' not in topic and '#' not in topic:
                client.publish(topic, "", qos=1, retain=False)
                time.sleep(0.1)  # Pequeña pausa entre publicaciones
            
        logging.info(f"Mensajes retain limpiados para ESP32 {esp32_id}")
    except Exception as e:
        logging.error(f"Error limpiando mensajes retain: {e}")

def clean_all_retained_messages(client):
    """Limpia todos los mensajes retain relevantes al iniciar"""
    try:
        # Tópicos base sin wildcards
        topics_to_clean = [
            "esp32/network_info",
            "esp32/config_ack",
            "esp32/panel_assignment"
        ]
        
        for topic in topics_to_clean:
            client.publish(topic, "", qos=1, retain=False)
            time.sleep(0.1)
        
        logging.info("Limpieza inicial de mensajes retain completada")
    except Exception as e:
        logging.error(f"Error en limpieza inicial de mensajes retain: {e}")

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

def handle_network_info(client, payload: Dict[str, Any], message):
    """Maneja mensajes de información de red de los ESP32"""
    try:
        if message.retain:
            esp32_id = payload.get('esp32_id')
            if esp32_id:
                clean_retained_message(client, esp32_id)
            return

        # Validar formato del timestamp
        timestamp = payload.get('timestamp', {})
        if not isinstance(timestamp, dict) or timestamp.get('type') != 'realtime':
            # Solo logear si es un error real, no un mensaje retain
            if not message.retain:
                logging.warning("Mensaje no válido o no en tiempo real")
            return

        # Extraer campos principales
        mac = normalize_mac(payload.get('MAC', ''))
        ip = payload.get('IP')
        esp32_id = payload.get('esp32_id')
        status = payload.get('status')
        client_id = payload.get('client_id')
        panel_id = payload.get('panel_id')

        if not all([mac, ip, esp32_id, status]):
            logging.warning(f"Información incompleta - MAC: {mac}, IP: {ip}, ESP32_ID: {esp32_id}, Status: {status}")
            return

        # Obtener estado anterior
        esp32_ref = db.collection('hdd-monitor/esp32/registered').document(esp32_id)
        current_doc = esp32_ref.get()
        previous_status = current_doc.get('status') if current_doc.exists else None

        # Si el dispositivo está en AWAITING_CONFIG
        if status == 'AWAITING_CONFIG':
            logging.info(f"ESP32 {esp32_id} en estado AWAITING_CONFIG")
            
            # Actualizar en Firestore explícitamente con el estado
            update_data = {
                'MAC': mac,
                'IP': ip,
                'status': 'AWAITING_CONFIG',
                'lastUpdate': firestore.SERVER_TIMESTAMP
            }
            esp32_ref.set(update_data, merge=True)
            
            # Publicar confirmación para la app
            confirmation = {
                'esp32_id': esp32_id,
                'status': 'AWAITING_CONFIG',
                'MAC': mac,
                'IP': ip,
                'timestamp': {
                    'value': int(time.time() * 1000),
                    'type': 'realtime'
                },
                'message_id': f"{int(time.time())}-{random.randint(1000, 9999)}"
            }
            client.publish(
                'esp32/network_info',
                json.dumps(confirmation),
                qos=1,
                retain=False
            )
            
            logging.info(f"Confirmación enviada para ESP32 {esp32_id}")
            return

        # Para otros estados, preparar datos de actualización
        update_data = {
            'MAC': mac,
            'IP': ip,
            'status': status,
            'lastUpdate': firestore.SERVER_TIMESTAMP
        }

        # Agregar client_id y panel_id si están presentes
        if client_id and panel_id:
            update_data.update({
                'client_id': client_id,
                'panel_id': panel_id
            })

        # Actualizar Firestore
        esp32_ref.set(update_data, merge=True)

        # Solo logear cambios de estado significativos
        if status != previous_status:
            logging.info(f"ESP32 {esp32_id} cambió estado de {previous_status} a {status}")
            
            # Si entra en modo RUNNING por primera vez
            if status == 'RUNNING' and previous_status != 'RUNNING':
                logging.info(f"ESP32 {esp32_id} inició monitoreo de panel")
                
                if client_id and panel_id:
                    handle_running_state(esp32_id, client_id, panel_id)
                else:
                    logging.error(f"ESP32 {esp32_id} en RUNNING sin client_id o panel_id")
            
            # Si se desconecta
            elif status == 'OFFLINE' and previous_status != 'OFFLINE':
                logging.warning(f"ESP32 {esp32_id} se desconectó")

        # Manejar configuración pendiente
        if status == 'AWAITING_CONFIG':
            # Verificar si tiene panel asignado
            existing_data = current_doc.to_dict() if current_doc.exists else {}
            existing_client_id = existing_data.get('client_id')
            existing_panel_id = existing_data.get('panel_id')
            
            if existing_client_id and existing_panel_id:
                config_message = {
                    'client_id': existing_client_id,
                    'panel_id': existing_panel_id,
                    'esp32_id': esp32_id,
                    'message_id': f"{int(time.time())}-{random.randint(1000, 9999)}",
                    'timestamp': {
                        'value': int(time.time() * 1000),
                        'type': 'realtime'
                    }
                }
                
                # Enviar configuración con QoS 2
                client.publish(
                    f"esp32/config/{esp32_id}",
                    json.dumps(config_message),
                    qos=2,
                    retain=False
                )
                
                logging.info(f"Configuración enviada a ESP32 {esp32_id}")
                
                # Actualizar estado a CONFIG_SENT
                esp32_ref.set({
                    'status': 'CONFIG_SENT',
                    'lastUpdate': firestore.SERVER_TIMESTAMP
                }, merge=True)

    except Exception as e:
        logging.error(f"Error en handle_network_info: {e}", exc_info=True)
        # Intentar limpiar mensajes retain en caso de error
        try:
            if esp32_id:
                clean_retained_message(client, esp32_id)
        except:
            pass

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

def handle_running_state(esp32_id: str, client_id: str, panel_id: str):
    """Maneja la transición a estado RUNNING"""
    try:
        # Verificar panel asignado
        panel_ref = db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
        panel_doc = panel_ref.get()

        if not panel_doc.exists:
            logging.error(f"Panel {panel_id} no encontrado")
            return

        # Actualizar estado del panel
        panel_ref.set({
            'status': 'ACTIVE',
            'lastUpdate': firestore.SERVER_TIMESTAMP
        }, merge=True)

        # Crear colección de relays si no existe
        relays_ref = panel_ref.collection('relays')
        for relay_name in ['Alarma', 'Problema', 'Supervision']:
            relay_doc = relays_ref.document(relay_name)
            if not relay_doc.get().exists:
                relay_doc.set({
                    'status': 'OK',
                    'lastUpdate': firestore.SERVER_TIMESTAMP
                })

        logging.info(f"Panel {panel_id} configurado para monitoreo")

    except Exception as e:
        logging.error(f"Error en handle_running_state: {e}", exc_info=True)

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
            logging.warning("ESP32 ID faltante en mensaje")
            return

        if 'relay' in payload and 'state' in payload:
            relay_name = payload['relay']
            new_state = payload['state']

            # Actualizar estado del relay
            panel_ref = db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
            relay_ref = panel_ref.collection('relays').document(relay_name)

            # Verificar cambio de estado
            old_doc = relay_ref.get()
            old_state = old_doc.get('status') if old_doc.exists else None

            if old_state != new_state:
                # Actualizar estado
                relay_ref.set({
                    'status': new_state,
                    'date_time': datetime.now().strftime('%d-%m-%Y %H:%M'),
                    'lastUpdate': firestore.SERVER_TIMESTAMP
                }, merge=True)

                # Crear notificación
                create_notification(
                    client_id=client_id,
                    panel_id=panel_id,
                    relay_name=relay_name,
                    new_state=new_state
                )

                logging.info(f"Relay {relay_name} actualizado a {new_state}")

    except Exception as e:
        logging.error(f"Error en handle_panel_message: {e}", exc_info=True)

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

def create_notification(client_id: str, panel_id: str, relay_name: str, new_state: str):
    """Crea notificación de cambio de estado de relay"""
    try:
        # Obtener información del panel
        panel_ref = db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
        panel_doc = panel_ref.get()
        
        if not panel_doc.exists:
            logging.error(f"Panel {panel_id} no encontrado")
            return

        panel_data = panel_doc.to_dict()
        panel_name = panel_data.get('name', 'Panel desconocido')
        current_time = datetime.now().strftime('%d-%m-%Y %H:%M')

        # Crear ID único para la notificación
        notification_id = f"notification_{client_id}_{datetime.now().strftime('%Y%m%d%H%M%S')}_{generate_random_id()}"

        # Crear notificación
        notification = {
            'date_time': current_time,
            'panelDocName': panel_id,
            'message': f'El relay {relay_name} del panel "{panel_name}" ha cambiado a {new_state}',
            'relayName': relay_name,
            'isRead': False,
            'documentName': notification_id,
            'lastUpdate': firestore.SERVER_TIMESTAMP
        }

        # Guardar notificación
        notifications_ref = db.collection(f'hdd-monitor/accounts/clients/{client_id}/notifications')
        notifications_ref.document(notification_id).set(notification)

        # Enviar notificación FCM a usuarios del cliente
        users_ref = db.collection(f'hdd-monitor/accounts/clients/{client_id}/users')
        users = users_ref.get()
        
        for user in users:
            user_data = user.to_dict()
            fcm_token = user_data.get('fcmToken')
            
            if fcm_token and fcm_token != 'None':
                send_fcm_notification(
                    token=fcm_token,
                    title=f"Cambio en {panel_name}",
                    body=f"Relay {relay_name}: {new_state}",
                    data={
                        'panel_id': panel_id,
                        'relay_name': relay_name,
                        'state': new_state,
                        'type': 'relay_change'
                    }
                )

        logging.info(f"Notificación creada para {panel_name}: {relay_name} -> {new_state}")

    except Exception as e:
        logging.error(f"Error creando notificación: {e}", exc_info=True)

def send_fcm_notification(token: str, title: str, body: str, data: Dict[str, Any]):
    """Envía notificación FCM a un token específico"""
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data=data,
            token=token
        )
        
        response = messaging.send(message)
        logging.info(f"Notificación FCM enviada: {response}")
        
    except Exception as e:
        logging.error(f"Error enviando FCM: {e}", exc_info=True)

def generate_random_id(length: int = 6) -> str:
    """Genera ID aleatorio para notificaciones"""
    import random
    import string
    chars = string.ascii_uppercase + string.digits
    return ''.join(random.choice(chars) for _ in range(length))

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
                qos=2,
                retain=False
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
        
        if status == 'config_applied':
            logging.info(f"ESP32 {esp32_id} confirmó configuración")
            esp32_ref.set({
                'status': 'RUNNING',
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
            "esp32/network_info": lambda: handle_network_info(client, payload, msg),
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