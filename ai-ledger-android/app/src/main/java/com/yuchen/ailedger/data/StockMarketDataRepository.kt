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
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class StockMarketDataRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    @Volatile
    private var lastSuccessfulHome: StockMarketHomeSnapshot? = null

    fun loadMarketHome(): Result<StockMarketHomeSnapshot> {
        return runCatching {
            val root = JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/market/home",
                    MARKET_TIMEOUT_MS,
                    MARKET_MICRO_CACHE_MS
                )
            )
            parseMarketHome(root).also { snapshot ->
                if (snapshot.hasUsefulData()) lastSuccessfulHome = snapshot
            }
        }.recoverCatching { error ->
            lastSuccessfulHome?.asClientStale(error) ?: throw error
        }
    }

    fun loadSlowStock(query: String): Result<StockSlowDataSnapshot> = runCatching {
        val encoded = encode(query.trim())
        val payload = payloadObject(
            JSONObject(
                httpGet(
                    "${baseUrl()}/api/stock/a-share/stock/full?query=$encoded",
                    SLOW_TIMEOUT_MS,
                    SLOW_MICRO_CACHE_MS
                )
            )
        )

        val profile = payload.optJSONObject("profile")
        val financials = payload.optJSONObject("financialsSummary")
        val capital = payload.optJSONObject("capitalSummary")
        val popularity = payload.optJSONObject("popularity")
        val announcements = payload.optJSONObject("announcements")
        val news = payload.optJSONObject("news")
        val research = payload.optJSONObject("research")
        val performanceForecast = payload.optJSONObject("performanceForecast")
        val shareholders = payload.optJSONObject("shareholders")
        val unlocks = payload.optJSONObject("unlocks")
        val dividends = payload.optJSONObject("dividends")

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
            updatedAt = firstText(payload, "updatedAt").orEmpty(),
            warnings = stringList(payload.optJSONArray("warnings"))
        )
    }

    private fun parseMarketHome(root: JSONObject): StockMarketHomeSnapshot {
        val payload = payloadObject(root)
        val boards = buildList {
            addBoard(payload, "gainers", "涨幅榜", "真实涨幅排序")
            addBoard(payload, "losers", "跌幅榜", "真实跌幅排序")
            addBoard(payload, "amountRanking", "成交额榜", "真实成交额排序")
            addBoard(payload, "turnoverRanking", "换手率榜", "真实换手率排序")
            addBoard(payload, "volumeRatioRanking", "量比榜", "真实量比排序")
            addBoard(payload, "speedRanking", "涨速榜", "真实涨速排序")
            addBoard(payload, "mainInflowRanking", "主力净流入榜", "真实主力净流入排序")
            addBoard(payload, "mainOutflowRanking", "主力净流出榜", "真实主力净流出排序")
        }

        val indicesModule = payload.optJSONObject("indices")
        val breadthModule = payload.optJSONObject("marketBreadth")
        val sentimentModule = payload.optJSONObject("sentiment")
        val sectorModule = payload.optJSONObject("sectorHotRanking")
        val marketNewsModule = payload.optJSONObject("marketNews")
        val popularityModule = payload.optJSONObject("popularityRanking")
        val limitUpModule = payload.optJSONObject("limitUpSummary")

        return StockMarketHomeSnapshot(
            indices = parseIndices(indicesModule),
            indicesMeta = metaFromModule(indicesModule),
            marketBreadth = parseBreadth(breadthModule),
            sentiment = parseSentiment(sentimentModule),
            boards = boards.distinctBy { it.title },
            sectors = parseSectors(sectorModule),
            marketNews = parseInformationItems(marketNewsModule),
            marketNewsMeta = metaFromModule(marketNewsModule),
            popularityMeta = metaFromModule(popularityModule),
            limitUpMeta = metaFromModule(limitUpModule),
            updatedAt = firstText(payload, "updatedAt").orEmpty(),
            warnings = stringList(payload.optJSONArray("warnings"))
        )
    }

    private fun StockMarketHomeSnapshot.hasUsefulData(): Boolean {
        return indices.isNotEmpty() ||
            marketBreadth.meta.hasRealData ||
            sentiment.meta.hasRealData ||
            boards.isNotEmpty() ||
            sectors.isNotEmpty()
    }

    private fun StockMarketHomeSnapshot.asClientStale(error: Throwable): StockMarketHomeSnapshot {
        val warning = "android_refresh_failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        return copy(
            indicesMeta = indicesMeta.asClientStale(warning),
            marketBreadth = marketBreadth.copy(meta = marketBreadth.meta.asClientStale(warning)),
            sentiment = sentiment.copy(meta = sentiment.meta.asClientStale(warning)),
            marketNewsMeta = marketNewsMeta.asClientStale(warning),
            popularityMeta = popularityMeta.asClientStale(warning),
            limitUpMeta = limitUpMeta.asClientStale(warning),
            warnings = (warnings + warning).distinct()
        )
    }

    private fun StockModuleMeta.asClientStale(warning: String): StockModuleMeta {
        return copy(
            status = if (hasRealData) StockModuleStatus.Stale else status,
            warnings = (warnings + warning).distinct()
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
        if (items.isNotEmpty() && meta.hasRealData) {
            add(
                StockMarketBoard(
                    title = title,
                    subtitle = "$subtitle · ${meta.source.ifBlank { "公开真实数据" }}",
                    items = items
                )
            )
        }
    }

    private fun parseIndices(module: JSONObject?): List<StockIndexSnapshot> {
        val array = moduleItemsArray(module) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = firstText(item, "name").orEmpty()
                val value = firstText(item, "price", "value").orEmpty()
                if (name.isBlank() || value.isBlank() || value == "--") continue
                val changePercent = firstText(item, "changePercent", "pct")
                    .orEmpty()
                    .ifBlank { "--" }
                add(
                    StockIndexSnapshot(
                        name = name,
                        value = value,
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun parseBreadth(module: JSONObject?): StockMarketBreadth {
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
            meta = metaFromModule(module)
        )
    }

    private fun parseSentiment(module: JSONObject?): StockMarketSentiment {
        val item = moduleItemsObject(module)
        return StockMarketSentiment(
            temperature = firstDouble(item, "sentimentTemperature", "temperature"),
            level = firstText(item, "sentimentLevel", "level").orEmpty(),
            formula = firstText(item, "formula").orEmpty(),
            redRate = firstDouble(item, "redRate"),
            limitUpCount = firstInt(item, "limitUpCount"),
            moneyMakingEffect = firstDouble(item, "moneyMakingEffect"),
            meta = metaFromModule(module)
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
                        changePercent = firstText(item, "changePercent", "pct")
                            .orEmpty()
                            .ifBlank { "--" },
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
                val changePercent = firstText(item, "changePercent", "pct")
                    .orEmpty()
                    .ifBlank { "--" }
                add(
                    StockRankItem(
                        name = name.ifBlank { code },
                        code = code,
                        value = rankingDisplayValue(item),
                        changePercent = changePercent,
                        isRising = !changePercent.startsWith("-")
                    )
                )
            }
        }
    }

    private fun rankingDisplayValue(item: JSONObject): String {
        val priorityKeys = listOf(
            "mainInflow",
            "amount",
            "turnoverRate",
            "volumeRatio",
            "changeSpeed",
            "price",
            "value"
        )
        priorityKeys.forEach { key ->
            firstText(item, key)
                ?.takeIf { it.isNotBlank() && it != "--" }
                ?.let { return it }
        }
        return "--"
    }

    private fun parseInformationItems(module: JSONObject?): List<StockInformationItem> {
        val meta = metaFromModule(module)
        if (!meta.hasRealData) return emptyList()
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
                        url = firstText(item, "url", "attachmentUrl").orEmpty(),
                        tag = firstText(item, "tag", "type").orEmpty()
                    )
                )
            }
        }
    }

    private fun metaFromModule(module: JSONObject?): StockModuleMeta {
        if (module == null) return StockModuleMeta()
        val status = StockModuleStatus.fromWire(firstText(module, "status"))
        return StockModuleMeta(
            status = status,
            source = firstText(module, "source").orEmpty(),
            sourceUrlType = firstText(module, "sourceUrlType").orEmpty(),
            cacheAgeMs = firstLong(module, "cacheAgeMs") ?: 0L,
            updatedAt = firstText(module, "updatedAt").orEmpty(),
            warnings = stringList(module.optJSONArray("warnings"))
        )
    }

    private fun payloadObject(root: JSONObject): JSONObject {
        return root.optJSONObject("data")
            ?: root.optJSONObject("payload")
            ?: root.optJSONObject("snapshot")
            ?: root.optJSONObject("result")
            ?: root
    }

    private fun moduleItemsArray(module: JSONObject?): JSONArray? {
        if (module == null) return null
        module.optJSONArray("items")?.let { return it }
        module.optJSONArray("data")?.let { return it }
        module.optJSONArray("result")?.let { return it }
        for (key in listOf("data", "result", "payload")) {
            val nested = module.optJSONObject(key) ?: continue
            nested.optJSONArray("items")?.let { return it }
            nested.optJSONArray("data")?.let { return it }
            nested.optJSONArray("result")?.let { return it }
        }
        return null
    }

    private fun moduleItemsObject(module: JSONObject?): JSONObject {
        if (module == null) return JSONObject()
        module.optJSONObject("items")?.let { return it }
        module.optJSONObject("data")?.let { data ->
            data.optJSONObject("items")?.let { return it }
            return data
        }
        module.optJSONObject("result")?.let { result ->
            result.optJSONObject("items")?.let { return it }
            return result
        }
        return JSONObject()
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
        val value = proxyBaseUrl.trim().trimEnd('/')
        if (value.isBlank()) throw IllegalStateException("行情代理地址为空")
        return value
    }

    private fun httpGet(
        url: String,
        timeoutMs: Int,
        microCacheMs: Long
    ): String = StockHttpClient.get(
        url = url,
        timeoutMs = timeoutMs,
        emptyMessage = "股票扩展数据返回为空",
        microCacheMs = microCacheMs
    )

    companion object {
        private const val MARKET_TIMEOUT_MS = 18_000
        private const val SLOW_TIMEOUT_MS = 12_000
        private const val MARKET_MICRO_CACHE_MS = 900L
        private const val SLOW_MICRO_CACHE_MS = 2_000L
    }
}
