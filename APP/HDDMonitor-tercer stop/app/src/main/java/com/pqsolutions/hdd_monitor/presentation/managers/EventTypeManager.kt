package com.pqsolutions.hdd_monitor.presentation.managers

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EventTypeManager(
    private val firestore: FirebaseFirestore,
    private val viewModelScope: CoroutineScope,
    private val onError: suspend (String) -> Unit,
    private val onMessage: suspend (String) -> Unit
) {
    companion object {
        private const val TAG = "EventTypeManager"
        private const val EVENT_TYPES_PATH = "hdd-monitor/event_types"
    }

    suspend fun loadEventTypes(): List<String> {
        try {
            val documentSnapshot = firestore.document(EVENT_TYPES_PATH).get().await()
            return documentSnapshot.getString("types")?.split(",")?.map { it.trim() }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading events types", e)
            onError("Error al cargar tipos de eventos")
            return emptyList()
        }
    }

    fun createNewEventType(
        newType: String,
        currentUser: UserData?,
        onSuccess: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (currentUser?.role != UserRole.ADMIN) {
                    onError("Solo los administradores pueden crear nuevos tipos de eventos")
                    return@launch
                }

                Log.d(TAG, "Intentando crear nuevo tipo de evento: $newType")

                val docRef = firestore.document(EVENT_TYPES_PATH)
                val snapshot = docRef.get().await()
                val currentTypes = snapshot.getString("types")?.split(",")?.map { it.trim() } ?: emptyList()

                if (currentTypes.contains(newType)) {
                    onError("Este tipo de evento ya existe")
                    return@launch
                }

                val updatedTypes = (currentTypes + newType).sorted().joinToString(",")
                docRef.update("types", updatedTypes).await()

                Log.d(TAG, "Tipo de evento creado exitosamente: $newType")
                onMessage("Nuevo tipo de evento creado: $newType")
                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Error creating new events type: $newType", e)
                onError(e.message ?: "Error al crear nuevo tipo de evento")
            }
        }
    }

    fun deleteEventType(
        eventType: String,
        currentUser: UserData?,
        onSuccess: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (currentUser?.role != UserRole.ADMIN) {
                    onError("Solo los administradores pueden eliminar tipos de eventos")
                    return@launch
                }

                Log.d(TAG, "Intentando eliminar tipo de evento: $eventType")

                val docRef = firestore.document(EVENT_TYPES_PATH)
                val snapshot = docRef.get().await()
                val currentTypes = snapshot.getString("types")?.split(",")?.map { it.trim() } ?: emptyList()

                if (!currentTypes.contains(eventType)) {
                    onError("Este tipo de evento no existe")
                    return@launch
                }

                // Verificar eventos existentes
                var hasExistingEvents = false
                firestore.collection("hdd-monitor/accounts/clients").get().await().documents.forEach { clientDoc ->
                    if (!hasExistingEvents) {
                        val eventsRef = clientDoc.reference.collection("events")
                            .whereEqualTo("type", eventType)
                            .limit(1)
                            .get()
                            .await()

                        if (!eventsRef.isEmpty) {
                            hasExistingEvents = true
                        }
                    }
                }

                if (hasExistingEvents) {
                    onError("No se puede eliminar: existen eventos usando este tipo")
                    return@launch
                }

                val updatedTypes = (currentTypes - eventType).sorted().joinToString(",")
                docRef.update("types", updatedTypes).await()

                Log.d(TAG, "Tipo de evento eliminado exitosamente: $eventType")
                onMessage("Tipo de evento eliminado: $eventType")
                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting events type: $eventType", e)
                onError(e.message ?: "Error al eliminar tipo de evento")
            }
        }
    }
}