package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.roundToInt

@Composable
fun FrostInfoGlassLab(state: AssistantUiState) {
    var radius by rememberSaveable { mutableStateOf(24f) }
    var backdropAlpha by rememberSaveable { mutableStateOf(0.92f) }
    var frostAlpha by rememberSaveable { mutableStateOf(0.18f) }
    var dimAlpha by rememberSaveable { mutableStateOf(0.06f) }
    var contentAlpha by rememberSaveable { mutableStateOf(0.88f) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        FrostInfoGlassPanel(
            radius = radius,
            backdropAlpha = backdropAlpha,
            frostAlpha = frostAlpha,
            dimAlpha = dimAlpha,
            modifier = Modifier.fillMaxWidth().height(132.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("雾面信息玻璃", color = Color.White.copy(alpha = contentAlpha), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("只裁剪背景模糊层，不叠边框、高光和折射。", color = Color.White.copy(alpha = contentAlpha * 0.58f), fontSize = 11.sp, lineHeight = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FrostMetric("记忆容量", "68%", contentAlpha, Modifier.weight(1f))
                    FrostMetric("今日对话", "24 次", contentAlpha, Modifier.weight(1f))
                    FrostMetric("模型状态", "在线", contentAlpha, Modifier.weight(1f))
                }
            }
        }

        FrostPanelSlider("圆角", "面板圆角半径", radius, 8f..42f) { radius = it }
        FrostPanelSlider("背景模糊层", "裁剪后的模糊背景透明度", backdropAlpha, 0f..1f) { backdropAlpha = it }
        FrostPanelSlider("雾面白罩", "越高越像磨砂信息面板", frostAlpha, 0f..0.65f) { frostAlpha = it }
        FrostPanelSlider("暗化遮罩", "压住背景噪声，增强文字可读性", dimAlpha, 0f..0.40f) { dimAlpha = it }
        FrostPanelSlider("文字透明度", "只影响预览内容，不影响材质", contentAlpha, 0.35f..1f) { contentAlpha = it }
    }
}

@Composable
fun FrostInfoGlassPanel(
    modifier: Modifier = Modifier,
    radius: Float = 24f,
    backdropAlpha: Float = 0.92f,
    frostAlpha: Float = 0.18f,
    dimAlpha: Float = 0.06f,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val shape = RoundedCornerShape(radius.dp)

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates.coordinates = it }
            .clip(shape)
    ) {
        FrostBackdropCrop(
            coordinateSource = coordinates,
            backdropAlpha = backdropAlpha.coerceIn(0f, 1f),
            modifier = Modifier.matchParentSize()
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = frostAlpha.coerceIn(0f, 0.85f)))
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.65f)))
        )
        content()
    }
}

@Composable
private fun FrostBackdropCrop(
    coordinateSource: GlassCoordinateSource,
    backdropAlpha: Float,
    modifier: Modifier = Modifier
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current

    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        if (cachedBackdrop != null) {
            val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
            val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
            val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.width - srcX)
            val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.height - srcY)
            drawImage(
                image = cachedBackdrop.image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = backdropAlpha,
                blendMode = BlendMode.SrcOver
            )
        } else {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A2B58).copy(alpha = backdropAlpha),
                        Color(0xFF5B4A8E).copy(alpha = backdropAlpha),
                        Color(0xFFB85D78).copy(alpha = backdropAlpha)
                    )
                )
            )
        }
    }
}

@Composable
private fun FrostMetric(label: String, value: String, alpha: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = alpha * 0.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White.copy(alpha = alpha), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FrostPanelSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(clamped.formatFrostValue(), color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = clamped,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White.copy(alpha = 0.95f),
                activeTrackColor = Color(0xFF8DF9EA).copy(alpha = 0.54f),
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

private fun Float.formatFrostValue(): String = "${((this * 100).roundToInt() / 100f)}"
