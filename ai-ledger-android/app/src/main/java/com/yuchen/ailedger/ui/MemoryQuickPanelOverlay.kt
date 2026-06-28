package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MEMORY_OVERLAY_TAIL_FRACTION = 0.72f
private val MemoryOverlayNoFontPadding = PlatformTextStyle(includeFontPadding = false)

@Stable
private object MemoryQuickOverlayState {
    var expanded by mutableStateOf(false)
    var anchorBounds by mutableStateOf(Rect.Zero)

    fun updateAnchor(next: Rect) {
        val current = anchorBounds
        if (
            abs(current.left - next.left) > 0.5f ||
            abs(current.top - next.top) > 0.5f ||
            abs(current.right - next.right) > 0.5f ||
            abs(current.bottom - next.bottom) > 0.5f
        ) {
            anchorBounds = next
        }
    }

    fun dismiss() {
        expanded = false
    }
}

@Immutable
private data class MemoryOverlayItem(
    val kind: String,
    val priority: String,
    val text: String,
    val accent: Color,
)

private val memoryOverlayItems = listOf(
    MemoryOverlayItem(
        kind = "自定义指令",
        priority = "高优先级",
        text = "中文回答，先给结论，避免机械套话。",
        accent = Color(0xFFC19AFF),
    ),
    MemoryOverlayItem(
        kind = "置顶 · 个人信息",
        priority = "核心",
        text = "用户名是邹羽宸，电气工程及其自动化专业。",
        accent = Color(0xFFFFD07F),
    ),
    MemoryOverlayItem(
        kind = "偏好",
        priority = "重要",
        text = "表达自然简洁，少分点，重点明确。",
        accent = Color(0xFF8DFFF4),
    ),
)

/**
 * 聊天玻璃标题栏中的记忆按钮。
 *
 * 这里只记录按钮的真实窗口坐标并切换共享展开状态，不创建 Popup、Dialog 或其他窗口。
 */
@Composable
internal fun MemoryQuickPanelButtonHost(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(32.dp)
            .height(22.dp)
            .onGloballyPositioned { coordinates ->
                MemoryQuickOverlayState.updateAnchor(coordinates.boundsInWindow())
            },
        contentAlignment = Alignment.Center,
    ) {
        MemoryOverlayFolderButton(
            expanded = MemoryQuickOverlayState.expanded,
            onClick = {
                MemoryQuickOverlayState.expanded = !MemoryQuickOverlayState.expanded
            },
        )
    }
}

/**
 * Assistant 页面同一 Compose 窗口中的记忆面板覆盖层。
 *
 * 该入口由 App.kt 已存在的页面兄弟节点承载，所以面板既不会被聊天 Shell 裁剪，
 * 也不会创建会与 OpenGL Surface 冲突的额外 Android 窗口。
 */
@Composable
internal fun MemoryQuickPanelSameWindowOverlayHost() {
    if (!MemoryQuickOverlayState.expanded) return

    val assistantViewModel: AssistantViewModel = viewModel()
    val density = LocalDensity.current
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entered = true
    }

    val appearance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(130),
        label = "memory-overlay-appearance",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootBounds = coordinates.boundsInWindow()
            },
    ) {
        val panelWidth = 270.dp
        val panelHeight = 232.dp
        val safeInset = 10.dp
        val gap = 8.dp
        val panelWidthPx = with(density) { panelWidth.roundToPx() }
        val panelHeightPx = with(density) { panelHeight.roundToPx() }
        val safePx = with(density) { safeInset.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val anchor = MemoryQuickOverlayState.anchorBounds
        val localAnchorLeft = anchor.left - rootBounds.left
        val localAnchorTop = anchor.top - rootBounds.top
        val localAnchorRight = anchor.right - rootBounds.left
        val localAnchorBottom = anchor.bottom - rootBounds.top
        val anchorCenterX = ((localAnchorLeft + localAnchorRight) * 0.5f).roundToInt()
        val desiredX = anchorCenterX - (panelWidthPx * MEMORY_OVERLAY_TAIL_FRACTION).roundToInt()
        val maxX = (constraints.maxWidth - panelWidthPx - safePx).coerceAtLeast(safePx)
        val panelX = desiredX.coerceIn(safePx, maxX)
        val aboveY = localAnchorTop.roundToInt() - gapPx - panelHeightPx
        val belowY = localAnchorBottom.roundToInt() + gapPx
        val maxY = (constraints.maxHeight - panelHeightPx - safePx).coerceAtLeast(safePx)
        val placeAbove = aboveY >= safePx
        val panelY = if (placeAbove) aboveY else belowY.coerceIn(safePx, maxY)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = MemoryQuickOverlayState::dismiss,
                ),
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(panelX, panelY) }
                .width(panelWidth)
                .height(panelHeight)
                .graphicsLayer {
                    alpha = appearance
                    scaleX = 0.94f + appearance * 0.06f
                    scaleY = 0.94f + appearance * 0.06f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = MEMORY_OVERLAY_TAIL_FRACTION,
                        pivotFractionY = if (placeAbove) 1f else 0f,
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            GlassSceneScope(group = GlassSceneGroup.Unassigned) {
                MemoryOverlayPanel(
                    quality = RenderQuality.Balanced,
                    tailOnBottom = placeAbove,
                    onOpenManager = {
                        MemoryQuickOverlayState.dismiss()
                        assistantViewModel.selectTab(AppTab.Settings)
                    },
                )
            }
        }
    }
}

@Composable
private fun MemoryOverlayFolderButton(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150),
        label = "memory-overlay-active",
    )
    val pressProgress by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            expanded -> 0.34f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMedium),
        label = "memory-overlay-press",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - pressProgress * 0.055f
                scaleY = 1f + pressProgress * 0.045f
                translationY = pressProgress * 0.72.dp.toPx()
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
                        Color.White.copy(alpha = 0.095f - pressProgress * 0.035f),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
        MemoryOverlayFolderGlyph(
            modifier = Modifier.size(13.dp),
            active = active,
        )
    }
}

@Composable
private fun MemoryOverlayFolderGlyph(
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
private fun MemoryOverlayPanel(
    quality: RenderQuality,
    tailOnBottom: Boolean,
    onOpenManager: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start,
    ) {
        if (!tailOnBottom) MemoryOverlayTail(pointUp = true)

        GlassPanel(
            quality = quality,
            glassIntensity = 1f,
            motionIntensity = 0f,
            radius = 24,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            role = GlassRole.Floating,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                MemoryOverlayHeader()
                MemoryOverlayDivider()
                memoryOverlayItems.forEach { item -> MemoryOverlayCard(item) }
                Spacer(Modifier.weight(1f))
                MemoryOverlayFooter(onOpenManager)
            }
        }

        if (tailOnBottom) MemoryOverlayTail(pointUp = false)
    }
}

@Composable
private fun MemoryOverlayTail(pointUp: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .offset(x = 182.dp)
                .size(width = 24.dp, height = 10.dp),
        ) {
            val tail = Path().apply {
                if (pointUp) {
                    moveTo(0f, size.height)
                    quadraticBezierTo(size.width * 0.28f, size.height - 1f, size.width * 0.50f, 0f)
                    quadraticBezierTo(size.width * 0.72f, size.height - 1f, size.width, size.height)
                } else {
                    moveTo(0f, 0f)
                    quadraticBezierTo(size.width * 0.28f, 1f, size.width * 0.50f, size.height)
                    quadraticBezierTo(size.width * 0.72f, 1f, size.width, 0f)
                }
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

@Composable
private fun MemoryOverlayHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFF8DFFF4).copy(alpha = 0.09f),
                            Color(0xFF9B73FF).copy(alpha = 0.13f),
                        ),
                    ),
                )
                .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MemoryOverlayFolderGlyph(Modifier.size(16.dp), active = 1f)
        }
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "记忆",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            )
            Text(
                text = "本轮生效 4 项 · 已保存 8 项",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(0.6.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            )
        }
    }
}

@Composable
private fun MemoryOverlayDivider() {
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
private fun MemoryOverlayCard(item: MemoryOverlayItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.058f),
                        item.accent.copy(alpha = 0.030f),
                        Color.White.copy(alpha = 0.022f),
                    ),
                ),
            )
            .border(0.6.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(item.accent.copy(alpha = 0.92f)),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.kind,
                color = item.accent.copy(alpha = 0.92f),
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.text,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.priority,
            color = Color.White.copy(alpha = 0.30f),
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            maxLines = 1,
        )
    }
}

@Composable
private fun MemoryOverlayFooter(onOpenManager: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "另有 5 项未展开",
            color = Color.White.copy(alpha = 0.30f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "完整管理",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
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
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
