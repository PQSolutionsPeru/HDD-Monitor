package com.pqsolutions.hdd_monitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var firestore: FirebaseFirestore
    private var relayListeners = mutableListOf<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)

        loadRelays()
    }

    override fun onDestroy() {
        super.onDestroy()
        relayListeners.forEach { it.remove() }
    }

    private fun loadRelays() {
        val relayCollections = listOf("Alarma", "Problema", "Supervision")
        val relays = mutableMapOf<String, Relay>()

        for (collection in relayCollections) {
            val listenerRegistration = firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("clients")
                .document("client_1")
                .collection("panels")
                .document("panel_1")
                .collection("relays")
                .document(collection)
                .addSnapshotListener { document, error ->
                    if (error != null) {
                        // Maneja el error
                        error.printStackTrace()
                        return@addSnapshotListener
                    }
                    document?.let {
                        val relay = it.toObject(Relay::class.java)
                        relay?.let { r ->
                            r.name = collection // Usamos el nombre del documento como el nombre del relay
                            relays[collection] = r
                            updateRecyclerView(relays.values.toList())
                        }
                    }
                }
            relayListeners.add(listenerRegistration)
        }
    }

    private fun updateRecyclerView(relays: List<Relay>) {
        binding.recyclerView.adapter?.let {
            (it as RelayAdapter).updateRelays(relays)
        } ?: run {
            binding.recyclerView.adapter = RelayAdapter(relays)
        }
    }
}
