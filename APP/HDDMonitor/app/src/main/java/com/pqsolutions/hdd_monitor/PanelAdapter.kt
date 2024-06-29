package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemPanelBinding

class PanelAdapter : RecyclerView.Adapter<PanelAdapter.PanelViewHolder>() {

    private var panels: List<Panel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PanelViewHolder {
        val binding = ItemPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PanelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PanelViewHolder, position: Int) {
        holder.bind(panels[position])
    }

    override fun getItemCount(): Int {
        return panels.size
    }

    fun updatePanels(newPanels: List<Panel>) {
        panels = newPanels
        notifyDataSetChanged()
    }

    inner class PanelViewHolder(private val binding: ItemPanelBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(panel: Panel) {
            binding.panelName.text = panel.name
            binding.panelLocation.text = panel.location
            // Bind other data if needed
        }
    }
}
