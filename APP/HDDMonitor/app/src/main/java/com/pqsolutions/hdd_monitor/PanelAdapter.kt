package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ItemPanelBinding

class PanelAdapter(private var panels: List<Panel>) : RecyclerView.Adapter<PanelAdapter.PanelViewHolder>() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PanelViewHolder {
        val binding = ItemPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PanelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PanelViewHolder, position: Int) {
        holder.bind(panels[position])
    }

    override fun getItemCount(): Int = panels.size

    fun updatePanels(newPanels: List<Panel>) {
        panels = newPanels
        notifyDataSetChanged()
    }

    inner class PanelViewHolder(private val binding: ItemPanelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(panel: Panel) {
            binding.panel = panel

            // Cargar el estado de los relays
            val panelDocument = firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("clients")
                .document(panel.clientId)
                .collection("panels")
                .document(panel.id)

            panelDocument.collection("relays").get().addOnSuccessListener { snapshots ->
                val relayAlarma = snapshots.documents.find { it.id == "Alarma" }?.toObject(Relay::class.java)
                val relayProblema = snapshots.documents.find { it.id == "Problema" }?.toObject(Relay::class.java)
                val relaySupervision = snapshots.documents.find { it.id == "Supervision" }?.toObject(Relay::class.java)

                binding.relayAlarmaIcon.setImageResource(if (relayAlarma?.status == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
                binding.relayProblemaIcon.setImageResource(if (relayProblema?.status == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
                binding.relaySupervisionIcon.setImageResource(if (relaySupervision?.status == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)

                binding.executePendingBindings()
            }
        }
    }
}
