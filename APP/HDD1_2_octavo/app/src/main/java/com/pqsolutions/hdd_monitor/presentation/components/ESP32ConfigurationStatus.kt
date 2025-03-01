package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.theme.HddBlue
import com.pqsolutions.hdd_monitor.presentation.theme.HddGreen
import com.pqsolutions.hdd_monitor.presentation.theme.HddRed

enum class ESP32ConfigStep {
    BLE_CONNECT,
    WIFI_CONFIG,
    MQTT_CONNECT,
    RECEIVING_CONFIG,
    RELAY_CONFIG
}

data class ConfigStep(
    val step: ESP32ConfigStep,
    val icon: ImageVector,
    val title: String,
    val isCompleted: Boolean,
    val isActive: Boolean,
    val error: String? = null
)

@Composable
fun ESP32ConfigurationStatus(
    bleConnected: Boolean = false,
    wifiConfigured: Boolean = false,
    wifiError: String? = null,
    mqttConnected: Boolean = false,
    configReceived: Boolean = false,
    relaysConfigured: Boolean = false,
    currentStep: ESP32ConfigStep = ESP32ConfigStep.BLE_CONNECT,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        ConfigStep(
            step = ESP32ConfigStep.BLE_CONNECT,
            icon = Icons.Default.Bluetooth,
            title = stringResource(R.string.ble_connection),
            isCompleted = bleConnected,
            isActive = currentStep == ESP32ConfigStep.BLE_CONNECT
        ),
        ConfigStep(
            step = ESP32ConfigStep.WIFI_CONFIG,
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.wifi_configuration),
            isCompleted = wifiConfigured,
            isActive = currentStep == ESP32ConfigStep.WIFI_CONFIG,
            error = wifiError
        ),
        ConfigStep(
            step = ESP32ConfigStep.MQTT_CONNECT,
            icon = Icons.Default.Cloud,
            title = stringResource(R.string.mqtt_connection),
            isCompleted = mqttConnected,
            isActive = currentStep == ESP32ConfigStep.MQTT_CONNECT
        ),
        ConfigStep(
            step = ESP32ConfigStep.RECEIVING_CONFIG,
            icon = Icons.Default.Settings,
            title = stringResource(R.string.receiving_config),
            isCompleted = configReceived,
            isActive = currentStep == ESP32ConfigStep.RECEIVING_CONFIG
        ),
        ConfigStep(
            step = ESP32ConfigStep.RELAY_CONFIG,
            icon = Icons.Default.Build,
            title = stringResource(R.string.relay_configuration),
            isCompleted = relaysConfigured,
            isActive = currentStep == ESP32ConfigStep.RELAY_CONFIG
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.configuration_status),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            steps.forEachIndexed { index, step ->
                ConfigurationStep(
                    step = step,
                    isLastStep = index == steps.lastIndex
                )
            }
        }
    }
}

@Composable
private fun ConfigurationStep(
    step: ConfigStep,
    isLastStep: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono con círculo de fondo
            ConfigStepIcon(step)

            Spacer(modifier = Modifier.width(16.dp))

            // Título y estado
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        step.isCompleted -> HddGreen
                        step.error != null -> HddRed
                        step.isActive -> HddBlue
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Mensaje de error si existe
                AnimatedVisibility(
                    visible = step.error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    step.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Indicador de estado
            ConfigStepStatus(step)
        }

        // Línea conectora entre pasos
        if (!isLastStep) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .height(24.dp)
                    .width(2.dp)
                    .background(
                        color = if (step.isCompleted) HddGreen
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun ConfigStepIcon(step: ConfigStep) {
    val backgroundColor = when {
        step.isCompleted -> HddGreen
        step.error != null -> HddRed
        step.isActive -> HddBlue
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = step.icon,
            contentDescription = step.title,
            tint = backgroundColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ConfigStepStatus(step: ConfigStep) {
    when {
        step.isCompleted -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.completed),
                tint = HddGreen,
                modifier = Modifier.size(24.dp)
            )
        }
        step.error != null -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(R.string.error),
                tint = HddRed,
                modifier = Modifier.size(24.dp)
            )
        }
        step.isActive -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = HddBlue
            )
        }
    }
}