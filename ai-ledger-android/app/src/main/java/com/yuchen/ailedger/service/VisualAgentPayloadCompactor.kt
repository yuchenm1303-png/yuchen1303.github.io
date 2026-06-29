package com.yuchen.ailedger.service

import org.json.JSONObject

private const val CANONICAL_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v15_unified_execution_permit"
private const val CANONICAL_VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"
private const val CANONICAL_VISUAL_MEMORY_SCHEMA = "android_visual_agent_loop_memory_v15_unified_execution_permit"

/**
 * Produces the single canonical transport contract consumed by the current Worker.
 *
 * The builder may still expose legacy aliases to old tests and synchronous callers, but the active
 * visual loop sends one copy of each state object. Screenshot binding, execution feedback, task
 * memory and GUI Plus ownership remain intact.
 */
internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    put("agentSessionProtocol", CANONICAL_VISUAL_SESSION_PROTOCOL)
    put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
    TRANSPORT_ALIAS_KEYS.forEach(::remove)

    optJSONObject("agentMemory")?.apply {
        put("schema", CANONICAL_VISUAL_MEMORY_SCHEMA)
        put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
        // Canonical copies live at the top level or inside taskMemory. Do not serialize the same
        // state graph multiple times into every visual-model request.
        AGENT_MEMORY_DUPLICATE_KEYS.forEach(::remove)
        optJSONObject("loopSignals")?.let { signals ->
            LOOP_SIGNAL_DUPLICATE_KEYS.forEach(signals::remove)
        }
    }
    return this
}

private val TRANSPORT_ALIAS_KEYS = setOf(
    "agentGoal",           // canonical: goal
    "recentActions",       // canonical: recentAgentActions
    "toolResponse",        // canonical: lastToolResponse
    "sessionId",           // canonical: agentSessionId
    "clientId",            // canonical: deviceId
    "message",             // canonical: goal
    "hasImage",            // canonical: hasScreenshot
    "hasImages",           // canonical: hasScreenshot + imageCount
    "taskContract",        // canonical: agentMemory.taskMemory.taskContract
)

private val AGENT_MEMORY_DUPLICATE_KEYS = setOf(
    "runtimeExecutionContext", // canonical top-level runtimeExecutionContext
    "surfaceContext",          // canonical top-level surfaceContext
    "deviceProfile",           // canonical top-level/deviceContext deviceProfile
    "appSelectionProtocol",    // canonical top-level appSelectionProtocol
    "executionFeedback",       // canonical top-level executionFeedback
    "lastToolResponse",        // canonical top-level lastToolResponse
    "taskContract",            // canonical agentMemory.taskMemory.taskContract
)

private val LOOP_SIGNAL_DUPLICATE_KEYS = setOf(
    "postActionFeedback",      // canonical top-level executionFeedback
    "lastToolResponse",        // canonical top-level lastToolResponse
    "currentMilestoneId",      // canonical agentMemory.taskMemory
    "completedMilestoneIds",
    "failedHypotheses",
    "blockedActions",
    "remainingExplorationBudget",
    "lastConfirmedPage",
    "semanticProgressStatus",
    "semanticReplanRequested",
    "legacyTaskContract",
)
