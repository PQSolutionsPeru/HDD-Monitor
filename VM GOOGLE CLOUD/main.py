import logging
import signal
import sys
from mqtt_client import MQTTClient
from firestore_handler import FirestoreHandler
import json
import threading

def handle_mqtt_message(msg):
    try:
        if msg.retain:
            logging.info(f"Ignorando mensaje retain en {msg.topic}")
            return

        payload = json.loads(msg.payload.decode())
        if not payload:
            return

        if msg.topic.startswith("clients/") and "panels" in msg.topic:
            firestore_handler.handle_panel_message(msg.topic, payload)
            
    except Exception as e:
        logging.error(f"Error procesando mensaje: {e}", exc_info=True)

def signal_handler(signum, frame):
    """Maneja la limpieza antes de cerrar"""
    logging.info("Señal de terminación recibida")
    try:
        firestore_handler.cleanup()
    except Exception as e:
        logging.error(f"Error durante la limpieza: {e}")
    sys.exit(0)

if __name__ == '__main__':
    try:
        logging.info("Iniciando servicio...")
        
        # Configurar manejador de señales
        signal.signal(signal.SIGTERM, signal_handler)
        signal.signal(signal.SIGINT, signal_handler)
        
        # Inicializar componentes
        firestore_handler = FirestoreHandler()
        
        # Iniciar observadores de Firestore en hilos separados
        firestore_thread = threading.Thread(
            target=firestore_handler.watch_events,
            daemon=True
        )
        firestore_thread.start()

        relay_watch_thread = threading.Thread(
            target=firestore_handler.watch_relay_states,
            daemon=True
        )
        relay_watch_thread.start()
        
        # Iniciar cliente MQTT
        mqtt_client = MQTTClient(handle_mqtt_message)
        mqtt_client.connect_and_loop()
        
    except Exception as e:
        logging.error(f"Error fatal: {e}", exc_info=True)
        sys.exit(1)