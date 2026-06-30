package com.yuchen.ailedger.service

import org.json.JSONObject

private const val CANONICAL_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v16_text_bootstrap_gui_loop"
private const val CANONICAL_VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"
private const val CANONICAL_VISUAL_MEMORY_SCHEMA = "android_visual_agent_loop_memory_v16_text_bootstrap_gui_loop"

/**
 * Produces the single canonical transport contract consumed by the current Worker.
 *
 * The builder still exposes compatibility aliases for old synchronous callers and tests. The active
 * visual loop sends only one copy of each state object while preserving screenshot binding,
 * execution feedback, task memory and GUI Plus ownership.
 */
internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    put("agentSessionProtocol", CANONICAL_VISUAL_SESSION_PROTOCOL)
    put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
    TRANSPORT_ALIAS_KEYS.forEach(::remove)

    optJSONObject("agentMemory")?.apply {
        put("schema", CANONICAL_VISUAL_MEMORY_SCHEMA)
        put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
        AGENT_MEMORY_DUPLICATE_KEYS.forEach(::remove)
        optJSONObject("loopSignals")?.let { signals ->
            LOOP_SIGNAL_DUPLICATE_KEYS.forEach(signals::remove)
        }
    }
    return this
}

private val TRANSPORT_ALIAS_KEYS = setOf(
    "agentGoal",
    "recentActions",
    "toolResponse",
    "sessionId",
    "clientId",
    "message",
    "hasImage",
    "hasImages",
    "taskContract",
)

private val AGENT_MEMORY_DUPLICATE_KEYS = setOf(
    "runtimeExecutionContext",
    "surfaceContext",
    "deviceProfile",
    "appSelectionProtocol",
    "executionFeedback",
    "lastToolResponse",
    "taskContract",
)

private val LOOP_SIGNAL_DUPLICATE_KEYS = setOf(
    "postActionFeedback",
    "lastToolResponse",
    "currentMilestoneId",
    "completedMilestoneIds",
    "failedHypotheses",
    "blockedActions",
    "remainingExplorationBudget",
    "lastConfirmedPage",
    "semanticProgressStatus",
    "semanticReplanRequested",
    "legacyTaskContract",
)
