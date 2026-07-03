package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri

internal class StorageIntelligenceCompleteRepository(context: Context) {
    private val mediaCollector = StorageUnlimitedMediaCollector(context.applicationContext)
    private val folderCollector = StorageUnlimitedFolderCollector(context.applicationContext)
    private val duplicateAnalyzer = StorageUnlimitedDuplicateAnalyzer(context.applicationContext)

    fun analyze(includeMedia: Boolean, authorizedTreeUri: Uri?): StorageIntelligenceResult {
        val startedAt = System.currentTimeMillis()
        val files = buildList {
            if (includeMedia) addAll(mediaCollector.collect())
            authorizedTreeUri?.let { addAll(folderCollector.collect(it)) }
        }.distinctBy(StorageIntelligenceFile::stableId)
        val duplicates = duplicateAnalyzer.analyze(files)
        val now = System.currentTimeMillis()
        val oldFiles = files.filter { StorageIntelligencePolicy.isOldFile(it.modifiedAt, it.sizeBytes, now) }
            .sortedByDescending(StorageIntelligenceFile::sizeBytes)
        return StorageIntelligenceResult(
            scannedFileCount = files.size,
            duplicateGroups = duplicates.groups,
            oldFiles = oldFiles,
            quickHashedFileCount = duplicates.quickHashed,
            fullHashedFileCount = duplicates.fullHashed,
            skippedHashFileCount = duplicates.skipped,
            limited = false,
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }
}
