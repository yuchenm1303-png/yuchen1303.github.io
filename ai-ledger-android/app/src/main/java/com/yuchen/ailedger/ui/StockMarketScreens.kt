package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.StockMarketViewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import kotlin.math.abs
import kotlin.math.max

private val StockTabs = listOf("分时", "日K", "周K", "月K", "五日")
private val DepthTabs = listOf("五档", "成交")
private val HomeQuickActions = listOf("热榜", "板块", "资金", "异动", "龙虎", "日历", "研报", "预警", "选股", "新股", "ETF", "公告")

@Composable
fun AStockMarketScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val viewModel: StockMarketViewModel = viewModel()
    val ui by viewModel.uiState.collectAsState()

    if (ui.showDetail) {
        StockDetailPage(
            appState = state,
            ui = ui,
            onBack = viewModel::backToHome,
            onRefresh = viewModel::refreshCurrent,
            onSelectTab = viewModel::selectTab,
            onSelectDepth = viewModel::selectDepthTab,
            onAction = viewModel::handleAction,
            onCloseAction = viewModel::closeAction,
            onOpenAssistant = onOpenAssistant
        )
    } else {
        StockHomePage(
            appState = state,
            ui = ui,
            onBack = onBack,
            onRefresh = viewModel::refreshHome,
            onQueryChange = viewModel::updateQuery,
            onSearch = viewModel::searchAndOpen,
            onOpenDetail = viewModel::openDetail,
            onOpenCode = viewModel::openCode,
            onSelectHomeAction = viewModel::selectHomeAction,
            onOpenAssistant = onOpenAssistant
        )
    }
}

@Composable
private fun StockHomePage(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenCode: (String) -> Unit,
    onSelectHomeAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { HomeHeader(appState, ui, onBack, onRefresh) }
        item { MarketPulseCard(appState, ui, onOpenDetail) }
        item { MarketBreadthCard(appState, ui.stock) }
        item { IndexStrip(appState, ui.stock.indices) }
        item { SearchCard(appState, ui, onQueryChange, onSearch) }
        item { OpportunityDashboard(appState, ui.stock, onOpenDetail, onOpenAssistant) }
        item { SectorHeatMapCard(appState, ui.stock, onOpenCode) }
        item { CapitalRadarCard(appState, ui.stock) }
        item { SignalMatrixCard(appState, ui.stock) }
        item { HomeToolGrid(appState, ui.selectedHomeAction, onSelectHomeAction) }
        item { HomeToolContent(appState, ui.stock, ui.selectedHomeAction, onOpenCode) }
        item { NewsCalendarCard(appState, ui.stock, onOpenAssistant) }
        item { SmartToolsCard(appState, ui.stock, onOpenAssistant) }
        item { WatchListCard(appState, ui.stock, onOpenCode) }
        item { MarketBoardsCard(appState, ui.stock, onOpenCode) }
    }
}

@Composable
private fun StockDetailPage(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onSelectDepth: (String) -> Unit,
    onAction: (String) -> Unit,
    onCloseAction: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { DetailTopBar(appState, ui, onBack, onRefresh) }
        item { TradingTerminalCard(appState, ui, onSelectTab, onSelectDepth) }
        if (ui.activeAction != null) item { ActionPanel(appState, ui, onAction, onCloseAction, onRefresh) }
        item { AiSummaryCard(appState, ui.stock, onOpenAssistant) }
        item { FundamentalsCard(appState, ui.stock) }
        item { BottomActionBar(appState, ui, onAction) }
    }
}

@Composable
private fun HomeHeader(appState: AssistantUiState, ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PressableGlass(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 999, Modifier.width(82.dp).height(38.dp), GlassRole.Chip, onClick = onBack) {
                CenterText("‹ 返回", 13, Color.White.copy(alpha = 0.82f))
            }
            Box(Modifier.weight(1f))
            PressableGlass(appState.quality, appState.glassIntensity * 0.70f, appState.motionIntensity, 999, Modifier.width(44.dp).height(38.dp), GlassRole.Chip, onClick = onRefresh) {
                CenterText(if (ui.loading || ui.marketLoading) "…" else "⟳", 17, Color.White.copy(alpha = 0.82f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE MARKET", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("A股行情首页", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(statusText(ui), color = statusColor(ui), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MarketPulseCard(appState: AssistantUiState, ui: StockMarketUiState, onOpenDetail: () -> Unit) {
    val stock = ui.stock
    PressableGlass(appState.quality, appState.glassIntensity, appState.motionIntensity, 30, Modifier.fillMaxWidth().height(228.dp), GlassRole.Card, onClick = onOpenDetail) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (ui.loading) "行情雷达连接中" else "市场温度", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(marketMoodText(stock), color = Color.White, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("基于价格、资金、换手、量比和热点榜单生成", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                    Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
            MiniTrendCanvas(stock, Modifier.fillMaxWidth().height(58.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("今日关注", "${stock.quote.name} ${stock.quote.code}", quoteColor(stock.quote.isRising), Modifier.weight(1.35f))
                MetricTile("成交额", stock.quote.amount, Color.White, Modifier.weight(1f))
                MetricTile("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarketBreadthCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("盘面宽度", "用热榜与板块数据估算市场强弱分布")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("上涨观察", "${risingCount(stock)} 个", RiseRed, Modifier.weight(1f))
                InfoChip("下跌/谨慎", "${fallingCount(stock)} 个", FallGreen, Modifier.weight(1f))
                InfoChip("热度池", "${stock.marketBoards.sumOf { it.items.size }} 条", Color.White, Modifier.weight(1f))
            }
            BreadthBar(stock, Modifier.fillMaxWidth().height(18.dp))
        }
    }
}

@Composable
private fun SearchCard(appState: AssistantUiState, ui: StockMarketUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("搜索个股", "输入代码或名称，首屏快速加载，K线按需补全")
            GlassPanel(appState.quality, appState.glassIntensity * 0.78f, appState.motionIntensity, 22, Modifier.fillMaxWidth().height(52.dp), GlassRole.Chip) {
                Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("⌕", color = Color.White.copy(alpha = 0.58f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("代码 / 名称", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        BasicTextField(
                            value = ui.query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black),
                            cursorBrush = SolidColor(Color.White.copy(alpha = 0.90f)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    if (ui.query.isBlank()) Text("输入 600519 / 贵州茅台", color = Color.White.copy(alpha = 0.38f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    inner()
                                }
                            }
                        )
                    }
                }
            }
            PressableGlass(appState.quality, appState.glassIntensity * 0.96f, appState.motionIntensity, 22, Modifier.fillMaxWidth().height(44.dp), GlassRole.Floating, onClick = onSearch) {
                CenterText(if (ui.loading) "正在连接行情代理…" else "搜索并打开个股详情", 14, Color.White)
            }
            ui.requestMessage?.let { Text(it, color = Color(0xFFFFC857).copy(alpha = 0.80f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun IndexStrip(appState: AssistantUiState, indices: List<StockIndexSnapshot>) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.84f, appState.motionIntensity, 24, Modifier.fillMaxWidth().height(78.dp), GlassRole.Card) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            indices.take(3).ifEmpty { listOf(StockIndexSnapshot("上证", "--", "--", true), StockIndexSnapshot("深成", "--", "--", true), StockIndexSnapshot("创业板", "--", "--", true)) }.forEach { item ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(item.value, color = Color.White.copy(alpha = 0.90f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun OpportunityDashboard(appState: AssistantUiState, stock: StockDetailUiState, onOpenDetail: () -> Unit, onOpenAssistant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 24, Modifier.weight(1.08f).height(150.dp), GlassRole.Card, onClick = onOpenDetail) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SmallSection("核心机会", "当前关注标的")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stock.quote.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stock.quote.code, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            CompactCard(appState, "AI 信号", signalLabel(stock), signalSubtitle(stock), signalColor(stock), onOpenAssistant)
            CompactCard(appState, "盘口强度", "买一 ${stock.buyLevels.firstOrNull()?.price ?: "--"}", "卖一 ${stock.sellLevels.lastOrNull()?.price ?: "--"}", Color.White, onOpenDetail)
        }
    }
}

@Composable
private fun SectorHeatMapCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    val items = sectorBoard(stock)?.items.orEmpty().ifEmpty { rankBoard(stock)?.items.orEmpty() }
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("热点题材热力图", "板块、概念和成交热度的轻量看板")
            items.take(6).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        PressableGlass(appState.quality, appState.glassIntensity * 0.54f, appState.motionIntensity, 18, Modifier.weight(1f).height(58.dp), GlassRole.Chip, onClick = { if (item.code.isAStockCode()) onOpenCode(item.code) }) {
                            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(item.name, color = Color.White.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.value, color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                }
                            }
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CapitalRadarCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("资金雷达", "主力、超大单、大单与散户方向")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
                InfoChip("超大单", stock.moneyFlow.superLargeOrder, flowColor(stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("大单", stock.moneyFlow.largeOrder, flowColor(stock.moneyFlow.largeOrder), Modifier.weight(1f))
                InfoChip("中小单", "${stock.moneyFlow.mediumOrder} / ${stock.moneyFlow.smallOrder}", Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignalMatrixCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("盘中信号矩阵", "把常见看盘指标压缩成一屏判断")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("趋势", if (stock.quote.isRising) "价格偏强" else "价格走弱", quoteColor(stock.quote.isRising), Modifier.weight(1f))
                InfoChip("量能", "量比 ${stock.quote.volumeRatio}", Color.White, Modifier.weight(1f))
                InfoChip("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("估值", "PE ${stock.quote.peTtm}", Color.White, Modifier.weight(1f))
                InfoChip("人气", stock.quote.popularityRank, Color.White, Modifier.weight(1f))
                InfoChip("风险", riskLabel(stock), riskColor(stock), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeToolGrid(appState: AssistantUiState, selected: String, onSelect: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.82f, appState.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("市场工具", "行情软件常用入口：榜单、题材、资金、日历、预警、选股")
            HomeQuickActions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { label ->
                        val active = selected == label
                        PressableGlass(appState.quality, appState.glassIntensity * if (active) 0.96f else 0.58f, appState.motionIntensity, 16, Modifier.weight(1f).height(46.dp), GlassRole.Chip, onClick = { onSelect(label) }) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(homeActionIcon(label), color = if (active) Color.White else Color.White.copy(alpha = 0.62f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                                Text(label, color = Color.White.copy(alpha = if (active) 0.90f else 0.56f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeToolContent(appState: AssistantUiState, stock: StockDetailUiState, selected: String, onOpenCode: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section(homeActionTitle(selected), homeActionSubtitle(selected))
            when (selected) {
                "热榜", "异动", "龙虎" -> RankRows(appState, rankBoard(stock)?.items.orEmpty(), onOpenCode)
                "板块" -> SectorRows(sectorBoard(stock))
                "资金" -> MoneyFlowRows(stock)
                "日历" -> CalendarRows(stock)
                "研报" -> Text("AI 研报摘要会基于行情、资金流、公告和新闻生成。当前已接入行情上下文：${stock.quote.name} ${stock.quote.changePercent}，成交额 ${stock.quote.amount}。", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 18.sp)
                "预警" -> AlertRows(stock)
                "选股" -> ScreenerRows(stock)
                "新股" -> NewStockRows(stock)
                "ETF" -> EtfRows(stock)
                "公告" -> AnnouncementRows(stock)
            }
        }
    }
}

@Composable
private fun NewsCalendarCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("新闻 / 公告 / 财报日历", "先用行情上下文生成摘要，后续可接独立新闻公告爬虫")
            Text("${stock.quote.name} 今日关注：价格 ${stock.quote.price}，涨跌幅 ${stock.quote.changePercent}，成交额 ${stock.quote.amount}。建议同步观察公告、业绩预告、机构调研和板块联动。", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("财报", "待接入", Color(0xFFFFC857), Modifier.weight(1f))
                InfoChip("公告", "待接入", Color(0xFFFFC857), Modifier.weight(1f))
                InfoChip("异动", signalLabel(stock), signalColor(stock), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmartToolsCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("智能工具箱", "给后续完整股票助手预留真实功能入口")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip(appState, "AI复盘", "问助手", onOpenAssistant, Modifier.weight(1f))
                ToolChip(appState, "条件选股", "待接入", {}, Modifier.weight(1f))
                ToolChip(appState, "价格预警", "本地", {}, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip(appState, "龙虎榜", "观察", {}, Modifier.weight(1f))
                ToolChip(appState, "资金追踪", stock.moneyFlow.mainInflow, {}, Modifier.weight(1f))
                ToolChip(appState, "板块轮动", "热度", {}, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WatchListCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("自选快照", "来自市场概览接口的候选池")
            stock.watchlist.take(5).ifEmpty { listOf(com.yuchen.ailedger.model.StockWatchItem(stock.quote.name, stock.quote.code, stock.quote.price, stock.quote.changePercent, stock.quote.isRising)) }.forEach { item ->
                PressableGlass(appState.quality, appState.glassIntensity * 0.60f, appState.motionIntensity, 16, Modifier.fillMaxWidth().height(38.dp), GlassRole.Chip, onClick = { if (item.code.isAStockCode()) onOpenCode(item.code) }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.code, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(item.price, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketBoardsCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("市场榜单", "涨幅、成交额和板块热度来自爬虫市场接口")
            stock.marketBoards.take(4).forEach { board ->
                SmallSection(board.title, board.subtitle)
                RankRows(appState, board.items.take(4), onOpenCode)
            }
        }
    }
}

@Composable
private fun DetailTopBar(appState: AssistantUiState, ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    val stock = ui.stock
    GlassPanel(appState.quality, appState.glassIntensity * 0.84f, appState.motionIntensity, 999, Modifier.fillMaxWidth().height(52.dp), GlassRole.Nav) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableGlass(appState.quality, appState.glassIntensity * 0.66f, appState.motionIntensity, 999, Modifier.width(44.dp).height(36.dp), GlassRole.Chip, onClick = onBack) { CenterText("‹", 25, Color.White.copy(alpha = 0.92f)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stock.quote.name, color = Color.White, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stock.quote.code} · ${stock.quote.market} · ${if (ui.kLineLoading) "K线加载中" else if (stock.kLinePoints.size >= 20) "K线已就绪" else "分时快返回"}", color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            PressableGlass(appState.quality, appState.glassIntensity * 0.66f, appState.motionIntensity, 999, Modifier.width(44.dp).height(36.dp), GlassRole.Chip, onClick = onRefresh) { CenterText(if (ui.loading) "…" else "⟳", 18, Color.White.copy(alpha = 0.82f)) }
        }
    }
}

@Composable
private fun TradingTerminalCard(appState: AssistantUiState, ui: StockMarketUiState, onSelectTab: (String) -> Unit, onSelectDepth: (String) -> Unit) {
    val stock = ui.stock
    GlassPanel(appState.quality, appState.glassIntensity * 0.70f, appState.motionIntensity, 18, Modifier.fillMaxWidth().height(600.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QuoteHeader(stock, ui.loading)
            PopularityTicker(stock)
            StockTabRow(appState, ui.selectedTab, onSelectTab)
            Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartColumn(stock, ui.selectedTab, ui.kLineLoading, Modifier.weight(2.18f))
                DepthColumn(appState, stock, ui.depthTab, onSelectDepth, Modifier.weight(0.98f))
            }
            FlowStrip(stock)
        }
    }
}

@Composable
private fun QuoteHeader(stock: StockDetailUiState, loading: Boolean) {
    val q = stock.quote
    Row(Modifier.fillMaxWidth().height(118.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(q.price, color = quoteColor(q.isRising), fontSize = 43.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("${q.changeAmount}  ${q.changePercent}", color = quoteColor(q.isRising), fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(if (loading) "刷新中" else stock.dataSourceLabel, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1.72f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            quoteBoardMetrics(stock).take(12).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { QuoteMetric(it, Modifier.weight(1f)) } }
            }
        }
    }
}

@Composable
private fun QuoteMetric(metric: StockMetric, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(metric.label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(metric.value, color = toneColor(metric.tone).copy(alpha = 0.95f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PopularityTicker(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("🔥", fontSize = 15.sp)
        Text("${stock.quote.name} 个股人气排名 ${stock.quote.popularityRank.ifBlank { "--" }}", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("×", color = Color.White.copy(alpha = 0.48f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StockTabRow(appState: AssistantUiState, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        StockTabs.forEach { label ->
            val active = selected == label
            PressableGlass(appState.quality, appState.glassIntensity * if (active) 0.95f else 0.56f, appState.motionIntensity, 12, Modifier.weight(1f).height(34.dp), GlassRole.Chip, onClick = { onSelect(label) }) {
                Column(Modifier.fillMaxSize().padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, color = Color.White.copy(alpha = if (active) 0.98f else 0.50f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(2.dp)) { if (active) Canvas(Modifier.fillMaxSize()) { drawRoundRect(RiseRed.copy(alpha = 0.90f), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())) } }
                }
            }
        }
    }
}

@Composable
private fun ChartColumn(stock: StockDetailUiState, selectedTab: String, kLineLoading: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (selectedTab == "分时") IntradayHeader(stock) else KLineHeader(stock, selectedTab)
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            if (selectedTab == "分时") {
                IntradayCanvas(stock, Modifier.fillMaxSize())
                IntradayAxisOverlay(stock)
            } else {
                val points = kLinesForTab(stock, selectedTab)
                if (points.size >= 2) KLineCanvas(points, Modifier.fillMaxSize()) else KLineEmptyOverlay(kLineLoading)
            }
        }
        if (selectedTab == "分时") TimeAxis() else KLineAxis(stock, selectedTab)
        if (selectedTab == "分时") VolumeCanvas(stock, Modifier.fillMaxWidth().height(72.dp)) else KLineVolumeCanvas(kLinesForTab(stock, selectedTab), Modifier.fillMaxWidth().height(72.dp))
    }
}

@Composable
private fun IntradayHeader(stock: StockDetailUiState) {
    val avg = stock.minutePoints.map { it.average }.averageOrNull() ?: stock.quote.previousClose
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("集合竞价", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("均价 ${formatTwo(avg)}", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("最新 ${stock.quote.price}", color = quoteColor(stock.quote.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun KLineHeader(stock: StockDetailUiState, tab: String) {
    val points = kLinesForTab(stock, tab)
    val ma5 = points.takeLast(5).map { it.close }.averageOrNull() ?: 0f
    val ma10 = points.takeLast(10).map { it.close }.averageOrNull() ?: 0f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tab, color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("MA5 ${formatTwo(ma5)}", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("MA10 ${formatTwo(ma10)}", color = Color(0xFF9FD2FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun KLineEmptyOverlay(loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (loading) "正在加载真实K线…" else "点击K线页签加载历史K线", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DepthColumn(appState: AssistantUiState, stock: StockDetailUiState, depthTab: String, onSelectDepth: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            DepthTabs.forEach { label ->
                val active = depthTab == label
                PressableGlass(appState.quality, appState.glassIntensity * if (active) 0.70f else 0.42f, appState.motionIntensity, 8, Modifier.weight(1f).height(24.dp), GlassRole.Chip, onClick = { onSelectDepth(label) }) { CenterText(label, 12, if (active) RiseRed else Color.White.copy(alpha = 0.50f)) }
            }
        }
        if (depthTab == "五档") {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                stock.sellLevels.takeLast(5).forEach { OrderRow(it) }
                Box(Modifier.fillMaxWidth().height(2.dp)) { Canvas(Modifier.fillMaxSize()) { drawLine(RiseRed.copy(alpha = 0.85f), Offset(0f, size.height / 2f), Offset(size.width * 0.58f, size.height / 2f), 2.dp.toPx()); drawLine(FallGreen.copy(alpha = 0.85f), Offset(size.width * 0.58f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx()) } }
                stock.buyLevels.take(5).forEach { OrderRow(it) }
            }
            normalizedTicks(stock).take(3).forEach { TickRow(it) }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { normalizedTicks(stock).take(10).forEach { TickRow(it) } }
            Text("成交明细来自分时聚合，后续可接逐笔接口", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun FlowStrip(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("两市北向净买", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("主力 ${stock.moneyFlow.mainInflow}", color = flowColor(stock.moneyFlow.mainInflow), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowChip("超大", stock.moneyFlow.superLargeOrder, Modifier.weight(1f))
            FlowChip("大单", stock.moneyFlow.largeOrder, Modifier.weight(1f))
            FlowChip("中单", stock.moneyFlow.mediumOrder, Modifier.weight(1f))
            FlowChip("小单", stock.moneyFlow.smallOrder, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionPanel(appState: AssistantUiState, ui: StockMarketUiState, onAction: (String) -> Unit, onClose: () -> Unit, onRefresh: () -> Unit) {
    val stock = ui.stock
    GlassPanel(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(actionTitle(ui.activeAction.orEmpty()), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                PressableGlass(appState.quality, appState.glassIntensity * 0.55f, appState.motionIntensity, 999, Modifier.width(38.dp).height(28.dp), GlassRole.Chip, onClick = onClose) { CenterText("×", 16, Color.White.copy(alpha = 0.72f)) }
            }
            when (ui.activeAction) {
                "下单" -> SimulatedOrderPanel(appState, stock)
                "社区" -> Text("社区观点会接入后端后显示真实讨论。当前行情上下文：${stock.quote.name} ${stock.quote.changePercent}，成交额 ${stock.quote.amount}。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 17.sp)
                "加自选" -> WatchStatePanel(ui.isWatched, stock)
                "更多" -> MorePanel(appState, stock, ui.selectedTab, onRefresh)
            }
        }
    }
}

@Composable
private fun AiSummaryCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 看盘摘要", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(if (stock.errorMessage != null) "示例" else if (stock.quote.isRising) "偏强" else "谨慎", color = if (stock.errorMessage != null) Color(0xFFFFC857) else quoteColor(stock.quote.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Text(stock.aiSummary, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, lineHeight = 18.sp)
            Text(stock.dataSourceLabel, color = Color.White.copy(alpha = 0.36f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FundamentalsCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("资料与消息", "财务、公告、新闻和研报入口")
            stock.fundamentals.chunked(3).forEach { row -> Text(row.joinToString("    ") { "${it.label} ${it.value}" }, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BottomActionBar(appState: AssistantUiState, ui: StockMarketUiState, onAction: (String) -> Unit) {
    val stock = ui.stock
    GlassPanel(appState.quality, appState.glassIntensity * 0.80f, appState.motionIntensity, 22, Modifier.fillMaxWidth().height(68.dp), GlassRole.Nav) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("沪 ${stock.indices.firstOrNull()?.value ?: stock.quote.price}", color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(stock.indices.firstOrNull()?.changePercent ?: stock.quote.changePercent, color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            listOf("下单", "社区", "加自选", "更多").forEach { label ->
                val display = if (label == "加自选" && ui.isWatched) "已自选" else label
                val active = ui.activeAction == label || (label == "加自选" && ui.isWatched)
                PressableGlass(appState.quality, appState.glassIntensity * if (active) 0.95f else 0.55f, appState.motionIntensity, 16, Modifier.weight(1f).height(52.dp), GlassRole.Chip, onClick = { onAction(label) }) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(if (label == "加自选") if (ui.isWatched) "★" else "+" else if (label == "更多") "…" else "□", color = Color.White.copy(alpha = if (active) 0.96f else 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(display, color = Color.White.copy(alpha = if (active) 0.90f else 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatedOrderPanel(appState: AssistantUiState, stock: StockDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("模拟委托入口，不连接真实券商账户，不产生真实交易。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableGlass(appState.quality, appState.glassIntensity * 0.78f, appState.motionIntensity, 18, Modifier.weight(1f).height(42.dp), GlassRole.Chip, onClick = {}) { CenterText("模拟买入 ${stock.quote.price}", 13, RiseRed) }
            PressableGlass(appState.quality, appState.glassIntensity * 0.78f, appState.motionIntensity, 18, Modifier.weight(1f).height(42.dp), GlassRole.Chip, onClick = {}) { CenterText("模拟卖出 ${stock.quote.price}", 13, FallGreen) }
        }
    }
}

@Composable
private fun WatchStatePanel(isWatched: Boolean, stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isWatched) "已加入自选" else "已移出自选", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text("${stock.quote.name} ${stock.quote.code} · 当前为本地状态，后续可接账号同步。", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text(if (isWatched) "★" else "☆", color = Color(0xFFFFC857), fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MorePanel(appState: AssistantUiState, stock: StockDetailUiState, selectedTab: String, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当前图表：$selectedTab · 数据源：${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        PressableGlass(appState.quality, appState.glassIntensity * 0.55f, appState.motionIntensity, 14, Modifier.fillMaxWidth().height(38.dp), GlassRole.Chip, onClick = onRefresh) { CenterText("立即刷新行情", 13, Color.White) }
    }
}

@Composable
private fun RankRows(appState: AssistantUiState, items: List<StockRankItem>, onOpenCode: (String) -> Unit) {
    val rows = items.ifEmpty { listOf(StockRankItem("暂无数据", "--", "--", "--", true)) }
    rows.take(5).forEachIndexed { index, item ->
        val clickable = item.code.isAStockCode()
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${index + 1}", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
            Column(Modifier.weight(1.2f)) {
                Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.code, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(item.value, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PressableGlass(appState.quality, appState.glassIntensity * 0.42f, appState.motionIntensity, 12, Modifier.width(52.dp).height(28.dp), GlassRole.Chip, onClick = { if (clickable) onOpenCode(item.code) }) {
                CenterText(item.changePercent, 10, quoteColor(item.isRising))
            }
        }
    }
}

@Composable
private fun SectorRows(board: StockMarketBoard?) {
    val items = board?.items.orEmpty().ifEmpty { listOf(StockRankItem("板块数据加载中", "--", "--", "--", true)) }
    items.take(6).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item -> InfoChip(item.name, "${item.value}  ${item.changePercent}", quoteColor(item.isRising), Modifier.weight(1f)) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoneyFlowRows(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
        InfoChip("超大单", stock.moneyFlow.superLargeOrder, flowColor(stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip("大单", stock.moneyFlow.largeOrder, flowColor(stock.moneyFlow.largeOrder), Modifier.weight(1f))
        InfoChip("中小单", "${stock.moneyFlow.mediumOrder} / ${stock.moneyFlow.smallOrder}", Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun CalendarRows(stock: StockDetailUiState) {
    InfoChip("今日事项", "交易中 · ${stock.quote.market}", Color.White, Modifier.fillMaxWidth())
    InfoChip("风险提示", "公告、财报与停复牌后续接入", Color(0xFFFFC857), Modifier.fillMaxWidth())
}

@Composable
private fun AlertRows(stock: StockDetailUiState) {
    InfoChip("价格预警", "突破 ${stock.quote.high} / 跌破 ${stock.quote.low}", Color.White, Modifier.fillMaxWidth())
    InfoChip("量能预警", "量比 ${stock.quote.volumeRatio} · 换手 ${stock.quote.turnoverRate}", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun ScreenerRows(stock: StockDetailUiState) {
    InfoChip("强势筛选", "涨幅 ${stock.quote.changePercent} · 量比 ${stock.quote.volumeRatio}", quoteColor(stock.quote.isRising), Modifier.fillMaxWidth())
    InfoChip("高换手观察", "换手 ${stock.quote.turnoverRate} · 成交 ${stock.quote.amount}", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun NewStockRows(stock: StockDetailUiState) {
    InfoChip("新股日历", "后续接入申购 / 上市 / 中签", Color(0xFFFFC857), Modifier.fillMaxWidth())
    InfoChip("当前关注", "${stock.quote.name} · ${stock.quote.market}", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun EtfRows(stock: StockDetailUiState) {
    InfoChip("宽基ETF", "上证 / 深成 / 创业板联动", Color.White, Modifier.fillMaxWidth())
    InfoChip("行业ETF", "按板块热度后续推荐", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun AnnouncementRows(stock: StockDetailUiState) {
    InfoChip("公司公告", "${stock.quote.name} 新闻公告爬虫待接入", Color(0xFFFFC857), Modifier.fillMaxWidth())
    InfoChip("交易提醒", "价格 ${stock.quote.price} · 换手 ${stock.quote.turnoverRate}", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun InfoChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.height(48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactCard(appState: AssistantUiState, title: String, value: String, subtitle: String, valueColor: Color, onClick: () -> Unit = {}) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.76f, appState.motionIntensity, 22, Modifier.fillMaxWidth().height(70.dp), GlassRole.Card, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolChip(appState: AssistantUiState, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.56f, appState.motionIntensity, 16, modifier.height(50.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SmallSection(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.35f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CenterText(text: String, size: Int, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun OrderRow(level: StockOrderLevel) {
    Row(Modifier.fillMaxWidth().height(21.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(level.label, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.62f), maxLines = 1)
        Text(level.price, color = quoteColor(level.isAsk), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.78f), maxLines = 1, textAlign = TextAlign.End)
        Text(level.volume, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.70f), maxLines = 1, textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TickRow(tick: StockTradeTick) {
    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tick.time, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.80f), maxLines = 1)
        Text(tick.price, color = quoteColor(tick.isBuy), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.72f), maxLines = 1, textAlign = TextAlign.End)
        Text(tick.volume, color = Color.White.copy(alpha = 0.76f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.68f), maxLines = 1, textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FlowChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = flowColor(value), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTrendCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val prices = points.map { it.price }
        val minValue = prices.minOrNull() ?: stock.quote.previousClose
        val maxValue = prices.maxOrNull() ?: stock.quote.previousClose
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val left = 2.dp.toPx(); val right = size.width - 2.dp.toPx(); val top = 5.dp.toPx(); val bottom = size.height - 5.dp.toPx()
        val path = Path()
        prices.forEachIndexed { index, value ->
            val x = left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat()
            val y = bottom - (value - minValue) / range * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color.White.copy(alpha = 0.12f), Offset(left, (top + bottom) / 2f), Offset(right, (top + bottom) / 2f), 1.dp.toPx())
        drawPath(path, quoteColor(stock.quote.isRising).copy(alpha = 0.88f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BreadthBar(stock: StockDetailUiState, modifier: Modifier) {
    val rise = risingCount(stock).coerceAtLeast(1)
    val fall = fallingCount(stock).coerceAtLeast(1)
    Canvas(modifier) {
        val total = (rise + fall).toFloat()
        val redWidth = size.width * rise / total
        drawRoundRect(RiseRed.copy(alpha = 0.70f), size = Size(redWidth, size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        drawRoundRect(FallGreen.copy(alpha = 0.70f), topLeft = Offset(redWidth, 0f), size = Size(size.width - redWidth, size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
    }
}

@Composable
private fun IntradayCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val prices = points.map { it.price }
        val averages = points.map { it.average }
        val minValue = minOf(prices.minOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 0.985f
        val maxValue = maxOf(prices.maxOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 1.015f
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx()
        drawChartGrid(left, right, top, bottom)
        val baseY = bottom - (stock.quote.previousClose - minValue) / range * (bottom - top)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(left, baseY), Offset(right, baseY), 1.dp.toPx(), cap = StrokeCap.Round)
        fun point(index: Int, value: Float): Offset = Offset(left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat(), bottom - (value - minValue) / range * (bottom - top))
        val pricePath = Path(); prices.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) pricePath.moveTo(p.x, p.y) else pricePath.lineTo(p.x, p.y) }
        val avgPath = Path(); averages.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) avgPath.moveTo(p.x, p.y) else avgPath.lineTo(p.x, p.y) }
        drawPath(pricePath, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(avgPath, Color(0xFFFFC857), style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun IntradayAxisOverlay(stock: StockDetailUiState) {
    val prices = stock.minutePoints.map { it.price }
    val base = stock.quote.previousClose.takeIf { it > 0f } ?: prices.firstOrNull() ?: 1f
    val high = prices.maxOrNull() ?: base
    val low = prices.minOrNull() ?: base
    Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTwo(high), color = quoteColor(high >= base), fontSize = 10.sp, fontWeight = FontWeight.Black); Text(formatSignedPct((high - base) / base * 100f), color = quoteColor(high >= base), fontSize = 10.sp, fontWeight = FontWeight.Black) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTwo(base), color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("0.00%", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTwo(low), color = FallGreen, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(formatSignedPct((low - base) / base * 100f), color = FallGreen, fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun KLineCanvas(points: List<StockKLinePoint>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx()
        drawChartGrid(left, right, top, bottom)
        val minValue = points.minOf { it.low }
        val maxValue = points.maxOf { it.high }
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val space = (right - left) / points.size.coerceAtLeast(1)
        val candleWidth = (space * 0.56f).coerceIn(3.dp.toPx(), 12.dp.toPx())
        fun y(value: Float): Float = bottom - (value - minValue) / range * (bottom - top)
        points.forEachIndexed { index, p ->
            val x = left + space * index + space / 2f
            val color = quoteColor(p.close >= p.open).copy(alpha = 0.86f)
            drawLine(color, Offset(x, y(p.high)), Offset(x, y(p.low)), 1.dp.toPx(), cap = StrokeCap.Round)
            val rectTop = minOf(y(p.open), y(p.close))
            val rectHeight = max(2.dp.toPx(), abs(y(p.open) - y(p.close)))
            drawRoundRect(color = color, topLeft = Offset(x - candleWidth / 2f, rectTop), size = Size(candleWidth, rectHeight), cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
private fun VolumeCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        val maxVolume = points.maxOfOrNull { it.volumeRatio } ?: 1f
        val barSpace = (right - left) / points.size.coerceAtLeast(1)
        val barWidth = barSpace * 0.58f
        points.forEachIndexed { index, point ->
            val x = left + index * barSpace + barSpace / 2f
            val prev = points.getOrNull(index - 1)?.price ?: stock.quote.previousClose
            val color = if (point.price >= prev) RiseRed else FallGreen
            val h = (bottom - top) * (point.volumeRatio / maxVolume).coerceIn(0.04f, 1f)
            drawRoundRect(color.copy(alpha = 0.66f), Offset(x - barWidth / 2f, bottom - h), Size(barWidth, h), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
private fun KLineVolumeCanvas(points: List<StockKLinePoint>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        val maxVolume = points.maxOfOrNull { it.volume } ?: 1f
        val barSpace = (right - left) / points.size.coerceAtLeast(1)
        val barWidth = (barSpace * 0.58f).coerceAtMost(10.dp.toPx())
        points.forEachIndexed { index, point ->
            val x = left + index * barSpace + barSpace / 2f
            val color = if (point.close >= point.open) RiseRed else FallGreen
            val h = (bottom - top) * (point.volume / maxVolume).coerceIn(0.04f, 1f)
            drawRoundRect(color.copy(alpha = 0.66f), Offset(x - barWidth / 2f, bottom - h), Size(barWidth, h), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(left: Float, right: Float, top: Float, bottom: Float) {
    repeat(5) { i -> val y = top + (bottom - top) * i / 4f; drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round) }
    repeat(4) { i -> val x = left + (right - left) * i / 3f; drawLine(Color.White.copy(alpha = 0.08f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(), cap = StrokeCap.Round) }
}

@Composable
private fun TimeAxis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("09:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("11:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("15:00", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KLineAxis(stock: StockDetailUiState, tab: String) {
    val points = kLinesForTab(stock, tab)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.firstOrNull()?.date ?: "--", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(tab, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(points.lastOrNull()?.date ?: "--", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun normalizedTicks(stock: StockDetailUiState): List<StockTradeTick> = stock.tradeTicks.ifEmpty {
    stock.minutePoints.takeLast(8).reversed().mapIndexed { index, item ->
        val prev = stock.minutePoints.getOrNull(stock.minutePoints.lastIndex - index - 1)?.price ?: stock.quote.previousClose
        StockTradeTick(item.time.ifBlank { "--" }, formatTwo(item.price), ((item.volumeRatio * 1000).toInt()).coerceAtLeast(1).toString(), if (item.price >= prev) "买" else "卖", item.price >= prev)
    }
}

private fun kLinesForTab(stock: StockDetailUiState, tab: String): List<StockKLinePoint> {
    val source = stock.kLinePoints
    if (source.isEmpty()) return emptyList()
    return when (tab) {
        "五日" -> source.takeLast(5)
        "周K" -> source.takeLast(80).chunked(5).mapNotNull { mergeKLine(it) }
        "月K" -> source.takeLast(160).chunked(20).mapNotNull { mergeKLine(it) }
        else -> source.takeLast(60)
    }
}

private fun mergeKLine(points: List<StockKLinePoint>): StockKLinePoint? {
    if (points.isEmpty()) return null
    val first = points.first(); val last = points.last()
    return StockKLinePoint(last.date, first.open, last.close, points.maxOf { it.high }, points.minOf { it.low }, points.sumOf { it.volume.toDouble() }.toFloat(), points.sumOf { it.amount.toDouble() }.toFloat(), last.changePercent)
}

private fun quoteBoardMetrics(stock: StockDetailUiState): List<StockMetric> = listOf(
    StockMetric("高", stock.quote.high, StockTone.Rising),
    StockMetric("市值", stock.quote.totalMarketValue),
    StockMetric("量比", stock.quote.volumeRatio, StockTone.Rising),
    StockMetric("低", stock.quote.low, StockTone.Falling),
    StockMetric("流通", stock.quote.floatMarketValue),
    StockMetric("换手", stock.quote.turnoverRate),
    StockMetric("开", stock.quote.open, if (stock.quote.isRising) StockTone.Rising else StockTone.Falling),
    StockMetric("市盈TTM", stock.quote.peTtm),
    StockMetric("市净率", stock.quote.pb),
    StockMetric("成交额", stock.quote.amount),
    StockMetric("人气", stock.quote.popularityRank),
    StockMetric("来源", if (stock.errorMessage != null) "示例" else "实时")
)

private fun rankBoard(stock: StockDetailUiState): StockMarketBoard? = stock.marketBoards.firstOrNull { it.title.contains("涨") || it.title.contains("热") } ?: stock.marketBoards.firstOrNull()
private fun sectorBoard(stock: StockDetailUiState): StockMarketBoard? = stock.marketBoards.firstOrNull { it.title.contains("板块") || it.title.contains("概念") }

private fun risingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { it.isRising }.coerceAtLeast(if (stock.quote.isRising) 1 else 0)
private fun fallingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { !it.isRising }.coerceAtLeast(if (!stock.quote.isRising) 1 else 0)

private fun marketMoodText(stock: StockDetailUiState): String = when {
    stock.errorMessage != null -> "等待真实行情"
    stock.quote.isRising && !stock.moneyFlow.mainInflow.startsWith("-") -> "量价偏强"
    stock.quote.isRising -> "价格偏强"
    else -> "谨慎观察"
}

private fun signalLabel(stock: StockDetailUiState): String = when {
    stock.errorMessage != null -> "数据待确认"
    stock.quote.isRising && stock.moneyFlow.mainInflow.startsWith("+") -> "资金共振"
    stock.quote.isRising -> "趋势修复"
    else -> "防守观察"
}

private fun signalSubtitle(stock: StockDetailUiState): String = "量比 ${stock.quote.volumeRatio} · 换手 ${stock.quote.turnoverRate}"
private fun signalColor(stock: StockDetailUiState): Color = if (stock.quote.isRising) RiseRed else FallGreen
private fun riskLabel(stock: StockDetailUiState): String = if (stock.quote.turnoverRate.contains("--")) "未知" else if (stock.quote.isRising) "追高" else "回撤"
private fun riskColor(stock: StockDetailUiState): Color = if (stock.quote.isRising) Color(0xFFFFC857) else FallGreen

private fun statusText(ui: StockMarketUiState): String = when {
    ui.loading -> "连接行情代理中"
    ui.marketLoading -> "实时行情 · 市场概览加载中"
    ui.stock.errorMessage != null -> "示例数据 · ${ui.stock.errorMessage}"
    else -> "实时行情 · ${ui.stock.dataSourceLabel}"
}

private fun statusColor(ui: StockMarketUiState): Color = when {
    ui.loading || ui.marketLoading || ui.stock.errorMessage != null -> Color(0xFFFFC857)
    else -> Color.White.copy(alpha = 0.66f)
}

private fun homeActionIcon(label: String): String = when (label) {
    "热榜" -> "🔥"
    "板块" -> "▦"
    "资金" -> "¥"
    "异动" -> "↯"
    "龙虎" -> "榜"
    "日历" -> "◎"
    "研报" -> "AI"
    "预警" -> "!"
    "选股" -> "筛"
    "新股" -> "新"
    "ETF" -> "E"
    "公告" -> "讯"
    else -> "•"
}

private fun homeActionTitle(label: String): String = when (label) {
    "热榜" -> "人气热榜"
    "板块" -> "板块雷达"
    "资金" -> "资金流向"
    "异动" -> "盘中异动"
    "龙虎" -> "龙虎榜观察"
    "日历" -> "交易日历"
    "研报" -> "AI 研报"
    "预警" -> "行情预警"
    "选股" -> "条件选股"
    "新股" -> "新股日历"
    "ETF" -> "ETF 观察"
    "公告" -> "公告快讯"
    else -> label
}

private fun homeActionSubtitle(label: String): String = when (label) {
    "热榜" -> "跟踪当前热度、涨跌幅和成交额"
    "板块" -> "读取爬虫板块热度榜，不再硬编码"
    "资金" -> "主力、超大单、大单与中小单"
    "异动" -> "价格、量比、换手和盘口变化"
    "龙虎" -> "后续接入营业部与机构席位"
    "日历" -> "公告、财报、停复牌和新股事项"
    "研报" -> "用 AI 汇总行情和基本面线索"
    "预警" -> "价格、量能和资金流触发提醒"
    "选股" -> "根据涨跌幅、量比、换手做初筛"
    "新股" -> "申购、上市、中签提醒入口"
    "ETF" -> "宽基与行业ETF观察入口"
    "公告" -> "公告和新闻爬虫的预留入口"
    else -> "行情工具"
}

private fun actionTitle(action: String): String = when (action) {
    "下单" -> "模拟下单"
    "社区" -> "社区观点"
    "加自选" -> "自选状态"
    "更多" -> "更多功能"
    else -> action
}

private fun toneColor(tone: StockTone): Color = when (tone) {
    StockTone.Rising -> RiseRed
    StockTone.Falling -> FallGreen
    StockTone.Neutral -> Color.White
}

private fun quoteColor(isRising: Boolean): Color = if (isRising) RiseRed else FallGreen

private fun flowColor(value: String): Color = when {
    value.trim().startsWith("-") -> FallGreen
    value.trim().startsWith("+") -> RiseRed
    else -> Color.White.copy(alpha = 0.78f)
}

private fun String.isAStockCode(): Boolean = length == 6 && all { it.isDigit() }
private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
private fun formatTwo(value: Float): String = "%.2f".format(value)
private fun formatSignedPct(value: Float): String = "%+.2f%%".format(value)

private val RiseRed = Color(0xFFFF4D5D)
private val FallGreen = Color(0xFF41D873)
