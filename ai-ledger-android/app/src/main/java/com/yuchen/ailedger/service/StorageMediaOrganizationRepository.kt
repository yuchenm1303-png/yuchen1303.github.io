package com.yuchen.ailedger.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val ORGANIZATION_PREFS = "storage_media_organization"
private const val IGNORE_FILE_KEY = "ignore_files"
private const val IGNORE_DIRECTORY_KEY = "ignore_directories"
private const val BURST_TIME_WINDOW_MS = 4_000L
private const val KB = 1024L
private const val MB = 1024L * KB

enum class StorageReviewRisk(
    val label: String,
    val explanation: String,
) {
    Low("低风险建议", "通常是安装包、压缩包或明确可重新获取的文件，仍需确认后删除。"),
    Review("建议检查", "可能有整理价值，但不能仅凭分类判断为无用文件。"),
    Caution("谨慎处理", "可能包含唯一照片、文档或重要内容，不提供自动勾选。"),
}

enum class StorageOrganizationKind(val label: String) {
    SimilarPhoto("相似照片"),
    Screenshot("截图"),
    BurstCandidate("连拍候选"),
    QualityCandidate("画质候选"),
    Installer("安装包"),
    Archive("压缩包"),
    Document("文档"),
    Media("媒体文件"),
    LargeOther("其他大文件"),
}

data class StorageOrganizationFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
    val source: StorageCandidateSource,
    val canDelete: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val kind: StorageOrganizationKind,
    val risk: StorageReviewRisk,
    val reviewNote: String = "",
) {
    val stableId: String get() = "${source.name}:$uri"
    val parentLocation: String
        get() = location.replace('\\', '/').substringBeforeLast('/', missingDelimiterValue = location)
}

data class SimilarPhotoGroup(
    val id: String,
    val files: List<StorageOrganizationFile>,
    val maxHashDistance: Int,
)

data class BurstPhotoGroup(
    val id: String,
    val files: List<StorageOrganizationFile>,
    val explicitBurstName: Boolean,
)

data class StorageDownloadCategory(
    val kind: StorageOrganizationKind,
    val files: List<StorageOrganizationFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

data class StorageOrganizationSnapshot(
    val similarGroups: List<SimilarPhotoGroup>,
    val screenshots: List<StorageOrganizationFile>,
    val burstGroups: List<BurstPhotoGroup>,
    val qualityCandidates: List<StorageOrganizationFile>,
    val downloadCategories: List<StorageDownloadCategory>,
    val indexedImageCount: Int,
    val perceptualHashedCount: Int,
    val indexedFolderCount: Int,
    val limited: Boolean,
    val elapsedMs: Long,
    val scannedAt: Long = System.currentTimeMillis(),
) {
    val similarPhotoCount: Int get() = similarGroups.sumOf { it.files.size }
    val burstPhotoCount: Int get() = burstGroups.sumOf { it.files.size }
    val downloadFileCount: Int get() = downloadCategories.sumOf { it.files.size }
}

data class StorageOrganizationIgnoreRules(
    val ignoredUris: Set<String>,
    val ignoredDirectories: Set<String>,
) {
    fun isIgnored(file: StorageOrganizationFile): Boolean {
        if (file.uri in ignoredUris) return true
        val cleanLocation = normalizeLocation(file.location)
        return ignoredDirectories.any { prefix ->
            val cleanPrefix = normalizeLocation(prefix)
            cleanPrefix.isNotBlank() && (cleanLocation == cleanPrefix || cleanLocation.startsWith("$cleanPrefix/"))
        }
    }

    private fun normalizeLocation(value: String): String {
        return value.trim().replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
    }
}

internal object StorageMediaOrganizationPolicy {
    private val installerExtensions = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "epub",
    )
    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "heic", "gif", "mp4", "mkv", "mov", "avi", "mp3", "wav", "flac", "m4a",
    )

    fun classifyDownload(
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
    ): Pair<StorageOrganizationKind, StorageReviewRisk>? {
        val extension = displayName.lowercase(Locale.ROOT).substringAfterLast('.', missingDelimiterValue = "")
        val mime = mimeType.lowercase(Locale.ROOT)
        return when {
            extension in installerExtensions -> StorageOrganizationKind.Installer to StorageReviewRisk.Low
            extension in archiveExtensions -> StorageOrganizationKind.Archive to StorageReviewRisk.Low
            extension in documentExtensions || mime.startsWith("application/pdf") || mime.startsWith("text/") ->
                StorageOrganizationKind.Document to StorageReviewRisk.Caution
            extension in mediaExtensions || mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") ->
                StorageOrganizationKind.Media to StorageReviewRisk.Caution
            sizeBytes >= 50L * MB -> StorageOrganizationKind.LargeOther to StorageReviewRisk.Review
            else -> null
        }
    }

    fun isScreenshot(displayName: String, location: String): Boolean {
        val cleanName = displayName.lowercase(Locale.ROOT)
        val cleanLocation = location.lowercase(Locale.ROOT).replace('\\', '/')
        return cleanLocation.contains("screenshot") ||
            cleanName.startsWith("screenshot") ||
            cleanName.startsWith("screen_shot") ||
            cleanName.startsWith("截屏") ||
            cleanName.startsWith("截图")
    }

    fun explicitBurstKey(file: StorageOrganizationFile): String? {
        val clean = file.displayName.lowercase(Locale.ROOT)
        if (!clean.contains("burst") && !clean.contains("连拍")) return null
        return clean
            .replace(Regex("\\d{3,}"), "#")
            .substringBeforeLast('.')
            .let { "${file.parentLocation.lowercase(Locale.ROOT)}:$it" }
    }

    fun dimensionsCompatible(first: StorageOrganizationFile, second: StorageOrganizationFile): Boolean {
        if (first.width <= 0 || first.height <= 0 || second.width <= 0 || second.height <= 0) return true
        val firstRatio = first.width.toDouble() / first.height.toDouble()
        val secondRatio = second.width.toDouble() / second.height.toDouble()
        return abs(firstRatio - secondRatio) <= 0.045
    }

    fun buildBurstGroups(files: List<StorageOrganizationFile>): List<BurstPhotoGroup> {
        val explicitGroups = files.groupBy { explicitBurstKey(it) }
            .filterKeys { it != null }
            .values
            .filter { it.size >= 2 }
            .map { group ->
                val sorted = group.sortedBy { it.modifiedAt }
                BurstPhotoGroup(
                    id = "explicit:${sorted.first().stableId}",
                    files = sorted,
                    explicitBurstName = true,
                )
            }
        val claimed = explicitGroups.flatMapTo(hashSetOf()) { group -> group.files.map { it.stableId } }
        val sorted = files.filterNot { it.stableId in claimed }
            .filter { it.modifiedAt > 0L }
            .sortedBy { it.modifiedAt }
        val timedGroups = mutableListOf<BurstPhotoGroup>()
        var current = mutableListOf<StorageOrganizationFile>()

        fun flush() {
            if (current.size >= 3) {
                val stable = current.toList()
                timedGroups += BurstPhotoGroup(
                    id = "timed:${stable.first().stableId}",
                    files = stable,
                    explicitBurstName = false,
                )
            }
            current = mutableListOf()
        }

        sorted.forEach { file ->
            val last = current.lastOrNull()
            val sameSeries = last != null &&
                file.modifiedAt - last.modifiedAt in 0L..BURST_TIME_WINDOW_MS &&
                last.parentLocation.equals(file.parentLocation, ignoreCase = true) &&
                dimensionsCompatible(last, file)
            if (last == null || sameSeries) {
                current += file
            } else {
                flush()
                current += file
            }
        }
        flush()
        return (explicitGroups + timedGroups)
            .distinctBy { group -> group.files.joinToString("|") { it.stableId } }
            .sortedByDescending { group -> group.files.sumOf { it.sizeBytes } }
    }
}

class StorageOrganizationIgnoreStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(ORGANIZATION_PREFS, Context.MODE_PRIVATE)

    fun load(): StorageOrganizationIgnoreRules {
        return StorageOrganizationIgnoreRules(
            ignoredUris = readSet(IGNORE_FILE_KEY),
            ignoredDirectories = readSet(IGNORE_DIRECTORY_KEY),
        )
    }

    fun ignoreFile(file: StorageOrganizationFile) {
        writeSet(IGNORE_FILE_KEY, readSet(IGNORE_FILE_KEY) + file.uri)
    }

    fun ignoreDirectory(file: StorageOrganizationFile) {
        val parent = file.parentLocation.trim()
        if (parent.isNotBlank()) writeSet(IGNORE_DIRECTORY_KEY, readSet(IGNORE_DIRECTORY_KEY) + parent)
    }

    fun clear() {
        prefs.edit().remove(IGNORE_FILE_KEY).remove(IGNORE_DIRECTORY_KEY).apply()
    }

    private fun readSet(key: String): Set<String> {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun writeSet(key: String, values: Set<String>) {
        val array = JSONArray()
        values.sorted().forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }
}

class StorageMediaOrganizationRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val completeRepository = StorageMediaOrganizationCompleteRepository(context.applicationContext)

    fun analyze(
        includeMedia: Boolean,
        authorizedTreeUri: Uri?,
        ignoreRules: StorageOrganizationIgnoreRules,
    ): StorageOrganizationSnapshot {
        return completeRepository.analyze(includeMedia, authorizedTreeUri, ignoreRules)
    }

    fun loadPreviewBitmap(file: StorageOrganizationFile, maxEdgePx: Int = 720): Bitmap? {
        if (!file.mimeType.startsWith("image/")) return null
        val uri = Uri.parse(file.uri)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(maxEdgePx, maxEdgePx), null)
            } else {
                decodeLegacyThumbnail(uri, maxEdgePx)
            }
        }.getOrNull()
    }

    fun existingUris(files: List<StorageOrganizationFile>): Set<String> {
        return files.asSequence().filter { file ->
            val uri = Uri.parse(file.uri)
            val queryExists = runCatching {
                resolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.moveToFirst() }
            }.getOrNull()
            queryExists ?: runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }.mapTo(linkedSetOf()) { it.uri }
    }

    private fun decodeLegacyThumbnail(uri: Uri, maxEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sampleSize > maxEdgePx * 2) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        return scaleDown(decoded, maxEdgePx)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) return bitmap
        val scale = maxEdgePx.toFloat() / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
