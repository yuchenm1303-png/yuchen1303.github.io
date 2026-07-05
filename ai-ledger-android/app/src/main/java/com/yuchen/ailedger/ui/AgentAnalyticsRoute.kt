package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.model.AssistantUiState
import kotlinx.coroutines.yield

@Composable
internal fun AgentAnalyticsRoute(
    appState: AssistantUiState,
    onBack: () -> Unit,
) {
    val analyticsViewModel: AgentAnalyticsViewModel = viewModel()
    DisposableEffect(analyticsViewModel) {
        analyticsViewModel.onScreenVisible()
        onDispose { analyticsViewModel.onScreenHidden() }
    }

    var visible by remember { mutableStateOf(appState.motionIntensity <= 0.05f) }
    LaunchedEffect(Unit) {
        if (!visible) {
            yield()
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (appState.motionIntensity <= 0.05f) {
            fadeIn(tween(70))
        } else {
            fadeIn(tween(132, delayMillis = 18)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it.coerceAtMost(44) } +
                scaleIn(
                    initialScale = 0.968f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
        },
        exit = fadeOut(tween(96)) +
            slideOutVertically(tween(118)) { -it.coerceAtMost(18) } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(118)),
    ) {
        AgentAnalyticsProfileWideScreen(
            viewModel = analyticsViewModel,
            onBack = onBack,
        )
    }
}
