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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.max
import kotlin.math.roundToInt

private data class BackdropEnvironment(
    val sourceMix: Float,
    val frost: Float,
    val edge: Float,
    val ambientLift: Float,
    val innerShade: Float,
    val readability: Float,
    val phase: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var sourceMix by rememberSaveable { mutableStateOf(0.92f) }
    var frost by rememberSaveable { mutableStateOf(0.72f) }
    var edge by rememberSaveable { mutableStateOf(0.64f) }
    var ambientLift by rememberSaveable { mutableStateOf(0.58f) }
    var innerShade by rememberSaveable { mutableStateOf(0.72f) }
    var readability by rememberSaveable { mutableStateOf(0.82f) }
    var segmentDepth by rememberSaveable { mutableStateOf(0.78f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.00f) }
    var phase by rememberSaveable { mutableStateOf(0.18f) }

    fun resetValues() {
        sourceMix = 0.92f
        frost = 0.72f
        edge = 0.64f
        ambientLift = 0.58f
        innerShade = 0.72f
        readability = 0.82f
        segmentDepth = 0.78f
        radiusScale = 1.00f
        phase = 0.18f
    }

    val environment = BackdropEnvironment(
        sourceMix = sourceMix,
        frost = frost,
        edge = edge,
        ambientLift = ambientLift,
        innerShade = innerShade,
        readability = readability,
        phase = phase
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("背景同源 Compose 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Haze 思路：背景是 source，玻璃只做薄材质 effect", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Source / Effect", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        SourceLinkedGlassSurface(
            modifier = Modifier.fillMaxWidth().height(224.dp),
            environment = environment,
            radiusScale = radiusScale
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("source-linked material", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                SourceLinkedSegmentedPill(
                    labels = listOf("Compose", "Canvas", "同源"),
                    insetDepth = segmentDepth,
                    environment = environment,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
                Text("目标：用背景同源色场做薄、润、克制的伪玻璃。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("背景染入", "玻璃吸收背景主色的程度", sourceMix, 0f..1.8f) { sourceMix = it }
        LiquidComposeSlider("雾面强度", "薄雾和柔化程度，不做惨白光晕", frost, 0f..1.6f) { frost = it }
        LiquidComposeSlider("边缘薄线", "外轮廓与顶部一笔高光", edge, 0f..1.6f) { edge = it }
        LiquidComposeSlider("环境亮区", "模拟背景亮区透入玻璃的幅度", ambientLift, 0f..1.6f) { ambientLift = it }
        LiquidComposeSlider("内侧暗边", "单层内凹压边，避免多层框", innerShade, 0f..1.6f) { innerShade = it }
        LiquidComposeSlider("可读暗场", "保护文字区域的连续暗场", readability, 0f..1.6f) { readability = it }
        LiquidComposeSlider("槽体嵌入", "分段胶囊槽压入玻璃的程度", segmentDepth, 0f..1.6f) { segmentDepth = it }
        LiquidComposeSlider("环境相位", "手动模拟背景色场移动", phase, 0f..1f) { phase = it }
        LiquidComposeSlider("圆角倍率", "控制一体玻璃外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置同源", "恢复建议初始值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("隔离验证", "不接入 OpenGL", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
fun SourceLinkedGlassSurface(
    modifier: Modifier = Modifier,
    environment: BackdropEnvironment,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val radius = (34f * radiusScale.coerceIn(0.65f, 1.55f)).dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val r = minOf(w, h) * 0.22f * radiusScale.coerceIn(0.65f, 1.55f)
                val corner = CornerRadius(r, r)
                val sourceShiftX = ((rootOffset.x / 1080f) + environment.phase).coerceIn(0f, 2f)
                val sourceShiftY = ((rootOffset.y / 2200f) + environment.phase * 0.55f).coerceIn(0f, 2f)
                val rimWidth = max(1f, density * (0.72f + environment.edge * 0.62f))
                val innerInset = rimWidth * (2.20f + environment.innerShade * 0.22f)
                val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
                val innerCorner = CornerRadius(max(1f, r - innerInset), max(1f, r - innerInset))

                val sourceField = Brush.linearGradient(
                    listOf(
                        Color(0xFF6AD6C7).copy(alpha = 0.030f * environment.sourceMix),
                        Color(0xFF3F5FD7).copy(alpha = 0.070f * environment.sourceMix),
                        Color(0xFFCF72D8).copy(alpha = 0.052f * environment.sourceMix)
                    ),
                    start = Offset(-w * (0.18f + sourceShiftX * 0.20f), h * (0.18f + sourceShiftY * 0.08f)),
                    end = Offset(w * (1.06f + sourceShiftX * 0.16f), h * (0.82f - sourceShiftY * 0.05f))
                )
                val thinMaterial = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.018f * environment.frost),
                        Color(0xFF0A153A).copy(alpha = 0.210f + environment.readability * 0.025f),
                        Color(0xFF050C28).copy(alpha = 0.225f + environment.innerShade * 0.024f)
                    )
                )
                val frostVeil = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.050f * environment.frost),
                        Color(0xFFC4D6FF).copy(alpha = 0.022f * environment.frost),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.22f + sourceShiftX * 0.08f), h * (0.12f + sourceShiftY * 0.05f)),
                    radius = w * 0.78f
                )
                val ambientSource = Brush.radialGradient(
                    listOf(
                        Color(0xFF9FD8FF).copy(alpha = 0.058f * environment.ambientLift),
                        Color(0xFF7E6CFF).copy(alpha = 0.024f * environment.ambientLift),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.72f + sourceShiftX * 0.10f), h * (0.38f + sourceShiftY * 0.18f)),
                    radius = w * 0.54f
                )
                val readabilityField = Brush.verticalGradient(
                    listOf(
                        Color(0xFF020820).copy(alpha = 0.082f * environment.readability),
                        Color.Transparent,
                        Color(0xFF020820).copy(alpha = 0.094f * environment.readability)
                    )
                )
                val innerShadeField = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF00051A).copy(alpha = 0.064f * environment.innerShade),
                        Color.Transparent
                    ),
                    start = Offset(w * 0.04f, h * 0.16f),
                    end = Offset(w * 0.96f, h * 1.02f)
                )
                val topGlance = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.064f * environment.edge),
                        Color.White.copy(alpha = 0.022f * environment.edge),
                        Color.Transparent
                    ),
                    start = Offset(-w * 0.02f, -h * 0.08f),
                    end = Offset(w * 0.72f, h * 0.28f)
                )
                val edgeTint = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.052f * environment.edge),
                        Color(0xFFA7D8FF).copy(alpha = 0.026f * environment.edge),
                        Color.White.copy(alpha = 0.034f * environment.edge)
                    ),
                    start = Offset(-w * 0.04f, h * 0.06f),
                    end = Offset(w * 1.04f, h * 0.92f)
                )

                onDrawWithContent {
                    drawRoundRect(brush = thinMaterial, size = size, cornerRadius = corner)
                    drawRoundRect(brush = sourceField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
                    drawRoundRect(brush = ambientSource, size = size, cornerRadius = corner)
                    drawRoundRect(brush = innerShadeField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = readabilityField, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
                    drawRoundRect(brush = edgeTint, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
                    drawRoundRect(color = Color(0xFF02071D).copy(alpha = 0.040f * environment.innerShade), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.42f)))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SourceLinkedSegmentedPill(
    labels: List<String>,
    insetDepth: Float,
    environment: BackdropEnvironment,
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
                val source = Brush.linearGradient(
                    listOf(
                        Color(0xFF8DE6D7).copy(alpha = 0.020f * environment.sourceMix),
                        Color(0xFF7898E8).copy(alpha = 0.040f * environment.sourceMix),
                        Color(0xFFD989E6).copy(alpha = 0.024f * environment.sourceMix)
                    ),
                    start = Offset(-w * environment.phase, h * 0.35f),
                    end = Offset(w * (1f + environment.phase * 0.50f), h * 0.70f)
                )
                val material = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.040f + 0.016f * insetDepth),
                        Color(0xFF061032).copy(alpha = 0.056f + 0.036f * insetDepth),
                        Color.White.copy(alpha = 0.012f * environment.edge)
                    )
                )
                onDrawWithContent {
                    drawRoundRect(brush = material, size = size, cornerRadius = corner)
                    drawRoundRect(brush = source, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(color = Color.White.copy(alpha = 0.032f * environment.edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.70f)))
                    drawLine(color = Color.White.copy(alpha = 0.020f * insetDepth), start = Offset(w / 3f, h * 0.26f), end = Offset(w / 3f, h * 0.74f), strokeWidth = max(1f, density * 0.50f))
                    drawLine(color = Color.White.copy(alpha = 0.020f * insetDepth), start = Offset(w * 2f / 3f, h * 0.26f), end = Offset(w * 2f / 3f, h * 0.74f), strokeWidth = max(1f, density * 0.50f))
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
