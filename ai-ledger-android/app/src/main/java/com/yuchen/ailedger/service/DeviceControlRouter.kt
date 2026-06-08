package com.yuchen.ailedger.service

import java.util.Locale

/**
 * Decides whether a user goal should be executed by the non-visual device tool layer
 * before entering the visual agent loop.
 *
 * This router is capability-oriented: it only opts in for explicit device/system
 * controls that DeviceControlRuntime actually owns. App navigation and ambiguous
 * goals stay with the agent planner.
 */
object DeviceControlRouter {
    fun shouldTryInternalFirst(goal: String): Boolean {
        val text = normalize(goal)
        if (text.isBlank()) return false
        return deviceStateSignals.any { text.contains(it) } ||
            writableSystemSignals.any { text.contains(it) } ||
            systemSettingsSignals.any { text.contains(it) } ||
            appSystemSettingsSignals.any { text.contains(it) } ||
            shellStatusSignals.any { text.contains(it) }
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), "")
            .replace("％", "%")
    }

    private val deviceStateSignals = listOf(
        "手机体检", "设备体检", "手机状态", "设备状态", "健康报告", "为什么卡", "卡顿", "存储空间", "内存情况",
    )

    private val writableSystemSignals = listOf(
        "亮度", "屏幕亮度", "锁屏时间", "息屏时间", "自动锁屏", "屏幕超时", "休眠时间",
    )

    private val systemSettingsSignals = listOf(
        "wifi设置", "wi-fi设置", "无线网设置", "无线网络设置", "蓝牙设置", "系统通知设置", "通知管理",
        "电池设置", "电量设置", "省电设置", "省电模式", "存储设置", "储存设置", "空间设置", "应用管理",
        "应用列表", "无障碍设置", "辅助功能设置", "显示设置", "屏幕设置", "声音设置", "音量设置", "定位设置",
        "位置设置", "gps设置", "流量设置", "数据使用", "移动数据设置", "开发者选项", "开发者设置",
        "修改系统设置", "写入系统设置", "系统设置授权",
    )

    private val appSystemSettingsSignals = listOf(
        "通知设置", "通知权限", "应用通知", "app通知", "权限设置", "权限管理", "应用权限", "app权限",
        "耗电设置", "后台限制", "电池优化", "应用信息", "应用详情", "app信息", "app详情", "app设置",
    )

    private val shellStatusSignals = listOf(
        "shizuku", "adb", "shell状态", "增强模式", "内部控制状态", "控制能力", "能力列表", "codex能力",
        "设备工具状态", "devicecontrol", "shell身份", "运行身份", "uid状态", "系统属性", "设备属性", "getprop",
        "安卓版本", "android版本", "动画缩放状态", "查看动画缩放", "读取动画缩放", "电池dumpsys", "dumpsys电池",
        "电池详情", "电池dump",
    )
}
