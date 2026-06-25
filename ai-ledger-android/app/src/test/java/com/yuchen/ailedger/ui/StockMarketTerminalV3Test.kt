package com.yuchen.ailedger.ui

import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMinutePoint
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMarketTerminalV3Test {
    @Test
    fun auctionPhasesUseOnlyRealOpeningAndClosingSegments() {
        val points = listOf(
            StockMinutePoint("2026-06-25 09:15", 10.05f, 10.05f, 0.20f),
            StockMinutePoint("2026-06-25 09:25", 10.20f, 10.12f, 0.75f),
            StockMinutePoint("2026-06-25 09:30", 10.18f, 10.13f, 0.40f),
            StockMinutePoint("2026-06-25 14:57", 10.30f, 10.22f, 0.35f),
            StockMinutePoint("2026-06-25 15:00", 10.40f, 10.24f, 1.00f)
        )

        val phases = buildStockAuctionPhasesV3(points, previousClose = 10.00f)

        assertEquals(2, phases.size)
        assertEquals("开盘竞价", phases[0].label)
        assertTrue(phases[0].available)
        assertEquals(10.20f, phases[0].price ?: 0f, 0.0001f)
        assertEquals(2.00f, phases[0].changePercent ?: 0f, 0.0001f)
        assertEquals("尾盘竞价", phases[1].label)
        assertTrue(phases[1].available)
        assertEquals(10.40f, phases[1].price ?: 0f, 0.0001f)
        assertTrue((phases[1].changePercent ?: 0f) > 0f)
    }

    @Test
    fun missingAuctionPointsStayUnavailable() {
        val phases = buildStockAuctionPhasesV3(
            listOf(StockMinutePoint("2026-06-25 10:30", 10f, 10f, 0.3f)),
            previousClose = 9.9f
        )

        assertFalse(phases[0].available)
        assertFalse(phases[1].available)
        assertEquals(null, phases[0].price)
        assertEquals(null, phases[1].price)
    }

    @Test
    fun technicalIndicatorsKeepCandleAlignmentAndFiniteValues() {
        val candles = (0 until 40).map { index ->
            val close = 10f + index * 0.05f + if (index % 4 == 0) 0.10f else -0.03f
            StockKLinePoint(
                date = "06-${(index + 1).toString().padStart(2, '0')}",
                open = close - 0.04f,
                close = close,
                high = close + 0.12f,
                low = close - 0.10f,
                volume = 100f + index * 8f,
                amount = 1000f + index * 20f,
                changePercent = "0.50%"
            )
        }

        val macd = buildStockMacdSeriesV3(candles)
        val kdj = buildStockKdjSeriesV3(candles)
        val rsi = buildStockRsiSeriesV3(candles)

        assertEquals(candles.size, macd.size)
        assertEquals(candles.size, kdj.size)
        assertEquals(candles.size, rsi.size)
        assertTrue(macd.all { point ->
            listOf(point.primary, point.secondary, point.histogram)
                .filterNotNull()
                .all { it.isFinite() && abs(it) < 10000f }
        })
        assertTrue(kdj.all { point ->
            listOf(point.primary, point.secondary, point.tertiary)
                .filterNotNull()
                .all { it.isFinite() }
        })
        assertTrue(rsi.drop(6).mapNotNull { it.primary }.all { it in 0f..100f })
    }
}
