package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MEMORY_PANEL_V2_TAIL_FRACTION = 0.72f
private val MemoryPanelV2NoFontPadding = PlatformTextStyle(includeFontPadding = false)

@Immutable
private data class MemoryPreviewItemV2(
    val kind: String,
    val priority: String,
    val text: String,
    val accent: Color,
)

private val memoryPreviewItemsV2 = listOf(
    MemoryPreviewItemV2(
        kind = "自定义指令",
        priority = "高优先级",
        text = "中文回答，先给结论，避免机械套话。",
        accent = Color(0xFFC19AFF),
    ),
    MemoryPreviewItemV2(
        kind = "置顶 · 个人信息",
        priority = "核心",
        text = "用户名是邹羽宸，电气工程及其自动化专业。",
        accent = Color(0xFFFFD07F),
    ),
    MemoryPreviewItemV2(
        kind = "偏好",
        priority = "重要",
        text = "表达自然简洁，少分点，重点明确。",
        accent = Color(0xFF8DFFF4),
    ),
)

/**
 * 聊天标题栏中的记忆快捷入口。
 *
 * 按钮和弹层都只使用普通 Compose 绘制；弹层明确退出页面父级批绘制后使用
 * GlassRole.Floating，绝不进入聊天 Shell 的 OpenGL registry 或几何同步链。
 */
@Composable
internal fun MemoryQuickPanelV2Host(modifier: Modifier = Modifier) {
    val assistantViewModel: AssistantViewModel = viewModel()
    val state = assistantViewModel.uiState
    MemoryQuickPanelV2Host(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        onOpenManager = { assistantViewModel.selectTab(AppTab.Settings) },
        modifier = modifier,
    )
}

@Composable
private fun MemoryQuickPanelV2Host(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var anchorTopPx by remember { mutableStateOf(0f) }
    val visibility = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        visibility.targetState = expanded
    }

    val availableUpwardDp = with(density) {
        (anchorTopPx - 12.dp.toPx()).coerceAtLeast(0f).toDp().value
    }
    val compact = availableUpwardDp in 0f..250f

    Box(
        modifier = modifier
            .width(32.dp)
            .height(22.dp)
            .onGloballyPositioned { coordinates ->
                val nextTop = coordinates.boundsInWindow().top
                if (abs(anchorTopPx - nextTop) > 0.5f) anchorTopPx = nextTop
            },
        contentAlignment = Alignment.Center,
    ) {
        MemoryFolderCapsuleButtonV2(
            expanded = expanded,
            onClick = { expanded = !expanded },
        )

        if (visibility.currentState || visibility.targetState) {
            val positionProvider = remember(density) {
                MemoryQuickPanelPositionProviderV2(
                    gapPx = with(density) { 8.dp.roundToPx() },
                    safeInsetPx = with(density) { 10.dp.roundToPx() },
                    tailFraction = MEMORY_PANEL_V2_TAIL_FRACTION,
                )
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(120)) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            initialOffsetY = { 7 },
                        ) +
                        scaleIn(
                            initialScale = 0.86f,
                            transformOrigin = TransformOrigin(MEMORY_PANEL_V2_TAIL_FRACTION, 1f),
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                    exit = fadeOut(tween(95)) +
                        slideOutVertically(tween(110)) { 5 } +
                        scaleOut(
                            targetScale = 0.95f,
                            transformOrigin = TransformOrigin(MEMORY_PANEL_V2_TAIL_FRACTION, 1f),
                            animationSpec = tween(110),
                        ),
                ) {
                    GlassSceneScope(group = GlassSceneGroup.Unassigned) {
                        MemoryQuickPanelV2(
                            quality = quality,
                            glassIntensity = glassIntensity,
                            motionIntensity = motionIntensity,
                            compact = compact,
                            onOpenManager = {
                                expanded = false
                                onOpenManager()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryFolderCapsuleButtonV2(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(180),
        label = "memory-v2-active",
    )
    val scaleX by animateFloatAsState(
        targetValue = when {
            pressed -> 0.925f
            expanded -> 0.975f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "memory-v2-press-x",
    )
    val scaleY by animateFloatAsState(
        targetValue = when {
            pressed -> 1.075f
            expanded -> 1.025f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "memory-v2-press-y",
    )
    val translationY by animateFloatAsState(
        targetValue = if (pressed) 0.75f else if (expanded) 0.35f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMedium),
        label = "memory-v2-press-y-offset",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
                this.translationY = translationY.dp.toPx()
            }
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.070f + active * 0.025f),
                        Color(0xFF8DFFF4).copy(alpha = 0.028f + active * 0.070f),
                        Color(0xFF9B73FF).copy(alpha = 0.035f + active * 0.075f),
                    ),
                ),
            )
            .border(
                width = 0.7.dp,
                color = Color.White.copy(alpha = 0.09f + active * 0.045f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (pressed) 0.045f else 0.105f),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            drawLine(
                color = Color(0xFF25335A).copy(alpha = 0.28f),
                start = Offset(size.width * 0.20f, size.height - 1.1f),
                end = Offset(size.width * 0.80f, size.height - 1.1f),
                strokeWidth = 0.65.dp.toPx(),
            )
        }
        MemoryFolderGlyphV2(
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer {
                    this.translationY = if (pressed) 0.35.dp.toPx() else 0f
                },
            active = active,
        )
    }
}

@Composable
private fun MemoryFolderGlyphV2(
    modifier: Modifier,
    active: Float,
) {
    Canvas(modifier) {
        val tab = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.40f)
            quadraticBezierTo(
                size.width * 0.14f,
                size.height * 0.22f,
                size.width * 0.30f,
                size.height * 0.22f,
            )
            lineTo(size.width * 0.48f, size.height * 0.22f)
            lineTo(size.width * 0.59f, size.height * 0.34f)
            lineTo(size.width * 0.82f, size.height * 0.34f)
            quadraticBezierTo(
                size.width * 0.88f,
                size.height * 0.34f,
                size.width * 0.88f,
                size.height * 0.42f,
            )
            lineTo(size.width * 0.88f, size.height * 0.47f)
            lineTo(size.width * 0.14f, size.height * 0.47f)
            close()
        }
        drawPath(
            path = tab,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFF3F8FF).copy(alpha = 0.82f),
                    Color(0xFF8DFFF4).copy(alpha = 0.72f + active * 0.20f),
                ),
            ),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFE9EFFA).copy(alpha = 0.94f),
                    Color(0xFFC8BCFF).copy(alpha = 0.72f + active * 0.20f),
                ),
            ),
            topLeft = Offset(size.width * 0.14f, size.height * 0.43f),
            size = Size(size.width * 0.74f, size.height * 0.41f),
            cornerRadius = CornerRadius(size.minDimension * 0.13f),
        )
    }
}

@Composable
private fun MemoryQuickPanelV2(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    compact: Boolean,
    onOpenManager: () -> Unit,
) {
    val panelWidth = if (compact) 250.dp else 270.dp
    val tailWidth = if (compact) 22.dp else 24.dp
    val tailHeight = if (compact) 9.dp else 10.dp
    val tailCenter = panelWidth * MEMORY_PANEL_V2_TAIL_FRACTION

    Column(
        modifier = Modifier.width(panelWidth),
        horizontalAlignment = Alignment.Start,
    ) {
        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity * 1.02f,
            motionIntensity = motionIntensity,
            radius = if (compact) 21 else 24,
            modifier = Modifier.fillMaxWidth(),
            role = GlassRole.Floating,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 10.dp else 12.dp,
                        top = if (compact) 9.dp else 11.dp,
                        end = if (compact) 10.dp else 12.dp,
                        bottom = if (compact) 9.dp else 11.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp),
            ) {
                MemoryPanelHeaderV2(compact = compact)
                MemoryPanelDividerV2()
                memoryPreviewItemsV2.forEach { item ->
                    MemoryPreviewCardV2(item = item, compact = compact)
                }
                MemoryPanelFooterV2(
                    hiddenCount = 5,
                    compact = compact,
                    onOpenManager = onOpenManager,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tailHeight),
        ) {
            Canvas(
                modifier = Modifier
                    .offset(x = tailCenter - tailWidth / 2)
                    .size(width = tailWidth, height = tailHeight),
            ) {
                val tail = Path().apply {
                    moveTo(0f, 0f)
                    quadraticBezierTo(size.width * 0.28f, 1f, size.width * 0.50f, size.height)
                    quadraticBezierTo(size.width * 0.72f, 1f, size.width, 0f)
                    close()
                }
                drawPath(
                    path = tail,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF152040).copy(alpha = 0.92f),
                            Color(0xFF10172E).copy(alpha = 0.88f),
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun MemoryPanelHeaderV2(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 27.dp else 30.dp)
                .clip(RoundedCornerShape(if (compact) 10.dp else 11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFF8DFFF4).copy(alpha = 0.09f),
                            Color(0xFF9B73FF).copy(alpha = 0.13f),
                        ),
                    ),
                )
                .border(
                    0.7.dp,
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(if (compact) 10.dp else 11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MemoryFolderGlyphV2(
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                active = 1f,
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "记忆",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = if (compact) 14.sp else 15.sp,
                lineHeight = if (compact) 17.sp else 18.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
            )
            Text(
                text = "本轮生效 4 项 · 已保存 8 项",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = if (compact) 8.sp else 8.5.sp,
                lineHeight = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(0.6.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(999.dp))
                .padding(horizontal = if (compact) 7.dp else 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF8DFFF4)),
            )
            Text(
                text = "生效",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = if (compact) 8.sp else 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
            )
        }
    }
}

@Composable
private fun MemoryPanelDividerV2() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f),
                        Color(0xFF8DFFF4).copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun MemoryPreviewCardV2(
    item: MemoryPreviewItemV2,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.058f),
                        item.accent.copy(alpha = 0.030f),
                        Color.White.copy(alpha = 0.022f),
                    ),
                ),
            )
            .border(
                0.6.dp,
                Color.White.copy(alpha = 0.055f),
                RoundedCornerShape(if (compact) 14.dp else 16.dp),
            )
            .padding(
                horizontal = if (compact) 9.dp else 10.dp,
                vertical = if (compact) 7.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(item.accent.copy(alpha = 0.92f)),
        )
        Spacer(Modifier.width(if (compact) 7.dp else 8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.kind,
                color = item.accent.copy(alpha = 0.92f),
                fontSize = if (compact) 9.sp else 9.5.sp,
                lineHeight = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.text,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = if (compact) 8.sp else 8.5.sp,
                lineHeight = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.priority,
            color = Color.White.copy(alpha = 0.30f),
            fontSize = if (compact) 7.sp else 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
            maxLines = 1,
        )
    }
}

@Composable
private fun MemoryPanelFooterV2(
    hiddenCount: Int,
    compact: Boolean,
    onOpenManager: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "另有 $hiddenCount 项未展开",
            color = Color.White.copy(alpha = 0.30f),
            fontSize = if (compact) 7.5.sp else 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "完整管理",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = if (compact) 8.sp else 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(platformStyle = MemoryPanelV2NoFontPadding),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.075f),
                            Color(0xFF8DFFF4).copy(alpha = 0.045f),
                            Color(0xFF9B73FF).copy(alpha = 0.055f),
                        ),
                    ),
                )
                .border(0.65.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenManager)
                .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = 5.dp),
        )
    }
}

private class MemoryQuickPanelPositionProviderV2(
    private val gapPx: Int,
    private val safeInsetPx: Int,
    private val tailFraction: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorCenterX = (anchorBounds.left + anchorBounds.right) / 2
        val desiredX = anchorCenterX - (popupContentSize.width * tailFraction).roundToInt()
        val maxX = (windowSize.width - popupContentSize.width - safeInsetPx).coerceAtLeast(safeInsetPx)
        val x = desiredX.coerceIn(safeInsetPx, maxX)

        val desiredY = anchorBounds.top - popupContentSize.height - gapPx
        val maxY = (windowSize.height - popupContentSize.height - safeInsetPx).coerceAtLeast(safeInsetPx)
        val y = desiredY.coerceIn(safeInsetPx, maxY)
        return IntOffset(x, y)
    }
}
