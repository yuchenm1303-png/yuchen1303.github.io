package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PreviewGlassKindV3 {
    WaterDrop,
    Inset,
    Groove,
    Jewel,
    Thin,
    Island
}

private data class ShapeGlassParamsV3(
    val backdropLift: Float,
    val lensShift: Float,
    val edgePower: Float,
    val frostPower: Float,
    val topLight: Float,
    val innerShadow: Float,
    val thickness: Float,
    val roundness: Float,
    val colorPop: Float,
    val grooveFill: Float,
    val pressPop: Float
)

@Composable
fun GlassShapeLabScreenV3(
    state: AssistantUiState,
    onBack: () -> Unit
) {
    var backdropLift by rememberSaveable { mutableFloatStateOf(1.05f) }
    var lensShift by rememberSaveable { mutableFloatStateOf(38f) }
    var edgePower by rememberSaveable { mutableFloatStateOf(2.4f) }
    var frostPower by rememberSaveable { mutableFloatStateOf(1.10f) }
    var topLight by rememberSaveable { mutableFloatStateOf(1.35f) }
    var innerShadow by rememberSaveable { mutableFloatStateOf(1.20f) }
    var thickness by rememberSaveable { mutableFloatStateOf(1.30f) }
    var roundness by rememberSaveable { mutableFloatStateOf(1.00f) }
    var colorPop by rememberSaveable { mutableFloatStateOf(0.70f) }
    var grooveFill by rememberSaveable { mutableFloatStateOf(0.58f) }
    var pressPop by rememberSaveable { mutableFloatStateOf(1.00f) }

    val params = ShapeGlassParamsV3(
        backdropLift = backdropLift,
        lensShift = lensShift,
        edgePower = edgePower,
        frostPower = frostPower,
        topLight = topLight,
        innerShadow = innerShadow,
        thickness = thickness,
        roundness = roundness,
        colorPop = colorPop,
        grooveFill = grooveFill,
        pressPop = pressPop
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "shape-lab-v3-header") {
            ShapeLabHeaderV3(state = state, onBack = onBack)
        }
        item(key = "shape-lab-v3-preview") {
            ShapePreviewBoardV3(state = state, params = params)
        }
        item(key = "shape-lab-v3-controls") {
            GlassPanel(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 0.96f,
                motionIntensity = state.motionIntensity,
                radius = 28,
                modifier = Modifier.fillMaxWidth().shapeLabGlowV3(0.32f, Color(0xFF8DF9EA)),
                role = GlassRole.Shell
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("形态控制参数 V3", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("这版直接复用老 Compose 玻璃内核，不再用矩形边缘采样，避免玻璃内出现分区直线。", color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, lineHeight = 15.sp)
                    ShapeLabSliderV3("背景采样", "正式模糊背景层混入，范围故意放大", backdropLift, 0f..3.0f) { backdropLift = it }
                    ShapeLabSliderV3("全幅 lens 偏移", "整层 lens 背景偏移，避免四块矩形分区线", lensShift, -180f..180f) { lensShift = it }
                    ShapeLabSliderV3("边缘聚焦", "棱镜边、外圈亮边、厚度边缘", edgePower, 0f..8.0f) { edgePower = it }
                    ShapeLabSliderV3("主体雾面", "中心乳化雾面和可读性", frostPower, 0f..5.0f) { frostPower = it }
                    ShapeLabSliderV3("顶部高光", "水滴、浮岛、宝石的上沿亮度", topLight, 0f..6.0f) { topLight = it }
                    ShapeLabSliderV3("内侧暗边", "凹槽、底边、内阴影强度", innerShadow, 0f..6.0f) { innerShadow = it }
                    ShapeLabSliderV3("厚度深度", "边缘宽度、立体凸起和底部厚度", thickness, 0f..8.0f) { thickness = it }
                    ShapeLabSliderV3("圆润程度", "胶囊和水滴圆角比例", roundness, 0.15f..2.20f) { roundness = it }
                    ShapeLabSliderV3("彩色核心", "宝石态、选中态和液体颜色", colorPop, 0f..5.0f) { colorPop = it }
                    ShapeLabSliderV3("滑轨填充", "液态滑轨进度长度", grooveFill, 0f..1f) { grooveFill = it }
                    ShapeLabSliderV3("按压鼓起", "模拟凸起/收缩体积", pressPop, 0.55f..1.55f) { pressPop = it }
                }
            }
        }
    }
}

@Composable
private fun ShapeLabHeaderV3(state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * 0.95f,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier.height(38.dp),
            role = GlassRole.Chip,
            onClick = onBack
        ) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回设置", color = Color.White.copy(alpha = 0.84f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SHAPE LAB V3", color = Color(0xFF8DF9EA).copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("玻璃形态预览", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text("复用老 Compose 玻璃内核，再叠不同形态皮肤。", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ShapePreviewBoardV3(state: AssistantUiState, params: ShapeGlassParamsV3) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.98f,
        motionIntensity = state.motionIntensity,
        radius = 32,
        modifier = Modifier.fillMaxWidth().shapeLabGlowV3(0.38f, Color(0xFF9EB7FF)),
        role = GlassRole.Shell
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("样本面板", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("底层是老 Compose 玻璃，额外 lens 用全幅偏移，不再留下矩形切线。", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                ShapeSampleCardV3(state, "水滴按钮", "凸起 / 选项", PreviewGlassKindV3.WaterDrop, params, Modifier.weight(1f).height(76.dp))
                ShapeSampleCardV3(state, "宝石状态", "选中 / 开关", PreviewGlassKindV3.Jewel, params, Modifier.weight(1f).height(76.dp))
            }
            ShapeSampleCardV3(state, "凹槽信息条", "嵌入式 / 低优先级信息", PreviewGlassKindV3.Inset, params, Modifier.fillMaxWidth().height(58.dp))
            ShapeSampleCardV3(state, "液态滑轨", "Slider / 进度槽", PreviewGlassKindV3.Groove, params, Modifier.fillMaxWidth().height(62.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                ShapeSampleCardV3(state, "薄标签", "Badge / 小提示", PreviewGlassKindV3.Thin, params, Modifier.weight(1f).height(48.dp))
                ShapeSampleCardV3(state, "浮岛胶囊", "底栏 / 悬浮入口", PreviewGlassKindV3.Island, params, Modifier.weight(1f).height(48.dp))
            }
        }
    }
}

@Composable
private fun ShapeSampleCardV3(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    kind: PreviewGlassKindV3,
    params: ShapeGlassParamsV3,
    modifier: Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when (kind) {
            PreviewGlassKindV3.WaterDrop, PreviewGlassKindV3.Jewel, PreviewGlassKindV3.Island -> params.pressPop
            PreviewGlassKindV3.Inset -> 1f - (params.pressPop - 1f) * 0.20f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "shape-v3-scale"
    )
    val coordinates = remember { GlassCoordinateSource() }
    val radius = ((24f * params.roundness.coerceIn(0.15f, 2.20f)).roundToInt()).coerceAtLeast(6)
    val blur = when (kind) {
        PreviewGlassKindV3.Thin -> 44
        PreviewGlassKindV3.Inset -> 72
        PreviewGlassKindV3.Groove -> 86
        else -> 112
    } + (params.frostPower.coerceIn(0f, 5f) * 10f).roundToInt()
    val liftAlpha = params.backdropLift.coerceIn(0f, 3f)
    val edgeStrength = (0.09f + params.edgePower.coerceIn(0f, 8f) * 0.055f).coerceIn(0f, 0.34f)
    val skinIntensity = (0.45f + params.frostPower * 0.42f + params.thickness * 0.10f).coerceIn(0.25f, 1.45f)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(radius.dp))
            .onPlaced { coordinates.coordinates = it },
        contentAlignment = Alignment.Center
    ) {
        SampledWeatherGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            radius = radius,
            coordinateSource = coordinates,
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            blurRadiusDp = blur,
            liftAlpha = liftAlpha
        )
        ShiftedLensWashV3(
            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(radius.dp)),
            coordinateSource = coordinates,
            shift = params.lensShift,
            alpha = lensAlphaForKindV3(kind, params),
            kind = kind
        )
        SampledWeatherEdgeRefraction(
            modifier = Modifier.matchParentSize(),
            radius = radius,
            coordinateSource = coordinates,
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            strength = edgeStrength
        )
        Box(
            Modifier
                .matchParentSize()
                .glassSkin(
                    quality = state.quality,
                    radius = radius,
                    shimmer = 0.23f + params.edgePower * 0.018f,
                    breathe = 0.42f,
                    glassIntensity = skinIntensity,
                    role = GlassRole.Chip,
                    includeShadow = false
                )
        )
        Box(Modifier.matchParentSize().shapePersonalitySkinV3(kind, params, radius))
        when (kind) {
            PreviewGlassKindV3.Groove -> GrooveSampleContentV3(params)
            PreviewGlassKindV3.Jewel -> JewelSampleContentV3(title, subtitle, params)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShiftedLensWashV3(
    modifier: Modifier,
    coordinateSource: GlassCoordinateSource,
    shift: Float,
    alpha: Float,
    kind: PreviewGlassKindV3
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val image = cachedBackdrop?.lensImage ?: return@Canvas
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        val dx = shift.coerceIn(-240f, 240f)
        val dy = shift.coerceIn(-240f, 240f) * when (kind) {
            PreviewGlassKindV3.Inset -> -0.24f
            PreviewGlassKindV3.Groove -> 0.10f
            PreviewGlassKindV3.Jewel -> 0.36f
            else -> -0.32f
        }
        val srcX = ((sampleOffset.x + dx) * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
        val srcY = ((sampleOffset.y + dy) * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
        val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
        val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
        drawImage(
            image = image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha.coerceIn(0f, 1f),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun GrooveSampleContentV3(params: ShapeGlassParamsV3) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("液态滑轨", color = Color.White.copy(alpha = 0.94f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("${(params.grooveFill * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.76f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).grooveTrackSkinV3(params))
    }
}

@Composable
private fun JewelSampleContentV3(title: String, subtitle: String, params: ShapeGlassParamsV3) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).shapePersonalitySkinV3(PreviewGlassKindV3.Jewel, params, 999))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShapeLabSliderV3(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val percent = ((clamped - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(54.dp).previewSliderRowSkinV3(percent)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(clamped.formatShapeValueV3(), color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = clamped,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White.copy(alpha = 0.96f),
                    activeTrackColor = Color(0xFF8DF9EA).copy(alpha = 0.56f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }
    }
}

private fun lensAlphaForKindV3(kind: PreviewGlassKindV3, params: ShapeGlassParamsV3): Float {
    val base = when (kind) {
        PreviewGlassKindV3.Thin -> 0.05f
        PreviewGlassKindV3.Inset -> 0.08f
        PreviewGlassKindV3.Groove -> 0.10f
        PreviewGlassKindV3.Jewel -> 0.18f
        else -> 0.14f
    }
    val shiftBoost = (abs(params.lensShift) / 120f).coerceIn(0f, 1.6f)
    return base * (0.55f + shiftBoost) * (0.45f + params.edgePower.coerceIn(0f, 8f) * 0.18f)
}

private fun Modifier.shapePersonalitySkinV3(kind: PreviewGlassKindV3, params: ShapeGlassParamsV3, radius: Int): Modifier = drawWithCache {
    val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
    val edge = params.edgePower.coerceIn(0f, 8f)
    val top = params.topLight.coerceIn(0f, 6f)
    val dark = params.innerShadow.coerceIn(0f, 6f)
    val thick = params.thickness.coerceIn(0f, 8f)
    val color = params.colorPop.coerceIn(0f, 5f)
    val accent = when (kind) {
        PreviewGlassKindV3.Jewel -> Color(0xFF8DF9EA)
        PreviewGlassKindV3.Island -> Color(0xFFFFD166)
        PreviewGlassKindV3.Groove -> Color(0xFF9EB7FF)
        else -> Color(0xFFEAF5FF)
    }
    val outerInset = 0.7.dp.toPx()
    val innerInset = (3f + thick * 1.1f).dp.toPx().coerceAtMost(size.minDimension * 0.36f)
    val strokeWide = (3.0f + edge * 1.9f + thick * 0.55f).dp.toPx()
    val outerSize = Size(size.width - outerInset * 2f, size.height - outerInset * 2f)
    val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
    onDrawWithContent {
        when (kind) {
            PreviewGlassKindV3.Inset -> {
                drawRoundRect(Color.Black.copy(alpha = (0.030f + dark * 0.035f).coerceIn(0f, 0.34f)), cornerRadius = corner, blendMode = BlendMode.Multiply)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = (dark * 0.050f).coerceIn(0f, 0.42f)), Color.Transparent, Color.White.copy(alpha = (top * 0.030f).coerceIn(0f, 0.20f))),
                        startY = 0f,
                        endY = size.height
                    ),
                    cornerRadius = corner,
                    blendMode = BlendMode.SrcOver
                )
                drawRoundRect(Color.White.copy(alpha = (top * 0.032f).coerceIn(0f, 0.20f)), topLeft = Offset(innerInset, size.height * 0.58f), size = Size(size.width - innerInset * 2f, size.height * 0.28f), cornerRadius = corner, style = Stroke(width = 1.1.dp.toPx()), blendMode = BlendMode.Screen)
            }
            PreviewGlassKindV3.Thin -> {
                drawRoundRect(Color.White.copy(alpha = (0.018f + top * 0.010f).coerceIn(0f, 0.11f)), cornerRadius = corner, blendMode = BlendMode.Screen)
            }
            else -> {
                drawRoundRect(
                    brush = Brush.radialGradient(listOf(accent.copy(alpha = (color * 0.070f).coerceIn(0f, 0.40f)), accent.copy(alpha = (color * 0.018f).coerceIn(0f, 0.14f)), Color.Transparent), center = Offset(size.width * 0.72f, size.height * 0.22f), radius = size.maxDimension * 0.78f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = (top * 0.048f).coerceIn(0f, 0.34f)), Color.White.copy(alpha = (top * 0.012f).coerceIn(0f, 0.10f)), Color.Transparent), startY = 0f, endY = size.height * 0.42f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = (dark * 0.040f + thick * 0.014f).coerceIn(0f, 0.38f))),
                    cornerRadius = corner,
                    blendMode = BlendMode.Multiply
                )
            }
        }
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = (top * 0.050f + edge * 0.018f).coerceIn(0f, 0.55f)), Color.Transparent, Color.Black.copy(alpha = (dark * 0.014f).coerceIn(0f, 0.18f)), Color.White.copy(alpha = (edge * 0.014f).coerceIn(0f, 0.22f))), start = Offset.Zero, end = Offset(size.width, size.height)),
            topLeft = Offset(outerInset, outerInset),
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(width = strokeWide),
            blendMode = BlendMode.Screen
        )
        if (innerSize.width > 0f && innerSize.height > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = (dark * 0.014f + thick * 0.010f).coerceIn(0f, 0.22f)), Color.Black.copy(alpha = (dark * 0.035f).coerceIn(0f, 0.32f))), startY = size.height * 0.48f, endY = size.height),
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                cornerRadius = corner,
                style = Stroke(width = (1.4f + thick * 0.6f).dp.toPx()),
                blendMode = BlendMode.Multiply
            )
        }
        if (kind == PreviewGlassKindV3.WaterDrop || kind == PreviewGlassKindV3.Jewel || kind == PreviewGlassKindV3.Island) {
            drawRoundRect(
                brush = Brush.radialGradient(listOf(Color.White.copy(alpha = (top * 0.055f).coerceIn(0f, 0.34f)), Color.White.copy(alpha = (top * 0.012f).coerceIn(0f, 0.10f)), Color.Transparent), center = Offset(size.width * 0.10f, size.height * 0.06f), radius = size.width * 0.34f),
                topLeft = Offset(outerInset, outerInset),
                size = outerSize,
                cornerRadius = corner,
                style = Stroke(width = (0.8f + thick * 0.38f).dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        drawContent()
    }
}

private fun Modifier.grooveTrackSkinV3(params: ShapeGlassParamsV3): Modifier = drawWithCache {
    val corner = CornerRadius(size.height / 2f, size.height / 2f)
    val fillWidth = size.width * params.grooveFill.coerceIn(0f, 1f)
    val dark = params.innerShadow.coerceIn(0f, 6f)
    val top = params.topLight.coerceIn(0f, 6f)
    val color = params.colorPop.coerceIn(0f, 5f)
    onDrawWithContent {
        drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = (0.08f + dark * 0.028f).coerceIn(0f, 0.36f)), Color.White.copy(alpha = (top * 0.022f).coerceIn(0f, 0.18f)), Color.Black.copy(alpha = (dark * 0.018f).coerceIn(0f, 0.20f)))), cornerRadius = corner, blendMode = BlendMode.SrcOver)
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF6E4DB6).copy(alpha = 0.80f), Color(0xFF8DF9EA).copy(alpha = (0.22f + color * 0.070f).coerceIn(0f, 0.70f)))), size = Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = (top * 0.055f).coerceIn(0f, 0.30f)), Color.Transparent), startY = 0f, endY = size.height * 0.55f), size = Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(Color.White.copy(alpha = (top * 0.030f).coerceIn(0f, 0.22f)), cornerRadius = corner, style = Stroke(width = 0.8.dp.toPx()), blendMode = BlendMode.Screen)
    }
}

private fun Modifier.previewSliderRowSkinV3(percent: Float): Modifier = drawWithCache {
    val corner = CornerRadius(18.dp.toPx(), 18.dp.toPx())
    val base = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.095f), Color.White.copy(alpha = 0.035f), Color.Black.copy(alpha = 0.055f)), start = Offset.Zero, end = Offset(size.width, size.height))
    val glow = Brush.radialGradient(listOf(Color(0xFF8DF9EA).copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * percent.coerceIn(0f, 1f), size.height * 0.54f), radius = size.height * 1.2f)
    onDrawWithContent {
        drawRoundRect(base, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(glow, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.shapeLabGlowV3(glow: Float, accent: Color): Modifier = drawWithCache {
    val brush = Brush.radialGradient(
        listOf(accent.copy(alpha = 0.14f * glow), Color.White.copy(alpha = 0.05f * glow), Color.Transparent),
        center = Offset(size.width * 0.22f, size.height * 0.12f),
        radius = size.maxDimension * 0.80f
    )
    onDrawWithContent {
        drawRect(brush, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Float.formatShapeValueV3(): String = "${((this * 100).roundToInt() / 100f)}x"
