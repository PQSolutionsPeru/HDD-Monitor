from machine import WDT, Timer
import utime
import machine
import gc

class WatchdogManager:
    def __init__(self):
        """Inicializa el watchdog con timeout aumentado"""
        self.TIMEOUT = 600000  # 600 segundos (10 minutos)
        self.watchdog = WDT(timeout=self.TIMEOUT)
        
        # Intervalos críticos
        self.last_feed = utime.ticks_ms()
        self.FEED_INTERVAL = 60000  # 60 segundos
        
        # Control de resets
        self.reset_count = 0
        self.MAX_RESETS = 3
        self.last_reset = 0
        self.RESET_WINDOW = 600000  # 10 minutos
        
        print("[WATCHDOG] Iniciado con timeout extendido")

    def feed(self):
        """Alimenta al watchdog si ha pasado el intervalo"""
        try:
            current_time = utime.ticks_ms()
            
            if utime.ticks_diff(current_time, self.last_feed) >= self.FEED_INTERVAL:
                # Verificar memoria
                if gc.mem_free() < 10000:  # Menos de 10KB libre
                    gc.collect()
                    utime.sleep_ms(100)
                
                self.watchdog.feed()
                self.last_feed = current_time
                
        except Exception as e:
            print(f"[WATCHDOG] Error en feed: {e}")
            try:
                self.watchdog.feed()  # Intentar alimentar de todos modos
            except:
                pass

    def force_reset(self, reason="watchdog_timeout"):
        """Fuerza un reset del sistema"""
        try:
            print(f"[WATCHDOG] Forzando reset: {reason}")
            
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_reset) < self.RESET_WINDOW:
                self.reset_count += 1
                if self.reset_count >= self.MAX_RESETS:
                    print("[WATCHDOG] Demasiados resets, ejecutando hard reset")
                    machine.reset()
            else:
                self.reset_count = 1
                
            self.last_reset = current_time
            machine.reset()
            
        except Exception as e:
            print(f"[WATCHDOG] Error en force_reset: {e}")
            machine.reset()

    def check_system_health(self):
        """Verifica salud básica del sistema"""
        try:
            # Verificar memoria
            if gc.mem_free() < 10000:
                gc.collect()
                utime.sleep_ms(100)
                if gc.mem_free() < 10000:
                    return False
            
            # Verificar tiempo desde último feed
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_feed) >= self.TIMEOUT:
                return False
                
            return True
            
        except:
            return False