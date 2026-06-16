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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.OpenGLLiquidPotentialLabLayer
import com.yuchen.ailedger.ui.gl.OpenGLLiquidPotentialLabOptics
import kotlin.math.roundToInt

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var surfaceWidth by rememberSaveable { mutableStateOf(0.24f) }
    var surfaceSteepness by rememberSaveable { mutableStateOf(1.35f) }
    var refractionGain by rememberSaveable { mutableStateOf(150f) }
    var slopeResponse by rememberSaveable { mutableStateOf(0.62f) }
    var lensClarity by rememberSaveable { mutableStateOf(0.92f) }
    var tangentSmear by rememberSaveable { mutableStateOf(0.86f) }
    var centerLens by rememberSaveable { mutableStateOf(22f) }
    var edgeDarkness by rememberSaveable { mutableStateOf(0.62f) }
    var highlightStrength by rememberSaveable { mutableStateOf(0.58f) }
    var brightness by rememberSaveable { mutableStateOf(1.03f) }

    fun resetValues() {
        surfaceWidth = 0.24f
        surfaceSteepness = 1.35f
        refractionGain = 150f
        slopeResponse = 0.62f
        lensClarity = 0.92f
        tangentSmear = 0.86f
        centerLens = 22f
        edgeDarkness = 0.62f
        highlightStrength = 0.58f
        brightness = 1.03f
    }

    val optics = OpenGLLiquidPotentialLabOptics(
        surfaceWidth = surfaceWidth,
        surfaceSteepness = surfaceSteepness,
        refractionGainPx = refractionGain,
        slopeResponse = slopeResponse,
        lensClarity = lensClarity,
        tangentSmear = tangentSmear,
        centerLensPx = centerLens,
        edgeDarkness = edgeDarkness,
        highlightStrength = highlightStrength,
        brightness = brightness
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("连续势能 OpenGL", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("只作用实验室样本；主界面 OpenGL 玻璃不接入这版 shader", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Lab", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color(0xFF071225).copy(alpha = 0.34f))
        ) {
            OpenGLLiquidPotentialLabLayer(
                optics = optics,
                radiusDp = 34,
                modifier = Modifier.matchParentSize()
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Liquid Potential", color = Color.White.copy(alpha = 0.96f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("single continuous optical field · no hard rim/body split", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                PotentialLabSegmentedPill(listOf("Potential", "Slope", "Lens"), Modifier.fillMaxWidth().height(42.dp))
                Text("目标：用一个连续厚度势能场驱动折射，让边缘、交界和中心自然衔接。", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("曲面宽度", "从边缘向内延伸的连续坡面宽度", surfaceWidth, 0.05f..0.65f) { surfaceWidth = it }
        LiquidComposeSlider("曲面陡度", "边缘到内侧的高度衰减速度", surfaceSteepness, 0.45f..2.80f) { surfaceSteepness = it }
        LiquidComposeSlider("折射增益", "高度场法线产生的背景位移强度", refractionGain, 0f..260f) { refractionGain = it }
        LiquidComposeSlider("坡面响应", "小值会抬高中等坡度，让交界带更明显", slopeResponse, 0.30f..1.30f) { slopeResponse = it }
        LiquidComposeSlider("清晰采样", "坡面区域 lens 纹理参与比例，避免 blur 吃掉折射", lensClarity, 0f..1.60f) { lensClarity = it }
        LiquidComposeSlider("切向拖色", "沿圆角边缘方向拉开背景色块", tangentSmear, 0f..1.80f) { tangentSmear = it }
        LiquidComposeSlider("中心透镜", "中心区域低频透镜，不让中间死平", centerLens, 0f..90f) { centerLens = it }
        LiquidComposeSlider("暗边厚度", "坡面和边缘的厚玻璃暗部", edgeDarkness, 0f..1.80f) { edgeDarkness = it }
        LiquidComposeSlider("高光强度", "跟随坡面法线生成的辅助高光", highlightStrength, 0f..1.60f) { highlightStrength = it }
        LiquidComposeSlider("整体亮度", "实验样本的折射结果亮度", brightness, 0.55f..1.80f) { brightness = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置势能场", "恢复连续折射默认值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("仅实验室", "不影响首页 OpenGL", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun PotentialLabSegmentedPill(labels: List<String>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, color = Color.White.copy(alpha = if (index == 1) 0.86f else 0.62f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LiquidComposeSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    InsetGlassParameterSlider(
        title = title,
        description = subtitle,
        value = value,
        valueRange = range,
        onValueChange = onValueChange,
        valueText = value.coerceIn(range.start, range.endInclusive).formatLiquidLabValue()
    )
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
