package com.pqsolutions.hdd_monitor.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun login(email: String, password: String): Result<UserData> = runCatching {
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("User ID not found")
        getUserData(userId)
    }

    private suspend fun getUserData(userId: String): UserData {
        // Primero, buscar en la colección de administradores
        val adminDoc = firestore.collection("hdd-monitor/accounts/admins").document(userId).get().await()
        if (adminDoc.exists()) {
            return UserData(
                id = userId,
                email = adminDoc.getString("email") ?: "",
                name = adminDoc.getString("name") ?: "",
                role = UserRole.ADMIN,
                clientId = "" // Los administradores no tienen clientId
            )
        }

        // Si no es admin, buscar en la colección de clientes
        val clientsRef = firestore.collection("hdd-monitor/accounts/clients")
        val query = clientsRef.get().await()
        for (clientDoc in query.documents) {
            val userDoc = clientDoc.reference.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                return UserData(
                    id = userId,
                    email = userDoc.getString("email") ?: "",
                    name = userDoc.getString("name") ?: "",
                    role = UserRole.USER,
                    clientId = clientDoc.id
                )
            }
        }

        throw Exception("User not found")
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun logout() {
        auth.signOut()
    }

    suspend fun getCurrentUserData(): UserData? {
        val currentUser = auth.currentUser ?: return null
        return getUserData(currentUser.uid)
    }
}

data class UserData(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
    val clientId: String
)

enum class UserRole {
    USER, ADMIN
}