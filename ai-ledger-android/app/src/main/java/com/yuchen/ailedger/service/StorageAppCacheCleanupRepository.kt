package com.yuchen.ailedger.service

import android.content.Context

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
    private val shellBridge = DeviceShellBridge(appContext)

    fun hasUsageAccess(): Boolean = storageRepository.hasUsageAccess()

    fun loadRanking(forceRefresh: Boolean = false): List<AppCacheUsage> {
        return storageRepository.loadAppCacheRanking(forceRefresh = forceRefresh)
    }

    fun shellStatus(forceRefresh: Boolean = false): DeviceShellStatus = shellBridge.probe(forceRefresh)

    fun requestShizukuPermission(): DeviceShellExecResult = shellBridge.requestShizukuPermission()

    fun clearAllAppCaches(): StorageAppCacheCleanupResult {
        val command = StorageAppCacheCleanupPolicy.command()
        check(StorageAppCacheCleanupPolicy.isCacheOnlyCommand(command)) {
            "应用缓存清理命令未通过固定策略校验"
        }
        val before = loadRanking(forceRefresh = true).takeIf { hasUsageAccess() }?.sumOf(AppCacheUsage::cacheBytes)
        val shell = shellBridge.runEnhancedCommand(
            title = "清理全机应用缓存",
            command = command,
            timeoutMs = 30_000L,
        )
        if (!shell.ok) {
            return StorageAppCacheCleanupResult(
                ok = false,
                message = shell.error.ifBlank { "系统没有完成应用缓存清理请求。" },
                beforeBytes = before,
                afterBytes = null,
                releasedBytes = null,
                shellResult = shell,
            )
        }
        Thread.sleep(450L)
        val after = loadRanking(forceRefresh = true).takeIf { hasUsageAccess() }?.sumOf(AppCacheUsage::cacheBytes)
        val released = if (before != null && after != null) (before - after).coerceAtLeast(0L) else null
        val message = when {
            released == null -> "系统已执行全机缓存回收请求；未授权使用情况访问，因此无法读取清理前后缓存总量。"
            released > 0L -> "系统已完成缓存回收，统计到的应用缓存减少了 ${formatCacheBytes(released)}。"
            else -> "系统已执行缓存回收请求，但当前统计没有发现可释放缓存；部分应用可能没有缓存或系统选择保留正在使用的缓存。"
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
