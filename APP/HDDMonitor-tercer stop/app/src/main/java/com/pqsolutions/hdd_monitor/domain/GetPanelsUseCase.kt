package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPanelsUseCase @Inject constructor(
    private val panelRepository: PanelRepository
) {
    operator fun invoke(params: Params): Flow<List<Panel>> {
        return if (params.clientDocName == null) {
            panelRepository.getPanelsForUser(UserData.createAdmin("", ""))
        } else {
            panelRepository.getPanelsForUser(UserData.createClientUser("", "", params.clientDocName, ""))
        }
    }

    data class Params(val clientDocName: String?)
}