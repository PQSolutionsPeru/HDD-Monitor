import machine
import utime
import gc
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from esp32_id_manager import ESP32IdManager
from bluetooth_manager import BluetoothManager
from time_manager import TimeManager

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
    """Configures relay monitoring"""
    try:
        print("\n[RELAY] Configuring relays...")
        managers["relay"] = RelayManager()
        relay_config = managers["relay"].config
        
        def relay_callback(pin, pin_num):
            try:
                state = "DISC" if pin.value() else "OK"
                pin_names = relay_config.get_relay_pins()
                pin_name = pin_names.get(pin_num, str(pin_num))
                print(f"[RELAY] Change in relay {pin_num} ({pin_name}): {state}")
                
                if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                    message = {
                        "esp32_id": esp32_id,
                        "relay": pin_name,
                        "state": state,
                        "timestamp": {
                            "value": utime.ticks_ms(),
                            "type": "realtime"
                        }
                    }
                    
                    managers["mqtt"].publish_event(
                        f"clients/{managers['mqtt'].client_id}/panels/{managers['mqtt'].panel_id}",
                        message,
                        retain=False,
                        qos=1
                    )
            except Exception as e:
                print(f"[RELAY] Callback error: {e}")
        
        # Configure each relay using config
        pin_config = relay_config.get_relay_pins()
        for pin_num, pin_name in pin_config.items():
            relay_pin = managers["relay"].setup_relay(pin_num, relay_callback)
            print(f"[RELAY] Configured relay on pin {pin_num} ({pin_name})")
            
            # Get and report initial state
            initial_state = "DISC" if relay_pin.value() else "OK"
            print(f"[RELAY] Initial state of relay {pin_num} ({pin_name}): {initial_state}")
            
            # Force callback for initial state
            relay_callback(relay_pin, pin_num)
            
        return True
        
    except Exception as e:
        print(f"[RELAY] Setup error: {e}")
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
        utime.sleep_ms(500)
        
        # Forzar GC antes de WiFi
        gc.collect()
        utime.sleep_ms(1000)
        
        # 2. Iniciar WiFiManager
        print("[INIT] Iniciando WiFiManager...")
        for attempt in range(3):
            try:
                print(f"[INIT] Intento WiFi {attempt + 1}/3")
                managers["watchdog"].feed()
                managers["wifi"] = WiFiManager()
                if managers["wifi"].sta_if:
                    print("[INIT] WiFiManager iniciado correctamente")
                    # Intentar conectar si hay configuración guardada
                    wifi_config = managers["wifi"].get_wifi_config()
                    if wifi_config.get('ssid') and wifi_config.get('password'):
                        print("[INIT] Intentando conectar con configuración guardada...")
                        if managers["wifi"].connect_wifi(wifi_config['ssid'], wifi_config['password']):
                            print("[INIT] Conexión exitosa con configuración guardada")
                            break
                        else:
                            print("[INIT] Fallo conexión con configuración guardada")
                    break
            except Exception as e:
                print(f"[INIT] Error en intento WiFi {attempt + 1}: {e}")
                gc.collect()
                utime.sleep_ms(2000)
                
        if "wifi" not in managers:
            raise Exception("No se pudo iniciar WiFiManager")
            
        # 3. Resto de managers
        for manager_init in [
            ("time", lambda: TimeManager(managers["wifi"])),
            ("esp32_id", lambda: ESP32IdManager()),
            ("mqtt", lambda: MQTTManager(managers["wifi"]))
        ]:
            name, init_func = manager_init
            print(f"[INIT] Iniciando {name}Manager...")
            managers["watchdog"].feed()
            managers[name] = init_func()
            utime.sleep_ms(500)
            gc.collect()
        
        # Configuración final de MQTT
        if managers.get("esp32_id"):
            managers["mqtt"].esp32_id = managers["esp32_id"].get_id()
            
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
        print("[CONFIG] Waiting for WiFi credentials...")
        
        # Initialize BLE
        bluetooth_manager = BluetoothManager()
        ble_timeout = BLE_CONFIG_TIMEOUT
        start_time = utime.ticks_ms()
        
        # Store ESP32 ID generated by BLE manager
        if bluetooth_manager.esp32_id:
            managers["esp32_id"].esp32_id = bluetooth_manager.esp32_id
            managers["esp32_id"]._save_with_backup()
            print(f"[CONFIG] Using ESP32 ID from BLE: {bluetooth_manager.esp32_id}")
        
        while utime.ticks_diff(utime.ticks_ms(), start_time) < ble_timeout:
            managers["watchdog"].feed()
            
            # Check for BLE timeout
            if bluetooth_manager.check_timeout():
                break
                
            # Wait for WiFi credentials
            data = bluetooth_manager.wait_for_data()
            if data and 'ssid' in data and 'password' in data:
                ssid = data['ssid'].strip()
                password = data['password'].strip()
                
                if not ssid or not password or len(password) < 8:
                    bluetooth_manager.write_data("error:invalid_credentials")
                    continue
                    
                # Configure WiFi
                if managers["wifi"].connect_wifi(ssid, password):
                    managers["wifi"].save_wifi_config(ssid, password)
                    bluetooth_manager.write_data("status:wifi_connected")
                    
                    # Allow some time for status message
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
    """Sets up MQTT connection and waits for VM configuration if needed"""
    try:
        managers["watchdog"].feed()
        esp32_id = managers["esp32_id"].get_id()
        
        if not esp32_id:
            print("[MQTT] Error: No ESP32 ID available")
            return False
            
        print(f"[MQTT] Setting up connection for ESP32 ID: {esp32_id}")
        managers["mqtt"].esp32_id = esp32_id
        
        # Un solo intento de conexión MQTT
        if not managers["mqtt"].connect():
            print("[MQTT] Could not establish MQTT connection")
            return False
            
        # Suscripción al tópico de configuración
        config_topic = f"esp32/config/{esp32_id}"
        if not managers["mqtt"].subscribe(config_topic):
            print("[MQTT] Subscription failed")
            return False
        
        print("[MQTT] Setup completed successfully")
        
        # Solo esperar configuración si no tenemos una válida
        if not managers["mqtt"].client_id or not managers["mqtt"].panel_id:
            print("[MQTT] No valid configuration found. Waiting for VM...")
            
            # Esperar la configuración del panel
            start_time = utime.ticks_ms()
            max_wait = 300000  # 5 minutos
            
            while utime.ticks_diff(utime.ticks_ms(), start_time) < max_wait:
                managers["watchdog"].feed()
                managers["mqtt"].check_msg()
                
                if not managers["wifi"].check_connection():
                    print("[MQTT] WiFi connection lost while waiting for config")
                    return False
                    
                if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                    print("[MQTT] Configuration received successfully")
                    return True
                    
                utime.sleep_ms(100)
                
            print("[MQTT] Configuration wait timeout")
            return False
        else:
            print("[MQTT] Valid configuration already exists")
            return True
            
    except Exception as e:
        print(f"[MQTT] Setup error: {e}")
        return False

def handle_running_mode(managers):
    """Handles system in running mode"""
    try:
        # Process MQTT messages
        if managers["mqtt"].client:
            managers["mqtt"].check_msg()
            
        # Check connections
        if not managers["wifi"].check_connection():
            print("[RUNNING] WiFi connection lost")
            return False
            
        if not managers["mqtt"].check_connection():
            print("[RUNNING] MQTT connection lost")
            return False
            
        return True
        
    except Exception as e:
        print(f"[RUNNING] Error: {e}")
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