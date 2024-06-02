package com.pqsolutions.hdd_monitor

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityAddEditUserBinding

class AddEditUserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditUserBinding
    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        userId = intent.getStringExtra("USER_ID")

        if (userId != null) {
            loadUserData(userId!!)
        } else {
            binding.user = User() // Inicializa un nuevo usuario si no hay userId
        }

        binding.saveButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData(userId: String) {
        val userDocRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("users")
            .document(userId)

        userDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val user = document.toObject(User::class.java)
                binding.user = user
            } else {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserData() {
        val user = binding.user
        if (user != null) {
            if (user.username.isEmpty() || user.email.isEmpty() || user.password.isEmpty() || user.role.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return
            }

            if (userId == null) {
                // Add new user
                firestore.collection("hdd-monitor")
                    .document("accounts")
                    .collection("clients")
                    .document("client_1")
                    .collection("users")
                    .add(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, "User added successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to add user", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Update existing user
                firestore.collection("hdd-monitor")
                    .document("accounts")
                    .collection("clients")
                    .document("client_1")
                    .collection("users")
                    .document(userId!!)
                    .set(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, "User updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update user", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}
