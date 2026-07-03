package com.yuchen.ailedger.service

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

data class StorageIndexedLargeFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
)

data class StorageFolderIndexProgress(
    val treeUri: String,
    val rootName: String,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val discoveredDirectories: Int,
    val completedDirectories: Int,
    val queuedDirectories: Int,
    val currentPath: String?,
    val currentChildOffset: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val complete: Boolean,
    val interrupted: Boolean,
    val errorMessage: String? = null,
)

data class StorageFolderIndexState(
    val progress: StorageFolderIndexProgress?,
    val largestFiles: List<StorageIndexedLargeFile>,
    val deviceGuard: StorageDeviceGuard,
    val permissionValid: Boolean,
    val blockedReason: String? = null,
)

class StorageResumableFolderRepository(context: Context) {
    private val completeRepository = StorageResumableFolderCompleteRepository(context.applicationContext)

    fun loadState(): StorageFolderIndexState = completeRepository.loadState()

    fun reset(): StorageFolderIndexState = completeRepository.reset()

    fun scanNextPage(
        pageFiles: Int = 300,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
    ): StorageFolderIndexState {
        return completeRepository.scanNextPage(
            pageFiles = pageFiles,
            stopSignal = stopSignal,
        )
    }
}
