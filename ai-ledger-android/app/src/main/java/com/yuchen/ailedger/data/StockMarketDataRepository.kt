package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockInformationItem
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMarketBreadth
import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockMarketSentiment
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import com.yuchen.ailedger.model.StockSlowDataSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class StockMarketDataRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    fun loadMarketHome(): Result<StockMarketHomeSnapshot> = runCatching {
        val root = JSONObject(
            httpGet(
                "${baseUrl()}/api/stock/a-share/market/home",
                timeoutMs = MARKET_TIMEOUT_MS
            )
        )

        val boards = buildList {
            addBoard(root, "gainers", "涨幅榜", "真实涨幅排序")
            addBoard(root, "losers", "跌幅榜", "真实跌幅排序")
            addBoard(root, "amountRanking", "成交额榜", "真实成交额排序")
            addBoard(root, "turnoverRanking", "换手率榜", "真实换手率排序")
            addBoard(root, "volumeRatioRanking", "量比榜", "真实量比排序")
            addBoard(root, "speedRanking", "涨速榜", "真实涨速排序")

            loadRankingBoard("main_inflow", "主力净流入榜")?.let(::add)
            loadRankingBoard("main_outflow", "主力净流出榜")?.let(::add)
        }

        val indicesModule = root.optJSONObject("indices")
        val breadthModule = root.optJSONObject("marketBreadth")
        val sentimentModule = root.optJSONObject("sentiment")
        val sectorModule = root.optJSONObject("sectorHotRanking")
        val marketNewsModule = root.optJSONObject("marketNews")
        val popularityModule = root.optJSONObject("popularityRanking")
        val limitUpModule = root.optJSONObject("limitUpSummary")

        StockMarketHomeSnapshot(
            indices = parseIndices(indicesModule),
            marketBreadth = parseBreadth(breadthModule),
            sentiment = parseSentiment(sentimentModule),
            boards = boards.distinctBy { it.title },
            sectors = parseSectors(sectorModule),
            marketNews = parseInformationItems(marketNewsModule),
            marketNewsMeta = metaFromModule(marketNewsModule),
            popularityMeta = metaFromModule(popularityModule),
            limitUpMeta = metaFromModule(limitUpModule),
            updatedAt = firstText(root, "updatedAt").orEmpty(),
            warnings = stringList(root.optJSONArray("warnings"))
        )
    }

    fun loadSlowStock(query: String): Result<StockSlowDataSnapshot> = runCatching {
        val encoded = encode(query.trim())
        val root = JSONObject(
            httpGet(
                "${baseUrl()}/api/stock/a-share/stock/full?query=$encoded",
                timeoutMs = SLOW_TIMEOUT_MS
            )
        )

        val profile = root.optJSONObject("profile")
        val financials = root.optJSONObject("financialsSummary")
        val capital = root.optJSONObject("capitalSummary")
        val popularity = root.optJSONObject("popularity")
        val announcements = root.optJSONObject("announcements")
        val news = root.optJSONObject("news")
        val research = root.optJSONObject("research")
        val performanceForecast = root.optJSONObject("performanceForecast")
        val shareholders = root.optJSONObject("shareholders")
        val unlocks = root.optJSONObject("unlocks")
        val dividends = root.optJSONObject("dividends")

        StockSlowDataSnapshot(
            profileMeta = metaFromModule(profile),
            financialsMeta = metaFromModule(financials),
            capitalMeta = metaFromModule(capital),
            popularityMeta = metaFromModule(popularity),
            announcements = parseInformationItems(announcements),
            announcementsMeta = metaFromModule(announcements),
            news = parseInformationItems(news),
            newsMeta = metaFromModule(news),
            research = parseInformationItems(research),
            researchMeta = metaFromModule(research),
            performanceForecastMeta = metaFromModule(performanceForecast),
            shareholdersMeta = metaFromModule(shareholders),
            unlocksMeta = metaFromModule(unlocks),
            dividendsMeta = metaFromModule(dividends),
            updatedAt = firstText(root, "updatedAt").orEmpty(),
            warnings = stringList(root.optJSONArray("warnings"))
        )
    }

    private fun MutableList<StockMarketBoard>.addBoard(
        root: JSONObject,
        key: String,
        title: String,
        subtitle: String
    ) {
        val module = root.optJSONObject(key) ?: return
        val items = parseRankingItems(module)
        val meta = metaFromModule(module)
        if (items.isNotEmpty()) {
            add(
                StockMarketBoard(
                    title = title,
                    subtitle = "$subtitle · ${meta.source.ifBlank { "公开真实数据" }}",
                    items = items
                )
            )
        }
    }

    private fun loadRankingBoard(type: String, title: String): StockMarketBoard? {
        return runCatching {
            val root = JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/rankings?type=${encode(type)}&limit=20",
                    timeoutMs = RANKING_TIMEOUT_MS
                )
            )
            val items = parseRankingItems(root)
            if (items.isEmpty()) {
                null
            } else {
                val meta = metaFromModule(root)
                StockMarketBoard(
                    title = title,
                    subtitle = "真实资金排序 · ${meta.source.ifBlank { "公开数据" }}",
                    items = items
                )
            }
        }.getOrNull()
    }

    private fun parseIndices(module: JSONObject?): List<StockIndexSnapshot> {
        val array = moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val changePercent = firstText(item, "changePercent", "pct").orEmpty().ifBlank { "--" }
                add(
                    StockIndexSnapshot(
                        name = firstText(item, "name").orEmpty().ifBlank { "指数" },
                        value = firstText(item, "price", "value").orEmpty().ifBlank { "--" },
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun parseBreadth(module: JSONObject?): StockMarketBreadth {
        val meta = metaFromModule(module)
        val item = moduleItemsObject(module)
        return StockMarketBreadth(
            upCount = firstInt(item, "upCount"),
            downCount = firstInt(item, "downCount"),
            flatCount = firstInt(item, "flatCount"),
            limitUpCount = firstInt(item, "limitUpCount"),
            limitDownCount = firstInt(item, "limitDownCount"),
            brokenBoardCount = firstInt(item, "brokenBoardCount"),
            brokenBoardRate = firstDouble(item, "brokenBoardRate"),
            maxConsecutiveBoards = firstInt(item, "maxConsecutiveBoards"),
            redRate = firstDouble(item, "redRate"),
            medianChangePercent = firstDouble(item, "medianChangePercent"),
            marketAmount = firstText(item, "marketAmount").orEmpty().ifBlank { "--" },
            shszAmount = firstText(item, "shszAmount").orEmpty().ifBlank { "--" },
            bjAmount = firstText(item, "bjAmount").orEmpty().ifBlank { "--" },
            moneyMakingEffect = firstDouble(item, "moneyMakingEffect"),
            updatedAt = firstText(item, "updatedAt").orEmpty(),
            meta = meta
        )
    }

    private fun parseSentiment(module: JSONObject?): StockMarketSentiment {
        val meta = metaFromModule(module)
        val item = moduleItemsObject(module)
        return StockMarketSentiment(
            temperature = firstDouble(item, "sentimentTemperature", "temperature"),
            level = firstText(item, "sentimentLevel", "level").orEmpty(),
            formula = firstText(item, "formula").orEmpty(),
            redRate = firstDouble(item, "redRate"),
            limitUpCount = firstInt(item, "limitUpCount"),
            moneyMakingEffect = firstDouble(item, "moneyMakingEffect"),
            meta = meta
        )
    }

    private fun parseSectors(module: JSONObject?): List<StockSectorSnapshot> {
        val array = moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = firstText(item, "sectorCode", "code").orEmpty()
                val name = firstText(item, "sectorName", "name").orEmpty()
                if (code.isBlank() && name.isBlank()) continue
                add(
                    StockSectorSnapshot(
                        sectorCode = code,
                        sectorName = name.ifBlank { code },
                        type = firstText(item, "type").orEmpty(),
                        changePercent = firstText(item, "changePercent", "pct").orEmpty().ifBlank { "--" },
                        upCount = firstInt(item, "upCount"),
                        downCount = firstInt(item, "downCount"),
                        flatCount = firstInt(item, "flatCount"),
                        leaderName = firstText(item, "leaderName").orEmpty(),
                        leaderChangePercent = firstText(item, "leaderChangePercent").orEmpty(),
                        amount = firstText(item, "amount").orEmpty(),
                        turnoverRate = firstText(item, "turnoverRate").orEmpty(),
                        mainInflow = firstText(item, "mainInflow").orEmpty(),
                        heatRank = firstInt(item, "heatRank"),
                        updatedAt = firstText(item, "updatedAt").orEmpty()
                    )
                )
            }
        }
    }

    private fun parseRankingItems(module: JSONObject?): List<StockRankItem> {
        val array = moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = firstText(item, "code", "symbol").orEmpty()
                val name = firstText(item, "name", "stockName").orEmpty()
                if (code.isBlank() && name.isBlank()) continue
                val changePercent = firstText(item, "changePercent", "pct").orEmpty().ifBlank { "--" }
                val displayValue = firstText(
                    item,
                    "price",
                    "mainInflow",
                    "amount",
                    "turnoverRate",
                    "volumeRatio",
                    "changeSpeed",
                    "value"
                ).orEmpty().ifBlank { "--" }
                add(
                    StockRankItem(
                        name = name.ifBlank { code },
                        code = code,
                        value = displayValue,
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun parseInformationItems(module: JSONObject?): List<StockInformationItem> {
        val array = moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = firstText(item, "title", "name").orEmpty()
                if (title.isBlank()) continue
                add(
                    StockInformationItem(
                        id = firstText(item, "id", "reportId").orEmpty(),
                        title = title,
                        summary = firstText(item, "summary", "description").orEmpty(),
                        publishTime = firstText(item, "publishTime", "time", "updatedAt").orEmpty(),
                        source = firstText(item, "source", "institution").orEmpty(),
                        url = firstText(item, "url", "attachmentUrl").orEmpty()
                    )
                )
            }
        }
    }

    private fun metaFromModule(module: JSONObject?): StockModuleMeta {
        if (module == null) return StockModuleMeta()
        return StockModuleMeta(
            status = StockModuleStatus.fromWire(firstText(module, "status")),
            source = firstText(module, "source").orEmpty(),
            sourceUrlType = firstText(module, "sourceUrlType").orEmpty(),
            updatedAt = firstText(module, "updatedAt").orEmpty(),
            cacheAgeMs = firstLong(module, "cacheAgeMs") ?: 0L,
            isDerived = module.optBoolean("isDerived", false),
            warnings = stringList(module.optJSONArray("warnings"))
        )
    }

    private fun moduleItemsArray(module: JSONObject?): JSONArray? {
        if (module == null) return null
        return module.optJSONArray("items")
            ?: module.optJSONArray("data")
            ?: module.optJSONObject("data")?.optJSONArray("items")
    }

    private fun moduleItemsObject(module: JSONObject?): JSONObject {
        if (module == null) return JSONObject()
        return module.optJSONObject("items")
            ?: module.optJSONObject("data")
            ?: JSONObject()
    }

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
            val text = obj.opt(key)?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && text != "null" && text != "NaN") return text
        }
        return null
    }

    private fun firstInt(obj: JSONObject?, vararg keys: String): Int? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toInt()
                is String -> raw.toDoubleOrNull()?.toInt()
                else -> null
            }
            if (value != null) return value
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

    private fun firstDouble(obj: JSONObject?, vararg keys: String): Double? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = when (val raw = obj.opt(key)) {
                is Number -> raw.toDouble()
                is String -> raw.replace("%", "").replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (value != null && !value.isNaN()) return value
        }
        return null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun baseUrl(): String {
        val value = proxyBaseUrl.trim().trimEnd('/')
        if (value.isBlank()) throw IllegalStateException("行情代理地址为空")
        return value
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
                setRequestProperty("Connection", "keep-alive")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${body.take(160)}".trim())
            }
            if (body.isBlank()) throw IllegalStateException("股票扩展数据返回为空")
            return body
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MARKET_TIMEOUT_MS = 18_000
        private const val RANKING_TIMEOUT_MS = 8_000
        private const val SLOW_TIMEOUT_MS = 12_000
    }
}
