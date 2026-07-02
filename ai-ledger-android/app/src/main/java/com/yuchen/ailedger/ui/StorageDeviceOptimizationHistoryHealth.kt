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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.StorageCapacitySnapshot
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupTrendPoint
import com.yuchen.ailedger.service.StorageCompatibilityReport
import com.yuchen.ailedger.service.StoragePermissionHealth
import java.time.format.DateTimeFormatter

@Composable
internal fun CleanupTrendSummary(history: List<StorageCleanupHistoryEntry>) {
    val released = history.sumOf { it.releasedBytes }
    val deleted = history.sumOf { it.deletedCount }
    val failed = history.sumOf { it.failedCount }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OptimizeMetric("累计释放", formatOptimizeBytes(released), Modifier.weight(1f))
        OptimizeMetric("成功", deleted.toString(), Modifier.weight(1f))
        OptimizeMetric("失败", failed.toString(), Modifier.weight(1f))
    }
}

@Composable
internal fun CleanupTrendCard(point: StorageCleanupTrendPoint, maximum: Long) {
    val fraction = if (maximum > 0L) {
        (point.releasedBytes.toFloat() / maximum.toFloat()).coerceIn(0.03f, 1f)
    } else {
        0.03f
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    point.day.format(DateTimeFormatter.ofPattern("MM-dd")),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(formatOptimizeBytes(point.releasedBytes), color = OptimizeSuccess, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(OptimizeSuccess.copy(alpha = 0.66f)))
            }
            Text("删除 ${point.deletedCount} · 失败 ${point.failedCount}", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp)
        }
    }
}

@Composable
internal fun CapacitySnapshotCard(snapshot: StorageCapacitySnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatOptimizeDateTime(snapshot.createdAt), color = Color.White.copy(alpha = 0.72f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Text("已用 ${formatOptimizeBytes(snapshot.usedBytes)}", color = Color.White.copy(alpha = 0.42f), fontSize = 9.5.sp)
            }
            Text("可用 ${formatOptimizeBytes(snapshot.freeBytes)}", color = OptimizeAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun OptimizeHistoryCard(entry: StorageCleanupHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(formatOptimizeDateTime(entry.createdAt), color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp)
                Text("成功 ${entry.deletedCount} · 失败 ${entry.failedCount}", color = Color.White.copy(alpha = 0.42f), fontSize = 9.2.sp)
            }
            Text(formatOptimizeBytes(entry.releasedBytes), color = OptimizeSuccess, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun PermissionHealthPanel(
    health: StoragePermissionHealth,
    onUsageAccess: () -> Unit,
    onMediaAccess: () -> Unit,
    onFolderAccess: () -> Unit,
    onAppSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OptimizeSectionHeader("权限健康", if (health.healthy) "状态正常" else "需要处理")
        PermissionHealthRow(
            title = "使用情况访问",
            status = if (health.usageAccessGranted) "已授权" else "未授权",
            ok = health.usageAccessGranted,
            onRepair = onUsageAccess,
        )
        PermissionHealthRow(
            title = "共享媒体",
            status = when {
                health.selectedPhotoAccessOnly -> "仅部分照片"
                health.mediaAccessGranted -> "已授权"
                else -> "未授权"
            },
            ok = health.mediaAccessGranted,
            onRepair = onMediaAccess,
        )
        PermissionHealthRow(
            title = "授权目录",
            status = when {
                !health.authorizedFolderPresent -> "尚未选择"
                health.authorizedFolderPermissionValid -> "授权有效"
                else -> "授权已失效"
            },
            ok = !health.authorizedFolderPresent || health.authorizedFolderPermissionValid,
            onRepair = onFolderAccess,
        )
        OptimizeSecondaryAction("打开本应用系统设置", Modifier.fillMaxWidth(), onAppSettings)
    }
}

@Composable
internal fun PermissionHealthRow(
    title: String,
    status: String,
    ok: Boolean,
    onRepair: () -> Unit,
) {
    val tone = if (ok) OptimizeSuccess else OptimizeWarning
    val shape = RoundedCornerShape(19.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onRepair),
        shape = shape,
        color = tone.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(status, color = tone, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (ok) "检查 ›" else "修复 ›", color = tone, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun ShizukuHealthPanel(shellStatus: DeviceShellStatus?, onRequest: () -> Unit) {
    val available = shellStatus?.shizukuAvailable == true
    val granted = shellStatus?.shizukuGranted == true
    val tone = if (granted) OptimizeSuccess else OptimizeAccent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Shizuku 可选增强", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                when {
                    granted -> "已授权。仍只通过现有动作白名单和高风险确认链执行，不开放任意 shell 清理。"
                    available -> "服务可用但尚未授权。授权不会开启后台自动清理。"
                    else -> "当前未检测到可用服务，存储管理的基础、智能和精细整理功能不受影响。"
                },
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            if (available && !granted) {
                OptimizePrimaryAction("请求 Shizuku 授权", true, onClick = onRequest)
            }
        }
    }
}

@Composable
internal fun CompatibilityPanel(report: StorageCompatibilityReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("厂商兼容诊断", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(report.title, color = OptimizeAccent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            report.guidance.forEach { item ->
                Text("• $item", color = Color.White.copy(alpha = 0.52f), fontSize = 10.2.sp, lineHeight = 15.sp)
            }
        }
    }
}
