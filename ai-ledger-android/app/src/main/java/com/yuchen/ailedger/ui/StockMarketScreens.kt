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
import com.yuchen.ailedger.model.sampleAStockDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var loading by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

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
        loading = true
        stock = withContext(Dispatchers.IO) { repository.loadAStock(query) }
        query = stock.quote.code
        loading = false
    }

    if (showDetail) {
        AStockDetailScreen(
            state = state,
            stock = stock,
            loading = loading,
            onBack = { showDetail = false },
            onOpenAssistant = onOpenAssistant
        )
    } else {
        AStockMarketHomeScreen(
            state = state,
            stock = stock,
            query = query,
            loading = loading,
            onQueryChange = { query = it },
            onSearch = { loadStock(query, true) },
            onBack = onBack,
            onOpenDetail = { showDetail = true },
            onOpenCode = { code -> loadStock(code, true) }
        )
    }
}

@Composable
private fun AStockMarketHomeScreen(
    state: AssistantUiState,
    stock: StockDetailUiState,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenCode: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { AStockTopBar("A股行情首页", "搜索个股、查看自选、指数、热榜和龙虎榜", state, onBack) }
        item { AStockSearchPanel(state, query, loading, stock, onQueryChange, onSearch) }
        item { AStockHomeHero(state, stock, onOpenDetail) }
        item { AStockIndexPanel(state, stock) }
        item { AStockWatchPanel(state, stock, onOpenCode) }
        item { AStockFeatureMatrix(state, stock) }
        item { AStockMarketBoards(state, stock) }
    }
}

@Composable
private fun AStockDetailScreen(
    state: AssistantUiState,
    stock: StockDetailUiState,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { AStockMarketTopNav(state, stock, onBack) }
        item { AStockCompactQuotePanel(state, stock) }
        item { AStockCompactStatus(state, stock, loading) }
        item { AStockTabs(state) }
        item { AStockTradingBoard(state, stock) }
        item { AStockTradeInfoStrip(state, stock) }
        item { AStockInfoPanel(state, stock) }
        item { AStockAiPanel(state, stock, onOpenAssistant) }
    }
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
            Section("搜索个股", "输入 A 股代码或名称，先接真实报价和日K线")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(state.quality, state.glassIntensity * 0.88f, state.motionIntensity, 20, Modifier.weight(1f).height(46.dp), GlassRole.Chip) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(Color.White.copy(alpha = 0.85f)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (query.isBlank()) Text("例如 600519 / 贵州茅台", color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp)
                    }
                }
                PressableGlass(state.quality, state.glassIntensity * 1.05f, state.motionIntensity, 20, Modifier.height(46.dp), GlassRole.Floating, onClick = onSearch) {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (loading) "加载" else "搜索", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text("当前：${stock.quote.name} ${stock.quote.code} · ${stock.dataSourceLabel}", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            stock.errorMessage?.let {
                Text("提示：公开行情源连接不稳定，已切换示例数据", color = Color(0xFFFFC857).copy(alpha = 0.86f), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun AStockCompactStatus(state: AssistantUiState, stock: StockDetailUiState, loading: Boolean) {
    val text = when {
        loading -> "正在刷新行情…"
        stock.errorMessage != null -> "公开行情源连接不稳定，已回退示例数据"
        else -> stock.dataSourceLabel
    }
    GlassPanel(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 20, Modifier.fillMaxWidth(), GlassRole.Card) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, color = if (stock.errorMessage == null) Color.White.copy(alpha = 0.62f) else Color(0xFFFFC857).copy(alpha = 0.84f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockTopBar(title: String, subtitle: String, state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, Modifier.height(38.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(title, color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockMarketTopNav(state: AssistantUiState, stock: StockDetailUiState, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 999, Modifier.height(48.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White.copy(alpha = 0.86f), fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Light)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stock.quote.name, color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${stock.quote.code} · ${stock.quote.market}    L2", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        PressableGlass(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 999, Modifier.height(48.dp), GlassRole.Chip, onClick = {}) {
            Box(Modifier.padding(horizontal = 12.dp).fillMaxSize(), contentAlignment = Alignment.Center) { Text("⌕", color = Color.White.copy(alpha = 0.78f), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        PressableGlass(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 999, Modifier.height(48.dp), GlassRole.Chip, onClick = {}) {
            Box(Modifier.padding(horizontal = 12.dp).fillMaxSize(), contentAlignment = Alignment.Center) { Text("☆", color = Color.White.copy(alpha = 0.78f), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AStockHomeHero(state: AssistantUiState, stock: StockDetailUiState, onOpenDetail: () -> Unit) {
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.04f,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(188.dp),
        mood = OpenGlShellMood.Summary,
        onClick = onOpenDetail
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("今日关注", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${stock.quote.name} · ${stock.quote.code}", color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("点入查看分时、K线、十档盘口、成交明细和资金流", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeHeroMetric("现价", stock.quote.price, quoteColor(stock.quote.isRising), Modifier.weight(1f))
                HomeHeroMetric("涨跌", stock.quote.changePercent, quoteColor(stock.quote.isRising), Modifier.weight(1f))
                HomeHeroMetric("成交额", stock.quote.amount, Color.White, Modifier.weight(1f))
            }
            Text("进入个股详情", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun HomeHeroMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockCompactQuotePanel(state: AssistantUiState, stock: StockDetailUiState) {
    val quote = stock.quote
    OpenGlShellGlass(state.quality, state.glassIntensity * 1.04f, state.motionIntensity, 28, Modifier.fillMaxWidth().height(156.dp), OpenGlShellMood.Summary) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(quote.price, color = quoteColor(quote.isRising), fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("${quote.changeAmount}   ${quote.changePercent}", color = quoteColor(quote.isRising), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("交易中  15:00", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(Modifier.weight(1.75f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                stock.topMetrics.chunked(3).take(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { metric -> QuoteMetric(metric, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteMetric(metric: StockMetric, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(metric.label, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(metric.value, color = toneColor(metric.tone).copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockTabs(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("分时", "日K", "周K", "月K", "五日", "更多").forEachIndexed { index, label ->
                GlassPanel(state.quality, state.glassIntensity * if (index == 0) 1.02f else 0.74f, state.motionIntensity, 999, Modifier.weight(1f).height(32.dp), if (index == 0) GlassRole.Floating else GlassRole.Chip) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = Color.White.copy(alpha = if (index == 0) 0.94f else 0.48f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun AStockTradingBoard(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2.15f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AStockInlineChartHeader(stock)
                    AStockIntradayCanvas(stock, Modifier.fillMaxWidth().height(184.dp))
                    AStockVolumeCanvas(stock, Modifier.fillMaxWidth().height(76.dp))
                    AStockTimeAxis()
                }
                AStockInlineOrderBook(state, stock, Modifier.weight(1f).height(306.dp))
            }
        }
    }
}

@Composable
private fun AStockInlineChartHeader(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("均价: 20.09", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("最新: ${stock.quote.price}", color = quoteColor(stock.quote.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("${stock.quote.changeAmount}  ${stock.quote.changePercent}", color = quoteColor(stock.quote.isRising), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun AStockIntradayCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints
    Canvas(modifier = modifier) {
        val prices = points.map { it.price }
        val averages = points.map { it.average }
        val minValue = minOf(prices.minOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 0.98f
        val maxValue = maxOf(prices.maxOrNull() ?: stock.quote.previousClose, stock.quote.previousClose) * 1.02f
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val left = 4.dp.toPx(); val right = size.width - 4.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 8.dp.toPx()
        repeat(4) { i -> val y = top + (bottom - top) * i / 3f; drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round) }
        repeat(4) { i -> val x = left + (right - left) * i / 3f; drawLine(Color.White.copy(alpha = 0.08f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(), cap = StrokeCap.Round) }
        val baseY = bottom - (stock.quote.previousClose - minValue) / range * (bottom - top)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(left, baseY), Offset(right, baseY), 1.dp.toPx(), cap = StrokeCap.Round)
        fun point(index: Int, value: Float): Offset = Offset(left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat(), bottom - (value - minValue) / range * (bottom - top))
        val pricePath = Path(); prices.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) pricePath.moveTo(p.x, p.y) else pricePath.lineTo(p.x, p.y) }
        val avgPath = Path(); averages.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) avgPath.moveTo(p.x, p.y) else avgPath.lineTo(p.x, p.y) }
        drawPath(pricePath, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(avgPath, Color(0xFFFFC857), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
        drawLine(RiseRed.copy(alpha = 0.32f), Offset(right - 7.dp.toPx(), top), Offset(right - 7.dp.toPx(), bottom), 2.dp.toPx())
    }
}

@Composable
private fun AStockVolumeCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.kLinePoints.takeLast(36).ifEmpty { sampleAStockDetailUiState().kLinePoints }
    Canvas(modifier = modifier) {
        val left = 4.dp.toPx(); val right = size.width - 4.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        repeat(3) { i -> val y = top + (bottom - top) * i / 2f; drawLine(Color.White.copy(alpha = 0.10f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round) }
        val maxVolume = points.maxOfOrNull { it.volume } ?: 1f
        val barSpace = (right - left) / points.size.coerceAtLeast(1)
        val barWidth = barSpace * 0.55f
        points.forEachIndexed { index, point ->
            val x = left + index * barSpace + barSpace / 2f
            val color = if (point.close >= point.open) RiseRed else FallGreen
            val h = (bottom - top) * (point.volume / maxVolume).coerceIn(0.04f, 1f)
            drawRoundRect(color.copy(alpha = 0.58f), Offset(x - barWidth / 2f, bottom - h), Size(barWidth, h), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
private fun AStockTimeAxis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("09:30", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("11:30/13:00", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("15:00", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockInlineOrderBook(state: AssistantUiState, stock: StockDetailUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 22, modifier, GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("盘口", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("五档", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stock.sellLevels.takeLast(5).forEach { OrderRowCompact(it) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp)) { Canvas(Modifier.fillMaxSize()) { drawLine(RiseRed.copy(alpha = 0.75f), Offset(0f, size.height / 2f), Offset(size.width * 0.56f, size.height / 2f), 2.dp.toPx()); drawLine(FallGreen.copy(alpha = 0.75f), Offset(size.width * 0.56f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx()) } }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stock.buyLevels.take(5).forEach { OrderRowCompact(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("56%", color = RiseRed.copy(alpha = 0.90f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("44%", color = FallGreen.copy(alpha = 0.90f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun OrderRowCompact(level: StockOrderLevel) {
    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(level.label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.64f), maxLines = 1)
        Text(level.price, color = quoteColor(level.isAsk), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.78f), maxLines = 1)
        Text(level.volume, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.70f), maxLines = 1)
    }
}

@Composable
private fun AStockTradeInfoStrip(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Text("成交", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("资金", color = Color.White.copy(alpha = 0.48f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("明细", color = Color.White.copy(alpha = 0.48f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TradeMetric("成交量", "44.76万手")
                    TradeMetric("成交额", stock.quote.amount)
                    TradeMetric("内盘", "21.21万")
                    TradeMetric("外盘", "23.55万")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TradeMetric("换手率", stock.quote.turnoverRate)
                    TradeMetric("量比", stock.quote.volumeRatio)
                    TradeMetric("振幅", "8.42%")
                    TradeMetric("均价", "20.09")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TradeMetric("最高", stock.quote.high, RiseRed)
                    TradeMetric("最低", stock.quote.low, FallGreen)
                    TradeMetric("涨停", "21.19", RiseRed)
                    TradeMetric("跌停", "17.33", FallGreen)
                }
            }
        }
    }
}

@Composable
private fun TradeMetric(label: String, value: String, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, color = color.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockKLineChart(state: AssistantUiState, stock: StockDetailUiState) {
    val points = stock.kLinePoints.takeLast(36).ifEmpty { sampleAStockDetailUiState().kLinePoints }
    GlassPanel(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("日K线", "已接真实日K原型；红涨绿跌，后续扩展 MA / 周K / 月K")
            AStockKLineCanvas(points, Modifier.fillMaxWidth().height(210.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(points.firstOrNull()?.date.orEmpty(), color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${points.size} 日", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(points.lastOrNull()?.date.orEmpty(), color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AStockKLineCanvas(points: List<StockKLinePoint>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val minPrice = (points.minOfOrNull { it.low } ?: 0f).coerceAtLeast(0.01f)
        val maxPrice = (points.maxOfOrNull { it.high } ?: 1f).coerceAtLeast(minPrice + 0.01f)
        val range = (maxPrice - minPrice).coerceAtLeast(0.01f)
        val left = 6.dp.toPx(); val right = size.width - 6.dp.toPx(); val top = 10.dp.toPx(); val bottom = size.height * 0.70f
        val volumeTop = bottom + 16.dp.toPx(); val volumeBottom = size.height - 8.dp.toPx()
        repeat(4) { i -> val y = top + (bottom - top) * i / 3f; drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round) }
        val candleSpace = (right - left) / points.size.coerceAtLeast(1)
        val candleWidth = candleSpace * 0.54f
        val maxVolume = points.maxOfOrNull { it.volume } ?: 1f
        fun y(value: Float): Float = bottom - (value - minPrice) / range * (bottom - top)
        points.forEachIndexed { index, point ->
            val x = left + candleSpace * index + candleSpace / 2f
            val color = if (point.close >= point.open) RiseRed else FallGreen
            val highY = y(point.high)
            val lowY = y(point.low)
            val openY = y(point.open)
            val closeY = y(point.close)
            drawLine(color.copy(alpha = 0.86f), Offset(x, highY), Offset(x, lowY), 1.2.dp.toPx(), cap = StrokeCap.Round)
            val rectTop = minOf(openY, closeY)
            val rectHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(1.6.dp.toPx())
            drawRoundRect(color.copy(alpha = 0.82f), Offset(x - candleWidth / 2f, rectTop), Size(candleWidth, rectHeight), CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()))
            val barHeight = (volumeBottom - volumeTop) * (point.volume / maxVolume).coerceIn(0.04f, 1f)
            drawRoundRect(color.copy(alpha = 0.45f), Offset(x - candleWidth / 2f, volumeBottom - barHeight), Size(candleWidth, barHeight), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
private fun AStockFeatureMatrix(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("行情功能总览", "榜单、板块、异动、资金、资讯和看盘工具")
            stock.featureGroups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section(group.title, group.subtitle)
                    group.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { entry -> AStockFeatureChip(entry, state, Modifier.weight(1f)) }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AStockFeatureChip(entry: StockFeatureEntry, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.88f, state.motionIntensity, 20, modifier.height(54.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(entry.title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AStockMarketBoards(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("市场榜单预览", "热度排行榜、龙虎榜、涨停梯队、板块热度、资金流向和竞价异动")
            stock.marketBoards.forEach { board ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Section(board.title, board.subtitle)
                    board.items.forEach { item -> AStockRankRow(item) }
                }
            }
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
private fun AStockInfoPanel(state: AssistantUiState, stock: StockDetailUiState) {
    InfoCard(state, "资料与消息", "财务、公告、新闻和研报入口", stock.fundamentals.chunked(3).map { row -> row.joinToString("    ") { "${it.label} ${it.value}" } })
}

@Composable
private fun AStockAiPanel(state: AssistantUiState, stock: StockDetailUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("AI 看盘摘要", "根据分时、量能、盘口和资金流整理")
            Text(stock.aiSummary, color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AStockIndexPanel(state: AssistantUiState, stock: StockDetailUiState) {
    InfoCard(state, "A股指数", "上证、深成、创业板和宽基指数", stock.indices.chunked(3).map { row -> row.joinToString("    ") { "${it.name} ${it.value} ${it.changePercent}" } })
}

@Composable
private fun AStockWatchPanel(state: AssistantUiState, stock: StockDetailUiState, onOpenCode: ((String) -> Unit)? = null) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section("A股自选", "点示例股会尝试加载真实报价与日K")
            stock.watchlist.forEach { item ->
                PressableGlass(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 18, Modifier.fillMaxWidth().height(42.dp), GlassRole.Chip, onClick = { onOpenCode?.invoke(item.code) }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
private fun InfoCard(state: AssistantUiState, title: String, subtitle: String, rows: List<String>) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Section(title, subtitle)
            rows.forEach { Text(it, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun toneColor(tone: StockTone): Color = when (tone) {
    StockTone.Rising -> RiseRed
    StockTone.Falling -> FallGreen
    StockTone.Neutral -> Color.White
}

private fun quoteColor(isRising: Boolean): Color = if (isRising) RiseRed else FallGreen

private val RiseRed = Color(0xFFFF4D5D)
private val FallGreen = Color(0xFF41D873)
