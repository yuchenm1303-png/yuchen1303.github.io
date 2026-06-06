package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
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
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.StockTradeTick
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
    var loading by remember { mutableStateOf(true) }
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
        loadStock(query, openDetail = false)
    }

    if (showDetail) {
        AStockDetailScreen(
            state = state,
            stock = stock,
            loading = loading,
            onBack = { showDetail = false },
            onRefresh = { loadStock(query, openDetail = true) },
            onOpenAssistant = onOpenAssistant
        )
    } else {
        AStockMarketHomeScreen(
            state = state,
            stock = stock,
            query = query,
            loading = loading,
            onQueryChange = { query = it },
            onSearch = { loadStock(query, openDetail = true) },
            onBack = onBack,
            onOpenDetail = {
                if (loading || stockUsesFallbackSample(stock)) loadStock(query, openDetail = true) else showDetail = true
            },
            onOpenCode = { code -> loadStock(code, openDetail = true) }
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
        item { AStockHomeHero(state, stock, loading, onOpenDetail) }
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
    onRefresh: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { AStockTerminalTopBar(state, stock, loading, onBack, onRefresh) }
        item { AStockTerminalPanel(state, stock, loading) }
        item { AStockAiPanel(state, stock, onOpenAssistant) }
        item { AStockInfoPanel(state, stock) }
        item { AStockBottomActionPanel(state, stock) }
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("‹", color = Color.White.copy(alpha = 0.92f), fontSize = 25.sp, lineHeight = 25.sp, fontWeight = FontWeight.Light)
                }
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (loading) "…" else "⟳", color = Color.White.copy(alpha = 0.82f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun AStockTerminalPanel(state: AssistantUiState, stock: StockDetailUiState, loading: Boolean) {
    GlassPanel(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 18, Modifier.fillMaxWidth().height(590.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AStockQuoteTerminalHeader(stock, loading)
            AStockPopularityTicker(stock)
            AStockTerminalTabs()
            AStockCoreTradingArea(stock)
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
private fun AStockTerminalTabs() {
    Row(Modifier.fillMaxWidth().height(37.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("分时", "日K", "周K", "月K", "五日", "更多⌄").forEachIndexed { index, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = Color.White.copy(alpha = if (index == 0) 0.96f else 0.48f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (index == 0) Canvas(Modifier.fillMaxSize()) { drawRoundRect(RiseRed.copy(alpha = 0.90f), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())) }
                }
            }
        }
        Text("⚙", color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockCoreTradingArea(stock: StockDetailUiState) {
    Row(Modifier.fillMaxWidth().height(334.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(2.18f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AStockInlineChartHeader(stock)
            Box(Modifier.fillMaxWidth().height(206.dp)) {
                AStockIntradayCanvas(stock, Modifier.fillMaxSize())
                AStockChartAxisOverlay(stock)
            }
            AStockTimeAxis()
            AStockVolumeCanvas(stock, Modifier.fillMaxWidth().height(72.dp))
        }
        AStockDepthAndTapeColumn(stock, Modifier.weight(0.98f))
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
        repeat(5) { i ->
            val y = top + (bottom - top) * i / 4f
            drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round)
        }
        repeat(4) { i ->
            val x = left + (right - left) * i / 3f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(), cap = StrokeCap.Round)
        }
        val baseY = bottom - (stock.quote.previousClose - minValue) / range * (bottom - top)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(left, baseY), Offset(right, baseY), 1.dp.toPx(), cap = StrokeCap.Round)
        fun point(index: Int, value: Float): Offset = Offset(left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat(), bottom - (value - minValue) / range * (bottom - top))
        val pricePath = Path()
        prices.forEachIndexed { i, v ->
            val p = point(i, v)
            if (i == 0) pricePath.moveTo(p.x, p.y) else pricePath.lineTo(p.x, p.y)
        }
        val avgPath = Path()
        averages.forEachIndexed { i, v ->
            val p = point(i, v)
            if (i == 0) avgPath.moveTo(p.x, p.y) else avgPath.lineTo(p.x, p.y)
        }
        drawPath(pricePath, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(avgPath, Color(0xFFFFC857), style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round))
        drawLine(Color.White.copy(alpha = 0.24f), Offset(left + (right - left) * 0.52f, top), Offset(left + (right - left) * 0.52f, bottom), 1.dp.toPx())
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
private fun AStockVolumeCanvas(stock: StockDetailUiState, modifier: Modifier) {
    val points = stock.minutePoints.ifEmpty { sampleAStockDetailUiState().minutePoints }
    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        repeat(3) { i ->
            val y = top + (bottom - top) * i / 2f
            drawLine(Color.White.copy(alpha = 0.10f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round)
        }
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
private fun AStockTimeAxis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("09:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("11:30", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("15:00", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AStockDepthAndTapeColumn(stock: StockDetailUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("五档", color = RiseRed, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("成交", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
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
        Column(Modifier.fillMaxWidth().height(88.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            normalizedTicks(stock).take(3).forEach { TerminalTickRow(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("大单", color = FallGreen.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(stock.moneyFlow.mainInflow, color = flowColor(stock.moneyFlow.mainInflow), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
private fun AStockTopBar(title: String, subtitle: String, state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, Modifier.width(82.dp).height(38.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
private fun AStockBadge(state: AssistantUiState, text: String) {
    GlassPanel(state.quality, state.glassIntensity * 0.64f, state.motionIntensity, 8, Modifier.height(18.dp), GlassRole.Chip) {
        Box(Modifier.padding(horizontal = 7.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color(0xFFFFC7A1), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun AStockHomeHero(state: AssistantUiState, stock: StockDetailUiState, loading: Boolean, onOpenDetail: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 1.02f, state.motionIntensity, 30, Modifier.fillMaxWidth().height(188.dp), GlassRole.Card, onClick = onOpenDetail) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (stockUsesFallbackSample(stock)) "等待真实行情" else "今日关注", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${stock.quote.name} · ${stock.quote.code}", color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(stockDataStatusText(stock, loading), color = dataStatusColor(stock, loading), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeHeroMetric("现价", stock.quote.price, quoteColor(stock.quote.isRising), Modifier.weight(1f))
                HomeHeroMetric("涨跌", stock.quote.changePercent, quoteColor(stock.quote.isRising), Modifier.weight(1f))
                HomeHeroMetric("成交额", stock.quote.amount, Color.White, Modifier.weight(1f))
            }
            Text(if (loading) "连接中，点击可继续等待" else "进入个股详情", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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
private fun AStockBottomActionPanel(state: AssistantUiState, stock: StockDetailUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.82f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(68.dp), GlassRole.Nav) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("沪 ${stock.indices.firstOrNull()?.value ?: "--"}", color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(stock.indices.firstOrNull()?.changePercent ?: "--", color = quoteColor(stock.indices.firstOrNull()?.isRising ?: stock.quote.isRising), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            listOf("下单", "社区", "加自选", "更多").forEach { label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (label == "加自选") "+" else if (label == "更多") "…" else "□", color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
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
            Section("A股自选", "点示例股会尝试加载真实报价与K线")
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
private fun InfoCard(state: AssistantUiState, title: String, subtitle: String, rows: List<String>) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section(title, subtitle)
            rows.forEach { Text(it, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

private fun normalizedTicks(stock: StockDetailUiState): List<StockTradeTick> {
    if (stock.tradeTicks.isNotEmpty()) return stock.tradeTicks
    return stock.minutePoints.takeLast(4).reversed().mapIndexed { index, item ->
        val prev = stock.minutePoints.getOrNull(stock.minutePoints.lastIndex - index - 1)?.price ?: stock.quote.previousClose
        StockTradeTick(
            time = item.time.ifBlank { "--" },
            price = formatTwo(item.price),
            volume = ((item.volumeRatio * 1000).toInt()).coerceAtLeast(1).toString(),
            direction = if (item.price >= prev) "买" else "卖",
            isBuy = item.price >= prev
        )
    }
}

private fun stockUsesFallbackSample(stock: StockDetailUiState): Boolean {
    return stock.errorMessage != null || stock.dataSourceLabel.contains("示例") || stock.aiSummary.contains("示例数据")
}

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
