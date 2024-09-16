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

            val adminsQuery = firestore.collection("hdd-monitor/accounts/admins").get().await()
            for (adminDoc in adminsQuery.documents) {
                users.add(UserData(
                    id = adminDoc.getString("ID") ?: "",
                    email = adminDoc.getString("email") ?: "",
                    name = adminDoc.getString("name") ?: "",
                    role = UserRole.ADMIN,
                    clientId = ""
                ))
            }

            val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
            for (clientDoc in clientsQuery.documents) {
                val usersQuery = clientDoc.reference.collection("users").get().await()
                for (userDoc in usersQuery.documents) {
                    users.add(UserData(
                        id = userDoc.getString("ID") ?: "",
                        email = userDoc.getString("email") ?: "",
                        name = userDoc.getString("name") ?: "",
                        role = UserRole.USER,
                        clientId = clientDoc.id
                    ))
                }
            }

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUser(user: UserData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collectionPath = if (user.role == UserRole.ADMIN) {
                "hdd-monitor/accounts/admins"
            } else {
                "hdd-monitor/accounts/clients/${user.clientId}/users"
            }
            firestore.collection(collectionPath).add(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
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
            val query = firestore.collection(collectionPath).whereEqualTo("ID", user.id).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).set(user).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: String, clientId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collectionPath = if (clientId != null) {
                "hdd-monitor/accounts/clients/$clientId/users"
            } else {
                "hdd-monitor/accounts/admins"
            }
            val query = firestore.collection(collectionPath).whereEqualTo("ID", userId).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).delete().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
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
            val query = firestore.collection(collectionPath).whereEqualTo("ID", userId).get().await()
            if (!query.isEmpty) {
                val documentId = query.documents.first().id
                firestore.collection(collectionPath).document(documentId).update("fcmToken", token).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): UserData? = withContext(Dispatchers.IO) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Log.d("UserRepository", "No Firebase user found")
            return@withContext null
        }
        Log.d("UserRepository", "Firebase user: ${firebaseUser.uid}")

        try {
            // Buscar en la colección de admins
            val adminQuery = firestore.collection("hdd-monitor/accounts/admins")
                .whereEqualTo("email", firebaseUser.email)
                .get()
                .await()

            if (!adminQuery.isEmpty) {
                val adminDoc = adminQuery.documents.first()
                Log.d("UserRepository", "User found in admin collection")
                return@withContext UserData(
                    id = adminDoc.getString("ID") ?: "",
                    email = adminDoc.getString("email") ?: "",
                    name = adminDoc.getString("name") ?: "",
                    role = UserRole.ADMIN,
                    clientId = ""
                )
            }

            // Buscar en la colección de clientes
            val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
            for (clientDoc in clientsQuery.documents) {
                val usersQuery = clientDoc.reference.collection("users")
                    .whereEqualTo("email", firebaseUser.email)
                    .get()
                    .await()

                if (!usersQuery.isEmpty) {
                    val userDoc = usersQuery.documents.first()
                    Log.d("UserRepository", "User found in client collection: ${clientDoc.id}")
                    return@withContext UserData(
                        id = userDoc.getString("ID") ?: "",
                        email = userDoc.getString("email") ?: "",
                        name = userDoc.getString("name") ?: "",
                        role = UserRole.USER,
                        clientId = clientDoc.id
                    )
                }
            }

            Log.e("UserRepository", "User not found in Firestore")
            null
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting current user: ${e.message}")
            null
        }
    }

    suspend fun addMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("hdd-monitor/accounts/messages")
                .add(message)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
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
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class Message(
    val ID_USER: String = "",
    val ID_CLIENT: String = "",
    val content: String = "",
    val subject: String = "",
    val timestamp: String = ""
)