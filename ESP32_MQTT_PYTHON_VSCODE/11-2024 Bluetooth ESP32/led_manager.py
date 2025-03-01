from machine import Pin, Timer
import utime

class LEDManager:
    def __init__(self):
        """Inicializa el gestor de LEDs para los estados del sistema"""
        # Definir pines para LEDs
        self.LED_GREEN = 22  # Estado RUNNING (monitoreo normal)
        self.LED_BLUE = 23   # Estado CONFIG (configuración)
        self.LED_RED = 21    # Estado ERROR
        
        # Inicializar pines como salidas
        self.led_green = Pin(self.LED_GREEN, Pin.OUT)
        self.led_blue = Pin(self.LED_BLUE, Pin.OUT)
        self.led_red = Pin(self.LED_RED, Pin.OUT)
        
        # Estado actual
        self.current_mode = None
        
        # Control de la secuencia de inicio
        self.running_startup_sequence = False
        self.sequence_timer = None
        self.transition_step = 0
        self.color_index = 0  # 0: rojo->verde, 1: verde->azul, 2: azul->rojo
        
        # Inicialmente apagar todos los LEDs de forma explícita
        self.led_green.value(0)
        self.led_blue.value(0)
        self.led_red.value(0)
        
        # Iniciar la secuencia de inicio mediante Timer (no bloqueante)
        print("[LED] Iniciando secuencia de inicio")
        self.start_sequence_timer()
        
        print("[LED] Gestor de LEDs iniciado correctamente")
    
    def all_off(self):
        """Apaga todos los LEDs de forma segura"""
        # Apagado explícito de cada LED
        self.led_green.value(0)
        self.led_blue.value(0)
        self.led_red.value(0)
        
        # Pequeña pausa para asegurar que los cambios surtan efecto
        utime.sleep_ms(5)
    
    def set_config_mode(self):
        """Configura LEDs para modo de configuración (solo azul encendido)"""
        # Detener la secuencia de inicio si está corriendo
        if self.running_startup_sequence:
            self.stop_sequence_timer()
        
        # Incluso si el modo es el mismo, forzamos la actualización
        # para garantizar el estado correcto de los LEDs
        self.all_off()
        self.led_blue.value(1)
        self.current_mode = "config"
        print("[LED] Modo configuración - LED azul encendido")
    
    def set_running_mode(self):
        """Configura LEDs para modo de operación normal (solo verde encendido)"""
        # Detener la secuencia de inicio si está corriendo
        if self.running_startup_sequence:
            self.stop_sequence_timer()
        
        # Forzar estado correcto de LEDs
        self.all_off()
        self.led_green.value(1)
        self.current_mode = "running"
        print("[LED] Modo operación normal - LED verde encendido")
    
    def set_error_mode(self):
        """Configura LEDs para modo de error (solo rojo encendido)"""
        # Detener la secuencia de inicio si está corriendo
        if self.running_startup_sequence:
            self.stop_sequence_timer()
        
        # Forzar estado correcto de LEDs
        self.all_off()
        self.led_red.value(1)
        self.current_mode = "error"
        print("[LED] Modo error - LED rojo encendido")
    
    def get_current_mode(self):
        """Obtiene el modo actual"""
        return self.current_mode
    
    def start_sequence_timer(self):
        """Inicia el timer para la secuencia de inicio"""
        # Asegurar que los LEDs estén apagados al inicio
        self.all_off()
        
        # Usar un timer para ejecutar la secuencia sin bloquear
        self.sequence_timer = Timer(-1)
        self.sequence_timer.init(period=10, mode=Timer.PERIODIC, callback=self.sequence_step)
        self.running_startup_sequence = True
        
    def stop_sequence_timer(self):
        """Detiene el timer de la secuencia de inicio"""
        if self.sequence_timer:
            self.sequence_timer.deinit()
            self.sequence_timer = None
        
        self.running_startup_sequence = False
        
        # Asegurar que ningún LED quede encendido
        self.all_off()
        
    def sequence_step(self, timer):
        """Ejecuta un paso de la secuencia de transición de colores"""
        if not self.running_startup_sequence:
            self.stop_sequence_timer()
            return
            
        step_duration = 20
        
        # Determinar qué LEDs usar según el color_index
        if self.color_index == 0:
            # Transición Rojo -> Verde
            led_from = self.led_red
            led_to = self.led_green
        elif self.color_index == 1:
            # Transición Verde -> Azul
            led_from = self.led_green
            led_to = self.led_blue
        else:  # self.color_index == 2
            # Transición Azul -> Rojo
            led_from = self.led_blue
            led_to = self.led_red
            
        # Primero apagar TODOS los LEDs de forma explícita
        self.all_off()
        
        # Calculamos si debe encenderse el LED origen o destino
        if self.transition_step < 10:
            # Primera mitad: LED origen encendido
            led_from.value(1)
        else:
            # Segunda mitad: LED destino encendido
            led_to.value(1)
            
        # Avanzar al siguiente paso
        self.transition_step += 1
        
        # Si completamos 20 pasos, avanzar al siguiente par de colores
        if self.transition_step >= 20:
            self.transition_step = 0
            self.color_index = (self.color_index + 1) % 3  # Rotar entre 0, 1, 2