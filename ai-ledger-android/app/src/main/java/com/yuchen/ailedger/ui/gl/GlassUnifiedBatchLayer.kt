package com.yuchen.ailedger.ui.gl

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop

@Composable
internal fun UnifiedGlassBatchLayer(
    state: OpenGLShellBatchState,
    parentCoordinates: GlassCoordinateSource,
    modifier: Modifier = Modifier,
) {
    val items = state.snapshot()
    val backdrop = LocalBlurredBackdrop.current ?: return
    if (!backdrop.isReady || items.isEmpty()) return

    val baseBorder = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val styleOverride = LocalNewOpenGlGlassStyleOverride.current
    remember(baseBorder, styleOverride) { styleOverride?.invoke(baseBorder) ?: baseBorder }
    LocalBackdropOrigin.current
    LocalBackdropFrameTicker.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        with(density) { maxWidth.toPx() + maxHeight.toPx() }
        parentCoordinates.rootOffsetNow()
    }
}
