package com.yuchen.ailedger.service

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.ArrayDeque
import java.util.Locale

private const val SPECIAL_CLEANUP_PREFS = "storage_special_cleanup"
private const val DOWNLOAD_TREE_KEY = "download_tree_uri"
private const val JUNK_TREE_KEY = "junk_tree_uri"
private const val SPECIAL_MB = 1024L * 1024L
private const val DAY_MS = 24L * 60L * 60L * 1000L

enum class StorageSpecialCleanupMode {
    Downloads,
    Junk,
}

enum class StorageSpecialCleanupRisk(val label: String) {
    Low("低风险"),
    Review("需检查"),
}

enum class StorageSpecialCleanupKind(
    val label: String,
    val explanation: String,
    val risk: StorageSpecialCleanupRisk,
) {
    Installer("安装包", "安装完成后通常可以删除，但请确认不再需要离线安装。", StorageSpecialCleanupRisk.Review),
    Archive("压缩包", "压缩文件可能包含唯一资料，删除前请确认内容已有其他副本。", StorageSpecialCleanupRisk.Review),
    PartialDownload("下载残留", "名称符合未完成下载特征，并且已经长时间未修改。", StorageSpecialCleanupRisk.Low),
    OldDownload("长期未整理", "下载文件已经超过 90 天未修改，仅作为人工整理建议。", StorageSpecialCleanupRisk.Review),
    LargeDownload("大型下载", "文件超过 100 MB，删除前请确认不再需要。", StorageSpecialCleanupRisk.Review),
    OtherDownload("其他下载", "普通下载文件，不会自动判断为垃圾。", StorageSpecialCleanupRisk.Review),
    Temporary("临时文件", "文件名或扩展名符合临时文件特征，并且已经超过 7 天未修改。", StorageSpecialCleanupRisk.Low),
    Log("旧日志", "日志文件已经超过 30 天未修改，仍需确认对应应用不再使用。", StorageSpecialCleanupRisk.Review),
    Backup("旧备份", "备份文件已经超过 90 天未修改，删除前请确认没有唯一数据。", StorageSpecialCleanupRisk.Review),
    ZeroByte("零字节文件", "文件大小为 0，并且已经超过 1 天未修改。", StorageSpecialCleanupRisk.Low),
    EmptyFolder("空文件夹", "扫描时没有发现任何直接子项；删除后不会影响其中不存在的文件。", StorageSpecialCleanupRisk.Low),
}

enum class StorageSpecialCleanupSource {
    MediaStoreDownloads,
    AuthorizedFolder,
}

data class StorageSpecialCleanupItem(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
    val source: StorageSpecialCleanupSource,
    val kind: StorageSpecialCleanupKind,
    val canDelete: Boolean,
    val isDirectory: Boolean = false,
) {
    val stableId: String get() = "${source.name}:$uri"
}

data class StorageSpecialCleanupScan(
    val mode: StorageSpecialCleanupMode,
    val items: List<StorageSpecialCleanupItem>,
    val scannedFileCount: Int,
    val scannedDirectoryCount: Int,
    val scannedBytes: Long,
    val treeUri: String?,
    val treeName: String?,
    val mediaStoreCount: Int,
    val errorMessage: String? = null,
)

data class StorageSpecialCleanupDeleteResult(
    val requestedCount: Int,
    val deletedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
)

internal object StorageSpecialCleanupPolicy {
    private val installerExtensions = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val partialExtensions = setOf("part", "partial", "crdownload", "download")
    private val temporaryExtensions = setOf("tmp", "temp", "cache")
    private val logExtensions = setOf("log", "trace", "dmp")
    private val backupExtensions = setOf("bak", "backup", "old")

    fun classifyDownload(
        displayName: String,
        sizeBytes: Long,
        modifiedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): StorageSpecialCleanupKind {
        val extension = extension(displayName)
        val ageDays = ageDays(modifiedAt, now)
        return when {
            extension in installerExtensions -> StorageSpecialCleanupKind.Installer
            extension in partialExtensions || looksLikePartialDownload(displayName) -> StorageSpecialCleanupKind.PartialDownload
            extension in archiveExtensions -> StorageSpecialCleanupKind.Archive
            ageDays >= 90L -> StorageSpecialCleanupKind.OldDownload
            sizeBytes >= 100L * SPECIAL_MB -> StorageSpecialCleanupKind.LargeDownload
            else -> StorageSpecialCleanupKind.OtherDownload
        }
    }

    fun classifyJunk(
        displayName: String,
        sizeBytes: Long,
        modifiedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): StorageSpecialCleanupKind? {
        val extension = extension(displayName)
        val ageDays = ageDays(modifiedAt, now)
        return when {
            sizeBytes == 0L && ageDays >= 1L -> StorageSpecialCleanupKind.ZeroByte
            (extension in partialExtensions || looksLikePartialDownload(displayName)) && ageDays >= 3L -> StorageSpecialCleanupKind.PartialDownload
            extension in logExtensions && ageDays >= 30L -> StorageSpecialCleanupKind.Log
            extension in backupExtensions && ageDays >= 90L -> StorageSpecialCleanupKind.Backup
            (extension in temporaryExtensions || looksLikeTemporary(displayName)) && ageDays >= 7L -> StorageSpecialCleanupKind.Temporary
            else -> null
        }
    }

    fun lowRiskKinds(): Set<StorageSpecialCleanupKind> = StorageSpecialCleanupKind.entries
        .filterTo(linkedSetOf()) { it.risk == StorageSpecialCleanupRisk.Low }

    private fun extension(displayName: String): String = displayName.trim().lowercase(Locale.ROOT)
        .substringAfterLast('.', missingDelimiterValue = "")

    private fun ageDays(modifiedAt: Long, now: Long): Long {
        if (modifiedAt <= 0L || now <= modifiedAt) return 0L
        return (now - modifiedAt) / DAY_MS
    }

    private fun looksLikePartialDownload(displayName: String): Boolean {
        val name = displayName.trim().lowercase(Locale.ROOT)
        return name.endsWith(".download") || name.endsWith(".crdownload") || name.endsWith(".part") ||
            name.contains("unfinished") || name.contains("download.tmp")
    }

    private fun looksLikeTemporary(displayName: String): Boolean {
        val name = displayName.trim().lowercase(Locale.ROOT)
        return name.startsWith("~$") || name.startsWith(".tmp") || name.endsWith('~') || name.contains("temporary")
    }
}

class StorageSpecialCleanupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(SPECIAL_CLEANUP_PREFS, Context.MODE_PRIVATE)

    fun savedTreeUri(mode: StorageSpecialCleanupMode): Uri? = prefs.getString(mode.prefKey(), null)
        ?.takeIf(String::isNotBlank)
        ?.let(Uri::parse)

    fun persistTreeUri(mode: StorageSpecialCleanupMode, uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrDefault(false)
        if (persisted) prefs.edit().putString(mode.prefKey(), uri.toString()).apply()
        return persisted
    }

    fun clearTreeUri(mode: StorageSpecialCleanupMode) {
        val uri = savedTreeUri(mode)
        prefs.edit().remove(mode.prefKey()).apply()
        if (uri != null && StorageSpecialCleanupMode.entries.none { it != mode && savedTreeUri(it) == uri }) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    fun scan(mode: StorageSpecialCleanupMode): StorageSpecialCleanupScan {
        val mediaItems = if (mode == StorageSpecialCleanupMode.Downloads) queryAccessibleDownloads() else emptyList()
        val treeUri = savedTreeUri(mode)
        val treeResult = treeUri?.let { scanTree(mode, it) }
        val combined = (mediaItems + treeResult?.items.orEmpty())
            .distinctBy(StorageSpecialCleanupItem::stableId)
            .sortedWith(compareBy<StorageSpecialCleanupItem> { it.kind.ordinal }.thenByDescending { it.sizeBytes })
        return StorageSpecialCleanupScan(
            mode = mode,
            items = combined,
            scannedFileCount = treeResult?.scannedFileCount ?: 0,
            scannedDirectoryCount = treeResult?.scannedDirectoryCount ?: 0,
            scannedBytes = treeResult?.scannedBytes ?: 0L,
            treeUri = treeUri?.toString(),
            treeName = treeResult?.treeName,
            mediaStoreCount = mediaItems.size,
            errorMessage = treeResult?.errorMessage,
        )
    }

    fun createMediaDeleteRequest(items: List<StorageSpecialCleanupItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = items.filter { it.source == StorageSpecialCleanupSource.MediaStoreDownloads }
            .map { Uri.parse(it.uri) }
            .distinct()
        return if (uris.isEmpty()) null else runCatching { MediaStore.createDeleteRequest(resolver, uris) }.getOrNull()
    }

    fun deleteMediaDirect(items: List<StorageSpecialCleanupItem>): StorageSpecialCleanupDeleteResult {
        val targets = items.filter { it.source == StorageSpecialCleanupSource.MediaStoreDownloads }
            .distinctBy(StorageSpecialCleanupItem::stableId)
        val errors = mutableListOf<String>()
        var deleted = 0
        targets.forEach { item ->
            val ok = runCatching { resolver.delete(Uri.parse(item.uri), null, null) > 0 }
                .onFailure { errors += it.message.orEmpty().ifBlank { "无法删除下载文件：${item.displayName}" } }
                .getOrDefault(false)
            if (ok) deleted += 1
        }
        return StorageSpecialCleanupDeleteResult(targets.size, deleted, targets.size - deleted, errors)
    }

    fun deleteAuthorized(items: List<StorageSpecialCleanupItem>): StorageSpecialCleanupDeleteResult {
        val targets = items.filter { it.source == StorageSpecialCleanupSource.AuthorizedFolder }
            .distinctBy(StorageSpecialCleanupItem::stableId)
            .sortedWith(compareBy<StorageSpecialCleanupItem> { !it.isDirectory }.thenByDescending { it.location.count { ch -> ch == '/' } })
        val errors = mutableListOf<String>()
        var deleted = 0
        targets.forEach { item ->
            val ok = runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(item.uri)) }
                .onFailure { errors += it.message.orEmpty().ifBlank { "无法删除授权项目：${item.displayName}" } }
                .getOrDefault(false)
            if (ok) deleted += 1
        }
        return StorageSpecialCleanupDeleteResult(targets.size, deleted, targets.size - deleted, errors)
    }

    private fun queryAccessibleDownloads(): List<StorageSpecialCleanupItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = buildList {
            add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }.joinToString(" AND ")
        val cursor = runCatching {
            resolver.query(collection, projection, selection, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
        }.getOrNull() ?: return emptyList()
        val result = mutableListOf<StorageSpecialCleanupItem>()
        cursor.use {
            val idIndex = it.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (it.moveToNext()) {
                val id = it.safeLong(idIndex)
                if (id <= 0L) continue
                val name = it.safeString(nameIndex).orEmpty().ifBlank { "未命名下载" }
                val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                val modifiedSeconds = it.safeLong(modifiedIndex)
                val modifiedAt = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L
                result += StorageSpecialCleanupItem(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = name,
                    sizeBytes = size,
                    mimeType = it.safeString(mimeIndex).orEmpty(),
                    modifiedAt = modifiedAt,
                    location = it.safeString(pathIndex).orEmpty().ifBlank { "系统下载集合" },
                    source = StorageSpecialCleanupSource.MediaStoreDownloads,
                    kind = StorageSpecialCleanupPolicy.classifyDownload(name, size, modifiedAt),
                    canDelete = true,
                )
            }
        }
        return result
    }

    private fun scanTree(mode: StorageSpecialCleanupMode, treeUri: Uri): TreeScanResult {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return TreeScanResult(errorMessage = "目录授权已失效，请重新选择目录。")
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryName(rootUri).ifBlank { if (mode == StorageSpecialCleanupMode.Downloads) "下载目录" else "待检查目录" }
        val queue = ArrayDeque<TreeNode>()
        queue.add(TreeNode(rootUri, rootName, canDelete = false, root = true))
        val items = mutableListOf<StorageSpecialCleanupItem>()
        var scannedFiles = 0
        var scannedDirectories = 0
        var scannedBytes = 0L
        var firstError: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            scannedDirectories += 1
            val parentId = runCatching { DocumentsContract.getDocumentId(node.uri) }.getOrNull() ?: continue
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
                        DocumentsContract.Document.COLUMN_FLAGS,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrElse { error ->
                firstError = firstError ?: error.message.orEmpty().ifBlank { "无法读取部分目录。" }
                null
            } ?: continue

            var childCount = 0
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                while (it.moveToNext()) {
                    childCount += 1
                    val documentId = it.safeString(idIndex) ?: continue
                    val name = it.safeString(nameIndex).orEmpty().ifBlank { "未命名项目" }
                    val mime = it.safeString(mimeIndex).orEmpty()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val path = "${node.path.trimEnd('/')}/$name"
                    val flags = it.safeLong(flagsIndex).toInt()
                    val canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(TreeNode(uri, path, canDelete = canDelete, root = false))
                        continue
                    }
                    scannedFiles += 1
                    val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                    scannedBytes += size
                    val modifiedAt = it.safeLong(modifiedIndex)
                    val kind = when (mode) {
                        StorageSpecialCleanupMode.Downloads -> StorageSpecialCleanupPolicy.classifyDownload(name, size, modifiedAt)
                        StorageSpecialCleanupMode.Junk -> StorageSpecialCleanupPolicy.classifyJunk(name, size, modifiedAt) ?: continue
                    }
                    items += StorageSpecialCleanupItem(
                        uri = uri.toString(),
                        displayName = name,
                        sizeBytes = size,
                        mimeType = mime,
                        modifiedAt = modifiedAt,
                        location = path,
                        source = StorageSpecialCleanupSource.AuthorizedFolder,
                        kind = kind,
                        canDelete = canDelete,
                    )
                }
            }
            if (mode == StorageSpecialCleanupMode.Junk && childCount == 0 && !node.root) {
                items += StorageSpecialCleanupItem(
                    uri = node.uri.toString(),
                    displayName = node.path.substringAfterLast('/').ifBlank { "空文件夹" },
                    sizeBytes = 0L,
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    modifiedAt = 0L,
                    location = node.path,
                    source = StorageSpecialCleanupSource.AuthorizedFolder,
                    kind = StorageSpecialCleanupKind.EmptyFolder,
                    canDelete = node.canDelete,
                    isDirectory = true,
                )
            }
        }
        return TreeScanResult(
            treeName = rootName,
            items = items,
            scannedFileCount = scannedFiles,
            scannedDirectoryCount = scannedDirectories,
            scannedBytes = scannedBytes,
            errorMessage = firstError,
        )
    }

    private fun queryName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.safeString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun StorageSpecialCleanupMode.prefKey(): String = when (this) {
        StorageSpecialCleanupMode.Downloads -> DOWNLOAD_TREE_KEY
        StorageSpecialCleanupMode.Junk -> JUNK_TREE_KEY
    }

    private fun Cursor.safeString(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return runCatching { getString(index) }.getOrNull()
    }

    private fun Cursor.safeLong(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return runCatching { getLong(index) }.getOrDefault(0L)
    }

    private data class TreeNode(
        val uri: Uri,
        val path: String,
        val canDelete: Boolean,
        val root: Boolean,
    )

    private data class TreeScanResult(
        val treeName: String? = null,
        val items: List<StorageSpecialCleanupItem> = emptyList(),
        val scannedFileCount: Int = 0,
        val scannedDirectoryCount: Int = 0,
        val scannedBytes: Long = 0L,
        val errorMessage: String? = null,
    )
}
