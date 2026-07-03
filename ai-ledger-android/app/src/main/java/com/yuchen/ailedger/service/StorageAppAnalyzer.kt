package com.yuchen.ailedger.service

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager

internal class StorageAppAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun analyze(
        app: ManagedAppSummary,
        usageGranted: Boolean,
        lastUsedRaw: Long?,
    ): StorageAppOptimizationItem? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(app.packageName, 0)
            }
        }.getOrNull() ?: return null
        val storage = queryStorage(app)
        val lastUsed = lastUsedRaw?.takeIf { it > 0L }
        val days = if (usageGranted) {
            StorageProductizationPolicy.unusedDays(lastUsed, packageInfo.firstInstallTime, System.currentTimeMillis())
        } else null
        return StorageAppOptimizationItem(
            label = app.label,
            packageName = app.packageName,
            apkBytes = app.apkBytes,
            appBytes = storage?.appBytes,
            dataBytes = storage?.dataBytes,
            cacheBytes = storage?.cacheBytes,
            totalBytes = storage?.let { it.appBytes + it.dataBytes + it.cacheBytes },
            firstInstallTime = packageInfo.firstInstallTime,
            lastUsedAt = lastUsed,
            unusedDays = days,
            isProtected = app.isProtected,
            suggestionReason = StorageProductizationPolicy.longUnusedReason(days, usageGranted),
        )
    }

    fun hasUsageAccess(): Boolean {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun queryStorage(app: ManagedAppSummary): PackageStorage? {
        if (!hasUsageAccess()) return null
        val manager = appContext.getSystemService(StorageStatsManager::class.java) ?: return null
        return runCatching {
            val stats = manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                app.packageName,
                UserHandle.getUserHandleForUid(app.uid),
            )
            PackageStorage(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrNull()
    }

    private data class PackageStorage(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long)
}
