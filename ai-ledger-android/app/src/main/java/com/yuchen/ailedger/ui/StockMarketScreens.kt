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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.yuchen.ailedger.data.StockRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockFeatureEntry
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.StockWatchItem
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StockTabs = listOf("分时", "日K", "周K", "月K", "五日")
private val DepthTabs = listOf("五档", "成交")
private val HomeQuickActions = listOf("热榜", "板块", "资金", "异动", "龙虎", "日历", "研报", "预警")

@Composable
fun AStockMarketScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val repository = remember { StockRepository() }
    val scope = rememberCoroutineScope()
    var stock by remember { mutableStateOf(sampleAStockDetailUiState()) }
    var query by remember { mutableStateOf(stock.quote.code) }
    var loading by remember { mutableStateOf(true) }
    var showDetail by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("分时") }
    var depthTab by remember { mutableStateOf("五档") }
    var isWatched by remember { mutableStateOf(false) }
    var activeAction by remember { mutableStateOf<String?>(null) }
    var selectedHomeAction by remember { mutableStateOf("热榜") }

    fun loadStock(input: String, openDetail: Boolean) {
        val target = input.trim().ifBlank { stock.quote.code }
        scope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) { repository.loadAStock(target) }
            stock = loaded
            query = loaded.quote.code
            loading = false
            if (openDetail) showDetail = true
        }
    }

    LaunchedEffect(Unit) {
        loadStock(query, openDetail = false)
    }

    if (showDetail) {
        AStockDetailScreen(
            state = state,
            stock = stock,
            loading = loading,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            depthTab = depthTab,
            onSelectDepthTab = { depthTab = it },
            isWatched = isWatched,
            activeAction = activeAction,
            onBack = {
                activeAction = null
                showDetail = false
            },
            onRefresh = { loadStock(query, openDetail = true) },
            onAction = { action ->
                when (action) {
                    "加自选" -> {
                        isWatched = !isWatched
                        activeAction = "加自选"
                    }
                    else -> activeAction = if (activeAction == action) null else action
                }
            },
            onCloseAction = { activeAction = null },
            onOpenAssistant = onOpenAssistant
        )
    } else {
        AStockMarketHomeScreen(
            state = state,
            stock = stock,
            query = query,
            loading = loading,
            selectedHomeAction = selectedHomeAction,
            onSelectHomeAction = { selectedHomeAction = it },
            onQueryChange = { query = it },
            onSearch = { loadStock(query, openDetail = true) },
            onBack = onBack,
            onOpenDetail = {
                if (loading || stockUsesFallbackSample(stock)) loadStock(query, openDetail = true) else showDetail = true
            },
            onOpenCode = { code -> loadStock(code, openDetail = true) },
            onRefresh = { loadStock(query, openDetail = false) }
        )
    }
}

@Composable
private fun AStockMarketHomeScreen(
    state: AssistantUiState,
    stock: StockDetailUiState,
    query: String,
    loading: Boolean,
    selectedHomeAction: String,
    onSelectHomeAction: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenCode: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { AStockHomeHeader(state, stock, loading, onBack, onRefresh) }
        item { AStockSearchPanel(state, query, loading, stock, onQueryChange, onSearch) }
        item { AStockMarketPulsePanel(state, stock, loading, onOpenDetail) }
        item { AStockIndexStrip(state, stock) }
        item { AStockHomeFocusGrid(state, stock, onOpenDetail) }
        item { AStockQuickActionGrid(state, selectedHomeAction, onSelectHomeAction) }
        item { AStockHomeActionContent(state, stock, selectedHomeAction, onOpenCode) }
        item { AStockWatchPanel(state, stock, onOpenCode) }
        item { AStockMarketBoards(state, stock) }
    }
}

@Composable
private fun AStockHomeHeader(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableGlass(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 999, Modifier.width(82.dp).height(38.dp), GlassRole.Chip, onClick = onBack) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("‹ 返回", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Box(Modifier.weight(1f))
            PressableGlass(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 999, Modifier.width(44.dp).height(38.dp), GlassRole.Chip, onClick = onRefresh) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (loading) "…" else "⟳", color = Color.White.copy(alpha = 0.82f), fontSize = 17.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE MARKET", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("A股行情首页", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(stockDataStatusText(stock, loading), color = dataStatusColor(stock, loading), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockMarketPulsePanel(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    onOpenDetail: () -> Unit
) {
    PressableGlass(state.quality, state.glassIntensity * 1.02f, state.motionIntensity, 30, Modifier.fillMaxWidth().height(228.dp), GlassRole.Card, onClick = onOpenDetail) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (loading) "行情雷达连接中" else "市场温度", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(marketMoodText(stock), color = Color.White, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("基于当前个股、资金流、换手与量比生成首屏判断", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                    Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
            MiniTrendCanvas(stock, Modifier.fillMaxWidth().height(58.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeMetricTile("今日关注", "${stock.quote.name} ${stock.quote.code}", quoteColor(stock.quote.isRising), Modifier.weight(1.35f))
                HomeMetricTile("成交额", stock.quote.amount, Color.White, Modifier.weight(1f))
                HomeMetricTile("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AStockIndexStrip(state: AssistantUiState, stock: StockDetailUiState) {
    val items = homeIndexItems(stock)
    GlassPanel(state.quality, state.glassIntensity * 0.88f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(78.dp), GlassRole.Card) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items.take(3).forEach { item ->
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
private fun AStockHomeFocusGrid(state: AssistantUiState, stock: StockDetailUiState, onOpenDetail: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 24, Modifier.weight(1.1f).height(138.dp), GlassRole.Card, onClick = onOpenDetail) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SectionSmall("核心个股", "点入详情")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stock.quote.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(stock.quote.code, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stock.quote.price, color = quoteColor(stock.quote.isRising), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(stock.quote.changePercent, color = quoteColor(stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeCompactCard(state, "资金流", stock.moneyFlow.mainInflow, "主力净流入", flowColor(stock.moneyFlow.mainInflow))
            HomeCompactCard(state, "盘口强度", "买一 ${stock.buyLevels.firstOrNull()?.price ?: "--"}", "卖一 ${stock.sellLevels.takeLast(5).lastOrNull()?.price ?: "--"}", Color.White)
        }
    }
}

@Composable
private fun HomeCompactCard(state: AssistantUiState, title: String, value: String, subtitle: String, valueColor: Color) {
    GlassPanel(state.quality, state.glassIntensity * 0.78f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(64.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockQuickActionGrid(state: AssistantUiState, selected: String, onSelect: (String) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity * 0.84f, state.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("市场工具", "热榜、板块、资金、异动和研报入口")
            HomeQuickActions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { label ->
                        val selectedItem = selected == label
                        PressableGlass(state.quality, state.glassIntensity * if (selectedItem) 0.98f else 0.58f, state.motionIntensity, 16, Modifier.weight(1f).height(46.dp), GlassRole.Chip, onClick = { onSelect(label) }) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(homeActionIcon(label), color = if (selectedItem) Color.White else Color.White.copy(alpha = 0.62f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                                Text(label, color = Color.White.copy(alpha = if (selectedItem) 0.90f else 0.56f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AStockHomeActionContent(
    state: AssistantUiState,
    stock: StockDetailUiState,
    selected: String,
    onOpenCode: (String) -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.88f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section(homeActionTitle(selected), homeActionSubtitle(selected))
            when (selected) {
                "热榜", "异动", "龙虎" -> HomeRankList(stock, onOpenCode)
                "板块" -> HomeSectorList(stock)
                "资金" -> HomeMoneyFlowBoard(stock)
                "日历" -> HomeCalendarBoard(stock)
                "研报" -> HomeResearchBoard(stock)
                "预警" -> HomeAlertBoard(stock)
            }
        }
    }
}

@Composable
private fun HomeRankList(stock: StockDetailUiState, onOpenCode: (String) -> Unit) {
    val ranks = homeRankItems(stock)
    ranks.take(4).forEachIndexed { index, item ->
        PressableGlass(sampleQuality(), 0.48f, 0.25f, 16, Modifier.fillMaxWidth().height(38.dp), GlassRole.Chip, onClick = { onOpenCode(item.code) }) {
            Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(item.name, color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(item.code, color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(item.value, color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeSectorList(stock: StockDetailUiState) {
    val sectors = listOf(
        "电力" to stock.quote.changePercent,
        "能源" to stock.quote.volumeRatio,
        "高股息" to stock.quote.turnoverRate,
        "央企改革" to stock.quote.amount
    )
    sectors.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item -> HomeInfoChip(item.first, item.second, quoteColor(!item.second.trim().startsWith("-")), Modifier.weight(1f)) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeMoneyFlowBoard(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeInfoChip("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
        HomeInfoChip("超大单", stock.moneyFlow.superLargeOrder, flowColor(stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeInfoChip("大单", stock.moneyFlow.largeOrder, flowColor(stock.moneyFlow.largeOrder), Modifier.weight(1f))
        HomeInfoChip("中小单", "${stock.moneyFlow.mediumOrder} / ${stock.moneyFlow.smallOrder}", Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun HomeCalendarBoard(stock: StockDetailUiState) {
    HomeInfoChip("今日事项", "交易中 · ${stock.quote.market}", Color.White, Modifier.fillMaxWidth())
    HomeInfoChip("风险提示", "公告、财报与停复牌后续接入", Color(0xFFFFC857), Modifier.fillMaxWidth())
}

@Composable
private fun HomeResearchBoard(stock: StockDetailUiState) {
    Text("AI 研报摘要会基于行情、资金流、公告和新闻生成。当前已接入行情上下文：${stock.quote.name} ${stock.quote.changePercent}，成交额 ${stock.quote.amount}。", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun HomeAlertBoard(stock: StockDetailUiState) {
    HomeInfoChip("价格预警", "突破 ${stock.quote.high} / 跌破 ${stock.quote.low}", Color.White, Modifier.fillMaxWidth())
    HomeInfoChip("量能预警", "量比 ${stock.quote.volumeRatio} · 换手 ${stock.quote.turnoverRate}", Color.White, Modifier.fillMaxWidth())
}

@Composable
private fun HomeInfoChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.height(48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeMetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTrendCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    Canvas(modifier) {
        val prices = points.map { it.price }
        val minValue = prices.minOrNull() ?: stock.quote.previousClose
        val maxValue = prices.maxOrNull() ?: stock.quote.previousClose
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 5.dp.toPx()
        val bottom = size.height - 5.dp.toPx()
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
private fun Section(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SectionSmall(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.35f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockDetailScreen(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    depthTab: String,
    onSelectDepthTab: (String) -> Unit,
    isWatched: Boolean,
    activeAction: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAction: (String) -> Unit,
    onCloseAction: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { AStockTerminalTopBar(state, stock, loading, onBack, onRefresh) }
        item { AStockTerminalPanel(state, stock, loading, selectedTab, onSelectTab, depthTab, onSelectDepthTab) }
        if (activeAction != null) {
            item { AStockActionPanel(state, stock, activeAction, isWatched, selectedTab, onCloseAction, onRefresh, { onAction("加自选") }) }
        }
        item { AStockAiPanel(state, stock, onOpenAssistant) }
        item { AStockInfoPanel(state, stock) }
        item { AStockBottomActionPanel(state, stock, isWatched, activeAction, onAction) }
    }
}

@Composable
private fun AStockTerminalTopBar(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 999, Modifier.fillMaxWidth().height(52.dp), GlassRole.Nav) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableGlass(state.quality, state.glassIntensity * 0.68f, state.motionIntensity, 999, Modifier.width(44.dp).height(36.dp), GlassRole.Chip, onClick = onBack) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹", color = Color.White.copy(alpha = 0.92f), fontSize = 25.sp, lineHeight = 25.sp, fontWeight = FontWeight.Light) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stock.quote.name, color = Color.White, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stock.quote.code, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    AStockBadge(state, stock.quote.market)
                    AStockBadge(state, if (stockUsesFallbackSample(stock)) "示例" else "L1")
                }
            }
            PressableGlass(state.quality, state.glassIntensity * 0.68f, state.motionIntensity, 999, Modifier.width(44.dp).height(36.dp), GlassRole.Chip, onClick = onRefresh) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (loading) "…" else "⟳", color = Color.White.copy(alpha = 0.82f), fontSize = 18.sp, fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun AStockTerminalPanel(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    depthTab: String,
    onSelectDepthTab: (String) -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 18, Modifier.fillMaxWidth().height(600.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AStockQuoteTerminalHeader(stock, loading)
            AStockPopularityTicker(stock)
            AStockTerminalTabs(state, selectedTab, onSelectTab)
            AStockCoreTradingArea(stock, selectedTab, depthTab, onSelectDepthTab)
            AStockTerminalTapeAndFlow(stock)
        }
    }
}

@Composable
private fun AStockQuoteTerminalHeader(stock: StockDetailUiState, loading: Boolean) {
    val q = stock.quote
    Row(Modifier.fillMaxWidth().height(118.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(q.price, color = quoteColor(q.isRising), fontSize = 43.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("${q.changeAmount}  ${q.changePercent}", color = quoteColor(q.isRising), fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(stockDataStatusText(stock, loading), color = dataStatusColor(stock, loading), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1.72f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            quoteBoardMetrics(stock).take(12).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { metric -> TerminalQuoteMetric(metric, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TerminalQuoteMetric(metric: StockMetric, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(metric.label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(metric.value, color = toneColor(metric.tone).copy(alpha = 0.95f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockPopularityTicker(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("🔥", fontSize = 15.sp)
        Text("${stock.quote.name} 个股人气排名 ${stock.quote.popularityRank.ifBlank { "--" }}", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("×", color = Color.White.copy(alpha = 0.48f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockTerminalTabs(state: AssistantUiState, selectedTab: String, onSelectTab: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        StockTabs.forEach { label ->
            val selected = selectedTab == label
            PressableGlass(state.quality, state.glassIntensity * if (selected) 0.95f else 0.56f, state.motionIntensity, 12, Modifier.weight(1f).height(34.dp), GlassRole.Chip, onClick = { onSelectTab(label) }) {
                Column(Modifier.fillMaxSize().padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, color = Color.White.copy(alpha = if (selected) 0.98f else 0.50f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(2.dp)) { if (selected) Canvas(Modifier.fillMaxSize()) { drawRoundRect(RiseRed.copy(alpha = 0.90f), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())) } }
                }
            }
        }
        Text("⚙", color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockCoreTradingArea(stock: StockDetailUiState, selectedTab: String, depthTab: String, onSelectDepthTab: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(2.18f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (selectedTab == "分时") AStockInlineChartHeader(stock) else AStockKLineHeader(stock, selectedTab)
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                if (selectedTab == "分时") {
                    AStockIntradayCanvas(stock, Modifier.fillMaxSize())
                    AStockChartAxisOverlay(stock)
                } else {
                    AStockKLineCanvas(stock, selectedTab, Modifier.fillMaxSize())
                }
            }
            if (selectedTab == "分时") AStockTimeAxis() else AStockKLineAxis(stock, selectedTab)
            if (selectedTab == "分时") AStockVolumeCanvas(stock, Modifier.fillMaxWidth().height(72.dp)) else AStockKLineVolumeCanvas(stock, selectedTab, Modifier.fillMaxWidth().height(72.dp))
        }
        AStockDepthAndTapeColumn(sampleQualityState = sampleQuality(), stock = stock, depthTab = depthTab, onSelectDepthTab = onSelectDepthTab, modifier = Modifier.weight(0.98f))
    }
}

@Composable
private fun AStockInlineChartHeader(stock: StockDetailUiState) {
    val avg = stock.minutePoints.map { it.average }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: stock.quote.previousClose
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("集合竞价", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("均价 ${formatTwo(avg)}", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("最新 ${stock.quote.price}", color = quoteColor(stock.quote.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun AStockKLineHeader(stock: StockDetailUiState, tab: String) {
    val points = kLinesForTab(stock, tab)
    val last = points.lastOrNull()
    val ma5 = points.takeLast(5).takeIf { it.isNotEmpty() }?.map { it.close }?.average()?.toFloat() ?: last?.close ?: 0f
    val ma10 = points.takeLast(10).takeIf { it.isNotEmpty() }?.map { it.close }?.average()?.toFloat() ?: last?.close ?: 0f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tab, color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("MA5 ${formatTwo(ma5)}", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("MA10 ${formatTwo(ma10)}", color = Color(0xFF9FD2FF), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun AStockIntradayCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    Canvas(modifier = modifier) {
        val prices = points.map { it.price }
        val averages = points.map { it.average }
        val minValue = minOf(prices.minOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 0.985f
        val maxValue = maxOf(prices.maxOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 1.015f
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        drawChartGrid(left, right, top, bottom)
        val baseY = bottom - (stock.quote.previousClose - minValue) / range * (bottom - top)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(left, baseY), Offset(right, baseY), 1.dp.toPx(), cap = StrokeCap.Round)
        fun point(index: Int, value: Float): Offset = Offset(left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat(), bottom - (value - minValue) / range * (bottom - top))
        val pricePath = Path()
        prices.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) pricePath.moveTo(p.x, p.y) else pricePath.lineTo(p.x, p.y) }
        val avgPath = Path()
        averages.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) avgPath.moveTo(p.x, p.y) else avgPath.lineTo(p.x, p.y) }
        drawPath(pricePath, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(avgPath, Color(0xFFFFC857), style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun AStockChartAxisOverlay(stock: StockDetailUiState) {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    val prices = points.map { it.price }
    val high = prices.maxOrNull() ?: stock.quote.previousClose
    val low = prices.minOrNull() ?: stock.quote.previousClose
    val base = stock.quote.previousClose.takeIf { it > 0f } ?: high
    val highPct = if (base != 0f) (high - base) / base * 100f else 0f
    val lowPct = if (base != 0f) (low - base) / base * 100f else 0f
    Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTwo(high), color = quoteColor(high >= base), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(formatSignedPct(highPct), color = quoteColor(highPct >= 0f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTwo(base), color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("0.00%", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTwo(low), color = quoteColor(false), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(formatSignedPct(lowPct), color = quoteColor(false), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AStockKLineCanvas(stock: StockDetailUiState, tab: String, modifier: Modifier) {
    val points = kLinesForTab(stock, tab)
    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        drawChartGrid(left, right, top, bottom)
        val minValue = points.minOfOrNull { it.low } ?: stock.quote.previousClose
        val maxValue = points.maxOfOrNull { it.high } ?: stock.quote.previousClose
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val space = (right - left) / points.size.coerceAtLeast(1)
        val candleWidth = (space * 0.56f).coerceAtLeast(3.dp.toPx())
        fun y(value: Float): Float = bottom - (value - minValue) / range * (bottom - top)
        points.forEachIndexed { index, p ->
            val x = left + space * index + space / 2f
            val color = quoteColor(p.close >= p.open).copy(alpha = 0.86f)
            drawLine(color, Offset(x, y(p.high)), Offset(x, y(p.low)), 1.dp.toPx(), cap = StrokeCap.Round)
            val rectTop = minOf(y(p.open), y(p.close))
            val rectHeight = max(2.dp.toPx(), abs(y(p.open) - y(p.close)))
            drawRoundRect(color, Offset(x - candleWidth / 2f, rectTop), Size(candleWidth, rectHeight), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(left: Float, right: Float, top: Float, bottom: Float) {
    repeat(5) { i ->
        val y = top + (bottom - top) * i / 4f
        drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round)
    }
    repeat(4) { i ->
        val x = left + (right - left) * i / 3f
        drawLine(Color.White.copy(alpha = 0.08f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun AStockVolumeCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    Canvas(modifier = modifier) {
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
private fun AStockKLineVolumeCanvas(stock: StockDetailUiState, tab: String, modifier: Modifier) {
    val points = kLinesForTab(stock, tab)
    Canvas(modifier = modifier) {
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        val maxVolume = points.maxOfOrNull { it.volume } ?: 1f
        val barSpace = (right - left) / points.size.coerceAtLeast(1)
        val barWidth = barSpace * 0.58f
        points.forEachIndexed { index, point ->
            val x = left + index * barSpace + barSpace / 2f
            val color = if (point.close >= point.open) RiseRed else FallGreen
            val h = (bottom - top) * (point.volume / maxVolume).coerceIn(0.04f, 1f)
            drawRoundRect(color.copy(alpha = 0.66f), Offset(x - barWidth / 2f, bottom - h), Size(barWidth, h), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
private fun AStockTimeAxis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("09:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("11:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("15:00", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockKLineAxis(stock: StockDetailUiState, tab: String) {
    val points = kLinesForTab(stock, tab)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.firstOrNull()?.date ?: "--", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(tab, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(points.lastOrNull()?.date ?: "--", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockDepthAndTapeColumn(
    sampleQualityState: com.yuchen.ailedger.model.RenderQuality,
    stock: StockDetailUiState,
    depthTab: String,
    onSelectDepthTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            DepthTabs.forEach { label ->
                val selected = depthTab == label
                PressableGlass(sampleQualityState, 0.42f, 0.25f, 8, Modifier.weight(1f).height(24.dp), GlassRole.Chip, onClick = { onSelectDepthTab(label) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = if (selected) RiseRed else Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
        if (depthTab == "五档") {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                stock.sellLevels.takeLast(5).forEach { TerminalOrderRow(it) }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(RiseRed.copy(alpha = 0.85f), Offset(0f, size.height / 2f), Offset(size.width * 0.58f, size.height / 2f), 2.dp.toPx())
                        drawLine(FallGreen.copy(alpha = 0.85f), Offset(size.width * 0.58f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx())
                    }
                }
                stock.buyLevels.take(5).forEach { TerminalOrderRow(it) }
            }
            Column(Modifier.fillMaxWidth().height(78.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                normalizedTicks(stock).take(3).forEach { TerminalTickRow(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("大单", color = FallGreen.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(stock.moneyFlow.mainInflow, color = flowColor(stock.moneyFlow.mainInflow), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                normalizedTicks(stock).take(10).forEach { TerminalTickRow(it) }
            }
            Text("成交明细来自分时聚合，后续可接逐笔接口", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun TerminalOrderRow(level: StockOrderLevel) {
    Row(Modifier.fillMaxWidth().height(21.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(level.label, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.62f), maxLines = 1)
        Text(level.price, color = quoteColor(level.isAsk), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.78f), maxLines = 1, textAlign = TextAlign.End)
        Text(level.volume, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.70f), maxLines = 1, textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TerminalTickRow(tick: StockTradeTick) {
    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tick.time, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.80f), maxLines = 1)
        Text(tick.price, color = quoteColor(tick.isBuy), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.72f), maxLines = 1, textAlign = TextAlign.End)
        Text(tick.volume, color = Color.White.copy(alpha = 0.76f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.68f), maxLines = 1, textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockTerminalTapeAndFlow(stock: StockDetailUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(27.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun FlowChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = flowColor(value), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockActionPanel(
    state: AssistantUiState,
    stock: StockDetailUiState,
    activeAction: String,
    isWatched: Boolean,
    selectedTab: String,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatch: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(actionTitle(activeAction), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                PressableGlass(state.quality, state.glassIntensity * 0.55f, state.motionIntensity, 999, Modifier.width(38.dp).height(28.dp), GlassRole.Chip, onClick = onClose) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("×", color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Black) }
                }
            }
            when (activeAction) {
                "下单" -> SimulatedOrderPanel(state, stock)
                "社区" -> CommunityPanel(stock)
                "加自选" -> WatchPanel(isWatched, stock)
                "更多" -> MorePanel(state, stock, selectedTab, onRefresh)
            }
        }
    }
}

@Composable
private fun SimulatedOrderPanel(state: AssistantUiState, stock: StockDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("模拟委托入口，不连接真实券商账户，不产生真实交易。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableGlass(state.quality, state.glassIntensity * 0.78f, state.motionIntensity, 18, Modifier.weight(1f).height(42.dp), GlassRole.Chip, onClick = {}) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("模拟买入 ${stock.quote.price}", color = RiseRed, fontSize = 13.sp, fontWeight = FontWeight.Black) } }
            PressableGlass(state.quality, state.glassIntensity * 0.78f, state.motionIntensity, 18, Modifier.weight(1f).height(42.dp), GlassRole.Chip, onClick = {}) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("模拟卖出 ${stock.quote.price}", color = FallGreen, fontSize = 13.sp, fontWeight = FontWeight.Black) } }
        }
    }
}

@Composable
private fun CommunityPanel(stock: StockDetailUiState) {
    Text("社区观点会接入后端后显示真实讨论。当前行情上下文：${stock.quote.name} ${stock.quote.changePercent}，成交额 ${stock.quote.amount}，量比 ${stock.quote.volumeRatio}。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 17.sp)
}

@Composable
private fun WatchPanel(isWatched: Boolean, stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isWatched) "已加入自选" else "已移出自选", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text("${stock.quote.name} ${stock.quote.code} · 本地状态已更新，后续可接账号同步。", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text(if (isWatched) "★" else "☆", color = Color(0xFFFFC857), fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MorePanel(state: AssistantUiState, stock: StockDetailUiState, selectedTab: String, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当前图表：$selectedTab · 数据源：${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        PressableGlass(state.quality, state.glassIntensity * 0.55f, state.motionIntensity, 14, Modifier.fillMaxWidth().height(38.dp), GlassRole.Chip, onClick = onRefresh) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("立即刷新行情", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) } }
    }
}

private fun actionTitle(action: String): String = when (action) {
    "下单" -> "模拟下单"
    "社区" -> "社区观点"
    "加自选" -> "自选状态"
    "更多" -> "更多功能"
    else -> action
}

@Composable
private fun AStockSearchPanel(
    state: AssistantUiState,
    query: String,
    loading: Boolean,
    stock: StockDetailUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("搜索个股", "输入 A 股代码或名称，加载真实报价、分时和K线")
            AStockSearchInputRow(state, query, loading, onQueryChange, onSearch)
            Text("当前：${stock.quote.name} ${stock.quote.code} · ${stockDataStatusText(stock, loading)}", color = dataStatusColor(stock, loading), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            stock.errorMessage?.let { Text("提示：$it", color = Color(0xFFFFC857).copy(alpha = 0.86f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun AStockSearchInputRow(
    state: AssistantUiState,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPanel(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(52.dp), GlassRole.Chip) {
            Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("⌕", color = Color.White.copy(alpha = 0.58f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("代码 / 名称", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black),
                        cursorBrush = SolidColor(Color.White.copy(alpha = 0.90f)),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (query.isBlank()) Text("输入 600519 / 贵州茅台", color = Color.White.copy(alpha = 0.38f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
        PressableGlass(state.quality, state.glassIntensity * 1.05f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(44.dp), GlassRole.Floating, onClick = onSearch) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (loading) "正在连接行情代理…" else "搜索并打开个股详情", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AStockBadge(state: AssistantUiState, text: String) {
    GlassPanel(state.quality, state.glassIntensity * 0.64f, state.motionIntensity, 8, Modifier.height(18.dp), GlassRole.Chip) {
        Box(Modifier.padding(horizontal = 7.dp).fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFFFFC7A1), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
    }
}

@Composable
private fun AStockAiPanel(state: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 看盘摘要", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(if (stockUsesFallbackSample(stock)) "示例" else if (stock.quote.isRising) "偏强" else "谨慎", color = dataStatusColor(stock, false), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Text(stock.aiSummary, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, lineHeight = 18.sp)
            Text(stock.dataSourceLabel, color = Color.White.copy(alpha = 0.36f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockInfoPanel(state: AssistantUiState, stock: StockDetailUiState) {
    InfoCard(state, "资料与消息", "财务、公告、新闻和研报入口", stock.fundamentals.chunked(3).map { row -> row.joinToString("    ") { "${it.label} ${it.value}" } })
}

@Composable
private fun AStockBottomActionPanel(state: AssistantUiState, stock: StockDetailUiState, isWatched: Boolean, activeAction: String?, onAction: (String) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity * 0.82f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(68.dp), GlassRole.Nav) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("沪 ${stock.indices.firstOrNull()?.value ?: stock.quote.price}", color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(stock.indices.firstOrNull()?.changePercent ?: stock.quote.changePercent, color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            listOf("下单", "社区", "加自选", "更多").forEach { label ->
                val displayLabel = if (label == "加自选" && isWatched) "已自选" else label
                val selected = activeAction == label || (label == "加自选" && isWatched)
                PressableGlass(state.quality, state.glassIntensity * if (selected) 0.95f else 0.55f, state.motionIntensity, 16, Modifier.weight(1f).height(52.dp), GlassRole.Chip, onClick = { onAction(label) }) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(if (label == "加自选") if (isWatched) "★" else "+" else if (label == "更多") "…" else "□", color = if (selected) Color.White else Color.White.copy(alpha = 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(displayLabel, color = Color.White.copy(alpha = if (selected) 0.90f else 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AStockWatchPanel(state: AssistantUiState, stock: StockDetailUiState, onOpenCode: ((String) -> Unit)? = null) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("自选快照", "真实自选同步前，先展示当前关注与候选池")
            homeWatchItems(stock).forEach { item ->
                PressableGlass(state.quality, state.glassIntensity * 0.62f, state.motionIntensity, 16, Modifier.fillMaxWidth().height(38.dp), GlassRole.Chip, onClick = { onOpenCode?.invoke(item.code) }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
private fun AStockMarketBoards(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("市场榜单", "热度排行、强弱异动、资金关注")
            homeRankItems(stock).take(5).forEach { item -> AStockRankRow(item) }
        }
    }
}

@Composable
private fun AStockRankRow(item: StockRankItem) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.code, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(item.value, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.changePercent, color = quoteColor(item.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.7f), maxLines = 1)
    }
}

@Composable
private fun InfoCard(state: AssistantUiState, title: String, subtitle: String, rows: List<String>) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section(title, subtitle)
            rows.forEach { Text(it, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun homeIndexItems(stock: StockDetailUiState): List<com.yuchen.ailedger.model.StockIndexSnapshot> {
    if (stock.indices.isNotEmpty()) return stock.indices
    return listOf(
        com.yuchen.ailedger.model.StockIndexSnapshot("上证参考", stock.quote.price, stock.quote.changePercent, stock.quote.isRising),
        com.yuchen.ailedger.model.StockIndexSnapshot("成交强度", stock.quote.amount, stock.quote.turnoverRate, !stock.quote.turnoverRate.startsWith("-")),
        com.yuchen.ailedger.model.StockIndexSnapshot("量能", stock.quote.volumeRatio, stock.moneyFlow.mainInflow, !stock.moneyFlow.mainInflow.startsWith("-"))
    )
}

private fun homeWatchItems(stock: StockDetailUiState): List<StockWatchItem> {
    if (stock.watchlist.isNotEmpty()) return stock.watchlist
    return listOf(
        StockWatchItem(stock.quote.name, stock.quote.code, stock.quote.price, stock.quote.changePercent, stock.quote.isRising),
        StockWatchItem("贵州茅台", "600519", "搜索", "A股", true),
        StockWatchItem("宁德时代", "300750", "搜索", "A股", true),
        StockWatchItem("比亚迪", "002594", "搜索", "A股", true)
    )
}

private fun homeRankItems(stock: StockDetailUiState): List<StockRankItem> {
    val boardItems = stock.marketBoards.flatMap { it.items }
    if (boardItems.isNotEmpty()) return boardItems
    return listOf(
        StockRankItem(stock.quote.name, stock.quote.code, stock.quote.price, stock.quote.changePercent, stock.quote.isRising),
        StockRankItem("贵州茅台", "600519", "白酒龙头", "查看", true),
        StockRankItem("宁德时代", "300750", "新能源", "查看", true),
        StockRankItem("比亚迪", "002594", "汽车链", "查看", true),
        StockRankItem("东方财富", "300059", "券商金融", "查看", true)
    )
}

private fun marketMoodText(stock: StockDetailUiState): String = when {
    stockUsesFallbackSample(stock) -> "等待真实行情"
    stock.quote.isRising && !stock.moneyFlow.mainInflow.startsWith("-") -> "量价偏强"
    stock.quote.isRising -> "价格偏强"
    else -> "谨慎观察"
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
    else -> label
}

private fun homeActionSubtitle(label: String): String = when (label) {
    "热榜" -> "跟踪当前热度、涨跌幅和成交额"
    "板块" -> "从个股行情反推相关概念入口"
    "资金" -> "主力、超大单、大单与中小单"
    "异动" -> "价格、量比、换手和盘口变化"
    "龙虎" -> "后续接入营业部与机构席位"
    "日历" -> "公告、财报、停复牌和新股事项"
    "研报" -> "用 AI 汇总行情和基本面线索"
    "预警" -> "价格、量能和资金流触发提醒"
    else -> "行情工具"
}

private fun sampleQuality(): com.yuchen.ailedger.model.RenderQuality = com.yuchen.ailedger.model.RenderQuality.Balanced

private fun normalizedTicks(stock: StockDetailUiState): List<StockTradeTick> {
    if (stock.tradeTicks.isNotEmpty()) return stock.tradeTicks
    return stock.minutePoints.takeLast(8).reversed().mapIndexed { index, item ->
        val prev = stock.minutePoints.getOrNull(stock.minutePoints.lastIndex - index - 1)?.price ?: stock.quote.previousClose
        StockTradeTick(item.time.ifBlank { "--" }, formatTwo(item.price), ((item.volumeRatio * 1000).toInt()).coerceAtLeast(1).toString(), if (item.price >= prev) "买" else "卖", item.price >= prev)
    }
}

private fun kLinesForTab(stock: StockDetailUiState, tab: String): List<StockKLinePoint> {
    val source = stock.kLinePoints.ifEmpty { minuteAsKLine(stock) }
    return when (tab) {
        "五日" -> source.takeLast(5)
        "周K" -> source.takeLast(60).chunked(5).mapNotNull { mergeKLine(it) }
        "月K" -> source.takeLast(120).chunked(20).mapNotNull { mergeKLine(it) }
        else -> source.takeLast(44)
    }.ifEmpty { source.takeLast(12) }
}

private fun minuteAsKLine(stock: StockDetailUiState): List<StockKLinePoint> {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    return points.mapIndexed { index, p ->
        val prev = points.getOrNull(index - 1)?.price ?: stock.quote.previousClose
        StockKLinePoint(p.time.ifBlank { index.toString() }, prev, p.price, max(prev, p.price), minOf(prev, p.price), p.volumeRatio.coerceAtLeast(0.01f), p.volumeRatio, "--")
    }
}

private fun mergeKLine(points: List<StockKLinePoint>): StockKLinePoint? {
    if (points.isEmpty()) return null
    val first = points.first()
    val last = points.last()
    return StockKLinePoint(last.date, first.open, last.close, points.maxOf { it.high }, points.minOf { it.low }, points.sumOf { it.volume.toDouble() }.toFloat(), points.sumOf { it.amount.toDouble() }.toFloat(), last.changePercent)
}

private fun stockUsesFallbackSample(stock: StockDetailUiState): Boolean = stock.errorMessage != null || stock.dataSourceLabel.contains("示例") || stock.aiSummary.contains("示例数据")

private fun stockDataStatusText(stock: StockDetailUiState, loading: Boolean): String = when {
    loading -> "连接行情代理中"
    stockUsesFallbackSample(stock) -> "示例数据 · 真实行情未返回"
    stock.dataSourceLabel.contains("缓存") -> stock.dataSourceLabel
    else -> "实时行情 · ${stock.dataSourceLabel}"
}

private fun dataStatusColor(stock: StockDetailUiState, loading: Boolean): Color = when {
    loading -> Color(0xFFFFC857)
    stockUsesFallbackSample(stock) -> Color(0xFFFFC857)
    else -> Color.White.copy(alpha = 0.66f)
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
    StockMetric("来源", if (stockUsesFallbackSample(stock)) "示例" else "实时")
)

private fun formatTwo(value: Float): String = "%.2f".format(value)
private fun formatSignedPct(value: Float): String = "%+.2f%%".format(value)

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

private val RiseRed = Color(0xFFFF4D5D)
private val FallGreen = Color(0xFF41D873)
