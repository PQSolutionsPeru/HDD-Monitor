package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemRelayBinding

data class Relay(
    var name: String = "",
    val status: String = "",
    val dateTime: String = ""
)

class RelayAdapter(private var relays: List<Relay>) : RecyclerView.Adapter<RelayAdapter.RelayViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelayViewHolder {
        val binding = ItemRelayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RelayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RelayViewHolder, position: Int) {
        holder.bind(relays[position])
    }

    override fun getItemCount(): Int = relays.size

    inner class RelayViewHolder(private val binding: ItemRelayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(relay: Relay) {
            binding.relayName.text = relay.name
            binding.relayStatus.text = relay.status
            binding.relayDateTime.text = relay.dateTime

            // Cambia el color del estado según el status
            val color = if (relay.status == "OK") {
                ContextCompat.getColor(binding.root.context, android.R.color.holo_green_dark)
            } else {
                ContextCompat.getColor(binding.root.context, android.R.color.holo_red_dark)
            }
            binding.relayStatus.setTextColor(color)
        }
    }

    fun updateRelays(newRelays: List<Relay>) {
        relays = newRelays
        notifyDataSetChanged()
    }
}
