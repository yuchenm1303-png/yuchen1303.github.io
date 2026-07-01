package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PlanContainerOpenEasing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
private val PlanContainerCloseEasing = CubicBezierEasing(0.40f, 0.00f, 1.00f, 1.00f)

private data class PlanLaunchAnchor(
    val boundsInRoot: Rect,
    val radiusDp: Float,
)

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val planState = viewModel.uiState
    val animationScope = rememberCoroutineScope()
    val containerProgress = remember { Animatable(0f) }
    val contentProgress = remember { Animatable(0f) }
    var modalAnimationJob by remember { mutableStateOf<Job?>(null) }
    var quickTitle by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editorDraft by remember { mutableStateOf<PlanDraft?>(null) }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<PlanTask?>(null) }
    var launchAnchor by remember { mutableStateOf<PlanLaunchAnchor?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(Size.Zero) }
    var composerBounds by remember { mutableStateOf(Rect.Zero) }
    var templateGridBounds by remember { mutableStateOf(Rect.Zero) }
    var emptyCardBounds by remember { mutableStateOf(Rect.Zero) }

    val modalMounted = editorDraft != null || deleteCandidate != null
    val motionEnabled = state.motionIntensity > 0.05f

    fun startModalAnimation() {
        modalAnimationJob?.cancel()
        modalAnimationJob = animationScope.launch {
            containerProgress.stop()
            contentProgress.stop()
            containerProgress.snapTo(0f)
            contentProgress.snapTo(0f)
            if (!motionEnabled) {
                containerProgress.snapTo(1f)
                contentProgress.snapTo(1f)
                return@launch
            }
            coroutineScope {
                launch {
                    containerProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 430,
                            easing = PlanContainerOpenEasing,
                        ),
                    )
                }
                launch {
                    delay(175)
                    contentProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 215,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        }
    }

    fun openEditor(
        anchor: PlanLaunchAnchor?,
        task: PlanTask? = null,
        template: PlanDraft? = null,
    ) {
        launchAnchor = anchor
        editingId = task?.id
        editorDraft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle)
        deleteCandidate = null
        quickTitle = ""
        editorGeneration += 1
        startModalAnimation()
    }

    fun openDelete(anchor: PlanLaunchAnchor?, task: PlanTask) {
        launchAnchor = anchor
        editingId = null
        editorDraft = null
        deleteCandidate = task
        startModalAnimation()
    }

    fun closeModal() {
        if (!modalMounted) return
        modalAnimationJob?.cancel()
        modalAnimationJob = animationScope.launch {
            containerProgress.stop()
            contentProgress.stop()
            if (motionEnabled) {
                contentProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 100,
                        easing = FastOutSlowInEasing,
                    ),
                )
                containerProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 285,
                        easing = PlanContainerCloseEasing,
                    ),
                )
            } else {
                contentProgress.snapTo(0f)
                containerProgress.snapTo(0f)
            }
            editorDraft = null
            editingId = null
            deleteCandidate = null
            launchAnchor = null
        }
    }

    BackHandler(enabled = modalMounted, onBack = ::closeModal)
    BackHandler(enabled = !modalMounted, onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(modalMounted) { onModalVisibilityChange(modalMounted) }
    DisposableEffect(Unit) {
        onDispose {
            modalAnimationJob?.cancel()
            onModalVisibilityChange(false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootOrigin = coordinates.positionInRoot()
                rootSize = Size(
                    width = coordinates.size.width.toFloat(),
                    height = coordinates.size.height.toFloat(),
                )
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PlanHeader(
                    state = state,
                    activeCount = planState.activeCount,
                    onBack = onBack,
                )
            }
            item {
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        composerBounds = it.boundsInRoot()
                    },
                ) {
                    PlanQuickComposer(
                        state = state,
                        value = quickTitle,
                        onValueChange = { quickTitle = it.take(80) },
                        onCreate = {
                            openEditor(
                                anchor = PlanLaunchAnchor(
                                    boundsInRoot = composerBounds,
                                    radiusDp = 27f,
                                ),
                            )
                        },
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        templateGridBounds = it.boundsInRoot()
                    },
                ) {
                    PlanTemplateGrid(state = state) { template ->
                        openEditor(
                            anchor = templateLaunchAnchor(
                                gridBounds = templateGridBounds,
                                draft = template,
                                gapPx = with(density) { 9.dp.toPx() },
                                labelHeightPx = with(density) { 22.dp.toPx() },
                                cellHeightPx = with(density) { 72.dp.toPx() },
                            ),
                            template = template,
                        )
                    }
                }
            }
            if (!planState.exactAlarmReady) {
                item {
                    PlanInfoBanner(state = state) {
                        if (!viewModel.requestExactAlarmAccess()) {
                            Toast.makeText(
                                context,
                                "当前系统没有可用的精确闹钟设置页面",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            item {
                PlanFilterBar(
                    state = state,
                    selected = planState.filter,
                    onSelect = viewModel::setFilter,
                )
            }
            item { PlanSectionTitle(planState.filter, planState.visibleTasks.size) }

            if (planState.visibleTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.onGloballyPositioned {
                            emptyCardBounds = it.boundsInRoot()
                        },
                    ) {
                        PlanEmptyCard(
                            state = state,
                            filtered = planState.tasks.isNotEmpty(),
                            onCreate = {
                                openEditor(
                                    anchor = emptyButtonAnchor(
                                        cardBounds = emptyCardBounds,
                                        buttonWidthPx = with(density) { 154.dp.toPx() },
                                        buttonHeightPx = with(density) { 40.dp.toPx() },
                                        bottomInsetPx = with(density) { 18.dp.toPx() },
                                    ),
                                )
                            },
                        )
                    }
                }
            } else {
                items(planState.visibleTasks, key = { it.id }) { task ->
                    var taskBounds by remember(task.id) { mutableStateOf(Rect.Zero) }
                    Box(
                        modifier = Modifier.onGloballyPositioned {
                            taskBounds = it.boundsInRoot()
                        },
                    ) {
                        PlanTaskCard(
                            state = state,
                            task = task,
                            onEdit = {
                                openEditor(
                                    anchor = PlanLaunchAnchor(taskBounds, 25f),
                                    task = task,
                                )
                            },
                            onDelete = {
                                openDelete(
                                    anchor = PlanLaunchAnchor(taskBounds, 25f),
                                    task = task,
                                )
                            },
                            onToggle = { enabled ->
                                val result = viewModel.toggleTask(task.id, enabled)
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }

        if (modalMounted) {
            val backdropBlocker = remember { MutableInteractionSource() }
            val panelBlocker = remember { MutableInteractionSource() }
            val rootWidthPx = rootSize.width.takeIf { it > 1f }
                ?: with(density) { maxWidth.toPx() }
            val rootHeightPx = rootSize.height.takeIf { it > 1f }
                ?: with(density) { maxHeight.toPx() }
            val fallbackSource = fallbackLaunchRect(
                rootWidthPx = rootWidthPx,
                topPx = with(density) { 92.dp.toPx() },
                heightPx = with(density) { 64.dp.toPx() },
            )
            val sourceRect = relativeSourceRect(
                anchor = launchAnchor,
                rootOrigin = rootOrigin,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                fallback = fallbackSource,
            )
            val targetRect = if (deleteCandidate != null) {
                deleteTargetRect(
                    rootWidthPx = rootWidthPx,
                    rootHeightPx = rootHeightPx,
                    horizontalInsetPx = with(density) { 32.dp.toPx() },
                    heightPx = with(density) { 198.dp.toPx() },
                    verticalLiftPx = with(density) { 24.dp.toPx() },
                )
            } else {
                editorTargetRect(
                    rootWidthPx = rootWidthPx,
                    rootHeightPx = rootHeightPx,
                    horizontalInsetPx = with(density) { 14.dp.toPx() },
                    topInsetPx = with(density) { 58.dp.toPx() },
                    bottomInsetPx = with(density) { 118.dp.toPx() },
                )
            }
            val geometryProgress = containerProgress.value.coerceIn(0f, 1f)
            val currentRect = lerpRect(sourceRect, targetRect, geometryProgress)
            val targetRadiusDp = if (deleteCandidate != null) 28f else 30f
            val currentRadiusDp = lerpFloat(
                launchAnchor?.radiusDp ?: 27f,
                targetRadiusDp,
                geometryProgress,
            ).coerceAtLeast(18f)

            Box(
                modifier = Modifier
                    .zIndex(100f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backdropBlocker,
                        indication = null,
                        onClick = ::closeModal,
                    ),
            )

            Box(
                modifier = Modifier
                    .zIndex(101f)
                    .offset {
                        IntOffset(
                            x = currentRect.left.roundToInt(),
                            y = currentRect.top.roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { currentRect.width.toDp() },
                        height = with(density) { currentRect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(currentRadiusDp.dp))
                    .clickable(
                        interactionSource = panelBlocker,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                editorDraft?.let { initial ->
                    key(editorGeneration) {
                        PlanEditorPanel(
                            state = state,
                            initial = initial,
                            editing = editingId != null,
                            exactAlarmReady = planState.exactAlarmReady,
                            panelRadius = currentRadiusDp.roundToInt(),
                            containerProgress = containerProgress.value,
                            contentProgress = contentProgress.value,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = ::closeModal,
                            onSave = { draft ->
                                val result = viewModel.saveTask(editingId, draft)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                if (result.ok) closeModal()
                            },
                        )
                    }
                }

                deleteCandidate?.let { task ->
                    PlanDeletePanel(
                        state = state,
                        task = task,
                        panelRadius = currentRadiusDp.roundToInt(),
                        containerProgress = containerProgress.value,
                        contentProgress = contentProgress.value,
                        modifier = Modifier.fillMaxSize(),
                        onDismiss = ::closeModal,
                        onConfirm = {
                            val result = viewModel.deleteTask(task.id)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            closeModal()
                        },
                    )
                }
            }
        }
    }
}

private fun templateLaunchAnchor(
    gridBounds: Rect,
    draft: PlanDraft,
    gapPx: Float,
    labelHeightPx: Float,
    cellHeightPx: Float,
): PlanLaunchAnchor? {
    if (!gridBounds.isUsable()) return null
    val index = when (draft.title) {
        "起床" -> 0
        "复习今天的内容" -> 1
        "准备上课" -> 2
        "整理今日事项" -> 3
        else -> 0
    }
    val column = index % 2
    val row = index / 2
    val cellWidth = ((gridBounds.width - gapPx) / 2f).coerceAtLeast(1f)
    val left = gridBounds.left + column * (cellWidth + gapPx)
    val top = gridBounds.top + labelHeightPx + row * (cellHeightPx + gapPx)
    return PlanLaunchAnchor(
        boundsInRoot = Rect(
            left = left,
            top = top,
            right = left + cellWidth,
            bottom = top + cellHeightPx,
        ),
        radiusDp = 21f,
    )
}

private fun emptyButtonAnchor(
    cardBounds: Rect,
    buttonWidthPx: Float,
    buttonHeightPx: Float,
    bottomInsetPx: Float,
): PlanLaunchAnchor? {
    if (!cardBounds.isUsable()) return null
    val left = cardBounds.center.x - buttonWidthPx / 2f
    val top = cardBounds.bottom - bottomInsetPx - buttonHeightPx
    return PlanLaunchAnchor(
        boundsInRoot = Rect(
            left = left,
            top = top,
            right = left + buttonWidthPx,
            bottom = top + buttonHeightPx,
        ),
        radiusDp = 999f,
    )
}

private fun relativeSourceRect(
    anchor: PlanLaunchAnchor?,
    rootOrigin: Offset,
    rootWidthPx: Float,
    rootHeightPx: Float,
    fallback: Rect,
): Rect {
    val raw = anchor?.boundsInRoot ?: return fallback
    if (!raw.isUsable()) return fallback
    val relative = Rect(
        left = raw.left - rootOrigin.x,
        top = raw.top - rootOrigin.y,
        right = raw.right - rootOrigin.x,
        bottom = raw.bottom - rootOrigin.y,
    )
    if (
        relative.right < 0f ||
        relative.bottom < 0f ||
        relative.left > rootWidthPx ||
        relative.top > rootHeightPx
    ) {
        return fallback
    }
    val width = relative.width.coerceAtLeast(1f).coerceAtMost(rootWidthPx)
    val height = relative.height.coerceAtLeast(1f).coerceAtMost(rootHeightPx)
    val left = relative.left.coerceIn(0f, (rootWidthPx - width).coerceAtLeast(0f))
    val top = relative.top.coerceIn(0f, (rootHeightPx - height).coerceAtLeast(0f))
    return Rect(left, top, left + width, top + height)
}

private fun fallbackLaunchRect(
    rootWidthPx: Float,
    topPx: Float,
    heightPx: Float,
): Rect {
    val width = rootWidthPx * 0.72f
    val left = (rootWidthPx - width) / 2f
    return Rect(left, topPx, left + width, topPx + heightPx)
}

private fun editorTargetRect(
    rootWidthPx: Float,
    rootHeightPx: Float,
    horizontalInsetPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
): Rect {
    val left = horizontalInsetPx.coerceAtLeast(0f)
    val right = (rootWidthPx - horizontalInsetPx).coerceAtLeast(left + 1f)
    val top = topInsetPx.coerceIn(0f, (rootHeightPx - 1f).coerceAtLeast(0f))
    val bottom = (rootHeightPx - bottomInsetPx).coerceAtLeast(top + 1f)
    return Rect(left, top, right, bottom)
}

private fun deleteTargetRect(
    rootWidthPx: Float,
    rootHeightPx: Float,
    horizontalInsetPx: Float,
    heightPx: Float,
    verticalLiftPx: Float,
): Rect {
    val left = horizontalInsetPx
    val right = (rootWidthPx - horizontalInsetPx).coerceAtLeast(left + 1f)
    val top = ((rootHeightPx - heightPx) / 2f - verticalLiftPx)
        .coerceIn(0f, (rootHeightPx - heightPx).coerceAtLeast(0f))
    return Rect(left, top, right, top + heightPx)
}

private fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect {
    val t = fraction.coerceIn(0f, 1f)
    return Rect(
        left = lerpFloat(start.left, end.left, t),
        top = lerpFloat(start.top, end.top, t),
        right = lerpFloat(start.right, end.right, t),
        bottom = lerpFloat(start.bottom, end.bottom, t),
    )
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun Rect.isUsable(): Boolean {
    return width > 1f && height > 1f &&
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
}
