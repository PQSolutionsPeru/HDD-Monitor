import network
import utime
import urequests
import json

class WiFiManager:
    def __init__(self):
        self.SSID = None
        self.PASSWORD = None
        self.sta_if = network.WLAN(network.STA_IF)
        self.sta_if.active(True)
        self.last_time_sync = 0
        self.TIME_SYNC_INTERVAL = 300000  # 5 minutos
        self.current_time = None
        
    def connect_wifi(self):
        if not self.SSID or not self.PASSWORD:
            raise Exception("SSID y PASSWORD no configurados")
            
        if not self.sta_if.isconnected():
            print(f"Intentando conectar a red: {self.SSID}")
            
            try:
                self.sta_if.disconnect()
            except:
                pass
                
            utime.sleep(1)
            
            try:
                self.sta_if.connect(self.SSID, self.PASSWORD)
            except Exception as e:
                print(f"Error al intentar conexión: {e}")
                return False
                
            print("Esperando respuesta del router...")
            
        return self.sta_if.isconnected()

    def ensure_wifi_connected(self):
        if not self.sta_if.isconnected():
            return self.connect_wifi()
        return True

    def disconnect(self):
        if self.sta_if.isconnected():
            self.sta_if.disconnect()
            while self.sta_if.isconnected():
                utime.sleep_ms(100)
                
    def get_status(self):
        if not self.sta_if.active():
            return "DISABLED"
        if self.sta_if.isconnected():
            return "CONNECTED"
        return "DISCONNECTED"

    def get_signal_strength(self):
        if self.sta_if.isconnected():
            try:
                return self.sta_if.status('rssi')
            except:
                return None
        return None

    def check_connection(self):
        self.ensure_wifi_connected()

    def get_current_time(self):
        current_time = utime.ticks_ms()
        
        # Si no tenemos tiempo almacenado o han pasado más de 5 minutos
        if (self.current_time is None or 
            utime.ticks_diff(current_time, self.last_time_sync) >= self.TIME_SYNC_INTERVAL):
            try:
                self.check_connection()
                new_time = self._get_world_time()
                if new_time:
                    self.current_time = new_time
                    self.last_time_sync = current_time
                    print(f"Hora actualizada: {self.current_time}")
            except Exception as e:
                print(f"Error actualizando la hora: {e}")
                
        return self.current_time

    def _get_world_time(self):
        try:
            # Intentar primero con worldtimeapi.org
            try:
                print("Intentando obtener hora de worldtimeapi.org...")
                response = urequests.get("https://worldtimeapi.org/api/timezone/America/Lima", timeout=5)
                data = response.json()
                response.close()
                return self._format_datetime(data["datetime"])
            except Exception as e:
                print(f"Error con worldtimeapi.org: {e}")
                
            # Si falla, intentar con timeapi.io
            print("Intentando obtener hora de timeapi.io...")
            response = urequests.get("https://timeapi.io/api/Time/current/zone?timeZone=America/Lima", timeout=5)
            data = response.json()
            response.close()
            
            # Formatear la fecha de timeapi.io
            date_str = f"{data['year']}-{data['month']:02d}-{data['day']:02d}"
            time_str = f"{data['hour']:02d}:{data['minute']:02d}:{data['seconds']:02d}"
            datetime_str = f"{date_str}T{time_str}"
            return self._format_datetime(datetime_str)
            
        except Exception as e:
            print(f"Error al obtener la fecha y hora: {str(e)}")
            return None

    def _format_datetime(self, datetime_str):
        try:
            # Formatear la fecha y hora según los requisitos
            if 'T' in datetime_str:
                date_part, time_part = datetime_str.split('T')
            else:
                date_part = datetime_str[:10]
                time_part = datetime_str[11:19]
                
            year, month, day = date_part.split("-")
            time_str = time_part[:8]  # Asegurar que solo tomamos HH:MM:SS
            
            return f"el {day}-{month}-{year} a las {time_str}"
        except Exception as e:
            print(f"Error al formatear fecha/hora: {str(e)}")
            return None