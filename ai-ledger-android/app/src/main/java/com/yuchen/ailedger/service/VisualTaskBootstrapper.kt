package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val VISUAL_BOOTSTRAP_HOME_SETTLE_MS = 520L

internal object VisualTaskBootstrapper {
    suspend fun prepareFirstFrame(
        appContext: Context,
        executionMode: AgentExecutionMode,
        isStopped: () -> Boolean,
    ): VisualTaskBootstrapResult {
        if (isStopped()) return VisualTaskBootstrapResult.Skipped("task_stopped")
        val context = appContext.applicationContext
        val hostPackage = context.packageName.orEmpty().trim()
        if (hostPackage.isBlank()) return VisualTaskBootstrapResult.Skipped("host_package_blank")

        val foreground = withContext(Dispatchers.IO) {
            ForegroundPackageProbe(DeviceShellBridge(context)).probe()
        }
        val foregroundPackage = foreground.packageName.trim()
        if (foregroundPackage != hostPackage) {
            return VisualTaskBootstrapResult.Skipped(
                reason = "foreground_not_host",
                foregroundPackage = foregroundPackage,
                foregroundSource = foreground.source.wireValue,
            )
        }

        if (isStopped()) return VisualTaskBootstrapResult.Skipped("task_stopped")
        val lease = AgentRuntimeController.acquireCleanVisualCaptureLease()
        return try {
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            delay(VISUAL_BOOTSTRAP_HOME_SETTLE_MS)
            val result = VisualTaskBootstrapResult.HomeLaunched(
                foregroundPackage = foregroundPackage,
                foregroundSource = foreground.source.wireValue,
                executionMode = executionMode.name,
            )
            recordBootstrap(result)
            result
        } catch (error: Throwable) {
            val result = VisualTaskBootstrapResult.Skipped(
                reason = "home_launch_failed:${error.message.orEmpty().take(80)}",
                foregroundPackage = foregroundPackage,
                foregroundSource = foreground.source.wireValue,
            )
            recordBootstrap(result)
            result
        } finally {
            lease.close()
        }
    }

    private fun recordBootstrap(result: VisualTaskBootstrapResult) {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "visual_first_frame_bootstrap",
            details = result.toJson(),
        )
    }
}

internal sealed class VisualTaskBootstrapResult {
    abstract val reason: String
    abstract val foregroundPackage: String
    abstract val foregroundSource: String

    data class HomeLaunched(
        override val foregroundPackage: String,
        override val foregroundSource: String,
        val executionMode: String,
    ) : VisualTaskBootstrapResult() {
        override val reason: String = "host_controller_sent_home"
    }

    data class Skipped(
        override val reason: String,
        override val foregroundPackage: String = "",
        override val foregroundSource: String = "",
    ) : VisualTaskBootstrapResult()

    fun toJson(): JSONObject = JSONObject().apply {
        put("reason", reason)
        put("foregroundPackage", foregroundPackage)
        put("foregroundSource", foregroundSource)
        put("sentHome", this@VisualTaskBootstrapResult is HomeLaunched)
        if (this@VisualTaskBootstrapResult is HomeLaunched) {
            put("executionMode", executionMode)
        }
    }
}
