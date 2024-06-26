package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var firestore: FirebaseFirestore
    private var panelListener: ListenerRegistration? = null
    private var relaysListener: ListenerRegistration? = null
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Subscribe to relay-status topic
        FirebaseMessaging.getInstance().subscribeToTopic("relay-status")
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, "Subscription to notifications failed", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("AdminMainActivity", "Subscribed to relay-status")
                }
            }

        // Check if user is signed in (non-null) and update UI accordingly.
        if (auth.currentUser == null) {
            // Redirect to LoginActivity if the user is not signed in
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // User is signed in, proceed with loading data
            firestore = FirebaseFirestore.getInstance()
            loadPanelInfo()
            loadRelays()
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_create_alert -> {
                    startActivity(Intent(this, AlertManagementActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        // Check if user is signed in (non-null) and update UI accordingly.
        if (auth.currentUser == null) {
            // Redirect to LoginActivity if the user is not signed in
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
    }

    private fun loadPanelInfo() {
        val panelDocRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("panels")
            .document("panel_1")

        panelListener = panelDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val panelName = snapshot.getString("name") ?: "Unknown Panel"
                val panelLocation = snapshot.getString("location") ?: "Unknown Location"
                binding.panelName.text = panelName
                binding.panelLocation.text = panelLocation
            }
        }
    }

    private fun loadRelays() {
        val relaysCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("panels")
            .document("panel_1")
            .collection("relays")

        relaysListener = relaysCollectionRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            if (snapshots != null && !snapshots.isEmpty) {
                for (document in snapshots.documents) {
                    val relayName = document.id
                    val relayStatus = document.getString("status") ?: "Unknown"
                    val iconRes = if (relayStatus == "OK") {
                        R.drawable.ic_check_green
                    } else {
                        R.drawable.ic_close_red
                    }

                    when (relayName) {
                        "Alarma" -> {
                            binding.relayAlarmaIcon.setImageResource(iconRes)
                            binding.relayAlarmaText.text = relayName
                        }
                        "Problema" -> {
                            binding.relayProblemaIcon.setImageResource(iconRes)
                            binding.relayProblemaText.text = relayName
                        }
                        "Supervision" -> {
                            binding.relaySupervisionIcon.setImageResource(iconRes)
                            binding.relaySupervisionText.text = relayName
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        panelListener?.remove()
        relaysListener?.remove()
        super.onDestroy()
    }
}
