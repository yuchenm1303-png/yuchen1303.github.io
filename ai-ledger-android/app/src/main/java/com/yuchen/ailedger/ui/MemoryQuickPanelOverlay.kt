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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val MemoryNoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val MemoryPanelWidth = 286.dp
private val MemoryPanelHeight = 258.dp
private val MemoryPanelMinHeight = 204.dp
private val MemoryTailHeight = 12.dp
private val MemoryTailHalfWidth = 15.dp

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
        MemoryFolderButton(
            expanded = MemoryQuickOverlayState.expanded,
            onClick = MemoryQuickOverlayState::toggle,
        )
    }
}

@Composable
internal fun MemoryQuickPanelSameWindowOverlayHost() {
    if (!MemoryQuickOverlayState.expanded) return

    val context = LocalContext.current.applicationContext
    val assistantViewModel: AssistantViewModel = viewModel()
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val customRepository = remember(context) { AssistantCustomInstructionsRepository.get(context) }
    val accountState by authRepository.state.collectAsState()
    val memoryState by memoryRepository.state.collectAsState()
    val customState by customRepository.state.collectAsState()
    val previewItems = remember(memoryState.memories, memoryState.memoryEnabled, customState) {
        buildPreviewItems(memoryState, customState)
    }

    val density = LocalDensity.current
    val revealX = remember { Animatable(0.42f) }
    val revealY = remember { Animatable(0.12f) }
    val revealAlpha = remember { Animatable(0f) }
    val revealLift = remember { Animatable(18f) }
    val panelPress = remember { Animatable(0f) }
    val pressScope = rememberCoroutineScope()
    var rootBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { revealX.animateTo(1f, spring(0.50f, Spring.StiffnessMediumLow)) }
            launch { revealY.animateTo(1f, spring(0.56f, Spring.StiffnessMediumLow)) }
            launch { revealAlpha.animateTo(1f, tween(92, easing = FastOutSlowInEasing)) }
            launch { revealLift.animateTo(0f, spring(0.52f, Spring.StiffnessMediumLow)) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        val safePx = with(density) { 10.dp.roundToPx() }
        val gapPx = with(density) { 7.dp.roundToPx() }
        val desiredWidthPx = with(density) { MemoryPanelWidth.roundToPx() }
        val panelWidthPx = desiredWidthPx.coerceAtMost((constraints.maxWidth - safePx * 2).coerceAtLeast(1))
        val panelWidth = with(density) { panelWidthPx.toDp() }

        val anchor = MemoryQuickOverlayState.anchorBounds
        val localAnchorTop = anchor.top - rootBounds.top
        val localAnchorCenterX = ((anchor.left + anchor.right) * 0.5f - rootBounds.left).roundToInt()
        val desiredHeightPx = with(density) { MemoryPanelHeight.roundToPx() }
        val minHeightPx = with(density) { MemoryPanelMinHeight.roundToPx() }
        val availableAbovePx = (localAnchorTop.roundToInt() - gapPx - safePx).coerceAtLeast(1)
        val panelHeightPx = desiredHeightPx
            .coerceAtMost(availableAbovePx)
            .coerceAtLeast(minOf(minHeightPx, availableAbovePx))
        val panelHeight = with(density) { panelHeightPx.toDp() }
        val compact = panelHeightPx < desiredHeightPx - with(density) { 20.dp.roundToPx() }

        val desiredX = localAnchorCenterX - (panelWidthPx * 0.72f).roundToInt()
        val panelX = desiredX.coerceIn(
            safePx,
            (constraints.maxWidth - panelWidthPx - safePx).coerceAtLeast(safePx),
        )
        val panelY = (localAnchorTop.roundToInt() - gapPx - panelHeightPx).coerceAtLeast(safePx)
        val tailFraction = ((localAnchorCenterX - panelX).toFloat() / panelWidthPx.coerceAtLeast(1))
            .coerceIn(0.16f, 0.84f)
        val panelShape = remember(panelWidthPx, panelHeightPx, tailFraction) {
            UnifiedMemoryShape(
                cornerRadius = 25.dp,
                tailHeight = MemoryTailHeight,
                tailHalfWidth = MemoryTailHalfWidth,
                tailCenterFraction = tailFraction,
            )
        }

        Box(
            Modifier
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
                    alpha = revealAlpha.value
                    scaleX = revealX.value * (1f + compression * 0.020f - rebound * 0.010f)
                    scaleY = revealY.value * (1f - compression * 0.034f + rebound * 0.020f)
                    translationY = revealLift.value.dp.toPx() +
                        compression * 3.2.dp.toPx() - rebound * 1.4.dp.toPx()
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
                        pressScope.launch {
                            panelPress.stop()
                            if (panelPress.value < 0.18f) panelPress.snapTo(0.18f)
                            panelPress.animateTo(1f, tween(145, easing = FastOutSlowInEasing))
                            panelPress.animateTo(0.82f, spring(0.64f, Spring.StiffnessMediumLow))
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        pressScope.launch {
                            panelPress.stop()
                            panelPress.animateTo(-0.18f, tween(120, easing = FastOutSlowInEasing))
                            panelPress.animateTo(0.055f, spring(0.44f, Spring.StiffnessMediumLow))
                            panelPress.animateTo(0f, spring(0.72f, Spring.StiffnessLow))
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
                    MemoryPanelContent(
                        accountState = accountState,
                        memoryState = memoryState,
                        customState = customState,
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
                            customRepository.refresh()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFolderButton(expanded: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150),
        label = "memory-folder-active",
    )
    val press by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            expanded -> 0.34f
            else -> 0f
        },
        animationSpec = spring(0.70f, Spring.StiffnessMedium),
        label = "memory-folder-press",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - press * 0.055f
                scaleY = 1f + press * 0.045f
                translationY = press * 0.72.dp.toPx()
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
                0.7.dp,
                Color.White.copy(alpha = 0.09f + active * 0.045f),
                RoundedCornerShape(999.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.095f - press * 0.035f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
        MemoryFolderGlyph(Modifier.size(13.dp), active)
    }
}

@Composable
private fun MemoryPanelContent(
    accountState: SupabaseAccountState,
    memoryState: AssistantMemoryState,
    customState: AssistantCustomInstructionsState,
    previewItems: List<MemoryPreviewUiItem>,
    compact: Boolean,
    onLogin: () -> Unit,
    onOpenManager: () -> Unit,
    onRefresh: () -> Unit,
) {
    val savedCount = memoryState.memories.size + if (customState.content.isNotBlank()) 1 else 0
    val activeCount = memoryState.activeCount + if (customState.effectiveText() != null) 1 else 0
    val busy = accountState.loading || memoryState.loading || customState.loading
    val cloudError = accountState.isLoggedIn && (
        memoryState.error || customState.error ||
            (!memoryState.loading && !memoryState.cloudReady) ||
            (!customState.loading && !customState.cloudReady)
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (compact) 11.dp else 13.dp,
                top = if (compact) 9.dp else 11.dp,
                end = if (compact) 11.dp else 13.dp,
                bottom = MemoryTailHeight + if (compact) 8.dp else 10.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        MemoryPanelHeader(accountState, activeCount, savedCount, busy, cloudError, compact)
        MemoryHairline()

        when {
            accountState.loading -> StatusBody(
                "正在检查登录状态",
                "正在恢复本机保存的账号会话。",
                Color(0xFF8DFFF4),
                compact,
            )
            !accountState.isLoggedIn -> LoginBody(compact, onLogin)
            busy -> StatusBody(
                "正在同步真实记忆",
                "正在读取该账号的自定义指令和长期记忆。",
                Color(0xFF8DFFF4),
                compact,
            )
            cloudError -> ErrorBody(
                message = listOf(memoryState.message, customState.message)
                    .firstOrNull {
                        it.isNotBlank() && (it.contains("失败") || it.contains("尚未"))
                    }
                    ?: "云端记忆暂时无法读取，请稍后重试。",
                compact = compact,
                onRefresh = onRefresh,
            )
            previewItems.isEmpty() -> EmptyBody(compact, onOpenManager)
            else -> {
                MemoryScrollableEntries(
                    previewItems = previewItems,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                MemoryFooter(
                    itemCount = previewItems.size,
                    scrollable = previewItems.size > if (compact) 2 else 3,
                    memoryEnabled = memoryState.memoryEnabled,
                    compact = compact,
                    onOpenManager = onOpenManager,
                )
            }
        }
    }
}

@Composable
private fun MemoryScrollableEntries(
    previewItems: List<MemoryPreviewUiItem>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        items(
            items = previewItems,
            key = { item -> item.id },
        ) { item ->
            MemoryPreviewCard(item, compact)
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Modifier.size(if (compact) 15.dp else 17.dp),
                if (accountState.isLoggedIn) 1f else 0.35f,
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "记忆",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = if (compact) 15.sp else 17.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryNoFontPadding),
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = if (compact) 8.sp else 9.sp,
                lineHeight = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = MemoryNoFontPadding),
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
                statusText,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = if (compact) 8.sp else 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(platformStyle = MemoryNoFontPadding),
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                color = item.accent.copy(alpha = if (item.active) 0.94f else 0.58f),
                fontSize = if (compact) 9.5.sp else 10.5.sp,
                lineHeight = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(platformStyle = MemoryNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.content,
                color = Color.White.copy(alpha = if (item.active) 0.64f else 0.38f),
                fontSize = if (compact) 8.sp else 9.sp,
                lineHeight = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = MemoryNoFontPadding),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            item.status,
            color = Color.White.copy(alpha = if (item.active) 0.34f else 0.24f),
            fontSize = if (compact) 7.sp else 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = MemoryNoFontPadding),
            maxLines = 1,
        )
    }
}

@Composable
private fun ColumnScope.LoginBody(compact: Boolean, onLogin: () -> Unit) {
    MemoryMessageSurface(compact) {
        Text(
            "登录后解锁真实记忆",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "同步自定义指令、个人偏好和长期项目背景。",
            color = Color.White.copy(alpha = 0.44f),
            fontSize = if (compact) 8.5.sp else 9.5.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        MemoryActionButton("立即登录", Color(0xFF8DFFF4), onLogin)
    }
}

@Composable
private fun ColumnScope.EmptyBody(compact: Boolean, onOpenManager: () -> Unit) {
    MemoryMessageSurface(compact) {
        Text(
            "还没有保存记忆",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "在完整管理中添加偏好、个人信息或项目背景。",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = if (compact) 8.5.sp else 9.5.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        MemoryActionButton("去添加", Color(0xFFC6AEFF), onOpenManager)
    }
}

@Composable
private fun ColumnScope.ErrorBody(
    message: String,
    compact: Boolean,
    onRefresh: () -> Unit,
) {
    MemoryMessageSurface(compact, Color(0xFFFFC178)) {
        Text(
            "记忆同步暂时受限",
            color = Color(0xFFFFD69E).copy(alpha = 0.88f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            message,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = if (compact) 8.sp else 9.sp,
            lineHeight = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        MemoryActionButton("重新同步", Color(0xFFFFC178), onRefresh)
    }
}

@Composable
private fun ColumnScope.StatusBody(
    title: String,
    description: String,
    accent: Color,
    compact: Boolean,
) {
    MemoryMessageSurface(compact, accent) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            description,
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
private fun ColumnScope.MemoryMessageSurface(
    compact: Boolean,
    accent: Color = Color(0xFF8DFFF4),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(if (compact) 17.dp else 20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.055f),
                        accent.copy(alpha = 0.032f),
                        Color(0xFF9B73FF).copy(alpha = 0.026f),
                    ),
                ),
            )
            .border(
                0.7.dp,
                accent.copy(alpha = 0.095f),
                RoundedCornerShape(if (compact) 17.dp else 20.dp),
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun MemoryFooter(
    itemCount: Int,
    scrollable: Boolean,
    memoryEnabled: Boolean,
    compact: Boolean,
    onOpenManager: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                scrollable -> "共 $itemCount 项 · 上下滑动"
                memoryEnabled -> "长期记忆已开启"
                else -> "长期记忆当前关闭"
            },
            color = Color.White.copy(alpha = 0.30f),
            fontSize = if (compact) 7.5.sp else 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = MemoryNoFontPadding),
        )
        Spacer(Modifier.weight(1f))
        MemoryActionButton("完整管理", Color(0xFFC6AEFF), onOpenManager)
    }
}

@Composable
private fun MemoryActionButton(text: String, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(0.62f, Spring.StiffnessMedium),
        label = "memory-action-scale",
    )
    Text(
        text,
        color = Color.White.copy(alpha = 0.84f),
        fontSize = 8.5.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(platformStyle = MemoryNoFontPadding),
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun MemoryHairline() {
    Box(
        Modifier
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
            quadraticBezierTo(size.width * 0.14f, size.height * 0.22f, size.width * 0.30f, size.height * 0.22f)
            lineTo(size.width * 0.48f, size.height * 0.22f)
            lineTo(size.width * 0.59f, size.height * 0.34f)
            lineTo(size.width * 0.82f, size.height * 0.34f)
            quadraticBezierTo(size.width * 0.88f, size.height * 0.34f, size.width * 0.88f, size.height * 0.42f)
            lineTo(size.width * 0.88f, size.height * 0.47f)
            lineTo(size.width * 0.14f, size.height * 0.47f)
            close()
        }
        drawPath(
            tab,
            Brush.horizontalGradient(
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

private fun buildPreviewItems(
    memoryState: AssistantMemoryState,
    customState: AssistantCustomInstructionsState,
): List<MemoryPreviewUiItem> {
    val customItem = customState.content.trim().takeIf { it.isNotBlank() }?.let { content ->
        MemoryPreviewUiItem(
            id = "custom-instructions",
            title = "自定义指令",
            status = if (customState.enabled && customState.cloudReady) "生效中" else "已停用",
            content = content.lineSequence().firstOrNull().orEmpty().trim(),
            accent = Color(0xFFC6A5FF),
            active = customState.enabled && customState.cloudReady,
        )
    }
    val memories = memoryState.memories
        .sortedWith(
            compareByDescending<AssistantMemoryItem> { it.pinned }
                .thenByDescending { it.priority }
                .thenByDescending { it.updatedAt },
        )
        .map { it.toPreviewItem(memoryState.memoryEnabled) }
    return buildList {
        customItem?.let(::add)
        addAll(memories)
    }
}

private fun AssistantMemoryItem.toPreviewItem(memoryEnabled: Boolean): MemoryPreviewUiItem {
    val accent = when (category) {
        "profile" -> Color(0xFFFFD07F)
        "preference" -> Color(0xFF8DFFF4)
        "project" -> Color(0xFF8FB9FF)
        "rule" -> Color(0xFFC6A5FF)
        else -> Color(0xFFE3EBFF)
    }
    return MemoryPreviewUiItem(
        id = id,
        title = buildString {
            if (pinned) append("置顶 · ")
            append(memoryCategoryLabel(category))
        },
        status = if (!enabled) "已停用" else memoryPriorityLabel(priority),
        content = content.lineSequence().firstOrNull().orEmpty().trim(),
        accent = accent,
        active = enabled && memoryEnabled,
    )
}

private class UnifiedMemoryShape(
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
        val tailH = with(density) { tailHeight.toPx() }.coerceIn(0f, size.height * 0.22f)
        val halfTail = with(density) { tailHalfWidth.toPx() }.coerceAtMost(size.width * 0.16f)
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
