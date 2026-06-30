package com.yuchen.ailedger.service

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val CANONICAL_MAX_RECENT_ACTIONS = 14
private const val CANONICAL_MAX_RECENT_ACTION_CHARS = 1_200
private const val CANONICAL_MAX_HISTORY_ITEMS = 4
private const val CANONICAL_MAX_HISTORY_OUTPUT_CHARS = 1_200
private const val CANONICAL_MAX_HISTORY_RESULT_CHARS = 240
private const val CANONICAL_MAX_APP_CONTEXT_ITEMS = 160
private const val CANONICAL_MAX_APP_TEXT_CHARS = 120
private const val CANONICAL_MAX_APP_ALIASES = 24
private const val CANONICAL_MAX_APP_CAPABILITIES = 32
private const val CANONICAL_MAX_INTERACTION_ITEMS = 16
private const val CANONICAL_MAX_INTERACTION_CHARS = 1_200
private const val CANONICAL_SESSION_PROTOCOL = "android_visual_agent_v15_unified_execution_permit"
private const val CANONICAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"

internal fun buildCanonicalVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-test",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val cleanDeviceId = deviceId.trim().take(120).ifBlank { "android-compose-visual" }
    val cleanSessionId = agentSessionId.trim().take(120).ifBlank {
        "visual-session-${System.currentTimeMillis()}"
    }
    val runtime = runtimeContext ?: VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.Planning,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
    )
    val cleanRecentActions = recentActions
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { it.take(CANONICAL_MAX_RECENT_ACTION_CHARS) }
        .toList()
        .takeLast(CANONICAL_MAX_RECENT_ACTIONS)
    val interactionHistory = buildCanonicalInteractionHistory(cleanRecentActions)
    val canonicalApps = buildCanonicalTransportApps(appContext)
    val appInventoryHash = buildCanonicalAppInventoryHash(canonicalApps)
    val visual = snapshot.visual?.takeIf { it.hasImage }
    val guiPlusEligible = runtime.guiPlusEligible && runtime.verifiedTargetPackage.isNotBlank()
    val memoryJson = taskMemory?.toCanonicalExecutionJson()
    val runtimeJson = JSONObject().apply {
        put("schema", "android_visual_execution_runtime_v2")
        put("surfaceState", runtime.surfaceState.wireValue)
        put("selectedTargetPackage", runtime.selectedTargetPackage)
        put("verifiedTargetPackage", runtime.verifiedTargetPackage)
        put("currentPackage", snapshot.packageName)
        put("observationId", runtime.observationId)
        put("routeEpoch", runtime.routeEpoch)
        put("surfaceEpoch", runtime.surfaceEpoch)
        put("guiPlusEligible", guiPlusEligible)
        put("targetPackageBound", runtime.verifiedTargetPackage.isNotBlank())
        put("currentPackageMatchesVerifiedTarget", snapshot.packageName == runtime.verifiedTargetPackage)
    }
    val executionFeedback = JSONObject().apply {
        put("schema", "android_visual_execution_feedback_v2")
        put("status", taskMemory?.progressStatus ?: "unknown")
        put("currentMilestoneId", taskMemory?.currentMilestoneId.orEmpty())
        put("completedMilestoneIds", JSONArray(taskMemory?.completedMilestoneIds.orEmpty()))
        put("currentFrameId", taskMemory?.currentPage?.id.orEmpty())
        put("lastVerifiedFrameId", taskMemory?.lastConfirmedPage?.id.orEmpty())
        put("taskRevision", taskMemory?.taskRevision ?: 0)
        put("taskRevisionPending", taskMemory?.taskRevisionPending == true)
        put("currentMilestoneInvalidated", taskMemory?.currentMilestoneInvalidated == true)
        put("replanRequested", taskMemory?.replanRequested == true || runtime.surfaceState == VisualSurfaceState.Replanning)
        put("structuralRegression", runtime.surfaceState == VisualSurfaceState.Replanning)
        put("latestUserUpdateRevision", taskMemory?.latestUserUpdate?.revision ?: 0)
        put("latestUserUpdateKind", taskMemory?.latestUserUpdate?.kind?.wireValue.orEmpty())
        put("semanticDecisionOwner", "gui_plus")
        put("localSemanticDecision", false)
        put("executionLedgerOnly", true)
    }
    val appCatalog = JSONObject().apply {
        put("schema", "android_visual_app_catalog_v5_canonical")
        put("identityProtocol", VisualAgentProtocol.appIdentityProtocol)
        put("identityField", "packageName")
        put("displayField", "label")
        put("selectionOwner", "deepseek")
        put("validationOwner", "android_package_identity_and_safety_only")
        put("inventoryHash", appInventoryHash)
        put("entryCount", canonicalApps.size)
    }

    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("intent", "visual_agent_step")
        put("requestType", "visual_agent_step")
        put("agentStepRequest", true)
        put("goal", cleanGoal)
        put("agentSessionId", cleanSessionId)
        put("deviceId", cleanDeviceId)
        put("agentSessionProtocol", CANONICAL_SESSION_PROTOCOL)
        put("interactionProtocol", CANONICAL_INTERACTION_PROTOCOL)
        put("executionMode", executionMode.canonicalWireValue())
        put("decisionOwner", "deepseek_then_gui_plus")
        put("visualDecisionOwner", if (guiPlusEligible) "gui_plus" else "deepseek")
        put("exclusiveVisualSession", guiPlusEligible)
        put("allowAgentBrain", !guiPlusEligible)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put("runtimeExecutionContext", runtimeJson)
        put("observationId", runtime.observationId)
        put("expectedActionObservationId", runtime.observationId)
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("recentAgentActions", JSONArray(cleanRecentActions))
        put("interactionHistory", interactionHistory)
        put("executionFeedback", executionFeedback)
        put("taskMemory", memoryJson ?: JSONObject.NULL)
        put("appCatalog", appCatalog)
        put("appInventoryHash", appInventoryHash)
        put("appContext", JSONArray().apply {
            canonicalApps.forEach { app -> put(app.toJson()) }
        })
        put("deviceProfile", deviceProfile?.toCanonicalVisualAgentJson() ?: JSONObject.NULL)
        put("visualHistory", JSONArray().apply {
            visualHistory.takeLast(CANONICAL_MAX_HISTORY_ITEMS).forEach { item ->
                if (item.assistantOutput.isNotBlank() || item.executionResult.isNotBlank()) {
                    put(JSONObject().apply {
                        put("assistantOutput", item.assistantOutput.take(CANONICAL_MAX_HISTORY_OUTPUT_CHARS))
                        put("executionResult", item.executionResult.take(CANONICAL_MAX_HISTORY_RESULT_CHARS))
                    })
                }
            }
        })
        put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
        put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
        put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
        put("supportsAgentStepBatch", false)
        put("actionBatchMax", 1)
        put("hasScreenshot", visual != null)
        put("imageCount", if (visual != null) 1 else 0)
        visual?.let { item ->
            put("screenshot", JSONObject().apply {
                put("mimeType", item.mimeType)
                put("base64Data", item.base64Jpeg)
                put("width", item.width)
                put("height", item.height)
                put("displayWidth", item.displayWidth)
                put("displayHeight", item.displayHeight)
                put("source", item.source)
                put("reason", item.reason)
                put("observationId", runtime.observationId)
            })
        }
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeTaskContract", true)
            put("echoObservationId", true)
        })
        put("client", "android-compose")
        put("clientVersion", "visual-clean-v1")
        put("now", System.currentTimeMillis())
    }
}
