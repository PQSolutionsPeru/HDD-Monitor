package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemRelayBinding

data class Relay(
    val name: String = "",
    val status: String = "",
    val dateTime: String = ""
)

class RelayAdapter(private val relays: List<Relay>) : RecyclerView.Adapter<RelayAdapter.RelayViewHolder>() {

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
            binding.relay = relay
            binding.executePendingBindings()
        }
    }
}
