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
    val backdropAlpha: Float,
    val surfaceMist: Float,
    val frost: Float,
    val edge: Float,
    val readability: Float,
    val slotDepth: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var backdropAlpha by rememberSaveable { mutableStateOf(0.96f) }
    var surfaceMist by rememberSaveable { mutableStateOf(0.62f) }
    var frost by rememberSaveable { mutableStateOf(0.54f) }
    var edge by rememberSaveable { mutableStateOf(0.54f) }
    var readability by rememberSaveable { mutableStateOf(0.58f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.34f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        backdropAlpha = 0.96f
        surfaceMist = 0.62f
        frost = 0.54f
        edge = 0.54f
        readability = 0.58f
        slotDepth = 0.34f
        radiusScale = 1.10f
    }

    val env = HazeEnv(backdropAlpha, surfaceMist, frost, edge, readability, slotDepth)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("高级 Haze 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("稳定背景采样、轻雾面和无内框薄边，不接 OpenGL", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("目标：小卡片和滚动按钮优先稳定，背景自然透出，边缘干净且不套内黑框。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("背景透出", "直接控制模糊背景图的可见度", backdropAlpha, 0.34f..1.0f) { backdropAlpha = it }
        LiquidComposeSlider("表面柔雾", "只控制玻璃表面雾气，不假装是背景再模糊", surfaceMist, 0f..1.6f) { surfaceMist = it }
        LiquidComposeSlider("玻璃雾度", "磨砂玻璃本体的白雾厚度", frost, 0f..1.6f) { frost = it }
        LiquidComposeSlider("边缘薄线", "极细轮廓和顶部高光，不生成厚边", edge, 0f..1.6f) { edge = it }
        LiquidComposeSlider("可读暗场", "保护文字区域，不画内部黑框", readability, 0f..1.6f) { readability = it }
        LiquidComposeSlider("槽体压入", "分段槽体的轻微内凹感", slotDepth, 0f..1.6f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制高级 Haze 外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置 Haze", "恢复精简雾面值", state, Modifier.weight(1f)) { resetValues() }
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
    val border = remember(env.edge) {
        GlassBorderStyle(
            outerStrokeAlpha = 0.04f + env.edge.coerceIn(0f, 1.6f) * 0.08f,
            innerStrokeAlpha = 0f,
            topHighlightAlpha = 0.14f + env.edge.coerceIn(0f, 1.6f) * 0.24f,
            bottomShadowAlpha = 0.04f,
            ringWidthDp = 4f + env.edge.coerceIn(0f, 1.6f) * 4f,
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
                liftAlpha = env.backdropAlpha.coerceIn(0.34f, 1.0f)
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
    val mist = env.surfaceMist.coerceIn(0f, 1.6f)
    val edge = env.edge.coerceIn(0f, 1.6f)
    val readability = env.readability.coerceIn(0f, 1.6f)
    val rimWidth = max(1f, density * (0.42f + edge * 0.28f))
    val frostVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.020f * frost + 0.010f * mist),
            Color.White.copy(alpha = 0.010f * frost),
            Color(0xFF10203C).copy(alpha = 0.006f * mist)
        )
    )
    val softMist = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.025f * mist),
            Color.White.copy(alpha = 0.008f * mist),
            Color.Transparent
        ),
        center = Offset(w * 0.50f, h * 0.20f),
        radius = max(w, h) * 0.86f
    )
    val readableShade = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.040f * readability),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.050f * readability)
        )
    )
    val topGlance = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.042f * edge), Color.White.copy(alpha = 0.016f * edge), Color.Transparent),
        Offset(-w * 0.04f, -h * 0.05f),
        Offset(w * 0.82f, h * 0.20f)
    )
    val outerLine = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.046f * edge), Color(0xFFC7F3FF).copy(alpha = 0.016f * edge), Color.White.copy(alpha = 0.020f * edge)),
        Offset(-w * 0.05f, h * 0.03f),
        Offset(w * 1.04f, h * 0.95f)
    )
    onDrawWithContent {
        drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = softMist, size = size, cornerRadius = corner)
        drawRoundRect(brush = readableShade, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
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
            val frost = env.frost.coerceIn(0f, 1.6f)
            val material = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.022f + 0.010f * frost),
                    Color(0xFF061032).copy(alpha = 0.028f + 0.026f * slot),
                    Color.White.copy(alpha = 0.010f * edge)
                )
            )
            onDrawWithContent {
                drawRoundRect(brush = material, size = size, cornerRadius = corner)
                drawContent()
                drawRoundRect(color = Color.White.copy(alpha = 0.026f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.56f)))
                drawLine(color = Color.White.copy(alpha = 0.010f * slot), start = Offset(w / 3f, h * 0.30f), end = Offset(w / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.42f))
                drawLine(color = Color.White.copy(alpha = 0.010f * slot), start = Offset(w * 2f / 3f, h * 0.30f), end = Offset(w * 2f / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.42f))
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
