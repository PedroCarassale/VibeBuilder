package com.vibebuilder.app.di

import android.content.Context
import com.vibebuilder.app.BuildConfig
import com.vibebuilder.app.data.remote.HttpVibeBuilderApi
import com.vibebuilder.app.data.remote.SharedPrefsSessionIdProvider
import com.vibebuilder.app.data.repository.RemoteProjectRepository
import com.vibebuilder.app.domain.repository.ProjectRepository

/**
 * Tiny manual DI container.
 *
 * Intentionally avoids Hilt/Koin in Delivery 1 to keep the project setup minimal.
 * Project list and prompt envío consume backend real via [RemoteProjectRepository].
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val projectRepository: ProjectRepository by lazy {
        check(::appContext.isInitialized) {
            "ServiceLocator must be initialized from Application.onCreate()"
        }
        val sessionIdProvider = SharedPrefsSessionIdProvider(appContext)
        val api = HttpVibeBuilderApi(
            baseUrl = BuildConfig.API_BASE_URL,
            sessionIdProvider = sessionIdProvider
        )
        RemoteProjectRepository(api)
    }
}
