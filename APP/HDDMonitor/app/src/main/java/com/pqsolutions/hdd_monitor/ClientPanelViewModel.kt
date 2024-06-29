package com.pqsolutions.hdd_monitor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore

class ClientPanelViewModel : ViewModel() {
    private val _panels = MutableLiveData<List<Panel>>()
    val panels: LiveData<List<Panel>> = _panels

    fun loadPanels(clientId: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("hdd-monitor")
            .document("accounts")
            .collection("clients")
            .document(clientId)
            .collection("panels")
            .get()
            .addOnSuccessListener { result ->
                val panels = result.map { document ->
                    document.toObject(Panel::class.java).apply {
                        id = document.id
                    }
                }
                _panels.value = panels
            }
    }
}
