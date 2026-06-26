package com.yuchen.ailedger.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMinutePoint
import com.yuchen.ailedger.model.StockOrderLevel
import com.yuchen.ailedger.model.StockTradeTick
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MirrorRise = Color(0xFFFF8F8F)
private val MirrorFall = Color(0xFF80F7B4)
private val MirrorAqua = Color(0xFF8DF9EA)
private val MirrorYellow = Color(0xFFFFD36E)
private val MirrorBlue = Color(0xFF9DCBFF)
private val MirrorPink = Color(0xFFFF72D2)
private val MirrorGreen = Color(0xFF48C995)
private val MirrorOrange = Color(0xFFFF9060)
private val MirrorGrid = Color.White.copy(alpha = 0.10f)
private val MirrorSoftGrid = Color.White.copy(alpha = 0.06f)
private val MirrorPanelFill = Color.White.copy(alpha = 0.035f)
private val MirrorPanelBorder = Color.White.copy(alpha = 0.085f)
private val MirrorPillShape = RoundedCornerShape(999.dp)
private val MirrorOrderShape = RoundedCornerShape(15.dp)
private val MirrorTimePattern = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
private val MirrorDatePattern = Regex("""(\d{4}-\d{2}-\d{2})""")

private data class MirrorTimeParts(
    val date: String,
    val minuteOfDay: Int,
    val secondOfDay: Int,
    val label: String
)

private data class MirrorPositionedMinute(
    val point: StockMinutePoint,
    val xFraction: Float,
    val daySlot: Int,
    val parts: MirrorTimeParts
)

private data class MirrorKWindow(
    val start: Int,
    val end: Int,
    val visible: List<StockKLinePoint>
)

private data class MirrorKPanelLayout(
    val gap: Float,
    val mainHeight: Float,
    val volumeTop: Float,
    val volumeHeight: Float,
    val indicatorTop: Float,
    val indicatorHeight: Float
)

private data class MirrorMacd(
    val dif: List<Float?>,
    val dea: List<Float?>,
    val histogram: List<Float?>
)

private data class MirrorKdj(
    val k: List<Float?>,
    val d: List<Float?>,
    val j: List<Float?>
)

private data class MirrorBoll(
    val mid: List<Float?>,
    val upper: List<Float?>,
    val lower: List<Float?>,
    val percentB: List<Float?>,
    val bandwidth: List<Float?>
)

@Composable
internal fun StockWebMirrorTerminal(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onSelectTab: (String) -> Unit
) {
    var indicator by remember(ui.stock.quote.code) { mutableStateOf("MACD") }
    val isTimeShare = ui.selectedTab == "分时" || ui.selectedTab == "五日"
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("分时", "日K", "周K", "月K", "五日").forEach { tab ->
                MirrorPill(
                    appState = appState,
                    text = tab,
                    active = ui.selectedTab == tab,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(tab) }
                )
            }
        }
        MirrorLegend(ui)
        if (!isTimeShare) MirrorMovingAverageToolbar(ui)
        if (isTimeShare) {
            MirrorTimeShareTerminal(ui = ui, isFiveDay = ui.selectedTab == "五日")
        } else {
            MirrorKLineTerminal(
                ui = ui,
                indicator = indicator,
                onIndicatorChange = { indicator = it }
            )
        }
    }
}

@Composable
private fun MirrorPill(
    appState: AssistantUiState,
    text: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    val fill = Color.White.copy(alpha = (if (active) 0.20f else 0.065f) * intensity)
    val border = if (active) MirrorAqua.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .background(fill, MirrorPillShape)
            .border(1.dp, border, MirrorPillShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun MirrorLegend(ui: StockMarketUiState) {
    val isTimeShare = ui.selectedTab == "分时" || ui.selectedTab == "五日"
    val quote = ui.stock.quote
    val tone = if (quote.isRising) MirrorRise else MirrorFall
    val candles = ui.stock.kLinePoints
    val ma5 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 5) }
    val ma10 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 10) }
    val continuous = ui.stock.minutePoints.filter { mirrorPhase(it) == "continuous" }
    val latestAverage = continuous.lastOrNull()?.average ?: ui.stock.minutePoints.lastOrNull()?.average
    val auctionPoints = ui.stock.minutePoints.filter { mirrorPhase(it) != "continuous" }
    val matchedCount = auctionPoints.count { (it.matchedVolume ?: 0f) > 0f }
    val unmatchedCount = auctionPoints.count { (it.unmatchedVolume ?: 0f) > 0f }
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isTimeShare) {
                if (ui.selectedTab == "五日") "五日分时" else "分时 · 首尾集合竞价"
            } else {
                "${ui.selectedTab}历史行情"
            },
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = if (isTimeShare) "均价 ${mirrorPrice(latestAverage)}" else "MA5 ${mirrorPrice(ma5.lastOrNull())}",
            color = MirrorYellow,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = if (isTimeShare) "最新 ${quote.price}" else "MA10 ${mirrorPrice(ma10.lastOrNull())}",
            color = if (isTimeShare) tone else MirrorBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (isTimeShare) {
                if (ui.selectedTab == "五日") {
                    "${mirrorFiveDayDates(ui.stock.minutePoints).size} 个交易日"
                } else if (auctionPoints.isNotEmpty()) {
                    "匹配 $matchedCount · 未匹配 $unmatchedCount"
                } else {
                    "竞价数据不可用"
                }
            } else {
                "${candles.size} 根真实K线"
            },
            color = MirrorAqua.copy(alpha = 0.62f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = quote.changePercent,
            color = tone,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun MirrorMovingAverageToolbar(ui: StockMarketUiState) {
    val candles = ui.stock.kLinePoints
    val ma5 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 5) }
    val ma10 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 10) }
    val ma20 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 20) }
    val ma30 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 30) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier.background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(7.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text("均线", color = Color.White.copy(alpha = 0.72f), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        MirrorToolbarValue("MA5 ${mirrorPrice(ma5.lastOrNull())}", MirrorYellow)
        MirrorToolbarValue("MA10 ${mirrorPrice(ma10.lastOrNull())}", MirrorBlue)
        MirrorToolbarValue("MA20 ${mirrorPrice(ma20.lastOrNull())}", MirrorPink)
        MirrorToolbarValue("MA30 ${mirrorPrice(ma30.lastOrNull())}", MirrorGreen)
        Spacer(Modifier.width(10.dp))
        MirrorToolbarValue("前复权", Color.White.copy(alpha = 0.42f))
        MirrorToolbarValue("${candles.size}根", Color.White.copy(alpha = 0.42f))
    }
}

@Composable
private fun MirrorToolbarValue(text: String, color: Color) {
    Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
}

@Composable
private fun MirrorTimeShareTerminal(ui: StockMarketUiState, isFiveDay: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MirrorTimeShareChart(ui.stock, isFiveDay, Modifier.fillMaxWidth().weight(1f))
            MirrorTimeAxis(ui.stock.minutePoints, isFiveDay)
            MirrorCaption(
                if (isFiveDay) listOf("五日均价", "五日成交量", "${mirrorFiveDayDates(ui.stock.minutePoints).size}日真实数据")
                else listOf("红/绿未匹配量：上沿向下", "白色匹配量：下沿向上", "首尾集合竞价")
            )
        }
        MirrorOrderPanel(ui, Modifier.width(132.dp).fillMaxHeight())
    }
}

@Composable
private fun MirrorTimeAxis(points: List<StockMinutePoint>, isFiveDay: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val labels = if (isFiveDay) {
            val dates = mirrorFiveDayDates(points)
            List(5) { index -> dates.getOrNull(index - (5 - dates.size))?.takeLast(5) ?: "--" }
        } else {
            listOf("09:15", "09:30", "11:30/13:00", "14:57", "15:00")
        }
        labels.forEach { label -> Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 7.sp, maxLines = 1) }
    }
}

@Composable
private fun MirrorCaption(items: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.Top
    ) {
        items.forEachIndexed { index, text ->
            Text(
                text = text,
                color = when (index) {
                    0 -> MirrorYellow.copy(alpha = 0.66f)
                    1 -> MirrorBlue.copy(alpha = 0.66f)
                    else -> Color.White.copy(alpha = 0.42f)
                },
                fontSize = 6.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = when (index) {
                    1 -> TextAlign.Center
                    2 -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MirrorTimeShareChart(stock: StockDetailUiState, isFiveDay: Boolean, modifier: Modifier) {
    val positioned = remember(stock.minutePoints, isFiveDay) { mirrorPositionMinutePoints(stock.minutePoints, isFiveDay) }
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val volumeHeight = height * 0.24f
        val gap = 8.dp.toPx()
        val chartHeight = (height - volumeHeight - gap).coerceAtLeast(1f)
        val volumeTop = chartHeight + gap
        mirrorDrawGrid(this, width, chartHeight, if (isFiveDay) 4 else 2)
        if (isFiveDay) {
            repeat(4) { index ->
                val x = width * (index + 1) / 5f
                drawLine(Color.White.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, height), 1.dp.toPx())
            }
        } else {
            val openX = width * 0.14f
            val closeX = width * 0.95f
            val lunchX = width * (0.14f + (0.95f - 0.14f) * 0.5f)
            drawRect(Color.White.copy(alpha = 0.045f), topLeft = Offset.Zero, size = Size(openX, chartHeight))
            drawRect(MirrorAqua.copy(alpha = 0.045f), topLeft = Offset(closeX, 0f), size = Size(width - closeX, chartHeight))
            listOf(openX, lunchX, closeX).forEach { x ->
                drawLine(Color.White.copy(alpha = 0.13f), Offset(x, 0f), Offset(x, height), 1.dp.toPx())
            }
        }
        drawRect(color = Color.White.copy(alpha = 0.13f), topLeft = Offset(0f, volumeTop), size = Size(width, volumeHeight), style = Stroke(width = 1.dp.toPx()))
        if (positioned.size < 2) {
            mirrorDrawText(if (isFiveDay) "暂无真实五日分时数据" else "暂无真实分时数据", width / 2f, chartHeight / 2f, 12.sp.toPx(), Color.White.copy(alpha = 0.42f), Paint.Align.CENTER)
            return@Canvas
        }
        val points = positioned.map { it.point }
        val previousClose = stock.quote.previousClose.takeIf { it > 0f } ?: points.first().price
        val values = points.flatMap { listOf(it.price, it.average) }
        val rawMin = values.minOrNull() ?: return@Canvas
        val rawMax = values.maxOrNull() ?: return@Canvas
        val minValue: Float
        val maxValue: Float
        if (isFiveDay) {
            val padding = max(max((rawMax - rawMin) * 0.08f, rawMax * 0.002f), 0.01f)
            minValue = rawMin - padding
            maxValue = rawMax + padding
        } else {
            val limitRatio = if (stock.quote.name.contains("ST", true)) 0.05f else 0.10f
            val observed = values.maxOf { abs(it - previousClose) }
            val half = max(max(previousClose * limitRatio, observed), 0.01f)
            minValue = previousClose - half
            maxValue = previousClose + half
        }
        val range = (maxValue - minValue).coerceAtLeast(0.0001f)
        fun xFor(item: MirrorPositionedMinute) = item.xFraction * width
        fun yFor(value: Float) = chartHeight - (value - minValue) / range * chartHeight
        if (!isFiveDay) {
            mirrorDashedLine(Offset(0f, yFor(previousClose)), Offset(width, yFor(previousClose)), Color.White.copy(alpha = 0.22f), 5.dp.toPx(), 5.dp.toPx())
            mirrorNumber(stock.quote.price)?.let { latest ->
                mirrorDashedLine(Offset(width * 0.14f, yFor(latest)), Offset(width, yFor(latest)), MirrorYellow.copy(alpha = 0.55f), 3.dp.toPx(), 4.dp.toPx())
            }
        }
        val maxVolume = points.maxOfOrNull { it.volume.takeIf { value -> value > 0f } ?: it.volumeRatio }?.coerceAtLeast(1f) ?: 1f
        positioned.forEach { item ->
            val volume = item.point.volume.takeIf { it > 0f } ?: item.point.volumeRatio
            val top = height - volume / maxVolume * volumeHeight * 0.88f
            drawLine(
                if (item.point.price >= previousClose) MirrorRise.copy(alpha = 0.42f) else MirrorFall.copy(alpha = 0.42f),
                Offset(xFor(item), height),
                Offset(xFor(item), max(volumeTop, top)),
                1.dp.toPx()
            )
        }
        if (!isFiveDay) mirrorDrawAuctionVolumes(this, positioned, width, height, volumeTop, volumeHeight)
        mirrorDrawMinutePath(positioned, ::xFor, { yFor(it.average) }, MirrorYellow.copy(alpha = 0.90f), 1.6.dp.toPx())
        mirrorDrawMinutePath(positioned, ::xFor, { yFor(it.price) }, if (stock.quote.isRising) MirrorRise else MirrorFall, 2.4.dp.toPx())
        if (!isFiveDay) {
            val limitRatio = if (stock.quote.name.contains("ST", true)) 0.05f else 0.10f
            mirrorDrawText(mirrorPrice(previousClose * (1f + limitRatio)), width * 0.14f + 5.dp.toPx(), 4.dp.toPx(), 8.sp.toPx(), MirrorRise)
            mirrorDrawText(mirrorPrice(previousClose), width * 0.14f + 5.dp.toPx(), yFor(previousClose) - 10.dp.toPx(), 8.sp.toPx(), Color.White.copy(alpha = 0.54f))
            mirrorDrawText(mirrorPrice(previousClose * (1f - limitRatio)), width * 0.14f + 5.dp.toPx(), chartHeight - 12.dp.toPx(), 8.sp.toPx(), MirrorFall)
            mirrorDrawText(String.format(Locale.US, "+%.2f%%", limitRatio * 100f), width - 4.dp.toPx(), 4.dp.toPx(), 8.sp.toPx(), MirrorRise, Paint.Align.RIGHT)
            mirrorDrawText("0.00%", width - 4.dp.toPx(), yFor(previousClose) - 10.dp.toPx(), 8.sp.toPx(), Color.White.copy(alpha = 0.54f), Paint.Align.RIGHT)
            mirrorDrawText(String.format(Locale.US, "-%.2f%%", limitRatio * 100f), width - 4.dp.toPx(), chartHeight - 12.dp.toPx(), 8.sp.toPx(), MirrorFall, Paint.Align.RIGHT)
        }
    }
}

private fun mirrorPositionMinutePoints(points: List<StockMinutePoint>, isFiveDay: Boolean): List<MirrorPositionedMinute> {
    if (!isFiveDay) {
        return points.mapNotNull { point ->
            val parts = mirrorTimeParts(point.time) ?: return@mapNotNull null
            val fraction = mirrorSessionFraction(parts) ?: return@mapNotNull null
            MirrorPositionedMinute(point, fraction, 0, parts)
        }.sortedBy { it.xFraction }
    }
    val dates = mirrorFiveDayDates(points)
    val firstSlot = max(0, 5 - dates.size)
    val slots = dates.mapIndexed { index, date -> date to firstSlot + index }.toMap()
    return points.mapNotNull { point ->
        val parts = mirrorTimeParts(point.time) ?: return@mapNotNull null
        val fraction = mirrorSessionFraction(parts) ?: return@mapNotNull null
        val slot = slots[parts.date] ?: 4
        MirrorPositionedMinute(point, (slot + fraction) / 5f, slot, parts)
    }.sortedWith(compareBy<MirrorPositionedMinute> { it.xFraction }.thenBy { it.parts.secondOfDay })
}

private fun mirrorSessionFraction(parts: MirrorTimeParts): Float? {
    val t = parts.minuteOfDay + (parts.secondOfDay % 60) / 60f
    val openWidth = 0.14f
    val closeStart = 0.95f
    val split = openWidth + (closeStart - openWidth) * 0.5f
    return when {
        t < 9 * 60 + 15 || t > 15 * 60 -> null
        t <= 9 * 60 + 25 -> ((t - (9 * 60 + 15)) / 10f * openWidth).coerceIn(0f, openWidth)
        t < 9 * 60 + 30 -> null
        t <= 11 * 60 + 30 -> openWidth + (t - (9 * 60 + 30)) / 120f * (split - openWidth)
        t < 13 * 60 -> null
        t < 14 * 60 + 57 -> split + (t - 13 * 60) / 117f * (closeStart - split)
        else -> closeStart + (t - (14 * 60 + 57)) / 3f * 0.05f
    }
}

private fun mirrorFiveDayDates(points: List<StockMinutePoint>): List<String> =
    points.mapNotNull { mirrorTimeParts(it.time)?.date?.takeIf(String::isNotBlank) }.distinct().sorted().takeLast(5)

private fun mirrorTimeParts(raw: String): MirrorTimeParts? {
    val match = MirrorTimePattern.find(raw) ?: return null
    val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val second = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    val date = MirrorDatePattern.find(raw)?.groupValues?.getOrNull(1).orEmpty()
    return MirrorTimeParts(date, hour * 60 + minute, (hour * 60 + minute) * 60 + second, "%02d:%02d".format(hour, minute))
}

private fun mirrorPhase(point: StockMinutePoint): String {
    val explicit = point.phase.lowercase()
    if (explicit.contains("open")) return "open"
    if (explicit.contains("close")) return "close"
    val minute = mirrorTimeParts(point.time)?.minuteOfDay ?: return "continuous"
    return when (minute) {
        in 555..565 -> "open"
        in 897..900 -> "close"
        else -> "continuous"
    }
}

private fun mirrorDrawAuctionVolumes(
    scope: DrawScope,
    points: List<MirrorPositionedMinute>,
    width: Float,
    height: Float,
    volumeTop: Float,
    volumeHeight: Float
) = with(scope) {
    val auction = points.filter { mirrorPhase(it.point) != "continuous" }
    val unmatchedMax = auction.maxOfOrNull { it.point.unmatchedVolume ?: 0f } ?: 0f
    val matchedMax = auction.maxOfOrNull { it.point.matchedVolume ?: 0f } ?: 0f
    if (unmatchedMax > 0f) {
        auction.groupBy { mirrorPhase(it.point) to it.point.unmatchedDirection.lowercase() }.values.forEach { segment ->
            val filtered = segment.filter { (it.point.unmatchedVolume ?: 0f) > 0f }.sortedBy { it.xFraction }
            if (filtered.isEmpty()) return@forEach
            val path = Path().apply {
                moveTo(filtered.first().xFraction * width, volumeTop)
                filtered.forEach { item ->
                    lineTo(item.xFraction * width, volumeTop + (item.point.unmatchedVolume ?: 0f) / unmatchedMax * volumeHeight * 0.46f)
                }
                lineTo(filtered.last().xFraction * width, volumeTop)
                close()
            }
            val direction = filtered.first().point.unmatchedDirection.lowercase()
            drawPath(path, if (direction == "sell") Color(0xFF2AC252).copy(alpha = 0.88f) else Color(0xFFFF3934).copy(alpha = 0.90f))
        }
    }
    if (matchedMax > 0f) {
        auction.groupBy { mirrorPhase(it.point) }.values.forEach { segment ->
            val filtered = segment.filter { (it.point.matchedVolume ?: 0f) > 0f }.sortedBy { it.xFraction }
            if (filtered.isEmpty()) return@forEach
            val fill = Path().apply {
                moveTo(filtered.first().xFraction * width, height)
                filtered.forEach { item ->
                    lineTo(item.xFraction * width, height - (item.point.matchedVolume ?: 0f) / matchedMax * volumeHeight * 0.46f)
                }
                lineTo(filtered.last().xFraction * width, height)
                close()
            }
            drawPath(fill, Color(0xFFF3F5FF).copy(alpha = 0.68f))
            val stroke = Path().apply {
                filtered.forEachIndexed { index, item ->
                    val x = item.xFraction * width
                    val y = height - (item.point.matchedVolume ?: 0f) / matchedMax * volumeHeight * 0.46f
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(stroke, Color.White.copy(alpha = 0.80f), style = Stroke(0.75.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.mirrorDrawMinutePath(
    points: List<MirrorPositionedMinute>,
    xFor: (MirrorPositionedMinute) -> Float,
    yFor: (StockMinutePoint) -> Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path()
    var started = false
    var lastSlot = -1
    points.forEach { item ->
        val x = xFor(item)
        val y = yFor(item.point)
        if (!started || item.daySlot != lastSlot) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
        lastSlot = item.daySlot
    }
    if (started) drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

@Composable
private fun MirrorOrderPanel(ui: StockMarketUiState, modifier: Modifier) {
    val quote = ui.stock.quote
    val previousClose = quote.previousClose
    Column(
        modifier = modifier
            .background(MirrorPanelFill, MirrorOrderShape)
            .border(1.dp, MirrorPanelBorder, MirrorOrderShape)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("五档盘口", color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(if (ui.depthState.canDisplayLevels) "实时" else "不可用", color = MirrorAqua, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("盘口", color = Color.White.copy(alpha = 0.30f), fontSize = 6.5.sp, modifier = Modifier.width(24.dp))
            Text("价格", color = Color.White.copy(alpha = 0.30f), fontSize = 6.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("手", color = Color.White.copy(alpha = 0.30f), fontSize = 6.5.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
            val sells = ui.stock.sellLevels.take(5)
            val buys = ui.stock.buyLevels.take(5)
            if (sells.isEmpty() && buys.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("真实五档\n暂不可用", color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp, textAlign = TextAlign.Center)
                }
            } else {
                sells.forEach { MirrorDepthRow(it, previousClose) }
                Box(Modifier.fillMaxWidth().height(2.dp).background(MirrorAqua.copy(alpha = 0.58f), RoundedCornerShape(99.dp)))
                buys.forEach { MirrorDepthRow(it, previousClose) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("逐笔成交", color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("明细", color = MirrorAqua, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.fillMaxWidth().weight(0.62f), verticalArrangement = Arrangement.SpaceEvenly) {
            val ticks = ui.stock.tradeTicks.takeLast(8)
            if (ticks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("真实逐笔\n暂不可用", color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp, textAlign = TextAlign.Center)
                }
            } else {
                ticks.forEach { MirrorTickRow(it, previousClose) }
            }
        }
    }
}

@Composable
private fun MirrorDepthRow(level: StockOrderLevel, previousClose: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(level.label, color = Color.White.copy(alpha = 0.37f), fontSize = 8.sp, modifier = Modifier.width(24.dp))
        Text(level.price, color = mirrorPriceTone(level.price, previousClose), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(level.volume, color = Color.White.copy(alpha = 0.62f), fontSize = 8.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun MirrorTickRow(tick: StockTradeTick, previousClose: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tick.time, color = Color.White.copy(alpha = 0.37f), fontSize = 7.sp, modifier = Modifier.width(30.dp), maxLines = 1)
        Text(tick.price, color = mirrorPriceTone(tick.price, previousClose), fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(tick.volume, color = Color.White.copy(alpha = 0.62f), fontSize = 7.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun MirrorKLineTerminal(
    ui: StockMarketUiState,
    indicator: String,
    onIndicatorChange: (String) -> Unit
) {
    val candles = ui.stock.kLinePoints
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(ui.stock.quote.code, ui.selectedTab) {
        zoom = 1f
        pan = 0f
        selectedIndex = -1
    }
    fun visibleCount(currentZoom: Float): Int {
        if (candles.isEmpty()) return 0
        val base = min(72, candles.size)
        val minimum = min(12, candles.size)
        return (base / currentZoom).roundToInt().coerceIn(minimum, candles.size)
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        val count = visibleCount(nextZoom).coerceAtLeast(1)
        val candleWidth = widthPx / count
        val maxPan = (candles.size - count).coerceAtLeast(0).toFloat()
        zoom = nextZoom
        pan = (pan + panChange.x / candleWidth.coerceAtLeast(1f)).coerceIn(0f, maxPan)
    }
    val count = visibleCount(zoom)
    val maxPan = (candles.size - count).coerceAtLeast(0)
    val end = if (candles.isEmpty()) 0 else (candles.size - pan.roundToInt().coerceIn(0, maxPan)).coerceIn(count, candles.size)
    val start = (end - count).coerceAtLeast(0)
    val window = MirrorKWindow(start, end, if (candles.isEmpty()) emptyList() else candles.subList(start, end))
    val selected = candles.getOrNull(selectedIndex)
    val ma20 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 20) }
    val ma30 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 30) }
    val vma5 = remember(candles) { mirrorMovingAverage(candles.map { it.volume }, 5) }
    val vma10 = remember(candles) { mirrorMovingAverage(candles.map { it.volume }, 10) }
    val snapshotLabel = mirrorIndicatorLabel(indicator)
    Column(Modifier.fillMaxWidth().weight(1f)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(ui.stock.quote.code, ui.selectedTab, start, count) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (count > 0) {
                                val local = ((offset.x / widthPx) * count).toInt().coerceIn(0, count - 1)
                                selectedIndex = start + local
                            }
                        },
                        onDoubleTap = {
                            zoom = 1f
                            pan = 0f
                            selectedIndex = -1
                        }
                    )
                }
                .transformable(state = transformState, lockRotationOnZoomPan = true)
        ) {
            val panel = mirrorKPanelLayoutDp(maxHeight)
            MirrorKLineCanvas(
                candles = candles,
                window = window,
                indicator = indicator,
                selectedGlobalIndex = selectedIndex,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = selected?.let {
                    "${it.date}  开${mirrorPrice(it.open)} 高${mirrorPrice(it.high)} 低${mirrorPrice(it.low)} 收${mirrorPrice(it.close)} 涨跌${it.changePercent} 振幅${it.amplitude} 换手${it.turnoverRate} 量${mirrorFormatVolume(it.volume)} 额${mirrorFormatMoney(it.amount)}"
                } ?: "${window.visible.size}根 · 滚轮/双指缩放 · 拖拽平移 · 点击查看完整OHLC与量价数据",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 8.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)
            )
            Row(
                modifier = Modifier
                    .offset(y = panel.indicatorTop + 3.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("MACD", "KDJ", "RSI", "BOLL").forEach { item ->
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .background(if (indicator == item) MirrorAqua.copy(alpha = 0.15f) else Color(0xFF080D26).copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                            .border(1.dp, if (indicator == item) MirrorAqua.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                            .clickable { onIndicatorChange(item) }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item, color = if (indicator == item) Color.White else Color.White.copy(alpha = 0.58f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(mirrorDateLabel(window.visible.firstOrNull()?.date), color = Color.White.copy(alpha = 0.38f), fontSize = 7.sp)
            Text(mirrorDateLabel(window.visible.getOrNull(window.visible.size / 2)?.date), color = Color.White.copy(alpha = 0.38f), fontSize = 7.sp)
            Text(mirrorDateLabel(window.visible.lastOrNull()?.date), color = Color.White.copy(alpha = 0.38f), fontSize = 7.sp)
        }
        MirrorCaption(
            listOf(
                "MA20 ${mirrorPrice(ma20.lastOrNull())} · MA30 ${mirrorPrice(ma30.lastOrNull())}",
                "量M5 ${mirrorFormatVolume(vma5.lastOrNull())} · M10 ${mirrorFormatVolume(vma10.lastOrNull())}",
                "$snapshotLabel · 缩放${String.format(Locale.US, "%.2f", zoom)}x"
            )
        )
    }
}

private data class MirrorPanelDp(val indicatorTop: Dp)

private fun mirrorKPanelLayoutDp(total: Dp): MirrorPanelDp {
    val totalValue = total.value.coerceAtLeast(1f)
    val gap = (totalValue * 0.012f).coerceIn(4f, 8f)
    val usable = (totalValue - gap * 2f).coerceAtLeast(1f)
    val main = usable * 0.52f
    val volume = usable * 0.24f
    return MirrorPanelDp((main + gap + volume + gap).dp)
}

@Composable
private fun MirrorKLineCanvas(
    candles: List<StockKLinePoint>,
    window: MirrorKWindow,
    indicator: String,
    selectedGlobalIndex: Int,
    modifier: Modifier
) {
    val ma5 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 5) }
    val ma10 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 10) }
    val ma20 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 20) }
    val ma30 = remember(candles) { mirrorMovingAverage(candles.map { it.close }, 30) }
    val vma5 = remember(candles) { mirrorMovingAverage(candles.map { it.volume }, 5) }
    val vma10 = remember(candles) { mirrorMovingAverage(candles.map { it.volume }, 10) }
    val macd = remember(candles) { mirrorMacd(candles) }
    val kdj = remember(candles) { mirrorKdj(candles) }
    val rsi6 = remember(candles) { mirrorRsi(candles, 6) }
    val rsi12 = remember(candles) { mirrorRsi(candles, 12) }
    val rsi24 = remember(candles) { mirrorRsi(candles, 24) }
    val boll = remember(candles) { mirrorBoll(candles) }
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val layout = mirrorKPanelLayout(height)
        mirrorDrawGrid(this, width, layout.mainHeight, 5)
        mirrorDrawPanelGrid(this, width, layout.volumeTop, layout.volumeHeight, 2, 5)
        mirrorDrawPanelGrid(this, width, layout.indicatorTop, layout.indicatorHeight, 2, 5)
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, layout.volumeTop - layout.gap / 2f), Offset(width, layout.volumeTop - layout.gap / 2f), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, layout.indicatorTop - layout.gap / 2f), Offset(width, layout.indicatorTop - layout.gap / 2f), 1.dp.toPx())
        val visible = window.visible
        if (visible.size < 2) {
            mirrorDrawText("等待真实K线数据", width / 2f, layout.mainHeight / 2f, 12.sp.toPx(), Color.White.copy(alpha = 0.42f), Paint.Align.CENTER)
            return@Canvas
        }
        val rangeValues = visible.flatMap { listOf(it.low, it.high) }.toMutableList()
        if (indicator == "BOLL") {
            for (index in window.start until window.end) {
                boll.upper.getOrNull(index)?.let(rangeValues::add)
                boll.lower.getOrNull(index)?.let(rangeValues::add)
            }
        }
        val rawMin = rangeValues.minOrNull() ?: return@Canvas
        val rawMax = rangeValues.maxOrNull() ?: return@Canvas
        val padding = max(max((rawMax - rawMin) * 0.06f, rawMax * 0.0015f), 0.01f)
        val bottom = rawMin - padding
        val top = rawMax + padding
        val range = (top - bottom).coerceAtLeast(0.0001f)
        val step = width / visible.size
        val bodyWidth = (step * 0.58f).coerceIn(2.2.dp.toPx(), 12.dp.toPx())
        fun xFor(index: Int) = index * step + step / 2f
        fun yFor(value: Float) = layout.mainHeight - (value - bottom) / range * layout.mainHeight
        visible.forEachIndexed { index, candle ->
            val x = xFor(index)
            val color = if (candle.close >= candle.open) MirrorRise else MirrorFall
            val highY = yFor(candle.high)
            val lowY = yFor(candle.low)
            val openY = yFor(candle.open)
            val closeY = yFor(candle.close)
            drawLine(color, Offset(x, highY), Offset(x, lowY), 1.1.dp.toPx(), cap = StrokeCap.Butt)
            val from = min(openY, closeY)
            val to = max(openY, closeY).coerceAtLeast(from + 1f)
            drawLine(color, Offset(x, from), Offset(x, to), max(1.6.dp.toPx(), bodyWidth), cap = StrokeCap.Butt)
        }
        mirrorDrawSeries(ma5, window, ::xFor, ::yFor, MirrorYellow, 1.45.dp.toPx())
        mirrorDrawSeries(ma10, window, ::xFor, ::yFor, MirrorBlue, 1.25.dp.toPx())
        mirrorDrawSeries(ma20, window, ::xFor, ::yFor, MirrorPink, 1.15.dp.toPx())
        mirrorDrawSeries(ma30, window, ::xFor, ::yFor, MirrorGreen, 1.15.dp.toPx())
        if (indicator == "BOLL") {
            mirrorDrawSeries(boll.upper, window, ::xFor, ::yFor, MirrorOrange, 1.05.dp.toPx())
            mirrorDrawSeries(boll.mid, window, ::xFor, ::yFor, Color.White.copy(alpha = 0.78f), 1.05.dp.toPx())
            mirrorDrawSeries(boll.lower, window, ::xFor, ::yFor, MirrorOrange, 1.05.dp.toPx())
        }
        val maxVolume = visible.maxOfOrNull { it.volume }?.coerceAtLeast(1f) ?: 1f
        fun volumeY(value: Float) = layout.volumeTop + layout.volumeHeight - value / maxVolume * layout.volumeHeight * 0.76f
        visible.forEachIndexed { index, candle ->
            val x = xFor(index)
            val color = if (candle.close >= candle.open) MirrorRise.copy(alpha = 0.62f) else MirrorFall.copy(alpha = 0.62f)
            drawLine(color, Offset(x, layout.volumeTop + layout.volumeHeight), Offset(x, volumeY(candle.volume)), bodyWidth * 0.82f, cap = StrokeCap.Butt)
        }
        mirrorDrawSeries(vma5, window, ::xFor, ::volumeY, Color.White.copy(alpha = 0.90f), 1.1.dp.toPx())
        mirrorDrawSeries(vma10, window, ::xFor, ::volumeY, MirrorOrange, 1.1.dp.toPx())
        val latest = visible.last()
        val latestGlobal = window.end - 1
        mirrorDrawText("成交量", 5.dp.toPx(), layout.volumeTop + 4.dp.toPx(), 7.5.sp.toPx(), Color.White.copy(alpha = 0.86f))
        mirrorDrawText("量 ${mirrorFormatVolume(latest.volume)}", 43.dp.toPx(), layout.volumeTop + 4.dp.toPx(), 7.5.sp.toPx(), if (latest.close >= latest.open) MirrorRise else MirrorFall)
        mirrorDrawText("M5 ${mirrorFormatVolume(vma5.getOrNull(latestGlobal))}", width * 0.29f, layout.volumeTop + 4.dp.toPx(), 7.5.sp.toPx(), Color.White.copy(alpha = 0.78f))
        mirrorDrawText("M10 ${mirrorFormatVolume(vma10.getOrNull(latestGlobal))}", width * 0.51f, layout.volumeTop + 4.dp.toPx(), 7.5.sp.toPx(), MirrorOrange)
        mirrorDrawText("换手 ${latest.turnoverRate}", width - 5.dp.toPx(), layout.volumeTop + 4.dp.toPx(), 7.5.sp.toPx(), MirrorBlue, Paint.Align.RIGHT)
        mirrorDrawIndicator(this, indicator, candles, window, ::xFor, layout, macd, kdj, rsi6, rsi12, rsi24, boll)
        val selectedLocal = selectedGlobalIndex - window.start
        if (selectedLocal in visible.indices) {
            val candle = visible[selectedLocal]
            val x = xFor(selectedLocal)
            val y = yFor(candle.close)
            drawLine(MirrorAqua.copy(alpha = 0.58f), Offset(x, 0f), Offset(x, height - 2f), 1.dp.toPx())
            drawLine(MirrorAqua.copy(alpha = 0.38f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
        }
        mirrorDrawText(mirrorPrice(top), width - 4.dp.toPx(), 3.dp.toPx(), 8.sp.toPx(), Color.White.copy(alpha = 0.48f), Paint.Align.RIGHT)
        mirrorDrawText(mirrorPrice((top + bottom) / 2f), width - 4.dp.toPx(), layout.mainHeight / 2f, 8.sp.toPx(), Color.White.copy(alpha = 0.48f), Paint.Align.RIGHT)
        mirrorDrawText(mirrorPrice(bottom), width - 4.dp.toPx(), layout.mainHeight - 12.dp.toPx(), 8.sp.toPx(), Color.White.copy(alpha = 0.48f), Paint.Align.RIGHT)
    }
}

private fun mirrorKPanelLayout(height: Float): MirrorKPanelLayout {
    val gap = (height * 0.012f).coerceIn(4f, 8f)
    val usable = (height - gap * 2f).coerceAtLeast(1f)
    val main = usable * 0.52f
    val volume = usable * 0.24f
    val indicator = usable - main - volume
    return MirrorKPanelLayout(gap, main, main + gap, volume, main + gap + volume + gap, indicator)
}

private fun DrawScope.mirrorDrawSeries(
    series: List<Float?>,
    window: MirrorKWindow,
    xFor: (Int) -> Float,
    yFor: (Float) -> Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path()
    var started = false
    var hasPoint = false
    window.visible.indices.forEach { local ->
        val value = series.getOrNull(window.start + local)
        if (value == null || !value.isFinite()) {
            started = false
            return@forEach
        }
        val x = xFor(local)
        val y = yFor(value)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else path.lineTo(x, y)
        hasPoint = true
    }
    if (hasPoint) drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

private fun mirrorDrawIndicator(
    scope: DrawScope,
    indicator: String,
    candles: List<StockKLinePoint>,
    window: MirrorKWindow,
    xFor: (Int) -> Float,
    layout: MirrorKPanelLayout,
    macd: MirrorMacd,
    kdj: MirrorKdj,
    rsi6: List<Float?>,
    rsi12: List<Float?>,
    rsi24: List<Float?>,
    boll: MirrorBoll
) = with(scope) {
    val label = when (indicator) {
        "KDJ" -> "KDJ(9,3,3)  K:${mirrorPrice(kdj.k.lastOrNull(), 3)}  D:${mirrorPrice(kdj.d.lastOrNull(), 3)}  J:${mirrorPrice(kdj.j.lastOrNull(), 3)}"
        "RSI" -> "RSI  R6:${mirrorPrice(rsi6.lastOrNull(), 3)}  R12:${mirrorPrice(rsi12.lastOrNull(), 3)}  R24:${mirrorPrice(rsi24.lastOrNull(), 3)}"
        "BOLL" -> "BOLL(20,2)  UP:${mirrorPrice(boll.upper.lastOrNull(), 3)}  MID:${mirrorPrice(boll.mid.lastOrNull(), 3)}  LOW:${mirrorPrice(boll.lower.lastOrNull(), 3)}  BW:${mirrorPrice(boll.bandwidth.lastOrNull(), 3)}"
        else -> "MACD(12,26,9)  DIF:${mirrorPrice(macd.dif.lastOrNull(), 3)}  DEA:${mirrorPrice(macd.dea.lastOrNull(), 3)}  MACD:${mirrorPrice(macd.histogram.lastOrNull(), 3)}"
    }
    mirrorDrawText(label, 6.dp.toPx(), layout.indicatorTop + 25.dp.toPx(), 7.2.sp.toPx(), Color.White.copy(alpha = 0.88f))
    val plotTop = layout.indicatorTop + 41.dp.toPx()
    val plotHeight = (layout.indicatorHeight - 47.dp.toPx()).coerceAtLeast(24.dp.toPx())
    fun drawLineSeries(series: List<Float?>, minValue: Float, maxValue: Float, color: Color, width: Float = 1.15f) {
        val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
        mirrorDrawSeries(series, window, xFor, { value -> (plotTop + plotHeight - (value - minValue) / range * plotHeight).coerceIn(plotTop + 1f, plotTop + plotHeight - 1f) }, color, width.dp.toPx())
    }
    when (indicator) {
        "KDJ" -> {
            val all = (kdj.k.subListSafe(window.start, window.end) + kdj.d.subListSafe(window.start, window.end) + kdj.j.subListSafe(window.start, window.end)).filterNotNull()
            val rawMin = min(0f, all.minOrNull() ?: 0f)
            val rawMax = max(100f, all.maxOrNull() ?: 100f)
            val pad = max((rawMax - rawMin) * 0.06f, 3f)
            val minValue = rawMin - pad
            val maxValue = rawMax + pad
            mirrorThreshold(this, size.width, plotTop, plotHeight, minValue, maxValue, 20f)
            mirrorThreshold(this, size.width, plotTop, plotHeight, minValue, maxValue, 80f)
            drawLineSeries(kdj.k, minValue, maxValue, MirrorYellow)
            drawLineSeries(kdj.d, minValue, maxValue, MirrorBlue)
            drawLineSeries(kdj.j, minValue, maxValue, MirrorPink)
        }
        "RSI" -> {
            mirrorThreshold(this, size.width, plotTop, plotHeight, 0f, 100f, 30f)
            mirrorThreshold(this, size.width, plotTop, plotHeight, 0f, 100f, 70f)
            drawLineSeries(rsi6, 0f, 100f, MirrorYellow)
            drawLineSeries(rsi12, 0f, 100f, MirrorBlue)
            drawLineSeries(rsi24, 0f, 100f, MirrorPink)
        }
        "BOLL" -> {
            val values = boll.percentB.subListSafe(window.start, window.end).filterNotNull()
            val rawMin = min(-0.1f, values.minOrNull() ?: -0.1f)
            val rawMax = max(1.1f, values.maxOrNull() ?: 1.1f)
            val pad = max((rawMax - rawMin) * 0.08f, 0.08f)
            val minValue = rawMin - pad
            val maxValue = rawMax + pad
            mirrorThreshold(this, size.width, plotTop, plotHeight, minValue, maxValue, 0f)
            mirrorThreshold(this, size.width, plotTop, plotHeight, minValue, maxValue, 1f)
            drawLineSeries(boll.percentB, minValue, maxValue, MirrorYellow, 1.35f)
        }
        else -> {
            val all = (macd.dif.subListSafe(window.start, window.end) + macd.dea.subListSafe(window.start, window.end) + macd.histogram.subListSafe(window.start, window.end)).filterNotNull()
            val maxAbs = max(0.0001f, all.maxOfOrNull { abs(it) } ?: 0.0001f)
            val zeroY = plotTop + plotHeight / 2f
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            window.visible.indices.forEach { local ->
                val value = macd.histogram.getOrNull(window.start + local) ?: return@forEach
                val x = xFor(local)
                val y = (zeroY - value / maxAbs * plotHeight / 2f * 0.88f).coerceIn(plotTop + 1f, plotTop + plotHeight - 1f)
                drawLine(if (value >= 0f) MirrorRise else MirrorFall, Offset(x, zeroY), Offset(x, y), max(1f, size.width / window.visible.size * 0.52f), cap = StrokeCap.Butt)
            }
            drawLineSeries(macd.dif, -maxAbs, maxAbs, MirrorYellow)
            drawLineSeries(macd.dea, -maxAbs, maxAbs, MirrorBlue)
        }
    }
}

private fun mirrorIndicatorLabel(indicator: String): String = when (indicator) {
    "KDJ" -> "KDJ(9,3,3)"
    "RSI" -> "RSI"
    "BOLL" -> "BOLL(20,2)"
    else -> "MACD(12,26,9)"
}

private fun mirrorMovingAverage(values: List<Float>, period: Int): List<Float?> {
    val result = MutableList<Float?>(values.size) { null }
    var sum = 0f
    values.forEachIndexed { index, value ->
        sum += value
        if (index >= period) sum -= values[index - period]
        if (index >= period - 1) result[index] = sum / period
    }
    return result
}

private fun mirrorEma(values: List<Float?>, period: Int): List<Float?> {
    val result = MutableList<Float?>(values.size) { null }
    val alpha = 2f / (period + 1f)
    var previous: Float? = null
    values.forEachIndexed { index, value ->
        if (value != null) {
            previous = previous?.let { it + alpha * (value - it) } ?: value
            result[index] = previous
        }
    }
    return result
}

private fun mirrorMacd(candles: List<StockKLinePoint>): MirrorMacd {
    val close: List<Float?> = candles.map { it.close }
    val ema12 = mirrorEma(close, 12)
    val ema26 = mirrorEma(close, 26)
    val dif = close.indices.map { index -> if (ema12[index] != null && ema26[index] != null) ema12[index]!! - ema26[index]!! else null }
    val dea = mirrorEma(dif, 9)
    val histogram = dif.indices.map { index -> if (dif[index] != null && dea[index] != null) (dif[index]!! - dea[index]!!) * 2f else null }
    return MirrorMacd(dif, dea, histogram)
}

private fun mirrorKdj(candles: List<StockKLinePoint>, period: Int = 9): MirrorKdj {
    val k = MutableList<Float?>(candles.size) { null }
    val d = MutableList<Float?>(candles.size) { null }
    val j = MutableList<Float?>(candles.size) { null }
    var previousK = 50f
    var previousD = 50f
    candles.forEachIndexed { index, candle ->
        val start = max(0, index - period + 1)
        val window = candles.subList(start, index + 1)
        val highest = window.maxOf { it.high }
        val lowest = window.minOf { it.low }
        val rsv = if (highest > lowest) (candle.close - lowest) / (highest - lowest) * 100f else 50f
        previousK = previousK * 2f / 3f + rsv / 3f
        previousD = previousD * 2f / 3f + previousK / 3f
        k[index] = previousK
        d[index] = previousD
        j[index] = 3f * previousK - 2f * previousD
    }
    return MirrorKdj(k, d, j)
}

private fun mirrorRsi(candles: List<StockKLinePoint>, period: Int): List<Float?> {
    val result = MutableList<Float?>(candles.size) { null }
    if (candles.size < 2) return result
    var averageGain = 0f
    var averageLoss = 0f
    for (index in 1 until candles.size) {
        val change = candles[index].close - candles[index - 1].close
        val gain = max(change, 0f)
        val loss = max(-change, 0f)
        if (index <= period) {
            averageGain += gain
            averageLoss += loss
            if (index == period) {
                averageGain /= period
                averageLoss /= period
                result[index] = if (averageLoss == 0f) 100f else 100f - 100f / (1f + averageGain / averageLoss)
            }
        } else {
            averageGain = (averageGain * (period - 1) + gain) / period
            averageLoss = (averageLoss * (period - 1) + loss) / period
            result[index] = if (averageLoss == 0f) 100f else 100f - 100f / (1f + averageGain / averageLoss)
        }
    }
    return result
}

private fun mirrorBoll(candles: List<StockKLinePoint>, period: Int = 20, multiplier: Float = 2f): MirrorBoll {
    val close = candles.map { it.close }
    val mid = mirrorMovingAverage(close, period)
    val upper = MutableList<Float?>(candles.size) { null }
    val lower = MutableList<Float?>(candles.size) { null }
    val percentB = MutableList<Float?>(candles.size) { null }
    val bandwidth = MutableList<Float?>(candles.size) { null }
    for (index in period - 1 until candles.size) {
        val mean = mid[index] ?: continue
        val window = close.subList(index - period + 1, index + 1)
        val variance = window.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / period
        val standard = sqrt(variance)
        val up = mean + multiplier * standard
        val low = mean - multiplier * standard
        upper[index] = up
        lower[index] = low
        bandwidth[index] = if (mean != 0f) (up - low) / mean * 100f else null
        percentB[index] = if (up != low) (close[index] - low) / (up - low) else 0.5f
    }
    return MirrorBoll(mid, upper, lower, percentB, bandwidth)
}

private fun mirrorPriceTone(value: String, zero: Float): Color {
    val price = mirrorNumber(value) ?: return Color.White.copy(alpha = 0.86f)
    if (zero <= 0f) return Color.White.copy(alpha = 0.86f)
    return when {
        price > zero -> MirrorRise
        price < zero -> MirrorFall
        else -> Color.White.copy(alpha = 0.86f)
    }
}

private fun mirrorNumber(value: String): Float? = value.replace(",", "").replace("%", "").replace("亿", "").replace("万", "").replace("手", "").trim().toFloatOrNull()

private fun mirrorPrice(value: Float?, digits: Int = 2): String = value?.takeIf(Float::isFinite)?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "--"

private fun mirrorFormatVolume(value: Float?): String {
    val number = value ?: return "--"
    return when {
        number >= 100_000_000f -> String.format(Locale.US, "%.2f亿", number / 100_000_000f)
        number >= 10_000f -> String.format(Locale.US, "%.2f万", number / 10_000f)
        else -> number.roundToInt().toString()
    }
}

private fun mirrorFormatMoney(value: Float?): String = mirrorFormatVolume(value)

private fun mirrorDateLabel(raw: String?): String {
    val text = raw.orEmpty()
    val match = Regex("""(\d{4})[-/.]?(\d{2})[-/.]?(\d{2})""").find(text)
    return match?.let { "${it.groupValues[2]}-${it.groupValues[3]}" } ?: text.takeLast(5).ifBlank { "--" }
}

private fun mirrorDrawGrid(scope: DrawScope, width: Float, chartHeight: Float, verticals: Int) = with(scope) {
    repeat(4) { index ->
        val y = chartHeight * (index + 1) / 5f
        drawLine(MirrorGrid, Offset(0f, y), Offset(width, y), 1.dp.toPx())
    }
    repeat(verticals) { index ->
        val x = width * (index + 1) / (verticals + 1f)
        drawLine(MirrorSoftGrid, Offset(x, 0f), Offset(x, chartHeight), 1.dp.toPx())
    }
}

private fun mirrorDrawPanelGrid(scope: DrawScope, width: Float, top: Float, height: Float, horizontal: Int, vertical: Int) = with(scope) {
    repeat(horizontal) { index ->
        val y = top + height * (index + 1) / (horizontal + 1f)
        drawLine(Color.White.copy(alpha = 0.085f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
    }
    repeat(vertical) { index ->
        val x = width * (index + 1) / (vertical + 1f)
        drawLine(Color.White.copy(alpha = 0.05f), Offset(x, top), Offset(x, top + height), 1.dp.toPx())
    }
}

private fun DrawScope.mirrorDrawText(
    text: String,
    x: Float,
    y: Float,
    textSize: Float,
    color: Color,
    align: Paint.Align = Paint.Align.LEFT
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y + textSize,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = android.graphics.Color.argb((color.alpha * 255).roundToInt(), (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())
            textAlign = align
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    )
}

private fun DrawScope.mirrorDashedLine(start: Offset, end: Offset, color: Color, dash: Float, gap: Float) {
    var x = start.x
    while (x < end.x) {
        val to = min(end.x, x + dash)
        drawLine(color, Offset(x, start.y), Offset(to, end.y), 1.dp.toPx())
        x += dash + gap
    }
}

private fun mirrorThreshold(scope: DrawScope, width: Float, top: Float, height: Float, minValue: Float, maxValue: Float, value: Float) = with(scope) {
    if (value !in minValue..maxValue) return@with
    val y = top + height - (value - minValue) / (maxValue - minValue).coerceAtLeast(0.0001f) * height
    mirrorDashedLine(Offset(0f, y), Offset(width, y), Color.White.copy(alpha = 0.14f), 4.dp.toPx(), 4.dp.toPx())
}

private fun <T> List<T>.subListSafe(start: Int, end: Int): List<T> = if (isEmpty()) emptyList() else subList(start.coerceIn(0, size), end.coerceIn(start.coerceIn(0, size), size))
