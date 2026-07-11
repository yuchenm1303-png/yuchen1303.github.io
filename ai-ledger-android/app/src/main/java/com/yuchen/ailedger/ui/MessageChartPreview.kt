package com.yuchen.ailedger.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.ChartContentBlock
import com.yuchen.ailedger.model.MessageChartPoint
import com.yuchen.ailedger.model.MessageChartSeries
import com.yuchen.ailedger.model.MessageChartType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun MessageChartCanvas(
    block: ChartContentBlock,
    modifier: Modifier = Modifier,
) {
    val validSeries = remember(block.series) {
        block.series.filter { series -> series.points.any { it.y.isFinite() } }
    }
    if (validSeries.isEmpty()) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.14f)),
    ) {
        when (block.type) {
            MessageChartType.Line -> drawLineChart(validSeries, block)
            MessageChartType.Bar -> drawBarChart(validSeries, block)
            MessageChartType.Pie -> drawPieChart(validSeries.first())
            MessageChartType.Scatter -> drawScatterChart(validSeries, block)
        }
    }
}

@Composable
internal fun MessageChartLegend(
    series: List<MessageChartSeries>,
    modifier: Modifier = Modifier,
) {
    if (series.size <= 1) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        series.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(messageChartColor(index)),
                )
                Text(
                    text = item.name.ifBlank { "系列 ${index + 1}" },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.drawLineChart(series: List<MessageChartSeries>, block: ChartContentBlock) {
    val bounds = chartBounds()
    drawChartGrid(bounds, series, block)
    val range = resolveChartRange(series)
    series.forEachIndexed { seriesIndex, item ->
        val points = item.points.filter { it.y.isFinite() }
        if (points.isEmpty()) return@forEachIndexed
        val path = Path()
        points.forEachIndexed { pointIndex, point ->
            val position = mapChartPoint(point, pointIndex, points.size, range, bounds)
            if (pointIndex == 0) path.moveTo(position.first, position.second) else path.lineTo(position.first, position.second)
        }
        drawPath(path, color = messageChartColor(seriesIndex), style = Stroke(width = 2.5.dp.toPx()))
        points.forEachIndexed { pointIndex, point ->
            val position = mapChartPoint(point, pointIndex, points.size, range, bounds)
            drawCircle(messageChartColor(seriesIndex), radius = 3.dp.toPx(), center = Offset(position.first, position.second))
        }
    }
}

private fun DrawScope.drawScatterChart(series: List<MessageChartSeries>, block: ChartContentBlock) {
    val bounds = chartBounds()
    drawChartGrid(bounds, series, block)
    val range = resolveChartRange(series)
    series.forEachIndexed { seriesIndex, item ->
        val points = item.points.filter { it.y.isFinite() }
        points.forEachIndexed { pointIndex, point ->
            val position = mapChartPoint(point, pointIndex, points.size, range, bounds)
            drawCircle(
                color = messageChartColor(seriesIndex).copy(alpha = 0.90f),
                radius = 4.dp.toPx(),
                center = Offset(position.first, position.second),
            )
        }
    }
}

private fun DrawScope.drawBarChart(series: List<MessageChartSeries>, block: ChartContentBlock) {
    val bounds = chartBounds()
    drawChartGrid(bounds, series, block)
    val range = resolveChartRange(series, includeZero = true)
    val maxPointCount = series.maxOfOrNull { it.points.size }?.coerceAtLeast(1) ?: 1
    val groupWidth = bounds.width / maxPointCount
    val barWidth = (groupWidth * 0.72f / series.size.coerceAtLeast(1)).coerceAtLeast(2.dp.toPx())
    val zeroY = mapY(0.0, range.minY, range.maxY, bounds)
    series.forEachIndexed { seriesIndex, item ->
        item.points.forEachIndexed pointLoop@ { pointIndex, point ->
            if (!point.y.isFinite()) return@pointLoop
            val centerX = bounds.left + groupWidth * (pointIndex + 0.5f)
            val left = centerX - groupWidth * 0.36f + barWidth * seriesIndex
            val valueY = mapY(point.y, range.minY, range.maxY, bounds)
            drawRect(
                color = messageChartColor(seriesIndex).copy(alpha = 0.88f),
                topLeft = Offset(left, min(valueY, zeroY)),
                size = Size(barWidth * 0.88f, abs(zeroY - valueY).coerceAtLeast(1f)),
            )
        }
    }
}

private fun DrawScope.drawPieChart(series: MessageChartSeries) {
    val values = series.points.map { max(0.0, it.y) }
    val total = values.sum()
    if (total <= 0.0) return
    val diameter = min(size.width, size.height) * 0.72f
    val left = (size.width - diameter) / 2f
    val top = (size.height - diameter) / 2f
    var start = -90f
    values.forEachIndexed { index, value ->
        val sweep = (value / total * 360.0).toFloat()
        drawArc(
            color = messageChartColor(index),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(left, top),
            size = Size(diameter, diameter),
        )
        start += sweep
    }
}

private data class ChartBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private data class ChartRange(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

private fun DrawScope.chartBounds(): ChartBounds = ChartBounds(
    left = 42.dp.toPx(),
    top = 14.dp.toPx(),
    right = size.width - 12.dp.toPx(),
    bottom = size.height - 28.dp.toPx(),
)

private fun resolveChartRange(series: List<MessageChartSeries>, includeZero: Boolean = false): ChartRange {
    val indexed = series.flatMap { item ->
        item.points.mapIndexedNotNull { index, point ->
            point.takeIf { it.y.isFinite() }?.let { (it.x ?: index.toDouble()) to it.y }
        }
    }
    var minX = indexed.minOfOrNull { it.first } ?: 0.0
    var maxX = indexed.maxOfOrNull { it.first } ?: 1.0
    var minY = indexed.minOfOrNull { it.second } ?: 0.0
    var maxY = indexed.maxOfOrNull { it.second } ?: 1.0
    if (includeZero) {
        minY = min(minY, 0.0)
        maxY = max(maxY, 0.0)
    }
    if (minX == maxX) {
        minX -= 0.5
        maxX += 0.5
    }
    if (minY == maxY) {
        val padding = max(abs(minY) * 0.08, 1.0)
        minY -= padding
        maxY += padding
    } else {
        val padding = (maxY - minY) * 0.08
        minY -= padding
        maxY += padding
    }
    return ChartRange(minX, maxX, minY, maxY)
}

private fun DrawScope.drawChartGrid(
    bounds: ChartBounds,
    series: List<MessageChartSeries>,
    block: ChartContentBlock,
) {
    val range = resolveChartRange(series, includeZero = block.type == MessageChartType.Bar)
    repeat(5) { index ->
        val ratio = index / 4f
        val y = bounds.bottom - bounds.height * ratio
        drawLine(
            color = Color.White.copy(alpha = if (index == 0) 0.24f else 0.09f),
            start = Offset(bounds.left, y),
            end = Offset(bounds.right, y),
            strokeWidth = 1.dp.toPx(),
        )
        val value = range.minY + (range.maxY - range.minY) * ratio
        drawChartLabel(formatChartNumber(value), 4.dp.toPx(), y + 3.dp.toPx(), Paint.Align.LEFT)
    }
    drawLine(
        color = Color.White.copy(alpha = 0.22f),
        start = Offset(bounds.left, bounds.top),
        end = Offset(bounds.left, bounds.bottom),
        strokeWidth = 1.dp.toPx(),
    )
    val labels = series.firstOrNull()?.points.orEmpty()
    if (labels.isNotEmpty()) {
        val indices = listOf(0, labels.lastIndex / 2, labels.lastIndex).distinct()
        indices.forEach { index ->
            val x = bounds.left + bounds.width * index / max(1, labels.lastIndex).toFloat()
            val label = labels[index].label.ifBlank { formatChartNumber(labels[index].x ?: index.toDouble()) }
            drawChartLabel(label.take(14), x, size.height - 8.dp.toPx(), Paint.Align.CENTER)
        }
    }
    block.yAxisLabel?.takeIf(String::isNotBlank)?.let {
        drawChartLabel(it.take(12), 4.dp.toPx(), 10.dp.toPx(), Paint.Align.LEFT)
    }
    block.xAxisLabel?.takeIf(String::isNotBlank)?.let {
        drawChartLabel(it.take(12), bounds.right, size.height - 8.dp.toPx(), Paint.Align.RIGHT)
    }
}

private fun DrawScope.drawChartLabel(text: String, x: Float, y: Float, align: Paint.Align) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(145, 255, 255, 255)
            textSize = 9.sp.toPx()
            textAlign = align
        }
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}

private fun mapChartPoint(
    point: MessageChartPoint,
    index: Int,
    count: Int,
    range: ChartRange,
    bounds: ChartBounds,
): Pair<Float, Float> {
    val xValue = point.x ?: index.toDouble()
    val x = if (point.x == null && count > 1) {
        bounds.left + bounds.width * index / (count - 1).toFloat()
    } else {
        bounds.left + ((xValue - range.minX) / (range.maxX - range.minX)).toFloat() * bounds.width
    }
    return x to mapY(point.y, range.minY, range.maxY, bounds)
}

private fun mapY(value: Double, minY: Double, maxY: Double, bounds: ChartBounds): Float {
    val ratio = ((value - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f)
    return bounds.bottom - bounds.height * ratio
}

internal fun messageChartColor(index: Int): Color = listOf(
    Color(0xFF8DF9EA),
    Color(0xFF9FD8FF),
    Color(0xFFFFB6D2),
    Color(0xFFFFD26A),
    Color(0xFFBFA8FF),
    Color(0xFF8DF9C2),
)[index % 6]

internal fun formatChartNumber(value: Double): String = when {
    abs(value) >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    abs(value) >= 1_000 -> "%.1fK".format(value / 1_000.0)
    abs(value) >= 100 -> "%.0f".format(value)
    abs(value) >= 10 -> "%.1f".format(value)
    else -> "%.2f".format(value)
}
