package com.vibebuilder.app.ui.screens.createproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vibebuilder.app.di.ServiceLocator
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateProjectFormState(
    val title: String = "",
    val description: String = "",
    val titleError: String? = null,
    val descriptionError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val createdProjectId: String? = null
) {
    val canSubmit: Boolean
        get() = !isSubmitting && title.isNotBlank()
}

class CreateProjectViewModel(
    private val repository: ProjectRepository = ServiceLocator.projectRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateProjectFormState())
    val state: StateFlow<CreateProjectFormState> = _state.asStateFlow()

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value, titleError = null, submitError = null) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value, descriptionError = null, submitError = null) }
    }

    fun submit() {
        val current = _state.value
        val titleError = when {
            current.title.isBlank() -> "El nombre es requerido"
            current.title.trim().length > 100 -> "El nombre no puede superar 100 caracteres"
            else -> null
        }
        val descriptionError = if (current.description.trim().length > 500) {
            "La descripción no puede superar 500 caracteres"
        } else null
        if (titleError != null || descriptionError != null) {
            _state.update { it.copy(titleError = titleError, descriptionError = descriptionError) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            runCatching {
                repository.createProject(
                    title = current.title.trim(),
                    description = current.description.trim()
                )
            }.onSuccess { project ->
                _state.update {
                    it.copy(isSubmitting = false, createdProjectId = project.id)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = error.message ?: "No se pudo crear el proyecto"
                    )
                }
            }
        }
    }

    fun consumeNavigation() {
        _state.update { it.copy(createdProjectId = null) }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateProjectViewModel() as T
        }
    }
}
