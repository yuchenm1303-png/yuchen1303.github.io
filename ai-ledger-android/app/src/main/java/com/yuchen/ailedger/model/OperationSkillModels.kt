package com.yuchen.ailedger.model

/**
 * 云端从一次视觉演示中提炼出的 Skill。
 *
 * 它描述“如何完成目标”，而不是保存固定控件、节点或坐标路线。运行时由视觉智能
 * 根据当前屏幕和本次输入重新决策，本地仅执行动作并守住权限与风险边界。
 */
data class LearnedVisualSkill(
    val schemaVersion: String = SCHEMA_VERSION,
    val workflowId: String,
    val name: String,
    val description: String,
    val triggerExamples: List<String> = emptyList(),
    val inputs: List<VisualSkillInput> = emptyList(),
    val operatingPrinciples: List<String> = emptyList(),
    val successCriteria: List<String> = emptyList(),
    val safetyRules: List<String> = emptyList(),
    val cloudSummary: String = "",
    val confidence: Float = 0f,
    val learnedAtMillis: Long,
) {
    companion object {
        const val SCHEMA_VERSION = "ai_ledger_visual_skill_v1"
    }
}

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
)

data class VisualDemonstrationManifest(
    val schemaVersion: String = "ai_ledger_visual_demonstration_v1",
    val demonstrationId: String,
    val workflowId: String,
    val workflowTitle: String,
    val goal: String,
    val allowedPackages: List<String>,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val frames: List<VisualDemonstrationFrame> = emptyList(),
)
