package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SettingsPolishedScreen(
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SettingsEntrance(delayMs = 0, initialOffsetY = -8, initialScale = 0.985f) {
                SettingsHeader()
            }
        }
        item {
            SettingsEntrance(delayMs = 46, initialOffsetY = 20, initialScale = 0.965f) {
                SettingsOverviewCard(state, aiEndpoint)
            }
        }
        item {
            SettingsEntrance(delayMs = 88, initialOffsetY = 22, initialScale = 0.962f) {
                AppearanceSettingsCard(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick)
            }
        }
        item {
            SettingsEntrance(delayMs = 130, initialOffsetY = 24, initialScale = 0.96f) {
                GlassFeelSettingsCard(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange)
            }
        }
        item {
            SettingsEntrance(delayMs = 172, initialOffsetY = 24, initialScale = 0.96f) {
                AssistantPreferenceCard(state, onPreviewConversationChange)
            }
        }
        item {
            SettingsEntrance(delayMs = 214, initialOffsetY = 26, initialScale = 0.958f) {
                DataBudgetSettingsCard(state)
            }
        }
        item {
            SettingsEntrance(delayMs = 256, initialOffsetY = 26, initialScale = 0.958f) {
                ServiceSettingsCard(state, aiEndpoint)
            }
        }
        item {
            SettingsEntrance(delayMs = 298, initialOffsetY = 28, initialScale = 0.956f) {
                AdvancedSettingsCard(state)
            }
        }
        item {
            SettingsEntrance(delayMs = 340, initialOffsetY = 30, initialScale = 0.954f) {
                GlassDebugFloatingPanel(
                    state = state,
                    onBackdropChange = onBackdropChange,
                    onBorderChange = onBorderChange,
                    onUploadBackgroundClick = onUploadBackgroundClick,
                    onClearCustomBackgroundClick = onClearCustomBackgroundClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsEntrance(
    delayMs: Long,
    initialOffsetY: Int = 24,
    initialScale: Float = 0.96f,
    content: @Composable () -> Unit
) {
    var visible by rememberSaveable(delayMs) { mutableStateOf(false) }
    LaunchedEffect(delayMs) {
        if (!visible) {
            if (delayMs > 0L) delay(delayMs)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } +
            scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.985f, animationSpec = tween(120))
    ) {
        content()
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("常用设置已直接展开，必要时也可以点按栏目收起。", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    SettingsSectionCard(
        state = state,
        title = "当前状态",
        subtitle = "应用状态、接口状态和关键外观概览。",
        summary = if (aiEndpoint.isBlank()) "本地优先" else "云端已配置"
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Compose 原生版", color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("设置栏目默认展开，仍然可以点按标题收起。", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 17.sp)
            }
            SettingsStatusBadge(if (aiEndpoint.isBlank()) "本地优先" else "云端已配置", state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniSettingMetric("画质", qualityLabel(state.quality), state, Modifier.weight(1f))
            MiniSettingMetric("玻璃", glassPresetLabel(state.glassPreset), state, Modifier.weight(1f))
            MiniSettingMetric("背景", themeLabel(state.backgroundTheme), state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingsSectionCard(
        state = state,
        title = "显示与背景",
        subtitle = "背景、图片和页面观感放在最前面。",
        summary = themeLabel(state.backgroundTheme)
    ) {
        SettingChipGrid(
            items = BackgroundTheme.entries,
            selected = state.backgroundTheme,
            label = { themeLabel(it) },
            state = state,
            onSelected = onBackgroundThemeChange
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            SettingActionButton("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
            SettingActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
        }
    }
}

@Composable
private fun GlassFeelSettingsCard(
    state: AssistantUiState,
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    SettingsSectionCard(
        state = state,
        title = "玻璃与流畅度",
        subtitle = "日常可调项，细节参数放在底部玻璃调试。",
        summary = "${qualityLabel(state.quality)} · ${glassPresetLabel(state.glassPreset)}"
    ) {
        SettingChipGrid(RenderQuality.entries, state.quality, { qualityLabel(it) }, state, onQualityChange)
        SettingChipGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabel(it) }, state, onGlassPresetChange)
        SliderSettingRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, state, onGlassIntensityChange)
        SliderSettingRow("动态强度", state.motionIntensity, 0f..1.4f, state, onMotionIntensityChange)
    }
}

@Composable
private fun AssistantPreferenceCard(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    SettingsSectionCard(
        state = state,
        title = "助手偏好",
        subtitle = "控制首页展示、默认模型和对话入口。",
        summary = state.selectedModelLabel
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("聊天预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("打开后首页会保留示例对话和建议词。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
        }
        SettingInfoRow("默认模型", state.selectedModelLabel, state)
        SettingInfoRow("首页消息", "${state.messages.size} 条", state)
    }
}

@Composable
private fun DataBudgetSettingsCard(state: AssistantUiState) {
    SettingsSectionCard(
        state = state,
        title = "数据与预算",
        subtitle = "账单状态、预算概览和后续同步入口。",
        summary = "${state.ledgerRecords.size} 笔"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
            MiniSettingMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", state, Modifier.weight(1f))
            MiniSettingMetric("同步", "本地", state, Modifier.weight(1f))
        }
        SettingInfoRow("数据保存", "当前为内存预览，重启后恢复示例数据", state)
    }
}

@Composable
private fun ServiceSettingsCard(state: AssistantUiState, aiEndpoint: String) {
    SettingsSectionCard(
        state = state,
        title = "服务状态",
        subtitle = "AI Worker、云端接口和本地执行状态。",
        summary = if (aiEndpoint.isBlank()) "未配置" else "已连接"
    ) {
        SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint, state)
        SettingInfoRow("执行模式", "本地动作优先，复杂问题后续交给云端", state)
    }
}

@Composable
private fun AdvancedSettingsCard(state: AssistantUiState) {
    SettingsSectionCard(
        state = state,
        title = "高级调试",
        subtitle = "渲染策略、调试入口和架构提示。",
        summary = "OpenGL 隔离"
    ) {
        SettingInfoRow("玻璃渲染", "单卡 OpenGL 大玻璃 + 普通控件隔离", state)
        SettingInfoRow("调试入口", "继续往下滑，展开玻璃调试", state)
    }
}

@Composable
private fun SettingsSectionCard(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    summary: String = "点按收起",
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-section-arrow-$title"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-section-scale-$title"
    )

    // Settings sections are ordinary UI cards, not page-level Shell glass.
    // Keep them isolated from OpenGL so text, sliders, chips and rows remain stable.
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity * if (expanded) 1.02f else 0.94f,
                motionIntensity = state.motionIntensity,
                radius = 24,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                role = GlassRole.Chip,
                onClick = { expanded = !expanded }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(title, color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = if (expanded) 0.52f else 0.42f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        summary,
                        color = Color.White.copy(alpha = 0.54f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.28f)
                    )
                    Text(
                        "⌄",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(120)) +
                    shrinkVertically(tween(150)) +
                    scaleOut(targetScale = 0.97f, animationSpec = tween(150))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        }
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun <T> SettingChipGrid(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    state: AssistantUiState,
    onSelected: (T) -> Unit
) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                PressableGlass(
                    quality = state.quality,
                    glassIntensity = state.glassIntensity,
                    motionIntensity = state.motionIntensity,
                    radius = 999,
                    modifier = Modifier.weight(1f).height(42.dp),
                    role = if (active) GlassRole.Floating else GlassRole.Chip,
                    onClick = { onSelected(item) }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label(item), color = Color.White.copy(alpha = if (active) 0.96f else 0.62f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    state: AssistantUiState,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.74f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${value.formatSettingValue()}x", color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SettingActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(58.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(52.dp), GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniSettingMetric(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 20, modifier.height(58.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsStatusBadge(text: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.height(36.dp), GlassRole.Floating) {
        Box(Modifier.padding(horizontal = 13.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

private fun qualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun glassPresetLabel(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

private fun themeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}

private fun Float.formatSettingValue(): String {
    return (this * 100).roundToInt().div(100f).toString()
}