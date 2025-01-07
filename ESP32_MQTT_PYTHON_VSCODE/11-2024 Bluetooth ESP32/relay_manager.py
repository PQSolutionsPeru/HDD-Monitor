from machine import Pin
import time

class RelayManager:
    def __init__(self):
        self.relays = {}
        self.relay_states = {}
        self.relay_callbacks = {}
        self.last_trigger_time = {}

    def setup_relay(self, pin_num, callback):
        """Configura un relay y reporta su estado inicial"""
        pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
        self.relays[pin_num] = pin
        self.relay_callbacks[pin_num] = callback
        
        # Leer estado inicial
        initial_state = pin.value()
        self.relay_states[pin_num] = initial_state
        
        # Notificar estado inicial
        if callback:
            callback(pin, pin_num)
            
        # Configurar interrupción
        pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, 
                handler=lambda p: self.debounce(pin_num, p, callback))
                
        return pin

    def debounce(self, pin_num, pin, callback):
        """Maneja cambios de estado con debounce"""
        try:
            current_time = time.ticks_ms()
            if (time.ticks_diff(current_time, self.last_trigger_time.get(pin_num, 0)) > 300 and 
                self.relay_states[pin_num] != pin.value()):
                
                self.relay_states[pin_num] = pin.value()
                if callback:
                    callback(pin, pin_num)
                self.last_trigger_time[pin_num] = current_time
                
        except Exception as e:
            print(f"[RELAY] Error en debounce: {e}")
            
    def get_relay_state(self, pin_num):
        """Obtiene el estado actual de un relay"""
        if pin_num in self.relays:
            return self.relays[pin_num].value()
        return None

    def check_all_states(self):
        """Verifica el estado de todos los relays"""
        for pin_num, pin in self.relays.items():
            current_state = pin.value()
            if current_state != self.relay_states.get(pin_num):
                self.relay_states[pin_num] = current_state
                if self.relay_callbacks.get(pin_num):
                    self.relay_callbacks[pin_num](pin, pin_num)