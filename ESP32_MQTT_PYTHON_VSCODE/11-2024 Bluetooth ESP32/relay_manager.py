from machine import Pin
import time

class RelayManager:
    def __init__(self):
        self.relays = {}
        self.relay_states = {}
        self.last_trigger_time = {}

    def setup_relay(self, pin_num, callback):
        pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
        self.relays[pin_num] = pin
        self.relay_states[pin_num] = pin.value()
        pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=lambda p: self.debounce(pin_num, p, callback))
        return pin

    def debounce(self, pin_num, pin, callback):
        """Maneja cambios en los relays con debounce"""
        current_time = utime.ticks_ms()
        if (utime.ticks_diff(current_time, self.last_trigger_time.get(pin_num, 0)) > 300 and 
            self.relay_states[pin_num] != pin.value()):
            
            self.relay_states[pin_num] = pin.value()  # Actualizar estado
            self.last_trigger_time[pin_num] = current_time
            
            # Obtener hora actual en GMT-5 si hay time_manager disponible
            current_datetime = None
            if hasattr(self, 'time_manager') and self.time_manager:
                current_datetime = self.time_manager.get_datetime_str()
                
            callback(pin, pin_num, current_datetime)
