package com.vibebuilder.app.data.auth

import com.vibebuilder.app.data.remote.VibeBuilderApi
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val api: VibeBuilderApi,
    private val tokenStore: AuthTokenStore
) {
    val session: StateFlow<AuthSession?> = tokenStore.session

    suspend fun register(name: String, email: String, password: String): AuthSession {
        val session = api.register(name = name, email = email, password = password)
        tokenStore.save(session)
        return session
    }

    suspend fun login(email: String, password: String): AuthSession {
        val session = api.login(email = email, password = password)
        tokenStore.save(session)
        return session
    }

    suspend fun refreshCurrentUser() {
        val currentToken = tokenStore.currentToken()
        if (currentToken.isNullOrBlank()) return
        runCatching {
            val user = api.getCurrentUser()
            val current = tokenStore.session.value
            if (current != null) {
                tokenStore.save(current.copy(user = user))
            }
        }.onFailure {
            tokenStore.clear()
        }
    }

    suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}
