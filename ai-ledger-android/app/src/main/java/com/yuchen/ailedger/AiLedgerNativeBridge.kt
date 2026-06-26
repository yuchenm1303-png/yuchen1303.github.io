package com.yuchen.ailedger

import android.app.Activity
import android.os.Looper
import android.webkit.JavascriptInterface
import com.yuchen.ailedger.service.AiAgentAccessibilityService
import com.yuchen.ailedger.service.CloudAgentStep
import com.yuchen.ailedger.service.toAgentScreenSnapshot
import kotlinx.coroutines.Dispatchers
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
            .put("agentActions", "open_app,tap_xy,tap_node,long_press,swipe,scroll,input_text,back,home,recents,notifications,quick_settings,wait,finish,need_user_help")
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

        // executeAgentStep 需要等待 Android 手势完成回执。WebView 的 JavaScript bridge
        // 本身运行在专用后台线程，不能再转到主线程后做同步等待，否则会阻塞回执。
        if (type == "executeAgentStep") {
            executeAgentStep(payload.toString())
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

    @JavascriptInterface
    fun executeAgentStep(rawStep: String?): String {
        val stepJson = runCatching { JSONObject(rawStep ?: "{}") }.getOrNull()
            ?: return agentStepError("invalid_agent_step_json", "Agent step JSON 无效。")
        val step = CloudAgentStep.fromJson(stepJson)
            ?: return agentStepError("invalid_agent_step", "Agent step 缺少可执行动作。")

        // 这条同步 JavaScript 接口只能在 WebView bridge 后台线程等待结果。真正的
        // AccessibilityService 调用仍切回主线程，并以挂起方式等待手势 onCompleted/onCancelled。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return agentStepError(
                "agent_step_main_thread_blocked",
                "视觉动作不能在主线程同步等待，请通过 JavaScript bridge 后台线程调用。",
            )
        }
        val result = runCatching {
            runBlocking {
                withContext(Dispatchers.Main.immediate) {
                    AiAgentAccessibilityService.executeStep(step)
                }
            }
        }.getOrElse { error ->
            return agentStepError(
                "agent_step_execution_failed",
                error.message ?: "视觉动作执行失败。",
            )
        }
        return JSONObject()
            .put("ok", result.ok)
            .put("type", step.type)
            .put("serviceEnabled", AiAgentAccessibilityService.isConnected())
            .put("message", result.message)
            .put("reason", result.message)
            .put("shouldContinue", result.shouldContinue)
            .toString()
    }

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
