import os
import utime

LOG_DIR = '/logs'
LOG_FILE = LOG_DIR + '/log.txt'
LOG_MAX_SIZE = 256 * 1024  # 256 KB
LOG_MAX_FILES = 4

class LogManager:
    def __init__(self):
        self.ensure_log_dir()

    def ensure_log_dir(self):
        try:
            os.mkdir(LOG_DIR)
        except OSError as e:
            if e.args[0] != 17:  # 17 significa que el directorio ya existe
                raise

    def log(self, message):
        timestamp = self.get_timestamp()
        log_message = f"{timestamp} - {message}\n"
        with open(LOG_FILE, 'a') as f:
            f.write(log_message)
        self.check_log_size()

    def get_timestamp(self):
        tm = utime.localtime()
        return "{:04d}-{:02d}-{:02d} {:02d}:{:02d}:{:02d}".format(tm[0], tm[1], tm[2], tm[3], tm[4], tm[5])

    def check_log_size(self):
        if os.stat(LOG_FILE)[6] > LOG_MAX_SIZE:
            self.rotate_logs()

    def rotate_logs(self):
        for i in range(LOG_MAX_FILES - 1, 0, -1):
            old_file = f"{LOG_FILE}.{i}"
            new_file = f"{LOG_FILE}.{i + 1}"
            if os.path.exists(old_file):
                os.rename(old_file, new_file)
        os.rename(LOG_FILE, f"{LOG_FILE}.1")

    def delete_old_logs(self):
        for i in range(LOG_MAX_FILES + 1, 100):
            old_file = f"{LOG_FILE}.{i}"
            try:
                os.remove(old_file)
            except OSError:
                pass

log_manager = LogManager()

def log_message(message):
    log_manager.log(message)

def delete_old_logs():
    log_manager.delete_old_logs()
