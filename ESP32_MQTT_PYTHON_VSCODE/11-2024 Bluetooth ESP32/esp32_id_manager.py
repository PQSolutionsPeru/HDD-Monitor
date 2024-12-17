import machine
import ubinascii
import os
import json
import gc
import utime
import random

class ESP32IdManager:
    def __init__(self, mqtt_manager=None, time_manager=None):
        """Inicializa el gestor de ID único y persistente"""
        print("[ESP32_ID] Iniciando gestor...")
        
        # Archivos de configuración
        self.ID_FILE = "esp32_id.json"
        self.BACKUP_FILE = "esp32_id.bak"
        
        # Gestores externos
        self.mqtt_manager = mqtt_manager
        self.time_manager = time_manager
        
        # Información básica
        self.esp32_id = None
        self.mac_address = None
        self._get_mac_address()  # Obtiene y guarda MAC address
        
        # Control de errores
        self.error_count = 0
        self.MAX_ERRORS = 3
        self.last_error = None
        
        # Historial
        self.id_history = []
        self.MAX_HISTORY = 10
        
        print("[ESP32_ID] Esperando sincronización horaria...")
        # Intentar sincronizar hora primero
        retry_count = 0
        while retry_count < 3:
            if self.time_manager and self.time_manager.sync_time():
                print("[ESP32_ID] Hora sincronizada correctamente")
                break
            print(f"[ESP32_ID] Intento {retry_count + 1} de sincronización fallido")
            retry_count += 1
            utime.sleep_ms(1000)
        
        print("[ESP32_ID] Cargando o generando ID...")
        # Intentar cargar ID existente
        if not self._load_or_generate_id():
            print("[ESP32_ID] Generando nuevo ID...")
            self._generate_unique_id()
            self._save_with_backup()
        
        print(f"[ESP32_ID] ID final: {self.esp32_id}")

    def _generate_unique_id(self):
        """Genera ID único de 8 caracteres"""
        try:
            print("[ESP32_ID] Generando ID único...")
            
            # Validar que haya sincronización horaria
            if self.time_manager and not self.time_manager.is_peru_time_synced():
                print("[ESP32_ID] Error: Hora no sincronizada")
                raise Exception("Time not synced")
                
            # Usar MAC address como base
            mac = self.mac_address or ubinascii.hexlify(machine.unique_id()).decode()
            
            # Obtener timestamp GMT-5
            current_time = self.time_manager.get_timestamp() if self.time_manager else utime.time()
            if not current_time:
                raise Exception("Invalid timestamp")
            
            # Usar los últimos 4 caracteres del MAC
            mac_part = mac[-4:].upper()
            
            # Convertir timestamp a hex y tomar últimos 4 caracteres
            time_hex = hex(int(current_time) % 0x10000)[2:].upper()
            # Asegurar que tenga 4 caracteres añadiendo ceros al inicio si es necesario
            time_part = '0' * (4 - len(time_hex)) + time_hex
            
            # Combinar para crear ID de 8 caracteres
            unique_id = f"{mac_part}{time_part}"
            
            print(f"[ESP32_ID] ID generado: {unique_id}")
            self.esp32_id = unique_id
            self._add_to_history(unique_id, "generated")
            
            return unique_id
            
        except Exception as e:
            print(f"[ESP32_ID] Error generando ID: {e}")
            self._handle_error("generation_error", str(e))
            return None

    def _load_id_from_db(self):
        """Intenta recuperar ID desde la BD usando MAC"""
        try:
            if not self.mqtt_manager:
                return False

            print(f"[ESP32_ID] Intentando recuperar ID para MAC: {self.mac_address}")
            
            # Variable para control de respuesta
            self.id_found = None
            
            # Callback para procesar respuesta
            def handle_mac_response(topic, msg):
                try:
                    response = json.loads(msg)
                    if response.get('MAC') == self.mac_address:
                        # El mensaje incluirá el esp32_id que es el nombre del documento
                        self.id_found = response.get('esp32_id')
                        if self.id_found:
                            print(f"[ESP32_ID] ID recuperado: {self.id_found}")
                except Exception as e:
                    print(f"[ESP32_ID] Error procesando respuesta MAC: {e}")

            # Suscribirse al tópico de respuesta
            self.mqtt_manager.subscribe(self.MAC_RESPONSE_TOPIC, handle_mac_response)
            
            # Publicar solicitud de búsqueda
            search_request = {
                'MAC': self.mac_address,
                'response_topic': self.MAC_RESPONSE_TOPIC,
                'timestamp': utime.ticks_ms()
            }
            
            self.mqtt_manager.publish_event(
                self.MAC_RECOVERY_TOPIC,
                search_request,
                retain=False,
                qos=1
            )
            
            # Esperar respuesta con timeout
            start_time = utime.ticks_ms()
            while not self.id_found:
                if utime.ticks_diff(utime.ticks_ms(), start_time) > self.MAC_RECOVERY_TIMEOUT:
                    print("[ESP32_ID] Timeout esperando respuesta de MAC")
                    break
                utime.sleep_ms(100)
                
            # Limpiar suscripción
            self.mqtt_manager.unsubscribe(self.MAC_RESPONSE_TOPIC)
            
            if self.id_found:
                self.esp32_id = self.id_found
                self._save_with_backup()
                self._add_to_history(self.id_found, "recovered_from_db")
                return True
                
            return False
            
        except Exception as e:
            print(f"[ESP32_ID] Error en recuperación por MAC: {e}")
            self._handle_error("mac_recovery_error", str(e))
            return False

    def _wait_time_sync(self):
        """Espera sincronización horaria GMT -5"""
        print("[ESP32_ID] Esperando sincronización horaria...")
        if not self.time_manager:
            print("[ESP32_ID] No hay gestor de tiempo")
            return False
                
        retry_count = 0
        while retry_count < 3:
            if retry_count > 0:
                print(f"[ESP32_ID] Reintento {retry_count + 1} de sincronización")
                utime.sleep_ms(1000)
                
            if self.time_manager.sync_time():
                if self.time_manager.is_synced:
                    print("[ESP32_ID] Hora sincronizada correctamente")
                    return True
                    
            retry_count += 1
            
        print("[ESP32_ID] No se pudo sincronizar la hora")
        return False

    def _get_mac_address(self):
        """Obtiene MAC address del ESP32"""
        try:
            self.mac_address = ubinascii.hexlify(machine.unique_id()).decode()
            print(f"[ESP32_ID] MAC address: {self.mac_address}")
        except Exception as e:
            print(f"[ESP32_ID] Error obteniendo MAC: {e}")
            self.mac_address = None

    def _save_id(self, filename):
        """Guarda ID de manera segura"""
        try:
            gc.collect()
            
            # Crear archivo temporal
            temp_file = filename + '.tmp'
            save_data = {
                'esp32_id': self.esp32_id,
                'mac': self.mac_address,
                'timestamp': utime.ticks_ms(),
                'history': self.id_history
            }
            
            with open(temp_file, 'w') as f:
                json.dump(save_data, f)
            
            # Verificar archivo temporal
            with open(temp_file, 'r') as f:
                data = json.load(f)
                if not data.get('esp32_id'):
                    raise ValueError("ID no válido")
            
            # Reemplazar archivo original
            os.rename(temp_file, filename)
            return True
            
        except Exception as e:
            print(f"[ESP32_ID] Error guardando en {filename}: {e}")
            self._handle_error("save_error", str(e))
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def _save_with_backup(self):
        """Guarda ID con sistema de respaldo"""
        try:
            print("[ESP32_ID] Guardando con backup...")
            
            # Guardar backup primero
            if not self._save_id(self.BACKUP_FILE):
                return False
                
            # Luego guardar archivo principal
            if not self._save_id(self.ID_FILE):
                return False
                
            return True
                
        except Exception as e:
            print(f"[ESP32_ID] Error en save_with_backup: {e}")
            self._handle_error("backup_error", str(e))
            return False

    def _load_or_generate_id(self):
        """Carga ID existente o genera uno nuevo"""
        try:
            # Intentar cargar archivo principal
            if self.ID_FILE in os.listdir():
                with open(self.ID_FILE, 'r') as f:
                    data = json.load(f)
                    if data.get('esp32_id'):
                        self.esp32_id = data['esp32_id']
                        self.id_history = data.get('history', [])
                        self._add_to_history(self.esp32_id, "loaded")
                        return

            # Intentar cargar backup
            if self.BACKUP_FILE in os.listdir():
                print("[ESP32_ID] Usando archivo de respaldo")
                with open(self.BACKUP_FILE, 'r') as f:
                    data = json.load(f)
                    if data.get('esp32_id'):
                        self.esp32_id = data['esp32_id']
                        self.id_history = data.get('history', [])
                        self._add_to_history(self.esp32_id, "loaded_backup")
                        self._save_id(self.ID_FILE)  # Restaurar principal
                        return

            # Generar nuevo ID si no existe
            self.esp32_id = self._generate_unique_id()
            self._save_with_backup()
            
        except Exception as e:
            print(f"[ESP32_ID] Error cargando/generando ID: {e}")
            self._handle_error("load_error", str(e))
            self.esp32_id = self._generate_unique_id()
            self._save_with_backup()

    def _handle_error(self, error_type, error_message):
        """Maneja errores del gestor"""
        try:
            self.error_count += 1
            self.last_error = {
                'type': error_type,
                'message': error_message,
                'timestamp': utime.ticks_ms()
            }
            
            # Si hay demasiados errores, intentar recuperación
            if self.error_count >= self.MAX_ERRORS:
                print("[ESP32_ID] Demasiados errores, intentando recuperación")
                self._recover_from_errors()
                
        except Exception as e:
            print(f"[ESP32_ID] Error en handle_error: {e}")

    def _recover_from_errors(self):
        """Intenta recuperarse de errores críticos"""
        try:
            # Resetear contadores
            self.error_count = 0
            
            # Intentar cargar desde backup
            if self.BACKUP_FILE in os.listdir():
                with open(self.BACKUP_FILE, 'r') as f:
                    data = json.load(f)
                    if data.get('esp32_id'):
                        self.esp32_id = data['esp32_id']
                        self._save_id(self.ID_FILE)
                        return
                        
            # Si no hay backup, generar nuevo ID
            self.esp32_id = self._generate_unique_id()
            self._save_with_backup()
            
        except Exception as e:
            print(f"[ESP32_ID] Error en recuperación: {e}")

    def _add_to_history(self, esp32_id, event_type):
        """Añade evento al historial"""
        try:
            self.id_history.append({
                'id': esp32_id,
                'event': event_type,
                'timestamp': utime.ticks_ms()
            })
            
            if len(self.id_history) > self.MAX_HISTORY:
                self.id_history = self.id_history[-self.MAX_HISTORY:]
                
        except Exception as e:
            print(f"[ESP32_ID] Error añadiendo a historial: {e}")

    def get_id(self):
        """Obtiene ID actual"""
        return self.esp32_id

    def get_mac(self):
        """Obtiene MAC address"""
        return self.mac_address

    def get_status(self):
        """Obtiene estado del gestor"""
        return {
            'esp32_id': self.esp32_id,
            'mac_address': self.mac_address,
            'error_count': self.error_count,
            'last_error': self.last_error,
            'history': self.id_history
        }

    def validate_id(self):
        """Valida que el ID sea correcto"""
        try:
            if not self.esp32_id:
                return False
                
            if len(self.esp32_id) != 8:  # Cambiar a 8 caracteres
                return False
                
            if not self.esp32_id.isalnum():
                return False
                
            if not self.esp32_id.isupper():
                return False
                
            return True
                
        except Exception as e:
            print(f"[ESP32_ID] Error validando ID: {e}")
            return False