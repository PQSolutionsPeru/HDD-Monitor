import os
import json
import hashlib
import machine
import gc
import utime
from config.ota_config import OTAConfig

class OTAManager:
    def __init__(self, mqtt_manager=None):
        """Inicializa el gestor de actualizaciones OTA"""
        print("[OTA] Iniciando gestor de actualizaciones...")
        self.mqtt_manager = mqtt_manager
        self.config = OTAConfig()
        
        # Control de actualizaciones
        self.current_update = None
        self.update_buffer = {}
        self.received_chunks = set()
        self.total_chunks = 0
        self.is_updating = False
        
        # Últimos estados
        self.last_update = None
        self.last_error = None
        
        # Suscribirse a tópicos OTA si MQTT está disponible
        if self.mqtt_manager:
            self._subscribe_to_ota_topics()

            
    def _handle_list_files(self, request_data):
        """Maneja solicitudes de listado de archivos"""
        try:
            # Verificar directorio raíz y obtener lista de archivos
            files = []
            for filename in os.listdir():
                try:
                    # Obtener información del archivo
                    stat = os.stat(filename)
                    file_info = {
                        'name': filename,
                        'size': stat[6],  # stat[6] es el tamaño del archivo
                        'modified': utime.ticks_ms()  # En MicroPython no tenemos timestamp de modificación
                    }
                    files.append(file_info)
                except Exception as e:
                    print(f"[OTA] Error obteniendo info de {filename}: {e}")
                    continue

            # Enviar respuesta
            response = {
                'type': 'file_list',
                'files': files,
                'message_id': request_data.get('message_id', ''),
                'esp32_id': self.mqtt_manager.esp32_id
            }

            # Publicar en tópico de files específicamente
            if self.mqtt_manager:
                self.mqtt_manager.publish_event(
                    f"esp32/ota/{self.mqtt_manager.esp32_id}/files",
                    response,
                    qos=1
                )
                print(f"[OTA] Lista de {len(files)} archivos enviada")
                return True
            return False

        except Exception as e:
            print(f"[OTA] Error listando archivos: {e}")
            self._send_response("error", f"Error listing files: {str(e)}")
            return False

    def _subscribe_to_ota_topics(self):
        """Suscribe a los tópicos relacionados con OTA"""
        try:
            if not self.mqtt_manager:
                print("[OTA] Error: MQTT manager no disponible")
                return False
                
            esp32_id = self.mqtt_manager.esp32_id
            if not esp32_id:
                print("[OTA] Error: ESP32 ID no disponible")
                return False
                
            # Suscribirse a múltiples tópicos OTA
            topics = [
                f"esp32/ota/{esp32_id}/update",  # Para recibir actualizaciones
                f"esp32/ota/{esp32_id}/command", # Para comandos como list_files
                f"esp32/ota/{esp32_id}/status",  # Para reportar estado
                "esp32/ota/broadcast"            # Para actualizaciones broadcast
            ]
            
            # Primero publicar estado inicial
            initial_status = {
                'esp32_id': esp32_id,
                'status': 'ONLINE',
                'type': 'status',
                'timestamp': utime.ticks_ms(),
                'version': self.get_firmware_version(),
                'update_status': {
                    'ready_for_update': True,
                    'current_version': self.get_firmware_version()
                }
            }

            # Publicar estado inicial
            if self.mqtt_manager:
                self.mqtt_manager.publish_event(
                    f"esp32/ota/{esp32_id}/status",
                    initial_status,
                    qos=1
                )
                print("[OTA] Estado inicial publicado")
            
            # Suscribirse a tópicos
            for topic in topics:
                try:
                    self.mqtt_manager.subscribe(topic, self._handle_update_message)
                    print(f"[OTA] Suscrito a: {topic}")
                except Exception as e:
                    print(f"[OTA] Error suscribiendo a {topic}: {e}")
                    continue
            
            print("[OTA] Setup de tópicos completado")
            return True
                
        except Exception as e:
            print(f"[OTA] Error en suscripción: {e}")
            return False

    def _handle_update_message(self, topic: str, msg):
        """Procesa mensajes de actualización OTA"""
        try:
            # Decodificar tópico si viene en bytes
            if isinstance(topic, bytes):
                topic = topic.decode()

            print(f"[OTA] Mensaje recibido en: {topic}")
            
            # No procesar mensajes de configuración
            if 'config' in topic:
                return

            # Solo procesar mensajes OTA
            if 'ota' not in topic:
                return

            # Decodificar mensaje
            try:
                update_data = json.loads(msg.decode())
            except Exception as e:
                print(f"[OTA] Error decodificando mensaje: {e}")
                return

            # Verificar estructura básica para mensajes OTA
            if 'type' not in update_data:
                if 'status' in topic:  # Ignorar mensajes de estado sin tipo
                    return
                self._send_response("error", "Invalid message format")
                return
                
            # Procesar según tipo de mensaje
            msg_type = update_data.get('type', '')
            
            if msg_type == "list_files":
                self._handle_list_files(update_data)
            elif msg_type == "read_file":
                self._handle_file_request(update_data)
            elif msg_type == 'init':
                self._handle_init_update(update_data)
            elif msg_type == 'chunk':
                self._handle_chunk_update(update_data)
            elif msg_type == 'finish':
                self._handle_finish_update(update_data)
            else:
                if 'command' in topic:  # Solo enviar error para comandos desconocidos
                    self._send_response("error", f"Unknown message type: {msg_type}")
            
        except Exception as e:
            print(f"[OTA] Error procesando mensaje: {e}")
            if 'command' in topic:  # Solo enviar error para comandos
                self._send_response("error", str(e))

    def _handle_file_request(self, request_data):
        """Maneja solicitudes de lectura de archivos"""
        try:
            filename = request_data.get('filename')
            if not filename:
                self._send_response("error", "No filename specified")
                return
                
            # Verificar que el archivo existe
            try:
                if filename not in os.listdir():
                    self._send_response("error", f"File {filename} not found")
                    return
            except:
                self._send_response("error", "Error reading directory")
                return
                
            # Leer contenido
            try:
                with open(filename, 'r') as f:
                    content = f.read()
                    
                # Enviar contenido
                response = {
                    "type": "file_content",
                    "filename": filename,
                    "content": content,
                    "message_id": request_data.get('message_id', '')
                }
                
                self.mqtt_manager.publish_event(
                    f"esp32/ota/{self.mqtt_manager.esp32_id}/status",
                    response,
                    qos=1
                )
                print(f"[OTA] Contenido de {filename} enviado")
                
            except Exception as e:
                self._send_response("error", f"Error reading file: {str(e)}")
                
        except Exception as e:
            print(f"[OTA] Error procesando solicitud de archivo: {e}")
            self._send_response("error", str(e))
            
    def _handle_init_update(self, data):
        """Maneja el inicio de una actualización"""
        try:
            print(f"[OTA] Processing init update with data: {data}")
            
            if self.is_updating:
                print("[OTA] Update already in progress")
                self._send_response("error", "Update already in progress")
                return
                
            # Verificar campos requeridos
            required_fields = ['version', 'files', 'total_size']
            missing_fields = [field for field in required_fields if field not in data]
            if missing_fields:
                print(f"[OTA] Missing required fields: {missing_fields}")
                self._send_response("error", f"Missing required fields: {missing_fields}")
                return
                
            # Verificar espacio disponible
            import os
            try:
                fs_info = os.statvfs('/')
                free_space = fs_info[0] * fs_info[3]  # block_size * free_blocks
                print(f"[OTA] Free space: {free_space}, Required: {data['total_size'] * 1.5}")
                
                if free_space < data['total_size'] * 1.5:  # 50% extra para seguridad
                    print("[OTA] Insufficient space for update")
                    self._send_response("error", "Insufficient space for update")
                    return
            except Exception as e:
                print(f"[OTA] Error checking free space: {e}")
                self._send_response("error", f"Error checking space: {str(e)}")
                return
                
            # Inicializar actualización
            self.current_update = {
                'version': data['version'],
                'files': data['files'],
                'total_size': data['total_size'],
                'start_time': utime.ticks_ms()
            }
            
            self.update_buffer = {}
            self.received_chunks = set()
            self.total_chunks = 0
            self.is_updating = True
            
            # Crear directorio temporal si no existe
            try:
                os.mkdir('/update')
            except OSError as e:
                if e.args[0] != 17:  # 17 is EEXIST
                    raise
                
            print("[OTA] Ready for update - Sending response")
            self._send_response("ready", "Ready for update")
            
        except Exception as e:
            print(f"[OTA] Error in init update: {e}")
            import sys
            sys.print_exception(e)
            self._cleanup_update()
            self._send_response("error", str(e))
            
    def _handle_chunk_update(self, data):
        """Procesa un chunk de actualización"""
        try:
            if not self.is_updating:
                self._send_response("error", "No update in progress")
                return
                
            # Verificar campos requeridos
            required_fields = ['chunk_id', 'total_chunks', 'file_name', 'data', 'chunk_hash']
            if not all(field in data for field in required_fields):
                self._send_response("error", "Invalid chunk format")
                return
                
            # Verificar hash del chunk
            chunk_data = data['data'].encode()
            calculated_hash = hashlib.sha256(chunk_data).hexdigest()
            
            if calculated_hash != data['chunk_hash']:
                self._send_response("error", f"Chunk hash mismatch: {data['chunk_id']}")
                return
                
            # Almacenar chunk
            file_name = data['file_name']
            if file_name not in self.update_buffer:
                self.update_buffer[file_name] = {}
                
            self.update_buffer[file_name][data['chunk_id']] = chunk_data
            self.received_chunks.add(data['chunk_id'])
            self.total_chunks = data['total_chunks']
            
            # Reportar progreso
            progress = (len(self.received_chunks) / self.total_chunks) * 100
            self._send_response("progress", f"Progress: {progress:.1f}%")
            
        except Exception as e:
            print(f"[OTA] Error procesando chunk: {e}")
            self._cleanup_update()
            self._send_response("error", str(e))
            
    def _handle_finish_update(self, data):
        """Finaliza y aplica la actualización"""
        try:
            if not self.is_updating:
                self._send_response("error", "No update in progress")
                return
                
            # Verificar que todos los chunks fueron recibidos
            if len(self.received_chunks) != self.total_chunks:
                missing = self.total_chunks - len(self.received_chunks)
                self._send_response("error", f"Missing {missing} chunks")
                self._cleanup_update()
                return
                
            # Procesar cada archivo
            for file_name, chunks in self.update_buffer.items():
                # Ordenar y combinar chunks
                sorted_chunks = [chunks[i] for i in range(len(chunks))]
                file_content = b''.join(sorted_chunks)
                
                # Verificar hash del archivo si está disponible
                if 'file_hashes' in data and file_name in data['file_hashes']:
                    file_hash = hashlib.sha256(file_content).hexdigest()
                    if file_hash != data['file_hashes'][file_name]:
                        self._send_response("error", f"File hash mismatch: {file_name}")
                        self._cleanup_update()
                        return
                
                # Guardar archivo en directorio temporal
                with open(f'/update/{file_name}', 'wb') as f:
                    f.write(file_content)
                
            # Guardar información de la actualización
            update_info = {
                'version': self.current_update['version'],
                'timestamp': utime.ticks_ms(),
                'files': list(self.update_buffer.keys())
            }
            
            with open('/update/update_info.json', 'w') as f:
                json.dump(update_info, f)
                
            # Mover archivos a su ubicación final
            self._apply_update()
            
            # Limpiar y reiniciar
            self._cleanup_update()
            self._send_response("success", "Update completed successfully")
            
            # Reiniciar el dispositivo
            utime.sleep_ms(1000)
            machine.reset()
            
        except Exception as e:
            print(f"[OTA] Error finalizando actualización: {e}")
            self._cleanup_update()
            self._send_response("error", str(e))
            
    def _apply_update(self):
        """Aplica la actualización moviendo los archivos"""
        try:
            # Primero verificar que todos los archivos nuevos son válidos
            for file_name in self.update_buffer.keys():
                try:
                    # Intentar compilar si es un archivo .py
                    if file_name.endswith('.py'):
                        with open(f'/update/{file_name}', 'r') as f:
                            compile(f.read(), file_name, 'exec')
                except Exception as e:
                    raise Exception(f"Invalid Python file: {file_name} - {str(e)}")
                    
            # Si todos los archivos son válidos, hacer backup y aplicar
            try:
                os.mkdir('/backup')
            except:
                pass
                
            # Hacer backup de archivos existentes
            for file_name in self.update_buffer.keys():
                if file_name in os.listdir():
                    os.rename(file_name, f'/backup/{file_name}')
                    
            # Mover nuevos archivos
            for file_name in self.update_buffer.keys():
                os.rename(f'/update/{file_name}', f'/{file_name}')
                
        except Exception as e:
            # En caso de error, intentar restaurar backup
            print(f"[OTA] Error aplicando actualización: {e}")
            self._restore_backup()
            raise
            
    def _restore_backup(self):
        """Restaura archivos desde el backup"""
        try:
            if 'backup' in os.listdir():
                for file_name in os.listdir('/backup'):
                    if file_name in os.listdir():
                        os.remove(file_name)
                    os.rename(f'/backup/{file_name}', file_name)
        except Exception as e:
            print(f"[OTA] Error restaurando backup: {e}")
            
    def _cleanup_update(self):
        """Limpia archivos temporales de actualización"""
        try:
            # Limpiar variables
            self.current_update = None
            self.update_buffer = {}
            self.received_chunks = set()
            self.total_chunks = 0
            self.is_updating = False
            
            # Limpiar directorios temporales
            for dir_name in ['/update', '/backup']:
                if dir_name in os.listdir():
                    for file_name in os.listdir(dir_name):
                        os.remove(f'{dir_name}/{file_name}')
                    os.rmdir(dir_name)
                    
            # Forzar recolección de basura
            gc.collect()
            
        except Exception as e:
            print(f"[OTA] Error en limpieza: {e}")
            
    def _send_response(self, status, message):
        """Envía respuesta del proceso OTA vía MQTT"""
        try:
            if not self.mqtt_manager or not self.mqtt_manager.esp32_id:
                return
                
            response = {
                'esp32_id': self.mqtt_manager.esp32_id,
                'status': status,
                'message': message,
                'timestamp': utime.ticks_ms(),
                'type': 'ota_status'
            }
            
            # Agregar información de progreso si está disponible
            if self.current_update:
                response.update({
                    'version': self.current_update['version'],
                    'progress': (len(self.received_chunks) / self.total_chunks * 100) if self.total_chunks else 0,
                    'update_id': self.current_update.get('update_id', 'unknown')
                })
                
            # Publicar en dos tópicos para mejor visibilidad
            self.mqtt_manager.publish_event(
                f"esp32/ota/{self.mqtt_manager.esp32_id}/status",
                response,
                qos=1
            )
            
            # Crear nuevo diccionario para network_info
            network_response = response.copy()
            network_response['capabilities'] = ['ota']
            
            self.mqtt_manager.publish_event(
                "esp32/network_info",
                network_response,
                qos=1
            )
                
        except Exception as e:
            print(f"[OTA] Error enviando respuesta: {e}")
            
    def get_current_version(self):
        """Obtiene la versión actual del firmware"""
        try:
            if 'version.json' in os.listdir():
                with open('version.json', 'r') as f:
                    version_info = json.load(f)
                    return version_info.get('version')
        except:
            pass
        return "0.0.0"  # Versión por defecto
        
    def get_update_status(self):
        """Obtiene el estado actual del proceso de actualización"""
        return {
            'is_updating': self.is_updating,
            'current_version': self.get_current_version(),
            'update_progress': (len(self.received_chunks) / self.total_chunks * 100) if self.is_updating and self.total_chunks else 0,
            'last_update': self.last_update,
            'last_error': self.last_error
        }