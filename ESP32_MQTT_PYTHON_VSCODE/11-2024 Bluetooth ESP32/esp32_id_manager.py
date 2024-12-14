import machine
import ubinascii
import os
import json
import gc

class ESP32IdManager:
    def __init__(self):
        """Inicializa el gestor de ID único y persistente"""
        self.ID_FILE = "esp32_id.json"
        self.BACKUP_FILE = "esp32_id.bak"
        self.esp32_id = None
        self._load_or_generate_id()
        print(f"[ESP32_ID] ID cargado/generado: {self.esp32_id}")

    def _generate_unique_id(self):
        """Genera ID único de 4 caracteres alfanuméricos"""
        try:
            # Usar MAC address como base
            raw_id = ubinascii.hexlify(machine.unique_id()).decode()
            # Tomar últimos 4 caracteres y convertir a mayúsculas
            unique_id = raw_id[-4:].upper()
            return unique_id
        except Exception as e:
            print(f"[ESP32_ID] Error generando ID: {e}")
            # Fallback a ID aleatorio si falla
            import random
            chars = '0123456789ABCDEF'
            return ''.join(random.choice(chars) for _ in range(4))

    def _save_id(self, filename):
        """Guarda ID de manera segura"""
        try:
            gc.collect()
            
            # Crear archivo temporal
            temp_file = filename + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump({
                    'esp32_id': self.esp32_id,
                    'timestamp': machine.RTC().datetime()
                }, f)
            
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
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def _save_with_backup(self):
        """Guarda ID con sistema de respaldo"""
        try:
            # Guardar backup primero
            if not self._save_id(self.BACKUP_FILE):
                return False
                
            # Luego guardar archivo principal
            if not self._save_id(self.ID_FILE):
                return False
                
            return True
            
        except Exception as e:
            print(f"[ESP32_ID] Error en save_with_backup: {e}")
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
                        return

            # Intentar cargar backup
            if self.BACKUP_FILE in os.listdir():
                print("[ESP32_ID] Usando archivo de respaldo")
                with open(self.BACKUP_FILE, 'r') as f:
                    data = json.load(f)
                    if data.get('esp32_id'):
                        self.esp32_id = data['esp32_id']
                        self._save_id(self.ID_FILE)  # Restaurar principal
                        return

            # Generar nuevo ID si no existe
            self.esp32_id = self._generate_unique_id()
            self._save_with_backup()
            
        except Exception as e:
            print(f"[ESP32_ID] Error cargando/generando ID: {e}")
            self.esp32_id = self._generate_unique_id()
            self._save_with_backup()

    def get_id(self):
        """Obtiene ID actual"""
        return self.esp32_id