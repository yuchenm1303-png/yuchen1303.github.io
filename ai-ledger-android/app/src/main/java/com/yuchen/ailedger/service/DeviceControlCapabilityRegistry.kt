package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceControlRiskLevel(val label: String) {
    Low("低风险"),
    Medium("中风险"),
    High("高风险"),
    Critical("极高风险"),
}

data class DeviceControlCapability(
    val id: String,
    val title: String,
    val status: String,
    val riskLevel: DeviceControlRiskLevel,
    val description: String,
    val examples: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("status", status)
        put("riskLevel", riskLevel.name.lowercase())
        put("riskLabel", riskLevel.label)
        put("description", description)
        put("examples", JSONArray().apply { examples.forEach { put(it) } })
    }
}

object DeviceControlCapabilityRegistry {
    val capabilities: List<DeviceControlCapability> = listOf(
        DeviceControlCapability(
            id = "device.health",
            title = "手机体检",
            status = "implemented",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "读取电量、内存、存储、网络和可启动应用数量，不操作其他应用。",
            examples = listOf("手机体检一下", "为什么手机卡", "看一下存储空间"),
        ),
        DeviceControlCapability(
            id = "settings.open",
            title = "打开系统设置入口",
            status = "implemented",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "通过 Android Settings Intent 直达 Wi‑Fi、蓝牙、电池、存储、无障碍、开发者选项等页面。",
            examples = listOf("打开 Wi-Fi 设置", "打开无障碍设置", "打开开发者选项"),
        ),
        DeviceControlCapability(
            id = "app.open",
            title = "打开应用",
            status = "implemented",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "通过 PackageManager 查找启动入口并打开应用；App 内页面仍交给 Computer Use 兜底。",
            examples = listOf("打开 QQ", "启动微信"),
        ),
        DeviceControlCapability(
            id = "app.settings",
            title = "打开 App 专属系统设置",
            status = "implemented",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "打开指定 App 的通知、权限、电池/后台或应用信息入口。",
            examples = listOf("打开 QQ 通知设置", "打开微信权限设置", "打开抖音电池设置"),
        ),
        DeviceControlCapability(
            id = "system.brightness",
            title = "调节屏幕亮度",
            status = "implemented_requires_write_settings",
            riskLevel = DeviceControlRiskLevel.Medium,
            description = "用户授予修改系统设置后，可直接写入屏幕亮度。",
            examples = listOf("把亮度调到 40%"),
        ),
        DeviceControlCapability(
            id = "system.screen_timeout",
            title = "设置自动锁屏时间",
            status = "implemented_requires_write_settings",
            riskLevel = DeviceControlRiskLevel.Medium,
            description = "用户授予修改系统设置后，可直接写入 SCREEN_OFF_TIMEOUT。",
            examples = listOf("自动锁屏改成 1 分钟"),
        ),
        DeviceControlCapability(
            id = "shell.probe",
            title = "Shell/Shizuku 增强模式探测",
            status = "implemented_shizuku_probe",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "检测基础 shell、Shizuku 服务、授权状态和增强执行身份。",
            examples = listOf("查看内部控制状态", "Shizuku 状态", "ADB 增强模式状态"),
        ),
        DeviceControlCapability(
            id = "shell.safe_read",
            title = "安全只读 Shell 诊断",
            status = "implemented_limited_shizuku",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "执行只读 allowlist 命令；Shizuku 已授权时优先走 Shizuku，否则降级普通 shell。",
            examples = listOf("查看系统属性", "查看动画缩放状态", "查看电池 dumpsys"),
        ),
        DeviceControlCapability(
            id = "shell.shizuku_permission",
            title = "请求 Shizuku 授权",
            status = "implemented_requires_shizuku_running",
            riskLevel = DeviceControlRiskLevel.Low,
            description = "检测到 Shizuku 服务后，可发起本应用授权请求。",
            examples = listOf("请求 Shizuku 授权", "授权 Shizuku"),
        ),
        DeviceControlCapability(
            id = "app.force_stop",
            title = "强停应用",
            status = "implemented_requires_shizuku_and_confirmation",
            riskLevel = DeviceControlRiskLevel.High,
            description = "Shizuku/ADB 增强身份可执行 am force-stop；执行前必须二次确认。",
            examples = listOf("强停抖音", "关闭淘宝后台"),
        ),
        DeviceControlCapability(
            id = "app.clear_data",
            title = "清除应用数据",
            status = "implemented_requires_shizuku_and_confirmation",
            riskLevel = DeviceControlRiskLevel.Critical,
            description = "Shizuku/ADB 增强身份可执行 pm clear；会删除目标 App 本地数据，必须二次确认。",
            examples = listOf("清除某 App 数据"),
        ),
        DeviceControlCapability(
            id = "system.settings_global_write",
            title = "写入 global settings",
            status = "implemented_requires_shizuku_and_confirmation",
            riskLevel = DeviceControlRiskLevel.High,
            description = "Shizuku/ADB 增强身份可写入 global settings；当前用于动画缩放等内部控制。",
            examples = listOf("关闭动画缩放", "把动画缩放调到 0.5"),
        ),
        DeviceControlCapability(
            id = "system.app_enable_disable",
            title = "启用/禁用应用",
            status = "implemented_requires_shizuku_and_confirmation",
            riskLevel = DeviceControlRiskLevel.Critical,
            description = "Shizuku/ADB 增强身份可执行 pm enable / pm disable-user；必须二次确认。",
            examples = listOf("禁用某 App", "启用某 App"),
        ),
    )

    fun toJsonArray(): JSONArray = JSONArray().apply { capabilities.forEach { put(it.toJson()) } }

    fun publicSummary(): String {
        val grouped = capabilities.groupBy { it.status.substringBefore('_') }
        val implemented = grouped["implemented"].orEmpty()
        val planned = grouped["planned"].orEmpty()
        return buildString {
            append("已接入能力：")
            append(implemented.joinToString("、") { it.title }.ifBlank { "暂无" })
            append("\n待增强能力：")
            append(planned.joinToString("、") { it.title }.ifBlank { "暂无" })
        }
    }
}
