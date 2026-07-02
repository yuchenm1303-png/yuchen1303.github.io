package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageCandidateClassifierTest {
    @Test
    fun installerPackagesAreAlwaysReviewCandidates() {
        val result = StorageCandidateClassifier.classify(
            displayName = "release.xapk",
            mimeType = "application/octet-stream",
            sizeBytes = 2L * 1024L * 1024L,
            source = StorageCandidateSource.AuthorizedFolder,
        )

        assertEquals(StorageCandidateKind.Installer, result)
    }

    @Test
    fun ordinaryDocumentsAreNeverCalledJunkByNameAlone() {
        val result = StorageCandidateClassifier.classify(
            displayName = "毕业论文.pdf",
            mimeType = "application/pdf",
            sizeBytes = 40L * 1024L * 1024L,
            source = StorageCandidateSource.AuthorizedFolder,
        )

        assertNull(result)
    }

    @Test
    fun largeMediaUsesConservativeThresholds() {
        assertNull(
            StorageCandidateClassifier.classify(
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                sizeBytes = 99L * 1024L * 1024L,
                source = StorageCandidateSource.MediaStore,
            ),
        )
        assertEquals(
            StorageCandidateKind.LargeVideo,
            StorageCandidateClassifier.classify(
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                sizeBytes = 100L * 1024L * 1024L,
                source = StorageCandidateSource.MediaStore,
            ),
        )
    }

    @Test
    fun genericLargeFilesRequireExplicitFolderAuthorization() {
        val size = 150L * 1024L * 1024L
        assertNull(
            StorageCandidateClassifier.classify(
                displayName = "dataset.bin",
                mimeType = "application/octet-stream",
                sizeBytes = size,
                source = StorageCandidateSource.MediaStore,
            ),
        )
        assertEquals(
            StorageCandidateKind.LargeFile,
            StorageCandidateClassifier.classify(
                displayName = "dataset.bin",
                mimeType = "application/octet-stream",
                sizeBytes = size,
                source = StorageCandidateSource.AuthorizedFolder,
            ),
        )
    }

    @Test
    fun temporaryExtensionStillRequiresMinimumSize() {
        assertNull(
            StorageCandidateClassifier.classify(
                displayName = "trace.log",
                mimeType = "text/plain",
                sizeBytes = 512L * 1024L,
                source = StorageCandidateSource.AuthorizedFolder,
            ),
        )
        assertEquals(
            StorageCandidateKind.Temporary,
            StorageCandidateClassifier.classify(
                displayName = "trace.log",
                mimeType = "text/plain",
                sizeBytes = 2L * 1024L * 1024L,
                source = StorageCandidateSource.AuthorizedFolder,
            ),
        )
    }
}
