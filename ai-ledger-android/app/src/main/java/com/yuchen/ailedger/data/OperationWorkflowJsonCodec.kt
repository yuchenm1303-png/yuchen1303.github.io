package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.TargetSelectorBundle
import com.yuchen.ailedger.model.TargetSelectorCandidate
import com.yuchen.ailedger.model.WorkflowMilestone
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.model.WorkflowVariableDefinition
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

    private const val SCHEMA_VERSION = 1
}
