package com.pqsolutions.hdd_monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    fun loadPanels(clientId: String, onResult: (List<Panel>) -> Unit) {
        viewModelScope.launch {
            val panelsCollection = db.collection("hdd-monitor")
                .document("accounts")
                .collection("clients")
                .document(clientId)
                .collection("panels")

            panelsCollection.get().addOnSuccessListener { result ->
                val panels = result.mapNotNull { it.toObject(Panel::class.java) }
                onResult(panels)
            }.addOnFailureListener {
                onResult(emptyList())
            }
        }
    }
}
