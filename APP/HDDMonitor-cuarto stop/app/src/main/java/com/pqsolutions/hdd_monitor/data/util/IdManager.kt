package com.pqsolutions.hdd_monitor.data.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object IdManager {
    // Prefijos para tipos de documentos
    const val PREFIX_ADMIN = "admin"
    private const val PREFIX_USER = "user"
    private const val PREFIX_EVENT = "event"
    private const val PREFIX_NOTIFICATION = "notification"
    private const val PREFIX_PANEL = "panel"
    private const val PREFIX_CLIENT = "client"

    // Formato de timestamp para nombres de documentos
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    // Longitud del componente aleatorio
    private const val RANDOM_LENGTH = 6

    /**
     * Genera un nombre de documento para un cliente
     */
    fun generateClientDocumentName(): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_CLIENT}_${timestamp}_$random"
    }

    /**
     * Genera un nombre de documento para un administrador
     */
    fun generateAdminDocumentName(): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_ADMIN}_${timestamp}_$random"
    }

    /**
     * Genera un nombre de documento para un usuario dentro de un cliente
     */
    fun generateUserDocumentName(clientDocName: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_USER}_${clientDocName}_${timestamp}_$random"
    }

    /**
     * Genera un nombre de documento para un evento de un cliente
     */
    fun generateEventDocumentName(clientDocName: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_EVENT}_${clientDocName}_${timestamp}_$random"
    }

    /**
     * Genera un nombre de documento para una notificación de un cliente
     */
    fun generateNotificationDocumentName(clientDocName: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_NOTIFICATION}_${clientDocName}_${timestamp}_$random"
    }

    /**
     * Genera un nombre de documento para un panel de un cliente
     */
    fun generatePanelDocumentName(clientDocName: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val random = generateRandomString(RANDOM_LENGTH)
        return "${PREFIX_PANEL}_${clientDocName}_${timestamp}_$random"
    }

    /**
     * Valida el formato de un nombre de documento
     */
    fun validateDocumentName(documentName: String, type: DocumentType): Boolean {
        val pattern = when (type) {
            DocumentType.ADMIN -> """^${PREFIX_ADMIN}_\d{14}_[A-Z0-9]{6}$"""
            DocumentType.USER -> """^${PREFIX_USER}_${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6}_\d{14}_[A-Z0-9]{6}$"""
            DocumentType.CLIENT -> """^${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6}$"""
            DocumentType.EVENT -> """^${PREFIX_EVENT}_${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6}_\d{14}_[A-Z0-9]{6}$"""
            DocumentType.NOTIFICATION -> """^${PREFIX_NOTIFICATION}_${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6}_\d{14}_[A-Z0-9]{6}$"""
            DocumentType.PANEL -> """^${PREFIX_PANEL}_${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6}_\d{14}_[A-Z0-9]{6}$"""
        }

        return Regex(pattern).matches(documentName) || isLegacyDocumentName(documentName)
    }

    /**
     * Verifica si es un nombre de documento legacy
     */
    private fun isLegacyDocumentName(documentName: String): Boolean {
        val legacyPatterns = mapOf(
            "client" to """^client_\d+$""",
            "admin" to """^[A-Za-z0-9]{20}$""",
            "user" to """^[A-Za-z0-9]{20}$""",
            "event" to """^event_\d+$""",
            "notification" to """^notification_\d+$""",
            "panel" to """^panel_\d+$"""
        )

        return legacyPatterns.any { (_, pattern) ->
            Regex(pattern).matches(documentName)
        }
    }

    /**
     * Extrae el nombre del documento del cliente de un nombre de documento secundario
     */
    fun extractClientDocumentName(documentName: String): String? {
        val pattern = """^(?:${PREFIX_EVENT}|${PREFIX_NOTIFICATION}|${PREFIX_PANEL})_(${PREFIX_CLIENT}_\d{14}_[A-Z0-9]{6})""".toRegex()
        return pattern.find(documentName)?.groupValues?.get(1)
    }

    /**
     * Genera una cadena aleatoria de longitud específica
     */
    private fun generateRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}

/**
 * Enumeración de tipos de documentos
 */
enum class DocumentType {
    ADMIN,
    USER,
    CLIENT,
    EVENT,
    NOTIFICATION,
    PANEL
}