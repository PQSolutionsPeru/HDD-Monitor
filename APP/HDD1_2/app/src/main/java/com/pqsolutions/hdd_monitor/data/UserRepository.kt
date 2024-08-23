package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getUsers(): Result<List<UserData>> = runCatching {
        val users = mutableListOf<UserData>()

        // Obtener administradores
        val adminsQuery = firestore.collection("hdd-monitor/accounts/admins").get().await()
        for (adminDoc in adminsQuery.documents) {
            users.add(UserData(
                id = adminDoc.id,
                email = adminDoc.getString("email") ?: "",
                name = adminDoc.getString("name") ?: "",
                role = UserRole.ADMIN,
                clientId = ""
            ))
        }

        // Obtener usuarios normales
        val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
        for (clientDoc in clientsQuery.documents) {
            val usersQuery = clientDoc.reference.collection("users").get().await()
            for (userDoc in usersQuery.documents) {
                users.add(UserData(
                    id = userDoc.id,
                    email = userDoc.getString("email") ?: "",
                    name = userDoc.getString("name") ?: "",
                    role = UserRole.USER,
                    clientId = clientDoc.id
                ))
            }
        }

        users
    }

    suspend fun createUser(userData: UserData): Result<Unit> = runCatching {
        if (userData.role == UserRole.ADMIN) {
            firestore.collection("hdd-monitor/accounts/admins").document(userData.id).set(userData).await()
        } else {
            firestore.collection("hdd-monitor/accounts/clients/${userData.clientId}/users").document(userData.id).set(userData).await()
        }
    }

    suspend fun updateUser(userData: UserData): Result<Unit> = runCatching {
        if (userData.role == UserRole.ADMIN) {
            firestore.collection("hdd-monitor/accounts/admins").document(userData.id).set(userData).await()
        } else {
            firestore.collection("hdd-monitor/accounts/clients/${userData.clientId}/users").document(userData.id).set(userData).await()
        }
    }

    suspend fun deleteUser(userId: String, role: UserRole, clientId: String? = null): Result<Unit> = runCatching {
        if (role == UserRole.ADMIN) {
            firestore.collection("hdd-monitor/accounts/admins").document(userId).delete().await()
        } else {
            clientId?.let {
                firestore.collection("hdd-monitor/accounts/clients/$it/users").document(userId).delete().await()
            } ?: throw IllegalArgumentException("ClientId is required for deleting a user")
        }
    }
}