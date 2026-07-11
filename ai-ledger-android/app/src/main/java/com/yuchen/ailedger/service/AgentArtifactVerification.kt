package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

internal const val AGENT_ARTIFACT_VERIFICATION_SCHEMA = "ai_ledger_agent_artifact_verification_v1"

internal enum class AgentVerificationSeverity(val wireName: String) {
    Info("info"),
    Warning("warning"),
    Error("error"),
}

internal data class AgentVerificationIssue(
    val code: String,
    val severity: AgentVerificationSeverity,
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val suggestion: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("code", code)
        put("severity", severity.wireName)
        put("message", message)
        file?.let { put("file", it) }
        line?.let { put("line", it) }
        suggestion?.let { put("suggestion", it) }
    }
}

internal data class AgentArtifactVerificationReport(
    val domain: String,
    val workspaceId: String,
    val revisionId: String?,
    val issues: List<AgentVerificationIssue>,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val errorCount: Int get() = issues.count { it.severity == AgentVerificationSeverity.Error }
    val warningCount: Int get() = issues.count { it.severity == AgentVerificationSeverity.Warning }
    val infoCount: Int get() = issues.count { it.severity == AgentVerificationSeverity.Info }
    val passed: Boolean get() = errorCount == 0
    val status: String
        get() = when {
            errorCount > 0 -> "failed"
            warningCount > 0 -> "passed_with_warnings"
            else -> "passed"
        }

    fun summary(): String = when {
        errorCount > 0 -> "验证失败：发现 $errorCount 个错误、$warningCount 个警告。"
        warningCount > 0 -> "验证通过，但仍有 $warningCount 个警告需要检查。"
        else -> "验证通过，没有发现阻断问题。"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", AGENT_ARTIFACT_VERIFICATION_SCHEMA)
        put("domain", domain)
        put("workspaceId", workspaceId)
        revisionId?.let { put("revisionId", it) }
        put("status", status)
        put("passed", passed)
        put("errorCount", errorCount)
        put("warningCount", warningCount)
        put("infoCount", infoCount)
        put("generatedAt", generatedAt)
        put("summary", summary())
        put("issues", JSONArray().apply { issues.forEach { put(it.toJson()) } })
    }
}
