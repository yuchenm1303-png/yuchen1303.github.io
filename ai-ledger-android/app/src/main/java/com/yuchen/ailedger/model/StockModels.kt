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

data class StockKLinePoint(
    val date: String,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Float,
    val amount: Float,
    val changePercent: String
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
            StockFeatureEntry("涨速榜", "短线拉升", "speed_rank"),
            StockFeatureEntry("新高新低", "趋势位置", "high_low_rank")
        )
    ),
    StockFeatureGroup(
        title = "板块题材",
        subtitle = "行业、概念、地域和轮动观察",
        entries = listOf(
            StockFeatureEntry("行业板块", "申万/证监会", "industry"),
            StockFeatureEntry("概念板块", "题材热点", "concept"),
            StockFeatureEntry("地域板块", "省区联动", "region"),
            StockFeatureEntry("板块热度", "涨跌家数", "sector_heat"),
            StockFeatureEntry("板块资金", "主力流向", "sector_flow"),
            StockFeatureEntry("强势题材", "连板方向", "strong_theme")
        )
    ),
    StockFeatureGroup(
        title = "交易异动",
        subtitle = "涨停、龙虎榜、大宗、停复牌等事件",
        entries = listOf(
            StockFeatureEntry("涨停板", "封单/炸板", "limit_up"),
            StockFeatureEntry("跌停板", "风险观察", "limit_down"),
            StockFeatureEntry("连板梯队", "高度/晋级", "limit_chain"),
            StockFeatureEntry("龙虎榜", "席位资金", "lhb"),
            StockFeatureEntry("大宗交易", "折溢价", "block_trade"),
            StockFeatureEntry("异动公告", "异常波动", "abnormal_notice"),
            StockFeatureEntry("停复牌", "状态变更", "suspend_resume"),
            StockFeatureEntry("集合竞价", "开盘情绪", "auction")
        )
    ),
    StockFeatureGroup(
        title = "资金情绪",
        subtitle = "主力、北向、两融和市场温度",
        entries = listOf(
            StockFeatureEntry("主力资金", "净流入", "main_flow"),
            StockFeatureEntry("北向资金", "沪深股通", "north_flow"),
            StockFeatureEntry("融资融券", "杠杆资金", "margin"),
            StockFeatureEntry("ETF资金", "宽基/行业", "etf_flow"),
            StockFeatureEntry("市场情绪", "红盘/炸板率", "sentiment"),
            StockFeatureEntry("赚钱效应", "涨跌分布", "profit_effect")
        )
    ),
    StockFeatureGroup(
        title = "资讯研究",
        subtitle = "公告、新闻、研报、财报和股东数据",
        entries = listOf(
            StockFeatureEntry("公告", "交易所公告", "announcements"),
            StockFeatureEntry("新闻快讯", "舆情事件", "news"),
            StockFeatureEntry("研报", "机构观点", "research"),
            StockFeatureEntry("财报", "利润/现金流", "financials"),
            StockFeatureEntry("业绩预告", "预增预减", "forecast"),
            StockFeatureEntry("股东户数", "筹码集中", "holders"),
            StockFeatureEntry("解禁", "限售流通", "unlock"),
            StockFeatureEntry("分红配股", "权益事件", "dividend")
        )
    ),
    StockFeatureGroup(
        title = "看盘工具",
        subtitle = "选股、预警、日历、持仓和 AI 辅助",
        entries = listOf(
            StockFeatureEntry("条件选股", "指标筛选", "screener"),
            StockFeatureEntry("价格预警", "到价提醒", "alert"),
            StockFeatureEntry("模拟持仓", "盈亏记录", "portfolio"),
            StockFeatureEntry("交易日历", "节假/事件", "calendar"),
            StockFeatureEntry("新股申购", "新股/新债", "ipo"),
            StockFeatureEntry("可转债", "溢价率", "convertible"),
            StockFeatureEntry("数据导出", "复盘记录", "export"),
            StockFeatureEntry("AI 问股", "解释数据", "ai_qa")
        )
    )
)

private fun sampleAStockMarketBoards(): List<StockMarketBoard> = listOf(
    StockMarketBoard("热度排行榜", "示例：综合搜索、讨论和成交活跃度", listOf(StockRankItem("华电能源", "600396", "热度 99", "+4.00%", true), StockRankItem("四川长虹", "600839", "热度 96", "+6.31%", true), StockRankItem("中际旭创", "300308", "热度 93", "-1.08%", false))),
    StockMarketBoard("龙虎榜", "示例：机构、游资和营业部席位异动", listOf(StockRankItem("宗申动力", "001696", "净买 1.86亿", "+10.01%", true), StockRankItem("万丰奥威", "002085", "机构买入", "+7.42%", true), StockRankItem("常山北明", "000158", "游资活跃", "-2.36%", false))),
    StockMarketBoard("涨停梯队", "示例：连板高度、首板和炸板观察", listOf(StockRankItem("南京商旅", "600250", "4连板", "+10.02%", true), StockRankItem("国机汽车", "600335", "2连板", "+9.98%", true), StockRankItem("合锻智能", "603011", "首板", "+10.00%", true))),
    StockMarketBoard("板块热度", "示例：行业/概念强弱和涨跌家数", listOf(StockRankItem("电力", "BK0428", "涨 42 家", "+2.86%", true), StockRankItem("算力", "BK1136", "涨 38 家", "+2.12%", true), StockRankItem("医药商业", "BK0465", "跌 29 家", "-1.06%", false))),
    StockMarketBoard("资金流向", "示例：主力净流入和北向关注方向", listOf(StockRankItem("宁德时代", "300750", "主力 +5.2亿", "+1.26%", true), StockRankItem("贵州茅台", "600519", "北向 +3.4亿", "+0.81%", true), StockRankItem("东方财富", "300059", "主力 -2.1亿", "-0.66%", false))),
    StockMarketBoard("竞价异动", "示例：高开、低开、抢筹和核按钮", listOf(StockRankItem("比亚迪", "002594", "高开 2.1%", "+1.12%", true), StockRankItem("赛力斯", "601127", "抢筹 1.4亿", "+3.28%", true), StockRankItem("药明康德", "603259", "低开 1.8%", "-1.54%", false)))
)
