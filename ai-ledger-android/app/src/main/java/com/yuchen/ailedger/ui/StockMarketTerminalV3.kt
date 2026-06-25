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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockTradeTick
import com.yuchen.ailedger.model.displayText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TerminalRise = Color(0xFFFF8F8F)
private val TerminalFall = Color(0xFF80F7B4)
private val TerminalAqua = Color(0xFF8DF9EA)
private val TerminalYellow = Color(0xFFFFD36E)
private val TerminalBlue = Color(0xFF9DCBFF)
private val TerminalPurple = Color(0xFFC7A6FF)
private val TerminalLine = Color.White.copy(alpha = 0.085f)
private val TerminalFill = Color.White.copy(alpha = 0.045f)
private val TerminalBorder = Color.White.copy(alpha = 0.09f)
private val TerminalShape = RoundedCornerShape(16.dp)
private val TerminalPill = RoundedCornerShape(999.dp)
private val TimePatternV3 = Regex("""(\d{1,2}):(\d{2})""")
private val DatePatternV3 = Regex("""(\d{4}-\d{2}-\d{2})""")

private const val OpenAuctionStartMinute = 9 * 60 + 15
private const val OpenAuctionEndMinute = 9 * 60 + 25
private const val MorningEndMinuteV3 = 11 * 60 + 30
private const val AfternoonStartMinuteV3 = 13 * 60
private const val CloseAuctionStartMinute = 14 * 60 + 57
private const val AfternoonEndMinuteV3 = 15 * 60
private const val TradingMinutesPerDayV3 =
    (MorningEndMinuteV3 - OpenAuctionStartMinute) +
        (AfternoonEndMinuteV3 - AfternoonStartMinuteV3)

internal data class StockAuctionPhaseV3(
    val label: String,
    val timeRange: String,
    val price: Float? = null,
    val changePercent: Float? = null,
    val volumeStrength: Float? = null,
    val available: Boolean = false
)

internal data class StockIndicatorPointV3(
    val primary: Float? = null,
    val secondary: Float? = null,
    val tertiary: Float? = null,
    val histogram: Float? = null
)

private data class PositionedMinutePointV3(
    val point: StockMinutePoint,
    val xFraction: Float,
    val daySlot: Int
)

private data class TimeShareLayoutV3(
    val points: List<PositionedMinutePointV3>,
    val labels: List<String>
)

@Composable
internal fun StockProfessionalTerminalV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onSelectTab: (String) -> Unit
) {
    var orderFlowTab by remember(ui.stock.quote.code) { mutableStateOf("五档盘口") }
    var indicatorTab by remember(ui.stock.quote.code) { mutableStateOf("成交量") }
    val isFiveDay = ui.selectedTab == "五日"
    val isTimeShare = ui.selectedTab == "分时" || isFiveDay

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("分时", "日K", "周K", "月K", "五日").forEach { tab ->
                TerminalPillButtonV3(
                    appState = appState,
                    text = tab,
                    active = ui.selectedTab == tab,
                    modifier = Modifier.weight(1f).height(36.dp),
                    onClick = { onSelectTab(tab) }
                )
            }
        }
        if (isTimeShare) {
            TimeShareTerminalV3(
                appState = appState,
                ui = ui,
                isFiveDay = isFiveDay,
                orderFlowTab = orderFlowTab,
                onSelectOrderFlow = { orderFlowTab = it }
            )
        } else {
            KLineTerminalV3(
                appState = appState,
                ui = ui,
                indicatorTab = indicatorTab,
                onSelectIndicator = { indicatorTab = it }
            )
        }
    }
}

@Composable
private fun TimeShareTerminalV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    isFiveDay: Boolean,
    orderFlowTab: String,
    onSelectOrderFlow: (String) -> Unit
) {
    val quote = ui.stock.quote
    TerminalLegendV3(
        title = if (isFiveDay) "五日分时" else "分时与集合竞价",
        secondary = "均价 ${averageLineLabelV3(ui.stock)}",
        latest = "最新 ${quote.price}",
        change = quote.changePercent,
        tone = terminalQuoteColorV3(quote.isRising)
    )
    TimeShareChartV3(
        stock = ui.stock,
        isFiveDay = isFiveDay,
        modifier = Modifier.fillMaxWidth().height(276.dp)
    )
    if (isFiveDay) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(TerminalFill, TerminalShape)
                .border(1.dp, TerminalBorder, TerminalShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "五日分时按交易日独立分段",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                "竞价段以最新交易日为准",
                color = TerminalAqua.copy(alpha = 0.62f),
                fontSize = 9.sp
            )
        }
    } else {
        val phases = remember(ui.stock.minutePoints, quote.previousClose) {
            buildStockAuctionPhasesV3(ui.stock.minutePoints, quote.previousClose)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            phases.forEach { phase ->
                AuctionPhaseCardV3(phase, Modifier.weight(1f))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("五档盘口", "逐笔成交").forEach { tab ->
            TerminalPillButtonV3(
                appState = appState,
                text = tab,
                active = orderFlowTab == tab,
                modifier = Modifier.weight(1f).height(34.dp),
                onClick = { onSelectOrderFlow(tab) }
            )
        }
    }
    DepthStatusRowV3(ui)
    when (orderFlowTab) {
        "逐笔成交" -> TradeTapePanelV3(ui.stock.tradeTicks, Modifier.fillMaxWidth().height(178.dp))
        else -> FiveLevelDepthPanelV3(ui.stock, Modifier.fillMaxWidth().height(178.dp))
    }
    ui.requestMessage?.let {
        Text(
            it,
            color = TerminalYellow.copy(alpha = 0.78f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KLineTerminalV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    indicatorTab: String,
    onSelectIndicator: (String) -> Unit
) {
    val quote = ui.stock.quote
    TerminalLegendV3(
        title = "${ui.selectedTab}历史行情",
        secondary = "MA5",
        latest = "MA10",
        change = quote.changePercent,
        tone = terminalQuoteColorV3(quote.isRising)
    )
    KLineViewportV3(
        stock = ui.stock,
        selectedTab = ui.selectedTab,
        indicatorTab = indicatorTab,
        modifier = Modifier.fillMaxWidth().height(430.dp)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("成交量", "MACD", "KDJ", "RSI").forEach { indicator ->
            TerminalPillButtonV3(
                appState = appState,
                text = indicator,
                active = indicatorTab == indicator,
                modifier = Modifier.weight(1f).height(34.dp),
                onClick = { onSelectIndicator(indicator) }
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TerminalMetricV3("均线", "MA5 / MA10", TerminalYellow, Modifier.weight(1f))
        TerminalMetricV3("成交额", quote.amount, Color.White, Modifier.weight(1f))
        TerminalMetricV3("交互", "缩放 · 横滑 · 十字线", TerminalAqua, Modifier.weight(1f))
    }
    ui.requestMessage?.let {
        Text(
            it,
            color = TerminalYellow.copy(alpha = 0.78f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TerminalLegendV3(
    title: String,
    secondary: String,
    latest: String,
    change: String,
    tone: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(secondary, color = TerminalYellow, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(latest, color = tone, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Text(change, color = tone, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TimeShareChartV3(
    stock: StockDetailUiState,
    isFiveDay: Boolean,
    modifier: Modifier
) {
    val layout = remember(stock.minutePoints, isFiveDay) {
        buildTimeShareLayoutV3(stock.minutePoints, isFiveDay)
    }
    val values = remember(layout.points) {
        buildList {
            layout.points.forEach {
                add(it.point.price)
                add(it.point.average)
            }
        }
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val width = size.width
            val height = size.height
            val chartHeight = height * 0.78f
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
                        Color.White.copy(alpha = 0.08f),
                        Offset(x, 0f),
                        Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            } else {
                val openEndX = width * tradingOffsetV3(OpenAuctionEndMinute).toFloat() / TradingMinutesPerDayV3
                val openContinuousX = width * tradingOffsetV3(9 * 60 + 30).toFloat() / TradingMinutesPerDayV3
                val closeStartX = width * tradingOffsetV3(CloseAuctionStartMinute).toFloat() / TradingMinutesPerDayV3
                drawRect(
                    TerminalYellow.copy(alpha = 0.075f),
                    topLeft = Offset.Zero,
                    size = Size(openEndX.coerceAtLeast(0f), chartHeight)
                )
                drawRect(
                    TerminalAqua.copy(alpha = 0.075f),
                    topLeft = Offset(closeStartX, 0f),
                    size = Size((width - closeStartX).coerceAtLeast(0f), chartHeight)
                )
                listOf(openEndX, openContinuousX, closeStartX).forEach { x ->
                    drawLine(
                        Color.White.copy(alpha = 0.12f),
                        Offset(x, 0f),
                        Offset(x, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                val lunchX = width *
                    (MorningEndMinuteV3 - OpenAuctionStartMinute).toFloat() /
                    TradingMinutesPerDayV3
                drawLine(
                    Color.White.copy(alpha = 0.10f),
                    Offset(lunchX, 0f),
                    Offset(lunchX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (layout.points.size < 2 || values.isEmpty()) return@Canvas
            val rawMin = values.minOrNull() ?: return@Canvas
            val rawMax = values.maxOrNull() ?: return@Canvas
            val previousClose = stock.quote.previousClose.takeIf { it > 0f }
                ?: layout.points.first().point.price
            val minValue: Float
            val maxValue: Float
            if (!isFiveDay) {
                val halfRange = maxOf(
                    values.maxOf { abs(it - previousClose) },
                    previousClose * 0.003f,
                    0.01f
                ) * 1.08f
                minValue = previousClose - halfRange
                maxValue = previousClose + halfRange
            } else {
                val padding = maxOf((rawMax - rawMin) * 0.08f, rawMax * 0.002f, 0.01f)
                minValue = rawMin - padding
                maxValue = rawMax + padding
            }
            val range = (maxValue - minValue).takeIf { it > 0.0001f } ?: 1f
            fun yFor(value: Float): Float =
                chartHeight - ((value - minValue) / range).coerceIn(0f, 1f) * chartHeight

            fun pathFor(selector: (StockMinutePoint) -> Float): Path {
                val path = Path()
                var started = false
                var previousDay = -1
                layout.points.forEach { positioned ->
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
                return path
            }

            val maxVolume = layout.points.maxOfOrNull { it.point.volumeRatio }
                ?.takeIf { it > 0f } ?: 1f
            val totalSlots = if (isFiveDay) TradingMinutesPerDayV3 * 5 else TradingMinutesPerDayV3
            val barWidth = (width / totalSlots.coerceAtLeast(1) * 0.78f)
                .coerceIn(0.7.dp.toPx(), 3.dp.toPx())
            layout.points.forEach { positioned ->
                val x = positioned.xFraction.coerceIn(0f, 1f) * width
                val top = volumeTop + volumeHeight *
                    (1f - (positioned.point.volumeRatio / maxVolume).coerceIn(0f, 1f))
                val volumeTone = if (positioned.point.price >= previousClose) TerminalRise else TerminalFall
                drawLine(
                    volumeTone.copy(alpha = 0.22f),
                    Offset(x, height),
                    Offset(x, top),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Butt
                )
            }
            if (!isFiveDay) {
                val centerY = yFor(previousClose)
                drawLine(
                    Color.White.copy(alpha = 0.17f),
                    Offset(0f, centerY),
                    Offset(width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawPath(
                pathFor { it.average },
                color = TerminalYellow.copy(alpha = 0.86f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                pathFor { it.price },
                color = terminalQuoteColorV3(stock.quote.isRising),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        TimeAxisLabelsV3(layout.labels)
    }
}

@Composable
private fun TimeAxisLabelsV3(labels: List<String>) {
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
private fun AuctionPhaseCardV3(
    phase: StockAuctionPhaseV3,
    modifier: Modifier
) {
    val tone = when {
        !phase.available -> Color.White.copy(alpha = 0.48f)
        (phase.changePercent ?: 0f) >= 0f -> TerminalRise
        else -> TerminalFall
    }
    Column(
        modifier = modifier
            .height(64.dp)
            .background(TerminalFill, TerminalShape)
            .border(1.dp, tone.copy(alpha = 0.12f), TerminalShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(phase.label, color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(phase.timeRange, color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                phase.price?.let(::formatPriceV3) ?: "暂无真实竞价",
                color = tone,
                fontSize = if (phase.available) 13.sp else 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            if (phase.available) {
                Text(
                    formatSignedPercentV3(phase.changePercent),
                    color = tone,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun DepthStatusRowV3(ui: StockMarketUiState) {
    val depth = ui.depthState
    Row(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("五档盘口", color = Color.White.copy(alpha = 0.44f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(
            depth.status.displayText(),
            color = terminalStatusToneV3(depth.status),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.weight(1f))
        Text(
            depth.source.ifBlank { "仅真实上游" },
            color = TerminalAqua.copy(alpha = 0.55f),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FiveLevelDepthPanelV3(
    stock: StockDetailUiState,
    modifier: Modifier
) {
    val asks = stock.sellLevels.take(5).reversed()
    val bids = stock.buyLevels.take(5)
    if (asks.isEmpty() && bids.isEmpty()) {
        Box(
            modifier = modifier
                .background(TerminalFill, TerminalShape)
                .border(1.dp, TerminalBorder, TerminalShape),
            contentAlignment = Alignment.Center
        ) {
            Text("真实五档盘口暂不可用", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp)
        }
        return
    }
    Row(
        modifier = modifier
            .background(TerminalFill, TerminalShape)
            .border(1.dp, TerminalBorder, TerminalShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DepthSideColumnV3("卖盘", asks, true, Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxSize().background(TerminalLine))
        DepthSideColumnV3("买盘", bids, false, Modifier.weight(1f))
    }
}

@Composable
private fun DepthSideColumnV3(
    title: String,
    levels: List<StockOrderLevel>,
    isAsk: Boolean,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = Color.White.copy(alpha = 0.68f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("价格 / 手", color = Color.White.copy(alpha = 0.28f), fontSize = 7.sp)
        }
        if (levels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("--", color = Color.White.copy(alpha = 0.36f), fontSize = 10.sp)
            }
        } else {
            levels.forEach { level ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        level.label,
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 8.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        level.price,
                        color = if (isAsk) TerminalRise else TerminalFall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        level.volume,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 8.sp,
                        modifier = Modifier.width(42.dp),
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
private fun TradeTapePanelV3(
    ticks: List<StockTradeTick>,
    modifier: Modifier
) {
    val visible = ticks.takeLast(8)
    Box(
        modifier = modifier
            .background(TerminalFill, TerminalShape)
            .border(1.dp, TerminalBorder, TerminalShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (visible.isEmpty()) {
            Text(
                "真实逐笔成交暂不可用",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            AnimatedContent(
                targetState = visible,
                transitionSpec = {
                    (
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { it / 3 }
                        ) + fadeIn(tween(110))
                        ).togetherWith(
                        slideOutVertically(tween(170), targetOffsetY = { -it / 3 }) + fadeOut(tween(100))
                    )
                },
                label = "stock-v3-trade-tape"
            ) { animatedTicks ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    animatedTicks.forEach { tick ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(tick.time.takeLast(5), color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp, modifier = Modifier.width(42.dp))
                            Text(
                                tick.direction,
                                color = if (tick.isBuy) TerminalRise else TerminalFall,
                                fontSize = 8.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                tick.price,
                                color = if (tick.isBuy) TerminalRise else TerminalFall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                tick.volume,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 8.sp,
                                modifier = Modifier.width(58.dp),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KLineViewportV3(
    stock: StockDetailUiState,
    selectedTab: String,
    indicatorTab: String,
    modifier: Modifier
) {
    val candles = stock.kLinePoints
    val ma5 = remember(candles) { movingAverageSeriesV3(candles, 5) }
    val ma10 = remember(candles) { movingAverageSeriesV3(candles, 10) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }
    var chartWidthPx by remember { mutableFloatStateOf(1f) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(stock.quote.code, selectedTab) {
        zoom = 1f
        panOffset = 0f
        selectedIndex = -1
    }

    fun visibleCountFor(currentZoom: Float): Int {
        if (candles.isEmpty()) return 0
        val base = min(72, candles.size)
        val minimum = min(12, candles.size)
        return (base / currentZoom).roundToInt().coerceIn(minimum, candles.size)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        val nextVisible = visibleCountFor(nextZoom).coerceAtLeast(1)
        val candleWidth = chartWidthPx / nextVisible
        val maxPan = (candles.size - nextVisible).coerceAtLeast(0).toFloat()
        zoom = nextZoom
        panOffset = (panOffset + panChange.x / candleWidth.coerceAtLeast(1f)).coerceIn(0f, maxPan)
    }

    val visibleCount = visibleCountFor(zoom)
    val maxPan = (candles.size - visibleCount).coerceAtLeast(0)
    val roundedPan = panOffset.roundToInt().coerceIn(0, maxPan)
    val endIndex = if (candles.isEmpty()) 0 else (candles.size - roundedPan).coerceIn(visibleCount, candles.size)
    val startIndex = (endIndex - visibleCount).coerceAtLeast(0)
    val visible = if (candles.isEmpty()) emptyList() else candles.subList(startIndex, endIndex)
    val selected = candles.getOrNull(selectedIndex)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(284.dp)
                .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(stock.quote.code, selectedTab, startIndex, visibleCount) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (visibleCount > 0) {
                                val local = ((offset.x / chartWidthPx) * visibleCount)
                                    .toInt()
                                    .coerceIn(0, visibleCount - 1)
                                selectedIndex = startIndex + local
                            }
                        },
                        onDoubleTap = {
                            zoom = 1f
                            panOffset = 0f
                            selectedIndex = -1
                        }
                    )
                }
                .transformable(transformState, lockRotationOnZoomPan = true)
        ) {
            KLineCanvasV3(
                candles = visible,
                globalStartIndex = startIndex,
                ma5 = ma5,
                ma10 = ma10,
                selectedGlobalIndex = selectedIndex,
                modifier = Modifier.fillMaxSize()
            )
            selected?.let { candle ->
                Text(
                    "${candle.date}  开 ${formatPriceV3(candle.open)}  高 ${formatPriceV3(candle.high)}  低 ${formatPriceV3(candle.low)}  收 ${formatPriceV3(candle.close)}",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
            Text(
                if (candles.isEmpty()) "等待真实${selectedTab}数据" else "双指缩放 · 横滑平移 · 点击十字线 · 双击复位",
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 7.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
            )
        }
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(visible.firstOrNull()?.date?.takeLast(5).orEmpty(), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
            Text(visible.getOrNull(visible.size / 2)?.date?.takeLast(5).orEmpty(), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
            Text(visible.lastOrNull()?.date?.takeLast(5).orEmpty(), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
        }
        IndicatorPanelV3(candles, startIndex, endIndex, indicatorTab, Modifier.fillMaxWidth().height(118.dp))
    }
}

@Composable
private fun KLineCanvasV3(
    candles: List<StockKLinePoint>,
    globalStartIndex: Int,
    ma5: List<Float?>,
    ma10: List<Float?>,
    selectedGlobalIndex: Int,
    modifier: Modifier
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val chartHeight = height * 0.76f
        val volumeTop = chartHeight + 6.dp.toPx()
        val volumeHeight = (height - volumeTop).coerceAtLeast(1f)
        repeat(4) { index ->
            val y = chartHeight * (index + 1) / 5f
            drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
        }
        if (candles.isEmpty()) return@Canvas
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val padding = max((maxPrice - minPrice) * 0.08f, 0.01f)
        val low = minPrice - padding
        val high = maxPrice + padding
        val range = (high - low).coerceAtLeast(0.01f)
        val maxVolume = candles.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        val slot = width / candles.size.coerceAtLeast(1)
        val bodyWidth = (slot * 0.58f).coerceIn(2.dp.toPx(), 11.dp.toPx())
        fun xFor(index: Int): Float = slot * index + slot / 2f
        fun yFor(value: Float): Float = chartHeight - ((value - low) / range).coerceIn(0f, 1f) * chartHeight

        candles.forEachIndexed { index, candle ->
            val x = xFor(index)
            val rising = candle.close >= candle.open
            val color = if (rising) TerminalRise else TerminalFall
            drawLine(color, Offset(x, yFor(candle.high)), Offset(x, yFor(candle.low)), 1.dp.toPx())
            val bodyTop = yFor(max(candle.open, candle.close))
            val bodyBottom = yFor(min(candle.open, candle.close))
            if (abs(bodyBottom - bodyTop) < 1.dp.toPx()) {
                drawLine(color, Offset(x - bodyWidth / 2, bodyTop), Offset(x + bodyWidth / 2, bodyTop), 1.4.dp.toPx())
            } else {
                drawLine(color, Offset(x, bodyTop), Offset(x, bodyBottom), bodyWidth, StrokeCap.Butt)
            }
            val volumeY = volumeTop + volumeHeight * (1f - (candle.volume / maxVolume).coerceIn(0f, 1f))
            drawLine(color.copy(alpha = 0.34f), Offset(x, height), Offset(x, volumeY), bodyWidth * 0.88f, StrokeCap.Butt)
        }

        fun drawAverage(series: List<Float?>, color: Color, stroke: Float) {
            val path = Path()
            var started = false
            candles.indices.forEach { local ->
                val value = series.getOrNull(globalStartIndex + local) ?: return@forEach
                val x = xFor(local)
                val y = yFor(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (started) drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawAverage(ma5, TerminalYellow.copy(alpha = 0.90f), 1.35.dp.toPx())
        drawAverage(ma10, TerminalBlue.copy(alpha = 0.82f), 1.2.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.13f), Offset(0f, volumeTop), Offset(width, volumeTop), 1.dp.toPx())

        val selectedLocal = selectedGlobalIndex - globalStartIndex
        if (selectedLocal in candles.indices) {
            val x = xFor(selectedLocal)
            val y = yFor(candles[selectedLocal].close)
            drawLine(TerminalAqua.copy(alpha = 0.58f), Offset(x, 0f), Offset(x, chartHeight), 1.dp.toPx())
            drawLine(TerminalAqua.copy(alpha = 0.38f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
        }
    }
}

@Composable
private fun IndicatorPanelV3(
    candles: List<StockKLinePoint>,
    startIndex: Int,
    endIndex: Int,
    indicator: String,
    modifier: Modifier
) {
    val series = remember(candles, indicator) {
        when (indicator) {
            "MACD" -> buildStockMacdSeriesV3(candles)
            "KDJ" -> buildStockKdjSeriesV3(candles)
            "RSI" -> buildStockRsiSeriesV3(candles)
            else -> candles.map { StockIndicatorPointV3(histogram = it.volume) }
        }
    }
    val visible = if (series.isEmpty() || startIndex >= endIndex) emptyList() else series.subList(startIndex, endIndex.coerceAtMost(series.size))
    Column(
        modifier = modifier
            .background(TerminalFill, TerminalShape)
            .border(1.dp, TerminalBorder, TerminalShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IndicatorLegendV3(indicator, visible.lastOrNull())
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (visible.isEmpty()) return@Canvas
            val values = buildList {
                visible.forEach { point ->
                    point.primary?.let(::add)
                    point.secondary?.let(::add)
                    point.tertiary?.let(::add)
                    point.histogram?.let(::add)
                }
            }
            if (values.isEmpty()) return@Canvas
            var minValue = values.minOrNull() ?: 0f
            var maxValue = values.maxOrNull() ?: 1f
            if (indicator == "MACD") {
                val absolute = max(abs(minValue), abs(maxValue)).coerceAtLeast(0.0001f)
                minValue = -absolute
                maxValue = absolute
            } else if (abs(maxValue - minValue) < 0.0001f) {
                maxValue = minValue + 1f
            }
            val range = (maxValue - minValue).coerceAtLeast(0.0001f)
            val width = size.width
            val height = size.height
            val slot = width / visible.size.coerceAtLeast(1)
            fun yFor(value: Float): Float = height - ((value - minValue) / range).coerceIn(0f, 1f) * height
            if (minValue < 0f && maxValue > 0f) {
                val zeroY = yFor(0f)
                drawLine(Color.White.copy(alpha = 0.13f), Offset(0f, zeroY), Offset(width, zeroY), 1.dp.toPx())
            }
            visible.forEachIndexed { index, point ->
                point.histogram?.let { value ->
                    val x = slot * index + slot / 2f
                    val zeroY = yFor(0f.coerceIn(minValue, maxValue))
                    val y = yFor(value)
                    val color = if (value >= 0f) TerminalRise else TerminalFall
                    drawLine(color.copy(alpha = 0.42f), Offset(x, zeroY), Offset(x, y), (slot * 0.54f).coerceAtLeast(1.dp.toPx()), StrokeCap.Butt)
                }
            }
            fun drawSeries(selector: (StockIndicatorPointV3) -> Float?, color: Color) {
                val path = Path()
                var started = false
                visible.forEachIndexed { index, point ->
                    val value = selector(point) ?: return@forEachIndexed
                    val x = slot * index + slot / 2f
                    val y = yFor(value)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                if (started) drawPath(path, color, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawSeries({ it.primary }, TerminalYellow)
            drawSeries({ it.secondary }, TerminalBlue)
            drawSeries({ it.tertiary }, TerminalPurple)
        }
    }
}

@Composable
private fun IndicatorLegendV3(indicator: String, latest: StockIndicatorPointV3?) {
    val detail = when (indicator) {
        "MACD" -> "DIF ${formatOptionalV3(latest?.primary)}  DEA ${formatOptionalV3(latest?.secondary)}"
        "KDJ" -> "K ${formatOptionalV3(latest?.primary)}  D ${formatOptionalV3(latest?.secondary)}  J ${formatOptionalV3(latest?.tertiary)}"
        "RSI" -> "RSI6 ${formatOptionalV3(latest?.primary)}"
        else -> "与主图可视区同步"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(indicator, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.42f), fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun TerminalMetricV3(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Column(modifier.height(46.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.36f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TerminalPillButtonV3(
    appState: AssistantUiState,
    text: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = (if (active) 0.20f else 0.07f) * intensity), TerminalPill)
            .border(1.dp, (if (active) TerminalAqua else Color.White).copy(alpha = if (active) 0.18f else 0.06f), TerminalPill)
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

internal fun buildStockAuctionPhasesV3(
    points: List<StockMinutePoint>,
    previousClose: Float
): List<StockAuctionPhaseV3> {
    val latestDate = points.mapNotNull { extractDateV3(it.time) }.maxOrNull()
    val current = if (latestDate != null) points.filter { extractDateV3(it.time) == latestDate } else points
    fun phase(
        label: String,
        range: String,
        startMinute: Int,
        endMinute: Int,
        baseline: (List<StockMinutePoint>) -> Float?
    ): StockAuctionPhaseV3 {
        val phasePoints = current.filter { point ->
            val minute = minuteOfV3(point.time)
            minute != null && minute in startMinute..endMinute
        }.sortedBy { minuteOfV3(it.time) }
        if (phasePoints.isEmpty()) return StockAuctionPhaseV3(label, range)
        val last = phasePoints.last()
        val base = baseline(phasePoints)?.takeIf { it > 0f }
        val change = base?.let { (last.price - it) / it * 100f }
        val strength = phasePoints.maxOfOrNull { it.volumeRatio }?.times(100f)
        return StockAuctionPhaseV3(
            label = label,
            timeRange = range,
            price = last.price,
            changePercent = change,
            volumeStrength = strength,
            available = true
        )
    }
    val open = phase("开盘竞价", "09:15–09:25", OpenAuctionStartMinute, OpenAuctionEndMinute) {
        previousClose.takeIf { value -> value > 0f } ?: it.firstOrNull()?.price
    }
    val close = phase("尾盘竞价", "14:57–15:00", CloseAuctionStartMinute, AfternoonEndMinuteV3) {
        it.firstOrNull()?.price
    }
    return listOf(open, close)
}

internal fun buildStockMacdSeriesV3(candles: List<StockKLinePoint>): List<StockIndicatorPointV3> {
    if (candles.isEmpty()) return emptyList()
    val closes = candles.map { it.close }
    val ema12 = emaSeriesV3(closes, 12)
    val ema26 = emaSeriesV3(closes, 26)
    val dif = closes.indices.map { ema12[it] - ema26[it] }
    val dea = emaSeriesV3(dif, 9)
    return closes.indices.map { index ->
        val histogram = (dif[index] - dea[index]) * 2f
        StockIndicatorPointV3(primary = dif[index], secondary = dea[index], histogram = histogram)
    }
}

internal fun buildStockKdjSeriesV3(candles: List<StockKLinePoint>): List<StockIndicatorPointV3> {
    if (candles.isEmpty()) return emptyList()
    var k = 50f
    var d = 50f
    return candles.indices.map { index ->
        val start = (index - 8).coerceAtLeast(0)
        val window = candles.subList(start, index + 1)
        val low = window.minOf { it.low }
        val high = window.maxOf { it.high }
        val rsv = if (high - low > 0.0001f) (candles[index].close - low) / (high - low) * 100f else 50f
        k = k * 2f / 3f + rsv / 3f
        d = d * 2f / 3f + k / 3f
        val j = 3f * k - 2f * d
        StockIndicatorPointV3(primary = k, secondary = d, tertiary = j)
    }
}

internal fun buildStockRsiSeriesV3(candles: List<StockKLinePoint>, period: Int = 6): List<StockIndicatorPointV3> {
    if (candles.isEmpty()) return emptyList()
    val result = MutableList(candles.size) { StockIndicatorPointV3() }
    if (candles.size <= period) return result
    var averageGain = 0f
    var averageLoss = 0f
    for (index in 1..period) {
        val change = candles[index].close - candles[index - 1].close
        if (change >= 0f) averageGain += change else averageLoss -= change
    }
    averageGain /= period
    averageLoss /= period
    fun rsi(): Float = if (averageLoss <= 0.000001f) 100f else 100f - 100f / (1f + averageGain / averageLoss)
    result[period] = StockIndicatorPointV3(primary = rsi())
    for (index in period + 1 until candles.size) {
        val change = candles[index].close - candles[index - 1].close
        val gain = max(change, 0f)
        val loss = max(-change, 0f)
        averageGain = (averageGain * (period - 1) + gain) / period
        averageLoss = (averageLoss * (period - 1) + loss) / period
        result[index] = StockIndicatorPointV3(primary = rsi())
    }
    return result
}

private fun buildTimeShareLayoutV3(
    points: List<StockMinutePoint>,
    isFiveDay: Boolean
): TimeShareLayoutV3 {
    if (points.isEmpty()) {
        return TimeShareLayoutV3(
            emptyList(),
            if (isFiveDay) List(5) { "--" } else listOf("09:15", "09:25", "11:30/13:00", "14:57", "15:00")
        )
    }
    val dates = points.mapNotNull { extractDateV3(it.time) }
    if (!isFiveDay) {
        val latestDate = dates.maxOrNull()
        val current = if (latestDate != null) points.filter { extractDateV3(it.time) == latestDate } else points
        val positioned = current.mapIndexedNotNull { index, point ->
            val offset = tradingMinuteOffsetV3(point.time) ?: index.coerceAtMost(TradingMinutesPerDayV3)
            if (offset !in 0..TradingMinutesPerDayV3) return@mapIndexedNotNull null
            PositionedMinutePointV3(point, offset.toFloat() / TradingMinutesPerDayV3, 0)
        }.sortedBy { it.xFraction }
        return TimeShareLayoutV3(positioned, listOf("09:15", "09:25", "11:30/13:00", "14:57", "15:00"))
    }
    val latestDates = dates.distinct().sorted().takeLast(5)
    val firstSlot = (5 - latestDates.size).coerceAtLeast(0)
    val dateSlots = latestDates.mapIndexed { index, date -> date to firstSlot + index }.toMap()
    val positioned = points.mapIndexedNotNull { index, point ->
        val slot = extractDateV3(point.time)?.let(dateSlots::get) ?: 4
        val offset = tradingMinuteOffsetV3(point.time) ?: (index % TradingMinutesPerDayV3)
        if (offset !in 0..TradingMinutesPerDayV3) return@mapIndexedNotNull null
        PositionedMinutePointV3(
            point,
            ((slot * TradingMinutesPerDayV3) + offset).toFloat() / (TradingMinutesPerDayV3 * 5),
            slot
        )
    }.sortedBy { it.xFraction }
    val labels = MutableList(5) { "--" }
    dateSlots.forEach { (date, slot) -> labels[slot] = date.takeLast(5) }
    return TimeShareLayoutV3(positioned, labels)
}

private fun tradingMinuteOffsetV3(raw: String): Int? {
    val minute = minuteOfV3(raw) ?: return null
    return when (minute) {
        in OpenAuctionStartMinute..MorningEndMinuteV3 -> minute - OpenAuctionStartMinute
        in AfternoonStartMinuteV3..AfternoonEndMinuteV3 ->
            (MorningEndMinuteV3 - OpenAuctionStartMinute) + (minute - AfternoonStartMinuteV3)
        else -> null
    }
}

private fun tradingOffsetV3(minute: Int): Int = when (minute) {
    in OpenAuctionStartMinute..MorningEndMinuteV3 -> minute - OpenAuctionStartMinute
    in AfternoonStartMinuteV3..AfternoonEndMinuteV3 ->
        (MorningEndMinuteV3 - OpenAuctionStartMinute) + (minute - AfternoonStartMinuteV3)
    else -> 0
}

private fun minuteOfV3(raw: String): Int? {
    val match = TimePatternV3.find(raw) ?: return null
    val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun extractDateV3(raw: String): String? = DatePatternV3.find(raw)?.groupValues?.getOrNull(1)

private fun movingAverageSeriesV3(candles: List<StockKLinePoint>, window: Int): List<Float?> {
    if (candles.isEmpty()) return emptyList()
    val result = MutableList<Float?>(candles.size) { null }
    var sum = 0f
    candles.forEachIndexed { index, candle ->
        sum += candle.close
        if (index >= window) sum -= candles[index - window].close
        if (index >= window - 1) result[index] = sum / window
    }
    return result
}

private fun emaSeriesV3(values: List<Float>, period: Int): List<Float> {
    if (values.isEmpty()) return emptyList()
    val alpha = 2f / (period + 1f)
    val result = MutableList(values.size) { values.first() }
    var ema = values.first()
    values.forEachIndexed { index, value ->
        if (index > 0) ema = value * alpha + ema * (1f - alpha)
        result[index] = ema
    }
    return result
}

private fun averageLineLabelV3(stock: StockDetailUiState): String =
    stock.minutePoints.lastOrNull()?.average?.let(::formatPriceV3) ?: "--"

private fun terminalQuoteColorV3(rising: Boolean): Color = if (rising) TerminalRise else TerminalFall

private fun terminalStatusToneV3(status: StockModuleStatus): Color = when (status) {
    StockModuleStatus.Ok -> TerminalAqua
    StockModuleStatus.Partial, StockModuleStatus.Stale -> TerminalYellow
    StockModuleStatus.Empty -> Color.White.copy(alpha = 0.50f)
    StockModuleStatus.Unavailable -> Color.White.copy(alpha = 0.40f)
}

private fun formatPriceV3(value: Float): String = String.format("%.2f", value)

private fun formatOptionalV3(value: Float?): String = value?.let { String.format("%.2f", it) } ?: "--"

private fun formatSignedPercentV3(value: Float?): String = value?.let { String.format("%+.2f%%", it) } ?: "--"
