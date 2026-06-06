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
import kotlin.math.max

private val StockTabs = listOf("分时", "日K", "周K", "月K", "五日")
private val DepthTabs = listOf("五档", "成交")
private val HomeQuickActions = listOf("热榜", "板块", "资金", "异动", "龙虎", "日历", "研报", "预警")
private val RiseRed = Color(0xFFFF8F8F)
private val FallGreen = Color(0xFF80F7B4)
private val Aqua = Color(0xFF8DF9EA)

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
        item { HomeTopSearchBar(appState, ui, onQueryChange, onSearch) }
        item { MarketPulseCard(appState, ui, onOpenDetail) }
        item { IndexStrip(appState, ui.stock.indices) }
        item { MarketBreadthCard(appState, ui.stock) }
        item { SearchCard(appState, ui, onQueryChange, onSearch) }
        item { OpportunityDashboard(appState, ui.stock, onOpenDetail, onOpenAssistant) }
        item { HomeToolGrid(appState, ui.selectedHomeAction, onSelectHomeAction) }
        item { HomeToolContent(appState, ui.stock, ui.selectedHomeAction, onOpenCode) }
        item { WatchListCard(appState, ui.stock, onOpenCode) }
        item { MarketBoardsCard(appState, ui.stock, onOpenCode) }
        item { AiSummaryCard(appState, ui.stock, onOpenAssistant) }
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
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { DetailTopBar(appState, ui, onBack, onRefresh) }
        item { QuoteHeroCard(appState, ui.stock) }
        item { ChartCard(appState, ui, onSelectTab) }
        item { DepthAndTradeCard(appState, ui, onSelectDepth) }
        if (ui.activeAction != null) item { ActionPanel(appState, ui.activeAction, ui.stock, onOpenAssistant) }
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
            Text("A-SHARE MARKET", color = Aqua.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("A股行情首页", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(statusText(ui), color = statusColor(ui), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeTopSearchBar(appState: AssistantUiState, ui: StockMarketUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.92f, appState.motionIntensity, 999, Modifier.fillMaxWidth().height(56.dp), GlassRole.Card) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            PressableGlass(appState.quality, appState.glassIntensity, appState.motionIntensity, 999, Modifier.width(70.dp).height(38.dp), GlassRole.Floating, onClick = onSearch) {
                CenterText(if (ui.loading) "连接" else "搜索", 13, Color.White)
            }
        }
    }
}

@Composable
private fun MarketPulseCard(appState: AssistantUiState, ui: StockMarketUiState, onOpenDetail: () -> Unit) {
    val stock = ui.stock
    PressableGlass(appState.quality, appState.glassIntensity, appState.motionIntensity, 30, Modifier.fillMaxWidth().height(218.dp), GlassRole.Card, onClick = onOpenDetail) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (ui.loading) "行情雷达连接中" else "市场温度", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(marketMoodText(stock), color = Color.White, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(stock.aiSummary, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun MarketBreadthCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("盘面宽度", "用热榜与板块数据估算市场强弱分布")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("上涨观察", "${risingCount(stock)} 个", RiseRed, Modifier.weight(1f))
                InfoChip("下跌/谨慎", "${fallingCount(stock)} 个", FallGreen, Modifier.weight(1f))
                InfoChip("热度池", "${stock.marketBoards.sumOf { it.items.size }} 条", Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchCard(appState: AssistantUiState, ui: StockMarketUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("搜索个股", "输入代码或名称，首屏快速加载，K线按需补全")
            HomeTopSearchBar(appState, ui, onQueryChange, onSearch)
            ui.requestMessage?.let { Text(it, color = Color(0xFFFFC857).copy(alpha = 0.80f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun OpportunityDashboard(appState: AssistantUiState, stock: StockDetailUiState, onOpenDetail: () -> Unit, onOpenAssistant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 24, Modifier.weight(1.08f).height(142.dp), GlassRole.Card, onClick = onOpenDetail) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SmallSection("核心机会", "当前关注标的")
                Text(stock.quote.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        PressableGlass(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.weight(1f).height(142.dp), GlassRole.Card, onClick = onOpenAssistant) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SmallSection("AI 复盘", "一键解释")
                Text("结合资金、盘口、公告生成看盘摘要", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, lineHeight = 17.sp)
                Text("问 AI", color = Aqua, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun HomeToolGrid(appState: AssistantUiState, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("行情工具", "热榜、板块、资金和异动入口")
        HomeQuickActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    PressableGlass(appState.quality, appState.glassIntensity * if (selected == action) 1.02f else 0.86f, appState.motionIntensity, 18, Modifier.weight(1f).height(48.dp), if (selected == action) GlassRole.Floating else GlassRole.Chip, onClick = { onSelect(action) }) {
                        CenterText(action, 12, if (selected == action) Color.White else Color.White.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeToolContent(appState: AssistantUiState, stock: StockDetailUiState, selected: String, onOpenCode: (String) -> Unit) {
    val board = stock.marketBoards.firstOrNull { it.title.contains(selected) } ?: stock.marketBoards.firstOrNull()
    if (board != null) MarketBoardPanel(appState, board, onOpenCode) else SectionCard(appState, selected, "真实行情接入后，这里会展示对应模块数据。")
}

@Composable
private fun WatchListCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("自选观察", stock.dataSourceLabel)
            stock.watchlist.take(4).forEach { item ->
                PressableGlass(appState.quality, appState.glassIntensity * 0.70f, appState.motionIntensity, 18, Modifier.fillMaxWidth().height(48.dp), GlassRole.Chip, onClick = { onOpenCode(item.code) }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(item.code, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(item.price, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketBoardsCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        stock.marketBoards.take(3).forEach { board -> MarketBoardPanel(appState, board, onOpenCode) }
    }
}

@Composable
private fun MarketBoardPanel(appState: AssistantUiState, board: StockMarketBoard, onOpenCode: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section(board.title, board.subtitle)
            board.items.take(5).forEachIndexed { index, item -> RankRow(appState, index + 1, item, onOpenCode) }
        }
    }
}

@Composable
private fun RankRow(appState: AssistantUiState, rank: Int, item: StockRankItem, onOpenCode: (String) -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.68f, appState.motionIntensity, 16, Modifier.fillMaxWidth().height(44.dp), GlassRole.Chip, onClick = { onOpenCode(item.code) }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(rank.toString(), color = Aqua.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(item.code, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(item.value, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun AiSummaryCard(appState: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Section("AI 看盘摘要", "点击回到助手继续追问")
            Text(stock.aiSummary, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailTopBar(appState: AssistantUiState, ui: StockMarketUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PressableGlass(appState.quality, appState.glassIntensity * 0.88f, appState.motionIntensity, 999, Modifier.width(82.dp).height(38.dp), GlassRole.Chip, onClick = onBack) { CenterText("‹ 首页", 13, Color.White.copy(alpha = 0.82f)) }
        Box(Modifier.weight(1f))
        Text(if (ui.loading || ui.kLineLoading) "加载中" else ui.stock.dataSourceLabel, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        PressableGlass(appState.quality, appState.glassIntensity * 0.70f, appState.motionIntensity, 999, Modifier.width(44.dp).height(38.dp), GlassRole.Chip, onClick = onRefresh) { CenterText("⟳", 17, Color.White.copy(alpha = 0.82f)) }
    }
}

@Composable
private fun QuoteHeroCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity, appState.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stock.quote.name, color = Color.White, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${stock.quote.code} · ${stock.quote.market}", color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
                    Text("${stock.quote.changeAmount}  ${stock.quote.changePercent}", color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("今开", stock.quote.open, Color.White, Modifier.weight(1f))
                MetricTile("最高", stock.quote.high, RiseRed, Modifier.weight(1f))
                MetricTile("最低", stock.quote.low, FallGreen, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartCard(appState: AssistantUiState, ui: StockMarketUiState, onSelectTab: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.90f, appState.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StockTabs.forEach { tab ->
                    PressableGlass(appState.quality, appState.glassIntensity * if (ui.selectedTab == tab) 1.0f else 0.72f, appState.motionIntensity, 999, Modifier.weight(1f).height(36.dp), if (ui.selectedTab == tab) GlassRole.Floating else GlassRole.Chip, onClick = { onSelectTab(tab) }) {
                        CenterText(tab, 11, if (ui.selectedTab == tab) Color.White else Color.White.copy(alpha = 0.60f))
                    }
                }
            }
            MiniTrendCanvas(ui.stock, Modifier.fillMaxWidth().height(166.dp))
            ui.requestMessage?.let { Text(it, color = Color(0xFFFFC857).copy(alpha = 0.78f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DepthAndTradeCard(appState: AssistantUiState, ui: StockMarketUiState, onSelectDepth: (String) -> Unit) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DepthTabs.forEach { tab ->
                    PressableGlass(appState.quality, appState.glassIntensity * if (ui.depthTab == tab) 0.96f else 0.72f, appState.motionIntensity, 999, Modifier.weight(1f).height(36.dp), if (ui.depthTab == tab) GlassRole.Floating else GlassRole.Chip, onClick = { onSelectDepth(tab) }) { CenterText(tab, 12, Color.White.copy(alpha = if (ui.depthTab == tab) 0.92f else 0.58f)) }
                }
            }
            if (ui.depthTab == "成交") TradeTickList(ui.stock.tradeTicks) else OrderBookList(ui.stock.sellLevels, ui.stock.buyLevels)
        }
    }
}

@Composable
private fun OrderBookList(sell: List<StockOrderLevel>, buy: List<StockOrderLevel>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { sell.takeLast(5).reversed().forEach { OrderLevelRow(it) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { buy.take(5).forEach { OrderLevelRow(it) } }
    }
}

@Composable
private fun OrderLevelRow(level: StockOrderLevel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(level.label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, modifier = Modifier.width(34.dp))
        Text(level.price, color = quoteColor(!level.isAsk), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(level.volume, color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.width(42.dp))
    }
}

@Composable
private fun TradeTickList(ticks: List<StockTradeTick>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ticks.take(6).forEach { tick ->
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
private fun ActionPanel(appState: AssistantUiState, action: String?, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Section(action ?: "动作", stock.quote.name)
            Text("已切换到 ${action ?: "当前"} 操作。真实交易、预警和研报入口会在后续版本接入确认流程。", color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun FundamentalsCard(appState: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.86f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("基本面", "估值、公告和财务摘要")
            stock.fundamentals.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { metric -> MetricTile(metric.label, metric.value, toneColor(metric.tone), Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(appState: AssistantUiState, ui: StockMarketUiState, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("加自选", "预警", "研报", "交易").forEach { action ->
            PressableGlass(appState.quality, appState.glassIntensity * 0.92f, appState.motionIntensity, 999, Modifier.weight(1f).height(46.dp), if (ui.activeAction == action) GlassRole.Floating else GlassRole.Chip, onClick = { onAction(action) }) {
                CenterText(if (action == "加自选" && ui.isWatched) "已自选" else action, 12, Color.White.copy(alpha = 0.88f))
            }
        }
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
private fun SmallSection(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionCard(appState: AssistantUiState, title: String, text: String) {
    GlassPanel(appState.quality, appState.glassIntensity * 0.84f, appState.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Section(title)
            Text(text, color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassPanel(RenderQualityForLocal(), 0.72f, 0f, 16, modifier.height(52.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = color.copy(alpha = 0.94f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.94f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun CenterText(text: String, size: Int, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MiniTrendCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val values = stock.minutePoints.map { it.price }.ifEmpty { stock.kLinePoints.map { it.close } }
    val rising = stock.quote.isRising
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
        val stepX = size.width / (values.lastIndex.coerceAtLeast(1))
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
private fun marketMoodText(stock: StockDetailUiState): String = if (stock.quote.isRising) "偏强活跃" else "谨慎震荡"
private fun risingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { it.isRising }
private fun fallingCount(stock: StockDetailUiState): Int = stock.marketBoards.flatMap { it.items }.count { !it.isRising }
private fun statusText(ui: StockMarketUiState): String = when {
    ui.loading -> "正在连接个股行情代理"
    ui.marketLoading -> "正在同步指数、自选与榜单"
    ui.requestMessage != null -> ui.requestMessage
    else -> "${ui.stock.quote.name} · ${ui.stock.quote.code} · ${ui.stock.dataSourceLabel}"
}
private fun statusColor(ui: StockMarketUiState): Color = if (ui.requestMessage != null) Color(0xFFFFC857) else Color.White.copy(alpha = 0.58f)

@Composable
private fun RenderQualityForLocal() = androidx.compose.runtime.remember { com.yuchen.ailedger.model.RenderQuality.Balanced }
