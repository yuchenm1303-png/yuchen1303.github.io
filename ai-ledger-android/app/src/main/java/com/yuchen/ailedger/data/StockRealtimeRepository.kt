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
import org.json.JSONArray
import org.json.JSONObject

data class StockRealtimeFrame(
    val quote: StockQuote,
    val minutePoints: List<StockMinutePoint>,
    val minuteIsSnapshot: Boolean,
    val sellLevels: List<StockOrderLevel>,
    val buyLevels: List<StockOrderLevel>,
    val tradeTicks: List<StockTradeTick>,
    val ticksAreSnapshot: Boolean,
    val sequence: Long,
    val sourceTimestamp: String,
    val cacheAgeMs: Long,
    val cacheHit: Boolean,
    val dataSourceLabel: String
)

class StockRealtimeRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadRealtimeFrame(
        query: String,
        current: StockDetailUiState,
        minuteDays: Int = 1
    ): Result<StockRealtimeFrame> = runCatching {
        val normalized = query.trim().ifBlank { current.quote.code }
        val safeDays = minuteDays.coerceIn(1, 5)
        loadUnifiedRealtime(normalized, safeDays, current)
    }.recoverCatching {
        val normalized = query.trim().ifBlank { current.quote.code }
        val safeDays = minuteDays.coerceIn(1, 5)
        loadLegacyRealtime(normalized, safeDays, current)
    }

    private fun loadUnifiedRealtime(
        query: String,
        minuteDays: Int,
        current: StockDetailUiState
    ): StockRealtimeFrame {
        val encoded = encode(query)
        val body = httpGet(
            "${baseUrl()}/api/stock/a-share/realtime?query=$encoded&ndays=$minuteDays",
            timeoutMs = 1800
        )
        val root = JSONObject(body)
        val payload = payloadObject(root)
        return parseFrame(
            root = root,
            payload = payload,
            current = current,
            minuteDays = minuteDays,
            fallbackLabel = "A股统一实时行情 · realtime"
        )
    }

    private fun loadLegacyRealtime(
        query: String,
        minuteDays: Int,
        current: StockDetailUiState
    ): StockRealtimeFrame {
        val encoded = encode(query)
        val minuteRoot = runCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/minute?query=$encoded&ndays=$minuteDays&days=$minuteDays",
                    timeoutMs = 2400
                )
            )
        }.recoverCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/crawl/a-share/minute?query=$encoded&ndays=$minuteDays&days=$minuteDays",
                    timeoutMs = 2400
                )
            )
        }.getOrThrow()

        val minutePayload = payloadObject(minuteRoot)
        val embeddedQuote = quoteObject(minutePayload)
        val quote = if (embeddedQuote != null) {
            quoteFromJson(embeddedQuote, current.quote)
        } else {
            loadLegacyQuote(encoded, current.quote)
        }

        val minutePoints = parseMinutePoints(minutePayload)
        val sellLevels = parseOrderLevels(minutePayload, listOf("sellLevels", "askLevels", "asks"), true)
        val buyLevels = parseOrderLevels(minutePayload, listOf("buyLevels", "bidLevels", "bids"), false)
        val ticks = parseTradeTicks(minutePayload).ifEmpty {
            ticksFromMinute(minutePoints, quote)
        }

        return StockRealtimeFrame(
            quote = quote,
            minutePoints = minutePoints,
            minuteIsSnapshot = true,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = ticks,
            ticksAreSnapshot = true,
            sequence = 0L,
            sourceTimestamp = firstText(minuteRoot, "sourceTimestamp", "updatedAt").orEmpty(),
            cacheAgeMs = firstLong(minuteRoot, "cacheAgeMs") ?: 0L,
            cacheHit = minuteRoot.optBoolean("cacheHit", false),
            dataSourceLabel = if (minuteDays >= 5) {
                "A股兼容实时行情 · 5日分时"
            } else {
                "A股兼容实时行情 · 分时/盘口/逐笔"
            }
        )
    }

    private fun loadLegacyQuote(encodedQuery: String, fallback: StockQuote): StockQuote {
        val root = runCatching {
            JSONObject(httpGet("${baseUrl()}/api/stock/a-share/quotes?codes=$encodedQuery", timeoutMs = 1800))
        }.recoverCatching {
            JSONObject(httpGet("${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encodedQuery", timeoutMs = 1800))
        }.getOrThrow()
        val quoteJson = parseQuoteObjects(root).firstOrNull()
            ?: throw IllegalStateException("quotes接口没有报价对象")
        return quoteFromJson(quoteJson, fallback)
    }

    private fun parseFrame(
        root: JSONObject,
        payload: JSONObject,
        current: StockDetailUiState,
        minuteDays: Int,
        fallbackLabel: String
    ): StockRealtimeFrame {
        val quoteJson = quoteObject(payload)
        val quote = quoteJson?.let { quoteFromJson(it, current.quote) } ?: current.quote

        val fullMinuteArrayPresent = hasArray(payload, listOf("minutePoints", "minutes"))
        val minutePoints = parseMinutePoints(payload).ifEmpty {
            parseMinuteDelta(payload)
        }
        val sellLevels = parseOrderLevels(payload, listOf("sellLevels", "askLevels", "asks"), true)
        val buyLevels = parseOrderLevels(payload, listOf("buyLevels", "bidLevels", "bids"), false)
        val fullTicksArrayPresent = hasArray(payload, listOf("tradeTicks", "ticks", "deals"))
        val ticks = parseTradeTicks(payload).ifEmpty {
            parseTickDelta(payload)
        }

        val cacheAgeMs = firstLong(root, "cacheAgeMs")
            ?: firstLong(payload, "cacheAgeMs")
            ?: 0L
        val cacheHit = when {
            root.has("cacheHit") -> root.optBoolean("cacheHit", false)
            payload.has("cacheHit") -> payload.optBoolean("cacheHit", false)
            else -> false
        }
        val sourceHost = firstText(root, "sourceHost")
            ?: firstText(payload, "sourceHost")
        val sourceTimestamp = firstText(root, "sourceTimestamp", "updatedAt")
            ?: firstText(payload, "sourceTimestamp", "updatedAt")
            ?: ""
        val sequence = firstLong(root, "sequence")
            ?: firstLong(payload, "sequence")
            ?: 0L
        val label = buildString {
            append(fallbackLabel)
            append(if (minuteDays >= 5) " · 5日" else " · 1日")
            if (!sourceHost.isNullOrBlank()) append(" · $sourceHost")
            if (cacheHit) append(" · 热缓存")
        }

        return StockRealtimeFrame(
            quote = quote,
            minutePoints = minutePoints,
            minuteIsSnapshot = fullMinuteArrayPresent,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = ticks,
            ticksAreSnapshot = fullTicksArrayPresent,
            sequence = sequence,
            sourceTimestamp = sourceTimestamp,
            cacheAgeMs = cacheAgeMs,
            cacheHit = cacheHit,
            dataSourceLabel = label
        )
    }

    private fun quoteObject(payload: JSONObject): JSONObject? {
        return payload.optJSONObject("quote")
            ?: payload.optJSONObject("quoteDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONObject("quote")
            ?: parseQuoteObjects(payload).firstOrNull()
    }

    private fun parseMinuteDelta(payload: JSONObject): List<StockMinutePoint> {
        val direct = payload.optJSONObject("latestMinutePoint")
            ?: payload.optJSONObject("minuteDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONObject("latestMinutePoint")
        return direct?.let { parseMinutePoint(it) }?.let(::listOf).orEmpty()
    }

    private fun parseTickDelta(payload: JSONObject): List<StockTradeTick> {
        val array = payload.optJSONArray("newTradeTicks")
            ?: payload.optJSONArray("tradeTickDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONArray("newTradeTicks")
        if (array != null) return parseTradeTickArray(array)
        val direct = payload.optJSONObject("latestTradeTick")
        return direct?.let { parseTradeTick(it) }?.let(::listOf).orEmpty()
    }

    private fun baseUrl(): String {
        val base = proxyBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("行情代理地址为空")
        return base
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun payloadObject(obj: JSONObject): JSONObject {
        return obj.optJSONObject("data")
            ?: obj.optJSONObject("payload")
            ?: obj.optJSONObject("snapshot")
            ?: obj.optJSONObject("result")
            ?: obj
    }

    private fun hasArray(obj: JSONObject, keys: List<String>): Boolean {
        return keys.any { obj.optJSONArray(it) != null }
    }

    private fun findArray(obj: JSONObject, keys: List<String>): JSONArray? {
        keys.forEach { key -> obj.optJSONArray(key)?.let { return it } }
        obj.optJSONObject("snapshot")?.let { nested ->
            keys.forEach { key -> nested.optJSONArray(key)?.let { return it } }
        }
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
        payload.optJSONObject("quote")?.let { return listOf(it) }
        val array = findArray(payload, listOf("quotes", "items", "list", "records", "stocks"))
        if (array != null) {
            return buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }
        }
        return if (payload.has("code") || payload.has("name") || payload.has("price")) {
            listOf(payload)
        } else {
            emptyList()
        }
    }

    private data class RawMinutePoint(
        val time: String,
        val price: Float,
        val average: Float,
        val explicitRatio: Float?,
        val volume: Float
    )

    private fun parseMinutePoints(obj: JSONObject): List<StockMinutePoint> {
        val array = findArray(obj, listOf("minutePoints", "minutes")) ?: return emptyList()
        val rawPoints = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val raw = parseRawMinutePoint(item) ?: continue
                add(raw)
            }
        }
        val maxVolume = rawPoints.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        return rawPoints.map { raw ->
            StockMinutePoint(
                time = raw.time,
                price = raw.price,
                average = raw.average,
                volumeRatio = raw.explicitRatio?.coerceIn(0.02f, 1f)
                    ?: (raw.volume / maxVolume).coerceIn(0.02f, 1f)
            )
        }
    }

    private fun parseMinutePoint(item: JSONObject): StockMinutePoint? {
        val raw = parseRawMinutePoint(item) ?: return null
        return StockMinutePoint(
            time = raw.time,
            price = raw.price,
            average = raw.average,
            volumeRatio = raw.explicitRatio?.coerceIn(0.02f, 1f)
                ?: if (raw.volume > 0f) 1f else 0.02f
        )
    }

    private fun parseRawMinutePoint(item: JSONObject): RawMinutePoint? {
        val price = firstDouble(item, "price", "close", "p", "最新价") ?: return null
        if (price <= 0f) return null
        val date = firstText(item, "date", "tradeDate", "day").orEmpty()
        val rawTime = firstText(item, "time", "minute", "t", "datetime", "dateTime", "时间")
            ?: firstLong(item, "timestamp")?.toString()
            ?: ""
        val time = if (date.isNotBlank() && rawTime.isNotBlank() && !rawTime.contains(date)) {
            "$date $rawTime"
        } else {
            rawTime
        }
        return RawMinutePoint(
            time = time,
            price = price,
            average = firstDouble(item, "average", "avg", "avgPrice", "均价") ?: price,
            explicitRatio = firstDouble(item, "volumeRatio", "ratio"),
            volume = firstDouble(item, "volume", "vol") ?: 0f
        )
    }

    private fun parseOrderLevels(
        obj: JSONObject,
        keys: List<String>,
        isAsk: Boolean
    ): List<StockOrderLevel> {
        val array = findArray(obj, keys) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    StockOrderLevel(
                        label = firstText(item, "label", "name")
                            ?: if (isAsk) "卖${index + 1}" else "买${index + 1}",
                        price = firstText(item, "price", "p") ?: "--",
                        volume = firstText(item, "volume", "qty", "vol") ?: "--",
                        isAsk = item.optBoolean("isAsk", isAsk)
                    )
                )
            }
        }
    }

    private fun parseTradeTicks(obj: JSONObject): List<StockTradeTick> {
        val array = findArray(obj, listOf("tradeTicks", "ticks", "deals")) ?: return emptyList()
        return parseTradeTickArray(array)
    }

    private fun parseTradeTickArray(array: JSONArray): List<StockTradeTick> {
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::parseTradeTick)?.let(::add)
            }
        }
    }

    private fun parseTradeTick(item: JSONObject): StockTradeTick? {
        val price = firstText(item, "price", "p") ?: return null
        val direction = firstText(item, "direction", "side", "type") ?: "--"
        return StockTradeTick(
            time = firstText(item, "time", "t")
                ?: firstLong(item, "timestamp")?.toString()
                ?: "--",
            price = price,
            volume = firstText(item, "volume", "qty", "vol") ?: "--",
            direction = direction,
            isBuy = item.optBoolean(
                "isBuy",
                direction.contains("买") || direction.equals("buy", ignoreCase = true)
            )
        )
    }

    private fun ticksFromMinute(
        points: List<StockMinutePoint>,
        quote: StockQuote
    ): List<StockTradeTick> {
        if (points.isEmpty()) return emptyList()
        return points.takeLast(8).reversed().mapIndexed { index, point ->
            val previous = points.getOrNull(points.lastIndex - index - 1)?.price
                ?: quote.previousClose
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
        val changePercent = firstText(
            json,
            "changePercent",
            "pct",
            "changePct",
            "percent",
            "涨跌幅"
        ) ?: fallback.changePercent
        val changeAmount = firstText(json, "changeAmount", "change", "涨跌额", "涨跌")
            ?: fallback.changeAmount
        return StockQuote(
            name = firstText(json, "name", "stockName", "securityName", "名称") ?: fallback.name,
            code = firstText(json, "code", "symbol", "ticker", "代码") ?: fallback.code,
            market = firstText(json, "market", "exchange", "市场") ?: fallback.market,
            price = firstText(json, "price", "last", "latest", "current", "close", "最新价")
                ?: fallback.price,
            changeAmount = changeAmount,
            changePercent = changePercent,
            isRising = json.optBoolean(
                "isRising",
                !changePercent.startsWith("-") && !changeAmount.startsWith("-")
            ),
            previousClose = firstDouble(json, "previousClose", "preClose", "prevClose", "昨收")
                ?: fallback.previousClose,
            high = firstText(json, "high", "最高") ?: fallback.high,
            low = firstText(json, "low", "最低") ?: fallback.low,
            open = firstText(json, "open", "今开", "开盘") ?: fallback.open,
            totalMarketValue = firstText(json, "totalMarketValue", "marketValue", "总市值", "市值")
                ?: fallback.totalMarketValue,
            floatMarketValue = firstText(
                json,
                "floatMarketValue",
                "circulatingMarketValue",
                "流通市值"
            ) ?: fallback.floatMarketValue,
            volumeRatio = firstText(json, "volumeRatio", "量比") ?: fallback.volumeRatio,
            turnoverRate = firstText(json, "turnoverRate", "turnover", "换手", "换手率")
                ?: fallback.turnoverRate,
            peTtm = firstText(json, "peTtm", "pe", "市盈率") ?: fallback.peTtm,
            pb = firstText(json, "pb", "市净率") ?: fallback.pb,
            amount = firstText(json, "amount", "成交额") ?: fallback.amount,
            popularityRank = firstText(json, "popularityRank", "rank", "人气")
                ?: fallback.popularityRank
        )
    }

    fun topMetricsFor(quote: StockQuote): List<StockMetric> = listOf(
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

    private fun firstLong(obj: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (value != null) return value
        }
        return null
    }

    private fun httpGet(url: String, timeoutMs: Int): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("User-Agent", "AI-Ledger-Android/1.0")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${body.take(120)}".trim())
            }
            if (body.isBlank()) throw IllegalStateException("实时行情返回为空")
            return body
        } finally {
            connection?.disconnect()
        }
    }
}
