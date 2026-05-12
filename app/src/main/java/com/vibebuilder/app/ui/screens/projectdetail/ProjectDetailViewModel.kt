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
import com.vibebuilder.app.domain.repository.PreviewUnavailableReason
import com.vibebuilder.app.domain.repository.PreviewUrlResolution
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiData(
    val project: Project,
    val versions: List<ProjectVersion>,
    val historyError: String?,
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

data class PreviewExternalUiState(
    val isResolving: Boolean = false,
    val urlToOpen: String? = null,
    val error: PreviewExternalError? = null,
    val errorMessage: String? = null
)

enum class PreviewExternalError {
    NotReady,
    Expired,
    Unavailable,
    NoBrowser,
    Unknown
}

class ProjectDetailViewModel(
    private val projectId: String,
    private val repository: ProjectRepository
) : ViewModel() {

    private val versionsState: StateFlow<HistoryVersionsState> =
        repository.observeVersions(projectId)
            .map { versions ->
                HistoryVersionsState(
                    versions = versions,
                    errorMessage = null
                )
            }
            .catch {
                emit(HistoryVersionsState(errorMessage = it.message ?: "No se pudo cargar el historial"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryVersionsState()
            )

    private val messagesState: StateFlow<List<PromptMessage>> =
        repository.observeMessages(projectId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        repository.observeProject(projectId),
        versionsState,
        messagesState
    ) { project, historyState, messages ->
        if (project == null) {
            ProjectDetailUiState.NotFound(projectId)
        } else {
            val currentVersion = historyState.versions.firstOrNull {
                it.versionNumber == project.currentVersionNumber
            }
            ProjectDetailUiState.Content(
                ProjectDetailUiData(
                    project = project,
                    versions = historyState.versions,
                    historyError = historyState.errorMessage,
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

    private val _previewExternalState = MutableStateFlow(PreviewExternalUiState())
    val previewExternalState: StateFlow<PreviewExternalUiState> = _previewExternalState.asStateFlow()

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

    fun openPreviewInBrowser(currentVersion: ProjectVersion?) {
        if (_previewExternalState.value.isResolving) return
        _previewExternalState.update {
            it.copy(
                isResolving = true,
                urlToOpen = null,
                error = null,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            when (val previewResolution = repository.resolvePreviewUrl(projectId, currentVersion)) {
                is PreviewUrlResolution.Available -> {
                    _previewExternalState.update {
                        it.copy(
                            isResolving = false,
                            urlToOpen = previewResolution.url
                        )
                    }
                }

                is PreviewUrlResolution.Unavailable -> {
                    _previewExternalState.update {
                        it.copy(
                            isResolving = false,
                            error = mapPreviewError(previewResolution.reason),
                            errorMessage = previewResolution.message
                        )
                    }
                }
            }
        }
    }

    fun onPreviewUrlHandled() {
        _previewExternalState.update {
            if (it.urlToOpen == null) it else it.copy(urlToOpen = null)
        }
    }

    fun onPreviewOpenFailedNoBrowser() {
        _previewExternalState.update {
            it.copy(
                isResolving = false,
                urlToOpen = null,
                error = PreviewExternalError.NoBrowser,
                errorMessage = null
            )
        }
    }

    fun onPreviewOpenFailedUnknown(message: String?) {
        _previewExternalState.update {
            it.copy(
                isResolving = false,
                urlToOpen = null,
                error = PreviewExternalError.Unknown,
                errorMessage = message
            )
        }
    }

    fun clearPreviewFeedback() {
        _previewExternalState.update {
            it.copy(
                error = null,
                errorMessage = null
            )
        }
    }

    private fun mapPreviewError(reason: PreviewUnavailableReason): PreviewExternalError = when (reason) {
        PreviewUnavailableReason.NotReady -> PreviewExternalError.NotReady
        PreviewUnavailableReason.Expired -> PreviewExternalError.Expired
        PreviewUnavailableReason.Unavailable -> PreviewExternalError.Unavailable
        PreviewUnavailableReason.Unknown -> PreviewExternalError.Unknown
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

data class HistoryVersionsState(
    val versions: List<ProjectVersion> = emptyList(),
    val errorMessage: String? = null
)
