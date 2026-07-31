package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.ApiProject
import com.vibebuilder.app.data.remote.ApiProjectPreview
import com.vibebuilder.app.data.remote.ApiPreviewTarget
import com.vibebuilder.app.data.remote.ApiProjectVersion
import com.vibebuilder.app.data.remote.ApiPublicProject
import com.vibebuilder.app.data.remote.ApiForkResponse
import com.vibebuilder.app.data.remote.ApiPromptMessage
import com.vibebuilder.app.data.remote.ApiPromptResponse
import com.vibebuilder.app.data.remote.ApiRequestException
import com.vibebuilder.app.data.remote.ApiV0IntegrationStatus
import com.vibebuilder.app.data.remote.VibeBuilderApi
import com.vibebuilder.app.data.auth.AuthSession
import com.vibebuilder.app.data.auth.AuthUser
import com.vibebuilder.app.domain.repository.PreviewUnavailableReason
import com.vibebuilder.app.domain.repository.PreviewUrlResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteProjectRepositoryTest {

    @Test
    fun updateProject_reemplazaEstadoReactivo() = runTest {
        val repository = RemoteProjectRepository(FakeVibeBuilderApi())
        repository.observeProjects().first()

        val updated = repository.updateProject(PROJECT_ID, "  Nuevo nombre  ", " Nueva descripción ")

        assertEquals("Nuevo nombre", updated.title)
        assertEquals("Nueva descripción", repository.observeProject(PROJECT_ID).first()?.description)
    }

    @Test
    fun deleteProject_eliminaProyectoYDetalleCacheado() = runTest {
        val repository = RemoteProjectRepository(FakeVibeBuilderApi())
        repository.observeProjects().first()
        assertTrue(repository.observeVersions(PROJECT_ID).first().isNotEmpty())

        repository.deleteProject(PROJECT_ID)

        assertNull(repository.observeProject(PROJECT_ID).first())
        assertTrue(repository.observeVersions(PROJECT_ID).first().isEmpty())
        assertTrue(repository.observeMessages(PROJECT_ID).first().isEmpty())
    }

    @Test
    fun erroresDeEdicionYBorrado_noMutanEstadoLocal() = runTest {
        val api = FakeVibeBuilderApi()
        val repository = RemoteProjectRepository(api)
        val before = repository.observeProjects().first()

        api.failUpdate = true
        runCatching { repository.updateProject(PROJECT_ID, "Fallará", "") }
        assertEquals(before, repository.observeProjects().first())

        api.failDelete = true
        runCatching { repository.deleteProject(PROJECT_ID) }
        assertEquals(before, repository.observeProjects().first())
    }

    @Test
    fun updateProjectVisibility_publicaProyectoYRefrescaBiblioteca() = runTest {
        val repository = RemoteProjectRepository(FakeVibeBuilderApi())
        repository.observeProjects().first()

        val published = repository.updateProjectVisibility(PROJECT_ID, isPublic = true)

        assertEquals(com.vibebuilder.app.domain.model.ProjectVisibility.PUBLIC, published.visibility)
        val libraryProject = repository.observeLibraryProjects().first().first()
        assertEquals(PROJECT_ID, libraryProject.id)
        assertEquals("Proyecto Persistente", libraryProject.title)
    }

    @Test
    fun observeLibraryProject_cargaDetallePublicoConVersiones() = runTest {
        val repository = RemoteProjectRepository(FakeVibeBuilderApi())

        val detail = repository.observeLibraryProject("public-1").first()

        assertEquals("Plantilla pública", detail?.title)
        assertEquals("Ada", detail?.ownerName)
        assertEquals(1, detail?.versions?.size)
        assertEquals("Prompt público", detail?.versions?.first()?.prompt)
    }

    @Test
    fun forkProject_creaProyectoPropioConAtribucion() = runTest {
        val repository = RemoteProjectRepository(FakeVibeBuilderApi())

        val fork = repository.forkProject("public-1")

        assertEquals("fork-public-1", fork.id)
        assertEquals("Fork de Plantilla pública", fork.title)
        assertEquals("public-1", fork.originalProjectId)
        assertEquals("Plantilla pública", fork.originalProjectTitle)
        assertEquals("Ada", fork.originalAuthorName)
        assertEquals("fork-public-1", repository.observeProjects().first().first().id)
    }

    @Test
    fun observeMessages_recuperaCronologico_ySinDuplicados() = runTest {
        val api = FakeVibeBuilderApi()
        val repository = RemoteProjectRepository(api)

        repository.observeProjects().first()
        val firstLoad = repository.observeMessages(PROJECT_ID).first()
        val secondLoad = repository.observeMessages(PROJECT_ID).first()

        assertEquals(listOf("m-1", "m-2"), firstLoad.map { it.id })
        assertEquals(listOf("m-1", "m-2"), secondLoad.map { it.id })
        assertTrue(firstLoad[0].createdAt <= firstLoad[1].createdAt)
    }

    @Test
    fun regenerateVersion_llamaEndpointYRefrescaDetalle() = runTest {
        val api = FakeVibeBuilderApi()
        val repository = RemoteProjectRepository(api)
        repository.observeProjects().first()

        val regenerated = repository.regenerateVersion(PROJECT_ID, "v-failed")

        assertEquals("v-failed", api.lastRegeneratedVersionId)
        assertEquals(3, regenerated.versionNumber)
        assertEquals(2, regenerated.attemptNumber)
        assertEquals("v-failed", regenerated.sourceVersionId)
        assertEquals(3, repository.observeVersions(PROJECT_ID).first().first().versionNumber)
        assertEquals("m-regenerated", repository.observeMessages(PROJECT_ID).first().last().id)
    }

    @Test
    fun regenerateVersion_fallidaRefrescaDetalleYLanzaError() = runTest {
        val api = FakeVibeBuilderApi().apply { failRegenerationResult = true }
        val repository = RemoteProjectRepository(api)
        repository.observeProjects().first()

        val result = runCatching { repository.regenerateVersion(PROJECT_ID, "v-failed") }

        assertTrue(result.isFailure)
        val latest = repository.observeVersions(PROJECT_ID).first().first()
        assertEquals("failed", api.lastRegenerationStatus)
        assertEquals(com.vibebuilder.app.domain.model.VersionStatus.FAILED, latest.status)
    }


    @Test
    fun resolvePreviewUrl_usaUrlLocal_siEsValida() = runTest {
        val api = FakeVibeBuilderApi()
        val repository = RemoteProjectRepository(api)
        val currentVersion = repository.observeVersions(PROJECT_ID).first().first()
            .copy(previewUrl = "https://preview.v0.dev/local-ready")

        val resolution = repository.resolvePreviewUrl(PROJECT_ID, currentVersion)

        assertEquals(0, api.previewRequests)
        assertEquals(
            PreviewUrlResolution.Available("https://preview.v0.dev/local-ready"),
            resolution
        )
    }

    @Test
    fun resolvePreviewUrl_haceFallbackEndpoint_siNoHayUrlLocal() = runTest {
        val api = FakeVibeBuilderApi().apply {
            forcedPreviewUrl = "https://preview.v0.dev/from-endpoint"
        }
        val repository = RemoteProjectRepository(api)
        val currentVersion = repository.observeVersions(PROJECT_ID).first().first()

        val resolution = repository.resolvePreviewUrl(PROJECT_ID, currentVersion)

        assertEquals(1, api.previewRequests)
        assertEquals(
            PreviewUrlResolution.Available("https://preview.v0.dev/from-endpoint"),
            resolution
        )
    }

    @Test
    fun resolvePreviewUrl_mapeaErroresPreviewConMotivo() = runTest {
        val api = FakeVibeBuilderApi()
        val repository = RemoteProjectRepository(api)
        val currentVersion = repository.observeVersions(PROJECT_ID).first().first()

        api.nextPreviewError = ApiRequestException(409, "PREVIEW_NOT_READY", "not-ready")
        val notReady = repository.resolvePreviewUrl(PROJECT_ID, currentVersion)

        api.nextPreviewError = ApiRequestException(410, "PREVIEW_EXPIRED", "expired")
        val expired = repository.resolvePreviewUrl(PROJECT_ID, currentVersion)

        api.nextPreviewError = ApiRequestException(424, "PREVIEW_UNAVAILABLE", "unavailable")
        val unavailable = repository.resolvePreviewUrl(PROJECT_ID, currentVersion)

        val notReadyResult = notReady as PreviewUrlResolution.Unavailable
        val expiredResult = expired as PreviewUrlResolution.Unavailable
        val unavailableResult = unavailable as PreviewUrlResolution.Unavailable

        assertEquals(PreviewUnavailableReason.NotReady, notReadyResult.reason)
        assertEquals("not-ready", notReadyResult.message)
        assertEquals(PreviewUnavailableReason.Expired, expiredResult.reason)
        assertEquals("expired", expiredResult.message)
        assertEquals(PreviewUnavailableReason.Unavailable, unavailableResult.reason)
        assertEquals("unavailable", unavailableResult.message)
    }

    companion object {
        private const val PROJECT_ID = "project-1"
    }
}

private class FakeVibeBuilderApi : VibeBuilderApi {
    private var versionCounter = 2
    var previewRequests: Int = 0
    var nextPreviewError: ApiRequestException? = null
    var forcedPreviewUrl: String? = null
    var failUpdate: Boolean = false
    var failDelete: Boolean = false
    var failRegenerationResult: Boolean = false
    var lastRegeneratedVersionId: String? = null
    var lastRegenerationStatus: String? = null

    private val projects = mutableListOf(
        ApiProject(
            id = "project-1",
            title = "Proyecto Persistente",
            description = null,
            currentVersionId = "v-2",
            createdAt = "2026-01-01T09:00:00.000Z",
            updatedAt = "2026-01-01T10:00:00.000Z"
        )
    )

    private val publicProjects = mutableListOf(
        ApiPublicProject(
            id = "public-1",
            title = "Plantilla pública",
            description = "Lista para fork",
            ownerName = "Ada",
            currentVersionNumber = 1,
            currentPreviewUrl = "https://preview.v0.dev/public-1",
            forkCount = 4,
            publishedAt = "2026-01-01T10:00:00.000Z",
            updatedAt = "2026-01-01T10:00:00.000Z",
            createdAt = "2026-01-01T09:00:00.000Z",
            originalProjectId = null,
            originalProjectTitle = null,
            originalAuthorName = null,
            versions = listOf(
                ApiProjectVersion(
                    id = "public-v1",
                    projectId = "public-1",
                    versionNumber = 1,
                    promptSnapshot = "Prompt público",
                    status = "success",
                    previewUrl = "https://preview.v0.dev/public-1",
                    createdAt = "2026-01-01T10:00:00.000Z"
                )
            )
        )
    )

    private val versions = mutableMapOf(
        "project-1" to mutableListOf(
            ApiProjectVersion(
                id = "v-2",
                projectId = "project-1",
                versionNumber = 2,
                promptSnapshot = "Segundo prompt",
                status = "success",
                previewUrl = null,
                createdAt = "2026-01-01T10:00:00.000Z"
            ),
            ApiProjectVersion(
                id = "v-1",
                projectId = "project-1",
                versionNumber = 1,
                promptSnapshot = "Primer prompt",
                status = "success",
                previewUrl = null,
                createdAt = "2026-01-01T09:30:00.000Z"
            ),
            ApiProjectVersion(
                id = "v-failed",
                projectId = "project-1",
                versionNumber = 0,
                promptSnapshot = "Prompt fallido",
                status = "failed",
                previewUrl = null,
                createdAt = "2026-01-01T09:00:00.000Z",
                failureCode = "PROVIDER_ERROR"
            )
        )
    )

    private val messages = mutableMapOf(
        "project-1" to mutableListOf(
            ApiPromptMessage(
                id = "m-1",
                projectId = "project-1",
                versionId = "v-1",
                role = "user",
                content = "Primer prompt",
                createdAt = "2026-01-01T09:30:00.000Z",
                versionNumber = 1
            ),
            ApiPromptMessage(
                id = "m-2",
                projectId = "project-1",
                versionId = "v-2",
                role = "user",
                content = "Segundo prompt",
                createdAt = "2026-01-01T10:00:00.000Z",
                versionNumber = 2
            ),
            ApiPromptMessage(
                id = "m-2",
                projectId = "project-1",
                versionId = "v-2",
                role = "user",
                content = "Segundo prompt duplicado remoto",
                createdAt = "2026-01-01T10:00:00.000Z",
                versionNumber = 2
            )
        )
    )

    override suspend fun register(name: String, email: String, password: String): AuthSession =
        AuthSession(
            token = "token",
            expiresAt = "2026-02-01T00:00:00.000Z",
            user = AuthUser(
                id = "user-1",
                email = email,
                name = name,
                avatarUrl = null
            )
        )

    override suspend fun login(email: String, password: String): AuthSession =
        AuthSession(
            token = "token",
            expiresAt = "2026-02-01T00:00:00.000Z",
            user = AuthUser(
                id = "user-1",
                email = email,
                name = "Ada",
                avatarUrl = null
            )
        )

    override suspend fun getCurrentUser(): AuthUser =
        AuthUser(
            id = "user-1",
            email = "ada@example.com",
            name = "Ada",
            avatarUrl = null
        )

    override suspend fun logout() = Unit

    override suspend fun getProjects(): List<ApiProject> = projects.toList()

    override suspend fun getLibraryProjects(): List<ApiPublicProject> = publicProjects.toList()

    override suspend fun getLibraryProject(projectId: String): ApiPublicProject =
        publicProjects.first { it.id == projectId }

    override suspend fun getProjectVersions(projectId: String): List<ApiProjectVersion> =
        versions[projectId].orEmpty().toList()

    override suspend fun getProjectMessages(projectId: String): List<ApiPromptMessage> =
        messages[projectId].orEmpty().toList()

    override suspend fun getProjectPreview(
        projectId: String,
        target: ApiPreviewTarget,
        versionNumber: Int?
    ): ApiProjectPreview {
        previewRequests += 1
        nextPreviewError?.let { error ->
            nextPreviewError = null
            throw error
        }
        val candidate = when (target) {
            ApiPreviewTarget.CURRENT -> versions[projectId].orEmpty().firstOrNull()
            ApiPreviewTarget.VERSION -> versions[projectId].orEmpty()
                .firstOrNull { it.versionNumber == versionNumber }
        } ?: error("Version no encontrada")

        return ApiProjectPreview(
            projectId = projectId,
            target = target.value,
            versionId = candidate.id,
            versionNumber = candidate.versionNumber,
            previewUrl = forcedPreviewUrl
                ?: candidate.previewUrl
                ?: "https://preview.v0.dev/mock-$projectId-${candidate.versionNumber}"
        )
    }

    override suspend fun createProject(title: String, description: String): String {
        val id = "project-${projects.size + 1}"
        projects += ApiProject(
            id = id,
            title = title,
            description = description,
            currentVersionId = null,
            createdAt = "2026-01-01T11:00:00.000Z",
            updatedAt = "2026-01-01T11:00:00.000Z"
        )
        return id
    }

    override suspend fun updateProject(projectId: String, title: String, description: String): ApiProject {
        if (failUpdate) throw IOException("update failed")
        val index = projects.indexOfFirst { it.id == projectId }
        val updated = projects[index].copy(
            title = title.trim(),
            description = description.trim().ifEmpty { null },
            updatedAt = "2026-01-01T12:00:00.000Z"
        )
        projects[index] = updated
        return updated
    }

    override suspend fun updateProjectVisibility(projectId: String, visibility: String): ApiProject {
        val index = projects.indexOfFirst { it.id == projectId }
        val updated = projects[index].copy(
            visibility = visibility,
            publishedAt = if (visibility == "public") "2026-01-01T12:00:00.000Z" else null,
            updatedAt = "2026-01-01T12:00:00.000Z"
        )
        projects[index] = updated
        if (visibility == "public") {
            publicProjects.removeAll { it.id == projectId }
            publicProjects.add(
                0,
                ApiPublicProject(
                    id = updated.id,
                    title = updated.title,
                    description = updated.description,
                    ownerName = "Tú",
                    currentVersionNumber = 2,
                    currentPreviewUrl = null,
                    forkCount = 0,
                    publishedAt = updated.publishedAt,
                    updatedAt = updated.updatedAt,
                    createdAt = updated.createdAt,
                    originalProjectId = updated.originalProjectId,
                    originalProjectTitle = updated.originalProjectTitle,
                    originalAuthorName = updated.originalAuthorName,
                    versions = emptyList()
                )
            )
        } else {
            publicProjects.removeAll { it.id == projectId }
        }
        return updated
    }

    override suspend fun deleteProject(projectId: String) {
        if (failDelete) throw IOException("delete failed")
        projects.removeAll { it.id == projectId }
        versions.remove(projectId)
        messages.remove(projectId)
    }

    override suspend fun forkProject(projectId: String): ApiForkResponse {
        val source = publicProjects.first { it.id == projectId }
        val forkedProjectId = "fork-$projectId"
        projects.add(
            0,
            ApiProject(
                id = forkedProjectId,
                title = "Fork de ${source.title}",
                description = source.description,
                currentVersionId = "fork-v1",
                currentVersionNumber = source.currentVersionNumber,
                visibility = "private",
                originalProjectId = source.id,
                originalProjectTitle = source.title,
                originalAuthorName = source.ownerName,
                forkedAt = "2026-01-01T12:00:00.000Z",
                createdAt = "2026-01-01T12:00:00.000Z",
                updatedAt = "2026-01-01T12:00:00.000Z"
            )
        )
        versions[forkedProjectId] = source.versions.map {
            it.copy(id = "fork-v${it.versionNumber}", projectId = forkedProjectId)
        }.toMutableList()
        messages[forkedProjectId] = mutableListOf()
        return ApiForkResponse(
            projectId = forkedProjectId,
            originalProjectId = source.id,
            originalProjectTitle = source.title,
            originalAuthorName = source.ownerName
        )
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ApiPromptResponse {
        versionCounter += 1
        val versionId = "v-$versionCounter"
        val messageId = "m-$versionCounter"
        val createdAt = "2026-01-01T11:0${versionCounter}0.000Z"
        versions.getOrPut(projectId) { mutableListOf() }.add(
            0,
            ApiProjectVersion(
                id = versionId,
                projectId = projectId,
                versionNumber = versionCounter,
                promptSnapshot = prompt,
                status = "success",
                previewUrl = null,
                createdAt = createdAt
            )
        )
        messages.getOrPut(projectId) { mutableListOf() }.add(
            ApiPromptMessage(
                id = messageId,
                projectId = projectId,
                versionId = versionId,
                role = "user",
                content = prompt,
                createdAt = createdAt,
                versionNumber = versionCounter
            )
        )
        return ApiPromptResponse(
            promptMessageId = messageId,
            projectVersionId = versionId,
            versionNumber = versionCounter,
            status = "success",
            providerMeta = JSONObject()
        )
    }

    override suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String?
    ): ApiPromptResponse {
        versionCounter += 1
        lastRegeneratedVersionId = versionId
        val newVersionId = "v-regenerated"
        val messageId = "m-regenerated"
        val status = if (failRegenerationResult) "failed" else "success"
        lastRegenerationStatus = status
        val createdAt = "2026-01-01T12:00:00.000Z"
        val prompt = correctedPrompt ?: "Prompt fallido"
        versions.getOrPut(projectId) { mutableListOf() }.add(
            0,
            ApiProjectVersion(
                id = newVersionId,
                projectId = projectId,
                versionNumber = versionCounter,
                promptSnapshot = prompt,
                status = status,
                previewUrl = null,
                createdAt = createdAt,
                sourceVersionId = versionId,
                attemptNumber = 2,
                failureCode = if (status == "failed") "PROVIDER_ERROR" else null
            )
        )
        messages.getOrPut(projectId) { mutableListOf() }.add(
            ApiPromptMessage(
                id = messageId,
                projectId = projectId,
                versionId = newVersionId,
                role = "user",
                content = prompt,
                createdAt = createdAt,
                versionNumber = versionCounter
            )
        )
        return ApiPromptResponse(
            promptMessageId = messageId,
            projectVersionId = newVersionId,
            versionNumber = versionCounter,
            status = status,
            providerMeta = null,
            sourceVersionId = versionId,
            attemptNumber = 2,
            failureCode = if (status == "failed") "PROVIDER_ERROR" else null
        )
    }

    override suspend fun getV0IntegrationStatus(): ApiV0IntegrationStatus =
        ApiV0IntegrationStatus(
            keyStorageAvailable = false,
            sessionKeyConfigured = false,
            sessionKeyHint = null,
            envKeyActive = false
        )

    override suspend fun saveV0ApiKey(apiKey: String) = Unit

    override suspend fun deleteV0ApiKey() = Unit

    override suspend fun testV0ApiKey(apiKey: String?) = Unit
}
