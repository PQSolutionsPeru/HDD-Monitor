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
    private lateinit var userAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        userAdapter = UserAdapter(emptyList()) { user ->
            // Handle user item click for editing or deleting
            val intent = Intent(this, AddEditUserActivity::class.java).apply {
                putExtra("USER_ID", user.ID)
            }
            startActivity(intent)
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminMainActivity)
            adapter = userAdapter
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, AdminMainActivity::class.java))
                    true
                }
                R.id.nav_create_user -> {
                    startActivity(Intent(this, AddEditUserActivity::class.java))
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

        loadUsers()
    }

    private fun loadUsers() {
        val usersCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("users")

        usersCollectionRef.get().addOnSuccessListener { snapshots ->
            val users = snapshots.documents.mapNotNull { it.toObject(User::class.java) }
            userAdapter.updateUsers(users)
        }
    }
}
