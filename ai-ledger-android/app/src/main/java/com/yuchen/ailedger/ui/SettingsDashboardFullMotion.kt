package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * 设置页八个入口使用普通雾面玻璃，不接入 OpenGL。
 * 选中态由卡片自身的胶囊形变、内层光带和共享时钟高光组成。
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
    val motionOn = state.quality.enableMotion && state.motionIntensity > 0.02f
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
            SettingsCapsuleDashboardTile(
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsDetailSection.Appearance,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Appearance) }
            SettingsCapsuleDashboardTile(
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsDetailSection.Glass,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Glass) }
        }
        SettingsDashboardRow {
            SettingsCapsuleDashboardTile(
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsDetailSection.Assistant,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Assistant) }
            SettingsCapsuleDashboardTile(
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsDetailSection.Data,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Data) }
        }
        SettingsDashboardRow {
            SettingsCapsuleDashboardTile(
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsCapsuleDashboardTile(
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsCapsuleDashboardTile(
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsDetailSection.Chat,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsCapsuleDashboardTile(
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsDetailSection.Memory,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Memory) }
        }
    }
}

@Composable
private fun RowScope.SettingsCapsuleDashboardTile(
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    motionOn: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motionOn) {
            spring(
                dampingRatio = 0.74f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "settings-capsule-selection-$title",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (motionOn) {
            spring(
                dampingRatio = if (pressed) 0.86f else 0.68f,
                stiffness = if (pressed) Spring.StiffnessHigh else Spring.StiffnessMedium,
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "settings-capsule-press-$title",
    )

    val sharedClock = LocalSettingsFrostMotionClock.current
    val elapsedNanos = if (selected && motionOn) sharedClock?.frameNanos ?: 0L else 0L
    val pulsePhase = if (elapsedNanos > 0L) {
        (elapsedNanos % 4_600_000_000L).toDouble() / 4_600_000_000.0
    } else {
        0.25
    }
    val shimmerPhase = if (elapsedNanos > 0L) {
        (elapsedNanos % 5_800_000_000L).toDouble() / 5_800_000_000.0
    } else {
        0.34
    }
    val pulse = ((sin(pulsePhase * PI * 2.0) + 1.0) * 0.5).toFloat()
    val shimmer = shimmerPhase.toFloat()

    val radius = 20f + selection * 38f
    val shape = RoundedCornerShape(radius.dp)
    val parentLayer = LocalSettingsFrostParentLayer.current
    val itemId = remember(title) { "settings-capsule-$title" }
    val frostAlpha = 0.078f + selection * (0.040f + pulse * 0.010f)

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
                frostAlpha = frostAlpha,
                dimAlpha = press * 0.010f,
            )
            .graphicsLayer {
                val selectedScale = 1f + selection * (0.006f + pulse * 0.002f)
                val pressScale = 1f - press * 0.018f
                scaleX = selectedScale * pressScale
                scaleY = selectedScale * pressScale
                translationY = -selection * (1.0f + pulse * 0.45f) + press * 1.5f
            }
            .clip(shape)
            .drawWithCache {
                val radiusPx = radius.dp.toPx()
                val cornerRadius = CornerRadius(radiusPx, radiusPx)
                val insetX = 4.5.dp.toPx()
                val insetY = 5.5.dp.toPx()
                val innerSize = Size(
                    (size.width - insetX * 2f).coerceAtLeast(1f),
                    (size.height - insetY * 2f).coerceAtLeast(1f),
                )
                val innerRadius = CornerRadius(innerSize.height * 0.5f, innerSize.height * 0.5f)
                val selectedFill = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF53E7FF).copy(alpha = selection * (0.105f + pulse * 0.025f)),
                        Color(0xFF427CFF).copy(alpha = selection * 0.150f),
                        Color(0xFF795CFF).copy(alpha = selection * 0.120f),
                        Color(0xFF67F4EA).copy(alpha = selection * (0.070f + pulse * 0.020f)),
                    ),
                    start = Offset(0f, size.height * 0.15f),
                    end = Offset(size.width, size.height * 0.85f),
                )
                val outerBorder = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.13f + selection * 0.34f),
                        Color(0xFF87FFF0).copy(alpha = selection * (0.52f + pulse * 0.10f)),
                        Color(0xFF88A7FF).copy(alpha = selection * 0.44f),
                        Color(0xFFD39BFF).copy(alpha = selection * 0.40f),
                        Color.White.copy(alpha = 0.10f + selection * 0.18f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val glintCenter = size.width * (-0.18f + shimmer * 1.36f)
                val glintHalfWidth = size.width * 0.16f
                val glintBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.50f to Color.White.copy(alpha = selection * (0.075f + pulse * 0.035f)),
                        0.58f to Color.Transparent,
                        1.00f to Color.Transparent,
                    ),
                    start = Offset(glintCenter - glintHalfWidth, insetY),
                    end = Offset(glintCenter + glintHalfWidth, size.height - insetY),
                )
                onDrawWithContent {
                    if (selection > 0.001f) {
                        drawRoundRect(
                            brush = selectedFill,
                            size = size,
                            cornerRadius = cornerRadius,
                        )
                        drawRoundRect(
                            color = Color(0xFF73FFF0).copy(alpha = selection * (0.028f + pulse * 0.014f)),
                            topLeft = Offset(insetX - 1.8.dp.toPx(), insetY - 1.8.dp.toPx()),
                            size = Size(
                                innerSize.width + 3.6.dp.toPx(),
                                innerSize.height + 3.6.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                (innerSize.height + 3.6.dp.toPx()) * 0.5f,
                                (innerSize.height + 3.6.dp.toPx()) * 0.5f,
                            ),
                            style = Stroke(width = 5.0.dp.toPx()),
                        )
                        drawRoundRect(
                            brush = glintBrush,
                            topLeft = Offset(insetX, insetY),
                            size = innerSize,
                            cornerRadius = innerRadius,
                        )
                    }
                    drawContent()
                    drawRoundRect(
                        brush = outerBorder,
                        size = size,
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 0.62.dp.toPx() + selection * 0.72.dp.toPx()),
                    )
                    if (selection > 0.001f) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = selection * 0.30f),
                                    Color(0xFF79FFF0).copy(alpha = selection * (0.28f + pulse * 0.08f)),
                                    Color(0xFF829BFF).copy(alpha = selection * 0.23f),
                                    Color(0xFFD192FF).copy(alpha = selection * 0.22f),
                                ),
                                start = Offset(insetX, insetY),
                                end = Offset(size.width - insetX, size.height - insetY),
                            ),
                            topLeft = Offset(insetX, insetY),
                            size = innerSize,
                            cornerRadius = innerRadius,
                            style = Stroke(width = 0.95.dp.toPx() + selection * 0.36.dp.toPx()),
                        )
                    }
                }
            }
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
        if (parentLayer == null) {
            FrostInfoGlassPanel(
                radius = radius,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = press * 0.010f,
                modifier = Modifier.fillMaxSize(),
            ) {}
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 17.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.91f + selection * 0.09f),
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.48f + selection * 0.18f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SettingsHairline(alpha = 0.095f + selection * 0.115f)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前",
                    color = Color.White.copy(alpha = 0.34f + selection * 0.13f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    color = Color.White.copy(alpha = 0.66f + selection * 0.30f),
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
