package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun getUsers(): Result<List<UserData>> = withContext(Dispatchers.IO) {
        try {
            val users = mutableListOf<UserData>()

            // Obtener administradores
            val adminsQuery = firestore.collection("hdd-monitor/accounts/admins").get().await()
            users.addAll(adminsQuery.documents.mapNotNull { adminDoc ->
                if (adminDoc.exists()) {
                    adminDoc.toUserData(UserRole.ADMIN, "")
                } else null
            })

            // Obtener usuarios de clientes
            val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
            clientsQuery.documents.forEach { clientDoc ->
                val usersQuery = clientDoc.reference.collection("users").get().await()
                users.addAll(usersQuery.documents.mapNotNull { userDoc ->
                    if (userDoc.exists()) {
                        userDoc.toUserData(UserRole.USER, clientDoc.id)
                    } else null
                })
            }

            Log.d(TAG, "Retrieved ${users.size} users")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting users: ${e.message}")
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toUserData(defaultRole: UserRole, clientDocName: String): UserData? {
        return try {
            UserData(
                documentName = id,
                email = getString("email") ?: "",
                name = getString("name") ?: "",
                roleString = getString("role") ?: UserRole.toFirestoreValue(defaultRole),
                clientDocName = clientDocName,
                fcmToken = getString("fcmToken")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting document to UserData: ${e.message}")
            null
        }
    }

    suspend fun createUser(user: UserData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val documentName = when (user.role) {
                UserRole.ADMIN -> IdManager.generateAdminDocumentName()
                UserRole.USER -> IdManager.generateUserDocumentName()
            }

            val collectionPath = when (user.role) {
                UserRole.ADMIN -> "hdd-monitor/accounts/admins"
                UserRole.USER -> "hdd-monitor/accounts/clients/${user.clientDocName}/users"
            }

            val userMap = mapOf(
                "documentName" to documentName,
                "email" to user.email,
                "name" to user.name,
                "role" to UserRole.toFirestoreValue(user.role),
                "clientDocName" to user.clientDocName,
                "fcmToken" to user.fcmToken
            )

            firestore.collection(collectionPath)
                .document(documentName)
                .set(userMap)
                .await()

            Log.d(TAG, "User created successfully: $documentName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: UserData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (user.documentName.isEmpty()) {
                throw IllegalArgumentException("Invalid document name")
            }

            val collectionPath = when (user.role) {
                UserRole.ADMIN -> "hdd-monitor/accounts/admins"
                UserRole.USER -> "hdd-monitor/accounts/clients/${user.clientDocName}/users"
            }

            val userMap = mapOf(
                "documentName" to user.documentName,
                "email" to user.email,
                "name" to user.name,
                "role" to UserRole.toFirestoreValue(user.role),
                "clientDocName" to user.clientDocName,
                "fcmToken" to user.fcmToken
            )

            firestore.collection(collectionPath)
                .document(user.documentName)
                .set(userMap)
                .await()

            Log.d(TAG, "User updated successfully: ${user.documentName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteUser(documentName: String, clientDocName: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (documentName.isEmpty()) {
                throw IllegalArgumentException("Invalid document name")
            }

            val collectionPath = if (documentName.startsWith(IdManager.PREFIX_ADMIN)) {
                "hdd-monitor/accounts/admins"
            } else {
                requireNotNull(clientDocName) { "Client document name is required for client users" }
                "hdd-monitor/accounts/clients/$clientDocName/users"
            }

            firestore.collection(collectionPath)
                .document(documentName)
                .delete()
                .await()

            Log.d(TAG, "User deleted successfully: $documentName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(userDocName: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userDocName.isEmpty()) {
                throw IllegalArgumentException("Invalid document name")
            }

            val user = getCurrentUser() ?: throw Exception("No user logged in")
            val collectionPath = when (user.role) {
                UserRole.ADMIN -> "hdd-monitor/accounts/admins"
                UserRole.USER -> "hdd-monitor/accounts/clients/${user.clientDocName}/users"
            }

            firestore.collection(collectionPath)
                .document(userDocName)
                .update("fcmToken", token)
                .await()

            Log.d(TAG, "FCM token updated successfully: $userDocName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token: ${e.message}")
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
                val user = findUserInCollection(
                    "hdd-monitor/accounts/clients/${clientDoc.id}/users",
                    firebaseUser.email,
                    UserRole.USER,
                    clientDoc.id
                )
                if (user != null) return@withContext user
            }

            Log.e(TAG, "User not found in Firestore")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}")
            null
        }
    }

    private suspend fun findUserInCollection(
        collectionPath: String,
        email: String?,
        defaultRole: UserRole,
        clientDocName: String = ""
    ): UserData? {
        val query = firestore.collection(collectionPath)
            .whereEqualTo("email", email)
            .get()
            .await()

        if (!query.isEmpty) {
            val userDoc = query.documents.first()
            Log.d(TAG, "User found in collection: $collectionPath")
            return userDoc.toUserData(defaultRole, clientDocName)
        }
        return null
    }

    data class Message(
        val documentName: String = "",
        val userDocName: String = "",
        val clientDocName: String = "",
        val content: String = "",
        val subject: String = "",
        val timestamp: String = ""
    )

    suspend fun addMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val messageDocName = IdManager.generateNotificationDocumentName(message.clientDocName)
            val messageWithDocName = message.copy(documentName = messageDocName)

            firestore.collection("hdd-monitor/accounts/messages")
                .document(messageDocName)
                .set(messageWithDocName)
                .await()

            Log.d(TAG, "Message added successfully: $messageDocName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding message: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getMessages(userDocName: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val messages = firestore.collection("hdd-monitor/accounts/messages")
                .whereEqualTo("userDocName", userDocName)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Message::class.java) }
            Log.d(TAG, "Retrieved ${messages.size} messages for user $userDocName")
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages: ${e.message}")
            Result.failure(e)
        }
    }
}