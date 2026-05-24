package com.vibebuilder.app.data.repository

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.domain.repository.PreviewUnavailableReason
import com.vibebuilder.app.domain.repository.PreviewUrlResolution
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * In-memory implementation of [ProjectRepository] for Delivery 1.
 *
 * Seeds a couple of demo projects so the UI is not empty on first launch and simulates
 * latency on writes. Replace with a real network-backed implementation when the API
 * described in AGENTS.md is available.
 */
class MockProjectRepository : ProjectRepository {

    private val mutex = Mutex()

    private val projectsState = MutableStateFlow<List<Project>>(emptyList())
    private val versionsState = MutableStateFlow<Map<String, List<ProjectVersion>>>(emptyMap())
    private val messagesState = MutableStateFlow<Map<String, List<PromptMessage>>>(emptyMap())

    init {
        seedDemoData()
    }

    override fun observeProjects(): Flow<List<Project>> =
        projectsState.asStateFlow().map { list -> list.sortedByDescending { it.updatedAt } }

    override fun observeProject(projectId: String): Flow<Project?> =
        projectsState.asStateFlow().map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        versionsState.asStateFlow().map { map ->
            (map[projectId] ?: emptyList()).sortedByDescending { it.versionNumber }
        }

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        messagesState.asStateFlow().map { map ->
            (map[projectId] ?: emptyList()).sortedBy { it.createdAt }
        }

    override suspend fun createProject(
        title: String,
        description: String
    ): Project = mutex.withLock {
        delay(SIMULATED_LATENCY_MS)
        val now = Clock.System.now()
        val projectId = UUID.randomUUID().toString()
        val project = Project(
            id = projectId,
            title = title,
            description = description,
            createdAt = now,
            updatedAt = now,
            currentVersionNumber = 0
        )
        projectsState.value = projectsState.value + project
        project
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        mutex.withLock {
            delay(SIMULATED_LATENCY_MS)
            val now = Clock.System.now()
            val project = projectsState.value.firstOrNull { it.id == projectId }
                ?: error("Project $projectId not found")

            val nextNumber = project.currentVersionNumber + 1
            val newVersion = buildVersion(projectId, nextNumber, prompt, now)

            val currentVersions = versionsState.value[projectId].orEmpty()
            versionsState.value =
                versionsState.value + (projectId to (currentVersions + newVersion))

            val currentMessages = messagesState.value[projectId].orEmpty()
            messagesState.value = messagesState.value + (
                projectId to (
                    currentMessages +
                        userMessage(projectId, prompt, now) +
                        assistantMessage(
                            projectId,
                            defaultAssistantReply(nextNumber),
                            now,
                            versionNumber = nextNumber
                        )
                    )
                )

            projectsState.value = projectsState.value.map {
                if (it.id == projectId) {
                    it.copy(updatedAt = now, currentVersionNumber = nextNumber)
                } else {
                    it
                }
            }
            newVersion
        }

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? {
        val project = projectsState.value.firstOrNull { it.id == projectId } ?: return null
        return versionsState.value[projectId]
            ?.firstOrNull { it.versionNumber == project.currentVersionNumber }
    }

    override suspend fun resolvePreviewUrl(
        projectId: String,
        currentVersion: ProjectVersion?
    ): PreviewUrlResolution {
        val localUrl = currentVersion?.previewUrl?.trim()
        return if (!localUrl.isNullOrBlank()) {
            PreviewUrlResolution.Available(localUrl)
        } else {
            PreviewUrlResolution.Unavailable(PreviewUnavailableReason.NotReady)
        }
    }

    private fun buildVersion(
        projectId: String,
        versionNumber: Int,
        prompt: String,
        createdAt: kotlinx.datetime.Instant
    ): ProjectVersion = ProjectVersion(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        versionNumber = versionNumber,
        prompt = prompt,
        previewHtml = mockPreviewHtml(prompt, versionNumber),
        previewUrl = null,
        createdAt = createdAt,
        status = VersionStatus.READY
    )

    private fun userMessage(
        projectId: String,
        content: String,
        createdAt: kotlinx.datetime.Instant
    ) = PromptMessage(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        role = PromptMessage.Role.USER,
        content = content,
        createdAt = createdAt
    )

    private fun assistantMessage(
        projectId: String,
        content: String,
        createdAt: kotlinx.datetime.Instant,
        versionNumber: Int
    ) = PromptMessage(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        role = PromptMessage.Role.ASSISTANT,
        content = content,
        createdAt = createdAt,
        versionNumber = versionNumber
    )

    private fun defaultAssistantReply(versionNumber: Int): String =
        "Tu app ya está lista (versión $versionNumber)."

    private fun mockPreviewHtml(prompt: String, versionNumber: Int): String = """
        <h1>Web app generada (v$versionNumber)</h1>
        <p><strong>Prompt:</strong> ${prompt.take(140)}</p>
        <p>Este es un placeholder. La integración real reemplazará este contenido por la web app generada por la IA.</p>
    """.trimIndent()

    private fun seedDemoData() {
        val now = Clock.System.now()
        val demo = Project(
            id = DEMO_PROJECT_ID,
            title = "Landing de gimnasio",
            description = "Demo precargada para mostrar el flujo end-to-end.",
            createdAt = now,
            updatedAt = now,
            currentVersionNumber = 1
        )
        projectsState.value = listOf(demo)

        val firstPrompt = "Crea una landing para un gimnasio con planes y formulario de contacto"
        val firstVersion = buildVersion(demo.id, 1, firstPrompt, now)
        versionsState.value = mapOf(demo.id to listOf(firstVersion))

        messagesState.value = mapOf(
            demo.id to listOf(
                userMessage(demo.id, firstPrompt, now),
                assistantMessage(demo.id, defaultAssistantReply(1), now, versionNumber = 1)
            )
        )
    }

    private companion object {
        const val SIMULATED_LATENCY_MS = 600L
        const val DEMO_PROJECT_ID = "demo-gym-landing"
    }
}
