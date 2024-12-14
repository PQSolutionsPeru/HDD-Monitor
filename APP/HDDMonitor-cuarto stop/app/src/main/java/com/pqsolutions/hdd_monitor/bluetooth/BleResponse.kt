package com.pqsolutions.hdd_monitor.bluetooth

data class BleResponse(
    val status: String,
    val message: String? = null,
    val ip: String? = null
) {
    companion object {
        fun parse(data: String): BleResponse? {
            return try {
                val parts = data.split(",").associate {
                    val (key, value) = it.split(":")
                    key.trim() to value.trim()
                }

                BleResponse(
                    status = parts["status"] ?: return null,
                    message = parts["message"],
                    ip = parts["ip"]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}