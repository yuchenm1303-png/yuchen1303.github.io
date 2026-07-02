package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMediaOrganizationPolicyTest {
    @Test
    fun screenshotsRequireDirectoryOrExplicitNameSignal() {
        assertTrue(StorageMediaOrganizationPolicy.isScreenshot("Screenshot_20260702.png", "Pictures/Other"))
        assertTrue(StorageMediaOrganizationPolicy.isScreenshot("IMG_1001.png", "Pictures/Screenshots"))
        assertFalse(StorageMediaOrganizationPolicy.isScreenshot("IMG_1001.png", "DCIM/Camera"))
    }

    @Test
    fun downloadsUseConservativeRiskLevels() {
        val installer = StorageMediaOrganizationPolicy.classifyDownload(
            displayName = "release.apk",
            mimeType = "application/vnd.android.package-archive",
            sizeBytes = 20L * 1024L * 1024L,
        )
        val document = StorageMediaOrganizationPolicy.classifyDownload(
            displayName = "毕业论文.pdf",
            mimeType = "application/pdf",
            sizeBytes = 5L * 1024L * 1024L,
        )
        val tinyUnknown = StorageMediaOrganizationPolicy.classifyDownload(
            displayName = "unknown.bin",
            mimeType = "application/octet-stream",
            sizeBytes = 1L * 1024L * 1024L,
        )

        assertEquals(StorageOrganizationKind.Installer, installer?.first)
        assertEquals(StorageReviewRisk.Low, installer?.second)
        assertEquals(StorageOrganizationKind.Document, document?.first)
        assertEquals(StorageReviewRisk.Caution, document?.second)
        assertNull(tinyUnknown)
    }

    @Test
    fun timedBurstRequiresAtLeastThreeCompatiblePhotos() {
        val first = photo("content://1", 1_000L, 4_000, 3_000)
        val second = photo("content://2", 3_000L, 4_000, 3_000)
        val third = photo("content://3", 6_000L, 4_000, 3_000)

        val groups = StorageMediaOrganizationPolicy.buildBurstGroups(listOf(first, second, third))

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().files.size)
        assertFalse(groups.single().explicitBurstName)
    }

    @Test
    fun timedBurstDoesNotCrossDirectories() {
        val first = photo("content://1", 1_000L, 4_000, 3_000, location = "DCIM/Camera")
        val second = photo("content://2", 2_000L, 4_000, 3_000, location = "Pictures/Imported")
        val third = photo("content://3", 3_000L, 4_000, 3_000, location = "DCIM/Camera")

        assertTrue(StorageMediaOrganizationPolicy.buildBurstGroups(listOf(first, second, third)).isEmpty())
    }

    @Test
    fun incompatibleAspectRatioBreaksTimedBurst() {
        val first = photo("content://1", 1_000L, 4_000, 3_000)
        val second = photo("content://2", 2_000L, 4_000, 3_000)
        val third = photo("content://3", 3_000L, 1_000, 1_000)

        assertTrue(StorageMediaOrganizationPolicy.buildBurstGroups(listOf(first, second, third)).isEmpty())
    }

    @Test
    fun explicitBurstNamesCanFormTwoPhotoGroup() {
        val first = photo("content://1", 1_000L, 4_000, 3_000, "IMG_BURST_0001.jpg")
        val second = photo("content://2", 9_000L, 4_000, 3_000, "IMG_BURST_0002.jpg")

        val group = StorageMediaOrganizationPolicy.buildBurstGroups(listOf(first, second)).singleOrNull()

        assertNotNull(group)
        assertTrue(group!!.explicitBurstName)
    }

    private fun photo(
        uri: String,
        modifiedAt: Long,
        width: Int,
        height: Int,
        name: String = "IMG.jpg",
        location: String = "DCIM/Camera",
    ): StorageOrganizationFile {
        return StorageOrganizationFile(
            uri = uri,
            displayName = name,
            sizeBytes = 3L * 1024L * 1024L,
            mimeType = "image/jpeg",
            modifiedAt = modifiedAt,
            location = "$location/$name",
            source = StorageCandidateSource.MediaStore,
            canDelete = true,
            width = width,
            height = height,
            kind = StorageOrganizationKind.BurstCandidate,
            risk = StorageReviewRisk.Caution,
        )
    }
}
