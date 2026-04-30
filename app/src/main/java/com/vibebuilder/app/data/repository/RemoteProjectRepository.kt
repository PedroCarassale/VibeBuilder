package com.vibebuilder.app.data.repository

import com.vibebuilder.app.data.remote.VibeBuilderApi
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

/**
 * Skeleton for the future network-backed repository.
 *
 * Intentionally throws so the app fails fast if it gets wired up before the backend
 * exists. Once the API is implemented, port the mock's behavior here using the
 * endpoints listed in [VibeBuilderApi].
 */
@Suppress("UNUSED_PARAMETER")
class RemoteProjectRepository(
    private val api: VibeBuilderApi
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> = notImplemented()
    override fun observeProject(projectId: String): Flow<Project?> = notImplemented()
    override fun observeVersions(projectId: String): Flow<List<ProjectVersion>> = notImplemented()
    override fun observeMessages(projectId: String): Flow<List<PromptMessage>> = notImplemented()

    override suspend fun createProject(
        title: String,
        description: String,
        initialPrompt: String
    ): Project = notImplemented()

    override suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion =
        notImplemented()

    override suspend fun getCurrentVersion(projectId: String): ProjectVersion? = notImplemented()

    private fun <T> notImplemented(): T =
        throw NotImplementedError("RemoteProjectRepository is not wired up yet (Delivery 1 uses MockProjectRepository).")
}
