import os

# Establece el directorio donde se encuentran tus archivos
directorio = 'E:\\PQSolutions\\HDD-Monitor\\HDD-Monitor-GitHub\\APP\\flut_hdd_monitor\\lib'

# Extensión de los archivos a buscar
extension_archivo = '.dart'  # Cambia esta línea para buscar diferentes tipos de archivos

# Nombre del archivo de salida donde se guardará todo el contenido
archivo_salida = 'contenido_combinado.txt'

# Crea una lista para almacenar todo el contenido
contenido_total = []

# Recorre todos los archivos en el directorio y subdirectorios
for root, dirs, files in os.walk(directorio):
    for archivo in files:
        if archivo.endswith(extension_archivo):
            ruta_completa = os.path.join(root, archivo)
            # Añade el nombre del archivo al contenido
            contenido_total.append(f"--- Contenido de {archivo} ---\n")
            # Abre el archivo y lee su contenido
            with open(ruta_completa, 'r', encoding='utf-8') as f:
                contenido_total.append(f.read() + '\n\n')

# Escribe todo el contenido recopilado en el archivo de salida
with open(os.path.join(directorio, archivo_salida), 'w', encoding='utf-8') as f:
    f.write('\n'.join(contenido_total))

print(f"Todo el contenido de los archivos {extension_archivo} ha sido combinado en {archivo_salida}")
