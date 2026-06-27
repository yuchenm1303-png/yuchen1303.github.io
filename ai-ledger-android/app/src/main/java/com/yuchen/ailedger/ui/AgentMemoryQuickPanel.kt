package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantCustomInstructionsState
import com.yuchen.ailedger.data.AssistantMemoryItem
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryState
import com.yuchen.ailedger.data.memoryCategoryLabel
import com.yuchen.ailedger.data.memoryPriorityLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MEMORY_QUICK_PREVIEW_LIMIT = 5

private data class MemoryQuickPreview(
    val key: String,
    val eyebrow: String,
    val content: String,
    val accent: Color,
    val trailing: String
)

@Composable
internal fun AgentMemoryQuickPanelButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val customRepository = remember(context) { AssistantCustomInstructionsRepository.get(context) }
    val memoryState by memoryRepository.state.collectAsState()
    val customState by customRepository.state.collectAsState()

    val storedCount = memoryState.memories.size + if (customState.content.isNotBlank()) 1 else 0
    val effectiveCount = memoryState.activeCount + if (customState.effectiveText() != null) 1 else 0
    val active = effectiveCount > 0
    val loading = memoryState.loading || customState.loading

    var popupMounted by remember { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val popupPositionProvider = remember(density) {
        MemoryPopupPositionProvider(
            gapPx = with(density) { 8.dp.roundToPx() },
            edgeMarginPx = with(density) { 12.dp.roundToPx() }
        )
    }

    fun dismissPanel() {
        panelVisible = false
        scope.launch {
            delay(170)
            popupMounted = false
        }
    }

    LaunchedEffect(popupMounted) {
        if (popupMounted) {
            withFrameNanos { }
            panelVisible = true
        }
    }

    Box(modifier = modifier) {
        MemoryFolderCapsule(
            storedCount = storedCount,
            active = active,
            loading = loading,
            expanded = popupMounted,
            onClick = {
                if (popupMounted) dismissPanel() else popupMounted = true
            }
        )

        if (popupMounted) {
            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = ::dismissPanel,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                AnimatedVisibility(
                    visible = panelVisible,
                    enter = fadeIn(tween(150)) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.76f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { -10 } +
                        scaleIn(
                            initialScale = 0.92f,
                            transformOrigin = TransformOrigin(0.88f, 0f),
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    exit = fadeOut(tween(120)) +
                        slideOutVertically(tween(145, easing = FastOutSlowInEasing)) { -7 } +
                        scaleOut(
                            targetScale = 0.965f,
                            transformOrigin = TransformOrigin(0.88f, 0f),
                            animationSpec = tween(145, easing = FastOutSlowInEasing)
                        )
                ) {
                    MemoryQuickPanel(
                        memoryState = memoryState,
                        customState = customState,
                        panelVisible = panelVisible,
                        onRefresh = {
                            memoryRepository.refresh()
                            customRepository.refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFolderCapsule(
    storedCount: Int,
    active: Boolean,
    loading: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "memory-capsule-breath")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "memory-capsule-breath-value"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val glow = when {
        expanded -> 1f
        active -> 0.46f + breath * 0.34f
        else -> 0f
    }
    val width = if (storedCount > 0) 42.dp else 32.dp

    Box(
        modifier = Modifier
            .width(width)
            .height(22.dp)
            .graphicsLayer {
                scaleX = if (expanded) 1.045f else 1f
                scaleY = if (expanded) 1.045f else 1f
            }
            .semantics {
                contentDescription = when {
                    loading -> "记忆正在同步"
                    active -> "记忆，当前生效 $storedCount 项"
                    storedCount > 0 -> "记忆，已保存 $storedCount 项，当前暂停"
                    else -> "记忆，暂无内容"
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.height / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF18284B).copy(alpha = 0.72f + glow * 0.10f),
                        Color(0xFF36245C).copy(alpha = 0.54f + glow * 0.14f),
                        Color(0xFF132B4B).copy(alpha = 0.66f + glow * 0.10f)
                    )
                ),
                cornerRadius = CornerRadius(radius)
            )
            if (glow > 0.001f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF8DFFF4).copy(alpha = 0.16f * glow),
                            Color(0xFFB78BFF).copy(alpha = 0.30f * glow),
                            Color(0xFFFF8FCB).copy(alpha = 0.14f * glow)
                        )
                    ),
                    cornerRadius = CornerRadius(radius)
                )
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.11f + glow * 0.11f),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = 0.7.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            MemoryFolderIcon(
                active = active || expanded,
                glow = glow,
                modifier = Modifier.size(15.dp)
            )
            if (storedCount > 0) {
                Text(
                    text = if (storedCount > 99) "99+" else storedCount.toString(),
                    color = Color.White.copy(alpha = if (active) 0.90f else 0.56f),
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MemoryFolderIcon(
    active: Boolean,
    glow: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val bodyTop = size.height * 0.31f
        val bodyLeft = size.width * 0.08f
        val bodyWidth = size.width * 0.84f
        val bodyHeight = size.height * 0.58f
        val color = if (active) Color(0xFFB7FFF7) else Color.White.copy(alpha = 0.54f)

        if (glow > 0.001f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9B73FF).copy(alpha = 0.30f * glow),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * 0.72f,
                center = center
            )
        }
        drawRoundRect(
            color = color.copy(alpha = 0.48f),
            topLeft = Offset(size.width * 0.15f, size.height * 0.16f),
            size = Size(size.width * 0.38f, size.height * 0.28f),
            cornerRadius = CornerRadius(size.height * 0.10f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    color.copy(alpha = 0.95f),
                    Color(0xFF9B73FF).copy(alpha = if (active) 0.78f else 0.34f)
                ),
                start = Offset(bodyLeft, bodyTop),
                end = Offset(bodyLeft + bodyWidth, bodyTop + bodyHeight)
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(size.height * 0.14f)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.52f),
            start = Offset(size.width * 0.25f, size.height * 0.55f),
            end = Offset(size.width * 0.75f, size.height * 0.55f),
            strokeWidth = 0.75.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MemoryQuickPanel(
    memoryState: AssistantMemoryState,
    customState: AssistantCustomInstructionsState,
    panelVisible: Boolean,
    onRefresh: () -> Unit
) {
    val previews = remember(memoryState, customState) {
        buildMemoryQuickPreviews(memoryState, customState)
    }
    val storedCount = memoryState.memories.size + if (customState.content.isNotBlank()) 1 else 0
    val effectiveCount = memoryState.activeCount + if (customState.effectiveText() != null) 1 else 0
    val loggedIn = memoryState.accountUserId != null || customState.accountUserId != null
    val loading = memoryState.loading || customState.loading
    val hasCloudError = (memoryState.error && !memoryState.cloudReady) ||
        (customState.error && !customState.cloudReady)

    Box(
        modifier = Modifier
            .width(316.dp)
            .padding(top = 8.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-19).dp, y = (-3).dp)
                .size(17.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(Color(0xEE17213C), RoundedCornerShape(4.dp))
                .border(0.7.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(4.dp))
        )

        val panelShape = RoundedCornerShape(27.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, panelShape, clip = false)
                .clip(panelShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xF21B2742),
                            Color(0xF522203A),
                            Color(0xF316243D)
                        ),
                        start = Offset.Zero,
                        end = Offset(900f, 760f)
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), panelShape)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8DFFF4).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.05f),
                        radius = size.width * 0.58f
                    ),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.16f, size.height * 0.05f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9B73FF).copy(alpha = 0.14f),
                            Color(0xFFFF8FCB).copy(alpha = 0.045f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.92f),
                        radius = size.width * 0.52f
                    ),
                    radius = size.width * 0.52f,
                    center = Offset(size.width * 0.88f, size.height * 0.92f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.055f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.018f)
                        )
                    ),
                    cornerRadius = CornerRadius(27.dp.toPx())
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(39.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MemoryFolderIcon(
                            active = effectiveCount > 0,
                            glow = if (effectiveCount > 0) 0.8f else 0f,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "记忆",
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 17.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            when {
                                !loggedIn -> "登录后查看账号记忆"
                                loading -> "正在同步个性化数据"
                                effectiveCount > 0 -> "本轮已注入 $effectiveCount 项 · 已保存 $storedCount 项"
                                storedCount > 0 -> "已保存 $storedCount 项 · 当前暂停"
                                else -> "暂时没有已保存内容"
                            },
                            color = Color.White.copy(alpha = 0.46f),
                            fontSize = 9.8.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MemoryPanelStatusPill(
                        text = when {
                            loading -> "同步中"
                            !loggedIn -> "未登录"
                            hasCloudError -> "未就绪"
                            effectiveCount > 0 -> "生效"
                            storedCount > 0 -> "暂停"
                            else -> "空"
                        },
                        active = effectiveCount > 0
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.13f),
                                    Color(0xFF9B73FF).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                when {
                    loading -> MemoryPanelEmptyState("正在读取长期记忆与自定义指令…")
                    !loggedIn -> MemoryPanelEmptyState("请先在设置页登录账号，记忆会按账号独立同步。")
                    hasCloudError && previews.isEmpty() -> MemoryPanelEmptyState("个性化云端数据尚未就绪，可在设置 → 记忆中重新检查。")
                    previews.isEmpty() -> MemoryPanelEmptyState(
                        if (storedCount > 0) {
                            "记忆已经保存，但当前总开关关闭或条目已停用。"
                        } else {
                            "还没有可展示的记忆，可以在设置 → 记忆中添加。"
                        }
                    )
                    else -> previews.forEachIndexed { index, preview ->
                        MemoryPreviewRow(
                            preview = preview,
                            index = index,
                            panelVisible = panelVisible,
                            showConnector = index < previews.lastIndex
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (previews.size < effectiveCount) {
                            "还有 ${effectiveCount - previews.size} 项未展开"
                        } else {
                            "完整编辑：设置 → 记忆"
                        },
                        color = Color.White.copy(alpha = 0.34f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "刷新",
                        color = Color(0xFF9EFFF4).copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onRefresh)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryPanelStatusPill(text: String, active: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF66E8DB).copy(alpha = 0.24f),
                            Color(0xFF9B73FF).copy(alpha = 0.22f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.White.copy(alpha = 0.035f)
                        )
                    )
                }
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.88f else 0.48f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun MemoryPreviewRow(
    preview: MemoryQuickPreview,
    index: Int,
    panelVisible: Boolean,
    showConnector: Boolean
) {
    var visible by remember(preview.key) { mutableStateOf(false) }
    LaunchedEffect(panelVisible, preview.key) {
        visible = false
        if (panelVisible) {
            delay(65L + index * 42L)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(170)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { 8 },
        exit = fadeOut(tween(90))
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Column(
                modifier = Modifier.width(13.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(preview.accent)
                        .shadow(5.dp, RoundedCornerShape(999.dp), clip = false)
                )
                if (showConnector) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(39.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        preview.accent.copy(alpha = 0.38f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.052f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview.eyebrow,
                        color = preview.accent.copy(alpha = 0.84f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        preview.trailing,
                        color = Color.White.copy(alpha = 0.34f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    preview.content,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemoryPanelEmptyState(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 13.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Color.White.copy(alpha = 0.43f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun buildMemoryQuickPreviews(
    memoryState: AssistantMemoryState,
    customState: AssistantCustomInstructionsState
): List<MemoryQuickPreview> {
    val result = mutableListOf<MemoryQuickPreview>()
    customState.effectiveText()?.let { instructions ->
        result += MemoryQuickPreview(
            key = "custom-instructions",
            eyebrow = "自定义指令",
            content = instructions.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            accent = Color(0xFFB995FF),
            trailing = "高优先级"
        )
    }
    if (memoryState.memoryEnabled && memoryState.cloudReady) {
        memoryState.memories
            .asSequence()
            .filter { it.enabled && it.content.isNotBlank() }
            .distinctBy { it.content.trim() }
            .sortedWith(
                compareByDescending<AssistantMemoryItem> { it.pinned }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.updatedAt }
            )
            .take((MEMORY_QUICK_PREVIEW_LIMIT - result.size).coerceAtLeast(0))
            .forEach { item ->
                result += MemoryQuickPreview(
                    key = item.id,
                    eyebrow = buildString {
                        if (item.pinned) append("置顶 · ")
                        append(memoryCategoryLabel(item.category))
                    },
                    content = item.content,
                    accent = memoryAccent(item),
                    trailing = memoryPriorityLabel(item.priority)
                )
            }
    }
    return result.take(MEMORY_QUICK_PREVIEW_LIMIT)
}

private fun memoryAccent(item: AssistantMemoryItem): Color = when {
    item.pinned -> Color(0xFFFFC978)
    item.priority >= 3 -> Color(0xFFFF9FC9)
    item.priority == 2 -> Color(0xFFB995FF)
    item.category == "profile" -> Color(0xFF8DFFF4)
    item.category == "project" -> Color(0xFF76C9FF)
    else -> Color(0xFFAFC4FF)
}

private class MemoryPopupPositionProvider(
    private val gapPx: Int,
    private val edgeMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val preferredX = anchorBounds.right - popupContentSize.width
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx)
            .coerceAtLeast(edgeMarginPx)
        val x = preferredX.coerceIn(edgeMarginPx, maxX)
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (below + popupContentSize.height <= windowSize.height - edgeMarginPx) {
            below
        } else {
            above.coerceAtLeast(edgeMarginPx)
        }
        return IntOffset(x, y)
    }
}
