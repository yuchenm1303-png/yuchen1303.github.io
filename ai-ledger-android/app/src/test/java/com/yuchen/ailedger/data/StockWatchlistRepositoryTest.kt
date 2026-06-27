package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockWatchlistRepositoryTest {
    @Test
    fun normalizationKeepsValidDistinctStocksInDisplayOrder() {
        val result = normalizeWatchlistItems(
            listOf(
                StockWatchlistItem(" 600519 ", " 贵州茅台 ", "沪A", 8),
                StockWatchlistItem("600519", "重复项", "沪A", 2),
                StockWatchlistItem("000001", "平安银行", "深A", 7),
                StockWatchlistItem("12", "无效代码", "", 0)
            )
        )

        assertEquals(listOf("600519", "000001"), result.map { it.code })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
        assertEquals("贵州茅台", result.first().name)
    }

    @Test
    fun normalizationCapsWatchlistAtThreeHundredItems() {
        val items = (0 until 350).map { index ->
            StockWatchlistItem(
                code = index.toString().padStart(6, '0'),
                name = "股票$index"
            )
        }

        val result = normalizeWatchlistItems(items)

        assertEquals(300, result.size)
        assertTrue(result.zipWithNext().all { (left, right) -> right.sortOrder == left.sortOrder + 1 })
    }
}
