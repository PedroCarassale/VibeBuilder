package com.vibebuilder.app.ui.screens.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibebuilder.app.di.ServiceLocator
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiData(
    val project: Project,
    val versions: List<ProjectVersion>,
    val messages: List<PromptMessage>,
    val currentVersion: ProjectVersion?
)

sealed interface ProjectDetailUiState {
    data object Loading : ProjectDetailUiState
    data class Error(val message: String) : ProjectDetailUiState
    data class NotFound(val projectId: String) : ProjectDetailUiState
    data class Content(val data: ProjectDetailUiData) : ProjectDetailUiState
}

data class PromptInputState(
    val text: String = "",
    val sendStatus: PromptSendStatus = PromptSendStatus.Idle,
    val sendError: String? = null
) {
    val isSending: Boolean get() = sendStatus == PromptSendStatus.Loading
    val canSend: Boolean get() = !isSending && text.isNotBlank()
}

enum class PromptSendStatus { Idle, Loading, Success, Failed }

class ProjectDetailViewModel(
    private val projectId: String,
    private val repository: ProjectRepository
) : ViewModel() {

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        repository.observeProject(projectId),
        repository.observeVersions(projectId),
        repository.observeMessages(projectId)
    ) { project, versions, messages ->
        if (project == null) {
            ProjectDetailUiState.NotFound(projectId)
        } else {
            val currentVersion = versions.firstOrNull {
                it.versionNumber == project.currentVersionNumber
            }
            ProjectDetailUiState.Content(
                ProjectDetailUiData(
                    project = project,
                    versions = versions,
                    messages = messages,
                    currentVersion = currentVersion
                )
            )
        }
    }
        .catch { emit(ProjectDetailUiState.Error(it.message ?: "Error desconocido")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectDetailUiState.Loading
        )

    private val _promptInput = MutableStateFlow(PromptInputState())
    val promptInput: StateFlow<PromptInputState> = _promptInput.asStateFlow()

    fun onPromptChange(value: String) {
        _promptInput.update {
            it.copy(
                text = value,
                sendStatus = PromptSendStatus.Idle,
                sendError = null
            )
        }
    }

    fun sendPrompt() {
        val current = _promptInput.value
        if (!current.canSend) return
        val text = current.text.trim()
        _promptInput.update {
            it.copy(
                sendStatus = PromptSendStatus.Loading,
                sendError = null
            )
        }
        viewModelScope.launch {
            runCatching { repository.sendPrompt(projectId, text) }
                .onSuccess {
                    _promptInput.value = PromptInputState(sendStatus = PromptSendStatus.Success)
                }
                .onFailure { error ->
                    _promptInput.update {
                        it.copy(
                            sendStatus = PromptSendStatus.Failed,
                            sendError = error.message ?: "No se pudo enviar el prompt"
                        )
                    }
                }
        }
    }

    fun retrySend() {
        sendPrompt()
    }

    companion object {
        fun factory(projectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectDetailViewModel(
                    projectId = projectId,
                    repository = ServiceLocator.projectRepository
                )
            }
        }
    }
}
