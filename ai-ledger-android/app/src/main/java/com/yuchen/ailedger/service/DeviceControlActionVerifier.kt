package com.yuchen.ailedger.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Performs lightweight post-action verification for structured internal-control tools.
 *
 * The verifier never exposes free-form shell to the model. It only reads Android settings or runs
 * fixed read-only shell checks after DeviceToolExecutor has executed a concrete allowlisted tool.
 */
class DeviceControlActionVerifier(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val shellBridge = DeviceShellBridge(appContext)

    fun verify(step: CloudAgentStep, execution: AgentExecutionResult): AgentExecutionResult {
        if (!execution.ok) return execution
        val verification = when (step.type) {
            "set_brightness" -> verifyBrightness(step)
            "set_screen_timeout" -> verifyScreenTimeout(step)
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

    private fun verifyBrightness(step: CloudAgentStep): VerificationResult {
        val targetPercent = expectedBrightnessPercent(step)
            ?: return VerificationResult(false, "未拿到目标亮度参数，无法确认亮度是否生效。")
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

    private fun verifyAnimationScale(step: CloudAgentStep): VerificationResult {
        val target = formatShellScale(
            (step.argFloat("scale", "value") ?: firstDecimal(step.text ?: step.targetText ?: step.reason.orEmpty()) ?: 0.5f)
                .coerceIn(0f, 10f),
        )
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
            command = "pidof $packageName || true",
            timeoutMs = 900L,
        )
        val output = result.output.trim()
        val noProcess = output.isBlank() || output == "无输出"
        return VerificationResult(
            verified = result.ok && noProcess,
            summary = if (result.ok && noProcess) {
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
            command = "pm path $packageName >/dev/null 2>&1; echo $?",
            timeoutMs = 900L,
        )
        val absent = result.output.lineSequence().firstOrNull()?.trim() == "1"
        return VerificationResult(
            verified = result.ok && absent,
            summary = if (result.ok && absent) {
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
            command = "pm list packages -d | grep -F '$packageName' || true",
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
            command = "pm list packages -d | grep -F '$packageName' || true",
            timeoutMs = 900L,
        )
        val enabled = !result.output.contains(packageName)
        return VerificationResult(
            verified = result.ok && enabled,
            summary = if (result.ok && enabled) {
                "verified=true；$packageName 未出现在禁用包列表。"
            } else {
                "verified=false；禁用包列表仍包含 $packageName。"
            },
        )
    }

    private fun expectedBrightnessPercent(step: CloudAgentStep): Float? {
        val currentPercent = currentBrightnessPercent() ?: DEFAULT_BRIGHTNESS_PERCENT
        val absolutePercent = step.argFloat("percent", "brightness", "value")
        val deltaPercent = step.argFloat("deltaPercent", "delta", "brightnessDelta", "changePercent", "adjustBy")
        val operationPercent = brightnessOperationDelta(step)
        val textPercent = firstNumber(step.text ?: step.targetText ?: step.reason.orEmpty())?.toFloat()
        return when {
            absolutePercent != null -> absolutePercent
            deltaPercent != null -> currentPercent + deltaPercent
            operationPercent != null -> currentPercent + operationPercent
            textPercent != null -> textPercent
            else -> null
        }?.coerceIn(0f, 100f)
    }

    private fun currentBrightnessPercent(): Float? {
        val raw = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return null
        return (raw.coerceIn(0, 255) * 100f / 255f).coerceIn(0f, 100f)
    }

    private fun expectedScreenTimeoutMs(step: CloudAgentStep): Int? {
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

    private fun brightnessOperationDelta(step: CloudAgentStep): Float? {
        val operation = step.argString("operation", "mode", "adjustment", "relative", "direction")
            ?.lowercase()
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
            ?: return null
        return when (operation) {
            "decrease", "reduce", "lower", "down", "dim", "darker", "less", "调低", "降低", "变暗" -> -DEFAULT_BRIGHTNESS_DELTA
            "increase", "raise", "higher", "up", "brighten", "brighter", "more", "调高", "提高", "变亮" -> DEFAULT_BRIGHTNESS_DELTA
            else -> null
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

    private fun sameScaleValue(actual: String, expected: String): Boolean {
        return actual == expected || actual.toFloatOrNull()?.let { formatShellScale(it) == expected } == true
    }

    private fun safePackageName(step: CloudAgentStep): String? {
        val packageName = step.packageName ?: step.argString("packageName", "package", "pkg") ?: return null
        return packageName.takeIf { it.matches(Regex("""[A-Za-z0-9_.]+""")) && it.contains('.') }
    }

    private data class VerificationResult(
        val verified: Boolean,
        val summary: String,
    )

    private companion object {
        private const val DEFAULT_BRIGHTNESS_PERCENT = 50f
        private const val DEFAULT_BRIGHTNESS_DELTA = 15f
        private const val BRIGHTNESS_TOLERANCE_PERCENT = 4f
        private const val SCREEN_TIMEOUT_TOLERANCE_MS = 1_000
    }
}
