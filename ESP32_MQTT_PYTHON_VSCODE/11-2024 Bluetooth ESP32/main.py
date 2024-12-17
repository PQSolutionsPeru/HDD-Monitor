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

# Constantes del Sistema
MAX_LOOP_TIME = 500          # 500ms máximo por ciclo
MIN_MEMORY_THRESHOLD = 10000  # 10KB mínimo de memoria libre
MQTT_RETRY_COUNT = 3          # 3 intentos para MQTT
WIFI_RETRY_COUNT = 3          # 3 intentos para WiFi
WATCHDOG_TIMEOUT = 30000      # 30 segundos watchdog
CHECK_INTERVAL = 1000         # 1 segundo entre verificaciones
STARTUP_DELAY = 2000          # 2 segundos al inicio

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
        
        # Inicializar gestor de tiempo después de conectar WiFi
        managers["time"] = TimeManager(managers["wifi"])
        
        # Inicializar resto de gestores críticos
        managers["watchdog"] = WatchdogManager()
        managers["mqtt"] = MQTTManager(managers["wifi"])
        
        if current_state == SystemState.CONFIG:
            managers["ble"] = BluetoothManager(esp32_id)
            managers["ble"].set_wifi_callback(
                lambda ssid, pwd: managers["wifi"].connect_wifi(ssid, pwd)
            )
            
            # Esperar a que se complete la configuración WiFi
            while current_state == SystemState.CONFIG:
                managers["ble"].process()
                if managers["wifi"].check_connection():
                    print("[MAIN] WiFi conectado, iniciando sincronización de hora...")
                    
                    # Dar tiempo antes de cerrar BLE
                    utime.sleep_ms(1000)
                    
                    try:
                        print("[MAIN] Desactivando BLE...")
                        managers["ble"].close()
                        del managers["ble"]
                        
                        # Dar más tiempo para que el BLE se cierre completamente
                        utime.sleep_ms(2000)
                        gc.collect()
                        utime.sleep_ms(500)
                        
                        current_state = SystemState.RUNNING
                        print("[MAIN] Transición a modo RUNNING completada")
                    except Exception as e:
                        print(f"[MAIN] Error cerrando BLE: {e}")
                        # Continuar de todos modos
                        current_state = SystemState.RUNNING
        
        if current_state == SystemState.RUNNING:
            # Obtener ID del ESP32 o generarlo si no existe
            managers["esp32_id"] = ESP32IdManager(managers["mqtt"], managers["time"])
            esp32_id = managers["esp32_id"].get_id()
            if not esp32_id:
                esp32_id = managers["esp32_id"]._generate_unique_id()
                managers["esp32_id"]._save_with_backup()
            
            # Dar tiempo al sistema
            gc.collect()
            utime.sleep_ms(1000)
            
            # Configurar resto de gestores con el ID del ESP32
            managers["config"].set_esp32_id(esp32_id)
            managers["relay"] = RelayManager()
            
            # Dar tiempo al sistema antes de MQTT
            gc.collect()
            utime.sleep_ms(1000)
            
            # Conectar a MQTT y suscribirse a tema de configuración
            if not managers["mqtt"].connect():
                raise Exception("No se pudo conectar a MQTT")

            # Dar tiempo entre conexión y suscripción
            utime.sleep_ms(500)

            # Callback básico para suscripción inicial
            def config_callback(topic, msg):
                try:
                    print(f"[MQTT] Mensaje recibido en {topic}: {msg}")
                except Exception as e:
                    print(f"[MQTT] Error en callback: {e}")

            print("[MAIN] Suscribiendo a tópico de configuración...")
            result = managers["mqtt"].subscribe("esp32/config", config_callback)

            if not result:
                print("[MAIN] Error en suscripción, reintentando...")
                utime.sleep_ms(1000)
                result = managers["mqtt"].subscribe("esp32/config", config_callback)
                if not result:
                    raise Exception("No se pudo suscribir a tema de configuración")

            print("[MAIN] Suscripción completada exitosamente")
        
        # Bucle principal
        while True:
            try:
                loop_start = utime.ticks_ms()
                
                # Alimentar watchdog
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
                    
                    # Verificar conexiones (solo si el tiempo está sincronizado)
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
                    
                    # Procesar mensajes MQTT solo si hay conexión
                    if managers["mqtt"].client:
                        managers["mqtt"].check_msg()
                        managers["mqtt"].check_status_report()
                        
                elif current_state == SystemState.CONFIG:
                    managers["ble"].process()
                    if managers["wifi"].check_connection():
                        if managers["config"].is_configured():
                            current_state = SystemState.RUNNING
                            managers["ble"].close()
                            gc.collect()
                
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