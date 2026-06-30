package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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

/**
 * The canonical label is already uploaded in its own field, so aliases only carry a genuinely
 * different normalization variant. This avoids repeating the label and package tail for every app
 * in each visual-agent request while preserving neutral matching information for the cloud model.
 */
internal fun buildNeutralInstalledAppAliases(label: String): List<String> {
    val cleanLabel = label.trim()
    if (cleanLabel.isBlank()) return emptyList()
    val normalizedLabel = normalizeInstalledAppLabel(cleanLabel)
    return listOf(normalizedLabel)
        .filter { value -> value.isNotBlank() && !value.equals(cleanLabel, ignoreCase = true) }
        .distinct()
}

internal fun normalizeInstalledAppLabel(value: String): String {
    return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), "")
}

class InstalledAppIndex(
    context: Context,
) {
    private val context = context.applicationContext

    init {
        ensurePackageChangeObserver(context)
    }

    /**
     * Supplies neutral identifiers to the cloud context only. These values are never used locally
     * to parse a goal or select an application.
     */
    fun aliasesFor(app: InstalledAppEntry): List<String> {
        return buildNeutralInstalledAppAliases(app.label)
    }

    fun getLaunchableApps(forceReload: Boolean = false): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (
                !forceReload &&
                sharedCacheLoaded &&
                now - sharedLastLoadedAt < CACHE_FALLBACK_TTL_MS
            ) {
                return sharedCachedApps
            }
        }

        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(launchIntent, launcherQueryFlags())
            .mapNotNull { info ->
                // queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER) already guarantees a
                // launchable activity. Avoid an additional getLaunchIntentForPackage() binder call
                // for every result.
                val packageName = info.activityInfo?.packageName.orEmpty().trim()
                if (packageName.isBlank()) return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { safeApplicationLabel(packageManager, packageName) }
                if (label.isBlank()) return@mapNotNull null
                InstalledAppEntry(label = label, packageName = packageName)
            }
            .distinctBy { app -> app.packageName }
            .sortedWith(
                compareBy<InstalledAppEntry> { app -> normalizeInstalledAppLabel(app.label) }
                    .thenBy { app -> app.packageName },
            )

        synchronized(cacheLock) {
            sharedCachedApps = apps
            sharedLastLoadedAt = now
            sharedCacheLoaded = true
        }
        return apps
    }

    private fun launcherQueryFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    companion object {
        private val cacheLock = Any()
        @Volatile private var sharedCachedApps: List<InstalledAppEntry> = emptyList()
        @Volatile private var sharedLastLoadedAt: Long = 0L
        @Volatile private var sharedCacheLoaded: Boolean = false
        @Volatile private var packageObserverInstalled: Boolean = false
        private var packageObserver: BroadcastReceiver? = null

        /**
         * Package add/remove/replace broadcasts invalidate the process-wide inventory immediately.
         * The long fallback TTL only protects against an OEM dropping a broadcast; normal chat no
         * longer rescans PackageManager every five minutes.
         */
        private fun ensurePackageChangeObserver(context: Context) {
            if (packageObserverInstalled) return
            synchronized(cacheLock) {
                if (packageObserverInstalled) return
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        invalidateCache()
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                val registered = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("DEPRECATION")
                        context.registerReceiver(receiver, filter)
                    }
                    true
                }.getOrDefault(false)
                if (registered) {
                    packageObserver = receiver
                    packageObserverInstalled = true
                }
            }
        }

        internal fun invalidateCache() {
            synchronized(cacheLock) {
                sharedCachedApps = emptyList()
                sharedLastLoadedAt = 0L
                sharedCacheLoaded = false
            }
        }

        private const val CACHE_FALLBACK_TTL_MS = 24 * 60 * 60_000L
    }
}
