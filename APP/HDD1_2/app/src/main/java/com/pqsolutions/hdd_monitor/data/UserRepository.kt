package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun getUsers(): Result<List<UserData>> = withContext(Dispatchers.IO) {
        try {
            val users = mutableListOf<UserData>()

            // Obtener administradores
            val adminsQuery = firestore.collection("hdd-monitor/accounts/admins").get().await()
            users.addAll(adminsQuery.documents.mapNotNull { adminDoc ->
                adminDoc.toUserData()?.copy(role = UserRole.ADMIN, clientId = "")
            })

            // Obtener usuarios de clientes
            val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
            clientsQuery.documents.forEach { clientDoc ->
                val usersQuery = clientDoc.reference.collection("users").get().await()
                users.addAll(usersQuery.documents.mapNotNull { userDoc ->
                    userDoc.toUserData()?.copy(role = UserRole.USER, clientId = clientDoc.id)
                })
            }

            Log.d(TAG, "Retrieved ${users.size} users")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting users: ${e.message}")
            Result.failure(e)
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserData(): UserData? {
        return try {
            UserData(
                id = getString("id") ?: "",
                email = getString("email") ?: "",
                name = getString("name") ?: "",
                role = getString("role")?.let { UserRole.valueOf(it.uppercase()) } ?: UserRole.USER,
                clientId = getString("clientId") ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting document to UserData: ${e.message}")
            null
        }
    }

    suspend fun createUser(user: UserData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collectionPath = if (user.role == UserRole.ADMIN) {
                "hdd-monitor/accounts/admins"
            } else {
                "hdd-monitor/accounts/clients/${user.clientId}/users"
            }
            firestore.collection(collectionPath).add(user.toMap()).await()
            Log.d(TAG, "User created successfully: ${user.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: UserData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collectionPath = if (user.role == UserRole.ADMIN) {
                "hdd-monitor/accounts/admins"
            } else {
                "hdd-monitor/accounts/clients/${user.clientId}/users"
            }
            val query = firestore.collection(collectionPath).whereEqualTo("id", user.id).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).set(user.toMap()).await()
                Log.d(TAG, "User updated successfully: ${user.email}")
                Result.success(Unit)
            } else {
                Log.w(TAG, "User not found for update: ${user.email}")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}")
            Result.failure(e)
        }
    }

    private fun UserData.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "email" to email,
            "name" to name,
            "role" to role.name,
            "clientId" to clientId
        )
    }

    suspend fun deleteUser(userId: String, clientId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collectionPath = if (clientId != null) {
                "hdd-monitor/accounts/clients/$clientId/users"
            } else {
                "hdd-monitor/accounts/admins"
            }
            val query = firestore.collection(collectionPath).whereEqualTo("id", userId).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).delete().await()
                Log.d(TAG, "User deleted successfully: $userId")
                Result.success(Unit)
            } else {
                Log.w(TAG, "User not found for deletion: $userId")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserToken(userId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = getCurrentUser() ?: throw Exception("No user logged in")
            val collectionPath = if (user.role == UserRole.ADMIN) {
                "hdd-monitor/accounts/admins"
            } else {
                "hdd-monitor/accounts/clients/${user.clientId}/users"
            }
            val query = firestore.collection(collectionPath).whereEqualTo("id", userId).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).update("fcmToken", token).await()
                Log.d(TAG, "User token updated successfully: $userId")
                Result.success(Unit)
            } else {
                Log.w(TAG, "User not found for token update: $userId")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user token: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): UserData? = withContext(Dispatchers.IO) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Log.d(TAG, "No Firebase user found")
            return@withContext null
        }
        Log.d(TAG, "Firebase user: ${firebaseUser.uid}")

        try {
            // Buscar en la colección de admins
            val adminUser = findUserInCollection("hdd-monitor/accounts/admins", firebaseUser.email, UserRole.ADMIN)
            if (adminUser != null) return@withContext adminUser

            // Buscar en la colección de clientes
            val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
            for (clientDoc in clientsQuery.documents) {
                val user = findUserInCollection("hdd-monitor/accounts/clients/${clientDoc.id}/users", firebaseUser.email, UserRole.USER, clientDoc.id)
                if (user != null) return@withContext user
            }

            Log.e(TAG, "User not found in Firestore")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}")
            null
        }
    }

    private suspend fun findUserInCollection(collectionPath: String, email: String?, role: UserRole, clientId: String = ""): UserData? {
        val query = firestore.collection(collectionPath)
            .whereEqualTo("email", email)
            .get()
            .await()

        if (!query.isEmpty) {
            val userDoc = query.documents.first()
            Log.d(TAG, "User found in collection: $collectionPath")
            return userDoc.toUserData()?.copy(role = role, clientId = clientId)
        }
        return null
    }

    suspend fun addMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("hdd-monitor/accounts/messages")
                .add(message)
                .await()
            Log.d(TAG, "Message added successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding message: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getMessages(userId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val messages = firestore.collection("hdd-monitor/accounts/messages")
                .whereEqualTo("ID_USER", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Message::class.java) }
            Log.d(TAG, "Retrieved ${messages.size} messages for user $userId")
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "UserRepository"
    }
}

data class Message(
    val ID_USER: String = "",
    val ID_CLIENT: String = "",
    val content: String = "",
    val subject: String = "",
    val timestamp: String = ""
)