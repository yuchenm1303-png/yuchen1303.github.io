package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalForceNewOpenGlShellRenderer
import kotlin.math.min
import kotlin.math.roundToInt

private val SettingsDashboardTileHeight = 116.dp
private val SettingsDashboardGap = 10.dp
private val SettingsDashboardTileRadius = 30.dp
private val SettingsDashboardTotalHeight =
    SettingsDashboardTileHeight * 4 + SettingsDashboardGap * 3

/**
 * 设置页八张入口卡片共用一个新版 OpenGL Shell。
 *
 * 这样八个视觉卡片都显示真实新版折射，同时只创建一个 EGL / TextureView 宿主；
 * 每个入口仍只是普通 Compose 点击与文字层，不注册 OpenGL geometry，也不建立八套 Renderer。
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
    val dashboardMask = remember {
        SettingsDashboardTileMaskShape(
            rowCount = 4,
            tileHeight = SettingsDashboardTileHeight,
            gap = SettingsDashboardGap,
            radius = SettingsDashboardTileRadius,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsDashboardTotalHeight),
    ) {
        CompositionLocalProvider(LocalForceNewOpenGlShellRenderer provides true) {
            GlassPanel(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 1.02f,
                motionIntensity = state.motionIntensity,
                radius = SettingsDashboardTileRadius.value.roundToInt(),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(dashboardMask),
                role = GlassRole.Shell,
            ) {}
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SettingsDashboardGap),
        ) {
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    title = "主题",
                    subtitle = "背景与主题",
                    value = settingsDashboardThemeLabel(state.backgroundTheme),
                    selected = selectedPanel == SettingsDetailSection.Appearance,
                ) { onSelected(SettingsDetailSection.Appearance) }
                SettingsOpenGlTile(
                    title = "玻璃",
                    subtitle = "质感与流畅度",
                    value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                    selected = selectedPanel == SettingsDetailSection.Glass,
                ) { onSelected(SettingsDetailSection.Glass) }
            }
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    title = "视觉智能",
                    subtitle = "边缘光与光标",
                    value = "运行 HUD",
                    selected = selectedPanel == SettingsDetailSection.Assistant,
                ) { onSelected(SettingsDetailSection.Assistant) }
                SettingsOpenGlTile(
                    title = "数据偏好",
                    subtitle = "预算与账单",
                    value = "${state.ledgerRecords.size} 笔",
                    selected = selectedPanel == SettingsDetailSection.Data,
                ) { onSelected(SettingsDetailSection.Data) }
            }
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    title = "账号设置",
                    subtitle = "账号 / Worker",
                    value = serviceValue,
                    selected = selectedPanel == SettingsDetailSection.Service,
                ) { onSelected(SettingsDetailSection.Service) }
                SettingsOpenGlTile(
                    title = "系统信息",
                    subtitle = "渲染边界",
                    value = "OpenGL 隔离",
                    selected = selectedPanel == SettingsDetailSection.Advanced,
                ) { onSelected(SettingsDetailSection.Advanced) }
            }
            SettingsDashboardRow {
                SettingsOpenGlTile(
                    title = "聊天设置",
                    subtitle = "消息与表情",
                    value = "${stickerSizeDp.roundToInt()} dp",
                    selected = selectedPanel == SettingsDetailSection.Chat,
                ) { onSelected(SettingsDetailSection.Chat) }
                SettingsOpenGlTile(
                    title = "记忆",
                    subtitle = "长期上下文",
                    value = memoryValue,
                    selected = selectedPanel == SettingsDetailSection.Memory,
                ) { onSelected(SettingsDetailSection.Memory) }
            }
        }
    }
}

@Composable
private fun RowScope.SettingsOpenGlTile(
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(SettingsDashboardTileHeight)
            .semantics {
                contentDescription = "$title，$subtitle，当前$value"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SettingsOpenGlTileSurface(
            selected = selected,
            modifier = Modifier.fillMaxSize(),
        )
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
private fun SettingsOpenGlTileSurface(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radiusPx = SettingsDashboardTileRadius.toPx()
        val corner = CornerRadius(radiusPx, radiusPx)
        val selectedGain = if (selected) 1f else 0f

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.030f + selectedGain * 0.022f),
                    Color.Transparent,
                    Color(0xFF6E91FF).copy(alpha = selectedGain * 0.030f),
                )
            ),
            size = size,
            cornerRadius = corner,
            blendMode = BlendMode.Screen,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.105f + selectedGain * 0.080f),
            size = size,
            cornerRadius = corner,
            style = Stroke(width = 0.85.dp.toPx()),
            blendMode = BlendMode.Screen,
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

private class SettingsDashboardTileMaskShape(
    private val rowCount: Int,
    private val tileHeight: Dp,
    private val gap: Dp,
    private val radius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val gapPx = with(density) { gap.toPx() }
        val tileHeightPx = with(density) { tileHeight.toPx() }
        val tileWidthPx = ((size.width - gapPx) / 2f).coerceAtLeast(1f)
        val radiusPx = with(density) { radius.toPx() }
            .coerceAtMost(min(tileWidthPx, tileHeightPx) * 0.5f)
        val corner = CornerRadius(radiusPx, radiusPx)
        val path = Path()

        repeat(rowCount) { rowIndex ->
            val top = rowIndex * (tileHeightPx + gapPx)
            val bottom = (top + tileHeightPx).coerceAtMost(size.height)
            val rightStart = tileWidthPx + gapPx

            path.addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, tileWidthPx, bottom),
                    cornerRadius = corner,
                )
            )
            path.addRoundRect(
                RoundRect(
                    rect = Rect(rightStart, top, size.width, bottom),
                    cornerRadius = corner,
                )
            )
        }

        return Outline.Generic(path)
    }
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
