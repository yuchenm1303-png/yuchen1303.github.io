package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

/**
 * 设置页八个入口固定为静态雾面卡片。
 * 不运行呼吸、按压形变、余辉、棱彩扫光或局部动态 Canvas。
 */
@Composable
internal fun SettingsDashboardGridFullMotion(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsDetailSection,
    onSelected: (SettingsDetailSection) -> Unit,
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
            SettingsStaticDashboardTile(
                icon = "景",
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsDetailSection.Appearance,
            ) { onSelected(SettingsDetailSection.Appearance) }
            SettingsStaticDashboardTile(
                icon = "璃",
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsDetailSection.Glass,
            ) { onSelected(SettingsDetailSection.Glass) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = "视",
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsDetailSection.Assistant,
            ) { onSelected(SettingsDetailSection.Assistant) }
            SettingsStaticDashboardTile(
                icon = "账",
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsDetailSection.Data,
            ) { onSelected(SettingsDetailSection.Data) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = "云",
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsStaticDashboardTile(
                icon = "GL",
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = "聊",
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsDetailSection.Chat,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsStaticDashboardTile(
                icon = "忆",
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsDetailSection.Memory,
            ) { onSelected(SettingsDetailSection.Memory) }
        }
    }
}

@Composable
private fun RowScope.SettingsStaticDashboardTile(
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val radius = 17.44f
    val parentLayer = LocalSettingsFrostParentLayer.current
    val itemId = remember(title, icon) { "settings-static-$title-$icon" }

    SettingsFrostParentRegistrationCleanup(parentLayer, itemId)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(116.dp)
            .registerSettingsFrostParentItem(
                id = itemId,
                layerState = parentLayer,
                radiusDp = radius,
                backdropAlpha = 1f,
                frostAlpha = 0.085f,
                dimAlpha = 0f,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (parentLayer == null) {
            FrostInfoGlassPanel(
                radius = radius,
                backdropAlpha = 1f,
                frostAlpha = 0.085f,
                dimAlpha = 0f,
                modifier = Modifier.fillMaxSize(),
            ) {}
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIconBadge(icon, selected)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        title,
                        color = Color.White.copy(alpha = if (selected) 0.98f else 0.88f),
                        fontSize = if (title.length >= 5) 16.sp else 20.sp,
                        lineHeight = if (title.length >= 5) 20.sp else 23.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = if (selected) 0.58f else 0.48f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            SettingsHairline(alpha = if (selected) 0.18f else 0.10f)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前",
                    color = Color.White.copy(alpha = if (selected) 0.44f else 0.34f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    color = Color.White.copy(alpha = if (selected) 0.90f else 0.62f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
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
