package com.vibebuilder.app.ui.screens.projectlist

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.repository.PreviewUrlResolution
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retry_reintentaCarga_yRecuperaContenido() = runTest(dispatcher) {
        val repository = RetryableRepository()
        val viewModel = ProjectListViewModel(repository = repository)
        val collector = collectUiState(viewModel)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ProjectListUiState.Error)
        assertEquals(1, repository.observeCalls)

        viewModel.retry()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertTrue(finalState is ProjectListUiState.Content)
        assertEquals(2, repository.observeCalls)

        collector.cancel()
    }

    @Test
    fun search_buscaTituloYDescripcion_yDistingueCeroResultados() = runTest(dispatcher) {
        val viewModel = ProjectListViewModel(RetryableRepository(failFirst = false))
        val collector = collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("  móvil ")
        advanceUntilIdle()
        var content = viewModel.uiState.value as ProjectListUiState.Content
        assertEquals(listOf("project-2"), content.projects.map { it.id })
        assertEquals(3, content.totalCount)

        viewModel.onSearchQueryChange("inexistente")
        advanceUntilIdle()
        content = viewModel.uiState.value as ProjectListUiState.Content
        assertTrue(content.projects.isEmpty())
        assertEquals(3, content.totalCount)
        collector.cancel()
    }

    @Test
    fun sort_aplicaLosTresOrdenes() = runTest(dispatcher) {
        val viewModel = ProjectListViewModel(RetryableRepository(failFirst = false))
        val collector = collectUiState(viewModel)
        advanceUntilIdle()
        assertEquals(listOf("project-3", "project-2", "project-1"), contentIds(viewModel))

        viewModel.onSortChange(ProjectSort.NameAscending)
        advanceUntilIdle()
        assertEquals(listOf("project-2", "project-1", "project-3"), contentIds(viewModel))

        viewModel.onSortChange(ProjectSort.NewestCreated)
        advanceUntilIdle()
        assertEquals(listOf("project-3", "project-2", "project-1"), contentIds(viewModel))
        collector.cancel()
    }

    private fun contentIds(viewModel: ProjectListViewModel) =
        (viewModel.uiState.value as ProjectListUiState.Content).projects.map { it.id }

    private fun TestScope.collectUiState(viewModel: ProjectListViewModel): Job =
        launch { viewModel.uiState.collect { } }
}

private class RetryableRepository(private val failFirst: Boolean = true) : ProjectRepository {
    var observeCalls: Int = 0
        private set

    override fun observeProjects(): Flow<List<Project>> = flow {
        observeCalls += 1
        if (failFirst && observeCalls == 1) {
            throw IOException("Sin conexion")
        }
        emit(
            listOf(
                Project(
                    id = "project-1",
                    title = "Beta",
                    description = "Verifica reintento",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    currentVersionNumber = 0
                ),
                Project(
                    id = "project-2",
                    title = "alpha",
                    description = "Aplicación móvil",
                    createdAt = Instant.parse("2026-01-02T00:00:00Z"),
                    updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
                    currentVersionNumber = 0
                ),
                Project(
                    id = "project-3",
                    title = "Gamma",
                    description = "Panel web",
                    createdAt = Instant.parse("2026-01-03T00:00:00Z"),
                    updatedAt = Instant.parse("2026-01-03T00:00:00Z"),
                    currentVersionNumber = 0
                )
            )
        )
    }

    override fun observeProject(projectId: String): Flow<Project?> =
        throw NotImplementedError("No requerido para esta prueba")

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        throw NotImplementedError("No requerido para esta prueba")

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun createProject(title: String, description: String): Project =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun updateProject(projectId: String, title: String, description: String): Project =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun deleteProject(projectId: String) =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String?
    ): ProjectVersion =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun resolvePreviewUrl(
        projectId: String,
        currentVersion: ProjectVersion?
    ): PreviewUrlResolution =
        throw NotImplementedError("No requerido para esta prueba")
}
