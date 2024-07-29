package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PanelAdapter(
    private val panels: List<Panel>,
    private val onItemClick: (Panel) -> Unit
) : RecyclerView.Adapter<PanelAdapter.PanelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PanelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_panel, parent, false)
        return PanelViewHolder(view)
    }

    override fun onBindViewHolder(holder: PanelViewHolder, position: Int) {
        val panel = panels[position]
        holder.bind(panel, onItemClick)
    }

    override fun getItemCount(): Int = panels.size

    class PanelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val panelNameTextView: TextView = itemView.findViewById(R.id.panelName)
        private val panelLocationTextView: TextView = itemView.findViewById(R.id.panelLocation)

        fun bind(panel: Panel, onItemClick: (Panel) -> Unit) {
            panelNameTextView.text = panel.name
            panelLocationTextView.text = panel.location
            itemView.setOnClickListener { onItemClick(panel) }
        }
    }
}
