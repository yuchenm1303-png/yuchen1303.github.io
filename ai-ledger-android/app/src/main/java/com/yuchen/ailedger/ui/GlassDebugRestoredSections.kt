package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.roundToInt

@Composable
fun RestoredGlassLabSections(state: AssistantUiState) {
    RestoredGlassLabFoldout(
        title = "轻量玻璃",
        subtitle = "普通 Card / Chip / Floating / Nav / Flex 实时参数",
        initiallyExpanded = false,
        state = state
    ) {
        RestoredComposeGlassLab(state)
    }

    RestoredGlassLabFoldout(
        title = "模型卡片",
        subtitle = "首页模型栏边缘 / 高光 / 彩虹 / 圆点参数",
        initiallyExpanded = false,
        state = state
    ) {
        RestoredModelCardGlassLab(state)
    }

    RestoredGlassLabFoldout(
        title = "玻璃面板",
        subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
        initiallyExpanded = false,
        state = state
    ) {
        AnimatedFrostInfoGlassPreview(state, Modifier.fillMaxWidth())
        FrostInfoGlassLab(state)
    }

    RestoredGlassLabFoldout(
        title = "液态compose",
        subtitle = "连续 OpenGL 折射 / Compose 框架 / 液态参数",
        initiallyExpanded = false,
        state = state
    ) {
        LiquidComposeGlassLab(state)
    }
}

@Composable
private fun RestoredGlassLabFoldout(
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
                    Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        GlassFoldoutAnimatedContent(
            expanded = expanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun RestoredComposeGlassLab(state: AssistantUiState) {
    val style = ComposeGlassLabState.style

    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.82f,
        motionIntensity = state.motionIntensity,
        radius = 34,
        modifier = Modifier.fillMaxWidth().height(154.dp),
        role = GlassRole.Card,
        onClick = {}
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "普通 Compose 实时样本",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.94f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "当前预设：${style.preset.restoredPresetTitle()} · 父级/子级绘制共用同一参数源",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RestoredComposeMetric("背景", style.backdrop, Modifier.weight(1f))
                RestoredComposeMetric("雾化", style.backdropBlur, Modifier.weight(1f))
                RestoredComposeMetric("顶光", style.topLight, Modifier.weight(1f))
            }
            Text(
                "拖动下方参数会立即更新全 App 普通 Card / Chip / Floating / Nav / Flex。",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.54f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    RestoredComposeGlassControlGroup("预设", "快速切换通透、雾化、晶体、厚重和极光方案", state, true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RestoredLabActionButton("通透", "Clear", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Clear) }
            RestoredLabActionButton("雾化", "Frost", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Frost) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RestoredLabActionButton("晶体", "Crystal", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Crystal) }
            RestoredLabActionButton("厚重", "Dense", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Dense) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RestoredLabActionButton("极光", "Aurora", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Aurora) }
            RestoredLabActionButton("重置当前", "恢复当前预设", state, Modifier.weight(1f)) { ComposeGlassLabState.reset() }
        }
    }

    RestoredComposeGlassControlGroup("背景采样", "透明度、柔化、压暗、乳白和背景高光", state, true) {
        RestoredLabSlider("背景透明", "背景纹理进入玻璃的整体强度", style.backdrop, 0.12f..1.55f) { ComposeGlassLabState.update(style.copy(backdrop = it)) }
        RestoredLabSlider("背景柔化", "普通玻璃采样的模糊倍率", style.backdropBlur, 0.35f..2.20f) { ComposeGlassLabState.update(style.copy(backdropBlur = it)) }
        RestoredLabSlider("背景压暗", "背景采样后的暗场强度", style.backdropDim, 0f..1.80f) { ComposeGlassLabState.update(style.copy(backdropDim = it)) }
        RestoredLabSlider("背景乳白", "背景上方乳白雾层强度", style.backdropMilk, 0f..1.80f) { ComposeGlassLabState.update(style.copy(backdropMilk = it)) }
        RestoredLabSlider("背景高光", "背景采样中心提亮强度", style.backdropHighlight, 0f..1.80f) { ComposeGlassLabState.update(style.copy(backdropHighlight = it)) }
    }

    RestoredComposeGlassControlGroup("主体材质", "安静暗场、主体吸收、下部重量和内侧过渡", state, true) {
        RestoredLabSlider("安静程度", "减少主体区域的杂光并控制暗场", style.quiet, 0f..2.40f) { ComposeGlassLabState.update(style.copy(quiet = it)) }
        RestoredLabSlider("主体吸收", "玻璃主体吸收背景亮度的程度", style.bodyAbsorption, 0f..2.40f) { ComposeGlassLabState.update(style.copy(bodyAbsorption = it)) }
        RestoredLabSlider("下部重量", "玻璃下半部的厚重和沉积感", style.lowerBodyMass, 0f..2.40f) { ComposeGlassLabState.update(style.copy(lowerBodyMass = it)) }
        RestoredLabSlider("内侧过渡", "主体与边缘之间的柔和过渡", style.innerTransition, 0f..2.80f) { ComposeGlassLabState.update(style.copy(innerTransition = it)) }
    }

    RestoredComposeGlassControlGroup("边缘结构", "顶部、底部、外圈和侧边光学", state, true) {
        RestoredLabSlider("顶部高光", "上沿主高光强度", style.topLight, 0.02f..3.40f) { ComposeGlassLabState.update(style.copy(topLight = it)) }
        RestoredLabSlider("顶部宽度", "上沿高光带宽度 dp", style.topWidthDp, 0.10f..8f) { ComposeGlassLabState.update(style.copy(topWidthDp = it)) }
        RestoredLabSlider("顶部变化", "顶部路径和碎光变化程度", style.topVariation, 0f..3f) { ComposeGlassLabState.update(style.copy(topVariation = it)) }
        RestoredLabSlider("底部高光", "下沿玻璃亮边强度", style.bottomLight, 0f..3f) { ComposeGlassLabState.update(style.copy(bottomLight = it)) }
        RestoredLabSlider("底部宽度", "下沿亮边和厚度带宽度 dp", style.bottomWidthDp, 0.10f..8f) { ComposeGlassLabState.update(style.copy(bottomWidthDp = it)) }
        RestoredLabSlider("外圈轮廓", "最外侧玻璃轮廓强度", style.outerRim, 0.02f..3.20f) { ComposeGlassLabState.update(style.copy(outerRim = it)) }
        RestoredLabSlider("底部暗重", "底部暗边与厚重阴影", style.bottomMass, 0.02f..3f) { ComposeGlassLabState.update(style.copy(bottomMass = it)) }
        RestoredLabSlider("侧边光", "左右侧边的细微高光", style.sideLight, 0f..2f) { ComposeGlassLabState.update(style.copy(sideLight = it)) }
    }

    RestoredComposeGlassControlGroup("形状与光带", "全局圆角倍率来源和辅助光带", state, false) {
        RestoredLabSlider("圆角基准", "普通玻璃全局圆角基准 dp", style.radius, 18f..72f) { ComposeGlassLabState.update(style.copy(radius = it)) }
        RestoredLabSlider("辅助光带", "普通玻璃内部辅助 ribbon 强度", style.ribbon, 0f..2f) { ComposeGlassLabState.update(style.copy(ribbon = it)) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        RestoredLabActionButton("重置轻量玻璃", "恢复 Frost 默认", state, Modifier.weight(1f)) { ComposeGlassLabState.resetAll() }
        RestoredLabActionButton("当前模式", style.preset.restoredPresetTitle(), state, Modifier.weight(1f)) { }
    }
}

@Composable
private fun RestoredComposeGlassControlGroup(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable("compose-$title") { mutableStateOf(initiallyExpanded) }
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
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起" else "展开", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        GlassFoldoutAnimatedContent(
            expanded = expanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
            }
        }
    }
}

@Composable
private fun RestoredComposeMetric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.44f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
        Text(value.restoredFormatLabValue(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

private fun ComposeGlassPreset.restoredPresetTitle(): String = when (this) {
    ComposeGlassPreset.Clear -> "通透"
    ComposeGlassPreset.Frost -> "雾化"
    ComposeGlassPreset.Crystal -> "晶体"
    ComposeGlassPreset.Dense -> "厚重"
    ComposeGlassPreset.Aurora -> "极光"
}

@Composable
private fun RestoredModelCardGlassLab(state: AssistantUiState) {
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
                    Text("实时样本", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("点击样本卡片可预览选中光效，拖动下方滑块会立即变化", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Clickable", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            ModelCardGlassLabPreview(state = state, modifier = Modifier.fillMaxWidth().height(188.dp))
        }
    }

    RestoredModelCardControlGroup("主体与轮廓", "透明底、雾面、圆角、未选中能量", state, true) {
        RestoredLabSlider("主体雾面", "卡片内部基础雾面强度", style.bodyAlpha, 0f..6f) { ModelCardGlassLabState.update(style.copy(bodyAlpha = it)) }
        RestoredLabSlider("内部雾面", "玻璃内部柔雾提亮，不影响边缘线", style.innerMist, 0f..6f) { ModelCardGlassLabState.update(style.copy(innerMist = it)) }
        RestoredLabSlider("圆角倍率", "模型卡圆角半径倍率", style.radiusScale, 0.2f..3f) { ModelCardGlassLabState.update(style.copy(radiusScale = it)) }
        RestoredLabSlider("未选中强度", "非当前模型卡片整体可见度", style.unselectedEnergy, 0f..5f) { ModelCardGlassLabState.update(style.copy(unselectedEnergy = it)) }
    }
    RestoredModelCardControlGroup("边缘结构", "外边 / 顶边 / 内边 / 暗边", state, true) {
        RestoredLabSlider("外边框", "外侧玻璃轮廓强度", style.outerRim, 0f..8f) { ModelCardGlassLabState.update(style.copy(outerRim = it)) }
        RestoredLabSlider("顶部高光", "上沿白色硬高光", style.topHairline, 0f..8f) { ModelCardGlassLabState.update(style.copy(topHairline = it)) }
        RestoredLabSlider("内侧折边", "内层玻璃细边与右下暗线", style.innerDepth, 0f..8f) { ModelCardGlassLabState.update(style.copy(innerDepth = it)) }
        RestoredLabSlider("底部暗边", "底部压暗与厚度感", style.bottomShadow, 0f..8f) { ModelCardGlassLabState.update(style.copy(bottomShadow = it)) }
    }
    RestoredModelCardControlGroup("选中彩虹", "当前模型卡片彩虹镀膜", state, true) {
        RestoredLabSlider("彩虹边框", "选中卡主彩虹边缘", style.selectedRainbowRim, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedRainbowRim = it)) }
        RestoredLabSlider("外圈光晕", "选中卡外侧淡彩虹 Halo", style.selectedOuterHalo, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedOuterHalo = it)) }
        RestoredLabSlider("选中底光", "选中卡背后的彩虹 aura", style.selectedAura, 0f..8f) { ModelCardGlassLabState.update(style.copy(selectedAura = it)) }
    }
    RestoredModelCardControlGroup("左上角边缘碎光", "贴边碎光，不再画内部圆弧", state, true) {
        RestoredLabSlider("碎光强度", "左上角边缘高光亮度", style.edgeGlint, 0f..10f) { ModelCardGlassLabState.update(style.copy(edgeGlint = it)) }
        RestoredLabSlider("碎光半径", "边缘碎光扩散范围", style.edgeGlintRadius, 0.05f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintRadius = it)) }
        RestoredLabSlider("碎光横向", "左上碎光中心 X 倍率", style.edgeGlintCenterX, -3f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintCenterX = it)) }
        RestoredLabSlider("碎光纵向", "左上碎光中心 Y 倍率", style.edgeGlintCenterY, -3f..5f) { ModelCardGlassLabState.update(style.copy(edgeGlintCenterY = it)) }
    }
    RestoredModelCardControlGroup("圆点", "模型状态点光晕", state, false) {
        RestoredLabSlider("圆点光晕", "选中/未选中状态点光晕强度", style.dotGlow, 0f..6f) { ModelCardGlassLabState.update(style.copy(dotGlow = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        RestoredLabActionButton("重置模型卡", "恢复默认参数", state, Modifier.weight(1f)) { ModelCardGlassLabState.reset() }
        RestoredLabActionButton("实时调试", "点击上方样本预览", state, Modifier.weight(1f)) { }
    }
}

@Composable
private fun RestoredModelCardControlGroup(
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
                    Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起" else "展开", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        GlassFoldoutAnimatedContent(
            expanded = expanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
            }
        }
    }
}

@Composable
private fun RestoredLabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    InsetGlassParameterSlider(
        title = title,
        description = subtitle,
        value = clamped,
        valueRange = range,
        onValueChange = onValueChange,
        valueText = clamped.restoredFormatLabValue()
    )
}

@Composable
private fun RestoredLabActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
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
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.restoredFormatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
