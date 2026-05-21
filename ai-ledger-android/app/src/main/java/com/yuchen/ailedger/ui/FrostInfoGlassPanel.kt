package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.DropletGlassStyle
import com.yuchen.ailedger.ui.gl.OpenGLDropletGlassLayer
import kotlin.math.roundToInt

@Composable
fun FrostInfoGlassLab(state: AssistantUiState) {
    var frostRadius by rememberSaveable { mutableStateOf(17.44f) }
    var frostBackdropAlpha by rememberSaveable { mutableStateOf(1f) }
    var frostAlpha by rememberSaveable { mutableStateOf(0f) }
    var frostDimAlpha by rememberSaveable { mutableStateOf(0f) }
    var frostContentAlpha by rememberSaveable { mutableStateOf(1f) }

    var insetRadius by rememberSaveable { mutableStateOf(18f) }
    var insetDepth by rememberSaveable { mutableStateOf(0.52f) }
    var insetBackdropAlpha by rememberSaveable { mutableStateOf(0.82f) }
    var insetRimHighlight by rememberSaveable { mutableStateOf(0.34f) }
    var insetInnerShadow by rememberSaveable { mutableStateOf(0.52f) }
    var insetFloorDim by rememberSaveable { mutableStateOf(0.22f) }

    var dropletBodyBulge by rememberSaveable { mutableStateOf(44f) }
    var dropletEdgePull by rememberSaveable { mutableStateOf(46f) }
    var dropletEdgeWidth by rememberSaveable { mutableStateOf(9f) }
    var dropletLensMix by rememberSaveable { mutableStateOf(0.92f) }
    var dropletDrag by rememberSaveable { mutableStateOf(0.36f) }
    var dropletBottomGlow by rememberSaveable { mutableStateOf(0.32f) }
    var dropletTopGloss by rememberSaveable { mutableStateOf(0.22f) }
    var dropletCornerGloss by rememberSaveable { mutableStateOf(0.30f) }
    var dropletInnerDark by rememberSaveable { mutableStateOf(0.18f) }
    var dropletAlpha by rememberSaveable { mutableStateOf(0.72f) }
    var dropletDebugMask by rememberSaveable { mutableStateOf(0f) }
    var dropletShadowAlpha by rememberSaveable { mutableStateOf(0.18f) }
    var dropletShadowOffsetX by rememberSaveable { mutableStateOf(3.0f) }
    var dropletShadowOffsetY by rememberSaveable { mutableStateOf(7.0f) }
    var dropletShadowSoftness by rememberSaveable { mutableStateOf(18f) }
    var dropletActiveGlow by rememberSaveable { mutableStateOf(0.62f) }
    var dropletBackgroundGlow by rememberSaveable { mutableStateOf(0.38f) }
    var dropletOuterGlow by rememberSaveable { mutableStateOf(0.46f) }
    var dropletWarmGlow by rememberSaveable { mutableStateOf(0.54f) }

    val dropletStyle = DropletGlassStyle(
        bodyBulgePx = dropletBodyBulge,
        edgePullPx = dropletEdgePull,
        edgeWidthPx = dropletEdgeWidth,
        lensMix = dropletLensMix,
        dragStrength = dropletDrag,
        bottomGlow = dropletBottomGlow,
        topGloss = dropletTopGloss,
        cornerGloss = dropletCornerGloss,
        innerDark = dropletInnerDark,
        alpha = dropletAlpha,
        debugMaskAlpha = dropletDebugMask
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassLabMiniTitle("雾面信息玻璃", "只裁剪背景模糊层，不叠边框、高光和折射。")
        FrostInfoGlassPanel(
            radius = frostRadius,
            backdropAlpha = frostBackdropAlpha,
            frostAlpha = frostAlpha,
            dimAlpha = frostDimAlpha,
            modifier = Modifier.fillMaxWidth().height(132.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("雾面信息玻璃", color = Color.White.copy(alpha = frostContentAlpha), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("只裁剪背景模糊层，不叠边框、高光和折射。", color = Color.White.copy(alpha = frostContentAlpha * 0.58f), fontSize = 11.sp, lineHeight = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FrostMetric("记忆容量", "68%", frostContentAlpha, Modifier.weight(1f))
                    FrostMetric("今日对话", "24 次", frostContentAlpha, Modifier.weight(1f))
                    FrostMetric("模型状态", "在线", frostContentAlpha, Modifier.weight(1f))
                }
            }
        }
        GlassPanelSlider("圆角", "面板圆角半径", frostRadius, 8f..42f) { frostRadius = it }
        GlassPanelSlider("背景模糊层", "裁剪后的模糊背景透明度", frostBackdropAlpha, 0f..1f) { frostBackdropAlpha = it }
        GlassPanelSlider("雾面白罩", "越高越像磨砂信息面板", frostAlpha, 0f..0.65f) { frostAlpha = it }
        GlassPanelSlider("暗化遮罩", "压住背景噪声，增强文字可读性", frostDimAlpha, 0f..0.40f) { frostDimAlpha = it }
        GlassPanelSlider("文字透明度", "只影响预览内容，不影响材质", frostContentAlpha, 0.35f..1f) { frostContentAlpha = it }

        GlassLabDivider()
        GlassLabMiniTitle("凹槽玻璃", "边缘高光采样背景颜色，深度只增强交界阴影。")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            InsetGlassSlot(
                radius = insetRadius,
                grooveDepth = insetDepth,
                floorBackdropAlpha = insetBackdropAlpha,
                rimHighlightAlpha = insetRimHighlight,
                innerShadowAlpha = insetInnerShadow,
                floorDimAlpha = insetFloorDim,
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("▸", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    InsetProgressBar(progress = 0.58f, modifier = Modifier.weight(1f).height(12.dp))
                    Text("58", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            InsetGlassSlot(
                radius = insetRadius,
                grooveDepth = insetDepth,
                floorBackdropAlpha = insetBackdropAlpha,
                rimHighlightAlpha = insetRimHighlight,
                innerShadowAlpha = insetInnerShadow,
                floorDimAlpha = insetFloorDim,
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("向 AI 助理提问...", color = Color.White.copy(alpha = 0.40f), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("↗", color = Color.White.copy(alpha = 0.30f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        GlassPanelSlider("槽圆角", "只控制凹槽洞口圆角", insetRadius, 10f..32f) { insetRadius = it }
        GlassPanelSlider("凹槽深度", "不缩小底面，只加重边缘阴影", insetDepth, 0f..1f) { insetDepth = it }
        GlassPanelSlider("底部模糊层", "下沉底面的背景采样", insetBackdropAlpha, 0f..1f) { insetBackdropAlpha = it }
        GlassPanelSlider("动态高光", "凹槽边缘采样背景亮色", insetRimHighlight, 0f..0.80f) { insetRimHighlight = it }
        GlassPanelSlider("内壁阴影", "洞口内侧压暗的厚度感", insetInnerShadow, 0f..0.90f) { insetInnerShadow = it }
        GlassPanelSlider("底部压暗", "让凹槽底面与外部弱分离", insetFloorDim, 0f..0.60f) { insetFloorDim = it }

        GlassLabDivider()
        GlassLabMiniTitle("OpenGL 横向水滴", "长胶囊测试：调试遮罩可单独验证 OpenGL 本体形状。")
        OpenGlLargeDropletPreview(
            style = dropletStyle,
            shadowAlpha = dropletShadowAlpha,
            shadowOffsetX = dropletShadowOffsetX,
            shadowOffsetY = dropletShadowOffsetY,
            shadowSoftness = dropletShadowSoftness,
            activeGlow = dropletActiveGlow,
            backgroundGlow = dropletBackgroundGlow,
            outerGlow = dropletOuterGlow,
            warmGlow = dropletWarmGlow,
            modifier = Modifier.fillMaxWidth().height(96.dp)
        )
        GlassPanelSlider("遮罩调试", "拉高后只显示 OpenGL 自己的真实遮罩", dropletDebugMask, 0f..1f) { dropletDebugMask = it }
        GlassPanelSlider("主体放大", "水滴透镜放大底部图像", dropletBodyBulge, -12f..72f) { dropletBodyBulge = it }
        GlassPanelSlider("边缘压缩", "厚边拉动并压缩背景", dropletEdgePull, -40f..120f) { dropletEdgePull = it }
        GlassPanelSlider("边缘宽度", "折射、高光和拖色宽度", dropletEdgeWidth, 2f..32f) { dropletEdgeWidth = it }
        GlassPanelSlider("清晰混入", "放大后的清晰背景参与", dropletLensMix, 0f..1f) { dropletLensMix = it }
        GlassPanelSlider("拖色强度", "边缘从背景吸色", dropletDrag, 0f..2f) { dropletDrag = it }
        GlassPanelSlider("底部焦散", "底边聚光与粉紫拖色", dropletBottomGlow, 0f..2f) { dropletBottomGlow = it }
        GlassPanelSlider("顶部反光", "上沿真实光亮", dropletTopGloss, 0f..1.8f) { dropletTopGloss = it }
        GlassPanelSlider("角部反光", "右上角弧形亮斑", dropletCornerGloss, 0f..2f) { dropletCornerGloss = it }
        GlassPanelSlider("内部暗边", "背光侧和底部厚边阴影", dropletInnerDark, 0f..1f) { dropletInnerDark = it }
        GlassPanelSlider("整体透明", "OpenGL 水滴层透明度", dropletAlpha, 0f..1f) { dropletAlpha = it }
        GlassPanelSlider("接触阴影", "水滴贴在背景上的软阴影", dropletShadowAlpha, 0f..0.75f) { dropletShadowAlpha = it }
        GlassPanelSlider("阴影 X", "阴影向右偏移", dropletShadowOffsetX, -12f..18f) { dropletShadowOffsetX = it }
        GlassPanelSlider("阴影 Y", "阴影向下偏移", dropletShadowOffsetY, -6f..22f) { dropletShadowOffsetY = it }
        GlassPanelSlider("阴影扩散", "阴影边缘柔化范围", dropletShadowSoftness, 2f..36f) { dropletShadowSoftness = it }
        GlassPanelSlider("选中发光", "控制水滴被点亮的总强度", dropletActiveGlow, 0f..1.5f) { dropletActiveGlow = it }
        GlassPanelSlider("背景点亮", "按钮背后的环境被照亮", dropletBackgroundGlow, 0f..1.5f) { dropletBackgroundGlow = it }
        GlassPanelSlider("外圈辉光", "水滴外侧的柔和光晕", dropletOuterGlow, 0f..1.5f) { dropletOuterGlow = it }
        GlassPanelSlider("底部暖光", "下沿粉紫色激活泛光", dropletWarmGlow, 0f..1.5f) { dropletWarmGlow = it }
    }
}

@Composable
private fun OpenGlLargeDropletPreview(
    style: DropletGlassStyle,
    shadowAlpha: Float,
    shadowOffsetX: Float,
    shadowOffsetY: Float,
    shadowSoftness: Float,
    activeGlow: Float,
    backgroundGlow: Float,
    outerGlow: Float,
    warmGlow: Float,
    modifier: Modifier = Modifier
) {
    val coordinates = remember { GlassCoordinateSource() }
    val debug = style.debugMaskAlpha.coerceIn(0f, 1f)
    Box(modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        if (debug <= 0.001f) {
            DropletBackgroundGlow(
                activeGlow = activeGlow,
                backgroundGlow = backgroundGlow,
                outerGlow = outerGlow,
                warmGlow = warmGlow,
                modifier = Modifier.fillMaxWidth().height(78.dp)
            )
            DropletContactShadow(
                alpha = shadowAlpha,
                offsetX = shadowOffsetX,
                offsetY = shadowOffsetY,
                softness = shadowSoftness,
                modifier = Modifier.fillMaxWidth().height(70.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 0.dp, vertical = 3.dp)
                .onGloballyPositioned { coordinates.coordinates = it }
                .clip(RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            OpenGLDropletGlassLayer(
                radius = 999,
                coordinateSource = coordinates,
                style = style,
                modifier = Modifier.fillMaxSize()
            )
            if (debug <= 0.001f) {
                DropletActiveOverlay(
                    activeGlow = activeGlow,
                    warmGlow = warmGlow,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)
                ) {
                    Text("🎙", color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    Text("语音输入", color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DropletBackgroundGlow(
    activeGlow: Float,
    backgroundGlow: Float,
    outerGlow: Float,
    warmGlow: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val bg = backgroundGlow.coerceIn(0f, 2f)
        val outer = outerGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val pillHeight = size.height * 0.58f
        val pillTop = size.height * 0.20f
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFBFEAFF).copy(alpha = bg * active * 0.18f),
                    Color(0xFF6CCBFF).copy(alpha = bg * active * 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.48f, size.height * 0.42f),
                radius = size.width * 0.58f
            ),
            topLeft = Offset(size.width * 0.03f, pillTop - size.height * 0.12f),
            size = Size(size.width * 0.94f, pillHeight + size.height * 0.24f),
            cornerRadius = CornerRadius(size.height * 0.42f, size.height * 0.42f),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF72B7).copy(alpha = warm * active * 0.22f),
                    Color(0xFFFF9B6F).copy(alpha = warm * active * 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.46f, size.height * 0.76f),
                radius = size.width * 0.48f
            ),
            topLeft = Offset(size.width * 0.05f, size.height * 0.42f),
            size = Size(size.width * 0.90f, size.height * 0.54f),
            cornerRadius = CornerRadius(size.height * 0.32f, size.height * 0.32f),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            color = Color(0xFFA9DFFF).copy(alpha = outer * active * 0.10f),
            topLeft = Offset(size.width * 0.05f, pillTop),
            size = Size(size.width * 0.90f, pillHeight),
            cornerRadius = CornerRadius(pillHeight * 0.50f, pillHeight * 0.50f),
            style = Stroke(width = 1.2.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun DropletActiveOverlay(activeGlow: Float, warmGlow: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val radius = size.height / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = active * 0.18f),
                    Color.White.copy(alpha = active * 0.035f),
                    Color.Transparent
                )
            ),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFFF73C5).copy(alpha = active * warm * 0.22f),
                    Color(0xFFFFB06C).copy(alpha = active * warm * 0.055f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.50f, size.height * 1.03f),
                radius = size.width * 0.42f
            ),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = active * 0.28f),
                    Color.White.copy(alpha = active * 0.08f),
                    Color.Transparent
                ),
                start = Offset(size.width * 0.10f, 0f),
                end = Offset(size.width * 0.92f, size.height * 0.30f)
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun DropletContactShadow(
    alpha: Float,
    offsetX: Float,
    offsetY: Float,
    softness: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val dx = offsetX.dp.toPx()
        val dy = offsetY.dp.toPx()
        val blur = softness.dp.toPx()
        val coreAlpha = alpha.coerceIn(0f, 1f)
        val shadowHeight = (size.height * 0.42f + blur * 0.25f).coerceAtLeast(6f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = coreAlpha * 0.38f),
                    Color.Black.copy(alpha = coreAlpha * 0.12f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.52f + dx, size.height * 0.62f + dy),
                radius = size.width * 0.58f + blur
            ),
            topLeft = Offset(dx + size.width * 0.10f, dy + size.height * 0.40f - blur * 0.12f),
            size = Size(size.width * 0.80f, shadowHeight),
            blendMode = BlendMode.Multiply
        )
    }
}

@Composable
fun FrostInfoGlassPanel(
    modifier: Modifier = Modifier,
    radius: Float = 17.44f,
    backdropAlpha: Float = 1f,
    frostAlpha: Float = 0f,
    dimAlpha: Float = 0f,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier = modifier.onGloballyPositioned { coordinates.coordinates = it }.clip(shape)) {
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = frostAlpha.coerceIn(0f, 0.85f))))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.65f))))
        content()
    }
}

@Composable
private fun InsetGlassSlot(
    modifier: Modifier = Modifier,
    radius: Float,
    grooveDepth: Float,
    floorBackdropAlpha: Float,
    rimHighlightAlpha: Float,
    innerShadowAlpha: Float,
    floorDimAlpha: Float,
    content: @Composable () -> Unit
) {
    val outerCoordinates = remember { GlassCoordinateSource() }
    val floorCoordinates = remember { GlassCoordinateSource() }
    val depth = grooveDepth.coerceIn(0f, 1f)
    val floorInset = 1.35f
    val floorRadius = (radius - 1.2f).coerceAtLeast(5f)

    Box(modifier = modifier.onGloballyPositioned { outerCoordinates.coordinates = it }.clip(RoundedCornerShape(radius.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val shadow = (0.30f + depth * 0.70f) * innerShadowAlpha
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = shadow * 0.72f),
                        Color(0xFF070C29).copy(alpha = 0.28f + depth * 0.12f),
                        Color.Black.copy(alpha = shadow * 0.18f)
                    )
                ),
                cornerRadius = corner,
                blendMode = BlendMode.Multiply
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(floorInset.dp)
                .onGloballyPositioned { floorCoordinates.coordinates = it }
                .clip(RoundedCornerShape(floorRadius.dp))
        ) {
            BackdropCrop(coordinateSource = floorCoordinates, backdropAlpha = floorBackdropAlpha.coerceIn(0f, 1f), modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (floorDimAlpha + depth * 0.06f).coerceIn(0f, 0.75f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = innerShadowAlpha * (0.12f + depth * 0.14f)), Color.Transparent, Color.White.copy(alpha = rimHighlightAlpha * 0.035f)))))
            content()
        }

        DynamicInsetRimHighlight(
            coordinateSource = outerCoordinates,
            radius = radius,
            alpha = rimHighlightAlpha * (0.42f + depth * 0.20f),
            strokeDp = 1.20f,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(Modifier.fillMaxSize()) {
            val floorInsetPx = floorInset.dp.toPx()
            val floorCorner = CornerRadius(floorRadius.dp.toPx(), floorRadius.dp.toPx())
            val floorSize = Size(size.width - floorInsetPx * 2f, size.height - floorInsetPx * 2f)
            val floorTopLeft = Offset(floorInsetPx, floorInsetPx)
            val shadowWidth = (1.2f + depth * 3.8f).dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = innerShadowAlpha * (0.58f + depth * 0.36f)), Color.Black.copy(alpha = innerShadowAlpha * (0.16f + depth * 0.16f)), Color.Transparent)),
                topLeft = floorTopLeft,
                size = floorSize,
                cornerRadius = floorCorner,
                style = Stroke(width = shadowWidth),
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = rimHighlightAlpha * 0.28f), Color.White.copy(alpha = rimHighlightAlpha * 0.08f), Color.Transparent, Color.Black.copy(alpha = innerShadowAlpha * 0.14f)), start = Offset.Zero, end = Offset(size.width, size.height)),
                topLeft = Offset(0.65.dp.toPx(), 0.65.dp.toPx()),
                size = Size(size.width - 1.3.dp.toPx(), size.height - 1.3.dp.toPx()),
                cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx()),
                style = Stroke(width = 0.72.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun DynamicInsetRimHighlight(
    coordinateSource: GlassCoordinateSource,
    radius: Float,
    alpha: Float,
    strokeDp: Float,
    modifier: Modifier = Modifier
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val image = cachedBackdrop?.image ?: return@Canvas
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
        val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
        val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
        val strokePx = strokeDp.dp.toPx()
        val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha.coerceIn(0f, 1f),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(strokePx * 0.50f, strokePx * 0.50f),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = corner,
                style = Stroke(width = strokePx),
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}

@Composable
private fun InsetProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.coerceIn(0f, 1f)
            val radius = size.height / 2f
            drawRoundRect(color = Color.White.copy(alpha = 0.09f), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.SrcOver)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.28f), Color(0xFF8DF9EA).copy(alpha = 0.20f), Color.White.copy(alpha = 0.12f))),
                size = Size(size.width * p, size.height),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun BackdropCrop(coordinateSource: GlassCoordinateSource, backdropAlpha: Float, modifier: Modifier = Modifier) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val backdrop = cachedBackdrop
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        if (backdrop != null) {
            val srcX = (sampleOffset.x * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
            val srcY = (sampleOffset.y * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
            val srcW = (size.width * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
            val srcH = (size.height * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)
            drawImage(
                image = backdrop.image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = backdropAlpha.coerceIn(0f, 1f),
                blendMode = BlendMode.SrcOver
            )
        } else {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78))))
        }
    }
}

@Composable
private fun GlassLabMiniTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun GlassLabDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

@Composable
private fun FrostMetric(label: String, value: String, alpha: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = alpha * 0.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White.copy(alpha = alpha), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GlassPanelSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.050f)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(clamped.formatGlassPanelValue(), color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = clamped,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White.copy(alpha = 0.92f),
                activeTrackColor = Color(0xFF8DF9EA).copy(alpha = 0.52f),
                inactiveTrackColor = Color.White.copy(alpha = 0.13f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

private fun Float.formatGlassPanelValue(): String = "${((this * 100).roundToInt() / 100f)}"
