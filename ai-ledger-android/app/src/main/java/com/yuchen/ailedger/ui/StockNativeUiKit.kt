package com.yuchen.ailedger.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.StockKLinePoint
import com.yuchen.ailedger.model.StockMinutePoint
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal val StockRise = Color(0xFFFF8F8F)
internal val StockFall = Color(0xFF80F7B4)
internal val StockAqua = Color(0xFF8DF9EA)
internal val StockYellow = Color(0xFFFFD86B)
internal val StockBlue = Color(0xFF70D8FF)
internal val StockPanel = Color(0xFF0B1028)
internal val StockPanelDeep = Color(0xFF070B1D)
internal val StockLine = Color.White.copy(alpha = 0.075f)
internal val StockMuted = Color.White.copy(alpha = 0.40f)
internal val StockSoft = Color.White.copy(alpha = 0.055f)
internal val StockPillShape = RoundedCornerShape(999.dp)

internal fun stockTone(value: String): Color =
    if (value.trim().startsWith("-")) StockFall else StockRise

internal fun stockFlowTone(value: String): Color = when {
    value.isBlank() || value == "--" -> Color.White.copy(alpha = 0.65f)
    value.trim().startsWith("-") -> StockFall
    else -> StockRise
}

internal fun stockPercent(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f%%", it) } ?: "--"

internal fun stockCount(value: Int?): String = value?.toString() ?: "--"

internal fun compactCount(value: Int): String = when {
    value >= 100_000_000 -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000 -> String.format(Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

@Composable
internal fun StockNativeGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE6101738),
                        Color(0xF20A1027),
                        Color(0xF6070B1D)
                    )
                ),
                shape
            )
            .border(1.dp, Color(0xFF7D8EC0).copy(alpha = 0.22f), shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
internal fun StockNativePageHeader(
    label: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    loading: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StockNativePill(
            text = "‹",
            active = false,
            modifier = Modifier.size(42.dp),
            fontSize = 26,
            onClick = onBack
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onRefresh != null) {
            StockNativePill(
                text = if (loading) "…" else "⟳",
                active = false,
                modifier = Modifier.size(42.dp),
                fontSize = 18,
                onClick = onRefresh
            )
        } else {
            Spacer(Modifier.width(42.dp))
        }
    }
}

@Composable
internal fun StockNativePill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Int = 11,
    onClick: () -> Unit
) {
    val background = if (active) {
        Brush.horizontalGradient(
            listOf(Color(0xFF8092D8).copy(alpha = 0.34f), StockAqua.copy(alpha = 0.13f))
        )
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.05f)))
    }
    Box(
        modifier = modifier
            .background(background, StockPillShape)
            .border(
                1.dp,
                if (active) StockAqua.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.065f),
                StockPillShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.76f),
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun StockSectionTitle(
    title: String,
    subtitle: String,
    trailing: String? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 8.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
internal fun StockDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(StockLine))
}

@Composable
internal fun StockMetricTile(
    label: String,
    value: String,
    tone: Color = Color.White.copy(alpha = 0.92f),
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    Column(
        modifier = modifier
            .height(if (prominent) 62.dp else 54.dp)
            .background(StockSoft, RoundedCornerShape(16.dp))
            .border(1.dp, tone.copy(alpha = if (prominent) 0.14f else 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = StockMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            value.ifBlank { "--" },
            color = tone,
            fontSize = if (prominent) 14.sp else 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun StockLoadingOrError(
    loading: Boolean,
    error: String?,
    emptyText: String,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(
            when {
                loading -> "正在加载…"
                !error.isNullOrBlank() -> error
                else -> emptyText
            },
            color = if (error.isNullOrBlank()) Color.White.copy(alpha = 0.42f) else StockYellow.copy(alpha = 0.82f),
            fontSize = 10.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun StockTextAvatar(author: String, modifier: Modifier = Modifier) {
    val hue = remember(author) {
        author.fold(0) { acc, char -> (acc * 31 + char.code) % 360 }
    }
    val first = author.trim().firstOrNull()?.toString() ?: "股"
    val base = Color.hsv(hue.toFloat(), 0.56f, 0.78f)
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    listOf(base.copy(alpha = 0.96f), base.copy(alpha = 0.54f), Color(0xFF17213F)),
                    center = Offset(22f, 15f),
                    radius = 58f
                ),
                CircleShape
            )
            .border(1.dp, base.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun StockNativeTrendChart(
    minutePoints: List<StockMinutePoint>,
    klinePoints: List<StockKLinePoint>,
    showKline: Boolean,
    modifier: Modifier = Modifier
) {
    if (showKline) {
        StockNativeKlineChart(klinePoints, modifier)
    } else {
        StockNativeMinuteChart(minutePoints, modifier)
    }
}

@Composable
private fun StockNativeMinuteChart(points: List<StockMinutePoint>, modifier: Modifier) {
    val rows = remember(points) { points.filter { it.price > 0f } }
    if (rows.isEmpty()) {
        StockLoadingOrError(false, null, "真实分时数据暂不可用", modifier)
        return
    }
    Canvas(modifier = modifier) {
        val left = 38.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val priceBottom = size.height * 0.72f
        val volumeTop = priceBottom + 10.dp.toPx()
        val volumeBottom = size.height - 8.dp.toPx()
        drawStockGrid(left, top, right, priceBottom)

        val prices = rows.map { it.price }
        val averages = rows.map { it.average }.filter { it > 0f }
        var low = min(prices.minOrNull() ?: 0f, averages.minOrNull() ?: Float.MAX_VALUE)
        var high = max(prices.maxOrNull() ?: 1f, averages.maxOrNull() ?: 1f)
        if (!low.isFinite()) low = prices.minOrNull() ?: 0f
        if (high <= low) high = low + 1f
        val padding = max((high - low) * 0.12f, high * 0.0025f)
        low -= padding
        high += padding
        val x = { index: Int -> left + (right - left) * index / max(rows.lastIndex, 1).toFloat() }
        val y = { value: Float -> top + (high - value) / (high - low) * (priceBottom - top) }

        val maxVolume = max(rows.maxOfOrNull { it.volume } ?: 0f, 1f)
        val barWidth = max(1f, (right - left) / rows.size * 0.7f)
        rows.forEachIndexed { index, row ->
            val barHeight = row.volume / maxVolume * (volumeBottom - volumeTop)
            val previous = rows.getOrNull(index - 1)?.price ?: row.price
            drawRect(
                color = if (row.price >= previous) StockRise.copy(alpha = 0.72f) else StockFall.copy(alpha = 0.72f),
                topLeft = Offset(x(index) - barWidth / 2f, volumeBottom - barHeight),
                size = Size(barWidth, max(1f, barHeight))
            )
        }

        val pricePath = Path()
        val averagePath = Path()
        rows.forEachIndexed { index, row ->
            val px = x(index)
            val py = y(row.price)
            if (index == 0) pricePath.moveTo(px, py) else pricePath.lineTo(px, py)
            if (row.average > 0f) {
                val ay = y(row.average)
                if (index == 0) averagePath.moveTo(px, ay) else averagePath.lineTo(px, ay)
            }
        }
        drawPath(pricePath, StockBlue, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(averagePath, StockYellow, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StockNativeKlineChart(points: List<StockKLinePoint>, modifier: Modifier) {
    val rows = remember(points) { points.filter { it.open > 0f && it.close > 0f }.takeLast(72) }
    if (rows.size < 2) {
        StockLoadingOrError(false, null, "真实K线数据暂不可用", modifier)
        return
    }
    Canvas(modifier = modifier) {
        val left = 38.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val priceBottom = size.height * 0.72f
        val volumeTop = priceBottom + 10.dp.toPx()
        val volumeBottom = size.height - 8.dp.toPx()
        drawStockGrid(left, top, right, priceBottom)

        var low = rows.minOf { it.low }
        var high = rows.maxOf { it.high }
        if (high <= low) high = low + 1f
        val padding = max((high - low) * 0.08f, high * 0.002f)
        low -= padding
        high += padding
        val step = (right - left) / rows.size
        val bodyWidth = max(2f, step * 0.62f)
        val y = { value: Float -> top + (high - value) / (high - low) * (priceBottom - top) }
        val maxVolume = max(rows.maxOfOrNull { it.volume } ?: 0f, 1f)

        rows.forEachIndexed { index, row ->
            val x = left + step * index + step / 2f
            val rising = row.close >= row.open
            val tone = if (rising) StockRise else StockFall
            drawLine(tone, Offset(x, y(row.high)), Offset(x, y(row.low)), strokeWidth = 1.dp.toPx())
            val bodyTop = min(y(row.open), y(row.close))
            val bodyBottom = max(y(row.open), y(row.close))
            drawRect(
                tone,
                Offset(x - bodyWidth / 2f, bodyTop),
                Size(bodyWidth, max(1.5.dp.toPx(), bodyBottom - bodyTop))
            )
            val volumeHeight = row.volume / maxVolume * (volumeBottom - volumeTop)
            drawRect(
                tone.copy(alpha = 0.72f),
                Offset(x - bodyWidth / 2f, volumeBottom - volumeHeight),
                Size(bodyWidth, max(1f, volumeHeight))
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStockGrid(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) {
    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        drawLine(StockLine, Offset(left, y), Offset(right, y), strokeWidth = 1f)
    }
    repeat(5) { index ->
        val x = left + (right - left) * index / 4f
        drawLine(StockLine, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
    }
}
