package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.PropertyName
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes

data class UserData(
    val documentName: String = "",
    val email: String = "",
    val name: String = "",
    @get:PropertyName("role")
    @set:PropertyName("role")
    private var roleString: String = "user",
    val clientDocName: String = "",
    val clientName: String = "", // Nuevo campo para el nombre del cliente
    @get:PropertyName("fcmToken")
    val fcmToken: String? = null
) {
    var role: UserRole
        get() = UserRole.fromString(roleString)
        set(value) { roleString = UserRole.toFirestoreValue(value) }

    fun isValid(): Boolean {
        return email.isNotBlank() &&
                name.isNotBlank() &&
                when (role) {
                    UserRole.ADMIN -> isValidAdminDocument()
                    UserRole.USER -> isValidUserDocument() && isValidClientDocument()
                }
    }

    private fun isValidAdminDocument(): Boolean {
        return documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.ADMIN)
    }

    private fun isValidUserDocument(): Boolean {
        return documentName.isEmpty() || documentName.startsWith(DocumentPrefixes.USER)
    }

    private fun isValidClientDocument(): Boolean {
        return clientDocName.startsWith(DocumentPrefixes.CLIENT)
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "documentName" to documentName,
            "email" to email,
            "name" to name,
            "role" to roleString,
            "clientDocName" to clientDocName,
            "clientName" to clientName,
            "fcmToken" to fcmToken
        )
    }

    companion object {
        fun createAdmin(
            email: String,
            name: String,
            fcmToken: String? = null
        ): UserData = UserData(
            email = email,
            name = name,
            roleString = "admin",
            fcmToken = fcmToken
        )

        fun createClientUser(
            email: String,
            name: String,
            clientDocName: String,
            clientName: String,
            fcmToken: String? = null
        ): UserData = UserData(
            email = email,
            name = name,
            roleString = "user",
            clientDocName = clientDocName,
            clientName = clientName,
            fcmToken = fcmToken
        )

        fun fromMap(map: Map<String, Any?>): UserData {
            return UserData(
                documentName = map["documentName"] as? String ?: "",
                email = map["email"] as? String ?: "",
                name = map["name"] as? String ?: "",
                roleString = map["role"] as? String ?: "user",
                clientDocName = map["clientDocName"] as? String ?: "",
                clientName = map["clientName"] as? String ?: "",
                fcmToken = map["fcmToken"] as? String
            )
        }
    }

    override fun toString(): String {
        return "UserData(documentName='$documentName', " +
                "email='$email', " +
                "name='$name', " +
                "role=${role.name}, " +
                "clientDocName='$clientDocName', " +
                "clientName='$clientName')" // No incluimos fcmToken por seguridad
    }
}