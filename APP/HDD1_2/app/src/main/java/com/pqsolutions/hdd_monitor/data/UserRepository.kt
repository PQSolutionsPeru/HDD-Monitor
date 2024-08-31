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

    suspend fun createUser(user: UserData): Result<Unit> = runCatching {
        val collectionPath = if (user.role == UserRole.ADMIN) {
            "hdd-monitor/accounts/admins"
        } else {
            "hdd-monitor/accounts/clients/${user.clientId}/users"
        }
        firestore.collection(collectionPath)
            .document(user.id)
            .set(user)
            .await()
    }

    suspend fun updateUser(user: UserData): Result<Unit> = runCatching {
        val collectionPath = if (user.role == UserRole.ADMIN) {
            "hdd-monitor/accounts/admins"
        } else {
            "hdd-monitor/accounts/clients/${user.clientId}/users"
        }
        firestore.collection(collectionPath)
            .document(user.id)
            .set(user)
            .await()
    }

    suspend fun deleteUser(userId: String, clientId: String? = null): Result<Unit> = runCatching {
        val collectionPath = if (clientId != null) {
            "hdd-monitor/accounts/clients/$clientId/users"
        } else {
            "hdd-monitor/accounts/admins"
        }
        firestore.collection(collectionPath)
            .document(userId)
            .delete()
            .await()
    }
}
