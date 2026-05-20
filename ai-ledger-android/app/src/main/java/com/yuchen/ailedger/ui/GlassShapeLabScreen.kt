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
    var sampleMix by rememberSaveable { mutableFloatStateOf(0.82f) }
    var refractionShift by rememberSaveable { mutableFloatStateOf(18f) }
    var edgeFocus by rememberSaveable { mutableFloatStateOf(0.86f) }

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
                    Text("底层复用正式 App 的 blurred/lens 背景缓存，上层只测试不同 Compose 小玻璃皮肤。", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 15.sp)
                    ShapeLabSlider("背景采样", "真实背景模糊层混入比例", sampleMix, 0f..1.20f) { sampleMix = it }
                    ShapeLabSlider("假折射偏移", "lens 背景相对主体的错位 px", refractionShift, 0f..64f) { refractionShift = it }
                    ShapeLabSlider("边缘聚焦", "边缘 lens、亮边和压缩感", edgeFocus, 0f..1.80f) { edgeFocus = it }
                    ShapeLabSlider("主体雾面", "整体玻璃底色和可读性", bodyAlpha, 0f..0.70f) { bodyAlpha = it }
                    ShapeLabSlider("顶部高光", "水滴、浮岛和宝石的亮边", highlight, 0f..1.60f) { highlight = it }
                    ShapeLabSlider("内侧暗边", "凹槽、厚度和下沉感", shadow, 0f..1.60f) { shadow = it }
                    ShapeLabSlider("厚度深度", "凸起/凹陷的立体差", depth, 0f..1.80f) { depth = it }
                    ShapeLabSlider("圆润程度", "胶囊和水滴的圆角比例", roundness, 0.45f..1.40f) { roundness = it }
                    ShapeLabSlider("彩色核心", "宝石态、选中态和液体色彩", colorPop, 0f..1.40f) { colorPop = it }
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
            Text("样本会裁正式背景缓存，并用 lens 偏移模拟弱折射。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
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
                Text("大底板仍然走 OpenGL，里面这些小组件使用真实背景采样 + 偏移 lens + 形态光影。", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
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
            if (cachedBackdrop != null) {
                fun drawPatch(dx: Float, dy: Float, useLens: Boolean, alpha: Float, blendMode: BlendMode) {
                    val image = if (useLens) cachedBackdrop.lensImage else cachedBackdrop.image
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
                        blendMode = blendMode
                    )
                }
                val shift = params.refractionShift.coerceIn(0f, 96f)
                val kindLens = when (kind) {
                    PreviewGlassKind.WaterDrop -> 1.00f
                    PreviewGlassKind.Jewel -> 1.18f
                    PreviewGlassKind.Inset -> 0.46f
                    PreviewGlassKind.Groove -> 0.62f
                    PreviewGlassKind.Thin -> 0.24f
                    PreviewGlassKind.Island -> 0.92f
                }
                val baseAlpha = params.sampleMix.coerceIn(0f, 1.2f) * when (kind) {
                    PreviewGlassKind.Thin -> 0.38f
                    PreviewGlassKind.Inset -> 0.62f
                    PreviewGlassKind.Groove -> 0.70f
                    else -> 0.82f
                }
                val lensAlpha = params.edgeFocus.coerceIn(0f, 1.8f) * 0.22f * kindLens
                drawPatch(-shift * kindLens, -shift * 0.36f, useLens = true, alpha = lensAlpha, blendMode = BlendMode.Screen)
                drawPatch(0f, 0f, useLens = false, alpha = baseAlpha, blendMode = BlendMode.SrcOver)
                if (kind != PreviewGlassKind.Thin) {
                    drawPatch(shift * 0.45f, -shift * 0.18f, useLens = true, alpha = lensAlpha * 0.55f, blendMode = BlendMode.Screen)
                }
            } else {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFBFD3FF).copy(alpha = 0.24f), Color(0xFF34557F).copy(alpha = 0.18f), Color(0xFF050A20).copy(alpha = 0.32f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawPreviewShapeOverlay(kind, params)
        }
        when (kind) {
            PreviewGlassKind.Groove -> GrooveSampleContent(params)
            PreviewGlassKind.Jewel -> JewelSampleContent(title, subtitle, params)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GrooveSampleContent(params: PreviewGlassShapeParams) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("液态滑轨", color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("${(params.grooveFill * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).grooveTrackSkin(params))
    }
}

@Composable
private fun JewelSampleContent(title: String, subtitle: String, params: PreviewGlassShapeParams) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).previewGlassShapeSkin(PreviewGlassKind.Jewel, params))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

private fun DrawScope.drawPreviewShapeOverlay(kind: PreviewGlassKind, params: PreviewGlassShapeParams) {
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
    val depth = params.depth.coerceIn(0f, 1.8f)
    val pop = params.colorPop.coerceIn(0f, 1.5f)
    val edgeFocus = params.edgeFocus.coerceIn(0f, 1.8f)
    val wash = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = baseAlpha + 0.045f * high),
            accent.copy(alpha = baseAlpha * 0.18f + pop * 0.065f),
            Color.Black.copy(alpha = 0.030f * dark)
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val top = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.26f * high), Color.White.copy(alpha = 0.050f * high), Color.Transparent),
        startY = 0f,
        endY = size.height * 0.44f
    )
    val bottom = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.17f * dark + 0.025f * depth)),
        startY = size.height * 0.48f,
        endY = size.height
    )
    val colorCore = Brush.radialGradient(
        listOf(accent.copy(alpha = 0.30f * pop), accent.copy(alpha = 0.06f * pop), Color.Transparent),
        center = Offset(size.width * 0.74f, size.height * 0.20f),
        radius = size.maxDimension * 0.72f
    )
    val sidePrism = Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0.10f * edgeFocus), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.045f * edgeFocus), Color.White.copy(alpha = 0.07f * edgeFocus))
    )
    val insetShade = Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.20f * dark), Color.Transparent, Color.White.copy(alpha = 0.12f * high)),
        startY = 0f,
        endY = size.height
    )
    when (kind) {
        PreviewGlassKind.Inset -> {
            drawRoundRect(Color.Black.copy(alpha = 0.16f * dark), cornerRadius = corner, blendMode = BlendMode.Multiply)
            drawRoundRect(insetShade, cornerRadius = corner, blendMode = BlendMode.SrcOver)
            drawRoundRect(Color.White.copy(alpha = 0.045f * high), topLeft = Offset(1.2f, size.height * 0.08f), size = Size(size.width - 2.4f, size.height - 2.4f), cornerRadius = corner, blendMode = BlendMode.Screen)
        }
        PreviewGlassKind.Thin -> {
            drawRoundRect(Color.White.copy(alpha = baseAlpha * 0.36f + 0.025f), cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(top, cornerRadius = corner, blendMode = BlendMode.Screen)
        }
        else -> {
            drawRoundRect(wash, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(colorCore, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(sidePrism, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(top, cornerRadius = corner, blendMode = BlendMode.Screen)
            drawRoundRect(bottom, cornerRadius = corner, blendMode = BlendMode.Multiply)
        }
    }
    val strokeAlpha = when (kind) {
        PreviewGlassKind.Inset -> 0.12f * high
        PreviewGlassKind.Groove -> 0.08f * high + 0.04f * edgeFocus
        PreviewGlassKind.Thin -> 0.06f * high
        else -> 0.20f * high + 0.05f * depth + 0.04f * edgeFocus
    }
    drawRoundRect(Color.White.copy(alpha = strokeAlpha), cornerRadius = corner, style = Stroke(width = 1.0.dp.toPx()), blendMode = BlendMode.Screen)
    if (kind == PreviewGlassKind.WaterDrop || kind == PreviewGlassKind.Island || kind == PreviewGlassKind.Jewel) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f * high), Color.Transparent), start = Offset(size.width * -0.10f, 0f), end = Offset(size.width * 0.92f, size.height * 0.20f)),
            cornerRadius = corner,
            style = Stroke(width = (1.0f + 2.0f * depth).dp.toPx()),
            blendMode = BlendMode.Plus
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
    val groove = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f * params.shadow), Color.White.copy(alpha = 0.10f * params.highlight), Color.Black.copy(alpha = 0.12f * params.shadow)))
    val liquid = Brush.horizontalGradient(listOf(Color(0xFF6E4DB6).copy(alpha = 0.80f), Color(0xFF8DF9EA).copy(alpha = 0.32f + 0.30f * params.colorPop)))
    onDrawWithContent {
        drawRoundRect(groove, cornerRadius = corner, blendMode = BlendMode.SrcOver)
        drawRoundRect(liquid, size = Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
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
