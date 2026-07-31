package com.vibebuilder.app.ui.screens.projectdetail

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.model.PublicProject
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.domain.repository.PreviewUrlResolution
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

    @Test
    fun regenerateVersion_bloqueaDobleTapMientrasCarga() = runTest(dispatcher) {
        val repository = DetailFakeRepository()
        val pendingRegeneration = CompletableDeferred<Unit>()
        repository.regenerateGate = pendingRegeneration
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        val failedVersion = ProjectVersion(
            id = "failed-version",
            projectId = PROJECT_ID,
            versionNumber = 1,
            prompt = "Prompt fallido",
            previewHtml = "",
            createdAt = Clock.System.now(),
            status = VersionStatus.FAILED
        )

        viewModel.regenerateVersion(failedVersion)
        viewModel.regenerateVersion(failedVersion)
        advanceUntilIdle()

        assertEquals(1, repository.regenerateCalls)
        assertTrue(viewModel.regenerationState.value.isRegenerating("failed-version"))

        pendingRegeneration.complete(Unit)
        advanceUntilIdle()

        assertTrue(!viewModel.regenerationState.value.isRegenerating("failed-version"))
    }

    @Test
    fun regenerateVersion_falloConservaErrorLocalYPermiteReintento() = runTest(dispatcher) {
        val repository = DetailFakeRepository().apply { failRegenerate = true }
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        val failedVersion = ProjectVersion(
            id = "failed-version",
            projectId = PROJECT_ID,
            versionNumber = 1,
            prompt = "Prompt fallido",
            previewHtml = "",
            createdAt = Clock.System.now(),
            status = VersionStatus.FAILED
        )

        viewModel.regenerateVersion(failedVersion)
        advanceUntilIdle()

        assertEquals(1, repository.regenerateCalls)
        assertEquals("No se pudo regenerar", viewModel.regenerationState.value.errorFor("failed-version"))

        repository.failRegenerate = false
        viewModel.regenerateVersion(failedVersion)
        advanceUntilIdle()

        assertEquals(2, repository.regenerateCalls)
        assertEquals(null, viewModel.regenerationState.value.errorFor("failed-version"))
    }

    @Test
    fun editProject_validaLimites_yActualizaProyecto() = runTest(dispatcher) {
        val repository = DetailFakeRepository()
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        val project = repository.projects.value.first()
        viewModel.showEditProject(project)
        viewModel.onEditTitleChange(" ")
        viewModel.onEditDescriptionChange("x".repeat(501))

        viewModel.saveProjectEdit()
        assertEquals(EditProjectValidationError.Required, viewModel.editProjectState.value.titleError)
        assertEquals(EditProjectValidationError.TooLong, viewModel.editProjectState.value.descriptionError)
        assertEquals(0, repository.updateCalls)

        viewModel.onEditTitleChange("Renombrado")
        viewModel.onEditDescriptionChange("")
        viewModel.saveProjectEdit()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals("Renombrado", repository.projects.value.first().title)
        assertTrue(!viewModel.editProjectState.value.isVisible)
    }

    @Test
    fun editProject_falloConservaEntrada() = runTest(dispatcher) {
        val repository = DetailFakeRepository().apply { failUpdate = true }
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        viewModel.showEditProject(repository.projects.value.first())
        viewModel.onEditTitleChange("Intento")
        viewModel.saveProjectEdit()
        advanceUntilIdle()

        assertEquals("Intento", viewModel.editProjectState.value.title)
        assertNotNull(viewModel.editProjectState.value.errorMessage)
        assertTrue(viewModel.editProjectState.value.isVisible)
    }

    @Test
    fun deleteProject_cancelacionExitoYFallo() = runTest(dispatcher) {
        val repository = DetailFakeRepository()
        val viewModel = ProjectDetailViewModel(PROJECT_ID, repository)
        viewModel.showDeleteProject()
        viewModel.dismissDeleteProject()
        assertEquals(0, repository.deleteCalls)

        viewModel.showDeleteProject()
        repository.failDelete = true
        viewModel.deleteProject()
        advanceUntilIdle()
        assertNotNull(viewModel.deleteProjectState.value.errorMessage)
        assertTrue(viewModel.deleteProjectState.value.isVisible)

        repository.failDelete = false
        viewModel.deleteProject()
        advanceUntilIdle()
        assertTrue(viewModel.deleteProjectState.value.succeeded)
    }

    companion object {
        private const val PROJECT_ID = "project-1"
    }
}

private class DetailFakeRepository : ProjectRepository {
    private val now = Clock.System.now()
    val projects = MutableStateFlow(
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
    var updateCalls = 0
    var deleteCalls = 0
    var regenerateCalls = 0
    var failUpdate = false
    var failDelete = false
    var failRegenerate = false
    var regenerateGate: CompletableDeferred<Unit>? = null

    override fun observeProjects(): Flow<List<Project>> = projects

    override fun observeLibraryProjects(): Flow<List<PublicProject>> =
        MutableStateFlow(emptyList())

    override fun observeProject(projectId: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeLibraryProject(projectId: String): Flow<PublicProject?> =
        MutableStateFlow(null)

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        versions.map { it[projectId].orEmpty() }

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        messages.map { it[projectId].orEmpty() }

    override suspend fun createProject(title: String, description: String): Project {
        throw NotImplementedError("No requerido para esta prueba")
    }

    override suspend fun updateProject(projectId: String, title: String, description: String): Project {
        updateCalls += 1
        if (failUpdate) throw IOException("No se pudo editar")
        val existing = projects.value.first { it.id == projectId }
        val updated = existing.copy(title = title, description = description)
        projects.value = projects.value.map { if (it.id == projectId) updated else it }
        return updated
    }

    override suspend fun updateProjectVisibility(projectId: String, isPublic: Boolean): Project {
        val existing = projects.value.first { it.id == projectId }
        val updated = existing.copy(
            visibility = if (isPublic) {
                com.vibebuilder.app.domain.model.ProjectVisibility.PUBLIC
            } else {
                com.vibebuilder.app.domain.model.ProjectVisibility.PRIVATE
            }
        )
        projects.value = projects.value.map { if (it.id == projectId) updated else it }
        return updated
    }

    override suspend fun deleteProject(projectId: String) {
        deleteCalls += 1
        if (failDelete) throw IOException("No se pudo eliminar")
        projects.value = projects.value.filterNot { it.id == projectId }
        versions.value = versions.value - projectId
        messages.value = messages.value - projectId
    }

    override suspend fun forkProject(projectId: String): Project {
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

    override suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String?
    ): ProjectVersion {
        regenerateCalls += 1
        regenerateGate?.await()
        if (failRegenerate) throw IOException("No se pudo regenerar")
        return ProjectVersion(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            versionNumber = 2,
            prompt = correctedPrompt ?: "retry",
            previewHtml = "<h1>preview</h1>",
            previewUrl = null,
            createdAt = Clock.System.now(),
            status = VersionStatus.READY,
            sourceVersionId = versionId,
            attemptNumber = 2
        )
    }

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? = null

    override suspend fun resolvePreviewUrl(
        projectId: String,
        currentVersion: ProjectVersion?
    ): PreviewUrlResolution = PreviewUrlResolution.Unavailable(
        reason = com.vibebuilder.app.domain.repository.PreviewUnavailableReason.NotReady
    )
}
