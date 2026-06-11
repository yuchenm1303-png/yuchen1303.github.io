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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    val params = state.backdropParams
    val border = state.glassBorderStyle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassLabFoldout(
            title = "OpenGL",
            subtitle = "旧 Shell 样本 / 保留原实现，不随新版替换",
            initiallyExpanded = false,
            state = state
        ) {
            OpenGlGlassLab(state = state, style = border, onBorderChange = onBorderChange)
        }

        GlassLabFoldout(
            title = "新版 OpenGL",
            subtitle = "网页新版玻璃原封装 / 样本玻璃 / 全参数滑块",
            initiallyExpanded = false,
            state = state
        ) {
            NewOpenGlGlassLab(state = state, style = border, onBorderChange = onBorderChange)
        }

        GlassLabFoldout(
            title = "玻璃调试",
            subtitle = "背景采样与全局背景参数",
            initiallyExpanded = false,
            state = state
        ) {
            LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
            LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
            LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.5f..1.8f) { onBackdropChange(params.copy(brightness = it)) }
            LabSlider("背景对比", "背景明暗反差", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                LabActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                LabActionButton("背景图片", "上传", state, Modifier.weight(1f), onUploadBackgroundClick)
            }
        }

        GlassLabFoldout(
            title = "模型卡片",
            subtitle = "首页模型栏单独材质，暂不和普通 Compose 玻璃共用参数",
            initiallyExpanded = false,
            state = state
        ) {
            ModelCardGlassLab(state)
        }

        GlassLabFoldout(
            title = "玻璃面板",
            subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
            initiallyExpanded = false,
            state = state
        ) {
            AnimatedFrostInfoGlassPreview(state, Modifier.fillMaxWidth())
            FrostInfoGlassLab(state)
        }

        GlassLabFoldout(
            title = "液态compose",
            subtitle = "连续 OpenGL 折射 / Compose 框架 / 液态参数",
            initiallyExpanded = false,
            state = state
        ) {
            LiquidComposeGlassLab(state)
        }
    }
}

@Composable
private fun OpenGlGlassLab(
    state: AssistantUiState,
    style: GlassBorderStyle,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
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
                ComposeGlassPreviewMetric("可见", style.openGlVisibility, Modifier.weight(1f))
                ComposeGlassPreviewMetric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                ComposeGlassPreviewMetric("亮度", style.edgeBrightness, Modifier.weight(1f))
            }
        }
    }
    GlassControlGroup("旧样本参数", "只影响这一栏旧样本，不作为新版路线", state, true) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("旧边缘亮度", "旧 shader 的折射亮度", style.edgeBrightness, 0.20f..2.40f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧边缘宽度", "旧 shader rim 宽度", style.ringWidthDp, 0f..96f) { onBorderChange(style.copy(ringWidthDp = it)) }
    }
}

@Composable
private fun NewOpenGlGlassLab(
    state: AssistantUiState,
    style: GlassBorderStyle,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.92f,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(160.dp),
        role = GlassRole.Shell,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("新版 OpenGL 样本玻璃", color = Color.White.copy(alpha = 0.96f), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("网页 v21 · compressed outer rim + inner transition wall", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeGlassPreviewMetric("主体", style.newOpenGlBodyGain, Modifier.weight(1f))
                ComposeGlassPreviewMetric("外缘", style.newOpenGlOuterRimGain, Modifier.weight(1f))
                ComposeGlassPreviewMetric("内墙", style.newOpenGlInnerWallGain, Modifier.weight(1f))
            }
        }
    }

    GlassControlGroup("主体折射 Body Field", "主体椭圆大范围背景拉伸扭曲", state, true) {
        LabSlider("主体宽度", "对应网页 bodyWidth", style.newOpenGlBodyWidth, 0.18f..1.50f) { onBorderChange(style.copy(newOpenGlBodyWidth = it)) }
        LabSlider("主体陡度", "对应网页 bodyCurve", style.newOpenGlBodyCurve, 0.20f..3.20f) { onBorderChange(style.copy(newOpenGlBodyCurve = it)) }
        LabSlider("主体强度", "对应网页 bodyGain", style.newOpenGlBodyGain, 0f..900f) { onBorderChange(style.copy(newOpenGlBodyGain = it)) }
    }

    GlassControlGroup("主体折射带 Body Band", "主体侧边折射带位置、宽度和强度", state, true) {
        LabSlider("主体折射带位置", "对应网页 bodyBandPos", style.newOpenGlBodyBandPos, 0.55f..0.98f) { onBorderChange(style.copy(newOpenGlBodyBandPos = it)) }
        LabSlider("主体折射带宽度", "对应网页 bodyBandWidth", style.newOpenGlBodyBandWidth, 0.015f..0.24f) { onBorderChange(style.copy(newOpenGlBodyBandWidth = it)) }
        LabSlider("主体折射带强度", "对应网页 bodyBandGain", style.newOpenGlBodyBandGain, 0f..1500f) { onBorderChange(style.copy(newOpenGlBodyBandGain = it)) }
    }

    GlassControlGroup("外侧压缩折射区 Outer Compressed Rim", "把背景压缩并抽取到外侧亮边", state, true) {
        LabSlider("外压缩区厚度 px", "对应网页 outerRimWidthPx", style.newOpenGlOuterRimWidthPx, 0.6f..14f) { onBorderChange(style.copy(newOpenGlOuterRimWidthPx = it)) }
        LabSlider("外压缩强度", "对应网页 outerRimCompression", style.newOpenGlOuterRimCompression, 0.25f..3f) { onBorderChange(style.copy(newOpenGlOuterRimCompression = it)) }
        LabSlider("外压缩区向内采样 px", "对应网页 outerRimReachPx", style.newOpenGlOuterRimReachPx, 0f..32f) { onBorderChange(style.copy(newOpenGlOuterRimReachPx = it)) }
        LabSlider("外压缩区显现强度", "对应网页 outerRimGain", style.newOpenGlOuterRimGain, 0f..2.5f) { onBorderChange(style.copy(newOpenGlOuterRimGain = it)) }
    }

    GlassControlGroup("内侧渐变折射墙 Inner Transition Wall", "边缘内侧强折射演替区", state, true) {
        LabSlider("内墙起点位置 px", "对应网页 innerWallOffsetPx", style.newOpenGlInnerWallOffsetPx, 1f..18f) { onBorderChange(style.copy(newOpenGlInnerWallOffsetPx = it)) }
        LabSlider("内墙渐变宽度 px", "对应网页 innerWallWidthPx", style.newOpenGlInnerWallWidthPx, 2f..34f) { onBorderChange(style.copy(newOpenGlInnerWallWidthPx = it)) }
        LabSlider("内墙折射强度", "对应网页 innerWallGain", style.newOpenGlInnerWallGain, 0f..420f) { onBorderChange(style.copy(newOpenGlInnerWallGain = it)) }
        LabSlider("内墙衰减速度", "对应网页 innerWallFalloff", style.newOpenGlInnerWallFalloff, 0.25f..4f) { onBorderChange(style.copy(newOpenGlInnerWallFalloff = it)) }
        LabSlider("内墙向内采样 px", "对应网页 innerWallReachPx", style.newOpenGlInnerWallReachPx, 0f..42f) { onBorderChange(style.copy(newOpenGlInnerWallReachPx = it)) }
        LabSlider("黑色抽取强度", "对应网页 darkExtract", style.newOpenGlDarkExtract, 0f..1.6f) { onBorderChange(style.copy(newOpenGlDarkExtract = it)) }
        LabSlider("柔肩过渡宽度 px", "对应网页 edgeShoulderWidthPx", style.newOpenGlEdgeShoulderWidthPx, 4f..38f) { onBorderChange(style.copy(newOpenGlEdgeShoulderWidthPx = it)) }
        LabSlider("边缘切向拖色", "对应网页 edgeTangentSmear", style.newOpenGlEdgeTangentSmear, 0f..160f) { onBorderChange(style.copy(newOpenGlEdgeTangentSmear = it)) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置新版", "恢复最终预置", state, Modifier.weight(1f)) {
            onBorderChange(style.copy(newOpenGlBodyWidth = 1.31f, newOpenGlBodyCurve = 2.23f, newOpenGlBodyGain = 509f, newOpenGlBodyBandPos = 0.77f, newOpenGlBodyBandWidth = 0.24f, newOpenGlBodyBandGain = 0f, newOpenGlOuterRimWidthPx = 2.2f, newOpenGlOuterRimCompression = 3.0f, newOpenGlOuterRimReachPx = 32f, newOpenGlOuterRimGain = 2.5f, newOpenGlInnerWallOffsetPx = 5.4f, newOpenGlInnerWallWidthPx = 34f, newOpenGlInnerWallGain = 45f, newOpenGlInnerWallFalloff = 2.38f, newOpenGlInnerWallReachPx = 0f, newOpenGlDarkExtract = 0.62f, newOpenGlEdgeShoulderWidthPx = 18f, newOpenGlEdgeTangentSmear = 42f))
        }
        LabActionButton("低负载", "保留主体，弱化边缘", state, Modifier.weight(1f)) {
            onBorderChange(style.copy(newOpenGlOuterRimGain = 0.9f, newOpenGlInnerWallGain = 20f, newOpenGlEdgeTangentSmear = 16f))
        }
    }
}

@Composable
private fun GlassLabFoldout(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean,
    state: AssistantUiState,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (expanded) 0.94f else 0.76f,
            motionIntensity = state.motionIntensity,
            radius = 24,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            role = GlassRole.Flex,
            onClick = { expanded = !expanded }
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun GlassControlGroup(title: String, subtitle: String, state: AssistantUiState, expandedByDefault: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(expandedByDefault) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.045f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
private fun ComposeGlassPreviewMetric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier.height(42.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun Float.formatLabValue(): String {
    val scaled = (this * 100f).roundToInt() / 100f
    return if (scaled % 1f == 0f) scaled.roundToInt().toString() else scaled.toString()
}
