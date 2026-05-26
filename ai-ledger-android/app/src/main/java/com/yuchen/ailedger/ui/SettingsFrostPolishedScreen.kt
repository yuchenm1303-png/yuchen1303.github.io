package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
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

private enum class FrostSettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Debug }

@Composable
fun SettingsFrostPolishedScreen(
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
    var selectedPanel by rememberSaveable { mutableStateOf(FrostSettingsPanel.Appearance) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { SettingsFrostEntrance(0, -8, 0.985f) { SettingsFrostHeader() } }
        item { SettingsFrostEntrance(42, 18, 0.965f) { SettingsFrostOverviewShell(state, aiEndpoint) } }
        item { SettingsFrostEntrance(78, 18, 0.97f) { SettingsFrostSectionTitle("常用设置", "六张入口卡片已换成雾面信息玻璃，普通控件仍与 OpenGL 隔离。") } }
        item { SettingsFrostEntrance(104, 20, 0.965f) { SettingsFrostDashboardGrid(state, aiEndpoint, selectedPanel) { selectedPanel = it } } }
        item {
            SettingsFrostEntrance(140, 22, 0.965f) {
                SettingsFrostDetailPanel(
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
        item { SettingsFrostEntrance(176, 24, 0.96f) { SettingsFrostLabEntry(state, selectedPanel == FrostSettingsPanel.Debug) { selectedPanel = FrostSettingsPanel.Debug } } }
    }
}

@Composable
private fun SettingsFrostEntrance(delayMs: Long, initialOffsetY: Int = 24, initialScale: Float = 0.96f, content: @Composable () -> Unit) {
    var visible by rememberSaveable(delayMs) { mutableStateOf(false) }
    LaunchedEffect(delayMs) { if (!visible) { if (delayMs > 0L) delay(delayMs); visible = true } }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } + scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.985f, animationSpec = tween(120))
    ) { content() }
}

@Composable
private fun SettingsFrostHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("雾面信息玻璃承载信息，背景大容器保留 OpenGL 表现。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsFrostOverviewShell(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(state.quality, state.glassIntensity * 0.98f, state.motionIntensity, 32, Modifier.fillMaxWidth(), GlassRole.Shell) {
        SettingsFrostOverviewGlass(
            title = "当前状态",
            subtitle = "服务、画质、背景、玻璃、账单与 OpenGL 状态集中到一张雾面信息玻璃。",
            badge = if (aiEndpoint.isBlank()) "本地优先" else "云端已配置",
            metrics = listOf(
                FrostSettingMetric("服务", if (aiEndpoint.isBlank()) "本地" else "已连接"),
                FrostSettingMetric("画质", frostQualityLabel(state.quality)),
                FrostSettingMetric("背景", frostThemeLabel(state.backgroundTheme)),
                FrostSettingMetric("玻璃", frostPresetLabel(state.glassPreset)),
                FrostSettingMetric("账单", "${state.ledgerRecords.size} 笔"),
                FrostSettingMetric("OpenGL", "隔离")
            ),
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun SettingsFrostSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 3.dp, start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsFrostDashboardGrid(state: AssistantUiState, aiEndpoint: String, selectedPanel: FrostSettingsPanel, onSelected: (FrostSettingsPanel) -> Unit) {
    val tiles = listOf(
        FrostTileSpec("景", "外观", "背景与主题", frostThemeLabel(state.backgroundTheme), FrostSettingsPanel.Appearance),
        FrostTileSpec("璃", "玻璃", "质感与流畅度", "${frostQualityLabel(state.quality)} · ${frostPresetLabel(state.glassPreset)}", FrostSettingsPanel.Glass),
        FrostTileSpec("助", "助手", "模型与首页", state.selectedModelLabel, FrostSettingsPanel.Assistant),
        FrostTileSpec("账", "数据", "预算与账单", "${state.ledgerRecords.size} 笔", FrostSettingsPanel.Data),
        FrostTileSpec("云", "服务", "AI Worker", if (aiEndpoint.isBlank()) "未配置" else "已连接", FrostSettingsPanel.Service),
        FrostTileSpec("GL", "高级", "渲染边界", "OpenGL 隔离", FrostSettingsPanel.Advanced)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    SettingsFrostTileGlass(
                        icon = tile.icon,
                        title = tile.title,
                        subtitle = tile.subtitle,
                        value = tile.value,
                        selected = selectedPanel == tile.panel,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(tile.panel) }
                    )
                }
            }
        }
    }
}

private data class FrostTileSpec(val icon: String, val title: String, val subtitle: String, val value: String, val panel: FrostSettingsPanel)

@Composable
private fun SettingsFrostDetailPanel(panel: FrostSettingsPanel, state: AssistantUiState, aiEndpoint: String, onQualityChange: (RenderQuality) -> Unit, onPreviewConversationChange: (Boolean) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit, onRainbowPrismChange: (RainbowPrismStyle) -> Unit, onBackdropChange: (BackdropDebugParams) -> Unit, onBorderChange: (GlassBorderStyle) -> Unit, onUploadBackgroundClick: () -> Unit, onClearCustomBackgroundClick: () -> Unit) {
    FrostInfoGlassPanel(radius = 28f, backdropAlpha = 0.88f, frostAlpha = 0.032f, dimAlpha = 0.075f, modifier = Modifier.fillMaxWidth().frostGlassEdgeHighlight(radius = 28f, active = false)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsFrostDetailHeader(frostPanelTitle(panel), frostPanelSubtitle(panel))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                when (panel) {
                    FrostSettingsPanel.Appearance -> FrostAppearanceContent(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick)
                    FrostSettingsPanel.Glass -> FrostGlassContent(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange, onRainbowPrismChange)
                    FrostSettingsPanel.Assistant -> FrostAssistantContent(state, onPreviewConversationChange)
                    FrostSettingsPanel.Data -> FrostDataContent(state)
                    FrostSettingsPanel.Service -> FrostServiceContent(state, aiEndpoint)
                    FrostSettingsPanel.Advanced -> FrostAdvancedContent(state)
                    FrostSettingsPanel.Debug -> GlassDebugFloatingPanel(state, onBackdropChange, onBorderChange, onUploadBackgroundClick, onClearCustomBackgroundClick, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SettingsFrostDetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FrostAppearanceContent(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onUploadBackgroundClick: () -> Unit, onClearCustomBackgroundClick: () -> Unit) {
    FrostChipGrid(BackgroundTheme.entries, state.backgroundTheme, { frostThemeLabel(it) }, onBackgroundThemeChange)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        FrostActionButton("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", Modifier.weight(1f), onUploadBackgroundClick)
        FrostActionButton("清除背景", "恢复主题", Modifier.weight(1f), onClearCustomBackgroundClick)
    }
}

@Composable
private fun FrostGlassContent(state: AssistantUiState, onQualityChange: (RenderQuality) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit, onRainbowPrismChange: (RainbowPrismStyle) -> Unit) {
    val prism = state.rainbowPrismStyle
    FrostChipGrid(RenderQuality.entries, state.quality, { frostQualityLabel(it) }, onQualityChange)
    FrostChipGrid(GlassPreset.entries, state.glassPreset, { frostPresetLabel(it) }, onGlassPresetChange)
    FrostSliderRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, onGlassIntensityChange)
    FrostSliderRow("动态强度", state.motionIntensity, 0f..1.4f, onMotionIntensityChange)
    FrostInlineTitle("首页聊天大玻璃彩虹")
    FrostSliderRow("整体彩虹强度", prism.overall, 0f..2f) { onRainbowPrismChange(prism.copy(overall = it)) }
    FrostSliderRow("棱彩边缘高光", prism.edgeHighlight, 0f..2f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
    FrostInlineTitle("随机渐变扫光区间")
    FrostSliderRow("扫光强度下限", prism.sweepMin, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
    FrostSliderRow("扫光强度上限", prism.sweepMax, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    FrostSliderRow("粉金青蓝彩虹光晕", prism.rainbowHalo, 0f..2f) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
}

@Composable
private fun FrostAssistantContent(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("聊天预览", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("打开后首页会保留示例对话和建议词。", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
    }
    FrostInfoRow("默认模型", state.selectedModelLabel)
    FrostInfoRow("首页消息", "${state.messages.size} 条")
    FrostInfoRow("联网模式", if (state.onlineEnabled) "已开启" else "已关闭")
}

@Composable
private fun FrostDataContent(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FrostMiniMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
        FrostMiniMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", Modifier.weight(1f))
        FrostMiniMetric("同步", "本地", Modifier.weight(1f))
    }
    FrostInfoRow("数据保存", "当前为内存预览，重启后恢复示例数据")
}

@Composable
private fun FrostServiceContent(state: AssistantUiState, aiEndpoint: String) { FrostInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint); FrostInfoRow("执行模式", "本地动作优先，复杂问题后续交给云端") }
@Composable
private fun FrostAdvancedContent(state: AssistantUiState) { FrostInfoRow("玻璃渲染", "大背景容器使用 OpenGL"); FrostInfoRow("雾面信息玻璃", "FrostInfoGlassPanel 完全隔离 OpenGL"); FrostInfoRow("普通控件", "不注册 OpenGL registry，不触发 geometry sync") }

@Composable
private fun SettingsFrostLabEntry(state: AssistantUiState, selected: Boolean, onClick: () -> Unit) {
    SettingsFrostTileGlass("⚗", "玻璃实验室", "高级调试与实验功能", if (selected) "已打开" else "进入", selected, Modifier.fillMaxWidth(), onClick)
}

@Composable
private fun <T> FrostChipGrid(items: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                SettingsFrostTileGlass("", label(item), if (active) "当前选中" else "点击切换", label(item), active, Modifier.weight(1f), onClick = { onSelected(item) })
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FrostSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.74f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${value.frostFormatValue()}x", color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun FrostActionButton(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) { SettingsFrostTileGlass("", title, subtitle, subtitle, false, modifier, onClick) }
@Composable
private fun FrostInlineTitle(title: String) { Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black) }
@Composable
private fun FrostInfoRow(title: String, value: String) { SettingsFrostTileGlass("", title, value, value, false, Modifier.fillMaxWidth(), {}) }
@Composable
private fun FrostMiniMetric(label: String, value: String, modifier: Modifier = Modifier) { SettingsFrostTileGlass("", label, value, value, false, modifier, {}) }

private fun frostPanelTitle(panel: FrostSettingsPanel): String = when (panel) { FrostSettingsPanel.Appearance -> "外观"; FrostSettingsPanel.Glass -> "玻璃"; FrostSettingsPanel.Assistant -> "助手"; FrostSettingsPanel.Data -> "数据"; FrostSettingsPanel.Service -> "服务"; FrostSettingsPanel.Advanced -> "高级"; FrostSettingsPanel.Debug -> "玻璃实验室" }
private fun frostPanelSubtitle(panel: FrostSettingsPanel): String = when (panel) { FrostSettingsPanel.Appearance -> "背景、主题和自定义图片。"; FrostSettingsPanel.Glass -> "画质、玻璃质感和聊天大玻璃彩虹。"; FrostSettingsPanel.Assistant -> "模型、联网和首页展示。"; FrostSettingsPanel.Data -> "账单状态、预算和本地数据。"; FrostSettingsPanel.Service -> "AI Worker、云端接口和执行模式。"; FrostSettingsPanel.Advanced -> "渲染边界和 OpenGL 隔离状态。"; FrostSettingsPanel.Debug -> "高级玻璃参数与实验入口。" }
private fun frostQualityLabel(quality: RenderQuality): String = when (quality) { RenderQuality.Smooth -> "流畅"; RenderQuality.Balanced -> "均衡"; RenderQuality.Experimental -> "高画质" }
private fun frostPresetLabel(preset: GlassPreset): String = when (preset) { GlassPreset.Basic -> "基础"; GlassPreset.Blur -> "模糊"; GlassPreset.Liquid -> "液态"; GlassPreset.Safe -> "安全" }
private fun frostThemeLabel(theme: BackgroundTheme): String = when (theme) { BackgroundTheme.Aurora -> "极光"; BackgroundTheme.Jade -> "翡翠"; BackgroundTheme.Sunset -> "暮色"; BackgroundTheme.Dawn -> "晨雾" }
private fun Float.frostFormatValue(): String = (this * 100).roundToInt().div(100f).toString()
