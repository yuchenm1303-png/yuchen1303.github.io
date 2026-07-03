package com.yuchen.ailedger.service

import java.time.LocalDate

internal const val STORAGE_APP_UNUSED_DAYS = 90

data class StorageDeviceGuardData(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalStatus: Int?,
    val thermalLabel: String,
    val heavyWorkAllowed: Boolean,
    val reason: String,
)

data class StoragePermissionHealthData(
    val usageAccessGranted: Boolean,
    val imageAccessGranted: Boolean,
    val videoAccessGranted: Boolean,
    val audioAccessGranted: Boolean,
    val selectedPhotoAccessOnly: Boolean,
    val authorizedFolderPresent: Boolean,
    val authorizedFolderPermissionValid: Boolean,
) {
    val mediaAccessGranted: Boolean
        get() = imageAccessGranted || videoAccessGranted || audioAccessGranted || selectedPhotoAccessOnly

    val healthy: Boolean
        get() = usageAccessGranted && mediaAccessGranted &&
            (!authorizedFolderPresent || authorizedFolderPermissionValid)
}

data class StorageCompatibilityReportData(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdk: Int,
    val title: String,
    val guidance: List<String>,
)

data class StorageAppOptimizationItemData(
    val label: String,
    val packageName: String,
    val apkBytes: Long,
    val appBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val totalBytes: Long?,
    val firstInstallTime: Long,
    val lastUsedAt: Long?,
    val unusedDays: Int?,
    val isProtected: Boolean,
    val suggestionReason: String,
) {
    val longUnusedCandidate: Boolean
        get() = unusedDays != null && unusedDays >= STORAGE_APP_UNUSED_DAYS && !isProtected
}

data class StorageAppScanProgressData(
    val packageSignature: String,
    val processedCount: Int,
    val totalCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val complete: Boolean,
    val interrupted: Boolean,
) {
    val fraction: Float
        get() = if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else 0f
}

data class StorageAppAnalysisStateData(
    val progress: StorageAppScanProgressData,
    val items: List<StorageAppOptimizationItemData>,
    val usageAccessGranted: Boolean,
    val deviceGuard: StorageDeviceGuardData,
    val blockedReason: String? = null,
) {
    val longUnusedApps: List<StorageAppOptimizationItemData>
        get() = items.filter(StorageAppOptimizationItemData::longUnusedCandidate)
            .sortedWith(
                compareByDescending<StorageAppOptimizationItemData> { it.unusedDays ?: 0 }
                    .thenByDescending { it.totalBytes ?: it.apkBytes },
            )

    val largestApps: List<StorageAppOptimizationItemData>
        get() = items.sortedByDescending { it.totalBytes ?: it.apkBytes }
}

data class StorageCleanupTrendPointData(
    val day: LocalDate,
    val releasedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
)

data class StorageCapacitySnapshotData(
    val createdAt: Long,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

data class StorageProductDashboardData(
    val overview: DeviceStorageOverview,
    val deviceGuard: StorageDeviceGuardData,
    val permissions: StoragePermissionHealthData,
    val compatibility: StorageCompatibilityReportData,
    val cleanupHistory: List<StorageCleanupHistoryEntry>,
    val cleanupTrend: List<StorageCleanupTrendPointData>,
    val capacitySnapshots: List<StorageCapacitySnapshotData>,
    val appAnalysis: StorageAppAnalysisStateData,
)
