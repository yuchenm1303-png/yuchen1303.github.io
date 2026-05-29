package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab

val LocalPageActive = compositionLocalOf { true }

@Composable
fun CachedAppTabHost(
    currentTab: AppTab,
    modifier: Modifier = Modifier,
    content: @Composable (AppTab) -> Unit
) {
    var visitedTabs by remember { mutableStateOf(setOf(AppTab.Assistant)) }
    LaunchedEffect(currentTab) {
        if (currentTab !in visitedTabs) visitedTabs = visitedTabs + currentTab
    }

    Box(modifier) {
        AppTab.entries.forEach { tab ->
            if (tab in visitedTabs) {
                val active = tab == currentTab
                val density = LocalDensity.current
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(durationMillis = if (active) 180 else 90, easing = FastOutSlowInEasing),
                    label = "tabAlpha-${tab.name}"
                )
                val offsetDp by animateFloatAsState(
                    targetValue = if (active) 0f else 8f,
                    animationSpec = tween(durationMillis = if (active) 220 else 90, easing = FastOutSlowInEasing),
                    label = "tabOffset-${tab.name}"
                )

                CompositionLocalProvider(LocalPageActive provides active) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(if (active) 1f else 0f)
                            .graphicsLayer {
                                this.alpha = alpha
                                translationY = with(density) { offsetDp.toDp().toPx() }
                            }
                    ) {
                        content(tab)
                    }
                }
            }
        }
    }
}
