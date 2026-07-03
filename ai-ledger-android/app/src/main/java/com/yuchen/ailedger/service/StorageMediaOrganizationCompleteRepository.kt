package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

internal class StorageMediaOrganizationCompleteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val mediaCollector = StorageUnlimitedMediaCollector(appContext)
    private val folderCollector = StorageUnlimitedFolderCollector(appContext)
    private val analyzer = StorageOrganizationCompleteAnalyzer(appContext)

    fun analyze(
        includeMedia: Boolean,
        authorizedTreeUri: Uri?,
        ignoreRules: StorageOrganizationIgnoreRules,
    ): StorageOrganizationSnapshot {
        val startedAt = System.currentTimeMillis()
        val images = if (includeMedia) {
            mediaCollector.collect().asSequence()
                .filter { it.mimeType.startsWith("image/", ignoreCase = true) }
                .map(::toOrganizationImage)
                .filterNot(ignoreRules::isIgnored)
                .toList()
        } else emptyList()
        val screenshots = images.filter { StorageMediaOrganizationPolicy.isScreenshot(it.displayName, it.location) }
            .map {
                it.copy(
                    kind = StorageOrganizationKind.Screenshot,
                    risk = StorageReviewRisk.Review,
                    reviewNote = "依据截图目录或文件名识别",
                )
            }.sortedByDescending(StorageOrganizationFile::modifiedAt)
        val bursts = StorageMediaOrganizationPolicy.buildBurstGroups(
            images.map {
                it.copy(
                    kind = StorageOrganizationKind.BurstCandidate,
                    risk = StorageReviewRisk.Caution,
                    reviewNote = "时间相邻或文件名含连拍标记，不代表内容重复",
                )
            },
        )
        val visual = analyzer.analyze(images)
        val folderFiles = authorizedTreeUri?.let(folderCollector::collect).orEmpty()
            .mapNotNull(::toOrganizationFolderFile)
            .filterNot(ignoreRules::isIgnored)
        val categories = folderFiles.groupBy(StorageOrganizationFile::kind)
            .map { (kind, files) ->
                StorageDownloadCategory(kind, files.sortedByDescending(StorageOrganizationFile::sizeBytes))
            }
            .sortedByDescending(StorageDownloadCategory::totalBytes)
        return StorageOrganizationSnapshot(
            similarGroups = visual.groups,
            screenshots = screenshots,
            burstGroups = bursts,
            qualityCandidates = visual.qualityCandidates,
            downloadCategories = categories,
            indexedImageCount = images.size,
            perceptualHashedCount = visual.hashedCount,
            indexedFolderCount = folderFiles.size,
            limited = false,
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    private fun toOrganizationImage(file: StorageIntelligenceFile): StorageOrganizationFile {
        val dimensions = queryDimensions(Uri.parse(file.uri))
        return StorageOrganizationFile(
            uri = file.uri,
            displayName = file.displayName,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
            modifiedAt = file.modifiedAt,
            location = file.location,
            source = file.source,
            canDelete = file.canDelete,
            width = dimensions.first,
            height = dimensions.second,
            kind = StorageOrganizationKind.SimilarPhoto,
            risk = StorageReviewRisk.Caution,
        )
    }

    private fun toOrganizationFolderFile(file: StorageIntelligenceFile): StorageOrganizationFile? {
        val classification = StorageMediaOrganizationPolicy.classifyDownload(
            displayName = file.displayName,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
        ) ?: return null
        val dimensions = if (file.mimeType.startsWith("image/", ignoreCase = true)) {
            queryDimensions(Uri.parse(file.uri))
        } else 0 to 0
        return StorageOrganizationFile(
            uri = file.uri,
            displayName = file.displayName,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
            modifiedAt = file.modifiedAt,
            location = file.location,
            source = file.source,
            canDelete = file.canDelete,
            width = dimensions.first,
            height = dimensions.second,
            kind = classification.first,
            risk = classification.second,
            reviewNote = classification.second.explanation,
        )
    }

    private fun queryDimensions(uri: Uri): Pair<Int, Int> {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0 to 0
                val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val width = if (widthIndex >= 0 && !cursor.isNull(widthIndex)) cursor.getInt(widthIndex) else 0
                val height = if (heightIndex >= 0 && !cursor.isNull(heightIndex)) cursor.getInt(heightIndex) else 0
                width.coerceAtLeast(0) to height.coerceAtLeast(0)
            } ?: (0 to 0)
        }.getOrDefault(0 to 0)
    }
}
