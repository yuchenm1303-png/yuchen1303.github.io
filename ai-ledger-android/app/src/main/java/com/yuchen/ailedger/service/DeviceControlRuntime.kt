package com.yuchen.ailedger.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Local, non-visual device control runtime.
 *
 * This is the Android-side equivalent of a small Codex tool layer: it handles stable
 * system/app operations directly through Android APIs and Intents, before falling
 * back to the visual Computer Use loop.
 */
data class DeviceControlResult(
    val ok: Boolean,
    val title: String,
    val message: String,
)

class DeviceControlRuntime(
    private val context: Context,
    private val installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
) {
    private val appContext: Context = context.applicationContext

    fun tryExecute(rawGoal: String): DeviceControlResult? {
        val goal = rawGoal.trim()
        if (goal.isBlank()) return null
        val normalized = normalize(goal)

        return trySetBrightness(goal, normalized)
            ?: trySetScreenTimeout(goal, normalized)
            ?: tryOpenAppScopedSettings(goal, normalized)
            ?: tryOpenSystemSettings(goal, normalized)
            ?: tryBuildDeviceHealthReport(goal, normalized)
            ?: tryOpenPlainApp(goal, normalized)
    }

    private fun tryBuildDeviceHealthReport(goal: String, normalized: String): DeviceControlResult? {
        val signal = hasAny(normalized, listOf("手机体检", "设备体检", "手机状态", "设备状态", "健康报告", "为什么卡", "卡顿", "清内存", "清理后台", "释放内存", "存储空间", "内存情况"))
        if (!signal) return null

        val memory = memoryStatus()
        val storage = storageStatus()
        val battery = batteryStatus()
        val network = networkStatus()
        val appCount = runCatching { installedAppIndex.getLaunchableApps(forceReload = false).size }.getOrDefault(0)
        val cleanupHint = if (hasAny(normalized, listOf("清内存", "清理后台", "释放内存"))) {
            "\n\n清理说明：普通 Android App 不能直接强制清理其他 App 进程；我已完成状态检查。后续接入 Shizuku/ADB 增强模式后，可以把强停、dumpsys、appops 做成真正的内部工具。"
        } else {
            ""
        }

        return DeviceControlResult(
            ok = true,
            title = "手机体检",
            message = buildString {
                append("手机内部控制体检完成\n\n")
                append("电量：").append(battery).append('\n')
                append("内存：").append(memory).append('\n')
                append("存储：").append(storage).append('\n')
                append("网络：").append(network).append('\n')
                append("可启动应用：").append(appCount).append(" 个")
                append(cleanupHint)
            },
        )
    }

    private fun tryOpenSystemSettings(goal: String, normalized: String): DeviceControlResult? {
        val target = systemSettingTargets.firstOrNull { target -> target.keywords.any { normalized.contains(normalize(it)) } }
            ?: return null
        val ok = launchActivity(Intent(target.action))
        return DeviceControlResult(
            ok = ok,
            title = target.title,
            message = if (ok) "已打开${target.title}。" else "无法打开${target.title}，当前系统可能不支持该设置入口。",
        )
    }

    private fun tryOpenAppScopedSettings(goal: String, normalized: String): DeviceControlResult? {
        val kind = when {
            hasAny(normalized, listOf("通知设置", "通知权限", "应用通知", "app通知")) -> AppSettingsKind.Notification
            hasAny(normalized, listOf("权限设置", "权限管理", "应用权限", "app权限")) -> AppSettingsKind.Permission
            hasAny(normalized, listOf("电池设置", "耗电设置", "后台限制", "电池优化")) -> AppSettingsKind.Battery
            hasAny(normalized, listOf("应用信息", "应用详情", "app信息", "app详情", "应用设置", "app设置")) -> AppSettingsKind.Details
            else -> null
        } ?: return null

        val query = extractAppQuery(
            goal,
            listOf("通知设置", "通知权限", "应用通知", "app通知", "权限设置", "权限管理", "应用权限", "app权限", "电池设置", "耗电设置", "后台限制", "电池优化", "应用信息", "应用详情", "app信息", "app详情", "应用设置", "app设置"),
        )
        if (query.isBlank()) return null
        val app = installedAppIndex.findBestApp(query)
            ?: return DeviceControlResult(false, "应用设置", "没有找到“$query”对应的已安装应用。")

        val intent = when (kind) {
            AppSettingsKind.Notification -> Intent(ACTION_APP_NOTIFICATION_SETTINGS_COMPAT).apply {
                putExtra(EXTRA_APP_PACKAGE_COMPAT, app.packageName)
            }
            AppSettingsKind.Permission,
            AppSettingsKind.Battery,
            AppSettingsKind.Details -> Intent(ACTION_APPLICATION_DETAILS_SETTINGS_COMPAT, Uri.parse("package:${app.packageName}"))
        }
        val ok = launchActivity(intent)
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
        return DeviceControlResult(
            ok = ok,
            title = title,
            message = if (ok) "已打开$title。${detail}".trim() else "无法打开$title。",
        )
    }

    private fun trySetBrightness(goal: String, normalized: String): DeviceControlResult? {
        if (!hasAny(normalized, listOf("亮度", "屏幕亮度"))) return null
        val percent = firstNumber(goal)?.coerceIn(0, 100) ?: return DeviceControlResult(false, "调节亮度", "我识别到亮度控制，但没有找到 0–100 的亮度百分比。")
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return DeviceControlResult(
                ok = false,
                title = "调节亮度",
                message = "需要先授权“修改系统设置”。我已打开授权页，开启后再说一次“把亮度调到 $percent%”。",
            )
        }
        val value = (percent * 255f / 100f).roundToInt().coerceIn(0, 255)
        val resolver = appContext.contentResolver
        val ok = runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.getOrDefault(false)
        return DeviceControlResult(
            ok = ok,
            title = "调节亮度",
            message = if (ok) "已把屏幕亮度调到约 $percent%。" else "调节亮度失败，当前系统可能限制第三方应用修改该设置。",
        )
    }

    private fun trySetScreenTimeout(goal: String, normalized: String): DeviceControlResult? {
        if (!hasAny(normalized, listOf("锁屏时间", "息屏时间", "自动锁屏", "屏幕超时", "休眠时间"))) return null
        val timeoutMs = parseTimeoutMs(goal)
            ?: return DeviceControlResult(false, "设置锁屏时间", "我识别到锁屏时间控制，但没有找到明确时长，比如“30秒”或“5分钟”。")
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return DeviceControlResult(
                ok = false,
                title = "设置锁屏时间",
                message = "需要先授权“修改系统设置”。我已打开授权页，开启后再说一次要设置的锁屏时间。",
            )
        }
        val ok = runCatching {
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
        }.getOrDefault(false)
        return DeviceControlResult(
            ok = ok,
            title = "设置锁屏时间",
            message = if (ok) "已把自动锁屏时间设置为 ${formatTimeout(timeoutMs)}。" else "设置锁屏时间失败，当前系统可能限制第三方应用修改该设置。",
        )
    }

    private fun tryOpenPlainApp(goal: String, normalized: String): DeviceControlResult? {
        if (!hasAny(normalized, listOf("打开", "启动", "进入"))) return null
        if (hasAny(normalized, deepNavigationWords)) return null
        val query = extractAppQuery(goal, listOf("打开", "启动", "进入", "应用", "app"))
        if (query.isBlank()) return null
        val app = installedAppIndex.findBestApp(query) ?: return DeviceControlResult(false, "打开应用", "没有找到“$query”对应的可启动应用。")
        val ok = launchApp(app)
        return DeviceControlResult(
            ok = ok,
            title = "打开应用",
            message = if (ok) "已打开 ${app.label}。" else "找到 ${app.label}，但系统没有返回可启动入口。",
        )
    }

    private fun launchApp(app: InstalledAppEntry): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return launchActivity(intent)
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
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "未知"
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
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return "未知"
        val level = runCatching { manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
        return if (level in 0..100) "$level%" else "未知"
    }

    private fun networkStatus(): String {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "未知"
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

    private fun extractAppQuery(goal: String, markers: List<String>): String {
        var value = goal.trim()
        val removeWords = listOf("帮我", "请", "麻烦", "一下", "打开", "进入", "查看", "管理", "修改", "关闭", "设置", "应用", "app", "APP", "的", "到") + markers
        removeWords.forEach { word -> value = value.replace(word, "", ignoreCase = true) }
        return value.replace(Regex("""[，。,.、:：/_　\s]+"""), "").take(40)
    }

    private fun firstNumber(value: String): Int? {
        return Regex("\\d{1,3}").findAll(value).mapNotNull { it.value.toIntOrNull() }.firstOrNull()
    }

    private fun parseTimeoutMs(value: String): Int? {
        val number = firstNumber(value) ?: return null
        val normalized = normalize(value)
        val seconds = when {
            normalized.contains("分钟") || normalized.contains("min") -> number * 60
            normalized.contains("秒") || normalized.contains("second") || normalized.contains("sec") -> number
            else -> number * 60
        }.coerceIn(5, 30 * 60)
        return seconds * 1000
    }

    private fun formatTimeout(timeoutMs: Int): String {
        val seconds = timeoutMs / 1000
        return if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} 分钟" else "$seconds 秒"
    }

    private fun hasAny(normalizedText: String, words: List<String>): Boolean {
        return words.any { normalizedText.contains(normalize(it)) }
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), "")
            .replace("％", "%")
    }

    private enum class AppSettingsKind { Notification, Permission, Battery, Details }

    private data class SystemSettingTarget(
        val title: String,
        val action: String,
        val keywords: List<String>,
    )

    private companion object {
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

        private val deepNavigationWords = listOf(
            "页面", "界面", "联系人", "朋友圈", "聊天", "消息", "搜索", "找到", "详情", "热榜", "行情", "文件", "小程序", "设置页",
        )

        private val systemSettingTargets = listOf(
            SystemSettingTarget("Wi‑Fi 设置", ACTION_WIFI_SETTINGS_COMPAT, listOf("wifi设置", "wi-fi设置", "无线网设置", "无线网络设置")),
            SystemSettingTarget("蓝牙设置", ACTION_BLUETOOTH_SETTINGS_COMPAT, listOf("蓝牙设置", "bluetooth设置")),
            SystemSettingTarget("系统通知设置", ACTION_NOTIFICATION_SETTINGS_COMPAT, listOf("系统通知设置", "通知管理", "通知设置")),
            SystemSettingTarget("电池设置", ACTION_BATTERY_SETTINGS_COMPAT, listOf("电池设置", "电量设置", "省电设置", "省电模式")),
            SystemSettingTarget("存储设置", ACTION_INTERNAL_STORAGE_SETTINGS_COMPAT, listOf("存储设置", "空间设置", "储存设置")),
            SystemSettingTarget("应用管理", ACTION_APPLICATION_SETTINGS_COMPAT, listOf("应用管理", "应用列表", "app管理", "应用设置")),
            SystemSettingTarget("无障碍设置", ACTION_ACCESSIBILITY_SETTINGS_COMPAT, listOf("无障碍设置", "辅助功能设置")),
            SystemSettingTarget("显示设置", ACTION_DISPLAY_SETTINGS_COMPAT, listOf("显示设置", "屏幕设置")),
            SystemSettingTarget("声音设置", ACTION_SOUND_SETTINGS_COMPAT, listOf("声音设置", "音量设置")),
            SystemSettingTarget("定位设置", ACTION_LOCATION_SOURCE_SETTINGS_COMPAT, listOf("定位设置", "位置设置", "gps设置")),
            SystemSettingTarget("流量设置", ACTION_DATA_USAGE_SETTINGS_COMPAT, listOf("流量设置", "数据使用", "移动数据设置")),
            SystemSettingTarget("开发者选项", ACTION_APPLICATION_DEVELOPMENT_SETTINGS_COMPAT, listOf("开发者选项", "开发者设置")),
            SystemSettingTarget("修改系统设置授权", ACTION_MANAGE_WRITE_SETTINGS_COMPAT, listOf("修改系统设置", "写入系统设置", "系统设置授权")),
        )
    }
}
