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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StorageFolderIndexState
import com.yuchen.ailedger.service.StorageIndexedLargeFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FolderGuardPanel(state: StorageFolderIndexState) {
    val guard = state.deviceGuard
    val tone = if (guard.heavyWorkAllowed) FolderSuccess else FolderCritical
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("负载保护", color = tone, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                Text(guard.reason, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, lineHeight = 14.sp)
            }
            Text(guard.batteryPercent?.let { "$it%" } ?: "电量未知", color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun FolderProgressPanel(
    state: StorageFolderIndexState,
    scanning: Boolean,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    val progress = state.progress ?: return
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
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(progress.rootName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        when {
                            progress.complete -> "索引完成"
                            progress.interrupted -> "已暂停，可从断点继续"
                            else -> "分页索引进行中"
                        },
                        color = FolderAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (scanning) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = FolderAccent)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FolderMetric("文件", progress.scannedFiles.toString(), Modifier.weight(1f))
                FolderMetric("体积", formatFolderBytes(progress.scannedBytes), Modifier.weight(1f))
                FolderMetric("待处理目录", progress.queuedDirectories.toString(), Modifier.weight(1f))
            }
            progress.currentPath?.let { path ->
                Text(
                    "当前位置：$path",
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 9.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (scanning) {
                FolderSecondaryAction("暂停并保存", Modifier.fillMaxWidth(), onStop)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FolderSecondaryAction("重新索引", Modifier.weight(1f), onRestart)
                    FolderPrimaryAction(
                        text = if (progress.complete) "已完成" else "继续下一批",
                        enabled = !progress.complete && state.deviceGuard.heavyWorkAllowed,
                        modifier = Modifier.weight(1f),
                        onClick = onContinue,
                    )
                }
                FolderSecondaryAction("更换授权目录", Modifier.fillMaxWidth(), onChangeFolder)
            }
        }
    }
}

@Composable
internal fun IndexedFileCard(file: StorageIndexedLargeFile, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onOpen),
        shape = shape,
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    file.displayName,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(file.location, color = Color.White.copy(alpha = 0.30f), fontSize = 8.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatFolderDate(file.modifiedAt), color = Color.White.copy(alpha = 0.38f), fontSize = 8.8.sp)
            }
            Text(formatFolderBytes(file.sizeBytes), color = FolderAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun FolderMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.05f)) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 8.7.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White.copy(alpha = 0.84f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun FolderSectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FolderInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = tone.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.17f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = tone.copy(alpha = 0.92f), fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
internal fun FolderLoadingPanel(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.05f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp, color = FolderAccent)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun FolderPrimaryAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = FolderAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, FolderAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = FolderAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(11.dp),
        )
    }
}

@Composable
internal fun FolderSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(11.dp),
        )
    }
}

internal fun formatFolderBytes(bytes: Long): String {
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

internal fun formatFolderDate(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

internal val FolderAccent = Color(0xFF9CD8FF)
internal val FolderSuccess = Color(0xFF83F3B8)
internal val FolderWarning = Color(0xFFFFCA72)
internal val FolderCritical = Color(0xFFFF7F8D)
