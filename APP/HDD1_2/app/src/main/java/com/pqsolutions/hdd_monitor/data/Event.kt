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
    val panelName: String? = null, // Nuevo campo
    val title: String = "",
    val text: String = "",
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = EventStatus.STATUS_PROGRAMADO,
    val date_time: String = "",
    val userAcceptDocName: String? = null
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        fun createNew(
            clientDocName: String,
            panelDocName: String?,
            panelName: String?, // Nuevo parámetro
            title: String,
            text: String,
            dateTime: LocalDateTime
        ): Event {
            return Event(
                documentName = "",  // Se generará en el Repository
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                panelName = panelName,
                title = title.trim(),
                text = text.trim(),
                date_time = dateTime.format(DATE_FORMATTER),
                status = EventStatus.STATUS_PROGRAMADO
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
                userAcceptDocName = map["userAcceptDocName"] as? String
            )
        }
    }

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
        panelName: String? = this.panelName // Nuevo parámetro
    ): Event? {
        if (!isProgramado) return null

        return copy(
            title = title.trim(),
            text = text.trim(),
            date_time = dateTime.format(DATE_FORMATTER),
            panelDocName = panelDocName,
            panelName = panelName
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
            "userAcceptDocName" to userAcceptDocName
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
        if (panelDocName != null) append("panelDocName='$panelDocName', ")
        if (panelName != null) append("panelName='$panelName', ")
        if (userAcceptDocName != null) append("userAcceptDocName='$userAcceptDocName', ")
        append(")")
    }

    // Constructor para migración de datos antiguos
    fun fromLegacy(
        ID: String,
        ID_CLIENT: String,
        ID_PANEL: String?,
        title: String,
        text: String,
        status: String,
        date_time: String,
        ID_USER_ACCEPT: String?
    ): Event {
        return Event(
            documentName = if (ID.startsWith(DocumentPrefixes.EVENT)) ID else "${DocumentPrefixes.EVENT}$ID",
            clientDocName = if (ID_CLIENT.startsWith(DocumentPrefixes.CLIENT)) ID_CLIENT else "${DocumentPrefixes.CLIENT}$ID_CLIENT",
            panelDocName = ID_PANEL?.let { if (it.startsWith(DocumentPrefixes.PANEL)) it else "${DocumentPrefixes.PANEL}$it" },
            title = title,
            text = text,
            status = status,
            date_time = date_time,
            userAcceptDocName = ID_USER_ACCEPT?.let { if (it.startsWith(DocumentPrefixes.USER)) it else "${DocumentPrefixes.USER}$it" }
        )
    }
}