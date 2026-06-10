package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import kotlin.math.max
import kotlin.math.roundToInt

private data class OpenGLLiquidEnvironment(
    val visibility: Float,
    val refraction: Float,
    val edgePull: Float,
    val lensMix: Float,
    val sampleRadius: Float,
    val edgeWidth: Float,
    val darkScale: Float,
    val frameMist: Float,
    val frameEdge: Float,
    val slotDepth: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var visibility by rememberSaveable { mutableStateOf(0.92f) }
    var refraction by rememberSaveable { mutableStateOf(0.72f) }
    var edgePull by rememberSaveable { mutableStateOf(0.78f) }
    var lensMix by rememberSaveable { mutableStateOf(0.42f) }
    var sampleRadius by rememberSaveable { mutableStateOf(0.52f) }
    var edgeWidth by rememberSaveable { mutableStateOf(0.60f) }
    var darkScale by rememberSaveable { mutableStateOf(0.34f) }
    var frameMist by rememberSaveable { mutableStateOf(0.32f) }
    var frameEdge by rememberSaveable { mutableStateOf(0.82f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.34f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        visibility = 0.92f
        refraction = 0.72f
        edgePull = 0.78f
        lensMix = 0.42f
        sampleRadius = 0.52f
        edgeWidth = 0.60f
        darkScale = 0.34f
        frameMist = 0.32f
        frameEdge = 0.82f
        slotDepth = 0.34f
        radiusScale = 1.10f
    }

    val environment = OpenGLLiquidEnvironment(
        visibility = visibility,
        refraction = refraction,
        edgePull = edgePull,
        lensMix = lensMix,
        sampleRadius = sampleRadius,
        edgeWidth = edgeWidth,
        darkScale = darkScale,
        frameMist = frameMist,
        frameEdge = frameEdge,
        slotDepth = slotDepth
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("OpenGL 折射背景预览", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("去掉 Compose 采样背景层，只保留 OpenGL 背景折射 + Compose 框架", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Hybrid", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        OpenGLLiquidBackdropSurface(
            state = state,
            modifier = Modifier.fillMaxWidth().height(224.dp),
            environment = environment,
            radiusScale = radiusScale
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("opengl refracted backdrop", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                OpenGLLiquidSegmentedPill(
                    labels = listOf("Compose", "OpenGL", "混合"),
                    environment = environment,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
                Text("目标：OpenGL 只负责背景折射，Compose 只负责外壳、槽体和文字保护。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("OpenGL 显示", "控制折射背景层整体透明度", visibility, 0f..1.35f) { visibility = it }
        LiquidComposeSlider("折射强度", "主体区域背景 UV 扭曲强度", refraction, 0f..1.6f) { refraction = it }
        LiquidComposeSlider("边缘拉伸", "边缘把背景向内拖拽的幅度", edgePull, 0f..1.6f) { edgePull = it }
        LiquidComposeSlider("透镜混合", "在折射区域混入更清晰的 lens 纹理", lensMix, 0f..1.6f) { lensMix = it }
        LiquidComposeSlider("采样半径", "OpenGL 内部多点采样柔化半径", sampleRadius, 0f..1.6f) { sampleRadius = it }
        LiquidComposeSlider("折边宽度", "控制 OpenGL 边缘折射带宽", edgeWidth, 0f..1.6f) { edgeWidth = it }
        LiquidComposeSlider("暗部收敛", "压住边缘暗场，避免玻璃发灰发脏", darkScale, 0f..1.6f) { darkScale = it }
        LiquidComposeSlider("框架雾面", "Compose 外壳薄雾，不再画背景模糊", frameMist, 0f..1.6f) { frameMist = it }
        LiquidComposeSlider("框架细边", "Compose 极简轮廓线和顶部高光", frameEdge, 0f..1.6f) { frameEdge = it }
        LiquidComposeSlider("槽体嵌入", "分段胶囊槽压入玻璃的程度", slotDepth, 0f..1.6f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制一体玻璃外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置 OpenGL", "恢复折射预览值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("框架验证", "Compose 仅画外壳", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun OpenGLLiquidBackdropSurface(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    environment: OpenGLLiquidEnvironment,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    val coordinateSource = remember { GlassCoordinateSource() }
    val radiusDp = (34f * radiusScale.coerceIn(0.65f, 1.55f)).roundToInt().coerceAtLeast(18)
    val shape = RoundedCornerShape(radiusDp.dp)
    val openGlStyle = remember(environment) {
        GlassBorderStyle(
            outerStrokeAlpha = 0f,
            innerStrokeAlpha = 0f,
            topHighlightAlpha = 0f,
            bottomShadowAlpha = 0f,
            ringWidthDp = 6f + environment.edgeWidth.coerceIn(0f, 1.6f) * 16f,
            edgePullDp = -(46f + environment.edgePull.coerceIn(0f, 1.6f) * 250f),
            edgeBrightness = 1.0f + environment.frameEdge.coerceIn(0f, 1.6f) * 0.10f,
            bodyAlpha = 0f,
            openGlDebugLineAlpha = 0f,
            openGlVisibility = environment.visibility.coerceIn(0f, 1.35f) * 20f,
            openGlMaxAlpha = environment.visibility.coerceIn(0f, 1f),
            openGlPullScale = 26f + environment.refraction.coerceIn(0f, 1.6f) * 145f,
            openGlCompressionScale = environment.lensMix.coerceIn(0f, 1.6f) * 2.4f,
            openGlCornerScale = 8f + environment.edgeWidth.coerceIn(0f, 1.6f) * 24f,
            openGlDarkScale = -environment.darkScale.coerceIn(0f, 1.6f) * 2.2f,
            openGlSampleRadiusScale = environment.sampleRadius.coerceIn(0f, 1.6f) * 18f
        )
    }
    val openGlBackdropSpec = remember(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, openGlStyle) {
        GlassBackdropSpec(
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            params = state.backdropParams,
            borderStyle = openGlStyle
        )
    }

    Box(
        modifier = modifier
            .onPlaced { coordinateSource.coordinates = it }
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides openGlBackdropSpec) {
            OpenGLGlassCardLayer(
                radius = radiusDp,
                glassIntensity = environment.visibility.coerceIn(0.35f, 1.35f),
                coordinateSource = coordinateSource,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .openGLLiquidFrameSkin(environment, radiusDp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun Modifier.openGLLiquidFrameSkin(
    environment: OpenGLLiquidEnvironment,
    radiusDp: Int
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val mist = environment.frameMist.coerceIn(0f, 1.6f)
    val edge = environment.frameEdge.coerceIn(0f, 1.6f)
    val dark = environment.darkScale.coerceIn(0f, 1.6f)
    val rimWidth = max(1f, density * (0.64f + edge * 0.46f))
    val innerInset = rimWidth * (2.10f + dark * 0.34f)
    val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
    val innerCorner = CornerRadius(max(1f, cornerRadius - innerInset), max(1f, cornerRadius - innerInset))
    val mistVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.016f * mist),
            Color(0xFFC7D7FF).copy(alpha = 0.010f * mist),
            Color(0xFF020820).copy(alpha = 0.018f * dark)
        )
    )
    val readabilityField = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.050f * dark),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.060f * dark)
        )
    )
    val topGlance = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.046f * edge),
            Color.White.copy(alpha = 0.016f * edge),
            Color.Transparent
        ),
        start = Offset(-w * 0.02f, -h * 0.10f),
        end = Offset(w * 0.72f, h * 0.26f)
    )
    val outerLine = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.052f * edge),
            Color(0xFFA7D8FF).copy(alpha = 0.020f * edge),
            Color.White.copy(alpha = 0.028f * edge)
        ),
        start = Offset(-w * 0.02f, h * 0.04f),
        end = Offset(w * 1.04f, h * 0.92f)
    )

    onDrawWithContent {
        drawRoundRect(brush = mistVeil, size = size, cornerRadius = corner)
        drawRoundRect(brush = readabilityField, size = size, cornerRadius = corner)
        drawContent()
        drawRoundRect(brush = topGlance, size = size, cornerRadius = corner)
        drawRoundRect(brush = outerLine, size = size, cornerRadius = corner, style = Stroke(width = rimWidth))
        drawRoundRect(color = Color(0xFF02071D).copy(alpha = 0.032f * dark), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.42f)))
        drawRoundRect(color = Color.White.copy(alpha = 0.018f * edge), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.24f)))
    }
}

@Composable
private fun OpenGLLiquidSegmentedPill(
    labels: List<String>,
    environment: OpenGLLiquidEnvironment,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val corner = CornerRadius(h / 2f, h / 2f)
                val edge = environment.frameEdge.coerceIn(0f, 1.6f)
                val slot = environment.slotDepth.coerceIn(0f, 1.6f)
                val dark = environment.darkScale.coerceIn(0f, 1.6f)
                val material = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.030f + 0.010f * slot),
                        Color(0xFF061032).copy(alpha = 0.046f + 0.028f * slot + 0.014f * dark),
                        Color.White.copy(alpha = 0.010f * edge)
                    )
                )
                onDrawWithContent {
                    drawRoundRect(brush = material, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(color = Color.White.copy(alpha = 0.030f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.70f)))
                    drawLine(color = Color.White.copy(alpha = 0.018f * slot), start = Offset(w / 3f, h * 0.28f), end = Offset(w / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
                    drawLine(color = Color.White.copy(alpha = 0.018f * slot), start = Offset(w * 2f / 3f, h * 0.28f), end = Offset(w * 2f / 3f, h * 0.72f), strokeWidth = max(1f, density * 0.50f))
                }
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
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
private fun LiquidComposeSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatLiquidLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = clamped, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LiquidComposeActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.64f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(54.dp),
        role = GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLiquidLabValue(): String = "${((this * 100).roundToInt() / 100f)}"
