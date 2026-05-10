package com.vibebuilder.app.data.remote

import android.content.Context
import java.util.UUID

class SharedPrefsSessionIdProvider(
    context: Context
) : SessionIdProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getSessionId(): String {
        val current = prefs.getString(KEY_SESSION_ID, null)
        if (!current.isNullOrBlank()) {
            return current
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, generated).apply()
        return generated
    }

    private companion object {
        const val PREFS_NAME = "vibebuilder_prefs"
        const val KEY_SESSION_ID = "session_id"
    }
}
