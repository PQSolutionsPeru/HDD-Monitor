import network
import utime
import machine
import gc
import json
import os

class WiFiManager:
    _instance = None
    _initialized = False
    
    def __new__(cls):
        if cls._instance is None:
            print("[WIFI] Creating WiFiManager singleton instance")
            cls._instance = super(WiFiManager, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if WiFiManager._initialized:
            return
            
        try:
            print("[WIFI] First-time WiFiManager initialization")
            WiFiManager._initialized = True
            
            # Clean up any existing WiFi state
            self._clean_wifi_state()
            
            # Configuration
            self.ssid = None
            self.password = None
            self.WIFI_CONFIG_FILE = "wifi_config.json"
            
            # Connection parameters
            self.CONNECT_TIMEOUT = 20000  # 20 seconds timeout per attempt
            self.MAX_RETRIES = 3         # 3 maximum attempts per cycle
            self.RETRY_DELAY = 2000      # 2 seconds between attempts
            self.CHECK_INTERVAL = 60000   # 60 seconds between checks
            
            self.last_check = utime.ticks_ms()
            self.last_error = None
            self.current_ip = None
            self.sta_if = None
            
            # Initialize interface safely
            self._safe_init_interface()
            
        except Exception as e:
            print(f"[WIFI] Initialization error: {e}")
            WiFiManager._initialized = False
            raise

    def _clean_wifi_state(self):
        """Cleans existing WiFi state"""
        try:
            # Disable any existing WiFi instance
            try:
                wlan = network.WLAN(network.STA_IF)
                if wlan:
                    wlan.active(False)
                    utime.sleep_ms(500)
                    del wlan
            except:
                pass
            
            gc.collect()
            utime.sleep_ms(500)
            
        except Exception as e:
            print(f"[WIFI] Error cleaning state: {e}")

    def _safe_init_interface(self):
        """Safely initializes WiFi interface with retries"""
        for attempt in range(3):
            try:
                print(f"[WIFI] Interface initialization attempt {attempt + 1}/3")
                
                # Clean previous state
                self._clean_wifi_state()
                gc.collect()
                utime.sleep_ms(1000)
                
                # Create new interface
                self.sta_if = network.WLAN(network.STA_IF)
                if not self.sta_if:
                    print("[WIFI] Error: Could not create interface")
                    continue
                
                # Deactivate first
                self.sta_if.active(False)
                utime.sleep_ms(500)
                
                # Try basic configurations
                try:
                    if hasattr(self.sta_if, 'config'):
                        self.sta_if.config(reconnects=3)
                except Exception as e:
                    print(f"[WIFI] Warning in basic config: {e}")
                
                # Buffer configuration in stages
                buffer_configs = [
                    {"rxbuf": 128, "txbuf": 128},  # Start with smaller buffers
                    {"rxbuf": 256, "txbuf": 256},
                    {"rxbuf": 512, "txbuf": 512}
                ]
                
                configured = False
                for config in buffer_configs:
                    try:
                        print(f"[WIFI] Testing buffer config: {config}")
                        if hasattr(self.sta_if, 'config'):
                            self.sta_if.config(**config)
                            utime.sleep_ms(100)
                            configured = True
                            break
                    except:
                        continue
                
                if not configured:
                    print("[WIFI] Warning: Could not configure buffers")
                
                # Verify interface is in good state
                if self.sta_if and hasattr(self.sta_if, 'active'):
                    print("[WIFI] Interface initialized successfully")
                    # Try loading saved configuration
                    self._load_saved_config()
                    return True
                    
            except Exception as e:
                print(f"[WIFI] Error in attempt {attempt + 1}: {e}")
                gc.collect()
                utime.sleep_ms(1000)
                
                # Clean up for next attempt
                self._clean_wifi_state()
                self.sta_if = None
        
        raise Exception("Could not initialize WiFi interface after 3 attempts")

    def connect_wifi(self, ssid, password):
        """Connects to WiFi with improved error handling"""
        try:
            if not ssid or not password:
                print("[WIFI] Error: Missing credentials")
                self.last_error = "invalid_credentials"
                return False

            print(f"[WIFI] Attempting connection to: {ssid}")
            gc.collect()

            # Handle interface activation
            if not self.sta_if or not hasattr(self.sta_if, 'active'):
                self._safe_init_interface()
                
            # Ensure interface is active
            if not self.sta_if.active():
                self.sta_if.active(True)
                utime.sleep_ms(1000)

            # Disconnect if already connected
            if self.sta_if.isconnected():
                self.sta_if.disconnect()
                utime.sleep_ms(500)

            retry_count = 0
            while retry_count < self.MAX_RETRIES:
                print(f"[WIFI] Attempt {retry_count + 1}/{self.MAX_RETRIES}")
                
                try:
                    self.sta_if.connect(ssid, password)
                except Exception as e:
                    print(f"[WIFI] Connection attempt error: {e}")
                    retry_count += 1
                    if retry_count < self.MAX_RETRIES:
                        gc.collect()
                        utime.sleep_ms(self.RETRY_DELAY)
                        continue
                    return False
                
                start_time = utime.ticks_ms()
                while utime.ticks_diff(utime.ticks_ms(), start_time) < self.CONNECT_TIMEOUT:
                    if self.sta_if.isconnected():
                        utime.sleep_ms(500)  # Wait for connection to stabilize
                        self.current_ip = self.sta_if.ifconfig()[0]
                        print(f"[WIFI] Successfully connected - IP: {self.current_ip}")
                        self.ssid = ssid
                        self.password = password
                        self._save_config()
                        self.last_error = None
                        return True
                    
                    status = self.sta_if.status()
                    if status == network.STAT_CONNECTING:
                        utime.sleep_ms(100)
                        continue
                    elif status == network.STAT_WRONG_PASSWORD:
                        print("[WIFI] Error: Wrong password")
                        self.last_error = "wrong_password"
                        return False
                    elif status == network.STAT_NO_AP_FOUND:
                        print("[WIFI] Error: Network not found")
                        self.last_error = "network_not_found"
                        break
                    
                    utime.sleep_ms(100)
                
                retry_count += 1
                if retry_count < self.MAX_RETRIES:
                    print(f"[WIFI] Retrying connection... ({retry_count + 1})")
                    gc.collect()
                    utime.sleep_ms(self.RETRY_DELAY)
                    self._safe_init_interface()
                    self.sta_if.active(True)
                    utime.sleep_ms(500)

            self.last_error = "connection_failed"
            print("[WIFI] Could not establish connection after all attempts")
            return False

        except Exception as e:
            print(f"[WIFI] Connection error: {e}")
            self.last_error = str(e)
            return False

    def check_connection(self):
        """Verifies and maintains WiFi connection"""
        try:
            if not self.sta_if:
                return False
                
            current_time = utime.ticks_ms()
            if utime.ticks_diff(current_time, self.last_check) >= self.CHECK_INTERVAL:
                self.last_check = current_time
                
                if not self.sta_if.isconnected():
                    print("[WIFI] Connection lost, attempting reconnection...")
                    if self.ssid and self.password:
                        return self.connect_wifi(self.ssid, self.password)
                    return False
                    
                self.current_ip = self.sta_if.ifconfig()[0]
                return True
                
            return self.sta_if.isconnected()
                
        except Exception as e:
            print(f"[WIFI] Error checking connection: {e}")
            return False

    def _load_saved_config(self):
        """Loads saved WiFi configuration"""
        try:
            if self.WIFI_CONFIG_FILE in os.listdir():
                print("[WIFI] Loading saved configuration...")
                with open(self.WIFI_CONFIG_FILE, 'r') as f:
                    config = json.load(f)
                    if config.get('ssid') and config.get('password'):
                        self.ssid = config['ssid']
                        self.password = config['password']
                        print(f"[WIFI] Configuration loaded for SSID: {self.ssid}")
                        return True
                    
            print("[WIFI] No valid saved configuration found")
            return False
            
        except Exception as e:
            print(f"[WIFI] Error loading configuration: {e}")
            return False

    def _save_config(self):
        """Safely saves WiFi configuration"""
        try:
            if not self.ssid or not self.password:
                return False
                
            config = {
                'ssid': self.ssid,
                'password': self.password,
                'last_connected': utime.time(),
                'ip': self.current_ip if self.current_ip else None
            }

            # Use temporary file for atomic write
            temp_file = self.WIFI_CONFIG_FILE + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump(config, f)
                
            # Verify the temporary file
            with open(temp_file, 'r') as f:
                verify_config = json.load(f)
                if not verify_config.get('ssid') or not verify_config.get('password'):
                    raise ValueError("Invalid configuration data")
                    
            # If verification passes, rename temp file to actual file
            os.rename(temp_file, self.WIFI_CONFIG_FILE)
            print(f"[WIFI] Configuration saved for SSID: {self.ssid}")
            return True
                
        except Exception as e:
            print(f"[WIFI] Error saving configuration: {e}")
            try:
                os.remove(temp_file)
            except:
                pass
            return False

    def disconnect(self):
        """Disconnects from WiFi and cleans up"""
        try:
            if self.sta_if and self.sta_if.isconnected():
                self.sta_if.disconnect()
                self.sta_if.active(False)
                utime.sleep_ms(500)
            return True
        except Exception as e:
            print(f"[WIFI] Error disconnecting: {e}")
            return False

    def get_current_ip(self):
        """Gets current IP address"""
        try:
            if self.sta_if and self.sta_if.isconnected():
                self.current_ip = self.sta_if.ifconfig()[0]
                return self.current_ip
            return None
        except Exception as e:
            print(f"[WIFI] Error getting IP: {e}")
            return None

    def get_error(self):
        """Gets last error message"""
        return self.last_error

    def is_connected(self):
        """Checks if currently connected"""
        try:
            return self.sta_if and self.sta_if.isconnected()
        except:
            return False

    def get_signal_strength(self):
        """Gets current signal strength if connected"""
        try:
            if self.sta_if and self.sta_if.isconnected():
                return self.sta_if.status('rssi')
            return None
        except:
            return None

    def get_saved_networks(self):
        """Gets list of saved networks"""
        try:
            if self.WIFI_CONFIG_FILE in os.listdir():
                with open(self.WIFI_CONFIG_FILE, 'r') as f:
                    config = json.load(f)
                    return [config.get('ssid')] if config.get('ssid') else []
            return []
        except:
            return []

    def forget_network(self):
        """Forgets current network configuration"""
        try:
            if self.WIFI_CONFIG_FILE in os.listdir():
                os.remove(self.WIFI_CONFIG_FILE)
            self.ssid = None
            self.password = None
            self.current_ip = None
            if self.sta_if and self.sta_if.isconnected():
                self.sta_if.disconnect()
            return True
        except Exception as e:
            print(f"[WIFI] Error forgetting network: {e}")
            return False