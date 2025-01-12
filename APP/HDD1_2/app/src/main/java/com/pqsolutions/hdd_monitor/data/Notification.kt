package com.pqsolutions.hdd_monitor.data

import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Notification(
    val documentName: String = "",
    val clientDocName: String = "",
    val panelDocName: String = "",
    val relayName: String = "",
    val message: String = "",
    val date_time: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val eventId: String? = null,
    val eventType: String? = null,
    val status: String? = null,
    val panelName: String? = null,
    val readByAdmin: Boolean = false,
    val readByUser: Boolean = false
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")

        fun createNew(
            clientDocName: String,
            panelDocName: String = "",
            relayName: String = "",
            message: String,
            eventId: String? = null,
            eventType: String? = null,
            status: String? = null,
            panelName: String? = null
        ): Notification {
            val now = LocalDateTime.now()
            return Notification(
                documentName = "",
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                relayName = relayName,
                message = message.trim(),
                date_time = now.format(DATE_FORMATTER),
                timestamp = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                eventId = eventId,
                eventType = eventType,
                status = status,
                panelName = panelName
            )
        }

        fun fromMap(map: Map<String, Any?>): Notification {
            val dateTimeStr = map["date_time"] as? String ?: ""
            val timestamp = when (val ts = map["timestamp"]) {
                is Long -> ts
                is Number -> ts.toLong()
                else -> try {
                    LocalDateTime.parse(dateTimeStr, DATE_FORMATTER)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            }

            return Notification(
                documentName = map["documentName"] as? String ?: "",
                clientDocName = map["clientDocName"] as? String ?: "",
                panelDocName = map["panelDocName"] as? String ?: "",
                relayName = map["relayName"] as? String ?: "",
                message = map["message"] as? String ?: "",
                date_time = dateTimeStr,
                timestamp = timestamp,
                isRead = map["isRead"] as? Boolean ?: false,
                eventId = map["eventId"] as? String,
                eventType = map["eventType"] as? String,
                status = map["status"] as? String,
                panelName = map["panelName"] as? String,
                readByAdmin = map["readByAdmin"] as? Boolean ?: false,
                readByUser = map["readByUser"] as? Boolean ?: false
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
                (isRelayNotification() || isEventNotification())
    }

    private fun validateDocumentNames(): Boolean {
        return (documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.NOTIFICATION)) &&
                clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                (panelDocName.isEmpty() || panelDocName.startsWith(DocumentPrefixes.PANEL))
    }

    fun isEventNotification(): Boolean {
        return eventId != null && eventType != null
    }

    fun isRelayNotification(): Boolean {
        return relayName.isNotBlank() && panelDocName.isNotBlank()
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "clientDocName" to clientDocName,
            "panelDocName" to panelDocName,
            "relayName" to relayName,
            "message" to message,
            "date_time" to date_time,
            "timestamp" to timestamp,
            "isRead" to isRead,
            "eventId" to eventId,
            "eventType" to eventType,
            "status" to status,
            "panelName" to panelName,
            "readByAdmin" to readByAdmin,
            "readByUser" to readByUser
        ).filterValues { it != null }
    }

    fun toLogString(): String = buildString {
        append("Notification(")
        append("documentName='$documentName', ")
        append("clientDocName='$clientDocName', ")
        if (isEventNotification()) {
            append("eventId='$eventId', ")
            append("eventType='$eventType', ")
            append("status='$status', ")
        } else {
            append("panelDocName='$panelDocName', ")
            append("relayName='$relayName', ")
        }
        append("message='${message.take(30)}${if (message.length > 30) "..." else ""}', ")
        append("date_time='$date_time', ")
        append("timestamp=$timestamp")
        append(")")
    }

    fun isRecent(): Boolean {
        val notificationDateTime = dateTime ?: return false
        val hoursAgo = java.time.Duration.between(notificationDateTime, LocalDateTime.now()).toHours()
        return hoursAgo < 24
    }
}