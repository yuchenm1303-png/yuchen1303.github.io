package com.yuchen.ailedger.service

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import org.json.JSONArray
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val ORGANIZATION_PREFS = "storage_media_organization"
private const val IGNORE_FILE_KEY = "ignore_files"
private const val IGNORE_DIRECTORY_KEY = "ignore_directories"
private const val MIN_IMAGE_BYTES = 120L * 1024L
private const val MAX_MEDIA_ROWS = 900
private const val MAX_SIMILAR_IMAGE_ROWS = 320
private const val MAX_FOLDER_ROWS = 3_000
private const val MAX_DOWNLOAD_RESULTS = 600
private const val SIMILAR_HASH_DISTANCE = 7
private const val SIMILAR_COLOR_DISTANCE = 78
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
            cleanLocation == cleanPrefix || cleanLocation.startsWith("$cleanPrefix/")
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
            cleanLocation.contains("screenshots") ||
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
            .sortedByDescending { it.files.sumOf(StorageOrganizationFile::sizeBytes) }
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
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun analyze(
        includeMedia: Boolean,
        authorizedTreeUri: Uri?,
        ignoreRules: StorageOrganizationIgnoreRules,
    ): StorageOrganizationSnapshot {
        val startedAt = System.currentTimeMillis()
        val imageBatch = if (includeMedia) queryImages() else ScanBatch(emptyList(), false)
        val visibleImages = imageBatch.files.filterNot(ignoreRules::isIgnored)
        val screenshotFiles = visibleImages
            .filter { StorageMediaOrganizationPolicy.isScreenshot(it.displayName, it.location) }
            .map { it.copy(kind = StorageOrganizationKind.Screenshot, risk = StorageReviewRisk.Review) }
            .sortedByDescending { it.modifiedAt }
        val burstGroups = StorageMediaOrganizationPolicy.buildBurstGroups(
            visibleImages.map { it.copy(kind = StorageOrganizationKind.BurstCandidate, risk = StorageReviewRisk.Caution) },
        )
        val similarResult = findSimilarPhotoGroups(visibleImages.take(MAX_SIMILAR_IMAGE_ROWS))
        val folderBatch = authorizedTreeUri?.let(::scanAuthorizedFolder) ?: ScanBatch(emptyList(), false)
        val categorizedDownloads = folderBatch.files
            .filterNot(ignoreRules::isIgnored)
            .groupBy(StorageOrganizationFile::kind)
            .map { (kind, files) ->
                StorageDownloadCategory(
                    kind = kind,
                    files = files.sortedByDescending { it.sizeBytes }.take(MAX_DOWNLOAD_RESULTS),
                )
            }
            .sortedByDescending(StorageDownloadCategory::totalBytes)
        return StorageOrganizationSnapshot(
            similarGroups = similarResult.groups,
            screenshots = screenshotFiles,
            burstGroups = burstGroups,
            downloadCategories = categorizedDownloads,
            indexedImageCount = visibleImages.size,
            perceptualHashedCount = similarResult.hashedCount,
            indexedFolderCount = folderBatch.files.size,
            limited = imageBatch.limited || folderBatch.limited || similarResult.limited,
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    fun loadPreviewBitmap(file: StorageOrganizationFile, maxEdgePx: Int = 720): Bitmap? {
        if (!file.mimeType.startsWith("image/")) return null
        val uri = Uri.parse(file.uri)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(maxEdgePx, maxEdgePx), null)
            } else {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { bitmap -> scaleDown(bitmap, maxEdgePx) }
            }
        }.getOrNull()
    }

    fun existingUris(files: List<StorageOrganizationFile>): Set<String> {
        return files.asSequence().filter { file ->
            val uri = Uri.parse(file.uri)
            runCatching {
                resolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.moveToFirst() }
                    ?: resolver.openFileDescriptor(uri, "r")?.use { true }
                    ?: false
            }.getOrDefault(false)
        }.mapTo(linkedSetOf()) { it.uri }
    }

    private fun queryImages(): ScanBatch {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val selectionParts = mutableListOf("${MediaStore.MediaColumns.SIZE} >= ?")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) selectionParts += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) selectionParts += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        val cursor = runCatching {
            resolver.query(
                collection,
                projection,
                selectionParts.joinToString(" AND "),
                arrayOf(MIN_IMAGE_BYTES.toString()),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return ScanBatch(emptyList(), false)
        val files = mutableListOf<StorageOrganizationFile>()
        var limited = false
        cursor.use {
            val idIndex = it.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthIndex = it.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightIndex = it.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                -1
            }
            while (it.moveToNext()) {
                if (files.size >= MAX_MEDIA_ROWS) {
                    limited = true
                    break
                }
                val id = it.safeLong(idIndex)
                if (id <= 0L) continue
                val modifiedSeconds = it.safeLong(modifiedIndex)
                files += StorageOrganizationFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名图片" },
                    sizeBytes = it.safeLong(sizeIndex).coerceAtLeast(0L),
                    mimeType = it.safeString(mimeIndex).orEmpty().ifBlank { "image/unknown" },
                    modifiedAt = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L,
                    location = it.safeString(pathIndex).orEmpty().ifBlank { "图片" },
                    source = StorageCandidateSource.MediaStore,
                    canDelete = true,
                    width = it.safeLong(widthIndex).toInt().coerceAtLeast(0),
                    height = it.safeLong(heightIndex).toInt().coerceAtLeast(0),
                    kind = StorageOrganizationKind.SimilarPhoto,
                    risk = StorageReviewRisk.Caution,
                )
            }
        }
        return ScanBatch(files, limited)
    }

    private fun scanAuthorizedFolder(treeUri: Uri): ScanBatch {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return ScanBatch(emptyList(), false)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryDocumentName(rootUri).ifBlank { "授权目录" }
        val queue = ArrayDeque<TreeNode>()
        queue.add(TreeNode(rootUri, rootName, 0))
        val files = mutableListOf<StorageOrganizationFile>()
        var visited = 0
        var limited = false
        while (queue.isNotEmpty()) {
            if (visited >= MAX_FOLDER_ROWS) {
                limited = true
                break
            }
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
            }.getOrNull() ?: continue
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                while (it.moveToNext()) {
                    if (visited >= MAX_FOLDER_ROWS) {
                        limited = true
                        break
                    }
                    val documentId = it.safeString(idIndex) ?: continue
                    val displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名文件" }
                    val mimeType = it.safeString(mimeIndex).orEmpty()
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val path = "${node.path.trimEnd('/')}/$displayName"
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (node.depth < 12) queue.add(TreeNode(documentUri, path, node.depth + 1))
                        continue
                    }
                    visited += 1
                    val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                    val classification = StorageMediaOrganizationPolicy.classifyDownload(displayName, mimeType, size)
                        ?: continue
                    val flags = it.safeLong(flagsIndex).toInt()
                    files += StorageOrganizationFile(
                        uri = documentUri.toString(),
                        displayName = displayName,
                        sizeBytes = size,
                        mimeType = mimeType,
                        modifiedAt = it.safeLong(modifiedIndex),
                        location = path,
                        source = StorageCandidateSource.AuthorizedFolder,
                        canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                        kind = classification.first,
                        risk = classification.second,
                    )
                }
            }
        }
        return ScanBatch(files, limited)
    }

    private fun findSimilarPhotoGroups(files: List<StorageOrganizationFile>): SimilarResult {
        val signatures = files.mapNotNull { file ->
            createPerceptualSignature(file)?.let { signature -> file to signature }
        }
        if (signatures.size < 2) return SimilarResult(emptyList(), signatures.size, false)
        val parent = IntArray(signatures.size) { it }

        fun find(index: Int): Int {
            var cursor = index
            while (parent[cursor] != cursor) {
                parent[cursor] = parent[parent[cursor]]
                cursor = parent[cursor]
            }
            return cursor
        }

        fun union(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }

        for (firstIndex in 0 until signatures.lastIndex) {
            val (firstFile, firstSignature) = signatures[firstIndex]
            for (secondIndex in firstIndex + 1 until signatures.size) {
                val (secondFile, secondSignature) = signatures[secondIndex]
                if (!StorageMediaOrganizationPolicy.dimensionsCompatible(firstFile, secondFile)) continue
                val hashDistance = java.lang.Long.bitCount(firstSignature.dHash xor secondSignature.dHash)
                if (hashDistance > SIMILAR_HASH_DISTANCE) continue
                val colorDistance = abs(firstSignature.averageRed - secondSignature.averageRed) +
                    abs(firstSignature.averageGreen - secondSignature.averageGreen) +
                    abs(firstSignature.averageBlue - secondSignature.averageBlue)
                if (colorDistance <= SIMILAR_COLOR_DISTANCE) union(firstIndex, secondIndex)
            }
        }

        val groupedIndexes = signatures.indices.groupBy(::find).values.filter { it.size >= 2 }
        val groups = groupedIndexes.map { indexes ->
            val groupFiles = indexes.map { signatures[it].first }
                .sortedByDescending { it.modifiedAt }
                .map { it.copy(kind = StorageOrganizationKind.SimilarPhoto, risk = StorageReviewRisk.Caution) }
            var maximumDistance = 0
            for (first in indexes.indices) {
                for (second in first + 1 until indexes.size) {
                    val distance = java.lang.Long.bitCount(
                        signatures[indexes[first]].second.dHash xor signatures[indexes[second]].second.dHash,
                    )
                    maximumDistance = max(maximumDistance, distance)
                }
            }
            SimilarPhotoGroup(
                id = "similar:${groupFiles.joinToString("|") { it.stableId }.hashCode()}",
                files = groupFiles,
                maxHashDistance = maximumDistance,
            )
        }.sortedByDescending { group -> group.files.sumOf { it.sizeBytes } }
        return SimilarResult(groups, signatures.size, files.size >= MAX_SIMILAR_IMAGE_ROWS)
    }

    private fun createPerceptualSignature(file: StorageOrganizationFile): PerceptualSignature? {
        val sourceBitmap = runCatching {
            val uri = Uri.parse(file.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(96, 96), null)
            } else {
                resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull() ?: return null
        val bitmap = runCatching { Bitmap.createScaledBitmap(sourceBitmap, 9, 8, true) }.getOrNull()
            ?: return null
        if (bitmap !== sourceBitmap) sourceBitmap.recycle()
        return try {
            var hash = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 9) {
                    val pixel = bitmap.getPixel(x, y)
                    red += pixel shr 16 and 0xFF
                    green += pixel shr 8 and 0xFF
                    blue += pixel and 0xFF
                    if (x < 8) {
                        val next = bitmap.getPixel(x + 1, y)
                        val currentGray = ((pixel shr 16 and 0xFF) * 30 + (pixel shr 8 and 0xFF) * 59 + (pixel and 0xFF) * 11)
                        val nextGray = ((next shr 16 and 0xFF) * 30 + (next shr 8 and 0xFF) * 59 + (next and 0xFF) * 11)
                        if (currentGray > nextGray) hash = hash or (1L shl bitIndex)
                        bitIndex += 1
                    }
                }
            }
            PerceptualSignature(
                dHash = hash,
                averageRed = (red / 72L).toInt(),
                averageGreen = (green / 72L).toInt(),
                averageBlue = (blue / 72L).toInt(),
            )
        } finally {
            bitmap.recycle()
        }
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

    private data class TreeNode(val uri: Uri, val path: String, val depth: Int)
    private data class ScanBatch(val files: List<StorageOrganizationFile>, val limited: Boolean)
    private data class PerceptualSignature(
        val dHash: Long,
        val averageRed: Int,
        val averageGreen: Int,
        val averageBlue: Int,
    )
    private data class SimilarResult(
        val groups: List<SimilarPhotoGroup>,
        val hashedCount: Int,
        val limited: Boolean,
    )
}
