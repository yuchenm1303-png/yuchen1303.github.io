package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 118.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopCommandPill("清空对话", state, Modifier.width(132.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopCommandPill("◎", "自动联网", state, Modifier.width(190.dp))
            }
        }
        item { AssistantShell(state) }
    }
}

@Composable
private fun AssistantShell(state: AssistantUiState) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 32,
        modifier = Modifier.fillMaxWidth(),
        role = GlassRole.Shell
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeaderChip("AI", "自动", state, Modifier.weight(1.05f))
                HeaderChip("✦", "轻量待命", state, Modifier.weight(1.22f))
            }
            if (state.showPreviewConversation) {
                state.messages.take(1).forEach { AssistantMessageCard(it, state) }
            } else {
                PreviewHiddenCard(state)
            }
            Spacer(Modifier.height(250.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("设提醒", "导航回家", "记一笔").forEach { action ->
                    SmallGlassButton(text = action, state = state, modifier = Modifier.weight(1f))
                }
            }
            ComposerBar(state)
            Text(
                text = "本地动作会优先快速识别，复杂问题再交给云端。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TopCommandPill(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(50.dp),
        role = GlassRole.Chip
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun TopCommandPill(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(56.dp),
        role = GlassRole.Chip
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(icon, color = Color(0xFF8DF9EA), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.48f)))
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
        modifier = modifier.height(58.dp),
        role = GlassRole.Chip
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AssistantMessageCard(message: ChatMessage, state: AssistantUiState) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "msg-alpha")
    val lift by animateDpAsState(if (visible) 0.dp else 10.dp, label = "msg-lift")

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.offset(y = lift),
        role = GlassRole.Card
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = message.text,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 20.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium
            )
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 999,
                modifier = Modifier.width(138.dp).height(42.dp),
                role = GlassRole.Chip
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.74f)))
                    Text("内置回复", color = Color.White.copy(alpha = 0.90f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(state: AssistantUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier.size(62.dp),
            role = GlassRole.Floating
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        ComposerInputGlass(state = state, modifier = Modifier.weight(1f))
        CircleGlassButton("➤", state)
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
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.height(62.dp), GlassRole.Card) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.026f + 0.026f * sweep), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "和我说点什么",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp)
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
        contentPadding = PaddingValues(top = 24.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SettingsHero() }
        item { GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 32, Modifier.fillMaxWidth().height(112.dp), GlassRole.Shell) {} }
        item { SettingsListCard("Aa", "显示与语言", "语言、字体、玻璃透明度、模糊强度和动效。", state) }
        item { SettingsListCard("⌖", "手机偏好", "家庭地址、默认地图等手机任务偏好。", state) }
        item { SettingsListCard("✦", "背景外观", "切换内置背景风格。", state) }
        item { SettingsListCard("▤", "数据与预算", "预算、导出、清空记录等数据工具。", state) }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Shell) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("玻璃性能模式", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "默认建议用“自动”。它会根据 Android 设备、内存、CPU 核心数、屏幕尺寸和当前交互状态自动选择策略。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 16.sp,
                        lineHeight = 25.sp
                    )
                    GlassPresetSelector(state, onGlassPresetChange)
                    Text("液态玻璃强度 ${state.glassIntensity.format2x()}x", color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
                    Slider(value = state.glassIntensity, onValueChange = onGlassIntensityChange, valueRange = 0.6f..1.4f)
                    Text("动态强度 ${state.motionIntensity.format2x()}x", color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
                    Slider(value = state.motionIntensity, onValueChange = onMotionIntensityChange, valueRange = 0f..1.4f)
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("聊天预览", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("保留示例对话和快捷指令。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp)
                    }
                    Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
                }
            }
        }
        item {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
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
private fun SettingsHero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("设置中心", color = Color(0xFF76F2FF), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("应用设置", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black, lineHeight = 52.sp)
        Text("接入登录、云端同步与个性化外观", color = Color.White.copy(alpha = 0.58f), fontSize = 20.sp)
    }
}

@Composable
private fun SettingsListCard(icon: String, title: String, subtitle: String, state: AssistantUiState) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth().height(108.dp),
        role = GlassRole.Card
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 18, Modifier.size(56.dp), GlassRole.Chip) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(icon, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color.White.copy(alpha = 0.78f), fontSize = 38.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun GlassPresetSelector(state: AssistantUiState, onGlassPresetChange: (GlassPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassPreset.entries.forEach { preset ->
            val selected = state.glassPreset == preset
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 20,
                modifier = Modifier.weight(1f).height(54.dp),
                role = if (selected) GlassRole.Floating else GlassRole.Chip,
                onClick = { onGlassPresetChange(preset) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(preset.label, color = Color.White.copy(alpha = if (selected) 1f else 0.72f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PreviewHiddenCard(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(20.dp)) {
            Text("预览对话已隐藏", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("现在展示的是更接近真实聊天入口的空白态，方便下一步直接接 AI Worker。", color = Color.White.copy(alpha = 0.58f), fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, state: AssistantUiState) {
    SettingsListCard(tool.icon, tool.title, tool.subtitle, state)
}

@Composable
private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(52.dp), GlassRole.Chip) {
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
        modifier = Modifier.size(62.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
        role = GlassRole.Floating
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
    GlassPanel(quality, glassIntensity, motionIntensity, 34, modifier.fillMaxWidth(), GlassRole.Nav) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(7.dp)) {
            val tabCount = AppTab.entries.size
            val slot = maxWidth / tabCount
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(slot * target.toFloat(), animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-x")
            val indicatorW by animateDpAsState(slot - 8.dp, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-w")

            GlassPanel(
                quality = quality,
                glassIntensity = glassIntensity * 1.10f,
                motionIntensity = motionIntensity,
                radius = 26,
                modifier = Modifier.offset(x = indicatorX + 4.dp, y = 1.dp).width(indicatorW).height(68.dp),
                role = GlassRole.Floating
            ) {}

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(interactionSource = interaction, indication = null) { onTabChange(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(navIcon(tab), color = Color.White.copy(alpha = if (selected) 0.98f else 0.55f), fontSize = 22.sp, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(tab.title, color = Color.White.copy(alpha = if (selected) 0.96f else 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
