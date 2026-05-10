package com.vibebuilder.app.ui.screens.projectlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vibebuilder.app.di.ServiceLocator
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface ProjectListUiState {
    data object Loading : ProjectListUiState
    data class Content(val projects: List<Project>) : ProjectListUiState
    data class Error(val message: String) : ProjectListUiState
}

class ProjectListViewModel(
    repository: ProjectRepository = ServiceLocator.projectRepository
) : ViewModel() {

    private val reloadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ProjectListUiState> = reloadTrigger
        .flatMapLatest {
            repository.observeProjects()
                .map<List<Project>, ProjectListUiState> { ProjectListUiState.Content(it) }
                .onStart { emit(ProjectListUiState.Loading) }
                .catch { emit(ProjectListUiState.Error(it.message ?: "Error desconocido")) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectListUiState.Loading
        )

    fun retry() {
        reloadTrigger.update { it + 1 }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProjectListViewModel() as T
        }
    }
}
