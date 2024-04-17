import machine

uart = machine.UART(0)  # Puerto UART predeterminado (0)
baudrate = uart.baudrate()

print("Velocidad de baudios actual:", baudrate)
