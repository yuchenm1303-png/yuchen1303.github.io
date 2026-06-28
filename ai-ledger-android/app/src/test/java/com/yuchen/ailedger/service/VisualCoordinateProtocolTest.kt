package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualCoordinateProtocolTest {
    private val frame = VisualDisplayFrame(1080, 2580)

    @Test
    fun normalizedEdgesMapToAddressablePixelRange() {
        val topLeft = VisualCoordinateProtocol.materializeNormalized(0f, 0f, frame)
        val bottomRight = VisualCoordinateProtocol.materializeNormalized(1f, 1f, frame)

        assertTrue(topLeft.valid)
        assertEquals(0f, topLeft.point!!.x, 0f)
        assertEquals(0f, topLeft.point!!.y, 0f)
        assertTrue(bottomRight.valid)
        assertEquals(1079f, bottomRight.point!!.x, 0f)
        assertEquals(2579f, bottomRight.point!!.y, 0f)
    }

    @Test
    fun materializedPointRemainsExactWhenFrameMatches() {
        val resolved = VisualCoordinateProtocol.resolveForExecution(
            rawX = 987.285f,
            rawY = 2573.842f,
            currentFrame = frame,
            expectedFrame = frame,
            alreadyMaterialized = true,
        )

        assertTrue(resolved.valid)
        assertTrue(resolved.frameMatched)
        assertEquals(987.285f, resolved.point!!.x, 0f)
        assertEquals(2573.842f, resolved.point!!.y, 0f)
        assertEquals("materialized_exact", resolved.reason)
    }

    @Test
    fun changedDisplayFrameRejectsStalePointInsteadOfRescaling() {
        val resolved = VisualCoordinateProtocol.resolveForExecution(
            rawX = 987.285f,
            rawY = 2573.842f,
            currentFrame = VisualDisplayFrame(2580, 1080),
            expectedFrame = frame,
            alreadyMaterialized = true,
        )

        assertFalse(resolved.valid)
        assertFalse(resolved.frameMatched)
        assertTrue(resolved.reason.startsWith("display_frame_changed:"))
    }

    @Test
    fun physicalPointOutsideDisplayIsRejected() {
        val resolved = VisualCoordinateProtocol.resolveForExecution(
            rawX = 1080f,
            rawY = 2580f,
            currentFrame = frame,
            expectedFrame = frame,
            alreadyMaterialized = true,
        )

        assertFalse(resolved.valid)
        assertEquals("materialized_coordinate_out_of_bounds", resolved.reason)
    }

    @Test
    fun nodeFallbackClipsOnlyToRealDisplayEdgeWithoutMargin() {
        val point = VisualCoordinateProtocol.clipPhysicalPoint(1200f, 2700f, frame)!!

        assertEquals(1079f, point.x, 0f)
        assertEquals(2579f, point.y, 0f)
    }
}
