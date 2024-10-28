package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    suspend fun login(email: String, password: String): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting login for email: $email")
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Firebase Auth successful, user ID: ${authResult.user?.uid}")

            val userData = getUserData(email)
            userPreferences.setUserData(userData)
            userPreferences.setAuthToken(authResult.user?.uid ?: "")

            Result.success(userData)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getUserData(email: String): UserData = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching user data for email: $email")

        // Intentar obtener de preferencias primero si el email coincide
        userPreferences.userDataFlow.firstOrNull()?.let { savedUser ->
            if (savedUser.email == email) {
                Log.d(TAG, "User found in preferences")
                return@withContext savedUser
            }
        }

        val adminQuery = firestore.collection("hdd-monitor/accounts/admins")
            .whereEqualTo("email", email)
            .get()
            .await()

        if (!adminQuery.isEmpty) {
            val adminDoc = adminQuery.documents.first()
            Log.d(TAG, "User found in admins collection")
            return@withContext UserData(
                documentName = adminDoc.id,
                email = adminDoc.getString("email") ?: "",
                name = adminDoc.getString("name") ?: "",
                roleString = "admin",
                clientDocName = "",
                fcmToken = adminDoc.getString("fcmToken")
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
                Log.d(TAG, "User found in clients collection, client document: ${clientDoc.id}")
                return@withContext UserData(
                    documentName = userDoc.id,
                    email = userDoc.getString("email") ?: "",
                    name = userDoc.getString("name") ?: "",
                    roleString = "user",
                    clientDocName = clientDoc.id,
                    fcmToken = userDoc.getString("fcmToken")
                )
            }
        }

        Log.e(TAG, "User not found in Firestore")
        throw Exception("User not found in Firestore")
    }

    suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
            val savedToken = userPreferences.authTokenFlow.firstOrNull()
            val savedUser = userPreferences.userDataFlow.firstOrNull()

            if (currentUser != null && savedToken == currentUser.uid && savedUser != null) {
                return@withContext true
            }

            // Si hay usuario en Firebase pero no en preferencias, intentar recuperar datos
            if (currentUser != null && currentUser.email != null) {
                try {
                    val userData = getUserData(currentUser.email!!)
                    userPreferences.setUserData(userData)
                    userPreferences.setAuthToken(currentUser.uid)
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Error recovering user data: ${e.message}")
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking login status: ${e.message}")
            false
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            userPreferences.clearUserData()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserData(): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val savedUser = userPreferences.userDataFlow.firstOrNull()
            if (savedUser != null) {
                return@withContext Result.success(savedUser)
            }

            val currentUser = auth.currentUser ?: throw Exception("No user logged in")
            val userData = getUserData(currentUser.email ?: throw Exception("User email not found"))
            userPreferences.setUserData(userData)
            Result.success(userData)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user data: ${e.message}")
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
                "hdd-monitor/accounts/clients/${userData.clientDocName}/users"
            }

            val userDoc = firestore.document("$collectionPath/${userData.documentName}")
            userDoc.update("fcmToken", token).await()
            userPreferences.setUserData(userData.copy(fcmToken = token))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user token: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun refreshUserData(): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("No user logged in")
            val userData = getUserData(currentUser.email ?: throw Exception("User email not found"))
            userPreferences.setUserData(userData)
            Result.success(userData)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing user data: ${e.message}")
            Result.failure(e)
        }
    }
}