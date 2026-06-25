package com.yuchen.ailedger.service

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock

internal data class ForegroundPackageResolution(
    val packageName: String,
    val matchedExpectedPackage: Boolean,
    val source: String,
    val evidencePackages: Set<String>,
)

/**
 * Keeps the exact package selected by DeepSeek available during the short launch handoff window.
 * It never chooses an app from user semantics; the value can only come from a cloud open_app step.
 */
internal object ForegroundTargetBinding {
    @Volatile private var expectedPackage: String = ""

    fun reset() {
        expectedPackage = ""
    }

    fun bind(packageName: String) {
        expectedPackage = packageName.trim().takeIf(::isPackageName).orEmpty()
    }

    fun current(): String = expectedPackage
}

/** Pure package-evidence policy, separated for local unit tests. */
internal object ForegroundPackageResolutionPolicy {
    fun resolve(
        observedPackage: String,
        expectedPackage: String,
        foregroundProcessPackages: Set<String>,
        shellForegroundPackages: Set<String>,
    ): ForegroundPackageResolution {
        val observed = observedPackage.normalizedPackage()
        val expected = expectedPackage.normalizedPackage()
        val processPackages = foregroundProcessPackages.mapNotNullTo(linkedSetOf()) {
            it.normalizedPackage().takeIf(::isPackageName)
        }
        val shellPackages = shellForegroundPackages.mapNotNullTo(linkedSetOf()) {
            it.normalizedPackage().takeIf(::isPackageName)
        }
        val evidence = linkedSetOf<String>().apply {
            observed.takeIf(::isPackageName)?.let(::add)
            addAll(shellPackages)
            addAll(processPackages)
        }
        val observedIsWeak = observed.isBlank() || observed in WEAK_FOREGROUND_PACKAGES
        val strongShellPackages = shellPackages.filterTo(linkedSetOf()) { it !in WEAK_FOREGROUND_PACKAGES }

        if (expected.isNotBlank() && observed == expected) {
            return ForegroundPackageResolution(expected, true, "accessibility_exact", evidence)
        }
        if (
            expected.isNotBlank() &&
            observedIsWeak &&
            expected in strongShellPackages &&
            strongShellPackages.all { it == expected }
        ) {
            return ForegroundPackageResolution(expected, true, "shell_foreground_exact", evidence)
        }
        if (expected.isNotBlank() && observedIsWeak && expected in processPackages) {
            return ForegroundPackageResolution(expected, true, "foreground_process_exact", evidence)
        }

        val resolved = when {
            observed.isNotBlank() && observed !in WEAK_FOREGROUND_PACKAGES -> observed
            strongShellPackages.size == 1 -> strongShellPackages.single()
            observedIsWeak -> processPackages.firstOrNull { it !in WEAK_FOREGROUND_PACKAGES }.orEmpty()
            observed.isNotBlank() -> observed
            else -> "unknown"
        }
        val source = when {
            resolved == observed && observed.isNotBlank() -> "accessibility"
            resolved in strongShellPackages -> "shell_foreground"
            resolved in processPackages -> "foreground_process"
            else -> "unknown"
        }
        return ForegroundPackageResolution(resolved.ifBlank { observed.ifBlank { "unknown" } }, false, source, evidence)
    }

    private val WEAK_FOREGROUND_PACKAGES = setOf(
        "unknown",
        VisualExecutionSessionState.ASSISTANT_HOST_PACKAGE,
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
    )
}

/**
 * Task-time foreground verifier. It uses lightweight process evidence first and only runs a
 * read-only Shizuku/ADB dumpsys probe when accessibility did not expose the cloud-selected target.
 * Results are cached briefly so visual-loop polling does not create sustained shell load.
 */
internal object ForegroundPackageResolver {
    private val lock = Any()
    @Volatile private var shellBridge: DeviceShellBridge? = null
    @Volatile private var cachedShellPackages: Set<String> = emptySet()
    @Volatile private var cachedShellAtMs: Long = 0L

    fun resolve(
        context: Context,
        observedPackage: String,
        expectedPackage: String,
    ): ForegroundPackageResolution {
        val expected = expectedPackage.normalizedPackage()
        val observed = observedPackage.normalizedPackage()
        val processPackages = foregroundProcessPackages(context)
        val needsShellEvidence = expected.isNotBlank() && observed != expected
        val shellPackages = if (needsShellEvidence) shellForegroundPackages(context) else emptySet()
        return ForegroundPackageResolutionPolicy.resolve(
            observedPackage = observed,
            expectedPackage = expected,
            foregroundProcessPackages = processPackages,
            shellForegroundPackages = shellPackages,
        )
    }

    private fun foregroundProcessPackages(context: Context): Set<String> {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptySet()
        return runCatching {
            manager.runningAppProcesses.orEmpty()
                .asSequence()
                .filter { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                .flatMap { process -> sequenceOf(process.processName) + process.pkgList.orEmpty().asSequence() }
                .map(String::normalizedPackage)
                .filter(::isPackageName)
                .toCollection(linkedSetOf())
        }.getOrDefault(emptySet())
    }

    private fun shellForegroundPackages(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        cachedShellPackages.takeIf { now - cachedShellAtMs <= SHELL_CACHE_TTL_MS }?.let { return it }
        return synchronized(lock) {
            val lockedNow = SystemClock.elapsedRealtime()
            cachedShellPackages.takeIf { lockedNow - cachedShellAtMs <= SHELL_CACHE_TTL_MS }?.let { return@synchronized it }
            val bridge = shellBridge ?: DeviceShellBridge(context.applicationContext).also { shellBridge = it }
            val result = bridge.runReadOnlyEnhancedCommand(
                title = "读取当前前台应用",
                command = FOREGROUND_DUMPSYS_COMMAND,
                timeoutMs = SHELL_QUERY_TIMEOUT_MS,
            )
            val packages = if (result.ok) parseForegroundPackages(result.output) else emptySet()
            cachedShellPackages = packages
            cachedShellAtMs = lockedNow
            packages
        }
    }

    internal fun parseForegroundPackages(output: String): Set<String> {
        return PACKAGE_COMPONENT_REGEX.findAll(output)
            .map { it.groupValues[1].normalizedPackage() }
            .filter(::isPackageName)
            .toCollection(linkedSetOf())
    }

    private const val SHELL_CACHE_TTL_MS = 520L
    private const val SHELL_QUERY_TIMEOUT_MS = 900L
    private const val FOREGROUND_DUMPSYS_COMMAND =
        "dumpsys activity activities | grep -m 1 -E 'topResumedActivity|mResumedActivity|ResumedActivity'; " +
            "dumpsys window windows | grep -m 1 -E 'mCurrentFocus|mFocusedApp'"
    private val PACKAGE_COMPONENT_REGEX = Regex("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+")
}

private fun String.normalizedPackage(): String = trim().removePrefix("package:").substringBefore(':')

private fun isPackageName(value: String): Boolean {
    if (value.isBlank() || value == "unknown") return false
    return PACKAGE_NAME_REGEX.matches(value)
}

private val PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
