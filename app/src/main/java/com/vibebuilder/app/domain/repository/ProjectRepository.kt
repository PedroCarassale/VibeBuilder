package com.vibebuilder.app.domain.repository

import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PromptMessage
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for project data, independent of the storage backend.
 *
 * Delivery 1 ships an in-memory mock implementation. A real implementation will
 * live in `data/remote` and call the endpoints listed in AGENTS.md
 * (`POST /projects`, `GET /projects`, `POST /projects/:id/prompts`, …).
 */
interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>

    fun observeProject(projectId: String): Flow<Project?>

    fun observeVersions(projectId: String): Flow<List<ProjectVersion>>

    fun observeMessages(projectId: String): Flow<List<PromptMessage>>

    suspend fun createProject(
        title: String,
        description: String
    ): Project

    suspend fun updateProject(projectId: String, title: String, description: String): Project

    suspend fun deleteProject(projectId: String)

    suspend fun sendPrompt(projectId: String, prompt: String): ProjectVersion

    suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String? = null
    ): ProjectVersion

    suspend fun getCurrentVersion(projectId: String): ProjectVersion?

    suspend fun resolvePreviewUrl(
        projectId: String,
        currentVersion: ProjectVersion?
    ): PreviewUrlResolution
}

sealed interface PreviewUrlResolution {
    data class Available(val url: String) : PreviewUrlResolution
    data class Unavailable(
        val reason: PreviewUnavailableReason,
        val message: String? = null
    ) : PreviewUrlResolution
}

enum class PreviewUnavailableReason {
    NotReady,
    Expired,
    Unavailable,
    Unknown
}
