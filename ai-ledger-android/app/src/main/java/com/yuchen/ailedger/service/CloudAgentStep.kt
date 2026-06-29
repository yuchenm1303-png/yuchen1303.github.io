package com.yuchen.ailedger.service

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class CloudAgentState(
    val isComplete: Boolean = false,
    val expectedProgress: Boolean = false,
    val isWrong: Boolean = false,
    val confidence: Float = 0f,
    val reason: String = "",
    val nextHint: String = "",
) {
    companion object {
        fun fromJson(root: JSONObject?): CloudAgentState? {
            if (root == null) return null
            val item = root.optJSONObject("agentState")
                ?: root.optJSONObject("state")
                ?: root.optJSONObject("data")?.optJSONObject("agentState")
                ?: root.optJSONObject("result")?.optJSONObject("agentState")
                ?: root.optJSONObject("plan")?.optJSONObject("agentState")
                ?: root.takeIf {
                    it.has("isComplete") || it.has("complete") ||
                        it.has("expectedProgress") || it.has("isWrong")
                }
                ?: return null
            val complete = item.optFlexibleBoolean("isComplete")
                ?: item.optFlexibleBoolean("complete")
                ?: item.optFlexibleBoolean("completed")
                ?: item.optFlexibleBoolean("isExpected")
                ?: false
            val progress = item.optFlexibleBoolean("expectedProgress")
                ?: item.optFlexibleBoolean("progress")
                ?: item.optFlexibleBoolean("isProgress")
                ?: item.optFlexibleBoolean("onRightTrack")
                ?: complete
            val wrong = item.optFlexibleBoolean("isWrong")
                ?: item.optFlexibleBoolean("wrong")
                ?: item.optFlexibleBoolean("wrongPage")
                ?: item.optFlexibleBoolean("offTarget")
                ?: false
            val safeWrong = wrong && !complete && !progress
            val score = item.optNullableFloat("confidence")
                ?: item.optNullableFloat("score")
                ?: when {
                    complete || safeWrong -> 0.72f
                    progress -> 0.62f
                    else -> 0.35f
                }
            return CloudAgentState(
                isComplete = complete,
                expectedProgress = progress || complete,
                isWrong = safeWrong,
                confidence = score.coerceIn(0f, 1f),
                reason = item.firstNonBlank("reason", "explanation", "rationale").orEmpty(),
                nextHint = item.firstNonBlank("nextHint", "next_hint", "hint").orEmpty(),
            )
        }
    }
}

data class CloudAgentPlan(
    val step: CloudAgentStep,
    val state: CloudAgentState? = null,
    val steps: List<CloudAgentStep> = emptyList(),
    val stopConditions: Set<String> = emptySet(),
    val rawModelOutput: String = "",
    val taskContract: VisualTaskContract? = null,
) {
    val executableSteps: List<CloudAgentStep>
        get() = steps.ifEmpty { listOf(step) }.take(MAX_BATCH_STEPS)

    companion object {
        const val MAX_BATCH_STEPS = 3

        fun fromJson(root: JSONObject?): CloudAgentPlan? {
            val parsedPrimary = CloudAgentStep.fromJson(root) ?: return null
            val visuallyAuthoritativePrimary = restoreGuiPlusVisualTap(root, parsedPrimary)
            val primary = repairMisclassifiedMobileUseTerminate(root, visuallyAuthoritativePrimary)
            val parsedSteps = extractBatchSteps(root)
                .filterNot { it.type == "need_user_help" || it.type == "finish" }
                .distinctBy { it.batchKey() }
                .take(MAX_BATCH_STEPS)
            return CloudAgentPlan(
                step = primary,
                state = CloudAgentState.fromJson(root),
                steps = parsedSteps.ifEmpty { listOf(primary) },
                stopConditions = extractStopConditions(root),
                rawModelOutput = extractRawModelOutput(root),
                taskContract = VisualTaskContract.fromJson(root),
            )
        }

        /**
         * GUI Plus is the sole visual grounding authority. Older backend builds may run a second
         * verifier and replace the original tap with wait/reobserve. When the response still carries
         * the original compact GUI Plus action, restore that exact observation-bound coordinate.
         *
         * This is protocol normalization, not local visual inference: Android never invents, moves or
         * re-labels the point. Missing binding or malformed coordinates remain non-executable.
         */
        private fun restoreGuiPlusVisualTap(
            root: JSONObject?,
            step: CloudAgentStep,
        ): CloudAgentStep {
            if (root == null || step.type != "wait") return step
            val rejectedType = step.argString("rejectedActionType").orEmpty()
                .trim().lowercase().replace('-', '_')
            val verifierReplacement = rejectedType == "tap_xy" ||
                step.reason.orEmpty().contains("grounding verifier did not confirm", ignoreCase = true)
            if (!verifierReplacement) return step

            val compact = extractGuiCompactAction(root) ?: return step
            val compactType = compact.firstNonBlank("a", "action", "type")
                .orEmpty().trim().lowercase().replace('-', '_')
            if (compactType !in setOf("tap_xy", "tap", "click", "press", "point")) return step

            val x = compact.optNullableFloat("x")?.takeIf { it.isFinite() && it in 0f..1f }
            val y = compact.optNullableFloat("y")?.takeIf { it.isFinite() && it in 0f..1f }
            val responseSessionId = step.argString("responseSessionId").orEmpty().trim()
            val responseObservationId = step.argString("responseObservationId").orEmpty().trim()
            if (x == null || y == null || responseSessionId.isBlank() || responseObservationId.isBlank()) {
                VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                    type = "gui_plus_visual_tap_restore_rejected",
                    details = JSONObject().apply {
                        put("reason", "missing_coordinate_or_response_binding")
                        put("hasX", x != null)
                        put("hasY", y != null)
                        put("hasResponseSessionId", responseSessionId.isNotBlank())
                        put("hasResponseObservationId", responseObservationId.isNotBlank())
                        put("secondaryVerifierVerdict", step.argString("guiVerifierVerdict").orEmpty())
                    },
                )
                return step
            }

            val args = step.toolArgs.deepCopy()
            args.put("responseSessionId", responseSessionId)
            args.put("responseObservationId", responseObservationId)
            args.put("visualDecisionOwner", "gui_plus")
            args.put("visualCoordinateAuthority", "gui_plus_original_action")
            args.put("secondaryTapVerifierAdvisoryOnly", true)
            args.put("restoredRejectedActionType", "tap_xy")

            val intent = compact.optJSONObject("actionIntent")
            val semanticContainers = listOfNotNull(intent, compact)
            val semanticFieldNames = setOf(
                "purpose", "milestoneId", "milestone", "expectedEvidence", "successEvidence",
                "failureEvidence", "wrongEvidence", "exploratory", "reversible", "confidence", "hypothesisId",
            )
            val hasSemanticContract = semanticContainers.any { container -> semanticFieldNames.any(container::has) }
            val expectedEvidence = semanticContainers
                .flatMap { it.stringList("expectedEvidence", "successEvidence", "expected") }
                .distinct().take(16)
            val failureEvidence = semanticContainers
                .flatMap { it.stringList("failureEvidence", "wrongEvidence", "negativeEvidence") }
                .distinct().take(16)
            val riskLevel = compact.firstNonBlank("r", "risk", "riskLevel")
                ?.lowercase()?.replace('-', '_')
                ?: step.riskLevel
            val requiresConfirmation = compact.optFlexibleBoolean("q")
                ?: compact.optFlexibleBoolean("confirm")
                ?: compact.optFlexibleBoolean("requiresConfirmation")
                ?: step.requiresConfirmation

            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                type = "gui_plus_visual_tap_restored",
                details = JSONObject().apply {
                    put("x", x)
                    put("y", y)
                    put("targetText", compact.firstNonBlank("t", "targetText", "target").orEmpty())
                    put("responseSessionId", responseSessionId)
                    put("responseObservationId", responseObservationId)
                    put("secondaryVerifierVerdict", step.argString("guiVerifierVerdict").orEmpty())
                    put("secondaryVerifierConfidence", step.argFloat("guiVerifierConfidence") ?: JSONObject.NULL)
                },
            )
            return step.copy(
                type = "tap_xy",
                targetNodeId = null,
                targetText = compact.firstNonBlank("t", "targetText", "target") ?: step.targetText,
                reason = compact.firstNonBlank("e", "reason", "rationale") ?: step.reason,
                riskLevel = riskLevel,
                requiresConfirmation = requiresConfirmation,
                x = x,
                y = y,
                durationMs = null,
                toolArgs = args,
                purpose = semanticContainers.firstNotNullOfOrNull {
                    it.firstNonBlank("purpose", "subgoal", "actionPurpose")
                } ?: step.purpose,
                milestoneId = semanticContainers.firstNotNullOfOrNull {
                    it.firstNonBlank("milestoneId", "milestone", "currentMilestoneId")
                } ?: step.milestoneId,
                expectedEvidence = expectedEvidence.ifEmpty { step.expectedEvidence },
                failureEvidence = failureEvidence.ifEmpty { step.failureEvidence },
                exploratory = semanticContainers.firstNotNullOfOrNull {
                    it.optFlexibleBooleanOrNull("exploratory")
                } ?: step.exploratory,
                reversible = semanticContainers.firstNotNullOfOrNull {
                    it.optFlexibleBooleanOrNull("reversible")
                } ?: step.reversible,
                confidence = semanticContainers.firstNotNullOfOrNull {
                    it.optNullableFloat("confidence")
                } ?: compact.optNullableFloat("c") ?: step.confidence,
                hypothesisId = semanticContainers.firstNotNullOfOrNull {
                    it.firstNonBlank("hypothesisId", "hypothesis", "intentId")
                } ?: step.hypothesisId,
                legacyIntent = if (hasSemanticContract) false else step.legacyIntent,
            )
        }

        private fun repairMisclassifiedMobileUseTerminate(
            root: JSONObject?,
            step: CloudAgentStep,
        ): CloudAgentStep {
            if (root == null || step.type != "need_user_help") return step
            val raw = extractGuiCompactRaw(root)
            if (!raw.hasOfficialMobileUseTerminateCall()) return step

            val sessionId = step.argString("responseSessionId").orEmpty().trim()
            val observationId = step.argString("responseObservationId").orEmpty().trim()
            if (sessionId.isBlank() || observationId.isBlank()) {
                VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                    type = "mobile_use_protocol_repair_rejected",
                    details = JSONObject().apply {
                        put("reason", "missing_response_binding")
                        put("parsedStepType", step.type)
                        put("hasResponseSessionId", sessionId.isNotBlank())
                        put("hasResponseObservationId", observationId.isNotBlank())
                    },
                )
                return step.copy(
                    type = "wait",
                    durationMs = 220L,
                    targetText = "重新观察",
                    reason = "mobile_use terminate was misclassified, but the response binding was incomplete; re-observe instead of pausing the user.",
                )
            }

            val args = step.toolArgs.deepCopy()
            val candidateId = completionCandidateId(sessionId, observationId)
            args.put("responseSessionId", sessionId)
            args.put("responseObservationId", observationId)
            args.put("completionCandidate", true)
            args.put("completionCandidateId", candidateId)
            args.put("completionCandidateSessionId", sessionId)
            args.put("completionCandidateObservationId", observationId)
            args.put("mobileUseProtocolRepair", "terminate_to_finish_candidate")
            args.put("mobileUseOriginalAction", "terminate")

            VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
                type = "mobile_use_protocol_repair",
                details = JSONObject().apply {
                    put("originalStepType", step.type)
                    put("repairedStepType", "finish")
                    put("sourceAction", "terminate")
                    put("candidateId", candidateId)
                    put("responseSessionId", sessionId)
                    put("responseObservationId", observationId)
                    put("completionPermitRequired", true)
                },
            )
            return step.copy(
                type = "finish",
                requiresConfirmation = false,
                toolArgs = args,
            )
        }

        private fun extractGuiCompactAction(root: JSONObject): JSONObject? = listOfNotNull(
            root.optJSONObject("debug")?.optJSONObject("guiCompactAction"),
            root.optJSONObject("guiCompactAction"),
            root.optJSONObject("data")?.optJSONObject("debug")?.optJSONObject("guiCompactAction"),
            root.optJSONObject("result")?.optJSONObject("debug")?.optJSONObject("guiCompactAction"),
        ).firstOrNull()

        private fun extractGuiCompactRaw(root: JSONObject): String =
            extractGuiCompactAction(root)
                ?.firstNonBlank("raw", "rawModelOutput", "guiPlusRawOutput")
                ?.take(6000)
                .orEmpty()

        private fun String.hasOfficialMobileUseTerminateCall(): Boolean =
            MOBILE_USE_NAME_PATTERN.containsMatchIn(this) && TERMINATE_ACTION_PATTERN.containsMatchIn(this)

        private fun completionCandidateId(sessionId: String, observationId: String): String {
            val canonical = "$sessionId|$observationId|mobile_use|terminate"
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(24)
            return "completion_candidate_$hash"
        }

        private fun extractRawModelOutput(root: JSONObject?): String {
            if (root == null) return ""
            val direct = listOfNotNull(root, root.optJSONObject("debug"), root.optJSONObject("data"), root.optJSONObject("result"))
                .firstNotNullOfOrNull { container ->
                    container.firstNonBlank("rawModelOutput", "guiPlusRawOutput", "rawOutput", "raw")
                }?.take(6000).orEmpty()
            return direct.ifBlank { extractGuiCompactRaw(root) }
        }

        private fun extractBatchSteps(root: JSONObject?): List<CloudAgentStep> {
            if (root == null) return emptyList()
            return listOfNotNull(root, root.optJSONObject("plan"), root.optJSONObject("data"), root.optJSONObject("result"), root.optJSONObject("agentPlan"))
                .flatMap { container ->
                    listOf("agentSteps", "steps", "actionBatch", "actions")
                        .flatMap { key -> container.optJSONArray(key).toAgentSteps() }
                }
        }

        private fun extractStopConditions(root: JSONObject?): Set<String> {
            if (root == null) return emptySet()
            return listOfNotNull(root, root.optJSONObject("plan"), root.optJSONObject("data"), root.optJSONObject("result"), root.optJSONObject("agentPlan"))
                .flatMap { container -> listOf("stopConditions", "batchStopConditions", "replanOn").flatMap(container::optStringSet) }
                .toSet()
        }

        private fun JSONArray?.toAgentSteps(): List<CloudAgentStep> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) optJSONObject(index)?.let(CloudAgentStep::fromJson)?.let(::add)
            }
        }

        private val MOBILE_USE_NAME_PATTERN = Regex(
            "[\\\"']name[\\\"']\\s*:\\s*[\\\"']mobile_use[\\\"']",
            RegexOption.IGNORE_CASE,
        )
        private val TERMINATE_ACTION_PATTERN = Regex(
            "[\\\"']action[\\\"']\\s*:\\s*[\\\"']terminate[\\\"']",
            RegexOption.IGNORE_CASE,
        )
    }
}

data class CloudAgentStep(
    val type: String,
    val targetNodeId: String? = null,
    val targetText: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val reason: String? = null,
    val riskLevel: String = "low",
    val requiresConfirmation: Boolean = false,
    val appName: String? = null,
    val packageName: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val durationMs: Long? = null,
    val inputMode: String? = null,
    val requiresInputNode: Boolean = true,
    val expectsFocusedInput: Boolean = false,
    val useFocusedInput: Boolean = false,
    val toolArgs: JSONObject? = null,
    val purpose: String? = null,
    val milestoneId: String? = null,
    val expectedEvidence: List<String> = emptyList(),
    val failureEvidence: List<String> = emptyList(),
    val exploratory: Boolean = false,
    val reversible: Boolean = true,
    val confidence: Float? = null,
    val hypothesisId: String? = null,
    val legacyIntent: Boolean = true,
) {
    val typeLabel: String
        get() = TYPE_LABELS[type] ?: type

    val shouldUseFocusedDirectInput: Boolean
        get() = inputMode?.lowercase()?.replace('-', '_') in focusedInputModes ||
            useFocusedInput || expectsFocusedInput || !requiresInputNode

    val actionIntent: VisualActionIntent
        get() = VisualActionIntent(
            purpose = purpose.orEmpty(),
            milestoneId = milestoneId.orEmpty(),
            expectedEvidence = expectedEvidence,
            failureEvidence = failureEvidence,
            exploratory = exploratory,
            reversible = reversible,
            confidence = confidence,
            hypothesisId = hypothesisId.orEmpty(),
            legacyMode = legacyIntent,
        )

    fun argString(vararg names: String): String? {
        val args = toolArgs ?: return null
        return args.firstNonBlank(*names)
    }

    fun argFloat(vararg names: String): Float? {
        val args = toolArgs ?: return null
        for (name in names) {
            if (!args.has(name) || args.isNull(name)) continue
            val value = runCatching { args.getDouble(name).toFloat() }.getOrNull()
                ?: args.optString(name).trim().removeSuffix("%").toFloatOrNull()
            if (value != null && value.isFinite()) return value
        }
        return null
    }

    fun argLong(vararg names: String): Long? {
        val args = toolArgs ?: return null
        for (name in names) {
            if (!args.has(name) || args.isNull(name)) continue
            val value = runCatching { args.getLong(name) }.getOrNull() ?: args.optString(name).trim().toLongOrNull()
            if (value != null) return value
        }
        return null
    }

    companion object {
        private val focusedInputModes = setOf(
            "focused_direct", "focused", "direct", "keyboard", "ime", "active_input", "current_focus",
        )

        private val TYPE_LABELS = mapOf(
            "open_app" to "打开应用", "tap_node" to "点击节点", "tap_xy" to "点击坐标",
            "input_text" to "输入文字", "scroll" to "滚动屏幕", "swipe" to "滑动屏幕",
            "back" to "返回", "home" to "回到桌面", "recents" to "打开最近任务",
            "notifications" to "下拉通知栏", "quick_settings" to "打开快捷设置", "wait" to "等待",
            "finish" to "任务完成", "need_user_help" to "需要用户协助",
            "open_system_settings" to "打开系统设置", "open_app_settings" to "打开应用设置",
            "set_brightness" to "调节亮度", "set_screen_timeout" to "设置息屏时间",
            "set_auto_rotate" to "设置自动旋转", "set_media_volume" to "设置媒体音量",
            "set_wifi_enabled" to "设置 Wi‑Fi", "set_bluetooth_enabled" to "设置蓝牙",
            "set_mobile_data_enabled" to "设置移动数据", "set_dark_mode" to "设置深色模式",
            "device_status" to "设备状态", "shizuku_status" to "Shizuku 状态",
            "request_shizuku_permission" to "请求 Shizuku 授权", "set_animation_scale" to "设置动画缩放",
            "force_stop_app" to "强停应用", "clear_app_data" to "清除应用数据",
            "uninstall_app" to "卸载应用", "disable_app" to "禁用应用", "enable_app" to "启用应用",
            "ledger_add_record" to "新增账单", "ledger_set_budget" to "设置账单预算",
            "ledger_query_summary" to "查询账单汇总", "ledger_list_records" to "查询账单明细",
        )

        val systemDeviceToolTypes = setOf(
            "open_app", "open_system_settings", "open_app_settings", "set_brightness",
            "set_screen_timeout", "set_auto_rotate", "set_media_volume", "set_wifi_enabled",
            "set_bluetooth_enabled", "set_mobile_data_enabled", "set_dark_mode", "device_status",
            "shizuku_status", "request_shizuku_permission", "set_animation_scale", "force_stop_app",
            "clear_app_data", "uninstall_app", "disable_app", "enable_app",
        )
        val ledgerToolTypes = setOf("ledger_add_record", "ledger_set_budget", "ledger_query_summary", "ledger_list_records")
        val deviceToolTypes = systemDeviceToolTypes + ledgerToolTypes
        val internalToolTypes = deviceToolTypes
        val accessibilityStepTypes = setOf(
            "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "home", "recents",
            "notifications", "quick_settings", "wait", "finish", "need_user_help",
        )
        val supportedTypes = accessibilityStepTypes + internalToolTypes

        fun fromJson(root: JSONObject?): CloudAgentStep? {
            val item = root?.optJSONObject("agentStep")
                ?: root?.optJSONObject("step")
                ?: root?.optJSONObject("agentAction")?.optJSONObject("step")
                ?: root?.optJSONObject("data")?.optJSONObject("agentStep")
                ?: root?.optJSONObject("data")?.optJSONObject("step")
                ?: root?.optJSONObject("result")?.optJSONObject("agentStep")
                ?: root?.takeIf { it.has("type") || it.has("action") || it.has("tool") || it.has("name") }
                ?: return null
            val args = item.mergedToolArgs()
            val nestedIntent = item.optJSONObject("actionIntent")
                ?: item.optJSONObject("progressContract")
                ?: item.optJSONObject("semanticIntent")
                ?: args.optJSONObject("actionIntent")
                ?: args.optJSONObject("progressContract")
                ?: args.optJSONObject("semanticIntent")
            val rawType = item.firstNonBlank("type", "action", "tool", "name") ?: return null
            val normalizedType = normalizeStepType(rawType) ?: return null
            val parsedInputMode = item.firstNonBlank("inputMode", "input_mode", "inputStrategy", "input_strategy")
                ?: args.firstNonBlank("inputMode", "input_mode", "inputStrategy", "input_strategy")
            val inputModeKey = parsedInputMode?.lowercase()?.replace('-', '_')
            val explicitFocused = item.optFlexibleBoolean("useFocusedInput")
                ?: item.optFlexibleBoolean("use_focused_input")
                ?: item.optFlexibleBoolean("focusedDirect")
                ?: item.optFlexibleBoolean("focused_direct")
            val expectsFocused = item.optFlexibleBoolean("expectsFocusedInput")
                ?: item.optFlexibleBoolean("expects_focused_input")
                ?: item.optFlexibleBoolean("focusedInput")
                ?: item.optFlexibleBoolean("focused_input")
                ?: false
            val inferredFocused = inputModeKey in focusedInputModes || explicitFocused == true || expectsFocused
            val requiresNode = item.optFlexibleBoolean("requiresInputNode")
                ?: item.optFlexibleBoolean("requires_input_node")
                ?: item.optFlexibleBoolean("inputNodeRequired")
                ?: item.optFlexibleBoolean("input_node_required")
                ?: !inferredFocused
            val targetText = item.firstNonBlank("targetText", "label", "title", "target")
                ?: args.firstNonBlank("targetText", "target", "label", "title", "page", "kind")
            val parsedText = item.firstNonBlank("text", "inputText", "value")
                ?: args.firstNonBlank("text", "inputText", "value", "query", "content")
            val appName = item.firstNonBlank("appName", "app", "application")
                ?: args.firstNonBlank("appName", "app", "application", "label", "name")
                ?: if (normalizedType == "open_app") targetText ?: parsedText else null
            val packageName = item.firstNonBlank("packageName", "package", "pkg", "appRef", "app_ref")
                ?: args.firstNonBlank("packageName", "package", "pkg", "appRef", "app_ref")

            val semanticContainers = listOfNotNull(nestedIntent, item, args)
            val semanticFieldNames = setOf(
                "purpose", "milestoneId", "milestone", "expectedEvidence", "successEvidence",
                "failureEvidence", "wrongEvidence", "exploratory", "reversible", "confidence", "hypothesisId",
            )
            val hasSemanticContract = semanticContainers.any { container -> semanticFieldNames.any(container::has) }
            val purpose = semanticContainers.firstNotNullOfOrNull { it.firstNonBlank("purpose", "subgoal", "actionPurpose") }
            val milestone = semanticContainers.firstNotNullOfOrNull { it.firstNonBlank("milestoneId", "milestone", "currentMilestoneId") }
            val expectedEvidence = semanticContainers.flatMap { it.stringList("expectedEvidence", "successEvidence", "expected") }
                .distinct().take(16)
            val failureEvidence = semanticContainers.flatMap { it.stringList("failureEvidence", "wrongEvidence", "negativeEvidence") }
                .distinct().take(16)
            val explicitExploratory = semanticContainers.firstNotNullOfOrNull { it.optFlexibleBooleanOrNull("exploratory") }
            val explicitReversible = semanticContainers.firstNotNullOfOrNull { it.optFlexibleBooleanOrNull("reversible") }
            val confidence = semanticContainers.firstNotNullOfOrNull { it.optNullableFloat("confidence") }
            val hypothesis = semanticContainers.firstNotNullOfOrNull { it.firstNonBlank("hypothesisId", "hypothesis", "intentId") }
            val legacyExploratory = normalizedType in setOf("swipe", "scroll", "wait", "back")

            return CloudAgentStep(
                type = normalizedType,
                targetNodeId = item.firstNonBlank("targetNodeId", "nodeId", "targetId")
                    ?: args.firstNonBlank("targetNodeId", "nodeId", "targetId"),
                targetText = targetText,
                text = parsedText,
                direction = item.firstNonBlank("direction")?.lowercase() ?: args.firstNonBlank("direction")?.lowercase(),
                reason = item.firstNonBlank("reason", "rationale") ?: args.firstNonBlank("reason", "rationale"),
                riskLevel = item.firstNonBlank("riskLevel", "risk")?.lowercase()?.replace('-', '_')
                    ?: args.firstNonBlank("riskLevel", "risk")?.lowercase()?.replace('-', '_') ?: "low",
                requiresConfirmation = item.optFlexibleBoolean("requiresConfirmation")
                    ?: item.optFlexibleBoolean("confirm") ?: false,
                appName = appName,
                packageName = packageName,
                x = item.optTapCoordinateComponent(0) ?: args.optTapCoordinateComponent(0),
                y = item.optTapCoordinateComponent(1) ?: args.optTapCoordinateComponent(1),
                durationMs = item.optNullableLong("durationMs") ?: item.optNullableLong("delayMs")
                    ?: item.optNullableLong("waitMs") ?: args.optNullableLong("durationMs")
                    ?: args.optNullableLong("delayMs") ?: args.optNullableLong("waitMs"),
                inputMode = parsedInputMode,
                requiresInputNode = requiresNode,
                expectsFocusedInput = expectsFocused,
                useFocusedInput = explicitFocused ?: inferredFocused,
                toolArgs = args.takeIf { it.length() > 0 },
                purpose = purpose?.take(240),
                milestoneId = milestone?.take(100),
                expectedEvidence = expectedEvidence,
                failureEvidence = failureEvidence,
                exploratory = explicitExploratory ?: legacyExploratory,
                reversible = explicitReversible ?: normalizedType !in setOf("input_text"),
                confidence = confidence?.coerceIn(0f, 1f),
                hypothesisId = hypothesis?.take(120),
                legacyIntent = !hasSemanticContract,
            )
        }

        private fun normalizeStepType(rawType: String): String? {
            val key = rawType.lowercase().trim().replace('-', '_')
            val normalized = TYPE_ALIASES[key] ?: key
            return normalized.takeIf { it in supportedTypes }
        }

        private val TYPE_ALIASES = mapOf(
            "open" to "open_app", "launch" to "open_app", "launch_app" to "open_app", "open_application" to "open_app",
            "tap" to "tap_xy", "click" to "tap_xy", "press" to "tap_xy", "point" to "tap_xy",
            "tap_point" to "tap_xy", "click_xy" to "tap_xy", "coordinate_click" to "tap_xy", "coordinate_tap" to "tap_xy",
            "input" to "input_text", "type" to "input_text", "enter_text" to "input_text", "text" to "input_text",
            "done" to "finish", "complete" to "finish", "completed" to "finish",
            "ask_user" to "need_user_help", "need_help" to "need_user_help", "clarify" to "need_user_help",
            "settings" to "open_system_settings", "open_settings" to "open_system_settings", "system_settings" to "open_system_settings",
            "app_settings" to "open_app_settings", "app_info" to "open_app_settings", "open_app_detail" to "open_app_settings",
            "brightness" to "set_brightness", "screen_brightness" to "set_brightness",
            "screen_timeout" to "set_screen_timeout", "sleep_timeout" to "set_screen_timeout",
            "auto_rotate" to "set_auto_rotate", "rotation" to "set_auto_rotate", "accelerometer_rotation" to "set_auto_rotate",
            "media_volume" to "set_media_volume", "volume" to "set_media_volume", "set_volume" to "set_media_volume", "music_volume" to "set_media_volume",
            "wifi" to "set_wifi_enabled", "wi_fi" to "set_wifi_enabled", "set_wifi" to "set_wifi_enabled", "wifi_enabled" to "set_wifi_enabled",
            "bluetooth" to "set_bluetooth_enabled", "set_bluetooth" to "set_bluetooth_enabled", "bluetooth_enabled" to "set_bluetooth_enabled",
            "mobile_data" to "set_mobile_data_enabled", "cellular_data" to "set_mobile_data_enabled", "data_enabled" to "set_mobile_data_enabled", "set_data" to "set_mobile_data_enabled",
            "dark_mode" to "set_dark_mode", "night_mode" to "set_dark_mode", "ui_mode" to "set_dark_mode",
            "health" to "device_status", "device_health" to "device_status",
            "shell_status" to "shizuku_status", "enhanced_status" to "shizuku_status", "shizuku" to "shizuku_status",
            "shizuku_permission" to "request_shizuku_permission", "request_shizuku" to "request_shizuku_permission",
            "animation_scale" to "set_animation_scale", "force_stop" to "force_stop_app", "force_stop_application" to "force_stop_app",
            "clear_data" to "clear_app_data", "uninstall" to "uninstall_app", "disable" to "disable_app", "enable" to "enable_app",
            "add_ledger_record" to "ledger_add_record", "create_ledger_record" to "ledger_add_record", "ledger_record_add" to "ledger_add_record", "ledger_add" to "ledger_add_record",
            "set_ledger_budget" to "ledger_set_budget", "ledger_budget_set" to "ledger_set_budget", "budget_set" to "ledger_set_budget",
            "query_ledger_summary" to "ledger_query_summary", "ledger_summary" to "ledger_query_summary", "ledger_query" to "ledger_query_summary",
            "list_ledger_records" to "ledger_list_records", "ledger_records" to "ledger_list_records", "ledger_list" to "ledger_list_records",
        )
    }
}

private fun CloudAgentStep.batchKey(): String = listOf(
    type, packageName.orEmpty(), appName.orEmpty(), targetNodeId.orEmpty(), targetText.orEmpty(), text.orEmpty(),
    x?.toString().orEmpty(), y?.toString().orEmpty(), milestoneId.orEmpty(), hypothesisId.orEmpty(), purpose.orEmpty(),
).joinToString("|")

private fun JSONObject.optStringSet(name: String): Set<String> {
    if (!has(name) || isNull(name)) return emptySet()
    return when (val raw = opt(name)) {
        is JSONArray -> buildSet {
            for (index in 0 until raw.length()) raw.optString(index).trim().takeIf { it.isNotBlank() }
                ?.lowercase()?.replace('-', '_')?.let(::add)
        }
        is String -> raw.split(',', ';', '|').mapNotNull { it.trim().takeIf(String::isNotBlank)?.lowercase()?.replace('-', '_') }.toSet()
        else -> emptySet()
    }
}

private fun JSONObject.deepCopy(): JSONObject =
    runCatching { JSONObject(toString()) }.getOrDefault(JSONObject())

private fun JSONObject?.deepCopy(): JSONObject =
    this?.let { runCatching { JSONObject(it.toString()) }.getOrDefault(JSONObject()) } ?: JSONObject()

private fun JSONObject.mergedToolArgs(): JSONObject {
    val source = optJSONObject("args") ?: optJSONObject("arguments") ?: JSONObject()
    val merged = runCatching { JSONObject(source.toString()) }.getOrDefault(JSONObject())
    val keys = listOf(
        "appName", "app", "application", "packageName", "package", "pkg", "appRef", "app_ref",
        "targetText", "target", "label", "title", "page", "kind", "percent", "brightness", "volume",
        "value", "seconds", "minutes", "timeoutMs", "durationMs", "delayMs", "waitMs", "scale", "enabled",
        "enable", "on", "state", "mode", "operation", "delta", "deltaPercent", "changePercent", "adjustBy",
        "text", "query", "content", "reason", "risk", "riskLevel", "direction", "inputMode", "amount", "budget",
        "recordType", "transactionType", "entryType", "category", "date", "dateLabel", "range", "period",
        "timeRange", "month", "startDate", "endDate", "limit", "count", "description", "purpose", "subgoal",
        "milestoneId", "milestone", "currentMilestoneId", "expectedEvidence", "successEvidence", "failureEvidence",
        "wrongEvidence", "negativeEvidence", "exploratory", "reversible", "confidence", "hypothesisId", "hypothesis",
        "actionIntent", "progressContract", "semanticIntent",
    )
    for (key in keys) if (!merged.has(key) && has(key)) merged.put(key, opt(key))
    return merged
}

private fun JSONObject.optNullableFloat(name: String): Float? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optDouble(name).toFloat() }.getOrNull()?.takeIf(Float::isFinite)
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { getLong(name) }.getOrNull() ?: optString(name).trim().toLongOrNull()
}

private fun JSONObject.optTapCoordinateComponent(index: Int): Float? {
    val directNames = if (index == 0) listOf("x", "centerX", "tapX", "targetX", "cx") else listOf("y", "centerY", "tapY", "targetY", "cy")
    directNames.firstNotNullOfOrNull { optNullableFloat(it) }?.let { return it }
    for (name in listOf("coordinate", "coordinates", "coord", "coords", "point", "position", "center", "tapPoint", "xy")) {
        optJSONArray(name)?.let { array ->
            if (array.length() > index) runCatching { array.getDouble(index).toFloat() }.getOrNull()?.let { return it }
        }
        optJSONObject(name)?.let { obj ->
            directNames.firstNotNullOfOrNull { obj.optNullableFloat(it) }?.let { return it }
            obj.optNullableFloat(index.toString())?.let { return it }
        }
    }
    return null
}

private fun JSONObject.optFlexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.trim().lowercase()) {
            "true", "yes", "1", "expected", "complete", "completed", "progress", "success", "wrong", "on", "enable", "enabled" -> true
            "false", "no", "0", "uncertain", "unknown", "", "off", "disable", "disabled" -> false
            else -> null
        }
        else -> null
    }
}

private fun JSONObject.optFlexibleBooleanOrNull(name: String): Boolean? = optFlexibleBoolean(name)
