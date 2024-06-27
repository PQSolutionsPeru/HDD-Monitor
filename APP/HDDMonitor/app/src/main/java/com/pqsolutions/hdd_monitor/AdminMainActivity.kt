package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var clientAdapter: ClientAdapter
    private var clientListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        clientAdapter = ClientAdapter(emptyList()) { client ->
            val intent = Intent(this, ClientPanelActivity::class.java)
            intent.putExtra("CLIENT_ID", client.ID)
            startActivity(intent)
        }
        binding.clientRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminMainActivity)
            adapter = clientAdapter
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
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

        loadClients()
    }

    private fun loadClients() {
        val clientsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")

        clientListener = clientsCollectionRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                // Handle error
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val clients = snapshots.documents.mapNotNull { it.toObject(Client::class.java) }
                clientAdapter.updateClients(clients)
            }
        }
    }

    override fun onDestroy() {
        clientListener?.remove()
        super.onDestroy()
    }
}
