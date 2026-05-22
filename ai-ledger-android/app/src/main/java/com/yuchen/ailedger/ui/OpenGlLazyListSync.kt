package com.yuchen.ailedger.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize

class OpenGlLazyListAnchor {
    var rootOffset = Offset.Zero
    var size = IntSize.Zero
}

@Composable
fun rememberOpenGlLazyListAnchor(): OpenGlLazyListAnchor = remember { OpenGlLazyListAnchor() }

fun Modifier.openGlLazyListAnchor(anchor: OpenGlLazyListAnchor): Modifier = onPlaced { coordinates ->
    anchor.rootOffset = coordinates.localToRoot(Offset.Zero)
    anchor.size = coordinates.size
}

@Composable
fun SyncOpenGlGlassWithLazyList(
    listState: LazyListState,
    anchor: OpenGlLazyListAnchor,
    contentLeftPx: Float = 0f,
    contentRightPx: Float = 0f
) {
    val coordinator = LocalOpenGlGlassFrameCoordinator.current ?: return
    val previouslyVisible = remember { mutableSetOf<Any>() }
    val viewportWidth = anchor.size.width.toFloat().coerceAtLeast(1f)
    val listRoot = anchor.rootOffset
    val layoutInfo = listState.layoutInfo

    SideEffect {
        val currentVisible = linkedSetOf<Any>()
        layoutInfo.visibleItemsInfo.forEach { item ->
            val key = item.key
            currentVisible += key
            val left = listRoot.x + contentLeftPx
            val top = listRoot.y + item.offset
            val width = (viewportWidth - contentLeftPx - contentRightPx).coerceAtLeast(1f)
            val height = item.size.toFloat().coerceAtLeast(1f)
            coordinator.upsertFromLazyList(
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
        if (removed.isNotEmpty()) coordinator.releaseLazyListKeys(removed)
        previouslyVisible.clear()
        previouslyVisible += currentVisible
    }

    DisposableEffect(coordinator) {
        onDispose {
            coordinator.releaseLazyListKeys(previouslyVisible.toSet())
            previouslyVisible.clear()
        }
    }
}
