package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.ApiProject
import com.vibebuilder.app.data.remote.VibeBuilderApi
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Repository híbrido para Delivery 1:
 * - Home usa datos reales de backend (`GET /projects`).
 * - Detalle/prompt mantiene comportamiento local para no romper el flujo actual.
 */
class RemoteProjectRepository(
    private val api: VibeBuilderApi
) : ProjectRepository {

    private val mutex = Mutex()
    private val projectsState = MutableStateFlow<List<Project>>(emptyList())
    private val versionsState = MutableStateFlow<Map<String, List<ProjectVersion>>>(emptyMap())
    private val messagesState = MutableStateFlow<Map<String, List<PromptMessage>>>(emptyMap())

    override fun observeProjects(): Flow<List<Project>> = projectsState.asStateFlow()
        .onStart { refreshFromBackend() }
        .map { list -> list.sortedByDescending { project -> project.updatedAt } }

    override fun observeProject(projectId: String): Flow<Project?> =
        projectsState.asStateFlow().map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        versionsState.asStateFlow().map { map ->
            map[projectId].orEmpty().sortedByDescending { it.versionNumber }
        }

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        messagesState.asStateFlow().map { map ->
            map[projectId].orEmpty().sortedBy { it.createdAt }
        }

    override suspend fun createProject(
        title: String,
        description: String
    ): Project = mutex.withLock {
        val now = Clock.System.now()
        val projectId = api.createProject(title = title, description = description)
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()

        val project = Project(
            id = projectId,
            title = normalizedTitle,
            description = normalizedDescription,
            createdAt = now,
            updatedAt = now,
            currentVersionNumber = 0
        )

        refreshFromBackend()
        upsertProject(project)
        project
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        mutex.withLock {
            val project = projectsState.value.firstOrNull { it.id == projectId }
                ?: error("Project $projectId not found")

            val nextNumber = project.currentVersionNumber + 1
            val now = Clock.System.now()

            val newVersion = buildVersion(
                projectId = projectId,
                versionNumber = nextNumber,
                prompt = prompt,
                createdAt = now
            )
            versionsState.value = versionsState.value + (
                projectId to (versionsState.value[projectId].orEmpty() + newVersion)
            )

            messagesState.value = messagesState.value + (
                projectId to (
                    messagesState.value[projectId].orEmpty() +
                        userMessage(projectId, prompt, now) +
                        assistantMessage(
                            projectId = projectId,
                            content = defaultAssistantReply(nextNumber),
                            createdAt = now,
                            versionNumber = nextNumber
                        )
                    )
                )

            upsertProject(project.copy(updatedAt = now, currentVersionNumber = nextNumber))
            newVersion
        }

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? {
        val project = projectsState.value.firstOrNull { it.id == projectId } ?: return null
        return versionsState.value[projectId]
            ?.firstOrNull { it.versionNumber == project.currentVersionNumber }
    }

    private suspend fun refreshFromBackend() {
        val remoteProjects = api.getProjects().map { apiProject ->
            val localProject = projectsState.value.firstOrNull { project -> project.id == apiProject.id }
            apiProject.toDomain(currentVersionNumber = localProject?.currentVersionNumber)
        }
        projectsState.value = mergeRemoteWithLocal(remoteProjects)
    }

    private fun mergeRemoteWithLocal(remoteProjects: List<Project>): List<Project> {
        val remoteById = remoteProjects.associateBy { it.id }
        val localOnly = projectsState.value.filter { local -> remoteById[local.id] == null }
        return remoteProjects + localOnly
    }

    private fun upsertProject(project: Project) {
        projectsState.value = buildList {
            add(project)
            addAll(projectsState.value.filterNot { it.id == project.id })
        }
    }

    private fun ApiProject.toDomain(currentVersionNumber: Int?): Project = Project(
        id = id,
        title = title,
        description = description.orEmpty(),
        createdAt = parseInstant(createdAt),
        updatedAt = parseInstant(updatedAt),
        currentVersionNumber = currentVersionNumber ?: if (currentVersionId == null) 0 else 1
    )

    private fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrElse { Clock.System.now() }

    private fun buildVersion(
        projectId: String,
        versionNumber: Int,
        prompt: String,
        createdAt: Instant
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
        createdAt: Instant
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
        createdAt: Instant,
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
        "Versión $versionNumber generada. Revisa el preview y envía un nuevo prompt para iterar."

    private fun mockPreviewHtml(prompt: String, versionNumber: Int): String = """
        <h1>Web app generada (v$versionNumber)</h1>
        <p><strong>Prompt:</strong> ${prompt.take(140)}</p>
        <p>Este es un placeholder. La integración real reemplazará este contenido por la web app generada por la IA.</p>
    """.trimIndent()
}
