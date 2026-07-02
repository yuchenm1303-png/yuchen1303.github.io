package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleBackdropDrawRegionTest {
    @Test
    fun topOverflowMovesDestinationInsteadOfFreezingTextureAtTopEdge() {
        val region = resolveRegion(
            sampleOffset = Offset(120f, -20f),
            sampleSize = Size(100f, 100f),
            destinationSize = Size(100f, 100f),
        )

        requireNotNull(region)
        assertEquals(IntOffset(60, 0), region.sourceOffset)
        assertEquals(IntSize(50, 40), region.sourceSize)
        assertEquals(IntOffset(0, 20), region.destinationOffset)
        assertEquals(IntSize(100, 80), region.destinationSize)
    }

    @Test
    fun bottomOverflowCropsSourceAndDestinationByTheSameVisibleFraction() {
        val region = resolveRegion(
            sampleOffset = Offset(120f, 950f),
            sampleSize = Size(100f, 100f),
            destinationSize = Size(100f, 100f),
        )

        requireNotNull(region)
        assertEquals(IntOffset(60, 475), region.sourceOffset)
        assertEquals(IntSize(50, 25), region.sourceSize)
        assertEquals(IntOffset.Zero, region.destinationOffset)
        assertEquals(IntSize(100, 50), region.destinationSize)
    }

    @Test
    fun transformedDestinationKeepsVisibleSampleAligned() {
        val region = resolveRegion(
            sampleOffset = Offset(-25f, -20f),
            sampleSize = Size(100f, 100f),
            destinationSize = Size(200f, 50f),
        )

        requireNotNull(region)
        assertEquals(IntOffset.Zero, region.sourceOffset)
        assertEquals(IntSize(38, 40), region.sourceSize)
        assertEquals(IntOffset(50, 10), region.destinationOffset)
        assertEquals(IntSize(150, 40), region.destinationSize)
    }

    @Test
    fun completelyOffscreenSampleProducesNoDrawRegion() {
        val region = resolveRegion(
            sampleOffset = Offset(120f, -120f),
            sampleSize = Size(100f, 100f),
            destinationSize = Size(100f, 100f),
        )

        assertNull(region)
    }

    private fun resolveRegion(
        sampleOffset: Offset,
        sampleSize: Size,
        destinationSize: Size,
    ): VisibleBackdropDrawRegion? = resolveVisibleBackdropDrawRegion(
        sampleOffset = sampleOffset,
        sampleSize = sampleSize,
        destinationSize = destinationSize,
        backdropWidthPx = 1_000f,
        backdropHeightPx = 1_000f,
        textureScale = 0.5f,
        textureWidthPx = 500,
        textureHeightPx = 500,
    )
}
