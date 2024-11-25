package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ClientManagementDialogs"
private const val VALIDATION_DEBOUNCE = 300L

@Composable
private fun ValidationError(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun validateField(value: String, field: String): String? {
    return if (value.isBlank()) {
        // Aquí normalmente usaríamos context.getString pero como es una función top-level,
        // retornaremos un mensaje estático
        "Este campo es requerido"
    } else null
}

@Composable
fun PanelDialog(
    panel: Panel?,
    onDismiss: () -> Unit,
    onConfirm: (Panel) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var validationJob by remember { mutableStateOf<Job?>(null) }

    // Estados memoizados para mejor rendimiento
    val nameState = rememberSaveable { mutableStateOf(panel?.name ?: "") }
    val locationState = rememberSaveable { mutableStateOf(panel?.location ?: "") }
    val esp32IpState = rememberSaveable { mutableStateOf(panel?.ESP32_IP ?: "") }
    val ssidState = rememberSaveable { mutableStateOf(panel?.SSID ?: "") }
    val ssidPwState = rememberSaveable { mutableStateOf(panel?.SSID_PW ?: "") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var esp32IpError by remember { mutableStateOf<String?>(null) }
    var ssidError by remember { mutableStateOf<String?>(null) }
    var ssidPwError by remember { mutableStateOf<String?>(null) }

    // Funciones de validación específicas del composable
    fun validateFieldWithContext(value: String, field: String): String? {
        return if (value.isBlank()) context.getString(R.string.error_required_field) else null
    }

    fun validateIp(value: String): String? {
        return when {
            value.isBlank() -> context.getString(R.string.error_required_field)
            !value.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\$")) ->
                context.getString(R.string.error_invalid_ip)
            else -> null
        }
    }

    fun validateSsidPw(value: String): String? {
        return when {
            value.isBlank() -> context.getString(R.string.error_required_field)
            value.length < 8 -> context.getString(R.string.error_ssid_password_length)
            else -> null
        }
    }

    // Función de validación con debounce
    fun validateWithDebounce(
        value: String,
        validator: (String) -> String?,
        onValidation: (String?) -> Unit
    ) {
        validationJob?.cancel()
        validationJob = scope.launch {
            delay(VALIDATION_DEBOUNCE)
            onValidation(validator(value))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (panel == null) R.string.new_panel else R.string.edit_panel))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OptimizedTextField(
                    value = nameState.value,
                    onValueChange = { newValue ->
                        nameState.value = newValue
                        validateWithDebounce(newValue, { validateFieldWithContext(it, "name") }) { nameError = it }
                    },
                    label = stringResource(R.string.field_name),
                    isError = nameError != null,
                    errorMessage = nameError,
                    maxLength = 50,
                    singleLine = true
                )

                OptimizedTextField(
                    value = locationState.value,
                    onValueChange = { newValue ->
                        locationState.value = newValue
                        validateWithDebounce(newValue, { validateField(it, "location") }) { locationError = it }
                    },
                    label = stringResource(R.string.field_location),
                    isError = locationError != null,
                    errorMessage = locationError,
                    maxLength = 100,
                    singleLine = true
                )

                OptimizedTextField(
                    value = esp32IpState.value,
                    onValueChange = { newValue ->
                        esp32IpState.value = newValue
                        validateWithDebounce(newValue, ::validateIp) { esp32IpError = it }
                    },
                    label = stringResource(R.string.field_esp32_ip),
                    isError = esp32IpError != null,
                    errorMessage = esp32IpError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OptimizedTextField(
                    value = ssidState.value,
                    onValueChange = { newValue ->
                        ssidState.value = newValue
                        validateWithDebounce(newValue, { validateField(it, "ssid") }) { ssidError = it }
                    },
                    label = stringResource(R.string.field_ssid),
                    isError = ssidError != null,
                    errorMessage = ssidError,
                    singleLine = true
                )

                OptimizedTextField(
                    value = ssidPwState.value,
                    onValueChange = { newValue ->
                        ssidPwState.value = newValue
                        validateWithDebounce(newValue, ::validateSsidPw) { ssidPwError = it }
                    },
                    label = stringResource(R.string.field_ssid_password),
                    isError = ssidPwError != null,
                    errorMessage = ssidPwError,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isValid = nameError == null && locationError == null &&
                            esp32IpError == null && ssidError == null && ssidPwError == null &&
                            nameState.value.isNotBlank() && locationState.value.isNotBlank() &&
                            esp32IpState.value.isNotBlank() && ssidState.value.isNotBlank() &&
                            ssidPwState.value.isNotBlank()

                    if (isValid) {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onConfirm(
                            Panel(
                                documentName = panel?.documentName ?: "",
                                name = nameState.value.trim(),
                                location = locationState.value.trim(),
                                ESP32_IP = esp32IpState.value.trim(),
                                SSID = ssidState.value.trim(),
                                SSID_PW = ssidPwState.value.trim(),
                                SSID_CON = panel?.SSID_CON,
                                clientDocName = panel?.clientDocName ?: ""
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun UserDialog(
    user: UserData?,
    clientDocName: String,
    onDismiss: () -> Unit,
    onConfirm: (UserData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var validationJob by remember { mutableStateOf<Job?>(null) }

    // Estados memoizados
    val nameState = rememberSaveable { mutableStateOf(user?.name ?: "") }
    val emailState = rememberSaveable { mutableStateOf(user?.email ?: "") }
    val passwordState = rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun validateEmail(value: String): String? {
        return when {
            value.isBlank() -> context.getString(R.string.error_required_field)
            !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() ->
                context.getString(R.string.error_invalid_email)
            else -> null
        }
    }

    fun validatePassword(value: String): String? {
        return when {
            value.isBlank() -> context.getString(R.string.error_required_field)
            value.length < 6 -> context.getString(R.string.error_password_length)
            else -> null
        }
    }

    // Función de validación con debounce
    fun validateWithDebounce(
        value: String,
        validator: (String) -> String?,
        onValidation: (String?) -> Unit
    ) {
        validationJob?.cancel()
        validationJob = scope.launch {
            delay(VALIDATION_DEBOUNCE)
            onValidation(validator(value))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (user == null) R.string.new_user else R.string.edit_user))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OptimizedTextField(
                    value = nameState.value,
                    onValueChange = { newValue ->
                        nameState.value = newValue
                        validateWithDebounce(newValue, { validateField(it, "name") }) { nameError = it }
                    },
                    label = stringResource(R.string.field_name),
                    isError = nameError != null,
                    errorMessage = nameError,
                    maxLength = 50,
                    singleLine = true
                )

                OptimizedTextField(
                    value = emailState.value,
                    onValueChange = { newValue ->
                        emailState.value = newValue
                        validateWithDebounce(newValue, ::validateEmail) { emailError = it }
                    },
                    label = stringResource(R.string.field_email),
                    isError = emailError != null,
                    errorMessage = emailError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                if (user == null) {
                    OptimizedTextField(
                        value = passwordState.value,
                        onValueChange = { newValue ->
                            passwordState.value = newValue
                            validateWithDebounce(newValue, ::validatePassword) { passwordError = it }
                        },
                        label = stringResource(R.string.field_password),
                        isError = passwordError != null,
                        errorMessage = passwordError,
                        visualTransformation = if (showPassword)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword)
                                        Icons.Default.Visibility
                                    else
                                        Icons.Default.VisibilityOff,
                                    contentDescription = if (showPassword)
                                        stringResource(R.string.hide_password)
                                    else
                                        stringResource(R.string.show_password)
                                )
                            }
                        },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isValid = nameError == null && emailError == null &&
                            (user != null || passwordError == null) &&
                            nameState.value.isNotBlank() && emailState.value.isNotBlank() &&
                            (user != null || passwordState.value.isNotBlank())

                    if (isValid) {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onConfirm(
                            UserData(
                                documentName = user?.documentName ?: "",
                                email = emailState.value.trim(),
                                name = nameState.value.trim(),
                                roleString = "user",
                                clientDocName = clientDocName,
                                fcmToken = user?.fcmToken
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}