package com.yuchen.ailedger

import android.app.Activity
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.yuchen.ailedger.service.AiAgentAccessibilityService
import com.yuchen.ailedger.service.CloudAgentStep
import com.yuchen.ailedger.service.toAgentScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiLedgerNativeBridge(
    private val activity: Activity,
    private val onGlassMode: (GlassMode) -> Unit,
    private val onHaptic: (String) -> Unit,
    private val onOpenView: (String) -> Unit,
    private val systemActionRouter: SystemActionRouter = SystemActionRouter(activity),
) {
    private val agentStepResults = NativeAgentStepResultStore()

    @JavascriptInterface
    fun getCapabilities(): String {
        return JSONObject()
            .put("nativeGlass", true)
            .put("haptic", true)
            .put("postMessage", true)
            .put("openView", true)
            .put("openApp", true)
            .put("setAlarm", true)
            .put("startNavigation", true)
            .put("agentAccessibility", AiAgentAccessibilityService.isConnected())
            .put("agentObserveScreen", true)
            .put("agentExecuteStep", true)
            .put("agentExecuteStepAsync", true)
            .put("agentStepResultPolling", true)
            .put("agentActions", CloudAgentStep.supportedTypes.sorted().joinToString(","))
            .put("glassModes", "basic,blur,liquid,safe")
            .toString()
    }

    @JavascriptInterface
    fun haptic(style: String?) {
        activity.runOnUiThread { onHaptic(style ?: "light") }
    }

    @JavascriptInterface
    fun postMessage(message: String?) {
        if (message.isNullOrBlank()) return
        val data = runCatching { JSONObject(message) }.getOrNull() ?: return
        val type = data.optString("type")
        val payload = data.optJSONObject("payload") ?: JSONObject()

        // 同步接口继续兼容旧网页；新网页可提交 requestId 后通过 pollAgentStepResult 读取结果。
        if (type == "executeAgentStep") {
            executeAgentStep(payload.toString())
            return
        }
        if (type == "executeAgentStepAsync") {
            executeAgentStepAsync(payload.toString(), payload.optString("requestId"))
            return
        }

        activity.runOnUiThread {
            when (type) {
                "haptic" -> onHaptic(payload.optString("style", "light"))
                "setGlassMode" -> onGlassMode(GlassMode.from(payload.optString("mode", "basic")))
                "openView" -> onOpenView(payload.optString("view", "ai"))
                "closeQuickAi" -> activity.finish()
                "openFullApp" -> onOpenView("ai")
                "openApp" -> openApp(payload.optString("packageName"), payload.optString("fallbackName"))
                "startNavigation" -> startNavigation(payload.optString("target"))
                "setAlarm" -> setAlarm(payload.toString())
                "observeAgentScreen" -> observeAgentScreen()
                "webReady" -> Unit
            }
        }
    }

    @JavascriptInterface
    fun setGlassMode(mode: String?) {
        activity.runOnUiThread { onGlassMode(GlassMode.from(mode)) }
    }

    @JavascriptInterface
    fun openApp(packageName: String?, fallbackName: String?) {
        if (packageName.isNullOrBlank()) return
        activity.runOnUiThread { systemActionRouter.openApp(packageName, fallbackName) }
    }

    @JavascriptInterface
    fun startNavigation(target: String?) {
        if (target.isNullOrBlank()) return
        activity.runOnUiThread { systemActionRouter.startNavigation(target) }
    }

    @JavascriptInterface
    fun setAlarm(rawAlarm: String?) {
        val alarm = runCatching { JSONObject(rawAlarm ?: "{}") }.getOrNull() ?: JSONObject()
        val hour = alarm.optInt("hour", -1)
        val minute = alarm.optInt("minute", -1)
        val message = alarm.optString("message", "AI 助手提醒")
        if (hour !in 0..23 || minute !in 0..59) return
        activity.runOnUiThread { systemActionRouter.setAlarm(hour, minute, message) }
    }

    @JavascriptInterface
    fun isAgentAccessibilityEnabled(): Boolean = AiAgentAccessibilityService.isConnected()

    @JavascriptInterface
    fun observeAgentScreen(): String {
        return AiAgentAccessibilityService
            .captureFreshSnapshot()
            .toAgentScreenSnapshot()
            .toJson(includeImage = false)
            .put("ok", true)
            .put("serviceEnabled", AiAgentAccessibilityService.isConnected())
            .toString()
    }

    /**
     * Backward-compatible synchronous bridge. It blocks only the WebView bridge thread and never
     * the Android main thread while the accessibility gesture waits for its completion callback.
     */
    @JavascriptInterface
    fun executeAgentStep(rawStep: String?): String {
        val step = parseAgentStep(rawStep) ?: return agentStepError(
            "invalid_agent_step",
            "Agent step JSON 无效或缺少可执行动作。",
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return agentStepError(
                "agent_step_main_thread_blocked",
                "视觉动作不能在主线程同步等待，请使用异步接口。",
            )
        }
        return runCatching {
            runBlocking {
                withContext(Dispatchers.Main.immediate) {
                    AiAgentAccessibilityService.executeStep(step)
                }
            }.let { result -> agentStepResult(step, result.ok, result.message, result.shouldContinue) }
        }.getOrElse { error ->
            agentStepError(
                "agent_step_execution_failed",
                error.message ?: "视觉动作执行失败。",
            )
        }
    }

    /** Submit without occupying the WebView bridge thread; poll with [pollAgentStepResult]. */
    @JavascriptInterface
    fun executeAgentStepAsync(rawStep: String?, preferredRequestId: String?): String {
        val step = parseAgentStep(rawStep) ?: return agentStepError(
            "invalid_agent_step",
            "Agent step JSON 无效或缺少可执行动作。",
        )
        val owner = activity as? LifecycleOwner ?: return agentStepError(
            "agent_step_lifecycle_unavailable",
            "当前页面不支持异步视觉动作生命周期。",
        )
        val requestId = agentStepResults.createRequestId(preferredRequestId)
        agentStepResults.start(requestId)
        owner.lifecycleScope.launch {
            val payload = runCatching {
                val result = AiAgentAccessibilityService.executeStep(step)
                agentStepResult(step, result.ok, result.message, result.shouldContinue)
            }.getOrElse { error ->
                agentStepError(
                    "agent_step_execution_failed",
                    error.message ?: "视觉动作执行失败。",
                )
            }
            agentStepResults.complete(requestId, payload)
        }
        return JSONObject()
            .put("ok", true)
            .put("accepted", true)
            .put("pending", true)
            .put("requestId", requestId)
            .put("type", step.type)
            .toString()
    }

    @JavascriptInterface
    fun pollAgentStepResult(requestId: String?, consumeReady: Boolean): String {
        val cleanId = requestId.orEmpty().trim()
        if (cleanId.isBlank()) return agentStepError("missing_request_id", "缺少异步动作 requestId。")
        return when (val result = agentStepResults.poll(cleanId, consumeReady)) {
            NativeAgentStepPollResult.Missing -> JSONObject()
                .put("ok", false)
                .put("pending", false)
                .put("error", "request_not_found")
                .put("requestId", cleanId)
                .toString()
            NativeAgentStepPollResult.Pending -> JSONObject()
                .put("ok", true)
                .put("pending", true)
                .put("requestId", cleanId)
                .toString()
            is NativeAgentStepPollResult.Ready -> JSONObject()
                .put("ok", true)
                .put("pending", false)
                .put("requestId", cleanId)
                .put("result", JSONObject(result.payload))
                .toString()
        }
    }

    private fun parseAgentStep(rawStep: String?): CloudAgentStep? {
        val stepJson = runCatching { JSONObject(rawStep ?: "{}") }.getOrNull() ?: return null
        return CloudAgentStep.fromJson(stepJson)
    }

    private fun agentStepResult(
        step: CloudAgentStep,
        ok: Boolean,
        message: String,
        shouldContinue: Boolean,
    ): String = JSONObject()
        .put("ok", ok)
        .put("type", step.type)
        .put("serviceEnabled", AiAgentAccessibilityService.isConnected())
        .put("message", message)
        .put("reason", message)
        .put("shouldContinue", shouldContinue)
        .toString()

    private fun agentStepError(error: String, message: String): String {
        return JSONObject()
            .put("ok", false)
            .put("error", error)
            .put("message", message)
            .put("reason", message)
            .put("shouldContinue", true)
            .toString()
    }
}
