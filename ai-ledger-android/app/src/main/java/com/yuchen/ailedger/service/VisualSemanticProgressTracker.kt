package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Objective execution ledger for the GUI Plus loop.
 *
 * Android owns protocol integrity, package ownership, execution results and authoritative user task
 * revisions. It never infers page meaning, visual progress, success evidence or failed hypotheses.
 * GUI Plus receives the fresh screenshots and exclusively decides all visual semantics.
 */
enum class VisualSemanticProgressStatus(val wireValue: String) {
    Observed("observed"),
    Advanced("advanced"),
    Stalled("stalled"),
    Regressed("regressed"),
    Ambiguous("ambiguous"),
}

data class VisualSemanticProgressResult(
    val status: VisualSemanticProgressStatus,
    val actionSignature: String,
    val milestoneId: String,
    /** Objective frame-byte/structure difference only; never interpreted as progress. */
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
            append("|executionObserved=true")
            append("|frameChanged=").append(pageChanged)
            append("|packageChanged=").append(packageChanged)
            append("|structuralRegression=").append(structuralRegression)
            append("|milestone=").append(milestoneId.take(80))
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
                put("semanticDecisionOwner", "gui_plus")
                put("localProgressClassification", false)
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
        currentPage = structuralFrame(snapshot)
        lastConfirmedPage = currentPage
        structuralReplanRequested = taskRevisionPending
        lastProgressStatus = if (taskRevisionPending) "user_update_pending_replan" else "surface_verified"
        addConfirmedFact("verified_surface:${snapshot.packageName.take(120)}:${currentPage?.id.orEmpty()}")
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "surface_verified",
            details = JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("frameId", currentPage?.id.orEmpty())
                put("hasVisualFrame", snapshot.visual?.hasImage == true)
                put("taskRevisionPending", taskRevisionPending)
                put("semanticDecisionOwner", "gui_plus")
            },
        )
    }

    fun resetAfterUserTakeover(snapshot: AgentScreenSnapshot? = null) {
        syncRuntimeUserUpdates()
        structuralReplanRequested = taskRevisionPending
        lastProgressStatus = if (taskRevisionPending) "user_update_pending_replan" else "user_takeover_reset"
        snapshot?.let {
            currentPage = structuralFrame(it)
            lastConfirmedPage = currentPage
        }
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "user_takeover_memory_reset",
            details = JSONObject().apply {
                put("packageName", snapshot?.packageName.orEmpty())
                put("frameId", currentPage?.id.orEmpty())
                put("taskRevision", taskRevision)
                put("taskRevisionPending", taskRevisionPending)
                put("localSemanticStateCleared", true)
            },
        )
    }

    /** GUI Plus actions are never blocked by a locally inferred page/hypothesis rule. */
    fun blockedHypothesisReason(
        @Suppress("UNUSED_PARAMETER") step: CloudAgentStep,
        @Suppress("UNUSED_PARAMETER") snapshot: AgentScreenSnapshot,
    ): String? = null

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
        val frameChanged = beforeFingerprint != afterFingerprint
        val packageChanged = before.packageName != after.packageName
        val structuralRegression = structuralRegressionConfirmed && verifiedTargetPackage.isNotBlank()
        val stepMilestoneId = step.milestoneId?.trim().orEmpty()
        if (stepMilestoneId.isNotBlank()) currentMilestoneId = stepMilestoneId.take(100)

        currentPage = structuralFrame(after)
        if (!structuralRegression) lastConfirmedPage = currentPage
        val requiresReplan = structuralRegression || taskRevisionPending
        structuralReplanRequested = requiresReplan
        lastProgressStatus = when {
            taskRevisionPending -> "user_update_pending_replan"
            structuralRegression -> "structural_regression"
            else -> "execution_observed"
        }
        val reason = when {
            taskRevisionPending ->
                "A newer authoritative user task revision is pending; GUI Plus must plan from the fresh screenshot."
            structuralRegression ->
                "The Android-verified target package was lost after consecutive foreign-package evidence."
            else ->
                "Android recorded the execution and fresh frame only. GUI Plus exclusively decides visual progress and page meaning."
        }
        val memory = memorySnapshot()
        val result = VisualSemanticProgressResult(
            status = if (structuralRegression) VisualSemanticProgressStatus.Regressed else VisualSemanticProgressStatus.Observed,
            actionSignature = VisualActionValidator.actionSignature(step),
            milestoneId = currentMilestoneId,
            pageChanged = frameChanged,
            packageChanged = packageChanged,
            expectedEvidenceMatched = emptyList(),
            failureEvidenceMatched = emptyList(),
            newEvidence = emptyList(),
            failedHypothesisCount = 0,
            explorationBudgetRemaining = memory.remainingExplorationBudget,
            requiresStrategyChange = requiresReplan,
            requiresReplan = requiresReplan,
            reobserveRecommended = false,
            shouldPauseForUser = false,
            structuralRegression = structuralRegression,
            reason = reason,
            taskMemory = memory,
        )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "execution_observation_ledger",
            details = JSONObject().apply {
                put("stepType", step.type)
                put("actionSignature", result.actionSignature)
                put("status", result.status.wireValue)
                put("beforePackage", before.packageName)
                put("afterPackage", after.packageName)
                put("verifiedTargetPackage", verifiedTargetPackage)
                put("beforeFingerprint", beforeFingerprint)
                put("afterFingerprint", afterFingerprint)
                put("frameChanged", frameChanged)
                put("frameChangeInterpretedAsProgress", false)
                put("packageChanged", packageChanged)
                put("structuralRegressionConfirmed", structuralRegressionConfirmed)
                put("structuralRegression", structuralRegression)
                put("failedHypothesisCount", 0)
                put("blockedActionCount", 0)
                put("requiresReplan", requiresReplan)
                put("taskRevision", taskRevision)
                put("taskRevisionPending", taskRevisionPending)
                put("semanticDecisionOwner", "gui_plus")
                put("beforeHasVisual", before.visual?.hasImage == true)
                put("afterHasVisual", after.visual?.hasImage == true)
            },
        )
        return result
    }

    fun memorySnapshot(snapshot: AgentScreenSnapshot? = null): VisualTaskMemory {
        syncRuntimeUserUpdates()
        consumeAcceptedRevisionIfPossible()
        snapshot?.let { currentPage = structuralFrame(it) }
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
            failedHypotheses = emptyList(),
            blockedActions = emptyList(),
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
        if (update.invalidatesCurrentMilestone) {
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

    private fun structuralFrame(snapshot: AgentScreenSnapshot): VisualPageState {
        val fingerprint = VisualActionValidator.completionFingerprint(snapshot)
        return VisualPageState(
            id = "frame-${Integer.toHexString(fingerprint.hashCode())}",
            packageName = snapshot.packageName.take(120),
            summary = "",
        )
    }

    companion object {
        private const val DEFAULT_MILESTONE_ID = "work_surface"
        private const val MAX_MEMORY_ITEMS = 24
        private const val MAX_USER_UPDATES = 8
    }
}
