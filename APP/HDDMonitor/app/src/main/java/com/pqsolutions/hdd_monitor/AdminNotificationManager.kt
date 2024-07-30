package com.pqsolutions.hdd_monitor

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminNotificationManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    fun startListeningForChanges() {
        val clientsRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")

        clientsRef.get().addOnSuccessListener { clientSnapshot ->
            for (clientDoc in clientSnapshot.documents) {
                val clientId = clientDoc.id
                val panelsRef = clientDoc.reference.collection("panels")

                panelsRef.get().addOnSuccessListener { panelSnapshot ->
                    for (panelDoc in panelSnapshot.documents) {
                        val panelId = panelDoc.id
                        val relaysRef = panelDoc.reference.collection("relays")

                        val listener = relaysRef.addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.w("AdminNotificationManager", "Listen failed.", e)
                                return@addSnapshotListener
                            }

                            for (dc in snapshot!!.documentChanges) {
                                val relay = dc.document.toObject(Relay::class.java)
                                if (dc.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                                    // Aquí es donde notificarías el cambio
                                    notifyRelayChange(clientId, panelId, relay)
                                }
                            }
                        }
                        listeners.add(listener)
                    }
                }
            }
        }
    }

    private fun notifyRelayChange(clientId: String, panelId: String, relay: Relay) {
        val title = "Cambio en Relay"
        val message = "El relay ${relay.name} del panel $panelId del cliente $clientId ha cambiado a ${relay.status}"

        // Usar MyFirebaseMessagingService para enviar la notificación
        val intent = MyFirebaseMessagingService.createNotificationIntent(context, title, message)
        MyFirebaseMessagingService.sendNotification(context, intent, title, message)
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}