package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceControlActionVerifierTest {
    @Test
    fun extractsExecutorConfirmedTargetPercent() {
        assertEquals(55f, targetPercentFromExecutionMessage("已把屏幕亮度从约 40% 调到约 55%。") ?: -1f, 0.001f)
        assertEquals(72f, targetPercentFromExecutionMessage("已把媒体音量从约 57% 调到约 72%。") ?: -1f, 0.001f)
        assertEquals(37.5f, targetPercentFromExecutionMessage("已调到约 37.5%。") ?: -1f, 0.001f)
    }

    @Test
    fun ignoresMessagesWithoutConfirmedTarget() {
        assertNull(targetPercentFromExecutionMessage("调节亮度失败。"))
        assertNull(targetPercentFromExecutionMessage("当前亮度约 55%。"))
    }
}
