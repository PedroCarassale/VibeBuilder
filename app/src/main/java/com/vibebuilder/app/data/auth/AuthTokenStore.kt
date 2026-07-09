package com.vibebuilder.app.data.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AuthTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    fun currentToken(): String? = _session.value?.token

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EXPIRES_AT, session.expiresAt)
            .putString(KEY_USER, session.user.toJson().toString())
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER)
            .apply()
        _session.value = null
    }

    private fun loadSession(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)?.takeIf { it.isNotBlank() } ?: return null
        val userJson = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            AuthSession(
                token = token,
                expiresAt = expiresAt,
                user = JSONObject(userJson).toAuthUser()
            )
        }.getOrNull()
    }

    private fun AuthUser.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("name", name)
        .put("avatarUrl", avatarUrl)

    private fun JSONObject.toAuthUser(): AuthUser = AuthUser(
        id = getString("id"),
        email = optStringOrNull("email"),
        name = optStringOrNull("name"),
        avatarUrl = optStringOrNull("avatarUrl")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private companion object {
        const val PREFS_NAME = "vibebuilder_prefs"
        const val KEY_TOKEN = "auth_token"
        const val KEY_EXPIRES_AT = "auth_expires_at"
        const val KEY_USER = "auth_user"
    }
}
