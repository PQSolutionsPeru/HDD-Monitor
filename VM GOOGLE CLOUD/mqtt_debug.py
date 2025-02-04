import paho.mqtt.client as mqtt
import ssl
import logging
import sys
import time

# Configuración de logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

# Configuración MQTT
MQTT_BROKER = 'node02.myqtthub.com'
MQTT_PORT = 8883
MQTT_CLIENT_ID = 'mqtt_firestore_handler'
MQTT_USER = 'mqtt_firestore_handler'
MQTT_PASSWORD = 'mqtt_firestore_handler'

def on_connect(client, userdata, flags, rc):
    """Callback que se ejecuta cuando se conecta al broker"""
    if rc == 0:
        logging.info("Conectado exitosamente al broker")
        # Intentar publicar un mensaje después de conectar
        client.publish("test", "Mensaje de prueba", qos=1)
    else:
        logging.error(f"Error de conexión, código: {rc}")

def on_disconnect(client, userdata, rc):
    """Callback que se ejecuta cuando se desconecta del broker"""
    if rc != 0:
        logging.error(f"Desconexión inesperada, código: {rc}")
    else:
        logging.info("Desconexión normal")

def on_publish(client, userdata, mid):
    """Callback que se ejecuta cuando se publica un mensaje"""
    logging.info(f"Mensaje {mid} publicado")

def on_log(client, userdata, level, buf):
    """Callback para logs detallados"""
    logging.debug(f"MQTT Log: {buf}")

def create_client():
    """Crea y configura el cliente MQTT"""
    client = mqtt.Client(
        client_id=MQTT_CLIENT_ID,
        clean_session=True
    )
    
    # Configurar callbacks
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_publish = on_publish
    client.on_log = on_log
    
    # Configurar autenticación
    client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    
    # Configurar TLS
    context = ssl.create_default_context()
    context.load_verify_locations(cafile='combined_ca.crt')
    context.check_hostname = False
    
    client.tls_set_context(context)
    
    return client

def main():
    """Función principal"""
    try:
        client = create_client()
        
        logging.info(f"Conectando a {MQTT_BROKER}:{MQTT_PORT}")
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        
        # Iniciar el loop en segundo plano
        client.loop_start()
        
        # Esperar un poco
        time.sleep(2)
        
        # Intentar publicar un mensaje
        result = client.publish("test", "Mensaje de prueba", qos=1)
        result.wait_for_publish()
        
        if result.is_published():
            logging.info("Mensaje publicado exitosamente")
        
        time.sleep(3)  # Esperar respuesta
        
        # Desconectar limpiamente
        client.disconnect()
        client.loop_stop()
        
    except KeyboardInterrupt:
        logging.info("Programa interrumpido por el usuario")
    except Exception as e:
        logging.error(f"Error: {e}", exc_info=True)
    finally:
        try:
            client.disconnect()
            client.loop_stop()
        except:
            pass

if __name__ == "__main__":
    main()