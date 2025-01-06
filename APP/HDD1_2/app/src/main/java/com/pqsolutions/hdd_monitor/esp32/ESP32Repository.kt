package com.pqsolutions.hdd_monitor.esp32

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ESP32Repository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "ESP32Repository"
        private const val ESP32_COLLECTION = "hdd-monitor/esp32/registered"
    }

    private fun DocumentSnapshot.toESP32Device(): ESP32Device? {
        return try {
            if (!exists()) return null

            toObject(ESP32Device::class.java)?.copy(
                documentName = id
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo documento ESP32", e)
            null
        }
    }

    fun observeESP32s(): Flow<List<ESP32Device>> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null

        try {
            Log.d(TAG, "Iniciando observación de ESP32s")
            listenerRegistration = firestore.collection(ESP32_COLLECTION)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error al observar ESP32s", error)
                        return@addSnapshotListener
                    }

                    val devices = snapshot?.documents?.mapNotNull { doc ->
                        doc.toESP32Device()
                    } ?: emptyList()

                    trySend(devices)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar listener de ESP32", e)
            close(e)
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    fun observeUnassignedESP32s(deviceId: String? = null): Flow<List<ESP32Device>> = callbackFlow {
        try {
            Log.d(TAG, "Iniciando observación de ESP32s no asignados. DeviceId: $deviceId")

            val baseQuery = firestore.collection(ESP32_COLLECTION).whereIn(
                "status",
                listOf(
                    ESP32Device.STATUS_AWAITING_CONFIG,
                    ESP32Device.STATUS_PENDING_ASSIGNMENT,
                    ESP32Device.STATUS_WIFI_CONFIG
                )
            )

            // Si hay deviceId, buscar por el ID del documento directamente
            val finalQuery = if (deviceId != null) {
                Log.d(TAG, "Buscando ESP32 con ID: $deviceId")
                // Primero filtramos por status y luego por ID
                baseQuery.whereEqualTo(FieldPath.documentId(), deviceId)
            } else {
                baseQuery
            }

            val listenerRegistration = finalQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observando ESP32s", error)
                    return@addSnapshotListener
                }

                val devices = snapshot?.documents?.mapNotNull { doc ->
                    doc.toESP32Device()?.also {
                        Log.d(TAG, "ESP32 encontrado - ID: ${doc.id}, Status: ${it.status}, MAC: ${it.MAC}")
                    }
                } ?: emptyList()

                trySend(devices)
            }

            awaitClose {
                listenerRegistration?.remove()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando listener", e)
            close(e)
        }
    }

    fun observeESP32Status(esp32Id: String): Flow<String> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null

        try {
            listenerRegistration = firestore.collection(ESP32_COLLECTION)
                .document(esp32Id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error al observar estado de ESP32 $esp32Id", error)
                        return@addSnapshotListener
                    }

                    val status = snapshot?.getString("status") ?: "UNKNOWN"
                    trySend(status)

                    if (status == ESP32Device.STATUS_RUNNING) {
                        snapshot?.getString("client_id")?.let { clientId ->
                            snapshot.getString("panel_id")?.let { panelId ->
                                observeRelayStates(clientId, panelId)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar listener de estado de ESP32", e)
            close(e)
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    private fun observeRelayStates(clientId: String, panelId: String) {
        val relaysRef = firestore
            .collection("hdd-monitor/accounts/clients")
            .document(clientId)
            .collection("panels")
            .document(panelId)
            .collection("relays")

        relaysRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observando relays", error)
                return@addSnapshotListener
            }

            snapshot?.documentChanges?.forEach { change ->
                val relay = change.document
                Log.d(TAG, "Cambio en relay ${relay.id}: ${relay.getString("status")}")
            }
        }
    }

    private fun generateESP32Id(mac: String): String {
        val normalizedMAC = mac.uppercase().replace(":", "").replace("-", "")
        return normalizedMAC.takeLast(4) + "AC" + normalizedMAC.take(2)
    }

    suspend fun findByMAC(mac: String): ESP32Device? {
        return try {
            Log.d(TAG, "Searching for ESP32 with MAC: $mac")
            val normalizedMAC = mac.uppercase().replace(":", "")
            val esp32Id = generateESP32Id(normalizedMAC)

            firestore.collection(ESP32_COLLECTION)
                .whereEqualTo("MAC", normalizedMAC)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toESP32Device()
        } catch (e: Exception) {
            Log.e(TAG, "Error finding ESP32 by MAC", e)
            null
        }
    }

    suspend fun assignToPanelAndClient(
        esp32Id: String,
        clientId: String,
        panelId: String
    ): Result<Unit> = runCatching {
        Log.d(TAG, "Assigning ESP32 $esp32Id to panel $panelId")

        val esp32Ref = firestore.document("$ESP32_COLLECTION/$esp32Id")
        val esp32Doc = esp32Ref.get().await()

        if (!esp32Doc.exists()) {
            throw IllegalStateException("ESP32 $esp32Id no encontrado")
        }

        esp32Doc.data?.let { currentData ->
            val currentClientId = currentData["client_id"] as? String
            val currentPanelId = currentData["panel_id"] as? String

            if (!currentClientId.isNullOrEmpty() && !currentPanelId.isNullOrEmpty()) {
                throw IllegalStateException("ESP32 ya está asignado a otro panel")
            }
        }

        val updateData = mapOf(
            "client_id" to clientId,
            "panel_id" to panelId,
            "status" to ESP32Device.STATUS_AWAITING_CONFIG,
            "lastUpdate" to Timestamp.now()
        )

        esp32Ref.set(updateData, SetOptions.merge()).await()
        Log.d(TAG, "ESP32 assigned successfully")
    }

    suspend fun updateNetworkInfo(esp32Id: String, ip: String, mac: String): Result<Unit> = runCatching {
        Log.d(TAG, "Updating network info for ESP32: $esp32Id")

        val esp32Ref = firestore.document("$ESP32_COLLECTION/$esp32Id")
        val doc = esp32Ref.get().await()

        val updateData = if (!doc.exists()) {
            mapOf(
                "MAC" to mac.uppercase().replace(":", ""),
                "IP" to ip,
                "status" to ESP32Device.STATUS_AWAITING_CONFIG,
                "client_id" to "",
                "panel_id" to "",
                "lastUpdate" to Timestamp.now()
            )
        } else {
            mapOf(
                "IP" to ip,
                "MAC" to mac.uppercase().replace(":", ""),
                "status" to ESP32Device.STATUS_AWAITING_CONFIG,
                "lastUpdate" to Timestamp.now()
            )
        }

        esp32Ref.set(updateData, SetOptions.merge()).await()
    }

    suspend fun updateStatus(esp32Id: String, status: String): Result<Unit> = runCatching {
        Log.d(TAG, "Updating status for ESP32: $esp32Id to $status")

        val updateData = mapOf(
            "status" to status,
            "lastUpdate" to Timestamp.now()
        )

        firestore.document("$ESP32_COLLECTION/$esp32Id")
            .update(updateData)
            .await()
    }

    suspend fun registerNewESP32(mac: String): Result<String> = runCatching {
        Log.d(TAG, "Registering new ESP32 with MAC: $mac")
        val normalizedMAC = mac.uppercase().replace(":", "")
        val esp32Id = generateESP32Id(normalizedMAC)

        val device = mapOf(
            "MAC" to normalizedMAC,
            "IP" to "",
            "status" to ESP32Device.STATUS_WIFI_CONFIG,
            "client_id" to "",
            "panel_id" to "",
            "lastUpdate" to Timestamp.now()
        )

        firestore.collection(ESP32_COLLECTION).document(esp32Id)
            .set(device)
            .await()

        Log.d(TAG, "ESP32 registered with ID: $esp32Id")
        esp32Id
    }

    suspend fun unassignFromPanel(esp32Id: String): Result<Unit> = runCatching {
        Log.d(TAG, "Unassigning ESP32: $esp32Id")

        val updateData = mapOf(
            "client_id" to "",
            "panel_id" to "",
            "status" to ESP32Device.STATUS_AWAITING_CONFIG,
            "lastUpdate" to Timestamp.now()
        )

        firestore.document("$ESP32_COLLECTION/$esp32Id")
            .update(updateData)
            .await()

        Log.d(TAG, "ESP32 unassigned successfully")
    }
}