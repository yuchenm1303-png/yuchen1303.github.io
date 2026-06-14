package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LaboratoryInsetRadius = 18f
private const val LaboratoryInsetDepth = 0.52f
private const val LaboratoryInsetBackdropAlpha = 0.82f
private const val LaboratoryInsetRimHighlight = 0.34f
private const val LaboratoryInsetInnerShadow = 0.67f
private const val LaboratoryInsetFloorDim = 0.23f
private const val SliderCommitFallbackDelayMs = 1_200L

/**
 * 设置页参数滑块。
 * 材质、层次和进度轨完全沿用玻璃实验室凹槽样本，只调整文字排版与可交互宽度。
 * 纯 Compose/Canvas 实现，不进入任何 OpenGL registry，也不触发 geometry sync。
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
    val acknowledgementTolerance = maxOf(span * 0.0005f, 0.0001f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentExternalValue by rememberUpdatedState(clampedValue)

    var dragValue by remember { mutableFloatStateOf(clampedValue) }
    var dragging by remember { mutableStateOf(false) }
    var pendingCommit by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val reservedWidthPx = with(LocalDensity.current) { 112.dp.toPx() }

    val displayValue = if (dragging || pendingCommit != null) {
        dragValue.coerceIn(start, end)
    } else {
        clampedValue
    }
    val progress = ((displayValue - start) / span).coerceIn(0f, 1f)
    val currentDisplayValue by rememberUpdatedState(displayValue)
    val displayValueText = if (dragging || pendingCommit != null) {
        displayValue.formatInsetSliderValue() + valueText.sliderValueSuffix()
    } else {
        valueText
    }

    // 外部状态只在空闲时接管显示。拖动中以及松手后的提交确认期内，
    // 始终保持本地最终值，避免旧的偏好流回灌造成一帧回跳。
    LaunchedEffect(clampedValue, dragging, pendingCommit, start, end) {
        if (dragging) return@LaunchedEffect
        val pending = pendingCommit
        if (pending == null) {
            dragValue = clampedValue
        } else if (abs(clampedValue - pending) <= acknowledgementTolerance) {
            dragValue = clampedValue
            pendingCommit = null
        }
    }

    // 正常情况下外部状态会很快确认最终值。该兜底仅防止回调被上层拒绝时
    // 本地锁永久悬挂，不参与正常拖动动画。
    LaunchedEffect(pendingCommit) {
        val expected = pendingCommit ?: return@LaunchedEffect
        delay(SliderCommitFallbackDelayMs)
        if (!dragging && pendingCommit == expected) {
            dragValue = currentExternalValue
            pendingCommit = null
        }
    }

    val dragState = rememberDraggableState { delta ->
        val next = (dragValue + delta / trackWidthPx.coerceAtLeast(1f) * span)
            .coerceIn(start, end)
        if (next != dragValue) {
            dragValue = next
            currentOnValueChange(next)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        LaboratoryInsetGlassSlot(
            radius = LaboratoryInsetRadius,
            grooveDepth = LaboratoryInsetDepth,
            floorBackdropAlpha = LaboratoryInsetBackdropAlpha,
            rimHighlightAlpha = LaboratoryInsetRimHighlight,
            innerShadowAlpha = LaboratoryInsetInnerShadow,
            floorDimAlpha = LaboratoryInsetFloorDim,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged {
                    trackWidthPx = (it.width.toFloat() - reservedWidthPx).coerceAtLeast(1f)
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(displayValue, start..end)
                    setProgress { requested ->
                        val resolved = requested.coerceIn(start, end)
                        dragValue = resolved
                        dragging = false
                        pendingCommit = resolved
                        currentOnValueChange(resolved)
                        true
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragValue = currentDisplayValue
                        pendingCommit = null
                        dragging = true
                    },
                    onDragStopped = {
                        val finalValue = dragValue.coerceIn(start, end)
                        pendingCommit = finalValue
                        dragging = false
                        currentOnValueChange(finalValue)
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = leadingMark,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                LaboratoryRecessedProgressTrack(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                )
                Text(
                    text = displayValueText,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
    }
}

/** 与玻璃实验室 LabInsetGlassSlot 完全一致的凹槽材质链。 */
@Composable
private fun LaboratoryInsetGlassSlot(
    radius: Float,
    grooveDepth: Float,
    floorBackdropAlpha: Float,
    rimHighlightAlpha: Float,
    innerShadowAlpha: Float,
    floorDimAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        FrostInfoGlassPanel(
            radius = radius,
            backdropAlpha = floorBackdropAlpha,
            dimAlpha = floorDimAlpha,
            modifier = Modifier.fillMaxSize()
        ) {}
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val inset = (1.5f + grooveDepth * 6f).dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = innerShadowAlpha * 0.45f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = r,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = innerShadowAlpha * 0.42f),
                        Color.Transparent,
                        Color.White.copy(alpha = rimHighlightAlpha * 0.26f)
                    )
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = r,
                style = Stroke(width = (1.2f + grooveDepth * 3f).dp.toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.White.copy(alpha = rimHighlightAlpha * 0.18f),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                cornerRadius = r,
                style = Stroke(width = 0.9.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        content()
    }
}

/** 与玻璃实验室 RecessedProgressTrack 完全一致的进度轨。 */
@Composable
private fun LaboratoryRecessedProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(999.dp))) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.09f),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFBFFAFF).copy(alpha = 0.95f),
                    Color(0xFF8DF9EA).copy(alpha = 0.72f)
                )
            ),
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Screen
        )
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

private fun String.sliderValueSuffix(): String {
    val suffixStart = indexOfFirst { character ->
        !character.isDigit() && character != '.' && character != '-' && character != '+' && !character.isWhitespace()
    }
    return if (suffixStart >= 0) substring(suffixStart) else ""
}
