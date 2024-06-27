import os

def list_files_and_folders(start_path):
    for root, dirs, files in os.walk(start_path):
        level = root.replace(start_path, '').count(os.sep)
        indent = ' ' * 4 * (level)
        print('{}{}/'.format(indent, os.path.basename(root)))
        sub_indent = ' ' * 4 * (level + 1)
        for f in files:
            print('{}{}'.format(sub_indent, f))

if __name__ == '__main__':
    # Reemplaza 'ruta_especificada' con la ruta que desees explorar
    ruta_especificada = 'E:\\PQSolutions\\HDD-Monitor\\HDD-Monitor-GitHub\\APP\\HDDMonitor\\app\\src\\main'
    list_files_and_folders(ruta_especificada)
