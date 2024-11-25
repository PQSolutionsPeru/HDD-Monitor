package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.domain.model.UserRole
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
    val adminAcceptDocName: String? = null,
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,
    val acceptedAt: String? = null,
    val finalizedAt: String? = null,
    val lastUpdate: String = LocalDateTime.now().format(DATE_FORMATTER),
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
            val now = LocalDateTime.now().format(DATE_FORMATTER)
            return Event(
                documentName = "",
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                panelName = panelName,
                title = title.trim(),
                text = text.trim(),
                date_time = now,
                status = EventStatus.STATUS_PROGRAMADO,
                type = type,
                createdByUserId = createdByUserId,
                createdByUserRole = createdByUserRole,
                isRead = false,
                lastUpdate = now
            )
        }
    }

    fun accept(acceptorDocName: String, isAdmin: Boolean): Event {
        val now = LocalDateTime.now().format(DATE_FORMATTER)
        return copy(
            status = EventStatus.STATUS_ACEPTADO,
            userAcceptDocName = if (!isAdmin) acceptorDocName else userAcceptDocName,
            adminAcceptDocName = if (isAdmin) acceptorDocName else adminAcceptDocName,
            isRead = false,
            acceptedAt = now,
            lastUpdate = now
        )
    }

    fun finalize(): Event {
        if (!isAceptado) return this
        val now = LocalDateTime.now().format(DATE_FORMATTER)
        return copy(
            status = EventStatus.STATUS_FINALIZADO,
            isRead = false,
            finalizedAt = now,
            lastUpdate = now
        )
    }

    fun reopen(): Event {
        if (!isFinalizado || !isAdmin) return this
        return copy(
            status = EventStatus.STATUS_PROGRAMADO,
            userAcceptDocName = null,
            isRead = false,
            lastUpdate = LocalDateTime.now().format(DATE_FORMATTER)
        )
    }

    fun markAsRead(): Event {
        return copy(
            isRead = true,
            lastUpdate = LocalDateTime.now().format(DATE_FORMATTER)
        )
    }

    val isProgramado: Boolean
        get() = status == EventStatus.STATUS_PROGRAMADO

    val isAceptado: Boolean
        get() = status == EventStatus.STATUS_ACEPTADO

    val isFinalizado: Boolean
        get() = status == EventStatus.STATUS_FINALIZADO

    val isEditable: Boolean
        get() = isProgramado || (isAdmin && isFinalizado)

    val dateTime: LocalDateTime?
        get() = try {
            LocalDateTime.parse(date_time, DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            null
        }

    private val isAdmin: Boolean
        get() = createdByUserRole == UserRole.ADMIN.toString()

    fun isValid(): Boolean {
        return title.isNotBlank() &&
                text.isNotBlank() &&
                date_time.isNotBlank() &&
                type?.isNotBlank() == true && // Modificar esta línea
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

    fun update(
        title: String,
        text: String,
        dateTime: LocalDateTime,
        panelDocName: String? = this.panelDocName,
        panelName: String? = this.panelName,
        type: String? = this.type
    ): Event? {
        if (!isProgramado && !(isAdmin && isFinalizado)) return null

        return copy(
            title = title.trim(),
            text = text.trim(),
            date_time = dateTime.format(DATE_FORMATTER),
            panelDocName = panelDocName,
            panelName = panelName,
            type = type,
            lastUpdate = LocalDateTime.now().format(DATE_FORMATTER)
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
            "createdByUserRole" to createdByUserRole,
            "isRead" to isRead,
            "acceptedAt" to acceptedAt,
            "finalizedAt" to finalizedAt,
            "lastUpdate" to lastUpdate
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
        append("isRead='$isRead', ")
        if (panelDocName != null) append("panelDocName='$panelDocName', ")
        if (panelName != null) append("panelName='$panelName', ")
        if (userAcceptDocName != null) append("userAcceptDocName='$userAcceptDocName', ")
        if (createdByUserId != null) append("createdByUserId='$createdByUserId', ")
        if (createdByUserRole != null) append("createdByUserRole='$createdByUserRole', ")
        if (acceptedAt != null) append("acceptedAt=$acceptedAt, ")
        if (finalizedAt != null) append("finalizedAt=$finalizedAt, ")
        append("lastUpdate=$lastUpdate")
        append(")")
    }
}