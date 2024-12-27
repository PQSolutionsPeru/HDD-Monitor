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

    fun observeUnassignedESP32s(): Flow<List<ESP32Device>> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null

        try {
            listenerRegistration = firestore.collection(ESP32_COLLECTION)
                .whereEqualTo("status", ESP32Device.STATUS_AWAITING_CONFIG)
                .whereEqualTo("panel_id", "")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error al observar ESP32s no asignados", error)
                        return@addSnapshotListener
                    }

                    val devices = snapshot?.documents?.mapNotNull { doc ->
                        doc.toESP32Device()
                    } ?: emptyList()

                    trySend(devices)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar listener de ESP32s no asignados", e)
            close(e)
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    fun observeESP32Status(esp32Id: String): Flow<String> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null

        try {
            listenerRegistration = firestore.collection(ESP32_COLLECTION).document(esp32Id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error al observar estado de ESP32 $esp32Id", error)
                        return@addSnapshotListener
                    }

                    val status = snapshot?.getString("status") ?: "UNKNOWN"
                    trySend(status)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar listener de estado de ESP32 $esp32Id", e)
            close(e)
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    private fun generateESP32Id(mac: String): String {
        // Convertir a formato que usa el ESP32: tomar los últimos 4 caracteres del MAC
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
                ?.let { doc ->
                    doc.toObject(ESP32Device::class.java)?.copy(documentName = esp32Id)
                }
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

        val updateData = mapOf(
            "client_id" to clientId,
            "panel_id" to panelId,
            "status" to ESP32Device.STATUS_AWAITING_CONFIG,
            "lastUpdate" to com.google.firebase.Timestamp.now()
        )

        firestore.document("$ESP32_COLLECTION/$esp32Id")
            .update(updateData)
            .await()

        Log.d(TAG, "ESP32 assigned successfully")
    }

    suspend fun updateNetworkInfo(
        esp32Id: String,
        ip: String,
        mac: String
    ): Result<Unit> = runCatching {
        Log.d(TAG, "Updating network info for ESP32: $esp32Id")

        val updateData = mapOf(
            "IP" to ip,
            "MAC" to mac.uppercase().replace(":", ""),
            "status" to ESP32Device.STATUS_AWAITING_CONFIG,
            "lastUpdate" to com.google.firebase.Timestamp.now()
        )

        firestore.document("$ESP32_COLLECTION/$esp32Id")
            .update(updateData)
            .await()

        Log.d(TAG, "Network info updated successfully")
    }

    suspend fun updateStatus(
        esp32Id: String,
        status: String
    ): Result<Unit> = runCatching {
        Log.d(TAG, "Updating status for ESP32: $esp32Id to $status")

        val updateData = mapOf(
            "status" to status,
            "lastUpdate" to com.google.firebase.Timestamp.now()
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
            "lastUpdate" to com.google.firebase.Timestamp.now()
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
            "lastUpdate" to com.google.firebase.Timestamp.now()
        )

        firestore.document("$ESP32_COLLECTION/$esp32Id")
            .update(updateData)
            .await()

        Log.d(TAG, "ESP32 assigned successfully")
    }
}