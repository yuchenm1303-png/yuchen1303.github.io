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
        GlassLabFoldout("新版 OpenGL", "单背景源：OpenGL 只折射模糊层，不再垫清晰背景", false, state) {
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
    val blurRadius = params.radius.roundToInt().coerceIn(0, 128)
    val blurLayer = LocalBlurredBackdrop.current
    val openGlBackdrop = remember(blurLayer) { blurLayer?.copy(lensImage = blurLayer.image) }
    val legacyRimStyle = style.copy(
        edgeBlurDp = 0f,
        ringWidthDp = style.ringWidthDp.coerceAtLeast(18f),
        edgePullDp = if (style.edgePullDp > -320f) -360f else style.edgePullDp,
        edgeBrightness = style.edgeBrightness.coerceAtLeast(1.70f),
        openGlPullScale = style.openGlPullScale.coerceAtLeast(110f),
        openGlDarkScale = style.openGlDarkScale.coerceAtLeast(2.20f),
        newOpenGlOuterRimReachPx = 0f,
        newOpenGlOuterRimGain = 0f,
        newOpenGlInnerWallGain = 0f,
        newOpenGlInnerWallReachPx = 0f,
        newOpenGlDarkExtract = 0f,
        newOpenGlEdgeTangentSmear = 0f
    )
    val openGlSpec = remember(state.quality, state.motionIntensity, state.backgroundTheme, params, legacyRimStyle) {
        GlassBackdropSpec(
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            params = params,
            borderStyle = legacyRimStyle
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
                    glassIntensity = state.glassIntensity * 0.92f,
                    coordinateSource = sampleCoordinates,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("新版 OpenGL 样本玻璃", color = Color.White.copy(alpha = 0.96f), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("单背景源；清晰底图已移除；旧版 rim-only 厚边保留", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("模糊", blurRadius.toFloat(), Modifier.weight(1f))
                Metric("旧边", legacyRimStyle.ringWidthDp, Modifier.weight(1f))
                Metric("拉力", legacyRimStyle.edgePullDp, Modifier.weight(1f))
            }
        }
    }

    Group("背景模糊层 Backdrop Blur Layer", "直接重建底层模糊背景；玻璃只折射这张背景", state) {
        LabSlider("背景模糊半径 dp", "写入 BackdropDebugParams.radius", params.radius, 0f..128f) { onBackdropChange(params.copy(radius = it)) }
        LabSlider("背景模糊层透明度", "只控制新版 OpenGL 折射清晰度", style.newOpenGlClarity, 0f..1.6f) { onBorderChange(style.copy(newOpenGlClarity = it)) }
        LabSlider("背景模糊层亮度", "写入 BackdropDebugParams.brightness", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
        LabSlider("OpenGL 最大透明", "新版 OpenGL 折射层 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
    }

    Group("旧版边框厚度层 Legacy Rim Overlay", "旧版厚边参数并入新版 shader；不叠完整旧 OpenGL", state) {
        LabSlider("旧边框宽度", "新版 shader 内 rim-only ringWidthDp", style.ringWidthDp, 0f..96f) { onBorderChange(style.copy(ringWidthDp = it)) }
        LabSlider("旧边框拉力", "新版 shader 内 edgePullDp", style.edgePullDp, -600f..600f) { onBorderChange(style.copy(edgePullDp = it)) }
        LabSlider("旧主体折射", "新版 shader 内 openGlPullScale", style.openGlPullScale, -300f..300f) { onBorderChange(style.copy(openGlPullScale = it)) }
        LabSlider("旧边框亮度", "新版 shader 内 edgeBrightness", style.edgeBrightness, -5f..5f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧暗边强度", "新版 shader 内 openGlDarkScale", style.openGlDarkScale, -10f..10f) { onBorderChange(style.copy(openGlDarkScale = it)) }
    }

    Group("主体折射 Body Field", "主体椭圆大范围背景拉伸扭曲", state) {
        LabSlider("主体宽度", "bodyWidth", style.newOpenGlBodyWidth, 0.18f..1.50f) { onBorderChange(style.copy(newOpenGlBodyWidth = it)) }
        LabSlider("主体陡度", "bodyCurve", style.newOpenGlBodyCurve, 0.20f..3.20f) { onBorderChange(style.copy(newOpenGlBodyCurve = it)) }
        LabSlider("主体强度", "bodyGain", style.newOpenGlBodyGain, 0f..900f) { onBorderChange(style.copy(newOpenGlBodyGain = it)) }
    }

    Group("主体折射带 Body Band", "主体侧边折射带位置、宽度和强度", state) {
        LabSlider("主体折射带位置", "bodyBandPos", style.newOpenGlBodyBandPos, 0.55f..0.98f) { onBorderChange(style.copy(newOpenGlBodyBandPos = it)) }
        LabSlider("主体折射带宽度", "bodyBandWidth", style.newOpenGlBodyBandWidth, 0.015f..0.24f) { onBorderChange(style.copy(newOpenGlBodyBandWidth = it)) }
        LabSlider("主体折射带强度", "bodyBandGain", style.newOpenGlBodyBandGain, 0f..1500f) { onBorderChange(style.copy(newOpenGlBodyBandGain = it)) }
    }

    Group("外侧压缩折射区 Outer Compressed Rim", "这套新版边缘默认关闭，保留滑块方便对比", state) {
        LabSlider("外压缩区厚度 px", "outerRimWidthPx", style.newOpenGlOuterRimWidthPx, 0.6f..14f) { onBorderChange(style.copy(newOpenGlOuterRimWidthPx = it)) }
        LabSlider("外压缩强度", "outerRimCompression", style.newOpenGlOuterRimCompression, 0.25f..3f) { onBorderChange(style.copy(newOpenGlOuterRimCompression = it)) }
        LabSlider("外压缩区向内采样 px", "outerRimReachPx", style.newOpenGlOuterRimReachPx, 0f..32f) { onBorderChange(style.copy(newOpenGlOuterRimReachPx = it)) }
        LabSlider("外压缩区显现强度", "outerRimGain", style.newOpenGlOuterRimGain, 0f..2.5f) { onBorderChange(style.copy(newOpenGlOuterRimGain = it)) }
    }

    Group("内侧渐变折射墙 Inner Transition Wall", "这套新版边缘默认关闭，保留滑块方便对比", state) {
        LabSlider("内墙起点位置 px", "innerWallOffsetPx", style.newOpenGlInnerWallOffsetPx, 1f..18f) { onBorderChange(style.copy(newOpenGlInnerWallOffsetPx = it)) }
        LabSlider("内墙渐变宽度 px", "innerWallWidthPx", style.newOpenGlInnerWallWidthPx, 2f..34f) { onBorderChange(style.copy(newOpenGlInnerWallWidthPx = it)) }
        LabSlider("内墙折射强度", "innerWallGain", style.newOpenGlInnerWallGain, 0f..420f) { onBorderChange(style.copy(newOpenGlInnerWallGain = it)) }
        LabSlider("内墙衰减速度", "innerWallFalloff", style.newOpenGlInnerWallFalloff, 0.25f..4f) { onBorderChange(style.copy(newOpenGlInnerWallFalloff = it)) }
        LabSlider("内墙向内采样 px", "innerWallReachPx", style.newOpenGlInnerWallReachPx, 0f..42f) { onBorderChange(style.copy(newOpenGlInnerWallReachPx = it)) }
        LabSlider("黑色抽取强度", "darkExtract", style.newOpenGlDarkExtract, 0f..1.6f) { onBorderChange(style.copy(newOpenGlDarkExtract = it)) }
        LabSlider("柔肩过渡宽度 px", "edgeShoulderWidthPx", style.newOpenGlEdgeShoulderWidthPx, 4f..38f) { onBorderChange(style.copy(newOpenGlEdgeShoulderWidthPx = it)) }
        LabSlider("边缘切向拖色", "edgeTangentSmear", style.newOpenGlEdgeTangentSmear, 0f..160f) { onBorderChange(style.copy(newOpenGlEdgeTangentSmear = it)) }
    }

    LabActionButton("重置新版", "恢复旧版 rim-only 厚边 + 关闭新版坏边缘", state, Modifier.fillMaxWidth()) {
        onBackdropChange(params.copy(radius = 24f, brightness = 1.00f))
        onBorderChange(
            style.copy(
                ringWidthDp = 18f,
                edgePullDp = -360f,
                edgeBrightness = 1.70f,
                openGlPullScale = 110f,
                openGlDarkScale = 2.20f,
                newOpenGlBodyWidth = 1.31f,
                newOpenGlBodyCurve = 2.23f,
                newOpenGlBodyGain = 509f,
                newOpenGlBodyBandPos = 0.77f,
                newOpenGlBodyBandWidth = 0.24f,
                newOpenGlBodyBandGain = 0f,
                newOpenGlOuterRimWidthPx = 2.2f,
                newOpenGlOuterRimCompression = 3.0f,
                newOpenGlOuterRimReachPx = 0f,
                newOpenGlOuterRimGain = 0f,
                newOpenGlInnerWallOffsetPx = 18f,
                newOpenGlInnerWallWidthPx = 2f,
                newOpenGlInnerWallGain = 0f,
                newOpenGlInnerWallFalloff = 2.38f,
                newOpenGlInnerWallReachPx = 0f,
                newOpenGlDarkExtract = 0f,
                newOpenGlEdgeShoulderWidthPx = 18f,
                newOpenGlEdgeTangentSmear = 0f,
                newOpenGlClarity = 1.00f,
                newOpenGlBrightness = 1.00f,
                edgeBlurDp = 0f
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
