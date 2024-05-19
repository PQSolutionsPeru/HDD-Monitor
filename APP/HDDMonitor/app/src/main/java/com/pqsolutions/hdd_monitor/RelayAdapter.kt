package com.pqsolutions.hddmonitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hddmonitor.databinding.ItemRelayBinding

class RelayAdapter(private val relays: List<Relay>) : RecyclerView.Adapter<RelayAdapter.RelayViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelayViewHolder {
        val binding = ItemRelayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RelayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RelayViewHolder, position: Int) {
        val relay = relays[position]
        holder.bind(relay)
    }

    override fun getItemCount() = relays.size

    inner class RelayViewHolder(private val binding: ItemRelayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(relay: Relay) {
            binding.relayIdTextView.text = relay.id
            binding.relayStateView.text = if (relay.state) "ON" else "OFF"
        }
    }
}
