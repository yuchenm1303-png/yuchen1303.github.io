package com.yuchen.ailedger.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val VISUAL_BOOTSTRAP_PLAN_REQUIRED = "visual_bootstrap_plan_required"
private const val VISUAL_BOOTSTRAP_FAILED = "visual_bootstrap_failed"

internal enum class VisualBootstrapAction(val wireValue: String) {
    OpenApp("open_app"),
    Home("home"),
    CurrentScreen("current_screen"),
    NeedUserContext("need_user_context"),
}

internal data class VisualBootstrapPlan(
    val action: VisualBootstrapAction,
    val packageName: String = "",
    val reason: String = "",
) {
    companion object {
        fun fromClientToolCall(call: CloudClientToolCall?): VisualBootstrapPlan? {
            val args = call?.arguments ?: return null
            val plan = firstJSONObject(
                args,
                "bootstrapPlan",
                "visualBootstrapPlan",
                "firstFramePlan",
                "startupPlan",
            )
            val source = plan ?: args
            val type = firstString(
                source,
                "type",
                "action",
                "bootstrapAction",
                "startupAction",
            ).normalizeBootstrapAction()
            if (type.isBlank()) return null
            val action = when (type) {
                "open_app", "openapp" -> VisualBootstrapAction.OpenApp
                "home" -> VisualBootstrapAction.Home
                "current_screen", "currentscreen" -> VisualBootstrapAction.CurrentScreen
                "need_user_context", "ask_user", "need_user_help" -> VisualBootstrapAction.NeedUserContext
                else -> return null
            }
            return VisualBootstrapPlan(
                action = action,
                packageName = firstString(source, "packageName", "package", "appPackage", "targetPackage"),
                reason = firstString(source, "reason", "rationale"),
            )
        }

        private fun firstJSONObject(source: JSONObject, vararg keys: String): JSONObject? {
            keys.forEach { key ->
                source.optJSONObject(key)?.let { return it }
                val text = source.optString(key).trim()
                if (text.startsWith("{") && text.endsWith("}")) {
                    runCatching { JSONObject(text) }.getOrNull()?.let { return it }
                }
            }
            return null
        }

        private fun firstString(source: JSONObject, vararg keys: String): String {
            keys.forEach { key ->
                val value = source.optString(key).trim()
                if (value.isNotBlank()) return value.take(200)
            }
            return ""
        }

        private fun String.normalizeBootstrapAction(): String = trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }
}

internal data class VisualBootstrapResult(
    val ok: Boolean,
    val message: String,
    val logs: List<AgentTaskStepLog> = emptyList(),
)

internal object VisualBootstrapFirstFrameState {
    private val lock = Any()
    private var verifiedTargetPackage: String = ""
    private var forceFirstVisualObservation: Boolean = false

    fun clear() {
        synchronized(lock) {
            verifiedTargetPackage = ""
            forceFirstVisualObservation = false
        }
    }

    fun publishFirstFrameContract(verifiedPackage: String = "") {
        synchronized(lock) {
            verifiedTargetPackage = verifiedPackage.trim()
            forceFirstVisualObservation = true
        }
    }

    fun consumeVerifiedTargetPackage(): String = synchronized(lock) {
        val value = verifiedTargetPackage
        verifiedTargetPackage = ""
        value
    }

    fun consumeForceFirstVisualObservation(): Boolean = synchronized(lock) {
        val value = forceFirstVisualObservation
        forceFirstVisualObservation = false
        value
    }
}

internal class VisualBootstrapRunner(
    appContext: Context,
) {
    private val applicationContext = appContext.applicationContext
    private val installedAppIndex = InstalledAppIndex(applicationContext)
    private val observationCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        VisualObservationCoordinator(
            captureSource = AccessibilityVisualObservationCaptureSource,
            foregroundPackageReader = ForegroundPackageProbe(DeviceShellBridge(applicationContext)),
        )
    }

    suspend fun prepareFirstFrame(
        plan: VisualBootstrapPlan?,
        isStopped: () -> Boolean,
    ): VisualBootstrapResult {
        VisualBootstrapFirstFrameState.clear()
        if (plan == null) {
            return VisualBootstrapResult(
                ok = false,
                message = "$VISUAL_BOOTSTRAP_PLAN_REQUIRED: computer_run_task must include bootstrapPlan selected by the Final Model.",
            )
        }
        return when (plan.action) {
            VisualBootstrapAction.OpenApp -> prepareOpenApp(plan, isStopped)
            VisualBootstrapAction.Home -> prepareHome(plan)
            VisualBootstrapAction.CurrentScreen -> prepareCurrentScreen(plan)
            VisualBootstrapAction.NeedUserContext -> VisualBootstrapResult(
                ok = false,
                message = "$VISUAL_BOOTSTRAP_FAILED: Final Model requested user context before GUI Plus handoff.",
            )
        }
    }

    private suspend fun prepareOpenApp(
        plan: VisualBootstrapPlan,
        isStopped: () -> Boolean,
    ): VisualBootstrapResult {
        val requestedPackage = plan.packageName.trim()
        if (requestedPackage.isBlank()) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: open_app bootstrap requires packageName.")
        }
        if (requestedPackage == applicationContext.packageName) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: first frame must not be the AI Ledger host app.")
        }
        val apps = withContext(Dispatchers.IO) { installedAppIndex.getLaunchableApps(forceReload = false) }
        val installed = apps.firstOrNull { it.packageName == requestedPackage }
            ?: return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: package is not installed or launchable: $requestedPackage")
        val step = CloudAgentStep(
            type = "open_app",
            appName = installed.label,
            packageName = installed.packageName,
            reason = plan.reason.ifBlank { "Final Model bootstrapPlan.open_app" },
        )
        val result = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        val logs = listOf(AgentTaskStepLog(1, applicationContext.packageName, step, result))
        if (!result.ok) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: ${result.message}", logs)
        }
        val verification = observationCoordinator.awaitStableTargetPackage(
            expectedPackage = installed.packageName,
            isStopped = isStopped,
        )
        if (!verification.verified || verification.lastSnapshot?.visual?.hasImage != true) {
            return VisualBootstrapResult(
                ok = false,
                message = "$VISUAL_BOOTSTRAP_FAILED: open_app target was not verified with a fresh screenshot; reason=${verification.reason.wireValue}.",
                logs = logs,
            )
        }
        VisualBootstrapFirstFrameState.publishFirstFrameContract(verifiedPackage = installed.packageName)
        return VisualBootstrapResult(
            ok = true,
            message = "visual_bootstrap_ok:open_app:${installed.packageName}",
            logs = logs,
        )
    }

    private suspend fun prepareHome(plan: VisualBootstrapPlan): VisualBootstrapResult {
        val step = CloudAgentStep(
            type = "home",
            reason = plan.reason.ifBlank { "Final Model bootstrapPlan.home" },
        )
        val result = withContext(Dispatchers.Main) { AiAgentAccessibilityService.executeStep(step) }
        val logs = listOf(AgentTaskStepLog(1, applicationContext.packageName, step, result))
        if (!result.ok) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: ${result.message}", logs)
        }
        val validation = validateFirstFrame("home")
        if (!validation.ok) return validation.copy(logs = logs)
        VisualBootstrapFirstFrameState.publishFirstFrameContract()
        return VisualBootstrapResult(true, "visual_bootstrap_ok:home", logs)
    }

    private suspend fun prepareCurrentScreen(plan: VisualBootstrapPlan): VisualBootstrapResult {
        val validation = validateFirstFrame(plan.action.wireValue)
        if (!validation.ok) return validation
        VisualBootstrapFirstFrameState.publishFirstFrameContract()
        return VisualBootstrapResult(true, "visual_bootstrap_ok:current_screen")
    }

    private suspend fun validateFirstFrame(source: String): VisualBootstrapResult {
        val observation = observationCoordinator.captureTrustedObservation(
            forceVisual = true,
            expectedPackage = "",
        )
        val snapshot = observation.toAgentScreenSnapshot()
        if (snapshot.visual?.hasImage != true) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: $source did not produce a fresh Android screenshot.")
        }
        if (snapshot.packageName == applicationContext.packageName) {
            return VisualBootstrapResult(false, "$VISUAL_BOOTSTRAP_FAILED: $source first frame is the AI Ledger host app.")
        }
        return VisualBootstrapResult(true, "visual_bootstrap_frame_valid:$source")
    }
}
