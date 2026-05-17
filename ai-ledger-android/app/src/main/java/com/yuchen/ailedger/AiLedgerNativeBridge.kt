package com.yuchen.ailedger

import android.app.Activity
import android.webkit.JavascriptInterface
import org.json.JSONObject

class AiLedgerNativeBridge(
    private val activity: Activity,
    private val onGlassMode: (GlassMode) -> Unit,
    private val onHaptic: (String) -> Unit,
    private val onOpenView: (String) -> Unit,
) {
    @JavascriptInterface
    fun getCapabilities(): String {
        return JSONObject()
            .put("nativeGlass", true)
            .put("haptic", true)
            .put("postMessage", true)
            .put("openView", true)
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
                "webReady" -> Unit
            }
        }
    }

    @JavascriptInterface
    fun setGlassMode(mode: String?) {
        activity.runOnUiThread { onGlassMode(GlassMode.from(mode)) }
    }
}
