package com.yuchen.ailedger.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMarketSnapshotParserTest {
    @Test
    fun parsesIndicesBreadthBoardsAndSectorsFromStagePayload() {
        val payload = JSONObject(
            """
            {
              "status": "ok",
              "updatedAt": "2026-06-30T12:00:00Z",
              "warnings": ["test-warning"],
              "indices": {
                "status": "ok",
                "source": "eastmoney_quote",
                "items": [
                  {"name": "上证指数", "price": "3000.12", "changePercent": "+0.52%"}
                ]
              },
              "marketBreadth": {
                "status": "ok",
                "source": "eastmoney_market",
                "items": {
                  "upCount": 3200,
                  "downCount": 1700,
                  "flatCount": 100,
                  "limitUpCount": 88,
                  "limitDownCount": 4,
                  "redRate": 64.0,
                  "marketAmount": "1.2万亿"
                }
              },
              "sentiment": {
                "status": "ok",
                "source": "derived",
                "items": {"sentimentTemperature": 71.5, "sentimentLevel": "偏热"}
              },
              "gainers": {
                "status": "ok",
                "source": "eastmoney_market",
                "items": [
                  {"code": "600000", "name": "浦发银行", "price": "10.20", "changePercent": "+5.00%"}
                ]
              },
              "sectorHotRanking": {
                "status": "ok",
                "source": "eastmoney_sector",
                "items": [
                  {"sectorCode": "BK0001", "sectorName": "测试板块", "changePercent": "+2.10%"}
                ]
              }
            }
            """.trimIndent()
        )

        val snapshot = StockMarketSnapshotParser.parse(payload)

        assertEquals(1, snapshot.indices.size)
        assertEquals("上证指数", snapshot.indices.first().name)
        assertEquals(3200, snapshot.marketBreadth.upCount)
        assertEquals("偏热", snapshot.sentiment.level)
        assertEquals(1, snapshot.boards.size)
        assertEquals("涨幅榜", snapshot.boards.first().title)
        assertEquals(1, snapshot.sectors.size)
        assertTrue(snapshot.warnings.contains("test-warning"))
    }

    @Test
    fun unwrapsNestedDataPayloadAndKeepsModuleMeta() {
        val payload = JSONObject(
            """
            {
              "data": {
                "status": "ok",
                "indices": {
                  "status": "stale",
                  "source": "cache",
                  "cacheAgeMs": 9000,
                  "items": [
                    {"name": "创业板指", "price": "2100", "changePercent": "-0.20%"}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val snapshot = StockMarketSnapshotParser.parse(payload)

        assertEquals("创业板指", snapshot.indices.single().name)
        assertEquals("cache", snapshot.indicesMeta.source)
        assertEquals(9000L, snapshot.indicesMeta.cacheAgeMs)
    }
}
