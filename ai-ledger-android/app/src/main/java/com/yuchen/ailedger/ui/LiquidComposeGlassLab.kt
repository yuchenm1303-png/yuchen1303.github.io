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

private data class ContinuousLiquidEnvironment(
    val visibility: Float,
    val bodyRefraction: Float,
    val surfaceDome: Float,
    val edgeBoost: Float,
    val lensClarity: Float,
    val sampleSoftness: Float,
    val edgeSettle: Float,
    val frameMist: Float,
    val frameEdge: Float,
    val slotDepth: Float
)

@Composable
fun LiquidComposeGlassLab(state: AssistantUiState) {
    var visibility by rememberSaveable { mutableStateOf(0.78f) }
    var bodyRefraction by rememberSaveable { mutableStateOf(0.92f) }
    var surfaceDome by rememberSaveable { mutableStateOf(0.66f) }
    var edgeBoost by rememberSaveable { mutableStateOf(0.18f) }
    var lensClarity by rememberSaveable { mutableStateOf(0.30f) }
    var sampleSoftness by rememberSaveable { mutableStateOf(0.74f) }
    var edgeSettle by rememberSaveable { mutableStateOf(0.18f) }
    var frameMist by rememberSaveable { mutableStateOf(0.22f) }
    var frameEdge by rememberSaveable { mutableStateOf(0.34f) }
    var slotDepth by rememberSaveable { mutableStateOf(0.24f) }
    var radiusScale by rememberSaveable { mutableStateOf(1.10f) }

    fun resetValues() {
        visibility = 0.78f
        bodyRefraction = 0.92f
        surfaceDome = 0.66f
        edgeBoost = 0.18f
        lensClarity = 0.30f
        sampleSoftness = 0.74f
        edgeSettle = 0.18f
        frameMist = 0.22f
        frameEdge = 0.34f
        slotDepth = 0.24f
        radiusScale = 1.10f
    }

    val environment = ContinuousLiquidEnvironment(
        visibility = visibility,
        bodyRefraction = bodyRefraction,
        surfaceDome = surfaceDome,
        edgeBoost = edgeBoost,
        lensClarity = lensClarity,
        sampleSoftness = sampleSoftness,
        edgeSettle = edgeSettle,
        frameMist = frameMist,
        frameEdge = frameEdge,
        slotDepth = slotDepth
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("连续整体 OpenGL 玻璃", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("一整块 OpenGL 折射体：主体轻折射，边缘只做自然增强", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("One piece", color = Color(0xFF8DF9EA).copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        ContinuousOpenGLLiquidSurface(
            state = state,
            modifier = Modifier.fillMaxWidth().height(224.dp),
            environment = environment,
            radiusScale = radiusScale
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liquid Compose", color = Color.White.copy(alpha = 0.95f), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("continuous opengl glass body", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                ContinuousLiquidSegmentedPill(
                    labels = listOf("Compose", "OpenGL", "一体"),
                    environment = environment,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
                Text("目标：整块玻璃连续折射，槽体和外壳属于同一材质，不再做拼接厚边。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LiquidComposeSlider("整体显示", "整块 OpenGL 玻璃体的透明度", visibility, 0f..1.35f) { visibility = it }
        LiquidComposeSlider("主体折射", "中间区域也参与的基础背景扭曲", bodyRefraction, 0f..1.6f) { bodyRefraction = it }
        LiquidComposeSlider("主体透镜", "整块表面的轻微凸透镜感", surfaceDome, 0f..1.6f) { surfaceDome = it }
        LiquidComposeSlider("边缘增强", "边缘只在连续场上轻微加强，不再单独成边", edgeBoost, 0f..1.6f) { edgeBoost = it }
        LiquidComposeSlider("透镜清晰", "混入较清晰背景纹理的比例", lensClarity, 0f..1.6f) { lensClarity = it }
        LiquidComposeSlider("采样柔化", "OpenGL 内部多点采样柔和度", sampleSoftness, 0f..1.6f) { sampleSoftness = it }
        LiquidComposeSlider("边缘收敛", "压住厚蓝边和脏暗边", edgeSettle, 0f..1.6f) { edgeSettle = it }
        LiquidComposeSlider("框架雾面", "Compose 只叠表面薄雾，不画背景", frameMist, 0f..1.6f) { frameMist = it }
        LiquidComposeSlider("框架细边", "Compose 极细轮廓线，不参与折射", frameEdge, 0f..1.6f) { frameEdge = it }
        LiquidComposeSlider("槽体压入", "分段槽体从同一块玻璃里轻微压入", slotDepth, 0f..1.6f) { slotDepth = it }
        LiquidComposeSlider("圆角倍率", "控制一体玻璃外壳圆角", radiusScale, 0.65f..1.55f) { radiusScale = it }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidComposeActionButton("重置一体", "恢复连续玻璃建议值", state, Modifier.weight(1f)) { resetValues() }
            LiquidComposeActionButton("框架验证", "Compose 仅画外壳", state, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun ContinuousOpenGLLiquidSurface(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    environment: ContinuousLiquidEnvironment,
    radiusScale: Float,
    content: @Composable () -> Unit
) {
    val coordinateSource = remember { GlassCoordinateSource() }
    val radiusDp = (34f * radiusScale.coerceIn(0.65f, 1.55f)).roundToInt().coerceAtLeast(18)
    val shape = RoundedCornerShape(radiusDp.dp)
    val openGlStyle = remember(environment) {
        val edge = environment.edgeBoost.coerceIn(0f, 1.6f)
        val body = environment.bodyRefraction.coerceIn(0f, 1.6f)
        val dome = environment.surfaceDome.coerceIn(0f, 1.6f)
        val clarity = environment.lensClarity.coerceIn(0f, 1.6f)
        val softness = environment.sampleSoftness.coerceIn(0f, 1.6f)
        val settle = environment.edgeSettle.coerceIn(0f, 1.6f)
        GlassBorderStyle(
            outerStrokeAlpha = 0f,
            innerStrokeAlpha = 0f,
            topHighlightAlpha = 0f,
            bottomShadowAlpha = 0f,
            ringWidthDp = 3.0f + edge * 5.0f,
            edgePullDp = -(4.0f + edge * 32.0f),
            edgeBrightness = 1.00f + environment.frameEdge.coerceIn(0f, 1.6f) * 0.06f,
            bodyAlpha = 0f,
            openGlDebugLineAlpha = 0f,
            openGlVisibility = environment.visibility.coerceIn(0f, 1.35f) * 20f,
            openGlMaxAlpha = environment.visibility.coerceIn(0f, 1f),
            openGlPullScale = 54f + body * 126f,
            openGlCompressionScale = 0.35f + clarity * 1.25f + dome * 0.30f,
            openGlCornerScale = 2.0f + dome * 12.0f,
            openGlDarkScale = settle * 0.60f,
            openGlSampleRadiusScale = 5.0f + softness * 20.0f
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
                .continuousLiquidFrameSkin(environment, radiusDp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun Modifier.continuousLiquidFrameSkin(
    environment: ContinuousLiquidEnvironment,
    radiusDp: Int
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val cornerRadius = radiusDp.dp.toPx()
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val mist = environment.frameMist.coerceIn(0f, 1.6f)
    val edge = environment.frameEdge.coerceIn(0f, 1.6f)
    val settle = environment.edgeSettle.coerceIn(0f, 1.6f)
    val rimWidth = max(1f, density * (0.46f + edge * 0.32f))
    val innerInset = rimWidth * (1.70f + settle * 0.20f)
    val innerSize = Size(max(1f, w - innerInset * 2f), max(1f, h - innerInset * 2f))
    val innerCorner = CornerRadius(max(1f, cornerRadius - innerInset), max(1f, cornerRadius - innerInset))
    val mistVeil = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.010f * mist),
            Color(0xFFC7D7FF).copy(alpha = 0.007f * mist),
            Color(0xFF020820).copy(alpha = 0.010f * settle)
        )
    )
    val readabilityField = Brush.verticalGradient(
        listOf(
            Color(0xFF020820).copy(alpha = 0.034f * settle),
            Color.Transparent,
            Color(0xFF020820).copy(alpha = 0.044f * settle)
        )
    )
    val topGlance = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.034f * edge),
            Color.White.copy(alpha = 0.012f * edge),
            Color.Transparent
        ),
        start = Offset(-w * 0.02f, -h * 0.10f),
        end = Offset(w * 0.72f, h * 0.26f)
    )
    val outerLine = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.038f * edge),
            Color(0xFFA7D8FF).copy(alpha = 0.014f * edge),
            Color.White.copy(alpha = 0.020f * edge)
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
        drawRoundRect(color = Color(0xFF02071D).copy(alpha = 0.018f * settle), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.30f)))
        drawRoundRect(color = Color.White.copy(alpha = 0.014f * edge), topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = innerCorner, style = Stroke(width = max(1f, rimWidth * 0.20f)))
    }
}

@Composable
private fun ContinuousLiquidSegmentedPill(
    labels: List<String>,
    environment: ContinuousLiquidEnvironment,
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
                val settle = environment.edgeSettle.coerceIn(0f, 1.6f)
                val material = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.020f + 0.006f * slot),
                        Color(0xFF061032).copy(alpha = 0.034f + 0.020f * slot + 0.006f * settle),
                        Color.White.copy(alpha = 0.006f * edge)
                    )
                )
                onDrawWithContent {
                    drawRoundRect(brush = material, size = size, cornerRadius = corner)
                    drawContent()
                    drawRoundRect(color = Color.White.copy(alpha = 0.022f * edge), size = size, cornerRadius = corner, style = Stroke(width = max(1f, density * 0.58f)))
                    drawLine(color = Color.White.copy(alpha = 0.012f * slot), start = Offset(w / 3f, h * 0.30f), end = Offset(w / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.42f))
                    drawLine(color = Color.White.copy(alpha = 0.012f * slot), start = Offset(w * 2f / 3f, h * 0.30f), end = Offset(w * 2f / 3f, h * 0.70f), strokeWidth = max(1f, density * 0.42f))
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
