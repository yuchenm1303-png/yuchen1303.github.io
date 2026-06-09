package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.roundToInt

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var bodyAlpha by rememberSaveable { mutableStateOf(1.10f) }
    var edgeRim by rememberSaveable { mutableStateOf(1.20f) }
    var topHighlight by rememberSaveable { mutableStateOf(1.05f) }
    var liquidGlow by rememberSaveable { mutableStateOf(0.85f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.00f) }

    fun resetValues() {
        bodyAlpha = 1.10f
        edgeRim = 1.20f
        topHighlight = 1.05f
        liquidGlow = 0.85f
        radiusScale = 1.00f
    }

    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.66f,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(246.dp),
        role = GlassRole.Flex,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("升级版 Compose 玻璃样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("厚边、内雾、液态高光、微色散，保持 Compose 隔离", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Compose", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            LiquidComposeGlassSample(
                modifier = Modifier.fillMaxWidth().weight(1f),
                bodyAlpha = bodyAlpha,
                edgeRim = edgeRim,
                topHighlight = topHighlight,
                liquidGlow = liquidGlow,
                radiusScale = radiusScale
            )
        }
    }

    LiquidComposeSlider("主体透度", "玻璃内部冷色基底透明度", bodyAlpha, 0f..2.6f) { bodyAlpha = it }
    LiquidComposeSlider("厚边强度", "外圈玻璃折边与内侧细线", edgeRim, 0f..2.8f) { edgeRim = it }
    LiquidComposeSlider("顶部高光", "上沿白边与左上反光", topHighlight, 0f..2.6f) { topHighlight = it }
    LiquidComposeSlider("流体光晕", "右上和左下液态局部亮斑", liquidGlow, 0f..2.6f) { liquidGlow = it }
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
    liquidGlow: Float,
    radiusScale: Float
) {
    val radius = (32f * radiusScale.coerceIn(0.55f, 1.65f)).dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.052f * bodyAlpha.coerceIn(0f, 2.6f)),
                        Color(0xFF6D91FF).copy(alpha = 0.082f * bodyAlpha.coerceIn(0f, 2.6f)),
                        Color(0xFF061332).copy(alpha = 0.32f)
                    )
                )
            )
            .border((1.2f + edgeRim * 1.2f).dp, Color.White.copy(alpha = 0.11f * edgeRim.coerceIn(0f, 2.8f)), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFB9F7FF).copy(alpha = 0.13f * liquidGlow.coerceIn(0f, 2.6f)),
                            Color(0xFF7B5CFF).copy(alpha = 0.05f * liquidGlow.coerceIn(0f, 2.6f)),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.06f * topHighlight.coerceIn(0f, 2.6f)))
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Liquid Compose", color = Color.White.copy(alpha = 0.96f), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiquidParamPill("Compose", Modifier.weight(1f))
                LiquidParamPill("Canvas", Modifier.weight(1f))
                LiquidParamPill("隔离", Modifier.weight(1f))
            }
            Text("目标：先让普通卡片拥有厚度、边缘折光和内部流动感。", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiquidParamPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(28.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.080f)).padding(horizontal = 10.dp),
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
