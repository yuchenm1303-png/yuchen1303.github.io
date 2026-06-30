package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockSlowDataSnapshot
import java.net.URLEncoder
import org.json.JSONObject

/**
 * 股票慢数据仓库。
 *
 * 市场首页已迁移到 [StockMarketStageRepository]。保留 [loadMarketHome] 仅用于旧调用兼容，
 * 内部同样走分阶段接口，不再维护旧整页线程池、Future、解析器和独立缓存。
 */
class StockMarketDataRepository(
    private val proxyBaseUrl: String = "https://ai-ledger-stock-proxy.onrender.com"
) {
    @Deprecated("市场首页请直接使用 StockMarketStageRepository")
    fun loadMarketHome(coldStartWait: Boolean = false): Result<StockMarketHomeSnapshot> = runCatching {
        val stages = StockMarketStageRepository(proxyBaseUrl)
        val indices = stages.loadIndices(forceNetwork = coldStartWait).getOrThrow()
        val breadth = stages.loadBreadth(forceNetwork = false).getOrElse { StockMarketHomeSnapshot() }
        val discovery = stages.loadDiscovery(forceNetwork = false).getOrElse { StockMarketHomeSnapshot() }
        indices.copy(
            marketBreadth = if (breadth.marketBreadth.meta.hasRealData) {
                breadth.marketBreadth
            } else {
                indices.marketBreadth
            },
            sentiment = if (breadth.sentiment.meta.hasRealData) {
                breadth.sentiment
            } else {
                indices.sentiment
            },
            boards = discovery.boards.ifEmpty { indices.boards },
            sectors = discovery.sectors.ifEmpty { indices.sectors },
            marketNews = discovery.marketNews.ifEmpty { indices.marketNews },
            marketNewsMeta = if (discovery.marketNewsMeta.hasRealData) {
                discovery.marketNewsMeta
            } else {
                indices.marketNewsMeta
            },
            popularityMeta = if (discovery.popularityMeta.hasRealData) {
                discovery.popularityMeta
            } else {
                indices.popularityMeta
            },
            limitUpMeta = if (discovery.limitUpMeta.hasRealData) {
                discovery.limitUpMeta
            } else {
                indices.limitUpMeta
            },
            updatedAt = listOf(indices.updatedAt, breadth.updatedAt, discovery.updatedAt)
                .firstOrNull { it.isNotBlank() }
                .orEmpty(),
            warnings = (indices.warnings + breadth.warnings + discovery.warnings)
                .distinct()
                .takeLast(MAX_WARNINGS)
        )
    }

    fun loadSlowStock(query: String): Result<StockSlowDataSnapshot> = runCatching {
        val encoded = encode(query.trim())
        val payload = StockJsonReader.payloadObject(
            JSONObject(
                StockHttpClient.get(
                    url = "${baseUrl()}/api/stock/a-share/stock/full?query=$encoded",
                    timeoutMs = SLOW_TIMEOUT_MS,
                    emptyMessage = "股票扩展数据返回为空",
                    microCacheMs = SLOW_MICRO_CACHE_MS
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
            profileMeta = StockMarketSnapshotParser.metaFromModule(profile),
            financialsMeta = StockMarketSnapshotParser.metaFromModule(financials),
            capitalMeta = StockMarketSnapshotParser.metaFromModule(capital),
            popularityMeta = StockMarketSnapshotParser.metaFromModule(popularity),
            announcements = StockMarketSnapshotParser.parseInformationItems(announcements),
            announcementsMeta = StockMarketSnapshotParser.metaFromModule(announcements),
            news = StockMarketSnapshotParser.parseInformationItems(news),
            newsMeta = StockMarketSnapshotParser.metaFromModule(news),
            research = StockMarketSnapshotParser.parseInformationItems(research),
            researchMeta = StockMarketSnapshotParser.metaFromModule(research),
            performanceForecastMeta = StockMarketSnapshotParser.metaFromModule(performanceForecast),
            shareholdersMeta = StockMarketSnapshotParser.metaFromModule(shareholders),
            unlocksMeta = StockMarketSnapshotParser.metaFromModule(unlocks),
            dividendsMeta = StockMarketSnapshotParser.metaFromModule(dividends),
            updatedAt = StockJsonReader.firstText(payload, "updatedAt").orEmpty(),
            warnings = StockJsonReader.stringList(payload.optJSONArray("warnings"))
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun baseUrl(): String {
        val value = proxyBaseUrl.trim().trimEnd('/')
        if (value.isBlank()) throw IllegalStateException("行情代理地址为空")
        return value
    }

    companion object {
        private const val SLOW_TIMEOUT_MS = 12_000
        private const val SLOW_MICRO_CACHE_MS = 2_000L
        private const val MAX_WARNINGS = 32

        @Deprecated("请使用 StockMarketStageRepository.prewarmMarketHome()")
        fun prewarmMarketHome() {
            StockMarketStageRepository.prewarmMarketHome()
        }
    }
}
