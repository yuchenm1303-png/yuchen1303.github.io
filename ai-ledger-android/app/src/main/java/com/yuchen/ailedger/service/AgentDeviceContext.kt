package com.yuchen.ailedger.service

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.yuchen.ailedger.data.AssistantLocalMemoryRuntime
import java.security.MessageDigest
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
        val buildStartedAt = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics

        val inventoryStartedAt = SystemClock.elapsedRealtime()
        val allLaunchableApps = installedAppIndex.getLaunchableApps(forceReload = false)
        val appInventoryMs = elapsedSince(inventoryStartedAt)

        val candidateStartedAt = SystemClock.elapsedRealtime()
        val candidateApps = installedAppIndex.findCandidateApps(goal, limit = MAX_CANDIDATE_APPS)
        val candidateResolveMs = elapsedSince(candidateStartedAt)

        val hashStartedAt = SystemClock.elapsedRealtime()
        val inventoryApps = mergePriorityApps(candidateApps, allLaunchableApps, MAX_LAUNCHABLE_APPS)
        val inventoryHash = buildInventoryHash(inventoryApps)
        val inventoryHashMs = elapsedSince(hashStartedAt)

        val currentPackage = screen.currentApp.ifBlank { "unknown" }
        val localMemory = AssistantLocalMemoryRuntime.current()
        val operationMemory = AgentOperationMemory.build(
            goal = goal,
            currentPackage = currentPackage,
            localMemory = localMemory,
        )
        val contract = AgentTaskContractRuntime.current(goal)
        val targetResolver = TargetAppResolver(appContext, installedAppIndex)
        val targetResolveStartedAt = SystemClock.elapsedRealtime()
        val targetResolution = targetResolver.resolve(contract)
        val targetAppResolveMs = elapsedSince(targetResolveStartedAt)

        val shellBridge = DeviceShellBridge(appContext)
        val shellStartedAt = SystemClock.elapsedRealtime()
        val shellStatus = runCatching { shellBridge.probe() }.getOrNull()
        val shellProbeMs = elapsedSince(shellStartedAt)
        val clientPerformance = JSONObject().apply {
            put("schema", "android_agent_client_performance_v1")
            put("appInventoryMs", appInventoryMs)
            put("candidateResolveMs", candidateResolveMs)
            put("inventoryHashMs", inventoryHashMs)
            put("targetAppResolveMs", targetAppResolveMs)
            put("shellProbeMs", shellProbeMs)
        }

        val jsonBuildStartedAt = SystemClock.elapsedRealtime()
        val json = JSONObject().apply {
            put("schema", "android_device_context_v6_app_capabilities")
            put("inventory", JSONObject().apply {
                put("schema", "android_launchable_app_inventory_v2")
                put("inventoryHash", inventoryHash)
                put("installedAppCount", allLaunchableApps.size)
                put("uploadedAppCount", inventoryApps.size)
                put("truncated", allLaunchableApps.size > inventoryApps.size)
                put("generatedAt", System.currentTimeMillis())
            })
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
                put("isAssistantHost", currentPackage == appContext.packageName)
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
            put("targetAppCandidates", appsToJsonArray(candidateApps, installedAppIndex))
            put("installedApps", appsToJsonArray(inventoryApps, installedAppIndex))
            put("installedAppCount", allLaunchableApps.size)
            put("uploadedAppCount", inventoryApps.size)
            put("installedAppsTruncated", allLaunchableApps.size > inventoryApps.size)
            put("appInventoryHash", inventoryHash)
            contract?.let {
                put("taskExecutionContract", it.toJson())
                put("targetAppResolution", targetResolution.toJson())
            }
            put("availableTools", JSONArray(CloudAgentStep.supportedTypes.toList()))
            put("localUserMemory", localMemory.toJson())
            put("operationMemory", operationMemory)
            put("clientPerformance", clientPerformance)
            put("internalDeviceControl", JSONObject().apply {
                put("enabled", true)
                put("runtime", "DeviceControlRuntime")
                put("policy", "prefer_internal_tool_before_visual_computer_use_with_post_action_verification")
                put("capabilities", DeviceControlCapabilityRegistry.toJsonArray())
                put("shellToolCatalog", shellBridge.controlledToolCatalogJson())
                put("postActionVerification", JSONObject().apply {
                    put("enabled", true)
                    put("androidSettingsReadback", true)
                    put("shellReadback", true)
                    put("blockedActionSignatures", true)
                })
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
                put("云端任务合同声明所需能力；设备本地 TargetAppResolver 从真实可启动应用中解析目标 App。")
                put("open_app 必须使用 targetAppResolution.selectedApp 中已验证可启动的真实 label/packageName。")
                put("targetAppResolution.status=ambiguous 时必须请求用户选择，不得让视觉模型猜测。")
                put("targetAppResolution.status=not_found 时必须返回 need_user_help，不得编造 packageName。")
                put("open_target_app / verify_target_app 阶段不得在宿主 AI 助手页面点击、输入或滑动。")
                put("进入目标 App 并验证前台包名后，才允许 tap_xy、tap_node、scroll、swipe。")
                put("系统设置类任务必须优先参考 operationMemory.routeSkills。")
                put("同一页面同坐标/同文字点击无进展时，必须更换策略。")
                put("能由 internalDeviceControl 完成的系统任务不要转成视觉点击。")
            })
            put("privacy", JSONObject().apply {
                put("scope", "只上传当前任务所需的可启动应用名称、包名、能力标签、基础设备执行能力、用户显式保存的默认 App 偏好和任务状态；不上传联系人、文件、短信、通知历史、权限明细或后台应用列表。")
            })
        }
        clientPerformance.put("jsonBuildMs", elapsedSince(jsonBuildStartedAt))

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
            contract?.let {
                append("，任务阶段 ").append(it.phase)
                append("，目标 App 解析 ").append(targetResolution.status)
            }
            shellStatus?.let { append("，Shell ").append(if (it.available) "可用" else "不可用") }
            if (localMemory.hasNavigationMemory) append("，本地导航记忆可用")
            if (allLaunchableApps.size > inventoryApps.size) append("，已上传优先清单 ${inventoryApps.size} 个")
            append("。")
        }
        clientPerformance.put("deviceContextMs", elapsedSince(buildStartedAt))
        return AgentDeviceContextSnapshot(json = json, summary = summary)
    }

    private fun appsToJsonArray(
        apps: List<InstalledAppEntry>,
        installedAppIndex: InstalledAppIndex,
    ): JSONArray = JSONArray().apply {
        apps.forEach { app ->
            val profile = AppCapabilityRegistry.profile(app)
            put(JSONObject().apply {
                put("label", app.label)
                put("packageName", app.packageName)
                put("launchable", true)
                put("aliases", JSONArray().apply {
                    installedAppIndex.aliasesFor(app).take(MAX_ALIASES_PER_APP).forEach { put(it) }
                })
                put("capabilityProfile", profile.toJson())
            })
        }
    }

    private fun mergePriorityApps(
        candidates: List<InstalledAppEntry>,
        allApps: List<InstalledAppEntry>,
        limit: Int,
    ): List<InstalledAppEntry> = (candidates + allApps).distinctBy { it.packageName }.take(limit)

    private fun buildInventoryHash(apps: List<InstalledAppEntry>): String {
        val canonical = apps.sortedBy { it.packageName }.joinToString("\n") { app ->
            val capabilities = AppCapabilityRegistry.profile(app).capabilities.sorted().joinToString(",")
            "${app.packageName}|${app.label}|$capabilities"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private fun isLauncherLikePackage(packageName: String): Boolean {
        val clean = packageName.lowercase()
        return clean.contains("launcher") || clean.contains("home") ||
            clean == "android" || clean == "com.android.systemui"
    }

    private fun elapsedSince(startedAt: Long): Long =
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private const val MAX_LAUNCHABLE_APPS = 160
    private const val MAX_CANDIDATE_APPS = 12
    private const val MAX_ALIASES_PER_APP = 4
}
