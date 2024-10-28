package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Notification(
    val documentName: String = "",
    val clientDocName: String = "",
    val panelDocName: String = "",
    val relayName: String = "",
    val message: String = "",
    val date_time: String = "",
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        fun createNew(
            clientDocName: String,
            panelDocName: String,
            relayName: String,
            message: String
        ): Notification {
            return Notification(
                documentName = "", // Se generará en el Repository
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                relayName = relayName,
                message = message.trim(),
                date_time = LocalDateTime.now().format(DATE_FORMATTER),
                isRead = false
            )
        }

        fun fromMap(map: Map<String, Any?>): Notification {
            return Notification(
                documentName = map["documentName"] as? String ?: "",
                clientDocName = map["clientDocName"] as? String ?: "",
                panelDocName = map["panelDocName"] as? String ?: "",
                relayName = map["relayName"] as? String ?: "",
                message = map["message"] as? String ?: "",
                date_time = map["date_time"] as? String ?: "",
                isRead = map["isRead"] as? Boolean ?: false
            )
        }
    }

    val dateTime: LocalDateTime?
        get() = try {
            LocalDateTime.parse(date_time, DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            null
        }

    fun isValid(): Boolean {
        return validateDocumentNames() &&
                message.isNotBlank() &&
                date_time.isNotBlank() &&
                relayName.isNotBlank()
    }

    private fun validateDocumentNames(): Boolean {
        return (documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.NOTIFICATION)) &&
                clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                panelDocName.startsWith(DocumentPrefixes.PANEL)
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "clientDocName" to clientDocName,
            "panelDocName" to panelDocName,
            "relayName" to relayName,
            "message" to message,
            "date_time" to date_time,
            "isRead" to isRead
        )
    }

    fun toLogString(): String = buildString {
        append("Notification(")
        append("documentName='$documentName', ")
        append("clientDocName='$clientDocName', ")
        append("panelDocName='$panelDocName', ")
        append("relayName='$relayName', ")
        append("message='${message.take(30)}${if (message.length > 30) "..." else ""}', ")
        append("date_time='$date_time', ")
        append("isRead=$isRead")
        append(")")
    }

    // Constructor para migración de datos antiguos
    fun fromLegacy(
        ID: String,
        ID_CLIENT: String,
        ID_PANEL: String,
        ID_RELAY: String,
        message: String,
        date_time: String,
        isRead: Boolean = false
    ): Notification {
        return Notification(
            documentName = if (ID.startsWith(DocumentPrefixes.NOTIFICATION)) ID else "${DocumentPrefixes.NOTIFICATION}$ID",
            clientDocName = if (ID_CLIENT.startsWith(DocumentPrefixes.CLIENT)) ID_CLIENT else "${DocumentPrefixes.CLIENT}$ID_CLIENT",
            panelDocName = if (ID_PANEL.startsWith(DocumentPrefixes.PANEL)) ID_PANEL else "${DocumentPrefixes.PANEL}$ID_PANEL",
            relayName = ID_RELAY,
            message = message,
            date_time = date_time,
            isRead = isRead
        )
    }

    // Función de utilidad para determinar si la notificación es reciente (menos de 24 horas)
    fun isRecent(): Boolean {
        val notificationDateTime = dateTime ?: return false
        val hoursAgo = java.time.Duration.between(notificationDateTime, LocalDateTime.now()).toHours()
        return hoursAgo < 24
    }

    // Función para obtener un timestamp para ordenamiento
    fun getTimestamp(): Long {
        return dateTime?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
    }
}