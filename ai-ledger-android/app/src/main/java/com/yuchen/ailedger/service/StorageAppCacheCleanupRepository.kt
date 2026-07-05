package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager

private const val ALL_APP_CACHE_TRIM_TARGET_BYTES = 999_999_999_999L

internal object StorageAppCacheCleanupPolicy {
    fun command(): String = "pm trim-caches $ALL_APP_CACHE_TRIM_TARGET_BYTES"

    fun isCacheOnlyCommand(command: String): Boolean {
        val normalized = command.trim().lowercase()
        return normalized.startsWith("pm trim-caches ") &&
            !normalized.contains("pm clear") &&
            !normalized.contains("rm ") &&
            !normalized.contains("clear_app_data")
    }
}

data class StorageAppCacheCleanupResult(
    val ok: Boolean,
    val message: String,
    val beforeBytes: Long?,
    val afterBytes: Long?,
    val releasedBytes: Long?,
    val shellResult: DeviceShellExecResult,
)

class StorageAppCacheCleanupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val storageRepository = StorageManagementRepository(appContext)
    private val specialCleanupRepository = StorageSpecialCleanupRepository(appContext)
    private val shellBridge = DeviceShellBridge(appContext)

    fun hasUsageAccess(): Boolean = storageRepository.hasUsageAccess()

    fun hasAllFilesAccess(): Boolean = specialCleanupRepository.hasGlobalSharedStorageAccess()

    fun allFilesAccessIntent(): Intent = specialCleanupRepository.globalSharedStorageAccessIntent()

    fun canUseSystemCacheCleanup(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()
    }

    fun systemCacheCleanupIntent(): Intent = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)

    fun loadRanking(forceRefresh: Boolean = false): List<AppCacheUsage> {
        return storageRepository.loadAppCacheRanking(forceRefresh = forceRefresh)
    }

    fun cacheTotal(forceRefresh: Boolean = false): Long? {
        if (!hasUsageAccess()) return null
        return loadRanking(forceRefresh = forceRefresh).sumOf(AppCacheUsage::cacheBytes)
    }

    fun deviceFreeBytes(): Long = storageRepository.loadOverview().freeBytes

    fun shellStatus(forceRefresh: Boolean = false): DeviceShellStatus = shellBridge.probe(forceRefresh)

    fun requestShizukuPermission(): DeviceShellExecResult = shellBridge.requestShizukuPermission()

    fun clearAllAppCachesEnhanced(): StorageAppCacheCleanupResult {
        val command = StorageAppCacheCleanupPolicy.command()
        check(StorageAppCacheCleanupPolicy.isCacheOnlyCommand(command)) {
            "应用缓存清理命令未通过固定策略校验"
        }
        val before = deviceFreeBytes()
        val shell = shellBridge.runEnhancedCommand(
            title = "清理全机应用缓存",
            command = command,
            timeoutMs = 6_000L,
        )
        if (!shell.ok) {
            return StorageAppCacheCleanupResult(
                ok = false,
                message = shell.error.ifBlank { "系统没有完成应用缓存回收请求。" },
                beforeBytes = before,
                afterBytes = null,
                releasedBytes = null,
                shellResult = shell,
            )
        }
        Thread.sleep(1_500L)
        val after = deviceFreeBytes()
        val released = (after - before).coerceAtLeast(0L)
        val message = if (released > 0L) {
            "增强缓存回收已完成，设备可用空间增加了 ${formatCacheBytes(released)}。"
        } else {
            "增强缓存回收命令执行成功，但设备可用空间暂未增加；系统可能没有可回收缓存，或空间统计尚未刷新。"
        }
        return StorageAppCacheCleanupResult(
            ok = true,
            message = message,
            beforeBytes = before,
            afterBytes = after,
            releasedBytes = released,
            shellResult = shell,
        )
    }

    fun usageAccessIntent() = storageRepository.usageAccessIntent()

    private fun formatCacheBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        val digits = if (index == 0 || value >= 100.0) 0 else 1
        return String.format(java.util.Locale.CHINA, "%.${digits}f %s", value, units[index])
    }
}
