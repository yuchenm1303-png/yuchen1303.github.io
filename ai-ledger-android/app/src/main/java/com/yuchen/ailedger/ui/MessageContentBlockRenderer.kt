package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.ActionGroupContentBlock
import com.yuchen.ailedger.model.CalloutContentBlock
import com.yuchen.ailedger.model.ChartContentBlock
import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.ImageGalleryContentBlock
import com.yuchen.ailedger.model.KeyValueContentBlock
import com.yuchen.ailedger.model.MessageActionType
import com.yuchen.ailedger.model.MessageCalloutTone
import com.yuchen.ailedger.model.MessageChartPoint
import com.yuchen.ailedger.model.MessageChartSeries
import com.yuchen.ailedger.model.MessageChartType
import com.yuchen.ailedger.model.MessageContentBlock
import com.yuchen.ailedger.model.MessageImageItem
import com.yuchen.ailedger.model.RichTextContentBlock
import com.yuchen.ailedger.model.TableContentBlock
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MESSAGE_IMAGE_MAX_BYTES = 12 * 1024 * 1024
private val messageImageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder().build()
}

@Composable
fun MessageContentBlockList(
    blocks: List<MessageContentBlock>,
    onOpenUrl: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is RichTextContentBlock -> RichTextBlockView(block)
                is CodeContentBlock -> CodeBlockView(block)
                is TableContentBlock -> TableBlockView(block)
                is ChartContentBlock -> ChartBlockView(block)
                is ImageContentBlock -> MessageImageBlockView(block.image)
                is ImageGalleryContentBlock -> ImageGalleryBlockView(block)
                is KeyValueContentBlock -> KeyValueBlockView(block)
                is CalloutContentBlock -> CalloutBlockView(block)
                is ActionGroupContentBlock -> ActionGroupBlockView(block, onOpenUrl)
            }
        }
    }
}

@Composable
private fun RichTextBlockView(block: RichTextContentBlock) {
    MessageBlockSurface {
        OptimizedRichMessageContent(
            text = block.text,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CodeBlockView(block: CodeContentBlock) {
    val clipboard = LocalClipboardManager.current
    val lines = remember(block.code) { block.code.lines() }
    val expandable = lines.size > 42 || block.code.length > 2200
    var expanded by remember(block.id) { mutableStateOf(false) }
    val visibleCode = remember(block.code, expanded) {
        if (expandable && !expanded) lines.take(42).joinToString("\n") else block.code
    }

    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.fileName ?: block.language?.uppercase() ?: "代码",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    block.caption?.takeIf(String::isNotBlank)?.let { caption ->
                        Text(
                            text = caption,
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MessageBlockActionPill("复制") {
                    clipboard.setText(AnnotatedString(block.code))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.20f))
                    .horizontalScroll(rememberScrollState())
                    .padding(11.dp),
            ) {
                Text(
                    text = visibleCode,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (expandable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MessageBlockActionPill(if (expanded) "收起代码" else "展开全部") {
                        expanded = !expanded
                    }
                }
            }
        }
    }
}

@Composable
private fun TableBlockView(block: TableContentBlock) {
    val columnCount = block.columns.size.coerceAtLeast(block.rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount <= 0) return
    val columns = List(columnCount) { index -> block.columns.getOrNull(index).orEmpty() }
    val horizontalState = rememberScrollState()

    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalState),
            ) {
                if (columns.any(String::isNotBlank)) {
                    TableRowView(columns, header = true)
                }
                block.rows.forEachIndexed { index, row ->
                    TableRowView(
                        cells = List(columnCount) { cellIndex -> row.getOrNull(cellIndex).orEmpty() },
                        header = false,
                        alternate = index % 2 == 1,
                    )
                }
            }
            block.footnote?.takeIf(String::isNotBlank)?.let { footnote ->
                Text(
                    text = footnote,
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TableRowView(
    cells: List<String>,
    header: Boolean,
    alternate: Boolean = false,
) {
    Row(
        modifier = Modifier.background(
            Color.White.copy(alpha = when {
                header -> 0.10f
                alternate -> 0.045f
                else -> 0.015f
            }),
        ),
        verticalAlignment = Alignment.Top,
    ) {
        cells.forEach { cell ->
            Text(
                text = cell,
                color = Color.White.copy(alpha = if (header) 0.88f else 0.72f),
                fontSize = if (header) 10.sp else 9.sp,
                lineHeight = 14.sp,
                fontWeight = if (header) FontWeight.Black else FontWeight.Medium,
                modifier = Modifier
                    .width(126.dp)
                    .padding(horizontal = 9.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChartBlockView(block: ChartContentBlock) {
    val validSeries = remember(block.series) {
        block.series.filter { series -> series.points.any { it.y.isFinite() } }
    }
    if (validSeries.isEmpty()) return

    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            block.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.13f)),
            ) {
                when (block.type) {
                    MessageChartType.Line -> drawLineChart(validSeries, block)
                    MessageChartType.Bar -> drawBarChart(validSeries, block)
                    MessageChartType.Pie -> drawPieChart(validSeries.first())
                    MessageChartType.Scatter -> drawScatterChart(validSeries, block)
                }
            }
            if (validSeries.size > 1) ChartLegend(validSeries)
        }
    }
}

@Composable
private fun ChartLegend(series: List<MessageChartSeries>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        series.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(chartColor(index)),
                )
                Text(
                    text = item.name.ifBlank { "系列 ${index + 1}" },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 9.sp,
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
        drawPath(path, color = chartColor(seriesIndex), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
        points.forEachIndexed { pointIndex, point ->
            val position = mapChartPoint(point, pointIndex, points.size, range, bounds)
            drawCircle(chartColor(seriesIndex), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(position.first, position.second))
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
                color = chartColor(seriesIndex).copy(alpha = 0.90f),
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(position.first, position.second),
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
        item.points.forEachIndexed { pointIndex, point ->
            if (!point.y.isFinite()) return@forEachIndexed
            val centerX = bounds.left + groupWidth * (pointIndex + 0.5f)
            val left = centerX - groupWidth * 0.36f + barWidth * seriesIndex
            val valueY = mapY(point.y, range.minY, range.maxY, bounds)
            drawRect(
                color = chartColor(seriesIndex).copy(alpha = 0.88f),
                topLeft = androidx.compose.ui.geometry.Offset(left, min(valueY, zeroY)),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.88f, abs(zeroY - valueY).coerceAtLeast(1f)),
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
            color = chartColor(index),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(diameter, diameter),
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
    if (minX == maxX) { minX -= 0.5; maxX += 0.5 }
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
            start = androidx.compose.ui.geometry.Offset(bounds.left, y),
            end = androidx.compose.ui.geometry.Offset(bounds.right, y),
            strokeWidth = 1.dp.toPx(),
        )
        val value = range.minY + (range.maxY - range.minY) * ratio
        drawChartLabel(formatChartNumber(value), 4.dp.toPx(), y + 3.dp.toPx(), align = Paint.Align.LEFT)
    }
    drawLine(
        color = Color.White.copy(alpha = 0.22f),
        start = androidx.compose.ui.geometry.Offset(bounds.left, bounds.top),
        end = androidx.compose.ui.geometry.Offset(bounds.left, bounds.bottom),
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
    block.yAxisLabel?.takeIf(String::isNotBlank)?.let { drawChartLabel(it.take(12), 4.dp.toPx(), 10.dp.toPx(), Paint.Align.LEFT) }
    block.xAxisLabel?.takeIf(String::isNotBlank)?.let { drawChartLabel(it.take(12), bounds.right, size.height - 8.dp.toPx(), Paint.Align.RIGHT) }
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

private fun chartColor(index: Int): Color = listOf(
    Color(0xFF8DF9EA),
    Color(0xFF9FD8FF),
    Color(0xFFFFB6D2),
    Color(0xFFFFD26A),
    Color(0xFFBFA8FF),
    Color(0xFF8DF9C2),
)[index % 6]

private fun formatChartNumber(value: Double): String = when {
    abs(value) >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    abs(value) >= 1_000 -> "%.1fK".format(value / 1_000.0)
    abs(value) >= 100 -> "%.0f".format(value)
    abs(value) >= 10 -> "%.1f".format(value)
    else -> "%.2f".format(value)
}

@Composable
private fun MessageImageBlockView(image: MessageImageItem) {
    MessageBlockSurface {
        MessageImageView(
            image = image,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        )
    }
}

@Composable
private fun ImageGalleryBlockView(block: ImageGalleryContentBlock) {
    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.images.forEach { image ->
                    MessageImageView(image = image, modifier = Modifier.width(220.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageImageView(image: MessageImageItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by produceState<MessageImageLoadState>(
        initialValue = MessageImageLoadState.Loading,
        key1 = image.source,
        key2 = context,
    ) {
        value = withContext(Dispatchers.IO) { loadMessageImage(context, image.source) }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(resolveImageAspectRatio(image))
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White.copy(alpha = 0.055f)),
            contentAlignment = Alignment.Center,
        ) {
            when (val loaded = state) {
                MessageImageLoadState.Loading -> Text(
                    text = "正在加载图片…",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.sp,
                )
                MessageImageLoadState.Failed -> Text(
                    text = "图片加载失败",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.sp,
                )
                is MessageImageLoadState.Ready -> Image(
                    bitmap = loaded.bitmap,
                    contentDescription = image.alt,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        image.caption?.takeIf(String::isNotBlank)?.let { caption ->
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 9.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private sealed interface MessageImageLoadState {
    data object Loading : MessageImageLoadState
    data object Failed : MessageImageLoadState
    data class Ready(val bitmap: ImageBitmap) : MessageImageLoadState
}

private fun loadMessageImage(context: Context, source: String): MessageImageLoadState {
    val bytes = runCatching {
        when {
            source.startsWith("data:image/", ignoreCase = true) -> {
                val encoded = source.substringAfter(',', "")
                if (encoded.length > MESSAGE_IMAGE_MAX_BYTES * 2) return MessageImageLoadState.Failed
                Base64.decode(encoded, Base64.DEFAULT)
            }
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
                val request = Request.Builder().url(source).get().build()
                messageImageHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return MessageImageLoadState.Failed
                    val body = response.body ?: return MessageImageLoadState.Failed
                    if (body.contentLength() > MESSAGE_IMAGE_MAX_BYTES) return MessageImageLoadState.Failed
                    body.bytes().takeIf { it.size <= MESSAGE_IMAGE_MAX_BYTES }
                        ?: return MessageImageLoadState.Failed
                }
            }
            source.startsWith("content://", ignoreCase = true) || source.startsWith("file://", ignoreCase = true) -> {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                    input.readBytes(MESSAGE_IMAGE_MAX_BYTES + 1).takeIf { it.size <= MESSAGE_IMAGE_MAX_BYTES }
                } ?: return MessageImageLoadState.Failed
            }
            else -> {
                if (source.length > MESSAGE_IMAGE_MAX_BYTES * 2) return MessageImageLoadState.Failed
                Base64.decode(source, Base64.DEFAULT)
            }
        }
    }.getOrNull() ?: return MessageImageLoadState.Failed
    if (bytes.isEmpty() || bytes.size > MESSAGE_IMAGE_MAX_BYTES) return MessageImageLoadState.Failed
    val bitmap = ByteArrayInputStream(bytes).use(BitmapFactory::decodeStream) ?: return MessageImageLoadState.Failed
    return MessageImageLoadState.Ready(bitmap.asImageBitmap())
}

private fun resolveImageAspectRatio(image: MessageImageItem): Float {
    val width = image.width ?: return 16f / 10f
    val height = image.height ?: return 16f / 10f
    if (width <= 0 || height <= 0) return 16f / 10f
    return (width.toFloat() / height.toFloat()).coerceIn(0.62f, 2.2f)
}

@Composable
private fun KeyValueBlockView(block: KeyValueContentBlock) {
    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(title, color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            block.items.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.065f))
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(item.label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.value, color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.detail?.takeIf(String::isNotBlank)?.let { detail ->
                                Text(detail, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalloutBlockView(block: CalloutContentBlock) {
    val accent = when (block.tone) {
        MessageCalloutTone.Info -> Color(0xFF9FD8FF)
        MessageCalloutTone.Success -> Color(0xFF8DF9C2)
        MessageCalloutTone.Warning -> Color(0xFFFFD26A)
        MessageCalloutTone.Error -> Color(0xFFFFA2A2)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(accent).padding(0.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.title?.takeIf(String::isNotBlank)?.let { title ->
                    Text(title, color = accent.copy(alpha = 0.94f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                OptimizedRichMessageContent(
                    text = block.text,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ActionGroupBlockView(
    block: ActionGroupContentBlock,
    onOpenUrl: (title: String, url: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    MessageBlockSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                block.actions.forEach { action ->
                    val enabled = when (action.type) {
                        MessageActionType.OpenUrl -> action.value.startsWith("https://") || action.value.startsWith("http://")
                        MessageActionType.CopyText -> action.value.isNotBlank()
                    }
                    Text(
                        text = action.label,
                        color = Color.White.copy(alpha = if (enabled) 0.90f else 0.35f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = if (action.emphasis) 0.14f else 0.075f))
                            .clickable(enabled = enabled) {
                                when (action.type) {
                                    MessageActionType.OpenUrl -> onOpenUrl(action.label, action.value)
                                    MessageActionType.CopyText -> clipboard.setText(AnnotatedString(action.value))
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBlockSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.085f)),
    ) {
        content()
    }
}

@Composable
private fun MessageBlockActionPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.68f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
