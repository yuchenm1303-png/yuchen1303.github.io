package com.yuchen.ailedger

import android.app.Activity
import android.webkit.JavascriptInterface
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
}
