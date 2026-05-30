package com.yuchen.ailedger.model

data class StockQuote(
    val name: String,
    val code: String,
    val market: String,
    val price: String,
    val changeAmount: String,
    val changePercent: String,
    val isRising: Boolean,
    val previousClose: Float,
    val high: String,
    val low: String,
    val open: String,
    val totalMarketValue: String,
    val floatMarketValue: String,
    val volumeRatio: String,
    val turnoverRate: String,
    val peTtm: String,
    val pb: String,
    val amount: String,
    val popularityRank: String
)

data class StockMetric(
    val label: String,
    val value: String,
    val tone: StockTone = StockTone.Neutral
)

data class StockMinutePoint(
    val time: String,
    val price: Float,
    val average: Float,
    val volumeRatio: Float
)

data class StockOrderLevel(
    val label: String,
    val price: String,
    val volume: String,
    val isAsk: Boolean
)

data class StockTradeTick(
    val time: String,
    val price: String,
    val volume: String,
    val direction: String,
    val isBuy: Boolean
)

data class StockMoneyFlow(
    val mainInflow: String,
    val superLargeOrder: String,
    val largeOrder: String,
    val mediumOrder: String,
    val smallOrder: String
)

data class StockIndexSnapshot(
    val name: String,
    val value: String,
    val changePercent: String,
    val isRising: Boolean
)

data class StockWatchItem(
    val name: String,
    val code: String,
    val price: String,
    val changePercent: String,
    val isRising: Boolean
)

data class StockDetailUiState(
    val quote: StockQuote,
    val topMetrics: List<StockMetric>,
    val minutePoints: List<StockMinutePoint>,
    val sellLevels: List<StockOrderLevel>,
    val buyLevels: List<StockOrderLevel>,
    val tradeTicks: List<StockTradeTick>,
    val moneyFlow: StockMoneyFlow,
    val fundamentals: List<StockMetric>,
    val indices: List<StockIndexSnapshot>,
    val watchlist: List<StockWatchItem>,
    val aiSummary: String
)

enum class StockTone { Rising, Falling, Neutral }

fun sampleAStockDetailUiState(): StockDetailUiState {
    val quote = StockQuote(
        name = "华电能源",
        code = "600396",
        market = "沪A",
        price = "20.03",
        changeAmount = "+0.77",
        changePercent = "+4.00%",
        isRising = true,
        previousClose = 19.26f,
        high = "20.80",
        low = "19.18",
        open = "19.18",
        totalMarketValue = "294.98亿",
        floatMarketValue = "294.98亿",
        volumeRatio = "1.10",
        turnoverRate = "20.34%",
        peTtm = "72.45",
        pb = "3.18",
        amount = "60.17亿",
        popularityRank = "9/4284"
    )
    return StockDetailUiState(
        quote = quote,
        topMetrics = listOf(
            StockMetric("高", quote.high, StockTone.Rising),
            StockMetric("低", quote.low, StockTone.Falling),
            StockMetric("开", quote.open, StockTone.Falling),
            StockMetric("市值", quote.totalMarketValue),
            StockMetric("量比", quote.volumeRatio, StockTone.Rising),
            StockMetric("换手", quote.turnoverRate),
            StockMetric("市盈", quote.peTtm),
            StockMetric("成交额", quote.amount),
            StockMetric("人气", quote.popularityRank)
        ),
        minutePoints = listOf(
            StockMinutePoint("09:30", 19.18f, 19.18f, 0.12f),
            StockMinutePoint("09:45", 19.18f, 19.44f, 0.25f),
            StockMinutePoint("10:00", 20.52f, 19.82f, 0.82f),
            StockMinutePoint("10:20", 21.19f, 20.01f, 1.00f),
            StockMinutePoint("10:45", 20.86f, 20.11f, 0.74f),
            StockMinutePoint("11:15", 20.72f, 20.14f, 0.42f),
            StockMinutePoint("13:10", 20.35f, 20.12f, 0.36f),
            StockMinutePoint("13:40", 20.18f, 20.11f, 0.32f),
            StockMinutePoint("14:10", 20.42f, 20.10f, 0.50f),
            StockMinutePoint("14:35", 20.09f, 20.09f, 0.44f),
            StockMinutePoint("14:50", 20.01f, 20.09f, 0.38f),
            StockMinutePoint("15:00", 20.03f, 20.09f, 0.34f)
        ),
        sellLevels = listOf(
            StockOrderLevel("卖10", "20.13", "128", true),
            StockOrderLevel("卖9", "20.12", "95", true),
            StockOrderLevel("卖8", "20.11", "165", true),
            StockOrderLevel("卖7", "20.10", "1128", true),
            StockOrderLevel("卖6", "20.09", "294", true),
            StockOrderLevel("卖5", "20.08", "480", true),
            StockOrderLevel("卖4", "20.07", "185", true),
            StockOrderLevel("卖3", "20.06", "872", true),
            StockOrderLevel("卖2", "20.05", "2348", true),
            StockOrderLevel("卖1", "20.04", "2735", true)
        ),
        buyLevels = listOf(
            StockOrderLevel("买1", "20.03", "5923", false),
            StockOrderLevel("买2", "20.02", "2209", false),
            StockOrderLevel("买3", "20.01", "2272", false),
            StockOrderLevel("买4", "20.00", "6464", false),
            StockOrderLevel("买5", "19.99", "775", false),
            StockOrderLevel("买6", "19.98", "1155", false),
            StockOrderLevel("买7", "19.97", "400", false),
            StockOrderLevel("买8", "19.96", "502", false),
            StockOrderLevel("买9", "19.95", "441", false),
            StockOrderLevel("买10", "19.94", "318", false)
        ),
        tradeTicks = listOf(
            StockTradeTick("15:00", "20.03", "44763", "收盘", true),
            StockTradeTick("14:59", "20.04", "2735", "主动买", true),
            StockTradeTick("14:58", "20.02", "2209", "主动卖", false),
            StockTradeTick("14:57", "20.01", "2272", "主动买", true)
        ),
        moneyFlow = StockMoneyFlow(
            mainInflow = "+1.28亿",
            superLargeOrder = "+0.46亿",
            largeOrder = "+0.82亿",
            mediumOrder = "-0.31亿",
            smallOrder = "-0.97亿"
        ),
        fundamentals = listOf(
            StockMetric("流通市值", quote.floatMarketValue),
            StockMetric("市净率", quote.pb),
            StockMetric("每股收益", "0.28"),
            StockMetric("公告", "交易所公告"),
            StockMetric("新闻", "快讯舆情"),
            StockMetric("研报", "机构观点")
        ),
        indices = listOf(
            StockIndexSnapshot("上证", "3048.03", "+0.52%", true),
            StockIndexSnapshot("深成", "9561.28", "+0.41%", true),
            StockIndexSnapshot("创业板", "1882.40", "-0.18%", false),
            StockIndexSnapshot("沪深300", "3650.72", "+0.37%", true),
            StockIndexSnapshot("科创50", "742.18", "-0.25%", false),
            StockIndexSnapshot("中证A500", "4621.36", "+0.22%", true)
        ),
        watchlist = listOf(
            StockWatchItem("贵州茅台", "600519", "¥1668.00", "+0.81%", true),
            StockWatchItem("宁德时代", "300750", "¥201.36", "-0.34%", false),
            StockWatchItem("比亚迪", "002594", "¥247.20", "+1.12%", true)
        ),
        aiSummary = "当前示例股冲高后回落，价格仍高于昨收线；成交量明显放大，盘口买一挂单较厚。接入真实行情后，这里会结合分时、量能、盘口、资金流、公告和新闻自动生成摘要。"
    )
}
