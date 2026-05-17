package com.yuchen.ailedger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private enum class AppTab(val label: String, val icon: String) {
    Chat("AI助手", "✦"),
    Tools("功能", "▦"),
    Settings("设置", "⚙")
}

private enum class MessageRole { User, Assistant }

private data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val actionHint: String? = null
)

@Composable
private fun AiLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF071326),
            surface = Color(0x141E2A44),
            primary = Color(0xFF8FD8FF),
            onPrimary = Color(0xFF071326),
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush)
    ) {
        AmbientCircle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 24.dp),
            size = 190.dp,
            color = Color(0x557BCFFF)
        )
        AmbientCircle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 130.dp),
            size = 220.dp,
            color = Color(0x4466E6D0)
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                GlassBottomBar(
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
                    enter = fadeIn() + scaleIn(initialScale = 0.98f),
                    exit = fadeOut() + scaleOut(targetScale = 0.98f)
                ) { ChatScreen() }

                AnimatedVisibility(
                    visible = currentTab == AppTab.Tools,
                    enter = fadeIn() + scaleIn(initialScale = 0.98f),
                    exit = fadeOut() + scaleOut(targetScale = 0.98f)
                ) { ToolsScreen() }

                AnimatedVisibility(
                    visible = currentTab == AppTab.Settings,
                    enter = fadeIn() + scaleIn(initialScale = 0.98f),
                    exit = fadeOut() + scaleOut(targetScale = 0.98f)
                ) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun ChatScreen() {
    var nextId by remember { mutableLongStateOf(2L) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1L,
                role = MessageRole.Assistant,
                content = "你好，我是原生 Compose 版 AI 助手。现在先把聊天、输入框、底部导航从 WebView 迁出来，后面再逐步接入手机动作和云端 AI。",
                actionHint = "compose_native"
            )
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
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
            subtitle = "这是第一版原生 Compose 聊天页"
        )

        GlassCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            padding = PaddingValues(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickChip("设提醒") { input = "明天早上8点叫我起床" }
            QuickChip("导航回家") { input = "导航回家" }
            QuickChip("记一笔") { input = "今天午饭28" }
        }

        ChatComposer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val clean = input.trim()
                if (clean.isEmpty()) return@ChatComposer
                messages += ChatMessage(nextId++, MessageRole.User, clean)
                messages += ChatMessage(
                    id = nextId++,
                    role = MessageRole.Assistant,
                    content = localAssistantReply(clean),
                    actionHint = inferActionHint(clean)
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
            subtitle = "先用原生卡片替代网页功能入口"
        )

        GlassCard(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolRow("▤", "账单中心", "下一步迁移记录列表、分类、导出 JSON。")
                ToolRow("▣", "数据统计", "后续用 Compose Canvas 或图表库替代网页 canvas。")
                ToolRow("⏰", "提醒闹钟", "接 Android AlarmClock Intent 或原生提醒能力。")
                ToolRow("◎", "应用控制", "通过包名、Intent、辅助服务逐步接入手机动作。")
                ToolRow("⌁", "快捷指令", "把常用任务沉淀成原生本地模板。")
            }
        }

        GlassCard(padding = PaddingValues(14.dp)) {
            Text(
                text = "迁移原则：先把高频路径原生化，低频设置页可以慢慢搬。WebView 先保留成旧版入口，不要一刀切删除。",
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
            subtitle = "先搭原生分组结构，后面逐项接真实数据"
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
            color = Color(0xFF8FD8FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 34.sp,
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
private fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(padding)
        ) {
            content()
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.88f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isUser) Color(0xFF8FD8FF) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = if (isUser) 22.dp else 8.dp,
                    bottomEnd = if (isUser) 8.dp else 22.dp
                )
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) Color(0xFF061428) else Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            if (!message.actionHint.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "来源：${message.actionHint}",
                    color = SoftText.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
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
    GlassCard(padding = PaddingValues(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("和我说点什么", color = SoftText) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color(0xFF8FD8FF)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Button(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8FD8FF),
                    contentColor = Color(0xFF061428)
                )
            ) {
                Text("➤", fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ToolRow(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 21.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = SoftText, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SettingGroup(icon: String, title: String, desc: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(desc, color = SoftText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text("›", color = SoftText, fontSize = 30.sp)
        }
    }
}

@Composable
private fun GlassBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp)),
        containerColor = Color.White.copy(alpha = 0.13f),
        tonalElevation = 0.dp
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(
                        text = tab.icon,
                        color = if (currentTab == tab) Color(0xFF061428) else Color.White.copy(alpha = 0.70f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF061428),
                    selectedTextColor = Color(0xFF061428),
                    unselectedIconColor = Color.White.copy(alpha = 0.70f),
                    unselectedTextColor = Color.White.copy(alpha = 0.70f),
                    indicatorColor = Color(0xFF8FD8FF)
                )
            )
        }
    }
}

@Composable
private fun AmbientCircle(modifier: Modifier, size: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

private fun localAssistantReply(text: String): String {
    return when {
        text.contains("闹钟") || text.contains("叫我") || text.contains("提醒") ->
            "我识别到了提醒/闹钟意图。下一步会把这里接到 Android 原生 AlarmClock Intent 或通知提醒。"
        text.contains("导航") || text.contains("回家") ->
            "我识别到了导航意图。下一步会读取手机偏好里的家庭地址，再调用地图 Intent。"
        text.contains("打开") || text.contains("微信") || text.contains("支付宝") ->
            "我识别到了打开应用意图。后续会用包名映射和 Intent 启动常用 App。"
        text.contains("元") || text.contains("午饭") || text.contains("花") || text.contains("买") ->
            "我识别到了记账意图。下一步会接 Room 数据库，把账单存在本地，再做云同步。"
        else ->
            "收到。现在这是 Compose 原生壳里的本地回复，后面会接入你原来的云端 AI 接口和手机动作桥。"
    }
}

private fun inferActionHint(text: String): String {
    return when {
        text.contains("闹钟") || text.contains("叫我") || text.contains("提醒") -> "local_alarm_intent"
        text.contains("导航") || text.contains("回家") -> "local_navigation_intent"
        text.contains("打开") || text.contains("微信") || text.contains("支付宝") -> "local_open_app"
        text.contains("元") || text.contains("午饭") || text.contains("花") || text.contains("买") -> "local_ledger_draft"
        else -> "compose_local"
    }
}

private val SoftText = Color(0xBFE4ECFF)

private val AppBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF071326),
        Color(0xFF152442),
        Color(0xFF473E60)
    )
)
