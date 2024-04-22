# Usamos una imagen base de Ubuntu
FROM ubuntu:latest

# Instalamos dependencias necesarias para VS Code y herramientas de utilidad
RUN apt-get update && apt-get install -y \
    curl \
    gnupg \
    software-properties-common \
    apt-transport-https \
    # Necesario si planeas usar Git dentro del contenedor
    git

# Agregamos el repositorio de Microsoft para VS Code
RUN curl -sSL https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://packages.microsoft.com/repos/vscode stable main"

# Instalamos VS Code
RUN apt-get update && apt-get install -y code

# Instalamos las extensiones de VS Code
RUN code --install-extension dart-code.dart-code --user-data-dir /root/.vscode
RUN code --install-extension dart-code.flutter --user-data-dir /root/.vscode
RUN code --install-extension ms-azuretools.vscode-docker --user-data-dir /root/.vscode
RUN code --install-extension ms-python.debugpy --user-data-dir /root/.vscode
RUN code --install-extension ms-python.isort --user-data-dir /root/.vscode
RUN code --install-extension ms-python.python --user-data-dir /root/.vscode
RUN code --install-extension ms-vscode-remote.remote-containers --user-data-dir /root/.vscode
RUN code --install-extension ms-vscode.cpptools --user-data-dir /root/.vscode
RUN code --install-extension ms-vscode.cpptools-extension-pack --user-data-dir /root/.vscode
RUN code --install-extension ms-vscode.cpptools-themes --user-data-dir /root/.vscode
RUN code --install-extension ms-vscode.vscode-serial-monitor --user-data-dir /root/.vscode
RUN code --install-extension seunlanlege.action-buttons --user-data-dir /root/.vscode

# Configuramos el directorio de trabajo
WORKDIR /workspace

# Comando por defecto al iniciar el contenedor
CMD ["code", ".", "--user-data-dir", "/root/.vscode", "--no-sandbox"]
