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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var currentTabName by rememberSaveable { mutableStateOf(AppTab.Chat.name) }
    val currentTab = AppTab.valueOf(currentTabName)

    Box(Modifier.fillMaxSize().background(NativeBackdropBrush)) {
        NativeBackdropDecor()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (currentTab) {
                AppTab.Chat -> NativeChatPage(Modifier.weight(1f))
                AppTab.Tools -> NativeToolsPage(Modifier.weight(1f))
                AppTab.Settings -> NativeSettingsPage(Modifier.weight(1f))
            }

            FloatingGlassBottomNav(
                currentTab = currentTab,
                onTabSelected = { currentTabName = it.name }
            )
        }
    }
}

@Composable
private fun NativeChatPage(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewPill("清空对话")
            PreviewPill("🌐  强制联网  ●", accent = true)
        }

        NativeGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            corner = 34.dp,
            padding = PaddingValues(16.dp),
            fill = Color.White.copy(alpha = 0.078f)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                NativeModelHeader(model = "Workers", state = "轻量待命")

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    item {
                        UserPreviewBubble("tackle是什么意思")
                    }
                    item {
                        NativeAiBubble(
                            "tackle 可以表示“处理、应对、着手解决”，也可以表示橄榄球里的“擒抱”。例如 tackle a problem 就是处理一个问题。"
                        )
                    }
                    item { PreviewStatusPill("联网总结 · orch-v9-nvidia-model-split · Workers AI Llama 3.1 8B") }
                    item {
                        NativeAiBubble(
                            "这个页面已经脱离 WebView，后续会把旧版聊天状态、复制、重试、命令卡片、联网结果卡片逐步迁移到这里。"
                        )
                    }
                    item { PreviewErrorPill("云端连接失败 · cloud-error-normalizer-v4") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PreviewPill("设提醒")
                    PreviewPill("导航回家")
                    PreviewPill("记一笔")
                }

                NativeComposerBar()
            }
        }
    }
}

@Composable
private fun NativeToolsPage(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 2.dp)
    ) {
        item { NativePageTitle("功能", "原生 Compose 功能页", "账单、统计、提醒、导航和手机动作会逐步从旧版迁过来。") }
        item {
            NativeGlassPanel(corner = 34.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.075f)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 能力面板", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("当前是迁移预览：先恢复旧版结构，再逐步接入本地 Intent、账单数据和云端 Worker。", color = Color(0xCCDDE7FF), fontSize = 13.sp, lineHeight = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewStatusPill("本地命令路由")
                        PreviewStatusPill("云端 Worker")
                    }
                }
            }
        }
        item { NativeToolGrid() }
        item {
            NativeGlassPanel(corner = 30.dp, padding = PaddingValues(14.dp), fill = Color.White.copy(alpha = 0.055f)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NativeToolRow("◎", "打开常用 App", "微信、支付宝、淘宝、QQ 等应用唤起会接回 Android Intent。")
                    NativeToolRow("⌖", "导航与位置", "家庭地址、默认地图、geo 链接和路线规划。")
                    NativeToolRow("⇪", "导出与备份", "账单 JSON、聊天记录、设置备份。")
                }
            }
        }
    }
}

@Composable
private fun NativeSettingsPage(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 2.dp)
    ) {
        item { NativePageTitle("设置", "原生 Compose 设置页", "先迁移设置分组和玻璃层级，再接具体配置逻辑。") }
        item { NativeSettingHero() }
        item { NativeSettingCard("☁", "账号与同步", "登录、注册、AI 接口、Worker 测试和云同步。") }
        item { NativeSettingCard("Aa", "显示与语言", "语言、字体大小、玻璃透明度、动画效果、紧凑模式。") }
        item { NativeSettingCard("⌖", "手机偏好", "家庭地址、默认地图、常用应用、本地动作权限。") }
        item { NativeSettingCard("✦", "背景外观", "天气星空、翡翠海雾、蓝紫雾面、动态光斑。") }
        item { NativeSettingCard("▤", "数据与预算", "预算、账单、聊天记录、导入导出、清空数据。") }
    }
}

@Composable
private fun NativeModelHeader(model: String, state: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        NativeGlassPanel(Modifier.weight(1f), corner = 28.dp, padding = PaddingValues(12.dp), fill = Color.White.copy(alpha = 0.105f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF9DEEFF), Color(0xFF9180FF)))), contentAlignment = Alignment.Center) {
                    Text("AI", color = Color.White, fontWeight = FontWeight.Black)
                }
                Text(model, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        NativeGlassPanel(Modifier.weight(1.55f), corner = 28.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.055f)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("✦ $state", color = Color(0xCCDFE7FF), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun UserPreviewBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4C6CFF).copy(alpha = 0.88f), Color(0xFF7A4CE1).copy(alpha = 0.86f))))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun NativeAiBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        NativeGlassPanel(Modifier.fillMaxWidth(0.92f), corner = 26.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.065f)) {
            Text(text, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun NativeComposerBar() {
    NativeGlassPanel(corner = 32.dp, padding = PaddingValues(8.dp), fill = Color.White.copy(alpha = 0.064f)) {
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

@Composable
private fun NativePageTitle(eyebrow: String, title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = Color(0xFF73E7FF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(desc, color = Color(0xB8D6E0F6), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun NativeToolGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NativeToolTile("▤", "账单中心", "记录 / 分类", Modifier.weight(1f))
            NativeToolTile("▣", "数据统计", "趋势 / 占比", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NativeToolTile("⏰", "提醒闹钟", "系统动作", Modifier.weight(1f))
            NativeToolTile("◎", "应用控制", "打开 App", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NativeToolTile("⌁", "快捷指令", "常用任务", Modifier.weight(1f))
            NativeToolTile("⇪", "导出数据", "备份 / 分享", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NativeToolTile(icon: String, title: String, desc: String, modifier: Modifier = Modifier) {
    NativeGlassPanel(modifier = modifier, corner = 28.dp, padding = PaddingValues(14.dp), fill = Color.White.copy(alpha = 0.058f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NativeIconBox(icon)
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Color(0xB8D6E0F6), fontSize = 12.sp)
        }
    }
}

@Composable
private fun NativeToolRow(icon: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NativeIconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Color(0xB8D6E0F6), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun NativeSettingHero() {
    NativeGlassPanel(corner = 34.dp, padding = PaddingValues(16.dp), fill = Color.White.copy(alpha = 0.075f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设置总览", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("这里会承接旧版设置页的五大分组。下一步把每个分组的详情弹窗改成 Compose 原生。", color = Color(0xCCDDE7FF), fontSize = 13.sp, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPill("中文")
                PreviewPill("中等字体")
                PreviewPill("流畅动画")
            }
        }
    }
}

@Composable
private fun NativeSettingCard(icon: String, title: String, desc: String) {
    NativeGlassPanel(corner = 28.dp, padding = PaddingValues(14.dp), fill = Color.White.copy(alpha = 0.056f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NativeIconBox(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(desc, color = Color(0xB8D6E0F6), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text("›", color = Color(0x99DDE7FF), fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NativeIconBox(icon: String) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
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
private fun PreviewPill(text: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (accent) Color(0xFF55F2C8).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, if (accent) Color(0xFF55F2C8).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
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
private fun PreviewErrorPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFFF728A).copy(alpha = 0.13f))
            .border(1.dp, Color(0xFFFF728A).copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("● $text", color = Color(0xFFFFC1CA), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NativeBackdropDecor() {
    Box(Modifier.fillMaxSize()) {
        PreviewOrb(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 18.dp), 260.dp, Color(0xFF68D8FF).copy(alpha = 0.18f))
        PreviewOrb(Modifier.align(Alignment.CenterEnd).padding(end = 0.dp), 280.dp, Color(0xFF2F72FF).copy(alpha = 0.12f))
        PreviewOrb(Modifier.align(Alignment.BottomStart).padding(start = 0.dp, bottom = 78.dp), 310.dp, Color(0xFFB783FF).copy(alpha = 0.16f))
        PreviewStars()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent, Color(0xFF2B1648).copy(alpha = 0.14f)))))
    }
}

@Composable
private fun PreviewOrb(modifier: Modifier, size: Dp, color: Color) {
    Box(modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.36f), Color.Transparent))))
}

@Composable
private fun PreviewStars() {
    Box(Modifier.fillMaxSize()) {
        repeat(32) { i ->
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
