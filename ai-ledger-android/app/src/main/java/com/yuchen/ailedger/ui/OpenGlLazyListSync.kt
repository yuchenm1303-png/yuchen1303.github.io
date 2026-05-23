package com.yuchen.ailedger.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

data class OpenGlLazyListVisibleItem(
    val key: Any,
    val offset: Int,
    val size: Int
)

data class OpenGlLazyListSnapshot(
    val rootOffset: Offset,
    val viewportWidth: Int,
    val visibleItems: List<OpenGlLazyListVisibleItem>
)

class OpenGlLazyListAnchor {
    var rootOffset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

@Composable
fun rememberOpenGlLazyListAnchor(): OpenGlLazyListAnchor = remember { OpenGlLazyListAnchor() }

fun Modifier.openGlLazyListAnchor(anchor: OpenGlLazyListAnchor): Modifier = onPlaced { coordinates ->
    val nextRoot = coordinates.localToRoot(Offset.Zero)
    val nextSize = coordinates.size
    if (anchor.rootOffset != nextRoot) anchor.rootOffset = nextRoot
    if (anchor.size != nextSize) anchor.size = nextSize
}

private fun OpenGlGlassFrameCoordinator.syncFromLazyListSnapshot(
    snapshot: OpenGlLazyListSnapshot,
    previouslyVisible: MutableSet<Any>,
    contentLeftPx: Float,
    contentRightPx: Float
) {
    val currentVisible = linkedSetOf<Any>()
    val viewportWidth = snapshot.viewportWidth.toFloat().coerceAtLeast(1f)
    snapshot.visibleItems.forEach { item ->
        val key = item.key
        currentVisible.add(key)
        val left = snapshot.rootOffset.x + contentLeftPx
        val top = snapshot.rootOffset.y + item.offset
        val width = (viewportWidth - contentLeftPx - contentRightPx).coerceAtLeast(1f)
        val height = item.size.toFloat().coerceAtLeast(1f)
        upsertFromLazyList(
            OpenGlGlassFrameRect(
                key = key,
                left = left,
                top = top,
                width = width,
                height = height,
                originX = left,
                originY = top
            )
        )
    }
    val removed = previouslyVisible.filterNot { it in currentVisible }.toSet()
    if (removed.isNotEmpty()) releaseLazyListKeys(removed)
    previouslyVisible.clear()
    previouslyVisible.addAll(currentVisible)
}

@Composable
fun SyncOpenGlGlassWithLazyList(
    listState: LazyListState,
    anchor: OpenGlLazyListAnchor,
    contentLeftPx: Float = 0f,
    contentRightPx: Float = 0f
) {
    val coordinator = LocalOpenGlGlassFrameCoordinator.current ?: return
    val ticker = LocalBackdropFrameTicker.current
    val previouslyVisible = remember { mutableSetOf<Any>() }

    fun currentSnapshot(): OpenGlLazyListSnapshot {
        return OpenGlLazyListSnapshot(
            rootOffset = anchor.rootOffset,
            viewportWidth = anchor.size.width.coerceAtLeast(1),
            visibleItems = listState.layoutInfo.visibleItemsInfo.map { item ->
                OpenGlLazyListVisibleItem(
                    key = item.key,
                    offset = item.offset,
                    size = item.size
                )
            }
        )
    }

    SideEffect {
        coordinator.syncFromLazyListSnapshot(
            snapshot = currentSnapshot(),
            previouslyVisible = previouslyVisible,
            contentLeftPx = contentLeftPx,
            contentRightPx = contentRightPx
        )
    }

    LaunchedEffect(listState, anchor, coordinator, contentLeftPx, contentRightPx) {
        snapshotFlow { currentSnapshot() }
            .collectLatest { snapshot ->
                coordinator.syncFromLazyListSnapshot(
                    snapshot = snapshot,
                    previouslyVisible = previouslyVisible,
                    contentLeftPx = contentLeftPx,
                    contentRightPx = contentRightPx
                )
            }
    }

    LaunchedEffect(listState, ticker) {
        if (ticker == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    while (currentCoroutineContext().isActive) {
                        ticker.frameNanos = withFrameNanos { it }
                    }
                } else {
                    repeat(14) {
                        ticker.frameNanos = withFrameNanos { it }
                    }
                }
            }
    }

    LaunchedEffect(listState, anchor, ticker) {
        if (ticker == null) return@LaunchedEffect
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset to Triple(
                first?.key,
                first?.offset,
                last?.offset
            ) to anchor.rootOffset
        }.collectLatest {
            ticker.frameNanos = withFrameNanos { it }
        }
    }

    DisposableEffect(coordinator) {
        onDispose {
            coordinator.releaseLazyListKeys(previouslyVisible.toSet())
            previouslyVisible.clear()
        }
    }
}