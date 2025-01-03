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

# Relay Configuration
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {32: "Alarma", 33: "Problema", 25: "Supervision"}

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
        
        def relay_callback(pin, pin_num):
            try:
                state = "DISC" if pin.value() else "OK"
                pin_name = RELAY_NAMES.get(pin_num, str(pin_num))
                print(f"[RELAY] Change in relay {pin_num} ({pin_name}): {state}")
                
                if managers["mqtt"].client_id and managers["mqtt"].panel_id:
                    message = {
                        "esp32_id": esp32_id,
                        "relay": pin_name,
                        "state": state
                    }
                    
                    managers["mqtt"].publish_event(
                        f"clients/{managers['mqtt'].client_id}/panels/{managers['mqtt'].panel_id}",
                        message,
                        retain=False,
                        qos=1
                    )
            except Exception as e:
                print(f"[RELAY] Callback error: {e}")
        
        # Configure each relay
        for pin in RELAY_PINS:
            managers["relay"].setup_relay(pin, relay_callback)
            print(f"[RELAY] Configured relay on pin {pin} ({RELAY_NAMES[pin]})")
            
        return True
        
    except Exception as e:
        print(f"[RELAY] Setup error: {e}")
        return False

def initialize_system():
    """Initializes basic system managers"""
    managers = {}
    
    try:
        print("\n[INIT] Starting system initialization...")
        print(f"[INIT] Initial free memory: {gc.mem_free()} bytes")
        
        # Initial memory cleanup
        gc.collect()
        utime.sleep_ms(1000)
        
        # 1. Initialize ConfigManager
        print("[INIT] Starting ConfigManager...")
        managers["config"] = ConfigManager()
        utime.sleep_ms(100)
        
        # 2. Initialize WatchdogManager
        print("[INIT] Starting WatchdogManager...")
        managers["watchdog"] = WatchdogManager()
        managers["watchdog"].feed()
        utime.sleep_ms(100)
        
        # 3. Initialize WiFiManager
        print("[INIT] Starting WiFiManager...")
        managers["wifi"] = WiFiManager()
        utime.sleep_ms(500)
            
        # Feed watchdog
        managers["watchdog"].feed()
        
        # 4. Initialize TimeManager
        print("[INIT] Starting TimeManager...")
        managers["time"] = TimeManager(managers["wifi"])
        utime.sleep_ms(100)
        
        # 5. Initialize ESP32IdManager
        print("[INIT] Starting ESP32IdManager...")
        managers["esp32_id"] = ESP32IdManager()
        esp32_id = managers["esp32_id"].get_id()
        if not esp32_id:
            esp32_id = managers["esp32_id"]._generate_unique_id()
            managers["esp32_id"]._save_with_backup()
        print(f"[INIT] ESP32 ID: {esp32_id}")
        
        # 6. Initialize MQTTManager
        print("[INIT] Starting MQTTManager...")
        managers["mqtt"] = MQTTManager(managers["wifi"])
        managers["mqtt"].esp32_id = esp32_id
        utime.sleep_ms(100)
        
        # Check memory
        gc.collect()
        print(f"[INIT] Memory after basic initializations: {gc.mem_free()} bytes")
        
        print("[INIT] Basic initialization completed")
        return managers
        
    except Exception as e:
        print(f"[INIT] Fatal initialization error: {e}")
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
                    managers["config"].save_wifi_config(ssid, password)
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
    """Sets up MQTT connection and waits for VM configuration"""
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
            
        # Un solo mensaje de estado inicial
        network_info = {
            "esp32_id": esp32_id,
            "MAC": managers["esp32_id"].get_mac(),
            "IP": managers["wifi"].get_current_ip(),
            "status": "AWAITING_CONFIG",
            "timestamp": {
                "value": utime.ticks_ms(),
                "type": "realtime"
            }
        }
        
        # Publicar una sola vez con QoS 1
        if not managers["mqtt"].publish_event(
            "esp32/network_info",
            network_info,
            retain=True,
            qos=1
        ):
            print("[MQTT] Failed to publish network info")
            return False
            
        print("[MQTT] Setup completed successfully")
        print("[MQTT] Waiting for VM to send configuration...")
        
        # Esperar la configuración del panel
        start_time = utime.ticks_ms()
        max_wait = 300000  # 5 minutos
        
        while utime.ticks_diff(utime.ticks_ms(), start_time) < max_wait:
            managers["watchdog"].feed()
            managers["mqtt"].check_msg()
            
            if not managers["wifi"].check_connection():
                print("[MQTT] WiFi connection lost while waiting for config")
                return False
                
            utime.sleep_ms(100)
            
        print("[MQTT] Configuration wait timeout")
        return False
        
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
            
        current_state = SystemState.CONFIG
        
        # Main operation loop
        while True:
            try:
                managers["watchdog"].feed()
                gc.collect()  # Regular garbage collection
                
                # Process current state
                if current_state == SystemState.CONFIG:
                    # Check if WiFi needs configuration
                    if not managers["wifi"].check_connection():
                        if setup_wifi_mode(managers):
                            # WiFi configured, proceed with MQTT setup
                            if setup_mqtt_connection(managers):
                                if setup_relay_monitoring(managers, managers["esp32_id"].get_id()):
                                    current_state = SystemState.RUNNING
                                else:
                                    raise Exception("Relay setup failed")
                    else:
                        # WiFi already configured, check MQTT
                        if managers["mqtt"].check_connection() or setup_mqtt_connection(managers):
                            if setup_relay_monitoring(managers, managers["esp32_id"].get_id()):
                                current_state = SystemState.RUNNING
                            else:
                                raise Exception("Relay setup failed")
                
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
            managers["mqtt"].disconnect()
        if "wifi" in managers:
            managers["wifi"].disconnect()
        gc.collect()
        machine.reset()

if __name__ == "__main__":
    main()