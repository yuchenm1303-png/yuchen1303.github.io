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
     * Converts a cloud-verified normalized coordinate to the last-addressable physical pixel in the
     * exact full-display frame captured for the model. Android never interprets target text or moves
     * the GUI model's chosen point to an arbitrary safety margin.
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
        val frame = visual?.let {
            VisualDisplayFrame(
                width = it.displayWidth.takeIf { value -> value > 0 } ?: it.width,
                height = it.displayHeight.takeIf { value -> value > 0 } ?: it.height,
            )
        }?.takeIf(VisualDisplayFrame::valid)

        val materialized = if (frame == null) {
            surfaceAwareStep.withTapExecutionTrace(
                modelX = modelX,
                modelY = modelY,
                modelPixelX = null,
                modelPixelY = null,
                materializedX = null,
                materializedY = null,
                displayWidth = null,
                displayHeight = null,
                imageWidth = visual?.width,
                imageHeight = visual?.height,
                pixelMappingProtocol = "unresolved",
                coordinateSpace = "normalized_screen",
            )
        } else {
            val resolution = VisualCoordinateProtocol.materializeNormalized(modelX, modelY, frame)
            val point = resolution.point
            if (!resolution.valid || point == null) {
                surfaceAwareStep.withTapExecutionTrace(
                    modelX = modelX,
                    modelY = modelY,
                    modelPixelX = null,
                    modelPixelY = null,
                    materializedX = null,
                    materializedY = null,
                    displayWidth = frame.width,
                    displayHeight = frame.height,
                    imageWidth = visual?.width,
                    imageHeight = visual?.height,
                    pixelMappingProtocol = "invalid:${resolution.reason}",
                    coordinateSpace = VisualCoordinateProtocol.coordinateSpace,
                )
            } else {
                surfaceAwareStep.copy(x = point.x, y = point.y).withTapExecutionTrace(
                    modelX = modelX,
                    modelY = modelY,
                    modelPixelX = point.x,
                    modelPixelY = point.y,
                    materializedX = point.x,
                    materializedY = point.y,
                    displayWidth = frame.width,
                    displayHeight = frame.height,
                    imageWidth = visual?.width,
                    imageHeight = visual?.height,
                    pixelMappingProtocol = VisualCoordinateProtocol.pixelMappingProtocol,
                    coordinateSpace = VisualCoordinateProtocol.coordinateSpace,
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
                put(
                    "toolArgs",
                    if (step.type == "input_text") "[输入参数已隐藏]" else step.toolArgs ?: JSONObject.NULL,
                )
            },
        )
    }

    private fun CloudAgentStep.withTapExecutionTrace(
        modelX: Float,
        modelY: Float,
        modelPixelX: Float?,
        modelPixelY: Float?,
        materializedX: Float?,
        materializedY: Float?,
        displayWidth: Int?,
        displayHeight: Int?,
        imageWidth: Int?,
        imageHeight: Int?,
        pixelMappingProtocol: String,
        coordinateSpace: String,
    ): CloudAgentStep = withExecutionTraceFields(
        linkedMapOf(
            TRACE_COORDINATE_PROTOCOL to VisualAgentProtocol.coordinateProtocol,
            TRACE_PIXEL_MAPPING_PROTOCOL to pixelMappingProtocol,
            TRACE_COORDINATE_SPACE to coordinateSpace,
            TRACE_MODEL_X to modelX,
            TRACE_MODEL_Y to modelY,
            TRACE_MODEL_PIXEL_X to modelPixelX,
            TRACE_MODEL_PIXEL_Y to modelPixelY,
            TRACE_MATERIALIZED_X to materializedX,
            TRACE_MATERIALIZED_Y to materializedY,
            TRACE_DISPLAY_WIDTH to displayWidth,
            TRACE_DISPLAY_HEIGHT to displayHeight,
            TRACE_IMAGE_WIDTH to imageWidth,
            TRACE_IMAGE_HEIGHT to imageHeight,
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
        val diagnosticSummary = if (step.type == "input_text") {
            "input_text:$status:result=${result.message.take(80)}"
        } else {
            summary
        }
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "execution_result",
            details = JSONObject().apply {
                put("stepType", step.type)
                put("signature", signature)
                put("ok", result.ok)
                put("shouldContinue", result.shouldContinue)
                put("message", result.message)
                put("summary", diagnosticSummary)
                put(
                    "toolArgs",
                    if (step.type == "input_text") "[输入参数已隐藏]" else step.toolArgs ?: JSONObject.NULL,
                )
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
        val materializedX = args?.optNullableTraceFloat(TRACE_MATERIALIZED_X)
        val materializedY = args?.optNullableTraceFloat(TRACE_MATERIALIZED_Y)
        val displayWidth = args?.optInt(TRACE_DISPLAY_WIDTH)?.takeIf { it > 0 }
        val displayHeight = args?.optInt(TRACE_DISPLAY_HEIGHT)?.takeIf { it > 0 }
        val imageWidth = args?.optInt(TRACE_IMAGE_WIDTH)?.takeIf { it > 0 }
        val imageHeight = args?.optInt(TRACE_IMAGE_HEIGHT)?.takeIf { it > 0 }
        val boundaryAdjusted = result.message.contains("边界保护")
        val protocol = args?.optString(TRACE_COORDINATE_PROTOCOL).orEmpty().ifBlank { "unknown" }
        val mapping = args?.optString(TRACE_PIXEL_MAPPING_PROTOCOL).orEmpty().ifBlank { "unknown" }
        val coordinateSpace = args?.optString(TRACE_COORDINATE_SPACE).orEmpty().ifBlank { "unknown" }
        val permitKind = args?.optString("executionPermitKind").orEmpty()

        return buildList {
            surfaceMode?.let { add("surface=$it") }
            add("protocol=$protocol")
            add("mapping=$mapping")
            add("space=$coordinateSpace")
            permitKind.takeIf(String::isNotBlank)?.let { add("permit=$it") }
            if (displayWidth != null && displayHeight != null) add("sourceFrame=${displayWidth}x$displayHeight")
            if (imageWidth != null && imageHeight != null) add("modelImage=${imageWidth}x$imageHeight")
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
    private const val TRACE_PIXEL_MAPPING_PROTOCOL = "__androidPixelMappingProtocol"
    private const val TRACE_COORDINATE_SPACE = "__androidCoordinateSpace"
    private const val TRACE_MODEL_X = "__androidModelX"
    private const val TRACE_MODEL_Y = "__androidModelY"
    private const val TRACE_MODEL_PIXEL_X = "__androidModelPixelX"
    private const val TRACE_MODEL_PIXEL_Y = "__androidModelPixelY"
    private const val TRACE_MATERIALIZED_X = "__androidMaterializedX"
    private const val TRACE_MATERIALIZED_Y = "__androidMaterializedY"
    private const val TRACE_DISPLAY_WIDTH = "__androidDisplayWidth"
    private const val TRACE_DISPLAY_HEIGHT = "__androidDisplayHeight"
    private const val TRACE_IMAGE_WIDTH = "__androidImageWidth"
    private const val TRACE_IMAGE_HEIGHT = "__androidImageHeight"
    private const val TRACE_GROUNDING_APPLIED = "__androidGroundingApplied"
    private const val TRACE_PERMIT_REJECTED = "__androidExecutionPermitRejected"
    private const val TRACE_PERMIT_REJECT_REASON = "__androidExecutionPermitRejectReason"
    private const val TRACE_REJECTED_ACTION = "__androidRejectedAction"
}
