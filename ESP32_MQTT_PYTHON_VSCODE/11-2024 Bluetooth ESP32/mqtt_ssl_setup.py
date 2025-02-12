import ssl

def get_ssl_params():
    """
    Retorna el contexto SSL para la conexión MQTT.
    """
    try:
        ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ssl_context.verify_mode = ssl.CERT_NONE
        ssl_context.check_hostname = False
        return ssl_context
    except Exception as e:
        print(f"[MQTT] Error en parámetros SSL: {e}")
        return None