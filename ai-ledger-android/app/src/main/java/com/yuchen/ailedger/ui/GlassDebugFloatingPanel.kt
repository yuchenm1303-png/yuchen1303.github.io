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
                        Color(0xFF344761).copy(alpha = 0.72f),
                        Color(0xFF182437).copy(alpha = 0.68f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), panelShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(buttonShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), buttonShape)
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val supportsAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val renderPath = if (supportsAgsl) "AGSL RuntimeShader" else "Canvas fallback + OpenGL card lens"
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
                DebugSlider("缓存分辨率", state.backdropParams.scale, 0.18f..0.72f) { onBackdropChange(state.backdropParams.copy(scale = it)) }
                DebugSlider("模糊半径", state.backdropParams.radius, 1f..18f) { onBackdropChange(state.backdropParams.copy(radius = it.roundToInt().toFloat())) }
                DebugSlider("模糊迭代", state.backdropParams.iterations, 1f..8f) { onBackdropChange(state.backdropParams.copy(iterations = it.roundToInt().toFloat())) }
                DebugSlider("亮度", state.backdropParams.brightness, 0.80f..1.35f) { onBackdropChange(state.backdropParams.copy(brightness = it)) }
                DebugSlider("对比度", state.backdropParams.contrast, 0.80f..1.35f) { onBackdropChange(state.backdropParams.copy(contrast = it)) }
                DebugSlider("饱和度", state.backdropParams.saturation, 0.70f..1.60f) { onBackdropChange(state.backdropParams.copy(saturation = it)) }

                Spacer(Modifier.height(6.dp))
                Text("云和月亮", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("云层强度", state.backdropParams.cloudAlpha, 0.25f..2.20f) { onBackdropChange(state.backdropParams.copy(cloudAlpha = it)) }
                DebugSlider("云层软度", state.backdropParams.cloudSoftness, 0.65f..2.40f) { onBackdropChange(state.backdropParams.copy(cloudSoftness = it)) }
                DebugSlider("云横向拉伸", state.backdropParams.cloudStretchX, 0.80f..3.80f) { onBackdropChange(state.backdropParams.copy(cloudStretchX = it)) }
                DebugSlider("云纵向厚度", state.backdropParams.cloudStretchY, 0.35f..1.40f) { onBackdropChange(state.backdropParams.copy(cloudStretchY = it)) }
                DebugSlider("云顶高光", state.backdropParams.cloudHighlightAlpha, 0f..0.80f) { onBackdropChange(state.backdropParams.copy(cloudHighlightAlpha = it)) }
                DebugSlider("月亮大小", state.backdropParams.moonScale, 0.45f..1.80f) { onBackdropChange(state.backdropParams.copy(moonScale = it)) }
                DebugSlider("月亮光晕", state.backdropParams.moonHaloAlpha, 0f..0.80f) { onBackdropChange(state.backdropParams.copy(moonHaloAlpha = it)) }
                DebugSlider("月牙高光", state.backdropParams.moonRimAlpha, 0f..1.00f) { onBackdropChange(state.backdropParams.copy(moonRimAlpha = it)) }

                Spacer(Modifier.height(6.dp))
                Text("OpenGL 折射调试", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("调试橙线", state.glassBorderStyle.openGlDebugLineAlpha, 0f..1f) { onBorderChange(state.glassBorderStyle.copy(openGlDebugLineAlpha = it)) }
                DebugSlider("折射可见度", state.glassBorderStyle.openGlVisibility, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlVisibility = it)) }
                DebugSlider("最大透明度", state.glassBorderStyle.openGlMaxAlpha, 0f..1.5f) { onBorderChange(state.glassBorderStyle.copy(openGlMaxAlpha = it)) }
                DebugSlider("边缘宽度倍率", state.glassBorderStyle.openGlEdgeWidthScale, 0.05f..5f) { onBorderChange(state.glassBorderStyle.copy(openGlEdgeWidthScale = it)) }
                DebugSlider("折射拉拽倍率", state.glassBorderStyle.openGlPullScale, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlPullScale = it)) }
                DebugSlider("压缩带倍率", state.glassBorderStyle.openGlCompressionScale, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlCompressionScale = it)) }
                DebugSlider("圆角焦散倍率", state.glassBorderStyle.openGlCornerScale, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlCornerScale = it)) }
                DebugSlider("内侧暗带倍率", state.glassBorderStyle.openGlDarkScale, 0f..6f) { onBorderChange(state.glassBorderStyle.copy(openGlDarkScale = it)) }
                DebugSlider("高光倍率", state.glassBorderStyle.openGlSpecularScale, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlSpecularScale = it)) }
                DebugSlider("色散倍率", state.glassBorderStyle.openGlChromaticScale, 0f..8f) { onBorderChange(state.glassBorderStyle.copy(openGlChromaticScale = it)) }
                DebugSlider("采样扩散倍率", state.glassBorderStyle.openGlSampleRadiusScale, 0.05f..6f) { onBorderChange(state.glassBorderStyle.copy(openGlSampleRadiusScale = it)) }

                Spacer(Modifier.height(6.dp))
                Text("iOS 透镜边缘", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("边缘厚度", state.glassBorderStyle.ringWidthDp, 0f..80f) { onBorderChange(state.glassBorderStyle.copy(ringWidthDp = it.roundToInt().toFloat())) }
                DebugSlider("内部拉取", state.glassBorderStyle.edgePullDp, 0f..300f) { onBorderChange(state.glassBorderStyle.copy(edgePullDp = it.roundToInt().toFloat())) }
                DebugSlider("折射强度", state.glassBorderStyle.edgeAlpha, 0f..5f) { onBorderChange(state.glassBorderStyle.copy(edgeAlpha = it)) }
                DebugSlider("折射模糊", state.glassBorderStyle.edgeBlurDp, 0f..80f) { onBorderChange(state.glassBorderStyle.copy(edgeBlurDp = it.roundToInt().toFloat())) }
                DebugSlider("边缘对比", state.glassBorderStyle.edgeContrast, 0.10f..5f) { onBorderChange(state.glassBorderStyle.copy(edgeContrast = it)) }
                DebugSlider("边缘饱和", state.glassBorderStyle.edgeSaturation, 0.10f..5f) { onBorderChange(state.glassBorderStyle.copy(edgeSaturation = it)) }
                DebugSlider("边缘亮度", state.glassBorderStyle.edgeBrightness, 0.10f..4f) { onBorderChange(state.glassBorderStyle.copy(edgeBrightness = it)) }
                DebugSlider("主体雾面", state.glassBorderStyle.bodyAlpha, 0.05f..0.60f) { onBorderChange(state.glassBorderStyle.copy(bodyAlpha = it)) }

                Spacer(Modifier.height(6.dp))
                Text("iOS 边框高光", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                DebugSlider("外边框", state.glassBorderStyle.outerStrokeAlpha, 0.00f..0.70f) { onBorderChange(state.glassBorderStyle.copy(outerStrokeAlpha = it)) }
                DebugSlider("内边框", state.glassBorderStyle.innerStrokeAlpha, 0.00f..0.35f) { onBorderChange(state.glassBorderStyle.copy(innerStrokeAlpha = it)) }
                DebugSlider("顶部高光", state.glassBorderStyle.topHighlightAlpha, 0.00f..0.60f) { onBorderChange(state.glassBorderStyle.copy(topHighlightAlpha = it)) }
                DebugSlider("底部暗边", state.glassBorderStyle.bottomShadowAlpha, 0f..0.50f) { onBorderChange(state.glassBorderStyle.copy(bottomShadowAlpha = it)) }
                DebugSlider("圆角 glint", state.glassBorderStyle.cornerGlintAlpha, 0f..0.30f) { onBorderChange(state.glassBorderStyle.copy(cornerGlintAlpha = it)) }
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
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
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