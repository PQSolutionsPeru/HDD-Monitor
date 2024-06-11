import usocket as socket
import machine
import ubinascii
import os
import esp32
import json
from wifi_manager import WiFiManager
import _thread
import uasyncio as asyncio
import log_manager
from websocket_helper import websocket_handler
from watchdog_manager import WatchdogManager
from mqtt_manager import MQTTManager

wifi_manager = WiFiManager()
watchdog_manager = WatchdogManager()
mqtt_manager = MQTTManager(wifi_manager)

# HTML para las diferentes secciones del servidor web
html_template = """
<!DOCTYPE html>
<html>
<head>
    <style>
        body {{
            font-family: Arial, sans-serif;
        }}
        .sidenav {{
            height: 100%;
            width: 200px;
            position: fixed;
            z-index: 1;
            top: 0;
            left: 0;
            background-color: #111;
            padding-top: 20px;
        }}
        .sidenav a {{
            padding: 8px 8px 8px 16px;
            text-decoration: none;
            font-size: 18px;
            color: #818181;
            display: block;
        }}
        .sidenav a:hover {{
            color: #f1f1f1;
        }}
        .main {{
            margin-left: 200px;
            padding: 0px 10px;
        }}
    </style>
</head>
<body>
    <div class="sidenav">
        <a href="/">Inicio</a>
        <a href="/config">Configuración Wi-Fi</a>
        <a href="/reboot">Reiniciar</a>
        <a href="/start">Iniciar main.py</a>
        <a href="/stop">Detener main.py</a>
        <a href="/upload">Subir Archivo</a>
        <a href="/logs">Logs</a>
    </div>
    <div class="main">
        {content}
    </div>
    <script>
        function toggleIPFields() {{
            var checkbox = document.getElementById("enable_static_ip");
            var ipFields = document.getElementById("ip_fields");
            ipFields.style.display = checkbox.checked ? "block" : "none";
        }}
    </script>
</body>
</html>
"""

# Credenciales para autenticación básica
BASIC_AUTH_CREDENTIALS = "admin:1234556"  # Cambia esto por tus credenciales

# WebSocket clients list
ws_clients = []

# Servidor HTTP
def iniciar_servidor():
    addr = socket.getaddrinfo('0.0.0.0', 8080)[0][-1]
    s = socket.socket()
    s.bind(addr)
    s.listen(5)
    print('Escuchando en', addr)

    while True:
        cl, addr = s.accept()
        print('Cliente conectado desde', addr)
        try:
            request = cl.recv(1024).decode()
            if autenticar(request):
                if "Upgrade: websocket" in request:
                    _thread.start_new_thread(manejar_websocket, (cl,))
                else:
                    manejar_solicitud(cl, request)
            else:
                cl.send('HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm="ESP32"\r\n\r\n')
        except Exception as e:
            print('Error al manejar la solicitud:', e)
        finally:
            cl.close()
        # Alimentar el watchdog después de cada solicitud
        watchdog_manager.alimentar()

# Verificar autenticación básica
def autenticar(request):
    headers = request.split("\r\n")
    for header in headers:
        if header.startswith("Authorization: Basic "):
            encoded_credentials = header.split(" ")[2]
            decoded_credentials = ubinascii.a2b_base64(encoded_credentials).decode("utf-8")
            if decoded_credentials == BASIC_AUTH_CREDENTIALS:
                return True
    return False

# Manejar solicitudes HTTP
def manejar_solicitud(cliente, solicitud):
    request_line = solicitud.split('\n')[0]
    method, path, _ = request_line.split()
    
    if method == 'GET' and path == '/':
        response = servir_pagina_principal()
    elif method == 'GET' and path == '/config':
        response = servir_pagina_config()
    elif method == 'POST' and path == '/config':
        response = manejar_post_config(solicitud)
    elif method == 'GET' and path == '/reboot':
        response = servir_pagina_reboot()
    elif method == 'POST' and path == '/reboot':
        response = manejar_post_reboot()
    elif method == 'GET' and path == '/start':
        response = servir_pagina_start()
    elif method == 'POST' and path == '/start':
        response = manejar_post_start()
    elif method == 'GET' and path == '/stop':
        response = servir_pagina_stop()
    elif method == 'POST' and path == '/stop':
        response = manejar_post_stop()
    elif method == 'GET' and path == '/upload':
        response = servir_pagina_upload()
    elif method == 'POST' and path == '/upload':
        response = manejar_post_upload(solicitud)
    elif method == 'GET' and path == '/logs':
        response = servir_pagina_logs()
    elif method == 'DELETE' and path.startswith('/file'):
        response = manejar_delete_file(solicitud, path)
    else:
        response = 'HTTP/1.1 404 Not Found\r\n\r\n'

    cliente.send(response)

# Servir página de inicio
def servir_pagina_principal():
    content = "<h2>Bienvenido al Monitor de HDD</h2><p>Seleccione una opción del menú.</p>"
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Servir página de configuración Wi-Fi
def servir_pagina_config():
    content = """
    <h2>Configuración Wi-Fi</h2>
    <form action="/config" method="post">
        <label for="ssid">SSID:</label>
        <input type="text" id="ssid" name="ssid"><br>
        <label for="password">Password:</label>
        <input type="password" id="password" name="password"><br>
        <label for="enable_static_ip">Habilitar IP estática:</label>
        <input type="checkbox" id="enable_static_ip" name="enable_static_ip" onchange="toggleIPFields()"><br>
        <div id="ip_fields" style="display: none;">
            <label for="ip">IP:</label>
            <input type="text" id="ip" name="ip"><br>
            <label for="netmask">Máscara de red:</label>
            <input type="text" id="netmask" name="netmask"><br>
            <label for="gateway">Puerta de enlace:</label>
            <input type="text" id="gateway" name="gateway"><br>
        </div>
        <input type="submit" value="Submit">
    </form>
    """
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar configuración de red Wi-Fi
def manejar_post_config(solicitud):
    print(f"Solicitud de configuración recibida: {solicitud}")
    ssid = parsear_datos_formulario(solicitud, 'ssid')
    password = parsear_datos_formulario(solicitud, 'password')
    enable_static_ip = parsear_datos_formulario(solicitud, 'enable_static_ip') == 'on'
    ip = parsear_datos_formulario(solicitud, 'ip') if enable_static_ip else None
    netmask = parsear_datos_formulario(solicitud, 'netmask') if enable_static_ip else None
    gateway = parsear_datos_formulario(solicitud, 'gateway') if enable_static_ip else None
    print(f"SSID obtenido: {ssid}")
    print(f"Password obtenido: {password}")
    print(f"IP obtenida: {ip}")
    print(f"Máscara de red obtenida: {netmask}")
    print(f"Puerta de enlace obtenida: {gateway}")
    wifi_manager.guardar_credenciales_wifi(ssid, password, ip, netmask, gateway)
    machine.reset()  # Reiniciar el ESP32 para aplicar la nueva configuración
    return 'HTTP/1.1 200 OK\r\n\r\nConfiguración guardada. Reiniciando...'

# Servir página de reinicio
def servir_pagina_reboot():
    content = "<h2>Reiniciar</h2><form action='/reboot' method='post'><input type='submit' value='Reiniciar'></form>"
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar reinicio del ESP32
def manejar_post_reboot():
    machine.reset()
    return 'HTTP/1.1 200 OK\r\n\r\nReiniciando...'

# Servir página para iniciar main.py
def servir_pagina_start():
    content = "<h2>Iniciar main.py</h2><form action='/start' method='post'><input type='submit' value='Iniciar'></form>"
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar inicio de main.py
def manejar_post_start():
    import main
    _thread.start_new_thread(main.main, ())
    return 'HTTP/1.1 200 OK\r\n\r\nmain.py iniciado.'

# Servir página para detener main.py
def servir_pagina_stop():
    content = "<h2>Detener main.py</h2><form action='/stop' method='post'><input type='submit' value='Detener'></form>"
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar detención de main.py
def manejar_post_stop():
    # No hay un método directo para detener un script en MicroPython
    # Una opción sería usar un flag compartido o reiniciar el ESP32
    machine.reset()
    return 'HTTP/1.1 200 OK\r\n\r\nmain.py detenido.'

# Servir página de subida de archivos
def servir_pagina_upload():
    content = """
    <h2>Subir Archivo</h2>
    <form action="/upload" method="post" enctype="multipart/form-data">
        <input type="file" name="file"><br>
        <input type="submit" value="Subir">
    </form>
    """
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar subida de archivos
def manejar_post_upload(solicitud):
    # Implementar lógica para subir archivos aquí
    return 'HTTP/1.1 200 OK\r\n\r\nArchivo subido exitosamente.'

# Servir página de logs
def servir_pagina_logs():
    try:
        with open('/logs/log.txt', 'r') as f:
            logs = f.read()
    except OSError:
        logs = 'No se encontraron logs.'
    content = f"<h2>Logs</h2><pre>{logs}</pre>"
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Parsear datos del formulario
def parsear_datos_formulario(solicitud, nombre_campo):
    try:
        body = solicitud.split('\r\n\r\n')[1]
        params = dict(param.split('=') for param in body.split('&'))
        return decode_url(params[nombre_campo])
    except (IndexError, KeyError):
        print(f"Error al parsear el campo {nombre_campo}")
        return ''

def decode_url(encoded_str):
    hex_chars = "0123456789ABCDEFabcdef"
    decoded_str = ''
    i = 0
    while i < len(encoded_str):
        if encoded_str[i] == '%' and i + 2 < len(encoded_str) and encoded_str[i+1] in hex_chars and encoded_str[i+2] in hex_chars:
            decoded_str += chr(int(encoded_str[i+1:i+3], 16))
            i += 3
        else:
            decoded_str += encoded_str[i]
            i += 1
    return decoded_str

# Manejar eliminación de archivos
def manejar_delete_file(solicitud, path):
    nombre_archivo = path.split('/file/')[1]
    try:
        os.remove(f'/spiffs/{nombre_archivo}')
        return 'HTTP/1.1 200 OK\r\n\r\nArchivo eliminado exitosamente.'
    except:
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al eliminar el archivo.'

# Manejar WebSocket
def manejar_websocket(cliente):
    global ws_clients
    ws_clients.append(cliente)
    try:
        while True:
            msg = cliente.recv(1024)
            if not msg:
                break
            # Procesar mensajes del cliente WebSocket aquí si es necesario
            send_console_output(msg)
    finally:
        ws_clients.remove(cliente)

# Enviar mensaje a todos los clientes WebSocket
def send_console_output(message):
    global ws_clients
    for client in ws_clients:
        try:
            client.send(message)
        except Exception as e:
            print('Error enviando mensaje a WebSocket:', e)

# Servidor HTTP y WebSocket en el mismo puerto
def iniciar():
    wifi_manager.cargar_credenciales_wifi()
    if wifi_manager.SSID and wifi_manager.PASSWORD:
        wifi_manager.conectar_wifi()
    else:
        wifi_manager.iniciar_ap()
    
    iniciar_servidor()

if __name__ == '__main__':
    iniciar()
