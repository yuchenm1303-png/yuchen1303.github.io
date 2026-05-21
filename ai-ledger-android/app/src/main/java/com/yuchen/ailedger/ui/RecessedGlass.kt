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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RecessedGlass(
    modifier: Modifier = Modifier,
    radius: Float = 18f,
    depth: Float = 0.52f,
    floorAlpha: Float = 0.82f,
    rimAlpha: Float = 0.34f,
    innerShadow: Float = 0.67f,
    bottomDim: Float = 0.23f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier = modifier.clip(shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val d = depth.coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = (0.42f + d * 0.32f) * innerShadow),
                        Color(0xFF071133).copy(alpha = 0.28f + floorAlpha * 0.20f),
                        Color.Black.copy(alpha = (0.20f + d * 0.22f) * innerShadow)
                    )
                ),
                cornerRadius = r,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF98D8FF).copy(alpha = floorAlpha * 0.16f),
                        Color(0xFF183E72).copy(alpha = floorAlpha * 0.13f),
                        Color(0xFF050A22).copy(alpha = bottomDim * 0.55f)
                    )
                ),
                cornerRadius = r,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = rimAlpha * 0.55f),
                        Color.White.copy(alpha = rimAlpha * 0.18f),
                        Color.Transparent,
                        Color.Black.copy(alpha = innerShadow * 0.20f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                topLeft = Offset(0.8.dp.toPx(), 0.8.dp.toPx()),
                size = Size(size.width - 1.6.dp.toPx(), size.height - 1.6.dp.toPx()),
                cornerRadius = r,
                style = Stroke(width = 1.05.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = innerShadow * (0.30f + d * 0.22f)),
                topLeft = Offset(1.2.dp.toPx(), 1.2.dp.toPx()),
                size = Size(size.width - 2.4.dp.toPx(), size.height - 2.4.dp.toPx()),
                cornerRadius = r,
                style = Stroke(width = (1.4f + d * 3.2f).dp.toPx()),
                blendMode = BlendMode.Multiply
            )
        }
        content()
    }
}

@Composable
fun RecessedProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(14.dp)) {
        val p = progress.coerceIn(0f, 1f)
        val r = size.height / 2f
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.34f),
            cornerRadius = CornerRadius(r, r),
            blendMode = BlendMode.Multiply
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            cornerRadius = CornerRadius(r, r),
            blendMode = BlendMode.Screen
        )
        if (p > 0.002f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color(0xFF8DF9EA).copy(alpha = 0.96f),
                        Color(0xFF8ED8FF).copy(alpha = 0.78f)
                    )
                ),
                size = Size(size.width * p, size.height),
                cornerRadius = CornerRadius(r, r),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.26f),
                size = Size(size.width * p, size.height * 0.36f),
                cornerRadius = CornerRadius(r, r),
                blendMode = BlendMode.Screen
            )
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.20f),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 0.6.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
fun SampleRecessedSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueText: String = value.formatSampleSliderValue(),
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val percent = ((clamped - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    RecessedGlass(
        modifier = modifier.fillMaxWidth().height(58.dp),
        radius = 18f,
        depth = 0.52f,
        floorAlpha = 0.82f,
        rimAlpha = 0.34f,
        innerShadow = 0.67f,
        bottomDim = 0.23f
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(0.78f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(valueText, color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(46.dp), maxLines = 1, overflow = TextOverflow.Clip)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                RecessedProgressTrack(percent, Modifier.fillMaxWidth().height(14.dp))
                Slider(
                    value = clamped,
                    onValueChange = onValueChange,
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth().height(24.dp),
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

private fun Float.formatSampleSliderValue(): String = "${((this * 100).roundToInt() / 100f)}"
