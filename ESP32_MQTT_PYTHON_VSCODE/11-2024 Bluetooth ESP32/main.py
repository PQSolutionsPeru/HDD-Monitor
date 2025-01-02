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

def check_memory():
    """Verifica el estado de la memoria"""
    free_mem = gc.mem_free()
    print(f"[MEMORY] Memoria libre: {free_mem} bytes")
    if free_mem < MIN_MEMORY_THRESHOLD:
        print("[MEMORY] Memoria baja, realizando limpieza")
        gc.collect()
        utime.sleep_ms(100)
        free_mem = gc.mem_free()
        print(f"[MEMORY] Memoria después de limpieza: {free_mem} bytes")
        return free_mem >= MIN_MEMORY_THRESHOLD
    return True

def setup_relays(relay_manager, mqtt_manager, esp32_id, time_manager):
    """Configura los relés y sus callbacks"""
    try:
        print("\n[RELAY] Configurando relés...")
        
        def relay_callback(pin, pin_number, timestamp=None):
            try:
                state = "DISC" if pin.value() else "OK"
                pin_name = RELAY_NAMES.get(pin_number, str(pin_number))
                print(f"[RELAY] Cambio en relé {pin_number} ({pin_name}): {state}")
                
                message = {
                    "esp32_id": esp32_id,
                    "relay": pin_name,
                    "state": state
                }
                
                if timestamp:
                    message["date_time"] = timestamp
                
                if mqtt_manager.client_id and mqtt_manager.panel_id:
                    mqtt_manager.publish_event(
                        f"clients/{mqtt_manager.client_id}/panels/{mqtt_manager.panel_id}",
                        message,
                        retain=False,
                        qos=1
                    )
            except Exception as e:
                print(f"[RELAY] Error en callback: {e}")
        
        # Configurar cada relé
        for pin in RELAY_PINS:
            relay_manager.setup_relay(
                pin_num=pin,
                callback=lambda p, pin=pin: relay_callback(p, pin, time_manager.get_datetime_str() if time_manager else None)
            )
            print(f"[RELAY] Configurado relé en pin {pin} ({RELAY_NAMES[pin]})")
        
        relay_manager.time_manager = time_manager
        return True
        
    except Exception as e:
        print(f"[RELAY] Error configurando relés: {e}")
        return False

def handle_critical_error(error_type, error_message, managers):
    """Maneja errores críticos del sistema con intentos de recuperación"""
    try:
        if "watchdog" not in managers:
            print("[ERROR] Watchdog no disponible")
            machine.reset()
            return False
            
        watchdog = managers["watchdog"]
        print(f"[MAIN] Error crítico: {error_type} - {error_message}")
        
        if error_type == "memory_critical":
            gc.collect()
            utime.sleep_ms(100)
            if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                try:
                    if "ble" in managers:
                        managers["ble"].close()
                        del managers["ble"]
                    gc.collect()
                    if gc.mem_free() >= MIN_MEMORY_THRESHOLD:
                        return True
                except:
                    pass
                watchdog.force_reset("low_memory")
                
        elif error_type == "wifi_critical":
            try:
                if "wifi" in managers and managers["wifi"].recover_from_disconnect():
                    print("[MAIN] Recuperación WiFi exitosa")
                    return True
            except:
                pass
            watchdog.force_reset("wifi_failure")
                
        elif error_type == "mqtt_critical":
            try:
                if "mqtt" in managers and managers["mqtt"].reconnect():
                    print("[MAIN] Recuperación MQTT exitosa")
                    return True
            except:
                pass
            watchdog.force_reset("mqtt_failure")
                
        elif error_type == "time_critical":
            try:
                if "time" in managers and managers["time"].check_sync():
                    print("[MAIN] Sincronización de tiempo exitosa")
                    return True
            except:
                pass
            watchdog.force_reset("time_sync_failure")
        
        return False
            
    except Exception as e:
        print(f"[MAIN] Error en manejador de errores: {e}")
        watchdog.force_reset("error_handler_failed")
        return False

def wifi_callback(managers, ssid, password):
    """Callback para configuración WiFi"""
    try:
        gc.collect()
        if gc.mem_free() < 15000:
            print(f"[MAIN] Memoria insuficiente: {gc.mem_free()} bytes")
            machine.reset()

        print(f"[MAIN] Intentando conexión WiFi a {ssid}")
        
        if not ssid or not password or len(password) < 8:
            print("[MAIN] Credenciales WiFi inválidas")
            return False
        
        # Inicializar WiFi solo cuando se necesita
        if not managers["wifi"]:
            print("[MAIN] Inicializando WiFiManager para conexión...")
            managers["wifi"] = WiFiManager()
            # Dar tiempo para que se inicialice
            utime.sleep_ms(1000)
        
        # Intentar conexión
        success = managers["wifi"].connect_wifi(ssid, password)
        
        if success:
            print("[MAIN] Conexión WiFi exitosa")
            if managers["config"].save_wifi_config(ssid, password):
                print("[MAIN] Configuración WiFi guardada")
                
                # Actualizar referencias
                if "mqtt" in managers:
                    managers["mqtt"].wifi_manager = managers["wifi"]
                if "time" in managers:
                    managers["time"].wifi_manager = managers["wifi"]
                    
                return True
                
            print("[MAIN] Error guardando configuración WiFi")
            return False
        
        print("[MAIN] Conexión WiFi fallida")
        return False
        
    except Exception as e:
        print(f"[MAIN] Error en callback WiFi: {e}")
        return False

def init_managers():
    """Inicializa los gestores básicos del sistema"""
    managers = {}
    
    try:
        print("\n[INIT] Iniciando sistema...")
        print(f"[INIT] Memoria libre inicial: {gc.mem_free()} bytes")
        
        # Limpieza inicial de memoria
        gc.collect()
        utime.sleep_ms(1000)
        
        # 1. Inicializar ConfigManager (es el más básico y necesario)
        print("[INIT] Iniciando ConfigManager...")
        managers["config"] = ConfigManager()
        utime.sleep_ms(100)
        
        # 2. Inicializar WatchdogManager
        print("[INIT] Iniciando WatchdogManager...")
        managers["watchdog"] = WatchdogManager()
        managers["watchdog"].feed()
        utime.sleep_ms(100)
        
        # 3. Inicializar WiFiManager si ya está configurado
        if managers["config"].is_configured():
            print("[INIT] Iniciando WiFiManager...")
            try:
                managers["wifi"] = WiFiManager()
                utime.sleep_ms(500)
            except Exception as e:
                print(f"[INIT] Error iniciando WiFiManager: {e}")
                managers["wifi"] = None
        else:
            managers["wifi"] = None
            
        # Alimentar watchdog
        managers["watchdog"].feed()
        
        # 4. Inicializar TimeManager (con o sin WiFi)
        print("[INIT] Iniciando TimeManager...")
        managers["time"] = TimeManager(managers["wifi"])
        utime.sleep_ms(100)
        
        # 5. Inicializar MQTTManager
        print("[INIT] Iniciando MQTTManager...")
        managers["mqtt"] = MQTTManager(managers["wifi"])
        if managers["mqtt"]:
            managers["config"].set_mqtt_manager(managers["mqtt"])
        utime.sleep_ms(100)
        
        # Verificar memoria después de inicializaciones básicas
        gc.collect()
        print(f"[INIT] Memoria después de inicializaciones básicas: {gc.mem_free()} bytes")
        
        # Verificar si necesitamos entrar en modo configuración
        current_mode = managers["config"].get_mode()
        if current_mode in [managers["config"].MODES['WIFI_CONFIG'], 
                          managers["config"].MODES['AWAITING_CONFIG']]:
            try:
                print("[INIT] Preparando modo configuración...")
                # Desactivar WiFi si existe para liberar recursos
                if managers["wifi"]:
                    try:
                        managers["wifi"].sta_if.active(False)
                        managers["wifi"] = None
                        gc.collect()
                        utime.sleep_ms(1000)
                    except:
                        pass
            except Exception as e:
                print(f"[INIT] Error preparando modo configuración: {e}")
        
        print("[INIT] Inicialización básica completada")
        return managers
        
    except Exception as e:
        print(f"[INIT] Error fatal en inicialización: {e}")
        # Intentar limpiar recursos si hay error
        if "watchdog" in managers:
            try:
                managers["watchdog"].force_reset("init_error")
            except:
                pass
        machine.reset()

def setup_wifi(managers):
    """Configura la conexión WiFi inicial"""
    try:
        if managers["config"].is_configured():
            print("[MAIN] Configuración encontrada...")
            
            # Verificar memoria antes de continuar
            if not check_memory():
                return SystemState.ERROR
            
            gc.collect()
            utime.sleep_ms(1000)
            
            # Asegurar que no hay una instancia activa de WiFi
            try:
                import network
                wlan = network.WLAN(network.STA_IF)
                wlan.active(False)
                utime.sleep_ms(1000)
                del wlan
            except:
                pass
            
            gc.collect()
            utime.sleep_ms(1000)
            
            # Crear nueva instancia de WiFiManager
            try:
                if "wifi" in managers:
                    del managers["wifi"]
                gc.collect()
                utime.sleep_ms(1000)
                managers["wifi"] = WiFiManager()
            except Exception as e:
                print(f"[MAIN] Error inicializando WiFiManager: {e}")
                return SystemState.CONFIG
                
            managers["time"].wifi_manager = managers["wifi"]
            managers["mqtt"].wifi_manager = managers["wifi"]
            
            wifi_config = managers["config"].get_wifi_config()
            
            # Dejar un tiempo para que el sistema se estabilice
            gc.collect()
            utime.sleep_ms(1000)
            
            # Intentar conexión con reintentos
            retry_count = 0
            while retry_count < WIFI_RETRY_COUNT:
                try:
                    if managers["wifi"].connect_wifi(wifi_config["ssid"], wifi_config["password"]):
                        current_mode = managers["config"].get_mode()
                        if current_mode == managers["config"].MODES['RUNNING']:
                            return SystemState.RUNNING
                        elif current_mode == managers["config"].MODES['AWAITING_CONFIG']:
                            return SystemState.CONFIG
                        else:
                            return SystemState.CONFIG
                except Exception as e:
                    print(f"[MAIN] Error en intento {retry_count + 1}: {e}")
                
                retry_count += 1
                if retry_count < WIFI_RETRY_COUNT:
                    print(f"[MAIN] Reintento WiFi {retry_count + 1}/{WIFI_RETRY_COUNT}")
                    gc.collect()
                    utime.sleep_ms(2000)
                    try:
                        managers["wifi"]._clean_wifi_state()
                    except:
                        pass
            
            print("[MAIN] Error conectando WiFi, iniciando BLE...")
            return SystemState.CONFIG
        
        return SystemState.CONFIG
        
    except Exception as e:
        print(f"[MAIN] Error en setup_wifi: {e}")
        return SystemState.CONFIG

def try_wifi_connection(wifi_manager, ssid, password, max_attempts=2, timeout_per_attempt=15):
    """Intenta conectar al WiFi con reintentos limitados"""
    print(f"[WIFI] Intentando conexión a {ssid}")
    
    wifi_manager.SSID = ssid
    wifi_manager.PASSWORD = password
    
    for attempt in range(max_attempts):
        try:
            if wifi_manager.connect_wifi():
                print("[WIFI] Conexión exitosa")
                return True
        except Exception as e:
            print(f"[WIFI] Error en intento {attempt + 1}: {e}")
        
        if attempt < max_attempts - 1:
            utime.sleep_ms(2000)
    
    return False

def setup_bluetooth(managers):
    """Configura y maneja el modo BLE"""
    print("[CONFIG] Iniciando modo configuración BLE")
    print("[CONFIG] Formato: ssid:nombre_red,password:contraseña")
    print("[CONFIG] Esperando conexión bluetooth...")
    
    bluetooth_manager = None
    
    try:
        # Desactivar WiFi si existe para liberar recursos
        if managers.get("wifi"):
            try:
                managers["wifi"].sta_if.active(False)
                managers["wifi"] = None
                gc.collect()
                utime.sleep_ms(1000)
            except:
                pass
        
        # Inicializar BLE
        bluetooth_manager = BluetoothManager()
        
        while True:
            try:
                # Alimentar watchdog
                managers["watchdog"].feed()
                
                # Verificar datos bluetooth
                data = bluetooth_manager.wait_for_data()
                if data and 'ssid' in data and 'password' in data:
                    print("[CONFIG] Datos recibidos, verificando...")
                    
                    ssid = data['ssid'].strip()
                    password = data['password'].strip()
                    
                    if not ssid or not password or len(password) < 8:
                        bluetooth_manager.write_data("error:credenciales_invalidas")
                        continue
                    
                    # Inicializar nuevo WiFiManager
                    print("[CONFIG] Iniciando WiFiManager...")
                    managers["wifi"] = WiFiManager()
                    
                    # Intentar conexión WiFi
                    if try_wifi_connection(managers["wifi"], ssid, password):
                        # Guardar configuración
                        config = {
                            'wifi_ssid': ssid,
                            'wifi_password': password
                        }
                        
                        try:
                            managers["config"].save_wifi_config(ssid, password)
                            print("[CONFIG] Configuración guardada")
                            bluetooth_manager.write_data("status:success")
                            utime.sleep_ms(1000)  # Dar tiempo para enviar respuesta
                            
                            # Cerrar BLE antes de reiniciar
                            bluetooth_manager.close()
                            gc.collect()
                            
                            print("[CONFIG] Reiniciando sistema...")
                            machine.reset()
                            
                        except Exception as e:
                            print(f"[CONFIG] Error guardando configuración: {e}")
                            bluetooth_manager.write_data("error:save_failed")
                    else:
                        print("[CONFIG] Conexión WiFi fallida")
                        bluetooth_manager.write_data("error:wifi_failed")
                
                utime.sleep_ms(100)
                gc.collect()
                
            except Exception as e:
                print(f"[CONFIG] Error en bucle BLE: {e}")
                managers["watchdog"].feed()
                continue
                
    except Exception as e:
        print(f"[CONFIG] Error en setup_bluetooth: {e}")
        return SystemState.ERROR
    finally:
        if bluetooth_manager:
            try:
                bluetooth_manager.close()
            except:
                pass
        gc.collect()

def cleanup_bluetooth(managers):
    """Limpia los recursos del BLE de manera segura"""
    try:
        print("[MAIN] Panel configurado y en modo RUNNING, cerrando BLE...")
        managers["watchdog"].feed()
        
        if "ble" in managers:
            try:
                managers["ble"].close()
            except Exception as e:
                print(f"[MAIN] Error cerrando BLE: {e}")
            finally:
                del managers["ble"]
                
        gc.collect()
        utime.sleep_ms(1000)
        
    except Exception as e:
        print(f"[MAIN] Error en cleanup_bluetooth: {e}")

def setup_running_mode(managers):
    """Configura el modo RUNNING del sistema"""
    try:
        managers["watchdog"].feed()
        
        # Inicializar ESP32IdManager
        print("[MAIN] Inicializando ESP32IdManager...")
        managers["esp32_id"] = ESP32IdManager(managers["mqtt"], managers["time"])
        
        esp32_id = managers["esp32_id"].get_id()
        if not esp32_id:
            esp32_id = managers["esp32_id"]._generate_unique_id()
            managers["esp32_id"]._save_with_backup()
        
        managers["config"].set_esp32_id(esp32_id)
        
        # Verificar memoria
        if not check_memory():
            return False
        
        # Inicializar RelayManager
        print("[MAIN] Inicializando RelayManager...")
        managers["relay"] = RelayManager()
        
        # Configurar MQTT
        if not setup_mqtt_connection(managers, esp32_id):
            return False
        
        # Configurar relays
        if setup_relays(managers["relay"], managers["mqtt"], esp32_id, managers["time"]):
            managers["_relays_configured"] = True
            managers["config"].relay_manager = managers["relay"]
            managers["config"].enter_running_mode()
            return True
            
        return False
        
    except Exception as e:
        print(f"[MAIN] Error en setup_running_mode: {e}")
        return False

def setup_mqtt_connection(managers, esp32_id):
    """Configura la conexión MQTT"""
    try:
        mqtt_connected = False
        retry_count = 0
        
        while retry_count < MQTT_RETRY_COUNT:
            managers["watchdog"].feed()
            print(f"[MAIN] Intento MQTT {retry_count + 1}/{MQTT_RETRY_COUNT}")
            
            if managers["mqtt"].connect():
                managers["config"].set_mqtt_manager(managers["mqtt"])
                mqtt_connected = True
                break
            
            retry_count += 1
            if retry_count < MQTT_RETRY_COUNT:
                gc.collect()
                utime.sleep_ms(2000)
        
        if not mqtt_connected:
            print("[MAIN] No se pudo conectar a MQTT después de reintentos")
            return False
        
        # Configurar suscripciones
        def config_callback(topic, msg):
            try:
                print(f"[MQTT] Mensaje recibido en {topic}: {msg}")
                managers["config"].handle_config_message(topic, msg)
            except Exception as e:
                print(f"[MQTT] Error en callback: {e}")
        
        config_topic = f"esp32/config/{esp32_id}"
        if not setup_mqtt_subscription(managers, config_topic, config_callback):
            return False
        
        # Publicar información de red
        if not publish_network_info(managers, esp32_id):
            return False
        
        print("[MAIN] Transición inicial a AWAITING_CONFIG...")
        managers["config"].current_mode = managers["config"].MODES['AWAITING_CONFIG']
        managers["watchdog"].feed()
        
        return True
        
    except Exception as e:
        print(f"[MAIN] Error en setup_mqtt_connection: {e}")
        return False

def setup_mqtt_subscription(managers, config_topic, callback):
    """Configura las suscripciones MQTT"""
    try:
        if not managers["mqtt"].subscribe(config_topic, callback):
            print("[MAIN] Error en suscripción, reintentando...")
            managers["watchdog"].feed()
            utime.sleep_ms(1000)
            if not managers["mqtt"].subscribe(config_topic, callback):
                return False
        return True
    except Exception as e:
        print(f"[MAIN] Error en setup_mqtt_subscription: {e}")
        return False

def publish_network_info(managers, esp32_id):
    """Publica la información de red inicial"""
    try:
        network_info = {
            "esp32_id": esp32_id,
            "MAC": managers["esp32_id"].get_mac(),
            "IP": managers["wifi"].current_ip,
            "status": "AWAITING_CONFIG",
            "lastUpdate": utime.ticks_ms()
        }
        
        return managers["mqtt"].publish_event(
            "esp32/network_info",
            network_info,
            retain=False,
            qos=1
        )
    except Exception as e:
        print(f"[MAIN] Error publicando network_info: {e}")
        return False

def main_loop(managers, current_state):
    """Bucle principal del sistema"""
    try:
        last_gc_time = utime.ticks_ms()
        last_time_check = utime.ticks_ms()
        
        while True:
            try:
                loop_start = utime.ticks_ms()
                managers["watchdog"].feed()
                
                # Limpieza periódica de memoria
                if utime.ticks_diff(loop_start, last_gc_time) >= 300000:
                    gc.collect()
                    last_gc_time = loop_start
                
                # Procesar estado actual
                current_state = process_system_state(managers, current_state, loop_start, last_time_check)
                
                # Control de tiempo de bucle
                loop_time = utime.ticks_diff(utime.ticks_ms(), loop_start)
                if loop_time < MAX_LOOP_TIME:
                    utime.sleep_ms(MAX_LOOP_TIME - loop_time)
                    
            except Exception as e:
                print(f"[MAIN] Error en bucle principal: {e}")
                handle_critical_error("main_loop_error", str(e), managers)
                
    except Exception as e:
        print(f"[MAIN] Error fatal en main_loop: {e}")
        raise

def process_system_state(managers, current_state, loop_start, last_time_check):
    """Procesa el estado actual del sistema"""
    try:
        if current_state == SystemState.RUNNING:
            return process_running_state(managers, loop_start, last_time_check)
        elif current_state == SystemState.CONFIG:
            return process_config_state(managers)
        elif current_state == SystemState.ERROR:
            return process_error_state(managers)
        return current_state
    except Exception as e:
        print(f"[MAIN] Error en process_system_state: {e}")
        return SystemState.ERROR

def process_running_state(managers, loop_start, last_time_check):
    """Procesa el estado RUNNING"""
    try:
        # Verificar memoria
        if gc.mem_free() < MIN_MEMORY_THRESHOLD:
            handle_critical_error("memory_critical", "Memoria bajo nivel crítico", managers)
        
        # Verificar conexiones si el tiempo está sincronizado
        if managers["time"].is_synced:
            # Verificar WiFi
            if not managers["wifi"].check_connection():
                print("[MAIN] Conexión WiFi perdida en modo RUNNING, volviendo a CONFIG")
                return SystemState.CONFIG
            
            # Verificar MQTT
            if not managers["mqtt"].check_connection():
                handle_critical_error("mqtt_critical", "Pérdida de conexión MQTT", managers)
        
        # Verificar sincronización de tiempo
        if utime.ticks_diff(loop_start, last_time_check) >= 60000:
            if not managers["time"].check_sync():
                print("[MAIN] Reintentando sincronización horaria...")
            last_time_check = loop_start
        
        # Procesar mensajes MQTT
        if managers["mqtt"].client:
            managers["mqtt"].check_msg()
            managers["mqtt"].check_status_report()
        
        return SystemState.RUNNING
        
    except Exception as e:
        print(f"[MAIN] Error en process_running_state: {e}")
        return SystemState.ERROR

def process_config_state(managers):
    """Procesa el estado CONFIG"""
    try:
        mode = managers["config"].get_mode()
        if managers["wifi"].check_connection() and mode == managers["config"].MODES['RUNNING']:
            print("[MAIN] Transición a modo RUNNING detectada")
            return SystemState.RUNNING
        return SystemState.CONFIG
    except Exception as e:
        print(f"[MAIN] Error en process_config_state: {e}")
        return SystemState.ERROR

def process_error_state(managers):
    """Procesa el estado ERROR"""
    try:
        print("[MAIN] Intentando recuperar de error...")
        if managers["wifi"].check_connection() and managers["mqtt"].check_connection():
            return SystemState.RUNNING
        managers["watchdog"].force_reset("recovery_failed")
        return SystemState.ERROR
    except Exception as e:
        print(f"[MAIN] Error en process_error_state: {e}")
        return SystemState.ERROR

def cleanup_system(managers):
    """Limpia los recursos del sistema"""
    try:
        if managers:
            print("[MAIN] Limpiando recursos del sistema...")
            
            # Cerrar BLE primero si existe
            if "ble" in managers:
                try:
                    managers["ble"].close()
                except:
                    pass
            
            # Cerrar MQTT
            if "mqtt" in managers:
                try:
                    managers["mqtt"].close()
                except:
                    pass
            
            # Desconectar WiFi
            if "wifi" in managers:
                try:
                    managers["wifi"].disconnect()
                except:
                    pass
            
            # Limpieza final
            gc.collect()
            print("[MAIN] Limpieza completada")
            
    except Exception as e:
        print(f"[MAIN] Error en cleanup_system: {e}")

def main():
    """Función principal del sistema"""
    managers = {}
    current_state = SystemState.INITIAL
    
    try:
        # 1. Inicialización básica de managers
        managers = init_managers()
        if not managers:
            raise Exception("Error en inicialización básica")
            
        managers["watchdog"].feed()
        
        # 2. Configuración WiFi inicial
        gc.collect()
        current_state = setup_wifi(managers)
        
        # 3. Configuración BLE si es necesaria
        if current_state == SystemState.CONFIG:
            print("[MAIN] Iniciando modo configuración BLE")
            # Limpiar memoria antes de BLE
            gc.collect()
            utime.sleep_ms(1000)
            
            try:
                current_state = setup_bluetooth(managers)
            except Exception as e:
                print(f"[MAIN] Error en setup bluetooth: {e}")
                # Si falla BLE, intentar recuperar modo WiFi
                if managers["wifi"] and managers["wifi"].check_connection():
                    current_state = SystemState.RUNNING
                else:
                    current_state = SystemState.ERROR
        
        # 4. Configuración modo RUNNING
        if current_state == SystemState.RUNNING:
            print("[MAIN] Iniciando modo RUNNING")
            gc.collect()
            if setup_running_mode(managers):
                # Verificar memoria antes de entrar al loop principal
                gc.collect()
                if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                    raise Exception("Memoria insuficiente para modo RUNNING")
                    
                print("[MAIN] Entrando en loop principal")
                main_loop(managers, current_state)
            else:
                current_state = SystemState.ERROR
        
        # 5. Manejo de estado de ERROR
        if current_state == SystemState.ERROR:
            print("[MAIN] Entrando en modo ERROR")
            handle_critical_error("initialization_error", 
                                "Error en inicialización del sistema", 
                                managers)
    
    except KeyboardInterrupt:
        print("\n[MAIN] Programa interrumpido por usuario")
    except Exception as e:
        print(f"\n[MAIN] Error fatal: {e}")
        if "watchdog" in managers:
            managers["watchdog"].force_reset("fatal_error")
    finally:
        cleanup_system(managers)
        machine.reset()

if __name__ == "__main__":
    main()