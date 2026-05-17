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
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private data class NativeDetailInfo(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val rows: List<Pair<String, String>>,
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
fun NativeDetailPanel(
    detailId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = detailInfo(detailId)
    NativePageSurface(
        eyebrow = info.eyebrow,
        title = info.title,
        subtitle = info.subtitle,
        modifier = modifier,
        headerAction = {
            NativeSmallButton(text = "返回", onClick = onBack)
        },
    ) {
        items(info.rows, key = { it.first }) { row ->
            NativeDetailRow(title = row.first, subtitle = row.second)
        }
        item {
            when (detailId) {
                "display", "appearance" -> NativeGlassControls()
                "phone" -> NativePhonePreferenceControls()
                else -> NativePendingHint(detailId)
            }
        }
    }
}

private fun detailInfo(id: String): NativeDetailInfo = when (id) {
    "ledger" -> NativeDetailInfo(
        "原生账单中心",
        "账单中心",
        "先接入原生外壳，后续把记录列表、删除、导出迁到 Room / DataStore。",
        listOf("当前状态" to "原生详情页已接入", "数据来源" to "暂时复用 Web localStorage", "下一步" to "迁移账单列表和导出按钮"),
    )
    "stats" -> NativeDetailInfo(
        "原生数据统计",
        "数据统计",
        "先做轻量统计卡片，再迁移趋势图和分类图。",
        listOf("当前状态" to "原生详情页已接入", "图表方案" to "后续使用 Compose Canvas", "性能策略" to "避免 Web canvas 和 CSS 动画参与"),
    )
    "alarm" -> NativeDetailInfo(
        "原生提醒闹钟",
        "提醒闹钟",
        "系统闹钟已经接到 SystemActionRouter，后续补确认页和历史记录。",
        listOf("执行方式" to "AlarmClock.ACTION_SET_ALARM", "安全策略" to "用户确认后跳转系统闹钟", "下一步" to "补常用提醒模板"),
    )
    "apps" -> NativeDetailInfo(
        "原生应用控制",
        "应用控制",
        "打开应用能力已经接到原生 Router，下一步补应用包名管理。",
        listOf("执行方式" to "PackageManager.getLaunchIntentForPackage", "常用应用" to "微信、支付宝、高德、百度地图等", "下一步" to "做原生应用选择器"),
    )
    "shortcuts" -> NativeDetailInfo(
        "原生快捷指令",
        "快捷指令",
        "把高频动作沉淀成一键指令，后续可接桌面快捷方式。",
        listOf("示例" to "回家导航、明早闹钟、打开微信", "存储" to "后续迁移 DataStore", "状态" to "原生壳已预留入口"),
    )
    "tasks" -> NativeDetailInfo(
        "原生任务记录",
        "任务记录",
        "用于记录手机动作是否执行成功，后续从 Web 卡片迁移为原生列表。",
        listOf("记录类型" to "闹钟、导航、打开应用", "状态" to "待确认 / 已执行 / 失败", "下一步" to "接原生执行回调"),
    )
    "account" -> NativeDetailInfo(
        "账号与同步",
        "账号与同步",
        "账号体系暂时保留 Web 逻辑，原生页先展示入口。",
        listOf("当前状态" to "本地模式", "云同步" to "后续接原生网络层", "建议" to "登录页最后迁移"),
    )
    "display" -> NativeDetailInfo(
        "显示与语言",
        "显示与语言",
        "高频视觉选项接入原生，先保证流畅和稳定。",
        listOf("语言" to "简体中文", "字体大小" to "标准", "动画策略" to "原生负责动画，Web 只做内容"),
    )
    "phone" -> NativeDetailInfo(
        "手机偏好",
        "手机偏好",
        "地图、常用地址、系统动作偏好后续全部走原生存储。",
        listOf("默认地图" to "系统地图 / 高德 / 百度", "默认方式" to "驾车", "常用地址" to "家、学校、公司、宿舍"),
    )
    "appearance" -> NativeDetailInfo(
        "背景外观",
        "背景外观",
        "液态玻璃强度、背景层、阴影等统一由 Compose 控制。",
        listOf("当前模式" to "Safe 优先", "玻璃策略" to "伪玻璃 + 少量原生动画", "目标" to "先流畅，再增强质感"),
    )
    "budget" -> NativeDetailInfo(
        "数据与预算",
        "数据与预算",
        "预算和数据工具暂时复用 Web 存储，后续迁移到原生数据层。",
        listOf("预算" to "等待同步 Web 数据", "导出" to "后续接原生分享面板", "清空数据" to "必须保留二次确认"),
    )
    else -> NativeDetailInfo("原生详情", "详情页", "这个入口已经进入原生容器。", listOf("状态" to "待完善"))
}

@Composable
private fun NativePageSurface(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
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
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                headerAction?.invoke()
            }
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

@Composable
private fun NativeDetailRow(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.085f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun NativePendingHint(detailId: String) {
    NativeDetailRow(
        title = "迁移计划",
        subtitle = "${detailId} 已接入原生详情容器，下一步把数据读取、按钮操作和执行结果迁到 Kotlin 层。",
    )
}

@Composable
private fun NativeSmallButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun NativeGlassControls() {
    var compactMode by remember { mutableStateOf(false) }
    var animationOn by remember { mutableStateOf(true) }
    var glassStrength by remember { mutableFloatStateOf(0.42f) }

    NativeSwitchRow("紧凑模式", "压缩页面间距，让信息密度更高。", compactMode) { compactMode = it }
    NativeSwitchRow("原生动画", "只保留 Compose 层动画，Web 动画保持关闭。", animationOn) { animationOn = it }
    NativeSliderRow("玻璃强度", "调节原生伪玻璃透明度，默认以流畅优先。", glassStrength) { glassStrength = it }
}

@Composable
private fun NativePhonePreferenceControls() {
    NativeDetailRow("默认地图", "后续可在这里选择系统地图 / 高德地图 / 百度地图。")
    NativeDetailRow("常用地址", "家、学校、公司、宿舍会迁移到原生 DataStore。")
    NativeDetailRow("系统能力", "闹钟、导航、打开应用都将统一走 SystemActionRouter。")
}

@Composable
private fun NativeSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.085f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun NativeSliderRow(title: String, subtitle: String, value: Float, onChange: (Float) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.085f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.5.sp, lineHeight = 17.sp)
            Slider(value = value, onValueChange = onChange, valueRange = 0.20f..0.75f)
        }
    }
}
