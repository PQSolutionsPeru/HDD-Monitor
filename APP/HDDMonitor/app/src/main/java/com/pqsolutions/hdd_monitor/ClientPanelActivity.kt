package com.pqsolutions.hdd_monitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.databinding.ActivityClientPanelBinding

class ClientPanelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClientPanelBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panelAdapter: PanelAdapter
    private var panelListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val clientId = intent.getStringExtra("CLIENT_ID") ?: return

        firestore = FirebaseFirestore.getInstance()
        panelAdapter = PanelAdapter(emptyList())
        binding.panelRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ClientPanelActivity)
            adapter = panelAdapter
        }

        loadPanels(clientId)
    }

    private fun loadPanels(clientId: String) {
        val panelsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document(clientId)
            .collection("panels")

        panelListener = panelsCollectionRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                // Handle error
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val panels = snapshots.documents.mapNotNull { it.toObject(Panel::class.java) }
                panelAdapter.updatePanels(panels)
            }
        }
    }

    override fun onDestroy() {
        panelListener?.remove()
        super.onDestroy()
    }
}
