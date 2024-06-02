package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityAlertManagementBinding

class AlertManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlertManagementBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var alertAdapter: AlertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        alertAdapter = AlertAdapter(emptyList()) {
            // Handle alert item click for editing or deleting
        }
        binding.alertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AlertManagementActivity)
            adapter = alertAdapter
        }

        binding.createAlertButton.setOnClickListener {
            val intent = Intent(this, AddEditAlertActivity::class.java)
            startActivity(intent)
        }

        loadAlerts()
    }

    private fun loadAlerts() {
        val alertsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("admins")
            .document("admin_1")
            .collection("custom_notifications")

        alertsCollectionRef.get().addOnSuccessListener { snapshots ->
            val alerts = snapshots.documents.mapNotNull { it.toObject(Alert::class.java) }
            alertAdapter.updateAlerts(alerts)
        }
    }
}
