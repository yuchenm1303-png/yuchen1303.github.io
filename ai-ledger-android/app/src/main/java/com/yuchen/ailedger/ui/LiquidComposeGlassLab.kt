package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.max
import kotlin.math.roundToInt

private data class HazeEnv(
    val backdropMix: Float,
    val softness: Float,
    val frost: Float,
    val tint: Float,
    val edge: Float,
    val depth: Float,
    val readability: Float,
    val slotDepth: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var backdropMix by rememberSaveable { mutableStateOf(0.96f) }
    var softness by rememberSaveable { mutableStateOf(0.86f) }
    var frost by rememberSaveable { mutableStateOf(0.74f) }
    var tint by rememberSaveable { mutableStateOf(0.34f) }
    var edge by rememberSaveable { mutableStateOf(0.62f) }
    var depth by rememberSaveable { mutableStateOf(0.58f) }
    var readability by rememberSaveable { mutableStateOf(0.72f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.42f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        backdropMix = 0.96f
        softness = 0.86f
        frost = 0.74f
        tint = 0.34f
        edge = 0.62f
        depth = 0.58f
        readability = 0.72f
        slotDepth = 0.42f
        radiusScale = 1.10f
    }

    val env = HazeEnv(backdropMix, softness, frost, tint, edge, depth, readability, slotDepth)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("高级 Haze 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("稳定背景采样、雾面染色和薄边层次，不接 OpenGL", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Compose", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        HazeGlassSurface(state, Modifier.fillMaxWidth().height(224.dp), env, radiusScale) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("advanced haze glass material", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                HazeSegmentedPill(listOf("Backdrop", "Haze", "Stable"), env, Modifier.fillMaxWidth().height(42.dp))
                Text("目标：小卡片和滚动按钮优先稳定，高级雾面、背景融合和轻量边缘层次。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("背景融合", "模糊背景进入玻璃的强度", backdropMix, 0f..1.35f) { backdropMix = it }
        LiquidComposeSlider("背景柔化", "额外雾化和柔和观感", softness, 0f..1.6f) { softness = it }
        LiquidComposeSlider("玻璃雾度", "磨砂玻璃本体的雾面厚度", frost, 0f..1.6f) { frost = it }
        LiquidComposeSlider("色场补偿", "给偏暗背景补淡青紫色场", tint, 0f..1.6f) { tint = it }
        LiquidComposeSlider("边缘薄线", "极细轮廓、顶部高光与伪折边", edge, 0f..1.6f) { edge = it }
        LiquidComposeSlider("内侧暗边", "内凹压边与底部深度", depth, 0f..1.6f) { depth = it }
        LiquidComposeSlider("可读暗场", "保护文字区域，不让背景抢内容", readability, 0f..1.6f) { readability = it }
        LiquidComposeSlider("槽体压入", "分段槽体从雾面玻璃里压入", slotDepth, 0f..1.6f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制高级 Haze 外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置 Haze", "恢复高级雾面值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("稳定小卡", "滚动组件方向", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun HazeGlassSurface(
    state: AssistantUiState,
    modifier: Modifier,
    env: HazeEnv,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    val coordinateSource = remember { GlassCoordinateSource() }
    val radiusDp = (34f * radiusScale.coerceIn(0.65f, 1.55f)).roundToInt().coerceAtLeast(18)
    val shape = RoundedCornerShape(radiusDp.dp)
    val border = remember(env) {
        GlassBorderStyle(
            outerStrokeAlpha = 0.06f + env.edge.coerceIn(0f, 1.6f) * 0.10f,
            innerStrokeAlpha = 0.02f + env.depth.coerceIn(0f, 1.6f) * 0.06f,
            topHighlightAlpha = 0.18f + env.edge.coerceIn(0f, 1.6f) * 0.34f,
            bottomShadowAlpha = 0.10f + env.depth.coerceIn(0f, 1.6f) * 0.22f,
            ringWidthDp = 5f + env.edge.coerceIn(0f, 1.6f) * 7f,
            edgeBrightness = 1.0f + env.tint.coerceIn(0f, 1.6f) * 0.05f,
            bodyAlpha = 0f
        )
    }
    val spec = remember(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border) {
        GlassBackdropSpec(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border)
    }

    Box(
        modifier = modifier.onPlaced { coordinateSource.coordinates = it }.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides spec) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radiusDp,
                coordinateSource = coordinateSource,
                quality = state.quality,
                motionIntensity = state.motionIntensity,
                theme = state.backgroundTheme,
                blurRadiusDp = (96f + env.softness.coerceIn(0f, 1.6f) * 48f).roundToInt(),
                liftAlpha = env.backdropMix.coerceIn(0.34f, 1.0f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radiusDp,
                coordinateSource = coordinateSource,
                quality = state.quality,
                motionIntensity = state.motionIntensity,
                theme = state.backgroundTheme,
                strength = (0.16f + env.edge.coerceIn(0f, 1.6f) * 0.10f).coerceIn(0f, 0.32f)
            )
        }
        Box(Modifier.matchParentSize().hazeSkin(env, radiusDp), contentAlignment = Alignment.Center) { content() }
    }
}

private fun Modifier.hazeSkin(env: HazeEnv, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val frost = env.frost.coerceIn(0f, 1.6f)
    val softness = env.softness.coerceIn(0f, 1.6f)
    val tint = env.tint.coerceIn(0f, 1.6f)
    val edge = env.edge.coerceIn(0f, 1.6f)
    val depth = env.depth.coerceIn(0f, 1.6f)
    val readability = env.readability.coerceIn(0f, 1.6f)
    val rimWidth = max(1f, density * (0.58f + edge * 0.38f))
    val innerInset = rimWidth * (1.80f + depth * 0.36f)
    val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
    val innerCorner = CornerRadius(max(1f, cornerRadius - innerInset), max(1f, cornerRadius - innerInset))
    val tintField = Brush.linearGradient(listOf(Color(0xFF8BF8FF).copy(alpha = 0.018f * tint), Color(0xFF798DFF).copy(alpha = 0.014f * tint), Color(0xFFFF7AD9).copy(alpha = 0.020f * tint)), Offset(-w * 0.15f, h * 0.12f), Offset(w * 1.10f, h * 0.88f))
    val frostVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.040f * frost + 0.010f * softness), Color(0xFFE5ECFF).copy(alpha = 0.020f * frost), Color(0xFF10203C).copy(alpha = 0.020f * depth)))
    val readabilityField = Brush.radialGradient(listOf(Color(0xFF020820).copy(alpha = 0.090f * readability), Color(0xFF020820).copy(alpha = 0.040f * readability), Color.Transparent), center = Offset(w * 0.40f, h * 0.52f), radius = max(w, h) * 0.82f)
    val topGlance = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.050f * edge), Color.White.copy(alpha = 0.020f * edge), Color.Transparent), Offset(-w * 0.04f, -h * 0.05f), Offset(w * 0.82f, h * 0.20f))
    val outerLine = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.060f * edge), Color(0xFFC7F3FF).copy(alpha = 0.022f * edge), Color.White.copy(alpha = 0.028f * edge)), Offset(-w * 0.05f, h * 0.03f), Offset(w * 1.04f, h * 0.95f))
    onDrawWithContent {
        drawRoundRect(brush = tintField, size = size, cornerRadius = corner)
        drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = readabilityField, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
        drawRoundRect(color = Color(0xFF02071D).copy(alpha = 0.026f * depth), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.38f)))
        drawRoundRect(color = Color.White.copy(alpha = 0.016f * edge), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.22f)))
    }
}

@Composable
private fun HazeSegmentedPill(labels: List<String>, env: HazeEnv, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier.clip(shape).drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val corner = CornerRadius(h / 2f, h / 2f)
            val edge = env.edge.coerceIn(0f, 1.6f)
            val slot = env.slotDepth.coerceIn(0f, 1.6f)
            val depth = env.depth.coerceIn(0f, 1.6f)
            val frost = env.frost.coerceIn(0f, 1.6f)
            val material = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.032f + 0.012f * frost), Color(0xFF061032).copy(alpha = 0.040f + 0.032f * slot + 0.012f * depth), Color.White.copy(alpha = 0.012f * edge)))
            onDrawWithContent {
                drawRoundRect(brush = material, size = size, cornerRadius = corner)
                drawContent()
                drawRoundRect(color = Color.White.copy(alpha = 0.034f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.68f)))
                drawLine(color = Color.White.copy(alpha = 0.014f * slot), start = Offset(w / 3f, h * 0.28f), end = Offset(w / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
                drawLine(color = Color.White.copy(alpha = 0.014f * slot), start = Offset(w * 2f / 3f, h * 0.28f), end = Offset(w * 2f / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
            }
        }.padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LiquidComposeSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatLiquidLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = clamped, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LiquidComposeActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(quality = state.quality, glassIntensity = state.glassIntensity * 0.64f, motionIntensity = state.motionIntensity, radius = 22, modifier = modifier.height(54.dp), role = GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLiquidLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
