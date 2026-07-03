package com.yuchen.ailedger.service

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val COMPLETE_SIMILAR_HASH_DISTANCE = 7
private const val COMPLETE_SIMILAR_COLOR_DISTANCE = 78
private const val COMPLETE_BLUR_VARIANCE_THRESHOLD = 105.0
private const val COMPLETE_LOW_RESOLUTION_PIXELS = 900_000L
private const val COMPLETE_KB = 1024L

internal data class StorageOrganizationCompleteAnalysis(
    val groups: List<SimilarPhotoGroup>,
    val qualityCandidates: List<StorageOrganizationFile>,
    val hashedCount: Int,
)

internal class StorageOrganizationCompleteAnalyzer(context: Context) {
    private val engine = StorageOrganizationSignatureEngine(context.applicationContext)

    fun analyze(files: List<StorageOrganizationFile>): StorageOrganizationCompleteAnalysis {
        val signatures = files.mapNotNull { file -> engine.create(file)?.let { file to it } }
        val quality = signatures.mapNotNull { (file, signature) ->
            if (StorageMediaOrganizationPolicy.isScreenshot(file.displayName, file.location)) return@mapNotNull null
            val pixels = file.width.toLong() * file.height.toLong()
            when {
                signature.sharpnessVariance < COMPLETE_BLUR_VARIANCE_THRESHOLD && min(file.width, file.height) >= 720 ->
                    file.copy(
                        kind = StorageOrganizationKind.QualityCandidate,
                        risk = StorageReviewRisk.Caution,
                        reviewNote = "缩略图锐度较低，仅为模糊候选",
                    )
                pixels in 1 until COMPLETE_LOW_RESOLUTION_PIXELS && file.sizeBytes >= 300L * COMPLETE_KB ->
                    file.copy(
                        kind = StorageOrganizationKind.QualityCandidate,
                        risk = StorageReviewRisk.Caution,
                        reviewNote = "分辨率较低：${file.width} × ${file.height}",
                    )
                else -> null
            }
        }.distinctBy(StorageOrganizationFile::stableId).sortedByDescending(StorageOrganizationFile::sizeBytes)
        if (signatures.size < 2) return StorageOrganizationCompleteAnalysis(emptyList(), quality, signatures.size)

        val parent = IntArray(signatures.size) { it }
        fun find(value: Int): Int {
            var cursor = value
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

        val buckets = hashMapOf<Int, MutableList<Int>>()
        signatures.forEachIndexed { index, (_, signature) ->
            for (band in 0 until 8) {
                val value = ((signature.dHash ushr (band * 8)) and 0xFFL).toInt()
                buckets.getOrPut((band shl 8) or value) { mutableListOf() } += index
            }
        }
        val candidatePairs = hashSetOf<Long>()
        buckets.values.forEach { indexes ->
            for (first in 0 until indexes.lastIndex) {
                for (second in first + 1 until indexes.size) {
                    val low = min(indexes[first], indexes[second])
                    val high = max(indexes[first], indexes[second])
                    candidatePairs += (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
                }
            }
        }
        candidatePairs.forEach { pair ->
            val firstIndex = (pair ushr 32).toInt()
            val secondIndex = pair.toInt()
            val (firstFile, firstSignature) = signatures[firstIndex]
            val (secondFile, secondSignature) = signatures[secondIndex]
            if (!StorageMediaOrganizationPolicy.dimensionsCompatible(firstFile, secondFile)) return@forEach
            val hashDistance = java.lang.Long.bitCount(firstSignature.dHash xor secondSignature.dHash)
            if (hashDistance > COMPLETE_SIMILAR_HASH_DISTANCE) return@forEach
            val colorDistance = abs(firstSignature.averageRed - secondSignature.averageRed) +
                abs(firstSignature.averageGreen - secondSignature.averageGreen) +
                abs(firstSignature.averageBlue - secondSignature.averageBlue)
            if (colorDistance <= COMPLETE_SIMILAR_COLOR_DISTANCE) union(firstIndex, secondIndex)
        }

        val groups = signatures.indices.groupBy(::find).values.filter { it.size >= 2 }.map { indexes ->
            val groupFiles = indexes.map { signatures[it].first }
                .sortedByDescending(StorageOrganizationFile::modifiedAt)
                .map {
                    it.copy(
                        kind = StorageOrganizationKind.SimilarPhoto,
                        risk = StorageReviewRisk.Caution,
                        reviewNote = "缩略图视觉接近，不代表文件或内容完全相同",
                    )
                }
            var maximumDistance = 0
            for (first in indexes.indices) {
                for (second in first + 1 until indexes.size) {
                    maximumDistance = max(
                        maximumDistance,
                        java.lang.Long.bitCount(
                            signatures[indexes[first]].second.dHash xor signatures[indexes[second]].second.dHash,
                        ),
                    )
                }
            }
            SimilarPhotoGroup(
                id = "similar:${groupFiles.joinToString("|") { it.stableId }.hashCode()}",
                files = groupFiles,
                maxHashDistance = maximumDistance,
            )
        }.sortedByDescending { it.files.sumOf(StorageOrganizationFile::sizeBytes) }
        return StorageOrganizationCompleteAnalysis(groups, quality, signatures.size)
    }
}
