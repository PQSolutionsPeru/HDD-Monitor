package com.pqsolutions.hdd_monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pqsolutions.hdd_monitor.databinding.ItemClientBinding

class ClientAdapter(private val clickListener: (Client) -> Unit) : RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    private var clients: List<Client> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val binding = ItemClientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        holder.bind(clients[position], clickListener)
    }

    override fun getItemCount(): Int = clients.size

    fun updateClients(newClients: List<Client>) {
        clients = newClients
        notifyDataSetChanged()
    }

    class ClientViewHolder(private val binding: ItemClientBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(client: Client, clickListener: (Client) -> Unit) {
            binding.client = client
            binding.root.setOnClickListener { clickListener(client) }
            binding.executePendingBindings()
        }
    }
}
