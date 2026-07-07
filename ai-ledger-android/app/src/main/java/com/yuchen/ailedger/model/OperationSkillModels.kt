package com.yuchen.ailedger.model

data class LearnedVisualSkill(
    val schemaVersion: String = SCHEMA_VERSION,
    val workflowId: String,
    val name: String,
    val description: String,
    val triggerExamples: List<String> = emptyList(),
    val inputs: List<VisualSkillInput> = emptyList(),
    val operatingPrinciples: List<String> = emptyList(),
    val routeSteps: List<VisualSkillRouteStep> = emptyList(),
    val successCriteria: List<String> = emptyList(),
    val safetyRules: List<String> = emptyList(),
    val cloudSummary: String = "",
    val confidence: Float = 0f,
    val learnedAtMillis: Long,
) {
    companion object {
        const val SCHEMA_VERSION = "ai_ledger_visual_skill_v2"
        const val LEGACY_SCHEMA_VERSION = "ai_ledger_visual_skill_v1"
    }
}

data class VisualSkillRouteStep(
    val order: Int,
    val instruction: String,
    val visualAnchor: String = "",
    val expectedEvidence: String = "",
    val fallback: String = "",
)

data class VisualSkillInput(
    val key: String,
    val label: String,
    val description: String = "",
    val required: Boolean = true,
    val sensitive: Boolean = false,
)

data class VisualDemonstrationFrame(
    val id: String,
    val capturedAtMillis: Long,
    val packageName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val encryptedFileName: String,
    val digest: String,
    val visualHash: String = "",
    val captureKind: String = "timed",
    val eventType: String = "",
    val eventIndex: Int = 0,
    val eventOccurredAtMillis: Long = 0L,
)

data class VisualDemonstrationManifest(
    val schemaVersion: String = "ai_ledger_visual_demonstration_v2",
    val demonstrationId: String,
    val workflowId: String,
    val workflowTitle: String,
    val goal: String,
    val allowedPackages: List<String>,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val frames: List<VisualDemonstrationFrame> = emptyList(),
)
