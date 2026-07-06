package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AppControlDashboard
import com.yuchen.ailedger.service.AppOptimizationSignal
import com.yuchen.ailedger.service.appControlHumanBytes

@androidx.compose.runtime.Composable
internal fun RuntimeMemoryHeroCard(
    dashboard: AppControlDashboard?,
    runningSignals: List<AppOptimizationSignal>,
    onRefresh: () -> Unit,
) {
    val memory = dashboard?.memory
    val usage = memory?.usagePercent ?: 0
    val statusTone = when {
        memory?.lowMemory == true -> AppCritical
        usage >= 88 -> AppWarning
        usage >= 72 -> AppWarning
        else -> AppAccent
    }
    FrostInfoGlassPanel(radius = 22f, backdropAlpha = 1f, frostAlpha = 0.115f, dimAlpha = 0f, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF15204E).copy(alpha = 0.52f),
                            Color(0xFF0A102B).copy(alpha = 0.30f),
                            statusTone.copy(alpha = 0.10f),
                        ),
                    ),
                )
                .padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("BACKGROUND SCAN", color = AppAccent.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("后台运行扫描", color = Color.White.copy(alpha = 0.96f), fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                    Text(
                        memory?.stateLabel ?: "正在读取系统内存状态…",
                        color = statusTone.copy(alpha = 0.86f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                AppCompactAction("刷新", onRefresh)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${usage}%", color = Color.White, fontSize = 42.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("运行内存已用", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    MemoryUsageBar(progress = usage / 100f, tone = statusTone)
                    Text(
                        memory?.let { "已用 ${it.usedBytes.appControlHumanBytes()} / 总计 ${it.totalBytes.appControlHumanBytes()}" } ?: "普通模式正在估算，部分机型会隐藏跨应用内存。",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSummaryMetric("剩余", memory?.availableBytes?.appControlHumanBytes() ?: "--", Modifier.weight(1f))
                AppSummaryMetric("后台应用", dashboard?.runningApps?.toString() ?: "--", Modifier.weight(1f))
                AppSummaryMetric("可清理", dashboard?.cleanCandidates?.toString() ?: "--", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSummaryMetric("估算占用", dashboard?.estimatedRuntimeBytes?.appControlHumanBytes() ?: "受限", Modifier.weight(1f))
                AppSummaryMetric("可释放", runningSignals.mapNotNull { it.runtime?.estimatedMemoryBytes }.takeIf { it.isNotEmpty() }?.sum()?.appControlHumanBytes() ?: "受限", Modifier.weight(1f))
                AppSummaryMetric("低内存线", memory?.thresholdBytes?.appControlHumanBytes() ?: "--", Modifier.weight(1f))
            }

            if (runningSignals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("内存占用 Top", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    runningSignals.take(4).forEach { signal ->
                        RuntimeTopRow(signal)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MemoryUsageBar(progress: Float, tone: Color) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeProgress)
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tone.copy(alpha = 0.82f)),
        )
    }
}

@androidx.compose.runtime.Composable
private fun RuntimeTopRow(signal: AppOptimizationSignal) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(signal.app.label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${signal.runtime?.stateLabel ?: "运行中"} · ${signal.runtime?.processCount ?: 0} 进程",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                signal.runtime?.estimatedMemoryBytes?.appControlHumanBytes() ?: "受限",
                color = AppAccent.copy(alpha = 0.84f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}
