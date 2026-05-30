package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockQuote
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class StockRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadAStock(query: String): StockDetailUiState {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        return runCatching {
            loadFromProxy(normalized, base)
        }.getOrElse { error ->
            base.copy(
                dataSourceLabel = "AKShare代理暂未返回，已回退示例数据",
                errorMessage = error.message ?: "行情代理网络异常"
            )
        }
    }

    private fun loadFromProxy(query: String, base: StockDetailUiState): StockDetailUiState {
        val baseUrl = proxyBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw IllegalStateException("行情代理地址为空")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = httpGet("$baseUrl/api/stock/a-share/detail?query=$encoded", referer = baseUrl, timeoutMs = 45000)
        val obj = JSONObject(body)
        val quoteJson = obj.optJSONObject("quote") ?: throw IllegalStateException("代理行情缺少 quote 字段")
        val quote = quoteFromJson(quoteJson, base.quote)
        val kLines = parseKLines(obj).ifEmpty { base.kLinePoints }
        val minutePoints = parseMinutePoints(obj).ifEmpty { minutePointsFromKLines(kLines, base) }
        return base.copy(
            quote = quote,
            topMetrics = topMetricsFor(quote),
            minutePoints = minutePoints,
            kLinePoints = kLines,
            dataSourceLabel = obj.optString("dataSourceLabel", "AKShare 行情代理 · ${quote.code}"),
            errorMessage = null,
            aiSummary = obj.optString(
                "aiSummary",
                "${quote.name} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。行情来自 AKShare 后端代理；盘口、资金和资讯可继续在代理侧扩展。"
            )
        )
    }

    private fun minutePointsFromKLines(kLines: List<StockKLinePoint>, base: StockDetailUiState): List<StockMinutePoint> {
        val recent = kLines.takeLast(12)
        return recent.mapIndexed { index, point ->
            StockMinutePoint(
                time = when (index) {
                    0 -> "09:30"
                    5 -> "11:30"
                    6 -> "13:00"
                    11 -> "15:00"
                    else -> ""
                },
                price = point.close,
                average = recent.take(index + 1).map { it.close }.average().toFloat(),
                volumeRatio = (point.volume / (recent.maxOfOrNull { it.volume } ?: 1f)).coerceIn(0.05f, 1f)
            )
        }.ifEmpty { base.minutePoints }
    }

    private fun quoteFromJson(json: JSONObject, fallback: StockQuote): StockQuote = StockQuote(
        name = json.optString("name", fallback.name),
        code = json.optString("code", fallback.code),
        market = json.optString("market", fallback.market),
        price = json.optString("price", fallback.price),
        changeAmount = json.optString("changeAmount", fallback.changeAmount),
        changePercent = json.optString("changePercent", fallback.changePercent),
        isRising = json.optBoolean("isRising", !json.optString("changePercent", fallback.changePercent).startsWith("-")),
        previousClose = json.optDouble("previousClose", fallback.previousClose.toDouble()).toFloat(),
        high = json.optString("high", fallback.high),
        low = json.optString("low", fallback.low),
        open = json.optString("open", fallback.open),
        totalMarketValue = json.optString("totalMarketValue", fallback.totalMarketValue),
        floatMarketValue = json.optString("floatMarketValue", fallback.floatMarketValue),
        volumeRatio = json.optString("volumeRatio", fallback.volumeRatio),
        turnoverRate = json.optString("turnoverRate", fallback.turnoverRate),
        peTtm = json.optString("peTtm", fallback.peTtm),
        pb = json.optString("pb", fallback.pb),
        amount = json.optString("amount", fallback.amount),
        popularityRank = json.optString("popularityRank", fallback.popularityRank)
    )

    private fun parseKLines(obj: JSONObject): List<StockKLinePoint> {
        val array = obj.optJSONArray("kLinePoints") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    StockKLinePoint(
                        date = item.optString("date"),
                        open = item.optDouble("open", 0.0).toFloat(),
                        close = item.optDouble("close", 0.0).toFloat(),
                        high = item.optDouble("high", 0.0).toFloat(),
                        low = item.optDouble("low", 0.0).toFloat(),
                        volume = item.optDouble("volume", 0.0).toFloat(),
                        amount = item.optDouble("amount", 0.0).toFloat(),
                        changePercent = item.optString("changePercent", "--")
                    )
                )
            }
        }
    }

    private fun parseMinutePoints(obj: JSONObject): List<StockMinutePoint> {
        val array = obj.optJSONArray("minutePoints") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    StockMinutePoint(
                        time = item.optString("time"),
                        price = item.optDouble("price", 0.0).toFloat(),
                        average = item.optDouble("average", 0.0).toFloat(),
                        volumeRatio = item.optDouble("volumeRatio", 0.0).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    private fun topMetricsFor(quote: StockQuote): List<StockMetric> = listOf(
        StockMetric("高", quote.high, StockTone.Rising),
        StockMetric("低", quote.low, StockTone.Falling),
        StockMetric("开", quote.open),
        StockMetric("市值", quote.totalMarketValue),
        StockMetric("量比", quote.volumeRatio),
        StockMetric("换手", quote.turnoverRate),
        StockMetric("市盈", quote.peTtm),
        StockMetric("成交额", quote.amount),
        StockMetric("人气", quote.popularityRank)
    )

    private fun httpGet(url: String, referer: String, timeoutMs: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Referer", referer)
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
