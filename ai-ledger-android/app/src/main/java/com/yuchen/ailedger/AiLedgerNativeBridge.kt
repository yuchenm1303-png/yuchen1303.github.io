package com.yuchen.ailedger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.view.inputmethod.InputMethodManager
import com.yuchen.ailedger.agent.AgentAccessibilityService
import com.yuchen.ailedger.agent.AgentInputMethodService
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
            .put("agentAccessibility", AgentAccessibilityService.isEnabled())
            .put("agentInputMethodActive", AgentInputMethodService.isActive())
            .put("agentObserveScreen", true)
            .put("agentExecuteStep", true)
            .put("agentActions", "tap_xy,tap_node,long_press,swipe,scroll,input_text,back,home,recents,notifications,quick_settings,wait,finish,need_user_help")
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
                "openAccessibilitySettings" -> openAccessibilitySettings()
                "openInputMethodSettings" -> openInputMethodSettings()
                "showInputMethodPicker" -> showInputMethodPicker()
                "executeAgentStep" -> executeAgentStep(payload.toString())
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
    fun isAgentAccessibilityEnabled(): Boolean = AgentAccessibilityService.isEnabled()

    @JavascriptInterface
    fun isAgentInputMethodActive(): Boolean = AgentInputMethodService.isActive()

    @JavascriptInterface
    fun observeAgentScreen(): String = AgentAccessibilityService.observe().toString()

    @JavascriptInterface
    fun executeAgentStep(rawStep: String?): String = AgentAccessibilityService.execute(rawStep).toString()

    @JavascriptInterface
    fun openAccessibilitySettings(): Boolean {
        return runCatching {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            true
        }.getOrDefault(false)
    }

    @JavascriptInterface
    fun openInputMethodSettings(): Boolean {
        return runCatching {
            activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            true
        }.getOrDefault(false)
    }

    @JavascriptInterface
    fun showInputMethodPicker(): Boolean {
        return runCatching {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            true
        }.getOrDefault(false)
    }
}
