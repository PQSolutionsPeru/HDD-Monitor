package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var clientAdapter: ClientAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupBottomNavigationView()
        loadAdminData()
        loadClientData()
    }

    private fun setupRecyclerView() {
        clientAdapter = ClientAdapter { client ->
            val intent = Intent(this, ClientPanelActivity::class.java).apply {
                putExtra("CLIENT_ID", client.ID)
            }
            startActivity(intent)
        }
        binding.clientRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.clientRecyclerView.adapter = clientAdapter
    }

    private fun setupBottomNavigationView() {
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

    private fun loadAdminData() {
        val adminId = "ADM001" // Suponiendo que tienes el ID del administrador de alguna manera
        val adminRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("admins")
            .document(adminId)

        adminRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val adminName = document.getString("name") ?: "Administrador"
                    binding.welcomeTextView.text = "Bienvenido $adminName"
                } else {
                    Toast.makeText(this, "No se encontró la información del administrador", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar la información del administrador: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadClientData() {
        val clientsRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")

        clientsRef.get()
            .addOnSuccessListener { querySnapshot ->
                val clients = querySnapshot.documents.mapNotNull { document ->
                    document.toObject(Client::class.java)
                }
                clientAdapter.updateClients(clients)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar la lista de clientes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
