package com.yuchen.ailedger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.service.StorageMediaOrganizationRepository
import com.yuchen.ailedger.service.StorageOrganizationFile
import com.yuchen.ailedger.service.StorageOrganizationSnapshot
import com.yuchen.ailedger.service.StorageReviewRisk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun OrganizationPreviewDialog(
    file: StorageOrganizationFile,
    repository: StorageMediaOrganizationRepository,
    selected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onIgnoreFile: () -> Unit,
    onIgnoreDirectory: () -> Unit,
) {
    val preview by produceState<Bitmap?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { repository.loadPreviewBitmap(file) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        title = { Text("文件预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (file.mimeType.startsWith("image/")) {
                    if (preview != null) {
                        Image(
                            bitmap = preview!!.asImageBitmap(),
                            contentDescription = file.displayName,
                            modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = OrganizationAccent)
                        }
                    }
                }
                Text(file.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${formatOrganizationBytes(file.sizeBytes)} · ${file.mimeType.ifBlank { "未知类型" }}", color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                if (file.width > 0 && file.height > 0) {
                    Text("${file.width} × ${file.height}", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp)
                }
                Text(file.location, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp, lineHeight = 14.sp)
                file.reviewNote.takeIf(String::isNotBlank)?.let { note ->
                    Text(note, color = riskTone(file.risk), fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Text(file.risk.explanation, color = Color.White.copy(alpha = 0.54f), fontSize = 10.5.sp, lineHeight = 15.sp)
                OrganizationTextAction("永不提示此文件", onIgnoreFile)
                OrganizationTextAction("忽略此目录", onIgnoreDirectory)
            }
        },
        confirmButton = {
            TextButton(enabled = file.canDelete, onClick = onToggleSelection) {
                Text(if (selected) "取消选择" else "加入清理选择", color = OrganizationCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
internal fun OrganizationDeleteDialog(
    files: List<StorageOrganizationFile>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cautionCount = files.count { it.risk == StorageReviewRisk.Caution }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        title = { Text("确认清理所选文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共 ${files.size} 个文件，约 ${formatOrganizationBytes(files.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (cautionCount > 0) {
                    Text("其中 $cautionCount 个属于谨慎处理项目，可能包含唯一照片、媒体或文档。", color = OrganizationWarning, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text(
                    "相似照片、连拍和画质候选都不等于重复文件。媒体删除仍由 Android 系统再次确认。",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = OrganizationCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

internal fun StorageOrganizationSnapshot.allOrganizationFiles(): List<StorageOrganizationFile> {
    return buildList {
        similarGroups.forEach { addAll(it.files) }
        addAll(screenshots)
        burstGroups.forEach { addAll(it.files) }
        addAll(qualityCandidates)
        downloadCategories.forEach { addAll(it.files) }
    }.distinctBy { it.stableId }
}

internal fun toggleOrganizationSelection(
    selectedIds: Set<String>,
    file: StorageOrganizationFile,
): Set<String> {
    if (!file.canDelete) return selectedIds
    return if (file.stableId in selectedIds) selectedIds - file.stableId else selectedIds + file.stableId
}

internal fun Context.hasOrganizationMediaAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = hasOrganizationPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasOrganizationPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        images || selected
    } else {
        hasOrganizationPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasOrganizationPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun riskTone(risk: StorageReviewRisk): Color = when (risk) {
    StorageReviewRisk.Low -> OrganizationSuccess
    StorageReviewRisk.Review -> OrganizationAccent
    StorageReviewRisk.Caution -> OrganizationCaution
}

internal fun formatOrganizationBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val digits = if (value >= 100 || index == 0) 0 else 1
    return String.format(Locale.CHINA, "%.${digits}f %s", value, units[index])
}

internal fun formatOrganizationDate(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

internal fun formatOrganizationElapsed(elapsedMs: Long): String {
    return if (elapsedMs < 1_000L) "$elapsedMs ms" else String.format(Locale.CHINA, "%.1f 秒", elapsedMs / 1_000.0)
}

internal val OrganizationAccent = Color(0xFF8DF9EA)
internal val OrganizationSuccess = Color(0xFF83F3B8)
internal val OrganizationWarning = Color(0xFFFFCA72)
internal val OrganizationCaution = Color(0xFFFFB47A)
internal val OrganizationCritical = Color(0xFFFF7F8D)
