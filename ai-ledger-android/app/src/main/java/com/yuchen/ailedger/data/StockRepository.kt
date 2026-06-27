package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockMoneyFlow
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockQuote
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class StockRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadAStock(query: String, mode: String = "lite"): StockDetailUiState {
        val normalized = query.trim().ifBlank { DEFAULT_STOCK_CODE }
        val safeMode = if (mode == "full") "full" else "lite"
        val encoded = encode(normalized)
        val base = emptyStock(normalized)
        val timeout = if (safeMode == "full") FULL_TIMEOUT_MS else LITE_TIMEOUT_MS

        val detailResult = runCatching {
            parseDetail(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/a-share/detail?query=$encoded&mode=$safeMode",
                        timeout,
                        DETAIL_MICRO_CACHE_MS
                    )
                ),
                base
            )
        }.recoverCatching {
            parseDetail(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=$safeMode",
                        timeout,
                        DETAIL_MICRO_CACHE_MS
                    )
                ),
                base
            )
        }
        if (detailResult.isSuccess) return detailResult.getOrThrow()

        val quoteResult = loadQuoteSnapshot(normalized)
        return quoteResult.fold(
            onSuccess = { quote ->
                base.copy(
                    quote = quote,
                    topMetrics = topMetricsFor(quote),
                    fundamentals = fundamentalsFor(quote),
                    dataSourceLabel = "A股真实报价",
                    errorMessage = null,
                    aiSummary = "${quote.name.ifBlank { quote.code }} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。"
                )
            },
            onFailure = { quoteError ->
                val detailError = detailResult.exceptionOrNull()
                base.copy(
                    dataSourceLabel = "真实行情暂不可用",
                    errorMessage = detailError?.message
                        ?: quoteError.message
                        ?: quoteError.javaClass.simpleName,
                    aiSummary = "真实行情暂不可用，未展示示例或模拟数据。"
                )
            }
        )
    }

    fun loadKLinePoints(
        query: String,
        period: String = "daily"
    ): Result<List<StockKLinePoint>> {
        val normalized = query.trim().ifBlank { DEFAULT_STOCK_CODE }
        val encoded = encode(normalized)
        val safePeriod = when (period.lowercase()) {
            "weekly", "week", "周k" -> "weekly"
            "monthly", "month", "月k" -> "monthly"
            else -> "daily"
        }
        return runCatching {
            parseKLines(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/a-share/kline?query=$encoded&period=$safePeriod&limit=160",
                        KLINE_TIMEOUT_MS,
                        KLINE_MICRO_CACHE_MS
                    )
                )
            ).ifEmpty { throw IllegalStateException("$safePeriod K线接口返回为空") }
        }.recoverCatching {
            parseKLines(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/crawl/a-share/kline?query=$encoded&period=$safePeriod&limit=160",
                        KLINE_TIMEOUT_MS,
                        KLINE_MICRO_CACHE_MS
                    )
                )
            ).ifEmpty { throw IllegalStateException("crawl $safePeriod K线接口返回为空") }
        }
    }

    fun loadMinutePoints(query: String): Result<List<StockMinutePoint>> {
        val normalized = query.trim().ifBlank { DEFAULT_STOCK_CODE }
        val encoded = encode(normalized)
        return runCatching {
            parseMinutePoints(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/a-share/realtime?query=$encoded&ndays=1&compact=false",
                        MINUTE_TIMEOUT_MS,
                        REALTIME_MICRO_CACHE_MS
                    )
                )
            ).ifEmpty { throw IllegalStateException("真实分时接口返回为空") }
        }.recoverCatching {
            parseMinutePoints(
                JSONObject(
                    httpGet(
                        "${baseUrl()}/api/stock/crawl/a-share/minute?query=$encoded",
                        MINUTE_TIMEOUT_MS,
                        REALTIME_MICRO_CACHE_MS
                    )
                )
            ).ifEmpty { throw IllegalStateException("兼容分时接口返回为空") }
        }
    }

    private fun loadQuoteSnapshot(query: String): Result<StockQuote> {
        val encoded = encode(query)
        val fallback = blankQuote(query)
        return runCatching {
            val root = JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/quotes?codes=$encoded",
                    QUOTE_TIMEOUT_MS,
                    QUOTE_MICRO_CACHE_MS
                )
            )
            quoteFromJson(
                parseQuoteObjects(root).firstOrNull()
                    ?: throw IllegalStateException("quotes接口没有报价对象"),
                fallback
            )
        }.recoverCatching {
            val root = JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encoded",
                    QUOTE_TIMEOUT_MS,
                    QUOTE_MICRO_CACHE_MS
                )
            )
            quoteFromJson(
                parseQuoteObjects(root).firstOrNull()
                    ?: throw IllegalStateException("crawl quotes接口没有报价对象"),
                fallback
            )
        }
    }

    private fun parseDetail(root: JSONObject, base: StockDetailUiState): StockDetailUiState {
        val payload = payloadObject(root)
        val quoteJson = payload.optJSONObject("quote")
            ?: parseQuoteObjects(payload).firstOrNull()
            ?: throw IllegalStateException("代理行情缺少 quote 字段")
        val quote = quoteFromJson(quoteJson, base.quote)
        if (quote.code.isBlank()) throw IllegalStateException("代理行情缺少股票代码")

        val warnings = (
            stringList(payload.optJSONArray("warnings")) +
                stringList(root.optJSONArray("warnings"))
            ).distinct()
        val ticksAreDerived = warnings.any {
            it.contains("rebuilt_from_minute", ignoreCase = true) ||
                it.contains("derived_from_minute", ignoreCase = true) ||
                it.contains("not_real_ticks", ignoreCase = true)
        }
        val minuteIsFallback = warnings.any {
            it.contains("minute_points_fallback", ignoreCase = true)
        }
        val sourceLabel = firstText(payload, "dataSourceLabel")
            ?: firstText(root, "dataSourceLabel")
            ?: "A股真实行情 · ${quote.code}"

        return base.copy(
            quote = quote,
            topMetrics = topMetricsFor(quote),
            minutePoints = if (minuteIsFallback) emptyList() else parseMinutePoints(payload),
            sellLevels = parseOrderLevels(payload, listOf("sellLevels", "askLevels", "asks"), true),
            buyLevels = parseOrderLevels(payload, listOf("buyLevels", "bidLevels", "bids"), false),
            tradeTicks = if (ticksAreDerived) emptyList() else parseTradeTicks(payload),
            moneyFlow = parseMoneyFlow(payload) ?: emptyMoneyFlow(),
            fundamentals = parseMetrics(payload, "fundamentals").ifEmpty {
                fundamentalsFor(quote)
            },
            indices = emptyList(),
            watchlist = emptyList(),
            marketBoards = emptyList(),
            kLinePoints = parseKLines(payload),
            dataSourceLabel = sourceLabel,
            errorMessage = null,
            aiSummary = firstText(payload, "aiSummary")
                ?: "${quote.name.ifBlank { quote.code }} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。"
        )
    }

    private fun payloadObject(root: JSONObject): JSONObject {
        return root.optJSONObject("data")
            ?: root.optJSONObject("payload")
            ?: root.optJSONObject("snapshot")
            ?: root.optJSONObject("result")
            ?: root
    }

    private fun findArray(obj: JSONObject, keys: List<String>): JSONArray? {
        keys.forEach { key -> obj.optJSONArray(key)?.let { return it } }
        listOf("data", "payload", "result", "snapshot").forEach { containerKey ->
            val nested = obj.optJSONObject(containerKey) ?: return@forEach
            findArray(nested, keys)?.let { return it }
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
        return if (payload.has("code") || payload.has("name") || payload.has("price")) {
            listOf(payload)
        } else {
            emptyList()
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
        val array = findArray(
            root,
            listOf("minutePoints", "minutes", "minuteDelta", "latestMinutePoints")
        ) ?: return emptyList()
        val raw = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val price = firstDouble(item, "price", "close", "p", "最新价") ?: continue
                if (price <= 0f) continue
                val date = firstText(item, "date", "tradeDate", "day").orEmpty()
                val rawTime = firstText(
                    item,
                    "time",
                    "minute",
                    "t",
                    "datetime",
                    "dateTime",
                    "时间"
                ) ?: firstLong(item, "timestamp")?.toString().orEmpty()
                val time = if (date.isNotBlank() && rawTime.isNotBlank() && !rawTime.contains(date)) {
                    "$date $rawTime"
                } else {
                    rawTime
                }
                add(
                    RawMinutePoint(
                        time = time,
                        price = price,
                        average = firstDouble(item, "average", "avg", "avgPrice", "均价") ?: price,
                        explicitRatio = firstDouble(item, "volumeRatio", "ratio"),
                        volume = firstDouble(item, "volume", "vol") ?: 0f,
                        matchedVolume = firstDouble(item, "matchedVolume", "matchVolume", "matched"),
                        unmatchedVolume = firstDouble(item, "unmatchedVolume", "unmatchVolume", "unmatched"),
                        unmatchedDirection = firstText(
                            item,
                            "unmatchedDirection",
                            "unmatchDirection"
                        ) ?: "unavailable",
                        phase = firstText(item, "phase", "sessionPhase", "auctionPhase")
                            ?: "continuous"
                    )
                )
            }
        }
        val maxVolume = raw.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        return raw.map { point ->
            StockMinutePoint(
                time = point.time,
                price = point.price,
                average = point.average,
                volumeRatio = point.explicitRatio?.coerceIn(0.02f, 1f)
                    ?: if (point.volume > 0f) {
                        (point.volume / maxVolume).coerceIn(0.02f, 1f)
                    } else {
                        0.02f
                    },
                volume = point.volume,
                matchedVolume = point.matchedVolume,
                unmatchedVolume = point.unmatchedVolume,
                unmatchedDirection = point.unmatchedDirection,
                phase = point.phase
            )
        }
    }

    private fun parseKLines(root: JSONObject): List<StockKLinePoint> {
        val array = findArray(
            root,
            listOf("kLinePoints", "klinePoints", "klines", "kLines")
        ) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val open = firstDouble(item, "open", "o", "开盘") ?: continue
                val close = firstDouble(item, "close", "c", "收盘", "price") ?: continue
                add(
                    StockKLinePoint(
                        date = firstText(item, "date", "day", "time", "日期").orEmpty(),
                        open = open,
                        close = close,
                        high = firstDouble(item, "high", "h", "最高") ?: close,
                        low = firstDouble(item, "low", "l", "最低") ?: close,
                        volume = firstDouble(item, "volume", "vol", "成交量") ?: 0f,
                        amount = firstDouble(item, "amount", "成交额") ?: 0f,
                        changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--",
                        amplitude = firstText(item, "amplitude", "振幅") ?: "--",
                        changeAmount = firstText(item, "changeAmount", "change", "涨跌额") ?: "--",
                        turnoverRate = firstText(item, "turnoverRate", "turnover", "换手率") ?: "--"
                    )
                )
            }
        }
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
        val array = findArray(
            root,
            listOf("tradeTicks", "ticks", "deals", "newTradeTicks", "tradeTickDelta")
        ) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val price = firstText(item, "price", "p") ?: continue
                val direction = firstText(item, "direction", "side", "type") ?: "--"
                add(
                    StockTradeTick(
                        time = firstText(item, "time", "t")
                            ?: firstLong(item, "timestamp")?.toString()
                            ?: "--",
                        price = price,
                        volume = firstText(item, "volume", "qty", "vol") ?: "--",
                        direction = direction,
                        isBuy = firstBoolean(item, "isBuy")
                            ?: (direction.contains("买") || direction.equals("buy", ignoreCase = true))
                    )
                )
            }
        }
    }

    private fun parseMoneyFlow(root: JSONObject): StockMoneyFlow? {
        val flow = root.optJSONObject("moneyFlow")
            ?: root.optJSONObject("fundFlow")
            ?: payloadObject(root).optJSONObject("moneyFlow")
            ?: return null
        return StockMoneyFlow(
            mainInflow = firstText(flow, "mainInflow", "main", "主力净流入") ?: "--",
            superLargeOrder = firstText(flow, "superLargeOrder", "superLarge", "超大单") ?: "--",
            largeOrder = firstText(flow, "largeOrder", "large", "大单") ?: "--",
            mediumOrder = firstText(flow, "mediumOrder", "medium", "中单") ?: "--",
            smallOrder = firstText(flow, "smallOrder", "small", "小单") ?: "--"
        )
    }

    private fun parseMetrics(root: JSONObject, key: String): List<StockMetric> {
        val array = payloadObject(root).optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = firstText(item, "label", "name") ?: continue
                val value = firstText(item, "value") ?: "--"
                val tone = when (firstText(item, "tone")?.lowercase()) {
                    "rising", "up", "red" -> StockTone.Rising
                    "falling", "down", "green" -> StockTone.Falling
                    else -> StockTone.Neutral
                }
                add(StockMetric(label, value, tone))
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

    private fun fundamentalsFor(quote: StockQuote): List<StockMetric> = listOf(
        StockMetric("市值", quote.totalMarketValue),
        StockMetric("流通市值", quote.floatMarketValue),
        StockMetric("市盈率", quote.peTtm),
        StockMetric("市净率", quote.pb),
        StockMetric("量比", quote.volumeRatio),
        StockMetric("换手", quote.turnoverRate)
    )

    private fun emptyStock(query: String): StockDetailUiState = StockDetailUiState(
        quote = blankQuote(query),
        topMetrics = emptyList(),
        minutePoints = emptyList(),
        sellLevels = emptyList(),
        buyLevels = emptyList(),
        tradeTicks = emptyList(),
        moneyFlow = emptyMoneyFlow(),
        fundamentals = emptyList(),
        indices = emptyList(),
        watchlist = emptyList(),
        featureGroups = emptyList(),
        marketBoards = emptyList(),
        aiSummary = "等待真实行情数据",
        kLinePoints = emptyList(),
        dataSourceLabel = "等待真实行情",
        errorMessage = null
    )

    private fun blankQuote(query: String): StockQuote {
        val code = query.filter(Char::isDigit).takeIf { it.length == 6 }.orEmpty()
        return StockQuote(
            name = code.ifBlank { query },
            code = code,
            market = "",
            price = "--",
            changeAmount = "--",
            changePercent = "--",
            isRising = true,
            previousClose = 0f,
            high = "--",
            low = "--",
            open = "--",
            totalMarketValue = "--",
            floatMarketValue = "--",
            volumeRatio = "--",
            turnoverRate = "--",
            peTtm = "--",
            pb = "--",
            amount = "--",
            popularityRank = "--"
        )
    }

    private fun emptyMoneyFlow(): StockMoneyFlow =
        StockMoneyFlow("--", "--", "--", "--", "--")

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

    private fun httpGet(
        url: String,
        timeoutMs: Int,
        microCacheMs: Long
    ): String = StockHttpClient.get(
        url = url,
        timeoutMs = timeoutMs,
        emptyMessage = "行情代理返回为空",
        microCacheMs = microCacheMs
    )

    companion object {
        private const val DEFAULT_STOCK_CODE = "600396"
        private const val QUOTE_TIMEOUT_MS = 8_500
        private const val LITE_TIMEOUT_MS = 8_500
        private const val FULL_TIMEOUT_MS = 16_000
        private const val MINUTE_TIMEOUT_MS = 4_000
        private const val KLINE_TIMEOUT_MS = 16_000
        private const val REALTIME_MICRO_CACHE_MS = 220L
        private const val QUOTE_MICRO_CACHE_MS = 350L
        private const val DETAIL_MICRO_CACHE_MS = 350L
        private const val KLINE_MICRO_CACHE_MS = 2_000L
    }
}
