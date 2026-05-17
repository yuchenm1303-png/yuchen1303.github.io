package com.yuchen.ailedger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.android.AndroidActionExecutor
import com.yuchen.ailedger.logic.CommandRouter
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantCommand
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiLedgerTheme {
                AiLedgerComposeApp()
            }
        }
    }
}

@Composable
private fun AiLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeepNavy,
            surface = GlassSurface,
            primary = LiquidBlue,
            onPrimary = InkDark,
            onBackground = Color(0xFFF7FAFF),
            onSurface = Color(0xFFF7FAFF)
        ),
        content = content
    )
}

@Composable
private fun AiLedgerComposeApp() {
    var currentTabName by rememberSaveable { mutableStateOf(AppTab.Chat.name) }
    val currentTab = AppTab.valueOf(currentTabName)

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                LiquidBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTabName = it.name }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedVisibility(
                    visible = currentTab == AppTab.Chat,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.985f)
                ) { ChatScreen() }

                AnimatedVisibility(
                    visible = currentTab == AppTab.Tools,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.985f)
                ) { ToolsScreen() }

                AnimatedVisibility(
                    visible = currentTab == AppTab.Settings,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.985f)
                ) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun ChatScreen() {
    val context = LocalContext.current
    var nextId by remember { mutableLongStateOf(2L) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1L,
                role = MessageRole.Assistant,
                content = "你好，我是原生 Compose 版 AI 助手。现在界面正在迁成液态玻璃风格，聊天、输入框、底部导航和动作卡片都已经脱离 WebView。",
                actionHint = "compose_liquid_glass"
            )
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader(
            eyebrow = "AI多功能助手",
            title = "对话",
            subtitle = "原生 Compose · 液态玻璃界面"
        )

        LiquidGlassCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            corner = 34.dp,
            padding = PaddingValues(12.dp),
            tint = Color(0xFFBFD8FF).copy(alpha = 0.055f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onExecuteCommand = { command -> AndroidActionExecutor.execute(context, command) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LiquidChip("设提醒") { input = "明天早上8点叫我起床" }
            LiquidChip("导航回家") { input = "导航回家" }
            LiquidChip("记一笔") { input = "今天午饭28" }
        }

        ChatComposer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val clean = input.trim()
                if (clean.isEmpty()) return@ChatComposer
                val result = CommandRouter.route(clean)
                messages += ChatMessage(nextId++, MessageRole.User, clean)
                messages += ChatMessage(
                    id = nextId++,
                    role = MessageRole.Assistant,
                    content = result.reply,
                    actionHint = result.source,
                    command = result.command,
                    ledgerDraft = result.ledgerDraft
                )
                input = ""
            }
        )
    }
}

@Composable
private fun ToolsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader(
            eyebrow = "功能中心",
            title = "工具与能力",
            subtitle = "原生功能入口，逐步替代网页工具页"
        )

        LiquidGlassCard(padding = PaddingValues(14.dp), corner = 30.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolRow("▤", "账单中心", "下一步接 Room 数据库，迁移记录列表、分类和导出 JSON。")
                ToolRow("▣", "数据统计", "后续用 Compose Canvas 或图表库替代网页 canvas。")
                ToolRow("⏰", "提醒闹钟", "当前已接 Android AlarmClock Intent 框架。")
                ToolRow("◎", "应用控制", "当前已接常用 App 包名映射和 Intent 启动框架。")
                ToolRow("⌁", "快捷指令", "把常用任务沉淀成原生本地模板。")
            }
        }

        LiquidGlassCard(padding = PaddingValues(14.dp), corner = 28.dp, tint = Color(0xFF7DE8D4).copy(alpha = 0.05f)) {
            Text(
                text = "迁移原则：原生主界面负责高频交互，旧 WebView 后续只保留为调试/兼容入口。液态玻璃使用轻量渐变模拟，不再走 WebView 的重度 backdrop-filter。",
                color = SoftText,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader(
            eyebrow = "设置中心",
            title = "应用设置",
            subtitle = "原生分组结构，后续逐项接真实数据"
        )

        SettingGroup("☁", "账号与同步", "登录、注册、AI 接口和云同步。")
        SettingGroup("Aa", "显示与语言", "语言、字体大小、玻璃透明度、动画效果。")
        SettingGroup("⌖", "手机偏好", "家庭地址、默认地图、常用应用。")
        SettingGroup("✦", "背景外观", "Compose 轻量渐变背景，避免 WebView 重模糊。")
        SettingGroup("▤", "数据与预算", "预算、聊天记录、账单导出、清空数据。")
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = eyebrow,
            color = LiquidBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = SoftText,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onExecuteCommand: (AssistantCommand) -> Unit
) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.88f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                Surface(
                    color = LiquidBlue,
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = 22.dp,
                        bottomEnd = 8.dp
                    ),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = message.content,
                        color = InkDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LiquidGlassCard(
                    corner = 24.dp,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    tint = Color.White.copy(alpha = 0.04f)
                ) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    )
                }
            }

            message.command?.let { command ->
                Spacer(Modifier.height(8.dp))
                CommandCard(command = command, onExecute = { onExecuteCommand(command) })
            }

            if (!message.actionHint.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "来源：${message.actionHint}",
                    color = SoftText.copy(alpha = 0.66f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CommandCard(command: AssistantCommand, onExecute: () -> Unit) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(12.dp),
        corner = 24.dp,
        tint = Color(0xFF8FD8FF).copy(alpha = 0.07f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(command.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(command.description, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
            LiquidActionButton(text = command.primaryActionLabel, onClick = onExecute)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    LiquidGlassCard(padding = PaddingValues(8.dp), corner = 30.dp, tint = Color.White.copy(alpha = 0.045f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("和我说点什么", color = SoftText.copy(alpha = 0.72f)) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = LiquidBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            LiquidSendButton(onClick = onSend)
        }
    }
}

@Composable
private fun LiquidChip(text: String, onClick: () -> Unit) {
    LiquidPressable(onClick = onClick) { pressed ->
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = if (pressed) 0.965f else 1f
                    scaleY = if (pressed) 0.965f else 1f
                }
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.08f),
                            Color(0xFF7DE8D4).copy(alpha = 0.08f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ToolRow(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidIconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = SoftText, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SettingGroup(icon: String, title: String, desc: String) {
    LiquidPressable(onClick = {}) { pressed ->
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = if (pressed) 0.992f else 1f
                    scaleY = if (pressed) 0.992f else 1f
                },
            padding = PaddingValues(14.dp),
            corner = 27.dp,
            tint = Color.White.copy(alpha = 0.045f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidIconBox(icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(desc, color = SoftText, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Text("›", color = SoftText, fontSize = 30.sp)
            }
        }
    }
}

@Composable
private fun LiquidBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    LiquidGlassCard(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth(),
        corner = 34.dp,
        padding = PaddingValues(8.dp),
        tint = Color.White.copy(alpha = 0.065f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                LiquidNavItem(
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
private fun LiquidNavItem(
    modifier: Modifier,
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    LiquidPressable(onClick = onClick) { pressed ->
        val shape = RoundedCornerShape(25.dp)
        Column(
            modifier = modifier
                .height(58.dp)
                .graphicsLayer {
                    scaleX = if (pressed) 0.965f else 1f
                    scaleY = if (pressed) 0.965f else 1f
                }
                .clip(shape)
                .background(
                    if (selected) {
                        Brush.linearGradient(
                            listOf(
                                LiquidBlue.copy(alpha = 0.96f),
                                Color(0xFFB7ECFF).copy(alpha = 0.88f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.Transparent)
                        )
                    }
                )
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tab.icon,
                color = if (selected) InkDark else Color.White.copy(alpha = 0.74f),
                fontSize = 21.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = tab.label,
                color = if (selected) InkDark else Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LiquidActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LiquidBlue,
            contentColor = InkDark
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiquidSendButton(onClick: () -> Unit) {
    LiquidPressable(onClick = onClick) { pressed ->
        Box(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = if (pressed) 0.94f else 1f
                    scaleY = if (pressed) 0.94f else 1f
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF99E1FF), Color(0xFF75CFFF))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("➤", color = InkDark, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LiquidIconBox(icon: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.17f),
                        Color.White.copy(alpha = 0.06f),
                        Color(0xFF8FD8FF).copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    tint: Color = Color.White.copy(alpha = 0.05f),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.15f),
                        tint,
                        Color(0xFF0C142A).copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(120f, 18f),
                        radius = 360f
                    )
                )
        )
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
private fun LiquidPressable(
    onClick: () -> Unit,
    content: @Composable (pressed: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        content(pressed)
    }
}

@Composable
private fun LiquidBackdrop() {
    val transition = rememberInfiniteTransition(label = "liquid-backdrop")
    val driftA by transition.animateFloat(
        initialValue = -12f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(8200), RepeatMode.Reverse),
        label = "orb-a"
    )
    val driftB by transition.animateFloat(
        initialValue = 16f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(tween(9600), RepeatMode.Reverse),
        label = "orb-b"
    )
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(7200), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush)
    ) {
        LiquidOrb(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = driftA.dp, y = 82.dp),
            size = 220.dp,
            color = Color(0xFF6DBDFF).copy(alpha = 0.20f * glow)
        )
        LiquidOrb(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-34).dp, y = driftB.dp),
            size = 250.dp,
            color = Color(0xFF6EF0D1).copy(alpha = 0.16f * glow)
        )
        LiquidOrb(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 118.dp, y = (-22).dp),
            size = 160.dp,
            color = Color(0xFFB18CFF).copy(alpha = 0.09f)
        )
    }
}

@Composable
private fun LiquidOrb(modifier: Modifier, size: Dp, color: Color) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = 0.92f }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = color.alpha * 1.25f),
                        color.copy(alpha = color.alpha * 0.38f),
                        Color.Transparent
                    )
                )
            )
    )
}

private val DeepNavy = Color(0xFF071326)
private val GlassSurface = Color(0x241E2A44)
private val LiquidBlue = Color(0xFF8FD8FF)
private val InkDark = Color(0xFF061428)
private val SoftText = Color(0xBFE4ECFF)

private val AppBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF061225),
        Color(0xFF10203F),
        Color(0xFF2F335C),
        Color(0xFF4B405E)
    )
)
