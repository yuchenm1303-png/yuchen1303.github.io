package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassCardLayer
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
        GlassLabFoldout("OpenGL", "旧 Shell 样本 / 保留原实现，不随新版替换", false, state) {
            OpenGlGlassLab(state, border, onBorderChange)
        }
        GlassLabFoldout("新版 OpenGL", "单一模糊背景层 + 新版采样层", false, state) {
            NewOpenGlGlassLab(state, border, onBorderChange)
        }
        RestoredGlassLabSections(state)
    }
}

@Composable
private fun OpenGlGlassLab(state: AssistantUiState, style: GlassBorderStyle, onBorderChange: (GlassBorderStyle) -> Unit) {
    LegacyOpenGLGlassPreviewShell(state.quality, state.glassIntensity * 0.70f, state.motionIntensity, 26, Modifier.fillMaxWidth().height(120.dp)) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("旧 OpenGL Shell 样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("可见", style.openGlVisibility, Modifier.weight(1f))
                Metric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                Metric("亮度", style.edgeBrightness, Modifier.weight(1f))
            }
        }
    }
    Group("旧样本参数", "只影响这一栏旧样本", state) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onBorderChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
    }
}

@Composable
private fun NewOpenGlGlassLab(state: AssistantUiState, style: GlassBorderStyle, onBorderChange: (GlassBorderStyle) -> Unit) {
    val sampleCoordinates = remember { GlassCoordinateSource() }
    val blurRadius = style.edgeBlurDp.roundToInt().coerceIn(0, 128)
    val blurAlpha = (style.newOpenGlClarity * 0.62f * style.newOpenGlBrightness.coerceIn(0.4f, 2.2f)).coerceIn(0f, 1.35f)
    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(30.dp)).onPlaced { sampleCoordinates.coordinates = it }.background(Color.White.copy(alpha = 0.018f))) {
        SampledWeatherGlassBackdrop(Modifier.matchParentSize(), 30, sampleCoordinates, state.quality, state.motionIntensity, state.backgroundTheme, blurRadius, blurAlpha)
        NewOpenGLGlassCardLayer(30, state.glassIntensity * 0.92f, sampleCoordinates, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("新版 OpenGL 样本玻璃", color = Color.White.copy(alpha = 0.96f), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("单一背景来源：模糊层独立，新版 OpenGL 独立采样", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("模糊", blurRadius.toFloat(), Modifier.weight(1f))
                Metric("雾化", style.newOpenGlClarity, Modifier.weight(1f))
                Metric("外缘", style.newOpenGlOuterRimGain, Modifier.weight(1f))
            }
        }
    }
    Group("背景模糊层", "只控制底层模糊背景；OpenGL 折射采样独立进行", state) {
        LabSlider("背景模糊半径 dp", "底层 blurRadiusDp", style.edgeBlurDp, 0f..128f) { onBorderChange(style.copy(edgeBlurDp = it)) }
        LabSlider("背景模糊层透明度", "底层模糊背景 liftAlpha", style.newOpenGlClarity, 0f..1.6f) { onBorderChange(style.copy(newOpenGlClarity = it)) }
        LabSlider("背景模糊层亮度", "底层模糊背景亮度响应", style.newOpenGlBrightness, 0.4f..2.2f) { onBorderChange(style.copy(newOpenGlBrightness = it)) }
        LabSlider("OpenGL 最大透明", "新版 OpenGL 折射层 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onBorderChange(style.copy(openGlMaxAlpha = it)) }
    }
    Group("主体折射", "新版 OpenGL 主体折射参数", state) {
        LabSlider("主体宽度", "bodyWidth", style.newOpenGlBodyWidth, 0.18f..1.50f) { onBorderChange(style.copy(newOpenGlBodyWidth = it)) }
        LabSlider("主体陡度", "bodyCurve", style.newOpenGlBodyCurve, 0.20f..3.20f) { onBorderChange(style.copy(newOpenGlBodyCurve = it)) }
        LabSlider("主体强度", "bodyGain", style.newOpenGlBodyGain, 0f..900f) { onBorderChange(style.copy(newOpenGlBodyGain = it)) }
    }
}

@Composable
private fun GlassLabFoldout(title: String, subtitle: String, initiallyExpanded: Boolean, state: AssistantUiState, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(state.quality, state.glassIntensity * if (expanded) 0.94f else 0.76f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(58.dp), GlassRole.Flex, onClick = { expanded = !expanded }) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.045f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (expanded) "收起" else "展开", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp).clickable { expanded = !expanded })
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun LabSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
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
private fun LabActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 22, modifier.height(54.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Metric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier.height(42.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun Float.formatLabValue(): String {
    val scaled = (this * 100f).roundToInt() / 100f
    return if (scaled % 1f == 0f) scaled.roundToInt().toString() else scaled.toString()
}
