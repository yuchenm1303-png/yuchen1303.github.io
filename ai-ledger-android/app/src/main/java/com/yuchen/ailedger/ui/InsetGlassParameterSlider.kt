package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 设置页统一凹槽玻璃参数滑块。
 * 纯 Compose/Canvas 实现，完全隔离于 OpenGL registry 与 geometry sync。
 */
@Composable
internal fun InsetGlassParameterSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String = value.formatInsetSliderValue(),
    leadingMark: String = "▸"
) {
    val start = minOf(valueRange.start, valueRange.endInclusive)
    val end = maxOf(valueRange.start, valueRange.endInclusive)
    val span = (end - start).coerceAtLeast(0.000001f)
    val clampedValue = value.coerceIn(start, end)
    val progress = ((clampedValue - start) / span).coerceIn(0f, 1f)
    val currentValue by rememberUpdatedState(clampedValue)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val reservedWidthPx = with(LocalDensity.current) { 126.dp.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val dragState = rememberDraggableState { delta ->
        val next = currentValue + delta / trackWidthPx.coerceAtLeast(1f) * span
        currentOnValueChange(next.coerceIn(start, end))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.43f),
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .onSizeChanged {
                    trackWidthPx = (it.width.toFloat() - reservedWidthPx).coerceAtLeast(1f)
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, start..end)
                    setProgress { requested ->
                        currentOnValueChange(requested.coerceIn(start, end))
                        true
                    }
                }
                .draggable(state = dragState, orientation = Orientation.Horizontal)
                .clip(RoundedCornerShape(23.dp)),
            contentAlignment = Alignment.Center
        ) {
            FrostInfoGlassPanel(
                radius = 23f,
                backdropAlpha = 0.82f,
                frostAlpha = 0.026f,
                dimAlpha = 0.18f,
                modifier = Modifier.matchParentSize()
            ) {}

            Canvas(Modifier.matchParentSize()) {
                val radius = size.height / 2f
                val inset = 2.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.14f),
                            Color(0xFF18254C).copy(alpha = 0.10f)
                        )
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                    blendMode = BlendMode.Multiply
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.34f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(radius - inset, radius - inset),
                    style = Stroke(1.8.dp.toPx()),
                    blendMode = BlendMode.Multiply
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent,
                            Color(0xFF9AEDE6).copy(alpha = 0.075f)
                        )
                    ),
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(radius - 1.dp.toPx(), radius - 1.dp.toPx()),
                    style = Stroke(0.9.dp.toPx()),
                    blendMode = BlendMode.Screen
                )
            }

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = leadingMark,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    val radius = size.height / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.055f),
                                Color.Black.copy(alpha = 0.16f)
                            )
                        ),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.075f),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(0.8.dp.toPx())
                    )
                    if (progress > 0.0001f) {
                        val activeWidth = maxOf(size.height, size.width * progress).coerceAtMost(size.width)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFC8FBFF).copy(alpha = 0.96f),
                                    Color(0xFFA7ECE8).copy(alpha = 0.92f),
                                    Color(0xFF83D7D4).copy(alpha = 0.90f)
                                )
                            ),
                            size = Size(activeWidth, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                            blendMode = BlendMode.Screen
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.30f), Color.Transparent)
                            ),
                            size = Size(activeWidth, size.height * 0.48f),
                            cornerRadius = CornerRadius(radius, radius),
                            blendMode = BlendMode.Screen
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Canvas(Modifier.size(1.dp, 22.dp)) {
                    drawRect(Color.White.copy(alpha = 0.10f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = valueText,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(54.dp)
                )
            }
        }
    }
}

private fun Float.formatInsetSliderValue(): String {
    val rounded = (this * 100f).roundToInt() / 100f
    return if (rounded == rounded.roundToInt().toFloat()) {
        rounded.roundToInt().toString()
    } else {
        rounded.toString()
    }
}
