package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockKLinePoint
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class StockNativeIndexKlineRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadDaily(query: String, limit: Int = 600): Result<List<StockKLinePoint>> = runCatching {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val root = JSONObject(
            StockHttpClient.get(
                url = "${proxyBaseUrl.trimEnd('/')}/api/stock/a-share/kline?query=$encoded&instrument=index&period=daily&limit=${limit.coerceIn(120, 1000)}",
                timeoutMs = 32_000,
                emptyMessage = "指数K线接口返回为空",
                microCacheMs = 10_000
            )
        )
        parseRows(root.optJSONArray("kLinePoints") ?: JSONArray())
    }

    private fun parseRows(rows: JSONArray): List<StockKLinePoint> = buildList {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val open = number(item, "open")?.toFloat() ?: continue
            val close = number(item, "close")?.toFloat() ?: continue
            if (open <= 0f || close <= 0f) continue
            add(
                StockKLinePoint(
                    date = item.optString("date"),
                    open = open,
                    close = close,
                    high = number(item, "high")?.toFloat() ?: maxOf(open, close),
                    low = number(item, "low")?.toFloat() ?: minOf(open, close),
                    volume = number(item, "volume")?.toFloat() ?: 0f,
                    amount = number(item, "amount")?.toFloat() ?: 0f,
                    changePercent = item.optString("changePercent", "--"),
                    amplitude = item.optString("amplitude", "--"),
                    changeAmount = item.optString("changeAmount", "--"),
                    turnoverRate = item.optString("turnoverRate", "--")
                )
            )
        }
    }

    private fun number(item: JSONObject, key: String): Double? = when (val raw = item.opt(key)) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", "").replace("%", "").toDoubleOrNull()
        else -> null
    }
}
