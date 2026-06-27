package com.yuchen.ailedger.service

import android.os.SystemClock
import org.json.JSONObject

internal object VisualLoopSupport {
    const val MAX_RECENT_ACTIONS = 14
    const val MAX_RECENT_ACTION_CHARS = 1_200
    const val MAX_INTERACTION_TEXT_CHARS = 1_000
    const val MAX_INTERACTION_ACTIONS = 12
    const val MAX_INTERACTION_IN_REQUEST = 8
    const val CLIENT_ACTION_LIMIT = 14
    const val MIN_RUNTIME_ACTIONS = 6
    const val NORMAL_HISTORY_ITEMS = 2
    const val RECOVERY_HISTORY_ITEMS = 4
    const val MAX_APP_CONTEXT_ITEMS = 160
    const val MAX_REJECTIONS = 3
    const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"

    /**
     * Converts GUI Plus normalized coordinates to physical screen pixels without interpreting the
     * declared target. Visual grounding and target existence are owned by the cloud GUI verifier;
     * Android only preserves coordinates, protocol metadata and objective execution traces.
     */
    fun materializeTap(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        val surfaceAwareStep = step.withExecutionTraceField(
            TRACE_SURFACE_MODE,
            surfaceEvidenceMode(snapshot),
        )
        if (surfaceAwareStep.type != "tap_xy") return surfaceAwareStep

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
                groundingApplied = false,
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
                    groundingApplied = false,
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
                    groundingApplied = false,
                    coordinateProtocol = VisualAgentProtocol.coordinateProtocol,
                )
            }
        }
        VisualAgentHudRuntime.notePlannedStep(materialized)
        awaitHudPointerLead()
        return materialized
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
        groundingApplied: Boolean,
        coordinateProtocol: String,
    ): CloudAgentStep {
        val fields = linkedMapOf<String, Any?>(
            TRACE_COORDINATE_PROTOCOL to coordinateProtocol,
            TRACE_MODEL_X to modelX,
            TRACE_MODEL_Y to modelY,
            TRACE_MODEL_PIXEL_X to modelPixelX,
            TRACE_MODEL_PIXEL_Y to modelPixelY,
            TRACE_MATERIALIZED_X to materializedX,
            TRACE_MATERIALIZED_Y to materializedY,
            TRACE_DISPLAY_WIDTH to displayWidth,
            TRACE_DISPLAY_HEIGHT to displayHeight,
            TRACE_GROUNDING_APPLIED to groundingApplied,
        )
        return withExecutionTraceFields(fields)
    }

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
        fields.forEach { (key, value) ->
            if (value != null) args.put(key, value)
        }
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
        // Preserve the established HUD presentation timing; this is visual presentation only and
        // does not participate in grounding, routing or semantic decisions.
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
        return buildList {
            add(signature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            step.purpose?.takeIf(String::isNotBlank)?.let { add("purpose=${it.take(72)}") }
            step.hypothesisId?.takeIf(String::isNotBlank)?.let { add("hypothesis=${it.take(72)}") }
            executionTrace?.let { add("executionTrace=${it.take(360)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
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
        val groundingApplied = args?.optBoolean(TRACE_GROUNDING_APPLIED, false) ?: false
        val boundaryAdjusted = result.message.contains("边界保护")
        val protocol = args?.optString(TRACE_COORDINATE_PROTOCOL).orEmpty().ifBlank { "unknown" }

        return buildList {
            surfaceMode?.let { add("surface=$it") }
            add("protocol=$protocol")
            if (modelX != null && modelY != null) add("modelNorm=${formatTraceCoordinate(modelX)},${formatTraceCoordinate(modelY)}")
            if (modelPixelX != null && modelPixelY != null) add("modelPx=${formatTraceCoordinate(modelPixelX)},${formatTraceCoordinate(modelPixelY)}")
            if (materializedX != null && materializedY != null) {
                add("materializedPx=${formatTraceCoordinate(materializedX)},${formatTraceCoordinate(materializedY)}")
            }
            actualPoint?.let { add("executedPx=${formatTraceCoordinate(it.first)},${formatTraceCoordinate(it.second)}") }
            add("groundingApplied=$groundingApplied")
            add("boundaryAdjusted=$boundaryAdjusted")
        }.joinToString(",")
    }

    private fun JSONObject.optNullableTraceFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key).toFloat() }.getOrNull()
            ?: optString(key).trim().toFloatOrNull()
    }

    private fun formatTraceCoordinate(value: Float): String = "%.3f".format(java.util.Locale.US, value)

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
        return recent.takeLast(runtimeBudget) + interactionBudget
    }

    fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * 3).coerceAtLeast(maxSteps + 8).coerceAtMost(120)
    }

    private val EXECUTED_POINT_PATTERN = Regex("实际落点\\s+(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
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
}
