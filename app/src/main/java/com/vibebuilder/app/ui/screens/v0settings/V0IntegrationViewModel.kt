package com.vibebuilder.app.ui.screens.v0settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vibebuilder.app.data.remote.ApiRequestException
import com.vibebuilder.app.data.remote.HttpVibeBuilderApi
import com.vibebuilder.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class V0IntegrationUiState(
    val loading: Boolean = true,
    val keyStorageAvailable: Boolean = false,
    val sessionKeyConfigured: Boolean = false,
    val sessionKeyHint: String? = null,
    val envKeyActive: Boolean = false,
    val apiKeyInput: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class V0IntegrationViewModel(
    private val api: HttpVibeBuilderApi = ServiceLocator.vibeBuilderApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(V0IntegrationUiState())
    val uiState: StateFlow<V0IntegrationUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKeyInput = value, errorMessage = null, statusMessage = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val s = api.getV0IntegrationStatus()
                _uiState.update {
                    it.copy(
                        loading = false,
                        keyStorageAvailable = s.keyStorageAvailable,
                        sessionKeyConfigured = s.sessionKeyConfigured,
                        sessionKeyHint = s.sessionKeyHint,
                        envKeyActive = s.envKeyActive
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = e.message ?: "Error al cargar el estado"
                    )
                }
            }
        }
    }

    fun saveKey() {
        val key = _uiState.value.apiKeyInput.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Pega tu API key de v0.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, statusMessage = null) }
            try {
                api.saveV0ApiKey(key)
                _uiState.update {
                    it.copy(
                        loading = false,
                        apiKeyInput = "",
                        statusMessage = "Clave guardada en el servidor (cifrada)."
                    )
                }
                refresh()
            } catch (e: ApiRequestException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = humanizeApiError(e)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = e.message ?: "No se pudo guardar.")
                }
            }
        }
    }

    fun clearSavedKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, statusMessage = null) }
            try {
                api.deleteV0ApiKey()
                _uiState.update {
                    it.copy(loading = false, statusMessage = "Se eliminó la key guardada en esta sesión.")
                }
                refresh()
            } catch (e: ApiRequestException) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = humanizeApiError(e))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = e.message ?: "No se pudo eliminar.")
                }
            }
        }
    }

    fun testConnection(useInputField: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, statusMessage = null) }
            try {
                val inline = _uiState.value.apiKeyInput.trim()
                val toSend = if (useInputField && inline.isNotEmpty()) inline else null
                api.testV0ApiKey(toSend)
                _uiState.update {
                    it.copy(loading = false, statusMessage = "Conexión con v0 correcta.")
                }
            } catch (e: ApiRequestException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = humanizeApiError(e)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = e.message ?: "Falló la prueba de conexión.")
                }
            }
        }
    }

    private fun humanizeApiError(e: ApiRequestException): String = when (e.errorCode) {
        "KEYSTORE_UNAVAILABLE" ->
            "El servidor no tiene V0_KEYSTORE_SECRET (≥16 caracteres); no se pueden guardar keys por sesión."
        "V0_CONNECTION_FAILED" -> e.message ?: "Falló la conexión con v0"
        "NO_STORED_V0_KEY" -> "No hay key guardada: pega una en el campo o guarda antes de probar."
        else -> e.message ?: "Error (${e.statusCode})"
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                V0IntegrationViewModel() as T
        }
    }
}
