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
    var insetInnerShadow by rememberSaveable { mutableStateOf(0.67f) }
    var insetFloorDim by rememberSaveable { mutableStateOf(0.23f) }
    var dropletBodyBulge by rememberSaveable { mutableStateOf(44f) }
    var dropletEdgePull by rememberSaveable { mutableStateOf(120f) }
    var dropletEdgeWidth by rememberSaveable { mutableStateOf(32f) }
    var dropletLensMix by rememberSaveable { mutableStateOf(0.92f) }
    var dropletDrag by rememberSaveable { mutableStateOf(2.0f) }
    var dropletBottomGlow by rememberSaveable { mutableStateOf(1.48f) }
    var dropletTopGloss by rememberSaveable { mutableStateOf(0.53f) }
    var dropletCornerGloss by rememberSaveable { mutableStateOf(1.03f) }
    var dropletInnerDark by rememberSaveable { mutableStateOf(0.65f) }
    var dropletAlpha by rememberSaveable { mutableStateOf(0.63f) }
    var dropletDebugMask by rememberSaveable { mutableStateOf(0f) }
    var dropletShadowAlpha by rememberSaveable { mutableStateOf(0.18f) }
    var dropletShadowOffsetX by rememberSaveable { mutableStateOf(3.0f) }
    var dropletShadowOffsetY by rememberSaveable { mutableStateOf(7.0f) }
    var dropletShadowSoftness by rememberSaveable { mutableStateOf(18f) }
    var dropletActiveGlow by rememberSaveable { mutableStateOf(0.73f) }
    var dropletActiveRefraction by rememberSaveable { mutableStateOf(4.0f) }
    var dropletActiveRimRefraction by rememberSaveable { mutableStateOf(3.16f) }
    var dropletActiveLightY by rememberSaveable { mutableStateOf(1.25f) }
    var dropletActiveEntryHeight by rememberSaveable { mutableStateOf(0.04f) }
    var dropletActiveLightThickness by rememberSaveable { mutableStateOf(0.22f) }
    var dropletActiveHotspot by rememberSaveable { mutableStateOf(1.27f) }
    var dropletActiveVolumeWarmth by rememberSaveable { mutableStateOf(0.14f) }
    var dropletActiveRimGather by rememberSaveable { mutableStateOf(1.21f) }
    var dropletActiveRimFlow by rememberSaveable { mutableStateOf(0.89f) }
    var dropletActiveLightX by rememberSaveable { mutableStateOf(1.0f) }
    var dropletActiveLightSpread by rememberSaveable { mutableStateOf(0.70f) }
    var dropletActiveEntryPearl by rememberSaveable { mutableStateOf(1.88f) }
    var dropletActiveRimPearl by rememberSaveable { mutableStateOf(1.35f) }
    var dropletActiveCenterClear by rememberSaveable { mutableStateOf(0.42f) }
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
        debugMaskAlpha = dropletDebugMask,
        activeGlow = dropletActiveGlow,
        activeRefraction = dropletActiveRefraction,
        activeRimRefraction = dropletActiveRimRefraction,
        activeLightY = dropletActiveLightY,
        activeLightThickness = dropletActiveLightThickness,
        activeHotspot = dropletActiveHotspot,
        activeVolumeWarmth = dropletActiveVolumeWarmth,
        activeRimGather = dropletActiveRimGather,
        activeRimFlow = dropletActiveRimFlow,
        activeLightX = dropletActiveLightX,
        activeLightSpread = dropletActiveLightSpread,
        activeEntryPearl = dropletActiveEntryPearl,
        activeRimPearl = dropletActiveRimPearl,
        activeCenterClear = dropletActiveCenterClear
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassLabMiniTitle("雾面信息玻璃", "只裁剪背景模糊层，不叠边框、高光和折射。")
        FrostInfoGlassPanel(radius = frostRadius, backdropAlpha = frostBackdropAlpha, frostAlpha = frostAlpha, dimAlpha = frostDimAlpha, modifier = Modifier.fillMaxWidth().height(132.dp)) {
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
            ApprovedInsetGlassSlot(radius = insetRadius, grooveDepth = insetDepth, floorBackdropAlpha = insetBackdropAlpha, rimHighlightAlpha = insetRimHighlight, innerShadowAlpha = insetInnerShadow, floorDimAlpha = insetFloorDim, modifier = Modifier.fillMaxWidth().height(38.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("▸", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    RecessedProgressTrack(progress = 0.58f, modifier = Modifier.weight(1f).height(12.dp))
                    Text("58", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            ApprovedInsetGlassSlot(radius = insetRadius, grooveDepth = insetDepth, floorBackdropAlpha = insetBackdropAlpha, rimHighlightAlpha = insetRimHighlight, innerShadowAlpha = insetInnerShadow, floorDimAlpha = insetFloorDim, modifier = Modifier.fillMaxWidth().height(38.dp)) {
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
        GlassLabMiniTitle("OpenGL 横向水滴", "选中光已改为连续内部光路：外圈任意方向入光 → 体积折射 → 边缘捕光。")
        OpenGlLargeDropletPreview(style = dropletStyle, shadowAlpha = dropletShadowAlpha, shadowOffsetX = dropletShadowOffsetX, shadowOffsetY = dropletShadowOffsetY, shadowSoftness = dropletShadowSoftness, activeGlow = dropletActiveGlow, backgroundGlow = dropletBackgroundGlow, outerGlow = dropletOuterGlow, warmGlow = dropletWarmGlow, modifier = Modifier.fillMaxWidth().height(96.dp))
        GlassPanelSlider("选中发光", "整条内部光路的总能量", dropletActiveGlow, 0f..1.5f) { dropletActiveGlow = it }
        GlassPanelSlider("体积折射", "内部光源被水滴体积连续扭曲的强度", dropletActiveRefraction, 0f..4f) { dropletActiveRefraction = it }
        GlassPanelSlider("边缘折射", "边缘捕获入射光的强度", dropletActiveRimRefraction, 0f..4f) { dropletActiveRimRefraction = it }
        GlassPanelSlider("入光方向", "沿水滴外圈旋转：左、上、右、下都可入射", dropletActiveLightX, 0f..1f) { dropletActiveLightX = it }
        GlassPanelSlider("入光宽度", "从局部一束光扩展到大面积边缘入光", dropletActiveLightSpread, 0f..1f) { dropletActiveLightSpread = it }
        GlassPanelSlider("入光半径", "入光点离中心的距离，越大越贴近外边缘", dropletActiveLightY, 0.45f..1.25f) { dropletActiveLightY = it }
        GlassPanelSlider("入光高度", "入射光源进入水滴内部后的独立高度，不等同于入光半径", dropletActiveEntryHeight, 0f..0.55f) { dropletActiveEntryHeight = it }
        GlassPanelSlider("光源厚度", "入射光束本身的厚度", dropletActiveLightThickness, 0.025f..0.30f) { dropletActiveLightThickness = it }
        GlassPanelSlider("入光强度", "入射点核心亮度", dropletActiveHotspot, 0f..2f) { dropletActiveHotspot = it }
        GlassPanelSlider("入光辉光", "入射点外缘的水珠式月牙辉光", dropletActiveEntryPearl, 0f..3f) { dropletActiveEntryPearl = it }
        GlassPanelSlider("边缘珠光", "暂时关闭输出，保留滑块占位", dropletActiveRimPearl, 0f..3f) { dropletActiveRimPearl = it }
        GlassPanelSlider("中心通透", "压住中部均匀染紫，让中心更清透", dropletActiveCenterClear, 0f..1f) { dropletActiveCenterClear = it }
        GlassPanelSlider("体积染色", "中部暖色透射雾感", dropletActiveVolumeWarmth, 0f..1.2f) { dropletActiveVolumeWarmth = it }
        GlassPanelSlider("边缘捕光", "入射光被厚边吸住的可见强度", dropletActiveRimGather, 0f..2.5f) { dropletActiveRimGather = it }
        GlassPanelSlider("边缘流动", "入射光沿左右圆角流动的范围", dropletActiveRimFlow, 0f..1.5f) { dropletActiveRimFlow = it }
        GlassPanelSlider("背景点亮", "按钮背后的环境被照亮", dropletBackgroundGlow, 0f..1.5f) { dropletBackgroundGlow = it }
        GlassPanelSlider("外圈辉光", "水滴外侧的柔和光晕", dropletOuterGlow, 0f..1.5f) { dropletOuterGlow = it }
        GlassPanelSlider("底部暖光", "Compose 外层底部泛光", dropletWarmGlow, 0f..1.5f) { dropletWarmGlow = it }
        GlassPanelSlider("遮罩调试", "拉高后只显示 OpenGL 自己的真实遮罩", dropletDebugMask, 0f..1f) { dropletDebugMask = it }
        GlassPanelSlider("主体放大", "水滴透镜放大底部图像", dropletBodyBulge, -12f..72f) { dropletBodyBulge = it }
        GlassPanelSlider("边缘压缩", "厚边拉动并压缩背景", dropletEdgePull, -40f..120f) { dropletEdgePull = it }
        GlassPanelSlider("边缘宽度", "折射、高光和拖色宽度", dropletEdgeWidth, 2f..32f) { dropletEdgeWidth = it }
        GlassPanelSlider("清晰混入", "放大后的清晰背景参与", dropletLensMix, 0f..1f) { dropletLensMix = it }
        GlassPanelSlider("拖色强度", "边缘从背景吸色", dropletDrag, 0f..2f) { dropletDrag = it }
        GlassPanelSlider("底部焦散", "旧底边背景拖色强度", dropletBottomGlow, 0f..2f) { dropletBottomGlow = it }
        GlassPanelSlider("顶部反光", "上沿真实光亮", dropletTopGloss, 0f..1.8f) { dropletTopGloss = it }
        GlassPanelSlider("角部反光", "右上角弧形亮斑", dropletCornerGloss, 0f..2f) { dropletCornerGloss = it }
        GlassPanelSlider("内部暗边", "背光侧和底部厚边阴影", dropletInnerDark, 0f..1f) { dropletInnerDark = it }
        GlassPanelSlider("整体透明", "OpenGL 水滴层透明度", dropletAlpha, 0f..1f) { dropletAlpha = it }
        GlassPanelSlider("接触阴影", "水滴贴在背景上的软阴影", dropletShadowAlpha, 0f..0.75f) { dropletShadowAlpha = it }
        GlassPanelSlider("阴影 X", "阴影向右偏移", dropletShadowOffsetX, -12f..18f) { dropletShadowOffsetX = it }
        GlassPanelSlider("阴影 Y", "阴影向下偏移", dropletShadowOffsetY, -6f..22f) { dropletShadowOffsetY = it }
        GlassPanelSlider("阴影扩散", "阴影边缘柔化范围", dropletShadowSoftness, 2f..36f) { dropletShadowSoftness = it }
    }
}

@Composable
private fun OpenGlLargeDropletPreview(style: DropletGlassStyle, shadowAlpha: Float, shadowOffsetX: Float, shadowOffsetY: Float, shadowSoftness: Float, activeGlow: Float, backgroundGlow: Float, outerGlow: Float, warmGlow: Float, modifier: Modifier = Modifier) {
    val coordinates = remember { GlassCoordinateSource() }
    val debug = style.debugMaskAlpha.coerceIn(0f, 1f)
    Box(modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        if (debug <= 0.001f) {
            DropletBackgroundGlow(activeGlow, backgroundGlow, outerGlow, warmGlow, Modifier.fillMaxWidth().height(78.dp))
            DropletContactShadow(shadowAlpha, shadowOffsetX, shadowOffsetY, shadowSoftness, Modifier.fillMaxWidth().height(70.dp))
        }
        Box(modifier = Modifier.fillMaxWidth().height(62.dp).padding(vertical = 3.dp).onGloballyPositioned { coordinates.coordinates = it }.clip(RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) {
            OpenGLDropletGlassLayer(radius = 999, coordinateSource = coordinates, style = style, modifier = Modifier.fillMaxSize())
            if (debug <= 0.001f) {
                DropletActiveOverlay(activeGlow, warmGlow, Modifier.fillMaxSize())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Text("🎙", color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Text("语音输入", color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DropletBackgroundGlow(activeGlow: Float, backgroundGlow: Float, outerGlow: Float, warmGlow: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val bg = backgroundGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val pillHeight = size.height * 0.58f
        val pillTop = size.height * 0.20f
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFBFEAFF).copy(alpha = bg * active * 0.18f), Color(0xFF6CCBFF).copy(alpha = bg * active * 0.08f), Color.Transparent), Offset(size.width * 0.48f, size.height * 0.42f), size.width * 0.58f), Offset(size.width * 0.03f, pillTop - size.height * 0.12f), Size(size.width * 0.94f, pillHeight + size.height * 0.24f), CornerRadius(size.height * 0.42f, size.height * 0.42f), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF72B7).copy(alpha = warm * active * 0.22f), Color(0xFFFF9B6F).copy(alpha = warm * active * 0.08f), Color.Transparent), Offset(size.width * 0.46f, size.height * 0.76f), size.width * 0.48f), Offset(size.width * 0.05f, size.height * 0.42f), Size(size.width * 0.90f, size.height * 0.54f), CornerRadius(size.height * 0.32f, size.height * 0.32f), blendMode = BlendMode.Screen)
    }
}

@Composable
private fun DropletActiveOverlay(activeGlow: Float, warmGlow: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val radius = size.height / 2f
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = active * 0.18f), Color.White.copy(alpha = active * 0.035f), Color.Transparent)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF73C5).copy(alpha = active * warm * 0.22f), Color(0xFFFFB06C).copy(alpha = active * warm * 0.055f), Color.Transparent), Offset(size.width * 0.50f, size.height * 1.03f), size.width * 0.42f), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
    }
}

@Composable
private fun DropletContactShadow(alpha: Float, offsetX: Float, offsetY: Float, softness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dx = offsetX.dp.toPx()
        val dy = offsetY.dp.toPx()
        val blur = softness.dp.toPx()
        val coreAlpha = alpha.coerceIn(0f, 1f)
        val shadowHeight = (size.height * 0.42f + blur * 0.25f).coerceAtLeast(6f)
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = coreAlpha * 0.38f), Color.Black.copy(alpha = coreAlpha * 0.12f), Color.Transparent), Offset(size.width * 0.52f + dx, size.height * 0.62f + dy), size.width * 0.58f + blur), Offset(dx + size.width * 0.10f, dy + size.height * 0.40f - blur * 0.12f), Size(size.width * 0.80f, shadowHeight), blendMode = BlendMode.Multiply)
    }
}

@Composable
fun FrostInfoGlassPanel(modifier: Modifier = Modifier, radius: Float = 17.44f, backdropAlpha: Float = 1f, frostAlpha: Float = 0f, dimAlpha: Float = 0f, content: @Composable () -> Unit) {
    val coordinates = remember { GlassCoordinateSource() }
    Box(modifier = modifier.onGloballyPositioned { coordinates.coordinates = it }.clip(RoundedCornerShape(radius.dp))) {
        BackdropCrop(coordinates, backdropAlpha.coerceIn(0f, 1f), Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = frostAlpha.coerceIn(0f, 0.85f))))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.65f))))
        content()
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
            drawImage(backdrop.image, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset.Zero, IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)), alpha = backdropAlpha.coerceIn(0f, 1f), blendMode = BlendMode.SrcOver)
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
private fun GlassLabDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f))) }

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
    SampleRecessedSlider(title = title, subtitle = subtitle, value = clamped, range = range, valueText = clamped.formatGlassPanelValue(), onValueChange = onValueChange)
}

private fun Float.formatGlassPanelValue(): String = "${((this * 100).roundToInt() / 100f)}"
