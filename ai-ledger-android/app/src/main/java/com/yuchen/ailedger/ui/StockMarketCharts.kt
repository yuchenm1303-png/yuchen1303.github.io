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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockTradeTick
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChartRiseRed = Color(0xFFFF8F8F)
private val ChartFallGreen = Color(0xFF80F7B4)
private val ChartAqua = Color(0xFF8DF9EA)
private val ChartAverageYellow = Color(0xFFFFD36E)
private val ChartMaBlue = Color(0xFF9DCBFF)
private val ChartSectionLine = Color.White.copy(alpha = 0.085f)
private val TimePattern = Regex("""(\d{1,2}):(\d{2})""")
private val DatePattern = Regex("""(\d{4}-\d{2}-\d{2})""")
private const val MorningStartMinute = 9 * 60 + 15
private const val MorningEndMinute = 11 * 60 + 30
private const val AfternoonStartMinute = 13 * 60
private const val AfternoonEndMinute = 15 * 60
private const val TradingMinutesPerDay =
    (MorningEndMinute - MorningStartMinute) + (AfternoonEndMinute - AfternoonStartMinute)

private data class PositionedMinutePoint(
    val point: StockMinutePoint,
    val xFraction: Float,
    val daySlot: Int
)

private data class TimeShareLayout(
    val points: List<PositionedMinutePoint>,
    val labels: List<String>
)

@Composable
internal fun StockProfessionalTerminalV2(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onSelectTab: (String) -> Unit
) {
    val isFiveDay = ui.selectedTab == "五日"
    val isTimeShare = ui.selectedTab == "分时" || isFiveDay
    Column(
        Modifier
            .fillMaxWidth()
            .height(430.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("分时", "日K", "周K", "月K", "五日").forEach { tab ->
                ChartTabButton(
                    appState = appState,
                    text = tab,
                    active = ui.selectedTab == tab,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    onClick = { onSelectTab(tab) }
                )
            }
        }
        if (isTimeShare) {
            TimeShareTerminalBody(ui, isFiveDay)
        } else {
            KLineTerminalBody(ui)
        }
    }
}

@Composable
private fun ColumnScope.TimeShareTerminalBody(
    ui: StockMarketUiState,
    isFiveDay: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChartLegendRow(
                title = if (isFiveDay) "五日分时" else "集合竞价",
                secondary = "均价 ${averageLineLabel(ui.stock)}",
                latest = "最新 ${ui.stock.quote.price}",
                latestColor = chartQuoteColor(ui.stock.quote.isRising),
                change = ui.stock.quote.changePercent,
                changeColor = chartQuoteColor(ui.stock.quote.isRising)
            )
            FixedSessionTimeShareChart(
                stock = ui.stock,
                isFiveDay = isFiveDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ChartMetricCell("副图", "MACD 待接入", ChartAverageYellow, Modifier.weight(1f))
                ChartMetricCell("成交量", ui.stock.quote.amount, Color.White, Modifier.weight(1f))
                ChartMetricCell("PB", ui.stock.quote.pb, Color.White, Modifier.weight(1f))
            }
            ui.requestMessage?.let {
                Text(
                    it,
                    color = Color(0xFFFFC857).copy(alpha = 0.82f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TimeShareDepthTapeColumn(
            stock = ui.stock,
            modifier = Modifier
                .width(118.dp)
                .fillMaxSize()
        )
    }
}

@Composable
private fun ColumnScope.KLineTerminalBody(ui: StockMarketUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChartLegendRow(
            title = "${ui.selectedTab}历史行情",
            secondary = "MA5",
            latest = "MA10",
            latestColor = ChartMaBlue,
            change = ui.stock.quote.changePercent,
            changeColor = chartQuoteColor(ui.stock.quote.isRising)
        )
        InteractiveKLineChart(
            stock = ui.stock,
            selectedTab = ui.selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChartMetricCell("均线", "MA5 / MA10", ChartAverageYellow, Modifier.weight(1f))
            ChartMetricCell("成交量", ui.stock.quote.amount, Color.White, Modifier.weight(1f))
            ChartMetricCell("操作", "双指缩放 · 横滑", ChartAqua, Modifier.weight(1f))
        }
        ui.requestMessage?.let {
            Text(
                it,
                color = Color(0xFFFFC857).copy(alpha = 0.82f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartLegendRow(
    title: String,
    secondary: String,
    latest: String,
    latestColor: Color,
    change: String,
    changeColor: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            secondary,
            color = ChartAverageYellow.copy(alpha = 0.92f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            latest,
            color = latestColor,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            change,
            color = changeColor,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun FixedSessionTimeShareChart(
    stock: StockDetailUiState,
    isFiveDay: Boolean,
    modifier: Modifier
) {
    val layout = remember(stock.minutePoints, isFiveDay) {
        buildTimeShareLayout(stock.minutePoints, isFiveDay)
    }
    val values = remember(layout.points) {
        buildList {
            layout.points.forEach { positioned ->
                add(positioned.point.price)
                add(positioned.point.average)
            }
        }
    }
    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
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
                val auctionEndX = width *
                    ((9 * 60 + 30 - MorningStartMinute).toFloat() / TradingMinutesPerDay)
                val lunchX = width *
                    ((MorningEndMinute - MorningStartMinute).toFloat() / TradingMinutesPerDay)
                drawLine(
                    Color.White.copy(alpha = 0.06f),
                    Offset(auctionEndX, 0f),
                    Offset(auctionEndX, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )
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
                val padding = maxOf(
                    (rawMax - rawMin) * 0.08f,
                    rawMax * 0.002f,
                    0.01f
                )
                minValue = rawMin - padding
                maxValue = rawMax + padding
            }
            val range = (maxValue - minValue).takeIf { it > 0.0001f } ?: 1f
            fun yFor(value: Float): Float =
                chartHeight - ((value - minValue) / range).coerceIn(0f, 1f) * chartHeight

            fun pathFor(selector: (StockMinutePoint) -> Float): Path {
                val path = Path()
                var started = false
                var previousDaySlot = -1
                layout.points.forEach { positioned ->
                    val x = positioned.xFraction.coerceIn(0f, 1f) * width
                    val y = yFor(selector(positioned.point))
                    if (!started || positioned.daySlot != previousDaySlot) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                    previousDaySlot = positioned.daySlot
                }
                return path
            }

            val maxVolume = layout.points.maxOfOrNull { it.point.volumeRatio }
                ?.takeIf { it > 0f }
                ?: 1f
            val totalSlots = if (isFiveDay) {
                TradingMinutesPerDay * 5
            } else {
                TradingMinutesPerDay
            }
            val barWidth = (width / totalSlots.coerceAtLeast(1) * 0.78f)
                .coerceIn(0.7.dp.toPx(), 3.dp.toPx())
            layout.points.forEach { positioned ->
                val x = positioned.xFraction.coerceIn(0f, 1f) * width
                val top = volumeTop + volumeHeight *
                    (1f - (positioned.point.volumeRatio / maxVolume).coerceIn(0f, 1f))
                drawLine(
                    Color.White.copy(alpha = 0.16f),
                    Offset(x, height),
                    Offset(x, top),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Butt
                )
            }

            if (!isFiveDay) {
                val centerY = yFor(previousClose)
                drawLine(
                    Color.White.copy(alpha = 0.18f),
                    Offset(0f, centerY),
                    Offset(width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawPath(
                pathFor { it.average },
                color = ChartAverageYellow.copy(alpha = 0.88f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                pathFor { it.price },
                color = chartQuoteColor(stock.quote.isRising),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        TimeAxisLabels(layout.labels)
    }
}

@Composable
private fun TimeAxisLabels(labels: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            Text(
                label,
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1
            )
        }
    }
}

private fun buildTimeShareLayout(
    points: List<StockMinutePoint>,
    isFiveDay: Boolean
): TimeShareLayout {
    if (points.isEmpty()) {
        return TimeShareLayout(
            points = emptyList(),
            labels = if (isFiveDay) {
                List(5) { "--" }
            } else {
                listOf("09:15", "11:30/13:00", "15:00")
            }
        )
    }

    val pointDates = points.mapNotNull { extractDate(it.time) }
    if (!isFiveDay) {
        val latestDate = pointDates.maxOrNull()
        val currentDayPoints = if (latestDate != null) {
            points.filter { extractDate(it.time) == latestDate }
        } else {
            points
        }
        val positioned = currentDayPoints.mapIndexedNotNull { index, point ->
            val offset = tradingMinuteOffset(point.time)
                ?: index.coerceAtMost(TradingMinutesPerDay)
            if (offset !in 0..TradingMinutesPerDay) return@mapIndexedNotNull null
            PositionedMinutePoint(
                point = point,
                xFraction = offset.toFloat() / TradingMinutesPerDay,
                daySlot = 0
            )
        }.sortedBy { it.xFraction }
        return TimeShareLayout(
            points = positioned,
            labels = listOf("09:15", "11:30/13:00", "15:00")
        )
    }

    val dates = pointDates.distinct().sorted().takeLast(5)
    val firstSlot = (5 - dates.size).coerceAtLeast(0)
    val dateSlots = dates
        .mapIndexed { index, date -> date to (firstSlot + index) }
        .toMap()
    val fallbackSlot = 4
    val positioned = points.mapIndexedNotNull { index, point ->
        val date = extractDate(point.time)
        val slot = date?.let(dateSlots::get) ?: fallbackSlot
        val offset = tradingMinuteOffset(point.time)
            ?: (index % TradingMinutesPerDay)
        if (offset !in 0..TradingMinutesPerDay) return@mapIndexedNotNull null
        PositionedMinutePoint(
            point = point,
            xFraction = ((slot * TradingMinutesPerDay) + offset).toFloat() /
                (TradingMinutesPerDay * 5),
            daySlot = slot
        )
    }.sortedBy { it.xFraction }
    val labels = MutableList(5) { "--" }
    dateSlots.forEach { (date, slot) -> labels[slot] = date.takeLast(5) }
    return TimeShareLayout(positioned, labels)
}

private fun tradingMinuteOffset(raw: String): Int? {
    val match = TimePattern.find(raw) ?: return null
    val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val total = hour * 60 + minute
    return when (total) {
        in MorningStartMinute..MorningEndMinute -> total - MorningStartMinute
        in AfternoonStartMinute..AfternoonEndMinute ->
            (MorningEndMinute - MorningStartMinute) + (total - AfternoonStartMinute)
        else -> null
    }
}

private fun extractDate(raw: String): String? =
    DatePattern.find(raw)?.groupValues?.getOrNull(1)

@Composable
private fun InteractiveKLineChart(
    stock: StockDetailUiState,
    selectedTab: String,
    modifier: Modifier
) {
    val candles = stock.kLinePoints
    val ma5 = remember(candles) { movingAverageSeries(candles, 5) }
    val ma10 = remember(candles) { movingAverageSeries(candles, 10) }
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
        val base = minOf(72, candles.size)
        val minimum = minOf(12, candles.size)
        return (base / currentZoom).roundToInt().coerceIn(minimum, candles.size)
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        val nextVisibleCount = visibleCountFor(nextZoom).coerceAtLeast(1)
        val candleWidth = chartWidthPx / nextVisibleCount
        val maxPan = (candles.size - nextVisibleCount).coerceAtLeast(0).toFloat()
        zoom = nextZoom
        panOffset = (panOffset + panChange.x / candleWidth.coerceAtLeast(1f))
            .coerceIn(0f, maxPan)
    }

    val visibleCount = visibleCountFor(zoom)
    val maxPan = (candles.size - visibleCount).coerceAtLeast(0)
    val roundedPan = panOffset.roundToInt().coerceIn(0, maxPan)
    val endIndex = if (candles.isEmpty()) {
        0
    } else {
        (candles.size - roundedPan).coerceIn(visibleCount, candles.size)
    }
    val startIndex = (endIndex - visibleCount).coerceAtLeast(0)
    val visibleCandles = if (candles.isEmpty()) {
        emptyList()
    } else {
        candles.subList(startIndex, endIndex)
    }
    val selectedCandle = candles.getOrNull(selectedIndex)

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    chartWidthPx = it.width.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(stock.quote.code, selectedTab, startIndex, visibleCount) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (visibleCount > 0) {
                                val localIndex = ((offset.x / chartWidthPx) * visibleCount)
                                    .toInt()
                                    .coerceIn(0, visibleCount - 1)
                                selectedIndex = startIndex + localIndex
                            }
                        },
                        onDoubleTap = {
                            zoom = 1f
                            panOffset = 0f
                            selectedIndex = -1
                        }
                    )
                }
                .transformable(
                    state = transformableState,
                    lockRotationOnZoomPan = true
                )
        ) {
            KLineCanvas(
                candles = visibleCandles,
                globalStartIndex = startIndex,
                ma5 = ma5,
                ma10 = ma10,
                selectedGlobalIndex = selectedIndex,
                modifier = Modifier.fillMaxSize()
            )
            selectedCandle?.let { candle ->
                Text(
                    text = "${candle.date}  开 ${formatPrice(candle.open)}  高 ${formatPrice(candle.high)}  低 ${formatPrice(candle.low)}  收 ${formatPrice(candle.close)}",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            Color.Black.copy(alpha = 0.34f),
                            androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
            Text(
                text = if (candles.isEmpty()) {
                    "等待真实${selectedTab}数据"
                } else {
                    "${visibleCount} 根 · 双指缩放 · 横滑平移 · 双击复位"
                },
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dateLabel(visibleCandles.firstOrNull()?.date),
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 8.sp
            )
            Text(
                dateLabel(visibleCandles.getOrNull(visibleCandles.size / 2)?.date),
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 8.sp
            )
            Text(
                dateLabel(visibleCandles.lastOrNull()?.date),
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun KLineCanvas(
    candles: List<StockKLinePoint>,
    globalStartIndex: Int,
    ma5: List<Float?>,
    ma10: List<Float?>,
    selectedGlobalIndex: Int,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartHeight = height * 0.78f
        val volumeTop = chartHeight + 8.dp.toPx()
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
        repeat(5) { index ->
            val x = width * (index + 1) / 6f
            drawLine(
                Color.White.copy(alpha = 0.06f),
                Offset(x, 0f),
                Offset(x, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
        }
        if (candles.size < 2) return@Canvas

        val minValue = candles.minOfOrNull { it.low } ?: return@Canvas
        val maxValue = candles.maxOfOrNull { it.high } ?: return@Canvas
        val padding = maxOf(
            (maxValue - minValue) * 0.06f,
            maxValue * 0.0015f,
            0.01f
        )
        val bottomValue = minValue - padding
        val topValue = maxValue + padding
        val range = (topValue - bottomValue).takeIf { abs(it) > 0.0001f } ?: 1f
        val stepX = width / candles.size
        val bodyWidth = (stepX * 0.58f).coerceIn(2.5.dp.toPx(), 14.dp.toPx())
        val maxVolume = candles.maxOfOrNull { it.volume }?.takeIf { it > 0f } ?: 1f
        fun xFor(index: Int): Float = index * stepX + stepX / 2f
        fun yFor(value: Float): Float =
            chartHeight - ((value - bottomValue) / range).coerceIn(0f, 1f) * chartHeight

        candles.forEachIndexed { index, candle ->
            val x = xFor(index)
            val rising = candle.close >= candle.open
            val color = if (rising) ChartRiseRed else ChartFallGreen
            val highY = yFor(candle.high)
            val lowY = yFor(candle.low)
            val openY = yFor(candle.open)
            val closeY = yFor(candle.close)
            val bodyTop = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)
            drawLine(
                color.copy(alpha = 0.90f),
                Offset(x, highY),
                Offset(x, lowY),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Butt
            )
            if (bodyBottom - bodyTop < 1.2.dp.toPx()) {
                drawLine(
                    color,
                    Offset(x - bodyWidth / 2f, closeY),
                    Offset(x + bodyWidth / 2f, closeY),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            } else {
                drawLine(
                    color,
                    Offset(x, bodyTop),
                    Offset(x, bodyBottom),
                    strokeWidth = bodyWidth,
                    cap = StrokeCap.Butt
                )
            }
            val volumeTopY = volumeTop + volumeHeight *
                (1f - (candle.volume / maxVolume).coerceIn(0f, 1f))
            drawLine(
                color.copy(alpha = 0.38f),
                Offset(x, height),
                Offset(x, volumeTopY),
                strokeWidth = bodyWidth * 0.88f,
                cap = StrokeCap.Butt
            )
        }

        fun drawAverage(series: List<Float?>, color: Color, strokeWidth: Float) {
            val path = Path()
            var started = false
            candles.indices.forEach { localIndex ->
                val globalIndex = globalStartIndex + localIndex
                val value = series.getOrNull(globalIndex) ?: return@forEach
                val x = xFor(localIndex)
                val y = yFor(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (started) {
                drawPath(
                    path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        drawAverage(ma5, ChartAverageYellow.copy(alpha = 0.88f), 1.35.dp.toPx())
        drawAverage(ma10, ChartMaBlue.copy(alpha = 0.78f), 1.2.dp.toPx())
        drawLine(
            Color.White.copy(alpha = 0.14f),
            Offset(0f, volumeTop),
            Offset(width, volumeTop),
            strokeWidth = 1.dp.toPx()
        )

        val selectedLocalIndex = selectedGlobalIndex - globalStartIndex
        if (selectedLocalIndex in candles.indices) {
            val selected = candles[selectedLocalIndex]
            val x = xFor(selectedLocalIndex)
            val y = yFor(selected.close)
            drawLine(
                ChartAqua.copy(alpha = 0.58f),
                Offset(x, 0f),
                Offset(x, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                ChartAqua.copy(alpha = 0.38f),
                Offset(0f, y),
                Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun movingAverageSeries(
    candles: List<StockKLinePoint>,
    window: Int
): List<Float?> {
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

@Composable
private fun TimeShareDepthTapeColumn(
    stock: StockDetailUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "盘口",
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        DepthRows(stock.sellLevels, isAskSide = true, Modifier.weight(1.25f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(chartQuoteColor(stock.quote.isRising).copy(alpha = 0.56f))
        )
        DepthRows(stock.buyLevels, isAskSide = false, Modifier.weight(1.25f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ChartSectionLine))
        Text(
            "逐笔",
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        AnimatedTradeRows(stock.tradeTicks, Modifier.weight(1f))
    }
}

@Composable
private fun DepthRows(
    levels: List<StockOrderLevel>,
    isAskSide: Boolean,
    modifier: Modifier
) {
    val display = if (isAskSide) {
        val source = levels.ifEmpty { fallbackAskLevels() }.take(10)
        if (source.firstOrNull()?.label?.contains("卖1") == true) source.reversed() else source
    } else {
        levels.take(6).ifEmpty { fallbackBidLevels() }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
        display.take(6).forEach { level ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    level.label.take(2),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.width(20.dp),
                    maxLines = 1
                )
                Text(
                    level.price,
                    color = chartQuoteColor(level.isAsk),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    level.volume,
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AnimatedTradeRows(
    ticks: List<StockTradeTick>,
    modifier: Modifier
) {
    val visibleTicks = ticks.takeLast(6).ifEmpty { fallbackTicks() }
    AnimatedContent(
        targetState = visibleTicks,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 3 }
                ) + fadeIn(animationSpec = tween(110))
            ).togetherWith(
                slideOutVertically(
                    animationSpec = tween(170),
                    targetOffsetY = { -it / 3 }
                ) + fadeOut(animationSpec = tween(100))
            )
        },
        label = "stock-trade-tape-scroll"
    ) { animatedTicks ->
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            animatedTicks.forEach { tick ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tick.time.takeLast(5),
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        modifier = Modifier.width(30.dp),
                        maxLines = 1
                    )
                    Text(
                        tick.price,
                        color = chartQuoteColor(tick.isBuy),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        tick.volume,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(34.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartMetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier.height(54.dp), verticalArrangement = Arrangement.Center) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { "--" },
            color = color.copy(alpha = 0.94f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChartTabButton(
    appState: AssistantUiState,
    text: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    val fillAlpha = (if (active) 0.20f else 0.075f) * intensity
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.76f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun averageLineLabel(stock: StockDetailUiState): String =
    stock.minutePoints.lastOrNull()?.average?.let { String.format("%.2f", it) } ?: "--"

private fun formatPrice(value: Float): String = String.format("%.2f", value)

private fun dateLabel(value: String?): String = value?.takeLast(5).orEmpty()

private fun chartQuoteColor(isRising: Boolean): Color =
    if (isRising) ChartRiseRed else ChartFallGreen

private fun fallbackAskLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("卖5", "--", "--", true),
    StockOrderLevel("卖4", "--", "--", true),
    StockOrderLevel("卖3", "--", "--", true),
    StockOrderLevel("卖2", "--", "--", true),
    StockOrderLevel("卖1", "--", "--", true)
)

private fun fallbackBidLevels(): List<StockOrderLevel> = listOf(
    StockOrderLevel("买1", "--", "--", false),
    StockOrderLevel("买2", "--", "--", false),
    StockOrderLevel("买3", "--", "--", false),
    StockOrderLevel("买4", "--", "--", false),
    StockOrderLevel("买5", "--", "--", false)
)

private fun fallbackTicks(): List<StockTradeTick> = listOf(
    StockTradeTick("--:--", "--", "--", "等待", true),
    StockTradeTick("--:--", "--", "--", "等待", false),
    StockTradeTick("--:--", "--", "--", "等待", true)
)
