package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val APPROVED_INSET_RADIUS = 18f
private const val APPROVED_INSET_DEPTH = 0.52f
private const val APPROVED_INSET_BACKDROP_ALPHA = 0.82f
private const val APPROVED_INSET_RIM_ALPHA = 0.34f
private const val APPROVED_INSET_INNER_SHADOW = 0.67f
private const val APPROVED_INSET_FLOOR_DIM = 0.23f

@Composable
fun RecessedGlass(
    modifier: Modifier = Modifier,
    radius: Float = APPROVED_INSET_RADIUS,
    depth: Float = APPROVED_INSET_DEPTH,
    floorAlpha: Float = APPROVED_INSET_BACKDROP_ALPHA,
    rimAlpha: Float = APPROVED_INSET_RIM_ALPHA,
    innerShadow: Float = APPROVED_INSET_INNER_SHADOW,
    bottomDim: Float = APPROVED_INSET_FLOOR_DIM,
    content: @Composable () -> Unit
) {
    ApprovedInsetGlassSlot(
        modifier = modifier,
        radius = radius,
        grooveDepth = depth,
        floorBackdropAlpha = floorAlpha,
        rimHighlightAlpha = rimAlpha,
        innerShadowAlpha = innerShadow,
        floorDimAlpha = bottomDim,
        content = content
    )
}

@Composable
fun ApprovedRecessedSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueText: String = value.formatApprovedSliderValue(),
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val percent = ((clamped - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    RecessedGlass(modifier = modifier.fillMaxWidth().height(58.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.78f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(valueText, color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(46.dp), maxLines = 1, overflow = TextOverflow.Clip)
            Spacer(Modifier.width(8.dp))
            ApprovedInsetGlassSlot(
                modifier = Modifier.weight(1f).height(38.dp),
                radius = APPROVED_INSET_RADIUS,
                grooveDepth = APPROVED_INSET_DEPTH,
                floorBackdropAlpha = APPROVED_INSET_BACKDROP_ALPHA,
                rimHighlightAlpha = APPROVED_INSET_RIM_ALPHA,
                innerShadowAlpha = APPROVED_INSET_INNER_SHADOW,
                floorDimAlpha = APPROVED_INSET_FLOOR_DIM
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    RecessedProgressTrack(percent, Modifier.fillMaxWidth().height(12.dp))
                    Slider(
                        value = clamped,
                        onValueChange = onValueChange,
                        valueRange = range,
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White.copy(alpha = 0.96f),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ApprovedRecessedInput(
    modifier: Modifier = Modifier,
    radius: Float = 28f,
    content: @Composable () -> Unit
) {
    ApprovedInsetGlassSlot(
        modifier = modifier,
        radius = radius,
        grooveDepth = APPROVED_INSET_DEPTH,
        floorBackdropAlpha = APPROVED_INSET_BACKDROP_ALPHA,
        rimHighlightAlpha = APPROVED_INSET_RIM_ALPHA,
        innerShadowAlpha = APPROVED_INSET_INNER_SHADOW,
        floorDimAlpha = APPROVED_INSET_FLOOR_DIM,
        content = content
    )
}

@Composable
fun SampleRecessedSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueText: String = value.formatApprovedSliderValue(),
    onValueChange: (Float) -> Unit
) {
    ApprovedRecessedSlider(title, subtitle, value, range, modifier, valueText, onValueChange)
}

@Composable
fun SampleRecessedInputSlot(
    modifier: Modifier = Modifier,
    radius: Float = 28f,
    content: @Composable () -> Unit
) {
    ApprovedRecessedInput(modifier = modifier, radius = radius, content = content)
}

@Composable
fun RecessedProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(14.dp)) {
        val p = progress.coerceIn(0f, 1f)
        val r = size.height / 2f
        drawRoundRect(color = Color.Black.copy(alpha = 0.34f), cornerRadius = CornerRadius(r, r), blendMode = BlendMode.Multiply)
        drawRoundRect(color = Color.White.copy(alpha = 0.18f), cornerRadius = CornerRadius(r, r), blendMode = BlendMode.Screen)
        if (p > 0.002f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.78f), Color(0xFF8DF9EA).copy(alpha = 0.96f), Color(0xFF8ED8FF).copy(alpha = 0.78f))),
                size = Size(size.width * p, size.height),
                cornerRadius = CornerRadius(r, r),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(color = Color.White.copy(alpha = 0.26f), size = Size(size.width * p, size.height * 0.36f), cornerRadius = CornerRadius(r, r), blendMode = BlendMode.Screen)
        }
        drawRoundRect(color = Color.White.copy(alpha = 0.20f), cornerRadius = CornerRadius(r, r), style = Stroke(width = 0.6.dp.toPx()), blendMode = BlendMode.Screen)
    }
}

@Composable
fun ApprovedInsetGlassSlot(
    modifier: Modifier = Modifier,
    radius: Float,
    grooveDepth: Float,
    floorBackdropAlpha: Float,
    rimHighlightAlpha: Float,
    innerShadowAlpha: Float,
    floorDimAlpha: Float,
    content: @Composable () -> Unit
) {
    val outerCoordinates = remember { GlassCoordinateSource() }
    val floorCoordinates = remember { GlassCoordinateSource() }
    val depth = grooveDepth.coerceIn(0f, 1f)
    val floorInset = 1.35f
    val floorRadius = (radius - 1.2f).coerceAtLeast(5f)
    Box(modifier = modifier.onGloballyPositioned { outerCoordinates.coordinates = it }.clip(RoundedCornerShape(radius.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val shadow = (0.30f + depth * 0.70f) * innerShadowAlpha
            drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = shadow * 0.72f), Color(0xFF070C29).copy(alpha = 0.28f + depth * 0.12f), Color.Black.copy(alpha = shadow * 0.18f))), cornerRadius = corner, blendMode = BlendMode.Multiply)
        }
        Box(modifier = Modifier.fillMaxSize().padding(floorInset.dp).onGloballyPositioned { floorCoordinates.coordinates = it }.clip(RoundedCornerShape(floorRadius.dp))) {
            ApprovedBackdropCrop(floorCoordinates, floorBackdropAlpha.coerceIn(0f, 1f), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (floorDimAlpha + depth * 0.06f).coerceIn(0f, 0.75f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = innerShadowAlpha * (0.12f + depth * 0.14f)), Color.Transparent, Color.White.copy(alpha = rimHighlightAlpha * 0.035f)))))
            content()
        }
        ApprovedDynamicInsetRimHighlight(outerCoordinates, radius, rimHighlightAlpha * (0.42f + depth * 0.20f), 1.20f, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val floorInsetPx = floorInset.dp.toPx()
            val floorCorner = CornerRadius(floorRadius.dp.toPx(), floorRadius.dp.toPx())
            val floorSize = Size(size.width - floorInsetPx * 2f, size.height - floorInsetPx * 2f)
            val floorTopLeft = Offset(floorInsetPx, floorInsetPx)
            val shadowWidth = (1.2f + depth * 3.8f).dp.toPx()
            drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = innerShadowAlpha * (0.58f + depth * 0.36f)), Color.Black.copy(alpha = innerShadowAlpha * (0.16f + depth * 0.16f)), Color.Transparent)), topLeft = floorTopLeft, size = floorSize, cornerRadius = floorCorner, style = Stroke(width = shadowWidth), blendMode = BlendMode.Multiply)
            drawRoundRect(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = rimHighlightAlpha * 0.28f), Color.White.copy(alpha = rimHighlightAlpha * 0.08f), Color.Transparent, Color.Black.copy(alpha = innerShadowAlpha * 0.14f)), start = Offset.Zero, end = Offset(size.width, size.height)), topLeft = Offset(0.65.dp.toPx(), 0.65.dp.toPx()), size = Size(size.width - 1.3.dp.toPx(), size.height - 1.3.dp.toPx()), cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx()), style = Stroke(width = 0.72.dp.toPx()), blendMode = BlendMode.Screen)
        }
    }
}

@Composable
private fun ApprovedDynamicInsetRimHighlight(coordinateSource: GlassCoordinateSource, radius: Float, alpha: Float, strokeDp: Float, modifier: Modifier = Modifier) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val image = cachedBackdrop?.image ?: return@Canvas
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
        val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
        val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
        val strokePx = strokeDp.dp.toPx()
        val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawImage(image = image, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)), alpha = alpha.coerceIn(0f, 1f), blendMode = BlendMode.Screen)
            drawRoundRect(color = Color.White, topLeft = Offset(strokePx * 0.50f, strokePx * 0.50f), size = Size(size.width - strokePx, size.height - strokePx), cornerRadius = corner, style = Stroke(width = strokePx), blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    }
}

@Composable
private fun ApprovedBackdropCrop(coordinateSource: GlassCoordinateSource, backdropAlpha: Float, modifier: Modifier = Modifier) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val backdrop = cachedBackdrop
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        if (backdrop != null) {
            val srcX = (sampleOffset.x * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
            val srcY = (sampleOffset.y * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
            val srcW = (size.width * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
            val srcH = (size.height * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)
            drawImage(image = backdrop.image, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)), alpha = backdropAlpha.coerceIn(0f, 1f), blendMode = BlendMode.SrcOver)
        } else {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78))))
        }
    }
}

private fun Float.formatApprovedSliderValue(): String = "${((this * 100).roundToInt() / 100f)}"
