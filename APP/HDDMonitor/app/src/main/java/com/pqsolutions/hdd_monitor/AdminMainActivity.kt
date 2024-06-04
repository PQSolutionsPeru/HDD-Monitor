package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panelAdapter: PanelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        panelAdapter = PanelAdapter(emptyList())
        binding.panelsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminMainActivity)
            adapter = panelAdapter
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_create_alert -> {
                    startActivity(Intent(this, AddEditAlertActivity::class.java))
                    true
                }
                R.id.nav_schedule_event -> {
                    startActivity(Intent(this, EventSchedulerActivity::class.java))
                    true
                }
                R.id.nav_manage_users -> {
                    startActivity(Intent(this, UserManagementActivity::class.java))
                    true
                }
                else -> false
            }
        }

        loadClientData()
    }

    private fun loadClientData() {
        val clientDocRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")

        clientDocRef.collection("users").document("user_1").get().addOnSuccessListener { userSnapshot ->
            val clientName = userSnapshot.getString("name") ?: "Cliente Desconocido"
            binding.clientNameTextView.text = "Cliente: $clientName"
        }

        clientDocRef.collection("panels").get().addOnSuccessListener { panelSnapshots ->
            val panels = mutableListOf<Panel>()
            for (documentSnapshot in panelSnapshots.documents) {
                val panel = documentSnapshot.toObject(Panel::class.java)
                if (panel != null) {
                    documentSnapshot.reference.collection("relays").get().addOnSuccessListener { relaySnapshots ->
                        val relays = relaySnapshots.documents.mapNotNull { relaySnapshot ->
                            relaySnapshot.toObject(Relay::class.java)?.copy(name = relaySnapshot.id)
                        }
                        panel.relays = relays.associateBy { it.name }
                        panels.add(panel)
                        panelAdapter.updatePanels(panels)
                    }
                }
            }
        }
    }
}
