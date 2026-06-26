package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Removes only exact legacy aliases from the transport payload. Canonical fields used by the
 * current Worker remain untouched, including the three-way observation binding, runtime context,
 * execution feedback, task memory and GUI Plus ownership gates.
 */
internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    TRANSPORT_ALIAS_KEYS.forEach { key -> remove(key) }

    optJSONObject("agentMemory")?.apply {
        // These objects are already present in canonical top-level/deviceContext locations. The
        // Worker normalizer reads those locations before rebuilding one canonical memory object.
        AGENT_MEMORY_DUPLICATE_KEYS.forEach { key -> remove(key) }
        optJSONObject("loopSignals")?.let { signals ->
            LOOP_SIGNAL_DUPLICATE_KEYS.forEach { key -> signals.remove(key) }
        }
    }
    return this
}

private val TRANSPORT_ALIAS_KEYS = setOf(
    "recentActions",       // canonical: recentAgentActions
    "toolResponse",        // canonical: lastToolResponse
    "sessionId",           // canonical: agentSessionId
    "clientId",            // canonical: deviceId
    "message",             // canonical: goal / agentGoal
    "hasImage",            // canonical: hasScreenshot
    "hasImages",           // canonical: hasScreenshot + imageCount
)

private val AGENT_MEMORY_DUPLICATE_KEYS = setOf(
    "runtimeExecutionContext", // canonical top-level runtimeExecutionContext
    "surfaceContext",          // canonical deviceContext.surfaceContext
    "deviceProfile",           // canonical top-level/deviceContext deviceProfile
    "appSelectionProtocol",    // canonical top-level/deviceContext appSelectionProtocol
)

private val LOOP_SIGNAL_DUPLICATE_KEYS = setOf(
    "postActionFeedback",      // canonical agentMemory.executionFeedback
    "lastToolResponse",        // canonical agentMemory.lastToolResponse
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
