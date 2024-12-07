package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.domain.model.Stateable
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import com.pqsolutions.hdd_monitor.util.Constants.Status
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Panel(
    val documentName: String = "",
    val name: String = "",
    val location: String = "",
    val SSID: String = "",
    @get:PropertyName("SSID_CON")
    @set:PropertyName("SSID_CON")
    var SSID_CON: String? = null,
    @get:PropertyName("SSID_PW")
    @set:PropertyName("SSID_PW")
    var SSID_PW: String = "",
    @get:PropertyName("ESP32_IP")
    @set:PropertyName("ESP32_IP")
    var ESP32_IP: String = "",
    val clientName: String = "",
    val relays: List<Relay> = listOf(
        Relay(name = "Alarma"),
        Relay(name = "Problema"),
        Relay(name = "Supervision")
    ),
    @get:PropertyName("overallStatus")
    var overallStatus: String = Status.OK
) : Stateable {

    override val status: String
        get() = overallStatus

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        fun createNew(
            name: String,
            location: String,
            ssid: String,
            ssidPw: String,
            esp32Ip: String,
            clientName: String
        ): Panel {
            return Panel(
                documentName = "",
                name = name.trim(),
                location = location.trim(),
                SSID = ssid.trim(),
                SSID_PW = ssidPw.trim(),
                ESP32_IP = esp32Ip.trim(),
                clientName = clientName,
                SSID_CON = null,
                relays = listOf(
                    Relay(name = "Alarma"),
                    Relay(name = "Problema"),
                    Relay(name = "Supervision")
                ),
                overallStatus = Status.OK
            )
        }

        fun fromMap(map: Map<String, Any?>): Panel {
            return Panel(
                documentName = map["documentName"] as? String ?: "",
                name = map["name"] as? String ?: "",
                location = map["location"] as? String ?: "",
                SSID = map["SSID"] as? String ?: "",
                SSID_CON = map["SSID_CON"] as? String,
                SSID_PW = map["SSID_PW"] as? String ?: "",
                ESP32_IP = map["ESP32_IP"] as? String ?: "",
                clientName = map["clientDocName"] as? String ?: "",
                relays = (map["relays"] as? List<*>)?.mapNotNull {
                    (it as? Map<*, *>)?.let { relayMap ->
                        Relay.fromMap(relayMap.mapKeys { entry -> entry.key.toString() })
                    }
                } ?: defaultRelays(),
                overallStatus = map["overallStatus"] as? String ?: Status.OK
            )
        }

        private fun defaultRelays() = listOf(
            Relay(name = "Alarma"),
            Relay(name = "Problema"),
            Relay(name = "Supervision")
        )
    }

    fun isValid(): Boolean {
        return name.isNotBlank() &&
                location.isNotBlank() &&
                SSID.isNotBlank() &&
                SSID_PW.isNotBlank() &&
                clientName.isNotBlank() &&
                relays.isNotEmpty() &&
                relays.all { it.isValid() }
    }

    private fun validateDocumentNames(): Boolean {
        return documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.PANEL)
        // Removida la validación de clientName ya que ahora es el nombre real
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "name" to name,
            "location" to location,
            "SSID" to SSID,
            "SSID_CON" to SSID_CON,
            "SSID_PW" to SSID_PW,
            "ESP32_IP" to ESP32_IP,
            "clientDocName" to clientName,
            "relays" to relays.map { it.toMap() },
            "overallStatus" to overallStatus
        )
    }

    fun updateOverallStatus() {
        overallStatus = when {
            relays.any { it.status == Status.DISC } -> Status.DISC
            else -> Status.OK
        }
    }

    fun toLogString(): String = buildString {
        append("Panel(")
        append("documentName='$documentName', ")
        append("name='$name', ")
        append("location='$location', ")
        append("ESP32_IP='$ESP32_IP', ")
        append("SSID_CON=${SSID_CON ?: "null"}, ")
        append("clientDocName='$clientName', ")
        append("status='$overallStatus', ")
        append("relays=${relays.size}")
        append(")")
    }

    fun getConnectionStatus(): String {
        return SSID_CON ?: Status.DISC
    }

    fun isConnected(): Boolean {
        return SSID_CON == Status.OK
    }

    override fun canTransitionTo(newStatus: String): Boolean {
        return when (status) {
            Status.OK -> newStatus == Status.DISC
            Status.DISC -> newStatus == Status.OK
            else -> false
        }
    }
}

data class Relay(
    val name: String = "",
    @get:PropertyName("status")
    override val status: String = Status.OK,
    val date_time: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
) : Stateable {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        fun fromMap(map: Map<String, Any?>): Relay {
            return Relay(
                name = map["name"] as? String ?: "",
                status = map["status"] as? String ?: Status.OK,
                date_time = map["date_time"] as? String ?:
                LocalDateTime.now().format(DATE_FORMATTER)
            )
        }
    }

    fun isValid(): Boolean {
        return name.isNotBlank() &&
                status in listOf(Status.OK, Status.DISC)
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "status" to status,
            "date_time" to date_time
        )
    }

    override fun canTransitionTo(newStatus: String): Boolean {
        return when (status) {
            Status.OK -> newStatus == Status.DISC
            Status.DISC -> newStatus == Status.OK
            else -> false
        }
    }

    fun withUpdatedStatus(newStatus: String): Relay {
        return if (canTransitionTo(newStatus)) {
            copy(
                status = newStatus,
                date_time = LocalDateTime.now().format(DATE_FORMATTER)
            )
        } else {
            this
        }
    }

    fun withUpdatedDateTime(): Relay {
        return copy(
            date_time = LocalDateTime.now().format(DATE_FORMATTER)
        )
    }

    override fun toString(): String = "$name: $status ($date_time)"
}