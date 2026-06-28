package com.yuchen.ailedger.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Executes only canonical device_tool commands selected by the cloud AgentBrain.
 *
 * This class deliberately contains no natural-language routing or argument inference. It never
 * reads step.text, step.targetText, step.reason or the user's goal to decide what to do. Android
 * validates the canonical JSON contract, executes the exact fixed operation, and verifies the
 * resulting device state whenever Android exposes a reliable readback.
 */
class DeviceToolExecutor(
    context: Context,
    private val installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val shellBridge = DeviceShellBridge(appContext)
    private val ledgerToolExecutor = LedgerInternalToolExecutor(appContext)

    fun canExecute(step: CloudAgentStep): Boolean {
        return step.type in executableDeviceToolTypes || ledgerToolExecutor.canExecute(step)
    }

    fun execute(step: CloudAgentStep, confirmedHighRisk: Boolean = false): AgentExecutionResult {
        if (ledgerToolExecutor.canExecute(step)) return ledgerToolExecutor.execute(step)

        val validation = DeviceControlSpecs.validate(step)
        if (!validation.ok) {
            return AgentExecutionResult(
                ok = false,
                message = "内部控制参数校验失败：${validation.reason}。请让云端 DeepSeek 重新返回规范 tool + args。",
                shouldContinue = false,
                diagnostics = "device_control_validation_failed:${validation.reason}",
            )
        }

        return runCatching {
            when (step.type) {
                "open_app" -> executeOpenApp(step)
                "open_system_settings" -> executeOpenSystemSettings(step)
                "open_app_settings" -> executeOpenAppSettings(step)
                "set_brightness" -> executeSetBrightness(step)
                "set_screen_timeout" -> executeSetScreenTimeout(step)
                "set_auto_rotate" -> executeSetAutoRotate(step)
                "set_media_volume" -> executeSetMediaVolume(step)
                "set_wifi_enabled" -> executeSetWifiEnabled(step)
                "set_bluetooth_enabled" -> executeSetBluetoothEnabled(step)
                "set_mobile_data_enabled" -> executeSetMobileDataEnabled(step)
                "set_dark_mode" -> executeSetDarkMode(step)
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
                diagnostics = "device_control_execution_exception:${error::class.java.simpleName}",
            )
        }
    }

    private fun executeOpenApp(step: CloudAgentStep): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "打开应用失败：云端必须返回真实已安装应用的 packageName。", false)
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AgentExecutionResult(false, "应用没有可启动入口：${app.label}（${app.packageName}）", false)
        val ok = launchActivity(launchIntent)
        return AgentExecutionResult(
            ok = ok,
            message = if (ok) "已打开 ${app.label}。" else "打开 ${app.label} 失败。",
            shouldContinue = ok,
        )
    }

    private fun executeOpenSystemSettings(step: CloudAgentStep): AgentExecutionResult {
        val page = canonicalString(step, "page")
        val target = systemSettingTargets[page]
            ?: return AgentExecutionResult(false, "打开系统设置失败：云端 page 不在规范枚举中。", false)
        val ok = launchActivity(Intent(target.action))
        return AgentExecutionResult(
            ok = ok,
            message = if (ok) "已打开${target.title}。" else "无法打开${target.title}，当前系统可能不支持该设置入口。",
            shouldContinue = false,
        )
    }

    private fun executeOpenAppSettings(step: CloudAgentStep): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "打开应用设置失败：云端缺少可验证的 packageName。", false)
        val kind = AppSettingsKind.fromWire(canonicalString(step, "page"))
            ?: return AgentExecutionResult(false, "打开应用设置失败：云端 page 不在规范枚举中。", false)
        val intent = when (kind) {
            AppSettingsKind.Notification -> Intent(ACTION_APP_NOTIFICATION_SETTINGS_COMPAT).apply {
                putExtra(EXTRA_APP_PACKAGE_COMPAT, app.packageName)
            }
            AppSettingsKind.Permission,
            AppSettingsKind.Battery,
            AppSettingsKind.Details -> Intent(
                ACTION_APPLICATION_DETAILS_SETTINGS_COMPAT,
                Uri.parse("package:${app.packageName}"),
            )
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
        return AgentExecutionResult(
            ok = ok,
            message = if (ok) "已打开$title。$detail".trim() else "无法打开$title。",
            shouldContinue = false,
        )
    }

    private fun executeSetBrightness(step: CloudAgentStep): AgentExecutionResult {
        val previousRaw = currentBrightnessRaw() ?: DEFAULT_BRIGHTNESS_RAW
        val previousPercent = previousRaw * 100f / 255f
        val targetPercent = canonicalPercentTarget(step, previousPercent)
            ?: return AgentExecutionResult(false, "调节亮度失败：云端必须提供 percent 或 deltaPercent。", false)
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return waitingPermissionResult(
                message = "需要先授权“修改系统设置”。我已打开授权页，授权后请让云端重新执行原命令。",
                diagnostic = "write_settings",
            )
        }

        val targetRaw = (targetPercent * 255f / 100f).roundToInt().coerceIn(0, 255)
        val wrote = runCatching {
            val modeOk = Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val valueOk = Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                targetRaw,
            )
            modeOk && valueOk
        }.getOrDefault(false)
        val actualRaw = currentBrightnessRaw()
        val verified = wrote && actualRaw != null && abs(actualRaw - targetRaw) <= BRIGHTNESS_RAW_TOLERANCE
        val actualPercent = actualRaw?.let { it * 100f / 255f }
        return AgentExecutionResult(
            ok = verified,
            message = if (verified) {
                "已把屏幕亮度从约 ${previousPercent.roundToInt()}% 调到约 ${actualPercent?.roundToInt() ?: targetPercent.roundToInt()}%，并完成状态核验。"
            } else {
                "亮度写入后状态核验未通过，系统实际值没有达到云端指定目标。"
            },
            shouldContinue = false,
            diagnostics = verificationDiagnostic("brightness", verified),
            undoStep = if (verified) undoStep(
                "set_brightness",
                "恢复执行前屏幕亮度",
                "percent" to previousPercent,
            ) else null,
        )
    }

    private fun executeSetScreenTimeout(step: CloudAgentStep): AgentExecutionResult {
        val timeoutMs = canonicalLong(step, "timeoutMs")
            ?.takeIf { it in 5_000L..1_800_000L }
            ?.toInt()
            ?: return AgentExecutionResult(false, "设置息屏时间失败：云端必须提供整数 timeoutMs。", false)
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return waitingPermissionResult(
                message = "需要先授权“修改系统设置”。我已打开授权页，授权后请让云端重新执行原命令。",
                diagnostic = "write_settings",
            )
        }
        val previousTimeoutMs = currentScreenTimeoutMs()
        val wrote = runCatching {
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
        }.getOrDefault(false)
        val verified = wrote && currentScreenTimeoutMs() == timeoutMs
        return AgentExecutionResult(
            ok = verified,
            message = if (verified) {
                "已把自动息屏时间设置为 ${formatTimeout(timeoutMs)}，并完成状态核验。"
            } else {
                "息屏时间写入后状态核验未通过。"
            },
            shouldContinue = false,
            diagnostics = verificationDiagnostic("screen_timeout", verified),
            undoStep = previousTimeoutMs?.takeIf { verified }?.let {
                undoStep("set_screen_timeout", "恢复执行前自动息屏时间", "timeoutMs" to it)
            },
        )
    }

    private fun executeSetAutoRotate(step: CloudAgentStep): AgentExecutionResult {
        val enabled = canonicalBoolean(step, "enabled")
            ?: return AgentExecutionResult(false, "设置自动旋转失败：云端必须提供 JSON Boolean enabled。", false)
        if (!canWriteSystemSettings()) {
            openWriteSettingsPermission()
            return waitingPermissionResult(
                message = "需要先授权“修改系统设置”。我已打开授权页，授权后请让云端重新执行原命令。",
                diagnostic = "write_settings",
            )
        }
        val previousEnabled = currentAutoRotateEnabled()
        val wrote = runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0,
            )
        }.getOrDefault(false)
        val verified = wrote && currentAutoRotateEnabled() == enabled
        return AgentExecutionResult(
            ok = verified,
            message = if (verified) {
                "已${if (enabled) "开启" else "关闭"}自动旋转，并完成状态核验。"
            } else {
                "自动旋转写入后状态核验未通过。"
            },
            shouldContinue = false,
            diagnostics = verificationDiagnostic("auto_rotate", verified),
            undoStep = previousEnabled?.takeIf { verified }?.let {
                undoStep("set_auto_rotate", "恢复执行前自动旋转状态", "enabled" to it)
            },
        )
    }

    private fun executeSetMediaVolume(step: CloudAgentStep): AgentExecutionResult {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AgentExecutionResult(false, "设置媒体音量失败：无法访问 AudioManager。", false)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
        val previousIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(min, max)
        val previousPercent = previousIndex * 100f / max
        val targetPercent = canonicalPercentTarget(step, previousPercent)
            ?: return AgentExecutionResult(false, "设置媒体音量失败：云端必须提供 percent 或 deltaPercent。", false)
        val targetIndex = (targetPercent * max / 100f).roundToInt().coerceIn(min, max)
        val wrote = runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            true
        }.getOrDefault(false)
        val actualIndex = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
        val verified = wrote && actualIndex == targetIndex
        val actualPercent = actualIndex?.let { it * 100f / max }
        return AgentExecutionResult(
            ok = verified,
            message = if (verified) {
                "已把媒体音量从约 ${previousPercent.roundToInt()}% 调到约 ${actualPercent?.roundToInt() ?: targetPercent.roundToInt()}%，并完成状态核验。"
            } else {
                "媒体音量写入后状态核验未通过。"
            },
            shouldContinue = false,
            diagnostics = verificationDiagnostic("media_volume", verified),
            undoStep = if (verified) undoStep(
                "set_media_volume",
                "恢复执行前媒体音量",
                "percent" to previousPercent,
            ) else null,
        )
    }

    private fun executeSetWifiEnabled(step: CloudAgentStep): AgentExecutionResult {
        val enabled = canonicalBoolean(step, "enabled")
            ?: return AgentExecutionResult(false, "设置 Wi‑Fi 失败：云端必须提供 JSON Boolean enabled。", false)
        return executeVerifiedEnhancedCommand(
            diagnosticName = "wifi",
            title = "设置 Wi‑Fi",
            command = "svc wifi ${if (enabled) "enable" else "disable"}; sleep 0.4",
            verifyCommand = "settings get global wifi_on",
            timeoutMs = 3_000L,
            successMessage = "已${if (enabled) "开启" else "关闭"} Wi‑Fi，并完成状态核验。",
            verifier = { output -> parseSettingBoolean(output) == enabled },
        )
    }

    private fun executeSetBluetoothEnabled(step: CloudAgentStep): AgentExecutionResult {
        val enabled = canonicalBoolean(step, "enabled")
            ?: return AgentExecutionResult(false, "设置蓝牙失败：云端必须提供 JSON Boolean enabled。", false)
        val verb = if (enabled) "enable" else "disable"
        return executeVerifiedEnhancedCommand(
            diagnosticName = "bluetooth",
            title = "设置蓝牙",
            command = "(cmd bluetooth_manager $verb || svc bluetooth $verb); sleep 0.4",
            verifyCommand = "settings get global bluetooth_on",
            timeoutMs = 3_000L,
            successMessage = "已${if (enabled) "开启" else "关闭"}蓝牙，并完成状态核验。",
            verifier = { output -> parseSettingBoolean(output) == enabled },
        )
    }

    private fun executeSetMobileDataEnabled(step: CloudAgentStep): AgentExecutionResult {
        val enabled = canonicalBoolean(step, "enabled")
            ?: return AgentExecutionResult(false, "设置移动数据失败：云端必须提供 JSON Boolean enabled。", false)
        return executeVerifiedEnhancedCommand(
            diagnosticName = "mobile_data",
            title = "设置移动数据",
            command = "svc data ${if (enabled) "enable" else "disable"}; sleep 0.4",
            verifyCommand = "settings get global mobile_data",
            timeoutMs = 3_000L,
            successMessage = "已${if (enabled) "开启" else "关闭"}移动数据，并完成状态核验。",
            verifier = { output -> parseSettingBoolean(output) == enabled },
        )
    }

    private fun executeSetDarkMode(step: CloudAgentStep): AgentExecutionResult {
        val mode = canonicalString(step, "mode")
            .takeIf { it in DARK_MODE_VALUES }
            ?: return AgentExecutionResult(false, "设置深色模式失败：云端 mode 必须是 yes、no 或 auto。", false)
        val titleText = when (mode) {
            "yes" -> "开启深色模式"
            "no" -> "关闭深色模式"
            else -> "设置深色模式为自动"
        }
        return executeVerifiedEnhancedCommand(
            diagnosticName = "dark_mode",
            title = "设置深色模式",
            command = "cmd uimode night $mode; sleep 0.3",
            verifyCommand = "cmd uimode night",
            timeoutMs = 2_500L,
            successMessage = "已$titleText，并完成状态核验。",
            verifier = { output -> verifyNightMode(output, mode) },
        )
    }

    private fun deviceStatus(): AgentExecutionResult {
        val memory = runCatching { memoryStatus() }.getOrDefault("未知")
        val storage = runCatching { storageStatus() }.getOrDefault("未知")
        val battery = runCatching { batteryStatus() }.getOrDefault("未知")
        val network = runCatching { networkStatus() }.getOrDefault("未知")
        val volume = runCatching { mediaVolumeStatus() }.getOrDefault("未知")
        val autoRotate = runCatching { autoRotateStatus() }.getOrDefault("未知")
        val appCount = runCatching {
            installedAppIndex.getLaunchableApps(forceReload = false).size
        }.getOrDefault(0)
        val shell = runCatching { shellBridge.probe() }.getOrNull()
        val shellText = shell?.let {
            buildString {
                append(if (it.available) "基础可用" else "不可用")
                append(" · ").append(if (it.isAdbShellLike) "增强级" else "App 沙箱级")
                append(" · Shizuku ").append(
                    if (it.shizukuGranted) "已授权" else if (it.shizukuAvailable) "待授权" else "未运行",
                )
            }
        } ?: "状态读取失败"
        val message = buildString {
            append("手机内部状态\n\n")
            append("电量：").append(battery).append('\n')
            append("内存：").append(memory).append('\n')
            append("存储：").append(storage).append('\n')
            append("网络：").append(network).append('\n')
            append("媒体音量：").append(volume).append('\n')
            append("自动旋转：").append(autoRotate).append('\n')
            append("可启动应用：").append(appCount).append(" 个\n")
            append("Shell：").append(shellText)
        }
        return AgentExecutionResult(true, message, shouldContinue = false)
    }

    private fun shellStatus(): AgentExecutionResult {
        return runCatching {
            AgentExecutionResult(true, shellBridge.enhancedModeGuide(), shouldContinue = false)
        }.getOrElse { error ->
            AgentExecutionResult(
                false,
                "读取 Shizuku/Shell 状态失败：${error.message ?: error::class.java.simpleName}",
                shouldContinue = false,
            )
        }
    }

    private fun requestShizukuPermission(): AgentExecutionResult {
        val result = shellBridge.requestShizukuPermission()
        val message = buildString {
            append(result.output.ifBlank { result.title })
            if (result.error.isNotBlank()) append("\n\n错误：").append(result.error)
        }
        return AgentExecutionResult(
            ok = result.ok,
            message = message,
            shouldContinue = false,
            diagnostics = if (result.ok) {
                "internal_control_verified:shizuku_permission"
            } else {
                "internal_control_permission_request_pending_or_failed:shizuku"
            },
        )
    }

    private fun executeAnimationScale(
        step: CloudAgentStep,
        confirmedHighRisk: Boolean,
    ): AgentExecutionResult {
        val scale = canonicalFloat(step, "scale")
            ?: return AgentExecutionResult(false, "设置动画缩放失败：云端必须提供 scale。", false)
        val scaleText = formatShellScale(scale)
        val command = listOf(
            "settings put global window_animation_scale $scaleText",
            "settings put global transition_animation_scale $scaleText",
            "settings put global animator_duration_scale $scaleText",
            "sleep 0.2",
        ).joinToString(" && ")
        return executeVerifiedEnhancedCommandIfConfirmed(
            diagnosticName = "animation_scale",
            title = "设置动画缩放",
            command = command,
            verifyCommand = listOf(
                "settings get global window_animation_scale",
                "settings get global transition_animation_scale",
                "settings get global animator_duration_scale",
            ).joinToString("; "),
            timeoutMs = 2_500L,
            confirmedHighRisk = confirmedHighRisk,
            pendingMessage = "设置动画缩放属于 global settings 写入，需要确认后执行：$scaleText。",
            successMessage = "已把窗口/过渡/动画程序时长缩放设置为 $scaleText，并完成状态核验。",
            verifier = { output -> verifyAnimationScales(output, scale) },
        )
    }

    private fun executePrivilegedAppTool(
        step: CloudAgentStep,
        tool: PrivilegedTool,
        confirmedHighRisk: Boolean,
    ): AgentExecutionResult {
        val app = resolveApp(step)
            ?: return AgentExecutionResult(false, "${tool.title}失败：云端缺少可验证的 packageName。", false)
        val packageName = app.packageName.takeIf(::isSafePackageName)
            ?: return AgentExecutionResult(false, "${tool.title}失败：目标包名格式异常。", false)
        if (!confirmedHighRisk) {
            return AgentExecutionResult(
                ok = false,
                message = "${tool.title} ${app.label}（$packageName）属于高风险内部控制，需要确认后执行。",
                shouldContinue = false,
                diagnostics = "internal_control_waiting_confirmation:${step.type}",
            )
        }

        val undo = tool.undoStep(packageName)
        return if (tool == PrivilegedTool.ClearData) {
            executeEnhancedCommand(
                diagnosticName = step.type,
                title = tool.title,
                command = tool.command(packageName),
                timeoutMs = tool.timeoutMs,
                successMessage = "已执行并核验：${tool.title} ${app.label}。",
                undoStep = undo,
                outputVerifier = { output -> output.contains("Success", ignoreCase = true) },
            )
        } else {
            executeVerifiedEnhancedCommand(
                diagnosticName = step.type,
                title = tool.title,
                command = tool.command(packageName),
                verifyCommand = tool.verifyCommand(packageName),
                timeoutMs = tool.timeoutMs,
                successMessage = "已执行并核验：${tool.title} ${app.label}。",
                undoStep = undo,
                verifier = { output -> tool.verifyOutput(output, packageName) },
            )
        }
    }

    private fun executeVerifiedEnhancedCommandIfConfirmed(
        diagnosticName: String,
        title: String,
        command: String,
        verifyCommand: String,
        timeoutMs: Long,
        confirmedHighRisk: Boolean,
        pendingMessage: String,
        successMessage: String,
        verifier: (String) -> Boolean,
        undoStep: CloudAgentStep? = null,
    ): AgentExecutionResult {
        if (!confirmedHighRisk) {
            return AgentExecutionResult(
                ok = false,
                message = pendingMessage,
                shouldContinue = false,
                diagnostics = "internal_control_waiting_confirmation",
            )
        }
        return executeVerifiedEnhancedCommand(
            diagnosticName = diagnosticName,
            title = title,
            command = command,
            verifyCommand = verifyCommand,
            timeoutMs = timeoutMs,
            successMessage = successMessage,
            verifier = verifier,
            undoStep = undoStep,
        )
    }

    private fun executeVerifiedEnhancedCommand(
        diagnosticName: String,
        title: String,
        command: String,
        verifyCommand: String,
        timeoutMs: Long,
        successMessage: String,
        verifier: (String) -> Boolean,
        undoStep: CloudAgentStep? = null,
    ): AgentExecutionResult {
        val result = shellBridge.runVerifiedEnhancedCommand(
            title = title,
            command = command,
            verifyCommand = verifyCommand,
            timeoutMs = timeoutMs,
            verifyTimeoutMs = 1_200L,
            verifier = verifier,
        )
        return AgentExecutionResult(
            ok = result.ok,
            message = buildShellResultMessage(
                ok = result.ok,
                title = title,
                successMessage = successMessage,
                output = result.output,
                exitCode = result.exitCode,
                error = result.error,
            ),
            shouldContinue = false,
            diagnostics = verificationDiagnostic(diagnosticName, result.ok),
            undoStep = undoStep.takeIf { result.ok },
        )
    }

    private fun executeEnhancedCommand(
        diagnosticName: String,
        title: String,
        command: String,
        timeoutMs: Long,
        successMessage: String,
        undoStep: CloudAgentStep? = null,
        outputVerifier: ((String) -> Boolean)? = null,
    ): AgentExecutionResult {
        val result = shellBridge.runEnhancedCommand(title = title, command = command, timeoutMs = timeoutMs)
        val verified = result.ok && (outputVerifier?.invoke(result.output) ?: true)
        val error = when {
            result.error.isNotBlank() -> result.error
            result.ok && !verified -> "命令返回成功，但执行结果内容未通过核验。"
            else -> ""
        }
        return AgentExecutionResult(
            ok = verified,
            message = buildShellResultMessage(
                ok = verified,
                title = title,
                successMessage = successMessage,
                output = result.output,
                exitCode = result.exitCode,
                error = error,
            ),
            shouldContinue = false,
            diagnostics = verificationDiagnostic(diagnosticName, verified),
            undoStep = undoStep.takeIf { verified },
        )
    }

    private fun buildShellResultMessage(
        ok: Boolean,
        title: String,
        successMessage: String,
        output: String,
        exitCode: Int?,
        error: String,
    ): String = buildString {
        append(if (ok) successMessage else "$title 执行或状态核验失败。")
        exitCode?.let { append("\nexit=").append(it) }
        if (output.isNotBlank() && output != "无输出") append("\n\n输出：").append(output)
        if (error.isNotBlank()) append("\n\n错误：").append(error)
    }

    private fun resolveApp(step: CloudAgentStep): InstalledAppEntry? {
        val topLevelPackage = step.packageName?.trim().orEmpty()
        val argsPackage = canonicalString(step, "packageName")
        if (topLevelPackage.isNotBlank() && argsPackage.isNotBlank() && topLevelPackage != argsPackage) {
            return null
        }
        val packageName = topLevelPackage.ifBlank { argsPackage }
            .takeIf(::isSafePackageName)
            ?: return null

        val launchableApp = installedAppIndex.getLaunchableApps(forceReload = false)
            .firstOrNull { app -> app.packageName == packageName }
        if (step.type == "open_app") return launchableApp

        val label = launchableApp?.label
            ?: applicationLabel(packageName).takeIf(String::isNotBlank)
            ?: return null
        return InstalledAppEntry(label = label, packageName = packageName)
    }

    private fun applicationLabel(packageName: String): String {
        return runCatching {
            val info: ApplicationInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

    private fun canonicalString(step: CloudAgentStep, name: String): String {
        val value = step.toolArgs?.opt(name) as? String ?: return ""
        return value.trim()
    }

    private fun canonicalFloat(step: CloudAgentStep, name: String): Float? {
        val value = step.toolArgs?.opt(name) as? Number ?: return null
        return value.toFloat()
    }

    private fun canonicalLong(step: CloudAgentStep, name: String): Long? {
        val value = step.toolArgs?.opt(name) as? Number ?: return null
        val doubleValue = value.toDouble()
        if (!doubleValue.isFinite() || doubleValue % 1.0 != 0.0) return null
        return doubleValue.toLong()
    }

    private fun canonicalBoolean(step: CloudAgentStep, name: String): Boolean? {
        return step.toolArgs?.opt(name) as? Boolean
    }

    private fun canonicalPercentTarget(step: CloudAgentStep, currentPercent: Float): Float? {
        val absolute = canonicalFloat(step, "percent")
        val delta = canonicalFloat(step, "deltaPercent")
        return when {
            absolute != null && delta == null -> absolute.coerceIn(0f, 100f)
            absolute == null && delta != null -> (currentPercent + delta).coerceIn(0f, 100f)
            else -> null
        }
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

    private fun waitingPermissionResult(message: String, diagnostic: String): AgentExecutionResult {
        return AgentExecutionResult(
            ok = false,
            message = message,
            shouldContinue = false,
            diagnostics = "internal_control_waiting_permission:$diagnostic",
        )
    }

    private fun currentBrightnessRaw(): Int? {
        return runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()?.coerceIn(0, 255)
    }

    private fun currentScreenTimeoutMs(): Int? {
        return runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull()
    }

    private fun currentAutoRotateEnabled(): Boolean? {
        return runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
        }.getOrNull()
    }

    private fun parseSettingBoolean(output: String): Boolean? {
        val value = output.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && it != "无输出" }
            .lastOrNull()
            ?: return null
        return when (value) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun verifyNightMode(output: String, expected: String): Boolean {
        return output.lowercase().lineSequence().any { line ->
            val clean = line.trim().replace('_', ' ')
            clean == expected || clean.endsWith(": $expected") || clean.endsWith("=$expected")
        }
    }

    private fun verifyAnimationScales(output: String, expected: Float): Boolean {
        val values = output.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && it != "无输出" }
            .mapNotNull(String::toFloatOrNull)
            .toList()
        return values.size >= 3 && values.takeLast(3).all { abs(it - expected) <= 0.001f }
    }

    private fun verificationDiagnostic(name: String, verified: Boolean): String {
        return if (verified) {
            "internal_control_verified:$name"
        } else {
            "internal_control_verification_failed:$name"
        }
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
        val level = runCatching {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
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

    private fun mediaVolumeStatus(): String {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "未知"
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return "约 ${(current * 100f / max).roundToInt()}%"
    }

    private fun autoRotateStatus(): String {
        val enabled = currentAutoRotateEnabled() ?: false
        return if (enabled) "已开启" else "已关闭"
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
        return DeviceControlSpecs.isSafePackageName(packageName)
    }

    private fun undoStep(type: String, reason: String, vararg args: Pair<String, Any>): CloudAgentStep {
        return CloudAgentStep(
            type = type,
            reason = reason,
            requiresConfirmation = DeviceControlSpecs.specFor(type)?.requiresConfirmation == true,
            toolArgs = JSONObject().apply { args.forEach { (key, value) -> put(key, value) } },
        )
    }

    private enum class AppSettingsKind(val wireValue: String) {
        Notification("notification"),
        Permission("permission"),
        Battery("battery"),
        Details("details");

        companion object {
            fun fromWire(value: String): AppSettingsKind? {
                return values().firstOrNull { it.wireValue == value }
            }
        }
    }

    private enum class PrivilegedTool(
        val title: String,
        val timeoutMs: Long,
    ) {
        ForceStop("强停应用", 1_500L) {
            override fun command(packageName: String): String = "am force-stop $packageName; sleep 0.2"
            override fun verifyCommand(packageName: String): String = "pidof $packageName || true"
            override fun verifyOutput(output: String, packageName: String): Boolean {
                return output.isBlank() || output.trim() == "无输出"
            }
        },
        ClearData("清除应用数据", 6_000L) {
            override fun command(packageName: String): String = "pm clear $packageName"
        },
        UninstallForUser("卸载当前用户应用", 6_000L) {
            override fun command(packageName: String): String = "pm uninstall --user 0 $packageName"
            override fun verifyCommand(packageName: String): String {
                return "if pm path $packageName 2>/dev/null | grep -q '^package:'; then echo present; else echo absent; fi"
            }
            override fun verifyOutput(output: String, packageName: String): Boolean {
                return output.lineSequence().any { it.trim() == "absent" }
            }
        },
        Disable("禁用应用", 3_000L) {
            override fun command(packageName: String): String = "pm disable-user --user 0 $packageName"
            override fun verifyCommand(packageName: String): String {
                return "pm list packages -d $packageName | grep -Fx 'package:$packageName' >/dev/null && echo disabled || echo enabled"
            }
            override fun verifyOutput(output: String, packageName: String): Boolean {
                return output.lineSequence().any { it.trim() == "disabled" }
            }
        },
        Enable("启用应用", 3_000L) {
            override fun command(packageName: String): String = "pm enable $packageName"
            override fun verifyCommand(packageName: String): String {
                return "pm list packages -e $packageName | grep -Fx 'package:$packageName' >/dev/null && echo enabled || echo disabled"
            }
            override fun verifyOutput(output: String, packageName: String): Boolean {
                return output.lineSequence().any { it.trim() == "enabled" }
            }
        };

        abstract fun command(packageName: String): String
        open fun verifyCommand(packageName: String): String = ""
        open fun verifyOutput(output: String, packageName: String): Boolean = false

        fun undoStep(packageName: String): CloudAgentStep? {
            val undoType = when (this) {
                Disable -> "enable_app"
                Enable -> "disable_app"
                else -> return null
            }
            return CloudAgentStep(
                type = undoType,
                packageName = packageName,
                reason = "撤销${title}",
                requiresConfirmation = true,
                toolArgs = JSONObject().put("packageName", packageName),
            )
        }
    }

    private data class SystemSettingTarget(
        val title: String,
        val action: String,
    )

    private companion object {
        private const val DEFAULT_BRIGHTNESS_RAW = 128
        private const val BRIGHTNESS_RAW_TOLERANCE = 1
        private val executableDeviceToolTypes = CloudAgentStep.systemDeviceToolTypes
        private val DARK_MODE_VALUES = setOf("yes", "no", "auto")

        private const val ACTION_SETTINGS_COMPAT = "android.settings.SETTINGS"
        private const val ACTION_WIFI_SETTINGS_COMPAT = "android.settings.WIFI_SETTINGS"
        private const val ACTION_BLUETOOTH_SETTINGS_COMPAT = "android.settings.BLUETOOTH_SETTINGS"
        private const val ACTION_NOTIFICATION_SETTINGS_COMPAT = "android.settings.NOTIFICATION_SETTINGS"
        private const val ACTION_ZEN_MODE_SETTINGS_COMPAT = "android.settings.ZEN_MODE_SETTINGS"
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

        private val systemSettingTargets = mapOf(
            "system" to SystemSettingTarget("系统设置", ACTION_SETTINGS_COMPAT),
            "wifi" to SystemSettingTarget("Wi‑Fi 设置", ACTION_WIFI_SETTINGS_COMPAT),
            "bluetooth" to SystemSettingTarget("蓝牙设置", ACTION_BLUETOOTH_SETTINGS_COMPAT),
            "battery" to SystemSettingTarget("电池设置", ACTION_BATTERY_SETTINGS_COMPAT),
            "display" to SystemSettingTarget("显示设置", ACTION_DISPLAY_SETTINGS_COMPAT),
            "notification" to SystemSettingTarget("系统通知设置", ACTION_NOTIFICATION_SETTINGS_COMPAT),
            "accessibility" to SystemSettingTarget("无障碍设置", ACTION_ACCESSIBILITY_SETTINGS_COMPAT),
            "apps" to SystemSettingTarget("应用管理", ACTION_APPLICATION_SETTINGS_COMPAT),
            "storage" to SystemSettingTarget("存储设置", ACTION_INTERNAL_STORAGE_SETTINGS_COMPAT),
            "sound" to SystemSettingTarget("声音设置", ACTION_SOUND_SETTINGS_COMPAT),
            "location" to SystemSettingTarget("定位设置", ACTION_LOCATION_SOURCE_SETTINGS_COMPAT),
            "data" to SystemSettingTarget("流量设置", ACTION_DATA_USAGE_SETTINGS_COMPAT),
            "developer" to SystemSettingTarget("开发者选项", ACTION_APPLICATION_DEVELOPMENT_SETTINGS_COMPAT),
            "dnd" to SystemSettingTarget("勿扰模式设置", ACTION_ZEN_MODE_SETTINGS_COMPAT),
        )
    }
}
