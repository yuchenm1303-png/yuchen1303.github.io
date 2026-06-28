package com.yuchen.ailedger.service

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Performs lightweight post-action verification for structured internal-control tools.
 *
 * DeepSeek has already selected the tool and calculated its canonical arguments. This verifier
 * never reads text, targetText, reason, aliases or direction words to reconstruct intent. It only
 * reads canonical toolArgs, Android state and fixed read-only shell checks after execution.
 */
class DeviceControlActionVerifier(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val shellBridge = DeviceShellBridge(appContext)

    fun verify(step: CloudAgentStep, execution: AgentExecutionResult): AgentExecutionResult {
        if (!execution.ok) return execution
        val verification = when (step.type) {
            "set_brightness" -> verifyBrightness(step, execution)
            "set_screen_timeout" -> verifyScreenTimeout(step)
            "set_auto_rotate" -> verifyAutoRotate(step)
            "set_media_volume" -> verifyMediaVolume(step, execution)
            "set_wifi_enabled" -> verifyShellSwitch(step, "Wi‑Fi", "settings get global wifi_on")
            "set_bluetooth_enabled" -> verifyShellSwitch(step, "蓝牙", "settings get global bluetooth_on")
            "set_mobile_data_enabled" -> verifyShellSwitch(step, "移动数据", "settings get global mobile_data")
            "set_dark_mode" -> verifyDarkMode(step)
            "set_animation_scale" -> verifyAnimationScale(step)
            "force_stop_app" -> verifyForceStop(step)
            "clear_app_data" -> VerificationResult(
                verified = true,
                summary = "verified=command_exit_ok；清除应用数据不读取隐私目录，采用系统命令返回码作为验证。",
            )
            "uninstall_app" -> verifyPackageAbsent(step)
            "disable_app" -> verifyPackageDisabled(step)
            "enable_app" -> verifyPackageEnabled(step)
            else -> return execution
        }
        val message = buildString {
            append(execution.message)
            append("\n\n执行后验证：")
            append(verification.summary)
        }
        return execution.copy(
            ok = execution.ok && verification.verified,
            message = message,
        )
    }

    private fun verifyBrightness(step: CloudAgentStep, execution: AgentExecutionResult): VerificationResult {
        val targetPercent = expectedBrightnessPercent(step, execution)
            ?: return VerificationResult(false, "未拿到执行器确认的目标亮度，无法确认亮度是否生效。")
        val actualPercent = currentBrightnessPercent()
            ?: return VerificationResult(false, "读取当前亮度失败，无法确认亮度是否生效。")
        val ok = abs(actualPercent - targetPercent) <= BRIGHTNESS_TOLERANCE_PERCENT
        return VerificationResult(
            verified = ok,
            summary = if (ok) {
                "verified=true；目标约 ${targetPercent.roundToInt()}%，当前约 ${actualPercent.roundToInt()}%。"
            } else {
                "verified=false；目标约 ${targetPercent.roundToInt()}%，当前约 ${actualPercent.roundToInt()}%，可能被系统亮度策略覆盖。"
            },
        )
    }

    private fun verifyScreenTimeout(step: CloudAgentStep): VerificationResult {
        val targetMs = expectedScreenTimeoutMs(step)
            ?: return VerificationResult(false, "未拿到目标息屏时间，无法确认设置是否生效。")
        val actualMs = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull() ?: return VerificationResult(false, "读取当前息屏时间失败，无法确认设置是否生效。")
        val ok = abs(actualMs - targetMs) <= SCREEN_TIMEOUT_TOLERANCE_MS
        return VerificationResult(
            verified = ok,
            summary = if (ok) {
                "verified=true；目标 ${formatTimeout(targetMs)}，当前 ${formatTimeout(actualMs)}。"
            } else {
                "verified=false；目标 ${formatTimeout(targetMs)}，当前 ${formatTimeout(actualMs)}。"
            },
        )
    }

    private fun verifyAutoRotate(step: CloudAgentStep): VerificationResult {
        val expected = desiredEnabledState(step)
            ?: return VerificationResult(false, "缺少目标开关状态，无法确认自动旋转结果。")
        val actual = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
        }.getOrNull()?.let { it == 1 } ?: return VerificationResult(false, "读取自动旋转状态失败。")
        return VerificationResult(
            verified = actual == expected,
            summary = if (actual == expected) {
                "verified=true；自动旋转当前为${if (actual) "开启" else "关闭"}。"
            } else {
                "verified=false；目标为${if (expected) "开启" else "关闭"}，当前为${if (actual) "开启" else "关闭"}。"
            },
        )
    }

    private fun verifyMediaVolume(step: CloudAgentStep, execution: AgentExecutionResult): VerificationResult {
        val targetPercent = expectedMediaVolumePercent(step, execution)
            ?: return VerificationResult(false, "未拿到执行器确认的目标音量，无法确认媒体音量是否生效。")
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VerificationResult(false, "读取 AudioManager 失败。")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val actualPercent = (current * 100f / max).coerceIn(0f, 100f)
        val ok = abs(actualPercent - targetPercent) <= VOLUME_TOLERANCE_PERCENT
        return VerificationResult(
            verified = ok,
            summary = if (ok) {
                "verified=true；目标约 ${targetPercent.roundToInt()}%，当前约 ${actualPercent.roundToInt()}%。"
            } else {
                "verified=false；目标约 ${targetPercent.roundToInt()}%，当前约 ${actualPercent.roundToInt()}%。"
            },
        )
    }

    private fun verifyShellSwitch(step: CloudAgentStep, title: String, command: String): VerificationResult {
        val expected = desiredEnabledState(step)
            ?: return VerificationResult(false, "缺少目标开关状态，无法确认$title 结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(title = "确认$title 状态", command = command, timeoutMs = 900L)
        val output = result.output.trim().lowercase()
        val actual = when {
            output.lineSequence().any { it.trim() == "1" } || output.contains("enabled") -> true
            output.lineSequence().any { it.trim() == "0" } || output.contains("disabled") -> false
            else -> null
        } ?: return VerificationResult(false, "verified=false；无法解析$title 状态：${result.output.ifBlank { result.error.ifBlank { "无输出" } }}。")
        return VerificationResult(
            verified = actual == expected,
            summary = if (actual == expected) {
                "verified=true；$title 当前为${if (actual) "开启" else "关闭"}。"
            } else {
                "verified=false；目标为${if (expected) "开启" else "关闭"}，当前为${if (actual) "开启" else "关闭"}。"
            },
        )
    }

    private fun verifyDarkMode(step: CloudAgentStep): VerificationResult {
        val expected = darkModeExpected(step)
            ?: return VerificationResult(false, "缺少目标深色模式，无法确认结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "确认深色模式",
            command = "cmd uimode night",
            timeoutMs = 900L,
        )
        val output = result.output.trim().lowercase()
        val ok = when (expected) {
            "yes" -> output.contains("yes") || output.contains("night") || output == "2"
            "no" -> output.contains("no") || output.contains("not") || output == "1"
            "auto" -> output.contains("auto") || output == "0"
            else -> false
        }
        return VerificationResult(
            verified = ok,
            summary = if (ok) {
                "verified=true；深色模式读取结果：${result.output.ifBlank { "无输出" }}。"
            } else {
                "verified=false；深色模式读取结果：${result.output.ifBlank { result.error.ifBlank { "无输出" } }}。"
            },
        )
    }

    private fun verifyAnimationScale(step: CloudAgentStep): VerificationResult {
        val scale = step.argFloat("scale")
            ?: return VerificationResult(false, "缺少规范 scale 参数，无法确认动画缩放结果。")
        val target = formatShellScale(scale.coerceIn(0f, 10f))
        val diagnostic = shellBridge.runSafeDiagnostic("animation_scales")
            ?: return VerificationResult(false, "动画缩放诊断模板不可用。")
        val lines = diagnostic.output.lineSequence().map { it.trim() }.filter { it.isNotBlank() && it != "无输出" }.toList()
        val ok = diagnostic.ok && lines.size >= 3 && lines.take(3).all { sameScaleValue(it, target) }
        return VerificationResult(
            verified = ok,
            summary = if (ok) {
                "verified=true；窗口/过渡/动画缩放均为 $target。"
            } else {
                "verified=false；读取到 ${lines.take(3).joinToString("/").ifBlank { diagnostic.error.ifBlank { "无输出" } }}。"
            },
        )
    }

    private fun verifyForceStop(step: CloudAgentStep): VerificationResult {
        val packageName = safePackageName(step)
            ?: return VerificationResult(false, "缺少安全包名，无法确认强停结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "确认进程是否仍在运行",
            command = "pidof $packageName",
            timeoutMs = 900L,
        )
        val output = result.output.trim()
        val noProcess = output.isBlank() || output == "无输出" || result.exitCode != 0
        return VerificationResult(
            verified = noProcess,
            summary = if (noProcess) {
                "verified=true；pidof 未发现 $packageName 进程。"
            } else {
                "verified=false；pidof 输出：${output.ifBlank { result.error.ifBlank { "未知" } }}。"
            },
        )
    }

    private fun verifyPackageAbsent(step: CloudAgentStep): VerificationResult {
        val packageName = safePackageName(step)
            ?: return VerificationResult(false, "缺少安全包名，无法确认卸载结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "确认应用是否仍安装",
            command = "pm path $packageName",
            timeoutMs = 900L,
        )
        val absent = result.exitCode != 0 || result.output.isBlank() || result.output == "无输出"
        return VerificationResult(
            verified = absent,
            summary = if (absent) {
                "verified=true；当前用户已找不到 $packageName 的安装路径。"
            } else {
                "verified=false；pm path 校验输出：${result.output.ifBlank { result.error.ifBlank { "未知" } }}。"
            },
        )
    }

    private fun verifyPackageDisabled(step: CloudAgentStep): VerificationResult {
        val packageName = safePackageName(step)
            ?: return VerificationResult(false, "缺少安全包名，无法确认禁用结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "确认应用是否禁用",
            command = "pm list packages -d",
            timeoutMs = 900L,
        )
        val disabled = result.output.contains(packageName)
        return VerificationResult(
            verified = result.ok && disabled,
            summary = if (result.ok && disabled) {
                "verified=true；$packageName 已出现在禁用包列表。"
            } else {
                "verified=false；禁用包列表未确认 $packageName。"
            },
        )
    }

    private fun verifyPackageEnabled(step: CloudAgentStep): VerificationResult {
        val packageName = safePackageName(step)
            ?: return VerificationResult(false, "缺少安全包名，无法确认启用结果。")
        val result = shellBridge.runReadOnlyEnhancedCommand(
            title = "确认应用是否启用",
            command = "pm list packages -d",
            timeoutMs = 900L,
        )
        val enabled = result.ok && !result.output.contains(packageName)
        return VerificationResult(
            verified = enabled,
            summary = if (enabled) {
                "verified=true；$packageName 未出现在禁用包列表。"
            } else {
                "verified=false；禁用包列表仍包含 $packageName。"
            },
        )
    }

    private fun expectedBrightnessPercent(step: CloudAgentStep, execution: AgentExecutionResult): Float? {
        targetPercentFromExecutionMessage(execution.message)?.let { return it }
        if (step.argFloat("deltaPercent") != null) return null
        return step.argFloat("percent")?.coerceIn(0f, 100f)
    }

    private fun expectedMediaVolumePercent(step: CloudAgentStep, execution: AgentExecutionResult): Float? {
        targetPercentFromExecutionMessage(execution.message)?.let { return it }
        if (step.argFloat("deltaPercent") != null) return null
        return step.argFloat("percent")?.coerceIn(0f, 100f)
    }

    private fun currentBrightnessPercent(): Float? {
        val raw = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return null
        return (raw.coerceIn(0, 255) * 100f / 255f).coerceIn(0f, 100f)
    }

    private fun expectedScreenTimeoutMs(step: CloudAgentStep): Int? {
        return step.argLong("timeoutMs")
            ?.takeIf { it in 5_000L..30L * 60L * 1000L }
            ?.toInt()
    }

    private fun desiredEnabledState(step: CloudAgentStep): Boolean? {
        return step.toolArgs?.opt("enabled") as? Boolean
    }

    private fun darkModeExpected(step: CloudAgentStep): String? {
        return step.argString("mode")?.takeIf { it in setOf("yes", "no", "auto") }
    }

    private fun formatTimeout(timeoutMs: Int): String {
        val seconds = timeoutMs / 1000
        return if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} 分钟" else "$seconds 秒"
    }

    private fun formatShellScale(scale: Float): String {
        val rounded = (scale * 10f).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun sameScaleValue(actual: String, expected: String): Boolean {
        return actual == expected || actual.toFloatOrNull()?.let { formatShellScale(it) == expected } == true
    }

    private fun safePackageName(step: CloudAgentStep): String? {
        val packageName = step.packageName ?: step.argString("packageName") ?: return null
        return packageName.takeIf { DeviceControlSpecs.isSafePackageName(it) }
    }

    private data class VerificationResult(
        val verified: Boolean,
        val summary: String,
    )

    private companion object {
        private const val BRIGHTNESS_TOLERANCE_PERCENT = 4f
        private const val VOLUME_TOLERANCE_PERCENT = 6f
        private const val SCREEN_TIMEOUT_TOLERANCE_MS = 1_000
    }
}

internal fun targetPercentFromExecutionMessage(message: String): Float? {
    return Regex("""调到约\s*(\d+(?:\.\d+)?)%""")
        .find(message)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?.coerceIn(0f, 100f)
}
