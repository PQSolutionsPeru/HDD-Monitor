import gc
import utime

def chequear_rendimiento(func, *args, **kwargs):
    gc.collect()  # Recolectar basura y medir memoria libre antes de la ejecución
    memoria_libre_antes = gc.mem_free()
    print("Memoria libre antes de la ejecución:", memoria_libre_antes)

    tiempo_inicio = utime.ticks_ms()  # Tiempo de inicio en milisegundos
    func(*args, **kwargs)  # Ejecutar la función con los argumentos proporcionados
    tiempo_fin = utime.ticks_ms()  # Tiempo de finalización en milisegundos
    tiempo_ejecucion = utime.ticks_diff(tiempo_fin, tiempo_inicio)  # Calcular la diferencia de tiempo

    gc.collect()  # Recolectar basura y medir memoria libre después de la ejecución
    memoria_libre_despues = gc.mem_free()
    print(f"Tiempo de ejecución de {func.__name__}: {tiempo_ejecucion} ms")
    print("Memoria libre después de la ejecución:", memoria_libre_despues)

    cambio_memoria = memoria_libre_antes - memoria_libre_despues
    print(f"Cambio en la memoria usada: {cambio_memoria} bytes")
