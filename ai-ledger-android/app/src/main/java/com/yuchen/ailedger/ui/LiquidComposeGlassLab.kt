package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    var potentialDepth by rememberSaveable { mutableStateOf(1.06f) }
    var refractionScale by rememberSaveable { mutableStateOf(92f) }
    var centerLens by rememberSaveable { mutableStateOf(18f) }
    var edgeFocus by rememberSaveable { mutableStateOf(1.02f) }
    var flowSmear by rememberSaveable { mutableStateOf(0.82f) }
    var lensBlend by rememberSaveable { mutableStateOf(0.76f) }
    var brightness by rememberSaveable { mutableStateOf(1.04f) }
    var darkEdge by rememberSaveable { mutableStateOf(0.62f) }

    fun resetValues() {
        potentialDepth = 1.06f
        refractionScale = 92f
        centerLens = 18f
        edgeFocus = 1.02f
        flowSmear = 0.82f
        lensBlend = 0.76f
        brightness = 1.04f
        darkEdge = 0.62f
    }

    val optics = OpenGLLiquidPotentialLabOptics(
        potentialDepth = potentialDepth,
        refractionScalePx = refractionScale,
        centerLensPx = centerLens,
        edgeFocus = edgeFocus,
        flowSmear = flowSmear,
        lensBlend = lensBlend,
        brightness = brightness,
        darkEdge = darkEdge
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

        LiquidComposeSlider("势能深度", "连续厚度场整体强度；越高整块玻璃越像厚透镜", potentialDepth, 0.20f..2.20f) { potentialDepth = it }
        LiquidComposeSlider("坡面折射", "由势能梯度产生的折射位移；决定背景扭曲强度", refractionScale, 0f..180f) { refractionScale = it }
        LiquidComposeSlider("中心透镜", "中心区域的大尺度柔和透镜，不再让中心完全静止", centerLens, 0f..72f) { centerLens = it }
        LiquidComposeSlider("边缘聚焦", "连续场靠近边缘处的斜率集中程度", edgeFocus, 0.35f..2.20f) { edgeFocus = it }
        LiquidComposeSlider("流动拖色", "沿势能坡面切线拖拽背景颜色", flowSmear, 0f..1.60f) { flowSmear = it }
        LiquidComposeSlider("透镜混合", "高斜率区域混合 lens 纹理的强度", lensBlend, 0f..1.50f) { lensBlend = it }
        LiquidComposeSlider("整体亮度", "实验样本的折射结果亮度", brightness, 0.55f..1.80f) { brightness = it }
        LiquidComposeSlider("暗部厚度", "高斜率边缘和交界处的暗角厚度", darkEdge, 0f..1.60f) { darkEdge = it }

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
