package com.yuchen.ailedger.service

import android.content.Context
import android.os.Build
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class AgentDeviceContextSnapshot(
    val json: JSONObject,
    val summary: String,
)

object AgentDeviceContextProvider {
    fun build(
        context: Context,
        screen: AgentScreenSnapshot,
        installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
    ): AgentDeviceContextSnapshot {
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val launchableApps = installedAppIndex.getLaunchableApps().take(MAX_LAUNCHABLE_APPS)
        val currentPackage = screen.currentApp.ifBlank { "unknown" }
        val installedAppsJson = JSONArray().apply {
            launchableApps.forEach { app ->
                put(JSONObject().apply {
                    put("label", app.label)
                    put("packageName", app.packageName)
                    put("launchable", true)
                    put("aliases", JSONArray().apply {
                        installedAppIndex.aliasesFor(app).take(MAX_ALIASES_PER_APP).forEach { put(it) }
                    })
                })
            }
        }
        val json = JSONObject().apply {
            put("schema", "android_device_context_v1")
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER.orEmpty())
                put("brand", Build.BRAND.orEmpty())
                put("model", Build.MODEL.orEmpty())
                put("androidSdk", Build.VERSION.SDK_INT)
                put("androidRelease", Build.VERSION.RELEASE.orEmpty())
                put("locale", Locale.getDefault().toLanguageTag())
            })
            put("screen", JSONObject().apply {
                put("widthPx", metrics.widthPixels)
                put("heightPx", metrics.heightPixels)
                put("density", metrics.density)
                put("scaledDensity", metrics.scaledDensity)
                put("coordinateProtocol", "normalized_screen_0_1")
            })
            put("currentApp", JSONObject().apply {
                put("packageName", currentPackage)
                put("isLauncherOrSystemSurface", isLauncherLikePackage(currentPackage))
            })
            put("accessibility", JSONObject().apply {
                put("connected", true)
                put("nodeCount", screen.nodeCount)
                put("capturedNodeCount", screen.capturedNodeCount)
                put("clickableCount", screen.clickableNodes.size)
                put("inputCount", screen.inputNodes.size)
                put("scrollableCount", screen.scrollableNodes.size)
                put("hasScreenshot", screen.hasVisualImage)
            })
            put("installedApps", installedAppsJson)
            put("availableTools", JSONArray(CloudAgentStep.supportedTypes.toList()))
            put("toolRules", JSONArray().apply {
                put("打开应用必须优先使用 open_app，并从 installedApps 中选择真实 label/packageName；不要凭常识编 packageName。")
                put("在桌面或启动器页面时，不要通过点击文件夹或翻页肉眼寻找 App 图标；如果目标是打开应用，直接返回 open_app。")
                put("App 内页面导航才使用 tap_xy、tap_node、scroll、swipe。")
                put("如果目标 App 不在 installedApps 中，返回 need_user_help，不要猜包名。")
                put("同一个动作失败或被本地拒绝后，下一轮必须避开相同路径，改用其他工具或说明无法继续。")
            })
            put("privacy", JSONObject().apply {
                put("scope", "只上传可启动应用名称、包名和基础设备执行能力，不上传联系人、文件、权限列表或后台运行应用。")
            })
        }
        val summary = buildString {
            append("设备：")
            append(Build.BRAND.orEmpty().ifBlank { "Android" })
            append(" ")
            append(Build.MODEL.orEmpty().ifBlank { "设备" })
            append("，Android ")
            append(Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() })
            append("，屏幕 ")
            append(metrics.widthPixels).append("x").append(metrics.heightPixels)
            append("，当前包名 ").append(currentPackage)
            append("，可启动应用 ").append(launchableApps.size).append(" 个。")
        }
        return AgentDeviceContextSnapshot(json = json, summary = summary)
    }

    private fun isLauncherLikePackage(packageName: String): Boolean {
        val clean = packageName.lowercase()
        return clean.contains("launcher") || clean.contains("home") || clean == "android" || clean == "com.android.systemui"
    }

    private const val MAX_LAUNCHABLE_APPS = 120
    private const val MAX_ALIASES_PER_APP = 6
}
