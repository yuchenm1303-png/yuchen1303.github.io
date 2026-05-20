package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val params = PreviewGlassShapeParams(
        bodyAlpha = bodyAlpha,
        highlight = highlight,
        shadow = shadow,
        depth = depth,
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
                    Text("这些参数只控制本页样本，不影响现有 OpenGL 玻璃。后面调舒服了再回填成正式 GlassRole。", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 15.sp)
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
            Text("先用轻量 Compose 皮肤做形态差异：水滴、凹槽、滑轨、宝石、薄标签和浮岛。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
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
                Text("大底板仍然走 OpenGL，里面这些是不同成本的 Compose 玻璃形态。", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
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
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape((24f * params.roundness).roundToInt().dp))
            .previewGlassShapeSkin(kind, params),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PreviewGlassKind.Groove -> GrooveSampleContent(params)
            PreviewGlassKind.Jewel -> JewelSampleContent(title, subtitle, params)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text("${(params.grooveFill * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    val pressPop: Float
)

private fun Modifier.previewGlassShapeSkin(kind: PreviewGlassKind, params: PreviewGlassShapeParams): Modifier = drawWithCache {
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
    val base = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = baseAlpha + 0.10f * high),
            accent.copy(alpha = baseAlpha * 0.34f + pop * 0.08f),
            Color.Black.copy(alpha = 0.05f * dark)
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val top = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.24f * high),
            Color.White.copy(alpha = 0.035f * high),
            Color.Transparent
        ),
        startY = 0f,
        endY = size.height * 0.44f
    )
    val bottom = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.16f * dark)
        ),
        startY = size.height * 0.54f,
        endY = size.height
    )
    val colorCore = Brush.radialGradient(
        listOf(
            accent.copy(alpha = 0.26f * pop),
            accent.copy(alpha = 0.06f * pop),
            Color.Transparent
        ),
        center = Offset(size.width * 0.74f, size.height * 0.22f),
        radius = size.maxDimension * 0.72f
    )
    val insetShade = Brush.verticalGradient(
        listOf(
            Color.Black.copy(alpha = 0.16f * dark),
            Color.Transparent,
            Color.White.copy(alpha = 0.10f * high)
        ),
        startY = 0f,
        endY = size.height
    )
    onDrawWithContent {
        when (kind) {
            PreviewGlassKind.Inset -> {
                drawRoundRect(Color.Black.copy(alpha = 0.12f * dark), cornerRadius = corner, blendMode = BlendMode.Multiply)
                drawRoundRect(insetShade, cornerRadius = corner, blendMode = BlendMode.SrcOver)
                drawRoundRect(Color.White.copy(alpha = 0.035f * high), topLeft = Offset(1.2f, size.height * 0.08f), size = androidx.compose.ui.geometry.Size(size.width - 2.4f, size.height - 2.4f), cornerRadius = corner, blendMode = BlendMode.Screen)
            }
            PreviewGlassKind.Thin -> {
                drawRoundRect(Color.White.copy(alpha = baseAlpha * 0.55f + 0.03f), cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(top, cornerRadius = corner, blendMode = BlendMode.Screen)
            }
            else -> {
                drawRoundRect(base, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(colorCore, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(top, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(bottom, cornerRadius = corner, blendMode = BlendMode.Multiply)
            }
        }
        val strokeAlpha = when (kind) {
            PreviewGlassKind.Inset -> 0.10f * high
            PreviewGlassKind.Groove -> 0.08f * high
            PreviewGlassKind.Thin -> 0.06f * high
            else -> 0.18f * high + 0.04f * depth
        }
        drawRoundRect(Color.White.copy(alpha = strokeAlpha), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0.dp.toPx()), blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.grooveTrackSkin(params: PreviewGlassShapeParams): Modifier = drawWithCache {
    val corner = CornerRadius(size.height / 2f, size.height / 2f)
    val fillWidth = size.width * params.grooveFill.coerceIn(0f, 1f)
    val groove = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f * params.shadow), Color.White.copy(alpha = 0.09f * params.highlight), Color.Black.copy(alpha = 0.10f * params.shadow)))
    val liquid = Brush.horizontalGradient(listOf(Color(0xFF6E4DB6).copy(alpha = 0.78f), Color(0xFF8DF9EA).copy(alpha = 0.32f + 0.28f * params.colorPop)))
    onDrawWithContent {
        drawRoundRect(groove, cornerRadius = corner, blendMode = BlendMode.SrcOver)
        drawRoundRect(liquid, size = androidx.compose.ui.geometry.Size(fillWidth, size.height), cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(Color.White.copy(alpha = 0.12f * params.highlight), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx()), blendMode = BlendMode.Screen)
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
