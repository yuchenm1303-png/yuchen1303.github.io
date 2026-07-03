package com.yuchen.ailedger.service

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.net.URLConnection
import java.util.ArrayDeque
import java.util.Locale

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
    SharedStorageFile,
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
    val treeUri: String? = null,
    val treeName: String? = null,
    val mediaStoreCount: Int = 0,
    val globalAccessGranted: Boolean = false,
    val globalRootCount: Int = 0,
    val restrictedFallback: Boolean = false,
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

    fun classifyGlobalDownload(
        location: String,
        displayName: String,
        sizeBytes: Long,
        modifiedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): StorageSpecialCleanupKind? {
        val extension = extension(displayName)
        return when {
            extension in installerExtensions -> StorageSpecialCleanupKind.Installer
            extension in partialExtensions || looksLikePartialDownload(displayName) -> StorageSpecialCleanupKind.PartialDownload
            extension in archiveExtensions -> StorageSpecialCleanupKind.Archive
            isDownloadLocation(location) -> classifyDownload(displayName, sizeBytes, modifiedAt, now)
            else -> null
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

    private fun isDownloadLocation(location: String): Boolean {
        return location.replace('\\', '/')
            .split('/')
            .any { segment -> segment.equals("download", ignoreCase = true) || segment.equals("downloads", ignoreCase = true) }
    }

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

    fun hasGlobalSharedStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun globalSharedStorageAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun scan(mode: StorageSpecialCleanupMode): StorageSpecialCleanupScan {
        return if (hasGlobalSharedStorageAccess()) {
            scanGlobalSharedStorage(mode)
        } else {
            scanRestricted(mode)
        }
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

    fun deleteSharedStorage(items: List<StorageSpecialCleanupItem>): StorageSpecialCleanupDeleteResult {
        val targets = items.filter { it.source == StorageSpecialCleanupSource.SharedStorageFile }
            .distinctBy(StorageSpecialCleanupItem::stableId)
            .sortedWith(compareByDescending<StorageSpecialCleanupItem> { it.location.count { char -> char == '/' } })
        val errors = mutableListOf<String>()
        var deleted = 0
        targets.forEach { item ->
            val file = Uri.parse(item.uri).path?.let(::File)
            val ok = if (file == null) {
                errors += "无法解析共享存储路径：${item.displayName}"
                false
            } else {
                runCatching { file.exists() && file.delete() }
                    .onFailure { errors += it.message.orEmpty().ifBlank { "无法删除共享存储项目：${item.displayName}" } }
                    .getOrDefault(false)
            }
            if (ok) deleted += 1
        }
        return StorageSpecialCleanupDeleteResult(targets.size, deleted, targets.size - deleted, errors)
    }

    fun deleteAuthorized(items: List<StorageSpecialCleanupItem>): StorageSpecialCleanupDeleteResult {
        return StorageSpecialCleanupDeleteResult(0, 0, 0, emptyList())
    }

    private fun scanRestricted(mode: StorageSpecialCleanupMode): StorageSpecialCleanupScan {
        val mediaItems = if (mode == StorageSpecialCleanupMode.Downloads) queryAccessibleDownloads() else emptyList()
        return StorageSpecialCleanupScan(
            mode = mode,
            items = mediaItems.sortedWith(compareBy<StorageSpecialCleanupItem> { it.kind.ordinal }.thenByDescending { it.sizeBytes }),
            scannedFileCount = mediaItems.size,
            scannedDirectoryCount = 0,
            scannedBytes = mediaItems.sumOf(StorageSpecialCleanupItem::sizeBytes),
            mediaStoreCount = mediaItems.size,
            globalAccessGranted = false,
            globalRootCount = 0,
            restrictedFallback = true,
        )
    }

    private fun scanGlobalSharedStorage(mode: StorageSpecialCleanupMode): StorageSpecialCleanupScan {
        val roots = sharedStorageRoots()
        if (roots.isEmpty()) {
            return StorageSpecialCleanupScan(
                mode = mode,
                items = emptyList(),
                scannedFileCount = 0,
                scannedDirectoryCount = 0,
                scannedBytes = 0L,
                globalAccessGranted = true,
                errorMessage = "没有发现可读取的共享存储卷。",
            )
        }
        val rootPaths = roots.mapTo(hashSetOf()) { it.absolutePath }
        val queue = ArrayDeque<File>()
        roots.forEach(queue::add)
        val visitedDirectories = hashSetOf<String>()
        val items = mutableListOf<StorageSpecialCleanupItem>()
        var scannedFiles = 0
        var scannedDirectories = 0
        var scannedBytes = 0L
        var firstError: String? = null

        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            val canonicalDirectory = try {
                directory.canonicalFile
            } catch (error: Throwable) {
                firstError = firstError ?: error.message.orEmpty().ifBlank { "部分共享目录无法解析。" }
                continue
            }
            if (!visitedDirectories.add(canonicalDirectory.absolutePath)) continue
            if (shouldSkipSharedDirectory(canonicalDirectory)) continue
            scannedDirectories += 1
            val children = try {
                canonicalDirectory.listFiles()
            } catch (error: Throwable) {
                firstError = firstError ?: error.message.orEmpty().ifBlank { "部分共享目录无法读取。" }
                null
            } ?: continue

            if (
                mode == StorageSpecialCleanupMode.Junk &&
                children.isEmpty() &&
                canonicalDirectory.absolutePath !in rootPaths &&
                (canonicalDirectory.canWrite() || canonicalDirectory.parentFile?.canWrite() == true)
            ) {
                items += StorageSpecialCleanupItem(
                    uri = Uri.fromFile(canonicalDirectory).toString(),
                    displayName = canonicalDirectory.name.ifBlank { "空文件夹" },
                    sizeBytes = 0L,
                    mimeType = "vnd.android.document/directory",
                    modifiedAt = canonicalDirectory.lastModified(),
                    location = canonicalDirectory.absolutePath,
                    source = StorageSpecialCleanupSource.SharedStorageFile,
                    kind = StorageSpecialCleanupKind.EmptyFolder,
                    canDelete = true,
                    isDirectory = true,
                )
            }

            children.forEach { child ->
                if (child.isDirectory) {
                    queue.add(child)
                    return@forEach
                }
                if (!child.isFile) return@forEach
                scannedFiles += 1
                val size = child.length().coerceAtLeast(0L)
                scannedBytes += size
                val location = child.absolutePath
                val kind = when (mode) {
                    StorageSpecialCleanupMode.Downloads -> StorageSpecialCleanupPolicy.classifyGlobalDownload(
                        location = location,
                        displayName = child.name,
                        sizeBytes = size,
                        modifiedAt = child.lastModified(),
                    )
                    StorageSpecialCleanupMode.Junk -> StorageSpecialCleanupPolicy.classifyJunk(
                        displayName = child.name,
                        sizeBytes = size,
                        modifiedAt = child.lastModified(),
                    )
                } ?: return@forEach
                items += StorageSpecialCleanupItem(
                    uri = Uri.fromFile(child).toString(),
                    displayName = child.name.ifBlank { "未命名文件" },
                    sizeBytes = size,
                    mimeType = URLConnection.guessContentTypeFromName(child.name).orEmpty(),
                    modifiedAt = child.lastModified(),
                    location = location,
                    source = StorageSpecialCleanupSource.SharedStorageFile,
                    kind = kind,
                    canDelete = child.canWrite() || child.parentFile?.canWrite() == true,
                )
            }
        }

        return StorageSpecialCleanupScan(
            mode = mode,
            items = items.sortedWith(compareBy<StorageSpecialCleanupItem> { it.kind.ordinal }.thenByDescending { it.sizeBytes }),
            scannedFileCount = scannedFiles,
            scannedDirectoryCount = scannedDirectories,
            scannedBytes = scannedBytes,
            globalAccessGranted = true,
            globalRootCount = roots.size,
            restrictedFallback = false,
            errorMessage = firstError,
        )
    }

    private fun sharedStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = appContext.getSystemService(StorageManager::class.java)
            manager?.storageVolumes.orEmpty().mapNotNullTo(roots) { volume ->
                volume.directory?.takeIf { it.exists() && it.isDirectory && it.canRead() }
            }
        }
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory()
            .takeIf { it.exists() && it.isDirectory && it.canRead() }
            ?.let(roots::add)
        return roots.mapNotNull { root -> runCatching { root.canonicalFile }.getOrNull() }
            .distinctBy(File::getAbsolutePath)
    }

    private fun shouldSkipSharedDirectory(directory: File): Boolean {
        val normalized = directory.absolutePath.replace('\\', '/').lowercase(Locale.ROOT)
        if (normalized.contains("/android/data") || normalized.contains("/android/obb")) return true
        return directory.name.lowercase(Locale.ROOT) in setOf(".trash", ".trashed", "lost.dir", "\$recycle.bin")
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

    private fun Cursor.safeString(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return runCatching { getString(index) }.getOrNull()
    }

    private fun Cursor.safeLong(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return runCatching { getLong(index) }.getOrDefault(0L)
    }
}
