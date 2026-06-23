package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.ApiProject
import com.vibebuilder.app.data.remote.ApiProjectPreview
import com.vibebuilder.app.data.remote.ApiPreviewTarget
import com.vibebuilder.app.data.remote.ApiProjectVersion
import com.vibebuilder.app.data.remote.ApiPromptMessage
import com.vibebuilder.app.data.remote.ApiPromptResponse
import com.vibebuilder.app.data.remote.ApiRequestException
import com.vibebuilder.app.data.remote.ApiV0IntegrationStatus
import com.vibebuilder.app.data.remote.VibeBuilderApi
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

    override suspend fun getProjects(): List<ApiProject> = projects.toList()

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

    override suspend fun deleteProject(projectId: String) {
        if (failDelete) throw IOException("delete failed")
        projects.removeAll { it.id == projectId }
        versions.remove(projectId)
        messages.remove(projectId)
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
