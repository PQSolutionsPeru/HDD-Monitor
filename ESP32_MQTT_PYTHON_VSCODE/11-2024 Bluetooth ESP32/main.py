import machine
import utime
import gc
import random
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from esp32_id_manager import ESP32IdManager
from bluetooth_manager import BluetoothManager
from time_manager import TimeManager

_last_state_check = 0

# System Constants
MAX_LOOP_TIME = 1000           # 1000ms maximum per cycle
MIN_MEMORY_THRESHOLD = 15000   # 15KB minimum free memory
MQTT_RETRY_COUNT = 5           # 5 MQTT attempts
WIFI_RETRY_COUNT = 5           # 5 WiFi attempts
WATCHDOG_TIMEOUT = 60000       # 60 seconds watchdog
CHECK_INTERVAL = 2000          # 2 seconds between checks
STARTUP_DELAY = 5000           # 5 seconds at startup
BLE_CONFIG_TIMEOUT = 300000    # 5 minutes for BLE configuration

class SystemState:
    INITIAL = "initial"
    CONFIG = "config"
    RUNNING = "running"
    ERROR = "error"

def check_memory():
    """Checks system memory state"""
    free_mem = gc.mem_free()
    print(f"[MEMORY] Free memory: {free_mem} bytes")
    if free_mem < MIN_MEMORY_THRESHOLD:
        print("[MEMORY] Low memory, performing cleanup")
        gc.collect()
        utime.sleep_ms(100)
        free_mem = gc.mem_free()
        print(f"[MEMORY] Memory after cleanup: {free_mem} bytes")
        return free_mem >= MIN_MEMORY_THRESHOLD
    return True

def setup_relay_monitoring(managers, esp32_id):
    try:
        print("\n[RELAY] Configuring relays...")
        managers["relay"] = RelayManager()
        managers["mqtt"].set_relay_manager(managers["relay"])
        relay_config = managers["relay"].config
        
        # Controladores de estado para evitar duplicados
        last_relay_states = {}  # Almacena el último estado enviado
        last_send_times = {}    # Almacena el último tiempo de envío por relé
        MIN_SEND_INTERVAL = 2000  # Intervalo mínimo entre envíos (2 segundos)
        
        def relay_callback(pin, pin_num):
            try:
                pin_names = relay_config.get_relay_pins()
                pin_name = pin_names.get(pin_num, str(pin_num))
                current_time = utime.ticks_ms()
                
                # Leer estado directamente del pin
                current_state = "OK" if pin.value() == 0 else "DISC"
                
                # Solo enviar si:
                # 1. Es el primer mensaje para este relé, o
                # 2. El estado ha cambiado desde el último envío, y
                # 3. Ha pasado suficiente tiempo desde el último envío
                
                if (
                    pin_name not in last_relay_states or 
                    (current_state != last_relay_states[pin_name] and 
                     utime.ticks_diff(current_time, last_send_times.get(pin_name, 0)) > MIN_SEND_INTERVAL)
                ):
                    print(f"[RELAY] Sending change notification for {pin_name}: {current_state}")
                    # Actualizar registros
                    last_relay_states[pin_name] = current_state
                    last_send_times[pin_name] = current_time
                    
                    if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                        message = {
                            "esp32_id": esp32_id,
                            "relay": pin_name,
                            "state": current_state,
                            "timestamp": {
                                "value": current_time,
                                "type": "realtime"
                            }
                        }
                        
                        managers["mqtt"].publish_event(
                            f"clients/{managers['mqtt'].client_id}/panels/{managers['mqtt'].panel_id}",
                            message,
                            retain=False,
                            qos=1
                        )
                else:
                    # Registrar que se evitó un duplicado
                    if pin_name in last_relay_states and current_state == last_relay_states[pin_name]:
                        time_since_last = utime.ticks_diff(current_time, last_send_times.get(pin_name, 0))
                        print(f"[RELAY] Skipping duplicate notification for {pin_name} (same state: {current_state}, {time_since_last}ms since last)")
                    else:
                        time_since_last = utime.ticks_diff(current_time, last_send_times.get(pin_name, 0)) 
                        print(f"[RELAY] Debounce active for {pin_name}, waiting more time (elapsed: {time_since_last}ms)")
                        
            except Exception as e:
                print(f"[RELAY] Callback error: {e}")
                import sys
                sys.print_exception(e)
        
        # Configure each relay using config
        pin_config = relay_config.get_relay_pins()
        for pin_num, pin_name in pin_config.items():
            relay_pin = managers["relay"].setup_relay(pin_num, relay_callback)
            print(f"[RELAY] Configured relay on pin {pin_num} ({pin_name})")
            
            # Get initial state
            pin_value = relay_pin.value()
            initial_state = "OK" if pin_value == 0 else "DISC"
            
            # Inicializar estados
            last_relay_states[pin_name] = initial_state
            last_send_times[pin_name] = utime.ticks_ms()
            
            print(f"[RELAY] Initial state of relay {pin_num} ({pin_name}): {initial_state}")
            
        return True
        
    except Exception as e:
        print(f"[RELAY] Setup error: {e}")
        import sys
        sys.print_exception(e)
        return False

def pre_init_cleanup():
    """Limpieza inicial del sistema antes de cualquier inicialización"""
    try:
        # Forzar recolección de basura inicial
        gc.collect()
        utime.sleep_ms(1000)
        
        # Intentar liberar recursos WiFi previos
        try:
            import network
            sta_if = network.WLAN(network.STA_IF)
            if sta_if:
                sta_if.active(False)
                utime.sleep_ms(1000)
                del sta_if
        except:
            pass
        
        # Segunda recolección después de liberar WiFi
        gc.collect()
        utime.sleep_ms(1000)
        
        print(f"[INIT] Memoria disponible después de limpieza: {gc.mem_free()} bytes")
        return True
    except Exception as e:
        print(f"[INIT] Error en limpieza inicial: {e}")
        return False

def initialize_system():
    """Inicializa el sistema con manejo mejorado de recursos"""
    managers = {}
    
    try:
        print("\n[INIT] Iniciando sistema...")
        print(f"[INIT] Memoria inicial: {gc.mem_free()} bytes")
        
        # Limpieza previa
        if not pre_init_cleanup():
            raise Exception("Fallo en limpieza inicial")
            
        # 1. Iniciar WatchdogManager primero
        print("[INIT] Iniciando WatchdogManager...")
        managers["watchdog"] = WatchdogManager()
        managers["watchdog"].feed()
        
        # 2. Iniciar WiFiManager
        print("[INIT] Iniciando WiFiManager...")
        for attempt in range(3):
            try:
                managers["watchdog"].feed()
                managers["wifi"] = WiFiManager()
                if managers["wifi"].sta_if:
                    print("[INIT] WiFiManager iniciado correctamente")
                    break
            except Exception as e:
                print(f"[INIT] Error en intento WiFi {attempt + 1}: {e}")
                gc.collect()
                utime.sleep_ms(2000)
                
        if "wifi" not in managers:
            raise Exception("No se pudo iniciar WiFiManager")
        
        # 3. Iniciar ESP32IdManager y obtener ID - NUEVO
        print("[INIT] Iniciando ESP32IdManager...")
        managers["esp32_id"] = ESP32IdManager()
        esp32_id = managers["esp32_id"].get_id()
        if not esp32_id:
            raise Exception("No se pudo obtener ESP32_ID")
        print(f"[INIT] ESP32_ID obtenido: {esp32_id}")
        
        # 4. Iniciar MQTTManager y configurar credenciales - MODIFICADO
        print("[INIT] Iniciando MQTTManager...")
        managers["mqtt"] = MQTTManager(managers["wifi"])
        if not managers["mqtt"].set_esp32_id(esp32_id):
            raise Exception("No se pudieron configurar credenciales MQTT")
        print("[INIT] Credenciales MQTT configuradas")
        
        # 5. Resto de managers
        managers["time"] = TimeManager(managers["wifi"])
        
        print("[INIT] Inicialización completada")
        return managers
        
    except Exception as e:
        print(f"[INIT] Error fatal en inicialización: {e}")
        if "watchdog" in managers:
            try:
                managers["watchdog"].force_reset("init_error")
            except:
                pass
        machine.reset()

def setup_wifi_mode(managers):
    """Sets up WiFi configuration mode using BLE"""
    try:
        print("[CONFIG] Starting BLE configuration mode")
        
        # Modificado: Pasar ESP32_ID al crear BluetoothManager
        bluetooth_manager = BluetoothManager(managers["esp32_id"].get_id())
        ble_timeout = BLE_CONFIG_TIMEOUT
        start_time = utime.ticks_ms()
        
        while utime.ticks_diff(utime.ticks_ms(), start_time) < ble_timeout:
            managers["watchdog"].feed()
            
            if bluetooth_manager.check_timeout():
                break
            
            data = bluetooth_manager.wait_for_data()
            if data and 'ssid' in data and 'password' in data:
                ssid = data['ssid'].strip()
                password = data['password'].strip()
                
                if not ssid or not password or len(password) < 8:
                    bluetooth_manager.write_data("error:invalid_credentials")
                    continue
                    
                if managers["wifi"].connect_wifi(ssid, password):
                    managers["wifi"].save_wifi_config(ssid, password)
                    bluetooth_manager.write_data("status:wifi_connected")
                    utime.sleep_ms(1000)
                    bluetooth_manager.cleanup()
                    return True
                else:
                    bluetooth_manager.write_data("error:wifi_failed")
            
            utime.sleep_ms(100)
            
        bluetooth_manager.cleanup()
        return False
        
    except Exception as e:
        print(f"[CONFIG] Error in BLE configuration: {e}")
        try:
            bluetooth_manager.cleanup()
        except:
            pass
        return False

def wait_for_mqtt_config(managers, timeout=300000):
    """Waits for MQTT configuration from the VM"""
    start_time = utime.ticks_ms()
    print("[MQTT] Waiting for configuration from VM...")

    while utime.ticks_diff(utime.ticks_ms(), start_time) < timeout:
        try:
            managers["watchdog"].feed()
            
            # Check WiFi connection
            if not managers["wifi"].check_connection():
                print("[MQTT] WiFi connection lost while waiting")
                return False
                
            # Process MQTT messages
            if managers["mqtt"].client:
                managers["mqtt"].check_msg()
                
                # Check if config was received
                if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                    print("[MQTT] Configuration received from VM")
                    return True
            
            # Memory management
            if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                print("[MQTT] Low memory, collecting garbage")
                gc.collect()
            
            utime.sleep_ms(1000)  # Only check once per second
            
        except Exception as e:
            print(f"[MQTT] Error waiting for config: {e}")
            return False

    print("[MQTT] Configuration timeout")
    return False

def setup_mqtt_connection(managers):
    """Configura la conexión MQTT usando las credenciales del ESP32_ID"""
    try:
        managers["watchdog"].feed()
        esp32_id = managers["esp32_id"].get_id()
        
        if not esp32_id:
            print("[MQTT] Error: No ESP32 ID available")
            return False
            
        print(f"[MQTT] Configurando conexión para ESP32 ID: {esp32_id}")
        
        # Intentar conexión MQTT con las nuevas credenciales
        if not managers["mqtt"].connect():
            print("[MQTT] Error: No se pudo establecer conexión MQTT")
            return False
            
        # Suscripción al tópico de configuración
        config_topic = f"esp32/config/{esp32_id}"
        if not managers["mqtt"].subscribe(config_topic):
            print("[MQTT] Error: Falló la suscripción")
            return False
        
        print("[MQTT] Setup completado. Esperando configuración de la VM...")
        
        # Esperar configuración
        start_time = utime.ticks_ms()
        max_wait = 300000  # 5 minutos
        
        while utime.ticks_diff(utime.ticks_ms(), start_time) < max_wait:
            managers["watchdog"].feed()
            managers["mqtt"].check_msg()
            
            if not managers["wifi"].check_connection():
                print("[MQTT] Se perdió conexión WiFi")
                return False
                
            if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                print("[MQTT] Configuración recibida exitosamente")
                return True
                
            utime.sleep_ms(100)
            
        print("[MQTT] Timeout esperando configuración")
        return False
            
    except Exception as e:
        print(f"[MQTT] Error en setup_mqtt_connection: {e}")
        return False

def handle_running_mode(managers):
    """Handles system in running mode with state verification"""
    try:
        # Process MQTT messages and check connection health
        if managers["mqtt"].client:
            managers["mqtt"].check_msg()
            
            # Verificar conexión MQTT
            if not managers["mqtt"].check_connection():
                print("[RUNNING] MQTT connection unhealthy")
                managers["mqtt"].was_previously_connected = False
                return False
                
        # Check WiFi separately
        if not managers["wifi"].check_connection():
            print("[RUNNING] WiFi connection lost")
            managers["mqtt"].was_previously_connected = False
            return False
            
        return True
        
    except Exception as e:
        print(f"[RUNNING] Error: {e}")
        import sys
        sys.print_exception(e)
        managers["mqtt"].was_previously_connected = False
        return False

def main():
    """Main program execution"""
    current_state = SystemState.INITIAL
    managers = {}
    
    try:
        # Initialize system
        managers = initialize_system()
        if not managers:
            raise Exception("System initialization failed")
            
        # Verificar si ya tenemos configuración WiFi
        wifi_config = managers["wifi"].get_wifi_config()
        has_wifi_config = wifi_config.get('ssid') and wifi_config.get('password')
        
        if has_wifi_config and managers["wifi"].check_connection():
            print("[MAIN] Usando configuración WiFi existente")
            current_state = SystemState.CONFIG
        else:
            print("[MAIN] Necesita configuración WiFi")
            current_state = SystemState.INITIAL
            
        # Main operation loop
        while True:
            try:
                managers["watchdog"].feed()
                gc.collect()
                
                # Process current state
                if current_state == SystemState.INITIAL:
                    # Solo entrar en modo BLE si no hay configuración o la conexión falló
                    if not has_wifi_config or not managers["wifi"].check_connection():
                        if setup_wifi_mode(managers):
                            current_state = SystemState.CONFIG
                        
                elif current_state == SystemState.CONFIG:
                    # Verificar conexión WiFi antes de MQTT
                    if managers["wifi"].check_connection():
                        if managers["mqtt"].check_connection() or setup_mqtt_connection(managers):
                            if setup_relay_monitoring(managers, managers["esp32_id"].get_id()):
                                current_state = SystemState.RUNNING
                            else:
                                raise Exception("Relay setup failed")
                    else:
                        current_state = SystemState.INITIAL
                
                elif current_state == SystemState.RUNNING:
                    if not handle_running_mode(managers):
                        current_state = SystemState.CONFIG
                
                # Memory check and cleanup
                if not check_memory():
                    gc.collect()
                    utime.sleep_ms(100)
                    
                utime.sleep_ms(CHECK_INTERVAL)
                
            except Exception as e:
                print(f"[MAIN] Loop error: {e}")
                if "watchdog" in managers:
                    managers["watchdog"].force_reset("loop_error")
                else:
                    machine.reset()
                    
    except KeyboardInterrupt:
        print("\n[MAIN] Program interrupted by user")
    except Exception as e:
        print(f"\n[MAIN] Fatal error: {e}")
    finally:
        # Cleanup
        if "mqtt" in managers:
            managers["mqtt"].close()  # Cambio de disconnect() a close() para liberar el device del pool
        if "wifi" in managers:
            managers["wifi"].disconnect()
        gc.collect()
        machine.reset()

if __name__ == "__main__":
    main()