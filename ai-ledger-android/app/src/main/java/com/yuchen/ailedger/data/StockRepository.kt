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
import java.util.Locale
import org.json.JSONObject

class StockRepository {
    fun loadAStock(query: String): StockDetailUiState {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        return runCatching {
            val resolved = resolveAStock(normalized)
            val quote = fetchQuote(resolved)
            val kLines = fetchDailyKLine(resolved).ifEmpty { base.kLinePoints }
            val recent = kLines.takeLast(12)
            val minutePoints = recent.mapIndexed { index, point ->
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
            }
            base.copy(
                quote = quote,
                topMetrics = topMetricsFor(quote),
                minutePoints = minutePoints.ifEmpty { base.minutePoints },
                kLinePoints = kLines,
                dataSourceLabel = "东方财富公开行情 · ${quote.code}",
                errorMessage = null,
                aiSummary = "${quote.name} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。日K与报价已尝试从公开行情源刷新；盘口、资金流、新闻公告等模块先保留骨架，后续继续逐项接入。"
            )
        }.getOrElse { error ->
            base.copy(
                dataSourceLabel = "真实行情加载失败，已回退示例数据",
                errorMessage = error.message ?: "网络或行情源异常"
            )
        }
    }

    private fun resolveAStock(query: String): ResolvedStock {
        val pureCode = query.filter { it.isDigit() }
        if (pureCode.length == 6) return ResolvedStock(code = pureCode, name = pureCode, secid = secidForCode(pureCode), market = marketNameForCode(pureCode))
        val keyword = URLEncoder.encode(query, "UTF-8")
        val url = "https://searchapi.eastmoney.com/api/suggest/get?input=${keyword}&type=14&token=D43BF722C8E33BD1F27132F629F5E0D0"
        val body = httpGet(url)
        val obj = JSONObject(body)
        val data = obj.optJSONObject("QuotationCodeTable")
        val list = data?.optJSONArray("Data")
        val firstQuote = list?.optJSONObject(0) ?: throw IllegalArgumentException("没有找到股票：$query")
        val code = firstQuote.optString("Code").ifBlank { throw IllegalArgumentException("没有找到股票：$query") }
        val name = firstQuote.optString("Name", code)
        val market = firstQuote.optString("MarketType", marketNameForCode(code))
        return ResolvedStock(code = code, name = name, secid = secidForCode(code), market = if (market.contains("SH")) "沪A" else if (market.contains("SZ")) "深A" else marketNameForCode(code))
    }

    private fun fetchQuote(stock: ResolvedStock): StockQuote {
        val url = "https://push2.eastmoney.com/api/qt/stock/get?secid=${stock.secid}&fields=f43,f44,f45,f46,f48,f50,f57,f58,f60,f116,f117,f162,f168,f169,f170"
        val body = httpGet(url)
        val data = JSONObject(body).optJSONObject("data") ?: throw IllegalStateException("行情返回为空")
        val price = scalePrice(data.optDouble("f43", 0.0))
        val previousClose = scalePrice(data.optDouble("f60", 0.0)).toFloatOrNull() ?: 0f
        val changePercent = signedPercent(data.optDouble("f170", 0.0) / 100.0)
        val changeAmountRaw = data.optDouble("f169", 0.0) / 100.0
        val isRising = changeAmountRaw >= 0.0
        return StockQuote(
            name = data.optString("f58", stock.name).ifBlank { stock.name },
            code = data.optString("f57", stock.code).ifBlank { stock.code },
            market = stock.market,
            price = price,
            changeAmount = signedNumber(changeAmountRaw),
            changePercent = changePercent,
            isRising = isRising,
            previousClose = previousClose,
            high = scalePrice(data.optDouble("f44", 0.0)),
            low = scalePrice(data.optDouble("f45", 0.0)),
            open = scalePrice(data.optDouble("f46", 0.0)),
            totalMarketValue = moneyCn(data.optDouble("f116", 0.0)),
            floatMarketValue = moneyCn(data.optDouble("f117", 0.0)),
            volumeRatio = number(data.optDouble("f50", 0.0) / 100.0),
            turnoverRate = percent(data.optDouble("f168", 0.0) / 100.0),
            peTtm = number(data.optDouble("f162", 0.0) / 100.0),
            pb = "--",
            amount = moneyCn(data.optDouble("f48", 0.0)),
            popularityRank = "--"
        )
    }

    private fun fetchDailyKLine(stock: ResolvedStock): List<StockKLinePoint> {
        val url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=${stock.secid}&klt=101&fqt=1&lmt=80&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
        val body = httpGet(url)
        val data = JSONObject(body).optJSONObject("data") ?: return emptyList()
        val klines = data.optJSONArray("klines") ?: return emptyList()
        return buildList {
            for (i in 0 until klines.length()) {
                val parts = klines.optString(i).split(',')
                if (parts.size >= 11) {
                    add(
                        StockKLinePoint(
                            date = parts[0].takeLast(5),
                            open = parts[1].toFloatOrNull() ?: 0f,
                            close = parts[2].toFloatOrNull() ?: 0f,
                            high = parts[3].toFloatOrNull() ?: 0f,
                            low = parts[4].toFloatOrNull() ?: 0f,
                            volume = ((parts[5].toFloatOrNull() ?: 0f) / 10000f).coerceAtLeast(0.01f),
                            amount = ((parts[6].toFloatOrNull() ?: 0f) / 100000000f).coerceAtLeast(0.01f),
                            changePercent = percent((parts[8].toDoubleOrNull() ?: 0.0) / 100.0)
                        )
                    )
                }
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

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Referer", "https://quote.eastmoney.com/")
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun secidForCode(code: String): String = when {
        code.startsWith("6") || code.startsWith("9") -> "1.$code"
        code.startsWith("8") || code.startsWith("4") -> "0.$code"
        else -> "0.$code"
    }

    private fun marketNameForCode(code: String): String = when {
        code.startsWith("6") || code.startsWith("9") -> "沪A"
        code.startsWith("8") || code.startsWith("4") -> "北交所"
        else -> "深A"
    }

    private fun scalePrice(value: Double): String = if (value <= 0.0 || value > 100000000) "--" else String.format(Locale.US, "%.2f", value / 100.0)
    private fun number(value: Double): String = if (value <= 0.0 || value > 100000000) "--" else String.format(Locale.US, "%.2f", value)
    private fun signedNumber(value: Double): String = String.format(Locale.US, "%+.2f", value)
    private fun percent(value: Double): String = if (value == 0.0) "--" else String.format(Locale.US, "%.2f%%", value * 100.0)
    private fun signedPercent(value: Double): String = String.format(Locale.US, "%+.2f%%", value * 100.0)
    private fun moneyCn(value: Double): String = when {
        value <= 0.0 || value > 9.0E18 -> "--"
        value >= 100000000.0 -> String.format(Locale.US, "%.2f亿", value / 100000000.0)
        value >= 10000.0 -> String.format(Locale.US, "%.2f万", value / 10000.0)
        else -> String.format(Locale.US, "%.0f", value)
    }

    private data class ResolvedStock(val code: String, val name: String, val secid: String, val market: String)
}
