package com.yuchen.ailedger.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * Keeps card-bound OpenGL glass sampling in phase with Compose LazyColumn scrolling.
 *
 * TextureView/OpenGL renders on a separate Surface from Compose. During fast scrolls,
 * the Compose item position can advance before the OpenGL card has re-sampled its
 * backdrop origin. Pumping the shared backdrop ticker every display frame while the
 * list is scrolling forces AndroidView.update to push fresh originX/originY to GL.
 */
@Composable
fun PumpOpenGlGlassWhileScrolling(listState: LazyListState) {
    val ticker = LocalBackdropFrameTicker.current ?: return

    LaunchedEffect(listState, ticker) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                while (scrolling && currentCoroutineContext().isActive) {
                    ticker.frameNanos = withFrameNanos { it }
                }
            }
    }
}
