package com.yuchen.ailedger.service

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class StorageProductizationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val storageRepository = StorageManagementRepository(appContext)
    private val historyStore = StorageCleanupHistoryStore(appContext)
    private val capacityStore = StorageCapacitySnapshotStore(appContext)
    private val deviceInfo = StorageProductizationDeviceInfo(appContext, storageRepository)
    private val appEngine = StorageProductizationAppEngine(appContext)

    fun loadDashboard(): StorageProductDashboard {
        val overview = storageRepository.loadOverview()
        capacityStore.append(overview)
        val history = historyStore.load()
        val guard = readDeviceGuard()
        return StorageProductDashboard(
            overview = overview,
            deviceGuard = guard,
            permissions = readPermissionHealth(),
            compatibility = compatibilityReport(),
            cleanupHistory = history,
            cleanupTrend = cleanupTrend(history),
            capacitySnapshots = capacityStore.load(),
            appAnalysis = appEngine.loadState(guard),
        )
    }

    fun loadAppAnalysisState(): StorageAppAnalysisState = appEngine.loadState(readDeviceGuard())
    fun resetAppAnalysis(): StorageAppAnalysisState = appEngine.reset(readDeviceGuard())

    fun analyzeNextAppPage(
        pageSize: Int = Int.MAX_VALUE,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
    ): StorageAppAnalysisState = appEngine.analyze(readDeviceGuard(), pageSize, stopSignal)

    fun readDeviceGuard(): StorageDeviceGuard = deviceInfo.guard()
    fun readPermissionHealth(): StoragePermissionHealth = deviceInfo.permissions()
    fun compatibilityReport(): StorageCompatibilityReport = deviceInfo.compatibility()

    private fun cleanupTrend(history: List<StorageCleanupHistoryEntry>): List<StorageCleanupTrendPoint> {
        val zone = ZoneId.systemDefault()
        return history.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .map { (day, entries) ->
                StorageCleanupTrendPoint(
                    day = day,
                    releasedBytes = entries.sumOf { it.releasedBytes },
                    deletedCount = entries.sumOf { it.deletedCount },
                    failedCount = entries.sumOf { it.failedCount },
                )
            }
            .sortedBy { it.day }
    }
}
