package com.yuchen.ailedger.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt

@Composable
fun GlassDebugFloatingPanel(
    state: AssistantUiState,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(28.dp)
    val buttonShape = RoundedCornerShape(22.dp)
    val clickSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF243247).copy(alpha = 0.42f),
                        Color(0xFF0B1320).copy(alpha = 0.46f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), panelShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(buttonShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), buttonShape)
                .clickable(interactionSource = clickSource, indication = null) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("玻璃调试", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (expanded) "收起" else "展开", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val supportsAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val renderPath = if (supportsAgsl) "AGSL RuntimeShader" else "OpenGL ES card material"
                val renderHint = if (supportsAgsl) "当前设备支持 Android 13+ Shader 路径" else "当前设备低于 API 33，卡片折射主要靠 OpenGL 路径"

                Text("设备渲染能力", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "SDK_INT ${Build.VERSION.SDK_INT} / Android ${Build.VERSION.RELEASE} · $renderPath",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${Build.MANUFACTURER} ${Build.MODEL}｜$renderHint",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))
                Text("自定义背景", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DebugActionButton("上传背景", Modifier.weight(1f), onUploadBackgroundClick)
                    DebugActionButton("恢复内置", Modifier.weight(1f), onClearCustomBackgroundClick)
                }
                Text(
                    text = if (state.customBackgroundPath == null) "当前：内置晚霞天气背景" else "当前：自定义图片背景",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))
                Text("背景模糊缓存", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("缓存分辨率", state.backdropParams.scale, 0.04f..2.00f) { onBackdropChange(state.backdropParams.copy(scale = it)) }
                DebugSlider("模糊半径", state.backdropParams.radius, 0f..180f) { onBackdropChange(state.backdropParams.copy(radius = it.roundToInt().toFloat())) }
                DebugSlider("模糊迭代", state.backdropParams.iterations, 1f..48f) { onBackdropChange(state.backdropParams.copy(iterations = it.roundToInt().toFloat())) }
                DebugSlider("亮度", state.backdropParams.brightness, 0.00f..6.00f) { onBackdropChange(state.backdropParams.copy(brightness = it)) }
                DebugSlider("对比度", state.backdropParams.contrast, 0.00f..8.00f) { onBackdropChange(state.backdropParams.copy(contrast = it)) }
                DebugSlider("饱和度", state.backdropParams.saturation, 0.00f..8.00f) { onBackdropChange(state.backdropParams.copy(saturation = it)) }

                Spacer(Modifier.height(6.dp))
                Text("OpenGL 透明折射核心", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "现在折射范围故意放得很夸张，方便直接拉爆调参；最终预设先不改。",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                DebugSlider("调试橙线", state.glassBorderStyle.openGlDebugLineAlpha, 0f..1f) { onBorderChange(state.glassBorderStyle.copy(openGlDebugLineAlpha = it)) }
                DebugSlider("整体可见度/透明度", state.glassBorderStyle.openGlVisibility, 0f..20f) { onBorderChange(state.glassBorderStyle.copy(openGlVisibility = it)) }
                DebugSlider("主体 Alpha", state.glassBorderStyle.openGlMaxAlpha, 0f..1f) { onBorderChange(state.glassBorderStyle.copy(openGlMaxAlpha = it)) }
                DebugSlider("背景亮度", state.glassBorderStyle.edgeBrightness, -2f..6f) { onBorderChange(state.glassBorderStyle.copy(edgeBrightness = it)) }
                DebugSlider("主体折射 px", state.glassBorderStyle.openGlPullScale, -1200f..1200f) { onBorderChange(state.glassBorderStyle.copy(openGlPullScale = it)) }
                DebugSlider("边缘折射 px", state.glassBorderStyle.edgePullDp, -2400f..2400f) { onBorderChange(state.glassBorderStyle.copy(edgePullDp = it)) }
                DebugSlider("边缘宽度 px", state.glassBorderStyle.ringWidthDp, 0f..900f) { onBorderChange(state.glassBorderStyle.copy(ringWidthDp = it.roundToInt().toFloat())) }
                DebugSlider("lens 清晰混入", state.glassBorderStyle.openGlCompressionScale, -10f..10f) { onBorderChange(state.glassBorderStyle.copy(openGlCompressionScale = it)) }
                DebugSlider("梯度放大", state.glassBorderStyle.openGlCornerScale, 0f..800f) { onBorderChange(state.glassBorderStyle.copy(openGlCornerScale = it)) }
                DebugSlider("额外模糊 px", state.glassBorderStyle.openGlSampleRadiusScale, 0f..600f) { onBorderChange(state.glassBorderStyle.copy(openGlSampleRadiusScale = it)) }
                DebugSlider("内侧暗带", state.glassBorderStyle.openGlDarkScale, -12f..12f) { onBorderChange(state.glassBorderStyle.copy(openGlDarkScale = it)) }

                Spacer(Modifier.height(6.dp))
                Text("旧边框/雾面（默认全关）", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("主体雾面", state.glassBorderStyle.bodyAlpha, -5f..5f) { onBorderChange(state.glassBorderStyle.copy(bodyAlpha = it)) }
                DebugSlider("外边框", state.glassBorderStyle.outerStrokeAlpha, 0.00f..2.00f) { onBorderChange(state.glassBorderStyle.copy(outerStrokeAlpha = it)) }
                DebugSlider("内边框", state.glassBorderStyle.innerStrokeAlpha, 0.00f..2.00f) { onBorderChange(state.glassBorderStyle.copy(innerStrokeAlpha = it)) }
                DebugSlider("顶部高光", state.glassBorderStyle.topHighlightAlpha, 0.00f..2.00f) { onBorderChange(state.glassBorderStyle.copy(topHighlightAlpha = it)) }
                DebugSlider("底部暗边", state.glassBorderStyle.bottomShadowAlpha, 0f..2.00f) { onBorderChange(state.glassBorderStyle.copy(bottomShadowAlpha = it)) }
                DebugSlider("圆角 glint", state.glassBorderStyle.cornerGlintAlpha, 0f..2.00f) { onBorderChange(state.glassBorderStyle.copy(cornerGlintAlpha = it)) }
            }
        }
    }
}

@Composable
private fun DebugActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DebugSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(value.formatDebug(), color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

private fun Float.formatDebug(): String = ((this * 100f).roundToInt() / 100f).toString()