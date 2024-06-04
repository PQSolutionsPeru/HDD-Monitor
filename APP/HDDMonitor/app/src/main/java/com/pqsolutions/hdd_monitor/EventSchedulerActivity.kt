package com.pqsolutions.hdd_monitor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityEventSchedulerBinding

class EventSchedulerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventSchedulerBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventSchedulerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        binding.buttonScheduleEvent.setOnClickListener {
            val eventName = binding.editTextEventName.text.toString()
            val eventDate = binding.editTextEventDate.text.toString()
            val eventTime = binding.editTextEventTime.text.toString()

            if (eventName.isNotEmpty() && eventDate.isNotEmpty() && eventTime.isNotEmpty()) {
                scheduleEvent(eventName, eventDate, eventTime)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
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

    private fun scheduleEvent(eventName: String, eventDate: String, eventTime: String) {
        val event = hashMapOf(
            "name" to eventName,
            "date" to eventDate,
            "time" to eventTime,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("scheduled_events")
            .add(event)
            .addOnSuccessListener {
                Toast.makeText(this, "Event scheduled successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to schedule event", Toast.LENGTH_SHORT).show()
            }
    }
}
