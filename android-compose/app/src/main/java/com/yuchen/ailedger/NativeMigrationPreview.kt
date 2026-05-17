package com.yuchen.ailedger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab

@Composable
fun NativeMigrationPreviewScreen() {
    Box(Modifier.fillMaxSize().background(NativeBackdropBrush)) {
        PreviewOrb(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 18.dp), 260.dp, Color(0xFF68D8FF).copy(alpha = 0.18f))
        PreviewOrb(Modifier.align(Alignment.BottomStart).padding(start = 0.dp, bottom = 78.dp), 310.dp, Color(0xFFB783FF).copy(alpha = 0.16f))
        PreviewStars()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewPill("清空对话")
                PreviewPill("🌐  强制联网  ●")
            }

            NativeGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                corner = 34.dp,
                padding = PaddingValues(16.dp),
                fill = Color.White.copy(alpha = 0.075f)
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NativeGlassPanel(Modifier.weight(1f), corner = 28.dp, padding = PaddingValues(12.dp), fill = Color.White.copy(alpha = 0.10f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF9DEEFF), Color(0xFF9180FF)))), contentAlignment = Alignment.Center) {
                                    Text("AI", color = Color.White, fontWeight = FontWeight.Black)
                                }
                                Text("Workers", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        NativeGlassPanel(Modifier.weight(1.6f), corner = 28.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.055f)) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Text("✦ 轻量待命", color = Color(0xCCDFE7FF), fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            NativeGlassPanel(corner = 26.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.065f)) {
                                Text(
                                    text = "这里是原生 Compose 迁移预览。旧版 WebView 继续作为视觉基准，接下来会把背景、玻璃卡片、底部导航、输入框逐块翻译成原生组件。",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                        item { PreviewStatusPill("原生迁移预览 · native-compose-baseline") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PreviewPill("设提醒")
                        PreviewPill("导航回家")
                        PreviewPill("记一笔")
                    }

                    NativeGlassPanel(corner = 32.dp, padding = PaddingValues(8.dp), fill = Color.White.copy(alpha = 0.06f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            }
                            Text("和我说点什么", color = Color(0x88E5EDFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.20f)).border(1.dp, Color.White.copy(alpha = 0.34f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("➤", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            FloatingGlassBottomNav(currentTab = AppTab.Chat, onTabSelected = {})
        }
    }
}

@Composable
private fun NativeGlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    fill: Color = Color.White.copy(alpha = 0.06f),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(22.dp, shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.26f), shape)
    ) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent), center = Offset(90f, 0f), radius = 340f)))
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color(0xFFBCA8FF).copy(alpha = 0.08f)))))
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun PreviewPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PreviewStatusPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF55F2C8).copy(alpha = 0.13f))
            .border(1.dp, Color(0xFF55F2C8).copy(alpha = 0.36f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("● $text", color = Color(0xFFB9FFE9), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PreviewOrb(modifier: Modifier, size: Dp, color: Color) {
    Box(modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.36f), Color.Transparent))))
}

@Composable
private fun PreviewStars() {
    Box(Modifier.fillMaxSize()) {
        repeat(28) { i ->
            Box(
                Modifier
                    .padding(start = ((i * 41) % 360).dp, top = ((i * 73) % 760).dp)
                    .size(if (i % 5 == 0) 2.dp else 1.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i % 5 == 0) 0.28f else 0.14f))
            )
        }
    }
}

private val NativeBackdropBrush = Brush.linearGradient(
    listOf(
        Color(0xFF050815),
        Color(0xFF08132D),
        Color(0xFF11284C),
        Color(0xFF211B4A),
        Color(0xFF3A1F55)
    )
)
