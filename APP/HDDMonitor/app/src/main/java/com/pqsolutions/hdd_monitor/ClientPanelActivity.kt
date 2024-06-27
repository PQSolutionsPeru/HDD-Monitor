package com.pqsolutions.hdd_monitor

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityClientPanelBinding

class ClientPanelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClientPanelBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panelAdapter: PanelAdapter
    private var clientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        clientId = intent.getStringExtra("CLIENT_ID")

        panelAdapter = PanelAdapter(emptyList())
        binding.panelRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.panelRecyclerView.adapter = panelAdapter

        if (clientId != null) {
            loadPanels(clientId!!)
        }
    }

    private fun loadPanels(clientId: String) {
        val panelsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document(clientId)
            .collection("panels")

        panelsCollectionRef.get().addOnSuccessListener { snapshots ->
            if (snapshots != null && !snapshots.isEmpty) {
                val panels = snapshots.documents.mapNotNull { document ->
                    val panel = document.toObject(Panel::class.java)
                    if (panel != null) {
                        panel.id = document.id
                        panel.clientId = clientId  // Asignar clientId al panel
                    }
                    panel
                }
                panelAdapter.updatePanels(panels)
                binding.noPanelsTextView.visibility = View.GONE
                binding.panelRecyclerView.visibility = View.VISIBLE
            } else {
                binding.noPanelsTextView.text = "Cliente aún no tiene paneles"
                binding.noPanelsTextView.visibility = View.VISIBLE
                binding.panelRecyclerView.visibility = View.GONE
            }
        }.addOnFailureListener { e ->
            binding.noPanelsTextView.text = "Error al cargar los paneles: ${e.message}"
            binding.noPanelsTextView.visibility = View.VISIBLE
            binding.panelRecyclerView.visibility = View.GONE
        }
    }
}
