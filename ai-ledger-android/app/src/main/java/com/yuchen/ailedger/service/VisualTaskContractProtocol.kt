package com.yuchen.ailedger.service

/**
 * Structural transaction boundary between GUI Plus and Android.
 *
 * This validator never interprets page text, app-specific words, visual meaning, or user intent.
 * GUI Plus owns every semantic decision. Android only verifies that the cloud decision arrived as a
 * complete, internally consistent transaction before any work-surface action is executed.
 */
internal object VisualTaskContractProtocol {
    const val PROMPT_LINE =
        "visual_task_contract_protocol:v2|requiredOnWorkSurface=true|minMilestones=2|fullOrderedContract=true|singleGoalForbidden=true|actionIntentRequired=true|currentMilestoneBindingRequired=true|provisionalFinishCannotCommit=true|completionCommitOwner=android_fresh_screen_ack"

    data class Decision(
        val accepted: Boolean,
        val code: String,
        val message: String,
    ) {
        companion object {
            val Accepted = Decision(true, "accepted", "Visual task transaction is structurally valid.")
        }
    }

    fun validate(
        plan: CloudAgentPlan,
        committedContract: VisualTaskContract?,
        runtime: VisualAgentRuntimeContext,
    ): Decision {
        val step = plan.step
        if (step.type == "open_app" && runtime.surfaceState != VisualSurfaceState.WorkSurface) {
            return Decision.Accepted
        }

        val contract = plan.taskContract ?: committedContract
            ?: return reject(
                code = "task_contract_required",
                message = "GUI Plus must provide a complete ordered task contract before a work-surface action can execute.",
            )

        validateContract(contract).takeUnless { it.accepted }?.let { return it }
        validateActionIntent(
            actionType = step.type,
            purpose = step.purpose.orEmpty(),
            milestoneId = step.milestoneId.orEmpty(),
            expectedEvidence = step.expectedEvidence,
            contract = contract,
        ).takeUnless { it.accepted }?.let { return it }
        validateTransition(committedContract, plan.taskContract).takeUnless { it.accepted }?.let { return it }
        return Decision.Accepted
    }

    fun validateContract(contract: VisualTaskContract): Decision {
        if (contract.legacyMode) {
            return reject("legacy_contract_forbidden", "Legacy task contracts cannot authorize work-surface execution.")
        }
        if (contract.originalGoal.isBlank()) {
            return reject("contract_goal_required", "The task contract must preserve the original goal.")
        }
        if (contract.milestones.size < 2) {
            return reject(
                "ordered_milestones_required",
                "The task contract must contain at least an execution milestone and an independent verification milestone.",
            )
        }
        val ids = contract.milestones.map { it.id.trim() }
        if (ids.any(String::isBlank) || ids.distinct().size != ids.size) {
            return reject("milestone_ids_invalid", "Task-contract milestone IDs must be non-empty and unique.")
        }
        val currentIndex = ids.indexOf(contract.currentMilestoneId)
        if (currentIndex < 0) {
            return reject("current_milestone_missing", "currentMilestoneId must reference an ordered milestone.")
        }
        val completed = contract.completedMilestoneIds.toSet() +
            contract.milestones.filter { it.completed }.map { it.id }
        if (!ids.containsAll(completed)) {
            return reject("completed_milestone_unknown", "completedMilestoneIds must be a subset of milestone IDs.")
        }
        if (contract.currentMilestoneId in completed) {
            return reject(
                "current_milestone_already_completed",
                "currentMilestoneId must point to the next unfinished milestone, never an already completed one.",
            )
        }
        val unfinishedPrior = ids.take(currentIndex).firstOrNull { it !in completed }
        if (unfinishedPrior != null) {
            return reject(
                "current_milestone_skips_unfinished_prior",
                "An ordered task contract cannot advance past an unfinished prior milestone.",
            )
        }
        if (contract.milestones.any { it.title.isBlank() && it.purpose.isBlank() }) {
            return reject("milestone_description_required", "Every milestone must have a title or purpose supplied by GUI Plus.")
        }
        if (contract.milestones.any { it.successEvidence.isEmpty() }) {
            return reject("milestone_evidence_required", "Every milestone must declare visible success evidence for GUI Plus.")
        }
        return validateTransition(VisualCommittedTaskContractRuntime.currentOrNull(), contract)
    }

    fun validateActionIntent(
        actionType: String,
        purpose: String,
        milestoneId: String,
        expectedEvidence: List<String>,
        contract: VisualTaskContract?,
    ): Decision {
        if (purpose.isBlank()) {
            return reject(
                code = "action_purpose_required",
                message = "Every work-surface action must carry its GUI Plus actionIntent purpose.",
            )
        }
        if (milestoneId.isBlank()) {
            return reject(
                code = "action_milestone_required",
                message = "Every work-surface action must bind to the current task-contract milestone.",
            )
        }
        if (expectedEvidence.isEmpty()) {
            return reject(
                code = "action_expected_evidence_required",
                message = "Every work-surface action must declare the evidence GUI Plus expects to inspect next.",
            )
        }
        if (contract != null && milestoneId != contract.currentMilestoneId) {
            return reject(
                code = "action_milestone_mismatch",
                message = "The action milestone does not match the contract currentMilestoneId.",
            )
        }
        if (actionType.isBlank()) {
            return reject("action_type_required", "A work-surface transaction must contain one explicit action type.")
        }
        return Decision.Accepted
    }

    fun validateTransition(
        previous: VisualTaskContract?,
        incoming: VisualTaskContract?,
    ): Decision {
        if (previous == null || incoming == null) return Decision.Accepted
        if (incoming.taskRevision > previous.taskRevision) return Decision.Accepted

        val previousIds = previous.milestones.map { it.id }
        val incomingIds = incoming.milestones.map { it.id }
        val retainedOrder = incomingIds.filter { it in previousIds }
        if (retainedOrder != previousIds) {
            return reject(
                "contract_history_rewritten",
                "A same-revision task-contract update cannot delete or reorder committed milestones.",
            )
        }
        val previousCompleted = previous.completedMilestoneIds.toSet() +
            previous.milestones.filter { it.completed }.map { it.id }
        val incomingCompleted = incoming.completedMilestoneIds.toSet() +
            incoming.milestones.filter { it.completed }.map { it.id }
        if (!incomingCompleted.containsAll(previousCompleted)) {
            return reject(
                "completed_milestone_rollback",
                "A same-revision task-contract update cannot roll back committed milestones.",
            )
        }
        return Decision.Accepted
    }

    private fun reject(code: String, message: String): Decision = Decision(false, code, message)
}

/**
 * Task-scoped committed contract ledger used only for structural transition validation.
 * It stores cloud-authored data verbatim and never creates or infers a milestone.
 */
internal object VisualCommittedTaskContractRuntime {
    private val lock = Any()
    private var taskId: Long = 0L
    private var committed: VisualTaskContract? = null

    fun currentOrNull(): VisualTaskContract? {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return null
        return synchronized(lock) {
            if (taskId != currentTaskId) {
                taskId = currentTaskId
                committed = null
            }
            committed
        }
    }

    fun commit(contract: VisualTaskContract) {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return
        synchronized(lock) {
            if (taskId != currentTaskId) {
                taskId = currentTaskId
                committed = null
            }
            committed = contract
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            taskId = 0L
            committed = null
        }
    }

    private fun currentTaskIdOrZero(): Long =
        runCatching { AgentRuntimeController.currentTaskId() }.getOrDefault(0L)
}
