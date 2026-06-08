package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockQuote
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.json.JSONArray
import org.json.JSONObject

class StockRealtimeRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    private val requestExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "ai-ledger-stock-realtime-http").apply { isDaemon = true }
    }

    fun loadRealtimeFrame(query: String, current: StockDetailUiState, minuteDays: Int = 1): Result<StockDetailUiState> = runCatching {
        val normalized = query.trim().ifBlank { current.quote.code }
        val encoded = encode(normalized)
        val safeDays = minuteDays.coerceIn(1, 5)

        val quote = runCatching {
            val quoteBody = httpGet("${baseUrl()}/api/stock/a-share/quotes?codes=$encoded", timeoutMs = 2400)
            val quoteJson = parseQuoteObjects(JSONObject(quoteBody)).firstOrNull() ?: throw IllegalStateException("quotes empty")
            quoteFromJson(quoteJson, current.quote)
        }.recoverCatching {
            val quoteBody = httpGet("${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encoded", timeoutMs = 2400)
            val quoteJson = parseQuoteObjects(JSONObject(quoteBody)).firstOrNull() ?: throw IllegalStateException("crawl quotes empty")
            quoteFromJson(quoteJson, current.quote)
        }.getOrElse { current.quote }

        val minuteObj = runCatching {
            JSONObject(httpGet("${baseUrl()}/api/stock/a-share/minute?query=$encoded&ndays=$safeDays&days=$safeDays", timeoutMs = 3000))
        }.recoverCatching {
            JSONObject(httpGet("${baseUrl()}/api/stock/crawl/a-share/minute?query=$encoded&ndays=$safeDays&days=$safeDays", timeoutMs = 3000))
        }.getOrNull()

        val payload = minuteObj?.let { payloadObject(it) }
        val minuteQuote = payload?.let { parseQuoteObjects(it).firstOrNull() }?.let { quoteFromJson(it, quote) } ?: quote
        val minutePoints = payload?.let { parseMinutePoints(it) }.orEmpty().ifEmpty { current.minutePoints }
        val sellLevels = payload?.let { parseOrderLevels(it, listOf("sellLevels", "askLevels", "asks"), true) }.orEmpty().ifEmpty { current.sellLevels }
        val buyLevels = payload?.let { parseOrderLevels(it, listOf("buyLevels", "bidLevels", "bids"), false) }.orEmpty().ifEmpty { current.buyLevels }
        val tradeTicks = payload?.let { parseTradeTicks(it) }.orEmpty().ifEmpty { ticksFromMinute(minutePoints, minuteQuote, current.tradeTicks) }

        current.copy(
            quote = minuteQuote,
            topMetrics = topMetricsFor(minuteQuote),
            minutePoints = minutePoints,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = tradeTicks,
            dataSourceLabel = if (safeDays >= 5) "A股实时行情代理 · 1s quotes/5d-minute/depth" else "A股实时行情代理 · 1s quotes/minute/depth"
        )
    }

    private fun baseUrl(): String {
        val base = proxyBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("行情代理地址为空")
        return base
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun payloadObject(obj: JSONObject): JSONObject {
        return obj.optJSONObject("data") ?: obj.optJSONObject("result") ?: obj.optJSONObject("payload") ?: obj
    }

    private fun findArray(obj: JSONObject, keys: List<String>): JSONArray? {
        keys.forEach { key -> obj.optJSONArray(key)?.let { return it } }
        val data = obj.opt("data")
        if (data is JSONArray) return data
        if (data is JSONObject && data != obj) findArray(data, keys)?.let { return it }
        val result = obj.opt("result")
        if (result is JSONArray) return result
        if (result is JSONObject && result != obj) findArray(result, keys)?.let { return it }
        return null
    }

    private fun parseQuoteObjects(obj: JSONObject): List<JSONObject> {
        val payload = payloadObject(obj)
        val directQuote = payload.optJSONObject("quote")
        if (directQuote != null) return listOf(directQuote)
        val array = findArray(payload, listOf("quotes", "items", "list", "records", "stocks", "data", "result"))
        if (array != null) {
            return buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(it) }
            }
        }
        val nestedData = payload.optJSONObject("data") ?: payload.optJSONObject("result")
        if (nestedData != null) {
            val result = mutableListOf<JSONObject>()
            val keys = nestedData.keys()
            while (keys.hasNext()) {
                val value = nestedData.opt(keys.next())
                if (value is JSONObject) result.add(value)
            }
            if (result.isNotEmpty()) return result
        }
        return if (payload.has("code") || payload.has("name") || payload.has("price")) listOf(payload) else emptyList()
    }

    private fun parseMinutePoints(obj: JSONObject): List<StockMinutePoint> {
        val array = findArray(obj, listOf("minutePoints", "minutes", "items", "data", "result")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val price = firstDouble(item, "price", "close", "p", "最新价") ?: continue
                if (price <= 0f) continue
                add(
                    StockMinutePoint(
                        time = firstText(item, "time", "minute", "t", "datetime", "dateTime", "时间") ?: "",
                        price = price,
                        average = firstDouble(item, "average", "avg", "avgPrice", "均价") ?: price,
                        volumeRatio = (firstDouble(item, "volumeRatio", "ratio") ?: firstDouble(item, "volume", "vol") ?: 0.0f).coerceIn(0.02f, 1f)
                    )
                )
            }
        }
    }

    private fun parseOrderLevels(obj: JSONObject, keys: List<String>, isAsk: Boolean): List<StockOrderLevel> {
        val array = findArray(obj, keys) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    StockOrderLevel(
                        label = firstText(item, "label", "name") ?: if (isAsk) "卖${i + 1}" else "买${i + 1}",
                        price = firstText(item, "price", "p") ?: "--",
                        volume = firstText(item, "volume", "qty", "vol") ?: "--",
                        isAsk = item.optBoolean("isAsk", isAsk)
                    )
                )
            }
        }
    }

    private fun parseTradeTicks(obj: JSONObject): List<StockTradeTick> {
        val array = findArray(obj, listOf("tradeTicks", "ticks", "deals", "items")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val direction = firstText(item, "direction", "side", "type") ?: "--"
                add(
                    StockTradeTick(
                        time = firstText(item, "time", "t") ?: "--",
                        price = firstText(item, "price", "p") ?: "--",
                        volume = firstText(item, "volume", "qty", "vol") ?: "--",
                        direction = direction,
                        isBuy = item.optBoolean("isBuy", direction.contains("买") || direction.equals("buy", ignoreCase = true))
                    )
                )
            }
        }
    }

    private fun ticksFromMinute(points: List<StockMinutePoint>, quote: StockQuote, fallback: List<StockTradeTick>): List<StockTradeTick> {
        if (points.isEmpty()) return fallback
        return points.takeLast(8).reversed().mapIndexed { index, point ->
            val previous = points.getOrNull(points.lastIndex - index - 1)?.price ?: quote.previousClose
            val isBuy = point.price >= previous
            StockTradeTick(
                time = point.time.ifBlank { "--" },
                price = "%.2f".format(point.price),
                volume = ((point.volumeRatio * 1000).toInt()).coerceAtLeast(1).toString(),
                direction = if (isBuy) "买" else "卖",
                isBuy = isBuy
            )
        }
    }

    private fun quoteFromJson(json: JSONObject, fallback: StockQuote): StockQuote {
        val changePercent = firstText(json, "changePercent", "pct", "changePct", "percent", "涨跌幅") ?: fallback.changePercent
        val changeAmount = firstText(json, "changeAmount", "change", "涨跌额", "涨跌") ?: fallback.changeAmount
        return StockQuote(
            name = firstText(json, "name", "stockName", "securityName", "名称") ?: fallback.name,
            code = firstText(json, "code", "symbol", "ticker", "代码") ?: fallback.code,
            market = firstText(json, "market", "exchange", "市场") ?: fallback.market,
            price = firstText(json, "price", "last", "latest", "current", "close", "最新价") ?: fallback.price,
            changeAmount = changeAmount,
            changePercent = changePercent,
            isRising = json.optBoolean("isRising", !changePercent.startsWith("-") && !changeAmount.startsWith("-")),
            previousClose = firstDouble(json, "previousClose", "preClose", "prevClose", "昨收") ?: fallback.previousClose,
            high = firstText(json, "high", "最高") ?: fallback.high,
            low = firstText(json, "low", "最低") ?: fallback.low,
            open = firstText(json, "open", "今开", "开盘") ?: fallback.open,
            totalMarketValue = firstText(json, "totalMarketValue", "marketValue", "总市值", "市值") ?: fallback.totalMarketValue,
            floatMarketValue = firstText(json, "floatMarketValue", "circulatingMarketValue", "流通市值") ?: fallback.floatMarketValue,
            volumeRatio = firstText(json, "volumeRatio", "量比") ?: fallback.volumeRatio,
            turnoverRate = firstText(json, "turnoverRate", "turnover", "换手", "换手率") ?: fallback.turnoverRate,
            peTtm = firstText(json, "peTtm", "pe", "市盈率") ?: fallback.peTtm,
            pb = firstText(json, "pb", "市净率") ?: fallback.pb,
            amount = firstText(json, "amount", "成交额") ?: fallback.amount,
            popularityRank = firstText(json, "popularityRank", "rank", "人气") ?: fallback.popularityRank
        )
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

    private fun firstText(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = obj.opt(key)
            val text = when (raw) {
                is Number -> raw.toString()
                is String -> raw
                else -> raw?.toString().orEmpty()
            }.trim()
            if (text.isNotBlank() && text != "null" && text != "NaN") return text
        }
        return null
    }

    private fun firstDouble(obj: JSONObject, vararg keys: String): Float? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toDouble()
                is String -> raw.replace("%", "").replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (value != null && !value.isNaN()) return value.toFloat()
        }
        return null
    }

    private fun httpGet(url: String, timeoutMs: Int): String {
        val future = requestExecutor.submit(Callable { httpGetBlocking(url, timeoutMs) })
        return try {
            future.get((timeoutMs + 500).toLong(), TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("实时行情请求超时 ${timeoutMs}ms")
        } catch (error: Exception) {
            future.cancel(true)
            val cause = error.cause ?: error
            throw IllegalStateException(cause.message ?: cause.javaClass.simpleName)
        }
    }

    private fun httpGetBlocking(url: String, timeoutMs: Int): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "AI-Ledger-Android/1.0")
                setRequestProperty("Referer", baseUrl())
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${body.take(120)}".trim())
            if (body.isBlank()) throw IllegalStateException("实时行情返回为空")
            return body
        } finally {
            connection?.disconnect()
        }
    }
}
