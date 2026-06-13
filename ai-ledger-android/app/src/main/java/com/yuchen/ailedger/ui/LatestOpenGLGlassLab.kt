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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassCardLayer

/** V25.3 网页最终玻璃的独立实验室内容。 */
@Composable
internal fun LatestOpenGLGlassLab(
    state: AssistantUiState,
    params: BackdropDebugParams,
    style: GlassBorderStyle,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    val sampleCoordinates = remember { GlassCoordinateSource() }
    val openGlBackdrop = LocalBlurredBackdrop.current
    val openGlSpec = remember(state.quality, state.motionIntensity, state.backgroundTheme, params, style) {
        GlassBackdropSpec(
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            params = params,
            borderStyle = style
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(30.dp))
            .onPlaced { sampleCoordinates.coordinates = it }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07112A).copy(alpha = 0.96f),
                        Color(0xFF08173E).copy(alpha = 0.98f),
                        Color(0xFF03091D).copy(alpha = 0.99f)
                    )
                )
            )
    ) {
        if (openGlBackdrop != null) {
            CompositionLocalProvider(
                LocalBlurredBackdrop provides openGlBackdrop,
                LocalGlassBackdrop provides openGlSpec
            ) {
                NewOpenGLGlassCardLayer(
                    radius = 30,
                    glassIntensity = style.newOpenGlGlassIntensity,
                    coordinateSource = sampleCoordinates,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("网页版 OpenGL 样本玻璃", color = Color.White.copy(alpha = 0.96f), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    "整圈统一主体折射 + 9a6e4ac 原版边缘带；网页双纹理链",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LatestMetric("模糊", params.radius, Modifier.weight(1f))
                LatestMetric("旧边", style.ringWidthDp, Modifier.weight(1f))
                LatestMetric("强度", style.newOpenGlGlassIntensity, Modifier.weight(1f))
            }
        }
    }

    LatestGroup("背景模糊层 BackdropDebugParams", "与 V25.3 网页默认参数一致") {
        LatestSlider("背景模糊半径", "radius", params.radius, 0f..4f) { onBackdropChange(params.copy(radius = it)) }
        LatestSlider("模糊迭代次数", "iterations", params.iterations, 1f..12f) { onBackdropChange(params.copy(iterations = it)) }
        LatestSlider("背景层亮度", "brightness", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
        LatestSlider("背景层对比", "contrast", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
        LatestSlider("背景层饱和", "saturation", params.saturation, 0.3f..1.8f) { onBackdropChange(params.copy(saturation = it)) }
    }

    LatestGroup("主体折射 Body Refraction", "V25.3 整圈统一主体折射参数") {
        LatestSlider("主体可见强度", "bodyVisibility", style.newOpenGlBodyVisibility, 0f..20f) { onBorderChange(style.copy(newOpenGlBodyVisibility = it)) }
        LatestSlider("主体最大透明", "bodyMaxAlpha", style.newOpenGlBodyMaxAlpha, 0f..1f) { onBorderChange(style.copy(newOpenGlBodyMaxAlpha = it)) }
        LatestSlider("主体折射亮度", "bodyOutputBrightness", style.newOpenGlBodyOutputBrightness, 0.2f..2.8f) { onBorderChange(style.copy(newOpenGlBodyOutputBrightness = it)) }
        LatestSlider("主体基础拉力", "bodyLensBasePull", style.newOpenGlBodyLensBasePull, -300f..300f) { onBorderChange(style.copy(newOpenGlBodyLensBasePull = it)) }
        LatestSlider("主体主拉力 dp", "bodyLensPullDp", style.newOpenGlBodyLensPullDp, -600f..600f) { onBorderChange(style.copy(newOpenGlBodyLensPullDp = it)) }
        LatestSlider("主体向内衰减集中度", "bodyLensConcentration", style.newOpenGlBodyLensConcentration, -10f..10f) { onBorderChange(style.copy(newOpenGlBodyLensConcentration = it)) }
        LatestSlider("主体圆角增强", "bodyLensCornerBoost", style.newOpenGlBodyLensCornerBoost, 0f..200f) { onBorderChange(style.copy(newOpenGlBodyLensCornerBoost = it)) }
        LatestSlider("主体额外折射距离", "bodyLensExtraDistance", style.newOpenGlBodyLensExtraDistance, 0f..200f) { onBorderChange(style.copy(newOpenGlBodyLensExtraDistance = it)) }
        LatestSlider("主体作用深度", "bodyLensReachDp", style.newOpenGlBodyLensReachDp, 8f..180f) { onBorderChange(style.copy(newOpenGlBodyLensReachDp = it)) }
        LatestSlider("主体暗部强度", "bodyLensDark", style.newOpenGlBodyLensDark, -10f..10f) { onBorderChange(style.copy(newOpenGlBodyLensDark = it)) }
        LatestSlider("主体调试线", "bodyLensDebug", style.newOpenGlBodyLensDebug, 0f..1f) { onBorderChange(style.copy(newOpenGlBodyLensDebug = it)) }
    }

    LatestGroup("主体低频运输 Body Low-Frequency Transport", "网页内部低频运输参数") {
        LatestSlider("样本玻璃强度", "glassIntensity", style.newOpenGlGlassIntensity, 0.35f..1.35f) { onBorderChange(style.copy(newOpenGlGlassIntensity = it)) }
        LatestSlider("内部输出亮度", "bodyBrightness", style.newOpenGlBrightness, 0.4f..2.2f) { onBorderChange(style.copy(newOpenGlBrightness = it)) }
        LatestSlider("内部运输宽度", "bodyLowFrequencyWidth", style.newOpenGlBodyWidth, 0.18f..1.5f) { onBorderChange(style.copy(newOpenGlBodyWidth = it)) }
        LatestSlider("内部运输曲率", "bodyLowFrequencyCurve", style.newOpenGlBodyCurve, 0.2f..3.2f) { onBorderChange(style.copy(newOpenGlBodyCurve = it)) }
        LatestSlider("内部运输强度", "bodyLowFrequencyGain", style.newOpenGlBodyGain, 0f..900f) { onBorderChange(style.copy(newOpenGlBodyGain = it)) }
    }

    LatestGroup("9a6e4ac 原版边缘折射带 Legacy Edge Refraction", "原版公式、采样权重和参数顺序保持不变") {
        LatestSlider("旧边缘可见强度", "legacyVisibility", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LatestSlider("旧边缘最大透明", "legacyMaxAlpha", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LatestSlider("旧边缘亮度", "legacyEdgeBrightness", style.edgeBrightness, -5f..5f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LatestSlider("旧边缘基础拉力", "legacyPullScale", style.openGlPullScale, -300f..300f) { onBorderChange(style.copy(openGlPullScale = it)) }
        LatestSlider("旧边缘拉力 dp", "legacyEdgePullDp", style.edgePullDp, -600f..600f) { onBorderChange(style.copy(edgePullDp = it)) }
        LatestSlider("旧边缘拖影强度", "legacyCompressionScale", style.openGlCompressionScale, -10f..10f) { onBorderChange(style.copy(openGlCompressionScale = it)) }
        LatestSlider("旧边缘梯度增益", "legacyCornerScale", style.openGlCornerScale, 0f..200f) { onBorderChange(style.copy(openGlCornerScale = it)) }
        LatestSlider("旧边缘柔化半径", "legacySampleRadiusScale", style.openGlSampleRadiusScale, 0f..200f) { onBorderChange(style.copy(openGlSampleRadiusScale = it)) }
        LatestSlider("旧边缘宽度", "legacyEdgeWidthDp", style.ringWidthDp, 0f..300f) { onBorderChange(style.copy(ringWidthDp = it)) }
        LatestSlider("旧边缘暗部强度", "legacyDarkScale", style.openGlDarkScale, -10f..10f) { onBorderChange(style.copy(openGlDarkScale = it)) }
        LatestSlider("旧边缘调试线", "legacyDebugLineAlpha", style.openGlDebugLineAlpha, 0f..1f) { onBorderChange(style.copy(openGlDebugLineAlpha = it)) }
    }

    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.72f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        role = GlassRole.Chip,
        onClick = {
            onBackdropChange(
                params.copy(
                    radius = 0.23041475f,
                    iterations = 12f,
                    brightness = 1.1423963f,
                    contrast = 1.0241935f,
                    saturation = 1.112f
                )
            )
            onBorderChange(style.copyLatestWebGlassDefaults())
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("重置新版", color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text("恢复 V25.3 网页最终默认参数", color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun GlassBorderStyle.copyLatestWebGlassDefaults(): GlassBorderStyle = copy(
    newOpenGlGlassIntensity = 1.35f,
    newOpenGlBodyVisibility = 20f,
    newOpenGlBodyMaxAlpha = 1f,
    newOpenGlBodyOutputBrightness = 1.8115207f,
    newOpenGlBodyLensBasePull = 300f,
    newOpenGlBodyLensPullDp = 600f,
    newOpenGlBodyLensConcentration = 10f,
    newOpenGlBodyLensCornerBoost = 0f,
    newOpenGlBodyLensExtraDistance = 200f,
    newOpenGlBodyLensReachDp = 180f,
    newOpenGlBodyLensDark = 0.23041475f,
    newOpenGlBodyLensDebug = 0f,
    newOpenGlBodyWidth = 1.250599f,
    newOpenGlBodyCurve = 0.2f,
    newOpenGlBodyGain = 12.442396f,
    newOpenGlBrightness = 0.5451613f,
    openGlVisibility = 20f,
    openGlMaxAlpha = 1f,
    edgeBrightness = 1.1520737f,
    openGlPullScale = -300f,
    edgePullDp = -600f,
    openGlCompressionScale = -10f,
    openGlCornerScale = 200f,
    openGlSampleRadiusScale = 14.285714f,
    ringWidthDp = 8.294931f,
    openGlDarkScale = -2.580645f,
    openGlDebugLineAlpha = 0f
)

@Composable
private fun LatestGroup(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun LatestSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.38f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(String.format("%.3f", value), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LatestMetric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
        Text(String.format("%.2f", value), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}
