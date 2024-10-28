package com.pqsolutions.hdd_monitor.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.util.IdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "PanelRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
    }

    fun getPanels(clientDocName: String?): Flow<List<Panel>> = callbackFlow {
        Log.d(TAG, "getPanels called with clientDocName: $clientDocName")

        val panelListeners = mutableMapOf<String, ListenerRegistration>()
        val currentPanels = mutableMapOf<String, Panel>()

        val clientsRef = firestore.collection(BASE_PATH)

        val clientListener = clientsRef.addSnapshotListener { clientSnapshot, clientError ->
            if (clientError != null) {
                Log.e(TAG, "Error listening to clients", clientError)
                close(clientError)
                return@addSnapshotListener
            }

            if (clientSnapshot != null) {
                for (clientDoc in clientSnapshot.documents) {
                    val currentClientDocName = clientDoc.id
                    Log.d(TAG, "Processing client: $currentClientDocName")

                    if (clientDocName == null || currentClientDocName == clientDocName) {
                        val panelsRef = clientDoc.reference.collection("panels")
                        val panelListener = panelsRef.addSnapshotListener { panelSnapshot, panelError ->
                            if (panelError != null) {
                                Log.e(TAG, "Error listening to panels for client $currentClientDocName", panelError)
                                return@addSnapshotListener
                            }

                            if (panelSnapshot != null) {
                                for (panelDoc in panelSnapshot.documents) {
                                    val panel = panelDoc.toObject(Panel::class.java)?.copy(
                                        documentName = panelDoc.id,
                                        clientDocName = currentClientDocName
                                    )

                                    if (panel != null) {
                                        Log.d(TAG, "Panel added/updated: ${panel.documentName} for client $currentClientDocName")
                                        currentPanels["${currentClientDocName}_${panel.documentName}"] = panel
                                        fetchRelaysForPanel(panel) { updatedPanel ->
                                            currentPanels["${currentClientDocName}_${updatedPanel.documentName}"] = updatedPanel
                                            trySend(currentPanels.values.toList())
                                        }
                                    }
                                }
                                trySend(currentPanels.values.toList())
                            }
                        }
                        panelListeners["client_$currentClientDocName"] = panelListener
                    }
                }
            }
        }

        awaitClose {
            Log.d(TAG, "Closing panel listeners")
            clientListener.remove()
            panelListeners.values.forEach { it.remove() }
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchRelaysForPanel(panel: Panel, onUpdate: (Panel) -> Unit) {
        Log.d(TAG, "Fetching relays for panel ${panel.documentName} of client ${panel.clientDocName}")

        firestore.collection("$BASE_PATH/${panel.clientDocName}/panels/${panel.documentName}/relays")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching relays for panel ${panel.documentName}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val relays = snapshot.documents.mapNotNull { relayDoc ->
                        relayDoc.toObject(Relay::class.java)?.copy(name = relayDoc.id).also { relay ->
                            Log.d(TAG, "Relay update from Firestore: ${relay?.name}, status: ${relay?.status}")
                        }
                    }

                    Log.d(TAG, "Fetched ${relays.size} relays for panel ${panel.documentName}")
                    val updatedPanel = panel.copy(relays = relays)
                    updatedPanel.overallStatus = determineOverallPanelStatus(updatedPanel)
                    onUpdate(updatedPanel)
                } else {
                    Log.d(TAG, "No relays found for panel ${panel.documentName}")
                    onUpdate(panel.copy(relays = emptyList()))
                }
            }
    }

    private fun determineOverallPanelStatus(panel: Panel): String {
        val discRelays = panel.relays.filter { it.status == "DISC" }
        return when {
            discRelays.isNotEmpty() -> discRelays.joinToString(", ") { it.name }
            else -> "OK"
        }
    }

    suspend fun createNewPanel(
        clientDocName: String,
        panel: Panel
    ): Result<String> = runCatching {
        if (!panel.isValid()) {
            throw IllegalArgumentException("Panel data is invalid")
        }

        val panelDocName = IdManager.generatePanelDocumentName(clientDocName)
        Log.d(TAG, "Creating new panel with documentName: $panelDocName")

        val panelData = panel.copy(
            documentName = panelDocName,
            clientDocName = clientDocName
        ).toMap()

        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName")

        // Crear el panel
        panelRef.set(panelData).await()

        // Crear los relays iniciales
        val batch = firestore.batch()
        panel.relays.forEach { relay ->
            val relayRef = panelRef.collection("relays").document(relay.name)
            batch.set(relayRef, relay.toMap())
        }
        batch.commit().await()

        panelDocName
    }

    suspend fun updateRelayStatus(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        Log.d(TAG, "Updating relay status: clientDocName=$clientDocName, panelDocName=$panelDocName, " +
                "relayName=$relayName, status=$newStatus")

        val relayRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName/relays/$relayName")

        val updates = mapOf(
            "status" to newStatus,
            "date_time" to java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
        )

        relayRef.update(updates).await()
        Log.d(TAG, "Relay status updated successfully")
    }

    suspend fun updatePanel(
        clientDocName: String,
        panel: Panel
    ): Result<Unit> = runCatching {
        if (!panel.isValid()) {
            throw IllegalArgumentException("Panel data is invalid")
        }

        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/${panel.documentName}")

        panelRef.set(panel.toMap()).await()
        Log.d(TAG, "Panel updated successfully: ${panel.documentName}")
    }

    suspend fun deletePanel(
        clientDocName: String,
        panelDocName: String
    ): Result<Unit> = runCatching {
        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName")

        // Primero eliminar todos los relays
        val relaysSnapshot = panelRef.collection("relays").get().await()
        val batch = firestore.batch()
        relaysSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()

        // Luego eliminar el panel
        panelRef.delete().await()
        Log.d(TAG, "Panel and all relays deleted successfully: $panelDocName")
    }
}