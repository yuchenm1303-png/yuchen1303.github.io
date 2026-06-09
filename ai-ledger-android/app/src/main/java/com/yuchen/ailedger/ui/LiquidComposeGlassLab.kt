package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
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
    var bodyAlpha by rememberSaveable { mutableStateOf(0.92f) }
    var edgeRim by rememberSaveable { mutableStateOf(1.34f) }
    var topHighlight by rememberSaveable { mutableStateOf(1.18f) }
    var causticGlow by rememberSaveable { mutableStateOf(1.06f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.00f) }

    fun resetValues() {
        bodyAlpha = 0.92f
        edgeRim = 1.34f
        topHighlight = 1.18f
        causticGlow = 1.06f
        radiusScale = 1.00f
    }

    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.66f,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(250.dp),
        role = GlassRole.Flex,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("升级版 Compose 玻璃样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("连续场材质：所有雾面、焦散、折边都跨整块玻璃渐变", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Continuous", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            LiquidComposeGlassSample(
                modifier = Modifier.fillMaxWidth().weight(1f),
                bodyAlpha = bodyAlpha,
                edgeRim = edgeRim,
                topHighlight = topHighlight,
                causticGlow = causticGlow,
                radiusScale = radiusScale
            )
        }
    }

    LiquidComposeSlider("主体透度", "只控制整块玻璃的连续基底，不再铺块状蒙层", bodyAlpha, 0f..2.4f) { bodyAlpha = it }
    LiquidComposeSlider("厚边强度", "外折边、内暗边和色散边同步增强", edgeRim, 0f..2.8f) { edgeRim = it }
    LiquidComposeSlider("掠射高光", "沿圆角走向的连续冷白反光", topHighlight, 0f..2.6f) { topHighlight = it }
    LiquidComposeSlider("焦散流光", "右下到左上的连续液态光场", causticGlow, 0f..2.6f) { causticGlow = it }
    LiquidComposeSlider("圆角倍率", "样本圆角和液滴胶囊感", radiusScale, 0.55f..1.65f) { radiusScale = it }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LiquidComposeActionButton("重置液态", "恢复建议初始值", state, Modifier.weight(1f)) { resetValues() }
        LiquidComposeActionButton("隔离验证", "Flex / Chip 路线", state, Modifier.weight(1f)) { }
    }
}

@Composable
private fun LiquidComposeGlassSample(
    modifier: Modifier,
    bodyAlpha: Float,
    edgeRim: Float,
    topHighlight: Float,
    causticGlow: Float,
    radiusScale: Float
) {
    val radius = (34f * radiusScale.coerceIn(0.55f, 1.65f)).dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val r = minOf(w, h) * 0.23f * radiusScale.coerceIn(0.55f, 1.65f)
                val corner = CornerRadius(r, r)
                val rimWidth = max(1f, density * (1.2f + edgeRim * 1.55f))
                val innerInset = rimWidth * 1.85f
                val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
                val innerCorner = CornerRadius(max(1f, r - innerInset), max(1f, r - innerInset))

                val baseField = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.030f * bodyAlpha),
                        Color(0xFF5B7BFF).copy(alpha = 0.072f * bodyAlpha),
                        Color(0xFF081138).copy(alpha = 0.285f + 0.030f * bodyAlpha)
                    ),
                    start = Offset(-w * 0.10f, -h * 0.18f),
                    end = Offset(w * 1.08f, h * 1.12f)
                )
                val continuousMist = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.108f * bodyAlpha),
                        Color(0xFF95BFFF).copy(alpha = 0.046f * bodyAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.34f, h * 0.16f),
                    radius = w * 0.88f
                )
                val longCaustic = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF63E7FF).copy(alpha = 0.050f * causticGlow),
                        Color(0xFF7764FF).copy(alpha = 0.070f * causticGlow),
                        Color(0xFFFFA6D8).copy(alpha = 0.046f * causticGlow),
                        Color.Transparent
                    ),
                    start = Offset(-w * 0.06f, h * 1.08f),
                    end = Offset(w * 1.12f, -h * 0.10f)
                )
                val cornerLens = Brush.radialGradient(
                    listOf(
                        Color(0xFFBDF8FF).copy(alpha = 0.140f * causticGlow),
                        Color(0xFF5D7CFF).copy(alpha = 0.050f * causticGlow),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.82f),
                    radius = w * 0.48f
                )
                val glancingLight = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.210f * topHighlight),
                        Color.White.copy(alpha = 0.050f * topHighlight),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.030f * topHighlight)
                    ),
                    start = Offset(-w * 0.05f, -h * 0.10f),
                    end = Offset(w * 0.96f, h * 0.40f)
                )
                val prismRim = Brush.linearGradient(
                    listOf(
                        Color(0xFF7AF7FF).copy(alpha = 0.118f * edgeRim),
                        Color.White.copy(alpha = 0.060f * edgeRim),
                        Color(0xFFB493FF).copy(alpha = 0.104f * edgeRim),
                        Color(0xFFFFA7DC).copy(alpha = 0.074f * edgeRim)
                    ),
                    start = Offset(-w * 0.08f, h * 1.04f),
                    end = Offset(w * 1.08f, -h * 0.08f)
                )
                val innerShade = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF010720).copy(alpha = 0.105f * edgeRim),
                        Color.White.copy(alpha = 0.040f * topHighlight)
                    ),
                    start = Offset(w * 0.05f, h * 0.12f),
                    end = Offset(w * 0.92f, h * 1.05f)
                )

                onDrawWithContent {
                    drawRoundRect(brush = baseField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = continuousMist, size = size, cornerRadius = corner)
                    drawRoundRect(brush = longCaustic, size = size, cornerRadius = corner)
                    drawRoundRect(brush = cornerLens, size = size, cornerRadius = corner)
                    drawRoundRect(brush = innerShade, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(brush = glancingLight, size = size, cornerRadius = corner)
                    drawRoundRect(brush = prismRim, size = size, cornerRadius = corner, style = Stroke(width = rimWidth * 1.36f))
                    drawRoundRect(color = Color.White.copy(alpha = 0.070f * edgeRim), size = size, cornerRadius = corner, style = Stroke(width = rimWidth * 0.78f))
                    drawRoundRect(color = Color.White.copy(alpha = 0.052f * topHighlight), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.34f)))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Liquid Compose", color = Color.White.copy(alpha = 0.96f), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiquidParamPill("Compose", Modifier.weight(1f))
                LiquidParamPill("Canvas", Modifier.weight(1f))
                LiquidParamPill("连续", Modifier.weight(1f))
            }
            Text("目标：用连续光场先做出厚度、折光和内部流动感。", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiquidParamPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.090f),
                        Color(0xFF8FB2FF).copy(alpha = 0.052f),
                        Color.White.copy(alpha = 0.034f)
                    )
                )
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
        glassIntensity = state.glassIntensity * 0.72f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(56.dp),
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
