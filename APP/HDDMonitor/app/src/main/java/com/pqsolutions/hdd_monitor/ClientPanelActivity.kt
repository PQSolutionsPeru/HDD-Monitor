package com.pqsolutions.hdd_monitor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ClientPanelActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noPanelsTextView: TextView
    private lateinit var panelDetailsLayout: LinearLayout
    private lateinit var panelNameTextView: TextView
    private lateinit var panelLocationTextView: TextView
    private lateinit var relayAlarmaIcon: ImageView
    private lateinit var relayProblemaIcon: ImageView
    private lateinit var relaySupervisionIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_panel)

        recyclerView = findViewById(R.id.recyclerView)
        noPanelsTextView = findViewById(R.id.noPanelsTextView)
        panelDetailsLayout = findViewById(R.id.panelDetailsLayout)
        panelNameTextView = findViewById(R.id.panelName)
        panelLocationTextView = findViewById(R.id.panelLocation)
        relayAlarmaIcon = findViewById(R.id.relayAlarmaIcon)
        relayProblemaIcon = findViewById(R.id.relayProblemaIcon)
        relaySupervisionIcon = findViewById(R.id.relaySupervisionIcon)

        val clientId = intent.getStringExtra("CLIENT_ID")
        Log.d("ClientPanelActivity", "Received client ID: $clientId")
        if (clientId != null) {
            loadPanels(clientId)
        } else {
            Log.e("ClientPanelActivity", "No client ID provided")
            showNoPanelsFound()
        }
    }

    private fun loadPanels(clientId: String) {
        val db = FirebaseFirestore.getInstance()
        val panelsRef = db.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document(clientId)
            .collection("panels")

        panelsRef.get().addOnSuccessListener { result ->
            val panels = result.documents.mapNotNull {
                Log.d("ClientPanelActivity", "Document ID: ${it.id}, Data: ${it.data}")
                it.toObject(Panel::class.java)?.apply { id = it.id }
            }
            if (panels.isEmpty()) {
                Log.d("ClientPanelActivity", "No panels found for client ID: $clientId")
                showNoPanelsFound()
            } else {
                Log.d("ClientPanelActivity", "Panels found: ${panels.size}")
                showPanels(panels)
            }
        }.addOnFailureListener { exception ->
            Log.e("ClientPanelActivity", "Error getting panels: ", exception)
            showNoPanelsFound()
        }
    }

    private fun showNoPanelsFound() {
        recyclerView.visibility = View.GONE
        noPanelsTextView.visibility = View.VISIBLE
    }

    private fun showPanels(panels: List<Panel>) {
        recyclerView.visibility = View.VISIBLE
        noPanelsTextView.visibility = View.GONE

        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = PanelAdapter(panels) { panel -> showPanelDetails(panel) }
        recyclerView.adapter = adapter
    }

    private fun showPanelDetails(panel: Panel) {
        panelDetailsLayout.visibility = View.VISIBLE
        panelNameTextView.text = panel.name
        panelLocationTextView.text = panel.location
        // Configura los iconos y los textos para los relés aquí según el estado del panel
        updateRelayStatus(panel)
    }

    private fun updateRelayStatus(panel: Panel) {
        // Aquí debes actualizar los iconos y textos de los relés según el estado del panel
        // Esto es solo un ejemplo de cómo puedes hacerlo
        relayAlarmaIcon.setImageResource(if (panel.alarmaStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
        relayProblemaIcon.setImageResource(if (panel.problemaStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
        relaySupervisionIcon.setImageResource(if (panel.supervisionStatus == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
    }
}
