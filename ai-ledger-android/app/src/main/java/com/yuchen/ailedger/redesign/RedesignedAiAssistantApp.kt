package com.yuchen.ailedger.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class MainTab(
    val title: String,
    val subtitle: String,
    val icon: String
) {
    Home("首页", "总览", "⌂"),
    Chat("聊天", "AI 助手", "✦"),
    Tools("功能", "手机能力", "▦"),
    Settings("设置", "偏好", "⚙")
}

private data class QuickAction(
    val title: String,
    val desc: String,
    val tag: String
)

private data class SettingGroup(
    val title: String,
    val desc: String
)

@Composable
fun RedesignedAiAssistantApp() {
    var selectedTab by remember { mutableStateOf(MainTab.Home) }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2563EB),
            background = Color(0xFFF5F7FB),
            surface = Color.White,
            onSurface = Color(0xFF101828)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F7FB)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEAF1FF),
                                Color(0xFFF8FAFC),
                                Color(0xFFF5F7FB)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    AppHeader(selectedTab)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            MainTab.Home -> HomeScreen(onOpenChat = { selectedTab = MainTab.Chat })
                            MainTab.Chat -> ChatScreen()
                            MainTab.Tools -> ToolsScreen()
                            MainTab.Settings -> SettingsScreen()
                        }
                    }
                    BottomNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(selectedTab: MainTab) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2563EB)),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedTab.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828)
            )
            Text(
                text = selectedTab.subtitle,
                fontSize = 13.sp,
                color = Color(0xFF667085)
            )
        }
        StatusChip(text = "Native")
    }
}

@Composable
private fun HomeScreen(onOpenChat: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroAssistantCard(onOpenChat)
        }
        item {
            SectionTitle("常用入口", "先把入口分清，后面再逐步接 Web 版功能")
        }
        item {
            QuickActionGrid(
                actions = listOf(
                    QuickAction("记账分析", "查看收支、预算和账单总结", "账本"),
                    QuickAction("手机指令", "闹钟、日程、应用跳转等能力", "控制"),
                    QuickAction("智能建议", "根据上下文给出下一步操作", "AI"),
                    QuickAction("同步中心", "账号、云端与本地数据状态", "同步")
                )
            )
        }
        item {
            SectionTitle("今天概览", "保留信息层级，不再把所有内容堆在一屏")
        }
        item {
            OverviewList()
        }
    }
}

@Composable
private fun HeroAssistantCard(onOpenChat: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            StatusChip(text = "AI 助手已就绪", dark = true)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "把复杂操作变成一句话",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "先以聊天为核心，再逐步接入记账、设置、手机控制和同步能力。",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenChat,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF172554))
            ) {
                Text("开始对话", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuickActionGrid(actions: List<QuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { action ->
                    QuickActionCard(
                        action = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusChip(text = action.tag)
            Spacer(Modifier.height(14.dp))
            Text(action.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101828))
            Spacer(Modifier.height(5.dp))
            Text(action.desc, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF667085))
        }
    }
}

@Composable
private fun OverviewList() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewItem("聊天入口", "主任务入口固定在底部第二项，避免首页塞满输入框。", "高优先级")
        OverviewItem("功能中心", "把系统能力、Web 功能迁移入口统一放到功能页。", "规划中")
        OverviewItem("设置结构", "账号、显示、手机偏好、数据预算分组展示。", "已重排")
    }
}

@Composable
private fun OverviewItem(title: String, desc: String, state: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Text("•", color = Color(0xFF2563EB), fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101828))
            Text(desc, fontSize = 12.sp, color = Color(0xFF667085), lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(text = state)
    }
}

@Composable
private fun ChatScreen() {
    var input by remember { mutableStateOf("") }
    val messages = listOf(
        "你好，我可以帮你记账、总结信息，也可以逐步接入手机控制能力。",
        "现在界面已按首页、聊天、功能、设置四个主层级重新整理。"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("和 AI 说点什么…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { input = "" },
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("发送")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MessageBubble(message: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF2563EB)),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(15.dp)
        ) {
            Text(message, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF101828))
        }
    }
}

@Composable
private fun ToolsScreen() {
    val tools = listOf(
        QuickAction("Web 功能迁移", "把旧版 ai-ledger 的功能按模块接入原生页面", "迁移"),
        QuickAction("手机控制", "先从闹钟、日程、打开应用等安全 Intent 开始", "Android"),
        QuickAction("数据分析", "预算、账单、消费趋势等统计入口", "数据"),
        QuickAction("助手技能", "为常用指令建立清晰的技能卡片", "技能")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("功能中心", "所有能力统一放在这里，不再散落在首页") }
        item { QuickActionGrid(tools) }
        item { SectionTitle("接入顺序", "先做低风险入口，再做深层系统能力") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewItem("第一步", "聊天页和首页入口稳定下来。", "当前")
                OverviewItem("第二步", "接设置、预算、同步等 Web 版成熟功能。", "下一步")
                OverviewItem("第三步", "接安卓 Intent、权限和无障碍能力。", "后续")
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val groups = listOf(
        SettingGroup("账号与同步", "登录状态、云端同步、本地备份"),
        SettingGroup("显示与语言", "语言、字体大小、紧凑模式"),
        SettingGroup("手机偏好", "权限、默认操作、系统能力开关"),
        SettingGroup("背景外观", "主题背景、颜色、卡片风格"),
        SettingGroup("数据与预算", "账本数据、预算提醒、导出设置")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("设置", "保留 Web 版分组，但改成原生清单结构") }
        items(groups) { group ->
            SettingGroupRow(group)
        }
    }
}

@Composable
private fun SettingGroupRow(group: SettingGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101828))
            Spacer(Modifier.height(4.dp))
            Text(group.desc, fontSize = 13.sp, color = Color(0xFF667085))
        }
        Text("›", fontSize = 28.sp, color = Color(0xFF98A2B3))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101828))
        Spacer(Modifier.height(3.dp))
        Text(subtitle, fontSize = 12.sp, color = Color(0xFF667085))
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE4E7EC), RoundedCornerShape(26.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MainTab.values().forEach { tab ->
            val selected = tab == selectedTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Color(0xFFEFF6FF) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tab.icon, fontSize = 17.sp, color = if (selected) Color(0xFF2563EB) else Color(0xFF98A2B3))
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.title,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color(0xFF2563EB) else Color(0xFF667085),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, dark: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFFEFF6FF))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (dark) Color.White else Color(0xFF2563EB)
        )
    }
}
