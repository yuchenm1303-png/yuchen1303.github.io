package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StorageAppAnalysisState
import com.yuchen.ailedger.service.StorageAppOptimizationItem
import com.yuchen.ailedger.service.StorageDeviceGuard
import com.yuchen.ailedger.service.StorageProductDashboard

@Composable
internal fun DeviceGuardPanel(guard: StorageDeviceGuard) {
    val tone = if (guard.heavyWorkAllowed) OptimizeSuccess else OptimizeCritical
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.19f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("设备负载保护", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(guard.reason, color = Color.White.copy(alpha = 0.64f), fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Text(if (guard.heavyWorkAllowed) "可继续" else "已暂停", color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptimizeMetric("电量", guard.batteryPercent?.let { "$it%" } ?: "未知", Modifier.weight(1f))
                OptimizeMetric("供电", if (guard.charging) "充电中" else "电池", Modifier.weight(1f))
                OptimizeMetric("温度", guard.thermalLabel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun OptimizationOverviewPanel(
    dashboard: StorageProductDashboard,
    onOpen: (StorageOptimizationTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptimizeMetric("已用", formatOptimizeBytes(dashboard.overview.usedBytes), Modifier.weight(1f))
            OptimizeMetric("可用", formatOptimizeBytes(dashboard.overview.freeBytes), Modifier.weight(1f))
        }
        OptimizeOverviewCard(
            title = "应用占用与长期未用",
            value = "${dashboard.appAnalysis.progress.processedCount}/${dashboard.appAnalysis.progress.totalCount}",
            detail = if (dashboard.appAnalysis.progress.complete) "分析完成" else "支持分批和断点恢复",
            tone = OptimizeAccent,
        ) { onOpen(StorageOptimizationTab.Apps) }
        OptimizeOverviewCard(
            title = "清理趋势",
            value = formatOptimizeBytes(dashboard.cleanupHistory.sumOf { it.releasedBytes }),
            detail = "${dashboard.cleanupHistory.sumOf { it.deletedCount }} 个文件已核验删除",
            tone = OptimizeSuccess,
        ) { onOpen(StorageOptimizationTab.Trends) }
        OptimizeOverviewCard(
            title = "权限健康",
            value = if (dashboard.permissions.healthy) "状态正常" else "需要检查",
            detail = permissionSummary(dashboard.permissions),
            tone = if (dashboard.permissions.healthy) OptimizeSuccess else OptimizeWarning,
        ) { onOpen(StorageOptimizationTab.Health) }
    }
}

@Composable
internal fun AppProgressSummary(
    analysis: StorageAppAnalysisState,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onOpen),
        shape = shape,
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("应用分析进度", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${analysis.progress.processedCount}/${analysis.progress.totalCount}", color = OptimizeAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(
                    Modifier.fillMaxWidth(analysis.progress.fraction.coerceIn(0f, 1f)).height(7.dp)
                        .clip(RoundedCornerShape(999.dp)).background(OptimizeAccent.copy(alpha = 0.72f)),
                )
            }
            Text(
                when {
                    analysis.progress.complete -> "完整分析已完成"
                    analysis.progress.interrupted -> "上次分析中断，可从断点继续"
                    analysis.progress.processedCount > 0 -> "断点已保存，可继续下一批"
                    else -> "尚未开始应用占用分析"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
internal fun AppAnalysisControlPanel(
    analysis: StorageAppAnalysisState,
    analyzing: Boolean,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.09f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF121743).copy(alpha = 0.30f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("分批应用分析", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("每批最多 20 个应用，每处理一个应用就保存断点。", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp)
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = OptimizeAccent)
            }
            AppProgressSummaryInline(analysis)
            if (!analysis.usageAccessGranted) {
                OptimizeInfoPanel("使用情况权限未开启", "可以分析安装包大小，但无法可靠判断最近使用时间和私有数据占用。", OptimizeWarning)
                OptimizePrimaryAction("开启使用情况访问", true, onClick = onGrantUsageAccess)
            }
            if (!analysis.deviceGuard.heavyWorkAllowed) {
                OptimizeInfoPanel("设备保护已触发", analysis.deviceGuard.reason, OptimizeCritical)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (analyzing) {
                    OptimizeSecondaryAction("暂停并保存", Modifier.weight(1f), onStop)
                } else {
                    OptimizeSecondaryAction("重新开始", Modifier.weight(1f), onRestart)
                    OptimizePrimaryAction(
                        text = when {
                            analysis.progress.complete -> "已完成"
                            analysis.progress.processedCount > 0 -> "继续下一批"
                            else -> "开始第一批"
                        },
                        enabled = !analysis.progress.complete && analysis.deviceGuard.heavyWorkAllowed,
                        modifier = Modifier.weight(1f),
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppProgressSummaryInline(analysis: StorageAppAnalysisState) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("进度", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text("${analysis.progress.processedCount}/${analysis.progress.totalCount}", color = OptimizeAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
            Box(
                Modifier.fillMaxWidth(analysis.progress.fraction.coerceIn(0f, 1f)).height(7.dp)
                    .clip(RoundedCornerShape(999.dp)).background(OptimizeAccent.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
internal fun OptimizationAppCard(
    app: StorageAppOptimizationItem,
    highlightUnused: Boolean,
    onOpenSettings: () -> Unit,
) {
    val tone = if (highlightUnused) OptimizeWarning else OptimizeAccent
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onOpenSettings),
        shape = shape,
        color = tone.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.label, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = Color.White.copy(alpha = 0.33f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatOptimizeBytes(app.totalBytes ?: app.apkBytes), color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Text(app.suggestionReason, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OptimizeTinyMetric("应用", formatOptimizeBytes(app.appBytes ?: app.apkBytes), Modifier.weight(1f))
                OptimizeTinyMetric("数据", formatOptionalBytes(app.dataBytes), Modifier.weight(1f))
                OptimizeTinyMetric("缓存", formatOptionalBytes(app.cacheBytes), Modifier.weight(1f))
            }
            Text("点击进入系统应用信息页", color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp)
        }
    }
}
