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
import androidx.compose.foundation.layout.size
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

    var insetRadius by rememberSaveable { mutableStateOf(22f) }
    var insetBackdropAlpha by rememberSaveable { mutableStateOf(0.94f) }
    var insetDepthAlpha by rememberSaveable { mutableStateOf(0.30f) }
    var insetHighlightAlpha by rememberSaveable { mutableStateOf(0.16f) }
    var insetCenterDimAlpha by rememberSaveable { mutableStateOf(0.18f) }

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
        GlassLabMiniTitle("凹槽玻璃", "用于输入框、搜索框、滑杆轨道，重点是内凹和压暗。")
        InsetGlassPanel(
            radius = insetRadius,
            backdropAlpha = insetBackdropAlpha,
            depthAlpha = insetDepthAlpha,
            highlightAlpha = insetHighlightAlpha,
            centerDimAlpha = insetCenterDimAlpha,
            modifier = Modifier.fillMaxWidth().height(116.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("▸", color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(0.58f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.34f)))
                    }
                    Text("58", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape((insetRadius * 0.78f).dp)).background(Color.Black.copy(alpha = 0.11f)), contentAlignment = Alignment.CenterStart) {
                    Text("向 AI 助理提问...", color = Color.White.copy(alpha = 0.42f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        GlassPanelSlider("圆角", "凹槽圆角半径", insetRadius, 10f..36f) { insetRadius = it }
        GlassPanelSlider("背景模糊层", "凹槽内部的模糊采样", insetBackdropAlpha, 0f..1f) { insetBackdropAlpha = it }
        GlassPanelSlider("内凹暗度", "边缘向内压暗的深度", insetDepthAlpha, 0f..0.80f) { insetDepthAlpha = it }
        GlassPanelSlider("内侧高光", "上沿和内壁的弱亮边", insetHighlightAlpha, 0f..0.50f) { insetHighlightAlpha = it }
        GlassPanelSlider("中心压暗", "输入区域整体沉入感", insetCenterDimAlpha, 0f..0.55f) { insetCenterDimAlpha = it }

        GlassLabDivider()
        GlassLabMiniTitle("水滴玻璃", "用于发送、AI 助理、语音输入等凸起按钮。")
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
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = frostAlpha.coerceIn(0f, 0.85f))))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.65f))))
        content()
    }
}

@Composable
private fun InsetGlassPanel(
    modifier: Modifier = Modifier,
    radius: Float,
    backdropAlpha: Float,
    depthAlpha: Float,
    highlightAlpha: Float,
    centerDimAlpha: Float,
    content: @Composable () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates.coordinates = it }
            .clip(shape)
    ) {
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = centerDimAlpha.coerceIn(0f, 0.70f))))
        Canvas(Modifier.matchParentSize()) {
            val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val w = size.width
            val h = size.height
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = depthAlpha * 0.92f),
                        Color.Transparent,
                        Color.White.copy(alpha = highlightAlpha * 0.26f)
                    )
                ),
                cornerRadius = corner,
                blendMode = BlendMode.SrcOver
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = depthAlpha),
                        Color.Black.copy(alpha = depthAlpha * 0.18f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(w - 4.dp.toPx(), h - 4.dp.toPx()),
                cornerRadius = corner,
                style = Stroke(width = 5.dp.toPx()),
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha),
                        Color.White.copy(alpha = highlightAlpha * 0.18f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
                cornerRadius = corner,
                style = Stroke(width = 1.2.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        content()
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
        BackdropCrop(coordinateSource = coordinates, backdropAlpha = backdropAlpha.coerceIn(0f, 1f), modifier = Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
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
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
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
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White.copy(alpha = 0.95f),
                activeTrackColor = Color(0xFF8DF9EA).copy(alpha = 0.54f),
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

private fun Float.formatGlassPanelValue(): String = "${((this * 100).roundToInt() / 100f)}"
