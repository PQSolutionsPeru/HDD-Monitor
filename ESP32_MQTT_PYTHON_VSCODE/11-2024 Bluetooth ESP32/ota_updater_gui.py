import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import paho.mqtt.client as mqtt
import json
import os
import hashlib
import base64
import threading
import queue
import time
import ssl
from datetime import datetime
from typing import Dict, Any, Optional, List 

class OTAUpdaterGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("ESP32 OTA Updater")
        self.root.geometry("1000x800")
        
        # Variables MQTT
        self.mqtt_client = None
        self.connected_devices = {}  # {esp32_id: last_seen_timestamp}
        self.update_status = {}      # {esp32_id: status_dict}
        self.message_queue = queue.Queue()
        
        # Variables de actualización
        self.selected_files = []
        self.chunk_size = 4096
        self.current_update = None
        
        self._setup_gui()
        self._connect_mqtt()  # Primero conectar MQTT
        time.sleep(0.5)      # Dar tiempo para la conexión
        self._start_message_processing()  # Luego iniciar procesamiento
        
    def _setup_gui(self):
        """Configura la interfaz gráfica"""
        # Frame principal
        main_frame = ttk.Frame(self.root, padding="5")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # === Panel izquierdo (Dispositivos y Archivos del ESP32) ===
        left_frame = ttk.LabelFrame(main_frame, text="Gestión del ESP32", padding="5")
        left_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), padx=5, pady=5)
        
        # Panel de Dispositivos
        devices_frame = ttk.LabelFrame(left_frame, text="Dispositivos Conectados")
        devices_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        self.devices_listbox = tk.Listbox(devices_frame, height=5, width=40)
        self.devices_listbox.grid(row=0, column=0, columnspan=2, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        ttk.Button(devices_frame, text="Ver Info", command=self.view_device_info).grid(row=1, column=0, padx=2, pady=5)
        ttk.Button(devices_frame, text="Reset ESP32", command=self.reset_esp32).grid(row=1, column=1, padx=2, pady=5)
        
        # Panel de Archivos del ESP32
        esp_files_frame = ttk.LabelFrame(left_frame, text="Archivos del ESP32")
        esp_files_frame.grid(row=1, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        ttk.Button(esp_files_frame, text="Listar Archivos", command=self.list_esp32_files).grid(row=0, column=0, sticky=tk.W, pady=5)
        
        self.esp_files_listbox = tk.Listbox(esp_files_frame, height=10, width=40)
        self.esp_files_listbox.grid(row=1, column=0, columnspan=2, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        # Frame de botones para archivos ESP32
        esp_buttons_frame = ttk.Frame(esp_files_frame)
        esp_buttons_frame.grid(row=2, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=5)
        
        ttk.Button(esp_buttons_frame, text="Editar", command=self.edit_esp_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(esp_buttons_frame, text="Eliminar", command=self.delete_esp_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(esp_buttons_frame, text="Ejecutar Script", command=self.execute_script).pack(side=tk.LEFT, padx=2)
        ttk.Button(esp_buttons_frame, text="Detener Script", command=self.stop_script).pack(side=tk.LEFT, padx=2)
        
        # === Panel derecho (Actualización y Logs) ===
        right_frame = ttk.LabelFrame(main_frame, text="Actualización y Logs", padding="5")
        right_frame.grid(row=0, column=1, sticky=(tk.W, tk.E, tk.N, tk.S), padx=5, pady=5)
        
        # Panel de Actualización
        update_frame = ttk.LabelFrame(right_frame, text="Actualización")
        update_frame.grid(row=0, column=0, sticky=(tk.W, tk.E), pady=5)
        
        ttk.Button(update_frame, text="Seleccionar Archivos", command=self._select_files).grid(row=0, column=0, sticky=tk.W, pady=5)
        
        ttk.Label(update_frame, text="Archivos Seleccionados:").grid(row=1, column=0, sticky=tk.W)
        self.local_files_listbox = tk.Listbox(update_frame, height=5, width=60)
        self.local_files_listbox.grid(row=2, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        # Frame para versión y botón de actualización
        version_frame = ttk.Frame(update_frame)
        version_frame.grid(row=3, column=0, sticky=(tk.W, tk.E), pady=5)
        
        ttk.Label(version_frame, text="Versión:").pack(side=tk.LEFT, padx=5)
        self.version_entry = ttk.Entry(version_frame, width=10)
        self.version_entry.pack(side=tk.LEFT, padx=5)
        self.version_entry.insert(0, "1.0.0")
        
        self.update_button = ttk.Button(version_frame, text="Iniciar Actualización", command=self._start_update)
        self.update_button.pack(side=tk.LEFT, padx=20)
        
        # Progreso
        ttk.Label(right_frame, text="Progreso de Actualización:").grid(row=1, column=0, sticky=tk.W)
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(right_frame, length=400, mode='determinate', variable=self.progress_var)
        self.progress_bar.grid(row=2, column=0, sticky=(tk.W, tk.E), pady=5)
        
        # Estado actual
        self.status_label = ttk.Label(right_frame, text="Estado: Esperando")
        self.status_label.grid(row=3, column=0, sticky=tk.W, pady=5)
        
        # Logs
        ttk.Label(right_frame, text="Logs:").grid(row=4, column=0, sticky=tk.W)
        self.log_text = tk.Text(right_frame, height=20, width=60)
        self.log_text.grid(row=5, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        
        # Scrollbar para logs
        log_scrollbar = ttk.Scrollbar(right_frame, orient=tk.VERTICAL, command=self.log_text.yview)
        log_scrollbar.grid(row=5, column=1, sticky=(tk.N, tk.S))
        self.log_text['yscrollcommand'] = log_scrollbar.set
        
        # Configurar expansión de widgets
        main_frame.columnconfigure(1, weight=1)
        main_frame.rowconfigure(0, weight=1)
        right_frame.columnconfigure(0, weight=1)
        right_frame.rowconfigure(5, weight=1)
        
    def _connect_mqtt(self):
        """Conecta al broker MQTT"""
        try:
            # Crear cliente MQTT especificando la versión de la API
            self.mqtt_client = mqtt.Client(
                client_id="ota_updater",
                callback_api_version=mqtt.CallbackAPIVersion.VERSION2
            )
            
            # Configurar callbacks
            self.mqtt_client.on_connect = self._on_mqtt_connect
            self.mqtt_client.on_message = self._on_mqtt_message
            
            # Configurar SSL y credenciales
            context = ssl.create_default_context()
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE
            
            self.mqtt_client.tls_set_context(context)
            self.mqtt_client.username_pw_set("ota_updater", "ota_updater")
            
            # Conectar
            self._log("Conectando a broker MQTT...")
            self.mqtt_client.connect("node02.myqtthub.com", 8883, 60)
            self.mqtt_client.loop_start()
            
        except Exception as e:
            self._log(f"Error conectando a MQTT: {e}")
            messagebox.showerror("Error", f"No se pudo conectar al broker MQTT: {e}")
            
    def _on_mqtt_connect(self, client, userdata, flags, rc, properties=None):
        """Callback de conexión MQTT"""
        rc_codes = {
            0: "Conexión exitosa",
            1: "Versión de protocolo incorrecta",
            2: "Identificador de cliente inválido", 
            3: "Servidor no disponible",
            4: "Usuario/contraseña incorrectos",
            5: "No autorizado"
        }
        
        if rc == 0:
            self._log("Conectado al broker MQTT")
            topics = [
                ("esp32/ota/+/status", 1),
                ("esp32/ota/+/response", 1),
                ("esp32/ota/+/files", 1),
                ("esp32/network_info", 1),
                ("system/status/+", 1)
            ]
            for topic, qos in topics:
                self.mqtt_client.subscribe(topic, qos)
                self._log(f"Suscrito a: {topic}")
        else:
            error_msg = rc_codes.get(rc, f"Error desconocido ({rc})")
            self._log(f"Error de conexión MQTT: {error_msg}")
            
    def _on_mqtt_message(self, client, userdata, msg):
        try:
            if msg.retain:
                return

            payload = json.loads(msg.payload.decode())
            
            # Manejar lista de archivos
            if "files" in msg.topic:
                self.handle_file_list(payload)
                return
                
            # Solo procesar mensajes de estado OTA
            if msg.topic.startswith("esp32/ota/") and "/status" in msg.topic:
                esp32_id = msg.topic.split('/')[2]  # Extraer ID del tópico
                self.connected_devices[esp32_id] = {
                    'last_seen': time.time(),
                    'status': payload.get('status', 'UNKNOWN'),
                    'version': payload.get('version', 'Unknown'),
                    'capabilities': payload.get('capabilities', []),
                    'ip': payload.get('IP'),
                    'mac': payload.get('MAC')
                }
                self._update_devices_list()
                
        except Exception as e:
            self._log(f"Error procesando mensaje MQTT: {e}")
            print(f"Payload que causó error: {msg.payload}")  # Para debug

    def _handle_ota_status(self, payload: Dict[str, Any]):
        """Maneja estados de actualización OTA"""
        try:
            esp32_id = payload.get('esp32_id')
            if not esp32_id:
                return
                
            if 'progress' in payload:
                self.progress_var.set(payload['progress'])
            if 'status' in payload:
                self.status_label['text'] = f"Estado: {payload['status']}"
            if 'message' in payload:
                self._log(payload['message'])
                
        except Exception as e:
            self._log(f"Error procesando estado OTA: {e}")

    def view_device_info(self):
        """Muestra información del dispositivo seleccionado"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            device_info = self.connected_devices.get(esp32_id)
            
            if not device_info:
                messagebox.showwarning("Advertencia", "No hay información disponible")
                return
                
            # Crear ventana de información
            info_window = tk.Toplevel(self.root)
            info_window.title(f"Información de {esp32_id}")
            info_window.geometry("400x300")
            
            # Crear tabla de información
            info_frame = ttk.Frame(info_window, padding="10")
            info_frame.pack(fill=tk.BOTH, expand=True)
            
            # Agregar información
            row = 0
            for key, value in {
                "ID": esp32_id,
                "Estado": device_info.get('status', 'Desconocido'),
                "IP": device_info.get('ip', 'No disponible'),
                "MAC": device_info.get('mac', 'No disponible'),
                "Última vez visto": datetime.fromtimestamp(device_info.get('last_seen', 0)).strftime('%Y-%m-%d %H:%M:%S')
            }.items():
                ttk.Label(info_frame, text=f"{key}:", font=('TkDefaultFont', 10, 'bold')).grid(
                    row=row, column=0, sticky=tk.W, pady=5
                )
                ttk.Label(info_frame, text=str(value)).grid(
                    row=row, column=1, sticky=tk.W, padx=10, pady=5
                )
                row += 1
                
        except Exception as e:
            self._log(f"Error mostrando información: {e}")
            messagebox.showerror("Error", f"Error mostrando información: {e}")
           
    def _start_message_processing(self):
        """Inicia el procesamiento de mensajes MQTT"""
        try:
            while self.message_queue.qsize():
                topic, payload = self.message_queue.get_nowait()
                self._process_mqtt_message(topic, payload)
        except queue.Empty:
            pass
        finally:
            # Programar siguiente verificación
            self.root.after(100, self._start_message_processing)
            
    def _process_mqtt_message(self, topic, payload):
        """Procesa mensajes MQTT recibidos"""
        try:
            if "network_info" in topic:
                # Actualizar lista de dispositivos
                if 'esp32_id' in payload:
                    esp32_id = payload['esp32_id']
                    self.connected_devices[esp32_id] = time.time()
                    self._update_devices_list()
                    
            elif "ota" in topic and "status" in topic:
                # Actualizar estado de OTA
                esp32_id = payload.get('esp32_id')
                if esp32_id:
                    self.update_status[esp32_id] = payload
                    self._update_progress(payload)
                    
        except Exception as e:
            self._log(f"Error procesando mensaje: {e}")
            
    def _handle_network_info(self, payload: Dict[str, Any]):
        """Maneja los mensajes de información de red de los ESP32"""
        try:
            esp32_id = payload.get('esp32_id')
            if not esp32_id:
                return
                
            # Actualizar lista de dispositivos
            self.connected_devices[esp32_id] = {
                'last_seen': time.time(),
                'status': payload.get('status', 'UNKNOWN'),
                'ip': payload.get('IP', payload.get('ip')),  # Manejar ambos formatos
                'mac': payload.get('MAC', payload.get('mac')),  # Manejar ambos formatos
                'version': payload.get('version', 'Unknown'),
                'capabilities': payload.get('capabilities', [])
            }
            
            # Log detallado para debug
            print(f"Dispositivo actualizado: {esp32_id}")
            print(f"Info completa: {self.connected_devices[esp32_id]}")
            
            self._update_devices_list()
            self._log(f"Dispositivo detectado: {esp32_id} (IP: {self.connected_devices[esp32_id]['ip']})")
            
        except Exception as e:
            self._log(f"Error procesando info de red: {e}")
            print(f"Payload que causó error: {payload}")  # Para debug

    def _update_devices_list(self):
        """Actualiza la lista de dispositivos en la GUI"""
        try:
            self.devices_listbox.delete(0, tk.END)
            current_time = time.time()
            
            # Filtrar dispositivos activos (últimos 5 minutos)
            active_devices = {
                esp32_id: info
                for esp32_id, info in self.connected_devices.items()
                if current_time - info['last_seen'] < 300  # 5 minutos
            }
            
            # Agregar a la lista
            for esp32_id in active_devices:
                self.devices_listbox.insert(tk.END, esp32_id)
                
        except Exception as e:
            self._log(f"Error actualizando lista: {e}")
            
    def _select_files(self):
        """Abre diálogo para seleccionar archivos"""
        files = filedialog.askopenfilenames(
            title="Seleccionar Archivos",
            filetypes=[
                ("Python Files", "*.py"),
                ("JSON Files", "*.json"),
                ("All Files", "*.*")
            ]
        )
        
        if files:
            self.selected_files = list(files)
            self.files_listbox.delete(0, tk.END)
            for file in self.selected_files:
                self.files_listbox.insert(tk.END, os.path.basename(file))
                
    def _start_update(self):
        """Inicia el proceso de actualización"""
        if not self.selected_files:
            messagebox.showwarning("Advertencia", "Seleccione archivos para actualizar")
            return
            
        selection = self.devices_listbox.curselection()
        if not selection:
            messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
            return
            
        esp32_id = self.devices_listbox.get(selection[0])
        version = self.version_entry.get().strip()
        
        if not version:
            messagebox.showwarning("Advertencia", "Ingrese una versión")
            return
            
        # Preparar actualización
        try:
            self._log(f"Preparando actualización para {esp32_id}")
            
            # Calcular tamaño total y hashes
            total_size = 0
            file_hashes = {}
            
            for file_path in self.selected_files:
                file_size = os.path.getsize(file_path)
                total_size += file_size
                
                with open(file_path, 'rb') as f:
                    content = f.read()
                    file_hashes[os.path.basename(file_path)] = hashlib.sha256(content).hexdigest()
                    
            # Mensaje inicial
            init_message = {
                "type": "init",
                "version": version,
                "files": [os.path.basename(f) for f in self.selected_files],
                "total_size": total_size
            }
            
            # Enviar mensaje inicial
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/update",
                json.dumps(init_message)
            )
            
            self._log("Enviando archivos...")
            self.update_button.state(['disabled'])
            
            # Iniciar envío de archivos en un hilo separado
            self.current_update = {
                'esp32_id': esp32_id,
                'version': version,
                'files': self.selected_files,
                'file_hashes': file_hashes
            }
            
            thread = threading.Thread(target=self._send_files)
            thread.daemon = True
            thread.start()
            
        except Exception as e:
            self._log(f"Error iniciando actualización: {e}")
            messagebox.showerror("Error", f"Error iniciando actualización: {e}")
            
    def _send_files(self):
        """Envía archivos en chunks"""
        try:
            esp32_id = self.current_update['esp32_id']
            total_chunks = 0
            current_chunk = 0
            
            # Calcular total de chunks
            for file_path in self.current_update['files']:
                size = os.path.getsize(file_path)
                total_chunks += (size + self.chunk_size - 1) // self.chunk_size
                
            # Enviar cada archivo
            for file_path in self.current_update['files']:
                file_name = os.path.basename(file_path)
                self._log(f"Enviando {file_name}")
                
                with open(file_path, 'rb') as f:
                    chunk_id = 0
                    while True:
                        chunk = f.read(self.chunk_size)
                        if not chunk:
                            break
                            
                        # Preparar mensaje de chunk
                        chunk_message = {
                            "type": "chunk",
                            "chunk_id": chunk_id,
                            "total_chunks": total_chunks,
                            "file_name": file_name,
                            "data": base64.b64encode(chunk).decode('utf-8'),
                            "chunk_hash": hashlib.sha256(chunk).hexdigest()
                        }
                        
                        # Enviar chunk
                        self.mqtt_client.publish(
                            f"esp32/ota/{esp32_id}/update",
                            json.dumps(chunk_message)
                        )
                        
                        chunk_id += 1
                        current_chunk += 1
                        self._update_progress({
                            'progress': (current_chunk / total_chunks) * 100
                        })
                        
                        # Pequeña pausa entre chunks
                        time.sleep(0.1)
                        
            # Enviar mensaje de finalización
            finish_message = {
                "type": "finish",
                "version": self.current_update['version'],
                "file_hashes": self.current_update['file_hashes']
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/update",
                json.dumps(finish_message)
            )
            
            self._log("Actualización completada")
            self.root.after(0, lambda: self.update_button.state(['!disabled']))
            
        except Exception as e:
            self._log(f"Error enviando archivos: {e}")
            self.root.after(0, lambda: self.update_button.state(['!disabled']))
            self.root.after(0, lambda: messagebox.showerror("Error", f"Error enviando archivos: {e}"))

    def add_view_buttons(self):
        """Agrega botones de control y visualización"""
        button_frame = ttk.Frame(self.left_frame)
        button_frame.grid(row=5, column=0, sticky=(tk.W, tk.E), pady=5)
        
        # Botones para gestión de archivos
        ttk.Button(button_frame, text="Listar Archivos", command=self.list_esp32_files).pack(side=tk.LEFT, padx=2)
        ttk.Button(button_frame, text="Editar", command=self.edit_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(button_frame, text="Eliminar", command=self.delete_file).pack(side=tk.LEFT, padx=2)
        
        # Botones para control del ESP32
        ttk.Button(button_frame, text="Reset ESP32", command=self.reset_esp32).pack(side=tk.LEFT, padx=2)
        ttk.Button(button_frame, text="Iniciar Main", command=self.start_main).pack(side=tk.LEFT, padx=2)
        ttk.Button(button_frame, text="Detener Main", command=self.stop_main).pack(side=tk.LEFT, padx=2)

    def list_esp32_files(self):
        """Solicita y muestra la lista de archivos del ESP32"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            
            # Enviar solicitud de lista de archivos
            request = {
                "type": "list_files",
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(request),
                qos=1
            )
            self._log("Solicitando lista de archivos...")
            
        except Exception as e:
            self._log(f"Error listando archivos: {e}")

    def edit_file(self):
        """Abre ventana para editar archivo seleccionado"""
        try:
            selection = self.files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un archivo")
                return
                
            filename = self.files_listbox.get(selection[0])
            
            # Crear ventana de edición
            edit_window = tk.Toplevel(self.root)
            edit_window.title(f"Editar {filename}")
            edit_window.geometry("800x600")
            
            # Área de edición
            edit_frame = ttk.Frame(edit_window, padding="5")
            edit_frame.pack(fill=tk.BOTH, expand=True)
            
            editor = tk.Text(edit_frame, wrap=tk.NONE)
            editor.pack(fill=tk.BOTH, expand=True)
            
            # Botones de control
            button_frame = ttk.Frame(edit_window)
            button_frame.pack(fill=tk.X, pady=5)
            
            ttk.Button(button_frame, text="Guardar", 
                    command=lambda: self.save_file_changes(filename, editor.get("1.0", tk.END))).pack(side=tk.RIGHT, padx=5)
            
        except Exception as e:
            self._log(f"Error editando archivo: {e}")

    def save_file_changes(self, filename, content):
        """Guarda cambios en un archivo"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            
            # Enviar contenido actualizado
            update_request = {
                "type": "update_file",
                "filename": filename,
                "content": content,
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(update_request),
                qos=1
            )
            self._log(f"Guardando cambios en {filename}...")
            
        except Exception as e:
            self._log(f"Error guardando cambios: {e}")

    def delete_file(self):
        """Elimina archivo seleccionado"""
        try:
            selection = self.files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un archivo")
                return
                
            filename = self.files_listbox.get(selection[0])
            if messagebox.askyesno("Confirmar", f"¿Desea eliminar {filename}?"):
                esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
                
                delete_request = {
                    "type": "delete_file",
                    "filename": filename,
                    "message_id": str(int(time.time() * 1000))
                }
                
                self.mqtt_client.publish(
                    f"esp32/ota/{esp32_id}/command",
                    json.dumps(delete_request),
                    qos=1
                )
                self._log(f"Eliminando {filename}...")
                
        except Exception as e:
            self._log(f"Error eliminando archivo: {e}")

    def reset_esp32(self):
        """Envía comando de reset al ESP32"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            if messagebox.askyesno("Confirmar", "¿Desea reiniciar el ESP32?"):
                reset_request = {
                    "type": "reset",
                    "message_id": str(int(time.time() * 1000))
                }
                
                self.mqtt_client.publish(
                    f"esp32/ota/{esp32_id}/command",
                    json.dumps(reset_request),
                    qos=1
                )
                self._log("Enviando comando de reset...")
                
        except Exception as e:
            self._log(f"Error enviando reset: {e}")

    def start_main(self):
        """Inicia el script main.py"""
        self._control_main("start")

    def stop_main(self):
        """Detiene el script main.py"""
        self._control_main("stop")

    def _control_main(self, action):
        """Controla la ejecución del script main.py"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            
            control_request = {
                "type": "control_script",
                "script": "main.py",
                "action": action,
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(control_request),
                qos=1
            )
            self._log(f"{action.capitalize()}ing main.py...")
            
        except Exception as e:
            self._log(f"Error controlando script: {e}")

    def view_file_content(self):
        """Solicita y muestra el contenido del archivo en el ESP32"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            file_selection = self.files_listbox.curselection()
            if not file_selection:
                messagebox.showwarning("Advertencia", "Seleccione un archivo")
                return
                
            filename = self.files_listbox.get(file_selection[0])
            
            # Enviar solicitud de lectura
            read_request = {
                "type": "read_file",
                "filename": filename,
                "message_id": str(int(time.time() * 1000))
            }
            
            # Mostrar ventana de contenido
            self.content_window = tk.Toplevel(self.root)
            self.content_window.title(f"Contenido de {filename}")
            self.content_window.geometry("800x600")
            
            # Área de texto con scrollbars
            text_frame = ttk.Frame(self.content_window)
            text_frame.pack(expand=True, fill=tk.BOTH, padx=5, pady=5)
            
            self.content_text = tk.Text(text_frame, wrap=tk.NONE)
            ys = ttk.Scrollbar(text_frame, orient='vertical', command=self.content_text.yview)
            xs = ttk.Scrollbar(text_frame, orient='horizontal', command=self.content_text.xview)
            self.content_text['yscrollcommand'] = ys.set
            self.content_text['xscrollcommand'] = xs.set
            
            self.content_text.grid(column=0, row=0, sticky=(tk.N,tk.S,tk.E,tk.W))
            ys.grid(column=1, row=0, sticky=(tk.N,tk.S))
            xs.grid(column=0, row=1, sticky=(tk.E,tk.W))
            
            text_frame.grid_columnconfigure(0, weight=1)
            text_frame.grid_rowconfigure(0, weight=1)
            
            self.content_text.insert('1.0', "Cargando contenido...")
            
            # Publicar solicitud
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/request",
                json.dumps(read_request),
                qos=1
            )
            self._log(f"Solicitando contenido de {filename}")
            
        except Exception as e:
            self._log(f"Error solicitando contenido: {e}")
            messagebox.showerror("Error", f"Error solicitando contenido: {e}")

    def handle_file_content(self, payload):
        """Maneja la respuesta con el contenido del archivo"""
        try:
            if hasattr(self, 'content_text'):
                self.content_text.delete('1.0', tk.END)
                self.content_text.insert('1.0', payload.get('content', ''))
        except Exception as e:
            self._log(f"Error mostrando contenido: {e}")
            
    def _update_progress(self, status):
        """Actualiza la barra de progreso y estado"""
        try:
            if 'progress' in status:
                self.progress_var.set(status['progress'])
                
            if 'status' in status:
                self.status_label['text'] = f"Estado: {status['status']}"
                if 'message' in status:
                    self._log(status['message'])
                    
        except Exception as e:
            print(f"Error actualizando progreso: {e}")

    def list_esp32_files(self):
        """Solicita lista de archivos del ESP32"""
        try:
            selection = self.devices_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un dispositivo")
                return
                
            esp32_id = self.devices_listbox.get(selection[0])
            
            request = {
                "type": "list_files",
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(request),
                qos=1
            )
            self._log("Solicitando lista de archivos del ESP32...")
            
        except Exception as e:
            self._log(f"Error listando archivos: {e}")

    def handle_file_list(self, payload):
        """Maneja la respuesta con la lista de archivos"""
        try:
            self._log("Recibida lista de archivos")
            self.esp_files_listbox.delete(0, tk.END)
            files = payload.get('files', [])
            for file_info in files:
                filename = file_info['name']
                size = file_info['size']
                modified = file_info.get('modified', 0)
                
                # Formatear tamaño
                if size < 1024:
                    size_str = f"{size} B"
                elif size < 1024*1024:
                    size_str = f"{size/1024:.1f} KB"
                else:
                    size_str = f"{size/(1024*1024):.1f} MB"
                    
                self.esp_files_listbox.insert(tk.END, f"{filename} ({size_str})")
                self._log(f"Archivo encontrado: {filename} - {size_str}")
                
        except Exception as e:
            self._log(f"Error mostrando lista de archivos: {e}")
            print(f"Payload que causó error: {payload}")  # Para debug

    def edit_esp_file(self):
        """Abre editor para archivo del ESP32"""
        try:
            selection = self.esp_files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un archivo del ESP32")
                return
                
            filename = self.esp_files_listbox.get(selection[0]).split(" (")[0]
            
            # Solicitar contenido del archivo
            esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
            request = {
                "type": "read_file",
                "filename": filename,
                "message_id": str(int(time.time() * 1000))
            }
            
            # Crear ventana de edición
            self.edit_window = tk.Toplevel(self.root)
            self.edit_window.title(f"Editar {filename}")
            self.edit_window.geometry("800x600")
            
            self.edit_text = tk.Text(self.edit_window, wrap=tk.NONE)
            self.edit_text.pack(expand=True, fill=tk.BOTH)
            
            # Botones
            button_frame = ttk.Frame(self.edit_window)
            button_frame.pack(fill=tk.X, pady=5)
            
            ttk.Button(button_frame, text="Guardar", 
                    command=lambda: self.save_esp_file(filename, self.edit_text.get("1.0", tk.END))).pack(side=tk.RIGHT, padx=5)
            
            # Solicitar contenido
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(request),
                qos=1
            )
            
        except Exception as e:
            self._log(f"Error editando archivo: {e}")

    def save_esp_file(self, filename, content):
        """Guarda cambios en archivo del ESP32"""
        try:
            esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
            
            update_request = {
                "type": "update_file",
                "filename": filename,
                "content": content,
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(update_request),
                qos=1
            )
            self._log(f"Guardando cambios en {filename}...")
            
        except Exception as e:
            self._log(f"Error guardando cambios: {e}")

    def delete_esp_file(self):
        """Elimina archivo del ESP32"""
        try:
            selection = self.esp_files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un archivo del ESP32")
                return
                
            filename = self.esp_files_listbox.get(selection[0]).split(" (")[0]
            
            if messagebox.askyesno("Confirmar", f"¿Desea eliminar {filename}?"):
                esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
                
                delete_request = {
                    "type": "delete_file",
                    "filename": filename,
                    "message_id": str(int(time.time() * 1000))
                }
                
                self.mqtt_client.publish(
                    f"esp32/ota/{esp32_id}/command",
                    json.dumps(delete_request),
                    qos=1
                )
                self._log(f"Eliminando {filename}...")
                
        except Exception as e:
            self._log(f"Error eliminando archivo: {e}")

    def execute_script(self):
        """Ejecuta script seleccionado"""
        try:
            selection = self.esp_files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un script para ejecutar")
                return
                
            filename = self.esp_files_listbox.get(selection[0]).split(" (")[0]
            if not filename.endswith('.py'):
                messagebox.showwarning("Advertencia", "Solo se pueden ejecutar archivos Python (.py)")
                return
                
            esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
            
            control_request = {
                "type": "control_script",
                "script": filename,
                "action": "start",
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(control_request),
                qos=1
            )
            self._log(f"Ejecutando {filename}...")
            
        except Exception as e:
            self._log(f"Error ejecutando script: {e}")

    def stop_script(self):
        """Detiene script en ejecución"""
        try:
            selection = self.esp_files_listbox.curselection()
            if not selection:
                messagebox.showwarning("Advertencia", "Seleccione un script para detener")
                return
                
            filename = self.esp_files_listbox.get(selection[0]).split(" (")[0]
            if not filename.endswith('.py'):
                messagebox.showwarning("Advertencia", "Solo se pueden detener archivos Python (.py)")
                return
                
            esp32_id = self.devices_listbox.get(self.devices_listbox.curselection()[0])
            
            control_request = {
                "type": "control_script",
                "script": filename,
                "action": "stop",
                "message_id": str(int(time.time() * 1000))
            }
            
            self.mqtt_client.publish(
                f"esp32/ota/{esp32_id}/command",
                json.dumps(control_request),
                qos=1
            )
            self._log(f"Deteniendo {filename}...")
            
        except Exception as e:
            self._log(f"Error deteniendo script: {e}")
            
    def _log(self, message):
        """Agrega mensaje al área de logs"""
        try:
            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
            self.log_text.see(tk.END)
        except Exception as e:
            print(f"Error en log: {e}")
            
    def run(self):
        """Inicia la aplicación"""
        self.root.mainloop()
        
    def cleanup(self):
        """Limpia recursos antes de cerrar"""
        if self.mqtt_client:
            self.mqtt_client.loop_stop()
            self.mqtt_client.disconnect()
            
if __name__ == "__main__":
    root = tk.Tk()
    app = OTAUpdaterGUI(root)
    
    def on_closing():
        app.cleanup()
        root.destroy()
        
    root.protocol("WM_DELETE_WINDOW", on_closing)
    app.run()