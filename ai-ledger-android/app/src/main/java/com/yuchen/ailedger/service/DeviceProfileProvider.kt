package com.yuchen.ailedger.service

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Provides non-private execution context for the cloud Computer Use planner.
 * It exposes only launchable app labels/packages and basic screen metadata, not contacts, files, messages, or notifications.
 */
class DeviceProfileProvider(
    private val context: Context,
) {
    fun toJson(snapshot: AgentScreenSnapshot? = null): JSONObject {
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val launchableApps = InstalledAppIndex(context)
            .getLaunchableApps()
            .filterNot { it.packageName == context.packageName }
            .take(MAX_LAUNCHABLE_APPS)

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("privacyScope", "launchable_apps_and_screen_only")
            put("currentPackage", snapshot?.currentApp.orEmpty())
            put("screen", JSONObject().apply {
                val visual = snapshot?.visual?.takeIf { it.hasImage }
                put("width", visual?.displayWidth?.takeIf { it > 0 } ?: metrics.widthPixels)
                put("height", visual?.displayHeight?.takeIf { it > 0 } ?: metrics.heightPixels)
                put("density", metrics.density)
                put("densityDpi", metrics.densityDpi)
                put("orientation", if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            })
            put("locale", Locale.getDefault().toLanguageTag())
            put("installedLaunchableApps", JSONArray().apply {
                launchableApps.forEach { app ->
                    put(JSONObject().apply {
                        put("label", app.label)
                        put("packageName", app.packageName)
                        put("hasLauncher", true)
                    })
                }
            })
            put("installedLaunchableAppCount", launchableApps.size)
            put("note", "Use installedLaunchableApps for open_app packageName. Do not guess package names when app label is present here.")
        }
    }

    companion object {
        private const val MAX_LAUNCHABLE_APPS = 180

        fun tryCreateFromCurrentApplication(): DeviceProfileProvider? {
            return runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentApplication = activityThread.getMethod("currentApplication").invoke(null) as? Context
                currentApplication?.applicationContext?.let { DeviceProfileProvider(it) }
            }.getOrNull()
        }
    }
}
