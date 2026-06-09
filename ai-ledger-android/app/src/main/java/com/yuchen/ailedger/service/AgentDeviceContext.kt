package com.yuchen.ailedger.service

import android.content.Context
import android.os.Build
import com.yuchen.ailedger.data.AssistantLocalMemoryRuntime
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
        goal = AgentRuntimeController.progress.value.currentAction,
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
        val localMemory = AssistantLocalMemoryRuntime.current()
        val operationMemory = AgentOperationMemory.build(
            goal = goal,
            currentPackage = currentPackage,
            localMemory = localMemory,
        )
        val installedAppsJson = appsToJsonArray(inventoryApps, installedAppIndex)
        val candidateAppsJson = appsToJsonArray(candidateApps, installedAppIndex)
        val shellStatus = runCatching { DeviceShellBridge(appContext).probe() }.getOrNull()
        val json = JSONObject().apply {
            put("schema", "android_device_context_v4_internal_control_memory")
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
            put("localUserMemory", localMemory.toJson())
            put("operationMemory", operationMemory)
            put("internalDeviceControl", JSONObject().apply {
                put("enabled", true)
                put("runtime", "DeviceControlRuntime")
                put("policy", "prefer_internal_tool_before_visual_computer_use")
                put("capabilities", DeviceControlCapabilityRegistry.toJsonArray())
                shellStatus?.let { status ->
                    put("shell", JSONObject().apply {
                        put("available", status.available)
                        put("uidLine", status.uidLine)
                        put("adbShellLike", status.isAdbShellLike)
                        put("androidRelease", status.androidRelease)
                        put("message", status.message)
                    })
                }
            })
            put("toolRules", JSONArray().apply {
                put("打开应用必须优先使用 open_app，并优先从 targetAppCandidates 选择；targetAppCandidates 为空时再从 installedApps 选择真实 label/packageName。")
                put("不要凭常识编 packageName。目标应用不在 targetAppCandidates 或 installedApps 时，返回 need_user_help。")
                put("在桌面或启动器页面时，不要通过点击文件夹或翻页肉眼寻找 App 图标；如果目标是打开应用，直接返回 open_app。")
                put("App 内页面导航才使用 tap_xy、tap_node、scroll、swipe。")
                put("系统设置类任务必须优先参考 operationMemory.routeSkills；不要在宿主 App 内反复点击 AI助手/功能/设置底部导航。")
                put("如果 operationMemory.localUserMemory.navigation 中已有家/学校/公司/宿舍地址，导航类任务必须优先使用这些显式保存的地址；为空时不要猜。")
                put("同一页面同坐标/同文字点击无进展时，必须视为失败路线并更换策略。")
                put("能由 internalDeviceControl 直接完成的系统级任务不要转成视觉点击；高风险内部控制必须停止并请求确认或说明需要 Shizuku/ADB。")
                put("同一个动作失败或被本地拒绝后，下一轮必须避开相同路径，改用其他工具或说明无法继续。")
            })
            put("privacy", JSONObject().apply {
                put("scope", "只上传可启动应用名称、包名、基础设备执行能力、增强模式可用性、用户显式保存的导航偏好和结构化操作规则，不上传联系人、文件、权限列表、后台运行应用或大段屏幕隐私文本。")
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
            shellStatus?.let { append("，Shell ").append(if (it.available) "可用" else "不可用") }
            if (localMemory.hasNavigationMemory) append("，本地导航记忆可用")
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
