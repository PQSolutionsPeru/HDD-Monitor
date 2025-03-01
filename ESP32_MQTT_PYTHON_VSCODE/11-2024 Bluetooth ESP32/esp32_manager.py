import subprocess
import time
import sys
import os
import logging
import argparse
import serial
import re
from typing import List, Dict, Tuple, Optional

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

class ESP32Manager:
    def __init__(self, port: str):
        self.port = port
        self.serial_timeout = 5
        self.command_timeout = 30
        self.max_retries = 3
        self.delay_between_retries = 5
        self.repl_enter_cmd = b'\r\x03\x03'  # ENTER + CTRL+C dos veces para interrumpir cualquier programa
        self.repl_exit_cmd = b'\x04'  # CTRL+D para soft reboot
        self.last_stopped_script = None  # Para guardar referencia al último script detenido

    def _open_serial(self) -> Optional[serial.Serial]:
        """Abre una conexión serie con el ESP32"""
        try:
            ser = serial.Serial(
                port=self.port,
                baudrate=115200,
                timeout=self.serial_timeout
            )
            return ser
        except serial.SerialException as e:
            logging.error(f"Error al abrir puerto serie {self.port}: {e}")
            return None

    def stop_running_scripts(self) -> bool:
        """Detiene todos los scripts en ejecución en el ESP32"""
        logging.info("Intentando detener scripts en ejecución...")
        
        try:
            ser = self._open_serial()
            if not ser:
                logging.error("No se pudo abrir la conexión serial")
                return False

            # Limpiar cualquier buffer pendiente
            ser.reset_input_buffer()
            ser.reset_output_buffer()
            
            # Intentar varias veces con diferentes enfoques
            for attempt in range(3):
                # Enviar CTRL+C varias veces con diferentes patrones
                if attempt == 0:
                    # Primer intento: simple CTRL+C repetido
                    ser.write(b'\x03\x03\x03\r\n')
                elif attempt == 1:
                    # Segundo intento: ENTER seguido de CTRL+C
                    ser.write(b'\r\n\x03\x03\r\n')
                else:
                    # Tercer intento: varios ENTER y CTRL+C con tiempos
                    ser.write(b'\r\n')
                    time.sleep(0.2)
                    ser.write(b'\x03')
                    time.sleep(0.2)
                    ser.write(b'\x03')
                    time.sleep(0.2)
                    ser.write(b'\r\n')
                
                # Dar tiempo para procesar los comandos
                time.sleep(1)
                
                # Leer la respuesta para verificar que entramos al REPL
                response = ser.read_all().decode('utf-8', errors='ignore')
                logging.info(f"Intento {attempt+1}, respuesta: {response[:100]}...")
                
                # Verificar si entramos al REPL
                if ">>>" in response or "MicroPython" in response:
                    logging.info(f"Scripts detenidos exitosamente (intento {attempt+1})")
                    ser.close()
                    return True
            
            # Si llegamos aquí, intentar un último enfoque usando ampy reset
            logging.info("Intentando detener scripts mediante reset...")
            ser.close()  # Cerrar el puerto antes
            
            try:
                # Usar ampy para reiniciar y luego conectar de nuevo para enviar CTRL+C
                subprocess.run(
                    ["ampy", "-p", self.port, "reset"],
                    capture_output=True,
                    timeout=5
                )
                time.sleep(2)  # Esperar a que reinicie
                
                # Volver a conectar
                ser = self._open_serial()
                if not ser:
                    return False
                    
                # Enviar CTRL+C justo después del reinicio
                time.sleep(0.5)
                ser.write(b'\x03\x03\r\n')
                time.sleep(1)
                
                response = ser.read_all().decode('utf-8', errors='ignore')
                if ">>>" in response or "MicroPython" in response:
                    logging.info("Scripts detenidos exitosamente mediante reset")
                    ser.close()
                    return True
                else:
                    logging.error("No se pudo detener los scripts")
                    ser.close()
                    return False
                    
            except Exception as e:
                logging.error(f"Error en el intento con reset: {e}")
                return False
                
        except Exception as e:
            logging.error(f"Error al detener scripts: {e}")
            return False

    def restart_device(self, soft_reset: bool = True) -> bool:
        """Reinicia el ESP32, por defecto usando soft reset"""
        if soft_reset:
            logging.info("Realizando soft reset del ESP32...")
            try:
                ser = self._open_serial()
                if not ser:
                    logging.warning("No se pudo abrir el puerto serial para soft reset, intentando hard reset")
                    return self.restart_device(soft_reset=False)
                
                # Enviar CTRL+D para soft reboot
                ser.write(self.repl_exit_cmd)
                time.sleep(2)
                
                # Verificar si el reinicio fue exitoso
                response = ser.read_all().decode('utf-8', errors='ignore')
                if "boot.py" in response or "main.py" in response:
                    logging.info("ESP32 reiniciado correctamente (soft reset)")
                else:
                    logging.info("No se detectó mensaje de reinicio, pero se envió la señal")
                
                ser.close()
                return True
            except Exception as e:
                logging.error(f"Error al realizar soft reset: {e}")
                logging.info("Intentando hard reset como alternativa...")
                return self.restart_device(soft_reset=False)
        else:
            # Hard reset usando ampy
            logging.info("Realizando hard reset del ESP32...")
            try:
                # Intentar múltiples veces
                for attempt in range(2):
                    try:
                        result = subprocess.run(
                            ["ampy", "-p", self.port, "reset"],
                            capture_output=True,
                            text=True,
                            timeout=5
                        )
                        if result.returncode == 0:
                            logging.info("ESP32 reiniciado (hard reset)")
                            return True
                        else:
                            logging.warning(f"Intento {attempt+1}: Error al reiniciar ESP32: {result.stderr}")
                    except subprocess.TimeoutExpired:
                        logging.warning(f"Intento {attempt+1}: Timeout durante reset")
                    
                    # Si fallamos, intentar con un pequeño retraso
                    time.sleep(2)
                
                # Último recurso: cerrar y volver a abrir el puerto
                logging.info("Intentando reset mediante cierre y reapertura del puerto...")
                try:
                    ser = self._open_serial()
                    if ser:
                        ser.close()
                    time.sleep(1)
                    ser = self._open_serial()
                    if ser:
                        ser.close()
                        logging.info("Puerto reabierto y cerrado para forzar reset")
                        return True
                except:
                    pass
                
                logging.error("No se pudo reiniciar el ESP32 después de múltiples intentos")
                return False
            except Exception as e:
                logging.error(f"Error al realizar hard reset: {e}")
                return False

    def execute_ampy_command(self, command: List[str], retry_count: int = 0) -> Tuple[bool, str]:
        """Ejecuta un comando ampy con reintentos"""
        try:
            cmd = ["ampy", "-p", self.port] + command
            logging.info(f"Ejecutando: {' '.join(cmd)}")
            
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=self.command_timeout
            )
            
            if result.returncode == 0:
                logging.info("Comando ejecutado exitosamente")
                return True, result.stdout
            else:
                logging.error(f"Error: {result.stderr}")
                if retry_count < self.max_retries:
                    logging.info(f"Reintentando en {self.delay_between_retries} segundos...")
                    time.sleep(self.delay_between_retries)
                    return self.execute_ampy_command(command, retry_count + 1)
                return False, result.stderr
                
        except subprocess.TimeoutExpired:
            logging.error("Timeout ejecutando comando")
            if retry_count < self.max_retries:
                logging.info(f"Reintentando en {self.delay_between_retries} segundos...")
                time.sleep(self.delay_between_retries)
                return self.execute_ampy_command(command, retry_count + 1)
            return False, "Timeout ejecutando comando"
            
        except Exception as e:
            logging.error(f"Error inesperado: {e}")
            return False, str(e)

    def list_files(self, directory: str = "/") -> List[str]:
        """Lista archivos en el ESP32"""
        logging.info(f"Listando archivos en directorio: {directory}")
        
        success, output = self.execute_ampy_command(["ls", directory])
        if success:
            # Convertir la salida en una lista de archivos
            files = [f.strip() for f in output.strip().split("\n") if f.strip()]
            logging.info(f"Archivos encontrados: {len(files)}")
            return files
        else:
            logging.error("No se pudieron listar los archivos")
            return []

    def get_file(self, remote_path: str, local_path: str = None) -> bool:
        """Descarga un archivo desde el ESP32"""
        if not local_path:
            local_path = os.path.basename(remote_path)
            
        logging.info(f"Descargando archivo {remote_path} a {local_path}")
        
        success, _ = self.execute_ampy_command(["get", remote_path, local_path])
        if success:
            logging.info(f"Archivo descargado exitosamente a {local_path}")
            return True
        else:
            logging.error(f"Error al descargar archivo {remote_path}")
            return False

    def put_file(self, local_path: str, remote_path: str = None) -> bool:
        """Sube un archivo al ESP32"""
        if not remote_path:
            remote_path = os.path.basename(local_path)
            
        logging.info(f"Subiendo archivo {local_path} a {remote_path}")
        
        if not os.path.exists(local_path):
            logging.error(f"El archivo local {local_path} no existe")
            return False
            
        success, _ = self.execute_ampy_command(["put", local_path, remote_path])
        if success:
            logging.info(f"Archivo subido exitosamente a {remote_path}")
            return True
        else:
            logging.error(f"Error al subir archivo {local_path}")
            return False

    def create_directory(self, directory: str) -> bool:
        """Crea un directorio en el ESP32"""
        logging.info(f"Creando directorio: {directory}")
        
        success, _ = self.execute_ampy_command(["mkdir", directory])
        if success:
            logging.info(f"Directorio {directory} creado exitosamente")
            return True
        else:
            logging.error(f"Error al crear directorio {directory}")
            return False

    def delete_file(self, remote_path: str) -> bool:
        """Elimina un archivo o directorio del ESP32"""
        logging.info(f"Eliminando: {remote_path}")
        
        success, _ = self.execute_ampy_command(["rm", remote_path])
        if success:
            logging.info(f"{remote_path} eliminado exitosamente")
            return True
        else:
            logging.error(f"Error al eliminar {remote_path}")
            return False

    def run_file(self, remote_path: str) -> Tuple[bool, str]:
        """Ejecuta un archivo Python en el ESP32 sin reiniciar"""
        logging.info(f"Ejecutando archivo: {remote_path}")
        
        return self.execute_ampy_command(["run", remote_path])

    def read_file_content(self, remote_path: str) -> Optional[str]:
        """Lee el contenido de un archivo en el ESP32"""
        logging.info(f"Leyendo contenido de: {remote_path}")
        
        success, output = self.execute_ampy_command(["get", remote_path])
        if success:
            logging.info(f"Contenido leído exitosamente ({len(output)} bytes)")
            return output
        else:
            logging.error(f"Error al leer contenido de {remote_path}")
            return None
            
    def edit_and_resume(self, file_to_edit: str, main_script: str = "main.py") -> bool:
        """Detiene el script principal, edita un archivo y reanuda el script principal"""
        logging.info(f"Iniciando secuencia de edición para {file_to_edit}...")
        
        # 1. Guardar referencia del script principal
        self.last_stopped_script = main_script
        
        # 2. Detener scripts en ejecución
        if not self.stop_running_scripts():
            logging.warning("No se pudo detener los scripts usando el método normal")
            logging.info("Intentando descargar el archivo de todos modos...")
            
        # 3. Descargar el archivo a editar
        temp_file = f"temp_{os.path.basename(file_to_edit)}"
        success, _ = self.execute_ampy_command(["get", file_to_edit, temp_file])
        if not success:
            logging.error(f"No se pudo descargar {file_to_edit}")
            logging.info("Verificando si el archivo existe en el ESP32...")
            
            # Listar archivos para ver si el archivo existe
            files = self.list_files()
            if file_to_edit not in files:
                response = input(f"El archivo {file_to_edit} no existe. ¿Desea crearlo? (s/n): ")
                if response.lower() != 's':
                    return False
                # Crear archivo vacío
                with open(temp_file, 'w') as f:
                    f.write("")
                logging.info(f"Archivo temporal {temp_file} creado para edición")
            else:
                return False
            
        # 4. Abrir el archivo en el editor predeterminado
        try:
            logging.info(f"Abriendo {temp_file} en el editor predeterminado...")
            if sys.platform == "win32":
                os.system(f"notepad {temp_file}")
            elif sys.platform == "darwin":
                os.system(f"open {temp_file}")
            else:
                editor = os.environ.get("EDITOR", "nano")
                os.system(f"{editor} {temp_file}")
                
            # Preguntar si desea guardar los cambios
            response = input("\n¿Guardar los cambios y subir el archivo? (s/n): ")
            if response.lower() != 's':
                logging.info("Operación cancelada por el usuario")
                os.remove(temp_file)
                return False
                
            # 5. Subir el archivo editado
            if not self.put_file(temp_file, file_to_edit):
                logging.error(f"No se pudo subir el archivo editado a {file_to_edit}")
                os.remove(temp_file)
                return False
            
            # Eliminar archivo temporal
            os.remove(temp_file)
            
            # 6. Reiniciar el ESP32 para que cargue el script principal
            logging.info(f"Reiniciando ESP32 para cargar {main_script}...")
            return self.restart_device(soft_reset=True)
            
        except Exception as e:
            logging.error(f"Error durante la edición: {e}")
            return False
            
    def quick_edit(self, file_to_edit: str, edit_function=None) -> bool:
        """Edita rápidamente un archivo sin interacción del usuario"""
        logging.info(f"Editando rápidamente {file_to_edit}...")
        
        # Guardar estado del script principal
        if not self.stop_running_scripts():
            return False
            
        # Descargar archivo
        temp_file = f"temp_{os.path.basename(file_to_edit)}"
        if not self.get_file(file_to_edit, temp_file):
            return False
            
        try:
            # Leer el contenido del archivo
            with open(temp_file, 'r') as f:
                content = f.read()
                
            # Si se proporciona una función de edición, usarla
            if edit_function:
                new_content = edit_function(content)
            else:
                # En caso contrario, permitir edición básica
                print(f"\nContenido actual de {file_to_edit}:")
                print("="*50)
                print(content)
                print("="*50)
                new_content = input("Ingrese el nuevo contenido (o presione ENTER para mantener el actual):\n")
                if not new_content:
                    new_content = content
                    
            # Guardar el nuevo contenido
            with open(temp_file, 'w') as f:
                f.write(new_content)
                
            # Subir el archivo editado
            if not self.put_file(temp_file, file_to_edit):
                os.remove(temp_file)
                return False
                
            # Limpiar
            os.remove(temp_file)
            
            # Reiniciar dispositivo
            return self.restart_device(soft_reset=True)
            
        except Exception as e:
            logging.error(f"Error en edición rápida: {e}")
            return False
            
    def print_device_info(self) -> None:
        """Imprime información del dispositivo ESP32"""
        try:
            # Detener scripts
            self.stop_running_scripts()
            
            # Abrir conexión serial
            ser = self._open_serial()
            if not ser:
                return
                
            # Enviar comando para ver información del sistema
            ser.write(b'import os, gc, sys\r\n')
            time.sleep(0.5)
            ser.write(b'print("Python:", sys.implementation)\r\n')
            time.sleep(0.5)
            ser.write(b'print("Plataforma:", sys.platform)\r\n')
            time.sleep(0.5)
            ser.write(b'print("Memoria libre:", gc.mem_free())\r\n')
            time.sleep(0.5)
            ser.write(b'print("Archivos en root:", os.listdir())\r\n')
            time.sleep(1)
            
            # Leer respuesta
            output = ser.read_all().decode('utf-8', errors='ignore')
            ser.close()
            
            print("\n--- INFORMACIÓN DEL DISPOSITIVO ---")
            print(output)
            print("----------------------------------\n")
            
        except Exception as e:
            logging.error(f"Error al obtener información del dispositivo: {e}")
            
def main():
    parser = argparse.ArgumentParser(description="ESP32 Manager - Herramienta para gestionar ESP32 con MicroPython")
    parser.add_argument("--port", "-p", type=str, default="COM3", help="Puerto serie (default: COM3)")
    parser.add_argument("--debug", "-d", action="store_true", help="Activar modo de depuración")
    
    # Subparsers para diferentes comandos
    subparsers = parser.add_subparsers(dest="command", help="Comando a ejecutar")
    
    # Comando edit - específico para detener, editar y reanudar
    edit_parser = subparsers.add_parser("edit", help="Detener, editar archivo y reanudar")
    edit_parser.add_argument("file_to_edit", help="Archivo a editar")
    edit_parser.add_argument("--main", "-m", default="main.py", help="Script principal a reanudar (default: main.py)")
    edit_parser.add_argument("--force", "-f", action="store_true", help="Continuar incluso si no se puede detener el script")
    
    # Comando stop
    stop_parser = subparsers.add_parser("stop", help="Detener scripts en ejecución")
    
    # Comando restart
    restart_parser = subparsers.add_parser("restart", help="Reiniciar el ESP32")
    restart_parser.add_argument("--hard", action="store_true", help="Realizar hard reset en lugar de soft reset")
    
    # Comando list
    list_parser = subparsers.add_parser("ls", help="Listar archivos")
    list_parser.add_argument("directory", nargs="?", default="/", help="Directorio a listar (default: /)")
    
    # Comando get
    get_parser = subparsers.add_parser("get", help="Descargar archivo")
    get_parser.add_argument("remote_path", help="Ruta del archivo en el ESP32")
    get_parser.add_argument("local_path", nargs="?", default=None, help="Ruta local donde guardar (default: mismo nombre)")
    
    # Comando put
    put_parser = subparsers.add_parser("put", help="Subir archivo")
    put_parser.add_argument("local_path", help="Ruta del archivo local")
    put_parser.add_argument("remote_path", nargs="?", default=None, help="Ruta en el ESP32 (default: mismo nombre)")
    
    # Comando mkdir
    mkdir_parser = subparsers.add_parser("mkdir", help="Crear directorio")
    mkdir_parser.add_argument("directory", help="Nombre del directorio a crear")
    
    # Comando rm
    rm_parser = subparsers.add_parser("rm", help="Eliminar archivo o directorio")
    rm_parser.add_argument("remote_path", help="Ruta a eliminar")
    
    # Comando run
    run_parser = subparsers.add_parser("run", help="Ejecutar archivo sin reiniciar")
    run_parser.add_argument("remote_path", help="Ruta del archivo a ejecutar")
    
    # Comando info
    info_parser = subparsers.add_parser("info", help="Mostrar información del dispositivo")
    
    # Comando cat
    cat_parser = subparsers.add_parser("cat", help="Mostrar contenido de un archivo")
    cat_parser.add_argument("remote_path", help="Ruta del archivo a mostrar")
    
    # Comando upload (similar al ejemplo de carga por grupos)
    upload_parser = subparsers.add_parser("upload", help="Subir múltiples archivos por grupos desde un directorio")
    upload_parser.add_argument("directory", help="Directorio local con los archivos a subir")
    upload_parser.add_argument("--clean", action="store_true", help="Limpiar archivos existentes antes de subir")
    
    args = parser.parse_args()
    
    # Si no se especifica un comando, mostrar ayuda
    if args.command is None:
        parser.print_help()
        return
    
    # Configurar nivel de logging si se activa el debug
    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)
        logging.debug("Modo debug activado")
    
    manager = ESP32Manager(args.port)
    
    try:
        if args.command == "stop":
            success = manager.stop_running_scripts()
            if not success:
                sys.exit(1)
                
        elif args.command == "restart":
            success = manager.restart_device(not args.hard)
            if not success:
                sys.exit(1)
                
        elif args.command == "ls":
            files = manager.list_files(args.directory)
            if files:
                print(f"Archivos en {args.directory}:")
                for file in files:
                    print(f"  {file}")
            else:
                print(f"No se encontraron archivos en {args.directory}")
                
        elif args.command == "get":
            success = manager.get_file(args.remote_path, args.local_path)
            if not success:
                sys.exit(1)
                
        elif args.command == "put":
            success = manager.put_file(args.local_path, args.remote_path)
            if not success:
                sys.exit(1)
                
        elif args.command == "mkdir":
            success = manager.create_directory(args.directory)
            if not success:
                sys.exit(1)
                
        elif args.command == "rm":
            success = manager.delete_file(args.remote_path)
            if not success:
                sys.exit(1)
                
        elif args.command == "run":
            success, output = manager.run_file(args.remote_path)
            if not success:
                sys.exit(1)
            else:
                print(f"Salida del programa:\n{output}")
                
        elif args.command == "info":
            manager.print_device_info()
            
        elif args.command == "cat":
            content = manager.read_file_content(args.remote_path)
            if content:
                print(f"Contenido de {args.remote_path}:")
                print(content)
            else:
                sys.exit(1)
                
        elif args.command == "edit":
            # Ejecutar el flujo de edición y reanudación
            success = manager.edit_and_resume(args.file_to_edit, args.main)
            if not success:
                sys.exit(1)
                
        elif args.command == "upload":
            # Implementación básica para subir múltiples archivos
            if not os.path.isdir(args.directory):
                logging.error(f"El directorio {args.directory} no existe")
                sys.exit(1)
                
            if args.clean:
                logging.info("Limpiando archivos existentes...")
                # Obtener lista de archivos y eliminarlos
                files = manager.list_files()
                for file in files:
                    if file not in ["boot.py"]:  # Preservar archivos críticos
                        manager.delete_file(file)
            
            # Subir los archivos
            for root, dirs, files in os.walk(args.directory):
                # Crear directorios primero
                for dir_name in dirs:
                    rel_path = os.path.relpath(os.path.join(root, dir_name), args.directory)
                    if rel_path != ".":
                        manager.create_directory(rel_path.replace("\\", "/"))
                
                # Luego subir archivos
                for file_name in files:
                    local_path = os.path.join(root, file_name)
                    rel_path = os.path.relpath(local_path, args.directory)
                    remote_path = rel_path.replace("\\", "/")
                    manager.put_file(local_path, remote_path)
                    time.sleep(1)  # Pequeña pausa entre archivos
            
            logging.info("Subida de archivos completada")
            
        else:
            logging.error(f"Comando desconocido: {args.command}")
            sys.exit(1)
            
    except KeyboardInterrupt:
        logging.info("\nProceso interrumpido por el usuario")
        sys.exit(1)
    except Exception as e:
        logging.error(f"Error inesperado: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()