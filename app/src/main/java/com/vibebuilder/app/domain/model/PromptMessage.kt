package com.vibebuilder.app.domain.model

import kotlinx.datetime.Instant

/**
 * A single message in the project prompt conversation, either from the user or the AI.
 */
data class PromptMessage(
    val id: String,
    val projectId: String,
    val role: Role,
    val content: String,
    val createdAt: Instant,
    val versionNumber: Int? = null
) {
    enum class Role { USER, ASSISTANT }
}
