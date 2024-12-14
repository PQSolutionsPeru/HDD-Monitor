package com.pqsolutions.hdd_monitor.bluetooth

import java.util.UUID

object BleConstants {
    const val SCAN_PERIOD: Long = 10000 // 10 segundos
    const val CONNECTION_TIMEOUT: Long = 30000 // 30 segundos
    const val CONFIGURATION_TIMEOUT: Long = 180000 // 3 minutos
    const val MAX_RETRY_ATTEMPTS = 3
    const val MAX_RETRIES = 3
    const val WRITE_DELAY = 100L // ms
    const val MAX_CHUNK_SIZE = 18

    val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    object Status {
        const val OK = "OK"
        const val DISC = "DISC"
    }

    object ResponseKeys {
        const val STATUS = "status"
        const val MESSAGE = "message"
        const val IP = "ip"
    }

    object ResponseValues {
        const val RECEIVED = "received"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val WIFI_CONNECTED = "wifi_connected"
        const val WIFI_FAILED = "wifi_connection_failed"
        const val MQTT_FAILED = "mqtt_connection_failed"

    }
}