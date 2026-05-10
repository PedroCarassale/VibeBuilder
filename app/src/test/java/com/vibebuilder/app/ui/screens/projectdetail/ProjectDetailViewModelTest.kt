package com.vibebuilder.app.ui.screens.projectdetail

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

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
    fun sendPrompt_bloqueaDobleTapMientrasCarga() = runTest(dispatcher) {
        val repository = DetailFakeRepository()
        val pendingSend = CompletableDeferred<Unit>()
        repository.sendGate = pendingSend

        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        viewModel.onPromptChange("Genera una landing")

        viewModel.sendPrompt()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(1, repository.sendCalls)
        assertTrue(viewModel.promptInput.value.isSending)
        assertEquals(PromptSendStatus.Loading, viewModel.promptInput.value.sendStatus)

        pendingSend.complete(Unit)
        advanceUntilIdle()

        assertEquals(PromptSendStatus.Success, viewModel.promptInput.value.sendStatus)
    }

    @Test
    fun retrySend_trasFallo_reintentaYRecuperaEstado() = runTest(dispatcher) {
        val repository = DetailFakeRepository().apply {
            failNextSend = true
        }
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        viewModel.onPromptChange("Crea dashboard")

        viewModel.sendPrompt()
        advanceUntilIdle()

        val failedState = viewModel.promptInput.value
        assertEquals(1, repository.sendCalls)
        assertEquals(PromptSendStatus.Failed, failedState.sendStatus)
        assertNotNull(failedState.sendError)
        assertTrue(failedState.canSend)

        viewModel.retrySend()
        advanceUntilIdle()

        assertEquals(2, repository.sendCalls)
        val recoveredState = viewModel.promptInput.value
        assertEquals(PromptSendStatus.Success, recoveredState.sendStatus)
        assertEquals("", recoveredState.text)
        assertEquals(null, recoveredState.sendError)
    }

    @Test
    fun retrySend_mientrasCarga_noDisparaEnvioDuplicado() = runTest(dispatcher) {
        val repository = DetailFakeRepository()
        val pendingSend = CompletableDeferred<Unit>()
        repository.sendGate = pendingSend
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        viewModel.onPromptChange("Genera un catálogo")

        viewModel.sendPrompt()
        viewModel.retrySend()
        advanceUntilIdle()

        assertEquals(1, repository.sendCalls)
        assertEquals(PromptSendStatus.Loading, viewModel.promptInput.value.sendStatus)

        pendingSend.complete(Unit)
        advanceUntilIdle()

        assertEquals(PromptSendStatus.Success, viewModel.promptInput.value.sendStatus)
    }

    companion object {
        private const val PROJECT_ID = "project-1"
    }
}

private class DetailFakeRepository : ProjectRepository {
    private val now = Clock.System.now()
    private val projects = MutableStateFlow(
        listOf(
            Project(
                id = "project-1",
                title = "Proyecto prueba",
                description = "desc",
                createdAt = now,
                updatedAt = now,
                currentVersionNumber = 0
            )
        )
    )
    private val versions = MutableStateFlow<Map<String, List<ProjectVersion>>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, List<PromptMessage>>>(emptyMap())

    var sendCalls: Int = 0
        private set
    var failNextSend: Boolean = false
    var sendGate: CompletableDeferred<Unit>? = null

    override fun observeProjects(): Flow<List<Project>> = projects

    override fun observeProject(projectId: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        versions.map { it[projectId].orEmpty() }

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        messages.map { it[projectId].orEmpty() }

    override suspend fun createProject(title: String, description: String): Project {
        throw NotImplementedError("No requerido para esta prueba")
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion {
        sendCalls += 1
        sendGate?.await()
        if (failNextSend) {
            failNextSend = false
            throw IOException("La generación falló. Reintenta.")
        }
        return ProjectVersion(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            versionNumber = 1,
            prompt = prompt,
            previewHtml = "<h1>preview</h1>",
            previewUrl = null,
            createdAt = Clock.System.now(),
            status = VersionStatus.READY
        )
    }

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? = null
}
