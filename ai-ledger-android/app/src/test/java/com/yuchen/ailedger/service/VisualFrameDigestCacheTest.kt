package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VisualFrameDigestCacheTest {
    @Test
    fun sameFrameReturnsStableDigest() {
        val frame = "YWJj".repeat(2_000)

        val first = VisualFrameDigestCache.digest(frame)
        val second = VisualFrameDigestCache.digest(frame)

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun differentFramesDoNotShareDigest() {
        assertNotEquals(
            VisualFrameDigestCache.digest("YWJj"),
            VisualFrameDigestCache.digest("ZGVm"),
        )
    }
}
