package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

private const val CANONICAL_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v16_text_bootstrap_gui_loop"
private const val CANONICAL_VISUAL_INTERACTION_PROTOCOL = "gui_plus_dialogue_v2_bound_turns"
private const val CANONICAL_VISUAL_MEMORY_SCHEMA = "android_visual_agent_loop_memory_v16_text_bootstrap_gui_loop"

/**
 * Keeps the active transport narrow: one runtime context, one task ledger and objective execution
 * results only. Android-local reasoning/watchdog projections never steer GUI Plus.
 */
internal fun JSONObject.compactVisualAgentPayloadForTransport(): JSONObject {
    put("agentSessionProtocol", CANONICAL_VISUAL_SESSION_PROTOCOL)
    put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
    TRANSPORT_ALIAS_KEYS.forEach(::remove)

    val actions = optJSONArray("recentAgentActions").stringValues()
    val workSurfaceActionExecuted = actions.any(String::isWorkSurfaceExecutionResult)
    put("recentAgentActions", JSONArray(actions.filter(String::isCloudRelevantVisualEvent).distinct().takeLast(10)))

    optJSONObject("deviceContext")?.apply {
        DEVICE_CONTEXT_DUPLICATE_KEYS.forEach(::remove)
    }
    optJSONObject("executionFeedback")?.apply {
        if (!workSurfaceActionExecuted) resetForFirstWorkSurfaceTurn()
    }
    optJSONObject("agentMemory")?.apply {
        put("schema", CANONICAL_VISUAL_MEMORY_SCHEMA)
        put("interactionProtocol", CANONICAL_VISUAL_INTERACTION_PROTOCOL)
        AGENT_MEMORY_DUPLICATE_KEYS.forEach(::remove)
        optJSONObject("taskMemory")?.remove("reasoningContext")
        optJSONObject("loopSignals")?.let { signals ->
            LOOP_SIGNAL_DUPLICATE_KEYS.forEach(signals::remove)
        }
    }
    return this
}

private fun JSONArray?.stringValues(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun String.isCloudRelevantVisualEvent(): Boolean {
    val value = lowercase()
    return contains(":ok:") || contains(":failed:") || contains(":retry:") ||
        value.startsWith("open_app_package_verified:") ||
        value.startsWith("open_app_package_verification_failed:") ||
        value.startsWith("visual_action_stale:") ||
        value.startsWith("visual_local_retry:") ||
        value.startsWith("finish_verification_pending:") ||
        value.startsWith("finish_candidate_rejected:") ||
        value.startsWith("finish_permit_rejected:") ||
        value.startsWith("usertakeover=")
}

private fun String.isWorkSurfaceExecutionResult(): Boolean {
    val marker = when {
        contains(":ok:") -> ":ok:"
        contains(":failed:") -> ":failed:"
        contains(":retry:") -> ":retry:"
        else -> return false
    }
    val type = substringBefore(marker).substringBefore('|').trim().lowercase()
    return type.isNotBlank() && type !in PRE_WORK_SURFACE_ACTION_TYPES
}

private fun JSONObject.resetForFirstWorkSurfaceTurn() {
    put("lastResultOk", true)
    put("lastVerification", "surface_verified")
    put("noProgressCount", 0)
    put("structuralFailureCount", 0)
    put("localVisualRetryCount", 0)
    put("sameActionCount", 0)
    put("localVisualRetryRequested", false)
    put("visualReplanRequested", false)
    put("guiPlusReplanRequested", false)
    put("routeRefreshRequested", false)
    put("verificationEvents", JSONArray())
    put("latestEvent", "work_surface_verified")
}

private val PRE_WORK_SURFACE_ACTION_TYPES = setOf(
    "open_app",
    "request_shizuku_permission",
    "set_animation_scale",
    "force_stop_app",
    "clear_app_data",
    "uninstall_app",
    "disable_app",
    "enable_app",
)

private val TRANSPORT_ALIAS_KEYS = setOf(
    "agentGoal", "recentActions", "toolResponse", "sessionId", "clientId", "message",
    "hasImage", "hasImages", "taskContract",
)

private val DEVICE_CONTEXT_DUPLICATE_KEYS = setOf(
    "runtimeExecutionContext", "surfaceContext", "deviceProfile", "appSelectionProtocol",
)

private val AGENT_MEMORY_DUPLICATE_KEYS = setOf(
    "recentActions", "runtimeExecutionContext", "surfaceContext", "deviceProfile",
    "appSelectionProtocol", "executionFeedback", "lastToolResponse", "taskContract",
)

private val LOOP_SIGNAL_DUPLICATE_KEYS = setOf(
    "postActionFeedback", "lastToolResponse", "observationId", "routeEpoch", "surfaceEpoch",
    "guiPlusEligible", "currentPackageMatchesVerifiedTarget", "currentMilestoneId",
    "completedMilestoneIds", "failedHypotheses", "blockedActions", "remainingExplorationBudget",
    "lastConfirmedPage", "semanticProgressStatus", "semanticReplanRequested", "legacyTaskContract",
)
