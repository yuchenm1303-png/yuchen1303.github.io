package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualScreenshotEncoderTest {
    @Test
    fun tallPhoneScreenshotKeepsReadableWidthAtHighResolution() {
        assertEquals(808 to 1800, VisualScreenshotEncoder.targetSize(1200, 2670))
        assertEquals(810 to 1800, VisualScreenshotEncoder.targetSize(1080, 2400))
    }

    @Test
    fun smallScreenshotIsNeverUpscaled() {
        assertEquals(720 to 1280, VisualScreenshotEncoder.targetSize(720, 1280))
    }

    @Test
    fun landscapeAspectRatioIsPreserved() {
        assertEquals(1800 to 1012, VisualScreenshotEncoder.targetSize(2560, 1440))
    }

    @Test
    fun largeEncodedFrameCanJumpDirectlyToMinimumAllowedSide() {
        val estimated = VisualScreenshotEncoder.estimatedLongSide(
            currentLongSide = 1800,
            encodedBytes = 3_000_000,
        )

        assertEquals(1280, estimated)
    }

    @Test
    fun nearBudgetEstimateStillReducesTheLongSide() {
        val estimated = VisualScreenshotEncoder.estimatedLongSide(
            currentLongSide = 1800,
            encodedBytes = 1_300_000,
        )

        assertTrue(estimated in 1280 until 1800)
    }

    @Test
    fun frameWithinBudgetKeepsCurrentResolution() {
        assertEquals(
            1800,
            VisualScreenshotEncoder.estimatedLongSide(
                currentLongSide = 1800,
                encodedBytes = 1_200_000,
            ),
        )
    }
}
