package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Test

class NormalChatDeviceIntentPolicyTest {
    @Test
    fun androidNeverRoutesNaturalLanguageIntoDeviceTools() {
        listOf(
            "解释一下蓝牙的工作原理",
            "请帮我打开微信",
            "设置明天早上七点的闹钟",
            "帮我记一杯 16 元奶茶",
            "记住我喜欢奶茶",
            "/agent 打开微信",
        ).forEach { text ->
            assertFalse(NormalChatDeviceIntentPolicy.shouldProbe(text))
            assertFalse(NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps(text))
        }
    }
}
