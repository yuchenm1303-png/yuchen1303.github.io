package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.ToolDestination
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

const val STOCK_MARKET_TOOL_TITLE = "股票行情"

private val DashboardBlue = Color(0xFF8FB2FF)
private val DashboardViolet = Color(0xFFB49BFF)
private val DashboardMint = Color(0xFF7BE8D2)
private val DashboardWarm = Color(0xFFFFC58A)

private enum class DashboardArtIcon {
    Statistics,
    AppControl,
    OperationLearning,
}

@Composable
fun StockFirstToolsHomeScreen(
    state: AssistantUiState,
    onOpenTool: (ToolDestination) -> Unit,
    onCloseTool: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    val pageVisible = LocalPageVisible.current
    val pageState = if (pageVisible) state else remember(pageVisible) {
        state.copy(motionIntensity = 0f)
    }
    val selectedTool = pageState.selectedTool
    val heroViewModel: ToolsMarketHeroViewModel = viewModel()
    val heroUi by heroViewModel.uiState.collectAsState()
    val heroVisible = pageVisible && selectedTool == null

    LaunchedEffect(heroVisible) { heroViewModel.setVisible(heroVisible) }
    DisposableEffect(heroViewModel) {
        onDispose { heroViewModel.setVisible(false) }
    }

    when (selectedTool) {
        ToolDestination.LedgerCenter -> {
            val ledgerViewModel: LedgerViewModel = viewModel()
            GlassSceneScope(GlassSceneGroup.LedgerCenterPage) {
                NativeLedgerCenterScreen(
                    appState = pageState,
                    ledgerViewModel = ledgerViewModel,
                    statisticsOnly = false,
                    onBack = onCloseTool,
                    onOpenAssistant = onOpenAssistant,
                )
            }
            return
        }

        ToolDestination.Statistics -> {
            GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
                AgentAnalyticsRoute(
                    appState = pageState,
                    onBack = onCloseTool,
                )
            }
            return
        }

        ToolDestination.Reminder -> {
            GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
                PlanCenterScreen(state = pageState, onBack = onCloseTool)
            }
            return
        }

        ToolDestination.AppControl -> {
            AppManagementScreen(state = pageState, onBack = onCloseTool)
            return
        }

        ToolDestination.StorageManagement -> {
            StorageManagementPhaseThreeHubScreen(state = pageState, onBack = onCloseTool)
            return
        }

        ToolDestination.Shortcuts -> {
            GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
                OperationLearningScreen(state = pageState, onBack = onCloseTool)
            }
            return
        }

        null,
        ToolDestination.StockMarket -> Unit

        else -> {
            PendingToolScreen(selectedTool, pageState, onCloseTool)
            return
        }
    }

    val ledgerViewModel: LedgerViewModel = viewModel()
    val planViewModel: PlanCenterViewModel = viewModel()
    val operationViewModel: OperationLearningViewModel = viewModel()
    val ledgerState = ledgerViewModel.state
    val planState = planViewModel.uiState
    val operationState = operationViewModel.uiState
    val deviceSummary = rememberDeviceToolsSummary(pageVisible)

    val monthKey = remember(ledgerState.records) { LedgerStore.todayIso().take(7) }
    val monthRecords = remember(ledgerState.records, monthKey) {
        ledgerState.records.filter {
            LedgerStore.normalizeDate(it.dateLabel).startsWith(monthKey)
        }
    }
    val monthExpense = remember(monthRecords) {
        monthRecords.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }
    }
    val budget = ledgerState.budgetText.toDoubleOrNull() ?: 0.0
    val budgetRemaining = (budget - monthExpense).coerceAtLeast(0.0)
    val nextPlan = remember(planState.tasks) {
        val now = System.currentTimeMillis()
        planState.tasks.firstOrNull { task ->
            task.enabled && task.nextRunAtMillis?.let { it >= now } == true
        }
    }
    val latestWorkflow = remember(operationState.drafts) {
        operationState.drafts.maxByOrNull { it.updatedAtMillis }
    }

    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                ToolsEntrance(0, initialOffsetY = -5, initialScale = 0.99f) {
                    StockToolsHeader()
                }
            }
            item {
                ToolsEntrance(70, initialOffsetY = 12, initialScale = 0.982f) {
                    StockMarketHeroEntry(pageState, heroUi, onOpenTool)
                }
            }
            item {
                ToolsEntrance(130, initialOffsetY = 13, initialScale = 0.985f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LedgerSummaryCard(
                            state = pageState,
                            monthExpense = monthExpense,
                            budgetRemaining = budgetRemaining,
                            modifier = Modifier.weight(1f).height(148.dp),
                            onClick = { onOpenTool(ToolDestination.LedgerCenter) },
                        )
                        StatisticsSummaryCard(
                            state = pageState,
                            modifier = Modifier.weight(1f).height(148.dp),
                            onClick = { onOpenTool(ToolDestination.Statistics) },
                        )
                    }
                }
            }
            item {
                ToolsEntrance(185, initialOffsetY = 13, initialScale = 0.987f) {
                    PlanSummaryCard(
                        state = pageState,
                        title = nextPlan?.title,
                        nextRunAtMillis = nextPlan?.nextRunAtMillis,
                        activeCount = planState.tasks.count { it.enabled && !it.isFinished },
                        onClick = { onOpenTool(ToolDestination.Reminder) },
                    )
                }
            }
            item {
                ToolsEntrance(240, initialOffsetY = 13, initialScale = 0.985f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppControlSummaryCard(
                            state = pageState,
                            installedApps = deviceSummary.installedApps,
                            userApps = deviceSummary.userApps,
                            appIcons = deviceSummary.appIcons,
                            loaded = deviceSummary.loaded,
                            modifier = Modifier.weight(1f).height(160.dp),
                            onClick = { onOpenTool(ToolDestination.AppControl) },
                        )
                        StorageSummaryCard(
                            state = pageState,
                            usedBytes = deviceSummary.usedBytes,
                            totalBytes = deviceSummary.totalBytes,
                            loaded = deviceSummary.loaded,
                            modifier = Modifier.weight(1f).height(160.dp),
                            onClick = { onOpenTool(ToolDestination.StorageManagement) },
                        )
                    }
                }
            }
            item {
                ToolsEntrance(295, initialOffsetY = 13, initialScale = 0.987f) {
                    OperationLearningSummaryCard(
                        state = pageState,
                        title = latestWorkflow?.title,
                        status = workflowStatusLabel(latestWorkflow?.status?.name),
                        draftCount = operationState.drafts.size,
                        onClick = { onOpenTool(ToolDestination.Shortcuts) },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDeviceToolsSummary(active: Boolean): ToolsHomeDeviceSummary {
    val context = LocalContext.current.applicationContext
    val summary by ToolsHomeDeviceSummaryStore.state.collectAsState()

    LaunchedEffect(context, active) {
        if (active) ToolsHomeDeviceSummaryStore.request(context)
    }
    return summary
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
                        Text(
                            "‹ 返回",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "TOOLS",
                        color = DashboardMint.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        destination.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        destination.subtitle,
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            item {
                FrostInfoGlassPanel(
                    radius = 17.44f,
                    backdropAlpha = 1f,
                    frostAlpha = 0.09f,
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
                        Text(
                            "功能正在建设",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "入口已经纳入统一路由，后续会按独立功能批次完成。",
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
    initialOffsetY: Int = 18,
    initialScale: Float = 0.982f,
    content: @Composable () -> Unit,
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
            if (pageLeaving && delayMs > 0L) {
                delay((delayMs / 18L).coerceAtMost(28L))
            }
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            ) { initialOffsetY } +
            scaleIn(
                initialScale = initialScale,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = fadeOut(tween(96)) +
            slideOutVertically(tween(108)) {
                (-initialOffsetY / 4).coerceIn(-8, 8)
            } + scaleOut(targetScale = 0.992f, animationSpec = tween(112)),
    ) {
        content()
    }
}

@Composable
private fun StockToolsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "功能",
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "重要信息与常用能力，一眼就能找到。",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StockMarketHeroEntry(
    state: AssistantUiState,
    heroUi: ToolsMarketHeroUiState,
    onOpenTool: (ToolDestination) -> Unit,
) {
    val statusLabel = when {
        heroUi.loading -> "同步中"
        heroUi.indices.any { it.hasRealQuote } -> "实时更新"
        !heroUi.errorMessage.isNullOrBlank() -> "待恢复"
        else -> "等待数据"
    }

    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.03f,
        motionIntensity = state.motionIntensity,
        radius = 29,
        modifier = Modifier.fillMaxWidth().height(236.dp),
        mood = OpenGlShellMood.Hero,
        onClick = { onOpenTool(ToolDestination.StockMarket) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ToolDestination.StockMarket.title,
                        color = Color.White,
                        fontSize = 27.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(DashboardMint.copy(alpha = 0.11f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "●  $statusLabel",
                            color = DashboardMint.copy(
                                alpha = if (heroUi.loading) 0.58f else 0.92f,
                            ),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    "三大指数、真实分时、热榜、板块和资金流",
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(108.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                heroUi.indices.take(3).forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.72f)
                                .background(Color.White.copy(alpha = 0.09f)),
                        )
                    }
                    StockHeroIndexMetric(
                        item = item,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StockHeroIndexMetric(
    item: ToolsMarketIndexItem,
    modifier: Modifier = Modifier,
) {
    val tone = when {
        !item.hasRealQuote -> Color.White.copy(alpha = 0.44f)
        item.isRising -> StockRise
        else -> StockFall
    }
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            item.name,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.price.ifBlank { "--" },
            color = Color.White.copy(alpha = if (item.hasRealQuote) 0.96f else 0.54f),
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            heroIndexChangeText(item),
            color = tone.copy(alpha = if (item.hasRealQuote) 0.92f else 0.6f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StockHeroSparkline(
            points = item.minutePoints,
            previousClose = item.previousClose,
            tone = tone,
            modifier = Modifier.fillMaxWidth().height(28.dp),
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
    modifier: Modifier = Modifier,
) {
    val rows = remember(points) {
        points.filter { it.price.isFinite() && it.price > 0f }
    }
    Canvas(modifier) {
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
                cap = StrokeCap.Round,
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
        fun xFor(index: Int): Float =
            left + (right - left) * index / rows.lastIndex.coerceAtLeast(1).toFloat()
        fun yFor(value: Float): Float = if (span <= 0.0001f) {
            (top + bottom) / 2f
        } else {
            bottom - (value - low) / span * (bottom - top)
        }

        if (previousClose.isFinite() && previousClose > 0f) {
            val referenceY = yFor(previousClose)
            drawLine(
                color = Color.White.copy(alpha = 0.095f),
                start = Offset(left, referenceY),
                end = Offset(right, referenceY),
                strokeWidth = 0.8.dp.toPx(),
                cap = StrokeCap.Round,
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
            style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun DashboardShellCard(
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.96f,
        motionIntensity = state.motionIntensity,
        radius = 25,
        modifier = modifier,
        mood = OpenGlShellMood.Hero,
        onClick = onClick,
    ) {
        content()
    }
}

@Composable
private fun DashboardIcon(
    symbol: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = tone.copy(alpha = 0.96f),
            fontSize = 17.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DashboardArtIcon(
    art: DashboardArtIcon,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 1.65.dp.toPx()
            when (art) {
                DashboardArtIcon.Statistics -> {
                    val barWidth = 3.4.dp.toPx()
                    val bottom = size.height * 0.84f
                    val heights = listOf(0.34f, 0.57f, 0.82f)
                    val xPositions = listOf(0.16f, 0.42f, 0.68f)
                    heights.forEachIndexed { index, ratio ->
                        val barHeight = size.height * ratio
                        drawRoundRect(
                            color = tone.copy(alpha = 0.55f + index * 0.16f),
                            topLeft = Offset(size.width * xPositions[index], bottom - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth, barWidth),
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 1.7.dp.toPx(),
                        center = Offset(size.width * 0.79f, size.height * 0.14f),
                    )
                }

                DashboardArtIcon.AppControl -> {
                    val cell = 6.2.dp.toPx()
                    val radius = CornerRadius(2.2.dp.toPx(), 2.2.dp.toPx())
                    val left = size.width * 0.16f
                    val top = size.height * 0.16f
                    val gap = 3.2.dp.toPx()
                    drawRoundRect(
                        color = tone.copy(alpha = 0.92f),
                        topLeft = Offset(left, top),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = tone.copy(alpha = 0.56f),
                        topLeft = Offset(left + cell + gap, top),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = tone.copy(alpha = 0.56f),
                        topLeft = Offset(left, top + cell + gap),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                    )
                    val controlCenter = Offset(
                        left + cell + gap + cell / 2f,
                        top + cell + gap + cell / 2f,
                    )
                    drawCircle(
                        color = tone.copy(alpha = 0.95f),
                        radius = cell * 0.48f,
                        center = controlCenter,
                        style = Stroke(stroke),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 1.45.dp.toPx(),
                        center = controlCenter,
                    )
                }

                DashboardArtIcon.OperationLearning -> {
                    val start = Offset(size.width * 0.13f, size.height * 0.72f)
                    val middle = Offset(size.width * 0.5f, size.height * 0.28f)
                    val end = Offset(size.width * 0.87f, size.height * 0.72f)
                    val route = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            size.width * 0.31f,
                            size.height * 0.72f,
                            size.width * 0.29f,
                            size.height * 0.28f,
                            middle.x,
                            middle.y,
                        )
                        cubicTo(
                            size.width * 0.71f,
                            size.height * 0.28f,
                            size.width * 0.69f,
                            size.height * 0.72f,
                            end.x,
                            end.y,
                        )
                    }
                    drawPath(
                        route,
                        tone.copy(alpha = 0.76f),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    listOf(start, middle, end).forEachIndexed { index, point ->
                        drawCircle(
                            color = tone.copy(alpha = if (index == 1) 0.98f else 0.74f),
                            radius = if (index == 1) 3.1.dp.toPx() else 2.5.dp.toPx(),
                            center = point,
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.94f),
                        radius = 1.25.dp.toPx(),
                        center = middle,
                    )
                    drawLine(
                        color = tone.copy(alpha = 0.82f),
                        start = Offset(end.x - 2.9.dp.toPx(), end.y - 2.4.dp.toPx()),
                        end = end,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCardHeader(
    symbol: String,
    title: String,
    tone: Color,
    art: DashboardArtIcon? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (art == null) DashboardIcon(symbol, tone) else DashboardArtIcon(art, tone)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "›",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 28.sp,
            lineHeight = 28.sp,
        )
    }
}

@Composable
private fun LedgerSummaryCard(
    state: AssistantUiState,
    monthExpense: Double,
    budgetRemaining: Double,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    DashboardShellCard(state, modifier, onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardCardHeader("¥", "账单中心", DashboardBlue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                SummaryValue(
                    "本月支出",
                    if (monthExpense > 0.0) "¥${formatMoney(monthExpense)}" else "暂无支出",
                    Modifier.weight(1f),
                )
                Box(
                    Modifier.width(1.dp).height(35.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                SummaryValue(
                    "预算剩余",
                    if (budgetRemaining > 0.0) "¥${formatMoney(budgetRemaining)}" else "未设置",
                    Modifier.weight(1f).padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StatisticsSummaryCard(
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    DashboardShellCard(state, modifier, onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardCardHeader(
                symbol = "",
                title = "智能体统计",
                tone = DashboardViolet,
                art = DashboardArtIcon.Statistics,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(0.78f)) {
                    Text(
                        "活动档案",
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        "Token · 任务 · 能力",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                    )
                    Text(
                        "按需读取，不增加首页负载",
                        color = DashboardMint.copy(alpha = 0.74f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MiniBarChart(
                    values = listOf(0.18f, 0.42f, 0.28f, 0.64f, 0.48f, 0.82f, 0.58f),
                    tone = DashboardViolet,
                    modifier = Modifier.weight(1f).height(49.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(
    state: AssistantUiState,
    title: String?,
    nextRunAtMillis: Long?,
    activeCount: Int,
    onClick: () -> Unit,
) {
    DashboardShellCard(
        state,
        Modifier.fillMaxWidth().height(102.dp),
        onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardIcon("✓", DashboardViolet)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "计划",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (title.isNullOrBlank()) "还没有创建计划" else "下一项：$title",
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$activeCount 项进行中",
                    color = DashboardViolet.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                nextRunAtMillis?.let(::formatPlanTime) ?: "去创建",
                color = DashboardBlue.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(10.dp))
            Text("›", color = Color.White.copy(alpha = 0.42f), fontSize = 28.sp)
        }
    }
}

@Composable
private fun AppControlSummaryCard(
    state: AssistantUiState,
    installedApps: Int,
    userApps: Int,
    appIcons: List<Bitmap>,
    loaded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    DashboardShellCard(state, modifier, onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardCardHeader(
                symbol = "",
                title = "应用控制",
                tone = DashboardMint,
                art = DashboardArtIcon.AppControl,
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (loaded) "$installedApps 个应用" else "正在读取应用",
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (loaded) "其中 $userApps 个用户应用" else "仅在进入功能页时读取一次",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RealAppIconPreview(appIcons, loading = !loaded)
            }
        }
    }
}

@Composable
private fun RealAppIconPreview(
    appIcons: List<Bitmap>,
    loading: Boolean,
) {
    val visibleCount = if (loading || appIcons.isEmpty()) 4 else appIcons.size.coerceAtMost(4)
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(38.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val gap = 5.dp
        val availableIconWidth = if (visibleCount > 0) {
            (maxWidth - gap * (visibleCount - 1).toFloat()) / visibleCount.toFloat()
        } else {
            0.dp
        }
        val iconSize = availableIconWidth.coerceAtMost(36.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(visibleCount) { index ->
                val icon = appIcons.getOrNull(index)
                val iconModifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = if (icon == null) 0.07f else 0.035f))
                if (icon == null) {
                    Box(iconModifier)
                } else {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = iconModifier,
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(
    state: AssistantUiState,
    usedBytes: Long,
    totalBytes: Long,
    loaded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ratio = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    DashboardShellCard(state, modifier, onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardCardHeader("◉", "存储管理", DashboardBlue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "已用空间",
                        color = Color.White.copy(alpha = 0.47f),
                        fontSize = 10.5.sp,
                    )
                    Text(
                        if (loaded && totalBytes > 0L) formatStorage(usedBytes) else "正在读取",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (loaded && totalBytes > 0L) "共 ${formatStorage(totalBytes)}" else "设备容量",
                        color = Color.White.copy(alpha = 0.43f),
                        fontSize = 9.5.sp,
                    )
                }
                StorageRing(ratio, Modifier.size(58.dp))
            }
        }
    }
}

@Composable
private fun OperationLearningSummaryCard(
    state: AssistantUiState,
    title: String?,
    status: String,
    draftCount: Int,
    onClick: () -> Unit,
) {
    DashboardShellCard(
        state,
        Modifier.fillMaxWidth().height(108.dp),
        onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardArtIcon(DashboardArtIcon.OperationLearning, DashboardViolet)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "操作学习",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (title.isNullOrBlank()) {
                        "演示一次操作，生成可审核流程"
                    } else {
                        "最近流程：$title"
                    },
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$draftCount 个流程草稿",
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 9.5.sp,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("状态", color = Color.White.copy(alpha = 0.38f), fontSize = 9.5.sp)
                Text(
                    status,
                    color = DashboardBlue.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text("›", color = Color.White.copy(alpha = 0.42f), fontSize = 28.sp)
        }
    }
}

@Composable
private fun SummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 9.5.sp)
        Text(
            value,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniBarChart(
    values: List<Float>,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size)
            .coerceAtLeast(2.dp.toPx())
        values.forEachIndexed { index, value ->
            val normalized = value.coerceIn(0.08f, 1f)
            val barHeight = size.height * normalized
            drawRoundRect(
                color = tone.copy(alpha = 0.3f + normalized * 0.45f),
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun StorageRing(
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = DashboardBlue.copy(alpha = 0.94f),
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "${(ratio * 100f).toInt()}%",
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private fun formatMoney(value: Double): String = DecimalFormat("#,##0.##").format(value)

private fun formatStorage(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return "${DecimalFormat("0.#").format(gib)}GB"
}

private fun formatPlanTime(value: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(value))

private fun workflowStatusLabel(statusName: String?): String = when (statusName) {
    "Intent" -> "待演示"
    "Recording" -> "演示中"
    "Compiling" -> "生成中"
    "ReadyForReview" -> "待审核"
    "Approved" -> "已批准"
    "Verified" -> "已验证"
    else -> "可开始"
}
