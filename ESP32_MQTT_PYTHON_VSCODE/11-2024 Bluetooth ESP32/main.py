--- Contenido de main.py ---

import machine
import utime
import gc
import os
from micropython import const
from wifi_manager import WiFiManager
from mqtt_manager import MQTTManager
from relay_manager import RelayManager
from watchdog_manager import WatchdogManager
from esp32_id_manager import ESP32IdManager
from bluetooth_manager import BluetoothManager
from config_manager import ConfigManager

# Configuración
RELAY_PINS = [32, 33, 25]
RELAY_NAMES = {32: "Alarma", 33: "Problema", 25: "Supervision"}

# Constantes
MAX_LOOP_TIME = const(500)  # ms
MIN_MEMORY_THRESHOLD = const(10000)  # bytes

# Sistema de estados
class SystemState:
    INITIAL = "initial"
    BLE_WIFI_CONFIG = "ble_wifi_config"
    MQTT_CONNECTING = "mqtt_connecting"
    WAITING_PANEL_CONFIG = "waiting_panel_config"
    RUNNING = "running"
    ERROR = "error"

def initialize_managers():
    """Inicializa todos los gestores necesarios"""
    try:
        print("\n[INIT] Iniciando sistema...")
        
        # 1. Inicializar ID del ESP32
        esp32_id_manager = ESP32IdManager()
        esp32_id = esp32_id_manager.get_id()
        print(f"[INIT] ESP32 ID: {esp32_id}")
        
        # 2. Inicializar gestores básicos
        wifi_manager = WiFiManager()
        watchdog = WatchdogManager(timeout=120000)
        
        # 3. Inicializar gestores dependientes
        mqtt_manager = MQTTManager(wifi_manager)
        config_manager = ConfigManager(mqtt_manager)
        bluetooth_manager = BluetoothManager(esp32_id)
        relay_manager = RelayManager()
        
        # 4. Configurar ESP32 ID en los gestores
        config_manager.set_esp32_id(esp32_id)
        
        print("[INIT] Managers inicializados correctamente")
        return (esp32_id_manager, wifi_manager, mqtt_manager, relay_manager,
                watchdog, bluetooth_manager, config_manager)
    except Exception as e:
        print(f"[INIT] Error inicializando managers: {e}")
        raise

def setup_relays(relay_manager):
    """Configura los relés y sus callbacks"""
    try:
        print("\n[RELAY] Configurando relés...")
        for pin in RELAY_PINS:
            def callback(p, pin_num):
                state = "DISC" if p.value() else "OK"
                print(f"[RELAY] Cambio en relé {pin_num}: {state}")
                
            relay_manager.setup_relay(pin_num=pin, callback=callback)
            print(f"[RELAY] Configurado relé en pin {pin}")
    except Exception as e:
        print(f"[RELAY] Error configurando relés: {e}")
        raise

def start_normal_operation(managers):
    """Inicia la operación normal del dispositivo"""
    try:
        print("\n[MAIN] Iniciando operación normal...")
        (esp32_id_manager, wifi_manager, mqtt_manager, relay_manager, 
         _, bluetooth_manager, config_manager) = managers
        
        # 1. Configurar relés
        setup_relays(relay_manager)
        utime.sleep_ms(1000)
        
        # 2. Cerrar BLE si está activo
        if bluetooth_manager:
            bluetooth_manager.close()
            gc.collect()
        
        # 3. Conectar a MQTT
        if not mqtt_manager.reinitialize_client():
            raise Exception("No se pudo conectar a MQTT")
        
        # 4. Obtener y suscribirse al tópico de operación
        operation_topic = config_manager.get_operation_topic()
        if not operation_topic:
            raise Exception("No se pudo obtener tópico de operación")
            
        mqtt_manager.subscribe(operation_topic)
        
        # 5. Publicar estado inicial
        mqtt_manager.publish_event(
            operation_topic,
            {
                "status": "online",
                "esp32_id": esp32_id_manager.get_id(),
                "relays": {name: "DISC" for name in RELAY_NAMES.values()},
                "timestamp": utime.ticks_ms()
            },
            retain=True
        )
        
        print("[MAIN] Operación normal iniciada correctamente")
        return True
        
    except Exception as e:
        print(f"[MAIN] Error iniciando operación normal: {e}")
        return False

def handle_wifi_config(ssid, password, wifi_manager, config_manager):
    """Maneja la configuración WiFi"""
    if wifi_manager.connect_wifi(ssid, password):
        print("[MAIN] Conexión WiFi exitosa")
        return config_manager.save_wifi_config(ssid, password)
    return False

def main():
    current_state = SystemState.INITIAL
    managers = None
    
    try:
        # Inicializar todos los gestores
        managers = initialize_managers()
        (esp32_id_manager, wifi_manager, mqtt_manager, relay_manager,
         watchdog, bluetooth_manager, config_manager) = managers

        esp32_id = esp32_id_manager.get_id()

        # Verificar si ya está configurado
        if config_manager.is_configured():
            print("[MAIN] Configuración encontrada, intentando conectar a WiFi...")
            wifi_config = config_manager.get_wifi_config()
            
            if wifi_manager.connect_wifi(wifi_config["ssid"], wifi_config["password"]):
                print("[MAIN] Conexión WiFi exitosa, iniciando operación normal...")
                if start_normal_operation(managers):
                    current_state = SystemState.RUNNING
            else:
                print("[MAIN] Error conectando a WiFi, entrando en modo configuración...")
                current_state = SystemState.BLE_WIFI_CONFIG
        else:
            print("[MAIN] No hay configuración, entrando en modo configuración...")
            current_state = SystemState.BLE_WIFI_CONFIG

        # Configurar callback de WiFi si es necesario
        if current_state == SystemState.BLE_WIFI_CONFIG:
            print("\n[MAIN] Iniciando configuración WiFi vía BLE...")
            bluetooth_manager.set_wifi_callback(
                lambda ssid, pwd: handle_wifi_config(ssid, pwd, wifi_manager, config_manager)
            )

        # Bucle principal
        while True:
            try:
                watchdog.feed()
                
                if current_state == SystemState.RUNNING:
                    # Verificar memoria
                    if gc.mem_free() < MIN_MEMORY_THRESHOLD:
                        gc.collect()
                        utime.sleep_ms(100)
                    
                    # Procesar MQTT
                    if mqtt_manager.check_connection():
                        mqtt_manager.process_messages()
                        mqtt_manager.process_queue()
                    else:
                        print("[MAIN] Reconectando MQTT...")
                        mqtt_manager.reinitialize_client()
                
                elif current_state == SystemState.BLE_WIFI_CONFIG:
                    bluetooth_manager.process()
                    
                elif current_state == SystemState.MQTT_CONNECTING:
                    if mqtt_manager.check_connection():
                        current_state = SystemState.WAITING_PANEL_CONFIG
                        config_manager.start_config_mode()
                    else:
                        print("[MAIN] Error conectando a MQTT")
                        current_state = SystemState.ERROR
                
                elif current_state == SystemState.WAITING_PANEL_CONFIG:
                    if mqtt_manager.check_connection():
                        mqtt_manager.process_messages()
                    else:
                        print("[MAIN] Perdida conexión MQTT durante configuración")
                        current_state = SystemState.ERROR
                
                elif current_state == SystemState.ERROR:
                    print("[MAIN] Error crítico, reiniciando...")
                    machine.reset()
                
                utime.sleep_ms(100)
                
            except Exception as e:
                print(f"[MAIN] Error en bucle principal: {e}")
                current_state = SystemState.ERROR
                
    except KeyboardInterrupt:
        print("\n[MAIN] Programa interrumpido por usuario")
    except Exception as e:
        print(f"\n[MAIN] Error fatal: {e}")
    finally:
        if managers:
            try:
                bluetooth_manager.close()
                mqtt_manager.close()
                wifi_manager.disconnect()
            except:
                pass
        machine.reset()

if __name__ == "__main__":
    main()
