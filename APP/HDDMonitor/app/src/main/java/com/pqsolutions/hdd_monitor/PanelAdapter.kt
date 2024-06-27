package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemPanelBinding

class PanelAdapter(private var panels: List<Panel>) : RecyclerView.Adapter<PanelAdapter.PanelViewHolder>() {

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
            binding.panelName.text = panel.name
            binding.relayStatusIcon.setImageResource(if (panel.status == "OK") R.drawable.ic_check_green else R.drawable.ic_close_red)
            // Update relay icons and statuses here
            binding.executePendingBindings()
        }
    }
}
