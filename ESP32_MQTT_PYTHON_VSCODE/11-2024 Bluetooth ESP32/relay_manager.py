from machine import Pin
import time
import gc

class RelayManager:
    def __init__(self):
        """Inicializa el gestor de relés con protección contra rebotes mejorada"""
        self.relays = {}
        self.relay_states = {}
        self.last_trigger_time = {}
        self.callbacks = {}
        self.DEBOUNCE_MS = 300
        self.last_gc_time = time.ticks_ms()
        self.GC_INTERVAL = 300000  # 5 minutos
        print("[RELAY] Gestor de relés iniciado")

    def setup_relay(self, pin_num, callback):
        """
        Configura un relé con protección contra ruido mejorada
        Args:
            pin_num: Número de pin
            callback: Función a llamar cuando cambie el estado
        """
        try:
            # Configurar pin con pull-up y protección contra ruido
            pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
            self.relays[pin_num] = pin
            self.relay_states[pin_num] = pin.value()
            self.callbacks[pin_num] = callback
            
            # Configurar interrupción con manejo de errores
            pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING,
                   handler=lambda p: self._handle_interrupt(pin_num, p))
            
            print(f"[RELAY] Configurado relé en pin {pin_num}")
            return True
            
        except Exception as e:
            print(f"[RELAY] Error configurando relé {pin_num}: {e}")
            return False

    def _handle_interrupt(self, pin_num, pin):
        """
        Maneja interrupciones con protección adicional
        Args:
            pin_num: Número de pin
            pin: Objeto Pin
        """
        try:
            current_time = time.ticks_ms()
            
            # Verificar GC periódico
            if time.ticks_diff(current_time, self.last_gc_time) >= self.GC_INTERVAL:
                gc.collect()
                self.last_gc_time = current_time
            
            # Validar debounce
            if time.ticks_diff(current_time, self.last_trigger_time.get(pin_num, 0)) > self.DEBOUNCE_MS:
                # Leer valor varias veces para confirmar
                value = pin.value()
                time.sleep_ms(1)
                if value == pin.value():  # Doble verificación
                    if self.relay_states[pin_num] != value:
                        self.relay_states[pin_num] = value
                        if pin_num in self.callbacks:
                            try:
                                self.callbacks[pin_num](pin, pin_num)
                            except Exception as callback_error:
                                print(f"[RELAY] Error en callback {pin_num}: {callback_error}")
                        self.last_trigger_time[pin_num] = current_time
                        
        except Exception as e:
            print(f"[RELAY] Error en interrupción {pin_num}: {e}")

    def get_state(self, pin_num):
        """
        Obtiene el estado actual de un relé
        Args:
            pin_num: Número de pin
        Returns:
            int: Estado del relé o None si hay error
        """
        try:
            return self.relay_states.get(pin_num)
        except Exception as e:
            print(f"[RELAY] Error obteniendo estado {pin_num}: {e}")
            return None

    def get_all_states(self):
        """
        Obtiene el estado de todos los relés
        Returns:
            dict: Estados de todos los relés
        """
        try:
            return {pin: self.relay_states[pin] for pin in self.relays}
        except Exception as e:
            print(f"[RELAY] Error obteniendo estados: {e}")
            return {}