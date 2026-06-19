package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
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
}
