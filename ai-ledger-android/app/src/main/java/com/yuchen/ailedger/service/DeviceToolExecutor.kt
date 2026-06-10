package com.yuchen.ailedger.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import kotlin.math.roundToInt

/**
 * Executes structured device_tool steps from AgentBrain.
 *
 * This layer deliberately does not parse the user's natural-language goal. The cloud brain must
 * already have selected a concrete tool and arguments. Android only validates and executes that
 * structure, so it cannot steal visual-agent tasks by keyword matching.
 */
class DeviceToolExecutor(
    context: Context,
    private val installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val shellBridge = DeviceShellBridge(appContext)

    fun canExecute(step: CloudAgentStep): Boolean {
        return step.type in executableDeviceToolTypes
    }

    fun execute(step: CloudAgentStep, confirmedHighRisk: Boolean = false): AgentExecutionResult {
        return runCatching {
            when (step.type) {
                "open_app" -> executeOpenApp(step)
                "open_system_settings" -> executeOpenSystemSettings(step)
                "open_app_settings" -> executeOpenAppSettings(step)
                "set_brightness" -> executeSetBrightness(step)
                "set_screen_timeout" -> executeSetScreenTimeout(step)
                "device_status" -> deviceStatus()
                "shizuku_status" -> shellStatus()
                "request_shizuku_permission" -> requestShizukuPermission()
                "set_animation_scale" -> executeAnimationScale(step, confirmedHighRisk)
                "force_stop_app" -> executePrivilegedAppTool(step, PrivilegedTool.ForceStop, confirmedHighRisk)
                "clear_app_data" -> executePrivilegedAppTool(step, PrivilegedTool.ClearData, confirmedHighRisk)
                "uninstall_app" -> executePrivilegedAppTool(step, PrivilegedTool.UninstallForUser, confirmedHighRisk)
                "disable_app" -> executePrivilegedAppTool(step, PrivilegedTool.Disable, confirmedHighRisk)
                "enable_app" -> executePrivilegedAppTool(step, PrivilegedTool.Enable, confirmedHighRisk)
                else -> AgentExecutionResult(false, "不支持的内部设备工具：${step.type}", false)
            }
        }.getOrElse { error ->
            AgentExecutionResult(
                ok = false,
                message = "内部设备工具运行异常：${error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}",
                shouldContinue = false,
            )
        }
    }

    private fun executeOpenApp(step: CloudAgentStep): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "没有找到要打开的应用：${step.appName ?: step.targetText ?: step.argString("appName", "app", "packageName") ?: "未知"}", false)
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AgentExecutionResult(false, "应用没有可启动入口：${app.label}（${app.packageName}）", false)
        val ok = launchActivity(launchIntent)
        return AgentExecutionResult(ok, if (ok) "已打开 ${app.label}。" else "打开 ${app.label} 失败。", shouldContinue = ok)
    }

    private fun executeOpenSystemSettings(step: CloudAgentStep): AgentExecutionResult {
        val target = systemSettingTarget(step)
        val ok = launchActivity(Intent(target.action))
        return AgentExecutionResult(
            ok = ok,
            message = if (ok) "已打开${target.title}。" else "无法打开${target.title}，当前系统可能不支持该设置入口。",
            shouldContinue = ok,
        )
    }

    private fun executeOpenAppSettings(step: CloudAgentStep): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "打开应用设置失败：没有找到目标应用。", false)
        val kind = appSettingsKind(step)
        val intent = when (kind) {
            AppSettingsKind.Notification -> Intent(ACTION_APP_NOTIFICATION_SETTINGS_COMPAT).apply {
                putExtra(EXTRA_APP_PACKAGE_COMPAT, app.packageName)
            }
            AppSettingsKind.Permission,
            AppSettingsKind.Battery,
            AppSettingsKind.Details -> Intent(ACTION_APPLICATION_DETAILS_SETTINGS_COMPAT, Uri.parse("package:${app.packageName}"))
        }
        val title = when (kind) {
            AppSettingsKind.Notification -> "${app.label} 通知设置"
            AppSettingsKind.Permission -> "${app.label} 应用权限入口"
            AppSettingsKind.Battery -> "${app.label} 电池/后台入口"
            AppSettingsKind.Details -> "${app.label} 应用信息"
        }
        val detail = when (kind) {
            AppSettingsKind.Permission -> "系统限制下先打开应用信息页，请在里面进入“权限”。"
            AppSettingsKind.Battery -> "系统限制下先打开应用信息页，请在里面进入“电池/后台管理”。"
            else -> ""
        }
        val ok = launchActivity(intent)
        return AgentExecutionResult(ok, if (ok) "已打开$title。$detail".trim() else "无法打开$title。", shouldContinue = ok)
    }

    private fun executeSetBrightness(step: CloudAgentStep): AgentExecutionResult {
        val percent = step.argFloat("percent", "brightness", "value")
            ?: firstNumber(step.text ?: step.targetText ?: step.reason.orEmpty())?.toFloat()
            ?: return AgentExecutionResult(false, "调节亮度失败：缺少 0–100 的亮度百分比。", false)
        val safePercent = percent.coerceIn(0f, 100f)
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return AgentExecutionResult(false, "需要先授权“修改系统设置”。我已打开授权页，开启后可再次执行亮度设置。", false)
        }
        val value = (safePercent * 255f / 100f).roundToInt().coerceIn(0, 255)
        val ok = runCatching {
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.getOrDefault(false)
        return AgentExecutionResult(ok, if (ok) "已把屏幕亮度调到约 ${safePercent.roundToInt()}%。" else "调节亮度失败，当前系统可能限制第三方应用修改该设置。", shouldContinue = false)
    }

    private fun executeSetScreenTimeout(step: CloudAgentStep): AgentExecutionResult {
        val timeoutMs = screenTimeoutMs(step)
            ?: return AgentExecutionResult(false, "设置息屏时间失败：缺少明确时长，比如 30 秒或 5 分钟。", false)
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return AgentExecutionResult(false, "需要先授权“修改系统设置”。我已打开授权页，开启后可再次执行息屏时间设置。", false)
        }
        val ok = runCatching {
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
        }.getOrDefault(false)
        return AgentExecutionResult(ok, if (ok) "已把自动息屏时间设置为 ${formatTimeout(timeoutMs)}。" else "设置息屏时间失败，当前系统可能限制第三方应用修改该设置。", shouldContinue = false)
    }

    private fun deviceStatus(): AgentExecutionResult {
        val memory = runCatching { memoryStatus() }.getOrDefault("未知")
        val storage = runCatching { storageStatus() }.getOrDefault("未知")
        val battery = runCatching { batteryStatus() }.getOrDefault("未知")
        val network = runCatching { networkStatus() }.getOrDefault("未知")
        val appCount = runCatching { installedAppIndex.getLaunchableApps(forceReload = false).size }.getOrDefault(0)
        val shell = runCatching { shellBridge.probe() }.getOrNull()
        val shellText = shell?.let {
            buildString {
                append(if (it.available) "基础可用" else "不可用")
                append(" · ").append(if (it.isAdbShellLike) "增强级" else "App 沙箱级")
                append(" · Shizuku ").append(if (it.shizukuGranted) "已授权" else if (it.shizukuAvailable) "待授权" else "未运行")
            }
        } ?: "状态读取失败"
        val message = buildString {
            append("手机内部状态\n\n")
            append("电量：").append(battery).append('\n')
            append("内存：").append(memory).append('\n')
            append("存储：").append(storage).append('\n')
            append("网络：").append(network).append('\n')
            append("可启动应用：").append(appCount).append(" 个\n")
            append("Shell：").append(shellText)
        }
        return AgentExecutionResult(true, message, shouldContinue = false)
    }

    private fun shellStatus(): AgentExecutionResult {
        return runCatching {
            AgentExecutionResult(true, shellBridge.enhancedModeGuide(), shouldContinue = false)
        }.getOrElse { error ->
            AgentExecutionResult(false, "读取 Shizuku/Shell 状态失败：${error.message ?: error::class.java.simpleName}", shouldContinue = false)
        }
    }

    private fun requestShizukuPermission(): AgentExecutionResult {
        val result = shellBridge.requestShizukuPermission()
        val message = buildString {
            append(result.output.ifBlank { result.title })
            if (result.error.isNotBlank()) append("\n\n错误：").append(result.error)
        }
        return AgentExecutionResult(result.ok, message, shouldContinue = false)
    }

    private fun executeAnimationScale(step: CloudAgentStep, confirmedHighRisk: Boolean): AgentExecutionResult {
        val scale = (step.argFloat("scale", "value") ?: firstDecimal(step.text ?: step.targetText ?: step.reason.orEmpty()) ?: 0.5f)
            .coerceIn(0f, 10f)
        val scaleText = formatShellScale(scale)
        val command = listOf(
            "settings put global window_animation_scale $scaleText",
            "settings put global transition_animation_scale $scaleText",
            "settings put global animator_duration_scale $scaleText",
        ).joinToString(" && ")
        return executeEnhancedCommandIfConfirmed(
            title = "设置动画缩放",
            command = command,
            timeoutMs = 2_000L,
            confirmedHighRisk = confirmedHighRisk,
            pendingMessage = "设置动画缩放属于 global settings 写入，需要确认后执行：$scaleText。",
            successMessage = "已把窗口/过渡/动画程序时长缩放设置为 $scaleText。",
        )
    }

    private fun executePrivilegedAppTool(step: CloudAgentStep, tool: PrivilegedTool, confirmedHighRisk: Boolean): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "${tool.title}失败：没有找到目标应用。", false)
        val packageName = app.packageName.takeIf { isSafePackageName(it) }
            ?: return AgentExecutionResult(false, "${tool.title}失败：目标包名格式异常。", false)
        return executeEnhancedCommandIfConfirmed(
            title = tool.title,
            command = tool.command(packageName),
            timeoutMs = tool.timeoutMs,
            confirmedHighRisk = confirmedHighRisk,
            pendingMessage = "${tool.title} ${app.label}（$packageName）属于高风险内部控制，需要确认后执行。",
            successMessage = "已执行：${tool.title} ${app.label}。",
        )
    }

    private fun executeEnhancedCommandIfConfirmed(
        title: String,
        command: String,
        timeoutMs: Long,
        confirmedHighRisk: Boolean,
        pendingMessage: String,
        successMessage: String,
    ): AgentExecutionResult {
        if (!confirmedHighRisk) return AgentExecutionResult(false, pendingMessage, shouldContinue = false)
        val result = shellBridge.runEnhancedCommand(title = title, command = command, timeoutMs = timeoutMs)
        val message = buildString {
            append(if (result.ok) successMessage else "$title 执行失败。")
            result.exitCode?.let { append("\nexit=").append(it) }
            if (result.output.isNotBlank() && result.output != "无输出") append("\n\n输出：").append(result.output)
            if (result.error.isNotBlank()) append("\n\n错误：").append(result.error)
        }
        return AgentExecutionResult(result.ok, message, shouldContinue = false)
    }

    private fun resolveApp(step: CloudAgentStep): InstalledAppEntry? {
        val packageName = step.packageName
            ?: step.argString("packageName", "package", "pkg")
        if (!packageName.isNullOrBlank()) {
            val label = applicationLabel(packageName).ifBlank { step.appName ?: step.argString("appName", "app", "label", "name") ?: packageName }
            return InstalledAppEntry(label = label, packageName = packageName)
                .takeIf { appContext.packageManager.getLaunchIntentForPackage(it.packageName) != null || step.type != "open_app" }
        }
        val query = step.appName
            ?: step.targetText
            ?: step.argString("appName", "app", "label", "name", "target")
            ?: return null
        return installedAppIndex.findBestApp(query)
    }

    private fun applicationLabel(packageName: String): String {
        return runCatching {
            val info: ApplicationInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

    private fun systemSettingTarget(step: CloudAgentStep): SystemSettingTarget {
        val key = listOfNotNull(
            step.argString("page", "kind", "target", "setting"),
            step.targetText,
            step.text,
        ).joinToString(" ").lowercase()
        return systemSettingTargets.firstOrNull { target ->
            target.keys.any { key.contains(it) }
        } ?: systemSettingTargets.first()
    }

    private fun appSettingsKind(step: CloudAgentStep): AppSettingsKind {
        val key = listOfNotNull(step.argString("page", "kind", "target"), step.targetText, step.text).joinToString(" ").lowercase()
        return when {
            listOf("通知", "notification").any { key.contains(it) } -> AppSettingsKind.Notification
            listOf("权限", "permission").any { key.contains(it) } -> AppSettingsKind.Permission
            listOf("电池", "后台", "battery", "background").any { key.contains(it) } -> AppSettingsKind.Battery
            else -> AppSettingsKind.Details
        }
    }

    private fun screenTimeoutMs(step: CloudAgentStep): Int? {
        step.argLong("timeoutMs", "screenTimeoutMs")?.let { return it.coerceIn(5_000L, 30L * 60L * 1000L).toInt() }
        step.argLong("seconds", "second", "sec")?.let { return (it * 1000L).coerceIn(5_000L, 30L * 60L * 1000L).toInt() }
        step.argLong("minutes", "minute", "min")?.let { return (it * 60_000L).coerceIn(5_000L, 30L * 60L * 1000L).toInt() }
        val text = listOfNotNull(step.text, step.targetText, step.reason).joinToString(" ")
        val number = firstNumber(text) ?: return null
        val normalized = text.lowercase()
        val seconds = when {
            normalized.contains("分钟") || normalized.contains("min") -> number * 60
            normalized.contains("秒") || normalized.contains("second") || normalized.contains("sec") -> number
            else -> number * 60
        }.coerceIn(5, 30 * 60)
        return seconds * 1000
    }

    private fun launchActivity(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun canWriteSystemSettings(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(appContext)
    }

    private fun openWriteSettingsPermission() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(ACTION_MANAGE_WRITE_SETTINGS_COMPAT, Uri.parse("package:${appContext.packageName}"))
        } else {
            Intent(ACTION_SETTINGS_COMPAT)
        }
        launchActivity(intent)
    }

    private fun memoryStatus(): String {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "未知"
        val info = ActivityManager.MemoryInfo()
        return runCatching {
            manager.getMemoryInfo(info)
            val avail = Formatter.formatFileSize(appContext, info.availMem)
            val total = Formatter.formatFileSize(appContext, info.totalMem)
            val low = if (info.lowMemory) "，系统提示内存紧张" else ""
            "可用 $avail / 总计 $total$low"
        }.getOrDefault("未知")
    }

    private fun storageStatus(): String {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val available = stat.availableBlocksLong * blockSize
            val usedPercent = if (total > 0) ((total - available) * 100 / total) else 0L
            "可用 ${Formatter.formatFileSize(appContext, available)} / 总计 ${Formatter.formatFileSize(appContext, total)}，已用约 $usedPercent%"
        }.getOrDefault("未知")
    }

    private fun batteryStatus(): String {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return "未知"
        val level = runCatching { manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
        return if (level in 0..100) "$level%" else "未知"
    }

    private fun networkStatus(): String {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "未知"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return "未连接"
            val caps = manager.getNetworkCapabilities(network) ?: return "未连接"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi 已连接"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据已连接"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网已连接"
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "网络已连接"
                else -> "未连接"
            }
        } else {
            @Suppress("DEPRECATION")
            val info = manager.activeNetworkInfo
            if (info?.isConnected == true) info.typeName ?: "网络已连接" else "未连接"
        }
    }

    private fun firstNumber(value: String): Int? {
        return Regex("\\d{1,4}").findAll(value).mapNotNull { it.value.toIntOrNull() }.firstOrNull()
    }

    private fun firstDecimal(value: String): Float? {
        return Regex("\\d+(?:\\.\\d+)?").findAll(value).mapNotNull { it.value.toFloatOrNull() }.firstOrNull()
    }

    private fun formatTimeout(timeoutMs: Int): String {
        val seconds = timeoutMs / 1000
        return if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} 分钟" else "$seconds 秒"
    }

    private fun formatShellScale(scale: Float): String {
        val rounded = (scale * 10f).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun isSafePackageName(packageName: String): Boolean {
        return packageName.matches(Regex("""[A-Za-z0-9_.]+""")) && packageName.contains('.')
    }

    private enum class AppSettingsKind { Notification, Permission, Battery, Details }

    private enum class PrivilegedTool(
        val title: String,
        val timeoutMs: Long,
    ) {
        ForceStop("强停应用", 1_500L) {
            override fun command(packageName: String): String = "am force-stop $packageName"
        },
        ClearData("清除应用数据", 6_000L) {
            override fun command(packageName: String): String = "pm clear $packageName"
        },
        UninstallForUser("卸载当前用户应用", 6_000L) {
            override fun command(packageName: String): String = "pm uninstall --user 0 $packageName"
        },
        Disable("禁用应用", 3_000L) {
            override fun command(packageName: String): String = "pm disable-user --user 0 $packageName"
        },
        Enable("启用应用", 3_000L) {
            override fun command(packageName: String): String = "pm enable $packageName"
        };

        abstract fun command(packageName: String): String
    }

    private data class SystemSettingTarget(
        val title: String,
        val action: String,
        val keys: List<String>,
    )

    private companion object {
        private val executableDeviceToolTypes = CloudAgentStep.deviceToolTypes + setOf("open_app")

        private const val ACTION_SETTINGS_COMPAT = "android.settings.SETTINGS"
        private const val ACTION_WIFI_SETTINGS_COMPAT = "android.settings.WIFI_SETTINGS"
        private const val ACTION_BLUETOOTH_SETTINGS_COMPAT = "android.settings.BLUETOOTH_SETTINGS"
        private const val ACTION_NOTIFICATION_SETTINGS_COMPAT = "android.settings.NOTIFICATION_SETTINGS"
        private const val ACTION_APP_NOTIFICATION_SETTINGS_COMPAT = "android.settings.APP_NOTIFICATION_SETTINGS"
        private const val ACTION_BATTERY_SETTINGS_COMPAT = "android.settings.BATTERY_SETTINGS"
        private const val ACTION_INTERNAL_STORAGE_SETTINGS_COMPAT = "android.settings.INTERNAL_STORAGE_SETTINGS"
        private const val ACTION_APPLICATION_SETTINGS_COMPAT = "android.settings.APPLICATION_SETTINGS"
        private const val ACTION_APPLICATION_DETAILS_SETTINGS_COMPAT = "android.settings.APPLICATION_DETAILS_SETTINGS"
        private const val ACTION_ACCESSIBILITY_SETTINGS_COMPAT = "android.settings.ACCESSIBILITY_SETTINGS"
        private const val ACTION_DISPLAY_SETTINGS_COMPAT = "android.settings.DISPLAY_SETTINGS"
        private const val ACTION_SOUND_SETTINGS_COMPAT = "android.settings.SOUND_SETTINGS"
        private const val ACTION_LOCATION_SOURCE_SETTINGS_COMPAT = "android.settings.LOCATION_SOURCE_SETTINGS"
        private const val ACTION_DATA_USAGE_SETTINGS_COMPAT = "android.settings.DATA_USAGE_SETTINGS"
        private const val ACTION_APPLICATION_DEVELOPMENT_SETTINGS_COMPAT = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
        private const val ACTION_MANAGE_WRITE_SETTINGS_COMPAT = "android.settings.action.MANAGE_WRITE_SETTINGS"
        private const val EXTRA_APP_PACKAGE_COMPAT = "android.provider.extra.APP_PACKAGE"

        private val systemSettingTargets = listOf(
            SystemSettingTarget("系统设置", ACTION_SETTINGS_COMPAT, listOf("settings", "system", "系统", "设置")),
            SystemSettingTarget("Wi‑Fi 设置", ACTION_WIFI_SETTINGS_COMPAT, listOf("wifi", "wi-fi", "无线", "无线网")),
            SystemSettingTarget("蓝牙设置", ACTION_BLUETOOTH_SETTINGS_COMPAT, listOf("bluetooth", "蓝牙")),
            SystemSettingTarget("系统通知设置", ACTION_NOTIFICATION_SETTINGS_COMPAT, listOf("notification", "通知")),
            SystemSettingTarget("电池设置", ACTION_BATTERY_SETTINGS_COMPAT, listOf("battery", "电池", "省电")),
            SystemSettingTarget("存储设置", ACTION_INTERNAL_STORAGE_SETTINGS_COMPAT, listOf("storage", "存储", "储存", "空间")),
            SystemSettingTarget("应用管理", ACTION_APPLICATION_SETTINGS_COMPAT, listOf("apps", "applications", "应用管理", "应用列表")),
            SystemSettingTarget("无障碍设置", ACTION_ACCESSIBILITY_SETTINGS_COMPAT, listOf("accessibility", "无障碍", "辅助功能")),
            SystemSettingTarget("显示设置", ACTION_DISPLAY_SETTINGS_COMPAT, listOf("display", "screen", "显示", "屏幕")),
            SystemSettingTarget("声音设置", ACTION_SOUND_SETTINGS_COMPAT, listOf("sound", "volume", "声音", "音量")),
            SystemSettingTarget("定位设置", ACTION_LOCATION_SOURCE_SETTINGS_COMPAT, listOf("location", "gps", "定位", "位置")),
            SystemSettingTarget("流量设置", ACTION_DATA_USAGE_SETTINGS_COMPAT, listOf("data", "traffic", "流量", "移动数据")),
            SystemSettingTarget("开发者选项", ACTION_APPLICATION_DEVELOPMENT_SETTINGS_COMPAT, listOf("developer", "开发者")),
        )
    }
}
