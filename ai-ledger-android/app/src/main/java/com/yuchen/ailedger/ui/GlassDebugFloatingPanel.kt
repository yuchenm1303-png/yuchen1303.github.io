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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
            subtitle = "2.3 连续玻璃轮廓：圆角 / 彩虹 / 单 drawWithCache",
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
    var selected by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(true) }
    var rainbowEnabled by rememberSaveable { mutableStateOf(true) }

    var radius by rememberSaveable { mutableFloatStateOf(32.4f) }
    var rainbowEdgeAlpha by rememberSaveable { mutableFloatStateOf(1.10f) }
    var rainbowEdgeWidth by rememberSaveable { mutableFloatStateOf(1.32f) }
    var rainbowHaloAlpha by rememberSaveable { mutableFloatStateOf(0.26f) }
    var rainbowHaloWidth by rememberSaveable { mutableFloatStateOf(9.6f) }
    var rainbowSaturation by rememberSaveable { mutableFloatStateOf(0.92f) }
    var rainbowSweepAlpha by rememberSaveable { mutableFloatStateOf(0.11f) }
    var rainbowSweepWidth by rememberSaveable { mutableFloatStateOf(0.28f) }
    var rainbowCornerGlow by rememberSaveable { mutableFloatStateOf(0.28f) }
    var rainbowBottomGlow by rememberSaveable { mutableFloatStateOf(0.16f) }

    val selectedEnergy = if (selected) 1.13f else 1f
    val motionGate = if (moving) 0.76f else 1f
    val edgeEnergy = selectedEnergy * if (moving) 0.94f else 1f
    val haloEnergy = selectedEnergy * motionGate
    val highCostEnergy = selectedEnergy * if (moving) 0.52f else 1f
    val rainbowEnergy = if (rainbowEnabled) 1f else 0f

    LightweightGlassPreview(
        radius = radius,
        rainbowEnabled = rainbowEnabled,
        rainbowEdgeAlpha = rainbowEdgeAlpha * rainbowEnergy * edgeEnergy,
        rainbowEdgeWidth = rainbowEdgeWidth,
        rainbowHaloAlpha = rainbowHaloAlpha * rainbowEnergy * haloEnergy,
        rainbowHaloWidth = rainbowHaloWidth,
        rainbowSaturation = rainbowSaturation,
        rainbowSweepAlpha = rainbowSweepAlpha * rainbowEnergy * highCostEnergy,
        rainbowSweepWidth = rainbowSweepWidth,
        rainbowCornerGlow = rainbowCornerGlow * rainbowEnergy * highCostEnergy,
        rainbowBottomGlow = rainbowBottomGlow * rainbowEnergy * edgeEnergy,
        selected = selected,
        moving = moving
    )

    LightGlassControlGroup("状态预览", "普通/选中、移动/静止只影响彩虹光效强弱，不再控制胶囊动画", state, initiallyExpanded = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LabToggleButton(if (selected) "选中态" else "普通态", "彩边增强", state, Modifier.weight(1f)) { selected = !selected }
            LabToggleButton(if (moving) "移动中" else "静止态", "自动降级", state, Modifier.weight(1f)) { moving = !moving }
            LabToggleButton(if (rainbowEnabled) "彩虹开启" else "彩虹关闭", "假发光层", state, Modifier.weight(1f)) { rainbowEnabled = !rainbowEnabled }
        }
        Text("绘制策略：连续基础轮廓不断线，局部彩光只做能量增强；仍然不使用 blur、shadowElevation、贴图、OpenGL 或点击形变。", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }

    LightGlassControlGroup("结构轮廓", "只保留圆角半径，其它结构层固定为 0", state, initiallyExpanded = true) {
        LabSlider("圆角半径", "胶囊整体圆润程度", radius, 18f..42f) { radius = it }
    }

    LightGlassControlGroup("彩虹发光", "连续轮廓 + 局部能量：左青蓝 / 上粉紫 / 右下黄绿 / 底部暖光", state, initiallyExpanded = true) {
        LabSlider("彩虹边缘强度", "彩色边缘发光亮度", rainbowEdgeAlpha, 0f..1.4f) { rainbowEdgeAlpha = it }
        LabSlider("彩虹边缘宽度", "彩色边缘厚度", rainbowEdgeWidth, 0.4f..3.4f) { rainbowEdgeWidth = it }
        LabSlider("彩虹光晕强度", "外侧彩色空气光", rainbowHaloAlpha, 0f..0.42f) { rainbowHaloAlpha = it }
        LabSlider("彩虹光晕宽度", "外晕扩散宽度", rainbowHaloWidth, 1f..16f) { rainbowHaloWidth = it }
        LabSlider("彩虹饱和度", "控制彩色光的浓度", rainbowSaturation, 0f..1f) { rainbowSaturation = it }
        LabSlider("棱彩扫光", "表面斜向薄膜光", rainbowSweepAlpha, 0f..0.40f) { rainbowSweepAlpha = it }
        LabSlider("扫光宽度", "扫光在表面的扩散", rainbowSweepWidth, 0.12f..0.90f) { rainbowSweepWidth = it }
        LabSlider("角落爆光", "右上/左下彩色亮点", rainbowCornerGlow, 0f..0.42f) { rainbowCornerGlow = it }
        LabSlider("底部彩光", "下沿彩色反射", rainbowBottomGlow, 0f..0.35f) { rainbowBottomGlow = it }
    }
}

@Composable
private fun LightweightGlassPreview(
    radius: Float,
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
    selected: Boolean,
    moving: Boolean
) {
    val shape = RoundedCornerShape(radius.dp)
    val rainbowSat = rainbowSaturation.coerceIn(0f, 1f)
    val rainbowEdge = if (rainbowEnabled) rainbowEdgeAlpha.coerceIn(0f, 1.4f) else 0f
    val rainbowHalo = if (rainbowEnabled) rainbowHaloAlpha.coerceIn(0f, 0.55f) else 0f
    val rainbowSweep = if (rainbowEnabled) rainbowSweepAlpha.coerceIn(0f, 0.55f) else 0f
    val rainbowCorner = if (rainbowEnabled) rainbowCornerGlow.coerceIn(0f, 0.55f) else 0f
    val rainbowBottom = if (rainbowEnabled) rainbowBottomGlow.coerceIn(0f, 0.45f) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .drawWithCache {
                fun a(value: Float): Float = value.coerceIn(0f, 1f)

                val padX = 8.dp.toPx()
                val padY = 13.dp.toPx()
                val bodyTopLeft = Offset(padX, padY)
                val bodySize = Size((size.width - padX * 2f).coerceAtLeast(1f), (size.height - padY * 2f).coerceAtLeast(1f))
                val cornerPx = radius.dp.toPx().coerceIn(1f, bodySize.height * 0.56f)
                val corner = CornerRadius(cornerPx, cornerPx)
                val left = bodyTopLeft.x
                val top = bodyTopLeft.y
                val right = left + bodySize.width
                val bottom = top + bodySize.height
                val centerY = top + bodySize.height * 0.50f
                val haloGrow = rainbowHaloWidth.dp.toPx().coerceIn(1.dp.toPx(), 28.dp.toPx())
                val edgeWidthPx = rainbowEdgeWidth.dp.toPx().coerceIn(0.5.dp.toPx(), 5.dp.toPx())
                val airyFill = a(0.014f + rainbowHalo * 0.042f + rainbowEdge * 0.012f)
                val lowerShade = a(rainbowEdge * 0.026f + rainbowBottom * 0.060f)

                val leftHaloBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF44F6FF).copy(alpha = a(rainbowHalo * 0.52f * rainbowSat)),
                        Color(0xFF6D8CFF).copy(alpha = a(rainbowHalo * 0.16f * rainbowSat)),
                        Color.Transparent
                    ),
                    center = Offset(left + bodySize.width * 0.10f, centerY),
                    radius = bodySize.width * 0.27f + haloGrow * 1.12f
                )
                val topHaloBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF56DE).copy(alpha = a(rainbowHalo * 0.36f * rainbowSat)),
                        Color(0xFFFFE584).copy(alpha = a(rainbowHalo * 0.17f * rainbowSat)),
                        Color.Transparent
                    ),
                    center = Offset(left + bodySize.width * 0.46f, top + bodySize.height * 0.04f),
                    radius = bodySize.width * 0.34f + haloGrow * 0.92f
                )
                val rightHaloBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFEA61).copy(alpha = a(rainbowHalo * 0.42f * rainbowSat)),
                        Color(0xFF55FF8D).copy(alpha = a(rainbowHalo * 0.32f * rainbowSat)),
                        Color(0xFF5AE8FF).copy(alpha = a(rainbowHalo * 0.12f * rainbowSat)),
                        Color.Transparent
                    ),
                    center = Offset(right - bodySize.width * 0.10f, bottom - bodySize.height * 0.22f),
                    radius = bodySize.width * 0.25f + haloGrow * 1.08f
                )
                val bodyBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = airyFill),
                        Color(0xFF7CEBFF).copy(alpha = airyFill * 0.30f),
                        Color(0xFF0B1B4A).copy(alpha = airyFill * 0.10f),
                        Color.Transparent
                    ),
                    startY = top,
                    endY = bottom
                )
                val lowerShadeBrush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF020617).copy(alpha = lowerShade)),
                    startY = top + bodySize.height * 0.48f,
                    endY = bottom
                )
                val outerAuraBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4EFAFF).copy(alpha = a(rainbowHalo * 0.40f * rainbowSat)),
                        Color(0xFF7D92FF).copy(alpha = a(rainbowHalo * 0.18f * rainbowSat)),
                        Color(0xFFFF5FE7).copy(alpha = a(rainbowHalo * 0.30f * rainbowSat)),
                        Color(0xFFFFEF71).copy(alpha = a(rainbowHalo * 0.28f * rainbowSat)),
                        Color(0xFF5BFF94).copy(alpha = a(rainbowHalo * 0.34f * rainbowSat)),
                        Color(0xFF62CFFF).copy(alpha = a(rainbowHalo * 0.18f * rainbowSat))
                    ),
                    start = Offset(left, top),
                    end = Offset(right, bottom)
                )
                val baseEdgeBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF48F8FF).copy(alpha = a(rainbowEdge * (0.70f * rainbowSat + 0.10f))),
                        Color(0xFF76F4FF).copy(alpha = a(rainbowEdge * 0.44f * rainbowSat)),
                        Color(0xFFFF62E6).copy(alpha = a(rainbowEdge * 0.78f * rainbowSat)),
                        Color(0xFFFFF16C).copy(alpha = a(rainbowEdge * 0.58f * rainbowSat)),
                        Color(0xFF5DFF94).copy(alpha = a(rainbowEdge * 0.76f * rainbowSat)),
                        Color(0xFF6DA2FF).copy(alpha = a(rainbowEdge * 0.48f * rainbowSat)),
                        Color(0xFF48F8FF).copy(alpha = a(rainbowEdge * 0.56f * rainbowSat))
                    ),
                    start = Offset(left, top + bodySize.height * 0.08f),
                    end = Offset(right, bottom - bodySize.height * 0.04f)
                )
                val innerEdgeBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = a(rainbowEdge * 0.13f)),
                        Color(0xFFBDFEFF).copy(alpha = a(rainbowEdge * 0.18f * rainbowSat)),
                        Color.White.copy(alpha = a(rainbowEdge * 0.08f)),
                        Color(0xFFFFD9F5).copy(alpha = a(rainbowEdge * 0.12f * rainbowSat)),
                        Color.White.copy(alpha = a(rainbowEdge * 0.10f))
                    ),
                    start = Offset(left, top),
                    end = Offset(right, bottom)
                )
                val sweepBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF6BFFF2).copy(alpha = a(rainbowSweep * 0.16f * rainbowSat)),
                        Color.White.copy(alpha = a(rainbowSweep * 0.24f)),
                        Color(0xFFFF72DD).copy(alpha = a(rainbowSweep * 0.16f * rainbowSat)),
                        Color.Transparent
                    ),
                    start = Offset(left + bodySize.width * (0.08f - rainbowSweepWidth * 0.58f), bottom),
                    end = Offset(left + bodySize.width * (0.64f + rainbowSweepWidth * 0.42f), top)
                )
                val bottomBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF36E8FF).copy(alpha = a(rainbowBottom * 0.30f * rainbowSat)),
                        Color(0xFFFF5BDA).copy(alpha = a(rainbowBottom * 0.44f * rainbowSat)),
                        Color(0xFFFFDE58).copy(alpha = a(rainbowBottom * 0.58f * rainbowSat)),
                        Color(0xFF58FF8C).copy(alpha = a(rainbowBottom * 0.42f * rainbowSat))
                    ),
                    startX = left + bodySize.width * 0.10f,
                    endX = right - bodySize.width * 0.04f
                )
                val hotCornerBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = a(rainbowCorner * 0.34f)),
                        Color(0xFFFFEC7A).copy(alpha = a(rainbowCorner * 0.30f * rainbowSat)),
                        Color(0xFFFF65E8).copy(alpha = a(rainbowCorner * 0.14f * rainbowSat)),
                        Color.Transparent
                    ),
                    center = Offset(right - bodySize.width * 0.11f, top + bodySize.height * 0.16f),
                    radius = bodySize.height * 0.88f
                )
                val coolCornerBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = a(rainbowCorner * 0.12f)),
                        Color(0xFF53F8FF).copy(alpha = a(rainbowCorner * 0.24f * rainbowSat)),
                        Color(0xFF6C98FF).copy(alpha = a(rainbowCorner * 0.14f * rainbowSat)),
                        Color.Transparent
                    ),
                    center = Offset(left + bodySize.width * 0.10f, bottom - bodySize.height * 0.18f),
                    radius = bodySize.height * 0.80f
                )

                onDrawBehind {
                    if (rainbowHalo > 0.002f) {
                        drawRoundRect(
                            brush = leftHaloBrush,
                            topLeft = Offset(left - haloGrow * 0.82f, top - haloGrow * 0.16f),
                            size = Size(bodySize.width + haloGrow * 1.10f, bodySize.height + haloGrow * 0.32f),
                            cornerRadius = corner,
                            blendMode = BlendMode.Plus
                        )
                        drawRoundRect(
                            brush = topHaloBrush,
                            topLeft = Offset(left - haloGrow * 0.12f, top - haloGrow * 0.54f),
                            size = Size(bodySize.width + haloGrow * 0.24f, bodySize.height + haloGrow * 0.76f),
                            cornerRadius = corner,
                            blendMode = BlendMode.Plus
                        )
                        drawRoundRect(
                            brush = rightHaloBrush,
                            topLeft = Offset(left - haloGrow * 0.04f, top - haloGrow * 0.06f),
                            size = Size(bodySize.width + haloGrow * 0.72f, bodySize.height + haloGrow * 0.56f),
                            cornerRadius = corner,
                            blendMode = BlendMode.Plus
                        )
                        drawRoundRect(
                            brush = outerAuraBrush,
                            topLeft = bodyTopLeft,
                            size = bodySize,
                            cornerRadius = corner,
                            style = Stroke(width = edgeWidthPx + haloGrow * 0.20f),
                            blendMode = BlendMode.Plus
                        )
                    }

                    drawRoundRect(bodyBrush, bodyTopLeft, bodySize, corner, blendMode = BlendMode.Screen)
                    drawRoundRect(lowerShadeBrush, bodyTopLeft, bodySize, corner, blendMode = BlendMode.Multiply)

                    if (rainbowSweep > 0.002f) {
                        drawRoundRect(sweepBrush, bodyTopLeft, bodySize, corner, blendMode = BlendMode.Screen)
                    }

                    if (rainbowBottom > 0.002f) {
                        drawRoundRect(
                            brush = bottomBrush,
                            topLeft = Offset(left + bodySize.width * 0.09f, bottom - 8.5.dp.toPx()),
                            size = Size(bodySize.width * 0.84f, 7.2.dp.toPx()),
                            cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx()),
                            blendMode = BlendMode.Plus
                        )
                    }

                    if (rainbowEdge > 0.002f) {
                        drawRoundRect(
                            brush = outerAuraBrush,
                            topLeft = bodyTopLeft,
                            size = bodySize,
                            cornerRadius = corner,
                            style = Stroke(width = edgeWidthPx * 3.15f),
                            blendMode = BlendMode.Plus
                        )
                        drawRoundRect(
                            brush = baseEdgeBrush,
                            topLeft = bodyTopLeft,
                            size = bodySize,
                            cornerRadius = corner,
                            style = Stroke(width = edgeWidthPx * 1.62f),
                            blendMode = BlendMode.Plus
                        )
                        drawRoundRect(
                            brush = innerEdgeBrush,
                            topLeft = Offset(left + 1.0.dp.toPx(), top + 1.0.dp.toPx()),
                            size = Size(bodySize.width - 2.0.dp.toPx(), bodySize.height - 2.0.dp.toPx()),
                            cornerRadius = corner,
                            style = Stroke(width = 0.78.dp.toPx()),
                            blendMode = BlendMode.Screen
                        )
                    }

                    if (rainbowCorner > 0.002f) {
                        drawRoundRect(hotCornerBrush, bodyTopLeft, bodySize, corner, blendMode = BlendMode.Plus)
                        drawRoundRect(coolCornerBrush, bodyTopLeft, bodySize, corner, blendMode = BlendMode.Plus)
                    }
                }
            }
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(Modifier.size(if (selected) 9.dp else 7.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.48f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("轻量玻璃 / Rainbow Capsule", color = Color.White.copy(alpha = 0.96f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(if (selected) "选中" else "普通", if (moving) "移动" else "静止", if (rainbowEnabled) "彩虹" else "冷色").joinToString(" · "), color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
