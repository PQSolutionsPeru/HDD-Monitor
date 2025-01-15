import paho.mqtt.client as mqtt
import ssl
import json
import time
import logging
from typing import Optional, Callable
from config import MQTT_CONFIG

class MQTTClient:
    def __init__(self, message_handler: Callable):
        # Volvemos a MQTT v3.1.1 para mejor compatibilidad
        self.client = mqtt.Client(
            client_id=MQTT_CONFIG['CLIENT_ID'],
            clean_session=True,
            protocol=mqtt.MQTTv311
        )
        self.message_handler = message_handler
        self.setup_client()

    def setup_client(self):
        # Configurar credenciales
        self.client.username_pw_set(MQTT_CONFIG['USER'], MQTT_CONFIG['PASSWORD'])
        
        # Configurar TLS
        self.client.tls_set(
            ca_certs=MQTT_CONFIG['TLS_CA_CERTS'],
            tls_version=ssl.PROTOCOL_TLSv1_2,
            cert_reqs=ssl.CERT_REQUIRED,
            ciphers='ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384'
        )
        
        # Configurar callbacks
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect
        
        # Configurar will message
        self.client.will_set(
            f"system/status/{MQTT_CONFIG['CLIENT_ID']}",
            payload=json.dumps({
                "status": "OFFLINE",
                "timestamp": int(time.time() * 1000)
            }),
            qos=MQTT_CONFIG['QOS'],
            retain=True
        )

    def _on_connect(self, client, userdata, flags, rc):
        """Callback para cuando se establece la conexión"""
        if rc == 0:
            logging.info("Conectado al Broker MQTT!")
            
            # Publicar estado online
            self.client.publish(
                f"system/status/{MQTT_CONFIG['CLIENT_ID']}",
                payload=json.dumps({
                    "status": "ONLINE",
                    "timestamp": int(time.time() * 1000)
                }),
                qos=MQTT_CONFIG['QOS'],
                retain=True
            )
            
            # Suscribirse a tópicos
            topics = [
                ("clients/+/panels/+/#", MQTT_CONFIG['QOS']),
                ("system/status/+", MQTT_CONFIG['QOS']),
                ("esp32/register/+", MQTT_CONFIG['QOS'])
            ]
            
            for topic, qos in topics:
                self.client.subscribe(topic, qos)
                logging.info(f"Suscrito a: {topic} (QoS {qos})")
        else:
            error_messages = {
                1: "Versión de protocolo incorrecta",
                2: "Identificador rechazado",
                3: "Servidor no disponible",
                4: "Credenciales incorrectas",
                5: "No autorizado"
            }
            error_msg = error_messages.get(rc, f"Error desconocido: {rc}")
            logging.error(f"Error de conexión: {error_msg}")

    def _on_message(self, client, userdata, msg):
        """Callback para cuando se recibe un mensaje"""
        self.message_handler(msg)

    def _on_disconnect(self, client, userdata, rc):
        """Callback para cuando se desconecta del broker"""
        if rc != 0:
            logging.warning(f"Desconexión inesperada, código: {rc}")
        
        try:
            self.client.publish(
                f"system/status/{MQTT_CONFIG['CLIENT_ID']}",
                payload=json.dumps({
                    "status": "OFFLINE",
                    "timestamp": int(time.time() * 1000)
                }),
                qos=MQTT_CONFIG['QOS'],
                retain=True
            )
        except Exception as e:
            logging.error(f"Error publicando estado offline: {e}")

    def connect_and_loop(self):
        """Inicia la conexión y el loop de eventos"""
        retry_count = 0
        while True:
            try:
                self.client.connect(
                    MQTT_CONFIG['BROKER'],
                    MQTT_CONFIG['PORT'],
                    keepalive=MQTT_CONFIG['KEEPALIVE']
                )
                retry_count = 0
                self.client.loop_forever()
            except Exception as e:
                retry_count += 1
                delay = min(
                    MQTT_CONFIG['RECONNECT_DELAY_MIN'] * (2 ** retry_count),
                    MQTT_CONFIG['RECONNECT_DELAY_MAX']
                )
                logging.error(f"Error en conexión (intento {retry_count}): {e}")
                
                if retry_count >= MQTT_CONFIG['MAX_RETRIES']:
                    raise Exception("Máximo de reintentos alcanzado")
                    
                time.sleep(delay)