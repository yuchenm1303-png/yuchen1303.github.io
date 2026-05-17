package com.yuchen.ailedger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.StatSummary
import com.yuchen.ailedger.model.ToolEntry

@Composable
fun AssistantScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                state.stats.take(2).forEach { stat ->
                    StatGlassCard(stat, state.quality, Modifier.weight(1f))
                }
            }
        }
        items(state.messages, key = { it.id }) { message ->
            MessageRow(message = message, quality = state.quality)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("设提醒", "导航回家", "记一笔").forEach {
                    SmallGlassButton(text = it, quality = state.quality, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(quality = state.quality, radius = 30, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "和我说点什么",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 19.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                CircleGlassButton("➤", state.quality)
            }
        }
    }
}

@Composable
fun ToolsScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("功能中心", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        items(state.tools, key = { it.title }) { tool ->
            ToolCard(tool = tool, quality = state.quality)
        }
    }
}

@Composable
fun SettingsScreen(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        item {
            GlassPanel(quality = state.quality, radius = 28, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("画质与性能", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    RenderQuality.values().forEach { item ->
                        QualityRow(
                            item = item,
                            selected = item == state.quality,
                            quality = state.quality,
                            onClick = { onQualityChange(item) }
                        )
                    }
                }
            }
        }
        item {
            GlassPanel(quality = state.quality, radius = 28, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("账号与同步", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前为 Compose 迁移预览版，后续会接入原来的云同步和 AI 解析服务。",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = aiEndpoint,
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, quality: RenderQuality) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == MessageRole.User) Arrangement.End else Arrangement.Start
    ) {
        if (message.role == MessageRole.User) {
            ActionPill(message.text, quality)
        } else {
            GlassPanel(
                quality = quality,
                radius = 26,
                modifier = Modifier.fillMaxWidth(0.84f)
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, quality: RenderQuality) {
    GlassPanel(quality = quality, radius = 26, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tool.icon, color = Color(0xFFA9F4FF), fontSize = 22.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(tool.subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp)
            }
            Text("›", color = Color.White.copy(alpha = 0.72f), fontSize = 28.sp)
        }
    }
}

@Composable
private fun QualityRow(
    item: RenderQuality,
    selected: Boolean,
    quality: RenderQuality,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "quality-press")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(
                color = if (selected) Color(0x334DA6FF) else Color.White.copy(alpha = quality.glassAlpha * 0.32f),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (selected) 0.48f else 0.20f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "●" else "○", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(item.desc, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun StatGlassCard(stat: StatSummary, quality: RenderQuality, modifier: Modifier = Modifier) {
    GlassPanel(quality = quality, radius = 26, modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(stat.title, color = Color.White.copy(alpha = 0.62f), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(stat.value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ActionPill(text: String, quality: RenderQuality) {
    PressableGlass(
        quality = quality,
        radius = 24,
        modifier = Modifier
            .width(154.dp)
            .height(64.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmallGlassButton(text: String, quality: RenderQuality, modifier: Modifier = Modifier) {
    PressableGlass(quality = quality, radius = 24, modifier = modifier.height(56.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircleGlassButton(text: String, quality: RenderQuality) {
    PressableGlass(quality = quality, radius = 999, modifier = Modifier.size(66.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiquidBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(quality = quality, radius = 34, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppTab.values().forEach { tab ->
                val selected = tab == currentTab
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(74.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(interactionSource = interaction, indication = null) { onTabChange(tab) }
                        .background(
                            color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                            shape = RoundedCornerShape(26.dp)
                        )
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = Color.White.copy(alpha = if (selected) 0.28f else 0f),
                            shape = RoundedCornerShape(26.dp)
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tab.icon, color = Color.White.copy(alpha = if (selected) 0.96f else 0.56f), fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.title, color = Color.White.copy(alpha = if (selected) 0.96f else 0.56f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
