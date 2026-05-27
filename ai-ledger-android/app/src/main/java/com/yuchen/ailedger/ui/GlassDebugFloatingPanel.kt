package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt

@Composable
fun GlassDebugFloatingPanel(
    state: AssistantUiState,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val params = state.backdropParams
    val border = state.glassBorderStyle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassLabFoldout(
            title = "玻璃调试",
            subtitle = "背景 / 边缘 / 高光折射",
            initiallyExpanded = false,
            state = state
        ) {
            LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
            LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
            LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.5f..1.8f) { onBackdropChange(params.copy(brightness = it)) }
            LabSlider("背景对比", "背景明暗反差", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
            LabSlider("边缘宽度", "玻璃外缘可见宽度", border.ringWidthDp, 0f..24f) { onBorderChange(border.copy(ringWidthDp = it)) }
            LabSlider("外描边", "外侧细边透明度", border.outerStrokeAlpha, 0f..1.5f) { onBorderChange(border.copy(outerStrokeAlpha = it)) }
            LabSlider("顶部高光", "上沿高光强度", border.topHighlightAlpha, 0f..2f) { onBorderChange(border.copy(topHighlightAlpha = it)) }
            LabSlider("底部阴影", "下沿暗部压边", border.bottomShadowAlpha, 0f..1.2f) { onBorderChange(border.copy(bottomShadowAlpha = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                LabActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                LabActionButton("背景图片", "上传", state, Modifier.weight(1f), onUploadBackgroundClick)
            }
        }

        GlassLabFoldout(
            title = "轻量玻璃",
            subtitle = "模型卡同款透明胶囊 / 棱彩镀膜 / 按压响应",
            initiallyExpanded = true,
            state = state
        ) {
            LightweightGlassLab(state)
        }

        GlassLabFoldout(
            title = "玻璃面板",
            subtitle = "雾面 / 凹槽 / OpenGL 水滴样本与参数",
            initiallyExpanded = true,
            state = state
        ) {
            AnimatedFrostInfoGlassPreview(state, Modifier.fillMaxWidth())
            FrostInfoGlassLab(state)
        }
    }
}

@Composable
private fun GlassLabFoldout(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean,
    state: AssistantUiState,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (expanded) 0.94f else 0.76f,
            motionIntensity = state.motionIntensity,
            radius = 24,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            role = GlassRole.Flex,
            onClick = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun LightweightGlassLab(state: AssistantUiState) {
    var radius by rememberSaveable { mutableFloatStateOf(30f) }
    var baseAlpha by rememberSaveable { mutableFloatStateOf(0.060f) }
    var selectedBoost by rememberSaveable { mutableFloatStateOf(0.026f) }
    var movingBoost by rememberSaveable { mutableFloatStateOf(0.010f) }
    var pressBoost by rememberSaveable { mutableFloatStateOf(0.014f) }
    var stackEnergy by rememberSaveable { mutableFloatStateOf(1.00f) }
    var selected by rememberSaveable { mutableStateOf(true) }
    var moving by rememberSaveable { mutableStateOf(true) }
    var pressed by rememberSaveable { mutableStateOf(false) }

    var selectedBorderAlpha by rememberSaveable { mutableFloatStateOf(0.60f) }
    var normalBorderAlpha by rememberSaveable { mutableFloatStateOf(0.28f) }
    var borderProgressBoost by rememberSaveable { mutableFloatStateOf(0.18f) }
    var borderMovingBoost by rememberSaveable { mutableFloatStateOf(0.08f) }
    var selectedBorderWidth by rememberSaveable { mutableFloatStateOf(1.22f) }
    var normalBorderWidth by rememberSaveable { mutableFloatStateOf(0.86f) }

    var innerAlpha by rememberSaveable { mutableFloatStateOf(0.018f) }
    var innerSelectedBoost by rememberSaveable { mutableFloatStateOf(0.024f) }
    var innerMovingBoost by rememberSaveable { mutableFloatStateOf(0.007f) }

    var prismSelectedBase by rememberSaveable { mutableFloatStateOf(0.075f) }
    var prismNormalBase by rememberSaveable { mutableFloatStateOf(0.044f) }
    var prismProgressBoost by rememberSaveable { mutableFloatStateOf(0.018f) }
    var prismMovingBoost by rememberSaveable { mutableFloatStateOf(0.014f) }
    var prismPressBoost by rememberSaveable { mutableFloatStateOf(0.018f) }
    var prismMax by rememberSaveable { mutableFloatStateOf(0.13f) }
    var prismWhiteWeight by rememberSaveable { mutableFloatStateOf(0.58f) }
    var prismPurpleWeight by rememberSaveable { mutableFloatStateOf(0.42f) }
    var prismPinkWeight by rememberSaveable { mutableFloatStateOf(0.34f) }
    var prismCyanWeight by rememberSaveable { mutableFloatStateOf(0.18f) }

    var pressScaleX by rememberSaveable { mutableFloatStateOf(0.012f) }
    var pressScaleY by rememberSaveable { mutableFloatStateOf(0.020f) }
    var pressTranslateY by rememberSaveable { mutableFloatStateOf(1.20f) }

    val selectedEnergy = if (selected) 1f else 0f
    val movingValue = if (moving) 1f else 0f
    val pressValue = if (pressed) 1f else 0f
    val expansionProgress = if (moving) 1f else 0.55f
    val bgAlpha = (baseAlpha + selectedEnergy * selectedBoost + movingValue * movingBoost + pressValue * pressBoost) * stackEnergy
    val borderAlpha = if (selected) {
        selectedBorderAlpha + borderProgressBoost * expansionProgress + borderMovingBoost * movingValue
    } else {
        (normalBorderAlpha + borderProgressBoost * expansionProgress + borderMovingBoost * movingValue) * stackEnergy
    }
    val inner = innerAlpha + selectedEnergy * innerSelectedBoost + movingValue * innerMovingBoost
    val prismBase = if (selected) prismSelectedBase else prismNormalBase
    val prism = (prismBase + prismProgressBoost * expansionProgress + prismMovingBoost * movingValue + prismPressBoost * pressValue).coerceIn(0f, prismMax)

    SectionTitleInline("预览状态")
    LightweightGlassPreview(
        radius = radius,
        bgAlpha = bgAlpha,
        borderAlpha = borderAlpha,
        borderWidth = if (selected) selectedBorderWidth else normalBorderWidth,
        innerAlpha = inner,
        prism = prism,
        prismWhiteWeight = prismWhiteWeight,
        prismPurpleWeight = prismPurpleWeight,
        prismPinkWeight = prismPinkWeight,
        prismCyanWeight = prismCyanWeight,
        pressScaleX = pressScaleX * pressValue,
        pressScaleY = pressScaleY * pressValue,
        pressTranslateY = pressTranslateY * pressValue,
        selected = selected,
        moving = moving,
        pressed = pressed
    )
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        LabToggleButton(if (selected) "选中态" else "普通态", "影响青色边框", state, Modifier.weight(1f)) { selected = !selected }
        LabToggleButton(if (moving) "移动中" else "静止态", "影响镀膜亮度", state, Modifier.weight(1f)) { moving = !moving }
        LabToggleButton(if (pressed) "按压中" else "未按压", "影响胶囊压缩", state, Modifier.weight(1f)) { pressed = !pressed }
    }

    SectionTitleInline("胶囊底色")
    LabSlider("圆角半径", "胶囊圆角大小", radius, 18f..42f) { radius = it }
    LabSlider("基础透明底", "白色透明玻璃底色", baseAlpha, 0f..0.16f) { baseAlpha = it }
    LabSlider("选中增亮", "选中态底色增加值", selectedBoost, 0f..0.08f) { selectedBoost = it }
    LabSlider("移动增亮", "移动/飞行时底色增加值", movingBoost, 0f..0.06f) { movingBoost = it }
    LabSlider("按压增亮", "点击胶囊时底色增加值", pressBoost, 0f..0.08f) { pressBoost = it }
    LabSlider("后层能量", "折叠后层卡片亮度倍率", stackEnergy, 0.25f..1.2f) { stackEnergy = it }

    SectionTitleInline("边框与白雾")
    LabSlider("选中边框透明", "青色边框基础透明度", selectedBorderAlpha, 0f..1.2f) { selectedBorderAlpha = it }
    LabSlider("普通边框透明", "白色边框基础透明度", normalBorderAlpha, 0f..1.0f) { normalBorderAlpha = it }
    LabSlider("展开边框增益", "展开进度带来的边框增益", borderProgressBoost, 0f..0.5f) { borderProgressBoost = it }
    LabSlider("移动边框增益", "移动中边框加强", borderMovingBoost, 0f..0.35f) { borderMovingBoost = it }
    LabSlider("选中边框宽度", "青色边框粗细", selectedBorderWidth, 0.4f..2.4f) { selectedBorderWidth = it }
    LabSlider("普通边框宽度", "白色边框粗细", normalBorderWidth, 0.3f..1.8f) { normalBorderWidth = it }
    LabSlider("内层白雾", "内部淡白柔光", innerAlpha, 0f..0.08f) { innerAlpha = it }
    LabSlider("选中白雾增益", "选中时内部白雾增强", innerSelectedBoost, 0f..0.08f) { innerSelectedBoost = it }
    LabSlider("移动白雾增益", "移动时内部白雾增强", innerMovingBoost, 0f..0.04f) { innerMovingBoost = it }

    SectionTitleInline("棱彩镀膜")
    LabSlider("选中棱彩基础", "选中态镀膜底强度", prismSelectedBase, 0f..0.18f) { prismSelectedBase = it }
    LabSlider("普通棱彩基础", "普通态镀膜底强度", prismNormalBase, 0f..0.14f) { prismNormalBase = it }
    LabSlider("展开棱彩增益", "展开进度带来的棱彩增强", prismProgressBoost, 0f..0.08f) { prismProgressBoost = it }
    LabSlider("移动棱彩增益", "移动中镀膜增强", prismMovingBoost, 0f..0.08f) { prismMovingBoost = it }
    LabSlider("按压棱彩增益", "点击时镀膜增强", prismPressBoost, 0f..0.10f) { prismPressBoost = it }
    LabSlider("棱彩强度上限", "限制镀膜最大透明度", prismMax, 0.02f..0.30f) { prismMax = it }
    LabSlider("白色高光权重", "镀膜中央白光强度", prismWhiteWeight, 0f..1.2f) { prismWhiteWeight = it }
    LabSlider("紫色权重", "紫色镀膜强度", prismPurpleWeight, 0f..1.2f) { prismPurpleWeight = it }
    LabSlider("粉色权重", "粉色镀膜强度", prismPinkWeight, 0f..1.2f) { prismPinkWeight = it }
    LabSlider("青色边光权重", "青色第二层边光强度", prismCyanWeight, 0f..1.2f) { prismCyanWeight = it }

    SectionTitleInline("点击胶囊动画")
    LabSlider("按压横向展开", "按下时横向 scale 增量", pressScaleX, 0f..0.06f) { pressScaleX = it }
    LabSlider("按压纵向压缩", "按下时纵向 scale 减量", pressScaleY, 0f..0.08f) { pressScaleY = it }
    LabSlider("按压下沉", "按下时向下位移 px", pressTranslateY, 0f..6f) { pressTranslateY = it }
}

@Composable
private fun LightweightGlassPreview(
    radius: Float,
    bgAlpha: Float,
    borderAlpha: Float,
    borderWidth: Float,
    innerAlpha: Float,
    prism: Float,
    prismWhiteWeight: Float,
    prismPurpleWeight: Float,
    prismPinkWeight: Float,
    prismCyanWeight: Float,
    pressScaleX: Float,
    pressScaleY: Float,
    pressTranslateY: Float,
    selected: Boolean,
    moving: Boolean,
    pressed: Boolean
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .graphicsLayer {
                scaleX = 1f + pressScaleX
                scaleY = 1f - pressScaleY
                translationY = pressTranslateY
            }
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha.coerceIn(0f, 0.40f)))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = borderWidth.dp,
                    color = if (selected) Color(0xFF8DF9EA).copy(alpha = borderAlpha.coerceIn(0f, 1f)) else Color.White.copy(alpha = borderAlpha.coerceIn(0f, 1f)),
                    shape = shape
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = innerAlpha.coerceIn(0f, 0.22f)))
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8DF9EA).copy(alpha = prism * 0.10f),
                            Color(0xFFB8A4FF).copy(alpha = prism * prismPurpleWeight),
                            Color.White.copy(alpha = prism * prismWhiteWeight),
                            Color(0xFFFFB3E6).copy(alpha = prism * prismPinkWeight),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 1.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = prism * 0.26f),
                            Color.Transparent,
                            Color(0xFF8DF9EA).copy(alpha = prism * prismCyanWeight)
                        )
                    )
                )
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(if (selected) 9.dp else 7.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.48f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("轻量玻璃 / Model Capsule", color = Color.White.copy(alpha = 0.96f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(if (selected) "选中" else "普通", if (moving) "移动" else "静止", if (pressed) "按压" else "松手").joinToString(" · "), color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LabToggleButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.72f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(50.dp),
        role = GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitleInline(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun LabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clamped.formatLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = clamped, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LabActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.78f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(56.dp),
        role = GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Float.formatLabValue(): String = "${((this * 100).roundToInt() / 100f)}"