import usocket as socket
import ussl as ssl
import machine
import ubinascii
import os
import esp32
import json
from wifi_manager import WiFiManager
from base64 import b64decode

wifi_manager = WiFiManager()

# HTML para la página de configuración Wi-Fi
html = """<!DOCTYPE html>
<html>
    <body>
        <form action="/config" method="post">
            <label for="ssid">SSID:</label>
            <input type="text" id="ssid" name="ssid"><br>
            <label for="password">Password:</label>
            <input type="password" id="password" name="password"><br>
            <input type="submit" value="Submit">
        </form>
    </body>
</html>"""

# Credenciales para autenticación básica
BASIC_AUTH_CREDENTIALS = "admin:password"  # Cambia esto por tus credenciales

# Servidor HTTPS
def iniciar_servidor_https():
    addr = socket.getaddrinfo('0.0.0.0', 443)[0][-1]
    s = socket.socket()
    s.bind(addr)
    s.listen(5)
    print('Escuchando en', addr)

    with open("key.pem", "r") as keyfile:
        key = keyfile.read()
    with open("cert.pem", "r") as certfile:
        cert = certfile.read()

    while True:
        cl, addr = s.accept()
        print('Cliente conectado desde', addr)
        try:
            cl = ssl.wrap_socket(cl, server_side=True, key=key, cert=cert)
        except Exception as e:
            print('Error al envolver el socket:', e)
            cl.close()
            continue
        request = cl.recv(1024).decode()
        if autenticar(request):
            manejar_solicitud(cl, request)
        else:
            cl.send('HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm="ESP32"\r\n\r\n')
        cl.close()

# Verificar autenticación básica
def autenticar(request):
    headers = request.split("\r\n")
    for header in headers:
        if header.startswith("Authorization: Basic "):
            encoded_credentials = header.split(" ")[2]
            decoded_credentials = b64decode(encoded_credentials).decode("utf-8")
            if decoded_credentials == BASIC_AUTH_CREDENTIALS:
                return True
    return False

# Manejar solicitudes HTTP
def manejar_solicitud(cliente, solicitud):
    request_line = solicitud.split('\n')[0]
    method, path, _ = request_line.split()
    
    if method == 'GET' and path == '/':
        response = servir_pagina_principal()
    elif method == 'POST' and path == '/config':
        response = manejar_post_config(solicitud)
    elif method == 'POST' and path == '/reboot':
        response = manejar_post_reboot()
    elif method == 'POST' and path == '/start':
        response = manejar_post_start()
    elif method == 'POST' and path == '/stop':
        response = manejar_post_stop()
    elif method == 'POST' and path == '/upload':
        response = manejar_post_upload(solicitud)
    elif method == 'DELETE' and path.startswith('/file'):
        response = manejar_delete_file(solicitud, path)
    else:
        response = 'HTTP/1.1 404 Not Found\r\n\r\n'

    cliente.send(response)

# Servir página de configuración inicial
def servir_pagina_principal():
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html

# Manejar configuración de red Wi-Fi
def manejar_post_config(solicitud):
    ssid = parsear_datos_formulario(solicitud, 'ssid')
    password = parsear_datos_formulario(solicitud, 'password')
    wifi_manager.guardar_credenciales_wifi(ssid, password)
    return 'HTTP/1.1 200 OK\r\n\r\nConfiguración guardada. Reiniciando...'

# Parsear datos del formulario
def parsear_datos_formulario(solicitud, nombre_campo):
    try:
        valor_campo = solicitud.split(f'name="{nombre_campo}"')[1].split('\r\n\r\n')[1].split('\r\n')[0]
    except:
        valor_campo = ''
    return valor_campo

# Reiniciar el ESP32
def manejar_post_reboot():
    machine.reset()
    return 'HTTP/1.1 200 OK\r\n\r\nReiniciando...'

# Iniciar el archivo main.py
def manejar_post_start():
    os.system('python main.py &')
    return 'HTTP/1.1 200 OK\r\n\r\nmain.py iniciado.'

# Detener el archivo main.py
def manejar_post_stop():
    os.system('pkill -f main.py')
    return 'HTTP/1.1 200 OK\r\n\r\nmain.py detenido.'

# Manejar subida de archivos
def manejar_post_upload(solicitud):
    # Implementar lógica para subir archivos aquí
    return 'HTTP/1.1 200 OK\r\n\r\nArchivo subido exitosamente.'

# Manejar eliminación de archivos
def manejar_delete_file(solicitud, path):
    nombre_archivo = path.split('/file/')[1]
    try:
        os.remove(f'/spiffs/{nombre_archivo}')
        return 'HTTP/1.1 200 OK\r\n\r\nArchivo eliminado exitosamente.'
    except:
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\nError al eliminar el archivo.'

# Servidor HTTPS separado
def iniciar():
    ssid, password = wifi_manager.obtener_credenciales_wifi()
    if ssid and password:
        wifi_manager.conectar_wifi()
        iniciar_servidor_https()
    else:
        wifi_manager.iniciar_ap()
        iniciar_servidor_https()

if __name__ == '__main__':
    iniciar()
