package com.yuchen.ailedger.service

import android.content.Context
import java.util.UUID

internal object AgentClientIdentity {
    private const val PREFERENCES_NAME = "ai_agent_client_identity"
    private const val DEVICE_ID_KEY = "device_id"
    private const val DEVICE_ID_PREFIX = "android-install-"
    private const val SESSION_ID_PREFIX = "visual-session-"

    fun getOrCreateDeviceId(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val existing = preferences.getString(DEVICE_ID_KEY, null)
            ?.trim()
            ?.takeIf { it.startsWith(DEVICE_ID_PREFIX) && it.length <= 120 }
        if (existing != null) return existing

        val created = DEVICE_ID_PREFIX + UUID.randomUUID().toString()
        preferences.edit().putString(DEVICE_ID_KEY, created).apply()
        return created
    }

    fun newVisualSessionId(): String = SESSION_ID_PREFIX + UUID.randomUUID().toString()
}
