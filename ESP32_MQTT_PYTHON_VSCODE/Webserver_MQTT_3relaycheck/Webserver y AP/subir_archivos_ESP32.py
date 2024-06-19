import tkinter as tk
from tkinter import filedialog, messagebox
import os
import subprocess

# Configuración del puerto
port = "COM3"

# Función para ejecutar un comando y devolver el resultado
def run_command(command):
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode == 0:
        return result.stdout
    else:
        messagebox.showerror("Error", f"Error ejecutando el comando: {command}\n{result.stderr}")
        return None

# Función para listar archivos en el ESP32
def list_files():
    output = run_command(f"ampy --port {port} ls")
    if output is not None:
        files_list.delete(0, tk.END)
        for line in output.splitlines():
            files_list.insert(tk.END, line)

# Función para subir archivos al ESP32
def upload_files():
    file_paths = filedialog.askopenfilenames()
    for file_path in file_paths:
        file_name = os.path.basename(file_path)
        command = f"ampy --port {port} put \"{file_path}\" {file_name}"
        run_command(command)
    list_files()

# Función para eliminar archivos del ESP32
def delete_file():
    selected_files = files_list.curselection()
    for index in selected_files:
        file_name = files_list.get(index)
        command = f"ampy --port {port} rm {file_name}"
        run_command(command)
    list_files()

# Función para descargar archivos del ESP32
def download_file():
    selected_files = files_list.curselection()
    for index in selected_files:
        file_name = files_list.get(index)
        save_path = filedialog.asksaveasfilename(initialfile=file_name)
        if save_path:
            command = f"ampy --port {port} get {file_name} \"{save_path}\""
            run_command(command)

# Crear la ventana principal
root = tk.Tk()
root.title("ESP32 File Manager")

# Crear el marco para la lista de archivos
frame = tk.Frame(root)
frame.pack(pady=10)

# Crear la lista de archivos
files_list = tk.Listbox(frame, selectmode=tk.MULTIPLE, width=50)
files_list.pack(side=tk.LEFT, padx=10)

# Crear la barra de desplazamiento
scrollbar = tk.Scrollbar(frame, orient=tk.VERTICAL, command=files_list.yview)
scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
files_list.config(yscrollcommand=scrollbar.set)

# Botones de acciones
upload_button = tk.Button(root, text="Upload Files", command=upload_files)
upload_button.pack(pady=5)

delete_button = tk.Button(root, text="Delete File", command=delete_file)
delete_button.pack(pady=5)

download_button = tk.Button(root, text="Download File", command=download_file)
download_button.pack(pady=5)

# Inicializar la lista de archivos
list_files()

# Ejecutar la aplicación
root.mainloop()
