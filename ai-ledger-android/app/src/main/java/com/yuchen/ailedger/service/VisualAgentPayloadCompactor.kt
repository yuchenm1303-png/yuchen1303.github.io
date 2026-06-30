package com.yuchen.ailedger.service

import org.json.JSONObject

private const val CANONICAL_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v15_unified_execution_permit"
private const val CANONICAL_VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"

/** Final transport boundary: one task memory, one execution feedback, no local reasoning fields. */
internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    put("agentSessionProtocol", CANONICAL_VISUAL_SESSION_PROTOCOL)
    put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)

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

private val LEGACY_TOP_LEVEL_KEYS = setOf(
    "agentGoal", "message", "sessionId", "clientId", "recentActions",
    "toolResponse", "lastToolResponse", "agentMemory", "surfaceContext",
    "taskContract", "hasImage", "hasImages", "visualReplanRequested",
    "guiPlusReplanRequested", "localVisualRetryRequested", "routeRefreshRequested",
    "invalidateCachedAgentBrainRoute",
)

private val LOCAL_REASONING_KEYS = setOf(
    "reasoningContext", "reasoningDepth", "reasoningTriggers",
    "failedHypotheses", "blockedActions", "remainingExplorationBudget",
)
