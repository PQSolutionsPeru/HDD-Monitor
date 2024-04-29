from machine import Pin
import time

class RelayManager:
    def __init__(self):
        self.relays = {}
        self.relay_states = {}
        self.last_trigger_time = {}

    def setup_relay(self, pin_num, callback):
        print(f"Debug: Configurando pin {pin_num}")
        pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
        self.relays[pin_num] = pin
        self.relay_states[pin_num] = pin.value()
        print(f"Debug: Estado inicial del pin {pin_num}: {pin.value()}")
        pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=lambda p: self.debounce(pin_num, p, callback))
        return pin

    def debounce(self, pin_num, pin, callback):
        print(f"Debug: Debounce activado para pin {pin_num}")
        current_time = time.ticks_ms()
        if current_time - self.last_trigger_time.get(pin_num, 0) > 300 and self.relay_states[pin_num] != pin.value():
            self.relay_states[pin_num] = pin.value()
            print(f"Debug: Cambio detectado en pin {pin_num}, nuevo estado {pin.value()}")
            callback(pin, pin_num)
            self.last_trigger_time[pin_num] = current_time

