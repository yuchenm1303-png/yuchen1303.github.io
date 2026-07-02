package com.yuchen.ailedger.service

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.system.Os
import java.util.ArrayDeque
import java.util.Locale

private const val STORAGE_PREFS = "storage_management"
private const val PREF_TREE_URI = "tree_uri"
private const val MAX_FOLDER_FILES = 5_000
private const val MAX_FOLDER_CANDIDATES = 500
private const val MAX_MEDIA_CANDIDATES_PER_KIND = 220

private const val MB = 1024L * 1024L

data class DeviceStorageOverview(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}

data class AppCacheUsage(
    val label: String,
    val packageName: String,
    val cacheBytes: Long,
    val dataBytes: Long,
    val appBytes: Long,
    val isSystemApp: Boolean,
    val isProtected: Boolean,
)

enum class StorageCandidateSource {
    MediaStore,
    AuthorizedFolder,
}

enum class StorageCandidateKind(val label: String) {
    LargeVideo("大型视频"),
    LargeImage("大型图片"),
    LargeAudio("大型音频"),
    Installer("安装包"),
    Archive("压缩包"),
    Temporary("临时文件"),
    LargeFile("大型文件"),
}

data class StorageFileCandidate(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
    val source: StorageCandidateSource,
    val kind: StorageCandidateKind,
    val canDelete: Boolean,
) {
    val stableId: String get() = "${source.name}:$uri"
    val reviewReason: String get() = StorageCandidateClassifier.reason(kind)
}

data class AuthorizedFolderScan(
    val treeUri: String,
    val displayName: String,
    val scannedFileCount: Int,
    val scannedBytes: Long,
    val candidates: List<StorageFileCandidate>,
    val truncated: Boolean,
    val errorMessage: String? = null,
)

data class StorageScanSnapshot(
    val overview: DeviceStorageOverview,
    val usageAccessGranted: Boolean,
    val appCaches: List<AppCacheUsage>,
    val mediaCandidates: List<StorageFileCandidate>,
    val folderScan: AuthorizedFolderScan?,
    val scannedAt: Long = System.currentTimeMillis(),
)

data class StorageDeleteResult(
    val requestedCount: Int,
    val deletedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
)

internal object StorageCandidateClassifier {
    private val installerExtensions = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val temporaryExtensions = setOf("tmp", "temp", "log", "bak", "old")

    fun classify(
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        source: StorageCandidateSource,
    ): StorageCandidateKind? {
        val cleanName = displayName.trim().lowercase(Locale.ROOT)
        val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
        val cleanMime = mimeType.trim().lowercase(Locale.ROOT)
        return when {
            extension in installerExtensions -> StorageCandidateKind.Installer
            extension in archiveExtensions && sizeBytes >= 20L * MB -> StorageCandidateKind.Archive
            extension in temporaryExtensions && sizeBytes >= 1L * MB -> StorageCandidateKind.Temporary
            cleanMime.startsWith("video/") && sizeBytes >= 100L * MB -> StorageCandidateKind.LargeVideo
            cleanMime.startsWith("image/") && sizeBytes >= 20L * MB -> StorageCandidateKind.LargeImage
            cleanMime.startsWith("audio/") && sizeBytes >= 30L * MB -> StorageCandidateKind.LargeAudio
            source == StorageCandidateSource.AuthorizedFolder && sizeBytes >= 100L * MB -> StorageCandidateKind.LargeFile
            else -> null
        }
    }

    fun reason(kind: StorageCandidateKind): String = when (kind) {
        StorageCandidateKind.Installer -> "安装完成后通常可以删除，但请先确认不再需要离线安装。"
        StorageCandidateKind.Archive -> "压缩包占用较大，删除前请确认其中没有唯一副本。"
        StorageCandidateKind.Temporary -> "名称像临时、日志或备份文件，仍需确认对应应用已经不再使用。"
        StorageCandidateKind.LargeVideo -> "视频占用较大，删除会永久丢失内容。"
        StorageCandidateKind.LargeImage -> "图片占用较大，删除会永久丢失内容。"
        StorageCandidateKind.LargeAudio -> "音频占用较大，删除会永久丢失内容。"
        StorageCandidateKind.LargeFile -> "这是授权目录中的大型文件，不会自动判断为垃圾。"
    }
}

class StorageManagementRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val packageManager = appContext.packageManager
    private val prefs = appContext.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
    private val appRepository = AppManagementRepository(appContext)

    fun loadOverview(): DeviceStorageOverview {
        val manager = appContext.getSystemService(StorageStatsManager::class.java)
        val total = runCatching { manager?.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
        val free = runCatching { manager?.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
        if (total != null && free != null && total > 0L) {
            return DeviceStorageOverview(totalBytes = total, freeBytes = free.coerceAtMost(total))
        }
        val stat = Os.statvfs(Environment.getDataDirectory().absolutePath)
        val fallbackTotal = stat.f_blocks * stat.f_frsize
        val fallbackFree = stat.f_bavail * stat.f_frsize
        return DeviceStorageOverview(fallbackTotal, fallbackFree.coerceAtMost(fallbackTotal))
    }

    fun hasUsageAccess(): Boolean {
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

    fun loadAppCacheRanking(limit: Int = 40): List<AppCacheUsage> {
        if (!hasUsageAccess()) return emptyList()
        val manager = appContext.getSystemService(StorageStatsManager::class.java) ?: return emptyList()
        return appRepository.loadApps().mapNotNull { app ->
            val info = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(
                        app.packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(app.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                }
            }.getOrNull() ?: return@mapNotNull null
            val stats = runCatching {
                manager.queryStatsForPackage(
                    info.storageUuid ?: StorageManager.UUID_DEFAULT,
                    app.packageName,
                    UserHandle.getUserHandleForUid(app.uid),
                )
            }.getOrNull() ?: return@mapNotNull null
            AppCacheUsage(
                label = app.label,
                packageName = app.packageName,
                cacheBytes = stats.cacheBytes.coerceAtLeast(0L),
                dataBytes = stats.dataBytes.coerceAtLeast(0L),
                appBytes = stats.appBytes.coerceAtLeast(0L),
                isSystemApp = app.isSystemApp,
                isProtected = app.isProtected,
            )
        }.filter { it.cacheBytes > 0L }
            .sortedByDescending { it.cacheBytes }
            .take(limit.coerceIn(1, 100))
    }

    fun scanAccessibleMedia(): List<StorageFileCandidate> {
        return buildList {
            addAll(queryMedia(MediaKind.Video, 100L * MB))
            addAll(queryMedia(MediaKind.Image, 20L * MB))
            addAll(queryMedia(MediaKind.Audio, 30L * MB))
        }.sortedByDescending { it.sizeBytes }
    }

    fun savedTreeUri(): Uri? = prefs.getString(PREF_TREE_URI, null)
        ?.takeIf(String::isNotBlank)
        ?.let(Uri::parse)

    fun persistTreeUri(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrDefault(false)
        if (persisted) prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply()
        return persisted
    }

    fun clearSavedTreeUri() {
        savedTreeUri()?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove(PREF_TREE_URI).apply()
    }

    fun scanSavedFolder(): AuthorizedFolderScan? = savedTreeUri()?.let(::scanFolder)

    fun scanFolder(treeUri: Uri): AuthorizedFolderScan {
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return AuthorizedFolderScan(
                treeUri = treeUri.toString(),
                displayName = "授权目录",
                scannedFileCount = 0,
                scannedBytes = 0L,
                candidates = emptyList(),
                truncated = false,
                errorMessage = "目录授权已失效，请重新选择目录。",
            )
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val rootName = queryDocumentName(rootUri).ifBlank { "授权目录" }
        val queue = ArrayDeque<TreeNode>()
        queue.add(TreeNode(rootUri, rootName, 0))
        val candidates = mutableListOf<StorageFileCandidate>()
        var scannedFiles = 0
        var scannedBytes = 0L
        var truncated = false
        var firstError: String? = null

        while (queue.isNotEmpty() && scannedFiles < MAX_FOLDER_FILES && candidates.size < MAX_FOLDER_CANDIDATES) {
            val node = queue.removeFirst()
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

            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                while (it.moveToNext()) {
                    if (scannedFiles >= MAX_FOLDER_FILES || candidates.size >= MAX_FOLDER_CANDIDATES) {
                        truncated = true
                        break
                    }
                    val documentId = it.safeString(idIndex) ?: continue
                    val displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名文件" }
                    val mimeType = it.safeString(mimeIndex).orEmpty()
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val childPath = "${node.path.trimEnd('/')}/$displayName"
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (node.depth < 12) queue.add(TreeNode(documentUri, childPath, node.depth + 1))
                        continue
                    }
                    scannedFiles += 1
                    val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                    scannedBytes += size
                    val kind = StorageCandidateClassifier.classify(
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = size,
                        source = StorageCandidateSource.AuthorizedFolder,
                    ) ?: continue
                    val flags = it.safeLong(flagsIndex).toInt()
                    candidates += StorageFileCandidate(
                        uri = documentUri.toString(),
                        displayName = displayName,
                        sizeBytes = size,
                        mimeType = mimeType,
                        modifiedAt = it.safeLong(modifiedIndex),
                        location = childPath,
                        source = StorageCandidateSource.AuthorizedFolder,
                        kind = kind,
                        canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                    )
                }
            }
        }
        if (queue.isNotEmpty()) truncated = true
        return AuthorizedFolderScan(
            treeUri = treeUri.toString(),
            displayName = rootName,
            scannedFileCount = scannedFiles,
            scannedBytes = scannedBytes,
            candidates = candidates.sortedByDescending { it.sizeBytes },
            truncated = truncated,
            errorMessage = firstError,
        )
    }

    fun createMediaDeleteRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(resolver, uris.distinct())
    }

    fun deleteMediaDirect(uris: List<Uri>): StorageDeleteResult {
        val errors = mutableListOf<String>()
        var deleted = 0
        uris.distinct().forEach { uri ->
            val ok = runCatching { resolver.delete(uri, null, null) > 0 }
                .onFailure { errors += it.message.orEmpty().ifBlank { "无法删除媒体文件：$uri" } }
                .getOrDefault(false)
            if (ok) deleted += 1
        }
        return StorageDeleteResult(
            requestedCount = uris.distinct().size,
            deletedCount = deleted,
            failedCount = uris.distinct().size - deleted,
            errors = errors,
        )
    }

    fun deleteAuthorizedDocuments(uris: List<Uri>): StorageDeleteResult {
        val errors = mutableListOf<String>()
        var deleted = 0
        uris.distinct().forEach { uri ->
            val ok = runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                .onFailure { errors += it.message.orEmpty().ifBlank { "无法删除授权目录文件：$uri" } }
                .getOrDefault(false)
            if (ok) deleted += 1
        }
        return StorageDeleteResult(
            requestedCount = uris.distinct().size,
            deletedCount = deleted,
            failedCount = uris.distinct().size - deleted,
            errors = errors,
        )
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = Uri.parse("package:${appContext.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun queryMedia(kind: MediaKind, minimumBytes: Long): List<StorageFileCandidate> {
        val collection = kind.collectionUri()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val selectionParts = mutableListOf("${MediaStore.MediaColumns.SIZE} >= ?")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionParts += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selectionParts += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        }
        val cursor = runCatching {
            resolver.query(
                collection,
                projection,
                selectionParts.joinToString(" AND "),
                arrayOf(minimumBytes.toString()),
                "${MediaStore.MediaColumns.SIZE} DESC",
            )
        }.getOrNull() ?: return emptyList()
        val result = mutableListOf<StorageFileCandidate>()
        cursor.use {
            val idIndex = it.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                -1
            }
            while (it.moveToNext() && result.size < MAX_MEDIA_CANDIDATES_PER_KIND) {
                val id = it.safeLong(idIndex)
                if (id <= 0L) continue
                val displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名媒体" }
                val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                val mime = it.safeString(mimeIndex).orEmpty().ifBlank { kind.defaultMimePrefix }
                val candidateKind = StorageCandidateClassifier.classify(
                    displayName = displayName,
                    mimeType = mime,
                    sizeBytes = size,
                    source = StorageCandidateSource.MediaStore,
                ) ?: continue
                val modifiedSeconds = it.safeLong(modifiedIndex)
                result += StorageFileCandidate(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = displayName,
                    sizeBytes = size,
                    mimeType = mime,
                    modifiedAt = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L,
                    location = it.safeString(pathIndex).orEmpty().ifBlank { kind.label },
                    source = StorageCandidateSource.MediaStore,
                    kind = candidateKind,
                    canDelete = true,
                )
            }
        }
        return result
    }

    private fun queryDocumentName(documentUri: Uri): String {
        return runCatching {
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.safeString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun Cursor.safeString(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return runCatching { getString(index) }.getOrNull()
    }

    private fun Cursor.safeLong(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return runCatching { getLong(index) }.getOrDefault(0L)
    }

    private enum class MediaKind(
        val label: String,
        val defaultMimePrefix: String,
    ) {
        Video("视频", "video/unknown"),
        Image("图片", "image/unknown"),
        Audio("音频", "audio/unknown");

        fun collectionUri(): Uri {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (this) {
                    Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    Image -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                }
            } else {
                when (this) {
                    Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            }
        }
    }

    private data class TreeNode(
        val uri: Uri,
        val path: String,
        val depth: Int,
    )
}
