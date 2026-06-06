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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val DepthTabs = listOf("五档", "成交")
private val HomeQuickActions = listOf("热榜", "板块", "资金", "异动", "龙虎", "日历", "研报", "预警")
private val RiseRed = Color(0xFFFF8F8F)
private val FallGreen = Color(0xFF80F7B4)
private val Aqua = Color(0xFF8DF9EA)
private val ParentPanelBg = Color.White.copy(alpha = 0.105f)
private val ParentPanelStroke = Color.White.copy(alpha = 0.085f)
private val ButtonBg = Color.White.copy(alpha = 0.085f)
private val ButtonBgActive = Color.White.copy(alpha = 0.16f)

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
            ui = ui,
            onBack = viewModel::backToHome,
            onRefresh = viewModel::refreshCurrent,
            onSelectTab = viewModel::selectTab,
            onSelectDepth = viewModel::selectDepthTab,
            onAction = viewModel::handleAction,
            onOpenAssistant = onOpenAssistant
        )
    } else {
        StockHomePage(
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
        item { HomeHeader(ui, onBack, onRefresh) }
        item {
            StockParentPanel {
                TopSearchBar(ui, onQueryChange, onSearch)
                SectionDivider()
                MarketOverviewSection(ui.stock, ui.loading, onOpenDetail)
                SectionDivider()
                IndexStripSection(ui.stock.indices)
                SectionDivider()
                HomeToolGrid(ui.selectedHomeAction, onSelectHomeAction)
                SectionDivider()
                HomeToolContent(ui.stock, ui.selectedHomeAction, onOpenCode)
                SectionDivider()
                WatchListSection(ui.stock, onOpenCode)
                SectionDivider()
                AiSummarySection(ui.stock, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun StockDetailPage(
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onSelectDepth: (String) -> Unit,
    onAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DetailTopBar(ui, onBack, onRefresh) }
        item {
            StockParentPanel {
                QuoteHeroSection(ui.stock)
                SectionDivider()
                BottomActionBar(ui, onAction)
                SectionDivider()
                ChartSection(ui, onSelectTab)
                SectionDivider()
                DepthAndTradeSection(ui, onSelectDepth)
                if (ui.activeAction != null) {
                    SectionDivider()
                    ActionSection(ui.activeAction, ui.stock, onOpenAssistant)
                }
                SectionDivider()
                FundamentalsSection(ui.stock)
                SectionDivider()
                AiSummarySection(ui.stock, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun HomeHeader(ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StockButton("‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
            Spacer(Modifier.weight(1f))
            Text("A股行情 · ${ui.stock.quote.code}", color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StockIconButton(if (ui.loading || ui.marketLoading) "…" else "⟳", onRefresh)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE MARKET", color = Aqua.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("A股行情首页", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(statusText(ui), color = statusColor(ui), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailTopBar(ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StockButton("‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
        Spacer(Modifier.weight(1f))
        Text("${ui.stock.quote.name} · ${ui.stock.quote.code}", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        StockIconButton(if (ui.loading || ui.kLineLoading) "…" else "⟳", onRefresh)
    }
}

@Composable
private fun StockParentPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(ParentPanelBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ParentPanelStroke)
    )
}

@Composable
private fun TopSearchBar(ui: StockMarketUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
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
                    if (ui.query.isBlank()) Text("搜索代码 / 名称 / 拼音", color = Color.White.copy(alpha = 0.38f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    inner()
                }
            }
        )
        StockButton(if (ui.loading) "连接" else "搜索", Modifier.width(70.dp).height(38.dp), onSearch, active = true)
    }
}

@Composable
private fun MarketOverviewSection(stock: StockDetailUiState, loading: Boolean, onOpenDetail: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clickable(onClick = onOpenDetail),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (loading) "行情雷达连接中" else "今日关注", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(stock.quote.name, color = Color.White, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stock.quote.code} · ${stock.quote.market} · ${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
                Text("${stock.quote.changeAmount}  ${stock.quote.changePercent}", color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
        MiniTrendCanvas(stock, Modifier.fillMaxWidth().height(62.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("成交额", stock.quote.amount, Color.White, Modifier.weight(1f))
            MetricTile("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            MetricTile("量比", stock.quote.volumeRatio, quoteColor(stock.quote.isRising), Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuoteHeroSection(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth().height(138.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stock.quote.name.ifBlank { "个股详情" }, color = Color.White, fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stock.quote.code} · ${stock.quote.market} · ${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stock.quote.price.ifBlank { "--" }, color = quoteColor(stock.quote.isRising), fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Black)
                Text("${stock.quote.changeAmount}  ${stock.quote.changePercent}", color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("今开", stock.quote.open, Color.White, Modifier.weight(1f))
            MetricTile("最高", stock.quote.high, RiseRed, Modifier.weight(1f))
            MetricTile("最低", stock.quote.low, FallGreen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChartSection(ui: StockMarketUiState, onSelectTab: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().height(238.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StockTabs.forEach { tab -> StockButton(tab, Modifier.weight(1f).height(36.dp), { onSelectTab(tab) }, active = ui.selectedTab == tab) }
        }
        MiniTrendCanvas(ui.stock, Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("昨收", ui.stock.quote.previousClose.toString(), Color.White, Modifier.weight(1f))
            MetricTile("市盈", ui.stock.quote.peTtm, Color.White, Modifier.weight(1f))
            MetricTile("人气", ui.stock.quote.popularityRank, Aqua, Modifier.weight(1f))
        }
        ui.requestMessage?.let { Text(it, color = Color(0xFFFFC857).copy(alpha = 0.82f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun DepthAndTradeSection(ui: StockMarketUiState, onSelectDepth: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().height(176.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DepthTabs.forEach { tab -> StockButton(tab, Modifier.weight(1f).height(36.dp), { onSelectDepth(tab) }, active = ui.depthTab == tab) }
        }
        if (ui.depthTab == "成交") TradeTickList(ui.stock.tradeTicks) else OrderBookList(ui.stock.sellLevels, ui.stock.buyLevels)
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
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric -> MetricTile(metric.label, metric.value, toneColor(metric.tone), Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BottomActionBar(ui: StockMarketUiState, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("加自选", "预警", "研报", "交易").forEach { action ->
            StockButton(
                text = if (action == "加自选" && ui.isWatched) "已自选" else action,
                modifier = Modifier.weight(1f).height(44.dp),
                onClick = { onAction(action) },
                active = ui.activeAction == action
            )
        }
    }
}

@Composable
private fun OrderBookList(sell: List<StockOrderLevel>, buy: List<StockOrderLevel>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { sell.takeLast(5).reversed().ifEmpty { fallbackAskLevels() }.forEach { OrderLevelRow(it) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { buy.take(5).ifEmpty { fallbackBidLevels() }.forEach { OrderLevelRow(it) } }
    }
}

@Composable
private fun OrderLevelRow(level: StockOrderLevel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(level.label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, modifier = Modifier.width(34.dp))
        Text(level.price, color = quoteColor(!level.isAsk), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
        Text(level.volume, color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.width(42.dp), maxLines = 1)
    }
}

@Composable
private fun TradeTickList(ticks: List<StockTradeTick>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ticks.take(6).ifEmpty { fallbackTicks() }.forEach { tick ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tick.time, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, modifier = Modifier.width(42.dp))
                Text(tick.price, color = quoteColor(tick.isBuy), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(tick.volume, color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                Text(tick.direction, color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
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
private fun HomeToolGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("行情工具", "热榜、板块、资金和异动入口")
        HomeQuickActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action -> StockButton(action, Modifier.weight(1f).height(42.dp), { onSelect(action) }, active = selected == action) }
            }
        }
    }
}

@Composable
private fun HomeToolContent(stock: StockDetailUiState, selected: String, onOpenCode: (String) -> Unit) {
    val board = stock.marketBoards.firstOrNull { it.title.contains(selected) } ?: stock.marketBoards.firstOrNull()
    if (board != null) MarketBoardSection(board, onOpenCode) else SectionText(selected, "真实行情接入后，这里会展示对应模块数据。")
}

@Composable
private fun WatchListSection(stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("自选观察", stock.dataSourceLabel)
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
        Section(board.title, board.subtitle)
        board.items.take(5).forEachIndexed { index, item -> RankRow(index + 1, item, onOpenCode) }
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
        Text(stock.aiSummary, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionSection(action: String?, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Section(action ?: "动作", stock.quote.name)
        Text("已切换到 ${action ?: "当前"} 操作。真实交易、预警和研报入口会在后续版本接入确认流程。", color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 17.sp)
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
private fun StockButton(text: String, modifier: Modifier, onClick: () -> Unit, active: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(if (active) ButtonBgActive else ButtonBg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = if (active) 0.96f else 0.78f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StockIconButton(text: String, onClick: () -> Unit) {
    StockButton(text, Modifier.width(46.dp).height(42.dp), onClick)
}

@Composable
private fun MiniTrendCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val values = stock.minutePoints.map { it.price }.ifEmpty { stock.kLinePoints.map { it.close } }.ifEmpty { listOf(stock.quote.previousClose) }
    val rising = stock.quote.isRising
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
        val stepX = size.width / values.lastIndex.coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
        drawPath(path, color = quoteColor(rising), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun quoteColor(isRising: Boolean): Color = if (isRising) RiseRed else FallGreen
private fun toneColor(tone: StockTone): Color = when (tone) { StockTone.Rising -> RiseRed; StockTone.Falling -> FallGreen; StockTone.Neutral -> Color.White }
private fun statusText(ui: StockMarketUiState): String = when {
    ui.loading -> "正在连接个股行情代理"
    ui.marketLoading -> "正在同步指数、自选与榜单"
    ui.requestMessage != null -> ui.requestMessage
    else -> "${ui.stock.quote.name} · ${ui.stock.quote.code} · ${ui.stock.dataSourceLabel}"
}
private fun statusColor(ui: StockMarketUiState): Color = if (ui.requestMessage != null) Color(0xFFFFC857) else Color.White.copy(alpha = 0.58f)
private fun fallbackAskLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("卖5", "--", "--", true), StockOrderLevel("卖4", "--", "--", true), StockOrderLevel("卖3", "--", "--", true), StockOrderLevel("卖2", "--", "--", true), StockOrderLevel("卖1", "--", "--", true)
)
private fun fallbackBidLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("买1", "--", "--", false), StockOrderLevel("买2", "--", "--", false), StockOrderLevel("买3", "--", "--", false), StockOrderLevel("买4", "--", "--", false), StockOrderLevel("买5", "--", "--", false)
)
private fun fallbackTicks(): List<StockTradeTick> = listOf(
    StockTradeTick("--:--", "--", "--", "等待", true), StockTradeTick("--:--", "--", "--", "等待", false), StockTradeTick("--:--", "--", "--", "等待", true)
)
