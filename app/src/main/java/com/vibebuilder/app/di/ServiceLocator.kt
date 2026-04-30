package com.vibebuilder.app.di

import com.vibebuilder.app.data.repository.MockProjectRepository
import com.vibebuilder.app.domain.repository.ProjectRepository

/**
 * Tiny manual DI container.
 *
 * Intentionally avoids Hilt/Koin in Delivery 1 to keep the project setup minimal.
 * When the real backend lands, swap [projectRepository] for `RemoteProjectRepository(api)`
 * here (or behind a build flag) — no UI code needs to change.
 */
object ServiceLocator {

    val projectRepository: ProjectRepository by lazy { MockProjectRepository() }
}
