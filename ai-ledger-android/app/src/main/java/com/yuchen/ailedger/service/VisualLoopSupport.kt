package com.yuchen.ailedger.service

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

internal object VisualLoopSupport {
    const val MAX_RECENT_ACTIONS = 14
    const val MAX_RECENT_ACTION_CHARS = 1_200
    const val MAX_INTERACTION_TEXT_CHARS = 1_000
    const val MAX_INTERACTION_ACTIONS = 12
    const val MAX_INTERACTION_IN_REQUEST = 8
    const val CLIENT_ACTION_LIMIT = 14
    const val MIN_RUNTIME_ACTIONS = 6
    const val NORMAL_HISTORY_ITEMS = 4
    const val RECOVERY_HISTORY_ITEMS = 4
    const val MAX_APP_CONTEXT_ITEMS = 160
    const val MAX_REJECTIONS = 3
    const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"

    /**
     * Converts a cloud-verified normalized coordinate to physical screen pixels.
     * Android never interprets target text or changes the GUI model's chosen point.
     */
    fun materializeTap(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        if (step.type != "tap_xy") {
            val traced = step.withExecutionTraceField(
                TRACE_SURFACE_MODE,
                surfaceEvidenceMode(snapshot),
            )
            recordPlannedAction(traced, snapshot, "non_coordinate_action")
            return traced
        }

        // Validate the original cloud response before Android adds local trace metadata. This keeps
        // missing/invalid permit diagnostics precise without weakening the execution guard.
        val permit = VisualExecutionPermitPolicy.validateTap(step)
        val surfaceAwareStep = step.withExecutionTraceField(
            TRACE_SURFACE_MODE,
            surfaceEvidenceMode(snapshot),
        )
        if (!permit.valid) {
            val rejected = surfaceAwareStep
                .withExecutionTraceFields(
                    linkedMapOf(
                        TRACE_PERMIT_REJECTED to true,
                        TRACE_PERMIT_REJECT_REASON to permit.reason,
                        TRACE_REJECTED_ACTION to "tap_xy",
                    ),
                )
                .copy(
                    type = "wait",
                    x = null,
                    y = null,
                    durationMs = MISSING_PERMIT_REOBSERVE_MS,
                    targetText = "重新观察",
                    reason = "Verified GUI execution permit is invalid (${permit.reason}); the coordinate was not executed.",
                )
            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                type = "tap_permit_rejected",
                details = JSONObject().apply {
                    put("originalType", step.type)
                    put("reason", permit.reason)
                    put("surfaceMode", surfaceEvidenceMode(snapshot))
                    put("packageName", snapshot.packageName)
                },
            )
            return rejected
        }

        val modelX = surfaceAwareStep.x ?: return surfaceAwareStep
        val modelY = surfaceAwareStep.y ?: return surfaceAwareStep
        val visual = snapshot.visual
        val materialized = if (visual == null) {
            surfaceAwareStep.withTapExecutionTrace(
                modelX = modelX,
                modelY = modelY,
                modelPixelX = null,
                modelPixelY = null,
                materializedX = modelX,
                materializedY = modelY,
                displayWidth = null,
                displayHeight = null,
                coordinateProtocol = "unresolved",
            )
        } else {
            val width = visual.displayWidth.takeIf { it > 0 } ?: visual.width.takeIf { it > 0 }
            val height = visual.displayHeight.takeIf { it > 0 } ?: visual.height.takeIf { it > 0 }
            if (width == null || height == null) {
                surfaceAwareStep.withTapExecutionTrace(
                    modelX = modelX,
                    modelY = modelY,
                    modelPixelX = null,
                    modelPixelY = null,
                    materializedX = modelX,
                    materializedY = modelY,
                    displayWidth = null,
                    displayHeight = null,
                    coordinateProtocol = "unresolved",
                )
            } else {
                val pixelX = (modelX * width).coerceIn(0f, width.toFloat())
                val pixelY = (modelY * height).coerceIn(0f, height.toFloat())
                surfaceAwareStep.copy(x = pixelX, y = pixelY).withTapExecutionTrace(
                    modelX = modelX,
                    modelY = modelY,
                    modelPixelX = pixelX,
                    modelPixelY = pixelY,
                    materializedX = pixelX,
                    materializedY = pixelY,
                    displayWidth = width,
                    displayHeight = height,
                    coordinateProtocol = VisualAgentProtocol.coordinateProtocol,
                )
            }
        }
        recordPlannedAction(materialized, snapshot, "tap_materialized")
        VisualAgentHudRuntime.notePlannedStep(materialized)
        awaitHudPointerLead()
        return materialized
    }

    private fun recordPlannedAction(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        stage: String,
    ) {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "planned_action",
            details = JSONObject().apply {
                put("stage", stage)
                put("type", step.type)
                put("targetNodeId", step.targetNodeId ?: JSONObject.NULL)
                put("targetText", step.targetText ?: JSONObject.NULL)
                put("text", if (step.type == "input_text") "[输入内容已隐藏]" else step.text ?: JSONObject.NULL)
                put("direction", step.direction ?: JSONObject.NULL)
                put("reason", step.reason ?: JSONObject.NULL)
                put("appName", step.appName ?: JSONObject.NULL)
                put("packageName", step.packageName ?: JSONObject.NULL)
                put("x", step.x ?: JSONObject.NULL)
                put("y", step.y ?: JSONObject.NULL)
                put("confidence", step.confidence ?: JSONObject.NULL)
                put("hypothesisId", step.hypothesisId)
                put("currentPackage", snapshot.packageName)
                put("toolArgs", step.toolArgs ?: JSONObject.NULL)
            },
        )
    }

    private fun CloudAgentStep.withTapExecutionTrace(
        modelX: Float,
        modelY: Float,
        modelPixelX: Float?,
        modelPixelY: Float?,
        materializedX: Float,
        materializedY: Float,
        displayWidth: Int?,
        displayHeight: Int?,
        coordinateProtocol: String,
    ): CloudAgentStep = withExecutionTraceFields(
        linkedMapOf(
            TRACE_COORDINATE_PROTOCOL to coordinateProtocol,
            TRACE_MODEL_X to modelX,
            TRACE_MODEL_Y to modelY,
            TRACE_MODEL_PIXEL_X to modelPixelX,
            TRACE_MODEL_PIXEL_Y to modelPixelY,
            TRACE_MATERIALIZED_X to materializedX,
            TRACE_MATERIALIZED_Y to materializedY,
            TRACE_DISPLAY_WIDTH to displayWidth,
            TRACE_DISPLAY_HEIGHT to displayHeight,
            TRACE_GROUNDING_APPLIED to false,
        ),
    )

    private fun CloudAgentStep.withExecutionTraceField(key: String, value: Any?): CloudAgentStep =
        withExecutionTraceFields(linkedMapOf(key to value))

    private fun CloudAgentStep.withExecutionTraceFields(fields: Map<String, Any?>): CloudAgentStep {
        val args = JSONObject()
        toolArgs?.let { source ->
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                args.put(key, source.opt(key))
            }
        }
        fields.forEach { (key, value) -> if (value != null) args.put(key, value) }
        return copy(toolArgs = args)
    }

    private fun surfaceEvidenceMode(snapshot: AgentScreenSnapshot): String {
        val hasNodeEvidence = snapshot.clickableNodes.isNotEmpty() ||
            snapshot.inputNodes.isNotEmpty() ||
            snapshot.scrollableNodes.isNotEmpty() ||
            snapshot.allNodes.any { it.clickable || it.editable || it.scrollable }
        val hasVisualEvidence = snapshot.visual?.hasImage == true
        return when {
            hasVisualEvidence && hasNodeEvidence -> "hybrid"
            hasVisualEvidence -> "visual_only"
            hasNodeEvidence -> "node_grounded"
            else -> "package_only"
        }
    }

    private fun awaitHudPointerLead() {
        SystemClock.sleep(HUD_POINTER_LEAD_MS)
    }

    fun requiresFreshObservation(step: CloudAgentStep): Boolean =
        step.type !in CloudAgentStep.deviceToolTypes &&
            step.type !in setOf("open_app", "wait", "need_user_help", "finish")

    fun requiresAccessibility(step: CloudAgentStep): Boolean =
        step.type == "open_app" ||
            (step.type !in CloudAgentStep.deviceToolTypes && step.type !in setOf("need_user_help", "finish"))

    fun validationFeedback(
        step: CloudAgentStep,
        validation: VisualActionValidation,
        runtime: VisualAgentRuntimeContext,
    ): String {
        val prefix = if (validation.failureClass == VisualFailureClass.StructuralRoute) {
            "visual_action_rejected"
        } else {
            "visual_action_retry"
        }
        return buildString {
            append(prefix).append(":type=").append(step.type)
            append("|failureClass=").append(validation.failureClass.wireValue)
            append("|surfaceState=").append(runtime.surfaceState.wireValue)
            append("|observationId=").append(runtime.observationId)
            append("|reason=").append(validation.message.take(260))
            append("|replanRequired=").append(validation.failureClass == VisualFailureClass.StructuralRoute)
        }.take(MAX_RECENT_ACTION_CHARS)
    }

    fun resultSummary(
        step: CloudAgentStep,
        signature: String,
        result: AgentExecutionResult,
    ): String {
        val status = when {
            result.ok -> "ok"
            step.type == "open_app" -> "failed"
            else -> "retry"
        }
        val target = step.targetText?.takeIf(String::isNotBlank)
            ?: step.appName?.takeIf(String::isNotBlank)
            ?: step.packageName?.takeIf(String::isNotBlank)
            ?: step.text?.take(32)?.takeIf(String::isNotBlank)
        val executionTrace = buildExecutionTrace(step, result)
        val summary = buildList {
            add(signature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            executionTrace?.let { add("executionTrace=${it.take(360)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "execution_result",
            details = JSONObject().apply {
                put("stepType", step.type)
                put("signature", signature)
                put("ok", result.ok)
                put("shouldContinue", result.shouldContinue)
                put("message", result.message)
                put("summary", summary)
                put("toolArgs", step.toolArgs ?: JSONObject.NULL)
            },
        )
        return summary
    }

    private fun buildExecutionTrace(step: CloudAgentStep, result: AgentExecutionResult): String? {
        val args = step.toolArgs
        val surfaceMode = args?.optString(TRACE_SURFACE_MODE).orEmpty().takeIf(String::isNotBlank)
        if (step.type != "tap_xy") return surfaceMode?.let { "surface=$it" }

        val actualPoint = EXECUTED_POINT_PATTERN.find(result.message)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull()
            val y = match.groupValues[2].toFloatOrNull()
            if (x != null && y != null) x to y else null
        }
        val modelX = args?.optNullableTraceFloat(TRACE_MODEL_X)
        val modelY = args?.optNullableTraceFloat(TRACE_MODEL_Y)
        val modelPixelX = args?.optNullableTraceFloat(TRACE_MODEL_PIXEL_X)
        val modelPixelY = args?.optNullableTraceFloat(TRACE_MODEL_PIXEL_Y)
        val materializedX = args?.optNullableTraceFloat(TRACE_MATERIALIZED_X) ?: step.x
        val materializedY = args?.optNullableTraceFloat(TRACE_MATERIALIZED_Y) ?: step.y
        val boundaryAdjusted = result.message.contains("边界保护")
        val protocol = args?.optString(TRACE_COORDINATE_PROTOCOL).orEmpty().ifBlank { "unknown" }
        val permitKind = args?.optString("executionPermitKind").orEmpty()

        return buildList {
            surfaceMode?.let { add("surface=$it") }
            add("protocol=$protocol")
            permitKind.takeIf(String::isNotBlank)?.let { add("permit=$it") }
            if (modelX != null && modelY != null) add("modelNorm=${formatTraceCoordinate(modelX)},${formatTraceCoordinate(modelY)}")
            if (modelPixelX != null && modelPixelY != null) add("modelPx=${formatTraceCoordinate(modelPixelX)},${formatTraceCoordinate(modelPixelY)}")
            if (materializedX != null && materializedY != null) {
                add("materializedPx=${formatTraceCoordinate(materializedX)},${formatTraceCoordinate(materializedY)}")
            }
            actualPoint?.let { add("executedPx=${formatTraceCoordinate(it.first)},${formatTraceCoordinate(it.second)}") }
            add("groundingApplied=false")
            add("boundaryAdjusted=$boundaryAdjusted")
        }.joinToString(",")
    }

    private fun JSONObject.optNullableTraceFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key).toFloat() }.getOrNull()
            ?: optString(key).trim().toFloatOrNull()
    }

    private fun formatTraceCoordinate(value: Float): String =
        "%.3f".format(java.util.Locale.US, value)

    fun appendRecent(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_RECENT_ACTION_CHARS).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_RECENT_ACTIONS) actions.removeAt(0)
    }

    fun appendInteraction(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_INTERACTION_TEXT_CHARS + 80).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_INTERACTION_ACTIONS) actions.removeAt(0)
    }

    fun requestActions(recent: List<String>, interactions: List<String>): List<String> {
        val mergedInteractions = interactions + AgentTakeoverDialogueBridge.interactionActions()
        val interactionBudget = mergedInteractions.takeLast(MAX_INTERACTION_IN_REQUEST)
        val runtimeBudget = (CLIENT_ACTION_LIMIT - interactionBudget.size).coerceAtLeast(MIN_RUNTIME_ACTIONS)
        val request = recent.takeLast(runtimeBudget) + interactionBudget
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "model_request_memory",
            details = JSONObject().apply {
                put("recentActionsBeforeBudget", JSONArray(recent))
                put("interactionActionsBeforeBudget", JSONArray(mergedInteractions))
                put("runtimeBudget", runtimeBudget)
                put("interactionBudget", interactionBudget.size)
                put("actualRequestActions", JSONArray(request))
                put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
                put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
            },
        )
        return request
    }

    fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * 3).coerceAtLeast(maxSteps + 8).coerceAtMost(120)
    }

    private val EXECUTED_POINT_PATTERN = Regex("实际落点\\s+(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
    private const val MISSING_PERMIT_REOBSERVE_MS = 220L
    private const val HUD_POINTER_LEAD_MS = 240L
    private const val TRACE_SURFACE_MODE = "__androidVisualSurfaceMode"
    private const val TRACE_COORDINATE_PROTOCOL = "__androidCoordinateProtocol"
    private const val TRACE_MODEL_X = "__androidModelX"
    private const val TRACE_MODEL_Y = "__androidModelY"
    private const val TRACE_MODEL_PIXEL_X = "__androidModelPixelX"
    private const val TRACE_MODEL_PIXEL_Y = "__androidModelPixelY"
    private const val TRACE_MATERIALIZED_X = "__androidMaterializedX"
    private const val TRACE_MATERIALIZED_Y = "__androidMaterializedY"
    private const val TRACE_DISPLAY_WIDTH = "__androidDisplayWidth"
    private const val TRACE_DISPLAY_HEIGHT = "__androidDisplayHeight"
    private const val TRACE_GROUNDING_APPLIED = "__androidGroundingApplied"
    private const val TRACE_PERMIT_REJECTED = "__androidExecutionPermitRejected"
    private const val TRACE_PERMIT_REJECT_REASON = "__androidExecutionPermitRejectReason"
    private const val TRACE_REJECTED_ACTION = "__androidRejectedAction"
}
