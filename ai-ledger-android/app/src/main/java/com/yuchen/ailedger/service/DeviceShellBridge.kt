package com.yuchen.ailedger.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
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

enum class DeviceShellPrivilege(val label: String) {
    AppSandbox("普通 App Shell"),
    ShellOrShizuku("Shizuku/ADB Shell"),
}

data class DeviceShellToolSpec(
    val id: String,
    val title: String,
    val privilege: DeviceShellPrivilege,
    val riskLevel: DeviceControlRiskLevel,
    val readOnly: Boolean,
    val requiresConfirmation: Boolean,
    val description: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("privilege", privilege.name)
        put("privilegeLabel", privilege.label)
        put("riskLevel", riskLevel.name.lowercase())
        put("riskLabel", riskLevel.label)
        put("readOnly", readOnly)
        put("requiresConfirmation", requiresConfirmation)
        put("description", description)
    }
}

class DeviceShellBridge(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun probe(forceRefresh: Boolean = false): DeviceShellStatus {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedProbeStatus?.takeIf { now - cachedProbeAtMs < PROBE_CACHE_TTL_MS }?.let { return it }
        }
        return synchronized(probeCacheLock) {
            val lockedNow = System.currentTimeMillis()
            if (!forceRefresh) {
                cachedProbeStatus?.takeIf { lockedNow - cachedProbeAtMs < PROBE_CACHE_TTL_MS }?.let { return@synchronized it }
            }
            probeFresh().also { status ->
                cachedProbeStatus = status
                cachedProbeAtMs = lockedNow
            }
        }
    }

    private fun probeFresh(): DeviceShellStatus {
        val shizukuAvailable = isShizukuAvailable()
        val shizukuGranted = shizukuAvailable && isShizukuPermissionGranted()
        val shizukuUid = if (shizukuGranted) runCatching { Shizuku.getUid() }.getOrNull() else null
        val id = if (shizukuGranted) executeShizukuRaw("id", 900L) else executeRaw("id", 700L)
        val release = if (shizukuGranted) executeShizukuRaw("getprop ro.build.version.release", 900L) else executeRaw("getprop ro.build.version.release", 700L)
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
        invalidateProbeCache()
        if (!isShizukuAvailable()) {
            return DeviceShellExecResult(false, "请求 Shizuku 授权", "", error = "没有检测到正在运行的 Shizuku 服务。请先安装并启动 Shizuku，再回到应用请求授权。")
        }
        if (isShizukuPermissionGranted()) {
            return DeviceShellExecResult(true, "请求 Shizuku 授权", "Shizuku 已授权。")
        }
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            DeviceShellExecResult(false, "请求 Shizuku 授权", "已发起 Shizuku 授权请求。请在系统弹窗中允许，然后再次执行内部控制任务。")
        }.getOrElse { error ->
            DeviceShellExecResult(false, "请求 Shizuku 授权", "", error = error.message.orEmpty().ifBlank { "发起 Shizuku 授权失败。" })
        }
    }

    fun runSafeDiagnostic(key: String): DeviceShellExecResult? {
        val command = safeDiagnostics[key] ?: return null
        val result = runShellCommand(command.command, command.timeoutMs, requireEnhanced = false, preferShizuku = true)
        return DeviceShellExecResult(result.ok, command.title, result.output.take(MAX_OUTPUT_CHARS).ifBlank { "无输出" }, result.exitCode, result.error.take(MAX_OUTPUT_CHARS))
    }

    fun runReadOnlyEnhancedCommand(title: String, command: String, timeoutMs: Long = 900L): DeviceShellExecResult {
        val result = runShellCommand(command, timeoutMs.coerceIn(300L, 3_000L), requireEnhanced = false, preferShizuku = true)
        return DeviceShellExecResult(result.ok, title, result.output.take(MAX_OUTPUT_CHARS).ifBlank { "无输出" }, result.exitCode, result.error.take(MAX_OUTPUT_CHARS))
    }

    fun runVerifiedEnhancedCommand(
        title: String,
        command: String,
        verifyCommand: String,
        timeoutMs: Long = 1_500L,
        verifyTimeoutMs: Long = 900L,
        verifier: (String) -> Boolean,
    ): DeviceShellExecResult {
        val execute = runEnhancedCommand(title = title, command = command, timeoutMs = timeoutMs)
        if (!execute.ok) return execute
        val verify = runReadOnlyEnhancedCommand(title = "$title 验证", command = verifyCommand, timeoutMs = verifyTimeoutMs)
        val verified = verify.ok && verifier(verify.output)
        val mergedOutput = buildString {
            if (execute.output.isNotBlank() && execute.output != "无输出") append(execute.output)
            append(if (verified) "\nverified=true" else "\nverified=false")
            append("\nverifyOutput=").append(verify.output.ifBlank { "无输出" })
        }.trim()
        val mergedError = buildString {
            if (execute.error.isNotBlank()) append(execute.error)
            if (!verified) {
                if (length > 0) append('\n')
                append(verify.error.ifBlank { "执行后验证未通过" })
            }
        }
        return DeviceShellExecResult(verified, title, mergedOutput, execute.exitCode, mergedError.take(MAX_OUTPUT_CHARS))
    }

    fun runEnhancedCommand(title: String, command: String, timeoutMs: Long = 1_500L): DeviceShellExecResult {
        val status = probe(forceRefresh = true)
        if (!status.isAdbShellLike) {
            return DeviceShellExecResult(
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
        val result = runShellCommand(command, timeoutMs.coerceIn(500L, 6_000L), requireEnhanced = true, preferShizuku = true)
        return DeviceShellExecResult(result.ok, title, result.output.take(MAX_OUTPUT_CHARS).ifBlank { "无输出" }, result.exitCode, result.error.take(MAX_OUTPUT_CHARS))
    }

    fun controlledToolCatalogJson(): JSONArray = JSONArray().apply { controlledTools.forEach { put(it.toJson()) } }

    fun controlledToolCatalogSummary(): String {
        val readOnly = controlledTools.count { it.readOnly }
        val writeTools = controlledTools.size - readOnly
        return "Shell 工具平台：${controlledTools.size} 个受控模板（只读 $readOnly 个，写入/管理 $writeTools 个），不开放自由 shell。"
    }

    fun enhancedModeGuide(): String {
        val probe = probe(forceRefresh = true)
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
            if (probe.shizukuAvailable && !probe.shizukuGranted) append("\n\n你可以说“请求 Shizuku 授权”，我会调起授权请求。")
            else if (!probe.shizukuAvailable) append("\n\n请先安装并启动 Shizuku，再回到应用请求授权。")
            append("\n\n")
            append(controlledToolCatalogSummary())
            append("\n")
            append(DeviceControlCapabilityRegistry.publicSummary())
        }
    }

    private fun runShellCommand(command: String, timeoutMs: Long, requireEnhanced: Boolean, preferShizuku: Boolean): ShellRawResult {
        val status = probe(forceRefresh = false)
        if (requireEnhanced && !status.isAdbShellLike) {
            return ShellRawResult(false, "", "缺少 Shizuku/ADB Shell 增强权限", null)
        }
        return when {
            preferShizuku && status.shizukuGranted -> executeShizukuRaw(command, timeoutMs)
            else -> executeRaw(command, timeoutMs)
        }
    }

    private fun executeRaw(command: String, timeoutMs: Long): ShellRawResult {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
            readProcess(process, timeoutMs)
        }.getOrElse { error -> ShellRawResult(false, "", error.message.orEmpty(), null) }
    }

    private fun executeShizukuRaw(command: String, timeoutMs: Long): ShellRawResult {
        return runCatching {
            val process = createShizukuProcess(command)
            readProcess(process, timeoutMs)
        }.getOrElse { error -> ShellRawResult(false, "", error.message.orEmpty(), null) }
    }

    private fun createShizukuProcess(command: String): Process {
        val method = Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        return method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
    }

    private fun readProcess(process: Process, timeoutMs: Long): ShellRawResult {
        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroy()
            return ShellRawResult(false, "", "命令超时", null)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.exitValue()
        return ShellRawResult(exit == 0, output, error, exit)
    }

    private fun isShizukuAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun isShizukuPermissionGranted(): Boolean = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private data class ShellRawResult(val ok: Boolean, val output: String, val error: String, val exitCode: Int?)

    private data class SafeShellDiagnostic(val title: String, val command: String, val timeoutMs: Long = 900L)

    companion object {
        private const val MAX_OUTPUT_CHARS = 1800
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 23051
        private const val PROBE_CACHE_TTL_MS = 20_000L
        private val probeCacheLock = Any()
        @Volatile private var cachedProbeStatus: DeviceShellStatus? = null
        @Volatile private var cachedProbeAtMs: Long = 0L

        fun invalidateProbeCache() {
            synchronized(probeCacheLock) {
                cachedProbeStatus = null
                cachedProbeAtMs = 0L
            }
        }

        private val controlledTools = listOf(
            DeviceShellToolSpec("shell.identity", "读取 Shell 身份", DeviceShellPrivilege.AppSandbox, DeviceControlRiskLevel.Low, true, false, "执行 id / getprop 等只读命令，用于判断当前内部控制权限。"),
            DeviceShellToolSpec("shell.safe_diagnostic", "只读系统诊断", DeviceShellPrivilege.AppSandbox, DeviceControlRiskLevel.Low, true, false, "执行 allowlist 里的 dumpsys/getprop/settings get 诊断命令。"),
            DeviceShellToolSpec("network.wifi_toggle", "Wi‑Fi 开关", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.Medium, false, false, "固定模板执行 svc wifi enable/disable，并通过 global wifi_on 读回验证。"),
            DeviceShellToolSpec("network.bluetooth_toggle", "蓝牙开关", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.Medium, false, false, "固定模板执行蓝牙管理命令，并通过 global bluetooth_on 读回验证。"),
            DeviceShellToolSpec("network.mobile_data_toggle", "移动数据开关", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.Medium, false, false, "固定模板执行 svc data enable/disable，并通过 global mobile_data 读回验证。"),
            DeviceShellToolSpec("system.dark_mode", "深色模式", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.Medium, false, false, "固定模板执行 cmd uimode night yes/no/auto，并执行后读回验证。"),
            DeviceShellToolSpec("system.global_settings_write", "写入 global settings", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.High, false, true, "仅允许固定模板写入动画缩放等 global settings，并执行后读取验证。"),
            DeviceShellToolSpec("app.force_stop", "强停应用", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.High, false, true, "固定模板执行 am force-stop，并在执行后尝试用 pidof 验证。"),
            DeviceShellToolSpec("app.package_admin", "应用数据/启停管理", DeviceShellPrivilege.ShellOrShizuku, DeviceControlRiskLevel.Critical, false, true, "固定模板执行 pm clear / uninstall / disable / enable，并通过 package 状态读取验证。"),
        )

        private val safeDiagnostics = mapOf(
            "identity" to SafeShellDiagnostic("Shell 身份", "id"),
            "system_properties" to SafeShellDiagnostic("系统属性", "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk"),
            "animation_scales" to SafeShellDiagnostic("动画缩放状态", "settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale"),
            "network_switches" to SafeShellDiagnostic("网络开关状态", "settings get global wifi_on; settings get global bluetooth_on; settings get global mobile_data"),
            "ui_mode" to SafeShellDiagnostic("深色模式状态", "cmd uimode night"),
            "battery_dump" to SafeShellDiagnostic("电池 dumpsys 摘要", "dumpsys battery | sed -n '1,16p'", 1200L),
        )
    }
}
