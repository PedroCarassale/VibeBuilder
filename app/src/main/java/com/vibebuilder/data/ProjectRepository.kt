package com.vibebuilder.data

import com.vibebuilder.model.MessageRole
import com.vibebuilder.model.Project
import com.vibebuilder.model.ProjectVersion
import com.vibebuilder.model.PromptMessage

interface ProjectRepository {
    fun getProjects(): List<Project>
    fun createProject(title: String, description: String): Project
    fun getMessages(projectId: String): List<PromptMessage>
    fun sendPrompt(projectId: String, prompt: String): List<PromptMessage>
    fun getVersions(projectId: String): List<ProjectVersion>
}

class MockProjectRepository : ProjectRepository {
    private val projects = mutableListOf(
        Project("1", "Gym Landing Page", "Marketing site for local gym", "2026-04-30", 2),
        Project("2", "Task Dashboard", "Simple productivity dashboard", "2026-04-29", 1)
    )

    private val messagesByProject = mutableMapOf(
        "1" to mutableListOf(
            PromptMessage("m1", MessageRole.USER, "Create a modern gym landing page.", "2026-04-29"),
            PromptMessage("m2", MessageRole.ASSISTANT, "Done. Added hero, pricing, and CTA sections.", "2026-04-29")
        )
    )

    override fun getProjects(): List<Project> = projects.toList()

    override fun createProject(title: String, description: String): Project {
        val newProject = Project(
            id = (projects.size + 1).toString(),
            title = title,
            description = description,
            updatedAt = "2026-04-30",
            currentVersion = 1
        )
        projects.add(0, newProject)
        messagesByProject[newProject.id] = mutableListOf()
        return newProject
    }

    override fun getMessages(projectId: String): List<PromptMessage> {
        return messagesByProject[projectId]?.toList().orEmpty()
    }

    override fun sendPrompt(projectId: String, prompt: String): List<PromptMessage> {
        val messages = messagesByProject.getOrPut(projectId) { mutableListOf() }
        messages.add(PromptMessage("u-${messages.size}", MessageRole.USER, prompt, "2026-04-30"))
        messages.add(
            PromptMessage(
                "a-${messages.size}",
                MessageRole.ASSISTANT,
                "Mock generation complete. Preview placeholder refreshed.",
                "2026-04-30"
            )
        )
        return messages
    }

    override fun getVersions(projectId: String): List<ProjectVersion> {
        val messageCount = messagesByProject[projectId]?.count { it.role == MessageRole.USER } ?: 0
        return (1..maxOf(1, messageCount)).map { version ->
            ProjectVersion(
                versionNumber = version,
                promptSummary = if (version == 1) "Initial generation" else "Iteration $version",
                status = "Ready",
                createdAt = "2026-04-30"
            )
        }
    }
}
