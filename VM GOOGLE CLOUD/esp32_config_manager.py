import json
import paho.mqtt.client as mqtt
from google.cloud import firestore
import time
from datetime import datetime, timezone
import logging
import random
from typing import Dict, Any, Optional

# Configuración de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

# Configuración MQTT
MQTT_BROKER = 'node02.myqtthub.com'
MQTT_PORT = 8883
MQTT_CLIENT_ID = 'esp32_config_manager'
MQTT_USER = 'esp32_config_manager'
MQTT_PASSWORD = 'esp32_config_manager'

# Configuración de Firestore
db = firestore.Client(project='fir-hdd-monitor-d00de')

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logging.info("Conectado al Broker MQTT!")
        # Primero limpiar cualquier mensaje retain
        client.publish("esp32/network_info", "", qos=1, retain=True)
        # Luego suscribirse
        client.subscribe("esp32/network_info", qos=1)
    else:
        logging.error(f"Error al conectar, código: {rc}")

def handle_network_info(client, payload: Dict[str, Any]):
    """Maneja mensajes de red del ESP32 y su configuración"""
    try:
        esp32_id = payload.get('esp32_id')
        status = payload.get('status')
        mac = payload.get('MAC')
        ip = payload.get('IP')

        if not all([esp32_id, status, mac]):
            logging.error(f"Datos faltantes en payload: {payload}")
            return

        # Verificar/Crear el documento del ESP32
        esp32_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')

        def on_esp32_snapshot(doc_snapshot, changes, read_time):
            try:
                if not doc_snapshot:
                    return

                esp32_doc = doc_snapshot[0]
                if not esp32_doc.exists:
                    return

                esp32_data = esp32_doc.to_dict()
                current_status = esp32_data.get('status')
                client_id = esp32_data.get('client_id')
                panel_id = esp32_data.get('panel_id')

                # Si el ESP32 tiene client_id y panel_id asignados, enviar configuración
                if current_status == 'AWAITING_CONFIG' and client_id and panel_id:
                    logging.info(f"ESP32 {esp32_id} tiene asignación de panel. Enviando configuración...")
                    config_message = {
                        'client_id': client_id,
                        'panel_id': panel_id,
                        'esp32_id': esp32_id,
                        'message_id': f"{int(time.time())}-{random.randint(1000, 9999)}",
                        'timestamp': {
                            'value': int(time.time() * 1000),
                            'type': 'realtime'
                        }
                    }
                    client.publish(
                        f"esp32/config/{esp32_id}",
                        json.dumps(config_message),
                        qos=2,
                        retain=False
                    )
                    logging.info(f"Configuración enviada a ESP32 {esp32_id}")

            except Exception as e:
                logging.error(f"Error en snapshot listener: {e}", exc_info=True)

        # Si el ESP32 no existe, crearlo
        esp32_doc = esp32_ref.get()
        if not esp32_doc.exists:
            logging.info(f"Creando nuevo registro para ESP32 {esp32_id}")
            esp32_data = {
                'MAC': mac,
                'IP': ip or '',
                'status': status,
                'client_id': '',
                'panel_id': '',
                'lastUpdate': firestore.SERVER_TIMESTAMP,
                'firstSeen': firestore.SERVER_TIMESTAMP
            }
            esp32_ref.set(esp32_data)
            logging.info(f"ESP32 {esp32_id} registrado en Firestore")
        else:
            # Actualizar registro existente
            esp32_ref.update({
                'IP': ip or '',
                'status': status,
                'lastUpdate': firestore.SERVER_TIMESTAMP
            })
            logging.info(f"ESP32 {esp32_id} actualizado en Firestore")

        if status == 'AWAITING_CONFIG':
            logging.info(f"ESP32 {esp32_id} en estado AWAITING_CONFIG")
            
            # Crear listener de cambios
            esp32_watch = esp32_ref.on_snapshot(on_esp32_snapshot)

            # Esperar por MAX_WAIT_TIME y luego remover el listener
            def cleanup_listener():
                time.sleep(300)  # 5 minutos
                esp32_watch.unsubscribe()
                logging.info(f"Listener removido para ESP32 {esp32_id}")

            # Iniciar thread para limpieza
            import threading
            cleanup_thread = threading.Thread(target=cleanup_listener)
            cleanup_thread.daemon = True
            cleanup_thread.start()

    except Exception as e:
        logging.error(f"Error en handle_network_info: {e}", exc_info=True)

def observe_panel_assignment(client, esp32_id: str):
    """Observa asignación de panel usando snapshot listener."""
    def on_snapshot(doc_snapshot, changes, read_time):
        for doc in doc_snapshot:
            if doc.exists:
                data = doc.to_dict()
                client_id = data.get('client_id')
                panel_id = data.get('panel_id')
                
                if client_id and panel_id:
                    send_panel_config(client, esp32_id, {
                        'client_id': client_id,
                        'panel_id': panel_id
                    })

    esp32_ref = db.document(f'hdd-monitor/esp32/registered/{esp32_id}')
    return esp32_ref.on_snapshot(on_snapshot)

def check_panel_assignment(esp32_id: str) -> Optional[Dict[str, str]]:
    """Verifica si hay un panel asignado al ESP32."""
    try:
        clients_ref = db.collection('hdd-monitor/accounts/clients')
        for client_doc in clients_ref.stream():
            client_id = client_doc.id
            panels_ref = clients_ref.document(client_id).collection('panels')
            query = panels_ref.where('esp32_id', '==', esp32_id).limit(1).get()
            
            for panel in query:
                return {
                    'client_id': client_id,
                    'panel_id': panel.id
                }
        return None
        
    except Exception as e:
        logging.error(f"Error verificando asignación de panel: {e}")
        return None

def send_panel_config(client, esp32_id: str, panel_info: Dict[str, str]):
    """Envía la configuración del panel al ESP32."""
    try:
        config_message = {
            'client_id': panel_info['client_id'],
            'panel_id': panel_info['panel_id'],
            'esp32_id': esp32_id,
            'message_id': f"{int(time.time())}-{random.randint(1000, 9999)}",
            'timestamp': {
                'value': int(time.time() * 1000),
                'type': 'realtime'
            }
        }
        
        client.publish(
            f"esp32/config/{esp32_id}",
            json.dumps(config_message),
            qos=2,
            retain=False
        )
        logging.info(f"Configuración enviada a ESP32 {esp32_id}")
        
    except Exception as e:
        logging.error(f"Error enviando configuración: {e}")

def validate_message_timestamp(payload: Dict[str, Any]) -> bool:
    """Valida el timestamp del mensaje."""
    try:
        if 'timestamp' not in payload:
            logging.warning("Mensaje sin timestamp")
            return False

        timestamp_data = payload['timestamp']
        
        # Validar estructura del timestamp
        if not isinstance(timestamp_data, dict):
            logging.warning("Formato de timestamp inválido")
            return False
            
        if 'value' not in timestamp_data or 'type' not in timestamp_data:
            logging.warning("Campos de timestamp faltantes")
            return False
            
        # Verificar que sea timestamp en tiempo real
        if timestamp_data['type'] != 'realtime':
            logging.warning(f"Tipo de timestamp no válido: {timestamp_data['type']}")
            return False
            
        # Para timestamps de ESP32 (que son ticks_ms desde boot)
        # Solo verificamos que no sean del futuro y que no sean 0
        message_time = int(timestamp_data['value'])
        if message_time <= 0:
            logging.warning("Timestamp inválido: <= 0")
            return False

        # Aceptamos el mensaje si:
        # 1. Es un timestamp pequeño (típico de ticks_ms del ESP32)
        # 2. Tiene una marca de tiempo razonable (últimas 24 horas si es epoch)
        current_time = int(time.time() * 1000)
        if message_time < 86400000:  # Si es menos de 24 horas en ms, asumimos que es ticks_ms
            return True
        else:
            # Si es un timestamp epoch, verificar que no sea más viejo de 30 segundos
            time_diff = current_time - message_time
            if time_diff > 30000:  # 30 segundos en milisegundos
                logging.warning(f"Mensaje demasiado antiguo: {time_diff}ms")
                return False
            
        return True
        
    except Exception as e:
        logging.error(f"Error validando timestamp: {e}")
        return False

def on_message(client, userdata, msg):
    """Procesa mensajes MQTT recibidos"""
    try:
        # Ignorar mensajes retain
        if msg.retain:
            logging.info(f"Ignorando mensaje retain en {msg.topic}")
            return

        # Decodificar payload
        try:
            payload = json.loads(msg.payload.decode())
        except json.JSONDecodeError as e:
            logging.error(f"Error decodificando JSON: {e}")
            return

        if not payload or all(not v for v in payload.values()):
            logging.info("Ignorando mensaje vacío (probablemente limpieza de retain)")
            return

        # Log inicial
        logging.debug(f"Mensaje recibido en {msg.topic}: {payload}")

        # Validar timestamp
        if not validate_message_timestamp(payload):
            logging.info(f"Ignorando mensaje con timestamp inválido en {msg.topic}")
            return

        # Procesar mensaje según tópico
        if msg.topic == "esp32/network_info":
            handle_network_info(client, payload)

    except Exception as e:
        logging.error(f"Error procesando mensaje: {e}", exc_info=True)

def main():
    client = mqtt.Client(client_id=MQTT_CLIENT_ID, clean_session=True)
    client.on_connect = on_connect
    client.on_message = on_message
    client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    
    # Configurar TLS
    client.tls_set(ca_certs='combined_ca.crt')
    
    logging.info(f"Conectando a {MQTT_BROKER}:{MQTT_PORT}")
    client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
    client.loop_forever()

if __name__ == '__main__':
    main()