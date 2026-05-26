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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class SettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Debug }

private val SettingsOverviewRole = GlassRole.Shell
private val SettingsTileRole = GlassRole.Card
private val SettingsDetailRole = GlassRole.Flex
private val SettingsChipRole = GlassRole.Chip
private val SettingsFloatingRole = GlassRole.Floating

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
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    val listState = rememberLazyListState()
    SyncGlassBackdropToScroll(listState)
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsPanel.Appearance) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { SettingsEntrance(delayMs = 0, initialOffsetY = -8, initialScale = 0.985f) { SettingsHeader() } }
        item { SettingsEntrance(delayMs = 42, initialOffsetY = 18, initialScale = 0.965f) { SettingsOverviewCard(state, aiEndpoint) } }
        item { SettingsEntrance(delayMs = 78, initialOffsetY = 18, initialScale = 0.97f) { SettingsSectionTitle("常用设置", "用入口卡片快速扫读，详情只在需要时打开。") } }
        item {
            SettingsEntrance(delayMs = 104, initialOffsetY = 20, initialScale = 0.965f) {
                SettingsDashboardGrid(
                    state = state,
                    aiEndpoint = aiEndpoint,
                    selectedPanel = selectedPanel,
                    onSelected = { selectedPanel = it }
                )
            }
        }
        item {
            SettingsEntrance(delayMs = 140, initialOffsetY = 22, initialScale = 0.965f) {
                SettingsDetailPanel(
                    panel = selectedPanel,
                    state = state,
                    aiEndpoint = aiEndpoint,
                    onQualityChange = onQualityChange,
                    onPreviewConversationChange = onPreviewConversationChange,
                    onGlassPresetChange = onGlassPresetChange,
                    onBackgroundThemeChange = onBackgroundThemeChange,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onMotionIntensityChange = onMotionIntensityChange,
                    onRainbowPrismChange = onRainbowPrismChange,
                    onBackdropChange = onBackdropChange,
                    onBorderChange = onBorderChange,
                    onUploadBackgroundClick = onUploadBackgroundClick,
                    onClearCustomBackgroundClick = onClearCustomBackgroundClick
                )
            }
        }
        item {
            SettingsEntrance(delayMs = 176, initialOffsetY = 24, initialScale = 0.96f) {
                SettingsLabEntry(state = state, selected = selectedPanel == SettingsPanel.Debug) { selectedPanel = SettingsPanel.Debug }
            }
        }
    }
}

@Composable
private fun SettingsEntrance(delayMs: Long, initialOffsetY: Int = 24, initialScale: Float = 0.96f, content: @Composable () -> Unit) {
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
    ) { content() }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("常用项直接展示，复杂参数收进详情面板。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(state.quality, state.glassIntensity * 0.98f, state.motionIntensity, 30, Modifier.fillMaxWidth(), SettingsOverviewRole) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前状态", color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("接口、画质和关键外观集中展示。", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 17.sp)
                }
                SettingsStatusBadge(if (aiEndpoint.isBlank()) "本地优先" else "云端已配置", state)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniSettingMetric("服务", if (aiEndpoint.isBlank()) "本地" else "已连接", state, Modifier.weight(1f))
                MiniSettingMetric("画质", qualityLabel(state.quality), state, Modifier.weight(1f))
                MiniSettingMetric("背景", themeLabel(state.backgroundTheme), state, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniSettingMetric("玻璃", glassPresetLabel(state.glassPreset), state, Modifier.weight(1f))
                MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
                MiniSettingMetric("OpenGL", "隔离", state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 3.dp, start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsDashboardGrid(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsPanel,
    onSelected: (SettingsPanel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("景", "外观", "背景与主题", themeLabel(state.backgroundTheme), selectedPanel == SettingsPanel.Appearance, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Appearance) }
            SettingsTile("璃", "玻璃", "质感与流畅度", "${qualityLabel(state.quality)} · ${glassPresetLabel(state.glassPreset)}", selectedPanel == SettingsPanel.Glass, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Glass) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("助", "助手", "模型与首页", state.selectedModelLabel, selectedPanel == SettingsPanel.Assistant, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Assistant) }
            SettingsTile("账", "数据", "预算与账单", "${state.ledgerRecords.size} 笔", selectedPanel == SettingsPanel.Data, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Data) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("云", "服务", "AI Worker", if (aiEndpoint.isBlank()) "未配置" else "已连接", selectedPanel == SettingsPanel.Service, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Service) }
            SettingsTile("GL", "高级", "渲染边界", "OpenGL 隔离", selectedPanel == SettingsPanel.Advanced, state, Modifier.weight(1f)) { onSelected(SettingsPanel.Advanced) }
        }
    }
}

@Composable
private fun SettingsTile(
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tile-pop-$title"
    )
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (selected) 1.02f else 0.86f,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = modifier.height(104.dp).graphicsLayer { scaleX = pop; scaleY = pop },
        role = SettingsTileRole,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsIconBadge(icon, state, selected)
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SettingsValuePill(value, state, selected)
        }
    }
}

@Composable
private fun SettingsDetailPanel(
    panel: SettingsPanel,
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity * 0.82f, state.motionIntensity, 28, Modifier.fillMaxWidth(), SettingsDetailRole) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeader(panelTitle(panel), panelSubtitle(panel))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(160)) + expandVertically(tween(180)) + scaleIn(initialScale = 0.98f, animationSpec = tween(180)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(120)) + scaleOut(targetScale = 0.99f, animationSpec = tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    when (panel) {
                        SettingsPanel.Appearance -> AppearanceContent(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick)
                        SettingsPanel.Glass -> GlassContent(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange, onRainbowPrismChange)
                        SettingsPanel.Assistant -> AssistantContent(state, onPreviewConversationChange)
                        SettingsPanel.Data -> DataContent(state)
                        SettingsPanel.Service -> ServiceContent(state, aiEndpoint)
                        SettingsPanel.Advanced -> AdvancedContent(state)
                        SettingsPanel.Debug -> GlassDebugFloatingPanel(
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
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppearanceContent(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingChipGrid(BackgroundTheme.entries, state.backgroundTheme, { themeLabel(it) }, state, onBackgroundThemeChange)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SettingActionButton("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
        SettingActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
    }
}

@Composable
private fun GlassContent(
    state: AssistantUiState,
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit
) {
    val prism = state.rainbowPrismStyle
    SettingChipGrid(RenderQuality.entries, state.quality, { qualityLabel(it) }, state, onQualityChange)
    SettingChipGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabel(it) }, state, onGlassPresetChange)
    SliderSettingRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, state, onGlassIntensityChange)
    SliderSettingRow("动态强度", state.motionIntensity, 0f..1.4f, state, onMotionIntensityChange)
    SectionTitleInline("首页聊天大玻璃彩虹")
    SliderSettingRow("整体彩虹强度", prism.overall, 0f..2f, state) { onRainbowPrismChange(prism.copy(overall = it)) }
    SliderSettingRow("棱彩边缘高光", prism.edgeHighlight, 0f..2f, state) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
    SliderSettingRow("斜向彩色扫光", prism.diagonalSweep, 0f..2f, state) { onRainbowPrismChange(prism.copy(diagonalSweep = it)) }
}

@Composable
private fun AssistantContent(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("聊天预览", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("打开后首页会保留示例对话和建议词。", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
    }
    SettingInfoRow("默认模型", state.selectedModelLabel, state)
    SettingInfoRow("首页消息", "${state.messages.size} 条", state)
    SettingInfoRow("联网模式", if (state.onlineEnabled) "已开启" else "已关闭", state)
}

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
        MiniSettingMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", state, Modifier.weight(1f))
        MiniSettingMetric("同步", "本地", state, Modifier.weight(1f))
    }
    SettingInfoRow("数据保存", "当前为内存预览，重启后恢复示例数据", state)
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint, state)
    SettingInfoRow("执行模式", "本地动作优先，复杂问题后续交给云端", state)
}

@Composable
private fun AdvancedContent(state: AssistantUiState) {
    SettingInfoRow("玻璃渲染", "设置页仅运行概览大卡使用 OpenGL", state)
    SettingInfoRow("普通控件", "Card / Chip / Nav / Floating / Flex 完全隔离", state)
    SettingInfoRow("调试入口", "底部玻璃实验室可调整底层参数", state)
}

@Composable
private fun SettingsLabEntry(state: AssistantUiState, selected: Boolean, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * if (selected) 0.92f else 0.76f, state.motionIntensity, 26, Modifier.fillMaxWidth().height(62.dp), SettingsTileRole, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge("⚗", state, selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("玻璃实验室", color = Color.White.copy(alpha = 0.90f), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("高级调试与实验功能", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (selected) "已打开" else "进入", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitleInline(title: String) {
    Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun SettingsIconBadge(text: String, state: AssistantUiState, active: Boolean) {
    GlassPanel(state.quality, state.glassIntensity * if (active) 0.96f else 0.70f, state.motionIntensity, 17, Modifier.size(42.dp), if (active) SettingsFloatingRole else SettingsChipRole) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (active) 0.94f else 0.66f), fontSize = if (text.length > 1) 13.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun <T> SettingChipGrid(items: List<T>, selected: T, label: (T) -> String, state: AssistantUiState, onSelected: (T) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.weight(1f).height(42.dp), if (active) SettingsFloatingRole else SettingsChipRole, onClick = { onSelected(item) }) {
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
private fun SliderSettingRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, state: AssistantUiState, onValueChange: (Float) -> Unit) {
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
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 23, modifier.height(58.dp), SettingsChipRole, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity * 0.82f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(52.dp), SettingsChipRole) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniSettingMetric(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.82f, state.motionIntensity, 20, modifier.height(54.dp), SettingsChipRole) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.50f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsStatusBadge(text: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.height(36.dp), SettingsFloatingRole) {
        Box(Modifier.padding(horizontal = 13.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsValuePill(text: String, state: AssistantUiState, selected: Boolean) {
    GlassPanel(state.quality, state.glassIntensity * if (selected) 0.72f else 0.56f, state.motionIntensity, 999, Modifier.height(28.dp).widthIn(min = 54.dp, max = 128.dp), if (selected) SettingsFloatingRole else SettingsChipRole) {
        Box(Modifier.padding(horizontal = 10.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.82f else 0.60f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

private fun panelTitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "外观"
    SettingsPanel.Glass -> "玻璃"
    SettingsPanel.Assistant -> "助手"
    SettingsPanel.Data -> "数据"
    SettingsPanel.Service -> "服务"
    SettingsPanel.Advanced -> "高级"
    SettingsPanel.Debug -> "玻璃实验室"
}

private fun panelSubtitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "背景、主题和自定义图片。"
    SettingsPanel.Glass -> "画质、玻璃质感和聊天大玻璃彩虹。"
    SettingsPanel.Assistant -> "模型、联网和首页展示。"
    SettingsPanel.Data -> "账单状态、预算和本地数据。"
    SettingsPanel.Service -> "AI Worker、云端接口和执行模式。"
    SettingsPanel.Advanced -> "渲染边界和 OpenGL 隔离状态。"
    SettingsPanel.Debug -> "高级玻璃参数与实验入口。"
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

private fun Float.formatSettingValue(): String = (this * 100).roundToInt().div(100f).toString()
