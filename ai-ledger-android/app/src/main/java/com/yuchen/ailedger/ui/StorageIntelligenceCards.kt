package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageDuplicateGroup
import com.yuchen.ailedger.service.StorageIntelligenceFile

@Composable
internal fun DuplicateGroupCard(
    group: StorageDuplicateGroup,
    selectedIds: Set<String>,
    onToggle: (StorageIntelligenceFile) -> Unit,
    onSelectSuggested: () -> Unit,
) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = IntelligenceAccent.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("完全相同 · ${group.files.size} 份", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(
                        "每份 ${formatIntelligenceBytes(group.sizeBytes)} · 建议释放 ${formatIntelligenceBytes(group.recoverableBytes)}",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 10.sp,
                    )
                }
                IntelligenceMiniAction(
                    text = "选择副本",
                    enabled = group.suggestedDeleteIds.isNotEmpty(),
                    onClick = onSelectSuggested,
                )
            }
            StorageLongListControls(
                totalCount = group.files.size,
                expanded = expanded,
                previewCount = STORAGE_HISTORY_PREVIEW_COUNT,
                onToggleExpanded = { expanded = !expanded },
                tone = IntelligenceAccent,
            )
            storagePreviewItems(group.files, expanded, STORAGE_HISTORY_PREVIEW_COUNT).forEach { file ->
                val keeper = file.stableId == group.keepFileId
                IntelligenceFileCard(
                    file = file,
                    selected = file.stableId in selectedIds,
                    label = if (keeper) "建议保留" else "完全相同副本",
                    enabled = file.canDelete && !keeper,
                    onClick = { onToggle(file) },
                )
            }
        }
    }
}

@Composable
internal fun IntelligenceFileCard(
    file: StorageIntelligenceFile,
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (selected) IntelligenceAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, if (selected) IntelligenceAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        when {
                            selected -> IntelligenceAccent.copy(alpha = 0.88f)
                            enabled -> Color.White.copy(alpha = 0.08f)
                            else -> IntelligenceSuccess.copy(alpha = 0.16f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        selected -> "✓"
                        !enabled -> "留"
                        else -> ""
                    },
                    color = if (selected) Color(0xFF101638) else IntelligenceSuccess,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.displayName,
                        color = Color.White.copy(alpha = if (enabled) 0.90f else 0.66f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatIntelligenceBytes(file.sizeBytes), color = IntelligenceAccent.copy(alpha = 0.80f), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "$label · ${if (file.source == StorageCandidateSource.MediaStore) "共享媒体" else "授权目录"}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.5.sp,
                )
                Text(file.location, color = Color.White.copy(alpha = 0.30f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!file.canDelete) {
                    Text("文档提供方未开放删除能力", color = IntelligenceWarning.copy(alpha = 0.78f), fontSize = 8.8.sp)
                }
            }
        }
    }
}

@Composable
internal fun IntelligenceDeleteDialog(
    files: List<StorageIntelligenceFile>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = {
            Text("确认清理智能分析结果", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共 ${files.size} 个文件，约 ${formatIntelligenceBytes(files.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "重复文件已通过完整 SHA-256 确认；长期未修改文件只是检查建议。媒体仍会交给 Android 系统二次确认，授权目录文件按当前勾选结果删除。",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text("删除后不保证能够恢复。", color = IntelligenceWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = IntelligenceCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
            }
        },
    )
}
