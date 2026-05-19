package com.yuchen.ailedger.ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
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
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader(state) }
        item { SettingsOverviewCard(state, aiEndpoint) }
        item { AppearanceSettingsCard(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick) }
        item { GlassFeelSettingsCard(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange) }
        item { AssistantPreferenceCard(state, onPreviewConversationChange) }
        item { DataBudgetSettingsCard(state) }
        item { ServiceSettingsCard(state, aiEndpoint) }
        item { AdvancedSettingsCard(state) }
    }
}

@Composable
private fun SettingsHeader(state: AssistantUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("常用设置放前面，玻璃调试收到底部，避免页面被调参项挤乱。", color = Color.White.copy(alpha = 0.62f), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(state.quality, state.glassIntensity * 1.02f, state.motionIntensity, 32, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前状态", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("原生 Compose 版", color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                }
                SettingsStatusBadge(if (aiEndpoint.isBlank()) "本地优先" else "云端已配置", state)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniSettingMetric("画质", qualityLabel(state.quality), state, Modifier.weight(1f))
                MiniSettingMetric("玻璃", glassPresetLabel(state.glassPreset), state, Modifier.weight(1f))
                MiniSettingMetric("背景", themeLabel(state.backgroundTheme), state, Modifier.weight(1f))
            }
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
    SettingsSectionCard(state, "显示与背景", "先管看得见的东西：背景、图片和页面观感。") {
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
    SettingsSectionCard(state, "玻璃与流畅度", "只调整入口参数，不改玻璃底层采样和模糊实现。") {
        SettingChipGrid(RenderQuality.entries, state.quality, { qualityLabel(it) }, state, onQualityChange)
        SettingChipGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabel(it) }, state, onGlassPresetChange)
        SliderSettingRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, state, onGlassIntensityChange)
        SliderSettingRow("动态强度", state.motionIntensity, 0f..1.4f, state, onMotionIntensityChange)
    }
}

@Composable
private fun AssistantPreferenceCard(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    SettingsSectionCard(state, "助手偏好", "控制首页是否保留引导内容。") {
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
    SettingsSectionCard(state, "数据与预算", "这里先显示账单状态，后续可接导出、清空和同步。") {
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
    SettingsSectionCard(state, "服务状态", "AI Worker、云端接口和本地执行状态。") {
        SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint, state)
        SettingInfoRow("执行模式", "本地动作优先，复杂问题后续交给云端", state)
    }
}

@Composable
private fun AdvancedSettingsCard(state: AssistantUiState) {
    SettingsSectionCard(state, "高级调试", "玻璃参数调试面板已从顶部浮层收起，避免遮挡设置内容。") {
        SettingInfoRow("背景采样", "由 Glass / Backdrop 相关文件继续维护", state)
        SettingInfoRow("当前建议", "普通使用只调画质、背景和强度", state)
    }
}

@Composable
private fun SettingsSectionCard(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    content: @Composable Column.() -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, lineHeight = 18.sp)
            }
            content()
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
                    modifier = Modifier.weight(1f).height(44.dp),
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
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(60.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(54.dp), GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniSettingMetric(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 22, modifier.height(70.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsStatusBadge(text: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.height(38.dp), GlassRole.Floating) {
        Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
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
