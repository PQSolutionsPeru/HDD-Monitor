package com.pqsolutions.hddmonitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hddmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        binding.recyclerView.setHasFixedSize(true)

        loadRelays() // Llamar al método para cargar los datos al iniciar la actividad
    }

    private fun loadRelays() {
        firestore.collection("clientes").document("clienteId").collection("relays")
            .get()
            .addOnSuccessListener { documents ->
                val relays = documents.map { it.toObject(Relay::class.java) }
                binding.recyclerView.adapter = RelayAdapter(relays) // Usar binding.recyclerView
            }
            .addOnFailureListener { _ ->
                // Manejar errores
            }
    }
}
