package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.roundToInt

private enum class PreviewGlassKind {
    WaterDrop,
    Inset,
    Groove,
    Jewel,
    Thin,
    Island
}

@Composable
fun GlassShapeLabScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit
) {
    var bodyAlpha by rememberSaveable { mutableFloatStateOf(0.18f) }
    var highlight by rememberSaveable { mutableFloatStateOf(0.78f) }
    var shadow by rememberSaveable { mutableFloatStateOf(0.42f) }
    var depth by rememberSaveable { mutableFloatStateOf(0.62f) }
    var roundness by rememberSaveable { mutableFloatStateOf(1.00f) }
    var colorPop by rememberSaveable { mutableFloatStateOf(0.34f) }
    var grooveFill by rememberSaveable { mutableFloatStateOf(0.58f) }
    var pressPop by rememberSaveable { mutableFloatStateOf(1.00f) }
    var sampleMix by rememberSaveable { mutableFloatStateOf(0.90f) }
    var refractionShift by rememberSaveable { mutableFloatStateOf(26f) }
    var edgeFocus by rememberSaveable { mutableFloatStateOf(1.12f) }

    val params = PreviewGlassShapeParams(
        bodyAlpha = bodyAlpha,
        highlight = highlight,
        shadow = shadow,
        depth = depth,
        roundness = roundness,
        colorPop = colorPop,
        grooveFill = grooveFill,
        pressPop = pressPop,
        sampleMix = sampleMix,
        refractionShift = refractionShift,
        edgeFocus = edgeFocus
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "shape-lab-header") {
            ShapeLabHeader(state = state, onBack = onBack)
        }
        item(key = "shape-lab-preview") {
            ShapePreviewBoard(state = state, params = params)
        }
        item(key = "shape-lab-controls") {
            GlassPanel(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 0.96f,
                motionIntensity = state.motionIntensity,
                radius = 28,
                modifier = Modifier.fillMaxWidth().shapeLabGlow(0.24f, Color(0xFF8DF9EA)),
                role = GlassRole.Shell
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("形态控制参数", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("现在按老 Compose 玻璃分层：背景裁剪、乳化雾面、边缘 lens 条带、宽棱镜边和形态皮肤。", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 15.sp)
                    ShapeLabSlider("背景采样", "真实背景模糊层混入比例", sampleMix, 0f..1.20f) { sampleMix = it }
                    ShapeLabSlider("假折射偏移", "只作用在边缘 lens 条带的错位 px", refractionShift, 0f..80f) { refractionShift = it }
                    ShapeLabSlider("边缘聚焦", "边缘带宽、棱镜亮度和压缩感", edgeFocus, 0f..2.20f) { edgeFocus = it }
                    ShapeLabSlider("主体雾面", "整体玻璃底色和可读性", bodyAlpha, 0f..0.70f) { bodyAlpha = it }
                    ShapeLabSlider("顶部高光", "水滴、浮岛和宝石的亮边", highlight, 0f..1.80f) { highlight = it }
                    ShapeLabSlider("内侧暗边", "凹槽、厚度和下沉感", shadow, 0f..1.80f) { shadow = it }
                    ShapeLabSlider("厚度深度", "凸起/凹陷的立体差", depth, 0f..2.00f) { depth = it }
                    ShapeLabSlider("圆润程度", "胶囊和水滴的圆角比例", roundness, 0.45f..1.40f) { roundness = it }
                    ShapeLabSlider("彩色核心", "宝石态、选中态和液体色彩", colorPop, 0f..1.60f) { colorPop = it }
                    ShapeLabSlider("滑轨填充", "液态滑轨的进度长度", grooveFill, 0f..1f) { grooveFill = it }
                    ShapeLabSlider("按压鼓起", "模拟按钮鼓起/收缩的体积感", pressPop, 0.86f..1.16f) { pressPop = it }
                }
            }
        }
    }
}

@Composable
private fun ShapeLabHeader(state: AssistantUiState, onBack: () -> Unit) {
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
            Text("SHAPE LAB", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("玻璃形态预览", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text("样本会裁正式背景缓存，并把 lens 偏移限制在边缘带。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ShapePreviewBoard(state: AssistantUiState, params: PreviewGlassShapeParams) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.98f,
        motionIntensity = state.motionIntensity,
        radius = 32,
        modifier = Modifier.fillMaxWidth().shapeLabGlow(0.32f, Color(0xFF9EB7FF)),
        role = GlassRole.Shell
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("样本面板", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("大底板仍然走 OpenGL；小样本用老 Compose 玻璃分层逻辑做不同形态。", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                ShapeSampleCard("水滴按钮", "凸起 / 选项", PreviewGlassKind.WaterDrop, params, Modifier.weight(1f).height(76.dp))
                ShapeSampleCard("宝石状态", "选中 / 开关", PreviewGlassKind.Jewel, params, Modifier.weight(1f).height(76.dp))
            }
            ShapeSampleCard("凹槽信息条", "嵌入式 / 低优先级信息", PreviewGlassKind.Inset, params, Modifier.fillMaxWidth().height(58.dp))
            ShapeSampleCard("液态滑轨", "Slider / 进度槽", PreviewGlassKind.Groove, params, Modifier.fillMaxWidth().height(62.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                ShapeSampleCard("薄标签", "Badge / 小提示", PreviewGlassKind.Thin, params, Modifier.weight(1f).height(48.dp))
                ShapeSampleCard("浮岛胶囊", "底栏 / 悬浮入口", PreviewGlassKind.Island, params, Modifier.weight(1f).height(48.dp))
            }
        }
    }
}

@Composable
private fun ShapeSampleCard(
    title: String,
    subtitle: String,
    kind: PreviewGlassKind,
    params: PreviewGlassShapeParams,
    modifier: Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when (kind) {
            PreviewGlassKind.WaterDrop, PreviewGlassKind.Jewel, PreviewGlassKind.Island -> params.pressPop
            PreviewGlassKind.Inset -> 1f - (params.pressPop - 1f) * 0.20f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "shape-sample-scale"
    )
    val coordinates = remember { GlassCoordinateSource() }
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameNanos = LocalBackdropFrameTicker.current?.frameNanos ?: 0L
    val radiusDp = (24f * params.roundness).roundToInt().dp
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(radiusDp))
            .onPlaced { coordinates.coordinates = it },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            frameNanos
            val sampleOffset = coordinates.offsetRelativeTo(backdropOrigin)
            val corner = CornerRadius(size.minDimension * 0.50f * params.roundness.coerceIn(0.45f, 1.40f), size.minDimension * 0.50f * params.roundness.coerceIn(0.45f, 1.40f))
            if (cachedBackdrop != null) {
                fun drawPatchRect(
                    dx: Float,
                    dy: Float,
                    useLens: Boolean,
                    alpha: Float,
                    blendMode: BlendMode,
                    dstLeft: Float,
                    dstTop: Float,
                    dstWidth: Float,
                    dstHeight: Float
                ) {
                    if (dstWidth <= 0f || dstHeight <= 0f) return
                    val image = if (useLens) cachedBackdrop.lensImage else cachedBackdrop.image
                    val srcX = ((sampleOffset.x + dstLeft + dx) * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
                    val srcY = ((sampleOffset.y + dstTop + dy) * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
                    val srcW = (dstWidth * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
                    val srcH = (dstHeight * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt()),
                        dstSize = IntSize(dstWidth.roundToInt().coerceAtLeast(1), dstHeight.roundToInt().coerceAtLeast(1)),
                        alpha = alpha.coerceIn(0f, 1f),
                        blendMode = blendMode
                    )
                }

                val baseAlpha = params.sampleMix.coerceIn(0f, 1.2f) * when (kind) {
                    PreviewGlassKind.Thin -> 0.44f
                    PreviewGlassKind.Inset -> 0.68f
                    PreviewGlassKind.Groove -> 0.74f
                    else -> 0.90f
                }
                drawPatchRect(0f, 0f, useLens = false, alpha = baseAlpha, blendMode = BlendMode.SrcOver, dstLeft = 0f, dstTop = 0f, dstWidth = size.width, dstHeight = size.height)

                val edgeBand = (7.dp.toPx() + params.edgeFocus.coerceIn(0f, 2.2f) * 8.dp.toPx() + params.depth.coerceIn(0f, 2f) * 2.dp.toPx()).coerceIn(5.dp.toPx(), size.minDimension * 0.46f)
                val shift = params.refractionShift.coerceIn(0f, 96f)
                val lensAlpha = when (kind) {
                    PreviewGlassKind.Thin -> 0.08f
                    PreviewGlassKind.Inset -> 0.13f
                    PreviewGlassKind.Groove -> 0.16f
                    PreviewGlassKind.Jewel -> 0.25f
                    else -> 0.22f
                } * params.edgeFocus.coerceIn(0f, 2.2f)

                drawPatchRect(-shift, -shift * 0.42f, useLens = true, alpha = lensAlpha, blendMode = BlendMode.Screen, dstLeft = 0f, dstTop = 0f, dstWidth = size.width, dstHeight = edgeBand)
                drawPatchRect(shift * 0.62f, shift * 0.36f, useLens = true, alpha = lensAlpha * 0.62f, blendMode = BlendMode.Screen, dstLeft = 0f, dstTop = size.height - edgeBand, dstWidth = size.width, dstHeight = edgeBand)
                drawPatchRect(-shift * 0.72f, shift * 0.18f, useLens = true, alpha = lensAlpha * 0.82f, blendMode = BlendMode.Screen, dstLeft = 0f, dstTop = 0f, dstWidth = edgeBand, dstHeight = size.height)
                drawPatchRect(shift * 0.92f, -shift * 0.18f, useLens = true, alpha = lensAlpha * 0.90f, blendMode = BlendMode.Screen, dstLeft = size.width - edgeBand, dstTop = 0f, dstWidth = edgeBand, dstHeight = size.height)
            } else {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFBFD3FF).copy(alpha = 0.22f), Color(0xFF34557F).copy(alpha = 0.18f), Color(0xFF050A20).copy(alpha = 0.34f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = corner
                )
            }
            drawFrostedMaterialLayer(kind, params)
            drawPreviewShapeOverlay(kind, params, sampleOffset)
        }
        when (kind) {
            PreviewGlassKind.Groove -> GrooveSampleContent(params)
            PreviewGlassKind.Jewel -> JewelSampleContent(title, subtitle, params)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GrooveSampleContent(params: PreviewGlassShapeParams) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("液态滑轨", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("${(params.grooveFill * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).grooveTrackSkin(params))
    }
}

@Composable
private fun JewelSampleContent(title: String, subtitle: String, params: PreviewGlassShapeParams) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).previewGlassShapeSkin(PreviewGlassKind.Jewel, params))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShapeLabSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val percent = ((clamped - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(54.dp).previewSliderRowSkin(percent)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(clamped.formatShapeValue(), color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
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

private data class PreviewGlassShapeParams(
    val bodyAlpha: Float,
    val highlight: Float,
    val shadow: Float,
    val depth: Float,
    val roundness: Float,
    val colorPop: Float,
    val grooveFill: Float,
    val pressPop: Float,
    val sampleMix: Float,
    val refractionShift: Float,
    val edgeFocus: Float
)

private fun DrawScope.drawFrostedMaterialLayer(kind: PreviewGlassKind, params: PreviewGlassShapeParams) {
    val high = params.highlight.coerceIn(0f, 1.8f)
    val dark = params.shadow.coerceIn(0f, 1.8f)
    val body = params.bodyAlpha.coerceIn(0f, 0.8f)
    val milk = when (kind) {
        PreviewGlassKind.Thin -> 0.020f
        PreviewGlassKind.Inset -> 0.036f
        PreviewGlassKind.Groove -> 0.044f
        else -> 0.052f
    } + body * 0.34f
    val baseScrim = when (kind) {
        PreviewGlassKind.Thin -> 0.030f
        PreviewGlassKind.Inset -> 0.070f
        else -> 0.095f
    } + body * 0.26f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = milk * 0.56f * high.coerceAtLeast(0.35f)),
                Color(0xFFDCE5EF).copy(alpha = milk * 0.24f),
                Color(0xFF8FA4BE).copy(alpha = baseScrim * 0.20f),
                Color(0xFF101B32).copy(alpha = baseScrim * 0.18f + 0.030f * dark)
            )
        ),
        blendMode = BlendMode.SrcOver
    )
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.080f * high), Color.White.copy(alpha = 0.020f * high), Color.Transparent),
            center = Offset(size.width * 0.36f, size.height * 0.05f),
            radius = size.width * 0.95f
        ),
        blendMode = BlendMode.Screen
    )
    if (kind != PreviewGlassKind.Thin) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.050f * dark + 0.020f * params.depth))),
            startY = size.height * 0.58f,
            blendMode = BlendMode.Multiply
        )
    }
}

private fun DrawScope.drawPreviewShapeOverlay(kind: PreviewGlassKind, params: PreviewGlassShapeParams, sampleOffset: Offset = Offset.Zero) {
    val radius = size.minDimension * 0.50f * params.roundness.coerceIn(0.45f, 1.40f)
    val corner = CornerRadius(radius, radius)
    val accent = when (kind) {
        PreviewGlassKind.Jewel -> Color(0xFF8DF9EA)
        PreviewGlassKind.Island -> Color(0xFFFFD166)
        PreviewGlassKind.Groove -> Color(0xFF9EB7FF)
        else -> Color(0xFFEAF5FF)
    }
    val baseAlpha = params.bodyAlpha.coerceIn(0f, 0.80f)
    val high = params.highlight.coerceIn(0f, 1.8f)
    val dark = params.shadow.coerceIn(0f, 1.8f)
    val depth = params.depth.coerceIn(0f, 2.0f)
    val pop = params.colorPop.coerceIn(0f, 1.6f)
    val edgeFocus = params.edgeFocus.coerceIn(0f, 2.2f)
    val positionPhase = ((sampleOffset.x + sampleOffset.y) / 900f) % 1f
    val edgeInset = 0.60.dp.toPx()
    val midInset = (2.2f + 1.8f * depth).dp.toPx()
    val innerInset = (6.5f + 3.5f * edgeFocus).dp.toPx().coerceAtMost(size.minDimension * 0.32f)
    val edgeSize = Size(size.width - edgeInset * 2f, size.height - edgeInset * 2f)
    val midSize = Size(size.width - midInset * 2f, size.height - midInset * 2f)
    val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)

    val broadLens = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.060f * edgeFocus * high),
            Color.White.copy(alpha = 0.018f * edgeFocus),
            Color.Transparent,
            Color.Black.copy(alpha = 0.014f * edgeFocus * dark),
            Color.White.copy(alpha = 0.016f * edgeFocus)
        ),
        start = Offset(size.width * (positionPhase - 0.22f), 0f),
        end = Offset(size.width * (positionPhase + 0.78f), size.height)
    )
    val topPrism = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.080f * edgeFocus * high), Color.White.copy(alpha = 0.020f * edgeFocus), Color.Transparent),
        startY = 0f,
        endY = size.height * 0.32f
    )
    val sideCompression = Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0.040f * edgeFocus), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.018f * edgeFocus * dark), Color.White.copy(alpha = 0.026f * edgeFocus))
    )
    val innerDarkBend = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.010f * edgeFocus * dark), Color.Black.copy(alpha = 0.038f * edgeFocus * dark)),
        startY = size.height * 0.48f,
        endY = size.height
    )
    val colorCore = Brush.radialGradient(
        listOf(accent.copy(alpha = 0.30f * pop), accent.copy(alpha = 0.07f * pop), Color.Transparent),
        center = Offset(size.width * 0.74f, size.height * 0.20f),
        radius = size.maxDimension * 0.72f
    )
    val topSurface = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.22f * high), Color.White.copy(alpha = 0.055f * high), Color.Transparent),
        startY = 0f,
        endY = size.height * 0.42f
    )
    val bottomShade = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.17f * dark + 0.035f * depth)),
        startY = size.height * 0.48f,
        endY = size.height
    )

    when (kind) {
        PreviewGlassKind.Inset -> {
            drawRoundRect(Color.Black.copy(alpha = 0.18f * dark + 0.030f * depth), cornerRadius = corner, blendMode = BlendMode.Multiply)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f * dark), Color.Transparent, Color.White.copy(alpha = 0.13f * high))),
                cornerRadius = corner,
                blendMode = BlendMode.SrcOver
            )
            drawRoundRect(broadLens, topLeft = Offset(edgeInset, edgeInset), size = edgeSize, cornerRadius = corner, style = Stroke(width = (5.5f + 4.0f * edgeFocus).dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(Color.White.copy(alpha = 0.10f * high), topLeft = Offset(midInset, size.height * 0.58f), size = Size(size.width - midInset * 2f, size.height * 0.30f), cornerRadius = corner, style = Stroke(width = 1.1.dp.toPx()), blendMode = BlendMode.Screen)
        }
        PreviewGlassKind.Thin -> {
            drawRoundRect(Color.White.copy(alpha = baseAlpha * 0.25f + 0.020f), cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(topSurface, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(Color.White.copy(alpha = 0.055f * high), topLeft = Offset(edgeInset, edgeInset), size = edgeSize, cornerRadius = corner, style = Stroke(width = 0.80.dp.toPx()), blendMode = BlendMode.Screen)
        }
        else -> {
            drawRoundRect(colorCore, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(topSurface, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(bottomShade, cornerRadius = corner, blendMode = BlendMode.Multiply)
            drawRoundRect(broadLens, topLeft = Offset(edgeInset, edgeInset), size = edgeSize, cornerRadius = corner, style = Stroke(width = (7.0f + 5.0f * edgeFocus).dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(topPrism, topLeft = Offset(midInset, midInset), size = midSize, cornerRadius = corner, style = Stroke(width = (4.6f + 2.0f * high).dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(sideCompression, topLeft = Offset(midInset, midInset), size = midSize, cornerRadius = corner, style = Stroke(width = (4.0f + 2.2f * edgeFocus).dp.toPx()), blendMode = BlendMode.Screen)
            if (innerSize.width > 0f && innerSize.height > 0f) {
                drawRoundRect(innerDarkBend, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(width = (1.8f + 1.2f * dark).dp.toPx()), blendMode = BlendMode.Multiply)
            }
        }
    }

    val outerStrokeAlpha = when (kind) {
        PreviewGlassKind.Inset -> 0.12f * high
        PreviewGlassKind.Groove -> 0.12f * high + 0.06f * edgeFocus
        PreviewGlassKind.Thin -> 0.07f * high
        else -> 0.22f * high + 0.06f * depth + 0.04f * edgeFocus
    }
    drawRoundRect(Color.White.copy(alpha = outerStrokeAlpha), topLeft = Offset(edgeInset, edgeInset), size = edgeSize, cornerRadius = corner, style = Stroke(width = 1.05.dp.toPx()), blendMode = BlendMode.Screen)

    if (kind == PreviewGlassKind.WaterDrop || kind == PreviewGlassKind.Island || kind == PreviewGlassKind.Jewel) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.20f * high), Color.Transparent), start = Offset(size.width * -0.10f, 0f), end = Offset(size.width * 0.90f, size.height * 0.20f)),
            topLeft = Offset(edgeInset, edgeInset),
            size = edgeSize,
            cornerRadius = corner,
            style = Stroke(width = (1.0f + 2.0f * depth).dp.toPx()),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f * high), Color.White.copy(alpha = 0.028f * high), Color.Transparent), center = Offset(size.width * 0.08f, size.height * 0.06f), radius = size.width * 0.35f),
            topLeft = Offset(edgeInset, edgeInset),
            size = edgeSize,
            cornerRadius = corner,
            style = Stroke(width = 0.85.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

private fun Modifier.previewGlassShapeSkin(kind: PreviewGlassKind, params: PreviewGlassShapeParams): Modifier = drawWithCache {
    onDrawWithContent {
        drawPreviewShapeOverlay(kind, params)
        drawContent()
    }
}

private fun Modifier.grooveTrackSkin(params: PreviewGlassShapeParams): Modifier = drawWithCache {
    val corner = CornerRadius(size.height / 2f, size.height / 2f)
    val fillWidth = size.width * params.grooveFill.coerceIn(0f, 1f)
    val groove = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.24f * params.shadow), Color.White.copy(alpha = 0.12f * params.highlight), Color.Black.copy(alpha = 0.15f * params.shadow)))
    val liquid = Brush.horizontalGradient(listOf(Color(0xFF6E4DB6).copy(alpha = 0.84f), Color(0xFF8DF9EA).copy(alpha = 0.34f + 0.34f * params.colorPop)))
    val topLight = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * params.highlight), Color.Transparent), startY = 0f, endY = size.height * 0.55f)
    onDrawWithContent {
        drawRoundRect(groove, cornerRadius = corner, blendMode = BlendMode.SrcOver)
        drawRoundRect(liquid, size = Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(topLight, size = Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(Color.White.copy(alpha = 0.14f * params.highlight), cornerRadius = corner, style = Stroke(width = 0.8.dp.toPx()), blendMode = BlendMode.Screen)
    }
}

private fun Modifier.previewSliderRowSkin(percent: Float): Modifier = drawWithCache {
    val corner = CornerRadius(18.dp.toPx(), 18.dp.toPx())
    val base = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.095f), Color.White.copy(alpha = 0.035f), Color.Black.copy(alpha = 0.055f)), start = Offset.Zero, end = Offset(size.width, size.height))
    val glow = Brush.radialGradient(listOf(Color(0xFF8DF9EA).copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * percent.coerceIn(0f, 1f), size.height * 0.54f), radius = size.height * 1.2f)
    onDrawWithContent {
        drawRoundRect(base, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(glow, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.shapeLabGlow(glow: Float, accent: Color): Modifier = drawWithCache {
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

private fun Float.formatShapeValue(): String = "${((this * 100).roundToInt() / 100f)}x"
