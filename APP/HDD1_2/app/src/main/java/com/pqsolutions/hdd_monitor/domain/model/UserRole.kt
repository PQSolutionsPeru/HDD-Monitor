package com.pqsolutions.hdd_monitor.domain.model

import com.google.firebase.firestore.PropertyName

enum class UserRole {
    @PropertyName("admin")
    ADMIN,

    @PropertyName("user")
    USER;

    companion object {
        fun fromString(value: String?): UserRole {
            return when (value?.lowercase()) {
                "admin" -> ADMIN
                "user" -> USER
                null -> USER
                else -> try {
                    valueOf(value.uppercase())
                } catch (e: IllegalArgumentException) {
                    USER
                }
            }
        }

        fun toFirestoreValue(role: UserRole): String {
            return when (role) {
                ADMIN -> "admin"
                USER -> "user"
            }
        }
    }

    val isAdmin: Boolean
        get() = this == ADMIN

    val canManageEvents: Boolean
        get() = this == ADMIN

    val canViewAllClients: Boolean
        get() = this == ADMIN

    override fun toString(): String {
        return when (this) {
            ADMIN -> "Administrador"
            USER -> "Usuario"
        }
    }
}