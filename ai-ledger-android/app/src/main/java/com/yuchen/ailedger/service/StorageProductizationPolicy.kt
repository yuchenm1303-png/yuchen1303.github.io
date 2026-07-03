package com.yuchen.ailedger.service

import android.os.PowerManager

internal object StorageProductizationPolicy {
    fun unusedDays(lastUsedAt: Long?, firstInstallTime: Long, now: Long): Int? {
        val reference = when {
            lastUsedAt != null && lastUsedAt > 0L -> lastUsedAt
            firstInstallTime > 0L -> firstInstallTime
            else -> return null
        }
        return ((now - reference).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
    }

    fun longUnusedReason(unusedDays: Int?, usageKnown: Boolean): String = when {
        !usageKnown -> "未获得使用情况访问权限，只展示占用信息"
        unusedDays == null -> "系统未提供可用的最近使用时间"
        unusedDays >= STORAGE_APP_UNUSED_DAYS -> "约 $unusedDays 天未使用，建议确认后进入系统页处理"
        else -> "最近约 $unusedDays 天内使用过"
    }

    fun heavyWorkAllowed(
        batteryPercent: Int?,
        charging: Boolean,
        thermalStatus: Int?,
    ): Pair<Boolean, String> {
        val reason = when {
            thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE ->
                "设备温度较高，仍按完整分析模式继续执行"
            !charging && batteryPercent != null && batteryPercent < 25 ->
                "当前电量较低，仍按完整分析模式继续执行"
            thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ->
                "设备温度偏高，分析不会减少覆盖范围"
            charging -> "设备正在充电，完整分析可继续"
            else -> "设备状态正常，完整分析可继续"
        }
        return true to reason
    }
}
