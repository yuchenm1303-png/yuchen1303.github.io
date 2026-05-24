package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SettingsScreenV2(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    var debugOpen by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !debugOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.98f, animationSpec = spring(dampingRatio = 0.76f)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.99f, animationSpec = tween(120))
    ) {
        SettingsHomeV2(
            state = state,
            aiEndpoint = aiEndpoint,
            onQualityChange = onQualityChange,
            onPreviewConversationChange = onPreviewConversationChange,
            onGlassPresetChange = onGlassPresetChange,
            onBackgroundThemeChange = onBackgroundThemeChange,
            onGlassIntensityChange = onGlassIntensityChange,
            onMotionIntensityChange = onMotionIntensityChange,
            onBorderChange = onBorderChange,
            onUploadBackgroundClick = onUploadBackgroundClick,
            onClearCustomBackgroundClick = onClearCustomBackgroundClick,
            onOpenDebug = { debugOpen = true }
        )
    }

    AnimatedVisibility(
        visible = debugOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.74f)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.99f, animationSpec = tween(120))
    ) {
        GlassDebugScreenV2(
            state = state,
            onBack = { debugOpen = false },
            onBackdropChange = onBackdropChange,
            onBorderChange = onBorderChange
        )
    }
}

@Composable
private fun SettingsHomeV2(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
    onOpenDebug: () -> Unit
) {
    var displayExpanded by rememberSaveable { mutableStateOf(false) }
    var glassExpanded by rememberSaveable { mutableStateOf(false) }
    var assistantExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var serviceExpanded by rememberSaveable { mutableStateOf(false) }
    var glassPanelExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "settings-header") { SettingsHeaderV2() }
        item(key = "display-background") {
            ExpandableSettingsSection(state, "显示与背景", "背景、图片和页面观感。", "景", Color(0xFF8DF9EA), displayExpanded, { displayExpanded = !displayExpanded }) {
                SettingOptionGrid(BackgroundTheme.entries, state.backgroundTheme, { themeLabelV2(it) }, state, onBackgroundThemeChange)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    LiquidActionCard("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
                    LiquidActionCard("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                }
            }
        }
        item(key = "glass-motion") {
            ExpandableSettingsSection(state, "玻璃与流畅度", "画质、预设和日常强度。", "璃", Color(0xFF9EB7FF), glassExpanded, { glassExpanded = !glassExpanded }) {
                SettingOptionGrid(RenderQuality.entries, state.quality, { qualityLabelV2(it) }, state, onQualityChange)
                SettingOptionGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabelV2(it) }, state, onGlassPresetChange)
                LiquidGlassSlider("玻璃强度", "卡片雾面与边缘存在感", state.glassIntensity, 0.6f..1.4f, state, onGlassIntensityChange)
                LiquidGlassSlider("动态强度", "呼吸、滑动和弹性动画", state.motionIntensity, 0f..1.4f, state, onMotionIntensityChange)
            }
        }
        item(key = "assistant-preference") {
            ExpandableSettingsSection(state, "助手偏好", "首页聊天体验和默认行为。", "AI", Color(0xFFFFD166), assistantExpanded, { assistantExpanded = !assistantExpanded }) {
                LiquidSwitchRow("聊天预览", "打开后首页保留示例对话和建议词。", state.showPreviewConversation, state, onPreviewConversationChange)
                SettingInfoGlass("默认模型", state.selectedModelLabel, state)
                SettingInfoGlass("首页消息", "${state.messages.size} 条", state)
            }
        }
        item(key = "data-budget") {
            ExpandableSettingsSection(state, "数据与预算", "账单状态、预算和后续同步。", "数", Color(0xFFFFB4D2), dataExpanded, { dataExpanded = !dataExpanded }) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniSettingGlass("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
                    MiniSettingGlass("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", state, Modifier.weight(1f))
                    MiniSettingGlass("同步", "本地", state, Modifier.weight(1f))
                }
                SettingInfoGlass("数据保存", "当前为内存预览，后续可接 DataStore / Room", state)
            }
        }
        item(key = "service-status") {
            ExpandableSettingsSection(state, "服务状态", "AI Worker、云端接口和本地执行。", "云", Color(0xFFC7A8FF), serviceExpanded, { serviceExpanded = !serviceExpanded }) {
                SettingInfoGlass("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint, state)
                SettingInfoGlass("执行模式", "本地动作优先，复杂问题交给云端", state)
            }
        }
        item(key = "advanced-debug-entry") {
            SettingsNavigationCard(state, "高级玻璃调试", "进入独立调试页，避免长展开导致 OpenGL 闪烁。", "调", Color(0xFF8DF9EA), onOpenDebug)
        }
        item(key = "glass-panel-lab") {
            ExpandableSettingsSection(state, "玻璃面板", "调试不同形态的 Compose 玻璃。", "面", Color(0xFF8DF9EA), glassPanelExpanded, { glassPanelExpanded = !glassPanelExpanded }) {
                FrostInfoGlassLab(state)
            }
        }
    }
}

@Composable
private fun GlassDebugScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    var group by rememberSaveable { mutableStateOf("背景") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "debug-header") { DebugHeaderV2(state, onBack) }
        item(key = "debug-tabs") { DebugTabRow(state, group) { group = it } }
        item(key = "debug-content-$group") {
            DebugGroupCard(state, debugGroupTitle(group), debugGroupSubtitle(group)) {
                when (group) {
                    "背景" -> BackgroundDebugGroup(state, onBackdropChange)
                    "天空" -> SkyDebugGroup(state, onBackdropChange)
                    "OpenGL" -> OpenGlDebugGroup(state, onBorderChange)
                    else -> ComposeGlassDebugGroup(state, onBorderChange)
                }
            }
        }
    }
}

@Composable
private fun DebugHeaderV2(state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, Modifier.height(38.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回设置", color = Color.White.copy(alpha = 0.84f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("GLASS LAB", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("玻璃调试", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text("按渲染路径整理参数：背景缓存、天空装饰、OpenGL 水滴和 Compose 玻璃。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DebugTabRow(state: AssistantUiState, selected: String, onSelected: (String) -> Unit) {
    val tabs = listOf("背景", "天空", "OpenGL", "Compose")
    GlassPanel(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Flex) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            tabs.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { tab ->
                        val active = tab == selected
                        PressableGlass(state.quality, state.glassIntensity * if (active) 1.05f else 0.88f, state.motionIntensity, 999, Modifier.weight(1f).height(34.dp), if (active) GlassRole.Floating else GlassRole.Chip, onClick = { onSelected(tab) }) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(tab, color = Color.White.copy(alpha = if (active) 0.94f else 0.58f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DebugGroupCard(state: AssistantUiState, title: String, subtitle: String, content: @Composable () -> Unit) {
    GlassPanel(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth().settingsGlow(0.24f, 1f, Color(0xFF8DF9EA)), GlassRole.Flex) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 16.sp)
            }
            AnimatedVisibility(visible = true, enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.98f, animationSpec = spring(dampingRatio = 0.72f)), exit = fadeOut(tween(100))) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
            }
        }
    }
}

@Composable
private fun BackgroundDebugGroup(state: AssistantUiState, onBackdropChange: (BackdropDebugParams) -> Unit) {
    DebugSliderRow("缓存分辨率", "越高越细，越低越省性能", state.backdropParams.scale, 0.04f..2.00f, state) { onBackdropChange(state.backdropParams.copy(scale = it)) }
    DebugSliderRow("模糊半径", "背景毛玻璃的柔化范围", state.backdropParams.radius, 0f..180f, state) { onBackdropChange(state.backdropParams.copy(radius = it.roundToInt().toFloat())) }
    DebugSliderRow("模糊迭代", "越高越柔，但更耗性能", state.backdropParams.iterations, 1f..48f, state) { onBackdropChange(state.backdropParams.copy(iterations = it.roundToInt().toFloat())) }
    DebugSliderRow("亮度", "背景采样后的提亮程度", state.backdropParams.brightness, 0.00f..6.00f, state) { onBackdropChange(state.backdropParams.copy(brightness = it)) }
    DebugSliderRow("对比度", "玻璃内部背景层次", state.backdropParams.contrast, 0.00f..8.00f, state) { onBackdropChange(state.backdropParams.copy(contrast = it)) }
    DebugSliderRow("饱和度", "背景颜色浓淡", state.backdropParams.saturation, 0.00f..8.00f, state) { onBackdropChange(state.backdropParams.copy(saturation = it)) }
}

@Composable
private fun SkyDebugGroup(state: AssistantUiState, onBackdropChange: (BackdropDebugParams) -> Unit) {
    DebugSliderRow("云层强度", "默认背景云雾存在感", state.backdropParams.cloudAlpha, 0f..1.8f, state) { onBackdropChange(state.backdropParams.copy(cloudAlpha = it)) }
    DebugSliderRow("云层柔度", "云雾边缘软硬", state.backdropParams.cloudSoftness, 0.6f..2.2f, state) { onBackdropChange(state.backdropParams.copy(cloudSoftness = it)) }
    DebugSliderRow("云横向拉伸", "横向云带长度", state.backdropParams.cloudStretchX, 0.5f..4.0f, state) { onBackdropChange(state.backdropParams.copy(cloudStretchX = it)) }
    DebugSliderRow("云纵向厚度", "云带纵向厚度", state.backdropParams.cloudStretchY, 0.3f..2.0f, state) { onBackdropChange(state.backdropParams.copy(cloudStretchY = it)) }
    DebugSliderRow("云高光", "云层顶部发亮程度", state.backdropParams.cloudHighlightAlpha, 0f..1.0f, state) { onBackdropChange(state.backdropParams.copy(cloudHighlightAlpha = it)) }
    DebugSliderRow("月亮大小", "默认背景月牙尺寸", state.backdropParams.moonScale, 0.5f..1.8f, state) { onBackdropChange(state.backdropParams.copy(moonScale = it)) }
    DebugSliderRow("月亮光晕", "月亮周围的柔光", state.backdropParams.moonHaloAlpha, 0f..0.8f, state) { onBackdropChange(state.backdropParams.copy(moonHaloAlpha = it)) }
    DebugSliderRow("月牙亮边", "月牙边缘高光", state.backdropParams.moonRimAlpha, 0f..1.2f, state) { onBackdropChange(state.backdropParams.copy(moonRimAlpha = it)) }
}

@Composable
private fun OpenGlDebugGroup(state: AssistantUiState, onBorderChange: (GlassBorderStyle) -> Unit) {
    Text("这一组只放真正参与 OpenGL / AGSL 水滴折射的参数。", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    DebugSliderRow("调试边界线", "显示真实 SDF 边界", state.glassBorderStyle.openGlDebugLineAlpha, 0f..1f, state) { onBorderChange(state.glassBorderStyle.copy(openGlDebugLineAlpha = it)) }
    DebugSliderRow("整体可见度", "OpenGL 玻璃层存在感", state.glassBorderStyle.openGlVisibility, 0f..20f, state) { onBorderChange(state.glassBorderStyle.copy(openGlVisibility = it)) }
    DebugSliderRow("主体 Alpha", "水滴主体透明度", state.glassBorderStyle.openGlMaxAlpha, 0f..1f, state) { onBorderChange(state.glassBorderStyle.copy(openGlMaxAlpha = it)) }
    DebugSliderRow("背景亮度", "OpenGL 采样背景亮度", state.glassBorderStyle.edgeBrightness, -2f..6f, state) { onBorderChange(state.glassBorderStyle.copy(edgeBrightness = it)) }
    DebugSliderRow("主体折射 px", "中心区域连续折射强度", state.glassBorderStyle.openGlPullScale, -1200f..1200f, state) { onBorderChange(state.glassBorderStyle.copy(openGlPullScale = it)) }
    DebugSliderRow("边缘折射 px", "边缘把背景向内/外拉动", state.glassBorderStyle.edgePullDp, -2400f..2400f, state) { onBorderChange(state.glassBorderStyle.copy(edgePullDp = it)) }
    DebugSliderRow("折射边宽 px", "透镜压缩带宽度", state.glassBorderStyle.ringWidthDp, 0f..900f, state) { onBorderChange(state.glassBorderStyle.copy(ringWidthDp = it.roundToInt().toFloat())) }
    DebugSliderRow("清晰混入", "清晰纹理参与折射比例", state.glassBorderStyle.openGlCompressionScale, -10f..10f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCompressionScale = it)) }
    DebugSliderRow("梯度厚度", "厚度场梯度增强", state.glassBorderStyle.openGlCornerScale, 0f..800f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCornerScale = it)) }
    DebugSliderRow("全局柔化 px", "全局折射区再柔化基准", state.glassBorderStyle.openGlSampleRadiusScale, 0f..600f, state) { onBorderChange(state.glassBorderStyle.copy(openGlSampleRadiusScale = it)) }
    DebugSliderRow("中心采样", "0 直接采模糊缓存，1 恢复中心 9 点柔化", state.glassBorderStyle.openGlCenterSampleMix, 0f..1f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCenterSampleMix = it)) }
    DebugSliderRow("中心半径", "中心额外柔化半径倍率", state.glassBorderStyle.openGlCenterSampleRadiusScale, 0f..3f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCenterSampleRadiusScale = it)) }
    DebugSliderRow("边缘采样", "边缘 9 点柔化保留比例", state.glassBorderStyle.openGlEdgeSampleMix, 0f..1.5f, state) { onBorderChange(state.glassBorderStyle.copy(openGlEdgeSampleMix = it)) }
    DebugSliderRow("边缘增强", "边缘采样半径额外放大", state.glassBorderStyle.openGlEdgeSampleRadiusBoost, 0f..1.5f, state) { onBorderChange(state.glassBorderStyle.copy(openGlEdgeSampleRadiusBoost = it)) }
    DebugSliderRow("内侧暗带", "边缘内侧压暗厚度感", state.glassBorderStyle.openGlDarkScale, -12f..12f, state) { onBorderChange(state.glassBorderStyle.copy(openGlDarkScale = it)) }
    DebugSliderRow("备用边宽", "备用 OpenGL 边缘宽度倍率", state.glassBorderStyle.openGlEdgeWidthScale, -20f..20f, state) { onBorderChange(state.glassBorderStyle.copy(openGlEdgeWidthScale = it)) }
    DebugSliderRow("备用高光", "备用 OpenGL 镜面高光倍率", state.glassBorderStyle.openGlSpecularScale, -10f..10f, state) { onBorderChange(state.glassBorderStyle.copy(openGlSpecularScale = it)) }
    DebugSliderRow("备用色散", "备用 RGB 边缘分离倍率", state.glassBorderStyle.openGlChromaticScale, -10f..10f, state) { onBorderChange(state.glassBorderStyle.copy(openGlChromaticScale = it)) }
}

@Composable
private fun ComposeGlassDebugGroup(state: AssistantUiState, onBorderChange: (GlassBorderStyle) -> Unit) {
    Text("这一组只放普通 Compose 玻璃的雾面、边缘、高光和阴影。", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    DebugSliderRow("边缘 Alpha", "Compose 兼容边缘整体强度", state.glassBorderStyle.edgeAlpha, 0f..2f, state) { onBorderChange(state.glassBorderStyle.copy(edgeAlpha = it)) }
    DebugSliderRow("边缘模糊 px", "Compose 兼容边缘柔化半径", state.glassBorderStyle.edgeBlurDp, 0f..600f, state) { onBorderChange(state.glassBorderStyle.copy(edgeBlurDp = it.roundToInt().toFloat())) }
    DebugSliderRow("边缘对比度", "Compose 边缘背景反差", state.glassBorderStyle.edgeContrast, 0.00f..8.00f, state) { onBorderChange(state.glassBorderStyle.copy(edgeContrast = it)) }
    DebugSliderRow("边缘饱和度", "Compose 边缘颜色浓度", state.glassBorderStyle.edgeSaturation, 0.00f..8.00f, state) { onBorderChange(state.glassBorderStyle.copy(edgeSaturation = it)) }
    DebugSliderRow("主体雾面", "玻璃中心雾面覆盖", state.glassBorderStyle.bodyAlpha, -5f..5f, state) { onBorderChange(state.glassBorderStyle.copy(bodyAlpha = it)) }
    DebugSliderRow("外轮廓高光", "最外层轮廓高光", state.glassBorderStyle.outerStrokeAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(outerStrokeAlpha = it)) }
    DebugSliderRow("内侧细线", "内侧细线高光", state.glassBorderStyle.innerStrokeAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(innerStrokeAlpha = it)) }
    DebugSliderRow("顶部高光", "卡片顶部发亮边缘", state.glassBorderStyle.topHighlightAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(topHighlightAlpha = it)) }
    DebugSliderRow("底部暗边", "卡片底部压暗层", state.glassBorderStyle.bottomShadowAlpha, 0f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(bottomShadowAlpha = it)) }
    DebugSliderRow("圆角 glint", "圆角小高光", state.glassBorderStyle.cornerGlintAlpha, 0f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(cornerGlintAlpha = it)) }
}

@Composable
private fun SettingsHeaderV2() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("设置", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text("常用设置折叠收纳，高级玻璃调试独立进入。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpandableSettingsSection(state: AssistantUiState, title: String, subtitle: String, glyph: String, accent: Color, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow), label = "settings-section-arrow")
    GlassPanel(state.quality, state.glassIntensity * if (expanded) 1.02f else 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth().settingsGlow(if (expanded) 0.42f else 0.10f, 1f, accent), GlassRole.Flex) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PressableGlass(state.quality, state.glassIntensity * if (expanded) 1.04f else 0.92f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(54.dp), if (expanded) GlassRole.Floating else GlassRole.Chip, onClick = onToggle) {
                Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionGlyph(glyph, expanded, accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("⌄", color = Color.White.copy(alpha = 0.60f), fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { rotationZ = rotation })
                }
            }
            LiquidCollapsibleSettingsContent(expanded = expanded) { content() }
        }
    }
}

@Composable
private fun LiquidCollapsibleSettingsContent(expanded: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var measuredHeightPx by rememberSaveable { mutableStateOf(0) }
    val targetHeight = with(density) { (if (expanded) measuredHeightPx else 0).toDp() }
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (expanded) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(150)
        },
        label = "settings-collapsible-height"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 140 else 110),
        label = "settings-collapsible-alpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.98f,
        animationSpec = if (expanded) {
            spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(140)
        },
        label = "settings-collapsible-scale"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { size ->
                    if (size.height > 0 && size.height != measuredHeightPx) measuredHeightPx = size.height
                }
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsNavigationCard(state: AssistantUiState, title: String, subtitle: String, glyph: String, accent: Color, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.98f, state.motionIntensity, 28, Modifier.fillMaxWidth().height(74.dp).settingsGlow(0.28f, 1f, accent), GlassRole.Card, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionGlyph(glyph, true, accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.95f), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SectionGlyph(glyph: String, active: Boolean, accent: Color) {
    val glow by animateFloatAsState(if (active) 1f else 0.18f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "section-glyph-glow")
    Box(Modifier.size(34.dp).settingsGlyphGlow(glow, 1f, accent), contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (active) 0.10f else 0.045f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White.copy(alpha = if (active) 0.96f else 0.66f), fontSize = if (glyph.length > 1) 11.sp else 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun <T> SettingOptionGrid(items: List<T>, selected: T, label: (T) -> String, state: AssistantUiState, onSelected: (T) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                val pop by animateFloatAsState(if (active) 1.014f else 1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow), label = "setting-chip-pop")
                PressableGlass(state.quality, state.glassIntensity * if (active) 1.04f else 0.90f, state.motionIntensity, 999, Modifier.weight(1f).height(34.dp).graphicsLayer { scaleX = pop; scaleY = pop }, if (active) GlassRole.Floating else GlassRole.Chip, onClick = { onSelected(item) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label(item), color = Color.White.copy(alpha = if (active) 0.94f else 0.58f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiquidSwitchRow(title: String, subtitle: String, checked: Boolean, state: AssistantUiState, onCheckedChange: (Boolean) -> Unit) {
    val glow by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "liquid-switch-glow")
    PressableGlass(state.quality, state.glassIntensity * if (checked) 1.05f else 0.92f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(58.dp).settingsGlow(glow * 0.42f, 1f, Color(0xFF8DF9EA)), if (checked) GlassRole.Floating else GlassRole.Flex, onClick = { onCheckedChange(!checked) }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LiquidSwitch(checked, glow)
        }
    }
}

@Composable
private fun LiquidSwitch(checked: Boolean, glow: Float) {
    val knobX by animateDpAsState(if (checked) 27.dp else 3.dp, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow), label = "liquid-switch-knob")
    val knobScale by animateFloatAsState(if (checked) 1.05f else 0.95f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "liquid-switch-knob-scale")
    Box(Modifier.width(58.dp).height(32.dp).clip(RoundedCornerShape(999.dp)).liquidSwitchSkin(glow), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = knobX).size(26.dp).graphicsLayer { scaleX = knobScale; scaleY = knobScale }.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = if (checked) 0.96f else 0.78f)))
    }
}

@Composable
private fun LiquidGlassSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, state: AssistantUiState, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    SliderContent(title, subtitle, clamped, range, onValueChange, Modifier)
}

@Composable
private fun DebugSliderRow(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, state: AssistantUiState, onValueChange: (Float) -> Unit) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    SliderContent(title, subtitle, clamped, range, onValueChange, Modifier)
}

@Composable
private fun SliderContent(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.050f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(value.formatSettingValueV2(), color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = value,
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

@Composable
private fun LiquidActionCard(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 22, modifier.height(50.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingInfoGlass(title: String, value: String, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 20, Modifier.fillMaxWidth().height(44.dp), GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniSettingGlass(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 17, modifier.height(46.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Modifier.settingsGlow(glow: Float, pulse: Float, accent: Color): Modifier = drawWithCache {
    val radius = min(size.width, size.height) * (1.22f + 0.10f * pulse)
    val brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.13f * glow), Color.White.copy(alpha = 0.045f * glow), Color.Transparent), center = Offset(size.width * 0.18f, size.height * 0.16f), radius = radius)
    onDrawWithContent { if (glow > 0.01f) drawRect(brush, blendMode = BlendMode.Screen); drawContent() }
}

private fun Modifier.settingsGlyphGlow(glow: Float, pulse: Float, accent: Color): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * (0.72f + 0.10f * pulse)
    val brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.30f * glow), accent.copy(alpha = 0.10f * glow), Color.Transparent), center = center, radius = radius)
    onDrawWithContent { if (glow > 0.01f) drawCircle(brush = brush, radius = radius, center = center, blendMode = BlendMode.Screen); drawContent() }
}

private fun Modifier.liquidSwitchSkin(glow: Float): Modifier = drawWithCache {
    val base = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f + 0.10f * glow), Color(0xFF8DF9EA).copy(alpha = 0.12f * glow), Color.Black.copy(alpha = 0.10f)), start = Offset.Zero, end = Offset(size.width, size.height))
    val light = Brush.radialGradient(listOf(Color(0xFF8DF9EA).copy(alpha = 0.32f * glow), Color.Transparent), center = Offset(size.width * 0.70f, size.height * 0.45f), radius = size.width * 0.66f)
    onDrawWithContent { drawRoundRect(base, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f), blendMode = BlendMode.Screen); if (glow > 0.01f) drawRoundRect(light, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f), blendMode = BlendMode.Screen); drawContent() }
}

private fun Modifier.liquidSliderGlow(percent: Float, pulse: Float): Modifier = drawWithCache {
    val center = Offset(size.width * percent.coerceIn(0f, 1f), size.height * 0.50f)
    val brush = Brush.radialGradient(listOf(Color(0xFF8DF9EA).copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f), Color.Transparent), center = center, radius = size.height * (1.05f + 0.12f * pulse))
    onDrawWithContent { drawRect(brush, blendMode = BlendMode.Screen); drawContent() }
}

private fun Modifier.debugRowSkin(percent: Float): Modifier = drawWithCache {
    val shape = CornerRadius(18.dp.toPx(), 18.dp.toPx())
    val base = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.095f), Color.White.copy(alpha = 0.035f), Color.Black.copy(alpha = 0.055f)), start = Offset.Zero, end = Offset(size.width, size.height))
    val glow = Brush.radialGradient(listOf(Color(0xFF8DF9EA).copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * percent.coerceIn(0f, 1f), size.height * 0.54f), radius = size.height * 1.2f)
    onDrawWithContent { drawRoundRect(base, cornerRadius = shape, blendMode = BlendMode.Screen); drawRoundRect(glow, cornerRadius = shape, blendMode = BlendMode.Screen); drawContent() }
}

private fun debugGroupTitle(group: String): String = when (group) {
    "背景" -> "背景缓存 / 毛玻璃底图"
    "天空" -> "天空细节 / 默认背景"
    "OpenGL" -> "OpenGL 水滴折射"
    else -> "Compose 玻璃外观"
}

private fun debugGroupSubtitle(group: String): String = when (group) {
    "背景" -> "控制模糊 bitmap 的缓存、柔化和颜色处理。"
    "天空" -> "调默认夜空、云层和月牙的绘制参数。"
    "OpenGL" -> "集中调 OpenGL / AGSL 透明水滴、折射、采样和色散。"
    else -> "集中调普通 Compose 玻璃的雾面、边缘、边框、高光和阴影。"
}

private fun qualityLabelV2(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun glassPresetLabelV2(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

private fun themeLabelV2(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}

private fun Float.formatSettingValueV2(): String = "${((this * 100).roundToInt() / 100f)}"
