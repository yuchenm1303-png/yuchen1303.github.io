package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AssistantUiState

private val AccountLoginCardHeight = 342.dp

@Composable
internal fun AccountLoginDialogHost(
    visible: Boolean,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    val backdropInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5000f),
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF07132D).copy(alpha = 0.03f),
                                Color(0xFF07132D).copy(alpha = 0.10f),
                                Color(0xFF02081C).copy(alpha = 0.24f),
                            )
                        )
                    )
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 76.dp)
                .zIndex(1f),
            enter = fadeIn(tween(155)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
            exit = fadeOut(tween(105)) +
                slideOutVertically(
                    animationSpec = tween(155),
                    targetOffsetY = { fullHeight -> fullHeight },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AccountLoginCardHeight)
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {},
                    ),
                propagateMinConstraints = true,
            ) {
                AccountLoginBottomCard(
                    state = state,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
