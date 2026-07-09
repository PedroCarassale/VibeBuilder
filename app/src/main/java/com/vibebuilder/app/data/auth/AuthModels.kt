package com.vibebuilder.app.data.auth

data class AuthUser(
    val id: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?
)

data class AuthSession(
    val token: String,
    val expiresAt: String,
    val user: AuthUser
)
