package com.yuchen.ailedger.model

enum class StockModuleStatus {
    Ok,
    Partial,
    Empty,
    Unavailable,
    Stale;

    companion object {
        fun fromWire(value: String?): StockModuleStatus = when (value?.trim()?.lowercase()) {
            "ok" -> Ok
            "partial" -> Partial
            "empty" -> Empty
            "stale" -> Stale
            else -> Unavailable
        }
    }
}

data class StockModuleMeta(
    val status: StockModuleStatus = StockModuleStatus.Unavailable,
    val source: String = "",
    val sourceUrlType: String = "",
    val updatedAt: String = "",
    val cacheAgeMs: Long = 0L,
    val isDerived: Boolean = false,
    val warnings: List<String> = emptyList()
) {
    val hasRealData: Boolean
        get() = status == StockModuleStatus.Ok ||
            status == StockModuleStatus.Partial ||
            status == StockModuleStatus.Stale
}

data class StockDepthState(
    val status: StockModuleStatus = StockModuleStatus.Unavailable,
    val source: String = "",
    val isDerived: Boolean = false,
    val updatedAt: String = "",
    val sourceTimestamp: String = "",
    val cacheAgeMs: Long = 0L,
    val warnings: List<String> = emptyList()
) {
    val canDisplayLevels: Boolean
        get() = !isDerived && (
            status == StockModuleStatus.Ok ||
                status == StockModuleStatus.Partial ||
                status == StockModuleStatus.Stale
            )
}

data class StockMarketBreadth(
    val upCount: Int? = null,
    val downCount: Int? = null,
    val flatCount: Int? = null,
    val limitUpCount: Int? = null,
    val limitDownCount: Int? = null,
    val brokenBoardCount: Int? = null,
    val brokenBoardRate: Double? = null,
    val maxConsecutiveBoards: Int? = null,
    val redRate: Double? = null,
    val medianChangePercent: Double? = null,
    val marketAmount: String = "--",
    val shszAmount: String = "--",
    val bjAmount: String = "--",
    val moneyMakingEffect: Double? = null,
    val updatedAt: String = "",
    val meta: StockModuleMeta = StockModuleMeta()
)

data class StockMarketSentiment(
    val temperature: Double? = null,
    val level: String = "",
    val formula: String = "",
    val redRate: Double? = null,
    val limitUpCount: Int? = null,
    val moneyMakingEffect: Double? = null,
    val meta: StockModuleMeta = StockModuleMeta()
)

data class StockSectorSnapshot(
    val sectorCode: String,
    val sectorName: String,
    val type: String,
    val changePercent: String,
    val upCount: Int? = null,
    val downCount: Int? = null,
    val flatCount: Int? = null,
    val leaderName: String = "",
    val leaderChangePercent: String = "",
    val amount: String = "",
    val turnoverRate: String = "",
    val mainInflow: String = "",
    val heatRank: Int? = null,
    val updatedAt: String = ""
)

data class StockInformationItem(
    val id: String = "",
    val title: String,
    val summary: String = "",
    val publishTime: String = "",
    val source: String = "",
    val url: String = ""
)

data class StockMarketHomeSnapshot(
    val indices: List<StockIndexSnapshot> = emptyList(),
    val indicesMeta: StockModuleMeta = StockModuleMeta(),
    val marketBreadth: StockMarketBreadth = StockMarketBreadth(),
    val sentiment: StockMarketSentiment = StockMarketSentiment(),
    val boards: List<StockMarketBoard> = emptyList(),
    val sectors: List<StockSectorSnapshot> = emptyList(),
    val marketNews: List<StockInformationItem> = emptyList(),
    val marketNewsMeta: StockModuleMeta = StockModuleMeta(),
    val popularityMeta: StockModuleMeta = StockModuleMeta(),
    val limitUpMeta: StockModuleMeta = StockModuleMeta(),
    val updatedAt: String = "",
    val warnings: List<String> = emptyList()
)

data class StockSlowDataSnapshot(
    val profileMeta: StockModuleMeta = StockModuleMeta(),
    val financialsMeta: StockModuleMeta = StockModuleMeta(),
    val capitalMeta: StockModuleMeta = StockModuleMeta(),
    val popularityMeta: StockModuleMeta = StockModuleMeta(),
    val announcements: List<StockInformationItem> = emptyList(),
    val announcementsMeta: StockModuleMeta = StockModuleMeta(),
    val news: List<StockInformationItem> = emptyList(),
    val newsMeta: StockModuleMeta = StockModuleMeta(),
    val research: List<StockInformationItem> = emptyList(),
    val researchMeta: StockModuleMeta = StockModuleMeta(),
    val performanceForecastMeta: StockModuleMeta = StockModuleMeta(),
    val shareholdersMeta: StockModuleMeta = StockModuleMeta(),
    val unlocksMeta: StockModuleMeta = StockModuleMeta(),
    val dividendsMeta: StockModuleMeta = StockModuleMeta(),
    val updatedAt: String = "",
    val warnings: List<String> = emptyList()
)

fun StockModuleStatus.displayText(): String = when (this) {
    StockModuleStatus.Ok -> "实时"
    StockModuleStatus.Partial -> "部分数据"
    StockModuleStatus.Empty -> "暂无数据"
    StockModuleStatus.Unavailable -> "数据源暂不可用"
    StockModuleStatus.Stale -> "缓存数据"
}
