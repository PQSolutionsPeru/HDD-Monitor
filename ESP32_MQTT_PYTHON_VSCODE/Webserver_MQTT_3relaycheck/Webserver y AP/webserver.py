import machine
import uasyncio as asyncio
import usocket as socket
import ujson
import os

main_script_running = False

index_html = """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configuración del ESP32</title>
</head>
<body>
    <h1>Configuración del ESP32</h1>
    <form action="/guardar" method="post">
        <label for="ssid">SSID:</label>
        <input type="text" id="ssid" name="ssid" required>
        <label for="password">Contraseña:</label>
        <input type="password" id="password" name="password" required>
        <button type="submit">Guardar y Conectar</button>
    </form>
    <form action="/reiniciar" method="get">
        <button type="submit">Reiniciar ESP32</button>
    </form>
    <h2>Subir Archivo</h2>
    <form action="/subir" method="post" enctype="multipart/form-data">
        <input type="file" name="file" required>
        <button type="submit">Subir</button>
    </form>
    <h2>Lista de Archivos</h2>
    <ul id="file-list"></ul>
    <h2>Controlar Programa Principal</h2>
    <form action="/control" method="post">
        <button type="submit" name="accion" value="iniciar">Iniciar Programa Principal</button>
        <button type="submit" name="accion" value="detener">Detener Programa Principal</button>
    </form>
    <h2>Estado del ESP32</h2>
    <button onclick="getEstado()">Ver Estado</button>
    <p id="estado"></p>
    <script>
        async function getEstado() {
            const response = await fetch('/estado');
            const estado = await response.json();
            document.getElementById('estado').innerText = `Memoria Libre: ${estado.memoria} bytes, Espacio Libre: ${estado.espacio_libre} bytes`;
        }

        async function listarArchivos() {
            const response = await fetch('/listar');
            const archivos = await response.json();
            const fileList = document.getElementById('file-list');
            fileList.innerHTML = '';
            archivos.archivos.forEach(file => {
                const li = document.createElement('li');
                li.textContent = file;
                const downloadLink = document.createElement('a');
                downloadLink.href = `/descargar/${file}`;
                downloadLink.textContent = ' Descargar';
                li.appendChild(downloadLink);
                const deleteLink = document.createElement('a');
                deleteLink.href = `/eliminar/${file}`;
                deleteLink.textContent = ' Eliminar';
                li.appendChild(deleteLink);
                fileList.appendChild(li);
            });
        }

        listarArchivos();
    </script>
</body>
</html>
"""

async def handle_client(client):
    request = client.recv(1024).decode()
    response = process_request(request)
    client.send(response)
    client.close()

def process_request(request):
    try:
        request_line = request.split('\r\n')[0]
        method, path, _ = request_line.split()
        if method == 'POST' and path == '/guardar':
            return guardar(request)
        elif path == '/reiniciar':
            return reiniciar()
        elif path == '/subir':
            return subir()
        elif path.startswith('/descargar'):
            return descargar(path.split('/')[-1])
        elif path.startswith('/eliminar'):
            return eliminar(path.split('/')[-1])
        elif path == '/listar':
            return listar()
        elif path == '/estado':
            return estado()
        elif method == 'POST' and path == '/control':
            return control(request)
        else:
            return send_file()
    except Exception as e:
        return 'HTTP/1.1 500 Internal Server Error\r\n\r\n' + str(e)

def guardar(request):
    headers, body = request.split('\r\n\r\n')
    data = ujson.loads(body)
    ssid = data['ssid']
    password = data['password']
    config = {'ssid': ssid, 'password': password}
    with open('wifi_config.json', 'w') as f:
        ujson.dump(config, f)
    machine.reset()
    return 'HTTP/1.1 200 OK\r\n\r\nGuardando credenciales y reiniciando...'

def reiniciar():
    machine.reset()
    return 'HTTP/1.1 200 OK\r\n\r\nReiniciando ESP32...'

def subir():
    # Implementa lógica de subida de archivos aquí
    return 'HTTP/1.1 200 OK\r\n\r\nArchivo subido exitosamente.'

def descargar(filename):
    # Implementa lógica de descarga de archivos aquí
    return 'HTTP/1.1 200 OK\r\n\r\n'

def eliminar(filename):
    os.remove('/' + filename)
    return 'HTTP/1.1 200 OK\r\n\r\nArchivo eliminado exitosamente.'

def listar():
    archivos = os.listdir('/')
    return 'HTTP/1.1 200 OK\r\n\r\n' + ujson.dumps({'archivos': archivos})

def estado():
    memoria = machine.mem_free()
    espacio = os.statvfs('/')
    espacio_libre = espacio[0] * espacio[3]
    return 'HTTP/1.1 200 OK\r\n\r\n' + ujson.dumps({'memoria': memoria, 'espacio_libre': espacio_libre})

def control(request):
    global main_script_running
    headers, body = request.split('\r\n\r\n')
    data = ujson.loads(body)
    if data['accion'] == 'iniciar':
        main_script_running = True
        asyncio.create_task(run_main_script())
    elif data['accion'] == 'detener':
        main_script_running = False
    return 'HTTP/1.1 200 OK\r\n\r\nAcción ejecutada.'

async def run_main_script():
    global main_script_running
    while main_script_running:
        # Aquí va el código del programa principal
        await asyncio.sleep(1)  # Simulando trabajo

def send_file():
    return 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + index_html

async def start_web_server():
    addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
    s = socket.socket()
    s.bind(addr)
    s.listen(5)
    print('Servidor web en', addr)

    while True:
        client, addr = s.accept()
        await handle_client(client)

