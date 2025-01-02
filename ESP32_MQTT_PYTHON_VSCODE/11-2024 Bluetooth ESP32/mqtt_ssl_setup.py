import os

def get_ssl_params():
    """
    Retorna los parámetros SSL para la conexión MQTT.
    SSL sin verificación de certificado pero con cifrado.
    """
    import ssl
    try:
        return {
            'cert_reqs': ssl.CERT_NONE,  # No verificar certificado
            'do_handshake': True,
            'ciphers': None  # Usar ciphers por defecto
        }
    except Exception as e:
        print(f"[MQTT] Error en parámetros SSL: {e}")
        return None