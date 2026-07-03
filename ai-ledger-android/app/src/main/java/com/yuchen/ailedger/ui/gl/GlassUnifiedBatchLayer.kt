package com.yuchen.ailedger.ui.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.ui.GlassCoordinateSource

@Composable
internal fun UnifiedGlassBatchLayer(
    state: OpenGLShellBatchState,
    parentCoordinates: GlassCoordinateSource,
    modifier: Modifier = Modifier,
) {
    state.snapshot()
    parentCoordinates.rootOffsetNow()
}
