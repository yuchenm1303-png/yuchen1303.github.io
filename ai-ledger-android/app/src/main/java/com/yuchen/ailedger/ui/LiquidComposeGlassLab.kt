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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var tintStrength by rememberSaveable { mutableStateOf(0.82f) }
    var frostStrength by rememberSaveable { mutableStateOf(0.58f) }
    var edgeHighlight by rememberSaveable { mutableStateOf(0.72f) }
    var innerShadow by rememberSaveable { mutableStateOf(0.86f) }
    var segmentDepth by rememberSaveable { mutableStateOf(0.76f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.00f) }

    fun resetValues() {
        tintStrength = 0.82f
        frostStrength = 0.58f
        edgeHighlight = 0.72f
        innerShadow = 0.86f
        segmentDepth = 0.76f
        radiusScale = 1.00f
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("极简液态 Compose", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("一体胶囊、少描边、弱光效，先做苹果式安静玻璃", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Minimal", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        MinimalLiquidComposeSurface(
            modifier = Modifier.fillMaxWidth().height(214.dp),
            tintStrength = tintStrength,
            frostStrength = frostStrength,
            edgeHighlight = edgeHighlight,
            innerShadow = innerShadow,
            radiusScale = radiusScale
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 1)
                MinimalLiquidSegmentedPill(
                    labels = listOf("Compose", "Canvas", "连续"),
                    insetDepth = segmentDepth,
                    edgeHighlight = edgeHighlight,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )
                Text("目标：一整块连续玻璃，不堆多层框。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("主体染色", "模拟背景染色，不再做厚重蓝色蒙层", tintStrength, 0f..1.8f) { tintStrength = it }
        LiquidComposeSlider("雾面强度", "连续柔雾，只保留轻微玻璃空气感", frostStrength, 0f..1.6f) { frostStrength = it }
        LiquidComposeSlider("边缘高光", "只保留一圈极细主轮廓和掠射光", edgeHighlight, 0f..1.8f) { edgeHighlight = it }
        LiquidComposeSlider("内侧暗边", "一条很淡的内凹暗边，避免多层框", innerShadow, 0f..1.8f) { innerShadow = it }
        LiquidComposeSlider("按钮嵌入", "把三个按钮改成同一胶囊内的分区", segmentDepth, 0f..1.8f) { segmentDepth = it }
        LiquidComposeSlider("圆角倍率", "控制一体胶囊外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置极简", "恢复建议初始值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("隔离验证", "Compose / Canvas", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
fun MinimalLiquidComposeSurface(
    modifier: Modifier = Modifier,
    tintStrength: Float,
    frostStrength: Float,
    edgeHighlight: Float,
    innerShadow: Float,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    val radius = (42f * radiusScale.coerceIn(0.65f, 1.55f)).dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val r = minOf(w, h) * 0.25f * radiusScale.coerceIn(0.65f, 1.55f)
                val corner = CornerRadius(r, r)
                val rimWidth = max(1f, density * (0.90f + edgeHighlight * 0.62f))
                val innerInset = rimWidth * 2.20f
                val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
                val innerCorner = CornerRadius(max(1f, r - innerInset), max(1f, r - innerInset))

                val tintField = Brush.linearGradient(
                    listOf(
                        Color(0xFF12A894).copy(alpha = 0.028f * tintStrength),
                        Color(0xFF1C3B8E).copy(alpha = 0.086f * tintStrength),
                        Color(0xFF456BFF).copy(alpha = 0.052f * tintStrength)
                    ),
                    start = Offset(-w * 0.06f, h * 0.20f),
                    end = Offset(w * 1.06f, h * 0.72f)
                )
                val quietBase = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.028f * frostStrength),
                        Color(0xFF071238).copy(alpha = 0.245f + tintStrength * 0.018f),
                        Color(0xFF030B28).copy(alpha = 0.265f + innerShadow * 0.025f)
                    )
                )
                val softMist = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.070f * frostStrength),
                        Color(0xFFB6CDFF).copy(alpha = 0.026f * frostStrength),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.16f),
                    radius = w * 0.72f
                )
                val edgeBlend = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.050f * edgeHighlight),
                        Color.Transparent,
                        Color(0xFF7FC4FF).copy(alpha = 0.044f * edgeHighlight)
                    ),
                    start = Offset(w * 0.05f, -h * 0.04f),
                    end = Offset(w * 1.02f, h * 1.05f)
                )
                val innerDarkField = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF00051A).copy(alpha = 0.078f * innerShadow),
                        Color.Transparent
                    ),
                    start = Offset(w * 0.05f, h * 0.18f),
                    end = Offset(w * 0.94f, h * 1.02f)
                )

                onDrawWithContent {
                    drawRoundRect(brush = quietBase, size = size, cornerRadius = corner)
                    drawRoundRect(brush = tintField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = softMist, size = size, cornerRadius = corner)
                    drawRoundRect(brush = innerDarkField, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(brush = edgeBlend, size = size, cornerRadius = corner)
                    drawRoundRect(color = Color.White.copy(alpha = 0.058f * edgeHighlight), size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
                    drawRoundRect(color = Color(0xFF060B20).copy(alpha = 0.054f * innerShadow), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.50f)))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun MinimalLiquidSegmentedPill(
    labels: List<String>,
    insetDepth: Float,
    edgeHighlight: Float,
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
                val body = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.048f + 0.020f * insetDepth),
                        Color(0xFF7898E8).copy(alpha = 0.028f + 0.018f * insetDepth),
                        Color(0xFF030824).copy(alpha = 0.052f * insetDepth)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
                val insetShade = Brush.verticalGradient(
                    listOf(
                        Color(0xFF00051A).copy(alpha = 0.080f * insetDepth),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.026f * edgeHighlight)
                    )
                )
                onDrawWithContent {
                    drawRoundRect(brush = body, size = size, cornerRadius = corner)
                    drawRoundRect(brush = insetShade, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(color = Color.White.copy(alpha = 0.040f * edgeHighlight), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.78f)))
                    drawLine(color = Color.White.copy(alpha = 0.025f * insetDepth), start = Offset(w / 3f, h * 0.22f), end = Offset(w / 3f, h * 0.78f), strokeWidth = max(1f, density * 0.52f))
                    drawLine(color = Color.White.copy(alpha = 0.025f * insetDepth), start = Offset(w * 2f / 3f, h * 0.22f), end = Offset(w * 2f / 3f, h * 0.78f), strokeWidth = max(1f, density * 0.52f))
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
