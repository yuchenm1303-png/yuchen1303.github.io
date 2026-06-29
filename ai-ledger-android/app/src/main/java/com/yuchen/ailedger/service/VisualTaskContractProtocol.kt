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

        validateContract(contract)?.let { return it }

        val milestoneId = step.milestoneId?.trim().orEmpty()
        if (milestoneId.isBlank()) {
            return reject(
                code = "action_milestone_required",
                message = "Every work-surface action must bind to the current task-contract milestone.",
            )
        }
        if (milestoneId != contract.currentMilestoneId) {
            return reject(
                code = "action_milestone_mismatch",
                message = "The action milestone does not match the contract currentMilestoneId.",
            )
        }
        if (step.purpose.isNullOrBlank()) {
            return reject(
                code = "action_purpose_required",
                message = "Every work-surface action must carry its GUI Plus actionIntent purpose.",
            )
        }
        if (step.expectedEvidence.isEmpty()) {
            return reject(
                code = "action_expected_evidence_required",
                message = "Every work-surface action must declare the evidence GUI Plus expects to inspect next.",
            )
        }

        val current = contract.currentMilestone()
            ?: return reject("current_milestone_missing", "The current milestone is absent from the ordered contract.")
        val completed = contract.completedMilestoneIds.toSet() + contract.milestones.filter { it.completed }.map { it.id }
        if (step.type != "finish" && current.id in completed) {
            return reject(
                code = "current_milestone_already_completed",
                message = "A non-finish action cannot remain bound to an already completed current milestone.",
            )
        }
        if (step.type == "finish") {
            val unfinishedBeforeCurrent = contract.milestones
                .takeWhile { it.id != current.id }
                .any { it.id !in completed }
            if (unfinishedBeforeCurrent) {
                return reject(
                    code = "finish_has_unfinished_prior_milestone",
                    message = "A completion candidate cannot skip an earlier ordered milestone.",
                )
            }
        }

        validateTransition(committedContract, plan.taskContract)?.let { return it }
        return Decision.Accepted
    }

    private fun validateContract(contract: VisualTaskContract): Decision? {
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
        if (contract.currentMilestoneId !in ids) {
            return reject("current_milestone_missing", "currentMilestoneId must reference an ordered milestone.")
        }
        val completed = contract.completedMilestoneIds.toSet()
        if (!ids.containsAll(completed)) {
            return reject("completed_milestone_unknown", "completedMilestoneIds must be a subset of milestone IDs.")
        }
        if (contract.milestones.any { it.title.isBlank() && it.purpose.isBlank() }) {
            return reject("milestone_description_required", "Every milestone must have a title or purpose supplied by GUI Plus.")
        }
        if (contract.milestones.any { it.successEvidence.isEmpty() }) {
            return reject("milestone_evidence_required", "Every milestone must declare visible success evidence for GUI Plus.")
        }
        return null
    }

    private fun validateTransition(
        previous: VisualTaskContract?,
        incoming: VisualTaskContract?,
    ): Decision? {
        if (previous == null || incoming == null) return null
        if (incoming.taskRevision > previous.taskRevision) return null

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
        return null
    }

    private fun reject(code: String, message: String): Decision = Decision(false, code, message)
}
