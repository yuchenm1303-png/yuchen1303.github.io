package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.StatSummary
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { CloudStatusPill(state) }
        item { AssistantGlassShell(state) }
    }
}

@Composable
private fun AssistantGlassShell(state: AssistantUiState) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 32,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeaderChip("AI", "Workers", state, Modifier.weight(1.05f))
                HeaderChip("✦", "轻量待命", state, Modifier.weight(1.22f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.stats.take(2).forEach { stat ->
                    StatGlassCard(stat, state, Modifier.weight(1f))
                }
            }
            if (state.showPreviewConversation) {
                state.messages.forEach { message ->
                    MessageRow(message = message, state = state)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("设提醒", "导航回家", "记一笔").forEach { action ->
                        SmallGlassButton(text = action, state = state, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                PreviewHiddenCard(state)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComposerInputGlass(state = state, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                CircleGlassButton("➤", state)
            }
            Text(
                text = "已连接云端 AI。本地动作会优先快速识别，复杂问题再交给云端。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun CloudStatusPill(state: AssistantUiState) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(162.dp).height(52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("◎", color = Color(0xFF8DF9EA), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("强制联网", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF66F5DC))
            )
        }
    }
}

@Composable
private fun HeaderChip(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolsScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("功能中心", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        items(state.tools, key = { it.title }) { tool -> ToolCard(tool = tool, state = state) }
    }
}

@Composable
fun SettingsScreen(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("玻璃模式", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GlassPreset.entries.forEach { preset ->
                            val selected = state.glassPreset == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.040f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = if (selected) 0.32f else 0.13f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { onGlassPresetChange(preset) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preset.label, color = Color.White.copy(alpha = if (selected) 0.98f else 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("画质与性能", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    RenderQuality.entries.forEach { item ->
                        QualityRow(item = item, selected = item == state.quality, state = state, onClick = { onQualityChange(item) })
                    }
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("液态玻璃强度", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("当前 ${state.glassIntensity.format2x()}x", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
                    Slider(value = state.glassIntensity, onValueChange = onGlassIntensityChange, valueRange = 0.6f..1.4f)
                    Text("动态强度", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("当前 ${state.motionIntensity.format2x()}x", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
                    Slider(value = state.motionIntensity, onValueChange = onMotionIntensityChange, valueRange = 0f..1.4f)
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("聊天预览", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("保留示例对话和快捷指令，关闭后只显示更接近真实聊天入口的空白态。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("账号与同步", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("当前是 Compose 迁移预览版，后续会接入原来的云同步和 AI 解析服务。", color = Color.White.copy(alpha = 0.62f), fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(text = aiEndpoint, color = Color.White.copy(alpha = 0.36f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun ComposerInputGlass(state: AssistantUiState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "composer-sheen")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "composer-sheen-value"
    )
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.026f + 0.026f * sweep), Color.Transparent)
                    )
                )
        ) {
            Text(
                text = if (state.showPreviewConversation) "和我说点什么" else "输入会接到后续原生 AI 会话",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp)
            )
        }
    }
}

@Composable
private fun PreviewHiddenCard(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("预览对话已隐藏", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("现在展示的是更接近真实聊天入口的空白态，方便下一步直接接 AI Worker。", color = Color.White.copy(alpha = 0.58f), fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, state: AssistantUiState) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "msg-alpha")
    val lift by animateDpAsState(if (visible) 0.dp else 8.dp, label = "msg-lift")

    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.offset(y = lift),
        horizontalArrangement = if (message.role == MessageRole.User) Arrangement.End else Arrangement.Start
    ) {
        if (message.role == MessageRole.User) {
            ActionPill(message.text, state)
        } else {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.fillMaxWidth(0.88f)) {
                Text(
                    text = message.text,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 19.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tool.icon, color = Color(0xFFA9F4FF), fontSize = 22.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(tool.subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 14.sp)
            }
            Text("→", color = Color.White.copy(alpha = 0.68f), fontSize = 26.sp)
        }
    }
}

@Composable
private fun QualityRow(item: RenderQuality, selected: Boolean, state: AssistantUiState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "quality-press")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(color = if (selected) Color(0x204DA6FF) else Color.White.copy(alpha = state.quality.glassAlpha * 0.11f), shape = RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = if (selected) 0.34f else 0.13f), Color.White.copy(alpha = 0.045f))),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "●" else "○", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(item.desc, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun StatGlassCard(stat: StatSummary, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(98.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stat.title, color = Color.White.copy(alpha = 0.56f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stat.value, color = Color.White.copy(alpha = 0.92f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ActionPill(text: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.width(154.dp).height(56.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(52.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CircleGlassButton(text: String, state: AssistantUiState) {
    val transition = rememberInfiniteTransition(label = "send-btn-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (state.motionIntensity > 0f) 1.025f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "send-btn-pulse-value"
    )
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.size(62.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiquidBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(quality, glassIntensity, motionIntensity, 34, modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(7.dp)) {
            val tabCount = AppTab.entries.size
            val slot = maxWidth / tabCount
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(slot * target.toFloat(), label = "nav-indicator-x")
            val indicatorW by animateDpAsState(slot - 8.dp, label = "nav-indicator-w")

            Box(
                modifier = Modifier
                    .offset(x = indicatorX + 4.dp, y = 2.dp)
                    .width(indicatorW)
                    .height(64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.16f * glassIntensity),
                                Color(0xFFB3D7FF).copy(alpha = 0.10f * glassIntensity),
                                Color(0xFFB7A6FF).copy(alpha = 0.09f * glassIntensity)
                            )
                        )
                    )
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.26f * glassIntensity), shape = RoundedCornerShape(24.dp))
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(interactionSource = interaction, indication = null) { onTabChange(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(navIcon(tab), color = Color.White.copy(alpha = if (selected) 0.98f else 0.55f), fontSize = 22.sp, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.title,
                            color = Color.White.copy(alpha = if (selected) 0.96f else 0.54f),
                            fontSize = 13.sp,
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

private fun navIcon(tab: AppTab): String {
    return when (tab) {
        AppTab.Assistant -> "✦"
        AppTab.Tools -> "▦"
        AppTab.Settings -> "⚙"
    }
}

private fun Float.format2x(): String {
    return (this * 100).roundToInt().div(100f).toString()
}
