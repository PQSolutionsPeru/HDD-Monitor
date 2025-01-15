from machine import Pin
import time
import utime
from config.relay_config import RelayConfig

class RelayManager:
    def __init__(self):
        """Inicializa el gestor de relays con configuración"""
        self.config = RelayConfig()
        self.relays = {}
        self.relay_states = {}
        self.relay_callbacks = {}
        self.last_trigger_time = {}
        print("[RELAY] Gestor iniciado con configuración cargada")

    def setup_relay(self, pin_num, callback):
        """Configura un relay con manejo de errores mejorado"""
        try:
            pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
            self.relays[pin_num] = pin
            self.relay_callbacks[pin_num] = callback
            
            def safe_callback(p):
                try:
                    self.debounce(pin_num, p, callback)
                except Exception as e:
                    print(f"[RELAY] Error crítico en callback del pin {pin_num}: {e}")
                    # Reiniciar interrupción
                    try:
                        p.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=None)
                        utime.sleep_ms(100)
                        p.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=safe_callback)
                        print(f"[RELAY] Interrupción reiniciada para pin {pin_num}")
                    except Exception as e:
                        print(f"[RELAY] Error fatal reiniciando interrupción: {e}")
            
            # Configurar interrupción inicial
            pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, 
                   handler=safe_callback)
            
            # Leer y notificar estado inicial
            initial_state = pin.value()
            self.relay_states[pin_num] = initial_state
            if callback:
                callback(pin, pin_num)
                
            print(f"[RELAY] Pin {pin_num} configurado exitosamente")
            return pin
            
        except Exception as e:
            print(f"[RELAY] Error crítico configurando pin {pin_num}: {e}")
            return None

    def debounce(self, pin_num, pin, callback):
        """Maneja cambios de estado con debounce mejorado"""
        try:
            current_time = time.ticks_ms()
            debounce_time = self.config.get_debounce_time()
            min_report_interval = self.config.get_min_report_interval()
            
            # Verificar tiempo mínimo entre reportes
            if time.ticks_diff(current_time, self.last_trigger_time.get(pin_num, 0)) <= min_report_interval:
                return
            
            # Verificar si el estado realmente cambió después del debounce
            if (time.ticks_diff(current_time, self.last_trigger_time.get(pin_num, 0)) > debounce_time and 
                self.relay_states[pin_num] != pin.value()):
                
                new_state = pin.value()
                self.relay_states[pin_num] = new_state
                self.last_trigger_time[pin_num] = current_time
                
                if callback:
                    try:
                        callback(pin, pin_num)
                    except Exception as e:
                        print(f"[RELAY] Error en callback del pin {pin_num}: {e}")
                        raise  # Propagar error para reiniciar interrupción
                
        except Exception as e:
            print(f"[RELAY] Error en debounce del pin {pin_num}: {e}")
            raise  # Propagar error para reiniciar interrupción

    def get_relay_state(self, pin_num):
        """Obtiene el estado actual de un relay"""
        try:
            if pin_num in self.relays:
                return self.relays[pin_num].value()
            return None
        except Exception as e:
            print(f"[RELAY] Error obteniendo estado del pin {pin_num}: {e}")
            return None

    def get_all_states(self):
        """Obtiene el estado de todos los relays"""
        states = {}
        for pin_num in self.relays:
            states[pin_num] = self.get_relay_state(pin_num)
        return states

    def cleanup(self):
        """Limpia recursos y desactiva interrupciones"""
        try:
            for pin_num, pin in self.relays.items():
                try:
                    # Desactivar interrupciones
                    pin.irq(trigger=0, handler=None)
                except:
                    pass
            
            # Limpiar diccionarios
            self.relays.clear()
            self.relay_states.clear()
            self.relay_callbacks.clear()
            self.last_trigger_time.clear()
            
            print("[RELAY] Limpieza completada")
            return True
        except Exception as e:
            print(f"[RELAY] Error en limpieza: {e}")
            return False