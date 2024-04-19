@echo off

REM Crear la carpeta principal del proyecto
mkdir app-hdd-monitor
cd app-hdd-monitor

REM Crear la estructura de carpetas para "backend"
mkdir backend
cd backend
mkdir src
cd src
mkdir login dashboard notifications
echo. > login\login.py
echo. > dashboard\dashboard.py
echo. > notifications\notifications.py
echo. > main.py
cd ..\..

REM Crear la estructura de carpetas para "frontend"
mkdir frontend
cd frontend
mkdir lib assets
cd lib
echo. > main.dart
mkdir screens
cd screens
mkdir login dashboard notifications
echo. > login\login.dart
echo. > dashboard\dashboard.dart
echo. > notifications\notifications.dart
cd ..\..

REM Crear la estructura de carpetas para "tests"
mkdir tests
cd tests
mkdir backend frontend
cd backend
echo. > test_login.py
echo. > test_dashboard.py
echo. > test_notifications.py
cd ..\frontend
echo. > test_login.dart
echo. > test_dashboard.dart
echo. > test_notifications.dart
cd ..\..

REM Volver a la carpeta "app-hdd-monitor" y crear pubspec.yaml en la raíz del "frontend"
cd ..
cd frontend
echo name: app_hdd_monitor > pubspec.yaml
echo description: Aplicación de monitoreo de relés de paneles de incendios >> pubspec.yaml
echo version: 1.0.0+1 >> pubspec.yaml
echo environment: >> pubspec.yaml
echo   sdk: ">=2.12.0 <3.0.0" >> pubspec.yaml
echo dependencies: >> pubspec.yaml
echo   flutter: >> pubspec.yaml
echo     sdk: flutter >> pubspec.yaml
echo dev_dependencies: >> pubspec.yaml
echo   flutter_test: >> pubspec.yaml
echo     sdk: flutter >> pubspec.yaml

REM Finalización del script
echo Estructura de carpetas y archivos para app-hdd-monitor creada exitosamente.
pause