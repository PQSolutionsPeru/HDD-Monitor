from config.base_config import BaseConfig

class OTAConfig(BaseConfig):
    def __init__(self):
        self.DEFAULT_CONFIG = {
            # Timeouts y delays
            "update_timeout": 300000,      # 5 minutos timeout total
            "chunk_timeout": 10000,        # 10 segundos entre chunks
            "retry_delay": 5000,           # 5 segundos entre reintentos
            "max_retries": 3,              # Máximo 3 reintentos por chunk
            
            # Tamaños y límites
            "chunk_size": 4096,            # 4KB por chunk
            "max_file_size": 1048576,      # 1MB máximo por archivo
            "min_free_space": 524288,      # 512KB mínimo espacio libre
            
            # Versiones
            "current_version": "1.0.0",    # Versión inicial
            "min_version": "1.0.0",        # Versión mínima requerida
            "rollback_versions": 2,        # Mantener últimas 2 versiones para rollback
            
            # Control de archivos
            "allowed_extensions": [".py", ".json", ".cert"],  # Extensiones permitidas
            "protected_files": [           # Archivos que no se pueden actualizar
                "boot.py",
                "wifi_config.json",
                "mqtt_config.json"
            ],
            
            # Verificación
            "verify_python_files": True,   # Verificar sintaxis de archivos Python
            "backup_enabled": True,        # Realizar backup antes de actualizar
            "verify_hash": True,           # Verificar hash de archivos
            
            # Logs
            "max_log_size": 10240,        # 10KB máximo para logs
            "keep_logs": 5                 # Mantener últimos 5 logs
        }
        super().__init__('ota_config.json')
        self._init_default_config()

    def _init_default_config(self):
        """Inicializa la configuración por defecto"""
        if not self.config:
            self.config = self.DEFAULT_CONFIG.copy()
            self._save_config()
            
    def get_update_timeout(self):
        """Obtiene timeout para actualización completa"""
        return self.config.get('update_timeout', self.DEFAULT_CONFIG['update_timeout'])
        
    def get_chunk_timeout(self):
        """Obtiene timeout entre chunks"""
        return self.config.get('chunk_timeout', self.DEFAULT_CONFIG['chunk_timeout'])
        
    def get_chunk_size(self):
        """Obtiene tamaño máximo de chunk"""
        return self.config.get('chunk_size', self.DEFAULT_CONFIG['chunk_size'])
        
    def is_file_allowed(self, filename):
        """Verifica si un archivo puede ser actualizado"""
        if filename in self.config.get('protected_files', []):
            return False
            
        ext = '.' + filename.split('.')[-1] if '.' in filename else ''
        return ext in self.config.get('allowed_extensions', [])
        
    def should_verify_python(self):
        """Verifica si se debe validar sintaxis Python"""
        return self.config.get('verify_python_files', True)
        
    def should_backup(self):
        """Verifica si se debe hacer backup"""
        return self.config.get('backup_enabled', True)
        
    def get_min_free_space(self):
        """Obtiene espacio libre mínimo requerido"""
        return self.config.get('min_free_space', self.DEFAULT_CONFIG['min_free_space'])
        
    def update_version(self, new_version):
        """Actualiza la versión actual"""
        self.config['current_version'] = new_version
        return self._save_config()