package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Any
) {
    val clickAction = remember(onClick) { (onClick as? () -> Unit) ?: {} }

    SampleOpenGlDropletCapsule(
        active = state.onlineEnabled,
        modifier = modifier,
        title = "•",
        value = if (state.onlineEnabled) "已联网" else "联网已关闭",
        enabled = enabled,
        onClick = clickAction
    )
}
