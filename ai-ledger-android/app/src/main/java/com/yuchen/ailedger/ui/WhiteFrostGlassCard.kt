package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WhiteFrostGlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 28,
    onClick: (() -> Unit)? = null,
    frostAlpha: Float = 0.135f,
    borderAlpha: Float = 0.185f,
    content: @Composable BoxScope.() -> Unit,
) {
    val safeRadius = if (radius >= 999) 999.dp else radius.coerceAtLeast(1).dp
    val shape = RoundedCornerShape(safeRadius)
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(9.dp, shape, clip = false)
            .clip(shape)
            .then(clickModifier),
    ) {
        FrostInfoGlassPanel(
            radius = if (radius >= 999) 999f else radius.coerceAtLeast(1).toFloat(),
            backdropAlpha = 0.96f,
            frostAlpha = frostAlpha.coerceIn(0.06f, 0.32f),
            dimAlpha = 0f,
            modifier = Modifier.fillMaxSize(),
        ) {}
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.155f),
                            Color(0xFFEAF1F8).copy(alpha = 0.075f),
                            Color.White.copy(alpha = 0.048f),
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = borderAlpha.coerceIn(0.08f, 0.32f)), shape)
        )
        content()
    }
}
