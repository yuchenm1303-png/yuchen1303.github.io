package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.service.SupabaseUserSession

class SupabaseSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "supabase_auth_session",
        Context.MODE_PRIVATE
    )

    fun load(): SupabaseUserSession? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null).orEmpty()
        val email = preferences.getString(KEY_EMAIL, null).orEmpty()
        val userId = preferences.getString(KEY_USER_ID, null).orEmpty()
        if (accessToken.isBlank() || email.isBlank() || userId.isBlank()) return null
        return SupabaseUserSession(
            userId = userId,
            email = email,
            accessToken = accessToken,
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null).orEmpty(),
            expiresAtEpochSeconds = preferences.getLong(KEY_EXPIRES_AT, 0L)
        )
    }

    fun save(session: SupabaseUserSession) {
        preferences.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
