package com.yuchen.ailedger.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Locale
import kotlin.math.roundToInt

private const val APP_CONTROL_DAY_MS = 24L * 60L * 60L * 1000L
private const val APP_CONTROL_WEEK_MS = 7L * APP_CONTROL_DAY_MS
private const val APP_CONTROL_MB = 1024L * 1024L

data class AppRuntimeSignal(
    val packageName: String,
    val processCount: Int,
    val processNames: List<String>,
    val importance: Int,
    val stateLabel: String,
    val estimatedMemoryBytes: Long?,
    val foregroundLike: Boolean,
)

data class AppOptimizationSignal(
    val app: ManagedAppSummary,
    val runtime: AppRuntimeSignal?,
    val lastUsedTime: Long,
    val totalForegroundMs: Long,
    val score: Int,
    val tags: List<String>,
    val recommendation: String,
    val quickActions: List<ManagedAppAction>,
    val cleanCandidate: Boolean,
    val storageHeavy: Boolean,
    val lowUseButActive: Boolean,
)

data class AppControlDashboard(
    val totalApps: Int,
    val runningApps: Int,
    val cleanCandidates: Int,
    val storageHeavyApps: Int,
    val lowUseButActiveApps: Int,
    val estimatedRuntimeBytes: Long?,
    val usageAccessGranted: Boolean,
    val enhancedControlAvailable: Boolean,
    val shellMessage: String,
    val generatedAt: Long,
)

data class AppControlInsights(
    val dashboard: AppControlDashboard,
    val signals: List<AppOptimizationSignal>,
    val byPackage: Map<String, AppOptimizationSignal>,
)

class AppControlInsightsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val usageManager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val shellBridge = DeviceShellBridge(appContext)

    fun loadInsights(apps: List<ManagedAppSummary>): AppControlInsights {
        val now = System.currentTimeMillis()
        val runtimeByPackage = runtimeSignals()
        val usageAccess = hasUsageStatsAccess()
        val usageByPackage = if (usageAccess) usageStats(now - APP_CONTROL_WEEK_MS, now) else emptyMap()
        val shellStatus = shellBridge.probe(forceRefresh = false)
        val signals = apps.map { app ->
            val runtime = runtimeByPackage[app.packageName]
            val usage = usageByPackage[app.packageName]
            buildSignal(app, runtime, usage, usageAccess, shellStatus)
        }.sortedWith(
            compareByDescending<AppOptimizationSignal> { it.score }
                .thenBy { normalize(it.app.label) }
                .thenBy { it.app.packageName },
        )
        val estimatedRuntimeBytes = runtimeByPackage.values
            .mapNotNull { it.estimatedMemoryBytes }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val dashboard = AppControlDashboard(
            totalApps = apps.size,
            runningApps = signals.count { it.runtime != null },
            cleanCandidates = signals.count { it.cleanCandidate },
            storageHeavyApps = signals.count { it.storageHeavy },
            lowUseButActiveApps = signals.count { it.lowUseButActive },
            estimatedRuntimeBytes = estimatedRuntimeBytes,
            usageAccessGranted = usageAccess,
            enhancedControlAvailable = shellStatus.isAdbShellLike || shellStatus.shizukuGranted,
            shellMessage = shellStatus.message,
            generatedAt = now,
        )
        return AppControlInsights(
            dashboard = dashboard,
            signals = signals,
            byPackage = signals.associateBy { it.app.packageName },
        )
    }

    fun hasUsageStatsAccess(): Boolean {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun buildSignal(
        app: ManagedAppSummary,
        runtime: AppRuntimeSignal?,
        usage: UsageStats?,
        usageAccess: Boolean,
        shellStatus: DeviceShellStatus,
    ): AppOptimizationSignal {
        val lastUsed = usage?.lastTimeUsed ?: 0L
        val foregroundMs = usage?.totalTimeInForeground ?: 0L
        val lastUsedDays = if (lastUsed > 0L) {
            ((System.currentTimeMillis() - lastUsed).coerceAtLeast(0L) / APP_CONTROL_DAY_MS).toInt()
        } else {
            Int.MAX_VALUE
        }
        val storageHeavy = app.apkBytes >= 350L * APP_CONTROL_MB
        val lowUse = usageAccess && (foregroundMs < 12L * 60L * 1000L) && lastUsedDays >= 3
        val lowUseButActive = lowUse && runtime != null && !app.isProtected
        val cleanCandidate = runtime != null && !runtime.foregroundLike && !app.isProtected
        val tags = buildList {
            if (runtime != null) add(runtime.stateLabel)
            if (cleanCandidate) add("可清后台")
            if (lowUseButActive) add("低频但活跃")
            if (storageHeavy) add("占空间")
            if (app.isSystemApp) add("系统应用") else add("用户应用")
            if (!app.isLaunchable) add("无桌面入口")
            if (app.isProtected) add("受保护")
            if (!usageAccess) add("待开启使用情况访问")
            if (shellStatus.shizukuGranted) add("增强可控")
        }.distinct()
        val score = buildScore(app, runtime, storageHeavy, lowUseButActive, cleanCandidate)
        val recommendation = when {
            app.isProtected -> app.protectionReason.ifBlank { "核心应用已保护，只建议查看信息。" }
            lowUseButActive -> "近期很少使用但仍在后台活跃，建议清后台或限制后台活动。"
            cleanCandidate -> "当前在后台运行，可加入智能清后台候选。"
            storageHeavy -> "安装体积偏大，建议查看存储详情或长期不用时卸载。"
            runtime != null -> "正在运行，建议先观察用途，必要时再清后台。"
            else -> "暂无明显异常，可保留常规管理入口。"
        }
        val quickActions = buildList {
            if (app.isLaunchable) add(ManagedAppAction.Open)
            if (cleanCandidate) add(ManagedAppAction.ForceStop)
            if (storageHeavy) add(ManagedAppAction.ManageStorage)
            add(ManagedAppAction.NotificationSettings)
            add(ManagedAppAction.PermissionSettings)
        }.distinct().take(4)
        return AppOptimizationSignal(
            app = app,
            runtime = runtime,
            lastUsedTime = lastUsed,
            totalForegroundMs = foregroundMs,
            score = score,
            tags = tags,
            recommendation = recommendation,
            quickActions = quickActions,
            cleanCandidate = cleanCandidate,
            storageHeavy = storageHeavy,
            lowUseButActive = lowUseButActive,
        )
    }

    private fun buildScore(
        app: ManagedAppSummary,
        runtime: AppRuntimeSignal?,
        storageHeavy: Boolean,
        lowUseButActive: Boolean,
        cleanCandidate: Boolean,
    ): Int {
        var score = 0
        if (runtime != null) score += 26
        if (cleanCandidate) score += 28
        if (lowUseButActive) score += 28
        if (storageHeavy) score += ((app.apkBytes / (180L * APP_CONTROL_MB)).coerceAtMost(22L)).toInt()
        if (app.isSystemApp) score -= 14
        if (app.isProtected) score -= 60
        if (!app.isLaunchable) score -= 5
        return score.coerceIn(0, 100)
    }

    private fun runtimeSignals(): Map<String, AppRuntimeSignal> {
        val manager = activityManager ?: return emptyMap()
        val processes = runCatching { manager.runningAppProcesses.orEmpty() }.getOrDefault(emptyList())
        val grouped = linkedMapOf<String, MutableList<ActivityManager.RunningAppProcessInfo>>()
        processes.forEach { process ->
            val packages = process.pkgList?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: listOf(process.processName.substringBefore(':'))
            packages.forEach { packageName ->
                grouped.getOrPut(packageName) { mutableListOf() }.add(process)
            }
        }
        return grouped.mapValues { (packageName, items) ->
            val pids = items.map { it.pid }.filter { it > 0 }.distinct()
            val memory = estimateMemoryBytes(manager, pids)
            val bestImportance = items.minOfOrNull { it.importance } ?: ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY
            AppRuntimeSignal(
                packageName = packageName,
                processCount = items.size,
                processNames = items.map { it.processName }.filter { it.isNotBlank() }.distinct().take(4),
                importance = bestImportance,
                stateLabel = importanceLabel(bestImportance),
                estimatedMemoryBytes = memory,
                foregroundLike = bestImportance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            )
        }
    }

    private fun estimateMemoryBytes(manager: ActivityManager, pids: List<Int>): Long? {
        if (pids.isEmpty()) return null
        return runCatching {
            val infos = manager.getProcessMemoryInfo(pids.toIntArray())
            infos.sumOf { info -> info.totalPss.toLong().coerceAtLeast(0L) * 1024L }
                .takeIf { it > 0L }
        }.getOrNull()
    }

    private fun usageStats(startMs: Long, endMs: Long): Map<String, UsageStats> {
        val manager = usageManager ?: return emptyMap()
        return runCatching {
            manager.queryAndAggregateUsageStats(startMs, endMs).orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun importanceLabel(importance: Int): String = when {
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "前台"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "可见"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "感知运行"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "后台服务"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "缓存进程"
        else -> "后台"
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}

fun Long.appControlHumanBytes(): String {
    val mb = this.toDouble() / APP_CONTROL_MB.toDouble()
    return when {
        mb >= 1024.0 -> String.format(Locale.ROOT, "%.1fGB", mb / 1024.0)
        mb >= 100.0 -> "${mb.roundToInt()}MB"
        mb >= 10.0 -> String.format(Locale.ROOT, "%.1fMB", mb)
        else -> "${mb.roundToInt().coerceAtLeast(1)}MB"
    }
}

fun Long.appControlUsageLabel(now: Long = System.currentTimeMillis()): String {
    if (this <= 0L) return "暂无记录"
    val days = ((now - this).coerceAtLeast(0L) / APP_CONTROL_DAY_MS).toInt()
    return when {
        days <= 0 -> "今天用过"
        days == 1 -> "昨天用过"
        days < 7 -> "${days} 天前用过"
        else -> "超过 7 天未用"
    }
}

fun Long.appControlDurationLabel(): String {
    val minutes = this / 60_000L
    return when {
        minutes <= 0L -> "不足 1 分钟"
        minutes < 60L -> "${minutes} 分钟"
        else -> String.format(Locale.ROOT, "%.1f 小时", minutes / 60.0)
    }
}
