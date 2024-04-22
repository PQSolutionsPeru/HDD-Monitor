# Usamos una imagen base de Ubuntu
FROM ubuntu:latest

# Instalamos herramientas de utilidad y dependencias comunes
RUN apt-get update && apt-get install -y \
    curl \
    gnupg \ 
    software-properties-common \
    apt-transport-https \
    git \ 
    python3 \ 
    python3-pip \ 
    # Agrega aquí cualquier otra herramienta que necesites
    && rm -rf /var/lib/apt/lists/* # Limpieza de cache de apt

# Configuramos el directorio de trabajo
WORKDIR /workspace

# Opcional: Comando por defecto al iniciar el contenedor
# Si quieres que el contenedor se mantenga en ejecución por defecto, puedes usar algo como:
CMD ["sleep", "infinity"]
# Esto mantendrá el contenedor en ejecución hasta que se cierre explícitamente.

# Otra opción es no definir CMD si vas a usar comandos específicos al iniciar el contenedor.
