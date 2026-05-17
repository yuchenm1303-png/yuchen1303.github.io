package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(state: AssistantUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 54.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TopCommandPill("清空对话", state, Modifier.width(118.dp).height(42.dp))
            TopCommandPill("◎", "自动联网", state, Modifier.width(170.dp).height(48.dp))
        }
        AssistantShell(state, Modifier.weight(1f))
    }
}

@Composable
private fun AssistantShell(state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                HeaderChip("AI", "自动", state, Modifier.weight(1f))
                HeaderChip("✦", "轻量待命", state, Modifier.weight(1.45f))
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.showPreviewConversation) {
                    items(state.messages, key = { it.id }) { message -> ChatBubble(message, state) }
                } else {
                    item { PreviewHiddenCard(state) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("设提醒", "导航回家", "记一笔").forEach { action ->
                    SmallGlassButton(action, state, Modifier.weight(1f))
                }
            }
            ComposerBar(state)
            Text(
                text = "本地动作会优先快速识别，复杂问题再交给云端。",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, state: AssistantUiState) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 22, Modifier.fillMaxWidth(0.68f), GlassRole.Floating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xAA4C6CFF), Color(0xA8784CE6))))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(message.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.fillMaxWidth(0.92f), GlassRole.Card) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message.text, color = Color.White.copy(alpha = 0.90f), fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
                    if (message.id == state.messages.firstOrNull()?.id) {
                        PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.width(116.dp).height(36.dp), GlassRole.Chip) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.70f)))
                                Text("内置回复", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCommandPill(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier, GlassRole.Chip) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun TopCommandPill(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier, GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, color = Color(0xFF8DF9EA), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.48f)))
        }
    }
}

@Composable
private fun HeaderChip(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(56.dp), GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ComposerBar(state: AssistantUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(50.dp), GlassRole.Floating) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        ComposerInputGlass(state = state, modifier = Modifier.weight(1f))
        CircleGlassButton("➤", state)
    }
}

@Composable
private fun ComposerInputGlass(state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, modifier.height(50.dp), GlassRole.Card) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text("和我说点什么", color = Color.White.copy(alpha = 0.50f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun ToolsScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("功能中心", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
        items(state.tools, key = { it.title }) { tool -> ToolCard(tool, state) }
    }
}

@Composable
fun SettingsScreen(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SettingsHero() }
        item { GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 32, Modifier.fillMaxWidth().height(92.dp), GlassRole.Shell) {} }
        item { SettingsListCard("Aa", "显示与语言", "语言、字体、玻璃透明度、模糊强度和动效。", state) }
        item { SettingsListCard("⌖", "手机偏好", "家庭地址、默认地图等手机任务偏好。", state) }
        item { SettingsListCard("✦", "背景外观", "切换内置背景风格。", state) }
        item { BackgroundThemeSelector(state, onBackgroundThemeChange) }
        item { SettingsListCard("▤", "数据与预算", "预算、导出、清空记录等数据工具。", state) }
        item { GlassPerformanceCard(state, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange) }
        item { PreviewSwitchCard(state, onPreviewConversationChange) }
        item { SyncCard(state, aiEndpoint) }
    }
}

@Composable
private fun SettingsHero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("设置中心", color = Color(0xFF76F2FF), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("应用设置", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, lineHeight = 48.sp)
        Text("接入登录、云端同步与个性化外观", color = Color.White.copy(alpha = 0.58f), fontSize = 18.sp)
    }
}

@Composable
private fun SettingsListCard(icon: String, title: String, subtitle: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth().height(100.dp), GlassRole.Card) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 18, Modifier.size(52.dp), GlassRole.Chip) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color.White.copy(alpha = 0.78f), fontSize = 34.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun BackgroundThemeSelector(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("背景主题", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackgroundTheme.entries.forEach { theme ->
                    val selected = state.backgroundTheme == theme
                    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 18, Modifier.weight(1f).height(64.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onBackgroundThemeChange(theme) }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(theme.label, color = Color.White.copy(alpha = if (selected) 1f else 0.70f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPerformanceCard(
    state: AssistantUiState,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("玻璃性能模式", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassPreset.entries.forEach { preset ->
                    val selected = state.glassPreset == preset
                    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.weight(1f).height(52.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onGlassPresetChange(preset) }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(preset.label, color = Color.White.copy(alpha = if (selected) 1f else 0.72f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                }
            }
            Text("液态玻璃强度 ${state.glassIntensity.format2x()}x", color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
            Slider(value = state.glassIntensity, onValueChange = onGlassIntensityChange, valueRange = 0.6f..1.4f)
            Text("动态强度 ${state.motionIntensity.format2x()}x", color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
            Slider(value = state.motionIntensity, onValueChange = onMotionIntensityChange, valueRange = 0f..1.4f)
        }
    }
}

@Composable
private fun PreviewSwitchCard(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("聊天预览", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("保留示例对话和快捷指令。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp)
            }
            Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
        }
    }
}

@Composable
private fun SyncCard(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(18.dp)) {
            Text("账号与同步", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("当前是 Compose 迁移预览版，后续会接入原来的云同步和 AI 解析服务。", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(12.dp))
            Text(text = aiEndpoint, color = Color.White.copy(alpha = 0.36f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PreviewHiddenCard(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(18.dp)) {
            Text("预览对话已隐藏", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("现在展示的是更接近真实聊天入口的空白态。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, state: AssistantUiState) {
    SettingsListCard(tool.icon, tool.title, tool.subtitle, state)
}

@Composable
private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(42.dp), GlassRole.Chip) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CircleGlassButton(text: String, state: AssistantUiState) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(50.dp), GlassRole.Floating) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
    GlassPanel(quality, glassIntensity, motionIntensity, 30, modifier.fillMaxWidth().height(82.dp), GlassRole.Nav) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            val slot = maxWidth / AppTab.entries.size
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(slot * target.toFloat(), animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-x")
            val indicatorW by animateDpAsState(slot - 8.dp, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-w")
            GlassPanel(quality, glassIntensity * 1.10f, motionIntensity, 24, Modifier.offset(x = indicatorX + 4.dp, y = 1.dp).width(indicatorW).height(66.dp), GlassRole.Floating) {}
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                    Column(
                        modifier = Modifier.weight(1f).height(68.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(26.dp)).clickable(interactionSource = interaction, indication = null) { onTabChange(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(navIcon(tab), color = Color.White.copy(alpha = if (selected) 0.98f else 0.55f), fontSize = 21.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text(tab.title, color = Color.White.copy(alpha = if (selected) 0.96f else 0.54f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun navIcon(tab: AppTab): String = when (tab) {
    AppTab.Assistant -> "✦"
    AppTab.Tools -> "▦"
    AppTab.Settings -> "⚙"
}

private fun Float.format2x(): String = (this * 100).roundToInt().div(100f).toString()
