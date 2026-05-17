package com.yuchen.ailedger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        setContent { AiLedgerTheme { AiLedgerApp() } }
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
            onBackground = Ink,
            onSurface = Ink
        ),
        content = content
    )
}

@Composable
private fun AiLedgerApp() {
    var tabName by rememberSaveable { mutableStateOf(AppTab.Chat.name) }
    val currentTab = AppTab.valueOf(tabName)

    Box(Modifier.fillMaxSize()) {
        OriginalLiquidBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { OriginalBottomNav(currentTab) { tabName = it.name } }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp)
            ) {
                AnimatedVisibility(currentTab == AppTab.Chat, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.992f)) { ChatScreen() }
                AnimatedVisibility(currentTab == AppTab.Tools, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.992f)) { ToolsScreen() }
                AnimatedVisibility(currentTab == AppTab.Settings, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.992f)) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun ChatScreen() {
    val context = LocalContext.current
    var nextId by remember { mutableLongStateOf(2L) }
    var plusOpen by rememberSaveable { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1L,
                role = MessageRole.Assistant,
                content = "云端 AI 请求超时或网络暂时不可用。这个问题没有成功返回云端结果，请稍后再试，或到设置里测试 Worker 连接。",
                actionHint = "云端连接失败 · cloud-error-normalizer-v4"
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OriginalTopControls {
            messages.clear()
            messages += ChatMessage(nextId++, MessageRole.Assistant, "对话已清空。", "compose_native")
        }

        OriginalGlassPanel(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            corner = 32.dp,
            padding = PaddingValues(14.dp),
            fill = LiquidFill
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelStatusStrip()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 6.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onExecuteCommand = { command -> AndroidActionExecutor.execute(context, command) },
                            onRetry = { input = "重新连接云端 AI" },
                            onCopy = { Toast.makeText(context, "已复制到剪贴板样式占位", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
                OriginalQuickTags(
                    onAlarm = { input = "明天早上8点叫我起床" },
                    onNav = { input = "导航回家" },
                    onLedger = { input = "今天午饭28" }
                )
                AnimatedVisibility(plusOpen) {
                    AttachmentPanel(
                        onPick = { label ->
                            plusOpen = false
                            Toast.makeText(context, "$label 功能稍后接入", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                OriginalComposer(
                    value = input,
                    onValueChange = { input = it },
                    onPlus = { plusOpen = !plusOpen },
                    onSend = {
                        val clean = input.trim()
                        if (clean.isEmpty()) return@OriginalComposer
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
    }
}

@Composable
private fun ToolsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 10.dp)
    ) {
        item { PageHeader("功能中心", "工具与能力", "把旧版功能页卡片迁成 Compose 原生玻璃网格。") }
        item { FeatureHeroCard() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolTile("▤", "账单中心", "记录 / 分类", Modifier.weight(1f))
                    ToolTile("▣", "数据统计", "趋势 / 占比", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolTile("⏰", "提醒闹钟", "系统 Intent", Modifier.weight(1f))
                    ToolTile("◎", "应用控制", "微信 / 支付宝", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolTile("⌁", "快捷指令", "常用任务", Modifier.weight(1f))
                    ToolTile("⇪", "导出数据", "JSON / 备份", Modifier.weight(1f))
                }
            }
        }
        item {
            OriginalGlassPanel(padding = PaddingValues(14.dp), corner = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolRow("✦", "旧版 WebView 备用入口", "后续只作为调试/兼容入口，主界面继续走 Compose 原生渲染。")
                    ToolRow("⚙", "性能模式", "玻璃效果用透明高光模拟，不启用高成本 backdrop-filter。")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 10.dp)
    ) {
        item { PageHeader("设置中心", "应用设置", "账号、显示、手机偏好、背景外观和数据预算。") }
        item { AccountSyncCard() }
        item { AppearancePreviewCard() }
        item { BudgetSnapshotCard() }
        item { SettingGroup("☁", "账号与同步", "登录、注册、AI 接口和云同步。") }
        item { SettingGroup("Aa", "显示与语言", "语言、字体大小、玻璃透明度、动画效果。") }
        item { SettingGroup("⌖", "手机偏好", "家庭地址、默认地图、常用应用。") }
        item { SettingGroup("✦", "背景外观", "选择天气星空、翡翠海雾等内置背景。") }
        item { SettingGroup("▤", "数据与预算", "预算、聊天记录、账单导出、清空数据。") }
    }
}

@Composable
private fun OriginalTopControls(onClear: () -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.Start) {
        OriginalSmallPill("清空对话", onClear)
        OriginalSmallPill("🌐  自动联网  ●") {}
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = LiquidBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(title, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun ModelStatusStrip() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OriginalGlassPanel(Modifier.weight(1f), corner = 28.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), fill = LiquidFillStrong) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF9DEEFF), Color(0xFF8F7DFF)))),
                    contentAlignment = Alignment.Center
                ) { Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) }
                Text("Mistral", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        OriginalGlassPanel(Modifier.weight(1.7f), corner = 28.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), fill = LiquidFillSoft) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("✦  轻量待命", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onExecuteCommand: (AssistantCommand) -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == MessageRole.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(if (isUser) 0.74f else 0.90f), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4C6CFF).copy(alpha = .88f), Color(0xFF7A4CE1).copy(alpha = .86f))))
                        .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) { Text(message.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp) }
            } else {
                OriginalGlassPanel(corner = 24.dp, padding = PaddingValues(horizontal = 15.dp, vertical = 13.dp), fill = LiquidFillSoft) {
                    Text(message.content, color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
            message.command?.let { command ->
                Spacer(Modifier.height(8.dp))
                CommandCard(command) { onExecuteCommand(command) }
            }
            message.ledgerDraft?.let {
                Spacer(Modifier.height(8.dp))
                LedgerDraftCard(it.title, it.amount, it.category)
            }
            if (!message.actionHint.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                OriginalStatusPill(message.actionHint)
                if (message.actionHint.contains("云端")) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OriginalMiniButton("重试", onRetry)
                        OriginalMiniButton("复制", onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(command: AssistantCommand, onExecute: () -> Unit) {
    OriginalGlassPanel(Modifier.fillMaxWidth(), corner = 20.dp, padding = PaddingValues(12.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(command.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(command.description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            OriginalAccentButton(command.primaryActionLabel, onExecute)
        }
    }
}

@Composable
private fun LedgerDraftCard(title: String, amount: Double, category: String) {
    OriginalGlassPanel(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(12.dp), fill = LiquidFillStrong) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("记账草稿", color = LiquidBlue, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("¥${"%.2f".format(amount)}", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Text("分类：$category · 等待确认保存", color = Muted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginalComposer(value: String, onValueChange: (String) -> Unit, onPlus: () -> Unit, onSend: () -> Unit) {
    OriginalGlassPanel(corner = 32.dp, padding = PaddingValues(8.dp), fill = LiquidFillSoft) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OriginalRoundButton("+", 52.dp, onPlus)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("和我说点什么", color = Muted.copy(alpha = .70f), fontSize = 17.sp) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = LiquidBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            OriginalSendButton(onSend)
        }
    }
}

@Composable
private fun OriginalQuickTags(onAlarm: () -> Unit, onNav: () -> Unit, onLedger: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OriginalTag("设提醒", onAlarm)
        OriginalTag("导航回家", onNav)
        OriginalTag("记一笔", onLedger)
    }
}

@Composable
private fun AttachmentPanel(onPick: (String) -> Unit) {
    OriginalGlassPanel(corner = 26.dp, padding = PaddingValues(12.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("快捷添加", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OriginalMiniButton("拍照", { onPick("拍照") })
                OriginalMiniButton("语音", { onPick("语音") })
                OriginalMiniButton("账单", { onPick("账单") })
                OriginalMiniButton("文件", { onPick("文件") })
            }
        }
    }
}

@Composable
private fun FeatureHeroCard() {
    OriginalGlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFill) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI 原生能力面板", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("这里开始承接原网页功能页：账单、统计、闹钟、导航、打开 App、导出数据。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OriginalStatusPill("本地命令路由")
                OriginalStatusPill("Intent 执行框架")
            }
        }
    }
}

@Composable
private fun ToolTile(icon: String, title: String, desc: String, modifier: Modifier = Modifier) {
    OriginalGlassPanel(modifier = modifier, corner = 28.dp, padding = PaddingValues(14.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidIconBox(icon)
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AccountSyncCard() {
    OriginalGlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFill) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidIconBox("☁")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("账号与云同步", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Worker 连接、模型选择、云端状态将在这里迁入。", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            OriginalMiniButton("测试") {}
        }
    }
}

@Composable
private fun AppearancePreviewCard() {
    OriginalGlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("显示与语言", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("语言 · 字体大小 · 玻璃透明度 · 动画效果 · 紧凑模式", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OriginalMiniButton("中文") {}
                OriginalMiniButton("中等字体") {}
                OriginalMiniButton("流畅动画") {}
            }
        }
    }
}

@Composable
private fun BudgetSnapshotCard() {
    OriginalGlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("数据与预算", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("本月支出", color = Muted, fontSize = 13.sp)
                Text("¥ 0.00", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(LiquidFillStrong)) {
                Box(Modifier.fillMaxWidth(0.32f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Brush.linearGradient(listOf(LiquidBlue, Color(0xFFBCA8FF)))))
            }
        }
    }
}

@Composable
private fun OriginalTag(text: String, onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = if (pressed) .97f else 1f; scaleY = if (pressed) .97f else 1f }
                .clip(RoundedCornerShape(999.dp))
                .background(LiquidFillSoft)
                .border(1.dp, LiquidLine, RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun ToolRow(icon: String, title: String, desc: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LiquidIconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SettingGroup(icon: String, title: String, desc: String) {
    OriginalGlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), corner = 27.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidIconBox(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(desc, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text("›", color = Muted, fontSize = 30.sp)
        }
    }
}

@Composable
private fun OriginalBottomNav(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    OriginalGlassPanel(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
        corner = 34.dp,
        padding = PaddingValues(8.dp),
        fill = Color.White.copy(alpha = .070f)
    ) {
        Row(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTab.entries.forEach { tab -> OriginalNavItem(Modifier.weight(1f), tab, currentTab == tab) { onTabSelected(tab) } }
        }
    }
}

@Composable
private fun OriginalNavItem(modifier: Modifier, tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .height(76.dp)
                .graphicsLayer { scaleX = if (pressed) .965f else 1f; scaleY = if (pressed) .965f else 1f }
                .clip(shape)
                .background(if (selected) Brush.linearGradient(listOf(Color(0xFFE9F8FF).copy(alpha = .92f), Color(0xFF9DEEFF).copy(alpha = .70f), Color(0xFFBCA8FF).copy(alpha = .34f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                .border(if (selected) 1.dp else 0.dp, Color.White.copy(alpha = if (selected) .38f else 0f), shape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tab.icon, color = if (selected) InkDark else Muted, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(tab.label, color = if (selected) InkDark else Muted, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun OriginalGlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    fill: Color = LiquidFill,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(1.dp, LiquidLine, shape)
    ) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), center = Offset(90f, 0f), radius = 300f)))
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color(0xFFBCA8FF).copy(alpha = .06f)))))
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun OriginalSmallPill(text: String, onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = if (pressed) .97f else 1f; scaleY = if (pressed) .97f else 1f }
                .clip(RoundedCornerShape(999.dp))
                .background(LiquidFillSoft)
                .border(1.dp, LiquidLineStrong, RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun OriginalStatusPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFFF728A).copy(alpha = .12f))
            .border(1.dp, Color(0xFFFF728A).copy(alpha = .45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text("●  $text", color = Color(0xFFFFC1CA), fontSize = 13.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun OriginalMiniButton(text: String, onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = if (pressed) .96f else 1f; scaleY = if (pressed) .96f else 1f }
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = .060f))
                .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun OriginalAccentButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LiquidBlue, contentColor = InkDark),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(text, fontWeight = FontWeight.Black) }
}

@Composable
private fun OriginalRoundButton(text: String, size: Dp, onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = if (pressed) .94f else 1f; scaleY = if (pressed) .94f else 1f }
                .clip(CircleShape)
                .background(LiquidFillStrong)
                .border(1.dp, LiquidLineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun OriginalSendButton(onClick: () -> Unit) {
    LiquidPressable(onClick) { pressed ->
        Box(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer { scaleX = if (pressed) .94f else 1f; scaleY = if (pressed) .94f else 1f }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFE9F8FF).copy(alpha = .70f), Color(0xFFBCA8FF).copy(alpha = .36f))))
                .border(1.dp, Color.White.copy(alpha = .34f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("➤", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun LiquidIconBox(icon: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(LiquidFillSoft)
            .border(1.dp, LiquidLine, RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) { Text(icon, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink) }
}

@Composable
private fun LiquidPressable(onClick: () -> Unit, content: @Composable (pressed: Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)) { content(pressed) }
}

@Composable
private fun OriginalLiquidBackdrop() {
    val transition = rememberInfiniteTransition(label = "original-web-liquid-backdrop")
    val driftA by transition.animateFloat(-12f, 18f, infiniteRepeatable(tween(26000), RepeatMode.Reverse), label = "glass-backdrop")
    val driftB by transition.animateFloat(14f, -18f, infiniteRepeatable(tween(32000), RepeatMode.Reverse), label = "aurora-flow")
    Box(Modifier.fillMaxSize().background(OriginalBackgroundBrush)) {
        LiquidOrb(Modifier.align(Alignment.TopStart).offset(x = (-40 + driftA).dp, y = 88.dp), 190.dp, Color(0xFF84B4FF).copy(alpha = .18f))
        LiquidOrb(Modifier.align(Alignment.TopEnd).offset(x = 54.dp, y = (70 + driftB).dp), 220.dp, Color(0xFFAE78FF).copy(alpha = .16f))
        LiquidOrb(Modifier.align(Alignment.BottomEnd).offset(x = 22.dp, y = (-130 + driftA).dp), 250.dp, Color(0xFF4CE8FF).copy(alpha = .12f))
        LiquidOrb(Modifier.align(Alignment.BottomStart).offset(x = (-76).dp, y = (-20 + driftB).dp), 240.dp, Color(0xFFCF67FF).copy(alpha = .10f))
    }
}

@Composable
private fun LiquidOrb(modifier: Modifier, size: Dp, color: Color) {
    Box(modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * .38f), Color.Transparent))))
}

private val DeepNavy = Color(0xFF070A18)
private val Ink = Color(0xF5F8FAFF)
private val InkDark = Color(0xFF061428)
private val Muted = Color(0xA6D6E0F6)
private val LiquidBlue = Color(0xFF73E7FF)
private val GlassSurface = Color(0x141E2A44)
private val LiquidFill = Color.White.copy(alpha = .055f)
private val LiquidFillSoft = Color.White.copy(alpha = .035f)
private val LiquidFillStrong = Color.White.copy(alpha = .090f)
private val LiquidLine = Color.White.copy(alpha = .24f)
private val LiquidLineStrong = Color.White.copy(alpha = .34f)
private val OriginalBackgroundBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF070A18), Color(0xFF0D1434), Color(0xFF17113A), Color(0xFF271E45))
)
