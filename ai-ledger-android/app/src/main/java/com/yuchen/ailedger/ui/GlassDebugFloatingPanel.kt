package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    val params = state.backdropParams
    val border = state.glassBorderStyle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassLabFoldout("OpenGL", "旧 Shell 样本 / 保留原实现，不随新版替换", false, state) {
            OpenGlGlassLab(state, border, onBorderChange)
        }
        GlassLabFoldout("新版 OpenGL", "网页版玻璃：旧边缘厚度场 + 新主体折射", false, state) {
            NewOpenGlGlassLab(state, params, border, onBackdropChange, onBorderChange)
        }
        GlassLabFoldout("玻璃调试", "背景采样与全局背景参数", false, state) {
            LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
            LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
            LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
            LabSlider("背景对比", "背景明暗反差", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
            LabSlider("边缘宽度", "玻璃外缘可见宽度", border.ringWidthDp, 0f..24f) { onBorderChange(border.copy(ringWidthDp = it)) }
            LabSlider("外描边", "外侧细边透明度", border.outerStrokeAlpha, 0f..1.5f) { onBorderChange(border.copy(outerStrokeAlpha = it)) }
            LabSlider("顶部高光", "上沿高光强度", border.topHighlightAlpha, 0f..2f) { onBorderChange(border.copy(topHighlightAlpha = it)) }
            LabSlider("底部阴影", "下沿暗部压边", border.bottomShadowAlpha, 0f..1.2f) { onBorderChange(border.copy(bottomShadowAlpha = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                LabActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                LabActionButton("背景图片", "上传", state, Modifier.weight(1f), onUploadBackgroundClick)
            }
        }
        RestoredGlassLabSections(state)
    }
}

@Composable
private fun OpenGlGlassLab(state: AssistantUiState, style: GlassBorderStyle, onBorderChange: (GlassBorderStyle) -> Unit) {
    LegacyOpenGLGlassPreviewShell(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.70f,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = Modifier.fillMaxWidth().height(120.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("旧 OpenGL Shell 样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("可见", style.openGlVisibility, Modifier.weight(1f))
                Metric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                Metric("亮度", style.edgeBrightness, Modifier.weight(1f))
            }
        }
    }
    Group("旧样本参数", "只影响这一栏旧样本", state) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("旧边缘亮度", "旧 shader 的折射亮度", style.edgeBrightness, 0.20f..2.40f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧边缘宽度", "旧 shader rim 宽度", style.ringWidthDp, 0f..96f) { onBorderChange(style.copy(ringWidthDp = it)) }
    }
}

@Composable
private fun NewOpenGlGlassLab(
    state: AssistantUiState,
    params: BackdropDebugParams,
    style: GlassBorderStyle,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    val sampleCoordinates = remember { GlassCoordinateSource() }
    val blurLayer = LocalBlurredBackdrop.current
    val openGlBackdrop = remember(blurLayer) { blurLayer?.copy(lensImage = blurLayer.image) }
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
        modifier = Modifier
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
                Text("旧边缘厚度场 + 新主体折射；单背景模糊层", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("模糊", params.radius, Modifier.weight(1f))
                Metric("旧边", style.ringWidthDp, Modifier.weight(1f))
                Metric("强度", style.newOpenGlGlassIntensity, Modifier.weight(1f))
            }
        }
    }

    Group("背景模糊层 BackdropDebugParams", "与网页预览右侧参数一致", state) {
        LabSlider("背景模糊半径", "radius", params.radius, 0f..4f) { onBackdropChange(params.copy(radius = it)) }
        LabSlider("模糊迭代次数", "iterations", params.iterations, 1f..12f) { onBackdropChange(params.copy(iterations = it)) }
        LabSlider("背景层亮度", "brightness", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
        LabSlider("背景层对比", "contrast", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
        LabSlider("背景层饱和", "saturation", params.saturation, 0.3f..1.8f) { onBackdropChange(params.copy(saturation = it)) }
    }

    Group("旧版 OpenGL 边缘折射 OpenGLGlassCardLayer", "网页玻璃使用的旧边缘厚度场参数", state) {
        LabSlider("OpenGL可见强度", "openGlVisibility", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("OpenGL最大透明", "openGlMaxAlpha", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("旧边缘亮度", "edgeBrightness", style.edgeBrightness, -5f..5f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧主体/边缘拉力", "openGlPullScale", style.openGlPullScale, -300f..300f) { onBorderChange(style.copy(openGlPullScale = it)) }
        LabSlider("旧边缘拉力 dp", "edgePullDp", style.edgePullDp, -600f..600f) { onBorderChange(style.copy(edgePullDp = it)) }
        LabSlider("旧压缩强度", "openGlCompressionScale", style.openGlCompressionScale, -10f..10f) { onBorderChange(style.copy(openGlCompressionScale = it)) }
        LabSlider("旧角部强度", "openGlCornerScale", style.openGlCornerScale, 0f..200f) { onBorderChange(style.copy(openGlCornerScale = it)) }
        LabSlider("旧采样半径", "openGlSampleRadiusScale", style.openGlSampleRadiusScale, 0f..200f) { onBorderChange(style.copy(openGlSampleRadiusScale = it)) }
        LabSlider("旧边缘宽度", "ringWidthDp", style.ringWidthDp, 0f..300f) { onBorderChange(style.copy(ringWidthDp = it)) }
        LabSlider("旧暗边强度", "openGlDarkScale", style.openGlDarkScale, -10f..10f) { onBorderChange(style.copy(openGlDarkScale = it)) }
        LabSlider("旧调试线", "openGlDebugLineAlpha", style.openGlDebugLineAlpha, 0f..1f) { onBorderChange(style.copy(openGlDebugLineAlpha = it)) }
    }

    Group("新版 OpenGL 主体折射 NewOpenGLGlassCardLayer", "网页玻璃使用的新主体折射参数", state) {
        LabSlider("样本玻璃强度", "glassIntensity", style.newOpenGlGlassIntensity, 0.35f..1.35f) { onBorderChange(style.copy(newOpenGlGlassIntensity = it)) }
        LabSlider("新版输出亮度", "newOpenGlBrightness", style.newOpenGlBrightness, 0.4f..2.2f) { onBorderChange(style.copy(newOpenGlBrightness = it)) }
        LabSlider("主体宽度", "newOpenGlBodyWidth", style.newOpenGlBodyWidth, 0.18f..1.5f) { onBorderChange(style.copy(newOpenGlBodyWidth = it)) }
        LabSlider("主体陡度", "newOpenGlBodyCurve", style.newOpenGlBodyCurve, 0.20f..3.2f) { onBorderChange(style.copy(newOpenGlBodyCurve = it)) }
        LabSlider("主体强度", "newOpenGlBodyGain", style.newOpenGlBodyGain, 0f..900f) { onBorderChange(style.copy(newOpenGlBodyGain = it)) }
        LabSlider("主体折射带位置", "newOpenGlBodyBandPos", style.newOpenGlBodyBandPos, 0.55f..0.98f) { onBorderChange(style.copy(newOpenGlBodyBandPos = it)) }
        LabSlider("主体折射带宽度", "newOpenGlBodyBandWidth", style.newOpenGlBodyBandWidth, 0.015f..0.24f) { onBorderChange(style.copy(newOpenGlBodyBandWidth = it)) }
        LabSlider("主体折射带强度", "newOpenGlBodyBandGain", style.newOpenGlBodyBandGain, 0f..1500f) { onBorderChange(style.copy(newOpenGlBodyBandGain = it)) }
    }

    LabActionButton("重置新版", "恢复网页版当前默认参数", state, Modifier.fillMaxWidth()) {
        onBackdropChange(
            params.copy(
                radius = 0.691f,
                iterations = 12f,
                brightness = 1.138f,
                contrast = 1.087f,
                saturation = 1.112f
            )
        )
        onBorderChange(
            style.copy(
                openGlVisibility = 19.954f,
                openGlMaxAlpha = 1f,
                edgeBrightness = 1.083f,
                openGlPullScale = -5.53f,
                edgePullDp = -199.078f,
                openGlCompressionScale = -10f,
                openGlCornerScale = 54.378f,
                openGlSampleRadiusScale = 66.359f,
                ringWidthDp = 8.295f,
                openGlDarkScale = -2.21f,
                openGlDebugLineAlpha = 0f,
                newOpenGlGlassIntensity = 1.348f,
                newOpenGlBrightness = 0.84f,
                newOpenGlBodyWidth = 0.18f,
                newOpenGlBodyCurve = 1.569f,
                newOpenGlBodyGain = 875.115f,
                newOpenGlBodyBandPos = 0.98f,
                newOpenGlBodyBandWidth = 0.201f,
                newOpenGlBodyBandGain = 20.737f,
                edgeBlurDp = 0f,
                newOpenGlOuterRimReachPx = 0f,
                newOpenGlOuterRimGain = 0f,
                newOpenGlInnerWallGain = 0f,
                newOpenGlInnerWallReachPx = 0f,
                newOpenGlDarkExtract = 0f,
                newOpenGlEdgeTangentSmear = 0f
            )
        )
    }
}

@Composable
private fun GlassLabFoldout(title: String, subtitle: String, initiallyExpanded: Boolean, state: AssistantUiState, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(state.quality, state.glassIntensity * if (expanded) 0.94f else 0.76f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(58.dp), GlassRole.Flex, onClick = { expanded = !expanded }) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun Group(title: String, subtitle: String, state: AssistantUiState, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.045f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (expanded) "收起" else "展开", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp).clickable { expanded = !expanded })
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun LabSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.38f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LabActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 22, modifier.height(54.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Metric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier.height(42.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun Float.formatLabValue(): String {
    val scaled = (this * 100f).roundToInt() / 100f
    return if (scaled % 1f == 0f) scaled.roundToInt().toString() else scaled.toString()
}
