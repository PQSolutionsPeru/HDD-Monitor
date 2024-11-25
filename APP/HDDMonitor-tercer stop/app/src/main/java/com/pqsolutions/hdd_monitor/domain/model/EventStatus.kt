package com.pqsolutions.hdd_monitor.domain.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.util.Constants.Status
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EventStatus {
    // Constantes para estados
    const val STATUS_PROGRAMADO = Status.STATUS_PROGRAMADO
    const val STATUS_ACEPTADO = Status.STATUS_ACEPTADO
    const val STATUS_FINALIZADO = "FINALIZADO"

    // Formatter para fechas
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    // Función para validar estados
    fun isValidStatus(status: String): Boolean =
        status.uppercase() in listOf(STATUS_PROGRAMADO, STATUS_ACEPTADO, STATUS_FINALIZADO)

    // Función para normalizar estados
    fun normalize(status: String): String =
        when (status.uppercase()) {
            STATUS_PROGRAMADO -> STATUS_PROGRAMADO
            STATUS_ACEPTADO -> STATUS_ACEPTADO
            STATUS_FINALIZADO -> STATUS_FINALIZADO
            else -> STATUS_PROGRAMADO
        }

    // Función para obtener color según estado
    fun getStatusColor(status: String): StatusColor =
        when (normalize(status)) {
            STATUS_PROGRAMADO -> StatusColor.Programado
            STATUS_ACEPTADO -> StatusColor.Aceptado
            STATUS_FINALIZADO -> StatusColor.Finalizado
            else -> StatusColor.Default
        }

    // Función para validar transición de estados
    fun isValidTransition(currentStatus: String, newStatus: String, isAdmin: Boolean = false): Boolean {
        if (!isValidStatus(currentStatus) || !isValidStatus(newStatus)) return false

        return when (normalize(currentStatus)) {
            STATUS_PROGRAMADO -> true // Puede transicionar a cualquier estado
            STATUS_ACEPTADO -> {
                // Puede pasar a FINALIZADO o volver a PROGRAMADO si es admin
                newStatus == STATUS_FINALIZADO || (isAdmin && newStatus == STATUS_PROGRAMADO)
            }
            STATUS_FINALIZADO -> {
                // Solo el admin puede reabrir el evento
                isAdmin && newStatus != STATUS_FINALIZADO
            }
            else -> false
        }
    }

    // Función para obtener mensaje de estado
    fun getStatusMessage(status: String): String =
        when (normalize(status)) {
            STATUS_PROGRAMADO -> "Programado"
            STATUS_ACEPTADO -> "Aceptado"
            STATUS_FINALIZADO -> "Finalizado"
            else -> "Estado desconocido"
        }

    // Función para verificar si un estado permite edición
    fun isEditable(status: String, isAdmin: Boolean = false): Boolean =
        when (normalize(status)) {
            STATUS_PROGRAMADO -> true
            STATUS_ACEPTADO -> isAdmin // Solo admin puede editar eventos aceptados
            STATUS_FINALIZADO -> isAdmin // Solo admin puede editar eventos finalizados
            else -> false
        }

    // Función para verificar si un estado puede ser eliminado
    fun isDeletable(status: String): Boolean =
        normalize(status) == STATUS_PROGRAMADO

    // Función para obtener siguiente estado válido
    fun getNextStatus(currentStatus: String): String? =
        when (normalize(currentStatus)) {
            STATUS_PROGRAMADO -> STATUS_ACEPTADO
            STATUS_ACEPTADO -> STATUS_FINALIZADO
            else -> null
        }

    // Clase para manejar transiciones de estado con metadata
    data class StatusTransition(
        val fromStatus: String,
        val toStatus: String,
        @get:PropertyName("timestamp")
        val timestamp: String = LocalDateTime.now().format(DATE_FORMATTER),
        @get:PropertyName("userDocName")
        val userDocName: String? = null,
        @get:PropertyName("isAdminAction")
        val isAdminAction: Boolean = false
    ) {
        fun isValid(): Boolean =
            isValidStatus(fromStatus) &&
                    isValidStatus(toStatus) &&
                    isValidTransition(fromStatus, toStatus, isAdminAction)

        override fun toString(): String =
            "Transición de $fromStatus a $toStatus por $userDocName en $timestamp"
    }
}

// Colores para los diferentes estados
enum class StatusColor(val value: Long, val composableColor: Color) {
    Programado(0xFFE53935, Color(0xFFE53935)), // Rojo
    Aceptado(0xFF2196F3, Color(0xFF2196F3)),  // Azul
    Finalizado(0xFF4CAF50, Color(0xFF4CAF50)), // Verde
    Default(0xFF757575, Color(0xFF757575));    // Gris

    companion object {
        fun fromStatus(status: String): StatusColor =
            EventStatus.getStatusColor(status)
    }
}

// Extensiones de String para estados
fun String.toEventStatus(): String = EventStatus.normalize(this)
fun String.isValidEventStatus(): Boolean = EventStatus.isValidStatus(this)
fun String.getStatusColor(): StatusColor = EventStatus.getStatusColor(this)
fun String.isEditableStatus(isAdmin: Boolean = false): Boolean = EventStatus.isEditable(this, isAdmin)
fun String.getStatusMessage(): String = EventStatus.getStatusMessage(this)
fun String.isDeletableStatus(): Boolean = EventStatus.isDeletable(this)