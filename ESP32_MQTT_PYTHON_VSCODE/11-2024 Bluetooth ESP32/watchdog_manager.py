from machine import WDT, Timer
import utime
import json
import gc

class WatchdogManager:
    def __init__(self, timeout=120000):
        """
        Inicializa el watchdog con timeout de 120 segundos por defecto
        Args:
            timeout: Tiempo en ms antes de reset
        """
        self.TIMEOUT = timeout
        self.watchdog = WDT(timeout=self.TIMEOUT)
        self.mqtt_manager = None
        
        # Control de tiempo
        self.last_feed = utime.ticks_ms()
        self.last_check = utime.ticks_ms()
        self.FEED_INTERVAL = 5000      # 5 segundos
        self.CHECK_INTERVAL = 10000    # 10 segundos
        
        # Contadores de reset
        self.reset_count = 0
        self.MAX_RESETS = 5  # Máximo de resets antes de entrar en modo seguro
        self.last_reset_time = 0
        self.RESET_WINDOW = 300000  # 5 minutos
        
        # Estado del sistema
        self.system_stats = {
            'uptime': 0,
            'memory_free': 0,
            'last_feed': 0,
            'reset_count': 0
        }
        
        print(f"[WATCHDOG] Iniciado con timeout de {timeout}ms")

    def feed(self):
        """Alimenta al watchdog con verificaciones de seguridad"""
        try:
            current_time = utime.ticks_ms()
            
            # Verificar si es momento de alimentar
            if utime.ticks_diff(current_time, self.last_feed) >= self.FEED_INTERVAL:
                # Verificar memoria antes de alimentar
                if gc.mem_free() < 10000:  # Menos de 10KB libre
                    gc.collect()
                    utime.sleep_ms(100)
                
                self.watchdog.feed()
                self.last_feed = current_time
                self.system_stats['last_feed'] = current_time
                self.system_stats['memory_free'] = gc.mem_free()
                
                # Verificar resets frecuentes
                self._check_reset_pattern()
        except Exception as e:
            print(f"[WATCHDOG] Error en feed: {e}")
            try:
                self.watchdog.feed()  # Intentar alimentar de todos modos
            except:
                pass

    def _check_reset_pattern(self):
        """Detecta patrones de reset problemáticos"""
        try:
            current_time = utime.ticks_ms()
            
            # Si han ocurrido varios resets en ventana de tiempo
            if (self.reset_count > 0 and 
                utime.ticks_diff(current_time, self.last_reset_time) < self.RESET_WINDOW):
                self.reset_count += 1
                
                if self.reset_count >= self.MAX_RESETS:
                    print("[WATCHDOG] Demasiados resets detectados")
                    self._enter_safe_mode()
            else:
                # Reiniciar contador si pasó la ventana de tiempo
                self.reset_count = 0
                self.last_reset_time = current_time
                
        except Exception as e:
            print(f"[WATCHDOG] Error en check_reset_pattern: {e}")

    def _enter_safe_mode(self):
        """Entra en modo seguro cuando hay problemas"""
        try:
            print("[WATCHDOG] Entrando en modo seguro")
            
            # Intentar notificar problema
            if self.mqtt_manager:
                try:
                    self.mqtt_manager.publish_event(
                        "system/status",
                        json.dumps({
                            "status": "safe_mode",
                            "reason": "excessive_resets",
                            "reset_count": self.reset_count
                        })
                    )
                except:
                    pass
            
            # Esperar para asegurar envío de mensaje
            utime.sleep_ms(1000)
            
            # Reducir funcionalidad
            self.FEED_INTERVAL = 2000  # Alimentar más frecuentemente
            gc.collect()
            
        except Exception as e:
            print(f"[WATCHDOG] Error entrando en modo seguro: {e}")

    def force_reset(self, reason="manual"):
        """Fuerza un reset controlado"""
        try:
            print(f"[WATCHDOG] Forzando reset: {reason}")
            
            # Intentar notificar reset
            if self.mqtt_manager:
                try:
                    self.mqtt_manager.publish_event(
                        "system/reset",
                        json.dumps({
                            "reason": reason,
                            "timestamp": utime.ticks_ms()
                        })
                    )
                except:
                    pass
            
            # Esperar para asegurar envío
            utime.sleep_ms(500)
            
            # No alimentar el watchdog causará reset
            while True:
                utime.sleep_ms(1000)
                
        except Exception as e:
            print(f"[WATCHDOG] Error en force_reset: {e}")
            import machine
            machine.reset()