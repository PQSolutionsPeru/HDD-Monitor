package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityAddEditAlertBinding

class AddEditAlertActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditAlertBinding
    private lateinit var firestore: FirebaseFirestore
    private var alertId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        alertId = intent.getStringExtra("ALERT_ID")

        if (alertId != null) {
            loadAlertData(alertId!!)
        }

        binding.saveButton.setOnClickListener {
            saveAlertData()
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, AdminMainActivity::class.java))
                    true
                }
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
    }

    private fun loadAlertData(alertId: String) {
        val alertDocRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("admins")
            .document("admin_1")
            .collection("custom_notifications")
            .document(alertId)

        alertDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val alert = document.toObject(Alert::class.java)
                binding.alert = alert
            } else {
                Toast.makeText(this, "Alert not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAlertData() {
        val alert = binding.alert ?: return

        if (alert.title.isEmpty() || alert.text.isEmpty() || alert.status.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (alertId == null) {
            // Add new alert
            firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("admins")
                .document("admin_1")
                .collection("custom_notifications")
                .add(alert)
                .addOnSuccessListener {
                    Toast.makeText(this, "Alert added successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to add alert", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Update existing alert
            firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("admins")
                .document("admin_1")
                .collection("custom_notifications")
                .document(alertId!!)
                .set(alert)
                .addOnSuccessListener {
                    Toast.makeText(this, "Alert updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update alert", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
