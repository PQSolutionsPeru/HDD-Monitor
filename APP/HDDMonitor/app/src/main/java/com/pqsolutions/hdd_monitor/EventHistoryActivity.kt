package com.pqsolutions.hdd_monitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityEventHistoryBinding

class EventHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventHistoryBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var eventAdapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        eventAdapter = EventAdapter(emptyList())
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@EventHistoryActivity)
            adapter = eventAdapter
        }

        loadEventHistory()
    }

    private fun loadEventHistory() {
        val eventsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("panels")
            .document("panel_1")
            .collection("panel_events_log")

        eventsCollectionRef.get().addOnSuccessListener { snapshots ->
            val events = snapshots.documents.mapNotNull { it.toObject(Event::class.java) }
            eventAdapter.updateEvents(events)
        }
    }
}
