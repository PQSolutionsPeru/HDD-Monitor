package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun login(email: String, password: String): Result<UserData> = runCatching {
        Log.d("AuthRepository", "Attempting login for email: $email")
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("User ID not found")
        Log.d("AuthRepository", "Firebase Auth successful, user ID: $userId")
        getUserData(email)
    }

    private suspend fun getUserData(email: String): UserData {
        Log.d("AuthRepository", "Fetching user data for email: $email")

        // Buscar en la colección de administradores
        val adminQuery = firestore.collection("hdd-monitor/accounts/admins")
            .whereEqualTo("email", email)
            .get()
            .await()

        if (!adminQuery.isEmpty) {
            val adminDoc = adminQuery.documents.first()
            Log.d("AuthRepository", "User found in admins collection")
            return UserData(
                id = adminDoc.getString("ID") ?: adminDoc.id,
                email = adminDoc.getString("email") ?: "",
                name = adminDoc.getString("name") ?: "",
                role = UserRole.ADMIN,
                clientId = "" // Los administradores no tienen clientId
            )
        }

        // Si no es admin, buscar en la colección de clientes
        val clientsRef = firestore.collection("hdd-monitor/accounts/clients")
        val clientsQuery = clientsRef.get().await()

        for (clientDoc in clientsQuery.documents) {
            val usersQuery = clientDoc.reference.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()

            if (!usersQuery.isEmpty) {
                val userDoc = usersQuery.documents.first()
                Log.d("AuthRepository", "User found in clients collection, client ID: ${clientDoc.id}")
                return UserData(
                    id = userDoc.getString("ID") ?: userDoc.id,
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

    suspend fun getCurrentUserData(): Result<UserData> = runCatching {
        val currentUser = auth.currentUser ?: throw Exception("No user logged in")
        getUserData(currentUser.email ?: throw Exception("User email not found"))
    }
}