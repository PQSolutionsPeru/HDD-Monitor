# Usar una imagen base que coincida con el entorno de Lambda
FROM amazonlinux:2

# Instalar Python y pip
RUN yum install -y python3 python3-pip python3-devel zip
RUN python3 -m pip install --upgrade pip

# Establecer el directorio de trabajo
WORKDIR /package

# Copiar el archivo de requisitos
COPY requirements.txt .

# Instalar las dependencias en el directorio de trabajo
RUN pip3 install -r requirements.txt -t .

# Copiar el código fuente
COPY . .

# Empaquetar todo en un archivo ZIP
RUN zip -r /tmp/my_lambda_function.zip .
