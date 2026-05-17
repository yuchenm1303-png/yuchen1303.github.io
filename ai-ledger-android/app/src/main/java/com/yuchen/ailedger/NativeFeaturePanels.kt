package com.yuchen.ailedger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NativeFeatureCard(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val badge: String = "Native",
)

@Composable
fun NativeToolsPanel(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        NativeFeatureCard("ledger", "▤", "账单中心", "查看记录、分类和导出数据"),
        NativeFeatureCard("stats", "▣", "数据统计", "收支总览、趋势和分类结构"),
        NativeFeatureCard("alarm", "⏰", "提醒闹钟", "通过原生系统闹钟执行"),
        NativeFeatureCard("apps", "◎", "应用控制", "打开微信、支付宝、地图等应用"),
        NativeFeatureCard("shortcuts", "⌁", "快捷指令", "沉淀常用手机动作"),
        NativeFeatureCard("tasks", "✓", "任务记录", "查看动作卡片和执行历史"),
    )

    NativePageSurface(
        eyebrow = "原生功能中心",
        title = "工具与能力",
        subtitle = "这一页已经由 Compose 接管，WebView 只保留后台能力。",
        modifier = modifier,
    ) {
        items(cards, key = { it.id }) { card ->
            NativeFeatureTile(card = card, onClick = { onAction(card.id) })
        }
    }
}

@Composable
fun NativeSettingsPanel(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        NativeFeatureCard("account", "◉", "账号与同步", "登录状态、云同步和本地模式", "Soon"),
        NativeFeatureCard("display", "Aa", "显示与语言", "语言、字体、动画和紧凑模式", "Native"),
        NativeFeatureCard("phone", "▧", "手机偏好", "地图、常用地址、系统动作偏好", "Native"),
        NativeFeatureCard("appearance", "✦", "背景外观", "原生玻璃强度和背景层", "Native"),
        NativeFeatureCard("budget", "¥", "数据与预算", "预算、导出、清空与同步", "Soon"),
    )

    NativePageSurface(
        eyebrow = "原生设置中心",
        title = "应用设置",
        subtitle = "高频设置页先接入原生外壳，具体表单逐步迁移。",
        modifier = modifier,
    ) {
        items(cards, key = { it.id }) { card ->
            NativeFeatureTile(card = card, onClick = { onAction(card.id) })
        }
    }
}

@Composable
private fun NativePageSurface(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(top = 82.dp, bottom = 92.dp)
            .shadow(12.dp, RoundedCornerShape(30.dp), clip = false),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.035f),
                            Color(0x12000000),
                        ),
                    ),
                )
                .padding(14.dp),
        ) {
            Text(
                text = eyebrow,
                color = Color(0xFF8BF7FF).copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun NativeFeatureTile(
    card: NativeFeatureCard,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.105f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.035f),
                            Color(0x126AD7FF),
                        ),
                    ),
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .padding(end = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = card.icon,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = card.subtitle,
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            ) {
                Text(
                    text = card.badge,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
