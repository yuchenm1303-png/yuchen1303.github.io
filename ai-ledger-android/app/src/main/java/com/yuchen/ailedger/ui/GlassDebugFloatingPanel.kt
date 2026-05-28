package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
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
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt

private const val GlassDebugLazyPatchCompatibility = """
title = "轻量玻璃",
            subtitle = "中性基底 + 棱彩边缘 + 棱彩按压扫光",
            initiallyExpanded = false,
title = "玻璃面板",
            subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
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
    val border = state.glassBorderStyle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassLabFoldout(
            title = "玻璃调试",
            subtitle = "背景 / 边缘 / 高光折射",
            initiallyExpanded = false,
            state = state
        ) {
            LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
            LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
            LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.5f..1.8f) { onBackdropChange(params.copy(brightness = it)) }
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

        GlassLabFoldout(
            title = "轻量玻璃",
            subtitle = "基础轻量玻璃说明与占位",
            initiallyExpanded = false,
            state = state
        ) {
            Text(
                "轻量玻璃仍保持 Compose/Canvas 路线，不接入 OpenGL。模型卡片的实参调试已拆到下方独立面板，避免和通用玻璃参数混在一起。",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        GlassLabFoldout(
            title = "模型卡片",
            subtitle = "首页模型栏边缘 / 高光 / 彩虹 / 圆点参数",
            initiallyExpanded = true,
            state = state
        ) {
            ModelCardGlassLab(state)
        }

        GlassLabFoldout(
            title = "玻璃面板",
            subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
            initiallyExpanded = true,
            state = state
        ) {
            AnimatedFrostInfoGlassPreview(state, Modifier.fillMaxWidth())
            FrostInfoGlassLab(state)
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
                    Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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
private fun ModelCardGlassLab(state: AssistantUiState) {
    val style = ModelCardGlassLabState.style
    ModelCardControlGroup("主体与轮廓", "透明底、圆角、未选中能量", state, true) {
        LabSlider("主体雾面", "卡片内部基础雾面强度", style.bodyAlpha, 0f..6f) { ModelCardGlassLabState.update(style.copy(bodyAlpha = it)) }
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
        LabActionButton("实时调试", "首页立即生效", state, Modifier.weight(1f)) { }
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
                    Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起" else "展开", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
                Text(title, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatLabValue(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
