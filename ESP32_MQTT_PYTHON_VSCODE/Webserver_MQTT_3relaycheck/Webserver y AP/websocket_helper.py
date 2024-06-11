import usocket as socket

def websocket_server(addr, handler):
    s = socket.socket()
    s.bind(addr)
    s.listen(5)
    while True:
        cl, addr = s.accept()
        handler(cl, addr)

def websocket_handler(client, addr):
    try:
        while True:
            msg = client.recv(1024)
            if not msg:
                break
            client.send(msg)  # Echo message back to client (simple handler)
    except Exception as e:
        print("WebSocket error:", e)
    finally:
        client.close()
