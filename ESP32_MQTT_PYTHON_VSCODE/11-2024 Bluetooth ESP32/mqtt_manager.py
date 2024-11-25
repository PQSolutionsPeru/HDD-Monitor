import usocket as socket
import ustruct as struct
import utime
from ubinascii import hexlify
import gc

def log_debug(msg, *args):
    """Función helper para logging con timestamp"""
    timestamp = utime.ticks_ms() // 1000  # Segundos desde el inicio
    if args:
        msg = msg % args
    print(f"[{timestamp}s] [DEBUG] {msg}")

def log_error(msg, *args):
    """Función helper para logging de errores con timestamp"""
    timestamp = utime.ticks_ms() // 1000
    if args:
        msg = msg % args
    print(f"[{timestamp}s] [ERROR] {msg}")

def log_info(msg, *args):
    """Función helper para logging de información con timestamp"""
    timestamp = utime.ticks_ms() // 1000
    if args:
        msg = msg % args
    print(f"[{timestamp}s] [INFO] {msg}")

class MQTTException(Exception):
    pass

class MQTTClientSimple:
    def __init__(
        self,
        client_id,
        server,
        port=0,
        user=None,
        password=None,
        keepalive=0,
        ssl=False,
        timeout=5,
    ):
        if port == 0:
            port = 8883 if ssl else 1883
        self.client_id = client_id
        self.sock = None
        self.server = server
        self.port = port
        self.ssl = ssl
        self.pid = 0
        self.cb = None
        self.user = user
        self.pswd = password
        self.keepalive = keepalive
        self.timeout = timeout
        self.last_ping = 0
        self.last_activity = 0
        log_info("MQTT Client initialized - Broker: %s:%d, Client ID: %s", server, port, client_id)

    def _send_str(self, s):
        try:
            log_debug("Enviando string de longitud %d bytes", len(s))
            self.sock.write(struct.pack("!H", len(s)))
            self.sock.write(s)
            self.last_activity = utime.ticks_ms()
        except Exception as e:
            log_error("Error en _send_str: %s", str(e))
            raise

    def connect(self, clean_session=True):
        try:
            # Memoria antes de la conexión
            gc.collect()
            mem_free = gc.mem_free()
            log_info("Memoria libre antes de conectar: %d bytes", mem_free)
            
            log_info("Iniciando conexión a %s:%d", self.server, self.port)
            self.sock = socket.socket()
            self.sock.settimeout(self.timeout)
            
            log_debug("Resolviendo dirección del servidor...")
            addr = socket.getaddrinfo(self.server, self.port)[0][-1]
            log_debug("Conectando a %s:%d", addr[0], addr[1])
            self.sock.connect(addr)
            
            log_debug("Socket conectado, preparando mensaje CONNECT")
            self.sock.settimeout(self.timeout)
            
            # Construir mensaje CONNECT
            premsg = bytearray(b"\x10\0\0\0\0\0")
            msg = bytearray(b"\x04MQTT\x04\x02\0\0")

            sz = 10 + 2 + len(self.client_id)
            msg[6] = clean_session << 1
            if self.user:
                sz += 2 + len(self.user) + 2 + len(self.pswd)
                msg[6] |= 0xC0

            i = 1
            while sz > 0x7F:
                premsg[i] = (sz & 0x7F) | 0x80
                sz >>= 7
                i += 1
            premsg[i] = sz

            log_debug("Enviando mensaje CONNECT...")
            self.sock.write(premsg, i + 2)
            self.sock.write(msg)
            self._send_str(self.client_id)
            if self.user:
                self._send_str(self.user)
                self._send_str(self.pswd)
            
            log_debug("Esperando CONNACK...")
            resp = self.sock.read(4)
            
            if not resp:
                raise MQTTException("No se recibió respuesta del servidor")
            
            log_debug("CONNACK recibido: %s", hexlify(resp))
            
            if len(resp) != 4:
                raise MQTTException(f"Respuesta de longitud inválida: {len(resp)}")
            
            if resp[0] != 0x20 or resp[1] != 0x02:
                raise MQTTException(f"Respuesta de conexión incorrecta: {hexlify(resp)}")
                
            if resp[3] != 0:
                raise MQTTException(f"Error de conexión, código: {resp[3]}")
                
            self.last_activity = utime.ticks_ms()
            log_info("Conexión MQTT establecida exitosamente")
            
            # Memoria después de la conexión
            gc.collect()
            mem_free_after = gc.mem_free()
            log_info("Memoria libre después de conectar: %d bytes (diferencia: %d bytes)", 
                    mem_free_after, mem_free_after - mem_free)
            
            return True
            
        except Exception as e:
            log_error("Error en connect: %s", str(e))
            if self.sock:
                try:
                    self.sock.close()
                except:
                    pass
            self.sock = None
            raise

    def disconnect(self):
        if self.sock:
            log_info("Desconectando del broker MQTT")
            try:
                self.sock.write(b"\xe0\0")
                self.sock.close()
                log_debug("Desconexión limpia completada")
            except Exception as e:
                log_error("Error en desconexión: %s", str(e))
            finally:
                self.sock = None
        gc.collect()

    def ping(self):
        try:
            log_debug("Enviando PINGREQ")
            self.sock.write(b"\xc0\0")
            self.last_ping = utime.ticks_ms()
        except Exception as e:
            log_error("Error en ping: %s", str(e))
            self.disconnect()
            raise

    def publish(self, topic, msg, retain=False, qos=0):
        if not self.sock:
            raise OSError("No hay conexión activa")
            
        try:
            log_info("Publicando mensaje - Topic: %s, Longitud: %d bytes, QoS: %d", 
                    topic, len(msg), qos)
            
            # Memoria antes de publicar
            gc.collect()
            mem_before = gc.mem_free()
            log_debug("Memoria libre antes de publicar: %d bytes", mem_before)
            
            pkt = bytearray(b"\x30\0\0\0")
            pkt[0] |= qos << 1 | retain
            sz = 2 + len(topic) + len(msg)
            if qos > 0:
                sz += 2
                
            i = 1
            while sz > 0x7F:
                pkt[i] = (sz & 0x7F) | 0x80
                sz >>= 7
                i += 1
            pkt[i] = sz
            
            log_debug("Enviando cabecera PUBLISH")
            self.sock.settimeout(self.timeout)
            self.sock.write(pkt, i + 1)
            
            log_debug("Enviando topic")
            self._send_str(topic)
            
            log_debug("Enviando payload")
            self.sock.write(msg)
            
            if qos == 0:
                log_debug("Mensaje QoS 0 enviado correctamente")
                return
                
            if qos == 1:
                log_debug("Esperando PUBACK para QoS 1")
                start_wait = utime.ticks_ms()
                while 1:
                    op = self.wait_msg()
                    if op == 0x40:
                        sz = self.sock.read(1)
                        assert sz == b"\x02"
                        rcv_pid = self.sock.read(2)
                        rcv_pid = rcv_pid[0] << 8 | rcv_pid[1]
                        log_debug("PUBACK recibido, pid: %d", rcv_pid)
                        # Memoria después de publicar
                        gc.collect()
                        mem_after = gc.mem_free()
                        log_debug("Memoria libre después de publicar: %d bytes (diferencia: %d bytes)", 
                                mem_after, mem_after - mem_before)
                        return
                    
                    if utime.ticks_diff(utime.ticks_ms(), start_wait) > self.timeout * 1000:
                        raise MQTTException("Timeout esperando PUBACK")
                        
        except Exception as e:
            log_error("Error en publish: %s", str(e))
            self.disconnect()
            raise

class MQTTClient(MQTTClientSimple):
    DELAY = 1
    DEBUG = True
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.last_check = 0
        self.last_ping = 0
        self.PING_INTERVAL = 20000  # 20 segundos
        self.CHECK_INTERVAL = 1000  # 1 segundo entre checks
        self.KEEPALIVE = 60  # 60 segundos
        log_debug("Cliente MQTT robusto inicializado")

    def wait_msg(self):
        try:
            if not self.sock:
                return None

            # Intentar leer solo si hay datos disponibles
            self.sock.setblocking(False)
            try:
                res = self.sock.read(1)
            except OSError as e:
                if e.args[0] == 11:  # EAGAIN - No hay datos disponibles
                    return None
                raise

            if res is None or res == b"":
                return None

            # Si llegamos aquí, hay datos para leer
            self.sock.setblocking(True)
            self.sock.settimeout(1)  # timeout corto para las siguientes lecturas
                
            if res == b"\xd0":  # PINGRESP
                sz = self.sock.read(1)[0]
                assert sz == 0
                log_debug("PINGRESP recibido")
                return None
                
            op = res[0]
            if op & 0xF0 != 0x30:
                return op
                
            sz = self._recv_len()
            topic_len = self.sock.read(2)
            topic_len = (topic_len[0] << 8) | topic_len[1]
            topic = self.sock.read(topic_len)
            sz -= topic_len + 2
            
            if op & 6:
                pid = self.sock.read(2)
                pid = pid[0] << 8 | pid[1]
                sz -= 2
                
            msg = self.sock.read(sz)
            if self.cb:
                self.cb(topic, msg)
                
            if op & 6 == 2:
                pkt = bytearray(b"\x40\x02\0\0")
                struct.pack_into("!H", pkt, 2, pid)
                self.sock.write(pkt)
                
            return op
            
        except Exception as e:
            if isinstance(e, OSError) and e.args[0] == 110:  # ETIMEDOUT
                return None  # Ignorar timeouts silenciosamente
            log_error("Error en wait_msg: %s", str(e))
            return None

    def check_msg(self):
        if not self.sock:
            return None
            
        current_time = utime.ticks_ms()
        
        # Verificar si es momento de hacer un check
        if utime.ticks_diff(current_time, self.last_check) < self.CHECK_INTERVAL:
            return None
            
        self.last_check = current_time
            
        # Verificar si es necesario hacer ping
        if utime.ticks_diff(current_time, self.last_ping) >= self.PING_INTERVAL:
            try:
                log_debug("Enviando ping de keepalive")
                self.ping()
                self.last_ping = current_time
            except Exception as e:
                log_error("Error en ping de keepalive: %s", str(e))
                return None

        return self.wait_msg()

class MQTTManager:
    def __init__(self, wifi_manager):
        self.wifi_manager = wifi_manager
        self.client = None
        self.message_queue = []
        self.MQTT_BROKER = "node02.myqtthub.com"
        self.MQTT_PORT = 1883
        self.MQTT_CLIENT_ID = "ESP32-PQ1"
        self.MQTT_USER = "ESP32-1"
        self.MQTT_PASSWORD = "esp32"
        self.last_publish_attempt = 0
        self.last_check = 0
        self.CHECK_INTERVAL = 1000  # 1 segundo entre checks
        self.PUBLISH_RETRY_INTERVAL = 5000  # 5 segundos entre reintentos
        log_info("MQTTManager inicializado - Broker: %s:%d", self.MQTT_BROKER, self.MQTT_PORT)

    def ensure_client(self):
        log_debug("Verificando conexión WiFi y cliente MQTT")
        self.wifi_manager.ensure_wifi_connected()
        try:
            if self.client is None or not self.client.sock:
                log_info("Cliente MQTT no disponible, reinicializando...")
                self.reinitialize_client()
            else:
                log_debug("Cliente MQTT OK")
        except Exception as e:
            log_error("Error en ensure_client: %s", str(e))
            self.reinitialize_client()

    def reinitialize_client(self):
        try:
            if self.client:
                try:
                    log_debug("Desconectando cliente MQTT existente")
                    self.client.disconnect()
                except Exception as e:
                    log_error("Error desconectando cliente anterior: %s", str(e))
                    
            log_info("Creando nuevo cliente MQTT")
            self.client = MQTTClient(
                self.MQTT_CLIENT_ID.encode('utf-8'),
                self.MQTT_BROKER,
                self.MQTT_PORT,
                user=self.MQTT_USER.encode('utf-8'),
                password=self.MQTT_PASSWORD.encode('utf-8'),
                ssl=False,
                timeout=5
            )
            
            log_debug("Conectando nuevo cliente MQTT")
            self.client.connect()
            log_info("Cliente MQTT conectado exitosamente")
            
        except Exception as e:
            log_error("Error en reinitialize_client: %s", str(e))
            self.client = None
            raise

    def reconnect(self):
        log_info("Iniciando reconexión MQTT")
        self.ensure_client()

    def publish_event(self, topic, message, max_retries=3):
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_publish_attempt) < self.PUBLISH_RETRY_INTERVAL:
            log_debug("Esperando intervalo entre reintentos (%d ms restantes)", 
                     self.PUBLISH_RETRY_INTERVAL - utime.ticks_diff(current_time, self.last_publish_attempt))
            return False

        self.last_publish_attempt = current_time
        
        try:
            log_info("Preparando publicación de evento")
            self.ensure_client()
            if not self.client:
                raise Exception("No hay cliente MQTT disponible")

            if isinstance(message, str):
                message = message.encode()
            if isinstance(topic, str):
                topic = topic.encode()

            log_info("Publicando mensaje - Topic: %s", topic)
            self.client.publish(topic, message, qos=0)
            log_info("Mensaje publicado exitosamente: %s", message)
            return True
            
        except Exception as e:
            log_error("Error al publicar evento: %s", str(e))
            self.message_queue.append({'topic': topic, 'message': message})
            self.client = None
            return False

    def process_queue(self):
        if not self.message_queue:
            return
            
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_publish_attempt) < self.PUBLISH_RETRY_INTERVAL:
            return

        log_info("Procesando cola de mensajes pendientes (%d mensajes)", len(self.message_queue))
        message = self.message_queue[0]
        if self.publish_event(message['topic'], message['message']):
            self.message_queue.pop(0)
            log_debug("Mensaje de cola enviado exitosamente")
        else:
            log_debug("No se pudo enviar mensaje de cola, se intentará más tarde")

    def check_connection(self):
        current_time = utime.ticks_ms()
        if utime.ticks_diff(current_time, self.last_check) < self.CHECK_INTERVAL:
            return
            
        self.last_check = current_time
        
        if self.client:
            try:
                self.client.check_msg()
            except Exception as e:
                log_error("Error en check_connection: %s", str(e))
                self.client = None