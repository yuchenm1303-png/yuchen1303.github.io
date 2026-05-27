package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
            subtitle = "2.0 光学分层：轮廓 / 受光 / 内腔 / 彩虹 / 联动",
            initiallyExpanded = true,
            state = state
        ) {
            LightweightGlassLab(state)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun LightweightGlassLab(state: AssistantUiState) {
    var selected by rememberSaveable { mutableStateOf(true) }
    var moving by rememberSaveable { mutableStateOf(true) }
    var pressed by rememberSaveable { mutableStateOf(false) }
    var rainbowEnabled by rememberSaveable { mutableStateOf(true) }

    var radius by rememberSaveable { mutableFloatStateOf(30f) }
    var surfaceAlpha by rememberSaveable { mutableFloatStateOf(0.050f) }
    var outerContourAlpha by rememberSaveable { mutableFloatStateOf(0.62f) }
    var outerContourWidth by rememberSaveable { mutableFloatStateOf(1.25f) }
    var outerHaloAlpha by rememberSaveable { mutableFloatStateOf(0.090f) }
    var outerHaloWidth by rememberSaveable { mutableFloatStateOf(5.0f) }
    var innerContourAlpha by rememberSaveable { mutableFloatStateOf(0.18f) }
    var innerContourInset by rememberSaveable { mutableFloatStateOf(2.0f) }

    var topRimAlpha by rememberSaveable { mutableFloatStateOf(0.42f) }
    var topRimHeight by rememberSaveable { mutableFloatStateOf(9.0f) }
    var topRimFocus by rememberSaveable { mutableFloatStateOf(0.38f) }
    var sideGlanceAlpha by rememberSaveable { mutableFloatStateOf(0.20f) }
    var sideGlanceWidth by rememberSaveable { mutableFloatStateOf(4.0f) }

    var cavityMistAlpha by rememberSaveable { mutableFloatStateOf(0.030f) }
    var cavityMistHeight by rememberSaveable { mutableFloatStateOf(0.58f) }
    var bottomDepthAlpha by rememberSaveable { mutableFloatStateOf(0.050f) }
    var bottomDepthHeight by rememberSaveable { mutableFloatStateOf(18.0f) }
    var surfaceBrightAlpha by rememberSaveable { mutableFloatStateOf(0.026f) }

    var rainbowEdgeAlpha by rememberSaveable { mutableFloatStateOf(0.78f) }
    var rainbowEdgeWidth by rememberSaveable { mutableFloatStateOf(1.35f) }
    var rainbowHaloAlpha by rememberSaveable { mutableFloatStateOf(0.16f) }
    var rainbowHaloWidth by rememberSaveable { mutableFloatStateOf(7.0f) }
    var rainbowSaturation by rememberSaveable { mutableFloatStateOf(0.74f) }
    var rainbowSweepAlpha by rememberSaveable { mutableFloatStateOf(0.13f) }
    var rainbowSweepWidth by rememberSaveable { mutableFloatStateOf(0.42f) }
    var rainbowCornerGlow by rememberSaveable { mutableFloatStateOf(0.18f) }
    var rainbowBottomGlow by rememberSaveable { mutableFloatStateOf(0.10f) }

    var selectedGain by rememberSaveable { mutableFloatStateOf(0.32f) }
    var movingGain by rememberSaveable { mutableFloatStateOf(0.14f) }
    var pressGain by rememberSaveable { mutableFloatStateOf(0.22f) }
    var backLayerFade by rememberSaveable { mutableFloatStateOf(0.62f) }

    var pressScaleX by rememberSaveable { mutableFloatStateOf(0.012f) }
    var pressScaleY by rememberSaveable { mutableFloatStateOf(0.020f) }
    var pressTranslateY by rememberSaveable { mutableFloatStateOf(1.20f) }

    val selectedEnergy = if (selected) 1f else 0f
    val movingEnergy = if (moving) 1f else 0f
    val pressEnergy = if (pressed) 1f else 0f
    val rainbowEnergy = if (rainbowEnabled) 1f else 0f
    val stateEnergy = (1f + selectedEnergy * selectedGain + movingEnergy * movingGain + pressEnergy * pressGain).coerceIn(0.35f, 2.2f)
    val layerEnergy = if (selected) 1f else backLayerFade
    val rainbowStateEnergy = rainbowEnergy * stateEnergy * layerEnergy

    LightweightGlassPreview(
        radius = radius,
        surfaceAlpha = surfaceAlpha * layerEnergy,
        outerContourAlpha = outerContourAlpha * stateEnergy * layerEnergy,
        outerContourWidth = outerContourWidth,
        outerHaloAlpha = outerHaloAlpha * stateEnergy * layerEnergy,
        outerHaloWidth = outerHaloWidth,
        innerContourAlpha = innerContourAlpha * stateEnergy * layerEnergy,
        innerContourInset = innerContourInset,
        topRimAlpha = topRimAlpha * stateEnergy,
        topRimHeight = topRimHeight,
        topRimFocus = topRimFocus,
        sideGlanceAlpha = sideGlanceAlpha * stateEnergy * layerEnergy,
        sideGlanceWidth = sideGlanceWidth,
        cavityMistAlpha = cavityMistAlpha + surfaceBrightAlpha * selectedEnergy + pressEnergy * 0.012f,
        cavityMistHeight = cavityMistHeight,
        bottomDepthAlpha = bottomDepthAlpha,
        bottomDepthHeight = bottomDepthHeight,
        rainbowEnabled = rainbowEnabled,
        rainbowEdgeAlpha = rainbowEdgeAlpha * rainbowStateEnergy,
        rainbowEdgeWidth = rainbowEdgeWidth,
        rainbowHaloAlpha = rainbowHaloAlpha * rainbowStateEnergy,
        rainbowHaloWidth = rainbowHaloWidth,
        rainbowSaturation = rainbowSaturation,
        rainbowSweepAlpha = rainbowSweepAlpha * rainbowStateEnergy,
        rainbowSweepWidth = rainbowSweepWidth,
        rainbowCornerGlow = rainbowCornerGlow * rainbowStateEnergy,
        rainbowBottomGlow = rainbowBottomGlow * rainbowStateEnergy,
        pressScaleX = pressScaleX * pressEnergy,
        pressScaleY = pressScaleY * pressEnergy,
        pressTranslateY = pressTranslateY * pressEnergy,
        selected = selected,
        moving = moving,
        pressed = pressed
    )

    LightGlassControlGroup("状态预览", "切换选中、移动、按压，观察光学层联动", state, initiallyExpanded = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LabToggleButton(if (selected) "选中态" else "普通态", "边缘能量", state, Modifier.weight(1f)) { selected = !selected }
            LabToggleButton(if (moving) "移动中" else "静止态", "高光联动", state, Modifier.weight(1f)) { moving = !moving }
            LabToggleButton(if (pressed) "按压中" else "未按压", "胶囊压缩", state, Modifier.weight(1f)) { pressed = !pressed }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LabToggleButton(if (rainbowEnabled) "彩虹开启" else "彩虹关闭", "只控制假发光层", state, Modifier.weight(1f)) { rainbowEnabled = !rainbowEnabled }
        }
    }

    LightGlassControlGroup("结构轮廓", "决定玻璃厚度，不再只是单线描边", state, initiallyExpanded = true) {
        LabSlider("圆角半径", "胶囊整体圆润程度", radius, 18f..42f) { radius = it }
        LabSlider("表面透明底", "中间透明蓝白底色", surfaceAlpha, 0f..0.14f) { surfaceAlpha = it }
        LabSlider("外轮廓亮度", "最外层受光强度", outerContourAlpha, 0f..1.2f) { outerContourAlpha = it }
        LabSlider("外轮廓宽度", "外缘玻璃厚度感", outerContourWidth, 0.4f..3.2f) { outerContourWidth = it }
        LabSlider("外缘柔光", "边缘外扩空气感", outerHaloAlpha, 0f..0.28f) { outerHaloAlpha = it }
        LabSlider("外缘柔光宽度", "柔光扩散宽度", outerHaloWidth, 1f..12f) { outerHaloWidth = it }
        LabSlider("内轮廓亮度", "内侧第二层玻璃截面", innerContourAlpha, 0f..0.7f) { innerContourAlpha = it }
        LabSlider("内轮廓内缩", "内轮廓离外边距离", innerContourInset, 0f..7f) { innerContourInset = it }
    }

    LightGlassControlGroup("受光层", "顶部折边与左右擦光，负责高级受光", state, initiallyExpanded = false) {
        LabSlider("顶部折边光", "上沿最亮的一层折射光", topRimAlpha, 0f..1.0f) { topRimAlpha = it }
        LabSlider("顶部光厚度", "顶部亮带高度", topRimHeight, 2f..26f) { topRimHeight = it }
        LabSlider("顶部集中度", "高光越集中越像锋利折边", topRimFocus, 0f..1f) { topRimFocus = it }
        LabSlider("侧边擦光", "左右圆头的体积光", sideGlanceAlpha, 0f..0.8f) { sideGlanceAlpha = it }
        LabSlider("侧边光宽度", "圆头侧光宽度", sideGlanceWidth, 1f..16f) { sideGlanceWidth = it }
    }

    LightGlassControlGroup("内腔深度", "透明但不空，保留玻璃内部空气感", state, initiallyExpanded = false) {
        LabSlider("内腔雾感", "中心柔亮雾层", cavityMistAlpha, 0f..0.16f) { cavityMistAlpha = it }
        LabSlider("雾感高度", "雾层占胶囊高度比例", cavityMistHeight, 0.2f..1.0f) { cavityMistHeight = it }
        LabSlider("底部深度", "下沿暗部压边", bottomDepthAlpha, 0f..0.20f) { bottomDepthAlpha = it }
        LabSlider("底部深度高度", "暗部向上扩散高度", bottomDepthHeight, 2f..36f) { bottomDepthHeight = it }
        LabSlider("表面提亮", "选中态中心额外亮度", surfaceBrightAlpha, 0f..0.12f) { surfaceBrightAlpha = it }
    }

    LightGlassControlGroup("彩虹发光", "用渐变假发光模拟棱彩，不使用 blur 或贴图", state, initiallyExpanded = true) {
        LabSlider("彩虹边缘强度", "彩色边缘发光亮度", rainbowEdgeAlpha, 0f..1.4f) { rainbowEdgeAlpha = it }
        LabSlider("彩虹边缘宽度", "彩色边缘厚度", rainbowEdgeWidth, 0.4f..3.4f) { rainbowEdgeWidth = it }
        LabSlider("彩虹光晕强度", "外侧彩色空气光", rainbowHaloAlpha, 0f..0.42f) { rainbowHaloAlpha = it }
        LabSlider("彩虹光晕宽度", "外晕扩散宽度", rainbowHaloWidth, 1f..16f) { rainbowHaloWidth = it }
        LabSlider("彩虹饱和度", "控制彩色光的浓度", rainbowSaturation, 0f..1f) { rainbowSaturation = it }
        LabSlider("棱彩扫光", "卡片表面斜向高光", rainbowSweepAlpha, 0f..0.40f) { rainbowSweepAlpha = it }
        LabSlider("扫光宽度", "扫光在表面的扩散", rainbowSweepWidth, 0.12f..0.90f) { rainbowSweepWidth = it }
        LabSlider("角落爆光", "右上/左下彩色亮点", rainbowCornerGlow, 0f..0.42f) { rainbowCornerGlow = it }
        LabSlider("底部彩光", "下沿彩色反射", rainbowBottomGlow, 0f..0.35f) { rainbowBottomGlow = it }
    }

    LightGlassControlGroup("状态联动", "选中、移动、按压共同改变整套光学层", state, initiallyExpanded = false) {
        LabSlider("选中增益", "选中时轮廓和高光增强", selectedGain, 0f..0.9f) { selectedGain = it }
        LabSlider("移动增益", "滑动/飞行时光效增强", movingGain, 0f..0.6f) { movingGain = it }
        LabSlider("按压增益", "按下时边缘能量变化", pressGain, 0f..0.8f) { pressGain = it }
        LabSlider("后层衰减", "折叠后层卡片亮度倍率", backLayerFade, 0.25f..1.0f) { backLayerFade = it }
    }

    LightGlassControlGroup("点击胶囊动画", "只控制形变，不负责绘制重玻璃", state, initiallyExpanded = false) {
        LabSlider("横向展开", "按下时横向 scale 增量", pressScaleX, 0f..0.06f) { pressScaleX = it }
        LabSlider("纵向压缩", "按下时纵向 scale 减量", pressScaleY, 0f..0.08f) { pressScaleY = it }
        LabSlider("按压下沉", "按下时向下位移 px", pressTranslateY, 0f..6f) { pressTranslateY = it }
    }
}

@Composable
private fun LightweightGlassPreview(
    radius: Float,
    surfaceAlpha: Float,
    outerContourAlpha: Float,
    outerContourWidth: Float,
    outerHaloAlpha: Float,
    outerHaloWidth: Float,
    innerContourAlpha: Float,
    innerContourInset: Float,
    topRimAlpha: Float,
    topRimHeight: Float,
    topRimFocus: Float,
    sideGlanceAlpha: Float,
    sideGlanceWidth: Float,
    cavityMistAlpha: Float,
    cavityMistHeight: Float,
    bottomDepthAlpha: Float,
    bottomDepthHeight: Float,
    rainbowEnabled: Boolean,
    rainbowEdgeAlpha: Float,
    rainbowEdgeWidth: Float,
    rainbowHaloAlpha: Float,
    rainbowHaloWidth: Float,
    rainbowSaturation: Float,
    rainbowSweepAlpha: Float,
    rainbowSweepWidth: Float,
    rainbowCornerGlow: Float,
    rainbowBottomGlow: Float,
    pressScaleX: Float,
    pressScaleY: Float,
    pressTranslateY: Float,
    selected: Boolean,
    moving: Boolean,
    pressed: Boolean
) {
    val shape = RoundedCornerShape(radius.dp)
    val rainbowSat = rainbowSaturation.coerceIn(0f, 1f)
    val rainbowEdge = rainbowEdgeAlpha.coerceIn(0f, 1.4f)
    val rainbowHalo = rainbowHaloAlpha.coerceIn(0f, 0.55f)
    val rainbowSweep = rainbowSweepAlpha.coerceIn(0f, 0.55f)
    val rainbowCorner = rainbowCornerGlow.coerceIn(0f, 0.55f)
    val rainbowBottom = rainbowBottomGlow.coerceIn(0f, 0.45f)
    val softWhite = 1f - rainbowSat * 0.35f
    val rainbowEdgeBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF58F7FF).copy(alpha = rainbowEdge * (0.88f * rainbowSat + 0.12f)),
            Color(0xFFFF5FE7).copy(alpha = rainbowEdge * rainbowSat),
            Color(0xFFFFE96A).copy(alpha = rainbowEdge * rainbowSat),
            Color(0xFF62FF8A).copy(alpha = rainbowEdge * rainbowSat),
            Color(0xFF6CA2FF).copy(alpha = rainbowEdge * (0.82f * rainbowSat + 0.10f)),
            Color.White.copy(alpha = rainbowEdge * 0.22f * softWhite)
        )
    )
    val quietEdgeBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8DF9EA).copy(alpha = outerContourAlpha.coerceIn(0f, 1f)),
            Color.White.copy(alpha = (outerContourAlpha * 0.72f).coerceIn(0f, 1f))
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer {
                scaleX = 1f + pressScaleX
                scaleY = 1f - pressScaleY
                translationY = pressTranslateY
            }
    ) {
        if (rainbowEnabled && rainbowHalo > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding((rainbowHaloWidth * 0.20f).dp)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = rainbowHalo * 0.75f * rainbowSat),
                                Color(0xFFFF4FD8).copy(alpha = rainbowHalo * rainbowSat),
                                Color(0xFFFFD54A).copy(alpha = rainbowHalo * 0.90f * rainbowSat),
                                Color(0xFF40FF88).copy(alpha = rainbowHalo * 0.70f * rainbowSat),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding((outerHaloWidth * 0.5f + rainbowHaloWidth * 0.25f).dp)
                .clip(shape)
                .background(Color(0xFF9FC8FF).copy(alpha = outerHaloAlpha.coerceIn(0f, 0.45f)))
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding((outerHaloWidth + rainbowHaloWidth * 0.35f).dp)
                .clip(shape)
                .background(Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 0.32f)))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = cavityMistAlpha.coerceIn(0f, 0.22f)),
                                Color.White.copy(alpha = (cavityMistAlpha * cavityMistHeight).coerceIn(0f, 0.14f)),
                                Color.Transparent
                            )
                        )
                    )
            )
            if (rainbowEnabled && rainbowSweep > 0f) {
                val mid = rainbowSweepWidth.coerceIn(0.12f, 0.9f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF5BFFF4).copy(alpha = rainbowSweep * 0.35f * rainbowSat),
                                    Color.White.copy(alpha = rainbowSweep * 0.52f),
                                    Color(0xFFFF64DD).copy(alpha = rainbowSweep * 0.42f * rainbowSat),
                                    Color.Transparent
                                )
                            )
                        )
                        .graphicsLayer {
                            alpha = 0.55f + mid * 0.45f
                            rotationZ = -8f
                            scaleX = 1.0f + mid * 0.22f
                        }
                )
            }
            if (rainbowEnabled && rainbowCorner > 0f) {
                Box(
                    Modifier
                        .size(90.dp)
                        .align(Alignment.TopEnd)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = rainbowCorner * 0.34f),
                                    Color(0xFFFF5FE7).copy(alpha = rainbowCorner * 0.26f * rainbowSat),
                                    Color(0xFFFFE96A).copy(alpha = rainbowCorner * 0.20f * rainbowSat),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    Modifier
                        .size(76.dp)
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF58F7FF).copy(alpha = rainbowCorner * 0.22f * rainbowSat),
                                    Color(0xFF6CA2FF).copy(alpha = rainbowCorner * 0.14f * rainbowSat),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bottomDepthHeight.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF020618).copy(alpha = bottomDepthAlpha.coerceIn(0f, 0.30f))
                            )
                        )
                    )
            )
            if (rainbowEnabled && rainbowBottom > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = rainbowBottom * 0.50f * rainbowSat),
                                    Color(0xFFFF4FD8).copy(alpha = rainbowBottom * 0.62f * rainbowSat),
                                    Color(0xFFFFD54A).copy(alpha = rainbowBottom * 0.54f * rainbowSat),
                                    Color(0xFF40FF88).copy(alpha = rainbowBottom * 0.42f * rainbowSat)
                                )
                            )
                        )
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topRimHeight.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = topRimAlpha.coerceIn(0f, 1f)),
                                Color(0xFF8DF9EA).copy(alpha = (topRimAlpha * topRimFocus * 0.55f).coerceIn(0f, 0.60f)),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .size(sideGlanceWidth.dp, 86.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = sideGlanceAlpha.coerceIn(0f, 1f)),
                                Color(0xFF8DF9EA).copy(alpha = (sideGlanceAlpha * 0.45f).coerceIn(0f, 0.45f)),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .size(sideGlanceWidth.dp, 86.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = (sideGlanceAlpha * 0.78f).coerceIn(0f, 1f))
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = outerContourWidth.dp,
                        brush = quietEdgeBrush,
                        shape = shape
                    )
            )
            if (rainbowEnabled && rainbowEdge > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(
                            width = rainbowEdgeWidth.dp,
                            brush = rainbowEdgeBrush,
                            shape = shape
                        )
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerContourInset.dp)
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = innerContourAlpha.coerceIn(0f, 0.9f)),
                        shape = shape
                    )
            )
            Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(if (selected) 9.dp else 7.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.48f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("轻量玻璃 / Rainbow Capsule", color = Color.White.copy(alpha = 0.96f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(if (selected) "选中" else "普通", if (moving) "移动" else "静止", if (pressed) "按压" else "松手", if (rainbowEnabled) "彩虹" else "冷色").joinToString(" · "), color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LightGlassControlGroup(
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
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun LabToggleButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.72f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(50.dp),
        role = GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
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
private fun LabActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
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

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"