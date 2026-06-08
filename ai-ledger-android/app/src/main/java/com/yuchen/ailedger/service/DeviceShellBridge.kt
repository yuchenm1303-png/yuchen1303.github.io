package com.yuchen.ailedger.service

import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit

data class DeviceShellStatus(
    val available: Boolean,
    val uidLine: String,
    val isAdbShellLike: Boolean,
    val androidRelease: String,
    val message: String,
)

data class DeviceShellExecResult(
    val ok: Boolean,
    val title: String,
    val output: String,
    val exitCode: Int? = null,
    val error: String = "",
)

class DeviceShellBridge(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun probe(): DeviceShellStatus {
        val id = executeRaw("id", timeoutMs = 700L)
        val release = executeRaw("getprop ro.build.version.release", timeoutMs = 700L)
        val uidLine = id.output.lineSequence().firstOrNull()?.trim().orEmpty()
        val releaseText = release.output.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { Build.VERSION.RELEASE.orEmpty() }
        val available = id.ok && uidLine.isNotBlank()
        val adbLike = uidLine.contains("uid=2000") || uidLine.contains("shell")
        val message = when {
            !available -> "基础 shell 不可用，当前只能使用 Android API / Intent 内部控制。"
            adbLike -> "检测到 shell/ADB 级运行身份，可继续扩展强停、settings、dumpsys 等增强工具。"
            else -> "基础 shell 可用，但当前是 App 沙箱身份，不具备强停其他 App 或写 secure/global settings 的权限。"
        }
        return DeviceShellStatus(
            available = available,
            uidLine = uidLine.ifBlank { "unknown" },
            isAdbShellLike = adbLike,
            androidRelease = releaseText.ifBlank { "unknown" },
            message = message,
        )
    }

    fun runSafeDiagnostic(key: String): DeviceShellExecResult? {
        val command = safeDiagnostics[key] ?: return null
        val result = executeRaw(command.command, timeoutMs = command.timeoutMs)
        return DeviceShellExecResult(
            ok = result.ok,
            title = command.title,
            output = result.output.take(MAX_OUTPUT_CHARS).ifBlank { "无输出" },
            exitCode = result.exitCode,
            error = result.error.take(MAX_OUTPUT_CHARS),
        )
    }

    fun runEnhancedCommand(
        title: String,
        command: String,
        timeoutMs: Long = 1_500L,
    ): DeviceShellExecResult {
        val status = probe()
        if (!status.isAdbShellLike) {
            return DeviceShellExecResult(
                ok = false,
                title = title,
                output = "",
                error = "当前不是 ADB/shell 级运行身份：${status.uidLine}。需要先接入并授权 Shizuku/ADB Bridge。",
            )
        }
        val result = executeRaw(command, timeoutMs = timeoutMs.coerceIn(500L, 6_000L))
        return DeviceShellExecResult(
            ok = result.ok,
            title = title,
            output = result.output.take(MAX_OUTPUT_CHARS).ifBlank { "无输出" },
            exitCode = result.exitCode,
            error = result.error.take(MAX_OUTPUT_CHARS),
        )
    }

    fun enhancedModeGuide(): String {
        val probe = probe()
        return buildString {
            append("增强模式状态\n\n")
            append("基础 shell：").append(if (probe.available) "可用" else "不可用").append('\n')
            append("运行身份：").append(probe.uidLine).append('\n')
            append("Android：").append(probe.androidRelease).append('\n')
            append("权限级别：").append(if (probe.isAdbShellLike) "ADB/shell 级" else "普通 App 沙箱级").append("\n\n")
            append(probe.message)
            if (!probe.isAdbShellLike) {
                append("\n\n下一步需要接入 Shizuku/ADB Bridge，才能安全执行 am force-stop、settings put、cmd appops、dumpsys 深度诊断等能力。")
            }
            append("\n\n")
            append(DeviceControlCapabilityRegistry.publicSummary())
        }
    }

    private fun executeRaw(command: String, timeoutMs: Long): ShellRawResult {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroy()
                return ShellRawResult(ok = false, output = "", error = "命令超时", exitCode = null)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exit = process.exitValue()
            ShellRawResult(ok = exit == 0, output = output, error = error, exitCode = exit)
        }.getOrElse { error ->
            ShellRawResult(ok = false, output = "", error = error.message.orEmpty(), exitCode = null)
        }
    }

    private data class ShellRawResult(
        val ok: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int?,
    )

    private data class SafeShellDiagnostic(
        val title: String,
        val command: String,
        val timeoutMs: Long = 900L,
    )

    companion object {
        private const val MAX_OUTPUT_CHARS = 1800

        private val safeDiagnostics = mapOf(
            "identity" to SafeShellDiagnostic(
                title = "Shell 身份",
                command = "id",
            ),
            "system_properties" to SafeShellDiagnostic(
                title = "系统属性",
                command = "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk",
            ),
            "animation_scales" to SafeShellDiagnostic(
                title = "动画缩放状态",
                command = "settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale",
            ),
            "battery_dump" to SafeShellDiagnostic(
                title = "电池 dumpsys 摘要",
                command = "dumpsys battery | sed -n '1,16p'",
                timeoutMs = 1200L,
            ),
        )
    }
}
