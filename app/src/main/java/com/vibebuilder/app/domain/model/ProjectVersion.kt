package com.vibebuilder.app.domain.model

import kotlinx.datetime.Instant

/**
 * A single generated state of a [Project]. Each successful prompt produces a new version.
 *
 * [previewHtml] is a placeholder representation of the generated web app for Delivery 1.
 * The real backend will return a `previewUrl` (see AGENTS.md → ProjectVersion).
 */
data class ProjectVersion(
    val id: String,
    val projectId: String,
    val versionNumber: Int,
    val prompt: String,
    val previewHtml: String,
    val previewUrl: String? = null,
    val createdAt: Instant,
    val status: VersionStatus = VersionStatus.READY,
    val sourceVersionId: String? = null,
    val attemptNumber: Int = 1,
    val failureCode: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null
)

enum class VersionStatus { QUEUED, GENERATING, VALIDATING, READY, FAILED, CANCELLED }
