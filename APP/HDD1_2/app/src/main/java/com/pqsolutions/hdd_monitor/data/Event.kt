package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Event(
    val documentName: String = "",
    val clientDocName: String = "",
    val panelDocName: String? = null,
    val panelName: String? = null,
    val title: String = "",
    val text: String = "",
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = EventStatus.STATUS_PROGRAMADO,
    val date_time: String = "",
    val userAcceptDocName: String? = null,
    val type: String? = null,
    val createdByUserId: String? = null,
    val createdByUserRole: String? = null
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        fun createNew(
            clientDocName: String,
            panelDocName: String?,
            panelName: String?,
            title: String,
            text: String,
            dateTime: LocalDateTime,
            type: String,
            createdByUserId: String,
            createdByUserRole: String
        ): Event {
            return Event(
                documentName = "",
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                panelName = panelName,
                title = title.trim(),
                text = text.trim(),
                date_time = dateTime.format(DATE_FORMATTER),
                status = EventStatus.STATUS_PROGRAMADO,
                type = type,
                createdByUserId = createdByUserId,
                createdByUserRole = createdByUserRole
            )
        }

        fun fromMap(map: Map<String, Any?>): Event {
            return Event(
                documentName = map["documentName"] as? String ?: "",
                clientDocName = map["clientDocName"] as? String ?: "",
                panelDocName = map["panelDocName"] as? String,
                panelName = map["panelName"] as? String,
                title = map["title"] as? String ?: "",
                text = map["text"] as? String ?: "",
                status = map["status"] as? String ?: EventStatus.STATUS_PROGRAMADO,
                date_time = map["date_time"] as? String ?: "",
                userAcceptDocName = map["userAcceptDocName"] as? String,
                type = map["type"] as? String,
                createdByUserId = map["createdByUserId"] as? String,
                createdByUserRole = map["createdByUserRole"] as? String
            )
        }
    }

    // Propiedades y métodos computados
    val isProgramado: Boolean
        get() = status == EventStatus.STATUS_PROGRAMADO

    val isAceptado: Boolean
        get() = status == EventStatus.STATUS_ACEPTADO

    val isEditable: Boolean
        get() = isProgramado

    val dateTime: LocalDateTime?
        get() = try {
            LocalDateTime.parse(date_time, DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            null
        }

    fun isValid(): Boolean {
        return title.isNotBlank() &&
                text.isNotBlank() &&
                date_time.isNotBlank() &&
                type != null &&
                type.isNotBlank() &&
                EventStatus.isValidStatus(status) &&
                validateDocumentNames()
    }

    fun validateDocumentNames(): Boolean {
        return (documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.EVENT)) &&
                clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                (panelDocName == null || panelDocName.startsWith(DocumentPrefixes.PANEL)) &&
                (userAcceptDocName == null || userAcceptDocName.startsWith(DocumentPrefixes.USER))
    }

    fun getFormattedDateTime(): String = date_time

    fun accept(userDocName: String): Event {
        return copy(
            status = EventStatus.STATUS_ACEPTADO,
            userAcceptDocName = userDocName
        )
    }

    fun update(
        title: String,
        text: String,
        dateTime: LocalDateTime,
        panelDocName: String? = this.panelDocName,
        panelName: String? = this.panelName,
        type: String? = this.type
    ): Event? {
        if (!isProgramado) return null

        return copy(
            title = title.trim(),
            text = text.trim(),
            date_time = dateTime.format(DATE_FORMATTER),
            panelDocName = panelDocName,
            panelName = panelName,
            type = type,
            documentName = this.documentName,
            clientDocName = this.clientDocName,
            status = this.status,
            userAcceptDocName = this.userAcceptDocName,
            createdByUserId = this.createdByUserId,
            createdByUserRole = this.createdByUserRole
        )
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "clientDocName" to clientDocName,
            "panelDocName" to panelDocName,
            "panelName" to panelName,
            "title" to title,
            "text" to text,
            "status" to status,
            "date_time" to date_time,
            "userAcceptDocName" to userAcceptDocName,
            "type" to type,
            "createdByUserId" to createdByUserId,
            "createdByUserRole" to createdByUserRole
        )
    }

    fun toLogString(): String = buildString {
        append("Event(")
        append("documentName='$documentName', ")
        append("clientDocName='$clientDocName', ")
        append("title='$title', ")
        append("status='$status', ")
        append("isProgramado=$isProgramado, ")
        append("date_time='$date_time', ")
        append("type='$type', ")
        if (panelDocName != null) append("panelDocName='$panelDocName', ")
        if (panelName != null) append("panelName='$panelName', ")
        if (userAcceptDocName != null) append("userAcceptDocName='$userAcceptDocName', ")
        if (createdByUserId != null) append("createdByUserId='$createdByUserId', ")
        if (createdByUserRole != null) append("createdByUserRole='$createdByUserRole', ")
        append(")")
    }
}