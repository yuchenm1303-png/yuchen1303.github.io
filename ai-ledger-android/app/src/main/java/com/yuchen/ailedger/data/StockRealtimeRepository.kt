package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockQuote
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
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
    val dataSourceLabel: String,
    val depthStatus: StockModuleStatus = StockModuleStatus.Unavailable,
    val depthSource: String = "",
    val depthIsDerived: Boolean = false,
    val depthUpdatedAt: String = "",
    val depthSourceTimestamp: String = "",
    val depthCacheAgeMs: Long = 0L,
    val depthWarnings: List<String> = emptyList(),
    val tradeTicksDerived: Boolean = false,
    val warnings: List<String> = emptyList()
)

class StockRealtimeRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    private data class RealtimeCursor(
        val minuteKey: String = "",
        val tradeKey: String = ""
    )

    private val retryAfterByQuery = ConcurrentHashMap<String, Long>()
    private val cursorByStream = ConcurrentHashMap<String, RealtimeCursor>()

    fun loadRealtimeFrame(
        query: String,
        current: StockDetailUiState,
        minuteDays: Int = 1
    ): Result<StockRealtimeFrame> {
        val normalized = query.trim().ifBlank { current.quote.code }
        val safeDays = if (minuteDays >= 5) 5 else 1
        val retryKey = normalized.lowercase()
        val now = System.currentTimeMillis()
        cleanupMaps(now)
        val retryAfter = retryAfterByQuery[retryKey] ?: 0L

        val unified = if (now >= retryAfter) {
            runCatching {
                loadUnifiedRealtime(normalized, safeDays, current).also {
                    retryAfterByQuery.remove(retryKey)
                }
            }
        } else {
            Result.failure(IllegalStateException("统一实时接口冷却中"))
        }

        return unified.recoverCatching { error ->
            if (now >= retryAfter) {
                val delayMs = if (
                    error.message?.contains("HTTP 404") == true ||
                    error.message?.contains("HTTP 405") == true
                ) {
                    UNIFIED_NOT_FOUND_RETRY_MS
                } else {
                    UNIFIED_TRANSIENT_RETRY_MS
                }
                retryAfterByQuery[retryKey] = now + delayMs
            }
            loadLegacyRealtime(normalized, safeDays, current)
        }
    }

    private fun cleanupMaps(now: Long) {
        if (retryAfterByQuery.size > MAX_RETRY_KEYS) {
            val iterator = retryAfterByQuery.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value <= now) iterator.remove()
            }
        }
        if (cursorByStream.size > MAX_CURSOR_KEYS) cursorByStream.clear()
    }

    private fun loadUnifiedRealtime(
        query: String,
        minuteDays: Int,
        current: StockDetailUiState
    ): StockRealtimeFrame {
        val encoded = encode(query)
        val streamKey = cursorKey(query, minuteDays)
        val savedCursor = cursorByStream[streamKey]
        val minuteCursor = savedCursor?.minuteKey.orEmpty().ifBlank {
            if (minuteDays == 1) current.minutePoints.lastOrNull()?.time.orEmpty() else ""
        }
        val tradeCursor = savedCursor?.tradeKey.orEmpty().ifBlank {
            current.tradeTicks.lastOrNull()?.time.orEmpty()
        }
        val url = buildString {
            append(baseUrl())
            append("/api/stock/a-share/realtime?query=")
            append(encoded)
            append("&ndays=")
            append(minuteDays)
            append("&compact=true")
            if (minuteCursor.isNotBlank()) {
                append("&sinceMinuteKey=")
                append(encode(minuteCursor))
            }
            if (tradeCursor.isNotBlank()) {
                append("&sinceTradeKey=")
                append(encode(tradeCursor))
            }
        }
        val root = JSONObject(httpGet(url, UNIFIED_TIMEOUT_MS))
        val nextMinuteCursor = firstText(root, "minuteCursor")
            ?: firstText(payloadObject(root), "minuteCursor")
            ?: minuteCursor
        val nextTradeCursor = firstText(root, "tradeCursor")
            ?: firstText(payloadObject(root), "tradeCursor")
            ?: tradeCursor
        if (nextMinuteCursor.isNotBlank() || nextTradeCursor.isNotBlank()) {
            cursorByStream[streamKey] = RealtimeCursor(nextMinuteCursor, nextTradeCursor)
        }
        return parseFrame(root, payloadObject(root), current, minuteDays, "A股统一实时行情")
    }

    private fun loadLegacyRealtime(
        query: String,
        minuteDays: Int,
        current: StockDetailUiState
    ): StockRealtimeFrame {
        val encoded = encode(query)
        val root = runCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/minute?query=$encoded&ndays=$minuteDays&days=$minuteDays",
                    LEGACY_TIMEOUT_MS
                )
            )
        }.recoverCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/crawl/a-share/minute?query=$encoded&ndays=$minuteDays&days=$minuteDays",
                    LEGACY_TIMEOUT_MS
                )
            )
        }.getOrThrow()

        val payload = payloadObject(root)
        val parsed = parseFrame(root, payload, current, minuteDays, "A股兼容实时行情")
        val hasEmbeddedQuote = quoteObject(payload) != null || quoteObject(root) != null
        return if (hasEmbeddedQuote) {
            parsed
        } else {
            parsed.copy(quote = loadLegacyQuote(encoded, current.quote))
        }
    }

    private fun loadLegacyQuote(encodedQuery: String, fallback: StockQuote): StockQuote {
        val root = runCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/quotes?codes=$encodedQuery",
                    QUOTE_TIMEOUT_MS
                )
            )
        }.recoverCatching {
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encodedQuery",
                    QUOTE_TIMEOUT_MS
                )
            )
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
        val quoteJson = quoteObject(payload) ?: quoteObject(root)
        val quote = quoteJson?.let { quoteFromJson(it, current.quote) } ?: current.quote

        val minuteReset = firstBoolean(root, "minuteReset")
            ?: firstBoolean(payload, "minuteReset")
            ?: false
        val minuteIsSnapshot = minuteReset ||
            (firstBoolean(root, "minuteIsSnapshot") == true) ||
            (firstBoolean(payload, "minuteIsSnapshot") == true) ||
            hasArray(payload, MINUTE_KEYS) ||
            hasArray(root, MINUTE_KEYS)
        val minutePoints = parseMinutePoints(payload)
            .ifEmpty { parseMinutePoints(root) }
            .ifEmpty { parseMinuteDelta(payload) }
            .ifEmpty { parseMinuteDelta(root) }

        val warnings = (
            stringList(root.optJSONArray("warnings")) +
                stringList(payload.optJSONArray("warnings"))
            ).distinct()
        val tradeTicksDerived = firstBoolean(root, "tradeTicksIsDerived", "ticksIsDerived")
            ?: firstBoolean(payload, "tradeTicksIsDerived", "ticksIsDerived")
            ?: warnings.any(::isDerivedTickWarning)
        val rawTicksAreSnapshot = firstBoolean(root, "ticksAreSnapshot")
            ?: firstBoolean(payload, "ticksAreSnapshot")
            ?: (hasArray(payload, TICK_KEYS) || hasArray(root, TICK_KEYS))
        val parsedTicks = parseTradeTicks(payload)
            .ifEmpty { parseTradeTicks(root) }
            .ifEmpty { parseTickDelta(payload) }
            .ifEmpty { parseTickDelta(root) }
        val tradeTicks = if (tradeTicksDerived) emptyList() else parsedTicks

        val depthStatus = StockModuleStatus.fromWire(
            firstText(root, "depthStatus") ?: firstText(payload, "depthStatus")
        )
        val depthSource = firstText(root, "depthSource")
            ?: firstText(payload, "depthSource")
            ?: ""
        val depthIsDerived = firstBoolean(root, "depthIsDerived")
            ?: firstBoolean(payload, "depthIsDerived")
            ?: false
        val depthWarnings = (
            stringList(root.optJSONArray("depthWarnings")) +
                stringList(payload.optJSONArray("depthWarnings"))
            ).distinct()
        val canDisplayDepth = !depthIsDerived && depthStatus in DISPLAYABLE_DEPTH_STATUSES
        val rawSellLevels = parseOrderLevels(payload, ASK_KEYS, true)
            .ifEmpty { parseOrderLevels(root, ASK_KEYS, true) }
        val rawBuyLevels = parseOrderLevels(payload, BID_KEYS, false)
            .ifEmpty { parseOrderLevels(root, BID_KEYS, false) }

        val sourceHost = firstText(root, "sourceHost")
            ?: firstText(payload, "sourceHost")
        val sourceTimestamp = firstText(root, "sourceTimestamp", "updatedAt")
            ?: firstText(payload, "sourceTimestamp", "updatedAt")
            ?: ""
        val label = buildString {
            append(fallbackLabel)
            append(if (minuteDays >= 5) " · 5日" else " · 1日")
            if (!sourceHost.isNullOrBlank()) append(" · $sourceHost")
            if (firstBoolean(root, "isDelta") == true || firstBoolean(payload, "isDelta") == true) {
                append(" · 增量")
            }
        }

        return StockRealtimeFrame(
            quote = quote,
            minutePoints = minutePoints,
            minuteIsSnapshot = minuteIsSnapshot,
            sellLevels = if (canDisplayDepth) rawSellLevels else emptyList(),
            buyLevels = if (canDisplayDepth) rawBuyLevels else emptyList(),
            tradeTicks = tradeTicks,
            ticksAreSnapshot = rawTicksAreSnapshot && !tradeTicksDerived,
            sequence = firstLong(root, "sequence")
                ?: firstLong(payload, "sequence")
                ?: 0L,
            sourceTimestamp = sourceTimestamp,
            cacheAgeMs = firstLong(root, "cacheAgeMs")
                ?: firstLong(payload, "cacheAgeMs")
                ?: 0L,
            cacheHit = firstBoolean(root, "cacheHit")
                ?: firstBoolean(payload, "cacheHit")
                ?: false,
            dataSourceLabel = label,
            depthStatus = depthStatus,
            depthSource = depthSource,
            depthIsDerived = depthIsDerived,
            depthUpdatedAt = firstText(root, "depthUpdatedAt")
                ?: firstText(payload, "depthUpdatedAt")
                ?: "",
            depthSourceTimestamp = firstText(root, "depthSourceTimestamp")
                ?: firstText(payload, "depthSourceTimestamp")
                ?: "",
            depthCacheAgeMs = firstLong(root, "depthCacheAgeMs")
                ?: firstLong(payload, "depthCacheAgeMs")
                ?: 0L,
            depthWarnings = depthWarnings,
            tradeTicksDerived = tradeTicksDerived,
            warnings = warnings
        )
    }

    private fun isDerivedTickWarning(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.contains("derived_from_minute") ||
            normalized.contains("rebuilt_from_minute") ||
            normalized.contains("not_real_ticks") ||
            normalized.contains("from_minute_tail")
    }

    private fun quoteObject(payload: JSONObject): JSONObject? {
        return payload.optJSONObject("quote")
            ?: payload.optJSONObject("quoteDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONObject("quote")
            ?: parseQuoteObjects(payload).firstOrNull()
    }

    private fun parseMinuteDelta(payload: JSONObject): List<StockMinutePoint> {
        val array = payload.optJSONArray("minuteDelta")
            ?: payload.optJSONArray("latestMinutePoints")
            ?: payload.optJSONObject("snapshot")?.optJSONArray("minuteDelta")
        if (array != null) return parseMinutePointArray(array)
        val direct = payload.optJSONObject("latestMinutePoint")
            ?: payload.optJSONObject("minuteDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONObject("latestMinutePoint")
        return direct?.let(::parseMinutePoint)?.let(::listOf).orEmpty()
    }

    private fun parseTickDelta(payload: JSONObject): List<StockTradeTick> {
        val array = payload.optJSONArray("newTradeTicks")
            ?: payload.optJSONArray("tradeTickDelta")
            ?: payload.optJSONObject("snapshot")?.optJSONArray("newTradeTicks")
        if (array != null) return parseTradeTickArray(array)
        val direct = payload.optJSONObject("latestTradeTick")
        return direct?.let(::parseTradeTick)?.let(::listOf).orEmpty()
    }

    private fun payloadObject(root: JSONObject): JSONObject {
        return root.optJSONObject("data")
            ?: root.optJSONObject("payload")
            ?: root.optJSONObject("snapshot")
            ?: root.optJSONObject("result")
            ?: root
    }

    private fun hasArray(obj: JSONObject, keys: List<String>): Boolean {
        if (keys.any { obj.optJSONArray(it) != null }) return true
        val snapshot = obj.optJSONObject("snapshot") ?: return false
        return keys.any { snapshot.optJSONArray(it) != null }
    }

    private fun findArray(obj: JSONObject, keys: List<String>): JSONArray? {
        keys.forEach { key -> obj.optJSONArray(key)?.let { return it } }
        obj.optJSONObject("snapshot")?.let { nested ->
            keys.forEach { key -> nested.optJSONArray(key)?.let { return it } }
        }
        listOf("data", "payload", "result").forEach { containerKey ->
            val nested = obj.opt(containerKey)
            when (nested) {
                is JSONArray -> return nested
                is JSONObject -> findArray(nested, keys)?.let { return it }
            }
        }
        return null
    }

    private fun parseQuoteObjects(root: JSONObject): List<JSONObject> {
        val payload = payloadObject(root)
        payload.optJSONObject("quote")?.let { return listOf(it) }
        val array = findArray(payload, listOf("quotes", "items", "list", "records", "stocks"))
        if (array != null) {
            return buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }
        }
        if (payload.has("code") || payload.has("name") || payload.has("price")) {
            return listOf(payload)
        }
        return buildList {
            val keys = payload.keys()
            while (keys.hasNext()) {
                val value = payload.opt(keys.next())
                if (value is JSONObject &&
                    (value.has("code") || value.has("name") || value.has("price"))
                ) {
                    add(value)
                }
            }
        }
    }

    private data class RawMinutePoint(
        val time: String,
        val price: Float,
        val average: Float,
        val explicitRatio: Float?,
        val volume: Float,
        val matchedVolume: Float?,
        val unmatchedVolume: Float?,
        val unmatchedDirection: String,
        val phase: String
    )

    private fun parseMinutePoints(root: JSONObject): List<StockMinutePoint> {
        val array = findArray(root, MINUTE_KEYS) ?: return emptyList()
        val raw = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::parseRawMinutePoint)?.let(::add)
            }
        }
        val maxVolume = raw.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        return raw.map { point -> point.toModel(maxVolume) }
    }

    private fun parseMinutePointArray(array: JSONArray): List<StockMinutePoint> {
        val raw = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::parseRawMinutePoint)?.let(::add)
            }
        }
        val maxVolume = raw.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        return raw.map { point -> point.toModel(maxVolume) }
    }

    private fun parseMinutePoint(item: JSONObject): StockMinutePoint? {
        val raw = parseRawMinutePoint(item) ?: return null
        return raw.toModel(raw.volume.takeIf { it > 0f } ?: 1f)
    }

    private fun RawMinutePoint.toModel(maxVolume: Float): StockMinutePoint = StockMinutePoint(
        time = time,
        price = price,
        average = average,
        volumeRatio = explicitRatio?.coerceIn(0.02f, 1f)
            ?: if (volume > 0f) (volume / maxVolume).coerceIn(0.02f, 1f) else 0.02f,
        volume = volume,
        matchedVolume = matchedVolume,
        unmatchedVolume = unmatchedVolume,
        unmatchedDirection = unmatchedDirection,
        phase = phase
    )

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
            volume = firstDouble(item, "volume", "vol") ?: 0f,
            matchedVolume = firstDouble(item, "matchedVolume", "matchVolume", "matched"),
            unmatchedVolume = firstDouble(item, "unmatchedVolume", "unmatchVolume", "unmatched"),
            unmatchedDirection = firstText(item, "unmatchedDirection", "unmatchDirection")
                ?: "unavailable",
            phase = firstText(item, "phase", "auctionPhase") ?: "continuous"
        )
    }

    private fun parseOrderLevels(
        root: JSONObject,
        keys: List<String>,
        isAsk: Boolean
    ): List<StockOrderLevel> {
        val array = findArray(root, keys) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val price = firstText(item, "price", "p") ?: continue
                val volume = firstText(item, "volume", "qty", "vol") ?: continue
                add(
                    StockOrderLevel(
                        label = firstText(item, "label", "name")
                            ?: if (isAsk) "卖${index + 1}" else "买${index + 1}",
                        price = price,
                        volume = volume,
                        isAsk = firstBoolean(item, "isAsk") ?: isAsk
                    )
                )
            }
        }
    }

    private fun parseTradeTicks(root: JSONObject): List<StockTradeTick> {
        val array = findArray(root, TICK_KEYS) ?: return emptyList()
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
            isBuy = firstBoolean(item, "isBuy")
                ?: (direction.contains("买") || direction.equals("buy", ignoreCase = true))
        )
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
            isRising = firstBoolean(json, "isRising")
                ?: (!changePercent.startsWith("-") && !changeAmount.startsWith("-")),
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

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun firstText(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
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

    private fun firstDouble(obj: JSONObject?, vararg keys: String): Float? {
        if (obj == null) return null
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

    private fun firstLong(obj: JSONObject?, vararg keys: String): Long? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toDoubleOrNull()?.toLong()
                else -> null
            }
            if (value != null) return value
        }
        return null
    }

    private fun firstBoolean(obj: JSONObject?, vararg keys: String): Boolean? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            return when (val raw = obj.opt(key)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
                else -> null
            }
        }
        return null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun baseUrl(): String {
        val base = proxyBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("行情代理地址为空")
        return base
    }

    private fun cursorKey(query: String, minuteDays: Int): String =
        "${query.trim().lowercase()}:$minuteDays"

    private fun httpGet(url: String, timeoutMs: Int): String =
        StockHttpClient.get(url, timeoutMs, "实时行情返回为空")

    companion object {
        private val MINUTE_KEYS = listOf("minutePoints", "minutes")
        private val TICK_KEYS = listOf("tradeTicks", "ticks", "deals")
        private val ASK_KEYS = listOf("sellLevels", "askLevels", "asks")
        private val BID_KEYS = listOf("buyLevels", "bidLevels", "bids")
        private val DISPLAYABLE_DEPTH_STATUSES = setOf(
            StockModuleStatus.Ok,
            StockModuleStatus.Partial,
            StockModuleStatus.Stale
        )
        private const val UNIFIED_TIMEOUT_MS = 1_800
        private const val LEGACY_TIMEOUT_MS = 2_400
        private const val QUOTE_TIMEOUT_MS = 1_800
        private const val UNIFIED_NOT_FOUND_RETRY_MS = 30_000L
        private const val UNIFIED_TRANSIENT_RETRY_MS = 5_000L
        private const val MAX_RETRY_KEYS = 128
        private const val MAX_CURSOR_KEYS = 128
    }
}
