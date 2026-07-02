package com.yuchen.ailedger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.service.StorageIntelligenceFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun toggleIntelligenceSelection(
    selectedIds: Set<String>,
    file: StorageIntelligenceFile,
): Set<String> {
    if (!file.canDelete) return selectedIds
    return if (file.stableId in selectedIds) selectedIds - file.stableId else selectedIds + file.stableId
}

internal fun Context.hasStorageMediaAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_AUDIO)
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        images || videos || audio || selected
    } else {
        hasPermissionForIntelligence(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasPermissionForIntelligence(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun formatIntelligenceBytes(bytes: Long): String {
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

internal fun formatIntelligenceDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

internal fun formatIntelligenceAge(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    val days = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
    return when {
        days >= 365 -> "约 ${days / 365} 年"
        else -> "约 $days 天"
    }
}

internal fun formatElapsed(elapsedMs: Long): String {
    return if (elapsedMs < 1_000L) "$elapsedMs ms" else String.format(Locale.CHINA, "%.1f 秒", elapsedMs / 1_000.0)
}

internal val IntelligenceAccent = Color(0xFF8DF9EA)
internal val IntelligenceSuccess = Color(0xFF83F3B8)
internal val IntelligenceWarning = Color(0xFFFFCA72)
internal val IntelligenceCritical = Color(0xFFFF7F8D)
