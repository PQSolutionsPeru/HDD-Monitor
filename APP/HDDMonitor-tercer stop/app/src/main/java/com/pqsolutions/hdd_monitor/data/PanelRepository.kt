package com.pqsolutions.hdd_monitor.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.util.Cache
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = Cache<String, Panel>(100, Cache.getMillis(30, TimeUnit.SECONDS))
    private var panelsListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "PanelRepository"
        private const val BASE_PATH = "hdd-monitor/accounts"
    }

    fun getPanelsForUser(userData: UserData): Flow<List<Panel>> = callbackFlow {
        Log.d(TAG, "getPanelsForUser: role=${userData.role}, clientDocName=${userData.clientDocName}")

        val query = when (userData.role) {
            UserRole.ADMIN -> {
                firestore.collectionGroup("panels")
            }
            UserRole.USER -> {
                requireNotNull(userData.clientDocName) { "clientDocName es requerido para usuarios" }
                firestore.collection("$BASE_PATH/clients/${userData.clientDocName}/panels")
            }
        }

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error escuchando paneles", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val updatedPanels = snapshot.documents.mapNotNull { doc ->
                            try {
                                val clientDocName = when (userData.role) {
                                    UserRole.ADMIN -> {
                                        doc.reference.path.split("/").let { parts ->
                                            parts.getOrNull(parts.indexOf("clients") + 1)
                                        } ?: return@mapNotNull null
                                    }
                                    UserRole.USER -> userData.clientDocName
                                }

                                val panel = doc.toObject(Panel::class.java)?.copy(
                                    documentName = doc.id,
                                    clientDocName = clientDocName
                                )

                                panel?.let { basePanel ->
                                    val updatedPanel = fetchRelaysForPanel(basePanel)
                                    updatedPanel.apply {
                                        updateOverallStatus()
                                        updatePanelStatus(this)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al convertir documento de panel", e)
                                null
                            }
                        }

                        updatedPanels.forEach { panel ->
                            cache.put("${panel.clientDocName}_${panel.documentName}", panel)
                        }

                        Log.d(TAG, "Emitiendo ${updatedPanels.size} paneles")
                        trySend(updatedPanels)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando actualizaciones de paneles", e)
                    }
                }
            }
        }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .catch { e ->
            Log.e(TAG, "Error en getPanelsForUser", e)
            emit(emptyList())
        }

    fun getPanels(clientDocName: String?): Flow<List<Panel>> = getPanelsForUser(
        if (clientDocName == null) {
            UserData.createAdmin("", "")
        } else {
            UserData.createClientUser("", "", clientDocName, "")
        }
    )

    fun getAllPanelsFlow(): Flow<List<Panel>> = getPanelsForUser(UserData.createAdmin("", ""))

    suspend fun verifyPanelExists(clientDocName: String, panelDocName: String): Boolean {
        return try {
            cache.get("${clientDocName}_$panelDocName")?.let { return true }

            val panelDoc = firestore
                .document("$BASE_PATH/clients/$clientDocName/panels/$panelDocName")
                .get()
                .await()
            panelDoc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying panel existence", e)
            false
        }
    }

    fun observePanelUpdates(clientDocName: String, panelDocName: String): Flow<Panel?> = callbackFlow {
        val panelRef = firestore
            .document("$BASE_PATH/clients/$clientDocName/panels/$panelDocName")

        val cachedPanel = cache.get("${clientDocName}_$panelDocName")
        cachedPanel?.let { trySend(it) }

        val listenerRegistration = panelRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing panel updates", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val panel = snapshot.toObject(Panel::class.java)?.copy(
                            documentName = snapshot.id,
                            clientDocName = clientDocName
                        )

                        panel?.let { basePanel ->
                            val updatedPanel = fetchRelaysForPanel(basePanel)
                            updatedPanel.apply {
                                updateOverallStatus()
                                updatePanelStatus(this)
                            }
                            cache.put("${clientDocName}_$panelDocName", updatedPanel)
                            trySend(updatedPanel)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing panel update", e)
                    }
                }
            } else {
                trySend(null)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .catch { e ->
            Log.e(TAG, "Error in observePanelUpdates", e)
            emit(null)
        }

    private suspend fun fetchRelaysForPanel(panel: Panel): Panel {
        return try {
            val relaysSnapshot = firestore
                .collection("$BASE_PATH/clients/${panel.clientDocName}/panels/${panel.documentName}/relays")
                .get()
                .await()

            val relays = relaysSnapshot.documents.mapNotNull { relayDoc ->
                relayDoc.toObject(Relay::class.java)?.copy(name = relayDoc.id)
            }

            panel.copy(relays = relays).also { updatedPanel ->
                updatedPanel.updateOverallStatus()
                updatePanelStatus(updatedPanel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching relays for panel ${panel.documentName}", e)
            panel.copy(relays = emptyList())
        }
    }

    suspend fun updateRelayStatus(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        val relayRef = firestore
            .document("$BASE_PATH/clients/$clientDocName/panels/$panelDocName/relays/$relayName")

        val updates = mapOf(
            "status" to newStatus,
            "date_time" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
        )

        relayRef.update(updates).await()

        cache.get("${clientDocName}_$panelDocName")?.let { panel ->
            val updatedRelays = panel.relays.map { relay ->
                if (relay.name == relayName) relay.copy(status = newStatus) else relay
            }
            val updatedPanel = panel.copy(relays = updatedRelays)
            updatedPanel.updateOverallStatus()
            updatePanelStatus(updatedPanel)
            cache.put("${clientDocName}_$panelDocName", updatedPanel)
        }
    }

    private suspend fun updatePanelStatus(panel: Panel) {
        try {
            firestore.document("$BASE_PATH/clients/${panel.clientDocName}/panels/${panel.documentName}")
                .update("overallStatus", panel.overallStatus)
                .await()

            Log.d(TAG, "Panel status updated: ${panel.name}, overallStatus: ${panel.overallStatus}")

            // Actualizar el caché con el nuevo estado
            cache.put("${panel.clientDocName}_${panel.documentName}", panel)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating panel status", e)
        }
    }

    suspend fun createNewPanel(clientDocName: String, panel: Panel): Result<String> = runCatching {
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
            .document("$BASE_PATH/clients/$clientDocName/panels/$panelDocName")

        val batch = firestore.batch()
        batch.set(panelRef, panelData)

        panel.relays.forEach { relay ->
            val relayRef = panelRef.collection("relays").document(relay.name)
            batch.set(relayRef, relay.toMap())
        }

        batch.commit().await()

        val newPanel = panel.copy(
            documentName = panelDocName,
            clientDocName = clientDocName
        )
        cache.put("${clientDocName}_$panelDocName", newPanel)

        panelDocName
    }

    suspend fun updatePanel(clientDocName: String, panel: Panel): Result<Unit> = runCatching {
        if (!panel.isValid()) {
            throw IllegalArgumentException("Panel data is invalid")
        }

        val panelRef = firestore
            .document("$BASE_PATH/clients/$clientDocName/panels/${panel.documentName}")

        panelRef.set(panel.toMap()).await()

        val updatedPanel = panel.apply { updateOverallStatus() }
        updatePanelStatus(updatedPanel)
        cache.put("${clientDocName}_${panel.documentName}", updatedPanel)

        Log.d(TAG, "Panel updated successfully: ${panel.documentName}")
    }

    suspend fun deletePanel(clientDocName: String, panelDocName: String): Result<Unit> = runCatching {
        val panelRef = firestore
            .document("$BASE_PATH/clients/$clientDocName/panels/$panelDocName")

        val batch = firestore.batch()

        val relaysSnapshot = panelRef.collection("relays").get().await()
        relaysSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        batch.delete(panelRef)
        batch.commit().await()

        cache.remove("${clientDocName}_$panelDocName")

        Log.d(TAG, "Panel and all relays deleted successfully: $panelDocName")
    }

    fun clearCache() {
        cache.clear()
    }

    fun getCachedPanels(clientDocName: String? = null): List<Panel> {
        return cache.getAllValues().filter { panel ->
            clientDocName == null || panel.clientDocName == clientDocName
        }
    }
}