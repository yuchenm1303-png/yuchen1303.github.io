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
import kotlin.math.min
import kotlin.math.roundToInt

private data class HazeEnv(
    val backdropAlpha: Float,
    val centerClarity: Float,
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
    var centerClarity by rememberSaveable { mutableStateOf(1.35f) }
    var frost by rememberSaveable { mutableStateOf(0.54f) }
    var edge by rememberSaveable { mutableStateOf(1.35f) }
    var readability by rememberSaveable { mutableStateOf(0.54f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.58f) }
    var elevationShadow by rememberSaveable { mutableStateOf(1.28f) }
    var contactShadow by rememberSaveable { mutableStateOf(1.05f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        backdropAlpha = 0.96f
        centerClarity = 1.35f
        frost = 0.54f
        edge = 1.35f
        readability = 0.54f
        slotDepth = 0.58f
        elevationShadow = 1.28f
        contactShadow = 1.05f
        radiusScale = 1.10f
    }

    val env = HazeEnv(backdropAlpha, centerClarity, frost, edge, readability, slotDepth, elevationShadow, contactShadow)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("悬浮 Haze 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("连续软阴影 + 外侧接触暗边 + 大范围测试参数", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Floating", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        HazeGlassSurface(state, Modifier.fillMaxWidth().height(246.dp), env, radiusScale) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("floating haze glass material", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                HazeSegmentedPill(listOf("Backdrop", "Haze", "Stable"), env, Modifier.fillMaxWidth().height(42.dp))
                Text("目标：像一块玻璃悬浮在壁纸上，而不是一张模糊贴纸贴在屏幕上。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("悬浮阴影", "连续扩散的外部软阴影，用来拉开玻璃和壁纸距离", elevationShadow, 0f..4f) { elevationShadow = it }
        LiquidComposeSlider("接触暗边", "外轮廓附近的环境遮蔽，只在玻璃外侧生效", contactShadow, 0f..4f) { contactShadow = it }
        LiquidComposeSlider("背景透出", "直接控制模糊背景图的可见度", backdropAlpha, 0.1f..1.2f) { backdropAlpha = it }
        LiquidComposeSlider("中心清透", "大范围测试：数值越高，中间越少白雾", centerClarity, 0f..4f) { centerClarity = it }
        LiquidComposeSlider("玻璃雾度", "磨砂玻璃本体的白雾厚度", frost, 0f..4f) { frost = it }
        LiquidComposeSlider("边缘厚度", "大范围测试：轮廓亮边、底部重量和厚度感", edge, 0f..4f) { edge = it }
        LiquidComposeSlider("可读暗场", "保护文字区域，不画内部黑框", readability, 0f..4f) { readability = it }
        LiquidComposeSlider("槽体压入", "分段槽体的轻微内凹感", slotDepth, 0f..4f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制高级 Haze 外壳圆角", radiusScale, 0.45f..2.2f) { radiusScale = it }

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
    val radiusDp = (34f * radiusScale.coerceIn(0.45f, 2.2f)).roundToInt().coerceAtLeast(14)
    val shape = RoundedCornerShape(radiusDp.dp)
    val edge = env.edge.coerceIn(0f, 4f)
    val border = remember(edge) {
        GlassBorderStyle(
            outerStrokeAlpha = 0.018f + min(edge, 4f) * 0.042f,
            innerStrokeAlpha = 0f,
            topHighlightAlpha = 0.10f + min(edge, 4f) * 0.15f,
            bottomShadowAlpha = 0.025f + min(edge, 4f) * 0.030f,
            ringWidthDp = 2.8f + min(edge, 4f) * 2.4f,
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
                .padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 24.dp)
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
                    liftAlpha = env.backdropAlpha.coerceIn(0.10f, 1.2f)
                )
            }
            Box(Modifier.matchParentSize().hazeSkin(env, radiusDp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

private fun Modifier.floatingHazeShadow(env: HazeEnv, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val side = 8.dp.toPx()
    val top = 7.dp.toPx()
    val bottom = 24.dp.toPx()
    val cardLeft = side
    val cardTop = top
    val cardSize = Size(max(1f, w - side * 2f), max(1f, h - top - bottom))
    val cornerRadius = radiusDp.dp.toPx()
    val elevation = env.elevationShadow.coerceIn(0f, 4f)
    val contact = env.contactShadow.coerceIn(0f, 4f)
    val ambientShadow = Brush.radialGradient(
        listOf(
            Color.Black.copy(alpha = 0.050f * elevation),
            Color.Black.copy(alpha = 0.020f * elevation),
            Color.Transparent
        ),
        center = Offset(w * 0.54f, cardTop + cardSize.height + bottom * 0.48f),
        radius = w * (0.54f + elevation * 0.035f)
    )
    val contactBrush = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.012f * contact),
            Color.Black.copy(alpha = 0.060f * contact),
            Color.Black.copy(alpha = 0.018f * contact),
            Color.Transparent
        ),
        startY = cardTop + cardSize.height * 0.60f,
        endY = cardTop + cardSize.height + bottom
    )
    onDrawWithContent {
        drawOval(
            brush = ambientShadow,
            topLeft = Offset(w * -0.05f, cardTop + cardSize.height * 0.68f),
            size = Size(w * 1.12f, bottom * 2.10f + 28.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
        for (i in 10 downTo 1) {
            val p = i / 10f
            val spread = (1f - p) * 20.dp.toPx()
            val yOffset = (1f - p) * 13.dp.toPx() + 6.dp.toPx()
            val alpha = 0.0065f * elevation * p * p
            drawRoundRect(
                color = Color.Black.copy(alpha = alpha),
                topLeft = Offset(cardLeft - spread * 0.45f, cardTop + yOffset - spread * 0.20f),
                size = Size(cardSize.width + spread * 0.90f, cardSize.height + spread * 0.46f),
                cornerRadius = CornerRadius(cornerRadius + spread * 0.40f, cornerRadius + spread * 0.40f),
                blendMode = BlendMode.Multiply
            )
        }
        drawRoundRect(
            brush = contactBrush,
            topLeft = Offset(cardLeft - 3.dp.toPx(), cardTop + 2.dp.toPx()),
            size = Size(cardSize.width + 6.dp.toPx(), cardSize.height + bottom * 0.55f),
            cornerRadius = CornerRadius(cornerRadius + 2.dp.toPx(), cornerRadius + 2.dp.toPx()),
            style = Stroke(width = max(1f, 10.dp.toPx() + contact * 5.dp.toPx())),
            blendMode = BlendMode.Multiply
        )
        drawContent()
    }
}

private fun Modifier.hazeSkin(env: HazeEnv, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val frost = env.frost.coerceIn(0f, 4f)
    val clear = env.centerClarity.coerceIn(0f, 4f)
    val edge = env.edge.coerceIn(0f, 4f)
    val readability = env.readability.coerceIn(0f, 4f)
    val centerFogFactor = (1f - min(clear / 3.2f, 0.88f)).coerceIn(0.06f, 1f)
    val rimWidth = max(1f, density * (0.34f + edge * 0.34f))
    val frostVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.010f * frost * centerFogFactor + 0.004f * edge),
            Color.White.copy(alpha = 0.006f * frost * centerFogFactor),
            Color(0xFF10203C).copy(alpha = 0.004f * frost)
        )
    )
    val centerClearField = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.004f * frost * centerFogFactor),
            Color.White.copy(alpha = 0.028f * frost)
        ),
        center = Offset(w * 0.50f, h * 0.50f),
        radius = max(w, h) * 0.82f
    )
    val readableShade = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.020f * readability),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.036f * readability)
        )
    )
    val topGlance = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.030f + 0.050f * edge), Color.White.copy(alpha = 0.014f + 0.018f * edge), Color.Transparent),
        Offset(-w * 0.04f, -h * 0.06f),
        Offset(w * 0.84f, h * 0.20f)
    )
    val sideGlance = Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0.032f * edge), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.020f * edge)),
        startX = 0f,
        endX = w
    )
    val outerLine = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.030f + 0.036f * edge), Color(0xFFC7F3FF).copy(alpha = 0.010f + 0.010f * edge), Color.White.copy(alpha = 0.012f + 0.014f * edge)),
        Offset(-w * 0.05f, h * 0.03f),
        Offset(w * 1.04f, h * 0.95f)
    )
    val bottomWeight = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.018f * edge)),
        startY = h * 0.42f,
        endY = h
    )
    onDrawWithContent {
        drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = centerClearField, size = size, cornerRadius = corner)
        drawRoundRect(brush = readableShade, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = sideGlance, size = size, cornerRadius = corner, style = Stroke(width = max(1f, rimWidth * 2.4f)), blendMode = BlendMode.Screen)
        drawRoundRect(brush = bottomWeight, size = size, cornerRadius = corner, style = Stroke(width = max(1f, rimWidth * 2.6f)), blendMode = BlendMode.Multiply)
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
            val edge = env.edge.coerceIn(0f, 4f)
            val slot = env.slotDepth.coerceIn(0f, 4f)
            val frost = env.frost.coerceIn(0f, 4f)
            val material = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.014f + 0.006f * frost),
                    Color(0xFF061032).copy(alpha = 0.020f + 0.020f * slot),
                    Color.White.copy(alpha = 0.006f * edge)
                )
            )
            onDrawWithContent {
                drawRoundRect(brush = material, size = size, cornerRadius = corner)
                drawContent()
                drawRoundRect(color = Color.White.copy(alpha = 0.020f + 0.014f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * (0.42f + 0.12f * edge))))
                drawLine(color = Color.White.copy(alpha = 0.008f * slot), start = Offset(w / 3f, h * 0.30f), end = Offset(w / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
                drawLine(color = Color.White.copy(alpha = 0.008f * slot), start = Offset(w * 2f / 3f, h * 0.30f), end = Offset(w * 2f / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
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
