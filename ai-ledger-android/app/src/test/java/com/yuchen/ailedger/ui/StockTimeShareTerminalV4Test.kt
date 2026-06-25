package com.yuchen.ailedger.ui

import com.yuchen.ailedger.model.StockMinutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockTimeShareTerminalV4Test {
    @Test
    fun auctionCoverageRecognizesRealOpeningAndClosingPoints() {
        val points = listOf(
            StockMinutePoint("2026-06-25 09:15", 15.70f, 15.70f, 0.10f),
            StockMinutePoint("2026-06-25 09:25", 15.69f, 15.69f, 0.40f),
            StockMinutePoint("2026-06-25 09:30", 15.60f, 15.62f, 0.50f),
            StockMinutePoint("2026-06-25 14:57", 15.23f, 15.33f, 0.30f),
            StockMinutePoint("2026-06-25 15:00", 15.24f, 15.33f, 1.00f)
        )

        val coverage = buildStockAuctionCoverageV4(
            points = points,
            openPrice = 15.69f,
            latestPrice = 15.24f,
            isFiveDay = false
        )

        assertEquals(2, coverage.openingPointCount)
        assertEquals(2, coverage.closingPointCount)
        assertFalse(coverage.useOpeningPriceFallback)
        assertFalse(coverage.useClosingPriceFallback)
    }

    @Test
    fun missingAuctionTraceUsesOnlyRealOpeningAndClosingPricesAsFallback() {
        val points = listOf(
            StockMinutePoint("2026-06-25 09:30", 15.69f, 15.69f, 0.50f),
            StockMinutePoint("2026-06-25 14:56", 15.23f, 15.33f, 0.30f)
        )

        val coverage = buildStockAuctionCoverageV4(
            points = points,
            openPrice = 15.69f,
            latestPrice = 15.24f,
            isFiveDay = false
        )

        assertEquals(0, coverage.openingPointCount)
        assertEquals(0, coverage.closingPointCount)
        assertTrue(coverage.useOpeningPriceFallback)
        assertTrue(coverage.useClosingPriceFallback)
    }

    @Test
    fun sessionAxisReservesVisibleSpaceForBothAuctions() {
        val openStart = sessionXFractionV4(9 * 60 + 15)
        val openEnd = sessionXFractionV4(9 * 60 + 25)
        val morningStart = sessionXFractionV4(9 * 60 + 30)
        val closeStart = sessionXFractionV4(14 * 60 + 57)
        val closeEnd = sessionXFractionV4(15 * 60)

        assertEquals(0f, openStart, 0.0001f)
        assertTrue(openEnd >= 0.13f)
        assertEquals(openEnd, morningStart, 0.0001f)
        assertTrue(closeStart >= 0.94f)
        assertEquals(1f, closeEnd, 0.0001f)
    }
}
