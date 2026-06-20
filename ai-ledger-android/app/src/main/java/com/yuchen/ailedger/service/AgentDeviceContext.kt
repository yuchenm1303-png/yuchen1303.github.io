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

/**
 * Builds factual Android execution context for the legacy internal-device-tool client.
 *
 * This provider reports the current device, screen and real launchable-app capabilities only. It
 * does not infer task semantics, select a target app from goal keywords, cache visual task contracts
 * or maintain a second app-routing system beside AgentOrchestrator/VisualLoopRunner.
 */
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
        val capabilityRegistry = AppCapabilityRegistry(appContext, installedAppIndex)

        val inventoryStartedAt = SystemClock.elapsedRealtime()
        val allLaunchableApps = installedAppIndex.getLaunchableApps(forceReload = false)
            .sortedWith(
                compareBy<InstalledAppEntry> { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName },
            )
        val inventoryApps = allLaunchableApps.take(MAX_LAUNCHABLE_APPS)
        val appInventoryMs = elapsedSince(inventoryStartedAt)

        val hashStartedAt = SystemClock.elapsedRealtime()
        val inventoryHash = buildInventoryHash(allLaunchableApps, capabilityRegistry)
        val inventoryHashMs = elapsedSince(hashStartedAt)

        val currentPackage = screen.currentApp.ifBlank { "unknown" }
        val localMemory = AssistantLocalMemoryRuntime.current()
        val operationMemory = AgentOperationMemory.build(
            goal = goal,
            currentPackage = currentPackage,
            localMemory = localMemory,
        )

        val shellBridge = DeviceShellBridge(appContext)
        val shellStartedAt = SystemClock.elapsedRealtime()
        val shellStatus = runCatching { shellBridge.probe() }.getOrNull()
        val shellProbeMs = elapsedSince(shellStartedAt)

        val clientPerformance = JSONObject().apply {
            put("schema", "android_agent_client_performance_v3_capability_inventory")
            put("appInventoryMs", appInventoryMs)
            put("inventoryHashMs", inventoryHashMs)
            put("shellProbeMs", shellProbeMs)
        }

        val jsonBuildStartedAt = SystemClock.elapsedRealtime()
        val json = JSONObject().apply {
            put("schema", "android_device_context_v8_capability_inventory")
            put("semanticOwner", "cloud")
            put("localTargetAppResolutionEnabled", false)
            put("taskContractOwner", "visual_agent_only")
            put("inventory", JSONObject().apply {
                put("schema", "android_launchable_app_inventory_v4_capabilities")
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
                put("buildDisplay", Build.DISPLAY.orEmpty())
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
                put("connected", AiAgentAccessibilityService.isConnected())
                put("nodeCount", screen.nodeCount)
                put("capturedNodeCount", screen.capturedNodeCount)
                put("clickableCount", screen.clickableNodes.size)
                put("inputCount", screen.inputNodes.size)
                put("scrollableCount", screen.scrollableNodes.size)
                put("hasScreenshot", screen.hasVisualImage)
            })
            put(
                "installedApps",
                appsToJsonArray(inventoryApps, installedAppIndex, capabilityRegistry),
            )
            put("installedAppCount", allLaunchableApps.size)
            put("uploadedAppCount", inventoryApps.size)
            put("installedAppsTruncated", allLaunchableApps.size > inventoryApps.size)
            put("appInventoryHash", inventoryHash)
            put("availableTools", JSONArray(CloudAgentStep.supportedTypes.toList()))
            put("localUserMemory", localMemory.toJson())
            put("operationMemory", operationMemory)
            put("internalDeviceControl", JSONObject().apply {
                put("enabled", true)
                put("runtime", "DeviceControlRuntime")
                put("semanticOwner", "cloud")
                put("policy", "execute_supported_internal_tools_with_android_safety_and_post_action_verification")
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
                put("任务语义、目标应用和页面路线由云端决定；Android 不根据 goal 关键词推断目标 App。")
                put("open_app 必须引用 installedApps 中真实可启动且 appName/packageName 对应的应用。")
                put("应用能力来自当前设备运行时 Intent、系统元数据和真实安装清单，不使用固定手机型号。")
                put("旧内部工具链不缓存、不生成也不修改 VisualLoopRunner 的任务契约。")
                put("能由 internalDeviceControl 完成的动作必须经过 Android 安全策略与执行后验证。")
                put("需要跨应用连续视觉操作时交由正式 VisualLoopRunner，不在本链建立旁路。")
            })
            put("privacy", JSONObject().apply {
                put(
                    "scope",
                    "只上传当前设备可启动应用的名称、包名、别名、动态能力标签、基础设备执行能力和用户显式保存的本地记忆；不上传联系人、文件、短信、通知历史、权限明细或后台应用列表。",
                )
            })
            put("clientPerformance", clientPerformance)
        }
        clientPerformance.put("jsonBuildMs", elapsedSince(jsonBuildStartedAt))
        clientPerformance.put("deviceContextMs", elapsedSince(buildStartedAt))

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
            append("，上传应用 ").append(inventoryApps.size).append(" 个")
            append("，任务语义由云端负责。")
        }
        return AgentDeviceContextSnapshot(json = json, summary = summary)
    }

    private fun appsToJsonArray(
        apps: List<InstalledAppEntry>,
        installedAppIndex: InstalledAppIndex,
        capabilityRegistry: AppCapabilityRegistry,
    ): JSONArray = JSONArray().apply {
        apps.forEach { app ->
            val capabilities = capabilityRegistry.profileFor(app).capabilities.sorted()
            put(JSONObject().apply {
                put("label", app.label)
                put("packageName", app.packageName)
                put("launchable", true)
                put("aliases", JSONArray().apply {
                    installedAppIndex.aliasesFor(app)
                        .asSequence()
                        .map { alias -> alias.trim() }
                        .filter { alias -> alias.isNotBlank() }
                        .distinct()
                        .take(MAX_ALIASES_PER_APP)
                        .forEach { alias -> put(alias) }
                })
                put("capabilities", JSONArray().apply {
                    capabilities.forEach { capability -> put(capability) }
                })
                put("capabilityProfile", JSONObject().apply {
                    put("capabilities", JSONArray().apply {
                        capabilities.forEach { capability -> put(capability) }
                    })
                })
            })
        }
    }

    private fun buildInventoryHash(
        apps: List<InstalledAppEntry>,
        capabilityRegistry: AppCapabilityRegistry,
    ): String {
        val canonical = apps.sortedBy { it.packageName }.joinToString("\n") { app ->
            val capabilities = capabilityRegistry.profileFor(app).capabilities.sorted().joinToString(",")
            "${app.packageName}|${app.label}|$capabilities"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(24)
    }

    private fun isLauncherLikePackage(packageName: String): Boolean {
        val clean = packageName.lowercase(Locale.ROOT)
        return clean.contains("launcher") || clean.contains("home") ||
            clean == "android" || clean == "com.android.systemui"
    }

    private fun elapsedSince(startedAt: Long): Long =
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private const val MAX_LAUNCHABLE_APPS = 160
    private const val MAX_ALIASES_PER_APP = 8
}
