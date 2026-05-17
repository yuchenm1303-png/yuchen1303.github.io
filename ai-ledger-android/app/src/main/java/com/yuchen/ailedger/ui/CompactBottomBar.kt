package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun CompactLiquidBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * 0.92f,
        motionIntensity = motionIntensity,
        radius = 28,
        modifier = modifier.fillMaxWidth().height(60.dp),
        role = GlassRole.Nav
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            val slot = maxWidth / AppTab.entries.size
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(
                targetValue = slot * target.toFloat(),
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "compact-nav-indicator-x"
            )
            val indicatorW by animateDpAsState(
                targetValue = slot - 8.dp,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "compact-nav-indicator-w"
            )

            GlassPanel(
                quality = quality,
                glassIntensity = glassIntensity * 1.08f,
                motionIntensity = motionIntensity,
                radius = 22,
                modifier = Modifier
                    .offset(x = indicatorX + 4.dp, y = 1.dp)
                    .width(indicatorW)
                    .height(48.dp),
                role = GlassRole.Floating
            ) {}

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "compact-tab-press")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(interactionSource = interaction, indication = null) { onTabChange(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            compactNavIcon(tab),
                            color = Color.White.copy(alpha = if (selected) 0.98f else 0.50f),
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            tab.title,
                            color = Color.White.copy(alpha = if (selected) 0.94f else 0.48f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun compactNavIcon(tab: AppTab): String = when (tab) {
    AppTab.Assistant -> "✦"
    AppTab.Tools -> "▦"
    AppTab.Settings -> "⚙"
}
