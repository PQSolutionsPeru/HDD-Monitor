@echo off

REM Crear carpeta principal del proyecto
mkdir hdd-monitor
cd hdd-monitor

REM Crear carpeta "backend"
mkdir backend
cd backend

REM Crear carpeta "src" dentro de "backend"
mkdir src
cd src

REM Crear carpetas de módulos o componentes del backend
mkdir login
mkdir dashboard
mkdir notifications

REM Crear archivo de configuración de Firebase
echo Configuración de Firebase > firebase_config.txt

cd ..\..\

REM Crear carpeta "frontend"
mkdir frontend
cd frontend

REM Crear carpeta "lib" dentro de "frontend"
mkdir lib
cd lib

REM Crear carpetas dentro de "lib"
mkdir screens
mkdir components
mkdir models
mkdir services

REM Crear archivo de configuración principal
echo Configuración principal > config.txt

cd ..\..\

REM Crear carpeta "docs"
mkdir docs

echo Estructura de carpetas creada exitosamente.