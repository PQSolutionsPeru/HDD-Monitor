package com.pqsolutions.hdd_monitor.presentation.util

import androidx.compose.runtime.staticCompositionLocalOf

interface ClientManager {
    fun getClientName(clientDocName: String): String
}

class ClientManagerImpl : ClientManager {
    private val clientNameCache = mutableMapOf<String, String>()

    fun updateClientName(clientDocName: String, name: String) {
        clientNameCache[clientDocName] = name
    }

    fun updateClientNames(clients: Map<String, String>) {
        clientNameCache.clear()
        clientNameCache.putAll(clients)
    }

    override fun getClientName(clientDocName: String): String {
        return clientNameCache[clientDocName] ?: clientDocName.removePrefix("client_")
    }

    fun clearCache() {
        clientNameCache.clear()
    }
}

val LocalClientManager = staticCompositionLocalOf<ClientManager> {
    object : ClientManager {
        override fun getClientName(clientDocName: String): String = clientDocName.removePrefix("client_")
    }
}