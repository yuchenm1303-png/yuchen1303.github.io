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
     * Converts the exact normalized point selected by GUI Plus into the last-addressable physical
     * pixel of the full-display screenshot sent to the model. Android validates only coordinate/frame
     * integrity here; it never re-grounds, moves or semantically vetoes the visual model's point.
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

        val surfaceAwareStep = step.withExecutionTraceFields(
            linkedMapOf(
                TRACE_SURFACE_MODE to surfaceEvidenceMode(snapshot),
                TRACE_VISUAL_AUTHORITY to step.argString("visualCoordinateAuthority")
                    .orEmpty().ifBlank { "gui_plus_screenshot" },
                TRACE_SECONDARY_VERIFIER_REQUIRED to false,
            ),
        )
        val modelX = surfaceAwareStep.x ?: return rejectCoordinateMaterialization(
            step = surfaceAwareStep,
            snapshot = snapshot,
            modelX = null,
            modelY = surfaceAwareStep.y,
            reason = "missing_model_x",
        )
        val modelY = surfaceAwareStep.y ?: return rejectCoordinateMaterialization(
            step = surfaceAwareStep,
            snapshot = snapshot,
            modelX = modelX,
            modelY = null,
            reason = "missing_model_y",
        )
        if (!modelX.isFinite() || modelX !in 0f..1f) {
            return rejectCoordinateMaterialization(
                step = surfaceAwareStep,
                snapshot = snapshot,
                modelX = modelX,
                modelY = modelY,
                reason = "model_x_not_normalized",
            )
        }
        if (!modelY.isFinite() || modelY !in 0f..1f) {
            return rejectCoordinateMaterialization(
                step = surfaceAwareStep,
                snapshot = snapshot,
                modelX = modelX,
                modelY = modelY,
                reason = "model_y_not_normalized",
            )
        }

        val visual = snapshot.visual
        if (visual?.hasImage != true) {
            return rejectCoordinateMaterialization(
                step = surfaceAwareStep,
                snapshot = snapshot,
                modelX = modelX,
                modelY = modelY,
                reason = "missing_visual_frame",
            )
        }
        if (visual.displayWidth <= 0 || visual.displayHeight <= 0) {
            return rejectCoordinateMaterialization(
                step = surfaceAwareStep,
                snapshot = snapshot,
                modelX = modelX,
                modelY = modelY,
                reason = "missing_source_display_frame",
            )
        }

        val frame = VisualDisplayFrame(
            width = visual.displayWidth,
            height = visual.displayHeight,
        )
        val resolution = VisualCoordinateProtocol.materializeNormalized(modelX, modelY, frame)
        val point = resolution.point
        if (!resolution.valid || point == null) {
            return rejectCoordinateMaterialization(
                step = surfaceAwareStep,
                snapshot = snapshot,
                modelX = modelX,
                modelY = modelY,
                reason = "coordinate_materialization_${resolution.reason}",
            )
        }

        val materialized = surfaceAwareStep.copy(x = point.x, y = point.y).withTapExecutionTrace(
            modelX = modelX,
            modelY = modelY,
            modelPixelX = point.x,
            modelPixelY = point.y,
            materializedX = point.x,
            materializedY = point.y,
            displayWidth = frame.width,
            displayHeight = frame.height,
            imageWidth = visual.width,
            imageHeight = visual.height,
            pixelMappingProtocol = VisualCoordinateProtocol.pixelMappingProtocol,
            coordinateSpace = VisualCoordinateProtocol.coordinateSpace,
        )
        recordPlannedAction(materialized, snapshot, "gui_plus_tap_materialized")
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "gui_plus_visual_coordinate_materialized",
            details = JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("modelX", modelX)
                put("modelY", modelY)
                put("physicalX", point.x)
                put("physicalY", point.y)
                put("displayWidth", frame.width)
                put("displayHeight", frame.height)
                put("visualDecisionOwner", "gui_plus")
                put("secondaryTapVerifierRequired", false)
                put("responseSessionId", step.argString("responseSessionId").orEmpty())
                put("responseObservationId", step.argString("responseObservationId").orEmpty())
            },
        )
        VisualAgentHudRuntime.notePlannedStep(materialized)
        awaitHudPointerLead()
        return materialized
    }

    private fun rejectCoordinateMaterialization(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        modelX: Float?,
        modelY: Float?,
        reason: String,
    ): CloudAgentStep {
        val visual = snapshot.visual
        val rejected = step
            .withTapExecutionTrace(
                modelX = modelX,
                modelY = modelY,
                modelPixelX = null,
                modelPixelY = null,
                materializedX = null,
                materializedY = null,
                displayWidth = visual?.displayWidth?.takeIf { it > 0 },
                displayHeight = visual?.displayHeight?.takeIf { it > 0 },
                imageWidth = visual?.width,
                imageHeight = visual?.height,
                pixelMappingProtocol = "rejected:$reason",
                coordinateSpace = "normalized_screen",
            )
            .withExecutionTraceFields(
                linkedMapOf(
                    TRACE_COORDINATE_MATERIALIZATION_REJECTED to true,
                    TRACE_COORDINATE_MATERIALIZATION_REJECT_REASON to reason,
                    TRACE_REJECTED_ACTION to "tap_xy",
                ),
            )
            .copy(
                type = "wait",
                x = null,
                y = null,
                durationMs = REOBSERVE_WAIT_MS,
                targetText = "重新观察",
                reason = "GUI Plus visual coordinate could not be bound to its source display frame ($reason); a fresh screenshot is required.",
            )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "tap_coordinate_materialization_rejected",
            details = JSONObject().apply {
                put("originalType", "tap_xy")
                put("reason", reason)
                put("packageName", snapshot.packageName)
                put("surfaceMode", surfaceEvidenceMode(snapshot))
                put("modelX", modelX ?: JSONObject.NULL)
                put("modelY", modelY ?: JSONObject.NULL)
                put("imageWidth", visual?.width ?: JSONObject.NULL)
                put("imageHeight", visual?.height ?: JSONObject.NULL)
                put("displayWidth", visual?.displayWidth ?: JSONObject.NULL)
                put("displayHeight", visual?.displayHeight ?: JSONObject.NULL)
            },
        )
        recordPlannedAction(rejected, snapshot, "tap_materialization_rejected")
        return rejected
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
        modelX: Float?,
        modelY: Float?,
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
            hasVisualEvidence && hasNodeEvidence -> "visual_with_optional_nodes"
            hasVisualEvidence -> "visual_only"
            hasNodeEvidence -> "node_execution_only"
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
            ?: step.text?.takeIf { step.type != "input_text" }?.take(32)?.takeIf(String::isNotBlank)
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
        val visualAuthority = args?.optString(TRACE_VISUAL_AUTHORITY).orEmpty().ifBlank { "gui_plus_screenshot" }

        return buildList {
            surfaceMode?.let { add("surface=$it") }
            add("authority=$visualAuthority")
            add("protocol=$protocol")
            add("mapping=$mapping")
            add("space=$coordinateSpace")
            if (displayWidth != null && displayHeight != null) add("sourceFrame=${displayWidth}x$displayHeight")
            if (imageWidth != null && imageHeight != null) add("modelImage=${imageWidth}x$imageHeight")
            if (modelX != null && modelY != null) add("modelNorm=${formatTraceCoordinate(modelX)},${formatTraceCoordinate(modelY)}")
            if (modelPixelX != null && modelPixelY != null) add("modelPx=${formatTraceCoordinate(modelPixelX)},${formatTraceCoordinate(modelPixelY)}")
            if (materializedX != null && materializedY != null) {
                add("materializedPx=${formatTraceCoordinate(materializedX)},${formatTraceCoordinate(materializedY)}")
            }
            actualPoint?.let { add("executedPx=${formatTraceCoordinate(it.first)},${formatTraceCoordinate(it.second)}") }
            add("secondaryVerifierRequired=false")
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
        captureStructuredUserReplies(interactions)
        val revisionSignals = VisualUserTaskUpdateRuntime.takeUndispatchedPromptLines().takeLast(4)
        val mergedInteractions = interactions + AgentTakeoverDialogueBridge.interactionActions()
        val interactionLimit = (MAX_INTERACTION_IN_REQUEST - revisionSignals.size).coerceAtLeast(4)
        val interactionBudget = mergedInteractions.takeLast(interactionLimit)
        val runtimeBudget = (CLIENT_ACTION_LIMIT - interactionBudget.size - revisionSignals.size)
            .coerceAtLeast(MIN_RUNTIME_ACTIONS)
        val request = recent.takeLast(runtimeBudget) + revisionSignals + interactionBudget
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "model_request_memory",
            details = JSONObject().apply {
                put("recentActionsBeforeBudget", JSONArray(recent))
                put("interactionActionsBeforeBudget", JSONArray(mergedInteractions))
                put("taskRevisionSignals", JSONArray(revisionSignals))
                put("runtimeBudget", runtimeBudget)
                put("interactionBudget", interactionBudget.size)
                put("actualRequestActions", JSONArray(request))
                put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
                put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
            },
        )
        return request
    }

    private fun captureStructuredUserReplies(interactions: List<String>) {
        var latestQuestion = ""
        interactions.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("guiPlusQuestion:") -> {
                    latestQuestion = line.substringAfter("guiPlusQuestion:").take(MAX_INTERACTION_TEXT_CHARS)
                }
                line.startsWith("userReply:") -> {
                    VisualUserTaskUpdateRuntime.record(
                        rawReply = line.substringAfter("userReply:"),
                        sourceReason = "model_help_reply",
                        prompt = latestQuestion,
                    )
                }
            }
        }
    }

    fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * 3).coerceAtLeast(maxSteps + 8).coerceAtMost(120)
    }

    private val EXECUTED_POINT_PATTERN = Regex("实际落点\\s+(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
    private const val REOBSERVE_WAIT_MS = 220L
    private const val HUD_POINTER_LEAD_MS = 240L
    private const val TRACE_SURFACE_MODE = "__androidVisualSurfaceMode"
    private const val TRACE_VISUAL_AUTHORITY = "__androidVisualAuthority"
    private const val TRACE_SECONDARY_VERIFIER_REQUIRED = "__androidSecondaryVerifierRequired"
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
    private const val TRACE_COORDINATE_MATERIALIZATION_REJECTED = "__androidCoordinateMaterializationRejected"
    private const val TRACE_COORDINATE_MATERIALIZATION_REJECT_REASON = "__androidCoordinateMaterializationRejectReason"
    private const val TRACE_REJECTED_ACTION = "__androidRejectedAction"
}
