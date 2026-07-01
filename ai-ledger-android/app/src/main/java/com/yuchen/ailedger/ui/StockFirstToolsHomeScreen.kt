package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.LedgerViewModel
import com.yuchen.ailedger.ToolsMarketHeroUiState
import com.yuchen.ailedger.ToolsMarketHeroViewModel
import com.yuchen.ailedger.ToolsMarketIndexItem
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.ToolDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

const val STOCK_MARKET_TOOL_TITLE = "股票行情"

@Composable
fun StockFirstToolsHomeScreen(
    state: AssistantUiState,
    onOpenTool: (ToolDestination) -> Unit,
    onCloseTool: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    val pageVisible = LocalPageVisible.current
    val pageState = if (pageVisible) {
        state
    } else {
        remember(pageVisible) { state.copy(motionIntensity = 0f) }
    }
    val selectedTool = pageState.selectedTool
    val heroViewModel: ToolsMarketHeroViewModel = viewModel()
    val heroUi by heroViewModel.uiState.collectAsState()
    val heroVisible = pageVisible && selectedTool == null

    LaunchedEffect(heroVisible) {
        heroViewModel.setVisible(heroVisible)
    }
    DisposableEffect(heroViewModel) {
        onDispose { heroViewModel.setVisible(false) }
    }

    if (selectedTool == ToolDestination.LedgerCenter || selectedTool == ToolDestination.Statistics) {
        val ledgerViewModel: LedgerViewModel = viewModel()
        GlassSceneScope(GlassSceneGroup.LedgerCenterPage) {
            NativeLedgerCenterScreen(
                appState = pageState,
                ledgerViewModel = ledgerViewModel,
                statisticsOnly = selectedTool == ToolDestination.Statistics,
                onBack = onCloseTool,
                onOpenAssistant = onOpenAssistant,
            )
        }
        return
    }

    if (selectedTool == ToolDestination.Reminder) {
        GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
            PlanCenterScreen(
                state = pageState,
                onBack = onCloseTool,
            )
        }
        return
    }

    if (selectedTool == ToolDestination.AppControl) {
        AppManagementScreen(
            state = pageState,
            onBack = onCloseTool,
        )
        return
    }

    if (selectedTool != null && selectedTool != ToolDestination.StockMarket) {
        PendingToolScreen(
            destination = selectedTool,
            state = pageState,
            onBack = onCloseTool,
        )
        return
    }

    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                ToolsEntrance(delayMs = 0, initialOffsetY = -8, initialScale = 0.985f) {
                    StockToolsHeader()
                }
            }
            item {
                ToolsEntrance(delayMs = 95, initialOffsetY = 18, initialScale = 0.966f) {
                    StockMarketHeroEntry(pageState, heroUi, onOpenTool)
                }
            }
            ToolDestination.entries.forEachIndexed { index, destination ->
                item(key = "tool-entry-${destination.name}") {
                    ToolsEntrance(
                        delayMs = 255L + index * 60L,
                        initialOffsetY = 20 + (index.coerceAtMost(3) * 2),
                        initialScale = 0.966f - index.coerceAtMost(3) * 0.002f,
                    ) {
                        StockToolEntryCard(
                            destination = destination,
                            state = pageState,
                            onClick = { onOpenTool(destination) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingToolScreen(
    destination: ToolDestination,
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PressableGlass(
                    quality = state.quality,
                    glassIntensity = state.glassIntensity,
                    motionIntensity = state.motionIntensity,
                    radius = 999,
                    modifier = Modifier.fillMaxWidth(0.28f).height(40.dp),
                    role = GlassRole.Chip,
                    onClick = onBack,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹ 返回", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TOOLS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(destination.title, color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(destination.subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            item {
                FrostInfoGlassPanel(
                    radius = 17.44f,
                    backdropAlpha = 1f,
                    frostAlpha = 0.090f,
                    dimAlpha = 0f,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF151A4F).copy(alpha = 0.28f))
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("功能正在建设", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(
                            "入口已经纳入统一路由，后续会按独立功能批次完成，不再出现点击后无反馈的情况。",
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsEntrance(
    delayMs: Long,
    modifier: Modifier = Modifier,
    initialOffsetY: Int = 24,
    initialScale: Float = 0.96f,
    content: @Composable () -> Unit
) {
    val pageActive = LocalPageActive.current
    val pageLeaving = LocalPageLeaving.current
    val activationTick = LocalPageActivationTick.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pageActive, pageLeaving, activationTick, delayMs) {
        if (pageActive) {
            visible = false
            yield()
            if (delayMs > 0L) delay(delayMs)
            visible = true
        } else {
            if (pageLeaving && delayMs > 0L) delay((delayMs / 16L).coerceAtMost(34L))
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } +
            scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(104)) +
            slideOutVertically(tween(118)) { (-initialOffsetY / 3).coerceIn(-10, 10) } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(126))
    ) {
        content()
    }
}

@Composable
private fun StockToolsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TOOLS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("功能", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text("把行情、记账和常用工具整理成可以执行的入口。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StockMarketHeroEntry(
    state: AssistantUiState,
    heroUi: ToolsMarketHeroUiState,
    onOpenTool: (ToolDestination) -> Unit
) {
    val statusLabel = when {
        heroUi.loading -> "同步中"
        heroUi.indices.any { it.hasRealQuote } -> "实时指数"
        !heroUi.errorMessage.isNullOrBlank() -> "待恢复"
        else -> "等待数据"
    }

    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.03f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth().height(224.dp),
        mood = OpenGlShellMood.Hero,
        onClick = { onOpenTool(ToolDestination.StockMarket) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "市场入口",
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        statusLabel,
                        color = Color(0xFF8DF9EA).copy(alpha = if (heroUi.loading) 0.54f else 0.76f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                Text(ToolDestination.StockMarket.title, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("三大指数、真实分时、热榜、板块和资金流", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth().height(100.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                heroUi.indices.take(3).forEach { item ->
                    StockHeroIndexMetric(
                        item = item,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun StockHeroIndexMetric(
    item: ToolsMarketIndexItem,
    modifier: Modifier = Modifier
) {
    val tone = when {
        !item.hasRealQuote -> Color.White.copy(alpha = 0.44f)
        item.isRising -> StockRise
        else -> StockFall
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF101742).copy(alpha = 0.26f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            item.name,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.price.ifBlank { "--" },
            color = Color.White.copy(alpha = if (item.hasRealQuote) 0.94f else 0.54f),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            heroIndexChangeText(item),
            color = tone.copy(alpha = if (item.hasRealQuote) 0.92f else 0.60f),
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        StockHeroSparkline(
            points = item.minutePoints,
            previousClose = item.previousClose,
            tone = tone,
            modifier = Modifier.fillMaxWidth().height(25.dp)
        )
    }
}

private fun heroIndexChangeText(item: ToolsMarketIndexItem): String {
    val amount = item.changeAmount.takeIf { it.isNotBlank() && it != "--" }
    val percent = item.changePercent.takeIf { it.isNotBlank() && it != "--" }
    return listOfNotNull(amount, percent).joinToString("  ").ifBlank { "--" }
}

@Composable
private fun StockHeroSparkline(
    points: List<StockMinutePoint>,
    previousClose: Float,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val rows = remember(points) { points.filter { it.price.isFinite() && it.price > 0f } }
    Canvas(modifier = modifier) {
        val inset = 1.dp.toPx()
        val left = inset
        val right = (size.width - inset).coerceAtLeast(left + 1f)
        val top = inset
        val bottom = (size.height - inset).coerceAtLeast(top + 1f)

        if (rows.size < 2) {
            val y = (top + bottom) / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.11f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val prices = rows.map { it.price }
        var low = prices.minOrNull() ?: return@Canvas
        var high = prices.maxOrNull() ?: return@Canvas
        if (previousClose.isFinite() && previousClose > 0f) {
            low = minOf(low, previousClose)
            high = maxOf(high, previousClose)
        }
        val span = high - low
        val xFor = { index: Int ->
            left + (right - left) * index / rows.lastIndex.coerceAtLeast(1).toFloat()
        }
        val yFor = { value: Float ->
            if (span <= 0.0001f) {
                (top + bottom) / 2f
            } else {
                bottom - (value - low) / span * (bottom - top)
            }
        }

        if (previousClose.isFinite() && previousClose > 0f) {
            val referenceY = yFor(previousClose)
            drawLine(
                color = Color.White.copy(alpha = 0.095f),
                start = Offset(left, referenceY),
                end = Offset(right, referenceY),
                strokeWidth = 0.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val path = Path()
        rows.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.price)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = tone.copy(alpha = 0.96f),
            style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun StockToolEntryCard(
    destination: ToolDestination,
    state: AssistantUiState,
    onClick: () -> Unit
) {
    val intensity = state.glassIntensity * if (destination == ToolDestination.StockMarket) 1.02f else 0.92f
    PressableGlass(
        quality = state.quality,
        glassIntensity = intensity,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        role = GlassRole.Card,
        onClick = onClick,
    ) {
        StockToolEntryContent(destination)
    }
}

@Composable
private fun StockToolEntryContent(destination: ToolDestination) {
    val appControl = destination == ToolDestination.AppControl
    val subtitle = if (appControl) "查看全部应用、存储、权限与内部控制" else destination.subtitle
    val available = destination.available || appControl
    Row(
        Modifier.fillMaxSize().padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(destination.title, color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (available) "进入" else "规划中", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}
