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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GlassDebugLazyPatchCompatibility = """
title = "轻量玻璃",
            subtitle = "Compose 玻璃预览 / 材质参数 / 实时调试",
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
            subtitle = "Compose 玻璃预览 / 材质参数 / 实时调试",
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

private data class ComposeGlassStyle(
    val ambientElevation: Float,
    val ambientAlpha: Float,
    val ambientOffsetY: Float,
    val contactElevation: Float,
    val contactAlpha: Float,
    val contactOffsetY: Float,
    val backdropAlpha: Float,
    val centerClarity: Float,
    val frost: Float,
    val edgeOptics: Float,
    val readability: Float,
    val slotDepth: Float,
    val radiusScale: Float
)

@Composable
private fun ComposeGlassLab(state: AssistantUiState) {
    var ambientElevation by rememberSaveable { mutableStateOf(30f) }
    var ambientAlpha by rememberSaveable { mutableStateOf(0.72f) }
    var ambientOffsetY by rememberSaveable { mutableStateOf(14f) }
    var contactElevation by rememberSaveable { mutableStateOf(9f) }
    var contactAlpha by rememberSaveable { mutableStateOf(0.54f) }
    var contactOffsetY by rememberSaveable { mutableStateOf(3.5f) }
    var backdropAlpha by rememberSaveable { mutableStateOf(0.92f) }
    var centerClarity by rememberSaveable { mutableStateOf(1.45f) }
    var frost by rememberSaveable { mutableStateOf(0.44f) }
    var edgeOptics by rememberSaveable { mutableStateOf(1.12f) }
    var readability by rememberSaveable { mutableStateOf(0.48f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.52f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun reset() {
        ambientElevation = 30f
        ambientAlpha = 0.72f
        ambientOffsetY = 14f
        contactElevation = 9f
        contactAlpha = 0.54f
        contactOffsetY = 3.5f
        backdropAlpha = 0.92f
        centerClarity = 1.45f
        frost = 0.44f
        edgeOptics = 1.12f
        readability = 0.48f
        slotDepth = 0.52f
        radiusScale = 1.10f
    }

    val style = ComposeGlassStyle(
        ambientElevation = ambientElevation,
        ambientAlpha = ambientAlpha,
        ambientOffsetY = ambientOffsetY,
        contactElevation = contactElevation,
        contactAlpha = contactAlpha,
        contactOffsetY = contactOffsetY,
        backdropAlpha = backdropAlpha,
        centerClarity = centerClarity,
        frost = frost,
        edgeOptics = edgeOptics,
        readability = readability,
        slotDepth = slotDepth,
        radiusScale = radiusScale
    )

    MatureComposeGlassSurface(state = state, style = style, modifier = Modifier.fillMaxWidth().height(246.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Compose Glass", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("shape-aware shadow and sampled haze", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            ComposeGlassSegmentedPill(listOf("Shadow", "Haze", "Stable"), style, Modifier.fillMaxWidth().height(42.dp))
            Text("目标：普通卡片、滚动按钮和设置项使用稳定 Compose 玻璃，阴影来自同一个圆角形状。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    GlassControlGroup("官方阴影", "同一圆角 shape 的环境阴影与接触阴影", state, true) {
        LabSlider("环境阴影高度", "shape-aware 大阴影的模糊距离", ambientElevation, 0f..96f) { ambientElevation = it }
        LabSlider("环境阴影强度", "悬浮在壁纸上的大面积软阴影", ambientAlpha, 0f..1.6f) { ambientAlpha = it }
        LabSlider("环境阴影下移", "阴影相对玻璃向下偏移", ambientOffsetY, 0f..42f) { ambientOffsetY = it }
        LabSlider("接触阴影高度", "贴近玻璃底部的小阴影模糊", contactElevation, 0f..48f) { contactElevation = it }
        LabSlider("接触阴影强度", "玻璃边缘与壁纸之间的压暗", contactAlpha, 0f..1.6f) { contactAlpha = it }
        LabSlider("接触阴影下移", "接触阴影向下偏移", contactOffsetY, 0f..22f) { contactOffsetY = it }
    }
    GlassControlGroup("背景与主体", "采样背景、中心清透、雾面和圆角", state, true) {
        LabSlider("背景透出", "模糊背景图进入玻璃的强度", backdropAlpha, 0.1f..1.2f) { backdropAlpha = it }
        LabSlider("中心清透", "中间越清透，越不像白雾贴纸", centerClarity, 0f..4f) { centerClarity = it }
        LabSlider("玻璃雾度", "磨砂材质自身的白雾厚度", frost, 0f..4f) { frost = it }
        LabSlider("圆角倍率", "控制样本玻璃圆角", radiusScale, 0.45f..2.2f) { radiusScale = it }
    }
    GlassControlGroup("边缘与槽体", "极细边缘、厚度感、文字暗场和胶囊槽", state, true) {
        LabSlider("边缘光学", "顶部亮边、侧边光和底部重量", edgeOptics, 0f..4f) { edgeOptics = it }
        LabSlider("可读暗场", "保护文字区域，不画内部黑框", readability, 0f..4f) { readability = it }
        LabSlider("槽体压入", "分段按钮压进玻璃的程度", slotDepth, 0f..4f) { slotDepth = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabActionButton("重置 Compose 玻璃", "恢复推荐值", state, Modifier.weight(1f)) { reset() }
        LabActionButton("小组件模式", "不接 OpenGL", state, Modifier.weight(1f)) { }
    }
}

@Composable
private fun MatureComposeGlassSurface(state: AssistantUiState, style: ComposeGlassStyle, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val coordinateSource = remember { GlassCoordinateSource() }
    val radiusDp = (34f * style.radiusScale.coerceIn(0.45f, 2.2f)).roundToInt().coerceAtLeast(14)
    val shape = RoundedCornerShape(radiusDp.dp)
    val border = remember(style.edgeOptics) {
        val edge = style.edgeOptics.coerceIn(0f, 4f)
        GlassBorderStyle(outerStrokeAlpha = 0.014f + edge * 0.040f, innerStrokeAlpha = 0f, topHighlightAlpha = 0.10f + edge * 0.14f, bottomShadowAlpha = 0.020f + edge * 0.030f, ringWidthDp = 2.6f + edge * 2.2f, bodyAlpha = 0f)
    }
    val spec = remember(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border) { GlassBackdropSpec(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, border) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ShapeShadowPlate(Modifier.matchParentSize().padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 26.dp), shape, style.ambientElevation, style.ambientAlpha, style.ambientOffsetY, 0.26f)
        ShapeShadowPlate(Modifier.matchParentSize().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp), shape, style.contactElevation, style.contactAlpha, style.contactOffsetY, 0.32f)
        Box(
            modifier = Modifier.matchParentSize().padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 24.dp).onPlaced { coordinateSource.coordinates = it }.clip(shape),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides spec) {
                SampledWeatherGlassBackdrop(Modifier.matchParentSize(), radiusDp, coordinateSource, state.quality, state.motionIntensity, state.backgroundTheme, liftAlpha = style.backdropAlpha.coerceIn(0.10f, 1.2f))
            }
            Box(Modifier.matchParentSize().composeGlassSkin(style, radiusDp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun ShapeShadowPlate(modifier: Modifier, shape: RoundedCornerShape, elevationDp: Float, alpha: Float, offsetYDp: Float, alphaScale: Float) {
    val safeAlpha = (alpha.coerceIn(0f, 1.6f) * alphaScale).coerceIn(0f, 0.55f)
    Box(
        modifier = modifier
            .offset(y = offsetYDp.coerceIn(0f, 96f).dp)
            .shadow(elevation = elevationDp.coerceIn(0f, 128f).dp, shape = shape, clip = false, ambientColor = Color.Black.copy(alpha = safeAlpha * 0.74f), spotColor = Color.Black.copy(alpha = safeAlpha))
            .background(Color.Black.copy(alpha = 0.001f), shape)
    )
}

private fun Modifier.composeGlassSkin(style: ComposeGlassStyle, radiusDp: Int): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val corner = CornerRadius(radiusDp.dp.toPx(), radiusDp.dp.toPx())
    val frost = style.frost.coerceIn(0f, 4f)
    val clear = style.centerClarity.coerceIn(0f, 4f)
    val edge = style.edgeOptics.coerceIn(0f, 4f)
    val readability = style.readability.coerceIn(0f, 4f)
    val centerFogFactor = (1f - min(clear / 3.2f, 0.88f)).coerceIn(0.06f, 1f)
    val rimWidth = max(1f, density * (0.34f + edge * 0.30f))
    val frostVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.010f * frost * centerFogFactor + 0.004f * edge), Color.White.copy(alpha = 0.006f * frost * centerFogFactor), Color(0xFF10203C).copy(alpha = 0.004f * frost)))
    val centerClearField = Brush.radialGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.003f * frost * centerFogFactor), Color.White.copy(alpha = 0.026f * frost)), center = Offset(w * 0.50f, h * 0.50f), radius = max(w, h) * 0.82f)
    val readableShade = Brush.verticalGradient(listOf(Color(0xFF020820).copy(alpha = 0.020f * readability), Color.Transparent, Color(0xFF020820).copy(alpha = 0.034f * readability)))
    val topGlance = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.028f + 0.048f * edge), Color.White.copy(alpha = 0.012f + 0.016f * edge), Color.Transparent), Offset(-w * 0.04f, -h * 0.06f), Offset(w * 0.84f, h * 0.20f))
    val sideGlance = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.026f * edge), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.018f * edge)), startX = 0f, endX = w)
    val outerLine = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.030f + 0.034f * edge), Color(0xFFC7F3FF).copy(alpha = 0.010f + 0.008f * edge), Color.White.copy(alpha = 0.012f + 0.012f * edge)), Offset(-w * 0.05f, h * 0.03f), Offset(w * 1.04f, h * 0.95f))
    val bottomWeight = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.016f * edge)), startY = h * 0.42f, endY = h)
    onDrawWithContent {
        drawRoundRect(brush = frostVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = centerClearField, size = size, cornerRadius = corner)
        drawRoundRect(brush = readableShade, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = sideGlance, size = size, cornerRadius = corner, style = Stroke(width = max(1f, rimWidth * 2.1f)), blendMode = BlendMode.Screen)
        drawRoundRect(brush = bottomWeight, size = size, cornerRadius = corner, style = Stroke(width = max(1f, rimWidth * 2.4f)), blendMode = BlendMode.Multiply)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
    }
}

@Composable
private fun ComposeGlassSegmentedPill(labels: List<String>, style: ComposeGlassStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(modifier.clip(shape).drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val corner = CornerRadius(h / 2f, h / 2f)
        val edge = style.edgeOptics.coerceIn(0f, 4f)
        val slot = style.slotDepth.coerceIn(0f, 4f)
        val frost = style.frost.coerceIn(0f, 4f)
        val material = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.014f + 0.006f * frost), Color(0xFF061032).copy(alpha = 0.018f + 0.020f * slot), Color.White.copy(alpha = 0.006f * edge)))
        onDrawWithContent {
            drawRoundRect(brush = material, size = size, cornerRadius = corner)
            drawContent()
            drawRoundRect(color = Color.White.copy(alpha = 0.018f + 0.012f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * (0.42f + 0.10f * edge))))
            drawLine(color = Color.White.copy(alpha = 0.008f * slot), start = Offset(w / 3f, h * 0.30f), end = Offset(w / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
            drawLine(color = Color.White.copy(alpha = 0.008f * slot), start = Offset(w * 2f / 3f, h * 0.30f), end = Offset(w * 2f / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.38f))
        }
    }.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ModelCardGlassLab(state: AssistantUiState) {
    val style = ModelCardGlassLabState.style
    PressableGlass(quality = state.quality, glassIntensity = state.glassIntensity * 0.72f, motionIntensity = state.motionIntensity, radius = 28, modifier = Modifier.fillMaxWidth().height(242.dp), role = GlassRole.Flex, onClick = {}) {
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
private fun ModelCardControlGroup(title: String, subtitle: String, state: AssistantUiState, initiallyExpanded: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(quality = state.quality, glassIntensity = state.glassIntensity * 0.58f, motionIntensity = state.motionIntensity, radius = 20, modifier = Modifier.fillMaxWidth().height(48.dp), role = GlassRole.Chip, onClick = { expanded = !expanded }) {
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
    PressableGlass(quality = state.quality, glassIntensity = state.glassIntensity * 0.78f, motionIntensity = state.motionIntensity, radius = 22, modifier = modifier.height(56.dp), role = GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
