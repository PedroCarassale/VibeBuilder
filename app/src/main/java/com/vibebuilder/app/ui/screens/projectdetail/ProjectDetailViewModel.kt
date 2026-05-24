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
import com.vibebuilder.app.domain.model.VersionStatus
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
    /** Texto del envío en curso mostrado en el chat hasta que el backend confirme. */
    val optimisticUserBubble: String? = null,
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

data class PreviewResolutionUiState(
    val isLoading: Boolean = false,
    val url: String? = null,
    val error: PreviewExternalError? = null,
    val errorMessage: String? = null,
    val versionNumber: Int? = null
)

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

    private val _previewResolutionState = MutableStateFlow(PreviewResolutionUiState())
    val previewResolutionState: StateFlow<PreviewResolutionUiState> = _previewResolutionState.asStateFlow()

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
                text = "",
                optimisticUserBubble = text,
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
                    _promptInput.update { state ->
                        val failedBubble = state.optimisticUserBubble
                        state.copy(
                            text = failedBubble ?: state.text,
                            optimisticUserBubble = null,
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

    fun resolvePreviewForDisplay(currentVersion: ProjectVersion?, force: Boolean = false) {
        if (currentVersion == null) {
            _previewResolutionState.value = PreviewResolutionUiState()
            return
        }

        val versionNumber = currentVersion.versionNumber
        val localPreviewUrl = currentVersion.previewUrl
            ?.trim()
            ?.takeIf(::isSupportedPreviewUrl)

        if (localPreviewUrl != null) {
            _previewResolutionState.value = PreviewResolutionUiState(
                url = localPreviewUrl,
                versionNumber = versionNumber
            )
            return
        }

        if (currentVersion.status != VersionStatus.READY) {
            _previewResolutionState.value = PreviewResolutionUiState(versionNumber = versionNumber)
            return
        }

        if (!force) {
            val currentResolution = _previewResolutionState.value
            if (
                currentResolution.isLoading && currentResolution.versionNumber == versionNumber ||
                currentResolution.url != null && currentResolution.versionNumber == versionNumber ||
                currentResolution.error != null && currentResolution.versionNumber == versionNumber
            ) {
                return
            }
        }

        _previewResolutionState.value = PreviewResolutionUiState(
            isLoading = true,
            versionNumber = versionNumber
        )
        viewModelScope.launch {
            applyPreviewResolution(currentVersion, updateExternalState = false)
        }
    }

    fun openPreviewInBrowser(currentVersion: ProjectVersion?) {
        if (_previewExternalState.value.isResolving) return

        val versionNumber = currentVersion?.versionNumber
        val cachedResolution = _previewResolutionState.value
        if (
            cachedResolution.url != null &&
            cachedResolution.versionNumber == versionNumber
        ) {
            _previewExternalState.update {
                it.copy(
                    isResolving = false,
                    urlToOpen = cachedResolution.url,
                    error = null,
                    errorMessage = null
                )
            }
            return
        }

        _previewExternalState.update {
            it.copy(
                isResolving = true,
                urlToOpen = null,
                error = null,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            applyPreviewResolution(currentVersion, updateExternalState = true)
        }
    }

    private suspend fun applyPreviewResolution(
        currentVersion: ProjectVersion?,
        updateExternalState: Boolean
    ) {
        when (val previewResolution = repository.resolvePreviewUrl(projectId, currentVersion)) {
            is PreviewUrlResolution.Available -> {
                _previewResolutionState.update {
                    PreviewResolutionUiState(
                        url = previewResolution.url,
                        versionNumber = currentVersion?.versionNumber
                    )
                }
                if (updateExternalState) {
                    _previewExternalState.update {
                        it.copy(
                            isResolving = false,
                            urlToOpen = previewResolution.url
                        )
                    }
                }
            }

            is PreviewUrlResolution.Unavailable -> {
                val mappedError = mapPreviewError(previewResolution.reason)
                _previewResolutionState.update {
                    PreviewResolutionUiState(
                        error = mappedError,
                        errorMessage = previewResolution.message,
                        versionNumber = currentVersion?.versionNumber
                    )
                }
                if (updateExternalState) {
                    _previewExternalState.update {
                        it.copy(
                            isResolving = false,
                            error = mappedError,
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

    private fun isSupportedPreviewUrl(previewUrl: String): Boolean {
        if (previewUrl.isBlank()) return false
        return runCatching {
            val parsed = android.net.Uri.parse(previewUrl)
            val scheme = parsed.scheme?.lowercase()
            (scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank()
        }.getOrDefault(false)
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
