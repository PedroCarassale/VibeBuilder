package com.vibebuilder.model

data class Project(
    val id: String,
    val title: String,
    val description: String,
    val updatedAt: String,
    val currentVersion: Int
)

data class PromptMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val createdAt: String
)

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ProjectVersion(
    val versionNumber: Int,
    val promptSummary: String,
    val status: String,
    val createdAt: String
)
