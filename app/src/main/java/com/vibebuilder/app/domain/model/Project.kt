package com.vibebuilder.app.domain.model

import kotlinx.datetime.Instant

/**
 * A user-created project that represents a generated web app.
 *
 * In Delivery 1 the [currentVersion] is just a snapshot held in memory; later it will
 * be backed by the remote backend described in AGENTS.md (`GET /projects/:id`).
 */
data class Project(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val currentVersionNumber: Int,
    val visibility: ProjectVisibility = ProjectVisibility.PRIVATE,
    val originalProjectId: String? = null,
    val originalProjectTitle: String? = null,
    val originalAuthorName: String? = null,
    val forkedAt: Instant? = null
)

enum class ProjectVisibility {
    PRIVATE,
    PUBLIC,
    SHARED
}
