from micropython import const
import struct
import bluetooth

# Constantes de advertising
_ADV_TYPE_FLAGS = const(0x01)
_ADV_TYPE_NAME = const(0x09)
_ADV_TYPE_UUID16_COMPLETE = const(0x3)
_ADV_TYPE_UUID32_COMPLETE = const(0x5)
_ADV_TYPE_UUID128_COMPLETE = const(0x7)
_ADV_TYPE_UUID16_MORE = const(0x2)
_ADV_TYPE_UUID32_MORE = const(0x4)
_ADV_TYPE_UUID128_MORE = const(0x6)
_ADV_TYPE_APPEARANCE = const(0x19)

# Máximo payload permitido
_ADV_MAX_PAYLOAD = const(31)

def advertising_payload(limited_disc=False, br_edr=False, name=None, services=None, appearance=0):
    """Genera payload de advertising con protección contra errores"""
    try:
        payload = bytearray()

        def _append(adv_type, value):
            nonlocal payload
            if len(payload) + 2 + len(value) > _ADV_MAX_PAYLOAD:
                raise ValueError("Payload excede máximo permitido")
            payload += struct.pack("BB", len(value) + 1, adv_type) + value

        # Flags
        _append(
            _ADV_TYPE_FLAGS,
            struct.pack("B", (0x01 if limited_disc else 0x02) + (0x18 if br_edr else 0x04))
        )

        # Nombre del dispositivo
        if name:
            _append(_ADV_TYPE_NAME, name.encode())

        # UUIDs de servicios
        if services:
            for uuid in services:
                b = bytes(uuid)
                if len(b) == 2:
                    _append(_ADV_TYPE_UUID16_COMPLETE, b)
                elif len(b) == 4:
                    _append(_ADV_TYPE_UUID32_COMPLETE, b)
                elif len(b) == 16:
                    _append(_ADV_TYPE_UUID128_COMPLETE, b)

        # Appearance
        if appearance:
            _append(_ADV_TYPE_APPEARANCE, struct.pack("<h", appearance))

        return payload

    except Exception as e:
        print(f"[BLE_ADV] Error generando payload: {e}")
        # Retornar payload mínimo en caso de error
        return struct.pack("BB", 2, _ADV_TYPE_FLAGS) + struct.pack("B", 0x06)

def decode_field(payload, adv_type):
    """Decodifica campos específicos del payload"""
    try:
        i = 0
        result = []
        while i + 1 < len(payload):
            if payload[i + 1] == adv_type:
                result.append(payload[i + 2 : i + payload[i] + 1])
            i += 1 + payload[i]
        return result
    except Exception as e:
        print(f"[BLE_ADV] Error decodificando campo: {e}")
        return []

def decode_name(payload):
    """Decodifica el nombre del dispositivo"""
    try:
        n = decode_field(payload, _ADV_TYPE_NAME)
        return str(n[0], "utf-8") if n else ""
    except Exception as e:
        print(f"[BLE_ADV] Error decodificando nombre: {e}")
        return ""

def decode_services(payload):
    """Decodifica servicios anunciados"""
    try:
        services = []
        for u in decode_field(payload, _ADV_TYPE_UUID16_COMPLETE):
            services.append(bluetooth.UUID(struct.unpack("<h", u)[0]))
        for u in decode_field(payload, _ADV_TYPE_UUID32_COMPLETE):
            services.append(bluetooth.UUID(struct.unpack("<d", u)[0]))
        for u in decode_field(payload, _ADV_TYPE_UUID128_COMPLETE):
            services.append(bluetooth.UUID(u))
        return services
    except Exception as e:
        print(f"[BLE_ADV] Error decodificando servicios: {e}")
        return []