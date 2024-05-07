import socket
import uasyncio as asyncio

async def http_server():
    try:
        addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
        s = socket.socket()
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(addr)
        s.listen(5)
        print("Servidor HTTP iniciado en " + str(addr))

        while True:
            try:
                client, _ = await asyncio.get_event_loop().sock_accept(s)
                print("Cliente conectado.")
                create_safe_task(handle_client, client)
            except Exception as e:
                print(f"Error aceptando conexión del cliente: {e}")

    except Exception as e:
        print(f"Error iniciando el servidor HTTP: {e}")

async def handle_client(client):
    try:
        stream = client.makefile('rwb', 0)
        request_line = await stream.readline()
        if request_line:
            request = request_line.decode().strip().split()
            method, path, protocol = request[0], request[1], request[2]
            print(f"Request: Method={method}, Path={path}, Protocol={protocol}")
            response = process_request(path)
            client.sendall(f'HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n{response}'.encode())
        else:
            print("Received empty request from client.")
    except Exception as e:
        print(f"Error handling client: {e}")
    finally:
        client.close()
        print("Cliente desconectado.")

def create_safe_task(coro_func, *args, **kwargs):
    print(f"Creating task for {coro_func.__name__}")
    asyncio.create_task(safe_task(coro_func, *args, **kwargs))

async def safe_task(coro_func, *args, **kwargs):
    try:
        await coro_func(*args, **kwargs)
    except Exception as e:
        print(f"Task failed with function {coro_func.__name__}: {e}")

def process_request(path):
    if path == '/memory':
        return memory_status()
    elif path == '/output':
        return read_output_log()
    elif path == '/logs':
        return read_logs()
    return '404 Not Found'

def memory_status():
    import gc
    gc.collect()
    total = gc.mem_alloc() + gc.mem_free()
    print(f"Memory Status - Total: {total} bytes, Used: {gc.mem_alloc()} bytes, Free: {gc.mem_free()} bytes")
    return f"Memory Total: {total} bytes, Used: {gc.mem_alloc()} bytes, Free: {gc.mem_free()} bytes"

def read_output_log():
    try:
        with open("output_log.txt", "r") as file:
            content = file.read()
            print(f"Output Log Read: {content}")
            return content
    except Exception as e:
        print(f"Error reading output log: {str(e)}")
        return f"Error reading output log: {str(e)}"

def read_logs():
    try:
        with open("logs.txt", "r") as file:
            content = file.read()
            print(f"Logs Read: {content}")
            return content
    except Exception as e:
        print(f"Error reading logs: {str(e)}")
        return f"Error reading logs: {str(e)}"
