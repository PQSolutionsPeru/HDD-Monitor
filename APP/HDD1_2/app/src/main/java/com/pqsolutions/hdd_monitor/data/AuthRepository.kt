package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun login(email: String, password: String): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            Log.d("AuthRepository", "Attempting login for email: $email")
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            Log.d("AuthRepository", "Firebase Auth successful, user ID: ${authResult.user?.uid}")
            val userData = getUserData(email)
            Result.success(userData)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getUserData(email: String): UserData = withContext(Dispatchers.IO) {
        Log.d("AuthRepository", "Fetching user data for email: $email")

        val adminQuery = firestore.collection("hdd-monitor/accounts/admins")
            .whereEqualTo("email", email)
            .get()
            .await()

        if (!adminQuery.isEmpty) {
            val adminDoc = adminQuery.documents.first()
            Log.d("AuthRepository", "User found in admins collection")
            return@withContext UserData(
                id = adminDoc.getString("ID") ?: "",
                email = adminDoc.getString("email") ?: "",
                name = adminDoc.getString("name") ?: "",
                role = UserRole.ADMIN,
                clientId = ""
            )
        }

        val clientsQuery = firestore.collection("hdd-monitor/accounts/clients").get().await()
        for (clientDoc in clientsQuery.documents) {
            val usersQuery = clientDoc.reference.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()

            if (!usersQuery.isEmpty) {
                val userDoc = usersQuery.documents.first()
                Log.d("AuthRepository", "User found in clients collection, client ID: ${clientDoc.id}")
                return@withContext UserData(
                    id = userDoc.getString("ID") ?: "",
                    email = userDoc.getString("email") ?: "",
                    name = userDoc.getString("name") ?: "",
                    role = UserRole.USER,
                    clientId = clientDoc.id
                )
            }
        }

        Log.e("AuthRepository", "User not found in Firestore")
        throw Exception("User not found in Firestore")
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun logout() {
        auth.signOut()
    }

    suspend fun getCurrentUserData(): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("No user logged in")
            val userData = getUserData(currentUser.email ?: throw Exception("User email not found"))
            Result.success(userData)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error getting current user data: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("No user logged in")
            val userData = getUserData(currentUser.email ?: throw Exception("User email not found"))

            val collectionPath = if (userData.role == UserRole.ADMIN) {
                "hdd-monitor/accounts/admins"
            } else {
                "hdd-monitor/accounts/clients/${userData.clientId}/users"
            }

            val query = firestore.collection(collectionPath)
                .whereEqualTo("ID", userData.id)
                .get()
                .await()

            if (!query.isEmpty) {
                val userDoc = query.documents.first()
                userDoc.reference.update("fcmToken", token).await()
                Result.success(Unit)
            } else {
                throw Exception("User document not found")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error updating user token: ${e.message}")
            Result.failure(e)
        }
    }
}