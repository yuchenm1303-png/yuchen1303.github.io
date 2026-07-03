package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val INTELLIGENCE_PREFS = "storage_intelligence"
private const val HISTORY_KEY = "cleanup_history"
private const val OLD_FILE_MIN_BYTES = 20L * 1024L * 1024L
private const val OLD_FILE_AGE_MS = 180L * 24L * 60L * 60L * 1000L

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
    private val completeRepository = StorageIntelligenceCompleteRepository(appContext)

    fun analyze(
        includeMedia: Boolean,
        authorizedTreeUri: Uri?,
    ): StorageIntelligenceResult {
        return completeRepository.analyze(includeMedia, authorizedTreeUri)
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
        val updated = listOf(entry) + load().filterNot { it.id == entry.id }
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
