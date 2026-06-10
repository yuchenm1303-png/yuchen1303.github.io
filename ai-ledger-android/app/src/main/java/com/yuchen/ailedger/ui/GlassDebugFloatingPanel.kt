package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
            subtitle = "Shell 大玻璃 / OpenGL 折射 / 水滴采样 / 调试参数",
            initiallyExpanded = false,
            state = state
        ) {
            OpenGlGlassLab(state = state, style = border, onBorderChange = onBorderChange)
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
            title = "轻量玻璃",
            subtitle = "网页同款详细参数 / 普通 Compose 玻璃实时生效",
            initiallyExpanded = false,
            state = state
        ) {
            ComposeGlassLab(state)
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
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.70f,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        role = GlassRole.Shell,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("OpenGL Shell 样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeGlassPreviewMetric("可见", style.openGlVisibility, Modifier.weight(1f))
                ComposeGlassPreviewMetric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                ComposeGlassPreviewMetric("亮度", style.edgeBrightness, Modifier.weight(1f))
            }
        }
    }
    GlassControlGroup("可见与明暗", "整体透明、亮度与边缘暗部", state, true) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("边缘亮度", "折射结果的整体明亮度", style.edgeBrightness, 0.20f..2.40f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("边缘暗部", "边缘暗角与厚度阴影", style.openGlDarkScale, -4f..4f) { onBorderChange(style.copy(openGlDarkScale = it)) }
    }
    GlassControlGroup("折射结构", "主体拉力、边缘拉力、透镜压缩与角部厚度", state, true) {
        LabSlider("主体折射", "玻璃主体对背景的拉动强度", style.openGlPullScale, -120f..180f) { onBorderChange(style.copy(openGlPullScale = it)) }
        LabSlider("边缘拉力", "靠边区域的折射拖拽方向与力度", style.edgePullDp, -600f..220f) { onBorderChange(style.copy(edgePullDp = it)) }
        LabSlider("透镜压缩", "边缘核心与按压时的 lens 混合", style.openGlCompressionScale, -10f..10f) { onBorderChange(style.copy(openGlCompressionScale = it)) }
        LabSlider("角部厚度", "圆角厚度梯度放大系数", style.openGlCornerScale, 0f..200f) { onBorderChange(style.copy(openGlCornerScale = it)) }
    }
    GlassControlGroup("边缘与采样", "边缘宽度、额外采样半径和调试线", state, true) {
        LabSlider("边缘宽度", "rim 宽度，同时影响拖色区域", style.ringWidthDp, 0f..96f) { onBorderChange(style.copy(ringWidthDp = it)) }
        LabSlider("采样半径", "额外多点采样；0 为最快默认路径", style.openGlSampleRadiusScale, 0f..48f) { onBorderChange(style.copy(openGlSampleRadiusScale = it)) }
        LabSlider("调试线", "橙色边界线，仅调试时打开", style.openGlDebugLineAlpha, 0f..1f) { onBorderChange(style.copy(openGlDebugLineAlpha = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置 OpenGL", "恢复 Shell 默认", state, Modifier.weight(1f)) { onBorderChange(GlassBorderStyle()) }
        LabActionButton("最快路径", "关闭额外采样", state, Modifier.weight(1f)) { onBorderChange(style.copy(openGlSampleRadiusScale = 0f, openGlDebugLineAlpha = 0f)) }
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
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
private fun ComposeGlassLab(state: AssistantUiState) {
    val style = ComposeGlassLabState.style
    ComposeGlassPreview(state)
    ComposeGlassPresetGrid(state, style.preset)

    GlassControlGroup("主体材质", "雾面、暗色覆膜、中心安静、背景采样", state, true) {
        LabSlider("背景采样", "背景透出强度，越高越清透", style.backdrop, 0.32f..1.55f) { ComposeGlassLabState.update(style.copy(backdrop = it)) }
        LabSlider("雾面密度", "网页 frost；只控制普通 Compose 玻璃雾面", style.frost, 0.15f..1.90f) { ComposeGlassLabState.update(style.copy(frost = it)) }
        LabSlider("暗色覆膜", "网页 tint；控制玻璃底色压暗", style.tint, 0f..1.60f) { ComposeGlassLabState.update(style.copy(tint = it)) }
        LabSlider("中心安静", "网页 quiet；压住中心大面积发光", style.quiet, 0f..1.40f) { ComposeGlassLabState.update(style.copy(quiet = it)) }
    }

    GlassControlGroup("上下边缘光场", "顶部主高光、底部弱回光", state, true) {
        LabSlider("顶部高光", "网页 topLight；上边主高光强度", style.topLight, 0f..2.60f) { ComposeGlassLabState.update(style.copy(topLight = it)) }
        LabSlider("顶部宽度", "网页 topWidth；顶部高光带宽度 dp", style.topWidthDp, 1f..10f) { ComposeGlassLabState.update(style.copy(topWidthDp = it)) }
        LabSlider("顶部不均匀", "网页 topVariation；顶部左右强弱变化", style.topVariation, 0f..1.50f) { ComposeGlassLabState.update(style.copy(topVariation = it)) }
        LabSlider("底部回光", "网页 bottomLight；底边轻反射强度", style.bottomLight, 0f..1.80f) { ComposeGlassLabState.update(style.copy(bottomLight = it)) }
        LabSlider("底部宽度", "网页 bottomWidth；底边回光宽度 dp", style.bottomWidthDp, 1f..9f) { ComposeGlassLabState.update(style.copy(bottomWidthDp = it)) }
    }

    GlassControlGroup("厚边截面", "边缘厚度、内暗槽、外缘细亮、底部重量", state, true) {
        LabSlider("截面宽度", "网页 edgeDepth；边缘厚度宽度 dp", style.edgeDepthDp, 0f..9f) { ComposeGlassLabState.update(style.copy(edgeDepthDp = it)) }
        LabSlider("内侧暗槽", "网页 innerBevel；边缘内侧暗槽", style.innerBevel, 0f..1.80f) { ComposeGlassLabState.update(style.copy(innerBevel = it)) }
        LabSlider("外缘细亮", "网页 outerRim；最外细 rim", style.outerRim, 0f..1.80f) { ComposeGlassLabState.update(style.copy(outerRim = it)) }
        LabSlider("底部重量", "网页 bottomMass；底边压暗厚度", style.bottomMass, 0f..2.20f) { ComposeGlassLabState.update(style.copy(bottomMass = it)) }
        LabSlider("侧边折暗", "网页 sideBevel；左右边折暗", style.sideBevel, 0f..1.40f) { ComposeGlassLabState.update(style.copy(sideBevel = it)) }
    }

    GlassControlGroup("形体", "左右弱边、圆角、投影、背景光带记录", state, true) {
        LabSlider("左右弱边", "网页 sideLight；左右高光辅助", style.sideLight, 0f..0.90f) { ComposeGlassLabState.update(style.copy(sideLight = it)) }
        LabSlider("圆角半径", "网页 radius；同时派生普通控件圆角倍率", style.radius, 18f..84f) { ComposeGlassLabState.update(style.copy(radius = it)) }
        LabSlider("投影", "网页 shadow；普通玻璃投影强度", style.shadow, 0f..1.60f) { ComposeGlassLabState.update(style.copy(shadow = it)) }
        LabSlider("背景光带", "网页 ribbon；App 不直接绘制，仅用于记录网页参数", style.ribbon, 0f..1f) { ComposeGlassLabState.update(style.copy(ribbon = it)) }
    }

    GlassControlGroup("派生结果", "给旧链路兼容用的派生量", state, false) {
        SettingDerivedInfo("背景模糊", style.blurScale)
        SettingDerivedInfo("主边框", style.rimAlpha)
        SettingDerivedInfo("内部细边", style.innerRimAlpha)
        SettingDerivedInfo("顶部折光", style.topHighlight)
        SettingDerivedInfo("底部厚度", style.bottomShadow)
        SettingDerivedInfo("圆角倍率", style.radiusScale)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置当前材质", "恢复 preset 默认", state, Modifier.weight(1f)) { ComposeGlassLabState.reset() }
        LabActionButton("重置 Frost", "回到网页默认", state, Modifier.weight(1f)) { ComposeGlassLabState.resetAll() }
    }
}

@Composable
private fun ComposeGlassPresetGrid(state: AssistantUiState, selected: ComposeGlassPreset) {
    ComposeGlassPreset.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { preset ->
                ComposeGlassPresetChip(preset = preset, active = preset == selected, state = state, modifier = Modifier.weight(1f))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ComposeGlassPresetChip(preset: ComposeGlassPreset, active: Boolean, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (active) 0.94f else 0.68f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(40.dp),
        role = if (active) GlassRole.Floating else GlassRole.Chip,
        onClick = { ComposeGlassLabState.usePreset(preset) }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(composeGlassPresetLabel(preset), color = Color.White.copy(alpha = if (active) 0.94f else 0.58f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ComposeGlassPreview(state: AssistantUiState) {
    val style = ComposeGlassLabState.style
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier.fillMaxWidth().height(206.dp),
        role = GlassRole.Card,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Compose 材质玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("${composeGlassPresetLabel(style.preset)} · 详细滑块 · 普通控件实时生效", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.070f + style.outerRim.coerceIn(0f, 2f) * 0.024f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeGlassPreviewMetric("雾面", style.frost, Modifier.weight(1f))
                ComposeGlassPreviewMetric("顶部", style.topLight, Modifier.weight(1f))
                ComposeGlassPreviewMetric("底部", style.bottomLight, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeGlassPreviewMetric("外缘", style.outerRim, Modifier.weight(1f))
                ComposeGlassPreviewMetric("重量", style.bottomMass, Modifier.weight(1f))
                ComposeGlassPreviewMetric("圆角", style.radius, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ComposeGlassMiniChip("Card", state, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ComposeGlassMiniChip("Chip", state, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ComposeGlassMiniChip("Flex", state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComposeGlassPreviewMetric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun ComposeGlassMiniChip(label: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.82f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(36.dp),
        role = GlassRole.Chip,
        onClick = {}
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SettingDerivedInfo(title: String, value: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.052f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun LabStaticInfo(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.052f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.52f), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GlassControlGroup(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * 0.58f,
            motionIntensity = state.motionIntensity,
            radius = 20,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            role = GlassRole.Chip,
            onClick = { expanded = !expanded }
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起" else "展开", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
        }
    }
}

@Composable
private fun ModelCardGlassLab(state: AssistantUiState) {
    LabStaticInfo("模型卡片", "该栏暂保留原运行样式；本次只调普通 Compose 玻璃")
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("普通玻璃优先", "上方轻量玻璃实时生效", state, Modifier.weight(1f)) {}
        LabActionButton("模型卡不联动", "避免影响首页模型栏", state, Modifier.weight(1f)) {}
    }
}

@Composable
private fun LabSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = clamped, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LabActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.78f,
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

private fun composeGlassPresetLabel(preset: ComposeGlassPreset): String = when (preset) {
    ComposeGlassPreset.Clear -> "Clear"
    ComposeGlassPreset.Frost -> "Frost"
    ComposeGlassPreset.Crystal -> "Crystal"
    ComposeGlassPreset.Dense -> "Dense"
    ComposeGlassPreset.Aurora -> "Aurora"
}

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
