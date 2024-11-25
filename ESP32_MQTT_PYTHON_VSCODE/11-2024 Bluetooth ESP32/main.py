import ujson
import machine
import utime
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from bluetooth_manager import BluetoothManager
import gc
import os

# Configuración
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {32: "Alarma", 33: "Problema", 25: "Supervision"}
CONFIG_FILE = "config.json"

# Managers
wifi_manager = WiFiManager()
mqtt_manager = MQTTManager(wifi_manager)
relay_manager = RelayManager()
watchdog_manager = WatchdogManager()

cached_datetime = None
last_datetime_update = utime.ticks_ms()
memory_report_enabled = True

def load_config():
    try:
        with open(CONFIG_FILE, 'r') as file:
            config = ujson.load(file)
    except (OSError, ValueError):
        config = {}
    return config

def save_config(config):
    with open(CONFIG_FILE, 'w') as file:
        ujson.dump(config, file)

def delete_config():
    """Elimina el archivo de configuración si existe"""
    try:
        os.remove(CONFIG_FILE)
        print("Configuración eliminada")
    except:
        print("No se encontró archivo de configuración para eliminar")

def try_wifi_connection(max_attempts=5, timeout_per_attempt=20):
    """
    Intenta conectar al WiFi con múltiples reintentos
    max_attempts: número máximo de intentos
    timeout_per_attempt: tiempo máximo por intento en segundos
    """
    for attempt in range(max_attempts):
        print(f"Intento de conexión WiFi {attempt + 1}/{max_attempts}")
        try:
            start_time = utime.ticks_ms()
            wifi_manager.connect_wifi()
            
            # Esperar hasta timeout_per_attempt segundos por intento
            while not wifi_manager.sta_if.isconnected():
                if utime.ticks_diff(utime.ticks_ms(), start_time) > timeout_per_attempt * 1000:
                    print(f"Timeout después de {timeout_per_attempt} segundos")
                    break
                print("Esperando conexión...")
                utime.sleep(1)
                
            if wifi_manager.sta_if.isconnected():
                print("Conexión WiFi exitosa!")
                print(f"IP asignada: {wifi_manager.sta_if.ifconfig()[0]}")
                return True
                
            print(f"No se pudo conectar en el intento {attempt + 1}")
            
        except Exception as e:
            print(f"Error en intento de conexión: {e}")
        
        if attempt < max_attempts - 1:  # Si no es el último intento
            wait_time = (attempt + 1) * 5  # Aumenta el tiempo de espera progresivamente
            print(f"Esperando {wait_time} segundos antes del siguiente intento...")
            utime.sleep(wait_time)
    
    return False

def bluetooth_config():
    """Espera indefinidamente por una configuración Bluetooth válida"""
    print("\n[CONFIG] Iniciando modo configuración BLE")
    print("[CONFIG] Formato: ssid:nombre_red,password:contraseña,panel_name:nombre,panel_location:ubicacion")
    print("[CONFIG] Esperando conexión bluetooth...")
    
    bluetooth_manager = None
    last_status_time = utime.ticks_ms()
    status_interval = 5000  # 5 segundos entre mensajes de estado
    last_gc_time = utime.ticks_ms()
    gc_interval = 30000  # 30 segundos entre limpiezas de memoria

    try:
        bluetooth_manager = BluetoothManager()
        
        while True:  # Loop infinito esperando configuración válida
            try:
                # Alimentar watchdog
                watchdog_manager.feed()
                
                # Limpieza periódica de memoria
                current_time = utime.ticks_ms()
                if utime.ticks_diff(current_time, last_gc_time) >= gc_interval:
                    gc.collect()
                    print(f"[MEMORY] Memoria libre: {gc.mem_free()} bytes")
                    last_gc_time = current_time

                # Verificar datos bluetooth
                data = bluetooth_manager.wait_for_data()
                if data is not None and 'ssid' in data and 'password' in data:
                    print("[CONFIG] Datos recibidos, verificando...")
                    
                    # Crear configuración
                    config = {
                        'wifi_ssid': data['ssid'].strip(),
                        'wifi_password': data['password'].strip(),
                        'panel_name': data.get('panel_name', 'Panel_Default').strip(),
                        'panel_location': data.get('panel_location', 'Location_Default').strip()
                    }
                    print(f"[CONFIG] Configuración a probar: {config}")
                    
                    # Intentar conexión WiFi con la nueva configuración
                    wifi_manager.SSID = config['wifi_ssid']
                    wifi_manager.PASSWORD = config['wifi_password']
                    
                    print("[CONFIG] Probando conexión WiFi...")
                    if try_wifi_connection(max_attempts=2, timeout_per_attempt=15):  # Prueba rápida
                        print("[CONFIG] ¡Conexión WiFi exitosa!")
                        try:
                            save_config(config)
                            print("[CONFIG] Configuración guardada exitosamente")
                            bluetooth_manager.write_data("status:success")
                            print("[CONFIG] Reiniciando sistema para aplicar configuración...")
                            utime.sleep(2)
                            machine.reset()
                        except Exception as e:
                            print(f"[CONFIG] Error guardando configuración: {e}")
                            bluetooth_manager.write_data("status:error,message:save_failed")
                    else:
                        print("[CONFIG] No se pudo conectar con las credenciales proporcionadas")
                        bluetooth_manager.write_data("status:error,message:wifi_connection_failed")
                
                # Mensaje de estado periódico
                if utime.ticks_diff(current_time, last_status_time) >= status_interval:
                    print("[CONFIG] Esperando datos de configuración bluetooth...")
                    last_status_time = current_time
                
                utime.sleep_ms(100)  # Pequeña pausa para no saturar el CPU
                
            except Exception as e:
                print(f"[CONFIG] Error en bucle bluetooth: {e}")
                gc.collect()  # Limpiar memoria en caso de error
                utime.sleep(1)
                
    except Exception as e:
        print(f"[CONFIG] Error crítico en bluetooth_config: {e}")
    finally:
        if bluetooth_manager:
            try:
                bluetooth_manager.close()
            except:
                pass
        gc.collect()

def get_current_datetime():
    global cached_datetime, last_datetime_update
    current_ticks = utime.ticks_ms()
    if utime.ticks_diff(current_ticks, last_datetime_update) > 600000 or cached_datetime is None:
        update_datetime()
    return cached_datetime

def update_datetime():
    global cached_datetime, last_datetime_update
    try:
        cached_datetime = wifi_manager.get_current_time()
        print(f"Fecha y hora actuales actualizadas a: {cached_datetime}")
    except Exception as e:
        print(f"Error al obtener fecha y hora, usando última conocida o 'unknown': {e}")
        cached_datetime = cached_datetime if cached_datetime else "unknown"
    last_datetime_update = utime.ticks_ms()

def relay_callback(pin, pin_num):
    gc.collect()
    print(f"Callback de relay activado para el pin {pin_num}.")
    current_datetime = get_current_datetime()
    status = "DISC" if pin.value() else "OK"
    message = {
        "date_time": current_datetime,
        "name": RELAY_NAMES.get(pin_num, "Relay Desconocido"),
        "status": status
    }
    print(f"Enviando mensaje MQTT: {message}")
    # No esperar a que se complete la publicación
    try:
        mqtt_manager.publish_event(f"EMPRESA_TEST/{mqtt_manager.MQTT_CLIENT_ID}/eventos", ujson.dumps(message))
    except Exception as e:
        print(f"Error al publicar evento: {e}")

def main():
    """Función principal del programa"""
    print("\nIniciando sistema...")
    
    while True:  # Loop principal infinito
        try:
            config = load_config()
            print(f"Configuración cargada: {config}")
            
            if not config or 'wifi_ssid' not in config or 'wifi_password' not in config:
                print("No se encontró configuración válida")
                print("Iniciando modo configuración bluetooth permanente...")
                bluetooth_config()  # Esta función ahora espera indefinidamente
                continue
            
            print("Configuración encontrada, probando conexión WiFi...")
            wifi_manager.SSID = config['wifi_ssid']
            wifi_manager.PASSWORD = config['wifi_password']
            
            if not try_wifi_connection():
                print("No se pudo conectar al WiFi - Borrando configuración...")
                delete_config()
                gc.collect()  # Limpiar memoria
                print("Iniciando modo configuración bluetooth...")
                bluetooth_config()
                continue
            
            # Si llegamos aquí, WiFi está conectado
            print("Iniciando sistema principal...")
            
            try:
                print("Configurando MQTT...")
                mqtt_manager.reconnect()  # Llamar a reconnect() en lugar de ensure_client()
                mqtt_manager.process_queue()  # Procesar mensajes en cola al inicio
                
                print("Configurando relés...")
                for relay_pin in RELAY_PINS:
                    pin = relay_manager.setup_relay(relay_pin, relay_callback)
                    print(f"Relé configurado en pin {relay_pin}")
                
                print("Sistema iniciado completamente")
                print("Iniciando bucle principal...")
                
                # Bucle de operación normal
                last_memory_report_time = utime.ticks_ms()
                last_gc_time = utime.ticks_ms()
                
                while True:
                    watchdog_manager.feed()
                    current_time = utime.ticks_ms()
                    
                    # Limpieza periódica de memoria
                    if utime.ticks_diff(current_time, last_gc_time) >= 30000:  # Cada 30 segundos
                        gc.collect()
                        last_gc_time = current_time
                    
                    # Verificar conexión WiFi y MQTT
                    mqtt_manager.check_connection()
                    
                    # Reporte de memoria si está habilitado
                    if memory_report_enabled and utime.ticks_diff(current_time, last_memory_report_time) > 60000:
                        free_memory = gc.mem_free()
                        print(f"[MEMORY] Memoria libre: {free_memory} bytes")
                        last_memory_report_time = current_time
                    
                    utime.sleep_ms(100)
                    
            except Exception as e:
                print(f"Error en operación normal: {e}")
                raise  # Re-lanzar excepción para reiniciar el ciclo principal
                
        except Exception as e:
            print(f"Error en sistema principal: {e}")
            delete_config()
            gc.collect()
            print("Volviendo a modo configuración bluetooth...")
            bluetooth_config()

if __name__ == "__main__":
    main()