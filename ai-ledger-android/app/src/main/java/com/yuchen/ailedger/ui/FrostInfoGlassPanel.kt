package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val DropletPrismTau = 6.2831855f
private val DropletPressEasing = CubicBezierEasing(0.10f, 0.00f, 0.05f, 1.00f)
private val DropletLightEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val DropletReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)

@Composable
fun GlassPanelLabSection(state: AssistantUiState, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    val clickSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF22304A).copy(alpha = 0.36f),
                        Color(0xFF09111F).copy(alpha = 0.42f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = if (expanded) 0.085f else 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .clickable(interactionSource = clickSource, indication = null) { expanded = !expanded }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text("玻璃面板", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("雾面 / 凹槽 / 水滴参数", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FrostInfoGlassLab(state)
            }
        }
    }
}

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
    var dropletActiveGlow by rememberSaveable { mutableStateOf(0.53f) }
    var dropletActiveRefraction by rememberSaveable { mutableStateOf(4.0f) }
    var dropletActiveRimRefraction by rememberSaveable { mutableStateOf(3.16f) }
    var dropletActiveLightX by rememberSaveable { mutableStateOf(1.0f) }
    var dropletActiveLightSpread by rememberSaveable { mutableStateOf(0.70f) }
    var dropletActiveLightY by rememberSaveable { mutableStateOf(1.25f) }
    var dropletActiveEntryHeight by rememberSaveable { mutableStateOf(0.04f) }
    var dropletActiveLightThickness by rememberSaveable { mutableStateOf(0.22f) }
    var dropletActiveHotspot by rememberSaveable { mutableStateOf(1.27f) }
    var dropletActiveEntryPearl by rememberSaveable { mutableStateOf(1.88f) }
    var dropletActiveRimPearl by rememberSaveable { mutableStateOf(1.35f) }
    var dropletActiveCenterClear by rememberSaveable { mutableStateOf(0.42f) }
    var dropletActiveVolumeWarmth by rememberSaveable { mutableStateOf(0.14f) }
    var dropletActiveRimGather by rememberSaveable { mutableStateOf(1.21f) }
    var dropletActiveRimFlow by rememberSaveable { mutableStateOf(0.89f) }
    var dropletBackgroundGlow by rememberSaveable { mutableStateOf(0.38f) }
    var dropletOuterGlow by rememberSaveable { mutableStateOf(0.46f) }
    var dropletWarmGlow by rememberSaveable { mutableStateOf(0.54f) }
    var dropletPrismStrength by rememberSaveable { mutableStateOf(2.76f) }
    var dropletPurpleWhiteLight by rememberSaveable { mutableStateOf(0.67f) }

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
        activeEntryHeight = dropletActiveEntryHeight,
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
            LabInsetGlassSlot(radius = insetRadius, grooveDepth = insetDepth, floorBackdropAlpha = insetBackdropAlpha, rimHighlightAlpha = insetRimHighlight, innerShadowAlpha = insetInnerShadow, floorDimAlpha = insetFloorDim, modifier = Modifier.fillMaxWidth().height(38.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("▸", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    RecessedProgressTrack(progress = 0.58f, modifier = Modifier.weight(1f).height(12.dp))
                    Text("58", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            LabInsetGlassSlot(radius = insetRadius, grooveDepth = insetDepth, floorBackdropAlpha = insetBackdropAlpha, rimHighlightAlpha = insetRimHighlight, innerShadowAlpha = insetInnerShadow, floorDimAlpha = insetFloorDim, modifier = Modifier.fillMaxWidth().height(38.dp)) {
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
        GlassLabMiniTitle("OpenGL 横向水滴", "按住点亮；棱彩只保留雾化光泽，不画曲线、直线或外圈霓虹。")
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
            prismStrength = dropletPrismStrength,
            purpleWhiteLight = dropletPurpleWhiteLight,
            modifier = Modifier.fillMaxWidth().height(112.dp)
        )
        GlassPanelSlider("棱彩强度", "控制柔和彩虹扫光、内部多色折射和低强度外圈辉光", dropletPrismStrength, 0f..3f) { dropletPrismStrength = it }
        GlassPanelSlider("紫白底光", "控制原来的紫白入射光和白色表面高光", dropletPurpleWhiteLight, 0f..1.5f) { dropletPurpleWhiteLight = it }
        GlassPanelSlider("选中发光", "整条内部光路的总能量", dropletActiveGlow, 0f..1.5f) { dropletActiveGlow = it }
        GlassPanelSlider("体积折射", "内部光源被水滴体积连续扭曲的强度", dropletActiveRefraction, 0f..4f) { dropletActiveRefraction = it }
        GlassPanelSlider("边缘折射", "边缘捕获入射光的强度", dropletActiveRimRefraction, 0f..4f) { dropletActiveRimRefraction = it }
        GlassPanelSlider("入光方向", "按压位置也会临时控制入光点", dropletActiveLightX, 0f..1f) { dropletActiveLightX = it }
        GlassPanelSlider("入光宽度", "从局部一束光扩展到大面积边缘入光", dropletActiveLightSpread, 0f..1f) { dropletActiveLightSpread = it }
        GlassPanelSlider("入光半径", "入光点离中心的距离，越大越贴近外边缘", dropletActiveLightY, 0.45f..1.25f) { dropletActiveLightY = it }
        GlassPanelSlider("入光高度", "入射光源进入水滴内部后的独立高度", dropletActiveEntryHeight, 0f..0.55f) { dropletActiveEntryHeight = it }
        GlassPanelSlider("光源厚度", "入射光束本身的厚度", dropletActiveLightThickness, 0.025f..0.30f) { dropletActiveLightThickness = it }
        GlassPanelSlider("入光强度", "入射点核心亮度", dropletActiveHotspot, 0f..2f) { dropletActiveHotspot = it }
        GlassPanelSlider("入光辉光", "入射点外缘的水珠式辉光", dropletActiveEntryPearl, 0f..3f) { dropletActiveEntryPearl = it }
        GlassPanelSlider("边缘珠光", "边缘柔光参数", dropletActiveRimPearl, 0f..3f) { dropletActiveRimPearl = it }
        GlassPanelSlider("中心通透", "压住中部泛白，让中心更清透", dropletActiveCenterClear, 0f..1f) { dropletActiveCenterClear = it }
        GlassPanelSlider("体积染色", "中部暖色透射雾感", dropletActiveVolumeWarmth, 0f..1.2f) { dropletActiveVolumeWarmth = it }
        GlassPanelSlider("边缘捕光", "入射光被厚边吸住的可见强度", dropletActiveRimGather, 0f..2.5f) { dropletActiveRimGather = it }
        GlassPanelSlider("边缘流动", "入射光沿左右圆角流动的范围", dropletActiveRimFlow, 0f..1.5f) { dropletActiveRimFlow = it }
        GlassPanelSlider("背景点亮", "按钮背后的环境被照亮", dropletBackgroundGlow, 0f..1.5f) { dropletBackgroundGlow = it }
        GlassPanelSlider("外圈辉光", "水滴外侧的柔和光晕", dropletOuterGlow, 0f..1.5f) { dropletOuterGlow = it }
        GlassPanelSlider("底部暖光", "Compose 外层底部泛光", dropletWarmGlow, 0f..1.5f) { dropletWarmGlow = it }
        GlassPanelSlider("遮罩调试", "0正常；0.25主光路；0.50边缘捕光；0.75体积染色", dropletDebugMask, 0f..1f) { dropletDebugMask = it }
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
    prismStrength: Float,
    purpleWhiteLight: Float,
    modifier: Modifier = Modifier
) {
    val coordinates = remember { GlassCoordinateSource() }
    val pressAnim = remember { Animatable(0f) }
    val latchAnim = remember { Animatable(0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val breathAnim = remember { Animatable(0f) }
    val driftAnim = remember { Animatable(0f) }
    val shimmerAnim = remember { Animatable(0.35f) }
    val scope = rememberCoroutineScope()
    var locked by rememberSaveable { mutableStateOf(false) }
    var capsuleSize by remember { mutableStateOf(Size(1f, 1f)) }
    var interactionLightX by remember { mutableStateOf(style.activeLightX.coerceIn(0.04f, 0.96f)) }

    LaunchedEffect(locked) {
        if (locked) {
            latchAnim.stop()
            if (latchAnim.value < 0.18f) latchAnim.snapTo(0.18f)
            latchAnim.animateTo(0.72f, tween(170, easing = DropletLightEasing))
            latchAnim.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
        } else {
            latchAnim.stop()
            latchAnim.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    val pressValue = pressAnim.value.coerceIn(-0.28f, 1.35f)
    val pressPositive = pressValue.coerceAtLeast(0f)
    val recoilValue = (-pressValue).coerceAtLeast(0f)
    val latchValue = latchAnim.value.coerceIn(0f, 1f)
    val afterglowValue = afterglowAnim.value.coerceIn(0f, 1f)
    val activeForBreath = locked || pressPositive > 0.05f

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                breathAnim.animateTo(Random.nextFloat(), tween(950 + Random.nextInt(850), easing = FastOutSlowInEasing))
            }
        } else {
            breathAnim.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                driftAnim.animateTo(Random.nextFloat() * 0.34f - 0.17f, tween(1200 + Random.nextInt(1300), easing = FastOutSlowInEasing))
            }
        } else {
            driftAnim.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                shimmerAnim.animateTo(0.22f + Random.nextFloat() * 0.78f, tween(520 + Random.nextInt(980), easing = FastOutSlowInEasing))
            }
        } else {
            shimmerAnim.animateTo(0.18f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    val breathValue = breathAnim.value.coerceIn(0f, 1f)
    val driftValue = driftAnim.value.coerceIn(-0.22f, 0.22f)
    val shimmerValue = shimmerAnim.value.coerceIn(0f, 1f)
    val prismAmount = prismStrength.coerceIn(0f, 3f)
    val purpleAmount = purpleWhiteLight.coerceIn(0f, 1.5f)
    val purpleMix = (purpleAmount / 1.5f).coerceIn(0f, 1f)
    val breathEnergy = 0.88f + breathValue * 0.14f + shimmerValue * 0.08f
    val holdEnergy = maxOf(pressPositive, latchValue)
    val lightEnergy = (holdEnergy * breathEnergy + afterglowValue * 0.55f).coerceIn(0f, 1.30f)
    val refractionEnergy = (holdEnergy * (0.92f + breathValue * 0.10f + shimmerValue * 0.08f) + afterglowValue * 0.40f).coerceIn(0f, 1.18f)
    val effectiveLightX = (interactionLightX + driftValue * holdEnergy).coerceIn(0.04f, 0.96f)
    val prismPhase = effectiveLightX * 0.80f + breathValue * 0.21f + shimmerValue * 0.19f + afterglowValue * 0.17f
    val prismMix = (lightEnergy * prismAmount * 0.80f).coerceIn(0f, 1f)
    val prismRed = 0.36f + dropletPrismChannel(prismPhase + 0.00f) * 0.64f
    val prismGreen = 0.38f + dropletPrismChannel(prismPhase + 0.34f) * 0.62f
    val prismBlue = 0.50f + dropletPrismChannel(prismPhase + 0.68f) * 0.50f
    val warmRed = 0.70f + dropletPrismChannel(prismPhase + 0.08f) * 0.30f
    val warmGreen = 0.30f + dropletPrismChannel(prismPhase + 0.28f) * 0.62f
    val warmBlue = 0.58f + dropletPrismChannel(prismPhase + 0.58f) * 0.42f
    val debug = style.debugMaskAlpha.coerceIn(0f, 1f)
    val animatedStyle = style.copy(
        bodyBulgePx = style.bodyBulgePx + pressPositive * 14.0f + latchValue * 5.0f - afterglowValue * 3.0f,
        edgePullPx = style.edgePullPx + pressPositive * 32.0f + latchValue * 12.0f + afterglowValue * 9.0f,
        edgeWidthPx = style.edgeWidthPx + pressPositive * 5.8f + latchValue * 2.8f,
        bottomGlow = style.bottomGlow * (0.68f + lightEnergy * (0.20f + purpleMix * 0.18f + prismAmount * 0.08f)),
        topGloss = style.topGloss + lightEnergy * (0.12f + purpleMix * 0.22f + prismAmount * 0.10f),
        cornerGloss = style.cornerGloss + lightEnergy * (0.10f + purpleMix * 0.18f) + prismAmount * shimmerValue * lightEnergy * 0.12f,
        innerDark = (style.innerDark + pressPositive * 0.10f - lightEnergy * 0.08f).coerceIn(0f, 1f),
        alpha = (style.alpha + lightEnergy * 0.13f).coerceIn(0f, 1f),
        activeGlow = style.activeGlow * lightEnergy * (0.50f + purpleMix * 0.28f + prismAmount * 0.16f),
        activeRefraction = style.activeRefraction * refractionEnergy,
        activeRimRefraction = style.activeRimRefraction * refractionEnergy * (1f + prismAmount * 0.12f),
        activeLightX = dropletLerp(style.activeLightX.coerceIn(0f, 1f), effectiveLightX, holdEnergy.coerceIn(0f, 1f)),
        activeLightSpread = (style.activeLightSpread * (0.50f + lightEnergy * 0.52f) + pressPositive * 0.12f + prismAmount * 0.035f).coerceIn(0f, 1f),
        activeLightThickness = (style.activeLightThickness * (0.58f + lightEnergy * 0.46f + prismAmount * 0.06f)).coerceIn(0.015f, 0.42f),
        activeHotspot = style.activeHotspot * (lightEnergy * (0.35f + purpleMix * 0.45f + prismAmount * 0.16f) + shimmerValue * holdEnergy * 0.18f),
        activeEntryPearl = style.activeEntryPearl * (lightEnergy * (0.30f + purpleMix * 0.44f + prismAmount * 0.16f) + pressPositive * 0.12f),
        activeRimPearl = style.activeRimPearl * (lightEnergy * (0.25f + purpleMix * 0.32f + prismAmount * 0.24f) + shimmerValue * holdEnergy * 0.12f),
        activeCenterClear = (style.activeCenterClear + lightEnergy * 0.24f).coerceIn(0f, 1f),
        activeVolumeWarmth = style.activeVolumeWarmth * (0.24f + lightEnergy * (purpleMix * 0.36f + prismAmount * 0.22f)),
        activeRimGather = style.activeRimGather * (lightEnergy + afterglowValue * 0.22f + prismAmount * shimmerValue * 0.07f),
        activeRimFlow = style.activeRimFlow * (0.42f + lightEnergy * 0.88f + prismAmount * 0.08f),
        accentRed = dropletLerp(style.accentRed, prismRed, prismMix),
        accentGreen = dropletLerp(style.accentGreen, prismGreen, prismMix),
        accentBlue = dropletLerp(style.accentBlue, prismBlue, prismMix),
        warmRed = dropletLerp(style.warmRed, warmRed, prismMix),
        warmGreen = dropletLerp(style.warmGreen, warmGreen, prismMix),
        warmBlue = dropletLerp(style.warmBlue, warmBlue, prismMix)
    )
    val animatedPurpleWhiteGlow = activeGlow * lightEnergy * purpleAmount
    val animatedPrismGlow = activeGlow * lightEnergy * (0.34f + prismAmount * 0.64f).coerceIn(0f, 2.2f)
    val animatedBackgroundGlow = backgroundGlow * (lightEnergy + recoilValue * 0.35f + prismAmount * lightEnergy * 0.16f).coerceIn(0f, 1.8f)
    val animatedOuterGlow = outerGlow * (lightEnergy + afterglowValue * 0.28f + recoilValue * 0.30f + prismAmount * lightEnergy * 0.22f).coerceIn(0f, 1.9f)
    val animatedWarmGlow = warmGlow * (lightEnergy * (0.20f + purpleMix * 0.46f + prismAmount * 0.18f) + pressPositive * 0.12f).coerceIn(0f, 1.8f)
    val contentAlpha = if (debug <= 0.001f) (0.50f + lightEnergy * 0.36f + recoilValue * 0.10f).coerceIn(0.44f, 0.98f) else 0f
    val statusLabel = if (locked) "锁定棱彩" else "按住点亮 · 轻点锁定"

    Box(modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        if (debug <= 0.001f) {
            DropletBackgroundGlow(animatedPrismGlow, animatedBackgroundGlow, animatedOuterGlow, animatedWarmGlow, prismAmount, prismPhase, Modifier.fillMaxWidth().height(90.dp))
            DropletContactShadow(
                alpha = shadowAlpha * (0.72f + pressPositive * 0.62f + afterglowValue * 0.32f),
                offsetX = shadowOffsetX + pressPositive * 2.2f + recoilValue * if (effectiveLightX > 0.5f) 1.2f else -1.2f,
                offsetY = shadowOffsetY + pressPositive * 4.4f - recoilValue * 1.1f,
                softness = shadowSoftness + lightEnergy * 5.5f + recoilValue * 4.0f,
                modifier = Modifier.fillMaxWidth().height(82.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(vertical = 2.dp)
                .onSizeChanged { capsuleSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat()) }
                .onGloballyPositioned { coordinates.coordinates = it }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(effectiveLightX.coerceIn(0f, 1f), 0.50f)
                    scaleX = 1f + pressPositive * 0.066f + latchValue * 0.018f - afterglowValue * 0.006f - recoilValue * 0.020f
                    scaleY = 1f - pressPositive * 0.074f + afterglowValue * 0.014f + recoilValue * 0.026f
                    translationX = (effectiveLightX - 0.5f) * pressPositive * 5.8f + recoilValue * if (effectiveLightX > 0.5f) 3.0f else -3.0f
                    translationY = pressPositive * 5.2f - afterglowValue * 1.1f - recoilValue * 1.8f
                    rotationZ = (effectiveLightX - 0.5f) * pressPositive * 0.72f + recoilValue * if (effectiveLightX > 0.5f) 0.36f else -0.36f
                }
                .pointerInput(style) {
                    awaitEachGesture {
                        fun updateLight(position: Offset) {
                            interactionLightX = (position.x / capsuleSize.width.coerceAtLeast(1f)).coerceIn(0.04f, 0.96f)
                        }
                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateLight(down.position)
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(0f)
                        }
                        scope.launch {
                            pressAnim.stop()
                            if (pressAnim.value < 0.24f) pressAnim.snapTo(0.24f)
                            pressAnim.animateTo(1.20f, tween(145, easing = DropletPressEasing))
                            pressAnim.animateTo(0.96f, spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow))
                        }
                        var releasedAt = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (tracked != null) {
                                updateLight(tracked.position)
                                if (!tracked.pressed) {
                                    releasedAt = tracked.uptimeMillis
                                    break
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        val wasTap = releasedAt - down.uptimeMillis < 260L
                        if (wasTap) locked = !locked
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(-0.22f, tween(130, easing = DropletReleaseEasing))
                            pressAnim.animateTo(0.08f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow))
                            pressAnim.animateTo(0f, tween(210, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(if (wasTap && locked) 0.42f else 0.78f)
                            afterglowAnim.animateTo(0f, tween(if (locked) 460 else 820, easing = FastOutSlowInEasing))
                        }
                    }
                }
                .clip(RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            OpenGLDropletGlassLayer(radius = 999, coordinateSource = coordinates, style = animatedStyle, modifier = Modifier.fillMaxSize())
            if (debug <= 0.001f) {
                DropletActiveOverlay(animatedPurpleWhiteGlow, animatedWarmGlow, purpleMix, Modifier.fillMaxSize())
                DropletPrismOverlay(animatedPrismGlow, lightEnergy, prismPhase, prismAmount, effectiveLightX, shimmerValue, Modifier.fillMaxSize())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Text("🎙", color = Color.White.copy(alpha = contentAlpha), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Text("语音输入", color = Color.White.copy(alpha = contentAlpha), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = statusLabel,
                    color = Color.White.copy(alpha = 0.40f + lightEnergy * 0.18f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 6.dp)
                        .background(Color.Black.copy(alpha = 0.13f + lightEnergy * 0.10f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun dropletLerp(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun dropletPrismChannel(phase: Float): Float {
    val wrapped = phase - phase.toInt()
    val value = 0.5f + 0.5f * sin((wrapped * DropletPrismTau).toDouble()).toFloat()
    return value.coerceIn(0f, 1f)
}

@Composable
private fun DropletBackgroundGlow(activeGlow: Float, backgroundGlow: Float, outerGlow: Float, warmGlow: Float, prismStrength: Float, phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val bg = backgroundGlow.coerceIn(0f, 2f)
        val outer = outerGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val prism = prismStrength.coerceIn(0f, 3f)
        val radius = size.height * 0.46f
        val drift = dropletPrismChannel(phase)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFBFEAFF).copy(alpha = bg * active * 0.18f), Color(0xFF6CCBFF).copy(alpha = bg * active * 0.06f), Color.Transparent), Offset(size.width * 0.50f, size.height * 0.42f), size.width * 0.62f), Offset(size.width * 0.02f, size.height * 0.08f), Size(size.width * 0.96f, size.height * 0.80f), CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF72B7).copy(alpha = warm * active * 0.08f), Color(0xFFFFB56F).copy(alpha = warm * active * 0.025f), Color.Transparent), Offset(size.width * 0.48f, size.height * 0.78f), size.width * 0.44f), Offset(size.width * 0.08f, size.height * 0.42f), Size(size.width * 0.84f, size.height * 0.42f), CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF4FA7).copy(alpha = outer * active * prism * 0.035f), Color(0xFF80FFD8).copy(alpha = outer * active * prism * 0.028f), Color(0xFF78A8FF).copy(alpha = outer * active * prism * 0.020f), Color.Transparent), Offset(size.width * (0.34f + drift * 0.32f), size.height * 0.34f), size.width * 0.68f), Offset(size.width * 0.03f, size.height * 0.10f), Size(size.width * 0.94f, size.height * 0.74f), CornerRadius(radius, radius), blendMode = BlendMode.Plus)
    }
}

@Composable
private fun DropletActiveOverlay(activeGlow: Float, warmGlow: Float, purpleMix: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val purple = purpleMix.coerceIn(0f, 1f)
        val radius = size.height / 2f
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = active * purple * 0.16f), Color.White.copy(alpha = active * purple * 0.024f), Color.Transparent)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF73C5).copy(alpha = active * warm * purple * 0.10f), Color(0xFFFFB06C).copy(alpha = active * warm * purple * 0.032f), Color.Transparent), Offset(size.width * 0.50f, size.height * 1.03f), size.width * 0.42f), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
    }
}

@Composable
private fun DropletPrismOverlay(activeGlow: Float, energy: Float, phase: Float, prismStrength: Float, lightX: Float, shimmer: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2.4f)
        val e = energy.coerceIn(0f, 1.35f)
        val prism = prismStrength.coerceIn(0f, 3f)
        if (active <= 0.001f || e <= 0.001f || prism <= 0.001f) return@Canvas
        val radius = size.height / 2f
        val sweep = (phase - phase.toInt()) * size.width * 0.66f
        val alpha = (active * e * prism).coerceIn(0f, 4.2f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF2E93).copy(alpha = alpha * 0.045f),
                    Color(0xFFFFD84D).copy(alpha = alpha * 0.038f),
                    Color(0xFF55FFD6).copy(alpha = alpha * 0.050f),
                    Color(0xFF4F89FF).copy(alpha = alpha * 0.046f),
                    Color(0xFFC05CFF).copy(alpha = alpha * 0.052f)
                ),
                start = Offset(-size.width * 0.42f + sweep, 0f),
                end = Offset(size.width * 1.04f + sweep, size.height)
            ),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha * (0.05f + shimmer * 0.02f)), Color(0xFF9EFFF0).copy(alpha = alpha * 0.03f), Color.Transparent),
                start = Offset(size.width * (lightX - 0.42f), size.height * 0.02f),
                end = Offset(size.width * (lightX + 0.40f), size.height * 0.28f)
            ),
            topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
            size = Size(size.width * 0.88f, size.height * 0.24f),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Screen
        )
        val edgeX = if (lightX > 0.5f) (lightX + 0.06f).coerceAtMost(0.92f) else (lightX - 0.06f).coerceAtLeast(0.08f)
        val edgeY = 0.42f + (shimmer - 0.5f) * 0.08f
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = alpha * 0.060f), Color(0xFFFFF4B5).copy(alpha = alpha * 0.045f), Color(0xFF63FFE4).copy(alpha = alpha * 0.040f), Color.Transparent),
                center = Offset(size.width * edgeX, size.height * edgeY),
                radius = size.width * (0.18f + shimmer * 0.10f)
            ),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color(0xFFFFF0A8).copy(alpha = alpha * 0.020f), Color(0xFF69FFE8).copy(alpha = alpha * 0.028f), Color.Transparent),
                start = Offset(size.width * 0.10f, size.height * 0.88f),
                end = Offset(size.width * 0.90f, size.height * 0.18f)
            ),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Plus
        )
    }
}

@Composable
private fun DropletContactShadow(alpha: Float, offsetX: Float, offsetY: Float, softness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dx = offsetX.dp.toPx()
        val dy = offsetY.dp.toPx()
        val blur = softness.dp.toPx()
        val coreAlpha = alpha.coerceIn(0f, 1f)
        val shadowHeight = (size.height * 0.44f + blur * 0.30f).coerceAtLeast(6f)
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = coreAlpha * 0.45f), Color.Black.copy(alpha = coreAlpha * 0.13f), Color.Transparent), Offset(size.width * 0.52f + dx, size.height * 0.62f + dy), size.width * 0.62f + blur), Offset(dx + size.width * 0.08f, dy + size.height * 0.40f - blur * 0.12f), Size(size.width * 0.84f, shadowHeight), blendMode = BlendMode.Multiply)
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
private fun LabInsetGlassSlot(radius: Float, grooveDepth: Float, floorBackdropAlpha: Float, rimHighlightAlpha: Float, innerShadowAlpha: Float, floorDimAlpha: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        FrostInfoGlassPanel(radius = radius, backdropAlpha = floorBackdropAlpha, dimAlpha = floorDimAlpha, modifier = Modifier.fillMaxSize()) {}
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val inset = (1.5f + grooveDepth * 6f).dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = innerShadowAlpha * 0.45f), Offset.Zero, size, r, blendMode = BlendMode.Multiply)
            drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = innerShadowAlpha * 0.42f), Color.Transparent, Color.White.copy(alpha = rimHighlightAlpha * 0.26f))), Offset(inset, inset), Size(size.width - inset * 2f, size.height - inset * 2f), r, style = Stroke(width = (1.2f + grooveDepth * 3f).dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(Color.White.copy(alpha = rimHighlightAlpha * 0.18f), Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), r, style = Stroke(width = 0.9.dp.toPx()), blendMode = BlendMode.Screen)
        }
        content()
    }
}

@Composable
private fun RecessedProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(999.dp))) {
        val radius = size.height / 2f
        drawRoundRect(Color.White.copy(alpha = 0.09f), cornerRadius = CornerRadius(radius, radius))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFBFFAFF).copy(alpha = 0.95f), Color(0xFF8DF9EA).copy(alpha = 0.72f))), size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatGlassPanelValue(), color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
        }
        Slider(value = clamped, onValueChange = onValueChange, valueRange = range)
    }
}

private fun Float.formatGlassPanelValue(): String = "${((this * 100).roundToInt() / 100f)}"
