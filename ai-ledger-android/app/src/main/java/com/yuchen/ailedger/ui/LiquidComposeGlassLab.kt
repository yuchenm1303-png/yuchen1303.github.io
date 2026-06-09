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
import kotlin.math.max
import kotlin.math.roundToInt

private data class SampledLiquidEnvironment(
    val backgroundMix: Float,
    val backgroundSoftness: Float,
    val frost: Float,
    val tintCompensation: Float,
    val edge: Float,
    val innerShade: Float,
    val readability: Float,
    val phase: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var backgroundMix by rememberSaveable { mutableStateOf(0.72f) }
    var backgroundSoftness by rememberSaveable { mutableStateOf(0.38f) }
    var frost by rememberSaveable { mutableStateOf(0.82f) }
    var tintCompensation by rememberSaveable { mutableStateOf(0.18f) }
    var edge by rememberSaveable { mutableStateOf(0.96f) }
    var innerShade by rememberSaveable { mutableStateOf(0.78f) }
    var readability by rememberSaveable { mutableStateOf(0.74f) }
    var segmentDepth by rememberSaveable { mutableStateOf(0.32f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }
    var phase by rememberSaveable { mutableStateOf(0.33f) }

    fun resetValues() {
        backgroundMix = 0.72f
        backgroundSoftness = 0.38f
        frost = 0.82f
        tintCompensation = 0.18f
        edge = 0.96f
        innerShade = 0.78f
        readability = 0.74f
        segmentDepth = 0.32f
        radiusScale = 1.10f
        phase = 0.33f
    }

    val environment = SampledLiquidEnvironment(
        backgroundMix = backgroundMix,
        backgroundSoftness = backgroundSoftness,
        frost = frost,
        tintCompensation = tintCompensation,
        edge = edge,
        innerShade = innerShade,
        readability = readability,
        phase = phase
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("采样背景 Compose 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("直接使用项目已有模糊背景缓存，玻璃只叠薄材质 effect", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Sampled", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        SampledLiquidComposeSurface(
            state = state,
            modifier = Modifier.fillMaxWidth().height(224.dp),
            environment = environment,
            radiusScale = radiusScale
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("sampled backdrop material", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                SampledLiquidSegmentedPill(
                    labels = listOf("Compose", "Canvas", "采样"),
                    insetDepth = segmentDepth,
                    environment = environment,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
                Text("目标：真实模糊背景进玻璃，再用薄雾、暗场和细边收住质感。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("背景融合", "采样模糊背景进入玻璃的强度", backgroundMix, 0f..1.8f) { backgroundMix = it }
        LiquidComposeSlider("背景柔化", "压低月亮、星点等细节的额外雾化层", backgroundSoftness, 0f..1.6f) { backgroundSoftness = it }
        LiquidComposeSlider("玻璃雾度", "乳玻璃表面薄雾，不再做固定白光晕", frost, 0f..1.6f) { frost = it }
        LiquidComposeSlider("色场补偿", "背景偏暗时补少量青紫大色面", tintCompensation, 0f..1.6f) { tintCompensation = it }
        LiquidComposeSlider("边缘薄线", "极简外轮廓和顶部一笔高光", edge, 0f..1.6f) { edge = it }
        LiquidComposeSlider("内侧暗边", "单层内凹压边，避免多层框", innerShade, 0f..1.6f) { innerShade = it }
        LiquidComposeSlider("可读暗场", "保护标题和说明文字对比度", readability, 0f..1.6f) { readability = it }
        LiquidComposeSlider("槽体嵌入", "分段胶囊槽压入玻璃的程度", segmentDepth, 0f..1.6f) { segmentDepth = it }
        LiquidComposeSlider("环境相位", "移动补偿色场，模拟背景色场位置", phase, 0f..1f) { phase = it }
        LiquidComposeSlider("圆角倍率", "控制一体玻璃外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置采样", "恢复建议初始值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("隔离验证", "只用 Compose / Canvas", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun SampledLiquidComposeSurface(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    environment: SampledLiquidEnvironment,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    val coordinateSource = remember { GlassCoordinateSource() }
    val radiusDp = (34f * radiusScale.coerceIn(0.65f, 1.55f)).roundToInt().coerceAtLeast(18)
    val shape = RoundedCornerShape(radiusDp.dp)
    Box(
        modifier = modifier
            .onPlaced { coordinateSource.coordinates = it }
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        SampledWeatherGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            radius = radiusDp,
            coordinateSource = coordinateSource,
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            blurRadiusDp = (72f + environment.backgroundSoftness.coerceIn(0f, 1.6f) * 64f).roundToInt(),
            liftAlpha = (0.36f + environment.backgroundMix.coerceIn(0f, 1.8f) * 0.40f).coerceIn(0.34f, 1f)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .sampledLiquidMaterialSkin(environment, radiusDp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun Modifier.sampledLiquidMaterialSkin(
    environment: SampledLiquidEnvironment,
    radiusDp: Int
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val mix = environment.backgroundMix.coerceIn(0f, 1.8f)
    val softness = environment.backgroundSoftness.coerceIn(0f, 1.6f)
    val frost = environment.frost.coerceIn(0f, 1.6f)
    val tint = environment.tintCompensation.coerceIn(0f, 1.6f)
    val edge = environment.edge.coerceIn(0f, 1.6f)
    val inner = environment.innerShade.coerceIn(0f, 1.6f)
    val read = environment.readability.coerceIn(0f, 1.6f)
    val phase = environment.phase.coerceIn(0f, 1f)
    val rimWidth = max(1f, density * (0.70f + edge * 0.42f))
    val innerInset = rimWidth * (2.05f + inner * 0.28f)
    val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
    val innerCorner = CornerRadius(max(1f, cornerRadius - innerInset), max(1f, cornerRadius - innerInset))

    val softeningVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.030f * frost + 0.040f * softness),
            Color(0xFFBFD2F5).copy(alpha = 0.020f * frost + 0.036f * softness),
            Color(0xFF07122F).copy(alpha = 0.030f * read + 0.016f * softness)
        )
    )
    val sourceTint = Brush.linearGradient(
        listOf(
            Color(0xFF6AD6C7).copy(alpha = 0.018f * mix + 0.018f * tint),
            Color(0xFF3F5FD7).copy(alpha = 0.030f * mix + 0.024f * tint),
            Color(0xFFCF72D8).copy(alpha = 0.014f * mix + 0.034f * tint)
        ),
        start = Offset(-w * (0.10f + phase * 0.28f), h * 0.18f),
        end = Offset(w * (1.05f + phase * 0.18f), h * 0.82f)
    )
    val frostField = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.040f * frost + 0.020f * softness),
            Color(0xFFE3ECFF).copy(alpha = 0.014f * frost + 0.016f * softness),
            Color.Transparent
        ),
        center = Offset(w * (0.20f + phase * 0.12f), h * 0.12f),
        radius = w * 0.86f
    )
    val readabilityField = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.070f * read),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.082f * read)
        )
    )
    val innerShadeField = Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color(0xFF00051A).copy(alpha = 0.044f * inner),
            Color.Transparent
        ),
        start = Offset(w * 0.05f, h * 0.16f),
        end = Offset(w * 0.96f, h * 1.02f)
    )
    val topGlance = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.050f * edge),
            Color.White.copy(alpha = 0.018f * edge),
            Color.Transparent
        ),
        start = Offset(-w * 0.02f, -h * 0.10f),
        end = Offset(w * 0.74f, h * 0.26f)
    )
    val outerLine = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.050f * edge),
            Color(0xFF9EDBFF).copy(alpha = 0.018f * edge + 0.010f * tint),
            Color.White.copy(alpha = 0.028f * edge)
        ),
        start = Offset(-w * 0.02f, h * 0.04f),
        end = Offset(w * 1.04f, h * 0.92f)
    )

    onDrawWithContent {
        drawRoundRect(brush = softeningVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = sourceTint, size = size, cornerRadius = corner)
        drawRoundRect(brush = frostField, size = size, cornerRadius = corner)
        drawRoundRect(brush = innerShadeField, size = size, cornerRadius = corner)
        drawRoundRect(brush = readabilityField, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
        drawRoundRect(color = Color(0xFF02071D).copy(alpha = 0.034f * inner), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.42f)))
        drawRoundRect(color = Color.White.copy(alpha = 0.022f * edge), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.26f)))
    }
}

@Composable
private fun SampledLiquidSegmentedPill(
    labels: List<String>,
    insetDepth: Float,
    environment: SampledLiquidEnvironment,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val corner = CornerRadius(h / 2f, h / 2f)
                val mix = environment.backgroundMix.coerceIn(0f, 1.8f)
                val edge = environment.edge.coerceIn(0f, 1.6f)
                val tint = environment.tintCompensation.coerceIn(0f, 1.6f)
                val inset = insetDepth.coerceIn(0f, 1.6f)
                val phase = environment.phase.coerceIn(0f, 1f)
                val material = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.034f + 0.014f * inset),
                        Color(0xFF061032).copy(alpha = 0.048f + 0.030f * inset),
                        Color.White.copy(alpha = 0.010f * edge)
                    )
                )
                val sampledTint = Brush.linearGradient(
                    listOf(
                        Color(0xFF8DE6D7).copy(alpha = 0.014f * mix + 0.012f * tint),
                        Color(0xFF7898E8).copy(alpha = 0.026f * mix + 0.014f * tint),
                        Color(0xFFD989E6).copy(alpha = 0.014f * mix + 0.020f * tint)
                    ),
                    start = Offset(-w * phase, h * 0.35f),
                    end = Offset(w * (1f + phase * 0.50f), h * 0.70f)
                )
                onDrawWithContent {
                    drawRoundRect(brush = material, size = size, cornerRadius = corner)
                    drawRoundRect(brush = sampledTint, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(color = Color.White.copy(alpha = 0.030f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.70f)))
                    drawLine(color = Color.White.copy(alpha = 0.018f * inset), start = Offset(w / 3f, h * 0.28f), end = Offset(w / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
                    drawLine(color = Color.White.copy(alpha = 0.018f * inset), start = Offset(w * 2f / 3f, h * 0.28f), end = Offset(w * 2f / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
                }
            }
            .padding(horizontal = 2.dp),
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
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.64f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(54.dp),
        role = GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLiquidLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
