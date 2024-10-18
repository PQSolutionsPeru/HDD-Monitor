package com.pqsolutions.hdd_monitor.data

enum class UserRole {
    ADMIN, USER
}

data class UserData(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: UserRole = UserRole.USER,
    val clientId: String = ""
){
    // Constructor sin argumentos para Firestore
    constructor() : this("", "", "", UserRole.USER, "")
}