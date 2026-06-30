package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatDeviceIntentPolicyTest {
    @Test
    fun ordinaryKnowledgeAndCodeRequestsStayOutOfDevicePlanner() {
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("解释一下蓝牙的工作原理"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("用 Kotlin 写一个打开文件的示例"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("帮我设置这个变量的初始值"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("总结一下今天的课程内容"))
    }

    @Test
    fun explicitDeviceRequestsEnterPlanner() {
        assertTrue(NormalChatDeviceIntentPolicy.shouldProbe("请帮我打开微信"))
        assertTrue(NormalChatDeviceIntentPolicy.shouldProbe("设置明天早上七点的闹钟"))
        assertTrue(NormalChatDeviceIntentPolicy.shouldProbe("请打开蓝牙"))
        assertTrue(NormalChatDeviceIntentPolicy.shouldProbe("请进入设置"))
    }

    @Test
    fun explicitAgentPrefixDoesNotRunNormalChatProbe() {
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("/agent 打开微信"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldProbe("智能体：打开微信"))
    }

    @Test
    fun appInventoryIsOnlyRequestedForAppFacingActions() {
        assertTrue(NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps("请帮我打开微信"))
        assertTrue(NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps("请进入设置"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps("请打开蓝牙"))
        assertFalse(NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps("设置明天七点的闹钟"))
    }
}
