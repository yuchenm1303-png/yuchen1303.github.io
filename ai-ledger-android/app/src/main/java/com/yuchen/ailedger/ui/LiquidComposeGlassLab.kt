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
    var bodyAlpha by rememberSaveable { mutableStateOf(0.86f) }
    var innerMist by rememberSaveable { mutableStateOf(1.16f) }
    var innerShadow by rememberSaveable { mutableStateOf(1.10f) }
    var chroma by rememberSaveable { mutableStateOf(1.04f) }
    var causticGlow by rememberSaveable { mutableStateOf(1.08f) }
    var textProtection by rememberSaveable { mutableStateOf(0.92f) }
    var buttonInset by rememberSaveable { mutableStateOf(0.92f) }
    var topHighlight by rememberSaveable { mutableStateOf(1.16f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.00f) }

    fun resetValues() {
        bodyAlpha = 0.86f
        innerMist = 1.16f
        innerShadow = 1.10f
        chroma = 1.04f
        causticGlow = 1.08f
        textProtection = 0.92f
        buttonInset = 0.92f
        topHighlight = 1.16f
        radiusScale = 1.00f
    }

    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.62f,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(266.dp),
        role = GlassRole.Flex,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("升级版 Compose 玻璃样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("连续场材质：雾面、暗边、焦散、色散和按钮嵌入一体流动", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("No GL", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            LiquidComposeGlassSurface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                bodyAlpha = bodyAlpha,
                innerMist = innerMist,
                innerShadow = innerShadow,
                chroma = chroma,
                causticGlow = causticGlow,
                textProtection = textProtection,
                topHighlight = topHighlight,
                radiusScale = radiusScale
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.96f), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LiquidComposeInsetPill("Compose", buttonInset, chroma, Modifier.weight(1f))
                        LiquidComposeInsetPill("Canvas", buttonInset, chroma, Modifier.weight(1f))
                        LiquidComposeInsetPill("连续", buttonInset, chroma, Modifier.weight(1f))
                    }
                    Text("目标：用连续光场先做出厚度、折光和内部流动感。", color = Color.White.copy(alpha = 0.64f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LiquidComposeSlider("主体透度", "整块玻璃的连续冷色基底", bodyAlpha, 0f..2.4f) { bodyAlpha = it }
    LiquidComposeSlider("内部雾面", "玻璃里面的奶雾与柔光体积", innerMist, 0f..2.8f) { innerMist = it }
    LiquidComposeSlider("内侧暗边", "内折边阴影和厚度压边", innerShadow, 0f..2.6f) { innerShadow = it }
    LiquidComposeSlider("色散强度", "青蓝紫边缘分离感", chroma, 0f..2.6f) { chroma = it }
    LiquidComposeSlider("焦散强度", "右下到左上的连续液态光场", causticGlow, 0f..2.8f) { causticGlow = it }
    LiquidComposeSlider("文字保护暗度", "连续暗场保护标题、标签和说明文字", textProtection, 0f..2.0f) { textProtection = it }
    LiquidComposeSlider("按钮嵌入深度", "三枚胶囊被玻璃包住的内嵌感", buttonInset, 0f..2.4f) { buttonInset = it }
    LiquidComposeSlider("掠射高光", "沿圆角走向的连续冷白反光", topHighlight, 0f..2.6f) { topHighlight = it }
    LiquidComposeSlider("圆角倍率", "样本圆角和液滴胶囊感", radiusScale, 0.55f..1.65f) { radiusScale = it }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LiquidComposeActionButton("重置液态", "恢复建议初始值", state, Modifier.weight(1f)) { resetValues() }
        LiquidComposeActionButton("隔离验证", "Flex / Chip 路线", state, Modifier.weight(1f)) { }
    }
}

@Composable
fun LiquidComposeGlassSurface(
    modifier: Modifier = Modifier,
    bodyAlpha: Float,
    innerMist: Float,
    innerShadow: Float,
    chroma: Float,
    causticGlow: Float,
    textProtection: Float,
    topHighlight: Float,
    radiusScale: Float,
    content: @Composable () -> Unit
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
                val rimWidth = max(1f, density * (1.2f + chroma * 1.30f + innerShadow * 0.45f))
                val innerInset = rimWidth * 1.85f
                val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
                val innerCorner = CornerRadius(max(1f, r - innerInset), max(1f, r - innerInset))

                val baseField = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.022f * bodyAlpha),
                        Color(0xFF6D82FF).copy(alpha = 0.064f * bodyAlpha),
                        Color(0xFF081238).copy(alpha = 0.260f + 0.034f * bodyAlpha)
                    ),
                    start = Offset(-w * 0.10f, -h * 0.18f),
                    end = Offset(w * 1.08f, h * 1.12f)
                )
                val mistField = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.106f * innerMist),
                        Color(0xFF9FC7FF).copy(alpha = 0.052f * innerMist),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.34f, h * 0.15f),
                    radius = w * 0.90f
                )
                val causticRibbon = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF6EEBFF).copy(alpha = 0.048f * causticGlow),
                        Color(0xFF7463FF).copy(alpha = 0.074f * causticGlow),
                        Color(0xFFFFA7D8).copy(alpha = 0.044f * causticGlow),
                        Color.Transparent
                    ),
                    start = Offset(-w * 0.08f, h * 1.10f),
                    end = Offset(w * 1.14f, -h * 0.12f)
                )
                val causticLens = Brush.radialGradient(
                    listOf(
                        Color(0xFFC6FAFF).copy(alpha = 0.138f * causticGlow),
                        Color(0xFF6A80FF).copy(alpha = 0.052f * causticGlow),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.82f),
                    radius = w * 0.50f
                )
                val glancingLight = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.200f * topHighlight),
                        Color.White.copy(alpha = 0.052f * topHighlight),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.030f * topHighlight)
                    ),
                    start = Offset(-w * 0.05f, -h * 0.12f),
                    end = Offset(w * 0.98f, h * 0.40f)
                )
                val prismRim = Brush.linearGradient(
                    listOf(
                        Color(0xFF78F7FF).copy(alpha = 0.120f * chroma),
                        Color.White.copy(alpha = 0.056f * chroma),
                        Color(0xFFB493FF).copy(alpha = 0.112f * chroma),
                        Color(0xFFFFA7DC).copy(alpha = 0.074f * chroma)
                    ),
                    start = Offset(-w * 0.08f, h * 1.04f),
                    end = Offset(w * 1.08f, -h * 0.08f)
                )
                val innerShadeField = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF010720).copy(alpha = 0.110f * innerShadow),
                        Color(0xFF00051A).copy(alpha = 0.070f * innerShadow),
                        Color.White.copy(alpha = 0.030f * topHighlight)
                    ),
                    start = Offset(w * 0.03f, h * 0.08f),
                    end = Offset(w * 0.96f, h * 1.04f)
                )
                val textProtectionField = Brush.verticalGradient(
                    listOf(
                        Color(0xFF020724).copy(alpha = 0.150f * textProtection),
                        Color.Transparent,
                        Color(0xFF020724).copy(alpha = 0.118f * textProtection)
                    )
                )

                onDrawWithContent {
                    drawRoundRect(brush = baseField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = mistField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = causticRibbon, size = size, cornerRadius = corner)
                    drawRoundRect(brush = causticLens, size = size, cornerRadius = corner)
                    drawRoundRect(brush = innerShadeField, size = size, cornerRadius = corner)
                    drawRoundRect(brush = textProtectionField, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(brush = glancingLight, size = size, cornerRadius = corner)
                    drawRoundRect(brush = prismRim, size = size, cornerRadius = corner, style = Stroke(width = rimWidth * 1.36f))
                    drawRoundRect(color = Color.White.copy(alpha = 0.068f * max(chroma, innerShadow)), size = size, cornerRadius = corner, style = Stroke(width = rimWidth * 0.80f))
                    drawRoundRect(color = Color(0xFF02081F).copy(alpha = 0.050f * innerShadow), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.52f)))
                    drawRoundRect(color = Color.White.copy(alpha = 0.050f * topHighlight), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.28f)))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun LiquidComposeInsetPill(text: String, insetDepth: Float, chroma: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .height(30.dp)
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val corner = CornerRadius(h / 2f, h / 2f)
                val body = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.090f + 0.028f * insetDepth),
                        Color(0xFF6E84E8).copy(alpha = 0.040f + 0.026f * insetDepth),
                        Color(0xFF060C2C).copy(alpha = 0.070f * insetDepth)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
                val insetShade = Brush.verticalGradient(
                    listOf(
                        Color(0xFF00051A).copy(alpha = 0.092f * insetDepth),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.034f * insetDepth)
                    )
                )
                val edge = Brush.linearGradient(
                    listOf(
                        Color(0xFF74F7FF).copy(alpha = 0.030f * chroma),
                        Color.White.copy(alpha = 0.038f * insetDepth),
                        Color(0xFFFFA6FF).copy(alpha = 0.026f * chroma)
                    ),
                    start = Offset(0f, h),
                    end = Offset(w, 0f)
                )
                onDrawWithContent {
                    drawRoundRect(brush = body, size = size, cornerRadius = corner)
                    drawRoundRect(brush = insetShade, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(brush = edge, size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * (0.6f + insetDepth * 0.55f))))
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
