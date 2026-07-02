package com.yuchen.ailedger.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.yuchen.ailedger.model.AssistantUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
internal fun AccountLoginDialogHost(
    visible: Boolean,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val animationScope = rememberCoroutineScope()
    val backdropInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    var contentVisible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }

    val requestDismiss: () -> Unit = remember(animationScope) {
        {
            if (!dismissing) {
                dismissing = true
                contentVisible = false
                animationScope.launch {
                    delay(170)
                    latestOnDismiss()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.apply {
                setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
            }
        }

        LaunchedEffect(Unit) {
            yield()
            contentVisible = true
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = contentVisible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(135)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF07132D).copy(alpha = 0.08f),
                                    Color(0xFF07132D).copy(alpha = 0.18f),
                                    Color(0xFF03091F).copy(alpha = 0.44f),
                                )
                            )
                        )
                        .clickable(
                            interactionSource = backdropInteraction,
                            indication = null,
                            onClick = requestDismiss,
                        )
                )
            }

            AnimatedVisibility(
                visible = contentVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                enter = fadeIn(tween(165)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    ) { fullHeight -> fullHeight },
                exit = fadeOut(tween(115)) +
                    slideOutVertically(tween(165)) { fullHeight -> fullHeight },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = cardInteraction,
                            indication = null,
                            onClick = {},
                        )
                ) {
                    AccountLoginBottomCard(
                        state = state,
                        onDismiss = requestDismiss,
                    )
                }
            }
        }
    }
}
