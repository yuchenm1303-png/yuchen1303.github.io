package com.yuchen.ailedger.service

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"

/** The single visual request payload implementation. No legacy payload or cleanup pass exists. */
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
    val actions = recentActions.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot(String::isRemovedLocalControlLine)
        .map { it.take(1_200) }
        .toList()
        .takeLast(14)
    val apps = appContext.asSequence()
        .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .take(160)
        .toList()
    val inventoryHash = apps.inventoryHash()
    val workSurface = runtime.guiPlusEligible && runtime.verifiedTargetPackage.isNotBlank()
    val visual = snapshot.visual?.takeIf { it.hasImage }
    val reportedPackage = snapshot.reportedForegroundPackage.trim().ifBlank { snapshot.packageName }
    val screenPayload = snapshot.toJson(includeImage = false).apply {
        put("reportedForegroundPackage", optString("packageName"))
    }

    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("intent", "visual_agent_step")
        put("requestType", "visual_agent_step")
        put("agentStepRequest", true)
        put("goal", goal.trim())
        put("agentSessionId", agentSessionId.trim().take(120))
        put("deviceId", deviceId.trim().take(120))
        put("agentSessionProtocol", "android_visual_agent_v15_unified_execution_permit")
        put("interactionProtocol", VISUAL_INTERACTION_PROTOCOL)
        put(
            "executionMode",
            when (executionMode) {
                AgentExecutionMode.VisualForce -> "visual_force"
                AgentExecutionMode.ExplicitAgent -> "explicit_agent"
                AgentExecutionMode.NormalChatDeviceTool -> "normal_chat_device_tool"
            },
        )
        put("decisionOwner", "gui_plus_exclusive_visual")
        put("visualDecisionOwner", "gui_plus_exclusive")
        put("visualAgentDirect", true)
        put("exclusiveVisualSession", true)
        put("allowAgentBrain", false)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put("computerUseOwner", "gui_plus")
        put(
            "runtimeExecutionContext",
            JSONObject().apply {
                put("schema", "android_visual_execution_runtime_v2")
                put("surfaceState", runtime.surfaceState.wireValue)
                put("selectedTargetPackage", runtime.selectedTargetPackage)
                put("verifiedTargetPackage", runtime.verifiedTargetPackage)
                put("currentPackage", snapshot.packageName)
                put("observationId", runtime.observationId)
                put("routeEpoch", runtime.routeEpoch)
                put("surfaceEpoch", runtime.surfaceEpoch)
                put("guiPlusEligible", workSurface)
                put("targetPackageBound", runtime.verifiedTargetPackage.isNotBlank())
                put("currentPackageMatchesVerifiedTarget", snapshot.packageName == runtime.verifiedTargetPackage)
                put("decisionOwner", "gui_plus_exclusive")
                put("allowAgentBrain", false)
            },
        )
        put("observationId", runtime.observationId)
        put("expectedActionObservationId", runtime.observationId)
        put("screenSnapshot", screenPayload)
        put("recentAgentActions", JSONArray(actions))
        put("interactionHistory", actions.toInteractionHistory())
        put("executionFeedback", taskMemory.toExecutionFeedback(runtime, actions))
        put("taskMemory", taskMemory?.toExecutionLedgerJson() ?: JSONObject.NULL)
        put("appIdentityProtocol", VisualAgentProtocol.appIdentityProtocol)
        put("appInventoryHash", inventoryHash)
        put(
            "appCatalog",
            JSONObject().apply {
                put("schema", "android_visual_app_catalog_v6_gui_plus_canonical")
                put("identityProtocol", VisualAgentProtocol.appIdentityProtocol)
                put("identityField", "packageName")
                put("displayField", "label")
                put("selectionOwner", "gui_plus")
                put("inventoryHash", inventoryHash)
                put("entryCount", apps.size)
            },
        )
        put(
            "deviceContext",
            JSONObject().apply {
                put("schema", "android_visual_device_context_v2")
                put("currentPackage", snapshot.packageName)
                put("deviceId", deviceId.trim().take(120))
            },
        )
        put("appContext", JSONArray().apply { apps.forEach { put(it.toPayloadJson()) } })
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
        put("supportsAgentStepBatch", false)
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
                    put("source", frame.source)
                    put("reason", frame.reason)
                    put("observationId", runtime.observationId)
                },
            )
        }
        put(
            "responseFormat",
            JSONObject().apply {
                put("type", "json_object")
                put("includeAgentState", true)
                put("includeAgentStep", true)
                put("includeTaskContract", true)
                put("echoObservationId", true)
            },
        )
        put("client", "android-compose")
        put("clientVersion", "visual-gui-plus-exclusive-v1")
        put("now", System.currentTimeMillis())
    }
}

private fun VisualTaskMemory?.toExecutionFeedback(
    runtime: VisualAgentRuntimeContext,
    actions: List<String>,
): JSONObject = JSONObject().apply {
    val lastResult = actions.asReversed().firstNotNullOfOrNull { line ->
        when {
            ":ok:" in line -> true
            ":failed:" in line || ":retry:" in line -> false
            else -> null
        }
    }
    val userDirectivePending = actions.any {
        it.startsWith("userInstruction:[LATEST_USER_DIRECTIVE]") ||
            it.startsWith("visual_replan_requested:reason=user_instruction|")
    }
    put("schema", "android_visual_execution_feedback_v2")
    put("lastResultOk", lastResult ?: JSONObject.NULL)
    put("latestEvent", actions.lastOrNull().orEmpty())
    put("status", this@toExecutionFeedback?.progressStatus ?: "unknown")
    put("currentMilestoneId", this@toExecutionFeedback?.currentMilestoneId.orEmpty())
    put("completedMilestoneIds", JSONArray(this@toExecutionFeedback?.completedMilestoneIds.orEmpty()))
    put("taskRevision", this@toExecutionFeedback?.taskRevision ?: 0)
    put("taskRevisionPending", this@toExecutionFeedback?.taskRevisionPending == true)
    put("currentMilestoneInvalidated", this@toExecutionFeedback?.currentMilestoneInvalidated == true)
    put("userDirectivePending", userDirectivePending)
    put(
        "replanRequested",
        userDirectivePending ||
            this@toExecutionFeedback?.taskRevisionPending == true ||
            runtime.surfaceState == VisualSurfaceState.Replanning,
    )
    put("structuralRegression", runtime.surfaceState == VisualSurfaceState.Replanning)
    put("semanticDecisionOwner", "gui_plus")
    put("localSemanticDecision", false)
    put("executionLedgerOnly", true)
}

private fun VisualTaskMemory.toExecutionLedgerJson(): JSONObject = JSONObject().apply {
    put("schema", "visual_task_memory_v6_execution_ledger")
    put("originalGoal", originalGoal)
    put("currentMilestoneId", currentMilestoneId)
    put("completedMilestoneIds", JSONArray(completedMilestoneIds))
    put("currentPage", currentPage?.toJson() ?: JSONObject.NULL)
    put("confirmedFacts", JSONArray(confirmedFacts))
    put("lastConfirmedPage", lastConfirmedPage?.toJson() ?: JSONObject.NULL)
    put("progressStatus", progressStatus)
    put("replanRequested", replanRequested || taskRevisionPending)
    put("recoveryMode", recoveryMode || taskRevisionPending)
    put("legacyMode", legacyMode)
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

private fun VisualAgentAppContextItem.toPayloadJson(): JSONObject = JSONObject().apply {
    put("appRef", packageName.trim().take(120))
    put("label", label.trim().take(120))
    put("packageName", packageName.trim().take(120))
    put("identityType", "package_name")
    put("launchable", true)
    put("aliases", JSONArray(aliases.map(String::trim).filter(String::isNotBlank).distinct().take(24)))
    put(
        "capabilities",
        JSONArray(
            capabilities.map { it.trim().lowercase().replace('-', '_') }
                .filter(String::isNotBlank)
                .distinct()
                .take(32),
        ),
    )
}

private fun List<VisualAgentAppContextItem>.inventoryHash(): String {
    val canonical = sortedBy { it.packageName }.joinToString("\n") { app ->
        listOf(
            app.packageName.trim(),
            app.label.trim(),
            app.aliases.map(String::trim).filter(String::isNotBlank).sorted().joinToString(","),
            app.capabilities.map { it.trim().lowercase().replace('-', '_') }
                .filter(String::isNotBlank)
                .sorted()
                .joinToString(","),
        ).joinToString("|")
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(24)
}

private fun List<String>.toInteractionHistory(): JSONArray = JSONArray().apply {
    this@toInteractionHistory.mapNotNull { line ->
        when {
            line.startsWith("guiPlusQuestion:") -> "assistant" to line.substringAfter("guiPlusQuestion:")
            line.startsWith("userReply:") -> "user" to line.substringAfter("userReply:")
            line.startsWith("userInstruction:") -> "user" to line.substringAfter("userInstruction:")
            else -> null
        }
    }.takeLast(12).forEachIndexed { index, (role, content) ->
        put(
            JSONObject().apply {
                put("index", index)
                put("role", role)
                put("content", content.trim().take(1_000))
            },
        )
    }
}

private fun String.isRemovedLocalControlLine(): Boolean =
    startsWith("visual_reasoning_context:") ||
        startsWith("visual_replan_requested:reason=adaptive_reasoning_depth|") ||
        startsWith("visual_task_memory:") ||
        startsWith("visual_execution_ledger:") ||
        startsWith("visual_runtime_context:")
