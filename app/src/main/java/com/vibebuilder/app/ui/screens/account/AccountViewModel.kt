package com.vibebuilder.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vibebuilder.app.data.auth.AuthRepository
import com.vibebuilder.app.data.auth.AuthSession
import com.vibebuilder.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val loading: Boolean = false,
    val session: AuthSession? = null,
    val registerMode: Boolean = false,
    val nameInput: String = "",
    val emailInput: String = "",
    val passwordInput: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class AccountViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _uiState.update { it.copy(session = session, loading = false) }
            }
        }
        viewModelScope.launch {
            authRepository.refreshCurrentUser()
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(nameInput = value, errorMessage = null, statusMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(emailInput = value, errorMessage = null, statusMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(passwordInput = value, errorMessage = null, statusMessage = null) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                registerMode = !it.registerMode,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.emailInput.trim()
        val password = state.passwordInput
        if (email.isBlank() || password.length < 8) {
            _uiState.update {
                it.copy(errorMessage = "Usa un email válido y una contraseña de al menos 8 caracteres.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, statusMessage = null) }
            runCatching {
                if (state.registerMode) {
                    authRepository.register(
                        name = state.nameInput.trim(),
                        email = email,
                        password = password
                    )
                } else {
                    authRepository.login(
                        email = email,
                        password = password
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        loading = false,
                        passwordInput = "",
                        statusMessage = if (state.registerMode) {
                            "Cuenta creada."
                        } else {
                            "Sesión iniciada."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message ?: "No se pudo autenticar."
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, statusMessage = null) }
            authRepository.logout()
            _uiState.update {
                it.copy(
                    loading = false,
                    passwordInput = "",
                    statusMessage = "Volviste al modo invitado."
                )
            }
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AccountViewModel() as T
        }
    }
}
