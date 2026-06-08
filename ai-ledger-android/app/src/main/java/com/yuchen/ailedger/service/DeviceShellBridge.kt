package com.yuchen.ailedger.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

data class DeviceShellStatus(
    val available: Boolean,
    val uidLine: String,
    val isAdbShellLike: Boolean,
    val androidRelease: String,
    val message: String,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuUid: Int? = null,
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
        val shizukuAvailable = isShizukuAvailable()
        val shizukuGranted = shizukuAvailable && isShizukuPermissionGranted()
        val shizukuUid = if (shizukuGranted) runCatching { Shizuku.getUid() }.getOrNull() else null
        val id = if (shizukuGranted) {
            executeShizukuRaw("id", timeoutMs = 900L)
        } else {
            executeRaw("id", timeoutMs = 700L)
        }
        val release = if (shizukuGranted) {
            executeShizukuRaw("getprop ro.build.version.release", timeoutMs = 900L)
        } else {
            executeRaw("getprop ro.build.version.release", timeoutMs = 700L)
        }
        val uidLine = id.output.lineSequence().firstOrNull()?.trim().orEmpty()
        val releaseText = release.output.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { Build.VERSION.RELEASE.orEmpty() }
        val available = id.ok && uidLine.isNotBlank()
        val adbLike = shizukuGranted || uidLine.contains("uid=2000") || uidLine.contains("shell")
        val message = when {
            shizukuGranted -> "Shizuku 已授权，增强内部控制可用。"
            shizukuAvailable -> "检测到 Shizuku 服务，但本应用尚未授权。可以请求 Shizuku 授权后启用增强内部控制。"
            !available -> "基础 shell 不可用，当前只能使用 Android API / Intent 内部控制。"
            adbLike -> "检测到 shell/ADB 级运行身份，可继续扩展强停、settings、dumpsys 等增强工具。"
            else -> "基础 shell 可用，但当前是 App 沙箱身份，不具备强停其他 App 或写 secure/global settings 的权限。"
        }
        return DeviceShellStatus(
            available = available || shizukuAvailable,
            uidLine = uidLine.ifBlank { shizukuUid?.let { "shizuku uid=$it" } ?: "unknown" },
            isAdbShellLike = adbLike,
            androidRelease = releaseText.ifBlank { "unknown" },
            message = message,
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted,
            shizukuUid = shizukuUid,
        )
    }

    fun requestShizukuPermission(): DeviceShellExecResult {
        if (!isShizukuAvailable()) {
            return DeviceShellExecResult(
                ok = false,
                title = "请求 Shizuku 授权",
                output = "",
                error = "没有检测到正在运行的 Shizuku 服务。请先安装并启动 Shizuku，再回到应用请求授权。",
            )
        }
        if (isShizukuPermissionGranted()) {
            return DeviceShellExecResult(
                ok = true,
                title = "请求 Shizuku 授权",
                output = "Shizuku 已授权。",
            )
        }
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            DeviceShellExecResult(
                ok = false,
                title = "请求 Shizuku 授权",
                output = "已发起 Shizuku 授权请求。请在系统弹窗中允许，然后再次执行内部控制任务。",
            )
        }.getOrElse { error ->
            DeviceShellExecResult(
                ok = false,
                title = "请求 Shizuku 授权",
                output = "",
                error = error.message.orEmpty().ifBlank { "发起 Shizuku 授权失败。" },
            )
        }
    }

    fun runSafeDiagnostic(key: String): DeviceShellExecResult? {
        val command = safeDiagnostics[key] ?: return null
        val result = if (isShizukuAvailable() && isShizukuPermissionGranted()) {
            executeShizukuRaw(command.command, timeoutMs = command.timeoutMs)
        } else {
            executeRaw(command.command, timeoutMs = command.timeoutMs)
        }
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
        val result = when {
            status.shizukuGranted -> executeShizukuRaw(command, timeoutMs = timeoutMs.coerceIn(500L, 6_000L))
            status.isAdbShellLike -> executeRaw(command, timeoutMs = timeoutMs.coerceIn(500L, 6_000L))
            else -> return DeviceShellExecResult(
                ok = false,
                title = title,
                output = "",
                error = if (status.shizukuAvailable) {
                    "Shizuku 服务已运行但尚未授权本应用。请先说“请求 Shizuku 授权”，授权后再执行。"
                } else {
                    "当前不是 ADB/shell 级运行身份：${status.uidLine}。需要先接入并启动 Shizuku/ADB Bridge。"
                },
            )
        }
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
            append("Shizuku 服务：").append(if (probe.shizukuAvailable) "已检测到" else "未检测到").append('\n')
            append("Shizuku 授权：").append(if (probe.shizukuGranted) "已授权" else "未授权").append('\n')
            probe.shizukuUid?.let { append("Shizuku UID：").append(it).append('\n') }
            append("基础 shell：").append(if (probe.available) "可用" else "不可用").append('\n')
            append("运行身份：").append(probe.uidLine).append('\n')
            append("Android：").append(probe.androidRelease).append('\n')
            append("权限级别：").append(if (probe.isAdbShellLike) "增强级" else "普通 App 沙箱级").append("\n\n")
            append(probe.message)
            if (probe.shizukuAvailable && !probe.shizukuGranted) {
                append("\n\n你可以说“请求 Shizuku 授权”，我会调起授权请求。")
            } else if (!probe.shizukuAvailable) {
                append("\n\n请先安装并启动 Shizuku，再回到应用请求授权。")
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
            readProcess(process, timeoutMs)
        }.getOrElse { error ->
            ShellRawResult(ok = false, output = "", error = error.message.orEmpty(), exitCode = null)
        }
    }

    private fun executeShizukuRaw(command: String, timeoutMs: Long): ShellRawResult {
        return runCatching {
            val process = createShizukuProcess(command)
            readProcess(process, timeoutMs)
        }.getOrElse { error ->
            ShellRawResult(ok = false, output = "", error = error.message.orEmpty(), exitCode = null)
        }
    }

    private fun createShizukuProcess(command: String): Process {
        val method = Shizuku::class.java.getMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        return method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null,
        ) as Process
    }

    private fun readProcess(process: Process, timeoutMs: Long): ShellRawResult {
        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroy()
            return ShellRawResult(ok = false, output = "", error = "命令超时", exitCode = null)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.exitValue()
        return ShellRawResult(ok = exit == 0, output = output, error = error, exitCode = exit)
    }

    private fun isShizukuAvailable(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    private fun isShizukuPermissionGranted(): Boolean {
        return runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
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
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 23051

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
