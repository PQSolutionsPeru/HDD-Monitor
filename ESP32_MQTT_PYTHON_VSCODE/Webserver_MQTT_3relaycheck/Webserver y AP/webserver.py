import usocket as socket
import machine
import ubinascii
import os
import esp32
import json
from wifi_manager import WiFiManager
import _thread
import log_manager
from websocket_helper import websocket_handler
from watchdog_manager import WatchdogManager

wifi_manager = WiFiManager()
watchdog_manager = WatchdogManager()

# HTML para las diferentes secciones del servidor web
html_template = """
<!DOCTYPE html>
<html>
<head>
    <style>
        body {{
            font-family: Arial, sans-serif;
            background-color: #000;
            color: #fff;
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
    <script>
        function setDarkMode() {{
            document.body.style.backgroundColor = '#000';
            document.body.style.color = '#fff';
        }}

        function setLightMode() {{
            document.body.style.backgroundColor = '#fff';
            document.body.style.color = '#000';
        }}

        function toggleDarkMode() {{
            if (document.body.style.backgroundColor === 'rgb(0, 0, 0)') {{
                setLightMode();
                localStorage.setItem('mode', 'light');
            }} else {{
                setDarkMode();
                localStorage.setItem('mode', 'dark');
            }}
        }}

        document.addEventListener('DOMContentLoaded', (event) => {{
            const mode = localStorage.getItem('mode');
            if (mode === 'dark') {{
                setDarkMode();
            }} else {{
                setLightMode();
            }}
        }});
    </script>
</head>
<body>
    <div class="sidenav">
        <a href="/">Inicio</a>
        <a href="/config">Configuración Wi-Fi</a>
        <a href="/reboot">Reiniciar</a>
        <a href="/manage">Gestión de Archivos</a>
        <a href="/logs">Logs</a>
        <a href="#" onclick="toggleDarkMode()">Modo Oscuro</a>
    </div>
    <div class="main">
        {content}
    </div>
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
    elif method == 'GET' and path == '/manage':
        response = servir_pagina_gestion_archivos()
    elif method == 'POST' and path == '/upload':
        response = manejar_post_upload(solicitud)
    elif method == 'POST' and path == '/start':
        response = manejar_post_start(solicitud)
    elif method == 'POST' and path == '/stop':
        response = manejar_post_stop()
    elif method == 'DELETE' and path.startswith('/file/'):
        response = manejar_delete_file(solicitud, path)
    elif method == 'GET' and path == '/logs':
        response = servir_pagina_logs()
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
        <label for="use_static_ip">Usar IP estática:</label>
        <input type="checkbox" id="use_static_ip" name="use_static_ip" onchange="toggleStaticIP()"><br>
        <div id="static_ip_config" style="display: none;">
            <label for="ip">IP:</label>
            <input type="text" id="ip" name="ip"><br>
            <label for="netmask">Netmask:</label>
            <input type="text" id="netmask" name="netmask"><br>
            <label for="gateway">Gateway:</label>
            <input type="text" id="gateway" name="gateway"><br>
        </div>
        <input type="submit" value="Submit">
    </form>
    <script>
        function toggleStaticIP() {
            var checkBox = document.getElementById("use_static_ip");
            var staticIPConfig = document.getElementById("static_ip_config");
            if (checkBox.checked == true){
                staticIPConfig.style.display = "block";
            } else {
                staticIPConfig.style.display = "none";
            }
        }
    </script>
    """
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar configuración de red Wi-Fi
def manejar_post_config(solicitud):
    print(f"Solicitud de configuración recibida: {solicitud}")
    ssid = parsear_datos_formulario(solicitud, 'ssid')
    password = parsear_datos_formulario(solicitud, 'password')
    use_static_ip = 'use_static_ip' in solicitud
    ip = parsear_datos_formulario(solicitud, 'ip') if use_static_ip else None
    netmask = parsear_datos_formulario(solicitud, 'netmask') if use_static_ip else None
    gateway = parsear_datos_formulario(solicitud, 'gateway') if use_static_ip else None
    print(f"SSID obtenido: {ssid}")
    print(f"Password obtenido: {password}")
    print(f"IP obtenida: {ip}")
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

# Servir página de gestión de archivos
def servir_pagina_gestion_archivos():
    archivos = os.listdir()
    archivo_list = ''.join([f'<li>{archivo} <button onclick="startScript(\'{archivo}\')">Iniciar</button> <button onclick="deleteFile(\'{archivo}\')">Eliminar</button></li>' for archivo in archivos if archivo.endswith('.py')])
    content = f"""
    <h2>Gestión de Archivos</h2>
    <form id="uploadForm" enctype="multipart/form-data">
        <input type="file" id="file" name="file"><br>
        <button type="button" onclick="uploadFile()">Subir</button>
    </form>
    <ul>{archivo_list}</ul>
    <div id="output"></div>
    <script>
        function uploadFile() {{
            var file = document.getElementById('file').files[0];
            var formData = new FormData();
            formData.append('file', file);
            var xhr = new XMLHttpRequest();
            xhr.open('POST', '/upload', true);
            xhr.onload = function() {{
                if (xhr.status == 200) {{
                    document.getElementById('output').innerText = 'Archivo subido exitosamente.';
                    location.reload();
                }} else {{
                    document.getElementById('output').innerText = 'Error al subir archivo.';
                }}
            }};
            xhr.send(formData);
        }}

        function startScript(filename) {{
            var xhr = new XMLHttpRequest();
            xhr.open('POST', '/start', true);
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.onload = function() {{
                if (xhr.status == 200) {{
                    document.getElementById('output').innerText = 'Script iniciado exitosamente.';
                }} else {{
                    document.getElementById('output').innerText = 'Error al iniciar script.';
                }}
            }};
            xhr.send(JSON.stringify({{ filename: filename }}));
        }}

        function deleteFile(filename) {{
            var xhr = new XMLHttpRequest();
            xhr.open('DELETE', '/file/' + filename, true);
            xhr.onload = function() {{
                if (xhr.status == 200) {{
                    document.getElementById('output').innerText = 'Archivo eliminado exitosamente.';
                    location.reload();
                }} else {{
                    document.getElementById('output').innerText = 'Error al eliminar archivo.';
                }}
            }};
            xhr.send();
        }}
    </script>
    """
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html_template.format(content=content)

# Manejar inicio de script
def manejar_post_start(solicitud):
    try:
        data = json.loads(solicitud.split('\r\n\r\n')[1])
        filename = data.get('filename')
        print(f"Iniciando script: {filename}")
        import __main__
        __main__.script = filename
        _thread.start_new_thread(lambda: exec(open(filename).read(), globals()), ())
        return 'HTTP/1.1 200 OK\r\n\r\nScript iniciado.'
    except Exception as e:
        print(f"Error al iniciar el script: {e}")
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al iniciar el script.'

# Manejar detención de script
def manejar_post_stop():
    try:
        import __main__
        __main__.script = None
        machine.reset()  # Reiniciar para detener cualquier script en ejecución
        return 'HTTP/1.1 200 OK\r\n\r\nScript detenido.'
    except Exception as e:
        print(f"Error al detener el script: {e}")
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al detener el script.'

# Manejar subida de archivos
def manejar_post_upload(solicitud):
    try:
        boundary = solicitud.split('\r\n')[1]
        file_data = solicitud.split(boundary)[1]
        file_header = file_data.split('\r\n')[1]
        file_content = file_data.split('\r\n\r\n')[1].rsplit('\r\n--', 1)[0]
        filename = file_header.split('filename=')[1].strip('"')
        with open(filename, 'wb') as f:
            f.write(file_content.encode('latin1'))  # Asegurarse de manejar los bytes correctamente
        return 'HTTP/1.1 200 OK\r\n\r\nArchivo subido exitosamente.'
    except Exception as e:
        print(f"Error al subir archivo: {e}")
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al subir archivo.'

# Manejar eliminación de archivos
def manejar_delete_file(solicitud, path):
    nombre_archivo = path.split('/file/')[1]
    try:
        os.remove(f'/{nombre_archivo}')
        return 'HTTP/1.1 200 OK\r\n\r\nArchivo eliminado exitosamente.'
    except Exception as e:
        print(f"Error al eliminar archivo: {e}")
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al eliminar el archivo.'

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
        return params.get(nombre_campo, '')
    except (IndexError, KeyError):
        print(f"Error al parsear el campo {nombre_campo}")
        return ''

# Manejar WebSocket
def manejar_websocket(cliente):
    global ws_clients
    ws_clients.append(cliente)
    try:
        while True:
            msg = cliente.recv(1024)
            if not msg:
                break
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
