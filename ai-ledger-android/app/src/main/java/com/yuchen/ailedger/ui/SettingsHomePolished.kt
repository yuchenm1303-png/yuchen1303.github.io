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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    var displayExpanded by rememberSaveable { mutableStateOf(false) }
    var glassExpanded by rememberSaveable { mutableStateOf(false) }
    var assistantExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var serviceExpanded by rememberSaveable { mutableStateOf(false) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "settings-header") { SettingsHeaderV2() }
        item(key = "display-background") {
            ExpandableSettingsSection(
                state = state,
                title = "显示与背景",
                subtitle = "背景、图片和页面观感。",
                glyph = "景",
                accent = Color(0xFF8DF9EA),
                expanded = displayExpanded,
                onToggle = { displayExpanded = !displayExpanded }
            ) {
                SettingOptionGrid(
                    items = BackgroundTheme.entries,
                    selected = state.backgroundTheme,
                    label = { themeLabelV2(it) },
                    state = state,
                    onSelected = onBackgroundThemeChange
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    LiquidActionCard("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
                    LiquidActionCard("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                }
            }
        }
        item(key = "glass-motion") {
            ExpandableSettingsSection(
                state = state,
                title = "玻璃与流畅度",
                subtitle = "日常可调项，不碰底层折射算法。",
                glyph = "璃",
                accent = Color(0xFF9EB7FF),
                expanded = glassExpanded,
                onToggle = { glassExpanded = !glassExpanded }
            ) {
                SettingOptionGrid(RenderQuality.entries, state.quality, { qualityLabelV2(it) }, state, onQualityChange)
                SettingOptionGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabelV2(it) }, state, onGlassPresetChange)
                LiquidGlassSlider("玻璃强度", "卡片雾面与边缘存在感", state.glassIntensity, 0.6f..1.4f, state, onGlassIntensityChange)
                LiquidGlassSlider("动态强度", "呼吸、滑动和弹性动画", state.motionIntensity, 0f..1.4f, state, onMotionIntensityChange)
            }
        }
        item(key = "assistant-preference") {
            ExpandableSettingsSection(
                state = state,
                title = "助手偏好",
                subtitle = "首页聊天体验和默认行为。",
                glyph = "AI",
                accent = Color(0xFFFFD166),
                expanded = assistantExpanded,
                onToggle = { assistantExpanded = !assistantExpanded }
            ) {
                LiquidSwitchRow(
                    title = "聊天预览",
                    subtitle = "打开后首页保留示例对话和建议词。",
                    checked = state.showPreviewConversation,
                    state = state,
                    onCheckedChange = onPreviewConversationChange
                )
                SettingInfoGlass("默认模型", state.selectedModelLabel, state)
                SettingInfoGlass("首页消息", "${state.messages.size} 条", state)
            }
        }
        item(key = "data-budget") {
            ExpandableSettingsSection(
                state = state,
                title = "数据与预算",
                subtitle = "账单状态、预算和后续同步入口。",
                glyph = "数",
                accent = Color(0xFFFFB4D2),
                expanded = dataExpanded,
                onToggle = { dataExpanded = !dataExpanded }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniSettingGlass("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
                    MiniSettingGlass("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", state, Modifier.weight(1f))
                    MiniSettingGlass("同步", "本地", state, Modifier.weight(1f))
                }
                SettingInfoGlass("数据保存", "当前为内存预览，后续可接 DataStore / Room", state)
            }
        }
        item(key = "service-status") {
            ExpandableSettingsSection(
                state = state,
                title = "服务状态",
                subtitle = "AI Worker、云端接口和本地执行。",
                glyph = "云",
                accent = Color(0xFFC7A8FF),
                expanded = serviceExpanded,
                onToggle = { serviceExpanded = !serviceExpanded }
            ) {
                SettingInfoGlass("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint, state)
                SettingInfoGlass("执行模式", "本地动作优先，复杂问题交给云端", state)
            }
        }
        item(key = "advanced-debug") {
            ExpandableSettingsSection(
                state = state,
                title = "高级玻璃调试",
                subtitle = "背景缓存、OpenGL 折射和边框高光。",
                glyph = "调",
                accent = Color(0xFF8DF9EA),
                expanded = debugExpanded,
                onToggle = { debugExpanded = !debugExpanded }
            ) {
                DebugGroupTitle("背景模糊缓存")
                LiquidGlassSlider("缓存分辨率", "越高越细，越低越省性能", state.backdropParams.scale, 0.04f..2.00f, state) { onBackdropChange(state.backdropParams.copy(scale = it)) }
                LiquidGlassSlider("模糊半径", "背景毛玻璃的柔化范围", state.backdropParams.radius, 0f..180f, state) { onBackdropChange(state.backdropParams.copy(radius = it.roundToInt().toFloat())) }
                LiquidGlassSlider("模糊迭代", "越高越柔，但更耗性能", state.backdropParams.iterations, 1f..48f, state) { onBackdropChange(state.backdropParams.copy(iterations = it.roundToInt().toFloat())) }
                LiquidGlassSlider("亮度", "背景采样后的提亮程度", state.backdropParams.brightness, 0.00f..6.00f, state) { onBackdropChange(state.backdropParams.copy(brightness = it)) }
                LiquidGlassSlider("对比度", "玻璃内部背景层次", state.backdropParams.contrast, 0.00f..8.00f, state) { onBackdropChange(state.backdropParams.copy(contrast = it)) }
                LiquidGlassSlider("饱和度", "背景颜色浓淡", state.backdropParams.saturation, 0.00f..8.00f, state) { onBackdropChange(state.backdropParams.copy(saturation = it)) }

                DebugGroupTitle("天空细节")
                LiquidGlassSlider("云层强度", "默认背景云雾存在感", state.backdropParams.cloudAlpha, 0f..1.8f, state) { onBackdropChange(state.backdropParams.copy(cloudAlpha = it)) }
                LiquidGlassSlider("云层柔度", "云雾边缘软硬", state.backdropParams.cloudSoftness, 0.6f..2.2f, state) { onBackdropChange(state.backdropParams.copy(cloudSoftness = it)) }
                LiquidGlassSlider("月亮大小", "默认背景月牙尺寸", state.backdropParams.moonScale, 0.5f..1.8f, state) { onBackdropChange(state.backdropParams.copy(moonScale = it)) }
                LiquidGlassSlider("月亮光晕", "月亮周围的柔光", state.backdropParams.moonHaloAlpha, 0f..0.8f, state) { onBackdropChange(state.backdropParams.copy(moonHaloAlpha = it)) }

                DebugGroupTitle("OpenGL 透明折射核心")
                Text("范围故意放大，方便拉爆调参；最终预设先不改。", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                LiquidGlassSlider("调试橙线", "显示真实 SDF 边界", state.glassBorderStyle.openGlDebugLineAlpha, 0f..1f, state) { onBorderChange(state.glassBorderStyle.copy(openGlDebugLineAlpha = it)) }
                LiquidGlassSlider("整体可见度/透明度", "OpenGL 玻璃层存在感", state.glassBorderStyle.openGlVisibility, 0f..20f, state) { onBorderChange(state.glassBorderStyle.copy(openGlVisibility = it)) }
                LiquidGlassSlider("主体 Alpha", "整体材质不透明度", state.glassBorderStyle.openGlMaxAlpha, 0f..1f, state) { onBorderChange(state.glassBorderStyle.copy(openGlMaxAlpha = it)) }
                LiquidGlassSlider("背景亮度", "玻璃内部采样亮度", state.glassBorderStyle.edgeBrightness, -2f..6f, state) { onBorderChange(state.glassBorderStyle.copy(edgeBrightness = it)) }
                LiquidGlassSlider("主体折射 px", "中心区域轻微连续折射", state.glassBorderStyle.openGlPullScale, -1200f..1200f, state) { onBorderChange(state.glassBorderStyle.copy(openGlPullScale = it)) }
                LiquidGlassSlider("边缘折射 px", "边缘把背景向内/外拉动", state.glassBorderStyle.edgePullDp, -2400f..2400f, state) { onBorderChange(state.glassBorderStyle.copy(edgePullDp = it)) }
                LiquidGlassSlider("边缘宽度 px", "iOS 透镜压缩带宽度", state.glassBorderStyle.ringWidthDp, 0f..900f, state) { onBorderChange(state.glassBorderStyle.copy(ringWidthDp = it.roundToInt().toFloat())) }
                LiquidGlassSlider("lens 清晰混入", "清晰纹理参与折射比例", state.glassBorderStyle.openGlCompressionScale, -10f..10f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCompressionScale = it)) }
                LiquidGlassSlider("梯度放大", "厚度场梯度增强", state.glassBorderStyle.openGlCornerScale, 0f..800f, state) { onBorderChange(state.glassBorderStyle.copy(openGlCornerScale = it)) }
                LiquidGlassSlider("额外模糊 px", "边缘折射区再柔化", state.glassBorderStyle.openGlSampleRadiusScale, 0f..600f, state) { onBorderChange(state.glassBorderStyle.copy(openGlSampleRadiusScale = it)) }
                LiquidGlassSlider("内侧暗带", "边缘内侧压暗厚度感", state.glassBorderStyle.openGlDarkScale, -12f..12f, state) { onBorderChange(state.glassBorderStyle.copy(openGlDarkScale = it)) }

                DebugGroupTitle("旧边框/雾面（默认全关）")
                LiquidGlassSlider("主体雾面", "玻璃中心雾面覆盖", state.glassBorderStyle.bodyAlpha, -5f..5f, state) { onBorderChange(state.glassBorderStyle.copy(bodyAlpha = it)) }
                LiquidGlassSlider("外边框", "最外层轮廓高光", state.glassBorderStyle.outerStrokeAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(outerStrokeAlpha = it)) }
                LiquidGlassSlider("内边框", "内侧细线高光", state.glassBorderStyle.innerStrokeAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(innerStrokeAlpha = it)) }
                LiquidGlassSlider("顶部高光", "卡片顶部发亮边缘", state.glassBorderStyle.topHighlightAlpha, 0.00f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(topHighlightAlpha = it)) }
                LiquidGlassSlider("底部暗边", "卡片底部压暗层", state.glassBorderStyle.bottomShadowAlpha, 0f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(bottomShadowAlpha = it)) }
                LiquidGlassSlider("圆角 glint", "圆角小高光", state.glassBorderStyle.cornerGlintAlpha, 0f..2.00f, state) { onBorderChange(state.glassBorderStyle.copy(cornerGlintAlpha = it)) }
            }
        }
    }
}

@Composable
private fun SettingsHeaderV2() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("设置", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text("点开小栏目再调具体内容，滑动时不会自动展开。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpandableSettingsSection(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    glyph: String,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-section-arrow"
    )
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (expanded) 1.02f else 0.94f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlow(glow = if (expanded) 0.42f else 0.10f, pulse = 1f, accent = accent),
        role = if (expanded) GlassRole.Shell else GlassRole.Card
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity * if (expanded) 1.04f else 0.92f,
                motionIntensity = state.motionIntensity,
                radius = 24,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                role = if (expanded) GlassRole.Floating else GlassRole.Chip,
                onClick = onToggle
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionGlyph(glyph = glyph, active = expanded, accent = accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("⌄", color = Color.White.copy(alpha = 0.60f), fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { rotationZ = rotation })
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SectionGlyph(glyph: String, active: Boolean, accent: Color) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0.18f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "section-glyph-glow"
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .settingsGlyphGlow(glow = glow, pulse = 1f, accent = accent),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (active) 0.10f else 0.045f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White.copy(alpha = if (active) 0.96f else 0.66f), fontSize = if (glyph.length > 1) 11.sp else 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun <T> SettingOptionGrid(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    state: AssistantUiState,
    onSelected: (T) -> Unit
) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                val pop by animateFloatAsState(
                    targetValue = if (active) 1.014f else 1f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
                    label = "setting-chip-pop"
                )
                PressableGlass(
                    state.quality,
                    state.glassIntensity * if (active) 1.04f else 0.90f,
                    state.motionIntensity,
                    999,
                    Modifier.weight(1f).height(34.dp).graphicsLayer { scaleX = pop; scaleY = pop },
                    if (active) GlassRole.Floating else GlassRole.Chip,
                    onClick = { onSelected(item) }
                ) {
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
private fun LiquidSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    state: AssistantUiState,
    onCheckedChange: (Boolean) -> Unit
) {
    val glow by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "liquid-switch-glow"
    )
    PressableGlass(
        state.quality,
        state.glassIntensity * if (checked) 1.05f else 0.92f,
        state.motionIntensity,
        24,
        Modifier.fillMaxWidth().height(58.dp).settingsGlow(glow * 0.42f, 1f, Color(0xFF8DF9EA)),
        if (checked) GlassRole.Floating else GlassRole.Card,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LiquidSwitch(checked = checked, glow = glow)
        }
    }
}

@Composable
private fun LiquidSwitch(checked: Boolean, glow: Float) {
    val knobX by animateDpAsState(
        targetValue = if (checked) 27.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "liquid-switch-knob"
    )
    val knobScale by animateFloatAsState(
        targetValue = if (checked) 1.05f else 0.95f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "liquid-switch-knob-scale"
    )
    Box(
        Modifier
            .width(58.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .liquidSwitchSkin(glow),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(26.dp)
                .graphicsLayer { scaleX = knobScale; scaleY = knobScale }
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = if (checked) 0.96f else 0.78f))
        )
    }
}

@Composable
private fun LiquidGlassSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    state: AssistantUiState,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val percent = ((clamped - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(72.dp), GlassRole.Card) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(clamped.formatSettingValueV2(), color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .liquidSliderGlow(percent = percent, pulse = 1f)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = clamped,
                    onValueChange = onValueChange,
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White.copy(alpha = 0.96f),
                        activeTrackColor = Color(0xFF8DF9EA).copy(alpha = 0.56f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
            }
        }
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

@Composable
private fun DebugGroupTitle(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
}

private fun Modifier.settingsGlow(glow: Float, pulse: Float, accent: Color): Modifier = drawWithCache {
    val radius = min(size.width, size.height) * (1.22f + 0.10f * pulse)
    val brush = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = 0.13f * glow),
            Color.White.copy(alpha = 0.045f * glow),
            Color.Transparent
        ),
        center = Offset(size.width * 0.18f, size.height * 0.16f),
        radius = radius
    )
    onDrawWithContent {
        if (glow > 0.01f) drawRect(brush, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.settingsGlyphGlow(glow: Float, pulse: Float, accent: Color): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * (0.72f + 0.10f * pulse)
    val brush = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = 0.30f * glow),
            accent.copy(alpha = 0.10f * glow),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    onDrawWithContent {
        if (glow > 0.01f) drawCircle(brush = brush, radius = radius, center = center, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.liquidSwitchSkin(glow: Float): Modifier = drawWithCache {
    val base = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.18f + 0.10f * glow),
            Color(0xFF8DF9EA).copy(alpha = 0.12f * glow),
            Color.Black.copy(alpha = 0.10f)
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val light = Brush.radialGradient(
        colors = listOf(Color(0xFF8DF9EA).copy(alpha = 0.32f * glow), Color.Transparent),
        center = Offset(size.width * 0.70f, size.height * 0.45f),
        radius = size.width * 0.66f
    )
    onDrawWithContent {
        drawRoundRect(base, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f), blendMode = BlendMode.Screen)
        if (glow > 0.01f) drawRoundRect(light, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f), blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun Modifier.liquidSliderGlow(percent: Float, pulse: Float): Modifier = drawWithCache {
    val center = Offset(size.width * percent.coerceIn(0f, 1f), size.height * 0.50f)
    val radius = size.height * (1.05f + 0.12f * pulse)
    val brush = Brush.radialGradient(
        colors = listOf(Color(0xFF8DF9EA).copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f), Color.Transparent),
        center = center,
        radius = radius
    )
    onDrawWithContent {
        drawRect(brush, blendMode = BlendMode.Screen)
        drawContent()
    }
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

private fun Float.formatSettingValueV2(): String {
    val rounded = (this * 100).roundToInt() / 100f
    return "${rounded}x"
}
