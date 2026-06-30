package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

data class VisualTaskMilestone(
    val id: String,
    val title: String = "",
    val purpose: String = "",
    val successEvidence: List<String> = emptyList(),
    val failureEvidence: List<String> = emptyList(),
    val completed: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("purpose", purpose)
        put("successEvidence", JSONArray(successEvidence)); put("failureEvidence", JSONArray(failureEvidence))
        put("completed", completed)
    }

    companion object {
        fun fromJson(value: JSONObject?): VisualTaskMilestone? {
            val item = value ?: return null
            val id = item.firstNonBlank("id", "milestoneId", "milestone") ?: return null
            return VisualTaskMilestone(
                id.take(100),
                item.firstNonBlank("title", "name").orEmpty().take(160),
                item.firstNonBlank("purpose", "goal", "description").orEmpty().take(240),
                item.stringList("successEvidence", "expectedEvidence", "evidence"),
                item.stringList("failureEvidence", "wrongEvidence", "negativeEvidence"),
                item.flexibleBoolean("completed") ?: item.flexibleBoolean("isComplete") ?: false,
            )
        }
    }
}

data class VisualTaskContract(
    val originalGoal: String = "",
    val currentMilestoneId: String = "",
    val milestones: List<VisualTaskMilestone> = emptyList(),
    val completedMilestoneIds: List<String> = emptyList(),
    val explorationBudgetPerMilestone: Int = DEFAULT_EXPLORATION_BUDGET,
    val schema: String = "visual_task_contract_v1",
    val legacyMode: Boolean = false,
    val taskRevision: Int = 0,
) {
    fun currentMilestone() = milestones.firstOrNull { it.id == currentMilestoneId }

    fun toJson() = JSONObject().apply {
        put("schema", schema); put("originalGoal", originalGoal); put("currentMilestoneId", currentMilestoneId)
        put("milestones", JSONArray().apply { milestones.forEach { put(it.toJson()) } })
        put("completedMilestoneIds", JSONArray(completedMilestoneIds))
        put("explorationBudgetPerMilestone", explorationBudgetPerMilestone)
        put("legacyMode", legacyMode); put("taskRevision", taskRevision)
    }

    companion object {
        const val DEFAULT_EXPLORATION_BUDGET = 2

        fun fromJson(
            root: JSONObject?,
            committedContract: VisualTaskContract? = VisualCommittedTaskContractRuntime.currentOrNull(),
        ): VisualTaskContract? = VisualTaskContractParser.fromJson(root, committedContract)
    }
}
