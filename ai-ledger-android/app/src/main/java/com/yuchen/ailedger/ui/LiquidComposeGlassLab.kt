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
import androidx.compose.ui.graphics.BlendMode
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
    val slotDepth: Float,
    val elevationShadow: Float,
    val contactShadow: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var backdropAlpha by rememberSaveable { mutableStateOf(0.96f) }
    var surfaceMist by rememberSaveable { mutableStateOf(0.52f) }
    var frost by rememberSaveable { mutableStateOf(0.46f) }
    var edge by rememberSaveable { mutableStateOf(0.54f) }
    var readability by rememberSaveable { mutableStateOf(0.50f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.34f) }
    var elevationShadow by rememberSaveable { mutableStateOf(0.72f) }
    var contactShadow by rememberSaveable { mutableStateOf(0.52f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        backdropAlpha = 0.96f
        surfaceMist = 0.52f
        frost = 0.46f
        edge = 0.54f
        readability = 0.50f
        slotDepth = 0.34f
        elevationShadow = 0.72f
        contactShadow = 0.52f
        radiusScale = 1.10f
    }

    val env = HazeEnv(backdropAlpha, surfaceMist, frost, edge, readability, slotDepth, elevationShadow, contactShadow)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("悬浮 Haze 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("外部软投影 + 接触暗边 + 稳定背景采样，不接 OpenGL", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Floating", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        HazeGlassSurface(state, Modifier.fillMaxWidth().height(232.dp), env, radiusScale) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("floating haze glass material", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                HazeSegmentedPill(listOf("Backdrop", "Haze", "Stable"), env, Modifier.fillMaxWidth().height(42.dp))
                Text("目标：像一块玻璃悬浮在壁纸上，而不是一张模糊贴纸贴在屏幕上。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("悬浮阴影", "玻璃投到壁纸上的外部软阴影", elevationShadow, 0f..1.6f) { elevationShadow = it }
        LiquidComposeSlider("接触暗边", "贴着外轮廓的环境遮蔽，不画内部黑框", contactShadow, 0f..1.6f) { contactShadow = it }
        LiquidComposeSlider("背景透出", "直接控制模糊背景图的可见度", backdropAlpha, 0.34f..1.0f) { backdropAlpha = it }
        LiquidComposeSlider("中心清透", "数值越高，中间越少白雾、越像厚玻璃", surfaceMist, 0f..1.6f) { surfaceMist = it }
        LiquidComposeSlider("玻璃雾度", "磨砂玻璃本体的白雾厚度", frost, 0f..1.6f) { frost = it }
        LiquidComposeSlider("边缘厚度", "极细轮廓、顶部亮边和底部重量", edge, 0f..1.6f) { edge = it }
        LiquidComposeSlider("可读暗场", "保护文字区域，不画内部黑框", readability, 0f..1.6f) { readability = it }
        LiquidComposeSlider("槽体压入", "分段槽体的轻微内凹感", slotDepth, 0f..1.6f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制高级 Haze 外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置悬浮", "恢复悬浮玻璃值", state, Modifier.weight(1f)) { resetValues() }
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
            outerStrokeAlpha = 0.035f + env.edge.coerceIn(0f, 1.6f) * 0.075f,
            innerStrokeAlpha = 0f,
            topHighlightAlpha = 0.16f + env.edge.coerceIn(0f, 1.6f) * 0.24f,
            bottomShadowAlpha = 0.035f,
            ringWidthDp = 3.5f + env.edge.coerceIn(0f, 1.6f) * 3.5f,
            bodyAlpha = 0f
        )
    }
    val spec = remember(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border) {
        GlassBackdropSpec(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border)
    }

    Box(
        modifier = modifier.floatingHazeShadow(env, radiusDp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, top = 3.dp, end = 4.dp, bottom = 11.dp)
                .onPlaced { coordinateSource.coordinates = it }
                .clip(shape),
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
}

private fun Modifier.floatingHazeShadow(env: HazeEnv, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val side = 4.dp.toPx()
    val top = 3.dp.toPx()
    val bottom = 11.dp.toPx()
    val cardSize = Size(max(1f, w - side * 2f), max(1f, h - top - bottom))
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val elevation = env.elevationShadow.coerceIn(0f, 1.6f)
    val contact = env.contactShadow.coerceIn(0f, 1.6f)
    onDrawWithContent {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.035f * elevation),
            topLeft = Offset(side + 2.dp.toPx(), top + 12.dp.toPx()),
            size = cardSize.copy(height = max(1f, cardSize.height - 1.dp.toPx())),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.026f * elevation),
            topLeft = Offset(side - 1.dp.toPx(), top + 7.dp.toPx()),
            size = Size(cardSize.width + 2.dp.toPx(), cardSize.height + 2.dp.toPx()),
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.000f),
                    Color.Black.copy(alpha = 0.045f * contact),
                    Color.Black.copy(alpha = 0.070f * contact)
                ),
                startY = top + cardSize.height * 0.55f,
                endY = top + cardSize.height + bottom
            ),
            topLeft = Offset(side, top),
            size = cardSize,
            cornerRadius = corner,
            style = Stroke(width = max(1f, 7.dp.toPx()))
        )
        drawContent()
    }
}

private fun Modifier.hazeSkin(env: HazeEnv, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val frost = env.frost.coerceIn(0f, 1.6f)
    val clearCenter = env.surfaceMist.coerceIn(0f, 1.6f)
    val edge = env.edge.coerceIn(0f, 1.6f)
    val readability = env.readability.coerceIn(0f, 1.6f)
    val rimWidth = max(1f, density * (0.40f + edge * 0.26f))
    val frostVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.018f * frost),
            Color.White.copy(alpha = 0.008f * frost * (1f - clearCenter.coerceIn(0f, 1f) * 0.45f)),
            Color(0xFF10203C).copy(alpha = 0.006f * frost)
        )
    )
    val centerClear = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.010f * (1f - clearCenter.coerceIn(0f, 1f))),
            Color.White.copy(alpha = 0.022f * frost)
        ),
        center = Offset(w * 0.50f, h * 0.50f),
        radius = max(w, h) * 0.80f
    )
    val readableShade = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.030f * readability),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.040f * readability)
        )
    )
    val topGlance = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.050f * edge), Color.White.copy(alpha = 0.018f * edge), Color.Transparent),
        Offset(-w * 0.04f, -h * 0.05f),
        Offset(w * 0.82f, h * 0.20f)
    )
    val outerLine = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.052f * edge), Color(0xFFC7F3FF).copy(alpha = 0.014f * edge), Color.White.copy(alpha = 0.018f * edge)),
        Offset(-w * 0.05f, h * 0.03f),
        Offset(w * 1.04f, h * 0.95f)
    )
    onDrawWithContent {
        drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = centerClear, size = size, cornerRadius = corner)
        drawRoundRect(brush = readableShade, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.030f * edge))),
            size = size,
            cornerRadius = corner,
            style = Stroke(width = max(1f, 1.5.dp.toPx())),
            blendMode = BlendMode.Multiply
        )
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
                    Color.White.copy(alpha = 0.018f + 0.008f * frost),
                    Color(0xFF061032).copy(alpha = 0.024f + 0.022f * slot),
                    Color.White.copy(alpha = 0.008f * edge)
                )
            )
            onDrawWithContent {
                drawRoundRect(brush = material, size = size, cornerRadius = corner)
                drawContent()
                drawRoundRect(color = Color.White.copy(alpha = 0.024f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.50f)))
                drawLine(color = Color.White.copy(alpha = 0.009f * slot), start = Offset(w / 3f, h * 0.30f), end = Offset(w / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
                drawLine(color = Color.White.copy(alpha = 0.009f * slot), start = Offset(w * 2f / 3f, h * 0.30f), end = Offset(w * 2f / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
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
