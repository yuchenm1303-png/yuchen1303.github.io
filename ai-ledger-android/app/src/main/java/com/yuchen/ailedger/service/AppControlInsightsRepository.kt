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
private val APP_CONTROL_WHITESPACE_REGEX = Regex("\\s+")

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

data class AppMemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val thresholdBytes: Long,
    val usagePercent: Int,
    val lowMemory: Boolean,
    val stateLabel: String,
)

data class AppControlDashboard(
    val totalApps: Int,
    val runningApps: Int,
    val cleanCandidates: Int,
    val storageHeavyApps: Int,
    val lowUseButActiveApps: Int,
    val estimatedRuntimeBytes: Long?,
    val memory: AppMemorySnapshot?,
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
        val memory = memorySnapshot()
        val shellStatus = shellBridge.probe(forceRefresh = false)
        val runtimeByPackage = runtimeSignals(apps, shellStatus)
        val usageAccess = hasUsageStatsAccess()
        val usageByPackage = if (usageAccess) usageStats(now - APP_CONTROL_WEEK_MS, now) else emptyMap()
        val signals = apps.map { app ->
            val runtime = runtimeByPackage[app.packageName]
            val usage = usageByPackage[app.packageName]
            buildSignal(app, runtime, usage, usageAccess, shellStatus, now)
        }.sortedWith(
            compareByDescending<AppOptimizationSignal> { it.score }
                .thenBy { normalize(it.app.label) }
                .thenBy { it.app.packageName },
        )

        var runningApps = 0
        var cleanCandidates = 0
        var storageHeavyApps = 0
        var lowUseButActiveApps = 0
        signals.forEach { signal ->
            if (signal.runtime != null) runningApps += 1
            if (signal.cleanCandidate) cleanCandidates += 1
            if (signal.storageHeavy) storageHeavyApps += 1
            if (signal.lowUseButActive) lowUseButActiveApps += 1
        }

        var estimatedRuntimeBytesValue = 0L
        var hasEstimatedRuntimeBytes = false
        runtimeByPackage.values.forEach { runtime ->
            val bytes = runtime.estimatedMemoryBytes ?: return@forEach
            hasEstimatedRuntimeBytes = true
            estimatedRuntimeBytesValue = if (Long.MAX_VALUE - estimatedRuntimeBytesValue < bytes) {
                Long.MAX_VALUE
            } else {
                estimatedRuntimeBytesValue + bytes
            }
        }
        val dashboard = AppControlDashboard(
            totalApps = apps.size,
            runningApps = runningApps,
            cleanCandidates = cleanCandidates,
            storageHeavyApps = storageHeavyApps,
            lowUseButActiveApps = lowUseButActiveApps,
            estimatedRuntimeBytes = estimatedRuntimeBytesValue.takeIf { hasEstimatedRuntimeBytes },
            memory = memory,
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
        now: Long,
    ): AppOptimizationSignal {
        val lastUsed = usage?.lastTimeUsed ?: 0L
        val foregroundMs = usage?.totalTimeInForeground ?: 0L
        val lastUsedDays = if (lastUsed > 0L) {
            ((now - lastUsed).coerceAtLeast(0L) / APP_CONTROL_DAY_MS).toInt()
        } else {
            Int.MAX_VALUE
        }
        val storageHeavy = app.apkBytes >= 350L * APP_CONTROL_MB
        val lowUse = usageAccess && (foregroundMs < 12L * 60L * 1000L) && lastUsedDays >= 3
        val lowUseButActive = lowUse && runtime != null && !app.isProtected
        val cleanCandidate = runtime != null && !runtime.foregroundLike && !app.isProtected && !app.isSystemApp
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
        }
        val score = buildScore(app, runtime, storageHeavy, lowUseButActive, cleanCandidate)
        val memoryLabel = runtime?.estimatedMemoryBytes?.appControlHumanBytes()
        val recommendation = when {
            app.isProtected -> app.protectionReason.ifBlank { "核心应用已保护，只建议查看信息。" }
            lowUseButActive -> "近期很少使用但仍在后台活跃，建议清后台或限制后台活动。"
            cleanCandidate -> "后台运行中${memoryLabel?.let { "，估算占用 $it" } ?: ""}，可加入智能清后台候选。"
            storageHeavy -> "安装体积偏大，建议查看存储详情或长期不用时卸载。"
            runtime != null -> "正在运行，建议先观察用途，必要时再清后台。"
            else -> "暂无明显异常，可保留常规管理入口。"
        }
        val quickActions = buildList(capacity = 4) {
            if (app.isLaunchable && size < 4) add(ManagedAppAction.Open)
            if (cleanCandidate && size < 4) add(ManagedAppAction.ForceStop)
            if (storageHeavy && size < 4) add(ManagedAppAction.ManageStorage)
            if (size < 4) add(ManagedAppAction.NotificationSettings)
            if (size < 4) add(ManagedAppAction.PermissionSettings)
        }
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
        runtime?.estimatedMemoryBytes?.let { score += ((it / (90L * APP_CONTROL_MB)).coerceAtMost(18L)).toInt() }
        if (cleanCandidate) score += 28
        if (lowUseButActive) score += 28
        if (storageHeavy) score += ((app.apkBytes / (180L * APP_CONTROL_MB)).coerceAtMost(22L)).toInt()
        if (app.isSystemApp) score -= 14
        if (app.isProtected) score -= 60
        if (!app.isLaunchable) score -= 5
        return score.coerceIn(0, 100)
    }

    private fun runtimeSignals(apps: List<ManagedAppSummary>, shellStatus: DeviceShellStatus): Map<String, AppRuntimeSignal> {
        val enhanced = enhancedRuntimeSignals(apps, shellStatus).toMutableMap()
        enhanced.putAll(apiRuntimeSignals())
        return enhanced
    }

    private fun apiRuntimeSignals(): Map<String, AppRuntimeSignal> {
        val manager = activityManager ?: return emptyMap()
        val processes = runCatching { manager.runningAppProcesses.orEmpty() }.getOrDefault(emptyList())
        val grouped = linkedMapOf<String, MutableList<ActivityManager.RunningAppProcessInfo>>()
        processes.forEach { process ->
            var packageAdded = false
            process.pkgList?.forEach { packageName ->
                if (packageName.isBlank()) return@forEach
                packageAdded = true
                grouped.getOrPut(packageName) { mutableListOf() }.add(process)
            }
            if (!packageAdded) {
                val fallback = process.processName.substringBefore(':').trim()
                if (fallback.isNotBlank()) grouped.getOrPut(fallback) { mutableListOf() }.add(process)
            }
        }
        return grouped.mapValues { (packageName, items) ->
            val pids = ArrayList<Int>(items.size)
            val seenPids = HashSet<Int>(items.size)
            val processNames = ArrayList<String>(minOf(items.size, 4))
            val seenNames = HashSet<String>(minOf(items.size, 4))
            var bestImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY
            items.forEach { item ->
                if (item.pid > 0 && seenPids.add(item.pid)) pids += item.pid
                val processName = item.processName.trim()
                if (processName.isNotBlank() && processNames.size < 4 && seenNames.add(processName)) {
                    processNames += processName
                }
                if (item.importance < bestImportance) bestImportance = item.importance
            }
            AppRuntimeSignal(
                packageName = packageName,
                processCount = items.size,
                processNames = processNames,
                importance = bestImportance,
                stateLabel = importanceLabel(bestImportance),
                estimatedMemoryBytes = estimateMemoryBytes(manager, pids),
                foregroundLike = bestImportance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            )
        }
    }

    private fun enhancedRuntimeSignals(apps: List<ManagedAppSummary>, shellStatus: DeviceShellStatus): Map<String, AppRuntimeSignal> {
        if (!shellStatus.isAdbShellLike) return emptyMap()
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "后台进程快照",
            command = "ps -A -o PID,RSS,ARGS 2>/dev/null || ps -A",
            timeoutMs = 1_600L,
        )
        if (!result.ok || result.output.isBlank()) return emptyMap()

        val appPackages = HashSet<String>(apps.size)
        apps.forEach { app -> appPackages += app.packageName }
        val grouped = linkedMapOf<String, MutableList<ShellProcessSample>>()
        result.output.lineSequence().forEach { line ->
            val sample = parseShellProcessLine(line) ?: return@forEach
            val packageName = packageNameForProcess(sample.processName)
            if (packageName !in appPackages) return@forEach
            grouped.getOrPut(packageName) { mutableListOf() }.add(sample)
        }
        if (grouped.isEmpty()) return emptyMap()

        return grouped.mapValues { (packageName, matched) ->
            var rssBytes = 0L
            var hasRss = false
            val processNames = ArrayList<String>(minOf(matched.size, 4))
            val seenNames = HashSet<String>(minOf(matched.size, 4))
            matched.forEach { sample ->
                sample.rssBytes?.let { bytes ->
                    hasRss = true
                    rssBytes = if (Long.MAX_VALUE - rssBytes < bytes) Long.MAX_VALUE else rssBytes + bytes
                }
                val processName = sample.processName.trim()
                if (processName.isNotBlank() && processNames.size < 4 && seenNames.add(processName)) {
                    processNames += processName
                }
            }
            AppRuntimeSignal(
                packageName = packageName,
                processCount = matched.size,
                processNames = processNames,
                importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
                stateLabel = "增强后台",
                estimatedMemoryBytes = rssBytes.takeIf { hasRss },
                foregroundLike = false,
            )
        }
    }

    private fun parseShellProcessLine(line: String): ShellProcessSample? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("PID ", ignoreCase = true) || trimmed.startsWith("USER ", ignoreCase = true)) return null
        val tokens = trimmed.split(APP_CONTROL_WHITESPACE_REGEX)
        if (tokens.size < 3) return null
        tokens[0].toIntOrNull()?.let { pid ->
            val rssBytes = tokens.getOrNull(1)?.toLongOrNull()?.times(1024L)
            val command = tokens.getOrNull(2).orEmpty()
            return ShellProcessSample(pid = pid, rssBytes = rssBytes, processName = command)
        }
        val fallbackPid = tokens.getOrNull(1)?.toIntOrNull() ?: return null
        val fallbackRss = tokens.getOrNull(4)?.toLongOrNull()?.times(1024L)
        val name = tokens.lastOrNull().orEmpty()
        if (name.isBlank() || name.startsWith("[")) return null
        return ShellProcessSample(pid = fallbackPid, rssBytes = fallbackRss, processName = name)
    }

    private fun packageNameForProcess(processName: String): String {
        return processName
            .trim()
            .substringAfterLast('/')
            .substringBefore(' ')
            .substringBefore(':')
    }

    private fun memorySnapshot(): AppMemorySnapshot? {
        val manager = activityManager ?: return null
        return runCatching {
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val total = info.totalMem.coerceAtLeast(0L)
            val available = info.availMem.coerceAtLeast(0L)
            val used = (total - available).coerceAtLeast(0L)
            val percent = if (total > 0L) ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100) else 0
            AppMemorySnapshot(
                totalBytes = total,
                availableBytes = available,
                usedBytes = used,
                thresholdBytes = info.threshold.coerceAtLeast(0L),
                usagePercent = percent,
                lowMemory = info.lowMemory,
                stateLabel = when {
                    info.lowMemory -> "内存紧张"
                    percent >= 88 -> "高负载"
                    percent >= 72 -> "偏高"
                    percent >= 50 -> "稳定"
                    else -> "宽裕"
                },
            )
        }.getOrNull()
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

    private data class ShellProcessSample(
        val pid: Int,
        val rssBytes: Long?,
        val processName: String,
    )
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
