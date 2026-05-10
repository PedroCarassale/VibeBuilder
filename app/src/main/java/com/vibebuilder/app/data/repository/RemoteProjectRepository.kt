package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.ApiProject
import com.vibebuilder.app.data.remote.ApiProjectVersion
import com.vibebuilder.app.data.remote.ApiPromptMessage
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
import org.json.JSONObject
import java.io.IOException

/**
 * Repository para Delivery 1 conectado al backend real:
 * - Home consume `GET /projects`.
 * - Chat consume `POST /projects/:projectId/prompts`.
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
        projectsState.asStateFlow()
            .onStart { refreshFromBackend() }
            .map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> =
        versionsState.asStateFlow()
            .onStart { refreshProjectDetailFromBackend(projectId) }
            .map { map -> map[projectId].orEmpty().sortedByDescending { it.versionNumber } }

    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> =
        messagesState.asStateFlow()
            .onStart { refreshProjectDetailFromBackend(projectId) }
            .map { map -> map[projectId].orEmpty().sortedBy { it.createdAt } }

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
        projectsState.value.firstOrNull { it.id == projectId } ?: project
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        mutex.withLock {
            if (projectsState.value.none { it.id == projectId }) error("Project $projectId not found")
            val normalizedPrompt = prompt.trim()
            val response = api.sendPrompt(projectId = projectId, prompt = normalizedPrompt)
            val newStatus = response.status.toDomainStatus()
            refreshProjectDetailFromBackend(projectId)
            refreshFromBackend()

            val newVersion = versionsState.value[projectId]
                .orEmpty()
                .firstOrNull { version -> version.id == response.projectVersionId }
                ?: ProjectVersion(
                    id = response.projectVersionId,
                    projectId = projectId,
                    versionNumber = response.versionNumber,
                    prompt = normalizedPrompt,
                    previewHtml = previewPlaceholder(normalizedPrompt, response.versionNumber),
                    previewUrl = null,
                    createdAt = Clock.System.now(),
                    status = newStatus
                )

            if (newStatus == VersionStatus.FAILED) {
                throw IOException(buildGenerationFailedMessage(response.providerMeta))
            }
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

    private suspend fun refreshProjectDetailFromBackend(projectId: String) {
        val remoteVersions = api.getProjectVersions(projectId).map { it.toDomain() }
        val remoteMessages = api.getProjectMessages(projectId).map { it.toDomain() }

        versionsState.value = versionsState.value + (
            projectId to dedupeById(remoteVersions, ProjectVersion::id)
        )
        messagesState.value = messagesState.value + (
            projectId to dedupeById(remoteMessages, PromptMessage::id)
        )

        val currentVersionNumber = remoteVersions
            .filter { it.status == VersionStatus.READY }
            .maxOfOrNull { it.versionNumber }
            ?: 0
        projectsState.value.firstOrNull { it.id == projectId }?.let { existingProject ->
            upsertProject(
                existingProject.copy(
                    currentVersionNumber = currentVersionNumber,
                    updatedAt = maxOf(existingProject.updatedAt, remoteVersions.maxOfOrNull { it.createdAt } ?: existingProject.updatedAt)
                )
            )
        }
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

    private fun <T> dedupeById(items: List<T>, idSelector: (T) -> String): List<T> {
        val seenIds = HashSet<String>()
        return items.filter { item -> seenIds.add(idSelector(item)) }
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

    private fun previewPlaceholder(prompt: String, versionNumber: Int): String = """
        <h1>Web app generada (v$versionNumber)</h1>
        <p><strong>Prompt:</strong> ${prompt.take(140)}</p>
        <p>Este es un placeholder. La integración real reemplazará este contenido por la web app generada por la IA.</p>
    """.trimIndent()

    private fun String.toDomainStatus(): VersionStatus = when (lowercase()) {
        "success" -> VersionStatus.READY
        "failed" -> VersionStatus.FAILED
        else -> throw IOException("Estado de generación inválido: $this")
    }

    private fun ApiProjectVersion.toDomain(): ProjectVersion = ProjectVersion(
        id = id,
        projectId = projectId,
        versionNumber = versionNumber,
        prompt = prompt,
        previewHtml = previewPlaceholder(prompt, versionNumber),
        previewUrl = previewUrl,
        createdAt = parseInstant(createdAt),
        status = status.toDomainStatus()
    )

    private fun ApiPromptMessage.toDomain(): PromptMessage = PromptMessage(
        id = id,
        projectId = projectId,
        role = when (role.lowercase()) {
            "user" -> PromptMessage.Role.USER
            else -> PromptMessage.Role.ASSISTANT
        },
        content = content,
        createdAt = parseInstant(createdAt),
        versionNumber = versionNumber
    )

    private fun buildGenerationFailedMessage(providerMeta: JSONObject?): String {
        val errorCode = providerMeta?.optString("errorCode")?.takeIf { it.isNotBlank() }
        return when (errorCode) {
            "PROVIDER_TIMEOUT" -> "La generación tardó demasiado. Reintenta."
            else -> "La generación falló. Reintenta."
        }
    }
}
