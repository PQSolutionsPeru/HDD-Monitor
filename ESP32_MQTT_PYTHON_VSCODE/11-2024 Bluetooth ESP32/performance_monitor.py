import gc
import utime
import json
import machine
from machine import Timer

class PerformanceMonitor:
    def __init__(self, mqtt_manager=None, wifi_manager=None, esp32_id=None):
        self.mqtt_manager = mqtt_manager
        self.wifi_manager = wifi_manager
        self.esp32_id = esp32_id
        self.start_time = utime.ticks_ms()
        self.monitoring_active = False
        
        # Intervalos críticos
        self.MEMORY_CHECK_INTERVAL = 30000    # 30 segundos
        self.MQTT_CHECK_INTERVAL = 15000      # 15 segundos
        self.WIFI_CHECK_INTERVAL = 10000      # 10 segundos
        self.STATS_PUBLISH_INTERVAL = 300000  # 5 minutos
        
        # Umbrales críticos
        self.MIN_FREE_MEMORY = 20000          # 20KB mínimo de memoria libre
        self.MAX_ALLOC_MEMORY = 100000        # 100KB máximo de memoria asignada
        self.CRITICAL_TEMP_THRESHOLD = 80     # 80°C temperatura crítica
        
        # Estado del sistema
        self.system_state = {
            'last_wifi_check': 0,
            'last_mqtt_check': 0,
            'last_memory_check': 0,
            'last_stats_publish': 0,
            'boot_count': 0,
            'last_reset_cause': self._get_reset_cause(),
            'critical_errors': [],
            'wifi_reconnects': 0,
            'mqtt_reconnects': 0
        }
        
        # Métricas
        self.metrics = {
            'memory_usage': [],
            'temperature': [],
            'wifi_signal': [],
            'mqtt_latency': [],
            'cpu_frequency': machine.freq()
        }
        
        # Configurar timer de monitoreo
        self.monitor_timer = Timer(0)
        self.monitor_timer.init(period=5000, mode=Timer.PERIODIC, 
                              callback=self._monitor_callback)
        
        print("[PERF] Monitor de rendimiento iniciado")

    def _monitor_callback(self, _):
        """Callback periódico para monitoreo"""
        try:
            current_time = utime.ticks_ms()
            
            # Verificar memoria
            if utime.ticks_diff(current_time, self.system_state['last_memory_check']) >= self.MEMORY_CHECK_INTERVAL:
                self._check_memory()
                self.system_state['last_memory_check'] = current_time
            
            # Verificar WiFi
            if utime.ticks_diff(current_time, self.system_state['last_wifi_check']) >= self.WIFI_CHECK_INTERVAL:
                self._check_wifi()
                self.system_state['last_wifi_check'] = current_time
            
            # Verificar MQTT
            if utime.ticks_diff(current_time, self.system_state['last_mqtt_check']) >= self.MQTT_CHECK_INTERVAL:
                self._check_mqtt()
                self.system_state['last_mqtt_check'] = current_time
            
            # Publicar estadísticas
            if utime.ticks_diff(current_time, self.system_state['last_stats_publish']) >= self.STATS_PUBLISH_INTERVAL:
                self._publish_system_stats()
                self.system_state['last_stats_publish'] = current_time
                
        except Exception as e:
            self._handle_critical_error("monitor_callback", str(e))

    def _check_memory(self):
        """Verifica el estado de la memoria"""
        try:
            gc.collect()
            free_mem = gc.mem_free()
            alloc_mem = gc.mem_alloc()
            
            self.metrics['memory_usage'].append({
                'free': free_mem,
                'allocated': alloc_mem,
                'timestamp': utime.ticks_ms()
            })
            
            # Mantener solo las últimas 100 mediciones
            if len(self.metrics['memory_usage']) > 100:
                self.metrics['memory_usage'].pop(0)
            
            # Verificar umbrales críticos
            if free_mem < self.MIN_FREE_MEMORY:
                self._handle_critical_error(
                    "memory_low",
                    f"Memoria libre crítica: {free_mem} bytes"
                )
            
            if alloc_mem > self.MAX_ALLOC_MEMORY:
                self._handle_critical_error(
                    "memory_high",
                    f"Memoria asignada crítica: {alloc_mem} bytes"
                )
                
        except Exception as e:
            self._handle_critical_error("memory_check", str(e))

    def _check_wifi(self):
        """Verifica la conexión WiFi"""
        try:
            if self.wifi_manager:
                if not self.wifi_manager.check_connection():
                    self.system_state['wifi_reconnects'] += 1
                    self._publish_alert("wifi_disconnected", "Conexión WiFi perdida")
                    
                signal = self.wifi_manager.get_signal_strength()
                if signal is not None:
                    self.metrics['wifi_signal'].append({
                        'rssi': signal,
                        'timestamp': utime.ticks_ms()
                    })
                    
        except Exception as e:
            self._handle_critical_error("wifi_check", str(e))

    def _check_mqtt(self):
        """Verifica la conexión MQTT"""
        try:
            if self.mqtt_manager:
                if not self.mqtt_manager.check_connection():
                    self.system_state['mqtt_reconnects'] += 1
                    self._publish_alert("mqtt_disconnected", "Conexión MQTT perdida")
                    
        except Exception as e:
            self._handle_critical_error("mqtt_check", str(e))

    def _publish_system_stats(self):
        """Publica estadísticas del sistema"""
        if not self.mqtt_manager:
            return
            
        try:
            stats = {
                'esp32_id': self.esp32_id,
                'uptime': utime.ticks_diff(utime.ticks_ms(), self.start_time) // 1000,
                'memory': self.metrics['memory_usage'][-1] if self.metrics['memory_usage'] else None,
                'wifi': {
                    'reconnects': self.system_state['wifi_reconnects'],
                    'signal': self.metrics['wifi_signal'][-1] if self.metrics['wifi_signal'] else None
                },
                'mqtt': {
                    'reconnects': self.system_state['mqtt_reconnects']
                },
                'cpu_freq': self.metrics['cpu_frequency'],
                'temperature': self._get_temperature(),
                'timestamp': utime.ticks_ms()
            }
            
            self.mqtt_manager.publish_event(
                f"system/stats/{self.esp32_id}",
                json.dumps(stats),
                retain=True
            )
            
        except Exception as e:
            self._handle_critical_error("stats_publish", str(e))

    def _publish_alert(self, alert_type, message):
        """Publica una alerta en el broker MQTT"""
        if not self.mqtt_manager:
            return
            
        try:
            alert = {
                'esp32_id': self.esp32_id,
                'type': alert_type,
                'message': message,
                'timestamp': utime.ticks_ms()
            }
            
            self.mqtt_manager.publish_event(
                f"system/alerts/{self.esp32_id}",
                json.dumps(alert),
                retain=True
            )
            
        except Exception as e:
            print(f"[PERF] Error publicando alerta: {e}")

    def _handle_critical_error(self, error_type, error_message):
        """Maneja errores críticos del sistema"""
        try:
            error_info = {
                'type': error_type,
                'message': error_message,
                'timestamp': utime.ticks_ms()
            }
            
            print(f"[PERF] Error crítico: {error_type} - {error_message}")
            
            # Almacenar error
            self.system_state['critical_errors'].append(error_info)
            if len(self.system_state['critical_errors']) > 10:
                self.system_state['critical_errors'].pop(0)
            
            # Publicar error
            self._publish_alert("critical_error", error_info)
            
            # Si es un error grave, reiniciar
            if error_type in ['memory_low', 'memory_high']:
                print("[PERF] Error crítico de memoria, reiniciando...")
                machine.reset()
                
        except Exception as e:
            print(f"[PERF] Error en manejo de error crítico: {e}")
            machine.reset()

    def _get_temperature(self):
        """Obtiene la temperatura del ESP32"""
        try:
            # Implementación específica para ESP32
            temp = machine.nvs_getint('temp') or 0
            return temp
        except:
            return None

    def _get_reset_cause(self):
        """Obtiene la causa del último reset"""
        causes = {
            machine.PWRON_RESET: "Power on",
            machine.HARD_RESET: "Hard reset",
            machine.WDT_RESET: "Watchdog",
            machine.DEEPSLEEP_RESET: "Deep sleep",
            machine.SOFT_RESET: "Soft reset"
        }
        try:
            return causes.get(machine.reset_cause(), "Unknown")
        except:
            return "Unknown"

    def cleanup(self):
        """Limpia recursos antes de cerrar"""
        try:
            self.monitor_timer.deinit()
            self._publish_alert("shutdown", "Sistema apagándose")
            gc.collect()
        except Exception as e:
            print(f"[PERF] Error en cleanup: {e}")

    def get_system_status(self):
        """Obtiene un reporte completo del estado del sistema"""
        return {
            'state': self.system_state,
            'metrics': self.metrics,
            'memory': {
                'free': gc.mem_free(),
                'allocated': gc.mem_alloc()
            },
            'timestamp': utime.ticks_ms()
        }