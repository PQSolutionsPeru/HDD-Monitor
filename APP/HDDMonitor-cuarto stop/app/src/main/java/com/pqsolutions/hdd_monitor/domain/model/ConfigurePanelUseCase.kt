package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.bluetooth.BleConfiguration
import com.pqsolutions.hdd_monitor.bluetooth.BleManager
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class ConfigurePanelUseCase @Inject constructor(
    private val panelRepository: PanelRepository,
    private val bleManager: BleManager
) {
    suspend operator fun invoke(
        bleConfig: BleConfiguration,
        panel: Panel
    ): Flow<BleState> = flow {
        try {
            emit(BleState.CreatingPanel)

            // Crear panel en Firestore
            val panelId = panelRepository.createNewPanel(bleConfig.clientId, panel)
                .getOrThrow()

            emit(BleState.PanelCreated)

            // Esperar a que BLE esté listo
            withTimeout(10000) { // 10 segundos de timeout
                bleManager.waitForConnection()
            }

            // Enviar configuración al ESP32
            val configMessage = bleConfig.copy(panelId = panelId).toBleMessage()
            val sent = bleManager.sendData(configMessage)

            if (!sent) {
                throw Exception("Error enviando configuración al ESP32")
            }

            emit(BleState.ConfigurationReceived)

            // Observar actualizaciones del panel
            panelRepository.observePanelUpdates(bleConfig.clientId, panelId)
                .collect { updatedPanel ->
                    when {
                        updatedPanel == null -> {
                            throw Exception("Panel no encontrado")
                        }
                        updatedPanel.SSID_CON == "OK" && updatedPanel.ESP32_IP.isNotBlank() -> {
                            emit(BleState.ConfigurationSuccess)
                            emit(BleState.DataSent)
                        }
                        updatedPanel.SSID_CON == "DISC" -> {
                            throw Exception("Error de conexión WiFi")
                        }
                    }
                }

        } catch (e: Exception) {
            emit(BleState.Error("Error en configuración: ${e.message}"))
        }
    }
}