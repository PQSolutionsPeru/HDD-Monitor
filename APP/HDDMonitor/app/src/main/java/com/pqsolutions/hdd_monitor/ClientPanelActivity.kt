package com.pqsolutions.hdd_monitor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.pqsolutions.hdd_monitor.databinding.ActivityClientPanelBinding

class ClientPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientPanelBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panelAdapter: PanelAdapter
    private var panelListener: ListenerRegistration? = null
    private var relaysListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        val clientId = intent.getStringExtra("CLIENT_ID")
        Log.d("ClientPanelActivity", "Received client ID: $clientId")
        if (clientId != null) {
            loadClientPanels(clientId)
        } else {
            Toast.makeText(this, "Client ID is missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        panelAdapter = PanelAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = panelAdapter
        Log.d("ClientPanelActivity", "RecyclerView setup completed")
    }

    private fun loadClientPanels(clientId: String) {
        val panelsCollectionRef = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document("client_1")
            .collection("panels")

        Log.d("ClientPanelActivity", "Loading panels for client ID: $clientId from collection: ${panelsCollectionRef.path}")

        panelsCollectionRef.get()
            .addOnSuccessListener { querySnapshot: QuerySnapshot ->
                Log.d("ClientPanelActivity", "Query successful. Number of documents: ${querySnapshot.size()}")
                if (!querySnapshot.isEmpty) {
                    val panels = querySnapshot.documents.mapNotNull { document ->
                        Log.d("ClientPanelActivity", "Document ID: ${document.id}, Data: ${document.data}")
                        try {
                            val panel = document.toObject(Panel::class.java)
                            if (panel != null) {
                                panel.id = document.id
                                Log.d("ClientPanelActivity", "Panel loaded: $panel")
                            } else {
                                Log.d("ClientPanelActivity", "Panel is null for document ID: ${document.id}")
                            }
                            panel
                        } catch (e: Exception) {
                            Log.e("ClientPanelActivity", "Error converting document to Panel: ${e.message}")
                            null
                        }
                    }
                    Log.d("ClientPanelActivity", "Panels found: ${panels.size}")
                    panelAdapter.updatePanels(panels)
                    binding.noPanelsTextView.visibility = View.GONE

                    if (panels.isNotEmpty()) {
                        showPanelDetails(panels.first())
                        loadRelays("client_1", panels.first().id)
                    }
                } else {
                    Log.d("ClientPanelActivity", "No panels found for client ID: $clientId")
                    binding.noPanelsTextView.visibility = View.VISIBLE
                    binding.noPanelsTextView.text = getString(R.string.no_panels_found)
                    hidePanelDetails()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar los paneles: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ClientPanelActivity", "Error loading panels: ${e.message}")
                hidePanelDetails()
            }
    }

    private fun showPanelDetails(panel: Panel) {
        Log.d("ClientPanelActivity", "Showing panel details: ${panel.name}, ${panel.location}")
        binding.panelIcon.visibility = View.VISIBLE
        binding.panelName.visibility = View.VISIBLE
        binding.panelLocation.visibility = View.VISIBLE
        binding.relaysLayout.visibility = View.VISIBLE
        binding.panelName.text = panel.name
        binding.panelLocation.text = panel.location

        Log.d("ClientPanelActivity", "Panel details displayed: $panel")

        val alarmIconRes = if (panel.alarmaStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red
        binding.relayAlarmaIcon.setImageResource(alarmIconRes)
        binding.relayAlarmaText.text = "Alarma"

        val problemaIconRes = if (panel.problemaStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red
        binding.relayProblemaIcon.setImageResource(problemaIconRes)
        binding.relayProblemaText.text = "Problema"

        val supervisionIconRes = if (panel.supervisionStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red
        binding.relaySupervisionIcon.setImageResource(supervisionIconRes)
        binding.relaySupervisionText.text = "Supervision"
    }

    private fun loadRelays(clientId: String, panelId: String) {
        val baseRelaysPath = firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document(clientId)
            .collection("panels")
            .document(panelId)
            .collection("relays")

        val relayNames = listOf("Alarma", "Supervision", "Problema")

        relayNames.forEach { relayName ->
            val relayDocRef = baseRelaysPath.document(relayName)
            relayDocRef.get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val relayStatus = documentSnapshot.getString("status") ?: "Unknown"
                        Log.d("ClientPanelActivity", "Relay: $relayName, Status: $relayStatus")
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
                    } else {
                        Log.d("ClientPanelActivity", "No data found for relay: $relayName")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ClientPanelActivity", "Error loading relay: $relayName, ${e.message}")
                }
        }
    }

    private fun hidePanelDetails() {
        Log.d("ClientPanelActivity", "Hiding panel details")
        binding.panelIcon.visibility = View.GONE
        binding.panelName.visibility = View.GONE
        binding.panelLocation.visibility = View.GONE
        binding.relaysLayout.visibility = View.GONE
    }

    override fun onDestroy() {
        panelListener?.remove()
        relaysListener?.remove()
        super.onDestroy()
    }
}
