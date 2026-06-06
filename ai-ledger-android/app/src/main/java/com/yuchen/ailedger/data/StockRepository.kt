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
        return runCatching {
            val encoded = URLEncoder.encode(normalized, "UTF-8")
            val safeMode = if (mode == "full") "full" else "lite"
            val body = httpGet(
                "${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=$safeMode",
                timeoutMs = if (safeMode == "full") 16000 else 8500
            )
            parseDetail(JSONObject(body), base)
        }.getOrElse { error ->
            base.copy(
                dataSourceLabel = "真实行情代理暂未返回，已回退示例数据",
                errorMessage = error.message ?: error.javaClass.simpleName,
                aiSummary = "正在等待真实行情代理返回。当前为本地示例数据，不代表真实行情。"
            )
        }
    }

    fun loadKLinePoints(query: String): Result<List<StockKLinePoint>> {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        val encoded = URLEncoder.encode(normalized, "UTF-8")
        return runCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/kline?query=$encoded&limit=120", timeoutMs = 16000)
            parseKLines(JSONObject(body)).ifEmpty { throw IllegalStateException("K线接口返回为空") }
        }.recoverCatching {
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/detail?query=$encoded&mode=full", timeoutMs = 18000)
            parseKLines(JSONObject(body)).ifEmpty { throw IllegalStateException("full详情接口未返回历史K线") }
        }
    }

    fun loadMarketOverview(query: String, current: StockDetailUiState): StockDetailUiState {
        val normalized = query.trim().ifBlank { current.quote.code }
        return runCatching {
            val encoded = URLEncoder.encode(normalized, "UTF-8")
            val body = httpGet("${baseUrl()}/api/stock/crawl/a-share/market/overview?query=$encoded", timeoutMs = 8500)
            val obj = JSONObject(body)
            current.copy(
                indices = parseIndices(obj).ifEmpty { current.indices },
                watchlist = parseWatchlist(obj).ifEmpty { current.watchlist },
                marketBoards = parseMarketBoards(obj).ifEmpty { current.marketBoards },
                dataSourceLabel = obj.optString("dataSourceLabel", current.dataSourceLabel).ifBlank { current.dataSourceLabel }
            )
        }.getOrElse { current }
    }

    private fun parseDetail(obj: JSONObject, base: StockDetailUiState): StockDetailUiState {
        val quoteJson = obj.optJSONObject("quote") ?: throw IllegalStateException("代理行情缺少 quote 字段")
        val quote = quoteFromJson(quoteJson, base.quote)
        val kLines = parseKLines(obj)
        val minutePoints = parseMinutePoints(obj).ifEmpty { minutePointsFromKLines(kLines, base) }
        val sellLevels = parseOrderLevels(obj, listOf("sellLevels", "askLevels", "asks"), true).ifEmpty { base.sellLevels }
        val buyLevels = parseOrderLevels(obj, listOf("buyLevels", "bidLevels", "bids"), false).ifEmpty { base.buyLevels }
        val tradeTicks = parseTradeTicks(obj).ifEmpty { ticksFromMinute(minutePoints, quote, base) }
        val moneyFlow = parseMoneyFlow(obj) ?: base.moneyFlow
        val fundamentals = parseMetrics(obj, "fundamentals").ifEmpty { fundamentalsFor(quote) }
        val sourceLabel = obj.optString("dataSourceLabel", "爬虫教学源 · 东方财富公开JSON · ${quote.code}")

        return base.copy(
            quote = quote,
            topMetrics = topMetricsFor(quote),
            minutePoints = minutePoints,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = tradeTicks,
            moneyFlow = moneyFlow,
            fundamentals = fundamentals,
            indices = parseIndices(obj).ifEmpty { base.indices },
            watchlist = parseWatchlist(obj).ifEmpty { base.watchlist },
            marketBoards = parseMarketBoards(obj).ifEmpty { base.marketBoards },
            kLinePoints = kLines,
            dataSourceLabel = sourceLabel,
            errorMessage = null,
            aiSummary = obj.optString(
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

    private fun quoteFromJson(json: JSONObject, fallback: StockQuote): StockQuote = StockQuote(
        name = json.optString("name", fallback.name),
        code = json.optString("code", fallback.code),
        market = json.optString("market", fallback.market),
        price = json.optString("price", fallback.price),
        changeAmount = json.optString("changeAmount", fallback.changeAmount),
        changePercent = json.optString("changePercent", fallback.changePercent),
        isRising = json.optBoolean(
            "isRising",
            !json.optString("changePercent", fallback.changePercent).startsWith("-") && !json.optString("changeAmount", fallback.changeAmount).startsWith("-")
        ),
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
                val open = item.optDouble("open", Double.NaN).toFloat()
                val close = item.optDouble("close", Double.NaN).toFloat()
                val high = item.optDouble("high", Double.NaN).toFloat()
                val low = item.optDouble("low", Double.NaN).toFloat()
                if (open.isNaN() || close.isNaN() || high.isNaN() || low.isNaN()) continue
                add(
                    StockKLinePoint(
                        date = item.optString("date"),
                        open = open,
                        close = close,
                        high = high,
                        low = low,
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
                val price = item.optDouble("price", Double.NaN).toFloat()
                if (price.isNaN() || price <= 0f) continue
                add(
                    StockMinutePoint(
                        time = item.optString("time"),
                        price = price,
                        average = item.optDouble("average", price.toDouble()).toFloat(),
                        volumeRatio = item.optDouble("volumeRatio", 0.0).toFloat().coerceIn(0.02f, 1f)
                    )
                )
            }
        }
    }

    private fun parseOrderLevels(obj: JSONObject, keys: List<String>, isAsk: Boolean): List<StockOrderLevel> {
        val array = keys.firstNotNullOfOrNull { key -> obj.optJSONArray(key) } ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    StockOrderLevel(
                        label = item.optString("label", if (isAsk) "卖${i + 1}" else "买${i + 1}"),
                        price = item.optString("price", "--"),
                        volume = item.optString("volume", item.optString("qty", "--")),
                        isAsk = item.optBoolean("isAsk", isAsk)
                    )
                )
            }
        }
    }

    private fun parseTradeTicks(obj: JSONObject): List<StockTradeTick> {
        val array = obj.optJSONArray("tradeTicks") ?: obj.optJSONArray("ticks") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val direction = item.optString("direction", "--")
                add(
                    StockTradeTick(
                        time = item.optString("time", "--"),
                        price = item.optString("price", "--"),
                        volume = item.optString("volume", item.optString("qty", "--")),
                        direction = direction,
                        isBuy = item.optBoolean("isBuy", direction.contains("买") || direction.equals("buy", ignoreCase = true))
                    )
                )
            }
        }
    }

    private fun parseMoneyFlow(obj: JSONObject): StockMoneyFlow? {
        val flow = obj.optJSONObject("moneyFlow") ?: return null
        return StockMoneyFlow(
            mainInflow = flow.optString("mainInflow", "--"),
            superLargeOrder = flow.optString("superLargeOrder", "--"),
            largeOrder = flow.optString("largeOrder", "--"),
            mediumOrder = flow.optString("mediumOrder", "--"),
            smallOrder = flow.optString("smallOrder", "--")
        )
    }

    private fun parseMetrics(obj: JSONObject, key: String): List<StockMetric> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(StockMetric(item.optString("label", "--"), item.optString("value", "--"), toneFromJson(item)))
            }
        }
    }

    private fun parseIndices(obj: JSONObject): List<StockIndexSnapshot> {
        val array = obj.optJSONArray("indices") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val changePercent = item.optString("changePercent", "--")
                add(StockIndexSnapshot(item.optString("name", "指数"), item.optString("value", "--"), changePercent, item.optBoolean("isRising", !changePercent.startsWith("-"))))
            }
        }
    }

    private fun parseWatchlist(obj: JSONObject): List<StockWatchItem> {
        val array = obj.optJSONArray("watchlist") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val changePercent = item.optString("changePercent", "--")
                add(StockWatchItem(item.optString("name", "自选股"), item.optString("code", "--"), item.optString("price", item.optString("value", "--")), changePercent, item.optBoolean("isRising", !changePercent.startsWith("-"))))
            }
        }
    }

    private fun parseMarketBoards(obj: JSONObject): List<StockMarketBoard> {
        val array = obj.optJSONArray("marketBoards") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val board = array.optJSONObject(i) ?: continue
                val itemsArray = board.optJSONArray("items")
                val items = buildList {
                    if (itemsArray != null) {
                        for (j in 0 until itemsArray.length()) {
                            val item = itemsArray.optJSONObject(j) ?: continue
                            add(parseRankItem(item))
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    add(StockMarketBoard(board.optString("title", "市场榜单"), board.optString("subtitle", "爬虫市场数据"), items))
                }
            }
        }
    }

    private fun parseRankItem(item: JSONObject): StockRankItem {
        val changePercent = item.optString("changePercent", "--")
        return StockRankItem(
            name = item.optString("name", "--"),
            code = item.optString("code", "--"),
            value = item.optString("value", item.optString("price", "--")),
            changePercent = changePercent,
            isRising = item.optBoolean("isRising", !changePercent.startsWith("-"))
        )
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
