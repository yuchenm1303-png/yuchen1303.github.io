package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun AgentInfinityCapsuleBody(
    enabled: Boolean,
    state: AgentInfinityWebState,
    motion: AgentInfinityCapsuleMotion,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(22.dp)
            .graphicsLayer {
                scaleX = motion.scaleX
                scaleY = motion.scaleY
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AgentInfinityCapsuleBackground(
            enabled = enabled,
            active = motion.active,
            modifier = Modifier.matchParentSize()
        )
        AgentInfinityCapsuleContent(
            enabled = enabled,
            state = state,
            active = motion.active,
            modifier = Modifier.matchParentSize()
        )
        AgentInfinityCapsuleSweep(
            enabled = enabled,
            sweep = motion.sweep,
            modifier = Modifier.matchParentSize()
        )
    }
}
