package com.yuchen.ailedger.service

import org.json.JSONObject

internal object VisualTaskContractParser {
    fun fromJson(
        root: JSONObject?,
        committedContract: VisualTaskContract?,
    ): VisualTaskContract? {
        root?.let { VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent("model_response", it) }
        root ?: return null
        if (VisualUserTaskUpdateRuntime.hasUndispatchedRevision()) return null

        val step = root.visualStep()
        val args = step?.optJSONObject("args") ?: step?.optJSONObject("arguments") ?: JSONObject()
        val intent = step?.optJSONObject("actionIntent") ?: args.optJSONObject("actionIntent") ?: JSONObject()
        val rejection = root.transactionRejectionReason()
        val workSurface = root.isWorkSurfaceResponse()

        if (rejection != null) {
            root.recordContractRejection(rejection)
            if (rejection == "provisional_completion_candidate") return null
            if (workSurface) root.failVisualProtocol(rejection, "Cloud response cannot mutate committed task state.")
            return null
        }

        val item = listOfNotNull(
            root.optJSONObject("taskContract"), root.optJSONObject("visualTaskContract"),
            root.optJSONObject("agentTaskContract"), root.optJSONObject("plan")?.optJSONObject("taskContract"),
            root.optJSONObject("data")?.optJSONObject("taskContract"),
            root.optJSONObject("result")?.optJSONObject("taskContract"),
            root.optJSONObject("agentMemory")?.optJSONObject("taskContract"),
        ).firstOrNull()

        if (item == null) {
            if (!workSurface) return null
            val committed = committedContract ?: root.failVisualProtocol(
                "task_contract_required",
                "GUI Plus must establish the full ordered task contract before the first work-surface action.",
            )
            VisualTaskContractProtocol.validateContract(committed).requireAccepted(root)
            root.validateVisualActionIntent(step, args, intent, committed)
            root.recordContractReuse(committed)
            return committed
        }

        val milestones = item.objectList("milestones", "steps")
            .mapNotNull(VisualTaskMilestone::fromJson).distinctBy { it.id }.take(24)
        val currentId = item.firstNonBlank("currentMilestoneId", "milestoneId", "currentMilestone")
            ?: milestones.firstOrNull { !it.completed }?.id.orEmpty()
        val contract = VisualTaskContract(
            originalGoal = item.firstNonBlank("originalGoal", "goal", "sourceGoal").orEmpty().take(240),
            currentMilestoneId = currentId.take(100),
            milestones = milestones,
            completedMilestoneIds = (item.stringList("completedMilestoneIds", "completedMilestones") +
                milestones.filter { it.completed }.map { it.id }).distinct().take(24),
            explorationBudgetPerMilestone = (item.optFlexibleInt("explorationBudgetPerMilestone")
                ?: item.optFlexibleInt("explorationBudget")
                ?: VisualTaskContract.DEFAULT_EXPLORATION_BUDGET).coerceIn(1, 4),
            schema = item.firstNonBlank("schema").orEmpty().ifBlank { "visual_task_contract_v1" }.take(80),
            legacyMode = item.flexibleBoolean("legacyMode") ?: false,
            taskRevision = (item.optFlexibleInt("taskRevision") ?: item.optFlexibleInt("userTaskRevision")
                ?: item.optFlexibleInt("revision") ?: 0).coerceAtLeast(0),
        )
        val decision = VisualTaskContractProtocol.validateContract(contract)
        if (!decision.accepted) {
            root.recordContractRejection(decision.code)
            if (workSurface) root.failVisualProtocol(decision.code, decision.message)
            return null
        }
        if (workSurface) root.validateVisualActionIntent(step, args, intent, contract)
        return contract
    }
}
