package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

private const val UNLIMITED_QUICK_HASH_CHUNK_BYTES = 128 * 1024
private const val UNLIMITED_FULL_HASH_BUFFER_BYTES = 256 * 1024

internal data class StorageUnlimitedDuplicateResult(
    val groups: List<StorageDuplicateGroup>,
    val quickHashed: Int,
    val fullHashed: Int,
    val skipped: Int,
)

internal class StorageUnlimitedDuplicateAnalyzer(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun analyze(files: List<StorageIntelligenceFile>): StorageUnlimitedDuplicateResult {
        val sameSizeGroups = files.groupBy { "${it.source.name}:${it.sizeBytes}" }
            .values
            .filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * (group.size - 1L) }
        val quickHashes = linkedMapOf<String, MutableList<StorageIntelligenceFile>>()
        var quickHashed = 0
        var fullHashed = 0
        var skipped = 0
        sameSizeGroups.flatten().forEach { file ->
            val hash = quickHash(file)
            if (hash == null) {
                skipped += 1
            } else {
                quickHashed += 1
                quickHashes.getOrPut("${file.source.name}:${file.sizeBytes}:$hash") { mutableListOf() } += file
            }
        }
        val exactGroups = mutableListOf<StorageDuplicateGroup>()
        quickHashes.values.filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * (group.size - 1L) }
            .forEach { quickGroup ->
                val byFullHash = linkedMapOf<String, MutableList<StorageIntelligenceFile>>()
                quickGroup.forEach { file ->
                    val hash = fullHash(file)
                    if (hash == null) {
                        skipped += 1
                    } else {
                        fullHashed += 1
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
        return StorageUnlimitedDuplicateResult(
            groups = exactGroups.sortedByDescending(StorageDuplicateGroup::recoverableBytes),
            quickHashed = quickHashed,
            fullHashed = fullHashed,
            skipped = skipped,
        )
    }

    private fun quickHash(file: StorageIntelligenceFile): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(file.sizeBytes).array())
        resolver.openFileDescriptor(Uri.parse(file.uri), "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val first = ByteArray(UNLIMITED_QUICK_HASH_CHUNK_BYTES)
                val firstRead = stream.read(first)
                if (firstRead > 0) digest.update(first, 0, firstRead)
                val seekSize = descriptor.statSize.takeIf { it > 0L } ?: file.sizeBytes
                if (seekSize > UNLIMITED_QUICK_HASH_CHUNK_BYTES) {
                    runCatching {
                        stream.channel.position((seekSize - UNLIMITED_QUICK_HASH_CHUNK_BYTES).coerceAtLeast(0L))
                        val last = ByteArray(UNLIMITED_QUICK_HASH_CHUNK_BYTES)
                        val lastRead = stream.read(last)
                        if (lastRead > 0) digest.update(last, 0, lastRead)
                    }
                }
            }
        } ?: return null
        digest.toHex()
    }.getOrNull()

    private fun fullHash(file: StorageIntelligenceFile): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(Uri.parse(file.uri))?.use { stream ->
            val buffer = ByteArray(UNLIMITED_FULL_HASH_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.toHex()
    }.getOrNull()

    private fun MessageDigest.toHex(): String = digest().joinToString("") { byte -> "%02x".format(byte) }
}
