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
import com.yuchen.ailedger.ui.gl.OpenGLLiquidPotentialLabLayer
import com.yuchen.ailedger.ui.gl.OpenGLLiquidPotentialLabOptics
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
            subtitle = "连续势能折射样本 / Shell 参数 / 仅实验室调试",
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
private fun OpenGlLiquidPotentialPreview(optics: OpenGLLiquidPotentialLabOptics) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF071225).copy(alpha = 0.36f))
    ) {
        OpenGLLiquidPotentialLabLayer(
            optics = optics,
            radiusDp = 28,
            modifier = Modifier.matchParentSize()
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("连续势能 OpenGL 样本", color = Color.White.copy(alpha = 0.95f), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("只替换实验室样本；主界面 OpenGLGlassCardLayer 不受影响", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Lab", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.070f))
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Potential", "Slope", "Lens").forEachIndexed { index, label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, color = Color.White.copy(alpha = if (index == 1) 0.88f else 0.62f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                }
            }
            Text("用一个连续厚度势能场驱动折射，让边缘、交界和中心自然衔接。", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
private fun OpenGlGlassLab(
    state: AssistantUiState,
    style: GlassBorderStyle,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    var potentialDepth by rememberSaveable { mutableStateOf(1.06f) }
    var refractionScale by rememberSaveable { mutableStateOf(92f) }
    var centerLens by rememberSaveable { mutableStateOf(18f) }
    var edgeFocus by rememberSaveable { mutableStateOf(1.02f) }
    var flowSmear by rememberSaveable { mutableStateOf(0.82f) }
    var lensBlend by rememberSaveable { mutableStateOf(0.76f) }
    var potentialBrightness by rememberSaveable { mutableStateOf(1.04f) }
    var potentialDarkEdge by rememberSaveable { mutableStateOf(0.62f) }

    fun resetPotential() {
        potentialDepth = 1.06f
        refractionScale = 92f
        centerLens = 18f
        edgeFocus = 1.02f
        flowSmear = 0.82f
        lensBlend = 0.76f
        potentialBrightness = 1.04f
        potentialDarkEdge = 0.62f
    }

    val optics = OpenGLLiquidPotentialLabOptics(
        potentialDepth = potentialDepth,
        refractionScalePx = refractionScale,
        centerLensPx = centerLens,
        edgeFocus = edgeFocus,
        flowSmear = flowSmear,
        lensBlend = lensBlend,
        brightness = potentialBrightness,
        darkEdge = potentialDarkEdge
    )

    OpenGlLiquidPotentialPreview(optics)

    GlassControlGroup("连续势能折射", "实验室新样本专用参数，不写入主界面 OpenGL", state, true) {
        LabSlider("势能深度", "连续厚度场整体强度", potentialDepth, 0.20f..2.20f) { potentialDepth = it }
        LabSlider("坡面折射", "由势能梯度产生的背景扭曲", refractionScale, 0f..180f) { refractionScale = it }
        LabSlider("中心透镜", "中心区域柔和透镜，避免中间静止", centerLens, 0f..72f) { centerLens = it }
        LabSlider("边缘聚焦", "连续场靠近边缘处的斜率集中程度", edgeFocus, 0.35f..2.20f) { edgeFocus = it }
        LabSlider("流动拖色", "沿势能坡面切线拖拽背景颜色", flowSmear, 0f..1.60f) { flowSmear = it }
        LabSlider("透镜混合", "高斜率区域混合 lens 纹理", lensBlend, 0f..1.50f) { lensBlend = it }
        LabSlider("样本亮度", "连续势能样本折射结果亮度", potentialBrightness, 0.55f..1.80f) { potentialBrightness = it }
        LabSlider("暗部厚度", "高斜率边缘和交界处暗角", potentialDarkEdge, 0f..1.60f) { potentialDarkEdge = it }
    }

    GlassControlGroup("主界面 OpenGL 参数", "保留原 Shell 玻璃参数，方便对照，不影响上方新样本", state, false) {
        LabSlider("可见强度", "主界面 OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "主界面 OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("边缘亮度", "主界面折射结果整体明亮度", style.edgeBrightness, 0.20f..2.40f) { onBorderChange(style.copy(edgeBrightness = it)) }
        LabSlider("边缘暗部", "主界面边缘暗角与厚度阴影", style.openGlDarkScale, -4f..4f) { onBorderChange(style.copy(openGlDarkScale = it)) }
        LabSlider("主体折射", "主界面玻璃主体对背景的拉动强度", style.openGlPullScale, -120f..180f) { onBorderChange(style.copy(openGlPullScale = it)) }
        LabSlider("边缘拉力", "主界面靠边区域的折射拖拽", style.edgePullDp, -600f..220f) { onBorderChange(style.copy(edgePullDp = it)) }
        LabSlider("透镜压缩", "主界面边缘核心与按压 lens 混合", style.openGlCompressionScale, -10f..10f) { onBorderChange(style.copy(openGlCompressionScale = it)) }
        LabSlider("角部厚度", "主界面圆角厚度梯度放大系数", style.openGlCornerScale, 0f..200f) { onBorderChange(style.copy(openGlCornerScale = it)) }
        LabSlider("边缘宽度", "主界面 rim 宽度", style.ringWidthDp, 0f..96f) { onBorderChange(style.copy(ringWidthDp = it)) }
        LabSlider("采样半径", "主界面额外多点采样；0 为最快默认路径", style.openGlSampleRadiusScale, 0f..48f) { onBorderChange(style.copy(openGlSampleRadiusScale = it)) }
        LabSlider("调试线", "主界面橙色边界线", style.openGlDebugLineAlpha, 0f..1f) { onBorderChange(style.copy(openGlDebugLineAlpha = it)) }
    }

    GlassControlGroup("无效参数清理", "OpenGL 无效字段已隐藏，兼容字段不暴露", state, false) {
        LabStaticInfo("已删除", "openGlEdgeWidthScale / openGlSpecularScale / openGlChromaticScale")
        LabStaticInfo("兼容保留", "GlassBorderStyle.bodyAlpha 供旧统一背景层读取")
        LabStaticInfo("不暴露", "OpenGL 调参栏不提供 bodyAlpha 滑块")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置势能场", "恢复实验室新样本", state, Modifier.weight(1f)) { resetPotential() }
        LabActionButton("重置主 OpenGL", "恢复主 Shell 默认", state, Modifier.weight(1f)) { onBorderChange(GlassBorderStyle()) }
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

    GlassControlGroup("主体材质", "背景采样与中心安静；雾面和暗色已固定", state, true) {
        LabSlider("背景采样", "背景采样透出强度", style.backdrop, 0.32f..1.55f) { ComposeGlassLabState.update(style.copy(backdrop = it)) }
        LabSlider("中心抑制", "离边缘较远区域的安静程度", style.quiet, 0f..1.40f) { ComposeGlassLabState.update(style.copy(quiet = it)) }
    }

    GlassControlGroup("统一圆角边缘场", "所有高光都沿实际圆角边缘距离层生成，不再画水平白带", state, true) {
        LabSlider("顶部入射光", "朝上的边缘与上圆角吃光强度", style.topLight, 0f..2.60f) { ComposeGlassLabState.update(style.copy(topLight = it)) }
        LabSlider("边缘场宽度", "统一 edge distance field 宽度，影响顶部与圆角延展", style.topWidthDp, 0.05f..4.50f) { ComposeGlassLabState.update(style.copy(topWidthDp = it)) }
        LabSlider("路径流动", "沿圆角路径的轻微强弱变化", style.topVariation, 0f..1.50f) { ComposeGlassLabState.update(style.copy(topVariation = it)) }
        LabSlider("底部回光", "朝下的边缘弱反射强度", style.bottomLight, 0f..1.80f) { ComposeGlassLabState.update(style.copy(bottomLight = it)) }
        LabSlider("底部场宽度", "底部回光与底部重量的边缘场宽度", style.bottomWidthDp, 0.05f..4.50f) { ComposeGlassLabState.update(style.copy(bottomWidthDp = it)) }
    }

    GlassControlGroup("边缘承接与重量", "外缘、侧边承接、底部重量都挂在同一个圆角边缘场上", state, true) {
        LabSlider("外缘强度", "最外层 edge field 强度", style.outerRim, 0f..1.80f) { ComposeGlassLabState.update(style.copy(outerRim = it)) }
        LabSlider("底部重量", "沿底边距离场衰减的暗厚度", style.bottomMass, 0f..2.20f) { ComposeGlassLabState.update(style.copy(bottomMass = it)) }
        LabSlider("侧边承接", "顶部/底部光沿侧边自然过渡的程度", style.sideLight, 0f..0.90f) { ComposeGlassLabState.update(style.copy(sideLight = it)) }
    }

    GlassControlGroup("形体", "圆角决定高光路径；背景光带仅记录网页参数", state, true) {
        LabSlider("圆角半径", "改变实际圆角路径，高光会跟随圆角变化", style.radius, 18f..84f) { ComposeGlassLabState.update(style.copy(radius = it)) }
        LabSlider("背景光带", "App 不直接绘制，仅用于记录网页参数", style.ribbon, 0f..1f) { ComposeGlassLabState.update(style.copy(ribbon = it)) }
    }

    GlassControlGroup("固定参数", "已从实验室移除，按当前默认值固定", state, false) {
        LabStaticInfo("雾面密度", ComposeGlassRuntimeDefaults.frost.formatLabValue())
        LabStaticInfo("暗色覆膜", ComposeGlassRuntimeDefaults.tint.formatLabValue())
        LabStaticInfo("截面宽度", ComposeGlassRuntimeDefaults.edgeDepthDp.formatLabValue())
        LabStaticInfo("内侧暗槽", ComposeGlassRuntimeDefaults.innerBevel.formatLabValue())
        LabStaticInfo("侧边折暗", ComposeGlassRuntimeDefaults.sideBevel.formatLabValue())
        LabStaticInfo("投影", ComposeGlassRuntimeDefaults.shadow.formatLabValue())
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
                    Text("${composeGlassPresetLabel(style.preset)} · 圆角边缘场 · 普通控件实时生效", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                ComposeGlassPreviewMetric("中心", style.quiet, Modifier.weight(1f))
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
