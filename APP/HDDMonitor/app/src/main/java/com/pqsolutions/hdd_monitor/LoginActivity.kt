package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityLoginBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                signIn(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, now check the user role
                    Log.d(TAG, "signInWithEmail:success")
                    checkUserRole(email)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserRole(email: String) {
        // Check if the user is an admin
        firestore.collection("hdd-monitor").document("accounts")
            .collection("admins")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // If not an admin, check if the user is a client
                    checkClientRole(email)
                } else {
                    // User is an admin
                    navigateToAdminMain()
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
                Toast.makeText(baseContext, "Error verifying user role.",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkClientRole(email: String) {
        firestore.collection("hdd-monitor").document("accounts")
            .collection("clients")
            .get()
            .addOnSuccessListener { clients ->
                for (client in clients) {
                    client.reference.collection("users")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (documents.isEmpty) {
                                // No user found
                                Toast.makeText(baseContext, "No user found with the provided email.",
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                // User is a client
                                navigateToMain()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.w(TAG, "Error getting documents: ", exception)
                            Toast.makeText(baseContext, "Error verifying user role.",
                                Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
                Toast.makeText(baseContext, "Error verifying user role.",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToAdminMain() {
        val intent = Intent(this, AdminMainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
