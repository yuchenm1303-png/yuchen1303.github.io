package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.text.Normalizer

/**
 * Builds a factual index of launchable apps installed on the current device.
 *
 * This class never interprets user language, ranks app candidates or chooses a target app. The
 * cloud planner owns semantic selection; Android only exposes the real inventory and validates a
 * returned package name before execution.
 */
data class InstalledAppEntry(
    val label: String,
    val packageName: String,
)

class InstalledAppIndex(
    context: Context,
) {
    private val context = context.applicationContext

    /**
     * Supplies neutral identifiers to the cloud context only. These values are never used locally
     * to parse a goal or select an application.
     */
    fun aliasesFor(app: InstalledAppEntry): List<String> {
        val normalizedLabel = normalizeAppLabel(app.label)
        val packageTail = app.packageName.substringAfterLast('.').trim()
        return listOf(app.label, normalizedLabel, packageTail)
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }
            .distinct()
    }

    fun getLaunchableApps(forceReload: Boolean = false): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (!forceReload && sharedCachedApps.isNotEmpty() && now - sharedLastLoadedAt < CACHE_TTL_MS) {
                return sharedCachedApps
            }
        }

        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(launchIntent, launcherQueryFlags())
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName.orEmpty().trim()
                if (packageName.isBlank()) return@mapNotNull null
                if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { safeApplicationLabel(packageManager, packageName) }
                if (label.isBlank()) return@mapNotNull null
                InstalledAppEntry(label = label, packageName = packageName)
            }
            .distinctBy { app -> app.packageName }
            .sortedWith(
                compareBy<InstalledAppEntry> { app -> normalizeAppLabel(app.label) }
                    .thenBy { app -> app.packageName },
            )

        synchronized(cacheLock) {
            sharedCachedApps = apps
            sharedLastLoadedAt = now
        }
        return apps
    }

    private fun launcherQueryFlags(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }
    }

    private fun safeApplicationLabel(
        packageManager: PackageManager,
        packageName: String,
    ): String {
        return runCatching {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

    private fun normalizeAppLabel(value: String): String {
        return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), "")
    }

    companion object {
        private val cacheLock = Any()
        private var sharedCachedApps: List<InstalledAppEntry> = emptyList()
        private var sharedLastLoadedAt: Long = 0L
        private const val CACHE_TTL_MS = 5 * 60_000L
    }
}
