package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AgentHeaderSwitchPill(
    label: String,
    enabled: Boolean,
    activeColors: List<Color>,
    onClick: () -> Unit
) {
    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "$label-header-switch-active"
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (enabled) 10f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "$label-header-switch-knob"
    )

    Row(
        modifier = Modifier
            .height(22.dp)
            .background(
                Brush.horizontalGradient(
                    if (enabled) {
                        activeColors
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.075f),
                            Color.White.copy(alpha = 0.030f)
                        )
                    }
                ),
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f + active * 0.38f),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(23.dp)
                .height(13.dp)
                .background(
                    Color.Black.copy(alpha = 0.16f - active * 0.035f),
                    RoundedCornerShape(999.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (2f + knobOffset).dp)
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = 0.92f + active * 0.12f
                        scaleY = 0.92f + active * 0.12f
                    }
                    .background(
                        if (enabled) Color(0xFFF3FFFC) else Color(0xFF9EA8C5),
                        RoundedCornerShape(999.dp)
                    )
            )
        }
        Text(
            text = if (enabled) "开" else "关",
            color = Color.White.copy(alpha = 0.50f + active * 0.36f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
