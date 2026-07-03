package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.concurrent.atomic.AtomicBoolean

private const val COMPLETE_FOLDER_PAGE_FILES = 300

class StorageResumableFolderCompleteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val storageRepository = StorageManagementRepository(appContext)
    private val productRepository = StorageProductizationRepository(appContext)
    private val store = StorageFolderIndexCompleteStore(appContext)
    private val engine = StorageFolderIndexCompleteEngine(appContext, store)

    fun loadState(): StorageFolderIndexState {
        val treeUri = storageRepository.savedTreeUri()
        val guard = productRepository.readDeviceGuard()
        if (treeUri == null) return emptyState("尚未选择授权目录", guard)
        if (!hasPersistedReadPermission(treeUri)) return emptyState("目录授权已失效，请重新选择目录", guard)
        val work = validWork(treeUri) ?: return emptyState("无法初始化目录索引", guard)
        return StorageFolderIndexState(
            progress = work.progress,
            largestFiles = work.files,
            deviceGuard = guard,
            permissionValid = true,
        )
    }

    fun reset(): StorageFolderIndexState {
        store.clear()
        return loadState()
    }

    fun scanNextPage(
        pageFiles: Int = COMPLETE_FOLDER_PAGE_FILES,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
    ): StorageFolderIndexState {
        val treeUri = storageRepository.savedTreeUri()
        val guard = productRepository.readDeviceGuard()
        if (treeUri == null) return emptyState("尚未选择授权目录", guard)
        if (!hasPersistedReadPermission(treeUri)) return emptyState("目录授权已失效，请重新选择目录", guard)
        val work = validWork(treeUri) ?: return emptyState("无法初始化目录索引", guard)
        if (work.progress.complete) {
            return StorageFolderIndexState(
                progress = work.progress,
                largestFiles = work.files,
                deviceGuard = guard,
                permissionValid = true,
            )
        }
        return engine.scan(
            treeUri = treeUri,
            work = work,
            pageFiles = pageFiles.coerceAtLeast(1),
            stopSignal = stopSignal,
            guard = guard,
        )
    }

    private fun validWork(treeUri: Uri): CompleteFolderWork? {
        val saved = store.load()
        return if (saved == null || saved.progress.treeUri != treeUri.toString()) {
            store.clear()
            initialize(treeUri)
        } else {
            saved
        }
    }

    private fun initialize(treeUri: Uri): CompleteFolderWork? {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryName(rootUri).ifBlank { "授权目录" }
        val now = System.currentTimeMillis()
        store.save(
            treeUri = treeUri,
            rootName = rootName,
            queue = emptyList(),
            current = CompleteFolderNode(rootUri, rootName, 0),
            childOffset = 0,
            scannedFiles = 0,
            scannedBytes = 0L,
            discoveredDirectories = 1,
            completedDirectories = 0,
            complete = false,
            interrupted = false,
            startedAt = now,
            errorMessage = null,
            files = emptyList(),
        )
        return store.load()
    }

    private fun hasPersistedReadPermission(treeUri: Uri): Boolean {
        return resolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
    }

    private fun queryName(uri: Uri): String {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun emptyState(reason: String, guard: StorageDeviceGuard): StorageFolderIndexState {
        return StorageFolderIndexState(
            progress = null,
            largestFiles = emptyList(),
            deviceGuard = guard,
            permissionValid = false,
            blockedReason = reason,
        )
    }
}
