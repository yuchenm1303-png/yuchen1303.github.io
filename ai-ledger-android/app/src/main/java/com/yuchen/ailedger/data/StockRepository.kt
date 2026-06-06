package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockMoneyFlow
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockQuote
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.StockWatchItem
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.json.JSONArray
import org.json.JSONObject

class StockRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    private val requestExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "ai-ledger-stock-proxy-http").apply { isDaemon = true }
    }

    fun loadAStock(query: String, mode: String = "lite"): StockDetailUiState {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        val safeMode = if (mode == "full") "full" else "lite"
        val encoded = encode(normalized)

        val detailResult = runCatching {
            val body = httpGet(
                "${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=$safeMode",
                timeoutMs = if (safeMode == "full") 16000 else 8500
            )
            parseDetail(JSONObject(body), base)
        }

        val quoteResult = loadQuoteSnapshot(normalized)
        val minuteResult = loadMinutePoints(normalized)
        val dailyKLineResult = if (safeMode == "full") loadKLinePoints(normalized, period = "daily") else Result.success(emptyList())

        val detail = detailResult.getOrElse { error ->
            val quote = quoteResult.getOrNull()
            if (quote != null) {
                base.copy(
                    quote = quote,
                    topMetrics = topMetricsFor(quote),
                    fundamentals = fundamentalsFor(quote),
                    dataSourceLabel = "A股实时行情代理 · quotes",
                    errorMessage = null,
                    aiSummary = "${quote.name} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。已通过批量报价接口返回。"
                )
            } else {
                base.copy(
                    dataSourceLabel = "真实行情代理暂未返回，已回退示例数据",
                    errorMessage = error.message ?: error.javaClass.simpleName,
                    aiSummary = "正在等待真实行情代理返回。当前为本地示例数据，不代表真实行情。"
                )
            }
        }

        val mergedQuote = quoteResult.getOrNull() ?: detail.quote
        val mergedMinute = minuteResult.getOrNull().takeUnless { it.isNullOrEmpty() } ?: detail.minutePoints
        val mergedKLines = dailyKLineResult.getOrNull().takeUnless { it.isNullOrEmpty() } ?: detail.kLinePoints
        val label = when {
            quoteResult.isSuccess || minuteResult.isSuccess || dailyKLineResult.isSuccess -> "A股实时行情代理 · quotes/minute/kline"
            else -> detail.dataSourceLabel
        }

        return detail.copy(
            quote = mergedQuote,
            topMetrics = topMetricsFor(mergedQuote),
            minutePoints = mergedMinute,
            kLinePoints = mergedKLines,
            fundamentals = detail.fundamentals.ifEmpty { fundamentalsFor(mergedQuote) },
            dataSourceLabel = label,
            errorMessage = if (quoteResult.isSuccess || minuteResult.isSuccess || detailResult.isSuccess) null else detail.errorMessage,
            aiSummary = detail.aiSummary.ifBlank { "${mergedQuote.name} 当前价 ${mergedQuote.price}，涨跌幅 ${mergedQuote.changePercent}。" }
        )
    }

    fun loadKLinePoints(query: String, period: String = "daily"): Result<List<StockKLinePoint>> {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        val encoded = encode(normalized)
        val safePeriod = when (period.lowercase()) {
            "weekly", "week", "周k" -> "weekly"
            "monthly", "month", "月k" -> "monthly"
            else -> "daily"
        }
        return runCatching {
            val body = httpGet("${baseUrl()}/api/stock/a-share/kline?query=$encoded&period=$safePeriod&limit=160", timeoutMs = 16000)
            parseKLines(JSONObject(body)).ifEmpty { throw IllegalStateException("$safePeriod K线接口返回为空") }
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/kline?query=$encoded&period=$safePeriod&limit=160", timeoutMs = 16000)
            parseKLines(JSONObject(body)).ifEmpty { throw IllegalStateException("crawl $safePeriod K线接口返回为空") }
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=full", timeoutMs = 18000)
            parseKLines(JSONObject(body)).ifEmpty { throw IllegalStateException("full详情接口未返回历史K线") }
        }
    }

    fun loadMinutePoints(query: String): Result<List<StockMinutePoint>> {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        val encoded = encode(normalized)
        return runCatching {
            val body = httpGet("${baseUrl()}/api/stock/a-share/minute?query=$encoded", timeoutMs = 12000)
            parseMinutePoints(JSONObject(body)).ifEmpty { throw IllegalStateException("分时接口返回为空") }
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/minute?query=$encoded", timeoutMs = 12000)
            parseMinutePoints(JSONObject(body)).ifEmpty { throw IllegalStateException("crawl分时接口返回为空") }
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=lite", timeoutMs = 12000)
            parseMinutePoints(JSONObject(body)).ifEmpty { throw IllegalStateException("detail未返回分时") }
        }
    }

    fun loadMarketOverview(query: String, current: StockDetailUiState): StockDetailUiState {
        val normalized = query.trim().ifBlank { current.quote.code }
        val encoded = encode(normalized)
        val oldOverview = runCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/market/overview?query=$encoded", timeoutMs = 8500)
            val obj = JSONObject(body)
            current.copy(
                indices = parseIndices(obj).ifEmpty { current.indices },
                watchlist = parseWatchlist(obj).ifEmpty { current.watchlist },
                marketBoards = parseMarketBoards(obj).ifEmpty { current.marketBoards },
                dataSourceLabel = obj.optString("dataSourceLabel", current.dataSourceLabel).ifBlank { current.dataSourceLabel }
            )
        }.getOrElse { current }

        val watchCodes = buildList {
            add(normalized)
            oldOverview.watchlist.map { it.code }.filter { it.isNotBlank() && it != "--" }.forEach { add(it) }
            add("600519")
            add("000001")
            add("300750")
        }.distinct().take(8)

        val liveWatchlist = loadQuoteWatchlist(watchCodes).getOrNull().orEmpty()
        val poolBoard = loadStockPoolBoard().getOrNull()
        val searchBoard = loadSearchBoard(normalized).getOrNull()
        val mergedBoards = buildList {
            oldOverview.marketBoards.forEach { add(it) }
            searchBoard?.takeIf { it.items.isNotEmpty() }?.let { add(it) }
            poolBoard?.takeIf { it.items.isNotEmpty() }?.let { add(it) }
        }.distinctBy { it.title }

        return oldOverview.copy(
            watchlist = liveWatchlist.ifEmpty { oldOverview.watchlist },
            marketBoards = mergedBoards.ifEmpty { oldOverview.marketBoards },
            dataSourceLabel = "A股实时行情代理 · list/search/quotes"
        )
    }

    private fun loadQuoteSnapshot(query: String): Result<StockQuote> {
        val base = sampleAStockDetailUiState()
        val encoded = encode(query.trim().ifBlank { base.quote.code })
        return runCatching {
            val body = httpGet("${baseUrl()}/api/stock/a-share/quotes?codes=$encoded", timeoutMs = 8500)
            val quoteJson = parseQuoteObjects(JSONObject(body)).firstOrNull() ?: throw IllegalStateException("quotes接口没有报价对象")
            quoteFromJson(quoteJson, base.quote)
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encoded", timeoutMs = 8500)
            val quoteJson = parseQuoteObjects(JSONObject(body)).firstOrNull() ?: throw IllegalStateException("crawl quotes接口没有报价对象")
            quoteFromJson(quoteJson, base.quote)
        }
    }

    private fun loadQuoteWatchlist(codes: List<String>): Result<List<StockWatchItem>> {
        if (codes.isEmpty()) return Result.success(emptyList())
        val encoded = encode(codes.joinToString(","))
        return runCatching {
            val body = httpGet("${baseUrl()}/api/stock/a-share/quotes?codes=$encoded", timeoutMs = 9000)
            parseWatchItemsFromQuotes(JSONObject(body))
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/quotes?codes=$encoded", timeoutMs = 9000)
            parseWatchItemsFromQuotes(JSONObject(body))
        }
    }

    private fun loadStockPoolBoard(): Result<StockMarketBoard> = runCatching {
        val body = httpGet("${baseUrl()}/api/stock/a-share/list?limit=20", timeoutMs = 9000)
        val items = parseRankItemsFromAny(JSONObject(body)).take(8)
        StockMarketBoard("股票池", "A股股票池实时返回", items)
    }

    private fun loadSearchBoard(query: String): Result<StockMarketBoard> = runCatching {
        val encoded = encode(query)
        val body = httpGet("${baseUrl()}/api/stock/a-share/search?query=$encoded", timeoutMs = 8500)
        val items = parseRankItemsFromAny(JSONObject(body)).take(6)
        StockMarketBoard("搜索结果", "${query} 相关股票", items)
    }

    private fun parseDetail(obj: JSONObject, base: StockDetailUiState): StockDetailUiState {
        val payload = payloadObject(obj)
        val quoteJson = payload.optJSONObject("quote") ?: parseQuoteObjects(payload).firstOrNull() ?: throw IllegalStateException("代理行情缺少 quote 字段")
        val quote = quoteFromJson(quoteJson, base.quote)
        val kLines = parseKLines(payload)
        val minutePoints = parseMinutePoints(payload).ifEmpty { minutePointsFromKLines(kLines, base) }
        val sellLevels = parseOrderLevels(payload, listOf("sellLevels", "askLevels", "asks"), true).ifEmpty { base.sellLevels }
        val buyLevels = parseOrderLevels(payload, listOf("buyLevels", "bidLevels", "bids"), false).ifEmpty { base.buyLevels }
        val tradeTicks = parseTradeTicks(payload).ifEmpty { ticksFromMinute(minutePoints, quote, base) }
        val moneyFlow = parseMoneyFlow(payload) ?: base.moneyFlow
        val fundamentals = parseMetrics(payload, "fundamentals").ifEmpty { fundamentalsFor(quote) }
        val sourceLabel = payload.optString("dataSourceLabel", "爬虫教学源 · 东方财富公开JSON · ${quote.code}")

        return base.copy(
            quote = quote,
            topMetrics = topMetricsFor(quote),
            minutePoints = minutePoints,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = tradeTicks,
            moneyFlow = moneyFlow,
            fundamentals = fundamentals,
            indices = parseIndices(payload).ifEmpty { base.indices },
            watchlist = parseWatchlist(payload).ifEmpty { base.watchlist },
            marketBoards = parseMarketBoards(payload).ifEmpty { base.marketBoards },
            kLinePoints = kLines,
            dataSourceLabel = sourceLabel,
            errorMessage = null,
            aiSummary = payload.optString(
                "aiSummary",
                "${quote.name} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。行情来自 ${sourceLabel}。"
            )
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

    private fun ticksFromMinute(points: List<StockMinutePoint>, quote: StockQuote, base: StockDetailUiState): List<StockTradeTick> {
        if (points.isEmpty()) return base.tradeTicks
        return points.takeLast(8).reversed().mapIndexed { index, point ->
            val previous = points.getOrNull(points.lastIndex - index - 1)?.price ?: quote.previousClose
            val isBuy = point.price >= previous
            StockTradeTick(
                time = point.time.ifBlank { "--" },
                price = formatTwo(point.price),
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

    private fun parseKLines(obj: JSONObject): List<StockKLinePoint> {
        val array = findArray(obj, listOf("kLinePoints", "klinePoints", "klines", "kLines", "items", "data", "result")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val open = firstDouble(item, "open", "o", "开盘") ?: continue
                val close = firstDouble(item, "close", "c", "收盘", "price") ?: continue
                val high = firstDouble(item, "high", "h", "最高") ?: close
                val low = firstDouble(item, "low", "l", "最低") ?: close
                add(
                    StockKLinePoint(
                        date = firstText(item, "date", "day", "time", "日期") ?: "",
                        open = open,
                        close = close,
                        high = high,
                        low = low,
                        volume = firstDouble(item, "volume", "vol", "成交量") ?: 0f,
                        amount = firstDouble(item, "amount", "成交额") ?: 0f,
                        changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--"
                    )
                )
            }
        }
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
                        time = firstText(item, "time", "minute", "t", "时间") ?: "",
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

    private fun parseMoneyFlow(obj: JSONObject): StockMoneyFlow? {
        val flow = obj.optJSONObject("moneyFlow") ?: obj.optJSONObject("fundFlow") ?: return null
        return StockMoneyFlow(
            mainInflow = firstText(flow, "mainInflow", "main", "主力净流入") ?: "--",
            superLargeOrder = firstText(flow, "superLargeOrder", "superLarge", "超大单") ?: "--",
            largeOrder = firstText(flow, "largeOrder", "large", "大单") ?: "--",
            mediumOrder = firstText(flow, "mediumOrder", "medium", "中单") ?: "--",
            smallOrder = firstText(flow, "smallOrder", "small", "小单") ?: "--"
        )
    }

    private fun parseMetrics(obj: JSONObject, key: String): List<StockMetric> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(StockMetric(firstText(item, "label", "name") ?: "--", firstText(item, "value") ?: "--", toneFromJson(item)))
            }
        }
    }

    private fun parseIndices(obj: JSONObject): List<StockIndexSnapshot> {
        val array = findArray(obj, listOf("indices", "indexList", "indexes")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--"
                add(
                    StockIndexSnapshot(
                        firstText(item, "name", "indexName", "名称") ?: "指数",
                        firstText(item, "value", "price", "latest") ?: "--",
                        changePercent,
                        item.optBoolean("isRising", !changePercent.startsWith("-"))
                    )
                )
            }
        }
    }

    private fun parseWatchlist(obj: JSONObject): List<StockWatchItem> {
        val array = findArray(obj, listOf("watchlist", "watchList", "quotes", "items")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--"
                add(
                    StockWatchItem(
                        firstText(item, "name", "stockName", "名称") ?: "自选股",
                        firstText(item, "code", "symbol", "代码") ?: "--",
                        firstText(item, "price", "value", "latest") ?: "--",
                        changePercent,
                        item.optBoolean("isRising", !changePercent.startsWith("-"))
                    )
                )
            }
        }
    }

    private fun parseWatchItemsFromQuotes(obj: JSONObject): List<StockWatchItem> = parseQuoteObjects(obj).map { item ->
        val changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--"
        StockWatchItem(
            name = firstText(item, "name", "stockName", "名称") ?: "自选股",
            code = firstText(item, "code", "symbol", "代码") ?: "--",
            price = firstText(item, "price", "value", "latest", "close") ?: "--",
            changePercent = changePercent,
            isRising = item.optBoolean("isRising", !changePercent.startsWith("-"))
        )
    }

    private fun parseMarketBoards(obj: JSONObject): List<StockMarketBoard> {
        val array = obj.optJSONArray("marketBoards") ?: obj.optJSONArray("boards") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val board = array.optJSONObject(i) ?: continue
                val items = parseRankItemsFromAny(board)
                if (items.isNotEmpty()) add(StockMarketBoard(board.optString("title", "市场榜单"), board.optString("subtitle", "爬虫市场数据"), items))
            }
        }
    }

    private fun parseRankItemsFromAny(obj: JSONObject): List<StockRankItem> {
        val array = findArray(obj, listOf("items", "stocks", "list", "records", "quotes", "data", "result")) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(parseRankItem(it)) }
        }
    }

    private fun parseRankItem(item: JSONObject): StockRankItem {
        val changePercent = firstText(item, "changePercent", "pct", "涨跌幅") ?: "--"
        return StockRankItem(
            name = firstText(item, "name", "stockName", "名称") ?: "--",
            code = firstText(item, "code", "symbol", "代码") ?: "--",
            value = firstText(item, "value", "price", "latest", "close") ?: "--",
            changePercent = changePercent,
            isRising = item.optBoolean("isRising", !changePercent.startsWith("-"))
        )
    }

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

    private fun toneFromJson(item: JSONObject): StockTone = when (item.optString("tone").lowercase()) {
        "rising", "up", "red" -> StockTone.Rising
        "falling", "down", "green" -> StockTone.Falling
        else -> StockTone.Neutral
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

    private fun httpGet(url: String, timeoutMs: Int): String {
        val future = requestExecutor.submit(Callable { httpGetBlocking(url, timeoutMs) })
        return try {
            future.get((timeoutMs + 1500).toLong(), TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("行情代理请求超时 ${timeoutMs}ms")
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
            if (body.isBlank()) throw IllegalStateException("行情代理返回为空")
            return body
        } finally {
            connection?.disconnect()
        }
    }

    private fun formatTwo(value: Float): String = "%.2f".format(value)
}
