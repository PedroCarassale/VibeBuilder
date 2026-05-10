package com.vibebuilder.app.ui.screens.projectlist

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
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

    private fun TestScope.collectUiState(viewModel: ProjectListViewModel): Job =
        launch { viewModel.uiState.collect { } }
}

private class RetryableRepository : ProjectRepository {
    var observeCalls: Int = 0
        private set

    override fun observeProjects(): Flow<List<Project>> = flow {
        observeCalls += 1
        if (observeCalls == 1) {
            throw IOException("Sin conexion")
        }
        val now = Clock.System.now()
        emit(
            listOf(
                Project(
                    id = "project-1",
                    title = "Proyecto QA",
                    description = "Verifica reintento",
                    createdAt = now,
                    updatedAt = now,
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

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        throw NotImplementedError("No requerido para esta prueba")

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? =
        throw NotImplementedError("No requerido para esta prueba")
}
