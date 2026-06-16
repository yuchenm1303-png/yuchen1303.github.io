package com.yuchen.ailedger.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class FullScreenOpenGlPreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var enabled by mutableStateOf(preferences.getBoolean(KEY_ENABLED, true))
        private set

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "render_feature_preferences"
        private const val KEY_ENABLED = "full_screen_opengl_enabled"

        @Volatile
        private var instance: FullScreenOpenGlPreferences? = null

        fun get(context: Context): FullScreenOpenGlPreferences {
            return instance ?: synchronized(this) {
                instance ?: FullScreenOpenGlPreferences(context).also { instance = it }
            }
        }
    }
}
