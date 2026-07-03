package com.yuchen.ailedger.service

import java.time.LocalDate

data class StorageCleanupTrendPoint(
    val day: LocalDate,
    val releasedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
)

data class StorageCapacitySnapshot(
    val createdAt: Long,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

data class StorageProductDashboard(
    val overview: DeviceStorageOverview,
    val deviceGuard: StorageDeviceGuard,
    val permissions: StoragePermissionHealth,
    val compatibility: StorageCompatibilityReport,
    val cleanupHistory: List<StorageCleanupHistoryEntry>,
    val cleanupTrend: List<StorageCleanupTrendPoint>,
    val capacitySnapshots: List<StorageCapacitySnapshot>,
    val appAnalysis: StorageAppAnalysisState,
)
