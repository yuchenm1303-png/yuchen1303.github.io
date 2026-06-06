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

private data class StockProxyRoute(
    val name: String,
    val path: String,
    val defaultSourceLabel: String,
    val timeoutMs: Int = 12000
)

class StockRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    private val requestExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "ai-ledger-stock-proxy-http").apply { isDaemon = true }
    }

    fun loadAStock(query: String): StockDetailUiState {
        val base = sampleAStockDetailUiState()
        val normalized = query.trim().ifBlank { base.quote.code }
        val errors = mutableListOf<String>()

        stockProxyRoutes().forEach { route ->
            runCatching {
                return loadFromProxy(normalized, base, route)
            }.onFailure { error ->
                errors += "${route.name}: ${error.message ?: error.javaClass.simpleName}"
            }
        }

        return base.copy(
            dataSourceLabel = "真实行情代理暂未返回，已回退示例数据",
            errorMessage = errors.joinToString("；").ifBlank { "行情代理网络异常" },
            aiSummary = "正在等待真实行情代理返回。当前为本地示例数据，不代表真实行情。"
        )
    }

    private fun stockProxyRoutes(): List<StockProxyRoute> = listOf(
        StockProxyRoute(
            name = "crawl",
            path = "/api/stock/crawl/a-share/detail",
            defaultSourceLabel = "爬虫教学源 · 东方财富公开JSON",
            timeoutMs = 10000
        ),
        StockProxyRoute(
            name = "a-share",
            path = "/api/stock/a-share/detail",
            defaultSourceLabel = "A股聚合行情代理",
            timeoutMs = 10000
        ),
        StockProxyRoute(
            name = "futu-compat",
            path = "/api/stock/futu/a-share/detail",
            defaultSourceLabel = "富途兼容行情代理",
            timeoutMs = 8000
        )
    )

    private fun loadFromProxy(query: String, base: StockDetailUiState, route: StockProxyRoute): StockDetailUiState {
        val baseUrl = proxyBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw IllegalStateException("行情代理地址为空")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = httpGet("$baseUrl${route.path}?query=$encoded&mode=lite", referer = baseUrl, timeoutMs = route.timeoutMs)
        val obj = JSONObject(body)
        val quoteJson = obj.optJSONObject("quote") ?: throw IllegalStateException("代理行情缺少 quote 字段")
        val quote = quoteFromJson(quoteJson, base.quote)
        val kLines = parseKLines(obj).ifEmpty { base.kLinePoints }
        val minutePoints = parseMinutePoints(obj).ifEmpty { minutePointsFromKLines(kLines, base) }
        val sellLevels = parseOrderLevels(obj, listOf("sellLevels", "askLevels", "asks"), true).ifEmpty { base.sellLevels }
        val buyLevels = parseOrderLevels(obj, listOf("buyLevels", "bidLevels", "bids"), false).ifEmpty { base.buyLevels }
        val tradeTicks = parseTradeTicks(obj).ifEmpty { base.tradeTicks }
        val moneyFlow = parseMoneyFlow(obj) ?: base.moneyFlow
        val fundamentals = parseMetrics(obj, "fundamentals").ifEmpty { base.fundamentals }
        val indices = parseIndices(obj).ifEmpty { base.indices }
        val watchlist = parseWatchlist(obj).ifEmpty { base.watchlist }
        val marketBoards = parseMarketBoards(obj).ifEmpty { base.marketBoards }
        val sourceLabel = sourceLabelFromJson(obj, route, quote)

        return base.copy(
            quote = quote,
            topMetrics = topMetricsFor(quote),
            minutePoints = minutePoints,
            sellLevels = sellLevels,
            buyLevels = buyLevels,
            tradeTicks = tradeTicks,
            moneyFlow = moneyFlow,
            fundamentals = fundamentals,
            indices = indices,
            watchlist = watchlist,
            marketBoards = marketBoards,
            kLinePoints = kLines,
            dataSourceLabel = sourceLabel,
            errorMessage = null,
            aiSummary = obj.optString(
                "aiSummary",
                "${quote.name} 当前价 ${quote.price}，涨跌幅 ${quote.changePercent}。行情来自 ${sourceLabel}。"
            )
        )
    }

    private fun sourceLabelFromJson(obj: JSONObject, route: StockProxyRoute, quote: StockQuote): String {
        val explicit = obj.optString("dataSourceLabel").trim()
        if (explicit.isNotBlank()) return explicit

        val provider = obj.optString("provider").lowercase().trim()
        val delayText = if (obj.optBoolean("delayed", false)) " · 延迟" else ""
        val prefix = when {
            provider.contains("eastmoney") || provider.contains("crawl") || route.name == "crawl" -> "爬虫教学源 · 东方财富公开JSON"
            provider.contains("akshare") || route.name == "a-share" -> "A股聚合行情代理"
            provider.contains("futu") || route.name == "futu-compat" -> "富途兼容行情代理"
            else -> route.defaultSourceLabel
        }
        return "$prefix$delayText · ${quote.code}"
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
                add(
                    StockIndexSnapshot(
                        name = item.optString("name", "指数"),
                        value = item.optString("value", "--"),
                        changePercent = changePercent,
                        isRising = item.optBoolean("isRising", !changePercent.startsWith("-"))
                    )
                )
            }
        }
    }

    private fun parseWatchlist(obj: JSONObject): List<StockWatchItem> {
        val array = obj.optJSONArray("watchlist") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val changePercent = item.optString("changePercent", "--")
                add(
                    StockWatchItem(
                        name = item.optString("name", "自选股"),
                        code = item.optString("code", "--"),
                        price = item.optString("price", "--"),
                        changePercent = changePercent,
                        isRising = item.optBoolean("isRising", !changePercent.startsWith("-"))
                    )
                )
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
                add(
                    StockMarketBoard(
                        title = board.optString("title", "市场榜单"),
                        subtitle = board.optString("subtitle", "爬虫市场数据"),
                        items = items
                    )
                )
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

    private fun toneFromJson(item: JSONObject): StockTone {
        return when (item.optString("tone").lowercase()) {
            "rising", "up", "red" -> StockTone.Rising
            "falling", "down", "green" -> StockTone.Falling
            else -> StockTone.Neutral
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
        val future = requestExecutor.submit(Callable { httpGetBlocking(url, referer, timeoutMs) })
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

    private fun httpGetBlocking(url: String, referer: String, timeoutMs: Int): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "AI-Ledger-Android/1.0")
                setRequestProperty("Referer", referer)
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
}
