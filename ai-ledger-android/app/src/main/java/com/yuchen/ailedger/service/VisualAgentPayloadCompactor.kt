package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_SESSION_PROTOCOL = "android_visual_agent_v15_unified_execution_permit"
private const val VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"

internal fun buildLeanVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem>,
    appContext: List<VisualAgentAppContextItem>,
    deviceId: String,
    agentSessionId: String,
    executionMode: AgentExecutionMode,
    runtimeContext: VisualAgentRuntimeContext?,
    taskMemory: VisualTaskMemory?,
): JSONObject {
    val runtime = runtimeContext ?: VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.Planning,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
    )
    val actions = recentActions.map(String::trim)
        .filter(String::isNotBlank)
        .filterNot {
            it.startsWith("visual_reasoning_context:") ||
                it.startsWith("visual_replan_requested:reason=adaptive_reasoning_depth|")
        }
        .takeLast(14)
    val workSurface = runtime.guiPlusEligible && runtime.verifiedTargetPackage.isNotBlank()
    val visual = snapshot.visual?.takeIf { it.hasImage }
    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("intent", "visual_agent_step")
        put("requestType", "visual_agent_step")
        put("agentStepRequest", true)
        put("goal", goal.trim().take(240))
        put("agentSessionId", agentSessionId.take(120))
        put("deviceId", deviceId.take(120))
        put("agentSessionProtocol", VISUAL_SESSION_PROTOCOL)
        put("interactionProtocol", VISUAL_INTERACTION_PROTOCOL)
        put(
            "executionMode",
            when (executionMode) {
                AgentExecutionMode.VisualForce -> "visual_force"
                AgentExecutionMode.ExplicitAgent -> "explicit_agent"
                AgentExecutionMode.NormalChatDeviceTool -> "normal_chat_device_tool"
            },
        )
        put("decisionOwner", "deepseek_then_gui_plus")
        put("visualDecisionOwner", if (workSurface) "gui_plus" else "deepseek")
        put("exclusiveVisualSession", workSurface)
        put("allowAgentBrain", !workSurface)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put(
            "runtimeExecutionContext",
            JSONObject().apply {
                put("surfaceState", runtime.surfaceState.wireValue)
                put("selectedTargetPackage", runtime.selectedTargetPackage)
                put("verifiedTargetPackage", runtime.verifiedTargetPackage)
                put("currentPackage", snapshot.packageName)
                put("observationId", runtime.observationId)
                put("routeEpoch", runtime.routeEpoch)
                put("surfaceEpoch", runtime.surfaceEpoch)
                put("guiPlusEligible", workSurface)
            },
        )
        put("observationId", runtime.observationId)
        put("expectedActionObservationId", runtime.observationId)
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("recentAgentActions", JSONArray(actions))
        put("executionFeedback", taskMemory.toLeanFeedback(runtime, actions))
        put("taskMemory", taskMemory?.toLeanMemoryJson() ?: JSONObject.NULL)
        put(
            "appContext",
            JSONArray().apply {
                appContext.distinctBy { it.packageName }.take(160).forEach { app ->
                    put(
                        JSONObject().apply {
                            put("appRef", app.packageName)
                            put("label", app.label)
                            put("packageName", app.packageName)
                            put("aliases", JSONArray(app.aliases.take(24)))
                            put("capabilities", JSONArray(app.capabilities.take(32)))
                        },
                    )
                }
            },
        )
        put(
            "visualHistory",
            JSONArray().apply {
                visualHistory.takeLast(4).forEach { item ->
                    put(
                        JSONObject().apply {
                            put("assistantOutput", item.assistantOutput.take(1_200))
                            put("executionResult", item.executionResult.take(240))
                        },
                    )
                }
            },
        )
        put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
        put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
        put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
        put("actionBatchMax", 1)
        put("hasScreenshot", visual != null)
        put("imageCount", if (visual != null) 1 else 0)
        visual?.let { frame ->
            put(
                "screenshot",
                JSONObject().apply {
                    put("mimeType", frame.mimeType)
                    put("base64Data", frame.base64Jpeg)
                    put("width", frame.width)
                    put("height", frame.height)
                    put("displayWidth", frame.displayWidth)
                    put("displayHeight", frame.displayHeight)
                    put("observationId", runtime.observationId)
                },
            )
        }
        put(
            "responseFormat",
            JSONObject().apply {
                put("type", "json_object")
                put("echoObservationId", true)
            },
        )
        put("client", "android-compose")
        put("clientVersion", "visual-clean-v1")
    }
}

private fun VisualTaskMemory?.toLeanFeedback(
    runtime: VisualAgentRuntimeContext,
    actions: List<String>,
) = JSONObject().apply {
    val result = actions.asReversed().firstNotNullOfOrNull { line ->
        when {
            ":ok:" in line -> true
            ":failed:" in line || ":retry:" in line -> false
            else -> null
        }
    }
    put("lastResultOk", result ?: JSONObject.NULL)
    put("latestEvent", actions.lastOrNull().orEmpty())
    put("currentMilestoneId", this@toLeanFeedback?.currentMilestoneId.orEmpty())
    put("completedMilestoneIds", JSONArray(this@toLeanFeedback?.completedMilestoneIds.orEmpty()))
    put("taskRevision", this@toLeanFeedback?.taskRevision ?: 0)
    put("taskRevisionPending", this@toLeanFeedback?.taskRevisionPending == true)
    put(
        "replanRequested",
        this@toLeanFeedback?.taskRevisionPending == true ||
            runtime.surfaceState == VisualSurfaceState.Replanning,
    )
    put("semanticDecisionOwner", "gui_plus")
    put("localSemanticDecision", false)
    put("executionLedgerOnly", true)
}

private fun VisualTaskMemory.toLeanMemoryJson() = JSONObject().apply {
    put("originalGoal", originalGoal)
    put("currentMilestoneId", currentMilestoneId)
    put("completedMilestoneIds", JSONArray(completedMilestoneIds))
    put("currentPage", currentPage?.toJson() ?: JSONObject.NULL)
    put("lastConfirmedPage", lastConfirmedPage?.toJson() ?: JSONObject.NULL)
    put("progressStatus", progressStatus)
    put("taskContract", taskContract?.toJson() ?: JSONObject.NULL)
    put("taskRevision", taskRevision)
    put("taskRevisionPending", taskRevisionPending)
    put("currentMilestoneInvalidated", currentMilestoneInvalidated)
    put("latestUserUpdate", latestUserUpdate?.toJson() ?: JSONObject.NULL)
    put(
        "userUpdateHistory",
        JSONArray().apply { userUpdateHistory.takeLast(8).forEach { put(it.toJson()) } },
    )
    put("semanticDecisionOwner", "gui_plus")
    put("localSemanticDecision", false)
    put("executionLedgerOnly", true)
}

internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    put("agentSessionProtocol", VISUAL_SESSION_PROTOCOL)
    put("interactionProtocol", VISUAL_INTERACTION_PROTOCOL)
    optJSONObject("agentMemory")?.optJSONObject("taskMemory")?.let { memory ->
        LOCAL_REASONING_KEYS.forEach(memory::remove)
        memory.put("semanticDecisionOwner", "gui_plus")
        memory.put("localSemanticDecision", false)
        memory.put("executionLedgerOnly", true)
        put("taskMemory", memory)
    }
    LEGACY_TOP_LEVEL_KEYS.forEach(::remove)
    return this
}

private val LOCAL_REASONING_KEYS = listOf(
    "reasoningContext",
    "reasoningDepth",
    "reasoningTriggers",
    "failedHypotheses",
    "blockedActions",
    "remainingExplorationBudget",
)

private val LEGACY_TOP_LEVEL_KEYS = listOf(
    "agentGoal",
    "message",
    "sessionId",
    "clientId",
    "recentActions",
    "toolResponse",
    "lastToolResponse",
    "agentMemory",
    "surfaceContext",
    "taskContract",
    "finishVerificationRequested",
    "localVisualRetryRequested",
    "visualReplanRequested",
    "guiPlusReplanRequested",
    "routeRefreshRequested",
    "invalidateCachedAgentBrainRoute",
    "hasImage",
    "hasImages",
)
