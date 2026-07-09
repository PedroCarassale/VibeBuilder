package com.vibebuilder.app.ui.screens.projectlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vibebuilder.app.data.auth.AuthSession
import com.vibebuilder.app.di.ServiceLocator
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed interface ProjectListUiState {
    data object Loading : ProjectListUiState
    data class Content(
        val projects: List<Project>,
        val query: String,
        val sort: ProjectSort,
        val totalCount: Int
    ) : ProjectListUiState
    data class Error(val message: String) : ProjectListUiState
}

enum class ProjectSort { RecentlyUpdated, NameAscending, NewestCreated }

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModel(
    repository: ProjectRepository = ServiceLocator.projectRepository,
    authSessionFlow: Flow<AuthSession?> = flowOf(null)
) : ViewModel() {

    private val reloadTrigger = MutableStateFlow(0)
    private val searchQuery = MutableStateFlow("")
    private val selectedSort = MutableStateFlow(ProjectSort.RecentlyUpdated)

    private val projectsFlow = combine(
        reloadTrigger,
        authSessionFlow
    ) { reload, session -> reload to session }
        .flatMapLatest {
            repository.observeProjects()
                .map<List<Project>, ProjectListUiState> {
                    ProjectListUiState.Content(it, "", ProjectSort.RecentlyUpdated, it.size)
                }
                .onStart { emit(ProjectListUiState.Loading) }
                .catch { emit(ProjectListUiState.Error(it.message ?: "Error desconocido")) }
        }

    val uiState: StateFlow<ProjectListUiState> = combine(
        projectsFlow,
        searchQuery,
        selectedSort
    ) { sourceState, query, sort ->
        if (sourceState !is ProjectListUiState.Content) return@combine sourceState
        val normalizedQuery = query.trim()
        val filtered = sourceState.projects.filter { project ->
            normalizedQuery.isEmpty() ||
                project.title.contains(normalizedQuery, ignoreCase = true) ||
                project.description.contains(normalizedQuery, ignoreCase = true)
        }
        val sorted = when (sort) {
            ProjectSort.RecentlyUpdated -> filtered.sortedByDescending(Project::updatedAt)
            ProjectSort.NameAscending -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            ProjectSort.NewestCreated -> filtered.sortedByDescending(Project::createdAt)
        }
        ProjectListUiState.Content(sorted, query, sort, sourceState.totalCount)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectListUiState.Loading
        )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortChange(sort: ProjectSort) {
        selectedSort.value = sort
    }

    fun retry() {
        reloadTrigger.update { it + 1 }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProjectListViewModel(
                    repository = ServiceLocator.projectRepository,
                    authSessionFlow = ServiceLocator.authRepository.session
                ) as T
        }
    }
}
