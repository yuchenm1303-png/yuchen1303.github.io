package com.yuchen.ailedger.service

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageProductizationPolicyTest {
    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun unusedDaysPrefersLastUsedTime() {
        val now = 200L * dayMs

        assertEquals(
            120,
            StorageProductizationPolicy.unusedDays(
                lastUsedAt = now - 120L * dayMs,
                firstInstallTime = now - 180L * dayMs,
                now = now,
            ),
        )
    }

    @Test
    fun unusedDaysFallsBackToInstallTimeWhenSystemHasNoUsageEvent() {
        val now = 200L * dayMs

        assertEquals(
            100,
            StorageProductizationPolicy.unusedDays(
                lastUsedAt = null,
                firstInstallTime = now - 100L * dayMs,
                now = now,
            ),
        )
        assertNull(StorageProductizationPolicy.unusedDays(null, 0L, now))
    }

    @Test
    fun lowBatteryBlocksHeavyAnalysisUnlessCharging() {
        val blocked = StorageProductizationPolicy.heavyWorkAllowed(
            batteryPercent = 19,
            charging = false,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        )
        val charging = StorageProductizationPolicy.heavyWorkAllowed(
            batteryPercent = 19,
            charging = true,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        )

        assertFalse(blocked.first)
        assertTrue(charging.first)
    }

    @Test
    fun severeThermalStatusAlwaysBlocksAnalysis() {
        val result = StorageProductizationPolicy.heavyWorkAllowed(
            batteryPercent = 90,
            charging = true,
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
        )

        assertFalse(result.first)
        assertTrue(result.second.contains("温度"))
    }

    @Test
    fun usagePermissionMessageDoesNotPretendAppIsUnused() {
        assertEquals(
            "未获得使用情况访问权限，只展示占用信息",
            StorageProductizationPolicy.longUnusedReason(unusedDays = 180, usageKnown = false),
        )
    }
}
