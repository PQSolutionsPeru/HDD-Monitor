package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.domain.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String, password: String) {
        Log.d(TAG, "Intento de inicio de sesión para el email: $email")
        if (email.isBlank() || password.isBlank()) {
            Log.w(TAG, "Intento de inicio de sesión con campos vacíos")
            _loginState.value = LoginState.Error("Por favor, ingrese su correo electrónico y contraseña")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando proceso de login")
                _loginState.value = LoginState.Loading
                val result = loginUseCase(LoginUseCase.Params(email, password))
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Login exitoso")
                        _loginState.value = LoginState.Success
                    },
                    onFailure = { throwable ->
                        Log.e(TAG, "Error durante el login", throwable)
                        _loginState.value = when (throwable) {
                            is Exception -> LoginState.Error("Usuario o contraseña incorrectos: ${throwable.message}")
                            else -> LoginState.Error("Error desconocido. Intente nuevamente. Detalles: ${throwable.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepción no controlada durante el login", e)
                _loginState.value = LoginState.Error("Error inesperado. Por favor, inténtelo de nuevo más tarde.")
            }
        }
    }

    fun resetState() {
        Log.d(TAG, "Reseteando el estado del login")
        _loginState.value = LoginState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        // Aquí puedes agregar cualquier lógica de limpieza necesaria
        Log.d(TAG, "LoginViewModel onCleared")
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}