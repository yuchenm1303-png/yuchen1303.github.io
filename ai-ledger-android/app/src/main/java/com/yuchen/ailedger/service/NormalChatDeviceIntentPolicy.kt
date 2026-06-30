package com.yuchen.ailedger.service

/**
 * Cheap, deterministic gate in front of the normal-chat device planner.
 *
 * It does not choose an action or an app. It only prevents ordinary questions, writing, code and
 * explanations from paying for an additional device-planning request and PackageManager inventory.
 * The cloud planner and Android validators remain the authority once a request passes this gate.
 */
internal object NormalChatDeviceIntentPolicy {
    private val explicitAgentPrefixes = listOf(
        "/agent",
        "/智能体",
        "agent:",
        "agent：",
        "智能体:",
        "智能体：",
    )

    private val imperativeSignals = listOf(
        "帮我",
        "请帮",
        "给我",
        "替我",
        "帮忙",
        "把",
        "现在",
        "立刻",
    )

    private val actionSignals = listOf(
        "打开",
        "开启",
        "关闭",
        "关掉",
        "启动",
        "运行",
        "进入",
        "切换到",
        "跳转到",
        "设置",
        "设一个",
        "设个",
        "创建",
        "取消",
        "调高",
        "调低",
        "增大",
        "减小",
        "发送",
        "拨打",
        "打电话",
        "导航",
        "带我去",
        "提醒我",
        "open ",
        "launch ",
        "turn on",
        "turn off",
        "navigate",
        "remind me",
    )

    private val deviceTargets = listOf(
        "闹钟",
        "提醒",
        "定时器",
        "导航",
        "地图",
        "应用",
        "app",
        "微信",
        "支付宝",
        "qq",
        "淘宝",
        "京东",
        "抖音",
        "小红书",
        "哔哩哔哩",
        "b站",
        "浏览器",
        "相机",
        "设置",
        "电话",
        "短信",
        "邮件",
        "邮箱",
        "日历",
        "备忘录",
        "音乐",
        "播放器",
        "wifi",
        "wi-fi",
        "无线网",
        "蓝牙",
        "移动数据",
        "蜂窝数据",
        "飞行模式",
        "深色模式",
        "夜间模式",
        "亮度",
        "音量",
        "静音",
        "震动",
        "手电筒",
        "热点",
        "package",
    )

    private val explanationSignals = listOf(
        "怎么实现",
        "如何实现",
        "代码",
        "源码",
        "原理",
        "为什么",
        "解释",
        "教程",
        "文档",
        "示例",
        "翻译",
        "总结",
        "分析一下",
    )

    private val systemOnlyTargets = listOf(
        "wifi",
        "wi-fi",
        "无线网",
        "蓝牙",
        "移动数据",
        "蜂窝数据",
        "飞行模式",
        "深色模式",
        "夜间模式",
        "亮度",
        "音量",
        "静音",
        "震动",
        "手电筒",
        "热点",
        "闹钟",
        "提醒",
        "定时器",
        "导航",
    )

    fun shouldProbe(text: String): Boolean {
        val clean = normalize(text)
        if (clean.isBlank()) return false
        if (explicitAgentPrefixes.any(clean::startsWith)) return false
        if (explanationSignals.any(clean::contains)) return false

        val hasAction = actionSignals.any(clean::contains)
        val hasTarget = deviceTargets.any(clean::contains)
        val imperative = imperativeSignals.any(clean::contains) || actionSignals.any(clean::startsWith)
        return hasAction && hasTarget && imperative
    }

    fun shouldIncludeInstalledApps(text: String): Boolean {
        val clean = normalize(text)
        if (!shouldProbe(clean)) return false
        if (systemOnlyTargets.any(clean::contains) && !containsKnownAppName(clean)) return false

        val appLaunchSignal = listOf(
            "打开",
            "启动",
            "运行",
            "进入",
            "切换到",
            "跳转到",
            "发送",
            "拨打",
            "打电话",
            "open ",
            "launch ",
        ).any(clean::contains)
        return appLaunchSignal
    }

    private fun containsKnownAppName(text: String): Boolean {
        return listOf(
            "微信",
            "支付宝",
            "qq",
            "淘宝",
            "京东",
            "抖音",
            "小红书",
            "哔哩哔哩",
            "b站",
            "浏览器",
            "相机",
            "邮件",
            "邮箱",
            "日历",
            "备忘录",
            "音乐",
            "播放器",
            "地图",
        ).any(text::contains)
    }

    private fun normalize(text: String): String = text.trim().lowercase()
}
