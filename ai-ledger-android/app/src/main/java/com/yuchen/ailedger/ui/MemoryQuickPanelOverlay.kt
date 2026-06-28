package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantCustomInstructionsState
import com.yuchen.ailedger.data.AssistantMemoryItem
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryState
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.memoryCategoryLabel
import com.yuchen.ailedger.data.memoryPriorityLabel
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val MemoryOverlayNoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val MemoryPanelDesiredWidth = 286.dp
private val MemoryPanelDesiredHeight = 258.dp
private val MemoryPanelMinimumHeight = 204.dp
private val MemoryPanelTailHeight = 12.dp
private val MemoryPanelTailHalfWidth = 15.dp

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

    fun toggle() {
        expanded = !expanded
    }

    fun dismiss() {
        expanded = false
    }
}

@Immutable
private data class MemoryPreviewUiItem(
    val id: String,
    val title: String,
    val status: String,
    val content: String,
    val accent: Color,
    val active: Boolean,
)

/**
 * 聊天玻璃标题栏中的记忆按钮。
 *
 * 这里只记录真实窗口坐标并切换展开状态。面板始终由 Assistant 根节点在同一个
 * Compose 窗口中绘制，不创建 Popup / Dialog，也不进入 OpenGL registry。
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
            onClick = MemoryQuickOverlayState::toggle,
        )
    }
}

/**
 * Assistant 页面同窗口中的真实记忆快捷面板。
 *
 * 数据直接来自 SupabaseAuthRepository、AssistantMemoryRepository 和
 * AssistantCustomInstructionsRepository；只有展开时才订阅这些 StateFlow。
 */
@Composable
internal fun MemoryQuickPanelSameWindowOverlayHost() {
    if (!MemoryQuickOverlayState.expanded) return

    val context = LocalContext.current.applicationContext
    val assistantViewModel: AssistantViewModel = viewModel()
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val customInstructionsRepository = remember(context) {
        AssistantCustomInstructionsRepository.get(context)
    }
    val accountState by authRepository.state.collectAsState()
    val memoryState by memoryRepository.state.collectAsState()
    val customInstructionsState by customInstructionsRepository.state.collectAsState()
    val previewItems = remember(memoryState.memories, customInstructionsState) {
        buildMemoryPreviewItems(memoryState, customInstructionsState)
    }

    val density = LocalDensity.current
    val openScaleX = remember { Animatable(0.42f) }
    val openScaleY = remember { Animatable(0.12f) }
    val openAlpha = remember { Animatable(0f) }
    val openLift = remember { Animatable(18f) }
    val panelPress = remember { Animatable(0f) }
    val panelPressScope = rememberCoroutineScope()
    var rootBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                openScaleX.animateTo(
                    1f,
                    spring(dampingRatio = 0.50f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch {
                openScaleY.animateTo(
                    1f,
                    spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch { openAlpha.animateTo(1f, tween(92, easing = FastOutSlowInEasing)) }
            launch {
                openLift.animateTo(
                    0f,
                    spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootBounds = coordinates.boundsInWindow()
            },
    ) {
        val safeInset = 10.dp
        val gap = 7.dp
        val safePx = with(density) { safeInset.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val desiredWidthPx = with(density) { MemoryPanelDesiredWidth.roundToPx() }
        val maxPanelWidthPx = (constraints.maxWidth - safePx * 2).coerceAtLeast(1)
        val panelWidthPx = desiredWidthPx.coerceAtMost(maxPanelWidthPx)
        val panelWidth = with(density) { panelWidthPx.toDp() }

        val anchor = MemoryQuickOverlayState.anchorBounds
        val localAnchorLeft = anchor.left - rootBounds.left
        val localAnchorTop = anchor.top - rootBounds.top
        val localAnchorRight = anchor.right - rootBounds.left
        val anchorCenterX = ((localAnchorLeft + localAnchorRight) * 0.5f).roundToInt()

        val desiredPanelHeightPx = with(density) { MemoryPanelDesiredHeight.roundToPx() }
        val minimumPanelHeightPx = with(density) { MemoryPanelMinimumHeight.roundToPx() }
        val availableAbovePx = (localAnchorTop.roundToInt() - gapPx - safePx).coerceAtLeast(1)
        val panelHeightPx = desiredPanelHeightPx
            .coerceAtMost(availableAbovePx)
            .coerceAtLeast(minOf(minimumPanelHeightPx, availableAbovePx))
        val panelHeight = with(density) { panelHeightPx.toDp() }
        val compact = panelHeightPx < desiredPanelHeightPx - with(density) { 20.dp.roundToPx() }

        val desiredX = anchorCenterX - (panelWidthPx * 0.72f).roundToInt()
        val maxX = (constraints.maxWidth - panelWidthPx - safePx).coerceAtLeast(safePx)
        val panelX = desiredX.coerceIn(safePx, maxX)
        val panelY = (localAnchorTop.roundToInt() - gapPx - panelHeightPx).coerceAtLeast(safePx)
        val tailFraction = ((anchorCenterX - panelX).toFloat() / panelWidthPx.coerceAtLeast(1))
            .coerceIn(0.16f, 0.84f)
        val panelShape = remember(panelWidthPx, panelHeightPx, tailFraction) {
            MemoryUnifiedBubbleShape(
                cornerRadius = 25.dp,
                tailHeight = MemoryPanelTailHeight,
                tailHalfWidth = MemoryPanelTailHalfWidth,
                tailCenterFraction = tailFraction,
            )
        }

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
                    val press = panelPress.value
                    val compression = press.coerceAtLeast(0f)
                    val rebound = (-press).coerceAtLeast(0f)
                    alpha = openAlpha.value
                    scaleX = openScaleX.value * (1f + compression * 0.020f - rebound * 0.010f)
                    scaleY = openScaleY.value * (1f - compression * 0.034f + rebound * 0.020f)
                    translationY = openLift.value.dp.toPx() + compression * 3.2.dp.toPx() - rebound * 1.4.dp.toPx()
                    transformOrigin = TransformOrigin(tailFraction, 1f)
                }
                .shadow(
                    elevation = 10.dp,
                    shape = panelShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color(0xFF8DFFF4).copy(alpha = 0.08f),
                )
                .clip(panelShape)
                .pointerInput(panelShape) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        panelPressScope.launch {
                            panelPress.stop()
                            if (panelPress.value < 0.18f) panelPress.snapTo(0.18f)
                            panelPress.animateTo(
                                1f,
                                tween(145, easing = FastOutSlowInEasing),
                            )
                            panelPress.animateTo(
                                0.82f,
                                spring(
                                    dampingRatio = 0.64f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        panelPressScope.launch {
                            panelPress.stop()
                            panelPress.animateTo(
                                -0.18f,
                                tween(120, easing = FastOutSlowInEasing),
                            )
                            panelPress.animateTo(
                                0.055f,
                                spring(
                                    dampingRatio = 0.44f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                            panelPress.animateTo(
                                0f,
                                spring(
                                    dampingRatio = 0.72f,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                    }
                },
        ) {
            GlassSceneScope(group = GlassSceneGroup.Unassigned) {
                PressableGlass(
                    quality = RenderQuality.Balanced,
                    glassIntensity = 1.04f,
                    motionIntensity = 0.72f,
                    radius = 25,
                    modifier = Modifier.fillMaxSize(),
                    role = GlassRole.Floating,
                    onClick = {},
                ) {
                    MemoryUnifiedPanelContent(
                        accountState = accountState,
                        memoryState = memoryState,
                        customInstructionsState = customInstructionsState,
                        previewItems = previewItems,
                        compact = compact,
                        onLogin = {
                            MemoryQuickOverlayState.dismiss()
                            assistantViewModel.selectTab(AppTab.Settings)
                        },
                        onOpenManager = {
                            MemoryQuickOverlayState.dismiss()
                            assistantViewModel.selectTab(AppTab.Settings)
                        },
                        onRefresh = {
                            memoryRepository.refresh()
                            customInstructionsRepository.refresh()
                        },
                    )
                }
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
        MemoryFolderGlyph(
            modifier = Modifier.size(13.dp),
            active = active,
        )
    }
}

@Composable
private fun MemoryUnifiedPanelContent(
    accountState: SupabaseAccountState,
    memoryState: AssistantMemoryState,
    customInstructionsState: AssistantCustomInstructionsState,
    previewItems: List<MemoryPreviewUiItem>,
    compact: Boolean,
    onLogin: () -> Unit,
    onOpenManager: () -> Unit,
    onRefresh: () -> Unit,
) {
    val savedCount = memoryState.memories.size +
        if (customInstructionsState.content.isNotBlank()) 1 else 0
    val activeCount = memoryState.activeCount +
        if (customInstructionsState.effectiveText() != null) 1 else 0
    val busy = accountState.loading || memoryState.loading || customInstructionsState.loading
    val cloudError = accountState.isLoggedIn && (
        memoryState.error || customInstructionsState.error ||
            (!memoryState.loading && !memoryState.cloudReady) ||
            (!customInstructionsState.loading && !customInstructionsState.cloudReady)
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (compact) 11.dp else 13.dp,
                top = if (compact) 9.dp else 11.dp,
                end = if (compact) 11.dp else 13.dp,
                bottom = MemoryPanelTailHeight + if (compact) 8.dp else 10.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        MemoryPanelHeader(
            accountState = accountState,
            activeCount = activeCount,
            savedCount = savedCount,
            busy = busy,
            cloudError = cloudError,
            compact = compact,
        )
        MemoryHairline()

        when {
            accountState.loading -> MemoryStatusCard(
                title = "正在检查登录状态",
                description = "正在恢复本机保存的账号会话。",
                accent = Color(0xFF8DFFF4),
                compact = compact,
            )

            !accountState.isLoggedIn -> MemoryLoginState(
                compact = compact,
                onLogin = onLogin,
            )

            busy -> MemoryStatusCard(
                title = "正在同步真实记忆",
                description = "正在读取该账号的自定义指令和长期记忆。",
                accent = Color(0xFF8DFFF4),
                compact = compact,
            )

            cloudError -> MemoryErrorState(
                message = listOf(memoryState.message, customInstructionsState.message)
                    .firstOrNull { it.isNotBlank() && it.contains("失败") || it.contains("尚未") }
                    ?: "云端记忆暂时无法读取，请稍后重试。",
                compact = compact,
                onRefresh = onRefresh,
            )

            previewItems.isEmpty() -> MemoryEmptyState(
                compact = compact,
                onOpenManager = onOpenManager,
            )

            else -> {
                previewItems.take(if (compact) 2 else 3).forEach { item ->
                    MemoryPreviewCard(item = item, compact = compact)
                }
                Spacer(Modifier.weight(1f))
                MemoryPanelFooter(
                    hiddenCount = (savedCount - previewItems.take(if (compact) 2 else 3).size)
                        .coerceAtLeast(0),
                    memoryEnabled = memoryState.memoryEnabled,
                    compact = compact,
                    onOpenManager = onOpenManager,
                )
            }
        }
    }
}

@Composable
private fun MemoryPanelHeader(
    accountState: SupabaseAccountState,
    activeCount: Int,
    savedCount: Int,
    busy: Boolean,
    cloudError: Boolean,
    compact: Boolean,
) {
    val statusText = when {
        accountState.loading -> "检查中"
        !accountState.isLoggedIn -> "未登录"
        busy -> "同步中"
        cloudError -> "受限"
        else -> "生效"
    }
    val statusColor = when {
        cloudError -> Color(0xFFFFC178)
        !accountState.isLoggedIn -> Color.White.copy(alpha = 0.42f)
        else -> Color(0xFF8DFFF4)
    }
    val subtitle = when {
        accountState.loading -> "正在恢复账号状态"
        !accountState.isLoggedIn -> "登录后同步自定义指令与长期记忆"
        busy -> "正在读取真实云端数据"
        cloudError -> "云端数据暂时不可用"
        else -> "本轮生效 $activeCount 项 · 已保存 $savedCount 项"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(if (compact) 11.dp else 13.dp))
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
                    RoundedCornerShape(if (compact) 11.dp else 13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MemoryFolderGlyph(
                modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                active = if (accountState.isLoggedIn) 1f else 0.35f,
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "记忆",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = if (compact) 15.sp else 17.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = if (compact) 8.sp else 9.sp,
                lineHeight = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(0.6.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(999.dp))
                .padding(horizontal = if (compact) 8.dp else 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor),
            )
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = if (compact) 8.sp else 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            )
        }
    }
}

@Composable
private fun MemoryPreviewCard(item: MemoryPreviewUiItem, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = if (item.active) 0.062f else 0.038f),
                        item.accent.copy(alpha = if (item.active) 0.040f else 0.018f),
                        Color.White.copy(alpha = 0.018f),
                    ),
                ),
            )
            .border(
                0.65.dp,
                item.accent.copy(alpha = if (item.active) 0.12f else 0.055f),
                RoundedCornerShape(if (compact) 14.dp else 17.dp),
            )
            .padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 7.dp else 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (compact) 6.dp else 7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(item.accent.copy(alpha = if (item.active) 0.95f else 0.42f)),
        )
        Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                color = item.accent.copy(alpha = if (item.active) 0.94f else 0.58f),
                fontSize = if (compact) 9.5.sp else 10.5.sp,
                lineHeight = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.content,
                color = Color.White.copy(alpha = if (item.active) 0.64f else 0.38f),
                fontSize = if (compact) 8.sp else 9.sp,
                lineHeight = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = item.status,
            color = Color.White.copy(alpha = if (item.active) 0.34f else 0.24f),
            fontSize = if (compact) 7.sp else 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
            maxLines = 1,
        )
    }
}

@Composable
private fun MemoryLoginState(compact: Boolean, onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(if (compact) 17.dp else 20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.060f),
                        Color(0xFF8DFFF4).copy(alpha = 0.028f),
                        Color(0xFF9B73FF).copy(alpha = 0.035f),
                    ),
                ),
            )
            .border(
                0.7.dp,
                Color.White.copy(alpha = 0.075f),
                RoundedCornerShape(if (compact) 17.dp else 20.dp),
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "登录后解锁真实记忆",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "同步自定义指令、个人偏好和长期项目背景。",
            color = Color.White.copy(alpha = 0.44f),
            fontSize = if (compact) 8.5.sp else 9.5.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        MemorySmallActionButton(
            text = "立即登录",
            accent = Color(0xFF8DFFF4),
            onClick = onLogin,
        )
    }
}

@Composable
private fun MemoryEmptyState(compact: Boolean, onOpenManager: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(if (compact) 17.dp else 20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有保存记忆",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "在完整管理中添加偏好、个人信息或项目背景。",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = if (compact) 8.5.sp else 9.5.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        MemorySmallActionButton(
            text = "去添加",
            accent = Color(0xFFC6AEFF),
            onClick = onOpenManager,
        )
    }
}

@Composable
private fun MemoryErrorState(
    message: String,
    compact: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(if (compact) 17.dp else 20.dp))
            .background(Color(0xFFFFC178).copy(alpha = 0.040f))
            .border(
                0.7.dp,
                Color(0xFFFFC178).copy(alpha = 0.10f),
                RoundedCornerShape(if (compact) 17.dp else 20.dp),
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "记忆同步暂时受限",
            color = Color(0xFFFFD69E).copy(alpha = 0.88f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = if (compact) 8.sp else 9.sp,
            lineHeight = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        MemorySmallActionButton(
            text = "重新同步",
            accent = Color(0xFFFFC178),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun MemoryStatusCard(
    title: String,
    description: String,
    accent: Color,
    compact: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(if (compact) 17.dp else 20.dp))
            .background(accent.copy(alpha = 0.035f))
            .border(
                0.7.dp,
                accent.copy(alpha = 0.085f),
                RoundedCornerShape(if (compact) 17.dp else 20.dp),
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = if (compact) 8.5.sp else 9.5.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun MemoryPanelFooter(
    hiddenCount: Int,
    memoryEnabled: Boolean,
    compact: Boolean,
    onOpenManager: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                hiddenCount > 0 -> "另有 $hiddenCount 项未展开"
                memoryEnabled -> "长期记忆已开启"
                else -> "长期记忆当前关闭"
            },
            color = Color.White.copy(alpha = 0.30f),
            fontSize = if (compact) 7.5.sp else 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
        )
        Spacer(Modifier.weight(1f))
        MemorySmallActionButton(
            text = "完整管理",
            accent = Color(0xFFC6AEFF),
            onClick = onOpenManager,
        )
    }
}

@Composable
private fun MemorySmallActionButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "memory-small-action-scale",
    )
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.84f),
        fontSize = 8.5.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(platformStyle = MemoryOverlayNoFontPadding),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f + (1f - scale) * 0.55f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.080f),
                        accent.copy(alpha = 0.060f),
                        Color.White.copy(alpha = 0.045f),
                    ),
                ),
            )
            .border(0.7.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun MemoryHairline() {
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
private fun MemoryFolderGlyph(modifier: Modifier, active: Float) {
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

private fun buildMemoryPreviewItems(
    memoryState: AssistantMemoryState,
    customInstructionsState: AssistantCustomInstructionsState,
): List<MemoryPreviewUiItem> {
    val customItem = customInstructionsState.content
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { content ->
            MemoryPreviewUiItem(
                id = "custom-instructions",
                title = "自定义指令",
                status = if (customInstructionsState.enabled && customInstructionsState.cloudReady) {
                    "生效中"
                } else {
                    "已停用"
                },
                content = content.lineSequence().firstOrNull().orEmpty().trim(),
                accent = Color(0xFFC6A5FF),
                active = customInstructionsState.enabled && customInstructionsState.cloudReady,
            )
        }

    val memoryItems = memoryState.memories
        .asSequence()
        .sortedWith(
            compareByDescending<AssistantMemoryItem> { it.pinned }
                .thenByDescending { it.priority }
                .thenByDescending { it.updatedAt },
        )
        .map { item -> item.toPreviewUiItem(memoryState.memoryEnabled) }
        .toList()

    return buildList {
        customItem?.let(::add)
        addAll(memoryItems)
    }
}

private fun AssistantMemoryItem.toPreviewUiItem(memoryEnabled: Boolean): MemoryPreviewUiItem {
    val accent = when (category) {
        "profile" -> Color(0xFFFFD07F)
        "preference" -> Color(0xFF8DFFF4)
        "project" -> Color(0xFF8FB9FF)
        "rule" -> Color(0xFFC6A5FF)
        else -> Color(0xFFE3EBFF)
    }
    val title = buildString {
        if (pinned) append("置顶 · ")
        append(memoryCategoryLabel(category))
    }
    return MemoryPreviewUiItem(
        id = id,
        title = title,
        status = if (!enabled) "已停用" else memoryPriorityLabel(priority),
        content = content.lineSequence().firstOrNull().orEmpty().trim(),
        accent = accent,
        active = enabled && memoryEnabled,
    )
}

private class MemoryUnifiedBubbleShape(
    private val cornerRadius: Dp,
    private val tailHeight: Dp,
    private val tailHalfWidth: Dp,
    private val tailCenterFraction: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
            .coerceAtMost(minOf(size.width, size.height) * 0.30f)
        val tailH = with(density) { tailHeight.toPx() }
            .coerceIn(0f, size.height * 0.22f)
        val halfTail = with(density) { tailHalfWidth.toPx() }
            .coerceAtMost(size.width * 0.16f)
        val bodyBottom = (size.height - tailH).coerceAtLeast(radius * 2f)
        val tailCenter = (size.width * tailCenterFraction.coerceIn(0.16f, 0.84f))
            .coerceIn(radius + halfTail, size.width - radius - halfTail)

        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, radius)
            lineTo(size.width, bodyBottom - radius)
            quadraticBezierTo(size.width, bodyBottom, size.width - radius, bodyBottom)
            lineTo(tailCenter + halfTail, bodyBottom)
            cubicTo(
                tailCenter + halfTail * 0.56f,
                bodyBottom + tailH * 0.08f,
                tailCenter + halfTail * 0.28f,
                bodyBottom + tailH * 0.72f,
                tailCenter,
                size.height,
            )
            cubicTo(
                tailCenter - halfTail * 0.28f,
                bodyBottom + tailH * 0.72f,
                tailCenter - halfTail * 0.56f,
                bodyBottom + tailH * 0.08f,
                tailCenter - halfTail,
                bodyBottom,
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
