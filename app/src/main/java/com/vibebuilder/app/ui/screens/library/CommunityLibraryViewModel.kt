package com.vibebuilder.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibebuilder.app.di.ServiceLocator
import com.vibebuilder.app.domain.model.PublicProject
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(
        val projects: List<PublicProject>,
        val query: String,
        val totalCount: Int
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface PublicProjectDetailUiState {
    data object Loading : PublicProjectDetailUiState
    data class Content(val project: PublicProject) : PublicProjectDetailUiState
    data class Error(val message: String) : PublicProjectDetailUiState
    data class NotFound(val projectId: String) : PublicProjectDetailUiState
}

data class ForkUiState(
    val isForking: Boolean = false,
    val forkedProjectId: String? = null,
    val errorMessage: String? = null
)

class CommunityLibraryViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val sourceState = repository.observeLibraryProjects()
        .map<List<PublicProject>, LibraryUiState> {
            LibraryUiState.Content(it, "", it.size)
        }
        .catch { emit(LibraryUiState.Error(it.message ?: "No se pudo cargar la biblioteca")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading
        )

    val uiState: StateFlow<LibraryUiState> = combine(sourceState, searchQuery) { state, query ->
        if (state !is LibraryUiState.Content) return@combine state
        val normalized = query.trim()
        val filtered = state.projects.filter { project ->
            normalized.isEmpty() ||
                project.title.contains(normalized, ignoreCase = true) ||
                project.description.contains(normalized, ignoreCase = true) ||
                project.ownerName.contains(normalized, ignoreCase = true)
        }
        LibraryUiState.Content(filtered, query, state.totalCount)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState.Loading
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CommunityLibraryViewModel(ServiceLocator.projectRepository) as T
        }
    }
}

class PublicProjectDetailViewModel(
    projectId: String,
    private val repository: ProjectRepository
) : ViewModel() {

    val uiState: StateFlow<PublicProjectDetailUiState> = repository.observeLibraryProject(projectId)
        .map { project ->
            if (project == null) {
                PublicProjectDetailUiState.NotFound(projectId)
            } else {
                PublicProjectDetailUiState.Content(project)
            }
        }
        .catch { emit(PublicProjectDetailUiState.Error(it.message ?: "No se pudo cargar el proyecto público")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PublicProjectDetailUiState.Loading
        )

    private val _forkState = MutableStateFlow(ForkUiState())
    val forkState: StateFlow<ForkUiState> = _forkState

    fun forkProject(projectId: String) {
        if (_forkState.value.isForking) return
        _forkState.value = ForkUiState(isForking = true)
        viewModelScope.launch {
            runCatching { repository.forkProject(projectId) }
                .onSuccess { project ->
                    _forkState.value = ForkUiState(forkedProjectId = project.id)
                }
                .onFailure { error ->
                    _forkState.value = ForkUiState(errorMessage = error.message ?: "No se pudo crear el fork")
                }
        }
    }

    fun clearForkError() {
        _forkState.update { it.copy(errorMessage = null) }
    }

    fun onForkNavigationHandled() {
        _forkState.value = ForkUiState()
    }

    companion object {
        fun factory(projectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PublicProjectDetailViewModel(
                    projectId = projectId,
                    repository = ServiceLocator.projectRepository
                )
            }
        }
    }
}
