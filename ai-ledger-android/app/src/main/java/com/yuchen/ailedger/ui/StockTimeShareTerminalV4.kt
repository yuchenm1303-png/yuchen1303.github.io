package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.displayText
import kotlin.math.abs

private val TimeV4Rise = Color(0xFFFF8F8F)
private val TimeV4Fall = Color(0xFF80F7B4)
private val TimeV4Aqua = Color(0xFF8DF9EA)
private val TimeV4Yellow = Color(0xFFFFD36E)
private val TimeV4White = Color(0xFFF4F6FF)
private val TimeV4Fill = Color.White.copy(alpha = 0.040f)
private val TimeV4Border = Color.White.copy(alpha = 0.085f)
private val TimeV4Line = Color.White.copy(alpha = 0.085f)
private val TimeV4Shape = RoundedCornerShape(14.dp)
private val TimeV4Pill = RoundedCornerShape(999.dp)
private val TimePatternV4 = Regex("""(\d{1,2}):(\d{2})""")
private val DatePatternV4 = Regex("""(\d{4}-\d{2}-\d{2})""")

private const val OpenAuctionStartV4 = 9 * 60 + 15
private const val OpenAuctionEndV4 = 9 * 60 + 25
private const val ContinuousMorningStartV4 = 9 * 60 + 30
private const val MorningEndV4 = 11 * 60 + 30
private const val AfternoonStartV4 = 13 * 60
private const val CloseAuctionStartV4 = 14 * 60 + 57
private const val DayEndV4 = 15 * 60

private const val OpenAuctionWidthV4 = 0.14f
private const val MorningEndFractionV4 = 0.54f
private const val CloseAuctionStartFractionV4 = 0.95f
private const val RightPanelWidthV4 = 126

private enum class StockSessionPhaseV4 {
    OpenAuction,
    Continuous,
    CloseAuction
}

private data class PositionedMinutePointV4(
    val point: StockMinutePoint,
    val xFraction: Float,
    val daySlot: Int,
    val phase: StockSessionPhaseV4
)

private data class TimeShareLayoutV4(
    val points: List<PositionedMinutePointV4>,
    val labels: List<String>,
    val realOpenAuction: Boolean,
    val realCloseAuction: Boolean
)

internal data class StockAuctionCoverageV4(
    val openingPointCount: Int,
    val closingPointCount: Int,
    val useOpeningPriceFallback: Boolean,
    val useClosingPriceFallback: Boolean
)

@Composable
internal fun StockProfessionalTerminalV4(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onSelectTab: (String) -> Unit
) {
    var orderFlowTab by remember(ui.stock.quote.code) { mutableStateOf("五档") }
    val isFiveDay = ui.selectedTab == "五日"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("分时", "日K", "周K", "月K", "五日").forEach { tab ->
                TimeV4TabButton(
                    appState = appState,
                    text = tab,
                    active = ui.selectedTab == tab,
                    modifier = Modifier.weight(1f).height(36.dp),
                    onClick = { onSelectTab(tab) }
                )
            }
        }
        TimeV4Legend(ui, isFiveDay)
        Row(
            modifier = Modifier.fillMaxWidth().height(492.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuctionAwareTimeShareChartV4(
                stock = ui.stock,
                isFiveDay = isFiveDay,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            OrderFlowRightPanelV4(
                ui = ui,
                selectedTab = orderFlowTab,
                onSelectTab = { orderFlowTab = it },
                modifier = Modifier.width(RightPanelWidthV4.dp).fillMaxHeight()
            )
        }
        ui.requestMessage?.let {
            Text(
                it,
                color = TimeV4Yellow.copy(alpha = 0.78f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeV4Legend(ui: StockMarketUiState, isFiveDay: Boolean) {
    val quote = ui.stock.quote
    val tone = if (quote.isRising) TimeV4Rise else TimeV4Fall
    val layout = remember(ui.stock.minutePoints, isFiveDay) {
        buildTimeShareLayoutV4(ui.stock.minutePoints, isFiveDay)
    }
    val coverage = remember(
        ui.stock.minutePoints,
        quote.open,
        quote.price,
        isFiveDay
    ) {
        buildStockAuctionCoverageV4(
            points = ui.stock.minutePoints,
            openPrice = quote.open.toFloatOrNull(),
            latestPrice = quote.price.toFloatOrNull(),
            isFiveDay = isFiveDay
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (isFiveDay) "五日分时" else "分时 · 首尾集合竞价",
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            "均价 ${ui.stock.minutePoints.lastOrNull()?.average?.let(::formatPriceV4) ?: "--"}",
            color = TimeV4Yellow,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            "最新 ${quote.price}",
            color = tone,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        if (!isFiveDay) {
            val auctionText = when {
                layout.realOpenAuction && layout.realCloseAuction -> "竞价轨迹完整"
                coverage.useOpeningPriceFallback -> "开盘定价虚线"
                else -> "竞价真实点"
            }
            Text(
                auctionText,
                color = TimeV4Aqua.copy(alpha = 0.64f),
                fontSize = 8.sp,
                maxLines = 1
            )
        }
        Text(
            quote.changePercent,
            color = tone,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun AuctionAwareTimeShareChartV4(
    stock: StockDetailUiState,
    isFiveDay: Boolean,
    modifier: Modifier
) {
    val layout = remember(stock.minutePoints, isFiveDay) {
        buildTimeShareLayoutV4(stock.minutePoints, isFiveDay)
    }
    val openPrice = stock.quote.open.toFloatOrNull()?.takeIf { it > 0f }
    val latestPrice = stock.quote.price.toFloatOrNull()?.takeIf { it > 0f }
    val previousClose = stock.quote.previousClose.takeIf { it > 0f }
        ?: layout.points.firstOrNull()?.point?.price
        ?: openPrice
        ?: latestPrice
        ?: 1f
    val values = remember(layout.points, openPrice, latestPrice, previousClose) {
        buildList {
            layout.points.forEach { positioned ->
                add(positioned.point.price)
                if (positioned.phase == StockSessionPhaseV4.Continuous) {
                    add(positioned.point.average)
                }
            }
            openPrice?.let(::add)
            latestPrice?.let(::add)
            add(previousClose)
        }
    }

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val width = size.width
            val height = size.height
            val chartHeight = height * 0.77f
            val volumeTop = chartHeight + 7.dp.toPx()
            val volumeHeight = (height - volumeTop).coerceAtLeast(1f)

            repeat(4) { index ->
                val y = chartHeight * (index + 1) / 5f
                drawLine(
                    Color.White.copy(alpha = 0.10f),
                    Offset(0f, y),
                    Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (isFiveDay) {
                repeat(4) { index ->
                    val x = width * (index + 1) / 5f
                    drawLine(
                        Color.White.copy(alpha = 0.085f),
                        Offset(x, 0f),
                        Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            } else {
                val openEndX = width * OpenAuctionWidthV4
                val lunchX = width * MorningEndFractionV4
                val closeStartX = width * CloseAuctionStartFractionV4
                drawRect(
                    TimeV4White.copy(alpha = 0.050f),
                    topLeft = Offset.Zero,
                    size = Size(openEndX, chartHeight)
                )
                drawRect(
                    TimeV4Aqua.copy(alpha = 0.050f),
                    topLeft = Offset(closeStartX, 0f),
                    size = Size(width - closeStartX, chartHeight)
                )
                listOf(openEndX, lunchX, closeStartX).forEach { x ->
                    drawLine(
                        Color.White.copy(alpha = 0.13f),
                        Offset(x, 0f),
                        Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            if (values.isEmpty()) return@Canvas
            val rawMin = values.minOrNull() ?: return@Canvas
            val rawMax = values.maxOrNull() ?: return@Canvas
            val minValue: Float
            val maxValue: Float
            if (isFiveDay) {
                val padding = maxOf((rawMax - rawMin) * 0.08f, rawMax * 0.002f, 0.01f)
                minValue = rawMin - padding
                maxValue = rawMax + padding
            } else {
                val halfRange = maxOf(
                    values.maxOf { abs(it - previousClose) },
                    previousClose * 0.003f,
                    0.01f
                ) * 1.08f
                minValue = previousClose - halfRange
                maxValue = previousClose + halfRange
            }
            val range = (maxValue - minValue).coerceAtLeast(0.0001f)
            fun yFor(value: Float): Float =
                chartHeight - ((value - minValue) / range).coerceIn(0f, 1f) * chartHeight

            val maxVolume = layout.points.maxOfOrNull { it.point.volumeRatio }
                ?.takeIf { it > 0f } ?: 1f
            val nominalSlots = if (isFiveDay) 1250 else 250
            val barWidth = (width / nominalSlots * 0.82f)
                .coerceIn(0.65.dp.toPx(), 2.6.dp.toPx())
            layout.points.forEach { positioned ->
                val x = positioned.xFraction.coerceIn(0f, 1f) * width
                val top = volumeTop + volumeHeight *
                    (1f - (positioned.point.volumeRatio / maxVolume).coerceIn(0f, 1f))
                val volumeTone = if (positioned.point.price >= previousClose) TimeV4Rise else TimeV4Fall
                drawLine(
                    volumeTone.copy(alpha = 0.26f),
                    Offset(x, height),
                    Offset(x, top),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Butt
                )
            }

            if (!isFiveDay) {
                val previousCloseY = yFor(previousClose)
                drawLine(
                    Color.White.copy(alpha = 0.20f),
                    Offset(0f, previousCloseY),
                    Offset(width, previousCloseY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                )
            }

            fun drawContinuousPath(
                points: List<PositionedMinutePointV4>,
                selector: (StockMinutePoint) -> Float,
                color: Color,
                strokeWidth: Float
            ) {
                if (points.size < 2) return
                val path = Path()
                var started = false
                var previousDay = -1
                points.forEach { positioned ->
                    val x = positioned.xFraction.coerceIn(0f, 1f) * width
                    val y = yFor(selector(positioned.point))
                    if (!started || positioned.daySlot != previousDay) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                    previousDay = positioned.daySlot
                }
                drawPath(
                    path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            fun drawStepAuctionPath(
                points: List<PositionedMinutePointV4>,
                color: Color
            ) {
                if (points.isEmpty()) return
                val path = Path()
                var previousX = 0f
                var previousY = 0f
                var started = false
                var previousDay = -1
                points.forEach { positioned ->
                    val x = positioned.xFraction.coerceIn(0f, 1f) * width
                    val y = yFor(positioned.point.price)
                    if (!started || positioned.daySlot != previousDay) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, previousY)
                        path.lineTo(x, y)
                    }
                    previousX = x
                    previousY = y
                    previousDay = positioned.daySlot
                }
                if (previousX >= 0f) {
                    drawPath(
                        path,
                        color = color,
                        style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Square)
                    )
                }
            }

            val openAuctionPoints = layout.points.filter { it.phase == StockSessionPhaseV4.OpenAuction }
            val continuousPoints = layout.points.filter { it.phase == StockSessionPhaseV4.Continuous }
            val closeAuctionPoints = layout.points.filter { it.phase == StockSessionPhaseV4.CloseAuction }

            drawContinuousPath(
                continuousPoints,
                selector = { it.average },
                color = TimeV4Yellow.copy(alpha = 0.88f),
                strokeWidth = 1.55.dp.toPx()
            )
            drawContinuousPath(
                continuousPoints,
                selector = { it.price },
                color = if (stock.quote.isRising) TimeV4Rise else TimeV4Fall,
                strokeWidth = 2.35.dp.toPx()
            )
            drawStepAuctionPath(openAuctionPoints, TimeV4White.copy(alpha = 0.96f))
            drawStepAuctionPath(closeAuctionPoints, TimeV4Aqua.copy(alpha = 0.96f))

            if (!isFiveDay && openAuctionPoints.isEmpty() && openPrice != null) {
                val openY = yFor(openPrice)
                val openEndX = width * OpenAuctionWidthV4
                drawLine(
                    TimeV4White.copy(alpha = 0.82f),
                    Offset(0f, openY),
                    Offset(openEndX, openY),
                    strokeWidth = 1.7.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                )
                drawCircle(
                    color = TimeV4White,
                    radius = 2.6.dp.toPx(),
                    center = Offset(openEndX, openY)
                )
            }
            if (!isFiveDay && closeAuctionPoints.isEmpty() && latestPrice != null) {
                val closeY = yFor(latestPrice)
                val closeStartX = width * CloseAuctionStartFractionV4
                drawLine(
                    TimeV4Aqua.copy(alpha = 0.82f),
                    Offset(closeStartX, closeY),
                    Offset(width, closeY),
                    strokeWidth = 1.7.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                )
                drawCircle(
                    color = TimeV4Aqua,
                    radius = 2.6.dp.toPx(),
                    center = Offset(width, closeY)
                )
            }
        }
        TimeV4AxisLabels(layout.labels)
        if (!isFiveDay) {
            Row(
                modifier = Modifier.fillMaxWidth().height(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (layout.realOpenAuction) "白线：开盘竞价匹配价" else "白色虚线：真实开盘定价",
                    color = TimeV4White.copy(alpha = 0.48f),
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (layout.realCloseAuction) "青线：尾盘竞价" else "青色虚线：收盘定价",
                    color = TimeV4Aqua.copy(alpha = 0.52f),
                    fontSize = 7.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TimeV4AxisLabels(labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            Text(
                label,
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 7.sp,
                lineHeight = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OrderFlowRightPanelV4(
    ui: StockMarketUiState,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(TimeV4Fill, TimeV4Shape)
            .border(1.dp, TimeV4Border, TimeV4Shape)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("五档", "逐笔").forEach { tab ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(
                            Color.White.copy(alpha = if (selectedTab == tab) 0.16f else 0.045f),
                            TimeV4Pill
                        )
                        .clickable { onSelectTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab,
                        color = Color.White.copy(alpha = if (selectedTab == tab) 0.96f else 0.54f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        RightPanelStatusV4(ui, selectedTab)
        Box(Modifier.fillMaxWidth().height(1.dp).background(TimeV4Line))
        when (selectedTab) {
            "逐笔" -> RightTradeTapeV4(ui.stock.tradeTicks, Modifier.fillMaxSize())
            else -> RightFiveLevelDepthV4(ui.stock.sellLevels, ui.stock.buyLevels, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RightPanelStatusV4(ui: StockMarketUiState, selectedTab: String) {
    val status = if (selectedTab == "五档") ui.depthState.status else {
        if (ui.stock.tradeTicks.isNotEmpty()) StockModuleStatus.Ok else StockModuleStatus.Unavailable
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (selectedTab == "五档") "盘口" else "明细",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            status.displayText(),
            color = when (status) {
                StockModuleStatus.Ok -> TimeV4Aqua
                StockModuleStatus.Partial, StockModuleStatus.Stale -> TimeV4Yellow
                else -> Color.White.copy(alpha = 0.38f)
            },
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun RightFiveLevelDepthV4(
    sellLevels: List<StockOrderLevel>,
    buyLevels: List<StockOrderLevel>,
    modifier: Modifier
) {
    val asks = sellLevels.take(5).sortedByDescending { it.price.toFloatOrNull() ?: 0f }
    val bids = buyLevels.take(5).sortedByDescending { it.price.toFloatOrNull() ?: 0f }
    if (asks.isEmpty() && bids.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "真实五档\n暂不可用",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 8.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            asks.forEach { level -> RightDepthRowV4(level, true) }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(TimeV4Aqua.copy(alpha = 0.55f)))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            bids.forEach { level -> RightDepthRowV4(level, false) }
        }
    }
}

@Composable
private fun RightDepthRowV4(level: StockOrderLevel, isAsk: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            level.label,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 7.sp,
            modifier = Modifier.width(22.dp),
            maxLines = 1
        )
        Text(
            level.price,
            color = if (isAsk) TimeV4Rise else TimeV4Fall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Text(
            compactVolumeV4(level.volume),
            color = Color.White.copy(alpha = 0.66f),
            fontSize = 7.sp,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RightTradeTapeV4(
    ticks: List<StockTradeTick>,
    modifier: Modifier
) {
    val visible = ticks.takeLast(12)
    if (visible.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "真实逐笔\n暂不可用",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 8.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    AnimatedContent(
        targetState = visible,
        modifier = modifier,
        transitionSpec = {
            (
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 5 }
                ) + fadeIn(tween(90))
                ).togetherWith(
                slideOutVertically(tween(130), targetOffsetY = { -it / 5 }) + fadeOut(tween(80))
            )
        },
        label = "stock-right-trade-tape-v4"
    ) { animatedTicks ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            animatedTicks.forEach { tick ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tick.time.takeLast(5),
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 7.sp,
                        modifier = Modifier.width(31.dp),
                        maxLines = 1
                    )
                    Text(
                        tick.price,
                        color = if (tick.isBuy) TimeV4Rise else TimeV4Fall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Text(
                        compactVolumeV4(tick.volume),
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 7.sp,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeV4TabButton(
    appState: AssistantUiState,
    text: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    Box(
        modifier = modifier
            .background(
                Color.White.copy(alpha = (if (active) 0.20f else 0.07f) * intensity),
                TimeV4Pill
            )
            .border(
                1.dp,
                (if (active) TimeV4Aqua else Color.White).copy(alpha = if (active) 0.18f else 0.06f),
                TimeV4Pill
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.68f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

internal fun buildStockAuctionCoverageV4(
    points: List<StockMinutePoint>,
    openPrice: Float?,
    latestPrice: Float?,
    isFiveDay: Boolean
): StockAuctionCoverageV4 {
    if (isFiveDay) {
        return StockAuctionCoverageV4(0, 0, false, false)
    }
    val latestDate = points.mapNotNull { extractDateV4(it.time) }.maxOrNull()
    val current = if (latestDate != null) {
        points.filter { extractDateV4(it.time) == latestDate }
    } else {
        points
    }
    val openingCount = current.count {
        minuteOfV4(it.time) in OpenAuctionStartV4..OpenAuctionEndV4
    }
    val closingCount = current.count {
        minuteOfV4(it.time) in CloseAuctionStartV4..DayEndV4
    }
    return StockAuctionCoverageV4(
        openingPointCount = openingCount,
        closingPointCount = closingCount,
        useOpeningPriceFallback = openingCount == 0 && openPrice != null && openPrice > 0f,
        useClosingPriceFallback = closingCount == 0 && latestPrice != null && latestPrice > 0f
    )
}

private fun buildTimeShareLayoutV4(
    points: List<StockMinutePoint>,
    isFiveDay: Boolean
): TimeShareLayoutV4 {
    val labels = if (isFiveDay) List(5) { "--" } else {
        listOf("09:15", "09:25", "11:30/13:00", "14:57", "15:00")
    }
    if (points.isEmpty()) return TimeShareLayoutV4(emptyList(), labels, false, false)

    val dates = points.mapNotNull { extractDateV4(it.time) }
    if (!isFiveDay) {
        val latestDate = dates.maxOrNull()
        val current = if (latestDate != null) {
            points.filter { extractDateV4(it.time) == latestDate }
        } else {
            points
        }
        val positioned = current.mapNotNull { point ->
            val minute = minuteOfV4(point.time) ?: return@mapNotNull null
            val phase = sessionPhaseV4(minute) ?: return@mapNotNull null
            PositionedMinutePointV4(
                point = point,
                xFraction = sessionXFractionV4(minute),
                daySlot = 0,
                phase = phase
            )
        }.sortedBy { it.xFraction }
        return TimeShareLayoutV4(
            points = positioned,
            labels = labels,
            realOpenAuction = positioned.any { it.phase == StockSessionPhaseV4.OpenAuction },
            realCloseAuction = positioned.any { it.phase == StockSessionPhaseV4.CloseAuction }
        )
    }

    val latestDates = dates.distinct().sorted().takeLast(5)
    val firstSlot = (5 - latestDates.size).coerceAtLeast(0)
    val dateSlots = latestDates.mapIndexed { index, date -> date to firstSlot + index }.toMap()
    val positioned = points.mapNotNull { point ->
        val minute = minuteOfV4(point.time) ?: return@mapNotNull null
        val phase = sessionPhaseV4(minute) ?: return@mapNotNull null
        val slot = extractDateV4(point.time)?.let(dateSlots::get) ?: 4
        val localX = sessionXFractionV4(minute)
        PositionedMinutePointV4(
            point = point,
            xFraction = (slot + localX) / 5f,
            daySlot = slot,
            phase = phase
        )
    }.sortedBy { it.xFraction }
    val dateLabels = MutableList(5) { "--" }
    dateSlots.forEach { (date, slot) -> dateLabels[slot] = date.takeLast(5) }
    return TimeShareLayoutV4(
        points = positioned,
        labels = dateLabels,
        realOpenAuction = positioned.any { it.phase == StockSessionPhaseV4.OpenAuction },
        realCloseAuction = positioned.any { it.phase == StockSessionPhaseV4.CloseAuction }
    )
}

internal fun sessionXFractionV4(minute: Int): Float = when (minute) {
    in OpenAuctionStartV4..OpenAuctionEndV4 -> {
        val progress = (minute - OpenAuctionStartV4).toFloat() /
            (OpenAuctionEndV4 - OpenAuctionStartV4).coerceAtLeast(1)
        progress * OpenAuctionWidthV4
    }
    in ContinuousMorningStartV4..MorningEndV4 -> {
        val progress = (minute - ContinuousMorningStartV4).toFloat() /
            (MorningEndV4 - ContinuousMorningStartV4).coerceAtLeast(1)
        OpenAuctionWidthV4 + progress * (MorningEndFractionV4 - OpenAuctionWidthV4)
    }
    in AfternoonStartV4 until CloseAuctionStartV4 -> {
        val progress = (minute - AfternoonStartV4).toFloat() /
            (CloseAuctionStartV4 - AfternoonStartV4).coerceAtLeast(1)
        MorningEndFractionV4 + progress * (CloseAuctionStartFractionV4 - MorningEndFractionV4)
    }
    in CloseAuctionStartV4..DayEndV4 -> {
        val progress = (minute - CloseAuctionStartV4).toFloat() /
            (DayEndV4 - CloseAuctionStartV4).coerceAtLeast(1)
        CloseAuctionStartFractionV4 + progress * (1f - CloseAuctionStartFractionV4)
    }
    else -> 0f
}.coerceIn(0f, 1f)

private fun sessionPhaseV4(minute: Int): StockSessionPhaseV4? = when (minute) {
    in OpenAuctionStartV4..OpenAuctionEndV4 -> StockSessionPhaseV4.OpenAuction
    in ContinuousMorningStartV4..MorningEndV4,
    in AfternoonStartV4 until CloseAuctionStartV4 -> StockSessionPhaseV4.Continuous
    in CloseAuctionStartV4..DayEndV4 -> StockSessionPhaseV4.CloseAuction
    else -> null
}

private fun minuteOfV4(raw: String): Int? {
    val match = TimePatternV4.find(raw) ?: return null
    val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun extractDateV4(raw: String): String? =
    DatePatternV4.find(raw)?.groupValues?.getOrNull(1)

private fun formatPriceV4(value: Float): String = String.format("%.2f", value)

private fun compactVolumeV4(raw: String): String {
    if (raw.isBlank() || raw == "--") return "--"
    val clean = raw.replace(",", "").trim()
    if (clean.endsWith("万手")) return clean.removeSuffix("手")
    val number = clean.toFloatOrNull() ?: return clean.take(7)
    return when {
        number >= 10000f -> String.format("%.1f万", number / 10000f)
        else -> number.toInt().toString()
    }
}
