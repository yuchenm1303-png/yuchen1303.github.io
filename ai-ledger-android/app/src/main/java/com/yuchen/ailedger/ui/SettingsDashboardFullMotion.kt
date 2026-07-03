package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val SettingsDashboardTileHeight = 116.dp
private val SettingsDashboardGap = 10.dp
private const val SettingsDashboardTileRadius = 30

@Immutable
private data class SettingsDashboardGlassSpec(
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float,
)

/**
 * 设置页八张入口卡片。
 *
 * 八张卡都直接调用功能页股票顶部正在使用的 [OpenGlShellGlass]。外层批宿主只共享
 * TextureView、EGL、纹理和 shader program；每张卡仍拥有自己的矩形、圆角、背景采样原点、
 * 折射场、按压中心和动态状态，不再对一块大玻璃做裁剪。
 *
 * 账号状态只在依赖它的两行内部订阅，避免登录恢复或云端同步带着整组八张 OpenGL 卡重组。
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
    val glassSpec = remember(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
    ) {
        SettingsDashboardGlassSpec(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
        )
    }
    val themeValue = remember(state.backgroundTheme) {
        settingsDashboardThemeLabel(state.backgroundTheme)
    }
    val glassValue = remember(state.quality, state.glassPreset) {
        "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}"
    }
    val recordValue = "${state.ledgerRecords.size} 笔"
    val stickerValue = "${stickerSizeDp.roundToInt()} dp"

    OpenGlShellBatchHost(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsDashboardGap)) {
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    glassSpec = glassSpec,
                    title = "主题",
                    subtitle = "背景与主题",
                    value = themeValue,
                    selected = selectedPanel == SettingsDetailSection.Appearance,
                ) { onSelected(SettingsDetailSection.Appearance) }
                SettingsOpenGlTile(
                    glassSpec = glassSpec,
                    title = "玻璃",
                    subtitle = "质感与流畅度",
                    value = glassValue,
                    selected = selectedPanel == SettingsDetailSection.Glass,
                ) { onSelected(SettingsDetailSection.Glass) }
            }
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    glassSpec = glassSpec,
                    title = "视觉智能",
                    subtitle = "边缘光与光标",
                    value = "运行 HUD",
                    selected = selectedPanel == SettingsDetailSection.Assistant,
                ) { onSelected(SettingsDetailSection.Assistant) }
                SettingsOpenGlTile(
                    glassSpec = glassSpec,
                    title = "数据偏好",
                    subtitle = "预算与账单",
                    value = recordValue,
                    selected = selectedPanel == SettingsDetailSection.Data,
                ) { onSelected(SettingsDetailSection.Data) }
            }
            SettingsAccountDependentRows(
                glassSpec = glassSpec,
                aiEndpoint = aiEndpoint,
                stickerValue = stickerValue,
                selectedPanel = selectedPanel,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun SettingsAccountDependentRows(
    glassSpec: SettingsDashboardGlassSpec,
    aiEndpoint: String,
    stickerValue: String,
    selectedPanel: SettingsDetailSection,
    onSelected: (SettingsDetailSection) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val accountRepository = remember(context) {
        SupabaseAuthRepository.get(context)
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

    Column(verticalArrangement = Arrangement.spacedBy(SettingsDashboardGap)) {
        SettingsDashboardRow {
            SettingsOpenGlTile(
                glassSpec = glassSpec,
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsOpenGlTile(
                glassSpec = glassSpec,
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsOpenGlTile(
                glassSpec = glassSpec,
                title = "聊天设置",
                subtitle = "消息与表情",
                value = stickerValue,
                selected = selectedPanel == SettingsDetailSection.Chat,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsOpenGlTile(
                glassSpec = glassSpec,
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsDetailSection.Memory,
            ) { onSelected(SettingsDetailSection.Memory) }
        }
    }
}

@Composable
private fun RowScope.SettingsOpenGlTile(
    glassSpec: SettingsDashboardGlassSpec,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stockGlassIntensity = LocalGlassBackdrop.current
        ?.borderStyle
        ?.newOpenGlGlassIntensity
        ?.takeIf { it > 0f }
        ?.coerceIn(0.35f, 1.35f)
        ?: (glassSpec.glassIntensity * 1.03f)

    OpenGlShellGlass(
        quality = glassSpec.quality,
        glassIntensity = stockGlassIntensity,
        motionIntensity = glassSpec.motionIntensity,
        radius = SettingsDashboardTileRadius,
        modifier = Modifier
            .weight(1f)
            .height(SettingsDashboardTileHeight)
            .semantics {
                contentDescription = "$title，$subtitle，当前$value"
            },
        mood = OpenGlShellMood.Settings,
        forceOpenGl = true,
        onClick = onClick,
    ) {
        SettingsStaticTileTextContent(
            title = title,
            subtitle = subtitle,
            value = value,
            selected = selected,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SettingsStaticTileTextContent(
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleAlpha = if (selected) 0.98f else 0.91f
    val subtitleAlpha = if (selected) 0.58f else 0.48f
    val hairlineAlpha = if (selected) 0.145f else 0.095f
    val currentAlpha = if (selected) 0.41f else 0.34f
    val valueAlpha = if (selected) 0.88f else 0.66f

    Column(
        modifier = modifier.padding(horizontal = 17.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = titleAlpha),
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = subtitleAlpha),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsHairline(alpha = hairlineAlpha)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前",
                color = Color.White.copy(alpha = currentAlpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                color = Color.White.copy(alpha = valueAlpha),
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

@Composable
private fun SettingsDashboardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsDashboardTileHeight),
        horizontalArrangement = Arrangement.spacedBy(SettingsDashboardGap),
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
