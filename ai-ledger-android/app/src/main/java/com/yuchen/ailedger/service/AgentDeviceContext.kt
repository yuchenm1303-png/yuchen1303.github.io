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
        installedAppIndex: InstalledAppIndex,
    ): AgentDeviceContextSnapshot = build(
        context = context,
        screen = screen,
        goal = "",
        installedAppIndex = installedAppIndex,
    )

    fun build(
        context: Context,
        screen: AgentScreenSnapshot,
        goal: String = "",
        installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
    ): AgentDeviceContextSnapshot {
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val allLaunchableApps = installedAppIndex.getLaunchableApps(forceReload = false)
        val candidateApps = installedAppIndex.findCandidateApps(goal, limit = MAX_CANDIDATE_APPS)
        val inventoryApps = mergePriorityApps(candidateApps, allLaunchableApps, MAX_LAUNCHABLE_APPS)
        val currentPackage = screen.currentApp.ifBlank { "unknown" }
        val installedAppsJson = appsToJsonArray(inventoryApps, installedAppIndex)
        val candidateAppsJson = appsToJsonArray(candidateApps, installedAppIndex)
        val json = JSONObject().apply {
            put("schema", "android_device_context_v2")
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
            put("targetAppCandidates", candidateAppsJson)
            put("installedApps", installedAppsJson)
            put("installedAppCount", allLaunchableApps.size)
            put("uploadedAppCount", inventoryApps.size)
            put("installedAppsTruncated", allLaunchableApps.size > inventoryApps.size)
            put("availableTools", JSONArray(CloudAgentStep.supportedTypes.toList()))
            put("toolRules", JSONArray().apply {
                put("打开应用必须优先使用 open_app，并优先从 targetAppCandidates 选择；targetAppCandidates 为空时再从 installedApps 选择真实 label/packageName。")
                put("不要凭常识编 packageName。目标应用不在 targetAppCandidates 或 installedApps 时，返回 need_user_help。")
                put("在桌面或启动器页面时，不要通过点击文件夹或翻页肉眼寻找 App 图标；如果目标是打开应用，直接返回 open_app。")
                put("App 内页面导航才使用 tap_xy、tap_node、scroll、swipe。")
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
            append("，可启动应用 ").append(allLaunchableApps.size).append(" 个")
            append("，目标候选 ").append(candidateApps.size).append(" 个")
            if (allLaunchableApps.size > inventoryApps.size) append("，已上传优先清单 ${inventoryApps.size} 个")
            append("。")
        }
        return AgentDeviceContextSnapshot(json = json, summary = summary)
    }

    private fun appsToJsonArray(apps: List<InstalledAppEntry>, installedAppIndex: InstalledAppIndex): JSONArray {
        return JSONArray().apply {
            apps.forEach { app ->
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
    }

    private fun mergePriorityApps(candidates: List<InstalledAppEntry>, allApps: List<InstalledAppEntry>, limit: Int): List<InstalledAppEntry> {
        return (candidates + allApps).distinctBy { it.packageName }.take(limit)
    }

    private fun isLauncherLikePackage(packageName: String): Boolean {
        val clean = packageName.lowercase()
        return clean.contains("launcher") || clean.contains("home") || clean == "android" || clean == "com.android.systemui"
    }

    private const val MAX_LAUNCHABLE_APPS = 160
    private const val MAX_CANDIDATE_APPS = 12
    private const val MAX_ALIASES_PER_APP = 4
}
