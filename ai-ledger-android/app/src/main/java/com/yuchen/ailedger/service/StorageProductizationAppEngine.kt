package com.yuchen.ailedger.service

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

internal class StorageProductizationAppEngine(context: Context) {
    private val appContext = context.applicationContext
    private val appRepository = AppManagementRepository(appContext)
    private val store = StorageAppAnalysisStore(appContext)
    private val analyzer = StorageAppAnalyzer(appContext)

    fun loadState(guard: StorageDeviceGuard): StorageAppAnalysisState {
        val candidates = candidates()
        val progress = validProgress(candidates)
        return state(
            progress = progress,
            items = store.readResults(),
            usageGranted = analyzer.hasUsageAccess(),
            guard = guard,
        )
    }

    fun reset(guard: StorageDeviceGuard): StorageAppAnalysisState {
        val candidates = candidates()
        val progress = resetProgress(candidates, signature(candidates))
        return state(progress, emptyList(), analyzer.hasUsageAccess(), guard)
    }

    fun analyze(
        guard: StorageDeviceGuard,
        pageSize: Int,
        stopSignal: AtomicBoolean,
    ): StorageAppAnalysisState {
        val candidates = candidates()
        var progress = validProgress(candidates)
        val results = store.readResults().associateByTo(linkedMapOf()) { it.packageName }
        val usageGranted = analyzer.hasUsageAccess()
        if (progress.complete) return state(progress, results.values, usageGranted, guard)
        val requested = if (pageSize <= 0 || pageSize == Int.MAX_VALUE) candidates.size else pageSize
        val end = (progress.processedCount.toLong() + requested.toLong())
            .coerceAtMost(candidates.size.toLong()).toInt()
        val usage = if (usageGranted) usageStats() else emptyMap()
        var processed = progress.processedCount
        var interrupted = false
        while (processed < end) {
            if (stopSignal.get()) {
                interrupted = true
                break
            }
            val app = candidates[processed]
            analyzer.analyze(app, usageGranted, usage[app.packageName]?.lastTimeUsed)?.let {
                results[it.packageName] = it
            }
            processed += 1
            progress = progress.copy(
                processedCount = processed,
                updatedAt = System.currentTimeMillis(),
                complete = processed >= candidates.size,
                interrupted = false,
            )
            store.write(progress, results.values.toList())
        }
        if (interrupted) {
            progress = progress.copy(updatedAt = System.currentTimeMillis(), interrupted = true)
            store.write(progress, results.values.toList())
        }
        return state(progress, results.values, usageGranted, guard)
    }

    private fun validProgress(candidates: List<ManagedAppSummary>): StorageAppScanProgress {
        val expected = signature(candidates)
        val saved = store.readProgress()
        return if (saved == null || saved.packageSignature != expected || saved.totalCount != candidates.size) {
            resetProgress(candidates, expected)
        } else saved
    }

    private fun resetProgress(
        candidates: List<ManagedAppSummary>,
        signature: String,
    ): StorageAppScanProgress {
        val now = System.currentTimeMillis()
        return StorageAppScanProgress(
            packageSignature = signature,
            processedCount = 0,
            totalCount = candidates.size,
            startedAt = now,
            updatedAt = now,
            complete = candidates.isEmpty(),
            interrupted = false,
        ).also { store.write(it, emptyList()) }
    }

    private fun state(
        progress: StorageAppScanProgress,
        items: Collection<StorageAppOptimizationItem>,
        usageGranted: Boolean,
        guard: StorageDeviceGuard,
    ) = StorageAppAnalysisState(
        progress = progress,
        items = items.sortedByDescending { it.totalBytes ?: it.apkBytes },
        usageAccessGranted = usageGranted,
        deviceGuard = guard,
    )

    private fun candidates(): List<ManagedAppSummary> = appRepository.loadApps()
        .asSequence()
        .filter { !it.isSystemApp && it.isLaunchable && it.isEnabled }
        .sortedBy { it.packageName }
        .toList()

    private fun signature(candidates: List<ManagedAppSummary>): String =
        candidates.joinToString("|") { "${it.packageName}:${it.uid}" }.hashCode().toString()

    private fun usageStats(): Map<String, android.app.usage.UsageStats> {
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        return runCatching { manager.queryAndAggregateUsageStats(0L, System.currentTimeMillis()) }.getOrDefault(emptyMap())
    }
}
