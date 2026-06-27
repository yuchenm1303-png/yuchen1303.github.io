package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable

@Immutable
enum class StockNativeRankingType(
    val wire: String,
    val title: String,
    val subtitle: String,
    val metricLabel: String
) {
    Gainers("gainers", "涨幅榜", "按实时涨跌幅由高到低排序", "成交额"),
    Losers("losers", "跌幅榜", "按实时涨跌幅由低到高排序", "成交额"),
    Amount("amount", "成交额榜", "按实时成交额由高到低排序", "成交额"),
    Turnover("turnover", "换手率榜", "按实时换手率由高到低排序", "换手率"),
    VolumeRatio("volume_ratio", "量比榜", "按实时量比由高到低排序", "量比"),
    Speed("speed", "涨速榜", "按实时涨速由高到低排序", "涨速"),
    MainInflow("main_inflow", "主力净流入榜", "按主力资金净流入由高到低排序", "主力净流入"),
    MainOutflow("main_outflow", "主力净流出榜", "按主力资金净流出由高到低排序", "主力净流出");

    companion object {
        fun fromWire(value: String?): StockNativeRankingType =
            entries.firstOrNull { it.wire == value } ?: Gainers
    }
}

@Immutable
data class StockNativeRankingItem(
    val rank: Int = 0,
    val name: String = "",
    val code: String = "",
    val industry: String = "",
    val price: String = "--",
    val amount: String = "--",
    val turnoverRate: String = "--",
    val volumeRatio: String = "--",
    val changeSpeed: String = "--",
    val mainInflow: String = "--",
    val changePercent: String = "--"
) {
    val isRising: Boolean get() = !changePercent.trim().startsWith("-")

    fun metric(type: StockNativeRankingType): String = when (type) {
        StockNativeRankingType.Amount,
        StockNativeRankingType.Gainers,
        StockNativeRankingType.Losers -> amount
        StockNativeRankingType.Turnover -> turnoverRate
        StockNativeRankingType.VolumeRatio -> volumeRatio
        StockNativeRankingType.Speed -> changeSpeed
        StockNativeRankingType.MainInflow,
        StockNativeRankingType.MainOutflow -> mainInflow
    }.ifBlank { "--" }

    fun displayedChange(type: StockNativeRankingType): String =
        if (type == StockNativeRankingType.Speed) changeSpeed.ifBlank { "--" }
        else changePercent.ifBlank { "--" }
}

@Immutable
enum class StockNativeHotType(val wire: String, val title: String, val subtitle: String) {
    Popularity("popularity", "个股人气榜", "东方财富站内真实行为形成的市场关注度排行"),
    Surge("surge", "人气飙升榜", "较昨日人气排名提升幅度最大的股票");

    companion object {
        fun fromWire(value: String?): StockNativeHotType =
            entries.firstOrNull { it.wire == value } ?: Popularity
    }
}

@Immutable
data class StockNativeHotItem(
    val rank: Int = 0,
    val currentRank: Int = 0,
    val rankChange: Int? = null,
    val code: String = "",
    val name: String = "",
    val market: String = "",
    val industry: String = "",
    val price: String = "--",
    val changePercent: String = "--",
    val amount: String = "--"
) {
    val isRising: Boolean get() = !changePercent.trim().startsWith("-")
}

@Immutable
data class StockNativeHotSnapshot(
    val type: StockNativeHotType = StockNativeHotType.Popularity,
    val items: List<StockNativeHotItem> = emptyList(),
    val risingCount: Int = 0,
    val fallingCount: Int = 0,
    val quoteMatchCount: Int = 0,
    val sourcePageUrl: String = "",
    val dataSourceLabel: String = "",
    val updatedAt: String = ""
)

@Immutable
data class StockNativeQuote(
    val code: String = "",
    val name: String = "",
    val market: String = "",
    val price: String = "--",
    val changeAmount: String = "--",
    val changePercent: String = "--",
    val open: String = "--",
    val high: String = "--",
    val low: String = "--",
    val previousClose: Float = 0f,
    val amount: String = "--",
    val volume: String = "--"
) {
    val isRising: Boolean get() = !changePercent.trim().startsWith("-")
}

@Immutable
data class StockNativeSectorBreadth(
    val upCount: Int? = null,
    val downCount: Int? = null,
    val flatCount: Int? = null,
    val redRate: Double? = null,
    val leaderName: String = "",
    val leaderChangePercent: String = "--",
    val mainInflow: String = "--"
)

@Immutable
data class StockNativeSectorLink(
    val code: String = "",
    val name: String = "",
    val type: String = "industry",
    val changePercent: String = "--"
)

@Immutable
data class StockNativeSectorDetail(
    val code: String = "",
    val name: String = "",
    val type: String = "industry",
    val quote: StockNativeQuote = StockNativeQuote(),
    val minutePoints: List<StockMinutePoint> = emptyList(),
    val breadth: StockNativeSectorBreadth = StockNativeSectorBreadth(),
    val relatedSectors: List<StockNativeSectorLink> = emptyList(),
    val dataSourceLabel: String = "",
    val updatedAt: String = ""
)

@Immutable
data class StockNativeConstituent(
    val rank: Int = 0,
    val code: String = "",
    val name: String = "",
    val price: String = "--",
    val changePercent: String = "--",
    val amount: String = "--"
) {
    val isRising: Boolean get() = !changePercent.trim().startsWith("-")
}

@Immutable
data class StockNativeConstituentPage(
    val items: List<StockNativeConstituent> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false
)

@Immutable
data class StockNativeIndexLink(
    val code: String = "",
    val name: String = "",
    val changePercent: String = "--"
)

@Immutable
data class StockNativeIndexDetail(
    val code: String = "",
    val name: String = "",
    val quote: StockNativeQuote = StockNativeQuote(),
    val minutePoints: List<StockMinutePoint> = emptyList(),
    val fiveDayPoints: List<StockMinutePoint> = emptyList(),
    val marketBreadth: StockMarketBreadth = StockMarketBreadth(),
    val sentiment: StockMarketSentiment = StockMarketSentiment(),
    val relatedIndices: List<StockNativeIndexLink> = emptyList(),
    val dataSourceLabel: String = "",
    val updatedAt: String = ""
)

@Immutable
data class StockNativeDiscussionPostSummary(
    val postId: String = "",
    val stockCode: String = "",
    val title: String = "",
    val author: String = "股吧用户",
    val updatedAt: String = "",
    val readCount: Int = 0,
    val commentCount: Int = 0,
    val kind: String = "discussion",
    val sourceUrl: String = ""
)

@Immutable
data class StockNativeDiscussionList(
    val code: String = "",
    val name: String = "",
    val page: Int = 0,
    val posts: List<StockNativeDiscussionPostSummary> = emptyList(),
    val hasMore: Boolean = false,
    val sourcePageUrl: String = ""
)

@Immutable
data class StockNativeDiscussionPost(
    val postId: String = "",
    val title: String = "",
    val author: String = "股吧用户",
    val publishedAt: String = "",
    val content: String = "",
    val likeCount: Int = 0,
    val sourceUrl: String = ""
)

@Immutable
data class StockNativeDiscussionReply(
    val author: String = "股吧用户",
    val content: String = "",
    val publishedAt: String = "",
    val likeCount: Int = 0
)

@Immutable
data class StockNativeDiscussionComment(
    val commentId: String = "",
    val author: String = "股吧用户",
    val content: String = "",
    val publishedAt: String = "",
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val replies: List<StockNativeDiscussionReply> = emptyList()
)

@Immutable
data class StockNativeDiscussionPostPage(
    val code: String = "",
    val name: String = "",
    val post: StockNativeDiscussionPost = StockNativeDiscussionPost(),
    val comments: List<StockNativeDiscussionComment> = emptyList(),
    val commentPage: Int = 0,
    val commentTotal: Int = 0,
    val hasMoreComments: Boolean = false,
    val sourcePageUrl: String = ""
)
