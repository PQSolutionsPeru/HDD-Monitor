package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemAlertBinding

class AlertAdapter(private var alerts: List<Alert>, private val itemClickListener: (Alert) -> Unit) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position], itemClickListener)
    }

    override fun getItemCount(): Int = alerts.size

    fun updateAlerts(newAlerts: List<Alert>) {
        alerts = newAlerts
        notifyDataSetChanged()
    }

    inner class AlertViewHolder(private val binding: ItemAlertBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(alert: Alert, clickListener: (Alert) -> Unit) {
            binding.apply {
                alertTitle.text = alert.title
                alertText.text = alert.text
                alertStatus.text = alert.status
                root.setOnClickListener { clickListener(alert) }
            }
        }
    }
}
