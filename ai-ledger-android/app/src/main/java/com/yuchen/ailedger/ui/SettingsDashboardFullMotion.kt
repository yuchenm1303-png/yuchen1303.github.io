package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

/**
 * 设置页八个入口统一复用完整雾面玻璃动效。
 * 静态底材仍由设置页父级批绘制，只有选中呼吸或实际按压时才启动局部光学层。
 */
@Composable
internal fun SettingsDashboardGridFullMotion(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsPanel,
    onSelected: (SettingsPanel) -> Unit,
) {
    val context = LocalContext.current
    val stickerSizeDp = InlineStickerDisplaySettings.sizeDp(context)
    val accountRepository = remember(context.applicationContext) {
        SupabaseAuthRepository.get(context.applicationContext)
    }
    val accountState by accountRepository.state.collectAsState()
    val serviceValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "已登录 · 云端"
        aiEndpoint.isBlank() -> "登录与本地"
        else -> "登录与云端"
    }
    val memoryValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "账号已登录"
        else -> "登录后使用"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsDashboardRow {
            AnimatedSettingsFrostTile(
                icon = "景",
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsPanel.Appearance,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Appearance) }
            AnimatedSettingsFrostTile(
                icon = "璃",
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsPanel.Glass,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Glass) }
        }
        SettingsDashboardRow {
            AnimatedSettingsFrostTile(
                icon = "视",
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsPanel.Assistant,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Assistant) }
            AnimatedSettingsFrostTile(
                icon = "账",
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsPanel.Data,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Data) }
        }
        SettingsDashboardRow {
            AnimatedSettingsFrostTile(
                icon = "云",
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsPanel.Service,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Service) }
            AnimatedSettingsFrostTile(
                icon = "GL",
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsPanel.Advanced,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Advanced) }
        }
        SettingsDashboardRow {
            AnimatedSettingsFrostTile(
                icon = "聊",
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsPanel.Chat,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Chat) }
            AnimatedSettingsFrostTile(
                icon = "忆",
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsPanel.Memory,
                state = state,
                modifier = Modifier.weight(1f),
            ) { onSelected(SettingsPanel.Memory) }
        }
    }
}

@Composable
private fun SettingsDashboardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun settingsDashboardQualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun settingsDashboardGlassLabel(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

private fun settingsDashboardThemeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}
