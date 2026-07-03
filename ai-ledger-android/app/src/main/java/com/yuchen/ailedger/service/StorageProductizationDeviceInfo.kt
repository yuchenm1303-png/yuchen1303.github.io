package com.yuchen.ailedger.service

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import java.util.Locale

internal class StorageProductizationDeviceInfo(
    context: Context,
    private val storageRepository: StorageManagementRepository,
) {
    private val appContext = context.applicationContext

    fun guard(): StorageDeviceGuard {
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { appContext.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull()
        } else null
        val result = StorageProductizationPolicy.heavyWorkAllowed(percent, charging, thermal)
        return StorageDeviceGuard(
            batteryPercent = percent,
            charging = charging,
            thermalStatus = thermal,
            thermalLabel = thermalLabel(thermal),
            heavyWorkAllowed = result.first,
            reason = result.second,
        )
    }

    fun permissions(): StoragePermissionHealth {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && has(Manifest.permission.READ_EXTERNAL_STORAGE)
        val images = legacy || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && has(Manifest.permission.READ_MEDIA_IMAGES))
        val videos = legacy || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && has(Manifest.permission.READ_MEDIA_VIDEO))
        val audio = legacy || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && has(Manifest.permission.READ_MEDIA_AUDIO))
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) && !images && !videos
        val treeUri = storageRepository.savedTreeUri()
        val validTree = treeUri == null || appContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        return StoragePermissionHealth(
            usageAccessGranted = usageAccess(),
            imageAccessGranted = images,
            videoAccessGranted = videos,
            audioAccessGranted = audio,
            selectedPhotoAccessOnly = selected,
            authorizedFolderPresent = treeUri != null,
            authorizedFolderPermissionValid = validTree,
        )
    }

    fun compatibility(): StorageCompatibilityReport {
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "未知" }
        val brand = Build.BRAND.orEmpty().ifBlank { "未知" }
        val model = Build.MODEL.orEmpty().ifBlank { "未知" }
        val clean = "$manufacturer $brand".lowercase(Locale.ROOT)
        val guidance = when {
            listOf("xiaomi", "redmi", "poco").any(clean::contains) -> listOf(
                "MIUI/HyperOS 可能额外限制后台和文件访问，权限失效时请重新打开系统授权页。",
                "系统清理工具可能同时修改媒体库，清理后建议重新扫描核验。",
            )
            listOf("huawei", "honor").any(clean::contains) -> listOf(
                "部分系统版本会回收使用情况访问权限，应用占用为空时请检查特殊访问权限。",
                "云图库占位文件可能无法读取，本页会跳过不可访问内容。",
            )
            else -> listOf(
                "厂商可能回收使用情况或目录授权，权限健康区会显示当前实际状态。",
                "低电量和高温只显示状态，不再缩小或暂停完整分析范围。",
            )
        }
        return StorageCompatibilityReport(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            sdk = Build.VERSION.SDK_INT,
            title = "$brand $model · Android API ${Build.VERSION.SDK_INT}",
            guidance = guidance,
        )
    }

    private fun usageAccess(): Boolean {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun thermalLabel(status: Int?): String = when (status) {
        null -> "系统未提供"
        PowerManager.THERMAL_STATUS_NONE -> "正常"
        PowerManager.THERMAL_STATUS_LIGHT -> "轻微升温"
        PowerManager.THERMAL_STATUS_MODERATE -> "温度偏高"
        PowerManager.THERMAL_STATUS_SEVERE -> "高温"
        PowerManager.THERMAL_STATUS_CRITICAL -> "严重高温"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "紧急"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "即将关机"
        else -> "未知"
    }
}
