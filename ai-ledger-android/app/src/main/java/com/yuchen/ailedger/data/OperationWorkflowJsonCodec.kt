package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.TargetSelectorBundle
import com.yuchen.ailedger.model.TargetSelectorCandidate
import com.yuchen.ailedger.model.WorkflowActionSpec
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowConfirmationPolicy
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowMilestone
import com.yuchen.ailedger.model.WorkflowRecoveryMode
import com.yuchen.ailedger.model.WorkflowRecoveryPolicy
import com.yuchen.ailedger.model.WorkflowRetryPolicy
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowRiskPolicy
import com.yuchen.ailedger.model.WorkflowSelectorKind
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.model.WorkflowVariableDefinition
import com.yuchen.ailedger.model.WorkflowVariableType
import org.json.JSONArray
import org.json.JSONObject

object OperationWorkflowJsonCodec {
    fun encode(draft: LearnedWorkflowDraft): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", draft.id)
        put("title", draft.title)
        put("goal", draft.goal)
        put("executionMode", draft.executionMode.name)
        put("status", draft.status.name)
        put("createdAtMillis", draft.createdAtMillis)
        put("updatedAtMillis", draft.updatedAtMillis)
        put("sourceDemonstrationId", draft.sourceDemonstrationId ?: JSONObject.NULL)
        put("appScope", JSONObject().apply {
            put("packageNames", JSONArray(draft.appScope.normalizedPackages))
            put("displayNames", JSONArray(draft.appScope.displayNames))
            put("allowSystemSurfaces", draft.appScope.allowSystemSurfaces)
        })
        put("variables", JSONArray().apply { draft.variables.forEach { put(it.toJson()) } })
        put("milestones", JSONArray().apply { draft.milestones.forEach { put(it.toJson()) } })
        put("steps", JSONArray().apply { draft.steps.forEach { put(it.toJson()) } })
        put("completionChecks", JSONArray().apply { draft.completionChecks.forEach { put(it.toJson()) } })
        put("riskPolicy", JSONObject().apply {
            put("maximumAllowedRisk", draft.riskPolicy.maximumAllowedRisk.name)
            put("requireConfirmationForHighRisk", draft.riskPolicy.requireConfirmationForHighRisk)
            put("blockPasswordCapture", draft.riskPolicy.blockPasswordCapture)
            put("blockOtpCapture", draft.riskPolicy.blockOtpCapture)
            put("blockPaymentConfirmation", draft.riskPolicy.blockPaymentConfirmation)
        })
        put("recoveryPolicy", JSONObject().apply {
            put("mode", draft.recoveryPolicy.mode.name)
            put("maximumAutomaticRetries", draft.recoveryPolicy.maximumAutomaticRetries)
            put("allowRouteMutation", draft.recoveryPolicy.allowRouteMutation)
        })
    }.toString()

    fun decode(raw: String): LearnedWorkflowDraft = JSONObject(raw).toDraft()

    private fun JSONObject.toDraft(): LearnedWorkflowDraft {
        val now = System.currentTimeMillis()
        val appScopeJson = optJSONObject("appScope") ?: JSONObject()
        return LearnedWorkflowDraft(
            id = getString("id"),
            title = optString("title"),
            goal = optString("goal"),
            appScope = WorkflowAppScope(
                packageNames = appScopeJson.optJSONArray("packageNames").toStringList(),
                displayNames = appScopeJson.optJSONArray("displayNames").toStringList(),
                allowSystemSurfaces = appScopeJson.optBoolean("allowSystemSurfaces", false),
            ),
            variables = optJSONArray("variables").toVariables(),
            milestones = optJSONArray("milestones").toMilestones(),
            steps = optJSONArray("steps").toSteps(),
            completionChecks = optJSONArray("completionChecks").toStateChecks(),
            riskPolicy = optJSONObject("riskPolicy").toRiskPolicy(),
            recoveryPolicy = optJSONObject("recoveryPolicy").toRecoveryPolicy(),
            executionMode = enumValueOrDefault(optString("executionMode"), WorkflowExecutionMode.CloudVisual),
            status = enumValueOrDefault(optString("status"), WorkflowDraftStatus.Intent),
            createdAtMillis = optLong("createdAtMillis", now).coerceAtLeast(0L),
            updatedAtMillis = optLong("updatedAtMillis", now).coerceAtLeast(0L),
            sourceDemonstrationId = optNullableString("sourceDemonstrationId"),
        )
    }

    private fun WorkflowVariableDefinition.toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("label", label)
        put("type", type.name)
        put("required", required)
        put("sensitive", sensitive)
        put("persistValue", persistValue)
        put("allowedValues", JSONArray(allowedValues))
        put("description", description)
    }

    private fun WorkflowMilestone.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("order", order)
        put("completionChecks", JSONArray().apply { completionChecks.forEach { put(it.toJson()) } })
    }

    private fun WorkflowStep.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("order", order)
        put("title", title)
        put("milestoneId", milestoneId)
        put("action", JSONObject().apply {
            put("type", action.type.name)
            put("variableKey", action.variableKey ?: JSONObject.NULL)
            put("fixedArgument", action.fixedArgument ?: JSONObject.NULL)
        })
        put("target", target?.toJson() ?: JSONObject.NULL)
        put("preconditions", JSONArray().apply { preconditions.forEach { put(it.toJson()) } })
        put("postconditions", JSONArray().apply { postconditions.forEach { put(it.toJson()) } })
        put("retryPolicy", JSONObject().apply {
            put("maxAttempts", retryPolicy.maxAttempts)
            put("delayMs", retryPolicy.delayMs)
        })
        put("riskLevel", riskLevel.name)
        put("confirmationPolicy", confirmationPolicy.name)
    }

    private fun TargetSelectorBundle.toJson(): JSONObject = JSONObject().apply {
        put("minimumScore", minimumScore.toDouble())
        put("coordinateFallbackAllowed", coordinateFallbackAllowed)
        put("candidates", JSONArray().apply { candidates.forEach { put(it.toJson()) } })
    }

    private fun TargetSelectorCandidate.toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("value", value)
        put("weight", weight.toDouble())
        put("packageName", packageName ?: JSONObject.NULL)
        put("role", role ?: JSONObject.NULL)
        put("ancestorHint", ancestorHint ?: JSONObject.NULL)
    }

    private fun WorkflowStateCheck.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("expectedValue", expectedValue)
        put("packageName", packageName ?: JSONObject.NULL)
        put("timeoutMs", timeoutMs)
        put("required", required)
    }

    private fun JSONArray?.toVariables(): List<WorkflowVariableDefinition> = buildList {
        val array = this@toVariables ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key").trim()
            val label = item.optString("label").trim()
            if (key.isBlank() || label.isBlank()) continue
            add(
                WorkflowVariableDefinition(
                    key = key,
                    label = label,
                    type = enumValueOrDefault(item.optString("type"), WorkflowVariableType.Text),
                    required = item.optBoolean("required", true),
                    sensitive = item.optBoolean("sensitive", false),
                    persistValue = item.optBoolean("persistValue", false),
                    allowedValues = item.optJSONArray("allowedValues").toStringList(),
                    description = item.optString("description"),
                ),
            )
        }
    }

    private fun JSONArray?.toMilestones(): List<WorkflowMilestone> = buildList {
        val array = this@toMilestones ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            add(
                WorkflowMilestone(
                    id = id,
                    title = item.optString("title"),
                    order = item.optInt("order", index),
                    completionChecks = item.optJSONArray("completionChecks").toStateChecks(),
                ),
            )
        }
    }

    private fun JSONArray?.toSteps(): List<WorkflowStep> = buildList {
        val array = this@toSteps ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val milestoneId = item.optString("milestoneId").trim()
            if (id.isBlank() || milestoneId.isBlank()) continue
            val actionJson = item.optJSONObject("action") ?: JSONObject()
            val retryJson = item.optJSONObject("retryPolicy") ?: JSONObject()
            add(
                WorkflowStep(
                    id = id,
                    order = item.optInt("order", index),
                    title = item.optString("title"),
                    milestoneId = milestoneId,
                    action = WorkflowActionSpec(
                        type = enumValueOrDefault(actionJson.optString("type"), WorkflowActionType.RequestUserConfirmation),
                        variableKey = actionJson.optNullableString("variableKey"),
                        fixedArgument = actionJson.optNullableString("fixedArgument"),
                    ),
                    target = item.optJSONObject("target")?.toTargetBundle(),
                    preconditions = item.optJSONArray("preconditions").toStateChecks(),
                    postconditions = item.optJSONArray("postconditions").toStateChecks(),
                    retryPolicy = WorkflowRetryPolicy(
                        maxAttempts = retryJson.optInt("maxAttempts", 1).coerceIn(0, 5),
                        delayMs = retryJson.optLong("delayMs", 600L).coerceIn(0L, 30_000L),
                    ),
                    riskLevel = enumValueOrDefault(item.optString("riskLevel"), WorkflowRiskLevel.Low),
                    confirmationPolicy = enumValueOrDefault(
                        item.optString("confirmationPolicy"),
                        WorkflowConfirmationPolicy.OnRisk,
                    ),
                ),
            )
        }
    }

    private fun JSONObject.toTargetBundle(): TargetSelectorBundle = TargetSelectorBundle(
        candidates = optJSONArray("candidates").toSelectorCandidates(),
        minimumScore = optDouble("minimumScore", 0.72).toFloat().coerceIn(0f, 1f),
        coordinateFallbackAllowed = optBoolean("coordinateFallbackAllowed", false),
    )

    private fun JSONArray?.toSelectorCandidates(): List<TargetSelectorCandidate> = buildList {
        val array = this@toSelectorCandidates ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val value = item.optString("value").trim()
            if (value.isBlank()) continue
            add(
                TargetSelectorCandidate(
                    kind = enumValueOrDefault(item.optString("kind"), WorkflowSelectorKind.RecordedBounds),
                    value = value,
                    weight = item.optDouble("weight", 1.0).toFloat().coerceIn(0f, 1f),
                    packageName = item.optNullableString("packageName"),
                    role = item.optNullableString("role"),
                    ancestorHint = item.optNullableString("ancestorHint"),
                ),
            )
        }
    }

    private fun JSONArray?.toStateChecks(): List<WorkflowStateCheck> = buildList {
        val array = this@toStateChecks ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            add(
                WorkflowStateCheck(
                    id = id,
                    type = enumValueOrDefault(item.optString("type"), WorkflowStateCheckType.UserConfirmed),
                    expectedValue = item.optString("expectedValue"),
                    packageName = item.optNullableString("packageName"),
                    timeoutMs = item.optLong("timeoutMs", 8_000L).coerceIn(0L, 60_000L),
                    required = item.optBoolean("required", true),
                ),
            )
        }
    }

    private fun JSONObject?.toRiskPolicy(): WorkflowRiskPolicy {
        val source = this ?: return WorkflowRiskPolicy()
        return WorkflowRiskPolicy(
            maximumAllowedRisk = enumValueOrDefault(
                source.optString("maximumAllowedRisk"),
                WorkflowRiskLevel.Medium,
            ),
            requireConfirmationForHighRisk = source.optBoolean("requireConfirmationForHighRisk", true),
            blockPasswordCapture = source.optBoolean("blockPasswordCapture", true),
            blockOtpCapture = source.optBoolean("blockOtpCapture", true),
            blockPaymentConfirmation = source.optBoolean("blockPaymentConfirmation", true),
        )
    }

    private fun JSONObject?.toRecoveryPolicy(): WorkflowRecoveryPolicy {
        val source = this ?: return WorkflowRecoveryPolicy()
        return WorkflowRecoveryPolicy(
            mode = enumValueOrDefault(
                source.optString("mode"),
                WorkflowRecoveryMode.StopAndAsk,
            ),
            maximumAutomaticRetries = source.optInt("maximumAutomaticRetries", 1).coerceIn(0, 5),
            allowRouteMutation = source.optBoolean("allowRouteMutation", false),
        )
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) {
        null
    } else {
        optString(key).trim().takeIf(String::isNotBlank)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private const val SCHEMA_VERSION = 1
}
