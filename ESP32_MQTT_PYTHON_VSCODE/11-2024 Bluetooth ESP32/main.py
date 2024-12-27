import machine
import utime
import gc
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from esp32_id_manager import ESP32IdManager
from bluetooth_manager import BluetoothManager
from config_manager import ConfigManager
from time_manager import TimeManager

# Configuración de Relés
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {32: "Alarma", 33: "Problema", 25: "Supervision"}

# Constantes del Sistema - ACTUALIZADAS
MAX_LOOP_TIME = 1000           # 1000ms máximo por ciclo
MIN_MEMORY_THRESHOLD = 10000   # 10KB mínimo de memoria libre
MQTT_RETRY_COUNT = 5           # 5 intentos para MQTT
WIFI_RETRY_COUNT = 5           # 5 intentos para WiFi
WATCHDOG_TIMEOUT = 60000       # 60 segundos watchdog
CHECK_INTERVAL = 2000          # 2 segundos entre verificaciones
STARTUP_DELAY = 5000           # 5 segundos al inicio
BLE_CONFIG_TIMEOUT = 300000    # 5 minutos para configuración vía BLE

class SystemState:
    INITIAL = "initial"
    CONFIG = "config"
    RUNNING = "running"
    ERROR = "error"

def setup_relays(relay_manager, mqtt_manager, esp32_id, time_manager):
    """Configura los relés y sus callbacks"""
    try:
        print("\n[RELAY] Configurando relés...")
        for pin in RELAY_PINS:
            def create_callback(pin_num):
                def callback(pin, pin_number=pin_num, timestamp=None):
                    state = "DISC" if pin.value() else "OK"
                    pin_name = RELAY_NAMES.get(pin_number, str(pin_number))
                    print(f"[RELAY] Cambio en relé {pin_number} ({pin_name}): {state}")
                    
                    # Mensaje con timestamp GMT-5
                    message = {
                        "esp32_id": esp32_id,
                        "relay": pin_name,
                        "state": state
                    }
                    
                    # Añadir timestamp si está disponible
                    if timestamp:
                        message["date_time"] = timestamp
                    
                    mqtt_manager.publish_event(
                        f"clients/{mqtt_manager.client_id}/panels/{mqtt_manager.panel_id}",
                        message,
                        retain=True,
                        qos=1
                    )
                return callback
            
            relay_manager.setup_relay(pin_num=pin, callback=create_callback(pin))
            print(f"[RELAY] Configurado relé en pin {pin} ({RELAY_NAMES[pin]})")
            
        # Asignar time_manager al relay_manager
        relay_manager.time_manager = time_manager
        return True
        
    except Exception as e:
        print(f"[RELAY] Error configurando relés: {e}")
        return False

def handle_critical_error(error_type, error_message, managers):
    """Maneja errores críticos del sistema"""
    watchdog = managers["watchdog"]
    
    try:
        print(f"[MAIN] Error crítico: {error_type} - {error_message}")
        
        if error_type == "memory_critical":
            gc.collect()
            utime.sleep_ms(100)
            if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                watchdog.force_reset("low_memory")
                
        elif error_type == "wifi_critical":
            # Intentar reconexión WiFi
            if not managers["wifi"].check_connection():
                watchdog.force_reset("wifi_failure")
                
        elif error_type == "mqtt_critical":
            # Intentar reconexión MQTT
            if not managers["mqtt"].check_connection():
                watchdog.force_reset("mqtt_failure")
                
        elif error_type == "time_critical":
            # Intentar resincronizar hora
            if not managers["time"].sync_time():
                watchdog.force_reset("time_sync_failure")
                
        watchdog.force_reset(error_type)
            
    except Exception as e:
        print(f"[MAIN] Error en manejador de errores: {e}")
        watchdog.force_reset("error_handler_failed")

def main():
    """Función principal del sistema"""
    current_state = SystemState.INITIAL
    last_gc_time = utime.ticks_ms()
    last_time_check = utime.ticks_ms()
    managers = {}
    esp32_id = None
    
    try:
        # Inicialización básica
        print("\n[INIT] Iniciando sistema...")
        utime.sleep_ms(STARTUP_DELAY)
        
        # Inicializar gestor de WiFi
        managers["wifi"] = WiFiManager()
        
        # Inicializar gestor de configuración
        managers["config"] = ConfigManager()
        
        # Verificar configuración y conectar WiFi
        if managers["config"].is_configured():
            print("[MAIN] Configuración encontrada, conectando WiFi...")
            wifi_config = managers["config"].get_wifi_config()
            
            if managers["wifi"].connect_wifi(wifi_config["ssid"], wifi_config["password"]):
                current_state = SystemState.RUNNING
            else:
                current_state = SystemState.CONFIG
        else:
            print("[MAIN] No hay configuración, iniciando BLE...")
            current_state = SystemState.CONFIG

        # Inicializar watchdog antes de operaciones críticas
        managers["watchdog"] = WatchdogManager()
        
        # Inicializar gestor de tiempo después de conectar WiFi
        managers["time"] = TimeManager(managers["wifi"])
        
        # Inicializar MQTT después de tiempo
        managers["mqtt"] = MQTTManager(managers["wifi"])
        
        if current_state == SystemState.CONFIG:
            # Solo crear BLE si realmente lo necesitamos
            if not managers["wifi"].check_connection():
                print("[MAIN] Iniciando BLE para configuración WiFi")
                managers["ble"] = BluetoothManager(
                    esp32_id=None, 
                    config_manager=managers["config"],
                    wifi_manager=managers["wifi"]
                )
                
                def wifi_callback(ssid, password):
                    try:
                        print(f"[MAIN] Intentando conexión WiFi a {ssid}")
                        success = managers["wifi"].connect_wifi(ssid, password)
                        
                        if success:
                            print("[MAIN] Conexión WiFi exitosa")
                            if managers["config"].save_wifi_config(ssid, password):
                                print("[MAIN] Configuración WiFi guardada")
                                managers["config"].wifi_configured()
                                return True
                            else:
                                print("[MAIN] Error guardando configuración WiFi")
                                return False
                        
                        print("[MAIN] Conexión WiFi fallida")
                        return False
                        
                    except Exception as e:
                        print(f"[MAIN] Error en conexión WiFi: {e}")
                        return False
                
                managers["ble"].set_wifi_callback(wifi_callback)
            
            # Esperar a que se complete la configuración WiFi y del panel
            ble_timeout = BLE_CONFIG_TIMEOUT
            start_time = utime.ticks_ms()
            wifi_connected = False
            panel_configured = False
            
            print("[MAIN] Esperando configuración WiFi y del panel vía BLE...")
            while utime.ticks_diff(utime.ticks_ms(), start_time) < ble_timeout:
                managers["watchdog"].feed()
                
                # Procesar BLE
                if "ble" in managers:
                    managers["ble"].process()
                
                # Verificar conexión WiFi
                if not wifi_connected and managers["wifi"].check_connection():
                    wifi_connected = True
                    print("[MAIN] WiFi conectado, manteniendo BLE activo para configuración del panel...")
                    
                    # Dar tiempo para que se envíe la respuesta BLE
                    managers["watchdog"].feed()
                    utime.sleep_ms(2000)
                    
                # Verificar configuración del panel
                if not panel_configured and managers["config"].is_configured():
                    panel_configured = True
                    print("[MAIN] Panel configurado, cerrando BLE...")
                    
                    # Cerrar BLE
                    if "ble" in managers:
                        print("[MAIN] Desactivando BLE...")
                        try:
                            managers["watchdog"].feed()
                            managers["ble"].close()
                            del managers["ble"]
                            gc.collect()
                            utime.sleep_ms(1000)
                        except Exception as e:
                            print(f"[MAIN] Error cerrando BLE: {e}")
                    
                    current_state = SystemState.RUNNING
                    break
                
                utime.sleep_ms(100)

            if not wifi_connected:
                print("[MAIN] Timeout esperando configuración WiFi")
                handle_critical_error("wifi_timeout", "Timeout esperando configuración WiFi", managers)
                
            if not panel_configured:
                print("[MAIN] Timeout esperando configuración del panel")
                handle_critical_error("panel_timeout", "Timeout esperando configuración del panel", managers)
        
        if current_state == SystemState.RUNNING:
            # Alimentar watchdog antes de operaciones largas
            managers["watchdog"].feed()
            
            # Obtener ID del ESP32 o generarlo si no existe
            managers["esp32_id"] = ESP32IdManager(managers["mqtt"], managers["time"])
            managers["watchdog"].feed()
            
            esp32_id = managers["esp32_id"].get_id()
            if not esp32_id:
                managers["watchdog"].feed()
                esp32_id = managers["esp32_id"]._generate_unique_id()
                managers["esp32_id"]._save_with_backup()
            
            managers["watchdog"].feed()
            
            # Configurar resto de gestores con el ID del ESP32
            managers["config"].set_esp32_id(esp32_id)
            managers["relay"] = RelayManager()
            
            # Preparar conexión MQTT
            print(f"[MAIN] Memoria libre antes de MQTT: {gc.mem_free()}")
            managers["watchdog"].feed()
            gc.collect()
            utime.sleep_ms(1000)

            # Intentar conexión MQTT con reintentos
            mqtt_connected = False
            for retry in range(MQTT_RETRY_COUNT):
                managers["watchdog"].feed()
                print(f"[MAIN] Intento MQTT {retry + 1}/{MQTT_RETRY_COUNT}")
                print(f"[MAIN] Memoria libre: {gc.mem_free()}")
                
                if managers["mqtt"].connect():
                    managers["config"].set_mqtt_manager(managers["mqtt"])
                    mqtt_connected = True
                    break
                    
                print(f"[MAIN] Fallo en intento {retry + 1}")
                gc.collect()
                utime.sleep_ms(2000)
            
            if not mqtt_connected:
                raise Exception(f"No se pudo conectar a MQTT después de {MQTT_RETRY_COUNT} intentos")

            # Configurar callback MQTT
            def config_callback(topic, msg):
                try:
                    print(f"[MQTT] Mensaje recibido en {topic}: {msg}")
                    managers["config"].handle_config_message(topic, msg)
                except Exception as e:
                    print(f"[MQTT] Error en callback: {e}")

            # Suscribirse al tópico específico
            print("[MAIN] Suscribiendo a tópico de configuración...")
            config_topic = f"esp32/config/{esp32_id}"
            if not managers["mqtt"].subscribe(config_topic, config_callback):
                print("[MAIN] Error en suscripción, reintentando...")
                managers["watchdog"].feed()
                utime.sleep_ms(1000)
                if not managers["mqtt"].subscribe(config_topic, config_callback):
                    raise Exception("No se pudo suscribir a tema de configuración")

            print("[MAIN] Suscripción completada exitosamente")

            # Publicar estado inicial
            network_info = {
                "esp32_id": esp32_id,
                "MAC": managers["esp32_id"].get_mac(),
                "IP": managers["wifi"].get_ip_address(),
                "status": "AWAITING_CONFIG",
                "lastUpdate": utime.ticks_ms()
            }
            
            # Publicar con retain=True para asegurar entrega
            if not managers["mqtt"].publish_event(
                "esp32/network_info",
                network_info,
                retain=True,
                qos=1
            ):
                raise Exception("No se pudo publicar información de red")

            # Entrar en modo espera de configuración
            managers["config"].current_mode = managers["config"].MODES['AWAITING_CONFIG']
            managers["watchdog"].feed()
        
        # Bucle principal con tiempo extendido para operaciones
        while True:
            try:
                loop_start = utime.ticks_ms()
                managers["watchdog"].feed()
                
                # GC periódico
                if utime.ticks_diff(loop_start, last_gc_time) >= 300000:  # 5 minutos
                    gc.collect()
                    last_gc_time = loop_start
                
                # Procesar según estado
                if current_state == SystemState.RUNNING:
                    # Verificar memoria
                    if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                        handle_critical_error("memory_critical", "Memoria bajo nivel crítico", managers)
                    
                    # Verificar conexiones
                    if managers["time"].is_synced:
                        if not managers["wifi"].check_connection():
                            handle_critical_error("wifi_critical", "Pérdida de conexión WiFi", managers)
                            
                        if not managers["mqtt"].check_connection():
                            handle_critical_error("mqtt_critical", "Pérdida de conexión MQTT", managers)
                    
                    # Verificar hora cada minuto
                    if utime.ticks_diff(loop_start, last_time_check) >= 60000:
                        if not managers["time"].check_sync():
                            print("[MAIN] Reintentando sincronización horaria...")
                        last_time_check = loop_start
                    
                    # Procesar mensajes MQTT
                    if managers["mqtt"].client:
                        managers["mqtt"].check_msg()
                        managers["mqtt"].check_status_report()

                    # Configurar relés
                    if not setup_relays(managers["relay"], managers["mqtt"], esp32_id, managers["time"]):
                        raise Exception("Error configurando relés")
                        
                    # Entrar en modo de operación normal
                    managers["config"].enter_running_mode()
                        
                elif current_state == SystemState.CONFIG:
                    if managers["wifi"].check_connection() and managers["config"].is_configured():
                        current_state = SystemState.RUNNING
                
                elif current_state == SystemState.ERROR:
                    print("[MAIN] Intentando recuperar de error...")
                    if managers["wifi"].check_connection() and managers["mqtt"].check_connection():
                        current_state = SystemState.RUNNING
                    else:
                        managers["watchdog"].force_reset("recovery_failed")
                
                # Control de tiempo de bucle
                loop_time = utime.ticks_diff(utime.ticks_ms(), loop_start)
                if loop_time < MAX_LOOP_TIME:
                    utime.sleep_ms(MAX_LOOP_TIME - loop_time)
                    
            except Exception as e:
                print(f"[MAIN] Error en bucle principal: {e}")
                handle_critical_error("main_loop_error", str(e), managers)
                
    except KeyboardInterrupt:
        print("\n[MAIN] Programa interrumpido por usuario")
    except Exception as e:
        print(f"\n[MAIN] Error fatal: {e}")
    finally:
        if managers:
            try:
                if "ble" in managers:
                    managers["ble"].close()
                if "mqtt" in managers:
                    managers["mqtt"].close()
                if "wifi" in managers:
                    managers["wifi"].disconnect()
            except:
                pass
        machine.reset()

if __name__ == "__main__":
    main()