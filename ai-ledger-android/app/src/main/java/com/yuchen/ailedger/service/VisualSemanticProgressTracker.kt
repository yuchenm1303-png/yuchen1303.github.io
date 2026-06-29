package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structural execution ledger for the GUI Plus loop.
 *
 * Android never invents page meaning or task evidence. It preserves cloud-declared milestones,
 * authoritative user updates and action hypotheses, then attaches only objective execution facts.
 */
enum class VisualSemanticProgressStatus(val wireValue: String) {
    Advanced("advanced"),
    Stalled("stalled"),
    Regressed("regressed"),
    Ambiguous("ambiguous"),
}

data class VisualSemanticProgressResult(
    val status: VisualSemanticProgressStatus,
    val actionSignature: String,
    val milestoneId: String,
    val pageChanged: Boolean,
    val packageChanged: Boolean,
    val expectedEvidenceMatched: List<String>,
    val failureEvidenceMatched: List<String>,
    val newEvidence: List<String>,
    val failedHypothesisCount: Int,
    val explorationBudgetRemaining: Int,
    val requiresStrategyChange: Boolean,
    val requiresReplan: Boolean,
    val reobserveRecommended: Boolean,
    val shouldPauseForUser: Boolean,
    val structuralRegression: Boolean,
    val reason: String,
    val taskMemory: VisualTaskMemory,
) {
    fun toFeedbackLine(step: CloudAgentStep): String {
        val prefix = if (structuralRegression) {
            "visual_action_rejected:type=${step.type}|failureClass=structural_route"
        } else {
            "visual_execution_observed:action=$actionSignature"
        }
        return buildString {
            append(prefix)
            append("|screenChanged=").append(pageChanged)
            append("|packageChanged=").append(packageChanged)
            append("|structuralRegression=").append(structuralRegression)
            append("|milestone=").append(milestoneId.take(80))
            append("|failedHypothesisCount=").append(failedHypothesisCount)
            append("|explorationBudgetRemaining=").append(explorationBudgetRemaining)
            append("|semanticDecisionOwner=gui_plus")
            append("|localSemanticDecision=false")
            append("|requiresStrategyChange=").append(requiresStrategyChange)
            append("|replanRequired=").append(requiresReplan)
            append("|reason=").append(reason.take(220))
        }.take(MAX_FEEDBACK_LINE_CHARS)
    }

    companion object {
        private const val MAX_FEEDBACK_LINE_CHARS = 1_200
    }
}

class VisualSemanticProgressTracker(
    private val originalGoal: String = "",
    private val defaultExplorationBudgetPerMilestone: Int = VisualTaskContract.DEFAULT_EXPLORATION_BUDGET,
    private val legacyExplorationBudgetPerMilestone: Int = 1,
    @Suppress("unused") private val maxConsecutiveAmbiguousTransitions: Int = 2,
) {
    private var taskContract: VisualTaskContract? = null
    private var currentMilestoneId: String = DEFAULT_MILESTONE_ID
    private val completedMilestoneIds = linkedSetOf<String>()
    private var currentPage: VisualPageState? = null
    private var lastConfirmedPage: VisualPageState? = null
    private var lastProgressStatus: String = "unknown"
    private var structuralReplanRequested: Boolean = false
    private var remainingExplorationBudget: Int = legacyExplorationBudgetPerMilestone.coerceAtLeast(1)
    private val confirmedFacts = linkedSetOf<String>()
    private val failedHypotheses = linkedMapOf<String, VisualFailedHypothesis>()
    private val blockedActions = linkedMapOf<String, VisualBlockedAction>()
    private var taskRevision: Int = 0
    private var taskRevisionPending: Boolean = false
    private var currentMilestoneInvalidated: Boolean = false
    private var latestUserUpdate: VisualUserTaskUpdate? = null
    private val userUpdateHistory = mutableListOf<VisualUserTaskUpdate>()

    fun updateTaskContract(contract: VisualTaskContract?, fallbackGoal: String = originalGoal) {
        if (contract == null) return
        syncRuntimeUserUpdates()
        val previousContract = taskContract
        val previousMilestoneId = currentMilestoneId
        val normalized = mergeTaskContracts(previousContract, contract, fallbackGoal)
        taskContract = normalized
        currentMilestoneId = normalized.currentMilestoneId.ifBlank { DEFAULT_MILESTONE_ID }
        completedMilestoneIds += normalized.completedMilestoneIds
        completedMilestoneIds += normalized.milestones.filter { it.completed }.map { it.id }
        completedMilestoneIds.forEach { id -> addConfirmedFact("cloud_milestone_completed:$id") }

        val configuredBudget = normalized.explorationBudgetPerMilestone.coerceIn(1, 4)
        remainingExplorationBudget = if (previousContract == null || previousMilestoneId != currentMilestoneId) {
            configuredBudget
        } else {
            remainingExplorationBudget.coerceIn(0, configuredBudget)
        }

        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "task_contract_update",
            details = JSONObject().apply {
                put("currentMilestoneId", currentMilestoneId)
                put("milestoneCount", normalized.milestones.size)
                put("completedMilestoneCount", completedMilestoneIds.size)
                put("explorationBudgetPerMilestone", normalized.explorationBudgetPerMilestone)
                put("remainingExplorationBudget", remainingExplorationBudget)
                put("taskRevision", taskRevision)
                put("contractTaskRevision", normalized.taskRevision)
                put("taskRevisionPending", taskRevisionPending)
                put("taskContract", normalized.toJson())
            },
        )
    }

    fun applyUserUpdate(update: VisualUserTaskUpdate): VisualUserTaskUpdate {
        val nextRevision = update.revision.takeIf { it > taskRevision } ?: (taskRevision + 1)
        return applyUserUpdateInternal(update.copy(revision = nextRevision))
    }

    fun acknowledgeUserUpdatePlan(step: CloudAgentStep) {
        VisualUserTaskUpdateRuntime.markDispatchedPlanValidated()
        syncRuntimeUserUpdates()
        consumeAcceptedRevisionIfPossible(step)
    }

    fun onVerifiedSurface(snapshot: AgentScreenSnapshot) {
        syncRuntimeUserUpdates()
        currentPage = structuralPage(snapshot)
        lastConfirmedPage = currentPage
        structuralReplanRequested = taskRevisionPending
        lastProgressStatus = if (taskRevisionPending) "user_update_pending_replan" else "surface_verified"
        addConfirmedFact("verified_surface:${snapshot.packageName.take(120)}:${currentPage?.id.orEmpty()}")
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "surface_verified",
            details = JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("surfaceId", currentPage?.id.orEmpty())
                put("hasVisualFrame", snapshot.visual?.hasImage == true)
                put("taskRevisionPending", taskRevisionPending)
            },
        )
    }

    fun resetAfterUserTakeover(snapshot: AgentScreenSnapshot? = null) {
        syncRuntimeUserUpdates()
        structuralReplanRequested = taskRevisionPending
        lastProgressStatus = if (taskRevisionPending) "user_update_pending_replan" else "user_takeover_reset"
        blockedActions.clear()
        snapshot?.let {
            currentPage = structuralPage(it)
            lastConfirmedPage = currentPage
        }
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "user_takeover_memory_reset",
            details = JSONObject().apply {
                put("packageName", snapshot?.packageName.orEmpty())
                put("surfaceId", currentPage?.id.orEmpty())
                put("blockedActionsCleared", true)
                put("taskRevision", taskRevision)
                put("taskRevisionPending", taskRevisionPending)
            },
        )
    }

    /** Only an exact cloud-declared hypothesis on the same structural page can be blocked. */
    fun blockedHypothesisReason(step: CloudAgentStep, snapshot: AgentScreenSnapshot): String? {
        syncRuntimeUserUpdates()
        if (taskRevisionPending) return null
        val intent = step.actionIntent
        if (!intent.hasSemanticContract) return null
        val hypothesisId = intent.hypothesisId.trim()
        if (hypothesisId.isBlank()) return null
        val milestoneId = intent.milestoneId.ifBlank { currentMilestoneId }
        val pageId = structuralPage(snapshot).id
        val cluster = VisualActionValidator.actionClusterSignature(step)
        return blockedActions.values.firstOrNull {
            it.milestoneId == milestoneId &&
                it.pageStateId == pageId &&
                it.actionCluster == cluster &&
                it.hypothesisId == hypothesisId
        }?.reason
    }

    fun evaluate(
        step: CloudAgentStep,
        before: AgentScreenSnapshot,
        after: AgentScreenSnapshot,
        verifiedTargetPackage: String,
        structuralRegressionConfirmed: Boolean = false,
    ): VisualSemanticProgressResult {
        syncRuntimeUserUpdates()
        val beforeFingerprint = VisualActionValidator.snapshotFingerprint(before)
        val afterFingerprint = VisualActionValidator.snapshotFingerprint(after)
        val pageChanged = beforeFingerprint != afterFingerprint
        val packageChanged = before.packageName != after.packageName
        val structuralRegression = structuralRegressionConfirmed && verifiedTargetPackage.isNotBlank()
        val stepMilestoneId = step.milestoneId?.trim().orEmpty()
        if (stepMilestoneId.isNotBlank()) currentMilestoneId = stepMilestoneId.take(100)

        val status = when {
            structuralRegression -> VisualSemanticProgressStatus.Regressed
            pageChanged -> VisualSemanticProgressStatus.Advanced
            else -> VisualSemanticProgressStatus.Stalled
        }
        currentPage = structuralPage(after)
        if (!structuralRegression) lastConfirmedPage = currentPage

        val failure = if (structuralRegression || !pageChanged) {
            recordObjectiveFailure(
                step = step,
                page = structuralPage(before),
                reason = if (structuralRegression) "verified_work_surface_lost" else "screen_structure_unchanged",
            )
        } else null
        if (pageChanged) {
            addConfirmedFact(
                "screen_transition:${structuralPage(before).id}->${currentPage?.id.orEmpty()}:action=${VisualActionValidator.actionSignature(step).take(120)}",
            )
        }

        val repeatedFailure = (failure?.count ?: 0) >= BLOCK_AFTER_FAILURE_COUNT
        val budgetExhausted = remainingExplorationBudget <= 0
        val requiresStrategyChange = structuralRegression || repeatedFailure || budgetExhausted || taskRevisionPending
        val requiresReplan = requiresStrategyChange
        structuralReplanRequested = requiresReplan
        lastProgressStatus = when {
            taskRevisionPending -> "user_update_pending_replan"
            structuralRegression -> "structural_regression"
            pageChanged -> "screen_changed_unjudged"
            repeatedFailure -> "repeated_hypothesis_failure"
            budgetExhausted -> "exploration_budget_exhausted"
            else -> "screen_unchanged_unjudged"
        }

        val reason = when {
            taskRevisionPending -> "A newer authoritative user task revision is pending; the previous semantic route is invalid."
            structuralRegression -> "The Android-verified target package was lost after consecutive foreign-package evidence."
            pageChanged -> "The observed screen structure changed. GUI Plus exclusively decides whether this is task progress."
            repeatedFailure -> "The same GUI Plus hypothesis produced the same unchanged structural outcome repeatedly on this page; choose a different hypothesis."
            budgetExhausted -> "The cloud-declared milestone exploration budget is exhausted; GUI Plus must replan instead of widening exploration."
            else -> "The observed screen structure remained stable. GUI Plus exclusively decides the next action."
        }
        val memory = memorySnapshot()
        val result = VisualSemanticProgressResult(
            status = status,
            actionSignature = VisualActionValidator.actionSignature(step),
            milestoneId = currentMilestoneId,
            pageChanged = pageChanged,
            packageChanged = packageChanged,
            expectedEvidenceMatched = emptyList(),
            failureEvidenceMatched = emptyList(),
            newEvidence = emptyList(),
            failedHypothesisCount = memory.failedHypotheses.size,
            explorationBudgetRemaining = memory.remainingExplorationBudget,
            requiresStrategyChange = requiresStrategyChange,
            requiresReplan = requiresReplan,
            reobserveRecommended = !pageChanged && !repeatedFailure && !budgetExhausted,
            shouldPauseForUser = false,
            structuralRegression = structuralRegression,
            reason = reason,
            taskMemory = memory,
        )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "semantic_progress",
            details = JSONObject().apply {
                put("stepType", step.type)
                put("actionSignature", result.actionSignature)
                put("status", result.status.wireValue)
                put("beforePackage", before.packageName)
                put("afterPackage", after.packageName)
                put("verifiedTargetPackage", verifiedTargetPackage)
                put("beforeFingerprint", beforeFingerprint)
                put("afterFingerprint", afterFingerprint)
                put("pageChanged", pageChanged)
                put("packageChanged", packageChanged)
                put("structuralRegressionConfirmed", structuralRegressionConfirmed)
                put("structuralRegression", structuralRegression)
                put("failedHypothesis", failure?.toJson() ?: JSONObject.NULL)
                put("failedHypothesisCount", result.failedHypothesisCount)
                put("blockedActions", JSONArray().apply { memory.blockedActions.forEach { put(it.toJson()) } })
                put("explorationBudgetRemaining", result.explorationBudgetRemaining)
                put("requiresStrategyChange", result.requiresStrategyChange)
                put("requiresReplan", result.requiresReplan)
                put("taskRevision", taskRevision)
                put("taskRevisionPending", taskRevisionPending)
                put("reason", reason)
                put("beforeHasVisual", before.visual?.hasImage == true)
                put("afterHasVisual", after.visual?.hasImage == true)
            },
        )
        return result
    }

    fun memorySnapshot(snapshot: AgentScreenSnapshot? = null): VisualTaskMemory {
        syncRuntimeUserUpdates()
        consumeAcceptedRevisionIfPossible()
        snapshot?.let { currentPage = structuralPage(it) }
        val contract = taskContract
        val neutralBudget = if (contract == null) {
            remainingExplorationBudget.coerceAtLeast(1)
        } else {
            remainingExplorationBudget.coerceIn(0, contract.explorationBudgetPerMilestone.coerceIn(1, 4))
        }
        val replan = structuralReplanRequested || taskRevisionPending
        return VisualTaskMemory(
            originalGoal = contract?.originalGoal?.ifBlank { originalGoal } ?: originalGoal,
            currentMilestoneId = currentMilestoneId,
            completedMilestoneIds = completedMilestoneIds.toList().takeLast(MAX_MEMORY_ITEMS),
            currentPage = currentPage,
            confirmedFacts = confirmedFacts.toList().takeLast(MAX_MEMORY_ITEMS),
            failedHypotheses = failedHypotheses.values.toList().takeLast(MAX_MEMORY_ITEMS),
            blockedActions = blockedActions.values.toList().takeLast(MAX_MEMORY_ITEMS),
            remainingExplorationBudget = neutralBudget,
            lastConfirmedPage = lastConfirmedPage,
            progressStatus = lastProgressStatus,
            replanRequested = replan,
            recoveryMode = replan,
            legacyMode = contract == null,
            taskContract = contract?.copy(taskRevision = maxOf(contract.taskRevision, taskRevision)),
            taskRevision = taskRevision,
            taskRevisionPending = taskRevisionPending,
            currentMilestoneInvalidated = currentMilestoneInvalidated,
            latestUserUpdate = latestUserUpdate,
            userUpdateHistory = userUpdateHistory.takeLast(MAX_USER_UPDATES),
        )
    }

    private fun syncRuntimeUserUpdates() {
        VisualUserTaskUpdateRuntime.updatesAfter(taskRevision)
            .sortedBy { it.revision }
            .forEach(::applyUserUpdateInternal)
    }

    private fun applyUserUpdateInternal(update: VisualUserTaskUpdate): VisualUserTaskUpdate {
        if (update.revision <= taskRevision) return latestUserUpdate ?: update
        taskRevision = update.revision
        latestUserUpdate = update
        userUpdateHistory += update
        while (userUpdateHistory.size > MAX_USER_UPDATES) userUpdateHistory.removeAt(0)
        taskRevisionPending = true
        currentMilestoneInvalidated = currentMilestoneInvalidated || update.invalidatesCurrentMilestone
        structuralReplanRequested = true
        lastProgressStatus = "user_update_${update.kind.wireValue}_pending"
        blockedActions.clear()
        if (update.invalidatesCurrentMilestone) {
            failedHypotheses.clear()
            remainingExplorationBudget = taskContract?.explorationBudgetPerMilestone
                ?.coerceIn(1, 4)
                ?: defaultExplorationBudgetPerMilestone.coerceAtLeast(1)
        }
        addConfirmedFact("user_task_update:revision=${update.revision}:kind=${update.kind.wireValue}")
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "user_task_update",
            details = JSONObject().apply {
                put("revision", update.revision)
                put("kind", update.kind.wireValue)
                put("sourceReason", update.sourceReason)
                put("invalidatesCurrentMilestone", update.invalidatesCurrentMilestone)
                put("invalidatesVisualHistory", update.invalidatesVisualHistory)
                put("manualStepCompleted", update.manualStepCompleted)
                put("contentStoredInTaskMemory", true)
            },
        )
        return update
    }

    private fun consumeAcceptedRevisionIfPossible(step: CloudAgentStep? = null) {
        if (!taskRevisionPending || VisualUserTaskUpdateRuntime.isRevisionPending(taskRevision)) return
        taskRevisionPending = false
        currentMilestoneInvalidated = false
        structuralReplanRequested = false
        lastProgressStatus = "user_update_consumed_by_plan"
        step?.milestoneId?.trim()?.takeIf(String::isNotBlank)?.let { currentMilestoneId = it.take(100) }
        taskContract = taskContract?.copy(taskRevision = maxOf(taskContract?.taskRevision ?: 0, taskRevision))
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "user_task_update_consumed",
            details = JSONObject().apply {
                put("taskRevision", taskRevision)
                put("stepType", step?.type.orEmpty())
                put("stepMilestoneId", step?.milestoneId.orEmpty())
            },
        )
    }

    private fun recordObjectiveFailure(
        step: CloudAgentStep,
        page: VisualPageState,
        reason: String,
    ): VisualFailedHypothesis? {
        if (taskRevisionPending) return null
        val intent = step.actionIntent
        if (!intent.hasSemanticContract) return null
        val hypothesisId = intent.hypothesisId.trim()
        if (hypothesisId.isBlank()) return null
        val milestoneId = intent.milestoneId.ifBlank { currentMilestoneId }.take(100)
        val actionSignature = VisualActionValidator.actionSignature(step)
        val actionCluster = VisualActionValidator.actionClusterSignature(step)
        val key = listOf(milestoneId, page.id, hypothesisId, actionCluster).joinToString("|")
        val previous = failedHypotheses[key]
        val updated = VisualFailedHypothesis(
            hypothesisId = hypothesisId.take(120),
            milestoneId = milestoneId,
            pageStateId = page.id,
            actionSignature = actionSignature.take(240),
            actionCluster = actionCluster.take(240),
            purpose = intent.purpose.take(240),
            failureReason = reason.take(240),
            count = (previous?.count ?: 0) + 1,
        )
        failedHypotheses[key] = updated
        trimMap(failedHypotheses)
        if (intent.exploratory) remainingExplorationBudget = (remainingExplorationBudget - 1).coerceAtLeast(0)
        if (updated.count >= BLOCK_AFTER_FAILURE_COUNT) {
            blockedActions[key] = VisualBlockedAction(
                milestoneId = milestoneId,
                pageStateId = page.id,
                actionCluster = actionCluster.take(240),
                hypothesisId = hypothesisId.take(120),
                reason = "Repeated objective failure for the same GUI Plus hypothesis on the same structural page; replan with a different hypothesis.",
            )
            trimMap(blockedActions)
        }
        return updated
    }

    private fun mergeTaskContracts(
        previous: VisualTaskContract?,
        incoming: VisualTaskContract,
        fallbackGoal: String,
    ): VisualTaskContract {
        val milestones = linkedMapOf<String, VisualTaskMilestone>()
        fun merge(item: VisualTaskMilestone) {
            val old = milestones[item.id]
            milestones[item.id] = if (old == null) item else VisualTaskMilestone(
                id = item.id,
                title = item.title.ifBlank { old.title },
                purpose = item.purpose.ifBlank { old.purpose },
                successEvidence = (old.successEvidence + item.successEvidence).distinct().take(16),
                failureEvidence = (old.failureEvidence + item.failureEvidence).distinct().take(16),
                completed = old.completed || item.completed,
            )
        }
        previous?.milestones?.forEach(::merge)
        incoming.milestones.forEach(::merge)
        val completed = (
            previous?.completedMilestoneIds.orEmpty() +
                incoming.completedMilestoneIds +
                milestones.values.filter { it.completed }.map { it.id }
            ).distinct().take(MAX_MEMORY_ITEMS)
        val requestedCurrent = incoming.currentMilestoneId.ifBlank { previous?.currentMilestoneId.orEmpty() }
        val current = requestedCurrent.ifBlank {
            milestones.values.firstOrNull { it.id !in completed }?.id ?: DEFAULT_MILESTONE_ID
        }
        return VisualTaskContract(
            originalGoal = previous?.originalGoal?.takeIf(String::isNotBlank)
                ?: incoming.originalGoal.ifBlank { fallbackGoal }.take(240),
            currentMilestoneId = current.take(100),
            milestones = milestones.values.toList().take(MAX_MEMORY_ITEMS),
            completedMilestoneIds = completed,
            explorationBudgetPerMilestone = incoming.explorationBudgetPerMilestone.coerceIn(1, 4),
            schema = incoming.schema.ifBlank { previous?.schema.orEmpty() }.ifBlank { "visual_task_contract_v1" }.take(80),
            legacyMode = previous?.let { it.legacyMode && incoming.legacyMode } ?: incoming.legacyMode,
            taskRevision = maxOf(previous?.taskRevision ?: 0, incoming.taskRevision, taskRevision),
        )
    }

    private fun addConfirmedFact(value: String) {
        val clean = value.trim().take(240)
        if (clean.isBlank()) return
        confirmedFacts += clean
        while (confirmedFacts.size > MAX_MEMORY_ITEMS) confirmedFacts.remove(confirmedFacts.first())
    }

    private fun <T> trimMap(map: LinkedHashMap<String, T>) {
        while (map.size > MAX_MEMORY_ITEMS) map.remove(map.keys.first())
    }

    private fun structuralPage(snapshot: AgentScreenSnapshot): VisualPageState {
        val fingerprint = VisualActionValidator.completionFingerprint(snapshot)
        return VisualPageState(
            id = "surface-${Integer.toHexString(fingerprint.hashCode())}",
            packageName = snapshot.packageName.take(120),
            summary = "",
        )
    }

    companion object {
        private const val DEFAULT_MILESTONE_ID = "work_surface"
        private const val MAX_MEMORY_ITEMS = 24
        private const val MAX_USER_UPDATES = 8
        private const val BLOCK_AFTER_FAILURE_COUNT = 2
    }
}
