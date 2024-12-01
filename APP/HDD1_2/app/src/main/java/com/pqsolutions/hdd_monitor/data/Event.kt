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
    val title: String = "",
    val text: String = "",
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = EventStatus.STATUS_PROGRAMADO,
    val date_time: String = "",
    val lastUpdate: String = "",
    val type: String? = null,
    val createdByUserId: String? = null,
    val createdByUserRole: String? = null,
    val panelDocName: String? = null,
    val panelName: String? = null,
    val userAcceptDocName: String? = null,
    val adminAcceptDocName: String? = null,
    val acceptedAt: String? = null,
    val finalizedAt: String? = null,
    val isRead: Boolean = false,
    val needsApproval: Boolean = true
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
                date_time = dateTime.format(DATE_FORMATTER),
                lastUpdate = now,
                status = EventStatus.STATUS_PROGRAMADO,
                type = type,
                createdByUserId = createdByUserId,
                createdByUserRole = createdByUserRole,
                isRead = false,
                needsApproval = true
            )
        }

        fun fromMap(map: Map<String, Any?>): Event {
            return Event(
                documentName = map["documentName"] as? String ?: "",
                clientDocName = map["clientDocName"] as? String ?: "",
                title = map["title"] as? String ?: "",
                text = map["text"] as? String ?: "",
                status = map["status"] as? String ?: EventStatus.STATUS_PROGRAMADO,
                date_time = map["date_time"] as? String ?: "",
                lastUpdate = map["lastUpdate"] as? String ?: "",
                type = map["type"] as? String,
                createdByUserId = map["createdByUserId"] as? String,
                createdByUserRole = map["createdByUserRole"] as? String,
                panelDocName = map["panelDocName"] as? String,
                panelName = map["panelName"] as? String,
                userAcceptDocName = map["userAcceptDocName"] as? String,
                adminAcceptDocName = map["adminAcceptDocName"] as? String,
                acceptedAt = map["acceptedAt"] as? String,
                finalizedAt = map["finalizedAt"] as? String,
                isRead = map["isRead"] as? Boolean ?: false,
                needsApproval = map["needsApproval"] as? Boolean ?: true
            )
        }
    }

    // Propiedades computadas para control de estado
    val isProgramado: Boolean
        get() = status == EventStatus.STATUS_PROGRAMADO

    val needsAdminApproval: Boolean
        get() = createdByUserRole == UserRole.USER.toString() &&
                isProgramado &&
                adminAcceptDocName == null &&
                needsApproval

    val needsUserApproval: Boolean
        get() = createdByUserRole == UserRole.ADMIN.toString() &&
                isProgramado &&
                userAcceptDocName == null &&
                needsApproval

    val isAceptado: Boolean
        get() = status == EventStatus.STATUS_ACEPTADO && when (createdByUserRole) {
            UserRole.USER.toString() -> adminAcceptDocName != null
            UserRole.ADMIN.toString() -> userAcceptDocName != null
            else -> false
        }

    val isFinalizado: Boolean
        get() = status == EventStatus.STATUS_FINALIZADO

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
                (userAcceptDocName == null || userAcceptDocName.startsWith(DocumentPrefixes.USER)) &&
                (adminAcceptDocName == null || adminAcceptDocName.startsWith(DocumentPrefixes.ADMIN))
    }

    fun getFormattedDateTime(): String = date_time

    fun accept(userDocName: String, isAdmin: Boolean, timestamp: String = LocalDateTime.now().format(DATE_FORMATTER)): Event {
        return when {
            // Si un admin acepta un evento creado por usuario
            isAdmin && createdByUserRole == UserRole.USER.toString() && needsAdminApproval -> copy(
                status = EventStatus.STATUS_ACEPTADO,
                lastUpdate = timestamp,
                acceptedAt = timestamp,
                adminAcceptDocName = userDocName,
                isRead = false
            )
            // Si un usuario acepta un evento creado por admin
            !isAdmin && createdByUserRole == UserRole.ADMIN.toString() && needsUserApproval -> copy(
                status = EventStatus.STATUS_ACEPTADO,
                lastUpdate = timestamp,
                acceptedAt = timestamp,
                userAcceptDocName = userDocName,
                isRead = false
            )
            else -> this
        }
    }

    fun finalize(timestamp: String = LocalDateTime.now().format(DATE_FORMATTER)): Event {
        return if (isAceptado) {
            copy(
                status = EventStatus.STATUS_FINALIZADO,
                lastUpdate = timestamp,
                finalizedAt = timestamp,
                isRead = false
            )
        } else this
    }

    fun markAsRead(): Event {
        return copy(isRead = true)
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

        val now = LocalDateTime.now().format(DATE_FORMATTER)
        return copy(
            title = title.trim(),
            text = text.trim(),
            date_time = dateTime.format(DATE_FORMATTER),
            lastUpdate = now,
            panelDocName = panelDocName,
            panelName = panelName,
            type = type,
            isRead = false
        )
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "clientDocName" to clientDocName,
            "title" to title,
            "text" to text,
            "status" to status,
            "date_time" to date_time,
            "lastUpdate" to lastUpdate,
            "type" to type,
            "createdByUserId" to createdByUserId,
            "createdByUserRole" to createdByUserRole,
            "panelDocName" to panelDocName,
            "panelName" to panelName,
            "userAcceptDocName" to userAcceptDocName,
            "adminAcceptDocName" to adminAcceptDocName,
            "acceptedAt" to acceptedAt,
            "finalizedAt" to finalizedAt,
            "isRead" to isRead,
            "needsApproval" to needsApproval
        ).filterValues { it != null }
    }

    fun toLogString(): String = buildString {
        append("Event(")
        append("documentName='$documentName', ")
        append("clientDocName='$clientDocName', ")
        append("title='$title', ")
        append("status='$status', ")
        append("isProgramado=$isProgramado, ")
        append("date_time='$date_time', ")
        append("lastUpdate='$lastUpdate', ")
        append("type='$type', ")
        if (panelDocName != null) append("panelDocName='$panelDocName', ")
        if (panelName != null) append("panelName='$panelName', ")
        if (userAcceptDocName != null) append("userAcceptDocName='$userAcceptDocName', ")
        if (adminAcceptDocName != null) append("adminAcceptDocName='$adminAcceptDocName', ")
        if (acceptedAt != null) append("acceptedAt='$acceptedAt', ")
        if (finalizedAt != null) append("finalizedAt='$finalizedAt', ")
        append("isRead=$isRead, ")
        append("needsApproval=$needsApproval, ")
        if (createdByUserId != null) append("createdByUserId='$createdByUserId', ")
        if (createdByUserRole != null) append("createdByUserRole='$createdByUserRole', ")
        append(")")
    }
}