package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageIntelligenceFile
import com.yuchen.ailedger.service.StorageIntelligenceResult

@Composable
internal fun IntelligenceActionPanel(
    result: StorageIntelligenceResult?,
    analyzing: Boolean,
    includeMedia: Boolean,
    hasFolder: Boolean,
    onAnalyze: () -> Unit,
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("分析范围", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            includeMedia && hasFolder -> "共享媒体 + 授权目录"
                            includeMedia -> "共享媒体"
                            hasFolder -> "授权目录"
                            else -> "尚未授权可分析范围"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.dp, color = IntelligenceAccent)
            }
            result?.let {
                IntelligenceMetric("已索引文件", it.scannedFileCount.toString())
                IntelligenceMetric("完整哈希校验", "${it.fullHashedFileCount} 个")
                IntelligenceMetric("重复文件", "${it.duplicateGroups.size} 组 · ${formatIntelligenceBytes(it.recoverableBytes)}")
                IntelligenceMetric("分析耗时", formatElapsed(it.elapsedMs))
                if (it.limited) {
                    Text(
                        "已达到本次性能保护上限，结果是已完成范围内的准确结果，并非设备全部文件结论。",
                        color = IntelligenceWarning.copy(alpha = 0.86f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            IntelligencePrimaryAction(
                text = when {
                    analyzing -> "正在计算内容指纹…"
                    result == null -> "开始智能分析"
                    else -> "重新进行智能分析"
                },
                enabled = !analyzing && (includeMedia || hasFolder),
                onClick = onAnalyze,
            )
            if (!includeMedia && !hasFolder) {
                Text(
                    "请先返回基础存储管理，授权共享媒体或选择一个目录。",
                    color = IntelligenceWarning.copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                )
            }
        }
    }
}

@Composable
internal fun IntelligenceSelectionPanel(
    files: List<StorageIntelligenceFile>,
    operationRunning: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = IntelligenceCritical.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, IntelligenceCritical.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            IntelligenceMetric("已选择", "${files.size} 个 · ${formatIntelligenceBytes(files.sumOf { it.sizeBytes })}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntelligenceSecondaryAction("清除选择", Modifier.weight(1f), onClear)
                IntelligenceDangerAction(
                    text = if (operationRunning) "正在处理…" else "清理已选",
                    enabled = !operationRunning,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun CleanupHistoryCard(entry: StorageCleanupHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(formatIntelligenceDate(entry.createdAt), color = Color.White.copy(alpha = 0.36f), fontSize = 9.5.sp)
                Text(
                    "请求 ${entry.requestedCount} · 成功 ${entry.deletedCount} · 失败 ${entry.failedCount}",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 9.5.sp,
                )
            }
            Text(formatIntelligenceBytes(entry.releasedBytes), color = IntelligenceSuccess, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun IntelligenceSectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun IntelligenceInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 10.8.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun IntelligenceEmptyPanel(text: String) {
    IntelligenceInfoPanel("暂无结果", text, Color.White)
}

@Composable
internal fun IntelligenceMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun IntelligencePrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = IntelligenceAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = IntelligenceAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        )
    }
}

@Composable
internal fun IntelligenceMiniAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = IntelligenceAccent.copy(alpha = if (enabled) 0.10f else 0.035f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = if (enabled) 0.20f else 0.06f)),
    ) {
        Text(
            text,
            color = IntelligenceAccent.copy(alpha = if (enabled) 0.84f else 0.30f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun IntelligenceSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.70f), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(11.dp))
    }
}

@Composable
internal fun IntelligenceDangerAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = IntelligenceCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, IntelligenceCritical.copy(alpha = if (enabled) 0.26f else 0.07f)),
    ) {
        Text(text, color = IntelligenceCritical.copy(alpha = if (enabled) 0.90f else 0.32f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(11.dp))
    }
}

@Composable
internal fun IntelligenceTextAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
