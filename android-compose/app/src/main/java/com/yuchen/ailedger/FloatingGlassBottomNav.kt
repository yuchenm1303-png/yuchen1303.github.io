package com.yuchen.ailedger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab

@Composable
fun FloatingGlassBottomNav(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    val islandShape = RoundedCornerShape(34.dp)
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(104.dp)
            .shadow(34.dp, islandShape, clip = false)
            .clip(islandShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color(0xFFB7C8FF).copy(alpha = 0.12f),
                        Color(0xFF7A5EA8).copy(alpha = 0.15f),
                        Color(0xFF12172D).copy(alpha = 0.18f)
                    ),
                    start = Offset.Zero,
                    end = Offset(0f, 340f)
                )
            )
            .border(1.2.dp, Color.White.copy(alpha = 0.34f), islandShape)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(92f, 18f),
                        radius = 260f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                FloatingGlassTab(
                    modifier = Modifier.weight(1f),
                    tab = tab,
                    selected = currentTab == tab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun FloatingGlassTab(modifier: Modifier, tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .height(82.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.965f else 1f
                scaleY = if (pressed) 0.965f else 1f
            }
            .clip(shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFF4FCFF).copy(alpha = 0.92f),
                            Color(0xFFCDEFFF).copy(alpha = 0.78f),
                            Color(0xFFB8A8FF).copy(alpha = 0.42f)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(if (selected) 1.2.dp else 0.dp, Color.White.copy(alpha = if (selected) 0.42f else 0f), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(tab.icon, color = if (selected) Color(0xFF061428) else Color(0xB8D6E0F6), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(tab.label, color = if (selected) Color(0xFF061428) else Color(0xB8D6E0F6), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
