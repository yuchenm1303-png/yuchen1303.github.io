package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
            subtitle = "中性基底 + 彩虹边缘 + 彩虹按压扫光",
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
    var rimAlpha by rememberSaveable { mutableFloatStateOf(0.10f) }
    var rainbowRimAlpha by rememberSaveable { mutableFloatStateOf(0.34f) }
var rainbowRimWidth by rememberSaveable { mutableFloatStateOf(0.56f) }
var rainbowPressEdge by rememberSaveable { mutableFloatStateOf(0.38f) }
var rainbowSweepAlpha by rememberSaveable { mutableFloatStateOf(0.34f) }
var rainbowCornerAlpha by rememberSaveable { mutableFloatStateOf(0.06f) }
var rainbowSaturation by rememberSaveable { mutableFloatStateOf(0.86f) }
var pressGlow by rememberSaveable { mutableFloatStateOf(0.26f) }
var pressEdgeBoost by rememberSaveable { mutableFloatStateOf(0.18f) }
var pressSweep by rememberSaveable { mutableFloatStateOf(0.18f) }

    LightweightGlassPreview(
        radius = radius,
        surfaceAlpha = surfaceAlpha,
        rimAlpha = rimAlpha,
        rimWidth = rimWidth,
        topHighlight = topHighlight,
        topHighlightHeight = topHighlightHeight,
        innerRimAlpha = innerRimAlpha,
        bottomDepth = bottomDepth,
        cornerCatchlight = cornerCatchlight,
        pressGlow = pressGlow,
        pressEdgeBoost = pressEdgeBoost,
        pressSweep = pressSweep,
        pressDarken = pressDarken,
        pressElasticity = pressElasticity,
        rainbowRimAlpha = rainbowRimAlpha,
        rainbowRimWidth = rainbowRimWidth,
        rainbowPressEdge = rainbowPressEdge,
        rainbowSweepAlpha = rainbowSweepAlpha,
        rainbowCornerAlpha = rainbowCornerAlpha,
        rainbowSaturation = rainbowSaturation
    )

    LightGlassControlGroup(
        title = "状态预览",
        subtitle = "按住样本可看彩虹局部高光、边缘增强和释放扫光",
        state = state,
        initiallyExpanded = true
    ) {
        Text(
            "这版把大玻璃的白色光学层升级成彩虹光学层：玻璃本体仍然中性，彩色只出现在边缘、按压局部增强和松手扫光上。没有 OpenGL、没有背景采样、没有常驻彩虹动态。",
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }

    LightGlassControlGroup(
        title = "基础玻璃片",
        subtitle = "中性本体：非均匀边框、顶部折边、底部暗边",
        state = state,
        initiallyExpanded = true
    ) {
        LabSlider("圆角半径", "胶囊整体圆润程度", radius, 18f..42f) { radius = it }
        LabSlider("表面透明底", "中性玻璃底色，不做厚雾", surfaceAlpha, 0f..0.10f) { surfaceAlpha = it }
        LabSlider("边框高光", "整体边缘亮度", rimAlpha, 0f..0.55f) { rimAlpha = it }
        LabSlider("边框宽度", "边缘截面厚度", rimWidth, 0.35f..2.20f) { rimWidth = it }
        LabSlider("顶部折边", "上沿薄高光", topHighlight, 0f..0.35f) { topHighlight = it }
        LabSlider("顶部光高度", "顶部高光向下衰减范围", topHighlightHeight, 0.08f..0.45f) { topHighlightHeight = it }
        LabSlider("内侧细边", "内层玻璃折线", innerRimAlpha, 0f..0.30f) { innerRimAlpha = it }
        LabSlider("底部深度", "下沿轻微压暗", bottomDepth, 0f..0.22f) { bottomDepth = it }
        LabSlider("角落高光", "左上角白色捕光点", cornerCatchlight, 0f..0.24f) { cornerCatchlight = it }
    }

    LightGlassControlGroup(
        title = "彩虹光效",
        subtitle = "只染边缘和按压光，不染整块玻璃底",
        state = state,
        initiallyExpanded = true
    ) {
        LabSlider("彩虹边框强度", "连续彩色边缘亮度", rainbowRimAlpha, 0f..0.70f) { rainbowRimAlpha = it }
        LabSlider("彩虹边框宽度", "彩色外沿厚度", rainbowRimWidth, 0f..1.40f) { rainbowRimWidth = it }
        LabSlider("彩虹局部增强", "按压附近的彩色边缘", rainbowPressEdge, 0f..0.80f) { rainbowPressEdge = it }
        LabSlider("棱彩扫光强度", "松手后的彩虹扫光", rainbowSweepAlpha, 0f..0.80f) { rainbowSweepAlpha = it }
        LabSlider("彩虹角部捕光", "角落轻微彩色薄膜", rainbowCornerAlpha, 0f..0.30f) { rainbowCornerAlpha = it }
        LabSlider("彩虹饱和度", "彩色光浓度", rainbowSaturation, 0f..1f) { rainbowSaturation = it }
    }

    LightGlassControlGroup(
        title = "按压光效",
        subtitle = "借鉴大玻璃按压层，但限制在轻量 Canvas 内",
        state = state,
        initiallyExpanded = false
    ) {
        LabSlider("中心高光", "手指附近白青压力光", pressGlow, 0f..0.55f) { pressGlow = it }
        LabSlider("边缘增亮", "靠近哪边哪边变亮", pressEdgeBoost, 0f..0.70f) { pressEdgeBoost = it }
        LabSlider("释放扫光", "松手后的细白色扫光", pressSweep, 0f..0.70f) { pressSweep = it }
        LabSlider("压力暗场", "按压时内部轻微压暗", pressDarken, 0f..0.22f) { pressDarken = it }
        LabSlider("胶囊弹性", "按压形变幅度", pressElasticity, 0f..1.2f) { pressElasticity = it }
    }
}

@Composable
private fun LightweightGlassPreview(
    radius: Float,
    surfaceAlpha: Float,
    rimAlpha: Float,
    rimWidth: Float,
    topHighlight: Float,
    topHighlightHeight: Float,
    innerRimAlpha: Float,
    bottomDepth: Float,
    cornerCatchlight: Float,
    pressGlow: Float,
    pressEdgeBoost: Float,
    pressSweep: Float,
    pressDarken: Float,
    pressElasticity: Float,
    rainbowRimAlpha: Float,
    rainbowRimWidth: Float,
    rainbowPressEdge: Float,
    rainbowSweepAlpha: Float,
    rainbowCornerAlpha: Float,
    rainbowSaturation: Float
) {
    val shape = RoundedCornerShape(radius.dp)
    val pressAnim = remember { Animatable(0f) }
    val sweepAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }
    val press = pressAnim.value.coerceIn(0f, 1.12f)
    val sweep = sweepAnim.value.coerceIn(0f, 1.18f)
    val p = smoothGlass(press.coerceIn(0f, 1f))
    val rebound = smoothGlass(((sweep - 0.68f) / 0.50f).coerceIn(0f, 1f)) * (1f - p)
    val elastic = pressElasticity.coerceIn(0f, 1.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + p * 0.018f * elastic - rebound * 0.004f * elastic
                scaleY = 1f - p * 0.026f * elastic + rebound * 0.010f * elastic
                translationY = p * 2.00f * elastic - rebound * 0.70f * elastic
            }
            .onSizeChanged { size ->
                pressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun updatePress(position: Offset) {
                        pressCenter = Offset(
                            (position.x / pressSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                            (position.y / pressSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePress(down.position)
                    scope.launch {
                        pressAnim.stop()
                        if (pressAnim.value < 0.18f) pressAnim.snapTo(0.18f)
                        pressAnim.animateTo(0.92f, tween(132, easing = FastOutSlowInEasing))
                        pressAnim.animateTo(0.78f, spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow))
                    }
                    scope.launch {
                        sweepAnim.stop()
                        sweepAnim.snapTo(0f)
                        sweepAnim.animateTo(0.42f, tween(180, easing = FastOutSlowInEasing))
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePress(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    scope.launch {
                        pressAnim.stop()
                        pressAnim.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
                    }
                    scope.launch {
                        sweepAnim.stop()
                        sweepAnim.animateTo(1.18f, tween(520, easing = FastOutSlowInEasing))
                        sweepAnim.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 13.dp)
            .clip(shape)
            .lightweightGlassLabSurface(
                radius = radius,
                surfaceAlpha = surfaceAlpha,
                rimAlpha = rimAlpha,
                rimWidth = rimWidth,
                topHighlight = topHighlight,
                topHighlightHeight = topHighlightHeight,
                innerRimAlpha = innerRimAlpha,
                bottomDepth = bottomDepth,
                cornerCatchlight = cornerCatchlight,
                press = p,
                sweep = sweep,
                pressCenter = pressCenter,
                pressGlow = pressGlow,
                pressEdgeBoost = pressEdgeBoost,
                pressSweep = pressSweep,
                pressDarken = pressDarken,
                rainbowRimAlpha = rainbowRimAlpha,
                rainbowRimWidth = rainbowRimWidth,
                rainbowPressEdge = rainbowPressEdge,
                rainbowSweepAlpha = rainbowSweepAlpha,
                rainbowCornerAlpha = rainbowCornerAlpha,
                rainbowSaturation = rainbowSaturation
            )
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF93FFF1).copy(alpha = 0.54f + 0.34f * p))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("轻量玻璃 / Rainbow Press Lab", color = Color.White.copy(alpha = 0.96f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("彩虹边缘 · 按压高光 · 棱彩扫光", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private fun Modifier.lightweightGlassLabSurface(
    radius: Float,
    surfaceAlpha: Float,
    rimAlpha: Float,
    rimWidth: Float,
    topHighlight: Float,
    topHighlightHeight: Float,
    innerRimAlpha: Float,
    bottomDepth: Float,
    cornerCatchlight: Float,
    press: Float,
    sweep: Float,
    pressCenter: Offset,
    pressGlow: Float,
    pressEdgeBoost: Float,
    pressSweep: Float,
    pressDarken: Float,
    rainbowRimAlpha: Float,
    rainbowRimWidth: Float,
    rainbowPressEdge: Float,
    rainbowSweepAlpha: Float,
    rainbowCornerAlpha: Float,
    rainbowSaturation: Float
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.72.dp.toPx()

    val bodySize = Size(w, h)
    val rimSize = Size(
        (w - rimInset * 2f).coerceAtLeast(1f),
        (h - rimInset * 2f).coerceAtLeast(1f)
    )
    val innerSize = Size(
        (w - innerInset * 2f).coerceAtLeast(1f),
        (h - innerInset * 2f).coerceAtLeast(1f)
    )

    val center = Offset(
        pressCenter.x.coerceIn(0f, 1f) * w,
        pressCenter.y.coerceIn(0f, 1f) * h
    )

    val topNear = (1f - pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
    val bottomNear = (1f - (1f - pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
    val leftNear = (1f - pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
    val rightNear = (1f - (1f - pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press

    val sweepT = smoothGlass(sweep.coerceIn(0f, 1f))
    val sweepX = -0.28f + sweepT * 1.56f
    val sat = rainbowSaturation.coerceIn(0f, 1f)

    fun prism(color: Color, alpha: Float): Color {
        return color.copy(alpha = (alpha * (0.32f + sat * 0.68f)).coerceIn(0f, 1f))
    }

    fun prismBandBrush(
        start: Offset,
        end: Offset,
        strength: Float
    ): Brush {
        return Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                prism(Color(0xFF68F7FF), strength * 0.24f),
                prism(Color(0xFFFF7CE1), strength * 0.28f),
                Color.White.copy(alpha = strength * 0.18f),
                prism(Color(0xFFFFE785), strength * 0.22f),
                prism(Color(0xFF7BFF9E), strength * 0.20f),
                prism(Color(0xFF6FA8FF), strength * 0.18f),
                Color.Transparent
            ),
            start = start,
            end = end
        )
    }

    val surface = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f)),
            Color(0xFFCFEAFF).copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f) * 0.28f),
            Color.Transparent,
            Color(0xFF000816).copy(alpha = bottomDepth.coerceIn(0f, 0.35f) * 0.42f)
        ),
        0f,
        h
    )

    val topLens = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topHighlight.coerceIn(0f, 0.5f)),
            Color(0xFFE8FFFF).copy(alpha = topHighlight.coerceIn(0f, 0.5f) * 0.22f),
            Color.Transparent
        ),
        0f,
        h * topHighlightHeight.coerceIn(0.05f, 0.60f)
    )

    val bottomShade = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF020815).copy(alpha = bottomDepth.coerceIn(0f, 0.35f))
        ),
        h * 0.52f,
        h
    )

    val topHairline = Brush.horizontalGradient(
        listOf(
            Color.Transparent,
            Color(0xFFDFFFFF).copy(alpha = rimAlpha.coerceIn(0f, 1f) * 0.18f + topHighlight * 0.20f),
            Color.White.copy(alpha = topHighlight * 0.34f),
            Color.Transparent
        ),
        0f,
        w
    )

    val innerRim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = innerRimAlpha.coerceIn(0f, 0.5f) * 0.62f),
            Color.Transparent,
            Color(0xFF00091E).copy(alpha = bottomDepth.coerceIn(0f, 0.35f) * 0.68f),
            Color.White.copy(alpha = innerRimAlpha.coerceIn(0f, 0.5f) * 0.16f)
        ),
        Offset(w * 0.08f, 0f),
        Offset(w * 0.92f, h)
    )

    val cornerLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = cornerCatchlight.coerceIn(0f, 0.5f)),
            Color(0xFFCFFFFF).copy(alpha = cornerCatchlight.coerceIn(0f, 0.5f) * 0.20f),
            Color.Transparent
        ),
        Offset(w * 0.055f, h * 0.045f),
        maxSide * 0.30f
    )

    val rainbowCorner = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = rainbowCornerAlpha.coerceIn(0f, 0.5f) * 0.22f),
            prism(Color(0xFFFF87E5), rainbowCornerAlpha * 0.16f),
            prism(Color(0xFF79F8FF), rainbowCornerAlpha * 0.18f),
            Color.Transparent
        ),
        Offset(w * 0.10f, h * 0.10f),
        maxSide * 0.24f
    )

    val pressureDark = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color(0xFF071B3D).copy(alpha = pressDarken.coerceIn(0f, 0.4f) * 0.34f * press),
            Color(0xFF01040C).copy(alpha = pressDarken.coerceIn(0f, 0.4f) * press)
        ),
        center,
        maxSide * (0.72f + 0.12f * press)
    )

    val prismPressLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = pressGlow.coerceIn(0f, 0.9f) * 0.20f * press),
            prism(Color(0xFF6AF7FF), pressGlow * 0.26f * press),
            prism(Color(0xFFFF7FE0), pressGlow * 0.24f * press),
            prism(Color(0xFFFFE789), pressGlow * 0.18f * press),
            prism(Color(0xFF7CFFA0), pressGlow * 0.16f * press),
            Color.Transparent
        ),
        center,
        maxSide * (0.30f + 0.22f * press)
    )

    val prismLocalEdge = Brush.linearGradient(
        listOf(
            Color.Transparent,
            prism(Color(0xFF6BF7FF), rainbowPressEdge * 0.30f * press + pressEdgeBoost * 0.10f * press),
            prism(Color(0xFFFF7FE0), rainbowPressEdge * 0.28f * press),
            Color.White.copy(alpha = pressEdgeBoost.coerceIn(0f, 1f) * 0.10f * press),
            prism(Color(0xFFFFE889), rainbowPressEdge * 0.22f * press),
            prism(Color(0xFF7DFFA0), rainbowPressEdge * 0.20f * press),
            Color.Transparent
        ),
        Offset(center.x - w * 0.24f, center.y - h * 0.74f),
        Offset(center.x + w * 0.22f, center.y + h * 0.74f)
    )

    val rimBandPower = rainbowRimAlpha.coerceIn(0f, 1f)
    val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)

    val rimBandMain = prismBandBrush(
        start = Offset(w * (sweepX - 0.22f), h * -0.06f),
        end = Offset(w * (sweepX + 0.28f), h * 1.04f),
        strength = rimBandPower * (0.72f + 0.28f * pressBandBoost)
    )

    val rimBandCounter = prismBandBrush(
        start = Offset(w * (1.12f - sweepX), h * 0.02f),
        end = Offset(w * (0.54f - sweepX), h * 1.00f),
        strength = rimBandPower * 0.52f * (0.70f + 0.30f * pressBandBoost)
    )

    val rimBandTop = prismBandBrush(
        start = Offset(w * (sweepX - 0.18f), h * 0.02f),
        end = Offset(w * (sweepX + 0.34f), h * 0.26f),
        strength = rimBandPower * 0.42f * (0.68f + 0.32f * topNear)
    )

    val prismSweep = prismBandBrush(
        start = Offset(w * (sweepX - 0.24f), h * -0.04f),
        end = Offset(w * (sweepX + 0.30f), h * 1.04f),
        strength = rainbowSweepAlpha.coerceIn(0f, 1f) * sweep + pressSweep.coerceIn(0f, 1f) * 0.12f * sweep
    )

    onDrawWithContent {
        drawRoundRect(
            brush = surface,
            size = bodySize,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = topLens,
            size = bodySize,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = bottomShade,
            size = bodySize,
            cornerRadius = corner,
            blendMode = BlendMode.Multiply
        )

        if (press > 0.001f) {
            drawRoundRect(
                brush = pressureDark,
                size = bodySize,
                cornerRadius = corner,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = prismPressLight,
                size = bodySize,
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
        }

        drawContent()

        drawRoundRect(
            brush = topHairline,
            topLeft = Offset(innerInset, innerInset),
            size = innerSize,
            cornerRadius = corner,
            style = Stroke(0.55.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = innerRim,
            topLeft = Offset(innerInset, innerInset),
            size = innerSize,
            cornerRadius = corner,
            style = Stroke(0.46.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = cornerLight,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = corner,
            style = Stroke(0.68.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        if (rainbowCornerAlpha > 0.001f) {
            drawRoundRect(
                brush = rainbowCorner,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = corner,
                style = Stroke(0.58.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }

        // 重新绘制边缘高光：3 条绑定的棱彩光带
        drawRoundRect(
            brush = rimBandMain,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = corner,
            style = Stroke((rimWidth + rainbowRimWidth * 0.92f).dp.toPx()),
            blendMode = BlendMode.Plus
        )

        drawRoundRect(
            brush = rimBandCounter,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = corner,
            style = Stroke((rimWidth * 0.72f + rainbowRimWidth * 0.58f).dp.toPx()),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = rimBandTop,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = corner,
            style = Stroke((rimWidth * 0.46f + rainbowRimWidth * 0.42f).dp.toPx()),
            blendMode = BlendMode.Plus
        )

        if (press > 0.001f) {
            val localEdgeAlpha = (topNear + bottomNear + leftNear + rightNear).coerceIn(0.16f, 1f)
            drawRoundRect(
                brush = prismLocalEdge,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = corner,
                style = Stroke((0.76f + 0.96f * localEdgeAlpha).dp.toPx()),
                blendMode = BlendMode.Plus
            )
        }

        if (sweep > 0.001f) {
            drawRoundRect(
                brush = prismSweep,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = corner,
                style = Stroke((0.58f + 0.68f * sweep).dp.toPx()),
                blendMode = BlendMode.Plus
            )
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

private fun smoothGlass(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
