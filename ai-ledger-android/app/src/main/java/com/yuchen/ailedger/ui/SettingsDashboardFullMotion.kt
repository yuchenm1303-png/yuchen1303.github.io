package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

private enum class SettingsDashboardIcon {
    Theme,
    Glass,
    Vision,
    Data,
    Account,
    System,
    Chat,
    Memory,
}

/**
 * 设置页八个入口固定为静态雾面卡片。
 * 玻璃底材由页面父级批绘制，卡片内容层只绘制静态图标、文字和选中描边。
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
                icon = SettingsDashboardIcon.Theme,
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsDetailSection.Appearance,
            ) { onSelected(SettingsDetailSection.Appearance) }
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Glass,
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsDetailSection.Glass,
            ) { onSelected(SettingsDetailSection.Glass) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Vision,
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsDetailSection.Assistant,
            ) { onSelected(SettingsDetailSection.Assistant) }
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Data,
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsDetailSection.Data,
            ) { onSelected(SettingsDetailSection.Data) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Account,
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.System,
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Chat,
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsDetailSection.Chat,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsStaticDashboardTile(
                icon = SettingsDashboardIcon.Memory,
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
    icon: SettingsDashboardIcon,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val radius = 17.44f
    val shape = RoundedCornerShape(radius.dp)
    val parentLayer = LocalSettingsFrostParentLayer.current
    val itemId = remember(title, icon) { "settings-static-$title-${icon.name}" }
    val frostAlpha = if (selected) 0.112f else 0.078f

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
                dimAlpha = 0f,
            )
            .clip(shape)
            .drawWithCache {
                val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
                val borderWidth = if (selected) 1.05.dp.toPx() else 0.58.dp.toPx()
                val selectedFill = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1688FF).copy(alpha = 0.18f),
                        Color(0xFF69B9FF).copy(alpha = 0.055f),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawWithContent {
                    if (selected) {
                        drawRoundRect(
                            brush = selectedFill,
                            size = size,
                            cornerRadius = cornerRadius,
                        )
                    }
                    drawContent()
                    drawRoundRect(
                        color = if (selected) {
                            Color(0xFF9BD9FF).copy(alpha = 0.78f)
                        } else {
                            Color.White.copy(alpha = 0.115f)
                        },
                        size = size,
                        cornerRadius = cornerRadius,
                        style = Stroke(borderWidth),
                    )
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
                SettingsDashboardIconBadge(icon = icon, selected = selected)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        title,
                        color = Color.White.copy(alpha = if (selected) 0.98f else 0.91f),
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = if (selected) 0.62f else 0.49f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            SettingsHairline(alpha = if (selected) 0.18f else 0.095f)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前",
                    color = Color.White.copy(alpha = if (selected) 0.49f else 0.34f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    color = Color.White.copy(alpha = if (selected) 0.94f else 0.66f),
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
private fun SettingsDashboardIconBadge(
    icon: SettingsDashboardIcon,
    selected: Boolean,
) {
    val badgeShape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(badgeShape)
            .background(
                Color.White.copy(alpha = if (selected) 0.115f else 0.058f)
            )
            .drawWithCache {
                val cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = if (selected) {
                            Color(0xFF8EDBFF).copy(alpha = 0.72f)
                        } else {
                            Color.White.copy(alpha = 0.16f)
                        },
                        size = size,
                        cornerRadius = cornerRadius,
                        style = Stroke(if (selected) 0.82.dp.toPx() else 0.55.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(25.dp)) {
            drawSettingsDashboardIcon(icon = icon, selected = selected)
        }
    }
}

private fun DrawScope.drawSettingsDashboardIcon(
    icon: SettingsDashboardIcon,
    selected: Boolean,
) {
    val lineColor = Color.White.copy(alpha = if (selected) 0.98f else 0.88f)
    val accentColor = Color(0xFF8DF9EA).copy(alpha = if (selected) 0.98f else 0.80f)
    val strokeWidth = 1.65.dp.toPx()
    val thinStroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val w = size.width
    val h = size.height

    when (icon) {
        SettingsDashboardIcon.Theme -> {
            drawCircle(
                color = lineColor,
                radius = size.minDimension * 0.36f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = thinStroke,
            )
            drawCircle(lineColor, strokeWidth * 0.72f, Offset(w * 0.39f, h * 0.34f))
            drawCircle(lineColor, strokeWidth * 0.72f, Offset(w * 0.58f, h * 0.31f))
            drawCircle(lineColor, strokeWidth * 0.72f, Offset(w * 0.66f, h * 0.49f))
            drawLine(
                color = lineColor,
                start = Offset(w * 0.61f, h * 0.68f),
                end = Offset(w * 0.75f, h * 0.77f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        SettingsDashboardIcon.Glass -> {
            val prism = Path().apply {
                moveTo(w * 0.50f, h * 0.17f)
                lineTo(w * 0.80f, h * 0.76f)
                lineTo(w * 0.20f, h * 0.76f)
                close()
            }
            drawPath(prism, lineColor, style = thinStroke)
            drawLine(
                lineColor,
                Offset(w * 0.03f, h * 0.48f),
                Offset(w * 0.36f, h * 0.48f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                lineColor,
                Offset(w * 0.36f, h * 0.48f),
                Offset(w * 0.61f, h * 0.47f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                accentColor,
                Offset(w * 0.61f, h * 0.43f),
                Offset(w * 0.97f, h * 0.27f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                accentColor,
                Offset(w * 0.61f, h * 0.52f),
                Offset(w * 0.97f, h * 0.69f),
                strokeWidth,
                StrokeCap.Round,
            )
        }

        SettingsDashboardIcon.Vision -> {
            val pointer = Path().apply {
                moveTo(w * 0.39f, h * 0.36f)
                lineTo(w * 0.76f, h * 0.55f)
                lineTo(w * 0.58f, h * 0.61f)
                lineTo(w * 0.49f, h * 0.81f)
                close()
            }
            drawPath(pointer, lineColor, style = thinStroke)
            drawLine(lineColor, Offset(w * 0.22f, h * 0.10f), Offset(w * 0.22f, h * 0.25f), strokeWidth, StrokeCap.Round)
            drawLine(lineColor, Offset(w * 0.03f, h * 0.31f), Offset(w * 0.17f, h * 0.35f), strokeWidth, StrokeCap.Round)
            drawLine(lineColor, Offset(w * 0.31f, h * 0.27f), Offset(w * 0.42f, h * 0.16f), strokeWidth, StrokeCap.Round)
            drawLine(accentColor, Offset(w * 0.11f, h * 0.58f), Offset(w * 0.25f, h * 0.52f), strokeWidth, StrokeCap.Round)
        }

        SettingsDashboardIcon.Data -> {
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.15f, h * 0.25f),
                size = Size(w * 0.70f, h * 0.54f),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                style = thinStroke,
            )
            drawLine(
                lineColor,
                Offset(w * 0.23f, h * 0.25f),
                Offset(w * 0.70f, h * 0.25f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.58f, h * 0.42f),
                size = Size(w * 0.29f, h * 0.20f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                style = thinStroke,
            )
            drawCircle(accentColor, strokeWidth * 0.60f, Offset(w * 0.67f, h * 0.52f))
        }

        SettingsDashboardIcon.Account -> {
            drawCircle(
                color = lineColor,
                radius = w * 0.12f,
                center = Offset(w * 0.35f, h * 0.31f),
                style = thinStroke,
            )
            val shoulders = Path().apply {
                moveTo(w * 0.12f, h * 0.76f)
                cubicTo(w * 0.17f, h * 0.56f, w * 0.52f, h * 0.56f, w * 0.59f, h * 0.76f)
            }
            drawPath(shoulders, lineColor, style = thinStroke)
            val cloud = Path().apply {
                moveTo(w * 0.53f, h * 0.67f)
                cubicTo(w * 0.55f, h * 0.58f, w * 0.67f, h * 0.56f, w * 0.72f, h * 0.64f)
                cubicTo(w * 0.77f, h * 0.60f, w * 0.86f, h * 0.63f, w * 0.87f, h * 0.72f)
                cubicTo(w * 0.95f, h * 0.73f, w * 0.95f, h * 0.84f, w * 0.85f, h * 0.85f)
                lineTo(w * 0.60f, h * 0.85f)
                cubicTo(w * 0.50f, h * 0.85f, w * 0.47f, h * 0.74f, w * 0.53f, h * 0.67f)
            }
            drawPath(cloud, accentColor, style = thinStroke)
        }

        SettingsDashboardIcon.System -> {
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.20f, h * 0.17f),
                size = Size(w * 0.60f, h * 0.66f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = thinStroke,
            )
            drawCircle(accentColor, strokeWidth * 0.72f, Offset(w * 0.50f, h * 0.35f))
            drawLine(
                lineColor,
                Offset(w * 0.50f, h * 0.48f),
                Offset(w * 0.50f, h * 0.68f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(lineColor, Offset(w * 0.11f, h * 0.35f), Offset(w * 0.20f, h * 0.35f), strokeWidth, StrokeCap.Round)
            drawLine(lineColor, Offset(w * 0.80f, h * 0.35f), Offset(w * 0.89f, h * 0.35f), strokeWidth, StrokeCap.Round)
            drawLine(lineColor, Offset(w * 0.11f, h * 0.65f), Offset(w * 0.20f, h * 0.65f), strokeWidth, StrokeCap.Round)
            drawLine(lineColor, Offset(w * 0.80f, h * 0.65f), Offset(w * 0.89f, h * 0.65f), strokeWidth, StrokeCap.Round)
        }

        SettingsDashboardIcon.Chat -> {
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.13f, h * 0.18f),
                size = Size(w * 0.74f, h * 0.57f),
                cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
                style = thinStroke,
            )
            val tail = Path().apply {
                moveTo(w * 0.34f, h * 0.74f)
                lineTo(w * 0.25f, h * 0.87f)
                lineTo(w * 0.49f, h * 0.75f)
            }
            drawPath(tail, lineColor, style = thinStroke)
            drawCircle(lineColor, strokeWidth * 0.62f, Offset(w * 0.37f, h * 0.47f))
            drawCircle(lineColor, strokeWidth * 0.62f, Offset(w * 0.50f, h * 0.47f))
            drawCircle(lineColor, strokeWidth * 0.62f, Offset(w * 0.63f, h * 0.47f))
        }

        SettingsDashboardIcon.Memory -> {
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.20f, h * 0.13f),
                size = Size(w * 0.60f, h * 0.74f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = thinStroke,
            )
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.34f, h * 0.15f),
                size = Size(w * 0.32f, h * 0.23f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                style = thinStroke,
            )
            drawLine(
                accentColor,
                Offset(w * 0.35f, h * 0.64f),
                Offset(w * 0.65f, h * 0.64f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                lineColor,
                Offset(w * 0.35f, h * 0.72f),
                Offset(w * 0.65f, h * 0.72f),
                strokeWidth,
                StrokeCap.Round,
            )
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
