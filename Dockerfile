# Imagen base de Python
FROM python:3.12

# Instalar las herramientas necesarias
RUN pip install platformio
RUN pip install paho-mqtt
RUN pip install adafruit-ampy

# Configurar el entorno de trabajo
WORKDIR /clean-hdd-monitor

# Copiar el código fuente al contenedor
COPY . /clean-hdd-monitor-container

# Iniciar el contenedor sin ejecutar nada
CMD ["sleep", "infinity"]