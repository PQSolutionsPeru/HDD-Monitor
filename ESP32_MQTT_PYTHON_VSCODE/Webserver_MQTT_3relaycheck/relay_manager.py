from machine import Pin

class RelayManager:
    def __init__(self):
        self.relays = {}

    def setup_relay(self, pin_num, callback):
        pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
        pin.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=lambda p: callback(p, pin_num))
        self.relays[pin_num] = pin
        return pin
