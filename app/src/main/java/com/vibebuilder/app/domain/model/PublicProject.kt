package com.vibebuilder.app.domain.model

import kotlinx.datetime.Instant

data class PublicProject(
    val id: String,
    val title: String,
    val description: String,
    val ownerName: String,
    val currentVersionNumber: Int,
    val currentPreviewUrl: String?,
    val forkCount: Int,
    val publishedAt: Instant?,
    val updatedAt: Instant,
    val originalProjectId: String? = null,
    val originalProjectTitle: String? = null,
    val originalAuthorName: String? = null,
    val versions: List<ProjectVersion> = emptyList()
)
