package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import org.json.JSONObject

/**
 * 市场首页分阶段数据仓库。
 *
 * 三大指数是首页首屏最高优先级：走独立极速报价路由，和市场宽度、榜单扫描完全隔离。
 * 旧版整页缓存只用于升级后的首次显示兜底，不再触发旧整页网络刷新。
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
            path = "/api/stock/a-share/index/priority/quotes",
            cacheFileName = "stock_market_index_priority_quotes_cache_v1.json",
            freshMs = 3_000L,
            timeoutMs = 1_600
        ),
        Breadth(
            wireName = "breadth",
            path = "/api/stock/a-share/market/breadth",
            cacheFileName = "stock_market_breadth_cache_v1.json",
            freshMs = 30_000L,
            timeoutMs = 4_500
        ),
        Discovery(
            wireName = "discovery",
            path = "/api/stock/a-share/market/discovery",
            cacheFileName = "stock_market_discovery_cache_v1.json",
            freshMs = 30_000L,
            timeoutMs = 4_500
        )
    }

    private data class ParsedCache(
        val entry: StockFileCache.Entry,
        val snapshot: StockMarketHomeSnapshot
    )

    fun loadIndices(
        forceNetwork: Boolean = false,
        allowStaleCache: Boolean = false
    ): Result<StockMarketHomeSnapshot> = loadStage(Stage.Indices, forceNetwork, allowStaleCache)

    fun loadBreadth(forceNetwork: Boolean = false): Result<StockMarketHomeSnapshot> =
        loadStage(Stage.Breadth, forceNetwork, allowStaleCache = false)

    fun loadDiscovery(forceNetwork: Boolean = false): Result<StockMarketHomeSnapshot> =
        loadStage(Stage.Discovery, forceNetwork, allowStaleCache = false)

    private fun loadStage(
        stage: Stage,
        forceNetwork: Boolean,
        allowStaleCache: Boolean
    ): Result<StockMarketHomeSnapshot> = runCatching {
        val cached = readUsableCache(stage)
        if (!forceNetwork && cached != null) {
            if (allowStaleCache || cached.entry.ageMs <= stage.freshMs) {
                return@runCatching cached.snapshot.withStageWarning(
                    "market_stage:${stage.wireName}: cache_first; cached=${cached.entry.source}; " +
                        "ageMs=${cached.entry.ageMs}"
                )
            }
        }

        try {
            val body = StockHttpClient.get(
                url = "${baseUrl()}${stage.path}",
                timeoutMs = stage.timeoutMs,
                emptyMessage = "${stage.wireName}阶段行情返回为空",
                microCacheMs = if (stage == Stage.Indices) 120L else 280L,
                requestGroup = if (stage == Stage.Indices) "stock-index-priority" else null
            )
            val snapshot = parseStage(stage, body)
            if (snapshot.hasStageData(stage)) {
                StockFileCache.write(stage.cacheFileName, body)
                snapshot
            } else if (cached != null) {
                cached.snapshot.withStageWarning(
                    "market_stage:${stage.wireName}: warming; cached=${cached.entry.source}; " +
                        "ageMs=${cached.entry.ageMs}"
                )
            } else {
                snapshot.withStageWarning("market_stage:${stage.wireName}: warming_without_cache")
            }
        } catch (networkError: Throwable) {
            if (cached == null) throw networkError
            cached.snapshot.withStageWarning(
                "market_stage:${stage.wireName}: cache_fallback; cached=${cached.entry.source}; " +
                    "ageMs=${cached.entry.ageMs}; reason=${networkError.message.orEmpty().take(120)}"
            )
        }
    }

    private fun readUsableCache(stage: Stage): ParsedCache? {
        val candidates = listOfNotNull(
            StockFileCache.read(
                fileName = stage.cacheFileName,
                maxAgeMs = CACHE_MAX_AGE_MS,
                source = stage.wireName
            ),
            StockFileCache.read(
                fileName = LEGACY_HOME_CACHE_FILE,
                maxAgeMs = CACHE_MAX_AGE_MS,
                source = "legacy-home"
            )
        )
        for (entry in candidates) {
            val parsed = runCatching { parseStage(stage, entry.body) }.getOrNull()
            if (parsed != null && parsed.hasStageData(stage)) {
                return ParsedCache(entry, parsed)
            }
            if (entry.source == stage.wireName) {
                StockFileCache.delete(stage.cacheFileName)
            } else {
                StockFileCache.delete(LEGACY_HOME_CACHE_FILE)
            }
        }
        return null
    }

    private fun parseStage(stage: Stage, body: String): StockMarketHomeSnapshot {
        val root = JSONObject(body)
        if (stage != Stage.Indices) return StockMarketSnapshotParser.parse(root)
        val priority = StockMarketSnapshotParser.parseIndexQuotes(root)
        return if (priority.indices.isNotEmpty()) {
            priority
        } else {
            StockMarketSnapshotParser.parse(root)
        }
    }

    private fun StockMarketHomeSnapshot.hasStageData(stage: Stage): Boolean = when (stage) {
        Stage.Indices -> indices.isNotEmpty()
        Stage.Breadth -> marketBreadth.meta.hasRealData
        Stage.Discovery -> boards.isNotEmpty() || sectors.isNotEmpty()
    }

    private fun StockMarketHomeSnapshot.withStageWarning(value: String): StockMarketHomeSnapshot =
        copy(warnings = (warnings + value).distinct().takeLast(MAX_WARNINGS))

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
