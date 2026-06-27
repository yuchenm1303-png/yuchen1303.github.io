package com.yuchen.ailedger.service

/**
 * Structural execution ledger for the GUI Plus loop.
 *
 * Android deliberately does not interpret page text, target meaning, expected evidence, failed
 * hypotheses or route quality. Those decisions belong exclusively to GUI Plus. This ledger keeps
 * only objective execution facts needed to preserve the verified work surface and to report what
 * physically changed after an action.
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
            append("|semanticDecisionOwner=gui_plus")
            append("|localSemanticDecision=false")
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

    fun updateTaskContract(contract: VisualTaskContract?, fallbackGoal: String = originalGoal) {
        if (contract == null) return
        val normalized = contract.copy(
            originalGoal = contract.originalGoal.ifBlank { fallbackGoal }.take(240),
            currentMilestoneId = contract.currentMilestoneId.ifBlank {
                contract.milestones.firstOrNull { !it.completed }?.id ?: currentMilestoneId
            },
            explorationBudgetPerMilestone = contract.explorationBudgetPerMilestone.coerceIn(1, 4),
        )
        taskContract = normalized
        currentMilestoneId = normalized.currentMilestoneId.ifBlank { DEFAULT_MILESTONE_ID }
        completedMilestoneIds += normalized.completedMilestoneIds
        completedMilestoneIds += normalized.milestones.filter { it.completed }.map { it.id }
    }

    fun onVerifiedSurface(snapshot: AgentScreenSnapshot) {
        currentPage = structuralPage(snapshot)
        lastConfirmedPage = currentPage
        structuralReplanRequested = false
        lastProgressStatus = "surface_verified"
    }

    fun resetAfterUserTakeover(snapshot: AgentScreenSnapshot? = null) {
        structuralReplanRequested = false
        lastProgressStatus = "user_takeover_reset"
        snapshot?.let {
            currentPage = structuralPage(it)
            lastConfirmedPage = currentPage
        }
    }

    /** GUI Plus owns hypothesis, route and evidence decisions; Android never blocks on semantics. */
    fun blockedHypothesisReason(
        @Suppress("UNUSED_PARAMETER") step: CloudAgentStep,
        @Suppress("UNUSED_PARAMETER") snapshot: AgentScreenSnapshot,
    ): String? = null

    fun evaluate(
        step: CloudAgentStep,
        before: AgentScreenSnapshot,
        after: AgentScreenSnapshot,
        verifiedTargetPackage: String,
    ): VisualSemanticProgressResult {
        val beforeFingerprint = VisualActionValidator.snapshotFingerprint(before)
        val afterFingerprint = VisualActionValidator.snapshotFingerprint(after)
        val pageChanged = beforeFingerprint != afterFingerprint
        val packageChanged = before.packageName != after.packageName
        val structuralRegression = verifiedTargetPackage.isNotBlank() &&
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                currentPackage = after.packageName,
                verifiedTargetPackage = verifiedTargetPackage,
            )

        val status = when {
            structuralRegression -> VisualSemanticProgressStatus.Regressed
            pageChanged -> VisualSemanticProgressStatus.Advanced
            else -> VisualSemanticProgressStatus.Stalled
        }
        structuralReplanRequested = structuralRegression
        lastProgressStatus = when {
            structuralRegression -> "structural_regression"
            pageChanged -> "screen_changed_unjudged"
            else -> "screen_unchanged_unjudged"
        }
        currentPage = structuralPage(after)
        if (!structuralRegression) lastConfirmedPage = currentPage

        val reason = when {
            structuralRegression -> "The Android-verified target package was lost; route ownership must be restored before another visual action."
            pageChanged -> "The observed screen structure changed. GUI Plus exclusively decides whether this is task progress."
            else -> "The observed screen structure remained stable. GUI Plus exclusively decides the next action."
        }
        val memory = memorySnapshot()
        return VisualSemanticProgressResult(
            status = status,
            actionSignature = VisualActionValidator.actionSignature(step),
            milestoneId = currentMilestoneId,
            pageChanged = pageChanged,
            packageChanged = packageChanged,
            expectedEvidenceMatched = emptyList(),
            failureEvidenceMatched = emptyList(),
            newEvidence = emptyList(),
            failedHypothesisCount = 0,
            explorationBudgetRemaining = memory.remainingExplorationBudget,
            requiresStrategyChange = structuralRegression,
            requiresReplan = structuralRegression,
            reobserveRecommended = false,
            shouldPauseForUser = false,
            structuralRegression = structuralRegression,
            reason = reason,
            taskMemory = memory,
        )
    }

    fun memorySnapshot(snapshot: AgentScreenSnapshot? = null): VisualTaskMemory {
        snapshot?.let { currentPage = structuralPage(it) }
        val contract = taskContract
        val neutralBudget = if (contract == null) {
            legacyExplorationBudgetPerMilestone.coerceAtLeast(1)
        } else {
            contract.explorationBudgetPerMilestone
                .takeIf { it > 0 }
                ?: defaultExplorationBudgetPerMilestone.coerceAtLeast(1)
        }
        return VisualTaskMemory(
            originalGoal = contract?.originalGoal?.ifBlank { originalGoal } ?: originalGoal,
            currentMilestoneId = currentMilestoneId,
            completedMilestoneIds = completedMilestoneIds.toList().takeLast(MAX_MEMORY_ITEMS),
            currentPage = currentPage,
            confirmedFacts = emptyList(),
            failedHypotheses = emptyList(),
            blockedActions = emptyList(),
            remainingExplorationBudget = neutralBudget,
            lastConfirmedPage = lastConfirmedPage,
            progressStatus = lastProgressStatus,
            replanRequested = structuralReplanRequested,
            recoveryMode = structuralReplanRequested,
            legacyMode = contract == null,
            taskContract = contract,
        )
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
    }
}
