import paho.mqtt.client as mqtt
import ssl
import json
import time
import logging
from typing import Optional, Callable
from config import MQTT_CONFIG

class MQTTClient:
    def __init__(self, message_handler: Callable, db=None):
        """Inicializa el cliente MQTT"""
        self.client_id = MQTT_CONFIG['CLIENT_ID']
        self.message_handler = message_handler
        self.connected = False
        self.db = db  # Referencia a la base de datos
        self._setup_mqtt_client()

    def _setup_mqtt_client(self):
        """Configura una nueva instancia del cliente MQTT"""
        self.client = mqtt.Client(
            client_id=self.client_id,
            clean_session=True,
            protocol=mqtt.MQTTv311
        )
        self.setup_client()

    def setup_client(self):
        """Configura el cliente MQTT"""
        try:
            # Configurar credenciales
            self.client.username_pw_set(MQTT_CONFIG['USER'], MQTT_CONFIG['PASSWORD'])
            
            # Configuración TLS
            context = ssl.create_default_context()
            context.load_verify_locations(MQTT_CONFIG['TLS_CA_CERTS'])
            context.check_hostname = False
            
            self.client.tls_set_context(context)
            self.client.tls_insecure_set(False)
            
            # Callbacks
            self.client.on_connect = self._on_connect
            self.client.on_message = self._on_message
            self.client.on_disconnect = self._on_disconnect
            self.client.on_subscribe = self._on_subscribe
            
            # Will message
            will_payload = json.dumps({
                "status": "OFFLINE",
                "client_id": self.client_id,
                "timestamp": int(time.time() * 1000)
            })
            self.client.will_set(
                f"system/status/{self.client_id}",
                payload=will_payload,
                qos=2,
                retain=True
            )
            
            logging.info(f"Cliente MQTT configurado con ID: {self.client_id}")
            
        except Exception as e:
            logging.error(f"Error configurando cliente MQTT: {str(e)}", exc_info=True)
            raise

    def connect_and_loop(self):
        """Maneja la conexión y reconexión"""
        retry_count = 0
        max_retries = 10
        
        while True:
            try:
                if not self.connected:
                    logging.info(f"Intentando conexión MQTT a {MQTT_CONFIG['BROKER']}:{MQTT_CONFIG['PORT']}...")
                    
                    self.client.connect(
                        MQTT_CONFIG['BROKER'],
                        MQTT_CONFIG['PORT'],
                        keepalive=60
                    )
                
                retry_count = 0
                self.client.loop_forever()
                
            except Exception as e:
                retry_count += 1
                delay = min(2 ** retry_count, 60)
                
                logging.error(f"Error en conexión MQTT (intento {retry_count}): {str(e)}")
                
                try:
                    self.client.disconnect()
                except:
                    pass
                
                self.connected = False
                time.sleep(delay)
                
                if retry_count >= max_retries:
                    logging.warning("Reiniciando cliente MQTT...")
                    self._setup_mqtt_client()  # Usar método que preserva db
                    retry_count = 0

    def _on_connect(self, client, userdata, flags, rc):
        """Callback de conexión"""
        if rc == 0:
            self.connected = True
            logging.info("Conectado al broker MQTT!")
            
            # Publicar estado online
            online_payload = json.dumps({
                "status": "ONLINE",
                "client_id": self.client_id,
                "timestamp": int(time.time() * 1000)
            })
            self.client.publish(
                f"system/status/{self.client_id}",
                payload=online_payload,
                qos=2,
                retain=True
            )
            
            # Suscribirse a tópicos
            self._subscribe_to_topics()
        else:
            self.connected = False
            logging.error(f"Error de conexión MQTT, código: {rc}")

    def _subscribe_to_topics(self):
        """Suscribe a los tópicos necesarios"""
        topics = [
            ("clients/+/panels/+/#", 2),
            ("system/status/+", 2),
            ("esp32/status/+", 2),  # Suscripción específica para estados de ESP32
            ("esp32/register/+", 2)
        ]
        
        for topic, qos in topics:
            try:
                result, mid = self.client.subscribe(topic, qos)
                if result == mqtt.MQTT_ERR_SUCCESS:
                    logging.info(f"Suscrito a: {topic}")
            except Exception as e:
                logging.error(f"Error en suscripción a {topic}: {e}")

    def _handle_status_message(self, topic: str, payload: dict):
        """Maneja mensajes de estado"""
        try:
            logging.info(f"Procesando mensaje de estado. Topic: {topic}, Payload: {payload}")
            
            if 'esp32_id' in payload and 'status' in payload:
                esp32_id = payload['esp32_id']
                status = payload['status']
                
                logging.info(f"Encontrado esp32_id: {esp32_id} con status: {status}")
                
                if status == 'OFFLINE':
                    if self.db is None:
                        logging.error("Error: db es None en MQTTClient")
                        return
                        
                    logging.info(f"Dispositivo {esp32_id} está OFFLINE. Notificando a administradores...")
                    from notification_handler import NotificationHandler
                    notification_handler = NotificationHandler(self.db)
                    notification_handler.send_offline_notification(esp32_id)
                else:
                    logging.info(f"No se envía notificación. Status: {status}, DB exists: {self.db is not None}")
                    
        except Exception as e:
            logging.error(f"Error procesando mensaje de estado: {e}", exc_info=True)

    def _on_message(self, client, userdata, msg):
        """Procesa mensajes recibidos"""
        try:
            logging.info(f"Mensaje recibido en tópico: {msg.topic}")
            logging.info(f"Payload recibido: {msg.payload.decode()}")
            
            if msg.retain:
                logging.info(f"Ignorando mensaje retain en {msg.topic}")
                return
                
            payload = json.loads(msg.payload.decode())
            logging.info(f"Payload decodificado: {payload}")
            
            # Procesar mensajes de estado del sistema o ESP32
            if msg.topic.startswith("system/status/") or msg.topic.startswith("esp32/status/"):
                logging.info("Procesando mensaje de estado...")
                if 'esp32_id' in payload:  # Solo procesar mensajes relacionados con ESP32
                    self._handle_status_message(msg.topic, payload)
                return
                
            # Procesar otros mensajes normalmente
            self.message_handler(msg)
                
        except Exception as e:
            logging.error(f"Error procesando mensaje: {e}", exc_info=True)

    def _on_disconnect(self, client, userdata, rc):
        """Maneja desconexiones"""
        self.connected = False
        if rc != 0:
            logging.warning(f"Desconexión inesperada, código: {rc}")
        else:
            logging.info("Desconexión normal del broker MQTT")

    def _on_subscribe(self, client, userdata, mid, granted_qos):
        """Confirma suscripciones"""
        logging.info(f"Suscripción confirmada con QoS: {granted_qos}")