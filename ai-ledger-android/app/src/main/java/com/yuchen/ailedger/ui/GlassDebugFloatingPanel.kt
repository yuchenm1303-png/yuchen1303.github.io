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

private const val GlassDebugLazyPatchCompatibility = """
title = "轻量玻璃",
            subtitle = "材质预设 / 三项核心参数 / 普通控件实时生效",
            initiallyExpanded = false,
title = "玻璃面板",
            subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
            initiallyExpanded = false,
title = "液态compose",
            subtitle = "连续 OpenGL 折射 / Compose 框架 / 液态参数",
            initiallyExpanded = false,
title = "状态预览",
        subtitle = "按住样本可看棱彩局部高光、边缘增强和释放扫光",
        state = state,
        initiallyExpanded = false
title = "基础玻璃片",
        subtitle = "中性本体：顶部折边、内侧细边、底部暗边",
        state = state,
        initiallyExpanded = false
title = "棱彩光效",
        subtitle = "不叠白边，直接把边缘与按压光改成棱彩",
        state = state,
        initiallyExpanded = false
"""

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
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
            subtitle = "材质预设 / 三项核心参数 / 普通控件实时生效",
            initiallyExpanded = false,
            state = state
        ) {
            ComposeGlassLab(state)
        }

        GlassLabFoldout(
            title = "模型卡片",
            subtitle = "首页模型栏边缘 / 高光 / 彩虹 / 圆点参数",
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
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun ComposeGlassLab(state: AssistantUiState) {
    val style = ComposeGlassLabState.style
    ComposeGlassPreview(state)
    ComposeGlassPresetGrid(state, style.preset)
    GlassControlGroup("核心材质", "只保留肉眼反馈明确的三项", state, true) {
        LabSlider("背景采样", "背景透出强度，越高越清透", style.backdrop, 0.40f..1.45f) { ComposeGlassLabState.update(style.copy(backdrop = it)) }
        LabSlider("雾面密度", "磨砂密度与背景模糊派生", style.density, 0.35f..1.75f) { ComposeGlassLabState.update(style.copy(density = it)) }
        LabSlider("边缘亮度", "主边框、内边与顶部折光", style.edge, 0.35f..1.90f) { ComposeGlassLabState.update(style.copy(edge = it)) }
    }
    GlassControlGroup("派生结果", "其余视觉由 preset 自动派生，不再单独暴露", state, false) {
        SettingDerivedInfo("背景模糊", style.blurScale)
        SettingDerivedInfo("主边框", style.rimAlpha)
        SettingDerivedInfo("内部细边", style.innerRimAlpha)
        SettingDerivedInfo("顶部折光", style.topHighlight)
        SettingDerivedInfo("底部厚度", style.bottomShadow)
        SettingDerivedInfo("圆角倍率", style.radiusScale)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置当前材质", "恢复 preset 默认", state, Modifier.weight(1f)) { ComposeGlassLabState.reset() }
        LabActionButton("重置 Frost", "回到标准磨砂", state, Modifier.weight(1f)) { ComposeGlassLabState.resetAll() }
    }
}

@Composable
private fun ComposeGlassPresetGrid(state: AssistantUiState, selected: ComposeGlassPreset) {
    ComposeGlassPreset.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { preset ->
                ComposeGlassPresetChip(
                    preset = preset,
                    active = preset == selected,
                    state = state,
                    modifier = Modifier.weight(1f)
                )
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
        modifier = Modifier.fillMaxWidth().height(182.dp),
        role = GlassRole.Card,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Compose 材质玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("${composeGlassPresetLabel(style.preset)} · 三核心参数 · 普通控件实时生效", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.075f + style.edge.coerceIn(0f, 2f) * 0.020f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeGlassPreviewMetric("采样", style.backdrop, Modifier.weight(1f))
                ComposeGlassPreviewMetric("密度", style.density, Modifier.weight(1f))
                ComposeGlassPreviewMetric("边缘", style.edge, Modifier.weight(1f))
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
    val style = ModelCardGlassLabState.style
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.72f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth().height(242.dp),
        role = GlassRole.Flex,
        onClick = {}
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("实时样本", color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("点击样本卡片可预览选中光效，拖动下方滑块会立即变化", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Clickable", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            ModelCardGlassLabPreview(state = state, modifier = Modifier.fillMaxWidth().height(188.dp))
        }
    }
    ModelCardControlGroup("主体与轮廓", "透明底、雾面、圆角、未选中能量", state, true) {
        LabSlider("主体雾面", "卡片内部基础雾面强度", style.bodyAlpha, 0f..6f) { ModelCardGlassLabState.update(style.copy(bodyAlpha = it)) }
        LabSlider("内部雾面", "玻璃内部柔雾提亮，不影响边缘线", style.innerMist, 0f..6f) { ModelCardGlassLabState.update(style.copy(innerMist = it)) }
        LabSlider("圆角倍率", "模型卡圆角半径倍率", style.radiusScale, 0.2f..3f) { ModelCardGlassLabState.update(style.copy(radiusScale = it)) }
        LabSlider("未选中强度", "非当前模型卡片整体可见度", style.unselectedEnergy, 0f..5f) { ModelCardGlassLabState.update(style.copy(unselectedEnergy = it)) }
    }
    ModelCardControlGroup("边缘结构", "外边 / 顶边 / 内边 / 暗边", state, true) {
        LabSlider("外边框", "外侧玻璃轮廓强度", style.outerRim, 0f..8f) { ModelCardGlassLabState.update(style.copy(outerRim = it)) }
        LabSlider("顶部高光", "上沿白色硬高光", style.topHairline, 0f..8f) { ModelCardGlassLabState.update(style.copy(topHairline = it)) }
        LabSlider("内侧折边", "内层玻璃细边与右下暗线", style.innerDepth, 0f..8f) { ModelCardGlassLabState.update(style.copy(innerDepth = it)) }
        LabSlider("底部暗边", "底部压暗与厚度感", style.bottomShadow, 0f..8f) { ModelCardGlassLabState.update(style.copy(bottomShadow = it)) }
    }
    ModelCardControlGroup("选中彩虹", "当前模型卡片彩虹镀膜", state, true) {
        LabSlider("彩虹边框", "选中卡主彩虹边缘", style.selectedRainbowRim, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedRainbowRim = it)) }
        LabSlider("外圈光晕", "选中卡外侧淡彩虹 Halo", style.selectedOuterHalo, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedOuterHalo = it)) }
        LabSlider("选中底光", "选中卡背后的彩虹 aura", style.selectedAura, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedAura = it)) }
    }
    ModelCardControlGroup("左上角边缘碎光", "贴边碎光，不再画内部圆弧", state, true) {
        LabSlider("碎光强度", "左上角边缘高光亮度", style.edgeGlint, 0f..10f) { ModelCardGlassLabState.update(style.copy(edgeGlint = it)) }
        LabSlider("碎光半径", "边缘碎光扩散范围", style.edgeGlintRadius, 0.05f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintRadius = it)) }
        LabSlider("碎光横向", "左上碎光中心 X 倍率", style.edgeGlintCenterX, -3f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintCenterX = it)) }
        LabSlider("碎光纵向", "左上碎光中心 Y 倍率", style.edgeGlintCenterY, -3f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintCenterY = it)) }
    }
    ModelCardControlGroup("圆点", "模型状态点光晕", state, false) {
        LabSlider("圆点光晕", "选中/未选中状态点光晕强度", style.dotGlow, 0f..6f) { ModelCardGlassLabState.update(style.copy(dotGlow = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置模型卡", "恢复默认参数", state, Modifier.weight(1f)) { ModelCardGlassLabState.reset() }
        LabActionButton("实时调试", "点击上方样本预览", state, Modifier.weight(1f)) { }
    }
}

@Composable
private fun ModelCardControlGroup(
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
