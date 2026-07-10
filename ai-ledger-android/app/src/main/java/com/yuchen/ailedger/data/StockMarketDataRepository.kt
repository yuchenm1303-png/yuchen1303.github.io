package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.StockMarketHomeSnapshot
import com.yuchen.ailedger.model.StockSlowDataSnapshot

/**
 * 股票慢数据仓库。
 *
 * 市场首页已迁移到 [StockMarketStageRepository]。保留 [loadMarketHome] 仅用于旧调用兼容，
 * 内部同样走分阶段接口，不再维护旧整页线程池、Future、解析器和独立缓存。
 *
 * 当前生产后端尚未接入经过验证的公告、新闻、研报、股东等真实慢数据源。为避免进入
 * 个股详情后发起一条只能返回 unavailable 的无收益请求，[loadSlowStock] 直接返回能力
 * 空快照；将来接入真实源时再恢复对应网络读取，不影响现有页面视觉和交互。
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

    fun loadSlowStock(query: String): Result<StockSlowDataSnapshot> = Result.success(
        StockSlowDataSnapshot(
            warnings = listOf(
                "slow_data_local_gate: no verified real source; skipped unavailable request for ${query.trim()}"
            )
        )
    )

    companion object {
        private const val MAX_WARNINGS = 32
    }
}
