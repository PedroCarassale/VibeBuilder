package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.ApiProject
import com.vibebuilder.app.data.remote.ApiProjectVersion
import com.vibebuilder.app.data.remote.ApiPromptMessage
import com.vibebuilder.app.data.remote.ApiPromptResponse
import com.vibebuilder.app.data.remote.VibeBuilderApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteProjectRepositoryTest {

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

    companion object {
        private const val PROJECT_ID = "project-1"
    }
}

private class FakeVibeBuilderApi : VibeBuilderApi {
    private var versionCounter = 2

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
                prompt = "Segundo prompt",
                status = "success",
                previewUrl = null,
                createdAt = "2026-01-01T10:00:00.000Z"
            ),
            ApiProjectVersion(
                id = "v-1",
                projectId = "project-1",
                versionNumber = 1,
                prompt = "Primer prompt",
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
                prompt = prompt,
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
}
