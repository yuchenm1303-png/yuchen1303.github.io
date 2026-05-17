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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
            bottomBar = { FloatingGlassBottomNav(currentTab) { tabName = it.name } }
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
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TopControls {
            messages.clear()
            messages += ChatMessage(nextId++, MessageRole.Assistant, "对话已清空。", "compose_native")
        }
        GlassPanel(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            corner = 32.dp,
            padding = PaddingValues(14.dp),
            fill = LiquidFill
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelStatusStrip()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                QuickTags(
                    onAlarm = { input = "明天早上8点叫我起床" },
                    onNav = { input = "导航回家" },
                    onLedger = { input = "今天午饭28" }
                )
                AnimatedVisibility(plusOpen) {
                    AttachmentPanel { label ->
                        plusOpen = false
                        Toast.makeText(context, "$label 功能稍后接入", Toast.LENGTH_SHORT).show()
                    }
                }
                Composer(
                    value = input,
                    onValueChange = { input = it },
                    onPlus = { plusOpen = !plusOpen },
                    onSend = {
                        val clean = input.trim()
                        if (clean.isEmpty()) return@Composer
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
        item { PageHeader("功能中心", "工具与能力", "这一版先重点看底部导航玻璃岛质感。") }
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
        item { InfoCard("账号与云同步", "Worker 连接、模型选择、云端状态将在这里迁入。", "☁") }
        item { InfoCard("显示与语言", "语言 · 字体大小 · 玻璃透明度 · 动画效果 · 紧凑模式", "Aa") }
        item { InfoCard("数据与预算", "预算、聊天记录、账单导出、清空数据。", "▤") }
    }
}

@Composable
private fun TopControls(onClear: () -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.Start) {
        SmallPill("清空对话", onClear)
        SmallPill("🌐  自动联网  ●") {}
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
        GlassPanel(Modifier.weight(1f), corner = 28.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), fill = LiquidFillStrong) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF9DEEFF), Color(0xFF8F7DFF)))),
                    contentAlignment = Alignment.Center
                ) { Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) }
                Text("Mistral", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        GlassPanel(Modifier.weight(1.7f), corner = 28.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), fill = LiquidFillSoft) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("✦  轻量待命", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onExecuteCommand: (AssistantCommand) -> Unit, onRetry: () -> Unit, onCopy: () -> Unit) {
    val isUser = message.role == MessageRole.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(if (isUser) 0.74f else 0.90f), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (isUser) UserBubble(message.content) else AiBubble(message.content)
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
                StatusPill(message.actionHint)
                if (message.actionHint.contains("云端")) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniButton("重试", onRetry)
                        MiniButton("复制", onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4C6CFF).copy(alpha = .88f), Color(0xFF7A4CE1).copy(alpha = .86f))))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) { Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp) }
}

@Composable
private fun AiBubble(text: String) {
    GlassPanel(corner = 24.dp, padding = PaddingValues(horizontal = 15.dp, vertical = 13.dp), fill = LiquidFillSoft) {
        Text(text, color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun CommandCard(command: AssistantCommand, onExecute: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), corner = 20.dp, padding = PaddingValues(12.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(command.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(command.description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            AccentButton(command.primaryActionLabel, onExecute)
        }
    }
}

@Composable
private fun LedgerDraftCard(title: String, amount: Double, category: String) {
    GlassPanel(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(12.dp), fill = LiquidFillStrong) {
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

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onPlus: () -> Unit, onSend: () -> Unit) {
    GlassPanel(corner = 32.dp, padding = PaddingValues(8.dp), fill = LiquidFillSoft) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton("+", 52.dp, onPlus)
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
            SendButton(onSend)
        }
    }
}

@Composable
private fun QuickTags(onAlarm: () -> Unit, onNav: () -> Unit, onLedger: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Tag("设提醒", onAlarm)
        Tag("导航回家", onNav)
        Tag("记一笔", onLedger)
    }
}

@Composable
private fun AttachmentPanel(onPick: (String) -> Unit) {
    GlassPanel(corner = 26.dp, padding = PaddingValues(12.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("快捷添加", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniButton("拍照") { onPick("拍照") }
                MiniButton("语音") { onPick("语音") }
                MiniButton("账单") { onPick("账单") }
                MiniButton("文件") { onPick("文件") }
            }
        }
    }
}

@Composable
private fun FeatureHeroCard() {
    GlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFill) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI 原生能力面板", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("这里开始承接原网页功能页：账单、统计、闹钟、导航、打开 App、导出数据。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ToolTile(icon: String, title: String, desc: String, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, corner = 28.dp, padding = PaddingValues(14.dp), fill = LiquidFillSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBox(icon)
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, desc: String, icon: String) {
    GlassPanel(corner = 32.dp, padding = PaddingValues(16.dp), fill = LiquidFill) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(desc, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun Tag(text: String, onClick: () -> Unit) {
    Pressable(onClick) { pressed ->
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
private fun GlassPanel(modifier: Modifier = Modifier, corner: Dp = 28.dp, padding: PaddingValues = PaddingValues(16.dp), fill: Color = LiquidFill, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier.shadow(18.dp, shape, clip = false).clip(shape).background(fill).border(1.dp, LiquidLine, shape)
    ) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), center = Offset(90f, 0f), radius = 300f)))
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color(0xFFBCA8FF).copy(alpha = .06f)))))
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) { MiniButton(text, onClick, horizontal = 18.dp, vertical = 12.dp, fontSize = 15) }

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFF728A).copy(alpha = .12f)).border(1.dp, Color(0xFFFF728A).copy(alpha = .45f), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text("●  $text", color = Color(0xFFFFC1CA), fontSize = 13.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit, horizontal: Dp = 15.dp, vertical: Dp = 9.dp, fontSize: Int = 13) {
    Pressable(onClick) { pressed ->
        Box(
            modifier = Modifier.graphicsLayer { scaleX = if (pressed) .96f else 1f; scaleY = if (pressed) .96f else 1f }.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = .060f)).border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(999.dp)).padding(horizontal = horizontal, vertical = vertical),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = fontSize.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquidBlue, contentColor = InkDark), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RoundButton(text: String, size: Dp, onClick: () -> Unit) {
    Pressable(onClick) { pressed ->
        Box(
            modifier = Modifier.size(size).graphicsLayer { scaleX = if (pressed) .94f else 1f; scaleY = if (pressed) .94f else 1f }.clip(CircleShape).background(LiquidFillStrong).border(1.dp, LiquidLineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(text, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun SendButton(onClick: () -> Unit) {
    Pressable(onClick) { pressed ->
        Box(
            modifier = Modifier.size(54.dp).graphicsLayer { scaleX = if (pressed) .94f else 1f; scaleY = if (pressed) .94f else 1f }.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFE9F8FF).copy(alpha = .70f), Color(0xFFBCA8FF).copy(alpha = .36f)))).border(1.dp, Color.White.copy(alpha = .34f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("➤", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun IconBox(icon: String) {
    Box(
        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(17.dp)).background(LiquidFillSoft).border(1.dp, LiquidLine, RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) { Text(icon, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink) }
}

@Composable
private fun Pressable(onClick: () -> Unit, content: @Composable (pressed: Boolean) -> Unit) {
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
private val OriginalBackgroundBrush = Brush.linearGradient(listOf(Color(0xFF070A18), Color(0xFF0D1434), Color(0xFF17113A), Color(0xFF271E45)))
