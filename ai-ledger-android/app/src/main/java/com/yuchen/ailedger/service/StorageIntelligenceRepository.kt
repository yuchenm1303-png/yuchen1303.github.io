package com.yuchen.ailedger.service

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale

private const val INTELLIGENCE_PREFS = "storage_intelligence"
private const val HISTORY_KEY = "cleanup_history"
private const val MIN_ANALYSIS_BYTES = 1L * 1024L * 1024L
private const val OLD_FILE_MIN_BYTES = 20L * 1024L * 1024L
private const val OLD_FILE_AGE_MS = 180L * 24L * 60L * 60L * 1000L
private const val MAX_MEDIA_ANALYSIS_FILES = 2_400
private const val MAX_FOLDER_ANALYSIS_FILES = 3_000
private const val MAX_QUICK_HASH_FILES = 600
private const val MAX_FULL_HASH_FILES = 240
private const val MAX_FULL_HASH_BYTES = 2L * 1024L * 1024L * 1024L
private const val QUICK_HASH_CHUNK_BYTES = 128 * 1024
private const val FULL_HASH_BUFFER_BYTES = 256 * 1024
private const val MAX_OLD_FILE_RESULTS = 200
private const val MAX_HISTORY_ITEMS = 30

data class StorageIntelligenceFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAt: Long,
    val location: String,
    val source: StorageCandidateSource,
    val canDelete: Boolean,
) {
    val stableId: String get() = "${source.name}:$uri"
}

data class StorageDuplicateGroup(
    val id: String,
    val sizeBytes: Long,
    val files: List<StorageIntelligenceFile>,
    val keepFileId: String,
    val suggestedDeleteIds: Set<String>,
    val recoverableBytes: Long,
)

data class StorageIntelligenceResult(
    val scannedFileCount: Int,
    val duplicateGroups: List<StorageDuplicateGroup>,
    val oldFiles: List<StorageIntelligenceFile>,
    val quickHashedFileCount: Int,
    val fullHashedFileCount: Int,
    val skippedHashFileCount: Int,
    val limited: Boolean,
    val elapsedMs: Long,
    val analyzedAt: Long = System.currentTimeMillis(),
) {
    val duplicateFileCount: Int get() = duplicateGroups.sumOf { it.files.size }
    val recoverableBytes: Long get() = duplicateGroups.sumOf { it.recoverableBytes }
}

data class StorageCleanupHistoryEntry(
    val id: String,
    val createdAt: Long,
    val requestedCount: Int,
    val deletedCount: Int,
    val failedCount: Int,
    val releasedBytes: Long,
    val label: String,
)

internal object StorageIntelligencePolicy {
    fun isOldFile(modifiedAt: Long, sizeBytes: Long, now: Long): Boolean {
        return modifiedAt > 0L && sizeBytes >= OLD_FILE_MIN_BYTES && modifiedAt <= now - OLD_FILE_AGE_MS
    }

    fun chooseKeeper(files: List<StorageIntelligenceFile>): StorageIntelligenceFile? {
        return files.minWithOrNull(
            compareByDescending<StorageIntelligenceFile> { preferredLocationScore(it.location) }
                .thenBy { it.modifiedAt.takeIf { value -> value > 0L } ?: Long.MAX_VALUE }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.uri },
        )
    }

    fun suggestedDeleteIds(
        files: List<StorageIntelligenceFile>,
        keepFileId: String,
    ): Set<String> {
        return files.asSequence()
            .filter { it.stableId != keepFileId && it.canDelete }
            .mapTo(linkedSetOf()) { it.stableId }
    }

    private fun preferredLocationScore(location: String): Int {
        val clean = location.lowercase(Locale.ROOT).replace('\\', '/')
        return when {
            clean.contains("dcim/camera") -> 3
            clean.contains("dcim") -> 2
            clean.contains("pictures") || clean.contains("movies") || clean.contains("music") -> 1
            else -> 0
        }
    }
}

class StorageIntelligenceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun analyze(
        includeMedia: Boolean,
        authorizedTreeUri: Uri?,
    ): StorageIntelligenceResult {
        val startedAt = System.currentTimeMillis()
        val media = if (includeMedia) collectMediaFiles() else ScanBatch(emptyList(), false)
        val folder = authorizedTreeUri?.let(::collectFolderFiles) ?: ScanBatch(emptyList(), false)
        val files = (media.files + folder.files)
            .distinctBy { it.stableId }
        val duplicateResult = findExactDuplicates(files)
        val now = System.currentTimeMillis()
        val oldFiles = files.asSequence()
            .filter { StorageIntelligencePolicy.isOldFile(it.modifiedAt, it.sizeBytes, now) }
            .sortedByDescending { it.sizeBytes }
            .take(MAX_OLD_FILE_RESULTS)
            .toList()
        return StorageIntelligenceResult(
            scannedFileCount = files.size,
            duplicateGroups = duplicateResult.groups,
            oldFiles = oldFiles,
            quickHashedFileCount = duplicateResult.quickHashed,
            fullHashedFileCount = duplicateResult.fullHashed,
            skippedHashFileCount = duplicateResult.skipped,
            limited = media.limited || folder.limited || duplicateResult.limited,
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    fun existingUris(files: List<StorageIntelligenceFile>): Set<String> {
        return files.asSequence().filter { file ->
            val uri = Uri.parse(file.uri)
            runCatching {
                resolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.moveToFirst() }
                    ?: resolver.openFileDescriptor(uri, "r")?.use { true }
                    ?: false
            }.getOrDefault(false)
        }.mapTo(linkedSetOf()) { it.uri }
    }

    private fun collectMediaFiles(): ScanBatch {
        val result = mutableListOf<StorageIntelligenceFile>()
        var limited = false
        for (kind in MediaKind.entries) {
            if (result.size >= MAX_MEDIA_ANALYSIS_FILES) {
                limited = true
                break
            }
            val remaining = MAX_MEDIA_ANALYSIS_FILES - result.size
            val batch = queryMedia(kind, remaining)
            result += batch.files
            limited = limited || batch.limited
        }
        return ScanBatch(result, limited)
    }

    private fun queryMedia(kind: MediaKind, limit: Int): ScanBatch {
        if (limit <= 0) return ScanBatch(emptyList(), true)
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
                arrayOf(MIN_ANALYSIS_BYTES.toString()),
                "${MediaStore.MediaColumns.SIZE} DESC",
            )
        }.getOrNull() ?: return ScanBatch(emptyList(), false)
        val files = mutableListOf<StorageIntelligenceFile>()
        var limited = false
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
            while (it.moveToNext()) {
                if (files.size >= limit) {
                    limited = true
                    break
                }
                val id = it.safeLong(idIndex)
                if (id <= 0L) continue
                val size = it.safeLong(sizeIndex)
                if (size < MIN_ANALYSIS_BYTES) continue
                val modifiedSeconds = it.safeLong(modifiedIndex)
                files += StorageIntelligenceFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名媒体" },
                    sizeBytes = size,
                    mimeType = it.safeString(mimeIndex).orEmpty().ifBlank { kind.defaultMime },
                    modifiedAt = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L,
                    location = it.safeString(pathIndex).orEmpty().ifBlank { kind.label },
                    source = StorageCandidateSource.MediaStore,
                    canDelete = true,
                )
            }
        }
        return ScanBatch(files, limited)
    }

    private fun collectFolderFiles(treeUri: Uri): ScanBatch {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return ScanBatch(emptyList(), false)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryDocumentName(rootUri).ifBlank { "授权目录" }
        val queue = ArrayDeque<TreeNode>()
        queue.add(TreeNode(rootUri, rootName, 0))
        val files = mutableListOf<StorageIntelligenceFile>()
        var visitedFiles = 0
        var limited = false
        while (queue.isNotEmpty()) {
            if (visitedFiles >= MAX_FOLDER_ANALYSIS_FILES) {
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
                    if (visitedFiles >= MAX_FOLDER_ANALYSIS_FILES) {
                        limited = true
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
                    visitedFiles += 1
                    val size = it.safeLong(sizeIndex)
                    if (size < MIN_ANALYSIS_BYTES) continue
                    val flags = it.safeLong(flagsIndex).toInt()
                    files += StorageIntelligenceFile(
                        uri = documentUri.toString(),
                        displayName = displayName,
                        sizeBytes = size,
                        mimeType = mimeType,
                        modifiedAt = it.safeLong(modifiedIndex),
                        location = childPath,
                        source = StorageCandidateSource.AuthorizedFolder,
                        canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                    )
                }
            }
        }
        return ScanBatch(files, limited)
    }

    private fun findExactDuplicates(files: List<StorageIntelligenceFile>): DuplicateSearchResult {
        val sameSizeGroups = files.groupBy { "${it.source.name}:${it.sizeBytes}" }
            .values
            .asSequence()
            .filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * (group.size - 1L) }
            .toList()
        val quickCandidates = mutableListOf<StorageIntelligenceFile>()
        var limited = false
        for (group in sameSizeGroups) {
            if (quickCandidates.size + group.size > MAX_QUICK_HASH_FILES) {
                limited = true
                continue
            }
            quickCandidates += group
        }
        val quickHashes = linkedMapOf<String, MutableList<StorageIntelligenceFile>>()
        var quickHashed = 0
        var skipped = files.count { file -> sameSizeGroups.any { group -> file in group } } - quickCandidates.size
        quickCandidates.forEach { file ->
            val hash = quickHash(file)
            if (hash == null) {
                skipped += 1
            } else {
                quickHashed += 1
                quickHashes.getOrPut("${file.source.name}:${file.sizeBytes}:$hash") { mutableListOf() } += file
            }
        }
        val fullCandidateGroups = quickHashes.values.filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * (group.size - 1L) }
        val exactGroups = mutableListOf<StorageDuplicateGroup>()
        var fullHashed = 0
        var fullBytes = 0L
        for (quickGroup in fullCandidateGroups) {
            val groupBytes = quickGroup.sumOf { it.sizeBytes }
            if (
                fullHashed + quickGroup.size > MAX_FULL_HASH_FILES ||
                fullBytes + groupBytes > MAX_FULL_HASH_BYTES
            ) {
                skipped += quickGroup.size
                limited = true
                continue
            }
            val byFullHash = linkedMapOf<String, MutableList<StorageIntelligenceFile>>()
            quickGroup.forEach { file ->
                val hash = fullHash(file)
                if (hash == null) {
                    skipped += 1
                } else {
                    fullHashed += 1
                    fullBytes += file.sizeBytes
                    byFullHash.getOrPut(hash) { mutableListOf() } += file
                }
            }
            byFullHash.forEach { (hash, exactFiles) ->
                if (exactFiles.size < 2) return@forEach
                val sortedFiles = exactFiles.sortedWith(
                    compareBy<StorageIntelligenceFile> { it.modifiedAt.takeIf { value -> value > 0L } ?: Long.MAX_VALUE }
                        .thenBy { it.displayName.lowercase(Locale.ROOT) }
                        .thenBy { it.uri },
                )
                val keeper = StorageIntelligencePolicy.chooseKeeper(sortedFiles) ?: return@forEach
                val deleteIds = StorageIntelligencePolicy.suggestedDeleteIds(sortedFiles, keeper.stableId)
                exactGroups += StorageDuplicateGroup(
                    id = "${keeper.source.name}:$hash",
                    sizeBytes = keeper.sizeBytes,
                    files = sortedFiles,
                    keepFileId = keeper.stableId,
                    suggestedDeleteIds = deleteIds,
                    recoverableBytes = keeper.sizeBytes * deleteIds.size,
                )
            }
        }
        return DuplicateSearchResult(
            groups = exactGroups.sortedByDescending { it.recoverableBytes },
            quickHashed = quickHashed,
            fullHashed = fullHashed,
            skipped = skipped.coerceAtLeast(0),
            limited = limited,
        )
    }

    private fun quickHash(file: StorageIntelligenceFile): String? {
        val uri = Uri.parse(file.uri)
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(file.sizeBytes).array())
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    val channel = stream.channel
                    val first = ByteArray(QUICK_HASH_CHUNK_BYTES)
                    val firstRead = stream.read(first)
                    if (firstRead > 0) digest.update(first, 0, firstRead)
                    val seekSize = descriptor.statSize.takeIf { it > 0L } ?: file.sizeBytes
                    if (seekSize > QUICK_HASH_CHUNK_BYTES) {
                        runCatching {
                            channel.position((seekSize - QUICK_HASH_CHUNK_BYTES).coerceAtLeast(0L))
                            val last = ByteArray(QUICK_HASH_CHUNK_BYTES)
                            val lastRead = stream.read(last)
                            if (lastRead > 0) digest.update(last, 0, lastRead)
                        }
                    }
                }
            } ?: return null
            digest.toHex()
        }.getOrNull()
    }

    private fun fullHash(file: StorageIntelligenceFile): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(Uri.parse(file.uri))?.use { stream ->
                val buffer = ByteArray(FULL_HASH_BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.toHex()
        }.getOrNull()
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

    private fun MessageDigest.toHex(): String {
        return digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private enum class MediaKind(val label: String, val defaultMime: String) {
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

    private data class TreeNode(val uri: Uri, val path: String, val depth: Int)
    private data class ScanBatch(val files: List<StorageIntelligenceFile>, val limited: Boolean)
    private data class DuplicateSearchResult(
        val groups: List<StorageDuplicateGroup>,
        val quickHashed: Int,
        val fullHashed: Int,
        val skipped: Int,
        val limited: Boolean,
    )
}

class StorageCleanupHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(INTELLIGENCE_PREFS, Context.MODE_PRIVATE)

    fun load(): List<StorageCleanupHistoryEntry> {
        val raw = prefs.getString(HISTORY_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageCleanupHistoryEntry(
                            id = item.optString("id"),
                            createdAt = item.optLong("createdAt"),
                            requestedCount = item.optInt("requestedCount"),
                            deletedCount = item.optInt("deletedCount"),
                            failedCount = item.optInt("failedCount"),
                            releasedBytes = item.optLong("releasedBytes"),
                            label = item.optString("label", "智能清理"),
                        ),
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun add(entry: StorageCleanupHistoryEntry) {
        val updated = (listOf(entry) + load().filterNot { it.id == entry.id }).take(MAX_HISTORY_ITEMS)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("createdAt", item.createdAt)
                    .put("requestedCount", item.requestedCount)
                    .put("deletedCount", item.deletedCount)
                    .put("failedCount", item.failedCount)
                    .put("releasedBytes", item.releasedBytes)
                    .put("label", item.label),
            )
        }
        prefs.edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(HISTORY_KEY).apply()
    }
}
