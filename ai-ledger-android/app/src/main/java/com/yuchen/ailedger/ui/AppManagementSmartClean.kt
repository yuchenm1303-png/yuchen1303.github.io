package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AppOptimizationSignal
import com.yuchen.ailedger.service.appControlHumanBytes

data class SmartCleanResult(
    val requested: Int,
    val success: Int,
    val failed: Int,
    val beforeBytes: Long?,
    val failedLabels: List<String> = emptyList(),
)

@Composable
internal fun SmartCleanControlPanel(
    candidateCount: Int,
    running: Boolean,
    result: SmartCleanResult?,
    onSmartClean: () -> Unit,
) {
    AppDetailSection("一键智能清后台") {
        Text(
            text = when {
                running -> "正在按保护策略清理后台应用，请稍等…"
                candidateCount > 0 -> "已识别 $candidateCount 个可清后台应用。只会处理非保护、非前台感知、策略允许强停的应用。"
                else -> "当前没有发现适合一键清理的后台应用。你可以切到“后台”筛选查看运行状态。"
            },
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        )
        if (result != null) {
            Spacer(Modifier.height(2.dp))
            SmartCleanResultRow(result)
        }
        Spacer(Modifier.height(2.dp))
        AppInlineButton(
            text = if (running) "正在清理…" else "智能清后台（$candidateCount）",
            onClick = { if (!running) onSmartClean() },
        )
    }
}

@Composable
private fun SmartCleanResultRow(result: SmartCleanResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppSuccess.copy(alpha = if (result.success > 0) 0.09f else 0.04f),
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "上次清理：成功 ${result.success}/${result.requested}，失败 ${result.failed}" +
                    (result.beforeBytes?.let { "，清理前估算内存 ${it.appControlHumanBytes()}" } ?: ""),
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (result.failedLabels.isNotEmpty()) {
                Text(
                    "未完成：${result.failedLabels.take(3).joinToString("、")}",
                    color = AppWarning.copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SmartCleanConfirmationDialog(
    candidates: List<AppOptimizationSignal>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val estimatedBytes = candidates.mapNotNull { it.runtime?.estimatedMemoryBytes }.takeIf { it.isNotEmpty() }?.sum()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = { Text("确认智能清后台", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "将强制停止 ${candidates.size} 个后台应用。只包含当前体检判定为可清理，且没有被系统保护策略拦截的应用。",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                estimatedBytes?.let {
                    Text("清理前估算内存：${it.appControlHumanBytes()}", color = AppAccent.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.take(6).forEach { signal ->
                        Text(
                            "• ${signal.app.label}：${signal.runtime?.stateLabel ?: "后台"}",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (candidates.size > 6) {
                        Text("还有 ${candidates.size - 6} 个应用…", color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
                    }
                }
                Text(
                    "这不会清除应用数据。重新打开应用后通常会恢复运行。",
                    color = AppWarning.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认清后台", color = AppWarning, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
            }
        },
    )
}
