package com.yuchen.ailedger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AppCacheUsage
import com.yuchen.ailedger.service.AuthorizedFolderScan
import com.yuchen.ailedger.service.DeviceStorageOverview
import com.yuchen.ailedger.service.StorageCandidateKind
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageDeleteResult
import com.yuchen.ailedger.service.StorageFileCandidate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun StorageOverviewPanel(
    overview: DeviceStorageOverview?,
    scanning: Boolean,
    onRefresh: () -> Unit,
    onOpenSystemStorage: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 19f,
        backdropAlpha = 1f,
        frostAlpha = 0.10f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF121743).copy(alpha = 0.31f)).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("设备空间", color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        overview?.let { "已用 ${formatStorageBytes(it.usedBytes)}" } ?: "正在读取…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        overview?.let { "可用 ${formatStorageBytes(it.freeBytes)} / 总计 ${formatStorageBytes(it.totalBytes)}" }.orEmpty(),
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                    )
                }
                if (scanning) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = StorageAccent)
            }
            Box(
                Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(overview?.usedFraction ?: 0f).height(9.dp)
                        .clip(RoundedCornerShape(999.dp)).background(StorageAccent.copy(alpha = 0.72f)),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageSmallAction("重新扫描", Modifier.weight(1f), onRefresh)
                StorageSmallAction("系统存储", Modifier.weight(1f), onOpenSystemStorage)
            }
        }
    }
}

@Composable
internal fun StorageSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    FrostInfoGlassPanel(
        radius = 17f,
        backdropAlpha = 1f,
        frostAlpha = 0.085f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF121743).copy(alpha = 0.27f)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
internal fun StorageAccessRow(
    title: String,
    detail: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.045f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(9.dp).clip(RoundedCornerShape(999.dp))
                .background(if (granted) StorageSuccess else StorageWarning),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.90f), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
        }
        val shape = RoundedCornerShape(999.dp)
        Surface(
            modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onAction),
            shape = shape,
            color = StorageAccent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, StorageAccent.copy(alpha = 0.20f)),
        ) {
            Text(
                actionText,
                color = StorageAccent.copy(alpha = 0.88f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun StorageCandidateCard(
    candidate: StorageFileCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val tone = when (candidate.kind) {
        StorageCandidateKind.Installer,
        StorageCandidateKind.Archive,
        StorageCandidateKind.Temporary -> StorageWarning
        else -> StorageAccent
    }
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, enabled = candidate.canDelete, onClick = onToggle),
        shape = shape,
        color = tone.copy(alpha = if (selected) 0.14f else 0.055f),
        border = BorderStroke(1.dp, tone.copy(alpha = if (selected) 0.40f else 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier.size(23.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (selected) tone.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (selected) "✓" else "", color = Color(0xFF101638), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        candidate.displayName,
                        color = Color.White.copy(alpha = if (candidate.canDelete) 0.92f else 0.42f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatStorageBytes(candidate.sizeBytes), color = tone.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "${candidate.kind.label} · ${if (candidate.source == StorageCandidateSource.MediaStore) "共享媒体" else "授权目录"}",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.sp,
                )
                Text(
                    candidate.location,
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(candidate.reviewReason, color = Color.White.copy(alpha = 0.43f), fontSize = 9.5.sp, lineHeight = 13.sp)
                if (!candidate.canDelete) {
                    Text("该文档提供方未开放删除能力", color = StorageWarning.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun AppCacheCard(app: AppCacheUsage, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(21.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(shape = shape, onClick = onOpen),
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    app.packageName,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "数据 ${formatStorageBytes(app.dataBytes)} · 应用 ${formatStorageBytes(app.appBytes)}",
                    color = Color.White.copy(alpha = 0.43f),
                    fontSize = 9.5.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatStorageBytes(app.cacheBytes), color = StorageWarning, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("缓存 · 去管理", color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
internal fun StorageDeleteConfirmationDialog(
    candidates: List<StorageFileCandidate>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = {
            Text("确认清理所选文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "共 ${candidates.size} 个文件，约 ${formatStorageBytes(candidates.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "媒体文件会交给 Android 系统再次确认；授权目录文件会按你当前的勾选结果删除。清理不会触碰应用登录状态或数据库。",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "删除后不保证可以恢复，请确认文件没有唯一副本。",
                    color = StorageWarning.copy(alpha = 0.86f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = StorageCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
internal fun StorageBackButton(state: AssistantUiState, onBack: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(116.dp).height(40.dp),
        role = GlassRole.Chip,
        onClick = onBack,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("‹ 返回功能", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
internal fun StorageMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.47f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun StorageFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) StorageAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, if (selected) StorageAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            label,
            color = if (selected) StorageAccent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.65f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun StoragePrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = StorageCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, StorageCritical.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = StorageCritical.copy(alpha = if (enabled) 0.92f else 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        )
    }
}

@Composable
internal fun StorageSmallAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        )
    }
}

@Composable
internal fun StorageInlineAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
internal fun StorageNoticePanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun StorageLoadingPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.065f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = StorageAccent)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun StorageEmptyPanel(text: String) {
    StorageNoticePanel("暂无候选", text, Color.White)
}

internal fun currentMediaAccess(context: Context): MediaAccessState {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = context.hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = context.hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        MediaAccessState(
            visualFull = images && videos,
            audioFull = audio,
            visualPartial = partial && !(images && videos),
        )
    } else {
        val granted = context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        MediaAccessState(granted, granted, false)
    }
}

internal fun requiredMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun folderAccessSummary(scan: AuthorizedFolderScan?): String {
    if (scan == null) return "未选择目录；应用不会扫描普通文档和下载文件。"
    scan.errorMessage?.let { return it }
    val suffix = if (scan.truncated) "，已达到扫描上限" else ""
    return "${scan.displayName} · ${scan.scannedFileCount} 个文件 · ${formatStorageBytes(scan.scannedBytes)}$suffix"
}

internal fun combineDeleteResults(first: StorageDeleteResult, second: StorageDeleteResult): StorageDeleteResult {
    return StorageDeleteResult(
        requestedCount = first.requestedCount + second.requestedCount,
        deletedCount = first.deletedCount + second.deletedCount,
        failedCount = first.failedCount + second.failedCount,
        errors = first.errors + second.errors,
    )
}

internal fun formatStorageBytes(bytes: Long): String {
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

@Suppress("unused")
internal fun formatStorageDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

internal val StorageAccent = Color(0xFF8DF9EA)
internal val StorageSuccess = Color(0xFF83F3B8)
internal val StorageWarning = Color(0xFFFFCA72)
internal val StorageCritical = Color(0xFFFF7F8D)
