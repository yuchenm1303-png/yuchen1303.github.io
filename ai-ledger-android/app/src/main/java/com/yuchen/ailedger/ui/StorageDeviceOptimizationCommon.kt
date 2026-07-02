package com.yuchen.ailedger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StoragePermissionHealth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OptimizeOverviewCard(
    title: String,
    value: String,
    detail: String,
    tone: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = tone.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                Text(value, color = tone, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.7.sp)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun OptimizeMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = 0.055f)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun OptimizeTinyMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.32f), fontSize = 8.5.sp)
        Text(value, color = Color.White.copy(alpha = 0.68f), fontSize = 9.3.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun OptimizeSectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun OptimizeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) OptimizeAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, if (selected) OptimizeAccent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            label,
            color = if (selected) OptimizeAccent else Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun OptimizeInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = tone.copy(alpha = 0.072f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.17f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = tone, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
internal fun OptimizeEmptyPanel(text: String) {
    OptimizeInfoPanel("暂无结果", text, Color.White)
}

@Composable
internal fun OptimizeLoadingPanel(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.05f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp, color = OptimizeAccent)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun OptimizePrimaryAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = OptimizeAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, OptimizeAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = OptimizeAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(11.dp),
        )
    }
}

@Composable
internal fun OptimizeSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
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

internal fun permissionSummary(health: StoragePermissionHealth): String {
    return buildList {
        if (!health.usageAccessGranted) add("使用情况")
        if (!health.mediaAccessGranted) add("媒体")
        if (health.authorizedFolderPresent && !health.authorizedFolderPermissionValid) add("目录授权")
    }.joinToString("、").ifBlank { "使用情况、媒体和目录授权均可用" }
}

internal fun storageMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }.toTypedArray()
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

internal fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

internal fun formatOptimizeBytes(bytes: Long): String {
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

internal fun formatOptionalBytes(bytes: Long?): String = bytes?.let(::formatOptimizeBytes) ?: "需授权"

internal fun formatOptimizeDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

internal val OptimizeAccent = Color(0xFF8DF9EA)
internal val OptimizeSuccess = Color(0xFF83F3B8)
internal val OptimizeWarning = Color(0xFFFFCA72)
internal val OptimizeCritical = Color(0xFFFF7F8D)
