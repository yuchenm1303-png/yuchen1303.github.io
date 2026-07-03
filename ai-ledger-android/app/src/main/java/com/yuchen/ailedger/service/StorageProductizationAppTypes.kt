package com.yuchen.ailedger.service

internal const val STORAGE_APP_UNUSED_DAYS = 90

data class StorageAppOptimizationItem(
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

data class StorageAppScanProgress(
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

data class StorageAppAnalysisState(
    val progress: StorageAppScanProgress,
    val items: List<StorageAppOptimizationItem>,
    val usageAccessGranted: Boolean,
    val deviceGuard: StorageDeviceGuard,
    val blockedReason: String? = null,
) {
    val longUnusedApps: List<StorageAppOptimizationItem>
        get() = items.filter(StorageAppOptimizationItem::longUnusedCandidate)
            .sortedWith(
                compareByDescending<StorageAppOptimizationItem> { it.unusedDays ?: 0 }
                    .thenByDescending { it.totalBytes ?: it.apkBytes },
            )

    val largestApps: List<StorageAppOptimizationItem>
        get() = items.sortedByDescending { it.totalBytes ?: it.apkBytes }
}
