package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

private const val FOLDER_INDEX_PREFS = "storage_resumable_folder_index"
private const val FOLDER_INDEX_STATE_KEY = "folder_index_state"
private const val FOLDER_INDEX_TOP_KEY = "folder_index_top_files"
private const val FOLDER_INDEX_PAGE_FILES = 300
private const val FOLDER_INDEX_MAX_DEPTH = 20
private const val FOLDER_INDEX_TOP_FILES = 120
private const val FOLDER_INDEX_CHECKPOINT_INTERVAL = 10

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
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(FOLDER_INDEX_PREFS, Context.MODE_PRIVATE)
    private val storageRepository = StorageManagementRepository(appContext)
    private val productRepository = StorageProductizationRepository(appContext)

    fun loadState(): StorageFolderIndexState {
        val treeUri = storageRepository.savedTreeUri()
        val permissionValid = treeUri != null && hasPersistedReadPermission(treeUri)
        val saved = readPersistedState()
        val progress = when {
            treeUri == null || !permissionValid -> null
            saved == null || saved.progress.treeUri != treeUri.toString() -> initialize(treeUri).progress
            else -> saved.progress
        }
        return StorageFolderIndexState(
            progress = progress,
            largestFiles = if (progress == null) emptyList() else readTopFiles(),
            deviceGuard = productRepository.readDeviceGuard(),
            permissionValid = permissionValid,
        )
    }

    fun reset(): StorageFolderIndexState {
        prefs.edit().clear().commit()
        val treeUri = storageRepository.savedTreeUri()
        val permissionValid = treeUri != null && hasPersistedReadPermission(treeUri)
        return if (treeUri != null && permissionValid) {
            initialize(treeUri)
        } else {
            StorageFolderIndexState(
                progress = null,
                largestFiles = emptyList(),
                deviceGuard = productRepository.readDeviceGuard(),
                permissionValid = false,
            )
        }
    }

    fun scanNextPage(
        pageFiles: Int = FOLDER_INDEX_PAGE_FILES,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
    ): StorageFolderIndexState {
        val treeUri = storageRepository.savedTreeUri()
            ?: return emptyState("尚未选择授权目录")
        if (!hasPersistedReadPermission(treeUri)) {
            return emptyState("目录授权已失效，请重新选择目录")
        }
        val guard = productRepository.readDeviceGuard()
        if (!guard.heavyWorkAllowed) {
            val currentState = loadState()
            return currentState.copy(deviceGuard = guard, blockedReason = guard.reason)
        }
        var persisted = readPersistedState()
        if (persisted == null || persisted.progress.treeUri != treeUri.toString()) {
            initialize(treeUri)
            persisted = readPersistedState()
        }
        persisted ?: return emptyState("无法初始化目录索引")
        if (persisted.progress.complete) {
            return StorageFolderIndexState(
                progress = persisted.progress,
                largestFiles = readTopFiles(),
                deviceGuard = guard,
                permissionValid = true,
            )
        }

        val queue = ArrayDeque(persisted.queue)
        var current = persisted.current
        var childOffset = persisted.progress.currentChildOffset.coerceAtLeast(0)
        var scannedFiles = persisted.progress.scannedFiles
        var scannedBytes = persisted.progress.scannedBytes
        var discoveredDirectories = persisted.progress.discoveredDirectories
        var completedDirectories = persisted.progress.completedDirectories
        var topFiles = readTopFiles().toMutableList()
        var processedThisPage = 0
        var interrupted = false
        var firstError = persisted.progress.errorMessage
        val boundedPageSize = pageFiles.coerceIn(50, 600)

        while (processedThisPage < boundedPageSize) {
            if (stopSignal.get()) {
                interrupted = true
                break
            }
            if (current == null) {
                current = if (queue.isEmpty()) null else queue.removeFirst()
                childOffset = 0
            }
            val activeNode = current ?: break
            val parentId = runCatching { DocumentsContract.getDocumentId(activeNode.uri) }.getOrNull()
            if (parentId == null) {
                firstError = firstError ?: "部分目录标识已失效，已跳过。"
                current = null
                childOffset = 0
                continue
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = runCatching {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrElse { error ->
                firstError = firstError ?: error.message.orEmpty().ifBlank { "部分目录无法读取，已跳过。" }
                null
            }
            if (cursor == null) {
                current = null
                childOffset = 0
                continue
            }

            var rowIndex = 0
            var fullyConsumed = true
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (it.moveToNext()) {
                    if (rowIndex < childOffset) {
                        rowIndex += 1
                        continue
                    }
                    rowIndex += 1
                    if (stopSignal.get() || processedThisPage >= boundedPageSize) {
                        fullyConsumed = false
                        break
                    }
                    childOffset = rowIndex
                    val documentId = it.safeString(idIndex) ?: continue
                    val name = it.safeString(nameIndex).orEmpty().ifBlank { "未命名文件" }
                    val mime = it.safeString(mimeIndex).orEmpty()
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val path = "${activeNode.path.trimEnd('/')}/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (activeNode.depth < FOLDER_INDEX_MAX_DEPTH) {
                            queue.addLast(FolderNode(documentUri, path, activeNode.depth + 1))
                            discoveredDirectories += 1
                        }
                        continue
                    }
                    val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                    scannedFiles += 1
                    scannedBytes += size
                    processedThisPage += 1
                    topFiles += StorageIndexedLargeFile(
                        uri = documentUri.toString(),
                        displayName = name,
                        sizeBytes = size,
                        mimeType = mime,
                        modifiedAt = it.safeLong(modifiedIndex),
                        location = path,
                    )
                    topFiles = topFiles.sortedByDescending { file -> file.sizeBytes }
                        .take(FOLDER_INDEX_TOP_FILES)
                        .toMutableList()
                    if (processedThisPage % FOLDER_INDEX_CHECKPOINT_INTERVAL == 0) {
                        persist(
                            treeUri = treeUri,
                            rootName = persisted.progress.rootName,
                            queue = queue,
                            current = activeNode,
                            currentChildOffset = childOffset,
                            scannedFiles = scannedFiles,
                            scannedBytes = scannedBytes,
                            discoveredDirectories = discoveredDirectories,
                            completedDirectories = completedDirectories,
                            complete = false,
                            interrupted = false,
                            startedAt = persisted.progress.startedAt,
                            errorMessage = firstError,
                            topFiles = topFiles,
                        )
                    }
                }
            }
            if (fullyConsumed) {
                completedDirectories += 1
                current = null
                childOffset = 0
            } else {
                current = activeNode
                break
            }
        }

        if (stopSignal.get()) interrupted = true
        val complete = current == null && queue.isEmpty()
        val finalProgress = persist(
            treeUri = treeUri,
            rootName = persisted.progress.rootName,
            queue = queue,
            current = current,
            currentChildOffset = childOffset,
            scannedFiles = scannedFiles,
            scannedBytes = scannedBytes,
            discoveredDirectories = discoveredDirectories,
            completedDirectories = completedDirectories,
            complete = complete,
            interrupted = interrupted,
            startedAt = persisted.progress.startedAt,
            errorMessage = firstError,
            topFiles = topFiles,
        )
        return StorageFolderIndexState(
            progress = finalProgress,
            largestFiles = topFiles,
            deviceGuard = guard,
            permissionValid = true,
        )
    }

    private fun initialize(treeUri: Uri): StorageFolderIndexState {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyState("无法读取授权目录标识")
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryName(rootUri).ifBlank { "授权目录" }
        val now = System.currentTimeMillis()
        val progress = persist(
            treeUri = treeUri,
            rootName = rootName,
            queue = ArrayDeque(),
            current = FolderNode(rootUri, rootName, 0),
            currentChildOffset = 0,
            scannedFiles = 0,
            scannedBytes = 0L,
            discoveredDirectories = 1,
            completedDirectories = 0,
            complete = false,
            interrupted = false,
            startedAt = now,
            errorMessage = null,
            topFiles = emptyList(),
        )
        return StorageFolderIndexState(
            progress = progress,
            largestFiles = emptyList(),
            deviceGuard = productRepository.readDeviceGuard(),
            permissionValid = true,
        )
    }

    private fun persist(
        treeUri: Uri,
        rootName: String,
        queue: ArrayDeque<FolderNode>,
        current: FolderNode?,
        currentChildOffset: Int,
        scannedFiles: Int,
        scannedBytes: Long,
        discoveredDirectories: Int,
        completedDirectories: Int,
        complete: Boolean,
        interrupted: Boolean,
        startedAt: Long,
        errorMessage: String?,
        topFiles: List<StorageIndexedLargeFile>,
    ): StorageFolderIndexProgress {
        val now = System.currentTimeMillis()
        val progress = StorageFolderIndexProgress(
            treeUri = treeUri.toString(),
            rootName = rootName,
            scannedFiles = scannedFiles,
            scannedBytes = scannedBytes,
            discoveredDirectories = discoveredDirectories,
            completedDirectories = completedDirectories,
            queuedDirectories = queue.size + if (current != null) 1 else 0,
            currentPath = current?.path,
            currentChildOffset = currentChildOffset,
            startedAt = startedAt,
            updatedAt = now,
            complete = complete,
            interrupted = interrupted,
            errorMessage = errorMessage,
        )
        val stateJson = JSONObject()
            .put("progress", progress.toJson())
            .put("current", current?.toJson() ?: JSONObject.NULL)
            .put("queue", JSONArray().apply { queue.forEach { node -> put(node.toJson()) } })
        val topJson = JSONArray().apply { topFiles.forEach { file -> put(file.toJson()) } }
        prefs.edit()
            .putString(FOLDER_INDEX_STATE_KEY, stateJson.toString())
            .putString(FOLDER_INDEX_TOP_KEY, topJson.toString())
            .commit()
        return progress
    }

    private fun readPersistedState(): PersistedFolderState? {
        val raw = prefs.getString(FOLDER_INDEX_STATE_KEY, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val progress = root.getJSONObject("progress").toProgress()
            val current = root.optJSONObject("current")?.toNode()
            val queueJson = root.optJSONArray("queue") ?: JSONArray()
            val queue = buildList {
                for (index in 0 until queueJson.length()) {
                    queueJson.optJSONObject(index)?.toNode()?.let(::add)
                }
            }
            PersistedFolderState(progress, current, queue)
        }.getOrNull()
    }

    private fun readTopFiles(): List<StorageIndexedLargeFile> {
        val raw = prefs.getString(FOLDER_INDEX_TOP_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageIndexedLargeFile(
                            uri = item.optString("uri"),
                            displayName = item.optString("displayName"),
                            sizeBytes = item.optLong("sizeBytes"),
                            mimeType = item.optString("mimeType"),
                            modifiedAt = item.optLong("modifiedAt"),
                            location = item.optString("location"),
                        ),
                    )
                }
            }.sortedByDescending { it.sizeBytes }
        }.getOrDefault(emptyList())
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
                if (cursor.moveToFirst()) cursor.safeString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun emptyState(reason: String): StorageFolderIndexState {
        return StorageFolderIndexState(
            progress = null,
            largestFiles = emptyList(),
            deviceGuard = productRepository.readDeviceGuard(),
            permissionValid = false,
            blockedReason = reason,
        )
    }

    private fun android.database.Cursor.safeString(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return runCatching { getString(index) }.getOrNull()
    }

    private fun android.database.Cursor.safeLong(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return runCatching { getLong(index) }.getOrDefault(0L)
    }

    private fun FolderNode.toJson(): JSONObject = JSONObject()
        .put("uri", uri.toString())
        .put("path", path)
        .put("depth", depth)

    private fun JSONObject.toNode(): FolderNode? {
        val uri = optString("uri").takeIf(String::isNotBlank)?.let(Uri::parse) ?: return null
        return FolderNode(uri, optString("path"), optInt("depth"))
    }

    private fun StorageFolderIndexProgress.toJson(): JSONObject = JSONObject()
        .put("treeUri", treeUri)
        .put("rootName", rootName)
        .put("scannedFiles", scannedFiles)
        .put("scannedBytes", scannedBytes)
        .put("discoveredDirectories", discoveredDirectories)
        .put("completedDirectories", completedDirectories)
        .put("queuedDirectories", queuedDirectories)
        .put("currentPath", currentPath ?: JSONObject.NULL)
        .put("currentChildOffset", currentChildOffset)
        .put("startedAt", startedAt)
        .put("updatedAt", updatedAt)
        .put("complete", complete)
        .put("interrupted", interrupted)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)

    private fun JSONObject.toProgress(): StorageFolderIndexProgress = StorageFolderIndexProgress(
        treeUri = optString("treeUri"),
        rootName = optString("rootName"),
        scannedFiles = optInt("scannedFiles"),
        scannedBytes = optLong("scannedBytes"),
        discoveredDirectories = optInt("discoveredDirectories"),
        completedDirectories = optInt("completedDirectories"),
        queuedDirectories = optInt("queuedDirectories"),
        currentPath = optString("currentPath").takeIf { it.isNotBlank() && it != "null" },
        currentChildOffset = optInt("currentChildOffset"),
        startedAt = optLong("startedAt"),
        updatedAt = optLong("updatedAt"),
        complete = optBoolean("complete"),
        interrupted = optBoolean("interrupted"),
        errorMessage = optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
    )

    private fun StorageIndexedLargeFile.toJson(): JSONObject = JSONObject()
        .put("uri", uri)
        .put("displayName", displayName)
        .put("sizeBytes", sizeBytes)
        .put("mimeType", mimeType)
        .put("modifiedAt", modifiedAt)
        .put("location", location)

    private data class FolderNode(
        val uri: Uri,
        val path: String,
        val depth: Int,
    )

    private data class PersistedFolderState(
        val progress: StorageFolderIndexProgress,
        val current: FolderNode?,
        val queue: List<FolderNode>,
    )
}
