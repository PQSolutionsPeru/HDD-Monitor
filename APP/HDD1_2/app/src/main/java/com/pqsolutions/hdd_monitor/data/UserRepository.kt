package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getUsers(): Result<List<UserData>> = runCatching {
        firestore.collection("users")
            .get()
            .await()
            .toObjects(UserData::class.java)
    }

    suspend fun createUser(userData: UserData): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(userData.id)
            .set(userData)
            .await()
    }

    suspend fun updateUser(userData: UserData): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(userData.id)
            .set(userData)
            .await()
    }

    suspend fun deleteUser(userId: String): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(userId)
            .delete()
            .await()
    }
}