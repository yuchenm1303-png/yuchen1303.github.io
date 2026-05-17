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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab

@Composable
fun NativeLiquidPreviewScreen() {
    var currentTabName by rememberSaveable { mutableStateOf(AppTab.Chat.name) }
    val currentTab = AppTab.valueOf(currentTabName)

    Box(Modifier.fillMaxSize().background(LiquidPreviewBackdrop)) {
        LiquidPreviewBackdropDecor()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (currentTab) {
                AppTab.Chat -> LiquidChatPage(Modifier.weight(1f))
                AppTab.Tools -> LiquidToolsPage(Modifier.weight(1f))
                AppTab.Settings -> LiquidSettingsPage(Modifier.weight(1f))
            }
            FloatingGlassBottomNav(currentTab = currentTab, onTabSelected = { currentTabName = it.name })
        }
    }
}

@Composable
private fun LiquidChatPage(modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidChipText("清空对话")
            LiquidChipText("🌐  强制联网  ●", Color(0xFF62F2D0))
        }

        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            corner = 34.dp,
            padding = PaddingValues(16.dp),
            strength = GlassStrength.Strong
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                LiquidModelHeader()
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    item { LiquidUserBubble("tackle是什么意思") }
                    item { LiquidAiBubble("tackle 可以表示“处理、应对、着手解决”，也可以表示橄榄球里的“擒抱”。例如 tackle a problem 就是处理一个问题。") }
                    item { LiquidStatusText("联网总结 · orch-v9-nvidia-model-split · Workers AI Llama 3.1 8B", Color(0xFF62F2D0)) }
                    item { LiquidAiBubble("这一页是 Compose 原生液态玻璃预览。旧版 WebView 继续作为基准，但这里开始用原生材质层模拟厚玻璃、高光、透色和内边折射。") }
                    item { LiquidStatusText("云端连接失败 · cloud-error-normalizer-v4", Color(0xFFFF8DA0)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LiquidChipText("设提醒")
                    LiquidChipText("导航回家")
                    LiquidChipText("记一笔")
                }
                LiquidComposerBar()
            }
        }
    }
}

@Composable
private fun LiquidToolsPage(modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LiquidPageTitle("功能", "原生液态玻璃功能页", "先把视觉材质统一，再逐步接账单、统计、提醒和手机动作。") }
        item {
            LiquidGlassSurface(corner = 34.dp, strength = GlassStrength.Strong) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 能力面板", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("这张卡片使用新的 LiquidGlassSurface：顶部高光、内边线、紫蓝透色和底部暗边统一由材质组件控制。", color = Color(0xCCDDE7FF), fontSize = 13.sp, lineHeight = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidChipText("本地命令")
                        LiquidChipText("云端 Worker", Color(0xFF62F2D0))
                    }
                }
            }
        }
        item { LiquidToolGrid() }
        item {
            LiquidGlassSurface(corner = 30.dp, strength = GlassStrength.Medium) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidRow("◎", "打开常用 App", "微信、支付宝、淘宝、QQ 等应用唤起。")
                    LiquidRow("⌖", "导航与位置", "家庭地址、默认地图、geo 链接和路线规划。")
                    LiquidRow("⇪", "导出与备份", "账单 JSON、聊天记录、设置备份。")
                }
            }
        }
    }
}

@Composable
private fun LiquidSettingsPage(modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LiquidPageTitle("设置", "原生液态玻璃设置页", "设置分组先统一成同一种玻璃材质，后面再迁详情弹窗。") }
        item {
            LiquidGlassSurface(corner = 34.dp, strength = GlassStrength.Strong) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("设置总览", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("旧版的账号与同步、显示与语言、手机偏好、背景外观、数据与预算会逐项翻译成 Compose。", color = Color(0xCCDDE7FF), fontSize = 13.sp, lineHeight = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidChipText("中文")
                        LiquidChipText("中等字体")
                        LiquidChipText("流畅动画")
                    }
                }
            }
        }
        item { LiquidSettingCard("☁", "账号与同步", "登录、注册、AI 接口、Worker 测试和云同步。") }
        item { LiquidSettingCard("Aa", "显示与语言", "语言、字体大小、玻璃透明度、动画效果、紧凑模式。") }
        item { LiquidSettingCard("⌖", "手机偏好", "家庭地址、默认地图、常用应用、本地动作权限。") }
        item { LiquidSettingCard("✦", "背景外观", "天气星空、翡翠海雾、蓝紫雾面、动态光斑。") }
        item { LiquidSettingCard("▤", "数据与预算", "预算、账单、聊天记录、导入导出、清空数据。") }
    }
}

@Composable
private fun LiquidModelHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidGlassSurface(Modifier.weight(1f), corner = 28.dp, padding = PaddingValues(12.dp), strength = GlassStrength.Strong) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFFBFF6FF), Color(0xFF9180FF)))), contentAlignment = Alignment.Center) {
                    Text("AI", color = Color.White, fontWeight = FontWeight.Black)
                }
                Text("Workers", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LiquidGlassSurface(Modifier.weight(1.55f), corner = 28.dp, padding = PaddingValues(16.dp), strength = GlassStrength.Soft) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("✦ 轻量待命", color = Color(0xCCDFE7FF), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LiquidUserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4C6CFF).copy(alpha = 0.92f), Color(0xFF7A4CE1).copy(alpha = 0.88f))))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp) }
    }
}

@Composable
private fun LiquidAiBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        LiquidGlassSurface(Modifier.fillMaxWidth(0.92f), corner = 26.dp, padding = PaddingValues(16.dp), strength = GlassStrength.Medium) {
            Text(text, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun LiquidComposerBar() {
    LiquidGlassSurface(corner = 32.dp, padding = PaddingValues(8.dp), strength = GlassStrength.Strong) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.34f), CircleShape), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Text("和我说点什么", color = Color(0x9AE5EDFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color(0xFFBCA8FF).copy(alpha = 0.36f)))).border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape), contentAlignment = Alignment.Center) {
                Text("➤", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LiquidChipText(text: String, accent: Color? = null) {
    LiquidGlassChip(accent = accent) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiquidStatusText(text: String, color: Color) {
    LiquidGlassChip(accent = color) {
        Text("● $text", color = color.copy(alpha = 0.95f), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiquidPageTitle(eyebrow: String, title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = Color(0xFF73E7FF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(desc, color = Color(0xB8D6E0F6), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun LiquidToolGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidTile("▤", "账单中心", "记录 / 分类", Modifier.weight(1f))
            LiquidTile("▣", "数据统计", "趋势 / 占比", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidTile("⏰", "提醒闹钟", "系统动作", Modifier.weight(1f))
            LiquidTile("◎", "应用控制", "打开 App", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LiquidTile("⌁", "快捷指令", "常用任务", Modifier.weight(1f))
            LiquidTile("⇪", "导出数据", "备份 / 分享", Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiquidTile(icon: String, title: String, desc: String, modifier: Modifier) {
    LiquidGlassSurface(modifier = modifier, corner = 28.dp, padding = PaddingValues(14.dp), strength = GlassStrength.Medium) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidIconBox(icon)
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Color(0xB8D6E0F6), fontSize = 12.sp)
        }
    }
}

@Composable
private fun LiquidRow(icon: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiquidIconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(desc, color = Color(0xB8D6E0F6), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LiquidSettingCard(icon: String, title: String, desc: String) {
    LiquidGlassSurface(corner = 28.dp, padding = PaddingValues(14.dp), strength = GlassStrength.Medium) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidIconBox(icon)
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
private fun LiquidIconBox(icon: String) {
    LiquidGlassSurface(corner = 17.dp, padding = PaddingValues(0.dp), strength = GlassStrength.Soft, modifier = Modifier.size(46.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(icon, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LiquidPreviewBackdropDecor() {
    Box(Modifier.fillMaxSize()) {
        PreviewOrb(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 18.dp), 270.dp, Color(0xFF68D8FF).copy(alpha = 0.20f))
        PreviewOrb(Modifier.align(Alignment.CenterEnd), 320.dp, Color(0xFF2F72FF).copy(alpha = 0.15f))
        PreviewOrb(Modifier.align(Alignment.BottomStart).padding(bottom = 80.dp), 340.dp, Color(0xFFB783FF).copy(alpha = 0.18f))
        PreviewOrb(Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp), 240.dp, Color(0xFFFF6FD8).copy(alpha = 0.10f))
        PreviewStars()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.040f), Color.Transparent, Color(0xFF2B1648).copy(alpha = 0.16f)))))
    }
}

@Composable
private fun PreviewOrb(modifier: Modifier, size: androidx.compose.ui.unit.Dp, color: Color) {
    Box(modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.36f), Color.Transparent))))
}

@Composable
private fun PreviewStars() {
    Box(Modifier.fillMaxSize()) {
        repeat(34) { i ->
            Box(
                Modifier
                    .padding(start = ((i * 41) % 360).dp, top = ((i * 73) % 760).dp)
                    .size(if (i % 5 == 0) 2.dp else 1.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i % 5 == 0) 0.30f else 0.15f))
            )
        }
    }
}

private val LiquidPreviewBackdrop = Brush.linearGradient(
    listOf(
        Color(0xFF040711),
        Color(0xFF08142F),
        Color(0xFF112A52),
        Color(0xFF211C4E),
        Color(0xFF3A1F55)
    )
)
