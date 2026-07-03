package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageSpecialCleanupPolicyTest {
    private val now = 200L * 24L * 60L * 60L * 1000L

    @Test
    fun installerIsAlwaysClassifiedAsInstaller() {
        assertEquals(
            StorageSpecialCleanupKind.Installer,
            StorageSpecialCleanupPolicy.classifyDownload(
                displayName = "release.xapk",
                sizeBytes = 12L,
                modifiedAt = now,
                now = now,
            ),
        )
    }

    @Test
    fun installerOutsideDownloadFolderIsStillFoundGlobally() {
        assertEquals(
            StorageSpecialCleanupKind.Installer,
            StorageSpecialCleanupPolicy.classifyGlobalDownload(
                location = "/storage/emulated/0/BrowserPackages/release.apk",
                displayName = "release.apk",
                sizeBytes = 20L,
                modifiedAt = now,
                now = now,
            ),
        )
    }

    @Test
    fun regularFileInsideDownloadFolderIsIncluded() {
        assertEquals(
            StorageSpecialCleanupKind.OtherDownload,
            StorageSpecialCleanupPolicy.classifyGlobalDownload(
                location = "/storage/emulated/0/Download/manual.pdf",
                displayName = "manual.pdf",
                sizeBytes = 20L,
                modifiedAt = now,
                now = now,
            ),
        )
    }

    @Test
    fun unrelatedRegularFileOutsideDownloadFolderIsIgnored() {
        assertNull(
            StorageSpecialCleanupPolicy.classifyGlobalDownload(
                location = "/storage/emulated/0/Documents/manual.pdf",
                displayName = "manual.pdf",
                sizeBytes = 20L,
                modifiedAt = now,
                now = now,
            ),
        )
    }

    @Test
    fun stalePartialDownloadIsLowRiskJunk() {
        assertEquals(
            StorageSpecialCleanupKind.PartialDownload,
            StorageSpecialCleanupPolicy.classifyJunk(
                displayName = "video.mp4.crdownload",
                sizeBytes = 20L,
                modifiedAt = now - 4L * 24L * 60L * 60L * 1000L,
                now = now,
            ),
        )
    }

    @Test
    fun recentTemporaryFileIsNotJunkYet() {
        assertNull(
            StorageSpecialCleanupPolicy.classifyJunk(
                displayName = "session.tmp",
                sizeBytes = 1024L,
                modifiedAt = now - 2L * 24L * 60L * 60L * 1000L,
                now = now,
            ),
        )
    }

    @Test
    fun oldZeroByteFileIsClassified() {
        assertEquals(
            StorageSpecialCleanupKind.ZeroByte,
            StorageSpecialCleanupPolicy.classifyJunk(
                displayName = "empty.dat",
                sizeBytes = 0L,
                modifiedAt = now - 2L * 24L * 60L * 60L * 1000L,
                now = now,
            ),
        )
    }
}
