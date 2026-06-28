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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MEMORY_PANEL_TAIL_FRACTION = 0.61f

@Immutable
private data class MemoryQuickPreviewItem(
    val kind: String,
    val priority: String,
    val text: String,
    val accent: Color
)

private val memoryQuickPreviewItems = listOf(
    MemoryQuickPreviewItem(
        kind = "自定义指令",
        priority = "高优先级",
        text = "中文回答，先给结论，避免机械套话。",
        accent = Color(0xFFC19AFF)
    ),
    MemoryQuickPreviewItem(
        kind = "置顶 · 个人信息",
        priority = "核心",
        text = "用户名是邹羽宸，电气工程及其自动化专业。",
        accent = Color(0xFFFFD07F)
    ),
    MemoryQuickPreviewItem(
        kind = "偏好",
        priority = "重要",
        text = "表达自然简洁，少分点，重点明确。",
        accent = Color(0xFF8DFFF4)
    )
)

@Composable
internal fun MemoryQuickPanelHost(
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
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
    val compact = availableUpwardDp in 0f..214f
    val visibleCount = when {
        availableUpwardDp <= 0f -> 3
        availableUpwardDp >= 190f -> 3
        availableUpwardDp >= 148f -> 2
        else -> 1
    }

    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .onGloballyPositioned { coordinates ->
                val nextTop = coordinates.boundsInWindow().top
                if (abs(anchorTopPx - nextTop) > 0.5f) anchorTopPx = nextTop
            },
        contentAlignment = Alignment.Center
    ) {
        MemoryFolderCapsuleButton(
            expanded = expanded,
            onClick = { expanded = !expanded }
        )

        if (visibility.currentState || visibility.targetState) {
            val positionProvider = remember(density) {
                MemoryQuickPanelPositionProvider(
                    desiredGapPx = with(density) { 10.dp.roundToPx() },
                    minimumGapPx = with(density) { 2.dp.roundToPx() },
                    safeTopPx = with(density) { 12.dp.roundToPx() },
                    safeHorizontalPx = with(density) { 11.dp.roundToPx() },
                    tailFraction = MEMORY_PANEL_TAIL_FRACTION
                )
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(120)) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.76f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { 8 }
                        ) +
                        scaleIn(
                            initialScale = 0.78f,
                            transformOrigin = TransformOrigin(MEMORY_PANEL_TAIL_FRACTION, 1f),
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    exit = fadeOut(tween(105)) +
                        slideOutVertically(tween(130)) { 5 } +
                        scaleOut(
                            targetScale = 0.92f,
                            transformOrigin = TransformOrigin(MEMORY_PANEL_TAIL_FRACTION, 1f),
                            animationSpec = tween(130)
                        )
                ) {
                    MemoryQuickPanel(
                        compact = compact,
                        visibleCount = visibleCount,
                        onOpenManager = {
                            expanded = false
                            onOpenManager()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFolderCapsuleButton(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            expanded -> 0.985f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "memory-folder-capsule-scale"
    )
    val pressOffset by animateFloatAsState(
        targetValue = if (pressed || expanded) 0.9f else 0f,
        animationSpec = tween(150),
        label = "memory-folder-capsule-press"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = pressOffset.dp.toPx()
            }
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (pressed || expanded) 0.075f else 0.105f),
                        Color.White.copy(alpha = if (pressed || expanded) 0.030f else 0.045f)
                    )
                )
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.095f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.height / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.095f),
                topLeft = Offset(1.4f, 1.2f),
                size = Size(size.width - 2.8f, size.height * 0.46f),
                cornerRadius = CornerRadius(radius)
            )
            drawLine(
                color = Color(0xFF243355).copy(alpha = 0.28f),
                start = Offset(size.width * 0.18f, size.height - 1.4f),
                end = Offset(size.width * 0.82f, size.height - 1.4f),
                strokeWidth = 0.7.dp.toPx()
            )
        }
        FolderGlyph(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    translationY = if (pressed || expanded) 0.35.dp.toPx() else 0f
                }
        )
    }
}

@Composable
private fun FolderGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tab = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.37f)
            quadraticBezierTo(
                size.width * 0.15f,
                size.height * 0.22f,
                size.width * 0.30f,
                size.height * 0.22f
            )
            lineTo(size.width * 0.49f, size.height * 0.22f)
            lineTo(size.width * 0.59f, size.height * 0.34f)
            lineTo(size.width * 0.82f, size.height * 0.34f)
            quadraticBezierTo(
                size.width * 0.88f,
                size.height * 0.34f,
                size.width * 0.88f,
                size.height * 0.41f
            )
            lineTo(size.width * 0.88f, size.height * 0.46f)
            lineTo(size.width * 0.15f, size.height * 0.46f)
            close()
        }
        drawPath(tab, Color(0xFFF4F8FF).copy(alpha = 0.84f))
        drawRoundRect(
            color = Color(0xFFE9EFFA).copy(alpha = 0.94f),
            topLeft = Offset(size.width * 0.15f, size.height * 0.42f),
            size = Size(size.width * 0.73f, size.height * 0.42f),
            cornerRadius = CornerRadius(size.minDimension * 0.13f)
        )
        drawLine(
            color = Color(0xFF6D7C9E).copy(alpha = 0.46f),
            start = Offset(size.width * 0.32f, size.height * 0.62f),
            end = Offset(size.width * 0.70f, size.height * 0.62f),
            strokeWidth = 0.9.dp.toPx()
        )
    }
}

@Composable
private fun MemoryQuickPanel(
    compact: Boolean,
    visibleCount: Int,
    onOpenManager: () -> Unit
) {
    val panelWidth = if (compact) 256.dp else 268.dp
    val tailHeight = 14.dp
    val bubbleShape = remember(panelWidth) {
        MemoryBubbleShape(
            cornerRadius = 22.dp,
            tailHeight = tailHeight,
            tailHalfWidth = 15.dp,
            tailFraction = MEMORY_PANEL_TAIL_FRACTION
        )
    }
    val shownItems = remember(visibleCount) {
        memoryQuickPreviewItems.take(visibleCount.coerceIn(1, 3))
    }

    Box(
        modifier = Modifier
            .width(panelWidth)
            .graphicsLayer {
                shape = bubbleShape
                shadowElevation = 18.dp.toPx()
                ambientShadowColor = Color.Black.copy(alpha = 0.28f)
                spotShadowColor = Color.Black.copy(alpha = 0.42f)
            }
            .clip(bubbleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.125f),
                        Color(0xE8111834),
                        Color(0xF21C1738),
                        Color(0xF6122145)
                    ),
                    start = Offset.Zero,
                    end = Offset(760f, 620f)
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color(0xFF8DFFF4).copy(alpha = 0.08f),
                        Color(0xFFB58DFF).copy(alpha = 0.12f)
                    )
                ),
                shape = bubbleShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compact) 9.dp else 12.dp,
                    top = if (compact) 8.dp else 11.dp,
                    end = if (compact) 9.dp else 12.dp,
                    bottom = tailHeight + if (compact) 6.dp else 8.dp
                )
        ) {
            MemoryPanelHeader(compact)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 5.dp else 7.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f),
                                Color(0xFFB58DFF).copy(alpha = 0.07f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
                shownItems.forEachIndexed { index, item ->
                    MemoryPreviewRow(
                        item = item,
                        compact = compact,
                        drawLineBelow = index < shownItems.lastIndex
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 5.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "另有 ${8 - shownItems.size} 项未展开",
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = if (compact) 6.8.sp else 7.2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .height(if (compact) 18.dp else 21.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.085f),
                                    Color.White.copy(alpha = 0.040f)
                                )
                            )
                        )
                        .border(0.7.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenManager)
                        .padding(horizontal = if (compact) 8.dp else 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "完整管理",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = if (compact) 6.8.sp else 7.2.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryPanelHeader(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
    ) {
        MemoryStackGlyph(glyphSize = if (compact) 24.dp else 30.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "记忆",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = if (compact) 11.sp else 13.5.sp,
                lineHeight = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "本轮生效 4 项 · 已保存 8 项",
                color = Color(0xFFBCC9E8).copy(alpha = 0.46f),
                fontSize = if (compact) 6.6.sp else 7.8.sp,
                lineHeight = if (compact) 7.2.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .height(if (compact) 17.dp else 20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(0.7.dp, Color(0xFF8DFFF4).copy(alpha = 0.07f), RoundedCornerShape(999.dp))
                .padding(horizontal = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF8DFFF4))
            )
            Text(
                text = "生效",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = if (compact) 6.4.sp else 7.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun MemoryStackGlyph(glyphSize: Dp) {
    val radius = if (glyphSize <= 24.dp) 9.dp else 11.dp
    Box(
        modifier = Modifier
            .size(glyphSize)
            .clip(RoundedCornerShape(radius))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.035f)
                    )
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(glyphSize * 0.62f)) {
            drawRoundRect(
                color = Color(0xFFF2F8FF).copy(alpha = 0.88f),
                topLeft = Offset(size.width * 0.06f, size.height * 0.08f),
                size = Size(size.width * 0.57f, size.height * 0.48f),
                cornerRadius = CornerRadius(size.minDimension * 0.14f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFB8F7F0).copy(alpha = 0.86f),
                        Color(0xFFCAB8FF).copy(alpha = 0.78f)
                    )
                ),
                topLeft = Offset(size.width * 0.30f, size.height * 0.39f),
                size = Size(size.width * 0.62f, size.height * 0.51f),
                cornerRadius = CornerRadius(size.minDimension * 0.15f)
            )
            drawLine(
                color = Color(0xFF6881A6).copy(alpha = 0.46f),
                start = Offset(size.width * 0.21f, size.height * 0.27f),
                end = Offset(size.width * 0.48f, size.height * 0.27f),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = Color(0xFF6881A6).copy(alpha = 0.30f),
                start = Offset(size.width * 0.21f, size.height * 0.40f),
                end = Offset(size.width * 0.55f, size.height * 0.40f),
                strokeWidth = 0.7.dp.toPx()
            )
        }
    }
}

@Composable
private fun MemoryPreviewRow(
    item: MemoryQuickPreviewItem,
    compact: Boolean,
    drawLineBelow: Boolean
) {
    val itemHeight = if (compact) 34.dp else 43.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(if (compact) 8.dp else 10.dp)
                .fillMaxHeight()
        ) {
            val centerX = size.width / 2f
            val dotY = if (compact) 9.dp.toPx() else 10.dp.toPx()
            if (drawLineBelow) {
                drawLine(
                    color = item.accent.copy(alpha = 0.20f),
                    start = Offset(centerX, dotY + 4.dp.toPx()),
                    end = Offset(centerX, size.height),
                    strokeWidth = 0.75.dp.toPx()
                )
            }
            drawCircle(
                color = item.accent.copy(alpha = 0.18f),
                radius = if (compact) 4.dp.toPx() else 4.5.dp.toPx(),
                center = Offset(centerX, dotY)
            )
            drawCircle(
                color = item.accent,
                radius = if (compact) 2.2.dp.toPx() else 2.5.dp.toPx(),
                center = Offset(centerX, dotY)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(if (compact) 11.dp else 14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (compact) 0.062f else 0.078f),
                            Color.White.copy(alpha = if (compact) 0.025f else 0.034f)
                        )
                    )
                )
                .border(
                    0.7.dp,
                    Color.White.copy(alpha = 0.065f),
                    RoundedCornerShape(if (compact) 11.dp else 14.dp)
                )
                .padding(
                    horizontal = if (compact) 7.dp else 9.dp,
                    vertical = if (compact) 5.dp else 6.dp
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.kind,
                    color = item.accent.copy(alpha = 0.94f),
                    fontSize = if (compact) 6.8.sp else 7.7.sp,
                    lineHeight = if (compact) 7.4.sp else 8.4.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.priority,
                    color = Color(0xFFBCC9E8).copy(alpha = 0.34f),
                    fontSize = if (compact) 6.1.sp else 6.8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(if (compact) 2.dp else 3.dp))
            Text(
                text = item.text,
                color = Color.White.copy(alpha = 0.76f),
                fontSize = if (compact) 7.3.sp else 8.6.sp,
                lineHeight = if (compact) 8.2.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private class MemoryQuickPanelPositionProvider(
    private val desiredGapPx: Int,
    private val minimumGapPx: Int,
    private val safeTopPx: Int,
    private val safeHorizontalPx: Int,
    private val tailFraction: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = (anchorBounds.left + anchorBounds.right) / 2
        val desiredLeft = anchorCenterX - (popupContentSize.width * tailFraction).roundToInt()
        val maximumLeft = max(
            safeHorizontalPx,
            windowSize.width - safeHorizontalPx - popupContentSize.width
        )
        val left = desiredLeft.coerceIn(safeHorizontalPx, maximumLeft)

        val freeForGap = anchorBounds.top - safeTopPx - popupContentSize.height
        val effectiveGap = min(desiredGapPx, max(minimumGapPx, freeForGap))
        val top = max(safeTopPx, anchorBounds.top - effectiveGap - popupContentSize.height)
        return IntOffset(left, top)
    }
}

private class MemoryBubbleShape(
    private val cornerRadius: Dp,
    private val tailHeight: Dp,
    private val tailHalfWidth: Dp,
    private val tailFraction: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
            .coerceAtMost(min(size.width, size.height) * 0.24f)
        val tailHeightPx = with(density) { tailHeight.toPx() }
            .coerceAtMost(size.height * 0.20f)
        val tailHalfWidthPx = with(density) { tailHalfWidth.toPx() }
        val bodyBottom = size.height - tailHeightPx
        val tailCenter = (size.width * tailFraction).coerceIn(
            radius + tailHalfWidthPx + 2f,
            size.width - radius - tailHalfWidthPx - 2f
        )

        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, radius)
            lineTo(size.width, bodyBottom - radius)
            quadraticBezierTo(size.width, bodyBottom, size.width - radius, bodyBottom)
            lineTo(tailCenter + tailHalfWidthPx, bodyBottom)
            cubicTo(
                tailCenter + tailHalfWidthPx * 0.70f,
                bodyBottom,
                tailCenter + tailHalfWidthPx * 0.46f,
                bodyBottom + tailHeightPx * 0.40f,
                tailCenter + tailHalfWidthPx * 0.15f,
                bodyBottom + tailHeightPx * 0.84f
            )
            cubicTo(
                tailCenter + tailHalfWidthPx * 0.07f,
                size.height - 0.7f,
                tailCenter - tailHalfWidthPx * 0.07f,
                size.height - 0.7f,
                tailCenter - tailHalfWidthPx * 0.15f,
                bodyBottom + tailHeightPx * 0.84f
            )
            cubicTo(
                tailCenter - tailHalfWidthPx * 0.46f,
                bodyBottom + tailHeightPx * 0.40f,
                tailCenter - tailHalfWidthPx * 0.70f,
                bodyBottom,
                tailCenter - tailHalfWidthPx,
                bodyBottom
            )
            lineTo(radius, bodyBottom)
            quadraticBezierTo(0f, bodyBottom, 0f, bodyBottom - radius)
            lineTo(0f, radius)
            quadraticBezierTo(0f, 0f, radius, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
