package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
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
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import kotlin.math.abs

private val StockTabs = listOf("分时", "日K", "周K", "月K", "五日")
private val HomeQuickActions = listOf("自选", "热榜", "板块", "资金", "异动", "新闻", "研报", "预警")
private val DetailInfoTabs = listOf("看点", "资金", "财务", "资讯")
private val RiseRed = Color(0xFFFF8F8F)
private val FallGreen = Color(0xFF80F7B4)
private val Aqua = Color(0xFF8DF9EA)
private val AvgYellow = Color(0xFFFFD36E)
private val MaBlue = Color(0xFF9DCBFF)
private val SectionLine = Color.White.copy(alpha = 0.085f)
private val MarketOverviewPanelHeight = 340.dp
private val MarketHotPanelHeight = 470.dp
private val MarketWatchPanelHeight = 450.dp
private val StockDetailCorePanelHeight = 880.dp
private val StockDetailInfoPanelHeight = 560.dp

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
            onAction = viewModel::handleAction,
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
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketOverviewPanelHeight)) {
                MarketOverviewHomeSection(appState, ui, onQueryChange, onSearch, onOpenDetail)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketHotPanelHeight)) {
                MarketGatewayHomeSection(appState, ui.stock, ui.selectedHomeAction, onSelectHomeAction, onOpenCode)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketWatchPanelHeight)) {
                WatchNewsAiHomeSection(ui.stock, onOpenCode, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun StockDetailPage(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DetailTopBar(appState, ui, onBack, onRefresh) }
        item {
            StockParentGlassPanel(appState, Modifier.height(StockDetailCorePanelHeight)) {
                DetailQuoteHeaderSection(ui.stock)
                SectionDivider()
                DetailMetricTickerSection(ui.stock)
                SectionDivider()
                ProfessionalTerminalSection(appState, ui, onSelectTab)
                SectionDivider()
                DetailActionBar(appState, ui, onAction)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(StockDetailInfoPanelHeight)) {
                DetailDecisionSection(appState, ui.stock, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun HomeHeader(appState: AssistantUiState, ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StockButton(appState, "‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
            Spacer(Modifier.weight(1f))
            Text("市场总览 · A股", color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StockIconButton(appState, if (ui.loading || ui.marketLoading) "…" else "⟳", onRefresh)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE MARKET", color = Aqua.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("市场概览", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(statusText(ui), color = statusColor(ui), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailTopBar(appState: AssistantUiState, ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StockButton(appState, "‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
        Spacer(Modifier.weight(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ui.stock.quote.name, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ui.stock.quote.code} · ${ui.stock.quote.market}", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        StockIconButton(appState, if (ui.loading || ui.kLineLoading) "…" else "⟳", onRefresh)
    }
}

@Composable
private fun StockParentGlassPanel(appState: AssistantUiState, modifier: Modifier, content: @Composable () -> Unit) {
    GlassPanel(
        quality = appState.quality,
        glassIntensity = appState.glassIntensity * 0.92f,
        motionIntensity = appState.motionIntensity,
        radius = 30,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SectionLine))
}

@Composable
private fun MarketOverviewHomeSection(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: () -> Unit
) {
    TopSearchBar(appState, ui, onQueryChange, onSearch)
    SectionDivider()
    Section("主要指数", "沪深京核心指数与市场温度")
    IndexStripSection(ui.stock.indices)
    SectionDivider()
    MarketMoodSection(ui.stock, onOpenDetail)
}

@Composable
private fun MarketGatewayHomeSection(
    appState: AssistantUiState,
    stock: StockDetailUiState,
    selected: String,
    onSelect: (String) -> Unit,
    onOpenCode: (String) -> Unit
) {
    Section("信息入口", "自选、热榜、板块、资金、新闻一屏进入")
    HomeToolGrid(appState, selected, onSelect)
    SectionDivider()
    HomeToolContent(stock, selected, onOpenCode)
}

@Composable
private fun WatchNewsAiHomeSection(stock: StockDetailUiState, onOpenCode: (String) -> Unit, onOpenAssistant: () -> Unit) {
    WatchListSection(stock, onOpenCode)
    SectionDivider()
    NewsSignalSection(stock)
    SectionDivider()
    AiSummarySection(stock, onOpenAssistant)
}

@Composable
private fun TopSearchBar(appState: AssistantUiState, ui: StockMarketUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("⌕", color = Aqua.copy(alpha = 0.88f), fontSize = 19.sp, fontWeight = FontWeight.Black)
        BasicTextField(
            value = ui.query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black),
            cursorBrush = SolidColor(Color.White.copy(alpha = 0.92f)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (ui.query.isBlank()) Text("搜索股票 / 板块 / 新闻", color = Color.White.copy(alpha = 0.38f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    inner()
                }
            }
        )
        StockButton(appState, if (ui.loading) "连接" else "搜索", Modifier.width(70.dp).height(38.dp), onSearch, active = true)
    }
}

@Composable
private fun MarketMoodSection(stock: StockDetailUiState, onOpenDetail: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("上涨观察", "${risingCount(stock)} 个", RiseRed, Modifier.weight(1f))
            MetricTile("谨慎观察", "${fallingCount(stock)} 个", FallGreen, Modifier.weight(1f))
            MetricTile("热点池", "${hotPoolCount(stock)} 条", Aqua, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(42.dp).clickable(onClick = onOpenDetail), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("当前关注", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${stock.quote.name} · ${stock.quote.code}", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailQuoteHeaderSection(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stock.quote.name.ifBlank { "个股详情" }, color = Color.White, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${stock.quote.code} · ${stock.quote.market} · ${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("人气排名 ${stock.quote.popularityRank}", color = Aqua.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stock.quote.price.ifBlank { "--" }, color = quoteColor(stock.quote.isRising), fontSize = 39.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Text("${stock.quote.changeAmount}  ${stock.quote.changePercent}", color = quoteColor(stock.quote.isRising), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("成交额 ${stock.quote.amount}", color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun DetailMetricTickerSection(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("高", stock.quote.high, RiseRed, Modifier.weight(1f))
            CompactMetricCell("低", stock.quote.low, FallGreen, Modifier.weight(1f))
            CompactMetricCell("开", stock.quote.open, Color.White, Modifier.weight(1f))
            CompactMetricCell("昨收", stock.quote.previousClose.toString(), Color.White, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("市值", stock.quote.totalMarketValue, Color.White, Modifier.weight(1f))
            CompactMetricCell("流通", stock.quote.floatMarketValue, Color.White, Modifier.weight(1f))
            CompactMetricCell("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            CompactMetricCell("量比", stock.quote.volumeRatio, quoteColor(stock.quote.isRising), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("市盈TTM", stock.quote.peTtm, Color.White, Modifier.weight(1f))
            CompactMetricCell("市净率", stock.quote.pb, Color.White, Modifier.weight(1f))
            CompactMetricCell("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
            CompactMetricCell("温度", stock.quote.popularityRank, Aqua, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfessionalTerminalSection(appState: AssistantUiState, ui: StockMarketUiState, onSelectTab: (String) -> Unit) {
    val isTimeShare = ui.selectedTab == "分时" || ui.selectedTab == "五日"
    Column(Modifier.fillMaxWidth().height(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StockTabs.forEach { tab -> StockButton(appState, tab, Modifier.weight(1f).height(34.dp), { onSelectTab(tab) }, active = ui.selectedTab == tab) }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isTimeShare) "集合竞价" else "历史K线", color = Color.White.copy(alpha = 0.60f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(if (isTimeShare) "均价 ${averageLineLabel(ui.stock)}" else "MA5", color = AvgYellow.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(if (isTimeShare) "最新 ${ui.stock.quote.price}" else "MA10", color = if (isTimeShare) quoteColor(ui.stock.quote.isRising) else MaBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(ui.stock.quote.changePercent, color = quoteColor(ui.stock.quote.isRising), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                StockMainChartCanvas(ui.stock, ui.selectedTab, Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CompactMetricCell(if (isTimeShare) "副图" else "均线", if (isTimeShare) "MACD 待接入" else "MA5/MA10", AvgYellow, Modifier.weight(1f))
                    CompactMetricCell("成交量", ui.stock.quote.amount, Color.White, Modifier.weight(1f))
                    CompactMetricCell("PB", ui.stock.quote.pb, Color.White, Modifier.weight(1f))
                }
                ui.requestMessage?.let { Text(it, color = Color(0xFFFFC857).copy(alpha = 0.82f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            RightDepthTapeColumn(ui.stock, Modifier.width(118.dp).fillMaxSize())
        }
    }
}

@Composable
private fun RightDepthTapeColumn(stock: StockDetailUiState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("盘口", color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        DepthMiniRows(stock.sellLevels, isAskSide = true, Modifier.weight(1.25f))
        Box(Modifier.fillMaxWidth().height(2.dp).background(quoteColor(stock.quote.isRising).copy(alpha = 0.56f)))
        DepthMiniRows(stock.buyLevels, isAskSide = false, Modifier.weight(1.25f))
        SectionDivider()
        Text("逐笔", color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        TradeMiniRows(stock.tradeTicks, Modifier.weight(1f))
    }
}

@Composable
private fun DepthMiniRows(levels: List<StockOrderLevel>, isAskSide: Boolean, modifier: Modifier) {
    val display = if (isAskSide) normalizeSellLevels(levels) else levels.take(6).ifEmpty { fallbackBidLevels() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
        display.take(6).forEach { level ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(level.label.take(2), color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, modifier = Modifier.width(20.dp), maxLines = 1)
                Text(level.price, color = quoteColor(!level.isAsk), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(level.volume, color = Color.White.copy(alpha = 0.68f), fontSize = 8.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TradeMiniRows(ticks: List<StockTradeTick>, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
        ticks.take(6).ifEmpty { fallbackTicks() }.forEach { tick ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tick.time.takeLast(5), color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, modifier = Modifier.width(30.dp), maxLines = 1)
                Text(tick.price, color = quoteColor(tick.isBuy), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
                Text(tick.volume, color = Color.White.copy(alpha = 0.65f), fontSize = 8.sp, textAlign = TextAlign.End, modifier = Modifier.width(34.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DetailActionBar(appState: AssistantUiState, ui: StockMarketUiState, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("加自选", "预警", "诊股", "买入", "卖出").forEach { action ->
            StockButton(
                appState = appState,
                text = if (action == "加自选" && ui.isWatched) "已自选" else action,
                modifier = Modifier.weight(1f).height(40.dp),
                onClick = { onAction(if (action == "买入" || action == "卖出") "交易" else action) },
                active = ui.activeAction == action || (action == "买入" && ui.activeAction == "交易")
            )
        }
    }
}

@Composable
private fun DetailDecisionSection(appState: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailInfoTabs.forEachIndexed { index, tab -> StockButton(appState, tab, Modifier.weight(1f).height(34.dp), onClick = {}, active = index == 0) }
        }
        SectionDivider()
        AiSummarySection(stock, onOpenAssistant)
        SectionDivider()
        MoneyFlowSection(stock)
        SectionDivider()
        FundamentalsSection(stock)
        SectionDivider()
        NewsSignalSection(stock)
    }
}

@Composable
private fun MoneyFlowSection(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("资金流向", "主力、大单、中单与小单结构")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("主力净流入", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
            MetricTile("超大单", stock.moneyFlow.superLargeOrder, flowColor(stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
            MetricTile("大单", stock.moneyFlow.largeOrder, flowColor(stock.moneyFlow.largeOrder), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("中单", stock.moneyFlow.mediumOrder, flowColor(stock.moneyFlow.mediumOrder), Modifier.weight(1f))
            MetricTile("小单", stock.moneyFlow.smallOrder, flowColor(stock.moneyFlow.smallOrder), Modifier.weight(1f))
            MetricTile("资金温度", if (stock.moneyFlow.mainInflow.contains("-")) "偏弱" else "偏强", if (stock.moneyFlow.mainInflow.contains("-")) FallGreen else RiseRed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FundamentalsSection(stock: StockDetailUiState) {
    val metrics = stock.fundamentals.ifEmpty {
        listOf(
            StockMetric("流通市值", stock.quote.floatMarketValue),
            StockMetric("市净率", stock.quote.pb),
            StockMetric("市盈率", stock.quote.peTtm),
            StockMetric("成交额", stock.quote.amount)
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Section("基本面", "估值、公告和财务摘要")
        metrics.take(6).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric -> MetricTile(metric.label, metric.value, toneColor(metric.tone), Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IndexStripSection(indices: List<StockIndexSnapshot>) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        indices.take(3).ifEmpty { listOf(StockIndexSnapshot("上证", "--", "--", true), StockIndexSnapshot("深成", "--", "--", true), StockIndexSnapshot("创业板", "--", "--", false)) }.forEach { item ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(item.value, color = Color.White.copy(alpha = 0.90f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeToolGrid(appState: AssistantUiState, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeQuickActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action -> StockButton(appState, action, Modifier.weight(1f).height(42.dp), { onSelect(action) }, active = selected == action) }
            }
        }
    }
}

@Composable
private fun HomeToolContent(stock: StockDetailUiState, selected: String, onOpenCode: (String) -> Unit) {
    when (selected) {
        "自选" -> WatchListSection(stock, onOpenCode)
        "新闻" -> NewsSignalSection(stock)
        "预警" -> SectionText("预警中心", "价格提醒、异动提醒和自选股风险提示会集中在这里。")
        "研报" -> SectionText("研报速览", "后续接入研报摘要、机构观点和目标价变化。")
        else -> {
            val board = stock.marketBoards.firstOrNull { it.title.contains(selected) } ?: stock.marketBoards.firstOrNull()
            if (board != null) MarketBoardSection(board, onOpenCode) else SectionText(selected, "真实行情接入后，这里会展示对应模块数据。")
        }
    }
}

@Composable
private fun WatchListSection(stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("自选股", stock.dataSourceLabel)
        stock.watchlist.take(4).forEach { item ->
            Row(Modifier.fillMaxWidth().height(40.dp).clickable { onOpenCode(item.code) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(item.code, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(item.price, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun MarketBoardSection(board: StockMarketBoard, onOpenCode: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("实时热点", board.subtitle.ifBlank { board.title })
        board.items.take(5).forEachIndexed { index, item -> RankRow(index + 1, item, onOpenCode) }
    }
}

@Composable
private fun NewsSignalSection(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Section("新闻与公告", "市场快讯、自选相关新闻和公告入口")
        marketNewsLines(stock).take(3).forEachIndexed { index, text ->
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((index + 1).toString(), color = Aqua.copy(alpha = 0.82f), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
                Text(text, color = Color.White.copy(alpha = 0.74f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, item: StockRankItem, onOpenCode: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(38.dp).clickable { onOpenCode(item.code) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(rank.toString(), color = Aqua.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(item.code, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(item.value, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun AiSummarySection(stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Section("AI 看盘摘要", "点击回到助手继续追问")
        Text(stock.aiSummary, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionText(title: String, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Section(title)
        Text(text, color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.height(46.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value.ifBlank { "--" }, color = color.copy(alpha = 0.94f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactMetricCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.height(34.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value.ifBlank { "--" }, color = color.copy(alpha = 0.94f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StockButton(appState: AssistantUiState, text: String, modifier: Modifier, onClick: () -> Unit, active: Boolean = false) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    val fillAlpha = (if (active) 0.20f else 0.075f) * intensity
    val textAlpha = if (active) 0.98f else 0.76f
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = textAlpha), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StockIconButton(appState: AssistantUiState, text: String, onClick: () -> Unit) {
    StockButton(appState, text, Modifier.width(46.dp).height(42.dp), onClick)
}

@Composable
private fun StockMainChartCanvas(stock: StockDetailUiState, selectedTab: String, modifier: Modifier) {
    val isTimeShare = selectedTab == "分时" || selectedTab == "五日"
    if (isTimeShare || stock.kLinePoints.size < 2) TimeShareChartCanvas(stock, modifier) else KLineCandleChartCanvas(stock, modifier)
}

@Composable
private fun TimeShareChartCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val minuteValues = stock.minutePoints.map { it.price }
    val averageValues = stock.minutePoints.map { it.average }
    val displayValues = minuteValues.ifEmpty { listOf(stock.quote.previousClose) }
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartHeight = height * 0.80f
        val volumeTop = chartHeight + 8.dp.toPx()
        val volumeHeight = (height - volumeTop).coerceAtLeast(1f)
        repeat(4) { index ->
            val y = chartHeight * (index + 1) / 5f
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }
        repeat(3) { index ->
            val x = width * (index + 1) / 4f
            drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, chartHeight), strokeWidth = 1.dp.toPx())
        }
        if (displayValues.size < 2) return@Canvas
        val minValue = (displayValues + averageValues).minOrNull() ?: return@Canvas
        val maxValue = (displayValues + averageValues).maxOrNull() ?: return@Canvas
        val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
        fun yFor(value: Float): Float = chartHeight - ((value - minValue) / range) * chartHeight
        fun buildPath(series: List<Float>): Path {
            val path = Path()
            val stepX = width / series.lastIndex.coerceAtLeast(1)
            series.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }
        val maxVolume = stock.minutePoints.maxOfOrNull { it.volumeRatio }?.takeIf { it > 0f } ?: 1f
        val barWidth = (width / stock.minutePoints.size.coerceAtLeast(1) * 0.56f).coerceIn(1.dp.toPx(), 5.dp.toPx())
        stock.minutePoints.forEachIndexed { index, point ->
            val stepX = width / stock.minutePoints.size.coerceAtLeast(1)
            val x = index * stepX + stepX / 2f
            val top = volumeTop + volumeHeight * (1f - (point.volumeRatio / maxVolume).coerceIn(0f, 1f))
            drawLine(Color.White.copy(alpha = 0.16f), Offset(x, height), Offset(x, top), strokeWidth = barWidth, cap = StrokeCap.Butt)
        }
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, chartHeight / 2f), Offset(width, chartHeight / 2f), strokeWidth = 1.dp.toPx())
        if (averageValues.size > 1) drawPath(buildPath(averageValues), color = AvgYellow.copy(alpha = 0.88f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(buildPath(displayValues), color = quoteColor(stock.quote.isRising), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun KLineCandleChartCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val candles = stock.kLinePoints.takeLast(72)
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartHeight = height * 0.78f
        val volumeTop = chartHeight + 8.dp.toPx()
        val volumeHeight = (height - volumeTop).coerceAtLeast(1f)
        repeat(4) { index ->
            val y = chartHeight * (index + 1) / 5f
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }
        repeat(3) { index ->
            val x = width * (index + 1) / 4f
            drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, chartHeight), strokeWidth = 1.dp.toPx())
        }
        if (candles.size < 2) return@Canvas
        val minValue = candles.minOfOrNull { it.low } ?: return@Canvas
        val maxValue = candles.maxOfOrNull { it.high } ?: return@Canvas
        val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
        val stepX = width / candles.size.coerceAtLeast(1)
        val bodyWidth = (stepX * 0.56f).coerceIn(2.5.dp.toPx(), 9.dp.toPx())
        val maxVolume = candles.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        fun xFor(index: Int): Float = index * stepX + stepX / 2f
        fun yFor(value: Float): Float = chartHeight - ((value - minValue) / range) * chartHeight
        candles.forEachIndexed { index, candle ->
            val x = xFor(index)
            val rising = candle.close >= candle.open
            val color = if (rising) RiseRed else FallGreen
            val highY = yFor(candle.high)
            val lowY = yFor(candle.low)
            val openY = yFor(candle.open)
            val closeY = yFor(candle.close)
            val bodyTop = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)
            drawLine(color.copy(alpha = 0.88f), Offset(x, highY), Offset(x, lowY), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Butt)
            if (bodyBottom - bodyTop < 1.2.dp.toPx()) {
                drawLine(color, Offset(x - bodyWidth / 2f, closeY), Offset(x + bodyWidth / 2f, closeY), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Butt)
            } else {
                drawLine(color, Offset(x, bodyTop), Offset(x, bodyBottom), strokeWidth = bodyWidth, cap = StrokeCap.Butt)
            }
            val volumeTopY = volumeTop + volumeHeight * (1f - (candle.volume / maxVolume).coerceIn(0f, 1f))
            drawLine(color.copy(alpha = 0.36f), Offset(x, height), Offset(x, volumeTopY), strokeWidth = (bodyWidth * 0.9f), cap = StrokeCap.Butt)
        }
        fun movingAveragePath(window: Int): Path? {
            if (candles.size < window) return null
            val path = Path()
            var hasPoint = false
            candles.forEachIndexed { index, _ ->
                if (index >= window - 1) {
                    val avg = candles.subList(index - window + 1, index + 1).map { it.close }.average().toFloat()
                    val x = xFor(index)
                    val y = yFor(avg)
                    if (!hasPoint) {
                        path.moveTo(x, y)
                        hasPoint = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }
            return if (hasPoint) path else null
        }
        movingAveragePath(5)?.let { drawPath(it, color = AvgYellow.copy(alpha = 0.86f), style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)) }
        movingAveragePath(10)?.let { drawPath(it, color = MaBlue.copy(alpha = 0.72f), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)) }
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, volumeTop), Offset(width, volumeTop), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun MiniTrendCanvas(stock: StockDetailUiState, modifier: Modifier) {
    TimeShareChartCanvas(stock, modifier)
}

private fun quoteColor(isRising: Boolean): Color = if (isRising) RiseRed else FallGreen
private fun toneColor(tone: StockTone): Color = when (tone) { StockTone.Rising -> RiseRed; StockTone.Falling -> FallGreen; StockTone.Neutral -> Color.White }
private fun flowColor(text: String): Color = if (text.contains("-")) FallGreen else RiseRed
private fun statusText(ui: StockMarketUiState): String = when {
    ui.loading -> "正在连接市场行情代理"
    ui.marketLoading -> "正在同步指数、自选与榜单"
    ui.requestMessage != null -> ui.requestMessage
    else -> "指数 · 热点 · 新闻 · 自选股"
}
private fun statusColor(ui: StockMarketUiState): Color = if (ui.requestMessage != null) Color(0xFFFFC857) else Color.White.copy(alpha = 0.58f)
private fun risingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { it.isRising }
private fun fallingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { !it.isRising }
private fun hotPoolCount(stock: StockDetailUiState): Int = stock.marketBoards.sumOf { it.items.size }
private fun averageLineLabel(stock: StockDetailUiState): String = stock.minutePoints.lastOrNull()?.average?.let { String.format("%.2f", it) } ?: "--"
private fun normalizeSellLevels(levels: List<StockOrderLevel>): List<StockOrderLevel> {
    val source = levels.ifEmpty { fallbackAskLevels() }.take(10)
    return if (source.firstOrNull()?.label?.contains("卖1") == true) source.reversed() else source
}
private fun marketNewsLines(stock: StockDetailUiState): List<String> {
    val hotNames = stock.marketBoards.flatMap { it.items }.take(3).joinToString("、") { it.name }.ifBlank { "暂无热点榜单" }
    val watchNames = stock.watchlist.take(3).joinToString("、") { it.name }.ifBlank { "暂无自选股" }
    return listOf(
        "实时热点：$hotNames 维持高关注度，可进入热榜继续跟踪。",
        "自选动态：$watchNames 的新闻、公告和异动将聚合到这里。",
        "市场新闻：后续接入宏观、行业、公告和研报摘要，支持一键问 AI。"
    )
}
private fun fallbackAskLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("卖5", "--", "--", true), StockOrderLevel("卖4", "--", "--", true), StockOrderLevel("卖3", "--", "--", true), StockOrderLevel("卖2", "--", "--", true), StockOrderLevel("卖1", "--", "--", true)
)
private fun fallbackBidLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("买1", "--", "--", false), StockOrderLevel("买2", "--", "--", false), StockOrderLevel("买3", "--", "--", false), StockOrderLevel("买4", "--", "--", false), StockOrderLevel("买5", "--", "--", false)
)
private fun fallbackTicks(): List<StockTradeTick> = listOf(
    StockTradeTick("--:--", "--", "--", "等待", true), StockTradeTick("--:--", "--", "--", "等待", false), StockTradeTick("--:--", "--", "--", "等待", true)
)
