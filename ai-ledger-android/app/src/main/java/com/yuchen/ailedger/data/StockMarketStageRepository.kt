package com.yuchen.ailedger.data

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMarketBreadth
import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockMarketSentiment
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 市场首页优先级数据仓库。
 *
 * 指数、市场宽度、榜单板块分别请求与缓存，避免低优先级模块拖住首屏。
 * 旧版整页缓存仍可作为升级后的首次显示兜底，但不会触发旧整页网络刷新。
 */
class StockMarketStageRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    enum class Stage(
        val wireName: String,
        val path: String,
        val cacheFileName: String,
        val freshMs: Long,
        val timeoutMs: Int
    ) {
        Indices(
            wireName = "indices",
            path = "/api/stock/a-share/market/indices",
            cacheFileName = "stock_market_indices_cache_v1.json",
            freshMs = 5_000L,
            timeoutMs = 2_800
        ),
        Breadth(
            wireName = "breadth",
            path = "/api/stock/a-share/market/breadth",
            cacheFileName = "stock_market_breadth_cache_v1.json",
            freshMs = 8_000L,
            timeoutMs = 2_200
        ),
        Discovery(
            wireName = "discovery",
            path = "/api/stock/a-share/market/discovery",
            cacheFileName = "stock_market_discovery_cache_v1.json",
            freshMs = 20_000L,
            timeoutMs = 2_200
        )
    }

    private data class CachedBody(
        val body: String,
        val ageMs: Long,
        val source: String
    )

    fun loadIndices(forceNetwork: Boolean = false): Result<StockMarketHomeSnapshot> =
        loadStage(Stage.Indices, forceNetwork)

    fun loadBreadth(forceNetwork: Boolean = false): Result<StockMarketHomeSnapshot> =
        loadStage(Stage.Breadth, forceNetwork)

    fun loadDiscovery(forceNetwork: Boolean = false): Result<StockMarketHomeSnapshot> =
        loadStage(Stage.Discovery, forceNetwork)

    private fun loadStage(
        stage: Stage,
        forceNetwork: Boolean
    ): Result<StockMarketHomeSnapshot> = runCatching {
        val cached = readCache(stage) ?: readLegacyHomeCache()
        if (!forceNetwork && cached != null && cached.ageMs <= stage.freshMs) {
            return@runCatching parseSnapshot(JSONObject(cached.body))
        }

        try {
            val body = StockHttpClient.get(
                url = "${baseUrl()}${stage.path}",
                timeoutMs = stage.timeoutMs,
                emptyMessage = "${stage.wireName}阶段行情返回为空",
                microCacheMs = 280L
            )
            val snapshot = parseSnapshot(JSONObject(body))
            if (snapshot.hasStageData(stage)) {
                writeCache(stage, body)
                snapshot
            } else if (cached != null) {
                parseSnapshot(JSONObject(cached.body)).withStageWarning(
                    "market_stage:${stage.wireName}: warming; cached=${cached.source}; ageMs=${cached.ageMs}"
                )
            } else {
                snapshot
            }
        } catch (networkError: Throwable) {
            if (cached == null) throw networkError
            parseSnapshot(JSONObject(cached.body)).withStageWarning(
                "market_stage:${stage.wireName}: cache_fallback; cached=${cached.source}; " +
                    "ageMs=${cached.ageMs}; reason=${networkError.message.orEmpty().take(120)}"
            )
        }
    }

    private fun parseSnapshot(root: JSONObject): StockMarketHomeSnapshot {
        val payload = payloadObject(root)
        val indicesModule = payload.optJSONObject("indices")
        val breadthModule = payload.optJSONObject("marketBreadth")
        val sentimentModule = payload.optJSONObject("sentiment")
        val sectorModule = payload.optJSONObject("sectorHotRanking")
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
        return StockMarketHomeSnapshot(
            indices = parseIndices(indicesModule),
            indicesMeta = metaFromModule(indicesModule),
            marketBreadth = parseBreadth(breadthModule),
            sentiment = parseSentiment(sentimentModule),
            boards = boards.distinctBy { it.title },
            sectors = parseSectors(sectorModule),
            updatedAt = firstText(payload, "updatedAt").orEmpty(),
            warnings = stringList(payload.optJSONArray("warnings"))
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
        for (key in listOf(
            "mainInflow",
            "amount",
            "turnoverRate",
            "volumeRatio",
            "changeSpeed",
            "price",
            "value"
        )) {
            firstText(item, key)
                ?.takeIf { it.isNotBlank() && it != "--" }
                ?.let { return it }
        }
        return "--"
    }

    private fun metaFromModule(module: JSONObject?): StockModuleMeta {
        if (module == null) return StockModuleMeta()
        return StockModuleMeta(
            status = StockModuleStatus.fromWire(firstText(module, "status")),
            source = firstText(module, "source").orEmpty(),
            sourceUrlType = firstText(module, "sourceUrlType").orEmpty(),
            updatedAt = firstText(module, "updatedAt").orEmpty(),
            cacheAgeMs = firstLong(module, "cacheAgeMs") ?: 0L,
            isDerived = firstBoolean(module, "isDerived") ?: false,
            warnings = stringList(module.optJSONArray("warnings"))
        )
    }

    private fun payloadObject(root: JSONObject): JSONObject {
        val data = root.optJSONObject("data")
        if (data != null && hasMarketPayload(data)) return data
        val payload = root.optJSONObject("payload")
        if (payload != null && hasMarketPayload(payload)) return payload
        val result = root.optJSONObject("result")
        if (result != null && hasMarketPayload(result)) return result
        return root
    }

    private fun hasMarketPayload(value: JSONObject): Boolean {
        return value.has("indices") ||
            value.has("marketBreadth") ||
            value.has("sectorHotRanking") ||
            value.has("gainers") ||
            value.has("status")
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

    private fun StockMarketHomeSnapshot.hasStageData(stage: Stage): Boolean = when (stage) {
        Stage.Indices -> indices.isNotEmpty()
        Stage.Breadth -> marketBreadth.meta.hasRealData
        Stage.Discovery -> boards.isNotEmpty() || sectors.isNotEmpty()
    }

    private fun StockMarketHomeSnapshot.withStageWarning(value: String): StockMarketHomeSnapshot =
        copy(warnings = (warnings + value).distinct().takeLast(MAX_WARNINGS))

    private fun writeCache(stage: Stage, body: String) {
        val file = cacheFile(stage.cacheFileName) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(body)
            if (!temporary.renameTo(file)) {
                file.writeText(body)
                temporary.delete()
            }
        }
    }

    private fun readCache(stage: Stage): CachedBody? =
        readCacheFile(stage.cacheFileName, stage.wireName)

    private fun readLegacyHomeCache(): CachedBody? =
        readCacheFile(LEGACY_HOME_CACHE_FILE, "legacy-home")

    private fun readCacheFile(fileName: String, source: String): CachedBody? {
        val file = cacheFile(fileName) ?: return null
        if (!file.isFile || file.length() <= 2L) return null
        val ageMs = (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0L)
        if (ageMs > CACHE_MAX_AGE_MS) {
            file.delete()
            return null
        }
        val body = runCatching { file.readText() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return CachedBody(body, ageMs, source)
    }

    private fun cacheFile(fileName: String): File? {
        val context = AiLedgerApplication.contextOrNull() ?: return null
        return File(context.filesDir, fileName)
    }

    private fun baseUrl(): String {
        val value = proxyBaseUrl.trim().trimEnd('/')
        if (value.isBlank()) throw IllegalStateException("行情代理地址为空")
        return value
    }

    companion object {
        private const val LEGACY_HOME_CACHE_FILE = "stock_market_home_cache_v1.json"
        private const val CACHE_MAX_AGE_MS = 4L * 24L * 60L * 60L * 1_000L
        private const val MAX_WARNINGS = 24
    }
}
