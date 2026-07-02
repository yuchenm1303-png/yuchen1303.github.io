package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageIntelligencePolicyTest {
    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun oldFileRequiresAgeAndMinimumSize() {
        val now = 1_800L * dayMs

        assertTrue(
            StorageIntelligencePolicy.isOldFile(
                modifiedAt = now - 180L * dayMs,
                sizeBytes = 20L * 1024L * 1024L,
                now = now,
            ),
        )
        assertFalse(
            StorageIntelligencePolicy.isOldFile(
                modifiedAt = now - 179L * dayMs,
                sizeBytes = 20L * 1024L * 1024L,
                now = now,
            ),
        )
        assertFalse(
            StorageIntelligencePolicy.isOldFile(
                modifiedAt = now - 300L * dayMs,
                sizeBytes = 19L * 1024L * 1024L,
                now = now,
            ),
        )
    }

    @Test
    fun cameraOriginalIsPreferredAsKeeper() {
        val copied = file(
            uri = "content://copy",
            name = "IMG_0001-copy.jpg",
            modifiedAt = 100L,
            location = "Download/IMG_0001-copy.jpg",
        )
        val cameraOriginal = file(
            uri = "content://camera",
            name = "IMG_0001.jpg",
            modifiedAt = 200L,
            location = "DCIM/Camera/IMG_0001.jpg",
        )

        assertEquals(cameraOriginal.stableId, StorageIntelligencePolicy.chooseKeeper(listOf(copied, cameraOriginal))?.stableId)
    }

    @Test
    fun suggestionNeverSelectsKeeperOrReadOnlyFile() {
        val keeper = file("content://keeper", "a.jpg", 10L, "DCIM/Camera/a.jpg")
        val deletableCopy = file("content://copy", "a-copy.jpg", 20L, "Download/a-copy.jpg")
        val readOnlyCopy = file(
            uri = "content://readonly",
            name = "a-readonly.jpg",
            modifiedAt = 30L,
            location = "Cloud/a.jpg",
            canDelete = false,
        )

        val ids = StorageIntelligencePolicy.suggestedDeleteIds(
            files = listOf(keeper, deletableCopy, readOnlyCopy),
            keepFileId = keeper.stableId,
        )

        assertEquals(setOf(deletableCopy.stableId), ids)
    }

    private fun file(
        uri: String,
        name: String,
        modifiedAt: Long,
        location: String,
        canDelete: Boolean = true,
    ): StorageIntelligenceFile {
        return StorageIntelligenceFile(
            uri = uri,
            displayName = name,
            sizeBytes = 25L * 1024L * 1024L,
            mimeType = "image/jpeg",
            modifiedAt = modifiedAt,
            location = location,
            source = StorageCandidateSource.MediaStore,
            canDelete = canDelete,
        )
    }
}
