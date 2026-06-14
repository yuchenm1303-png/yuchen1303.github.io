package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
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
    var legacyBorder by remember { mutableStateOf(legacyOpenGlLabStyle()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassLabFoldout("OpenGL", "旧 Shell 样本 / 保留原实现，不随新版替换", false, state) {
            OpenGlGlassLab(state, params, legacyBorder) { legacyBorder = it }
        }
        GlassLabFoldout("新版 OpenGL", "V29.8 整圈统一映射 + 精确切向校正圆肩", false, state) {
            LatestOpenGLGlassLab(state, params, border, onBackdropChange, onBorderChange)
        }
        GlassLabFoldout("玻璃调试", "背景采样与全局背景参数", false, state) {
            LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
            LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
            LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
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
        RestoredGlassLabSections(state)
    }
}

@Composable
private fun OpenGlGlassLab(
    state: AssistantUiState,
    params: BackdropDebugParams,
    style: GlassBorderStyle,
    onStyleChange: (GlassBorderStyle) -> Unit
) {
    val legacySpec = remember(state.quality, state.motionIntensity, state.backgroundTheme, params, style) {
        GlassBackdropSpec(
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            params = params,
            borderStyle = style
        )
    }
    CompositionLocalProvider(LocalGlassBackdrop provides legacySpec) {
        LegacyOpenGLGlassPreviewShell(
            quality = state.quality,
            glassIntensity = state.glassIntensity * 0.70f,
            motionIntensity = state.motionIntensity,
            radius = 26,
            modifier = Modifier.fillMaxWidth().height(120.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("旧 OpenGL Shell 样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("可见", style.openGlVisibility, Modifier.weight(1f))
                    Metric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                    Metric("亮度", style.edgeBrightness, Modifier.weight(1f))
                }
            }
        }
    }
    Group("旧样本参数", "只影响这一栏旧样本", state) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onStyleChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onStyleChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("旧边缘亮度", "旧 shader 的折射亮度", style.edgeBrightness, 0.20f..2.40f) { onStyleChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧边缘宽度", "旧 shader rim 宽度", style.ringWidthDp, 0f..96f) { onStyleChange(style.copy(ringWidthDp = it)) }
    }
}

private fun legacyOpenGlLabStyle(): GlassBorderStyle = GlassBorderStyle(
    ringWidthDp = 8.295f,
    edgePullDp = -199.078f,
    edgeBrightness = 1.083f,
    openGlVisibility = 19.954f,
    openGlMaxAlpha = 1f,
    openGlPullScale = -5.53f,
    openGlCompressionScale = -10f,
    openGlCornerScale = 54.378f,
    openGlDarkScale = -2.21f,
    openGlSampleRadiusScale = 66.359f
)

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
            state.quality,
            state.glassIntensity * if (expanded) 0.94f else 0.76f,
            state.motionIntensity,
            24,
            Modifier.fillMaxWidth().height(58.dp),
            GlassRole.Flex,
            onClick = { expanded = !expanded }
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
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
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun Group(title: String, subtitle: String, state: AssistantUiState, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (expanded) "收起" else "展开",
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.060f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable { expanded = !expanded }
            )
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun LabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.38f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
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
        state.quality,
        state.glassIntensity * 0.72f,
        state.motionIntensity,
        22,
        modifier.height(54.dp),
        GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Metric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun Float.formatLabValue(): String {
    val scaled = (this * 100f).roundToInt() / 100f
    return if (scaled % 1f == 0f) scaled.roundToInt().toString() else scaled.toString()
}
