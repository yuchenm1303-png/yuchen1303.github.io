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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.roundToInt

@Composable
fun FrostInfoGlassLab(state: AssistantUiState) {
    var frostRadius by rememberSaveable { mutableStateOf(17.44f) }
    var frostBackdropAlpha by rememberSaveable { mutableStateOf(1.00f) }
    var frostAlpha by rememberSaveable { mutableStateOf(0.00f) }
    var frostDimAlpha by rememberSaveable { mutableStateOf(0.00f) }
    var frostContentAlpha by rememberSaveable { mutableStateOf(1.00f) }

    var insetRadius by rememberSaveable { mutableStateOf(20f) }
    var insetInset by rememberSaveable { mutableStateOf(5.8f) }
    var insetBackdropAlpha by rememberSaveable { mutableStateOf(0.86f) }
    var insetRimHighlight by rememberSaveable { mutableStateOf(0.42f) }
    var insetInnerShadow by rememberSaveable { mutableStateOf(0.64f) }
    var insetFloorDim by rememberSaveable { mutableStateOf(0.30f) }

    var dropletRadius by rememberSaveable { mutableStateOf(30f) }
    var dropletBackdropAlpha by rememberSaveable { mutableStateOf(0.88f) }
    var dropletGlossAlpha by rememberSaveable { mutableStateOf(0.34f) }
    var dropletBottomGlowAlpha by rememberSaveable { mutableStateOf(0.34f) }
    var dropletDepthAlpha by rememberSaveable { mutableStateOf(0.18f) }

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
        GlassLabMiniTitle("凹槽玻璃", "先铺一层固定平板玻璃，再在里面挖槽；滑块只调凹槽。")
        InsetGlassPlate(modifier = Modifier.fillMaxWidth().height(126.dp)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                InsetGlassSlot(
                    radius = insetRadius,
                    innerInset = insetInset,
                    floorBackdropAlpha = insetBackdropAlpha,
                    rimHighlightAlpha = insetRimHighlight,
                    innerShadowAlpha = insetInnerShadow,
                    floorDimAlpha = insetFloorDim,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("▸", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        InsetProgressBar(progress = 0.58f, modifier = Modifier.weight(1f).height(18.dp))
                        Text("58", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                InsetGlassSlot(
                    radius = insetRadius,
                    innerInset = insetInset,
                    floorBackdropAlpha = insetBackdropAlpha,
                    rimHighlightAlpha = insetRimHighlight,
                    innerShadowAlpha = insetInnerShadow,
                    floorDimAlpha = insetFloorDim,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("向 AI 助理提问...", color = Color.White.copy(alpha = 0.42f), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("↗", color = Color.White.copy(alpha = 0.34f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        GlassPanelSlider("槽圆角", "只控制凹槽洞口圆角", insetRadius, 10f..36f) { insetRadius = it }
        GlassPanelSlider("凹槽内缩", "洞口到下沉底面的距离", insetInset, 1f..10f) { insetInset = it }
        GlassPanelSlider("底部模糊层", "下沉底面的背景采样", insetBackdropAlpha, 0f..1f) { insetBackdropAlpha = it }
        GlassPanelSlider("洞口高光", "凹槽外沿的细亮边", insetRimHighlight, 0f..0.80f) { insetRimHighlight = it }
        GlassPanelSlider("内壁阴影", "洞口内侧压暗的厚度感", insetInnerShadow, 0f..1f) { insetInnerShadow = it }
        GlassPanelSlider("底部压暗", "让凹槽底面和外侧平板分离", insetFloorDim, 0f..0.75f) { insetFloorDim = it }

        GlassLabDivider()
        GlassLabMiniTitle("水滴玻璃", "暂时保留 Compose 预览，后续更适合接 OpenGL 透镜。")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            DropletGlassButton("✦", "发送", dropletRadius, dropletBackdropAlpha, dropletGlossAlpha, dropletBottomGlowAlpha, dropletDepthAlpha, Modifier.weight(1f))
            DropletGlassButton("AI", "AI 助理", dropletRadius, dropletBackdropAlpha, dropletGlossAlpha, dropletBottomGlowAlpha, dropletDepthAlpha, Modifier.weight(1f))
            DropletGlassButton("♪", "语音", dropletRadius, dropletBackdropAlpha, dropletGlossAlpha, dropletBottomGlowAlpha, dropletDepthAlpha, Modifier.weight(1f))
        }
        GlassPanelSlider("圆角", "水滴胶囊的圆润程度", dropletRadius, 16f..42f) { dropletRadius = it }
        GlassPanelSlider("背景模糊层", "按钮内部的背景采样", dropletBackdropAlpha, 0f..1f) { dropletBackdropAlpha = it }
        GlassPanelSlider("顶部光泽", "凸起水滴的上沿亮斑", dropletGlossAlpha, 0f..0.85f) { dropletGlossAlpha = it }
        GlassPanelSlider("底部泛光", "下沿粉紫色液态光", dropletBottomGlowAlpha, 0f..0.90f) { dropletBottomGlowAlpha = it }
        GlassPanelSlider("厚度暗边", "底部和侧边压暗厚度", dropletDepthAlpha, 0f..0.55f) { dropletDepthAlpha = it }
    }
}

@Composable
fun FrostInfoGlassPanel(
    modifier: Modifier = Modifier,
    radius: Float = 17.44f,
    backdropAlpha: Float = 1.00f,
    frostAlpha: Float = 0.00f,
    dimAlpha: Float = 0.00f,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val shape = RoundedCornerShape(radius.dp)

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates.coordinates = it }
            .clip(shape)
    ) {
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = frostAlpha.coerceIn(0f, 0.85f))))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.65f))))
        content()
    }
}

@Composable
private fun InsetGlassPlate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val radius = 30f
    val shape = RoundedCornerShape(radius.dp)

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates.coordinates = it }
            .clip(shape)
    ) {
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = 0.74f, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.055f)))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.055f)))
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.035f),
                        Color.Black.copy(alpha = 0.10f)
                    )
                ),
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                topLeft = Offset(0.9.dp.toPx(), 0.9.dp.toPx()),
                size = Size(size.width - 1.8.dp.toPx(), size.height - 1.8.dp.toPx()),
                cornerRadius = corner,
                style = Stroke(width = 1.05.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        content()
    }
}

@Composable
private fun InsetGlassSlot(
    modifier: Modifier = Modifier,
    radius: Float,
    innerInset: Float,
    floorBackdropAlpha: Float,
    rimHighlightAlpha: Float,
    innerShadowAlpha: Float,
    floorDimAlpha: Float,
    content: @Composable () -> Unit
) {
    val floorCoordinates = remember { GlassCoordinateSource() }
    val outerShape = RoundedCornerShape(radius.dp)
    val safeInset = innerInset.coerceIn(0.5f, 12f)
    val floorRadius = (radius - safeInset * 0.78f).coerceAtLeast(5f)
    val floorShape = RoundedCornerShape(floorRadius.dp)

    Box(modifier = modifier.clip(outerShape)) {
        Canvas(Modifier.fillMaxSize()) {
            val outerCorner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val w = size.width
            val h = size.height
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = innerShadowAlpha * 0.56f),
                        Color(0xFF080D2C).copy(alpha = 0.42f),
                        Color.Black.copy(alpha = innerShadowAlpha * 0.18f)
                    )
                ),
                cornerRadius = outerCorner,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = rimHighlightAlpha),
                        Color.White.copy(alpha = rimHighlightAlpha * 0.18f),
                        Color.Transparent,
                        Color.Black.copy(alpha = innerShadowAlpha * 0.34f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                topLeft = Offset(0.9.dp.toPx(), 0.9.dp.toPx()),
                size = Size(w - 1.8.dp.toPx(), h - 1.8.dp.toPx()),
                cornerRadius = outerCorner,
                style = Stroke(width = 1.20.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safeInset.dp)
                .onGloballyPositioned { floorCoordinates.coordinates = it }
                .clip(floorShape)
        ) {
            BackdropCrop(
                coordinateSource = floorCoordinates,
                backdropAlpha = floorBackdropAlpha.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = floorDimAlpha.coerceIn(0f, 0.85f))))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = innerShadowAlpha * 0.24f),
                            Color.Transparent,
                            Color.White.copy(alpha = rimHighlightAlpha * 0.06f)
                        )
                    )
                )
            )
            content()
        }

        Canvas(Modifier.fillMaxSize()) {
            val insetPx = safeInset.dp.toPx()
            val floorCorner = CornerRadius(floorRadius.dp.toPx(), floorRadius.dp.toPx())
            val floorSize = Size(size.width - insetPx * 2f, size.height - insetPx * 2f)
            val floorTopLeft = Offset(insetPx, insetPx)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = innerShadowAlpha),
                        Color.Black.copy(alpha = innerShadowAlpha * 0.38f),
                        Color.Transparent
                    )
                ),
                topLeft = floorTopLeft,
                size = floorSize,
                cornerRadius = floorCorner,
                style = Stroke(width = (safeInset * 1.05f).dp.toPx()),
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = rimHighlightAlpha * 0.56f),
                        Color.White.copy(alpha = rimHighlightAlpha * 0.10f),
                        Color.Transparent
                    )
                ),
                topLeft = floorTopLeft + Offset(0.7.dp.toPx(), 0.7.dp.toPx()),
                size = Size(floorSize.width - 1.4.dp.toPx(), floorSize.height - 1.4.dp.toPx()),
                cornerRadius = floorCorner,
                style = Stroke(width = 0.78.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun InsetProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.coerceIn(0f, 1f)
            val radius = size.height / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.SrcOver
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color(0xFF8DF9EA).copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                size = Size(size.width * p, size.height),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Screen
            )
            val beadX = (size.width * p).coerceIn(radius, size.width - radius)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color(0xFFBEEFFF).copy(alpha = 0.38f),
                        Color(0xFF18204C).copy(alpha = 0.20f)
                    ),
                    center = Offset(beadX - radius * 0.25f, size.height * 0.28f),
                    radius = size.height * 1.10f
                ),
                radius = size.height * 0.68f,
                center = Offset(beadX, size.height / 2f),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun DropletGlassButton(
    icon: String,
    label: String,
    radius: Float,
    backdropAlpha: Float,
    glossAlpha: Float,
    bottomGlowAlpha: Float,
    depthAlpha: Float,
    modifier: Modifier = Modifier
) {
    val coordinates = remember { GlassCoordinateSource() }
    Box(
        modifier = modifier
            .height(58.dp)
            .onGloballyPositioned { coordinates.coordinates = it }
            .clip(RoundedCornerShape(radius.dp))
    ) {
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glossAlpha * 0.62f),
                        Color.White.copy(alpha = glossAlpha * 0.14f),
                        Color.Black.copy(alpha = depthAlpha)
                    )
                ),
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF8AC8).copy(alpha = bottomGlowAlpha),
                        Color(0xFF8DF9EA).copy(alpha = bottomGlowAlpha * 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.58f, size.height * 0.96f),
                    radius = size.width * 0.62f
                ),
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glossAlpha * 0.72f),
                        Color.Transparent,
                        Color.Black.copy(alpha = depthAlpha * 0.68f)
                    )
                ),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, color = Color.White.copy(alpha = 0.95f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BackdropCrop(
    coordinateSource: GlassCoordinateSource,
    backdropAlpha: Float,
    modifier: Modifier = Modifier
) {
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current

    Canvas(modifier = modifier) {
        frameTicker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        if (cachedBackdrop != null) {
            val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
            val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
            val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.width - srcX)
            val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.height - srcY)
            drawImage(
                image = cachedBackdrop.image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = backdropAlpha,
                blendMode = BlendMode.SrcOver
            )
        } else {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A2B58).copy(alpha = backdropAlpha),
                        Color(0xFF5B4A8E).copy(alpha = backdropAlpha),
                        Color(0xFFB85D78).copy(alpha = backdropAlpha)
                    )
                )
            )
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
private fun GlassPanelSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.050f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
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
