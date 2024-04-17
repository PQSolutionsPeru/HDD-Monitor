# Imagen base de Windows 10 Pro para Intel
FROM mcr.microsoft.com/windows:20H2-amd64

# Actualizar el sistema
RUN powershell -Command "Install-PackageProvider -Name NuGet -MinimumVersion 2.8.5.201 -Force"

# Instalar Python 3.12
RUN powershell -Command "Invoke-WebRequest -Uri 'https://www.python.org/ftp/python/3.12.0/python-3.12.0-amd64.exe' -OutFile 'python.exe' -UseBasicParsing; Start-Process -Wait -NoNewWindow .\python.exe /quiet InstallAllUsers=1 PrependPath=1 Include_test=0"

# Instalar las herramientas necesarias
RUN powershell -Command "Invoke-WebRequest 'https://code.visualstudio.com/sha/download?build=stable&os=win32-x64-user' -OutFile 'vscode.exe' -UseBasicParsing; Start-Process -Wait -NoNewWindow .\vscode.exe /VERYSILENT /MERGETASKS='!runcode,!desktopicon,!quicklaunchicon,!startmenuicon'"

# Instalar PlatformIO
RUN powershell -Command "Invoke-WebRequest 'https://github.com/platformio/platformio-core-installer/releases/latest/download/PlatformIO-CLI.exe' -OutFile 'platformio.exe' -UseBasicParsing"

# Instalar el cliente de MQTT (myqtthub)
RUN pip install paho-mqtt

# Instalar las herramientas ampy
RUN pip install adafruit-ampy

# Configurar el entorno de trabajo
WORKDIR /clean-hdd-monitor

# Copiar el código fuente al contenedor
COPY . /clean-hdd-monitor-container

# Iniciar el contenedor sin ejecutar nada
CMD ["timeout", "/t", "9999", "/nobreak"]
