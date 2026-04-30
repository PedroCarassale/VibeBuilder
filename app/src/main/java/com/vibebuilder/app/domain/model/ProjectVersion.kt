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
    val status: VersionStatus = VersionStatus.READY
)

enum class VersionStatus { GENERATING, READY, FAILED }
