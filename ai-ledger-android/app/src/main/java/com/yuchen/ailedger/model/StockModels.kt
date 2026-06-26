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
    val volumeRatio: Float,
    val volume: Float = 0f,
    val matchedVolume: Float? = null,
    val unmatchedVolume: Float? = null,
    val unmatchedDirection: String = "unavailable",
    val phase: String = "continuous"
)

data class StockKLinePoint(
    val date: String,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Float,
    val amount: Float,
    val changePercent: String,
    val amplitude: String = "--",
    val changeAmount: String = "--",
    val turnoverRate: String = "--"
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

data class StockFeatureEntry(
    val title: String,
    val subtitle: String,
    val routeKey: String
)

data class StockFeatureGroup(
    val title: String,
    val subtitle: String,
    val entries: List<StockFeatureEntry>
)

data class StockRankItem(
    val name: String,
    val code: String,
    val value: String,
    val changePercent: String,
    val isRising: Boolean
)

data class StockMarketBoard(
    val title: String,
    val subtitle: String,
    val items: List<StockRankItem>
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
    val featureGroups: List<StockFeatureGroup>,
    val marketBoards: List<StockMarketBoard>,
    val aiSummary: String,
    val kLinePoints: List<StockKLinePoint> = emptyList(),
    val dataSourceLabel: String = "示例数据",
    val errorMessage: String? = null
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
        featureGroups = sampleAStockFeatureGroups(),
        marketBoards = sampleAStockMarketBoards(),
        aiSummary = "当前示例股冲高后回落，价格仍高于昨收线；成交量明显放大，盘口买一挂单较厚。接入真实行情后，这里会结合分时、量能、盘口、资金流、公告和新闻自动生成摘要。",
        kLinePoints = sampleKLinePoints(),
        dataSourceLabel = "示例数据"
    )
}

private fun sampleKLinePoints(): List<StockKLinePoint> = listOf(
    StockKLinePoint("05-10", 18.10f, 18.55f, 18.70f, 17.98f, 0.42f, 7.2f, "+2.12%"),
    StockKLinePoint("05-13", 18.60f, 19.05f, 19.22f, 18.30f, 0.56f, 9.1f, "+2.70%"),
    StockKLinePoint("05-14", 19.08f, 18.86f, 19.44f, 18.72f, 0.48f, 8.4f, "-1.00%"),
    StockKLinePoint("05-15", 18.90f, 19.26f, 19.38f, 18.65f, 0.62f, 10.2f, "+2.12%"),
    StockKLinePoint("05-16", 19.18f, 20.03f, 20.80f, 19.18f, 1.00f, 60.1f, "+4.00%")
)

private fun sampleAStockFeatureGroups(): List<StockFeatureGroup> = listOf(
    StockFeatureGroup(
        title = "基础看盘",
        subtitle = "从搜索、自选到个股详情的核心入口",
        entries = listOf(
            StockFeatureEntry("股票搜索", "代码/拼音", "search"),
            StockFeatureEntry("自选股", "关注列表", "watchlist"),
            StockFeatureEntry("指数行情", "沪深京/宽基", "indices"),
            StockFeatureEntry("分时K线", "分时/日周月", "chart"),
            StockFeatureEntry("盘口明细", "五档/十档", "order_book"),
            StockFeatureEntry("个股资料", "财务/股东", "profile")
        )
    ),
    StockFeatureGroup(
        title = "行情榜单",
        subtitle = "热度、涨跌、量价和速度榜",
        entries = listOf(
            StockFeatureEntry("热度排行榜", "人气/讨论", "hot_rank"),
            StockFeatureEntry("涨幅榜", "强势个股", "gain_rank"),
            StockFeatureEntry("跌幅榜", "弱势个股", "loss_rank"),
            StockFeatureEntry("成交额榜", "资金活跃", "amount_rank"),
            StockFeatureEntry("换手榜", "筹码活跃", "turnover_rank"),
            StockFeatureEntry("量比榜", "放量异动", "volume_ratio_rank"),
            StockFeatureEntry("涨速榜", "短线加速", "speed_rank"),
            StockFeatureEntry("振幅榜", "波动强度", "amplitude_rank"),
            StockFeatureEntry("新股榜", "上市表现", "ipo_rank")
        )
    ),
    StockFeatureGroup(
        title = "板块与资金",
        subtitle = "行业、概念和资金流向",
        entries = listOf(
            StockFeatureEntry("行业板块", "行业涨跌", "industry"),
            StockFeatureEntry("概念板块", "主题热点", "concept"),
            StockFeatureEntry("地域板块", "区域表现", "region"),
            StockFeatureEntry("主力资金流", "净流入/流出", "main_flow"),
            StockFeatureEntry("北向资金", "沪深港通", "northbound"),
            StockFeatureEntry("融资融券", "两融余额", "margin"),
            StockFeatureEntry("大单监控", "超大单/大单", "large_order")
        )
    ),
    StockFeatureGroup(
        title = "信息与研究",
        subtitle = "公告、新闻、财务和研究资料",
        entries = listOf(
            StockFeatureEntry("个股公告", "交易所公告", "announcements"),
            StockFeatureEntry("个股新闻", "实时资讯", "news"),
            StockFeatureEntry("研究报告", "券商研报", "research"),
            StockFeatureEntry("财务摘要", "利润/营收", "financials"),
            StockFeatureEntry("业绩预告", "预增/预减", "forecast"),
            StockFeatureEntry("股东数据", "股东户数", "shareholders"),
            StockFeatureEntry("解禁提醒", "限售解禁", "unlock"),
            StockFeatureEntry("分红送转", "权益分派", "dividend")
        )
    ),
    StockFeatureGroup(
        title = "交易辅助",
        subtitle = "诊股、提醒和条件监控",
        entries = listOf(
            StockFeatureEntry("智能诊股", "技术/基本面", "diagnosis"),
            StockFeatureEntry("价格预警", "到价提醒", "price_alert"),
            StockFeatureEntry("条件选股", "多条件筛选", "screener"),
            StockFeatureEntry("模拟交易", "虚拟买卖", "paper_trade"),
            StockFeatureEntry("交易日历", "停复牌/事件", "calendar"),
            StockFeatureEntry("龙虎榜", "席位数据", "dragon_tiger")
        )
    )
)

private fun sampleAStockMarketBoards(): List<StockMarketBoard> = listOf(
    StockMarketBoard(
        "人气热榜",
        "市场关注度最高",
        listOf(
            StockRankItem("常山北明", "000158", "12.36", "+9.98%", true),
            StockRankItem("中科曙光", "603019", "78.20", "+6.12%", true),
            StockRankItem("贵州茅台", "600519", "1668.00", "+0.81%", true)
        )
    ),
    StockMarketBoard(
        "涨幅榜",
        "强势个股",
        listOf(
            StockRankItem("示例A", "600001", "10.00", "+10.02%", true),
            StockRankItem("示例B", "000002", "8.80", "+9.96%", true),
            StockRankItem("示例C", "300003", "21.50", "+8.43%", true)
        )
    ),
    StockMarketBoard(
        "成交额榜",
        "资金最活跃",
        listOf(
            StockRankItem("东方财富", "300059", "18.90", "+2.11%", true),
            StockRankItem("中际旭创", "300308", "168.20", "-1.22%", false),
            StockRankItem("宁德时代", "300750", "201.36", "-0.34%", false)
        )
    )
)
