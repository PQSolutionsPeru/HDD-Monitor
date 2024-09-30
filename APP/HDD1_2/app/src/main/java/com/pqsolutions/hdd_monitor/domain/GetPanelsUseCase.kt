package com.pqsolutions.hdd_monitor.domain

import android.util.Log
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class GetPanelsUseCase @Inject constructor(
    private val panelRepository: PanelRepository
) {
    operator fun invoke(params: Params): Flow<List<Panel>> {
        Log.d(TAG, "GetPanelsUseCase invoked with clientId: ${params.clientId}")
        return panelRepository.getPanelsFlow(params.clientId)
            .onEach { panels ->
                Log.d(TAG, "Panels received: ${panels.size}")
                panels.forEach { panel ->
                    Log.d(TAG, "Panel: ${panel.name}, Relays: ${panel.relays.size}")
                }
            }
            .catch { error ->
                Log.e(TAG, "Error in GetPanelsUseCase: ${error.message}", error)
                throw error
            }
    }

    data class Params(val clientId: String?)

    companion object {
        private const val TAG = "GetPanelsUseCase"
    }
}